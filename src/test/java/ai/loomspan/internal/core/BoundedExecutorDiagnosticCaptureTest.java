package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BoundedExecutorDiagnosticCaptureTest
{
    final BoundedExecutorDiagnosticCapture capture = new BoundedExecutorDiagnosticCapture();
    private BoundedExecutorDiagnosticCapture.Captured data(Object value) { return capture.capture("INFO", "  雪\n", true, value); }
    private void reason(Object value, String reason)
    {
        var result = data(value);
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.data().path("dataState").asText()).isEqualTo("UNAVAILABLE");
        assertThat(result.data().has("data")).isFalse();
    }

    @Test void capturesEveryApprovedConcreteTypeAndSnapshotsWithoutMasking()
    {
        List<Object> values = Arrays.asList(null, true, "雪🧭\n\"\\", (byte) 1, (short) 2, 3, 4L, 1.25f, 2.5d,
                new BigInteger("9007199254740993"), new BigDecimal("0.12345678901234567890123456789"),
                new HashMap<>(Map.of("a", 1)), new LinkedHashMap<>(Map.of("a", 1)), Map.of(), Map.of("a", 1),
                Map.of("a", 1, "b", 2), Map.copyOf(Map.of("a", 1)), new ArrayList<>(), List.of(), List.of(1), List.of(1, 2),
                List.of(1, 2, 3), List.copyOf(List.of(1)), new Object[]{null, "a"}, new String[]{"a"},
                new boolean[]{true}, new byte[]{1}, new short[]{1}, new int[]{1}, new long[]{1}, new float[]{1}, new double[]{1});
        for (Object value : values)
        {
            var result = data(value);
            assertThat(result.reason()).isNull();
            assertThat(result.data().path("dataState").asText()).isEqualTo("COMPLETE");
            assertThat(result.bytes()).isLessThanOrEqualTo(BoundedExecutorDiagnosticCapture.ENVELOPE_BYTES);
        }
        assertThat(data(values.get(10)).data().path("data").decimalValue()).isEqualByComparingTo((BigDecimal) values.get(10));
        var list = new ArrayList<Object>(List.of("before"));
        var map = new LinkedHashMap<String, Object>(); map.put("Authorization", list); map.put("token", "unchanged");
        var snapshot = data(map).data(); list.clear(); map.clear();
        assertThat(snapshot.path("data").path("Authorization").get(0).asText()).isEqualTo("before");
        assertThat(snapshot.path("data").path("token").asText()).isEqualTo("unchanged");
        assertThat(capture.capture("WARN", "x", false, null).data().path("dataState").asText()).isEqualTo("ABSENT");
        assertThat(data(null).data().get("data").isNull()).isTrue();
    }

    @Test void rejectsUnsupportedValuesBeforeCallingAnyApplicationCode()
    {
        Object hostile = new Object() { @Override public String toString() { throw new AssertionError("rendered"); } };
        var map = new HashMap<>() {
            @Override public Set<Entry<Object,Object>> entrySet() { throw new AssertionError("traversed subclass"); }
            @Override public int size() { throw new AssertionError("measured subclass"); }
        };
        var list = new ArrayList<>() { @Override public Iterator<Object> iterator() { throw new AssertionError("traversed subclass"); } };
        Number number = new Number() {
            public int intValue() { throw new AssertionError(); } public long longValue() { throw new AssertionError(); }
            public float floatValue() { throw new AssertionError(); } public double doubleValue() { throw new AssertionError(); }
            @Override public String toString() { throw new AssertionError(); }
        };
        for (Object value : List.of(hostile, map, list, number, 'x', new char[]{'x'}, Double.NaN, Float.POSITIVE_INFINITY,
                Collections.unmodifiableMap(Map.of("x", 1)), Arrays.asList(1), Map.of(1, "bad")))
            reason(value, "UNSUPPORTED_VALUE");
    }

    @Test void detectsCyclesButAllowsRepeatedAcyclicAliases()
    {
        var list = new ArrayList<>(); list.add(list); reason(list, "CYCLE");
        var map = new HashMap<String,Object>(); Object[] array = {map}; map.put("cycle", array); reason(map, "CYCLE");
        Object[] direct = new Object[1]; direct[0] = direct; reason(direct, "CYCLE");
        var alias = List.of("same"); assertThat(data(List.of(alias, alias)).reason()).isNull();
    }

    @Test void enforcesDepthWorkDataAndNumberBudgetsBeforeLargeConversions()
    {
        Object deep = null; for (int i = 0; i < 32; i++) deep = new Object[]{deep};
        assertThat(data(deep).reason()).isNull(); reason(new Object[]{deep}, "DEPTH_LIMIT");
        assertThat(data(new int[4095]).reason()).isNull(); reason(new int[4096], "TRAVERSAL_LIMIT");
        assertThat(data("a".repeat(32766)).reason()).isNull(); reason("a".repeat(32767), "DATA_LIMIT");
        assertThat(data("🧭".repeat(8000)).reason()).isNull();
        reason("\u0001".repeat(6000), "DATA_LIMIT");
        reason(Map.of("x".repeat(40000), 1), "DATA_LIMIT");
        reason(BigInteger.ONE.shiftLeft(100000), "NUMBER_LIMIT");
        reason(new BigDecimal(BigInteger.ONE.shiftLeft(100000)), "NUMBER_LIMIT");
        reason(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE), "NUMBER_LIMIT");
        reason(new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE), "NUMBER_LIMIT");
        assertThat(data(new BigDecimal(BigInteger.ONE, 4096)).reason()).isNull();
    }

    @Test void exactDecimalCannotSmuggleAnArbitraryUnscaledNumberSubclass()
    {
        class HostileInteger extends BigInteger
        {
            boolean armed;
            HostileInteger() { super("123456789012345678901234567890"); }
            @Override public int bitLength() { if (armed) throw new AssertionError("custom number conversion"); return super.bitLength(); }
            @Override public String toString() { if (armed) throw new AssertionError("custom number rendering"); return super.toString(); }
        }
        var unscaled = new HostileInteger();
        var decimal = new BigDecimal(unscaled);
        unscaled.armed = true;
        // JDKs may defensively normalize the stored integer themselves.
        if (decimal.unscaledValue().getClass() == BigInteger.class) assertThat(data(decimal).reason()).isNull();
        else reason(decimal, "UNSUPPORTED_VALUE");
    }

    @Test void shortControlEscapesUseTheirActualJsonByteBudgetForValuesAndKeys()
    {
        for (String control : List.of("\n", "\r", "\t", "\b", "\f"))
        {
            String atLimit = control.repeat(16383); // Two quotes plus 16,383 two-byte escapes.
            var result = data(atLimit);
            assertThat(result.reason()).isNull();
            assertThat(result.data().path("data").asText()).isEqualTo(atLimit);
            reason(atLimit + control, "DATA_LIMIT");
            String key = control.repeat(8000);
            var object = data(Map.of(key, true));
            assertThat(object.reason()).isNull();
            assertThat(object.data().path("data").path(key).asBoolean()).isTrue();
        }
    }

    @Test void truncatesMessageAtCodePointBoundaryAndKeepsFixedNoticeBounded()
    {
        String message = "🧭".repeat(2048) + "tail";
        var result = capture.capture("ERROR", message, false, null);
        assertThat(result.data().path("message").asText()).isEqualTo("🧭".repeat(2048));
        assertThat(result.data().path("messageState").asText()).isEqualTo("TRUNCATED");
        assertThat(result.reason()).isEqualTo("MESSAGE_LIMIT");
        var notice = capture.notice("MESSAGE_LIMIT", "\u0001".repeat(100000));
        assertThat(notice.bytes()).isLessThanOrEqualTo(2048);
        assertThat(notice.data().has("severity")).isFalse();
        assertThat(capture.capture("INFO", null, true, null).data().path("messageState").asText()).isEqualTo("UNAVAILABLE");
        var amplified = capture.capture("INFO", "\u0001".repeat(8192), true, "x".repeat(32000));
        assertThat(amplified.bytes()).isLessThanOrEqualTo(49152);
        assertThat(amplified.data().path("messageState").asText()).isEqualTo("TRUNCATED");
    }

    @Test void envelopeOverflowDoesNotMarkAnUnchangedMessageAsTruncated()
    {
        String message = "\u0001".repeat(3000);
        var result = capture.capture("INFO", message, true, "x".repeat(32000));
        assertThat(result.reason()).isEqualTo("DATA_LIMIT");
        assertThat(result.data().path("dataState").asText()).isEqualTo("UNAVAILABLE");
        assertThat(result.data().has("data")).isFalse();
        assertThat(result.data().path("message").asText()).isEqualTo(message);
        assertThat(result.data().path("messageState").asText()).isEqualTo("COMPLETE");
        assertThat(result.bytes()).isLessThanOrEqualTo(BoundedExecutorDiagnosticCapture.ENVELOPE_BYTES);
    }

    @Test void numberGuardPreservesSignedBitLengthBoundariesAndRejectsHugeNegativeMagnitudes()
    {
        BigInteger minimum = BigInteger.ONE.shiftLeft(4096).negate();
        BigInteger maximum = minimum.negate().subtract(BigInteger.ONE);
        for (BigInteger accepted : List.of(minimum, maximum))
        {
            assertThat(data(accepted).reason()).isNull();
            assertThat(data(new BigDecimal(accepted)).reason()).isNull();
        }
        for (BigInteger rejected : List.of(minimum.subtract(BigInteger.ONE), maximum.add(BigInteger.ONE),
                BigInteger.ONE.shiftLeft(1_000_000).negate()))
        {
            reason(rejected, "NUMBER_LIMIT");
            reason(new BigDecimal(rejected), "NUMBER_LIMIT");
        }
    }
}
