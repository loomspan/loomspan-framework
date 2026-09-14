package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.ExecutionTraceHandle;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandle;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only package peer that blocks the real canonical TRACE_COMPLETED append. */
public final class BlockingTraceHandleTestSupport
{
    private final CountDownLatch completionAppendEntered = new CountDownLatch(1);
    private final CountDownLatch releaseCompletionAppend = new CountDownLatch(1);
    private final AtomicInteger ids = new AtomicInteger();

    public ExecutionTraceHandle create(String sessionId, String entrySkill,
            TracePersistencePolicy persistencePolicy, Clock clock,
            ExecutionObservationHandle observationHandle)
    {
        try
        {
            var path = Files.createTempFile("loomspan-blocking-completion-", ".ndjson");
            TraceRecordWriter delegate = new NdjsonTraceRecordWriter(
                    path, LoomspanJacksonCodecs.defaults().canonicalTrace());
            TraceRecordWriter blocking = record -> {
                if (record.recordType() == TraceRecordType.TRACE_COMPLETED)
                {
                    completionAppendEntered.countDown();
                    try { releaseCompletionAppend.await(); }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while blocking completion append", ex);
                    }
                }
                delegate.append(record);
            };
            return new DefaultExecutionTraceHandle(
                    "blocking-trace-" + ids.incrementAndGet(), sessionId, entrySkill, path,
                    persistencePolicy, clock, () -> "record-" + ids.incrementAndGet(),
                    Thread.currentThread().getName(), path.toString(), observationHandle, blocking);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not create blocking trace test file", ex);
        }
    }

    public boolean awaitCompletionAppend(long timeout, TimeUnit unit) throws InterruptedException
    {
        return completionAppendEntered.await(timeout, unit);
    }

    public void releaseCompletionAppend()
    {
        releaseCompletionAppend.countDown();
    }
}
