package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionBindingTest
{
    @Test
    void failsCurrentBindingAccessOutsideScope()
    {
        assertThat(ExecutionBindingScope.current()).isEmpty();
        assertThatThrownBy(ExecutionBindingScope::requireCurrent)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(LoomspanSession::getCurrentSession)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scopesAndRestoresTheExactBindingForEveryThrowablePath() throws Exception
    {
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("binding", "entry", 3);
        ExecutionBinding parent = ExecutionBinding.sessionOnly(session, ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty()));
        MissionContext mission = new MissionContext(session, "entry", "mission", null);
        ExecutionBinding child = parent.withMission(mission);
        assertThat(child.snapshot()).isSameAs(parent.snapshot());

        assertThat(ExecutionBindingScope.current()).isEmpty();
        ExecutionBindingScope.runWith(parent, () -> {
            assertThat(LoomspanSession.getCurrentSession()).isSameAs(session);
            assertThat(ExecutionBindingScope.supplyWith(child, ExecutionBindingScope::requireCurrent)).isSameAs(child);
            assertThatThrownBy(() -> ExecutionBindingScope.runWith(child, () -> { throw new AssertionError("boom"); }))
                    .isInstanceOf(AssertionError.class);
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
        });
        assertThat(ExecutionBindingScope.current()).isEmpty();

        try (var executor = Executors.newSingleThreadExecutor())
        {
            assertThat(executor.submit(ExecutionBindingScope::current).get()).isEmpty();
        }
    }

    @Test
    void rejectsMixedSessions()
    {
        LoomspanSession first = ai.loomspan.internal.core.TestLoomspanSessions.create("first", "entry", 3);
        LoomspanSession second = ai.loomspan.internal.core.TestLoomspanSessions.create("second", "entry", 3);
        assertThatThrownBy(() -> ai.loomspan.testkit.TestSkillGenerations.binding(first, null, new PhysicalBranchContext(second), ai.loomspan.testkit.TestSkillGenerations.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ai.loomspan.testkit.TestSkillGenerations.binding(
                first, new MissionContext(second, "entry", "mission", null),
                new PhysicalBranchContext(first), ai.loomspan.testkit.TestSkillGenerations.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        ExecutionBindingScope.runWith(ExecutionBinding.sessionOnly(first, ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty())), () ->
                assertThatThrownBy(() -> ExecutionBindingScope.runWith(ExecutionBinding.sessionOnly(second, ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty())), () -> {}))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void forkBranchRetainsSessionAndMissionWithIndependentPhysicalBranch()
    {
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("fork-binding", "entry", 3);
        MissionContext mission = new MissionContext(session, "entry", "mission", null);
        ExecutionBinding parent = ExecutionBinding.sessionOnly(session, ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty())).withMission(mission);
        ExecutionFrame root = new ExecutionFrame("root", null, OperationType.SKILL,
                TraceFrameType.ROOT_MISSION, "root", java.util.Map.of(), java.time.Instant.EPOCH, null, null);
        parent.branch().push(root);

        ExecutionBinding fork = parent.forkBranch();

        assertThat(fork.session()).isSameAs(session);
        assertThat(fork.mission()).isSameAs(mission);
        assertThat(fork.generation()).isSameAs(parent.generation());
        assertThat(fork.snapshot()).isSameAs(parent.snapshot());
        assertThat(fork.branch()).isNotSameAs(parent.branch());
        assertThat(fork.branch().rootToLeafSnapshot()).containsExactly(root);
        assertThat(fork.branch().localDepth()).isZero();
    }

    @Test void rejectsGenerationOutsideTheAuthoritativeAssociation() {
        var session = ai.loomspan.internal.core.TestLoomspanSessions.create("association", "entry", 3);
        var first = ai.loomspan.testkit.TestSkillGenerations.empty();
        var other = ai.loomspan.testkit.TestSkillGenerations.of(ai.loomspan.testkit.TestSkillGenerations.leaf());
        assertThatThrownBy(() -> new ExecutionBinding(session, null, new PhysicalBranchContext(session), other,
                ai.loomspan.testkit.TestSkillGenerations.snapshot(first)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("captured snapshot");
    }

    @Test
    void restoresParentAfterRuntimeCheckedExceptionAndError() throws Exception
    {
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("throwables", "entry", 3);
        ExecutionBinding parent = ExecutionBinding.sessionOnly(session, ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty()));
        ExecutionBinding child = parent.withMission(new MissionContext(session, "entry", "mission", null));

        ExecutionBindingScope.runWith(parent, () ->
        {
            assertThatThrownBy(() -> ExecutionBindingScope.runWith(child, () ->
            { throw new IllegalStateException("runtime"); })).isInstanceOf(IllegalStateException.class);
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
            assertThatThrownBy(() -> ExecutionBindingScope.callWith(child, () ->
            { throw new Exception("checked"); })).isInstanceOf(Exception.class).hasMessage("checked");
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
            assertThatThrownBy(() -> ExecutionBindingScope.runWith(child, () ->
            { throw new AssertionError("error"); })).isInstanceOf(AssertionError.class);
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
        });
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void unwrappedVirtualThreadDoesNotInheritBinding() throws Exception
    {
        ExecutionBinding binding = ExecutionBinding.sessionOnly(ai.loomspan.internal.core.TestLoomspanSessions.create("virtual", "entry", 3), ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            ExecutionBindingScope.runWith(binding, () ->
            {
                try
                {
                    assertThat(executor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
                }
                catch (Exception ex)
                {
                    throw new AssertionError(ex);
                }
            });
        }
    }

    @Test
    void wrappedCallableRestoresPreexistingWorkerBinding() throws Exception
    {
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("worker", "entry", 3);
        ExecutionBinding worker = ExecutionBinding.sessionOnly(session, ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty()));
        ExecutionBinding mission = worker.withMission(new MissionContext(session, "entry", "mission", null));
        try (var executor = Executors.newSingleThreadExecutor())
        {
            Callable<ExecutionBinding> wrapped = () -> ExecutionBindingScope.callWith(
                    worker, () -> ExecutionBindingScope.callWith(mission, ExecutionBindingScope::requireCurrent));
            assertThat(executor.submit(wrapped).get(2, TimeUnit.SECONDS)).isSameAs(mission);
            assertThat(executor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }
}
