package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.TraceRecord;

import java.io.IOException;

@FunctionalInterface
interface TraceRecordWriter
{
    void append(TraceRecord record) throws IOException;
}
