package ai.loomspan.internal.chat;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.evidence.TestEvidenceContracts;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.skill.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class OutputValidationAdvisorOrderingTest {
    @Test
    void preservesNestedAdvisorOrderAndResetsDownstreamAttempts() {
        var manifest = new YamlSkillManifest();
        manifest.setName("parent"); manifest.setOutputSchemaMaxRetries(1);
        manifest.setOutputSchema(ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree().readTree("""
                {"type":"object","properties":{"result":{"type":"string"},"claim":{"type":"string"}},"required":["result"],"additionalProperties":false}
                """));
        var regex = new YamlSkillManifest.RegexManifest(); regex.setPattern(".*GOOD.*"); regex.setMessage("Use GOOD");
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(1); linter.setRegex(regex); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", new ByteArrayResource(new byte[0]), manifest,
                new EffectiveSkillExecutionConfiguration("model", "connection", AiDriver.OPENAI, "model", "medium"),
                TestEvidenceContracts.compiled(Map.of("claim", "default-missingProducer")));
        var requests = new ArrayList<Prompt>();
        var candidates = new ArrayDeque<>(List.of("{\"result\":\"BAD\",\"claim\":\"unsupported\"}",
                "{\"result\":3}", "{\"result\":\"BAD\"}", "{\"result\":\"GOOD\"}"));
        ChatModel model = new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) {
                requests.add(prompt);
                var raw = new AssistantMessage(candidates.remove());
                var options = (org.springframework.ai.model.tool.ToolCallingChatOptions) prompt.getOptions();
                ((ai.loomspan.internal.springai.OrdinaryFinalOutputRepair) options.getToolContext()
                        .get(ai.loomspan.internal.springai.OrdinaryFinalOutputRepair.CONTEXT_KEY)).captureRaw(raw);
                return new ChatResponse(List.of(new Generation(raw)));
            }
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().model("fixture").build();
            }
        };
        var records = new ArrayList<TraceRecord>();
        new LoomspanSessionRunner(3).callWithNewSession("default-test_entry", ai.loomspan.testkit.TestSkillGenerations.snapshot(ai.loomspan.testkit.TestSkillGenerations.empty()), session ->
            TestExecutionBindings.callWithCurrentSessionMission(() -> {
                var client = ChatClient.builder(model)
                        .defaultOptions(org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().model("fixture"))
                        .defaultAdvisors(new DefaultSkillAdvisorResolver(new DefaultExecutionStateService(Clock.systemUTC())).resolve(definition)).build();
                var repair = new ai.loomspan.internal.springai.OrdinaryFinalOutputRepair();
                assertThat(client.prompt().user("Return JSON")
                        .toolContext(Map.of(ai.loomspan.internal.springai.OrdinaryFinalOutputRepair.CONTEXT_KEY, repair))
                        .advisors(spec -> spec.param(ai.loomspan.internal.springai.OrdinaryFinalOutputRepair.CONTEXT_KEY, repair))
                        .call().content()).isEqualTo("{\"result\":\"GOOD\"}");
                session.readTraceRecords(records::add); return null;
            }));
        var mutations = records.stream().filter(r -> r.recordType() == TraceRecordType.ADVISOR_REQUEST_MUTATION_RECORDED || r.recordType() == TraceRecordType.ADVISOR_RESPONSE_MUTATION_RECORDED).toList();
        assertThat(mutations.stream().map(r -> r.metadata().get("advisorName") + ":" + r.metadata().get("attempt") + ":" + r.metadata().get("status")))
                .containsExactly("EvidenceContractCallAdvisor[default-parent]:1:retrying", "EvidenceContractCallAdvisor[default-parent]:2:passed",
                        "OutputSchemaCallAdvisor[default-parent]:1:retrying", "EvidenceContractCallAdvisor[default-parent]:1:passed",
                        "OutputSchemaCallAdvisor[default-parent]:2:passed", "LinterCallAdvisor[default-parent]:1:retrying",
                        "EvidenceContractCallAdvisor[default-parent]:1:passed", "OutputSchemaCallAdvisor[default-parent]:1:passed", "LinterCallAdvisor[default-parent]:2:passed");
        assertThat(requests).hasSize(4);
        assertThat(requests.get(1).getSystemMessage().getText()).contains("Evidence validation failed", "Do NOT call any tools again");
        assertThat(requests.get(2).getInstructions().toString()).contains("expected", "3");
        assertThat(requests.get(3).getSystemMessage().getText()).contains("Use GOOD").doesNotContain("Evidence validation failed");
    }
}

