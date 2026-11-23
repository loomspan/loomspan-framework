package ai.loomspan.internal.runtime.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import ai.loomspan.internal.core.FinalizedTraceArtifact;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.core.TracePersistencePolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledCompletionGraceRetentionTest
{
    @TempDir
    Path tempDir;

    @Test
    void schedulingCannotPublishAfterExpiryClosureOrRejection() throws Exception
    {
        for (String ordering : java.util.List.of("inline-expiry", "deadline", "close", "reject"))
        {
            MutableClock clock = new MutableClock(Instant.parse("2026-07-26T12:00:00Z"));
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            var retention = new ScheduledCompletionGraceRetention(Duration.ofMinutes(1), clock, executor);
            Path active = Files.writeString(tempDir.resolve(ordering + ".in-progress"), "{}\n");
            Path completed = tempDir.resolve(ordering + ".ndjson");
            when(executor.schedule(any(Runnable.class), anyLong(), eq(java.util.concurrent.TimeUnit.NANOSECONDS)))
                    .thenAnswer(call -> {
                        assertThat(completed).doesNotExist();
                        if (ordering.equals("reject")) throw new java.util.concurrent.RejectedExecutionException();
                        if (ordering.equals("close")) retention.close();
                        else {
                            clock.advance(Duration.ofMinutes(1));
                            if (ordering.equals("inline-expiry")) ((Runnable) call.getArgument(0)).run();
                        }
                        return future;
                    });
            assertThat(retention.retainOrDelete(active, completed, clock.instant(), "trace", "session")).isEmpty();
            assertThat(active).doesNotExist();
            assertThat(completed).doesNotExist();
            assertThat(retention.retainedArtifactCount()).isZero();
            if (!ordering.equals("reject")) verify(future).cancel(false);
            retention.close();
        }
    }

    @Test
    void failedPublicationCancelsPendingOwnershipAndKeepsActiveEvidence() throws Exception
    {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-26T12:00:00Z"));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor).schedule(any(Runnable.class), anyLong(), eq(java.util.concurrent.TimeUnit.NANOSECONDS));
        var retention = new ScheduledCompletionGraceRetention(Duration.ofMinutes(1), clock, executor);
        Path active = Files.writeString(tempDir.resolve("failed.in-progress"), "{}\n");
        Path completed = Files.createDirectory(tempDir.resolve("blocked.ndjson"));
        Path marker = Files.writeString(completed.resolve("marker"), "owned");
        assertThatThrownBy(() -> retention.retainOrDelete(active, completed, clock.instant(), "trace", "session"))
                .isInstanceOf(java.io.IOException.class);
        verify(future).cancel(false);
        assertThat(retention.retainedArtifactCount()).isZero();
        assertThat(active).exists();
        assertThat(Files.readString(marker)).isEqualTo("owned");
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(task.capture(), anyLong(), eq(java.util.concurrent.TimeUnit.NANOSECONDS));
        task.getValue().run();
        assertThat(active).exists();
        retention.close();
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void concurrentExpiryOrCloseWhileSchedulingNeverPublishes() throws Exception
    {
        for (boolean close : java.util.List.of(false, true))
        {
            MutableClock clock = new MutableClock(Instant.parse("2026-07-26T12:00:00Z"));
            ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            var retention = new ScheduledCompletionGraceRetention(Duration.ofMinutes(1), clock, scheduler);
            var scheduled = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var task = new java.util.concurrent.atomic.AtomicReference<Runnable>();
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(java.util.concurrent.TimeUnit.NANOSECONDS)))
                    .thenAnswer(call -> {
                        task.set(call.getArgument(0));
                        scheduled.countDown();
                        if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("schedule release timed out");
                        return future;
                    });
            Path active = Files.writeString(tempDir.resolve("concurrent-" + close + ".in-progress"), "{}\n");
            Path completed = tempDir.resolve("concurrent-" + close + ".ndjson");
            try (var workers = java.util.concurrent.Executors.newSingleThreadExecutor())
            {
                var result = workers.submit(() -> retention.retainOrDelete(active, completed, clock.instant(), "trace", "session"));
                try {
                    assertThat(scheduled.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    if (close) retention.close();
                    else {
                        clock.advance(Duration.ofMinutes(1));
                        task.get().run();
                    }
                    assertThat(active).doesNotExist();
                    assertThat(completed).doesNotExist();
                } finally { release.countDown(); }
                assertThat(result.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEmpty();
                verify(future).cancel(false);
                assertThat(retention.retainedArtifactCount()).isZero();
            } finally { retention.close(); }
        }
    }

    @Test
    void zeroGraceDeletesSynchronouslyAndNonzeroCloseCancelsWithoutDeleting() throws Exception
    {
        Path immediate = Files.writeString(tempDir.resolve("immediate"), "trace");
        try (ScheduledCompletionGraceRetention retention =
                     new ScheduledCompletionGraceRetention(Duration.ZERO))
        {
            assertThat(retention.retainOrDelete(
                    immediate, tempDir.resolve("immediate-completed"), Instant.parse("2026-07-24T12:00:00Z"), "trace", "session")).isEmpty();
        }
        assertThat(immediate).doesNotExist();

        Path held = Files.writeString(tempDir.resolve("held"), "trace");
        ScheduledCompletionGraceRetention retention =
                new ScheduledCompletionGraceRetention(Duration.ofHours(1));
        Instant finalizedAt = Instant.now();
        assertThat(retention.retainOrDelete(
                held, tempDir.resolve("held-completed"), finalizedAt, "trace", "session"))
                .contains(new CompletionGraceRetention.RetainedArtifact(
                        finalizedAt.plus(Duration.ofHours(1)),
                        Files.size(tempDir.resolve("held-completed"))));
        retention.close();
        assertThat(held).doesNotExist();
        assertThat(tempDir.resolve("held-completed")).exists();
    }

    @Test
    void rejectsNegativeGrace()
    {
        assertThatThrownBy(() -> new ScheduledCompletionGraceRetention(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leaseOpenedBeforeDeadlineDefersDueDeletionUntilClose() throws Exception
    {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-26T12:00:00Z"));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor).schedule(
                any(Runnable.class), anyLong(), eq(java.util.concurrent.TimeUnit.NANOSECONDS));
        Path file = Files.writeString(tempDir.resolve("leased.ndjson"), "{}\n");
        ScheduledCompletionGraceRetention retention =
                new ScheduledCompletionGraceRetention(Duration.ofMinutes(1), clock, executor);
        Instant finalizedAt = clock.instant();
        var retained = retention.retainOrDelete(file, tempDir.resolve("completed.ndjson"), finalizedAt, "trace", "session").orElseThrow();
        FinalizedTraceArtifact artifact = new FinalizedTraceArtifact(
                "trace", "session", "default-test_entry", "generation-test", "default", java.util.Map.of("default", "generation-test"), TraceOutcome.SUCCEEDED, finalizedAt, tempDir.resolve("completed.ndjson"),
                retained.sizeBytes(), TracePersistencePolicy.NEVER, retained.expiresAt());
        CompletionGraceRetention.ArtifactLease lease = retention.acquire(artifact).orElseThrow();
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(task.capture(), anyLong(), eq(java.util.concurrent.TimeUnit.NANOSECONDS));

        clock.advance(Duration.ofMinutes(1));
        task.getValue().run();

        assertThat(tempDir.resolve("completed.ndjson")).exists();
        assertThat(retention.acquire(artifact)).isEmpty();
        assertThat(lease.input().readAllBytes()).isEqualTo("{}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        lease.close();
        lease.close();
        assertThat(file).doesNotExist();
        assertThat(tempDir.resolve("completed.ndjson")).doesNotExist();
        assertThat(retention.retainedArtifactCount()).isZero();
        retention.close();
    }

    @Test
    void nonExpiringArtifactLeaseNeverTransfersDeletionOwnership() throws Exception
    {
        Path file = Files.writeString(tempDir.resolve("always.ndjson"), "{}\n");
        try (ScheduledCompletionGraceRetention retention =
                     new ScheduledCompletionGraceRetention(Duration.ofMinutes(1)))
        {
            FinalizedTraceArtifact artifact = new FinalizedTraceArtifact(
                    "always", "session", "default-test_entry", "generation-test", "default", java.util.Map.of("default", "generation-test"), TraceOutcome.SUCCEEDED, Instant.now(), file,
                    Files.size(file), TracePersistencePolicy.ALWAYS, null);
            try (CompletionGraceRetention.ArtifactLease lease = retention.acquire(artifact).orElseThrow())
            {
                assertThat(lease.sizeBytes()).isEqualTo(Files.size(file));
            }
        }
        assertThat(file).exists();
    }

    private static final class MutableClock extends Clock
    {
        private Instant instant;

        private MutableClock(Instant instant)
        {
            this.instant = instant;
        }

        void advance(Duration duration)
        {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
