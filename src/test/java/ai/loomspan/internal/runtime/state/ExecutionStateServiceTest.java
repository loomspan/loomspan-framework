package ai.loomspan.internal.runtime.state;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStateServiceTest
{
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void routesPlanAndEvidenceToTheCurrentMission()
    {
        LoomspanSession session = new LoomspanSession(4, "entry");
        MissionContext parent = new MissionContext(session, "entry", "parent", null);
        MissionContext child = new MissionContext(session, "child", "child", parent);
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        ExecutionPlan plan = new ExecutionPlan("plan", "entry", CLOCK.instant(), List.of());

        ExecutionBindingScope.runWith(new ExecutionBinding(session, parent, branch), () -> {
            ExecutionFrame root = state.openMissionFrame(session, "entry", Map.of());
            try {
                state.storePlan(plan);
                state.recordSuccessfulSkill("direct", null, true);
                ExecutionBindingScope.runWith(new ExecutionBinding(session, child, branch), () -> {
                    assertThat(state.currentPlan()).isEmpty();
                    assertThat(state.currentSuccessfulSkills()).isEmpty();
                });
                assertThat(state.currentPlan()).contains(plan);
                assertThat(state.currentSuccessfulSkills()).containsExactly("direct");
            }
            finally { state.closeMissionFrame(session, root); }
        });
    }

    @Test
    void opensAndClosesFramesAgainstTheBoundBranchInStrictOrder()
    {
        LoomspanSession session = new LoomspanSession(4, "entry");
        MissionContext mission = new MissionContext(session, "entry", "mission", null);
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        ExecutionBindingScope.runWith(new ExecutionBinding(session, mission, branch), () -> {
            ExecutionFrame root = state.openMissionFrame(session, "entry", Map.of());
            ExecutionFrame child = state.openFrame(session, TraceFrameType.MODEL_CALL, "model", Map.of());
            assertThat(child.parentFrameId()).isEqualTo(root.frameId());
            assertThatThrownBy(() -> state.closeFrame(session, root, Map.of())).isInstanceOf(IllegalStateException.class);
            assertThat(branch.requireLeaf()).isEqualTo(child);
            state.closeFrame(session, child, Map.of());
            state.closeMissionFrame(session, root);
            assertThat(branch.depth()).isZero();
        });
    }

    @Test
    void recordsDiagnosticsOnMissionImmediately()
    {
        LoomspanSession session = new LoomspanSession(4, "entry");
        MissionContext mission = new MissionContext(session, "entry", "mission", null);
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        LinterOutcome outcome = new LinterOutcome("entry", "regex", 1, 0, 1,
                LinterOutcomeStatus.PASSED, "ok");
        ExecutionBindingScope.runWith(new ExecutionBinding(session, mission, branch), () -> {
            ExecutionFrame root = state.openMissionFrame(session, "entry", Map.of());
            state.recordLinterOutcome(session, outcome);
            assertThat(mission.lastLinterOutcome()).contains(outcome);
            state.closeMissionFrame(session, root);
        });
    }

    @Test
    void forkedBranchRecordsDiagnosticDeltaWithoutMutatingMission()
    {
        LoomspanSession session = new LoomspanSession(4, "entry");
        MissionContext mission = new MissionContext(session, "entry", "mission", null);
        ExecutionBinding parent = new ExecutionBinding(session, mission, new PhysicalBranchContext(session));
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        LinterOutcome outcome = new LinterOutcome("entry", "regex", 1, 0, 1,
                LinterOutcomeStatus.PASSED, "worker-only");

        ExecutionBindingScope.runWith(parent, () -> {
            ExecutionFrame root = state.openMissionFrame(session, "entry", Map.of());
            ExecutionBinding fork = ExecutionBindingScope.requireCurrent().forkBranch();
            ExecutionBindingScope.runWith(fork, () -> {
                ExecutionFrame worker = state.openFrame(session, TraceFrameType.STEP_EXECUTION, "worker", Map.of());
                assertThat(worker.parentFrameId()).isEqualTo(root.frameId());
                state.recordLinterOutcome(session, outcome);
                assertThat(fork.branch().lastLinterOutcome()).containsSame(outcome);
                assertThat(mission.lastLinterOutcome()).isEmpty();
                state.closeFrame(session, worker, Map.of());
            });
            assertThat(parent.branch().requireLeaf()).isSameAs(root);
            state.closeMissionFrame(session, root);
        });
    }

    @Test
    void rejectsAnExplicitSessionFromAnotherBinding()
    {
        LoomspanSession first = new LoomspanSession(4, "entry");
        LoomspanSession second = new LoomspanSession(4, "entry");
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        ExecutionBindingScope.runWith(TestExecutionBindings.missionBinding(first), () ->
                assertThatThrownBy(() -> state.openFrame(second, TraceFrameType.MODEL_CALL, "model", Map.of()))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void closedBindingSuppressesStateTraceFailureAndFrameMutations()
    {
        LoomspanSession session = new LoomspanSession(8, "entry");
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        ExecutionPlan original = new ExecutionPlan("original", "entry", CLOCK.instant(), List.of());
        binding.requireMission().storePlan(original);

        ExecutionBindingScope.runWith(binding, () -> {
            int recordsBefore = records(session).size();
            binding.requireMission().lifecycle().closeNow();
            state.storePlan(new ExecutionPlan("late", "entry", CLOCK.instant(), List.of()));
            state.recordSuccessfulSkill("late-skill", null, true);
            state.recordStepEvent(session, binding.branch().requireLeaf(), TraceRecordType.STEP_COMPLETED,
                    Map.of(), Map.of());

            assertThatThrownBy(() -> state.openFrame(session, TraceFrameType.MODEL_CALL, "late", Map.of()))
                    .isInstanceOf(MissionWriteRevokedException.class);
            assertThatThrownBy(() -> state.recordFailure(session, new IllegalStateException("late"), Map.of()))
                    .isInstanceOf(MissionWriteRevokedException.class);
            assertThat(binding.requireMission().currentPlan()).containsSame(original);
            assertThat(binding.requireMission().successfulDirectSkills()).isEmpty();
            assertThat(records(session)).hasSize(recordsBefore);
        });
    }

    @Test
    void parentCutoffSuppressesEveryLaterChildCleanupMutation()
    {
        LoomspanSession session = new LoomspanSession(8, "entry");
        MissionContext parent = new MissionContext(session, "entry", "parent-frame", null);
        MissionContext child = new MissionContext(session, "child", "child-frame", parent);
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        DefaultExecutionStateService state = new DefaultExecutionStateService(CLOCK);
        ExecutionBinding parentBinding = new ExecutionBinding(session, parent, branch);

        ExecutionBindingScope.runWith(parentBinding, () -> {
            state.openMissionFrame(session, "entry", Map.of());
            ExecutionBinding childBinding = new ExecutionBinding(session, child, branch);
            ExecutionBindingScope.runWith(childBinding, () -> {
                ExecutionFrame childFrame = state.openMissionFrame(session, "child", Map.of());
                ExecutionPlan original = new ExecutionPlan("child-original", "child", CLOCK.instant(), List.of());
                child.storePlan(original);
                MissionLifecycle.Cutoff childCutoff = child.lifecycle().closeNow();
                int recordsBefore = records(session).size();

                parent.lifecycle().closeNow();
                state.storeAndLogPlanForCleanup(session,
                        new ExecutionPlan("child-late", "child", CLOCK.instant(), List.of()), null, childCutoff);
                state.recordSuccessfulSkillForCleanup(session, "late-skill", "late-task", childCutoff);
                state.closeFrameForCleanup(session, childFrame, Map.of("status", "aborted"), childCutoff, false);

                assertThat(child.currentPlan()).containsSame(original);
                assertThat(child.successfulDirectSkills()).isEmpty();
                assertThat(records(session)).hasSize(recordsBefore);
                assertThat(branch.contains(childFrame)).isTrue();
            });
        });
    }

    private static List<TraceRecord> records(LoomspanSession session)
    {
        java.util.ArrayList<TraceRecord> records = new java.util.ArrayList<>();
        session.readTraceRecords(records::add);
        return List.copyOf(records);
    }
}
