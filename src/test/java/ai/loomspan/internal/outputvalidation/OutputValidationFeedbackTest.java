package ai.loomspan.internal.outputvalidation;

import ai.loomspan.internal.outputschema.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OutputValidationFeedbackTest {
    @Test
    void preservesWholeAndContributionCorrectionFormatsExactly() {
        var issue = new OutputSchemaValidationIssue("$.amount", "wrong type", "amount", "wrong", "number", "string", null, null, null, null, null);
        var result = OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, List.of(issue));
        String common = "The previous response is valid JSON but does not satisfy the configured output_schema.\nIssues:\n"
                + "- Path \"$.amount\": expected \"number\", received \"string\".\n"
                + "Preserve all already-valid structure and values visible in the previous assistant response.\n"
                + "Do NOT call any tools again; use the data already returned by completed tool calls.\n";
        assertThat(OutputValidationFeedback.ordinarySchemaCorrection(result, false)).isEqualTo(common
                + "Return one complete corrected JSON object only, with no explanation, markdown, or code fences.");
        assertThat(OutputValidationFeedback.ordinarySchemaCorrection(result, true)).isEqualTo(common
                + "Return only the corrected model-owned JSON contribution. Omit every framework-bound destination; Framework supplies those values.");
    }

    @Test
    void preservesParserEscapingLocationsAndSeparateSummaryFormats() {
        var issue = new OutputSchemaValidationIssue("$", "bad", "canonical", OutputSchemaValidator.INVALID_JSON, null, null,
                "Unexpected \"quote\"", 1, 2, 3L, "\\quoted\"");
        var result = OutputSchemaValidationResult.failed(OutputSchemaFailureMode.INVALID_JSON, List.of(issue));
        assertThat(OutputValidationFeedback.ordinarySchemaCorrection(result, false)).startsWith(
                "The previous response could not be parsed as JSON.\nIssues:\n- Path \"$\": invalid JSON. Parser reason: \"Unexpected \\\"quote\\\"\". Location: line 1, column 2, character offset 3. Nearby fragment (escaped JSON string): \"\\\\quoted\\\"\".\n");
        assertThat(OutputValidationFeedback.ordinarySchemaSummary(List.of(issue), 4)).isEqualTo("$: invalid JSON");
        assertThat(OutputValidationFeedback.planningSchemaSummary(List.of(issue))).isEqualTo("canonical: bad");
        assertThat(OutputValidationFeedback.planningSchemaSummary(List.of())).isEqualTo("unknown schema validation error");
        assertThat(OutputValidationFeedback.ordinarySchemaSummary(List.of(), 4)).isEqualTo("no validation issues recorded");
    }

    @Test
    void preservesIssueLimitsAndUnicodeResourceBound() {
        var issue = new OutputSchemaValidationIssue("$.field", "😀".repeat(512), null, "wrong", null, null, null, null, null, null, null);
        var result = OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, List.of(issue, issue, issue, issue, issue, issue));
        var rendered = OutputValidationFeedback.ordinarySchemaCorrection(result, false);
        assertThat(rendered.codePointCount(0, rendered.length())).isLessThanOrEqualTo(2048);
        assertThat(rendered).contains("additional issue(s) omitted.").endsWith("Return one complete corrected JSON object only, with no explanation, markdown, or code fences.");
        assertThat(OutputValidationFeedback.planningSchemaSummary(result.issues()).split("; ")).hasSize(3);
        var unknown = new OutputSchemaValidationIssue("$.authoredName", "bad", null, OutputSchemaValidator.UNKNOWN_PROPERTY, null, null, null, null, null, null, null);
        assertThat(OutputValidationFeedback.ordinarySchemaSummary(List.of(unknown), 4)).isEqualTo("output object: validation issue code unknown_property");
    }
}
