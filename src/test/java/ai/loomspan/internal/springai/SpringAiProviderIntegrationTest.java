package ai.loomspan.internal.springai;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIServiceException;
import com.google.genai.Client;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderFailureClassification;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import javax.net.ssl.SSLException;

import static org.assertj.core.api.Assertions.catchThrowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiProviderIntegrationTest
{
    private final SpringAiProviderIntegration integration =
            new SpringAiProviderIntegration(new DefaultResourceLoader());

    @Test
    void malformedInMemoryVertexCredentialFailsLocallyWithoutDisclosingContent()
    {
        var connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.GEMINI);
        var options = new LoomspanProperties.GeminiOptions();
        options.setVertexAi(true);
        options.setProjectId("project");
        options.setLocation("us-central1");
        options.setCredentialsJson("invalid-json-secret-sentinel");
        connection.setGemini(options);
        assertThat(catchThrowable(() -> integration.create("vertex", connection)))
                .hasMessageContaining("credentials-json-ref")
                .hasMessageNotContaining("invalid-json-secret-sentinel");
    }

    @Test
    void inMemoryVertexServiceAccountBuildsWithoutModelRequest() throws Exception
    {
        var keys = java.security.KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(keys.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = """
                {"type":"service_account","project_id":"project","private_key_id":"test",
                 "private_key":"%s","client_email":"test@project.iam.gserviceaccount.com",
                 "client_id":"123456789", "auth_uri":"https://accounts.google.com/o/oauth2/auth",
                 "token_uri":"https://oauth2.googleapis.com/token"}
                """.formatted(pem.replace("\n", "\\n"));
        assertThat(com.google.auth.oauth2.ServiceAccountCredentials.fromStream(
                new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).isNotNull();
        var connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.GEMINI);
        var options = new LoomspanProperties.GeminiOptions();
        options.setVertexAi(true);
        options.setProjectId("project");
        options.setLocation("us-central1");
        options.setCredentialsJson(json);
        connection.setGemini(options);
        assertThat(integration.create("vertex", connection).chatModel()).isNotNull();
    }

    @Test
    void googleSdkRetriesAreDisabledAtTheHttpClientBoundary()
    {
        assertThat(SpringAiProviderIntegration.oneAttemptGoogleHttpOptions(null)
                .retryOptions().orElseThrow().attempts()).contains(1);
    }

    @Test
    void googleDirectInvocationMakesOneHttpAttemptOnRetryableFailure() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse().setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"temporarily unavailable\"}}"));
            Client.setDefaultBaseUrls(Optional.of(server.url("/").toString()), Optional.empty());
            try
            {
                var runtime = integration.create("gemini", gemini());
                Throwable failure = catchThrowable(() -> runtime.chatModel().call(new Prompt("hello",
                        GoogleGenAiChatOptions.builder().model("gemini-test").build())));

                assertThat(failure).isNotNull();
                assertThat(server.getRequestCount()).isEqualTo(1);
                assertThat(runtime.failureTranslator().translate(failure).classification())
                        .isEqualTo(ProviderFailureClassification.TRANSIENT);
            }
            finally
            {
                Client.setDefaultBaseUrls(Optional.empty(), Optional.empty());
            }
        }
    }

    @Test
    void translatesTypedGoogleHttpAndTransportFailuresWithoutMessageParsing()
    {
        var translator = integration.create("gemini", gemini()).failureTranslator();

        var unavailable = translator.translate(new RuntimeException(
                new ServerException(503, "UNAVAILABLE", "provider overloaded")));
        assertThat(unavailable.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
        assertThat(unavailable.category()).isEqualTo(ProviderFailureCategory.SERVER_ERROR);
        assertThat(unavailable.httpStatus()).isEqualTo(503);
        assertThat(unavailable.providerErrorCode()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.get("text")).asString().contains("provider overloaded"));

        var rateLimited = translator.translate(new ClientException(429, "RESOURCE_EXHAUSTED", "slow down"));
        assertThat(rateLimited.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
        assertThat(rateLimited.category()).isEqualTo(ProviderFailureCategory.RATE_LIMITED);

        var connectivity = translator.translate(new GenAiIOException("network failed", new SocketException("reset")));
        assertThat(connectivity.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
        assertThat(connectivity.category()).isEqualTo(ProviderFailureCategory.CONNECTIVITY);

        var tls = translator.translate(new GenAiIOException("TLS failed", new SSLException("untrusted")));
        assertThat(tls.classification()).isEqualTo(ProviderFailureClassification.PERMANENT);

        var genericIo = translator.translate(new GenAiIOException("decode failed", new IOException("invalid body")));
        assertThat(genericIo.classification()).isEqualTo(ProviderFailureClassification.UNKNOWN);
    }

    @Test
    void translatesOpenAiServiceExceptionsThroughExistingHttpPolicy()
    {
        var translator = integration.create("openai", openAi()).failureTranslator();
        int[] statuses = {401, 403, 404, 429, 503};
        ProviderFailureClassification[] classifications = {
                ProviderFailureClassification.PERMANENT, ProviderFailureClassification.PERMANENT,
                ProviderFailureClassification.PERMANENT, ProviderFailureClassification.TRANSIENT,
                ProviderFailureClassification.TRANSIENT};
        ProviderFailureCategory[] categories = {
                ProviderFailureCategory.AUTHENTICATION, ProviderFailureCategory.AUTHORIZATION,
                ProviderFailureCategory.INVALID_REQUEST, ProviderFailureCategory.RATE_LIMITED,
                ProviderFailureCategory.SERVER_ERROR};

        for (int index = 0; index < statuses.length; index++)
        {
            OpenAIServiceException exception = mock(OpenAIServiceException.class);
            Headers headers = Headers.builder().put("Retry-After", "7").build();
            JsonValue body = JsonValue.from(java.util.Map.of("error", "provider detail"));
            when(exception.statusCode()).thenReturn(statuses[index]);
            when(exception.headers()).thenReturn(headers);
            when(exception.body()).thenReturn(body);
            when(exception.type()).thenReturn(Optional.of("provider_type"));
            when(exception.code()).thenReturn(Optional.of("provider_code"));

            var details = translator.translate(new RuntimeException(exception));

            assertThat(details.classification()).isEqualTo(classifications[index]);
            assertThat(details.category()).isEqualTo(categories[index]);
            assertThat(details.httpStatus()).isEqualTo(statuses[index]);
            assertThat(details.retryAfter()).isEqualTo(Duration.ofSeconds(7));
            assertThat(details.providerErrorType()).isEqualTo("provider_type");
            assertThat(details.providerErrorCode()).isEqualTo("provider_code");
            assertThat(details.diagnostics()).singleElement().satisfies(diagnostic ->
                    assertThat(diagnostic.get("text")).isEqualTo("{\"error\":\"provider detail\"}"));
        }

        int limit = SpringAiProviderIntegration.DIAGNOSTIC_LIMIT_BYTES;
        OpenAIServiceException oversized = mock(OpenAIServiceException.class);
        when(oversized.statusCode()).thenReturn(401);
        when(oversized.headers()).thenReturn(Headers.builder().build());
        when(oversized.body()).thenReturn(JsonValue.from(java.util.Map.of(
                "error", "a".repeat(limit - 11) + "💥")));
        when(oversized.type()).thenReturn(Optional.empty());
        when(oversized.code()).thenReturn(Optional.empty());

        var oversizedDetails = translator.translate(oversized);

        assertThat(oversizedDetails.diagnostics()).singleElement().satisfies(diagnostic ->
        {
            assertThat(diagnostic.get("truncated")).isEqualTo(true);
            assertThat(((String) diagnostic.get("text")).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .hasSizeLessThanOrEqualTo(limit);
        });
    }

    @Test
    void translatesOpenAiInterruptedReadTimeoutAsTransientTimeout()
    {
        var translator = integration.create("openai", openAi()).failureTranslator();

        var observed = translator.translate(new RuntimeException("Error reading response",
                new InterruptedIOException("timeout")));
        assertThat(observed.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
        assertThat(observed.category()).isEqualTo(ProviderFailureCategory.TIMEOUT);

        var direct = translator.translate(new InterruptedIOException("read timed out"));
        assertThat(direct.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
        assertThat(direct.category()).isEqualTo(ProviderFailureCategory.TIMEOUT);

        var socket = translator.translate(new SocketTimeoutException("read timed out"));
        assertThat(socket.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
        assertThat(socket.category()).isEqualTo(ProviderFailureCategory.TIMEOUT);
    }

    @Test
    void doesNotTreatCancellationOrGenericInterruptedIoAsTimeout()
    {
        var translator = integration.create("openai", openAi()).failureTranslator();
        CancellationException cancellation = new CancellationException("caller canceled");
        cancellation.initCause(new InterruptedIOException("timeout"));
        assertThat(translator.translate(cancellation).category()).isEqualTo(ProviderFailureCategory.UNKNOWN);
        assertThat(translator.translate(new RuntimeException(new InterruptedException("caller interrupted"))).category())
                .isEqualTo(ProviderFailureCategory.UNKNOWN);
        assertThat(translator.translate(new InterruptedIOException("stream closed")).category())
                .isEqualTo(ProviderFailureCategory.UNKNOWN);
        assertThat(translator.translate(new IOException("stream was reset: CANCEL")).category())
                .isEqualTo(ProviderFailureCategory.UNKNOWN);
        assertThat(translator.translate(new IOException("invalid body")).category())
                .isEqualTo(ProviderFailureCategory.UNKNOWN);

        try
        {
            Thread.currentThread().interrupt();
            assertThat(translator.translate(new InterruptedIOException("timeout")).category())
                    .isEqualTo(ProviderFailureCategory.UNKNOWN);
        }
        finally
        {
            Thread.interrupted();
        }
    }

    @Test
    void openRouterDiagnosticCaptureIsBoundedAtOneMebibyte() throws Exception
    {
        int limit = SpringAiProviderIntegration.DIAGNOSTIC_LIMIT_BYTES;
        for (int bodySize : new int[] { limit - 1, limit, limit + 1, limit * 2 })
        {
            try (MockWebServer server = new MockWebServer())
            {
                server.enqueue(new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(openRouterErrorBody(bodySize)));
                LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
                properties.setDriver(AiDriver.OPENAI);
                properties.setApiKey("gateway-key");
                properties.setBaseUrl(server.url("/v1").toString());
                LoomspanProperties.OpenAiOptions openAi = new LoomspanProperties.OpenAiOptions();
                openAi.setCompatibilityProfile(LoomspanProperties.OpenAiCompatibilityProfile.OPENROUTER);
                properties.setOpenai(openAi);
                var runtime = integration.create("openrouter", properties);

                Throwable failure = catchThrowable(() -> runtime.chatModel().call(new Prompt("hello",
                        OpenAiChatOptions.builder().model("routed-model").build())));
                var details = runtime.failureTranslator().translate(failure);

                assertThat(failure).isNotNull();
                assertThat(details.classification()).isEqualTo(ProviderFailureClassification.TRANSIENT);
                assertThat(details.category()).isEqualTo(ProviderFailureCategory.PROVIDER_OVERLOADED);
                assertThat(details.providerErrorCode()).isEqualTo("E_OVERLOAD");
                assertThat(server.getRequestCount()).isEqualTo(1);
                assertThat(details.diagnostics()).singleElement().satisfies(diagnostic ->
                {
                    assertThat(diagnostic.get("text")).isEqualTo(openRouterErrorBody(bodySize).substring(0, Math.min(bodySize, limit)));
                    assertThat(diagnostic.get("captureLimitBytes")).isEqualTo(limit);
                    assertThat(((String) diagnostic.get("text")).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .hasSizeLessThanOrEqualTo(limit);
                    assertThat(diagnostic.get("truncated")).isEqualTo(bodySize > limit);
                });
            }
        }
    }

    @Test
    void validResponsesPreserveContentToolsAndMetadataAcrossProfilesAndDiagnosticBoundary() throws Exception
    {
        int limit = SpringAiProviderIntegration.DIAGNOSTIC_LIMIT_BYTES;
        for (boolean openRouter : new boolean[] {false, true})
        {
            for (int size : new int[] {limit - 1, limit, limit + 1, limit * 2})
            {
                String content = "answer-" + "x".repeat(size / 2 - 500) + "-answer-end";
                String arguments = "{\"value\":\"" + "y".repeat(size / 2 - 500) + "-argument-end\"}";
                String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                        "id", "complete-response", "object", "chat.completion", "created", 1,
                        "model", "routed-model", "choices", java.util.List.of(java.util.Map.of(
                                "index", 0, "finish_reason", "tool_calls", "message", java.util.Map.of(
                                        "role", "assistant", "content", content, "tool_calls", java.util.List.of(
                                                java.util.Map.of("id", "call-1", "type", "function", "function",
                                                        java.util.Map.of("name", "lookup", "arguments", arguments)))))),
                        "usage", java.util.Map.of("prompt_tokens", 2, "completion_tokens", 3, "total_tokens", 5)));
                // Pad outside the object so each fixture has an exact UTF-8 byte size.
                json += " ".repeat(size - json.length());
                try (MockWebServer server = new MockWebServer())
                {
                    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(json));
                    var properties = openAi();
                    properties.setBaseUrl(server.url("/v1").toString());
                    if (openRouter)
                    {
                        var options = new LoomspanProperties.OpenAiOptions();
                        options.setCompatibilityProfile(LoomspanProperties.OpenAiCompatibilityProfile.OPENROUTER);
                        properties.setOpenai(options);
                    }
                    var response = integration.create("test", properties).chatModel().call(new Prompt("hello",
                            OpenAiChatOptions.builder().model("routed-model").build()));
                    assertThat(response.getResult().getOutput().getText()).isEqualTo(content);
                    assertThat(response.getResult().getOutput().getToolCalls()).singleElement().satisfies(tool ->
                    {
                        assertThat(tool.id()).isEqualTo("call-1");
                        assertThat(tool.name()).isEqualTo("lookup");
                        assertThat(tool.arguments()).isEqualTo(arguments);
                    });
                    assertThat(response.getMetadata().getId()).isEqualTo("complete-response");
                    assertThat(response.getMetadata().getModel()).isEqualTo("routed-model");
                    assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(5);
                    assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("TOOL_CALLS");
                    assertThat(server.getRequestCount()).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void inspectedResponsesReleaseOriginalStreamOnSuccessAndFailure() throws Exception
    {
        int limit = SpringAiProviderIntegration.DIAGNOSTIC_LIMIT_BYTES;
        String success = "{\"choices\":[{\"finish_reason\":\"stop\"}],\"padding\":\"" + "x".repeat(limit + 1) + "\"}";
        for (String json : new String[] {success, openRouterErrorBody(limit * 2), "not-json",
                "{\"choices\":[{\"finish_reason\":\"error\"}]}",
                "{\"padding\":\"" + "x".repeat(limit + 1) + "\",\"choices\":[{\"finish_reason\":\"error\"}]}"})
        {
            TrackingInputStream stream = new TrackingInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Response response = streamingResponse(stream);
            if (json.equals(success))
            {
                try (Response inspected = integration.inspectOpenRouter(response))
                {
                    assertThat(inspected.body().string()).isEqualTo(json);
                }
            }
            else
            {
                Throwable failure = catchThrowable(() -> integration.inspectOpenRouter(response));
                assertThat(failure).isNotNull();
                if (json.contains("finish_reason"))
                {
                    var details = ((ProviderCallException) failure).details();
                    assertThat(details.category()).isEqualTo(json.contains("provider_overloaded")
                            ? ProviderFailureCategory.PROVIDER_OVERLOADED : ProviderFailureCategory.CLIENT_DECODING);
                    assertThat(details.diagnostics()).singleElement().satisfies(diagnostic ->
                            assertThat(diagnostic.get("truncated")).isEqualTo(json.length() > limit));
                }
            }
            assertThat(stream.closed).isTrue();
            assertThat(stream.available()).isZero();
        }
    }

    @Test
    void inspectionReadFailureClosesOriginalStream()
    {
        TrackingInputStream stream = new TrackingInputStream(new byte[0])
        {
            @Override public synchronized int read(byte[] bytes, int offset, int length)
            {
                throw new java.io.UncheckedIOException(new IOException("failed read"));
            }
        };
        assertThat(catchThrowable(() -> integration.inspectOpenRouter(streamingResponse(stream)))).isNotNull();
        assertThat(stream.closed).isTrue();
    }

    private static Response streamingResponse(InputStream stream)
    {
        ResponseBody body = new ResponseBody()
        {
            private final BufferedSource source = Okio.buffer(Okio.source(stream));
            @Override public MediaType contentType() { return MediaType.get("application/json"); }
            @Override public long contentLength() { return -1; }
            @Override public BufferedSource source() { return source; }
        };
        return new Response.Builder().request(new Request.Builder().url("https://openrouter.example/v1/chat/completions").build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build();
    }

    private static LoomspanProperties.ConnectionProperties gemini()
    {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.GEMINI);
        properties.setApiKey("test-key");
        return properties;
    }

    private static LoomspanProperties.ConnectionProperties openAi()
    {
        LoomspanProperties.ConnectionProperties properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OPENAI);
        properties.setApiKey("test-key");
        return properties;
    }

    private static String openRouterErrorBody(int targetBytes)
    {
        String prefix = "{\"id\":\"chatcmpl-error\",\"object\":\"chat.completion\",\"created\":1,"
                + "\"model\":\"routed-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"";
        String suffix = "\"},\"finish_reason\":\"error\",\"error\":{\"message\":\"overloaded\","
                + "\"code\":\"E_OVERLOAD\",\"metadata\":{\"error_type\":\"provider_overloaded\"}}}]}";
        int padding = targetBytes - prefix.length() - suffix.length();
        if (padding < 0)
        {
            throw new IllegalArgumentException("targetBytes is too small");
        }
        return prefix + "x".repeat(padding) + suffix;
    }

    private static class TrackingInputStream extends ByteArrayInputStream
    {
        boolean closed;
        private TrackingInputStream(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
}
