package ai.loomspan.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Copies map/list containers only. Nulls and all other leaves retain their values/identity. */
public final class ImmutableContainers
{
    private ImmutableContainers() {}

    public static Map<String, Object> copyMap(Map<String, Object> source)
    {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copy(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copy(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(copy(key), copy(item)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list)
        {
            ArrayList<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(copy(item)));
            return Collections.unmodifiableList(result);
        }
        return value;
    }
}
