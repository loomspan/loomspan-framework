package ai.loomspan.internal.skill;

import com.schibsted.spt.data.jslt.JsltException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutorOutputTransformTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "\"\"", "\"returns\"", "true", "false", "42", "0.95",
            "9007199254740993123456789", "1234567890.1234567890123456789",
            "{\"Keep.Case\":[null,false,{},[],{\"keep.case\":\"intact\"}]}",
            "\"quotes: \\\" backslash: \\\\\""})
    void identityAcceptsEveryJsonRootAndPreservesValues(String json) {
        assertThat(compile(".").applyToOutput(" \r\n " + json + "\t ")).isEqualTo(json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "\"returns\"", "42", "0.95", "false"})
    void expressionCanPublishEveryJsonRoot(String expression) {
        assertThat(compile(expression).applyToOutput("{\"unused\":true}")).isEqualTo(expression);
    }

    @Test void lookupsAndObjectConstructionPreserveJsonSemantics() {
        assertThat(compile(".missing").applyToOutput("{}")).isEqualTo("null");
        assertThat(compile(".value").applyToOutput("{\"value\":\"returns\"}")).isEqualTo("\"returns\"");
        String string = "quotes: \" backslash: \\";
        var input = JSON.createObjectNode().put("value", string);
        String result = compile(".value").applyToOutput(input.toString());
        assertThat(result).isEqualTo(JSON.writeValueAsString(string));
        assertThat(JSON.readTree(result).textValue()).isEqualTo(string);
        assertThat(compile("{\"nil\":null,\"object\":{},\"array\":[]}").applyToOutput("{}"))
                .isEqualTo("{\"nil\":null,\"object\":{},\"array\":[]}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\r\n", "returns", "{\"open\":", "[1,]", "{} trailing", "{} {}", "1 2", "null true",
            "\"returns\" false", "{bare:1}", "'single'", "/* comment */ {}", "NaN", "01", "1."})
    void rejectsInvalidTextBeforeEvaluating(String text) {
        assertThatThrownBy(() -> compile("error(\"evaluation sentinel\")").applyToOutput(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one JSON value")
                .hasMessageNotContaining("evaluation sentinel");
    }

    @Test void reportsEvaluationFailureAndRejectsNonfiniteResults() {
        assertThatThrownBy(() -> compile("error(\"evaluation sentinel\")").applyToOutput("null"))
                .isInstanceOf(JsltException.class).hasMessageContaining("evaluation sentinel");
        assertThatThrownBy(() -> compile("1.0 / 0.0").applyToOutput("{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at / must be a finite JSON number");
        assertThatThrownBy(() -> compile("{\"nested\": [1.0 / 0.0]}").applyToOutput("{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at /nested/0 must be a finite JSON number");
    }

    @Test void reusesOnePreparedExpressionConcurrently() throws Exception {
        var prepared = compile("let copied = .value {\"original\": $copied, \"twice\": $copied + $copied}");
        try (var pool = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<String>>();
            for (int index = 0; index < 32; index++) {
                String input = "{\"value\":\"case-" + index + "\"}";
                results.add(pool.submit(() -> prepared.applyToOutput(input)));
            }
            for (int index = 0; index < results.size(); index++) {
                String input = "case-" + index;
                assertThat(results.get(index).get(5, TimeUnit.SECONDS))
                        .isEqualTo("{\"original\":\"" + input + "\",\"twice\":\"" + input + input + "\"}");
            }
        }
    }

    private static ExecutorTransform compile(String source) {
        return ExecutorTransform.compile(source, "executor output transform test");
    }
}
