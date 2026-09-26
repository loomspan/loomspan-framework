package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.Environment;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.core.StreamReadFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict boundary between authored candidate YAML and runtime configuration. */
public final class ExecutionConfigurationParser
{
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final Set<String> ROOT = Set.of("loomspan");
    private static final Set<String> PUBLISHABLE = Set.of("connections", "models", "session", "execution-trace");
    private static final Set<String> CONNECTION = Set.of("driver", "base-url", "api-key-ref", "header-refs", "openai", "gemini", "provider-retry");
    private static final Set<String> MODEL = Set.of("connection", "provider-model", "thinking-levels");
    private static final Set<String> SESSION = Set.of("max-depth", "mission-timeout", "quotas", "attachments");
    private static final Set<String> QUOTAS = Set.of("max-skill-invocations", "max-tool-invocations", "max-linter-retries", "max-model-calls", "max-provider-attempts", "max-usage-units");
    private static final Set<String> RETRY = Set.of("enabled", "max-attempts", "initial-backoff", "multiplier", "max-backoff", "jitter");

    private ExecutionConfigurationParser() {}

    public static Parsed parse(ExecutionConfiguration authored, Environment environment, boolean resolveReferences)
    {
        if (authored == null) throw new IllegalArgumentException("configuration must not be null");
        try
        {
            Object decoded = YAML.readValue(authored.yaml(), Object.class);
            Map<String, Object> document = object(decoded, "configuration");
            keys(document, ROOT, "configuration");
            Map<String, Object> loomspan = object(document.get("loomspan"), "loomspan");
            keys(loomspan, PUBLISHABLE, "loomspan");
            Map<String, Object> flat = new LinkedHashMap<>();
            Map<String, Object> connections = optionalObject(loomspan.get("connections"), "loomspan.connections");
            for (var entry : connections.entrySet())
            {
                String path = "loomspan.connections." + entry.getKey();
                Map<String, Object> connection = object(entry.getValue(), path);
                keys(connection, CONNECTION, path);
                for (var field : connection.entrySet())
                {
                    String fieldPath = path + "." + field.getKey();
                    switch (field.getKey())
                    {
                        case "api-key-ref" -> flat.put(path + ".api-key", reference(field.getValue(), fieldPath, environment, resolveReferences));
                        case "header-refs" -> {
                            for (var header : object(field.getValue(), fieldPath).entrySet())
                                flat.put(path + ".headers[" + header.getKey() + "]", reference(header.getValue(), fieldPath + "." + header.getKey(), environment, resolveReferences));
                        }
                        case "openai" -> flattenObject(object(field.getValue(), fieldPath), Set.of("compatibility-profile", "organization-id", "project-id"), fieldPath, flat);
                        case "gemini" -> {
                            Map<String, Object> gemini = object(field.getValue(), fieldPath);
                            keys(gemini, Set.of("vertex-ai", "project-id", "location", "credentials-ref"), fieldPath);
                            for (var geminiField : gemini.entrySet())
                                if (geminiField.getKey().equals("credentials-ref"))
                                    flat.put(fieldPath + ".credentials-uri", reference(geminiField.getValue(), fieldPath + ".credentials-ref", environment, resolveReferences));
                                else flat.put(fieldPath + "." + geminiField.getKey(), scalar(geminiField.getValue(), fieldPath));
                        }
                        case "provider-retry" -> flattenObject(object(field.getValue(), fieldPath), RETRY, fieldPath, flat);
                        default -> flat.put(fieldPath, scalar(field.getValue(), fieldPath));
                    }
                }
            }
            for (var entry : optionalObject(loomspan.get("models"), "loomspan.models").entrySet())
            {
                String path = "loomspan.models." + entry.getKey();
                Map<String, Object> model = object(entry.getValue(), path);
                keys(model, MODEL, path);
                for (var field : model.entrySet())
                {
                    if (field.getKey().equals("thinking-levels"))
                    {
                        if (!(field.getValue() instanceof List<?> levels)) throw invalid(path + ".thinking-levels");
                        for (int i = 0; i < levels.size(); i++) flat.put(path + ".thinking-levels[" + i + "]", scalar(levels.get(i), path));
                    }
                    else flat.put(path + "." + field.getKey(), scalar(field.getValue(), path));
                }
            }
            Map<String, Object> session = optionalObject(loomspan.get("session"), "loomspan.session");
            keys(session, SESSION, "loomspan.session");
            for (var entry : session.entrySet())
            {
                String path = "loomspan.session." + entry.getKey();
                if (entry.getKey().equals("quotas")) flattenObject(object(entry.getValue(), path), QUOTAS, path, flat);
                else if (entry.getKey().equals("attachments")) flattenObject(object(entry.getValue(), path), Set.of("max-size"), path, flat);
                else flat.put(path, scalar(entry.getValue(), path));
            }
            Map<String, Object> trace = optionalObject(loomspan.get("execution-trace"), "loomspan.execution-trace");
            keys(trace, Set.of("persistence"), "loomspan.execution-trace");
            if (trace.containsKey("persistence")) flat.put("loomspan.execution-trace.persistence", scalar(trace.get("persistence"), "loomspan.execution-trace.persistence"));
            LoomspanProperties properties = new Binder(new MapConfigurationPropertySource(flat))
                    .bind("loomspan", Bindable.of(LoomspanProperties.class)).orElseGet(LoomspanProperties::new);
            properties.afterPropertiesSet();
            validateSession(properties.getSession());
            return new Parsed(properties, properties.getExecutionTrace().getPersistence());
        }
        catch (BindException ex) { throw new IllegalArgumentException(ex.getName() + " has an invalid value"); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid execution configuration; check field types and values"); }
    }

    public record Parsed(LoomspanProperties properties, TracePersistencePolicy tracePersistence) {}

    private static void validateSession(LoomspanProperties.Session session)
    {
        if (session.getMaxDepth() < 1) throw invalid("loomspan.session.max-depth");
        var q = session.getQuotas();
        if (q.getMaxSkillInvocations() < 0 || q.getMaxToolInvocations() < 0 || q.getMaxLinterRetries() < 0
                || q.getMaxModelCalls() < 0 || q.getMaxProviderAttempts() < 0 || q.getMaxUsageUnits() < 0)
            throw invalid("loomspan.session.quotas");
    }

    private static String reference(Object value, String path, Environment environment, boolean resolve)
    {
        String key = scalar(value, path);
        if (key.isBlank()) throw invalid(path);
        if (!resolve) return "unresolved-reference";
        String resolved = environment.getProperty(key);
        if (resolved == null || resolved.isBlank()) throw new IllegalArgumentException(path + " references a missing or blank external property");
        return resolved;
    }

    private static void flattenObject(Map<String, Object> object, Set<String> allowed, String path, Map<String, Object> flat)
    {
        keys(object, allowed, path);
        object.forEach((key, value) -> flat.put(path + "." + key, scalar(value, path + "." + key)));
    }

    private static Map<String, Object> optionalObject(Object value, String path)
    {
        return value == null ? Map.of() : object(value, path);
    }

    private static Map<String, Object> object(Object value, String path)
    {
        if (!(value instanceof Map<?, ?> source)) throw invalid(path);
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : source.entrySet())
        {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) throw invalid(path);
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static String scalar(Object value, String path)
    {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) throw invalid(path);
        return String.valueOf(value);
    }

    private static void keys(Map<String, Object> object, Set<String> allowed, String path)
    {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException(path + "." + key + " is not publishable");
    }

    private static IllegalArgumentException invalid(String path) { return new IllegalArgumentException(path + " has an invalid value"); }
}
