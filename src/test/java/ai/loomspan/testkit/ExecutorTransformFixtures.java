package ai.loomspan.testkit;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ExecutorTransformFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExecutorTransformFixtures() {}

    public static String acceptanceExpression() throws IOException {
        return new ClassPathResource("executor-transform/acceptance.jslt").getContentAsString(StandardCharsets.UTF_8);
    }

    public static Map<String, Object> businessInput() throws IOException {
        return readObject("input");
    }

    public static Map<String, Object> executorOutput() throws IOException {
        return readObject("output");
    }

    private static Map<String, Object> readObject(String name) throws IOException {
        var resource = new ClassPathResource("executor-transform/" + name + ".json");
        return JSON.readValue(resource.getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() {});
    }
}
