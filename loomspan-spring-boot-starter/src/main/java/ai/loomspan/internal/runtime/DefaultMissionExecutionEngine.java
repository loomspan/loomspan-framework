package ai.loomspan.internal.runtime;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionRequest;
import ai.loomspan.internal.runtime.attachment.DefaultMissionInputMaterializer;
import ai.loomspan.internal.runtime.attachment.MissionInputMaterializer;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.MissionLifecycle;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceFailureMetadata;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.prompt.SkillPromptComposer;
import ai.loomspan.internal.runtime.prompt.SkillPromptComposition;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.vfs.DefaultRefResolver;
import ai.loomspan.internal.vfs.SessionLocalVirtualFileSystem;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultMissionExecutionEngine implements MissionExecutionEngine
{
    private final PlanningService planningService;
    private final ExecutionStateService executionStateService;
    private final MissionWorkExecutor missionWorkExecutor;
    private final MissionInputMaterializer missionInputMaterializer;

    public DefaultMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService, MissionWorkExecutor missionWorkExecutor)
    {
        this(planningService, executionStateService, missionWorkExecutor, defaultMaterializer());
    }

    public DefaultMissionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService, MissionWorkExecutor missionWorkExecutor,
            MissionInputMaterializer missionInputMaterializer)
    {
        this.planningService = Objects.requireNonNull(planningService, "planningService must not be null");
        this.executionStateService = Objects.requireNonNull(executionStateService, "executionStateService must not be null");
        this.missionWorkExecutor = Objects.requireNonNull(missionWorkExecutor, "missionWorkExecutor must not be null");
        this.missionInputMaterializer = Objects.requireNonNull(missionInputMaterializer, "missionInputMaterializer must not be null");
    }

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
        return missionWorkExecutor.execute(session, skillName, () -> {
            ExecutionBinding capturedBinding = ExecutionBindingScope.requireCurrent();
            MissionLifecycle lifecycle = capturedBinding.requireMission().lifecycle();
            RenderedMissionInput renderedInput = missionInputMaterializer.materialize(session, definition, objective, missionInput);
            if (planningEnabled)
            {
                planningService.initializePlan(session, objective, planningInput(missionInput, renderedInput), definition, modelInteraction, visibleTools);
            }

            lifecycle.requireOpenForNewWork(capturedBinding);
            SkillPromptComposition promptComposition = executionStateService.currentPlan()
                    .map(plan -> SkillPromptComposer.composePlannedExecutionPrompt(definition, buildPlannedExecutionPrompt(plan)))
                    .orElseGet(() -> SkillPromptComposer.composeDefaultExecutionPrompt(definition));
            String executionPrompt = promptComposition.systemPrompt();

            ModelExecutionIdentity modelIdentity = ModelExecutionIdentity.from(executionConfiguration);
            ExecutionFrame modelFrame = executionStateService.openFrame(
                    session,
                    TraceFrameType.MODEL_CALL,
                    skillName + "#mission-model",
                    modelIdentity.metadata());

            String modelFrameStatus = "completed";
            Throwable modelFailure = null;

            try
            {
                ModelTraceContext modelTraceContext = new ModelTraceContext(
                        modelIdentity,
                        skillName,
                        "mission");

                return modelInteraction.call(new ModelInteractionRequest(
                        executionPrompt, renderedInput, modelTraceContext, visibleTools, false)).content();
            }
            catch (RuntimeException | Error ex)
            {
                modelFailure = ex;
                boolean cancelling = lifecycle.state() != MissionLifecycle.State.OPEN;
                modelFrameStatus = cancelling || Thread.currentThread().isInterrupted() ? "aborted" : "failed";
                if (!cancelling)
                {
                    executionStateService.recordFailure(session, ex, Map.of("message", "Model invocation failed"));
                }
                if (ex instanceof RuntimeException runtimeException && !renderedInput.attachments().isEmpty())
                {
                    String mediaDetails = renderedInput.attachments().stream()
                            .map(attachment -> attachment.mediaType() + "/" + attachment.contentType())
                            .collect(java.util.stream.Collectors.joining(", "));
                    throw new IllegalStateException("Model call for skill '" + skillName + "' using framework model '"
                            + executionConfiguration.frameworkModel() + "' through connection '" + executionConfiguration.connection()
                            + "' (driver " + executionConfiguration.driver().name() + ", provider model '"
                            + executionConfiguration.providerModel() + "') failed with " + renderedInput.attachments().size()
                            + " attachment(s) [" + mediaDetails
                            + "]. Use a model/driver that supports the declared attachment media.", runtimeException);
                }
                throw ex;
            }
            finally
            {
                executionStateService.closeFrame(session, modelFrame, closeMetadata(modelFrameStatus, modelFailure));
            }
        });
    }

    private Map<String, Object> closeMetadata(String status, @Nullable Throwable failure)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", Thread.currentThread().isInterrupted() ? "aborted" : status);

        if (failure != null)
        {
            TraceFailureMetadata.addTo(metadata, failure, "Model invocation failed");
        }

        return metadata;
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

    private String buildPlannedExecutionPrompt(ExecutionPlan plan)
    {
        String readyTaskLines = plan.readyTasks().stream()
                .map(task -> "- [" + task.status() + "] " + task.taskId() + ": " + task.title()
                        + (task.note() == null ? "" : " (" + task.note() + ")"))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- No ready tasks");

        String failedTaskLines = plan.tasks().stream()
                .filter(task -> task.status() == PlanTaskStatus.FAILED)
                .map(task -> "- " + task.taskId() + ": " + task.title()
                        + (task.note() == null ? "" : " (" + task.note() + ")"))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- No failed tasks");

        String activeTasks = plan.tasks().stream()
                .filter(task -> task.status() == PlanTaskStatus.IN_PROGRESS)
                .map(task -> task.taskId() + ": " + task.title())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");

        return """
                Execute the mission using only the visible skill tools when needed.
                Keep the stored flight plan as the execution anchor and advance work consistently with it.
                Active plan %s for capability %s is %s.
                Active tasks: %s
                Ready tasks:
                %s
                Failed tasks:
                %s
                """.formatted(plan.planId(), plan.capabilityName(), plan.status(), activeTasks, readyTaskLines, failedTaskLines);
    }

}
