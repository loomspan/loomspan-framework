package ai.loomspan.internal.core;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.internal.runtime.step.AssignedTaskExecution;
import ai.loomspan.internal.runtime.step.AssignedTaskOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionLifecycleTest
{
    @Test
    void startsOpenAndClosesMonotonicallyExactlyOnce()
    {
        ExecutionBinding binding = binding("monotonic", null);
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();

        assertThat(lifecycle.state()).isEqualTo(MissionLifecycle.State.OPEN);
        MissionLifecycle.Cutoff first = lifecycle.closeNow();

        assertThat(lifecycle.state()).isEqualTo(MissionLifecycle.State.CLOSED);
        assertThat(lifecycle.closeNow()).isSameAs(first);
        assertThat(first.claimCleanup()).isTrue();
        assertThat(first.claimCleanup()).isFalse();
    }

    @Test
    void firstCancellationSignalOwnsCauseFailureIdAndDeadline()
    {
        ExecutionBinding binding = binding("primary", null);
        Throwable first = new IllegalStateException("first");
        Throwable second = new IllegalArgumentException("second");
        MissionLifecycle owned = binding.requireMission().lifecycle();
        MissionLifecycle.PrimaryCancellation primary = owned.beginCancellation(
                binding, first, () -> "failure-1");
        MissionLifecycle.PrimaryCancellation later = owned.beginCancellation(
                binding, second, () -> "failure-2");

        assertThat(primary.cause()).isSameAs(first);
        assertThat(later.cause()).isSameAs(first);
        assertThat(later.failureId()).isEqualTo("failure-1");
        assertThat(later.deadlineNanos()).isEqualTo(primary.deadlineNanos());
    }

    @Test
    void admissionAndCancellationAreAllOrNone()
    {
        ExecutionBinding binding = binding("admission", null);
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        AtomicInteger commits = new AtomicInteger();
        List<AssignedTaskExecution> assignments = List.of(assignment("a", 1), assignment("b", 2));

        List<MissionLifecycle.AdmittedTask> admitted = lifecycle.admitUnit(
                binding, assignments, commits::incrementAndGet);
        lifecycle.beginCancellation(binding, new IllegalStateException("stop"), () -> "failure");

        assertThat(admitted).hasSize(2);
        assertThat(commits).hasValue(1);
        assertThatThrownBy(() -> lifecycle.admitUnit(binding, List.of(assignment("c", 3)), commits::incrementAndGet))
                .isInstanceOf(MissionWriteRevokedException.class);
        assertThat(commits).hasValue(1);
    }

    @Test
    void admittedMutationCompletesBeforeCloseAndSubsequentWritesAreRejected() throws Exception
    {
        ExecutionBinding binding = binding("write-race", null);
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            var writer = executor.submit(() -> binding.runIfWritable(() -> {
                entered.countDown();
                await(release);
                writes.incrementAndGet();
            }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var closer = executor.submit(lifecycle::closeNow);
            assertThat(closer.isDone()).isFalse();
            release.countDown();
            assertThat(writer.get(2, TimeUnit.SECONDS)).isTrue();
            closer.get(2, TimeUnit.SECONDS);
        }

        assertThat(binding.runIfWritable(writes::incrementAndGet)).isFalse();
        assertThat(writes).hasValue(1);
    }

    @Test
    void publicationRacingCloseHasOneCutoffClassification()
    {
        ExecutionBinding binding = binding("publication", null);
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        MissionLifecycle.AdmittedTask task = lifecycle.admitUnit(
                binding, List.of(assignment("a", 1)), () -> {}).getFirst();
        lifecycle.bindBranch(task, binding.forkBranch());
        lifecycle.beginCancellation(binding, new IllegalStateException("stop"), () -> "failure");
        MissionLifecycle.Cutoff cutoff = lifecycle.closeNow();

        assertThat(lifecycle.publishOutcome(task, binding.forkBranch(),
                new AssignedTaskOutcome.Success("late", null, null))).isFalse();
        assertThat(cutoff.tasks().getFirst().outcome()).isNull();
    }

    @Test
    void cutoffSnapshotsAcceptedBranchDiagnostics()
    {
        ExecutionBinding binding = binding("diagnostic-cutoff", null);
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        MissionLifecycle.AdmittedTask task = lifecycle.admitUnit(
                binding, List.of(assignment("a", 1)), () -> {}).getFirst();
        ExecutionBinding worker = binding.forkBranch();
        lifecycle.bindBranch(task, worker);
        LinterOutcome linter = new LinterOutcome(
                "worker", "regex", 1, 0, 1, LinterOutcomeStatus.PASSED, null);
        OutputSchemaOutcome outputSchema = new OutputSchemaOutcome(
                "worker", null, 1, 0, 1, OutputSchemaOutcomeStatus.PASSED, List.of());

        worker.runIfWritable(() -> {
            worker.branch().recordLinterOutcome(linter);
            worker.branch().recordOutputSchemaOutcome(outputSchema);
        });
        MissionLifecycle.CutoffTask cutoffTask = lifecycle.closeNow().tasks().getFirst();

        assertThat(cutoffTask.linterOutcome()).isSameAs(linter);
        assertThat(cutoffTask.outputSchemaOutcome()).isSameAs(outputSchema);
        assertThat(worker.runIfWritable(() -> worker.branch().recordLinterOutcome(
                new LinterOutcome("worker", "regex", 2, 0, 1, LinterOutcomeStatus.PASSED, null))))
                .isFalse();
        assertThat(cutoffTask.linterOutcome()).isSameAs(linter);
    }

    @Test
    void futureRegisteredAfterCancellationIsCancelledImmediately()
    {
        ExecutionBinding binding = binding("late-future", null);
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        MissionLifecycle.AdmittedTask task = lifecycle.admitUnit(
                binding, List.of(assignment("a", 1)), () -> {}).getFirst();
        lifecycle.beginCancellation(binding, new IllegalStateException("stop"), () -> "failure");
        FutureTask<AssignedTaskOutcome> future = new FutureTask<>(
                () -> new AssignedTaskOutcome.Success("unused", null, null));

        lifecycle.registerFuture(task, future);

        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    void allPhysicallyReturnedWorkClosesWithoutConsultingTheDeadline()
    {
        AtomicInteger clockReads = new AtomicInteger();
        ExecutionBinding binding = binding("returned", null, () -> { clockReads.incrementAndGet(); return 10L; });
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        MissionLifecycle.AdmittedTask task = lifecycle.admitUnit(
                binding, List.of(assignment("a", 1)), () -> {}).getFirst();
        FutureTask<AssignedTaskOutcome> future = new FutureTask<>(
                () -> new AssignedTaskOutcome.Success("unused", null, null));
        lifecycle.registerFuture(task, future);
        assertThat(lifecycle.taskStarted(task, binding)).isTrue();
        lifecycle.beginCancellation(binding, new IllegalStateException("stop"), () -> "failure", false);
        lifecycle.taskReturned(task);

        MissionLifecycle.Cutoff cutoff = lifecycle.awaitCutoff();

        assertThat(clockReads).hasValue(1);
        assertThat(cutoff.tasks().getFirst().physicallyReturned()).isTrue();
    }

    @Test
    void cancelledFutureThatNeverStartedClosesWithoutConsultingTheDeadline()
    {
        AtomicInteger clockReads = new AtomicInteger();
        ExecutionBinding binding = binding("physical-return", null, () -> {
            clockReads.incrementAndGet();
            return 100L;
        });
        MissionLifecycle lifecycle = binding.requireMission().lifecycle();
        MissionLifecycle.AdmittedTask task = lifecycle.admitUnit(
                binding, List.of(assignment("a", 1)), () -> {}).getFirst();
        FutureTask<AssignedTaskOutcome> future = new FutureTask<>(
                () -> new AssignedTaskOutcome.Success("unused", null, null));
        future.cancel(true);
        lifecycle.registerFuture(task, future);
        lifecycle.beginCancellation(binding, new IllegalStateException("stop"), () -> "failure", false);

        MissionLifecycle.Cutoff cutoff = lifecycle.awaitCutoff();

        assertThat(future.isDone()).isTrue();
        assertThat(clockReads).hasValue(1);
        assertThat(cutoff.tasks().getFirst().physicallyStarted()).isFalse();
        assertThat(cutoff.tasks().getFirst().physicallyReturned()).isFalse();
    }

    @Test
    void closedAncestorRevokesDescendantWhileClosedChildLeavesParentWritable()
    {
        ExecutionBinding parent = binding("parent", null);
        ExecutionBinding child = binding("child", parent.requireMission());

        child.requireMission().lifecycle().closeNow();
        assertThat(parent.runIfWritable(() -> {})).isTrue();
        assertThat(child.runIfWritable(() -> {})).isFalse();

        ExecutionBinding sibling = binding("sibling", parent.requireMission());
        parent.requireMission().lifecycle().closeNow();
        assertThat(sibling.runIfWritable(() -> {})).isFalse();
    }

    @Test
    void cancellingAncestorRejectsDescendantAdmissionAndTaskStartButStillAllowsExistingWrites()
    {
        ExecutionBinding parent = binding("cancelling-parent", null);
        ExecutionBinding child = binding("cancelling-child", parent.requireMission());
        MissionLifecycle childLifecycle = child.requireMission().lifecycle();
        MissionLifecycle.AdmittedTask admitted = childLifecycle.admitUnit(
                child, List.of(assignment("existing", 1)), () -> {}).getFirst();

        parent.requireMission().lifecycle().beginCancellation(
                parent, new IllegalStateException("stop"), () -> "failure", false);

        assertThat(child.runIfWritable(() -> {})).isTrue();
        assertThat(childLifecycle.taskStarted(admitted, child)).isFalse();
        assertThatThrownBy(() -> childLifecycle.requireOpenForNewWork(child))
                .isInstanceOf(MissionWriteRevokedException.class);
        assertThatThrownBy(() -> childLifecycle.admitUnit(
                child, List.of(assignment("new", 2)), () -> {}))
                .isInstanceOf(MissionWriteRevokedException.class);
    }

    @Test
    void closedAncestorSuppressesLaterChildCleanupAuthority()
    {
        ExecutionBinding parent = binding("cleanup-parent", null);
        ExecutionBinding child = binding("cleanup-child", parent.requireMission());
        MissionLifecycle.Cutoff childCutoff = child.requireMission().lifecycle().closeNow();
        AtomicInteger cleanups = new AtomicInteger();

        assertThat(childCutoff.runIfPermitted(child, cleanups::incrementAndGet)).isTrue();
        parent.requireMission().lifecycle().closeNow();

        assertThat(childCutoff.runIfPermitted(child, cleanups::incrementAndGet)).isFalse();
        assertThat(cleanups).hasValue(1);
    }

    @Test
    void ancestorCloseWaitsForCleanupAlreadyAdmittedByChild() throws Exception
    {
        ExecutionBinding parent = binding("cleanup-race-parent", null);
        ExecutionBinding child = binding("cleanup-race-child", parent.requireMission());
        MissionLifecycle.Cutoff childCutoff = child.requireMission().lifecycle().closeNow();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            var cleanup = executor.submit(() -> childCutoff.runIfPermitted(child, () -> {
                entered.countDown();
                await(release);
            }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var parentClose = executor.submit(parent.requireMission().lifecycle()::closeNow);
            assertThat(parentClose.isDone()).isFalse();
            release.countDown();
            assertThat(cleanup.get(2, TimeUnit.SECONDS)).isTrue();
            parentClose.get(2, TimeUnit.SECONDS);
        }
    }

    private static ExecutionBinding binding(String id, MissionContext parent)
    {
        return binding(id, parent, System::nanoTime);
    }

    private static ExecutionBinding binding(String id, MissionContext parent, java.util.function.LongSupplier nanoTime)
    {
        LoomspanSession session = parent == null ? new LoomspanSession("session-" + id, id, 8) : parent.session();
        MissionContext mission = new MissionContext(session, id, "mission-" + id, parent, nanoTime);
        return new ExecutionBinding(session, mission, new PhysicalBranchContext(session));
    }

    private static AssignedTaskExecution assignment(String id, int step)
    {
        return new AssignedTaskExecution(new PlanTask(id, id, PlanTaskStatus.PENDING,
                "tool", id, List.of(), List.of(), null, null), step, true);
    }

    private static void await(CountDownLatch latch)
    {
        try { latch.await(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }

}
