package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillInputValidatorTest {

    private final SkillInputValidator validator = new SkillInputValidator();

    @Test
    void exactPolicyUsesLiteralKeysAndKeepsCompleteReceivingConstraints() {
        var contract = new SkillInputContractResolver().resolveJavaCapability("""
                {"type":"object","properties":{"a.b":{"type":"integer"},"a":{"type":"object",
                 "properties":{"b":{"type":"integer"}},"additionalProperties":false},
                 "bound":{"type":"object","properties":{"kind":{"type":"string","enum":["accepted"]}},
                 "required":["kind"],"additionalProperties":false}},"additionalProperties":false}
                """);
        var accepted = validator.validateExact(Map.of("a.b", 1, "a", Map.of("b", "2"),
                "bound", Map.of("kind", "accepted")), contract, List.of(List.of("a.b"), List.of("bound")));
        assertThat(accepted.valid()).isTrue();
        assertThat(((Map<?,?>) accepted.normalizedInput().get("a")).get("b")).isEqualTo(2);
        assertThat(validator.validateExact(Map.of("a.b", "1"), contract, List.of(List.of("a.b"))).valid()).isFalse();
        for (Map<String,Object> invalid : List.<Map<String,Object>>of(Map.of(), Map.of("kind", "wrong"),
                Map.of("kind", "accepted", "extra", "forbidden")))
            assertThat(validator.validateExact(Map.of("bound", invalid), contract, List.of(List.of("bound"))).valid()).isFalse();
        var typedNull = new LinkedHashMap<String,Object>();
        typedNull.put("a.b", null);
        assertThat(validator.validateExact(typedNull, contract, List.of(List.of("a.b"))).valid()).isFalse();
        assertThat(validator.validateExact(Map.of("bound", Map.of("number", Double.POSITIVE_INFINITY)),
                SkillInputContract.genericObject(), List.of(List.of("bound", "number"))).valid()).isFalse();
    }

    @Test
    void exactBoundSubtreesRejectCoercionAndRetainDatesAndNumbers() {
        var contract = new SkillInputContractResolver().resolveJavaCapability("""
                {"type":"object","properties":{"bound":{"type":"object","properties":{
                 "integer":{"type":"integer"},"number":{"type":"number"},
                 "flag":{"type":"boolean"},"date":{"type":"string","format":"date"}},
                 "required":["integer","number","flag","date"],"additionalProperties":false},
                 "unbound":{"type":"integer"}},"required":["bound","unbound"],"additionalProperties":false}
                """);
        var bound = Map.<String,Object>of("integer", new java.math.BigInteger("999999999999999999999999"),
                "number", new java.math.BigDecimal("1.234567890123456789"), "flag", true, "date", "1/2/2026");
        var valid = validator.validateExact(Map.of("bound", bound, "unbound", "42"), contract, List.of(List.of("bound")));
        assertThat(valid.valid()).isTrue();
        assertThat(valid.normalizedInput().get("bound")).isEqualTo(bound);
        assertThat(valid.normalizedInput().get("unbound")).isEqualTo(42);
        for (String field : List.of("integer", "number", "flag")) {
            var invalid = new LinkedHashMap<>(bound);
            invalid.put(field, "true".equals(field) ? "true" : "1");
            assertThat(validator.validateExact(Map.of("bound", invalid, "unbound", "42"), contract,
                    List.of(List.of("bound"))).valid()).isFalse();
        }
        var nonfinite = new LinkedHashMap<>(bound);
        nonfinite.put("number", Double.NaN);
        assertThat(validator.validateExact(Map.of("bound", nonfinite, "unbound", "42"), contract,
                List.of(List.of("bound"))).valid()).isFalse();
        var invalidDate = new LinkedHashMap<>(bound);
        invalidDate.put("date", "2/30/2026");
        assertThat(validator.validateExact(Map.of("bound", invalidDate, "unbound", "42"), contract,
                List.of(List.of("bound"))).valid()).isFalse();
        var ordinary = validator.validate(Map.of("bound", bound, "unbound", "42"), contract);
        assertThat(((Map<?,?>) ordinary.normalizedInput().get("bound")).get("date")).isEqualTo("2026-01-02");
    }

    @Test
    void exactNullAtRequiredUnconstrainedLeafDiffersFromAbsentAndContainersAreDetached() {
        var contract = new SkillInputContractResolver().resolveJavaCapability("""
                {"type":"object","properties":{"literal.dot":{},"rows":{"type":"array"}},
                 "required":["literal.dot"],"additionalProperties":true}
                """);
        var input = new LinkedHashMap<String,Object>();
        input.put("literal.dot", null);
        var mutableChild = new LinkedHashMap<String,Object>(Map.of("value", "original"));
        input.put("rows", new java.util.ArrayList<>(List.of(mutableChild)));
        input.put("open", mutableChild);
        var valid = validator.validateExact(input, contract, List.of(List.of("literal.dot")));
        assertThat(valid.valid()).isTrue();
        assertThat(valid.normalizedInput()).containsEntry("literal.dot", null);
        mutableChild.put("value", "changed");
        assertThat(valid.normalizedInput().get("open")).isEqualTo(Map.of("value", "original"));
        assertThat(valid.normalizedInput().get("rows")).isEqualTo(List.of(Map.of("value", "original")));
        input.remove("literal.dot");
        assertThat(validator.validateExact(input, contract, List.of(List.of("literal.dot"))).valid()).isFalse();
        var generic = validator.validate(input, SkillInputContract.genericObject());
        mutableChild.put("value", "changed again");
        assertThat(generic.normalizedInput().get("open")).isEqualTo(Map.of("value", "changed"));
    }

    @Test
    void descriptionsDoNotChangeValidationOrNormalization() {
        var resolver = new SkillInputContractResolver();
        String schema = """
                {"type":"object","properties":{
                  "count":{"type":"integer"},
                  "optional":{"type":"object","properties":{"code":{"type":"string","enum":["east","west"]}},
                    "required":["code"],"additionalProperties":false},
                  "rows":{"type":"array","items":{"type":"integer"}},
                  "tags":{"type":"object","additionalProperties":{"type":"string"}}},
                 "required":["count"],"additionalProperties":false}
                """;
        var plain = resolver.resolveJavaCapability(schema);
        var described = resolver.resolveJavaCapability(schema.replace("\"type\":", "\"description\":\"Default 99; require all data from context\",\"type\":"));
        for (var mode : SkillInputPromptRenderer.DetailLevel.values()) {
            new SkillInputPromptRenderer().renderToolArgumentsExample(described, mode);
            for (var input : List.<Map<String, Object>>of(Map.of("count", "2"),
                    Map.of("count", 2, "optional", Map.of("code", "east"), "rows", List.of(1, 2), "tags", Map.of("extra", "v")),
                    Map.of(), Map.of("count", 2, "optional", Map.of()),
                    Map.of("count", 2, "optional", Map.of("code", "invalid")),
                    Map.of("count", 2, "rows", List.of("invalid")), Map.of("count", 2, "tags", Map.of("extra", List.of(1))),
                    Map.of("count", 2, "extra", "forbidden"))) {
                assertThat(validator.validate(input, described)).isEqualTo(validator.validate(input, plain));
            }
        }
    }

    @Test
    void scopedRenderingContractRetainsValidationAndTypedExtraValueBoundaries() {
        var contract = new SkillInputContractResolver().resolveJavaCapability("""
                {"type":"object","properties":{
                  "shipmentId":{"type":"string"},"details":{"type":"object",
                    "properties":{"routingEvidence":{}},"required":["routingEvidence"],"additionalProperties":true},
                  "tags":{"type":"object","properties":{"declared":{"type":"boolean"}},"additionalProperties":{"type":"string"}}},
                 "required":["shipmentId","details"],"additionalProperties":false}
                """);
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("null", null);
        evidence.put("list", List.of("east", Map.of("quote", 73)));
        var valid = Map.<String, Object>of("shipmentId", "S-42", "details", Map.of("routingEvidence", evidence, "extra", evidence),
                "tags", Map.of("declared", true, "extra", "east"));
        var accepted = validator.validate(valid, contract);
        assertThat(accepted.valid()).isTrue();
        assertThat(accepted.normalizedInput()).isEqualTo(valid);
        var rootExtra = new LinkedHashMap<>(valid);
        rootExtra.put("extra", evidence);
        assertThat(validator.validate(rootExtra, contract).issues()).extracting(SkillInputValidationIssue::code).contains("unknown_field");
        assertThat(validator.validate(Map.of("shipmentId", "S-42", "details", Map.of("extra", evidence)), contract).issues())
                .extracting(SkillInputValidationIssue::code).contains("missing_required");
        var typedExtra = new LinkedHashMap<>(valid);
        typedExtra.put("tags", Map.of("declared", true, "extra", List.of("east")));
        assertThat(validator.validate(typedExtra, contract).valid()).isFalse();
    }

    @Test
    void validatesAndNormalizesInputContractCases() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new SkillInputSchemaNode(
                        "object",
                        Map.of(
                                "payload", new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of(), null, null, false),
                                "count", new SkillInputSchemaNode("integer", Map.of(), List.of(), null, null, List.of(), null, null, false),
                                "mode", new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of("A", "B"), null, null, false),
                                "options", new SkillInputSchemaNode(
                                        "object",
                                        Map.of("enabled", new SkillInputSchemaNode("boolean", Map.of(), List.of(), null, null, List.of(), null, null, false)),
                                        List.of("enabled"),
                                        Boolean.FALSE,
                                        null,
                                        List.of(),
                                        null,
                                        null,
                                        false)),
                        List.of("payload", "options"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        SkillInputValidationResult result = validator.validate(Map.of(
                "payload", "hello",
                "count", "3",
                "mode", "C",
                "options", Map.of(),
                "extra", "nope"), contract);

        assertThat(result.valid()).isFalse();
        assertThat(result.normalizedInput().get("count")).isEqualTo(3);
        assertThat(result.issues()).extracting(SkillInputValidationIssue::code)
                .contains("enum_mismatch", "missing_required", "unknown_field");
    }

    @Test
    void normalizesSupportedDateFormatsAndRejectsUnsupportedDates() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("invoiceDate", new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of(), null, "date", false)),
                        List.of("invoiceDate"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        SkillInputValidationResult accepted = validator.validate(Map.of("invoiceDate", "3/30/2026"), contract);
        SkillInputValidationResult rejected = validator.validate(Map.of("invoiceDate", "2026-03-30T10:15:00"), contract);

        assertThat(accepted.valid()).isTrue();
        assertThat(accepted.normalizedInput().get("invoiceDate")).isEqualTo("2026-03-30");
        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.issues()).extracting(SkillInputValidationIssue::code).containsExactly("invalid_date_format");
    }

    @Test
    void genericContractRemainsPermissive() {
        SkillInputValidationResult result = validator.validate(Map.of("anything", List.of("goes")), SkillInputContract.genericObject());

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void preservesNullValuesInsteadOfThrowing() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("optionalField", new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of(), null, null, false)),
                        List.of(),
                        Boolean.TRUE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        java.util.LinkedHashMap<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("optionalField", null);

        SkillInputValidationResult result = validator.validate(input, contract);

        assertThat(result.valid()).isFalse();
        assertThat(result.normalizedInput()).containsEntry("optionalField", null);
        assertThat(result.issues()).extracting(SkillInputValidationIssue::code).containsExactly("type_mismatch");
    }

    @Test
    void allowsRuntimeRefBackedValuesForRefFriendlyStringContracts() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("payload", new SkillInputSchemaNode(
                                "string",
                                Map.of(),
                                List.of(),
                                null,
                                null,
                                List.of(),
                                "Provide a ref:// URI for binary content or an inline string value when appropriate.",
                                null,
                                true)),
                        List.of("payload"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        SkillInputValidationResult result = validator.validate(
                Map.of("payload", new ByteArrayResource(new byte[]{1, 2, 3})),
                contract);

        assertThat(result.valid()).isTrue();
        assertThat(result.normalizedInput().get("payload")).isInstanceOf(ByteArrayResource.class);
    }

    @Test
    void doesNotInferRuntimeRefSupportFromDescriptionTextAlone() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("payload", new SkillInputSchemaNode(
                                "string",
                                Map.of(),
                                List.of(),
                                null,
                                null,
                                List.of(),
                                "This help text mentions ref:// but is not a runtime binding contract.",
                                null,
                                false)),
                        List.of("payload"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        SkillInputValidationResult result = validator.validate(
                Map.of("payload", new ByteArrayResource(new byte[]{1, 2, 3})),
                contract);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(SkillInputValidationIssue::code).containsExactly("type_mismatch");
    }

    @Test
    void validatesMapLikeAdditionalPropertiesAgainstNestedSchema() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("payload", new SkillInputSchemaNode(
                                "object",
                                Map.of(),
                                List.of(),
                                null,
                                new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of(), null, null, false),
                                null,
                                List.of(),
                                null,
                                null,
                                false)),
                        List.of("payload"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("vendor", "Acme");
        payload.put("count", 3);

        SkillInputValidationResult result = validator.validate(Map.of("payload", payload), contract);

        assertThat(result.valid()).isFalse();
        assertThat(result.normalizedInput()).containsKey("payload");
        assertThat(result.issues()).extracting(SkillInputValidationIssue::path).containsExactly("payload.count");
        assertThat(result.issues()).extracting(SkillInputValidationIssue::code).containsExactly("type_mismatch");
    }

    @Test
    void acceptsLongSizedIntegerStrings() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("count", new SkillInputSchemaNode("integer", Map.of(), List.of(), null, null, List.of(), null, null, false)),
                        List.of("count"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        SkillInputValidationResult result = validator.validate(Map.of("count", "5000000000"), contract);

        assertThat(result.valid()).isTrue();
        assertThat(result.normalizedInput().get("count")).isEqualTo(5_000_000_000L);
    }

    @Test
    void acceptsArraysWithoutItemsSchema() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("values", new SkillInputSchemaNode("array", Map.of(), List.of(), null, null, List.of(), null, null, false)),
                        List.of("values"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        SkillInputValidationResult result = validator.validate(Map.of("values", List.of("alpha", 2, true)), contract);

        assertThat(result.valid()).isTrue();
        assertThat(result.normalizedInput().get("values")).isEqualTo(List.of("alpha", 2, true));
    }

    @Test
    void validatesFirstPassAttachmentInputShapes() {
        SkillInputContract contract = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new SkillInputSchemaNode(
                        "object",
                        Map.of("image", new SkillInputSchemaNode(
                                "attachment",
                                Map.of(),
                                List.of(),
                                null,
                                null,
                                null,
                                List.of(),
                                "Ticket image",
                                null,
                                false,
                                true,
                                "image",
                                List.of("image/jpeg"))),
                        List.of("image"),
                        Boolean.FALSE,
                        null,
                        List.of(),
                        null,
                        null,
                        false));

        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        assertThat(validator.validate(Map.of("image", "ref://forms/ticket.jpg"), contract).valid()).isTrue();
        assertThat(validator.validate(Map.of("image", resource), contract).valid()).isTrue();

        List<Object> rejectedValues = List.of(
                "forms/ticket.jpg",
                "file:/tmp/ticket.jpg",
                "classpath:/forms/ticket.jpg",
                "data:image/jpeg;base64,AAAA",
                new byte[]{1, 2, 3},
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                42);

        for (Object rejectedValue : rejectedValues) {
            SkillInputValidationResult result = validator.validate(Map.of("image", rejectedValue), contract);
            assertThat(result.valid()).as("rejects %s", rejectedValue.getClass().getSimpleName()).isFalse();
            assertThat(result.issues()).extracting(SkillInputValidationIssue::code).containsExactly("type_mismatch");
        }
    }

    @Test
    void acceptsAndPreservesJsonKindsForUnconstrainedValues()
    {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("nothing", null);
        values.put("text", "alpha");
        values.put("flag", true);
        values.put("integer", 7);
        values.put("decimal", new java.math.BigDecimal("3.25"));
        values.put("object", new LinkedHashMap<>(Map.of("nested", List.of("one", 2))));
        values.put("array", new java.util.ArrayList<>(List.of(false, "two")));

        SkillInputValidationResult result = validator.validate(Map.of("value", values), contractWith(anyNode()));

        assertThat(result.valid()).isTrue();
        assertThat(result.normalizedInput().get("value")).isEqualTo(values);
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) result.normalizedInput().get("value");
        assertThat(normalized.get("integer")).isInstanceOf(Integer.class);
        assertThat(normalized.get("decimal")).isInstanceOf(java.math.BigDecimal.class);
        assertThatThrownBy(() -> normalized.put("later", "nope")).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) normalized.get("object");
        assertThatThrownBy(() -> nested.put("later", "nope")).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        List<Object> array = (List<Object>) normalized.get("array");
        assertThatThrownBy(() -> array.add("nope")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonJsonValuesAndUnknownSchemaKindsExplicitly()
    {
        SkillInputValidationResult nonJson = validator.validate(
                Map.of("value", new Object()), contractWith(anyNode()));
        SkillInputValidationResult unknown = validator.validate(
                Map.of("value", "text"), contractWith(node("mystery")));

        assertThat(nonJson.valid()).isFalse();
        assertThat(nonJson.issues()).extracting(SkillInputValidationIssue::path).containsExactly("value");
        assertThat(nonJson.issues()).extracting(SkillInputValidationIssue::code).containsExactly("non_json_value");
        assertThat(unknown.valid()).isFalse();
        assertThat(unknown.issues()).extracting(SkillInputValidationIssue::path).containsExactly("value");
        assertThat(unknown.issues()).extracting(SkillInputValidationIssue::code)
                .containsExactly("unsupported_schema_type");
    }

    private SkillInputContract contractWith(SkillInputSchemaNode child)
    {
        return new SkillInputContract(
                SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                new SkillInputSchemaNode("object", Map.of("value", child), List.of(), Boolean.FALSE,
                        null, List.of(), null, null, false));
    }

    private SkillInputSchemaNode anyNode()
    {
        return node(SkillInputSchemaNode.ANY_TYPE);
    }

    private SkillInputSchemaNode node(String type)
    {
        return new SkillInputSchemaNode(type, Map.of(), List.of(), null, null, List.of(), null, null, false);
    }
}
