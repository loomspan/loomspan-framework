package ai.loomspan.internal.runtime.attachment;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;

import java.util.Map;

public interface MissionInputMaterializer
{
    RenderedMissionInput materialize(LoomspanSession session,
            YamlSkillDefinition definition,
            String objective,
            @Nullable Map<String, Object> missionInput);
}
