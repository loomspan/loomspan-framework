package ai.loomspan.api.extensions.v1;

/**
 * Invocation-scoped developer diagnostic capability declared by the v1 contract.
 * Recording is currently unavailable through {@link SkillExecutorInvocation#diagnostics()}.
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
 * retains its own failure semantics. Recording and its bounds are not implemented
 * by these declarations.
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
