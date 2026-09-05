package ai.loomspan.internal.core;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Test-only construction of a complete mission binding. */
public final class TestExecutionBindings
{
    private TestExecutionBindings() {}

    public static ExecutionBinding missionBinding(LoomspanSession session)
    {
        Objects.requireNonNull(session, "session must not be null");
        String missionFrameId = "mission-frame-" + UUID.randomUUID();
        MissionContext mission = new MissionContext(session, session.entrySkill(), missionFrameId, null);
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        branch.push(new ExecutionFrame(missionFrameId, null, OperationType.CAPABILITY,
                TraceFrameType.ROOT_MISSION, session.entrySkill(), Map.of(), Instant.now()));
        return new ExecutionBinding(session, mission, branch);
    }

    public static <T> T callWithSession(LoomspanSession session, Callable<T> action)
    {
        try
        {
            return ExecutionBindingScope.callWith(missionBinding(session), action);
        }
        catch (RuntimeException | Error ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    public static <T> T callWithCurrentSessionMission(Callable<T> action)
    {
        ExecutionBinding outer = ExecutionBindingScope.requireCurrent();
        String missionFrameId = "mission-frame-" + UUID.randomUUID();
        MissionContext mission = new MissionContext(
                outer.session(), outer.session().entrySkill(), missionFrameId, outer.mission());
        ExecutionFrame frame = new ExecutionFrame(missionFrameId,
                outer.branch().leaf().map(ExecutionFrame::frameId).orElse(null), OperationType.CAPABILITY,
                TraceFrameType.ROOT_MISSION, outer.session().entrySkill(), Map.of(), Instant.now());
        outer.branch().push(frame);
        try
        {
            return ExecutionBindingScope.callWith(outer.withMission(mission), action);
        }
        catch (RuntimeException | Error ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
        finally
        {
            outer.branch().close(frame);
        }
    }
}
