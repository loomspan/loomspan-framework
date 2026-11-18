package ai.loomspan.internal.skill;

import com.schibsted.spt.data.jslt.JsltException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ai.loomspan.testkit.ExecutorTransformFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ExecutorInputTransformTest {
    @Test void evaluatesAcceptanceExpression() throws Exception {
        assertThat(compile(acceptanceExpression()).applyToInput(businessInput())).isEqualTo(executorOutput());
    }

    @Test void preservesConstructedValuesAndExactKeys() {
        var result = compile("""
                {
                  "Keep.Case": {"a/b": null, "a~b": {}, "": []},
                  "elements": [null, false, "text", 1],
                  "literal": "${/input/notResolved}"
                }
                """).applyToInput(Map.of());
        assertThat(result).containsEntry("elements", Arrays.asList(null, false, "text", 1))
                .containsEntry("literal", "${/input/notResolved}");
        assertThat(result.get("Keep.Case")).isInstanceOfSatisfying(Map.class, map -> {
            assertThat(map).containsEntry("a/b", null).containsEntry("a~b", Map.of()).containsEntry("", List.of());
        });
        assertThat(compile("{}").applyToInput(Map.of("ignored", "value"))).isEmpty();
        assertThat(compile(".").applyToInput(Map.of())).isEmpty();
    }

    @Test void transfersFiniteScalarsWithoutNumericRounding() {
        var decimal = new BigDecimal("1234567890.1234567890123456789");
        var big = new BigInteger("9007199254740993123456789");
        var values = Arrays.asList(null, true, "12", (byte) 10, (short) 11, 12, 9007199254740993L,
                1.25f, 2.5d, big, decimal);
        var result = compile("{\"copied\": .values}").applyToInput(Map.of("values", values));
        var copied = (List<?>) result.get("copied");
        assertThat(copied).hasSize(11);
        assertThat(copied.subList(0, 3)).isEqualTo(Arrays.asList(null, true, "12"));
        assertThat(copied.subList(3, 7).stream().map(Number.class::cast).map(Number::longValue))
                .containsExactly(10L, 11L, 12L, 9007199254740993L);
        assertThat(copied.subList(7, 9).stream().map(Number.class::cast).map(Number::doubleValue))
                .containsExactly(1.25d, 2.5d);
        assertThat(copied.get(9)).isEqualTo(big);
        assertThat(copied.get(10)).isEqualTo(decimal);
    }

    @Test void rejectsRuntimeHandlesWithoutInspectingThem() {
        var resource = new ByteArrayResource(new byte[0]) {
            @Override public String getDescription() { throw new AssertionError("handle inspected"); }
        };
        var arbitrary = new Object() {
            public String getPayload() { throw new AssertionError("getter invoked"); }
            @Override public String toString() { throw new AssertionError("formatted"); }
        };
        var transform = compile("error(\"must reject input first\")");
        for (Object invalid : List.of(resource, new ByteArrayInputStream(new byte[0]), new byte[0], arbitrary,
                new String[0], Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThatThrownBy(() -> transform.applyToInput(Map.of("a/b~", List.of(invalid))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContainingAll("/a~1b~0/0", invalid.getClass().getTypeName());
        }
        assertThatThrownBy(() -> transform.applyToInput(Map.of("nested", Map.of(arbitrary, "value"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("/nested", "non-string object key", arbitrary.getClass().getTypeName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "\"text\"", "false", "[]", "null"})
    void requiresObjectResult(String source) {
        assertThatThrownBy(() -> compile(source).applyToInput(Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("result must be a JSON object");
    }

    @Test void failsOnEvaluationErrorsAndConstructedNonFiniteNumbers() {
        assertThatThrownBy(() -> compile("error(\"fixture failure\")").applyToInput(Map.of()))
                .isInstanceOf(JsltException.class).hasMessageContaining("fixture failure");
        assertThatThrownBy(() -> compile("{\"non-finite\": [1.0 / 0.0]}").applyToInput(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll("/non-finite/0", "finite JSON number");
    }

    @Test void isolatesConcurrentApplicationsOfOnePreparedExpression() throws Exception {
        var prepared = compile("let copied = .value {\"original\": $copied, \"twice\": $copied + $copied}");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<Map<String, Object>>>();
            for (int index = 0; index < 100; index++) {
                String input = "case-" + index;
                results.add(executor.submit(() -> prepared.applyToInput(Map.of("value", input))));
            }
            for (int index = 0; index < results.size(); index++) {
                String input = "case-" + index;
                assertThat(results.get(index).get(5, TimeUnit.SECONDS))
                        .isEqualTo(Map.of("original", input, "twice", input + input));
            }
        }
    }

    private static ExecutorTransform compile(String source) {
        return ExecutorTransform.compile(source, "executor transform test");
    }
}
