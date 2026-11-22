package ai.loomspan.internal.autoconfigure;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.serialization.AuthoredYamlVariables;
import ai.loomspan.internal.skill.ExecutionConfigurationParser;
import ai.loomspan.internal.skill.ExecutorConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One startup's effective authored settings and normal host lookups. */
public final class StartupVariableResolution {
    private final Environment environment;
    private final Map<String, String> values = new LinkedHashMap<>();

    StartupVariableResolution(Environment environment) {
        this.environment = environment;
    }

    private String lookup(String name) {
        if (!values.containsKey(name)) values.put(name, environment.getProperty(name));
        return values.get(name);
    }

    public Map<?, ?> skill(String yaml, String source) {
        return (Map<?, ?>) AuthoredYamlVariables.skill(yaml, source).substitute(this::lookup);
    }

    void select(LoomspanProperties properties, Iterable<ConfigurationPropertySource> sources) {
        Value connections = null;
        Value executor = null;
        for (var source : sources) {
            Value selected = read(source, ExecutorYamlPropertySourceLoader.CONNECTIONS);
            connections = connections == null ? selected : connections.inherit(selected);
            if (executor == null) executor = read(source, ExecutorYamlPropertySourceLoader.EXECUTORS);
        }
        if (connections != null) connections.discover();
        if (executor != null) executor.discover();
        if (connections != null)
            properties.setConnections(ExecutionConfigurationParser.startupConnections(connections.resolve(this::lookup)));
        if (executor != null)
            properties.setExecutorConfig(ExecutorConfiguration.from(executor.resolve(this::lookup)));
    }

    private static Value read(ConfigurationPropertySource source, String root) {
        Value value = null;
        if (source.getUnderlyingSource() instanceof MapPropertySource properties) {
            // A single YAML document can mix nested and dotted keys. Assemble all
            // retained slices in the same order as Boot's flattened document.
            for (String name : properties.getPropertyNames()) {
                if (name == null || !ExecutorYamlPropertySourceLoader.selected(name, root)
                        || !(properties.getProperty(name) instanceof ExecutorYamlPropertySourceLoader.Raw raw)) continue;
                Value selected;
                if (ConfigurationPropertyName.adapt(name.toLowerCase(java.util.Locale.ROOT), '.')
                        .equals(ConfigurationPropertyName.of(root))) selected = Value.authored(raw.value());
                else {
                    var bound = new Binder(new MapConfigurationPropertySource(Map.of(name, raw)))
                            .bind(root, Bindable.mapOf(String.class, Object.class));
                    selected = Value.literal(bound.get(), new AuthoredYamlVariables.Location(raw.value().location().source(), root));
                }
                if (root.equals(ExecutorYamlPropertySourceLoader.CONNECTIONS)) selected.connectionFields();
                value = selected.inherit(value);
            }
        }
        if (value == null) {
            var result = new Binder(source).bind(root, Bindable.mapOf(String.class, Object.class));
            if (!result.isBound()) return null;
            Object underlying = source.getUnderlyingSource();
            String label = underlying instanceof org.springframework.core.env.PropertySource<?> properties
                    ? properties.getName() : "<Spring properties>";
            value = Value.literal(result.get(), new AuthoredYamlVariables.Location(label, root));
        }
        if (root.equals(ExecutorYamlPropertySourceLoader.CONNECTIONS)) value.connectionFields();
        return value;
    }

    private record Entry(Object key, AuthoredYamlVariables.Slice authoredKey, Value value) {
        Object resolvedKey(java.util.function.Function<String, String> lookup) {
            return authoredKey == null ? key : authoredKey.resolve(lookup);
        }
        AuthoredYamlVariables.Location location() { return authoredKey == null ? value.location : authoredKey.location(); }
    }

    /** Mapping overlays retain each winning node's own authored source and path. */
    private static final class Value {
        private final AuthoredYamlVariables.Slice slice;
        private final Object literal;
        private final AuthoredYamlVariables.Location location;
        private final List<Entry> entries;
        private boolean overlaid;
        private boolean relaxedKeys;

        private Value(AuthoredYamlVariables.Slice slice, Object literal, AuthoredYamlVariables.Location location, List<Entry> entries) {
            this.slice = slice;
            this.literal = literal;
            this.location = location;
            this.entries = entries;
        }
        static Value authored(AuthoredYamlVariables.Slice slice) {
            var children = slice.entries();
            if (children == null) return new Value(slice, null, slice.location(), null);
            var entries = new ArrayList<Entry>();
            for (var child : children) entries.add(new Entry(child.getKey().literal(), child.getKey(), authored(child.getValue())));
            return new Value(slice, null, slice.location(), entries);
        }
        static Value literal(Object value, AuthoredYamlVariables.Location location) {
            if (value instanceof ExecutorYamlPropertySourceLoader.Raw raw) return authored(raw.value());
            if (!(value instanceof Map<?, ?> map)) return new Value(null, value, location, null);
            var entries = new ArrayList<Entry>();
            map.forEach((key, child) -> entries.add(new Entry(key, null, literal(child,
                    new AuthoredYamlVariables.Location(location.source(), location.path() + "." + key)))));
            return new Value(null, null, location, entries);
        }
        Value inherit(Value lower) {
            if (lower == null || entries == null || lower.entries == null) return this;
            var combined = new ArrayList<>(entries);
            for (Entry entry : lower.entries) {
                int index = -1;
                for (int i = 0; i < combined.size(); i++) {
                    if (sameKey(combined.get(i).key, entry.key)) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) combined.add(entry);
                else {
                    Entry higher = combined.get(index);
                    combined.set(index, new Entry(higher.key, higher.authoredKey, higher.value.inherit(entry.value)));
                }
            }
            var value = new Value(slice, literal, location, combined);
            value.overlaid = true;
            value.relaxedKeys = relaxedKeys;
            return value;
        }
        private void connectionFields() {
            if (entries == null) return;
            for (Entry connection : entries) {
                Value fields = connection.value;
                fields.relaxedKeys = true;
                if (fields.entries == null) continue;
                for (Entry field : fields.entries) {
                    if (field.key instanceof String key && (sameField(key, "openai") || sameField(key, "gemini") || sameField(key, "provider-retry")))
                        field.value.relaxedKeys = true;
                }
            }
        }
        private boolean sameKey(Object first, Object second) {
            if (first.equals(second)) return true;
            return relaxedKeys && first instanceof String left && second instanceof String right && sameField(left, right);
        }
        private static boolean sameField(String first, String second) {
            return ConfigurationPropertyName.adapt(first.toLowerCase(java.util.Locale.ROOT), '.')
                    .equals(ConfigurationPropertyName.adapt(second.toLowerCase(java.util.Locale.ROOT), '.'));
        }
        void discover() {
            if (slice != null && !overlaid) {
                slice.discover();
                return;
            }
            if (entries == null) return;
            for (Entry entry : entries) {
                if (entry.authoredKey != null) entry.authoredKey.discover();
                entry.value.discover();
            }
        }
        Object resolve(java.util.function.Function<String, String> lookup) {
            if (slice != null && !overlaid) return slice.resolve(lookup);
            if (entries == null) return slice == null ? literal : slice.resolve(lookup);
            var result = new AuthoredYamlVariables.AuthoredMap();
            var origins = new LinkedHashMap<Object, AuthoredYamlVariables.Location>();
            for (Entry entry : entries) {
                Object key = entry.resolvedKey(lookup);
                if (origins.containsKey(key)) throw AuthoredYamlVariables.collision(origins.get(key), entry.location());
                origins.put(key, entry.location());
                result.put(key, entry.value.resolve(lookup));
                if (entry.authoredKey != null && entry.authoredKey.variable()) result.variableKey(key, entry.location());
            }
            return result;
        }
    }
}
