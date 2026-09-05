package ai.loomspan.internal.runtime.planning;

import java.util.List;

record PlanTaskConstraintValidationResult(List<PlanTaskConstraintIssue> issues)
{
    PlanTaskConstraintValidationResult
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    boolean hasErrors()
    {
        return !issues.isEmpty();
    }

    String retryFeedback()
    {
        return issues.stream().map(issue -> "- " + issue.message()).reduce((left, right) -> left + "\n" + right).orElse("");
    }
}
