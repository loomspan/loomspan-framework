package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class CanonicalProviderNamesTest {
    @ParameterizedTest
    @EnumSource(value=AiDriver.class,names={"OPENAI","ANTHROPIC","GEMINI"})
    void sendsCanonicalIdentityUnchangedIncludingUnderscoresAndMaximumLength(AiDriver driver) throws Exception {
        try (var server = new MockWebServer()) {
            String response = switch(driver) {
                case OPENAI -> "{\"id\":\"test\",\"object\":\"response\",\"created_at\":1,\"model\":\"test\",\"status\":\"completed\",\"output\":[{\"id\":\"msg_0\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"ok\",\"annotations\":[]}]}],\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}";
                case ANTHROPIC -> "{\"id\":\"test\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"test\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
                case GEMINI -> "{\"modelVersion\":\"test\",\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}],\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1,\"totalTokenCount\":2}}";
                default -> throw new IllegalArgumentException();
            };
            server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(response));
            ChatModel model;
            if (driver == AiDriver.GEMINI) {
                var client = com.google.genai.Client.builder().apiKey("test-key").httpOptions(
                        com.google.genai.types.HttpOptions.builder().baseUrl(server.url("/").toString()).build()).build();
                model = org.springframework.ai.google.genai.GoogleGenAiChatModel.builder().genAiClient(client).build();
            } else {
                var properties = new LoomspanProperties.ConnectionProperties(); properties.setDriver(driver);
                properties.setApiKey("test-key"); properties.setBaseUrl(server.url("/").toString());
                model = new SpringAiProviderIntegration(new DefaultResourceLoader()).create("test",properties).requireChatModel();
            }
            var names = List.of("a_b-c_d","n".repeat(32)+"-"+"x".repeat(31));
            var callbacks = names.stream().map(name -> FunctionToolCallback.<String,String>builder(name,input -> input)
                    .description("fixture").inputType(String.class).inputSchema("{\"type\":\"object\",\"properties\":{}}").build()).toList();
            var options = ((ToolCallingChatOptions) new SpringAiChatOptionsContributor().createOptions(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                    "test","test",driver,"test",null)).build()).mutate()
                    .toolCallbacks(callbacks.toArray(org.springframework.ai.tool.ToolCallback[]::new)).build();
            model.call(new Prompt("user",options));
            var request = server.takeRequest(3,TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            var body = request.getBody().readUtf8();
            for (String name : names) assertThat(body).contains("\"name\":\""+name+"\"");
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }
}
