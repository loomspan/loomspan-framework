package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AcceptedResultDecoderTest
{
    private final AcceptedResultDecoder decoder = new AcceptedResultDecoder();

    @Test
    void decodesCompleteDocumentsWithLosslessNumbersWithoutInterpretingNestedStrings()
    {
        Map<?, ?> decoded = (Map<?, ?>) decoder.decode("""
                {"integer":123456789012345678901234567890,"decimal":1.234567890123456789,
                 "string":"{\\\"not\\\":\\\"parsed\\\"}","array":[null,true,3]}
                """);
        assertThat(decoded.get("integer")).isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(decoded.get("decimal")).isEqualTo(new BigDecimal("1.234567890123456789"));
        assertThat(decoded.get("string")).isEqualTo("{\"not\":\"parsed\"}");
        assertThat((List<?>) decoded.get("array")).hasSize(3);
        assertThat(decoder.decode("null")).isNull();
        assertThat(decoder.decode("true")).isEqualTo(true);
        assertThat(decoder.decode("123")).isEqualTo(new BigInteger("123"));
        assertThat(decoder.decode("\"{\\\"value\\\":1}\"")).isEqualTo("{\"value\":1}");
        assertThat(decoder.decode("[1,2]")).isEqualTo(List.of(new BigInteger("1"), new BigInteger("2")));
    }

    @Test
    void retainsPlainMalformedAndTrailingTextVerbatim()
    {
        for (String raw : List.of("", "  ", "plain text", "{\"value\":1", "{} {}", "true false"))
            assertThat(decoder.decode(raw)).isEqualTo(raw);
    }

    @Test
    @SuppressWarnings("unchecked")
    void decodedContainersCannotMutateAcceptedSource()
    {
        Map<String, Object> decoded = (Map<String, Object>) decoder.decode("{\"nested\":[{\"value\":1}]}");
        assertThatThrownBy(() -> decoded.put("other", 1)).isInstanceOf(UnsupportedOperationException.class);
        List<Object> nested = (List<Object>) decoded.get("nested");
        assertThatThrownBy(() -> nested.add(1)).isInstanceOf(UnsupportedOperationException.class);
        Map<String, Object> child = (Map<String, Object>) nested.getFirst();
        assertThatThrownBy(() -> child.put("value", 2)).isInstanceOf(UnsupportedOperationException.class);
    }
}
