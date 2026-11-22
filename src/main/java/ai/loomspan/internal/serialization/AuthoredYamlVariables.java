package ai.loomspan.internal.serialization;

import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Framework-only whole-node variables, retained before YAML mapping keys are constructed. */
public final class AuthoredYamlVariables {
    private AuthoredYamlVariables() {}

    public record Location(String source, String path) {}
    public record Occurrence(String name, String source, String path) {}

    /** Failures contain authored locations and fixed reasons, independent of lookup contents. */
    public static final class InvalidVariablesException extends IllegalArgumentException {
        private final String reason;
        private final List<Location> locations;

        private InvalidVariablesException(String reason, List<Location> locations) {
            super(reason + " at " + locations);
            this.reason = reason;
            this.locations = List.copyOf(locations);
        }

        public String reason() { return reason; }
        public List<Location> locations() { return locations; }
    }

    public static Document execution(String yaml, String source) {
        return new Document(new Compose(YamlValues.settings(false, true)).composeString(yaml), source, true);
    }

    public static Document execution(InputStream yaml, String source) {
        return new Document(new Compose(YamlValues.settings(false, true)).composeInputStream(yaml), source, true);
    }

    public static Document skill(String yaml, String source) {
        return new Document(new Compose(YamlValues.settings(false, true)).composeString(yaml), source, false);
    }

    public static Document skill(InputStream yaml, String source) {
        return new Document(new Compose(YamlValues.settings(false, true)).composeInputStream(yaml), source, false);
    }

    /** Opaque reusable authored document. Every substitution builds independent output containers. */
    public static final class Document {
        private final Node root;
        private final String source;
        private final List<Boundary> boundaries = new ArrayList<>();
        private final Set<String> selectedPaths = new LinkedHashSet<>();

        private Document(Optional<Node> root, String source, boolean execution) {
            this.root = root.orElse(null);
            this.source = Objects.requireNonNull(source, "source");
            check(this.root, new IdentityHashMap<>());
            if (execution) selectExecution();
            else if (field(this.root, "executor") != null && field(this.root, "config") instanceof MappingNode config)
                select(config, "config");
        }

        private void selectExecution() {
            Node loomspan = field(root, "loomspan");
            if (!(loomspan instanceof MappingNode sections)) return;
            for (var sectionEntry : sections.getValue()) {
                String section = string(sectionEntry.getKeyNode());
                if (("connections".equals(section) || "executor-config".equals(section))
                        && sectionEntry.getValueNode() instanceof MappingNode map) {
                    for (int i = 0; i < map.getValue().size(); i++) {
                        var entry = map.getValue().get(i);
                        select(entry.getValueNode(), valuePath("loomspan." + section, entry.getKeyNode(), i));
                    }
                }
            }
        }

        private void select(Node node, String path) {
            boundaries.add(new Boundary(node, path));
            selectedPaths.add(path);
        }

        public List<Occurrence> discover() {
            var result = new ArrayList<Occurrence>();
            for (Boundary boundary : boundaries) discover(boundary.node(), boundary.path(), result);
            return List.copyOf(result);
        }

        private void discover(Node node, String path, List<Occurrence> result) {
            Node name = markerName(node);
            if (name != null) {
                result.add(new Occurrence(validName(name, path), source, path));
                return;
            }
            if (node instanceof MappingNode map) {
                for (int i = 0; i < map.getValue().size(); i++) {
                    var entry = map.getValue().get(i);
                    discover(entry.getKeyNode(), path + "{key#" + i + "}", result);
                    discover(entry.getValueNode(), valuePath(path, entry.getKeyNode(), i), result);
                }
            }
            else if (node instanceof SequenceNode sequence) {
                for (int i = 0; i < sequence.getValue().size(); i++)
                    discover(sequence.getValue().get(i), path + "[" + i + "]", result);
            }
        }

        public Object substitute(Function<String, String> lookup) {
            Objects.requireNonNull(lookup, "lookup");
            discover(); // Validate every authored marker before consulting a value source.
            return materialize(root, "", false, lookup, new StandardConstructor(YamlValues.settings(false, true)));
        }

        private Object materialize(Node node, String path, boolean selected, Function<String, String> lookup,
                                   StandardConstructor constructor) {
            if (node == null) return null;
            selected = selected || selectedPaths.contains(path);
            Node name = selected ? markerName(node) : null;
            if (name != null) {
                String value;
                try { value = lookup.apply(validName(name, path)); }
                catch (RuntimeException failure) { throw invalid("lookup-failed", path); }
                if (value == null) throw invalid("missing-value", path);
                if (blank(value)) throw invalid("blank-value", path);
                return value;
            }
            if (node instanceof MappingNode map && (Tag.MAP.equals(node.getTag()) || Tag.SET.equals(node.getTag()))) {
                var values = new LinkedHashMap<Object, Object>();
                var origins = new LinkedHashMap<Object, String>();
                for (int i = 0; i < map.getValue().size(); i++) {
                    var entry = map.getValue().get(i);
                    String keyPath = path + "{key#" + i + "}";
                    String valuePath = valuePath(path, entry.getKeyNode(), i);
                    Object key = materialize(entry.getKeyNode(), keyPath, selected, lookup, constructor);
                    String origin = string(entry.getKeyNode()) != null ? valuePath : keyPath;
                    if (origins.containsKey(key))
                        throw new InvalidVariablesException("key-collision", List.of(
                                new Location(source, origins.get(key)), new Location(source, origin)));
                    origins.put(key, origin);
                    values.put(key, materialize(entry.getValueNode(), valuePath, selected, lookup, constructor));
                }
                if (Tag.SET.equals(node.getTag())) return new LinkedHashSet<>(values.keySet());
                return values;
            }
            if (node instanceof SequenceNode sequence && Tag.SEQ.equals(node.getTag())) {
                var values = new ArrayList<Object>();
                for (int i = 0; i < sequence.getValue().size(); i++)
                    values.add(materialize(sequence.getValue().get(i), path + "[" + i + "]", selected, lookup, constructor));
                return values;
            }
            return constructor.constructSingleDocument(Optional.of(node));
        }

        private String validName(Node node, String path) {
            String value = string(node);
            if (value == null) throw invalid("name-must-be-string", path);
            if (blank(value)) throw invalid("blank-name", path);
            if (whitespace(value.codePointAt(0)) || whitespace(value.codePointBefore(value.length())))
                throw invalid("padded-name", path);
            return value;
        }

        private InvalidVariablesException invalid(String reason, String path) {
            return new InvalidVariablesException(reason, List.of(new Location(source, path)));
        }
    }

    private record Boundary(Node node, String path) {}

    private static Node markerName(Node node) {
        if (node instanceof MappingNode map && map.getValue().size() == 1) {
            var entry = map.getValue().getFirst();
            if ("$var".equals(string(entry.getKeyNode()))) return entry.getValueNode();
        }
        return null;
    }

    private static Node field(Node node, String name) {
        if (node instanceof MappingNode map)
            for (var entry : map.getValue())
                if (name.equals(string(entry.getKeyNode()))) return entry.getValueNode();
        return null;
    }

    private static String string(Node node) {
        return node instanceof ScalarNode scalar && Tag.STR.equals(node.getTag()) ? scalar.getValue() : null;
    }

    private static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean blank(String value) { return value.codePoints().allMatch(AuthoredYamlVariables::whitespace); }

    private static String valuePath(String path, Node key, int index) {
        String name = string(key);
        if (name == null) return path + "{value#" + index + "}";
        if (name.matches("[A-Za-z_][A-Za-z0-9_-]*")) return path.isEmpty() ? name : path + "." + name;
        return path + "[" + quote(name) + "]";
    }

    private static String quote(String value) {
        var result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) result.append(String.format("\\u%04x", (int)c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }

    private static long check(Node node, IdentityHashMap<Node, Long> visited) {
        if (node == null || node instanceof ScalarNode) return 1;
        Long size = visited.get(node);
        if (size != null) {
            if (size < 0) throw new YamlEngineException("YAML recursive containers are not supported");
            return size;
        }
        visited.put(node, -1L);
        long count = 1;
        if (node instanceof MappingNode map) {
            for (var entry : map.getValue()) {
                count = YamlValues.bounded(count + check(entry.getKeyNode(), visited));
                count = YamlValues.bounded(count + check(entry.getValueNode(), visited));
            }
        }
        else if (node instanceof SequenceNode sequence)
            for (Node item : sequence.getValue()) count = YamlValues.bounded(count + check(item, visited));
        visited.put(node, count);
        return count;
    }
}
