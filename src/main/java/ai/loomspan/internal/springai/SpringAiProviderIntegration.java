package ai.loomspan.internal.springai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.autoconfigure.SafeAiConnectionConfigurationException;
import ai.loomspan.internal.provider.AttemptOwnership;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderFailureClassification;
import ai.loomspan.internal.provider.ProviderFailureDetails;
import ai.loomspan.internal.provider.ProviderRetryPolicy;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIServiceException;
import io.micrometer.observation.ObservationRegistry;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Official Spring AI provider integration boundary. */
public final class SpringAiProviderIntegration
{
    static final int DIAGNOSTIC_LIMIT_BYTES = 1024 * 1024;
    private final ResourceLoader resourceLoader;
    private final Clock clock;
    private final ObservationRegistry observationRegistry;
    private final ObjectMapper objectMapper;

    public SpringAiProviderIntegration(ResourceLoader resourceLoader)
    {
        this(resourceLoader, ObservationRegistry.NOOP);
    }

    public SpringAiProviderIntegration(ResourceLoader resourceLoader, ObservationRegistry observationRegistry)
    {
        this(resourceLoader, Clock.systemUTC(), observationRegistry,
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
    }

    public SpringAiProviderIntegration(ResourceLoader resourceLoader, ObservationRegistry observationRegistry,
            ObjectMapper objectMapper)
    {
        this(resourceLoader, Clock.systemUTC(), observationRegistry, objectMapper);
    }

    SpringAiProviderIntegration(ResourceLoader resourceLoader, Clock clock)
    {
        this(resourceLoader, clock, ObservationRegistry.NOOP,
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
    }

    SpringAiProviderIntegration(ResourceLoader resourceLoader, Clock clock, ObservationRegistry observationRegistry)
    {
        this(resourceLoader, clock, observationRegistry,
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
    }

    SpringAiProviderIntegration(ResourceLoader resourceLoader, Clock clock, ObservationRegistry observationRegistry,
            ObjectMapper objectMapper)
    {
        this.resourceLoader = resourceLoader;
        this.clock = clock;
        this.observationRegistry = java.util.Objects.requireNonNull(observationRegistry,
                "observationRegistry must not be null");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public ProviderConnectionRuntime create(String connectionName, LoomspanProperties.ConnectionProperties properties)
    {
        ProviderRetryPolicy retry = ProviderRetryPolicy.from(properties.getProviderRetry());
        return ProviderConnectionRuntime.chat(switch (properties.getDriver())
        {
            case OPENAI -> openAi(properties);
            case ANTHROPIC -> anthropic(properties);
            case GEMINI -> gemini(connectionName, properties);
            case TYPESAFE -> throw new IllegalArgumentException("Native evaluations do not construct a ChatModel");
        }, properties.getDriver(), AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP, retry, this::translate);
    }

    private OpenAiResponsesChatModel openAi(LoomspanProperties.ConnectionProperties properties)
    {
        Map<String, String> headers = new java.util.LinkedHashMap<>(properties.getHeaders());
        OpenAiResponsesChatOptions.Builder optionsBuilder = OpenAiResponsesChatOptions.builder()
                .apiKey(properties.getApiKey())
                .maxRetries(0);
        if (properties.getRequestTimeout() != null) optionsBuilder.timeout(properties.getRequestTimeout());
        if (StringUtils.hasText(properties.getBaseUrl())) optionsBuilder.baseUrl(properties.getBaseUrl());
        LoomspanProperties.OpenAiOptions options = properties.getOpenai();
        if (options != null)
        {
            if (StringUtils.hasText(options.getOrganizationId())) optionsBuilder.organizationId(options.getOrganizationId());
            if (StringUtils.hasText(options.getProjectId())) headers.put("OpenAI-Project", options.getProjectId());
        }
        if (!headers.isEmpty()) optionsBuilder.customHeaders(headers);
        OpenAiResponsesChatModel.Builder builder = OpenAiResponsesChatModel.builder().options(optionsBuilder.build())
                .observationRegistry(observationRegistry);
        // Responses exceptions retain only a code/message for failed HTTP-200 envelopes.
        // Capture their original evidence at the existing HTTP boundary.
        builder.httpClientBuilderCustomizer(http -> http.interceptor(chain -> inspectResponses(chain.proceed(chain.request()))));
        return builder.build();
    }

    private AnthropicChatModel anthropic(LoomspanProperties.ConnectionProperties properties)
    {
        Map<String, String> headers = new java.util.LinkedHashMap<>(properties.getHeaders());
        // Preserve API-key authentication when the SDK's static credential default changes.
        if (headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("x-api-key")
                || name.equalsIgnoreCase("Authorization")))
            headers.put("x-api-key", properties.getApiKey());
        AnthropicChatOptions.Builder optionsBuilder = AnthropicChatOptions.builder()
                .apiKey(properties.getApiKey())
                .maxRetries(0)
                .customHeaders(headers);
        if (properties.getRequestTimeout() != null) optionsBuilder.timeout(properties.getRequestTimeout());
        if (StringUtils.hasText(properties.getBaseUrl())) optionsBuilder.baseUrl(properties.getBaseUrl());
        return AnthropicChatModel.builder().options(optionsBuilder.build())
                .observationRegistry(observationRegistry).build();
    }

    private GoogleGenAiChatModel gemini(String connectionName, LoomspanProperties.ConnectionProperties properties)
    {
        Client.Builder builder = Client.builder().httpOptions(oneAttemptGoogleHttpOptions(properties.getRequestTimeout()));
        LoomspanProperties.GeminiOptions options = properties.getGemini();
        if (StringUtils.hasText(properties.getApiKey())) builder.apiKey(properties.getApiKey());
        else
        {
            builder.vertexAI(true).project(options.getProjectId()).location(options.getLocation());
            if (StringUtils.hasText(options.getCredentialsUri())) builder.credentials(loadCredentials(connectionName, options.getCredentialsUri()));
            else if (StringUtils.hasText(options.getCredentialsJson())) builder.credentials(loadCredentialsJson(connectionName, options.getCredentialsJson()));
        }
        return GoogleGenAiChatModel.builder().genAiClient(builder.build()).retryTemplate(oneAttemptTemplate())
                .observationRegistry(observationRegistry).build();
    }

    static HttpOptions oneAttemptGoogleHttpOptions(Duration timeout)
    {
        var builder = HttpOptions.builder().retryOptions(HttpRetryOptions.builder().attempts(1).build());
        if (timeout != null) builder.timeout(Math.toIntExact(timeout.toMillis()));
        return builder.build();
    }

    private RetryTemplate oneAttemptTemplate()
    {
        return new RetryTemplate(RetryPolicy.withMaxRetries(0));
    }

    okhttp3.Response inspectResponses(okhttp3.Response response) throws IOException
    {
        okhttp3.ResponseBody body = response.body();
        if (!response.isSuccessful() || body == null) return response;
        okhttp3.MediaType contentType = body.contentType();
        byte[] bytes;
        try (body; InputStream stream = body.byteStream())
        {
            bytes = stream.readAllBytes();
        }
        inspectResponsesBody(bytes);
        return response.newBuilder().body(okhttp3.ResponseBody.create(contentType, bytes)).build();
    }

    private void inspectResponsesBody(byte[] bytes)
    {
        JsonNode root;
        try { root = objectMapper.readTree(bytes); }
        catch (tools.jackson.core.JacksonException failure) {
            var captured = decodingFailure("Malformed Responses JSON", bytes);
            captured.initCause(failure);
            throw captured;
        }
        if (root == null || !root.isObject()) throw decodingFailure("Malformed Responses envelope", bytes);
        JsonNode error = root.path("error");
        if (!"failed".equals(root.path("status").asText()) && !error.isObject()) return;
        if (!error.isObject()) throw decodingFailure("Malformed failed Responses envelope", bytes);
        String type = text(error.path("metadata").path("error_type"));
        if (type == null) type = text(error.path("error_type"));
        if (type == null) type = text(root.path("error_type"));
        if (type == null) type = text(error.path("type"));
        String code = text(error.path("code"));
        if (code == null) code = text(error.path("provider_code"));
        String summary = text(error.path("message"));
        ProviderFailureCategory category = category(type);
        ProviderFailureClassification classification = retryableResponses(type)
                ? ProviderFailureClassification.TRANSIENT : ProviderFailureClassification.PERMANENT;
        var usage = failedResponseUsage(root.path("usage"));
        throw new ProviderCallException("Provider returned a failed Responses envelope",
                new ProviderFailureDetails(classification, category, 200, null, type, code, summary,
                        List.of(diagnostic(bytes, bytes.length > DIAGNOSTIC_LIMIT_BYTES))),
                java.util.Collections.unmodifiableMap(objectMapper.convertValue(root, java.util.LinkedHashMap.class)), usage);
    }

    private ai.loomspan.internal.runtime.usage.ModelUsageRecord failedResponseUsage(JsonNode usage)
    {
        if (!usage.isObject() || !(usage.path("input_tokens").isIntegralNumber()
                || usage.path("output_tokens").isIntegralNumber() || usage.path("total_tokens").isIntegralNumber()))
            return null;
        try
        {
            int input = responseTokenCount(usage.path("input_tokens"));
            int output = responseTokenCount(usage.path("output_tokens"));
            var totalNode = usage.path("total_tokens");
            int total = totalNode.isMissingNode() || totalNode.isNull()
                    ? Math.addExact(input, output) : responseTokenCount(totalNode);
            return new ai.loomspan.internal.runtime.usage.ModelUsageRecord(input, output, total,
                    ai.loomspan.internal.runtime.usage.UsagePrecision.EXACT,
                    objectMapper.convertValue(usage, Map.class));
        }
        catch (IllegalArgumentException | ArithmeticException invalidUsage)
        {
            // Keep the original envelope and provider failure authoritative when its
            // counts cannot be represented. Never replace native values with narrowed ones.
            return null;
        }
    }

    private static int responseTokenCount(JsonNode count)
    {
        if (count.isMissingNode() || count.isNull()) return 0;
        if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0)
            throw new IllegalArgumentException("Response token count must be a nonnegative int");
        return count.intValue();
    }

    private ProviderFailureDetails translate(Throwable failure)
    {
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12 && seen.put(current, Boolean.TRUE) == null; depth++)
        {
            if (current instanceof ProviderCallException normalized) return normalized.details();
            if (current instanceof ApiException response)
            {
                return googleHttpFailure(response);
            }
            if (current instanceof AnthropicServiceException response)
            {
                byte[] body = String.valueOf(response.body()).getBytes(StandardCharsets.UTF_8);
                boolean truncated = body.length > DIAGNOSTIC_LIMIT_BYTES;
                if (truncated) body = java.util.Arrays.copyOf(body, DIAGNOSTIC_LIMIT_BYTES);
                String retryAfter = response.headers().values("Retry-After").stream().findFirst().orElse(null);
                return httpFailure(response.statusCode(), retryAfter, body, truncated);
            }
            if (current instanceof OpenAIServiceException response)
            {
                byte[] body = openAiBody(response.body());
                boolean truncated = body.length > DIAGNOSTIC_LIMIT_BYTES;
                if (truncated) body = java.util.Arrays.copyOf(body, DIAGNOSTIC_LIMIT_BYTES);
                String retryAfter = response.headers().values("Retry-After").stream().findFirst().orElse(null);
                ProviderFailureDetails base = httpFailure(response.statusCode(), retryAfter, body, truncated);
                return new ProviderFailureDetails(base.classification(), base.category(), base.httpStatus(),
                        base.retryAfter(), openAiOptional(response::type), openAiOptional(response::code),
                        base.summary(), base.diagnostics());
            }
            if (current instanceof RestClientResponseException response)
            {
                byte[] body = response.getResponseBodyAsByteArray();
                boolean truncated = body.length > DIAGNOSTIC_LIMIT_BYTES;
                if (truncated) body = java.util.Arrays.copyOf(body, DIAGNOSTIC_LIMIT_BYTES);
                return httpFailure(response.getStatusCode().value(),
                        response.getResponseHeaders() == null ? null : response.getResponseHeaders().getFirst("Retry-After"),
                        body, truncated);
            }
            if (current instanceof InterruptedException || current instanceof java.util.concurrent.CancellationException
                    || current instanceof SSLException)
            {
                return new ProviderFailureDetails(ProviderFailureClassification.PERMANENT,
                        ProviderFailureCategory.UNKNOWN, null, null, null, null, null, List.of());
            }
            if (current instanceof SocketTimeoutException)
            {
                return transientTransport(ProviderFailureCategory.TIMEOUT);
            }
            if (current instanceof InterruptedIOException && isProviderReadTimeout(failure))
            {
                return transientTransport(ProviderFailureCategory.TIMEOUT);
            }
            if (current instanceof ConnectException || current instanceof SocketException
                    || current instanceof EOFException || current instanceof UnknownHostException)
            {
                return transientTransport(ProviderFailureCategory.CONNECTIVITY);
            }
            current = current.getCause();
        }
        return ProviderFailureDetails.unknown();
    }

    private byte[] openAiBody(com.openai.core.JsonValue body)
    {
        try
        {
            return objectMapper.writeValueAsBytes(body.convert(Object.class));
        }
        catch (RuntimeException ignored)
        {
            try
            {
                return objectMapper.writeValueAsBytes(String.valueOf(body));
            }
            catch (tools.jackson.core.JacksonException impossible)
            {
                return new byte[0];
            }
        }
    }

    private static String openAiOptional(java.util.function.Supplier<java.util.Optional<String>> accessor)
    {
        try
        {
            return accessor.get().orElse(null);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private boolean isProviderReadTimeout(Throwable failure)
    {
        if (Thread.currentThread().isInterrupted()) return false;
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        boolean timeout = false;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12 && seen.put(current, Boolean.TRUE) == null; depth++)
        {
            if (current instanceof CancellationException || current instanceof InterruptedException) return false;
            if (current instanceof InterruptedIOException interrupted)
            {
                String message = interrupted.getMessage();
                if (message != null)
                {
                    String normalized = message.toLowerCase(Locale.ROOT);
                    timeout |= normalized.contains("timeout") || normalized.contains("timed out");
                }
            }
            current = current.getCause();
        }
        return timeout;
    }

    private ProviderFailureDetails googleHttpFailure(ApiException response)
    {
        byte[] bytes;
        try
        {
            bytes = objectMapper.writeValueAsBytes(Map.of(
                    "status", response.status() == null ? "" : response.status(),
                    "message", response.message() == null ? "" : response.message()));
        }
        catch (tools.jackson.core.JacksonException impossible)
        {
            bytes = new byte[0];
        }
        boolean truncated = bytes.length > DIAGNOSTIC_LIMIT_BYTES;
        if (truncated) bytes = java.util.Arrays.copyOf(bytes, DIAGNOSTIC_LIMIT_BYTES);
        ProviderFailureDetails base = httpFailure(response.code(), null, bytes, truncated);
        return new ProviderFailureDetails(base.classification(), base.category(), base.httpStatus(), null,
                null, response.status(), null, base.diagnostics());
    }

    private ProviderFailureDetails transientTransport(ProviderFailureCategory category)
    {
        return new ProviderFailureDetails(ProviderFailureClassification.TRANSIENT, category,
                null, null, null, null, null, List.of());
    }

    private ProviderFailureDetails httpFailure(int status, String retryAfterValue, byte[] bytes, boolean truncated)
    {
        Duration retryAfter = parseRetryAfter(retryAfterValue);
        ProviderFailureClassification classification = java.util.Set.of(408, 429, 500, 502, 503, 504).contains(status)
                ? ProviderFailureClassification.TRANSIENT : ProviderFailureClassification.PERMANENT;
        ProviderFailureCategory category = status == 429 ? ProviderFailureCategory.RATE_LIMITED
                : status == 401 ? ProviderFailureCategory.AUTHENTICATION
                : status == 403 ? ProviderFailureCategory.AUTHORIZATION
                : status == 402 ? ProviderFailureCategory.PAYMENT_REQUIRED
                : status >= 500 ? ProviderFailureCategory.SERVER_ERROR : ProviderFailureCategory.INVALID_REQUEST;
        return new ProviderFailureDetails(classification, category, status, retryAfter, null, null, null,
                List.of(diagnostic(bytes, truncated)));
    }

    private ProviderCallException decodingFailure(String message, byte[] body)
    {
        return new ProviderCallException(message,
                new ProviderFailureDetails(ProviderFailureClassification.PERMANENT,
                        ProviderFailureCategory.CLIENT_DECODING, 200, null, null, null, message,
                        List.of(diagnostic(body, body.length > DIAGNOSTIC_LIMIT_BYTES))));
    }

    private static Map<String, Object> diagnostic(byte[] body, boolean truncated)
    {
        String text = new String(body, StandardCharsets.UTF_8);
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > DIAGNOSTIC_LIMIT_BYTES)
        {
            int end = DIAGNOSTIC_LIMIT_BYTES;
            while (end > 0 && (encoded[end] & 0xc0) == 0x80) end--;
            text = new String(encoded, 0, end, StandardCharsets.UTF_8);
            truncated = true;
        }
        return Map.of("kind", "PROVIDER_ERROR", "contentType", "application/json; charset=utf-8",
                "text", text, "truncated", truncated,
                "captureLimitBytes", DIAGNOSTIC_LIMIT_BYTES);
    }

    private static boolean retryableResponses(String type)
    {
        return type != null && java.util.Set.of("rate_limit_exceeded", "provider_overloaded",
                "provider_unavailable", "timeout", "server", "unmapped").contains(type);
    }

    private static ProviderFailureCategory category(String type)
    {
        if (type == null) return ProviderFailureCategory.UNKNOWN;
        return switch (type)
        {
            case "rate_limit_exceeded" -> ProviderFailureCategory.RATE_LIMITED;
            case "provider_overloaded" -> ProviderFailureCategory.PROVIDER_OVERLOADED;
            case "provider_unavailable", "unmapped" -> ProviderFailureCategory.PROVIDER_UNAVAILABLE;
            case "timeout" -> ProviderFailureCategory.TIMEOUT;
            case "server" -> ProviderFailureCategory.SERVER_ERROR;
            default -> ProviderFailureCategory.UNKNOWN;
        };
    }

    private static String text(JsonNode node) { return node.isTextual() && !node.asText().isBlank() ? node.asText() : null; }

    private GoogleCredentials loadCredentials(String connectionName, String uri)
    {
        try (InputStream input = resourceLoader.getResource(uri).getInputStream())
        {
            return GoogleCredentials.fromStream(input);
        }
        catch (IOException ex)
        {
            throw new SafeAiConnectionConfigurationException("loomspan.connections." + connectionName
                    + ".gemini.credentials-uri could not be loaded");
        }
    }

    private GoogleCredentials loadCredentialsJson(String connectionName, String json)
    {
        try (InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        {
            return ServiceAccountCredentials.fromStream(input);
        }
        catch (IOException | RuntimeException ex)
        {
            throw new SafeAiConnectionConfigurationException("loomspan.connections." + connectionName
                    + ".gemini.credentials-json-ref is invalid");
        }
    }

    private Duration parseRetryAfter(String value)
    {
        if (!StringUtils.hasText(value)) return null;
        try
        {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        }
        catch (RuntimeException ignored)
        {
            try
            {
                Duration delay = Duration.between(clock.instant(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return delay.isNegative() || delay.isZero() ? null : delay;
            }
            catch (RuntimeException invalid) { return null; }
        }
    }

}
