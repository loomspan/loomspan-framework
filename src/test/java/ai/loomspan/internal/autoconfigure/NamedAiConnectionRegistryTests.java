package ai.loomspan.internal.autoconfigure;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.provider.AttemptOwnership;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.provider.ProviderFailureDetails;
import ai.loomspan.internal.provider.ProviderRetryPolicy;
import ai.loomspan.internal.springai.SpringAiProviderIntegration;
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
        ProviderConnectionRuntime opaque = ProviderConnectionRuntime.chat(model, AiDriver.OPENAI,
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
        ProviderConnectionRuntime disabled = ProviderConnectionRuntime.chat(model, AiDriver.OPENAI,
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
    void destructionClosesEveryModelOnceAndRetainsCleanupFailures() throws Exception {
        var integration = mock(SpringAiProviderIntegration.class);
        var disposable = mock(ChatModel.class, withSettings().extraInterfaces(DisposableBean.class));
        var closeable = mock(ChatModel.class, withSettings().extraInterfaces(AutoCloseable.class));
        var firstFailure = new IllegalStateException("disposal failed");
        var secondFailure = new IllegalStateException("close failed");
        doThrow(firstFailure).when((DisposableBean) disposable).destroy();
        doThrow(secondFailure).when((AutoCloseable) closeable).close();
        var connections = new LinkedHashMap<String, LoomspanProperties.ConnectionProperties>();
        connections.put("first", connection("http://one.example"));
        connections.put("second", connection("http://two.example"));
        when(integration.create("first", connections.get("first"))).thenReturn(runtime(disposable));
        when(integration.create("second", connections.get("second"))).thenReturn(runtime(closeable));
        var registry = new NamedAiConnectionRegistry(connections, integration);
        var failure = org.assertj.core.api.Assertions.catchThrowable(registry::destroy);
        assertThat(failure).isIn(firstFailure, secondFailure);
        assertThat(failure.getSuppressed()).containsExactly(failure == firstFailure ? secondFailure : firstFailure);
        registry.destroy();
        verify((DisposableBean) disposable).destroy();
        verify((AutoCloseable) closeable).close();
    }

    private static ProviderConnectionRuntime runtime(ChatModel model) {
        return ProviderConnectionRuntime.chat(model, AiDriver.OPENAI, AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                ProviderRetryPolicy.from(new LoomspanProperties.ProviderRetryProperties()), ignored -> ProviderFailureDetails.unknown());
    }

    private static LoomspanProperties.ConnectionProperties connection(String baseUrl) {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OPENAI);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("ollama");
        return properties;
    }

    private static LoomspanProperties.ConnectionProperties geminiConnection() {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.GEMINI);
        properties.setApiKey("test-key");
        return properties;
    }
}
