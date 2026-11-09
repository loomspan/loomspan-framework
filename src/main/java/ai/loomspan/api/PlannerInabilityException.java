package ai.loomspan.api;

/** Signals that an invocation ended unsuccessfully, with the exact reason in {@link #getMessage()}. */
public final class PlannerInabilityException extends RecoverableSkillException
{
    public PlannerInabilityException(String reason)
    {
        super(reason);
    }
}
