package ai.loomspan.api;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SkillExecutionEvent(
        Instant timestamp,
        String level,
        String type,
        Map<String, Object> details,
        String frameId,
        String route)
{
    public SkillExecutionEvent
    {
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        level = requireNonBlank(level, "level");
        type = requireNonBlank(type, "type");
        details = immutableMap(details == null ? Map.of() : details);
        frameId = normalizeNullable(frameId);
        route = normalizeNullable(route);
    }

    private static Map<String, Object> immutableMap(Map<String, ?> source)
    {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "detail keys must not be null"),
                immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value)
    {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character)
        {
            return value;
        }
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, nested) -> converted.put(
                    Objects.requireNonNull((String) key, "nested detail keys must be non-null strings"),
                    immutableValue(nested)));
            return Collections.unmodifiableMap(converted);
        }
        if (value instanceof Iterable<?> iterable)
        {
            ArrayList<Object> converted = new ArrayList<>();
            iterable.forEach(item -> converted.add(immutableValue(item)));
            return Collections.unmodifiableList(converted);
        }
        if (value.getClass().isArray())
        {
            ArrayList<Object> converted = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++)
            {
                converted.add(immutableValue(Array.get(value, index)));
            }
            return Collections.unmodifiableList(converted);
        }
        throw new IllegalArgumentException("Diagnostic details support only standard scalar, map, list, and array values");
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeNullable(String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
