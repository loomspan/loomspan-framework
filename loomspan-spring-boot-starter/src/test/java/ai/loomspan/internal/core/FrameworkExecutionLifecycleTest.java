package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;

import java.time.Duration;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.trace.ImmediateCompletionRetention;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

class FrameworkExecutionLifecycleTest
{
    @Test
    void acceptsPositiveDurationsLargerThanNanosecondsCanRepresent()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(
                context, Duration.ofSeconds(Long.MAX_VALUE), executor, () -> 0L);

        lifecycle.closeAdmission();

        assertThat(lifecycle.deadlineNanos()).isEqualTo(Long.MAX_VALUE);
        lifecycle.destroy();
    }

    @Test
    void owningCloseEventAtomicallyRejectsLateRootsAndDoesNotWait()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor);
        var root = lifecycle.admitRoot();

        lifecycle.onApplicationEvent(new ContextClosedEvent(context));

        assertThat(lifecycle.supportsAsyncExecution()).isFalse();
        assertThat(lifecycle.activeRootCount()).isOne();
        assertThatThrownBy(lifecycle::admitRoot).isInstanceOf(RejectedExecutionException.class);
        root.close();
        root.close();
        assertThat(lifecycle.activeRootCount()).isZero();
        lifecycle.destroy();
    }

    @Test
    void foreignContextEventDoesNotCloseAdmission()
    {
        var owner = new StaticApplicationContext();
        var foreign = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(owner, Duration.ofSeconds(1), executor);

        lifecycle.onApplicationEvent(new ContextClosedEvent(foreign));

        lifecycle.admitRoot().close();
        lifecycle.destroy();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void deadlinePublishesLockFreeMissionCutoffAndCancelsRegisteredFuture()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(25), executor);
        var root = lifecycle.admitRoot();
        var session = new LoomspanSession("shutdown", "entry", 3);
        session.attachAdmittedRoot(root);
        var mission = new MissionContext(session, "entry", "frame", null);
        var future = new FutureTask<Void>(() -> null);
        mission.lifecycle().registerOwningFuture(future);

        lifecycle.stop();

        assertThat(future.isCancelled()).isTrue();
        assertThat(mission.lifecycle().primaryCancellation()).get()
                .extracting(MissionLifecycle.PrimaryCancellation::cause)
                .isInstanceOf(FrameworkShutdownException.class);
        root.close();
        lifecycle.destroy();
    }

    @Test
    void runnerCompletionOccursAfterBindingRestorationAndBeforeRootRelease()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor);
        var codecs = LoomspanJacksonCodecs.defaults();
        var runner = new LoomspanSessionRunner(3, TracePersistencePolicy.ONERROR, Clock.systemUTC(),
                NoOpExecutionObservationHandleFactory.INSTANCE, ImmediateCompletionRetention.INSTANCE,
                new LoomspanProperties.Session.Quotas(), codecs.canonicalTrace(), lifecycle);

        String value = runner.callWithNewSession("entry", null, session -> "done", (result, session) -> {
            assertThat(ExecutionBindingScope.current()).isEmpty();
            assertThat(lifecycle.activeRootCount()).isOne();
            return result;
        });

        assertThat(value).isEqualTo("done");
        assertThat(lifecycle.activeRootCount()).isZero();
        lifecycle.destroy();
    }

    @Test
    void missionRegisteredAfterAdmissionCloseInheritsTheSharedDeadline()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var nanoTime = new AtomicLong(100L);
        var lifecycle = new FrameworkExecutionLifecycle(
                context, Duration.ofNanos(50L), executor, nanoTime::get);
        var root = lifecycle.admitRoot();
        lifecycle.closeAdmission();
        var session = new LoomspanSession("late-mission", "entry", 3);
        session.attachAdmittedRoot(root);
        var mission = new MissionContext(session, "entry", "frame", null, nanoTime::get);
        var binding = new ExecutionBinding(session, mission, new PhysicalBranchContext(session));

        MissionLifecycle.PrimaryCancellation cancellation = mission.lifecycle().beginCancellation(
                binding, new IllegalStateException("local"), () -> "failure", false);

        assertThat(cancellation.deadlineNanos()).isEqualTo(150L);
        root.close();
        lifecycle.destroy();
    }

    @Test
    void repeatedCloseDoesNotRenewTheDeadline()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var nanoTime = new AtomicLong(100L);
        var lifecycle = new FrameworkExecutionLifecycle(
                context, Duration.ofNanos(50L), executor, nanoTime::get);

        lifecycle.closeAdmission();
        nanoTime.set(1_000L);
        lifecycle.closeAdmission();

        assertThat(lifecycle.deadlineNanos()).isEqualTo(150L);
        lifecycle.destroy();
    }

    @Test
    void saturatedDeadlineStillConsumesElapsedTime()
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var nanoTime = new AtomicLong(100L);
        var lifecycle = new FrameworkExecutionLifecycle(
                context, Duration.ofNanos(Long.MAX_VALUE - 50L), executor, nanoTime::get);

        lifecycle.closeAdmission();
        nanoTime.set(200L);

        assertThat(lifecycle.deadlineNanos()).isEqualTo(Long.MAX_VALUE);
        assertThat(lifecycle.remainingNanos()).isEqualTo(Long.MAX_VALUE - 200L);
        lifecycle.destroy();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void interruptedStopWaitsForAdmittedRootInsteadOfPublishingEarlyCutoff() throws Exception
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor);
        var root = lifecycle.admitRoot();
        var session = new LoomspanSession("interrupted-stop", "entry", 3);
        session.attachAdmittedRoot(root);
        var mission = new MissionContext(session, "entry", "frame", null);
        var future = new FutureTask<Void>(() -> null);
        mission.lifecycle().registerOwningFuture(future);
        var stopReturned = new AtomicBoolean();
        Thread stopThread = Thread.ofVirtual().start(() -> {
            lifecycle.stop();
            stopReturned.set(true);
        });
        long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!isWaiting(stopThread) && System.nanoTime() < waitDeadline)
            Thread.onSpinWait();
        assertThat(isWaiting(stopThread)).isTrue();

        stopThread.interrupt();
        Thread.sleep(25L);

        assertThat(stopReturned).isFalse();
        assertThat(future.isCancelled()).isFalse();
        root.close();
        stopThread.join(Duration.ofSeconds(1));
        assertThat(stopReturned).isTrue();
        assertThat(stopThread.isInterrupted()).isTrue();
        lifecycle.destroy();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void interruptedStopKeepsWaitingForExecutorWithinTheSharedBudget() throws Exception
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var workStarted = new CountDownLatch(1);
        var releaseWork = new AtomicBoolean();
        executor.submit(() -> {
            workStarted.countDown();
            while (!releaseWork.get()) Thread.onSpinWait();
        });
        assertThat(workStarted.await(1, TimeUnit.SECONDS)).isTrue();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor);
        var stopReturned = new AtomicBoolean();
        Thread stopThread = Thread.ofVirtual().start(() -> {
            lifecycle.stop();
            stopReturned.set(true);
        });
        long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!isWaiting(stopThread) && System.nanoTime() < waitDeadline)
            Thread.onSpinWait();
        assertThat(isWaiting(stopThread)).isTrue();

        try
        {
            stopThread.interrupt();
            Thread.sleep(25L);

            assertThat(stopReturned).isFalse();
        }
        finally
        {
            releaseWork.set(true);
        }
        stopThread.join(Duration.ofSeconds(1));
        assertThat(stopReturned).isTrue();
        assertThat(stopThread.isInterrupted()).isTrue();
        lifecycle.destroy();
    }

    private static boolean isWaiting(Thread thread)
    {
        return thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING;
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void destructionFallbackDoesNotWaitForUncooperativeExecutorWork() throws Exception
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var release = new AtomicBoolean();
        executor.submit(() -> {
            started.countDown();
            while (!release.get()) Thread.onSpinWait();
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor);

        lifecycle.destroy();

        assertThat(executor.isShutdown()).isTrue();
        assertThat(executor.isTerminated()).isFalse();
        release.set(true);
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void frameworkCutoffDoesNotWaitForMissionLockHeldByWriter() throws Exception
    {
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(25), executor);
        var root = lifecycle.admitRoot();
        var session = new LoomspanSession("blocked-writer", "entry", 3);
        session.attachAdmittedRoot(root);
        var mission = new MissionContext(session, "entry", "frame", null);
        var binding = new ExecutionBinding(session, mission, new PhysicalBranchContext(session));
        var writerEntered = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        var writerFailure = new AtomicReference<Throwable>();
        Thread writer = Thread.ofVirtual().start(() -> {
            try
            {
                MissionLifecycle.runIfWritable(binding, () -> {
                    writerEntered.countDown();
                    try { releaseWriter.await(); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                });
            }
            catch (Throwable ex) { writerFailure.set(ex); }
        });
        assertThat(writerEntered.await(1, TimeUnit.SECONDS)).isTrue();

        lifecycle.stop();

        releaseWriter.countDown();
        writer.join(Duration.ofSeconds(1));
        assertThat(writer.isAlive()).isFalse();
        assertThat(writerFailure.get()).isNull();
        assertThat(mission.lifecycle().primaryCancellation()).get()
                .extracting(MissionLifecycle.PrimaryCancellation::cause)
                .isInstanceOf(FrameworkShutdownException.class);
        assertThat(MissionLifecycle.runIfWritable(binding, () -> { })).isFalse();
        root.close();
        lifecycle.destroy();
    }
}
