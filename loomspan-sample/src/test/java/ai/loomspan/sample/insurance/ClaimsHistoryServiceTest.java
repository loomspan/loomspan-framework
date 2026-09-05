package ai.loomspan.sample.insurance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ClaimsHistoryServiceTest {

    private ClaimsHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ClaimsHistoryService();
    }

    @Test
    void clearAutoPayHasCleanHistoryAndLowAnomaly() {
        Map<String, Object> history = service.priorClaimsLookup("clear-auto-pay");
        Map<String, Object> anomaly = service.anomalyScore("clear-auto-pay");
        Map<String, Object> address = service.addressRiskSignals("clear-auto-pay");

        assertThat(((Number) history.get("priorClaimsCount")).intValue()).isZero();
        assertThat(((Number) anomaly.get("anomalyScore")).doubleValue()).isLessThan(0.2);
        @SuppressWarnings("unchecked")
        List<String> signals = (List<String>) address.get("riskSignals");
        assertThat(signals).isEmpty();
    }

    @Test
    void fraudVelocityElevatesAnomalyPriorsAndAddressFlags() {
        Map<String, Object> history = service.priorClaimsLookup("fraud-velocity");
        Map<String, Object> anomaly = service.anomalyScore("fraud-velocity");
        Map<String, Object> address = service.addressRiskSignals("fraud-velocity");

        assertThat(((Number) history.get("priorClaimsCount")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(((Number) history.get("claimsLast60Days")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(((Number) anomaly.get("anomalyScore")).doubleValue()).isGreaterThan(0.8);
        assertThat(anomaly.get("fraudRiskHint")).isEqualTo("high");
        @SuppressWarnings("unchecked")
        List<String> signals = (List<String>) address.get("riskSignals");
        assertThat(signals).isNotEmpty();
    }

    @Test
    void overLimitKeepsFraudClean() {
        Map<String, Object> history = service.priorClaimsLookup("over-limit");
        Map<String, Object> anomaly = service.anomalyScore("over-limit");

        assertThat(((Number) history.get("priorClaimsCount")).intValue()).isZero();
        assertThat(((Number) anomaly.get("anomalyScore")).doubleValue()).isLessThan(0.3);
    }

    @Test
    void unknownScenarioReturnsNeutralValidStructures() {
        String unknown = "unknown-key-xyz";

        assertThatCode(() -> {
            Map<String, Object> history = service.priorClaimsLookup(unknown);
            Map<String, Object> anomaly = service.anomalyScore(unknown);
            Map<String, Object> address = service.addressRiskSignals(unknown);

            assertThat(history).isNotEmpty();
            assertThat(((Number) history.get("priorClaimsCount")).intValue()).isZero();
            assertThat(((Number) anomaly.get("anomalyScore")).doubleValue()).isBetween(0.0, 1.0);
            @SuppressWarnings("unchecked")
            List<String> signals = (List<String>) address.get("riskSignals");
            assertThat(signals).isEmpty();
        }).doesNotThrowAnyException();
    }
}
