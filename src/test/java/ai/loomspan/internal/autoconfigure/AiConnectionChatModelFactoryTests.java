package ai.loomspan.internal.autoconfigure;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.springai.SpringAiProviderIntegration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel;

import static org.assertj.core.api.Assertions.assertThat;

class AiConnectionChatModelFactoryTests {

    @Test
    void constructsDistinctOpenAiAndOllamaModelsPerConnection() {
        LoomspanProperties.ConnectionProperties openAi = new LoomspanProperties.ConnectionProperties();
        openAi.setDriver(AiDriver.OPENAI);
        openAi.setApiKey("test-key");
        SpringAiProviderIntegration integration = integration();
        assertThat(integration.create("one", openAi).chatModel()).isInstanceOf(OpenAiResponsesChatModel.class)
                .isNotSameAs(integration.create("two", openAi).chatModel());

        LoomspanProperties.ConnectionProperties ollama = new LoomspanProperties.ConnectionProperties();
        ollama.setDriver(AiDriver.OPENAI);
        ollama.setApiKey("ollama");
        ollama.setBaseUrl("http://localhost:11434/v1");
        assertThat(integration.create("one", ollama).chatModel()).isInstanceOf(OpenAiResponsesChatModel.class)
                .isNotSameAs(integration.create("two", ollama).chatModel());
    }

    @Test
    void constructsAnthropicAndBothGeminiCredentialModes() {
        LoomspanProperties.ConnectionProperties anthropic = new LoomspanProperties.ConnectionProperties();
        anthropic.setDriver(AiDriver.ANTHROPIC);
        anthropic.setApiKey("test-key");
        assertThat(integration().create("anthropic", anthropic).chatModel())
                .isInstanceOf(AnthropicChatModel.class);

        SpringAiProviderIntegration geminiFactory = new SpringAiProviderIntegration();
        LoomspanProperties.ConnectionProperties apiKeyGemini = new LoomspanProperties.ConnectionProperties();
        apiKeyGemini.setDriver(AiDriver.GEMINI);
        apiKeyGemini.setApiKey("test-key");
        assertThat(geminiFactory.create("gemini-key", apiKeyGemini).chatModel()).isInstanceOf(GoogleGenAiChatModel.class);

        LoomspanProperties.ConnectionProperties vertexGemini = new LoomspanProperties.ConnectionProperties();
        vertexGemini.setDriver(AiDriver.GEMINI);
        LoomspanProperties.GeminiOptions vertex = new LoomspanProperties.GeminiOptions();
        vertex.setVertexAi(true);
        vertex.setProjectId("test-project");
        vertex.setLocation("us-central1");
        vertexGemini.setGemini(vertex);
        var defaultCredentials = com.google.auth.oauth2.GoogleCredentials.create(
                new com.google.auth.oauth2.AccessToken("fixture-token", new java.util.Date(Long.MAX_VALUE)));
        try (var defaults = org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class)) {
            defaults.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault)
                    .thenReturn(defaultCredentials);
            assertThat(geminiFactory.create("gemini-vertex", vertexGemini).chatModel()).isInstanceOf(GoogleGenAiChatModel.class);
            defaults.verify(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault);
        }
    }

    private static SpringAiProviderIntegration integration() {
        return new SpringAiProviderIntegration();
    }
}
