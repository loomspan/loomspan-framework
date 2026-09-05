package ai.loomspan.internal.runtime.observation;

import tools.jackson.databind.JsonNode;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExecutionProjectionState
{
    final String sessionId;
    final LinkedHashMap<String, FrameNode> frames = new LinkedHashMap<>();
    String traceId;
    Instant startedAt;
    final String entrySkill;
    String phase = "STARTING";
    String summary = "Execution started";
    SessionUsageSnapshot usage = SessionUsageSnapshot.empty();
    TraceOutcome outcome;
    private String rootFrameId;
    private final Set<String> explicitAssignments = new HashSet<>();

    ExecutionProjectionState(String sessionId, String entrySkill)
    {
        this.sessionId = sessionId;
        if (entrySkill == null || entrySkill.isBlank()) throw new IllegalArgumentException("entrySkill must not be blank");
        this.entrySkill = entrySkill;
    }

    void openFrame(TraceRecord record)
    {
        String frameId = requireNonBlank(record.frameId(), "frameId");
        TraceFrameType frameType = requireNonNull(record.frameType(), "frameType");
        String route = requireNonBlank(record.route(), "route");
        String parentFrameId = record.parentFrameId();
        if (frames.containsKey(frameId)) throw invalid("duplicate frame open");
        if (frameId.equals(parentFrameId)) throw invalid("frame cannot parent itself");
        if (parentFrameId == null)
        {
            if (rootFrameId != null || frameType != TraceFrameType.ROOT_MISSION)
                throw invalid("trace must contain exactly one root mission");
            rootFrameId = frameId;
        }
        else
        {
            FrameNode parent = frames.get(parentFrameId);
            if (parent == null || !parent.open) throw invalid("frame parent must be known and open");
            parent.openChildCount++;
        }
        Assignment assignment = assignment(record.data());
        if (assignment != null)
        {
            if (frameType != TraceFrameType.STEP_EXECUTION)
                throw invalid("assignment metadata is valid only on a step execution frame");
            if (!explicitAssignments.add(assignment.planId + "\u0000" + assignment.taskId))
                throw invalid("duplicate explicit plan/task assignment");
        }
        frames.put(frameId, new FrameNode(frameId, parentFrameId, frameType, route, record.sequence(), assignment));
    }

    void closeFrame(TraceRecord record)
    {
        String frameId = requireNonBlank(record.frameId(), "frameId");
        FrameNode node = frames.get(frameId);
        if (node == null || !node.open) throw invalid("frame close must reference an open frame");
        if (node.openChildCount != 0) throw invalid("frame cannot close while a child is open");
        if (node.frameType != record.frameType()
                || !java.util.Objects.equals(node.parentFrameId, record.parentFrameId())
                || !node.route.equals(record.route()))
            throw invalid("frame close definition conflicts with its open");
        node.open = false;
        if (node.parentFrameId != null)
        {
            FrameNode parent = frames.get(node.parentFrameId);
            if (parent == null || parent.openChildCount <= 0) throw invalid("frame parent child count is inconsistent");
            parent.openChildCount--;
        }
    }

    List<ActiveExecutionSnapshot.ActiveBranch> activeBranches()
    {
        List<FrameNode> leaves = frames.values().stream()
                .filter(node -> node.open && node.openChildCount == 0)
                .sorted(Comparator.comparingLong(node -> node.openSequence)).toList();
        List<ActiveExecutionSnapshot.ActiveBranch> branches = new ArrayList<>(leaves.size());
        for (FrameNode leaf : leaves)
        {
            List<FrameNode> reversed = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            FrameNode current = leaf;
            Assignment nearest = null;
            while (current != null)
            {
                if (!current.open || !seen.add(current.frameId)) throw invalid("active frame ancestry is closed or cyclic");
                reversed.add(current);
                if (nearest == null && current.assignment != null) nearest = current.assignment;
                current = current.parentFrameId == null ? null : frames.get(current.parentFrameId);
            }
            java.util.Collections.reverse(reversed);
            if (reversed.isEmpty() || !reversed.getFirst().frameId.equals(rootFrameId))
                throw invalid("active frame ancestry does not reach the root");
            List<ActiveExecutionSnapshot.FramePathEntry> path = reversed.stream()
                    .map(node -> new ActiveExecutionSnapshot.FramePathEntry(node.frameId, node.frameType, node.route)).toList();
            branches.add(nearest == null
                    ? new ActiveExecutionSnapshot.ActiveBranch(null, null, null, null, null, path)
                    : new ActiveExecutionSnapshot.ActiveBranch(nearest.planId, nearest.taskId, nearest.stepNumber,
                            nearest.parallelGroup, nearest.effectiveConcurrency, path));
        }
        return List.copyOf(branches);
    }

    Map<String, FrameNode> frameView() { return Map.copyOf(frames); }

    @Nullable
    private Assignment assignment(@Nullable JsonNode data)
    {
        if (data == null || !data.isObject()) return null;
        boolean hasAssignmentOnlyField = data.has("assignedTaskId") || data.has("parallelGroup")
                || data.has("effectiveConcurrency");
        if (!hasAssignmentOnlyField) return null;
        JsonNode plan = data.get("planId");
        JsonNode task = data.get("assignedTaskId");
        JsonNode step = data.get("stepNumber");
        JsonNode effective = data.get("effectiveConcurrency");
        JsonNode group = data.get("parallelGroup");
        if (plan == null || !plan.isTextual() || plan.textValue().isBlank()
                || task == null || !task.isTextual() || task.textValue().isBlank()
                || step == null || !step.isIntegralNumber() || step.intValue() <= 0
                || effective == null || !effective.isBoolean()
                || (group != null && !group.isNull() && (!group.isTextual() || group.textValue().isBlank())))
            throw invalid("assigned frame metadata is incomplete or malformed");
        String parallelGroup = group == null || group.isNull() ? null : group.textValue();
        if (effective.booleanValue() && parallelGroup == null)
            throw invalid("concurrent assigned frame requires a parallel group");
        return new Assignment(plan.textValue(), task.textValue(), step.intValue(), parallelGroup, effective.booleanValue());
    }

    private static IllegalArgumentException invalid(String detail)
    {
        return new IllegalArgumentException("Invalid frame relationship: " + detail);
    }

    private static String requireNonBlank(String value, String name)
    {
        if (value == null || value.isBlank()) throw invalid(name + " must be nonblank");
        return value;
    }

    private static <T> T requireNonNull(T value, String name)
    {
        if (value == null) throw invalid(name + " must be present");
        return value;
    }

    static final class FrameNode
    {
        final String frameId;
        final String parentFrameId;
        final TraceFrameType frameType;
        final String route;
        final long openSequence;
        final Assignment assignment;
        boolean open = true;
        int openChildCount;

        FrameNode(String frameId, String parentFrameId, TraceFrameType frameType, String route,
                long openSequence, Assignment assignment)
        {
            this.frameId = frameId;
            this.parentFrameId = parentFrameId;
            this.frameType = frameType;
            this.route = route;
            this.openSequence = openSequence;
            this.assignment = assignment;
        }
    }

    record Assignment(String planId, String taskId, int stepNumber, @Nullable String parallelGroup,
            boolean effectiveConcurrency) {}
}
