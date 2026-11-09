package ai.loomspan.internal.core;

import java.nio.charset.StandardCharsets;

/** One bounded opaque text diagnostic embedded in trace content. */
record TraceDiagnostic(String kind, String contentType, String text, boolean truncated, int captureLimitBytes)
{
    static TraceDiagnostic plannerInabilitySignal(byte[] utf8)
    {
        int limit = BoundedStackTraceCapture.LIMIT_BYTES;
        boolean truncated = utf8.length > limit;
        int end = Math.min(utf8.length, limit);
        if (truncated)
        {
            while ((utf8[end] & 0xC0) == 0x80) end--;
        }
        String text = new String(utf8, 0, end, StandardCharsets.UTF_8);
        return new TraceDiagnostic("PLANNER_INABILITY_SIGNAL", BoundedStackTraceCapture.CONTENT_TYPE, text, truncated, limit);
    }
}
