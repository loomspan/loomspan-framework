package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.CapturedNamespaceMetadata;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandle;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandle;
import org.springframework.lang.Nullable;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Explicit synthetic trace construction for low-level tests, never a production admission fallback. */
public final class TestExecutionTraceHandles {
    private TestExecutionTraceHandles() {}
    private static String newTraceId() { return UUID.randomUUID().toString(); }

    public static DefaultExecutionTraceHandle create(String sessionId, String entrySkill, TracePersistencePolicy persistencePolicy, Clock clock)
    {
        DefaultExecutionTraceHandle handle = create(newTraceId(), sessionId, entrySkill, null, persistencePolicy, false, false, clock, 0L, false,
                TestExecutionTraceHandles::newTraceId, null, null, NoOpExecutionObservationHandle.INSTANCE, null);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String sessionId,
            String entrySkill,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandle observationHandle)
    {
        DefaultExecutionTraceHandle handle = create(newTraceId(), sessionId, entrySkill, null, persistencePolicy, false, false, clock, 0L, false,
                TestExecutionTraceHandles::newTraceId, null, null, observationHandle, null);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            Supplier<String> idSupplier,
            String threadName,
            String tracePathMetadata)
    {
        DefaultExecutionTraceHandle handle = create(traceId, sessionId, entrySkill, tracePath, persistencePolicy, false, false, clock, 0L, false,
                idSupplier, threadName, tracePathMetadata, NoOpExecutionObservationHandle.INSTANCE, null);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            Supplier<String> idSupplier,
            String threadName,
            String tracePathMetadata,
            ConfiguredLimitsSnapshot configuredLimits)
    {
        DefaultExecutionTraceHandle handle = create(traceId, sessionId, entrySkill, tracePath, persistencePolicy, false, false, clock, 0L, false,
                idSupplier, threadName, tracePathMetadata, NoOpExecutionObservationHandle.INSTANCE,
                Objects.requireNonNull(configuredLimits, "configuredLimits must not be null"));
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            Supplier<String> idSupplier,
            String threadName,
            String tracePathMetadata,
            ExecutionObservationHandle observationHandle)
    {
        DefaultExecutionTraceHandle handle = create(traceId, sessionId, entrySkill, tracePath, persistencePolicy, false, false, clock, 0L, false,
                idSupplier, threadName, tracePathMetadata, observationHandle, null);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            Supplier<String> idSupplier,
            String threadName,
            String tracePathMetadata,
            ExecutionObservationHandle observationHandle,
            TraceRecordWriter writer)
    {
        DefaultExecutionTraceHandle handle = create(traceId, sessionId, entrySkill, tracePath, persistencePolicy, false, false, clock, 0L, false,
                idSupplier, threadName, tracePathMetadata, observationHandle, null, writer,
                ImmediateCompletionRetention.INSTANCE);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            boolean errored,
            boolean completed,
            Clock clock,
            long startingSequence,
            boolean initialized,
            Supplier<String> idSupplier,
            @Nullable String threadName,
            @Nullable String tracePathMetadata,
            ExecutionObservationHandle observationHandle,
            @Nullable ConfiguredLimitsSnapshot configuredLimits)
    {
        return create(traceId, sessionId, entrySkill, tracePath, persistencePolicy, errored, completed, clock, startingSequence,
                initialized, idSupplier, threadName, tracePathMetadata, observationHandle, configuredLimits, null,
                ImmediateCompletionRetention.INSTANCE);
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            boolean errored,
            boolean completed,
            Clock clock,
            long startingSequence,
            boolean initialized,
            Supplier<String> idSupplier,
            @Nullable String threadName,
            @Nullable String tracePathMetadata,
            ExecutionObservationHandle observationHandle,
            @Nullable ConfiguredLimitsSnapshot configuredLimits,
            @Nullable TraceRecordWriter writer,
            CompletionGraceRetention completionGraceRetention)
    {
        return create(traceId, sessionId, entrySkill, tracePath, persistencePolicy, errored, completed, clock,
                startingSequence, initialized, idSupplier, threadName, tracePathMetadata, observationHandle,
                configuredLimits, writer, completionGraceRetention, null);
    }

    public static DefaultExecutionTraceHandle create(
            String traceId,
            String sessionId,
            String entrySkill,
            Path tracePath,
            TracePersistencePolicy persistencePolicy,
            boolean errored,
            boolean completed,
            Clock clock,
            long startingSequence,
            boolean initialized,
            Supplier<String> idSupplier,
            @Nullable String threadName,
            @Nullable String tracePathMetadata,
            ExecutionObservationHandle observationHandle,
            @Nullable ConfiguredLimitsSnapshot configuredLimits,
            @Nullable TraceRecordWriter writer,
            CompletionGraceRetention completionGraceRetention,
            @Nullable ObjectMapper canonicalTraceMapper)
    {
        return new DefaultExecutionTraceHandle(traceId, sessionId, entrySkill, new CapturedNamespaceMetadata("default", traceId, Map.of("default", traceId)), tracePath, persistencePolicy,
                errored, completed, clock, startingSequence, initialized, idSupplier, threadName,
                tracePathMetadata, observationHandle, configuredLimits, writer, completionGraceRetention,
                canonicalTraceMapper);
    }

    public static DefaultExecutionTraceHandle create(
            String sessionId,
            String entrySkill,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandle observationHandle,
            CompletionGraceRetention completionGraceRetention)
    {
        DefaultExecutionTraceHandle handle = create(newTraceId(), sessionId, entrySkill, null, persistencePolicy, false, false, clock, 0L, false,
                TestExecutionTraceHandles::newTraceId, null, null, observationHandle, null, null,
                completionGraceRetention);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String sessionId,
            String entrySkill,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandle observationHandle,
            CompletionGraceRetention completionGraceRetention,
            ConfiguredLimitsSnapshot configuredLimits,
            ObjectMapper canonicalTraceMapper)
    {
        DefaultExecutionTraceHandle handle = create(newTraceId(), sessionId, entrySkill, null, persistencePolicy, false, false, clock, 0L, false,
                TestExecutionTraceHandles::newTraceId, null, null, observationHandle,
                Objects.requireNonNull(configuredLimits, "configuredLimits must not be null"), null,
                completionGraceRetention, Objects.requireNonNull(canonicalTraceMapper,
                        "canonicalTraceMapper must not be null"));
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }

    public static DefaultExecutionTraceHandle create(
            String sessionId,
            String entrySkill,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandle observationHandle,
            CompletionGraceRetention completionGraceRetention,
            ConfiguredLimitsSnapshot configuredLimits)
    {
        DefaultExecutionTraceHandle handle = create(newTraceId(), sessionId, entrySkill, null, persistencePolicy, false, false, clock, 0L, false,
                TestExecutionTraceHandles::newTraceId, null, null, observationHandle,
                Objects.requireNonNull(configuredLimits, "configuredLimits must not be null"), null,
                completionGraceRetention);
        handle.resetTraceFile();
        handle.initialize();

        return handle;
    }
}
