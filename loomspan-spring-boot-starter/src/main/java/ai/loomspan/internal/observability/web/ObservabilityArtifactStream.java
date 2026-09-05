package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

final class ObservabilityArtifactStream implements AsyncListener, Runnable, AutoCloseable
{
    static final int COPY_BUFFER_BYTES = 16 * 1024;

    private final ObservabilityArtifactDelivery.Admission admission;
    private final CompletionGraceRetention.ArtifactLease lease;
    private final AsyncContext async;
    private final ServletOutputStream output;
    private final CountDownLatch startGate = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Future<?> task;
    private volatile Thread worker;

    private ObservabilityArtifactStream(
            ObservabilityArtifactDelivery.Admission admission,
            CompletionGraceRetention.ArtifactLease lease,
            AsyncContext async,
            ServletOutputStream output)
    {
        this.admission = admission;
        this.lease = lease;
        this.async = async;
        this.output = output;
    }

    static ObservabilityArtifactStream open(
            HttpServletRequest request,
            HttpServletResponse response,
            ObservabilityArtifactDelivery delivery,
            ObservabilityArtifactDelivery.Admission admission,
            CompletionGraceRetention.ArtifactLease lease)
    {
        Objects.requireNonNull(delivery, "delivery must not be null");
        AsyncContext async = request.startAsync(request, response);
        ObservabilityArtifactStream stream;
        try
        {
            async.setTimeout(ObservabilityDeliveryLimits.ARTIFACT_DOWNLOAD_TIMEOUT.toMillis());
            stream = new ObservabilityArtifactStream(admission, lease, async, response.getOutputStream());
            async.addListener(stream);
        }
        catch (IOException | RuntimeException failure)
        {
            try
            {
                async.complete();
            }
            catch (RuntimeException ignored)
            {
            }
            throw new IllegalStateException("Artifact async delivery could not start", failure);
        }
        return stream;
    }

    void start(ExecutorService executor)
    {
        task = executor.submit(this);
        if (closed.get())
        {
            task.cancel(true);
        }
    }

    void begin()
    {
        startGate.countDown();
    }

    @Override
    public void run()
    {
        worker = Thread.currentThread();
        try
        {
            startGate.await();
            copyExactly(lease.input(), output, lease.sizeBytes());
            output.flush();
        }
        catch (InterruptedException ignored)
        {
            Thread.currentThread().interrupt();
        }
        catch (IOException | RuntimeException ignored)
        {
            // The response is already owned as NDJSON. Terminate it; never mix in a JSON problem.
        }
        finally
        {
            close();
        }
    }

    static void copyExactly(InputStream input, ServletOutputStream output, long sizeBytes) throws IOException
    {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long remaining = sizeBytes;
        while (remaining > 0)
        {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0)
            {
                throw new EOFException("Finalized artifact ended before its cataloged size");
            }
            if (read == 0)
            {
                continue;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        if (input.read() >= 0)
        {
            throw new IOException("Finalized artifact exceeded its cataloged size");
        }
    }

    @Override
    public void close()
    {
        finish(true);
    }

    private void finish(boolean completeAsync)
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        Future<?> current = task;
        if (current != null && !current.isDone() && Thread.currentThread() != worker)
        {
            current.cancel(true);
        }
        try
        {
            lease.close();
        }
        catch (IOException ignored)
        {
        }
        admission.close();
        if (completeAsync)
        {
            try
            {
                async.complete();
            }
            catch (RuntimeException ignored)
            {
            }
        }
    }

    @Override
    public void onComplete(AsyncEvent event)
    {
        finish(false);
    }

    @Override
    public void onTimeout(AsyncEvent event)
    {
        close();
    }

    @Override
    public void onError(AsyncEvent event)
    {
        close();
    }

    @Override
    public void onStartAsync(AsyncEvent event)
    {
    }
}
