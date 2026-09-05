package ai.loomspan.internal.runtime.evidence;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record EvidenceCoverageIssue(
        String claimName,
        String requiredExpression,
        Set<String> satisfiedSkills,
        List<EvidenceRequirement> unsatisfiedRequirements,
        String message)
{
    public EvidenceCoverageIssue
    {
        satisfiedSkills = satisfiedSkills == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(satisfiedSkills));
        unsatisfiedRequirements = unsatisfiedRequirements == null ? List.of() : List.copyOf(unsatisfiedRequirements);
    }
}
