package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionFactory;
import ai.loomspan.internal.model.ModelInteractionMode;
import ai.loomspan.internal.outputschema.LoomspanOutputSchemaValidationException;
import ai.loomspan.internal.outputschema.OutputSchemaValidator;
import ai.loomspan.internal.outputvalidation.OutputValidationPolicy;
import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.MissionWorkExecutor;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.tool.CapabilityBindingFactory;
import ai.loomspan.internal.runtime.tool.ToolSurfaceService;
import ai.loomspan.internal.security.AccessGuard;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

public class ExecutionCoordinator
{
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
    private final OutputSchemaValidator outputSchemaValidator = new OutputSchemaValidator(
            ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());

    public ExecutionCoordinator(ModelInteractionFactory modelInteractionFactory,
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
        ExecutionBinding baseBinding = ExecutionBindingScope.requireCurrent();
        CapabilityMetadata rootCapability = requireCapability(baseBinding, skillName);
        return execute(rootCapability, objective, missionInput, session, authentication);
    }

    public String execute(CapabilityMetadata rootCapability,
            String objective,
            @Nullable Map<String, Object> missionInput,
            LoomspanSession session,
            @Nullable Authentication authentication)
    {
        Objects.requireNonNull(rootCapability, "rootCapability must not be null");
        Objects.requireNonNull(session, "session must not be null");
        requireNonBlank(objective, "objective");
        ExecutionBinding baseBinding = ExecutionBindingScope.requireCurrent();
        if (baseBinding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        var targetGeneration = baseBinding.snapshot().generation(rootCapability.identity().namespace());
        if (!targetGeneration.owns(rootCapability))
            throw new IllegalArgumentException("Capability '" + rootCapability.name()
                    + "' does not belong to the current skill generation");
        accessGuard.checkAccess(rootCapability, session, authentication);
        YamlSkillDefinition definition = targetGeneration.definition(rootCapability.name());
        if (rootCapability.kind() == SkillKind.YAML && definition == null)
            throw new IllegalArgumentException("Unknown captured YAML skill '" + rootCapability.name() + "'");
        MissionContext parentMission = baseBinding.mission();
        boolean topLevelInvocation = parentMission == null;
        if (topLevelInvocation && !rootCapability.name().equals(session.entrySkill()))
        {
            throw new IllegalArgumentException("Top-level capability name does not match session entry skill");
        }

        MissionContext mission = new MissionContext(session, rootCapability.name(), UUID.randomUUID().toString(), parentMission);
        mission.captureInput(missionInput == null ? Map.of() : missionInput);
        ExecutionBinding missionBinding = baseBinding.withTarget(rootCapability, mission);
        try
        {
            return ExecutionBindingScope.supplyWith(missionBinding, () -> executeBound(
                    definition, rootCapability, objective, missionInput, session, authentication,
                    mission, baseBinding, topLevelInvocation));
        }
        finally
        {
            mission.lifecycle().closeNow();
        }
    }

    private String executeBound(YamlSkillDefinition definition,
            CapabilityMetadata rootCapability,
            String objective,
            @Nullable Map<String, Object> missionInput,
            LoomspanSession session,
            @Nullable Authentication authentication,
            MissionContext mission,
            ExecutionBinding parentBinding,
            boolean topLevelInvocation)
    {
        String skillName = rootCapability.name();
        MissionContext parentMission = parentBinding.mission();

        LinkedHashMap<String, Object> frameParameters = new LinkedHashMap<>();
        frameParameters.put("objective", objective);
        if (definition != null && definition.form() == YamlSkillDefinition.Form.EXECUTOR)
        {
            var executor = parentBinding.snapshot().executorBinding(skillName);
            frameParameters.put("executorName", executor.registration().name());
            frameParameters.put("executorConfigGenerationId", executor.generationId());
        }

        if (missionInput != null && !missionInput.isEmpty())
        {
            // Trace recording serializes the original input before reference resolution.
            if (definition != null && definition.form() == YamlSkillDefinition.Form.EXECUTOR)
                definition.validateTransformInput(missionInput);
            frameParameters.put("missionInput", definition == null
                    ? traceSafeJavaInput(missionInput)
                    : traceSafeMissionInput(definition, missionInput));
        }

        ExecutionFrame frame = executionStateService.openMissionFrame(session, rootCapability.name(), Map.copyOf(frameParameters));
        Throwable failure = null;
        String terminalFailureId = null;

        try
        {
            if (rootCapability.kind() == SkillKind.JAVA)
            {
                Authentication caller = accessGuard.resolveAuthentication(authentication, session);
                return missionWorkExecutor.execute(session, skillName, () -> {
                    try (var ignored = scopedAuthentication.open(caller))
                    {
                        var resolved = refResolver.resolveArguments(missionInput == null ? Map.of() : missionInput,
                                session, rootCapability.inputContract());
                        if (definition != null && definition.form() == YamlSkillDefinition.Form.EXECUTOR) {
                            var executorInput = definition.executorInput(resolved);
                            var capturedBinding = ExecutionBindingScope.requireCurrent();
                            if (definition.hasInputTransform()) mission.lifecycle().requireOpenForNewWork(capturedBinding);
                            var binding = parentBinding.snapshot().executorBinding(skillName);
                            var diagnostics = new DefaultExecutorDiagnostics(capturedBinding, frame);
                            String rawResult;
                            try
                            {
                                rawResult = binding.registration().invoker().apply(ExecutorInvocations.create(
                                        executorInput, definition.config(), binding.configuration(), skillName, binding.generationId(),
                                        mission.lifecycle()::isCancelled, mission.lifecycle()::remainingExecutionTime, diagnostics));
                            }
                            finally { diagnostics.close(); }
                            return publishExecutorResult(definition, capturedBinding, frame, rawResult, topLevelInvocation);
                        }
                        return String.valueOf(rootCapability.invoker().invoke(resolved));
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
            Optional<MissionLifecycle.PrimaryCancellation> primaryCancellation =
                    mission.lifecycle().primaryCancellation();
            if (primaryCancellation.isPresent())
            {
                terminalFailureId = primaryCancellation.orElseThrow().failureId();
                if (terminalFailureId == null)
                {
                    try
                    {
                        terminalFailureId = recordFrameworkCancellationFailure(
                                session, mission, ex, errorPayload(skillName, objective, ex));
                    }
                    catch (RuntimeException recordingFailure)
                    {
                        if (recordingFailure != ex) ex.addSuppressed(recordingFailure);
                    }
                }
            }
            else
            {
                terminalFailureId = executionStateService.recordFailure(
                        session, ex, errorPayload(skillName, objective, ex));
            }
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
            finally
            {
                mission.lifecycle().closeNow();
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

    private String publishExecutorResult(YamlSkillDefinition definition, ExecutionBinding binding,
            ExecutionFrame frame, String rawResult, boolean topLevelInvocation)
    {
        String published = rawResult;
        var completion = new LinkedHashMap<String, Object>();
        completion.put("executorOutput", rawResult);
        if (definition.hasOutputTransform())
        {
            try
            {
                published = definition.executorOutput(rawResult);
            }
            catch (RuntimeException ex)
            {
                completion.put("outputTransformStatus", "failed");
                completion.put("outputTransformFailure", ex.getMessage());
                recordExecutorCompletion(binding, frame, completion);
                throw ex;
            }
            completion.put("outputTransformStatus", "succeeded");
        }
        if (definition.outputSchema() != null)
        {
            recordExecutorCompletion(binding, frame, completion);
            var result = outputSchemaValidator.validate(published, definition.outputSchema());
            var outcome = OutputValidationPolicy.schemaOutcome(definition.identity().toString(),
                    1, 0, result.valid(), result.failureMode(), result.issues(), 4);
            try
            {
                executionStateService.recordOutputSchemaOutcome(binding.session(), outcome);
            }
            catch (RuntimeException ignoredFailure) { /* Schema acceptance does not depend on diagnostic writes. */ }
            if (!result.valid())
                throw new LoomspanOutputSchemaValidationException(definition.identity().toString(), published,
                        result.issues(), 1, 0, result.failureMode());
            recordExecutorCompletion(binding, frame, Map.of("skillOutput", published));
        }
        else if (definition.hasOutputTransform())
        {
            completion.put("skillOutput", published);
            recordExecutorCompletion(binding, frame, completion);
        }
        else if (topLevelInvocation) recordExecutorCompletion(binding, frame, completion);
        return published;
    }

    private void recordExecutorCompletion(ExecutionBinding binding, ExecutionFrame frame, Map<String, Object> data)
    {
        try
        {
            binding.runIfWritable(() -> binding.session().appendTraceRecord(
                    TraceRecordType.FRAME_METADATA_RECORDED, frame, Map.of(), data));
        }
        catch (RuntimeException ignoredFailure) { /* Completion evidence is best effort. */ }
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
                    || current instanceof FrameworkShutdownException
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
            if (failureId != null) metadata.put("failureId", failureId);
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

    private String recordFrameworkCancellationFailure(LoomspanSession session,
            MissionContext mission,
            Throwable failure,
            Map<String, Object> payload)
    {
        MissionLifecycle.Cutoff cutoff = mission.lifecycle().cutoff().orElseThrow(
                () -> new IllegalStateException("Framework cancellation did not establish mission cutoff"));
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        String[] failureId = {null};
        boolean recorded = cutoff.runIfPermitted(binding, () -> failureId[0] = session.recordFailure(
                failure, payload, binding.branch().leaf().orElse(null)));
        if (!recorded || failureId[0] == null)
        {
            throw new IllegalStateException("Framework cancellation failure could not be recorded");
        }
        return failureId[0];
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
        return traceSafeNode(definition.inputSchema().tree(), value, "");
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

    private Object traceSafeNode(tools.jackson.databind.JsonNode schema, Object value, String path)
    {
        if (schema == null || value == null)
        {
            return value;
        }
        if (ai.loomspan.internal.runtime.attachment.AttachmentSchemaNodes.isAttachment(schema))
        {
            return attachmentPlaceholder(value, path, schema);
        }
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, childValue) ->
            {
                String childName = String.valueOf(key);
                safe.put(childName, traceSafeNode(
                        ai.loomspan.internal.runtime.attachment.AttachmentSchemaNodes.property(schema, childName),
                        childValue,
                        ai.loomspan.internal.runtime.attachment.AttachmentSchemaNodes.child(path, childName)));
            });
            return Collections.unmodifiableMap(safe);
        }
        if (value instanceof List<?> list && schema.has("items"))
        {
            List<Object> safe = new ArrayList<>(list.size());
            for (int index = 0; index < list.size(); index++)
            {
                safe.add(traceSafeNode(schema.get("items"), list.get(index), ai.loomspan.internal.runtime.attachment.AttachmentSchemaNodes.child(path, Integer.toString(index))));
            }
            return Collections.unmodifiableList(safe);
        }
        return value;
    }

    private Object attachmentPlaceholder(Object value,
            String path,
            tools.jackson.databind.JsonNode schema)
    {
        LinkedHashMap<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("attachment", true);
        placeholder.put("fieldPath", path);
        placeholder.put("mediaType", schema.path("x-loomspan-attachment").path("media_type").asText());
        placeholder.put("allowedContentTypes", schema.path("x-loomspan-attachment").path("allowed_content_types"));
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
            return value;
        }
        return ai.loomspan.internal.util.ImmutableContainers.copyMap(placeholder);
    }

    private CapabilityMetadata requireCapability(ExecutionBinding binding, String skillName)
    {
        return binding.snapshot().resolve(SkillIdentity.parse(skillName));
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
