package ai.loomspan.internal.core;

import org.springframework.lang.Nullable;
import ai.loomspan.internal.skill.SkillGeneration;
import ai.loomspan.internal.skill.SkillAdmissionSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Immutable identity binding installed only for the duration of runtime work. */
public record ExecutionBinding(
        LoomspanSession session,
        @Nullable MissionContext mission,
        PhysicalBranchContext branch,
        SkillGeneration generation,
        SkillAdmissionSnapshot snapshot)
{
    public ExecutionBinding
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(generation, "generation must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (snapshot.generation(generation.namespace()) != generation)
            throw new IllegalArgumentException("Generation must belong to the captured snapshot");
        if (branch.session() != session)
        {
            throw new IllegalArgumentException("Physical branch belongs to a different session.");
        }
        if (mission != null && mission.session() != session)
        {
            throw new IllegalArgumentException("Mission belongs to a different session.");
        }
    }

    public static ExecutionBinding sessionOnly(LoomspanSession session, SkillAdmissionSnapshot snapshot)
    {
        return new ExecutionBinding(session, null, new PhysicalBranchContext(session), snapshot.entryGeneration(), snapshot);
    }

    public ExecutionBinding withMission(MissionContext nextMission)
    {
        return new ExecutionBinding(session, Objects.requireNonNull(nextMission, "mission must not be null"), branch, generation, snapshot);
    }

    public SkillGeneration entryGeneration() { return snapshot.entryGeneration(); }
    public String entryNamespace() { return snapshot.entry().namespace(); }
    public String currentNamespace() { return generation.namespace(); }
    public boolean isEntryTarget(CapabilityMetadata target) {
        return target.identity().equals(snapshot.entry()) && target.name().equals(session.entrySkill());
    }

    public ExecutionBinding withTarget(CapabilityMetadata target, MissionContext nextMission)
    {
        SkillGeneration selected = snapshot.generation(target.identity().namespace());
        if (!selected.owns(target)) throw new IllegalArgumentException("Target must belong to the captured generation");
        if (!target.name().equals(nextMission.skillName()))
            throw new IllegalArgumentException("Target must match the next mission");
        return new ExecutionBinding(session, nextMission, branch, selected, snapshot);
    }

    public ExecutionBinding forkBranch()
    {
        return new ExecutionBinding(session, mission, branch.fork(), generation, snapshot);
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
