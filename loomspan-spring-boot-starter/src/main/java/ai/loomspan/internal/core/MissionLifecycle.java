package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.step.AssignedTaskExecution;
import ai.loomspan.internal.runtime.step.AssignedTaskOutcome;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Mission-bounded admission, cancellation, write-fence, and cleanup authority. */
public final class MissionLifecycle
{
    public static final Duration CLEANUP_GRACE = Duration.ofMillis(250);

    public enum State { OPEN, CANCELLING, CLOSED }

    private final MissionContext mission;
    private final LongSupplier nanoTime;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<AdmittedTask> admitted = new ArrayList<>();
    private State state = State.OPEN;
    private @Nullable Throwable primaryCause;
    private @Nullable String primaryFailureId;
    private long deadlineNanos = Long.MAX_VALUE;
    private @Nullable Future<?> owningFuture;
    private boolean owningStarted;
    private boolean owningReturned;
    private @Nullable Cutoff cutoff;
    private boolean cleanupClaimed;

    MissionLifecycle(MissionContext mission)
    {
        this(mission, System::nanoTime);
    }

    MissionLifecycle(MissionContext mission, LongSupplier nanoTime)
    {
        this.mission = Objects.requireNonNull(mission, "mission must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public State state()
    {
        lock.lock();
        try { return state; }
        finally { lock.unlock(); }
    }

    boolean writableWhileLocked() { return state != State.CLOSED; }

    public void markOwningStarted()
    {
        lock.lock();
        try { owningStarted = true; }
        finally { lock.unlock(); }
    }

    public void registerOwningFuture(Future<?> future)
    {
        Objects.requireNonNull(future, "future must not be null");
        boolean cancel;
        lock.lock();
        try
        {
            owningFuture = future;
            cancel = state != State.OPEN;
        }
        finally { lock.unlock(); }
        if (cancel) future.cancel(true);
    }

    public void owningReturned()
    {
        lock.lock();
        try
        {
            owningReturned = true;
            changed.signalAll();
        }
        finally { lock.unlock(); }
    }

    public List<AdmittedTask> admitUnit(ExecutionBinding binding, List<AssignedTaskExecution> assignments,
            Runnable commitAdmission)
    {
        List<AssignedTaskExecution> ordered = List.copyOf(assignments);
        if (ordered.isEmpty()) throw new IllegalArgumentException("assignments must not be empty");
        Objects.requireNonNull(commitAdmission, "commitAdmission must not be null");
        requireCurrentMission(binding);
        return runWhileOpen(binding, true, () -> {
            commitAdmission.run();
            List<AdmittedTask> entries = ordered.stream().map(AdmittedTask::new).toList();
            admitted.addAll(entries);
            return entries;
        }).orElseThrow();
    }

    public void bindBranch(AdmittedTask task, ExecutionBinding binding)
    {
        lock.lock();
        try { requireOwned(task).binding = Objects.requireNonNull(binding, "binding must not be null"); }
        finally { lock.unlock(); }
    }

    /** Atomically marks the task callable as physically started only while its complete ancestry is open. */
    public boolean taskStarted(AdmittedTask task, ExecutionBinding binding)
    {
        requireCurrentMission(binding);
        return runWhileOpen(binding, false, () -> {
            AdmittedTask owned = requireOwned(task);
            owned.started = true;
            changed.signalAll();
            return true;
        }).orElse(false);
    }

    /** Establishes the atomic start boundary for a new execution phase. */
    public void requireOpenForNewWork(ExecutionBinding binding)
    {
        requireCurrentMission(binding);
        runWhileOpen(binding, true, () -> true).orElseThrow();
    }

    public void registerFuture(AdmittedTask task, Future<AssignedTaskOutcome> future)
    {
        Objects.requireNonNull(future, "future must not be null");
        boolean cancel;
        lock.lock();
        try
        {
            requireOwned(task).future = future;
            cancel = state != State.OPEN;
        }
        finally { lock.unlock(); }
        if (cancel) future.cancel(true);
    }

    public boolean publishOutcome(AdmittedTask task, ExecutionBinding binding, AssignedTaskOutcome outcome)
    {
        return runWithAncestry(binding, false, () -> {
            AdmittedTask owned = requireOwned(task);
            if (owned.outcome != null) throw new IllegalStateException("Assigned task outcome was already published.");
            owned.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
            changed.signalAll();
            return true;
        }).orElse(false);
    }

    public void taskReturned(AdmittedTask task)
    {
        lock.lock();
        try
        {
            requireOwned(task).returned = true;
            changed.signalAll();
        }
        finally { lock.unlock(); }
    }

    public PrimaryCancellation beginCancellation(ExecutionBinding binding, Throwable cause, Supplier<String> failureRecorder)
    {
        return beginCancellation(binding, cause, failureRecorder, true);
    }

    public PrimaryCancellation beginCancellation(ExecutionBinding binding, Throwable cause,
            Supplier<String> failureRecorder, boolean cancelOwning)
    {
        Objects.requireNonNull(cause, "cause must not be null");
        Objects.requireNonNull(failureRecorder, "failureRecorder must not be null");
        List<Future<?>> futures;
        PrimaryCancellation primary;
        primary = runWithAncestry(binding, true, () -> {
            if (state == State.OPEN)
            {
                state = State.CANCELLING;
                primaryCause = cause;
                deadlineNanos = saturatingAdd(nanoTime.getAsLong(), CLEANUP_GRACE.toNanos());
                primaryFailureId = Objects.requireNonNull(failureRecorder.get(), "failure id must not be null");
                changed.signalAll();
            }
            return new PrimaryCancellation(
                    Objects.requireNonNull(primaryCause, "primary cause must be present"),
                    Objects.requireNonNull(primaryFailureId, "primary failure id must be present"), deadlineNanos);
        }).orElseThrow();
        lock.lock();
        try
        {
            futures = cancellableFuturesLocked(cancelOwning);
        }
        finally { lock.unlock(); }
        futures.forEach(future -> future.cancel(true));
        return primary;
    }

    /** Close immediately for normal completion or rejected submission with no started work. */
    public Cutoff closeNow()
    {
        lock.lock();
        try { return closeLocked(); }
        finally { lock.unlock(); }
    }

    /** Waits uninterruptibly using only the first cancellation signal's remaining grace. */
    public Cutoff awaitCutoff()
    {
        boolean interrupted = false;
        lock.lock();
        try
        {
            if (cutoff != null) return cutoff;
            if (state == State.OPEN) return closeLocked();
            while (!allPhysicalWorkReturnedLocked())
            {
                long remaining = deadlineNanos - nanoTime.getAsLong();
                if (remaining <= 0) break;
                try { changed.awaitNanos(remaining); }
                catch (InterruptedException ex) { interrupted = true; }
            }
            return closeLocked();
        }
        finally
        {
            lock.unlock();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public Optional<PrimaryCancellation> primaryCancellation()
    {
        lock.lock();
        try
        {
            return primaryCause == null ? Optional.empty()
                    : Optional.of(new PrimaryCancellation(primaryCause, primaryFailureId, deadlineNanos));
        }
        finally { lock.unlock(); }
    }

    public Optional<Cutoff> cutoff()
    {
        lock.lock();
        try { return Optional.ofNullable(cutoff); }
        finally { lock.unlock(); }
    }

    public static boolean runIfWritable(ExecutionBinding binding, Runnable action)
    {
        return runWithAncestry(binding, false, () -> { action.run(); return true; }).orElse(false);
    }

    public static <T> T requireWritable(ExecutionBinding binding, Supplier<T> action)
    {
        Box<T> result = runWithAncestry(binding, true, () -> new Box<>(action.get()))
                .orElseThrow(() -> new MissionWriteRevokedException(binding.requireMission().skillName()));
        return result.value();
    }

    private boolean runCleanupIfPermitted(Cutoff candidate, ExecutionBinding binding, Runnable action)
    {
        Objects.requireNonNull(candidate, "cutoff must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (binding.requireMission() != mission || binding.session() != mission.session())
        {
            throw new IllegalArgumentException("Cleanup authority does not belong to the current mission.");
        }

        List<MissionLifecycle> ancestry = ancestry(binding.requireMission());
        ancestry.forEach(lifecycle -> lifecycle.lock.lock());
        try
        {
            if (candidate != cutoff)
            {
                throw new IllegalArgumentException("Cleanup authority does not belong to the current mission.");
            }
            int ownerIndex = ancestry.size() - 1;
            for (int index = 0; index < ownerIndex; index++)
            {
                if (ancestry.get(index).state == State.CLOSED) return false;
            }
            action.run();
            return true;
        }
        finally
        {
            for (int index = ancestry.size() - 1; index >= 0; index--) ancestry.get(index).lock.unlock();
        }
    }

    private static <T> Optional<T> runWithAncestry(ExecutionBinding binding, boolean failIfClosed, Supplier<T> action)
    {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (binding.mission() == null) return Optional.ofNullable(action.get());
        List<MissionLifecycle> ancestry = ancestry(binding.requireMission());
        ancestry.forEach(lifecycle -> lifecycle.lock.lock());
        try
        {
            if (ancestry.stream().anyMatch(lifecycle -> !lifecycle.writableWhileLocked()))
            {
                if (failIfClosed) throw new MissionWriteRevokedException(binding.requireMission().skillName());
                return Optional.empty();
            }
            return Optional.ofNullable(action.get());
        }
        finally
        {
            for (int index = ancestry.size() - 1; index >= 0; index--) ancestry.get(index).lock.unlock();
        }
    }

    private static <T> Optional<T> runWhileOpen(ExecutionBinding binding, boolean failIfNotOpen, Supplier<T> action)
    {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (binding.mission() == null) return Optional.ofNullable(action.get());
        List<MissionLifecycle> ancestry = ancestry(binding.requireMission());
        ancestry.forEach(lifecycle -> lifecycle.lock.lock());
        try
        {
            if (ancestry.stream().anyMatch(lifecycle -> lifecycle.state != State.OPEN))
            {
                if (failIfNotOpen) throw new MissionWriteRevokedException(binding.requireMission().skillName());
                return Optional.empty();
            }
            return Optional.ofNullable(action.get());
        }
        finally
        {
            for (int index = ancestry.size() - 1; index >= 0; index--) ancestry.get(index).lock.unlock();
        }
    }

    private void requireCurrentMission(ExecutionBinding binding)
    {
        Objects.requireNonNull(binding, "binding must not be null");
        if (binding.mission() != mission || binding.session() != mission.session())
        {
            throw new IllegalArgumentException("Execution binding does not belong to the current mission.");
        }
    }

    private static List<MissionLifecycle> ancestry(MissionContext mission)
    {
        List<MissionLifecycle> ancestry = new ArrayList<>();
        MissionContext cursor = mission;
        while (cursor != null)
        {
            ancestry.add(cursor.lifecycle());
            cursor = cursor.parent().orElse(null);
        }
        Collections.reverse(ancestry);
        return ancestry;
    }

    private AdmittedTask requireOwned(AdmittedTask task)
    {
        Objects.requireNonNull(task, "task must not be null");
        if (!admitted.contains(task)) throw new IllegalArgumentException("Task was not admitted by this lifecycle.");
        return task;
    }

    private List<Future<?>> cancellableFuturesLocked(boolean includeOwning)
    {
        List<Future<?>> futures = new ArrayList<>();
        if (includeOwning && owningFuture != null && !owningFuture.isDone()) futures.add(owningFuture);
        for (AdmittedTask task : admitted)
        {
            if (task.future != null && !task.future.isDone()) futures.add(task.future);
        }
        return futures;
    }

    private boolean allPhysicalWorkReturnedLocked()
    {
        if (owningStarted && !owningReturned) return false;
        return admitted.stream().allMatch(task -> !task.started || task.returned);
    }

    private Cutoff closeLocked()
    {
        if (cutoff != null) return cutoff;
        state = State.CLOSED;
        List<CutoffTask> tasks = admitted.stream()
                .map(task -> {
                    BranchDiagnosticDelta diagnostics = task.binding == null
                            ? new BranchDiagnosticDelta(null, null)
                            : task.binding.branch().diagnosticDelta();
                    return new CutoffTask(task.assignment, task.binding, task.outcome,
                            diagnostics.linterOutcome(), diagnostics.outputSchemaOutcome(),
                            task.future != null, task.started, task.returned);
                })
                .toList();
        cutoff = new Cutoff(this, tasks, primaryCause, primaryFailureId);
        changed.signalAll();
        return cutoff;
    }

    private boolean claimCleanup(Cutoff candidate)
    {
        lock.lock();
        try
        {
            if (candidate != cutoff) throw new IllegalArgumentException("Cleanup authority belongs to another cutoff.");
            if (cleanupClaimed) return false;
            cleanupClaimed = true;
            return true;
        }
        finally { lock.unlock(); }
    }

    private static long saturatingAdd(long left, long right)
    {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    private record Box<T>(T value) {}

    public final class AdmittedTask
    {
        private final AssignedTaskExecution assignment;
        private @Nullable ExecutionBinding binding;
        private @Nullable Future<AssignedTaskOutcome> future;
        private @Nullable AssignedTaskOutcome outcome;
        private boolean started;
        private boolean returned;

        private AdmittedTask(AssignedTaskExecution assignment)
        {
            this.assignment = Objects.requireNonNull(assignment, "assignment must not be null");
        }

        public AssignedTaskExecution assignment() { return assignment; }
        public Optional<Future<AssignedTaskOutcome>> future() { return Optional.ofNullable(future); }
    }

    public record PrimaryCancellation(Throwable cause, @Nullable String failureId, long deadlineNanos) {}

    public record CutoffTask(AssignedTaskExecution assignment, @Nullable ExecutionBinding binding,
            @Nullable AssignedTaskOutcome outcome, @Nullable LinterOutcome linterOutcome,
            @Nullable OutputSchemaOutcome outputSchemaOutcome, boolean submitted,
            boolean physicallyStarted, boolean physicallyReturned) {}

    public static final class Cutoff
    {
        private final MissionLifecycle owner;
        private final List<CutoffTask> tasks;
        private final @Nullable Throwable primaryCause;
        private final @Nullable String primaryFailureId;

        private Cutoff(MissionLifecycle owner, List<CutoffTask> tasks,
                @Nullable Throwable primaryCause, @Nullable String primaryFailureId)
        {
            this.owner = owner;
            this.tasks = List.copyOf(tasks);
            this.primaryCause = primaryCause;
            this.primaryFailureId = primaryFailureId;
        }

        public List<CutoffTask> tasks() { return tasks; }
        public Optional<Throwable> primaryCause() { return Optional.ofNullable(primaryCause); }
        public Optional<String> primaryFailureId() { return Optional.ofNullable(primaryFailureId); }
        public boolean claimCleanup() { return owner.claimCleanup(this); }
        public boolean runIfPermitted(ExecutionBinding binding, Runnable action)
        {
            return owner.runCleanupIfPermitted(this, binding, action);
        }
        public boolean belongsTo(MissionContext mission) { return owner.mission == mission; }
        public boolean ownsSession(LoomspanSession session) { return owner.mission.session() == session; }
        MissionLifecycle owner() { return owner; }
    }
}
