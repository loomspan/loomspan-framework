package ai.loomspan.api;

import java.util.List;
import java.util.Objects;

/** Immutable feedback for a complete proposed YAML skill set. */
public record SkillValidationResult(List<SkillValidationIssue> issues)
{
    public SkillValidationResult
    {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
    }

    public boolean valid()
    {
        return issues.stream().noneMatch(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR);
    }
}
