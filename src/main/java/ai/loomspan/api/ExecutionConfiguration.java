package ai.loomspan.api;

import java.util.Objects;

/** Authored, reference-only YAML for a complete publishable execution configuration. */
public record ExecutionConfiguration(String yaml)
{
    public ExecutionConfiguration
    {
        Objects.requireNonNull(yaml, "yaml must not be null");
        if (yaml.length() > 1_000_000) throw new IllegalArgumentException("execution configuration is too large");
    }
}
