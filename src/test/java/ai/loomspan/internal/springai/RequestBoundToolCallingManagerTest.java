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

class RequestBoundToolCallingManagerTest {
    @Test void nativeDriverOptionsUseTheSharedDecisionBeforeAnyDelegation() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new RequestBoundToolCallingManager(delegate);
        var calls = new AtomicInteger();
        var callback = callback("order-support--find-order", calls);
        List<org.springframework.ai.chat.prompt.ChatOptions> options = List.of(
                org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().toolCallbacks(callback).build(),
                org.springframework.ai.anthropic.AnthropicChatOptions.builder().toolCallbacks(callback).build(),
                org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder().toolCallbacks(callback).build());
        for (int index = 0; index < options.size(); index++) {
            String finish = List.of("LENGTH", "refusal", "SAFETY").get(index);
            var response = new ChatResponse(List.of(new Generation(response("order-support--find-order").getResult().getOutput(),
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
        var manager = new RequestBoundToolCallingManager(delegate);
        var options = ToolCallingChatOptions.builder().toolCallbacks(callback("order-support--find-order", new AtomicInteger())).build();
        var complete = generation(call("order-support--find-order", "{}"));
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
        var manager = new RequestBoundToolCallingManager(delegate);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(new PlannerInabilityTool()).build());

        assertThatThrownBy(() -> manager.executeToolCalls(prompt,
                new ChatResponse(List.of(generation(call(PlannerInabilityTool.NAME, arguments))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonblank string reason");
        verifyNoInteractions(delegate);
    }

    @Test void rejectsMixedOrRepeatedControlsAcrossAllGenerationsBeforeDispatch() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new RequestBoundToolCallingManager(delegate);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(new PlannerInabilityTool(), callback("order-support--find-order", new AtomicInteger())).build());
        var control = call(PlannerInabilityTool.NAME, "{\"reason\":\"blocked\"}");
        var application = call("order-support--find-order", "{}");

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
        var manager = new RequestBoundToolCallingManager(delegate);
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

    @Test void rejectedResponseRetainsOriginalEvidenceAndArguments() {
        var delegate = mock(ToolCallingManager.class);
        var manager = new RequestBoundToolCallingManager(delegate);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("order-support--find-order", new AtomicInteger())).build());
        var original = response("order-support:find-order");
        assertThatThrownBy(() -> manager.executeToolCalls(prompt, original)).isInstanceOf(IllegalArgumentException.class);
        assertThat(original.getResult().getOutput().getToolCalls().getFirst().name()).isEqualTo("order-support:find-order");
        assertThat(original.getResult().getOutput().getToolCalls().getFirst().arguments()).isEqualTo("{\"value\":null}");
        verifyNoInteractions(delegate);
    }

    @Test void callbackFromAnotherRequestCannotBeResolved() {
        var count = new AtomicInteger();
        var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
        var first = ToolCallingChatOptions.builder().toolCallbacks(callback("order-support--find-order", count)).build();
        manager.resolveToolDefinitions(first);
        var second = ToolCallingChatOptions.builder().build();
        assertThatThrownBy(() -> manager.executeToolCalls(new Prompt("user", second), response("order-support--find-order")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count).hasValue(0);
    }

    @Test void rejectsEveryInvalidOrUnboundSelectionBeforeAnyCallbackExecutes() {
        for (String name : List.of("order-support:find-order", "other--find-order", "order-support--other", "Order-support--find-order", "", "order-support--find-order ", "tax-CalculateTax", "other-calculateTax", "calculateTax", "tax-a-b", "tax/a", " order-support--find-order",
                "n".repeat(33) + "-x", "tax-" + "x".repeat(61))) {
            var count = new AtomicInteger();
            var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
            var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(callback("order-support--find-order", count)).build());
            var response = new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(
                    new AssistantMessage.ToolCall("valid", "function", "order-support--find-order", "{}"),
                    new AssistantMessage.ToolCall("invalid", "function", name, "{}"))).build())));
            assertThatThrownBy(() -> manager.executeToolCalls(prompt, response)).isInstanceOf(IllegalArgumentException.class);
            assertThat(count).hasValue(0);
        }
    }

    @Test void realManagerExecutesExactOfferedNamesAndMaximumLength() {
        for (String name : List.of("order-support--find-order", "a".repeat(31) + "--" + "b".repeat(31))) {
            var count = new AtomicInteger();
            var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
            var callback = callback(name, count);
            var options = ToolCallingChatOptions.builder().toolCallbacks(callback, new PlannerInabilityTool()).build();
            assertThat(manager.resolveToolDefinitions(options)).extracting(ToolDefinition::name)
                    .containsExactlyInAnyOrder(name, PlannerInabilityTool.NAME);
            var result = manager.executeToolCalls(new Prompt("user", options), response(name));
            assertThat(count).hasValue(1);
            assertThat(result.conversationHistory().toString()).contains(name);
        }
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " "})
    void rejectsMissingNamesBeforeDelegation(String name) {
        var delegate = mock(ToolCallingManager.class);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("order-support--find-order", new AtomicInteger())).build());
        var original = new ChatResponse(List.of(generation(call(name, "{}"))));
        assertThatThrownBy(() -> new RequestBoundToolCallingManager(delegate).executeToolCalls(prompt, original))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(delegate);
    }

    @Test void laterGenerationCannotRunAnEarlierValidSelectionBeforePreflight() {
        var count = new AtomicInteger();
        var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("order-support--find-order", count)).build());
        var original = new ChatResponse(List.of(generation(call("order-support--find-order", "{}")),
                generation(call("other--find-order", "{}"))));
        assertThatThrownBy(() -> manager.executeToolCalls(prompt, original)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count).hasValue(0);
    }

    @Test void unchangedResponseRetainsMessageAndPartIdentity() {
        var delegate = mock(ToolCallingManager.class);
        var message = richMessage("order-support--find-order");
        var original = new ChatResponse(List.of(new Generation(message)));
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("order-support--find-order", new AtomicInteger())).build());
        new RequestBoundToolCallingManager(delegate).executeToolCalls(prompt, original);
        var capture = org.mockito.ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(eq(prompt), capture.capture());
        assertThat(capture.getValue()).isSameAs(original);
        assertThat(capture.getValue().getResult().getOutput()).isSameAs(message);
    }

    @Test void rejectedRichResponseRetainsEveryPart() {
        var delegate = mock(ToolCallingManager.class);
        var message = richMessage("order-support:find-order");
        var original = new ChatResponse(List.of(new Generation(message)));
        var prompt = new Prompt("user", ToolCallingChatOptions.builder()
                .toolCallbacks(callback("order-support--find-order", new AtomicInteger())).build());
        assertThatThrownBy(() -> new RequestBoundToolCallingManager(delegate).executeToolCalls(prompt, original))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(original.getResult().getOutput()).isSameAs(message);
        verifyNoInteractions(delegate);
    }

    /** Uses the actual Spring manager and callback, with a mocked downstream capability boundary. */
    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", " ", "{", "null", "\"\"", "\"text\"", "[]", "[{}]", "1", "1.25", "true", "false",
            "{} {}", "{} 1", "{} \"text\"", "{} true", "{} null", "{} trailing garbage"})
    void ordinaryCallbacksRejectRootsAndTrailingContentThroughRealManager(String arguments) {
        var capability = SpringAiToolCallbackAdapterTest.capability("{\"type\":\"object\"}");
        var callback = SpringAiToolCallbackAdapterTest.adapt(capability);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(callback).build());
        var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
        assertThatThrownBy(() -> manager.executeToolCalls(prompt,
                new ChatResponse(List.of(generation(call(callback.getToolDefinition().name(), arguments))))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(capability, never()).invoke(any(), any());
    }

    @Test void ordinaryCallbacksKeepEmptyObjectsNestedNullAndWhitespaceThroughRealManager() {
        var capability = SpringAiToolCallbackAdapterTest.capability("{\"type\":\"object\"}");
        when(capability.invoke(anyMap(), isNull())).thenReturn(Map.of("accepted", true));
        var callback = SpringAiToolCallbackAdapterTest.adapt(capability);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(callback).build());
        var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
        for (String arguments : List.of("{} \r\n\t", "{\"value\":null}  ")) {
            manager.executeToolCalls(prompt, new ChatResponse(List.of(generation(call(callback.getToolDefinition().name(), arguments)))));
        }
        var captured = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(capability, times(2)).invoke(captured.capture(), isNull());
        assertThat(captured.getAllValues().getFirst()).isEmpty();
        assertThat(captured.getAllValues().getLast()).containsEntry("value", null);
    }

    static class DecimalTarget {
        int calls;
        java.math.BigDecimal received;
        Map<String, Object> nested;
        @ai.loomspan.api.SkillMethod(namespace="numbers", name="decimal", description="Exact decimal input")
        public Object decimal(java.math.BigDecimal value,
                @ai.loomspan.api.SkillParam(required=false) Map<String, Object> nested) {
            calls++;
            received = value;
            this.nested = nested;
            return value;
        }
    }

    /** Decoder -> canonical validator -> real registered Java binder. Capability/auth pipeline is mocked. */
    @Test void realManagerDecoderAndValidatorPreserveJavaDecimalsAndRejectRoundedBoundaryBeforeInvocation() {
        var target = new DecimalTarget();
        var factory = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        factory.addBean("decimal", target);
        var processor = ai.loomspan.internal.core.SkillMethodBeanPostProcessor.create(
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion(),
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree());
        processor.setBeanFactory(factory);
        processor.postProcessAfterInitialization(target, "decimal");
        var metadata = processor.capabilities().getFirst();
        var compiler = new ai.loomspan.internal.schema.StandardSchemaCompiler();
        var canonical = compiler.compile("""
                {"type":"object","properties":{
                  "value":{"type":"number","minimum":0,"maximum":1},
                  "nested":{"type":"object","additionalProperties":{}}},
                 "required":["value"],"additionalProperties":false}
                """, ai.loomspan.internal.schema.StandardSchemaCompiler.Role.INPUT,
                ai.loomspan.internal.schema.StandardSchemaCompiler.Origin.AUTHORED);
        var contract = new ai.loomspan.internal.runtime.input.SkillInputContract(
                ai.loomspan.internal.runtime.input.SkillInputContract.SkillInputContractKind.YAML_EXPLICIT, canonical);
        var validator = new ai.loomspan.internal.runtime.input.SkillInputValidator();
        var capability = SpringAiToolCallbackAdapterTest.capability(metadata.tool().inputSchema());
        when(capability.name()).thenReturn(metadata.name());
        when(capability.invoke(anyMap(), isNull())).thenAnswer(invocation -> {
            Map<String, Object> input = invocation.getArgument(0);
            var checked = validator.validate(input, contract);
            if (!checked.valid()) throw new IllegalArgumentException("Invalid ordinary arguments: " + checked.issues());
            return metadata.invoker().invoke(checked.normalizedInput());
        });
        var callback = SpringAiToolCallbackAdapterTest.adapt(capability);
        var prompt = new Prompt("user", ToolCallingChatOptions.builder().toolCallbacks(callback).build());
        var manager = new RequestBoundToolCallingManager(ToolCallingManager.builder().build());
        String raw = """
                {"value":0.12345678901234567890123456789,
                 "nested":{"fraction":9.876543210987654321e30,"values":[1.234567890123456789e-30,null]}}
                """;
        var response = new ChatResponse(List.of(generation(call(callback.getToolDefinition().name(), raw))));
        manager.executeToolCalls(prompt, response);
        assertThat(target.calls).isEqualTo(1);
        assertThat(target.received).isEqualTo(new java.math.BigDecimal("0.12345678901234567890123456789"));
        assertThat(target.nested.get("fraction")).isEqualTo(new java.math.BigDecimal("9.876543210987654321e30"));
        assertThat(target.nested.get("values")).isEqualTo(Arrays.asList(new java.math.BigDecimal("1.234567890123456789e-30"), null));
        assertThat(response.getResult().getOutput().getToolCalls().getFirst().arguments()).isEqualTo(raw);
        for (String invalid : List.of("1.000000000000000000000000000001", "-0.000000000000000000000000000001")) {
            var rejected = new ChatResponse(List.of(generation(call(callback.getToolDefinition().name(), "{\"value\":" + invalid + "}"))));
            var feedback = manager.executeToolCalls(prompt, rejected);
            assertThat(feedback.conversationHistory().toString()).contains("Invalid ordinary arguments");
            assertThat(target.calls).isEqualTo(1);
        }
        // Schema validity does not make an unbound selection available.
        assertThatThrownBy(() -> manager.executeToolCalls(prompt,
                new ChatResponse(List.of(generation(call("other-decimal", "{\"value\":0.5}"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(target.calls).isEqualTo(1);
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
