package ai.loomspan.internal.runtime.step;

import ai.loomspan.internal.core.PlanTask;

import java.util.Objects;

public record AssignedTaskExecution(PlanTask task, int stepNumber, boolean effectiveConcurrency)
{
    public AssignedTaskExecution
    {
        task = Objects.requireNonNull(task, "task must not be null");
        if (stepNumber <= 0) throw new IllegalArgumentException("stepNumber must be positive");
    }
}
