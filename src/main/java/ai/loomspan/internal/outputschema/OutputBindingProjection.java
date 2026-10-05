package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.skill.YamlSkillManifest.OutputSchemaManifest;
import java.util.*;

/** Native output ownership projection, preserving output nullability and constraints. */
public final class OutputBindingProjection {
    private final List<ChildInputBinding> bindings;
    private final OutputSchemaManifest schema;
    private final boolean modelContributionRequired;
    public OutputBindingProjection(OutputSchemaManifest original, List<ChildInputBinding> bindings) {
        this.bindings = List.copyOf(bindings);
        var paths = bindings.stream().map(b -> b.destination().tokens()).toList();
        schema = project(original, paths);
        modelContributionRequired = hasSpace(original, paths);
    }
    public OutputSchemaManifest schema() { return schema; }
    public List<ChildInputBinding> bindings() { return bindings; }
    public boolean modelContributionRequired() { return modelContributionRequired; }
    public String guidance() { return "Framework supplies these output destinations; omit them entirely, including equal or null values: "
            + bindings.stream().map(b -> b.destination().pointer()).toList() + ". Return only the model-owned output contribution."; }
    public List<String> validateModelContribution(Map<String,Object> contribution) {
        List<String> issues = new ArrayList<>();
        for (var binding : bindings) {
            Object current = contribution;
            var tokens = binding.destination().tokens();
            for (int i = 0; i < tokens.size(); i++) {
                if (!(current instanceof Map<?,?> object)) {
                    issues.add("binding_model_override: Nonobject ancestor of " + binding.destination().pointer()); break;
                }
                String token = tokens.get(i);
                var matches = object.keySet().stream().filter(key -> key instanceof String s && OutputSchemaValidator.propertyNamesMatch(s, token)).toList();
                if (matches.isEmpty()) break;
                if (matches.size() > 1 || i == tokens.size()-1) {
                    issues.add("binding_model_override: Model supplied bound destination " + binding.destination().pointer()); break;
                }
                current = object.get(matches.getFirst());
            }
        }
        return List.copyOf(issues);
    }
    private static boolean hasSpace(OutputSchemaManifest node, List<List<String>> paths) {
        if (Boolean.TRUE.equals(node.getAdditionalProperties())) return true;
        for (var property : node.getProperties().entrySet()) {
            var selected = paths.stream().filter(p -> OutputSchemaValidator.propertyNamesMatch(p.getFirst(), property.getKey())).toList();
            if (selected.isEmpty()) return true;
            if (selected.stream().anyMatch(p -> p.size()==1)) continue;
            if (hasSpace(property.getValue(), selected.stream().map(p -> p.subList(1,p.size())).toList())) return true;
        }
        return false;
    }
    private static OutputSchemaManifest project(OutputSchemaManifest node, List<List<String>> paths) {
        OutputSchemaManifest copy = copy(node);
        Map<String,OutputSchemaManifest> properties = new LinkedHashMap<>(node.getProperties());
        Set<String> required = new LinkedHashSet<>(node.getRequired());
        Map<String,List<List<String>>> grouped = new LinkedHashMap<>();
        for (var path : paths) grouped.computeIfAbsent(path.getFirst(), ignored -> new ArrayList<>()).add(path);
        for (var group : grouped.entrySet()) {
            String key = properties.keySet().stream().filter(k -> OutputSchemaValidator.propertyNamesMatch(k, group.getKey())).findFirst().orElse(group.getKey());
            if (group.getValue().stream().anyMatch(p -> p.size()==1)) { properties.remove(key); required.remove(key); continue; }
            OutputSchemaManifest child = properties.get(key);
            if (child == null) { child = new OutputSchemaManifest(); child.setType("object"); child.setAdditionalProperties(true); }
            var projected = project(child, group.getValue().stream().map(p -> p.subList(1,p.size())).toList());
            // Insertion requires an object and makes required unbound siblings applicable.
            projected.setNullable(false);
            properties.put(key, projected);
            if (projected.getRequired().isEmpty()) required.remove(key); else required.add(key);
        }
        copy.setProperties(properties); copy.setRequired(List.copyOf(required));
        if (!paths.isEmpty()) copy.setNullable(false);
        return copy;
    }
    public static OutputSchemaManifest copy(OutputSchemaManifest node) {
        OutputSchemaManifest copy = new OutputSchemaManifest();
        copy.setType(node.getType()); copy.setNullable(node.getNullable()); copy.setAdditionalProperties(node.getAdditionalProperties());
        copy.setRequired(node.getRequired()); copy.setEnumValues(node.getEnumValues()); copy.setDescription(node.getDescription());
        copy.setFormat(node.getFormat()); copy.setEvidence(node.getEvidence());
        Map<String,OutputSchemaManifest> properties = new LinkedHashMap<>();
        node.getProperties().forEach((key,value)->properties.put(key,copy(value))); copy.setProperties(properties);
        if (node.getItems()!=null) copy.setItems(copy(node.getItems()));
        return copy;
    }
}

