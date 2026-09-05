package ai.loomspan.sample.insurance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InsurancePolicyServiceTest {

    private InsurancePolicyService service;

    @BeforeEach
    void setUp() {
        service = new InsurancePolicyService();
    }

    @Test
    void clearAutoPayReturnsActiveAutoPolicy() {
        Map<String, Object> policy = service.getPolicy("clear-auto-pay", "POL-AUTO-1001");

        assertThat(policy.get("policyId")).isEqualTo("POL-AUTO-1001");
        assertThat(policy.get("productType")).isEqualTo("auto");
        assertThat(((Number) policy.get("policyLimit")).doubleValue()).isEqualTo(5000.0);
        assertThat(((Number) policy.get("deductible")).doubleValue()).isEqualTo(500.0);
        assertThat(policy.get("active")).isEqualTo(true);
    }

    @Test
    void exclusionFloodMatchesFloodExclusions() {
        Map<String, Object> exclusions = service.checkExclusions("exclusion-flood", "property", "flood water");

        @SuppressWarnings("unchecked")
        List<String> matched = (List<String>) exclusions.get("matchedExclusions");
        assertThat(matched).isNotEmpty().anyMatch(s -> s.toLowerCase().contains("flood"));
    }

    @Test
    void estimatePayoutUsesLockedFormulaForClearAutoPay() {
        // claimed 2200, limit 5000, deductible 500 → payable 1700
        Map<String, Object> estimate = service.estimatePayout("clear-auto-pay", 2200.0, "POL-AUTO-1001", null, null);

        assertThat(((Number) estimate.get("payableAmount")).doubleValue()).isEqualTo(1700.0);
        assertThat(((Number) estimate.get("gross")).doubleValue()).isEqualTo(2200.0);
        assertThat(estimate.get("excluded")).isEqualTo(false);
    }

    @Test
    void estimatePayoutCapsAtLimitThenSubtractsDeductible() {
        // claimed 25000, limit 5000, deductible 500 → gross 5000, payable 4500
        Map<String, Object> estimate = service.estimatePayout("over-limit", 25000.0, "POL-AUTO-1001", null, null);

        assertThat(((Number) estimate.get("gross")).doubleValue()).isEqualTo(5000.0);
        assertThat(((Number) estimate.get("payableAmount")).doubleValue()).isEqualTo(4500.0);
        assertThat(estimate.get("excluded")).isEqualTo(false);
    }

    @Test
    void estimatePayoutZeroesWhenFloodExcluded() {
        Map<String, Object> estimate = service.estimatePayout("exclusion-flood", 15000.0, "POL-HOME-2002", null, null);

        assertThat(((Number) estimate.get("payableAmount")).doubleValue()).isZero();
        assertThat(estimate.get("excluded")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> matched = (List<String>) estimate.get("matchedExclusions");
        assertThat(matched).isNotEmpty();
    }

    @Test
    void estimatePayoutZeroesWhenFloodKeywordsMatchWithoutScenario() {
        Map<String, Object> estimate = service.estimatePayout(
                null, 15000.0, "POL-HOME-2002", "property", "flood rising water");

        assertThat(((Number) estimate.get("payableAmount")).doubleValue()).isZero();
        assertThat(estimate.get("excluded")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> matched = (List<String>) estimate.get("matchedExclusions");
        assertThat(matched).contains("flood");

        Map<String, Object> exclusions = service.checkExclusions(null, "property", "flood rising water");
        @SuppressWarnings("unchecked")
        List<String> checkMatched = (List<String>) exclusions.get("matchedExclusions");
        assertThat(checkMatched).containsExactlyElementsOf(matched);
    }

    @Test
    void genericWaterDamageDoesNotMatchFloodExclusion() {
        Map<String, Object> exclusions = service.checkExclusions(null, "property", "water damage from burst pipe");
        @SuppressWarnings("unchecked")
        List<String> matched = (List<String>) exclusions.get("matchedExclusions");
        assertThat(matched).isEmpty();

        Map<String, Object> estimate = service.estimatePayout(
                null, 3000.0, "POL-HOME-2002", "property", "water damage from burst pipe");
        assertThat(estimate.get("excluded")).isEqualTo(false);
        assertThat(((Number) estimate.get("payableAmount")).doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void unknownScenarioReturnsNeutralValidPolicyAndEstimate() {
        String unknown = "unknown-key-xyz";

        assertThatCode(() -> {
            Map<String, Object> policy = service.getPolicy(unknown, null);
            Map<String, Object> exclusions = service.checkExclusions(unknown, null, null);
            Map<String, Object> estimate = service.estimatePayout(unknown, 1000.0, null, null, null);

            assertThat(policy).isNotEmpty();
            assertThat(((Number) policy.get("policyLimit")).doubleValue()).isEqualTo(5000.0);
            assertThat(((Number) policy.get("deductible")).doubleValue()).isEqualTo(500.0);
            assertThat(exclusions.get("matchedExclusions")).isInstanceOf(List.class);
            // max(0, min(1000, 5000) - 500) = 500
            assertThat(((Number) estimate.get("payableAmount")).doubleValue()).isEqualTo(500.0);
        }).doesNotThrowAnyException();
    }
}
