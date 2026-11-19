package ai.loomspan.internal.outputschema;

import ai.loomspan.api.SkillContractIssue;

import java.util.List;

public record OutputSchemaValidationResult(boolean valid, OutputSchemaFailureMode failureMode, List<OutputSchemaValidationIssue> issues, List<SkillContractIssue> contractIssues)
{
    public OutputSchemaValidationResult
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
        contractIssues = contractIssues == null ? List.of() : List.copyOf(contractIssues);
    }

    public static OutputSchemaValidationResult passed()
    {
        return new OutputSchemaValidationResult(true, null, List.of(), List.of());
    }

    public static OutputSchemaValidationResult failed(OutputSchemaFailureMode failureMode, List<OutputSchemaValidationIssue> issues, List<SkillContractIssue> contractIssues)
    {
        return new OutputSchemaValidationResult(false, failureMode, issues, contractIssues);
    }
}
