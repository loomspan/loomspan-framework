package ai.loomspan.internal.springai;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Local ordinary-callback decoder; it has no invocation or retry behavior. */
final class OrdinaryToolArguments
{
    private static final ObjectMapper ARGUMENTS = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final TypeReference<Map<String, Object>> ARGUMENT_MAP = new TypeReference<>() {};

    private OrdinaryToolArguments() {}

    static Map<String, Object> read(String input)
    {
        String invalid = "Tool arguments must contain exactly one JSON object";
        if (input == null || input.isBlank()) throw new IllegalArgumentException(invalid);
        try
        {
            var root = ARGUMENTS.readTree(input);
            if (root == null || !root.isObject()) throw new IllegalArgumentException(invalid);
            return ARGUMENTS.convertValue(root, ARGUMENT_MAP);
        }
        catch (JacksonException ex)
        {
            throw new IllegalArgumentException(invalid, ex);
        }
    }
}
