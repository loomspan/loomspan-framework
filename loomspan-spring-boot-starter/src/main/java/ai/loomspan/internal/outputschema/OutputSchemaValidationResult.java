package ai.loomspan.internal.outputschema;

import java.util.List;

public record OutputSchemaValidationResult(boolean valid, OutputSchemaFailureMode failureMode, List<OutputSchemaValidationIssue> issues)
{
    public OutputSchemaValidationResult
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static OutputSchemaValidationResult passed()
    {
        return new OutputSchemaValidationResult(true, null, List.of());
    }

    public static OutputSchemaValidationResult failed(OutputSchemaFailureMode failureMode, List<OutputSchemaValidationIssue> issues)
    {
        return new OutputSchemaValidationResult(false, failureMode, issues);
    }
}
