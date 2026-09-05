package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.skill.YamlSkillManifest;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OutputSchemaPromptAugmentor
{
    public Prompt augment(Prompt prompt, YamlSkillManifest.OutputSchemaManifest schema)
    {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        String guidance = """
                Return JSON only.
                Do not include markdown fences, commentary, or prose.
                Use the configured field names exactly.
                Omit unknown fields unless they are explicitly allowed.

                %s
                """.formatted(renderContract(schema)).stripTrailing();

        return prompt.augmentSystemMessage(systemMessage -> systemMessage.mutate()
                .text(joinSystemText(systemMessage.getText(), guidance))
                .build());
    }

    public String renderContract(YamlSkillManifest.OutputSchemaManifest schema)
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
        renderNode(builder, schema, "$", 0, null);
        return builder.toString().stripTrailing();
    }

    private void renderNode(StringBuilder builder,
            YamlSkillManifest.OutputSchemaManifest schema,
            String path,
            int depth,
            String presence)
    {
        builder.append("  ".repeat(depth)).append(path).append(" — ").append(schema.getType());
        if (presence != null)
        {
            builder.append(", ").append(presence);
        }
        builder.append(Boolean.TRUE.equals(schema.getNullable()) ? ", nullable" : ", non-null");
        if ("object".equals(schema.getType()))
        {
            builder.append(", additionalProperties=")
                    .append(Boolean.TRUE.equals(schema.getAdditionalProperties()));
        }
        if (!schema.getEnumValues().isEmpty())
        {
            builder.append(", enum=[")
                    .append(schema.getEnumValues().stream()
                            .map(OutputSchemaPath::jsonString)
                            .collect(Collectors.joining(", ")))
                    .append(']');
        }
        if (StringUtils.hasText(schema.getFormat()))
        {
            builder.append(", format=").append(schema.getFormat());
        }
        if (StringUtils.hasText(schema.getDescription()))
        {
            builder.append(" - ").append(schema.getDescription());
        }
        builder.append('\n');

        if ("object".equals(schema.getType()))
        {
            for (Map.Entry<String, YamlSkillManifest.OutputSchemaManifest> entry : schema.getProperties().entrySet())
            {
                String childPresence = schema.getRequired().contains(entry.getKey()) ? "required" : "optional";
                renderNode(builder, entry.getValue(), OutputSchemaPath.property(path, entry.getKey()), depth + 1, childPresence);
            }
        }
        else if ("array".equals(schema.getType()) && schema.getItems() != null)
        {
            renderNode(builder, schema.getItems(), path + "[]", depth + 1, null);
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
