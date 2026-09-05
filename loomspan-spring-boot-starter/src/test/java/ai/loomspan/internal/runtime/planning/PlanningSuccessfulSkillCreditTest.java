package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningSuccessfulSkillCreditTest
{
    @Test
    void startedAndFailedTasksDoNotCreditButVerifiedCompletionDoesExactlyOnce()
    {
        DefaultExecutionStateService state = new DefaultExecutionStateService(Clock.fixed(
                Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC));
        DefaultPlanningService planning = new DefaultPlanningService(state);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("planning-credit", "test.entry", 3);
        TestExecutionBindings.callWithSession(session, () -> {
        state.storePlan(plan("task-success", "investigateNetwork"));

        assertThat(successfulSkills()).isEmpty();
        planning.markToolCompleted(session, "task-success", "investigateNetwork").orElseThrow();
        assertThat(successfulSkills()).containsExactly("investigateNetwork");

        state.storePlan(plan("task-failure", "investigateApp"));
        planning.markToolFailed(session, "task-failure", "investigateApp", new IllegalStateException("failed"));
        assertThat(successfulSkills())
                .containsExactly("investigateNetwork")
                .doesNotContain("investigateApp");
        return null;
        });
    }

    private static java.util.Set<String> successfulSkills()
    {
        return ExecutionBindingScope.requireCurrent().requireMission().successfulDirectSkills();
    }

    private static ExecutionPlan plan(String taskId, String capability)
    {
        return new ExecutionPlan(
                "plan-" + taskId,
                "handleIncident",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(new PlanTask(
                        taskId,
                        "Use " + capability,
                        PlanTaskStatus.IN_PROGRESS,
                        capability,
                        "Use capability",
                        List.of(),
                        List.of(),
                        null,
                        null)));
    }
}
