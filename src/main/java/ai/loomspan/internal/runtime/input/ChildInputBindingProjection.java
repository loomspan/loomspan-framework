package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.StandardSchemaCompiler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

/** One canonical authority for advertised model input and reserved-path checks. */
public final class ChildInputBindingProjection {
    private final List<ChildInputBinding> bindings;
    private final SkillInputContract argumentContract;
    private final DispatchEligibility dispatchEligibility;
    public ChildInputBindingProjection(SkillInputContract receivingContract, List<ChildInputBinding> bindings) {
        this.bindings = List.copyOf(bindings);
        this.argumentContract = bindings.isEmpty() ? receivingContract : new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new StandardSchemaCompiler().compile(project(receivingContract.schema().tree(),
                        bindings.stream().map(b -> b.destination().tokens()).toList()),
                        StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED));
        this.dispatchEligibility = proveDispatch(receivingContract);
    }
    public SkillInputContract argumentContract() { return argumentContract; }
    public List<ChildInputBinding> bindings() { return bindings; }
    public DispatchEligibility dispatchEligibility() { return dispatchEligibility; }
    public record DispatchEligibility(boolean eligible, String reason) {}
    private DispatchEligibility proveDispatch(SkillInputContract receiving) {
        var root = receiving.schema().tree();
        if (!supportedTree(root) || bindings.stream().anyMatch(b -> b.destination().tokens().size() != 1))
            return new DispatchEligibility(false, "unsupported_or_ambiguous_shape");
        if (receiving.isGeneric() || !root.path("additionalProperties").isBoolean()
                || root.path("additionalProperties").booleanValue())
            return new DispatchEligibility(false, "open_or_unknown_contract");
        var projected = argumentContract.schema().tree();
        for (var property : projected.path("properties"))
            if (!property.isBoolean() || property.booleanValue())
                return new DispatchEligibility(false, "unbound_input_remains");
        if (!projected.path("required").isEmpty()) return new DispatchEligibility(false, "unbound_input_remains");
        if (!new SkillInputValidator().validate(Map.of(), argumentContract).valid() || !validateModelArguments(Map.of()).isEmpty())
            return new DispatchEligibility(false, "unsupported_or_ambiguous_shape");
        return new DispatchEligibility(true, "eligible");
    }
    private static boolean supportedTree(JsonNode node) {
        if (node.isBoolean()) return !node.booleanValue();
        if (!node.has("type") || node.has("x-loomspan-attachment") || node.path("x-loomspan-runtime-ref-capable").asBoolean(false)) return false;
        for (var child : node.path("properties")) if (!supportedTree(child)) return false;
        return (!node.has("items") || supportedTree(node.get("items")))
                && (!node.path("additionalProperties").isObject() || supportedTree(node.get("additionalProperties")));
    }
    public String inputSchema() { return argumentContract.schema().providerTree().toString(); }
    public List<String> validateModelArguments(Map<String, Object> arguments) {
        List<String> issues = new ArrayList<>();
        for (var binding : bindings) {
            try { ChildInputBindingAssembler.checkDestination(arguments, binding.destination()); }
            catch (IllegalArgumentException ex) { issues.add(ex.getMessage()); }
        }
        return List.copyOf(issues);
    }
    private static ObjectNode project(JsonNode node, List<List<String>> paths) {
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.put("type", "object");
        ObjectNode properties = copy.has("properties") ? (ObjectNode) copy.get("properties") : copy.putObject("properties");
        Set<String> required = new LinkedHashSet<>();
        copy.path("required").forEach(value -> required.add(value.asText()));
        Map<String, List<List<String>>> groups = new LinkedHashMap<>();
        paths.forEach(path -> groups.computeIfAbsent(path.getFirst(), key -> new ArrayList<>()).add(path));
        for (var group : groups.entrySet()) {
            String key = group.getKey();
            if (group.getValue().stream().anyMatch(path -> path.size() == 1)) {
                properties.put(key, false); required.remove(key); continue;
            }
            JsonNode child = properties.get(key);
            if (child == null) child = copy.get("additionalProperties");
            if (child == null || !child.isObject()) child = copy.objectNode();
            var projected = project(child, group.getValue().stream().map(path -> path.subList(1, path.size())).toList());
            properties.set(key, projected);
            if (projected.path("required").isEmpty()) required.remove(key); else required.add(key);
        }
        var array = copy.putArray("required"); required.forEach(array::add);
        return copy;
    }
}
