package ai.loomspan.internal.runtime.planning;

import org.springframework.lang.Nullable;

record PlanTaskConstraintIssue(
        String code,
        String skillName,
        @Nullable Integer configuredMinTasks,
        int effectiveMinTasks,
        @Nullable Integer maxTasks,
        int actualTaskCount,
        String message)
{
}
