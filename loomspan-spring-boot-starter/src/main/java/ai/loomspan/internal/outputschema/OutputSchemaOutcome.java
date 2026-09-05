package ai.loomspan.internal.outputschema;

import java.util.List;

public record OutputSchemaOutcome(
        String skillName,
        OutputSchemaFailureMode failureMode,
        int attempt,
        int retryCount,
        int maxRetries,
        OutputSchemaOutcomeStatus status,
        List<OutputSchemaValidationIssue> issues)
{
    public OutputSchemaOutcome
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
