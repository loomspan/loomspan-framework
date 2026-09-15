package ai.loomspan.internal.runtime.observation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class InMemoryActiveExecutionRegistry implements ActiveExecutionRegistry
{
    private final ConcurrentHashMap<String, ActiveExecutionSnapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicLong ordinal = new AtomicLong();
    private final LongSupplier ordinalSupplier;
    private volatile long assignedHighWater;

    public InMemoryActiveExecutionRegistry()
    {
        this.ordinalSupplier = this::nextOrdinal;
    }

    InMemoryActiveExecutionRegistry(LongSupplier ordinalSupplier)
    {
        this.ordinalSupplier = Objects.requireNonNull(ordinalSupplier, "ordinalSupplier must not be null");
    }

    @Override
    public ActiveExecutionSnapshot replace(ActiveExecutionSnapshot snapshot)
    {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return snapshots.compute(snapshot.sessionId(), (sessionId, current) ->
        {
            long assigned = current == null ? assignOrdinal() : current.registryOrdinal();
            return snapshot.withRegistryOrdinal(assigned);
        });
    }

    @Override
    public Optional<ActiveExecutionSnapshot> find(String sessionId)
    {
        return Optional.ofNullable(snapshots.get(sessionId));
    }

    @Override
    public boolean remove(String sessionId)
    {
        return snapshots.remove(sessionId) != null;
    }

    @Override
    public int activeCount()
    {
        return snapshots.size();
    }

    @Override
    public long highestOrdinal()
    {
        return assignedHighWater;
    }

    @Override
    public List<ActiveExecutionSnapshot> newestFirst(long highWaterMark, long beforeOrdinal, int limit)
    {
        if (highWaterMark < 0 || beforeOrdinal < 0)
        {
            throw new IllegalArgumentException("ordinal positions must not be negative");
        }
        if (limit <= 0)
        {
            throw new IllegalArgumentException("limit must be positive");
        }
        long effectiveHighWater = highWaterMark == 0 ? Long.MAX_VALUE : highWaterMark;
        long effectiveBefore = beforeOrdinal == 0 ? Long.MAX_VALUE : beforeOrdinal;
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.registryOrdinal() <= effectiveHighWater)
                .filter(snapshot -> snapshot.registryOrdinal() < effectiveBefore)
                .sorted(Comparator.comparingLong(ActiveExecutionSnapshot::registryOrdinal).reversed())
                .limit(limit)
                .toList();
    }

    private long nextOrdinal()
    {
        return ordinal.updateAndGet(current ->
        {
            if (current == Long.MAX_VALUE)
            {
                throw new IllegalStateException("Registry ordinal exhausted");
            }
            return current + 1;
        });
    }

    private synchronized long assignOrdinal()
    {
        long value = ordinalSupplier.getAsLong();
        if (value <= 0 || value <= assignedHighWater)
        {
            throw new IllegalStateException("Registry ordinal must be positive, unique, and strictly increasing");
        }
        assignedHighWater = value;
        return value;
    }
}
