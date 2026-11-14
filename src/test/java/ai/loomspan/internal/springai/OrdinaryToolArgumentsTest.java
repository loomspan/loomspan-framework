package ai.loomspan.internal.springai;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Real reader/validator/registered binder composition. This is not callback/manager or public dispatch. */
class OrdinaryToolArgumentsTest
{
    @Test void preservesNestedFractionsExponentsLargeIntegersAndNulls()
    {
        String input = """
                {"fraction":0.12345678901234567890123456789,
                 "nested":{"values":[1.234567890123456789e-30,9.876543210987654321e30,null]},
                 "integer":1234567890123456789012345678901234567890}
                """;
        var received = OrdinaryToolArguments.read(input);
        assertThat(received.get("fraction")).isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
        assertThat(received.get("integer")).isEqualTo(new BigInteger("1234567890123456789012345678901234567890"));
        assertThat(((Map<?, ?>) received.get("nested")).get("values")).isEqualTo(Arrays.asList(
                new BigDecimal("1.234567890123456789e-30"), new BigDecimal("9.876543210987654321e30"), null));
    }

    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", " ", "{", "null", "\"\"", "\"text\"", "[]", "[{}]", "1", "1.25", "true", "false",
            "{} {}", "{} 1", "{} \"text\"", "{} true", "{} null", "{} trailing garbage"})
    void rejectsInvalidWholeDocuments(String input)
    {
        assertThatThrownBy(() -> OrdinaryToolArguments.read(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsObjectControlsWhitespaceAndNestedNull()
    {
        assertThat(OrdinaryToolArguments.read("{} \t\r\n")).isEmpty();
        assertThat(OrdinaryToolArguments.read("{\"value\":null} \t")).containsEntry("value", null);
    }

    static class DecimalTarget
    {
        int calls;
        BigDecimal received;
        Map<String, Object> nested;
        @SkillMethod(namespace="numbers", name="decimal", description="Exact decimal input")
        public Object decimal(BigDecimal value, @SkillParam(required=false) Map<String, Object> nested)
        {
            calls++;
            received = value;
            this.nested = nested;
            return value;
        }
    }

    @Test void exactValidationPreventsJavaEffectsAtRoundedBoundariesAndInvalidDocuments()
    {
        var target = new DecimalTarget();
        var factory = new StaticListableBeanFactory();
        factory.addBean("decimal", target);
        var processor = SkillMethodBeanPostProcessor.create(LoomspanJacksonCodecs.defaults().applicationConversion(),
                LoomspanJacksonCodecs.defaults().schemaTree());
        processor.setBeanFactory(factory);
        processor.postProcessAfterInitialization(target, "decimal");
        var metadata = processor.capabilities().getFirst();
        var canonical = new StandardSchemaCompiler().compile("""
                {"type":"object","properties":{
                 "value":{"type":"number","minimum":0,"maximum":1},
                 "nested":{"type":"object","additionalProperties":{}}},
                 "required":["value"],"additionalProperties":false}
                """, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.AUTHORED);
        var contract = new SkillInputContract(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT, canonical);
        var validator = new SkillInputValidator();
        java.util.function.Function<String, Object> invoke = raw -> {
            var input = OrdinaryToolArguments.read(raw);
            var result = validator.validate(input, contract);
            if (!result.valid()) throw new IllegalArgumentException("Invalid ordinary arguments: " + result.issues());
            return metadata.invoker().invoke(result.normalizedInput());
        };
        invoke.apply("""
                {"value":0.12345678901234567890123456789,
                 "nested":{"fraction":9.876543210987654321e30,"values":[1.234567890123456789e-30,null]}}
                """);
        assertThat(target.calls).isEqualTo(1);
        assertThat(target.received).isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
        assertThat(target.nested.get("fraction")).isEqualTo(new BigDecimal("9.876543210987654321e30"));
        assertThat(target.nested.get("values")).isEqualTo(Arrays.asList(new BigDecimal("1.234567890123456789e-30"), null));
        for (String invalid : List.of("{\"value\":1.000000000000000000000000000001}",
                "{\"value\":-0.000000000000000000000000000001}", "{} {}", "{} 1", "{} garbage",
                "null", "\"\"", "[]", "1", "false", ""))
        {
            assertThatThrownBy(() -> invoke.apply(invalid)).isInstanceOf(IllegalArgumentException.class);
            assertThat(target.calls).isEqualTo(1);
        }
    }
}
