package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputSchemaPromptAugmentorTest
{
    @Test
    void augmentsPromptWithCompleteRecursiveEffectiveContractInDeclaredOrder()
    {
        YamlSkillManifest.OutputSchemaManifest root = node("object");
        root.setAdditionalProperties(false);

        YamlSkillManifest.OutputSchemaManifest transport = node("object");
        transport.setAdditionalProperties(true);
        YamlSkillManifest.OutputSchemaManifest outbound = node("object");
        outbound.setNullable(true);
        outbound.setAdditionalProperties(true);
        outbound.setDescription("Selected outbound option from the digest.");
        YamlSkillManifest.OutputSchemaManifest mode = node("string");
        mode.setEnumValues(List.of("train, express", "flight\"overnight"));
        LinkedHashMap<String, YamlSkillManifest.OutputSchemaManifest> transportProperties = new LinkedHashMap<>();
        transportProperties.put("outbound", outbound);
        transportProperties.put("mode", mode);
        transport.setProperties(transportProperties);
        transport.setRequired(List.of("outbound"));

        YamlSkillManifest.OutputSchemaManifest questions = node("array");
        questions.setItems(node("string"));
        questions.setDescription("Questions still needing answers.");

        YamlSkillManifest.OutputSchemaManifest rows = node("array");
        YamlSkillManifest.OutputSchemaManifest row = node("object");
        row.setAdditionalProperties(false);
        YamlSkillManifest.OutputSchemaManifest date = node("string");
        date.setFormat("date");
        date.setEvidence("SENTINEL_EVIDENCE");
        row.setProperties(new LinkedHashMap<>(java.util.Map.of("date", date)));
        row.setRequired(List.of("date"));
        rows.setItems(row);

        YamlSkillManifest.OutputSchemaManifest empty = node("object");
        empty.setAdditionalProperties(false);

        LinkedHashMap<String, YamlSkillManifest.OutputSchemaManifest> properties = new LinkedHashMap<>();
        properties.put("transport", transport);
        properties.put("questions", questions);
        properties.put("rows", rows);
        properties.put("empty.object[]", empty);
        root.setProperties(properties);
        root.setRequired(List.of("transport", "rows"));

        Prompt augmented = new OutputSchemaPromptAugmentor().augment(
                new Prompt(List.of(new SystemMessage("ORIGINAL SYSTEM"))), root);

        assertThat(augmented.getSystemMessage().getText()).isEqualTo("""
                ORIGINAL SYSTEM

                Return JSON only.
                Do not include markdown fences, commentary, or prose.
                Use the configured field names exactly.
                Omit unknown fields unless they are explicitly allowed.

                Property semantics:
                - required: the property must be present
                - optional: omit the property when its value is unknown
                - nullable: JSON null is allowed
                - non-null: JSON null is not allowed when the property is present

                Output contract:
                $ — object, non-null, additionalProperties=false
                  $.transport — object, required, non-null, additionalProperties=true
                    $.transport.outbound — object, required, nullable, additionalProperties=true - Selected outbound option from the digest.
                    $.transport.mode — string, optional, non-null, enum=["train, express", "flight\\\"overnight"]
                  $.questions — array, optional, non-null - Questions still needing answers.
                    $.questions[] — string, non-null
                  $.rows — array, required, non-null
                    $.rows[] — object, non-null, additionalProperties=false
                      $.rows[].date — string, required, non-null, format=date
                  $["empty.object[]"] — object, optional, non-null, additionalProperties=false""");
        assertThat(augmented.getSystemMessage().getText())
                .doesNotContain("SENTINEL_EVIDENCE")
                .doesNotContain("output_schema_max_retries")
                .doesNotContain("mapping")
                .doesNotContain("response format");
    }

    private YamlSkillManifest.OutputSchemaManifest node(String type)
    {
        YamlSkillManifest.OutputSchemaManifest node = new YamlSkillManifest.OutputSchemaManifest();
        node.setType(type);
        return node;
    }
}
