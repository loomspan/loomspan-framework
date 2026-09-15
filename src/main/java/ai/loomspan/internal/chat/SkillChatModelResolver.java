package ai.loomspan.internal.chat;

import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;

public interface SkillChatModelResolver
{
    ProviderConnectionRuntime resolve(String skillName, EffectiveSkillExecutionConfiguration configuration);
}
