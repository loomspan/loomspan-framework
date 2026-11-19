package ai.loomspan.internal.runtime.step;

import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * Result of validating a proposed {@link StepAction} against the current plan state.
 */
record StepValidationResult(boolean valid, @Nullable String rejectionReason, @Nullable ai.loomspan.api.SkillFailureDiagnosis diagnosis)
{
    public static StepValidationResult ok()
    {
        return new StepValidationResult(true, null, null);
    }

    public static StepValidationResult rejected(String reason)
    {
        Objects.requireNonNull(reason, "rejectionReason must not be null");
        return rejected(reason, null);
    }

    public static StepValidationResult rejected(String reason, ai.loomspan.api.SkillFailureDiagnosis diagnosis)
    {
        return new StepValidationResult(false, reason, diagnosis);
    }
}
