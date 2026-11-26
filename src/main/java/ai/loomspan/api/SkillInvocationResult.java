package ai.loomspan.api;

import java.util.Objects;

/** Exact skill result text and the identity of its root execution. */
public record SkillInvocationResult(String text, String sessionId)
{
    public SkillInvocationResult
    {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (sessionId.isBlank()) throw new IllegalArgumentException("sessionId must not be blank");
    }
}
