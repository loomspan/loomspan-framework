package ai.loomspan.internal.runtime.observation;

import java.util.List;
import java.util.Optional;

public interface ActiveExecutionRegistry
{
    ActiveExecutionSnapshot replace(ActiveExecutionSnapshot snapshot);

    Optional<ActiveExecutionSnapshot> find(String sessionId);

    boolean remove(String sessionId);

    int activeCount();

    long highestOrdinal();

    List<ActiveExecutionSnapshot> newestFirst(long highWaterMark, long beforeOrdinal, int limit);
}
