package ai.loomspan.internal.provider;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.ModelExecutionIdentity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Builds bounded, framework-owned guidance from normalized provider failure facts. */
public final class ProviderFailureGuidance
{
    public static final int CAPTURE_LIMIT_BYTES = 8 * 1024;

    private ProviderFailureGuidance() {}

    public static Guidance explain(ModelExecutionIdentity identity, ProviderFailureDetails details)
    {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(details, "details must not be null");
        String connection = "loomspan.connections." + identity.connection();
        String model = "loomspan.models." + identity.frameworkModel() + ".provider-model";
        String credential = credentialProperty(identity.driver(), connection);
        String endpoint = endpointProperty(identity.driver(), connection);
        String advice = switch (details.category())
        {
            case AUTHENTICATION -> "The provider rejected the request credentials. Check " + credential + ".";
            case AUTHORIZATION -> "The provider denied access. Check " + credential + " and whether " + model
                    + " is accessible to this account.";
            case CONNECTIVITY -> "The provider could not be reached. Check network reachability and " + endpoint
                    + "; this does not prove that the configured endpoint is wrong.";
            case TIMEOUT -> "The provider request timed out. Check provider availability, network reachability, and "
                    + endpoint + ".";
            case INVALID_REQUEST -> invalidRequestAdvice(details.httpStatus(), endpoint, model);
            case RATE_LIMITED -> "The provider rate-limited the request. Check account limits and retry settings under "
                    + connection + ".provider-retry.";
            case PROVIDER_OVERLOADED, PROVIDER_UNAVAILABLE, SERVER_ERROR ->
                    "The provider was unavailable or returned a server error. Check provider status, "
                            + endpoint + ", and " + model + ".";
            case PAYMENT_REQUIRED -> "The provider rejected the request for account or billing reasons. Check the account for "
                    + connection + " and access to " + model + ".";
            case CONTEXT_LIMIT, CONTENT_POLICY, CLIENT_DECODING ->
                    "The provider rejected or could not process the request. Check " + model
                            + " and the provider explanation in the attempt diagnostics.";
            case UNKNOWN -> "The provider request failed for an unclassified reason. Check " + connection + ", " + model
                    + ", and the provider explanation in the attempt diagnostics.";
        };
        String message = "Loomspan provider failure for framework model '" + identity.frameworkModel()
                + "', connection '" + identity.connection() + "', driver " + identity.driver()
                + ", provider model '" + identity.providerModel() + "'. " + advice;
        boolean truncated = message.getBytes(StandardCharsets.UTF_8).length > CAPTURE_LIMIT_BYTES;
        if (truncated) message = truncateUtf8(message, CAPTURE_LIMIT_BYTES);
        return new Guidance(message, Map.of(
                "kind", "LOOMSPAN_PROVIDER_GUIDANCE",
                "contentType", "text/plain; charset=utf-8",
                "text", message,
                "truncated", truncated,
                "captureLimitBytes", CAPTURE_LIMIT_BYTES));
    }

    private static String invalidRequestAdvice(Integer status, String endpoint, String model)
    {
        String prefix = status != null && status == 404
                ? "The provider returned HTTP 404; the endpoint path, model identifier, or account access may be involved. "
                : "The provider rejected the request; endpoint configuration, model identifier, or account access may be involved. ";
        return prefix + "Check " + endpoint + " and " + model + ".";
    }

    private static String credentialProperty(AiDriver driver, String connection)
    {
        return driver == AiDriver.GEMINI
                ? connection + ".api-key or the Vertex AI settings " + connection + ".gemini.project-id, "
                        + connection + ".gemini.location, and " + connection + ".gemini.credentials-uri"
                : driver == AiDriver.OLLAMA ? connection + ".base-url" : connection + ".api-key";
    }

    private static String endpointProperty(AiDriver driver, String connection)
    {
        return driver == AiDriver.GEMINI
                ? "the Gemini mode settings " + connection + ".api-key, " + connection + ".gemini.vertex-ai, "
                        + connection + ".gemini.project-id, and " + connection + ".gemini.location"
                : connection + ".base-url";
    }

    private static String truncateUtf8(String value, int limit)
    {
        int end = 0;
        int byteCount = 0;
        while (end < value.length())
        {
            int codePoint = value.codePointAt(end);
            int codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + codePointBytes > limit) break;
            byteCount += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    public record Guidance(String text, Map<String, Object> diagnostic) {}
}
