package ai.loomspan.internal.chat;

import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.ai.chat.client.advisor.api.Advisor;

import java.util.List;

final class NoOpSkillAdvisorResolver implements SkillAdvisorResolver
{
    @Override
    public List<Advisor> resolve(YamlSkillDefinition definition)
    {
        return List.of();
    }
}
