package ai.loomspan.internal.outputvalidation;

import ai.loomspan.internal.outputschema.*;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class OutputValidationPolicyTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void sharedPolicyPreservesBudgetBoundaries(int budget) {
        for (int attempt = 1; attempt <= budget + 1; attempt++) {
            var outcome = OutputValidationPolicy.linterOutcome("skill", "regex", attempt, budget, false, "authored detail");
            assertThat(outcome.status()).isEqualTo(attempt <= budget ? LinterOutcomeStatus.RETRYING : LinterOutcomeStatus.EXHAUSTED);
            assertThat(outcome.retryCount()).isEqualTo(attempt - 1);
            assertThat(outcome.detail()).isEqualTo("authored detail");
            assertThat(OutputValidationPolicy.linterOutcome("skill", "regex", attempt, budget, true, "passed").status()).isEqualTo(LinterOutcomeStatus.PASSED);
        }
        var immutable = OutputValidationPolicy.immutableLinterOutcome("skill", "regex", budget, false, "immutable");
        assertThat(immutable.status()).isEqualTo(LinterOutcomeStatus.EXHAUSTED);
        assertThat(immutable.attempt()).isEqualTo(1); assertThat(immutable.retryCount()).isZero();
    }

    @Test
    void schemaIssueLimitAndFailureFactsRemainCallerChoices() {
        var issue = new OutputSchemaValidationIssue("$.field", "bad", "field", "wrong", null, null, null, null, null, null, null);
        var issues = List.of(issue, issue, issue, issue, issue, issue);
        var ordinary = OutputValidationPolicy.schemaOutcome("skill", 3, 2, false, OutputSchemaFailureMode.INVALID_JSON, issues, 4);
        assertThat(ordinary.issues()).hasSize(4); assertThat(ordinary.failureMode()).isEqualTo(OutputSchemaFailureMode.INVALID_JSON);
        assertThat(ordinary.status()).isEqualTo(OutputSchemaOutcomeStatus.EXHAUSTED); assertThat(ordinary.retryCount()).isEqualTo(2);
        assertThat(OutputValidationPolicy.schemaOutcome("skill", 1, 2, false, OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, issues, Integer.MAX_VALUE).issues()).hasSize(6);
        assertThat(OutputValidationPolicy.schemaOutcome("skill", 1, 0, true, null, List.of(), 4).status()).isEqualTo(OutputSchemaOutcomeStatus.PASSED);
    }

    @Test
    void regexUsesFullStringMatchingAndPreservesNullableCallerBoundary() {
        assertThat(OutputValidationPolicy.matches(Pattern.compile("OK"), "OK suffix")).isFalse();
        assertThat(OutputValidationPolicy.matches(Pattern.compile("(?s).*OK.*"), "first\nOK\nlast")).isTrue();
        assertThat(OutputValidationPolicy.matches(Pattern.compile(""), "")).isTrue();
        assertThat(OutputValidationPolicy.matches(Pattern.compile(""), null)).isFalse();
    }
}
