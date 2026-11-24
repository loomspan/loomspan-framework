package ai.loomspan.internal.springai;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrdinaryFinalOutputRepairTest {
    @Test void replacesOnlyOwnedMessagesAndRetainsLatestRawObjectAndTypedOptions() {
        for (ToolCallingChatOptions options : List.of(
                org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().maxOutputTokens(234).reasoningEffort("high")
                        .hostedTools(org.springframework.ai.openai.responses.HostedTool.WebSearch.of()).build(),
                org.springframework.ai.anthropic.AnthropicChatOptions.builder().maxTokens(234).build(),
                org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder().maxOutputTokens(234).googleSearchRetrieval(true).build())) {
            var state = new OrdinaryFinalOutputRepair();
            var original = new UserMessage("The previous response RESULT must not be inferred as replay");
            var request = request(state, List.of(new SystemMessage("baseline"), original), options);
            var raw = AssistantMessage.builder().content("RAW_ONE").properties(Map.of("opaque", "kept")).build();
            state.captureRaw(raw);
            var first = OrdinaryFinalOutputRepair.repair(request, UnaryOperator.identity(), List.of(new UserMessage("old schema hint")));
            state.captureRaw(new AssistantMessage("RAW_TWO"));
            var second = OrdinaryFinalOutputRepair.repair(first,
                    prompt -> prompt.augmentSystemMessage(system -> system.mutate().text(system.getText() + " feedback").build()), List.of());
            assertThat(second.prompt().getInstructions()).hasSize(3);
            assertThat(second.prompt().getInstructions().get(1)).isSameAs(original);
            assertThat(second.prompt().getInstructions().get(2).getText()).isEqualTo("RAW_TWO");
            assertThat(first.prompt().getInstructions().get(2)).isSameAs(raw);
            assertThat(second.prompt().getOptions()).isExactlyInstanceOf(options.getClass());
            assertThat(second.prompt().getOptions().getMaxTokens()).isEqualTo(234);
            assertThat(((ToolCallingChatOptions)second.prompt().getOptions()).getToolCallbacks()).isEmpty();
            if (options instanceof org.springframework.ai.openai.responses.OpenAiResponsesChatOptions typed) {
                assertThat(typed.getHostedTools()).hasSize(1);
                assertThat(((org.springframework.ai.openai.responses.OpenAiResponsesChatOptions)second.prompt().getOptions()).getHostedTools()).isEmpty();
            }
            if (options instanceof org.springframework.ai.google.genai.GoogleGenAiChatOptions typed) {
                assertThat(typed.getGoogleSearchRetrieval()).isTrue();
                assertThat(((org.springframework.ai.google.genai.GoogleGenAiChatOptions)second.prompt().getOptions()).getGoogleSearchRetrieval()).isFalse();
            }
            assertThat(request.prompt().getInstructions()).hasSize(2);
        }
    }

    @Test void cannotSpliceAnotherCallsOwnedSegmentOrSilentlyUseComposedText() {
        var state = new OrdinaryFinalOutputRepair();
        var request = request(state, List.of(new UserMessage("input")), ToolCallingChatOptions.builder().build());
        assertThatThrownBy(() -> OrdinaryFinalOutputRepair.repair(request, UnaryOperator.identity(), List.of()))
                .hasMessageContaining("raw provider reply");
        state.captureRaw(new AssistantMessage("raw"));
        var repaired = OrdinaryFinalOutputRepair.repair(request, UnaryOperator.identity(), List.of());
        var other = new OrdinaryFinalOutputRepair();
        other.captureRaw(new AssistantMessage("other"));
        assertThatThrownBy(() -> OrdinaryFinalOutputRepair.repair(repaired.mutate()
                .context(OrdinaryFinalOutputRepair.CONTEXT_KEY, other).build(), UnaryOperator.identity(), List.of()))
                .hasMessageContaining("ownership");
        var changed = repaired.mutate().prompt(new Prompt(List.of(new UserMessage("input"), new AssistantMessage("raw")), repaired.prompt().getOptions())).build();
        assertThatThrownBy(() -> OrdinaryFinalOutputRepair.repair(changed, UnaryOperator.identity(), List.of()))
                .hasMessageContaining("outside its owner");
        OrdinaryFinalOutputRepair.capture(request, ChatClientResponse.builder().chatResponse(new ChatResponse(List.of())).build());
        assertThatThrownBy(() -> OrdinaryFinalOutputRepair.repair(request, UnaryOperator.identity(), List.of()))
                .hasMessageContaining("raw provider reply");
    }

    @Test void completedHistoryRetainsOriginalObjectsAndAppendsOnlyNewExchange() {
        var state = new OrdinaryFinalOutputRepair();
        var prompt = new Prompt("input");
        var assistant = AssistantMessage.builder().toolCalls(List.of(new AssistantMessage.ToolCall("one", "function", "test-tool", "{}"))).build();
        var resultMessage = ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("one", "test-tool", "RESULT"))).build();
        var result = mock(ToolExecutionResult.class);
        when(result.conversationHistory()).thenReturn(List.of(prompt.getInstructions().getFirst(), assistant, resultMessage));
        state.completed(prompt, result);
        state.captureRaw(new AssistantMessage("RAW"));
        var repaired = OrdinaryFinalOutputRepair.repair(request(state, prompt.getInstructions(), ToolCallingChatOptions.builder().build()), UnaryOperator.identity(), List.of());
        assertThat(repaired.prompt().getInstructions().get(1)).isSameAs(assistant);
        assertThat(repaired.prompt().getInstructions().get(2)).isSameAs(resultMessage);
        assertThatThrownBy(() -> state.completed(new Prompt("other"), result)).hasMessageContaining("request prefix");
    }

    @Test void terminalFlagRejectsWholeResponseBeforeAuthorizationOrDelegateEffects() {
        var state = new OrdinaryFinalOutputRepair();
        state.captureRaw(new AssistantMessage("rejected"));
        var options = ToolCallingChatOptions.builder().toolContext(OrdinaryFinalOutputRepair.CONTEXT_KEY, state).build();
        OrdinaryFinalOutputRepair.repair(request(state, List.of(new UserMessage("input")), options), UnaryOperator.identity(), List.of());
        var delegate = mock(ToolCallingManager.class);
        var manager = new RequestBoundToolCallingManager(delegate);
        for (String name : List.of("test-authorized", "unbound", PlannerInabilityTool.NAME)) {
            var assistant = AssistantMessage.builder().toolCalls(List.of(new AssistantMessage.ToolCall("id", "function", name, "{}"))).build();
            assertThatThrownBy(() -> manager.executeToolCalls(new Prompt("input", options), new ChatResponse(List.of(new Generation(assistant)))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("prohibited");
        }
        var mixed = AssistantMessage.builder().toolCalls(List.of(
                new AssistantMessage.ToolCall("first", "function", "test-authorized", "{}"),
                new AssistantMessage.ToolCall("second", "function", "unbound", "{}"),
                new AssistantMessage.ToolCall("control", "function", PlannerInabilityTool.NAME, "{}"))).build();
        assertThatThrownBy(() -> manager.executeToolCalls(new Prompt("input", options), new ChatResponse(List.of(new Generation(mixed)))))
                .hasMessageContaining("prohibited");
        verifyNoInteractions(delegate);
    }

    private static ChatClientRequest request(OrdinaryFinalOutputRepair state, List<Message> messages, ToolCallingChatOptions options) {
        return ChatClientRequest.builder().prompt(new Prompt(messages, options)).context(Map.of(OrdinaryFinalOutputRepair.CONTEXT_KEY, state)).build();
    }
}
