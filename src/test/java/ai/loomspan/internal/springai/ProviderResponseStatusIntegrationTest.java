package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.chat.ProviderAttemptCallAdvisor;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.outputschema.*;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.usage.*;
import ai.loomspan.internal.skill.YamlSkillManifest;
import com.google.genai.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real official adapters against deterministic loopback HTTP; no live capability/billing claim. */
class ProviderResponseStatusIntegrationTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOOL = "default-test_lookup";
    private static final String VALID_JSON = "{\"ok\":true}";

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void incompleteValidJsonBypassesSchemaAcceptanceAndCorrection(AiDriver driver) throws Exception
    {
        try (Harness h = new Harness(driver))
        {
            h.enqueue(text(driver, incomplete(driver), VALID_JSON));
            List<OutputSchemaOutcome> outcomes = new ArrayList<>();
            var schema = new ai.loomspan.internal.schema.StandardSchemaCompiler().compile("""
                    {"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"],"additionalProperties":false}
                    """, ai.loomspan.internal.schema.StandardSchemaCompiler.Role.OUTPUT,
                    ai.loomspan.internal.schema.StandardSchemaCompiler.Origin.AUTHORED);
            var validation = new OutputSchemaCallAdvisor("default-test_skill", schema,
                    new OutputSchemaValidator(), new OutputSchemaPromptAugmentor(), 2, outcomes::add);
            var lintOutcomes = new AtomicInteger();
            var evidenceOutcomes = new AtomicInteger();
            var linter = new ai.loomspan.internal.linter.LinterCallAdvisor("default-test_skill", "regex",
                    java.util.regex.Pattern.compile("MUST_NEVER_MATCH"), "correction forbidden", 2,
                    ignored -> lintOutcomes.incrementAndGet());
            var evidenceValidator = org.mockito.Mockito.mock(ai.loomspan.internal.runtime.evidence.EvidenceBackedOutputValidator.class);
            var evidence = new ai.loomspan.internal.runtime.evidence.EvidenceContractCallAdvisor("default-test_skill",
                    ai.loomspan.internal.runtime.evidence.EvidenceContract.empty(), evidenceValidator, 2,
                    ignored -> evidenceOutcomes.incrementAndGet(), ignored -> evidenceOutcomes.incrementAndGet(), AdvisorTraceRecorder.noOp());
            assertThatThrownBy(() -> h.call(h.client(validation, linter, evidence)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(outcomes).as("provider rejection must escape before schema acceptance/correction").isEmpty();
            assertThat(lintOutcomes).hasValue(0);
            assertThat(evidenceOutcomes).hasValue(0);
            org.mockito.Mockito.verifyNoInteractions(evidenceValidator);
            h.assertRejected(VALID_JSON, incomplete(driver));
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void explicitRefusalFailsOnceWithOriginalEvidence(AiDriver driver) throws Exception
    {
        try (Harness h = new Harness(driver))
        {
            h.enqueue(refusal(driver));
            assertThatThrownBy(() -> h.call(h.client())).isInstanceOf(RuntimeException.class);
            h.assertRejected(driver == AiDriver.GEMINI ? "SAFETY" : "REFUSAL_SENTINEL",
                    driver == AiDriver.OPENAI ? "refusal" : driver == AiDriver.ANTHROPIC ? "refusal" : "SAFETY");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII"})
    void geminiExplicitTextFilteringStatusesAreTerminal(String status) throws Exception
    {
        try (Harness h = new Harness(AiDriver.GEMINI))
        {
            h.enqueue(text(AiDriver.GEMINI, status, "FILTERED_CONTENT_SENTINEL"));
            assertThatThrownBy(() -> h.call(h.client())).isInstanceOf(ai.loomspan.internal.provider.ProviderResponseRejectedException.class);
            h.assertRejected("FILTERED_CONTENT_SENTINEL", status);
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void incompleteMultiToolResponseExecutesNoCallbacks(AiDriver driver) throws Exception
    {
        try (Harness h = new Harness(driver))
        {
            AtomicInteger callbacks = new AtomicInteger();
            h.enqueue(tools(driver, true));
            // This would let an incorrect partial dispatch finish rather than hang on a second request.
            h.enqueue(text(driver, complete(driver), "unexpected accepted partial tools"));
            assertThatThrownBy(() -> h.call(h.client(), callback(callbacks))).isInstanceOf(RuntimeException.class);
            assertThat(callbacks).hasValue(0);
            h.assertRejected(TOOL, incomplete(driver));
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void completeToolTurnsContinueAndUsageIsNotAggregatedTwice(AiDriver driver) throws Exception
    {
        try (Harness h = new Harness(driver))
        {
            AtomicInteger callbacks = new AtomicInteger();
            h.enqueue(tools(driver, false));
            h.enqueue(text(driver, complete(driver), "finished"));
            assertThat(h.call(h.client(), callback(callbacks))).isEqualTo("finished");
            assertThat(callbacks).hasValue(2);
            assertThat(h.server.getRequestCount()).isEqualTo(2);
            var usage = h.session.getSessionUsage().orElseThrow();
            assertThat(usage.providerAttempts()).isEqualTo(2);
            assertThat(usage.modelCalls()).isEqualTo(2);
            assertThat(usage.promptUnits()).isEqualTo(14);
            assertThat(usage.completionUnits()).isEqualTo(6);
            assertThat(usage.usageUnits()).isEqualTo(20);
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(2);
            assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).isEmpty();
            h.assertMetrics(0, 2, 2);
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void completeRefusalLikeProseIsAcceptedWithoutTextHeuristics(AiDriver driver) throws Exception
    {
        try (Harness h = new Harness(driver))
        {
            String prose = "I cannot help with that request; the response is incomplete.";
            h.enqueue(text(driver, complete(driver), prose));
            assertThat(h.call(h.client())).isEqualTo(prose);
            assertThat(h.server.getRequestCount()).isEqualTo(1);
            assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).isEmpty();
            h.assertMetrics(0, 1, 1);
        }
    }

    @Test
    void geminiBlockedPromptRetainsFeedbackAndRejectsWithoutCandidates() throws Exception
    {
        requireB05();
        try (Harness h = new Harness(AiDriver.GEMINI))
        {
            h.enqueue(Map.of("modelVersion", "fixture", "candidates", List.of(),
                    "promptFeedback", Map.of("blockReason", "SAFETY", "blockReasonMessage", "PROMPT_BLOCK_SENTINEL"),
                    "usageMetadata", geminiUsage()));
            Throwable failure = catchThrowable(() -> h.call(h.client()));
            org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(failure).as("explicit prompt block must terminate").isInstanceOf(RuntimeException.class);
                soft.assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED).toString())
                        .contains("promptFeedback", "SAFETY", "PROMPT_BLOCK_SENTINEL");
                soft.assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).hasSize(1);
                soft.assertThat(h.session.getSessionUsage().orElseThrow().usageUnits()).isEqualTo(10);
                soft.assertThat(h.server.getRequestCount()).isEqualTo(1);
            });
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void incompletePlanningResponseNeverReachesJsonOrYamlParser(AiDriver driver) throws Exception
    {
        try (Harness h = new Harness(driver))
        {
            h.enqueue(text(driver, incomplete(driver), "{\"capabilityName\":\"default-test_skill\",\"tasks\":[]}"));
            var jsonParser = org.mockito.Mockito.mock(tools.jackson.databind.ObjectMapper.class);
            var yamlParser = org.mockito.Mockito.mock(tools.jackson.databind.ObjectMapper.class);
            var planner = new ai.loomspan.internal.runtime.planning.DefaultPlanningService(h.state, jsonParser, yamlParser);
            var manifest = new YamlSkillManifest();
            manifest.setName("test_skill");
            manifest.setDescription("Fixture planner");
            manifest.setModel("fixture");
            var definition = new ai.loomspan.internal.skill.YamlSkillDefinition(
                    ai.loomspan.internal.skill.YamlSkillDefinition.Form.MODEL, "default",
                    new org.springframework.core.io.ByteArrayResource(new byte[0]), manifest,
                    new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration("fixture", "fixture", driver, "fixture", "medium"));
            var interaction = new SpringAiModelInteraction(h.client(), ai.loomspan.internal.model.ModelInteractionMode.STANDARD);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(h.binding, () -> planner.initializePlan(
                    h.session, "Plan fixture", Map.of(), definition, interaction, List.of())))
                    .isInstanceOf(ai.loomspan.internal.provider.ProviderResponseRejectedException.class);
            org.mockito.Mockito.verifyNoInteractions(jsonParser, yamlParser);
            assertThat(h.server.getRequestCount()).isEqualTo(1);
            assertThat(h.facts(TraceRecordType.PLAN_CREATED)).isEmpty();
            assertThat(h.facts(TraceRecordType.PLAN_RETRY_REQUESTED)).isEmpty();
            assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).hasSize(1);
            assertThat(h.session.getSessionUsage().orElseThrow().usageUnits()).isEqualTo(10);
        }
    }

    @Test
    void geminiCandidateRetainsNativeFinishAndSafetyDetails() throws Exception
    {
        requireB05();
        try (Harness h = new Harness(AiDriver.GEMINI))
        {
            var body = new LinkedHashMap<>(text(AiDriver.GEMINI, "SAFETY", "PARTIAL_SENTINEL"));
            body.put("candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(Map.of("text", "PARTIAL_SENTINEL"))),
                    "finishReason", "SAFETY", "finishMessage", "CANDIDATE_FINISH_SENTINEL",
                    "safetyRatings", List.of(Map.of("category", "HARM_CATEGORY_HARASSMENT", "probability", "HIGH", "blocked", true)))));
            h.enqueue(body);
            Throwable failure = catchThrowable(() -> h.call(h.client()));
            org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(failure).isInstanceOf(RuntimeException.class);
                soft.assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED).toString())
                        .contains("CANDIDATE_FINISH_SENTINEL", "HARM_CATEGORY_HARASSMENT", "HIGH", "PARTIAL_SENTINEL");
                soft.assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).hasSize(1);
                soft.assertThat(h.session.getSessionUsage().orElseThrow().usageUnits()).isEqualTo(10);
                soft.assertThat(h.server.getRequestCount()).isEqualTo(1);
            });
        }
    }

    private static void requireB05()
    {
        Assumptions.assumeTrue(Boolean.getBoolean("loomspan.pr30.blockers"),
                "PR30-B05: RC2 drops Gemini prompt/finish evidence; see ai/thoughts/phases/pr-30-blockers.md. "
                        + "Run complete assertions with -Dloomspan.pr30.blockers=true");
    }

    @Test
    void failedResponsesEnvelopeRetainsActualUsageAndRetriesOnlyEvidencedError() throws Exception
    {
        try (Harness h = new Harness(AiDriver.OPENAI))
        {
            h.enqueue(failedEnvelope("provider_overloaded"));
            h.enqueue(text(AiDriver.OPENAI, "completed", "recovered"));
            assertThat(h.call(h.client())).isEqualTo("recovered");
            assertThat(h.server.getRequestCount()).isEqualTo(2);
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(2);
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED).toString()).contains("FAILED_CONTENT_SENTINEL", "FAILED_ERROR_SENTINEL");
            assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).singleElement().satisfies(record ->
                    assertThat(record.metadata()).containsEntry("retryDecision", "RETRY"));
            var usage = h.session.getSessionUsage().orElseThrow();
            assertThat(usage.modelCalls()).isEqualTo(2);
            assertThat(usage.providerAttempts()).isEqualTo(2);
            assertThat(usage.usageUnits()).isEqualTo(20);
            h.assertMetrics(1, 1, 2);
        }
    }

    @Test
    void unknownFailedResponsesErrorIsNotMadeTransientByFailedStatus() throws Exception
    {
        try (Harness h = new Harness(AiDriver.OPENAI))
        {
            h.enqueue(failedEnvelope("unclassified_fixture_error"));
            assertThatThrownBy(() -> h.call(h.client())).isInstanceOf(RuntimeException.class);
            h.assertRejected("FAILED_CONTENT_SENTINEL", "failed");
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED).toString()).contains("FAILED_ERROR_SENTINEL");
        }
    }

    @Test
    void failedEnvelopeWithUnrepresentableUsageRetainsEvidenceAndRetryPolicy() throws Exception
    {
        try (Harness h = new Harness(AiDriver.OPENAI))
        {
            var failed = new LinkedHashMap<>(failedEnvelope("provider_overloaded"));
            failed.put("usage", Map.of("input_tokens", 4294967296L, "output_tokens", 0, "total_tokens", 4294967296L));
            h.enqueue(failed);
            h.enqueue(text(AiDriver.OPENAI, "completed", "recovered"));
            assertThat(h.call(h.client())).isEqualTo("recovered");
            assertThat(h.server.getRequestCount()).isEqualTo(2);
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(2);
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED).getFirst().data().toString())
                    .contains("4294967296", "FAILED_CONTENT_SENTINEL", "FAILED_ERROR_SENTINEL");
            assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).singleElement().satisfies(record ->
                    assertThat(record.metadata()).containsEntry("retryDecision", "RETRY"));
            var usage = h.session.getSessionUsage().orElseThrow();
            assertThat(usage.providerAttempts()).isEqualTo(2);
            assertThat(usage.modelCalls()).isEqualTo(2);
            assertThat(usage.usageUnits()).isEqualTo(10);
            assertThat(usage.unavailableModelResponses()).isEqualTo(1);
            assertThat(usage.exactModelResponses()).isEqualTo(1);
            h.assertMetrics(1, 1, 2);
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiDriver.class, names = {"OPENAI", "ANTHROPIC", "GEMINI"})
    void rejectionPreservesExactNativeZeroAndMissingUsageDistinction(AiDriver driver) throws Exception
    {
        for (boolean supplied : List.of(true, false))
        {
            try (Harness h = new Harness(driver))
            {
                var body = new LinkedHashMap<>(text(driver, incomplete(driver), VALID_JSON));
                String key = driver == AiDriver.GEMINI ? "usageMetadata" : "usage";
                if (!supplied) body.remove(key);
                else body.put(key, switch (driver) {
                    case OPENAI -> Map.of("input_tokens", 0, "output_tokens", 0, "total_tokens", 0);
                    case ANTHROPIC -> Map.of("input_tokens", 0, "output_tokens", 0);
                    case GEMINI -> Map.of("promptTokenCount", 0, "candidatesTokenCount", 0, "totalTokenCount", 0);
                    default -> throw new AssertionError();
                });
                h.enqueue(body);
                Throwable failure = catchThrowable(() -> h.call(h.client()));
                assertThat(failure).isInstanceOf(RuntimeException.class);
                var usage = h.session.getSessionUsage().orElseThrow();
                assertThat(h.server.getRequestCount()).isEqualTo(1);
                // Anthropic's native Message requires usage; omission throws before response mapping.
                // No actual usage is accessible from that SDK exception, so fabricate neither a
                // returned response receipt nor heuristic tokens for it.
                if (!supplied && driver == AiDriver.ANTHROPIC)
                {
                    assertThat(failure).hasStackTraceContaining("usage");
                    assertThat(usage.modelCalls()).isZero();
                    assertThat(usage.usageUnits()).isZero();
                    assertThat(usage.providerAttempts()).isEqualTo(1);
                    assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED)).isEmpty();
                    assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).hasSize(1);
                    h.assertMetrics(1, 0, 0);
                    continue;
                }
                assertThat(usage.modelCalls()).isEqualTo(1);
                assertThat(usage.providerAttempts()).isEqualTo(1);
                assertThat(usage.exactModelResponses()).isEqualTo(supplied ? 1 : 0);
                assertThat(usage.heuristicModelResponses()).isEqualTo(supplied ? 0 : 1);
                if (supplied) assertThat(usage.usageUnits()).isZero();
                else assertThat(usage.usageUnits()).isPositive();
                h.assertMetrics(1, 0, 1);
            }
        }
    }

    @Test
    void quotaRaisedDuringRejectedOrFailedEnvelopeAccountingRemainsAuthoritative() throws Exception
    {
        for (boolean thrownEnvelope : List.of(true, false))
        {
            var quotas = new LoomspanProperties.Session.Quotas();
            quotas.setMaxUsageUnits(5);
            try (Harness h = new Harness(AiDriver.OPENAI, quotas))
            {
                h.enqueue(thrownEnvelope ? failedEnvelope("provider_overloaded") : text(AiDriver.OPENAI, "incomplete", VALID_JSON));
                assertThatThrownBy(() -> h.call(h.client()))
                        .isInstanceOf(ai.loomspan.internal.runtime.LoomspanQuotaExceededException.class);
                assertThat(h.server.getRequestCount()).isEqualTo(1);
                assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(1);
                assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).hasSize(1);
                var usage = h.session.getSessionUsage().orElseThrow();
                assertThat(usage.modelCalls()).isEqualTo(1);
                assertThat(usage.providerAttempts()).isEqualTo(1);
                assertThat(usage.usageUnits()).isEqualTo(10);
                h.assertMetrics(1, 0, 1);
            }
        }
    }

    private static Map<String, Object> failedEnvelope(String type)
    {
        var body = new LinkedHashMap<>(text(AiDriver.OPENAI, "failed", "FAILED_CONTENT_SENTINEL"));
        body.put("error", Map.of("code", "FIXTURE_CODE", "type", type, "message", "FAILED_ERROR_SENTINEL", "metadata", Map.of("error_type", type)));
        return body;
    }

    @Test
    void cancellationBeforeSendAllocatesNoAttemptOrUsage() throws Exception
    {
        try (Harness h = new Harness(AiDriver.OPENAI))
        {
            try
            {
                Thread.currentThread().interrupt();
                assertThatThrownBy(() -> h.call(h.client())).isInstanceOf(java.util.concurrent.CancellationException.class);
            }
            finally { Thread.interrupted(); }
            assertThat(h.server.getRequestCount()).isZero();
            assertThat(h.session.getSessionUsage()).isEmpty();
            assertThat(h.facts(TraceRecordType.MODEL_REQUEST_SENT)).isEmpty();
            h.assertMetrics(0, 0, 0);
        }
    }

    @Test
    void rejectedLateResponseAfterMissionClosureCannotWriteUsageOrDispatch() throws Exception
    {
        try (Harness h = new Harness(AiDriver.OPENAI))
        {
            var callbacks = new AtomicInteger();
            h.server.setDispatcher(new okhttp3.mockwebserver.Dispatcher()
            {
                @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request)
                {
                    // Deterministically close after the request exists but before provider evidence returns.
                    h.binding.requireMission().lifecycle().closeNow();
                    return new MockResponse().setHeader("Content-Type", "application/json")
                            .setBody(JSON.writeValueAsString(tools(AiDriver.OPENAI, true)));
                }
            });
            assertThatThrownBy(() -> h.call(h.client(), callback(callbacks))).isInstanceOf(RuntimeException.class);
            assertThat(callbacks).hasValue(0);
            assertThat(h.server.getRequestCount()).isEqualTo(1);
            assertThat(h.facts(TraceRecordType.MODEL_REQUEST_SENT)).hasSize(1);
            assertThat(h.facts(TraceRecordType.MODEL_RESPONSE_RECEIVED)).isEmpty();
            assertThat(h.facts(TraceRecordType.MODEL_ATTEMPT_FAILED)).isEmpty();
            var usage = h.session.getSessionUsage().orElseThrow();
            assertThat(usage.modelCalls()).isZero();
            assertThat(usage.usageUnits()).isZero();
            assertThat(usage.providerAttempts()).isEqualTo(1);
            h.assertMetrics(0, 0, 0);
        }
    }

    private static ToolCallback callback(AtomicInteger effects)
    {
        return FunctionToolCallback.<Map<String, Object>, String>builder(TOOL, input -> {
            effects.incrementAndGet(); return "tool-result";
        }).description("Fixture authorized lookup").inputType(Map.class)
                .inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"required\":[\"value\"]}")
                .build();
    }

    private static String incomplete(AiDriver driver)
    {
        return switch (driver) { case OPENAI -> "incomplete"; case ANTHROPIC -> "max_tokens"; case GEMINI -> "MAX_TOKENS"; default -> throw new AssertionError(); };
    }

    private static String complete(AiDriver driver)
    {
        return switch (driver) { case OPENAI -> "completed"; case ANTHROPIC -> "end_turn"; case GEMINI -> "STOP"; default -> throw new AssertionError(); };
    }

    private static Map<String, Object> text(AiDriver driver, String status, String text)
    {
        return switch (driver)
        {
            case OPENAI -> openai(status, List.of(Map.of("id", "msg_fixture", "type", "message", "role", "assistant", "status", "completed",
                    "content", List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())))));
            case ANTHROPIC -> anthropic(status, List.of(Map.of("type", "text", "text", text)));
            case GEMINI -> Map.of("modelVersion", "fixture", "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(Map.of("text", text))),
                    "finishReason", status)), "usageMetadata", geminiUsage());
            default -> throw new AssertionError();
        };
    }

    private static Map<String, Object> refusal(AiDriver driver)
    {
        if (driver == AiDriver.OPENAI) return openai("completed", List.of(Map.of("id", "msg_fixture", "type", "message", "role", "assistant", "status", "completed",
                "content", List.of(Map.of("type", "refusal", "refusal", "REFUSAL_SENTINEL")))));
        return text(driver, driver == AiDriver.ANTHROPIC ? "refusal" : "SAFETY", "REFUSAL_SENTINEL");
    }

    private static Map<String, Object> tools(AiDriver driver, boolean incomplete)
    {
        var parts = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= 2; i++)
        {
            Map<String, Object> args = Map.of("value", "v" + i);
            parts.add(switch (driver)
            {
                case OPENAI -> Map.of("type", "function_call", "id", "fc_" + i, "call_id", "call_" + i,
                        "name", TOOL, "arguments", JSON.writeValueAsString(args), "status", "completed");
                case ANTHROPIC -> Map.of("type", "tool_use", "id", "toolu_" + i, "name", TOOL, "input", args);
                case GEMINI -> Map.of("functionCall", Map.of("name", TOOL, "args", args));
                default -> throw new AssertionError();
            });
        }
        return switch (driver)
        {
            case OPENAI -> openai(incomplete ? "incomplete" : "completed", parts);
            case ANTHROPIC -> anthropic(incomplete ? "max_tokens" : "tool_use", parts);
            case GEMINI -> Map.of("modelVersion", "fixture", "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", parts),
                    "finishReason", incomplete ? "MAX_TOKENS" : "STOP")), "usageMetadata", geminiUsage());
            default -> throw new AssertionError();
        };
    }

    private static Map<String, Object> openai(String status, List<?> output)
    {
        var response = new LinkedHashMap<String, Object>();
        response.putAll(Map.of("id", "resp_fixture", "object", "response", "created_at", 1, "model", "fixture",
                "status", status, "output", output, "usage", Map.of("input_tokens", 7, "output_tokens", 3, "total_tokens", 10)));
        if (status.equals("incomplete")) response.put("incomplete_details", Map.of("reason", "max_output_tokens"));
        return response;
    }

    private static Map<String, Object> anthropic(String status, List<?> content)
    {
        return Map.of("id", "msg_fixture", "type", "message", "role", "assistant", "model", "fixture",
                "content", content, "stop_reason", status, "usage", Map.of("input_tokens", 7, "output_tokens", 3));
    }

    private static Map<String, Object> geminiUsage()
    {
        return Map.of("promptTokenCount", 7, "candidatesTokenCount", 3, "totalTokenCount", 10);
    }

    private static final class Harness implements AutoCloseable
    {
        final MockWebServer server = new MockWebServer();
        final AiDriver driver;
        final ProviderConnectionRuntime runtime;
        final UsageMetricsRecorder metrics = mock(UsageMetricsRecorder.class);
        final DefaultSessionUsageService usage;
        final DefaultExecutionStateService state;
        final LoomspanSession session;
        final ExecutionBinding binding;
        final ModelTraceContext context;

        Harness(AiDriver driver) throws Exception
        {
            this(driver, new LoomspanProperties.Session.Quotas());
        }

        Harness(AiDriver driver, LoomspanProperties.Session.Quotas quotas) throws Exception
        {
            this.driver = driver;
            usage = new DefaultSessionUsageService(quotas, metrics);
            state = new DefaultExecutionStateService(Clock.systemUTC(), usage);
            String base = server.url("/").toString();
            if (driver == AiDriver.GEMINI) Client.setDefaultBaseUrls(Optional.of(base), Optional.of(base));
            var properties = new LoomspanProperties.ConnectionProperties();
            properties.setDriver(driver);
            properties.setApiKey("fixture-local-key");
            if (driver != AiDriver.GEMINI) properties.setBaseUrl(base);
            properties.getProviderRetry().setMaxAttempts(3);
            properties.getProviderRetry().setInitialBackoff(java.time.Duration.ZERO);
            properties.getProviderRetry().setMaxBackoff(java.time.Duration.ZERO);
            properties.getProviderRetry().setJitter(0);
            runtime = new SpringAiProviderIntegration(new DefaultResourceLoader()).create("fixture", properties);
            session = TestLoomspanSessions.withId("status-" + driver + "-" + UUID.randomUUID(), "default-test_entry", 4);
            binding = TestExecutionBindings.missionBinding(session);
            ExecutionBindingScope.runWith(binding, () -> {
                state.openMissionFrame(session, "default-test_skill", Map.of());
                state.openFrame(session, TraceFrameType.MODEL_CALL, "test.skill#model", Map.of());
            });
            context = new ModelTraceContext(new ModelExecutionIdentity("fixture", "fixture", driver, "fixture"), "default-test_skill", "mission");
        }

        void enqueue(Map<String, Object> body)
        {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(body)));
        }

        ChatClient client(Advisor... outer)
        {
            var advisors = new ArrayList<Advisor>(List.of(outer));
            advisors.add(new ProviderAttemptCallAdvisor(runtime, state, new ModelUsageExtractor(), usage));
            var builder = ChatClient.builder(runtime.chatModel()).defaultAdvisors(advisors);
            switch (driver)
            {
                case OPENAI -> builder.defaultOptions(OpenAiResponsesChatOptions.builder().model("fixture"));
                case ANTHROPIC -> builder.defaultOptions(AnthropicChatOptions.builder().model("fixture").maxTokens(256));
                case GEMINI -> builder.defaultOptions(GoogleGenAiChatOptions.builder().model("fixture"));
                default -> throw new AssertionError();
            }
            return builder.build();
        }

        String call(ChatClient client, ToolCallback... callbacks)
        {
            return ExecutionBindingScope.supplyWith(binding, () -> {
                var request = client.prompt().user("Return fixture output")
                        .advisors(spec -> spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, context));
                switch (driver)
                {
                    case OPENAI -> request.options(OpenAiResponsesChatOptions.builder().model("fixture"));
                    case ANTHROPIC -> request.options(AnthropicChatOptions.builder().model("fixture").maxTokens(256));
                    case GEMINI -> request.options(GoogleGenAiChatOptions.builder().model("fixture"));
                    default -> throw new AssertionError();
                }
                if (callbacks.length != 0) request.toolCallbacks(callbacks);
                return request.call().content();
            });
        }

        List<TraceRecord> facts(TraceRecordType type)
        {
            List<TraceRecord> records = new ArrayList<>();
            session.readTraceRecords(records::add);
            return records.stream().filter(record -> record.recordType() == type).toList();
        }

        void assertRejected(String content, String status)
        {
            assertThat(server.getRequestCount()).isEqualTo(1);
            var responses = facts(TraceRecordType.MODEL_RESPONSE_RECEIVED);
            var failures = facts(TraceRecordType.MODEL_ATTEMPT_FAILED);
            assertThat(responses).singleElement().satisfies(record -> {
                assertThat(record.data().toString()).contains(status);
                if (VALID_JSON.equals(content)) assertThat(JSON.readTree(record.data().toString()).at("/generations/0/text").asText()).isEqualTo(content);
                else assertThat(record.data().toString()).contains(content);
            });
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().metadata().get("attemptId")).isEqualTo(responses.getFirst().metadata().get("attemptId"));
            var snapshot = session.getSessionUsage().orElseThrow();
            assertThat(snapshot.providerAttempts()).isEqualTo(1);
            assertThat(snapshot.modelCalls()).isEqualTo(1);
            assertThat(snapshot.promptUnits()).isEqualTo(7);
            assertThat(snapshot.completionUnits()).isEqualTo(3);
            assertThat(snapshot.usageUnits()).isEqualTo(10);
            assertThat(snapshot.exactModelResponses()).isEqualTo(1);
            assertMetrics(1, 0, 1);
        }

        void assertMetrics(int failed, int succeeded, int modelUsage)
        {
            verify(metrics, times(failed)).recordProviderAttempt(any(), any(), eq("failed"), any(), any());
            verify(metrics, times(succeeded)).recordProviderAttempt(any(), any(), eq("succeeded"), any(), any());
            verify(metrics, times(modelUsage)).recordModelUsage(any(), any(), any());
        }

        @Override public void close() throws Exception
        {
            try { server.close(); }
            finally { if (driver == AiDriver.GEMINI) Client.setDefaultBaseUrls(Optional.empty(), Optional.empty()); }
        }
    }
}
