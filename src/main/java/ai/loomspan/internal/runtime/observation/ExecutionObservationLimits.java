package ai.loomspan.internal.runtime.observation;

import java.nio.charset.StandardCharsets;

public final class ExecutionObservationLimits
{
    public static final int TEXT_CODE_POINTS = 256;
    public static final int SUMMARY_CODE_POINTS = 512;
    public static final int DETAIL_FIELDS = 32;
    public static final int DETAIL_UTF8_BYTES = 8 * 1024;
    public static final int ACTIVITY_UTF8_BYTES = 12 * 1024;
    public static final int REPLAY_EVENTS = 10_000;
    public static final long REPLAY_UTF8_BYTES = 16L * 1024L * 1024L;

    private ExecutionObservationLimits()
    {
    }

    static String truncate(String value, int maximumCodePoints)
    {
        if (value == null)
        {
            return "";
        }
        int count = value.codePointCount(0, value.length());
        if (count <= maximumCodePoints)
        {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
    }

    static int utf8Weight(String value)
    {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
