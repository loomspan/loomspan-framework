package ai.loomspan.internal.chat;

import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.ai.chat.client.advisor.api.Advisor;

import java.util.List;

public interface SkillAdvisorResolver
{
    List<Advisor> resolve(YamlSkillDefinition definition);
}
