package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.skill.AllowedSkillConstraint;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ChildInputBindingDeclarationsTest {
    private static SkillInputContract contract(String schema) {
        return new SkillInputContract(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new StandardSchemaCompiler().compile(schema, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED));
    }
    private static ChildInputBinding binding(String destination, String source) {
        return new ChildInputBinding(ObjectFieldPath.parse(destination, false), ChildInputBinding.SourceKind.INPUT,
                ObjectFieldPath.parse(source, true), null);
    }
    private static List<String> validate(SkillInputContract source, SkillInputContract receiver, ChildInputBinding binding) {
        var child = new AllowedSkillConstraint("default-consumer", null, null, false, List.of(binding));
        return ChildInputBindingDeclarations.validate(source, List.of(child), Map.of(child.name(), receiver));
    }
    @Test void numericEscapedAndCaseDistinctPropertyNamesUseExactObjectLookup() {
        var schema = contract("""
                {"type":"object","properties":{"0":{"type":"string"},"a/b":{"type":"string"},"~":{"type":"string"},"Value":{"type":"string"},"value":{"type":"integer"}},"additionalProperties":false}
                """);
        for (String path : List.of("/0", "/a~1b", "/~0", "/Value", "/value"))
            assertThat(validate(schema, schema, binding(path, path))).as(path).isEmpty();
        assertThat(validate(schema, schema, binding("/VALUE", "/value"))).singleElement().asString().contains("closed object");
        assertThat(validate(schema, schema, binding("/value", "/VALUE"))).singleElement().asString().contains("parent source", "closed object");
    }
    @Test void arrayValuesTransferWholeButNumericTokensCannotIndexThem() {
        var schema = contract("""
                {"type":"object","properties":{"rows":{"type":"array","items":{"type":"object","properties":{"0":{"type":"string"}},"additionalProperties":false}}},"additionalProperties":false}
                """);
        assertThat(validate(schema, schema, binding("/rows", "/rows"))).isEmpty();
        assertThat(validate(schema, schema, binding("/rows/0", "/rows"))).singleElement().asString().contains("non-object");
        assertThat(validate(schema, schema, binding("/rows", "/rows/0"))).singleElement().asString().contains("parent source", "non-object");
    }
    @Test void omittedAndTrueOpennessAgreeForSourceAndDestinationAtEveryDepth() {
        var generic = SkillInputContract.genericObject();
        for (boolean nested : List.of(false, true)) for (String openness : List.of("omitted", "true", "false")) {
            String additional = openness.equals("omitted") ? "" : ",\"additionalProperties\":" + openness;
            var schema = contract(nested
                    ? "{\"type\":\"object\",\"properties\":{\"context\":{\"type\":\"object\"" + additional + "}},\"additionalProperties\":false}"
                    : "{\"type\":\"object\"" + additional + "}");
            String path = nested ? "/context/unknown/deeper" : "/unknown/deeper";
            var destinationIssues = validate(generic, schema, binding(path, ""));
            var sourceIssues = validate(schema, generic, binding("/received", path));
            if (openness.equals("false")) {
                assertThat(destinationIssues).singleElement().asString().contains("closed object");
                assertThat(sourceIssues).singleElement().asString().contains("parent source", "closed object");
            } else {
                assertThat(destinationIssues).as(openness + " destination " + path).isEmpty();
                assertThat(sourceIssues).as(openness + " source " + path).isEmpty();
            }
        }
    }
    @Test void nullableObjectAncestorsRemainTraversableAndFalsePropertiesReject() {
        var nullable = contract("""
                {"type":"object","properties":{"context":{"type":["null","object"],"properties":{"value":{"type":"string"},"reserved":false},"additionalProperties":false}},"additionalProperties":false}
                """);
        assertThat(validate(nullable, nullable, binding("/context/value", "/context/value"))).isEmpty();
        assertThat(validate(nullable, nullable, binding("/context/reserved", "/context/value")))
                .singleElement().asString().contains("prohibited field");
        assertThat(validate(nullable, nullable, binding("/context/value", "/context/reserved")))
                .singleElement().asString().contains("parent source", "prohibited field");
    }
    @Test void duplicateAndOverlappingDestinationsRemainRejected() {
        var child = new AllowedSkillConstraint("default-consumer", null, null, false,
                List.of(binding("/context", ""), binding("/context/value", ""), binding("/context", "")));
        assertThat(ChildInputBindingDeclarations.validate(SkillInputContract.genericObject(), List.of(child), Map.of()))
                .hasSize(3).allMatch(issue -> issue.contains("duplicate or overlapping destination"));
    }
}
