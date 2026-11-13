package ai.loomspan.internal.provider;

import ai.loomspan.autoconfigure.AiDriver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ProviderResponseStatusClassifierTest {
    @Test void everyPinnedStatusHasAnExplicitDecisionAcrossAllGenerations() {
        for (var driver : List.of(AiDriver.OPENAI, AiDriver.ANTHROPIC, AiDriver.GEMINI)) {
            var rejected = switch (driver) {
                case OPENAI -> List.of("LENGTH", "CONTENT_FILTER", "INCOMPLETE");
                case ANTHROPIC -> List.of("max_tokens", "model_context_window_exceeded", "pause_turn", "refusal");
                default -> List.of("MAX_TOKENS", "SAFETY", "RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII");
            };
            for (String status : rejected) {
                var response = new ChatResponse(List.of(generation(""), generation(status)));
                assertThat(ProviderResponseStatusClassifier.rejection(driver, response)).isEqualTo(status);
                assertThatThrownBy(() -> ProviderResponseStatusClassifier.requireAccepted(driver, response))
                        .isInstanceOf(ProviderResponseRejectedException.class);
            }
            for (String status : List.of("", "STOP", "TOOL_CALLS", "tool_use", "end_turn", "stop_sequence", "future_status"))
                assertThat(ProviderResponseStatusClassifier.rejection(driver, new ChatResponse(List.of(generation(status))))).isNull();
        }
    }

    @Test void explicitResponsesStatusRefusalAndItemStatusAreIndependentOfText() {
        var generation = generation("STOP");
        var response = new ChatResponse(List.of(generation), ChatResponseMetadata.builder()
                .keyValue("openai.responses.status", "incomplete").build());
        assertThat(ProviderResponseStatusClassifier.rejection(AiDriver.OPENAI, response)).isEqualTo("incomplete");
        var refusal = new Generation(new AssistantMessage("valid text"), ChatGenerationMetadata.builder()
                .finishReason("STOP").metadata("refusal", "ORIGINAL REFUSAL").build());
        assertThat(ProviderResponseStatusClassifier.rejection(AiDriver.OPENAI, new ChatResponse(List.of(refusal))))
                .isEqualTo("refusal");
        var item = AssistantMessage.builder().parts(List.of(new org.springframework.ai.chat.messages.part.TextPart(
                "{}", null, Map.of("itemStatus", "incomplete")))).build();
        assertThat(ProviderResponseStatusClassifier.rejection(AiDriver.OPENAI,
                new ChatResponse(List.of(new Generation(item))))).isEqualTo("incomplete output item");
        assertThat(ProviderResponseStatusClassifier.rejection(AiDriver.GEMINI, response)).isNull();
    }

    private static Generation generation(String finish) {
        return new Generation(new AssistantMessage("I cannot help with that"),
                ChatGenerationMetadata.builder().finishReason(finish).build());
    }
}
