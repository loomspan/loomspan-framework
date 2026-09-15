package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ObservabilityArtifactDelivery implements AutoCloseable
{
    private final ExecutorService executor;
    private final AtomicLong identifiers = new AtomicLong();
    private final Map<Long, Admission> admissions = new LinkedHashMap<>();
    private final Map<Long, ObservabilityArtifactStream> transfers = new LinkedHashMap<>();
    private boolean closed;

    public ObservabilityArtifactDelivery()
    {
        this(Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("loomspan-artifact-download-", 1).factory()));
    }

    ObservabilityArtifactDelivery(ExecutorService executor)
    {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public synchronized Admission admit()
    {
        if (closed)
        {
            throw new ObservabilityException(
                    503, ObservabilityProblem.Code.APPLICATION_ERROR,
                    "Artifact delivery is unavailable");
        }
        if (admissions.size() == ObservabilityDeliveryLimits.OPEN_ARTIFACT_DOWNLOADS)
        {
            throw new ObservabilityException(
                    429, ObservabilityProblem.Code.LIMIT_EXCEEDED,
                    "The artifact download limit has been reached");
        }
        Admission admission = new Admission(identifiers.incrementAndGet());
        admissions.put(admission.id, admission);
        return admission;
    }

    void open(
            HttpServletRequest request,
            HttpServletResponse response,
            Admission admission,
            CompletionGraceRetention.ArtifactLease lease,
            Runnable prepareResponse)
    {
        Objects.requireNonNull(admission, "admission must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(prepareResponse, "prepareResponse must not be null");
        ObservabilityArtifactStream stream = null;
        try
        {
            stream = ObservabilityArtifactStream.open(request, response, this, admission, lease);
            synchronized (this)
            {
                if (closed || admission.released.get())
                {
                    throw new ObservabilityException(
                            503, ObservabilityProblem.Code.APPLICATION_ERROR,
                            "Artifact delivery is unavailable");
                }
                transfers.put(admission.id, stream);
                stream.start(executor);
                prepareResponse.run();
                stream.begin();
            }
        }
        catch (RuntimeException failure)
        {
            if (stream != null)
            {
                stream.close();
            }
            else
            {
                close(lease);
                admission.close();
            }
            throw failure;
        }
    }

    private static void close(CompletionGraceRetention.ArtifactLease lease)
    {
        try
        {
            lease.close();
        }
        catch (java.io.IOException ignored)
        {
        }
    }

    synchronized int admittedCount()
    {
        return admissions.size();
    }

    private synchronized void release(Admission admission)
    {
        admissions.remove(admission.id);
        transfers.remove(admission.id);
    }

    @Override
    public void close()
    {
        List<ObservabilityArtifactStream> active;
        List<Admission> reserved;
        synchronized (this)
        {
            if (closed)
            {
                return;
            }
            closed = true;
            active = List.copyOf(transfers.values());
            reserved = List.copyOf(admissions.values());
        }
        active.forEach(ObservabilityArtifactStream::close);
        reserved.forEach(Admission::close);
        executor.shutdownNow();
    }

    public final class Admission implements AutoCloseable
    {
        private final long id;
        private final AtomicBoolean released = new AtomicBoolean();

        private Admission(long id)
        {
            this.id = id;
        }

        @Override
        public void close()
        {
            if (released.compareAndSet(false, true))
            {
                ObservabilityArtifactDelivery.this.release(this);
            }
        }
    }
}
