package ai.loomspan.internal.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CanonicalToolCallingManagerTest {
    @Test void canonicalExecutionCopyPreservesOriginalEvidenceAndArguments() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new CanonicalToolCallingManager(delegate);
        var callback = callback("tax-calculateTax", new AtomicInteger());
        var prompt = new Prompt("authored Tax-calculateTax", ToolCallingChatOptions.builder().toolCallbacks(callback).build());
        var original = response("Tax-calculateTax");
        manager.executeToolCalls(prompt, original);
        var captured = org.mockito.ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(eq(prompt), captured.capture());
        var copy = captured.getValue();
        assertThat(original.getResult().getOutput().getToolCalls().getFirst().name()).isEqualTo("Tax-calculateTax");
        assertThat(copy.getResult().getOutput().getToolCalls().getFirst())
                .isEqualTo(new AssistantMessage.ToolCall("id", "function", "tax-calculateTax", "{\"value\":null}"));
        assertThat(copy.getMetadata()).isSameAs(original.getMetadata());
        assertThat(copy.getResult().getMetadata()).isSameAs(original.getResult().getMetadata());
        assertThat(copy.getResult().getOutput().getText()).isEqualTo("raw text");
        assertThat(copy.getResult().getOutput().getMetadata()).containsEntry("raw", "Tax-calculateTax");
    }

    @Test void rejectsEveryInvalidOrUnboundSelectionBeforeAnyCallbackExecutes() {
        for (String name : List.of("tax-CalculateTax", "other-calculateTax", "calculateTax", "tax-a-b", "tax/a", " tax-calculateTax",
                "n".repeat(33) + "-x", "tax-" + "x".repeat(61))) {
            var count = new AtomicInteger();
            var manager = new CanonicalToolCallingManager(ToolCallingManager.builder().build());
            var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(callback("tax-calculateTax", count)).build());
            var response = new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(
                    new AssistantMessage.ToolCall("valid", "function", "tax-calculateTax", "{}"),
                    new AssistantMessage.ToolCall("invalid", "function", name, "{}"))).build())));
            assertThatThrownBy(() -> manager.executeToolCalls(prompt, response)).isInstanceOf(IllegalArgumentException.class);
            assertThat(count).hasValue(0);
        }
    }

    @Test void realManagerExecutesCanonicalUnderscoresAndMaximumLengthWithoutRenaming() {
        for (String name : List.of("a_b-c_d", "a".repeat(32) + "-" + "b".repeat(31))) {
            var count = new AtomicInteger();
            var manager = new CanonicalToolCallingManager(ToolCallingManager.builder().build());
            var callback = callback(name, count);
            var options = ToolCallingChatOptions.builder().toolCallbacks(callback).build();
            assertThat(manager.resolveToolDefinitions(options).getFirst().name()).isEqualTo(name);
            var result = manager.executeToolCalls(new Prompt("user", options), response(name));
            assertThat(count).hasValue(1);
            assertThat(result.conversationHistory().toString()).contains(name);
        }
    }
    private static ChatResponse response(String name) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("raw text")
                .properties(Map.of("raw", name)).toolCalls(List.of(new AssistantMessage.ToolCall(
                        "id", "function", name, "{\"value\":null}"))).build())));
    }
    private static ToolCallback callback(String name, AtomicInteger calls) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() { return ToolDefinition.builder().name(name).description("test").inputSchema("{}").build(); }
            public String call(String arguments) { calls.incrementAndGet(); return "result"; }
        };
    }
}
