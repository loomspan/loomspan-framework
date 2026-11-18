package ai.loomspan.internal.skill;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A prepared JSLT expression and its JSON-only executor handoff. */
final class ExecutorInputTransform {
    private static final ObjectMapper JSON = LoomspanJacksonCodecs.defaults().schemaTree();
    private final Expression expression;

    private ExecutorInputTransform(Expression expression) {
        this.expression = expression;
    }

    static ExecutorInputTransform compile(String sourceText, String sourceName) {
        return new ExecutorInputTransform(new Parser(new StringReader(sourceText))
                .withSource(sourceName)
                .withObjectFilter("true")
                .compile());
    }

    Map<String, Object> apply(Map<String, Object> input) {
        requireJsonInput(input);
        JsonNode output = expression.apply(JSON.valueToTree(input));
        if (!(output instanceof ObjectNode object)) {
            throw new IllegalArgumentException("JSLT result must be a JSON object; found " + output.getNodeType());
        }
        return readObject(object, "");
    }

    static void requireJsonInput(Map<String, Object> input) {
        checkJson(input, "");
    }

    private static void checkJson(Object value, String path) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal
                || (value instanceof Float number && Float.isFinite(number))
                || (value instanceof Double number && Double.isFinite(number))) return;
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Input at " + displayPath(path)
                            + " has a non-string object key of type " + typeName(entry.getKey()));
                }
                checkJson(entry.getValue(), fieldPath(path, key));
            }
            return;
        }
        if (value instanceof List<?> list) {
            int index = 0;
            for (Object item : list) checkJson(item, path + "/" + index++);
            return;
        }
        throw new IllegalArgumentException("Input at " + displayPath(path)
                + " must be a JSON value; found " + typeName(value));
    }

    private static Map<String, Object> readObject(JsonNode object, String path) {
        var values = new LinkedHashMap<String, Object>();
        for (var entry : object.properties()) {
            values.put(entry.getKey(), fromJson(entry.getValue(), fieldPath(path, entry.getKey())));
        }
        return values;
    }

    private static Object fromJson(JsonNode node, String path) {
        if (node.isNull()) return null;
        if (node.isString()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNumber()) {
            Number number = node.numberValue();
            if ((number instanceof Double decimal && !Double.isFinite(decimal))
                    || (number instanceof Float floating && !Float.isFinite(floating))) {
                throw new IllegalArgumentException("JSLT result at " + displayPath(path) + " must be a finite JSON number");
            }
            return number;
        }
        if (node.isObject()) return readObject(node, path);
        if (node.isArray()) {
            var values = new ArrayList<>(node.size());
            for (JsonNode item : node) values.add(fromJson(item, path + "/" + values.size()));
            return values;
        }
        throw new IllegalArgumentException("JSLT result at " + displayPath(path)
                + " must be a JSON value; found " + node.getNodeType());
    }

    private static String fieldPath(String parent, String key) {
        return parent + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    private static String displayPath(String path) {
        return path.isEmpty() ? "/" : path;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getTypeName();
    }
}
