package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.AiDriver;
import org.springframework.lang.Nullable;

public record EffectiveSkillExecutionConfiguration(
                String frameworkModel,
                String connection,
                AiDriver driver,
                String providerModel,
                @Nullable String thinkingLevel)
{
}
