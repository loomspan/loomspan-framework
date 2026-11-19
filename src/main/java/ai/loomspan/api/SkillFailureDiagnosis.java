package ai.loomspan.api;

import java.util.List;
import java.util.Objects;

/** Immutable detail of the skill contract selected as the invocation's terminal cause. */
public record SkillFailureDiagnosis(String skillName, SkillContractStage stage, String message,
        List<SkillContractIssue> issues)
{
    public SkillFailureDiagnosis
    {
        if (skillName == null || skillName.isBlank()) throw new IllegalArgumentException("skillName must not be blank");
        Objects.requireNonNull(stage, "stage must not be null");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
