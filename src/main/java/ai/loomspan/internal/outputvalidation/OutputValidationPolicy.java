package ai.loomspan.internal.outputvalidation;

import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaFailureMode;
import ai.loomspan.internal.outputschema.OutputSchemaValidationIssue;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import java.util.List;
import java.util.regex.Pattern;

/** Stateless validation decisions. Execution paths own attempts, retries and recording. */
public final class OutputValidationPolicy
{
    private OutputValidationPolicy() {}

    public static boolean exhausted(int attempt, int maxRetries) {
        return attempt > maxRetries;
    }

    public static OutputSchemaOutcome schemaOutcome(String skillName, int attempt, int maxRetries,
            boolean valid, OutputSchemaFailureMode failureMode, List<OutputSchemaValidationIssue> issues, int issueLimit) {
        return new OutputSchemaOutcome(skillName, failureMode, attempt, attempt - 1, maxRetries,
                valid ? OutputSchemaOutcomeStatus.PASSED : exhausted(attempt, maxRetries)
                        ? OutputSchemaOutcomeStatus.EXHAUSTED : OutputSchemaOutcomeStatus.RETRYING,
                limitIssues(issues, issueLimit));
    }

    public static LinterOutcome linterOutcome(String skillName, String type, int attempt, int maxRetries,
            boolean matches, String detail) {
        return new LinterOutcome(skillName, type, attempt, attempt - 1, maxRetries,
                matches ? LinterOutcomeStatus.PASSED : exhausted(attempt, maxRetries)
                        ? LinterOutcomeStatus.EXHAUSTED : LinterOutcomeStatus.RETRYING, detail);
    }

    public static LinterOutcome immutableLinterOutcome(String skillName, String type, int maxRetries,
            boolean matches, String detail) {
        return new LinterOutcome(skillName, type, 1, 0, maxRetries,
                matches ? LinterOutcomeStatus.PASSED : LinterOutcomeStatus.EXHAUSTED, detail);
    }

    public static List<OutputSchemaValidationIssue> limitIssues(List<OutputSchemaValidationIssue> issues, int limit) {
        return issues == null || issues.isEmpty() ? List.of() : issues.stream().limit(limit).toList();
    }

    public static boolean matches(Pattern pattern, String candidate) {
        return candidate != null && pattern.matcher(candidate).matches();
    }
}
