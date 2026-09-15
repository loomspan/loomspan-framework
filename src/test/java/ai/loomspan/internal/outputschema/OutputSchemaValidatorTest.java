package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutputSchemaValidatorTest
{
    private final OutputSchemaValidator validator = new OutputSchemaValidator();

    @Test
    void reportsBoundedParserFactsForMalformedJson()
    {
        String candidate = """
                {"estimatedTotal": 49 + 0,
                 "note": "\ud83d\ude80\nline"} // comment
                """;

        OutputSchemaValidationIssue issue = validator.validate(candidate, objectSchema(Map.of(), List.of(), true))
                .issues().getFirst();

        assertThat(issue.code()).isEqualTo(OutputSchemaValidator.INVALID_JSON);
        assertThat(issue.reason()).isNotBlank();
        assertThat(issue.reason().codePointCount(0, issue.reason().length()))
                .isLessThanOrEqualTo(OutputSchemaValidationIssue.MAX_REASON_CODE_POINTS);
        assertThat(issue.line()).isPositive();
        assertThat(issue.column()).isPositive();
        assertThat(issue.characterOffset()).isNotNegative();
        assertThat(issue.fragment()).startsWith("\"").endsWith("\"");
        assertThat(issue.fragment().codePointCount(0, issue.fragment().length()))
                .isLessThanOrEqualTo(OutputSchemaValidationIssue.MAX_FRAGMENT_CODE_POINTS);
        assertThat(issue.fragment()).doesNotContain("\n");
        assertThat(issue.message()).doesNotContain("JacksonException", " at [Source:");
    }

    @Test
    void fallsBackWhenParserFactsAreUnavailable() throws Exception
    {
        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonException failure = mock(JacksonException.class);
        when(failure.getOriginalMessage()).thenReturn("  ");
        when(failure.getLocation()).thenReturn(TokenStreamLocation.NA);
        when(mapper.readTree("malformed")).thenThrow(failure);
        when(mapper.readTree("null-tree")).thenReturn(null);

        OutputSchemaValidationIssue issue = new OutputSchemaValidator(mapper)
                .validate("malformed", scalarSchema("string", false)).issues().getFirst();

        assertThat(issue.message()).isEqualTo("Response is not valid JSON.");
        assertThat(issue.reason()).isNull();
        assertThat(issue.line()).isNull();
        assertThat(issue.column()).isNull();
        assertThat(issue.characterOffset()).isNull();
        assertThat(issue.fragment()).isNull();

        OutputSchemaValidationIssue nullTree = new OutputSchemaValidator(mapper)
                .validate("null-tree", scalarSchema("string", false)).issues().getFirst();
        assertThat(nullTree.message()).isEqualTo("Response is not valid JSON.");
        assertThat(nullTree).extracting(OutputSchemaValidationIssue::reason,
                OutputSchemaValidationIssue::line,
                OutputSchemaValidationIssue::column,
                OutputSchemaValidationIssue::characterOffset,
                OutputSchemaValidationIssue::fragment)
                .containsOnlyNulls();

        OutputSchemaValidationIssue blank = validator.validate("  ", scalarSchema("string", false)).issues().getFirst();
        assertThat(blank).extracting(OutputSchemaValidationIssue::reason,
                OutputSchemaValidationIssue::line,
                OutputSchemaValidationIssue::column,
                OutputSchemaValidationIssue::characterOffset,
                OutputSchemaValidationIssue::fragment)
                .containsOnlyNulls();
    }

    @Test
    void capsParserAndSchemaFactsAtTheirUnicodeBoundaries() throws Exception
    {
        ObjectMapper mapper = mock(ObjectMapper.class);
        JacksonException failure = mock(JacksonException.class);
        TokenStreamLocation location = mock(TokenStreamLocation.class);
        when(failure.getOriginalMessage()).thenReturn("r".repeat(241));
        when(failure.getLocation()).thenReturn(location);
        when(location.getLineNr()).thenReturn(1);
        when(location.getColumnNr()).thenReturn(150);
        when(location.getCharOffset()).thenReturn(150L);
        String malformed = "x".repeat(300);
        when(mapper.readTree(malformed)).thenThrow(failure);

        OutputSchemaValidationIssue parserIssue = new OutputSchemaValidator(mapper)
                .validate(malformed, scalarSchema("string", false)).issues().getFirst();

        assertThat(parserIssue.reason().codePointCount(0, parserIssue.reason().length())).isEqualTo(240);
        assertThat(parserIssue.fragment().codePointCount(0, parserIssue.fragment().length())).isEqualTo(120);

        YamlSkillManifest.OutputSchemaManifest enumSchema = scalarSchema("string", false);
        enumSchema.setEnumValues(List.of("e".repeat(300)));
        OutputSchemaValidationIssue enumIssue = validator.validate("\"different\"", enumSchema).issues().getFirst();
        assertThat(enumIssue.expected().codePointCount(0, enumIssue.expected().length())).isEqualTo(256);
        assertThat(enumIssue.message().codePointCount(0, enumIssue.message().length()))
                .isLessThanOrEqualTo(OutputSchemaValidationIssue.MAX_MESSAGE_CODE_POINTS);
    }

    @Test
    void reportsFullPathExpectedAndActualSchemaFacts()
    {
        YamlSkillManifest.OutputSchemaManifest outbound = objectSchema(Map.of(), List.of(), true);
        YamlSkillManifest.OutputSchemaManifest returnLeg = objectSchema(Map.of(), List.of(), true);
        returnLeg.setNullable(true);
        YamlSkillManifest.OutputSchemaManifest transport = objectSchema(
                linkedMap("outbound", outbound, "returnLeg", returnLeg),
                List.of("outbound", "returnLeg"), false);
        YamlSkillManifest.OutputSchemaManifest root = objectSchema(
                Map.of("transport", transport), List.of("transport"), false);

        List<OutputSchemaValidationIssue> issues = validator.validate("""
                {"transport":{"outbound":"flight","returnLeg":"train"}}
                """, root).issues();

        assertThat(issues).extracting(OutputSchemaValidationIssue::path)
                .containsExactly("$.transport.outbound", "$.transport.returnLeg");
        assertThat(issues).extracting(OutputSchemaValidationIssue::expected)
                .containsExactly("object", "object or null");
        assertThat(issues).extracting(OutputSchemaValidationIssue::actual)
                .containsExactly("string", "string");
        assertThat(issues).extracting(OutputSchemaValidationIssue::message)
                .containsExactly("expected object, received string", "expected object or null, received string");
    }

    @Test
    void distinguishesArraysScalarsNumbersAndJsonNull()
    {
        Map<String, YamlSkillManifest.OutputSchemaManifest> properties = new LinkedHashMap<>();
        properties.put("arrayValue", arraySchema());
        properties.put("stringValue", scalarSchema("string", false));
        properties.put("integerValue", scalarSchema("integer", false));
        properties.put("numberValue", scalarSchema("number", false));
        properties.put("booleanValue", scalarSchema("boolean", false));
        properties.put("nonnullValue", scalarSchema("object", false));

        List<OutputSchemaValidationIssue> issues = validator.validate("""
                {"arrayValue":{},"stringValue":1,"integerValue":1.5,"numberValue":"1", "booleanValue":[],"nonnullValue":null}
                """, objectSchema(properties, List.copyOf(properties.keySet()), false)).issues();

        assertThat(issues).extracting(OutputSchemaValidationIssue::expected)
                .containsExactly("array", "string", "integer", "number", "boolean", "object");
        assertThat(issues).extracting(OutputSchemaValidationIssue::actual)
                .containsExactly("object", "integer", "number", "string", "array", "null");
    }

    @Test
    void reportsConstraintFactsForMissingUnknownEnumAndAmbiguousProperties()
    {
        YamlSkillManifest.OutputSchemaManifest status = scalarSchema("string", false);
        status.setEnumValues(List.of("OPEN", "CLOSED"));
        YamlSkillManifest.OutputSchemaManifest schema = objectSchema(
                linkedMap("vendorName", scalarSchema("string", false), "status", status),
                List.of("vendorName", "status"), false);

        List<OutputSchemaValidationIssue> issues = validator.validate("""
                {"status":"SECRET_SENTINEL","Status":"OPEN","extra":"SECRET_VALUE"}
                """, schema).issues();

        assertThat(issues).extracting(OutputSchemaValidationIssue::code)
                .containsExactly(
                        OutputSchemaValidator.AMBIGUOUS_PROPERTY,
                        OutputSchemaValidator.UNKNOWN_PROPERTY,
                        OutputSchemaValidator.MISSING_REQUIRED);
        assertThat(issues).extracting(OutputSchemaValidationIssue::path)
                .containsExactly("$", "$.extra", "$.vendorName");
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.expected()).isNotBlank();
            assertThat(issue.actual()).isNotBlank();
            assertThat(issue.message()).doesNotContain("SECRET_SENTINEL", "SECRET_VALUE");
        });

        OutputSchemaValidationIssue enumIssue = validator.validate(
                "{\"vendorName\":\"Acme\",\"status\":\"SECRET_SENTINEL\"}", schema).issues().getFirst();
        assertThat(enumIssue.code()).isEqualTo(OutputSchemaValidator.ENUM_MISMATCH);
        assertThat(enumIssue.expected()).contains("OPEN", "CLOSED");
        assertThat(enumIssue.actual()).isEqualTo("string");
        assertThat(enumIssue.message()).doesNotContain("SECRET_SENTINEL");
    }

    @Test
    void treatsPresenceAndNullabilityAsIndependentConstraints()
    {
        YamlSkillManifest.OutputSchemaManifest optionalObject = objectSchema(Map.of(), List.of(), false);
        YamlSkillManifest.OutputSchemaManifest requiredNullable = objectSchema(Map.of(), List.of(), false);
        requiredNullable.setNullable(true);
        YamlSkillManifest.OutputSchemaManifest schema = objectSchema(
                linkedMap("optionalObject", optionalObject, "requiredNullable", requiredNullable),
                List.of("requiredNullable"), false);

        assertThat(validator.validate("{\"requiredNullable\":null}", schema).valid()).isTrue();
        assertThat(validator.validate("{\"optionalObject\":{},\"requiredNullable\":null}", schema).valid()).isTrue();

        OutputSchemaValidationIssue nullOptional = validator.validate(
                "{\"optionalObject\":null,\"requiredNullable\":null}", schema).issues().getFirst();
        assertThat(nullOptional.code()).isEqualTo(OutputSchemaValidator.TYPE_MISMATCH);
        assertThat(nullOptional.path()).isEqualTo("$.optionalObject");

        OutputSchemaValidationIssue missingRequired = validator.validate("{}", schema).issues().getFirst();
        assertThat(missingRequired.code()).isEqualTo(OutputSchemaValidator.MISSING_REQUIRED);
        assertThat(missingRequired.path()).isEqualTo("$.requiredNullable");
    }

    @Test
    void doesNotEnforceFormatOrDescription()
    {
        YamlSkillManifest.OutputSchemaManifest date = scalarSchema("string", false);
        date.setFormat("date");
        date.setDescription("An ISO calendar date.");
        YamlSkillManifest.OutputSchemaManifest schema = objectSchema(Map.of("date", date), List.of("date"), false);

        assertThat(validator.validate("{\"date\":\"not-a-date\"}", schema).valid()).isTrue();
        assertThat(validator.validate("{\"date\":42}", schema).issues().getFirst().code())
                .isEqualTo(OutputSchemaValidator.TYPE_MISMATCH);
    }

    @Test
    void validatesArrayItemsRecursivelyWithIndexedPaths()
    {
        YamlSkillManifest.OutputSchemaManifest status = scalarSchema("string", false);
        status.setEnumValues(List.of("OPEN", "CLOSED"));
        YamlSkillManifest.OutputSchemaManifest row = objectSchema(
                linkedMap("amount", scalarSchema("number", false), "status", status),
                List.of("amount", "status"), false);
        YamlSkillManifest.OutputSchemaManifest rows = scalarSchema("array", false);
        rows.setItems(row);
        YamlSkillManifest.OutputSchemaManifest schema = objectSchema(Map.of("rows", rows), List.of("rows"), false);

        List<OutputSchemaValidationIssue> issues = validator.validate(
                "{\"rows\":[{\"amount\":\"many\",\"status\":\"UNKNOWN\"}]}", schema).issues();

        assertThat(issues).extracting(OutputSchemaValidationIssue::path)
                .containsExactly("$.rows[0].amount", "$.rows[0].status");
        assertThat(issues).extracting(OutputSchemaValidationIssue::code)
                .containsExactly(OutputSchemaValidator.TYPE_MISMATCH, OutputSchemaValidator.ENUM_MISMATCH);
    }

    @Test
    void respectsAdditionalPropertiesForNestedObjects()
    {
        YamlSkillManifest.OutputSchemaManifest open = objectSchema(
                Map.of("known", scalarSchema("string", false)), List.of(), true);
        YamlSkillManifest.OutputSchemaManifest closed = objectSchema(
                Map.of("known", scalarSchema("string", false)), List.of(), false);
        YamlSkillManifest.OutputSchemaManifest schema = objectSchema(
                linkedMap("open", open, "closed", closed), List.of("open", "closed"), false);

        assertThat(validator.validate("{\"open\":{\"extra\":1},\"closed\":{}}", schema).valid()).isTrue();
        OutputSchemaValidationIssue issue = validator.validate(
                "{\"open\":{},\"closed\":{\"extra\":1}}", schema).issues().getFirst();
        assertThat(issue.code()).isEqualTo(OutputSchemaValidator.UNKNOWN_PROPERTY);
        assertThat(issue.path()).isEqualTo("$.closed.extra");
    }

    @Test
    void escapesPathSignificantPropertyNamesWithoutCollisions()
    {
        YamlSkillManifest.OutputSchemaManifest nested = objectSchema(
                Map.of("b", scalarSchema("string", false)), List.of("b"), false);
        YamlSkillManifest.OutputSchemaManifest schema = objectSchema(
                linkedMap("a.b", scalarSchema("string", false), "a", nested), List.of("a.b", "a"), false);

        List<OutputSchemaValidationIssue> issues = validator.validate(
                "{\"a.b\":1,\"a\":{\"b\":2}}", schema).issues();

        assertThat(issues).extracting(OutputSchemaValidationIssue::path)
                .containsExactly("$[\"a.b\"]", "$.a.b");
    }

    private static YamlSkillManifest.OutputSchemaManifest objectSchema(
            Map<String, YamlSkillManifest.OutputSchemaManifest> properties,
            List<String> required,
            boolean additionalProperties)
    {
        YamlSkillManifest.OutputSchemaManifest schema = scalarSchema("object", false);
        schema.setProperties(properties);
        schema.setRequired(required);
        schema.setAdditionalProperties(additionalProperties);
        return schema;
    }

    private static YamlSkillManifest.OutputSchemaManifest arraySchema()
    {
        YamlSkillManifest.OutputSchemaManifest schema = scalarSchema("array", false);
        schema.setItems(scalarSchema("string", false));
        return schema;
    }

    private static YamlSkillManifest.OutputSchemaManifest scalarSchema(String type, boolean nullable)
    {
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType(type);
        schema.setNullable(nullable);
        return schema;
    }

    private static Map<String, YamlSkillManifest.OutputSchemaManifest> linkedMap(
            String firstName,
            YamlSkillManifest.OutputSchemaManifest first,
            String secondName,
            YamlSkillManifest.OutputSchemaManifest second)
    {
        Map<String, YamlSkillManifest.OutputSchemaManifest> values = new LinkedHashMap<>();
        values.put(firstName, first);
        values.put(secondName, second);
        return values;
    }
}
