package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import ai.loomspan.internal.serialization.AuthoredYamlVariables;

/** Strict boundary between authored candidate YAML and runtime configuration. */
public final class ExecutionConfigurationParser
{
    private static final Set<String> ROOT = Set.of("loomspan");
    private static final Set<String> PUBLISHABLE = Set.of("connections", "models", "session", "execution-trace", "executor-config");
    private static final Set<String> CONNECTION = Set.of("driver", "base-url", "request-timeout", "api-key", "headers", "openai", "gemini", "provider-retry");
    private static final Set<String> MODEL = Set.of("connection", "provider-model", "thinking-levels");
    private static final Set<String> SESSION = Set.of("max-depth", "mission-timeout", "quotas", "attachments");
    private static final Set<String> QUOTAS = Set.of("max-skill-invocations", "max-tool-invocations", "max-linter-retries", "max-model-calls", "max-provider-attempts", "max-usage-units");
    private static final Set<String> RETRY = Set.of("enabled", "max-attempts", "initial-backoff", "multiplier", "max-backoff", "jitter");

    private ExecutionConfigurationParser() {}

    public static Parsed parse(String targetNamespace, ExecutionConfiguration authored, Environment environment, boolean resolveVariables)
    {
        var candidate = new PreparedCandidateVariables(authored, List.of());
        var staged = parseTree(targetNamespace, candidate.stagedExecution());
        return resolveVariables ? parseTree(targetNamespace, candidate.resolve(name -> environment.getProperty(name), null).execution()) : staged;
    }

    public static Parsed parse(String targetNamespace, ExecutionConfiguration authored, Map<String, String> variableValues)
    {
        var values = new LinkedHashMap<>(java.util.Objects.requireNonNull(variableValues, "variableValues must not be null"));
        var candidate = new PreparedCandidateVariables(authored, List.of());
        parseTree(targetNamespace, candidate.stagedExecution());
        return parseTree(targetNamespace, candidate.resolve(values::get, values).execution());
    }

    private final Set<String> unknown = new HashSet<>();
    private final Set<String> nonblank = new HashSet<>();
    private final Map<String, AuthoredYamlVariables.Location> variableFields = new LinkedHashMap<>();

    static Parsed parseTree(String targetNamespace, Object decoded)
    {
        return new ExecutionConfigurationParser().bindTree(targetNamespace, decoded);
    }

    private Parsed bindTree(String targetNamespace, Object decoded)
    {
        try
        {
            Map<String, Object> document = object(decoded, "configuration");
            keys(document, ROOT, "configuration");
            Map<String, Object> loomspan = object(document.get("loomspan"), "loomspan");
            keys(loomspan, PUBLISHABLE, "loomspan");
            ExecutorConfiguration executorConfig = loomspan.containsKey("executor-config")
                    ? ExecutorConfiguration.from(PreparedCandidateVariables.knownKeys(loomspan.get("executor-config"))).requireTarget(targetNamespace) : ExecutorConfiguration.empty();
            Map<String, Object> flat = new LinkedHashMap<>();
            Map<String, Map<String, String>> connectionHeaders = new LinkedHashMap<>();
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
                        case "headers" -> {
                            if (!(field.getValue() instanceof Map<?, ?> headers)) throw invalid(fieldPath);
                            Map<String, String> values = new LinkedHashMap<>();
                            for (var header : headers.entrySet())
                            {
                                if (header.getValue() == null) throw invalid(fieldPath + " value must not be null");
                                if (header.getKey() instanceof AuthoredYamlVariables.Unresolved) {
                                    scalar(header.getValue(), fieldPath);
                                    continue;
                                }
                                if (!(header.getKey() instanceof String name)) throw invalid(fieldPath);
                                LoomspanProperties.validateHeaderName(fieldPath, name);
                                if (header.getValue() instanceof AuthoredYamlVariables.Unresolved) continue;
                                values.put(name, scalar(header.getValue(), fieldPath + "." + name));
                            }
                            connectionHeaders.put(entry.getKey(), values);
                        }
                        case "openai" -> flattenObject(object(field.getValue(), fieldPath), Set.of("organization-id", "project-id"), fieldPath, flat);
                        case "gemini" -> {
                            Map<String, Object> gemini = object(field.getValue(), fieldPath);
                            keys(gemini, Set.of("vertex-ai", "project-id", "location", "credentials-json"), fieldPath);
                            for (var geminiField : gemini.entrySet())
                                flat.put(fieldPath + "." + geminiField.getKey(), scalar(geminiField.getValue(), fieldPath + "." + geminiField.getKey()));
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
            properties.setExecutorConfig(executorConfig);
            connections.forEach((name, fields) -> {
                var connection = properties.getConnections().computeIfAbsent(name, ignored -> new LoomspanProperties.ConnectionProperties());
                var authoredFields = (Map<?, ?>) fields;
                if (authoredFields.get("headers") instanceof Map<?, ?> headers)
                    LoomspanProperties.validateHeaderDriver("loomspan.connections." + name + ".headers", connection.getDriver(), !headers.isEmpty());
                if (authoredFields.containsKey("openai") && connection.getOpenai() == null)
                    connection.setOpenai(new LoomspanProperties.OpenAiOptions());
                if (authoredFields.containsKey("gemini") && connection.getGemini() == null)
                    connection.setGemini(new LoomspanProperties.GeminiOptions());
            });
            connectionHeaders.forEach((name, headers) -> properties.getConnections()
                    .computeIfAbsent(name, ignored -> new LoomspanProperties.ConnectionProperties()).setHeaders(headers));
            properties.validateAuthored(path -> flat.get(path) != null
                    || unknown.stream().noneMatch(item -> path.equals(item) || path.startsWith(item + ".")), nonblank::contains);
            validateSession(properties.getSession());
            return new Parsed(properties, properties.getExecutionTrace().getPersistence(), executorConfig);
        }
        catch (BindException ex) { throw new IllegalArgumentException(ex.getName() + " has an invalid value"); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid execution configuration; check field types and values"); }
    }

    public record Parsed(LoomspanProperties properties, TracePersistencePolicy tracePersistence, ExecutorConfiguration executorConfig) {}

    private static void validateSession(LoomspanProperties.Session session)
    {
        if (session.getMaxDepth() < 1) throw invalid("loomspan.session.max-depth");
        var q = session.getQuotas();
        if (q.getMaxSkillInvocations() < 0 || q.getMaxToolInvocations() < 0 || q.getMaxLinterRetries() < 0
                || q.getMaxModelCalls() < 0 || q.getMaxProviderAttempts() < 0 || q.getMaxUsageUnits() < 0)
            throw invalid("loomspan.session.quotas");
    }

    private void flattenObject(Map<String, Object> object, Set<String> allowed, String path, Map<String, Object> flat)
    {
        keys(object, allowed, path);
        object.forEach((key, value) -> flat.put(path + "." + key, scalar(value, path + "." + key)));
    }

    private Map<String, Object> optionalObject(Object value, String path)
    {
        return value == null ? Map.of() : object(value, path);
    }

    private Map<String, Object> object(Object value, String path)
    {
        if (!(value instanceof Map<?, ?> source)) throw invalid(path);
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : source.entrySet())
        {
            if (entry.getKey() instanceof AuthoredYamlVariables.Unresolved) { unknown.add(path); continue; }
            if (!(entry.getKey() instanceof String key) || key.isBlank()) throw invalid(path);
            if (source instanceof AuthoredYamlVariables.AuthoredMap authored && authored.variableKeyLocation(key) != null)
                variableFields.put(path + "." + key, authored.variableKeyLocation(key));
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String scalar(Object value, String path)
    {
        if (value instanceof AuthoredYamlVariables.Unresolved) { unknown.add(path); nonblank.add(path); return null; }
        if (value == null || value instanceof Map<?, ?> || value instanceof java.util.Collection<?>) throw invalid(path);
        return String.valueOf(value);
    }

    private void keys(Map<String, Object> object, Set<String> allowed, String path)
    {
        for (String key : object.keySet()) {
            if (allowed.contains(key)) continue;
            var location = variableFields.get(path + "." + key);
            String authored = location == null ? path + "." + key : location.source() + " at " + location.path();
            throw new IllegalArgumentException(authored + " is not publishable");
        }
    }

    private static IllegalArgumentException invalid(String path) { return new IllegalArgumentException(path + " has an invalid value"); }

}
