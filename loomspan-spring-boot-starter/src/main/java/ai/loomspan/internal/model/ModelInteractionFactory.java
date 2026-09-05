package ai.loomspan.internal.model;

import ai.loomspan.internal.skill.YamlSkillDefinition;

public interface ModelInteractionFactory
{
    ModelInteraction create(YamlSkillDefinition definition, ModelInteractionMode mode);
}
