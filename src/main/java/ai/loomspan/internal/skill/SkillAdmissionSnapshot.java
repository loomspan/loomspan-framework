package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;
import java.util.Map;
import java.util.Objects;

/** Immutable authoritative generation association selected for one root's declared graph. */
public record SkillAdmissionSnapshot(SkillIdentity entry, Map<String, SkillGeneration> generations) {
    public SkillAdmissionSnapshot {
        Objects.requireNonNull(entry, "entry must not be null");
        generations = Map.copyOf(generations);
        generations.forEach((namespace, generation) -> {
            if (!namespace.equals(generation.namespace()))
                throw new IllegalArgumentException("Snapshot key must match generation namespace");
        });
        if (!generations.containsKey(entry.namespace()))
            throw new IllegalArgumentException("Snapshot must contain entry namespace");
    }

    public SkillGeneration entryGeneration() { return generations.get(entry.namespace()); }
    public ai.loomspan.internal.core.CapabilityMetadata resolve(SkillIdentity identity) {
        var capability = generation(identity.namespace()).capability(identity.toString());
        if (capability == null) throw new IllegalArgumentException("Unknown captured capability '" + identity + "'");
        return capability;
    }
    public SkillGeneration generation(String namespace) {
        var generation = generations.get(namespace);
        if (generation == null) throw new IllegalArgumentException("Namespace '" + namespace + "' was not captured");
        return generation;
    }
}
