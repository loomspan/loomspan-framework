package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;

import java.util.List;

class PhysicalBranchContextTest
{
    @Test
    void enforcesLifoAndDepthWithoutCorruptingThePath()
    {
        PhysicalBranchContext branch = new PhysicalBranchContext(new LoomspanSession("branch", "entry", 2));
        ExecutionFrame root = frame("root", TraceFrameType.ROOT_MISSION);
        ExecutionFrame model = frame("model", TraceFrameType.MODEL_CALL);
        ExecutionFrame child = frame("child", TraceFrameType.SKILL_EXECUTION);
        branch.push(root);
        branch.push(model);
        branch.push(child);
        assertThat(branch.rootToLeafSnapshot()).containsExactly(root, model, child);
        assertThatThrownBy(() -> branch.close(root)).isInstanceOf(IllegalStateException.class);
        assertThat(branch.requireLeaf()).isEqualTo(child);
        assertThatThrownBy(() -> branch.push(frame("overflow", TraceFrameType.RETRY)))
                .isInstanceOf(LoomspanStackOverflowException.class);
        assertThat(branch.requireLeaf()).isEqualTo(child);
    }

    @Test
    void closesAbsentFramesAsNoOpAndRollsBackOnlyCurrentLeaf()
    {
        PhysicalBranchContext branch = new PhysicalBranchContext(new LoomspanSession("rollback", "entry", 4));
        ExecutionFrame root = frame("root", TraceFrameType.ROOT_MISSION);
        ExecutionFrame child = frame("child", TraceFrameType.SKILL_EXECUTION);
        branch.push(root);
        branch.push(child);

        assertThat(branch.close(frame("absent", TraceFrameType.RETRY)).frameId()).isEqualTo("absent");
        assertThat(branch.rollback(root)).isFalse();
        assertThat(branch.rollback(child)).isTrue();
        assertThat(branch.requireLeaf()).isEqualTo(root);
        assertThat(branch.close(root)).isEqualTo(root);
        assertThat(branch.leaf()).isEmpty();
    }

    @Test
    void returnsImmutableSnapshotsInBothDirections()
    {
        PhysicalBranchContext branch = new PhysicalBranchContext(new LoomspanSession("snapshots", "entry", 4));
        ExecutionFrame root = frame("root", TraceFrameType.ROOT_MISSION);
        ExecutionFrame child = frame("child", TraceFrameType.SKILL_EXECUTION);
        branch.push(root);
        branch.push(child);
        var rootToLeaf = branch.rootToLeafSnapshot();
        var leafToRoot = branch.leafToRootSnapshot();

        assertThat(rootToLeaf).containsExactly(root, child);
        assertThat(leafToRoot).containsExactly(child, root);
        assertThatThrownBy(() -> rootToLeaf.add(frame("extra", TraceFrameType.RETRY)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tracksLastNonEmptyDiagnosticOfEachType()
    {
        PhysicalBranchContext branch = new PhysicalBranchContext(new LoomspanSession("diagnostics", "entry", 3));
        LinterOutcome linter = new LinterOutcome(
                "entry", "regex", 1, 0, 1, LinterOutcomeStatus.PASSED, "retry");
        OutputSchemaOutcome output = new OutputSchemaOutcome(
                "entry", null, 1, 0, 1, OutputSchemaOutcomeStatus.PASSED, List.of());
        branch.recordLinterOutcome(linter);
        branch.recordOutputSchemaOutcome(output);

        BranchDiagnosticDelta delta = branch.diagnosticDelta();
        assertThat(delta.linterOutcome()).isSameAs(linter);
        assertThat(delta.outputSchemaOutcome()).isSameAs(output);
    }

    @Test
    void forkRetainsImmutableAncestorPathAndOwnsOnlyLocalFrames()
    {
        PhysicalBranchContext parent = new PhysicalBranchContext(new LoomspanSession("fork", "entry", 4));
        ExecutionFrame root = frame("root", TraceFrameType.ROOT_MISSION);
        ExecutionFrame coordinator = frame("coordinator", TraceFrameType.SKILL_EXECUTION);
        parent.push(root);
        parent.push(coordinator);

        PhysicalBranchContext fork = parent.fork();
        ExecutionFrame worker = new ExecutionFrame("worker", coordinator.frameId(), OperationType.SKILL,
                TraceFrameType.STEP_EXECUTION, "worker", Map.of(), Instant.EPOCH);
        fork.push(worker);

        assertThat(fork.rootToLeafSnapshot()).containsExactly(root, coordinator, worker);
        assertThat(fork.localDepth()).isEqualTo(1);
        assertThat(parent.rootToLeafSnapshot()).containsExactly(root, coordinator);
        assertThat(fork.owns(coordinator)).isFalse();
        assertThat(fork.close(coordinator)).isSameAs(coordinator);
        assertThat(fork.requireLeaf()).isSameAs(worker);
        assertThat(fork.close(worker)).isSameAs(worker);
        assertThat(fork.requireLeaf()).isSameAs(coordinator);
    }

    @Test
    void forkStartsWithEmptyDiagnosticsAndDoesNotMutateParent()
    {
        PhysicalBranchContext parent = new PhysicalBranchContext(new LoomspanSession("fork-diagnostics", "entry", 3));
        LinterOutcome parentOutcome = new LinterOutcome(
                "entry", "regex", 1, 0, 1, LinterOutcomeStatus.PASSED, "parent");
        parent.recordLinterOutcome(parentOutcome);

        PhysicalBranchContext fork = parent.fork();
        OutputSchemaOutcome workerOutcome = new OutputSchemaOutcome(
                "entry", null, 1, 0, 1, OutputSchemaOutcomeStatus.PASSED, List.of());
        fork.recordOutputSchemaOutcome(workerOutcome);

        assertThat(fork.lastLinterOutcome()).isEmpty();
        assertThat(fork.lastOutputSchemaOutcome()).containsSame(workerOutcome);
        assertThat(parent.lastLinterOutcome()).containsSame(parentOutcome);
        assertThat(parent.lastOutputSchemaOutcome()).isEmpty();
        assertThat(fork.ownsMissionDiagnostics()).isFalse();
    }

    @Test
    void preservesCountedAndExcludedMaxDepthFrameTypes()
    {
        for (TraceFrameType excluded : List.of(
                TraceFrameType.MODEL_CALL, TraceFrameType.PLANNING,
                TraceFrameType.TOOL_INVOCATION, TraceFrameType.STEP_EXECUTION))
        {
            PhysicalBranchContext branch = new PhysicalBranchContext(
                    new LoomspanSession("excluded-" + excluded, "entry", 1));
            branch.push(frame("root", TraceFrameType.ROOT_MISSION));
            branch.push(frame("excluded", excluded));
            assertThat(branch.depth()).isEqualTo(2);
        }

        for (TraceFrameType counted : List.of(TraceFrameType.SKILL_EXECUTION, TraceFrameType.RETRY))
        {
            PhysicalBranchContext branch = new PhysicalBranchContext(
                    new LoomspanSession("counted-" + counted, "entry", 1));
            branch.push(frame("root", TraceFrameType.ROOT_MISSION));
            assertThatThrownBy(() -> branch.push(frame("counted", counted)))
                    .isInstanceOf(LoomspanStackOverflowException.class);
        }
    }

    @Test
    void cleanupDrainTransfersOnlyLocalFramesLeafToRootExactlyOnce()
    {
        ExecutionBinding binding = TestExecutionBindings.missionBinding(
                new LoomspanSession("drain", "entry", 8));
        PhysicalBranchContext worker = binding.branch().fork();
        ExecutionFrame first = frame("first", TraceFrameType.STEP_EXECUTION);
        ExecutionFrame second = frame("second", TraceFrameType.TOOL_INVOCATION);
        worker.push(first);
        worker.push(second);
        MissionLifecycle.Cutoff cutoff = binding.requireMission().lifecycle().closeNow();

        assertThat(worker.drainForCleanup(cutoff, binding.requireMission().missionFrameId()))
                .containsExactly(second, first);
        assertThat(worker.drainForCleanup(cutoff, binding.requireMission().missionFrameId())).isEmpty();
        assertThat(worker.rootToLeafSnapshot()).containsExactlyElementsOf(binding.branch().rootToLeafSnapshot());
        assertThat(worker.close(second)).isSameAs(second);
    }

    private ExecutionFrame frame(String id, TraceFrameType type)
    {
        return new ExecutionFrame(id, null, OperationType.SKILL, type, id, Map.of(), Instant.EPOCH);
    }
}
