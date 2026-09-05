package ai.loomspan.internal.runtime.planning;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.core.MissionInputMessageFormatter;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceFailureMetadata;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import ai.loomspan.internal.runtime.evidence.EvidenceCoverageResult;
import ai.loomspan.internal.runtime.evidence.EvidenceCoverageValidator;
import ai.loomspan.internal.runtime.prompt.SkillPromptComposer;
import ai.loomspan.internal.runtime.prompt.SkillPromptComposition;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.AllowedSkillConstraint;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionRequest;
import ai.loomspan.internal.model.ModelInteractionResult;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultPlanningService implements PlanningService
{
    private static final Logger log = LoggerFactory.getLogger(DefaultPlanningService.class);

    private static final int MAX_PLANNING_VALIDATION_RETRIES = 1;

    private final ExecutionStateService executionStateService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlObjectMapper;
    private final PlanTaskConstraintValidator planTaskConstraintValidator;
    private final EvidenceCoverageValidator evidenceCoverageValidator;
    private final PlanStructureValidator planStructureValidator = new PlanStructureValidator();
    private final Supplier<String> planIdSupplier;

    public DefaultPlanningService(ExecutionStateService executionStateService)
    {
        this(
                executionStateService,
                defaultObjectMapper(),
                defaultYamlObjectMapper(),
                new PlanTaskConstraintValidator(),
                new EvidenceCoverageValidator(),
                () -> UUID.randomUUID().toString());
    }

    public DefaultPlanningService(ExecutionStateService executionStateService,
            ObjectMapper planningJsonMapper,
            ObjectMapper planningYamlMapper)
    {
        this(executionStateService, planningJsonMapper, planningYamlMapper,
                new PlanTaskConstraintValidator(), new EvidenceCoverageValidator(),
                () -> UUID.randomUUID().toString());
    }

    DefaultPlanningService(ExecutionStateService executionStateService,
            ObjectMapper objectMapper,
            ObjectMapper yamlObjectMapper,
            PlanTaskConstraintValidator planTaskConstraintValidator,
            EvidenceCoverageValidator evidenceCoverageValidator,
            Supplier<String> planIdSupplier)
    {
        this.executionStateService = Objects.requireNonNull(executionStateService, "executionStateService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.yamlObjectMapper = Objects.requireNonNull(yamlObjectMapper, "yamlObjectMapper must not be null");
        this.planTaskConstraintValidator = Objects.requireNonNull(planTaskConstraintValidator, "planTaskConstraintValidator must not be null");
        this.evidenceCoverageValidator = Objects.requireNonNull(evidenceCoverageValidator, "evidenceCoverageValidator must not be null");
        this.planIdSupplier = Objects.requireNonNull(planIdSupplier, "planIdSupplier must not be null");
    }

    @Override
    public Optional<ExecutionPlan> initializePlan(LoomspanSession session,
            String objective,
            @Nullable Map<String, Object> missionInput,
            YamlSkillDefinition definition,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(modelInteraction, "modelInteraction must not be null");
        String capabilityName = definition.manifest().getName();
        var executionConfiguration = definition.requireExecutionConfiguration();

        log.debug(
                "Initializing plan for capability='{}' chatClientType={} visibleTools={}",
                capabilityName,
                modelInteraction.getClass().getName(),
                visibleTools == null ? 0 : visibleTools.size());

        ModelExecutionIdentity modelIdentity = ModelExecutionIdentity.from(executionConfiguration);
        ExecutionFrame planningFrame = executionStateService.openFrame(
                session,
                TraceFrameType.PLANNING,
                capabilityName + "#planning",
                modelIdentity.metadata());

        String planningFrameStatus = "completed";
        Throwable planningFailure = null;
        try
        {
            return initializePlanWithValidation(
                    session,
                    objective,
                    missionInput,
                    definition,
                    modelInteraction,
                    visibleTools,
                    planningFrame);
        }
        catch (RuntimeException | Error ex)
        {
            planningFailure = ex;
            planningFrameStatus = Thread.currentThread().isInterrupted() ? "aborted" : "failed";
            executionStateService.recordFailure(session, ex, Map.of("message", "Planning failed"));
            throw ex;
        }
        finally
        {
            executionStateService.closeFrame(session, planningFrame, closeMetadata(planningFrameStatus, planningFailure));
        }
    }

    @Override
    public Optional<String> markToolStarted(LoomspanSession session, CapabilityMetadata capability)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(capability, "capability must not be null");
        return ExecutionBindingScope.requireCurrent().requireWritable(() ->
                executionStateService.currentPlan()
                        .flatMap(plan -> linkTask(plan, capability)
                                .map(taskId ->
                                {
                                    PlanTask linkedTask = plan.findTask(taskId).orElseThrow();
                                    ExecutionPlan updated = replacePlanTask(
                                            plan,
                                            taskId,
                                            task -> task.bindInProgress("Starting tool " + capability.name()));
                                    executionStateService.storePlan(updated);
                                    executionStateService.logPlanUpdated(session, updated,
                                            linkedTask.status() == PlanTaskStatus.PENDING
                                                    ? PlanExecutionTransition.admission(
                                                            List.of(taskId), linkedTask.parallelGroup(), false)
                                                    : null);
                                    return taskId;
                                })));
    }

    @Override
    public Optional<ExecutionPlan> markToolCompleted(LoomspanSession session,
            String taskId,
            String capabilityName)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(capabilityName, "capabilityName must not be null");

        return ExecutionBindingScope.requireCurrent().requireWritable(() -> {
            Optional<ExecutionPlan> updatedPlan = executionStateService.currentPlan()
                    .flatMap(plan -> plan.findTask(taskId)
                            .map(task ->
                            {
                                requireBoundCapability(task, capabilityName);
                                ExecutionPlan updated = replacePlanTask(
                                        plan,
                                        taskId,
                                        current -> current.complete("Completed tool " + capabilityName));
                                executionStateService.storePlan(updated);
                                executionStateService.logPlanUpdated(session, updated,
                                        PlanExecutionTransition.join(List.of(taskId), task.parallelGroup(),
                                                PlanExecutionTransition.JoinOutcome.COMPLETED));
                                return updated;
                            }))
                    .filter(plan -> plan.findTask(taskId)
                            .map(task -> task.status() == PlanTaskStatus.COMPLETED)
                            .orElse(false));

            if (updatedPlan.isPresent())
            {
                executionStateService.recordSuccessfulSkill(capabilityName, taskId, false);
            }
            return updatedPlan;
        });
    }

    @Override
    public Optional<ExecutionPlan> markToolFailed(LoomspanSession session,
            String taskId,
            String capabilityName,
            RuntimeException ex)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(capabilityName, "capabilityName must not be null");
        Objects.requireNonNull(ex, "ex must not be null");

        return ExecutionBindingScope.requireCurrent().requireWritable(() ->
                executionStateService.currentPlan()
                        .flatMap(plan -> plan.findTask(taskId)
                                .map(task ->
                                {
                                    requireBoundCapability(task, capabilityName);
                                    ExecutionPlan updated = replacePlanTask(
                                            plan,
                                            taskId,
                                            current -> current.fail("Tool " + capabilityName + " failed: " + ex.getClass().getSimpleName()))
                                            .withStatus(PlanStatus.STALE);
                                    executionStateService.storePlan(updated);
                                    executionStateService.logPlanUpdated(session, updated,
                                            PlanExecutionTransition.join(List.of(taskId), task.parallelGroup(),
                                                    PlanExecutionTransition.JoinOutcome.FAILED));
                                    return updated;
                                })));
    }

    private Optional<String> linkTask(ExecutionPlan plan, CapabilityMetadata capability)
    {
        List<PlanTask> inProgress = plan.tasks().stream()
                .filter(task -> task.status() == PlanTaskStatus.IN_PROGRESS)
                .filter(task -> capability.name().equals(task.capabilityName()))
                .toList();
        if (inProgress.size() == 1)
        {
            return Optional.of(inProgress.getFirst().taskId());
        }
        if (!inProgress.isEmpty())
        {
            return Optional.empty();
        }

        List<PlanTask> ready = plan.readyTasks().stream()
                .filter(task -> capability.name().equals(task.capabilityName()))
                .toList();
        return ready.size() == 1 ? Optional.of(ready.getFirst().taskId()) : Optional.empty();
    }

    private Optional<ExecutionPlan> initializePlanWithValidation(LoomspanSession session,
            String objective,
            @Nullable Map<String, Object> missionInput,
            YamlSkillDefinition definition,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            ExecutionFrame planningFrame)
    {
        String capabilityName = definition.manifest().getName();
        var executionConfiguration = definition.requireExecutionConfiguration();
        EvidenceContract evidenceContract = definition.evidenceContract();
        List<AllowedSkillConstraint> taskConstraints = definition.allowedSkillConstraints();
        validateRequiredChildrenVisible(capabilityName, taskConstraints, visibleTools);
        String retryFeedback = null;
        int retryCount = 0;
        ModelTraceContext modelTraceContext = new ModelTraceContext(
                ModelExecutionIdentity.from(executionConfiguration),
                capabilityName,
                "planning");

        while (true)
        {
            PlanningAttemptResult attemptResult = requestPlanAttempt(
                    session,
                    objective,
                    missionInput,
                    definition,
                    executionConfiguration,
                    modelInteraction,
                    visibleTools,
                    retryFeedback,
                    evidenceContract,
                    modelTraceContext);

            Set<String> visibleCapabilityNames = visibleTools == null ? Set.of() : visibleTools.stream()
                    .filter(Objects::nonNull)
                    .map(BoundCapability::name)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            PlanStructureValidationResult structureValidation = planStructureValidator.validate(
                    attemptResult.planTree(), visibleCapabilityNames);
            if (structureValidation.hasErrors())
            {
                recordPlanStructureEvent(session, planningFrame, TraceRecordType.PLAN_VALIDATION_FAILED,
                        structureValidation.issues(), retryCount, attemptResult.modelAttempt());
                if (retryCount < MAX_PLANNING_VALIDATION_RETRIES)
                {
                    retryFeedback = structureValidation.retryFeedback();
                    recordPlanStructureEvent(session, planningFrame, TraceRecordType.PLAN_RETRY_REQUESTED,
                            structureValidation.issues(), retryCount, attemptResult.modelAttempt());
                    retryCount++;
                    continue;
                }
                throw new IllegalStateException(
                        "Plan validation failed for skill '%s': %s"
                                .formatted(capabilityName, structureValidation.retryFeedback()));
            }

            ExecutionPlan plan = convertPlan(attemptResult.planTree(), capabilityName);

            PlanTaskConstraintValidationResult validation = planTaskConstraintValidator.validate(
                    plan, taskConstraints);
            EvidenceCoverageResult evidenceCoverage = evidenceCoverageValidator.validatePlanCoverage(
                    plan,
                    evidenceContract);

            boolean hasDeterministicEvidenceGap = !evidenceCoverage.complete();
            if ((validation.hasErrors() || hasDeterministicEvidenceGap) && retryCount < MAX_PLANNING_VALIDATION_RETRIES)
            {
                recordPlanTaskConstraintEvent(session, planningFrame, TraceRecordType.PLAN_VALIDATION_FAILED,
                        validation.issues(), retryCount, attemptResult.modelAttempt());
                if (hasDeterministicEvidenceGap)
                {
                    recordEvidenceCoverageEvent(session, planningFrame, TraceRecordType.PLAN_VALIDATION_FAILED,
                            evidenceCoverage, retryCount, attemptResult.modelAttempt());
                }

                retryFeedback = mergeRetryFeedback(validation.retryFeedback(), evidenceCoverage.retryFeedback());
                recordPlanTaskConstraintEvent(session, planningFrame, TraceRecordType.PLAN_RETRY_REQUESTED,
                        validation.issues(), retryCount, attemptResult.modelAttempt());
                if (hasDeterministicEvidenceGap)
                {
                    recordEvidenceCoverageEvent(session, planningFrame, TraceRecordType.PLAN_RETRY_REQUESTED,
                            evidenceCoverage, retryCount, attemptResult.modelAttempt());
                }

                retryCount++;
                continue;
            }

            if (validation.hasErrors() || hasDeterministicEvidenceGap)
            {
                recordPlanTaskConstraintEvent(session, planningFrame, TraceRecordType.PLAN_VALIDATION_FAILED,
                        validation.issues(), retryCount, attemptResult.modelAttempt());
                if (hasDeterministicEvidenceGap)
                {
                    recordEvidenceCoverageEvent(session, planningFrame, TraceRecordType.PLAN_VALIDATION_FAILED,
                            evidenceCoverage, retryCount, attemptResult.modelAttempt());
                }
                throw new IllegalStateException(
                        "Plan validation failed for skill '%s': %s"
                                .formatted(capabilityName,
                                        mergeRetryFeedback(validation.retryFeedback(), evidenceCoverage.retryFeedback())));
            }

            Map<String, Object> acceptedAttempt = requireAcceptedAttempt(attemptResult.modelAttempt());
            return ExecutionBindingScope.requireCurrent().requireWritable(() -> {
                executionStateService.storePlan(plan);
                executionStateService.logPlanCreated(session, plan, acceptedAttempt);
                return Optional.of(plan);
            });
        }
    }

    private void recordEvidenceCoverageEvent(LoomspanSession session,
            ExecutionFrame planningFrame,
            TraceRecordType recordType,
            EvidenceCoverageResult coverage,
            int retryCount,
            Map<String, Object> modelAttempt)
    {
        if (coverage == null || coverage.complete())
        {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retryCount", retryCount);
        metadata.put("validationStatus", planningValidationStatus(recordType, retryCount));
        metadata.put("issueCodes", List.of("evidence-coverage"));
        metadata.put("severity", "ERROR");
        metadata.put("claims", coverage.evaluatedClaims());
        metadata.put("unsatisfiedClaims", coverage.issues().stream().map(ai.loomspan.internal.runtime.evidence.EvidenceCoverageIssue::claimName).toList());
        metadata.put("requiredExpressions", coverage.requiredExpressions());
        metadata.put("satisfiedSkills", coverage.satisfiedSkills());
        metadata.put("unsatisfiedRequirements", coverage.issues().stream()
                .flatMap(issue -> issue.unsatisfiedRequirements().stream())
                .toList());
        metadata.putAll(modelAttempt);

        executionStateService.recordPlanningEvent(session, planningFrame, recordType, metadata, coverage.issues());
    }

    private void validateRequiredChildrenVisible(String capabilityName,
            List<AllowedSkillConstraint> constraints,
            List<BoundCapability> visibleTools)
    {
        Set<String> visibleNames = visibleTools == null ? Set.of() : visibleTools.stream()
                .filter(Objects::nonNull)
                .map(BoundCapability::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<AllowedSkillConstraint> unavailable = constraints.stream()
                .filter(AllowedSkillConstraint::hasMinimumConstraint)
                .filter(constraint -> !visibleNames.contains(constraint.name()))
                .toList();
        if (!unavailable.isEmpty())
        {
            String details = unavailable.stream()
                    .map(constraint -> "'%s' (minimum %d)".formatted(
                            constraint.name(), constraint.effectiveMinTasks()))
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Planning skill '%s' cannot satisfy required task constraints because "
                    .formatted(capabilityName) + details + " are not visible for this execution");
        }
    }

    private PlanningAttemptResult requestPlanAttempt(LoomspanSession session,
            String objective,
            @Nullable Map<String, Object> missionInput,
            YamlSkillDefinition definition,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            @Nullable String retryFeedback,
            @Nullable EvidenceContract evidenceContract,
            ModelTraceContext modelTraceContext)
    {
        String capabilityName = definition.manifest().getName();
        ModelExecutionIdentity modelIdentity = ModelExecutionIdentity.from(executionConfiguration);
        ExecutionFrame modelFrame = executionStateService.openFrame(
                session,
                TraceFrameType.MODEL_CALL,
                capabilityName + "#planning-model",
                modelIdentity.metadata("segment", "planning"));

        String modelFrameStatus = "completed";
        Throwable modelFailure = null;
        SkillPromptComposition promptComposition = SkillPromptComposer.composePlanningPrompt(
                definition,
                buildPlanningPrompt(capabilityName, visibleTools, retryFeedback, evidenceContract,
                        definition.allowedSkillConstraints()));
        String planningPrompt = promptComposition.systemPrompt();
        String planningUserMessage = MissionInputMessageFormatter.buildUserMessage(objective, missionInput);

        try
        {
            ModelInteractionResult result = modelInteraction.call(new ModelInteractionRequest(
                    planningPrompt,
                    new RenderedMissionInput(planningUserMessage, List.of(), Map.of()),
                    modelTraceContext,
                    List.of(),
                    true));
            String planPayload = result.content();
            Map<String, Object> modelAttempt = ModelTraceContext.attemptFrom(result.context());

            return new PlanningAttemptResult(
                    parseNormalizedPlanTree(planPayload, capabilityName),
                    planningPrompt,
                    planningUserMessage,
                    modelAttempt);
        }
        catch (RuntimeException | Error ex)
        {
            modelFailure = ex;
            modelFrameStatus = Thread.currentThread().isInterrupted() ? "aborted" : "failed";
            executionStateService.recordFailure(session, ex, Map.of("message", "Planning model invocation failed"));
            throw ex;
        }
        finally
        {
            executionStateService.closeFrame(session, modelFrame, closeMetadata(modelFrameStatus, modelFailure));
        }
    }

    private Map<String, Object> buildPlanningTracePayload(SkillPromptComposition composition, String userMessage)
    {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("system", composition.systemPrompt());
        payload.put("user", userMessage);
        payload.putAll(composition.traceMetadata());
        return Map.copyOf(payload);
    }

    private void recordPlanTaskConstraintEvent(LoomspanSession session,
            ExecutionFrame planningFrame,
            TraceRecordType recordType,
            List<PlanTaskConstraintIssue> issues,
            int retryCount,
            Map<String, Object> modelAttempt)
    {
        if (issues == null || issues.isEmpty())
        {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retryCount", retryCount);
        metadata.put("validationStatus", planningValidationStatus(recordType, retryCount));
        metadata.put("severity", "ERROR");
        metadata.put("issueCodes", issues.stream().map(PlanTaskConstraintIssue::code).distinct().toList());
        metadata.putAll(modelAttempt);
        List<Map<String, Object>> payload = issues.stream()
                .map(DefaultPlanningService::constraintIssuePayload)
                .toList();

        executionStateService.recordPlanningEvent(session, planningFrame, recordType, metadata, payload);
    }

    private void recordPlanStructureEvent(LoomspanSession session,
            ExecutionFrame planningFrame,
            TraceRecordType recordType,
            List<PlanStructureIssue> issues,
            int retryCount,
            Map<String, Object> modelAttempt)
    {
        if (issues == null || issues.isEmpty())
        {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retryCount", retryCount);
        metadata.put("validationStatus", planningValidationStatus(recordType, retryCount));
        metadata.put("severity", "ERROR");
        metadata.put("issueCodes", issues.stream().map(PlanStructureIssue::code).distinct().toList());
        metadata.putAll(modelAttempt);
        List<Map<String, Object>> payload = issues.stream().map(issue -> Map.<String, Object>of(
                "code", issue.code(),
                "message", issue.message(),
                "severity", "ERROR")).toList();
        executionStateService.recordPlanningEvent(session, planningFrame, recordType, metadata, payload);
    }

    private static String planningValidationStatus(TraceRecordType recordType, int retryCount)
    {
        return recordType == TraceRecordType.PLAN_RETRY_REQUESTED || retryCount < MAX_PLANNING_VALIDATION_RETRIES
                ? "retrying"
                : "exhausted";
    }

    private static Map<String, Object> constraintIssuePayload(PlanTaskConstraintIssue issue)
    {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", issue.code());
        payload.put("skillName", issue.skillName());
        payload.put("configuredMinTasks", issue.configuredMinTasks());
        payload.put("effectiveMinTasks", issue.effectiveMinTasks());
        payload.put("maxTasks", issue.maxTasks());
        payload.put("actualTaskCount", issue.actualTaskCount());
        payload.put("severity", "ERROR");
        payload.put("message", issue.message());
        return Collections.unmodifiableMap(payload);
    }

    private static String buildPlanningPrompt(String capabilityName,
            List<BoundCapability> visibleTools,
            @Nullable String retryFeedback,
            @Nullable EvidenceContract evidenceContract,
            List<AllowedSkillConstraint> taskConstraints)
    {
        String toolList = (visibleTools == null || visibleTools.isEmpty())
                ? "(none)"
                : visibleTools.stream()
                        .filter(Objects::nonNull)
                        .map(DefaultPlanningService::describeTool)
                        .collect(Collectors.joining("\n"));

        String retrySection = retryFeedback == null || retryFeedback.isBlank()
                ? ""
                : """

                        Previous plan was too weak. Correct these issues in the next plan:
                        %s
                        """.formatted(retryFeedback);

        String evidenceConstraints = "";
        if (evidenceContract != null && !evidenceContract.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            List<String> sortedClaims = new ArrayList<>(evidenceContract.claims());
            Collections.sort(sortedClaims);

            for (String claim : sortedClaims)
            {
                builder.append("- The '")
                        .append(claim)
                        .append("' output field requires tasks whose exact capability names satisfy: ")
                        .append(evidenceContract.canonicalExpressionForClaim(claim))
                        .append(". For an 'or' group, include any one alternative; for an 'and' group, include every requirement.\n");
            }

            if (!builder.isEmpty())
            {
                evidenceConstraints = "Evidence Constraints:\n" + builder.toString();
            }
        }

        String taskCountConstraints = renderTaskCountConstraints(taskConstraints);

        return """
                Create an ordered flight plan for this mission before execution.
                Return ONLY valid JSON - no markdown, no explanation, no code fences.
                The JSON must match this exact structure:
                {
                  "capabilityName": "%s",
                  "createdAt": "<ISO-8601 timestamp, e.g. 2024-01-01T00:00:00Z>",
                  "status": "VALID",
                  "tasks": [
                    {
                      "taskId": "<unique string>",
                      "title": "<short title>",
                      "status": "PENDING",
                      "capabilityName": "<one of the available sub-skills listed below>",
                      "intent": "<what this task must accomplish>",
                      "dependsOn": [],
                      "expectedOutputs": ["<output description>"],
                      "parallelGroup": null,
                      "note": "<optional note or empty string>"
                    }
                  ]
                }
                Available sub-skills (use these exact names for task capabilityName):
                %s
                Constraints:
                - plan status must be exactly: VALID
                - task status must be exactly: PENDING
                - dependsOn must be a JSON array of taskId strings (empty array if no dependencies)
                - expectedOutputs must be a JSON array of strings
                - parallelGroup may be omitted or null for an ungrouped task; otherwise it must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$ exactly
                - Return raw JSON only - no additional text before or after
                Planning rules:
                - Each task must have a distinct purpose that advances the mission.
                - Bind each task to the tool that best matches that task's intent.
                - List order defines execution-unit order. Consecutive tasks with the same non-null parallelGroup form one unit; every other task is a singleton unit.
                - Only consecutive tasks in the same non-null parallelGroup may overlap. The runtime joins the complete unit before starting the next unit.
                - A non-null parallelGroup is a positive assertion that every member is safe to run at the same time and does not require another member's result.
                - Leave tasks ungrouped whenever ordering, shared-result dependence, or safe overlap is uncertain.
                - dependsOn expresses explicit causal or dataflow requirements and may reference only tasks in earlier execution units.
                - Gather enough evidence to support the final answer before the mission is complete.
                %s%s%s""".formatted(capabilityName, toolList, taskCountConstraints, evidenceConstraints, retrySection);
    }

    private static String renderTaskCountConstraints(List<AllowedSkillConstraint> constraints)
    {
        StringBuilder builder = new StringBuilder();
        for (AllowedSkillConstraint constraint : constraints == null ? List.<AllowedSkillConstraint>of() : constraints)
        {
            if (!constraint.hasMinimumConstraint() && !constraint.hasMaximumConstraint())
            {
                continue;
            }
            builder.append("- ").append(constraint.name()).append(": ");
            if (constraint.hasMinimumConstraint())
            {
                builder.append("use in at least ").append(constraint.effectiveMinTasks()).append(" plan task(s)");
            }
            if (constraint.hasMinimumConstraint() && constraint.hasMaximumConstraint())
            {
                builder.append(" and ");
            }
            if (constraint.hasMaximumConstraint())
            {
                builder.append("use in at most ").append(constraint.maxTasks()).append(" plan task(s)");
            }
            builder.append(".\n");
        }
        return builder.isEmpty() ? "" : "Plan Task Count Constraints:\n" + builder;
    }

    private String mergeRetryFeedback(@Nullable String qualityFeedback, @Nullable String evidenceFeedback)
    {
        if ((qualityFeedback == null || qualityFeedback.isBlank())
                && (evidenceFeedback == null || evidenceFeedback.isBlank()))
        {
            return null;
        }
        if (qualityFeedback == null || qualityFeedback.isBlank())
        {
            return evidenceFeedback;
        }
        if (evidenceFeedback == null || evidenceFeedback.isBlank())
        {
            return qualityFeedback;
        }
        return qualityFeedback + "\n" + evidenceFeedback;
    }

    private static String describeTool(BoundCapability callback)
    {
        String description = callback.description();

        if (description == null || description.isBlank())
        {
            description = "No description provided.";
        }

        return "- %s: %s".formatted(callback.name(), description);
    }

    private Map<String, Object> closeMetadata(String status, @Nullable Throwable failure)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", Thread.currentThread().isInterrupted() ? "aborted" : status);

        if (failure != null)
        {
            TraceFailureMetadata.addTo(metadata, failure, "Planning model invocation failed");
        }

        return metadata;
    }

    private ExecutionPlan replacePlanTask(ExecutionPlan plan, String taskId, Function<PlanTask, PlanTask> updater)
    {
        return plan.updateTask(taskId, updater);
    }

    private JsonNode parseNormalizedPlanTree(String payload, String capabilityName)
    {
        String unwrapped = unwrapFencedBlock(payload);
        log.debug(
                "Parsing plan for capability='{}' looksLikeJson={} payloadPreview={}...",
                capabilityName,
                looksLikeJson(unwrapped),
                preview(unwrapped));

        try
        {
            JsonNode tree = parsePlanTree(unwrapped, capabilityName);
            normalizePlanTree(tree);
            return tree;
        }
        catch (JacksonException ex)
        {
            throw new IllegalStateException(
                    "Failed to parse planning response for capability '" + capabilityName
                            + "' as JSON or YAML. Payload preview: " + preview(unwrapped),
                    ex);
        }
    }

    private ExecutionPlan convertPlan(JsonNode tree, String capabilityName)
    {
        try
        {
            return objectMapper.treeToValue(tree, ExecutionPlan.class);
        }
        catch (JacksonException ex)
        {
            throw new IllegalStateException(
                    "Failed to parse planning response for capability '" + capabilityName + "' as JSON or YAML.", ex);
        }
    }

    private void requireBoundCapability(PlanTask task, String capabilityName)
    {
        if (!Objects.equals(task.capabilityName(), capabilityName))
        {
            throw new IllegalStateException(
                    "Task '%s' is bound to capability '%s' but received '%s'."
                            .formatted(task.taskId(), task.capabilityName(), capabilityName));
        }
    }

    private JsonNode parsePlanTree(String payload, String capabilityName) throws JacksonException
    {
        if (looksLikeJson(payload))
        {
            try
            {
                return objectMapper.readTree(payload);
            }
            catch (JacksonException ex)
            {
                log.debug("JSON plan parsing failed for capability='{}'; trying YAML tree parsing", capabilityName, ex);
            }
        }

        return yamlObjectMapper.readTree(payload);
    }

    private void normalizePlanTree(JsonNode tree)
    {
        if (!(tree instanceof ObjectNode planNode))
        {
            return;
        }

        normalizePlanStatus(planNode);
        normalizeTaskStatuses(planNode.get("tasks"));
        planNode.remove("planId");
        planNode.put("planId", requireNonBlank(planIdSupplier.get(), "planId"));
    }

    private Map<String, Object> requireAcceptedAttempt(Map<String, Object> modelAttempt)
    {
        if (modelAttempt == null || modelAttempt.isEmpty())
        {
            throw new IllegalStateException("Accepted planning response must include model attempt context");
        }
        return Map.of(
                "attemptId", requireNonBlank((String) modelAttempt.get("attemptId"), "attemptId"),
                "retrySequenceId", requireNonBlank((String) modelAttempt.get("retrySequenceId"), "retrySequenceId"));
    }

    private String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private void normalizePlanStatus(ObjectNode planNode)
    {
        JsonNode statusNode = planNode.get("status");
        if (statusNode == null || !statusNode.isTextual())
        {
            return;
        }

        String normalizedStatus = normalizePlanStatusValue(statusNode.asText());
        if (normalizedStatus != null)
        {
            planNode.put("status", normalizedStatus);
        }
    }

    private void normalizeTaskStatuses(@Nullable JsonNode tasksNode)
    {
        if (!(tasksNode instanceof ArrayNode taskArray))
        {
            return;
        }
        for (JsonNode taskNode : taskArray)
        {
            if (!(taskNode instanceof ObjectNode objectTaskNode))
            {
                continue;
            }
            JsonNode statusNode = objectTaskNode.get("status");
            if (statusNode == null || !statusNode.isTextual())
            {
                continue;
            }
            String normalizedStatus = normalizeTaskStatusValue(statusNode.asText());
            if (normalizedStatus != null)
            {
                objectTaskNode.put("status", normalizedStatus);
            }
        }
    }

    @Nullable
    private String normalizePlanStatusValue(String rawStatus)
    {
        String normalized = canonicalizeEnumToken(rawStatus);
        return switch (normalized)
        {
            case "VALID", "STALE", "INVALID" -> normalized;
            case "EXECUTED", "EXECUTING", "COMPLETED", "COMPLETE", "SUCCESS", "SUCCEEDED", "DONE", "READY", "PENDING", "IN_PROGRESS", "INPROGRESS", "ACTIVE", "RUNNING", "OPEN", "NEW", "CURRENT", "ONGOING", "STARTED" -> PlanStatus.VALID.name();
            case "FAILED", "FAILURE", "ERROR", "BLOCKED" -> PlanStatus.INVALID.name();
            default -> null;
        };
    }

    @Nullable
    private String normalizeTaskStatusValue(String rawStatus)
    {
        String normalized = canonicalizeEnumToken(rawStatus);
        return switch (normalized)
        {
            case "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED" -> normalized;
            case "SUCCESS", "SUCCEEDED", "DONE", "COMPLETE", "EXECUTED" -> PlanTaskStatus.COMPLETED.name();
            case "RUNNING", "ACTIVE", "EXECUTING", "INPROGRESS" -> PlanTaskStatus.IN_PROGRESS.name();
            case "WAITING", "READY", "TODO", "NEW", "OPEN", "QUEUED", "NOT_STARTED" -> PlanTaskStatus.PENDING.name();
            case "FAILURE", "ERROR", "INVALID", "STALE" -> PlanTaskStatus.FAILED.name();
            default -> null;
        };
    }

    private String canonicalizeEnumToken(String rawStatus)
    {
        return rawStatus == null
                ? ""
                : rawStatus.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String unwrapFencedBlock(String payload)
    {
        String safePayload = payload == null ? "" : payload.trim();
        if (safePayload.startsWith("```"))
        {
            int firstNewline = safePayload.indexOf('\n');
            int lastFence = safePayload.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline)
            {
                return safePayload.substring(firstNewline + 1, lastFence).trim();
            }
        }

        if (safePayload.startsWith("---"))
        {
            safePayload = safePayload.substring(3).trim();
        }

        return safePayload;
    }

    private boolean looksLikeJson(String payload)
    {
        return payload.startsWith("{") || payload.startsWith("[");
    }

    private String preview(String payload)
    {
        if (payload == null || payload.isBlank())
        {
            return "<empty>";
        }

        String normalized = payload.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private static ObjectMapper defaultObjectMapper()
    {
        return ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().planningJson();
    }

    private static ObjectMapper defaultYamlObjectMapper()
    {
        return ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().planningYaml();
    }

    private String stringifyPlan(ExecutionPlan plan)
    {
        return plan == null ? "" : plan.toString();
    }

    private record PlanningAttemptResult(JsonNode planTree,
            String prompt,
            String userMessage,
            Map<String, Object> modelAttempt)
    {
    }
}
