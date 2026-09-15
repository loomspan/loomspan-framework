package ai.loomspan.internal.runtime.observation;

import java.util.List;

public record ReplayResult(Status status, long currentCursor, List<ExecutionActivity> activities)
{
    public ReplayResult
    {
        activities = activities == null ? List.of() : List.copyOf(activities);
    }

    public enum Status
    {
        AVAILABLE,
        EMPTY,
        TOO_OLD,
        FUTURE
    }
}
