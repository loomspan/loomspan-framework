package ai.loomspan.internal.runtime.planning;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class PlanStructureValidator
{
    static final String PLAN_STRUCTURE = "plan-structure";
    static final String PLAN_DEPENDENCY = "plan-dependency";
    static final String PARALLEL_GROUP_TYPE = "parallel-group-type";
    static final String PARALLEL_GROUP_FORMAT = "parallel-group-format";
    static final String PARALLEL_GROUP_SINGLETON = "parallel-group-singleton";
    static final String PARALLEL_GROUP_NONCONSECUTIVE = "parallel-group-nonconsecutive";
    static final String PARALLEL_GROUP_SAME_UNIT_DEPENDENCY = "parallel-group-same-unit-dependency";

    private static final String GROUP_GRAMMAR = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";
    private static final Pattern GROUP_PATTERN = Pattern.compile(GROUP_GRAMMAR);

    PlanStructureValidationResult validate(JsonNode planTree, Set<String> visibleCapabilityNames)
    {
        Set<String> visibleNames = visibleCapabilityNames == null ? Set.of() : Set.copyOf(visibleCapabilityNames);
        List<PlanStructureIssue> issues = new ArrayList<>();
        if (!(planTree instanceof ObjectNode planNode))
        {
            issues.add(issue(PLAN_STRUCTURE, "Plan root must be an object with a tasks array."));
            return new PlanStructureValidationResult(issues);
        }
        JsonNode tasksNode = planNode.get("tasks");
        if (!(tasksNode instanceof ArrayNode tasks))
        {
            issues.add(issue(PLAN_STRUCTURE, "Plan field 'tasks' must be an array of task objects."));
            return new PlanStructureValidationResult(issues);
        }

        int size = tasks.size();
        String[] taskIds = new String[size];
        String[] groups = new String[size];
        JsonNode[] dependencies = new JsonNode[size];
        Map<String, List<Integer>> idIndexes = new LinkedHashMap<>();
        Map<String, List<Integer>> groupIndexes = new LinkedHashMap<>();
        Set<String> duplicateIds = new LinkedHashSet<>();

        for (int index = 0; index < size; index++)
        {
            JsonNode taskNode = tasks.get(index);
            if (!(taskNode instanceof ObjectNode task))
            {
                issues.add(issue(PLAN_STRUCTURE, "Task at index %d must be an object.".formatted(index)));
                continue;
            }

            JsonNode taskIdNode = task.get("taskId");
            if (taskIdNode == null || !taskIdNode.isTextual() || taskIdNode.textValue().isBlank())
            {
                issues.add(issue(PLAN_STRUCTURE,
                        "Task at index %d field 'taskId' must be a nonblank string.".formatted(index)));
            }
            else
            {
                taskIds[index] = taskIdNode.textValue();
                List<Integer> indexes = idIndexes.computeIfAbsent(taskIds[index], ignored -> new ArrayList<>());
                if (!indexes.isEmpty())
                {
                    duplicateIds.add(taskIds[index]);
                    issues.add(issue(PLAN_STRUCTURE,
                            "Task at index %d ('%s') duplicates taskId '%s'; task IDs must be unique."
                                    .formatted(index, taskIds[index], taskIds[index])));
                }
                indexes.add(index);
            }

            String label = taskLabel(index, taskIds[index]);
            JsonNode capabilityNode = task.get("capabilityName");
            if (capabilityNode == null || !capabilityNode.isTextual() || capabilityNode.textValue().isBlank())
            {
                issues.add(issue(PLAN_STRUCTURE,
                        "%s field 'capabilityName' must be a nonblank string naming an exact visible capability."
                                .formatted(label)));
            }
            else if (!visibleNames.contains(capabilityNode.textValue()))
            {
                issues.add(issue(PLAN_STRUCTURE,
                        "%s field 'capabilityName' value '%s' is not an exact visible capability name."
                                .formatted(label, capabilityNode.textValue())));
            }

            JsonNode groupNode = task.get("parallelGroup");
            if (groupNode != null && !groupNode.isNull())
            {
                if (!groupNode.isTextual())
                {
                    issues.add(issue(PARALLEL_GROUP_TYPE,
                            "%s field 'parallelGroup' must be null, omitted, or a string matching %s."
                                    .formatted(label, GROUP_GRAMMAR)));
                }
                else if (!GROUP_PATTERN.matcher(groupNode.textValue()).matches())
                {
                    issues.add(issue(PARALLEL_GROUP_FORMAT,
                            "%s field 'parallelGroup' value '%s' must match %s."
                                    .formatted(label, groupNode.textValue(), GROUP_GRAMMAR)));
                }
                else
                {
                    groups[index] = groupNode.textValue();
                    groupIndexes.computeIfAbsent(groups[index], ignored -> new ArrayList<>()).add(index);
                }
            }

            JsonNode dependsOn = task.get("dependsOn");
            dependencies[index] = dependsOn;
            if (!(dependsOn instanceof ArrayNode dependencyArray))
            {
                issues.add(issue(PLAN_DEPENDENCY,
                        "%s field 'dependsOn' must be an array of task-ID strings.".formatted(label)));
            }
            else
            {
                for (int dependencyIndex = 0; dependencyIndex < dependencyArray.size(); dependencyIndex++)
                {
                    JsonNode reference = dependencyArray.get(dependencyIndex);
                    if (reference == null || !reference.isTextual() || reference.textValue().isBlank())
                    {
                        issues.add(issue(PLAN_DEPENDENCY,
                                "%s field 'dependsOn[%d]' must be a nonblank task-ID string."
                                        .formatted(label, dependencyIndex)));
                    }
                }
            }
        }

        idIndexes.forEach((id, indexes) ->
        {
            if (indexes.size() > 1)
            {
                duplicateIds.add(id);
            }
        });

        groupIndexes.forEach((group, indexes) ->
        {
            if (indexes.size() == 1)
            {
                int index = indexes.getFirst();
                issues.add(issue(PARALLEL_GROUP_SINGLETON,
                        "%s field 'parallelGroup' value '%s' is used by only one task; a group must contain at least two consecutive tasks."
                                .formatted(taskLabel(index, taskIds[index]), group)));
                return;
            }
            for (int occurrence = 1; occurrence < indexes.size(); occurrence++)
            {
                if (indexes.get(occurrence) != indexes.get(occurrence - 1) + 1)
                {
                    int index = indexes.get(occurrence);
                    issues.add(issue(PARALLEL_GROUP_NONCONSECUTIVE,
                            "%s field 'parallelGroup' value '%s' reuses a group outside its first consecutive run."
                                    .formatted(taskLabel(index, taskIds[index]), group)));
                    break;
                }
            }
        });

        int[] units = executionUnits(groups);
        Map<String, Integer> uniqueIdIndexes = new HashMap<>();
        idIndexes.forEach((id, indexes) ->
        {
            if (indexes.size() == 1)
            {
                uniqueIdIndexes.put(id, indexes.getFirst());
            }
        });

        for (int index = 0; index < size; index++)
        {
            if (!(dependencies[index] instanceof ArrayNode dependencyArray))
            {
                continue;
            }
            for (int dependencyIndex = 0; dependencyIndex < dependencyArray.size(); dependencyIndex++)
            {
                JsonNode referenceNode = dependencyArray.get(dependencyIndex);
                if (referenceNode == null || !referenceNode.isTextual() || referenceNode.textValue().isBlank())
                {
                    continue;
                }
                String reference = referenceNode.textValue();
                if (duplicateIds.contains(reference))
                {
                    continue;
                }
                Integer referencedIndex = uniqueIdIndexes.get(reference);
                String label = taskLabel(index, taskIds[index]);
                if (referencedIndex == null)
                {
                    issues.add(issue(PLAN_DEPENDENCY,
                            "%s field 'dependsOn[%d]' references unknown taskId '%s'; dependencies must name tasks in an earlier execution unit."
                                    .formatted(label, dependencyIndex, reference)));
                }
                else if (referencedIndex == index)
                {
                    issues.add(issue(PLAN_DEPENDENCY,
                            "%s field 'dependsOn[%d]' references itself by taskId '%s'; dependencies must name tasks in an earlier execution unit."
                                    .formatted(label, dependencyIndex, reference)));
                }
                else if (units[referencedIndex] == units[index]
                        && groups[index] != null && Objects.equals(groups[index], groups[referencedIndex]))
                {
                    issues.add(issue(PARALLEL_GROUP_SAME_UNIT_DEPENDENCY,
                            "%s field 'dependsOn[%d]' references same-unit task '%s' in parallelGroup '%s'; group members must be independent."
                                    .formatted(label, dependencyIndex, reference, groups[index])));
                }
                else if (units[referencedIndex] >= units[index])
                {
                    issues.add(issue(PLAN_DEPENDENCY,
                            "%s field 'dependsOn[%d]' references taskId '%s' outside an earlier execution unit."
                                    .formatted(label, dependencyIndex, reference)));
                }
            }
        }

        return new PlanStructureValidationResult(issues);
    }

    private static int[] executionUnits(String[] groups)
    {
        int[] units = new int[groups.length];
        int unit = -1;
        for (int index = 0; index < groups.length; index++)
        {
            if (index == 0 || groups[index] == null || !Objects.equals(groups[index], groups[index - 1]))
            {
                unit++;
            }
            units[index] = unit;
        }
        return units;
    }

    private static String taskLabel(int index, String taskId)
    {
        return taskId == null ? "Task at index %d".formatted(index) : "Task at index %d ('%s')".formatted(index, taskId);
    }

    private static PlanStructureIssue issue(String code, String message)
    {
        return new PlanStructureIssue(code, message);
    }
}
