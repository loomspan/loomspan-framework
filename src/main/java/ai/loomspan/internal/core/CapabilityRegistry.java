package ai.loomspan.internal.core;

import java.util.List;

/** Internal shared registry for every callable capability kind. */
public interface CapabilityRegistry
{
    void register(String capabilityName, CapabilityMetadata metadata);

    CapabilityMetadata getCapability(String name);

    List<CapabilityMetadata> getAllCapabilities();
}
