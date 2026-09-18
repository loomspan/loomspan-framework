package ai.loomspan.api;

import java.util.Objects;

/** One current-run authoring diagnostic. A null skill name or field path means it is unavailable. */
public record SkillValidationIssue(Severity severity, String sourceName, String skillName,
        String fieldPath, String message)
{
    public enum Severity { ERROR, WARNING }

    public SkillValidationIssue
    {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
