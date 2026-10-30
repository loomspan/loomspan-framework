package ai.loomspan.internal.typesafe;

import static org.assertj.core.api.Assertions.*;

import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.provider.*;
import ai.loomspan.internal.runtime.usage.UsagePrecision;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

import okhttp3.mockwebserver.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

class TypeSafeClientTest {
    private LoomspanProperties.ConnectionProperties properties(String prefix) {
        var result = new LoomspanProperties.ConnectionProperties();
        result.setDriver(AiDriver.TYPESAFE);
        result.setApiKey("fixture-key");
        result.setBaseUrl(prefix);
        return result;
    }

    @Test
    void defaultAndPrefixedEndpointsKeepRoutesAndVerbatimHeaders() throws Exception {
        var direct = properties(null);
        try (var client = new TypeSafeClient(direct)) {
            assertThat(client.endpoint().toString())
                    .isEqualTo("https://api.typesafe.ai/v1/systemone");
        }
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("{\"answers\":{}}"));
            var props = properties(server.url("/api/").toString());
            props.setHeaders(Map.of("X-Title", "original title"));
            try (var client = new TypeSafeClient(props)) {
                assertThat(client.send("{\"model\":\"Verbatim/Model\"}", null))
                        .isEqualTo("{\"answers\":{}}");
            }
            var request = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request.getPath()).isEqualTo("/api/v1/systemone");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer fixture-key");
            assertThat(request.getHeader("X-Title")).isEqualTo("original title");
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getBody().readUtf8()).isEqualTo("{\"model\":\"Verbatim/Model\"}");
        }
    }

    @Test
    void redirectsAndOverloadHaveExactlyOnePhysicalSendAndErrorsRemainBounded() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(302)
                            .addHeader("Location", server.url("/again")));
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(529)
                            .addHeader("Retry-After", "2")
                            .setBody("x".repeat(1048577)));
            try (var client = new TypeSafeClient(properties(server.url("/").toString()))) {
                assertThatThrownBy(() -> client.send("{}", null))
                        .satisfies(
                                failure -> {
                                    assertThat(TypeSafeClient.translate(failure).httpStatus())
                                            .isEqualTo(302);
                                });
                assertThat(server.getRequestCount()).isEqualTo(1);
                assertThatThrownBy(() -> client.send("{}", null))
                        .satisfies(
                                failure -> {
                                    var details = TypeSafeClient.translate(failure);
                                    assertThat(details.category())
                                            .isEqualTo(ProviderFailureCategory.PROVIDER_OVERLOADED);
                                    assertThat(details.classification())
                                            .isEqualTo(ProviderFailureClassification.TRANSIENT);
                                    assertThat(details.retryAfter())
                                            .isEqualTo(Duration.ofSeconds(2));
                                    assertThat(details.diagnostics().getFirst())
                                            .containsEntry("truncated", true)
                                            .containsEntry("captureLimitBytes", 1048576);
                                    assertThat(
                                                    (String)
                                                            details.diagnostics()
                                                                    .getFirst()
                                                                    .get("text"))
                                            .hasSize(1048576);
                                });
                assertThat(server.getRequestCount()).isEqualTo(2);
            }
        }
    }

    @Test
    void responseTimeoutIsInactivityWhileProgressingChunksMayExceedIt() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            var props = properties(server.url("/").toString());
            props.setRequestTimeout(Duration.ofMillis(150));
            server.enqueue(
                    new MockResponse()
                            .setBody("abcdef")
                            .throttleBody(1, 60, TimeUnit.MILLISECONDS));
            server.enqueue(
                    new MockResponse()
                            .setBody("never")
                            .setHeadersDelay(500, TimeUnit.MILLISECONDS));
            try (var client = new TypeSafeClient(props)) {
                long before = System.nanoTime();
                assertThat(client.send("{}", null)).isEqualTo("abcdef");
                assertThat(Duration.ofNanos(System.nanoTime() - before))
                        .isGreaterThan(Duration.ofMillis(150));
                assertThatThrownBy(() -> client.send("{}", null))
                        .satisfies(
                                failure ->
                                        assertThat(TypeSafeClient.translate(failure).category())
                                                .isEqualTo(ProviderFailureCategory.TIMEOUT));
            }
        }
    }

    @Test
    void boundedErrorCaptureKeepsCompleteUnicodeCharactersAtByteBoundary() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(
                    new MockResponse().setResponseCode(400).setBody("a".repeat(1048575) + "😀"));
            try (var client = new TypeSafeClient(properties(server.url("/").toString()))) {
                assertThatThrownBy(() -> client.send("{}", null))
                        .satisfies(
                                failure -> {
                                    var diagnostic =
                                            TypeSafeClient.translate(failure)
                                                    .diagnostics()
                                                    .getFirst();
                                    assertThat(diagnostic).containsEntry("truncated", true);
                                    String text = (String) diagnostic.get("text");
                                    assertThat(text).isEqualTo("a".repeat(1048575));
                                    assertThat(
                                                    text.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8))
                                            .hasSizeLessThanOrEqualTo(1048576);
                                });
            }
        }
    }

    @Test
    void oversizedErrorStopsReadingAfterCaptureInsteadOfDrainingTheRemainder() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(400)
                            .setBody("x".repeat(2097152))
                            .throttleBody(1048577, 2, TimeUnit.SECONDS));
            try (var client = new TypeSafeClient(properties(server.url("/").toString()))) {
                long before = System.nanoTime();
                assertThatThrownBy(() -> client.send("{}", null))
                        .satisfies(
                                failure ->
                                        assertThat(
                                                        TypeSafeClient.translate(failure)
                                                                .diagnostics()
                                                                .getFirst())
                                                .containsEntry("truncated", true));
                assertThat(Duration.ofNanos(System.nanoTime() - before))
                        .isLessThan(Duration.ofMillis(1500));
            }
        }
    }

    @Test
    void nativeUsageRequiresIntegralRepresentableCompleteCountsAndRetainsMetadata() {
        var codec = LoomspanJacksonCodecs.defaults().schemaTree();
        for (String json :
                List.of(
                        "{\"input_tokens\":0,\"output_tokens\":0}",
                        "{\"input_tokens\":2,\"output_tokens\":3}")) {
            var node = codec.readTree(json);
            var counts = TypeSafeExecutor.nativeUsage(node);
            assertThat(counts.precision()).isEqualTo(UsagePrecision.EXACT);
            assertThat(counts.totalUnits())
                    .isEqualTo(counts.promptUnits() + counts.completionUnits());
            assertThat(counts.nativeUsage()).isEqualTo(node);
        }
        for (String json :
                List.of(
                        "null",
                        "{}",
                        "{\"input_tokens\":1}",
                        "{\"input_tokens\":1.0,\"output_tokens\":1}",
                        "{\"input_tokens\":-1,\"output_tokens\":0}",
                        "{\"input_tokens\":2147483647,\"output_tokens\":1}",
                        "{\"input_tokens\":2147483648,\"output_tokens\":0}",
                        "{\"input_tokens\":\"1\",\"output_tokens\":1}")) {
            var node = codec.readTree(json);
            var counts = TypeSafeExecutor.nativeUsage(node);
            assertThat(counts.precision()).isEqualTo(UsagePrecision.UNAVAILABLE);
            assertThat(counts.totalUnits()).isZero();
            assertThat(counts.nativeUsage()).isEqualTo(node);
        }
    }

    @Test
    void connectionValidationUsesExistingConfigurationBoundary() {
        var properties = new LoomspanProperties();
        var connection = properties("https://openrouter.ai/api/");
        properties.setConnections(Map.of("native", connection));
        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
        for (String prefix :
                List.of(
                        "relative/path",
                        "ftp://example.test",
                        "https://example.test/?q=1",
                        "https://example.test/#fragment",
                        "")) {
            connection.setBaseUrl(prefix);
            assertThatThrownBy(properties::afterPropertiesSet).hasMessageContaining("base-url");
        }
        connection.setBaseUrl(null);
        connection.setApiKey(null);
        assertThatThrownBy(properties::afterPropertiesSet).hasMessageContaining("api-key");
        connection.setApiKey("fixture");
        connection.setHeaders(Map.of("aUtHoRiZaTiOn", "explicit"));
        assertThatThrownBy(properties::afterPropertiesSet).hasMessageContaining("Authorization");
        connection.setHeaders(Map.of());
        connection.setOpenai(new LoomspanProperties.OpenAiOptions());
        assertThatThrownBy(properties::afterPropertiesSet).hasMessageContaining("openai");
        connection.setOpenai(null);
        var model = new LoomspanProperties.ModelCatalogEntry();
        model.setConnection("native");
        model.setProviderModel("jev");
        model.setThinkingLevels(Set.of("high"));
        properties.setModels(Map.of("judge", model));
        assertThatThrownBy(properties::afterPropertiesSet).hasMessageContaining("thinking-levels");
    }

    @Test
    void pinnedApacheTimeoutDefaultsAndExplicitSettingsMatchActualClientConfiguration()
            throws Exception {
        for (Duration configured : Arrays.asList(null, Duration.ofMillis(240))) {
            var props = properties(null);
            props.setRequestTimeout(configured);
            try (var client = new TypeSafeClient(props)) {
                var requestField = TypeSafeClient.class.getDeclaredField("requestConfig");
                requestField.setAccessible(true);
                var connectionField = TypeSafeClient.class.getDeclaredField("connectionConfig");
                connectionField.setAccessible(true);
                var request =
                        (org.apache.hc.client5.http.config.RequestConfig) requestField.get(client);
                var connection =
                        (org.apache.hc.client5.http.config.ConnectionConfig)
                                connectionField.get(client);
                assertThat(request.getConnectionRequestTimeout().toMilliseconds())
                        .isEqualTo(180000);
                assertThat(connection.getConnectTimeout().toMilliseconds()).isEqualTo(180000);
                if (configured == null) {
                    assertThat(request.getResponseTimeout()).isNull();
                    assertThat(connection.getSocketTimeout()).isNull();
                    assertThat(
                                    org.apache.hc.core5.http.io.SocketConfig.DEFAULT
                                            .getSoTimeout()
                                            .toMilliseconds())
                            .isEqualTo(180000);
                } else {
                    assertThat(request.getResponseTimeout().toMilliseconds()).isEqualTo(240);
                    assertThat(connection.getSocketTimeout().toMilliseconds()).isEqualTo(240);
                }
            }
        }
    }

    @Test
    void registryOwnsNativeResourceAndClosesIdempotently() throws Exception {
        var props = properties(null);
        var registry =
                new ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry(
                        Map.of("native", props),
                        new ai.loomspan.internal.springai.SpringAiProviderIntegration(
                                new org.springframework.core.io.DefaultResourceLoader()));
        var nativeRuntime = registry.asMap().get("native");
        assertThat(nativeRuntime.chatModel()).isNull();
        assertThat(nativeRuntime.nativeClient()).isNotNull();
        assertThatThrownBy(nativeRuntime::requireChatModel)
                .hasMessageContaining("cannot execute chat");
        registry.destroy();
        registry.destroy();
        assertThatThrownBy(() -> nativeRuntime.nativeClient().send("{}", null))
                .hasStackTraceContaining("shut down");
    }

    @Test
    void missionCancellationAbortsBlockedHttpWithoutDependingOnWorkerInterruption()
            throws Exception {
        try (var server = new MockWebServer();
                var worker = Executors.newSingleThreadExecutor()) {
            server.start();
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            server.enqueue(new MockResponse().setBody("released"));
            var session =
                    ai.loomspan.internal.core.TestLoomspanSessions.withId(
                            "cancel-native", "default-judge", 8);
            var mission =
                    new ai.loomspan.internal.core.MissionContext(session, "default-judge", "native", null);
            var binding =
                    ai.loomspan.testkit.TestSkillGenerations.binding(
                            session,
                            mission,
                            new ai.loomspan.internal.core.PhysicalBranchContext(session),
                            ai.loomspan.testkit.TestSkillGenerations.empty());
            try (var client = new TypeSafeClient(properties(server.url("/").toString()))) {
                Future<String> waiting =
                        worker.submit(() -> client.send("{}", mission.lifecycle()));
                var blocked = server.takeRequest(2, TimeUnit.SECONDS);
                assertThat(blocked).isNotNull();
                mission.lifecycle()
                        .beginCancellation(
                                binding,
                                new CancellationException("cutoff"),
                                () -> "cancellation-fixture");
                assertThatThrownBy(() -> waiting.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class);
                // A new successful send uses a new socket: the cancelled request released its
                // physical connection.
                assertThat(client.send("{}", null)).isEqualTo("released");
                assertThat(server.takeRequest(2, TimeUnit.SECONDS).getSequenceNumber()).isZero();
                assertThat(waiting.isCancelled()).isFalse();
            }
            mission.lifecycle().closeNow();
        }
    }
}
