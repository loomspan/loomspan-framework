package ai.loomspan.internal.outputschema;

import org.springframework.util.StringUtils;

public record OutputSchemaValidationIssue(
        String path,
        String message,
        String canonicalField,
        String code,
        String expected,
        String actual,
        String reason,
        Integer line,
        Integer column,
        Long characterOffset,
        String fragment)
{
    static final int MAX_REASON_CODE_POINTS = 240;
    static final int MAX_FRAGMENT_CODE_POINTS = 120;
    static final int MAX_FACT_CODE_POINTS = 256;
    static final int MAX_MESSAGE_CODE_POINTS = 512;

    public OutputSchemaValidationIssue
    {
        path = normalize(path, MAX_FACT_CODE_POINTS);
        path = path == null ? "$" : path;
        message = normalize(message, MAX_MESSAGE_CODE_POINTS);
        message = message == null ? "Output schema validation failed." : message;
        canonicalField = normalize(canonicalField, MAX_FACT_CODE_POINTS);
        code = normalize(code, MAX_FACT_CODE_POINTS);
        expected = normalize(expected, MAX_FACT_CODE_POINTS);
        actual = normalize(actual, MAX_FACT_CODE_POINTS);
        reason = normalize(reason, MAX_REASON_CODE_POINTS);
        fragment = normalize(fragment, MAX_FRAGMENT_CODE_POINTS);
        line = positive(line);
        column = positive(column);
        characterOffset = nonNegative(characterOffset);
    }

    private static String normalize(String value, int maxCodePoints)
    {
        if (!StringUtils.hasText(value))
        {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").trim();
        if (sanitized.codePointCount(0, sanitized.length()) <= maxCodePoints)
        {
            return sanitized;
        }
        int end = sanitized.offsetByCodePoints(0, maxCodePoints);
        return sanitized.substring(0, end);
    }

    private static Integer positive(Integer value)
    {
        return value != null && value > 0 ? value : null;
    }

    private static Long nonNegative(Long value)
    {
        return value != null && value >= 0 ? value : null;
    }
}
