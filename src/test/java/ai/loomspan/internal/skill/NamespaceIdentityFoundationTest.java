package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamespaceIdentityFoundationTest {
    private LoomspanProperties properties() {
        var props = new LoomspanProperties();
        props.getSkills().setLocations(List.of("classpath:/namespace-foundation-none/*.yaml"));
        var connection = new LoomspanProperties.ConnectionProperties(); connection.setDriver(AiDriver.OPENAI);
        props.setConnections(Map.of("connection", connection));
        var model = new LoomspanProperties.ModelCatalogEntry(); model.setConnection("connection"); model.setProviderModel("model"); model.setThinkingLevels(Set.of("medium"));
        props.setModels(Map.of("model", model)); return props;
    }
    private SkillDocument rest(String name) { return new SkillDocument("raw source " + name, "name: " + name + "\ndescription: leaf\nrest: true\n"); }
    private CapabilityMetadata javaSkill(String name) {
        return new CapabilityMetadata("java source", name, "Java", SkillExecutionDescriptor.none(), SkillAccessPolicy.unrestricted(),
                args -> { throw new AssertionError("validation must not execute Java"); }, CapabilityKind.JAVA_SKILL,
                null, new SkillSource(null,"bean","method"));
    }
    private SkillGenerationManager manager(CapabilityMetadata... fixed) {
        var processor = mock(SkillMethodBeanPostProcessor.class); when(processor.capabilities()).thenReturn(List.of(fixed));
        var beans = new StaticListableBeanFactory(); beans.addBean("handler", (RestSkillHandler) request -> request.skillName()+"@"+request.generationId());
        var props=properties();
        var manager = new SkillGenerationManager(processor, () -> new YamlSkillCatalog(props), new SkillInputContractResolver(), beans);
        manager.afterSingletonsInstantiated(); return manager;
    }
    @Test void targetFeedbackAndGenerationsUseCanonicalOwnership() {
        var manager=manager(javaSkill("alpha-fixed"),javaSkill("beta-fixed"));
        var docs=List.of(rest("search"));
        var result=manager.validate("Alpha",docs);
        assertThat(result.valid()).isTrue();
        assertThat(result.skills()).extracting(ValidatedSkill::name).containsExactly("alpha-fixed","alpha-search");
        var alpha=manager.prepare("ALPHA",docs); var beta=manager.prepare("Beta",docs);
        assertThat(alpha.namespace()).isEqualTo("alpha"); assertThat(beta.namespace()).isEqualTo("beta");
        assertThat(alpha.skillCatalog().skills()).extracting(SkillDescriptor::name).containsExactly("alpha-fixed","alpha-search");
        assertThat(beta.skillCatalog().skills()).extracting(SkillDescriptor::name).containsExactly("beta-fixed","beta-search");
        assertThat(alpha.definitions()).containsOnlyKeys("alpha-search");
        assertThat(alpha.capability("alpha-search").invoker().invoke(Map.of())).isEqualTo("alpha-search@"+alpha.id());
        assertThat(alpha.id()).isNotEqualTo(beta.id());
        var retained=alpha.skillCatalog(); alpha.close();
        assertThat(retained.generationId()).isEqualTo(alpha.id()); assertThat(retained.skill("ALPHA-search")).isPresent();
        assertThat(retained.skill("beta-search")).isEmpty();
    }
    @Test void validationDoesNotReserveCandidateIdsAndErrorsAreIncomplete() {
        var manager=manager(); var first=manager.prepare("alpha",List.of(rest("search")));
        for(int i=0;i<3;i++) assertThat(manager.validate("alpha",List.of(rest("search"))).valid()).isTrue();
        var bad=manager.validate("alpha",List.of(rest("search"),rest("x".repeat(59))));
        assertThat(bad.valid()).isFalse(); assertThat(bad.skills()).isEmpty();
        var second=manager.prepare("alpha",List.of(rest("search")));
        long ordinal=Long.parseLong(first.id().substring(first.id().lastIndexOf('-')+1));
        assertThat(second.id()).endsWith("-"+(ordinal+1));
    }
    @Test void declarationsEnforceBothLimitsEvenForUnofferedRestSkills() {
        String ns="n".repeat(32);
        var catalog=new YamlSkillCatalog(properties());
        var valid=catalog.checkedSupplied(ns,List.of(rest("s".repeat(31))),false); assertThat(valid.issues()).isEmpty();
        var oversized=catalog.checkedSupplied(ns,List.of(rest("s".repeat(32))),false);
        assertThat(oversized.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.fieldPath()).isEqualTo("name"); assertThat(issue.sourceName()).startsWith("raw source");
            assertThat(issue.message()).contains("length 65","limit 64");
        });
        assertThatThrownBy(() -> catalog.checkedSupplied(ns+"n",List.of(rest("x")),false)).hasMessageContaining("length 33").hasMessageContaining("limit 32");
    }
    @Test void allReferenceFamiliesRejectMalformedAndOversizedReferencesWithContext() {
        var catalog=new YamlSkillCatalog(properties());
        for(String ref:List.of("child","a/b","a:b","a-b-c","n".repeat(33)+"-s","n".repeat(32)+"-"+"s".repeat(32))) {
            for(String field:List.of("allowed","forward","input","output","evidence")) {
                String base="name: parent\ndescription: parent\nmodel: model\nplanning_mode: true\n";
                String yaml=switch(field) {
                    case "allowed" -> base+"allowed_skills: [{name: '"+ref+"', max_tasks: 0}]\n";
                    case "forward" -> base+"allowed_skills: [{name: default-child, required: true, max_tasks: 1}]\noutput_from: {skill: '"+ref+"'}\n";
                    case "input" -> base+"allowed_skills: [{name: default-child, input_bindings: {/id: {from: child_result, skill: '"+ref+"', path: ''}}}]\n";
                    case "output" -> base+"allowed_skills: [{name: default-child}]\noutput_schema: {type: object}\noutput_bindings: {/id: {from: child_result, skill: '"+ref+"', path: ''}}\n";
                    default -> base+"allowed_skills: [{name: default-child}]\noutput_schema: {type: object, properties: {id: {type: string, evidence: '"+ref+"'}}}\n";
                };
                var checked=catalog.checkedSupplied(List.of(new SkillDocument("exact raw source",yaml)),false);
                assertThat(checked.issues()).as(field+" "+ref).isNotEmpty();
                assertThat(checked.issues().getFirst().sourceName()).isEqualTo("exact raw source");
                assertThat(checked.issues().getFirst().fieldPath()).isNotBlank();
                if(ref.length()==65) assertThat(checked.issues().getFirst().message()).contains("length 65","limit 64");
                if(ref.startsWith("n".repeat(33))) assertThat(checked.issues().getFirst().message()).contains("length 33","limit 32");
            }
        }
    }
    @Test void acceptsBoundaryReferencesAndRetainsCanonicalBindingIdentities() {
        String target="N".repeat(32)+"-"+"s".repeat(31);
        String canonical=target.toLowerCase(java.util.Locale.ROOT);
        String base="name: parent\ndescription: parent\nmodel: model\nplanning_mode: true\n";
        for(String yaml:List.of(
                base+"allowed_skills: [{name: "+target+", max_tasks: 0}]\n",
                base+"allowed_skills: [{name: "+target+", required: true, max_tasks: 1}]\noutput_from: {skill: "+target+"}\n",
                base+"allowed_skills: [{name: default-consumer, input_bindings: {/id: {from: child_result, skill: "+target+", path: ''}}}, {name: "+target+"}]\n",
                base+"allowed_skills: [{name: "+target+"}]\noutput_schema: {type: object}\noutput_bindings: {/id: {from: child_result, skill: "+target+", path: ''}}\n",
                base+"allowed_skills: [{name: "+target+"}]\noutput_schema: {type: object, properties: {id: {type: string, evidence: '"+target+"'}}}\n")) {
            var checked=new YamlSkillCatalog(properties()).checkedSupplied(List.of(new SkillDocument("boundary",yaml)),false);
            assertThat(checked.issues()).as(yaml).isEmpty();
            var definition=checked.definitions().getFirst();
            assertThat(definition.allowedSkills()).contains(canonical);
            if(!definition.outputBindings().isEmpty()) assertThat(definition.outputBindings().getFirst().skillIdentity().toString()).isEqualTo(canonical);
        }
        var parser=new ai.loomspan.internal.runtime.evidence.EvidenceExpressionParser();
        var expression=parser.parse("Tax-search and (orders-load or default-fallback)");
        assertThat(expression.referencedSkills()).containsExactly("tax-search","orders-load","default-fallback");
        assertThat(parser.parse("and-search").canonical()).isEqualTo("and-search");
    }

    @Test void resolvedReferenceComparisonsPreserveRawContent() {
        String yaml="""
                name: parent
                description: private description
                model: model
                prompt: Keep Tax-child exactly in this private prompt.
                planning_mode: true
                allowed_skills: [{name: Tax-child, required: true, max_tasks: 1}]
                output_from: {skill: TAX-child}
                """;
        var checked=new YamlSkillCatalog(properties()).checkedSupplied("Tax",List.of(new SkillDocument("opaque raw source",yaml)),false);
        assertThat(checked.issues()).isEmpty(); var definition=checked.definitions().getFirst();
        assertThat(definition.allowedSkills()).containsExactly("tax-child"); assertThat(definition.outputFromSkill()).isEqualTo("tax-child");
        assertThat(definition.source().bytes()).isEqualTo(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(definition.prompt()).contains("Tax-child"); assertThat(definition.manifest().getName()).isEqualTo("parent");
        var duplicate=new YamlSkillCatalog(properties()).checkedSupplied("tax",List.of(new SkillDocument("duplicate",yaml.replace("required: true, max_tasks: 1", "required: true, max_tasks: 1}, {name: tax-child"))),false);
        assertThat(duplicate.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("duplicate allowed skill"));
    }
    @Test void generationRejectsForeignAndLocalKeys() {
        var metadata=javaSkill("alpha-search");
        assertThatThrownBy(() -> new SkillGeneration("alpha","id",Map.of("search",metadata),Map.of())).hasMessageContaining("capability key");
        assertThatThrownBy(() -> new SkillGeneration("beta","id",Map.of(metadata.name(),metadata),Map.of())).hasMessageContaining("capability key");
        var checked=new YamlSkillCatalog(properties()).checkedSupplied("alpha",List.of(rest("search")),false);
        var definition=checked.definitions().getFirst();
        assertThatThrownBy(() -> new SkillGeneration("alpha","bad-kind",Map.of(metadata.name(),new CapabilityMetadata("yaml", "alpha-search", "wrong kind", SkillExecutionDescriptor.none(), null, args -> "unused", CapabilityKind.YAML_SKILL, null, null)),Map.of("alpha-search",definition)))
                .hasMessageContaining("matching YAML, REST or TYPESAFE capability");
        assertThatThrownBy(() -> new SkillGeneration("alpha","local-definition",Map.of(metadata.name(),metadata),Map.of("search",definition)))
                .hasMessageContaining("definition key");
        var empty=new SkillGeneration("Alpha","opaque",Map.of(),Map.of());
        assertThat(empty.skillCatalog().namespace()).isEqualTo("alpha"); assertThat(empty.skillCatalog().generationId()).isEqualTo("opaque");
    }
}
