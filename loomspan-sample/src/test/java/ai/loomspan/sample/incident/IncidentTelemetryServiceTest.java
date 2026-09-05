package ai.loomspan.sample.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class IncidentTelemetryServiceTest {

    private IncidentTelemetryService service;

    @BeforeEach
    void setUp() {
        service = new IncidentTelemetryService();
    }

    @Test
    void networkDnsScenarioSupportsDnsFailureSignal() {
        Map<String, Object> dns = service.checkDns("network-dns");

        assertThat(dns.get("status")).isEqualTo("FAIL");
        assertThat(dns.get("hostname")).isEqualTo("api.example.com");
        assertThat(dns.get("notes").toString()).containsIgnoringCase("EU");
    }

    @Test
    void appDeployRegressionSupportsErrorsAndDeploy() {
        Map<String, Object> errors = service.getErrorRate("app-deploy-regression");
        Map<String, Object> deploys = service.getRecentDeploys("app-deploy-regression");

        assertThat(((Number) errors.get("errorRate5xx")).doubleValue()).isGreaterThan(0.1);
        assertThat(errors.get("service")).isEqualTo("checkout");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deployList = (List<Map<String, Object>>) deploys.get("deploys");
        assertThat(deployList).isNotEmpty();
        assertThat(deployList.getFirst().get("service")).isEqualTo("checkout");
        assertThat(deployList.getFirst().get("version").toString()).contains("2.14");
    }

    @Test
    void firewallBlockSupportsDenyHits() {
        Map<String, Object> firewall = service.checkFirewallRules("firewall-block");

        assertThat(((Number) firewall.get("denyHitsLast15m")).intValue()).isGreaterThan(100);
        assertThat(firewall.get("ruleId")).isEqualTo("ACL-4421");
        assertThat(firewall.get("source").toString()).containsIgnoringCase("office");
        assertThat(firewall.get("notes").toString()).isNotBlank();
    }

    @Test
    void ambiguousSlowReturnsMixedSignalsWithoutThrowing() {
        assertThatCode(() -> {
            Map<String, Object> latency = service.checkLatency("ambiguous-slow");
            Map<String, Object> health = service.getServiceHealth("ambiguous-slow");
            Map<String, Object> deploys = service.getRecentDeploys("ambiguous-slow");

            assertThat(((Number) latency.get("p95Ms")).intValue()).isGreaterThan(1000);
            assertThat(health.get("status")).isIn("UP", "DEGRADED", "DOWN");
            List<?> deployList = (List<?>) deploys.get("deploys");
            assertThat(deployList).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void unknownScenarioReturnsNeutralValidDataForAllProbes() {
        String unknown = "unknown-key-xyz";

        assertThatCode(() -> {
            assertThat(service.checkDns(unknown)).isNotEmpty();
            assertThat(service.checkLatency(unknown)).isNotEmpty();
            assertThat(service.checkFirewallRules(unknown)).isNotEmpty();
            assertThat(service.getErrorRate(unknown)).isNotEmpty();
            assertThat(service.getRecentDeploys(unknown)).isNotEmpty();
            assertThat(service.getServiceHealth(unknown)).isNotEmpty();
            assertThat(service.lookupRunbook(unknown, null)).isNotEmpty();
        }).doesNotThrowAnyException();

        assertThat(service.checkDns(unknown).get("status")).isEqualTo("OK");
        assertThat(((Number) service.getErrorRate(unknown).get("errorRate5xx")).doubleValue()).isZero();
        List<?> deploys = (List<?>) service.getRecentDeploys(unknown).get("deploys");
        assertThat(deploys).isEmpty();
    }

    @Test
    void lookupRunbookAcceptsOptionalCategory() {
        Map<String, Object> withoutCategory = service.lookupRunbook("network-dns", null);
        Map<String, Object> withCategory = service.lookupRunbook("network-dns", "network");

        assertThat(withoutCategory.get("runbookText").toString()).isNotBlank();
        assertThat(withCategory.get("runbookText").toString()).isNotBlank();
        assertThat(withCategory.get("category")).isEqualTo("network");
    }
}
