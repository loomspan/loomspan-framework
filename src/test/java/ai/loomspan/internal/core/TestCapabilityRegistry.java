package ai.loomspan.internal.core;

import ai.loomspan.internal.skill.SkillGeneration;
import ai.loomspan.internal.skill.SkillGenerationManager;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.testkit.TestSkillGenerations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mutable test fixture builder; production generations remain immutable. */
public final class TestCapabilityRegistry
{
    private final Map<String, CapabilityMetadata> capabilities = new LinkedHashMap<>();
    private SkillMethodBeanPostProcessor discovered;
    private SkillGenerationManager manager;

    public void bind(SkillMethodBeanPostProcessor processor) { this.discovered = processor; }

    public void register(String name, CapabilityMetadata metadata)
    {
        if (!name.equals(metadata.name())) throw new IllegalArgumentException("name mismatch");
        CapabilityMetadata prior = capabilities.putIfAbsent(name, metadata);
        if (prior != null) throw new CapabilityCollisionException("duplicate capability " + name);
        if (manager != null) manager.activate(generation());
    }

    public CapabilityMetadata getCapability(String name)
    {
        CapabilityMetadata direct = capabilities.get(name);
        if (direct != null || discovered == null) return direct;
        return discovered.capabilities().stream().filter(value -> value.name().equals(name)).findFirst().orElse(null);
    }
    public List<CapabilityMetadata> getAllCapabilities()
    {
        if (discovered == null) return List.copyOf(capabilities.values());
        LinkedHashMap<String, CapabilityMetadata> combined = new LinkedHashMap<>(capabilities);
        discovered.capabilities().forEach(value -> combined.put(value.name(), value));
        return List.copyOf(combined.values());
    }

    public SkillGeneration generation()
    {
        LinkedHashMap<String, CapabilityMetadata> snapshot = new LinkedHashMap<>();
        getAllCapabilities().forEach(value -> snapshot.put(value.name(), value));
        return TestSkillGenerations.of(snapshot, Map.of());
    }

    public SkillGeneration generation(YamlSkillCatalog catalog)
    {
        LinkedHashMap<String, YamlSkillDefinition> definitions = new LinkedHashMap<>();
        LinkedHashMap<String, CapabilityMetadata> snapshot = new LinkedHashMap<>();
        getAllCapabilities().forEach(value -> {
            snapshot.put(value.name(), value);
            YamlSkillDefinition definition = catalog.getSkill(value.name());
            if (definition != null) definitions.put(value.name(), definition);
        });
        return TestSkillGenerations.of(snapshot, definitions);
    }

    public SkillGenerationManager manager()
    {
        if (manager != null) return manager;
        manager = new SkillGenerationManager(new SkillMethodBeanPostProcessor(),
                () -> { throw new AssertionError("test manager must not prepare"); },
                new ai.loomspan.internal.runtime.input.SkillInputContractResolver(),
                new org.springframework.beans.factory.support.StaticListableBeanFactory());
        manager.activate(generation());
        return manager;
    }
}
