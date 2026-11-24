package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static ai.loomspan.internal.skill.SkillReloaderTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillDependencyValidatorTest {
    @Test void richStructuralMetadataAgreesAcrossForwardingCatalogsAndDtoWithoutEvidenceInheritance() {
        var f = new Fixture(null);
        String yaml = model("leaf").yaml() + """
                allowed_skills: [{name: helper}]
                output_schema:
                  type: object
                  description: '<b>structural contract</b>'
                  required: [evidence]
                  additionalProperties: false
                  properties:
                    evidence: {type: [string, 'null'], minLength: 1, maxLength: 30, enum: [ok, null], x-loomspan-evidence: '  helper  '}
                    x-loomspan-evidence: {type: number, minimum: 0.25, maximum: 10}
                    records:
                      type: array
                      minItems: 1
                      maxItems: 4
                      items:
                        type: object
                        additionalProperties: true
                        properties:
                          evidence: {type: string, format: date}
                          x-loomspan-evidence: {type: array, items: {type: string, maxLength: 5}}
                """;
        f.reloader.publish(f.reloader.prepare("a", List.of(new SkillDocument("rich.yaml", yaml), model("helper"),
                forward("bridge", "leaf"), forward("root", "bridge")), MODEL));
        var generation = f.manager.active("a");
        var definition = generation.definition("a:leaf");
        var schema = definition.outputSchema();
        var canonical = schema.tree();
        var expected = canonical.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) expected.at("/properties/evidence")).remove("x-loomspan-evidence");
        assertThat(schema.structuralTree()).isEqualTo(expected);
        assertThat(definition.evidenceContract().claims()).containsExactly("evidence");
        assertThat(canonical.at("/properties/evidence/x-loomspan-evidence").textValue()).isEqualTo("  helper  ");
        ((tools.jackson.databind.node.ObjectNode) schema.providerTree()).removeAll();
        ((tools.jackson.databind.node.ObjectNode) schema.structuralTree()).removeAll();
        assertThat(schema.tree()).isEqualTo(canonical);
        var json = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree();
        for (String name : List.of("a:leaf", "a:bridge", "a:root")) {
            String displayed = generation.skillCatalog().skill(name).orElseThrow().outputSchema();
            var registered = generation.registeredSkillCatalog().find(name).orElseThrow();
            assertThat(json.readTree(displayed)).isEqualTo(expected);
            assertThat(registered.outputSchema()).isEqualTo(displayed);
            var dto = new ai.loomspan.internal.observability.web.ObservabilityDtoMapper().skill(registered);
            assertThat(dto.outputSchema()).isEqualTo(displayed);
            assertThat(dto.yaml()).isEqualTo(registered.yaml());
            if (!name.equals("a:leaf")) assertThat(generation.definition(name).evidenceContract().isEmpty()).isTrue();
        }
        assertThat(generation.registeredSkillCatalog().find("a:leaf").orElseThrow().yaml()).isEqualTo(yaml);
    }
    @Test void omittedLocalSkillsNeverFallBackToTheOldGeneration() {
        var f=new Fixture(null);
        f.reloader.publish(f.reloader.prepare("a",List.of(model("leaf")),MODEL));
        var result=f.reloader.validate("a",List.of(calls("parent","a:leaf")),MODEL);
        assertThat(result.valid()).isFalse();assertThat(result.skills()).isEmpty();
        assertThat(result.issues()).anyMatch(i->i.message().contains("a:leaf"));
        assertThatThrownBy(()->f.reloader.prepare("a",List.of(calls("parent","a:leaf")),MODEL)).isInstanceOf(SkillReloadException.class);
    }

    @Test void transitiveReturnUsesCandidateDeclarationsContractsAndVisibility() {
        var f=new Fixture(null);
        f.reloader.publish(f.reloader.prepare("a",List.of(producer("leaf","string")),MODEL,NamespaceVisibility.PUBLIC));
        f.reloader.publish(f.reloader.prepare("b",List.of(forward("bridge","a:leaf")),MODEL,NamespaceVisibility.PUBLIC));
        var changed=List.of(producer("leaf","integer"),forward("root","b:bridge"));
        var candidate=f.reloader.prepare("a",changed,MODEL,NamespaceVisibility.PUBLIC);
        assertThat(candidate.snapshot().skill("a:root").orElseThrow().outputSchema()).contains("integer");
        candidate.close();
        assertThat(f.reloader.validate("a",changed,MODEL).issues()).anyMatch(i->i.message().contains("Private"));
        assertThat(f.reloader.validate("a",List.of(forward("root","b:bridge")),MODEL,NamespaceVisibility.PUBLIC).issues()).anyMatch(i->i.message().contains("a:leaf"));
        // A real staged ordinary cycle remains valid; a forwarding/schema cycle remains invalid.
        f.reloader.publish(f.reloader.prepare("a",List.of(calls("leaf","b:bridge")),MODEL,NamespaceVisibility.PUBLIC));
        assertThat(f.reloader.validate("a",List.of(forward("leaf","b:bridge")),MODEL,NamespaceVisibility.PUBLIC).issues()).anyMatch(i->i.message().contains("Forwarding cycle"));
    }

    @Test void invalidOutgoingEdgesRejectBeforeRuntimeConstructionAndIncomingCallersAreNotScanned() {
        var f=new Fixture(null);
        f.reloader.publish(f.reloader.prepare("provider",List.of(model("leaf")),MODEL,NamespaceVisibility.PUBLIC));
        f.reloader.publish(f.reloader.prepare("incoming",List.of(calls("root","provider:leaf")),MODEL));
        // Replacement invalidates an incoming caller prospectively, without scanning it.
        f.reloader.publish(f.reloader.prepare("provider",List.of(),EMPTY));
        assertThat(f.reloader.snapshot("incoming").skills()).hasSize(1);
        for(String reference:List.of("provider:leaf","missing:leaf"))
            assertThat(f.reloader.validate("a",List.of(calls("root",reference)),MODEL).valid()).isFalse();
        f.reloader.publish(f.reloader.prepare("private",List.of(model("leaf")),MODEL));
        assertThat(f.reloader.validate("a",List.of(calls("root","private:leaf")),MODEL).issues()).anyMatch(i->i.message().contains("Private"));
    }

    @Test void capturedMetadataSurvivesPublicationRemovalAndResourceRetirement() throws Exception {
        var registries=new CopyOnWriteArrayList<ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry>();
        var f=new Fixture((p,policy)->{var r=mock(ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry.class);registries.add(r);return new ExecutionRuntime(p,policy,r);});
        var dep=f.reloader.prepare("dep",List.of(producer("leaf","string")),MODEL,NamespaceVisibility.PUBLIC);f.reloader.publish(dep);
        var captured=f.manager.capturePreparation("a");
        f.reloader.remove("dep",dep.generationId());
        verify(registries.get(1),times(1)).destroy();
        f.reloader.publish(f.reloader.prepare("dep",List.of(producer("leaf","integer")),MODEL));
        var built=f.manager.prepare(captured,List.of(forward("root","dep:leaf")),MODEL,NamespaceVisibility.PRIVATE,null, null);
        assertThat(built.skillCatalog().skill("a:root").orElseThrow().outputSchema()).contains("string");
        built.close();
        assertThat(f.reloader.validate("a",List.of(forward("root","dep:leaf")),MODEL).issues()).anyMatch(i->i.message().contains("Private"));
        // No executable dependency owner was retained while preparing or staging the candidate.
        verify(registries.get(1),times(1)).destroy();
    }

    @Test void metadataCaptureRacesWholeAssociationPublicationAndRemoval() throws Exception {
        var f=new Fixture(null);
        for(int i=0;i<12;i++){
            var dep=f.reloader.prepare("dep",List.of(producer("leaf","string")),MODEL,NamespaceVisibility.PUBLIC);f.reloader.publish(dep);
            boolean removal=i%2==0;
            var replacement=removal?null:f.reloader.prepare("dep",List.of(producer("leaf","integer")),MODEL,NamespaceVisibility.PUBLIC);
            var start=new CyclicBarrier(2);
            try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
                var mutation=workers.submit(()->{barrier(start);if(removal)f.reloader.remove("dep",dep.generationId());else f.reloader.publish(replacement);});
                var capture=workers.submit(()->{barrier(start);return f.manager.capturePreparation("a");});
                var view=capture.get(5,TimeUnit.SECONDS);mutation.get(5,TimeUnit.SECONDS);
                var result=f.manager.validate(view,List.of(forward("root","dep:leaf")),MODEL,NamespaceVisibility.PRIVATE);
                assertThat(result.valid()).isEqualTo(view.namespaces().containsKey("dep"));
                if(result.valid()) {
                    var prepared=f.manager.prepare(view,List.of(forward("root","dep:leaf")),MODEL,NamespaceVisibility.PRIVATE,null, null);
                    String expected=view.namespaces().get("dep").id().equals(dep.generationId())?"string":"integer";
                    assertThat(prepared.skillCatalog().skill("a:root").orElseThrow().outputSchema()).contains(expected);
                    prepared.close();
                }
            }
        }
    }

    @Test void ambiguousBareChildrenKeepTheirOwnerAndMissingLocalTargetsEndLookup() {
        var fixture = new Fixture(null);
        try {
            fixture.reloader.publish(fixture.reloader.prepare("warehouse", List.of(producer("lookup", "integer")), MODEL, NamespaceVisibility.PUBLIC));
            fixture.reloader.publish(fixture.reloader.prepare("orders", List.of(producer("warehouse-lookup", "string"),
                    forward("local", "warehouse-lookup"), forward("foreign", "warehouse:lookup")), MODEL));
            var generation = fixture.manager.active("orders");
            assertThat(generation.definition("orders:local").outputFromSkill()).isEqualTo("orders:warehouse-lookup");
            assertThat(generation.skillCatalog().skill("orders:local").orElseThrow().outputSchema()).contains("string");
            assertThat(generation.definition("orders:foreign").outputFromSkill()).isEqualTo("warehouse:lookup");
            assertThat(generation.skillCatalog().skill("orders:foreign").orElseThrow().outputSchema()).contains("integer");
            var capture = fixture.manager.capture(ai.loomspan.internal.core.SkillIdentity.parse("orders:local"));
            try {
                var admitted = fixture.manager.validateAdmission(capture.snapshot());
                assertThat(admitted.resolve(ai.loomspan.internal.core.SkillIdentity.parse("orders:warehouse-lookup")))
                        .isSameAs(generation.capability("orders:warehouse-lookup"));
            } finally { capture.lease().close(); }
            var missing = fixture.reloader.validate("orders", List.of(forward("local", "warehouse-lookup")), MODEL);
            assertThat(missing.valid()).isFalse();
            assertThat(missing.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("orders:warehouse-lookup"));
            assertThat(fixture.reloader.validate("orders", List.of(forward("foreign", "warehouse:lookup")), MODEL).valid()).isTrue();
        } finally { fixture.lifecycle.destroy(); fixture.manager.destroy(); }
    }

    static void barrier(CyclicBarrier b){try{b.await(5,TimeUnit.SECONDS);}catch(Exception ex){throw new AssertionError(ex);}}
    static SkillDocument producer(String name,String type){return new SkillDocument(name,model(name).yaml()+"output_schema: {type: object, properties: {value: {type: "+type+"}}}\n");}
    static SkillDocument forward(String name,String child){return new SkillDocument(name,model(name).yaml()+"planning_mode: true\nallowed_skills: [{name: "+child+", required: true, max_tasks: 1}]\noutput_from: {skill: "+child+"}\n");}
}

