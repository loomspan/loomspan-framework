package ai.loomspan.internal.runtime.tool;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.ExecutionCoordinator;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import ai.loomspan.internal.runtime.evidence.EvidenceCoverageValidator;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.vfs.RefResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NestedSuccessfulSkillBoundaryTest
{
    @Test
    void successfulNestedCallCreditsOnlyItsPublicBoundaryName()
    {
        Harness harness = harness(false);

        ExecutionBindingScope.runWith(harness.binding, () -> harness.callback.invoke(Map.of(), null));

        assertThat(harness.mission().successfulDirectSkills())
                .containsExactly("classifyIncident", "investigateNetwork")
                .doesNotContain("checkDns");
        EvidenceContract parentContract = parentContract();
        assertThat(new EvidenceCoverageValidator().validateEvidenceForClaims(
                Set.of("likelyCause"), harness.mission().successfulDirectSkills(), parentContract).complete()).isTrue();
        assertThat(new EvidenceCoverageValidator().validateEvidenceForClaims(
                Set.of("internalProbe"), harness.mission().successfulDirectSkills(), parentContract).complete()).isFalse();
    }

    @Test
    void failedNestedCallRestoresParentAndCreditsNeitherBoundaryNorInternals()
    {
        Harness harness = harness(true);

        assertThatThrownBy(() -> ExecutionBindingScope.runWith(
                harness.binding, () -> harness.callback.invoke(Map.of(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("nested failure");
        assertThat(harness.mission().successfulDirectSkills())
                .containsExactly("classifyIncident")
                .doesNotContain("investigateNetwork", "checkDns");
    }

    private static Harness harness(boolean fail)
    {
        DefaultExecutionStateService state = new DefaultExecutionStateService(Clock.fixed(
                Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC));
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("nested", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ExecutionBindingScope.runWith(binding,
                () -> state.recordSuccessfulSkill("classifyIncident", "task-classify", false));

        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        when(coordinator.execute(eq("investigateNetwork"), any(), any(), eq(session), eq(null)))
                .thenAnswer(invocation ->
                {
                    return TestExecutionBindings.callWithCurrentSessionMission(() ->
                    {
                        assertThat(ExecutionBindingScope.requireCurrent().requireMission()
                                .successfulDirectSkills()).isEmpty();
                        state.recordSuccessfulSkill("checkDns", "task-dns", false);
                        if (fail) throw new IllegalStateException("nested failure");
                        return "network result";
                    });
                });
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        RefResolver refs = (value, ignored) -> value;
        CapabilityExecutionRouter router = new CapabilityExecutionRouter( beans.getBeanProvider(ExecutionCoordinator.class), new DefaultAccessGuard());
        PlanningService planning = mock(PlanningService.class);
        CapabilityMetadata capability = capability();
        when(planning.markToolStarted(eq(session), eq(capability))).thenReturn(Optional.empty());
        BoundCapability callback = new DefaultCapabilityInvoker(router, planning, state)
                .bind(session, definition(), List.of(capability), null)
                .getFirst();
        return new Harness(session, binding, callback);
    }

    private static CapabilityMetadata capability()
    {
        return new CapabilityMetadata(
                "yaml:investigateNetwork",
                "investigateNetwork",
                "Investigate network",
                SkillExecutionDescriptor.from(new EffectiveSkillExecutionConfiguration(
                        "gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("investigateNetwork", "Investigate network"),
                null);
    }

    private static YamlSkillDefinition definition()
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("handleIncident");
        manifest.setDescription("Handle incident");
        manifest.setModel("gpt-5");
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                new EffectiveSkillExecutionConfiguration(
                        "gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium"));
    }

    private static EvidenceContract parentContract()
    {
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType("object");
        YamlSkillManifest.OutputSchemaManifest value = new YamlSkillManifest.OutputSchemaManifest();
        value.setType("string");
        schema.setProperties(Map.of("likelyCause", value, "internalProbe", value));
        return ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(Map.of(
                "likelyCause", "classifyIncident and investigateNetwork",
                "internalProbe", "checkDns"));
    }

    private record Harness(LoomspanSession session, ExecutionBinding binding, BoundCapability callback)
    {
        MissionContext mission() { return binding.requireMission(); }
    }
}
