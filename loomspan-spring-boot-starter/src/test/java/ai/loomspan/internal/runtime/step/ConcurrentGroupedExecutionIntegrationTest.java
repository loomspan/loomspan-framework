package ai.loomspan.internal.runtime.step;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.ExecutionCoordinator;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.InMemoryCapabilityRegistry;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.LoomspanStackOverflowException;
import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.PhysicalBranchContext;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionFactory;
import ai.loomspan.internal.model.ModelInteractionRequest;
import ai.loomspan.internal.model.ModelInteractionResult;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.LoomspanQuotaExceededException;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.planning.DefaultPlanningService;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.tool.DefaultCapabilityInvoker;
import ai.loomspan.internal.runtime.tool.DefaultToolSurfaceService;
import ai.loomspan.internal.runtime.usage.DefaultSessionUsageService;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.runtime.usage.NoOpUsageMetricsRecorder;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentGroupedExecutionIntegrationTest
{
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration CONFIG = new EffectiveSkillExecutionConfiguration(
            "gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");
    private static final ObjectMapper PLAN_MAPPER = LoomspanJacksonCodecs.defaults().planningJson();

    @Test
    void concurrentNestedDirectAndPlanningMissionsKeepStateAndDiagnosticsIsolated() throws Exception
    {
        String root = "rootPlanner";
        String directChild = "directChild";
        String planningChild = "planningChild";
        String leafA = "leafA";
        String leafB = "leafB";
        Authentication authentication = allowedAuthentication();
        LoomspanSession session = session("concurrent-nested", root, 6, authentication);
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        CountDownLatch directStarted = new CountDownLatch(1);
        CountDownLatch planningStarted = new CountDownLatch(1);
        CountDownLatch innerWorkersStarted = new CountDownLatch(2);
        AtomicReference<MissionContext> rootMission = new AtomicReference<>();
        AtomicReference<MissionContext> directMission = new AtomicReference<>();
        AtomicReference<MissionContext> planningMission = new AtomicReference<>();
        Set<PhysicalBranchContext> innerBranches = ConcurrentHashMap.newKeySet();
        List<List<ExecutionFrame>> innerPaths = java.util.Collections.synchronizedList(new ArrayList<>());

        LinterOutcome directLinter = new LinterOutcome(
                directChild, "regex", 1, 0, 1, LinterOutcomeStatus.PASSED, null);
        OutputSchemaOutcome nestedOutput = new OutputSchemaOutcome(
                planningChild, null, 1, 0, 1, OutputSchemaOutcomeStatus.PASSED, List.of());
        ExecutionPlan rootPlan = groupedPlan("root-plan", root,
                task("root-direct", directChild, "outer"),
                task("root-planning", planningChild, "outer"));
        ExecutionPlan childPlan = groupedPlan("child-plan", planningChild,
                task("inner-a", leafA, "inner"),
                task("inner-b", leafB, "inner"));

        ScenarioModelFactory models = new ScenarioModelFactory(
                Map.of(root, rootPlan, planningChild, childPlan),
                Map.of(
                        root, Map.of("root-direct", directChild, "root-planning", planningChild),
                        planningChild, Map.of("inner-a", leafA, "inner-b", leafB)),
                Map.of(root, "root complete", planningChild, "planning complete"),
                skillName -> {
                    MissionContext current = ExecutionBindingScope.requireCurrent().requireMission();
                    if (root.equals(skillName)) rootMission.compareAndSet(null, current);
                    if (planningChild.equals(skillName))
                    {
                        planningMission.compareAndSet(null, current);
                        planningStarted.countDown();
                        await(directStarted, "outer direct child must overlap nested planning startup");
                    }
                });

        AtomicReference<ExecutionCoordinator> coordinator = new AtomicReference<>();
        MissionExecutionEngine directEngine = (currentSession, definition, objective, missionInput, model,
                visibleTools, planningEnabled, currentAuthentication) -> {
            assertThat(definition.manifest().getName()).isEqualTo(directChild);
            assertThat(currentAuthentication).isSameAs(authentication);
            assertAuthorized(currentSession);
            MissionContext current = ExecutionBindingScope.requireCurrent().requireMission();
            directMission.set(current);
            state.storePlan(new ExecutionPlan(
                    "direct-plan", directChild, Instant.parse("2026-03-15T12:02:00Z"), List.of()));
            state.recordLinterOutcome(currentSession, directLinter);
            directStarted.countDown();
            await(planningStarted, "nested planner must start before direct child returns");
            return "direct complete";
        };

        Function<String, Object> leafInvocation = leafName -> {
            assertAuthorized(session);
            ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
            assertThat(binding.requireMission().skillName()).isEqualTo(leafName);
            assertThat(binding.requireMission().parent()).containsSame(planningMission.get());
            innerBranches.add(binding.branch());
            innerPaths.add(binding.branch().rootToLeafSnapshot());
            if (leafB.equals(leafName)) state.recordOutputSchemaOutcome(session, nestedOutput);
            innerWorkersStarted.countDown();
            await(innerWorkersStarted, "inner planner workers must overlap on the shared executor");
            return leafName + " complete";
        };

        List<YamlSkillDefinition> definitions = List.of(
                plannedDefinition(root, List.of(directChild, planningChild)),
                directDefinition(directChild, List.of()),
                plannedDefinition(planningChild, List.of(leafA, leafB)));
        List<CapabilityMetadata> capabilities = List.of(
                yamlCapability(root, false, arguments -> "unused"),
                yamlCapability(directChild, false, arguments -> "unused"),
                yamlCapability(planningChild, false, arguments -> "unused"),
                yamlCapability(leafA, true, arguments -> leafInvocation.apply(leafA)),
                yamlCapability(leafB, true, arguments -> leafInvocation.apply(leafB)));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            ExecutionCoordinator runtime = runtime(definitions, capabilities, models, directEngine,
                    state, executor, new NoOpSessionUsageService(), coordinator);

            assertThat(runtime.execute(root, "run nested work", session, authentication)).isEqualTo("root complete");
        }

        assertThat(directMission.get().parent()).containsSame(rootMission.get());
        assertThat(planningMission.get().parent()).containsSame(rootMission.get());
        assertThat(directMission.get().currentPlan()).get().extracting(ExecutionPlan::planId).isEqualTo("direct-plan");
        assertThat(planningMission.get().currentPlan()).get().satisfies(plan -> {
            assertThat(plan.capabilityName()).isEqualTo(planningChild);
            assertThat(plan.tasks()).extracting(PlanTask::status)
                    .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.COMPLETED);
        });
        assertThat(rootMission.get().successfulDirectSkills()).containsExactly(directChild, planningChild);
        assertThat(planningMission.get().successfulDirectSkills()).containsExactly(leafA, leafB);
        assertThat(session.getExecutionPlanSnapshot().capabilityName()).isEqualTo(root);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.COMPLETED);
        assertThat(session.getLastLinterOutcome()).containsSame(directLinter);
        assertThat(session.getLastOutputSchemaOutcome()).containsSame(nestedOutput);
        assertThat(innerBranches).hasSize(2);
        assertThat(innerPaths).hasSize(2).allSatisfy(path -> {
            assertThat(path).extracting(ExecutionFrame::frameId)
                    .contains(rootMission.get().missionFrameId(), planningMission.get().missionFrameId());
        });
        assertThat(innerPaths.get(0).getFirst().frameId()).isEqualTo(innerPaths.get(1).getFirst().frameId());
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void actualNestedDepthFailureIsAnOrdinaryMemberOutcomeAndDoesNotCancelSibling()
    {
        String root = "depthRoot";
        String deepChild = "deepChild";
        String grandchild = "grandchild";
        String successfulChild = "successfulChild";
        Authentication authentication = allowedAuthentication();
        LoomspanSession session = session("concurrent-depth", root, 2, authentication);
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        CountDownLatch deepStarted = new CountDownLatch(1);
        CountDownLatch siblingStarted = new CountDownLatch(1);
        CountDownLatch depthAttempted = new CountDownLatch(1);
        AtomicInteger grandchildEngineCalls = new AtomicInteger();
        ExecutionPlan plan = groupedPlan("depth-plan", root,
                task("depth-task", deepChild, "depth-group"),
                task("success-task", successfulChild, "depth-group"));
        ScenarioModelFactory models = rootOnlyModels(root, plan,
                Map.of("depth-task", deepChild, "success-task", successfulChild));
        AtomicReference<ExecutionCoordinator> coordinator = new AtomicReference<>();
        MissionExecutionEngine directEngine = (currentSession, definition, objective, missionInput, model,
                visibleTools, planningEnabled, currentAuthentication) -> switch (definition.manifest().getName())
        {
            case "deepChild" -> {
                deepStarted.countDown();
                await(siblingStarted, "sibling must start before the nested depth failure");
                try
                {
                    yield String.valueOf(visibleTools.getFirst().invoke(Map.of(), null));
                }
                finally
                {
                    depthAttempted.countDown();
                }
            }
            case "successfulChild" -> {
                siblingStarted.countDown();
                await(deepStarted, "deep child must start concurrently");
                await(depthAttempted, "sibling must remain alive through the nested depth failure");
                yield "sibling complete";
            }
            case "grandchild" -> {
                grandchildEngineCalls.incrementAndGet();
                yield "unreachable";
            }
            default -> throw new AssertionError("unexpected direct mission");
        };

        List<YamlSkillDefinition> definitions = List.of(
                plannedDefinition(root, List.of(deepChild, successfulChild)),
                directDefinition(deepChild, List.of(grandchild)),
                directDefinition(successfulChild, List.of()),
                directDefinition(grandchild, List.of()));
        List<CapabilityMetadata> capabilities = List.of(
                yamlCapability(root, false, arguments -> "unused"),
                yamlCapability(deepChild, false, arguments -> "unused"),
                yamlCapability(successfulChild, false, arguments -> "unused"),
                yamlCapability(grandchild, false, arguments -> "unused"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            ExecutionCoordinator runtime = runtime(definitions, capabilities, models, directEngine,
                    state, executor, new NoOpSessionUsageService(), coordinator);

            assertThatThrownBy(() -> runtime.execute(root, "exercise depth", session, authentication))
                    .isInstanceOf(LoomspanStackOverflowException.class);
        }

        assertThat(grandchildEngineCalls).hasValue(0);
        assertThat(depthAttempted.getCount()).isZero();
        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.COMPLETED);
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void nestedChildTimeoutIsMemberFailureAndDoesNotCancelSibling()
    {
        String root = "timeoutRoot";
        String timeoutChild = "timeoutChild";
        String successfulChild = "timeoutSibling";
        Authentication authentication = allowedAuthentication();
        LoomspanSession session = session("concurrent-child-timeout", root, 4, authentication);
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        CountDownLatch siblingStarted = new CountDownLatch(1);
        CountDownLatch timeoutThrown = new CountDownLatch(1);
        LoomspanMissionTimeoutException expected = new LoomspanMissionTimeoutException(
                session.getSessionId(), timeoutChild, Duration.ofMillis(25), new IllegalStateException("child timed out"));
        ExecutionPlan plan = groupedPlan("timeout-plan", root,
                task("timeout-task", timeoutChild, "timeout-group"),
                task("sibling-task", successfulChild, "timeout-group"));
        ScenarioModelFactory models = rootOnlyModels(root, plan,
                Map.of("timeout-task", timeoutChild, "sibling-task", successfulChild));
        AtomicReference<ExecutionCoordinator> coordinator = new AtomicReference<>();
        MissionExecutionEngine directEngine = (currentSession, definition, objective, missionInput, model,
                visibleTools, planningEnabled, currentAuthentication) -> switch (definition.manifest().getName())
        {
            case "timeoutChild" -> {
                await(siblingStarted, "sibling must start before the nested timeout");
                timeoutThrown.countDown();
                throw expected;
            }
            case "timeoutSibling" -> {
                siblingStarted.countDown();
                await(timeoutThrown, "sibling must remain alive through the nested timeout");
                yield "sibling complete";
            }
            default -> throw new AssertionError("unexpected direct mission");
        };

        List<YamlSkillDefinition> definitions = List.of(
                plannedDefinition(root, List.of(timeoutChild, successfulChild)),
                directDefinition(timeoutChild, List.of()),
                directDefinition(successfulChild, List.of()));
        List<CapabilityMetadata> capabilities = List.of(
                yamlCapability(root, false, arguments -> "unused"),
                yamlCapability(timeoutChild, false, arguments -> "unused"),
                yamlCapability(successfulChild, false, arguments -> "unused"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            ExecutionCoordinator runtime = runtime(definitions, capabilities, models, directEngine,
                    state, executor, new NoOpSessionUsageService(), coordinator);

            assertThatThrownBy(() -> runtime.execute(root, "exercise child timeout", session, authentication))
                    .isSameAs(expected);
        }

        assertThat(timeoutThrown.getCount()).isZero();
        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.COMPLETED);
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void parentTimeoutRevokesLateWritesFromAnActualNestedDirectMission() throws Exception
    {
        String root = "parentTimeoutRoot";
        String child = "lateNestedChild";
        Authentication authentication = allowedAuthentication();
        LoomspanSession session = session("concurrent-parent-timeout", root, 4, authentication);
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch childReturned = new CountDownLatch(1);
        AtomicInteger externalSideEffects = new AtomicInteger();
        AtomicReference<MissionContext> childMission = new AtomicReference<>();
        LinterOutcome preCutoffLinter = new LinterOutcome(
                child, "regex", 1, 0, 1, LinterOutcomeStatus.PASSED, null);
        LinterOutcome postCutoffLinter = new LinterOutcome(
                child, "regex", 2, 0, 1, LinterOutcomeStatus.PASSED, null);
        ExecutionPlan plan = groupedPlan("parent-timeout-plan", root,
                task("late-child-task", child, null));
        ScenarioModelFactory models = rootOnlyModels(root, plan, Map.of("late-child-task", child));
        AtomicReference<ExecutionCoordinator> coordinator = new AtomicReference<>();
        MissionExecutionEngine directEngine = (currentSession, definition, objective, missionInput, model,
                visibleTools, planningEnabled, currentAuthentication) -> {
            ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
            childMission.set(binding.requireMission());
            state.recordLinterOutcome(currentSession, preCutoffLinter);
            childStarted.countDown();
            boolean interrupted = false;
            while (releaseChild.getCount() > 0)
            {
                try { releaseChild.await(); }
                catch (InterruptedException ex) { interrupted = true; }
            }
            externalSideEffects.incrementAndGet();
            state.recordLinterOutcome(currentSession, postCutoffLinter);
            state.storePlan(new ExecutionPlan(
                    "late-child-plan", child, Instant.parse("2026-03-15T12:05:00Z"), List.of()));
            state.recordSuccessfulSkill("lateEvidence", null, true);
            childReturned.countDown();
            if (interrupted) Thread.currentThread().interrupt();
            return "late child result";
        };

        List<YamlSkillDefinition> definitions = List.of(
                plannedDefinition(root, List.of(child)),
                directDefinition(child, List.of()));
        List<CapabilityMetadata> capabilities = List.of(
                yamlCapability(root, false, arguments -> "unused"),
                yamlCapability(child, false, arguments -> "unused"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            ExecutionCoordinator runtime = runtime(definitions, capabilities, models, directEngine,
                    state, executor, new NoOpSessionUsageService(), coordinator, Duration.ofMillis(100));

            assertThatThrownBy(() -> runtime.execute(root, "time out parent", session, authentication))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(childStarted.await(2, TimeUnit.SECONDS)).isTrue();
            List<TraceRecord> finalized = readRecords(session);
            assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
            assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                    .containsExactly(PlanTaskStatus.FAILED);

            releaseChild.countDown();
            assertThat(childReturned.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(externalSideEffects).hasValue(1);
            assertThat(childMission.get().currentPlan()).isEmpty();
            assertThat(childMission.get().successfulDirectSkills()).isEmpty();
            MissionContext parentMission = childMission.get().parent().orElseThrow();
            assertThat(parentMission.lastLinterOutcome()).containsSame(preCutoffLinter);
            assertThat(session.getLastLinterOutcome()).containsSame(preCutoffLinter);
            assertThat(readRecords(session)).containsExactlyElementsOf(finalized);
        }

        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void quotaFailureBecomesMemberOutcomeWithoutRefundingSiblingWork()
    {
        String root = "quotaRoot";
        String quotaChild = "quotaChild";
        String successfulChild = "quotaSibling";
        Authentication authentication = allowedAuthentication();
        LoomspanSession session = session("concurrent-quota", root, 4, authentication);
        LoomspanProperties.Session.Quotas quotas = new LoomspanProperties.Session.Quotas();
        quotas.setMaxProviderAttempts(1);
        SessionUsageService usage = new DefaultSessionUsageService(quotas, new NoOpUsageMetricsRecorder());
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        CountDownLatch siblingReserved = new CountDownLatch(1);
        CountDownLatch quotaAttempted = new CountDownLatch(1);
        ExecutionPlan plan = groupedPlan("quota-plan", root,
                task("quota-task", quotaChild, "quota-group"),
                task("sibling-task", successfulChild, "quota-group"));
        ScenarioModelFactory models = rootOnlyModels(root, plan,
                Map.of("quota-task", quotaChild, "sibling-task", successfulChild));
        AtomicReference<ExecutionCoordinator> coordinator = new AtomicReference<>();
        MissionExecutionEngine directEngine = (currentSession, definition, objective, missionInput, model,
                visibleTools, planningEnabled, currentAuthentication) -> switch (definition.manifest().getName())
        {
            case "quotaSibling" -> {
                usage.reserveProviderAttempt(currentSession, successfulChild);
                siblingReserved.countDown();
                await(quotaAttempted, "successful sibling must finish after the rejected reservation");
                yield "paid work retained";
            }
            case "quotaChild" -> {
                await(siblingReserved, "successful sibling must reserve the only provider attempt first");
                try
                {
                    usage.reserveProviderAttempt(currentSession, quotaChild);
                    yield "unreachable";
                }
                finally
                {
                    quotaAttempted.countDown();
                }
            }
            default -> throw new AssertionError("unexpected direct mission");
        };

        List<YamlSkillDefinition> definitions = List.of(
                plannedDefinition(root, List.of(quotaChild, successfulChild)),
                directDefinition(quotaChild, List.of()),
                directDefinition(successfulChild, List.of()));
        List<CapabilityMetadata> capabilities = List.of(
                yamlCapability(root, false, arguments -> "unused"),
                yamlCapability(quotaChild, false, arguments -> "unused"),
                yamlCapability(successfulChild, false, arguments -> "unused"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            ExecutionCoordinator runtime = runtime(definitions, capabilities, models, directEngine,
                    state, executor, usage, coordinator);

            assertThatThrownBy(() -> runtime.execute(root, "exercise quota", session, authentication))
                    .isInstanceOf(LoomspanQuotaExceededException.class);
        }

        assertThat(quotaAttempted.getCount()).isZero();
        assertThat(usage.snapshot(session).providerAttempts()).isEqualTo(1);
        assertThat(usage.snapshot(session).toolInvocations()).isEqualTo(2);
        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.COMPLETED);
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    private static ExecutionCoordinator runtime(List<YamlSkillDefinition> definitions,
            List<CapabilityMetadata> capabilities,
            ModelInteractionFactory models,
            MissionExecutionEngine directEngine,
            DefaultExecutionStateService state,
            ExecutorService executor,
            SessionUsageService usage,
            AtomicReference<ExecutionCoordinator> coordinator)
    {
        return runtime(definitions, capabilities, models, directEngine, state, executor, usage,
                coordinator, Duration.ofSeconds(5));
    }

    private static ExecutionCoordinator runtime(List<YamlSkillDefinition> definitions,
            List<CapabilityMetadata> capabilities,
            ModelInteractionFactory models,
            MissionExecutionEngine directEngine,
            DefaultExecutionStateService state,
            ExecutorService executor,
            SessionUsageService usage,
            AtomicReference<ExecutionCoordinator> coordinator,
            Duration missionTimeout)
    {
        StubYamlSkillCatalog catalog = new StubYamlSkillCatalog(definitions);
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        capabilities.forEach(capability -> registry.register(capability.name(), capability));
        PlanningService planning = new DefaultPlanningService(state);
        StepLoopMissionExecutionEngine stepEngine = new StepLoopMissionExecutionEngine(
                planning, state, registry, catalog, missionTimeout, executor, usage);
        DefaultAccessGuard accessGuard = new DefaultAccessGuard();
        CapabilityExecutionRouter router = new CapabilityExecutionRouter( coordinatorProvider(coordinator), accessGuard);
        DefaultCapabilityInvoker invoker = new DefaultCapabilityInvoker(
                router, planning, state, usage, new NoOpUsageMetricsRecorder());
        DefaultToolSurfaceService tools = new DefaultToolSurfaceService((skillName, currentSession, authentication) -> {
            YamlSkillDefinition definition = catalog.getSkill(skillName);
            Set<String> allowed = definition.manifest().getAllowedSkills().stream()
                    .map(YamlSkillManifest.AllowedSkillManifest::getName)
                    .collect(java.util.stream.Collectors.toSet());
            return capabilities.stream().filter(capability -> allowed.contains(capability.name())).toList();
        });
        ExecutionCoordinator runtime = new ExecutionCoordinator(
                catalog,
                registry,
                models,
                tools,
                invoker,
                directEngine,
                stepEngine,
                state,
                accessGuard,
                (value, session) -> value,
                new ai.loomspan.internal.security.ScopedAuthentication(null),
                new ai.loomspan.internal.runtime.MissionWorkExecutor(state, missionTimeout, executor, usage));
        coordinator.set(runtime);
        return runtime;
    }

    private static ScenarioModelFactory rootOnlyModels(String root, ExecutionPlan plan, Map<String, String> taskTools)
    {
        return new ScenarioModelFactory(
                Map.of(root, plan), Map.of(root, taskTools), Map.of(root, "unused"), ignored -> { });
    }

    private static ExecutionPlan groupedPlan(String planId, String skillName, PlanTask... tasks)
    {
        return new ExecutionPlan(planId, skillName, Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID, List.of(tasks));
    }

    private static PlanTask task(String taskId, String capabilityName, String group)
    {
        return new PlanTask(taskId, "Run " + capabilityName, PlanTaskStatus.PENDING,
                capabilityName, "Complete " + capabilityName, List.of(), List.of(), group, null);
    }

    private static YamlSkillDefinition plannedDefinition(String name, List<String> allowedSkills)
    {
        YamlSkillManifest manifest = manifest(name, allowedSkills);
        manifest.setPlanningMode(true);
        manifest.setConcurrency(true);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, CONFIG);
    }

    private static YamlSkillDefinition directDefinition(String name, List<String> allowedSkills)
    {
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest(name, allowedSkills), CONFIG);
    }

    private static YamlSkillManifest manifest(String name, List<String> allowedSkills)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setModel("gpt-5");
        manifest.setAllowedSkills(allowedSkills.stream()
                .map(child -> new YamlSkillManifest.AllowedSkillManifest(child, null, null, null))
                .toList());
        return manifest;
    }

    private static CapabilityMetadata yamlCapability(String name, boolean mapped,
            Function<Map<String, Object>, Object> invocation)
    {
        return new CapabilityMetadata(
                "yaml:" + name,
                name,
                name,
                SkillExecutionDescriptor.from(CONFIG), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of("ALLOWED")),
                invocation::apply, mapped ? CapabilityKind.JAVA_SKILL : CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic(name, name), null);
    }

    private static Authentication allowedAuthentication()
    {
        return UsernamePasswordAuthenticationToken.authenticated(
                "user", "pw", AuthorityUtils.createAuthorityList("ROLE_ALLOWED"));
    }

    private static LoomspanSession session(String id, String entrySkill, int maxDepth, Authentication authentication)
    {
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                id, entrySkill, maxDepth);
        session.setAuthentication(authentication);
        return session;
    }

    private static void assertAuthorized(LoomspanSession session)
    {
        assertThat(ExecutionBindingScope.requireCurrent().session()).isSameAs(session);
        assertThat(session.getAuthentication()).containsInstanceOf(Authentication.class)
                .get().extracting(Authentication::getAuthorities)
                .satisfies(authorities -> assertThat(authorities).extracting(Object::toString).contains("ROLE_ALLOWED"));
    }

    private static List<TraceRecord> readRecords(LoomspanSession session)
    {
        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        return List.copyOf(records);
    }

    private static void await(CountDownLatch latch, String description)
    {
        try
        {
            assertThat(latch.await(3, TimeUnit.SECONDS)).as(description).isTrue();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(description + " interrupted", ex);
        }
    }

    private static ObjectProvider<ExecutionCoordinator> coordinatorProvider(
            AtomicReference<ExecutionCoordinator> coordinator)
    {
        return new ObjectProvider<>()
        {
            @Override public ExecutionCoordinator getObject(Object... args) { return getObject(); }
            @Override public ExecutionCoordinator getObject() { return coordinator.get(); }
            @Override public ExecutionCoordinator getIfAvailable() { return coordinator.get(); }
            @Override public ExecutionCoordinator getIfUnique() { return coordinator.get(); }
            @Override public Stream<ExecutionCoordinator> stream() { return Stream.of(coordinator.get()); }
            @Override public Stream<ExecutionCoordinator> orderedStream() { return stream(); }
        };
    }

    private static final class ScenarioModelFactory implements ModelInteractionFactory
    {
        private final Map<String, ExecutionPlan> plans;
        private final Map<String, Map<String, String>> taskTools;
        private final Map<String, String> finalResponses;
        private final java.util.function.Consumer<String> planningStarted;

        private ScenarioModelFactory(Map<String, ExecutionPlan> plans,
                Map<String, Map<String, String>> taskTools,
                Map<String, String> finalResponses,
                java.util.function.Consumer<String> planningStarted)
        {
            this.plans = Map.copyOf(plans);
            this.taskTools = Map.copyOf(taskTools);
            this.finalResponses = Map.copyOf(finalResponses);
            this.planningStarted = planningStarted;
        }

        @Override
        public ModelInteraction create(YamlSkillDefinition definition,
                ai.loomspan.internal.model.ModelInteractionMode mode)
        {
            String skillName = definition.manifest().getName();
            return request -> respond(skillName, request);
        }

        private ModelInteractionResult respond(String skillName, ModelInteractionRequest request)
        {
            String prompt = request.systemPrompt();
            String response;
            if (prompt.contains("--- ASSIGNED TASK ---"))
            {
                Map.Entry<String, String> task = taskTools.getOrDefault(skillName, Map.of()).entrySet().stream()
                        .filter(entry -> prompt.contains("ID: " + entry.getKey()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No assigned response for " + skillName));
                response = "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"%s\",\"toolName\":\"%s\",\"toolArguments\":{}}"
                        .formatted(task.getKey(), task.getValue());
            }
            else if (prompt.contains("All required plan tasks are already COMPLETE"))
            {
                response = "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"%s\"}"
                        .formatted(finalResponses.getOrDefault(skillName, "complete"));
            }
            else
            {
                planningStarted.accept(skillName);
                response = writePlan(plans.get(skillName));
            }
            return new ModelInteractionResult(response, Map.of(
                    ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, request.traceContext().nextAttempt()));
        }
    }

    private static String writePlan(ExecutionPlan plan)
    {
        try
        {
            return PLAN_MAPPER.writeValueAsString(plan);
        }
        catch (JacksonException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static final class StubYamlSkillCatalog extends YamlSkillCatalog
    {
        private final Map<String, YamlSkillDefinition> definitions;

        private StubYamlSkillCatalog(List<YamlSkillDefinition> definitions)
        {
            super(new LoomspanProperties(), new LoomspanProperties.Skills());
            this.definitions = definitions.stream().collect(java.util.stream.Collectors.toMap(
                    definition -> definition.manifest().getName(), Function.identity()));
        }

        @Override
        public YamlSkillDefinition getSkill(String name)
        {
            return definitions.get(name);
        }
    }
}
