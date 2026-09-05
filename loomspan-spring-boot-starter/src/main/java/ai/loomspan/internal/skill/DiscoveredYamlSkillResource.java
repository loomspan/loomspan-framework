package ai.loomspan.internal.skill;

import org.springframework.core.io.Resource;

import java.util.Objects;

record DiscoveredYamlSkillResource(String locationPattern, Resource resource)
{
    DiscoveredYamlSkillResource
    {
        if (locationPattern == null || locationPattern.isBlank())
        {
            throw new IllegalArgumentException("locationPattern must not be blank");
        }
        Objects.requireNonNull(resource, "resource must not be null");
    }
}
