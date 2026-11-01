package ai.loomspan.internal.serialization;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/** Raw YAML values, before configuration flattening or typed manifest binding. */
public final class YamlValues {
    private YamlValues() {}
    // Bound logical expansion before downstream Jackson/Boot tree traversal.
    private static final long MAX_EXPANDED_VALUES = 1_000_000;

    private static Load loader() {
        return new Load(LoadSettings.builder().setSchema(new CoreSchema())
                // Keep explicit timestamps textual, as the existing Jackson value ingress did.
                // Ordinary Spring values are still decoded by Boot's delegated loader.
                .setTagConstructors(java.util.Map.of(new org.snakeyaml.engine.v2.nodes.Tag("tag:yaml.org,2002:timestamp"),
                        node -> ((org.snakeyaml.engine.v2.nodes.ScalarNode)node).getValue()))
                .setAllowDuplicateKeys(false).setMaxAliasesForCollections(Integer.MAX_VALUE)
                .setCodePointLimit(Integer.MAX_VALUE).build());
    }

    public static Object read(String yaml) { return acyclic(loader().loadFromString(yaml)); }
    public static Object read(java.io.InputStream input) { return acyclic(loader().loadFromInputStream(input)); }
    public static Iterable<Object> documents(java.io.InputStream input) {
        var result = new java.util.ArrayList<Object>();
        loader().loadAllFromInputStream(input).forEach(value -> result.add(acyclic(value)));
        return result;
    }

    private static Object acyclic(Object value) {
        check(value, new java.util.IdentityHashMap<>());
        return value;
    }

    private static long check(Object value, java.util.IdentityHashMap<Object, Long> ancestors) {
        if (!(value instanceof java.util.Map<?, ?>) && !(value instanceof java.util.List<?>)) return 1;
        Long visited = ancestors.get(value);
        if (visited != null) {
            if (visited < 0) throw new org.snakeyaml.engine.v2.exceptions.YamlEngineException("YAML recursive containers are not supported");
            return visited;
        }
        ancestors.put(value, -1L);
        long size = 1;
        if (value instanceof java.util.Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                size = bounded(size + check(entry.getKey(), ancestors));
                size = bounded(size + check(entry.getValue(), ancestors));
            }
        }
        else for (Object item : (java.util.List<?>)value) size = bounded(size + check(item, ancestors));
        // A completed shared node needs no second walk; YAML aliases form a DAG.
        ancestors.put(value, size);
        return size;
    }

    private static long bounded(long size) {
        if (size > MAX_EXPANDED_VALUES)
            throw new org.snakeyaml.engine.v2.exceptions.YamlEngineException("YAML expanded value count exceeds limit " + MAX_EXPANDED_VALUES);
        return size;
    }
}
