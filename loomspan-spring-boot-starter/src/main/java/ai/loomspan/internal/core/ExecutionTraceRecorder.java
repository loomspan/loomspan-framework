package ai.loomspan.internal.core;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.springframework.lang.Nullable;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.runtime.usage.ModelUsageRecord;

public interface ExecutionTraceRecorder
{
    record PlanExecutionTransition(Kind kind, List<String> taskIds, @Nullable String parallelGroup,
            @Nullable Boolean effectiveConcurrency, @Nullable JoinOutcome outcome)
    {
        public enum Kind { ADMISSION, JOIN }
        public enum JoinOutcome { COMPLETED, FAILED }

        public PlanExecutionTransition
        {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(taskIds, "taskIds must not be null");
            taskIds = List.copyOf(taskIds);
            if (taskIds.isEmpty()) throw new IllegalArgumentException("taskIds must not be empty");
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String taskId : taskIds)
            {
                if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskIds must be nonblank");
                if (!unique.add(taskId)) throw new IllegalArgumentException("taskIds must not contain duplicates");
            }
            if (parallelGroup != null && parallelGroup.isBlank())
                throw new IllegalArgumentException("parallelGroup must be null or nonblank");
            if (kind == Kind.ADMISSION && (effectiveConcurrency == null || outcome != null))
                throw new IllegalArgumentException("admission requires effectiveConcurrency and forbids outcome");
            if (kind == Kind.JOIN && (outcome == null || effectiveConcurrency != null))
                throw new IllegalArgumentException("join requires outcome and forbids effectiveConcurrency");
            if (Boolean.TRUE.equals(effectiveConcurrency) && parallelGroup == null)
                throw new IllegalArgumentException("concurrent admission requires a parallelGroup");
        }

        public static PlanExecutionTransition admission(List<String> taskIds, @Nullable String parallelGroup,
                boolean effectiveConcurrency)
        {
            return new PlanExecutionTransition(Kind.ADMISSION, taskIds, parallelGroup, effectiveConcurrency, null);
        }

        public static PlanExecutionTransition join(List<String> taskIds, @Nullable String parallelGroup, JoinOutcome outcome)
        {
            return new PlanExecutionTransition(Kind.JOIN, taskIds, parallelGroup, null,
                    Objects.requireNonNull(outcome, "outcome must not be null"));
        }

        public Map<String, Object> metadata()
        {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("kind", kind.name());
            value.put("taskIds", taskIds);
            value.put("parallelGroup", parallelGroup);
            if (kind == Kind.ADMISSION) value.put("effectiveConcurrency", effectiveConcurrency);
            else value.put("outcome", outcome.name());
            return Collections.unmodifiableMap(value);
        }
    }
    void recordFrameOpened(LoomspanSession session, ExecutionFrame frame);

    void recordFrameClosed(LoomspanSession session, ExecutionFrame frame, Map<String, Object> metadata);

    void recordModelRequestSent(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, Object payload);

    void recordModelResponseReceived(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, ModelUsageRecord usage, Object payload);

    void recordModelAttemptFailed(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, Map<String, Object> failureMetadata, Throwable failure,
            List<Map<String, Object>> providerDiagnostics);

    void recordPlanCreated(LoomspanSession session, ExecutionPlan plan, Map<String, Object> acceptedAttempt);

    void recordPlanUpdated(LoomspanSession session, ExecutionPlan plan, PlanExecutionTransition transition);

    void recordToolStarted(LoomspanSession session, ExecutionFrame frame, ToolTraceContext context, Object payload);

    void recordToolCompleted(LoomspanSession session, ExecutionFrame frame, ToolTraceContext context, Object payload);

    void recordToolFailed(LoomspanSession session, ExecutionFrame frame, ToolTraceContext context, Object payload);

    void recordAdvisorRequestMutation(LoomspanSession session, AdvisorTraceContext context, Object payload);

    void recordAdvisorResponseMutation(LoomspanSession session, AdvisorTraceContext context, Object payload);

    void recordLinterOutcome(LoomspanSession session, LinterOutcome outcome);

    void recordOutputSchemaOutcome(LoomspanSession session, OutputSchemaOutcome outcome);

    void finalizeTrace(LoomspanSession session, TraceCompletion completion);
}
