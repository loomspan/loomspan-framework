package ai.loomspan.internal.outputschema;

@FunctionalInterface
public interface OutputSchemaOutcomeRecorder
{
    void record(OutputSchemaOutcome outcome);
}
