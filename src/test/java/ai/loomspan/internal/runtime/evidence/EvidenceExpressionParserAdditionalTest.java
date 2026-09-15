package ai.loomspan.internal.runtime.evidence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceExpressionParserAdditionalTest
{
    private final EvidenceExpressionParser parser = new EvidenceExpressionParser();

    @ParameterizedTest
    @CsvSource({
            "'a AnD(b oR c)','a and (b or c)'",
            "'androidCheck OR orderLookup AND candyParser','androidCheck or orderLookup and candyParser'"
    })
    void supportsOperatorAdjacencyCasingAndWholeTokenNames(String raw, String canonical)
    {
        assertThat(parser.parse(raw).canonical()).isEqualTo(canonical);
    }

    @ParameterizedTest
    @CsvSource({
            "' ',2,expression must not be blank",
            "'and a',1,reserved operator 'and'",
            "'OR',1,reserved operator 'OR'",
            "'a or',5,expected a skill name",
            "'a b',3,expected 'and'",
            "'()',2,empty parentheses",
            "'(a',3,expected ')'",
            "'a)',2,unmatched closing",
            "'a && b',3,expected 'and'",
            "'a || b',3,expected 'and'",
            "'a, b',2,expected 'and'",
            "'\"a\"',1,expected a skill name",
            "'not a',5,expected 'and'"
    })
    void reportsMalformedCategoryAndPreciseColumn(String raw, int column, String detail)
    {
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(EvidenceExpressionParser.ParseException.class)
                .satisfies(ex ->
                {
                    EvidenceExpressionParser.ParseException parseException = (EvidenceExpressionParser.ParseException) ex;
                    assertThat(parseException.column()).isEqualTo(column);
                    assertThat(parseException).hasMessageContaining(detail);
                });
    }
}
