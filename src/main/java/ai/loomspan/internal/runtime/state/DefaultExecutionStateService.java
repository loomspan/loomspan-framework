package ai.loomspan.internal.runtime.state;

import ai.loomspan.internal.core.AdvisorTraceContext;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.core.MissionLifecycle;
import ai.loomspan.internal.core.PhysicalBranchContext;
import ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.DefaultExecutionTraceRecorder;
import ai.loomspan.internal.core.ExecutionTraceRecorder;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.OperationType;
import ai.loomspan.internal.core.TaskExecutionEvent;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.core.TraceCompletion;
import ai.loomspan.internal.core.ToolTraceContext;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.runtime.usage.ModelUsageRecord;
import ai.loomspan.internal.runtime.usage.SessionUsageService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;

public class DefaultExecutionStateService implements ExecutionStateService
{
    private final Clock clock;
    private final SessionUsageService sessionUsageService;
    // Runtime observability flows through one recorder boundary so feature code does not invent parallel trace semantics.
    private final ExecutionTraceRecorder traceRecorder;

    public DefaultExecutionStateService(Clock clock)
    {
        this(clock, new NoOpSessionUsageService());
    }

    public DefaultExecutionStateService(Clock clock, SessionUsageService sessionUsageService)
    {
        this(clock, sessionUsageService, new DefaultExecutionTraceRecorder(clock));
    }

    public DefaultExecutionStateService(Clock clock,
            SessionUsageService sessionUsageService,
            ExecutionTraceRecorder traceRecorder)
    {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.sessionUsageService = Objects.requireNonNull(sessionUsageService, "sessionUsageService must not be null");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
    }

    @Override
    public ExecutionFrame openMissionFrame(LoomspanSession session, String route, Map<String, Object> parameters)
    {
        return openFrame(session, TraceFrameType.ROOT_MISSION, route, parameters);
    }

    @Override
    public ExecutionFrame openFrame(LoomspanSession session, TraceFrameType traceFrameType, String route, Map<String, Object> parameters)
    {
        ExecutionBinding binding = requireBinding(session);
        return binding.requireWritable(() -> openFrameWritable(session, traceFrameType, route, parameters, binding));
    }

    private ExecutionFrame openFrameWritable(LoomspanSession session, TraceFrameType traceFrameType, String route,
            Map<String, Object> parameters, ExecutionBinding binding)
    {
        PhysicalBranchContext branch = binding.branch();
        Instant now = clock.instant();
        ExecutionFrame frame = new ExecutionFrame(
                traceFrameType == TraceFrameType.ROOT_MISSION
                        ? binding.requireMission().missionFrameId()
                        : UUID.randomUUID().toString(),
                branch.leaf().map(ExecutionFrame::frameId).orElse(null),
                mapOperationType(traceFrameType),
                traceFrameType,
                route,
                parameters == null ? Map.of() : Map.copyOf(parameters),
                now);

        branch.push(frame);
        try
        {
            traceRecorder.recordFrameOpened(session, frame);
        }
        catch (RuntimeException | Error ex)
        {
            branch.rollback(frame);
            throw ex;
        }
        return frame;
    }

    @Override
    public void closeMissionFrame(LoomspanSession session, ExecutionFrame frame)
    {
        closeFrame(session, frame, Map.of());
    }

    @Override
    public void closeFrame(LoomspanSession session, ExecutionFrame frame, Map<String, Object> metadata)
    {
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> closeFrameWritable(session, frame, metadata, binding));
    }

    private void closeFrameWritable(LoomspanSession session, ExecutionFrame frame, Map<String, Object> metadata,
            ExecutionBinding binding)
    {
        Objects.requireNonNull(frame, "frame must not be null");
        PhysicalBranchContext branch = binding.branch();
        List<ExecutionFrame> frames = branch.leafToRootSnapshot();

        if (frames.isEmpty())
        {
            return;
        }
        if (!branch.owns(frame))
        {
            return;
        }

        ExecutionFrame activeFrame;
        try
        {
            activeFrame = branch.requireLeaf();
        }
        catch (IllegalStateException ex)
        {
            return;
        }

        if (!activeFrame.equals(frame))
        {
            throw new IllegalStateException("Attempted to close execution frame '%s' but active frame was '%s'."
                    .formatted(frame.frameId(), activeFrame.frameId()));
        }

        traceRecorder.recordFrameClosed(session, frame, metadata == null ? Map.of() : Map.copyOf(metadata));
        try
        {
            branch.close(frame);
            session.clearToolActivity(frame.frameId());
        }
        catch (IllegalStateException ex)
        {
            if (!branch.contains(frame))
            {
                return;
            }
            throw ex;
        }
    }

    @Override
    public void closeFrameForCleanup(LoomspanSession session, ExecutionFrame frame, Map<String, Object> metadata,
            MissionLifecycle.Cutoff cutoff, boolean alreadyDrained)
    {
        ExecutionBinding binding = requireBinding(session);
        requireCleanupAuthority(binding, cutoff);
        Objects.requireNonNull(frame, "frame must not be null");
        cutoff.runIfPermitted(binding, () -> {
            RuntimeException traceFailure = null;
            try
            {
                session.appendTraceRecord(TraceRecordType.FRAME_CLOSED, frame,
                        metadata == null ? Map.of() : Map.copyOf(metadata), Map.of(
                                "frameId", frame.frameId(), "route", frame.route(), "closedAt", clock.instant().toString()));
            }
            catch (RuntimeException ex) { traceFailure = ex; }
            try
            {
                if (!alreadyDrained) binding.branch().close(frame);
            }
            catch (RuntimeException closeFailure)
            {
                if (traceFailure != null && traceFailure != closeFailure) closeFailure.addSuppressed(traceFailure);
                throw closeFailure;
            }
            finally { session.clearToolActivity(frame.frameId()); }
            if (traceFailure != null) throw traceFailure;
        });
    }

    @Override
    public void storePlan(ExecutionPlan plan)
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        binding.runIfWritable(() -> binding.requireMission().storePlan(Objects.requireNonNull(plan, "plan must not be null")));
    }

    @Override
    public void clearPlan()
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        binding.runIfWritable(binding.requireMission()::clearPlan);
    }

    @Override
    public Optional<ExecutionPlan> currentPlan()
    {
        return requireMission().currentPlan();
    }

    @Override
    public void logPlanCreated(LoomspanSession session, ExecutionPlan plan, Map<String, Object> acceptedAttempt)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.recordPlanCreated(
                session,
                Objects.requireNonNull(plan, "plan must not be null"),
                Objects.requireNonNull(acceptedAttempt, "acceptedAttempt must not be null"));
    }

    @Override
    public void logPlanUpdated(LoomspanSession session, ExecutionPlan plan, PlanExecutionTransition transition)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.recordPlanUpdated(session, Objects.requireNonNull(plan, "plan must not be null"), transition);
    }

    @Override
    public void storeAndLogPlanForCleanup(LoomspanSession session, ExecutionPlan plan,
            PlanExecutionTransition transition, MissionLifecycle.Cutoff cutoff)
    {
        ExecutionBinding binding = requireBinding(session);
        requireCleanupAuthority(binding, cutoff);
        cutoff.runIfPermitted(binding, () -> {
            ExecutionPlan safePlan = Objects.requireNonNull(plan, "plan must not be null");
            binding.requireMission().storePlan(safePlan);
            ExecutionFrame frame = binding.branch().rootToLeafSnapshot().stream()
                    .filter(candidate -> candidate.traceFrameType() == TraceFrameType.ROOT_MISSION)
                    .findFirst().orElse(null);
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("planId", safePlan.planId());
            metadata.put("recordedAt", clock.instant().toString());
            if (transition != null) metadata.put("transition", transition.metadata());
            session.appendTraceRecord(TraceRecordType.PLAN_UPDATED, frame, Map.copyOf(metadata), safePlan);
        });
    }

    @Override
    public void recordSuccessfulSkillForCleanup(LoomspanSession session, String capabilityName, String linkedTaskId,
            MissionLifecycle.Cutoff cutoff)
    {
        ExecutionBinding binding = requireBinding(session);
        requireCleanupAuthority(binding, cutoff);
        cutoff.runIfPermitted(binding, () -> {
            MissionContext mission = binding.requireMission();
            mission.recordSuccessfulDirectSkill(capabilityName);
            session.appendTraceRecord(TraceRecordType.EVIDENCE_RECORDED, binding.branch().requireLeaf(), Map.of(
                    "capabilityName", capabilityName, "unplanned", false, "linkedTaskId", linkedTaskId), Map.of(
                    "successfulSkill", capabilityName,
                    "successfulDirectSkills", mission.successfulDirectSkills()));
        });
    }

    @Override
    public void recordPlanningEvent(LoomspanSession session,
            ExecutionFrame frame,
            TraceRecordType recordType,
            Map<String, Object> metadata,
            Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(recordType, "recordType must not be null");
        requireBinding(session).runIfWritable(() -> session.appendTraceRecord(recordType, frame,
                metadata == null ? Map.of() : new LinkedHashMap<>(metadata),
                payload == null ? Map.of() : payload));
    }

    @Override
    public void recordModelRequestSent(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.recordModelRequestSent(
                session,
                Objects.requireNonNull(frame, "frame must not be null"),
                Objects.requireNonNull(context, "context must not be null"),
                attempt,
                payload);
    }

    @Override
    public void recordModelResponseReceived(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, ModelUsageRecord usage, Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.recordModelResponseReceived(
                session,
                Objects.requireNonNull(frame, "frame must not be null"),
                Objects.requireNonNull(context, "context must not be null"),
                attempt,
                Objects.requireNonNull(usage, "usage must not be null"),
                payload);
    }

    @Override
    public void recordModelAttemptFailed(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, Map<String, Object> failureMetadata, Throwable failure,
            List<Map<String, Object>> providerDiagnostics)
    {
        Objects.requireNonNull(session, "session must not be null");
        List<Map<String, Object>> safeProviderDiagnostics = providerDiagnostics == null
                ? List.of()
                : providerDiagnostics.stream().map(Map::copyOf).toList();
        traceRecorder.recordModelAttemptFailed(session, Objects.requireNonNull(frame, "frame must not be null"),
                Objects.requireNonNull(context, "context must not be null"), attempt,
                failureMetadata == null ? Map.of() : Map.copyOf(failureMetadata),
                Objects.requireNonNull(failure, "failure must not be null"), safeProviderDiagnostics);
    }

    @Override
    public void logToolCall(LoomspanSession session, TaskExecutionEvent event)
    {
        Objects.requireNonNull(session, "session must not be null");
        TaskExecutionEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> traceRecorder.recordToolStarted(
                session, binding.branch().requireLeaf(), toolContext(safeEvent, false), safeEvent));
    }

    @Override
    public void logUnplannedToolCall(LoomspanSession session, TaskExecutionEvent event)
    {
        Objects.requireNonNull(session, "session must not be null");
        TaskExecutionEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> traceRecorder.recordToolStarted(
                session, binding.branch().requireLeaf(), toolContext(safeEvent, true), safeEvent));
    }

    @Override
    public void logToolResult(LoomspanSession session, TaskExecutionEvent event)
    {
        Objects.requireNonNull(session, "session must not be null");
        TaskExecutionEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> traceRecorder.recordToolCompleted(
                session, binding.branch().requireLeaf(),
                toolContext(safeEvent, safeEvent.linkedTaskId() == null), safeEvent));
    }

    @Override
    public void logToolFailure(LoomspanSession session, ToolTraceContext context, Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        ToolTraceContext safeContext = Objects.requireNonNull(context, "context must not be null");
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> traceRecorder.recordToolFailed(
                session, binding.branch().requireLeaf(), safeContext, payload));
    }

    @Override
    public void clearSuccessfulSkills()
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        binding.runIfWritable(binding.requireMission()::clearSuccessfulDirectSkills);
    }

    @Override
    public Set<String> currentSuccessfulSkills()
    {
        return requireMission().successfulDirectSkills();
    }

    @Override
    public void recordSuccessfulSkill(String capabilityName,
            String linkedTaskId,
            boolean unplanned)
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        LoomspanSession session = binding.session();
        MissionContext mission = binding.requireMission();
        Objects.requireNonNull(capabilityName, "capabilityName must not be null");
        binding.runIfWritable(() -> {
            mission.recordSuccessfulDirectSkill(capabilityName);
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("capabilityName", capabilityName);
            metadata.put("unplanned", unplanned);
            if (linkedTaskId != null) metadata.put("linkedTaskId", linkedTaskId);
            session.appendTraceRecord(TraceRecordType.EVIDENCE_RECORDED, binding.branch().requireLeaf(), Map.copyOf(metadata), Map.of(
                    "successfulSkill", capabilityName,
                    "successfulDirectSkills", mission.successfulDirectSkills()));
        });
    }

    @Override
    public void recordEvidenceValidation(LoomspanSession session,
            boolean passed,
            Map<String, Object> metadata,
            Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> session.appendTraceRecord(
                passed ? TraceRecordType.EVIDENCE_VALIDATION_PASSED : TraceRecordType.EVIDENCE_VALIDATION_FAILED,
                binding.branch().requireLeaf(),
                metadata == null ? Map.of() : Map.copyOf(metadata),
                payload == null ? Map.of() : payload));
    }

    @Override
    public void recordLinterOutcome(LoomspanSession session, LinterOutcome outcome)
    {
        ExecutionBinding binding = requireBinding(session);
        LinterOutcome recordedOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
        binding.runIfWritable(() -> {
            binding.branch().recordLinterOutcome(recordedOutcome);
            if (binding.branch().ownsMissionDiagnostics()) binding.requireMission().recordLinterOutcome(recordedOutcome);
            traceRecorder.recordLinterOutcome(session, recordedOutcome);
            sessionUsageService.recordLinterOutcome(session, recordedOutcome);
        });
    }

    @Override
    public void recordOutputSchemaOutcome(LoomspanSession session, OutputSchemaOutcome outcome)
    {
        ExecutionBinding binding = requireBinding(session);
        OutputSchemaOutcome recordedOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
        binding.runIfWritable(() -> {
            binding.branch().recordOutputSchemaOutcome(recordedOutcome);
            if (binding.branch().ownsMissionDiagnostics()) binding.requireMission().recordOutputSchemaOutcome(recordedOutcome);
            traceRecorder.recordOutputSchemaOutcome(session, recordedOutcome);
        });
    }

    @Override
    public void recordAdvisorRequestMutation(LoomspanSession session, AdvisorTraceContext context, Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.recordAdvisorRequestMutation(session, Objects.requireNonNull(context, "context must not be null"), payload);
    }

    @Override
    public void recordAdvisorResponseMutation(LoomspanSession session, AdvisorTraceContext context, Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.recordAdvisorResponseMutation(session, Objects.requireNonNull(context, "context must not be null"), payload);
    }

    @Override
    public String recordFailure(LoomspanSession session, Throwable failure, Map<String, Object> payload)
    {
        ExecutionBinding binding = requireBinding(session);
        return binding.requireWritable(() -> session.recordFailure(
                Objects.requireNonNull(failure, "failure must not be null"), payload,
                binding.branch().leaf().orElse(null)));
    }

    @Override
    public void registerProviderFailure(LoomspanSession session, Throwable failure, Map<String, Object> attempt)
    {
        ExecutionBinding binding = requireBinding(session);
        binding.runIfWritable(() -> session.registerProviderFailure(
                Objects.requireNonNull(failure, "failure must not be null"),
                Objects.requireNonNull(attempt, "attempt must not be null")));
    }

    @Override
    public void recordStepEvent(LoomspanSession session, ExecutionFrame frame, TraceRecordType recordType,
            Map<String, Object> metadata, Object payload)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(frame, "frame must not be null");
        Objects.requireNonNull(recordType, "recordType must not be null");
        requireBinding(session).runIfWritable(() -> session.appendTraceRecord(recordType, frame,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                payload == null ? Map.of() : payload));
    }

    @Override
    public void finalizeTrace(LoomspanSession session, TraceCompletion completion)
    {
        Objects.requireNonNull(session, "session must not be null");
        traceRecorder.finalizeTrace(session, Objects.requireNonNull(completion, "completion must not be null"));
    }

    private OperationType mapOperationType(TraceFrameType traceFrameType)
    {
        if (traceFrameType == null)
        {
            return OperationType.SKILL;
        }

        return switch (traceFrameType)
        {
            case ROOT_MISSION -> OperationType.CAPABILITY;
            case SKILL_EXECUTION, PLANNING, MODEL_CALL, TOOL_INVOCATION, STEP_EXECUTION -> OperationType.SKILL;
            case RETRY -> OperationType.SUB_AGENT;
        };
    }

    private ToolTraceContext toolContext(TaskExecutionEvent event, boolean unplanned)
    {
        return new ToolTraceContext(event.capabilityName(), event.linkedTaskId(), unplanned);
    }

    private ExecutionBinding requireBinding(LoomspanSession session)
    {
        Objects.requireNonNull(session, "session must not be null");
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        if (binding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        return binding;
    }

    private MissionContext requireMission()
    {
        return ExecutionBindingScope.requireCurrent().requireMission();
    }

    private void requireCleanupAuthority(ExecutionBinding binding, MissionLifecycle.Cutoff cutoff)
    {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        if (!cutoff.belongsTo(binding.requireMission()) || !cutoff.ownsSession(binding.session()))
        {
            throw new IllegalArgumentException("Cleanup authority does not belong to the current mission.");
        }
    }
}
