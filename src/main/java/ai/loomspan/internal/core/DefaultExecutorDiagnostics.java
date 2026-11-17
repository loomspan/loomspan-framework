package ai.loomspan.internal.core;

import ai.loomspan.api.extensions.v1.ExecutorDiagnostics;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/** One bounded recorder per executor invocation, independent of the calling thread. */
final class DefaultExecutorDiagnostics implements ExecutorDiagnostics
{
    static final int EVENTS = 128, TOTAL_BYTES = 256 * 1024, NOTICES = 4, WARNINGS = 4;
    interface Capture { BoundedExecutorDiagnosticCapture.Captured capture(String level, String message, boolean supplied, Object data); }
    interface Notice { BoundedExecutorDiagnosticCapture.Captured capture(String reason, String message); }
    interface Append { void append(TraceRecordType type, JsonNode data); }
    private final Capture capture;
    private final Notice notice;
    private final Append append;
    private final Consumer<String> warning;
    private final BooleanSupplier writable;
    private int events, bytes, notices, warnings;
    private boolean closed, suppressed, active;

    DefaultExecutorDiagnostics(ExecutionBinding binding, ExecutionFrame frame)
    {
        this(new BoundedExecutorDiagnosticCapture(), binding, frame);
    }

    private DefaultExecutorDiagnostics(BoundedExecutorDiagnosticCapture capture, ExecutionBinding binding, ExecutionFrame frame)
    {
        this(capture::capture, capture::notice,
                (type, data) -> binding.runIfWritable(() -> binding.session().appendTraceRecord(type, frame, Map.of(), data)),
                reason -> LoggerFactory.getLogger(DefaultExecutorDiagnostics.class).warn("Executor diagnostic notice: {}", reason),
                () -> binding.runIfWritable(() -> {}));
    }

    DefaultExecutorDiagnostics(Capture capture, Notice notice, Append append, Consumer<String> warning, BooleanSupplier writable)
    {
        this.capture = capture; this.notice = notice; this.append = append; this.warning = warning; this.writable = writable;
    }

    @Override public void info(String message) { record("INFO", message, false, null); }
    @Override public void info(String message, Object data) { record("INFO", message, true, data); }
    @Override public void warn(String message) { record("WARN", message, false, null); }
    @Override public void warn(String message, Object data) { record("WARN", message, true, data); }
    @Override public void error(String message) { record("ERROR", message, false, null); }
    @Override public void error(String message, Object data) { record("ERROR", message, true, data); }

    private synchronized void record(String level, String message, boolean supplied, Object data)
    {
        if (closed || suppressed || active) return;
        active = true;
        try
        {
            if (!writable.getAsBoolean()) return;
            if (events >= EVENTS) { limit("INVOCATION_EVENT_LIMIT"); return; }
            events++;
            BoundedExecutorDiagnosticCapture.Captured result;
            try { result = capture.capture(level, message, supplied, data); }
            catch (RuntimeException failure) { report("CAPTURE_FAILURE", message, false); return; }
            if (result.bytes() > TOTAL_BYTES - bytes) { limit("INVOCATION_BYTE_LIMIT"); return; }
            bytes += result.bytes();
            try { append.append(TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED, result.data()); }
            catch (RuntimeException failure) { report("RECORDING_FAILURE", message, false); return; }
            if (result.reason() != null) report(result.reason(), message, false);
        }
        catch (RuntimeException failure) { report("CAPTURE_FAILURE", message, false); }
        finally { active = false; }
    }

    private void limit(String reason) { suppressed = true; report(reason, null, true); }

    private void report(String reason, String message, boolean reserved)
    {
        if (reserved || notices < NOTICES)
        {
            if (!reserved) notices++;
            try
            {
                var result = notice.capture(reason, message);
                append.append(TraceRecordType.EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED, result.data());
            }
            catch (RuntimeException ignored) { /* Best effort; never recursively report reporting failures. */ }
        }
        if (warnings < WARNINGS)
        {
            warnings++;
            try { warning.accept(reason); }
            catch (RuntimeException ignored) { /* Independent best-effort warning. */ }
        }
    }

    synchronized void close() { closed = true; }
}
