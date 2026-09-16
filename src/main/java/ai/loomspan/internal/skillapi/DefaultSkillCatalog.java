package ai.loomspan.internal.skillapi;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillDescriptor;
import ai.loomspan.internal.core.CapabilityMetadata;

import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class DefaultSkillCatalog implements SkillCatalog
{
    private final String generationId;
    private final NavigableMap<String, SkillDescriptor> entries;
    private final List<SkillDescriptor> skills;

    public DefaultSkillCatalog(String generationId, List<CapabilityMetadata> capabilities)
    {
        this.generationId = Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) throw new IllegalArgumentException("generationId must not be blank");
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        TreeMap<String, SkillDescriptor> built = new TreeMap<>();
        for (var metadata : capabilities)
        {
            SkillDescriptor descriptor = new SkillDescriptor(metadata.name(), metadata.description(),
                    metadata.kind().publicKind(), metadata.tool().inputSchema());
            if (built.putIfAbsent(metadata.name(), descriptor) != null)
            {
                throw new IllegalArgumentException("Duplicate registered skill name '" + metadata.name() + "'");
            }
        }
        this.entries = Collections.unmodifiableNavigableMap(built);
        this.skills = List.copyOf(built.values());
    }

    @Override
    public String generationId() { return generationId; }

    @Override
    public List<SkillDescriptor> skills()
    {
        return skills;
    }

    @Override
    public Optional<SkillDescriptor> skill(String name)
    {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(entries.get(name));
    }
}
