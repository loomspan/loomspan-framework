package ai.loomspan.internal.serialization;

import ai.loomspan.internal.runtime.input.SkillInputContract;


import ai.loomspan.internal.schema.CanonicalSchema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoomspanMethodInputSchemaGeneratorTest
{
    private final ObjectMapper objectMapper = LoomspanJacksonCodecs.defaults().applicationConversion();
    private final LoomspanMethodInputSchemaGenerator generator = new LoomspanMethodInputSchemaGenerator(objectMapper, objectMapper);
    @Test
    void reflectedListOfGenericMapsAcceptsScalarValuedTransportOptions() throws Exception
    {
        var schema = generator.generate(Fixture.class.getDeclaredMethod("rankTransportOptions", List.class, String.class));
        var input = Map.of("options", List.of(option("Northeast Regional", 69, 210)), "sortBy", "price");
        assertThat(schema.validate(objectMapper.valueToTree(input))).isEmpty();
    }

    @Test
    void javaObjectGeneratesUnconstrainedProviderSchema() throws Exception
    {
        var schema = generator.generate(Fixture.class.getDeclaredMethod("acceptObject", Object.class));
        assertThat(schema.tree().path("type").asText()).isEqualTo("object");
        assertThat(schema.tree().path("properties").path("value").isEmpty()).isTrue();
        for (Object value : List.of("text", 7, 3.5, true, Map.of("nested", "value"), List.of(1, "two")))
            assertThat(schema.validate(objectMapper.valueToTree(Map.of("value", value)))).isEmpty();
        assertThat(schema.validate(objectMapper.readTree("{\"value\":null}"))).isEmpty();
        assertThat(schema.validate(objectMapper.readTree("{}"))).isNotEmpty();
    }

    @Test
    void typedMapSchemasRemainConstrainedAfterObjectBroadening() throws Exception
    {
        var integers = generator.generate(Fixture.class.getDeclaredMethod("acceptIntegers", Map.class));
        assertThat(integers.validate(objectMapper.readTree("{\"values\":{\"one\":7}}"))).isEmpty();
        assertThat(integers.validate(objectMapper.readTree("{\"values\":{\"one\":\"7\"}}"))).isNotEmpty();
        var strings = generator.generate(Fixture.class.getDeclaredMethod("acceptStrings", Map.class));
        assertThat(strings.validate(objectMapper.valueToTree(Map.of("values", Map.of("one", "text"))))).isEmpty();
        assertThat(strings.validate(objectMapper.valueToTree(Map.of("values", Map.of("one", 4))))).isNotEmpty();
        var records = generator.generate(Fixture.class.getDeclaredMethod("acceptRecords", Map.class));
        assertThat(records.validate(objectMapper.valueToTree(Map.of("values", Map.of("one", Map.of("name", "valid")))))).isEmpty();
        var errors = records.validate(objectMapper.valueToTree(Map.of("values", Map.of("one", Map.of("name", 4)))));
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getInstanceLocation().toString()).contains("values", "one", "name");
        assertThat(records.tree().at("/properties/values/additionalProperties/additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void recursiveDtoIsRejectedWithMethodPathAndCycle() throws Exception
    {
        var method = Fixture.class.getDeclaredMethod("acceptRecursive", RecursiveDto.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> generator.generate(method))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acceptRecursive")
                .hasMessageContaining("value.child").hasMessageContaining("cycle");
    }

    private Map<String, Object> option(String operator, double price, int durationMinutes)
    {
        LinkedHashMap<String, Object> option = new LinkedHashMap<>();
        option.put("operator", operator);
        option.put("price", price);
        option.put("durationMinutes", durationMinutes);
        return option;
    }

    @SuppressWarnings("unused")
    private static final class Fixture
    {
        void rankTransportOptions(List<Map<String, Object>> options, String sortBy)
        {
        }

        void acceptObject(Object value)
        {
        }

        void acceptStrings(Map<String, String> values)
        {
        }

        void acceptIntegers(Map<String, Integer> values)
        {
        }

        void acceptRecords(Map<String, SomeRecord> values)
        {
        }

        void acceptRecursive(RecursiveDto value)
        {
        }
    }

    private record SomeRecord(String name)
    {
    }

    private record RecursiveDto(String name, RecursiveDto child)
    {
    }
}
