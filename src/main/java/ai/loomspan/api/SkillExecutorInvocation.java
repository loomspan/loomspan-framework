package ai.loomspan.api;

import ai.loomspan.internal.util.ImmutableContainers;
import java.util.Map;
import java.util.Objects;

/**
 * One executor handoff with relevant business input, skill and executor configuration,
 * and captured identities. Map/list containers are defensively copied and unmodifiable;
 * arbitrary leaves (including resource handles and their backing bytes) are not frozen.
 */
public record SkillExecutorInvocation(Map<String, Object> input,
        Map<String, Object> skillConfig,
        Map<String, Object> executorConfig,
        String skillName,
        String executorConfigGenerationId)
{
    public SkillExecutorInvocation
    {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(skillConfig, "skillConfig must not be null");
        Objects.requireNonNull(executorConfig, "executorConfig must not be null");
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(executorConfigGenerationId, "executorConfigGenerationId must not be null");
        if (skillName.isBlank()) throw new IllegalArgumentException("skillName must not be blank");
        if (executorConfigGenerationId.isBlank()) throw new IllegalArgumentException("executorConfigGenerationId must not be blank");
        input = ImmutableContainers.copyMap(input);
        skillConfig = ImmutableContainers.copyMap(skillConfig);
        executorConfig = ImmutableContainers.copyMap(executorConfig);
    }
}
