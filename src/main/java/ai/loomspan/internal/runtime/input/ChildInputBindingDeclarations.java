package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.skill.AllowedSkillConstraint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks declarations using only schema information actually available in a generation. */
public final class ChildInputBindingDeclarations {
    private ChildInputBindingDeclarations() { }
    public static List<String> validate(SkillInputContract parent, List<AllowedSkillConstraint> children,
            Map<String, SkillInputContract> receivers) {
        List<String> issues = new ArrayList<>();
        Map<String, AllowedSkillConstraint> names = new LinkedHashMap<>();
        children.forEach(child -> names.put(child.name(), child));
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (var child : children) {
            Set<String> producers = new HashSet<>();
            graph.put(child.name(), producers);
            List<ObjectFieldPath> destinations = new ArrayList<>();
            for (var binding : child.inputBindings()) {
                String label = "Invalid input binding declaration for child '" + child.name() + "' destination " + binding.destination().pointer() + ": ";
                for (var previous : destinations)
                    if (previous.isAncestorOf(binding.destination()) || binding.destination().isAncestorOf(previous))
                        issues.add(label + "duplicate or overlapping destination");
                destinations.add(binding.destination());
                SkillInputContract receiver = receivers.get(child.name());
                if (receiver != null) checkPath(receiver.schema(), binding.destination(), label, issues);
                if (binding.sourceKind() == ChildInputBinding.SourceKind.INPUT) {
                    if (parent != null) checkPath(parent.schema(), binding.sourcePath(), label + "parent source ", issues);
                } else {
                    producers.add(binding.skill());
                    var producer = names.get(binding.skill());
                    if (producer == null) issues.add(label + "unknown direct producer '" + binding.skill() + "'");
                    else if (!Integer.valueOf(0).equals(child.maxTasks())
                            && (Integer.valueOf(0).equals(producer.maxTasks()) || producer.effectiveMinTasks() > 1))
                        issues.add(label + "producer cannot have exactly one task '" + binding.skill() + "'");
                    if (child.name().equals(binding.skill())) issues.add(label + "self dependency");
                }
            }
        }
        for (String child : graph.keySet())
            if (cycle(child, graph, new HashSet<>(), new HashSet<>())) {
                issues.add("Invalid input binding declaration: cyclic child-result dependencies involving '" + child + "'"); break;
            }
        return List.copyOf(issues);
    }
    private static boolean cycle(String name, Map<String, Set<String>> graph, Set<String> visiting, Set<String> done) {
        if (done.contains(name)) return false;
        if (!visiting.add(name)) return true;
        for (String producer : graph.getOrDefault(name, Set.of())) if (cycle(producer, graph, visiting, done)) return true;
        visiting.remove(name); done.add(name); return false;
    }
    private static void checkPath(SkillInputSchemaNode node, ObjectFieldPath path, String label, List<String> issues) {
        for (String token : path.tokens()) {
            if (node.isUnconstrained()) return;
            if (!node.isObject()) { issues.add(label + "path traverses a non-object schema at " + path.pointer()); return; }
            SkillInputSchemaNode next = node.properties().get(token);
            if (next == null) {
                if (!node.allowsAdditionalProperties()) { issues.add(label + "path selects undeclared field in closed object " + path.pointer()); return; }
                next = node.additionalPropertiesSchema();
                if (next == null) return;
            }
            node = next;
        }
    }
}
