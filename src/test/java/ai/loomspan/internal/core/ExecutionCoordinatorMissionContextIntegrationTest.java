package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;
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
        when(catalog.getSkill("default:root-skill")).thenReturn(definition("default:root-skill"));
        TestCapabilityRegistry registry = new TestCapabilityRegistry();
        registry.register("default:root-skill", capability("default:root-skill"));
        MissionExecutionEngine engine = (session, definition, objective, missionInput, model, tools, planning, authentication) ->
                "complete";
        ExecutionCoordinator coordinator = new ExecutionCoordinator(
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
        LoomspanSession session = TestLoomspanSessions.withId("retained-reset", "default:root-skill", 3);
        session.replaceRetainedRootState(
                plan("stale-plan", "default:root-skill"),
                linter("default:root-skill", LinterOutcomeStatus.RETRYING),
                output("default:root-skill"));

        assertThat(TestExecutionBindings.callWithGeneration(session, registry.generation(catalog),
                () -> coordinator.execute("default:root-skill", "objective", session, null))).isEqualTo("complete");

        assertThat(session.getExecutionPlanSnapshot()).isNull();
        assertThat(session.getLastLinterOutcome()).isEmpty();
        assertThat(session.getLastOutputSchemaOutcome()).isEmpty();
    }

    @Test
    void threeLevelFailureRestoresBindingsAndMergesDiagnosticsWithoutLeakingChildState()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        YamlSkillCatalog catalog = mock(YamlSkillCatalog.class);
        TestCapabilityRegistry registry = new TestCapabilityRegistry();
        for (String name : List.of("default:root-skill", "default:child-skill", "default:grandchild-skill"))
        {
            when(catalog.getSkill(name)).thenReturn(definition(name));
            registry.register(name, capability(name));
        }

        AtomicReference<ExecutionCoordinator> coordinatorRef = new AtomicReference<>();
        AtomicReference<MissionContext> rootRef = new AtomicReference<>();
        AtomicReference<MissionContext> childRef = new AtomicReference<>();
        ExecutionPlan rootPlan = plan("root-plan", "default:root-skill");
        ExecutionPlan childPlan = plan("child-plan", "default:child-skill");
        LinterOutcome grandchildLinter = linter("default:grandchild-skill", LinterOutcomeStatus.RETRYING);
        LinterOutcome laterRootLinter = linter("default:root-skill", LinterOutcomeStatus.PASSED);
        OutputSchemaOutcome childOutput = output("default:child-skill");

        MissionExecutionEngine engine = (session, definition, objective, missionInput, model, tools, planning, authentication) ->
        {
            MissionContext current = ExecutionBindingScope.requireCurrent().requireMission();
            return switch (definition.identity().toString())
            {
                case "default:root-skill" ->
                {
                    rootRef.set(current);
                    stateService.storePlan(rootPlan);
                    stateService.recordSuccessfulSkill("default:root-evidence", null, true);
                    assertThatThrownBy(() -> coordinatorRef.get().execute(
                            "default:child-skill", "child objective", session, authentication))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("grandchild failed");
                    assertThat(ExecutionBindingScope.requireCurrent().requireMission()).isSameAs(current);
                    assertThat(current.currentPlan()).containsSame(rootPlan);
                    assertThat(current.successfulDirectSkills()).containsExactly("default:root-evidence");
                    assertThat(current.lastLinterOutcome()).containsSame(grandchildLinter);
                    assertThat(current.lastOutputSchemaOutcome()).containsSame(childOutput);
                    stateService.recordLinterOutcome(session, laterRootLinter);
                    yield "root complete";
                }
                case "default:child-skill" ->
                {
                    childRef.set(current);
                    assertThat(current.parent()).containsSame(rootRef.get());
                    assertThat(current.currentPlan()).isEmpty();
                    assertThat(current.successfulDirectSkills()).isEmpty();
                    stateService.storePlan(childPlan);
                    stateService.recordSuccessfulSkill("default:child-evidence", null, true);
                    stateService.recordOutputSchemaOutcome(session, childOutput);
                    coordinatorRef.get().execute("default:grandchild-skill", "grandchild objective", session, authentication);
                    throw new AssertionError("grandchild failure should propagate");
                }
                case "default:grandchild-skill" ->
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
        LoomspanSession session = TestLoomspanSessions.withId("three-level", "default:root-skill", 5);

        assertThat(TestExecutionBindings.callWithGeneration(session, registry.generation(catalog),
                () -> coordinator.execute("default:root-skill", "root objective", session, null))).isEqualTo("root complete");
        assertThat(ExecutionBindingScope.current()).isEmpty();
        assertThat(session.getExecutionPlanSnapshot()).isSameAs(rootPlan);
        assertThat(session.getLastLinterOutcome()).containsSame(laterRootLinter);
        assertThat(session.getLastOutputSchemaOutcome()).containsSame(childOutput);
        assertThat(session.getExecutionPlanSnapshot()).isNotSameAs(childPlan);
    }

    private static YamlSkillDefinition definition(String name)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(ai.loomspan.internal.core.SkillIdentity.parse(name).localName());
        manifest.setDescription(name);
        manifest.setModel("gpt-5");
        String child = switch (name) {
            case "default:root-skill" -> "default:child-skill";
            case "default:child-skill" -> "default:grandchild-skill";
            default -> null;
        };
        if (child != null) manifest.setAllowedSkills(List.of(
                new YamlSkillManifest.AllowedSkillManifest(child, null, null, null)));
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", new ByteArrayResource(new byte[0]), manifest, CONFIG);
    }

    private static CapabilityMetadata capability(String name)
    {
        return new CapabilityMetadata(
                "yaml:" + name,
                name,
                name,
                SkillExecutionDescriptor.from(CONFIG), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                SkillKind.YAML,
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
