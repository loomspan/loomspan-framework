package ai.loomspan.internal.runtime.evidence;

import tools.jackson.databind.json.JsonMapper;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceCoverageValidatorTest
{
    private final EvidenceExpressionParser parser = new EvidenceExpressionParser();
    private final EvidenceCoverageValidator validator = new EvidenceCoverageValidator();

    @Test
    void evaluatesNestedBooleanRequirementsAndExplainsUnsatisfiedClauses()
    {
        EvidenceContract contract = contract(Map.of(
                "likelyCause", "default:classify-incident and (default:investigate-network or default:investigate-app)"));

        assertThat(finalCoverage(contract, Set.of("default:classify-incident", "default:investigate-network")).complete()).isTrue();
        assertThat(finalCoverage(contract, Set.of("default:classify-incident", "default:investigate-app")).complete()).isTrue();
        assertThat(finalCoverage(contract, Set.of("default:classify-incident", "default:investigate-network", "default:investigate-app")).complete()).isTrue();
        assertThat(finalCoverage(contract, Set.of("default:classifyincident", "default:investigate-network")).complete()).isFalse();

        EvidenceCoverageIssue classificationOnly = finalCoverage(contract, Set.of("default:classify-incident")).issues().getFirst();
        assertThat(classificationOnly.requiredExpression())
                .isEqualTo("default:classify-incident and (default:investigate-network or default:investigate-app)");
        assertThat(classificationOnly.unsatisfiedRequirements()).singleElement().satisfies(all ->
        {
            assertThat(all.mode()).isEqualTo("all");
            assertThat(all.children()).singleElement().satisfies(any ->
            {
                assertThat(any.mode()).isEqualTo("any");
                assertThat(any.skills()).containsExactly("default:investigate-network", "default:investigate-app");
            });
        });

        EvidenceCoverageIssue investigatorOnly = finalCoverage(contract, Set.of("default:investigate-network")).issues().getFirst();
        assertThat(investigatorOnly.unsatisfiedRequirements()).singleElement().satisfies(all ->
                assertThat(all.children()).singleElement().satisfies(missing ->
                {
                    assertThat(missing.mode()).isEqualTo("skill");
                    assertThat(missing.skills()).containsExactly("default:classify-incident");
                }));
    }

    @Test
    void preservesConjunctiveAlternativesWithoutFlatteningAndRemainsMonotonic()
    {
        EvidenceContract contract = contract(Map.of("result", "(default:a and default:b) or (default:c and default:d)"));

        EvidenceCoverageIssue issue = validator.validateEvidenceForClaims(
                Set.of("result"), Set.of("default:a"), contract).issues().getFirst();
        EvidenceRequirement any = issue.unsatisfiedRequirements().getFirst();
        assertThat(any.mode()).isEqualTo("any");
        assertThat(any.children()).hasSize(2);
        assertThat(any.children().getFirst().mode()).isEqualTo("all");
        assertThat(any.children().getFirst().skills()).containsExactly("default:b");
        assertThat(any.children().get(1).skills()).containsExactly("default:c", "default:d");

        assertThat(validator.validateEvidenceForClaims(Set.of("result"), Set.of("default:a", "default:b"), contract).complete()).isTrue();
        assertThat(validator.validateEvidenceForClaims(Set.of("result"), Set.of("default:a", "default:b", "default:c"), contract).complete()).isTrue();
    }

    @Test
    void planningEvaluatesEveryClaimAgainstExactNonblankCapabilityNames()
    {
        EvidenceContract contract = contract(Map.of(
                "severity", "default:classify-incident",
                "likelyCause", "default:classify-incident and (default:investigate-network or default:investigate-app)",
                "optionalSummary", "default:draft-incident-response"));

        EvidenceCoverageResult missingOptional = validator.validatePlanCoverage(plan(
                "default:classify-incident", "default:investigate-network", " ", null), contract);
        assertThat(missingOptional.evaluatedClaims()).containsExactlyInAnyOrder("severity", "likelyCause", "optionalSummary");
        assertThat(missingOptional.issues()).extracting(EvidenceCoverageIssue::claimName)
                .containsExactly("optionalSummary");

        assertThat(validator.validatePlanCoverage(plan(
                "default:classify-incident", "default:investigate-network", "default:draft-incident-response"), contract).complete()).isTrue();
        assertThat(validator.validatePlanCoverage(plan(
                "default:classify-incident", "default:investigate-app", "default:draft-incident-response"), contract).complete()).isTrue();
        assertThat(validator.validatePlanCoverage(plan(
                "default:classifyincident", "default:investigate-app", "default:draft-incident-response"), contract).complete()).isFalse();
    }

    @Test
    void finalValidationEvaluatesOnlyPresentContractBackedClaims() throws Exception
    {
        EvidenceContract contract = contract(Map.of(
                "requiredResult", "default:classify-incident",
                "optionalDetail", "default:investigate-network or default:investigate-app"));
        EvidenceBackedOutputValidator outputValidator = new EvidenceBackedOutputValidator();

        EvidenceCoverageResult requiredOnly = outputValidator.validate(
                JsonMapper.builder().build().readTree("{\"requiredResult\":\"high\"}"),
                contract,
                Set.of("default:classify-incident"));
        assertThat(requiredOnly.complete()).isTrue();
        assertThat(requiredOnly.evaluatedClaims()).containsExactly("requiredResult");

        EvidenceCoverageResult unsupportedOptional = outputValidator.validate(
                JsonMapper.builder().build().readTree("{\"requiredResult\":\"high\",\"optionalDetail\":\"network\"}"),
                contract,
                Set.of("default:classify-incident"));
        assertThat(unsupportedOptional.issues()).extracting(EvidenceCoverageIssue::claimName)
                .containsExactly("optionalDetail");
        var presentNull = JsonMapper.builder().build().readTree("{\"optionalDetail\":null}");
        assertThat(outputValidator.validate(presentNull, contract, Set.of()).issues())
                .extracting(EvidenceCoverageIssue::claimName).containsExactly("optionalDetail");
        assertThat(outputValidator.validate(presentNull, contract, Set.of("default:investigate-network")).complete()).isTrue();
    }

    private EvidenceCoverageResult finalCoverage(EvidenceContract contract, Set<String> successfulSkills)
    {
        return validator.validateEvidenceForClaims(Set.of("likelyCause"), successfulSkills, contract);
    }

    private EvidenceContract contract(Map<String, String> expressions)
    {
        LinkedHashMap<String, EvidenceExpression> compiled = new LinkedHashMap<>();
        expressions.forEach((claim, expression) ->
        {
            compiled.put(claim, parser.parse(expression));
        });
        return EvidenceContract.compiled(compiled);
    }

    private static ExecutionPlan plan(String... capabilityNames)
    {
        List<PlanTask> tasks = java.util.stream.IntStream.range(0, capabilityNames.length)
                .mapToObj(index -> new PlanTask(
                        "task-" + index,
                        "Task " + index,
                        PlanTaskStatus.PENDING,
                        capabilityNames[index],
                        "Use capability",
                        List.of(),
                        List.of(),
                        null,
                        null))
                .toList();
        return new ExecutionPlan(
                "plan-1",
                "default:handle-incident",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                tasks);
    }
}
