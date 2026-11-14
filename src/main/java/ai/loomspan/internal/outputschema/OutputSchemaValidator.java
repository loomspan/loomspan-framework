package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.schema.CanonicalSchema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Objects;

/** Ordinary output ingress: strict lossless JSON, then the canonical validator. */
public final class OutputSchemaValidator
{
    public static final String INVALID_JSON = "invalid_json";
    public static final String UNKNOWN_PROPERTY = "additionalProperties";
    private static final ObjectMapper READER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public OutputSchemaValidator() { }

    public OutputSchemaValidator(ObjectMapper objectMapper)
    {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public OutputSchemaValidationResult validate(String rawOutput, CanonicalSchema schema)
    {
        Objects.requireNonNull(schema, "schema must not be null");
        if (!StringUtils.hasText(rawOutput)) return invalid(null, rawOutput);
        JsonNode root;
        try { root = READER.readTree(rawOutput); }
        catch (JacksonException ex) { return invalid(ex, rawOutput); }
        if (root == null) return invalid(null, rawOutput);
        // Only completed validation errors are verdicts. Operational failures propagate.
        var errors = schema.validate(root);
        if (errors.isEmpty()) return OutputSchemaValidationResult.passed();
        return OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED,
                errors.stream().map(error -> new OutputSchemaValidationIssue(
                        error.getInstanceLocation().toString().isEmpty() ? "$" : error.getInstanceLocation().toString(),
                        error + " (schema " + error.getSchemaLocation() + ")", null, error.getKeyword(), null, null,
                        null, null, null, null, null)).toList());
    }

    private OutputSchemaValidationResult invalid(JacksonException exception, String rawOutput)
    {
        String reason = normalizeParserReason(exception == null ? null : exception.getOriginalMessage());
        TokenStreamLocation location = exception == null ? null : exception.getLocation();
        return OutputSchemaValidationResult.failed(OutputSchemaFailureMode.INVALID_JSON,
                List.of(new OutputSchemaValidationIssue("$", reason == null ? "Response is not valid JSON." : "Response is not valid JSON: " + reason,
                        null, INVALID_JSON, "valid JSON", "unparseable JSON", reason,
                        location == null ? null : location.getLineNr(), location == null ? null : location.getColumnNr(),
                        location == null ? null : location.getCharOffset(),
                        extractEscapedFragment(rawOutput, location == null ? null : location.getCharOffset()))));
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

}
