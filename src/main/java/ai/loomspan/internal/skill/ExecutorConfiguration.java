package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;
import ai.loomspan.internal.util.ImmutableContainers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable namespace-owned data; deliberately has no path resolver or domain semantics. */
public final class ExecutorConfiguration {
    private final Map<String, Map<String, Object>> namespaces;
    private ExecutorConfiguration(Map<String, Map<String, Object>> namespaces) {
        this.namespaces = Collections.unmodifiableMap(namespaces);
    }
    public static ExecutorConfiguration empty() { return new ExecutorConfiguration(new LinkedHashMap<>()); }
    public static ExecutorConfiguration from(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw invalid("loomspan.executor-config", "must be a non-null map");
        var result = new LinkedHashMap<String, Map<String, Object>>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw invalid("loomspan.executor-config", "namespace must be textual");
            String namespace;
            try { namespace = SkillIdentity.normalizeNamespace(key); }
            catch (IllegalArgumentException ex) { throw invalid("loomspan.executor-config." + key, ex.getMessage()); }
            if (!(entry.getValue() instanceof Map<?, ?> data)) throw invalid("loomspan.executor-config." + key, "must be a non-null map");
            var typed = new LinkedHashMap<String, Object>();
            for (var item : data.entrySet()) {
                if (!(item.getKey() instanceof String dataKey)) throw invalid("loomspan.executor-config." + key, "data keys must be textual");
                typed.put(dataKey, item.getValue());
            }
            if (result.putIfAbsent(namespace, ImmutableContainers.copyMap(typed)) != null)
                throw invalid("loomspan.executor-config." + key, "duplicate normalized namespace");
        }
        return new ExecutorConfiguration(result);
    }
    public ExecutorConfiguration requireTarget(String target) {
        String normalized = SkillIdentity.normalizeNamespace(target);
        for (String namespace : namespaces.keySet())
            if (!namespace.equals(normalized)) throw invalid("loomspan.executor-config." + namespace, "is outside publication target namespace " + normalized);
        return this;
    }
    public Map<String, Map<String, Object>> namespaces() { return namespaces; }
    private static IllegalArgumentException invalid(String path, String detail) { return new IllegalArgumentException(path + " " + detail); }
}
