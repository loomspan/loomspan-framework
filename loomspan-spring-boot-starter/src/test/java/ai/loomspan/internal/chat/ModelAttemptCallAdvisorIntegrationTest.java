package ai.loomspan.internal.chat;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.AdvisorTraceFact;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.core.TestLoomspanSessions;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.linter.LinterCallAdvisor;
import ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor;
import ai.loomspan.internal.outputschema.OutputSchemaPromptAugmentor;
import ai.loomspan.internal.outputschema.OutputSchemaValidator;
import ai.loomspan.internal.runtime.LoomspanQuotaExceededException;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.usage.DefaultSessionUsageService;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.MicrometerUsageMetricsRecorder;
import ai.loomspan.internal.runtime.usage.NoOpUsageMetricsRecorder;
import ai.loomspan.internal.provider.*;
import ai.loomspan.internal.springai.SpringAiProviderIntegration;
import ai.loomspan.internal.skill.YamlSkillManifest;
import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIServiceException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ModelAttemptCallAdvisorIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void recordsEachAdvisorRetryAsOnePhysicalAttemptInTheSameRetrySequence()
    {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DefaultSessionUsageService usageService = new DefaultSessionUsageService(
                new LoomspanProperties().getSession().getQuotas(),
                new MicrometerUsageMetricsRecorder(meterRegistry));
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("attempt-retry", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ExecutionFrame root = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        ExecutionFrame modelFrame = openFrame(binding, stateService,
                session,
                TraceFrameType.MODEL_CALL,
                "test.skill#model",
                Map.of());
        ModelTraceContext traceContext = traceContext();
        QueueChatModel model = new QueueChatModel(List.of(
                response("invalid", 10, 4),
                response("OK: corrected", 8, 3)));

        LinterCallAdvisor linter = new LinterCallAdvisor(
                "test.skill",
                "regex",
                Pattern.compile("^OK:.*$"),
                "Return an OK response.",
                1,
                outcome -> stateService.recordLinterOutcome(LoomspanSession.getCurrentSession(), outcome),
                fact -> recordFact(stateService, fact));
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(
                        linter,
                        new ProviderAttemptCallAdvisor(runtime(model), stateService, new ModelUsageExtractor(), usageService))
                .build();

        String content = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .system("system")
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                .call()
                .content());

        closeFrame(binding, stateService, session, modelFrame, Map.of("status", "completed"));
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                binding, () -> stateService.closeMissionFrame(session, root));

        assertThat(content).isEqualTo("OK: corrected");
        assertThat(model.calls).isEqualTo(2);
        List<TraceRecord> records = records(session);
        List<TraceRecord> responses = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                .toList();
        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(record -> record.metadata().get("attemptNumber"))
                .containsExactly(1, 2);
        assertThat(responses).extracting(record -> record.metadata().get("retrySequenceId"))
                .containsOnly(traceContext.retrySequenceId());
        assertThat(responses).extracting(record -> record.metadata().get("attemptId"))
                .doesNotHaveDuplicates();
        assertThat(session.getSessionUsage().orElseThrow().modelCalls()).isEqualTo(2);
        assertThat(session.getSessionUsage().orElseThrow().promptUnits()).isEqualTo(18);
        assertThat(session.getSessionUsage().orElseThrow().completionUnits()).isEqualTo(7);
        assertThat(meterRegistry.get("loomspan.model.calls")
                .tag("skill", "test.skill")
                .tag("connection", "test-connection")
                .tag("driver", "openai")
                .tag("precision", "EXACT")
                .counter()
                .count()).isEqualTo(2.0d);

        TraceRecord retryFact = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.ADVISOR_REQUEST_MUTATION_RECORDED)
                .filter(record -> "retrying".equals(record.metadata().get("status")))
                .findFirst()
                .orElseThrow();
        assertThat(retryFact.metadata())
                .containsEntry("retrySequenceId", traceContext.retrySequenceId())
                .containsEntry("attemptNumber", 1)
                .containsEntry("attemptId", responses.getFirst().metadata().get("attemptId"));
    }

    @Test
    void semanticRetriesWrapOneObservableAi2ToolLoop() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(openAiToolCall("call-1", "first"));
            server.enqueue(openAiText("invalid"));
            server.enqueue(openAiToolCall("call-2", "second"));
            server.enqueue(openAiText("OK: corrected"));
            LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
            properties.setDriver(AiDriver.OPENAI);
            properties.setApiKey("test-key");
            properties.setBaseUrl(server.url("/v1").toString());
            properties.getProviderRetry().setEnabled(false);
            ProviderConnectionRuntime runtime = new SpringAiProviderIntegration(new DefaultResourceLoader())
                    .create("openai", properties);
            DefaultSessionUsageService usageService = usageService();
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
            LoomspanSession session = TestLoomspanSessions.withId("advisor-recursion", "test.entry", 4);
            ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
            openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
            AtomicInteger toolExecutions = new AtomicInteger();
            List<String> toolValues = new ArrayList<>();
            ToolCallback tool = FunctionToolCallback.<Map<String, Object>, String>builder(
                            "lookup",
                            (arguments, context) ->
                            {
                                toolExecutions.incrementAndGet();
                                toolValues.add(String.valueOf(arguments.get("value")));
                                return "looked-up-" + arguments.get("value");
                            })
                    .description("Look up a value")
                    .inputType(new ParameterizedTypeReference<Map<String, Object>>() { })
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"required\":[\"value\"]}")
                    .build();
            LinterCallAdvisor semanticPolicy = new LinterCallAdvisor(
                    "test.skill", "regex", Pattern.compile("^OK:.*$"),
                    "Return an OK response.", 1, ignored -> { });
            ChatClient client = ChatClient.builder(runtime.chatModel())
                    .defaultAdvisors(semanticPolicy,
                            new ProviderAttemptCallAdvisor(runtime, stateService,
                                    new ModelUsageExtractor(), usageService))
                    .build();

            String content = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                    .user("user")
                    .options(OpenAiChatOptions.builder().model("gpt-test"))
                    .toolCallbacks(tool)
                    .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext()))
                    .call()
                    .content());

            assertThat(content).isEqualTo("OK: corrected");
            assertThat(server.getRequestCount()).isEqualTo(4);
            assertThat(toolExecutions).hasValue(2);
            assertThat(toolValues).containsExactly("first", "second");
            assertThat(server.takeRequest().getBody().readUtf8()).contains("\"name\":\"lookup\"");
            assertThat(server.takeRequest().getBody().readUtf8()).contains("\"tool_call_id\":\"call-1\"");
            assertThat(server.takeRequest().getBody().readUtf8()).contains("\"name\":\"lookup\"");
            assertThat(server.takeRequest().getBody().readUtf8()).contains("\"tool_call_id\":\"call-2\"");

            // Semantic validation wraps the AI 2 tool advisor; every inner model turn crosses the
            // physical-attempt advisor exactly once.
            assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(4);
            assertThat(session.getSessionUsage().orElseThrow().modelCalls()).isEqualTo(4);
            assertThat(records(session).stream()
                    .filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT)).hasSize(4);
            assertThat(records(session).stream()
                    .filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(4);
        }
    }

    @Test
    void outputSchemaRetryReusesCompletedToolResultWhenCorrectionReturnsJsonDirectly() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(openAiToolCall("call-1", "first"));
            server.enqueue(openAiText("{\\\"result\\\":1}"));
            server.enqueue(openAiText("{\\\"result\\\":\\\"looked-up-first\\\"}"));
            LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
            properties.setDriver(AiDriver.OPENAI);
            properties.setApiKey("test-key");
            properties.setBaseUrl(server.url("/v1").toString());
            properties.getProviderRetry().setEnabled(false);
            ProviderConnectionRuntime runtime = new SpringAiProviderIntegration(new DefaultResourceLoader())
                    .create("openai", properties);
            DefaultSessionUsageService usageService = usageService();
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
            LoomspanSession session = TestLoomspanSessions.withId("output-schema-tool-reuse", "test.entry", 4);
            ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
            openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
            AtomicInteger toolExecutions = new AtomicInteger();
            ToolCallback tool = FunctionToolCallback.<Map<String, Object>, String>builder(
                            "lookup",
                            (arguments, context) ->
                            {
                                toolExecutions.incrementAndGet();
                                return "looked-up-" + arguments.get("value");
                            })
                    .description("Look up a value")
                    .inputType(new ParameterizedTypeReference<Map<String, Object>>() { })
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"required\":[\"value\"]}")
                    .build();
            YamlSkillManifest.OutputSchemaManifest valueSchema = new YamlSkillManifest.OutputSchemaManifest();
            valueSchema.setType("string");
            YamlSkillManifest.OutputSchemaManifest outputSchema = new YamlSkillManifest.OutputSchemaManifest();
            outputSchema.setType("object");
            outputSchema.setProperties(Map.of("result", valueSchema));
            outputSchema.setRequired(List.of("result"));
            outputSchema.setAdditionalProperties(false);
            OutputSchemaCallAdvisor semanticPolicy = new OutputSchemaCallAdvisor(
                    "test.skill", outputSchema, new OutputSchemaValidator(), new OutputSchemaPromptAugmentor(),
                    1, ignored -> { });
            ChatClient client = ChatClient.builder(runtime.chatModel())
                    .defaultAdvisors(semanticPolicy,
                            new ProviderAttemptCallAdvisor(runtime, stateService,
                                    new ModelUsageExtractor(), usageService))
                    .build();

            String content = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                    .user("user")
                    .options(OpenAiChatOptions.builder().model("gpt-test"))
                    .toolCallbacks(tool)
                    .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext()))
                    .call()
                    .content());

            assertThat(content).isEqualTo("{\"result\":\"looked-up-first\"}");
            assertThat(server.getRequestCount()).isEqualTo(3);
            assertThat(toolExecutions).hasValue(1);
            assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(3);
            assertThat(records(session).stream()
                    .filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT)).hasSize(3);
            assertThat(records(session).stream()
                    .filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(3);
            server.takeRequest();
            server.takeRequest();
            String retryBody = server.takeRequest().getBody().readUtf8();
            assertThat(retryBody)
                    .contains("{\\\"result\\\":1}")
                    .contains("Do NOT call any tools again");
        }
    }

    @Test
    void recordsSentButNoResponseWhenProviderThrows(CapturedOutput output)
    {
        DefaultSessionUsageService usageService = usageService();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("attempt-throw", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ExecutionFrame root = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        ExecutionFrame modelFrame = openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        ModelTraceContext traceContext = traceContext();
        ChatModel throwingModel = prompt ->
        {
            throw new IllegalStateException("provider failed");
        };
        ChatClient client = ChatClient.builder(throwingModel)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime(throwingModel), stateService, new ModelUsageExtractor(), usageService))
                .build();

        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                .call()
                .content()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider failed");

        List<TraceRecord> records = records(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT)).hasSize(1);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED);
        assertThat(session.getSessionUsage()
                .orElse(ai.loomspan.internal.runtime.usage.SessionUsageSnapshot.empty())
                .modelCalls()).isZero();
        assertThat(occurrences(output.getOut(), "Loomspan provider failure for framework model")).isEqualTo(1);

        closeFrame(binding, stateService, session, modelFrame, Map.of("status", "failed"));
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                binding, () -> stateService.closeMissionFrame(session, root));
    }

    @Test
    void terminalOpenAiAuthenticationFailureIsActionableInWarningAndAttemptTrace(CapturedOutput output)
    {
        OpenAIServiceException authenticationFailure = mock(OpenAIServiceException.class);
        JsonValue body = JsonValue.from(java.util.Map.of("error", "invalid credential"));
        when(authenticationFailure.statusCode()).thenReturn(401);
        when(authenticationFailure.headers()).thenReturn(Headers.builder().build());
        when(authenticationFailure.body()).thenReturn(body);
        when(authenticationFailure.type()).thenReturn(java.util.Optional.of("authentication_error"));
        when(authenticationFailure.code()).thenReturn(java.util.Optional.of("invalid_api_key"));

        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OPENAI);
        properties.setApiKey("not-configured");
        ProviderFailureTranslator translator = new SpringAiProviderIntegration(new DefaultResourceLoader())
                .create("primary-openai", properties)
                .failureTranslator();
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt ->
        {
            calls.incrementAndGet();
            throw authenticationFailure;
        };
        ProviderConnectionRuntime runtime = new ProviderConnectionRuntime(
                model,
                AiDriver.OPENAI,
                AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                new ProviderRetryPolicy(true, 3, java.time.Duration.ZERO, 2.0d,
                        java.time.Duration.ZERO, 0.0d),
                translator);
        DefaultSessionUsageService usageService = usageService();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("openai-authentication", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        ModelTraceContext traceContext = new ModelTraceContext(
                new ModelExecutionIdentity("support-model", "primary-openai", AiDriver.OPENAI, "gpt-example"),
                "test.skill",
                "mission");
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, stateService,
                        new ModelUsageExtractor(), usageService))
                .build();

        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                .call()
                .content()))
                .isSameAs(authenticationFailure);

        assertThat(calls).hasValue(1);
        TraceRecord failedAttempt = records(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(failedAttempt.metadata())
                .containsEntry("failureClassification", "PERMANENT")
                .containsEntry("failureCategory", "AUTHENTICATION")
                .containsEntry("retryDecision", "DO_NOT_RETRY")
                .containsEntry("httpStatus", 401)
                .containsEntry("providerErrorType", "authentication_error")
                .containsEntry("providerErrorCode", "invalid_api_key");
        assertThat(failedAttempt.data().path("diagnostics")).hasSize(3);
        assertThat(failedAttempt.data().path("diagnostics").get(0).path("kind").asText())
                .isEqualTo("JAVA_STACK_TRACE");
        assertThat(failedAttempt.data().path("diagnostics").get(1).path("kind").asText())
                .isEqualTo("LOOMSPAN_PROVIDER_GUIDANCE");
        assertThat(failedAttempt.data().path("diagnostics").get(2).path("kind").asText())
                .isEqualTo("PROVIDER_ERROR");
        String guidance = failedAttempt.data().path("diagnostics").get(1).path("text").asText();
        assertThat(guidance)
                .contains("support-model", "primary-openai", "OPENAI", "gpt-example",
                        "loomspan.connections.primary-openai.api-key", "rejected")
                .doesNotContain("missing", "not-configured", "invalid credential");
        assertThat(output.getOut()).contains(guidance);
        assertThat(occurrences(output.getOut(), guidance)).isEqualTo(1);
    }

    @Test
    void retriesTransientProviderFailuresAsDistinctPhysicalAttempts(CapturedOutput output)
    {
        DefaultSessionUsageService usageService = usageService();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("provider-retry", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        ModelTraceContext traceContext = traceContext();
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt ->
        {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary provider failure");
            return response("OK", 3, 2);
        };
        ProviderFailureDetails transientFailure = new ProviderFailureDetails(
                ProviderFailureClassification.TRANSIENT, ProviderFailureCategory.SERVER_ERROR,
                503, null, null, null, "Provider temporarily unavailable", List.of(
                        Map.of(
                                "kind", "PROVIDER_ERROR",
                                "contentType", "text/plain; charset=utf-8",
                                "text", "provider diagnostic one",
                                "truncated", false,
                                "captureLimitBytes", 128),
                        Map.of(
                                "kind", "PROVIDER_RESPONSE",
                                "contentType", "application/json",
                                "text", "{\"error\":\"provider diagnostic two\"}",
                                "truncated", true,
                                "captureLimitBytes", 64)));
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(retryingRuntime(model, transientFailure),
                        stateService, new ModelUsageExtractor(), usageService))
                .build();

        String content = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                .call()
                .content());

        assertThat(content).isEqualTo("OK");
        assertThat(calls).hasValue(2);
        assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(2);
        assertThat(session.getSessionUsage().orElseThrow().modelCalls()).isEqualTo(1);
        assertThat(records(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT)).hasSize(2);
        List<TraceRecord> attemptFailures = records(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED)
                .toList();
        assertThat(attemptFailures).singleElement().satisfies(record ->
        {
            assertThat(record.metadata())
                    .containsEntry("attemptReason", "INITIAL")
                    .containsEntry("providerAttemptNumber", 1)
                    .containsEntry("failureClassification", "TRANSIENT")
                    .containsEntry("retryDecision", "RETRY");
            assertThat(record.data().path("diagnostics")).hasSize(4);
            assertThat(record.data().path("diagnostics").get(0).path("kind").asText())
                    .isEqualTo("JAVA_STACK_TRACE");
            assertThat(record.data().path("diagnostics").get(1).path("kind").asText())
                    .isEqualTo("LOOMSPAN_PROVIDER_GUIDANCE");
            assertThat(record.data().path("diagnostics").get(1).path("text").asText())
                    .contains("loomspan.connections.test-connection.base-url");
            assertThat(record.data().path("diagnostics").get(2).path("kind").asText())
                    .isEqualTo("PROVIDER_ERROR");
            assertThat(record.data().path("diagnostics").get(2).path("text").asText())
                    .isEqualTo("provider diagnostic one");
            assertThat(record.data().path("diagnostics").get(3).path("kind").asText())
                    .isEqualTo("PROVIDER_RESPONSE");
            assertThat(record.data().path("diagnostics").get(3).path("text").asText())
                    .isEqualTo("{\"error\":\"provider diagnostic two\"}");
            assertThat(record.data().path("diagnostics").get(3).path("truncated").asBoolean()).isTrue();
        });
        assertThat(output.getOut()).doesNotContain("Loomspan provider failure for framework model");
        assertThat(records(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                .findFirst().orElseThrow().metadata())
                .containsEntry("attemptReason", "PROVIDER_RETRY")
                .containsEntry("providerAttemptNumber", 2);
    }

    @Test
    void recordsTranslatedOpenAiReadTimeoutWithAttemptLocalStackBeforeSuccessfulRetry()
    {
        DefaultSessionUsageService usageService = usageService();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("openai-read-timeout", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt ->
        {
            if (calls.incrementAndGet() == 1)
            {
                throw new RuntimeException("Error reading response", new InterruptedIOException("timeout"));
            }
            return response("OK", 3, 2);
        };
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OPENAI);
        properties.setApiKey("test-key");
        ProviderFailureTranslator translator = new SpringAiProviderIntegration(new DefaultResourceLoader())
                .create("openai", properties)
                .failureTranslator();
        ProviderConnectionRuntime runtime = new ProviderConnectionRuntime(
                model,
                AiDriver.OPENAI,
                AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                new ProviderRetryPolicy(true, 2, java.time.Duration.ZERO, 2.0d,
                        java.time.Duration.ZERO, 0.0d),
                translator);
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, stateService,
                        new ModelUsageExtractor(), usageService))
                .build();

        String content = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext()))
                .call()
                .content());

        assertThat(content).isEqualTo("OK");
        assertThat(calls).hasValue(2);
        List<TraceRecord> records = records(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT))
                .hasSize(2);
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED))
                .singleElement()
                .satisfies(record ->
                {
                    assertThat(record.metadata())
                            .containsEntry("failureClassification", "TRANSIENT")
                            .containsEntry("failureCategory", "TIMEOUT")
                            .containsEntry("retryDecision", "RETRY")
                            .containsEntry("providerAttemptNumber", 1);
                    assertThat(record.data().path("diagnostics")).hasSize(2);
                    assertThat(record.data().path("diagnostics").get(0).path("kind").asText())
                            .isEqualTo("JAVA_STACK_TRACE");
                    assertThat(record.data().path("diagnostics").get(0).path("text").asText())
                            .contains("java.lang.RuntimeException: Error reading response")
                            .contains("java.io.InterruptedIOException: timeout")
                            .contains("recordsTranslatedOpenAiReadTimeoutWithAttemptLocalStackBeforeSuccessfulRetry");
                    assertThat(record.data().path("diagnostics").get(0).path("truncated").asBoolean()).isFalse();
                    assertThat(record.data().path("diagnostics").get(0).path("captureLimitBytes").asInt())
                            .isEqualTo(1024 * 1024);
                    assertThat(record.data().path("diagnostics").get(1).path("kind").asText())
                            .isEqualTo("LOOMSPAN_PROVIDER_GUIDANCE");
                });
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED))
                .hasSize(1);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.ERROR_RECORDED);
        assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(2);
        assertThat(session.getSessionUsage().orElseThrow().modelCalls()).isEqualTo(1);
    }

    @Test
    void exhaustedProviderRetriesRetainExactAttemptQuotaMetricAndTerminalFacts(CapturedOutput output)
    {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DefaultSessionUsageService usageService = new DefaultSessionUsageService(
                new LoomspanProperties().getSession().getQuotas(),
                new MicrometerUsageMetricsRecorder(meterRegistry));
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("provider-exhaustion", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException terminal = new IllegalStateException("provider remained unavailable");
        ChatModel model = prompt ->
        {
            calls.incrementAndGet();
            throw terminal;
        };
        ProviderFailureDetails transientFailure = new ProviderFailureDetails(
                ProviderFailureClassification.TRANSIENT,
                ProviderFailureCategory.SERVER_ERROR,
                503,
                null,
                null,
                null,
                "Provider temporarily unavailable",
                List.of());
        ProviderConnectionRuntime runtime = new ProviderConnectionRuntime(
                model,
                AiDriver.OPENAI,
                AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                new ProviderRetryPolicy(true, 3, java.time.Duration.ZERO, 2.0d,
                        java.time.Duration.ZERO, 0.0d),
                ignored -> transientFailure);
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, stateService,
                        new ModelUsageExtractor(), usageService))
                .build();

        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext()))
                .call()
                .content()))
                .isSameAs(terminal);

        assertThat(calls).hasValue(3);
        assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(3);
        assertThat(session.getSessionUsage().orElseThrow().modelCalls()).isZero();
        List<TraceRecord> records = records(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT))
                .hasSize(3);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED))
                .isEmpty();
        List<TraceRecord> failures = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED)
                .toList();
        assertThat(failures).hasSize(3);
        assertThat(failures).extracting(record -> record.metadata().get("providerAttemptNumber"))
                .containsExactly(1, 2, 3);
        assertThat(failures).extracting(record -> record.metadata().get("retryDecision"))
                .containsExactly("RETRY", "RETRY", "ATTEMPTS_EXHAUSTED");
        assertThat(failures).extracting(record -> record.metadata().get("retrySequenceId"))
                .containsOnly(failures.getFirst().metadata().get("retrySequenceId"));
        assertThat(failures).allSatisfy(record ->
        {
            assertThat(record.data().path("diagnostics")).hasSize(2);
            assertThat(record.data().path("diagnostics").get(0).path("kind").asText())
                    .isEqualTo("JAVA_STACK_TRACE");
            assertThat(record.data().path("diagnostics").get(0).path("text").asText())
                    .contains("java.lang.IllegalStateException: provider remained unavailable")
                    .contains("exhaustedProviderRetriesRetainExactAttemptQuotaMetricAndTerminalFacts");
            assertThat(record.data().path("diagnostics").get(1).path("kind").asText())
                    .isEqualTo("LOOMSPAN_PROVIDER_GUIDANCE");
        });
        assertThat(occurrences(output.getOut(), "Loomspan provider failure for framework model")).isEqualTo(1);
        assertThat(meterRegistry.get("loomspan.provider.attempts")
                .tag("skill", "test.skill")
                .tag("connection", "test-connection")
                .tag("driver", "openai")
                .tag("outcome", "failed")
                .tag("category", "server_error")
                .meters()).hasSize(2);
        assertThat(meterRegistry.get("loomspan.provider.attempts")
                .tag("decision", "retry").counter().count()).isEqualTo(2.0d);
        assertThat(meterRegistry.get("loomspan.provider.attempts")
                .tag("decision", "attempts_exhausted").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void retryQuotaIsEnforcedFromTheSamePhysicalAttemptsThatAreTraced()
    {
        LoomspanProperties properties = new LoomspanProperties();
        properties.getSession().getQuotas().setMaxModelCalls(1);
        DefaultSessionUsageService usageService = new DefaultSessionUsageService(
                properties.getSession().getQuotas(),
                new NoOpUsageMetricsRecorder());
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("attempt-quota", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        ModelTraceContext traceContext = traceContext();
        QueueChatModel model = new QueueChatModel(List.of(
                response("invalid", 10, 4),
                response("OK: corrected", 8, 3)));
        LinterCallAdvisor linter = new LinterCallAdvisor(
                "test.skill",
                "regex",
                Pattern.compile("^OK:.*$"),
                "Return an OK response.",
                1,
                outcome -> stateService.recordLinterOutcome(LoomspanSession.getCurrentSession(), outcome),
                fact -> recordFact(stateService, fact));
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(
                        linter,
                        new ProviderAttemptCallAdvisor(runtime(model), stateService, new ModelUsageExtractor(), usageService))
                .build();

        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                .user("user")
                .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                .call()
                .content()))
                .isInstanceOf(LoomspanQuotaExceededException.class);

        assertThat(model.calls).isEqualTo(2);
        assertThat(records(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED))
                .hasSize(2);
        assertThat(session.getSessionUsage().orElseThrow().modelCalls()).isEqualTo(2);
    }

    @Test
    void interruptionDuringBackoffPreservesInterruptAndCreatesNoPhantomAttempt() throws Exception
    {
        DefaultSessionUsageService usageService = usageService();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("retry-interrupted", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        ModelTraceContext traceContext = traceContext();
        CountDownLatch firstCall = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt ->
        {
            calls.incrementAndGet();
            firstCall.countDown();
            throw new IllegalStateException("temporary provider failure");
        };
        ProviderFailureDetails transientFailure = new ProviderFailureDetails(
                ProviderFailureClassification.TRANSIENT, ProviderFailureCategory.SERVER_ERROR,
                503, null, null, null, null, List.of());
        ProviderConnectionRuntime runtime = new ProviderConnectionRuntime(model, AiDriver.OPENAI,
                AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                new ProviderRetryPolicy(true, 3, java.time.Duration.ofSeconds(30), 2.0d,
                        java.time.Duration.ofSeconds(30), 0.0d), ignored -> transientFailure);
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, stateService,
                        new ModelUsageExtractor(), usageService)).build();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        Thread worker = new Thread(() ->
        {
            try
            {
                ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt().user("user")
                        .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                        .call().content());
            }
            catch (Throwable failure)
            {
                observed.set(failure);
                interruptPreserved.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        assertThat(firstCall.await(5, TimeUnit.SECONDS)).isTrue();
        worker.interrupt();
        worker.join(5_000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(observed.get()).isInstanceOf(CancellationException.class);
        assertThat(interruptPreserved).isTrue();
        assertThat(calls).hasValue(1);
        assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(1);
        assertThat(records(session).stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT))
                .hasSize(1);
        assertThat(records(session).stream().filter(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED))
                .singleElement().satisfies(record -> assertThat(record.metadata()).containsEntry("retryDecision", "RETRY"));
    }

    @Test
    void interruptionBeforeSendCreatesNoAttempt()
    {
        DefaultSessionUsageService usageService = usageService();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
        LoomspanSession session = TestLoomspanSessions.withId("retry-pre-interrupted", "test.entry", 4);
        ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
        openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt ->
        {
            calls.incrementAndGet();
            return response("unexpected", 1, 1);
        };
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime(model), stateService,
                        new ModelUsageExtractor(), usageService)).build();

        Thread.currentThread().interrupt();
        try
        {
            assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt().user("user")
                    .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext()))
                    .call().content())).isInstanceOf(CancellationException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally
        {
            Thread.interrupted();
        }
        assertThat(calls).hasValue(0);
        assertThat(session.getSessionUsage()).isEmpty();
        assertThat(records(session)).noneMatch(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT);
    }

    @Test
    void openRouterRetryableErrorCompletionRecoversWithTwoVisibleEndpointCalls() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id":"error-1","object":"chat.completion","created":1,"model":"routed-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"unsafe partial"},
                       "finish_reason":"error","error":{"message":"overloaded","code":"E_OVERLOAD",
                       "metadata":{"error_type":"provider_overloaded"}}}]}
                    """));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id":"success-2","object":"chat.completion","created":2,"model":"routed-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"recovered"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                    """));
            LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
            properties.setDriver(AiDriver.OPENAI);
            properties.setApiKey("gateway-key");
            properties.setBaseUrl(server.url("/v1").toString());
            LoomspanProperties.OpenAiOptions openAi = new LoomspanProperties.OpenAiOptions();
            openAi.setCompatibilityProfile(LoomspanProperties.OpenAiCompatibilityProfile.OPENROUTER);
            properties.setOpenai(openAi);
            properties.getProviderRetry().setInitialBackoff(java.time.Duration.ZERO);
            properties.getProviderRetry().setMaxBackoff(java.time.Duration.ZERO);
            properties.getProviderRetry().setJitter(0.0d);
            ProviderConnectionRuntime runtime = new SpringAiProviderIntegration(new DefaultResourceLoader())
                    .create("openrouter", properties);
            DefaultSessionUsageService usageService = usageService();
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(CLOCK, usageService);
            LoomspanSession session = TestLoomspanSessions.withId("openrouter-recovery", "test.entry", 4);
            ai.loomspan.internal.core.ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> stateService.openMissionFrame(session, "test.skill", Map.of()));
            openFrame(binding, stateService, session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
            ModelTraceContext traceContext = traceContext();
            ChatClient client = ChatClient.builder(runtime.chatModel())
                    .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, stateService,
                            new ModelUsageExtractor(), usageService)).build();

            String content = ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () -> client.prompt()
                    .user("hello")
                    .options(OpenAiChatOptions.builder().model("routed-model"))
                    .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, traceContext))
                    .call().content());

            assertThat(content).isEqualTo("recovered");
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(2);
            assertThat(records(session).stream().filter(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED))
                    .singleElement().satisfies(record -> assertThat(record.data().toString()).contains("unsafe partial"));
            assertThat(records(session).stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED))
                    .singleElement().satisfies(record -> assertThat(record.metadata())
                            .containsEntry("attemptReason", "PROVIDER_RETRY")
                            .containsEntry("providerAttemptNumber", 2));
            assertThat(records(session)).noneMatch(record -> record.recordType() == TraceRecordType.ERROR_RECORDED);
        }
    }

    private static void recordFact(DefaultExecutionStateService stateService, AdvisorTraceFact fact)
    {
        if (fact.direction() == AdvisorTraceFact.Direction.REQUEST)
        {
            stateService.recordAdvisorRequestMutation(LoomspanSession.getCurrentSession(), fact.context(), fact.attributes());
        }
        else
        {
            stateService.recordAdvisorResponseMutation(LoomspanSession.getCurrentSession(), fact.context(), fact.attributes());
        }
    }

    private static int occurrences(String value, String needle)
    {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static DefaultSessionUsageService usageService()
    {
        return new DefaultSessionUsageService(
                new LoomspanProperties().getSession().getQuotas(),
                new NoOpUsageMetricsRecorder());
    }

    private static ProviderConnectionRuntime runtime(ChatModel model)
    {
        LoomspanProperties.ProviderRetryProperties retry = new LoomspanProperties.ProviderRetryProperties();
        retry.setEnabled(false);
        return new ProviderConnectionRuntime(model, AiDriver.OPENAI, AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                ProviderRetryPolicy.from(retry), ignored -> ProviderFailureDetails.unknown());
    }

    private static ProviderConnectionRuntime retryingRuntime(ChatModel model, ProviderFailureDetails details)
    {
        return new ProviderConnectionRuntime(model, AiDriver.OPENAI, AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                new ProviderRetryPolicy(true, 2, java.time.Duration.ZERO, 2.0d, java.time.Duration.ZERO, 0.0d),
                ignored -> details);
    }

    private static ModelTraceContext traceContext()
    {
        return new ModelTraceContext(
                new ModelExecutionIdentity("test-model", "test-connection", AiDriver.OPENAI, "provider/model"),
                "test.skill",
                "mission");
    }

    private static ChatResponse response(String text, int promptUnits, int completionUnits)
    {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptUnits, completionUnits, promptUnits + completionUnits))
                        .build());
    }

    private static MockResponse openAiToolCall(String callId, String value)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"chatcmpl-tool","object":"chat.completion","created":1,"model":"gpt-test",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"%s","type":"function","function":{"name":"lookup","arguments":"{\\\"value\\\":\\\"%s\\\"}"}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(callId, value));
    }

    private static MockResponse openAiText(String text)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"chatcmpl-text","object":"chat.completion","created":1,"model":"gpt-test",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(text));
    }

    private static List<TraceRecord> records(LoomspanSession session)
    {
        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        return records;
    }

    private static ExecutionFrame openFrame(
            ai.loomspan.internal.core.ExecutionBinding binding,
            DefaultExecutionStateService stateService,
            LoomspanSession session,
            TraceFrameType frameType,
            String route,
            Map<String, Object> parameters)
    {
        return ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(
                binding, () -> stateService.openFrame(session, frameType, route, parameters));
    }

    private static void closeFrame(
            ai.loomspan.internal.core.ExecutionBinding binding,
            DefaultExecutionStateService stateService,
            LoomspanSession session,
            ExecutionFrame frame,
            Map<String, Object> metadata)
    {
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                binding, () -> stateService.closeFrame(session, frame, metadata));
    }

    private static final class QueueChatModel implements ChatModel
    {
        private final Queue<ChatResponse> responses;
        private int calls;

        private QueueChatModel(List<ChatResponse> responses)
        {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt)
        {
            calls++;
            return responses.remove();
        }
    }
}
