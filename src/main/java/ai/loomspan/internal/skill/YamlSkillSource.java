package ai.loomspan.internal.skill;

import org.springframework.core.io.Resource;

import java.util.Objects;

public final class YamlSkillSource
{
    private final Resource resource;
    private final String locationPattern;
    private final byte[] bytes;
    private final String suppliedLabel;

    public YamlSkillSource(Resource resource, String locationPattern, byte[] bytes)
    {
        this.resource = Objects.requireNonNull(resource, "resource must not be null");
        if (locationPattern == null || locationPattern.isBlank())
        {
            throw new IllegalArgumentException("locationPattern must not be blank");
        }
        this.locationPattern = locationPattern;
        this.bytes = Objects.requireNonNull(bytes, "bytes must not be null").clone();
        this.suppliedLabel = null;
    }

    public YamlSkillSource(Resource resource, byte[] bytes, String suppliedLabel)
    {
        this.resource = Objects.requireNonNull(resource, "resource must not be null");
        this.locationPattern = null;
        this.bytes = Objects.requireNonNull(bytes, "bytes must not be null").clone();
        this.suppliedLabel = Objects.requireNonNull(suppliedLabel, "suppliedLabel must not be null");
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

    public String suppliedLabel()
    {
        return suppliedLabel;
    }

    public String diagnosticName()
    {
        return suppliedLabel == null ? resource.getDescription() : suppliedLabel;
    }
}
