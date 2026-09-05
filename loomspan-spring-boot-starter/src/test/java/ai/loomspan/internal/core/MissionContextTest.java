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
    void startsEmptyAndPreservesFiveLineSummaryAndEvidenceOrder()
    {
        LoomspanSession session = new LoomspanSession("mission", "entry", 3);
        MissionContext mission = new MissionContext(session, "entry", "frame", null);
        assertThat(mission.currentPlan()).isEmpty();
        assertThat(mission.successfulDirectSkills()).isEmpty();
        assertThat(mission.executionSummary()).isEmpty();
        assertThat(mission.lastToolResult()).isEmpty();

        mission.recordSuccessfulDirectSkill("a");
        mission.recordSuccessfulDirectSkill("b");
        for (int index = 1; index <= 6; index++) mission.appendExecutionSummary("line-" + index);
        mission.setLastToolResult("");

        assertThat(mission.successfulDirectSkills()).containsExactly("a", "b");
        assertThat(mission.executionSummary()).contains("line-2\nline-3\nline-4\nline-5\nline-6");
        assertThat(mission.lastToolResult()).contains("");
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
