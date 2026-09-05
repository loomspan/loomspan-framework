package ai.loomspan.internal.core;

/** One bounded opaque text diagnostic embedded in trace content. */
record TraceDiagnostic(String kind, String contentType, String text, boolean truncated, int captureLimitBytes)
{
}
