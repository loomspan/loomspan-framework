package ai.loomspan.internal.outputschema;

import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class OutputSchemaValidator
{
    static final String INVALID_JSON = "invalid_json";
    static final String TYPE_MISMATCH = "type_mismatch";
    static final String MISSING_REQUIRED = "missing_required_property";
    static final String UNKNOWN_PROPERTY = "unknown_property";
    static final String ENUM_MISMATCH = "enum_mismatch";
    static final String AMBIGUOUS_PROPERTY = "ambiguous_property";
    static final String UNSUPPORTED_SCHEMA = "unsupported_schema";

    private final ObjectMapper objectMapper;

    public OutputSchemaValidator()
    {
        this(ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
    }

    public OutputSchemaValidator(ObjectMapper objectMapper)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public OutputSchemaValidationResult validate(String rawOutput, YamlSkillManifest.OutputSchemaManifest schema)
    {
        Objects.requireNonNull(schema, "schema must not be null");
        if (!StringUtils.hasText(rawOutput))
        {
            return OutputSchemaValidationResult.failed(
                    OutputSchemaFailureMode.INVALID_JSON,
                    List.of(invalidJsonIssue(null, rawOutput)));
        }

        JsonNode root;
        try
        {
            root = objectMapper.readTree(rawOutput);
        }
        catch (JacksonException ex)
        {
            return OutputSchemaValidationResult.failed(
                    OutputSchemaFailureMode.INVALID_JSON,
                    List.of(invalidJsonIssue(ex, rawOutput)));
        }

        if (root == null)
        {
            return OutputSchemaValidationResult.failed(
                    OutputSchemaFailureMode.INVALID_JSON,
                    List.of(invalidJsonIssue(null, rawOutput)));
        }

        List<OutputSchemaValidationIssue> issues = new ArrayList<>();
        validateNode(root, schema, "$", null, issues);

        if (issues.isEmpty())
        {
            return OutputSchemaValidationResult.passed();
        }

        return OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, issues);
    }

    private void validateNode(JsonNode node,
            YamlSkillManifest.OutputSchemaManifest schema,
            String path,
            String canonicalField,
            List<OutputSchemaValidationIssue> issues)
    {
        if (node.isNull() && Boolean.TRUE.equals(schema.getNullable()))
        {
            return;
        }
        switch (schema.getType())
        {
            case "object" -> validateObject(node, schema, path, canonicalField, issues);
            case "array" -> validateArray(node, schema, path, canonicalField, issues);
            case "string" -> validateString(node, schema, path, canonicalField, issues);
            case "number" -> validateType(node, schema, path, canonicalField, node.isNumber(), issues);
            case "integer" -> validateType(node, schema, path, canonicalField, node.isIntegralNumber(), issues);
            case "boolean" -> validateType(node, schema, path, canonicalField, node.isBoolean(), issues);
            default -> issues.add(issue(path,
                    "Unsupported schema type '" + schema.getType() + "'.",
                    canonicalField,
                    UNSUPPORTED_SCHEMA,
                    schema.getType(),
                    actualType(node)));
        }
    }

    private void validateObject(JsonNode node,
            YamlSkillManifest.OutputSchemaManifest schema,
            String path,
            String canonicalField,
            List<OutputSchemaValidationIssue> issues)
    {
        if (!node.isObject())
        {
            issues.add(typeMismatch(path, canonicalField, schema, node));
            return;
        }

        Map<String, String> canonicalByLowercase = new LinkedHashMap<>();
        schema.getProperties().keySet().forEach(property -> canonicalByLowercase.put(property.toLowerCase(Locale.ROOT), property));

        Map<String, List<String>> actualByLowercase = new LinkedHashMap<>();
        node.propertyNames().forEach(fieldName -> actualByLowercase.computeIfAbsent(fieldName.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(fieldName));

        for (Map.Entry<String, List<String>> entry : actualByLowercase.entrySet())
        {
            String canonicalName = canonicalByLowercase.get(entry.getKey());
            if (entry.getValue().size() > 1)
            {
                issues.add(issue(
                        path,
                        "ambiguous fields " + entry.getValue() + " differ only by case",
                        canonicalName,
                        AMBIGUOUS_PROPERTY,
                        canonicalName == null ? "one unambiguous property name" : canonicalName,
                        "multiple case-insensitive matches"));
                continue;
            }

            String actualField = entry.getValue().getFirst();
            if (canonicalName == null)
            {
                if (!Boolean.TRUE.equals(schema.getAdditionalProperties()))
                {
                    issues.add(issue(pathOf(path, actualField),
                            "unknown field '" + actualField + "'",
                            actualField,
                            UNKNOWN_PROPERTY,
                            "declared property",
                            "unknown property"));
                }
                continue;
            }

            validateNode(node.get(actualField), schema.getProperties().get(canonicalName), pathOf(path, canonicalName), canonicalName, issues);
        }

        for (String requiredField : schema.getRequired())
        {
            if (!actualByLowercase.containsKey(requiredField.toLowerCase(Locale.ROOT)))
            {
                issues.add(issue(
                        pathOf(path, requiredField),
                        "missing required field '" + requiredField + "'",
                        requiredField,
                        MISSING_REQUIRED,
                        "required property",
                        "missing"));
            }
        }
    }

    private void validateArray(JsonNode node,
            YamlSkillManifest.OutputSchemaManifest schema,
            String path,
            String canonicalField,
            List<OutputSchemaValidationIssue> issues)
    {
        if (!node.isArray())
        {
            issues.add(typeMismatch(path, canonicalField, schema, node));
            return;
        }
        for (int index = 0; index < node.size(); index++)
        {
            validateNode(node.get(index), schema.getItems(), path + "[" + index + "]", canonicalField, issues);
        }
    }

    private void validateString(JsonNode node, YamlSkillManifest.OutputSchemaManifest schema, String path, String canonicalField, List<OutputSchemaValidationIssue> issues)
    {
        if (!node.isTextual())
        {
            issues.add(typeMismatch(path, canonicalField, schema, node));
            return;
        }
        if (!schema.getEnumValues().isEmpty() && !schema.getEnumValues().contains(node.textValue()))
        {
            String expected = "one of " + schema.getEnumValues();
            issues.add(issue(
                    path,
                    "expected " + expected + ", received string value",
                    canonicalField,
                    ENUM_MISMATCH,
                    expected,
                    "string"));
        }
    }

    private void validateType(JsonNode node,
            YamlSkillManifest.OutputSchemaManifest schema,
            String path,
            String canonicalField,
            boolean condition,
            List<OutputSchemaValidationIssue> issues)
    {
        if (!condition)
        {
            issues.add(typeMismatch(path, canonicalField, schema, node));
        }
    }

    private OutputSchemaValidationIssue invalidJsonIssue(JacksonException exception, String rawOutput)
    {
        String reason = normalizeParserReason(exception == null ? null : exception.getOriginalMessage());
        TokenStreamLocation location = exception == null ? null : exception.getLocation();
        Integer line = location == null ? null : location.getLineNr();
        Integer column = location == null ? null : location.getColumnNr();
        Long offset = location == null ? null : location.getCharOffset();
        String fragment = extractEscapedFragment(rawOutput, offset);
        String message = StringUtils.hasText(reason)
                ? "Response is not valid JSON: " + reason
                : "Response is not valid JSON.";
        return new OutputSchemaValidationIssue(
                "$", message, null, INVALID_JSON, "valid JSON", "unparseable JSON",
                reason, line, column, offset, fragment);
    }

    private OutputSchemaValidationIssue typeMismatch(String path,
            String canonicalField,
            YamlSkillManifest.OutputSchemaManifest schema,
            JsonNode node)
    {
        String expected = expectedType(schema);
        String actual = actualType(node);
        return issue(path,
                "expected " + expected + ", received " + actual,
                canonicalField,
                TYPE_MISMATCH,
                expected,
                actual);
    }

    private OutputSchemaValidationIssue issue(String path,
            String message,
            String canonicalField,
            String code,
            String expected,
            String actual)
    {
        return new OutputSchemaValidationIssue(
                path, message, canonicalField, code, expected, actual,
                null, null, null, null, null);
    }

    private String expectedType(YamlSkillManifest.OutputSchemaManifest schema)
    {
        String expected = schema.getType();
        return Boolean.TRUE.equals(schema.getNullable()) ? expected + " or null" : expected;
    }

    private String actualType(JsonNode node)
    {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isIntegralNumber()) return "integer";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return "unknown";
    }

    private String extractEscapedFragment(String rawOutput, Long rawOffset)
    {
        if (rawOutput == null || rawOffset == null || rawOffset < 0 || rawOffset > rawOutput.length())
        {
            return null;
        }
        int offset = Math.toIntExact(rawOffset);
        if (offset > 0 && offset < rawOutput.length()
                && Character.isLowSurrogate(rawOutput.charAt(offset))
                && Character.isHighSurrogate(rawOutput.charAt(offset - 1)))
        {
            offset--;
        }
        int before = Math.min(59, rawOutput.codePointCount(0, offset));
        int start = rawOutput.offsetByCodePoints(offset, -before);
        int after = Math.min(59, rawOutput.codePointCount(offset, rawOutput.length()));
        int end = rawOutput.offsetByCodePoints(offset, after);
        return escapeJsonLiteral(rawOutput.substring(start, end), OutputSchemaValidationIssue.MAX_FRAGMENT_CODE_POINTS);
    }

    private String escapeJsonLiteral(String value, int maxCodePoints)
    {
        StringBuilder escaped = new StringBuilder("\"");
        int budget = Math.max(0, maxCodePoints - 2);
        for (int index = 0; index < value.length();)
        {
            int codePoint = value.codePointAt(index);
            String encoded = switch (codePoint)
            {
                case '\"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\b' -> "\\b";
                case '\f' -> "\\f";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                default -> codePoint < 0x20
                        ? String.format("\\u%04x", codePoint)
                        : new String(Character.toChars(codePoint));
            };
            int encodedPoints = encoded.codePointCount(0, encoded.length());
            if (encodedPoints > budget) break;
            escaped.append(encoded);
            budget -= encodedPoints;
            index += Character.charCount(codePoint);
        }
        return escaped.append('\"').toString();
    }

    private String truncate(String value, int maxCodePoints)
    {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) return value;
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private String normalizeParserReason(String reason)
    {
        if (!StringUtils.hasText(reason)) return null;
        String sanitized = reason.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return truncate(sanitized, OutputSchemaValidationIssue.MAX_REASON_CODE_POINTS);
    }

    private String pathOf(String parent, String child)
    {
        return OutputSchemaPath.property(parent, child);
    }
}
