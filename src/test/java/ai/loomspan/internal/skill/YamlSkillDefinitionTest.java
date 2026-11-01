package ai.loomspan.internal.skill;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlSkillDefinitionTest
{
    private static final EffectiveSkillExecutionConfiguration CONFIGURATION =
            new EffectiveSkillExecutionConfiguration("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    @Test
    void executorConfigCopiesContainersWithoutSerializingScalarLeaves() {
        Object scalar = new Object();
        var nested = new java.util.LinkedHashMap<String, Object>();
        nested.put("Nil", null);
        nested.put("Scalar", scalar);
        nested.put("Long", 7L);
        var items = new java.util.ArrayList<Object>();
        items.add(nested);
        var manifest = manifest("executorCopy");
        manifest.setExecutor("default-sql");
        manifest.setConfig(Map.of("items", items));
        var definition = new YamlSkillDefinition(YamlSkillDefinition.Form.EXECUTOR, "default", resource(), manifest, null);
        nested.clear(); items.clear(); manifest.setConfig(Map.of());
        var copy = (Map<?, ?>)((List<?>)definition.manifest().getConfig().get("items")).getFirst();
        assertThat(copy.get("Scalar")).isSameAs(scalar);
        assertThat(copy.get("Long")).isInstanceOf(Long.class).isEqualTo(7L);
        assertThat(copy.containsKey("Nil")).isTrue();
        assertThatThrownBy(copy::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesDeclaredFieldsAcrossDefinitionDefensiveCopies()
    {
        YamlSkillManifest manifest = manifest("llm_copy_skill");
        manifest.setModel(null);
        manifest.setThinkingLevel(" ");
        manifest.setPrompt(null);
        manifest.setPlanningMode(false);
        manifest.setMaxSteps(0);
        manifest.setAllowedSkills(List.of());
        manifest.setInputSchema(null);
        manifest.setOutputSchema(null);
        manifest.setLinter(null);
        manifest.setOutputSchemaMaxRetries(0);
        YamlSkillDefinition definition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), manifest, CONFIGURATION);
        YamlSkillManifest firstCopy = definition.manifest();
        YamlSkillManifest secondCopy = definition.manifest();

        assertThat(firstCopy.declaredFields()).isEqualTo(manifest.declaredFields());
        assertThat(firstCopy.isDeclared(YamlSkillManifest.Field.MODEL)).isTrue();
        assertThat(firstCopy.isDeclared(YamlSkillManifest.Field.ALLOWED_SKILLS)).isTrue();
        firstCopy.setModel("mutated");
        firstCopy.setRbacRoles(List.of("ROLE_MUTATED"));
        assertThat(secondCopy.getModel()).isNull();
        assertThat(secondCopy.getRbacRoles()).isEmpty();
        assertThat(new ObjectMapper().valueToTree(firstCopy).has("declaredFields")).isFalse();

        YamlSkillManifest omitted = manifest("llm_omitted_copy_skill");
        YamlSkillDefinition llm = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), omitted, CONFIGURATION);
        assertThat(llm.manifest().declaredFields()).isEmpty();

    }

    @Test
    void preservesEvidenceAndAbsenceAcrossDefinitionDefensiveCopies()
    {
        YamlSkillManifest.OutputSchemaManifest annotated = outputSchema("string");
        annotated.setEvidence("default-reviewSkill");
        YamlSkillManifest.OutputSchemaManifest unannotated = outputSchema("array");
        unannotated.setItems(outputSchema("string"));
        YamlSkillManifest.OutputSchemaManifest root = outputSchema("object");
        root.setProperties(Map.of("ResultValue", annotated, "notes", unannotated));

        YamlSkillManifest manifest = manifest("llm_evidence_copy_skill");
        manifest.setOutputSchema(root);
        EvidenceContract contract = ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(
                Map.of("ResultValue", "default-reviewSkill"));
        YamlSkillDefinition definition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), manifest, CONFIGURATION, contract);

        YamlSkillManifest.OutputSchemaManifest first = definition.outputSchema();
        assertThat(first.getProperties().get("ResultValue").getEvidence()).isEqualTo("default-reviewSkill");
        assertThat(first.getProperties().get("notes").getEvidence()).isNull();
        assertThat(first.getProperties().get("notes").getItems().getEvidence()).isNull();
        first.getProperties().get("ResultValue").setEvidence("mutated");
        assertThat(definition.outputSchema().getProperties().get("ResultValue").getEvidence())
                .isEqualTo("default-reviewSkill");
    }

    @Test
    void enforcesExecutionConfigurationInvariantByImplementationType()
    {
        YamlSkillManifest llm = manifest("llm_skill");
        YamlSkillDefinition llmDefinition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), llm, CONFIGURATION);
        assertThat(llmDefinition.requireExecutionConfiguration()).isSameAs(CONFIGURATION);

        assertThatThrownBy(() -> new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), llm, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("execution configuration");
    }



    @Test
    void resolvesPlanningConcurrencyAndPreservesItsDeclaration()
    {
        YamlSkillManifest omitted = manifest("planner_omitted");
        omitted.setPlanningMode(true);
        YamlSkillDefinition omittedDefinition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), omitted, CONFIGURATION);
        assertThat(omittedDefinition.concurrencyEnabled()).isTrue();
        assertThat(omittedDefinition.manifest().isDeclared(YamlSkillManifest.Field.CONCURRENCY)).isFalse();

        YamlSkillManifest enabled = manifest("planner_enabled");
        enabled.setPlanningMode(true);
        enabled.setConcurrency(true);
        YamlSkillDefinition enabledDefinition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), enabled, CONFIGURATION);
        assertThat(enabledDefinition.concurrencyEnabled()).isTrue();
        assertThat(enabledDefinition.manifest().isDeclared(YamlSkillManifest.Field.CONCURRENCY)).isTrue();

        YamlSkillManifest disabled = manifest("planner_disabled");
        disabled.setPlanningMode(true);
        disabled.setConcurrency(false);
        assertThat(new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), disabled, CONFIGURATION).concurrencyEnabled()).isFalse();

        YamlSkillManifest direct = manifest("direct");
        assertThat(new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", resource(), direct, CONFIGURATION).concurrencyEnabled()).isFalse();
    }

    private static YamlSkillManifest manifest(String name)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        return manifest;
    }

    private static YamlSkillManifest.OutputSchemaManifest outputSchema(String type)
    {
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType(type);
        return schema;
    }

    private static ByteArrayResource resource()
    {
        return new ByteArrayResource(new byte[0], "test-skill.yaml");
    }
}
