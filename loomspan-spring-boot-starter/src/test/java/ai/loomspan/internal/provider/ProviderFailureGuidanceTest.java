package ai.loomspan.internal.provider;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureGuidanceTest
{
    private static final ModelExecutionIdentity IDENTITY =
            new ModelExecutionIdentity("support-model", "primary-openai", AiDriver.OPENAI, "gpt-example");

    @Test
    void formatsConservativeGuidanceForNormalizedFailureCategories()
    {
        var authentication = guidance(ProviderFailureCategory.AUTHENTICATION, 401);
        assertThat(authentication).contains("support-model", "primary-openai", "OPENAI", "gpt-example",
                "loomspan.connections.primary-openai.api-key", "rejected").doesNotContain("missing");

        var notFound = guidance(ProviderFailureCategory.INVALID_REQUEST, 404);
        assertThat(notFound).contains("loomspan.connections.primary-openai.base-url",
                "loomspan.models.support-model.provider-model", "may be involved")
                .doesNotContain("base-url.base-url", "model does not exist", "model is nonexistent");

        var connectivity = guidance(ProviderFailureCategory.CONNECTIVITY, null);
        assertThat(connectivity).contains("network reachability", "loomspan.connections.primary-openai.base-url",
                "does not prove").doesNotContain("bad URL");

        assertThat(guidance(ProviderFailureCategory.AUTHORIZATION, 403))
                .contains("denied access", "loomspan.connections.primary-openai.api-key",
                        "loomspan.models.support-model.provider-model");
        assertThat(guidance(ProviderFailureCategory.TIMEOUT, null))
                .contains("timed out", "network reachability", "loomspan.connections.primary-openai.base-url");
        assertThat(guidance(ProviderFailureCategory.RATE_LIMITED, 429))
                .contains("rate-limited", "loomspan.connections.primary-openai.provider-retry");
        for (ProviderFailureCategory category : List.of(ProviderFailureCategory.PROVIDER_OVERLOADED,
                ProviderFailureCategory.PROVIDER_UNAVAILABLE, ProviderFailureCategory.SERVER_ERROR))
        {
            assertThat(guidance(category, 503)).contains("provider status",
                    "loomspan.connections.primary-openai.base-url",
                    "loomspan.models.support-model.provider-model");
        }
        assertThat(guidance(ProviderFailureCategory.UNKNOWN, null))
                .contains("unclassified", "provider explanation", "loomspan.connections.primary-openai",
                        "loomspan.models.support-model.provider-model");
    }

    @Test
    void exposesAValidBoundedPlainTextDiagnostic()
    {
        var guidance = ProviderFailureGuidance.explain(IDENTITY,
                details(ProviderFailureCategory.UNKNOWN, null));

        assertThat(guidance.diagnostic()).containsEntry("kind", "LOOMSPAN_PROVIDER_GUIDANCE")
                .containsEntry("contentType", "text/plain; charset=utf-8")
                .containsEntry("text", guidance.text())
                .containsEntry("truncated", false)
                .containsEntry("captureLimitBytes", ProviderFailureGuidance.CAPTURE_LIMIT_BYTES);
    }

    @Test
    void boundsGuidanceByUtf8Bytes()
    {
        var guidance = ProviderFailureGuidance.explain(
                new ModelExecutionIdentity("support-model", "primary-openai", AiDriver.OPENAI,
                        "model-" + "é".repeat(ProviderFailureGuidance.CAPTURE_LIMIT_BYTES)),
                details(ProviderFailureCategory.UNKNOWN, null));

        assertThat(guidance.text().getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ProviderFailureGuidance.CAPTURE_LIMIT_BYTES);
        assertThat(guidance.diagnostic()).containsEntry("truncated", true)
                .containsEntry("text", guidance.text());
    }

    @Test
    void namesDriverRelevantSettingsAcrossSupportedProviderIdentities()
    {
        assertThat(authenticationGuidance(AiDriver.OPENAI, "openai"))
                .contains("driver OPENAI", "loomspan.connections.openai.api-key");
        assertThat(authenticationGuidance(AiDriver.ANTHROPIC, "anthropic"))
                .contains("driver ANTHROPIC", "loomspan.connections.anthropic.api-key");
        assertThat(authenticationGuidance(AiDriver.GEMINI, "gemini"))
                .contains("driver GEMINI", "loomspan.connections.gemini.api-key",
                        "loomspan.connections.gemini.gemini.project-id",
                        "loomspan.connections.gemini.gemini.location",
                        "loomspan.connections.gemini.gemini.credentials-uri")
                .doesNotContain("gemini.gemini Vertex");
        assertThat(authenticationGuidance(AiDriver.OLLAMA, "ollama"))
                .contains("driver OLLAMA", "loomspan.connections.ollama.base-url");

        String geminiConnectivity = ProviderFailureGuidance.explain(
                new ModelExecutionIdentity("support-model", "gemini", AiDriver.GEMINI, "provider-model"),
                details(ProviderFailureCategory.CONNECTIVITY, null)).text();
        assertThat(geminiConnectivity)
                .contains("loomspan.connections.gemini.api-key",
                        "loomspan.connections.gemini.gemini.vertex-ai",
                        "loomspan.connections.gemini.gemini.project-id",
                        "loomspan.connections.gemini.gemini.location")
                .doesNotContain("loomspan.connections.gemini.base-url");
        String geminiNotFound = ProviderFailureGuidance.explain(
                new ModelExecutionIdentity("support-model", "gemini", AiDriver.GEMINI, "provider-model"),
                details(ProviderFailureCategory.INVALID_REQUEST, 404)).text();
        assertThat(geminiNotFound)
                .contains("loomspan.connections.gemini.gemini.vertex-ai",
                        "loomspan.models.support-model.provider-model")
                .doesNotContain("loomspan.connections.gemini.base-url", "base-url.base-url");
    }

    private static String authenticationGuidance(AiDriver driver, String connection)
    {
        return ProviderFailureGuidance.explain(
                new ModelExecutionIdentity("support-model", connection, driver, "provider-model"),
                details(ProviderFailureCategory.AUTHENTICATION, 401)).text();
    }

    private static String guidance(ProviderFailureCategory category, Integer status)
    {
        return ProviderFailureGuidance.explain(IDENTITY, details(category, status)).text();
    }

    private static ProviderFailureDetails details(ProviderFailureCategory category, Integer status)
    {
        return new ProviderFailureDetails(ProviderFailureClassification.PERMANENT, category,
                status, null, null, null, null, List.of());
    }
}
