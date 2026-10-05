package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.skill.AllowedSkillConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit edges and joined execution units enforce declaration data dependencies. */
public final class PlanInputBindingDependencyValidator {
    public List<String> validate(ExecutionPlan plan, List<AllowedSkillConstraint> constraints) {
        List<String> issues = new ArrayList<>();
        for (var constraint : constraints) {
            List<PlanTask> consumers = plan.tasks().stream().filter(task -> constraint.name().equals(task.capabilityName())).toList();
            if (consumers.isEmpty()) continue;
            var producers = constraint.inputBindings().stream().filter(binding -> binding.sourceKind() == ChildInputBinding.SourceKind.CHILD_RESULT)
                    .map(ChildInputBinding::skill).distinct().toList();
            for (String producerName : producers) {
                var tasks = plan.tasks().stream().filter(task -> producerName.equals(task.capabilityName())).toList();
                if (tasks.size() != 1) {
                    issues.add("Input binding dependency: child '" + constraint.name() + "' requires exactly one producer task for '" + producerName + "'; found " + tasks.size());
                    continue;
                }
                PlanTask producer = tasks.getFirst();
                for (PlanTask consumer : consumers) {
                    if (!consumer.dependsOn().contains(producer.taskId()))
                        issues.add("Input binding dependency: consumer task '" + consumer.taskId() + "' must explicitly depend on producer task '" + producer.taskId() + "'");
                    if (plan.tasks().indexOf(producer) >= plan.tasks().indexOf(consumer)
                            || producer.parallelGroup() != null && Objects.equals(producer.parallelGroup(), consumer.parallelGroup()))
                        issues.add("Input binding dependency: producer task '" + producer.taskId() + "' must occupy an earlier execution unit than consumer '" + consumer.taskId() + "'");
                }
            }
        }
        return List.copyOf(issues);
    }
    public static String render(List<AllowedSkillConstraint> constraints) {
        List<String> lines = new ArrayList<>();
        for (var constraint : constraints) {
            var producers = constraint.inputBindings().stream().filter(binding -> binding.sourceKind() == ChildInputBinding.SourceKind.CHILD_RESULT)
                    .map(ChildInputBinding::skill).distinct().toList();
            for (String producer : producers)
                lines.add("- If '" + constraint.name() + "' is planned, include exactly one '" + producer + "' task in an earlier execution unit and explicitly include its taskId in each consumer dependsOn. They cannot share a parallelGroup.");
            if (!constraint.inputBindings().isEmpty())
                lines.add("- Child '" + constraint.name() + "' receives author-bound inputs directly from Framework. Generate only inputs permitted by its effective argument schema; never reproduce bound evidence.");
        }
        return lines.isEmpty() ? "" : "\nDeclared child input dependencies:\n" + String.join("\n", lines) + "\n";
    }
}
