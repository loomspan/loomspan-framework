package ai.loomspan.internal.runtime.input;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

public class SkillInputPromptRenderer
{
    public enum DetailLevel
    {
        COMPACT,
        VERBOSE
    }

    public String renderToolArgumentsExample(SkillInputContract contract, DetailLevel detailLevel)
    {
        if (contract == null || contract.isGeneric())
        {
            return "";
        }
        if (contract.schema().isObject()
                && contract.schema().properties().isEmpty()
                && contract.schema().additionalPropertiesSchema() == null
                && !contract.schema().allowsAdditionalProperties())
        {
            StringBuilder builder = new StringBuilder("{}\n(Note: This tool takes no arguments. You must pass an empty object.)");
            appendDescriptions(builder, contract.schema());
            return builder.toString();
        }
        StringBuilder builder = new StringBuilder("Illustrative declared structure (optional fields may be omitted; permitted fields are not exhaustive in open objects):\n");
        builder.append(renderValue(contract.schema(), 0));
        builder.append("\nObject rules apply only at the stated location when that object is supplied. "
                + "Required child fields do not require an optional parent. "
                + "Populate permitted fields with actual data required by the skill instructions; openness does not require copying all context. "
                + "`<key>` illustrates an additional key, not a required field.");
        appendRules(builder, contract.schema(), "$", detailLevel);
        appendDescriptions(builder, contract.schema());
        return builder.toString();
    }

    private void appendDescriptions(StringBuilder builder, SkillInputSchemaNode schema)
    {
        StringBuilder descriptions = new StringBuilder();
        collectDescriptions(descriptions, schema, "$");
        if (!descriptions.isEmpty())
        {
            builder.append("\nAuthored descriptions (paths and text are JSON-quoted; escapes preserve literal content). "
                    + "Descriptions explain meaning; they do not add validation rules, defaults, data bindings, "
                    + "or permission to invent absent values.").append(descriptions);
        }
    }

    private void collectDescriptions(StringBuilder descriptions, SkillInputSchemaNode schema, String path)
    {
        if (schema.description() != null && !schema.description().isBlank())
        {
            descriptions.append("\nDescription at ").append(quote(path)).append(": ").append(quote(schema.description()));
        }
        if (schema.isObject())
        {
            for (var entry : new TreeMap<>(schema.properties()).entrySet())
            {
                collectDescriptions(descriptions, entry.getValue(), propertyPath(path, entry.getKey()));
            }
            if (schema.additionalPropertiesSchema() != null)
            {
                collectDescriptions(descriptions, schema.additionalPropertiesSchema(), path + ".*");
            }
        }
        if (schema.isArray() && schema.items() != null)
        {
            collectDescriptions(descriptions, schema.items(), path + "[]");
        }
    }

    private String renderValue(SkillInputSchemaNode schema, int depth)
    {
        String indent = "  ".repeat(depth);
        if (schema.isObject())
        {
            StringBuilder builder = new StringBuilder("{\n");
            List<String> names = schema.properties().keySet().stream().sorted().toList();
            int count = names.size() + (schema.additionalPropertiesSchema() != null ? 1 : 0);
            int index = 0;
            for (String name : names)
            {
                builder.append(indent).append("  ").append(quote(name)).append(": ")
                        .append(renderValue(schema.properties().get(name), depth + 1));
                if (++index < count) builder.append(",");
                builder.append("\n");
            }
            if (schema.additionalPropertiesSchema() != null)
            {
                builder.append(indent).append("  \"<key>\": ")
                        .append(renderValue(schema.additionalPropertiesSchema(), depth + 1)).append("\n");
            }
            return builder.append(indent).append("}").toString();
        }
        if (schema.isArray())
        {
            return schema.items() == null ? "[ <any JSON value> ]"
                    : "[ " + renderValue(schema.items(), depth + 1) + " ]";
        }
        if (!schema.enumValues().isEmpty())
        {
            return quote("<one of: " + String.join(", ", schema.enumValues()) + ">");
        }
        return switch (schema.type())
        {
            case SkillInputSchemaNode.ANY_TYPE -> "<any JSON value>";
            case "string" -> "\"<string>\"";
            case "number", "integer" -> "<number>";
            case "boolean" -> "<boolean>";
            default -> "\"<value>\"";
        };
    }

    private void appendRules(StringBuilder builder, SkillInputSchemaNode schema, String path, DetailLevel detailLevel)
    {
        if (detailLevel == DetailLevel.VERBOSE)
        {
            builder.append("\n`").append(path).append("` must be ").append(typeDescription(schema));
            if (!schema.enumValues().isEmpty()) builder.append(" with one of ").append(schema.enumValues());
        }
        if (schema.isObject())
        {
            List<String> names = schema.properties().keySet().stream().sorted().toList();
            builder.append("\nAt `").append(path).append("`");
            if (path.equals("$")) builder.append(" (top level)");
            builder.append(": Required fields: ").append(fieldNames(schema.required().stream().sorted().toList()))
                    .append(". Optional declared fields: ")
                    .append(fieldNames(names.stream().filter(name -> !schema.required().contains(name)).toList())).append(".");
            if (!schema.allowsAdditionalProperties())
            {
                builder.append(" Only these fields are allowed: ").append(fieldNames(names)).append(".");
            }
            else if (schema.additionalPropertiesSchema() == null)
            {
                builder.append(" Additional fields are allowed with any JSON value; the illustrated fields are not exhaustive, and an empty illustration need not remain empty.");
            }
            else
            {
                builder.append(" Additional fields are allowed; each unlisted field value must be ")
                        .append(typeDescription(schema.additionalPropertiesSchema()));
                if (!schema.additionalPropertiesSchema().enumValues().isEmpty())
                    builder.append(" with one of ").append(schema.additionalPropertiesSchema().enumValues());
                builder.append(" and follow the rules at `").append(path).append(".*`. These constraints apply only to unlisted fields.");
                appendRules(builder, schema.additionalPropertiesSchema(), path + ".*", detailLevel);
            }
            for (Map.Entry<String, SkillInputSchemaNode> entry : new TreeMap<>(schema.properties()).entrySet())
            {
                appendRules(builder, entry.getValue(), propertyPath(path, entry.getKey()), detailLevel);
            }
        }
        if (schema.isArray() && schema.items() != null)
        {
            appendRules(builder, schema.items(), path + "[]", detailLevel);
        }
    }

    private String propertyPath(String path, String name)
    {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*") ? path + "." + name : path + "[" + quote(name) + "]";
    }

    private String fieldNames(List<String> names)
    {
        return "[" + names.stream().map(name -> name.matches("[A-Za-z_][A-Za-z0-9_]*") ? name : quote(name))
                .reduce((left, right) -> left + ", " + right).orElse("") + "]";
    }

    private String quote(String value)
    {
        return LoomspanJacksonCodecs.defaults().planningJson().writeValueAsString(value);
    }

    private String typeDescription(SkillInputSchemaNode schema)
    {
        return schema.isUnconstrained() ? "any JSON value" : "a " + schema.type();
    }
}
