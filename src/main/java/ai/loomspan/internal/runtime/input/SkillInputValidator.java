package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.springframework.core.io.Resource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Java-value admission followed by the shared standard-schema validator. No input coercion. */
public class SkillInputValidator
{
    private final StandardSchemaCompiler compiler = new StandardSchemaCompiler();

    public SkillInputValidationResult validate(Map<String, Object> input, SkillInputContract contract)
    {
        Objects.requireNonNull(contract, "contract must not be null");
        Map<String, Object> original = input == null ? Map.of() : input;
        var state = new Admission(contract.isGeneric());
        JsonNode schema = contract.schema().tree();
        Object view = state.visit(original, schema, "");
        if (state.issues.isEmpty() && !contract.isGeneric())
        {
            CanonicalSchema structural = contract.schema();
            if (state.adapted)
            {
                weakenRuntimeLeaves(schema);
                structural = compiler.compile(schema, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED);
            }
            state.standardIssues(structural, view, original, "", false);
        }
        return new SkillInputValidationResult(state.issues.isEmpty(), snapshotMap(original), List.copyOf(state.issues));
    }

    /** Assembly retains its call boundary; all ordinary validation now has exact semantics. */
    public SkillInputValidationResult validateExact(Map<String, Object> input, SkillInputContract contract,
            List<List<String>> exactPaths)
    {
        Objects.requireNonNull(exactPaths, "exactPaths must not be null");
        return validate(input, contract);
    }

    private final class Admission
    {
        private final boolean generic;
        private final List<SkillInputValidationIssue> issues = new ArrayList<>();
        private final Map<JsonNode, CanonicalSchema> leafValidators = new HashMap<>();
        private boolean adapted;
        private Admission(boolean generic) { this.generic = generic; }

        private Object visit(Object value, JsonNode schema, String path)
        {
            boolean attachment = schema != null && schema.path("x-loomspan-attachment").isObject();
            boolean marked = schema != null && schema.path("x-loomspan-runtime-ref-capable").isBoolean()
                    && schema.path("x-loomspan-runtime-ref-capable").booleanValue() && !attachment;
            if ((marked && runtimeValue(value)) || (attachment && value instanceof Resource))
            {
                adapted = true;
                return null;
            }
            if (value instanceof Map<?, ?> map)
            {
                var result = new LinkedHashMap<String, Object>();
                for (var entry : map.entrySet())
                {
                    if (!(entry.getKey() instanceof String key))
                    {
                        issues.add(new SkillInputValidationIssue(path, "non_string_key",
                                "Expected string object keys.", entry.getKey()));
                        continue;
                    }
                    result.put(key, visit(entry.getValue(), propertySchema(schema, key), child(path, key)));
                }
                if (marked || attachment) leafCheck(result, value, schema, path);
                return result;
            }
            if (value instanceof List<?> list)
            {
                var result = new ArrayList<Object>(list.size());
                JsonNode itemSchema = schema == null ? null : schema.get("items");
                for (int i = 0; i < list.size(); i++) result.add(visit(list.get(i), itemSchema, child(path, Integer.toString(i))));
                if (marked || attachment) leafCheck(result, value, schema, path);
                return result;
            }
            if (generic)
            {
                if (value != null && value.getClass().isArray() && !(value instanceof byte[]))
                    for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++)
                        visit(java.lang.reflect.Array.get(value, i), null, child(path, Integer.toString(i)));
                return value;
            }
            if (!jsonScalar(value))
            {
                issues.add(new SkillInputValidationIssue(path, "non_json_value", "Expected a JSON-compatible value.", value));
                return null;
            }
            if (marked || attachment)
            {
                leafCheck(value, value, schema, path);
                if (attachment && value instanceof String text && !text.matches("^ref://\\S+$"))
                    issues.add(new SkillInputValidationIssue(path, "invalid_attachment", "Expected an exact ref:// reference or Resource.", value));
            }
            return value;
        }

        private void leafCheck(Object view, Object original, JsonNode schema, String path)
        {
            // A failed compatibility walk must never serialize arbitrary caller objects.
            if (!issues.isEmpty()) return;
            CanonicalSchema validator = leafValidators.computeIfAbsent(schema, leaf -> {
                ObjectNode wrapper = LoomspanJacksonCodecs.defaults().schemaTree().createObjectNode();
                wrapper.put("type", "object");
                wrapper.putObject("properties").set("value", leaf.deepCopy());
                return compiler.compile(wrapper, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED);
            });
            Map<String, Object> wrapped = new LinkedHashMap<>();
            // Even an invalid container may have adapted descendants; never serialize its original handles.
            wrapped.put("value", view);
            standardIssues(validator, wrapped, original, path, true);
        }

        private void standardIssues(CanonicalSchema validator, Object view, Object original, String prefix, boolean wrapped)
        {
            JsonNode instance = LoomspanJacksonCodecs.defaults().schemaTree().valueToTree(view);
            for (var error : validator.validate(instance))
            {
                String location = error.getInstanceLocation().toString();
                if (wrapped) location = location.equals("/value") ? "" : location.replaceFirst("^/value/", "/");
                Object rejected = at(original, location);
                // NetworkNT messages may render the private view; report only assertion and original path.
                issues.add(new SkillInputValidationIssue(prefix + location, error.getKeyword(),
                        "Input violates '" + error.getKeyword() + "' at " + (prefix + location).replaceFirst("^$", "/") + ".", rejected));
            }
        }
    }

    private static boolean jsonScalar(Object value)
    {
        return value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof BigDecimal
                || value instanceof Float f && Float.isFinite(f) || value instanceof Double d && Double.isFinite(d);
    }

    private static boolean runtimeValue(Object value)
    {
        return value instanceof Resource || value instanceof InputStream || value instanceof byte[];
    }

    private static JsonNode propertySchema(JsonNode schema, String name)
    {
        if (schema == null) return null;
        JsonNode declared = schema.path("properties").get(name);
        return declared != null ? declared : schema.path("additionalProperties").isObject() ? schema.get("additionalProperties") : null;
    }

    private static void weakenRuntimeLeaves(JsonNode schema)
    {
        if (!(schema instanceof ObjectNode object)) return;
        if (schema.path("x-loomspan-runtime-ref-capable").asBoolean(false)
                || schema.path("x-loomspan-attachment").isObject())
        {
            object.removeAll();
            return;
        }
        schema.path("properties").forEach(SkillInputValidator::weakenRuntimeLeaves);
        if (schema.has("items")) weakenRuntimeLeaves(schema.get("items"));
        if (schema.path("additionalProperties").isObject()) weakenRuntimeLeaves(schema.get("additionalProperties"));
    }

    private static String child(String path, String token) { return path + "/" + token.replace("~", "~0").replace("/", "~1"); }

    private static Object at(Object original, String pointer)
    {
        Object current = original;
        if (pointer.isEmpty()) return current;
        for (String part : pointer.substring(1).split("/", -1))
        {
            String token = part.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map) current = map.get(token);
            else if (current instanceof List<?> list)
            {
                try { current = list.get(Integer.parseInt(token)); }
                catch (IndexOutOfBoundsException | NumberFormatException ex) { return null; }
            }
            else return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshotMap(Map<String, Object> value) { return (Map<String, Object>) snapshot(value); }
    private static Object snapshot(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            var copy = new LinkedHashMap<Object, Object>();
            map.forEach((key, item) -> copy.put(key, snapshot(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(SkillInputValidator::snapshot).toList());
        return value;
    }
}
