package ai.loomspan.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable application handoff for one REST skill invocation. */
public record RestSkillInvocation(String skillName, Map<String, Object> input, String generationId)
{
    public RestSkillInvocation
    {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) throw new IllegalArgumentException("generationId must not be blank");
        input = freezeInputMap(input);
    }

    private static Map<String, Object> freezeInputMap(Map<String, Object> source)
    {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Object, Object> freezeNestedMap(Map<?, ?> source)
    {
        LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(freeze(key), freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value)
    {
        if (value instanceof Map<?, ?> map) return freezeNestedMap(map);
        if (value instanceof List<?> list)
        {
            ArrayList<Object> copy = new ArrayList<>(list.size());
            list.forEach(valueItem -> copy.add(freeze(valueItem)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
