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
        var scalar = new YamlSkillManifest.OutputSchemaManifest(); scalar.setType("string");
        var schema = new YamlSkillManifest.OutputSchemaManifest(); schema.setType("object");
        schema.setProperties(Map.of("result", scalar, "claim", scalar));
        schema.setRequired(List.of("result")); schema.setAdditionalProperties(false); manifest.setOutputSchema(schema);
        var regex = new YamlSkillManifest.RegexManifest(); regex.setPattern(".*GOOD.*"); regex.setMessage("Use GOOD");
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(1); linter.setRegex(regex); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest,
                new EffectiveSkillExecutionConfiguration("model", "connection", AiDriver.OPENAI, "model", "medium"),
                TestEvidenceContracts.compiled(Map.of("claim", "missingProducer")));
        var requests = new ArrayList<Prompt>();
        var candidates = new ArrayDeque<>(List.of("{\"result\":\"BAD\",\"claim\":\"unsupported\"}",
                "{\"result\":3}", "{\"result\":\"BAD\"}", "{\"result\":\"GOOD\"}"));
        ChatModel model = prompt -> { requests.add(prompt); return new ChatResponse(List.of(new Generation(new AssistantMessage(candidates.remove())))); };
        var records = new ArrayList<TraceRecord>();
        new LoomspanSessionRunner(3).callWithNewSession("test.entry", ai.loomspan.testkit.TestSkillGenerations.empty(), session ->
            TestExecutionBindings.callWithCurrentSessionMission(() -> {
                var client = ChatClient.builder(model).defaultAdvisors(new DefaultSkillAdvisorResolver(new DefaultExecutionStateService(Clock.systemUTC())).resolve(definition)).build();
                assertThat(client.prompt().user("Return JSON").call().content()).isEqualTo("{\"result\":\"GOOD\"}");
                session.readTraceRecords(records::add); return null;
            }));
        var mutations = records.stream().filter(r -> r.recordType() == TraceRecordType.ADVISOR_REQUEST_MUTATION_RECORDED || r.recordType() == TraceRecordType.ADVISOR_RESPONSE_MUTATION_RECORDED).toList();
        assertThat(mutations.stream().map(r -> r.metadata().get("advisorName") + ":" + r.metadata().get("attempt") + ":" + r.metadata().get("status")))
                .containsExactly("EvidenceContractCallAdvisor[parent]:1:retrying", "EvidenceContractCallAdvisor[parent]:2:passed",
                        "OutputSchemaCallAdvisor[parent]:1:retrying", "EvidenceContractCallAdvisor[parent]:1:passed",
                        "OutputSchemaCallAdvisor[parent]:2:passed", "LinterCallAdvisor[parent]:1:retrying",
                        "EvidenceContractCallAdvisor[parent]:1:passed", "OutputSchemaCallAdvisor[parent]:1:passed", "LinterCallAdvisor[parent]:2:passed");
        assertThat(requests).hasSize(4);
        assertThat(requests.get(1).getSystemMessage().getText()).contains("Evidence validation failed", "Do NOT call any tools again");
        assertThat(requests.get(2).getInstructions().toString()).contains("expected", "3");
        assertThat(requests.get(3).getSystemMessage().getText()).contains("Use GOOD").doesNotContain("Evidence validation failed");
    }
}

