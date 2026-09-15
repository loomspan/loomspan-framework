package ai.loomspan.internal.runtime.observation;

import ai.loomspan.internal.core.TraceRecord;

public enum NoOpExecutionObservationHandle implements ExecutionObservationHandle
{
    INSTANCE;

    @Override
    public void recordAppended(TraceRecord record)
    {
        // Observation is deliberately disabled.
    }

    @Override
    public void close(ObservationCompletionDisposition disposition)
    {
        // Observation is deliberately disabled.
    }
}
