package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.observation.ExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import ai.loomspan.internal.runtime.trace.ConfiguredLimitsSnapshot;
import ai.loomspan.autoconfigure.LoomspanProperties;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BiFunction;
import tools.jackson.databind.ObjectMapper;

public class LoomspanSessionRunner
{
    private final int maxDepth;
    private final TracePersistencePolicy tracePersistencePolicy;
    private final Clock clock;
    private final ExecutionObservationHandleFactory observationHandleFactory;
    private final InternalExecutionTraceHandleFactory traceHandleFactory;
    private final ObjectMapper canonicalTraceMapper;
    private final @Nullable FrameworkExecutionLifecycle frameworkLifecycle;

    public LoomspanSessionRunner(int maxDepth)
    {
        this(maxDepth, TracePersistencePolicy.ONERROR, Clock.systemUTC());
    }

    public LoomspanSessionRunner(int maxDepth, TracePersistencePolicy tracePersistencePolicy)
    {
        this(maxDepth, tracePersistencePolicy, Clock.systemUTC());
    }

    public LoomspanSessionRunner(int maxDepth, TracePersistencePolicy tracePersistencePolicy, Clock clock)
    {
        this(maxDepth, tracePersistencePolicy, clock, NoOpExecutionObservationHandleFactory.INSTANCE);
    }

    public LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory,
                ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle::new);
    }

    public LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            CompletionGraceRetention completionGraceRetention)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory,
                (sessionId, entrySkill, policy, handleClock, observationHandle) ->
                        new ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle(
                                sessionId,
                                entrySkill,
                                policy,
                                handleClock,
                                observationHandle,
                                Objects.requireNonNull(
                                        completionGraceRetention,
                                        "completionGraceRetention must not be null")));
    }

    public LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            CompletionGraceRetention completionGraceRetention,
            LoomspanProperties.Session.Quotas quotas)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory,
                (sessionId, entrySkill, policy, handleClock, observationHandle) ->
                        new ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle(
                                sessionId,
                                entrySkill,
                                policy,
                                handleClock,
                                observationHandle,
                                Objects.requireNonNull(
                                        completionGraceRetention,
                                        "completionGraceRetention must not be null"),
                                ConfiguredLimitsSnapshot.from(quotas)));
    }

    public LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            CompletionGraceRetention completionGraceRetention,
            LoomspanProperties.Session.Quotas quotas,
            ObjectMapper canonicalTraceMapper)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory,
                (sessionId, entrySkill, policy, handleClock, observationHandle) ->
                        new ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle(
                                sessionId, entrySkill, policy, handleClock, observationHandle,
                                Objects.requireNonNull(completionGraceRetention,
                                        "completionGraceRetention must not be null"),
                                ConfiguredLimitsSnapshot.from(quotas),
                                Objects.requireNonNull(canonicalTraceMapper,
                                        "canonicalTraceMapper must not be null")), canonicalTraceMapper);
    }

    LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory, traceHandleFactory,
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().canonicalTrace(), null);
    }

    private LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory,
            ObjectMapper canonicalTraceMapper)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory, traceHandleFactory,
                canonicalTraceMapper, null);
    }

    private LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            InternalExecutionTraceHandleFactory traceHandleFactory,
            ObjectMapper canonicalTraceMapper,
            @Nullable FrameworkExecutionLifecycle frameworkLifecycle)
    {
        if (maxDepth <= 0)
        {
            throw new IllegalArgumentException("maxDepth must be greater than zero");
        }

        this.maxDepth = maxDepth;
        this.tracePersistencePolicy = tracePersistencePolicy == null ? TracePersistencePolicy.ONERROR : tracePersistencePolicy;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.observationHandleFactory = Objects.requireNonNull(
                observationHandleFactory, "observationHandleFactory must not be null");
        this.traceHandleFactory = Objects.requireNonNull(traceHandleFactory, "traceHandleFactory must not be null");
        this.canonicalTraceMapper = Objects.requireNonNull(canonicalTraceMapper,
                "canonicalTraceMapper must not be null");
        this.frameworkLifecycle = frameworkLifecycle;
    }

    public LoomspanSessionRunner(
            int maxDepth,
            TracePersistencePolicy tracePersistencePolicy,
            Clock clock,
            ExecutionObservationHandleFactory observationHandleFactory,
            CompletionGraceRetention completionGraceRetention,
            LoomspanProperties.Session.Quotas quotas,
            ObjectMapper canonicalTraceMapper,
            FrameworkExecutionLifecycle frameworkLifecycle)
    {
        this(maxDepth, tracePersistencePolicy, clock, observationHandleFactory,
                (sessionId, entrySkill, policy, handleClock, observationHandle) ->
                        new ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle(
                                sessionId, entrySkill, policy, handleClock, observationHandle,
                                Objects.requireNonNull(completionGraceRetention,
                                        "completionGraceRetention must not be null"),
                                ConfiguredLimitsSnapshot.from(quotas),
                                Objects.requireNonNull(canonicalTraceMapper,
                                        "canonicalTraceMapper must not be null")),
                canonicalTraceMapper, Objects.requireNonNull(frameworkLifecycle));
    }

    public void runWithNewSession(String entrySkill, Consumer<LoomspanSession> action)
    {
        runWithNewSession(entrySkill, null, action);
    }

    public void runWithNewSession(String entrySkill, @Nullable Authentication authentication, Consumer<LoomspanSession> action)
    {
        Objects.requireNonNull(action, "action must not be null");
        executeRoot(entrySkill, authentication, session -> { action.accept(session); return null; },
                (ignored, session) -> null);
    }

    public <T> T callWithNewSession(String entrySkill, Function<LoomspanSession, T> action)
    {
        return callWithNewSession(entrySkill, null, action);
    }

    public <T> T callWithNewSession(String entrySkill, @Nullable Authentication authentication, Function<LoomspanSession, T> action)
    {
        return callWithNewSession(entrySkill, authentication, action, (result, session) -> result);
    }

    public <T, R> R callWithNewSession(String entrySkill, @Nullable Authentication authentication,
            Function<LoomspanSession, T> action, BiFunction<T, LoomspanSession, R> completion)
    {
        return executeRoot(entrySkill, authentication, action, completion);
    }

    private <T, R> R executeRoot(String entrySkill, @Nullable Authentication authentication,
            Function<LoomspanSession, T> action, BiFunction<T, LoomspanSession, R> completion)
    {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(completion, "completion must not be null");
        FrameworkExecutionLifecycle.AdmittedRoot root = frameworkLifecycle == null
                ? null : frameworkLifecycle.admitRoot();
        try
        {
            LoomspanSession session = new LoomspanSession(
                UUID.randomUUID().toString(),
                entrySkill,
                maxDepth,
                null,
                null,
                null,
                null,
                authentication,
                tracePersistencePolicy,
                clock,
                observationHandleFactory,
                traceHandleFactory,
                () -> UUID.randomUUID().toString(),
                canonicalTraceMapper);
            if (root != null) session.attachAdmittedRoot(root);
            T result = ExecutionBindingScope.supplyWith(ExecutionBinding.sessionOnly(session), () ->
            {
                Throwable failure = null;
                try { return action.apply(session); }
                catch (RuntimeException | Error ex) { failure = ex; throw ex; }
                finally { completeSession(session, failure); }
            });
            try { return completion.apply(result, session); }
            catch (RuntimeException ex) { throw new CompletionPhaseFailure(ex); }
        }
        finally
        {
            if (root != null) root.close();
        }
    }

    public static final class CompletionPhaseFailure extends RuntimeException
    {
        private CompletionPhaseFailure(RuntimeException cause) { super(cause); }
        public RuntimeException original() { return (RuntimeException) getCause(); }
    }

    private void finalizeSessionTrace(LoomspanSession session, @Nullable Throwable failure)
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        if (binding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        PhysicalBranchContext branch = binding.branch();
        if (session.getExecutionTrace().completed())
        {
            return;
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("entryPoint", "session-runner");
        metadata.put("remainingFrames", branch.depth());

        if (branch.depth() > 0)
        {
            IllegalStateException openFrameFailure = new IllegalStateException(
                    "Cannot finalize standalone session '%s' with %d open execution frame(s)."
                            .formatted(session.getSessionId(), branch.depth()));
            String failureId = session.recordFailure(openFrameFailure,
                    Map.of("message", "Standalone session completed with open execution frames"), branch.leaf().orElse(null));
            session.markTraceErrored();
            session.finalizeTrace(new TraceCompletion(
                    TraceOutcome.FAILED,
                    session.getSessionUsage().orElse(
                            ai.loomspan.internal.runtime.usage.SessionUsageSnapshot.empty()),
                    failureId,
                    Map.copyOf(metadata)));
            throw openFrameFailure;
        }

        String terminalFailureId = null;
        if (failure != null)
        {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            TraceFailureMetadata.addTo(payload, failure, "Session execution failed");
            terminalFailureId = session.recordFailure(failure, Map.copyOf(payload), branch.leaf().orElse(null));
        }

        session.finalizeTrace(new TraceCompletion(
                failure == null
                        ? TraceOutcome.SUCCEEDED
                        : (Thread.currentThread().isInterrupted() ? TraceOutcome.ABORTED : TraceOutcome.FAILED),
                session.getSessionUsage().orElse(
                        ai.loomspan.internal.runtime.usage.SessionUsageSnapshot.empty()),
                terminalFailureId,
                Map.copyOf(metadata)));
    }

    private void completeSession(LoomspanSession session, @Nullable Throwable failure)
    {
        RuntimeException cleanupFailure = null;

        try
        {
            finalizeSessionTrace(session, failure);
        }
        catch (RuntimeException ex)
        {
            cleanupFailure = ex;
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
