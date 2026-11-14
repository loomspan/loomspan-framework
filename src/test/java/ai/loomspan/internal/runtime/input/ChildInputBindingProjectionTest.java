package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ChildInputBindingProjectionTest {
    @Test void directDispatchRequiresEntireClosedEffectiveContract() {
        var schema = resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"value":{"type":"string"},"optional":{"type":"string"}},"required":["value"],"additionalProperties":false}
                """);
        assertThat(new ChildInputBindingProjection(schema, List.of(input("/value"))).dispatchEligibility().reason()).isEqualTo("unbound_input_remains");
        var complete = new ChildInputBindingProjection(schema, List.of(input("/value"), input("/optional")));
        assertThat(complete.dispatchEligibility().eligible()).isTrue();
        assertThat(complete.argumentContract().schema().tree().path("properties").path("value").booleanValue()).isFalse();
        assertThat(new SkillInputValidator().validate(Map.of(), complete.argumentContract()).valid()).isTrue();
        assertThat(new ChildInputBindingProjection(resolver.resolveFromToolSchema("{\"type\":\"object\",\"additionalProperties\":false}"), List.of()).dispatchEligibility().eligible()).isTrue();
        for (String unknown : List.of("", "{\"type\":\"object\"}", "{\"type\":\"object\",\"additionalProperties\":true}",
                "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"))
            assertThat(new ChildInputBindingProjection(resolver.resolveFromToolSchema(unknown), List.of()).dispatchEligibility().eligible()).as(unknown).isFalse();
    }
    @Test void unsupportedRawKeywordsCannotDisappearIntoEligibleProof() {
        for (String keyword : List.of("\"$ref\":\"#/defs/x\"", "\"allOf\":[]", "\"anyOf\":[]", "\"oneOf\":[]", "\"default\":{}",
                "\"const\":{}", "\"if\":{}", "\"minProperties\":0", "\"unknown\":true", "\"required\":\"bad\"", "\"items\":false")) {
            assertThatThrownBy(() -> resolver.resolveFromToolSchema("{\"type\":\"object\",\"additionalProperties\":false," + keyword + "}"))
                    .as(keyword).isInstanceOf(IllegalArgumentException.class);
        }
        var nullable = resolver.resolveFromToolSchema("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":[\"string\",\"null\"]}},\"additionalProperties\":false}");
        assertThat(new ChildInputBindingProjection(nullable, List.of(input("/value"))).dispatchEligibility().eligible()).isTrue();
    }
    @Test void wholeBoundSubtreesQualifyButNestedPresenceChoicesDoNot() {
        for (String required : List.of("", ",\"required\":[\"context\"]")) {
            var contract = resolver.resolveFromToolSchema("{\"type\":\"object\",\"properties\":{\"context\":{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"additionalProperties\":false}},\"additionalProperties\":false" + required + "}");
            assertThat(new ChildInputBindingProjection(contract, List.of(input("/context/value"))).dispatchEligibility().reason()).isEqualTo("unsupported_or_ambiguous_shape");
            assertThat(new ChildInputBindingProjection(contract, List.of(input("/context"))).dispatchEligibility().eligible()).isTrue();
        }
        var array = resolver.resolveFromToolSchema("{\"type\":\"object\",\"properties\":{\"rows\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}},\"additionalProperties\":false}");
        assertThat(new ChildInputBindingProjection(array, List.of(input("/rows"))).dispatchEligibility().eligible()).isTrue();
    }
    private final SkillInputContractResolver resolver = new SkillInputContractResolver();
    private static ChildInputBinding input(String target) {
        return new ChildInputBinding(ObjectFieldPath.parse(target, false), ChildInputBinding.SourceKind.INPUT, ObjectFieldPath.parse("", true), null);
    }
    @Test void preservesUnboundRequiredSiblingUnderOriginallyOptionalAncestor() {
        var original = resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"context":{"type":"object","properties":{"evidence":{"type":"object"},"reason":{"type":"string","enum":["yes"],"description":"why"}},"required":["evidence","reason"],"additionalProperties":false}},"additionalProperties":false}
                """);
        var projection = new ChildInputBindingProjection(original, List.of(input("/context/evidence")));
        assertThat(projection.argumentContract().schema().tree().path("required").valueStream().map(v -> v.asText()).toList()).containsExactly("context");
        var context = projection.argumentContract().schema().tree().path("properties").path("context");
        assertThat(context.path("required").valueStream().map(v -> v.asText()).toList()).containsExactly("reason");
        assertThat(context.path("properties").path("reason").path("enum").valueStream().map(v -> v.asText()).toList()).containsExactly("yes");
        assertThat(projection.validateModelArguments(Map.of("context", Map.of("reason", "yes")))).isEmpty();
        assertThat(original.schema().tree().path("properties").path("context").path("required").valueStream().map(v -> v.asText()).toList()).containsExactly("evidence", "reason");
    }
    @Test void allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden() {
        var original = resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"context":{"type":"object","properties":{"evidence":{"type":"string"}},"required":["evidence"]}},"required":["context"]}
                """);
        var projection = new ChildInputBindingProjection(original, List.of(input("/context/evidence")));
        assertThat(projection.argumentContract().allowsEmptyInput()).isTrue();
        assertThat(projection.validateModelArguments(Map.of())).isEmpty();
        var generic = new ChildInputBindingProjection(SkillInputContract.genericObject(), List.of(input("/context/evidence")));
        assertThat(generic.inputSchema()).contains("\"evidence\":false");
        assertThat(generic.validateModelArguments(Map.of("context", Map.of("evidence", "same")))).hasSize(1);
        assertThat(generic.validateModelArguments(Map.of("context", "bad"))).hasSize(1);
        assertThat(generic.validateModelArguments(Map.of("context", Map.of("reason", "new")))).isEmpty();
    }
    @Test void preservesTypedExtensionsArrayItemsAndFormattingWhileExcludingReservedNames() {
        var original = resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"payload":{"type":"string"},"rows":{"type":"array","items":{"type":"object","properties":{"date":{"type":"string","format":"date","description":"authored date"}},"required":["date"],"additionalProperties":false}}},"required":["payload"],"additionalProperties":{"type":"string","enum":["extension"]}}
                """);
        var projection = new ChildInputBindingProjection(original, List.of(input("/payload")));
        assertThat(projection.argumentContract().schema().tree().path("additionalProperties")).isEqualTo(original.schema().tree().path("additionalProperties"));
        assertThat(projection.argumentContract().schema().tree().path("properties").path("rows")).isEqualTo(original.schema().tree().path("properties").path("rows"));
        assertThat(projection.inputSchema()).contains("\"payload\":false", "\"format\":\"date\"", "authored date", "extension");
        assertThat(projection.validateModelArguments(Map.of("unbound", "extension"))).isEmpty();
        var nullOverride = new java.util.HashMap<String, Object>(); nullOverride.put("payload", null);
        assertThat(projection.validateModelArguments(nullOverride)).hasSize(1);
        assertThat(projection.validateModelArguments(Map.of("payload", Map.of("underneath", "copy")))).hasSize(1);
    }
    @Test void canonicalReservationsBoundsNullableAncestorsAndCopiesAreEnforced() {
        var original=resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"context":{"type":["object","null"],"properties":{"value":{"type":"string"},"email":{"type":"string","format":"email","minLength":5,"maxLength":40},"rows":{"type":"array","items":{"type":"integer","minimum":1,"maximum":9},"minItems":1,"maxItems":2}},"required":["value","email","rows"],"additionalProperties":false}},"additionalProperties":false}
                """);
        var before=original.schema().tree();
        var projection=new ChildInputBindingProjection(original,List.of(input("/context/value")));
        var validator=new SkillInputValidator();
        var valid=Map.<String,Object>of("context",Map.of("email","a@example.com","rows",List.of(1,9)));
        assertThat(validator.validate(valid,projection.argumentContract()).valid()).isTrue();
        for(var context:List.of(Map.of("email","invalid","rows",List.of(1)),Map.of("email","a@example.com","rows",List.of(0)),Map.of("email","a@example.com","rows",List.of()),Map.of("email","a@example.com","rows",List.of(1,2,3)),Map.of("value","equal","email","a@example.com","rows",List.of(1))))
            assertThat(validator.validate(Map.of("context",context),projection.argumentContract()).valid()).isFalse();
        var nil=new java.util.LinkedHashMap<String,Object>();nil.put("value",null);nil.put("email","a@example.com");nil.put("rows",List.of(1));
        assertThat(validator.validate(Map.of("context",nil),projection.argumentContract()).valid()).isFalse();
        var copy=projection.argumentContract().schema().tree();((tools.jackson.databind.node.ObjectNode)copy).remove("properties");
        assertThat(projection.argumentContract().schema().tree().path("properties").has("context")).isTrue();
        assertThat(original.schema().tree()).isEqualTo(before);
        assertThat(validator.validateExact(Map.of("context",Map.of("value","exact","email","invalid","rows",List.of(1))),original,List.of(List.of("context","value"))).valid()).isFalse();
    }

}
