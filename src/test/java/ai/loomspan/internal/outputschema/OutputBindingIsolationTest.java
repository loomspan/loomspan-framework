package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.skill.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OutputBindingIsolationTest {
    private static YamlSkillDefinition definition(String owner) {
        var manifest = new YamlSkillManifest(); manifest.setName(SkillIdentity.parse(owner).localName());
        manifest.setOutputSchema(LoomspanJacksonCodecs.defaults().schemaTree().readTree("""
                {"type":"object","properties":{"value":{"type":"string"}},"required":["value"],"additionalProperties":false}
                """));
        var binding = new YamlSkillManifest.InputBindingManifest(); binding.setFrom("child_result"); binding.setSkill("default-producer"); binding.setPath("");
        manifest.setOutputBindings(Map.of("/value",binding));
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", new ByteArrayResource(new byte[0]),manifest,
                new EffectiveSkillExecutionConfiguration("local","test",ai.loomspan.autoconfigure.AiDriver.OPENAI,"test","medium"));
    }
    private static MissionContext owner(LoomspanSession session, String skill, String frame, MissionContext parent, String text) {
        var mission = new MissionContext(session,skill,frame,parent); mission.captureInput(Map.of());
        var task = new PlanTask("same-task", "work", PlanTaskStatus.COMPLETED,"default-producer","work",List.of(),List.of(),null,null);
        mission.storePlan(new ExecutionPlan("same-plan",skill,Instant.EPOCH,List.of(task)));
        if (text != null) mission.recordCompletedTaskResult("same-task","default-producer",text);
        return mission;
    }
    @Test void identicalProducerNamesAndTaskIdsRemainBoundToTheirOwningMission() {
        var session = TestLoomspanSessions.withId("nested-output","default-root",5);
        var parent = owner(session,"default-root","default-parent",null,"parent result");
        var first = owner(session,"default-nested","first",parent,"first result");
        var second = owner(session,"default-nested","second",parent,"second result");
        var mapper = LoomspanJacksonCodecs.defaults().schemaTree();
        assertThat(new OutputBindingComposition(definition("default-root"),parent,mapper).composeEmpty().content()).isEqualTo("{\"value\":\"parent result\"}");
        var one = new OutputBindingComposition(definition("default-nested"),first,mapper);
        var two = new OutputBindingComposition(definition("default-nested"),second,mapper);
        assertThat(one.composeEmpty().content()).isEqualTo("{\"value\":\"first result\"}");
        assertThat(two.composeEmpty().content()).isEqualTo("{\"value\":\"second result\"}");
        assertThat(one.provenance().getFirst()).containsEntry("parentMissionFrameId","first");
        assertThat(two.provenance().getFirst()).containsEntry("parentMissionFrameId","second");
        var missing = owner(session,"default-nested","missing",parent,null);
        assertThatThrownBy(() -> new OutputBindingComposition(definition("default-nested"),missing,mapper)).hasMessageContaining("binding_source_unavailable");
        assertThatThrownBy(() -> new OutputBindingComposition(definition("default-root"),first,mapper)).hasMessageContaining("owner differs");
    }
    @Test void acceptedPlanMutationDuringContributionCorrectionPreventsPublication() {
        var session = TestLoomspanSessions.withId("stale-output","default-root",5);
        var mission = owner(session,"default-root","owner",null,"exact");
        var composition = new OutputBindingComposition(definition("default-root"),mission,LoomspanJacksonCodecs.defaults().schemaTree());
        mission.storePlan(mission.currentPlan().orElseThrow().withStatus(PlanStatus.STALE));
        assertThatThrownBy(composition::composeEmpty).hasMessageContaining("Accepted plan changed");
    }
    @Test void incompleteUnrelatedWorkRejectsOtherwiseFullyBoundOutput() {
        var session = TestLoomspanSessions.withId("incomplete-output", "default-root", 5);
        var mission = owner(session, "default-root", "owner", null, "exact");
        var producer = mission.currentPlan().orElseThrow().tasks().getFirst();
        var later = new PlanTask("later", "unrelated required work", PlanTaskStatus.PENDING, "default-later", "work", List.of(), List.of(), null, null);
        mission.storePlan(new ExecutionPlan("same-plan", "default-root", Instant.EPOCH, List.of(producer, later)));
        assertThatThrownBy(() -> new OutputBindingComposition(definition("default-root"), mission, LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("Accepted work is incomplete: later");
    }
    @Test void acceptedTaskProducerAndExtraResultChecksPrecedeAssemblyPublication() {
        var session = TestLoomspanSessions.withId("mismatched-output", "default-root", 5);
        var mismatch = owner(session, "default-root", "mismatch", null, "exact");
        mismatch.storePlan(new ExecutionPlan("same-plan", "default-root", Instant.EPOCH,
                List.of(new PlanTask("same-task", "work", PlanTaskStatus.COMPLETED, "default-other", "work", List.of(), List.of(), null, null))));
        assertThatThrownBy(() -> new OutputBindingComposition(definition("default-root"), mismatch, LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("Accepted task producer differs");
        var extra = owner(session, "default-root", "extra", null, "exact");
        extra.recordCompletedTaskResult("outside", "default-other", "unrelated");
        assertThatThrownBy(() -> new OutputBindingComposition(definition("default-root"), extra, LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("Result is outside accepted plan");
    }
    @Test void owningMissionClosureRejectsCapturedComposition() {
        var session = TestLoomspanSessions.withId("closed-output", "default-root", 5);
        var mission = owner(session, "default-root", "owner", null, "exact");
        var composition = new OutputBindingComposition(definition("default-root"), mission, LoomspanJacksonCodecs.defaults().schemaTree());
        mission.lifecycle().closeNow();
        assertThatThrownBy(composition::composeEmpty).hasMessageContaining("no longer open");
    }
}
