package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor;
import ai.loomspan.internal.schema.CanonicalSchema;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/** Per-request formatting at the existing provider boundary. Never changes the canonical contract. */
public final class NativeOutputContract
{
    public static final String PREPARED_SCHEMA_KEY = "loomspan.nativeSchema.prepared";
    private static final ObjectMapper JSON = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree();

    /* Claude Messages output_config.format, docs retrieved 2026-11-13:
     * https://platform.claude.com/docs/en/build-with-claude/structured-outputs
     * JSON Schema limitations: these assertions are rejected (HTTP 400).
     * Only OUTPUT/PLANNING uses these omissions. Non-strict tool schemas are untouched.
     * minItems is rejected except 0/1; no format omission is established.
     * Spring AI 2.1.0-RC2 / Anthropic SDK 3.4.0. Local/wire evidence:
     * NativeOutputContractTest and OrdinaryNativeOutputIntegrationTest.
     */
    private static final List<String> ANTHROPIC_REJECTED_ASSERTIONS =
            List.of("minimum", "maximum", "minLength", "maxLength", "maxItems");

    private NativeOutputContract() {}

    public static ChatClientRequest prepare(ChatClientRequest request, AiDriver driver, ModelTraceContext trace)
    {
        Object value = request.context().get(OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY);
        if (!(value instanceof CanonicalSchema contract)) return request;
        JsonNode prepared = contract.providerTree();
        adapt(prepared, driver, "$", trace);
        ChatOptions options = request.prompt().getOptions();
        ChatOptions formatted = switch (driver)
        {
            case OPENAI -> {
                if (!(options instanceof OpenAiResponsesChatOptions typed)) throw missingOptions(driver, trace);
                yield typed.mutate().outputSchema(JSON.writeValueAsString(prepared)).build();
            }
            case ANTHROPIC -> {
                if (!(options instanceof AnthropicChatOptions typed)) throw missingOptions(driver, trace);
                Map<String, Object> schema = JSON.convertValue(prepared, new TypeReference<>() {});
                yield AnthropicNativeOptions.withOutputSchema(typed, schema);
            }
            case GEMINI -> {
                if (!(options instanceof GoogleGenAiChatOptions typed)) throw missingOptions(driver, trace);
                yield typed.mutate().outputSchema(JSON.writeValueAsString(prepared)).build();
            }
            case TYPESAFE -> throw new IllegalArgumentException("Native evaluations do not accept chat output schemas");
        };
        return request.mutate().prompt(request.prompt().mutate().chatOptions(formatted).build())
                .context(PREPARED_SCHEMA_KEY, prepared).build();
    }

    private static void adapt(JsonNode node, AiDriver driver, String path, ModelTraceContext trace)
    {
        if (!(node instanceof ObjectNode object)) return;
        if (hasType(node, "object"))
        {
            if ((driver == AiDriver.OPENAI || driver == AiDriver.ANTHROPIC)
                    && !(node.path("additionalProperties").isBoolean() && !node.path("additionalProperties").booleanValue()))
                throw incompatible(driver, trace, path + "/additionalProperties", "native output requires additionalProperties:false");
            if (driver == AiDriver.OPENAI)
            {
                JsonNode properties = node.path("properties");
                JsonNode required = node.path("required");
                for (String name : properties.propertyNames())
                {
                    boolean present = required.isArray() && required.valueStream().anyMatch(item -> name.equals(item.asText()));
                    if (!present) throw incompatible(driver, trace, path + "/properties/" + pointer(name),
                            "shared strict output mapping requires every declared property to be required");
                }
            }
        }
        if (driver == AiDriver.ANTHROPIC)
        {
            ANTHROPIC_REJECTED_ASSERTIONS.forEach(object::remove);
            JsonNode minItems = node.get("minItems");
            if (minItems != null && minItems.decimalValue().compareTo(java.math.BigDecimal.ZERO) != 0
                    && minItems.decimalValue().compareTo(java.math.BigDecimal.ONE) != 0) object.remove("minItems");
        }
        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject())
            properties.properties().forEach(entry -> adapt(entry.getValue(), driver,
                    path + "/properties/" + pointer(entry.getKey()), trace));
        if (node.has("items")) adapt(node.get("items"), driver, path + "/items", trace);
        if (node.path("additionalProperties").isObject())
            adapt(node.get("additionalProperties"), driver, path + "/additionalProperties", trace);
    }

    private static boolean hasType(JsonNode node, String type)
    {
        JsonNode types = node.path("type");
        return types.isString() && type.equals(types.stringValue())
                || types.isArray() && types.valueStream().anyMatch(item -> type.equals(item.stringValue()));
    }

    private static String pointer(String name) { return name.replace("~", "~0").replace("/", "~1"); }

    private static IllegalArgumentException incompatible(AiDriver driver, ModelTraceContext trace, String path, String reason)
    {
        return new IllegalArgumentException("Skill '" + trace.skillName() + "' native output incompatibility: driver="
                + driver.name().toLowerCase(java.util.Locale.ROOT) + ", model=" + trace.identity().providerModel()
                + ", schema=" + path + ": " + reason);
    }

    private static IllegalStateException missingOptions(AiDriver driver, ModelTraceContext trace)
    {
        return new IllegalStateException("Skill '" + trace.skillName() + "' requires typed native output options for driver="
                + driver + ", model=" + trace.identity().providerModel());
    }
}
