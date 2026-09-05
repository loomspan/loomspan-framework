package ai.loomspan.internal.core;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import org.springframework.lang.Nullable;

import java.util.Objects;

public record SkillExecutionDescriptor(
        @Nullable String frameworkModel,
        @Nullable String connection,
        @Nullable AiDriver driver,
        @Nullable String providerModel,
        @Nullable String thinkingLevel)
{
    public static SkillExecutionDescriptor none()
    {
        return new SkillExecutionDescriptor(null, null, null, null, null);
    }

    public static SkillExecutionDescriptor from(EffectiveSkillExecutionConfiguration configuration)
    {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return new SkillExecutionDescriptor(
                configuration.frameworkModel(),
                configuration.connection(),
                configuration.driver(),
                configuration.providerModel(),
                configuration.thinkingLevel());
    }

    public boolean configured()
    {
        return frameworkModel != null;
    }
}
