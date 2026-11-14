package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.runtime.input.ChildInputBindingAssembler;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

/** Model ownership projection independent of the complete canonical output contract. */
public final class OutputBindingProjection {
    private final List<ChildInputBinding> bindings;
    private final CanonicalSchema schema;
    private final boolean modelContributionRequired;
    public OutputBindingProjection(CanonicalSchema original, List<ChildInputBinding> bindings) {
        this.bindings = List.copyOf(bindings);
        var paths = bindings.stream().map(b -> b.destination().tokens()).toList();
        schema = new StandardSchemaCompiler().compile(project(original.tree(), paths),
                StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.GENERATED);
        modelContributionRequired = hasSpace(original.tree(), paths);
    }
    public CanonicalSchema schema() { return schema; }
    public List<ChildInputBinding> bindings() { return bindings; }
    public boolean modelContributionRequired() { return modelContributionRequired; }
    public String guidance() { return "Framework supplies these output destinations; omit them entirely, including equal or null values: "
            + bindings.stream().map(b -> b.destination().pointer()).toList() + ". Return only the model-owned output contribution."; }
    public List<String> validateModelContribution(Map<String,Object> contribution) {
        List<String> issues = new ArrayList<>();
        for (var binding : bindings) {
            try { ChildInputBindingAssembler.checkDestination(contribution, binding.destination()); }
            catch (IllegalArgumentException ex) { issues.add(ex.getMessage()); }
        }
        return List.copyOf(issues);
    }
    private static boolean hasSpace(JsonNode node, List<List<String>> paths) {
        if (!node.path("additionalProperties").isBoolean() || node.path("additionalProperties").booleanValue()) return true;
        var properties = node.path("properties");
        for (String name : properties.propertyNames()) {
            if (properties.get(name).isBoolean() && !properties.get(name).booleanValue()) continue;
            var selected = paths.stream().filter(p -> p.getFirst().equals(name)).toList();
            if (selected.isEmpty()) return true;
            if (selected.stream().anyMatch(p -> p.size()==1)) continue;
            if (hasSpace(properties.get(name), selected.stream().map(p -> p.subList(1,p.size())).toList())) return true;
        }
        return false;
    }
    private static ObjectNode project(JsonNode node, List<List<String>> paths) {
        ObjectNode copy = (ObjectNode) node.deepCopy();
        ObjectNode properties = copy.has("properties") ? (ObjectNode) copy.get("properties") : copy.putObject("properties");
        Set<String> required = new LinkedHashSet<>();
        copy.path("required").forEach(value -> required.add(value.asText()));
        Map<String,List<List<String>>> grouped = new LinkedHashMap<>();
        for (var path : paths) grouped.computeIfAbsent(path.getFirst(), ignored -> new ArrayList<>()).add(path);
        for (var group : grouped.entrySet()) {
            String key = group.getKey();
            if (group.getValue().stream().anyMatch(p -> p.size()==1)) { properties.remove(key); required.remove(key); continue; }
            JsonNode child = properties.get(key);
            if (child == null) child = copy.objectNode().put("type", "object");
            var projected = project(child, group.getValue().stream().map(p -> p.subList(1,p.size())).toList());
            projected.put("type", "object");
            properties.set(key, projected);
            if (projected.path("required").isEmpty()) required.remove(key); else required.add(key);
        }
        var array = copy.putArray("required"); required.forEach(array::add);
        if (!paths.isEmpty()) copy.put("type", "object");
        return copy;
    }
}
