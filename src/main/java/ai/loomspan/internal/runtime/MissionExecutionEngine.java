package ai.loomspan.internal.runtime;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

public interface MissionExecutionEngine
{
    String executeMission(
            LoomspanSession session,
            YamlSkillDefinition definition,
            String objective,
            @Nullable Map<String, Object> missionInput,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            boolean planningEnabled,
            @Nullable Authentication authentication);
}
