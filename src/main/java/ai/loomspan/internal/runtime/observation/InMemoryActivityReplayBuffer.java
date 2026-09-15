package ai.loomspan.internal.runtime.observation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class InMemoryActivityReplayBuffer implements ActivityReplayBuffer
{
    private final int maximumEvents;
    private final long maximumBytes;
    private final AtomicLong cursor = new AtomicLong();
    private final LongSupplier cursorSupplier;
    private final Deque<ExecutionActivity> activities = new ArrayDeque<>();
    private long retainedBytes;
    private long publishedCursor;

    public InMemoryActivityReplayBuffer()
    {
        this(ExecutionObservationLimits.REPLAY_EVENTS, ExecutionObservationLimits.REPLAY_UTF8_BYTES);
    }

    InMemoryActivityReplayBuffer(int maximumEvents, long maximumBytes)
    {
        this.maximumEvents = requirePositive(maximumEvents, "maximumEvents");
        this.maximumBytes = requirePositive(maximumBytes, "maximumBytes");
        this.cursorSupplier = this::nextCursor;
    }

    InMemoryActivityReplayBuffer(int maximumEvents, long maximumBytes, LongSupplier cursorSupplier)
    {
        this.maximumEvents = requirePositive(maximumEvents, "maximumEvents");
        this.maximumBytes = requirePositive(maximumBytes, "maximumBytes");
        this.cursorSupplier = Objects.requireNonNull(cursorSupplier, "cursorSupplier must not be null");
    }

    @Override
    public synchronized ExecutionActivity append(ExecutionActivity activity)
    {
        Objects.requireNonNull(activity, "activity must not be null");
        if (activity.retainedWeight() > maximumBytes)
        {
            throw new IllegalArgumentException("activity exceeds replay byte capacity");
        }
        long next = cursorSupplier.getAsLong();
        if (next <= 0)
        {
            throw new IllegalStateException("Delivery cursor must be positive and must not wrap");
        }
        if (next <= publishedCursor)
        {
            throw new IllegalStateException("Delivery cursor must be strictly increasing");
        }
        publishedCursor = next;
        ExecutionActivity positioned = activity.withDeliveryCursor(next);
        activities.addLast(positioned);
        retainedBytes += positioned.retainedWeight();
        while (activities.size() > maximumEvents || retainedBytes > maximumBytes)
        {
            retainedBytes -= activities.removeFirst().retainedWeight();
        }
        return positioned;
    }

    @Override
    public synchronized long currentCursor()
    {
        return publishedCursor;
    }

    @Override
    public synchronized ReplayResult replayAfter(long afterCursor, int limit)
    {
        if (afterCursor < 0)
        {
            throw new IllegalArgumentException("cursor must not be negative");
        }
        if (limit <= 0)
        {
            throw new IllegalArgumentException("limit must be positive");
        }
        long current = currentCursor();
        if (afterCursor > current)
        {
            return new ReplayResult(ReplayResult.Status.FUTURE, current, List.of());
        }
        if (activities.isEmpty() || afterCursor == current)
        {
            return new ReplayResult(ReplayResult.Status.EMPTY, current, List.of());
        }
        long oldest = activities.getFirst().deliveryCursor();
        if (afterCursor < oldest - 1)
        {
            return new ReplayResult(ReplayResult.Status.TOO_OLD, current, List.of());
        }
        List<ExecutionActivity> result = new ArrayList<>(Math.min(limit, activities.size()));
        for (ExecutionActivity activity : activities)
        {
            if (activity.deliveryCursor() > afterCursor)
            {
                result.add(activity);
                if (result.size() == limit)
                {
                    break;
                }
            }
        }
        return new ReplayResult(
                result.isEmpty() ? ReplayResult.Status.EMPTY : ReplayResult.Status.AVAILABLE,
                current,
                result);
    }

    synchronized long retainedBytes()
    {
        return retainedBytes;
    }

    synchronized int retainedCount()
    {
        return activities.size();
    }

    private long nextCursor()
    {
        return cursor.updateAndGet(current ->
        {
            if (current == Long.MAX_VALUE)
            {
                throw new IllegalStateException("Delivery cursor exhausted");
            }
            return current + 1;
        });
    }

    private static int requirePositive(int value, String name)
    {
        if (value <= 0)
        {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long requirePositive(long value, String name)
    {
        if (value <= 0)
        {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
