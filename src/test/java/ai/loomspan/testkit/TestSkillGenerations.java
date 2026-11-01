package ai.loomspan.testkit;

import ai.loomspan.api.SkillKind;
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
    private static final SkillGeneration EMPTY = new SkillGeneration("default", "test-empty", Map.of(), Map.of());

    private TestSkillGenerations() {}

    public static void activate(ai.loomspan.internal.skill.SkillGenerationManager manager, SkillGeneration generation) {
        var base = manager.capturePreparation(generation.namespace()).baseId();
        ai.loomspan.internal.skill.SkillGenerationManager.dispatch(manager.publish(generation, base));
    }

    public static SkillGeneration empty() { return EMPTY; }

    public static CapabilityMetadata leaf() {
        return new CapabilityMetadata("test:leaf", "default-leaf", "fixture",
                ai.loomspan.internal.core.SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.unrestricted(),
                input -> "ok", ai.loomspan.api.SkillKind.JAVA,
                ai.loomspan.internal.core.CapabilityToolDescriptor.generic("default-leaf", "fixture"),
                new ai.loomspan.internal.core.SkillSource(null, "fixture", "leaf()"));
    }

    public static ai.loomspan.internal.skill.SkillAdmissionSnapshot snapshot(SkillGeneration generation) {
        return new ai.loomspan.internal.skill.SkillAdmissionSnapshot(
                new ai.loomspan.internal.core.SkillIdentity(generation.namespace(), "test"), Map.of(generation.namespace(), generation));
    }

    public static ai.loomspan.internal.core.ExecutionBinding binding(ai.loomspan.internal.core.LoomspanSession session,
            ai.loomspan.internal.core.MissionContext mission, ai.loomspan.internal.core.PhysicalBranchContext branch,
            SkillGeneration generation) {
        return new ai.loomspan.internal.core.ExecutionBinding(session, mission, branch, generation,
                new ai.loomspan.internal.skill.SkillAdmissionSnapshot(
                        ai.loomspan.internal.core.SkillIdentity.parse(ai.loomspan.internal.core.TestLoomspanSessions.entrySkill(session)), Map.of(generation.namespace(), generation)));
    }

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
        String id = UUID.randomUUID().toString();
        return new SkillGeneration("default", id, capabilities, definitions,
                new DefaultSkillCatalog("default", id, capabilities.values().stream().toList()),
                new DefaultRegisteredSkillCatalog(java.util.List.of(), Map.of()));
    }
}
