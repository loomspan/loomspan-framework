package ai.loomspan.internal.runtime.observation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryActivityReplayBufferTest
{
    @Test
    void usesZeroAsBeforeFirstAndEvictsWholeOldestEvents()
    {
        InMemoryActivityReplayBuffer buffer = new InMemoryActivityReplayBuffer(2, 1_000);

        ExecutionActivity first = buffer.append(activity("one", 200));
        ExecutionActivity second = buffer.append(activity("two", 200));
        ExecutionActivity third = buffer.append(activity("three", 200));

        assertThat(first.deliveryCursor()).isEqualTo(1);
        assertThat(second.deliveryCursor()).isEqualTo(2);
        assertThat(third.deliveryCursor()).isEqualTo(3);
        assertThat(buffer.replayAfter(0, 10).status()).isEqualTo(ReplayResult.Status.TOO_OLD);
        assertThat(buffer.replayAfter(1, 10).activities())
                .extracting(ExecutionActivity::sessionId)
                .containsExactly("two", "three");
        assertThat(buffer.retainedCount()).isEqualTo(2);
        assertThat(buffer.retainedBytes()).isEqualTo(400);
    }

    @Test
    void distinguishesEmptyCurrentAndFutureRanges()
    {
        InMemoryActivityReplayBuffer buffer = new InMemoryActivityReplayBuffer();
        assertThat(buffer.replayAfter(0, 10).status()).isEqualTo(ReplayResult.Status.EMPTY);
        buffer.append(activity("one", 200));
        assertThat(buffer.replayAfter(1, 10).status()).isEqualTo(ReplayResult.Status.EMPTY);
        assertThat(buffer.replayAfter(2, 10).status()).isEqualTo(ReplayResult.Status.FUTURE);
    }

    @Test
    void failsInsteadOfWrappingDeliveryCursor()
    {
        AtomicLong value = new AtomicLong(Long.MAX_VALUE);
        InMemoryActivityReplayBuffer buffer = new InMemoryActivityReplayBuffer(10, 10_000, value::getAndIncrement);
        buffer.append(activity("one", 200));
        assertThatThrownBy(() -> buffer.append(activity("two", 200)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void evictsAtByteLimitAndEnforcesBothBounds()
    {
        InMemoryActivityReplayBuffer buffer = new InMemoryActivityReplayBuffer(3, 350);
        buffer.append(activity("one", 175));
        buffer.append(activity("two", 175));
        buffer.append(activity("three", 175));

        assertThat(buffer.retainedBytes()).isEqualTo(350);
        assertThat(buffer.retainedCount()).isEqualTo(2);
        assertThat(buffer.replayAfter(1, 10).activities())
                .extracting(ExecutionActivity::sessionId)
                .containsExactly("two", "three");
    }

    @Test
    void supportsConcurrentPublishersWithoutCursorReuse() throws Exception
    {
        InMemoryActivityReplayBuffer buffer = new InMemoryActivityReplayBuffer(256, 1_000_000);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            var futures = java.util.stream.IntStream.range(0, 128)
                    .mapToObj(index -> executor.submit(() ->
                            buffer.append(activity("session-" + index, 100))))
                    .toList();
            for (var future : futures)
            {
                future.get();
            }
        }

        assertThat(buffer.replayAfter(0, 256).activities())
                .extracting(ExecutionActivity::deliveryCursor)
                .isSorted()
                .doesNotHaveDuplicates();
    }

    @Test
    void enforcesProductionCountAndByteBounds()
    {
        InMemoryActivityReplayBuffer countBound = new InMemoryActivityReplayBuffer();
        for (int index = 0; index <= ExecutionObservationLimits.REPLAY_EVENTS; index++)
        {
            countBound.append(activity("count-" + index, 100));
        }
        assertThat(countBound.retainedCount()).isEqualTo(ExecutionObservationLimits.REPLAY_EVENTS);

        InMemoryActivityReplayBuffer byteBound = new InMemoryActivityReplayBuffer();
        int publications = (int) (ExecutionObservationLimits.REPLAY_UTF8_BYTES
                / ExecutionObservationLimits.ACTIVITY_UTF8_BYTES) + 1;
        for (int index = 0; index < publications; index++)
        {
            byteBound.append(activity("bytes-" + index, ExecutionObservationLimits.ACTIVITY_UTF8_BYTES));
        }
        assertThat(byteBound.retainedBytes())
                .isLessThanOrEqualTo(ExecutionObservationLimits.REPLAY_UTF8_BYTES);
        assertThat(byteBound.retainedCount()).isEqualTo(publications - 1);
    }

    private ExecutionActivity activity(String sessionId, int weight)
    {
        return new ExecutionActivity(
                0, sessionId, "trace-" + sessionId, 1L, Instant.parse("2026-07-24T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, null,
                "started", Map.of(), weight);
    }
}
