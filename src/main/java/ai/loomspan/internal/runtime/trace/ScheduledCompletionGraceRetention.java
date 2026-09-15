package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.FinalizedTraceArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScheduledCompletionGraceRetention implements CompletionGraceRetention
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledCompletionGraceRetention.class);

    private final Duration grace;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final Map<Path, RetentionState> states = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ScheduledCompletionGraceRetention(Duration grace)
    {
        this(
                grace,
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(runnable ->
                        Thread.ofPlatform().daemon().name("loomspan-trace-grace").unstarted(runnable)));
    }

    ScheduledCompletionGraceRetention(Duration grace, Clock clock, ScheduledExecutorService executor)
    {
        this.grace = Objects.requireNonNull(grace, "grace must not be null");
        if (grace.isNegative())
        {
            throw new IllegalArgumentException("grace must not be negative");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Optional<RetainedArtifact> retainOrDelete(
            Path artifactPath,
            Instant finalizedAt,
            String traceId,
            String sessionId) throws IOException
    {
        Objects.requireNonNull(artifactPath, "artifactPath must not be null");
        Objects.requireNonNull(finalizedAt, "finalizedAt must not be null");
        Instant expiresAt;
        try
        {
            expiresAt = finalizedAt.plus(grace);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException("finalizedAt plus grace must be representable", ex);
        }
        Path exactPath = artifactPath.toAbsolutePath().normalize();
        if (grace.isZero() || !expiresAt.isAfter(Instant.now(clock)))
        {
            Files.deleteIfExists(exactPath);
            return Optional.empty();
        }

        if (closed.get())
        {
            Files.deleteIfExists(exactPath);
            return Optional.empty();
        }
        long sizeBytes = Files.size(exactPath);
        RetentionState state = new RetentionState(
                exactPath, traceId, sessionId, expiresAt, sizeBytes);
        synchronized (this)
        {
            if (closed.get())
            {
                Files.deleteIfExists(exactPath);
                return Optional.empty();
            }
            if (states.putIfAbsent(exactPath, state) != null)
            {
                throw new IllegalStateException("Completion retention already owns the exact artifact path");
            }
        }
        try
        {
            long delayNanos;
            try
            {
                delayNanos = Math.max(0, Duration.between(Instant.now(clock), expiresAt).toNanos());
            }
            catch (ArithmeticException ex)
            {
                delayNanos = Long.MAX_VALUE;
            }
            state.future = executor.schedule(() -> expire(state), delayNanos, TimeUnit.NANOSECONDS);
            synchronized (this)
            {
                if (states.get(exactPath) != state)
                {
                    return Optional.empty();
                }
            }
            return Optional.of(new RetainedArtifact(expiresAt, sizeBytes));
        }
        catch (RejectedExecutionException ex)
        {
            synchronized (this)
            {
                states.remove(exactPath, state);
                state.closed = true;
            }
            Files.deleteIfExists(exactPath);
            return Optional.empty();
        }
    }

    @Override
    public Optional<ArtifactLease> acquire(FinalizedTraceArtifact artifact) throws IOException
    {
        Objects.requireNonNull(artifact, "artifact must not be null");
        if (closed.get())
        {
            return Optional.empty();
        }
        Path exactPath = artifact.artifactPath().toAbsolutePath().normalize();
        RetentionState state = null;
        if (artifact.artifactExpiresAt() != null)
        {
            synchronized (this)
            {
                state = states.get(exactPath);
                if (closed.get() || state == null || state.closed || state.expired
                        || !state.matches(artifact)
                        || !Instant.now(clock).isBefore(state.expiresAt))
                {
                    return Optional.empty();
                }
                state.readers++;
            }
        }

        InputStream input;
        try
        {
            input = Files.newInputStream(exactPath);
        }
        catch (NoSuchFileException ex)
        {
            if (state != null)
            {
                releaseReader(state);
            }
            return Optional.empty();
        }
        catch (IOException | RuntimeException ex)
        {
            if (state != null)
            {
                releaseReader(state);
            }
            throw ex;
        }
        return Optional.of(new Lease(input, artifact.sizeBytes(), state));
    }

    private void expire(RetentionState state)
    {
        boolean delete;
        synchronized (this)
        {
            if (state.closed || states.get(state.path) != state)
            {
                return;
            }
            state.expired = true;
            delete = state.readers == 0;
            if (delete)
            {
                state.closed = true;
                states.remove(state.path, state);
            }
        }
        if (delete)
        {
            delete(state);
        }
    }

    private void releaseReader(RetentionState state)
    {
        boolean delete = false;
        synchronized (this)
        {
            if (state.readers <= 0)
            {
                throw new IllegalStateException("artifact lease accounting underflow");
            }
            state.readers--;
            if (state.readers == 0
                    && (state.expired || !Instant.now(clock).isBefore(state.expiresAt))
                    && !state.closed)
            {
                state.expired = true;
                state.closed = true;
                states.remove(state.path, state);
                delete = true;
            }
        }
        if (delete)
        {
            delete(state);
        }
    }

    private static void delete(RetentionState state)
    {
        try
        {
            Files.deleteIfExists(state.path);
        }
        catch (IOException ex)
        {
            LOGGER.warn(
                    "Delayed trace deletion failed traceId={} sessionId={} exceptionClass={}",
                    state.traceId, state.sessionId, ex.getClass().getName());
        }
    }

    @Override
    public synchronized void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        states.values().forEach(state ->
        {
            state.closed = true;
            if (state.future != null)
            {
                state.future.cancel(false);
            }
        });
        states.clear();
        executor.shutdownNow();
    }

    synchronized int retainedArtifactCount()
    {
        return states.size();
    }

    private final class Lease implements ArtifactLease
    {
        private final InputStream input;
        private final long sizeBytes;
        private final RetentionState state;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(InputStream input, long sizeBytes, RetentionState state)
        {
            this.input = input;
            this.sizeBytes = sizeBytes;
            this.state = state;
        }

        @Override
        public InputStream input()
        {
            return input;
        }

        @Override
        public long sizeBytes()
        {
            return sizeBytes;
        }

        @Override
        public void close() throws IOException
        {
            if (!released.compareAndSet(false, true))
            {
                return;
            }
            try
            {
                input.close();
            }
            finally
            {
                if (state != null)
                {
                    releaseReader(state);
                }
            }
        }
    }

    private static final class RetentionState
    {
        private final Path path;
        private final String traceId;
        private final String sessionId;
        private final Instant expiresAt;
        private final long sizeBytes;
        private ScheduledFuture<?> future;
        private int readers;
        private boolean expired;
        private boolean closed;

        private RetentionState(Path path, String traceId, String sessionId, Instant expiresAt, long sizeBytes)
        {
            this.path = path;
            this.traceId = traceId;
            this.sessionId = sessionId;
            this.expiresAt = expiresAt;
            this.sizeBytes = sizeBytes;
        }

        private boolean matches(FinalizedTraceArtifact artifact)
        {
            return traceId.equals(artifact.traceId())
                    && sessionId.equals(artifact.sessionId())
                    && expiresAt.equals(artifact.artifactExpiresAt())
                    && sizeBytes == artifact.sizeBytes();
        }
    }
}
