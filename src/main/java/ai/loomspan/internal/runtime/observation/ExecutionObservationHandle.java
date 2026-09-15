package ai.loomspan.internal.runtime.observation;

import ai.loomspan.internal.core.TraceRecord;

/**
 * Internal, optional observation boundary for one authoritative session.
 * Implementations must contain their own failures.
 */
public interface ExecutionObservationHandle
{
    void recordAppended(TraceRecord record);

    void close(ObservationCompletionDisposition disposition);
}
