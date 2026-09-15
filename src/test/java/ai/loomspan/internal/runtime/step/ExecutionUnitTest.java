package ai.loomspan.internal.runtime.step;

import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionUnitTest
{
    @Test
    void partitionsTasksIntoOrderedSingletonAndMaximalGroupedUnits()
    {
        List<PlanTask> tasks = List.of(
                task("z", "tool-z", null, List.of("later-looking-id")),
                task("b", "tool-b", "group-2", List.of()),
                task("a", "tool-a", "group-2", List.of()),
                task("m", "tool-m", null, List.of()),
                task("c", "tool-c", "group-1", List.of()),
                task("d", "tool-d", "group-1", List.of()),
                task("e", "tool-e", "group-2", List.of()));

        List<ExecutionUnit> units = ExecutionUnit.partition(tasks);

        assertThat(units).extracting(unit -> unit.members().stream().map(PlanTask::taskId).toList())
                .containsExactly(
                        List.of("z"),
                        List.of("b", "a"),
                        List.of("m"),
                        List.of("c", "d"),
                        List.of("e"));
        assertThat(units).extracting(ExecutionUnit::parallelGroup)
                .containsExactly(null, "group-2", null, "group-1", "group-2");
    }

    @Test
    void assignmentAndOutcomeCarriersRejectInvalidValues()
    {
        PlanTask task = task("t-1", "tool", null, List.of());
        assertThatThrownBy(() -> new AssignedTaskExecution(task, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssignedTaskOutcome.Success(null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssignedTaskOutcome.Failure(new IllegalStateException("boom"), " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AssignedTaskExecution(task, 1, false).task()).isSameAs(task);
        assertThat(new AssignedTaskExecution(task, 1, true).effectiveConcurrency()).isTrue();

        AssignedTaskExecution assignment = new AssignedTaskExecution(task, 1, true);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "assignment-handle", "test.entry", 3);
        var binding = ai.loomspan.internal.core.TestExecutionBindings.missionBinding(session).forkBranch();
        var future = CompletableFuture.<AssignedTaskOutcome>completedFuture(
                new AssignedTaskOutcome.Success("done", null, null));
        var lifecycle = binding.requireMission().lifecycle();
        var entry = lifecycle.admitUnit(binding, List.of(assignment), () -> {}).getFirst();
        lifecycle.bindBranch(entry, binding);
        lifecycle.registerFuture(entry, future);
        var cutoff = lifecycle.closeNow();
        assertThat(entry.assignment()).isSameAs(assignment);
        assertThat(entry.future()).containsSame(future);
        assertThat(cutoff.tasks().getFirst().binding()).isSameAs(binding);
    }

    private static PlanTask task(String id, String capability, String group, List<String> dependencies)
    {
        return new PlanTask(id, "Task " + id, PlanTaskStatus.PENDING, capability,
                "Intent " + id, dependencies, List.of("result"), group, null);
    }
}
