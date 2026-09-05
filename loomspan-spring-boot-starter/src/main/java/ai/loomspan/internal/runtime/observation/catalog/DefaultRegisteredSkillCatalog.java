package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.core.CapabilityKind;
import org.springframework.lang.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class DefaultRegisteredSkillCatalog implements RegisteredSkillCatalog
{
    private final NavigableMap<String, RegisteredSkillEntry> entries;

    public DefaultRegisteredSkillCatalog(CapabilityRegistry registry, YamlSkillCatalog catalog,
            YamlSkillCapabilityRegistrar registrar)
    {
        Objects.requireNonNull(registrar, "registrar must not be null").completeRegistration();
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
        SkillSourcePathResolver pathResolver = new SkillSourcePathResolver();
        TreeMap<String, RegisteredSkillEntry> built = new TreeMap<>();
        for (var metadata : registry.getAllCapabilities())
        {
            String name = metadata.name();
            RegisteredSkillEntry entry;
            if (metadata.kind() == CapabilityKind.YAML_SKILL)
            {
                var definition = Objects.requireNonNull(catalog.getSkill(name), "YAML registration must have a definition");
                entry = new RegisteredSkillEntry(name, "YAML", pathResolver.resolve(definition.source()),
                        null, null, decode(definition.source().bytes(), name));
            }
            else
            {
                entry = new RegisteredSkillEntry(name, "JAVA", null,
                        metadata.source().beanName(), metadata.source().method(), null);
            }
            if (built.putIfAbsent(name, entry) != null)
                throw new IllegalArgumentException("Duplicate registered skill name '" + name + "'");
        }
        this.entries = java.util.Collections.unmodifiableNavigableMap(built);
    }

    @Override
    public Optional<RegisteredSkillEntry> find(String registeredName)
    {
        Objects.requireNonNull(registeredName, "registeredName must not be null");
        return Optional.ofNullable(entries.get(registeredName));
    }

    @Override
    public List<RegisteredSkillEntry.Summary> listAfter(@Nullable String exclusiveName, int limit)
    {
        if (limit <= 0)
        {
            throw new IllegalArgumentException("limit must be positive");
        }
        NavigableMap<String, RegisteredSkillEntry> tail = exclusiveName == null
                ? entries
                : entries.tailMap(exclusiveName, false);
        return tail.values().stream().limit(limit).map(RegisteredSkillEntry::summary).toList();
    }

    @Override
    public int registeredSkillCount()
    {
        return entries.size();
    }

    private static String decode(byte[] bytes, String name)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        }
        catch (CharacterCodingException ex)
        {
            throw new IllegalStateException("YAML for registered skill '" + name + "' is not valid UTF-8", ex);
        }
    }
}
