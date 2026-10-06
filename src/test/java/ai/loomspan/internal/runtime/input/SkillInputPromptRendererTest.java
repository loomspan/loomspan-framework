package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SkillInputPromptRendererTest
{
    private final SkillInputPromptRenderer renderer = new SkillInputPromptRenderer();
    private final SkillInputContractResolver resolver = new SkillInputContractResolver();

    @Test
    void preservesDescriptionsAtExactPathsInBothModes()
    {
        String multiline = "  Units: ms; `literal.*[]` and \"quotes\".\nKeep this line.\r\nBackslash: \\  ";
        var properties = new java.util.LinkedHashMap<String, Object>();
        properties.put("optional", java.util.Map.of("type", "object", "description", "Optional container",
                "properties", java.util.Map.of("value", java.util.Map.of("type", "string", "description", multiline)),
                "required", java.util.List.of("value"), "additionalProperties", false));
        properties.put("rows", java.util.Map.of("type", "array", "description", "Rows", "items",
                java.util.Map.of("type", "array", "items", java.util.Map.of("type", "object", "description", "Each record",
                        "additionalProperties", java.util.Map.of("type", "string", "description", "Additional value")))));
        properties.put("literal.dot", java.util.Map.of("type", "string", "description", "Literal key"));
        properties.put("literal", java.util.Map.of("type", "object", "properties",
                java.util.Map.of("dot", java.util.Map.of("type", "string", "description", "Nested key"))));
        properties.put("tick`\"\\\n[]*", java.util.Map.of("description", "Unconstrained value"));
        properties.put("absent", java.util.Map.of("type", "string"));
        properties.put("blank", java.util.Map.of("type", "string", "description", " \n\t"));
        var json = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().planningJson();
        var contract = resolver.resolveJavaCapability(json.writeValueAsString(java.util.Map.of(
                "type", "object", "description", "Root meaning", "properties", properties, "additionalProperties", false)));
        var expected = java.util.Map.of("$", "Root meaning", "$.optional", "Optional container", "$.optional.value", multiline,
                "$.rows", "Rows", "$.rows[][]", "Each record", "$.rows[][].*", "Additional value",
                "$[\"literal.dot\"]", "Literal key", "$.literal.dot", "Nested key",
                "$[" + json.writeValueAsString("tick`\"\\\n[]*") + "]", "Unconstrained value");
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            String rendered = renderer.renderToolArgumentsExample(contract, mode);
            var actual = new java.util.LinkedHashMap<String, String>();
            rendered.lines().filter(line -> line.startsWith("Description at ")).forEach(line -> {
                var pair = json.readTree("[" + line.substring("Description at ".length()).replaceFirst(": ", ",") + "]");
                assertThat(actual.put(pair.get(0).asText(), pair.get(1).asText())).isNull();
            });
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
            assertThat(rendered).contains("Optional declared fields:", "Required child fields do not require an optional parent",
                    "Descriptions explain meaning; they do not add validation rules, defaults, data bindings",
                    "Only these fields are allowed: [value]");
        }
    }

    @Test
    void rendersYamlDescriptionsThroughSharedResolvedNodes()
    {
        var root = new ai.loomspan.internal.skill.YamlSkillManifest.InputSchemaManifest();
        root.setType("object");
        root.setDescription("YAML root");
        var child = new ai.loomspan.internal.skill.YamlSkillManifest.InputSchemaManifest();
        child.setType("string");
        child.setDescription("YAML field");
        root.setProperties(java.util.Map.of("field", child));
        var contract = new SkillInputContract(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT, resolver.fromManifest(root));
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            assertThat(renderer.renderToolArgumentsExample(contract, mode))
                    .contains("Description at \"$\": \"YAML root\"", "Description at \"$.field\": \"YAML field\"");
        }
    }

    @Test
    void leavesUndescribedAndGenericGuidanceUnchangedAndDescribesNoArgumentRoot()
    {
        for (var mode : SkillInputPromptRenderer.DetailLevel.values())
        {
            assertThat(renderer.renderToolArgumentsExample(resolver.resolveJavaCapability(
                    "{\"type\":\"object\",\"additionalProperties\":false}"), mode))
                    .isEqualTo("{}\n(This closed argument object has no declared fields. You must pass an empty object.)");
            assertThat(renderer.renderToolArgumentsExample(resolver.resolveJavaCapability(
                    "{\"type\":\"object\",\"description\":\"Nothing to supply\",\"additionalProperties\":false}"), mode))
                    .contains("must pass an empty object", "Description at \"$\": \"Nothing to supply\"");
            assertThat(renderer.renderToolArgumentsExample(resolver.resolveFromToolSchema(
                    "{\"type\":\"object\",\"description\":\"Generic\"}"), mode)).isEmpty();
            assertThat(renderer.renderToolArgumentsExample(resolver.resolveJavaCapability(
                    "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}}}"), mode))
                    .doesNotContain("Authored descriptions", "Description at");
        }
    }

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
