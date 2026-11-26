package ai.loomspan.api;

import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/** Access denial after root execution identity has been allocated. */
public final class SkillAccessDeniedException extends AccessDeniedException
{
    private final String sessionId;

    public SkillAccessDeniedException(String message, Throwable cause, String sessionId)
    {
        super(message, cause);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (sessionId.isBlank()) throw new IllegalArgumentException("sessionId must not be blank");
    }

    public String getSessionId()
    {
        return sessionId;
    }
}
