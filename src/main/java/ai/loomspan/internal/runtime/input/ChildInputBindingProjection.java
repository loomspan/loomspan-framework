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
    public ChildInputBindingProjection(SkillInputContract receivingContract, List<ChildInputBinding> bindings) {
        this.bindings = List.copyOf(bindings);
        this.argumentContract = bindings.isEmpty() ? receivingContract : new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                project(receivingContract.schema(), bindings.stream().map(b -> b.destination().tokens()).toList()));
    }
    public SkillInputContract argumentContract() { return argumentContract; }
    public List<ChildInputBinding> bindings() { return bindings; }
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
                node.description(), node.format(), node.runtimeRefCapable(), node.attachment(), node.attachmentMediaType(), node.allowedContentTypes());
    }
}
