package ai.loomspan.internal.autoconfigure;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import ai.loomspan.internal.serialization.AuthoredYamlVariables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retains selected authored nodes while Boot selects profiles and imported sources. */
public final class ExecutorYamlPropertySourceLoader implements PropertySourceLoader, Ordered {
    static final String EXECUTORS = "loomspan.executor-config";
    static final String CONNECTIONS = "loomspan.connections";
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public String[] getFileExtensions() { return new String[]{"yml", "yaml"}; }
    /** Opaque transport only. Validation occurs when an active source is bound. */
    record Raw(AuthoredYamlVariables.Slice value) {}
    @Override public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        List<AuthoredYamlVariables.Document> documents;
        try (var input = resource.getInputStream()) {
            documents = AuthoredYamlVariables.startupDocuments(input, resource.getDescription()).stream()
                    .filter(AuthoredYamlVariables.Document::bootDocumentPresent).toList();
        }
        var delegated = new YamlPropertySourceLoader().load(name, resource);
        if (delegated.size() != documents.size()) throw new IllegalStateException(resource.getDescription() + " YAML document association mismatch");
        var result = new ArrayList<PropertySource<?>>();
        for (int i = 0; i < delegated.size(); i++) {
            var source = (MapPropertySource) delegated.get(i);
            var retained = new LinkedHashMap<String, Raw>();
            retain(documents.get(i), documents.get(i).slice(), "", retained);
            if (retained.isEmpty()) {
                result.add(source);
                continue;
            }
            var properties = new LinkedHashMap<String, Object>();
            for (String key : source.getPropertyNames())
                if (retained.keySet().stream().noneMatch(root -> selected(key, root)))
                    properties.put(key, source.getSource().get(key));
            properties.putAll(retained);
            result.add(new org.springframework.boot.env.OriginTrackedMapPropertySource(source.getName(), properties, true));
        }
        return result;
    }

    static boolean selected(String name, String root) {
        var candidate = org.springframework.boot.context.properties.source.ConfigurationPropertyName.adapt(name.toLowerCase(java.util.Locale.ROOT), '.');
        var prefix = org.springframework.boot.context.properties.source.ConfigurationPropertyName.adapt(root.toLowerCase(java.util.Locale.ROOT), '.');
        return prefix.equals(candidate) || prefix.isAncestorOf(candidate);
    }

    private static void retain(AuthoredYamlVariables.Document document, AuthoredYamlVariables.Slice node,
            String path, Map<String, Raw> retained) {
        var entries = node.entries();
        if (entries == null) return;
        for (var entry : entries) {
            if (!(entry.getKey().literal() instanceof String key)) continue;
            String name = path.isEmpty() ? key : path + "." + key;
            if (selected(name, CONNECTIONS) || selected(name, EXECUTORS)) {
                var candidate = org.springframework.boot.context.properties.source.ConfigurationPropertyName.adapt(name.toLowerCase(java.util.Locale.ROOT), '.');
                boolean root = candidate.equals(org.springframework.boot.context.properties.source.ConfigurationPropertyName.of(CONNECTIONS))
                        || candidate.equals(org.springframework.boot.context.properties.source.ConfigurationPropertyName.of(EXECUTORS));
                if (!root) document.startupSelection(entry.getValue());
                else {
                    var children = entry.getValue().entries();
                    if (children != null) for (var child : children)
                        if (!child.getValue().selected()) document.startupSelection(child.getValue());
                }
                retained.put(name, new Raw(entry.getValue()));
            }
            else if (selected(CONNECTIONS, name) || selected(EXECUTORS, name))
                retain(document, entry.getValue(), name, retained);
        }
    }
}
