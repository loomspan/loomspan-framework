package ai.loomspan.internal.runtime.observation;

import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ActiveExecutionSnapshot(
        String sessionId,
        String traceId,
        long registryOrdinal,
        long lastCanonicalSequence,
        Instant startedAt,
        Instant updatedAt,
        String entrySkill,
        String phase,
        String summary,
        List<ActiveBranch> activeBranches,
        SessionUsageSnapshot usage,
        @Nullable TraceOutcome outcome)
{
    public ActiveExecutionSnapshot
    {
        sessionId = requireNonBlank(sessionId, "sessionId");
        traceId = requireNonBlank(traceId, "traceId");
        if (registryOrdinal < 0)
        {
            throw new IllegalArgumentException("registryOrdinal must not be negative");
        }
        if (lastCanonicalSequence <= 0)
        {
            throw new IllegalArgumentException("lastCanonicalSequence must be positive");
        }
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        entrySkill = requireNonBlank(entrySkill, "entrySkill");
        phase = ExecutionObservationLimits.truncate(requireNonBlank(phase, "phase"),
                ExecutionObservationLimits.TEXT_CODE_POINTS);
        summary = ExecutionObservationLimits.truncate(summary, ExecutionObservationLimits.SUMMARY_CODE_POINTS);
        activeBranches = activeBranches == null ? List.of() : List.copyOf(activeBranches);
        validateBranches(activeBranches);
        usage = usage == null ? SessionUsageSnapshot.empty() : usage;
    }

    ActiveExecutionSnapshot withRegistryOrdinal(long ordinal)
    {
        return new ActiveExecutionSnapshot(
                sessionId, traceId, ordinal, lastCanonicalSequence, startedAt, updatedAt, entrySkill,
                phase, summary, activeBranches, usage, outcome);
    }

    public record ActiveBranch(
            @Nullable String planId,
            @Nullable String taskId,
            @Nullable Integer stepNumber,
            @Nullable String parallelGroup,
            @Nullable Boolean effectiveConcurrency,
            List<FramePathEntry> path)
    {
        public ActiveBranch
        {
            planId = requireNonBlankIfPresent(planId, "planId");
            taskId = requireNonBlankIfPresent(taskId, "taskId");
            parallelGroup = requireNonBlankIfPresent(parallelGroup, "parallelGroup");
            path = path == null ? List.of() : List.copyOf(path);
            if (path.isEmpty())
            {
                throw new IllegalArgumentException("active branch path must not be empty");
            }

            boolean assigned = taskId != null;
            boolean completeAssignment = planId != null && taskId != null && stepNumber != null
                    && effectiveConcurrency != null;
            boolean emptyAssignment = planId == null && taskId == null && stepNumber == null
                    && parallelGroup == null && effectiveConcurrency == null;
            if (!completeAssignment && !emptyAssignment)
            {
                throw new IllegalArgumentException("active branch assignment metadata must be all present or all null");
            }
            if (assigned && stepNumber <= 0)
            {
                throw new IllegalArgumentException("assigned active branch stepNumber must be positive");
            }
            if (Boolean.TRUE.equals(effectiveConcurrency) && parallelGroup == null)
            {
                throw new IllegalArgumentException("effectiveConcurrency true requires parallelGroup");
            }

            Set<String> frameIds = new HashSet<>();
            for (FramePathEntry entry : path)
            {
                Objects.requireNonNull(entry, "active branch path entry must not be null");
                if (!frameIds.add(entry.frameId()))
                {
                    throw new IllegalArgumentException("active branch path must not repeat a frameId");
                }
            }
            if (path.getFirst().frameType() != TraceFrameType.ROOT_MISSION)
            {
                throw new IllegalArgumentException("active branch path must begin at the root mission");
            }
        }
    }

    public record FramePathEntry(String frameId, TraceFrameType frameType, String route)
    {
        public FramePathEntry
        {
            frameId = requireNonBlank(frameId, "frameId");
            Objects.requireNonNull(frameType, "frameType must not be null");
            route = ExecutionObservationLimits.truncate(requireNonBlank(route, "route"),
                    ExecutionObservationLimits.TEXT_CODE_POINTS);
        }
    }

    private static void validateBranches(List<ActiveBranch> branches)
    {
        if (branches.isEmpty()) return;
        FramePathEntry root = branches.getFirst().path().getFirst();
        Set<String> leaves = new HashSet<>();
        Map<String, FramePathEntry> definitions = new HashMap<>();
        Map<String, String> parents = new HashMap<>();
        for (ActiveBranch branch : branches)
        {
            Objects.requireNonNull(branch, "active branch must not be null");
            if (!root.equals(branch.path().getFirst()))
            {
                throw new IllegalArgumentException("active branches must share one identical root definition");
            }
            if (!leaves.add(branch.path().getLast().frameId()))
            {
                throw new IllegalArgumentException("active branch leaf frameId must be unique");
            }
            for (int index = 0; index < branch.path().size(); index++)
            {
                FramePathEntry entry = branch.path().get(index);
                FramePathEntry existing = definitions.putIfAbsent(entry.frameId(), entry);
                if (existing != null && !existing.equals(entry))
                {
                    throw new IllegalArgumentException("shared active frame definitions must be identical");
                }
                if (index > 0)
                {
                    String parentId = branch.path().get(index - 1).frameId();
                    String existingParent = parents.putIfAbsent(entry.frameId(), parentId);
                    if (existingParent != null && !existingParent.equals(parentId))
                    {
                        throw new IllegalArgumentException("shared active frame prefixes must be coherent");
                    }
                }
            }
        }
    }

    private static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Nullable
    private static String requireNonBlankIfPresent(@Nullable String value, String name)
    {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
