package ai.loomspan.internal.autoconfigure;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.provider.AttemptOwnership;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.provider.ProviderFailureDetails;
import ai.loomspan.internal.provider.ProviderRetryPolicy;
import ai.loomspan.internal.springai.SpringAiProviderIntegration;
import ai.loomspan.internal.typesafe.TypeSafeClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.DisposableBean;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.doThrow;

class NamedAiConnectionRegistryTests {

    @Test
    void constructsAndKeepsDistinctNamedConnectionsUsingTheSameDriver() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel secondary = mock(ChatModel.class);
        SpringAiProviderIntegration integration = mock(SpringAiProviderIntegration.class);

        LoomspanProperties.ConnectionProperties first = connection("http://one.example");
        LoomspanProperties.ConnectionProperties second = connection("http://two.example");
        ProviderConnectionRuntime firstRuntime = runtime(primary);
        ProviderConnectionRuntime secondRuntime = runtime(secondary);
        when(integration.create("primary", first)).thenReturn(firstRuntime);
        when(integration.create("secondary", second)).thenReturn(secondRuntime);
        NamedAiConnectionRegistry registry = new NamedAiConnectionRegistry(
                Map.of("primary", first, "secondary", second), integration);

        assertThat(registry.asMap()).containsEntry("primary", firstRuntime).containsEntry("secondary", secondRuntime);
        assertThat(registry.get("primary")).isNotSameAs(registry.get("secondary"));
    }

    @Test
    void preservesSafeFieldDiagnosticsAndCleansUpModelsBuiltBeforeFailure() throws Exception {
        ChatModel closeable = mock(ChatModel.class, withSettings().extraInterfaces(DisposableBean.class));
        SpringAiProviderIntegration integration = mock(SpringAiProviderIntegration.class);

        LinkedHashMap<String, LoomspanProperties.ConnectionProperties> connections = new LinkedHashMap<>();
        connections.put("first", geminiConnection());
        connections.put("second", geminiConnection());
        when(integration.create("first", connections.get("first"))).thenReturn(runtime(closeable));
        when(integration.create("second", connections.get("second"))).thenThrow(new SafeAiConnectionConfigurationException(
                "loomspan.connections.second.gemini.credentials-uri could not be loaded"));

        assertThatThrownBy(() -> new NamedAiConnectionRegistry(connections, integration))
                .isInstanceOf(SafeAiConnectionConfigurationException.class)
                .hasMessage("loomspan.connections.second.gemini.credentials-uri could not be loaded");
        verify((DisposableBean) closeable).destroy();
    }

    @Test
    void opaqueClientRetryOwnershipRequiresLoomspanRetryToBeDisabled() {
        ChatModel model = mock(ChatModel.class, withSettings().extraInterfaces(DisposableBean.class));
        SpringAiProviderIntegration integration = mock(SpringAiProviderIntegration.class);
        LoomspanProperties.ConnectionProperties properties = connection("http://opaque.example");
        ProviderConnectionRuntime opaque = ProviderConnectionRuntime.chat(model, AiDriver.OLLAMA,
                AttemptOwnership.OPAQUE_CLIENT_RETRIES,
                ProviderRetryPolicy.from(properties.getProviderRetry()), ignored -> ProviderFailureDetails.unknown());
        when(integration.create("opaque", properties)).thenReturn(opaque);

        assertThatThrownBy(() -> new NamedAiConnectionRegistry(Map.of("opaque", properties), integration))
                .isInstanceOf(SafeAiConnectionConfigurationException.class)
                .hasMessageContaining("loomspan.connections.opaque.provider-retry.enabled")
                .hasMessageNotContaining("opaque.example");
        try { verify((DisposableBean) model).destroy(); }
        catch (Exception ex) { throw new AssertionError(ex); }

        properties.getProviderRetry().setEnabled(false);
        ProviderConnectionRuntime disabled = ProviderConnectionRuntime.chat(model, AiDriver.OLLAMA,
                AttemptOwnership.OPAQUE_CLIENT_RETRIES,
                ProviderRetryPolicy.from(properties.getProviderRetry()), ignored -> ProviderFailureDetails.unknown());
        when(integration.create("opaque", properties)).thenReturn(disabled);
        assertThat(new NamedAiConnectionRegistry(Map.of("opaque", properties), integration).get("opaque"))
                .isSameAs(disabled);
    }

    @Test
    void genericGeminiConstructionFailureNamesOnlyApplicableSettings() {
        SpringAiProviderIntegration integration = mock(SpringAiProviderIntegration.class);
        LoomspanProperties.ConnectionProperties properties = geminiConnection();
        var original = new IllegalArgumentException("original construction detail");
        when(integration.create("gemini", properties)).thenThrow(original);

        assertThatThrownBy(() -> new NamedAiConnectionRegistry(Map.of("gemini", properties), integration))
                .hasMessageContaining("loomspan.connections.gemini.api-key")
                .hasMessageContaining("loomspan.connections.gemini.gemini.vertex-ai")
                .hasMessageContaining("loomspan.connections.gemini.gemini.project-id")
                .hasMessageContaining("loomspan.connections.gemini.gemini.location")
                .hasMessageContaining("loomspan.connections.gemini.gemini.credentials-uri")
                .hasMessageNotContaining("loomspan.connections.gemini.base-url")
                .hasCause(original)
                .satisfies(failure -> assertThat(failure.getCause()).isSameAs(original));
    }

    @Test
    void constructionFailureRetainsCleanupFailuresAndContinuesClosing() throws Exception {
        for (boolean safe : new boolean[] {true, false}) {
            var integration = mock(SpringAiProviderIntegration.class);
            var connections = new LinkedHashMap<String, LoomspanProperties.ConnectionProperties>();
            var first = mock(ChatModel.class, withSettings().extraInterfaces(DisposableBean.class));
            var second = mock(ChatModel.class, withSettings().extraInterfaces(DisposableBean.class));
            var third = mock(ChatModel.class, withSettings().extraInterfaces(DisposableBean.class));
            var firstCleanup = new IllegalStateException("first cleanup failed");
            var secondCleanup = new IllegalStateException("second cleanup failed");
            doThrow(firstCleanup).when((DisposableBean) first).destroy();
            doThrow(secondCleanup).when((DisposableBean) second).destroy();
            for (String name : new String[] {"first", "second", "third", "failed"})
                connections.put(name, connection("http://example.test"));
            when(integration.create("first", connections.get("first"))).thenReturn(runtime(first));
            when(integration.create("second", connections.get("second"))).thenReturn(runtime(second));
            when(integration.create("third", connections.get("third"))).thenReturn(runtime(third));
            RuntimeException original = safe ? new SafeAiConnectionConfigurationException("configuration failure")
                    : new IllegalArgumentException("construction failure");
            when(integration.create("failed", connections.get("failed"))).thenThrow(original);
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(
                    () -> new NamedAiConnectionRegistry(connections, integration));
            assertThat(failure.getSuppressed()).containsExactly(firstCleanup);
            assertThat(firstCleanup.getSuppressed()).containsExactly(secondCleanup);
            if (safe) assertThat(failure).isSameAs(original);
            else assertThat(failure.getCause()).isSameAs(original);
            verify((DisposableBean) first).destroy();
            verify((DisposableBean) second).destroy();
            verify((DisposableBean) third).destroy();
        }
    }

    @Test
    void nativeConstructionOwnershipClosesAcquiredResources() throws Exception {
        var integration = mock(SpringAiProviderIntegration.class);
        var properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.TYPESAFE);
        properties.setApiKey("fixture-key");
        var connections = new LinkedHashMap<String, LoomspanProperties.ConnectionProperties>();
        connections.put("native", properties);
        connections.put("failed", connection("http://example.test"));
        // Observe the actual Apache resource via its existing builder, without adding a factory SPI.
        try (var clients = org.mockito.Mockito.mockStatic(org.apache.hc.client5.http.impl.classic.HttpClients.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            var apache = new java.util.concurrent.atomic.AtomicReference<org.apache.hc.client5.http.impl.classic.CloseableHttpClient>();
            var builder = org.mockito.Mockito.spy(org.apache.hc.client5.http.impl.classic.HttpClients.custom());
            org.mockito.Mockito.doAnswer(call -> {
                var built = (org.apache.hc.client5.http.impl.classic.CloseableHttpClient) call.callRealMethod();
                apache.set(built);
                return built;
            }).when(builder).build();
            clients.when(org.apache.hc.client5.http.impl.classic.HttpClients::custom).thenReturn(builder);
            var original = new IllegalStateException("later construction failed");
            when(integration.create("failed", connections.get("failed"))).thenThrow(original);
            assertThatThrownBy(() -> new NamedAiConnectionRegistry(connections, integration)).hasCause(original);
            assertThat(apache.get()).isNotNull();
            assertThatThrownBy(() -> apache.get().execute(new org.apache.hc.client5.http.classic.methods.HttpGet(
                    "http://127.0.0.1:1"), response -> response.getCode()))
                    .hasStackTraceContaining("Connection pool shut down");
        }
    }

    @Test
    void nativeAllocationClosesWhenRuntimeConstructionFails() throws Exception {
        var original = new IllegalStateException("runtime construction failed");
        var properties = new LoomspanProperties.ConnectionProperties() {
            private int reads;
            @Override public AiDriver getDriver() {
                if (++reads == 2) throw original;
                return AiDriver.TYPESAFE;
            }
        };
        properties.setApiKey("fixture-key");
        try (var clients = org.mockito.Mockito.mockStatic(org.apache.hc.client5.http.impl.classic.HttpClients.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            var apache = new java.util.concurrent.atomic.AtomicReference<org.apache.hc.client5.http.impl.classic.CloseableHttpClient>();
            var builder = org.mockito.Mockito.spy(org.apache.hc.client5.http.impl.classic.HttpClients.custom());
            org.mockito.Mockito.doAnswer(call -> {
                var built = (org.apache.hc.client5.http.impl.classic.CloseableHttpClient) call.callRealMethod();
                apache.set(built);
                return built;
            }).when(builder).build();
            clients.when(org.apache.hc.client5.http.impl.classic.HttpClients::custom).thenReturn(builder);
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                    new NamedAiConnectionRegistry(Map.of("native", properties), mock(SpringAiProviderIntegration.class)));
            assertThat(apache.get()).isNotNull();
            assertThatThrownBy(() -> apache.get().execute(new org.apache.hc.client5.http.classic.methods.HttpGet(
                    "http://127.0.0.1:1"), response -> response.getCode()))
                    .hasStackTraceContaining("Connection pool shut down");
            assertThat(failure).hasCause(original);
            apache.get().close();
        }
    }

    @Test
    void invalidNativeRetrySettingsFailBeforeAllocatingTransport() {
        var properties = new LoomspanProperties.ConnectionProperties() {
            @Override public LoomspanProperties.ProviderRetryProperties getProviderRetry() { return null; }
        };
        properties.setDriver(AiDriver.TYPESAFE);
        properties.setApiKey("fixture-key");
        try (var clients = org.mockito.Mockito.mockConstruction(TypeSafeClient.class)) {
            assertThatThrownBy(() -> new NamedAiConnectionRegistry(Map.of("native", properties),
                    mock(SpringAiProviderIntegration.class))).hasRootCauseMessage("properties must not be null");
            assertThat(clients.constructed()).isEmpty();
        }
    }

    private static ProviderConnectionRuntime runtime(ChatModel model) {
        return ProviderConnectionRuntime.chat(model, AiDriver.OLLAMA, AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                ProviderRetryPolicy.from(new LoomspanProperties.ProviderRetryProperties()), ignored -> ProviderFailureDetails.unknown());
    }

    private static LoomspanProperties.ConnectionProperties connection(String baseUrl) {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OLLAMA);
        properties.setBaseUrl(baseUrl);
        return properties;
    }

    private static LoomspanProperties.ConnectionProperties geminiConnection() {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.GEMINI);
        properties.setApiKey("test-key");
        return properties;
    }
}
