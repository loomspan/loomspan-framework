package ai.loomspan.internal.observability.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillEntry;

import com.fasterxml.jackson.annotation.JsonInclude;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.runtime.observation.ExecutionActivityKind;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ObservabilityDtos
{
    private ObservabilityDtos() {}

    public record InstanceStatus(
            String instanceId,
            String consoleCompatibilityVersion,
            Instant observedAt,
            boolean liveMonitoringAvailable,
            int registeredSkillCount,
            int activeExecutionCount,
            int catalogedTraceCount,
            TracePersistencePolicy tracePersistencePolicy,
            Duration completionGraceTtl,
            Duration traceCatalogMetadataTtl) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkillSummary(String registeredName, String kind, String declaration, String sourcePath, String executorName, String beanName, String method, String href)
    {
        public SkillSummary
        {
            RegisteredSkillEntry.validateLocation(registeredName, kind, declaration, sourcePath, executorName, beanName, method);
        }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkillDetail(String registeredName, String kind, String declaration, String sourcePath, String executorName, String beanName, String method, String yaml, @JsonInclude(JsonInclude.Include.ALWAYS) String outputSchema)
    {
        public SkillDetail
        {
            new RegisteredSkillEntry(registeredName, kind, declaration, sourcePath, executorName, beanName, method, yaml, outputSchema);
        }
    }
    public record FramePathEntry(String frameId, TraceFrameType frameType, String route)
    {
        public FramePathEntry
        {
            requireNonBlank(frameId, "frameId");
            Objects.requireNonNull(frameType, "frameType must not be null");
            requireNonBlank(route, "route");
        }
    }
    public record ActiveBranch(
            @JsonInclude(JsonInclude.Include.ALWAYS) String planId,
            @JsonInclude(JsonInclude.Include.ALWAYS) String taskId,
            @JsonInclude(JsonInclude.Include.ALWAYS) Integer stepNumber,
            @JsonInclude(JsonInclude.Include.ALWAYS) String parallelGroup,
            @JsonInclude(JsonInclude.Include.ALWAYS) Boolean effectiveConcurrency,
            List<FramePathEntry> path)
    {
        public ActiveBranch
        {
            path = List.copyOf(Objects.requireNonNull(path, "path must not be null"));
            if (path.isEmpty()) throw new IllegalArgumentException("path must not be empty");
            boolean complete = planId != null && taskId != null && stepNumber != null && effectiveConcurrency != null;
            boolean empty = planId == null && taskId == null && stepNumber == null
                    && parallelGroup == null && effectiveConcurrency == null;
            if (!complete && !empty) throw new IllegalArgumentException("assignment fields must be complete or null");
            if (complete)
            {
                requireNonBlank(planId, "planId");
                requireNonBlank(taskId, "taskId");
                if (stepNumber <= 0) throw new IllegalArgumentException("stepNumber must be positive");
                if (parallelGroup != null) requireNonBlank(parallelGroup, "parallelGroup");
                if (effectiveConcurrency && parallelGroup == null)
                    throw new IllegalArgumentException("effectiveConcurrency true requires parallelGroup");
            }
        }
    }
    public record QuotaLimits(
            int maxSkillInvocations,
            int maxToolInvocations,
            int maxLinterRetries,
            int maxModelCalls,
            int maxProviderAttempts,
            int maxUsageUnits) {}
    public record Usage(
            int skillInvocations,
            int toolInvocations,
            int linterRetries,
            int modelCalls,
            int providerAttempts,
            int promptUnits,
            int completionUnits,
            int usageUnits,
            int exactModelResponses,
            int heuristicModelResponses,
            int unavailableModelResponses) {}
    public record ActiveExecution(
            String sessionId,
            String traceId,
            long lastCanonicalSequence,
            Instant startedAt,
            Instant updatedAt,
            long elapsedMillis,
            String entrySkill,
            String generationId,
            String entryNamespace,
            java.util.Map<String, String> namespaceGenerationIds,
            String status,
            String phase,
            String summary,
            List<ActiveBranch> activeBranches,
            Usage usage,
            QuotaLimits configuredLimits)
    {
        public ActiveExecution
        {
            namespaceGenerationIds = new ai.loomspan.internal.core.CapturedNamespaceMetadata(
                    entryNamespace, generationId, namespaceGenerationIds).namespaceGenerationIds();
            activeBranches = List.copyOf(Objects.requireNonNull(activeBranches, "activeBranches must not be null"));
        }
    }
    public record Trace(
            String traceId,
            String sessionId,
            String entrySkill,
            String generationId,
            String entryNamespace,
            java.util.Map<String, String> namespaceGenerationIds,
            TraceOutcome outcome,
            Instant finalizedAt,
            long sizeBytes,
            TracePersistencePolicy persistencePolicy,
            Instant applicationTraceExpiresAt)
    {
        public Trace
        {
            namespaceGenerationIds = new ai.loomspan.internal.core.CapturedNamespaceMetadata(
                    entryNamespace, generationId, namespaceGenerationIds).namespaceGenerationIds();
        }
    }
    public record Page<T>(
            List<T> items,
            boolean hasMore,
            @JsonInclude(JsonInclude.Include.ALWAYS) String nextCursor,
            Instant observedAt) {}
    public record ActivePage(
            List<ActiveExecution> items,
            boolean hasMore,
            @JsonInclude(JsonInclude.Include.ALWAYS) String nextCursor,
            Instant observedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String resumeCursor) {}

    public record ActivityHandshake(
            String instanceId,
            Instant observedAt,
            String afterCursor) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActivityEnvelope(
            String instanceId,
            String cursor,
            String sessionId,
            String traceId,
            Long canonicalSequence,
            Instant timestamp,
            ExecutionActivityKind kind,
            String executionStatus,
            String frameId,
            String parentFrameId,
            TraceFrameType frameType,
            String route,
            String summary,
            Map<String, Object> details) {}

    private static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
