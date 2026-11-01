package ai.loomspan.internal.autoconfigure;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import ai.loomspan.internal.serialization.YamlValues;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Retains executor YAML values before Boot's scalar flattening; ordinary values use Boot. */
public final class ExecutorYamlPropertySourceLoader implements PropertySourceLoader, Ordered {
    private static final String KEY = "loomspan.executor-config";
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public String[] getFileExtensions() { return new String[]{"yml", "yaml"}; }
    /** Opaque transport only. Validation occurs when an active source is bound. */
    record Raw(Object value) {}
    @Override public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        var documents = new ArrayList<Map<?, ?>>();
        try (var input = resource.getInputStream()) {
            for (Object value : YamlValues.documents(input)) {
                if (value instanceof Map<?, ?> map) documents.add(map);
                // Boot ignores null/empty scalar and empty sequence documents, but
                // retains other root values under its ordinary "document" property.
                else if (value != null && !(value instanceof String text && text.isEmpty())
                        && !(value instanceof List<?> list && list.isEmpty())) documents.add(Map.of());
            }
        }
        var delegated = new YamlPropertySourceLoader().load(name, resource);
        if (delegated.size() != documents.size()) throw new IllegalStateException(resource.getDescription() + " YAML document association mismatch");
        var result = new ArrayList<PropertySource<?>>();
        for (int i = 0; i < delegated.size(); i++) {
            var source = (MapPropertySource) delegated.get(i);
            Object loomspan = documents.get(i).get("loomspan");
            if (!(loomspan instanceof Map<?, ?> framework) || !framework.containsKey("executor-config")) {
                result.add(source);
                continue;
            }
            var properties = new LinkedHashMap<String, Object>();
            for (String key : source.getPropertyNames())
                if (!key.equals(KEY) && !key.startsWith(KEY + ".") && !key.startsWith(KEY + "["))
                    properties.put(key, source.getSource().get(key));
            properties.put(KEY, new Raw(framework.get("executor-config")));
            result.add(new org.springframework.boot.env.OriginTrackedMapPropertySource(source.getName(), properties, true));
        }
        return result;
    }
}
