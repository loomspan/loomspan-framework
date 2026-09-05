package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PlanningService
{
    Optional<ExecutionPlan> initializePlan(
            LoomspanSession session,
            String objective,
            @Nullable Map<String, Object> missionInput,
            YamlSkillDefinition definition,
            ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools);

    Optional<String> markToolStarted(LoomspanSession session, CapabilityMetadata capability);

    Optional<ExecutionPlan> markToolCompleted(LoomspanSession session,
            String taskId,
            String capabilityName);

    Optional<ExecutionPlan> markToolFailed(LoomspanSession session, String taskId, String capabilityName, RuntimeException ex);
}
