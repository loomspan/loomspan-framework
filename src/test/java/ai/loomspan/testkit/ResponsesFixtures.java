package ai.loomspan.testkit;

import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import tools.jackson.databind.ObjectMapper;

/** Local Responses wire fixtures shared by framework integration tests. */
public final class ResponsesFixtures
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private ResponsesFixtures() {}

    public static Map<String, Object> textItem(String text)
    {
        return Map.of("id", "msg_local", "type", "message", "role", "assistant", "status", "completed",
                "content", List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())));
    }

    public static Map<String, Object> functionCall(String name, String id, String arguments)
    {
        return Map.of("id", "fc_" + id, "type", "function_call", "call_id", id,
                "name", name, "arguments", arguments, "status", "completed");
    }

    public static String body(String id, String model, List<?> output)
    {
        return JSON.writeValueAsString(Map.of("id", id, "object", "response", "created_at", 1,
                "model", model, "status", "completed", "output", output,
                "usage", Map.of("input_tokens", 1, "output_tokens", 1, "total_tokens", 2)));
    }

    public static MockResponse response(String id, String model, List<?> output)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body(id, model, output));
    }

    public static MockResponse text(String value)
    {
        return response("resp_local", "deterministic", List.of(textItem(value)));
    }
    /** Text views of actual Responses instructions/input for prompt-policy assertions. */
    public static tools.jackson.databind.JsonNode transcript(tools.jackson.databind.JsonNode request)
    {
        var messages = JSON.createArrayNode();
        if (request.has("instructions")) messages.add(JSON.createObjectNode()
                .put("role", "system").put("content", request.path("instructions").asString()));
        for (var item : request.path("input"))
        {
            if ("function_call_output".equals(item.path("type").asString()))
            {
                messages.add(JSON.createObjectNode().put("role", "tool")
                        .put("content", item.path("output").asString()).put("call_id", item.path("call_id").asString()));
                continue;
            }
            if (!item.has("role")) continue;
            var content = item.path("content");
            String text;
            if (content.isString()) text = content.asString();
            else
            {
                var combined = new StringBuilder();
                for (var part : content) if (part.has("text")) combined.append(part.path("text").asString());
                text = combined.toString();
            }
            messages.add(JSON.createObjectNode().put("role", item.path("role").asString()).put("content", text));
        }
        return messages;
    }

}
