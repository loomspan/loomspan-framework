package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionContextTest
{
    @Test
    @SuppressWarnings("unchecked")
    void ownsDetachedInputAndDecodedAcceptedResultsPerMission() {
        var session = new LoomspanSession("isolated", "entry", 3);
        var parent = new MissionContext(session, "entry", "parent", null);
        var child = new MissionContext(session, "entry", "child", parent);
        var mutable = new java.util.LinkedHashMap<String,Object>(java.util.Map.of("marker", "parent"));
        parent.captureInput(java.util.Map.of("nested", new java.util.ArrayList<>(List.of(mutable))));
        child.captureInput(java.util.Map.of("marker", "child"));
        mutable.put("marker", "mutated");
        assertThat(parent.input().get("nested")).isEqualTo(List.of(java.util.Map.of("marker", "parent")));
        assertThat(child.input()).containsEntry("marker", "child");
        assertThatThrownBy(() -> parent.captureInput(java.util.Map.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parent.input().put("forbidden", 1)).isInstanceOf(UnsupportedOperationException.class);
        parent.recordCompletedTaskResult("one", "producer", "  {\"amount\":1234567890123456789012345,\"text\":\"{ordinary}\"}  ");
        var snapshot = parent.completedTaskResults();
        child.recordCompletedTaskResult("one", "producer", "\"child\"");
        parent.recordCompletedTaskResult("two", "producer", "{malformed");
        assertThat(snapshot).hasSize(1);
        var decoded = (java.util.Map<String,Object>) snapshot.getFirst().decodedResult();
        assertThat(decoded).containsEntry("amount", new java.math.BigInteger("1234567890123456789012345"))
                .containsEntry("text", "{ordinary}");
        assertThat(snapshot.getFirst().result()).startsWith("  {").endsWith("}  ");
        assertThatThrownBy(() -> decoded.put("forbidden", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(child.completedTaskResults().getFirst().decodedResult()).isEqualTo("child");
        assertThat(parent.completedTaskResults().getLast().decodedResult()).isEqualTo("{malformed");
    }

    @Test
    void startsEmptyAndPreservesFiveLineSummaryAndEvidenceOrder()
    {
        LoomspanSession session = new LoomspanSession("mission", "entry", 3);
        MissionContext mission = new MissionContext(session, "entry", "frame", null);
        assertThat(mission.currentPlan()).isEmpty();
        assertThat(mission.successfulDirectSkills()).isEmpty();
        assertThat(mission.executionSummary()).isEmpty();
        assertThat(mission.completedTaskResults()).isEmpty();

        mission.recordSuccessfulDirectSkill("a");
        mission.recordSuccessfulDirectSkill("b");
        for (int index = 1; index <= 6; index++) mission.appendExecutionSummary("line-" + index);
        mission.recordCompletedTaskResult("task", "a", "");

        assertThat(mission.successfulDirectSkills()).containsExactly("a", "b");
        assertThat(mission.executionSummary()).contains("line-2\nline-3\nline-4\nline-5\nline-6");
        assertThat(mission.completedTaskResults()).containsExactly(new MissionContext.CompletedTaskResult("task", "a", ""));
    }

    @Test
    void ownsPlanAndReturnsImmutableEvidenceSnapshots()
    {
        MissionContext mission = mission("mission");
        ExecutionPlan plan = new ExecutionPlan("plan", "entry", Instant.EPOCH, List.of());
        mission.storePlan(plan);
        mission.recordSuccessfulDirectSkill("first");
        var snapshot = mission.successfulDirectSkills();
        mission.recordSuccessfulDirectSkill("second");

        assertThat(mission.currentPlan()).containsSame(plan);
        assertThat(snapshot).containsExactly("first");
        assertThatThrownBy(() -> snapshot.add("forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        mission.clearPlan();
        mission.clearSuccessfulDirectSkills();
        assertThat(mission.currentPlan()).isEmpty();
        assertThat(mission.successfulDirectSkills()).isEmpty();
    }

    @Test
    void mergesOnlyPresentChildDiagnosticsAndAllowsLaterParentValuesToWin()
    {
        MissionContext parent = mission("parent");
        MissionContext child = new MissionContext(parent.session(), "child", "child-frame", parent);
        LinterOutcome parentLinter = linter("parent", LinterOutcomeStatus.RETRYING);
        OutputSchemaOutcome childOutput = output("child", OutputSchemaOutcomeStatus.PASSED);
        parent.recordLinterOutcome(parentLinter);
        child.recordOutputSchemaOutcome(childOutput);

        parent.mergeDiagnostics(child.diagnosticDelta());
        assertThat(parent.lastLinterOutcome()).containsSame(parentLinter);
        assertThat(parent.lastOutputSchemaOutcome()).containsSame(childOutput);

        LinterOutcome later = linter("parent", LinterOutcomeStatus.PASSED);
        parent.recordLinterOutcome(later);
        assertThat(parent.lastLinterOutcome()).containsSame(later);
    }

    @Test
    void preservesIdentityReferencesAndRejectsCrossSessionParent()
    {
        MissionContext parent = mission("parent");
        MissionContext child = new MissionContext(parent.session(), "child", "child-frame", parent);
        assertThat(child.session()).isSameAs(parent.session());
        assertThat(child.parent()).containsSame(parent);
        assertThat(child.missionFrameId()).isEqualTo("child-frame");
        assertThatThrownBy(() -> new MissionContext(
                new LoomspanSession("other", "entry", 3), "child", "frame", parent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retainsCompleteTaskResultsIndependentlyOfProgressSummaries()
    {
        MissionContext mission = mission("retention");
        String longResult = "α\n\"quote\"\\".repeat(1000);
        for (int index = 0; index < 8; index++)
        {
            mission.recordCompletedTaskResult("task-" + index, "repeated", longResult + index);
            mission.appendExecutionSummary("line-" + index);
        }
        var snapshot = mission.completedTaskResults();
        mission.recordCompletedTaskResult("empty", "repeated", "");
        mission.recordCompletedTaskResult("whitespace", "repeated", " \n");
        assertThat(snapshot).hasSize(8);
        assertThat(snapshot).extracting(MissionContext.CompletedTaskResult::taskId)
                .containsExactly("task-0", "task-1", "task-2", "task-3", "task-4", "task-5", "task-6", "task-7");
        assertThat(snapshot.getFirst().result()).isEqualTo(longResult + "0");
        assertThat(mission.completedTaskResults()).extracting(MissionContext.CompletedTaskResult::result).endsWith("", " \n");
        assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mission.recordCompletedTaskResult("task-0", "other", "overwrite"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mission.completedTaskResults().getFirst().result()).isEqualTo(longResult + "0");
        assertThatThrownBy(() -> mission.recordCompletedTaskResult(" ", "skill", "result"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mission.recordCompletedTaskResult("task", " ", "result"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mission.recordCompletedTaskResult("task", "skill", null))
                .isInstanceOf(NullPointerException.class);
    }

    private static MissionContext mission(String id)
    {
        return new MissionContext(new LoomspanSession(id, "entry", 3), "entry", id + "-frame", null);
    }

    private static LinterOutcome linter(String skill, LinterOutcomeStatus status)
    {
        return new LinterOutcome(skill, "regex", 1, 0, 1, status, "retry");
    }

    private static OutputSchemaOutcome output(String skill, OutputSchemaOutcomeStatus status)
    {
        return new OutputSchemaOutcome(skill, null, 1, 0, 1, status, List.of());
    }
}
