package ai.loomspan.internal.linter;

import java.util.Objects;

public record LinterOutcome(
        String skillName,
        String linterType,
        int attempt,
        int retryCount,
        int maxRetries,
        LinterOutcomeStatus status,
        String detail)
{
    public LinterOutcome
    {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(linterType, "linterType must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (attempt <= 0)
        {
            throw new IllegalArgumentException("attempt must be greater than zero");
        }
        if (retryCount < 0)
        {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (maxRetries < 0)
        {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
    }

    public boolean passed()
    {
        return status == LinterOutcomeStatus.PASSED;
    }

    public boolean terminal()
    {
        return status != LinterOutcomeStatus.RETRYING;
    }
}
