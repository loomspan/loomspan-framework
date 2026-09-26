package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.internal.core.TracePersistencePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionConfigurationParserTest
{
    private static final String CANDIDATE = """
            loomspan:
              connections:
                primary:
                  driver: openai
                  api-key-ref: provider.key
                  header-refs:
                    X-Tenant: tenant.header
                  provider-retry:
                    max-attempts: 2
              models:
                selected:
                  connection: primary
                  provider-model: compact
              session:
                max-depth: 3
                quotas:
                  max-provider-attempts: 4
              execution-trace:
                persistence: always
            """;

    @Test
    void freezesDefaultsAndResolvesReferencesOnlyDuringPreparation()
    {
        StandardEnvironment environment = new StandardEnvironment();
        var validated = ExecutionConfigurationParser.parse(new ExecutionConfiguration(CANDIDATE), environment, false);
        assertThat(validated.tracePersistence()).isEqualTo(TracePersistencePolicy.ALWAYS);
        assertThat(validated.properties().getSession().getMaxDepth()).isEqualTo(3);
        assertThat(validated.properties().getSession().getQuotas().getMaxProviderAttempts()).isEqualTo(4);
        assertThat(validated.properties().getSession().getQuotas().getMaxToolInvocations()).isEqualTo(128);
        assertThat(validated.properties().getConnections().get("primary").getProviderRetry().getMaxAttempts()).isEqualTo(2);
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(new ExecutionConfiguration(CANDIDATE), environment, true))
                .hasMessageContaining("api-key-ref").hasMessageNotContaining("provider.key");

        environment.getPropertySources().addFirst(new MapPropertySource("credentials",
                Map.of("provider.key", "secret-sentinel", "tenant.header", "header-sentinel")));
        var prepared = ExecutionConfigurationParser.parse(new ExecutionConfiguration(CANDIDATE), environment, true);
        assertThat(prepared.properties().getConnections().get("primary").getApiKey()).isEqualTo("secret-sentinel");
        assertThat(prepared.properties().getConnections().get("primary").getHeaders()).containsEntry("X-Tenant", "header-sentinel");
        assertThat(new ExecutionConfiguration(CANDIDATE).yaml()).doesNotContain("secret-sentinel", "header-sentinel");
    }

    @Test
    void rejectsProcessSettingsDirectSecretsAndDuplicateKeys()
    {
        StandardEnvironment environment = new StandardEnvironment();
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(new ExecutionConfiguration("""
                loomspan:
                  shutdown:
                    timeout: 1s
                """), environment, false)).hasMessageContaining("loomspan.shutdown");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(new ExecutionConfiguration("""
                loomspan:
                  connections:
                    primary:
                      driver: openai
                      api-key: literal-secret-sentinel
                """), environment, false)).hasMessageContaining("loomspan.connections.primary.api-key")
                .hasMessageNotContaining("literal-secret-sentinel");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(new ExecutionConfiguration("""
                loomspan:
                  session:
                    max-depth: 2
                    max-depth: 3
                """), environment, false)).hasMessageNotContaining("max-depth: 2");
    }
}
