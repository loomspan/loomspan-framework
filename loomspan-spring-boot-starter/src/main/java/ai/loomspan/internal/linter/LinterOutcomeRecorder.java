package ai.loomspan.internal.linter;

@FunctionalInterface
public interface LinterOutcomeRecorder
{
    void record(LinterOutcome outcome);
}
