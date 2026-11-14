package ai.loomspan.internal.runtime.input;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import tools.jackson.databind.JsonNode;
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
        if ("object".equals(type(contract.schema().tree()))
                && properties(contract.schema().tree()).isEmpty()
                && required(contract.schema().tree()).isEmpty()
                && additionalPropertiesSchema(contract.schema().tree()) == null
                && !allowsAdditionalProperties(contract.schema().tree()))
        {
            StringBuilder builder = new StringBuilder("{}\n(This closed argument object has no declared fields. You must pass an empty object.)");
            appendDescriptions(builder, contract.schema().tree());
            return builder.toString();
        }
        StringBuilder builder = new StringBuilder("Illustrative declared structure (optional fields may be omitted; permitted fields are not exhaustive in open objects):\n");
        builder.append(renderValue(contract.schema().tree(), 0));
        builder.append("\nObject rules apply only at the stated location when that object is supplied. "
                + "Required child fields do not require an optional parent. "
                + "Populate permitted fields with actual data required by the skill instructions; openness does not require copying all context. "
                + "`<key>` illustrates an additional key, not a required field.");
        appendRules(builder, contract.schema().tree(), "$", detailLevel);
        appendDescriptions(builder, contract.schema().tree());
        return builder.toString();
    }

    private void appendDescriptions(StringBuilder builder, JsonNode schema)
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

    private void collectDescriptions(StringBuilder descriptions, JsonNode schema, String path)
    {
        if (description(schema) != null && !description(schema).isBlank())
        {
            descriptions.append("\nDescription at ").append(quote(path)).append(": ").append(quote(description(schema)));
        }
        if ("object".equals(type(schema)))
        {
            for (var entry : new TreeMap<>(properties(schema)).entrySet())
            {
                collectDescriptions(descriptions, entry.getValue(), propertyPath(path, entry.getKey()));
            }
            if (additionalPropertiesSchema(schema) != null)
            {
                collectDescriptions(descriptions, additionalPropertiesSchema(schema), path + ".*");
            }
        }
        if ("array".equals(type(schema)) && items(schema) != null)
        {
            collectDescriptions(descriptions, items(schema), path + "[]");
        }
    }

    private String renderValue(JsonNode schema, int depth)
    {
        String indent = "  ".repeat(depth);
        if ("object".equals(type(schema)))
        {
            StringBuilder builder = new StringBuilder("{\n");
            List<String> names = properties(schema).keySet().stream().sorted().toList();
            int count = names.size() + (additionalPropertiesSchema(schema) != null ? 1 : 0);
            int index = 0;
            for (String name : names)
            {
                builder.append(indent).append("  ").append(quote(name)).append(": ")
                        .append(renderValue(properties(schema).get(name), depth + 1));
                if (++index < count) builder.append(",");
                builder.append("\n");
            }
            if (additionalPropertiesSchema(schema) != null)
            {
                builder.append(indent).append("  \"<key>\": ")
                        .append(renderValue(additionalPropertiesSchema(schema), depth + 1)).append("\n");
            }
            return builder.append(indent).append("}").toString();
        }
        if ("array".equals(type(schema)))
        {
            return items(schema) == null ? "[ <any JSON value> ]"
                    : "[ " + renderValue(items(schema), depth + 1) + " ]";
        }
        if (!enumValues(schema).isEmpty())
        {
            return quote("<one of: " + String.join(", ", enumValues(schema)) + ">");
        }
        return switch (type(schema))
        {
            case "" -> "<any JSON value>";
            case "string" -> "\"<string>\"";
            case "number", "integer" -> "<number>";
            case "boolean" -> "<boolean>";
            default -> "\"<value>\"";
        };
    }

    private void appendRules(StringBuilder builder, JsonNode schema, String path, DetailLevel detailLevel)
    {
        if (permitsNull(schema))
        {
            builder.append("\nAt `").append(path).append("`: null is permitted.");
        }
        if (detailLevel == DetailLevel.VERBOSE)
        {
            builder.append("\n`").append(path).append("` must be ").append(typeDescription(schema));
            if (!enumValues(schema).isEmpty()) builder.append(" with one of ").append(enumValues(schema));
        }
        if ("object".equals(type(schema)))
        {
            List<String> names = properties(schema).keySet().stream().sorted().toList();
            builder.append("\nAt `").append(path).append("`");
            if (path.equals("$")) builder.append(" (top level)");
            builder.append(": Required fields: ").append(fieldNames(required(schema).stream().sorted().toList()))
                    .append(". Optional declared fields: ")
                    .append(fieldNames(names.stream().filter(name -> !required(schema).contains(name)).toList())).append(".");
            if (!allowsAdditionalProperties(schema))
            {
                builder.append(" Only these fields are allowed: ").append(fieldNames(names)).append(".");
            }
            else if (additionalPropertiesSchema(schema) == null)
            {
                builder.append(" Additional fields are allowed with any JSON value; the illustrated fields are not exhaustive, and an empty illustration need not remain empty.");
            }
            else
            {
                builder.append(" Additional fields are allowed; each unlisted field value must be ")
                        .append(typeDescription(additionalPropertiesSchema(schema)));
                if (!enumValues(additionalPropertiesSchema(schema)).isEmpty())
                    builder.append(" with one of ").append(enumValues(additionalPropertiesSchema(schema)));
                builder.append(" and follow the rules at `").append(path).append(".*`. These constraints apply only to unlisted fields.");
                appendRules(builder, additionalPropertiesSchema(schema), path + ".*", detailLevel);
            }
            for (Map.Entry<String, JsonNode> entry : new TreeMap<>(properties(schema)).entrySet())
            {
                appendRules(builder, entry.getValue(), propertyPath(path, entry.getKey()), detailLevel);
            }
        }
        if ("array".equals(type(schema)) && items(schema) != null)
        {
            appendRules(builder, items(schema), path + "[]", detailLevel);
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

    private String typeDescription(JsonNode schema)
    {
        return (type(schema).isEmpty() ? "any JSON value" : "a " + type(schema))
                + (schema.path("type").isArray() ? " or null" : "");
    }
    private static boolean permitsNull(JsonNode schema) {
        return schema.path("type").isArray()
                && (!schema.has("enum") || schema.path("enum").valueStream().anyMatch(JsonNode::isNull));
    }
    private static String type(JsonNode schema) {
        JsonNode value = schema.get("type");
        if (value == null) return "";
        if (value.isString()) return value.asText();
        for (JsonNode item : value) if (!"null".equals(item.asText())) return item.asText();
        return "";
    }
    private static Map<String, JsonNode> properties(JsonNode schema) {
        Map<String, JsonNode> result = new TreeMap<>();
        JsonNode props = schema.path("properties");
        for (String name : props.propertyNames()) {
            JsonNode value = props.get(name);
            if (!value.isBoolean() || value.booleanValue()) result.put(name, value);
        }
        return result;
    }
    private static List<String> required(JsonNode schema) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : schema.path("required")) {
            JsonNode property = schema.path("properties").path(value.asText());
            if (!property.isBoolean() || property.booleanValue()) result.add(value.asText());
        }
        return result;
    }
    private static boolean allowsAdditionalProperties(JsonNode schema) { return !schema.path("additionalProperties").isBoolean() || schema.path("additionalProperties").booleanValue(); }
    private static JsonNode additionalPropertiesSchema(JsonNode schema) { JsonNode node = schema.get("additionalProperties"); return node != null && node.isObject() ? node : null; }
    private static JsonNode items(JsonNode schema) { return schema.get("items"); }
    private static List<String> enumValues(JsonNode schema) { List<String> values = new ArrayList<>(); for (JsonNode value : schema.path("enum")) values.add(value.isString() ? value.asText() : value.toString()); return values; }
    private static String description(JsonNode schema) { return schema.path("description").isString() ? schema.path("description").asText() : null; }

}
