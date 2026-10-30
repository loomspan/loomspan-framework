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
            "'default-a AnD(default-b oR default-c)','default-a and (default-b or default-c)'",
            "'default-androidCheck OR default-orderLookup AND default-candyParser','default-androidCheck or default-orderLookup and default-candyParser'"
    })
    void supportsOperatorAdjacencyCasingAndWholeTokenNames(String raw, String canonical)
    {
        assertThat(parser.parse(raw).canonical()).isEqualTo(canonical);
    }

    @ParameterizedTest
    @CsvSource({
            "' ',2,expression must not be blank",
            "'and default-a',1,reserved operator 'and'",
            "'OR',1,reserved operator 'OR'",
            "'default-a or',13,expected a skill name",
            "'default-a default-b',11,expected 'and'",
            "'()',2,empty parentheses",
            "'(default-a',11,expected ')'",
            "'default-a)',10,unmatched closing",
            "'default-a && default-b',11,expected 'and'",
            "'default-a || default-b',11,expected 'and'",
            "'default-a, default-b',1,invalid local name",
            "'\"a\"',1,expected a skill name",
            "'not default-a',1,invalid qualified skill reference"
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
