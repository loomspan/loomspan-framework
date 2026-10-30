package ai.loomspan.testkit;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.autoconfigure.LoomspanProperties;
import java.util.*;

/** Explicit complete publication inputs for tests; never reads an active generation. */
public final class PublicationFixtures {
    private PublicationFixtures() {}
    public static ExecutionConfiguration configuration() {
        return new ExecutionConfiguration("""
                loomspan:
                  connections:
                    connection: {driver: ollama, base-url: 'http://localhost:11434'}
                    native: {driver: typesafe, api-key-ref: fixture.key}
                  models:
                    model: {connection: connection, provider-model: provider-model, thinking-levels: [medium]}
                    native: {connection: native, provider-model: jev}
                    gpt-5: {connection: connection, provider-model: provider-model}
                """);
    }
    public static ExecutionConfiguration configuration(LoomspanProperties properties) {
        Map<String,Object> root = new LinkedHashMap<>();
        Map<String,Object> connections = new LinkedHashMap<>();
        properties.getConnections().forEach((name, connection) -> {
            Map<String,Object> value = bean(connection);
            value.remove("headers"); value.remove("api-key");
            if (connection.getApiKey() != null) value.put("api-key-ref", "loomspan.connections." + name + ".api-key");
            if (!connection.getHeaders().isEmpty()) {
                Map<String,Object> headers = new LinkedHashMap<>();
                connection.getHeaders().forEach((header, ignored) -> headers.put(header, "loomspan.connections." + name + ".headers." + header));
                value.put("header-refs", headers);
            }
            connections.put(name, value);
        });
        root.put("connections", connections); root.put("models", convert(properties.getModels()));
        root.put("session", bean(properties.getSession()));
        root.put("execution-trace", Map.of("persistence", properties.getExecutionTrace().getPersistence().name()));
        return new ExecutionConfiguration(tools.jackson.dataformat.yaml.YAMLMapper.builder().build().writeValueAsString(Map.of("loomspan", root)));
    }
    public static List<SkillDocument> documents(LoomspanProperties properties) {
        List<SkillDocument> documents = new ArrayList<>();
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        try {
            for (String pattern : properties.getSkills().getLocations())
                for (var resource : resolver.getResources(pattern))
                    if (resource.exists()) documents.add(new SkillDocument(resource.getDescription(), resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        return List.copyOf(documents);
    }
    private static Map<String,Object> bean(Object value) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (var method : value.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0 || name.equals("getClass") || !(name.startsWith("get") || name.startsWith("is"))) continue;
            String field = name.substring(name.startsWith("get") ? 3 : 2).replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
            try { Object fieldValue = method.invoke(value); if (fieldValue != null) result.put(field, convert(fieldValue)); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
        return result;
    }
    private static Object convert(Object value) {
        if (value instanceof Map<?,?> map) { Map<String,Object> result = new LinkedHashMap<>(); map.forEach((k,v) -> result.put(k.toString(), convert(v))); return result; }
        if (value instanceof Collection<?> list) return list.stream().map(PublicationFixtures::convert).toList();
        if (value instanceof java.time.Duration || value instanceof org.springframework.util.unit.DataSize) return value.toString();
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        return bean(value);
    }
}
