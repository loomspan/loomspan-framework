package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.chat.ProviderAttemptCallAdvisor;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.model.*;
import ai.loomspan.internal.outputschema.*;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.testkit.ResponsesFixtures;
import com.google.genai.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import static ai.loomspan.internal.springai.OrdinaryNativeOutputIntegrationTest.*;

/** Actual planner and official-adapter loopback captures. */
class PlanningNativeOutputIntegrationTest {
    @ParameterizedTest @ValueSource(strings = {"gpt-6.1-terra", "openai/gpt-6.1-terra", "muse-spark-1.3-contributor",
            "qwen3.5:4b-q4_K_M", "claude-sonnet-5-5", "gemini-3.8-flash"})
    void actualPlannerInitialCorrectionAndIndependentCallRetainOptions(String identity) throws Exception {
        local(identity, (server, runtime) -> {
            var defaults = options(runtime, identity, 4096, null);
            // A deliberately different ordinary final contract cannot replace the planner contract.
            var client = client(runtime, defaults, contract(runtime.driver(), "name"), 1, mock(ExecutionStateService.class));
            var manifest = new ai.loomspan.internal.skill.YamlSkillManifest();
            manifest.setName("planner"); manifest.setDescription("planner"); manifest.setModel("fixture"); manifest.setPlanningMode(true);
            var config = new EffectiveSkillExecutionConfiguration("fixture", "loopback", runtime.driver(), identity,
                    identity.startsWith("qwen") ? null : "medium");
            var definition = new ai.loomspan.internal.skill.YamlSkillDefinition(ai.loomspan.internal.skill.YamlSkillDefinition.Form.MODEL,
                    "default", new org.springframework.core.io.ByteArrayResource(new byte[0]), manifest, config);
            var session = TestLoomspanSessions.withId("planner-wire", "default-planner", 3);
            var state = new ai.loomspan.internal.runtime.state.DefaultExecutionStateService(java.time.Clock.systemUTC());
            var planner = new ai.loomspan.internal.runtime.planning.DefaultPlanningService(state);
            var captures = new java.util.ArrayList<ModelInteractionRequest>();
            var interaction = new SpringAiModelInteraction(client, ModelInteractionMode.STEP_EXECUTION);
            server.enqueue(text(runtime.driver(), "{\"name\":\"wrong final schema\"}"));
            server.enqueue(text(runtime.driver(), "{\"capabilityName\":\"default-planner\",\"createdAt\":\"2026-01-01T00:00:00Z\",\"status\":\"VALID\",\"tasks\":[]}"));
            if (runtime.driver() == AiDriver.GEMINI) {
                // Retained PR30-B04: Spring AI SDK Schema fallback cannot represent nullable type arrays.
                // This asserts failure timing only; successful Gemini plan serialization remains blocked.
                assertThatThrownBy(() -> TestExecutionBindings.callWithSession(session, () -> planner.initializePlan(session, "plan", null,
                        definition, request -> { captures.add(request); return interaction.call(request); }, List.of())))
                        .hasStackTraceContaining("Cannot deserialize value of type `com.google.genai.types.Type` from Array value");
                assertThat(captures).hasSize(1);
                assertThat(server.getRequestCount()).isZero();
                assertNoFormat(defaults);
                return;
            }
            TestExecutionBindings.callWithSession(session, () -> planner.initializePlan(session, "plan", Map.of("attachmentDescriptor", "descriptor-only"),
                    definition, request -> { captures.add(request); return interaction.call(request); }, List.of()).orElseThrow());
            assertThat(captures).hasSize(2);
            assertThat(captures.getLast().outputContract()).isSameAs(captures.getFirst().outputContract());
            var schema = captures.getFirst().outputContract();
            assertThat(schema.tree().path("properties").has("tasks")).isTrue();
            assertThat(schema.tree().path("properties").has("name")).isFalse();
            assertThat(captures).allSatisfy(request -> {
                assertThat(request.input().attachments()).isEmpty();
                assertThat(request.planning()).isTrue();
            });
            var expected = schema.tree();
            if (runtime.driver() == AiDriver.ANTHROPIC) removePlanningAssertions(expected);
            var expectedContract = compile(expected.toString());
            for (int i = 0; i < 2; i++) assertNative(request(server), runtime.driver(), identity, 4096, expectedContract);
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertNoFormat(defaults);
            // The same model/defaults can subsequently make an unrelated unstructured call.
            server.enqueue(text(runtime.driver(), "plain independent call"));
            var plain = client(runtime, defaults, null, 0, mock(ExecutionStateService.class));
            assertThat(call(plain, runtime, identity, null).content()).isEqualTo("plain independent call");
            assertThat(nativeSchema(request(server), runtime.driver()).isMissingNode()).isTrue();
        });
    }

    private static void removePlanningAssertions(JsonNode node) {
        if (!node.isObject()) return;
        ((tools.jackson.databind.node.ObjectNode) node).remove("minLength");
        for (JsonNode value : node.path("properties")) removePlanningAssertions(value);
        if (node.has("items")) removePlanningAssertions(node.get("items"));
    }

}
