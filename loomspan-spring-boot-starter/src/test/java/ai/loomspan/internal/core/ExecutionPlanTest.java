package ai.loomspan.internal.core;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionPlanTest {

    @Test
    void storesImmutableOrderedTasks() {
        ExecutionPlan plan = new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(
                        new PlanTask("task-1", "Plan", PlanTaskStatus.PENDING, null),
                        new PlanTask("task-2", "Execute", PlanTaskStatus.IN_PROGRESS, "started")));

        assertThat(plan.tasks()).extracting(PlanTask::taskId).containsExactly("task-1", "task-2");
        assertThatThrownBy(() -> plan.tasks().add(new PlanTask("task-3", "Done", PlanTaskStatus.COMPLETED, null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updatesOnlyMatchingTask() {
        ExecutionPlan plan = new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(
                        new PlanTask("task-1", "Plan", PlanTaskStatus.PENDING, null),
                        new PlanTask("task-2", "Execute", PlanTaskStatus.PENDING, null)));

        ExecutionPlan updated = plan.updateTask("task-2",
                task -> task.withStatus(PlanTaskStatus.COMPLETED, "done"));

        assertThat(updated.tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.PENDING, PlanTaskStatus.COMPLETED);
        assertThat(updated.tasks().get(1).note()).isEqualTo("done");
    }

    @Test
    void allImmutablePlanCopiesPreservePlanId() {
        ExecutionPlan plan = new ExecutionPlan(
                "framework-plan-id",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(
                        new PlanTask("task-1", "Plan", PlanTaskStatus.PENDING, null),
                        new PlanTask("task-2", "Execute", PlanTaskStatus.PENDING, null)));

        assertThat(plan.updateTask("task-2", task -> task.withStatus(PlanTaskStatus.COMPLETED, "done")).planId())
                .isEqualTo(plan.planId());
        assertThat(plan.withStatus(PlanStatus.STALE).planId()).isEqualTo(plan.planId());
        assertThat(plan.withStatus(PlanStatus.STALE).planId()).isEqualTo(plan.planId());
    }

    @Test
    void preservesParallelGroupThroughTaskCopiesPlanUpdatesAndSerialization() throws Exception
    {
        PlanTask grouped = new PlanTask("task-1", "Plan", PlanTaskStatus.PENDING,
                "tool", "work", List.of(), List.of("result"), "Group_A-1", null);
        ExecutionPlan plan = new ExecutionPlan("plan-1", "root", Instant.parse("2026-03-15T12:00:00Z"),
                List.of(grouped));

        assertThat(grouped.bindInProgress("started").parallelGroup()).isEqualTo("Group_A-1");
        assertThat(grouped.complete("done").parallelGroup()).isEqualTo("Group_A-1");
        PlanTask failed = grouped.fail("failed");
        assertThat(failed)
                .returns("task-1", PlanTask::taskId)
                .returns("Plan", PlanTask::title)
                .returns(PlanTaskStatus.FAILED, PlanTask::status)
                .returns("Group_A-1", PlanTask::parallelGroup)
                .returns("failed", PlanTask::note);
        assertThat(plan.updateTask("task-1", task -> task.complete("done")).tasks().getFirst().parallelGroup())
                .isEqualTo("Group_A-1");

        ObjectMapper mapper = LoomspanJacksonCodecs.defaults().planningJson();
        String json = mapper.writeValueAsString(plan);
        assertThat(json).contains("\"parallelGroup\":\"Group_A-1\"")
                .doesNotContain("active" + "TaskId", "auto" + "Completable");
        assertThat(mapper.readValue(json, ExecutionPlan.class).tasks().getFirst().parallelGroup())
                .isEqualTo("Group_A-1");
    }

    @Test
    void exposesOnlyTheCurrentPlanTaskStatusVocabulary()
    {
        assertThat(PlanTaskStatus.values()).containsExactly(
                PlanTaskStatus.PENDING,
                PlanTaskStatus.IN_PROGRESS,
                PlanTaskStatus.COMPLETED,
                PlanTaskStatus.FAILED);
    }
}
