package ai.loomspan.internal.skill;

import org.springframework.core.io.Resource;

import java.util.Objects;

public final class YamlSkillSource
{
    private final Resource resource;
    private final String locationPattern;
    private final byte[] bytes;

    public YamlSkillSource(Resource resource, String locationPattern, byte[] bytes)
    {
        this.resource = Objects.requireNonNull(resource, "resource must not be null");
        if (locationPattern == null || locationPattern.isBlank())
        {
            throw new IllegalArgumentException("locationPattern must not be blank");
        }
        this.locationPattern = locationPattern;
        this.bytes = Objects.requireNonNull(bytes, "bytes must not be null").clone();
    }

    public Resource resource()
    {
        return resource;
    }

    public String locationPattern()
    {
        return locationPattern;
    }

    public byte[] bytes()
    {
        return bytes.clone();
    }
}
