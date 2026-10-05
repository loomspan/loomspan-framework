package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ChildInputBindingProjectionTest {
    private final SkillInputContractResolver resolver = new SkillInputContractResolver();
    private static ChildInputBinding input(String target) {
        return new ChildInputBinding(ObjectFieldPath.parse(target, false), ChildInputBinding.SourceKind.INPUT, ObjectFieldPath.parse("", true), null);
    }
    @Test void preservesUnboundRequiredSiblingUnderOriginallyOptionalAncestor() {
        var original = resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"context":{"type":"object","properties":{"evidence":{"type":"object"},"reason":{"type":"string","enum":["yes"],"description":"why"}},"required":["evidence","reason"],"additionalProperties":false}},"additionalProperties":false}
                """);
        var projection = new ChildInputBindingProjection(original, List.of(input("/context/evidence")));
        assertThat(projection.argumentContract().schema().required()).containsExactly("context");
        var context = projection.argumentContract().schema().properties().get("context");
        assertThat(context.required()).containsExactly("reason");
        assertThat(context.properties().get("reason").enumValues()).containsExactly("yes");
        assertThat(projection.validateModelArguments(Map.of("context", Map.of("reason", "yes")))).isEmpty();
        assertThat(original.schema().properties().get("context").required()).containsExactly("evidence", "reason");
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
        assertThat(projection.argumentContract().schema().additionalPropertiesSchema()).isEqualTo(original.schema().additionalPropertiesSchema());
        assertThat(projection.argumentContract().schema().properties().get("rows")).isEqualTo(original.schema().properties().get("rows"));
        assertThat(projection.inputSchema()).contains("\"payload\":false", "\"format\":\"date\"", "authored date", "extension");
        assertThat(projection.validateModelArguments(Map.of("unbound", "extension"))).isEmpty();
        var nullOverride = new java.util.HashMap<String, Object>(); nullOverride.put("payload", null);
        assertThat(projection.validateModelArguments(nullOverride)).hasSize(1);
        assertThat(projection.validateModelArguments(Map.of("payload", Map.of("underneath", "copy")))).hasSize(1);
    }
}
