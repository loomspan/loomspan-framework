package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.ExecutionTraceReader;

public final class ExecutionTraceReaders
{
    private ExecutionTraceReaders()
    {
    }

    public static ExecutionTraceReader ndjson()
    {
        return new NdjsonExecutionTraceReader();
    }
}
