package ai.loomspan.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/** Copies map/list containers only. Nulls and all other leaves retain their values/identity. */
public final class ImmutableContainers
{
    private ImmutableContainers() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> copyMap(Map<String, Object> source)
    {
        return (Map<String, Object>) copy(source, new IdentityHashMap<>());
    }

    private static Object copy(Object value, IdentityHashMap<Object, Object> copies)
    {
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) return value;
        if (copies.containsKey(value))
        {
            Object existing = copies.get(value);
            if (existing == null) throw new IllegalArgumentException("Recursive containers are not supported");
            return existing;
        }
        copies.put(value, null);
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(copy(key, copies), copy(item, copies)));
            Object frozen = Collections.unmodifiableMap(result);
            copies.put(value, frozen);
            return frozen;
        }
        List<?> list = (List<?>) value;
        ArrayList<Object> result = new ArrayList<>(list.size());
        list.forEach(item -> result.add(copy(item, copies)));
        Object frozen = Collections.unmodifiableList(result);
        copies.put(value, frozen);
        return frozen;
    }
}
