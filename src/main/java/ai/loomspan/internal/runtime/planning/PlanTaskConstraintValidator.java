package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.skill.AllowedSkillConstraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

class PlanTaskConstraintValidator
{
    static final String MINIMUM_NOT_MET = "PLAN_TASK_MINIMUM_NOT_MET";
    static final String MAXIMUM_EXCEEDED = "PLAN_TASK_MAXIMUM_EXCEEDED";

    PlanTaskConstraintValidationResult validate(ExecutionPlan plan, List<AllowedSkillConstraint> constraints)
    {
        Objects.requireNonNull(plan, "plan must not be null");
        List<AllowedSkillConstraint> orderedConstraints = constraints == null ? List.of() : constraints;
        Map<String, Long> counts = plan.tasks().stream()
                .map(PlanTask::capabilityName)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<PlanTaskConstraintIssue> issues = new ArrayList<>();
        for (AllowedSkillConstraint constraint : orderedConstraints)
        {
            int actual = Math.toIntExact(counts.getOrDefault(constraint.name(), 0L));
            if (actual < constraint.effectiveMinTasks())
            {
                issues.add(new PlanTaskConstraintIssue(
                        MINIMUM_NOT_MET,
                        constraint.name(),
                        constraint.minTasks(),
                        constraint.effectiveMinTasks(),
                        constraint.maxTasks(),
                        actual,
                        "Skill '%s' must be used in at least %d plan task(s), but the plan uses it %d time(s)."
                                .formatted(constraint.name(), constraint.effectiveMinTasks(), actual)));
            }
            if (constraint.maxTasks() != null && actual > constraint.maxTasks())
            {
                issues.add(new PlanTaskConstraintIssue(
                        MAXIMUM_EXCEEDED,
                        constraint.name(),
                        constraint.minTasks(),
                        constraint.effectiveMinTasks(),
                        constraint.maxTasks(),
                        actual,
                        "Skill '%s' must be used in at most %d plan task(s), but the plan uses it %d time(s)."
                                .formatted(constraint.name(), constraint.maxTasks(), actual)));
            }
        }
        return new PlanTaskConstraintValidationResult(issues);
    }
}
