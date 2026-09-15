package ai.loomspan.internal.runtime.step;

import ai.loomspan.internal.core.PlanTask;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record ExecutionUnit(List<PlanTask> members, @Nullable String parallelGroup)
{
    ExecutionUnit
    {
        members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
        if (members.isEmpty()) throw new IllegalArgumentException("members must not be empty");
    }

    static List<ExecutionUnit> partition(List<PlanTask> tasks)
    {
        List<PlanTask> acceptedTasks = List.copyOf(Objects.requireNonNull(tasks, "tasks must not be null"));
        List<ExecutionUnit> units = new ArrayList<>();
        for (int index = 0; index < acceptedTasks.size();)
        {
            PlanTask first = Objects.requireNonNull(acceptedTasks.get(index), "task must not be null");
            String group = first.parallelGroup();
            if (group == null)
            {
                units.add(new ExecutionUnit(List.of(first), null));
                index++;
                continue;
            }

            int end = index + 1;
            while (end < acceptedTasks.size() && group.equals(acceptedTasks.get(end).parallelGroup())) end++;
            units.add(new ExecutionUnit(acceptedTasks.subList(index, end), group));
            index = end;
        }
        return List.copyOf(units);
    }
}
