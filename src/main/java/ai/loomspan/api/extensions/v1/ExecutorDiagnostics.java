package ai.loomspan.api.extensions.v1;

/**
 * Invocation-scoped bounded developer diagnostics, obtained from
 * {@link SkillExecutorInvocation#diagnostics()}.
 * Applications do not register or supply implementations.
 *
 * <p>The data argument supports null, String, Boolean, exact Byte/Short/Integer/Long,
 * finite Float/Double, BigInteger and BigDecimal; exact HashMap/LinkedHashMap and
 * JDK Map.of/Map.ofEntries/Map.copyOf maps with String keys; exact ArrayList and
 * JDK List.of/List.copyOf lists; reference arrays and primitive boolean/byte/short/
 * int/long/float/double arrays. Custom implementations/subclasses, DTOs, characters,
 * char arrays, adapters and implicit string conversion are unsupported. Cycles are
 * rejected within capture budgets. This is separate from business-value handling.
 *
 * <p>The recording contract contains ordinary validation, capture, recording,
 * limit, notice and fallback-logging failures after method entry. Caller argument
 * evaluation and JVM-wide failures are outside that guarantee. Levels do not
 * change execution outcome or trigger retries. Existing automatic observation
 * retains its own failure semantics.
 *
 * <p>Capture snapshots supported values during the call. Concurrent calls are
 * serialized within the invocation; canonical trace sequence orders delivered
 * records. Calls after executor return/failure or lifecycle cutoff are ignored
 * before data inspection. Concurrent mutation of a supplied container is unsupported.
 *
 * <p>Limits per call: 8 KiB UTF-8 message, 32 KiB JSON data, 48 KiB JSON envelope,
 * depth 32 and 4,096 values/keys. Messages may retain an explicitly marked prefix;
 * incomplete data is unavailable, never malformed JSON. BigInteger and unscaled
 * BigDecimal are limited to 4,096 bits; absolute decimal scale to 4,096.
 * Per invocation: 128 attempts and 256 KiB developer envelopes, four ordinary
 * system notices plus one reserved limit notice (2 KiB each), and four warning
 * attempts. Captures and notices use existing trace frames and sequencing.
 * Notices have no developer severity and delivery is best effort; absence proves
 * no capture condition. Discarded data cannot be recovered from a complete notice.
 */
public interface ExecutorDiagnostics
{
    void info(String message);
    void info(String message, Object data);
    void warn(String message);
    void warn(String message, Object data);
    void error(String message);
    void error(String message, Object data);
}
