package ai.loomspan.api;

import java.util.Optional;

public class SkillException extends RuntimeException
{
    private final SkillFailureDiagnosis failureDiagnosis;

    public SkillException(String message)
    {
        super(message);
        this.failureDiagnosis = null;
    }

    public SkillException(String message, Throwable cause)
    {
        this(message, cause, null);
    }

    public SkillException(String message, Throwable cause, SkillFailureDiagnosis failureDiagnosis)
    {
        super(message, cause);
        this.failureDiagnosis = failureDiagnosis;
    }

    /** Diagnosis of the selected terminal contract failure, when available. */
    public Optional<SkillFailureDiagnosis> getFailureDiagnosis()
    {
        return Optional.ofNullable(failureDiagnosis);
    }
}
