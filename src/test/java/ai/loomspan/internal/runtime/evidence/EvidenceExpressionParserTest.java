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

    @Test void contextualLeavesPreserveGrammarTruthAndOriginalColumnsWithoutContextLeakage()
    {
        String raw = "  first and (second or shared:third)";
        var expression = parser.parse(raw, "sales");
        assertThat(expression.canonical()).isEqualTo("sales:first and (sales:second or shared:third)");
        assertThat(expression.referencedSkills()).containsExactly("sales:first", "sales:second", "shared:third");
        assertThat(expression.evaluate(Set.of("sales:first", "sales:second"))).isTrue();
        assertThat(expression.evaluate(Set.of("sales:first", "shared:third"))).isTrue();
        assertThat(expression.evaluate(Set.of("sales:first"))).isFalse();
        var all = (EvidenceExpression.AllOf) expression;
        assertThat(((EvidenceExpression.Skill) all.expressions().getFirst()).column()).isEqualTo(3);
        var any = (EvidenceExpression.AnyOf) all.expressions().get(1);
        assertThat(((EvidenceExpression.Skill) any.expressions().getFirst()).column()).isEqualTo(raw.indexOf("second") + 1);
        assertThat(parser.parse("first", "other").canonical()).isEqualTo("other:first");
        assertThatThrownBy(() -> parser.parse("first")).isInstanceOf(EvidenceExpressionParser.ParseException.class);
        assertThatThrownBy(() -> new EvidenceExpression.Skill("first", 1)).isInstanceOf(IllegalArgumentException.class);
        for (String operator : Set.of("and", "AND", "Or", "OR"))
            assertThatThrownBy(() -> parser.parse(operator, "sales")).hasMessageContaining("reserved operator");
        assertThat(parser.parse("sales:and or and:search", "other").canonical()).isEqualTo("sales:and or and:search");
        assertThatThrownBy(() -> parser.parse(" first and )", "sales"))
                .isInstanceOf(EvidenceExpressionParser.ParseException.class)
                .extracting(ex -> ((EvidenceExpressionParser.ParseException) ex).column()).isEqualTo(12);
    }


    @Test
    void qualifiedOperatorNamesRemainLeavesInOwningNamespace()
    {
        var disjunction = parser.parse("orders:and or lookup", "orders");
        assertThat(disjunction).isInstanceOf(EvidenceExpression.AnyOf.class);
        var alternatives = ((EvidenceExpression.AnyOf) disjunction).expressions();
        assertThat(alternatives).hasSize(2).allMatch(EvidenceExpression.Skill.class::isInstance);
        assertThat(disjunction.referencedSkills()).containsExactly("orders:and", "orders:lookup");
        assertThat(disjunction.canonical()).isEqualTo("orders:and or orders:lookup");
        assertThat(disjunction.evaluate(Set.of("orders:and"))).isTrue();
        assertThat(disjunction.evaluate(Set.of("orders:lookup"))).isTrue();
        assertThat(disjunction.evaluate(Set.of())).isFalse();
        assertThat(((EvidenceExpression.Skill) alternatives.getFirst()).column()).isEqualTo(1);
        assertThat(((EvidenceExpression.Skill) alternatives.get(1)).column()).isEqualTo(15);

        var conjunction = parser.parse("orders:or and lookup", "orders");
        assertThat(conjunction).isInstanceOf(EvidenceExpression.AllOf.class);
        assertThat(((EvidenceExpression.AllOf) conjunction).expressions()).hasSize(2);
        assertThat(conjunction.referencedSkills()).containsExactly("orders:or", "orders:lookup");
        assertThat(conjunction.canonical()).isEqualTo("orders:or and orders:lookup");
        assertThat(conjunction.evaluate(Set.of("orders:or", "orders:lookup"))).isTrue();
        assertThat(conjunction.evaluate(Set.of("orders:or"))).isFalse();
        assertThat(parser.parse("orders:and or orders:or", "orders").referencedSkills())
                .containsExactly("orders:and", "orders:or");
        for (String operator : Set.of("and", "or")) {
            assertThatThrownBy(() -> parser.parse(operator, "orders"))
                    .hasMessageContaining("reserved operator")
                    .extracting(ex -> ((EvidenceExpressionParser.ParseException) ex).column()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "default:a,default:a",
            "default:a AND default:b AND default:c,default:a and default:b and default:c",
            "default:a Or default:b OR default:c,default:a or default:b or default:c",
            "default:a or default:b and default:c,default:a or default:b and default:c",
            "'(default:a or default:b) and default:c','(default:a or default:b) and default:c'",
            "'default:a AND (default:b Or default:c)','default:a and (default:b or default:c)'",
            "default:android-check or default:order-lookup and default:candy-parser,default:android-check or default:order-lookup and default:candy-parser"
    })
    void parsesAndCanonicallyRendersSupportedExpressions(String raw, String canonical)
    {
        assertThat(parser.parse(raw).canonical()).isEqualTo(canonical);
    }

    @Test
    void evaluatesPrecedenceAndAlternatives()
    {
        EvidenceExpression expression = parser.parse("default:classify-incident and (default:investigate-network or default:investigate-app)");
        assertThat(expression.referencedSkills())
                .containsExactly("default:classify-incident", "default:investigate-network", "default:investigate-app");
        assertThat(expression.evaluate(Set.of("default:classify-incident", "default:investigate-network"))).isTrue();
        assertThat(expression.evaluate(Set.of("default:classify-incident", "default:investigate-app"))).isTrue();
        assertThat(expression.evaluate(Set.of("default:classify-incident"))).isFalse();
        assertThat(expression.evaluate(Set.of("default:investigate-network"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "' ',2",
            "'and default:a',1",
            "'default:a or',13",
            "'default:a default:b',11",
            "'()',2",
            "'(default:a',11",
            "'default:a)',10",
            "'default:a && default:b',11",
            "'default:a || default:b',11",
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
