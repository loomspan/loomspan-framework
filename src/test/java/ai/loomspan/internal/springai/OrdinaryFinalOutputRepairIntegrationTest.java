package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.chat.ProviderAttemptCallAdvisor;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.linter.LinterCallAdvisor;
import ai.loomspan.internal.model.*;
import ai.loomspan.internal.outputschema.*;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.evidence.*;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.*;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.testkit.ResponsesFixtures;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static ai.loomspan.internal.springai.OrdinaryNativeOutputIntegrationTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual ordinary interaction/advisors/canonical dispatch and official serializers over loopback.
 * Session/state collaborators are mocked here; real accounting/isolation lives in lifecycle tests. */
class OrdinaryFinalOutputRepairIntegrationTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String BAD_SCHEMA = "{\"name\":\"x\"}";
    static final String BAD_EVIDENCE = "{\"name\":\"evidence candidate\",\"claim\":\"unsupported\"}";
    static final String BAD_LINTER = "{\"name\":\"linter candidate\"}";
    static final String GOOD = "{\"name\":\"accepted\"}";
    static Stream<org.junit.jupiter.params.provider.Arguments> cases() {
        return Stream.of("gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash")
                .flatMap(id -> Stream.of("schema", "linter", "evidence", "combined")
                        .map(mode -> org.junit.jupiter.params.provider.Arguments.of(id, mode)));
    }
    static Stream<String> identities() { return Stream.of("gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"); }

    @ParameterizedTest @MethodSource("cases")
    void eachOrdinaryAdvisorAndCombinationReplaysCompletedWorkWithoutExecutableDefaults(String identity, String mode) throws Exception {
        local(identity, (server, runtime) -> {
            var effects = new AtomicInteger();
            var schema = schema(runtime.driver());
            var effective = modelDefaults(runtime, callback(effects));
            var client = repairClient(effective, identity, schema, mode, null, 3);
            server.enqueue(tool(runtime.driver(), "one", false));
            var candidates = mode.equals("combined") ? List.of(BAD_SCHEMA, BAD_EVIDENCE, BAD_LINTER)
                    : List.of(bad(mode));
            candidates.forEach(candidate -> server.enqueue(text(runtime.driver(), candidate)));
            server.enqueue(text(runtime.driver(), GOOD));
            assertThat(invoke(client, runtime, identity, schema, input(false)).content()).isEqualTo(GOOD);
            var initial = request(server);
            assertThat(initial.path("tools").isEmpty()).isFalse();
            var continuation = request(server);
            assertExchange(continuation, runtime.driver(), "one", 1);
            for (int index = 0; index < candidates.size(); index++) {
                var repair = request(server);
                assertRepair(repair, runtime.driver(), candidates.get(index));
                assertNative(repair, runtime.driver(), identity, 2048, schema);
                for (String stale : candidates.subList(0, index)) assertThat(texts(repair)).doesNotContain(stale);
            }
            assertThat(server.getRequestCount()).isEqualTo(3 + candidates.size() - 1);
            assertThat(effects).hasValue(1);
        });
    }

    @ParameterizedTest @MethodSource("cases")
    void attemptedRepairToolCallsCannotDispatchOriginalDefaultCallbacks(String identity, String mode) throws Exception {
        local(identity, (server, runtime) -> {
            var effects = new AtomicInteger();
            var schema = schema(runtime.driver());
            var effective = modelDefaults(runtime, callback(effects));
            var client = repairClient(effective, identity, schema, mode, null, 3);
            server.enqueue(tool(runtime.driver(), "one", false));
            server.enqueue(text(runtime.driver(), bad(mode)));
            server.enqueue(tool(runtime.driver(), "forbidden", false));
            assertThatThrownBy(() -> invoke(client, runtime, identity, schema, input(false)))
                    .hasStackTraceContaining("repair");
            request(server); request(server);
            assertRepair(request(server), runtime.driver(), bad(mode));
            assertThat(effects).hasValue(1);
            assertThat(server.getRequestCount()).isEqualTo(3);
        });
    }

    @ParameterizedTest @MethodSource("identities")
    void repeatedSchemaRepairsRetainTwoRichExchangesLatestCandidateAndAttachmentOnce(String identity) throws Exception {
        local(identity, (server, runtime) -> {
            var effects = new AtomicInteger();
            var schema = schema(runtime.driver());
            var client = repairClient(runtime, identity, schema, "schema", callback(effects), 3);
            server.enqueue(tool(runtime.driver(), "one", true));
            server.enqueue(tool(runtime.driver(), "two", true));
            var candidates = List.of("{\"name\":\"a\"}", "{\"name\":\"b\"}", "{\"name\":\"c\"}");
            for (int i = 0; i < candidates.size(); i++) server.enqueue(richText(runtime.driver(), candidates.get(i), i));
            server.enqueue(text(runtime.driver(), GOOD));
            assertThat(invoke(client, runtime, identity, schema, input(true)).content()).isEqualTo(GOOD);
            request(server); request(server); request(server);
            for (int index = 0; index < candidates.size(); index++) {
                var repair = request(server);
                assertNoTools(repair);
                assertExchange(repair, runtime.driver(), "one", 2);
                assertExchange(repair, runtime.driver(), "two", 2);
                assertThat(texts(repair)).contains(candidates.get(index));
                for (String stale : candidates.subList(0, index)) assertThat(texts(repair)).doesNotContain(stale);
                assertRich(repair, runtime.driver());
                assertThat(texts(repair)).contains("candidate-signature-" + index);
                for (int prior = 0; prior < index; prior++) assertThat(texts(repair)).doesNotContain("candidate-signature-" + prior);
                assertThat(find(repair, runtime.driver() == AiDriver.OPENAI ? "type" : runtime.driver() == AiDriver.ANTHROPIC ? "type" : "mimeType",
                        runtime.driver() == AiDriver.OPENAI ? "input_image" : runtime.driver() == AiDriver.ANTHROPIC ? "image" : "image/png")).hasSize(1);
            }
            assertThat(effects).hasValue(2);
            assertThat(server.getRequestCount()).isEqualTo(6);
        });
    }

    static ProviderConnectionRuntime modelDefaults(ProviderConnectionRuntime runtime, ToolCallback callback) {
        var options = ((org.springframework.ai.model.tool.ToolCallingChatOptions) runtime.requireChatModel().getDefaultOptions())
                .mutate().toolCallbacks(callback).build();
        org.springframework.ai.chat.model.ChatModel model;
        if (options instanceof org.springframework.ai.openai.responses.OpenAiResponsesChatOptions typed)
            model = org.springframework.ai.openai.responses.OpenAiResponsesChatModel.builder().options(typed).build();
        else if (options instanceof org.springframework.ai.anthropic.AnthropicChatOptions typed)
            model = org.springframework.ai.anthropic.AnthropicChatModel.builder().options(typed).build();
        else model = org.springframework.ai.google.genai.GoogleGenAiChatModel.builder()
                .genAiClient(com.google.genai.Client.builder().apiKey("loopback-fixture").httpOptions(
                        SpringAiProviderIntegration.oneAttemptGoogleHttpOptions(null)).build())
                .options((org.springframework.ai.google.genai.GoogleGenAiChatOptions) options).build();
        assertThat(((org.springframework.ai.model.tool.ToolCallingChatOptions) model.getDefaultOptions()).getToolCallbacks()).containsExactly(callback);
        return ProviderConnectionRuntime.chat(model, runtime.driver(), runtime.attemptOwnership(), runtime.retryPolicy(), runtime.failureTranslator());
    }
    static String bad(String mode) { return mode.equals("linter") ? BAD_LINTER : mode.equals("evidence") ? BAD_EVIDENCE : BAD_SCHEMA; }
    static CanonicalSchema schema(AiDriver driver) {
        return contract(driver, "name");
    }
    static ToolCallback callback(AtomicInteger effects) {
        return FunctionToolCallback.<Map<String,Object>,String>builder("test-lookup", input -> "SENTINEL_COMPLETED_WORK_" + effects.incrementAndGet())
                .description("fixture").inputType(Map.class).inputSchema("{\"type\":\"object\"}").build();
    }
    static ChatClient repairClient(ProviderConnectionRuntime runtime, String identity, CanonicalSchema schema, String mode, ToolCallback callback, int retries) {
        var builder = ChatClient.builder(runtime.requireChatModel(), io.micrometer.observation.ObservationRegistry.NOOP, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(new CanonicalToolCallingManager(ToolCallingManager.builder().build())))
                .defaultOptions(options(runtime, identity, 2048, callback).mutate())
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, mock(ExecutionStateService.class), new ModelUsageExtractor(), mock(SessionUsageService.class)));
        if (!mode.equals("evidence") && !mode.equals("linter")) builder.defaultAdvisors(new OutputSchemaCallAdvisor("ordinary-fixture", schema,
                new OutputSchemaValidator(), new OutputSchemaPromptAugmentor(), retries, outcome -> {}));
        if (mode.equals("linter") || mode.equals("combined")) builder.defaultAdvisors(new LinterCallAdvisor("ordinary-fixture", "regex",
                Pattern.compile("(?s).*accepted.*"), "Include accepted", retries, outcome -> {}));
        if (mode.equals("evidence") || mode.equals("combined")) builder.defaultAdvisors(new EvidenceContractCallAdvisor("ordinary-fixture",
                EvidenceContract.compiled(Map.of("claim", new EvidenceExpressionParser().parse("test-unperformed")), Map.of("claim", "claim")),
                new EvidenceBackedOutputValidator(), retries, outcome -> {}, outcome -> {}, fact -> {}));
        return builder.build();
    }
    static ModelInteractionResult invoke(ChatClient client, ProviderConnectionRuntime runtime, String identity, CanonicalSchema schema, RenderedMissionInput input) {
        var binding = mock(ExecutionBinding.class);
        var branch = mock(PhysicalBranchContext.class);
        when(binding.session()).thenReturn(mock(LoomspanSession.class));
        when(binding.branch()).thenReturn(branch);
        when(branch.requireLeaf()).thenReturn(mock(ExecutionFrame.class));
        var mission = mock(MissionContext.class);
        when(binding.requireMission()).thenReturn(mission);
        when(mission.successfulDirectSkills()).thenReturn(Set.of());
        var trace = new ModelTraceContext(new ModelExecutionIdentity("fixture", "loopback", runtime.driver(), identity), "ordinary-fixture", "ordinary");
        return ExecutionBindingScope.supplyWith(binding, () -> new SpringAiModelInteraction(client, ModelInteractionMode.STEP_EXECUTION)
                .call(new ModelInteractionRequest("ORIGINAL_SYSTEM", input, trace, List.of(), false, schema)));
    }
    static RenderedMissionInput input(boolean attachment) {
        var attachments = attachment ? List.of(ai.loomspan.internal.runtime.attachment.OrdinaryAttachmentFixture.image(Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aY1EAAAAASUVORK5CYII="))) : List.<ai.loomspan.internal.runtime.attachment.LoomspanAttachment>of();
        return new RenderedMissionInput("ORIGINAL_USER", attachments, Map.of());
    }
    static void assertRepair(JsonNode body, AiDriver driver, String candidate) {
        assertThat(texts(body)).contains("ORIGINAL_SYSTEM", "ORIGINAL_USER", candidate);
        assertNoTools(body);
        assertExchange(body, driver, "one", 1);
    }
    static void assertNoTools(JsonNode body) { assertThat(body.path("tools").isMissingNode() || body.path("tools").isEmpty()).isTrue(); }
    static void assertExchange(JsonNode body, AiDriver driver, String id, int count) {
        if (driver == AiDriver.OPENAI) {
            assertThat(find(body.path("input"), "type", "function_call")).hasSize(count);
            var calls = find(body.path("input"), "call_id", id);
            assertThat(calls).hasSize(2);
            assertThat(calls.stream().filter(node -> node.path("type").asText().equals("function_call"))).hasSize(1);
            assertThat(calls.stream().filter(node -> node.path("type").asText().equals("function_call_output"))).hasSize(1);
        } else if (driver == AiDriver.ANTHROPIC) {
            assertThat(find(body.path("messages"), "type", "tool_use")).hasSize(count);
            assertThat(find(body.path("messages"), "id", id)).hasSize(1);
            assertThat(find(body.path("messages"), "tool_use_id", id)).hasSize(1);
        } else {
            assertThat(findKeys(body.path("contents"), "functionCall")).hasSize(count);
            assertThat(findKeys(body.path("contents"), "functionResponse")).hasSize(count);
        }
        assertThat(texts(body).split("SENTINEL_COMPLETED_WORK_1", -1)).hasSize(2);
        if (count == 2) assertThat(texts(body).split("SENTINEL_COMPLETED_WORK_2", -1)).hasSize(2);
    }
    static List<JsonNode> find(JsonNode node, String field, String value) {
        var found = new ArrayList<JsonNode>();
        if (node.isObject() && node.path(field).asText().equals(value)) found.add(node);
        node.forEach(child -> { if ((child.isArray() || child.isObject())) found.addAll(find(child, field, value)); });
        return found;
    }
    static List<JsonNode> findKeys(JsonNode node, String field) {
        var found = new ArrayList<JsonNode>();
        if (node.isObject() && node.has(field)) found.add(node.path(field));
        node.forEach(child -> { if ((child.isArray() || child.isObject())) found.addAll(findKeys(child, field)); });
        return found;
    }
    static String texts(JsonNode node) {
        var result = new StringBuilder();
        if (node.isString()) result.append(node.asText()).append('\n');
        else node.forEach(child -> result.append(texts(child)));
        return result.toString();
    }
    static void assertRich(JsonNode body, AiDriver driver) {
        if (driver == AiDriver.OPENAI) {
            assertThat(find(body, "id", "rs_one")).hasSize(1);
            assertThat(find(body, "id", "rs_two")).hasSize(1);
            assertThat(find(body, "encrypted_content", "opaque-one")).hasSize(1);
            assertThat(find(body, "encrypted_content", "opaque-two")).hasSize(1);
        } else if (driver == AiDriver.ANTHROPIC) {
            assertThat(find(body, "signature", "signed-one")).hasSize(1);
            assertThat(find(body, "signature", "signed-two")).hasSize(1);
        } else {
            assertThat(find(body, "thoughtSignature", "c2lnbmVkLW9uZQ==")).hasSize(1);
            assertThat(find(body, "thoughtSignature", "c2lnbmVkLXR3bw==")).hasSize(1);
        }
    }
    static MockResponse richText(AiDriver driver, String candidate, int index) {
        String marker = "candidate-signature-" + index;
        if (driver == AiDriver.OPENAI) return ResponsesFixtures.response("raw" + index, "fixture", List.of(
                Map.of("id", "rs_raw" + index, "type", "reasoning", "encrypted_content", marker,
                        "summary", List.of(Map.of("type", "summary_text", "text", "candidate reasoning"))),
                ResponsesFixtures.textItem(candidate)));
        if (driver == AiDriver.ANTHROPIC) return response(Map.of("id", "raw" + index, "type", "message", "role", "assistant", "model", "fixture",
                "content", List.of(Map.of("type", "thinking", "thinking", "candidate reasoning", "signature", marker),
                        Map.of("type", "text", "text", candidate)), "stop_reason", "end_turn", "usage", Map.of("input_tokens", 1, "output_tokens", 1)));
        return response(Map.of("modelVersion", "fixture", "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(
                Map.of("thought", true, "text", marker, "thoughtSignature", Base64.getEncoder().encodeToString(marker.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                Map.of("text", candidate))), "finishReason", "STOP")),
                "usageMetadata", Map.of("promptTokenCount", 1, "candidatesTokenCount", 1, "totalTokenCount", 2)));
    }
    static MockResponse tool(AiDriver driver, String id, boolean rich) {
        if (driver == AiDriver.OPENAI) {
            var parts = new ArrayList<Object>();
            if (rich) parts.add(Map.of("id", "rs_" + id, "type", "reasoning", "encrypted_content", "opaque-" + id,
                    "summary", List.of(Map.of("type", "summary_text", "text", "reason-" + id))));
            parts.add(ResponsesFixtures.functionCall("test-lookup", id, "{\"value\":\"x\"}"));
            return ResponsesFixtures.response("fixture", "fixture", parts);
        }
        if (driver == AiDriver.ANTHROPIC) {
            var parts = new ArrayList<Object>();
            if (rich) parts.add(Map.of("type", "thinking", "thinking", "reason-" + id, "signature", "signed-" + id));
            parts.add(Map.of("type", "tool_use", "id", id, "name", "test-lookup", "input", Map.of("value", "x")));
            return response(Map.of("id", "fixture", "type", "message", "role", "assistant", "model", "fixture", "content", parts,
                    "stop_reason", "tool_use", "usage", Map.of("input_tokens", 1, "output_tokens", 1)));
        }
        var part = new LinkedHashMap<String,Object>();
        part.put("functionCall", Map.of("name", "test-lookup", "args", Map.of("value", "x")));
        if (rich) part.put("thoughtSignature", Base64.getEncoder().encodeToString(("signed-" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return response(Map.of("modelVersion", "fixture", "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(part)), "finishReason", "STOP")),
                "usageMetadata", Map.of("promptTokenCount", 1, "candidatesTokenCount", 1, "totalTokenCount", 2)));
    }
    static MockResponse response(Object body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(body)); }
}







