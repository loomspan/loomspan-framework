package ai.loomspan.internal.core;

import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.*;
import java.lang.reflect.Array;
import java.math.*;
import java.util.*;

/** Exact-class JSON snapshotting. Never hands application objects to a serializer. */
final class BoundedExecutorDiagnosticCapture
{
    static final int MESSAGE_BYTES = 8 * 1024, DATA_BYTES = 32 * 1024, ENVELOPE_BYTES = 48 * 1024;
    static final int DEPTH = 32, WORK = 4096, NOTICE_BYTES = 2048;
    private static final BigInteger MIN_NUMBER = BigInteger.ONE.shiftLeft(4096).negate();
    private static final BigInteger MAX_NUMBER = MIN_NUMBER.negate().subtract(BigInteger.ONE);
    private static final Set<Class<?>> MAPS = Set.of(HashMap.class, LinkedHashMap.class,
            Map.of().getClass(), Map.of("a", 1).getClass());
    private static final Set<Class<?>> LISTS = Set.of(ArrayList.class, List.of().getClass(), List.of(1).getClass());
    private final JsonFactory factory = JsonFactory.builder()
            .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(DEPTH + 4).build())
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(DEPTH + 4)
                    .maxNumberLength(8192).maxStringLength(ENVELOPE_BYTES).build()).build();
    private final ObjectMapper json = JsonMapper.builder(factory)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    record Captured(JsonNode data, int bytes, String reason) {}
    static final class Rejected extends RuntimeException
    {
        final String reason;
        Rejected(String reason) { super(reason); this.reason = reason; }
    }

    Captured capture(String severity, String message, boolean supplied, Object value)
    {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("severity", severity);
        String reason = null;
        if (message == null)
        {
            envelope.put("messageState", "UNAVAILABLE");
            reason = "UNSUPPORTED_VALUE";
        }
        else
        {
            String prefix = prefix(message, MESSAGE_BYTES);
            envelope.put("message", prefix);
            envelope.put("messageState", prefix.length() == message.length() ? "COMPLETE" : "TRUNCATED");
            if (prefix.length() != message.length()) reason = "MESSAGE_LIMIT";
        }
        if (!supplied) envelope.put("dataState", "ABSENT");
        else
        {
            try
            {
                CappedOutput out = new CappedOutput(DATA_BYTES, "DATA_LIMIT");
                try (JsonGenerator generator = factory.createGenerator(out))
                {
                    new Walker().write(generator, value, 0);
                }
                envelope.set("data", json.readTree(out.toByteArray()));
                envelope.put("dataState", "COMPLETE");
            }
            catch (RuntimeException failure)
            {
                String dataReason = rejection(failure);
                envelope.put("dataState", "CAPTURE_FAILURE".equals(dataReason) ? "CAPTURE_FAILED" : "UNAVAILABLE");
                reason = dataReason;
            }
        }
        if (reason != null) envelope.put("reason", reason);
        try { return encoded(envelope, ENVELOPE_BYTES, reason); }
        catch (RuntimeException tooLarge)
        {
            // A message with JSON escape amplification can exhaust the envelope.
            // Its bounded prefix remains explicit; no rejected application value is revisited.
            envelope.remove("data");
            envelope.put("dataState", supplied ? "UNAVAILABLE" : "ABSENT");
            envelope.put("reason", "DATA_LIMIT");
            if (message != null)
            {
                String prefix = prefix(message, 4096);
                envelope.put("message", prefix);
                envelope.put("messageState", prefix.length() == message.length() ? "COMPLETE" : "TRUNCATED");
            }
            return encoded(envelope, ENVELOPE_BYTES, "DATA_LIMIT");
        }
    }

    Captured notice(String reason, String original)
    {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("reason", reason);
        envelope.put("message", "Executor diagnostic capture or delivery was limited: " + reason);
        if (original != null)
        {
            String prefix = prefix(original, 256);
            envelope.put("originalMessage", prefix);
            envelope.put("originalMessageState", prefix.length() == original.length() ? "COMPLETE" : "TRUNCATED");
        }
        else envelope.put("originalMessageState", "UNAVAILABLE");
        return encoded(envelope, NOTICE_BYTES, reason);
    }

    private Captured encoded(ObjectNode envelope, int limit, String reason)
    {
        CappedOutput out = new CappedOutput(limit, "DATA_LIMIT");
        try (JsonGenerator generator = factory.createGenerator(out)) { json.writeValue(generator, envelope); }
        return new Captured(envelope, out.size(), reason);
    }

    private static String rejection(RuntimeException failure)
    {
        // Jackson wraps output failures; only inspect this short, Framework-owned chain.
        Throwable cause = failure;
        for (int i = 0; cause != null && i < 8; i++, cause = cause.getCause())
            if (cause instanceof Rejected rejected) return rejected.reason;
        return "CAPTURE_FAILURE";
    }

    static String prefix(String source, int limit)
    {
        int bytes = 0, end = 0;
        while (end < source.length())
        {
            int point = source.codePointAt(end);
            int count = point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
            if (bytes + count > limit) break;
            bytes += count;
            end += Character.charCount(point);
        }
        return end == source.length() ? source : source.substring(0, end);
    }

    private static void string(String text)
    {
        int bytes = 2;
        for (int index = 0; index < text.length(); index++)
        {
            char c = text.charAt(index);
            if (Character.isHighSurrogate(c) && index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1)))
            { bytes += 4; index++; }
            else bytes += switch (c)
            {
                case '\b', '\t', '\n', '\f', '\r', '"', '\\' -> 2;
                default -> c < 32 ? 6 : c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
            };
            if (bytes > DATA_BYTES) throw new Rejected("DATA_LIMIT");
        }
    }

    private static void number(BigInteger value)
    {
        // compareTo checks magnitude length before comparing its bounded digits.
        // bitLength can scan an arbitrarily large negative power of two.
        if (value.compareTo(MIN_NUMBER) < 0 || value.compareTo(MAX_NUMBER) > 0)
            throw new Rejected("NUMBER_LIMIT");
    }

    private static final class Walker
    {
        int work;
        final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
        void visit() { if (++work > WORK) throw new Rejected("TRAVERSAL_LIMIT"); }
        void write(JsonGenerator g, Object value, int depth)
        {
            visit();
            if (value == null) { g.writeNull(); return; }
            Class<?> type = value.getClass();
            if (type == String.class) { string((String) value); g.writeString((String) value); return; }
            if (type == Boolean.class) { g.writeBoolean((Boolean) value); return; }
            if (type == Byte.class || type == Short.class || type == Integer.class) { g.writeNumber(((Number) value).intValue()); return; }
            if (type == Long.class) { g.writeNumber((Long) value); return; }
            if (type == Float.class)
            {
                float number = (Float) value;
                if (!Float.isFinite(number)) throw new Rejected("UNSUPPORTED_VALUE");
                g.writeNumber(number); return;
            }
            if (type == Double.class)
            {
                double number = (Double) value;
                if (!Double.isFinite(number)) throw new Rejected("UNSUPPORTED_VALUE");
                g.writeNumber(number); return;
            }
            if (type == BigInteger.class)
            {
                BigInteger number = (BigInteger) value;
                number(number);
                g.writeNumber(number); return;
            }
            if (type == BigDecimal.class)
            {
                BigDecimal number = (BigDecimal) value;
                BigInteger unscaled = number.unscaledValue();
                if (unscaled.getClass() != BigInteger.class) throw new Rejected("UNSUPPORTED_VALUE");
                if (Math.abs((long) number.scale()) > 4096) throw new Rejected("NUMBER_LIMIT");
                number(unscaled);
                g.writeNumber(number); return;
            }
            boolean map = MAPS.contains(type), list = LISTS.contains(type);
            boolean array = type.isArray() && type != char[].class;
            if (!map && !list && !array) throw new Rejected("UNSUPPORTED_VALUE");
            if (depth >= DEPTH) throw new Rejected("DEPTH_LIMIT");
            if (active.put(value, Boolean.TRUE) != null) throw new Rejected("CYCLE");
            try
            {
                if (map)
                {
                    g.writeStartObject();
                    for (var entry : ((Map<?, ?>) value).entrySet())
                    {
                        visit();
                        if (entry.getKey() == null || entry.getKey().getClass() != String.class) throw new Rejected("UNSUPPORTED_VALUE");
                        String key = (String) entry.getKey(); string(key); g.writeName(key);
                        write(g, entry.getValue(), depth + 1);
                    }
                    g.writeEndObject();
                }
                else
                {
                    g.writeStartArray();
                    if (list) for (Object child : (List<?>) value) write(g, child, depth + 1);
                    else for (int index = 0, length = Array.getLength(value); index < length; index++) write(g, Array.get(value, index), depth + 1);
                    g.writeEndArray();
                }
            }
            finally { active.remove(value); }
        }
    }

    private static final class CappedOutput extends OutputStream
    {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private final String reason;
        CappedOutput(int limit, String reason) { this.limit = limit; this.reason = reason; }
        @Override public void write(int b) { check(1); bytes.write(b); }
        @Override public void write(byte[] b, int off, int len) { check(len); bytes.write(b, off, len); }
        private void check(int added) { if (added > limit - bytes.size()) throw new Rejected(reason); }
        byte[] toByteArray() { return bytes.toByteArray(); }
        int size() { return bytes.size(); }
    }
}
