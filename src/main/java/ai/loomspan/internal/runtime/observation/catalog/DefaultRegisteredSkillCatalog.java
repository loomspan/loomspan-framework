package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityKind;
import org.springframework.lang.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class DefaultRegisteredSkillCatalog implements RegisteredSkillCatalog
{
    private final NavigableMap<String, RegisteredSkillEntry> entries;

    public DefaultRegisteredSkillCatalog(List<CapabilityMetadata> capabilities,
            Map<String, YamlSkillDefinition> definitions)
    {
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        Objects.requireNonNull(definitions, "definitions must not be null");
        SkillSourcePathResolver pathResolver = new SkillSourcePathResolver();
        TreeMap<String, RegisteredSkillEntry> built = new TreeMap<>();
        for (var metadata : capabilities)
        {
            String name = metadata.name();
            RegisteredSkillEntry entry;
            if (metadata.kind() == CapabilityKind.YAML_SKILL || metadata.kind() == CapabilityKind.REST_SKILL)
            {
                var definition = Objects.requireNonNull(definitions.get(name), "manifest registration must have a definition");
                entry = new RegisteredSkillEntry(name,
                        metadata.kind().publicKind().name(),
                        pathResolver.resolve(definition.source()),
                        null, null, decode(definition.source().bytes(), name));
            }
            else if (metadata.kind() == CapabilityKind.JAVA_SKILL)
            {
                entry = new RegisteredSkillEntry(name, metadata.kind().publicKind().name(), null,
                        metadata.source().beanName(), metadata.source().method(), null);
            }
            else throw new IllegalStateException("Unsupported capability kind " + metadata.kind());
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
