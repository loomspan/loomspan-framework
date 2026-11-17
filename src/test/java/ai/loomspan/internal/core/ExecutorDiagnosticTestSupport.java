package ai.loomspan.internal.core;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

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
        diagnostics.close();
    }
}
