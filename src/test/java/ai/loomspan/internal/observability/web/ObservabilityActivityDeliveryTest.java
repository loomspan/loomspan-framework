package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.runtime.observation.ExecutionActivity;
import ai.loomspan.internal.runtime.observation.ExecutionActivityKind;
import ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.ActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability;
import ai.loomspan.internal.runtime.observation.ReplayResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityActivityDeliveryTest
{
    @Test
    void rejectsSeventeenthValidStreamWithoutQueuingAndReclaimsSlot()
    {
        try (ObservabilityActivityDelivery delivery = delivery(new InMemoryActivityReplayBuffer()))
        {
            List<ObservabilityActivityDelivery.Admission> admissions = new ArrayList<>();
            for (int index = 0; index < 16; index++)
            {
                admissions.add(delivery.admit(0));
            }

            assertThat(delivery.admittedCount()).isEqualTo(16);
            assertThatThrownBy(() -> delivery.admit(0))
                    .isInstanceOfSatisfying(ObservabilityException.class, failure ->
                    {
                        assertThat(failure.problem().status()).isEqualTo(429);
                        assertThat(failure.problem().code()).isEqualTo(ObservabilityProblem.Code.LIMIT_EXCEEDED);
                    });

            admissions.getFirst().close();
            try (var replacement = delivery.admit(0))
            {
                assertThat(delivery.admittedCount()).isEqualTo(16);
            }
            admissions.stream().skip(1).forEach(ObservabilityActivityDelivery.Admission::close);
        }
    }

    @Test
    void fansOutStrictCursorOrderAndLiveFailureClosesSubscriber() throws Exception
    {
        InMemoryActivityReplayBuffer replay = new InMemoryActivityReplayBuffer();
        replay.append(activity("one"));
        replay.append(activity("two"));
        try (ObservabilityActivityDelivery delivery = delivery(replay))
        {
            var admission = delivery.admit(0);
            CountDownLatch frames = new CountDownLatch(2);
            CountDownLatch closed = new CountDownLatch(1);
            List<Long> cursors = java.util.Collections.synchronizedList(new ArrayList<>());
            delivery.activate(admission, new ObservabilityActivityDelivery.Subscriber()
            {
                @Override
                public boolean offer(byte[] frame, long cursor)
                {
                    cursors.add(cursor);
                    frames.countDown();
                    return true;
                }

                @Override
                public void close()
                {
                    admission.close();
                    closed.countDown();
                }
            });

            assertThat(frames.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(cursors).containsExactly(1L, 2L);

            delivery.liveUnavailable();
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(delivery.admittedCount()).isZero();
            assertThatThrownBy(() -> delivery.admit(2))
                    .isInstanceOfSatisfying(ObservabilityException.class,
                            failure -> assertThat(failure.problem().status()).isEqualTo(503));
        }
    }

    @Test
    void authoritativeLiveFailureRejectsAdmissionBeforeDeliverySignalArrives()
    {
        LiveMonitoringAvailability liveMonitoring = mock(LiveMonitoringAvailability.class);
        when(liveMonitoring.isAvailable()).thenReturn(false);
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new InMemoryActivityReplayBuffer(),
                liveMonitoring,
                new ObservabilityDtoMapper(),
                new ObservabilityJsonCodec()))
        {
            assertThatThrownBy(() -> delivery.admit(0))
                    .isInstanceOfSatisfying(ObservabilityException.class, failure ->
                    {
                        assertThat(failure.problem().status()).isEqualTo(503);
                        assertThat(failure.problem().code())
                                .isEqualTo(ObservabilityProblem.Code.LIVE_MONITORING_UNAVAILABLE);
                    });
            assertThat(delivery.admittedCount()).isZero();
        }
    }

    @Test
    void fansOutStrictCursorOrderAcrossReplayBatchBoundary() throws Exception
    {
        InMemoryActivityReplayBuffer replay = new InMemoryActivityReplayBuffer();
        for (int index = 0; index < 257; index++)
        {
            replay.append(activity("event-" + index));
        }
        try (ObservabilityActivityDelivery delivery = delivery(replay))
        {
            var admission = delivery.admit(0);
            CountDownLatch frames = new CountDownLatch(257);
            List<Long> cursors = java.util.Collections.synchronizedList(new ArrayList<>());
            delivery.activate(admission, new ObservabilityActivityDelivery.Subscriber()
            {
                @Override
                public boolean offer(byte[] frame, long cursor)
                {
                    cursors.add(cursor);
                    frames.countDown();
                    return true;
                }

                @Override
                public void close()
                {
                    admission.close();
                }
            });

            assertThat(frames.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(cursors).containsExactlyElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, 257).boxed().toList());
            admission.close();
        }
    }

    @Test
    void coalescesDispatchAndReusesSerializedFrameWithoutCouplingSubscribers()
    {
        InMemoryActivityReplayBuffer replay = new InMemoryActivityReplayBuffer();
        replay.append(activity("one"));
        replay.append(activity("two"));
        ExecutorService dispatcher = mock(ExecutorService.class);
        ScheduledExecutorService deadlines = mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        doAnswer(invocation ->
        {
            task.set(invocation.getArgument(0));
            return null;
        }).when(dispatcher).execute(any(Runnable.class));
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                replay,
                new LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                new ObservabilityJsonCodec(),
                dispatcher,
                deadlines))
        {
            var slowAdmission = delivery.admit(0);
            var healthyAdmission = delivery.admit(0);
            List<byte[]> slowFrames = new ArrayList<>();
            List<byte[]> healthyFrames = new ArrayList<>();
            delivery.activate(slowAdmission, subscriber(slowAdmission, slowFrames, false));
            delivery.activate(healthyAdmission, subscriber(healthyAdmission, healthyFrames, true));
            delivery.activityAvailable();
            delivery.activityAvailable();

            verify(dispatcher, times(1)).execute(any(Runnable.class));
            task.get().run();

            assertThat(slowFrames).hasSize(1);
            assertThat(healthyFrames).hasSize(2);
            assertThat(healthyFrames.getFirst()).isSameAs(slowFrames.getFirst());
            assertThat(delivery.admittedCount()).isEqualTo(1);
            healthyAdmission.close();
        }
    }

    @Test
    void publicationAfterAdmissionBeforeInitialDispatchIsNotSkipped()
    {
        InMemoryActivityReplayBuffer replay = new InMemoryActivityReplayBuffer();
        ExecutorService dispatcher = mock(ExecutorService.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        doAnswer(invocation ->
        {
            task.set(invocation.getArgument(0));
            return null;
        }).when(dispatcher).execute(any(Runnable.class));
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                replay,
                new LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                new ObservabilityJsonCodec(),
                dispatcher,
                mock(ScheduledExecutorService.class)))
        {
            var admission = delivery.admit(0);
            List<byte[]> frames = new ArrayList<>();
            delivery.activate(admission, subscriber(admission, frames, true));

            replay.append(activity("published-during-subscribe"));
            delivery.activityAvailable();
            task.get().run();

            assertThat(frames).hasSize(1);
            assertThat(new String(frames.getFirst(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"cursor\":\"1\"", "published-during-subscribe");
            admission.close();
        }
    }

    @Test
    void closesSubscriberWhenReplayCursorHasExpired()
    {
        ActivityReplayBuffer replay = mock(ActivityReplayBuffer.class);
        when(replay.replayAfter(4, ObservabilityDeliveryLimits.REPLAY_BATCH))
                .thenReturn(new ReplayResult(ReplayResult.Status.TOO_OLD, 20, List.of()));
        ExecutorService dispatcher = mock(ExecutorService.class);
        doAnswer(invocation ->
        {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(dispatcher).execute(any(Runnable.class));
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                replay,
                new LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                new ObservabilityJsonCodec(),
                dispatcher,
                mock(ScheduledExecutorService.class)))
        {
            var admission = delivery.admit(4);
            delivery.activate(admission, subscriber(admission, new ArrayList<>(), true));

            assertThat(delivery.admittedCount()).isZero();
        }
    }

    @Test
    void shutdownRejectsAdmissionAndStopsOwnedThreads() throws Exception
    {
        ObservabilityActivityDelivery delivery = delivery(new InMemoryActivityReplayBuffer());
        var admission = delivery.admit(0);
        delivery.scheduleDeadline(() -> {}, java.time.Duration.ofHours(1));
        delivery.activityAvailable();

        delivery.close();
        admission.close();

        assertThatThrownBy(() -> delivery.admit(0))
                .isInstanceOfSatisfying(ObservabilityException.class,
                        failure -> assertThat(failure.problem().status()).isEqualTo(503));
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (hasDeliveryThread() && System.nanoTime() < limit)
        {
            Thread.onSpinWait();
        }
        assertThat(hasDeliveryThread()).isFalse();
        assertThat(delivery.admittedCount()).isZero();
    }

    private static boolean hasDeliveryThread()
    {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .anyMatch(name -> name.startsWith("loomspan-activity-delivery-")
                        || name.startsWith("loomspan-activity-deadline-"));
    }

    private static ObservabilityActivityDelivery delivery(InMemoryActivityReplayBuffer replay)
    {
        return new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                replay,
                new LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                new ObservabilityJsonCodec());
    }

    private static ObservabilityActivityDelivery.Subscriber subscriber(
            ObservabilityActivityDelivery.Admission admission,
            List<byte[]> frames,
            boolean acceptsAll)
    {
        java.util.concurrent.atomic.AtomicBoolean open = new java.util.concurrent.atomic.AtomicBoolean(true);
        return new ObservabilityActivityDelivery.Subscriber()
        {
            @Override
            public boolean offer(byte[] frame, long cursor)
            {
                if (!open.get())
                {
                    return false;
                }
                frames.add(frame);
                return acceptsAll;
            }

            @Override
            public void close()
            {
                if (open.compareAndSet(true, false))
                {
                    admission.close();
                }
            }
        };
    }

    private static ExecutionActivity activity(String summary)
    {
        return new ExecutionActivity(
                0, "session", "trace", 1L, Instant.parse("2026-07-26T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, "ACTIVE",
                summary, Map.of(), 256);
    }
}
