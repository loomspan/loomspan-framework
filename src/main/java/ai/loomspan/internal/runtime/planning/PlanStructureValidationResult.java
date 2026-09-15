package ai.loomspan.internal.runtime.planning;

import java.util.List;

record PlanStructureValidationResult(List<PlanStructureIssue> issues)
{
    PlanStructureValidationResult
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    boolean hasErrors()
    {
        return !issues.isEmpty();
    }

    String retryFeedback()
    {
        return issues.stream()
                .map(issue -> "- [%s] %s".formatted(issue.code(), issue.message()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
