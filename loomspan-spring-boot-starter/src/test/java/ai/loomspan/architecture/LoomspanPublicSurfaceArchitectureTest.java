package ai.loomspan.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LoomspanPublicSurfaceArchitectureTest
{
    private static final Set<String> API_TYPES = Set.of(
            "ai.loomspan.api.SkillTemplate",
            "ai.loomspan.api.SkillExecutionView",
            "ai.loomspan.api.SkillExecutionEvent",
            "ai.loomspan.api.SkillMethod",
            "ai.loomspan.api.SkillParam",
            "ai.loomspan.api.SkillException",
            "ai.loomspan.api.SkillInputValidationException",
            "ai.loomspan.api.SkillInputValidationIssue");

    private static final Set<String> FRAMEWORK_INTEGRATION_TYPES = Set.of(
            "ai.loomspan.autoconfigure.LoomspanAutoConfiguration",
            "ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration",
            "ai.loomspan.autoconfigure.LoomspanObservabilityWebAutoConfiguration",
            "ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration",
            "ai.loomspan.autoconfigure.LoomspanProperties",
            "ai.loomspan.autoconfigure.ExecutionTraceProperties",
            "ai.loomspan.autoconfigure.AiDriver");

    private static final Map<String, String> TECHNICALLY_PUBLIC_INTERNAL_TYPES = Map.ofEntries(
            Map.entry("ai.loomspan.internal.release.LoomspanReleaseVersion", "Public only for framework-owned release metadata collaboration."),
            Map.entry("ai.loomspan.internal.observability.ObservabilityActivationCoordinator", "Public only for framework-owned auto-configuration composition."),
            Map.entry("ai.loomspan.internal.observability.ObservabilityRuntime", "Public only for framework-owned adapter composition."),
            Map.entry("ai.loomspan.internal.observability.web.BoundedJsonPageWriter", "Public only for framework-owned bounded REST serialization."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityActivityDelivery", "Public only for framework-owned live-delivery runtime composition."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityArtifactDelivery", "Public only for framework-owned bounded artifact-delivery runtime composition."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityAccessService", "Public only for framework-owned operator authorization."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityApiKeyFilter", "Public only for servlet filter registration by auto-configuration."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityApiPaths", "Public only to keep internal route ownership coherent."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityCursorCodec", "Public only for framework-owned REST continuation encoding."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityDtoMapper", "Public only for framework-owned wire projection."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityJsonCodec", "Public only for framework-owned stable REST and cursor serialization."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityException", "Public only for internal web problem propagation."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityProblem", "Public only for the internal serialized problem boundary."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityProblemMapper", "Public only for framework-owned problem mapping."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityRestController", "Public only for programmatic Spring MVC handler registration."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityRouteCollisionDetector", "Public only for framework-owned namespace inspection."),
            Map.entry("ai.loomspan.internal.observability.web.ObservabilityRouteRegistrar", "Public only for framework-owned programmatic route lifecycle."),
            Map.entry("ai.loomspan.internal.observability.web.dto.ObservabilityDtos", "Public only as internal hand-authored REST boundary DTOs."),
            Map.entry("ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry", "Public only so LoomspanAutoConfiguration can construct this type across the Spring integration boundary."),
            Map.entry("ai.loomspan.internal.autoconfigure.SafeAiConnectionConfigurationException", "Public only so the version-scoped Spring AI integration can report sanitized connection configuration failures."),
            Map.entry("ai.loomspan.internal.provider.AttemptOwnership", "Public only for typed collaboration between the neutral provider policy and version-scoped Spring AI integration."),
            Map.entry("ai.loomspan.internal.provider.ProviderConnectionRuntime", "Public only to carry an internal connection model and its provider retry policy across subsystem packages."),
            Map.entry("ai.loomspan.internal.provider.ProviderFailureCategory", "Public only for typed internal provider failure diagnostics."),
            Map.entry("ai.loomspan.internal.provider.ProviderFailureClassification", "Public only for typed internal provider retry classification."),
            Map.entry("ai.loomspan.internal.provider.ProviderFailureDetails", "Public only to carry sanitized provider failure details between internal integration, retry, and trace packages."),
            Map.entry("ai.loomspan.internal.provider.ProviderFailureGuidance", "Public only to carry bounded framework-owned provider guidance into the internal chat attempt boundary."),
            Map.entry("ai.loomspan.internal.provider.ProviderFailureTranslator", "Public only for the neutral retry advisor to invoke a version-scoped provider failure translator."),
            Map.entry("ai.loomspan.internal.provider.ProviderRetryDecider", "Public only for internal provider retry policy evaluation by the chat advisor."),
            Map.entry("ai.loomspan.internal.provider.ProviderRetryDecision", "Public only to carry a typed internal retry decision into tracing and execution."),
            Map.entry("ai.loomspan.internal.provider.ProviderRetryOutcome", "Public only for typed internal provider attempt trace outcomes."),
            Map.entry("ai.loomspan.internal.provider.ProviderRetryPolicy", "Public only to carry immutable provider retry policy between internal configuration and execution packages."),
            Map.entry("ai.loomspan.internal.provider.RetryDelaySource", "Public only for typed internal retry delay trace metadata."),
            Map.entry("ai.loomspan.internal.springai.SpringAiProviderIntegration", "Public only as the official Spring AI integration boundary used by internal auto-configuration."),
            Map.entry("ai.loomspan.internal.springai.SpringAiChatOptionsContributor", "Public only for framework-owned Spring AI client assembly."),
            Map.entry("ai.loomspan.internal.springai.SpringAiModelInteraction", "Public only to adapt Spring AI behind the neutral model boundary."),
            Map.entry("ai.loomspan.internal.springai.SpringAiModelInteractionFactory", "Public only for framework-owned Spring AI composition."),
            Map.entry("ai.loomspan.internal.model.ModelInteraction", "Public only as the neutral internal model boundary."),
            Map.entry("ai.loomspan.internal.model.ModelInteractionFactory", "Public only as the neutral internal model factory boundary."),
            Map.entry("ai.loomspan.internal.model.ModelInteractionMode", "Public only to select neutral internal interaction assembly."),
            Map.entry("ai.loomspan.internal.model.ModelInteractionRequest", "Public only to carry neutral internal model requests."),
            Map.entry("ai.loomspan.internal.model.ModelInteractionResult", "Public only to carry neutral internal model results."),
            Map.entry("ai.loomspan.internal.serialization.LoomspanJacksonCodecs", "Public only for framework-owned purpose-specific codec composition."),
            Map.entry("ai.loomspan.internal.serialization.LoomspanMethodInputSchemaGenerator", "Public only for framework-owned schema generation across internal composition packages."),
            Map.entry("ai.loomspan.internal.runtime.tool.BoundCapability", "Public only as the neutral bound-capability boundary."),
            Map.entry("ai.loomspan.internal.runtime.tool.CapabilityInvoker", "Public only as the neutral capability invocation boundary."),
            Map.entry("ai.loomspan.internal.chat.ProviderAttemptCallAdvisor", "Public only for framework-owned physical-attempt advisor assembly."),
            Map.entry("ai.loomspan.internal.chat.DefaultSkillAdvisorResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.chat.DefaultSkillChatModelResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.chat.SkillAdvisorResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.chat.SkillChatModelResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.AdvisorTraceContext", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.AdvisorTraceFact", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.AdvisorTraceRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.LoomspanExceptionTransformer", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.LoomspanSession", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.LoomspanSessionRunner", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.LoomspanStackOverflowException", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.CapabilityExecutionRouter", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.CapabilityInvoker", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.CapabilityKind", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.CapabilityMetadata", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.CapabilityRegistry", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.CapabilityToolDescriptor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.DefaultLoomspanExceptionTransformer", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.DefaultExecutionTraceRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionCoordinator", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionBinding", "Public only for immutable execution identity collaboration between internal runtime packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionBindingScope", "Public only for explicit scoped binding at internal runtime boundaries."),
            Map.entry("ai.loomspan.internal.core.ExecutionFrame", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionJournal", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionPlan", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionTrace", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionTraceHandle", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.FinalizedTraceArtifact", "Public only for core-issued finalized artifact collaboration with internal observability."),
            Map.entry("ai.loomspan.internal.core.ExecutionTraceReader", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ExecutionTraceRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.InMemoryCapabilityRegistry", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.JournalEntry", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.JournalEntryType", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.JournalLevel", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.MissionInputMessageFormatter", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.MissionContext", "Public only for typed mission-state ownership across internal runtime packages."),
            Map.entry("ai.loomspan.internal.core.MissionLifecycle", "Public only for mission-bounded cancellation, cutoff, and cleanup collaboration across internal runtime packages."),
            Map.entry("ai.loomspan.internal.core.MissionWriteRevokedException", "Public only as internal cutoff control flow across runtime packages."),
            Map.entry("ai.loomspan.internal.core.ModelExecutionIdentity", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ModelTraceContext", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.step.AssignedTaskExecution", "Public only to carry immutable admitted-task identity into the internal mission lifecycle."),
            Map.entry("ai.loomspan.internal.runtime.step.AssignedTaskOutcome", "Public only to publish immutable task outcomes through the internal mission lifecycle."),
            Map.entry("ai.loomspan.internal.core.TraceOutcome", "Public only for typed Java collaboration between distinct internal trace lifecycle packages."),
            Map.entry("ai.loomspan.internal.core.OperationType", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.PlanStatus", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.PlanTask", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.PlanTaskStatus", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.PhysicalBranchContext", "Public only for typed branch-frame ownership across internal runtime packages."),
            Map.entry("ai.loomspan.internal.core.SkillExecutionDescriptor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.SkillMethodBeanPostProcessor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TaskExecutionEvent", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.ToolTraceContext", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TraceCompletion", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TraceFailureMetadata", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TraceFrameType", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TracePersistencePolicy", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TraceRecord", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.TraceRecordType", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.linter.LinterCallAdvisor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.linter.LinterOutcome", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.linter.LinterOutcomeRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.linter.LinterOutcomeStatus", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaFailureMode", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaOutcome", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaOutcomeRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaPromptAugmentor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaValidationIssue", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaValidationResult", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.outputschema.OutputSchemaValidator", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.attachment.LoomspanAttachment", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.attachment.DefaultMissionInputMaterializer", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.attachment.MissionInputMaterializer", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.attachment.RenderedMissionInput", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.LoomspanMissionTimeoutException", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.LoomspanQuotaExceededException", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.MissionWorkExecutor", "Internal shared mission submission, timeout, and cutoff implementation."),
            Map.entry("ai.loomspan.internal.runtime.DefaultMissionExecutionEngine", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceBackedOutputValidator", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceContract", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceExpression", "Public only for immutable expression collaboration between internal catalog and runtime packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceExpressionParser", "Public only for compile-once expression parsing in the internal catalog."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceRequirement", "Public only for structured current-version evidence diagnostics across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceContractCallAdvisor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceCoverageIssue", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceCoverageResult", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.evidence.EvidenceCoverageValidator", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputContract", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputContractResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputPromptRenderer", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputSchemaNode", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputValidationIssue", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputValidationResult", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.input.SkillInputValidator", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.MissionExecutionEngine", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.planning.DefaultPlanningService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.planning.PlanningService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.prompt.SkillPromptComposer", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.prompt.SkillPromptComposition", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.state.DefaultExecutionStateService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.state.ExecutionStateService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.step.StepLoopMissionExecutionEngine", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.tool.DefaultCapabilityInvoker", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.tool.DefaultToolSurfaceService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.tool.CapabilityBindingFactory", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.tool.ToolSurfaceService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.trace.CompletionGraceRetention", "Public only for core-owned trace retention composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.trace.ConfiguredLimitsSnapshot", "Public only to carry an immutable run-start quota snapshot from internal core wiring into the trace writer."),
            Map.entry("ai.loomspan.internal.runtime.trace.ImmediateCompletionRetention", "Public only for framework-owned disabled trace-retention composition."),
            Map.entry("ai.loomspan.internal.runtime.trace.ScheduledCompletionGraceRetention", "Public only for framework-owned trace-retention lifecycle composition."),
            Map.entry("ai.loomspan.internal.runtime.trace.ExecutionJournalProjector", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ActiveExecutionSnapshot", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ActiveExecutionRegistry", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ActivityReplayBuffer", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.DefaultExecutionObservationHandleFactory", "Public only for framework-owned composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ExecutionActivity", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ExecutionActivityKind", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.LiveActivitySignal", "Public only for the internal observation-to-delivery notification boundary."),
            Map.entry("ai.loomspan.internal.runtime.observation.ExecutionObservationHandle", "Public only for Java collaboration with the internal canonical trace package."),
            Map.entry("ai.loomspan.internal.runtime.observation.ExecutionObservationHandleFactory", "Public only for framework-owned session composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ExecutionObservationLimits", "Public only to keep internal projection and future adapter bounds coherent."),
            Map.entry("ai.loomspan.internal.runtime.observation.InMemoryActiveExecutionRegistry", "Public only for framework-owned composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer", "Public only for framework-owned composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.LiveActivityProjector", "Public only for framework-owned composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandle", "Public only for Java collaboration with the internal canonical trace package."),
            Map.entry("ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory", "Public only for framework-owned disabled observation composition across internal packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.ObservationCompletionDisposition", "Public only for Java collaboration with the internal session finalization authority."),
            Map.entry("ai.loomspan.internal.runtime.observation.ReplayResult", "Public only for Java collaboration between internal observation and future application-adapter packages."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog", "Public only for future framework-owned observability adapter composition."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.FinalizedTraceCatalog", "Public only for internal finalization and future adapter collaboration."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.FinalizedTraceCatalogEntry", "Public only for internal observation and future adapter collaboration."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.InMemoryFinalizedTraceCatalog", "Public only for framework-owned observability composition."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog", "Public only for future internal application-adapter collaboration."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillEntry", "Public only for future internal application-adapter collaboration."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.SkillSourcePathResolver", "Public only for framework-owned registered-skill catalog construction."),
            Map.entry("ai.loomspan.internal.runtime.observation.catalog.TraceCatalogSlice", "Public only for future internal keyset adapter collaboration."),
            Map.entry("ai.loomspan.internal.runtime.usage.DefaultSessionUsageService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.GuardrailType", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.MicrometerUsageMetricsRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.ModelUsageExtractor", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.ModelUsageRecord", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.NoOpSessionUsageService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.NoOpUsageMetricsRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.SessionUsageService", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.SessionUsageSnapshot", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.UsageMetricsRecorder", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.runtime.usage.UsagePrecision", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.security.AccessGuard", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.core.SkillSource", "Public only for internal registration, authorization and execution coordination; no supported SPI."),
            Map.entry("ai.loomspan.internal.security.SkillAccessPolicy", "Public only for internal registration, authorization and execution coordination; no supported SPI."),
            Map.entry("ai.loomspan.internal.security.SkillAccessPolicyResolver", "Public only for internal registration, authorization and execution coordination; no supported SPI."),
            Map.entry("ai.loomspan.internal.security.SkillRoleEvaluator", "Public only for internal registration, authorization and execution coordination; no supported SPI."),
            Map.entry("ai.loomspan.internal.security.ScopedAuthentication", "Public only for internal registration, authorization and execution coordination; no supported SPI."),
            Map.entry("ai.loomspan.internal.security.Jsr250EnforcementVerifier", "Public only for internal registration, authorization and execution coordination; no supported SPI."),
            Map.entry("ai.loomspan.internal.security.DefaultAccessGuard", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.DefaultSkillVisibilityResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.AllowedSkillConstraint", "Public only for immutable Java collaboration between internal catalog and planning packages."),
            Map.entry("ai.loomspan.internal.skill.SkillVisibilityResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.YamlSkillCatalog", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.YamlSkillDefinition", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skill.YamlSkillSource", "Public only for immutable startup-source collaboration with the internal observability catalog."),
            Map.entry("ai.loomspan.internal.skill.YamlSkillManifest", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.skillapi.DefaultSkillTemplate", "Public only so LoomspanAutoConfiguration can construct the application facade implementation."),
            Map.entry("ai.loomspan.internal.vfs.DefaultRefResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.vfs.RefResolver", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.vfs.SessionLocalVirtualFileSystem", "Public only for Java collaboration between distinct internal subsystem packages."),
            Map.entry("ai.loomspan.internal.vfs.VirtualFileSystem", "Public only for Java collaboration between distinct internal subsystem packages."));

    private final Set<JavaClass> productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ai.loomspan")
            .stream()
            .filter(javaClass -> !javaClass.isNestedClass())
            .collect(Collectors.toSet());

    @Test
    void apiPackageContainsExactlyEightApprovedPublicTypes()
    {
        assertThat(publicTopLevelTypesIn("ai.loomspan.api"))
                .containsExactlyInAnyOrderElementsOf(API_TYPES);
    }

    @Test
    void autoconfigurePackageContainsExactlySevenIntegrationTypes()
    {
        assertThat(publicTopLevelTypesIn("ai.loomspan.autoconfigure"))
                .containsExactlyInAnyOrderElementsOf(FRAMEWORK_INTEGRATION_TYPES);
    }

    @Test
    void everyExternallyAccessibleTopLevelTypeIsClassified()
    {
        Set<String> exposed = productionClasses.stream()
                .filter(this::isPublic)
                .map(JavaClass::getName)
                .filter(name -> !name.startsWith("ai.loomspan.internal."))
                .collect(Collectors.toSet());

        assertThat(exposed)
                .as("Every externally accessible top-level type must be in the closed API or framework-integration allowlist")
                .containsExactlyInAnyOrderElementsOf(Stream.concat(
                                API_TYPES.stream(), FRAMEWORK_INTEGRATION_TYPES.stream())
                        .collect(Collectors.toSet()));
    }

    @Test
    void technicallyPublicInternalTypesHaveNonblankReasons()
    {
        Set<String> actual = productionClasses.stream()
                .filter(this::isPublic)
                .map(JavaClass::getName)
                .filter(name -> name.startsWith("ai.loomspan.internal."))
                .collect(Collectors.toSet());

        assertThat(actual)
                .as("Every technically public internal type must be deliberately allowlisted")
                .containsExactlyInAnyOrderElementsOf(TECHNICALLY_PUBLIC_INTERNAL_TYPES.keySet());
        assertThat(TECHNICALLY_PUBLIC_INTERNAL_TYPES)
                .allSatisfy((name, reason) -> assertThat(reason)
                        .as("classification reason for %s", name)
                        .isNotBlank());
    }

    @Test
    void noSupportedSpiPackageOrTypeExists()
    {
        assertThat(productionClasses.stream().map(JavaClass::getPackageName))
                .noneMatch(packageName -> packageName.contains(".spi"));
    }

    @Test
    void apiSignaturesRecursivelyExcludeInternalAndAutoconfigureTypes() throws Exception
    {
        for (String typeName : API_TYPES)
        {
            Class<?> apiType = Class.forName(typeName);
            assertAnnotationsAreApiSafe(apiType, apiType.getName());
            assertApiSafe(apiType.getGenericSuperclass(), apiType.getName() + " superclass", new LinkedHashSet<>());
            for (Type interfaceType : apiType.getGenericInterfaces())
            {
                assertApiSafe(interfaceType, apiType.getName() + " interface", new LinkedHashSet<>());
            }

            for (var field : apiType.getDeclaredFields())
            {
                if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers()))
                {
                    assertApiSafe(field.getGenericType(), field.toString(), new LinkedHashSet<>());
                    assertAnnotationsAreApiSafe(field, field.toString());
                }
            }
            for (var constructor : apiType.getDeclaredConstructors())
            {
                if (Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers()))
                {
                    assertExecutableIsApiSafe(constructor, constructor.toString());
                }
            }
            for (var method : apiType.getDeclaredMethods())
            {
                if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers()))
                {
                    assertApiSafe(method.getGenericReturnType(), method.toString(), new LinkedHashSet<>());
                    assertExecutableIsApiSafe(method, method.toString());
                }
            }
            if (apiType.isRecord())
            {
                for (var component : apiType.getRecordComponents())
                {
                    assertApiSafe(component.getGenericType(), component.toString(), new LinkedHashSet<>());
                    assertAnnotationsAreApiSafe(component, component.toString());
                }
            }
        }
    }

    @Test
    void observationDtosExposeOnlyBoundedImmutableDomainTypes() throws Exception
    {
        Set<Class<?>> forbidden = Set.of(
                java.nio.file.Path.class,
                org.springframework.core.io.Resource.class,
                tools.jackson.databind.JsonNode.class,
                ai.loomspan.internal.core.TraceRecord.class,
                Throwable.class,
                java.util.stream.Stream.class,
                java.util.concurrent.Flow.Publisher.class);

        for (Class<?> dto : List.of(
                ai.loomspan.internal.runtime.observation.ActiveExecutionSnapshot.class,
                ai.loomspan.internal.runtime.observation.ExecutionActivity.class))
        {
            for (var component : dto.getRecordComponents())
            {
                Class<?> rawType = component.getType();
                assertThat(forbidden)
                        .as("%s component %s", dto.getSimpleName(), component.getName())
                        .noneMatch(type -> type.isAssignableFrom(rawType) || rawType.isAssignableFrom(type));
            }
        }
    }

    @Test
    void observabilityWireDtosDoNotEmbedRuntimeUsageTypes()
    {
        for (Class<?> dto : ai.loomspan.internal.observability.web.dto.ObservabilityDtos.class
                .getDeclaredClasses())
        {
            if (!dto.isRecord())
            {
                continue;
            }
            for (var component : dto.getRecordComponents())
            {
                assertThat(component.getType())
                        .as("%s component %s", dto.getSimpleName(), component.getName())
                        .isNotEqualTo(ai.loomspan.internal.runtime.usage.SessionUsageSnapshot.class);
            }
        }
    }

    private Set<String> publicTopLevelTypesIn(String packageName)
    {
        return productionClasses.stream()
                .filter(this::isPublic)
                .filter(javaClass -> javaClass.getPackageName().equals(packageName))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
    }

    private boolean isPublic(JavaClass javaClass)
    {
        return javaClass.getModifiers().contains(JavaModifier.PUBLIC);
    }

    private void assertExecutableIsApiSafe(java.lang.reflect.Executable executable, String owner)
    {
        for (Type parameter : executable.getGenericParameterTypes())
        {
            assertApiSafe(parameter, owner, new LinkedHashSet<>());
        }
        for (Type exception : executable.getGenericExceptionTypes())
        {
            assertApiSafe(exception, owner, new LinkedHashSet<>());
        }
        assertAnnotationsAreApiSafe(executable, owner);
        Arrays.stream(executable.getParameterAnnotations())
                .flatMap(Arrays::stream)
                .map(Annotation::annotationType)
                .forEach(type -> assertClassIsApiSafe(type, owner));
    }

    private void assertApiSafe(Type type, String owner, Set<Type> visited)
    {
        if (type == null || !visited.add(type))
        {
            return;
        }
        if (type instanceof Class<?> clazz)
        {
            assertClassIsApiSafe(clazz, owner);
            if (clazz.isArray())
            {
                assertApiSafe(clazz.getComponentType(), owner, visited);
            }
        }
        else if (type instanceof ParameterizedType parameterized)
        {
            assertApiSafe(parameterized.getRawType(), owner, visited);
            assertApiSafe(parameterized.getOwnerType(), owner, visited);
            for (Type argument : parameterized.getActualTypeArguments())
            {
                assertApiSafe(argument, owner, visited);
            }
        }
        else if (type instanceof GenericArrayType array)
        {
            assertApiSafe(array.getGenericComponentType(), owner, visited);
        }
        else if (type instanceof WildcardType wildcard)
        {
            Stream.concat(Arrays.stream(wildcard.getUpperBounds()), Arrays.stream(wildcard.getLowerBounds()))
                    .forEach(bound -> assertApiSafe(bound, owner, visited));
        }
        else if (type instanceof TypeVariable<?> variable)
        {
            Arrays.stream(variable.getBounds()).forEach(bound -> assertApiSafe(bound, owner, visited));
        }
    }

    private void assertAnnotationsAreApiSafe(AnnotatedElement element, String owner)
    {
        Arrays.stream(element.getAnnotations())
                .map(Annotation::annotationType)
                .forEach(type -> assertClassIsApiSafe(type, owner));
    }

    private void assertClassIsApiSafe(Class<?> type, String owner)
    {
        Class<?> inspected = type.isArray() ? type.getComponentType() : type;
        if (inspected.getName().startsWith("ai.loomspan."))
        {
            assertThat(inspected.getPackageName())
                    .as("Public API signature %s leaks Loomspan type %s", owner, inspected.getName())
                    .isEqualTo("ai.loomspan.api");
        }
    }
}
