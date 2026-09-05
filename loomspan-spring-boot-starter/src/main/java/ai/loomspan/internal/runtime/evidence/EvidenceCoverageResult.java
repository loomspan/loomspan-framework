package ai.loomspan.internal.runtime.evidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record EvidenceCoverageResult(
        Set<String> evaluatedClaims,
        Map<String, String> requiredExpressions,
        Set<String> satisfiedSkills,
        List<EvidenceCoverageIssue> issues)
{
    public EvidenceCoverageResult
    {
        evaluatedClaims = evaluatedClaims == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(evaluatedClaims));
        requiredExpressions = requiredExpressions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(requiredExpressions));
        satisfiedSkills = satisfiedSkills == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(satisfiedSkills));
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean complete()
    {
        return issues.isEmpty();
    }

    public String retryFeedback()
    {
        return complete() ? "" : issues.stream().map(EvidenceCoverageIssue::message).collect(Collectors.joining("\n"));
    }
}
