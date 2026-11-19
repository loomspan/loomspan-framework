package ai.loomspan.api;

import java.util.Objects;

/** A validator issue with an exact JSON Pointer, including the empty root pointer. */
public record SkillContractIssue(String path, String code, String message)
{
    public SkillContractIssue
    {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
