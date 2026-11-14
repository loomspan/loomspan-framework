package ai.loomspan.internal.skill;

import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Real dependency-validator entry; catalog parsing/publication is a separate integration check. */
class SkillDependencyBindingValidationTest {
    private static YamlSkillDefinition definition(String name, String schema, String destination, String source, int maxTasks) {
        var manifest = new YamlSkillManifest();
        manifest.setName(name); manifest.setDescription(name); manifest.setModel("model");
        manifest.setOutputSchema(LoomspanJacksonCodecs.defaults().schemaTree().readTree(schema));
        if (destination != null) {
            manifest.setAllowedSkills(List.of(new YamlSkillManifest.AllowedSkillManifest("default-producer", null, maxTasks, null)));
            var binding = new YamlSkillManifest.InputBindingManifest();
            binding.setFrom("child_result"); binding.setSkill("default-producer"); binding.setPath(source);
            manifest.setOutputBindings(Map.of(destination, binding));
        }
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default", new ByteArrayResource(new byte[0], name), manifest,
                new EffectiveSkillExecutionConfiguration("model", "connection", ai.loomspan.autoconfigure.AiDriver.OPENAI, "provider-model", null));
    }
    private static SkillDependencyValidator.CheckedSet check(String sourceSchema, String targetSchema, String source, String destination, int maxTasks) {
        return new SkillDependencyValidator(new SkillInputContractResolver(), List.of()).check("default",
                new YamlSkillCatalog.CheckedDocuments(List.of(definition("parent", targetSchema, destination, source, maxTasks),
                        definition("producer", sourceSchema, null, null, 1)), List.of()),
                NamespaceVisibility.PRIVATE, Map.of(), List.of(), Map.of());
    }
    private static void valid(SkillDependencyValidator.CheckedSet checked) { assertThat(checked.issues()).isEmpty(); }
    private static void invalid(SkillDependencyValidator.CheckedSet checked, String message) {
        assertThat(checked.issues()).anySatisfy(issue -> {
            assertThat(issue.fieldPath()).isEqualTo("output_bindings");
            assertThat(issue.message()).contains(message);
        });
    }

    @Test void actualCallerUsesNumericValueEnumOverlap() {
        for (String[] pair : new String[][]{{"1", "1.0"}, {"1", "1e0"}, {"1.0", "1.00"},
                {"900719925474099300000000000000000001", "900719925474099300000000000000000001.0"}})
            valid(check(number(pair[0], "value"), number(pair[1], "result"), "/value", "/result", 1));
        invalid(check(number("1.0000000000000000000000000001", "value"), number("1.0000000000000000000000000002", "result"), "/value", "/result", 1), "enums");
        invalid(check(number("1", "value"), number("2", "result"), "/value", "/result", 1), "enums");
        invalid(check(number("1", "value"), number("\"1\"", "result"), "/value", "/result", 1), "enums");
    }

    @Test void actualCallerPreservesRootAndNestedOmittedOpenness() {
        String known = "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"additionalProperties\":false}";
        for (String openness : new String[]{"", ",\"additionalProperties\":true", ",\"additionalProperties\":false"}) {
            for (boolean nested : new boolean[]{false, true}) {
                String object = "{\"type\":\"object\"" + openness + "}";
                String root = nested ? "{\"type\":\"object\",\"properties\":{\"nested\":" + object + "},\"additionalProperties\":false}" : object;
                String path = nested ? "/nested/unknown" : "/unknown";
                if (openness.contains("false")) {
                    invalid(check(root, known, path, "/value", 1), "closed object");
                    invalid(check(known, root, "/value", path, 1), "closed object");
                } else {
                    valid(check(root, known, path, "/value", 1));
                    valid(check(known, root, "/value", path, 1));
                }
            }
        }
    }

    @Test void actualCallerRetainsProducerCountAndExactDestinationChecks() {
        String known = number("1", "value");
        invalid(check(known, known, "/value", "/value", 0), "exactly one");
        invalid(check(known, known, "/value", "/VALUE", 1), "closed object");
        invalid(check(known, known, "/VALUE", "/value", 1), "closed object");
        valid(check(known, known, "/value", "/value", 2));
    }

    private static String number(String value, String property) {
        return "{\"type\":\"object\",\"properties\":{\"" + property + "\":{\"type\":\"number\",\"enum\":[" + value + "]}},\"additionalProperties\":false}";
    }
}
