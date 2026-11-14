package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputSchemaPromptAugmentorTest
{
    @Test
    void augmentsPromptWithCompleteRecursiveEffectiveContractInDeclaredOrder()
    {
        var root = compile("""
                {"type":"object","additionalProperties":false,"properties":{
                  "transport":{"type":"object","additionalProperties":true,"properties":{
                    "outbound":{"type":["object","null"],"additionalProperties":true,"description":"Selected outbound option from the digest."},
                    "mode":{"type":"string","enum":["train, express","flight\\\"overnight"]}},"required":["outbound"]},
                  "questions":{"type":"array","items":{"type":"string"},"description":"Questions still needing answers.","x-loomspan-evidence":"SENTINEL_EVIDENCE"},
                  "rows":{"type":"array","items":{"type":"object","additionalProperties":false,"properties":{"date":{"type":"string","format":"date"}},"required":["date"]}},
                  "empty.object[]":{"type":"object","additionalProperties":false}},"required":["transport","rows"]}
                """);

        Prompt augmented = new OutputSchemaPromptAugmentor().augment(
                new Prompt(List.of(new SystemMessage("ORIGINAL SYSTEM"))), root);

        assertThat(augmented.getSystemMessage().getText()).isEqualTo("""
                ORIGINAL SYSTEM

                Return JSON only.
                Do not include markdown fences, commentary, or prose.
                Use the configured field names exactly.

                Property semantics:
                - required: the property must be present
                - optional: omit the property when its value is unknown
                - nullable: JSON null is allowed
                - non-null: JSON null is not allowed when the property is present

                Output contract:
                $ — object, non-null, additionalProperties=false
                  $.transport — object, required, non-null, additionalProperties=true
                    $.transport.outbound — object, required, nullable, additionalProperties=true - Selected outbound option from the digest.
                    $.transport.mode — string, optional, non-null, enum=["train, express","flight\\\"overnight"]
                  $.questions — array, optional, non-null - Questions still needing answers.
                    $.questions[] — string, non-null
                  $.rows — array, required, non-null
                    $.rows[] — object, non-null, additionalProperties=false
                      $.rows[].date — string, required, non-null, format=date
                  $["empty.object[]"] — object, optional, non-null, additionalProperties=false""");
        assertThat(augmented.getSystemMessage().getText())
                .doesNotContain("SENTINEL_EVIDENCE")
                .doesNotContain("output_schema_max_retries")
                .doesNotContain("mapping")
                .doesNotContain("response format");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"additionalProperties\":true", ",\"additionalProperties\":false"})
    void rendersRootAndNestedStandardOpenness(String openness) {
        var contract = compile("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"object\"" + openness + "}}" + openness + "}");
        String expected = openness.endsWith("false") ? "false" : "true";
        var prompt = new OutputSchemaPromptAugmentor().augment(new Prompt("Task"), contract);
        assertThat(prompt.getSystemMessage().getText())
                .contains("$ — object, non-null, additionalProperties=" + expected)
                .contains("$.nested — object, optional, non-null, additionalProperties=" + expected)
                .doesNotContain("Omit unknown fields unless");
    }

    @Test
    void rendersScalarEnumValuesAndEveryBoundWithoutChangingCanonicalTree() {
        var contract = compile("""
                {"type":"object","properties":{
                 "number":{"type":"number","enum":[1,2.5],"minimum":1,"maximum":3},
                 "text":{"type":["string","null"],"minLength":2,"maxLength":4,"format":"uuid","description":"Original description"},
                 "items":{"type":"array","items":{"type":"boolean","enum":[true,false]},"minItems":1,"maxItems":3}}}
                """);
        String before = contract.tree().toString();
        String rendered = new OutputSchemaPromptAugmentor().renderContract(contract);
        assertThat(rendered).contains("enum=[1,2.5]", "minimum=1", "maximum=3", "minLength=2", "maxLength=4", "format=uuid", "minItems=1", "maxItems=3", "enum=[true,false]", "Original description");
        assertThat(contract.tree().toString()).isEqualTo(before);
    }

    private static CanonicalSchema compile(String json) {
        return new StandardSchemaCompiler().compile(json, StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.AUTHORED);
    }
}
