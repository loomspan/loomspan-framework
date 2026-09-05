package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition;

class PlanExecutionTransitionTest
{
    @Test
    void serializesExactKindSpecificShapesAndCopiesTaskIds()
    {
        ArrayList<String> ids = new ArrayList<>(List.of("a", "b"));
        PlanExecutionTransition admission = PlanExecutionTransition.admission(ids, "batch", true);
        ids.clear();
        assertThat(admission.metadata()).containsOnlyKeys(
                "kind", "taskIds", "parallelGroup", "effectiveConcurrency")
                .containsEntry("kind", "ADMISSION")
                .containsEntry("taskIds", List.of("a", "b"))
                .containsEntry("parallelGroup", "batch")
                .containsEntry("effectiveConcurrency", true);

        assertThat(PlanExecutionTransition.join(List.of("a"), null,
                PlanExecutionTransition.JoinOutcome.FAILED).metadata())
                .containsOnlyKeys("kind", "taskIds", "parallelGroup", "outcome")
                .containsEntry("kind", "JOIN")
                .containsEntry("taskIds", List.of("a"))
                .containsEntry("parallelGroup", null)
                .containsEntry("outcome", "FAILED");
    }

    @Test
    void rejectsMalformedOrContradictoryValues()
    {
        assertThatThrownBy(() -> PlanExecutionTransition.admission(List.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanExecutionTransition.admission(List.of("a", "a"), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanExecutionTransition.admission(List.of("a"), null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanExecutionTransition(PlanExecutionTransition.Kind.JOIN,
                List.of("a"), null, false, PlanExecutionTransition.JoinOutcome.COMPLETED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
