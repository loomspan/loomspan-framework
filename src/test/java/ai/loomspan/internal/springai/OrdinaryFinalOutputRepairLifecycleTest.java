package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.chat.ProviderAttemptCallAdvisor;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.linter.LinterCallAdvisor;
import ai.loomspan.internal.model.*;
import ai.loomspan.internal.outputschema.*;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.runtime.LoomspanQuotaExceededException;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.usage.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/** Real sessions, accounting, Spring tool loop and official Responses wire serializer. */
class OrdinaryFinalOutputRepairLifecycleTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void sequentialAndConcurrentCallsOnOneInteractionKeepCompletedWorkPrivate() throws Exception {
        try (var server = new MockWebServer()) {
            var stages = new ConcurrentHashMap<String, AtomicInteger>();
            var captures = new ConcurrentHashMap<String, List<JsonNode>>();
            var barrier = new CountDownLatch(2);
            server.setDispatcher(script(stages, captures, barrier));
            var effects = new ConcurrentHashMap<String, AtomicInteger>();
            var fixture = fixture(server, input -> {
                String id = input.get("id").toString();
                effects.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
                return "RESULT_" + id;
            }, new LoomspanProperties.Session.Quotas());
            for (String id : List.of("seqone", "seqtwo")) {
                var invocation = invocation(fixture, id);
                assertThat(call(fixture, invocation, id)).isEqualTo("OK_" + id);
                assertCounts(invocation, 3, 6, 9);
            }
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = invocation(fixture, "parallelone");
                var second = invocation(fixture, "paralleltwo");
                var a = workers.submit(() -> call(fixture, first, "parallelone"));
                var b = workers.submit(() -> call(fixture, second, "paralleltwo"));
                assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo("OK_parallelone");
                assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo("OK_paralleltwo");
                assertCounts(first, 3, 6, 9);
                assertCounts(second, 3, 6, 9);
            }
            assertThat(effects).hasSize(4).allSatisfy((id, count) -> assertThat(count).hasValue(1));
            assertPrivateRepairs(captures);
            assertThat(server.getRequestCount()).isEqualTo(12);
        }
    }

    @Test void nestedInteractionFromInitialToolGetsItsOwnHistoryAndTerminalFlag() throws Exception {
        try (var server = new MockWebServer()) {
            var stages = new ConcurrentHashMap<String, AtomicInteger>();
            var captures = new ConcurrentHashMap<String, List<JsonNode>>();
            server.setDispatcher(script(stages, captures, null));
            var reference = new AtomicReference<Fixture>();
            var childRef = new AtomicReference<Invocation>();
            var effects = new ConcurrentHashMap<String, AtomicInteger>();
            var fixture = fixture(server, input -> {
                String id = input.get("id").toString();
                effects.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
                if (id.equals("parent")) {
                    String answer = TestExecutionBindings.callWithCurrentSessionMission(() -> {
                        var childBinding = ExecutionBindingScope.requireCurrent();
                        var frame = reference.get().state.openFrame(childBinding.session(), TraceFrameType.MODEL_CALL, "default:root#child", Map.of());
                        var child = new Invocation(childBinding.session(), childBinding);
                        childRef.set(child);
                        try { return call(reference.get(), child, "child"); }
                        finally { reference.get().state.closeFrame(childBinding.session(), frame, Map.of("status", "completed")); }
                    });
                    assertThat(answer).isEqualTo("OK_child");
                }
                return "RESULT_" + id;
            }, new LoomspanProperties.Session.Quotas());
            reference.set(fixture);
            var parent = invocation(fixture, "parent");
            assertThat(call(fixture, parent, "parent")).isEqualTo("OK_parent");
            assertCounts(parent, 6, 12, 18);
            assertThat(effects).containsKey("parent");
            assertThat(childRef.get()).as("nested child invocation; observed effects %s", effects).isNotNull();
            assertThat(childRef.get().session).isSameAs(parent.session);
            assertThat(childRef.get().binding.mission()).isNotSameAs(parent.binding.mission());
            assertThat(effects).hasSize(2).allSatisfy((id, count) -> assertThat(count).hasValue(1));
            assertPrivateRepairs(captures);
            assertThat(server.getRequestCount()).isEqualTo(6);
        }
    }

    @Test void providerAttemptQuotaStopsRepairWithoutRepeatingCompletedEffect() throws Exception {
        try (var server = new MockWebServer()) {
            var captures = new ConcurrentHashMap<String, List<JsonNode>>();
            server.setDispatcher(script(new ConcurrentHashMap<>(), captures, null));
            var quotas = new LoomspanProperties.Session.Quotas();
            quotas.setMaxProviderAttempts(2);
            var effects = new AtomicInteger();
            var fixture = fixture(server, input -> { effects.incrementAndGet(); return "RESULT_quota"; }, quotas);
            var invocation = invocation(fixture, "quota");
            assertThatThrownBy(() -> call(fixture, invocation, "quota")).isInstanceOf(LoomspanQuotaExceededException.class);
            assertThat(effects).hasValue(1);
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertCounts(invocation, 2, 4, 6);
        }
    }

    @Test void interruptionAfterInitialToolAndCandidatePreventsRepairSend() throws Exception {
        try (var server = new MockWebServer()) {
            var stages = new ConcurrentHashMap<String, AtomicInteger>();
            var captures = new ConcurrentHashMap<String, List<JsonNode>>();
            server.setDispatcher(script(stages, captures, null));
            var effects = new AtomicInteger();
            var fixture = fixture(server, input -> { effects.incrementAndGet(); return "RESULT_cancel"; }, new LoomspanProperties.Session.Quotas());
            var invocation = invocation(fixture, "cancel");
            // A real linter recorder runs after receiving the invalid candidate and before rebuilding repair.
            var linter = new LinterCallAdvisor("default:root", "regex", Pattern.compile("^OK_.*$"), "Return OK", 1,
                    outcome -> { if (!outcome.passed()) Thread.currentThread().interrupt(); });
            var client = builder(fixture.runtime, fixture.state, fixture.usage, fixture.options).defaultAdvisors(linter).build();
            var cancellationFixture = new Fixture(fixture.runtime, fixture.state, fixture.usage, fixture.options,
                    new SpringAiModelInteraction(client, ModelInteractionMode.STEP_EXECUTION));
            try {
                assertThatThrownBy(() -> call(cancellationFixture, invocation, "cancel")).isInstanceOf(CancellationException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally { Thread.interrupted(); }
            assertThat(effects).hasValue(1);
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertCounts(invocation, 2, 4, 6);
        }
    }

    @Test void outerLinterRepairReplaysRawContributionAndKeepsFixedInputBinding() throws Exception {
        try (var server = new MockWebServer()) {
            var fixture = fixture(server, input -> "unused", new LoomspanProperties.Session.Quotas());
            var manifest = new YamlSkillManifest();
            manifest.setName("root");
            manifest.setOutputSchema(JSON.readTree("""
                    {"type":"object","properties":{"bound":{"type":"string"},"label":{"type":"string"}},
                     "required":["bound","label"],"additionalProperties":false}
                    """));
            var binding = new YamlSkillManifest.InputBindingManifest();
            binding.setFrom("input"); binding.setPath("/source");
            manifest.setOutputBindings(Map.of("/bound", binding));
            var definition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", new ByteArrayResource(new byte[0]),
                    manifest, new EffectiveSkillExecutionConfiguration("fixture", "loopback", AiDriver.OPENAI, "gpt-test", null));
            var schema = definition.outputSchema();
            var schemaAdvisor = new OutputSchemaCallAdvisor("default:root", schema, new OutputSchemaValidator(),
                    new OutputSchemaPromptAugmentor(), 1, outcome -> {}).withOutputBindings(definition);
            var linter = new LinterCallAdvisor("default:root", "regex", Pattern.compile(".*okay.*", Pattern.DOTALL),
                    "Use okay", 1, outcome -> {});
            var client = builder(fixture.runtime, fixture.state, fixture.usage, fixture.options)
                    .defaultAdvisors(schemaAdvisor, linter).build();
            var actual = new Fixture(fixture.runtime, fixture.state, fixture.usage, fixture.options,
                    new SpringAiModelInteraction(client, ModelInteractionMode.STEP_EXECUTION));
            var invocation = invocation(actual, "bound");
            var source = new LinkedHashMap<String,Object>(); source.put("source", "FIXED_EXACT");
            invocation.binding.requireMission().captureInput(source);
            var ready = new CountDownLatch(1); var resume = new CountDownLatch(1);
            server.setDispatcher(new Dispatcher() {
                private int turn;
                @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    if (++turn == 1) { ready.countDown(); assertThat(resume.await(5, TimeUnit.SECONDS)).isTrue(); return text("{\"label\":\"bad\"}"); }
                    return text("{\"label\":\"okay\"}");
                }
            });
            try (var worker = Executors.newSingleThreadExecutor()) {
                var result = worker.submit(() -> ExecutionBindingScope.supplyWith(invocation.binding, () -> actual.interaction.call(
                        new ModelInteractionRequest("SYSTEM_BOUND", new RenderedMissionInput("CALL_bound", List.of(), Map.of()),
                                trace("bound"), List.of(), false, schema)).content()));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                source.put("source", "CHANGED_LATER");
                resume.countDown();
                assertThat(JSON.readTree(result.get(10, TimeUnit.SECONDS))).isEqualTo(JSON.readTree("{\"bound\":\"FIXED_EXACT\",\"label\":\"okay\"}"));
            }
            server.takeRequest(2, TimeUnit.SECONDS);
            String repair = server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8();
            var messages = ai.loomspan.testkit.ResponsesFixtures.transcript(JSON.readTree(repair));
            assertThat(messages.valueStream().filter(node -> node.path("role").asText().equals("assistant")))
                    .singleElement().satisfies(node -> assertThat(node.path("content").asText()).isEqualTo("{\"label\":\"bad\"}"));
            assertThat(repair).doesNotContain("CHANGED_LATER");
            assertThat(invocation.binding.requireMission().getOrCreateOutputComposition(() -> { throw new AssertionError("snapshot recaptured"); })
                    .provenance()).hasSize(1);
            assertCounts(invocation, 2, 4, 6);
        }
    }

    private static Dispatcher script(ConcurrentHashMap<String, AtomicInteger> stages,
            ConcurrentHashMap<String, List<JsonNode>> captures, CountDownLatch barrier) {
        return new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                JsonNode body = JSON.readTree(request.getBody().readUtf8());
                String id = ai.loomspan.testkit.ResponsesFixtures.transcript(body).valueStream()
                        .filter(node -> node.path("role").asText().equals("user"))
                        .map(node -> node.path("content").asText()).filter(value -> value.startsWith("CALL_"))
                        .findFirst().orElseThrow().substring(5);
                captures.computeIfAbsent(id, ignored -> Collections.synchronizedList(new ArrayList<>())).add(body);
                int turn = stages.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
                if (turn == 1) {
                    if (barrier != null && id.startsWith("parallel")) { barrier.countDown(); assertThat(barrier.await(5, TimeUnit.SECONDS)).isTrue(); }
                    return tool(id);
                }
                return text((turn == 2 ? "INVALID_" : "OK_") + id);
            }
        };
    }

    private static Fixture fixture(MockWebServer server, java.util.function.Function<Map<String,Object>,String> effect,
            LoomspanProperties.Session.Quotas quotas) {
        var properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OPENAI); properties.setApiKey("local"); properties.setBaseUrl(server.url("/v1").toString());
        properties.getProviderRetry().setEnabled(false);
        var runtime = new SpringAiProviderIntegration().create("loopback", properties);
        var usage = new DefaultSessionUsageService(quotas, new NoOpUsageMetricsRecorder());
        var state = new DefaultExecutionStateService(Clock.systemUTC(), usage);
        var callback = FunctionToolCallback.<Map<String,Object>,String>builder("test--lookup", effect)
                .description("sentinel").inputType(Map.class).inputSchema("{\"type\":\"object\",\"additionalProperties\":true}").build();
        var options = OpenAiResponsesChatOptions.builder().model("gpt-test").toolCallbacks(callback).build();
        var linter = new LinterCallAdvisor("default:root", "regex", Pattern.compile("^OK_.*$"), "Return OK", 1, outcome -> {});
        var client = builder(runtime, state, usage, options).defaultAdvisors(linter).build();
        return new Fixture(runtime, state, usage, options, new SpringAiModelInteraction(client, ModelInteractionMode.STEP_EXECUTION));
    }

    private static ChatClient.Builder builder(ProviderConnectionRuntime runtime, DefaultExecutionStateService state,
            DefaultSessionUsageService usage, OpenAiResponsesChatOptions options) {
        return OrdinaryRepairTestClients.builder(runtime.chatModel()).defaultOptions(options.mutate())
                .defaultAdvisors(new ProviderAttemptCallAdvisor(runtime, state, new ModelUsageExtractor(), usage));
    }

    private static Invocation invocation(Fixture fixture, String id) {
        var session = TestLoomspanSessions.withId("repair-" + id, "default:root", 5);
        var binding = TestExecutionBindings.missionBinding(session);
        ExecutionBindingScope.runWith(binding, () -> fixture.state.openFrame(session, TraceFrameType.MODEL_CALL, "default:root#model", Map.of()));
        return new Invocation(session, binding);
    }

    private static String call(Fixture fixture, Invocation invocation, String id) {
        return ExecutionBindingScope.supplyWith(invocation.binding, () -> fixture.interaction.call(new ModelInteractionRequest(
                "SYSTEM_" + id, new RenderedMissionInput("CALL_" + id, List.of(), Map.of()), trace(id), List.of(), false, null)).content());
    }

    private static ModelTraceContext trace(String id) {
        return new ModelTraceContext(new ModelExecutionIdentity("fixture", "loopback", AiDriver.OPENAI, "gpt-test"), "default:root", id);
    }

    private static void assertCounts(Invocation invocation, int requests, long prompt, long completion) {
        var usage = invocation.session.getSessionUsage().orElseThrow();
        assertThat(usage.providerAttempts()).isEqualTo(requests);
        assertThat(usage.modelCalls()).isEqualTo(requests);
        assertThat(usage.promptUnits()).isEqualTo(prompt);
        assertThat(usage.completionUnits()).isEqualTo(completion);
        var records = new ArrayList<TraceRecord>(); invocation.session.readTraceRecords(records::add);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT)).hasSize(requests);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)).hasSize(requests);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                .map(record -> record.metadata().get("attemptId"))).doesNotHaveDuplicates();
    }

    private static void assertPrivateRepairs(Map<String,List<JsonNode>> captures) {
        captures.forEach((id, bodies) -> {
            assertThat(bodies).hasSize(3);
            var repair = bodies.get(2);
            assertThat(repair.path("tools").isEmpty()).isTrue();
            var input = repair.path("input");
            assertThat(input.valueStream().filter(node -> node.path("type").asText().equals("function_call")))
                    .singleElement().satisfies(node -> assertThat(node.path("call_id").asText()).isEqualTo("call_" + id));
            assertThat(input.valueStream().filter(node -> node.path("type").asText().equals("function_call_output")))
                    .singleElement().satisfies(node -> { assertThat(node.path("call_id").asText()).isEqualTo("call_" + id);
                        assertThat(node.path("output").asText()).contains("RESULT_" + id); });
            assertThat(repair.toString()).contains("INVALID_" + id, "SYSTEM_" + id, "CALL_" + id);
            captures.keySet().stream().filter(other -> !other.equals(id)).forEach(other ->
                    assertThat(repair.toString()).doesNotContain("RESULT_" + other, "INVALID_" + other, "call_" + other));
        });
    }

    private static MockResponse text(String value) {
        return json("{\"id\":\"response\",\"object\":\"response\",\"created_at\":1,\"model\":\"gpt-test\",\"status\":\"completed\",\"output\":[{\"id\":\"msg\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":" + JSON.writeValueAsString(value) + ",\"annotations\":[]}]}],\"usage\":{\"input_tokens\":2,\"output_tokens\":3,\"total_tokens\":5}}");
    }

    private static MockResponse tool(String id) {
        return json("{\"id\":\"response\",\"object\":\"response\",\"created_at\":1,\"model\":\"gpt-test\",\"status\":\"completed\",\"output\":[{\"id\":\"fc_" + id + "\",\"type\":\"function_call\",\"call_id\":\"call_" + id + "\",\"name\":\"test--lookup\",\"arguments\":" + JSON.writeValueAsString("{\"id\":\"" + id + "\"}") + ",\"status\":\"completed\"}],\"usage\":{\"input_tokens\":2,\"output_tokens\":3,\"total_tokens\":5}}");
    }
    private static MockResponse json(String body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(body); }
    private record Fixture(ProviderConnectionRuntime runtime, DefaultExecutionStateService state, DefaultSessionUsageService usage,
                           OpenAiResponsesChatOptions options, SpringAiModelInteraction interaction) {}
    private record Invocation(LoomspanSession session, ExecutionBinding binding) {}
}
