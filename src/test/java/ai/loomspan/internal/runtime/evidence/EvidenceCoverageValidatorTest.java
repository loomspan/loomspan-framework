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
                "likelyCause", "default-classifyIncident and (default-investigateNetwork or default-investigateApp)"));

        assertThat(finalCoverage(contract, Set.of("default-classifyIncident", "default-investigateNetwork")).complete()).isTrue();
        assertThat(finalCoverage(contract, Set.of("default-classifyIncident", "default-investigateApp")).complete()).isTrue();
        assertThat(finalCoverage(contract, Set.of("default-classifyIncident", "default-investigateNetwork", "default-investigateApp")).complete()).isTrue();
        assertThat(finalCoverage(contract, Set.of("default-classifyincident", "default-investigateNetwork")).complete()).isFalse();

        EvidenceCoverageIssue classificationOnly = finalCoverage(contract, Set.of("default-classifyIncident")).issues().getFirst();
        assertThat(classificationOnly.requiredExpression())
                .isEqualTo("default-classifyIncident and (default-investigateNetwork or default-investigateApp)");
        assertThat(classificationOnly.unsatisfiedRequirements()).singleElement().satisfies(all ->
        {
            assertThat(all.mode()).isEqualTo("all");
            assertThat(all.children()).singleElement().satisfies(any ->
            {
                assertThat(any.mode()).isEqualTo("any");
                assertThat(any.skills()).containsExactly("default-investigateNetwork", "default-investigateApp");
            });
        });

        EvidenceCoverageIssue investigatorOnly = finalCoverage(contract, Set.of("default-investigateNetwork")).issues().getFirst();
        assertThat(investigatorOnly.unsatisfiedRequirements()).singleElement().satisfies(all ->
                assertThat(all.children()).singleElement().satisfies(missing ->
                {
                    assertThat(missing.mode()).isEqualTo("skill");
                    assertThat(missing.skills()).containsExactly("default-classifyIncident");
                }));
    }

    @Test
    void preservesConjunctiveAlternativesWithoutFlatteningAndRemainsMonotonic()
    {
        EvidenceContract contract = contract(Map.of("result", "(default-a and default-b) or (default-c and default-d)"));

        EvidenceCoverageIssue issue = validator.validateEvidenceForClaims(
                Set.of("result"), Set.of("default-a"), contract).issues().getFirst();
        EvidenceRequirement any = issue.unsatisfiedRequirements().getFirst();
        assertThat(any.mode()).isEqualTo("any");
        assertThat(any.children()).hasSize(2);
        assertThat(any.children().getFirst().mode()).isEqualTo("all");
        assertThat(any.children().getFirst().skills()).containsExactly("default-b");
        assertThat(any.children().get(1).skills()).containsExactly("default-c", "default-d");

        assertThat(validator.validateEvidenceForClaims(Set.of("result"), Set.of("default-a", "default-b"), contract).complete()).isTrue();
        assertThat(validator.validateEvidenceForClaims(Set.of("result"), Set.of("default-a", "default-b", "default-c"), contract).complete()).isTrue();
    }

    @Test
    void planningEvaluatesEveryClaimAgainstExactNonblankCapabilityNames()
    {
        EvidenceContract contract = contract(Map.of(
                "severity", "default-classifyIncident",
                "likelyCause", "default-classifyIncident and (default-investigateNetwork or default-investigateApp)",
                "optionalSummary", "default-draftIncidentResponse"));

        EvidenceCoverageResult missingOptional = validator.validatePlanCoverage(plan(
                "default-classifyIncident", "default-investigateNetwork", " ", null), contract);
        assertThat(missingOptional.evaluatedClaims()).containsExactlyInAnyOrder("severity", "likelyCause", "optionalSummary");
        assertThat(missingOptional.issues()).extracting(EvidenceCoverageIssue::claimName)
                .containsExactly("optionalSummary");

        assertThat(validator.validatePlanCoverage(plan(
                "default-classifyIncident", "default-investigateNetwork", "default-draftIncidentResponse"), contract).complete()).isTrue();
        assertThat(validator.validatePlanCoverage(plan(
                "default-classifyIncident", "default-investigateApp", "default-draftIncidentResponse"), contract).complete()).isTrue();
        assertThat(validator.validatePlanCoverage(plan(
                "default-classifyincident", "default-investigateApp", "default-draftIncidentResponse"), contract).complete()).isFalse();
    }

    @Test
    void finalValidationEvaluatesOnlyPresentContractBackedClaims() throws Exception
    {
        EvidenceContract contract = contract(Map.of(
                "requiredResult", "default-classifyIncident",
                "optionalDetail", "default-investigateNetwork or default-investigateApp"));
        EvidenceBackedOutputValidator outputValidator = new EvidenceBackedOutputValidator();

        EvidenceCoverageResult requiredOnly = outputValidator.validate(
                JsonMapper.builder().build().readTree("{\"requiredResult\":\"high\"}"),
                contract,
                Set.of("default-classifyIncident"));
        assertThat(requiredOnly.complete()).isTrue();
        assertThat(requiredOnly.evaluatedClaims()).containsExactly("requiredResult");

        EvidenceCoverageResult unsupportedOptional = outputValidator.validate(
                JsonMapper.builder().build().readTree("{\"requiredResult\":\"high\",\"optionalDetail\":\"network\"}"),
                contract,
                Set.of("default-classifyIncident"));
        assertThat(unsupportedOptional.issues()).extracting(EvidenceCoverageIssue::claimName)
                .containsExactly("optionalDetail");
    }

    private EvidenceCoverageResult finalCoverage(EvidenceContract contract, Set<String> successfulSkills)
    {
        return validator.validateEvidenceForClaims(Set.of("likelyCause"), successfulSkills, contract);
    }

    private EvidenceContract contract(Map<String, String> expressions)
    {
        LinkedHashMap<String, EvidenceExpression> compiled = new LinkedHashMap<>();
        LinkedHashMap<String, String> canonical = new LinkedHashMap<>();
        expressions.forEach((claim, expression) ->
        {
            compiled.put(claim, parser.parse(expression));
            canonical.put(claim.toLowerCase(java.util.Locale.ROOT), claim);
        });
        return EvidenceContract.compiled(compiled, canonical);
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
                "default-handleIncident",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                tasks);
    }
}
