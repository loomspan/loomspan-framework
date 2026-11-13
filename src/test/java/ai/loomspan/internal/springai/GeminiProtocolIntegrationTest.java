package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import com.google.genai.Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Official adapter/factory requests against loopback only; no hosted acceptance claim. */
class GeminiProtocolIntegrationTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RESULT = """
            {"modelVersion":"fixture","candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
            """;
    private static final String SIMPLE_A = """
            {"type":"object","properties":{"name":{"type":"string","minLength":1,"maxLength":20}},"required":["name"]}
            """;
    private static final String SIMPLE_B = """
            {"type":"object","properties":{"label":{"type":"string","minLength":2,"maxLength":30}},"required":["label"]}
            """;
    private static final String OUTPUT = """
            {"type":"object","description":"Exact result","additionalProperties":false,
             "properties":{"name":{"type":["string","null"],"description":"Name","minLength":1,"maxLength":20},
               "code":{"type":"string","enum":["a","b"]},"email":{"type":"string","format":"email"},
               "score":{"type":"number","minimum":0.5,"maximum":8},
               "rows":{"type":"array","minItems":1,"maxItems":4,"items":{"type":"object",
                 "properties":{"values":{"type":"array","items":{"type":"boolean"}}},
                 "required":["values"],"additionalProperties":true}}},"required":["name"]}
            """;
    private static final String MAPS = """
            {"type":"object","description":"Authored tool contract","additionalProperties":false,
             "properties":{
               "code":{"type":"string","enum":["a","b"],"description":"Exact code","minLength":1,"maxLength":5},
               "email":{"type":"string","format":"email"},
               "score":{"type":"number","minimum":0.5,"maximum":8},
               "strings":{"type":"object","description":"String map","additionalProperties":{"type":"string","minLength":2}},
               "quantities":{"type":"object","additionalProperties":{"type":"integer","minimum":0,"maximum":9}},
               "records":{"type":"object","additionalProperties":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"],"additionalProperties":false}},
               "nested":{"type":"object","additionalProperties":{"type":"object","additionalProperties":{"type":"integer"}}},
               "rows":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","properties":{"map":{"type":"object","additionalProperties":{"type":"string"}}},"required":["map"],"additionalProperties":false}},
               "anything":{}},"required":["code","quantities"]}
            """;

    @ParameterizedTest
    @ValueSource(ints = {1024, 4096, 2048})
    void nativeMediumAndCapsSurviveChangingPerCallSchemas(int cap) throws Exception
    {
        local(server -> {
            var defaults = options("gemini-3.8-flash").mutate().maxOutputTokens(cap)
                    .temperature(0.2).stopSequences(List.of("END_FIXTURE")).build();
            var client = ChatClient.builder(model()).defaultOptions(defaults.mutate()).build();
            JsonNode first = null;
            for (String schema : List.of(SIMPLE_A, SIMPLE_B))
            {
                enqueue(server);
                client.prompt("fixture").options(defaults.mutate().outputSchema(schema)).call().content();
                JsonNode body = request(server, "options-" + cap + "-" + (schema.equals(SIMPLE_A) ? "a" : "b"));
                assertSelected(body, cap);
                assertThat(body.at("/generationConfig/responseJsonSchema")).isEqualTo(JSON.readTree(schema));
                if (first == null) first = body.at("/generationConfig/responseJsonSchema");
                else assertThat(body.at("/generationConfig/responseJsonSchema")).isNotEqualTo(first);
            }
            enqueue(server);
            client.prompt("format-free").call().content();
            JsonNode body = request(server, "options-" + cap + "-unformatted");
            assertSelected(body, cap);
            assertThat(body.at("/generationConfig/responseJsonSchema").isMissingNode()).isTrue();
            assertThat(defaults.getResponseSchema()).isNull();
            assertThat(defaults.getThinkingBudget()).isNull();
            assertThat(server.getRequestCount()).isEqualTo(3);
        });
    }

    @Test
    void freshEffectiveTargetsKeepNativeAndLegacyOptionsIndependent() throws Exception
    {
        local(server -> {
            ChatModel model = model();
            for (String id : List.of("gemini-2.5-pro", "gemini-3.8-flash", "gemini-2.5-pro", "gemini-3.8-flash"))
            {
                var defaults = options(id).mutate().maxOutputTokens(2048).build();
                var client = ChatClient.builder(model).defaultOptions(defaults.mutate()).build();
                enqueue(server);
                client.prompt("fixture").call().content();
                JsonNode body = request(server, "target-" + server.getRequestCount());
                assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(2048);
                if (id.equals("gemini-3.8-flash"))
                {
                    assertThat(body.at("/generationConfig/thinkingConfig/thinkingLevel").asText()).isEqualTo("MEDIUM");
                    assertThat(body.at("/generationConfig/thinkingConfig/thinkingBudget").isMissingNode()).isTrue();
                }
                else
                {
                    assertThat(body.at("/generationConfig/thinkingConfig/thinkingBudget").asInt()).isEqualTo(4096);
                    assertThat(body.at("/generationConfig/thinkingConfig/thinkingLevel").isMissingNode()).isTrue();
                }
            }
            assertThat(server.getRequestCount()).isEqualTo(4);
        });
    }

    @Test
    void nullableOutputSchemaRemainsRawAndComplete() throws Exception
    {
        requireRawSchemaAcceptance();
        local(server -> {
            var client = ChatClient.builder(model()).defaultOptions(options("gemini-3.8-flash").mutate()).build();
            for (String schema : List.of(OUTPUT, OUTPUT.replace("\"name\"", "\"label\"")))
            {
                enqueue(server);
                try
                {
                    client.prompt("output fixture").options(GoogleGenAiChatOptions.builder().outputSchema(schema)).call().content();
                }
                catch (RuntimeException failure)
                {
                    evidence("nullable-output-failure", failure + "\nphysicalRequests=" + server.getRequestCount());
                    assertThat(server.getRequestCount()).as("nullable schema failed before HTTP").isZero();
                    throw failure;
                }
                assertThat(request(server, "nullable-output").at("/generationConfig/responseJsonSchema"))
                        .isEqualTo(JSON.readTree(schema));
            }
        });
    }

    @Test
    void ordinaryInputMapsAndUnconstrainedLeavesRemainRawAndComplete() throws Exception
    {
        requireRawSchemaAcceptance();
        assertTool(MAPS, "map-tool");
    }

    @Test
    void nullableToolSchemaRemainsRawAndComplete() throws Exception
    {
        requireRawSchemaAcceptance();
        assertTool("""
                {"type":"object","properties":{"optional":{"type":["string","null"]}},"additionalProperties":true}
                """, "nullable-tool");
    }

    @Test
    void generatedReservedFalsePropertyRemainsRawAndComplete() throws Exception
    {
        requireRawSchemaAcceptance();
        assertTool("""
                {"type":"object","properties":{"name":{"type":"string"},"reserved":false},"required":["name"],"additionalProperties":false}
                """, "reserved-tool");
    }

    private static void requireRawSchemaAcceptance()
    {
        Assumptions.assumeTrue(Boolean.getBoolean("loomspan.pr30.blockers"),
                "PR30-B04: RC2 narrows Gemini raw schemas; see ai/thoughts/phases/pr-30-blockers.md. "
                        + "Reproduce with -Dloomspan.pr30.blockers=true");
    }

    private void assertTool(String schema, String label) throws Exception
    {
        local(server -> {
            AtomicInteger effects = new AtomicInteger();
            var callback = FunctionToolCallback.<Map<String,Object>,String>builder("inspect", input -> {
                effects.incrementAndGet();
                return "unused";
            }).description("fixture").inputType(Map.class).inputSchema(schema).build();
            var client = ChatClient.builder(model()).defaultOptions(options("gemini-3.8-flash").mutate()).build();
            enqueue(server);
            try { client.prompt("tool fixture").toolCallbacks(callback).call().content(); }
            catch (RuntimeException failure)
            {
                evidence(label + "-failure", failure + "\nphysicalRequests=" + server.getRequestCount());
                assertThat(server.getRequestCount()).as("schema failed before HTTP").isZero();
                throw failure;
            }
            finally
            {
                assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(schema);
                assertThat(effects.get()).isZero();
            }
            JsonNode body = request(server, label);
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertThat(body.at("/tools/0/functionDeclarations/0/parametersJsonSchema")).isEqualTo(JSON.readTree(schema));
            assertThat(body.at("/tools/0/functionDeclarations/0/parameters").isMissingNode()).isTrue();
        });
    }

    private static GoogleGenAiChatOptions options(String id)
    {
        return (GoogleGenAiChatOptions) new SpringAiChatOptionsContributor().createOptions(
                new EffectiveSkillExecutionConfiguration("fixture", "test", AiDriver.GEMINI, id, "medium")).build();
    }

    private static ChatModel model()
    {
        var properties = new LoomspanProperties.ConnectionProperties();
        properties.setDriver(AiDriver.GEMINI);
        properties.setApiKey("local-fake-key");
        return new SpringAiProviderIntegration(new DefaultResourceLoader()).create("test", properties).requireChatModel();
    }

    private static void local(LocalCase action) throws Exception
    {
        try (var server = new MockWebServer())
        {
            String base = server.url("/").toString();
            Client.setDefaultBaseUrls(Optional.of(base), Optional.of(base));
            try { action.run(server); }
            finally { Client.setDefaultBaseUrls(Optional.empty(), Optional.empty()); }
        }
    }

    private static void enqueue(MockWebServer server)
    {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(RESULT));
    }

    private static JsonNode request(MockWebServer server, String label) throws Exception
    {
        var request = server.takeRequest(3, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        String body = request.getBody().readUtf8();
        evidence(label, body);
        return JSON.readTree(body);
    }

    private static void evidence(String label, String content) throws Exception
    {
        Path directory = Path.of("target", "pr-30.1.4", "requests");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(label + ".txt"), content);
    }

    private static void assertSelected(JsonNode body, int cap)
    {
        assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(cap);
        assertThat(body.at("/generationConfig/thinkingConfig/thinkingLevel").asText()).isEqualTo("MEDIUM");
        assertThat(body.at("/generationConfig/thinkingConfig/thinkingBudget").isMissingNode()).isTrue();
        assertThat(body.at("/generationConfig/thinkingConfig/includeThoughts").asBoolean()).isTrue();
        assertThat(body.at("/generationConfig/temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.at("/generationConfig/stopSequences")).isEqualTo(JSON.readTree("[\"END_FIXTURE\"]"));
    }

    @FunctionalInterface
    private interface LocalCase { void run(MockWebServer server) throws Exception; }
}
