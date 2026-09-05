package ai.loomspan.internal.core;

import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionFactory;
import ai.loomspan.internal.model.ModelInteractionMode;
import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.MissionWorkExecutor;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.tool.CapabilityBindingFactory;
import ai.loomspan.internal.runtime.tool.ToolSurfaceService;
import ai.loomspan.internal.security.AccessGuard;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

public class ExecutionCoordinator
{
    private final YamlSkillCatalog yamlSkillCatalog;
    private final CapabilityRegistry capabilityRegistry;
    private final ModelInteractionFactory modelInteractionFactory;
    private final ToolSurfaceService toolSurfaceService;
    private final CapabilityBindingFactory capabilityBindingFactory;
    private final MissionExecutionEngine missionExecutionEngine;
    private final MissionExecutionEngine stepLoopMissionExecutionEngine;
    private final ExecutionStateService executionStateService;
    private final AccessGuard accessGuard;
    private final MissionWorkExecutor missionWorkExecutor;
    private final ai.loomspan.internal.vfs.RefResolver refResolver;
    private final ai.loomspan.internal.security.ScopedAuthentication scopedAuthentication;

    public ExecutionCoordinator(YamlSkillCatalog yamlSkillCatalog,
            CapabilityRegistry capabilityRegistry,
            ModelInteractionFactory modelInteractionFactory,
            ToolSurfaceService toolSurfaceService,
            CapabilityBindingFactory capabilityBindingFactory,
            MissionExecutionEngine missionExecutionEngine,
            MissionExecutionEngine stepLoopMissionExecutionEngine,
            ExecutionStateService executionStateService,
            AccessGuard accessGuard,
            ai.loomspan.internal.vfs.RefResolver refResolver,
            ai.loomspan.internal.security.ScopedAuthentication scopedAuthentication,
            MissionWorkExecutor missionWorkExecutor)
    {
        this.yamlSkillCatalog = Objects.requireNonNull(yamlSkillCatalog, "yamlSkillCatalog must not be null");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
        this.modelInteractionFactory = Objects.requireNonNull(modelInteractionFactory, "modelInteractionFactory must not be null");
        this.toolSurfaceService = Objects.requireNonNull(toolSurfaceService, "toolSurfaceService must not be null");
        this.capabilityBindingFactory = Objects.requireNonNull(capabilityBindingFactory, "capabilityBindingFactory must not be null");
        this.missionExecutionEngine = Objects.requireNonNull(missionExecutionEngine, "missionExecutionEngine must not be null");
        this.stepLoopMissionExecutionEngine = Objects.requireNonNull(stepLoopMissionExecutionEngine, "stepLoopMissionExecutionEngine must not be null");
        this.executionStateService = Objects.requireNonNull(executionStateService, "executionStateService must not be null");
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard must not be null");
        this.refResolver = Objects.requireNonNull(refResolver);
        this.scopedAuthentication = Objects.requireNonNull(scopedAuthentication);
        this.missionWorkExecutor = Objects.requireNonNull(missionWorkExecutor);
    }

    public String execute(String skillName, String objective, LoomspanSession session, @Nullable Authentication authentication)
    {
        return execute(skillName, objective, null, session, authentication);
    }

    public String execute(String skillName,
            String objective,
            @Nullable Map<String, Object> missionInput,
            LoomspanSession session,
            @Nullable Authentication authentication)
    {
        Objects.requireNonNull(session, "session must not be null");
        requireNonBlank(objective, "objective");
        CapabilityMetadata rootCapability = requireCapability(skillName);
        YamlSkillDefinition definition = rootCapability.kind() == CapabilityKind.YAML_SKILL ? requireYamlSkill(skillName) : null;
        ExecutionBinding baseBinding = ExecutionBindingScope.current().orElseGet(() -> ExecutionBinding.sessionOnly(session));
        if (baseBinding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        MissionContext parentMission = baseBinding.mission();
        boolean topLevelInvocation = parentMission == null;
        if (topLevelInvocation && !rootCapability.name().equals(session.entrySkill()))
        {
            throw new IllegalArgumentException("Top-level capability name does not match session entry skill");
        }

        MissionContext mission = new MissionContext(session, rootCapability.name(), UUID.randomUUID().toString(), parentMission);
        ExecutionBinding missionBinding = baseBinding.withMission(mission);
        return ExecutionBindingScope.supplyWith(missionBinding, () -> executeBound(
                definition, rootCapability, objective, missionInput, session, authentication,
                mission, parentMission, topLevelInvocation));
    }

    private String executeBound(YamlSkillDefinition definition,
            CapabilityMetadata rootCapability,
            String objective,
            @Nullable Map<String, Object> missionInput,
            LoomspanSession session,
            @Nullable Authentication authentication,
            MissionContext mission,
            @Nullable MissionContext parentMission,
            boolean topLevelInvocation)
    {
        String skillName = rootCapability.name();

        LinkedHashMap<String, Object> frameParameters = new LinkedHashMap<>();
        frameParameters.put("objective", objective);

        if (missionInput != null && !missionInput.isEmpty())
        {
            frameParameters.put("missionInput", definition == null
                    ? traceSafeJavaInput(missionInput)
                    : traceSafeMissionInput(definition, missionInput));
        }

        ExecutionFrame frame = executionStateService.openMissionFrame(session, rootCapability.name(), Map.copyOf(frameParameters));
        Throwable failure = null;
        String terminalFailureId = null;

        try
        {
            accessGuard.checkAccess(rootCapability, session, authentication);
            if (rootCapability.kind() == CapabilityKind.JAVA_SKILL)
            {
                Authentication caller = accessGuard.resolveAuthentication(authentication, session);
                return missionWorkExecutor.execute(session, skillName, () -> {
                    try (var ignored = scopedAuthentication.open(caller))
                    {
                        return String.valueOf(rootCapability.invoker().invoke(refResolver.resolveArguments(
                                missionInput == null ? Map.of() : missionInput, session, rootCapability.inputContract())));
                    }
                });
            }
            boolean stepExecutionEnabled = definition.planningModeExplicitlyEnabled();
            MissionExecutionEngine engine = stepExecutionEnabled ? stepLoopMissionExecutionEngine : missionExecutionEngine;
            ModelInteraction modelInteraction = modelInteractionFactory.create(definition,
                    stepExecutionEnabled ? ModelInteractionMode.STEP_EXECUTION : ModelInteractionMode.STANDARD);

            List<BoundCapability> visibleTools = capabilityBindingFactory.bind(
                    session,
                    definition,
                    toolSurfaceService.visibleToolsFor(skillName, session, authentication),
                    authentication);

            return engine.executeMission(
                    session,
                    definition,
                    objective,
                    missionInput,
                    modelInteraction,
                    visibleTools,
                    definition.planningModeExplicitlyEnabled(),
                    authentication);
        }
        catch (RuntimeException | Error ex)
        {
            failure = ex;
            terminalFailureId = mission.lifecycle().primaryCancellation()
                    .map(MissionLifecycle.PrimaryCancellation::failureId)
                    .orElseGet(() -> executionStateService.recordFailure(session, ex, errorPayload(skillName, objective, ex)));
            throw ex;
        }
        finally
        {
            RuntimeException cleanupFailure = null;
            try
            {
                Optional<MissionLifecycle.Cutoff> cutoff = mission.lifecycle().cutoff();
                if (cutoff.isPresent())
                {
                    executionStateService.closeFrameForCleanup(
                            session, frame, closeMetadata(failure, terminalFailureId), cutoff.orElseThrow(), false);
                }
                else
                {
                    executionStateService.closeFrame(session, frame, closeMetadata(failure, terminalFailureId));
                    mission.lifecycle().closeNow();
                }
            }
            catch (RuntimeException ex)
            {
                cleanupFailure = ex;
                if (terminalFailureId == null)
                {
                    try
                    {
                        terminalFailureId = executionStateService.recordFailure(
                                session, ex, cleanupErrorPayload(skillName, objective, ex));
                    }
                    catch (RuntimeException errorRecordingFailure)
                    {
                        // Canonical failure recording must not mutate the application exception.
                    }
                }
            }
            if (topLevelInvocation)
            {
                session.replaceRetainedRootState(
                        mission.currentPlan().orElse(null),
                        mission.lastLinterOutcome().orElse(null),
                        mission.lastOutputSchemaOutcome().orElse(null));
                try
                {
                    TraceOutcome outcome = terminalOutcome(failure, cleanupFailure);
                    executionStateService.finalizeTrace(session, new TraceCompletion(
                            outcome,
                            session.getSessionUsage().orElse(
                                    ai.loomspan.internal.runtime.usage.SessionUsageSnapshot.empty()),
                            terminalFailureId,
                            Map.of(
                                    "skillName", skillName,
                                    "objective", objective,
                                    "remainingFrames", ExecutionBindingScope.requireCurrent().branch().depth())));
                }
                catch (RuntimeException ex)
                {
                    if (cleanupFailure == null)
                    {
                        cleanupFailure = ex;
                    }
                    else
                    {
                        cleanupFailure.addSuppressed(ex);
                    }
                }
            }
            if (parentMission != null)
            {
                ExecutionBinding childBinding = ExecutionBindingScope.requireCurrent();
                ExecutionBinding parentBinding = childBinding.withMission(parentMission);
                parentBinding.runIfWritable(() -> {
                    PhysicalBranchContext branch = parentBinding.branch();
                    BranchDiagnosticDelta diagnostics = mission.diagnosticDelta();
                    if (branch.ownsMissionDiagnostics())
                    {
                        parentMission.mergeDiagnostics(diagnostics);
                    }
                    else
                    {
                        branch.mergeDiagnostics(diagnostics);
                    }
                });
            }
            if (cleanupFailure != null)
            {
                if (failure != null)
                {
                    if (!session.hasFailureRecordingFailure())
                    {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                else
                {
                    throw cleanupFailure;
                }
            }
        }
    }

    private TraceOutcome terminalOutcome(@Nullable Throwable failure, @Nullable Throwable cleanupFailure)
    {
        if (failure == null && cleanupFailure == null)
        {
            return TraceOutcome.SUCCEEDED;
        }
        return Thread.currentThread().isInterrupted() || isCancellation(failure) || isCancellation(cleanupFailure)
                ? TraceOutcome.ABORTED
                : TraceOutcome.FAILED;
    }

    private boolean isCancellation(@Nullable Throwable failure)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (current instanceof LoomspanMissionTimeoutException
                    || current instanceof CancellationException
                    || current instanceof InterruptedException)
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, Object> closeMetadata(@Nullable Throwable failure, @Nullable String failureId)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", failure == null
                ? "completed"
                : (Thread.currentThread().isInterrupted() || isCancellation(failure) ? "aborted" : "failed"));
        if (failure != null)
        {
            metadata.put("failureId", Objects.requireNonNull(failureId, "failureId must not be null"));
            TraceFailureMetadata.addTo(metadata, failure, "Mission execution failed");
        }
        return metadata;
    }

    private Map<String, Object> cleanupErrorPayload(String skillName, String objective, Throwable failure)
    {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("skillName", skillName);
        payload.put("objective", objective);
        TraceFailureMetadata.addTo(payload, failure, "Mission finalization failed");
        return Map.copyOf(payload);
    }

    private Map<String, Object> errorPayload(String skillName, String objective, Throwable failure)
    {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("skillName", skillName);
        payload.put("objective", objective);
        TraceFailureMetadata.addTo(payload, failure, "Mission execution failed");
        return payload;
    }

    private Object traceSafeMissionInput(YamlSkillDefinition definition, Object value)
    {
        if (!definition.hasDeclaredInputSchema())
        {
            return value;
        }
        return traceSafeNode(definition.inputSchema(), value, "");
    }

    private Object traceSafeJavaInput(Object value)
    {
        if (value instanceof Resource || value instanceof java.io.InputStream || value instanceof byte[])
        {
            return Map.of("source", "resource", "redacted", true);
        }
        if (value instanceof Map<?, ?> values)
        {
            Map<String, Object> safe = new LinkedHashMap<>();
            values.forEach((key, child) -> safe.put(String.valueOf(key), traceSafeJavaInput(child)));
            return safe;
        }
        if (value instanceof List<?> values)
        {
            return values.stream().map(this::traceSafeJavaInput).toList();
        }
        return value;
    }

    private Object traceSafeNode(YamlSkillManifest.InputSchemaManifest schema, Object value, String path)
    {
        if (schema == null)
        {
            return value;
        }
        if ("attachment".equals(schema.getType()))
        {
            return attachmentPlaceholder(value, path, schema);
        }
        if ("object".equals(schema.getType()) && value instanceof Map<?, ?> map)
        {
            LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, childValue) ->
            {
                String childName = String.valueOf(key);
                safe.put(childName, traceSafeNode(
                        schema.getProperties().get(childName),
                        childValue,
                        join(path, childName)));
            });
            return Map.copyOf(safe);
        }
        if ("array".equals(schema.getType()) && value instanceof List<?> list && schema.getItems() != null)
        {
            List<Object> safe = new ArrayList<>(list.size());
            for (int index = 0; index < list.size(); index++)
            {
                safe.add(traceSafeNode(schema.getItems(), list.get(index), path + "[" + index + "]"));
            }
            return List.copyOf(safe);
        }
        return value;
    }

    private Map<String, Object> attachmentPlaceholder(Object value,
            String path,
            YamlSkillManifest.InputSchemaManifest schema)
    {
        LinkedHashMap<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("attachment", true);
        placeholder.put("fieldPath", path);
        placeholder.put("mediaType", schema.getMediaType());
        placeholder.put("allowedContentTypes", schema.getAllowedContentTypes());
        if (value instanceof String text && text.matches("^ref://\\S+$"))
        {
            placeholder.put("source", text);
        }
        else if (value instanceof Resource resource)
        {
            placeholder.put("source", "resource");
            placeholder.put("name", resource.getFilename());
        }
        else
        {
            placeholder.put("source", "redacted");
        }
        return Map.copyOf(placeholder);
    }

    private String join(String parent, String child)
    {
        return parent == null || parent.isBlank() ? child : parent + "." + child;
    }

    private YamlSkillDefinition requireYamlSkill(String skillName)
    {
        YamlSkillDefinition definition = yamlSkillCatalog.getSkill(skillName);
        if (definition == null)
        {
            throw new IllegalArgumentException("Unknown YAML skill '" + skillName + "'");
        }
        return definition;
    }

    private CapabilityMetadata requireCapability(String skillName)
    {
        CapabilityMetadata capability = capabilityRegistry.getCapability(skillName);
        if (capability == null)
        {
            throw new IllegalArgumentException("Unknown capability '" + skillName + "'");
        }
        if (!skillName.equals(capability.name()))
        {
            throw new IllegalStateException("CapabilityRegistry returned inconsistent registered skill metadata for '"
                    + skillName + "'");
        }
        return capability;
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
