package ai.loomspan.internal.springai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Test void nativeDriverOptionsUseTheSharedDecisionBeforeAnyDelegation() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new CanonicalToolCallingManager(delegate);
        var calls = new AtomicInteger();
        var callback = callback("tax-calculateTax", calls);
        List<org.springframework.ai.chat.prompt.ChatOptions> options = List.of(
                org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().toolCallbacks(callback).build(),
                org.springframework.ai.anthropic.AnthropicChatOptions.builder().toolCallbacks(callback).build(),
                org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder().toolCallbacks(callback).build());
        for (int index = 0; index < options.size(); index++) {
            String finish = List.of("LENGTH", "refusal", "SAFETY").get(index);
            var response = new ChatResponse(List.of(new Generation(response("tax-calculateTax").getResult().getOutput(),
                    org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason(finish).build())));
            var prompt = new Prompt("user", options.get(index));
            assertThatThrownBy(() -> manager.executeToolCalls(prompt, response))
                    .isInstanceOf(ai.loomspan.internal.provider.ProviderResponseRejectedException.class);
        }
        verifyNoInteractions(delegate);
        assertThat(calls).hasValue(0);
    }
    @Test void incompleteContainingResponseRejectsEveryGenerationBeforeDelegation() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new CanonicalToolCallingManager(delegate);
        var options = ToolCallingChatOptions.builder().toolCallbacks(callback("tax-calculateTax", new AtomicInteger())).build();
        var complete = generation(call("tax-calculateTax", "{}"));
        var incomplete = new Generation(complete.getOutput(), org.springframework.ai.chat.metadata.ChatGenerationMetadata
                .builder().finishReason("LENGTH").build());
        assertThatThrownBy(() -> manager.executeToolCalls(new Prompt("user", options),
                new ChatResponse(List.of(complete, incomplete))))
                .isInstanceOf(ai.loomspan.internal.provider.ProviderResponseRejectedException.class);
        verifyNoInteractions(delegate);
    }
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "{", "null", "[]", "1", "{}", "{\"reason\":null}", "{\"reason\":true}",
            "{\"reason\":\"\"}", "{\"reason\":\" \\r\\n\"}", "{\"reason\":\"x\",\"extra\":false}",
            "{\"reason\":\"x\",\"reason\":\"y\"}", "{\"reason\":\"x\"} {}"})
    void rejectsMalformedBoundControlArguments(String arguments) {
        var delegate = mock(ToolCallingManager.class);
        var manager = new CanonicalToolCallingManager(delegate);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(new PlannerInabilityTool()).build());

        assertThatThrownBy(() -> manager.executeToolCalls(prompt,
                new ChatResponse(List.of(generation(call(PlannerInabilityTool.NAME, arguments))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonblank string reason");
        verifyNoInteractions(delegate);
    }

    @Test void rejectsMixedOrRepeatedControlsAcrossAllGenerationsBeforeDispatch() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new CanonicalToolCallingManager(delegate);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(new PlannerInabilityTool(), callback("tax-calculateTax", new AtomicInteger())).build());
        var control = call(PlannerInabilityTool.NAME, "{\"reason\":\"blocked\"}");
        var application = call("Tax-calculateTax", "{}");

        for (var generations : List.of(List.of(generation(control, application)), List.of(generation(application, control)),
                List.of(generation(control, control)), List.of(generation(application), generation(control)),
                List.of(generation(control), generation(control)))) {
            assertThatThrownBy(() -> manager.executeToolCalls(prompt, new ChatResponse(generations)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only tool selection");
        }
        verifyNoInteractions(delegate);
    }

    @Test void controlNameRequiresExactSpellingAndAnActualFrameworkBinding() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new CanonicalToolCallingManager(delegate);
        var fake = callback(PlannerInabilityTool.NAME, new AtomicInteger());
        for (var options : List.of(ToolCallingChatOptions.builder().build(),
                ToolCallingChatOptions.builder().toolCallbacks(fake).build())) {
            assertThatThrownBy(() -> manager.executeToolCalls(new Prompt("user", options), response(PlannerInabilityTool.NAME)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var bound = ToolCallingChatOptions.builder().toolCallbacks(new PlannerInabilityTool()).build();
        assertThatThrownBy(() -> manager.executeToolCalls(new Prompt("user", bound), response("LOOMSPAN_CANNOT_COMPLETE")))
                .isInstanceOf(IllegalArgumentException.class);
        var conflicting = ToolCallingChatOptions.builder().toolCallbacks(new PlannerInabilityTool(), fake).build();
        assertThatThrownBy(() -> manager.resolveToolDefinitions(conflicting)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(delegate);
    }

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
        for (String name : List.of("a_b-c_d", "a".repeat(32) + "-" + "b".repeat(31), "loomspan-loomspan_cannot_complete")) {
            var count = new AtomicInteger();
            var manager = new CanonicalToolCallingManager(ToolCallingManager.builder().build());
            var callback = callback(name, count);
            var options = ToolCallingChatOptions.builder().toolCallbacks(callback, new PlannerInabilityTool()).build();
            assertThat(manager.resolveToolDefinitions(options)).extracting(ToolDefinition::name)
                    .containsExactlyInAnyOrder(name, PlannerInabilityTool.NAME);
            var result = manager.executeToolCalls(new Prompt("user", options), response(name));
            assertThat(count).hasValue(1);
            assertThat(result.conversationHistory().toString()).contains(name);
        }
    }

    @Test void unchangedCanonicalResponseRetainsMessageAndPartIdentity() {
        var delegate = mock(ToolCallingManager.class);
        var message = richMessage("tax-calculateTax");
        var original = new ChatResponse(List.of(new Generation(message)));
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("tax-calculateTax", new AtomicInteger())).build());
        new CanonicalToolCallingManager(delegate).executeToolCalls(prompt, original);
        var capture = org.mockito.ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(eq(prompt), capture.capture());
        assertThat(capture.getValue()).isSameAs(original);
        assertThat(capture.getValue().getResult().getOutput()).isSameAs(message);
    }

    @Test void canonicalRenameReplacesOnlyTheAttributedToolPart() {
        var delegate = mock(ToolCallingManager.class);
        var message = richMessage("Tax-calculateTax");
        var original = new ChatResponse(List.of(new Generation(message)));
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("tax-calculateTax", new AtomicInteger())).build());
        new CanonicalToolCallingManager(delegate).executeToolCalls(prompt, original);
        var capture = org.mockito.ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(eq(prompt), capture.capture());
        var parts = capture.getValue().getResult().getOutput().getParts();
        assertThat(parts).hasSize(message.getParts().size());
        for (int i : List.of(0, 1, 3, 4)) assertThat(parts.get(i)).isSameAs(message.getParts().get(i));
        var changed = (org.springframework.ai.chat.messages.part.ToolCallPart) parts.get(2);
        var before = (org.springframework.ai.chat.messages.part.ToolCallPart) message.getParts().get(2);
        assertThat(changed.toolCall().name()).isEqualTo("tax-calculateTax");
        assertThat(changed.toolCall().id()).isEqualTo(before.toolCall().id());
        assertThat(changed.toolCall().arguments()).isEqualTo(before.toolCall().arguments());
        assertThat(changed.payload()).isSameAs(before.payload());
        assertThat(changed.attributes()).isEqualTo(before.attributes());
        assertThat(before.toolCall().name()).isEqualTo("Tax-calculateTax");
    }

    private static AssistantMessage richMessage(String name) {
        var opaque = new org.springframework.ai.chat.messages.part.OpaquePayload(
                "openai.responses", "encrypted_content", "opaque exact payload");
        return AssistantMessage.builder().parts(List.of(
                new org.springframework.ai.chat.messages.part.ReasoningPart(null, "summary", opaque,
                        Map.of("id", "rs_1", "status", "completed")),
                new org.springframework.ai.chat.messages.part.TextPart("commentary", null, Map.of("phase", "commentary")),
                new org.springframework.ai.chat.messages.part.ToolCallPart(call(name, "{\"value\":null}"), opaque,
                        Map.of("id", "fc_1", "status", "completed")),
                new org.springframework.ai.chat.messages.part.UnknownPart("test", "future", "{\"exact\":1}", null,
                        Map.of("future", "attribute")),
                org.springframework.ai.chat.messages.part.MediaPart.of(org.springframework.ai.content.Media.builder()
                        .mimeType(org.springframework.util.MimeType.valueOf("image/png")).data(new byte[]{1,2,3}).build()))).properties(Map.of("raw", name)).build();
    }

    private static Generation generation(AssistantMessage.ToolCall... calls) {
        return new Generation(AssistantMessage.builder().content("accompanying text").toolCalls(List.of(calls)).build());
    }

    private static AssistantMessage.ToolCall call(String name, String arguments) {
        return new AssistantMessage.ToolCall("id", "function", name, arguments);
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
