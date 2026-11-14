package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.chat.ProviderAttemptCallAdvisor;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.model.*;
import ai.loomspan.internal.outputschema.*;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.testkit.ResponsesFixtures;
import com.google.genai.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real ordinary call/advisor and official adapter serialization against loopback. No hosted enforcement claim. */
class OrdinaryNativeOutputIntegrationTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> IDENTITIES = List.of("gpt-6.1-terra", "openai/gpt-6.1-terra",
            "muse-spark-1.3-contributor", "qwen3.5:4b-q4_K_M", "claude-sonnet-5-5", "gemini-3.8-flash");

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "openai/gpt-6.1-terra", "muse-spark-1.3-contributor",
            "qwen3.5:4b-q4_K_M", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void actualInitialAndCorrectionCarryNativeContractAndPreserveCaps(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var schema = contract(runtime.driver(), "name");
            var options = options(runtime, identity, 1024, null);
            var client = client(runtime, options, schema, 1, mock(ExecutionStateService.class));
            server.enqueue(text(runtime.driver(), "{\"name\":\"a\"}"));
            server.enqueue(text(runtime.driver(), "{\"name\":\"okay\"}"));
            assertThat(call(client, runtime, identity, schema).content()).isEqualTo("{\"name\":\"okay\"}");
            JsonNode first = request(server), second = request(server);
            for (JsonNode body : List.of(first, second)) {
                assertNative(body, runtime.driver(), identity, 1024, schema);
            }
            assertThat(second.toString()).contains("a");
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(schema.tree().at("/properties/name/minLength").asInt()).isEqualTo(2);
            assertNoFormat(options);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "openai/gpt-6.1-terra", "muse-spark-1.3-contributor",
            "qwen3.5:4b-q4_K_M", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void realToolContinuationRetainsContractEffortAnd4096Cap(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            AtomicInteger effects = new AtomicInteger();
            ToolCallback callback = FunctionToolCallback.<Map<String,Object>,String>builder("test-lookup", input -> {
                effects.incrementAndGet(); return "found";
            }).description("fixture").inputType(Map.class)
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}").build();
            var schema = contract(runtime.driver(), "name");
            var options = options(runtime, identity, 4096, callback);
            var client = client(runtime, options, schema, 1, mock(ExecutionStateService.class));
            server.enqueue(tool(runtime.driver()));
            server.enqueue(text(runtime.driver(), "{\"name\":\"okay\"}"));
            assertThat(call(client, runtime, identity, schema).content()).isEqualTo("{\"name\":\"okay\"}");
            assertNative(request(server), runtime.driver(), identity, 4096, schema);
            JsonNode continuation = request(server);
            assertNative(continuation, runtime.driver(), identity, 4096, schema);
            assertThat(continuation.toString()).contains("found");
            assertThat(effects).hasValue(1);
            assertThat(server.getRequestCount()).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void schemaTextSchemaCallsOnSharedModelDoNotLeakNativeFormatting(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var defaults = options(runtime, identity, 2048, null);
            for (String name : List.of("name", "", "label")) {
                var schema = name.isEmpty() ? null : contract(runtime.driver(), name);
                var client = client(runtime, defaults, schema, 0, mock(ExecutionStateService.class));
                server.enqueue(text(runtime.driver(), name.isEmpty() ? "plain" : "{\"" + name + "\":\"okay\"}"));
                assertThat(call(client, runtime, identity, schema).content()).isNotEmpty();
                JsonNode body = request(server);
                if (name.isEmpty()) assertThat(nativeSchema(body, runtime.driver()).isMissingNode()).isTrue();
                else assertNative(body, runtime.driver(), identity, 2048, schema);
            }
            assertNoFormat(defaults);
            assertThat(server.getRequestCount()).isEqualTo(3);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void explicitSchemaRejectionIsSentOnceWithoutCorrectionOrFallback(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var schema = contract(runtime.driver(), "name");
            var client = client(runtime, options(runtime, identity, 1024, null), schema, 2, mock(ExecutionStateService.class));
            server.enqueue(error(runtime.driver(), 400, "schema support rejected exact original detail"));
            assertThatThrownBy(() -> call(client, runtime, identity, schema))
                    .hasStackTraceContaining("schema support rejected exact original detail");
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertNative(request(server), runtime.driver(), identity, 1024, schema);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void transientTransportRetryPreservesNativeContractAndCorrectionBudget(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var schema = contract(runtime.driver(), "name");
            var client = client(runtime, options(runtime, identity, 1024, null), schema, 0, mock(ExecutionStateService.class));
            server.enqueue(error(runtime.driver(), 503, "temporary provider failure"));
            server.enqueue(text(runtime.driver(), "{\"name\":\"okay\"}"));
            assertThat(call(client, runtime, identity, schema).content()).isEqualTo("{\"name\":\"okay\"}");
            var first = request(server);
            var second = request(server);
            assertNative(first, runtime.driver(), identity, 1024, schema);
            assertThat(second).isEqualTo(first);
            assertThat(server.getRequestCount()).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void schemaRejectionAfterSuccessfulToolPreservesEarlierEffectAndNeverRepeatsIt(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var effects = new AtomicInteger();
            var callback = FunctionToolCallback.<Map<String,Object>,String>builder("test-lookup", input -> {
                effects.incrementAndGet(); return "completed effect";
            }).description("fixture").inputType(Map.class)
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}").build();
            var schema = contract(runtime.driver(), "name");
            var client = client(runtime, options(runtime, identity, 4096, callback), schema, 2, mock(ExecutionStateService.class));
            server.enqueue(tool(runtime.driver()));
            server.enqueue(error(runtime.driver(), 400, "continuation schema rejected after effect"));
            assertThatThrownBy(() -> call(client, runtime, identity, schema))
                    .hasStackTraceContaining("continuation schema rejected after effect");
            assertThat(effects).hasValue(1);
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertNative(request(server), runtime.driver(), identity, 4096, schema);
            JsonNode continuation = request(server);
            assertNative(continuation, runtime.driver(), identity, 4096, schema);
            assertThat(continuation.toString()).contains("completed effect");
        });
    }

    @Test void incompatibleAndInvalidContractsMakeNoAffectedRequest() throws Exception
    {
        for (String identity : List.of("gpt-6.1-terra", "claude-sonnet-5-5")) {
            local(identity, (server, runtime) -> {
                var schema = compile("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
                var client = client(runtime, options(runtime, identity, 1024, null), schema, 2, mock(ExecutionStateService.class));
                assertThatThrownBy(() -> call(client, runtime, identity, schema)).hasMessageContaining("incompatibility")
                        .hasMessageContaining("ordinary-fixture").hasMessageContaining(identity).hasMessageContaining("schema=$/");
                assertThatThrownBy(() -> compile("{\"type\":\"object\",\"properties\":{\"bad\":{\"type\":\"string\",\"format\":\"invented\"}}}"))
                        .isInstanceOfSatisfying(StandardSchemaCompiler.DeclarationException.class,
                                failure -> assertThat(failure.path()).isEqualTo("/properties/bad/format"));
                assertThat(server.getRequestCount()).isZero();
            });
        }
    }

    @Test void canonicalAndPreparedSchemaFactsRemainDistinctInExistingDiagnostics() throws Exception
    {
        local("claude-sonnet-5-5", (server, runtime) -> {
            var schema = contract(runtime.driver(), "name");
            var state = mock(ExecutionStateService.class);
            server.enqueue(text(runtime.driver(), "{\"name\":\"okay\"}"));
            call(client(runtime, options(runtime, "claude-sonnet-5-5", 1024, null), schema, 0, state), runtime, "claude-sonnet-5-5", schema);
            var capture = org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(state).recordModelRequestSent(any(), any(), any(), anyMap(), capture.capture());
            Map<?,?> payload = capture.getValue();
            assertThat((JsonNode) payload.get("canonicalGenerationSchema")).isEqualTo(schema.tree());
            assertThat(((JsonNode) payload.get("canonicalGenerationSchema")).at("/properties/name/minLength").asInt()).isEqualTo(2);
            assertThat(((JsonNode) payload.get("preparedNativeSchema")).at("/properties/name/minLength").isMissingNode()).isTrue();
        });
    }

    @Test void richAnthropicRequestOmitsOnlyEvidencedAssertionsAtSchemaNodes() throws Exception
    {
        local("claude-sonnet-5-5", (server, runtime) -> {
            var schema = compile("""
                    {"type":"object","additionalProperties":false,"properties":{
                      "minimum":{"type":"number","minimum":1,"maximum":5},
                      "format":{"type":"string","format":"email","minLength":2,"maxLength":20},
                      "rows":{"type":"array","minItems":2,"maxItems":4,"items":{
                        "type":"object","additionalProperties":false,"properties":{
                          "maxLength":{"type":"string","minLength":1,"maxLength":4},
                          "minimum":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"integer","minimum":0,"maximum":5}}},
                        "required":["maxLength","minimum"]}}},"required":["minimum","format","rows"]}
                    """);
            String candidate = "{\"minimum\":2,\"format\":\"a@example.com\",\"rows\":[{\"maxLength\":\"yes\",\"minimum\":[1]},{\"maxLength\":\"yes\",\"minimum\":[2]}]}";
            server.enqueue(text(runtime.driver(), candidate));
            call(client(runtime, options(runtime, "claude-sonnet-5-5", 1024, null), schema, 0, mock(ExecutionStateService.class)), runtime, "claude-sonnet-5-5", schema);
            JsonNode wire = nativeSchema(request(server), runtime.driver());
            assertThat(wire.at("/properties").propertyNames()).containsExactly("minimum", "format", "rows");
            assertThat(wire.at("/properties/minimum").has("minimum")).isFalse();
            assertThat(wire.at("/properties/minimum").has("maximum")).isFalse();
            assertThat(wire.at("/properties/format").has("minLength")).isFalse();
            assertThat(wire.at("/properties/format").has("maxLength")).isFalse();
            assertThat(wire.at("/properties/format/format").asText()).isEqualTo("email");
            assertThat(wire.at("/properties/rows").has("minItems")).isFalse();
            assertThat(wire.at("/properties/rows").has("maxItems")).isFalse();
            assertThat(wire.at("/properties/rows/items/properties").propertyNames()).containsExactly("maxLength", "minimum");
            assertThat(wire.at("/properties/rows/items/properties/minimum/minItems").asInt()).isEqualTo(1);
            assertThat(wire.at("/properties/rows/items/properties/minimum").has("maxItems")).isFalse();
            assertThat(wire.at("/properties/rows/items/properties/minimum/items").has("minimum")).isFalse();
            assertThat(wire.at("/properties/rows/items/properties/maxLength").has("maxLength")).isFalse();
            assertThat(schema.tree().at("/properties/rows/minItems").asInt()).isEqualTo(2);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void actualBindingProjectionIsTheNativeGenerationContractAndCompleteCompositionUsesOriginal(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var original = compile("{\"type\":\"object\",\"properties\":{\"bound\":{\"type\":\"string\"},\"label\":{\"type\":\"string\",\"minLength\":2}},\"required\":[\"bound\",\"label\"]"
                    + (runtime.driver() == AiDriver.GEMINI ? "}" : ",\"additionalProperties\":false}"));
            JsonNode snapshot = original.tree();
            var binding = new ai.loomspan.internal.runtime.input.ChildInputBinding(
                    ai.loomspan.internal.runtime.input.ObjectFieldPath.parse("/bound", false),
                    ai.loomspan.internal.runtime.input.ChildInputBinding.SourceKind.INPUT,
                    ai.loomspan.internal.runtime.input.ObjectFieldPath.parse("/source", true), null);
            var composition = new OutputBindingComposition(original, List.of(binding), Map.of("source", "framework exact value"),
                    List.of(), "owner", ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
            CanonicalSchema projected = composition.projection().schema();
            server.enqueue(text(runtime.driver(), "{\"label\":\"okay\"}"));
            String contribution = call(client(runtime, options(runtime, identity, 1024, null), projected, 0, mock(ExecutionStateService.class)),
                    runtime, identity, projected).content();
            JsonNode body = request(server);
            assertNative(body, runtime.driver(), identity, 1024, projected);
            assertThat(nativeSchema(body, runtime.driver()).path("properties").has("bound")).isFalse();
            assertThat(nativeSchema(body, runtime.driver()).path("required")).isEqualTo(JSON.readTree("[\"label\"]"));
            var accepted = composition.compose(contribution);
            assertThat(accepted.validation().valid()).isTrue();
            assertThat(JSON.readTree(accepted.content()).path("bound").asText()).isEqualTo("framework exact value");
            assertThat(original.tree()).isEqualTo(snapshot);
        });
    }

    private static ChatClient client(ProviderConnectionRuntime runtime, ChatOptions options, CanonicalSchema schema, int retries,
            ExecutionStateService state)
    {
        var builder = ChatClient.builder(runtime.requireChatModel(), io.micrometer.observation.ObservationRegistry.NOOP, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(new CanonicalToolCallingManager(ToolCallingManager.builder().build())))
                .defaultOptions(options.mutate())
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, state, new ModelUsageExtractor(), mock(SessionUsageService.class)));
        if (schema != null) builder.defaultAdvisors(new OutputSchemaCallAdvisor("ordinary-fixture", schema,
                new OutputSchemaValidator(), new OutputSchemaPromptAugmentor(), retries, outcome -> {}));
        return builder.build();
    }

    private static ModelInteractionResult call(ChatClient client, ProviderConnectionRuntime runtime, String identity, CanonicalSchema schema)
    {
        return call(client, runtime, identity, schema, new RenderedMissionInput("generate", List.of(), Map.of()));
    }

    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void attachmentRequestsAndCorrectionsRetainNativeContractAnd2048Cap(String identity) throws Exception
    {
        local(identity, (server, runtime) -> {
            var schema = contract(runtime.driver(), "name");
            var client = client(runtime, options(runtime, identity, 2048, null), schema, 1, mock(ExecutionStateService.class));
            byte[] bytes = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aY1EAAAAASUVORK5CYII=");
            var attachment = ai.loomspan.internal.runtime.attachment.OrdinaryAttachmentFixture.image(bytes);
            server.enqueue(text(runtime.driver(), "{\"name\":\"a\"}"));
            server.enqueue(text(runtime.driver(), "{\"name\":\"okay\"}"));
            assertThat(call(client, runtime, identity, schema, new RenderedMissionInput("generate", List.of(attachment), Map.of())).content())
                    .isEqualTo("{\"name\":\"okay\"}");
            assertNative(request(server), runtime.driver(), identity, 2048, schema);
            assertNative(request(server), runtime.driver(), identity, 2048, schema);
            assertThat(server.getRequestCount()).isEqualTo(2);
        });
    }

    private static ModelInteractionResult call(ChatClient client, ProviderConnectionRuntime runtime, String identity, CanonicalSchema schema,
            RenderedMissionInput input)
    {
        var session = mock(LoomspanSession.class);
        var frame = mock(ExecutionFrame.class);
        var branch = mock(PhysicalBranchContext.class);
        var binding = mock(ExecutionBinding.class);
        when(binding.session()).thenReturn(session);
        when(binding.branch()).thenReturn(branch);
        when(branch.requireLeaf()).thenReturn(frame);
        var trace = new ModelTraceContext(new ModelExecutionIdentity("fixture", "loopback", runtime.driver(), identity), "ordinary-fixture", "ordinary");
        return ExecutionBindingScope.supplyWith(binding, () -> new SpringAiModelInteraction(client, ModelInteractionMode.STEP_EXECUTION)
                .call(new ModelInteractionRequest("fixture", input, trace, List.of(), false, schema)));
    }

    private static ChatOptions options(ProviderConnectionRuntime runtime, String identity, int cap, ToolCallback callback)
    {
        var configured = new SpringAiChatOptionsContributor().createOptions(new EffectiveSkillExecutionConfiguration(
                "fixture", "loopback", runtime.driver(), identity, identity.startsWith("qwen") ? null : "medium"));
        var options = runtime.requireChatModel().getDefaultOptions().mutate().combineWith(configured).maxTokens(cap);
        if (options instanceof OpenAiResponsesChatOptions.Builder typed) {
            if (identity.startsWith("qwen")) typed.extraBody(Map.of("think", true));
            if (identity.startsWith("openai/")) typed.extraBody(Map.of("provider", Map.of("only", List.of("openai"), "allow_fallbacks", false, "require_parameters", true)));
            if (callback != null) typed.toolCallbacks(callback);
        } else if (options instanceof AnthropicChatOptions.Builder typed) {
            typed.stopSequences(List.of("END_FIXTURE"));
            if (callback != null) typed.toolCallbacks(callback);
        } else if (options instanceof GoogleGenAiChatOptions.Builder typed) {
            typed.temperature(0.2).stopSequences(List.of("END_FIXTURE"));
            if (callback != null) typed.toolCallbacks(callback);
        }
        return options.build();
    }

    private static CanonicalSchema contract(AiDriver driver, String name)
    {
        // Gemini's simple single-string adapter control is distinct from retained PR30-B04 raw schema cases.
        return compile("{\"type\":\"object\",\"properties\":{\"" + name + "\":{\"type\":\"string\",\"minLength\":2}},\"required\":[\"" + name + "\"]"
                + (driver == AiDriver.GEMINI ? "}" : ",\"additionalProperties\":false}"));
    }
    private static CanonicalSchema compile(String schema) {
        return new StandardSchemaCompiler().compile(schema, StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.AUTHORED);
    }
    private static void assertNative(JsonNode body, AiDriver driver, String identity, int cap, CanonicalSchema schema)
    {
        var expected = schema.providerTree();
        if (driver == AiDriver.ANTHROPIC) ((tools.jackson.databind.node.ObjectNode)expected.path("properties").valueStream().findFirst().orElseThrow()).remove("minLength");
        assertThat(nativeSchema(body, driver)).isEqualTo(expected);
        switch (driver) {
            case OPENAI -> {
                assertThat(body.at("/text/format/type").asText()).isEqualTo("json_schema");
                assertThat(body.at("/text/format/strict").asBoolean()).isTrue();
                assertThat(body.path("max_output_tokens").asInt()).isEqualTo(cap);
                if (identity.startsWith("qwen")) {
                    assertThat(body.path("think").asBoolean()).isTrue();
                    assertThat(body.has("reasoning")).isFalse();
                } else assertThat(body.at("/reasoning/effort").asText()).isEqualTo("medium");
                if (identity.startsWith("openai/")) {
                    assertThat(body.at("/provider/only/0").asText()).isEqualTo("openai");
                    assertThat(body.at("/provider/allow_fallbacks").asBoolean()).isFalse();
                    assertThat(body.at("/provider/require_parameters").asBoolean()).isTrue();
                }
            }
            case ANTHROPIC -> {
                assertThat(body.at("/output_config/format/type").asText()).isEqualTo("json_schema");
                assertThat(body.at("/output_config/effort").asText()).isEqualTo("medium");
                assertThat(body.path("max_tokens").asInt()).isEqualTo(cap);
                assertThat(body.at("/thinking/type").asText()).isEqualTo("adaptive");
                assertThat(body.path("stop_sequences")).isEqualTo(JSON.readTree("[\"END_FIXTURE\"]"));
            }
            case GEMINI -> {
                assertThat(body.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
                assertThat(body.at("/generationConfig/thinkingConfig/thinkingLevel").asText()).isEqualTo("MEDIUM");
                assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(cap);
                assertThat(body.at("/generationConfig/temperature").asDouble()).isEqualTo(0.2);
                assertThat(body.at("/generationConfig/stopSequences")).isEqualTo(JSON.readTree("[\"END_FIXTURE\"]"));
            }
            default -> throw new AssertionError(driver);
        }
        assertThat(body.path("model").asText(identity)).isEqualTo(identity);
    }
    private static JsonNode nativeSchema(JsonNode body, AiDriver driver) {
        return body.at(switch(driver) { case OPENAI -> "/text/format/schema"; case ANTHROPIC -> "/output_config/format/schema"; default -> "/generationConfig/responseJsonSchema"; });
    }
    private static void assertNoFormat(ChatOptions options) {
        if (options instanceof OpenAiResponsesChatOptions typed) assertThat(typed.getResponseFormat()).isNull();
        if (options instanceof AnthropicChatOptions typed) assertThat(typed.getOutputConfig().format()).isEmpty();
        if (options instanceof GoogleGenAiChatOptions typed) assertThat(typed.getOutputSchema()).isNull();
    }
    private static JsonNode request(MockWebServer server) throws Exception {
        var request = server.takeRequest(3, TimeUnit.SECONDS); assertThat(request).isNotNull();
        return JSON.readTree(request.getBody().readUtf8());
    }
    private static MockResponse text(AiDriver driver, String text) {
        if (driver == AiDriver.OPENAI) return ResponsesFixtures.text(text);
        if (driver == AiDriver.ANTHROPIC) return response(Map.of("id", "fixture", "type", "message", "role", "assistant", "model", "fixture",
                "content", List.of(Map.of("type", "text", "text", text)), "stop_reason", "end_turn", "usage", Map.of("input_tokens", 1, "output_tokens", 1)));
        return response(Map.of("modelVersion", "fixture", "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(Map.of("text", text))), "finishReason", "STOP")),
                "usageMetadata", Map.of("promptTokenCount", 1, "candidatesTokenCount", 1, "totalTokenCount", 2)));
    }
    private static MockResponse tool(AiDriver driver) {
        if (driver == AiDriver.OPENAI) return ResponsesFixtures.response("fixture", "fixture", List.of(ResponsesFixtures.functionCall("test-lookup", "one", "{\"value\":\"x\"}")));
        if (driver == AiDriver.ANTHROPIC) return response(Map.of("id", "fixture", "type", "message", "role", "assistant", "model", "fixture",
                "content", List.of(Map.of("type", "tool_use", "id", "one", "name", "test-lookup", "input", Map.of("value", "x"))), "stop_reason", "tool_use", "usage", Map.of("input_tokens", 1, "output_tokens", 1)));
        return response(Map.of("modelVersion", "fixture", "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(Map.of("functionCall", Map.of("name", "test-lookup", "args", Map.of("value", "x"))))), "finishReason", "STOP")),
                "usageMetadata", Map.of("promptTokenCount", 1, "candidatesTokenCount", 1, "totalTokenCount", 2)));
    }
    private static MockResponse error(AiDriver driver, int code, String detail) {
        String body = driver == AiDriver.GEMINI ? JSON.writeValueAsString(Map.of("error", Map.of("code", code, "message", detail, "status", code == 400 ? "INVALID_ARGUMENT" : "UNAVAILABLE")))
                : JSON.writeValueAsString(Map.of("type", "error", "error", Map.of("message", detail, "type", code == 400 ? "invalid_request_error" : "api_error")));
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }
    private static MockResponse response(Object body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(body)); }
    private static void local(String identity, LocalCase action) throws Exception {
        AiDriver driver = identity.startsWith("claude") ? AiDriver.ANTHROPIC : identity.startsWith("gemini") ? AiDriver.GEMINI : AiDriver.OPENAI;
        try (var server = new MockWebServer()) {
            String base = server.url("/").toString();
            if (driver == AiDriver.GEMINI) Client.setDefaultBaseUrls(Optional.of(base), Optional.of(base));
            try {
                var properties = new LoomspanProperties.ConnectionProperties();
                properties.setDriver(driver); properties.setApiKey("loopback-fixture"); properties.setBaseUrl(base);
                properties.getProviderRetry().setMaxAttempts(2);
                properties.getProviderRetry().setInitialBackoff(java.time.Duration.ZERO);
                properties.getProviderRetry().setMaxBackoff(java.time.Duration.ZERO);
                properties.getProviderRetry().setJitter(0);
                action.run(server, new SpringAiProviderIntegration(new DefaultResourceLoader()).create("loopback", properties));
            } finally { if (driver == AiDriver.GEMINI) Client.setDefaultBaseUrls(Optional.empty(), Optional.empty()); }
        }
    }
    @FunctionalInterface private interface LocalCase { void run(MockWebServer server, ProviderConnectionRuntime runtime) throws Exception; }
}

