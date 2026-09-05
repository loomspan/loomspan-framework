package ai.loomspan.internal.core;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PlanTask(
        String taskId,
        String title,
        PlanTaskStatus status,
        @Nullable String capabilityName,
        @Nullable String intent,
        List<String> dependsOn,
        List<String> expectedOutputs,
        @Nullable String parallelGroup,
        @Nullable String note)
{

    public PlanTask
    {
        taskId = requireNonBlank(taskId, "taskId");
        title = requireNonBlank(title, "title");
        status = Objects.requireNonNull(status, "status must not be null");
        capabilityName = normalizeNullable(capabilityName);
        intent = normalizeNullable(intent);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        expectedOutputs = expectedOutputs == null ? List.of() : List.copyOf(expectedOutputs);
        note = normalizeNullable(note);
    }

    public PlanTask(String taskId, String title, PlanTaskStatus status, @Nullable String note)
    {
        this(taskId, title, status, null, null, List.of(), List.of(), null, note);
    }

    public PlanTask withStatus(PlanTaskStatus nextStatus, @Nullable String nextNote)
    {
        return new PlanTask(taskId, title, nextStatus, capabilityName, intent, dependsOn, expectedOutputs, parallelGroup, nextNote);
    }

    public PlanTask bindInProgress(@Nullable String nextNote)
    {
        return withStatus(PlanTaskStatus.IN_PROGRESS, nextNote);
    }

    public PlanTask complete(@Nullable String nextNote)
    {
        return withStatus(PlanTaskStatus.COMPLETED, nextNote);
    }

    public PlanTask fail(@Nullable String nextNote)
    {
        return withStatus(PlanTaskStatus.FAILED, nextNote);
    }

    public boolean isReady(Map<String, PlanTask> tasksById)
    {
        if (status != PlanTaskStatus.PENDING)
        {
            return false;
        }
        return dependsOn.stream().allMatch(dependencyId ->
        {
            PlanTask dependency = tasksById.get(dependencyId);
            return dependency != null && dependency.status() == PlanTaskStatus.COMPLETED;
        });
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

    private static String normalizeNullable(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return value;
    }
}
