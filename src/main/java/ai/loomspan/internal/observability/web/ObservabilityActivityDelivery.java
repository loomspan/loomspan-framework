package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.runtime.observation.ActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.ExecutionActivity;
import ai.loomspan.internal.runtime.observation.LiveActivitySignal;
import ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability;
import ai.loomspan.internal.runtime.observation.ReplayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ObservabilityActivityDelivery implements LiveActivitySignal, AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityActivityDelivery.class);

    private final String instanceId;
    private final ActivityReplayBuffer replay;
    private final LiveMonitoringAvailability liveMonitoring;
    private final ObservabilityDtoMapper mapper;
    private final ObservabilityJsonCodec json;
    private final ExecutorService dispatcher;
    private final ScheduledExecutorService deadlines;
    private final AtomicBoolean dispatchRunning = new AtomicBoolean();
    private final AtomicBoolean dispatchNeeded = new AtomicBoolean();
    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final AtomicLong identifiers = new AtomicLong();
    private final Map<Long, Registration> subscribers = new LinkedHashMap<>();
    private final Map<Long, Admission> reservations = new LinkedHashMap<>();

    private volatile boolean closed;
    private int admitted;

    public ObservabilityActivityDelivery(
            String instanceId,
            ActivityReplayBuffer replay,
            LiveMonitoringAvailability liveMonitoring,
            ObservabilityDtoMapper mapper,
            ObservabilityJsonCodec json)
    {
        this(
                instanceId,
                replay,
                liveMonitoring,
                mapper,
                json,
                Executors.newSingleThreadExecutor(threadFactory("loomspan-activity-delivery-")),
                Executors.newSingleThreadScheduledExecutor(threadFactory("loomspan-activity-deadline-")));
    }

    ObservabilityActivityDelivery(
            String instanceId,
            ActivityReplayBuffer replay,
            LiveMonitoringAvailability liveMonitoring,
            ObservabilityDtoMapper mapper,
            ObservabilityJsonCodec json,
            ExecutorService dispatcher,
            ScheduledExecutorService deadlines)
    {
        this.instanceId = requireNonBlank(instanceId, "instanceId");
        this.replay = Objects.requireNonNull(replay, "replay must not be null");
        this.liveMonitoring = Objects.requireNonNull(liveMonitoring, "liveMonitoring must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.deadlines = Objects.requireNonNull(deadlines, "deadlines must not be null");
    }

    public synchronized Admission admit(long afterCursor)
    {
        if (afterCursor < 0)
        {
            throw new IllegalArgumentException("afterCursor must not be negative");
        }
        if (closed || unavailable.get() || !liveMonitoring.isAvailable())
        {
            throw new ObservabilityException(
                    503,
                    ObservabilityProblem.Code.LIVE_MONITORING_UNAVAILABLE,
                    "Live execution monitoring is unavailable");
        }
        if (admitted == ObservabilityDeliveryLimits.OPEN_SUBSCRIPTIONS)
        {
            throw new ObservabilityException(
                    429,
                    ObservabilityProblem.Code.LIMIT_EXCEEDED,
                    "The live activity subscription limit has been reached");
        }
        admitted++;
        Admission admission = new Admission(identifiers.incrementAndGet(), afterCursor);
        reservations.put(admission.id, admission);
        return admission;
    }

    void activate(Admission admission, Subscriber subscriber)
    {
        Objects.requireNonNull(admission, "admission must not be null");
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        synchronized (this)
        {
            if (closed || unavailable.get() || admission.released.get())
            {
                subscriber.close();
                admission.release();
                return;
            }
            if (admission.activated)
            {
                throw new IllegalStateException("admission has already been activated");
            }
            admission.activated = true;
            subscribers.put(admission.id, new Registration(subscriber, admission.afterCursor));
        }
        scheduleDispatch();
    }

    ScheduledFuture<?> scheduleDeadline(Runnable task, Duration delay)
    {
        return deadlines.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void activityAvailable()
    {
        scheduleDispatch();
    }

    @Override
    public void liveUnavailable()
    {
        if (unavailable.compareAndSet(false, true))
        {
            scheduleDispatch();
        }
    }

    private void scheduleDispatch()
    {
        dispatchNeeded.set(true);
        if (!closed && dispatchRunning.compareAndSet(false, true))
        {
            try
            {
                dispatcher.execute(this::dispatch);
            }
            catch (RuntimeException failure)
            {
                dispatchRunning.set(false);
                if (!closed)
                {
                    closeSubscribers("DISPATCH_START_FAILED", failure);
                }
            }
        }
    }

    private void dispatch()
    {
        try
        {
            while (!closed && dispatchNeeded.getAndSet(false))
            {
                if (unavailable.get())
                {
                    closeSubscribers("LIVE_UNAVAILABLE", null);
                    continue;
                }
                dispatchOneBatchPerCursor();
            }
        }
        finally
        {
            dispatchRunning.set(false);
            if (!closed && dispatchNeeded.get())
            {
                scheduleDispatch();
            }
        }
    }

    private void dispatchOneBatchPerCursor()
    {
        Map<Long, List<Registration>> groups = new LinkedHashMap<>();
        synchronized (this)
        {
            subscribers.values().forEach(registration ->
                    groups.computeIfAbsent(registration.cursor, ignored -> new ArrayList<>()).add(registration));
        }
        Map<Long, byte[]> serialized = new HashMap<>();
        boolean more = false;
        for (Map.Entry<Long, List<Registration>> group : groups.entrySet())
        {
            ReplayResult result = replay.replayAfter(group.getKey(), ObservabilityDeliveryLimits.REPLAY_BATCH);
            if (result.status() == ReplayResult.Status.TOO_OLD)
            {
                group.getValue().forEach(registration -> registration.subscriber.close());
                continue;
            }
            if (result.status() == ReplayResult.Status.FUTURE)
            {
                group.getValue().forEach(registration -> registration.subscriber.close());
                continue;
            }
            for (ExecutionActivity activity : result.activities())
            {
                byte[] frame;
                try
                {
                    frame = serialized.computeIfAbsent(activity.deliveryCursor(), ignored ->
                    {
                        try
                        {
                            return ObservabilityActivityStream.activityFrame(
                                    json, mapper.activity(instanceId, activity));
                        }
                        catch (IOException failure)
                        {
                            throw new FrameEncodingFailure(failure);
                        }
                    });
                }
                catch (FrameEncodingFailure failure)
                {
                    closeSubscribers("ACTIVITY_ENCODING_FAILED", failure.getCause());
                    return;
                }
                for (Registration registration : group.getValue())
                {
                    if (registration.cursor >= activity.deliveryCursor())
                    {
                        continue;
                    }
                    if (!registration.subscriber.offer(frame, activity.deliveryCursor()))
                    {
                        registration.subscriber.close();
                    }
                    else
                    {
                        registration.cursor = activity.deliveryCursor();
                    }
                }
            }
            if (!result.activities().isEmpty()
                    && result.activities().getLast().deliveryCursor() < result.currentCursor())
            {
                more = true;
            }
        }
        if (more)
        {
            dispatchNeeded.set(true);
        }
    }

    private void closeSubscribers(String operation, Throwable failure)
    {
        List<Subscriber> current;
        synchronized (this)
        {
            current = subscribers.values().stream().map(registration -> registration.subscriber).toList();
        }
        current.forEach(Subscriber::close);
        if (failure != null)
        {
            LOGGER.warn(
                    "Activity delivery closed operation={} instanceId={} exceptionClass={}",
                    operation,
                    instanceId,
                    failure.getClass().getName());
        }
    }

    @Override
    public void close()
    {
        List<Subscriber> current;
        List<Admission> currentAdmissions;
        synchronized (this)
        {
            if (closed)
            {
                return;
            }
            closed = true;
            current = subscribers.values().stream().map(registration -> registration.subscriber).toList();
            currentAdmissions = List.copyOf(reservations.values());
        }
        current.forEach(Subscriber::close);
        currentAdmissions.forEach(Admission::release);
        deadlines.shutdownNow();
        dispatcher.shutdownNow();
    }

    synchronized int admittedCount()
    {
        return admitted;
    }

    private synchronized void release(Admission admission)
    {
        subscribers.remove(admission.id);
        reservations.remove(admission.id);
        if (admitted <= 0)
        {
            throw new IllegalStateException("activity admission accounting underflow");
        }
        admitted--;
    }

    public final class Admission implements AutoCloseable
    {
        private final long id;
        private final long afterCursor;
        private final AtomicBoolean released = new AtomicBoolean();
        private volatile boolean activated;

        private Admission(long id, long afterCursor)
        {
            this.id = id;
            this.afterCursor = afterCursor;
        }

        @Override
        public void close()
        {
            release();
        }

        void release()
        {
            if (released.compareAndSet(false, true))
            {
                ObservabilityActivityDelivery.this.release(this);
            }
        }
    }

    interface Subscriber
    {
        boolean offer(byte[] frame, long cursor);

        void close();
    }

    private static ThreadFactory threadFactory(String prefix)
    {
        AtomicLong sequence = new AtomicLong();
        return task ->
        {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static final class Registration
    {
        private final Subscriber subscriber;
        private long cursor;

        private Registration(Subscriber subscriber, long cursor)
        {
            this.subscriber = subscriber;
            this.cursor = cursor;
        }
    }

    private static final class FrameEncodingFailure extends RuntimeException
    {
        private FrameEncodingFailure(IOException cause)
        {
            super(cause);
        }
    }
}
