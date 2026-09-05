package ai.loomspan.internal.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ExecutionPlan(
        String planId,
        String capabilityName,
        Instant createdAt,
        PlanStatus status,
        List<PlanTask> tasks)
{
    public ExecutionPlan
    {
        planId = requireNonBlank(planId, "planId");
        capabilityName = requireNonBlank(capabilityName, "capabilityName");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        status = status == null ? PlanStatus.VALID : status;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    public ExecutionPlan(String planId, String capabilityName, Instant createdAt, List<PlanTask> tasks)
    {
        this(planId, capabilityName, createdAt, PlanStatus.VALID, tasks);
    }

    public ExecutionPlan updateTask(String taskId, Function<PlanTask, PlanTask> updater)
    {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(updater, "updater must not be null");
        return new ExecutionPlan(
                planId,
                capabilityName,
                createdAt,
                status,
                tasks.stream()
                        .map(task -> task.taskId().equals(taskId) ? Objects.requireNonNull(updater.apply(task)) : task)
                        .toList());
    }

    public Optional<PlanTask> findTask(String taskId)
    {
        Objects.requireNonNull(taskId, "taskId must not be null");
        return tasks.stream().filter(task -> task.taskId().equals(taskId)).findFirst();
    }

    public List<PlanTask> readyTasks()
    {
        Map<String, PlanTask> tasksById = tasksById();
        return tasks.stream().filter(task -> task.isReady(tasksById)).toList();
    }

    public ExecutionPlan withStatus(PlanStatus nextStatus)
    {
        return new ExecutionPlan(planId, capabilityName, createdAt, nextStatus, tasks);
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private Map<String, PlanTask> tasksById()
    {
        return tasks.stream().collect(Collectors.toMap(
                PlanTask::taskId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

}
