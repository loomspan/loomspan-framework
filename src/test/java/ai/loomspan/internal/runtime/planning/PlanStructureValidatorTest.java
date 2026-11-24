package ai.loomspan.internal.runtime.planning;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanStructureValidatorTest
{
    @Test void validatesExactIdentitiesWithoutMutatingAcceptedPlan() {
        String raw = "{\"capabilityName\":\"orders:root\",\"tasks\":[{\"taskId\":\"one\",\"capabilityName\":\"order-support:find-order\",\"dependsOn\":[]}]}";
        var tree = mapper.readTree(raw);
        var before = tree.deepCopy();
        assertThat(validator.validate(tree, Set.of("order-support:find-order"), "orders:root").issues()).isEmpty();
        assertThat(tree).isEqualTo(before);
        for (String selected : List.of("order-support--find-order", "find-order", "Order-support:find-order",
                "orders:find_order", "other:find-order", "orders:find-order ", "order-support:" + "a".repeat(50))) {
            var invalid = mapper.readTree(raw.replace("order-support:find-order", selected));
            assertThat(validator.validate(invalid, Set.of("order-support:find-order"), "orders:root").issues()).isNotEmpty();
        }
        for (String boundary : List.of("order-support:find-order", "order-support:" + "a".repeat(49)))
            assertThat(validator.validate(mapper.readTree(raw.replace("order-support:find-order", boundary)),
                    Set.of(boundary), "orders:root").issues()).isEmpty();
    }
    private final ObjectMapper mapper = LoomspanJacksonCodecs.defaults().planningJson();
    private final PlanStructureValidator validator = new PlanStructureValidator();

    @Test
    void acceptsOmittedNullAndExactValidGroupsWithEarlierUnitDependencies() throws Exception
    {
        String sixtyFourCharacters = "a".repeat(64);
        JsonNode tree = plan(
                task("t0", "default:tool", null, "[]", false),
                task("t1", "default:tool", "A", "[]", true),
                task("t2", "default:tool", "A", "[]", true),
                task("t3", "default:tool", "a-b_c", "[\"t1\",\"t2\"]", true),
                task("t4", "default:tool", "a-b_c", "[\"t0\"]", true),
                task("t5", "default:tool", sixtyFourCharacters, "[\"t3\"]", true),
                task("t6", "default:tool", sixtyFourCharacters, "[\"t4\"]", true));

        assertThat(validator.validate(tree, Set.of("default:tool"), null).issues()).isEmpty();
    }

    @Test
    void reportsGroupTypeAndFormatWithoutSecondaryGroupCascades() throws Exception
    {
        JsonNode tree = mapper.readTree("""
                {"tasks":[
                  {"taskId":"t0","capabilityName":"default:tool","dependsOn":[],"parallelGroup":7},
                  {"taskId":"t1","capabilityName":"default:tool","dependsOn":[],"parallelGroup":""},
                  {"taskId":"t2","capabilityName":"default:tool","dependsOn":[],"parallelGroup":"has space"},
                  {"taskId":"t3","capabilityName":"default:tool","dependsOn":[],"parallelGroup":"%s"}
                ]}
                """.formatted("a".repeat(65)));

        PlanStructureValidationResult result = validator.validate(tree, Set.of("default:tool"), null);

        assertThat(result.issues()).extracting(PlanStructureIssue::code)
                .containsExactly("parallel-group-type", "parallel-group-format",
                        "parallel-group-format", "parallel-group-format");
        assertThat(result.issues()).noneMatch(issue -> issue.code().equals("parallel-group-singleton")
                || issue.code().equals("parallel-group-nonconsecutive"));
        assertThat(result.issues().getFirst().message())
                .isEqualTo("Task at index 0 ('t0') field 'parallelGroup' must be null, omitted, or a string matching ^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$.");
    }

    @Test
    void reportsSingletonNonconsecutiveAndSameUnitDependencyDeterministically() throws Exception
    {
        JsonNode tree = plan(
                task("t0", "default:tool", "reused", "[]", true),
                task("t1", "default:tool", "pair", "[]", true),
                task("t2", "default:tool", "pair", "[\"t1\"]", true),
                task("t3", "default:tool", "reused", "[]", true),
                task("t4", "default:tool", "single", "[]", true));

        PlanStructureValidationResult result = validator.validate(tree, Set.of("default:tool"), null);

        assertThat(result.issues()).extracting(PlanStructureIssue::code)
                .containsExactly("parallel-group-nonconsecutive", "parallel-group-singleton",
                        "parallel-group-same-unit-dependency");
        assertThat(result.retryFeedback()).contains(
                "[parallel-group-nonconsecutive] Task at index 3 ('t3')",
                "[parallel-group-same-unit-dependency] Task at index 2 ('t2')");
    }

    @Test
    void reportsTaskIdsAndExactVisibleCapabilityBindingsWithoutDuplicateTopologyCascades() throws Exception
    {
        JsonNode tree = mapper.readTree("""
                {"tasks":[
                  {"taskId":"dup","capabilityName":"default:other","dependsOn":[]},
                  {"taskId":"dup","capabilityName":"default:tool","dependsOn":["dup"]},
                  {"taskId":" ","capabilityName":"","dependsOn":[]},
                  {"taskId":"t3","dependsOn":[]},
                  {"taskId":"t4","capabilityName":4,"dependsOn":[]}
                ]}
                """);

        PlanStructureValidationResult result = validator.validate(tree, Set.of("default:tool"), null);

        assertThat(result.issues()).extracting(PlanStructureIssue::code)
                .containsOnly("plan-structure");
        assertThat(result.issues()).extracting(PlanStructureIssue::message)
                .contains("Task at index 0 ('dup') field 'capabilityName' value 'default:other' is not an exact visible capability name.",
                        "Task at index 1 ('dup') duplicates taskId 'dup'; task IDs must be unique.");
        assertThat(result.issues()).noneMatch(issue -> issue.message().contains("dependsOn[0]"));
    }

    @Test
    void reportsMalformedUnknownSelfForwardAndCyclicDependencies() throws Exception
    {
        JsonNode tree = mapper.readTree("""
                {"tasks":[
                  {"taskId":"t0","capabilityName":"default:tool","dependsOn":"t1"},
                  {"taskId":"t1","capabilityName":"default:tool","dependsOn":[4,"", "missing", "t1", "t2"]},
                  {"taskId":"t2","capabilityName":"default:tool","dependsOn":["t1", "t3"]},
                  {"taskId":"t3","capabilityName":"default:tool","dependsOn":["t2"]}
                ]}
                """);

        PlanStructureValidationResult result = validator.validate(tree, Set.of("default:tool"), null);

        assertThat(result.issues()).extracting(PlanStructureIssue::code)
                .containsOnly("plan-dependency");
        assertThat(result.issues()).hasSize(7);
        assertThat(result.issues()).noneMatch(issue -> issue.message().contains("unknown taskId '4'"));
        assertThat(result.issues()).extracting(PlanStructureIssue::message)
                .contains("Task at index 1 ('t1') field 'dependsOn[2]' references unknown taskId 'missing'; dependencies must name tasks in an earlier execution unit.",
                        "Task at index 1 ('t1') field 'dependsOn[3]' references itself by taskId 't1'; dependencies must name tasks in an earlier execution unit.",
                        "Task at index 2 ('t2') field 'dependsOn[1]' references taskId 't3' outside an earlier execution unit.");
    }

    private JsonNode plan(String... tasks) throws Exception
    {
        return mapper.readTree("{\"tasks\":[" + String.join(",", tasks) + "]}");
    }

    private static String task(String id, String capability, String group, String dependencies, boolean includeGroup)
    {
        String groupField = includeGroup
                ? ",\"parallelGroup\":" + (group == null ? "null" : "\"" + group + "\"")
                : "";
        return "{\"taskId\":\"%s\",\"capabilityName\":\"%s\",\"dependsOn\":%s%s}"
                .formatted(id, capability, dependencies, groupField);
    }
}
