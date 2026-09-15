package ai.loomspan.internal.core;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionCoordinatorMissionContextIntegrationTest
{
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration CONFIG =
            new EffectiveSkillExecutionConfiguration(
                    "gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    @Test
    void rootWithoutPlanOrDiagnosticsClearsAllEarlierRetainedSnapshots()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        YamlSkillCatalog catalog = mock(YamlSkillCatalog.class);
        when(catalog.getSkill("rootSkill")).thenReturn(definition("rootSkill"));
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        registry.register("rootSkill", capability("rootSkill"));
        MissionExecutionEngine engine = (session, definition, objective, missionInput, model, tools, planning, authentication) ->
                "complete";
        ExecutionCoordinator coordinator = new ExecutionCoordinator(
                catalog,
                registry,
                (definition, mode) -> request ->
                { throw new AssertionError("test engine does not call the model"); },
                (skillName, session, authentication) -> List.of(),
                (session, definition, capabilities, authentication) -> List.of(),
                engine,
                engine,
                stateService,
                new DefaultAccessGuard(),
                (value, session) -> value,
                new ai.loomspan.internal.security.ScopedAuthentication(null),
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, java.time.Duration.ofSeconds(5), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
        LoomspanSession session = TestLoomspanSessions.withId("retained-reset", "rootSkill", 3);
        session.replaceRetainedRootState(
                plan("stale-plan", "rootSkill"),
                linter("rootSkill", LinterOutcomeStatus.RETRYING),
                output("rootSkill"));

        assertThat(coordinator.execute("rootSkill", "objective", session, null)).isEqualTo("complete");

        assertThat(session.getExecutionPlanSnapshot()).isNull();
        assertThat(session.getLastLinterOutcome()).isEmpty();
        assertThat(session.getLastOutputSchemaOutcome()).isEmpty();
    }

    @Test
    void threeLevelFailureRestoresBindingsAndMergesDiagnosticsWithoutLeakingChildState()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        YamlSkillCatalog catalog = mock(YamlSkillCatalog.class);
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        for (String name : List.of("rootSkill", "childSkill", "grandchildSkill"))
        {
            when(catalog.getSkill(name)).thenReturn(definition(name));
            registry.register(name, capability(name));
        }

        AtomicReference<ExecutionCoordinator> coordinatorRef = new AtomicReference<>();
        AtomicReference<MissionContext> rootRef = new AtomicReference<>();
        AtomicReference<MissionContext> childRef = new AtomicReference<>();
        ExecutionPlan rootPlan = plan("root-plan", "rootSkill");
        ExecutionPlan childPlan = plan("child-plan", "childSkill");
        LinterOutcome grandchildLinter = linter("grandchildSkill", LinterOutcomeStatus.RETRYING);
        LinterOutcome laterRootLinter = linter("rootSkill", LinterOutcomeStatus.PASSED);
        OutputSchemaOutcome childOutput = output("childSkill");

        MissionExecutionEngine engine = (session, definition, objective, missionInput, model, tools, planning, authentication) ->
        {
            MissionContext current = ExecutionBindingScope.requireCurrent().requireMission();
            return switch (definition.manifest().getName())
            {
                case "rootSkill" ->
                {
                    rootRef.set(current);
                    stateService.storePlan(rootPlan);
                    stateService.recordSuccessfulSkill("rootEvidence", null, true);
                    assertThatThrownBy(() -> coordinatorRef.get().execute(
                            "childSkill", "child objective", session, authentication))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("grandchild failed");
                    assertThat(ExecutionBindingScope.requireCurrent().requireMission()).isSameAs(current);
                    assertThat(current.currentPlan()).containsSame(rootPlan);
                    assertThat(current.successfulDirectSkills()).containsExactly("rootEvidence");
                    assertThat(current.lastLinterOutcome()).containsSame(grandchildLinter);
                    assertThat(current.lastOutputSchemaOutcome()).containsSame(childOutput);
                    stateService.recordLinterOutcome(session, laterRootLinter);
                    yield "root complete";
                }
                case "childSkill" ->
                {
                    childRef.set(current);
                    assertThat(current.parent()).containsSame(rootRef.get());
                    assertThat(current.currentPlan()).isEmpty();
                    assertThat(current.successfulDirectSkills()).isEmpty();
                    stateService.storePlan(childPlan);
                    stateService.recordSuccessfulSkill("childEvidence", null, true);
                    stateService.recordOutputSchemaOutcome(session, childOutput);
                    coordinatorRef.get().execute("grandchildSkill", "grandchild objective", session, authentication);
                    throw new AssertionError("grandchild failure should propagate");
                }
                case "grandchildSkill" ->
                {
                    assertThat(current.parent()).containsSame(childRef.get());
                    assertThat(current.currentPlan()).isEmpty();
                    assertThat(current.successfulDirectSkills()).isEmpty();
                    stateService.recordLinterOutcome(session, grandchildLinter);
                    throw new IllegalStateException("grandchild failed");
                }
                default -> throw new AssertionError("unexpected mission");
            };
        };

        ExecutionCoordinator coordinator = new ExecutionCoordinator(
                catalog,
                registry,
                (definition, mode) -> request ->
                { throw new AssertionError("test engine does not call the model"); },
                (skillName, session, authentication) -> List.of(),
                (session, definition, capabilities, authentication) -> List.of(),
                engine,
                engine,
                stateService,
                new DefaultAccessGuard(),
                (value, session) -> value,
                new ai.loomspan.internal.security.ScopedAuthentication(null),
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, java.time.Duration.ofSeconds(5), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
        coordinatorRef.set(coordinator);
        LoomspanSession session = TestLoomspanSessions.withId("three-level", "rootSkill", 5);

        assertThat(coordinator.execute("rootSkill", "root objective", session, null)).isEqualTo("root complete");
        assertThat(ExecutionBindingScope.current()).isEmpty();
        assertThat(session.getExecutionPlanSnapshot()).isSameAs(rootPlan);
        assertThat(session.getLastLinterOutcome()).containsSame(laterRootLinter);
        assertThat(session.getLastOutputSchemaOutcome()).containsSame(childOutput);
        assertThat(session.getExecutionPlanSnapshot()).isNotSameAs(childPlan);
    }

    private static YamlSkillDefinition definition(String name)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setModel("gpt-5");
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, CONFIG);
    }

    private static CapabilityMetadata capability(String name)
    {
        return new CapabilityMetadata(
                "yaml:" + name,
                name,
                name,
                SkillExecutionDescriptor.from(CONFIG), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic(name, name),
                null);
    }

    private static ExecutionPlan plan(String id, String skillName)
    {
        return new ExecutionPlan(id, skillName, Instant.parse("2026-03-15T12:00:00Z"), List.of());
    }

    private static LinterOutcome linter(String skillName, LinterOutcomeStatus status)
    {
        return new LinterOutcome(skillName, "regex", 1, 0, 1, status, "retry");
    }

    private static OutputSchemaOutcome output(String skillName)
    {
        return new OutputSchemaOutcome(
                skillName, null, 1, 0, 1, OutputSchemaOutcomeStatus.PASSED, List.of());
    }
}
