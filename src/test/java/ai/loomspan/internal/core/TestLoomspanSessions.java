package ai.loomspan.internal.core;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandleFactory;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

import java.time.Clock;
import java.util.function.Supplier;

public final class TestLoomspanSessions {

    private static final Clock DEFAULT_CLOCK = Clock.systemUTC();

    // Low-level tests explicitly own synthetic metadata; production sessions require captured admission facts.
    private static CapturedNamespaceMetadata syntheticMetadata() {
        String id = UUID.randomUUID().toString();
        return new CapturedNamespaceMetadata("default", id, Map.of("default", id));
    }

    private TestLoomspanSessions() {
    }

    public static String entrySkill(LoomspanSession session) { return session.entrySkill(); }

    public static LoomspanSession withId(String sessionId, String entrySkill, int maxDepth) {
        return create(sessionId, entrySkill, maxDepth);
    }

    public static LoomspanSession withId(String sessionId,
                                        String entrySkill,
                                        int maxDepth,
                                        @Nullable Authentication authentication) {
        return create(sessionId, entrySkill, maxDepth, authentication);
    }

    public static LoomspanSession withId(String sessionId,
                                        String entrySkill,
                                        int maxDepth,
                                        @Nullable Authentication authentication,
                                        TracePersistencePolicy persistencePolicy) {
        return create(sessionId, entrySkill, maxDepth, authentication, persistencePolicy);
    }

    public static LoomspanSession withId(String sessionId,
                                        String entrySkill,
                                        int maxDepth,
                                        @Nullable Authentication authentication,
                                        TracePersistencePolicy persistencePolicy,
                                        Clock clock) {
        return create(sessionId, entrySkill, maxDepth, authentication, persistencePolicy, clock);
    }

    public static LoomspanSession withTraceHandle(String sessionId,
                                                  String entrySkill,
                                                  Clock clock,
                                                  ExecutionTraceHandle traceHandle,
                                                  Supplier<String> failureIdSupplier) {
        return create(
                sessionId, entrySkill, 8, null, null, null, null, null,
                TracePersistencePolicy.ALWAYS, clock, NoOpExecutionObservationHandleFactory.INSTANCE,
                (ignoredSessionId, ignoredEntrySkill, ignoredGenerationId, ignoredPolicy, ignoredClock, ignoredObservation) -> traceHandle,
                failureIdSupplier);
    }

    public static LoomspanSession withObservation(String sessionId,
                                                   String entrySkill,
                                                   int maxDepth,
                                                   Clock clock,
                                                   ExecutionObservationHandleFactory observationHandleFactory) {
        return create(
                sessionId, entrySkill, maxDepth, null, TracePersistencePolicy.ALWAYS, clock,
                observationHandleFactory);
    }

    public static LoomspanSession create(int maxDepth, String entrySkill)
    {
        return create(UUID.randomUUID().toString(), entrySkill, maxDepth);
    }

    public static LoomspanSession create(String sessionId, String entrySkill, int maxDepth)
    {
        return create(sessionId, entrySkill, maxDepth, null, null, null, null, null, TracePersistencePolicy.ONERROR, DEFAULT_CLOCK);
    }

    public static LoomspanSession create(int maxDepth, String entrySkill, @Nullable Authentication authentication)
    {
        return create(UUID.randomUUID().toString(), entrySkill, maxDepth, null, null, null, null, authentication, TracePersistencePolicy.ONERROR, DEFAULT_CLOCK);
    }

    public static LoomspanSession create(String sessionId, String entrySkill, int maxDepth, @Nullable Authentication authentication)
    {
        return create(sessionId, entrySkill, maxDepth, null, null, null, null, authentication, TracePersistencePolicy.ONERROR, DEFAULT_CLOCK);
    }

    public static LoomspanSession create(String sessionId,
            String entrySkill,
            int maxDepth,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy)
    {
        return create(sessionId, entrySkill, maxDepth, authentication, persistencePolicy, DEFAULT_CLOCK);
    }

    public static LoomspanSession create(String sessionId,
            String entrySkill,
            int maxDepth,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock)
    {
        return create(sessionId, entrySkill, maxDepth, null, null, null, null, authentication, persistencePolicy, clock);
    }

    public static LoomspanSession create(String sessionId,
            String entrySkill,
            int maxDepth,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory)
    {
        return create(sessionId, entrySkill, maxDepth, null, null, null, null, authentication, persistencePolicy, clock,
                observationHandleFactory,
                DefaultExecutionTraceHandle::new);
    }

    public static LoomspanSession create(String sessionId,
            String entrySkill,
            int maxDepth,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory)
    {
        return create(sessionId, entrySkill, maxDepth, null, null, null, null, authentication, persistencePolicy, clock,
                observationHandleFactory, traceHandleFactory);
    }

    public static LoomspanSession create(
            String sessionId,
            String entrySkill,
            int maxDepth,
            ExecutionPlan executionPlan,
            @Nullable LinterOutcome lastLinterOutcome,
            @Nullable OutputSchemaOutcome lastOutputSchemaOutcome,
            @Nullable SessionUsageSnapshot sessionUsage,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock)
    {
        return create(sessionId, entrySkill, maxDepth, executionPlan, lastLinterOutcome, lastOutputSchemaOutcome,
                sessionUsage, authentication, persistencePolicy, clock,
                NoOpExecutionObservationHandleFactory.INSTANCE,
                DefaultExecutionTraceHandle::new);
    }

    public static LoomspanSession create(
            String sessionId,
            String entrySkill,
            int maxDepth,
            ExecutionPlan executionPlan,
            @Nullable LinterOutcome lastLinterOutcome,
            @Nullable OutputSchemaOutcome lastOutputSchemaOutcome,
            @Nullable SessionUsageSnapshot sessionUsage,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory)
    {
        return create(sessionId, entrySkill, maxDepth, executionPlan, lastLinterOutcome, lastOutputSchemaOutcome,
                sessionUsage, authentication, persistencePolicy, clock, observationHandleFactory,
                DefaultExecutionTraceHandle::new);
    }

    public static LoomspanSession create(
            String sessionId,
            String entrySkill,
            int maxDepth,
            ExecutionPlan executionPlan,
            @Nullable LinterOutcome lastLinterOutcome,
            @Nullable OutputSchemaOutcome lastOutputSchemaOutcome,
            @Nullable SessionUsageSnapshot sessionUsage,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory)
    {
        return create(sessionId, entrySkill, maxDepth, executionPlan, lastLinterOutcome, lastOutputSchemaOutcome,
                sessionUsage, authentication, persistencePolicy, clock, observationHandleFactory, traceHandleFactory,
                () -> UUID.randomUUID().toString());
    }

    public static LoomspanSession create(
            String sessionId,
            String entrySkill,
            int maxDepth,
            ExecutionPlan executionPlan,
            @Nullable LinterOutcome lastLinterOutcome,
            @Nullable OutputSchemaOutcome lastOutputSchemaOutcome,
            @Nullable SessionUsageSnapshot sessionUsage,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory,
            Supplier<String> failureIdSupplier)
    {
        return create(sessionId, entrySkill, maxDepth, executionPlan, lastLinterOutcome, lastOutputSchemaOutcome,
                sessionUsage, authentication, persistencePolicy, clock, observationHandleFactory, traceHandleFactory,
                failureIdSupplier,
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().canonicalTrace());
    }

    public static LoomspanSession create(
            String sessionId,
            String entrySkill,
            int maxDepth,
            ExecutionPlan executionPlan,
            @Nullable LinterOutcome lastLinterOutcome,
            @Nullable OutputSchemaOutcome lastOutputSchemaOutcome,
            @Nullable SessionUsageSnapshot sessionUsage,
            @Nullable Authentication authentication,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory,
            Supplier<String> failureIdSupplier,
            ObjectMapper canonicalTraceMapper)
    {
        return new LoomspanSession(sessionId, entrySkill, syntheticMetadata(), maxDepth, executionPlan,
                lastLinterOutcome, lastOutputSchemaOutcome, sessionUsage, authentication, persistencePolicy,
                clock, observationHandleFactory, traceHandleFactory, failureIdSupplier, canonicalTraceMapper);
    }
}
