package ai.loomspan.internal.core;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Internal owner of root admission and the single framework shutdown budget. */
public final class FrameworkExecutionLifecycle
        implements ApplicationListener<ContextClosedEvent>, SmartLifecycle, DisposableBean
{
    private final ApplicationContext applicationContext;
    private final ExecutorService executor;
    private final long timeoutNanos;
    private final LongSupplier nanoTime;
    private final Object monitor = new Object();
    private final Set<AdmittedRoot> activeRoots = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean admissionClosed = new AtomicBoolean();
    private final AtomicBoolean cutoff = new AtomicBoolean();
    private final AtomicLong deadlineNanos = new AtomicLong(Long.MAX_VALUE);
    private volatile boolean running = true;

    public FrameworkExecutionLifecycle(ApplicationContext applicationContext, Duration timeout,
            ExecutorService executor)
    {
        this(applicationContext, timeout, executor, System::nanoTime);
    }

    FrameworkExecutionLifecycle(ApplicationContext applicationContext, Duration timeout,
            ExecutorService executor, LongSupplier nanoTime)
    {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("timeout must be greater than zero");
        this.timeoutNanos = toNanosSaturated(timeout);
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public AdmittedRoot admitRoot()
    {
        synchronized (monitor)
        {
            if (admissionClosed.get())
                throw new RejectedExecutionException("Loomspan framework is shutting down; new root work is not accepted");
            AdmittedRoot root = new AdmittedRoot(this);
            activeRoots.add(root);
            return root;
        }
    }

    private void release(AdmittedRoot root)
    {
        if (activeRoots.remove(root))
        {
            synchronized (monitor) { monitor.notifyAll(); }
        }
    }

    /** Closes admission and establishes the deadline exactly once; it never waits. */
    public void closeAdmission()
    {
        synchronized (monitor)
        {
            if (admissionClosed.compareAndSet(false, true))
            {
                long deadline = saturatingAdd(nanoTime.getAsLong(), timeoutNanos);
                deadlineNanos.set(deadline);
                activeRoots.forEach(root -> root.establishDeadline(deadline));
            }
            monitor.notifyAll();
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event)
    {
        if (event.getApplicationContext() == applicationContext) closeAdmission();
    }

    @Override
    public boolean supportsAsyncExecution() { return false; }

    @Override
    public void start() { running = true; }

    @Override
    public void stop() { stop(() -> { }); }

    @Override
    public void stop(Runnable callback)
    {
        Objects.requireNonNull(callback, "callback must not be null");
        try
        {
            closeAdmission();
            waitForRoots();
            executor.shutdown();
            awaitExecutorWithinBudget();
            if (!executor.isTerminated()) publishCutoff();
        }
        finally
        {
            running = false;
            callback.run();
        }
    }

    private void waitForRoots()
    {
        boolean interrupted = false;
        synchronized (monitor)
        {
            while (!activeRoots.isEmpty())
            {
                long remaining = remainingNanos();
                if (remaining <= 0) break;
                try { TimeUnit.NANOSECONDS.timedWait(monitor, remaining); }
                catch (InterruptedException ex) { interrupted = true; }
            }
        }
        if (!activeRoots.isEmpty()) publishCutoff();
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void awaitExecutorWithinBudget()
    {
        boolean interrupted = false;
        try
        {
            while (!executor.isTerminated())
            {
                long remaining = remainingNanos();
                if (remaining <= 0) return;
                try
                {
                    if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) return;
                }
                catch (InterruptedException ex) { interrupted = true; }
            }
        }
        finally
        {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    long remainingNanos()
    {
        if (!admissionClosed.get()) return timeoutNanos;
        long deadline = deadlineNanos.get();
        long now = nanoTime.getAsLong();
        if (deadline <= now) return 0;
        if (now < 0 && deadline > Long.MAX_VALUE + now) return Long.MAX_VALUE;
        return deadline - now;
    }

    private void publishCutoff()
    {
        if (cutoff.compareAndSet(false, true))
        {
            long deadline = deadlineNanos.get();
            activeRoots.forEach(root -> root.cutoff(deadline));
        }
        executor.shutdownNow();
    }

    /** Non-waiting fallback for failed startup or skipped/failed close-event delivery. */
    @Override
    public void destroy()
    {
        closeAdmission();
        publishCutoff();
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public boolean isAutoStartup() { return true; }

    @Override
    public int getPhase() { return Integer.MAX_VALUE; }

    int activeRootCount() { return activeRoots.size(); }
    long deadlineNanos() { return deadlineNanos.get(); }

    private static long saturatingAdd(long left, long right)
    {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long toNanosSaturated(Duration duration)
    {
        try { return duration.toNanos(); }
        catch (ArithmeticException ex) { return Long.MAX_VALUE; }
    }

    public static final class AdmittedRoot implements AutoCloseable
    {
        private final FrameworkExecutionLifecycle owner;
        private final Set<MissionLifecycle> missions = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean cutOff = new AtomicBoolean();
        private volatile long cutoffDeadlineNanos = Long.MAX_VALUE;

        private AdmittedRoot(FrameworkExecutionLifecycle owner) { this.owner = owner; }

        synchronized void register(MissionLifecycle mission)
        {
            missions.add(mission);
            mission.frameworkDeadline(cutoffDeadlineNanos);
            if (cutOff.get()) mission.frameworkCutoff(cutoffDeadlineNanos);
        }

        boolean isCutOff() { return cutOff.get(); }
        long cutoffDeadlineNanos() { return cutoffDeadlineNanos; }

        private synchronized void cutoff(long deadline)
        {
            cutoffDeadlineNanos = deadline;
            cutOff.set(true);
            missions.forEach(mission -> mission.frameworkCutoff(deadline));
        }

        private synchronized void establishDeadline(long deadline)
        {
            cutoffDeadlineNanos = deadline;
            missions.forEach(mission -> mission.frameworkDeadline(deadline));
        }

        @Override
        public void close()
        {
            if (closed.compareAndSet(false, true)) owner.release(this);
        }
    }
}
