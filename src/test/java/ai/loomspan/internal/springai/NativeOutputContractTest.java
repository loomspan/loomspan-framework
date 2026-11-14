package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class NativeOutputContractTest
{
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
              "minimum":{"type":"number","minimum":1,"maximum":4},
              "format":{"type":"string","format":"email","minLength":2,"maxLength":8},
              "minItems":{"type":"array","minItems":0,"maxItems":8,"items":{"type":"integer","minimum":2}},
              "maxItems":{"type":"array","minItems":1,"items":{"type":"array","minItems":2,"maxItems":4,"items":{"type":"string","minLength":1}}},
              "nested":{"type":["object","null"],"additionalProperties":false,"properties":{"x":{"type":"string","enum":["a","b"]}},"required":["x"]}},
             "required":["minimum","format","minItems","maxItems","nested"]}
            """;

    @Test void anthropicOnlyOmitsEvidencedAssertionsAtSchemaNodesAndRetainsLocalEnforcement()
    {
        var schema = compile(SCHEMA);
        JsonNode canonical = schema.tree();
        var defaults = AnthropicChatOptions.builder().model("fixture").maxTokens(1024).build();
        var prepared = prepare(schema, AiDriver.ANTHROPIC, defaults);
        JsonNode wire = preparedSchema(prepared);
        assertThat(wire.path("properties").propertyNames()).containsExactly("minimum", "format", "minItems", "maxItems", "nested");
        assertThat(wire.at("/properties/minimum").has("minimum")).isFalse();
        assertThat(wire.at("/properties/minimum").has("maximum")).isFalse();
        assertThat(wire.at("/properties/format").has("minLength")).isFalse();
        assertThat(wire.at("/properties/format").has("maxLength")).isFalse();
        assertThat(wire.at("/properties/format/format").asText()).isEqualTo("email");
        assertThat(wire.at("/properties/minItems/minItems").asInt()).isZero();
        assertThat(wire.at("/properties/maxItems/minItems").asInt()).isOne();
        assertThat(wire.at("/properties/maxItems/items").has("minItems")).isFalse();
        assertThat(wire.at("/properties/maxItems/items").has("maxItems")).isFalse();
        assertThat(wire.at("/properties/maxItems/items/items").has("minLength")).isFalse();
        assertThat(wire.at("/properties/nested")).isEqualTo(canonical.at("/properties/nested"));
        assertThat(schema.tree()).isEqualTo(canonical);
        assertThat(defaults.getOutputConfig()).isNull();
        for (String declaration : List.of(
                "{\"type\":\"number\",\"minimum\":1}", "{\"type\":\"number\",\"maximum\":1}",
                "{\"type\":\"string\",\"minLength\":2}", "{\"type\":\"string\",\"maxLength\":1}",
                "{\"type\":\"array\",\"minItems\":2,\"items\":{\"type\":\"string\"}}",
                "{\"type\":\"array\",\"maxItems\":1,\"items\":{\"type\":\"string\"}}"))
        {
            var contract = compile("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"x\":" + declaration + "},\"required\":[\"x\"]}");
            var tree = contract.tree();
            prepare(contract, AiDriver.ANTHROPIC, defaults);
            String bad = declaration.contains("minimum") ? "0" : declaration.contains("maximum") ? "2"
                    : declaration.contains("minLength") ? "\"a\"" : declaration.contains("maxLength") ? "\"ab\""
                    : declaration.contains("minItems") ? "[]" : "[\"a\",\"b\"]";
            assertThat(contract.validate(json("{\"x\":" + bad + "}"))).as(declaration).isNotEmpty();
            assertThat(contract.tree()).isEqualTo(tree);
        }
    }

    @Test void sharedOpenAiAndGeminiKeepApprovedAssertionsAndCanonicalIsIsolated() throws Exception
    {
        var contract = compile(SCHEMA);
        JsonNode expected = contract.providerTree();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor())
        {
            var tasks = java.util.stream.IntStream.range(0, 24).mapToObj(index -> pool.submit(() -> {
                AiDriver driver = index % 3 == 0 ? AiDriver.ANTHROPIC : index % 3 == 1 ? AiDriver.OPENAI : AiDriver.GEMINI;
                ChatOptions options = switch (driver) {
                    case OPENAI -> OpenAiResponsesChatOptions.builder().model("same").build();
                    case ANTHROPIC -> AnthropicChatOptions.builder().model("same").build();
                    default -> GoogleGenAiChatOptions.builder().model("same").build();
                };
                return Map.entry(driver, preparedSchema(prepare(contract, driver, options)));
            })).toList();
            for (var task : tasks)
            {
                var entry = task.get(5, TimeUnit.SECONDS);
                if (entry.getKey() != AiDriver.ANTHROPIC) assertThat(entry.getValue()).isEqualTo(expected);
            }
        }
        assertThat(contract.providerTree()).isEqualTo(expected);
    }

    @ParameterizedTest @ValueSource(strings = {"", ",\"additionalProperties\":true"})
    void openObjectsFailWithoutCanonicalRewrite(String openness)
    {
        for (AiDriver driver : List.of(AiDriver.OPENAI, AiDriver.ANTHROPIC))
        {
            for (String schema : List.of("{\"type\":\"object\"" + openness + "}",
                    "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"a/b\":{\"type\":\"object\"" + openness + "}},\"required\":[\"a/b\"]}"))
            {
                var canonical = compile(schema);
                assertThatThrownBy(() -> prepare(canonical, driver, ChatOptions.builder().build()))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("skill").hasMessageContaining(driver.name().toLowerCase())
                        .hasMessageContaining("model=fixture").hasMessageContaining("additionalProperties");
                assertThat(canonical.tree()).isEqualTo(json(schema));
            }
        }
    }

    @Test void optionalOpenAiPropertyFailsWhileAnthropicPreservesAbsenceAndNullability()
    {
        var schema = compile("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"maybe\":{\"type\":[\"string\",\"null\"]}}}");
        assertThatThrownBy(() -> prepare(schema, AiDriver.OPENAI, OpenAiResponsesChatOptions.builder().model("fixture").build()))
                .hasMessageContaining("$/properties/maybe").hasMessageContaining("every declared property");
        assertThat(preparedSchema(prepare(schema, AiDriver.ANTHROPIC, AnthropicChatOptions.builder().model("fixture").build())))
                .isEqualTo(schema.providerTree());
    }

    @Test void noContractLeavesTextOptionsUnchangedAndUnsupportedOptionTypeFailsClearly()
    {
        var request = ChatClientRequest.builder().prompt(new Prompt("text")).build();
        assertThat(NativeOutputContract.prepare(request, AiDriver.OPENAI, trace(AiDriver.OPENAI))).isSameAs(request);
        assertThatThrownBy(() -> prepare(compile("{\"type\":\"object\",\"additionalProperties\":false}"),
                AiDriver.OPENAI, ChatOptions.builder().build())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("typed native output options");
    }

    @Test void everyStandardFormatIsRetainedWithoutBlanketFiltering()
    {
        for (String format : StandardSchemaCompiler.FORMATS) {
            var schema = compile("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"value\":{\"type\":\"string\",\"format\":\"" + format + "\"}},\"required\":[\"value\"]}");
            assertThat(preparedSchema(prepare(schema, AiDriver.OPENAI, OpenAiResponsesChatOptions.builder().model("fixture").build())))
                    .isEqualTo(schema.providerTree());
            assertThat(preparedSchema(prepare(schema, AiDriver.ANTHROPIC, AnthropicChatOptions.builder().model("fixture").build())))
                    .isEqualTo(schema.providerTree());
            assertThat(preparedSchema(prepare(schema, AiDriver.GEMINI, GoogleGenAiChatOptions.builder().model("fixture").build())))
                    .isEqualTo(schema.providerTree());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0", "1", "2", "4294967296", "4294967297"})
    void anthropicMinItemsRestrictionUsesExactValueRatherThanNarrowedInteger(String count)
    {
        var schema = compile("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"value\":{\"type\":\"array\",\"minItems\":" + count + ",\"items\":{\"type\":\"string\"}}}}");
        JsonNode prepared = preparedSchema(prepare(schema, AiDriver.ANTHROPIC, AnthropicChatOptions.builder().model("fixture").build()));
        assertThat(prepared.at("/properties/value").has("minItems")).isEqualTo(count.equals("0") || count.equals("1"));
    }

    @Test void outputPreparationPreservesNonStrictToolSchemaAndCallbackDefaults()
    {
        var callback = org.springframework.ai.tool.function.FunctionToolCallback.<Map<String,Object>,String>builder("test-lookup", value -> "unused")
                .description("fixture").inputType(Map.class).inputSchema(SCHEMA).build();
        for (AiDriver driver : List.of(AiDriver.OPENAI, AiDriver.ANTHROPIC, AiDriver.GEMINI)) {
            ChatOptions defaults = switch (driver) {
                case OPENAI -> OpenAiResponsesChatOptions.builder().model("fixture").strict(false).toolCallbacks(callback).build();
                case ANTHROPIC -> AnthropicChatOptions.builder().model("fixture").toolCallbacks(callback).build();
                default -> GoogleGenAiChatOptions.builder().model("fixture").toolCallbacks(callback).build();
            };
            var prepared = prepare(compile(SCHEMA), driver, defaults);
            var callbacks = ((org.springframework.ai.model.tool.ToolCallingChatOptions)prepared.prompt().getOptions()).getToolCallbacks();
            assertThat(callbacks).containsExactly(callback);
            assertThat(callbacks.getFirst().getToolDefinition().inputSchema()).isEqualTo(SCHEMA);
            assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(SCHEMA);
            if (prepared.prompt().getOptions() instanceof OpenAiResponsesChatOptions options) assertThat(options.getStrict()).isFalse();
        }
    }

    private static CanonicalSchema compile(String schema) {
        return new StandardSchemaCompiler().compile(schema, StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.AUTHORED);
    }
    private static JsonNode json(String source) { return ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree().readTree(source); }
    private static ModelTraceContext trace(AiDriver driver) { return new ModelTraceContext(new ModelExecutionIdentity("fixture", "test", driver, "fixture"), "skill", "ordinary"); }
    private static ChatClientRequest prepare(CanonicalSchema schema, AiDriver driver, ChatOptions options) {
        return NativeOutputContract.prepare(ChatClientRequest.builder().prompt(new Prompt("generate", options))
                .context(OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY, schema).build(), driver, trace(driver));
    }
    private static JsonNode preparedSchema(ChatClientRequest request) { return (JsonNode) request.context().get(NativeOutputContract.PREPARED_SCHEMA_KEY); }
}
