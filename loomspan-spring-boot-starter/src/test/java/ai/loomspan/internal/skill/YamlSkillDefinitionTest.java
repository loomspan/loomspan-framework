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
    void preservesDeclaredFieldsAcrossDefinitionDefensiveCopies()
    {
        YamlSkillManifest manifest = manifest("llm.copy.skill");
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
        YamlSkillDefinition definition = new YamlSkillDefinition(resource(), manifest, CONFIGURATION);
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

        YamlSkillManifest omitted = manifest("llm.omitted.copy.skill");
        YamlSkillDefinition llm = new YamlSkillDefinition(resource(), omitted, CONFIGURATION);
        assertThat(llm.manifest().declaredFields()).isEmpty();

    }

    @Test
    void preservesEvidenceAndAbsenceAcrossDefinitionDefensiveCopies()
    {
        YamlSkillManifest.OutputSchemaManifest annotated = outputSchema("string");
        annotated.setEvidence("reviewSkill");
        YamlSkillManifest.OutputSchemaManifest unannotated = outputSchema("array");
        unannotated.setItems(outputSchema("string"));
        YamlSkillManifest.OutputSchemaManifest root = outputSchema("object");
        root.setProperties(Map.of("ResultValue", annotated, "notes", unannotated));

        YamlSkillManifest manifest = manifest("llm.evidence.copy.skill");
        manifest.setOutputSchema(root);
        EvidenceContract contract = ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(
                Map.of("ResultValue", "reviewSkill"));
        YamlSkillDefinition definition = new YamlSkillDefinition(resource(), manifest, CONFIGURATION, contract);

        YamlSkillManifest.OutputSchemaManifest first = definition.outputSchema();
        assertThat(first.getProperties().get("ResultValue").getEvidence()).isEqualTo("reviewSkill");
        assertThat(first.getProperties().get("notes").getEvidence()).isNull();
        assertThat(first.getProperties().get("notes").getItems().getEvidence()).isNull();
        first.getProperties().get("ResultValue").setEvidence("mutated");
        assertThat(definition.outputSchema().getProperties().get("ResultValue").getEvidence())
                .isEqualTo("reviewSkill");
    }

    @Test
    void enforcesExecutionConfigurationInvariantByImplementationType()
    {
        YamlSkillManifest llm = manifest("llm.skill");
        YamlSkillDefinition llmDefinition = new YamlSkillDefinition(resource(), llm, CONFIGURATION);
        assertThat(llmDefinition.requireExecutionConfiguration()).isSameAs(CONFIGURATION);

        assertThatThrownBy(() -> new YamlSkillDefinition(resource(), llm, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("execution configuration");
    }



    @Test
    void resolvesPlanningConcurrencyAndPreservesItsDeclaration()
    {
        YamlSkillManifest omitted = manifest("planner.omitted");
        omitted.setPlanningMode(true);
        YamlSkillDefinition omittedDefinition = new YamlSkillDefinition(resource(), omitted, CONFIGURATION);
        assertThat(omittedDefinition.concurrencyEnabled()).isTrue();
        assertThat(omittedDefinition.manifest().isDeclared(YamlSkillManifest.Field.CONCURRENCY)).isFalse();

        YamlSkillManifest enabled = manifest("planner.enabled");
        enabled.setPlanningMode(true);
        enabled.setConcurrency(true);
        YamlSkillDefinition enabledDefinition = new YamlSkillDefinition(resource(), enabled, CONFIGURATION);
        assertThat(enabledDefinition.concurrencyEnabled()).isTrue();
        assertThat(enabledDefinition.manifest().isDeclared(YamlSkillManifest.Field.CONCURRENCY)).isTrue();

        YamlSkillManifest disabled = manifest("planner.disabled");
        disabled.setPlanningMode(true);
        disabled.setConcurrency(false);
        assertThat(new YamlSkillDefinition(resource(), disabled, CONFIGURATION).concurrencyEnabled()).isFalse();

        YamlSkillManifest direct = manifest("direct");
        assertThat(new YamlSkillDefinition(resource(), direct, CONFIGURATION).concurrencyEnabled()).isFalse();
    }

    @Test
    void rejectsProgrammaticInapplicableOrNullConcurrencyDeclarations()
    {
        YamlSkillManifest direct = manifest("direct.invalid");
        direct.setConcurrency(false);
        assertThatThrownBy(() -> new YamlSkillDefinition(resource(), direct, CONFIGURATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planning_mode is explicitly true");

        YamlSkillManifest nullPlanner = manifest("planner.null");
        nullPlanner.setPlanningMode(true);
        nullPlanner.setConcurrency(null);
        assertThatThrownBy(() -> new YamlSkillDefinition(resource(), nullPlanner, CONFIGURATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-null 'concurrency'");


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
