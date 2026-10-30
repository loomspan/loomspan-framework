package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillValidationIssue;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.input.SkillInputContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

/** Metadata-only outgoing contract validation, reusable with a captured namespace view. */
public final class SkillDependencyValidator {
    public static boolean allowsNamespace(String sourceNamespace, String targetNamespace,
            ai.loomspan.api.NamespaceVisibility targetVisibility) {
        return sourceNamespace.equals(targetNamespace) || targetVisibility == ai.loomspan.api.NamespaceVisibility.PUBLIC;
    }
    private final SkillInputContractResolver inputs;
    private final List<CapabilityMetadata> fixedJavaCapabilities;
    private final List<String> fixedRestHandlerBeanNames;
    SkillDependencyValidator(SkillInputContractResolver inputs, List<CapabilityMetadata> javaCapabilities, List<String> restNames) {
        this.inputs = inputs; this.fixedJavaCapabilities = javaCapabilities; this.fixedRestHandlerBeanNames = restNames;
    }
    record CheckedSet(String namespace, List<YamlSkillDefinition> definitions,
            Map<YamlSkillDefinition, SkillInputContract> contracts, Map<String, String> outputSchemas, List<SkillValidationIssue> issues)
    {
        void requireValid()
        {
            issues.stream().filter(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR)
                    .findFirst().ifPresent(issue -> { throw new IllegalStateException(issue.message()); });
        }
    }

    CheckedSet check(String namespace, YamlSkillCatalog.CheckedDocuments documents,
            ai.loomspan.api.NamespaceVisibility visibility, Map<String, SkillGeneration> external)
    {
        List<SkillValidationIssue> issues = new ArrayList<>(documents.issues());
        LinkedHashMap<String, CapabilityMetadata> names = new LinkedHashMap<>();
        for (CapabilityMetadata javaCapability : fixedJavaCapabilities.stream().filter(cap -> cap.identity().namespace().equals(namespace)).toList())
            names.put(javaCapability.name(), javaCapability);
        Map<String, YamlSkillDefinition> definitions = new LinkedHashMap<>();
        for (YamlSkillDefinition definition : documents.definitions()) {
            String name = definition.identity().toString();
            if (names.containsKey(name)) issues.add(error(definition, "name", "Capability with name '" + name + "' is already registered"));
            else names.put(name, null);
            definitions.put(name, definition);
        }
        // The sole overlay rule: all references returning to the candidate use its complete declarations.
        external.forEach((owner, generation) -> {
            if (!owner.equals(namespace)) {
                generation.capabilities().forEach(cap -> names.put(cap.name(), cap));
                definitions.putAll(generation.definitions());
            }
        });
        return checkGraph(namespace, documents, visibility, external, names, definitions, issues);
    }

    void checkAdmission(SkillAdmissionSnapshot snapshot) {
        var names = new LinkedHashMap<String, CapabilityMetadata>();
        var definitions = new LinkedHashMap<String, YamlSkillDefinition>();
        snapshot.generations().values().forEach(generation -> {
            generation.capabilities().forEach(capability -> names.put(capability.name(), capability));
            definitions.putAll(generation.definitions());
        });
        var root = definitions.get(snapshot.entry().toString());
        var documents = new YamlSkillCatalog.CheckedDocuments(root == null ? List.of() : List.of(root), List.of());
        var checked = checkGraph(snapshot.entry().namespace(), documents, snapshot.entryGeneration().visibility(),
                snapshot.generations(), names, definitions, new ArrayList<>());
        checked.requireValid();
        var mapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();
        for (var definition : checked.contracts().keySet()) {
            String name = definition.identity().toString();
            String prepared = names.get(name).tool().outputSchema();
            String recomputed = checked.outputSchemas().get(name);
            if (!Objects.equals(prepared == null ? null : mapper.readTree(prepared),
                    recomputed == null ? null : mapper.readTree(recomputed)))
                throw new IllegalStateException("Prepared output contract changed for skill '" + name + "'; republish its namespace");
        }
    }

    private CheckedSet checkGraph(String namespace, YamlSkillCatalog.CheckedDocuments documents,
            ai.loomspan.api.NamespaceVisibility visibility, Map<String, SkillGeneration> external,
            Map<String, CapabilityMetadata> names, Map<String, YamlSkillDefinition> definitions,
            List<SkillValidationIssue> issues) {
        Map<YamlSkillDefinition, SkillInputContract> contracts = new LinkedHashMap<>();
        List<YamlSkillDefinition> reachable = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (YamlSkillDefinition root : documents.definitions())
            visit(root, namespace, visibility, external, definitions, names, visited, reachable, issues, documents.issues());
        for (YamlSkillDefinition definition : reachable) {
            try { contracts.put(definition, inputs.resolveYamlCapability(definition)); }
            catch (IllegalStateException ex) { issues.add(error(definition, "input_schema", ex.getMessage())); }
        }
        List<YamlSkillDefinition> rest = documents.definitions().stream().filter(YamlSkillDefinition::rest).toList();
        if (!rest.isEmpty() && fixedRestHandlerBeanNames.size() != 1)
        {
            String message = fixedRestHandlerBeanNames.isEmpty()
                    ? "REST skill manifests require exactly one RestSkillHandler bean; found none for "
                        + rest.stream().map(definition -> definition.source().diagnosticName()).sorted()
                                .reduce((left, right) -> left + ", " + right).orElseThrow()
                    : "REST skill manifests require exactly one RestSkillHandler bean; found "
                        + String.join(", ", fixedRestHandlerBeanNames);
            issues.add(error(rest.getFirst(), "rest", message));
        }
        // A nameless failed document could have declared any child; a named failure obscures only its own name.
        boolean unknownFailedName = documents.issues().stream()
                .anyMatch(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR && issue.skillName() == null);
        List<String> failedNames = documents.issues().stream()
                .filter(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR)
                .map(SkillValidationIssue::skillName).filter(Objects::nonNull).toList();
        Map<String, SkillInputContract> receiverContracts = new LinkedHashMap<>();
        names.values().stream().filter(Objects::nonNull).forEach(capability -> receiverContracts.put(capability.name(), capability.inputContract()));
        contracts.forEach((definition, contract) -> receiverContracts.put(definition.identity().toString(), contract));
        for (var definition : reachable) {
            var bindingIssues = ai.loomspan.internal.runtime.input.ChildInputBindingDeclarations.validate(
                    contracts.get(definition), definition.allowedSkillConstraints(), receiverContracts);
            bindingIssues.forEach(message -> issues.add(error(definition, "allowed_skills.input_bindings", message)));
        }
        Map<String, String> schemas = new LinkedHashMap<>();
        Set<String> resolved = new HashSet<>();
        for (YamlSkillDefinition definition : reachable)
            resolveOutputSchema(definition, definitions, names.keySet(), schemas, resolved,
                    new LinkedHashSet<>(), issues, unknownFailedName, failedNames);
        for (YamlSkillDefinition definition : reachable)
            validateOutputBindings(definition, contracts.get(definition), schemas, issues);
        return new CheckedSet(namespace, documents.definitions(), Map.copyOf(contracts), Map.copyOf(schemas), List.copyOf(issues));
    }

    private static void validateOutputBindings(YamlSkillDefinition definition, SkillInputContract input,
            Map<String, String> effectiveSchemas, List<SkillValidationIssue> issues) {
        if (definition.outputBindings().isEmpty()) return;
        var mapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();
        var destinationSchema = mapper.valueToTree(bindingOutputSchemaMetadata(definition.outputSchema()));
        var inputSchema = input == null ? null : mapper.valueToTree(inputSchemaMetadata(input.schema()));
        for (var binding : definition.outputBindings()) {
            String label = "Output binding " + binding.destination().pointer() + ": ";
            try {
                var target = selectBindingSchema(destinationSchema, binding.destination(), true);
                tools.jackson.databind.JsonNode source;
                if (binding.sourceKind() == ai.loomspan.internal.runtime.input.ChildInputBinding.SourceKind.INPUT)
                    source = selectBindingSchema(inputSchema, binding.sourcePath(), false);
                else {
                    var producer = definition.allowedSkillConstraints().stream()
                            .filter(child -> child.identity().equals(binding.skillIdentity())).findFirst().orElseThrow(
                                    () -> new IllegalArgumentException("unknown direct producer '" + binding.skill() + "'"));
                    if (Integer.valueOf(0).equals(producer.maxTasks()) || producer.effectiveMinTasks() > 1)
                        throw new IllegalArgumentException("producer cannot have exactly one task '" + binding.skill() + "'");
                    String producerSchema = effectiveSchemas.get(binding.skill());
                    var tree = producerSchema == null ? null : mapper.readTree(producerSchema);
                    if (tree != null) applyOutputDefaultOpenness(tree);
                    source = selectBindingSchema(tree, binding.sourcePath(), false);
                }
                checkBindingCompatibility(source, target, "selected value");
            } catch (IllegalArgumentException ex) {
                issues.add(error(definition, "output_bindings", label + ex.getMessage()));
            }
        }
    }

    private static Map<String, Object> bindingOutputSchemaMetadata(YamlSkillManifest.OutputSchemaManifest schema) {
        var metadata = outputSchemaMetadata(schema);
        var mapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();
        var tree = mapper.valueToTree(metadata);
        applyOutputDefaultOpenness(tree);
        return mapper.convertValue(tree, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private static void applyOutputDefaultOpenness(tools.jackson.databind.JsonNode schema) {
        if (schema.isObject()) {
            if ("object".equals(schema.path("type").asText()) && !schema.has("additionalProperties"))
                ((tools.jackson.databind.node.ObjectNode) schema).put("additionalProperties", false);
            for (var property : schema.path("properties").properties()) applyOutputDefaultOpenness(property.getValue());
            if (schema.has("items")) applyOutputDefaultOpenness(schema.get("items"));
        }
    }

    /** Open or unknown schema branches are runtime obligations; never infer producer shapes. */
    private static tools.jackson.databind.JsonNode selectBindingSchema(tools.jackson.databind.JsonNode node,
            ai.loomspan.internal.runtime.input.ObjectFieldPath path, boolean destination) {
        for (String token : path.tokens()) {
            if (node == null || ai.loomspan.internal.runtime.input.SkillInputSchemaNode.ANY_TYPE.equals(node.path("type").asText())) return null;
            if (!"object".equals(node.path("type").asText()))
                throw new IllegalArgumentException((destination ? "destination" : "source") + " path traverses a non-object schema at " + path.pointer());
            var properties = node.path("properties");
            var next = properties.get(token);
            if (next == null && destination)
                for (var field : properties.properties())
                    if (ai.loomspan.internal.outputschema.OutputSchemaValidator.propertyNamesMatch(field.getKey(), token)) { next = field.getValue(); break; }
            if (next == null) {
                if (node.path("additionalProperties").isBoolean() && !node.path("additionalProperties").booleanValue())
                    throw new IllegalArgumentException((destination ? "destination" : "source") + " path selects an undeclared field in a closed object at " + path.pointer());
                if (!node.path("additionalProperties").isObject()) return null;
                next = node.get("additionalProperties");
            }
            node = next;
        }
        return node;
    }

    private static void checkBindingCompatibility(tools.jackson.databind.JsonNode source,
            tools.jackson.databind.JsonNode target, String location) {
        if (source == null || target == null) return;
        String from = source.path("type").asText(), to = target.path("type").asText();
        if (ai.loomspan.internal.runtime.input.SkillInputSchemaNode.ANY_TYPE.equals(from)) return;
        if (!from.equals(to) && !(from.equals("integer") && to.equals("number")))
            throw new IllegalArgumentException("statically incompatible types at " + location + ": " + from + " -> " + to);
        if (source.path("nullable").asBoolean(false) && !target.path("nullable").asBoolean(false))
            throw new IllegalArgumentException("nullable source is incompatible with non-nullable destination at " + location);
        var destinationEnum = target.path("enum");
        if (destinationEnum.isArray() && !destinationEnum.isEmpty()) {
            var sourceEnum = source.path("enum");
            if (sourceEnum.isArray() && !sourceEnum.isEmpty()) {
                boolean intersects = false;
                for (var candidate : sourceEnum) for (var allowed : destinationEnum)
                    if (candidate.equals(allowed)) intersects = true;
                if (!intersects) throw new IllegalArgumentException("statically incompatible enums at " + location);
            }
        }
        if (from.equals("array")) checkBindingCompatibility(source.get("items"), target.get("items"), location + "[]");
        if (from.equals("object")) {
            var sourceProperties = source.path("properties");
            var targetProperties = target.path("properties");
            for (var field : sourceProperties.properties()) {
                var receiver = targetProperties.get(field.getKey());
                if (receiver == null) for (var declared : targetProperties.properties())
                    if (ai.loomspan.internal.outputschema.OutputSchemaValidator.propertyNamesMatch(declared.getKey(), field.getKey())) { receiver = declared.getValue(); break; }
                if (receiver == null && target.path("additionalProperties").isBoolean()
                        && !target.path("additionalProperties").booleanValue()) {
                    // Optional producer fields may be absent; only guaranteed fields prove a conflict.
                    for (var required : source.path("required"))
                        if (required.asText().equals(field.getKey()))
                            throw new IllegalArgumentException("required source field is not allowed in closed destination at " + location + "/" + field.getKey());
                }
                checkBindingCompatibility(field.getValue(), receiver, location + "/" + field.getKey());
            }
            for (var required : target.path("required")) {
                boolean declared = false;
                for (String property : sourceProperties.propertyNames())
                    if (ai.loomspan.internal.outputschema.OutputSchemaValidator.propertyNamesMatch(property, required.asText())) declared = true;
                if (!declared && source.path("additionalProperties").isBoolean()
                        && !source.path("additionalProperties").booleanValue())
                    throw new IllegalArgumentException("closed source cannot supply required destination field at " + location + "/" + required.asText());
            }
        }
    }

    private static Map<String, Object> inputSchemaMetadata(ai.loomspan.internal.runtime.input.SkillInputSchemaNode schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", schema.type());
        if (!schema.properties().isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            schema.properties().forEach((name, child) -> properties.put(name, inputSchemaMetadata(child)));
            result.put("properties", properties);
        }
        result.put("required", schema.required());
        if (schema.additionalPropertiesSchema() != null) result.put("additionalProperties", inputSchemaMetadata(schema.additionalPropertiesSchema()));
        else if (schema.additionalProperties() != null) result.put("additionalProperties", schema.additionalProperties());
        if (schema.items() != null) result.put("items", inputSchemaMetadata(schema.items()));
        if (!schema.enumValues().isEmpty()) result.put("enum", schema.enumValues());
        return result;
    }

    private static String resolveOutputSchema(YamlSkillDefinition definition,
            Map<String, YamlSkillDefinition> definitions, Set<String> names, Map<String, String> schemas,
            Set<String> resolved, LinkedHashSet<String> visiting, List<SkillValidationIssue> issues,
            boolean unknownFailedName, List<String> failedNames)
    {
        String name = definition.identity().toString();
        if (resolved.contains(name)) return schemas.get(name);
        if (!visiting.add(name)) {
            issues.add(error(definition, "output_from.skill", "Forwarding cycle: " + String.join(" -> ", visiting) + " -> " + name));
            return null;
        }
        String schema = null;
        String child = definition.outputFromSkill();
        if (child != null) {
            if (!names.contains(child) && !unknownFailedName && !failedNames.contains(child))
                issues.add(error(definition, "output_from.skill", "Unknown forwarding child '" + child + "'"));
            YamlSkillDefinition target = definitions.get(child);
            if (target != null) schema = resolveOutputSchema(target, definitions, names, schemas,
                    resolved, visiting, issues, unknownFailedName, failedNames);
        }
        else if (!definition.rest() && definition.outputSchema() != null) {
            var mapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();
            schema = mapper.writeValueAsString(outputSchemaMetadata(definition.outputSchema()));
        }
        visiting.remove(name);
        resolved.add(name);
        if (schema != null) schemas.put(name, schema);
        return schema;
    }

    /** Schema shape metadata excludes child-local evidence orchestration annotations. */
    private static Map<String, Object> outputSchemaMetadata(YamlSkillManifest.OutputSchemaManifest schema)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", schema.getType());
        if (!schema.getProperties().isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            schema.getProperties().forEach((name, child) -> properties.put(name, outputSchemaMetadata(child)));
            result.put("properties", properties);
        }
        if (!schema.getRequired().isEmpty()) result.put("required", schema.getRequired());
        if (schema.getAdditionalProperties() != null) result.put("additionalProperties", schema.getAdditionalProperties());
        if (schema.getItems() != null) result.put("items", outputSchemaMetadata(schema.getItems()));
        if (!schema.getEnumValues().isEmpty()) result.put("enum", schema.getEnumValues());
        if (schema.getDescription() != null) result.put("description", schema.getDescription());
        if (schema.getFormat() != null) result.put("format", schema.getFormat());
        if (schema.getNullable() != null) result.put("nullable", schema.getNullable());
        return result;
    }

    private static SkillValidationIssue error(YamlSkillDefinition definition, String path, String message)
    {
        return new SkillValidationIssue(SkillValidationIssue.Severity.ERROR,
                definition.source().diagnosticName(), definition.identity().toString(), path, message);
    }

    private static void visit(YamlSkillDefinition source, String candidateNamespace,
            ai.loomspan.api.NamespaceVisibility candidateVisibility, Map<String, SkillGeneration> external,
            Map<String, YamlSkillDefinition> definitions, Map<String, CapabilityMetadata> names,
            Set<String> visited, List<YamlSkillDefinition> reachable, List<SkillValidationIssue> issues,
            List<SkillValidationIssue> documentIssues) {
        if (!visited.add(source.identity().toString())) return;
        reachable.add(source);
        for (String child : source.allowedSkills()) {
            String targetNamespace = SkillIdentity.parse(child).namespace();
            boolean suppressed = targetNamespace.equals(candidateNamespace) && documentIssues.stream().anyMatch(issue ->
                    issue.severity() == SkillValidationIssue.Severity.ERROR && (issue.skillName() == null || child.equals(issue.skillName())));
            if (!names.containsKey(child)) {
                if (!suppressed) issues.add(error(source, "allowed_skills", "Unknown child skill '" + child + "'"));
                continue;
            }
            var targetVisibility = targetNamespace.equals(candidateNamespace) ? candidateVisibility : external.get(targetNamespace).visibility();
            if (!allowsNamespace(source.identity().namespace(), targetNamespace, targetVisibility))
                issues.add(error(source, "allowed_skills", "Foreign namespace '" + targetNamespace + "' is Private for child '" + child + "'"));
            YamlSkillDefinition target = definitions.get(child);
            if (target != null) visit(target, candidateNamespace, candidateVisibility, external, definitions, names,
                    visited, reachable, issues, documentIssues);
        }
    }

}
