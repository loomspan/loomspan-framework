package ai.loomspan.internal.runtime.input;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Decodes only the accepted transport result, once, retaining malformed/plain text verbatim. */
public final class AcceptedResultDecoder
{
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public Object decode(String result)
    {
        if (result == null) return null;
        try
        {
            return DeepInputValues.immutableCopy(JSON.readValue(result, Object.class));
        }
        catch (RuntimeException ex)
        {
            return result;
        }
    }
}
