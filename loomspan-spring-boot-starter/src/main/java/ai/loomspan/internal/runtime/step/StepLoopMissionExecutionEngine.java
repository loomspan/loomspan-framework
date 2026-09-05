package ai.loomspan.internal.runtime.step;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.LoomspanStackOverflowException;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.PhysicalBranchContext;
import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.core.MissionLifecycle;
import ai.loomspan.internal.core.MissionWriteRevokedException;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceFailureMetadata;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaValidationIssue;
import ai.loomspan.internal.outputschema.OutputSchemaValidationResult;
import ai.loomspan.internal.outputschema.OutputSchemaValidator;
import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.attachment.DefaultMissionInputMaterializer;
import ai.loomspan.internal.runtime.attachment.MissionInputMaterializer;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.evidence.EvidenceBackedOutputValidator;
import ai.loomspan.internal.runtime.evidence.EvidenceCoverageResult;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.prompt.SkillPromptComposer;
import ai.loomspan.internal.runtime.prompt.SkillPromptComposition;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.vfs.DefaultRefResolver;
import ai.loomspan.internal.vfs.SessionLocalVirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionRequest;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plan-step execution engine that replaces the single-shot mission model call with a deterministic loop.
 */
public class StepLoopMissionExecutionEngine implements MissionExecutionEngine
{
    private static final Logger log = LoggerFactory.getLogger(StepLoopMissionExecutionEngine.class);

    private static final int DEFAULT_MAX_STEPS = 10;
    private static final int MAX_INVALID_ACTION_RETRIES = 1;
    private static final int MAX_EXECUTION_SUMMARY_LINES = 5;

    private final PlanningService planningService;
    private final ExecutionStateService executionStateService;
    @SuppressWarnings("unused")
    private final CapabilityRegistry capabilityRegistry;
    private @Nullable YamlSkillCatalog yamlSkillCatalog;
    private final Duration missionTimeout;
    private final ExecutorService missionExecutor;
    private final SessionUsageService sessionUsageService;
    private final ObjectMapper objectMapper;
    private final OutputSchemaValidator outputSchemaValidator;
    private final EvidenceBackedOutputValidator evidenceBackedOutputValidator;
    private final int defaultMaxSteps;
    private final MissionInputMaterializer missionInputMaterializer;

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            YamlSkillCatalog ignoredYamlSkillCatalog,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, DEFAULT_MAX_STEPS);
        this.yamlSkillCatalog = Objects.requireNonNull(ignoredYamlSkillCatalog, "yamlSkillCatalog must not be null");
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            YamlSkillCatalog yamlSkillCatalog,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService,
            MissionInputMaterializer missionInputMaterializer,
            ObjectMapper schemaMapper)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, DEFAULT_MAX_STEPS, missionInputMaterializer, schemaMapper);
        this.yamlSkillCatalog = Objects.requireNonNull(yamlSkillCatalog, "yamlSkillCatalog must not be null");
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            YamlSkillCatalog ignoredYamlSkillCatalog,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService,
            int defaultMaxSteps)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, defaultMaxSteps, defaultMaterializer());
        this.yamlSkillCatalog = Objects.requireNonNull(ignoredYamlSkillCatalog, "yamlSkillCatalog must not be null");
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, DEFAULT_MAX_STEPS);
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService,
            int defaultMaxSteps)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, defaultMaxSteps, defaultMaterializer());
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            YamlSkillCatalog ignoredYamlSkillCatalog,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService,
            MissionInputMaterializer missionInputMaterializer)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, DEFAULT_MAX_STEPS, missionInputMaterializer);
        this.yamlSkillCatalog = Objects.requireNonNull(ignoredYamlSkillCatalog, "yamlSkillCatalog must not be null");
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService,
            int defaultMaxSteps,
            MissionInputMaterializer missionInputMaterializer)
    {
        this(planningService, executionStateService, capabilityRegistry, missionTimeout, missionExecutor,
                sessionUsageService, defaultMaxSteps, missionInputMaterializer,
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
    }

    public StepLoopMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            Duration missionTimeout,
            ExecutorService missionExecutor,
            SessionUsageService sessionUsageService,
            int defaultMaxSteps,
            MissionInputMaterializer missionInputMaterializer,
            ObjectMapper schemaMapper)
    {
        this.planningService = Objects.requireNonNull(planningService, "planningService must not be null");
        this.executionStateService = Objects.requireNonNull(executionStateService, "executionStateService must not be null");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
        this.yamlSkillCatalog = null;
        this.missionTimeout = Objects.requireNonNull(missionTimeout, "missionTimeout must not be null");
        this.missionExecutor = Objects.requireNonNull(missionExecutor, "missionExecutor must not be null");
        this.sessionUsageService = Objects.requireNonNull(sessionUsageService, "sessionUsageService must not be null");
        this.objectMapper = Objects.requireNonNull(schemaMapper, "schemaMapper must not be null");
        this.outputSchemaValidator = new OutputSchemaValidator(schemaMapper);
        this.evidenceBackedOutputValidator = new EvidenceBackedOutputValidator(schemaMapper,
                new ai.loomspan.internal.runtime.evidence.EvidenceCoverageValidator());
        this.defaultMaxSteps = defaultMaxSteps;
        this.missionInputMaterializer = Objects.requireNonNull(missionInputMaterializer, "missionInputMaterializer must not be null");
    }

    @Override
    public String executeMission(LoomspanSession session,
            YamlSkillDefinition definition,
            String objective,
            @Nullable Map<String, Object> missionInput,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            boolean planningEnabled,
            @Nullable Authentication authentication)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        Objects.requireNonNull(modelInteraction, "modelInteraction must not be null");
        Objects.requireNonNull(visibleTools, "visibleTools must not be null");
        String skillName = definition.manifest().getName();
        EffectiveSkillExecutionConfiguration executionConfiguration = definition.requireExecutionConfiguration();

        ExecutionBinding capturedBinding = ExecutionBindingScope.requireCurrent();
        if (capturedBinding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        PhysicalBranchContext branch = capturedBinding.branch();
        MissionContext missionContext = capturedBinding.requireMission();
        MissionLifecycle lifecycle = missionContext.lifecycle();

        Callable<String> missionCall = () -> ExecutionBindingScope.callWith(capturedBinding, () ->
        {
            lifecycle.markOwningStarted();
            try
            {
                lifecycle.requireOpenForNewWork(capturedBinding);
                sessionUsageService.recordMissionStart(session, skillName);
                RenderedMissionInput renderedInput = missionInputMaterializer.materialize(session, definition, objective, missionInput);

                if (planningEnabled)
                {
                    planningService.initializePlan(
                            session,
                            objective,
                            planningInput(missionInput, renderedInput),
                            definition,
                            modelInteraction,
                            visibleTools);
                }

                Optional<ExecutionPlan> planOpt = executionStateService.currentPlan();
                if (planOpt.isEmpty())
                {
                    throw new IllegalStateException(
                            "Step-loop execution requires a plan but none was created for skill '" + skillName + "'");
                }

                try
                {
                    return executeStepLoop(session, definition, objective, renderedInput, executionConfiguration, modelInteraction,
                            visibleTools, lifecycle);
                }
                catch (LoomspanMissionTimeoutException failure)
                {
                    lifecycle.beginCancellation(capturedBinding, failure, () -> executionStateService.recordFailure(
                            session, failure, Map.of("message", "Mission execution interrupted")), false);
                    throw failure;
                }
            }
            finally
            {
                lifecycle.owningReturned();
            }
        });

        Future<String> mission;
        try
        {
            mission = missionExecutor.submit(missionCall);
            lifecycle.registerOwningFuture(mission);
        }
        catch (RuntimeException | Error ex)
        {
            lifecycle.beginCancellation(capturedBinding, ex, () -> executionStateService.recordFailure(
                    session, ex, Map.of("message", "Mission submission failed")));
            lifecycle.closeNow();
            throw ex;
        }
        try
        {
            return mission.get(missionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ex)
        {
            LoomspanMissionTimeoutException failure = new LoomspanMissionTimeoutException(
                    session.getSessionId(), skillName, missionTimeout, ex);
            MissionLifecycle.PrimaryCancellation primary = lifecycle.beginCancellation(capturedBinding, failure,
                    () -> executionStateService.recordFailure(session, failure, Map.of("message", "Mission execution timed out")));
            cleanupPreservingPrimary(session, missionContext, capturedBinding, lifecycle.awaitCutoff(), primary.cause());
            throw propagate(primary.cause());
        }
        catch (InterruptedException ex)
        {
            LoomspanMissionTimeoutException failure = new LoomspanMissionTimeoutException(
                    session.getSessionId(), skillName, missionTimeout, ex);
            MissionLifecycle.PrimaryCancellation primary = lifecycle.beginCancellation(capturedBinding, failure,
                    () -> executionStateService.recordFailure(session, failure, Map.of("message", "Mission execution interrupted")));
            cleanupPreservingPrimary(session, missionContext, capturedBinding, lifecycle.awaitCutoff(), primary.cause());
            Thread.currentThread().interrupt();
            throw propagate(primary.cause());
        }
        catch (ExecutionException ex)
        {
            if (lifecycle.primaryCancellation().isPresent())
            {
                MissionLifecycle.PrimaryCancellation primary = lifecycle.primaryCancellation().orElseThrow();
                cleanupPreservingPrimary(session, missionContext, capturedBinding, lifecycle.awaitCutoff(), primary.cause());
                throw propagate(primary.cause());
            }
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException)
            {
                throw unwrapMissionFailure(runtimeException);
            }
            if (cause instanceof Error error)
            {
                throw error;
            }
            throw new IllegalStateException("Mission execution failed for skill '" + skillName + "'", cause);
        }
        catch (java.util.concurrent.CancellationException ex)
        {
            MissionLifecycle.PrimaryCancellation primary = lifecycle.primaryCancellation().orElseThrow(() -> ex);
            cleanupPreservingPrimary(session, missionContext, capturedBinding, lifecycle.awaitCutoff(), primary.cause());
            throw propagate(primary.cause());
        }
    }

    private String executeStepLoop(LoomspanSession session,
            YamlSkillDefinition definition,
            String objective,
            RenderedMissionInput renderedInput,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            MissionLifecycle lifecycle)
    {
        String skillName = definition.manifest().getName();
        int maxSteps = definition.maxSteps(defaultMaxSteps);
        if (maxSteps <= 0)
        {
            IllegalStateException failure = new IllegalStateException(
                        "Step-loop execution requires max_steps > 0 for skill '%s' but was %d."
                            .formatted(skillName, maxSteps));
            recordTerminalFailure(session, skillName, 0, failure,
                    "Step-loop execution rejected invalid max_steps value: " + maxSteps);
            throw failure;
        }

        MissionContext mission = ExecutionBindingScope.requireCurrent().requireMission();
        ExecutionPlan acceptedPlan = executionStateService.currentPlan()
                .orElseThrow(() -> new IllegalStateException(
                        "Plan disappeared before step-loop execution for skill '" + skillName + "'"));
        List<ExecutionUnit> units = ExecutionUnit.partition(acceptedPlan.tasks());
        Set<String> earlierTaskIds = new LinkedHashSet<>();
        int nextTaskStep = 1;

        for (ExecutionUnit unit : units)
        {
            lifecycle.requireOpenForNewWork(ExecutionBindingScope.requireCurrent());
            if (Thread.currentThread().isInterrupted())
            {
                throw new LoomspanMissionTimeoutException(session.getSessionId(), skillName, missionTimeout,
                        new InterruptedException("Step loop interrupted"));
            }

            ExecutionPlan currentPlan = executionStateService.currentPlan()
                    .orElseThrow(() -> new IllegalStateException("Plan disappeared during execution-unit traversal."));
            boolean unitAlreadyComplete = unit.members().stream().allMatch(member -> currentPlan.findTask(member.taskId())
                    .map(task -> task.status() == PlanTaskStatus.COMPLETED)
                    .orElse(false));
            if (unitAlreadyComplete)
            {
                unit.members().forEach(task -> earlierTaskIds.add(task.taskId()));
                continue;
            }

            if (nextTaskStep + unit.members().size() > maxSteps)
            {
                IllegalStateException failure = new IllegalStateException("Step-loop exhausted %d steps for skill '%s' before the plan completed."
                        .formatted(maxSteps, skillName));
                recordTerminalFailure(session, skillName, nextTaskStep, failure,
                        "Step limit reached before the complete execution unit and final synthesis could be admitted.");
                throw failure;
            }
            preflightUnit(session, skillName, unit, earlierTaskIds, nextTaskStep, visibleTools);
            boolean concurrentDispatch = unit.parallelGroup() != null && definition.concurrencyEnabled();
            List<AssignedTaskExecution> assignments = new ArrayList<>(unit.members().size());
            for (int memberIndex = 0; memberIndex < unit.members().size(); memberIndex++)
            {
                assignments.add(new AssignedTaskExecution(
                        unit.members().get(memberIndex), nextTaskStep + memberIndex, concurrentDispatch));
            }

            if (concurrentDispatch)
            {
                Admission admission = admitAssignments(session, assignments, lifecycle);
                ExecutionPlan admittedPlan = admission.plan();
                String priorLastToolResult = mission.lastToolResult().orElse(null);
                String priorExecutionSummary = mission.executionSummary().orElse(null);
                ExecutionBinding coordinatorBinding = ExecutionBindingScope.requireCurrent();
                try
                {
                    for (MissionLifecycle.AdmittedTask entry : admission.entries())
                    {
                        lifecycle.requireOpenForNewWork(coordinatorBinding);
                        AssignedTaskExecution assignment = entry.assignment();
                        ExecutionBinding workerBinding = coordinatorBinding.forkBranch();
                        lifecycle.bindBranch(entry, workerBinding);
                        Future<AssignedTaskOutcome> future = missionExecutor.submit(() -> {
                            try
                            {
                                if (!lifecycle.taskStarted(entry, workerBinding))
                                {
                                    throw new MissionWriteRevokedException(skillName);
                                }
                                AssignedTaskOutcome outcome = executeAssignedTask(
                                        session, skillName, objective, renderedInput, executionConfiguration, modelInteraction,
                                        visibleTools, admittedPlan, assignment, workerBinding, priorLastToolResult,
                                        priorExecutionSummary, definition, lifecycle);
                                lifecycle.publishOutcome(entry, workerBinding, outcome);
                                return outcome;
                            }
                            finally { lifecycle.taskReturned(entry); }
                        });
                        lifecycle.registerFuture(entry, future);
                    }
                }
                catch (RuntimeException | Error ex)
                {
                    lifecycle.beginCancellation(ExecutionBindingScope.requireCurrent(), ex, () -> executionStateService.recordFailure(
                            session, ex, Map.of("message", "Concurrent task dispatch failed")), false);
                    throw ex;
                }

                List<AssignedOutcome> outcomes = joinAssignedTasks(session, skillName, admission.entries(), lifecycle);
                foldOutcomes(session, unit, outcomes, mission);
                propagateFirstFailure(outcomes);
                nextTaskStep += assignments.size();
            }
            else
            {
                for (AssignedTaskExecution assignment : assignments)
                {
                    Admission admission = admitAssignments(session, List.of(assignment), lifecycle);
                    ExecutionPlan admittedPlan = admission.plan();
                    MissionLifecycle.AdmittedTask entry = admission.entries().getFirst();
                    ExecutionBinding workerBinding = ExecutionBindingScope.requireCurrent().forkBranch();
                    lifecycle.bindBranch(entry, workerBinding);
                    AssignedTaskOutcome outcome;
                    try
                    {
                        if (!lifecycle.taskStarted(entry, workerBinding))
                        {
                            throw new MissionWriteRevokedException(skillName);
                        }
                        outcome = executeAssignedTask(
                                session, skillName, objective, renderedInput, executionConfiguration, modelInteraction,
                                visibleTools, admittedPlan, assignment, workerBinding, mission.lastToolResult().orElse(null),
                                mission.executionSummary().orElse(null), definition, lifecycle);
                        lifecycle.publishOutcome(entry, workerBinding, outcome);
                    }
                    finally { lifecycle.taskReturned(entry); }
                    List<AssignedOutcome> outcomes = List.of(new AssignedOutcome(assignment, outcome));
                    foldOutcomes(session, unit, outcomes, mission);
                    propagateFirstFailure(outcomes);
                    nextTaskStep++;
                }
            }
            unit.members().forEach(task -> earlierTaskIds.add(task.taskId()));
        }

        lifecycle.requireOpenForNewWork(ExecutionBindingScope.requireCurrent());
        ExecutionPlan completedPlan = executionStateService.currentPlan()
                .orElseThrow(() -> new IllegalStateException("Plan disappeared before final synthesis for skill '" + skillName + "'"));
        if (nextTaskStep > maxSteps)
        {
            IllegalStateException failure = new IllegalStateException("Step-loop exhausted %d steps for skill '%s' before the plan completed."
                    .formatted(maxSteps, skillName));
            recordTerminalFailure(session, skillName, maxSteps, failure, "Step limit reached before final synthesis.");
            throw failure;
        }
        StepResult finalResult = executeOneStep(
                session, skillName, objective, renderedInput, executionConfiguration, modelInteraction, visibleTools,
                completedPlan, nextTaskStep, mission.lastToolResult().orElse(null), mission.executionSummary().orElse(null),
                definition, null, lifecycle);
        if (!finalResult.isFinalResponse())
        {
            throw new IllegalStateException("Final synthesis did not return a final response for skill '" + skillName + "'.");
        }
        return finalResult.finalResponse();
    }

    private void preflightUnit(LoomspanSession session,
            String skillName,
            ExecutionUnit unit,
            Set<String> earlierTaskIds,
            int stepNumber,
            List<BoundCapability> visibleTools)
    {
        ExecutionPlan plan = executionStateService.currentPlan()
                .orElseThrow(() -> new IllegalStateException("Plan disappeared during execution-unit preflight."));
        boolean earlierIncomplete = plan.tasks().stream()
                .filter(task -> earlierTaskIds.contains(task.taskId()))
                .anyMatch(task -> task.status() != PlanTaskStatus.COMPLETED);
        boolean invalidMember = unit.members().stream().anyMatch(accepted -> {
            long exactBindings = visibleTools.stream()
                    .filter(Objects::nonNull)
                    .filter(tool -> Objects.equals(tool.name(), accepted.capabilityName()))
                    .count();
            return exactBindings != 1 || plan.findTask(accepted.taskId())
                .map(current -> current.status() != PlanTaskStatus.PENDING
                        || !Objects.equals(current.capabilityName(), accepted.capabilityName())
                        || current.dependsOn().stream().anyMatch(dependency -> !earlierTaskIds.contains(dependency)
                                || plan.findTask(dependency)
                                        .map(task -> task.status() != PlanTaskStatus.COMPLETED)
                                        .orElse(true)))
                .orElse(true);
        });
        if (!earlierIncomplete && !invalidMember) return;

        IllegalStateException failure = new IllegalStateException(
                "Execution unit is not eligible at step %d for skill '%s'.".formatted(stepNumber, skillName));
        recordTerminalFailure(session, skillName, stepNumber, failure,
                "Execution-unit state did not match accepted task-list ordering.");
        throw failure;
    }

    private Admission admitAssignments(LoomspanSession session, List<AssignedTaskExecution> assignments,
            MissionLifecycle lifecycle)
    {
        List<AssignedTaskExecution> orderedAssignments = List.copyOf(assignments);
        if (orderedAssignments.isEmpty()) throw new IllegalArgumentException("assignments must not be empty");
        ExecutionPlan plan = executionStateService.currentPlan()
                .orElseThrow(() -> new IllegalStateException("Plan disappeared before task admission."));
        ExecutionPlan admitted = plan;
        for (AssignedTaskExecution assignment : orderedAssignments)
        {
            PlanTask acceptedTask = assignment.task();
            PlanTask current = plan.findTask(acceptedTask.taskId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Assigned task disappeared before admission: " + acceptedTask.taskId()));
            if (current.status() != PlanTaskStatus.PENDING)
            {
                throw new IllegalStateException("Assigned task '%s' must be PENDING before admission but was %s."
                        .formatted(current.taskId(), current.status()));
            }
            if (!Objects.equals(current.capabilityName(), acceptedTask.capabilityName()))
            {
                throw new IllegalStateException("Assigned task capability changed before admission: " + current.taskId());
            }
            admitted = admitted.updateTask(current.taskId(),
                    task -> task.bindInProgress("Starting tool " + task.capabilityName()));
        }
        ExecutionPlan admittedPlan = admitted;
        List<MissionLifecycle.AdmittedTask> entries = lifecycle.admitUnit(
                ExecutionBindingScope.requireCurrent(), orderedAssignments, () -> {
            AssignedTaskExecution first = orderedAssignments.getFirst();
            executionStateService.logPlanUpdated(session, admittedPlan, PlanExecutionTransition.admission(
                    orderedAssignments.stream().map(entry -> entry.task().taskId()).toList(),
                    first.task().parallelGroup(), first.effectiveConcurrency()));
            executionStateService.storePlan(admittedPlan);
        });
        return new Admission(admittedPlan, entries);
    }

    private AssignedTaskOutcome executeAssignedTask(LoomspanSession session,
            String skillName,
            String objective,
            RenderedMissionInput renderedInput,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            ExecutionPlan admittedPlan,
            AssignedTaskExecution assignment,
            ExecutionBinding workerBinding,
            @Nullable String priorLastToolResult,
            @Nullable String priorExecutionSummary,
            @Nullable YamlSkillDefinition skillDefinition,
            MissionLifecycle lifecycle)
    {
        PhysicalBranchContext workerBranch = workerBinding.branch();
        try
        {
            StepResult result = ExecutionBindingScope.supplyWith(workerBinding, () -> executeOneStep(
                    session, skillName, objective, renderedInput, executionConfiguration, modelInteraction, visibleTools,
                    admittedPlan, assignment.stepNumber(), priorLastToolResult,
                    priorExecutionSummary, skillDefinition, assignment, lifecycle));
            return new AssignedTaskOutcome.Success(
                    Objects.requireNonNull(result.toolResult(), "assigned tool result must not be null"),
                    workerBranch.lastLinterOutcome().orElse(null),
                    workerBranch.lastOutputSchemaOutcome().orElse(null));
        }
        catch (RuntimeException ex)
        {
            RuntimeException unwrapped = unwrapMissionFailure(ex);
            if (Thread.currentThread().isInterrupted())
            {
                throw unwrapped;
            }
            String failureId = ExecutionBindingScope.supplyWith(workerBinding, () -> executionStateService.recordFailure(
                    session, unwrapped, Map.of("message", "Assigned task execution failed",
                            "taskId", assignment.task().taskId(), "stepNumber", assignment.stepNumber())));
            return new AssignedTaskOutcome.Failure(
                    unwrapped,
                    failureId,
                    workerBranch.lastLinterOutcome().orElse(null),
                    workerBranch.lastOutputSchemaOutcome().orElse(null));
        }
    }

    private List<AssignedOutcome> joinAssignedTasks(LoomspanSession session,
            String skillName,
            List<MissionLifecycle.AdmittedTask> entries,
            MissionLifecycle lifecycle)
    {
        List<MissionLifecycle.AdmittedTask> orderedEntries = List.copyOf(entries);
        List<AssignedOutcome> outcomes = new ArrayList<>(orderedEntries.size());
        try
        {
            for (MissionLifecycle.AdmittedTask entry : orderedEntries)
            {
                outcomes.add(new AssignedOutcome(entry.assignment(), entry.future().orElseThrow().get()));
            }
            return List.copyOf(outcomes);
        }
        catch (InterruptedException ex)
        {
            LoomspanMissionTimeoutException failure = new LoomspanMissionTimeoutException(
                    session.getSessionId(), skillName, missionTimeout, ex);
            MissionLifecycle.PrimaryCancellation primary = lifecycle.beginCancellation(
                    ExecutionBindingScope.requireCurrent(), failure, () -> executionStateService.recordFailure(
                            session, failure, Map.of("message", "Concurrent task join interrupted")), false);
            Thread.currentThread().interrupt();
            throw propagate(primary.cause());
        }
        catch (ExecutionException ex)
        {
            lifecycle.beginCancellation(ExecutionBindingScope.requireCurrent(), ex.getCause(), () -> executionStateService.recordFailure(
                    session, ex.getCause(), Map.of("message", "Concurrent task join failed")), false);
            throw propagate(ex.getCause());
        }
    }

    private static void propagateFirstFailure(List<AssignedOutcome> outcomes)
    {
        outcomes.stream()
                .map(AssignedOutcome::outcome)
                .filter(AssignedTaskOutcome.Failure.class::isInstance)
                .map(AssignedTaskOutcome.Failure.class::cast)
                .findFirst()
                .ifPresent(failure -> { throw propagate(failure.failure()); });
    }

    private static RuntimeException propagate(Throwable failure)
    {
        if (failure instanceof RuntimeException runtimeException) return runtimeException;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Assigned task failed", failure);
    }

    private void foldOutcomes(LoomspanSession session,
            ExecutionUnit unit,
            List<AssignedOutcome> assignedOutcomes,
            MissionContext mission)
    {
        ExecutionBindingScope.requireCurrent().requireWritable(() -> {
            foldWritableOutcomes(session, unit, assignedOutcomes, mission);
            return null;
        });
    }

    private void foldWritableOutcomes(LoomspanSession session,
            ExecutionUnit unit,
            List<AssignedOutcome> assignedOutcomes,
            MissionContext mission)
    {
        ExecutionPlan plan = executionStateService.currentPlan()
                .orElseThrow(() -> new IllegalStateException("Plan disappeared during outcome fold."));
        int previousMemberIndex = -1;
        for (AssignedOutcome assignedOutcome : List.copyOf(assignedOutcomes))
        {
            AssignedTaskExecution assignment = assignedOutcome.assignment();
            int memberIndex = unit.members().indexOf(assignment.task());
            if (memberIndex < 0 || memberIndex <= previousMemberIndex)
            {
                throw new IllegalStateException("Outcomes must be folded in execution-unit member order.");
            }
            previousMemberIndex = memberIndex;

            PlanTask current = plan.findTask(assignment.task().taskId())
                    .orElseThrow(() -> new IllegalStateException("Assigned task disappeared during fold."));
            if (current.status() != PlanTaskStatus.IN_PROGRESS)
            {
                throw new IllegalStateException("Assigned task '%s' must be IN_PROGRESS during fold but was %s."
                        .formatted(current.taskId(), current.status()));
            }

            ExecutionPlan folded;
            if (assignedOutcome.outcome() instanceof AssignedTaskOutcome.Success success)
            {
                folded = plan.updateTask(current.taskId(),
                        task -> task.complete("Completed tool " + task.capabilityName()));
                executionStateService.recordSuccessfulSkill(current.capabilityName(), current.taskId(), false);
                String summaryLine = "Step %d: Called %s for task %s -> %s".formatted(
                        assignment.stepNumber(), current.capabilityName(), current.taskId(), truncate(success.result(), 100));
                mission.appendExecutionSummary(summaryLine);
                mission.setLastToolResult(success.result());
                if (success.linterOutcome() != null) mission.recordLinterOutcome(success.linterOutcome());
                if (success.outputSchemaOutcome() != null) mission.recordOutputSchemaOutcome(success.outputSchemaOutcome());
            }
            else if (assignedOutcome.outcome() instanceof AssignedTaskOutcome.Failure failure)
            {
                folded = plan.updateTask(current.taskId(), task -> task.fail(
                                "Tool " + task.capabilityName() + " failed: " + failure.failure().getClass().getSimpleName()))
                        .withStatus(ai.loomspan.internal.core.PlanStatus.STALE);
                if (failure.linterOutcome() != null) mission.recordLinterOutcome(failure.linterOutcome());
                if (failure.outputSchemaOutcome() != null) mission.recordOutputSchemaOutcome(failure.outputSchemaOutcome());
            }
            else
            {
                throw new IllegalStateException("Unknown assigned task outcome type.");
            }
            plan = folded;
        }
        executionStateService.storePlan(plan);
        List<AssignedOutcome> orderedOutcomes = List.copyOf(assignedOutcomes);
        AssignedTaskExecution first = orderedOutcomes.getFirst().assignment();
        PlanExecutionTransition.JoinOutcome joinOutcome = orderedOutcomes.stream()
                .anyMatch(entry -> entry.outcome() instanceof AssignedTaskOutcome.Failure)
                ? PlanExecutionTransition.JoinOutcome.FAILED : PlanExecutionTransition.JoinOutcome.COMPLETED;
        executionStateService.logPlanUpdated(session, plan, PlanExecutionTransition.join(
                orderedOutcomes.stream().map(entry -> entry.assignment().task().taskId()).toList(),
                first.task().parallelGroup(), joinOutcome));
    }

    private Map<String, Object> trustedStepIdentity(
            ExecutionPlan plan, @Nullable AssignedTaskExecution assignment, int stepNumber)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planId", plan.planId());
        metadata.put("stepNumber", stepNumber);
        if (assignment != null)
        {
            metadata.put("assignedTaskId", assignment.task().taskId());
            metadata.put("effectiveConcurrency", assignment.effectiveConcurrency());
            if (assignment.task().parallelGroup() != null)
            {
                metadata.put("parallelGroup", assignment.task().parallelGroup());
            }
        }
        return Map.copyOf(metadata);
    }

    private static Map<String, Object> mergeMetadata(Map<String, Object> first, Map<String, Object> second)
    {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(first);
        merged.putAll(second);
        return Map.copyOf(merged);
    }

    private StepResult executeOneStep(LoomspanSession session,
            String skillName,
            String objective,
            RenderedMissionInput renderedInput,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            ExecutionPlan plan,
            int stepNumber,
            @Nullable String lastToolResult,
            @Nullable String executionSummary,
            @Nullable YamlSkillDefinition skillDefinition,
            @Nullable AssignedTaskExecution assignment,
            MissionLifecycle lifecycle)
    {
        boolean finalResponseOnly = assignment == null;
        Map<String, Object> trustedIdentity = trustedStepIdentity(plan, assignment, stepNumber);
        ExecutionFrame stepFrame = executionStateService.openFrame(
                session,
                TraceFrameType.STEP_EXECUTION,
                skillName + "#step-" + stepNumber,
                trustedIdentity);

        executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_STARTED,
                mergeMetadata(trustedIdentity, Map.of("readyTasks", plan.readyTasks().size())),
                Map.of("planStatus", plan.status().name()));

        String stepFrameStatus = "completed";
        Throwable stepFailure = null;
        StepAction lastAction = null;
        boolean forceVerboseToolArgumentGuidance = false;
        try
        {
            int invalidActionRetryCount = 0;
            int linterAttempt = 1;
            int outputSchemaAttempt = 1;
            int evidenceAttempt = 1;
            String invalidActionFeedback = null;
            while (true)
            {
                lastAction = null;
                String stepPrompt = assignment == null
                        ? StepPromptBuilder.buildFinalResponsePrompt(
                                plan, objective, renderedInput.traceSafeInput(), stepNumber, lastToolResult,
                                executionSummary,
                                skillDefinition == null ? null : skillDefinition.outputSchema())
                        : StepPromptBuilder.buildAssignedStepPrompt(
                                plan, assignment.task(), objective, renderedInput.traceSafeInput(), stepNumber,
                                lastToolResult, executionSummary, visibleTools, forceVerboseToolArgumentGuidance);
                SkillPromptComposition promptComposition = skillDefinition == null
                        ? new SkillPromptComposition(stepPrompt, false, null, "step_execution_prompt")
                        : SkillPromptComposer.composeStepExecutionPrompt(skillDefinition, stepPrompt);
                String stepUserMessage = StepPromptBuilder.buildStepUserMessage(plan, objective, renderedInput.traceSafeInput());
                String effectivePrompt = invalidActionFeedback == null || invalidActionFeedback.isBlank()
                        ? promptComposition.systemPrompt()
                        : promptComposition.systemPrompt() + "\n\nYOUR PREVIOUS ACTION WAS INVALID: "
                                + invalidActionFeedback + "\nPlease correct and try again.";

                String modelResponse = callModelForStep(
                        session, skillName, executionConfiguration, modelInteraction, effectivePrompt,
                        new RenderedMissionInput(stepUserMessage, renderedInput.attachments(), renderedInput.traceSafeInput()),
                        stepNumber,
                        promptComposition.traceMetadata(),
                        trustedIdentity,
                        lifecycle);

                StepAction action = parseStepAction(modelResponse, finalResponseOnly);
                if (action == null)
                {
                    executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_ACTION_REJECTED,
                            mergeMetadata(trustedIdentity, Map.of("retry", invalidActionRetryCount, "reason", "Failed to parse model response as StepAction")),
                            Map.of("rawResponse", truncate(modelResponse, 500)));
                    if (invalidActionRetryCount >= MAX_INVALID_ACTION_RETRIES)
                    {
                        IllegalStateException failure = new IllegalStateException(
                                "Model failed to produce a valid step action after %d attempts at step %d for skill '%s'."
                                        .formatted(MAX_INVALID_ACTION_RETRIES + 1, stepNumber, skillName));
                        throw failure;
                    }
                    invalidActionFeedback = "Failed to parse model response as StepAction";
                    invalidActionRetryCount++;
                    continue;
                }
                lastAction = action;

                executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_ACTION_PROPOSED,
                        mergeMetadata(trustedIdentity, Map.of("stepAction", actionName(action),
                                "taskId", action.taskId() == null ? "" : action.taskId(),
                                "toolName", action.toolName() == null ? "" : action.toolName())),
                        Map.of());

                StepValidationResult validation = assignment == null
                        ? StepActionValidator.validateFinal(action, plan)
                        : StepActionValidator.validateAssigned(action, plan, assignment.task(), visibleTools);
                boolean skillValidationRejected = false;
                if (validation.valid() && action.stepAction() == StepActionType.FINAL_RESPONSE)
                {
                    FinalResponseValidationOutcome finalValidation = validateFinalResponseForSkill(
                            session, action, skillDefinition, linterAttempt, outputSchemaAttempt, evidenceAttempt);
                    validation = finalValidation.validation();
                    linterAttempt = finalValidation.nextLinterAttempt();
                    outputSchemaAttempt = finalValidation.nextOutputSchemaAttempt();
                    evidenceAttempt = finalValidation.nextEvidenceAttempt();
                    skillValidationRejected = !validation.valid();
                    if (!validation.valid() && finalValidation.exhausted())
                    {
                        executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_ACTION_REJECTED,
                                mergeMetadata(trustedIdentity, Map.of("reason", validation.rejectionReason(), "exhausted", true)),
                                Map.of("stepAction", actionName(action)));
                        IllegalStateException failure = new IllegalStateException("Final response validation exhausted at step %d for skill '%s': %s"
                                .formatted(stepNumber, skillName, validation.rejectionReason()));
                        throw failure;
                    }
                }
                if (!validation.valid())
                {
                    executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_ACTION_REJECTED,
                            mergeMetadata(trustedIdentity, Map.of("retry", invalidActionRetryCount, "reason", validation.rejectionReason())),
                            Map.of("stepAction", actionName(action)));
                    log.debug("Step {} action rejected (retry {}): {}", stepNumber, invalidActionRetryCount, validation.rejectionReason());
                    if (!skillValidationRejected && invalidActionRetryCount >= MAX_INVALID_ACTION_RETRIES)
                    {
                        IllegalStateException failure = new IllegalStateException("Step action validation exhausted at step %d for skill '%s': %s"
                                .formatted(stepNumber, skillName, validation.rejectionReason()));
                        throw failure;
                    }
                    invalidActionFeedback = validation.rejectionReason();
                    forceVerboseToolArgumentGuidance = true;
                    if (!skillValidationRejected)
                    {
                        invalidActionRetryCount++;
                    }
                    continue;
                }

                executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_ACTION_VALIDATED,
                        mergeMetadata(trustedIdentity, Map.of("stepAction", actionName(action))), Map.of());

                return switch (action.stepAction())
                {
                    case CALL_TOOL -> executeToolAction(session, action, visibleTools, stepFrame, stepNumber, trustedIdentity);
                    case FINAL_RESPONSE -> {
                        String finalResponse = serializeFinalResponse(action.finalResponse());
                        executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_COMPLETED,
                                mergeMetadata(trustedIdentity, Map.of("stepAction", "FINAL_RESPONSE")), Map.of());
                        yield StepResult.finalResponse(finalResponse);
                    }
                };
            }
        }
        catch (RuntimeException | Error ex)
        {
            stepFailure = ex;
            boolean aborted = lifecycle.state() != MissionLifecycle.State.OPEN || Thread.currentThread().isInterrupted();
            stepFrameStatus = aborted ? "aborted" : "failed";
            if (!aborted)
            {
                String failureId = executionStateService.recordFailure(session, ex,
                        Map.of("message", "Step execution failed", "stepNumber", stepNumber, "skillName", skillName));
                LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                metadata.putAll(trustedIdentity);
                metadata.put("failureId", failureId);
                metadata.put("exceptionType", ex.getClass().getName());
                metadata.put("message", truncate(ex.getMessage() == null ? "Step execution failed" : ex.getMessage(), 500));
                if (lastAction != null)
                {
                    metadata.put("stepAction", actionName(lastAction));
                    if (lastAction.taskId() != null) metadata.put("taskId", lastAction.taskId());
                    if (lastAction.toolName() != null) metadata.put("toolName", lastAction.toolName());
                }
                executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_FAILED,
                        metadata, Map.of("failureId", failureId));
            }
            throw ex;
        }
        finally
        {
            executionStateService.closeFrame(session, stepFrame, closeMetadata(stepFrameStatus, stepFailure));
        }
    }

    private String callModelForStep(LoomspanSession session,
            String skillName,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            ModelInteraction modelInteraction,
            String stepPrompt,
            RenderedMissionInput renderedInput,
            int stepNumber,
            Map<String, Object> promptTraceMetadata,
            Map<String, Object> trustedIdentity,
            MissionLifecycle lifecycle)
    {
        ModelExecutionIdentity modelIdentity = ModelExecutionIdentity.from(executionConfiguration);
        ExecutionFrame modelFrame = executionStateService.openFrame(
                session,
                TraceFrameType.MODEL_CALL,
                skillName + "#step-" + stepNumber + "-model",
                modelIdentity.metadata("segment", "step-" + stepNumber));

        String modelFrameStatus = "completed";
        Throwable modelFailure = null;
        try
        {
            ModelTraceContext modelTraceContext = new ModelTraceContext(
                    modelIdentity,
                    skillName,
                    "step-" + stepNumber);

            return modelInteraction.call(new ModelInteractionRequest(
                    stepPrompt, renderedInput, modelTraceContext, List.of(), false)).content();
        }
        catch (RuntimeException | Error ex)
        {
            modelFailure = ex;
            boolean cancelling = lifecycle.state() != MissionLifecycle.State.OPEN;
            modelFrameStatus = cancelling || Thread.currentThread().isInterrupted() ? "aborted" : "failed";
            if (!cancelling)
            {
                executionStateService.recordFailure(session, ex, Map.of("message", "Step model invocation failed"));
            }
            throw ex;
        }
        finally
        {
            executionStateService.closeFrame(session, modelFrame, closeMetadata(modelFrameStatus, modelFailure));
        }
    }

    private Map<String, Object> buildStepTracePayload(String stepPrompt,
            RenderedMissionInput renderedInput,
            Map<String, Object> promptTraceMetadata)
    {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("system", stepPrompt);
        payload.put("user", renderedInput.userText());
        payload.put("attachments", attachmentDescriptors(renderedInput));
        payload.put("attachmentCount", renderedInput.attachments().size());
        payload.putAll(promptTraceMetadata);
        return Map.copyOf(payload);
    }

    private StepResult executeToolAction(LoomspanSession session,
            StepAction action,
            List<BoundCapability> visibleTools,
            ExecutionFrame stepFrame,
            int stepNumber,
            Map<String, Object> trustedIdentity)
    {
        BoundCapability toolCallback = visibleTools.stream()
                .filter(t -> t != null && action.toolName().equals(t.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Tool '%s' validated but not found in visible tools".formatted(action.toolName())));

        String toolResult;
        try
        {
            Object rawResult = toolCallback.invoke(
                    action.toolArguments() == null ? Map.of() : action.toolArguments(), action.taskId());

            toolResult = rawResult == null ? "null" : String.valueOf(rawResult);
        }
        catch (RuntimeException ex)
        {
            RuntimeException unwrapped = unwrapMissionFailure(ex);
            if (unwrapped instanceof LoomspanStackOverflowException || unwrapped instanceof LoomspanMissionTimeoutException)
            {
                throw unwrapped;
            }
            throw unwrapped;
        }

        executionStateService.recordStepEvent(session, stepFrame, TraceRecordType.STEP_COMPLETED,
                mergeMetadata(trustedIdentity, Map.of("stepAction", "CALL_TOOL",
                        "taskId", action.taskId(), "toolName", action.toolName())),
                Map.of("resultPreview", truncate(toolResult, 200)));

        return StepResult.toolExecuted(toolResult);
    }

    @Nullable
    private StepAction parseStepAction(String modelResponse, boolean finalResponseOnly)
    {
        if (modelResponse == null || modelResponse.isBlank())
        {
            return null;
        }
        String unwrapped = unwrapFencedBlock(modelResponse);
        try
        {
            StepAction parsed = objectMapper.readValue(unwrapped, StepAction.class);
            if (finalResponseOnly && (parsed.stepAction() == null || parsed.stepAction() != StepActionType.FINAL_RESPONSE))
            {
                JsonNode node = objectMapper.readTree(unwrapped);
                if (looksLikeBareFinalResponsePayload(node))
                {
                    return StepAction.finalResponse(node);
                }
            }
            return parsed;
        }
        catch (JacksonException ex)
        {
            log.debug("Failed to parse step action JSON: {}", ex.getMessage());
            return null;
        }
    }

    private boolean looksLikeBareFinalResponsePayload(@Nullable JsonNode node)
    {
        return node != null
                && node.isObject()
                && !node.has("stepAction")
                && !node.has("action")
                && !node.has("finalResponse");
    }

    private String unwrapFencedBlock(String payload)
    {
        String safePayload = payload.trim();
        if (safePayload.startsWith("```"))
        {
            int firstNewline = safePayload.indexOf('\n');
            int lastFence = safePayload.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline)
            {
                return safePayload.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return safePayload;
    }

    private void recordTerminalFailure(LoomspanSession session,
            String skillName,
            int stepNumber,
            Throwable failure,
            String message)
    {
        executionStateService.recordFailure(session, failure, Map.of(
                "skillName", skillName,
                "stepNumber", stepNumber,
                "phase", "step-loop",
                "message", message));
    }

    private FinalResponseValidationOutcome validateFinalResponseForSkill(LoomspanSession session,
            StepAction action,
            @Nullable YamlSkillDefinition skillDefinition,
            int linterAttempt,
            int outputSchemaAttempt,
            int evidenceAttempt)
    {
        if (skillDefinition == null)
        {
            return FinalResponseValidationOutcome.ok(linterAttempt, outputSchemaAttempt, evidenceAttempt);
        }

        String finalResponse = serializeFinalResponse(action.finalResponse());
        ValidatorAttemptOutcome outputSchemaValidation = validateOutputSchema(
                session, skillDefinition, finalResponse, outputSchemaAttempt);
        if (!outputSchemaValidation.valid())
        {
            return new FinalResponseValidationOutcome(
                    outputSchemaValidation.validation(),
                    linterAttempt,
                    outputSchemaValidation.nextAttempt(),
                    evidenceAttempt,
                    outputSchemaValidation.exhausted());
        }

        ValidatorAttemptOutcome evidenceValidation = validateEvidence(session, skillDefinition, action.finalResponse(), evidenceAttempt);
        if (!evidenceValidation.valid())
        {
            return new FinalResponseValidationOutcome(
                    evidenceValidation.validation(),
                    linterAttempt,
                    outputSchemaAttempt,
                    evidenceValidation.nextAttempt(),
                    evidenceValidation.exhausted());
        }

        ValidatorAttemptOutcome linterValidation = validateLinter(skillDefinition, finalResponse, linterAttempt);
        return new FinalResponseValidationOutcome(
                linterValidation.validation(),
                linterValidation.nextAttempt(),
                outputSchemaAttempt,
                evidenceAttempt,
                linterValidation.exhausted());
    }

    private ValidatorAttemptOutcome validateLinter(YamlSkillDefinition skillDefinition,
            @Nullable String finalResponse,
            int attempt)
    {
        var linter = skillDefinition.linter();
        if (linter == null || !"regex".equals(linter.getType()) || linter.getRegex() == null)
        {
            return ValidatorAttemptOutcome.passed(attempt);
        }

        int maxRetries = linter.getMaxRetries() == null ? 0 : linter.getMaxRetries();
        boolean matches = finalResponse != null && java.util.regex.Pattern
                .compile(linter.getRegex().getPattern())
                .matcher(finalResponse)
                .matches();
        LinterOutcomeStatus status = matches
                ? LinterOutcomeStatus.PASSED
                : attempt <= maxRetries ? LinterOutcomeStatus.RETRYING : LinterOutcomeStatus.EXHAUSTED;
        String detail = matches
                ? "Final response matched configured regex linter."
                : (linter.getRegex().getMessage() == null || linter.getRegex().getMessage().isBlank()
                        ? "Final response did not match the configured regex linter."
                        : linter.getRegex().getMessage());

        executionStateService.recordLinterOutcome(LoomspanSession.getCurrentSession(), new LinterOutcome(
                skillDefinition.manifest().getName(),
                linter.getType(),
                attempt,
                attempt - 1,
                maxRetries,
                status,
                detail));

        return matches
                ? ValidatorAttemptOutcome.passed(attempt)
                : ValidatorAttemptOutcome.failed(detail, attempt + 1, status == LinterOutcomeStatus.EXHAUSTED);
    }

    private ValidatorAttemptOutcome validateOutputSchema(LoomspanSession session,
            YamlSkillDefinition skillDefinition,
            @Nullable String finalResponse,
            int attempt)
    {
        if (skillDefinition.outputSchema() == null)
        {
            return ValidatorAttemptOutcome.passed(attempt);
        }

        int maxRetries = skillDefinition.outputSchemaMaxRetries();
        OutputSchemaValidationResult result = outputSchemaValidator.validate(finalResponse, skillDefinition.outputSchema());
        OutputSchemaOutcomeStatus status = result.valid()
                ? OutputSchemaOutcomeStatus.PASSED
                : attempt <= maxRetries ? OutputSchemaOutcomeStatus.RETRYING : OutputSchemaOutcomeStatus.EXHAUSTED;

        executionStateService.recordOutputSchemaOutcome(session, new OutputSchemaOutcome(
                skillDefinition.manifest().getName(),
                result.failureMode(),
                attempt,
                attempt - 1,
                maxRetries,
                status,
                result.issues()));

        if (result.valid())
        {
            return ValidatorAttemptOutcome.passed(attempt);
        }

        return ValidatorAttemptOutcome.failed(
                "Final response violates output_schema: " + summarizeOutputSchemaIssues(result.issues()),
                attempt + 1,
                status == OutputSchemaOutcomeStatus.EXHAUSTED);
    }

    private ValidatorAttemptOutcome validateEvidence(LoomspanSession session,
            YamlSkillDefinition skillDefinition,
            @Nullable JsonNode finalResponseNode,
            int attempt)
    {
        if (skillDefinition.evidenceContract().isEmpty())
        {
            return ValidatorAttemptOutcome.passed(attempt);
        }

        int maxRetries = skillDefinition.outputSchemaMaxRetries();
        EvidenceCoverageResult result = evidenceBackedOutputValidator.validate(
                finalResponseNode,
                skillDefinition.evidenceContract(),
                executionStateService.currentSuccessfulSkills());

        executionStateService.recordEvidenceValidation(
                session,
                result.complete(),
                Map.of(
                        "skillName", skillDefinition.manifest().getName(),
                        "claims", result.evaluatedClaims(),
                        "attempt", attempt,
                        "maxRetries", maxRetries),
                result);

        if (result.complete())
        {
            return ValidatorAttemptOutcome.passed(attempt);
        }

        boolean exhausted = attempt > maxRetries;
        if (exhausted)
        {
            return ValidatorAttemptOutcome.failed(
                    "Final response is unsupported by gathered evidence: " + result.retryFeedback(),
                    attempt,
                    true);
        }

        return ValidatorAttemptOutcome.failed(
                "Final response is unsupported by gathered evidence: " + result.retryFeedback(),
                attempt + 1,
                false);
    }

    private String actionName(StepAction action)
    {
        if (action == null || action.stepAction() == null)
        {
            return "";
        }
        return action.stepAction().name();
    }

    private String serializeFinalResponse(@Nullable tools.jackson.databind.JsonNode finalResponseNode)
    {
        if (finalResponseNode == null || finalResponseNode.isNull())
        {
            return "";
        }
        if (finalResponseNode.isTextual())
        {
            return finalResponseNode.asText();
        }
        try
        {
            return objectMapper.writeValueAsString(finalResponseNode);
        }
        catch (JacksonException ex)
        {
            throw new IllegalStateException("Failed to serialize FINAL_RESPONSE payload", ex);
        }
    }

    private String summarizeOutputSchemaIssues(List<OutputSchemaValidationIssue> issues)
    {
        if (issues == null || issues.isEmpty())
        {
            return "unknown schema validation error";
        }

        return issues.stream()
                .limit(3)
                .map(issue ->
                {
                    String field = issue.canonicalField() == null || issue.canonicalField().isBlank()
                            ? issue.path()
                            : issue.canonicalField();
                    return field + ": " + issue.message();
                })
                .reduce((left, right) -> left + "; " + right)
                .orElse("unknown schema validation error");
    }

    private List<Map<String, Object>> attachmentDescriptors(RenderedMissionInput renderedInput)
    {
        return renderedInput.attachments().stream()
                .map(attachment -> attachment.descriptor())
                .toList();
    }

    @Nullable
    private Map<String, Object> planningInput(@Nullable Map<String, Object> originalInput, RenderedMissionInput renderedInput)
    {
        return renderedInput.attachments().isEmpty() ? originalInput : renderedInput.traceSafeInput();
    }

    private static MissionInputMaterializer defaultMaterializer()
    {
        return new DefaultMissionInputMaterializer(new DefaultRefResolver(
                new SessionLocalVirtualFileSystem(Paths.get(System.getProperty("java.io.tmpdir"), "loomspan-vfs"))));
    }

    private void cleanupCancelledMission(LoomspanSession session, MissionContext mission,
            ExecutionBinding coordinatorBinding, MissionLifecycle.Cutoff cutoff)
    {
        RuntimeException[] cleanupFailure = {null};
        cutoff.runIfPermitted(coordinatorBinding, () -> {
            if (!cutoff.claimCleanup()) return;
            Optional<ExecutionPlan> current = mission.currentPlan();
            if (current.isPresent())
            {
                ExecutionPlan plan = current.orElseThrow();
                List<String> joinedTaskIds = new ArrayList<>();
                String joinedGroup = null;
                boolean failedJoin = false;
                for (MissionLifecycle.CutoffTask task : cutoff.tasks())
                {
                    AssignedTaskExecution assignment = task.assignment();
                    PlanTask existing = plan.findTask(assignment.task().taskId()).orElse(null);
                    if (existing == null || existing.status() != PlanTaskStatus.IN_PROGRESS) continue;
                    if (joinedTaskIds.isEmpty()) joinedGroup = existing.parallelGroup();
                    else if (!Objects.equals(joinedGroup, existing.parallelGroup()))
                        throw new IllegalStateException("Cleanup join tasks must share one parallel group");
                    joinedTaskIds.add(existing.taskId());
                    LinterOutcome linterOutcome = task.linterOutcome();
                    OutputSchemaOutcome outputSchemaOutcome = task.outputSchemaOutcome();
                    if (task.outcome() instanceof AssignedTaskOutcome.Success success)
                    {
                        plan = plan.updateTask(existing.taskId(), candidate -> candidate.complete(
                                "Completed tool " + candidate.capabilityName()));
                        try
                        {
                            executionStateService.recordSuccessfulSkillForCleanup(
                                    session, existing.capabilityName(), existing.taskId(), cutoff);
                        }
                        catch (RuntimeException ex) { retainCleanupFailure(cleanupFailure, ex); }
                        mission.appendExecutionSummary("Step %d: Called %s for task %s -> %s".formatted(
                                assignment.stepNumber(), existing.capabilityName(), existing.taskId(),
                                truncate(success.result(), 100)));
                        mission.setLastToolResult(success.result());
                        if (linterOutcome == null) linterOutcome = success.linterOutcome();
                        if (outputSchemaOutcome == null) outputSchemaOutcome = success.outputSchemaOutcome();
                    }
                    else if (task.outcome() instanceof AssignedTaskOutcome.Failure failure)
                    {
                        failedJoin = true;
                        plan = plan.updateTask(existing.taskId(), candidate -> candidate.fail(
                                "Tool " + candidate.capabilityName() + " failed: "
                                        + failure.failure().getClass().getSimpleName()));
                        if (linterOutcome == null) linterOutcome = failure.linterOutcome();
                        if (outputSchemaOutcome == null) outputSchemaOutcome = failure.outputSchemaOutcome();
                    }
                    else
                    {
                        failedJoin = true;
                        plan = plan.updateTask(existing.taskId(), candidate -> candidate.fail(
                                "Owning mission terminated before an outcome became available"));
                    }
                    if (linterOutcome != null) mission.recordLinterOutcome(linterOutcome);
                    if (outputSchemaOutcome != null) mission.recordOutputSchemaOutcome(outputSchemaOutcome);
                }
                try
                {
                    PlanExecutionTransition transition = joinedTaskIds.isEmpty() ? null : PlanExecutionTransition.join(
                            joinedTaskIds, joinedGroup, failedJoin
                                    ? PlanExecutionTransition.JoinOutcome.FAILED
                                    : PlanExecutionTransition.JoinOutcome.COMPLETED);
                    executionStateService.storeAndLogPlanForCleanup(session, plan.withStatus(PlanStatus.STALE), transition, cutoff);
                }
                catch (RuntimeException ex) { retainCleanupFailure(cleanupFailure, ex); }
            }

            for (MissionLifecycle.CutoffTask task : cutoff.tasks())
            {
                if (task.binding() == null) continue;
                drainBranchForCleanup(session, task.binding().branch(), mission, cutoff, cleanupFailure);
            }
            drainBranchForCleanup(session, coordinatorBinding.branch(), mission, cutoff, cleanupFailure);
        });
        if (cleanupFailure[0] != null) throw cleanupFailure[0];
    }

    private void drainBranchForCleanup(LoomspanSession session, PhysicalBranchContext branch,
            MissionContext mission, MissionLifecycle.Cutoff cutoff, RuntimeException[] cleanupFailure)
    {
        for (ExecutionFrame frame : branch.drainForCleanup(cutoff, mission.missionFrameId()))
        {
            try
            {
                executionStateService.closeFrameForCleanup(session, frame,
                        Map.of("status", "aborted", "reason", "mission-cleanup"), cutoff, true);
            }
            catch (RuntimeException ex) { retainCleanupFailure(cleanupFailure, ex); }
        }
    }

    private void cleanupPreservingPrimary(LoomspanSession session, MissionContext mission,
            ExecutionBinding coordinatorBinding, MissionLifecycle.Cutoff cutoff, Throwable primary)
    {
        try { cleanupCancelledMission(session, mission, coordinatorBinding, cutoff); }
        catch (RuntimeException cleanupFailure)
        {
            if (cleanupFailure != primary) primary.addSuppressed(cleanupFailure);
        }
    }

    private static void retainCleanupFailure(RuntimeException[] retained, RuntimeException failure)
    {
        if (retained[0] == null) retained[0] = failure;
        else if (retained[0] != failure) retained[0].addSuppressed(failure);
    }

    private RuntimeException unwrapMissionFailure(RuntimeException runtimeException)
    {
        return runtimeException;
    }

    private Map<String, Object> closeMetadata(String status, @Nullable Throwable failure)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", Thread.currentThread().isInterrupted() ? "aborted" : status);
        if (failure != null)
        {
            TraceFailureMetadata.addTo(metadata, failure, "Step execution failed");
        }
        return metadata;
    }

    private static String truncate(@Nullable String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private record StepResult(@Nullable String finalResponse, @Nullable String toolResult)
    {
        boolean isFinalResponse()
        {
            return finalResponse != null;
        }

        static StepResult finalResponse(String response)
        {
            return new StepResult(response, null);
        }

        static StepResult toolExecuted(String toolResult)
        {
            return new StepResult(null, toolResult);
        }
    }

    private record Admission(ExecutionPlan plan, List<MissionLifecycle.AdmittedTask> entries)
    {
        private Admission
        {
            Objects.requireNonNull(plan, "plan must not be null");
            entries = List.copyOf(entries);
        }
    }

    private record AssignedOutcome(AssignedTaskExecution assignment, AssignedTaskOutcome outcome)
    {
        private AssignedOutcome
        {
            Objects.requireNonNull(assignment, "assignment must not be null");
            Objects.requireNonNull(outcome, "outcome must not be null");
        }
    }

    private record FinalResponseValidationOutcome(StepValidationResult validation,
            int nextLinterAttempt,
            int nextOutputSchemaAttempt,
            int nextEvidenceAttempt,
            boolean exhausted)
    {
        static FinalResponseValidationOutcome ok(int linterAttempt, int outputSchemaAttempt, int evidenceAttempt)
        {
            return new FinalResponseValidationOutcome(
                    StepValidationResult.ok(),
                    linterAttempt,
                    outputSchemaAttempt,
                    evidenceAttempt,
                    false);
        }
    }

    private record ValidatorAttemptOutcome(StepValidationResult validation, int nextAttempt, boolean exhausted)
    {
        boolean valid()
        {
            return validation.valid();
        }

        static ValidatorAttemptOutcome passed(int attempt)
        {
            return new ValidatorAttemptOutcome(StepValidationResult.ok(), attempt, false);
        }

        static ValidatorAttemptOutcome failed(String reason, int nextAttempt, boolean exhausted)
        {
            return new ValidatorAttemptOutcome(StepValidationResult.rejected(reason), nextAttempt, exhausted);
        }
    }
}
