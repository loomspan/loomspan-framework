package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.testkit.ResponsesFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.responses.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Real Framework factory + canonical/default manager; all endpoints are local fixtures. */
class SharedResponsesProtocolIntegrationTest {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @ParameterizedTest
    @ValueSource(strings={"gpt-6.1-terra", "muse-spark-1.3-contributor", "openai/gpt-6.1-terra", "qwen3.5:4b-q4_K_M"})
    void preservesThreeStatelessRoundsAndCanonicalParts(String identity) throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(round("one", List.of(reasoning("one", true), commentary("one"), call("one"))));
            server.enqueue(round("two", List.of(reasoning("two", true), commentary("two"), call("two"))));
            server.enqueue(ResponsesFixtures.text("finished"));
            var effects = new AtomicInteger();
            var options = requestOptions(identity, 4096).toolCallbacks(callback(effects, "{}" )).build();
            var client = client(model(server), options);
            assertThat(client.prompt("perform two lookups").call().content()).isEqualTo("finished");
            assertThat(effects).hasValue(2);
            for (int round=0; round<3; round++) {
                var request = server.takeRequest(2, TimeUnit.SECONDS);
                assertThat(request).isNotNull();
                assertThat(request.getPath()).isEqualTo("/v1/responses");
                var body = JSON.readTree(request.getBody().readUtf8());
                assertThat(body.path("store").booleanValue()).isFalse();
                assertThat(body.has("previous_response_id")).isFalse();
                assertThat(body.path("model").asText()).isEqualTo(identity);
                assertThat(body.path("max_output_tokens").asInt()).isEqualTo(4096);
                assertThat(body.at("/tools/0/strict").booleanValue()).isFalse();
                if (identity.startsWith("qwen")) {
                    assertThat(body.path("think").booleanValue()).isTrue();
                    assertThat(body.has("reasoning")).isFalse();
                } else assertThat(body.at("/reasoning/effort").asText()).isEqualTo("medium");
                if (identity.startsWith("openai/")) {
                    assertThat(body.at("/provider/require_parameters").booleanValue()).isTrue();
                    assertThat(body.at("/provider/allow_fallbacks").booleanValue()).isFalse();
                    assertThat(body.at("/provider/only/0").asText()).isEqualTo("openai");
                }
                var items = body.path("input");
                assertThat(items.size()).isEqualTo(1 + round*4);
                for (int prior=0; prior<round; prior++) {
                    String id = prior==0 ? "one" : "two";
                    int at=1+prior*4;
                    assertThat(items.get(at).path("type").asText()).isEqualTo("reasoning");
                    assertThat(items.get(at).path("id").asText()).isEqualTo("rs_"+id);
                    assertThat(items.get(at).path("encrypted_content").asText()).isEqualTo("opaque "+id);
                    assertThat(items.get(at).at("/summary/0/text").asText()).isEqualTo("summary "+id);
                    assertThat(items.get(at+1).path("phase").asText()).isEqualTo("commentary");
                    assertThat(items.get(at+2).path("id").asText()).isEqualTo("fc_"+id);
                    assertThat(items.get(at+2).path("name").asText()).isEqualTo("test-lookup");
                    assertThat(items.get(at+2).path("call_id").asText()).isEqualTo("call_"+id);
                    assertThat(items.get(at+3).path("type").asText()).isEqualTo("function_call_output");
                    assertThat(items.get(at+3).path("call_id").asText()).isEqualTo("call_"+id);
                }
            }
            assertThat(server.getRequestCount()).isEqualTo(3);
        }
    }

    @ParameterizedTest @ValueSource(ints={1024,2048,4096})
    void modelOnlyOverridesCombineCapsEffortHeadersAndTimeout(int cap) throws Exception {
        try (var server = new MockWebServer()) {
            server.start(); server.enqueue(ResponsesFixtures.text("done"));
            var model = model(server);
            var defaults = requestOptions("default-model", cap).timeout(Duration.ofSeconds(3))
                    .customHeaders(Map.of("X-Request", "kept")).build();
            var client = ChatClient.builder(model).defaultOptions(defaults.mutate()).build();
            client.prompt("hello").options(defaults.mutate().model("overridden")).call().content();
            var request = server.takeRequest(2,TimeUnit.SECONDS);
            var body = JSON.readTree(request.getBody().readUtf8());
            assertThat(body.path("model").asText()).isEqualTo("overridden");
            assertThat(body.path("max_output_tokens").asInt()).isEqualTo(cap);
            assertThat(body.at("/reasoning/effort").asText()).isEqualTo("medium");
            assertThat(request.getHeader("X-Connection")).isEqualTo("kept");
            assertThat(request.getHeader("X-Request")).isEqualTo("kept");
            server.enqueue(ResponsesFixtures.text("direct"));
            model.call(new Prompt("hello", defaults.mutate().model("direct").build()));
            body = JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            assertThat(body.path("max_output_tokens").asInt()).isEqualTo(cap);
            assertThat(body.at("/reasoning/effort").asText()).isEqualTo("medium");
        }
    }

    @ParameterizedTest @ValueSource(strings={"0.12345678901234567890123456789", "1.0000000000000001", "1.234567890123456789e100", "-1.234567890123456789e-100", "123456789012345678901234567890123456789", "0.5"})
    void outputAndToolSchemaNumbersRemainExact(String number) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("loomspan.pr30.blockers")
                        || Set.of("123456789012345678901234567890123456789", "0.5").contains(number),
                "PR30-B01: RC2 rounds schema decimals; see ai/thoughts/phases/pr-30-blockers.md. "
                        + "Reproduce with -Dloomspan.pr30.blockers=true");
        String schema = "{\"type\":\"object\",\"properties\":{\"rows\":{\"type\":\"array\",\"items\":{\"type\":\"number\",\"minimum\":"+number+",\"maximum\":"+number+",\"enum\":["+number+"]}}},\"additionalProperties\":false}";
        try (var server = new MockWebServer()) {
            server.start(); server.enqueue(ResponsesFixtures.text("{}"));
            client(model(server), requestOptions("gpt-6.1-terra",1024).outputSchema(schema)
                    .toolCallbacks(callback(new AtomicInteger(),schema)).build()).prompt("schema").call().content();
            var body = JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            var softly = new org.assertj.core.api.SoftAssertions();
            for (String path: List.of("/text/format/schema", "/tools/0/parameters")) {
                for (String keyword:List.of("minimum","maximum","enum/0")) {
                    var actual=body.at(path+"/properties/rows/items/"+keyword);
                    softly.assertThat(actual.isNumber()).as(path+keyword).isTrue();
                    softly.assertThat(actual.decimalValue().compareTo(new BigDecimal(number)))
                            .as(path+keyword+" numeric value "+actual).isZero();
                }
            }
            softly.assertThat(body.at("/tools/0/strict").booleanValue()).isFalse();
            softly.assertThat(body.at("/text/format/strict").booleanValue()).isTrue();
            softly.assertAll();
        }
    }

    @ParameterizedTest @ValueSource(strings={"plaintext", "signed", "summary-only"})
    void requiredNonCiphertextReasoningSurvivesContinuation(String shape) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("loomspan.pr30.blockers"),
                "PR30-B02: RC2 drops required non-ciphertext reasoning; see ai/thoughts/phases/pr-30-blockers.md. "
                        + "Reproduce with -Dloomspan.pr30.blockers=true");
        try (var server=new MockWebServer()) {
            server.start();
            var item = new LinkedHashMap<String,Object>(reasoning(shape,false));
            if (!shape.equals("summary-only")) item.put("content",List.of(Map.of("type","reasoning_text","text","required plaintext")));
            if (shape.equals("signed")) item.put("signature","required signature");
            server.enqueue(round("required",List.of(item,call("required"))));
            server.enqueue(ResponsesFixtures.text("done"));
            client(model(server),requestOptions("openai/gpt-6.1-terra",4096)
                    .toolCallbacks(callback(new AtomicInteger(),"{}")).build()).prompt("lookup").call().content();
            server.takeRequest(2,TimeUnit.SECONDS);
            var body=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            var replay = body.path("input").valueStream().filter(v->v.path("type").asText().equals("reasoning")).findFirst();
            assertThat(replay).as("required "+shape+" reasoning must not be silently dropped").isPresent();
            var input = body.path("input");
            assertThat(input.size()).isEqualTo(4);
            assertThat(input.get(1)).as("reasoning must precede its tool call and result").isEqualTo(replay.orElseThrow());
            assertThat(replay.orElseThrow().path("id").asText()).isEqualTo("rs_"+shape);
            assertThat(replay.orElseThrow().path("summary")).isEqualTo(JSON.valueToTree(item.get("summary")));
            if (!shape.equals("summary-only"))
                assertThat(replay.orElseThrow().path("content")).isEqualTo(JSON.valueToTree(item.get("content")));
            if(shape.equals("signed")) assertThat(replay.orElseThrow().path("signature").asText()).isEqualTo("required signature");
            assertThat(input.get(2).path("type").asText()).isEqualTo("function_call");
            assertThat(input.get(2).path("call_id").asText()).isEqualTo("call_required");
            assertThat(input.get(3).path("type").asText()).isEqualTo("function_call_output");
            assertThat(input.get(3).path("call_id").asText()).isEqualTo("call_required");
        }
    }

    @Test void concurrentInvocationsDoNotShareContinuationState() throws Exception {
        try (var server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    String raw=request.getBody().readUtf8();
                    String owner=raw.contains("alpha") ? "alpha" : "beta";
                    return raw.contains("function_call_output") ? ResponsesFixtures.text("done "+owner)
                            : round(owner,List.of(reasoning(owner,true),call(owner)));
                }
            });
            server.start();
            var effects=new AtomicInteger();
            var shared=client(model(server),requestOptions("gpt-6.1-terra",4096)
                    .toolCallbacks(callback(effects,"{}")).build());
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
                var alpha=pool.submit(()->shared.prompt("alpha").call().content());
                var beta=pool.submit(()->shared.prompt("beta").call().content());
                assertThat(alpha.get(10,TimeUnit.SECONDS)).isEqualTo("done alpha");
                assertThat(beta.get(10,TimeUnit.SECONDS)).isEqualTo("done beta");
            }
            assertThat(effects).hasValue(2);
            for(int i=0;i<4;i++) {
                String raw=server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8();
                assertThat(raw.contains("alpha") && raw.contains("beta")).isFalse();
            }
        }
    }

    @Test void rejectedContinuationDoesNotRepeatCompletedToolsOrSwitchModels() throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            server.enqueue(round("first",List.of(reasoning("first",true),call("first"))));
            server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type","application/json")
                    .setBody("{\"error\":{\"message\":\"route cannot replay opaque state\",\"type\":\"invalid_request_error\"}}"));
            var effects=new AtomicInteger();
            assertThatThrownBy(()->client(model(server),requestOptions("openai/gpt-6.1-terra",4096)
                    .toolCallbacks(callback(effects,"{}")).build()).prompt("lookup").call().content())
                    .hasMessageContaining("route cannot replay opaque state");
            assertThat(effects).hasValue(1);
            assertThat(server.getRequestCount()).isEqualTo(2);
            server.takeRequest(2,TimeUnit.SECONDS);
            var request=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            assertThat(request.path("model").asText()).isEqualTo("openai/gpt-6.1-terra");
            assertThat(request.at("/input/1/encrypted_content").asText()).isEqualTo("opaque first");
        }
    }

    @Test void parallelSelectionsAndRecoverableErrorsRetainBothResults() throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            server.enqueue(round("parallel",List.of(reasoning("parallel",true),call("one"),call("two"))));
            server.enqueue(ResponsesFixtures.text("done"));
            var effects=new AtomicInteger();
            ToolCallback callback=new ToolCallback() {
                public ToolDefinition getToolDefinition(){return ToolDefinition.builder().name("test-lookup").description("lookup").inputSchema("{}").build();}
                public String call(String input){
                    if(effects.incrementAndGet()==1)throw new org.springframework.ai.tool.execution.ToolExecutionException(getToolDefinition(),new IllegalArgumentException("recoverable detail"));
                    return "second result";
                }
            };
            var manager=ToolCallingManager.builder().toolExecutionExceptionProcessor(failure->"recoverable detail").build();
            var client=ChatClient.builder(model(server),io.micrometer.observation.ObservationRegistry.NOOP,null,null,
                    ToolCallingAdvisor.builder().toolCallingManager(new CanonicalToolCallingManager(manager)))
                    .defaultOptions(requestOptions("gpt-6.1-terra",4096).toolCallbacks(callback)).build();
            assertThat(client.prompt("lookup").call().content()).isEqualTo("done");
            assertThat(effects).hasValue(2);
            server.takeRequest(2,TimeUnit.SECONDS);
            var input=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8()).path("input");
            assertThat(input.get(3).path("call_id").asText()).isEqualTo("call_two");
            assertThat(input.get(4).path("output").asText()).isEqualTo("recoverable detail");
            assertThat(input.get(5).path("output").asText()).isEqualTo("second result");
        }
    }

    @Test void malformedTypedSchemaFailsBeforeSending() throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            server.enqueue(ResponsesFixtures.text("unexpected send"));
            var options=requestOptions("gpt-6.1-terra",1024).outputSchema("{ malformed").build();
            assertThatThrownBy(()->client(model(server),options).prompt("fixture").call().content())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Failed to parse JSON schema");
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void unsupportedRoutingDoesNotRetryOrFallback(boolean require) throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type","application/json")
                    .setBody("{\"error\":{\"message\":\"No eligible route supports these parameters\",\"code\":\"no_route\"}}"));
            var options=requestOptions("openai/gpt-6.1-terra",4096).extraBody(Map.of("provider",Map.of("require_parameters",require))).build();
            assertThatThrownBy(()->client(model(server),options).prompt("fixture").call().content()).hasMessageContaining("No eligible route");
            assertThat(server.getRequestCount()).isEqualTo(1);
            var body=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            assertThat(body.at("/provider/require_parameters").booleanValue()).isEqualTo(require);
        }
    }

    @Test void perCallSchemasReplacePreviousSchemasWithoutMutation() throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            var model=model(server);
            for(String field:List.of("first","second")) {
                server.enqueue(ResponsesFixtures.text("{}"));
                String schema="{\"type\":\"object\",\"properties\":{\""+field+"\":{\"type\":\"number\",\"maximum\":0.5}},\"additionalProperties\":false}";
                var callback=callback(new AtomicInteger(),schema);
                client(model,requestOptions("gpt-6.1-terra",1024).outputSchema(schema).toolCallbacks(callback).build())
                        .prompt("fixture").call().content();
                var body=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
                for(String prefix:List.of("/text/format/schema","/tools/0/parameters")) {
                    assertThat(body.at(prefix+"/properties").propertyNames()).containsExactly(field);
                    assertThat(body.at(prefix+"/properties/"+field+"/maximum").decimalValue()).isEqualByComparingTo("0.5");
                }
                assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(schema);
            }
        }
    }

    @Test void modelDefaultsCombineAndBareTypedDirectOptionsReplace() throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            var factory=model(server);
            // This adapter control supplies request defaults absent from authored connection properties.
            var configured=OpenAiResponsesChatModel.builder().options(factory.getOptions().mutate()
                    .model("initial").reasoningEffort("medium").maxOutputTokens(4096).build()).build();
            server.enqueue(ResponsesFixtures.text("client"));
            ChatClient.create(configured).prompt("fixture").options(OpenAiResponsesChatOptions.builder().model("override")).call().content();
            var body=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            assertThat(body.path("model").asText()).isEqualTo("override");
            assertThat(body.path("max_output_tokens").asInt()).isEqualTo(4096);
            assertThat(body.at("/reasoning/effort").asText()).isEqualTo("medium");
            server.enqueue(ResponsesFixtures.text("direct"));
            configured.call(new Prompt("fixture",OpenAiResponsesChatOptions.builder().model("bare").build()));
            body=JSON.readTree(server.takeRequest(2,TimeUnit.SECONDS).getBody().readUtf8());
            assertThat(body.has("max_output_tokens")).isFalse();
            assertThat(body.has("reasoning")).isFalse();
        }
    }

    @Test void incompleteStatusAndNativeUsageRemainAvailableWithoutDoubleCounting() throws Exception {
        try(var server=new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(
                    "{\"id\":\"incomplete\",\"object\":\"response\",\"created_at\":1,\"model\":\"fixture\",\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[],\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"total_tokens\":15,\"output_tokens_details\":{\"reasoning_tokens\":3}}}"));
            var response=model(server).call(new Prompt("fixture",requestOptions("fixture",1024).build()));
            assertThat((String)response.getMetadata().get("openai.responses.status")).isEqualTo("incomplete");
            var usage=new ai.loomspan.internal.runtime.usage.ModelUsageExtractor().extract(response,List.of(new UserMessage("fixture")),"");
            assertThat(usage.totalUnits()).isEqualTo(15);
            assertThat(usage.completionUnits()).isEqualTo(5);
            assertThat(usage.nativeUsage()).isInstanceOf(com.openai.models.responses.ResponseUsage.class);
            assertThat(((com.openai.models.responses.ResponseUsage)usage.nativeUsage()).outputTokensDetails().reasoningTokens()).isEqualTo(3);
        }
    }

    @ParameterizedTest @ValueSource(strings={"not-json","{\"id\":\"bad\",\"object\":\"response\",\"created_at\":1,\"model\":\"fixture\",\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\",\"summary\":[]}]}"})
    void malformedResponsesFailWithoutNestedRetry(String body) throws Exception {
        try(var server=new MockWebServer()) {
            server.start();server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(body));
            assertThatThrownBy(()->model(server).call(new Prompt("fixture",requestOptions("fixture",1024).build()))).isInstanceOf(RuntimeException.class);
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    private static OpenAiResponsesChatOptions.Builder requestOptions(String model,int cap) {
        var builder=OpenAiResponsesChatOptions.builder().model(model).maxOutputTokens(cap);
        if(model.startsWith("qwen")) builder.extraBody(Map.of("think",true));
        else builder.reasoningEffort("medium");
        if(model.startsWith("openai/")) builder.extraBody(Map.of("provider",Map.of("only",List.of("openai"),"allow_fallbacks",false,"require_parameters",true)));
        return builder;
    }
    private static OpenAiResponsesChatModel model(MockWebServer server) {
        var properties=new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.OPENAI); properties.setApiKey("local-fixture");
        properties.setBaseUrl(server.url("/v1").toString()); properties.setRequestTimeout(Duration.ofSeconds(5));
        properties.setHeaders(Map.of("X-Connection","kept"));
        return (OpenAiResponsesChatModel)new SpringAiProviderIntegration(new DefaultResourceLoader()).create("fixture",properties).chatModel();
    }
    private static ChatClient client(OpenAiResponsesChatModel model,OpenAiResponsesChatOptions options) {
        return ChatClient.builder(model,io.micrometer.observation.ObservationRegistry.NOOP,null,null,
                ToolCallingAdvisor.builder().toolCallingManager(new CanonicalToolCallingManager(
                        ToolCallingManager.builder().unlimitedCallsPerTool().unlimitedTotalToolCalls().build())))
                .defaultOptions(options.mutate()).build();
    }
    private static ToolCallback callback(AtomicInteger effects,String schema) {
        return new CountingCallback(effects,schema);
    }
    private record CountingCallback(AtomicInteger effects,String schema) implements ToolCallback {
        public ToolDefinition getToolDefinition(){return ToolDefinition.builder().name("test-lookup").description("lookup").inputSchema(schema).build();}
        public String call(String input){return "effect "+effects.incrementAndGet();}
    }
    private static Map<String,Object> call(String id) {return ResponsesFixtures.functionCall("Test-lookup","call_"+id,"{}").entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getKey().equals("id")?"fc_"+id:e.getValue()));}
    private static Map<String,Object> reasoning(String id,boolean encrypted) {
        var item=new LinkedHashMap<String,Object>(); item.put("type","reasoning");item.put("id","rs_"+id);
        item.put("summary",List.of(Map.of("type","summary_text","text","summary "+id)));
        if(encrypted)item.put("encrypted_content","opaque "+id);return item;
    }
    private static Map<String,Object> commentary(String id) {
        var item=new LinkedHashMap<String,Object>(ResponsesFixtures.textItem("commentary "+id));
        item.put("id","msg_"+id);item.put("phase","commentary");return item;
    }
    private static MockResponse round(String id,List<?> items){return ResponsesFixtures.response("resp_"+id,"fixture",items);}
}
