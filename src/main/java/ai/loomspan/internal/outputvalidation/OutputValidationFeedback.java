package ai.loomspan.internal.outputvalidation;

import ai.loomspan.internal.outputschema.OutputSchemaFailureMode;
import ai.loomspan.internal.outputschema.OutputSchemaValidationIssue;
import ai.loomspan.internal.outputschema.OutputSchemaValidationResult;
import ai.loomspan.internal.outputschema.OutputSchemaValidator;
import ai.loomspan.internal.runtime.evidence.EvidenceCoverageResult;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;

/** Pure feedback renderers; each named format preserves its execution path's diagnostics. */
public final class OutputValidationFeedback
{
    private static final int MAX_ISSUES_IN_HINT = 4;
    public static final int MAX_CORRECTION_CODE_POINTS = 2_048;
    private OutputValidationFeedback() {}

    private static String issueMessage(OutputSchemaValidationIssue issue)
    {
        String path = StringUtils.hasText(issue.path()) ? issue.path() : "$";
        if (OutputSchemaValidator.INVALID_JSON.equals(issue.code()))
        {
            StringBuilder message = new StringBuilder("Path ").append(quoteFact(path)).append(": invalid JSON");
            if (StringUtils.hasText(issue.reason()))
            {
                message.append(". Parser reason: ").append(quoteFact(issue.reason()));
            }
            if (issue.line() != null || issue.column() != null || issue.characterOffset() != null)
            {
                message.append(". Location:");
                if (issue.line() != null) message.append(" line ").append(issue.line());
                if (issue.column() != null) message.append(", column ").append(issue.column());
                if (issue.characterOffset() != null) message.append(", character offset ").append(issue.characterOffset());
            }
            if (StringUtils.hasText(issue.fragment()))
            {
                message.append(". Nearby fragment (escaped JSON string): ").append(quoteFact(issue.fragment()));
            }
            return message.append('.').toString();
        }
        if (StringUtils.hasText(issue.expected()) && StringUtils.hasText(issue.actual()))
        {
            return "Path " + quoteFact(path) + ": expected " + quoteFact(issue.expected())
                    + ", received " + quoteFact(issue.actual()) + ".";
        }
        return "Path " + quoteFact(path) + ": " + quoteFact(sentence(issue.message()));
    }

    public static String ordinarySchemaSummary(List<OutputSchemaValidationIssue> issues, int maxIssues)
    {
        if (issues == null || issues.isEmpty())
        {
            return "no validation issues recorded";
        }

        List<String> summarized = issues.stream()
                .limit(maxIssues)
                .map(OutputValidationFeedback::logIssueMessage)
                .toList();

        if (issues.size() > maxIssues)
        {
            summarized = new ArrayList<>(summarized);
            summarized.add("+" + (issues.size() - maxIssues) + " more issue(s)");
        }

        return String.join("; ", summarized);
    }

    public static String ordinarySchemaCorrection(OutputSchemaValidationResult result, boolean modelContribution)
    {
        String heading = result.failureMode() == OutputSchemaFailureMode.INVALID_JSON
                ? "The previous response could not be parsed as JSON."
                : "The previous response is valid JSON but does not satisfy the configured output_schema.";
        String prefix = heading + "\nIssues:\n";
        List<String> bullets = result.issues().stream()
                .limit(MAX_ISSUES_IN_HINT)
                .map(issue -> "- " + issueMessage(issue) + "\n")
                .toList();

        for (int displayed = bullets.size(); displayed >= 0; displayed--)
        {
            int omitted = result.issues().size() - displayed;
            String tail = correctionTail(omitted, modelContribution);
            String renderedBullets = String.join("", bullets.subList(0, displayed)).stripTrailing();
            String correction = prefix + renderedBullets + "\n" + tail;
            if (codePointCount(correction) <= MAX_CORRECTION_CODE_POINTS)
            {
                return correction;
            }
        }

        throw new IllegalStateException("Required output-schema correction text exceeds its configured bound");
    }

    private static String correctionTail(int omitted, boolean modelContribution)
    {
        StringBuilder tail = new StringBuilder();
        if (omitted > 0)
        {
            tail.append(omitted).append(" additional issue(s) omitted.\n");
        }
        return tail.append("Preserve all already-valid structure and values visible in the previous assistant response.\n")
                .append("Do NOT call any tools again; use the data already returned by completed tool calls.\n")
                .append(!modelContribution ? "Return one complete corrected JSON object only, with no explanation, markdown, or code fences." : "Return only the corrected model-owned JSON contribution. Omit every framework-bound destination; Framework supplies those values.")
                .toString();
    }

    private static String quoteFact(String value)
    {
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < value.length();)
        {
            int codePoint = value.codePointAt(index);
            switch (codePoint)
            {
                case '\"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (codePoint < 0x20)
                    {
                        quoted.append(String.format("\\u%04x", codePoint));
                    }
                    else
                    {
                        quoted.appendCodePoint(codePoint);
                    }
                }
            }
            index += Character.charCount(codePoint);
        }
        return quoted.append('\"').toString();
    }

    private static String logIssueMessage(OutputSchemaValidationIssue issue)
    {
        String path = StringUtils.hasText(issue.path()) ? issue.path() : "$";
        if (OutputSchemaValidator.INVALID_JSON.equals(issue.code()))
        {
            return path + ": invalid JSON";
        }
        if (OutputSchemaValidator.UNKNOWN_PROPERTY.equals(issue.code()))
        {
            return "output object: validation issue code " + issue.code();
        }
        if (StringUtils.hasText(issue.expected()) && StringUtils.hasText(issue.actual()))
        {
            return path + ": expected " + issue.expected() + ", received " + issue.actual();
        }
        return path + ": validation issue code " + issue.code();
    }

    private static String sentence(String message)
    {
        if (!StringUtils.hasText(message)) return "validation failed.";
        return message.endsWith(".") ? message : message + ".";
    }

    private static int codePointCount(String value)
    {
        return value.codePointCount(0, value.length());
    }

    public static String planningSchemaSummary(List<OutputSchemaValidationIssue> issues)
    {
        if (issues == null || issues.isEmpty())
        {
            return "unknown schema validation error";
        }

        return issues.stream()
                .limit(3)
                .map(issue ->
                {
                    String field = issue.canonicalField() == null || issue.canonicalField().isBlank()
                            ? issue.path()
                            : issue.canonicalField();
                    return field + ": " + issue.message();
                })
                .reduce((left, right) -> left + "; " + right)
                .orElse("unknown schema validation error");
    }

    public static String ordinaryEvidenceHint(EvidenceCoverageResult result)
    {
        StringBuilder hint = new StringBuilder("""
                Evidence validation failed for the previous response.
                Do NOT call any tools again. Use only results already gathered from successfully completed direct child skills.
                Return ONLY corrected raw JSON that removes unsupported optional claims or limits them to supported successful skills.
                Issues:
                """);

        result.issues().stream()
                .limit(MAX_ISSUES_IN_HINT)
                .forEach(issue -> hint.append("- ").append(issue.message()).append('\n'));

        return hint.toString().stripTrailing();
    }

    public static String joinSystemText(String original, String hint)
    {
        if (!StringUtils.hasText(original)) return hint;
        return original + "\n\n" + hint;
    }

}
