package ai.loomspan.internal.outputschema;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.YamlSkillManifest.OutputSchemaManifest;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class OutputBindingCompositionTest {
    @Test void distinctUnicodeOutputPropertiesRetainNativeOutputMatching() {
        var fields=new LinkedHashMap<String,OutputSchemaManifest>();
        fields.put("i",schema("string"));fields.put("\u0131",schema("string"));
        var original=object(fields,"i","\u0131");
        var composition=compose(original,List.of(input("/i","/source")),Map.of("source","exact"));
        assertThat(composition.modelContributionRequired()).isTrue();
        var result=composition.compose("{\"\u0131\":\"reasoned\"}");
        assertThat(result.validation().valid()).isTrue();
        assertThat(result.content()).contains("\"i\":\"exact\"","\"\u0131\":\"reasoned\"");
    }
    private OutputSchemaManifest schema(String type) { var s=new OutputSchemaManifest(); s.setType(type); s.setAdditionalProperties(false); return s; }
    private OutputSchemaManifest object(Map<String,OutputSchemaManifest> properties,String...required) { var s=schema("object");s.setProperties(properties);s.setRequired(List.of(required));return s; }
    private ChildInputBinding input(String destination,String source) { return new ChildInputBinding(ObjectFieldPath.parse(destination,false),ChildInputBinding.SourceKind.INPUT,ObjectFieldPath.parse(source,true),null); }
    private OutputBindingComposition compose(OutputSchemaManifest s,List<ChildInputBinding>b,Map<String,Object>input) { return new OutputBindingComposition(s,b,input,List.of(),"owner",LoomspanJacksonCodecs.defaults().schemaTree()); }
    @Test void preservesOptionalAncestorRequiredSiblingAndNullableUnboundFields() {
        var nullable=schema("string");nullable.setNullable(true);
        var original=object(Map.of("context",object(Map.of("evidence",schema("string"),"reasoning",nullable),"evidence","reasoning")));
        var composition=compose(original,List.of(input("/context/evidence","/source")),Map.of("source","exact"));
        assertThat(composition.modelContributionRequired()).isTrue();
        assertThat(composition.projection().schema().getRequired()).containsExactly("context");
        assertThat(composition.compose("{}").validation().valid()).isFalse();
        assertThat(composition.compose("{\"context\":{\"reasoning\":null}}").validation().valid()).isTrue();
        assertThat(original.getRequired()).isEmpty();
    }
    @Test void fullBindingAndOpenOrOptionalResidualSpace() {
        var binding=input("/value","/source");var closed=object(Map.of("value",schema("string")),"value");
        assertThat(compose(closed,List.of(binding),Map.of("source","x")).modelContributionRequired()).isFalse();
        closed.setAdditionalProperties(true);
        assertThat(compose(closed,List.of(binding),Map.of("source","x")).modelContributionRequired()).isTrue();
        var open=schema("object");open.setAdditionalProperties(true);var root=object(Map.of("value",open),"value");
        assertThat(compose(root,List.of(binding),Map.of("source",Map.of("nested",1))).modelContributionRequired()).isFalse();
        root.setProperties(Map.of("value",open,"optional",object(Map.of())));
        assertThat(compose(root,List.of(binding),Map.of("source",Map.of())).modelContributionRequired()).isTrue();
    }
    @Test void overridesIncludeCaseAliasesEqualNullAndBlockingAncestors() {
        var composition=compose(object(Map.of("context",object(Map.of("evidence",schema("string"),"reasoning",schema("string")),"evidence","reasoning"))),List.of(input("/context/evidence","/source")),Map.of("source","exact"));
        for(String candidate:List.of("{\"context\":{\"evidence\":\"exact\"}}","{\"context\":{\"EVIDENCE\":null}}","{\"CONTEXT\":null}","{\"context\":[]}"))
            assertThat(composition.compose(candidate).validation().issues()).anyMatch(issue->issue.code().equals("binding_model_override"));
        assertThat(composition.compose("{\"CONTEXT\":{\"reasoning\":\"ok\"}}").validation().valid()).isTrue();
    }
    @Test void missingAndInvalidSourcesFailBeforeModelButNullableNullSucceeds() {
        var nullable=schema("string");nullable.setNullable(true);var root=object(Map.of("value",nullable));
        assertThatThrownBy(()->compose(root,List.of(input("/value","/missing")),Map.of())).hasMessageContaining("binding_source_unavailable");
        assertThatThrownBy(()->compose(root,List.of(input("/value","/source")),Map.of("source",42))).hasMessageContaining("binding_output_validation");
        var input=new LinkedHashMap<String,Object>();input.put("source",null);
        assertThat(compose(root,List.of(input("/value","/source")),input).composeEmpty().content()).isEqualTo("{\"value\":null}");
    }
    @Test void exactChildValuesPlainTextArraysAndProvenanceRemainDetached() {
        var array=schema("array");array.setItems(schema("integer"));
        var root=object(Map.of("numbers",array,"text",schema("string")),"numbers","text");
        var bindings=List.of(new ChildInputBinding(ObjectFieldPath.parse("/numbers",false),ChildInputBinding.SourceKind.CHILD_RESULT,ObjectFieldPath.parse("/values",true),"one"),new ChildInputBinding(ObjectFieldPath.parse("/text",false),ChildInputBinding.SourceKind.CHILD_RESULT,ObjectFieldPath.parse("",true),"two"));
        var accepted=List.of(new ai.loomspan.internal.core.MissionContext.CompletedTaskResult("task1","one","{\"values\":[900719925474099312345,2]}"),new ai.loomspan.internal.core.MissionContext.CompletedTaskResult("task2","two","Plain text {not parsed}"));
        var composition=new OutputBindingComposition(root,bindings,Map.of(),accepted,"owner",LoomspanJacksonCodecs.defaults().schemaTree());
        var result=composition.composeEmpty();
        assertThat(result.validation().valid()).isTrue();
        assertThat(result.content()).contains("[900719925474099312345,2]","Plain text {not parsed}");
        assertThat(result.provenance().getFirst()).containsEntry("sourceTaskId","task1").containsEntry("parentMissionFrameId","owner").doesNotContainKey("value");
        assertThat(accepted.getFirst().result()).isEqualTo("{\"values\":[900719925474099312345,2]}");
        assertThatThrownBy(()->new OutputBindingComposition(root,bindings,Map.of(),List.of(accepted.getFirst(),accepted.getFirst()),"owner",LoomspanJacksonCodecs.defaults().schemaTree())).hasMessageContaining("binding_source_ambiguous");
    }
    @Test void projectionPreservesEnumFormatDescriptionAndUnboundNestedOpenSpace() {
        var choice=schema("string");choice.setEnumValues(List.of("accepted","declined"));choice.setFormat("choice");choice.setDescription("Reasoned decision");choice.setNullable(true);
        var context=object(Map.of("evidence",schema("string"),"decision",choice),"evidence","decision");context.setAdditionalProperties(true);
        var composition=compose(object(Map.of("context",context)),List.of(input("/context/evidence","/source")),Map.of("source","exact"));
        var projected=composition.projection().schema().getProperties().get("context");
        assertThat(projected.getAdditionalProperties()).isTrue();
        var decision=projected.getProperties().get("decision");
        assertThat(decision.getNullable()).isTrue();assertThat(decision.getEnumValues()).containsExactly("accepted","declined");
        assertThat(decision.getFormat()).isEqualTo("choice");assertThat(decision.getDescription()).isEqualTo("Reasoned decision");
        assertThat(composition.compose("{\"context\":{\"decision\":\"invented\"}}").validation().valid()).isFalse();
        assertThat(composition.compose("{\"context\":{\"decision\":\"accepted\",\"extra\":42}}").validation().valid()).isTrue();
    }
    @Test void escapedObjectKeysAndNestedJsonStringsRemainExactAcrossCorrectionAndSourceMutation() {
        var value=schema("object");value.setAdditionalProperties(true);
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
        var binding=new ChildInputBinding(ObjectFieldPath.parse("/value",false),ChildInputBinding.SourceKind.CHILD_RESULT,ObjectFieldPath.parse("/missing",true),"producer");
        assertThatThrownBy(()->new OutputBindingComposition(root,List.of(binding),Map.of(),List.of(),"owner",LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("binding_source_unavailable");
        assertThatThrownBy(()->new OutputBindingComposition(root,List.of(binding),Map.of(),List.of(new ai.loomspan.internal.core.MissionContext.CompletedTaskResult("task","producer","{}")),"owner",LoomspanJacksonCodecs.defaults().schemaTree()))
                .hasMessageContaining("binding_source_unavailable").hasMessageContaining("/missing");
    }
}
