package ai.loomspan.internal.runtime.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detached snapshots of decoded input trees; scalar values are never interpreted. */
public final class DeepInputValues
{
    private DeepInputValues() { }

    public static Object immutableCopy(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                if (!(key instanceof String name))
                    throw new IllegalArgumentException("Input object keys must be strings.");
                copy.put(name, immutableCopy(child));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list)
        {
            List<Object> copy = new ArrayList<>();
            list.forEach(child -> copy.add(immutableCopy(child)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof byte[] bytes) return bytes.clone();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> immutableMap(Map<String, Object> value)
    {
        return (Map<String, Object>) immutableCopy(value);
    }

    public static Object mutableCopy(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put((String) key, mutableCopy(child)));
            return copy;
        }
        if (value instanceof List<?> list)
        {
            List<Object> copy = new ArrayList<>();
            list.forEach(child -> copy.add(mutableCopy(child)));
            return copy;
        }
        if (value instanceof byte[] bytes) return bytes.clone();
        return value;
    }
}
