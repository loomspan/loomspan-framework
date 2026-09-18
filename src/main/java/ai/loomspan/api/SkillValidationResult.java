package ai.loomspan.api;

import java.util.List;
import java.util.Objects;

/**
 * Immutable feedback for a checked proposal. Check {@link #valid()} before treating
 * {@link #skills()} as complete. Validation does not prepare or reserve a generation.
 */
public record SkillValidationResult(List<SkillValidationIssue> issues, List<ValidatedSkill> skills)
{
    public SkillValidationResult
    {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
        if (issues.stream().anyMatch(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR)
                && !skills.isEmpty())
            throw new IllegalArgumentException("skills must be empty when validation has errors");
    }

    public boolean valid()
    {
        return issues.stream().noneMatch(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR);
    }
}
