package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.core.AdvisorTraceFact;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.runtime.input.ObjectFieldPath;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.part.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual advisor and composition acceptance without unrelated application startup. */
class OrdinaryOutputAcceptanceTest {
    @Test
    void preciseInvalidCandidateIsCorrectedWithoutChangingOriginalEvidenceOrOptions() {
        var contract = compile("""
                {"type":"object","properties":{"value":{"type":"number","maximum":1}},"required":["value"],"additionalProperties":false}
                """);
        String candidate = "{\"value\":1.0000000000000001}";
        var chain = new RecordingChain(List.of(response(candidate), response("{\"value\":1}")));
        var facts = new ArrayList<AdvisorTraceFact>();
        var outcomes = new ArrayList<OutputSchemaOutcome>();
        var advisor = new OutputSchemaCallAdvisor("default-numbers", contract,
                new OutputSchemaValidator(JsonMapper.builder().build()), new OutputSchemaPromptAugmentor(),
                1, outcomes::add, facts::add);
        var options = OpenAiResponsesChatOptions.builder().model("fixture").maxOutputTokens(1024).build();
        var original = new ChatClientRequest(new Prompt("Task", options), Map.of("marker", "retained"));

        var accepted = advisor.adviseCall(original, chain);

        assertThat(accepted.chatResponse().getResult().getOutput().getText()).isEqualTo("{\"value\":1}");
        assertThat(chain.requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.context()).containsEntry("marker", "retained");
            assertThat(request.context().get(OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY)).isSameAs(contract);
            assertThat(request.prompt().getOptions()).isEqualTo(options);
        });
        assertThat(chain.requests.get(1).prompt().getInstructions())
                .filteredOn(message -> message.getMessageType() == MessageType.ASSISTANT)
                .singleElement().satisfies(message -> assertThat(message.getText()).isEqualTo(candidate));
        assertThat(outcomes).extracting(OutputSchemaOutcome::status)
                .containsExactly(OutputSchemaOutcomeStatus.RETRYING, OutputSchemaOutcomeStatus.PASSED);
        assertThat(original.context()).doesNotContainKey(OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY);
    }

    @Test
    void preciseViolationsExhaustTheExistingBudgetAndReturnTheExactTerminalCandidate() {
        var contract = compile("""
                {"type":"object","properties":{"value":{"type":"number","minimum":-1}},"required":["value"]}
                """);
        String candidate = "{\"value\":-10000000000000001e-16}";
        var chain = new RecordingChain(List.of(response(candidate)));
        var advisor = advisor(contract, 2);
        assertThatThrownBy(() -> advisor.adviseCall(request(), chain))
                .isInstanceOfSatisfying(LoomspanOutputSchemaValidationException.class, failure -> {
                    assertThat(failure.getAttemptCount()).isEqualTo(3);
                    assertThat(failure.getMaxRetries()).isEqualTo(2);
                    assertThat(failure.getRawOutput()).isEqualTo(candidate);
                });
        assertThat(chain.requests).hasSize(3);
    }

    @Test
    void operationalValidationFailureEscapesWithoutRetryOrAcceptedOutput() {
        var contract = mock(CanonicalSchema.class);
        when(contract.tree()).thenReturn(LoomspanJacksonCodecs.defaults().schemaTree().readTree("{\"type\":\"object\"}"));
        when(contract.validate(any())).thenThrow(new IllegalStateException("validation unavailable"));
        var chain = new RecordingChain(List.of(response("{}")));
        assertThatThrownBy(() -> advisor(contract, 3).adviseCall(request(), chain))
                .isInstanceOf(IllegalStateException.class).hasMessage("validation unavailable");
        assertThat(chain.requests).hasSize(1);
    }

    @Test
    void standardOpenObjectsRemainOpenInOrdinaryPromptAndAcceptance() {
        var contract = compile("""
                {"type":"object","properties":{"nested":{"type":"object"}}}
                """);
        var chain = new RecordingChain(List.of(response("{\"extra\":true,\"nested\":{\"extra\":1}}")));
        advisor(contract, 0).adviseCall(request(), chain);
        assertThat(chain.requests.getFirst().prompt().getSystemMessage().getText())
                .contains("$ — object, non-null, additionalProperties=true")
                .contains("$.nested — object, optional, non-null, additionalProperties=true")
                .doesNotContain("Omit unknown fields unless");
    }

    @Test
    void planningBypassesTheOrdinaryFinalContract() {
        var contract = compile("{\"type\":\"object\"}");
        var chain = new RecordingChain(List.of(response("planner-owned text")));
        var request = new ChatClientRequest(new Prompt("Plan"), Map.of(OutputSchemaCallAdvisor.PLANNING_CALL_KEY, true));
        assertThat(advisor(contract, 0).adviseCall(request, chain).chatResponse().getResult().getOutput().getText())
                .isEqualTo("planner-owned text");
        assertThat(chain.requests).singleElement().satisfies(sent -> {
            assertThat(sent.prompt().getContents()).isEqualTo("Plan");
            assertThat(sent.context()).doesNotContainKey(OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY);
        });
    }

    @Test
    void compositionReplacesApplicationTextOnceAndPreservesAllUntouchedPartsAndRawEvidence() {
        var contract = compile("""
                {"type":"object","properties":{"bound":{"type":"string"},"reasoned":{"type":"integer"}},"required":["bound","reasoned"],"additionalProperties":false}
                """);
        var binding = new ChildInputBinding(ObjectFieldPath.parse("/bound", false), ChildInputBinding.SourceKind.INPUT,
                ObjectFieldPath.parse("/source", true), null);
        var composition = new OutputBindingComposition(contract, List.of(binding), Map.of("source", "exact bound value"), List.of(),
                "owner", LoomspanJacksonCodecs.defaults().schemaTree());
        var opaque = new OpaquePayload("fixture", "signature", " exact\r\n雪 ");
        var thought = new ReasoningPart("thought", "summary", opaque, Map.of("id", "reasoning-id"));
        var first = new TextPart("{\"reasoned\":", opaque, Map.of("phase", "final", "id", "anchor"));
        var media = new MediaPart(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(URI.create("https://example.invalid/image.png")).build(),
                opaque, Map.of("id", "media-id"));
        var tool = new ToolCallPart(new AssistantMessage.ToolCall("call1", "function", "lookup", " {}\n"), opaque, Map.of("id", "tool-id"));
        var unknown = new UnknownPart("fixture", "future", "{\"exact\":1.0000000000000001}", opaque, Map.of("id", "future-id"));
        var last = new TextPart("2}", null, Map.of("id", "superseded-text"));
        var rawMessage = AssistantMessage.builder().parts(List.of(thought, first, media, tool, unknown, last))
                .properties(Map.of("provider-marker", "raw-message")).build();
        var generationMetadata = ChatGenerationMetadata.builder().finishReason("stop").metadata("marker", "generation").build();
        var extra = new Generation(new AssistantMessage("other generation"));
        var responseMetadata = ChatResponseMetadata.builder().id("response-id").model("fixture").keyValue("marker", "response").build();
        var raw = ChatClientResponse.builder().chatResponse(new ChatResponse(List.of(new Generation(rawMessage, generationMetadata), extra), responseMetadata))
                .context("raw-marker", "untouched").build();
        var chain = new RecordingChain(List.of(raw));
        var facts = new ArrayList<AdvisorTraceFact>();
        var advisor = new OutputSchemaCallAdvisor("default-compose", contract, new OutputSchemaValidator(), new OutputSchemaPromptAugmentor(),
                0, ignored -> {}, facts::add).withOutputBindings(mock(YamlSkillDefinition.class));
        var mission = mock(MissionContext.class);
        when(mission.getOrCreateOutputComposition(any())).thenReturn(composition);
        var ambient = mock(ExecutionBinding.class);
        when(ambient.requireMission()).thenReturn(mission);

        var accepted = ExecutionBindingScope.supplyWith(ambient, () -> advisor.adviseCall(request(), chain));

        var message = accepted.chatResponse().getResult().getOutput();
        assertThat(message.getText()).isEqualTo("{\"reasoned\":2,\"bound\":\"exact bound value\"}");
        assertThat(message.getParts()).hasSize(5);
        assertThat(message.getParts().get(0)).isSameAs(thought);
        assertThat(message.getParts().get(2)).isSameAs(media);
        assertThat(message.getParts().get(3)).isSameAs(tool);
        assertThat(message.getParts().get(4)).isSameAs(unknown);
        assertThat((TextPart) message.getParts().get(1)).satisfies(text -> {
            assertThat(text.attributes()).isEqualTo(first.attributes());
            assertThat(text.payload()).isSameAs(opaque);
        });
        assertThat(message.getToolCalls()).containsExactly(tool.toolCall());
        assertThat(message.getMedia()).containsExactly(media.media());
        assertThat(message.getMetadata()).isEqualTo(rawMessage.getMetadata());
        assertThat(accepted.chatResponse().getResult().getMetadata()).isSameAs(generationMetadata);
        assertThat(accepted.chatResponse().getResults().get(1)).isSameAs(extra);
        assertThat(accepted.chatResponse().getMetadata()).isSameAs(responseMetadata);
        assertThat(accepted.context()).containsEntry("raw-marker", "untouched");
        assertThat(rawMessage.getParts()).containsExactly(thought, first, media, tool, unknown, last);
        assertThat(rawMessage.getText()).isEqualTo("{\"reasoned\":2}");
        assertThat(facts).singleElement().satisfies(fact -> assertThat(fact.attributes()).containsEntry("candidate", "{\"reasoned\":2}"));
        var projected = (CanonicalSchema) chain.requests.getFirst().context().get(OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY);
        assertThat(projected.tree().path("properties").has("bound")).isFalse();
        assertThat(projected.tree().path("required").valueStream().map(node -> node.asText()).toList()).containsExactly("reasoned");
        assertThat(contract.tree().at("/properties/bound/type").asText()).isEqualTo("string");
    }

    @Test
    void emptyTextAndAbsentTextCannotBeAcceptedAsValidGeneratedJson() {
        var contract = compile("{\"type\":\"object\"}");
        for (var message : List.of(AssistantMessage.builder().parts(List.of(ReasoningPart.of("thought"))).build(),
                AssistantMessage.builder().parts(List.of(TextPart.of(""), ReasoningPart.of("thought"))).build())) {
            var chain = new RecordingChain(List.of(ChatClientResponse.builder().chatResponse(new ChatResponse(List.of(new Generation(message)))).build()));
            assertThatThrownBy(() -> advisor(contract, 0).adviseCall(request(), chain))
                    .isInstanceOfSatisfying(LoomspanOutputSchemaValidationException.class,
                            failure -> assertThat(failure.getFailureMode()).isEqualTo(OutputSchemaFailureMode.INVALID_JSON));
            assertThat(chain.requests).hasSize(1);
            assertThat(message.getParts()).anyMatch(part -> part instanceof ReasoningPart);
        }
    }

    private static CanonicalSchema compile(String json) {
        return new StandardSchemaCompiler().compile(json, StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.AUTHORED);
    }
    private static OutputSchemaCallAdvisor advisor(CanonicalSchema schema, int retries) {
        return new OutputSchemaCallAdvisor("default-ordinary", schema, new OutputSchemaValidator(), new OutputSchemaPromptAugmentor(), retries, ignored -> {});
    }
    private static ChatClientRequest request() { return new ChatClientRequest(new Prompt("Task"), Map.of()); }
    private static ChatClientResponse response(String text) {
        return ChatClientResponse.builder().chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text))))).build();
    }
    private static final class RecordingChain implements CallAdvisorChain {
        private final List<ChatClientResponse> responses;
        final List<ChatClientRequest> requests = new ArrayList<>();
        RecordingChain(List<ChatClientResponse> responses) { this.responses = responses; }
        public ChatClientResponse nextCall(ChatClientRequest request) {
            requests.add(request.copy());
            return responses.get(Math.min(requests.size() - 1, responses.size() - 1));
        }
        public List<CallAdvisor> getCallAdvisors() { return List.of(); }
        public CallAdvisorChain copy(CallAdvisor after) { return this; }
    }
}
