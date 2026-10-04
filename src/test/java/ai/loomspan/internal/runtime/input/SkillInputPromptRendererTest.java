package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SkillInputPromptRendererTest
{
    private final SkillInputPromptRenderer renderer = new SkillInputPromptRenderer();
    private final SkillInputContractResolver resolver = new SkillInputContractResolver();

    @Test
    void scopesClosedRootAndOpenChildInBothModes()
    {
        var contract = resolver.resolveJavaCapability("""
                {"type":"object","properties":{
                  "shipmentId":{"type":"string"},
                  "details":{"type":"object","properties":{
                    "routingEvidence":{},"note":{"type":"string"}},
                    "required":["routingEvidence"],"additionalProperties":true}},
                 "required":["shipmentId","details"],"additionalProperties":false}
                """);
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            assertThat(renderer.renderToolArgumentsExample(contract, mode))
                    .contains("Illustrative declared structure", "At `$` (top level)",
                            "Required fields: [details, shipmentId]", "Only these fields are allowed: [details, shipmentId]",
                            "At `$.details`", "Required fields: [routingEvidence]", "Optional declared fields: [note]",
                            "Additional fields are allowed with any JSON value", "not exhaustive")
                    .doesNotContain("Do not add fields not shown above.");
        }
    }

    @Test
    void traversesConsecutiveArraysAndTypedAdditionalValuesWithoutDepthLoss()
    {
        var contract = resolver.resolveJavaCapability("""
                {"type":"object","properties":{
                  "catalog":{"type":"array","items":{"type":"array","items":{
                    "type":"object","additionalProperties":{"type":"array","items":{
                      "type":"object","properties":{
                        "entry":{"type":"object","properties":{
                          "facts":{"type":"object"},"code":{"type":"string"}},
                          "required":["code"],"additionalProperties":false}},
                      "additionalProperties":false}}}}},
                  "literal.dot":{"type":"object","additionalProperties":false},
                  "literal":{"type":"object","properties":{"dot":{"type":"object"}}},
                  "tags":{"type":"object","properties":{"declared":{"type":"boolean"}},
                    "additionalProperties":{"type":"string","enum":["east","west"]}},
                  "anything":{},"untyped":{"type":"array"}},"additionalProperties":true}
                """);
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            assertThat(renderer.renderToolArgumentsExample(contract, mode))
                    .contains("At `$.catalog[][]`", "each unlisted field value must be a array",
                            "At `$.catalog[][].*[]`", "Only these fields are allowed: [entry]",
                            "At `$.catalog[][].*[].entry`", "Required fields: [code]",
                            "At `$.catalog[][].*[].entry.facts`", "Additional fields are allowed with any JSON value",
                            "At `$[\"literal.dot\"]`", "At `$.literal.dot`", "Only these fields are allowed: []",
                            "each unlisted field value must be a string with one of [east, west]",
                            "These constraints apply only to unlisted fields", "<any JSON value>")
                    .doesNotContain("takes no arguments");
        }
    }

    @Test
    void optionalParentAndEmptyOpenObjectRemainOptionalAndNonemptyPermitted()
    {
        var contract = resolver.resolveJavaCapability("""
                {"type":"object","properties":{
                  "details":{"type":"object","properties":{"code":{"type":"string"}},"required":["code"]},
                  "extra":{"type":"object"}},"additionalProperties":false}
                """);
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            assertThat(renderer.renderToolArgumentsExample(contract, mode))
                    .contains("At `$` (top level): Required fields: []. Optional declared fields: [details, extra]",
                            "At `$.details`: Required fields: [code]", "Required child fields do not require an optional parent",
                            "At `$.extra`: Required fields: []", "an empty illustration need not remain empty");
        }
    }

    @Test
    void distinguishesGenericStrictEmptyAndReflectedOpenEmptyContracts()
    {
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            assertThat(renderer.renderToolArgumentsExample(SkillInputContract.genericObject(), mode)).isEmpty();
            assertThat(renderer.renderToolArgumentsExample(resolver.resolveJavaCapability(
                    "{\"type\":\"object\",\"additionalProperties\":false}"), mode)).contains("must pass an empty object");
            assertThat(renderer.renderToolArgumentsExample(resolver.resolveJavaCapability(
                    "{\"type\":\"object\"}"), mode)).contains("Additional fields are allowed").doesNotContain("takes no arguments");
        }
    }
}
