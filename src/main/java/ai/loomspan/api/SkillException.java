package ai.loomspan.api;

import java.util.Optional;

public class SkillException extends RuntimeException
{
    private final SkillFailureDiagnosis failureDiagnosis;
    private final String sessionId;

    public SkillException(String message)
    {
        super(message);
        this.failureDiagnosis = null;
        this.sessionId = null;
    }

    public SkillException(String message, Throwable cause)
    {
        this(message, cause, null);
    }

    public SkillException(String message, Throwable cause, SkillFailureDiagnosis failureDiagnosis)
    {
        this(message, cause, failureDiagnosis, null);
    }

    public SkillException(String message, Throwable cause, SkillFailureDiagnosis failureDiagnosis, String sessionId)
    {
        super(message, cause);
        this.failureDiagnosis = failureDiagnosis;
        this.sessionId = sessionId;
    }

    /** Root execution identity, or {@code null} when execution has not allocated a session. */
    public String getSessionId()
    {
        return sessionId;
    }

    /** Diagnosis of the selected terminal contract failure, when available. */
    public Optional<SkillFailureDiagnosis> getFailureDiagnosis()
    {
        return Optional.ofNullable(failureDiagnosis);
    }
}
