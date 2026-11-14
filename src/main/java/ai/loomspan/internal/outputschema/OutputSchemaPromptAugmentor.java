package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.schema.CanonicalSchema;
import tools.jackson.databind.JsonNode;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;

public final class OutputSchemaPromptAugmentor
{
    public Prompt augment(Prompt prompt, CanonicalSchema schema)
    {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        String guidance = """
                Return JSON only.
                Do not include markdown fences, commentary, or prose.
                Use the configured field names exactly.

                %s
                """.formatted(renderContract(schema)).stripTrailing();

        return prompt.augmentSystemMessage(systemMessage -> systemMessage.mutate()
                .text(joinSystemText(systemMessage.getText(), guidance))
                .build());
    }

    public String renderContract(CanonicalSchema schema)
    {
        Objects.requireNonNull(schema, "schema must not be null");
        StringBuilder builder = new StringBuilder("""
                Property semantics:
                - required: the property must be present
                - optional: omit the property when its value is unknown
                - nullable: JSON null is allowed
                - non-null: JSON null is not allowed when the property is present

                Output contract:
                """);
        renderNode(builder, schema.tree(), "$", 0, null);
        return builder.toString().stripTrailing();
    }

    private void renderNode(StringBuilder builder, JsonNode schema, String path, int depth, String presence)
    {
        builder.append("  ".repeat(depth)).append(path).append(" — ");
        if (schema.isBoolean()) {
            builder.append(schema.booleanValue() ? "any value" : "framework-owned; omit").append('\n');
            return;
        }
        JsonNode type = schema.path("type");
        boolean nullable = type.isString() && "null".equals(type.asString()) || type.isArray()
                && java.util.stream.StreamSupport.stream(type.spliterator(), false).anyMatch(t -> "null".equals(t.asString()));
        String baseType = type.isArray() ? java.util.stream.StreamSupport.stream(type.spliterator(), false)
                .map(JsonNode::asString).filter(t -> !"null".equals(t)).collect(Collectors.joining(" or ")) : type.asString();
        builder.append(baseType);
        if (presence != null) builder.append(", ").append(presence);
        builder.append(nullable ? ", nullable" : ", non-null");
        if ("object".equals(baseType)) builder.append(", additionalProperties=")
                .append(!schema.has("additionalProperties") || !schema.path("additionalProperties").isBoolean()
                        || schema.path("additionalProperties").booleanValue());
        if (schema.has("enum")) builder.append(", enum=").append(schema.get("enum"));
        for (String keyword : java.util.List.of("format", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems"))
            if (schema.has(keyword)) builder.append(", ").append(keyword).append('=')
                    .append(schema.get(keyword).isString() ? schema.get(keyword).asString() : schema.get(keyword).toString());
        if (StringUtils.hasText(schema.path("description").asString("")))
            builder.append(" - ").append(schema.path("description").asString());
        builder.append('\n');
        if ("object".equals(baseType)) {
            for (var entry : schema.path("properties").properties()) {
                boolean required = java.util.stream.StreamSupport.stream(schema.path("required").spliterator(), false)
                        .anyMatch(value -> entry.getKey().equals(value.asString()));
                renderNode(builder, entry.getValue(), OutputSchemaPath.property(path, entry.getKey()), depth + 1,
                        required ? "required" : "optional");
            }
        } else if ("array".equals(baseType) && schema.has("items")) {
            renderNode(builder, schema.get("items"), path + "[]", depth + 1, null);
        }
    }

    private String joinSystemText(String original, String hint)
    {
        if (!StringUtils.hasText(original))
        {
            return hint;
        }
        return original + "\n\n" + hint;
    }
}
