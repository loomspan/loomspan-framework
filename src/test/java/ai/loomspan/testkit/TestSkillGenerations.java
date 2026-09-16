package ai.loomspan.testkit;

import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.skill.SkillGeneration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog;
import ai.loomspan.internal.skillapi.DefaultSkillCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TestSkillGenerations
{
    private static final SkillGeneration EMPTY = new SkillGeneration("test-empty", Map.of(), Map.of());

    private TestSkillGenerations() {}

    public static SkillGeneration empty() { return EMPTY; }

    public static SkillGeneration of(CapabilityMetadata... capabilities)
    {
        LinkedHashMap<String, CapabilityMetadata> byName = new LinkedHashMap<>();
        for (CapabilityMetadata capability : capabilities) byName.put(capability.name(), capability);
        return testGeneration(byName, Map.of());
    }

    public static SkillGeneration of(Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions)
    {
        return testGeneration(capabilities, definitions);
    }

    private static SkillGeneration testGeneration(Map<String, CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions)
    {
        return new SkillGeneration(UUID.randomUUID().toString(), capabilities, definitions,
                new DefaultSkillCatalog(capabilities.values().stream().toList()),
                new DefaultRegisteredSkillCatalog(java.util.List.of(), Map.of()));
    }
}
