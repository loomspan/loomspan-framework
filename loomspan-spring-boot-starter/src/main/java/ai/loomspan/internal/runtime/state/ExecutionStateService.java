package ai.loomspan.internal.runtime.state;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.AdvisorTraceContext;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.TaskExecutionEvent;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceCompletion;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.core.ToolTraceContext;
import ai.loomspan.internal.core.MissionLifecycle;
import ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.runtime.usage.ModelUsageRecord;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ExecutionStateService
{
    ExecutionFrame openMissionFrame(LoomspanSession session, String route, Map<String, Object> parameters);

    ExecutionFrame openFrame(LoomspanSession session, TraceFrameType traceFrameType, String route, Map<String, Object> parameters);

    void closeMissionFrame(LoomspanSession session, ExecutionFrame frame);

    void closeFrame(LoomspanSession session, ExecutionFrame frame, Map<String, Object> metadata);

    void closeFrameForCleanup(LoomspanSession session, ExecutionFrame frame, Map<String, Object> metadata,
            MissionLifecycle.Cutoff cutoff, boolean alreadyDrained);

    void storePlan(ExecutionPlan plan);

    void clearPlan();

    Optional<ExecutionPlan> currentPlan();

    void logPlanCreated(LoomspanSession session, ExecutionPlan plan, Map<String, Object> acceptedAttempt);

    void logPlanUpdated(LoomspanSession session, ExecutionPlan plan, @Nullable PlanExecutionTransition transition);

    void storeAndLogPlanForCleanup(LoomspanSession session, ExecutionPlan plan,
            @Nullable PlanExecutionTransition transition, MissionLifecycle.Cutoff cutoff);

    void recordSuccessfulSkillForCleanup(LoomspanSession session, String capabilityName, String linkedTaskId,
            MissionLifecycle.Cutoff cutoff);

    void recordPlanningEvent(LoomspanSession session,
            ExecutionFrame frame,
            TraceRecordType recordType,
            Map<String, Object> metadata,
            Object payload);

    void recordModelRequestSent(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, Object payload);

    void recordModelResponseReceived(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, ModelUsageRecord usage, Object payload);

    void recordModelAttemptFailed(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, Map<String, Object> failureMetadata, Throwable failure,
            List<Map<String, Object>> providerDiagnostics);

    void logToolCall(LoomspanSession session, TaskExecutionEvent event);

    void logUnplannedToolCall(LoomspanSession session, TaskExecutionEvent event);

    void logToolResult(LoomspanSession session, TaskExecutionEvent event);

    void logToolFailure(LoomspanSession session, ToolTraceContext context, Object payload);

    void clearSuccessfulSkills();

    Set<String> currentSuccessfulSkills();

    void recordSuccessfulSkill(String capabilityName,
            @Nullable String linkedTaskId,
            boolean unplanned);

    void recordEvidenceValidation(LoomspanSession session,
            boolean passed,
            Map<String, Object> metadata,
            Object payload);

    void recordLinterOutcome(LoomspanSession session, LinterOutcome outcome);

    void recordOutputSchemaOutcome(LoomspanSession session, OutputSchemaOutcome outcome);

    void recordAdvisorRequestMutation(LoomspanSession session, AdvisorTraceContext context, Object payload);

    void recordAdvisorResponseMutation(LoomspanSession session, AdvisorTraceContext context, Object payload);

    String recordFailure(LoomspanSession session, Throwable failure, Map<String, Object> payload);

    void registerProviderFailure(LoomspanSession session, Throwable failure, Map<String, Object> attempt);

    void recordStepEvent(LoomspanSession session, ExecutionFrame frame, TraceRecordType recordType,
            Map<String, Object> metadata, Object payload);

    void finalizeTrace(LoomspanSession session, TraceCompletion completion);
}
