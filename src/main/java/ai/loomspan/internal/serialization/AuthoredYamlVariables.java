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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Framework-only whole-node variables, retained before YAML mapping keys are constructed. */
public final class AuthoredYamlVariables {
    private AuthoredYamlVariables() {}

    public record Location(String source, String path) {}
    public record Occurrence(String name, String source, String path) {}
    public record Unresolved(Location location) {}

    /** Materialized entries retain provenance for variable-authored field names. */
    public static final class AuthoredMap extends LinkedHashMap<Object, Object> {
        private final Map<Object, Location> variableKeys = new LinkedHashMap<>();
        public Location variableKeyLocation(Object key) { return variableKeys.get(key); }
        public void variableKey(Object key, Location location) { variableKeys.put(key, location); }
    }

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

    /** Startup documents retain their authored nodes until Boot selects active sources. */
    public static List<Document> startupDocuments(InputStream yaml, String source) {
        var result = new ArrayList<Document>();
        int index = 0;
        for (Node node : new Compose(YamlValues.settings(false, true)).composeAllFromInputStream(yaml))
            result.add(new Document(Optional.of(node), source + " (document #" + index++ + ")", true));
        return List.copyOf(result);
    }

    /** A selected node keeps its original path, even when fields inherit from several sources. */
    public static final class Slice {
        private final Document document;
        private final Node node;
        private final String path;
        private Slice(Document document, Node node, String path) { this.document = document; this.node = node; this.path = path; }
        public Location location() { return new Location(document.source, path); }
        public boolean selected() {
            return document.selectedPaths.stream().anyMatch(boundary -> path.equals(boundary)
                    || path.startsWith(boundary + ".") || path.startsWith(boundary + "[") || path.startsWith(boundary + "{"));
        }
        public boolean variable() { return selected() && markerName(node) != null; }
        public Object literal() {
            return document.materialize(node, path, false, null, true,
                    new StandardConstructor(YamlValues.settings(false, true)), new IdentityHashMap<>());
        }
        public Object resolve(Function<String, String> lookup) {
            discover();
            return document.materialize(node, path, selected(), lookup, false,
                    new StandardConstructor(YamlValues.settings(false, true)), new IdentityHashMap<>());
        }
        public List<Occurrence> discover() {
            var result = new ArrayList<Occurrence>();
            if (selected()) document.discover(node, path, result);
            else for (Boundary boundary : document.boundaries)
                if (boundary.path().startsWith(path + ".") || boundary.path().startsWith(path + "["))
                    document.discover(boundary.node(), boundary.path(), result);
            return List.copyOf(result);
        }
        public List<Map.Entry<Slice, Slice>> entries() {
            if (!(node instanceof MappingNode map) || markerName(node) != null) return null;
            var result = new ArrayList<Map.Entry<Slice, Slice>>();
            for (int i = 0; i < map.getValue().size(); i++) {
                var entry = map.getValue().get(i);
                result.add(Map.entry(new Slice(document, entry.getKeyNode(), path + "{key#" + i + "}"),
                        new Slice(document, entry.getValueNode(), valuePath(path, entry.getKeyNode(), i))));
            }
            return result;
        }
    }

    public static InvalidVariablesException collision(Location first, Location second) {
        return new InvalidVariablesException("key-collision", List.of(first, second));
    }

    /** Opaque reusable authored document. Every substitution builds independent output containers. */
    public static final class Document {
        private final Node root;
        private final String source;
        private final List<Boundary> boundaries = new ArrayList<>();
        private final Set<String> selectedPaths = new LinkedHashSet<>();
        private final IdentityHashMap<Node, Boolean> markerNodes = new IdentityHashMap<>();

        private Document(Optional<Node> root, String source, boolean execution) {
            this.root = root.orElse(null);
            this.source = Objects.requireNonNull(source, "source");
            check(this.root, new IdentityHashMap<>());
            hasMarker(this.root);
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

        public Slice slice(String... fields) {
            Node node = root;
            String path = "";
            for (String name : fields) {
                node = field(node, name);
                path = path.isEmpty() ? name : path + "." + name;
                if (node == null) return null;
            }
            return new Slice(this, node, path);
        }

        /** A flattened Spring YAML field still retains its own authored occurrence. */
        public void startupSelection(Slice slice) {
            if (slice.document != this) throw new IllegalArgumentException("slice belongs to another document");
            select(slice.node, slice.path);
        }

        public boolean bootDocumentPresent() {
            if (root == null) return false;
            if (root instanceof ScalarNode scalar)
                return !Tag.NULL.equals(scalar.getTag()) && !scalar.getValue().isEmpty();
            return !(root instanceof SequenceNode sequence && sequence.getValue().isEmpty());
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

        /** Decode declarations independently of ordinary config, retaining exact schema numbers. */
        public Object declarationValues() {
            var constructor = new StandardConstructor(YamlValues.settings(true, true));
            var literals = new IdentityHashMap<Node, Object>();
            if (!(root instanceof MappingNode map)) return materialize(root, "", false, null, true, constructor, literals);
            var result = new LinkedHashMap<Object, Object>();
            for (var entry : map.getValue()) {
                Object key = constructor.constructSingleDocument(Optional.of(entry.getKeyNode()));
                if ("config".equals(key)) continue;
                if (result.containsKey(key)) throw invalid("key-collision", "");
                result.put(key, materialize(entry.getValueNode(), String.valueOf(key), false, null, true, constructor, literals));
            }
            return result;
        }

        public Object substitute(Function<String, String> lookup) {
            Objects.requireNonNull(lookup, "lookup");
            discover(); // Validate every authored marker before consulting a value source.
            return materialize(root, "", false, lookup, false, new StandardConstructor(YamlValues.settings(false, true)), new IdentityHashMap<>());
        }

        /** Structural projection retaining unresolved markers as explicit internal values. */
        public Object staged() {
            discover();
            return materialize(root, "", false, null, true, new StandardConstructor(YamlValues.settings(false, true)), new IdentityHashMap<>());
        }

        private Object materialize(Node node, String path, boolean selected, Function<String, String> lookup,
                                   boolean staging, StandardConstructor constructor, IdentityHashMap<Node, Object> literals) {
            if (node == null) return null;
            if (!hasMarker(node) && literals.containsKey(node)) return literals.get(node);
            selected = selected || selectedPaths.contains(path);
            Node name = selected ? markerName(node) : null;
            if (name != null) {
                if (staging) return new Unresolved(new Location(source, path));
                String value;
                try { value = lookup.apply(validName(name, path)); }
                catch (RuntimeException failure) { throw invalid("lookup-failed", path); }
                if (value == null) throw invalid("missing-value", path);
                if (blank(value)) throw invalid("blank-value", path);
                return value;
            }
            if (node instanceof MappingNode map && (Tag.MAP.equals(node.getTag()) || Tag.SET.equals(node.getTag()))) {
                var values = new AuthoredMap();
                var origins = new LinkedHashMap<Object, String>();
                for (int i = 0; i < map.getValue().size(); i++) {
                    var entry = map.getValue().get(i);
                    String keyPath = path + "{key#" + i + "}";
                    String valuePath = valuePath(path, entry.getKeyNode(), i);
                    Object key = materialize(entry.getKeyNode(), keyPath, selected, lookup, staging, constructor, literals);
                    String origin = string(entry.getKeyNode()) != null ? valuePath : keyPath;
                    if (origins.containsKey(key))
                        throw new InvalidVariablesException("key-collision", List.of(
                                new Location(source, origins.get(key)), new Location(source, origin)));
                    origins.put(key, origin);
                    values.put(key, materialize(entry.getValueNode(), valuePath, selected, lookup, staging, constructor, literals));
                    if (selected && markerName(entry.getKeyNode()) != null)
                        values.variableKeys.put(key, new Location(source, keyPath));
                }
                Object result = Tag.SET.equals(node.getTag()) ? new LinkedHashSet<>(values.keySet()) : values;
                if (!hasMarker(node)) literals.put(node, result);
                return result;
            }
            if (node instanceof SequenceNode sequence && Tag.SEQ.equals(node.getTag())) {
                var values = new ArrayList<Object>();
                for (int i = 0; i < sequence.getValue().size(); i++)
                    values.add(materialize(sequence.getValue().get(i), path + "[" + i + "]", selected, lookup, staging, constructor, literals));
                if (!hasMarker(node)) literals.put(node, values);
                return values;
            }
            return constructor.constructSingleDocument(Optional.of(node));
        }

        private boolean hasMarker(Node node) {
            if (node == null || node instanceof ScalarNode) return false;
            Boolean cached = markerNodes.get(node);
            if (cached != null) return cached;
            boolean found = markerName(node) != null;
            if (node instanceof MappingNode map) {
                for (var entry : map.getValue()) {
                    boolean keyMarker = hasMarker(entry.getKeyNode());
                    boolean valueMarker = hasMarker(entry.getValueNode());
                    found = found || keyMarker || valueMarker;
                }
            }
            else if (node instanceof SequenceNode sequence) {
                for (Node child : sequence.getValue()) {
                    boolean childMarker = hasMarker(child);
                    found = found || childMarker;
                }
            }
            markerNodes.put(node, found);
            return found;
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
