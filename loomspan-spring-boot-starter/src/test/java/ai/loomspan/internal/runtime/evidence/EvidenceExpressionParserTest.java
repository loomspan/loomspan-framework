package ai.loomspan.internal.runtime.evidence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceExpressionParserTest
{
    private final EvidenceExpressionParser parser = new EvidenceExpressionParser();

    @ParameterizedTest
    @CsvSource({
            "a,a",
            "a AND b AND c,a and b and c",
            "a Or b OR c,a or b or c",
            "a or b and c,a or b and c",
            "'(a or b) and c','(a or b) and c'",
            "'a AND (b Or c)','a and (b or c)'",
            "androidCheck or orderLookup and candyParser,androidCheck or orderLookup and candyParser"
    })
    void parsesAndCanonicallyRendersSupportedExpressions(String raw, String canonical)
    {
        assertThat(parser.parse(raw).canonical()).isEqualTo(canonical);
    }

    @Test
    void evaluatesPrecedenceAndAlternatives()
    {
        EvidenceExpression expression = parser.parse("classifyIncident and (investigateNetwork or investigateApp)");
        assertThat(expression.referencedSkills())
                .containsExactly("classifyIncident", "investigateNetwork", "investigateApp");
        assertThat(expression.evaluate(Set.of("classifyIncident", "investigateNetwork"))).isTrue();
        assertThat(expression.evaluate(Set.of("classifyIncident", "investigateApp"))).isTrue();
        assertThat(expression.evaluate(Set.of("classifyIncident"))).isFalse();
        assertThat(expression.evaluate(Set.of("investigateNetwork"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "' ',2",
            "'and a',1",
            "'a or',5",
            "'a b',3",
            "'()',2",
            "'(a',3",
            "'a)',2",
            "'a && b',3",
            "'a || b',3",
            "'\"a\"',1"
    })
    void rejectsMalformedExpressionsWithPreciseColumns(String raw, int column)
    {
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(EvidenceExpressionParser.ParseException.class)
                .extracting(ex -> ((EvidenceExpressionParser.ParseException) ex).column())
                .isEqualTo(column);
    }
}
