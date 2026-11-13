package ai.loomspan.internal.serialization;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lossless JSON-bound skill-document ingress, with a separate executor configuration boundary. */
public final class SkillDocumentValues {
    private SkillDocumentValues() {}

    /** Retain the YAML loader's BOM-aware byte decoding for file-backed skills. */
    public static Decoded decode(java.io.InputStream yaml, String source, ObjectMapper mapper)
            throws java.io.IOException {
        var reader = new org.snakeyaml.engine.v2.api.YamlUnicodeReader(yaml);
        var text = new java.io.StringWriter();
        reader.transferTo(text);
        return decode(text.toString(), source, mapper);
    }

    public record Decoded(JsonNode root, Object executorConfig, boolean configPresent) {}

    /** Structured ingress failure for catalog authoring diagnostics. */
    public static final class InvalidDocumentException extends IllegalArgumentException {
        private final String path;
        private final String detail;

        private InvalidDocumentException(String source, String path, String detail) {
            super(source + " at " + (path.isEmpty() ? "/" : path) + ": " + detail);
            this.path = path;
            this.detail = detail;
        }

        public String path() { return path; }
        public String detail() { return detail; }
    }

    public static Decoded decode(String yaml, String source, ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Object decoded = YamlValues.readSkillDocument(yaml);
        boolean configPresent = decoded instanceof Map<?, ?> map && map.containsKey("config");
        Object executorConfig = null;
        if (configPresent) {
            // Config remains ordinary YAML domain data, including its original Java numeric kinds.
            executorConfig = ((Map<?, ?>) YamlValues.read(yaml)).get("config");
            var fields = new LinkedHashMap<>((Map<?, ?>) decoded);
            fields.remove("config");
            decoded = fields;
        }
        checkJson(decoded, "", source);
        JsonNode root = mapper.valueToTree(decoded);
        if (configPresent && root instanceof ObjectNode object) {
            // Existing declaration-form checks need presence/type without converting config values.
            object.set("config", executorConfig instanceof Map<?, ?> ? mapper.createObjectNode() : mapper.nullNode());
        }
        return new Decoded(root, executorConfig, configPresent);
    }

    private static void checkJson(Object value, String path, String source) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal) return;
        if (value instanceof Double number && Double.isFinite(number)) return;
        if (value instanceof Float number && Float.isFinite(number)) return;
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key))
                    throw invalid(source, path, "map keys must be textual; found " + entry.getKey());
                checkJson(entry.getValue(), path + "/" + escape(key), source);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) checkJson(list.get(i), path + "/" + i, source);
            return;
        }
        throw invalid(source, path, "value is not JSON-compatible: " + value);
    }

    private static InvalidDocumentException invalid(String source, String path, String reason) {
        return new InvalidDocumentException(source, path, reason);
    }

    private static String escape(String key) { return key.replace("~", "~0").replace("/", "~1"); }
}
