package ai.loomspan.internal.vfs;

import tools.jackson.databind.JsonNode;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.UnaryOperator;

/** Schema-guided reference selection; resource authorization remains in the supplied resolver. */
final class InputReferenceTraversal
{
    private InputReferenceTraversal() {}

    static Object resolve(Object value, JsonNode schema, boolean generic, UnaryOperator<Object> resolver)
    {
        if (value instanceof Map<?, ?> map)
        {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet())
            {
                if (!(entry.getKey() instanceof String name)) throw new IllegalArgumentException("Expected string object keys");
                JsonNode child = null;
                if (schema != null)
                {
                    child = schema.path("properties").get(name);
                    if (child == null && schema.path("additionalProperties").isObject()) child = schema.get("additionalProperties");
                }
                result.put(name, resolve(entry.getValue(), child, generic, resolver));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list)
        {
            var result = new ArrayList<Object>(list.size());
            JsonNode items = schema == null ? null : schema.get("items");
            list.forEach(item -> result.add(resolve(item, items, generic, resolver)));
            return Collections.unmodifiableList(result);
        }
        if (generic && value != null && value.getClass().isArray())
        {
            var result = new ArrayList<Object>(Array.getLength(value));
            for (int i = 0; i < Array.getLength(value); i++) result.add(resolve(Array.get(value, i), null, true, resolver));
            return Collections.unmodifiableList(result);
        }
        return generic || schema != null && (schema.path("x-loomspan-runtime-ref-capable").asBoolean(false)
                || schema.path("x-loomspan-attachment").isObject()) ? resolver.apply(value) : value;
    }
}
