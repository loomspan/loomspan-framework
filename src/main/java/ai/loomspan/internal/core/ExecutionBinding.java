package ai.loomspan.internal.core;

import org.springframework.lang.Nullable;
import ai.loomspan.internal.skill.SkillGeneration;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Immutable identity binding installed only for the duration of runtime work. */
public record ExecutionBinding(
        LoomspanSession session,
        @Nullable MissionContext mission,
        PhysicalBranchContext branch,
        SkillGeneration generation)
{
    public ExecutionBinding
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(generation, "generation must not be null");
        if (branch.session() != session)
        {
            throw new IllegalArgumentException("Physical branch belongs to a different session.");
        }
        if (mission != null && mission.session() != session)
        {
            throw new IllegalArgumentException("Mission belongs to a different session.");
        }
    }

    public static ExecutionBinding sessionOnly(LoomspanSession session, SkillGeneration generation)
    {
        return new ExecutionBinding(session, null, new PhysicalBranchContext(session), generation);
    }

    public ExecutionBinding withMission(MissionContext nextMission)
    {
        return new ExecutionBinding(session, Objects.requireNonNull(nextMission, "mission must not be null"), branch, generation);
    }

    public ExecutionBinding forkBranch()
    {
        return new ExecutionBinding(session, mission, branch.fork(), generation);
    }

    public Optional<MissionContext> currentMission()
    {
        return Optional.ofNullable(mission);
    }

    public MissionContext requireMission()
    {
        if (mission == null) throw new IllegalStateException("No YAML mission is bound to the current execution.");
        return mission;
    }

    public boolean runIfWritable(Runnable action)
    {
        return MissionLifecycle.runIfWritable(this, action);
    }

    public <T> T requireWritable(Supplier<T> action)
    {
        return MissionLifecycle.requireWritable(this, action);
    }
}
