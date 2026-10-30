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
            "default-a,default-a",
            "default-a AND default-b AND default-c,default-a and default-b and default-c",
            "default-a Or default-b OR default-c,default-a or default-b or default-c",
            "default-a or default-b and default-c,default-a or default-b and default-c",
            "'(default-a or default-b) and default-c','(default-a or default-b) and default-c'",
            "'default-a AND (default-b Or default-c)','default-a and (default-b or default-c)'",
            "default-androidCheck or default-orderLookup and default-candyParser,default-androidCheck or default-orderLookup and default-candyParser"
    })
    void parsesAndCanonicallyRendersSupportedExpressions(String raw, String canonical)
    {
        assertThat(parser.parse(raw).canonical()).isEqualTo(canonical);
    }

    @Test
    void evaluatesPrecedenceAndAlternatives()
    {
        EvidenceExpression expression = parser.parse("default-classifyIncident and (default-investigateNetwork or default-investigateApp)");
        assertThat(expression.referencedSkills())
                .containsExactly("default-classifyIncident", "default-investigateNetwork", "default-investigateApp");
        assertThat(expression.evaluate(Set.of("default-classifyIncident", "default-investigateNetwork"))).isTrue();
        assertThat(expression.evaluate(Set.of("default-classifyIncident", "default-investigateApp"))).isTrue();
        assertThat(expression.evaluate(Set.of("default-classifyIncident"))).isFalse();
        assertThat(expression.evaluate(Set.of("default-investigateNetwork"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "' ',2",
            "'and default-a',1",
            "'default-a or',13",
            "'default-a default-b',11",
            "'()',2",
            "'(default-a',11",
            "'default-a)',10",
            "'default-a && default-b',11",
            "'default-a || default-b',11",
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
