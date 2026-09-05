package ai.loomspan.internal.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryCapabilityRegistry implements CapabilityRegistry
{
    private final ConcurrentMap<String, CapabilityMetadata> capabilitiesByName = new ConcurrentHashMap<>();

    @Override
    public void register(String capabilityName, CapabilityMetadata metadata)
    {
        String normalizedName = requireNonBlank(capabilityName, "capabilityName");
        CapabilityMetadata nonNullMetadata = Objects.requireNonNull(metadata, "metadata must not be null");


        if (!normalizedName.equals(nonNullMetadata.name()))
        {
            throw new IllegalArgumentException("capabilityName must match metadata.name");
        }

        if (!normalizedName.matches("^[A-Za-z_][A-Za-z0-9_]{0,63}$"))
        {
            throw new IllegalArgumentException("Invalid skill name '" + normalizedName + "': use 1-64 ASCII letters, digits or underscores, starting with a letter or underscore");
        }
        CapabilityMetadata existing = capabilitiesByName.putIfAbsent(normalizedName, nonNullMetadata);
        if (existing != null)
        {
            throw new CapabilityCollisionException("Capability with name '" + normalizedName + "' is already registered at " + existing.id() + "; conflicting declaration at " + metadata.id());
        }
    }

    @Override
    public CapabilityMetadata getCapability(String name)
    {
        if (name == null || name.isBlank())
        {
            return null;
        }
        return capabilitiesByName.get(name);
    }

    @Override
    public List<CapabilityMetadata> getAllCapabilities()
    {
        return List.copyOf(capabilitiesByName.values());
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
