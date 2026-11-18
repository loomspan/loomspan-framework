package ai.loomspan.internal.core;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** Test-only seams; production exposes no replacement or construction SPI. */
public final class ExecutorDiagnosticTestSupport
{
    private ExecutorDiagnosticTestSupport() {}
    public static List<TraceRecord> logicalRecords(LoomspanSession session)
    {
        var physical = new ArrayList<TraceRecord>(); session.readTraceRecords(physical::add);
        var result = new ArrayList<TraceRecord>();
        for (var record : physical)
        {
            if (record.recordType() == TraceRecordType.PAYLOAD_CHUNK_APPENDED) continue;
            if (Boolean.TRUE.equals(record.metadata().get("payloadChunked")))
            {
                String payloadId = (String) record.metadata().get("payloadId");
                String content = physical.stream().filter(r -> r.recordType() == TraceRecordType.PAYLOAD_CHUNK_APPENDED
                        && payloadId.equals(r.metadata().get("payloadId")))
                        .sorted(Comparator.comparingInt(r -> ((Number) r.metadata().get("chunkIndex")).intValue()))
                        .map(r -> r.data().asText()).collect(java.util.stream.Collectors.joining());
                var json = new tools.jackson.databind.ObjectMapper();
                var data = "application/json".equals(record.metadata().get("contentType")) ? json.readTree(content) : json.getNodeFactory().stringNode(content);
                record = new TraceRecord(record.traceId(), record.sessionId(), record.sequence(), record.timestamp(),
                        record.recordType(), record.frameId(), record.parentFrameId(), record.frameType(), record.route(), record.threadName(), record.metadata(), data);
            }
            result.add(record);
        }
        return result;
    }
    public static void fail(SkillExecutorInvocation invocation, int seam)
    {
        Object handle = invocation.diagnostics();
        if (seam == 0) ReflectionTestUtils.setField(handle, "capture", (DefaultExecutorDiagnostics.Capture)
                (l,m,s,d) -> { throw new IllegalStateException("injected capture failure"); });
        if (seam == 1 || seam == 4) ReflectionTestUtils.setField(handle, "notice", (DefaultExecutorDiagnostics.Notice)
                (r,m) -> { throw new IllegalStateException("injected fallback failure"); });
        if (seam == 2 || seam == 4) ReflectionTestUtils.setField(handle, "append", (DefaultExecutorDiagnostics.Append)
                (t,d) -> { throw new IllegalStateException("injected recording failure"); });
        if (seam == 3 || seam == 4) ReflectionTestUtils.setField(handle, "warning", (java.util.function.Consumer<String>)
                r -> { throw new IllegalStateException("injected logging failure"); });
    }

    public static void failExecutorCompletionRecord(LoomspanSession session) {
        var real = (ExecutionTraceHandle) ReflectionTestUtils.getField(session, "executionTraceHandle");
        var handle = mock(ExecutionTraceHandle.class, delegatesTo(real));
        try {
            doThrow(new IOException("injected completion write failure")).when(handle).append(
                    eq(TraceRecordType.FRAME_METADATA_RECORDED), any(), any(), anyMap(),
                    argThat(data -> data instanceof Map<?, ?> map && map.containsKey("executorOutput")));
        } catch (IOException ex) {
            throw new AssertionError("failed to install completion write failure", ex);
        }
        ReflectionTestUtils.setField(session, "executionTraceHandle", handle);
    }

    public static void mixedFixture(ExecutionBinding binding, ExecutionFrame frame)
    {
        var diagnostics = new DefaultExecutorDiagnostics(binding, frame);
        var fidelity = new java.util.LinkedHashMap<String, Object>();
        fidelity.put("Authorization", "kept verbatim"); fidelity.put("token", "original");
        diagnostics.info("  request 雪\n", fidelity);
        diagnostics.warn("explicit JSON null", null);
        diagnostics.error("authored error keeps success");
        diagnostics.info("chunked JSON", Map.of("payload", "x".repeat(5000)));
        diagnostics.info("m".repeat(9000));
        diagnostics.warn("unsupported supplied JSON", new Object());
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 8; index++)
                futures.add(workers.submit(() -> diagnostics.info("concurrent event", Map.of("workerEvidence", "retained"))));
            for (var future : futures) future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
        diagnostics.close();
    }

    public static void accountingFixture(ExecutionBinding binding, ExecutionFrame frame) {
        var diagnostics = new DefaultExecutorDiagnostics(binding, frame);
        try (var server = new okhttp3.mockwebserver.MockWebServer()) {
            server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(503));
            server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody("approved")); server.start();
            try (var client = java.net.http.HttpClient.newHttpClient()) {
                var request = java.net.http.HttpRequest.newBuilder(server.url("/operation").uri()).GET().build();
                var first = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                var failedEvidence = new java.util.LinkedHashMap<String,Object>();
                failedEvidence.put("httpStatus", first.statusCode()); failedEvidence.put("usage", Map.of("totalUnits", 999999)); failedEvidence.put("outcome", "FAILED");
                diagnostics.error("caught request failure; retry", failedEvidence);
                var second = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                var evidence = new java.util.LinkedHashMap<String,Object>();
                evidence.put("httpStatus", second.statusCode()); evidence.put("usage", null); evidence.put("retry", true);
                diagnostics.info("request succeeded", evidence);
                diagnostics.warn("unavailable supplied JSON", new Object());
                if (server.getRequestCount() != 2) throw new AssertionError("Executor HTTP request inventory");
            }
        } catch (Exception ex) { throw new IllegalStateException(ex); }
        diagnostics.close();
    }

    public static void failureFixture(ExecutionBinding binding, ExecutionFrame frame) {
        var diagnostics = new DefaultExecutorDiagnostics(binding, frame);
        var evidence = new java.util.LinkedHashMap<String,Object>(); evidence.put("outcome", "SUCCEEDED"); evidence.put("usage", 999999);
        diagnostics.info("before designated failure", evidence);
        diagnostics.close();
    }
}
