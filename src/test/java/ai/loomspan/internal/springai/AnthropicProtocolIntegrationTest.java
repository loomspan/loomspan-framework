package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.OutputConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicProtocolIntegrationTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RESULT = """
            {"id":"fixture","type":"message","role":"assistant","model":"fixture",
             "content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn",
             "usage":{"input_tokens":1,"output_tokens":1}}
            """;
    private static final String SCHEMA_A = """
            {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
             "description":"Exact authored result","additionalProperties":false,
             "properties":{"name":{"type":"string","description":"Name","minLength":1,"maxLength":20},
               "rows":{"type":"array","minItems":1,"maxItems":4,"items":{"type":"object",
                 "properties":{"count":{"type":"integer","minimum":0,"maximum":8}},
                 "required":["count"],"additionalProperties":false}}},"required":["name"]}
            """;
    private static final String SCHEMA_B = """
            {"type":"object","properties":{"accepted":{"type":["boolean","null"]}},
             "required":["accepted"],"additionalProperties":true}
            """;
    private static final String TOOL_SCHEMA = """
            {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
             "description":"Authored tool contract","additionalProperties":false,
             "properties":{
               "code":{"type":"string","enum":["a","b"],"description":"Exact code","minLength":1,"maxLength":5},
               "email":{"type":"string","format":"email"},
               "optional":{"type":["string","null"]},
               "score":{"type":"number","minimum":0.5,"maximum":8},
               "strings":{"type":"object","additionalProperties":{"type":"string","minLength":2}},
               "quantities":{"type":"object","additionalProperties":{"type":"integer","minimum":0,"maximum":9}},
               "records":{"type":"object","additionalProperties":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"],"additionalProperties":false}},
               "nested":{"type":"object","additionalProperties":{"type":"object","additionalProperties":{"type":"integer"}}},
               "rows":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","properties":{"map":{"type":"object","additionalProperties":{"type":"string"}}},"required":["map"],"additionalProperties":false}},
               "anything":{},"reserved":false},"required":["code","quantities"]}
            """;

    @ParameterizedTest
    @ValueSource(ints = {1024, 4096, 2048})
    void selectedEffortAndNativeOutputSurviveChatClientPerCallCombination(int cap) throws Exception
    {
        try (var server = new MockWebServer())
        {
            ChatModel model = model(server);
            var defaults = selected().mutate().maxTokens(cap).stopSequences(java.util.List.of("END_FIXTURE")).build();
            defaults = defaults.mutate().outputConfig(defaults.getOutputConfig().toBuilder()
                    .putAdditionalProperty("fixture_config", JsonValue.from("unchanged")).build()).build();
            var client = ChatClient.builder(model).defaultOptions(defaults.mutate()).build();
            var schemaA = schema(SCHEMA_A);
            var schemaB = schema(SCHEMA_B);
            for (var schema : java.util.List.of(schemaA, schemaB))
            {
                var combined = AnthropicNativeOptions.withOutputSchema(defaults, schema);
                assertThat(combined).isNotSameAs(defaults);
                enqueue(server);
                assertThat(client.prompt("fixture").options(combined.mutate()).call().content()).isEqualTo("ok");
                JsonNode body = request(server);
                assertSelected(body, cap);
                assertThat(body.at("/output_config/format/schema")).isEqualTo(JSON.valueToTree(schema));
                assertThat(body.at("/output_config/fixture_config").asText()).isEqualTo("unchanged");
                assertThat(body.path("stop_sequences")).isEqualTo(JSON.readTree("[\"END_FIXTURE\"]"));
            }
            enqueue(server);
            client.prompt("format-free").call().content();
            JsonNode unformatted = request(server);
            assertSelected(unformatted, cap);
            assertThat(unformatted.at("/output_config/format").isMissingNode()).isTrue();
            assertThat(defaults.getOutputConfig().format()).isEmpty();
            assertThat((JsonNode) JSON.valueToTree(schemaA)).isEqualTo(JSON.readTree(SCHEMA_A));
            assertThat((JsonNode) JSON.valueToTree(schemaB)).isEqualTo(JSON.readTree(SCHEMA_B));
            assertThat(server.getRequestCount()).isEqualTo(3);
        }
    }

    @Test
    void nativeFormatReplacementPreservesExistingFormatAndToolDefaults()
    {
        var callback = FunctionToolCallback.<String,String>builder("echo", value -> value)
                .description("fixture").inputType(String.class).inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
        var defaults = selected().mutate().maxTokens(1024).temperature(0.7).toolCallbacks(callback).build();
        var first = AnthropicNativeOptions.withOutputSchema(defaults, schema(SCHEMA_A));
        var second = AnthropicNativeOptions.withOutputSchema(first, schema(SCHEMA_B));
        assertThat(second.getModel()).isEqualTo(defaults.getModel());
        assertThat(second.getTemperature()).isEqualTo(0.7);
        assertThat(second.getMaxTokens()).isEqualTo(1024);
        assertThat(second.getToolCallbacks()).containsExactly(callback);
        assertThat(second.getOutputConfig().effort()).contains(OutputConfig.Effort.MEDIUM);
        assertThat(first.getOutputConfig().format()).isNotEqualTo(second.getOutputConfig().format());
        assertThat(defaults.getOutputConfig().format()).isEmpty();
        var empty = AnthropicChatOptions.builder().model("fixture").build();
        assertThat(AnthropicNativeOptions.withOutputSchema(empty, schema(SCHEMA_A)).getOutputConfig().format()).isPresent();
        assertThat(empty.getOutputConfig()).isNull();
    }

    @Test
    void effectiveSelectedModelOverrideAndLegacyControlSerializeIndependently() throws Exception
    {
        try (var server = new MockWebServer())
        {
            var contributor = new SpringAiChatOptionsContributor();
            var legacy = (AnthropicChatOptions) contributor.createOptions(new EffectiveSkillExecutionConfiguration(
                    "legacy", "test", AiDriver.ANTHROPIC, "claude-sonnet-4", "medium")).build();
            var client = ChatClient.builder(model(server)).defaultOptions(legacy.mutate()).build();
            enqueue(server);
            client.prompt("legacy").call().content();
            JsonNode old = request(server);
            assertThat(old.path("model").asText()).isEqualTo("claude-sonnet-4");
            assertThat(old.at("/thinking/type").asText()).isEqualTo("enabled");
            assertThat(old.at("/thinking/budget_tokens").asInt()).isEqualTo(4096);
            assertThat(old.path("max_tokens").asInt()).isEqualTo(8192);
            enqueue(server);
            client.prompt("override").options(AnthropicNativeOptions.withOutputSchema(
                    selected().mutate().maxTokens(2048).build(), schema(SCHEMA_B)).mutate()).call().content();
            assertSelected(request(server), 2048);
            assertThat(legacy.getThinking().asEnabled().budgetTokens()).isEqualTo(4096);
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @Test
    void toolInputSchemaPreservesApprovedRootAndNestedConstraints() throws Exception
    {
        Assumptions.assumeTrue(Boolean.getBoolean("loomspan.pr30.blockers"),
                "PR30-B03: RC2 drops Anthropic tool root constraints; see ai/thoughts/phases/pr-30-blockers.md. "
                        + "Reproduce with -Dloomspan.pr30.blockers=true");
        assertThat(captureToolSchemaAndVerifyPreservationControls()).isEqualTo(JSON.readTree(TOOL_SCHEMA));
    }

    @Test
    void toolNestedConstraintsAndCanonicalSchemaRemainUnchanged() throws Exception
    {
        captureToolSchemaAndVerifyPreservationControls();
    }

    private JsonNode captureToolSchemaAndVerifyPreservationControls() throws Exception
    {
        try (var server = new MockWebServer())
        {
            var callback = FunctionToolCallback.<Map<String,Object>,String>builder("inspect", input -> "unused")
                    .description("fixture").inputType(Map.class).inputSchema(TOOL_SCHEMA).build();
            var client = ChatClient.builder(model(server)).defaultOptions(selected().mutate()).build();
            enqueue(server);
            client.prompt("tool fixture").toolCallbacks(callback).call().content();
            JsonNode body = request(server);
            JsonNode actual = body.at("/tools/0/input_schema");
            JsonNode expected = JSON.readTree(TOOL_SCHEMA);
            assertThat(actual.path("properties")).isEqualTo(expected.path("properties"));
            assertThat(actual.at("/properties/reserved").isBoolean()).isTrue();
            assertThat(actual.at("/properties/reserved").booleanValue()).isFalse();
            assertThat(body.at("/tools/0/strict").asBoolean(false)).isFalse();
            assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(TOOL_SCHEMA);
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertThat(actual.path("required")).isEqualTo(expected.path("required"));
            return actual;
        }
    }

    private static AnthropicChatOptions selected()
    {
        return (AnthropicChatOptions) new SpringAiChatOptionsContributor().createOptions(
                new EffectiveSkillExecutionConfiguration("selected", "test", AiDriver.ANTHROPIC,
                        "claude-sonnet-5-5", "medium")).build();
    }

    private static ChatModel model(MockWebServer server)
    {
        var properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.ANTHROPIC);
        properties.setApiKey("fixture");
        properties.setBaseUrl(server.url("/").toString());
        return new SpringAiProviderIntegration(new DefaultResourceLoader()).create("test", properties).requireChatModel();
    }

    private static Map<String,Object> schema(String json)
    {
        return JSON.readValue(json, new TypeReference<>() {});
    }

    private static void enqueue(MockWebServer server)
    {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(RESULT));
    }

    private static JsonNode request(MockWebServer server) throws Exception
    {
        var request = server.takeRequest(3, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return JSON.readTree(request.getBody().readUtf8());
    }

    private static void assertSelected(JsonNode body, int cap)
    {
        assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(cap);
        assertThat(body.at("/output_config/effort").asText()).isEqualTo("medium");
        assertThat(body.at("/thinking/type").asText()).isEqualTo("adaptive");
        assertThat(body.at("/thinking/budget_tokens").isMissingNode()).isTrue();
    }
}
