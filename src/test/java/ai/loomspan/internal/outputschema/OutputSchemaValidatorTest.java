package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutputSchemaValidatorTest {
    private final OutputSchemaValidator validator = new OutputSchemaValidator();

    @Test
    void reportsBoundedParserFactsForMalformedJson() {
        String candidate = "{\"estimatedTotal\":49 + 0,\n\"note\":\"🚀\\nline\"} // comment";
        var issue = validator.validate(candidate, schema("{\"type\":\"object\"}")).issues().getFirst();
        assertThat(issue.code()).isEqualTo(OutputSchemaValidator.INVALID_JSON);
        assertThat(issue.reason()).isNotBlank();
        assertThat(issue.reason().codePointCount(0, issue.reason().length())).isLessThanOrEqualTo(240);
        assertThat(issue.line()).isPositive();
        assertThat(issue.column()).isPositive();
        assertThat(issue.characterOffset()).isNotNegative();
        assertThat(issue.fragment()).startsWith("\"").endsWith("\"").doesNotContain("\n");
        assertThat(issue.fragment().codePointCount(0, issue.fragment().length())).isLessThanOrEqualTo(120);
    }

    @Test
    void blankOutputHasNoInventedParserLocation() {
        for (String candidate : new String[]{null, "", " \t\n"}) {
            var issue = validator.validate(candidate, schema("{\"type\":\"object\"}")).issues().getFirst();
            assertThat(issue.code()).isEqualTo("invalid_json");
            assertThat(issue).extracting(OutputSchemaValidationIssue::line, OutputSchemaValidationIssue::column,
                    OutputSchemaValidationIssue::characterOffset, OutputSchemaValidationIssue::fragment).containsOnlyNulls();
        }
    }

    @Test
    void parserAndSchemaFactsRespectUnicodeBounds() {
        String malformed = "{\"🚀\":\"" + "🚀".repeat(200) + "\",\"value\":oops}";
        var parserIssue = validator.validate(malformed, schema("{\"type\":\"object\"}")).issues().getFirst();
        assertThat(parserIssue.fragment().codePointCount(0, parserIssue.fragment().length())).isLessThanOrEqualTo(120);
        assertThat(parserIssue.fragment().codePoints()).noneMatch(c -> c >= 0xD800 && c <= 0xDFFF);
        var enumContract = valueSchema("{\"type\":\"string\",\"enum\":[\"" + "🚀".repeat(300) + "\"]}");
        var issue = validator.validate("{\"value\":\"different\"}", enumContract).issues().getFirst();
        assertThat(issue.message().codePointCount(0, issue.message().length())).isLessThanOrEqualTo(512);
        if (issue.expected() != null) assertThat(issue.expected().codePointCount(0, issue.expected().length())).isLessThanOrEqualTo(256);
    }

    @Test
    void usesExactIndependentPropertyNamesAndStandardRequiredness() {
        var contract = schema("""
                {"type":"object","properties":{"name":{"type":"string"},"Name":{"type":"integer"},"a.b":{"type":"string"},"🚀":{"type":"boolean"}},
                 "required":["name","Name","a.b","🚀"],"additionalProperties":false}
                """);
        assertThat(validator.validate("{\"name\":\"x\",\"Name\":2,\"a.b\":\"exact\",\"🚀\":true}", contract).valid()).isTrue();
        var missing = validator.validate("{\"Name\":2,\"a.b\":\"exact\",\"🚀\":true}", contract);
        assertThat(missing.valid()).isFalse();
        assertThat(missing.issues()).anySatisfy(issue -> assertThat(issue.code()).isEqualTo("required"));
        assertThat(validator.validate("{\"name\":\"x\",\"Name\":2,\"a.b\":1,\"🚀\":false}", contract).issues())
                .anySatisfy(issue -> assertThat(issue.path()).isEqualTo("/a.b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"additionalProperties\":true", ",\"additionalProperties\":false"})
    void rootAndNestedObjectOpennessUsesStandardDefaults(String openness) {
        var contract = schema("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"object\"" + openness + "}}" + openness + "}");
        boolean open = !openness.endsWith("false");
        assertThat(validator.validate("{\"extra\":1}", contract).valid()).isEqualTo(open);
        assertThat(validator.validate("{\"nested\":{\"extra\":1}}", contract).valid()).isEqualTo(open);
        assertThat(validator.validate("{\"nested\":{}}", contract).valid()).isTrue();
    }

    @Test
    void omissionAndNullAreIndependentWithoutCoercion() {
        var contract = schema("""
                {"type":"object","properties":{"optional":{"type":"string"},"requiredNullable":{"type":["string","null"]}},"required":["requiredNullable"],"additionalProperties":false}
                """);
        assertThat(validator.validate("{\"requiredNullable\":null}", contract).valid()).isTrue();
        assertThat(validator.validate("{\"optional\":null,\"requiredNullable\":null}", contract).valid()).isFalse();
        assertThat(validator.validate("{}", contract).valid()).isFalse();
        assertThat(validator.validate("{\"requiredNullable\":1}", contract).valid()).isFalse();
    }

    @Test
    void validatesOrdinaryTypesScalarEnumsAndNestedHomogeneousArrays() {
        var contract = valueSchema("""
                {"type":"array","items":{"type":"object","properties":{"count":{"type":"integer","enum":[1,2.0]},"active":{"type":"boolean","enum":[true]},"status":{"type":["string","null"],"enum":["OPEN",null]}},"required":["count","active","status"],"additionalProperties":false}}
                """);
        assertThat(validator.validate("{\"value\":[{\"count\":2,\"active\":true,\"status\":null}]}", contract).valid()).isTrue();
        var failures = validator.validate("{\"value\":[{\"count\":1.5,\"active\":\"true\",\"status\":\"OTHER\"}]}", contract);
        assertThat(failures.valid()).isFalse();
        assertThat(failures.issues()).extracting(OutputSchemaValidationIssue::path)
                .contains("/value/0/count", "/value/0/active", "/value/0/status");
    }

    @Test
    void locallyEnforcesEveryApprovedAssertionThatAnthropicMayOmit() {
        String[][] cases = {
                {"{\"type\":\"number\",\"minimum\":2}", "1", "2"},
                {"{\"type\":\"number\",\"maximum\":2}", "3", "2"},
                {"{\"type\":\"string\",\"minLength\":2}", "\"x\"", "\"xy\""},
                {"{\"type\":\"string\",\"maxLength\":2}", "\"xyz\"", "\"xy\""},
                {"{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"minItems\":2}", "[1]", "[1,2]"},
                {"{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"maxItems\":2}", "[1,2,3]", "[1,2]"}
        };
        for (String[] fixture : cases) {
            var contract = valueSchema(fixture[0]);
            assertThat(validator.validate("{\"value\":" + fixture[1] + "}", contract).valid()).as(fixture[0]).isFalse();
            assertThat(validator.validate("{\"value\":" + fixture[2] + "}", contract).valid()).as(fixture[0]).isTrue();
        }
        assertThat(validator.validate("{\"value\":\"🚀\"}", valueSchema("{\"type\":\"string\",\"maxLength\":1}")).valid()).isTrue();
    }

    @Test
    void enforcesFormatButKeepsDescriptionAsAnnotation() {
        var contract = valueSchema("{\"type\":\"string\",\"format\":\"date\",\"description\":\"The word birthday is descriptive.\"}");
        assertThat(validator.validate("{\"value\":\"not-a-date\"}", contract).valid()).isFalse();
        assertThat(validator.validate("{\"value\":\"2026-11-13\"}", contract).valid()).isTrue();
    }

    @Test
    void preservesExactDecimalBoundsEvenWithCallerSuppliedLossyMapper() {
        var lossless = new OutputSchemaValidator(JsonMapper.builder().build());
        var upper = valueSchema("{\"type\":\"number\",\"maximum\":1}");
        var lower = valueSchema("{\"type\":\"number\",\"minimum\":-1}");
        for (String number : new String[]{"1.0000000000000001", "10000000000000001e-16", "1.00000000000000000000000000000000000000001"}) {
            String candidate = "{\"value\":" + number + "}";
            assertThat(lossless.validate(candidate, upper).valid()).as(number).isFalse();
            assertThat(candidate).isEqualTo("{\"value\":" + number + "}");
        }
        for (String number : new String[]{"-1.0000000000000001", "-10000000000000001e-16"})
            assertThat(lossless.validate("{\"value\":" + number + "}", lower).valid()).as(number).isFalse();
        assertThat(lossless.validate("{\"value\":1.0000000000000000}", upper).valid()).isTrue();
        assertThat(lossless.validate("{\"value\":-1e0}", lower).valid()).isTrue();
        assertThat(lossless.validate("{\"value\":90071992547409931234567890123456789}", valueSchema("{\"type\":\"integer\"}")).valid()).isTrue();
        var nested = valueSchema("{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\",\"maximum\":1}},\"required\":[\"amount\"]}}");
        assertThat(lossless.validate("{\"value\":[{\"amount\":1.0000000000000001}]}", nested).valid()).isFalse();
        assertThat(lossless.validate("{\"value\":[{\"amount\":1}]}", nested).valid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{} {}", "{} null", "{} trailing", "{} //comment"})
    void rejectsTrailingTokensAndGarbage(String candidate) {
        assertThat(validator.validate(candidate, schema("{\"type\":\"object\"}")).failureMode()).isEqualTo(OutputSchemaFailureMode.INVALID_JSON);
    }

    @Test
    void acceptsOnlyWhitespaceAfterOneJsonValue() {
        assertThat(validator.validate("{} \t\n\r", schema("{\"type\":\"object\"}")).valid()).isTrue();
    }

    @Test
    void operationalValidationFailureIsNeverAnInvalidInstanceVerdict() {
        var contract = mock(CanonicalSchema.class);
        when(contract.validate(any())).thenThrow(new IllegalStateException("validator unavailable"));
        assertThatThrownBy(() -> validator.validate("{}", contract)).isInstanceOf(IllegalStateException.class).hasMessage("validator unavailable");
    }

    private static CanonicalSchema valueSchema(String value) {
        return schema("{\"type\":\"object\",\"properties\":{\"value\":" + value + "},\"required\":[\"value\"],\"additionalProperties\":false}");
    }
    private static CanonicalSchema schema(String json) {
        return new StandardSchemaCompiler().compile(json, StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.AUTHORED);
    }
}
