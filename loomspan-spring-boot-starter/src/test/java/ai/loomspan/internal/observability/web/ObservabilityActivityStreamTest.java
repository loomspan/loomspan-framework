package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.observability.web.dto.ObservabilityDtos;
import ai.loomspan.internal.runtime.observation.ExecutionActivityKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityActivityStreamTest
{
    private final ObservabilityJsonCodec json = new ObservabilityJsonCodec();

    @Test
    void framesHandshakeWithoutId() throws Exception
    {
        byte[] frame = ObservabilityActivityStream.handshakeFrame(
                json,
                new ObservabilityDtos.ActivityHandshake(
                        "123e4567-e89b-12d3-a456-426614174000",
                        Instant.parse("2026-07-26T12:00:00Z"),
                        "41"));

        assertThat(new String(frame, StandardCharsets.UTF_8)).isEqualTo(
                "event: handshake\n"
                        + "data: {\"instanceId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"observedAt\":\"2026-07-26T12:00:00Z\",\"afterCursor\":\"41\"}\n\n");
    }

    @Test
    void framesActivityWithCursorAsIdAndSingleJsonDataLine() throws Exception
    {
        var activity = new ObservabilityDtos.ActivityEnvelope(
                "123e4567-e89b-12d3-a456-426614174000",
                "42",
                "session",
                "trace",
                7L,
                Instant.parse("2026-07-26T12:00:01Z"),
                ExecutionActivityKind.TOOL_CALL_STARTED,
                "ACTIVE",
                "frame",
                "parent",
                ai.loomspan.internal.core.TraceFrameType.TOOL_INVOCATION,
                "route",
                "line one\nline two",
                Map.of());

        String frame = new String(ObservabilityActivityStream.activityFrame(json, activity), StandardCharsets.UTF_8);

        assertThat(frame).startsWith("id: 42\nevent: activity\ndata: ")
                .endsWith("\n\n")
                .contains("\"cursor\":\"42\"", "\"parentFrameId\":\"parent\"", "line one\\nline two")
                .doesNotContain("consoleCompatibilityVersion");
        assertThat(frame.lines().filter(line -> line.startsWith("data:")).count()).isEqualTo(1);
    }

    @Test
    void productionDeliveryLimitsRemainFixed()
    {
        assertThat(ObservabilityDeliveryLimits.OPEN_SUBSCRIPTIONS).isEqualTo(16);
        assertThat(ObservabilityDeliveryLimits.PENDING_ACTIVITY_FRAMES).isEqualTo(256);
        assertThat(ObservabilityDeliveryLimits.PENDING_BYTES).isEqualTo(1024L * 1024L);
        assertThat(ObservabilityDeliveryLimits.REPLAY_BATCH).isEqualTo(256);
        assertThat(ObservabilityDeliveryLimits.WRITE_READINESS_DEADLINE).isEqualTo(java.time.Duration.ofSeconds(5));
    }

    @Test
    void writesOnlyWhileReadyAndClosesOnFirstEventOverBound() throws Exception
    {
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json))
        {
            var admission = delivery.admit(0);
            RecordingOutput output = new RecordingOutput();
            var stream = new ObservabilityActivityStream(
                    delivery, admission, mock(jakarta.servlet.AsyncContext.class), output);
            byte[] frame = "id: 1\nevent: activity\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8);

            for (int index = 0; index < ObservabilityDeliveryLimits.PENDING_ACTIVITY_FRAMES; index++)
            {
                assertThat(stream.offer(frame, index + 1)).isTrue();
            }
            assertThat(output.bytes.size()).isZero();
            assertThat(stream.offer(frame, 257)).isFalse();

            output.ready = true;
            stream.onWritePossible();
            assertThat(output.bytes.size()).isEqualTo(
                    frame.length * ObservabilityDeliveryLimits.PENDING_ACTIVITY_FRAMES);
            stream.close();
            assertThat(delivery.admittedCount()).isZero();
        }
    }

    @Test
    void countsCompleteFrameBytesAgainstPendingLimit()
    {
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json))
        {
            var admission = delivery.admit(0);
            var stream = new ObservabilityActivityStream(
                    delivery, admission, mock(jakarta.servlet.AsyncContext.class), new RecordingOutput());
            byte[] atLimit = new byte[(int) ObservabilityDeliveryLimits.PENDING_BYTES];

            assertThat(stream.offer(atLimit, 1)).isTrue();
            assertThat(stream.offer(new byte[] { 1 }, 2)).isFalse();
            stream.close();
        }
    }

    @Test
    void closesOnlyTheStalledSubscriberWhenItsHeadDeadlineExpires()
    {
        ExecutorService dispatcher = mock(ExecutorService.class);
        ScheduledExecutorService deadlines = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        org.mockito.ArgumentCaptor<Runnable> deadline = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(deadlines).schedule(
                deadline.capture(),
                anyLong(),
                eq(TimeUnit.NANOSECONDS));
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json,
                dispatcher,
                deadlines))
        {
            var admission = delivery.admit(0);
            var stream = new ObservabilityActivityStream(
                    delivery, admission, mock(jakarta.servlet.AsyncContext.class), new RecordingOutput());

            assertThat(stream.offer(new byte[] { 1 }, 1)).isTrue();
            assertThat(deadline.getValue()).isNotNull();

            deadline.getValue().run();

            assertThat(delivery.admittedCount()).isZero();
            assertThat(stream.offer(new byte[] { 2 }, 2)).isFalse();
        }
    }

    @Test
    void staleDeadlineCannotCloseAReplacementHeadFrame()
    {
        ExecutorService dispatcher = mock(ExecutorService.class);
        ScheduledExecutorService deadlines = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        org.mockito.ArgumentCaptor<Runnable> deadlinesCaptured =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(deadlines).schedule(
                deadlinesCaptured.capture(),
                anyLong(),
                eq(TimeUnit.NANOSECONDS));
        jakarta.servlet.AsyncContext async = mock(jakarta.servlet.AsyncContext.class);
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json,
                dispatcher,
                deadlines))
        {
            var admission = delivery.admit(0);
            RecordingOutput output = new RecordingOutput();
            var stream = new ObservabilityActivityStream(delivery, admission, async, output);

            assertThat(stream.offer(new byte[] { 1 }, 1)).isTrue();
            output.ready = true;
            stream.onWritePossible();
            output.ready = false;
            assertThat(stream.offer(new byte[] { 2 }, 2)).isTrue();

            assertThat(deadlinesCaptured.getAllValues()).hasSize(2);
            deadlinesCaptured.getAllValues().getFirst().run();

            assertThat(delivery.admittedCount()).isEqualTo(1);
            verify(async, org.mockito.Mockito.never()).complete();

            deadlinesCaptured.getAllValues().getLast().run();

            assertThat(delivery.admittedCount()).isZero();
            verify(async).complete();
        }
    }

    @Test
    void failureBeforeAsyncOwnershipReleasesAdmissionAndPropagates()
    {
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json))
        {
            var admission = delivery.admit(0);
            jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
            jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
            when(request.startAsync(request, response)).thenThrow(new IllegalStateException("not async"));

            assertThatThrownBy(() -> ObservabilityActivityStream.open(
                    request, response, delivery, admission, new byte[] { 1 }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(delivery.admittedCount()).isZero();
        }
    }

    @Test
    void failureAfterAsyncOwnershipClosesContextAndReleasesAdmission()
    {
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json))
        {
            var admission = delivery.admit(0);
            jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
            jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
            jakarta.servlet.AsyncContext async = mock(jakarta.servlet.AsyncContext.class);
            when(request.startAsync(request, response)).thenReturn(async);
            doThrow(new IllegalStateException("container stopped")).when(async).setTimeout(0);

            ObservabilityActivityStream.open(request, response, delivery, admission, new byte[] { 1 });

            assertThat(delivery.admittedCount()).isZero();
            verify(async).complete();
        }
    }

    @Test
    void overlappingAsyncCallbacksReleaseAdmissionExactlyOnce()
    {
        try (ObservabilityActivityDelivery delivery = new ObservabilityActivityDelivery(
                "123e4567-e89b-12d3-a456-426614174000",
                new ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer(),
                new ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability(),
                new ObservabilityDtoMapper(),
                json))
        {
            var admission = delivery.admit(0);
            jakarta.servlet.AsyncContext async = mock(jakarta.servlet.AsyncContext.class);
            var stream = new ObservabilityActivityStream(delivery, admission, async, new RecordingOutput());

            stream.onTimeout(mock(jakarta.servlet.AsyncEvent.class));
            stream.onError(mock(jakarta.servlet.AsyncEvent.class));
            stream.onComplete(mock(jakarta.servlet.AsyncEvent.class));
            stream.onError(new IllegalStateException("write failed"));

            assertThat(delivery.admittedCount()).isZero();
            verify(async).complete();
        }
    }

    private static final class RecordingOutput extends jakarta.servlet.ServletOutputStream
    {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean ready;

        @Override
        public boolean isReady()
        {
            return ready;
        }

        @Override
        public void setWriteListener(jakarta.servlet.WriteListener listener)
        {
        }

        @Override
        public void write(int value)
        {
            bytes.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length)
        {
            bytes.write(values, offset, length);
        }
    }
}
