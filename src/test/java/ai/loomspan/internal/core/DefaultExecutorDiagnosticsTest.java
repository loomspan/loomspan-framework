package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class DefaultExecutorDiagnosticsTest
{
    final BoundedExecutorDiagnosticCapture capture = new BoundedExecutorDiagnosticCapture();

    @Test void invalidFloodHasFourOrdinaryNoticesFourWarningsAndReservedLimitNotice()
    {
        var types = new ArrayList<TraceRecordType>(); var warnings = new AtomicInteger(); var captures = new AtomicInteger();
        var diagnostics = new DefaultExecutorDiagnostics((level, message, supplied, data) -> {
            captures.incrementAndGet(); return capture.capture(level, message, supplied, data);
        }, capture::notice, (type, data) -> types.add(type), reason -> warnings.incrementAndGet(), () -> true);
        for (int i = 0; i < 1000; i++) diagnostics.info("invalid", new Object());
        assertThat(captures).hasValue(128);
        assertThat(types.stream().filter(t -> t == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED)).hasSize(128);
        assertThat(types.stream().filter(t -> t == TraceRecordType.EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED)).hasSize(5);
        assertThat(warnings).hasValue(4);
    }

    @Test void attemptedBytesAreChargedOnAppendFailureAndReservedNoticeSurvives()
    {
        var attempts = new AtomicInteger(); var notices = new ArrayList<String>();
        var diagnostics = new DefaultExecutorDiagnostics(capture::capture, capture::notice, (type, data) -> {
            if (type == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED) { attempts.incrementAndGet(); throw new IllegalStateException(); }
            notices.add(data.path("reason").asText());
        }, reason -> {}, () -> true);
        for (int i = 0; i < 1000; i++) diagnostics.info("large", "x".repeat(32000));
        assertThat(attempts.get()).isBetween(1, 9);
        assertThat(notices).hasSize(5).endsWith("INVOCATION_BYTE_LIMIT");
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void ordinaryFailuresAtEveryNewPathReturnNormallyAndNeverReportRecursively(int seam)
    {
        var calls = new AtomicInteger();
        var diagnostics = new DefaultExecutorDiagnostics((level, message, supplied, data) -> {
            if (seam == 0) throw new IllegalStateException();
            return capture.capture(level, message, supplied, data);
        }, (reason, message) -> { if (seam == 1 || seam == 4) throw new IllegalStateException(); return capture.notice(reason, message); },
                (type, data) -> { calls.incrementAndGet(); if (seam == 2 || seam == 4) throw new IllegalStateException(); },
                reason -> { if (seam == 3 || seam == 4) throw new IllegalStateException(); }, () -> true);
        assertThatCode(() -> diagnostics.error("same business result", new Object())).doesNotThrowAnyException();
        assertThat(calls.get()).isLessThanOrEqualTo(2);
    }

    @Test void reentryFromAppendAndLoggingIsSuppressedBeforeCapture()
    {
        var current = new AtomicReference<DefaultExecutorDiagnostics>(); var captures = new AtomicInteger();
        var diagnostics = new DefaultExecutorDiagnostics((l,m,s,d) -> { captures.incrementAndGet(); return capture.capture(l,m,s,d); },
                capture::notice, (type, data) -> current.get().info("recursive", new Object()),
                reason -> current.get().warn("recursive", new Object()), () -> true);
        current.set(diagnostics); diagnostics.error("outer", new Object()); assertThat(captures).hasValue(1);
    }

    @Test void closureWaitsForAdmittedCaptureAndLateCallsDoNoWork() throws Exception
    {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var captures = new AtomicInteger();
        var types = new ArrayList<TraceRecordType>();
        var diagnostics = new DefaultExecutorDiagnostics((l,m,s,d) -> {
            captures.incrementAndGet(); entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
            catch (InterruptedException ex) { throw new IllegalStateException(ex); }
            return capture.capture(l,m,s,d);
        }, capture::notice, (type, data) -> types.add(type), reason -> {}, () -> true);
        Thread caller = Thread.ofPlatform().start(() -> diagnostics.info("admitted"));
        try
        {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Thread close = Thread.ofPlatform().start(diagnostics::close);
            release.countDown(); caller.join(5000); close.join(5000);
            assertThat(caller.isAlive()).isFalse(); assertThat(close.isAlive()).isFalse();
            diagnostics.info("late", new Object());
            assertThat(captures).hasValue(1); assertThat(types).hasSize(1);
        }
        finally { release.countDown(); }
    }

    @Test void lifecycleRevocationAvoidsEvenCapture()
    {
        var diagnostics = new DefaultExecutorDiagnostics((l,m,s,d) -> { throw new AssertionError("captured after cutoff"); },
                capture::notice, (type, data) -> { throw new AssertionError(); }, reason -> {}, () -> false);
        diagnostics.info("late", new Object());
    }
}
