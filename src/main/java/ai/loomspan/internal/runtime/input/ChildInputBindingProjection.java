package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** One compiled authority for model schemas and reserved-path checks. */
public final class ChildInputBindingProjection {
    private final List<ChildInputBinding> bindings;
    private final SkillInputContract argumentContract;
    private final DispatchEligibility dispatchEligibility;
    public ChildInputBindingProjection(SkillInputContract receivingContract, List<ChildInputBinding> bindings) {
        this.bindings = List.copyOf(bindings);
        this.argumentContract = bindings.isEmpty() ? receivingContract : new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                project(receivingContract.schema(), bindings.stream().map(b -> b.destination().tokens()).toList()));
        this.dispatchEligibility = proveDispatch(receivingContract);
    }
    public SkillInputContract argumentContract() { return argumentContract; }
    public List<ChildInputBinding> bindings() { return bindings; }
    public DispatchEligibility dispatchEligibility() { return dispatchEligibility; }
    public record DispatchEligibility(boolean eligible, String reason) {}
    private DispatchEligibility proveDispatch(SkillInputContract receiving) {
        var root = receiving.schema();
        if (!supportedTree(root) || bindings.stream().anyMatch(binding -> binding.destination().tokens().size() != 1))
            return new DispatchEligibility(false, "unsupported_or_ambiguous_shape");
        if (receiving.isGeneric() || !root.isObject() || root.allowsAdditionalProperties())
            return new DispatchEligibility(false, "open_or_unknown_contract");
        if (root.runtimeRefCapable() || root.isAttachment())
            return new DispatchEligibility(false, "unsupported_or_ambiguous_shape");
        var projected = argumentContract.schema();
        if (!projected.properties().isEmpty() || !projected.required().isEmpty())
            return new DispatchEligibility(false, "unbound_input_remains");
        if (!new SkillInputValidator().validate(Map.of(), argumentContract).valid() || !validateModelArguments(Map.of()).isEmpty())
            return new DispatchEligibility(false, "unsupported_or_ambiguous_shape");
        return new DispatchEligibility(true, "eligible");
    }
    private static boolean supportedTree(SkillInputSchemaNode node) {
        return node.dispatchProofSupported() && !node.isUnconstrained()
                && node.properties().values().stream().allMatch(ChildInputBindingProjection::supportedTree)
                && (node.items() == null || supportedTree(node.items()))
                && (node.additionalPropertiesSchema() == null || supportedTree(node.additionalPropertiesSchema()));
    }
    public String inputSchema() {
        var mapper = LoomspanJacksonCodecs.defaults().schemaTree();
        var resolver = new SkillInputContractResolver(mapper);
        Map<String, Object> schema = mapper.readValue(resolver.toJsonSchema(argumentContract), Map.class);
        for (var binding : bindings) {
            Map<String, Object> current = schema;
            var tokens = binding.destination().tokens();
            for (int i = 0; i < tokens.size(); i++) {
                Map<String, Object> properties = (Map<String, Object>) current.computeIfAbsent("properties", key -> new LinkedHashMap<>());
                if (i == tokens.size() - 1) properties.put(tokens.get(i), false);
                else current = (Map<String, Object>) properties.computeIfAbsent(tokens.get(i), key -> new LinkedHashMap<>());
            }
        }
        return mapper.writeValueAsString(schema);
    }
    public List<String> validateModelArguments(Map<String, Object> arguments) {
        List<String> issues = new ArrayList<>();
        for (var binding : bindings) {
            Object current = arguments;
            var tokens = binding.destination().tokens();
            for (int i = 0; i < tokens.size(); i++) {
                if (!(current instanceof Map<?, ?> map)) {
                    issues.add("Prohibited model override: non-object ancestor of bound destination " + binding.destination().pointer());
                    break;
                }
                if (!map.containsKey(tokens.get(i))) break;
                if (i == tokens.size() - 1) {
                    issues.add("Prohibited model override of bound destination " + binding.destination().pointer()); break;
                }
                current = map.get(tokens.get(i));
            }
        }
        return List.copyOf(issues);
    }
    private static SkillInputSchemaNode project(SkillInputSchemaNode node, List<List<String>> paths) {
        Map<String, SkillInputSchemaNode> properties = new LinkedHashMap<>(node.properties());
        LinkedHashSet<String> required = new LinkedHashSet<>(node.required());
        Map<String, List<List<String>>> grouped = new LinkedHashMap<>();
        paths.forEach(path -> grouped.computeIfAbsent(path.getFirst(), key -> new ArrayList<>()).add(path));
        for (var group : grouped.entrySet()) {
            String key = group.getKey();
            if (group.getValue().stream().anyMatch(path -> path.size() == 1)) {
                properties.remove(key); required.remove(key); continue;
            }
            SkillInputSchemaNode child = properties.get(key);
            if (child == null) child = node.additionalPropertiesSchema();
            if (child == null || child.isUnconstrained()) child = SkillInputContract.genericObject().schema();
            var projected = project(child, group.getValue().stream().map(path -> path.subList(1, path.size())).toList());
            properties.put(key, projected);
            // Binding creates this ancestor even when its receiving property was optional.
            if (projected.required().isEmpty()) required.remove(key); else required.add(key);
        }
        return new SkillInputSchemaNode(node.isUnconstrained() ? "object" : node.type(), properties, List.copyOf(required),
                node.additionalProperties(), node.additionalPropertiesSchema(), node.items(), node.enumValues(),
                node.description(), node.format(), node.runtimeRefCapable(), node.attachment(), node.attachmentMediaType(), node.allowedContentTypes(), node.dispatchProofSupported());
    }
}
