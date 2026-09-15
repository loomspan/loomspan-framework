package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.observability.web.dto.ObservabilityDtos;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class ObservabilityActivityStream
        implements ObservabilityActivityDelivery.Subscriber, WriteListener, AsyncListener
{
    private final ObservabilityActivityDelivery delivery;
    private final ObservabilityActivityDelivery.Admission admission;
    private final AsyncContext async;
    private final ServletOutputStream output;
    private final Deque<PendingFrame> pending = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean draining = new AtomicBoolean();

    private long pendingBytes;
    private int pendingActivities;
    private long headGeneration;
    private ScheduledFuture<?> headDeadline;

    ObservabilityActivityStream(
            ObservabilityActivityDelivery delivery,
            ObservabilityActivityDelivery.Admission admission,
            AsyncContext async,
            ServletOutputStream output)
    {
        this.delivery = delivery;
        this.admission = admission;
        this.async = async;
        this.output = output;
    }

    static void open(
            HttpServletRequest request,
            HttpServletResponse response,
            ObservabilityActivityDelivery delivery,
            ObservabilityActivityDelivery.Admission admission,
            byte[] handshake)
    {
        Objects.requireNonNull(handshake, "handshake must not be null");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        AsyncContext async;
        try
        {
            async = request.startAsync(request, response);
        }
        catch (RuntimeException failure)
        {
            admission.close();
            throw failure;
        }
        ObservabilityActivityStream stream = null;
        try
        {
            async.setTimeout(0);
            stream = new ObservabilityActivityStream(delivery, admission, async, response.getOutputStream());
            async.addListener(stream);
            stream.enqueue(handshake, false);
            stream.output.setWriteListener(stream);
            stream.drain();
            delivery.activate(admission, stream);
        }
        catch (IOException | RuntimeException failure)
        {
            if (stream != null)
            {
                stream.close();
            }
            else
            {
                admission.close();
                try
                {
                    async.complete();
                }
                catch (RuntimeException ignored)
                {
                }
            }
        }
    }

    static byte[] handshakeFrame(ObservabilityJsonCodec json, ObservabilityDtos.ActivityHandshake handshake)
            throws IOException
    {
        return frame(null, "handshake", json.write(handshake));
    }

    static byte[] activityFrame(ObservabilityJsonCodec json, ObservabilityDtos.ActivityEnvelope activity)
            throws IOException
    {
        return frame(activity.cursor(), "activity", json.write(activity));
    }

    private static byte[] frame(String id, String event, byte[] data)
    {
        String prefix = (id == null ? "" : "id: " + id + "\n")
                + "event: " + event + "\ndata: ";
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] framed = new byte[prefixBytes.length + data.length + 2];
        System.arraycopy(prefixBytes, 0, framed, 0, prefixBytes.length);
        System.arraycopy(data, 0, framed, prefixBytes.length, data.length);
        framed[framed.length - 2] = '\n';
        framed[framed.length - 1] = '\n';
        return framed;
    }

    @Override
    public boolean offer(byte[] frame, long cursor)
    {
        if (!enqueue(frame, true))
        {
            return false;
        }
        drain();
        return !closed.get();
    }

    private synchronized boolean enqueue(byte[] bytes, boolean activity)
    {
        if (closed.get())
        {
            return false;
        }
        int nextActivities = pendingActivities + (activity ? 1 : 0);
        if (nextActivities > ObservabilityDeliveryLimits.PENDING_ACTIVITY_FRAMES
                || pendingBytes + bytes.length > ObservabilityDeliveryLimits.PENDING_BYTES)
        {
            return false;
        }
        boolean becomesHead = pending.isEmpty();
        pending.addLast(new PendingFrame(bytes, activity));
        pendingBytes += bytes.length;
        pendingActivities = nextActivities;
        if (becomesHead)
        {
            startHeadDeadline();
        }
        return true;
    }

    @Override
    public void onWritePossible()
    {
        drain();
    }

    @Override
    public void onError(Throwable failure)
    {
        close();
    }

    private void drain()
    {
        if (!draining.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            while (!closed.get() && output.isReady())
            {
                PendingFrame frame;
                synchronized (this)
                {
                    frame = pending.peekFirst();
                }
                if (frame == null)
                {
                    return;
                }
                output.write(frame.bytes);
                output.flush();
                synchronized (this)
                {
                    if (pending.peekFirst() != frame)
                    {
                        continue;
                    }
                    pending.removeFirst();
                    pendingBytes -= frame.bytes.length;
                    if (frame.activity)
                    {
                        pendingActivities--;
                    }
                    cancelHeadDeadline();
                    if (!pending.isEmpty())
                    {
                        startHeadDeadline();
                    }
                }
            }
        }
        catch (IOException | RuntimeException failure)
        {
            close();
        }
        finally
        {
            draining.set(false);
            boolean retry;
            synchronized (this)
            {
                retry = !closed.get() && !pending.isEmpty() && output.isReady();
            }
            if (retry)
            {
                drain();
            }
        }
    }

    private synchronized void startHeadDeadline()
    {
        long generation = ++headGeneration;
        headDeadline = delivery.scheduleDeadline(
                () -> expireHead(generation),
                ObservabilityDeliveryLimits.WRITE_READINESS_DEADLINE);
    }

    private void expireHead(long generation)
    {
        if (!beginClose(generation))
        {
            return;
        }
        finishClose(true);
    }

    private synchronized void cancelHeadDeadline()
    {
        headGeneration++;
        if (headDeadline != null)
        {
            headDeadline.cancel(false);
            headDeadline = null;
        }
    }

    @Override
    public void close()
    {
        close(true);
    }

    private void close(boolean complete)
    {
        if (!beginClose(null))
        {
            return;
        }
        finishClose(complete);
    }

    private synchronized boolean beginClose(Long expectedHeadGeneration)
    {
        if (expectedHeadGeneration != null
                && (expectedHeadGeneration != headGeneration || pending.isEmpty()))
        {
            return false;
        }
        if (!closed.compareAndSet(false, true))
        {
            return false;
        }
        cancelHeadDeadline();
        pending.clear();
        pendingBytes = 0;
        pendingActivities = 0;
        return true;
    }

    private void finishClose(boolean complete)
    {
        admission.release();
        if (complete)
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
        close(false);
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

    private record PendingFrame(byte[] bytes, boolean activity) {}
}
