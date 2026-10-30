package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog;
import ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog;
import ai.loomspan.internal.skillapi.DefaultSkillCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One complete immutable executable view of every skill declaration. */
public final class SkillGeneration implements AutoCloseable
{
    private final String namespace;
    private final String id;
    private final Map<String, CapabilityMetadata> capabilities;
    private final Map<String, YamlSkillDefinition> definitions;
    private final SkillCatalog skillCatalog;
    private final RegisteredSkillCatalog registeredSkillCatalog;
    private final ExecutionRuntime runtime;

    public SkillGeneration(String namespace, String id, Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions)
    {
        this(namespace, id, capabilities, definitions, null, null);
    }

    public SkillGeneration(String namespace, String id, Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions, SkillCatalog skillCatalog,
            RegisteredSkillCatalog registeredSkillCatalog)
    {
        this(namespace, id, capabilities, definitions, skillCatalog, registeredSkillCatalog, null);
    }

    public SkillGeneration(String namespace, String id, Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions, SkillCatalog skillCatalog,
            RegisteredSkillCatalog registeredSkillCatalog, ExecutionRuntime runtime)
    {
        this.namespace = SkillIdentity.normalizeNamespace(namespace);
        this.runtime = runtime;
        this.id = requireNonBlank(id, "id");
        this.capabilities = immutableExactMap(capabilities, "capabilities");
        this.definitions = immutableExactMap(definitions, "definitions");
        for (Map.Entry<String, CapabilityMetadata> entry : this.capabilities.entrySet())
            if (!this.namespace.equals(entry.getValue().identity().namespace()) || !entry.getKey().equals(entry.getValue().name()))
                throw new IllegalArgumentException("capability key must match metadata name");
        for (Map.Entry<String, YamlSkillDefinition> entry : this.definitions.entrySet())
        {
            if (!this.namespace.equals(entry.getValue().identity().namespace())
                    || !entry.getKey().equals(new SkillIdentity(this.namespace, entry.getValue().manifest().getName()).toString()))
                throw new IllegalArgumentException("definition key must match manifest name");
            CapabilityMetadata metadata = this.capabilities.get(entry.getKey());
            var definition = entry.getValue();
            var expectedKind = definition.rest() ? ai.loomspan.internal.core.CapabilityKind.REST_SKILL
                    : definition.typesafe() ? ai.loomspan.internal.core.CapabilityKind.TYPESAFE_SKILL
                    : ai.loomspan.internal.core.CapabilityKind.YAML_SKILL;
            if (metadata == null || metadata.kind() != expectedKind)
                throw new IllegalArgumentException("definition must have matching YAML, REST or TYPESAFE capability '"
                        + entry.getKey() + "'");
        }
        List<CapabilityMetadata> ordered = this.capabilities.values().stream()
                .sorted(java.util.Comparator.comparing(CapabilityMetadata::name)).toList();
        this.skillCatalog = skillCatalog == null ? new DefaultSkillCatalog(this.namespace, id, ordered) : skillCatalog;
        if (!this.namespace.equals(this.skillCatalog.namespace()) || !id.equals(this.skillCatalog.generationId()))
            throw new IllegalArgumentException("catalog must match generation namespace and ID");
        this.registeredSkillCatalog = registeredSkillCatalog == null
                ? new DefaultRegisteredSkillCatalog(ordered, this.definitions)
                : registeredSkillCatalog;
    }

    public String namespace() { return namespace; }
    public String id() { return id; }
    public CapabilityMetadata capability(String name) { return capabilities.get(name); }
    public YamlSkillDefinition definition(String name) { return definitions.get(name); }
    public List<CapabilityMetadata> capabilities() { return List.copyOf(capabilities.values()); }
    public Map<String, YamlSkillDefinition> definitions() { return definitions; }
    public SkillCatalog skillCatalog() { return skillCatalog; }
    public RegisteredSkillCatalog registeredSkillCatalog() { return registeredSkillCatalog; }
    public ExecutionRuntime runtime() { return runtime; }
    @Override public void close() { if (runtime != null) runtime.close(); }

    public boolean owns(CapabilityMetadata capability)
    {
        return capability != null && capabilities.get(capability.name()) == capability;
    }

    private static <T> Map<String, T> immutableExactMap(Map<String, T> source, String name)
    {
        Objects.requireNonNull(source, name + " must not be null");
        LinkedHashMap<String, T> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(requireNonBlank(key, name + " key"),
                Objects.requireNonNull(value, name + " value must not be null")));
        return Collections.unmodifiableMap(copy);
    }

    private static String requireNonBlank(String value, String field)
    {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
