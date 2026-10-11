package ai.loomspan.internal.typesafe;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.MissionLifecycle;
import ai.loomspan.internal.provider.*;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.FutureTask;

/**
 * One connection-owned native System One transport. Success bodies have no diagnostic byte ceiling.
 */
public final class TypeSafeClient implements AutoCloseable {
    static final int ERROR_LIMIT_BYTES = 1024 * 1024;
    private final CloseableHttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final Map<String, String> headers;
    private final RequestConfig requestConfig;
    private final ConnectionConfig connectionConfig;

    public TypeSafeClient(LoomspanProperties.ConnectionProperties properties) {
        String prefix =
                properties.getBaseUrl() == null
                        ? "https://api.typesafe.ai"
                        : properties.getBaseUrl();
        endpoint = URI.create(prefix.replaceAll("/+$", "") + "/v1/systemone");
        apiKey = Objects.requireNonNull(properties.getApiKey());
        headers = Map.copyOf(properties.getHeaders());
        var request = RequestConfig.custom();
        var connection = ConnectionConfig.custom();
        if (properties.getRequestTimeout() != null) {
            Timeout timeout = Timeout.ofMilliseconds(properties.getRequestTimeout().toMillis());
            request.setResponseTimeout(timeout);
            connection.setSocketTimeout(timeout);
        }
        requestConfig = request.build();
        connectionConfig = connection.build();
        var manager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();
        client =
                HttpClients.custom()
                        .setConnectionManager(manager)
                        .setDefaultRequestConfig(requestConfig)
                        .disableAutomaticRetries()
                        .disableRedirectHandling()
                        .build();
    }

    public URI endpoint() {
        return endpoint;
    }

    public String send(String body, MissionLifecycle lifecycle) {
        HttpPost request = new HttpPost(endpoint);
        request.setConfig(requestConfig);
        request.setHeader("Authorization", "Bearer " + apiKey);
        headers.forEach(request::setHeader);
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        FutureTask<Void> handle =
                new FutureTask<>(() -> null) {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        request.cancel();
                        return super.cancel(interrupt);
                    }
                };
        if (lifecycle != null) lifecycle.registerPhysicalFuture(handle);
        try {
            return client.execute(
                    request,
                    response -> {
                        int status = response.getCode();
                        try (var stream =
                                response.getEntity() == null
                                        ? java.io.InputStream.nullInputStream()
                                        : response.getEntity().getContent()) {
                            if (status >= 200 && status < 300)
                                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                            byte[] captured = stream.readNBytes(ERROR_LIMIT_BYTES + 1);
                            boolean truncated = captured.length > ERROR_LIMIT_BYTES;
                            int end = Math.min(captured.length, ERROR_LIMIT_BYTES);
                            if (truncated) while (end > 0 && (captured[end] & 0xc0) == 0x80) end--;
                            String text = new String(captured, 0, end, StandardCharsets.UTF_8);
                            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
                            if (encoded.length > ERROR_LIMIT_BYTES) {
                                end = ERROR_LIMIT_BYTES;
                                while (end > 0 && (encoded[end] & 0xc0) == 0x80) end--;
                                text = new String(encoded, 0, end, StandardCharsets.UTF_8);
                                truncated = true;
                            }
                            var retryAfter = response.getFirstHeader("Retry-After");
                            var contentType = response.getFirstHeader("Content-Type");
                            // Closing an Apache entity stream can otherwise drain an arbitrarily
                            // long error body.
                            if (truncated) request.cancel();
                            throw new HttpFailure(
                                    status,
                                    text,
                                    truncated,
                                    retryAfter == null ? null : retryAfter.getValue(),
                                    contentType == null
                                            ? "text/plain; charset=utf-8"
                                            : contentType.getValue());
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("System One HTTP execution failed", ex);
        } finally {
            if (lifecycle != null) lifecycle.unregisterPhysicalFuture(handle);
        }
    }

    public static ProviderFailureDetails translate(Throwable failure) {
        if (failure instanceof HttpFailure http) {
            boolean transientStatus =
                    Set.of(408, 429, 500, 502, 503, 504, 529).contains(http.status);
            ProviderFailureCategory category =
                    switch (http.status) {
                        case 401 -> ProviderFailureCategory.AUTHENTICATION;
                        case 403 -> ProviderFailureCategory.AUTHORIZATION;
                        case 402 -> ProviderFailureCategory.PAYMENT_REQUIRED;
                        case 408 -> ProviderFailureCategory.TIMEOUT;
                        case 429 -> ProviderFailureCategory.RATE_LIMITED;
                        case 529 -> ProviderFailureCategory.PROVIDER_OVERLOADED;
                        case 502, 503, 504 -> ProviderFailureCategory.PROVIDER_UNAVAILABLE;
                        default ->
                                http.status >= 500
                                        ? ProviderFailureCategory.SERVER_ERROR
                                        : ProviderFailureCategory.INVALID_REQUEST;
                    };
            return new ProviderFailureDetails(
                    transientStatus
                            ? ProviderFailureClassification.TRANSIENT
                            : ProviderFailureClassification.PERMANENT,
                    category,
                    http.status,
                    retryAfter(http.retryAfter),
                    null,
                    null,
                    http.getMessage(),
                    List.of(
                            Map.of(
                                    "kind",
                                    "PROVIDER_ERROR",
                                    "contentType",
                                    http.contentType,
                                    "text",
                                    http.body,
                                    "truncated",
                                    http.truncated,
                                    "captureLimitBytes",
                                    ERROR_LIMIT_BYTES)));
        }
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return new ProviderFailureDetails(
                cause instanceof IOException
                        ? ProviderFailureClassification.TRANSIENT
                        : ProviderFailureClassification.PERMANENT,
                cause instanceof SocketTimeoutException
                        ? ProviderFailureCategory.TIMEOUT
                        : cause instanceof IOException
                                ? ProviderFailureCategory.CONNECTIVITY
                                : ProviderFailureCategory.UNKNOWN,
                null,
                null,
                null,
                null,
                failure.getMessage(),
                List.of());
    }

    private static Duration retryAfter(String value) {
        if (value == null) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (RuntimeException ignored) {
            try {
                return Duration.ofMillis(
                        Math.max(
                                0,
                                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                                .toInstant()
                                                .toEpochMilli()
                                        - System.currentTimeMillis()));
            } catch (RuntimeException invalid) {
                return null;
            }
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private static final class HttpFailure extends RuntimeException {
        final int status;
        final String body;
        final boolean truncated;
        final String retryAfter;
        final String contentType;

        HttpFailure(
                int status, String body, boolean truncated, String retryAfter, String contentType) {
            super("System One returned HTTP " + status);
            this.status = status;
            this.body = body;
            this.truncated = truncated;
            this.retryAfter = retryAfter;
            this.contentType = contentType;
        }
    }
}
