package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamespaceIdentityFoundationTest {
    private static final List<String> FIELDS = List.of("allowed", "executor", "forward", "input", "output", "evidence");

    @Test void localAndQualifiedFieldsSelectDistinctIdentities() {
        for (String field : FIELDS) {
            for (String reference : List.of("warehouse-lookup", "warehouse:lookup")) {
                var checked = check("orders", authored(field, reference, reference));
                assertThat(checked.issues()).as(field + " " + reference).isEmpty();
                String expected = reference.contains(":") ? reference : "orders:" + reference;
                assertThat(selected(checked.definitions().getFirst(), field)).isEqualTo(expected);
            }
        }
    }

    @Test void bindingsForwardingAndEvidenceCannotMatchForeignOnlyChildren() {
        for (String field : List.of("forward", "output", "evidence")) {
            var checked = check("orders", authored(field, "warehouse-lookup", "warehouse:lookup"));
            assertThat(checked.issues()).as(field).isNotEmpty();
            assertThat(checked.issues()).allSatisfy(issue -> {
                assertThat(issue.sourceName()).isEqualTo("exact raw source");
                assertThat(issue.fieldPath()).isNotBlank();
            });
        }

        var input = check("orders", authored("input", "warehouse-lookup", "warehouse:lookup"));
        assertThat(input.issues()).isEmpty();
        assertThat(ai.loomspan.internal.runtime.input.ChildInputBindingDeclarations.validate(null,
                input.definitions().getFirst().allowedSkillConstraints(), Map.of()))
                .anySatisfy(issue -> assertThat(issue).contains("unknown direct producer 'orders:warehouse-lookup'"));
    }

    @Test void allReferenceFamiliesRejectMalformedComponentsWithContext() {
        for (String reference : List.of("Lookup", " orders:lookup", "orders:lookup ", "3lookup", "look_up",
                "orders/lookup", "orders::lookup", "orders:", ":lookup", "orders:lookup--detail",
                "lookup-", "löö kup", "claude-tools:lookup", "orders:lookup-anthropic")) {
            for (String field : FIELDS) {
                if (field.equals("evidence") && !reference.equals(reference.strip())) continue;
                var checked = check("orders", authored(field, reference, "warehouse:lookup"));
                assertThat(checked.issues()).as(field + " " + reference).isNotEmpty();
                assertThat(checked.issues()).allSatisfy(issue -> {
                    assertThat(issue.sourceName()).isEqualTo("exact raw source");
                    assertThat(issue.fieldPath()).isNotBlank();
                });
            }
        }
    }

    @Test void pairLimitsApplyAtEveryReferenceField() {
        for (String field : FIELDS) {
            String valid = "s".repeat(49);
            assertThat(check("order-support", authored(field, valid, valid)).issues()).as(field).isEmpty();
            String oversized = "s".repeat(50);
            var checked = check("order-support", authored(field, oversized, "child"));
            assertThat(checked.issues()).as(field).anySatisfy(issue ->
                    assertThat(issue.message()).contains("length 65", "limit 64"));
            // The target namespace determines an explicit reference's pair limit.
            assertThat(check("order-support", authored(field, "a:" + oversized, "a:" + oversized)).issues())
                    .as(field + " explicit owner").isEmpty();
        }
    }

    @Test void acceptsHyphenatedAndDigitReferencesAndQualifiedOperators() {
        for (String reference : List.of("lookup-3pl", "order-support:find-order", "orders:and", "orders:or")) {
            for (String field : FIELDS) {
                assertThat(check("orders", authored(field, reference, reference)).issues())
                        .as(field + " " + reference).isEmpty();
            }
        }
    }

    @Test void sourcePromptSchemaAndRawEvidenceRetainAuthoredContent() {
        String yaml = """
                name: parent
                description: Keep warehouse-lookup and TAX-child exactly.
                model: model
                prompt: Keep warehouse-lookup and TAX-child exactly.
                planning_mode: true
                allowed_skills:
                  - name: warehouse-lookup
                  - name: warehouse:lookup
                  - name: consumer
                    input_bindings: {/id: {from: child_result, skill: warehouse-lookup, path: ''}}
                output_schema:
                  type: object
                  properties:
                    ResultValue: {type: string, description: 'Keep TAX-child', x-loomspan-evidence: 'warehouse-lookup or warehouse:lookup'}
                output_bindings: {/ResultValue: {from: child_result, skill: warehouse:lookup, path: ''}}
                """;
        var checked = check("orders", yaml);
        assertThat(checked.issues()).isEmpty();
        var definition = checked.definitions().getFirst();
        assertThat(definition.source().bytes()).isEqualTo(yaml.getBytes(StandardCharsets.UTF_8));
        assertThat(definition.prompt()).isEqualTo("Keep warehouse-lookup and TAX-child exactly.");
        assertThat(definition.manifest().getDescription()).isEqualTo("Keep warehouse-lookup and TAX-child exactly.");
        assertThat(definition.manifest().getOutputSchema().at("/properties/ResultValue/description").textValue()).isEqualTo("Keep TAX-child");
        assertThat(definition.manifest().getOutputSchema().at("/properties/ResultValue/x-loomspan-evidence").textValue())
                .isEqualTo("warehouse-lookup or warehouse:lookup");
        assertThat(definition.evidenceContract().canonicalExpressionForClaim("ResultValue"))
                .isEqualTo("orders:warehouse-lookup or warehouse:lookup");
        assertThat(definition.allowedSkillConstraints().get(2).inputBindings().getFirst().skillIdentity().toString())
                .isEqualTo("orders:warehouse-lookup");
        assertThat(definition.outputBindings().getFirst().skillIdentity().toString()).isEqualTo("warehouse:lookup");
    }

    @Test void evidenceErrorsRetainOriginalTokenColumns() {
        for (String expression : List.of("  first and Second", "  first and missing", "  first and )")) {
            String yaml = "name: parent\ndescription: parent\nmodel: model\nallowed_skills: [{name: first}, {name: second}]\n"
                    + "output_schema: {type: object, properties: {id: {type: string, x-loomspan-evidence: '" + expression + "'}}}\n";
            assertThat(check("orders", yaml).issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceName()).isEqualTo("exact raw source");
                assertThat(issue.fieldPath()).isEqualTo("output_schema.properties.id.x-loomspan-evidence");
                assertThat(issue.message()).contains("column 13");
            });
        }
    }

    @Test void targetFeedbackAndGenerationsKeepExactOwnership() {
        var manager = manager(javaSkill("alpha:fixed"), javaSkill("beta:fixed"));
        var docs = List.of(modelLeaf("search"));
        var result = manager.validate(manager.capturePreparation("alpha"), docs,
                ai.loomspan.testkit.PublicationFixtures.configuration(), NamespaceVisibility.PRIVATE);
        assertThat(result.valid()).isTrue();
        assertThat(result.skills()).extracting(ValidatedSkill::qualifiedName).containsExactly("alpha:fixed", "alpha:search");
        var alpha = manager.prepare(manager.capturePreparation("alpha"), docs,
                ai.loomspan.testkit.PublicationFixtures.configuration(), NamespaceVisibility.PRIVATE,
                Map.of("fixture.key", "fixture"), null);
        var beta = manager.prepare(manager.capturePreparation("beta"), docs,
                ai.loomspan.testkit.PublicationFixtures.configuration(), NamespaceVisibility.PRIVATE,
                Map.of("fixture.key", "fixture"), null);
        assertThat(alpha.skillCatalog().skills()).extracting(SkillDescriptor::qualifiedName).containsExactly("alpha:fixed", "alpha:search");
        assertThat(beta.skillCatalog().skills()).extracting(SkillDescriptor::qualifiedName).containsExactly("beta:fixed", "beta:search");
        assertThat(alpha.definitions()).containsOnlyKeys("alpha:search");
        assertThat(alpha.id()).isNotEqualTo(beta.id());
        var retained = alpha.skillCatalog();
        alpha.close();
        assertThat(retained.skill("search")).isPresent();
        assertThatThrownBy(() -> retained.skill("beta:search")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.capturePreparation("Alpha")).isInstanceOf(IllegalArgumentException.class);
        beta.close();
        manager.destroy();
    }

    @Test void validationDoesNotReserveCandidateIdsAndErrorsAreIncomplete() {
        var manager = manager();
        var configuration = ai.loomspan.testkit.PublicationFixtures.configuration();
        var first = manager.prepare(manager.capturePreparation("alpha"), List.of(modelLeaf("search")), configuration,
                NamespaceVisibility.PRIVATE, Map.of("fixture.key", "fixture"), null);
        for (int i = 0; i < 3; i++) {
            assertThat(manager.validate(manager.capturePreparation("alpha"), List.of(modelLeaf("search")), configuration,
                    NamespaceVisibility.PRIVATE).valid()).isTrue();
        }
        var bad = manager.validate(manager.capturePreparation("alpha"), List.of(modelLeaf("search"), modelLeaf("x".repeat(58))),
                configuration, NamespaceVisibility.PRIVATE);
        assertThat(bad.valid()).isFalse();
        assertThat(bad.skills()).isEmpty();
        var second = manager.prepare(manager.capturePreparation("alpha"), List.of(modelLeaf("search")), configuration,
                NamespaceVisibility.PRIVATE, Map.of("fixture.key", "fixture"), null);
        long ordinal = Long.parseLong(first.id().substring(first.id().lastIndexOf('-') + 1));
        assertThat(second.id()).endsWith("-" + (ordinal + 1));
        first.close();
        second.close();
        manager.destroy();
    }

    @Test void generationRejectsForeignAndLocalKeys() {
        var metadata = javaSkill("alpha:search");
        assertThatThrownBy(() -> new SkillGeneration("alpha", "id", Map.of("search", metadata), Map.of())).hasMessageContaining("capability key");
        assertThatThrownBy(() -> new SkillGeneration("beta", "id", Map.of(metadata.name(), metadata), Map.of())).hasMessageContaining("capability key");
        var definition = check("alpha", modelLeaf("search").yaml()).definitions().getFirst();
        assertThatThrownBy(() -> new SkillGeneration("alpha", "bad-kind", Map.of(metadata.name(), metadata), Map.of("alpha:search", definition)))
                .hasMessageContaining("matching YAML capability");
        assertThatThrownBy(() -> new SkillGeneration("alpha", "local-definition", Map.of(metadata.name(), metadata), Map.of("search", definition)))
                .hasMessageContaining("definition key");
        assertThatThrownBy(() -> new SkillGeneration("Alpha", "opaque", Map.of(), Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private YamlSkillCatalog.CheckedDocuments check(String owner, String yaml) {
        return new YamlSkillCatalog(properties()).checkedSupplied(owner, List.of(new SkillDocument("exact raw source", yaml)), false);
    }

    private String authored(String field, String reference, String child) {
        String quoted = "'" + reference + "'";
        if (field.equals("executor")) return "name: parent\ndescription: parent\nexecutor: " + quoted + "\n";
        String base = "name: parent\ndescription: parent\nmodel: model\nplanning_mode: true\n";
        return switch (field) {
            case "allowed" -> base + "allowed_skills: [{name: " + quoted + "}]\n";
            case "forward" -> base + "allowed_skills: [{name: '" + child + "', required: true, max_tasks: 1}]\noutput_from: {skill: " + quoted + "}\n";
            case "input" -> base + "allowed_skills: [{name: '" + child + "'}, {name: consumer, input_bindings: {/id: {from: child_result, skill: " + quoted + ", path: ''}}}]\n";
            case "output" -> base + "allowed_skills: [{name: '" + child + "'}]\noutput_schema: {type: object}\noutput_bindings: {/id: {from: child_result, skill: " + quoted + ", path: ''}}\n";
            case "evidence" -> base + "allowed_skills: [{name: '" + child + "'}]\noutput_schema: {type: object, properties: {id: {type: string, x-loomspan-evidence: " + quoted + "}}}\n";
            default -> throw new IllegalArgumentException(field);
        };
    }

    private String selected(YamlSkillDefinition definition, String field) {
        return switch (field) {
            case "executor" -> definition.executor();
            case "forward" -> definition.outputFromSkill();
            case "input" -> definition.allowedSkillConstraints().get(1).inputBindings().getFirst().skillIdentity().toString();
            case "output" -> definition.outputBindings().getFirst().skillIdentity().toString();
            case "evidence" -> definition.evidenceContract().canonicalExpressionForClaim("id");
            default -> definition.allowedSkills().getFirst();
        };
    }

    private LoomspanProperties properties() {
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of("classpath:/namespace-foundation-none/*.yaml"));
        var connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.OPENAI);
        properties.setConnections(Map.of("connection", connection));
        var model = new LoomspanProperties.ModelCatalogEntry();
        model.setConnection("connection");
        model.setProviderModel("model");
        model.setThinkingLevels(Set.of("medium"));
        properties.setModels(Map.of("model", model));
        return properties;
    }

    private SkillDocument modelLeaf(String name) {
        return new SkillDocument("raw source " + name, "name: " + name + "\ndescription: leaf\nmodel: model\n");
    }

    private CapabilityMetadata javaSkill(String name) {
        return new CapabilityMetadata("java source", name, "Java", SkillExecutionDescriptor.none(), SkillAccessPolicy.unrestricted(),
                args -> { throw new AssertionError("validation must not execute Java"); }, SkillKind.JAVA,
                null, new SkillSource(null, "bean", "method", null));
    }

    private SkillGenerationManager manager(CapabilityMetadata... fixed) {
        var processor = mock(SkillMethodBeanPostProcessor.class);
        when(processor.capabilities()).thenReturn(List.of(fixed));
        var properties = properties();
        var manager = new SkillGenerationManager(processor, () -> new YamlSkillCatalog(properties), new SkillInputContractResolver());
        manager.afterSingletonsInstantiated();
        return manager;
    }
}
