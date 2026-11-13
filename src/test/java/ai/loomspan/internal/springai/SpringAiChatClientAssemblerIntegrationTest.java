package ai.loomspan.internal.springai;

import ai.loomspan.api.PlannerInabilityException;
import ai.loomspan.api.SkillKind;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.linter.LinterCallAdvisor;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.model.ModelInteractionMode;
import ai.loomspan.internal.model.ModelInteractionRequest;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.usage.*;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import ai.loomspan.internal.core.TestLoomspanSessions;
import io.micrometer.observation.ObservationRegistry;
import com.google.genai.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiChatClientAssemblerIntegrationTest
{
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void productionStandardAssemblyHasOneToolLoopInsideSemanticRetry() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(toolCall("call-1", "first"));
            server.enqueue(text("invalid"));
            server.enqueue(toolCall("call-2", "second"));
            server.enqueue(text("OK: corrected"));
            Harness harness = harness(server, true);

            String result = harness.call(ModelInteractionMode.STANDARD);

            assertThat(result).isEqualTo("OK: corrected");
            assertThat(server.getRequestCount()).isEqualTo(4);
            assertThat(harness.toolExecutions).hasValue(2);
            assertThat(harness.session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(4);
            var tools = JSON.readTree(server.takeRequest().getBody().readUtf8()).path("tools");
            assertThat(tools).extracting(tool -> tool.path("name").asString())
                    .containsExactlyInAnyOrder("tax-lookup", PlannerInabilityTool.NAME);
        }
    }

    @Test
    void productionStepAssemblyKeepsOneToolLoopAndFiltersSemanticValidators() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(toolCall("call-1", "step"));
            server.enqueue(text("invalid"));
            Harness harness = harness(server, true);

            String result = harness.call(ModelInteractionMode.STEP_EXECUTION);

            assertThat(result).isEqualTo("invalid");
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(harness.toolExecutions).hasValue(1);
            assertThat(harness.session.getSessionUsage().orElseThrow().providerAttempts()).isEqualTo(2);
        }
    }

    @Test
    void exposesControlForToolFreeStandardExecution() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(text("OK: direct"));
            Harness harness = harness(server, false);

            assertThat(harness.call(ModelInteractionMode.STANDARD)).isEqualTo("OK: direct");
            assertThat(server.getRequestCount()).isOne();
            var request = JSON.readTree(server.takeRequest().getBody().readUtf8());
            var tools = request.path("tools");
            assertThat(tools.size()).isOne();
            var function = tools.get(0);
            assertThat(function.path("name").asText()).isEqualTo("loomspan_cannot_complete");
            var parameters = function.path("parameters");
            assertThat(parameters.path("properties").path("reason").path("type").asText()).isEqualTo("string");
            assertThat(parameters.path("required").get(0).asText()).isEqualTo("reason");
            assertThat(parameters.path("additionalProperties").isBoolean()).isTrue();
            assertThat(parameters.path("additionalProperties").asBoolean()).isFalse();
            assertThat(harness.toolExecutions).hasValue(0);
            assertThat(harness.session.getSessionUsage().orElseThrow().providerAttempts()).isOne();
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void standardControlWorksWithEachRealAdapter(AiDriver driver) throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            if (driver == AiDriver.GEMINI)
                Client.setDefaultBaseUrls(Optional.of(server.url("/").toString()), Optional.empty());
            try
            {
                String reason = "  Missing information\r\n\u96ea\t";
                String arguments = "{ \n \"reason\" : " + JSON.writeValueAsString(reason) + " \r\n }";
                server.enqueue(nativeResponse(driver, true, arguments));
                server.enqueue(nativeResponse(driver, false, "OK: direct"));
                Harness harness = harness(server, false, driver);

                assertThatThrownBy(() -> harness.call(ModelInteractionMode.STANDARD))
                        .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(reason);
                assertThat(server.getRequestCount()).isOne();
                assertNativeControl(driver, JSON.readTree(server.takeRequest().getBody().readUtf8()));
                assertThat(harness.call(ModelInteractionMode.STANDARD)).isEqualTo("OK: direct");
                assertNativeControl(driver, JSON.readTree(server.takeRequest().getBody().readUtf8()));
                assertThat(server.getRequestCount()).isEqualTo(2);
                assertThat(harness.toolExecutions).hasValue(0);

                var records = new ArrayList<TraceRecord>();
                harness.session.readTraceRecords(records::add);
                var response = records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                        .findFirst().orElseThrow();
                var call = response.data().path("generations").get(0).path("toolCalls").get(0);
                assertThat(call.path("name").asString()).isEqualTo(PlannerInabilityTool.NAME);
                assertThat(JSON.readTree(call.path("arguments").asString()).path("reason").asString()).isEqualTo(reason);
                switch (driver)
                {
                    case OPENAI -> {
                        assertThat(call.path("id").asString()).isEqualTo("signal");
                        assertThat(call.path("arguments").asString()).isEqualTo(arguments);
                    }
                    case ANTHROPIC -> assertThat(call.path("id").asString()).isEqualTo("signal");
                    case GEMINI -> assertThat(call.path("id").asString()).isEmpty();
                    default -> throw new AssertionError(driver);
                }
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                        .singleElement().satisfies(failure -> {
                            assertThat(failure.frameId()).isEqualTo(response.frameId());
                            var diagnostics = failure.data().path("diagnostics");
                            assertThat(diagnostics).extracting(entry -> entry.path("kind").asString())
                                    .containsExactly("JAVA_STACK_TRACE", "PLANNER_INABILITY_SIGNAL");
                            var signal = diagnostics.get(1);
                            assertThat(signal.path("contentType").asString()).isEqualTo("text/plain; charset=utf-8");
                            assertThat(signal.path("truncated").asBoolean()).isFalse();
                            assertThat(signal.path("captureLimitBytes").asInt()).isEqualTo(1024 * 1024);
                            assertThat(JSON.readTree(signal.path("text").asString())).isEqualTo(call);
                        });
            }
            finally
            {
                if (driver == AiDriver.GEMINI) Client.setDefaultBaseUrls(Optional.empty(), Optional.empty());
            }
        }
    }

    @Test
    void initialPlanningAndStepTurnsRejectAnUnboundControl() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            String arguments = "{\"reason\":\"missing information\"}";
            server.enqueue(nativeResponse(AiDriver.OPENAI, true, arguments));
            server.enqueue(nativeResponse(AiDriver.OPENAI, true, arguments));
            Harness harness = harness(server, false);

            assertThatThrownBy(() -> harness.call(ModelInteractionMode.STANDARD, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unbound tool selection");
            assertThatThrownBy(() -> harness.call(ModelInteractionMode.STEP_EXECUTION))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unbound tool selection");
            for (int turn = 0; turn < 2; turn++)
            {
                var wire = JSON.readTree(server.takeRequest().getBody().readUtf8());
                assertThat(wire.path("tools").size()).isZero();
                assertThat(wire.path("instructions").asString()).isEqualTo("system");
            }
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedResponseRetainsEveryGenerationAndProviderCharge(boolean mixed) throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            String arguments = mixed ? "{\"reason\":\"blocked\"}" : "{ invalid JSON \r\n  ";
            var outputItems = new ArrayList<Map<String, Object>>();
            if (mixed) outputItems.add(openAiToolItem(0, "tax-lookup", "same-id", "  {}\r\n"));
            outputItems.add(openAiToolItem(outputItems.size(), PlannerInabilityTool.NAME, "same-id", arguments));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(Map.of(
                    "id", "tool", "object", "response", "created_at", 1, "model", "gpt-test", "output", outputItems,
                    "status", "completed", "usage", Map.of("input_tokens", 1, "output_tokens", 1, "total_tokens", 2)))));
            Harness harness = harness(server, true);

            assertThatThrownBy(() -> harness.call(ModelInteractionMode.STANDARD)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(mixed ? "only tool selection" : "nonblank string reason");
            assertThat(server.getRequestCount()).isOne();
            assertThat(harness.toolExecutions).hasValue(0);
            var usage = harness.session.getSessionUsage().orElseThrow();
            assertThat(usage.providerAttempts()).isOne();
            assertThat(usage.modelCalls()).isOne();
            assertThat(usage.usageUnits()).isEqualTo(2);
            var records = new ArrayList<TraceRecord>();
            harness.session.readTraceRecords(records::add);
            assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED
                    || record.recordType() == TraceRecordType.ERROR_RECORDED);
            assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT).hasSize(1);
            assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                    .singleElement().satisfies(response -> {
                        var generations = response.data().path("generations");
                        assertThat(generations.size()).isOne();
                        assertThat(generations.get(0).path("toolCalls").size()).isEqualTo(outputItems.size());
                        assertThat(generations).allSatisfy(generation -> {
                            assertThat(generation.path("text").asString()).isEmpty();
                            assertThat(generation.path("toolCalls").get(0).path("id").asString()).isEqualTo("same-id");
                        });
                        if (mixed) assertThat(generations.get(0).path("toolCalls").get(0).path("arguments").asString())
                                .isEqualTo("  {}\r\n");
                        assertThat(generations.get(0).path("toolCalls").get(outputItems.size() - 1).path("arguments").asString())
                                .isEqualTo(arguments);
                    });
        }
    }

    @Test
    void responseQuotaPrecedesAcceptanceOfAValidDeclaration() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(nativeResponse(AiDriver.OPENAI, true, "{\"reason\":\"missing information\"}"));
            var quotas = new LoomspanProperties().getSession().getQuotas();
            quotas.setMaxUsageUnits(1);
            Harness harness = harness(server, false, AiDriver.OPENAI, quotas);

            assertThatThrownBy(() -> harness.call(ModelInteractionMode.STANDARD))
                    .isInstanceOf(ai.loomspan.internal.runtime.LoomspanQuotaExceededException.class)
                    .hasMessageContaining("MAX_USAGE_UNITS");
            var records = new ArrayList<TraceRecord>();
            harness.session.readTraceRecords(records::add);
            assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED).hasSize(1);
            assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.ERROR_RECORDED);
            var usage = harness.session.getSessionUsage().orElseThrow();
            assertThat(usage.providerAttempts()).isOne();
            assertThat(usage.modelCalls()).isOne();
            assertThat(usage.usageUnits()).isEqualTo(2);
            assertThat(server.getRequestCount()).isOne();
        }
    }

    @Test
    void signalDiagnosticIsUtf8BoundedWhileReasonAndModelEvidenceRemainExact() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            String reason = "\u96ea".repeat(350_000) + "\r\n ";
            String arguments = "{\"reason\":" + JSON.writeValueAsString(reason) + "}";
            server.enqueue(nativeResponse(AiDriver.OPENAI, true, arguments));
            Harness harness = harness(server, false);
            assertThatThrownBy(() -> harness.call(ModelInteractionMode.STANDARD))
                    .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(reason);

            var records = new ArrayList<TraceRecord>();
            harness.session.readTraceRecords(records::add);
            var response = records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                    .findFirst().orElseThrow();
            assertThat(response.metadata()).containsEntry("payloadChunked", true);
            assertThat(response.data().path("generations").get(0).path("toolCalls").get(0).path("arguments").asString())
                    .isEqualTo(arguments);
            var failure = records.stream().filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                    .findFirst().orElseThrow().data();
            assertThat(failure.path("exceptionMessage").asString()).isEqualTo(reason);
            var signal = failure.path("diagnostics").get(1);
            assertThat(signal.path("truncated").asBoolean()).isTrue();
            int limit = signal.path("captureLimitBytes").asInt();
            String captured = signal.path("text").asString();
            assertThat(captured.getBytes(StandardCharsets.UTF_8).length).isBetween(limit - 2, limit);
            var expected = new LinkedHashMap<String, Object>();
            expected.put("id", "signal");
            expected.put("name", PlannerInabilityTool.NAME);
            expected.put("arguments", arguments);
            assertThat(JSON.writeValueAsString(expected)).startsWith(captured);
            assertThat(captured).doesNotContain("\ufffd");
        }
    }

    @Test
    void escapedReasonCodeUnitSurvivesCanonicalFailureRecording() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            String reason = "exact " + (char) 0xD800;
            String arguments = "{\"reason\":\"exact \\uD800\"}";
            server.enqueue(nativeResponse(AiDriver.OPENAI, true, arguments));
            Harness harness = harness(server, false);
            assertThatThrownBy(() -> harness.call(ModelInteractionMode.STANDARD))
                    .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(reason);

            var records = new ArrayList<TraceRecord>();
            harness.session.readTraceRecords(records::add);
            assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                    .singleElement().satisfies(failure -> {
                        assertThat(failure.data()).isNotNull();
                        assertThat(failure.data().path("exceptionMessage").asString()).isEqualTo(reason);
                        var signal = failure.data().path("diagnostics").get(1).path("text").asString();
                        assertThat(JSON.readTree(signal).path("arguments").asString()).isEqualTo(arguments);
                    });
        }
    }

    private static Map<String, Object> openAiToolItem(int index, String name, String id, String arguments)
    {
        return ai.loomspan.testkit.ResponsesFixtures.functionCall(name, id, arguments);
    }

    private static void assertNativeControl(AiDriver driver, JsonNode wire)
    {
        var tools = wire.path("tools");
        assertThat(tools.size()).isOne();
        var definition = switch (driver)
        {
            case OPENAI -> tools.get(0);
            case ANTHROPIC -> tools.get(0);
            case GEMINI -> tools.get(0).path("functionDeclarations").get(0);
            default -> throw new AssertionError(driver);
        };
        assertThat(definition.path("name").asString()).isEqualTo(PlannerInabilityTool.NAME);
        assertThat(definition.path("description").asString()).contains("unsuccessfully", "alone", "does not undo");
        var schema = definition.path(driver == AiDriver.ANTHROPIC ? "input_schema" : "parameters");
        assertThat(schema.path("properties").path("reason").path("type").asString()).isEqualToIgnoringCase("string");
        assertThat(schema.path("required")).extracting(JsonNode::asString).containsExactly("reason");
        if (driver == AiDriver.OPENAI)
        {
            assertThat(schema.path("additionalProperties").isBoolean()).isTrue();
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        }
    }

    private static MockResponse nativeResponse(AiDriver driver, boolean signal, String value)
    {
        String name = PlannerInabilityTool.NAME;
        String quoted = JSON.writeValueAsString(value);
        String body = switch (driver)
        {
            case OPENAI -> signal ? ai.loomspan.testkit.ResponsesFixtures.body("tool", "gpt-test", List.of(
                    ai.loomspan.testkit.ResponsesFixtures.textItem("OK: accompanying text"),
                    ai.loomspan.testkit.ResponsesFixtures.functionCall(name, "signal", value))) : null;
            case ANTHROPIC -> """
                    {"id":"msg_1","type":"message","role":"assistant","model":"gpt-test","content":[%s],
                     "stop_reason":"%s","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}
                    """.formatted(signal ? "{\"type\":\"tool_use\",\"id\":\"signal\",\"name\":\"%s\",\"input\":%s}".formatted(name, value)
                            : "{\"type\":\"text\",\"text\":%s}".formatted(quoted), signal ? "tool_use" : "end_turn");
            case GEMINI -> """
                    {"modelVersion":"gpt-test","candidates":[{"content":{"role":"model","parts":[%s]},"finishReason":"STOP","index":0}],
                     "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
                    """.formatted(signal ? "{\"functionCall\":{\"name\":\"%s\",\"args\":%s}}".formatted(name, value)
                            : "{\"text\":%s}".formatted(quoted));
            default -> throw new AssertionError(driver);
        };
        return body == null ? text(value) : new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void productionToolAdapterPreservesExplicitNullArguments() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(toolCallArguments("call-null", "{\\\"value\\\":null}"));
            server.enqueue(text("OK: null accepted"));
            Harness harness = harness(server, true);

            assertThat(harness.call(ModelInteractionMode.STANDARD)).isEqualTo("OK: null accepted");
            assertThat(harness.observedArguments.get()).containsEntry("value", null);
        }
    }

    private static Harness harness(MockWebServer server, boolean includeTool)
    {
        return harness(server, includeTool, AiDriver.OPENAI);
    }

    private static Harness harness(MockWebServer server, boolean includeTool, AiDriver driver)
    {
        return harness(server, includeTool, driver, new LoomspanProperties().getSession().getQuotas());
    }

    private static Harness harness(MockWebServer server, boolean includeTool, AiDriver driver,
            LoomspanProperties.Session.Quotas quotas)
    {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(driver);
        properties.setApiKey("assembler-key");
        if (driver != AiDriver.GEMINI)
            properties.setBaseUrl(server.url(driver == AiDriver.OPENAI ? "/v1" : "/").toString());
        properties.getProviderRetry().setEnabled(false);
        ProviderConnectionRuntime runtime = new SpringAiProviderIntegration(new DefaultResourceLoader()).create("openai", properties);
        DefaultSessionUsageService usage = new DefaultSessionUsageService(quotas, new NoOpUsageMetricsRecorder());
        DefaultExecutionStateService state = new DefaultExecutionStateService(Clock.systemUTC(), usage);
        var resolver = (ai.loomspan.internal.chat.SkillAdvisorResolver) ignored -> List.of(
                new LinterCallAdvisor("default-test", "regex", Pattern.compile("^OK:.*$"), "Return OK", 1, fact -> { }));
        SpringAiModelInteractionFactory factory = new SpringAiModelInteractionFactory(
                (skill, configuration) -> runtime, resolver, state, new ModelUsageExtractor(), usage,
                new SpringAiChatOptionsContributor(), ObservationRegistry.NOOP);
        LoomspanSession session = TestLoomspanSessions.withId("assembler", "default-test", 8);
        var binding = ai.loomspan.internal.core.TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(binding, () -> {
            state.openMissionFrame(session, "default-test", Map.of());
            state.openFrame(session, TraceFrameType.MODEL_CALL, "default-test#model", Map.of());
        });
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<Map<String, Object>> observedArguments = new AtomicReference<>();
        BoundCapability capability = capability(executions, observedArguments);
        return new Harness(factory, definition(driver), session, binding,
                includeTool ? List.of(capability) : List.of(), executions,
                observedArguments);
    }

    private record Harness(SpringAiModelInteractionFactory factory, YamlSkillDefinition definition,
            LoomspanSession session, ai.loomspan.internal.core.ExecutionBinding binding,
            List<BoundCapability> capabilities, AtomicInteger toolExecutions,
            AtomicReference<Map<String, Object>> observedArguments)
    {
        String call(ModelInteractionMode mode)
        {
            return call(mode, false);
        }

        String call(ModelInteractionMode mode, boolean planning)
        {
            return ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, () ->
                    factory.create(definition, mode).call(new ModelInteractionRequest(
                            "system", new RenderedMissionInput("user", List.of(), Map.of()),
                            new ModelTraceContext(ModelExecutionIdentity.from(definition.requireExecutionConfiguration()),
                                    "default-test", "mission"), capabilities, planning)).content());
        }
    }

    private static YamlSkillDefinition definition(AiDriver driver)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("test");
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", new ByteArrayResource(new byte[0]), manifest,
                new EffectiveSkillExecutionConfiguration("gpt-test", "openai", driver, "gpt-test", null));
    }

    private static BoundCapability capability(AtomicInteger executions,
            AtomicReference<Map<String, Object>> observedArguments)
    {
        CapabilityMetadata metadata = new CapabilityMetadata("test:lookup", "tax-lookup", "Look up",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()), ignored -> null, SkillKind.YAML,
                new CapabilityToolDescriptor("tax-lookup", "Look up",
                        "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}", null),
                SkillInputContract.genericObject(), null);
        return new BoundCapability(metadata, java.util.List.of(), (arguments, task, sourceResults) -> {
            executions.incrementAndGet();
            observedArguments.set(arguments);
            return "looked-up-" + arguments.get("value");
        });
    }

    private static MockResponse toolCall(String id, String value)
    {
        return toolCallArguments(id, "{\\\"value\\\":\\\"%s\\\"}".formatted(value));
    }

    private static MockResponse toolCallArguments(String id, String arguments)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"tool","object":"response","created_at":1,"model":"gpt-test","status":"completed","output":[{"type":"function_call","id":"fc_0","call_id":"%s","name":"Tax-lookup","arguments":"%s","status":"completed"}],"usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}
                """.formatted(id, arguments));
    }

    private static MockResponse text(String value)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"text","object":"response","created_at":1,"model":"gpt-test","status":"completed","output":[{"id":"msg_0","type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"%s","annotations":[]}]}],"usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}
                """.formatted(value));
    }
}
