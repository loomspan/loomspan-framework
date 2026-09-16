package ai.loomspan.internal.skill;

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
public final class SkillGeneration
{
    private final String id;
    private final Map<String, CapabilityMetadata> capabilities;
    private final Map<String, YamlSkillDefinition> definitions;
    private final SkillCatalog skillCatalog;
    private final RegisteredSkillCatalog registeredSkillCatalog;

    public SkillGeneration(String id, Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions)
    {
        this(id, capabilities, definitions, null, null);
    }

    public SkillGeneration(String id, Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions, SkillCatalog skillCatalog,
            RegisteredSkillCatalog registeredSkillCatalog)
    {
        this.id = requireNonBlank(id, "id");
        this.capabilities = immutableExactMap(capabilities, "capabilities");
        this.definitions = immutableExactMap(definitions, "definitions");
        for (Map.Entry<String, CapabilityMetadata> entry : this.capabilities.entrySet())
            if (!entry.getKey().equals(entry.getValue().name()))
                throw new IllegalArgumentException("capability key must match metadata name");
        for (Map.Entry<String, YamlSkillDefinition> entry : this.definitions.entrySet())
        {
            if (!entry.getKey().equals(entry.getValue().manifest().getName()))
                throw new IllegalArgumentException("definition key must match manifest name");
            CapabilityMetadata metadata = this.capabilities.get(entry.getKey());
            if (metadata == null || (metadata.kind() != ai.loomspan.internal.core.CapabilityKind.YAML_SKILL
                    && metadata.kind() != ai.loomspan.internal.core.CapabilityKind.REST_SKILL))
                throw new IllegalArgumentException("definition must have matching YAML or REST capability '"
                        + entry.getKey() + "'");
        }
        List<CapabilityMetadata> ordered = this.capabilities.values().stream()
                .sorted(java.util.Comparator.comparing(CapabilityMetadata::name)).toList();
        this.skillCatalog = skillCatalog == null ? new DefaultSkillCatalog(id, ordered) : skillCatalog;
        this.registeredSkillCatalog = registeredSkillCatalog == null
                ? new DefaultRegisteredSkillCatalog(ordered, this.definitions)
                : registeredSkillCatalog;
    }

    public String id() { return id; }
    public CapabilityMetadata capability(String name) { return capabilities.get(name); }
    public YamlSkillDefinition definition(String name) { return definitions.get(name); }
    public List<CapabilityMetadata> capabilities() { return List.copyOf(capabilities.values()); }
    public Map<String, YamlSkillDefinition> definitions() { return definitions; }
    public SkillCatalog skillCatalog() { return skillCatalog; }
    public RegisteredSkillCatalog registeredSkillCatalog() { return registeredSkillCatalog; }

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
