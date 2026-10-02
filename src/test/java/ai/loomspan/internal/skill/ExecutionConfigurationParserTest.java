package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionConfigurationParserTest
{
    @Test
    void rejectsInvalidRequestTimeoutWithSafeFullPath()
    {
        for (String value : java.util.List.of("0ms", "-1ms", "PT0.000999999S", "PT0.0015S",
                "2147483648ms", "PT9223372036854775807S", "malformed-secret-sentinel"))
            assertThatThrownBy(() -> ExecutionConfigurationParser.parse(new ExecutionConfiguration(
                    CANDIDATE.replace("driver: openai", "driver: openai\n      request-timeout: " + value)),
                    new StandardEnvironment(), false))
                    .hasMessageContaining("loomspan.connections.primary.request-timeout")
                    .hasMessageNotContaining("secret-sentinel");
    }
    @Test
    void acceptsRequestTimeoutOnSupportedConnection()
    {
        var parsed = ExecutionConfigurationParser.parse(new ExecutionConfiguration(
                CANDIDATE.replace("driver: openai", "driver: openai\n      request-timeout: 240s")),
                new StandardEnvironment(), false);
        assertThat(parsed.properties().getConnections()).containsKey("primary");
        assertThat(parsed.properties().getConnections().get("primary").getRequestTimeout())
                .isEqualTo(java.time.Duration.ofSeconds(240));
    }

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
    void explicitCandidateUsesTheSameFreshExecutionDefaultsAsFileBinding()
    {
        LoomspanProperties startupDefaults = new LoomspanProperties();
        var explicit = ExecutionConfigurationParser.parse(new ExecutionConfiguration("loomspan: {}"),
                new StandardEnvironment(), false).properties();
        assertThat(explicit.getSession().getMaxDepth()).isEqualTo(startupDefaults.getSession().getMaxDepth());
        assertThat(explicit.getSession().getMissionTimeout())
                .isEqualTo(startupDefaults.getSession().getMissionTimeout());
        assertThat(explicit.getSession().getAttachments().getMaxSize())
                .isEqualTo(startupDefaults.getSession().getAttachments().getMaxSize());
        assertThat(explicit.getSession().getQuotas().getMaxModelCalls())
                .isEqualTo(startupDefaults.getSession().getQuotas().getMaxModelCalls());
        assertThat(explicit.getExecutionTrace().getPersistence())
                .isEqualTo(startupDefaults.getExecutionTrace().getPersistence());
        assertThat(explicit.getConnections()).isEmpty();
        assertThat(explicit.getModels()).isEmpty();
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

    @Test
    void hostValuesAreCompleteAndNeverFallBackToEnvironment()
    {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("deployment",
                Map.of("provider.key", "deployment-secret", "tenant.header", "deployment-header")));
        var configuration = new ExecutionConfiguration(CANDIDATE);
        var supplied = new java.util.LinkedHashMap<>(Map.of(
                "provider.key", "host-secret", "tenant.header", "host-header"));
        var parsed = ExecutionConfigurationParser.parse(configuration, supplied);
        supplied.put("provider.key", "changed-secret");
        assertThat(parsed.properties().getConnections().get("primary").getApiKey()).isEqualTo("host-secret");
        assertThat(parsed.properties().getConnections().get("primary").getHeaders())
                .containsEntry("X-Tenant", "host-header");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(configuration,
                Map.of("tenant.header", "host-header")))
                .hasMessageContaining("api-key-ref").hasMessageNotContaining("deployment-secret")
                .hasMessageNotContaining("host-header");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(configuration,
                Map.of("provider.key", "host-secret", "tenant.header", "host-header", "unused", "sentinel")))
                .hasMessageContaining("unused reference").hasMessageNotContaining("sentinel")
                .hasMessageNotContaining("host-secret");
        supplied.put("provider.key", " ");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(configuration, supplied))
                .hasMessageContaining("api-key-ref").hasMessageNotContaining("host-header");
    }

    @Test
    void vertexJsonReferenceUsesOnlySuppliedContentAndRejectsOtherSources()
    {
        String yaml = """
                loomspan:
                  connections:
                    vertex:
                      driver: gemini
                      gemini:
                        vertex-ai: true
                        project-id: project
                        location: us-central1
                        credentials-json-ref: vertex.json
                """;
        var parsed = ExecutionConfigurationParser.parse(new ExecutionConfiguration(yaml),
                Map.of("vertex.json", "json-secret-sentinel"));
        assertThat(parsed.properties().getConnections().get("vertex").getGemini().getCredentialsJson())
                .isEqualTo("json-secret-sentinel");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse(new ExecutionConfiguration(
                yaml.replace("credentials-json-ref: vertex.json", "credentials-ref: vertex.uri\n        credentials-json-ref: vertex.json")),
                new StandardEnvironment(), false))
                .hasMessageContaining("credentials-json-ref");
    }
}
