package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;
import java.util.Map;
import java.util.Objects;

/** Immutable authoritative generation association selected for one root's declared graph. */
public record SkillAdmissionSnapshot(SkillIdentity entry, Map<String, SkillGeneration> generations,
        Map<String, ExecutorBinding> executorBindings) {
    public record ExecutorBinding(ai.loomspan.internal.core.SkillExecutorRegistration registration,
            Map<String, Object> configuration, String generationId) {
        public ExecutorBinding {
            Objects.requireNonNull(registration);
            configuration = ai.loomspan.internal.util.ImmutableContainers.copyMap(configuration);
            Objects.requireNonNull(generationId);
        }
    }
    public SkillAdmissionSnapshot(SkillIdentity entry, Map<String, SkillGeneration> generations) {
        this(entry, generations, Map.of());
    }
    public SkillAdmissionSnapshot {
        Objects.requireNonNull(entry, "entry must not be null");
        generations = Map.copyOf(generations);
        executorBindings = Map.copyOf(executorBindings);
        generations.forEach((namespace, generation) -> {
            if (!namespace.equals(generation.namespace()))
                throw new IllegalArgumentException("Snapshot key must match generation namespace");
        });
        if (!generations.containsKey(entry.namespace()))
            throw new IllegalArgumentException("Snapshot must contain entry namespace");
    }

    public SkillGeneration entryGeneration() { return generations.get(entry.namespace()); }
    public ExecutorBinding executorBinding(String skillName) {
        var binding = executorBindings.get(skillName);
        if (binding == null) throw new IllegalStateException("No admitted executor binding for '" + skillName + "'");
        return binding;
    }
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
