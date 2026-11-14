package ai.loomspan.internal.outputschema;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.schema.*;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class OutputBindingCompositionTest {
    @Test void malformedAndBlankContributionsRetainInvalidJsonFailureClassification() {
        var composition=compose(object(Map.of("value",schema("string")),"value"),
                List.of(input("/value","/source")),Map.of("source","exact"));
        for (String candidate : new String[]{null, "", " ", "{broken"}) {
            var result=composition.compose(candidate);
            assertThat(result.validation().valid()).isFalse();
            assertThat(result.validation().failureMode()).as("candidate %s",candidate)
                    .isEqualTo(OutputSchemaFailureMode.INVALID_JSON);
            assertThat(result.validation().issues()).allMatch(issue->issue.code().equals("invalid_json"));
        }
        assertThat(composition.compose("[]").validation().failureMode())
                .isEqualTo(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED);
    }
    @Test void distinctUnicodeOutputPropertiesRetainNativeOutputMatching() {
        var fields=new LinkedHashMap<String,ObjectNode>();
        fields.put("i",schema("string"));fields.put("\u0131",schema("string"));
        var original=object(fields,"i","\u0131");
        var composition=compose(original,List.of(input("/i","/source")),Map.of("source","exact"));
        assertThat(composition.modelContributionRequired()).isTrue();
        var result=composition.compose("{\"\u0131\":\"reasoned\"}");
        assertThat(result.validation().valid()).isTrue();
        assertThat(result.content()).contains("\"i\":\"exact\"","\"\u0131\":\"reasoned\"");
    }
    private ObjectNode schema(String type) { return LoomspanJacksonCodecs.defaults().schemaTree().createObjectNode().put("type",type).put("additionalProperties",false); }
    private ObjectNode object(Map<String,ObjectNode> properties,String...required) {
        var s=schema("object"); var p=s.putObject("properties");properties.forEach(p::set);var r=s.putArray("required");for(var name:required)r.add(name);return s;
    }
    private ChildInputBinding input(String destination,String source) { return new ChildInputBinding(ObjectFieldPath.parse(destination,false),ChildInputBinding.SourceKind.INPUT,ObjectFieldPath.parse(source,true),null); }
    private OutputBindingComposition compose(ObjectNode s,List<ChildInputBinding>b,Map<String,Object>input) { return new OutputBindingComposition(compile(s),b,input,List.of(),"owner",LoomspanJacksonCodecs.defaults().schemaTree()); }
    private CanonicalSchema compile(ObjectNode s) { return new StandardSchemaCompiler().compile(s,StandardSchemaCompiler.Role.OUTPUT,StandardSchemaCompiler.Origin.AUTHORED); }
    @Test void preservesOptionalAncestorRequiredSiblingAndNullableUnboundFields() {
        var nullable=schema("string");nullable.putArray("type").add("string").add("null");
        var original=object(Map.of("context",object(Map.of("evidence",schema("string"),"reasoning",nullable),"evidence","reasoning")));
        var composition=compose(original,List.of(input("/context/evidence","/source")),Map.of("source","exact"));
        assertThat(composition.modelContributionRequired()).isTrue();
        assertThat(composition.projection().schema().tree().path("required").valueStream().map(v->v.asText()).toList()).containsExactly("context");
        assertThat(composition.compose("{}").validation().valid()).isFalse();
        assertThat(composition.compose("{\"context\":{\"reasoning\":null}}").validation().valid()).isTrue();
        assertThat(original.path("required")).isEmpty();
    }
    @Test void fullBindingAndOpenOrOptionalResidualSpace() {
        var binding=input("/value","/source");var closed=object(Map.of("value",schema("string")),"value");
        assertThat(compose(closed,List.of(binding),Map.of("source","x")).modelContributionRequired()).isFalse();
        closed.put("additionalProperties",true);
        assertThat(compose(closed,List.of(binding),Map.of("source","x")).modelContributionRequired()).isTrue();
        var open=schema("object");open.put("additionalProperties",true);var root=object(Map.of("value",open),"value");
        assertThat(compose(root,List.of(binding),Map.of("source",Map.of("nested",1))).modelContributionRequired()).isFalse();
        root.putObject("properties").set("value",open);root.withObject("properties").set("optional",object(Map.of()));
        assertThat(compose(root,List.of(binding),Map.of("source",Map.of())).modelContributionRequired()).isTrue();
    }
    @Test void overridesIncludeEqualNullAndBlockingAncestorsButNamesAreExact() {
        var composition=compose(object(Map.of("context",object(Map.of("evidence",schema("string"),"reasoning",schema("string")),"evidence","reasoning"))),List.of(input("/context/evidence","/source")),Map.of("source","exact"));
        for(String candidate:List.of("{\"context\":{\"evidence\":\"exact\"}}","{\"context\":{\"evidence\":null}}","{\"context\":null}","{\"context\":[]}"))
            assertThat(composition.compose(candidate).validation().issues()).anyMatch(issue->issue.code().equals("binding_model_override"));
        assertThat(composition.compose("{\"CONTEXT\":{\"reasoning\":\"ok\"}}").validation().valid()).isFalse();
        var open=object(Map.of("value",schema("string")));open.remove("additionalProperties");
        var exact=compose(open,List.of(input("/value","/source")),Map.of("source","exact"));
        assertThat(exact.compose("{\"VALUE\":null}").validation().valid()).isTrue();
        assertThat(exact.compose("{\"VALUE\":null}").content()).contains("\"value\":\"exact\"","\"VALUE\":null");
    }
    @Test void missingAndInvalidSourcesFailBeforeModelButNullableNullSucceeds() {
        var nullable=schema("string");nullable.putArray("type").add("string").add("null");var root=object(Map.of("value",nullable));
        assertThatThrownBy(()->compose(root,List.of(input("/value","/missing")),Map.of())).hasMessageContaining("binding_source_unavailable");
        assertThatThrownBy(()->compose(root,List.of(input("/value","/source")),Map.of("source",42))).hasMessageContaining("binding_output_validation");
        var input=new LinkedHashMap<String,Object>();input.put("source",null);
        assertThat(compose(root,List.of(input("/value","/source")),input).composeEmpty().content()).isEqualTo("{\"value\":null}");
    }
    @Test void exactChildValuesPlainTextArraysAndProvenanceRemainDetached() {
        var array=schema("array");array.set("items",schema("integer"));
        var root=object(Map.of("numbers",array,"text",schema("string")),"numbers","text");
        var bindings=List.of(new ChildInputBinding(ObjectFieldPath.parse("/numbers",false),ChildInputBinding.SourceKind.CHILD_RESULT,ObjectFieldPath.parse("/values",true),"default-one"),new ChildInputBinding(ObjectFieldPath.parse("/text",false),ChildInputBinding.SourceKind.CHILD_RESULT,ObjectFieldPath.parse("",true),"default-two"));
        var accepted=List.of(new ai.loomspan.internal.core.MissionContext.CompletedTaskResult("task1","default-one","{\"values\":[900719925474099312345,2]}"),new ai.loomspan.internal.core.MissionContext.CompletedTaskResult("task2","default-two","Plain text {not parsed}"));
        var composition=new OutputBindingComposition(compile(root),bindings,Map.of(),accepted,"owner",LoomspanJacksonCodecs.defaults().schemaTree());
        var result=composition.composeEmpty();
        assertThat(result.validation().valid()).isTrue();
        assertThat(result.content()).contains("[900719925474099312345,2]","Plain text {not parsed}");
        assertThat(result.provenance().getFirst()).containsEntry("sourceTaskId","task1").containsEntry("parentMissionFrameId","owner").doesNotContainKey("value");
        assertThat(accepted.getFirst().result()).isEqualTo("{\"values\":[900719925474099312345,2]}");
        assertThatThrownBy(()->new OutputBindingComposition(compile(root),bindings,Map.of(),List.of(accepted.getFirst(),accepted.getFirst()),"owner",LoomspanJacksonCodecs.defaults().schemaTree())).hasMessageContaining("binding_source_ambiguous");
    }
    @Test void projectionPreservesEnumFormatDescriptionAndUnboundNestedOpenSpace() {
        var choice=schema("string");choice.putArray("enum").add("accepted").add("declined");choice.put("description","Reasoned decision");choice.putArray("type").add("string").add("null");
        var context=object(Map.of("evidence",schema("string"),"decision",choice),"evidence","decision");context.put("additionalProperties",true);
        var composition=compose(object(Map.of("context",context)),List.of(input("/context/evidence","/source")),Map.of("source","exact"));
        var projected=composition.projection().schema().tree().path("properties").path("context");
        assertThat(projected.path("additionalProperties").booleanValue()).isTrue();
        var decision=projected.path("properties").path("decision");
        assertThat(decision.path("type").toString().contains("null")).isTrue();assertThat(decision.path("enum").valueStream().map(v->v.asText()).toList()).containsExactly("accepted","declined");
        assertThat(decision.path("description").asText()).isEqualTo("Reasoned decision");
        assertThat(composition.compose("{\"context\":{\"decision\":\"invented\"}}").validation().valid()).isFalse();
        assertThat(composition.compose("{\"context\":{\"decision\":\"accepted\",\"extra\":42}}").validation().valid()).isTrue();
    }
    @Test void escapedObjectKeysAndNestedJsonStringsRemainExactAcrossCorrectionAndSourceMutation() {
        var value=schema("object");value.put("additionalProperties",true);
        var root=object(Map.of("a/b",object(Map.of("0",value,"summary",schema("string")),"0","summary")));
        var retained=new LinkedHashMap<String,Object>();retained.put("text","{\"still\":\"a string\"}");retained.put("rows",new ArrayList<>(List.of(3,2,1)));
        var source=new LinkedHashMap<String,Object>();source.put("a~b",retained);
        var composition=compose(root,List.of(input("/a~1b/0","/a~0b")),source);
        assertThat(composition.compose("{}").validation().valid()).isFalse();
        retained.put("text","mutated");((List<Integer>)retained.get("rows")).clear();source.clear();
        var result=composition.compose("{\"a/b\":{\"summary\":\"reasoned\"}}");
        assertThat(result.validation().valid()).isTrue();
        var tree=LoomspanJacksonCodecs.defaults().schemaTree().readTree(result.content());
        assertThat(tree.path("a/b").path("0").path("text").asText()).isEqualTo("{\"still\":\"a string\"}");
        assertThat(tree.path("a/b").path("0").path("rows").toString()).isEqualTo("[3,2,1]");
    }
    @Test void unavailableChildAndMissingSubtreeNeverBecomeModelOwnedFallbacks() {
        var root=object(Map.of("value",schema("string"),"summary",schema("string")),"value","summary");
        var binding=new ChildInputBinding(ObjectFieldPath.parse("/value",false),ChildInputBinding.SourceKind.CHILD_RESULT,ObjectFieldPath.parse("/missing",true),"default-producer");
        assertThatThrownBy(()->new OutputBindingComposition(compile(root),List.of(binding),Map.of(),List.of(),"owner",LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("binding_source_unavailable");
        assertThatThrownBy(()->new OutputBindingComposition(compile(root),List.of(binding),Map.of(),List.of(new ai.loomspan.internal.core.MissionContext.CompletedTaskResult("task","default-producer","{}")),"owner",LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("binding_source_unavailable").hasMessageContaining("/missing");
    }
    @Test void standardOpennessAtRootAndNestedAncestorsControlsContribution() {
        for (String openness : List.of("omitted","true","false")) {
            var root=object(Map.of("value",schema("string")),"value");
            if (openness.equals("omitted")) root.remove("additionalProperties"); else root.put("additionalProperties",Boolean.parseBoolean(openness));
            var composition=compose(root,List.of(input("/value","/source")),Map.of("source","exact"));
            assertThat(composition.modelContributionRequired()).as(openness).isEqualTo(!openness.equals("false"));
            var nested=object(Map.of("value",schema("string")),"value");
            if(openness.equals("omitted"))nested.remove("additionalProperties");else nested.put("additionalProperties",Boolean.parseBoolean(openness));
            var outer=object(Map.of("context",nested),"context");
            assertThat(compose(outer,List.of(input("/context/value","/source")),Map.of("source","exact")).modelContributionRequired()).as("nested "+openness).isEqualTo(!openness.equals("false"));
            assertThat(compose(outer,List.of(input("/context","/source")),Map.of("source",Map.of("value","exact"))).modelContributionRequired()).isFalse();
        }
    }
    @Test void boundAndModelBoundsFormatsAndFinalContractAreValidatedIndependently() {
        var email=schema("string");email.put("format","email").put("minLength",5).put("maxLength",40);
        var amount=schema("number");amount.put("minimum",1).put("maximum",10);
        var rows=schema("array");rows.set("items",amount);rows.put("minItems",1).put("maxItems",2);
        var root=object(Map.of("email",email,"rows",rows),"email","rows");
        assertThatThrownBy(()->compose(root,List.of(input("/email","/source")),Map.of("source","invalid"))).hasMessageContaining("binding_output_validation");
        var c=compose(root,List.of(input("/email","/source")),Map.of("source","a@example.com"));
        for(String candidate:List.of("{}","{\"rows\":[]}","{\"rows\":[0]}","{\"rows\":[11]}","{\"rows\":[1,2,3]}"))
            assertThat(c.compose(candidate).validation().valid()).as(candidate).isFalse();
        assertThat(c.compose("{\"rows\":[1,10]}").validation().valid()).isTrue();
        assertThatThrownBy(()->compose(root,List.of(input("/rows","/source")),Map.of("source",List.of(0)))).hasMessageContaining("binding_output_validation");
        // Unknown/open declarations are runtime obligations; complete output still guards closed schemas.
        var invalid=compose(object(Map.of("value",schema("string")),"value"),List.of(input("/other","/source")),Map.of("source","exact"));
        assertThat(invalid.compose("{\"value\":\"ok\"}").validation().valid()).isFalse();
    }
    @Test void nullableBindingAncestorsAndSchemaCopiesRetainExactConstraintsAndNumbers() {
        var amount=schema("number");amount.put("minimum",0);
        var context=object(Map.of("0",amount,"note",schema("string")),"0","note");context.putArray("type").add("object").add("null");
        var root=object(Map.of("context",context));var before=root.deepCopy();
        var number=new java.math.BigDecimal("1.0000000000000000000000000001");
        var c=compose(root,List.of(input("/context/0","/source")),Map.of("source",number));
        assertThat(c.compose("{}").validation().valid()).isFalse();
        assertThat(c.compose("{\"context\":null}").validation().valid()).isFalse();
        assertThat(c.compose("{\"context\":{\"note\":\"ok\"}}").content()).contains(number.toPlainString());
        var copy=c.projection().schema().providerTree();((ObjectNode)copy).remove("properties");
        assertThat(c.projection().schema().tree().path("properties").has("context")).isTrue();
        assertThat(root).isEqualTo(before);
    }

}
