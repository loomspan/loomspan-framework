package ai.loomspan.internal.runtime.observation;

public interface ActivityReplayBuffer
{
    ExecutionActivity append(ExecutionActivity activity);

    long currentCursor();

    ReplayResult replayAfter(long cursor, int limit);
}
