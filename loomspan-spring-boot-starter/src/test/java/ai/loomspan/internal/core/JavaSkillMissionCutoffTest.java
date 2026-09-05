package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.MissionWorkExecutor;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.security.ScopedAuthentication;
import ai.loomspan.internal.security.SkillAccessPolicy;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Timeout(10)
class JavaSkillMissionCutoffTest
{
    @Test
    void timeoutClosesJavaRootAndFencesNonCooperativeLateReturn() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean lateWrite = new AtomicBoolean();
        var session = TestLoomspanSessions.withId("java-timeout", "javaRoot", 3);
        var executor = new ControlledTimeoutExecutor(started);
        try
        {
            var coordinator = coordinator(executor, arguments -> work(started, release, lateWrite));
            assertThatThrownBy(() -> coordinator.execute("javaRoot", "root", session, null))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
            var before = records(session);
            assertClosedOnce(before);
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            assertThat(lateWrite).isFalse();
            assertThat(records(session)).containsExactlyElementsOf(before);
        }
        finally
        {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptionPreservesCallerFlagAndCutsOffJavaWorker() throws Exception
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean lateWrite = new AtomicBoolean();
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var session = TestLoomspanSessions.withId("java-interrupted", "javaRoot", 3);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var coordinator = coordinator(executor, arguments -> work(started, release, lateWrite));
        Thread caller = Thread.ofPlatform().unstarted(() -> {
            try { coordinator.execute("javaRoot", "root", session, null); }
            catch (Throwable ex) { failure.set(ex); callerInterrupted.set(Thread.currentThread().isInterrupted()); }
        });
        try
        {
            caller.start();
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(3000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(callerInterrupted).isTrue();
            var before = records(session);
            assertClosedOnce(before);
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            assertThat(lateWrite).isFalse();
            assertThat(records(session)).containsExactlyElementsOf(before);
        }
        finally
        {
            release.countDown();
            caller.interrupt();
            executor.shutdownNow();
        }
    }

    private static String work(CountDownLatch started, CountDownLatch release, AtomicBoolean lateWrite)
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        started.countDown();
        boolean released = false;
        while (!released)
        {
            try { release.await(); released = true; }
            catch (InterruptedException ignored) { /* Simulate application code that ignores cancellation. */ }
        }
        binding.runIfWritable(() -> lateWrite.set(true));
        return "late result";
    }

    private static ExecutionCoordinator coordinator(ExecutorService executor, CapabilityInvoker invoker)
    {
        var registry = new InMemoryCapabilityRegistry();
        registry.register("javaRoot", new CapabilityMetadata("javaRoot", "javaRoot", "Java work",
                SkillExecutionDescriptor.none(), SkillAccessPolicy.unrestricted(), invoker, CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic("javaRoot", "Java work"), new SkillSource(null, "bean", "work()")));
        var state = new DefaultExecutionStateService(Clock.systemUTC());
        MissionExecutionEngine noModel = (s,d,o,i,m,t,p,a) -> { throw new AssertionError("Java dispatched model work"); };
        return new ExecutionCoordinator(mock(YamlSkillCatalog.class), registry,
                (d,m) -> { throw new AssertionError("Java requested a model"); },
                (n,s,a) -> List.of(), (s,d,c,a) -> List.of(), noModel, noModel, state,
                new DefaultAccessGuard(), (v,s) -> v, new ScopedAuthentication(null),
                new MissionWorkExecutor(state, Duration.ofSeconds(5), executor, new NoOpSessionUsageService()));
    }

    private static void assertClosedOnce(List<TraceRecord> records)
    {
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.FRAME_CLOSED)).hasSize(1);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.TRACE_COMPLETED)).hasSize(1);
        assertThat(records.getLast().metadata()).containsEntry("remainingFrames", 0);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.EVIDENCE_RECORDED)).isEmpty();
    }

    private static List<TraceRecord> records(LoomspanSession session)
    {
        var records = new ArrayList<TraceRecord>();
        session.readTraceRecords(records::add);
        return records;
    }

    /** Produces a deterministic timeout only after the Java worker has entered its method. */
    private static final class ControlledTimeoutExecutor extends AbstractExecutorService
    {
        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final CountDownLatch started;
        private ControlledTimeoutExecutor(CountDownLatch started) { this.started = started; }
        @Override protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable)
        {
            return new FutureTask<>(callable) {
                @Override public T get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException
                {
                    if (!started.await(3, TimeUnit.SECONDS)) throw new AssertionError("Java worker did not start");
                    throw new TimeoutException("controlled timeout after worker entry");
                }
            };
        }
        @Override public void execute(Runnable command) { delegate.execute(command); }
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
        { return delegate.awaitTermination(timeout, unit); }
    }
}
