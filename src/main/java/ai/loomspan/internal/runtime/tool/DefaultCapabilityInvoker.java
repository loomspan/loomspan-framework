package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.TaskExecutionEvent;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.ToolTraceContext;
import ai.loomspan.internal.core.TraceFailureMetadata;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.runtime.usage.NoOpUsageMetricsRecorder;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.runtime.usage.UsageMetricsRecorder;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultCapabilityInvoker implements CapabilityInvoker, CapabilityBindingFactory
{
    private final CapabilityExecutionRouter capabilityExecutionRouter;
    private final PlanningService planningService;
    private final ExecutionStateService executionStateService;
    private final SessionUsageService sessionUsageService;
    private final UsageMetricsRecorder usageMetricsRecorder;

    public DefaultCapabilityInvoker(CapabilityExecutionRouter capabilityExecutionRouter,
            PlanningService planningService,
            ExecutionStateService executionStateService)
    {
        this(capabilityExecutionRouter, planningService, executionStateService, new NoOpSessionUsageService(), new NoOpUsageMetricsRecorder());
    }

    public DefaultCapabilityInvoker(CapabilityExecutionRouter capabilityExecutionRouter,
            PlanningService planningService,
            ExecutionStateService executionStateService,
            SessionUsageService sessionUsageService,
            UsageMetricsRecorder usageMetricsRecorder)
    {
        this.capabilityExecutionRouter = Objects.requireNonNull(capabilityExecutionRouter, "capabilityExecutionRouter must not be null");
        this.planningService = Objects.requireNonNull(planningService, "planningService must not be null");
        this.executionStateService = Objects.requireNonNull(executionStateService, "executionStateService must not be null");
        this.sessionUsageService = Objects.requireNonNull(sessionUsageService, "sessionUsageService must not be null");
        this.usageMetricsRecorder = Objects.requireNonNull(usageMetricsRecorder, "usageMetricsRecorder must not be null");
    }

    @Override
    public List<BoundCapability> bind(LoomspanSession session,
            YamlSkillDefinition definition,
            List<CapabilityMetadata> capabilities,
            @Nullable Authentication authentication)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        return capabilities.stream()
                .map(capability -> new BoundCapability(capability,
                        (arguments, linkedTaskId) -> invoke(
                                capability, arguments, session, definition, authentication, linkedTaskId)))
                .toList();
    }

    @Override
    public Object invoke(CapabilityMetadata capability,
            Map<String, Object> arguments,
            LoomspanSession session,
            YamlSkillDefinition definition,
            @Nullable Authentication authentication,
            @Nullable String boundTaskId)
    {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        String currentSkillName = currentSkillName(session);
        sessionUsageService.recordToolCall(session, currentSkillName, capability.name());
        String linkedTaskId = boundTaskId;

        if (linkedTaskId == null)
        {
            linkedTaskId = planningService.markToolStarted(session, capability).orElse(null);
        }

        ExecutionFrame toolFrame = executionStateService.openFrame(
                session,
                TraceFrameType.TOOL_INVOCATION,
                capability.name(),
                toolFrameParameters(safeArguments, linkedTaskId));

        String toolFrameStatus = "completed";
        Throwable toolFailure = null;

        try
        {
            if (linkedTaskId == null)
            {
                executionStateService.logUnplannedToolCall(session, TaskExecutionEvent.unlinked(
                        capability.name(),
                        Map.of("arguments", safeArguments),
                        "No unique ready task matched this tool call"));
            }
            else
            {
                executionStateService.logToolCall(session, TaskExecutionEvent.linked(
                        capability.name(),
                        linkedTaskId,
                        Map.of("arguments", safeArguments),
                        null));
            }

            Object result = capabilityExecutionRouter.execute(capability, safeArguments, session, authentication);
            if (linkedTaskId != null && boundTaskId == null)
            {
                planningService.markToolCompleted(session, linkedTaskId, capability.name());
            }
            else if (linkedTaskId == null)
            {
                executionStateService.recordSuccessfulSkill(capability.name(), null, true);
            }

            sessionUsageService.recordToolOutcome(session, currentSkillName, capability.name(), "success");
            executionStateService.logToolResult(session, linkedTaskId == null
                    ? TaskExecutionEvent.unlinked(capability.name(), Map.of("result", result), null)
                    : TaskExecutionEvent.linked(capability.name(), linkedTaskId, Map.of("result", result), null));

            return result;
        }
        catch (RuntimeException | Error ex)
        {
            toolFailure = ex;
            toolFrameStatus = Thread.currentThread().isInterrupted() ? "aborted" : "failed";

            if (linkedTaskId != null && boundTaskId == null && ex instanceof RuntimeException runtimeException)
            {
                planningService.markToolFailed(session, linkedTaskId, capability.name(), runtimeException);
            }

            sessionUsageService.recordToolOutcome(session, currentSkillName, capability.name(), "failure");
            LinkedHashMap<String, Object> failureMetadata = new LinkedHashMap<>();
            failureMetadata.put("capabilityName", capability.name());

            if (linkedTaskId != null)
            {
                failureMetadata.put("linkedTaskId", linkedTaskId);
            }
            TraceFailureMetadata.addTo(failureMetadata, ex, "Tool execution failed");

            executionStateService.logToolFailure(
                    session,
                    new ToolTraceContext(capability.name(), linkedTaskId, linkedTaskId == null),
                    Map.of("arguments", safeArguments, "failure", failureMetadata));

            LinkedHashMap<String, Object> errorPayload = new LinkedHashMap<>();
            errorPayload.put("tool", capability.name());

            if (linkedTaskId != null)
            {
                errorPayload.put("linkedTaskId", linkedTaskId);
            }
            TraceFailureMetadata.addTo(errorPayload, ex, "Tool execution failed");
            executionStateService.recordFailure(session, ex, errorPayload);
            throw ex;
        }
        finally
        {
            executionStateService.closeFrame(session, toolFrame, closeMetadata(toolFrameStatus, toolFailure));
        }
    }

    private Map<String, Object> toolFrameParameters(Map<String, Object> arguments, @Nullable String linkedTaskId)
    {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("arguments", arguments);

        if (linkedTaskId != null)
        {
            parameters.put("linkedTaskId", linkedTaskId);
        }
        return parameters;
    }

    private Map<String, Object> closeMetadata(String status, @Nullable Throwable failure)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", Thread.currentThread().isInterrupted() ? "aborted" : status);
        if (failure != null)
        {
            TraceFailureMetadata.addTo(metadata, failure, "Tool execution failed");
        }
        return metadata;
    }

    private String currentSkillName(LoomspanSession session)
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        if (binding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        return binding.branch().leaf().map(ExecutionFrame::route)
                .orElseGet(() -> binding.requireMission().currentPlan()
                        .map(plan -> plan.capabilityName()).orElse("unknown"));
    }

}
