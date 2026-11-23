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
    void publishedTraceConfigurationRejectsHostDirectory() {
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("finance", new ExecutionConfiguration(
                "loomspan: {execution-trace: {persistence: always, directory: redirected}}"), Map.of()))
                .hasMessageContaining("loomspan.execution-trace.directory").hasMessageContaining("not publishable");
    }

    @Test void markedTypedSettingsKeepNativeConversionAndOwningValidation() {
        String yaml = """
                loomspan:
                  connections:
                    primary:
                      driver: {$var: driver}
                      api-key: {$var: key}
                      request-timeout: {$var: timeout}
                      provider-retry: {max-attempts: {$var: attempts}, initial-backoff: {$var: initial}}
                """;
        var values = new java.util.LinkedHashMap<>(Map.of("driver", "openai", "key", "${region}",
                "timeout", "123ms", "attempts", "2", "initial", "1ms"));
        assertThat(ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), new StandardEnvironment(), false)
                .properties().getConnections().get("primary").getDriver()).isNull();
        var connection = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), values)
                .properties().getConnections().get("primary");
        assertThat(connection.getRequestTimeout()).isEqualTo(java.time.Duration.ofMillis(123));
        assertThat(connection.getProviderRetry().getMaxAttempts()).isEqualTo(2);
        assertThat(connection.getApiKey()).isEqualTo("${region}");
        for (String[] invalid : java.util.List.of(new String[]{"driver", "absent", "driver"},
                new String[]{"timeout", "0ms", "request-timeout"}, new String[]{"attempts", "0", "max-attempts"},
                new String[]{"initial", "-1ms", "initial-backoff"})) {
            var bad = new java.util.LinkedHashMap<>(values);
            bad.put(invalid[0], invalid[1]);
            assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), bad))
                    .hasMessageContaining("loomspan.connections.primary").hasMessageContaining(invalid[2]);
        }
    }

    @Test void vertexJsonMarkersKeepMultilineContentsAndBooleanMode() {
        String yaml = "loomspan: {connections: {vertex: {driver: gemini, gemini: {vertex-ai: {$var: enabled}, project-id: p, location: west, credentials-json: {$var: json}}}}}";
        String json = "{\n  \"type\": \"service_account\",\n  \"other\": \"${literal}\"\n}\n";
        var values = Map.of("enabled", "true", "json", json);
        var marked = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), values);
        assertThat(marked.properties().getConnections().get("vertex").getGemini().getCredentialsJson()).isEqualTo(json);
        assertThat(marked.properties().getConnections().get("vertex").getGemini().getVertexAi()).isTrue();
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml),
                Map.of("enabled", "invalid", "json", json))).hasMessageContaining("gemini.vertex-ai");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml),
                Map.of("enabled", "false", "json", json))).hasMessageContaining("exactly one");
    }

    @Test void dynamicKeysDeferTheirOwnChecksWhileLiteralSiblingsRemainValidated() {
        for (String field : java.util.List.of("request-timeout: 0ms", "provider-retry: {max-attempts: 0}",
                "headers: {{$var: name}: {nested: invalid}}")) {
            String yaml = "loomspan:\n  connections:\n    api:\n      driver: openai\n      api-key: {$var: key}\n"
                    + "      ? {$var: field}\n      : {$var: value}\n" + field.indent(6);
            assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), new StandardEnvironment(), false))
                    .as(field).hasMessageContaining("loomspan.connections.api");
        }
    }

    @Test void effectiveProviderFieldNamesUseAuthoredLocations() {
        var configuration = new ExecutionConfiguration("loomspan: {connections: {primary: {driver: openai, api-key: demo, {$var: field}: {$var: value}}}}");
        var parsed = ExecutionConfigurationParser.parse("default", configuration, Map.of("field", "request-timeout", "value", "123ms"));
        assertThat(parsed.properties().getConnections().get("primary").getRequestTimeout()).isEqualTo(java.time.Duration.ofMillis(123));
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", configuration,
                Map.of("field", "not-publishable-private-sentinel", "value", "private-value")))
                .hasMessageContaining("<configuration>").hasMessageContaining("primary{key#2}")
                .hasMessageContaining("is not publishable").hasMessageNotContaining("private-sentinel").hasMessageNotContaining("private-value");
    }

    @Test void markedNonblankSettingsRetainDecidableAuthenticationRules() {
        for (String fields : java.util.List.of("api-key: {$var: key}, gemini: {vertex-ai: true, project-id: p, location: west}",
                "api-key: {$var: key}, gemini: {}", "base-url: {$var: url}, gemini: {vertex-ai: true, project-id: p, location: west}",
                "api-key: demo, headers: {X-Region: {$var: region}}")) {
            var configuration = new ExecutionConfiguration("loomspan: {connections: {primary: {driver: gemini, " + fields + "}}}");
            assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", configuration, new StandardEnvironment(), false))
                    .as(fields).hasMessageContaining("loomspan.connections.primary");
        }
    }

    @Test
    void literalProviderSettingsPrepareWithEmptyValues()
    {
        var parsed = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                CANDIDATE.replace("api-key: {$var: provider.key}", "api-key: demo-key")
                        .replace("headers:", "headers:").replace("{$var: tenant.header}", "west")), Map.of());
        var connection = parsed.properties().getConnections().get("primary");
        assertThat(connection.getApiKey()).isEqualTo("demo-key");
        assertThat(connection.getHeaders()).containsEntry("X-Tenant", "west");
    }

    @Test
    void literalHeadersPreserveNamesAndConvertScalarValuesAtProviderValidation()
    {
        for (String driver : java.util.List.of("openai", "anthropic"))
        {
            var parsed = literal(driver, "headers: {X-Region: west, X-Count: 3, X-Enabled: true, X!Token: exact}");
            assertThat(parsed.properties().getConnections().get("primary").getHeaders())
                    .containsEntry("X-Count", "3").containsEntry("X-Enabled", "true")
                    .containsEntry("X!Token", "exact");
        }
        for (String name : java.util.List.of("", " ", "X Bad", "X[Bad]", "X/Bad"))
            assertThatThrownBy(() -> literal("openai", "headers: {'" + name + "': value}"))
                    .hasMessageContaining("headers").hasMessageContaining("invalid HTTP header name");
        assertThatThrownBy(() -> literal("openai", "headers: {X-Null: null}"))
                .hasMessageContaining("headers").hasMessageContaining("null");
        assertThatThrownBy(() -> literal("openai", "headers: {X-Nested: {a: b}}"))
                .hasMessageContaining("headers.X-Nested");
        assertThatThrownBy(() -> literal("gemini", "headers: {X-Region: west}"))
                .hasMessageContaining("only supported for drivers OPENAI and ANTHROPIC");
        assertThat(literal("gemini", "").properties().getConnections().get("primary").getApiKey())
                .isEqualTo("demo-key");
    }

    @Test
    void rejectsRetiredProviderFields() {
        for (String field : java.util.List.of("api-key-ref: key.ref", "header-refs: {}",
                "gemini: {credentials-json-ref: json.ref}"))
            assertThatThrownBy(() -> literal("openai", field)).hasMessageContaining("is not publishable");
    }

    @Test
    void literalGeminiOptionsKeepModeAndTypedValidation()
    {
        String vertex = "loomspan: {connections: {primary: {driver: gemini, gemini: {vertex-ai: true, project-id: project, location: west, credentials-json: '{}'}}}}";
        assertThat(ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(vertex), Map.of())
                .properties().getConnections().get("primary").getGemini().getCredentialsJson()).isEqualTo("{}");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                vertex.replace("driver: gemini,", "driver: gemini, api-key: demo-key,")), Map.of()))
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                vertex.replace("project-id: project, ", "")), Map.of()))
                .hasMessageContaining("gemini.project-id");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                vertex.replace("vertex-ai: true", "vertex-ai: malformed")), Map.of()))
                .hasMessageContaining("gemini.vertex-ai");
        assertThatThrownBy(() -> literal("openai", "gemini: {vertex-ai: true}"))
                .hasMessageContaining("only supported for driver GEMINI");
        assertThatThrownBy(() -> literal("openai", "request-timeout: 0ms"))
                .hasMessageContaining("request-timeout");
    }

    @Test
    void retiredCredentialSyntaxRejectsBeforeAnyResourceAccess() throws Exception
    {
        try (var server = new okhttp3.mockwebserver.MockWebServer())
        {
            server.start();
            for (String uri : java.util.List.of("file:unread-credential.json", server.url("/credentials").toString()))
                for (String field : java.util.List.of("credentials-ref", "credentials-uri"))
                {
                    String yaml = "loomspan: {connections: {vertex: {driver: gemini, gemini: {vertex-ai: true, project-id: p, location: west, "
                            + field + ": 'vertex.uri'}}}}";
                    assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), Map.of("vertex.uri", uri)))
                            .hasMessageContaining("gemini." + field + " is not publishable");
                    assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), new StandardEnvironment(), false))
                            .hasMessageContaining("gemini." + field + " is not publishable");
                }
            assertThat(server.getRequestCount()).isZero();
        }
    }

    private static ExecutionConfigurationParser.Parsed literal(String driver, String fields)
    {
        String yaml = "loomspan:\n  connections:\n    primary:\n      driver: " + driver + "\n      api-key: demo-key\n"
                + fields.indent(6);
        // A source-conflict case supplies its own API key.
        if (fields.contains("api-key:")) yaml = yaml.replace("      api-key: demo-key\n", "");
        return ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml), Map.of());
    }

    @Test void rejectsRemovedTypeSafeDriver() {
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                CANDIDATE.replace("driver: openai", "driver: typesafe")), new StandardEnvironment(), false))
                .hasMessageContaining("loomspan.connections.primary.driver");
    }

    @Test
    void candidateCapturesAccountReferencesAndProtocolOptions() {
        String yaml = CANDIDATE.replace("driver: openai", "driver: openai\n      base-url: https://openrouter.ai/api/\n      request-timeout: 240ms");
        var parsed = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml),
                Map.of("provider.key", "fixture-native-key", "tenant.header", "fixture-header"));
        var connection = parsed.properties().getConnections().get("primary");
        assertThat(connection.getDriver()).isEqualTo(ai.loomspan.autoconfigure.AiDriver.OPENAI);
        assertThat(connection.getBaseUrl()).isEqualTo("https://openrouter.ai/api/");
        assertThat(connection.getRequestTimeout()).isEqualTo(java.time.Duration.ofMillis(240));
        assertThat(connection.getApiKey()).isEqualTo("fixture-native-key");
        assertThat(connection.getHeaders()).containsEntry("X-Tenant", "fixture-header");
        assertThat(parsed.properties().getModels().get("selected").getProviderModel()).isEqualTo("compact");
    }

    @Test
    void rejectsInvalidRequestTimeoutWithSafeFullPath()
    {
        for (String value : java.util.List.of("0ms", "-1ms", "PT0.000999999S", "PT0.0015S",
                "2147483648ms", "PT9223372036854775807S", "malformed-secret-sentinel"))
            assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                    CANDIDATE.replace("driver: openai", "driver: openai\n      request-timeout: " + value)),
                    new StandardEnvironment(), false))
                    .hasMessageContaining("loomspan.connections.primary.request-timeout")
                    .hasMessageNotContaining("secret-sentinel");
    }
    @Test
    void acceptsRequestTimeoutOnSupportedConnection()
    {
        var parsed = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                CANDIDATE.replace("driver: openai", "driver: openai\n      request-timeout: 240s")),
                new StandardEnvironment(), false);
        assertThat(parsed.properties().getConnections()).containsKey("primary");
        assertThat(parsed.properties().getConnections().get("primary").getRequestTimeout())
                .isEqualTo(java.time.Duration.ofSeconds(240));
    }

    @Test
    void rejectsRemovedNativeDriverAndCompatibilityProfile()
    {
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                CANDIDATE.replace("driver: openai", "driver: ollama")), new StandardEnvironment(), false))
                .hasMessageContaining("loomspan.connections.primary.driver");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                CANDIDATE.replace("driver: openai", "driver: openai\n      openai:\n        compatibility-profile: openrouter")),
                new StandardEnvironment(), false))
                .hasMessageContaining("loomspan.connections.primary.openai.compatibility-profile");
    }

    private static final String CANDIDATE = """
            loomspan:
              connections:
                primary:
                  driver: openai
                  api-key: {$var: provider.key}
                  headers:
                    X-Tenant: {$var: tenant.header}
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
        var validated = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(CANDIDATE), environment, false);
        assertThat(validated.tracePersistence()).isEqualTo(TracePersistencePolicy.ALWAYS);
        assertThat(validated.properties().getSession().getMaxDepth()).isEqualTo(3);
        assertThat(validated.properties().getSession().getQuotas().getMaxProviderAttempts()).isEqualTo(4);
        assertThat(validated.properties().getSession().getQuotas().getMaxToolInvocations()).isEqualTo(128);
        assertThat(validated.properties().getConnections().get("primary").getProviderRetry().getMaxAttempts()).isEqualTo(2);
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(CANDIDATE), environment, true))
                .hasMessageContaining("api-key").hasMessageNotContaining("provider.key");

        environment.getPropertySources().addFirst(new MapPropertySource("credentials",
                Map.of("provider.key", "secret-sentinel", "tenant.header", "header-sentinel")));
        var prepared = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(CANDIDATE), environment, true);
        assertThat(prepared.properties().getConnections().get("primary").getApiKey()).isEqualTo("secret-sentinel");
        assertThat(prepared.properties().getConnections().get("primary").getHeaders()).containsEntry("X-Tenant", "header-sentinel");
        assertThat(new ExecutionConfiguration(CANDIDATE).yaml()).doesNotContain("secret-sentinel", "header-sentinel");
    }

    @Test
    void explicitCandidateUsesTheSameFreshExecutionDefaultsAsFileBinding()
    {
        LoomspanProperties startupDefaults = new LoomspanProperties();
        var explicit = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration("loomspan: {}"),
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
    void rejectsProcessSettingsAndDuplicateKeys()
    {
        StandardEnvironment environment = new StandardEnvironment();
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration("""
                loomspan:
                  shutdown:
                    timeout: 1s
                """), environment, false)).hasMessageContaining("loomspan.shutdown");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration("""
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
        var parsed = ExecutionConfigurationParser.parse("default", configuration, supplied);
        supplied.put("provider.key", "changed-secret");
        assertThat(parsed.properties().getConnections().get("primary").getApiKey()).isEqualTo("host-secret");
        assertThat(parsed.properties().getConnections().get("primary").getHeaders())
                .containsEntry("X-Tenant", "host-header");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", configuration,
                Map.of("tenant.header", "host-header")))
                .hasMessageContaining("api-key").hasMessageNotContaining("deployment-secret")
                .hasMessageNotContaining("host-header");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", configuration,
                Map.of("provider.key", "host-secret", "tenant.header", "host-header", "unused", "sentinel")))
                .hasMessageContaining("unused entry").hasMessageNotContaining("sentinel")
                .hasMessageNotContaining("host-secret");
        supplied.put("provider.key", " ");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", configuration, supplied))
                .hasMessageContaining("api-key").hasMessageNotContaining("host-header");
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
                        credentials-json: {$var: vertex.json}
                """;
        var parsed = ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(yaml),
                Map.of("vertex.json", "json-secret-sentinel"));
        assertThat(parsed.properties().getConnections().get("vertex").getGemini().getCredentialsJson())
                .isEqualTo("json-secret-sentinel");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("default", new ExecutionConfiguration(
                yaml.replace("credentials-json: {$var: vertex.json}", "credentials-ref: vertex.uri\n        credentials-json: {$var: vertex.json}")),
                new StandardEnvironment(), false))
                .hasMessageContaining("credentials-ref");
    }
}
