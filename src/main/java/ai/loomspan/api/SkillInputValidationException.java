package ai.loomspan.api;

import java.util.List;

public class SkillInputValidationException extends SkillException
{
    private final List<SkillInputValidationIssue> issues;

    public SkillInputValidationException(String message, List<SkillInputValidationIssue> issues)
    {
        super(message);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public SkillInputValidationException(String message, List<SkillInputValidationIssue> issues,
            SkillFailureDiagnosis failureDiagnosis)
    {
        super(message, null, failureDiagnosis);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public List<SkillInputValidationIssue> getIssues()
    {
        return issues;
    }

    public SkillInputValidationException(String message, Throwable cause, List<SkillInputValidationIssue> issues,
            SkillFailureDiagnosis failureDiagnosis, String sessionId)
    {
        super(message, cause, failureDiagnosis, sessionId);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
