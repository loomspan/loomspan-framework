package ai.loomspan.internal.runtime.evidence;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EvidenceCoverageValidator
{
    public EvidenceCoverageResult validatePlanCoverage(ExecutionPlan plan, EvidenceContract contract)
    {
        if (plan == null || contract == null || contract.isEmpty())
        {
            return new EvidenceCoverageResult(Set.of(), Map.of(), Set.of(), List.of());
        }
        LinkedHashSet<String> plannedSkills = new LinkedHashSet<>();
        for (PlanTask task : plan.tasks())
        {
            if (task.capabilityName() != null && !task.capabilityName().isBlank())
            {
                plannedSkills.add(task.capabilityName());
            }
        }
        return coverageForClaims(contract.claims(), plannedSkills, contract, true);
    }

    public EvidenceCoverageResult validateEvidenceForClaims(Set<String> presentClaims,
            Set<String> successfulSkills,
            EvidenceContract contract)
    {
        Set<String> safeClaims = presentClaims == null ? Set.of() : new LinkedHashSet<>(presentClaims);
        Set<String> safeSkills = successfulSkills == null ? Set.of() : new LinkedHashSet<>(successfulSkills);
        if (contract == null || contract.isEmpty() || safeClaims.isEmpty())
        {
            return new EvidenceCoverageResult(safeClaims, Map.of(), safeSkills, List.of());
        }
        return coverageForClaims(safeClaims, safeSkills, contract, false);
    }

    private EvidenceCoverageResult coverageForClaims(Set<String> evaluatedClaims,
            Set<String> satisfiedSkills,
            EvidenceContract contract,
            boolean planning)
    {
        List<EvidenceCoverageIssue> issues = new ArrayList<>();
        Map<String, String> requiredExpressions = new LinkedHashMap<>();
        for (String claimName : evaluatedClaims)
        {
            EvidenceExpression expression = contract.expressionForClaim(claimName);
            if (expression == null)
            {
                continue;
            }
            requiredExpressions.put(claimName, expression.canonical());
            if (expression.evaluate(satisfiedSkills))
            {
                continue;
            }
            EvidenceRequirement requirement = expression.unsatisfiedRequirement(satisfiedSkills);
            String message = planning
                    ? "Validation Error: The plan does not satisfy expression '%s' required for the '%s' claim. Include tasks whose exact capability names satisfy this expression; already planned: %s."
                            .formatted(expression.canonical(), claimName, satisfiedSkills)
                    : "Validation Error: The '%s' claim requires successful completion of '%s' (successfully completed direct skills: %s)."
                            .formatted(claimName, expression.canonical(), satisfiedSkills);
            issues.add(new EvidenceCoverageIssue(
                    claimName,
                    expression.canonical(),
                    satisfiedSkills,
                    requirement == null ? List.of() : List.of(requirement),
                    message));
        }
        return new EvidenceCoverageResult(evaluatedClaims, requiredExpressions, satisfiedSkills, issues);
    }
}
