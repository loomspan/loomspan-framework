package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderFailureClassification;
import com.google.genai.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Real synchronous adapters, including original OpenRouter body consumption. No external auth. */
class ProviderRequestTimeoutIntegrationTest
{
    enum Provider { OPENAI, OPENROUTER, ANTHROPIC, GEMINI, VERTEX }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void actualTransportPreservesDefaultAndConnectPhase(Provider provider) throws Exception
    {
        var properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(provider == Provider.ANTHROPIC ? AiDriver.ANTHROPIC
                : provider == Provider.GEMINI || provider == Provider.VERTEX ? AiDriver.GEMINI : AiDriver.OPENAI);
        properties.setApiKey("local-fake-key");
        if (provider == Provider.VERTEX)
        {
            properties.setApiKey(null);
            var options = new LoomspanProperties.GeminiOptions();
            options.setVertexAi(true);
            options.setProjectId("project");
            options.setLocation("us-central1");
            options.setCredentialsJson(serviceAccount("http://127.0.0.1:1/token"));
            properties.setGemini(options);
        }
        if (provider == Provider.OPENROUTER)
        {
            var options = new LoomspanProperties.OpenAiOptions();
            options.setCompatibilityProfile(LoomspanProperties.OpenAiCompatibilityProfile.OPENROUTER);
            properties.setOpenai(options);
        }
        boolean google = properties.getDriver() == AiDriver.GEMINI;
        for (Duration timeout : new Duration[] {null, Duration.ofSeconds(240)})
        {
            properties.setRequestTimeout(timeout);
            var runtime = new SpringAiProviderIntegration(new DefaultResourceLoader()).create("local", properties);
            okhttp3.OkHttpClient transport = transport(runtime.chatModel(), new java.util.IdentityHashMap<>(), 0);
            assertThat(transport).as("actual %s transport", provider).isNotNull();
            assertThat(transport.callTimeoutMillis()).isEqualTo(timeout == null ? google ? 0 : 60000 : 240000);
            assertThat(transport.connectTimeoutMillis()).isEqualTo(google ? 0 : 60000);
            assertThat(transport.readTimeoutMillis()).isEqualTo(google ? 0 : timeout == null ? 60000 : 240000);
            assertThat(transport.writeTimeoutMillis()).isEqualTo(google ? 0 : timeout == null ? 60000 : 240000);
        }
    }

    // Follow the actual model/client object graph rather than inspecting only default options.
    private static okhttp3.OkHttpClient transport(Object value, java.util.IdentityHashMap<Object, Boolean> seen,
            int depth) throws IllegalAccessException
    {
        if (value instanceof okhttp3.OkHttpClient client) return client;
        if (value == null || depth > 16 || seen.put(value, true) != null) return null;
        String name = value.getClass().getName();
        if (!(name.startsWith("org.springframework.ai.") || name.startsWith("com.openai.")
                || name.startsWith("com.anthropic.") || name.startsWith("com.google.genai."))) return null;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            for (var field : type.getDeclaredFields())
            {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                var found = transport(field.get(value), seen, depth + 1);
                if (found != null) return found;
            }
        return null;
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void configuredBudgetAllowsDelayedHeadersAndCompleteChunkedBody(Provider provider) throws Exception
    {
        exercise(provider, false, false);
        exercise(provider, true, false);
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void wholeCallExpiresDespiteHeadersAndContinuouslyActiveBody(Provider provider) throws Exception
    {
        exercise(provider, false, true);
        exercise(provider, true, true);
    }

    private void exercise(Provider provider, boolean chunks, boolean expires) throws Exception
    {
        try (MockWebServer server = new MockWebServer(); MockWebServer token = new MockWebServer())
        {
            String json = response(provider);
            MockResponse response = new MockResponse().setHeader("Content-Type", "application/json");
            if (chunks)
                response.setChunkedBody(" ".repeat(1000) + json, 50).throttleBody(50, 75, TimeUnit.MILLISECONDS);
            else response.setBody(json).setHeadersDelay(expires ? 1800 : 150, TimeUnit.MILLISECONDS);
            server.enqueue(response);
            var properties = new LoomspanProperties.ConnectionProperties();
            properties.setDriver(provider == Provider.ANTHROPIC ? AiDriver.ANTHROPIC
                    : provider == Provider.GEMINI || provider == Provider.VERTEX ? AiDriver.GEMINI : AiDriver.OPENAI);
            properties.setRequestTimeout(Duration.ofSeconds(expires ? 1 : 4));
            if (provider == Provider.VERTEX)
            {
                token.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("{\"access_token\":\"local-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));
                var options = new LoomspanProperties.GeminiOptions();
                options.setVertexAi(true);
                options.setProjectId("project");
                options.setLocation("us-central1");
                options.setCredentialsJson(serviceAccount(token.url("/token").toString()));
                properties.setGemini(options);
            }
            else properties.setApiKey("local-fake-key");
            if (properties.getDriver() != AiDriver.GEMINI) properties.setBaseUrl(server.url("/v1").toString());
            if (provider == Provider.OPENROUTER)
            {
                var options = new LoomspanProperties.OpenAiOptions();
                options.setCompatibilityProfile(LoomspanProperties.OpenAiCompatibilityProfile.OPENROUTER);
                properties.setOpenai(options);
            }
            Client.setDefaultBaseUrls(Optional.of(server.url("/").toString()), Optional.of(server.url("/").toString()));
            try
            {
                var runtime = new SpringAiProviderIntegration(new DefaultResourceLoader()).create("local", properties);
                var options = switch (properties.getDriver()) {
                    case OPENAI -> OpenAiChatOptions.builder().model("test-model").build();
                    case ANTHROPIC -> AnthropicChatOptions.builder().model("test-model").maxTokens(128).build();
                    case GEMINI -> GoogleGenAiChatOptions.builder().model("test-model").build();
                    default -> throw new AssertionError();
                };
                long start = System.nanoTime();
                if (expires)
                {
                    Throwable failure = catchThrowable(() -> runtime.chatModel().call(new Prompt("hello", options)));
                    assertThat(failure).as("%s chunks=%s", provider, chunks).isNotNull();
                    var details = runtime.failureTranslator().translate(failure);
                    assertThat(details.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
                    assertThat(details.category()).isEqualTo(ProviderFailureCategory.TIMEOUT);
                    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
                }
                else assertThat(runtime.chatModel().call(new Prompt("hello", options)).getResult().getOutput().getText())
                        .isEqualTo("ok");
                assertThat(server.getRequestCount()).isEqualTo(1);
                var request = server.takeRequest(2, TimeUnit.SECONDS);
                assertThat(request).isNotNull();
                assertThat(request.getBody().readUtf8()).doesNotContain("request-timeout", "requestTimeout");
                if (provider == Provider.VERTEX)
                {
                    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer local-token");
                    assertThat(token.getRequestCount()).isEqualTo(1);
                }
                else assertThat(token.getRequestCount()).isZero();
            }
            finally { Client.setDefaultBaseUrls(Optional.empty(), Optional.empty()); }
        }
    }

    private static String response(Provider provider)
    {
        return switch (provider) {
            case OPENAI, OPENROUTER -> """
                    {"id":"local","object":"chat.completion","created":1,"model":"test-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """;
            case ANTHROPIC -> """
                    {"id":"local","type":"message","role":"assistant","model":"test-model",
                     "content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn","stop_sequence":null,
                     "usage":{"input_tokens":1,"output_tokens":1}}
                    """;
            case GEMINI, VERTEX -> """
                    {"modelVersion":"test-model","candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]},"finishReason":"STOP"}],
                     "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
                    """;
        };
    }

    private static String serviceAccount(String tokenUri) throws Exception
    {
        var keys = java.security.KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(keys.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return """
                {"type":"service_account","project_id":"project","private_key_id":"local",
                 "private_key":"%s","client_email":"local@project.iam.gserviceaccount.com",
                 "client_id":"123456789","token_uri":"%s"}
                """.formatted(pem.replace("\n", "\\n"), tokenUri);
    }
}
