package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;

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
    SkillDependencyValidator(SkillInputContractResolver inputs, List<CapabilityMetadata> javaCapabilities) {
        this.inputs = inputs; this.fixedJavaCapabilities = javaCapabilities;
    }
    record CheckedSet(String namespace, List<YamlSkillDefinition> definitions,
            Map<YamlSkillDefinition, SkillInputContract> contracts, Map<String, String> outputSchemas, List<SkillValidationIssue> issues,
            Map<String, SkillAdmissionSnapshot.ExecutorBinding> executorBindings)
    {
        void requireValid()
        {
            issues.stream().filter(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR)
                    .findFirst().ifPresent(issue -> { throw new IllegalStateException(issue.message()); });
        }
    }

    CheckedSet check(String namespace, YamlSkillCatalog.CheckedDocuments documents,
            ai.loomspan.api.NamespaceVisibility visibility, Map<String, SkillGeneration> external,
            List<ai.loomspan.internal.core.SkillExecutorRegistration> fixedExecutors, Map<String, Object> configuration)
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
        var owners = namespaceViews(external);
        var registrations = new LinkedHashMap<String, ai.loomspan.internal.core.SkillExecutorRegistration>();
        fixedExecutors.forEach(registration -> registrations.put(registration.name(), registration));
        var candidate = new NamespaceView(visibility, registrations, configuration, "candidate");
        owners.put(namespace, candidate);
        // Every assigned fixed declaration is validated for the complete candidate, even without a callable.
        for (var registration : fixedExecutors) {
            try { selectConfiguration(registration, configuration); }
            catch (IllegalStateException ex) { issues.add(new SkillValidationIssue(SkillValidationIssue.Severity.ERROR,
                    registration.source().toString(), registration.name(), "executor.config", ex.getMessage())); }
        }
        return checkGraph(namespace, documents, names, definitions, issues, owners);
    }

    SkillAdmissionSnapshot checkAdmission(SkillAdmissionSnapshot snapshot) {
        var names = new LinkedHashMap<String, CapabilityMetadata>();
        var definitions = new LinkedHashMap<String, YamlSkillDefinition>();
        snapshot.generations().values().forEach(generation -> {
            generation.capabilities().forEach(capability -> names.put(capability.name(), capability));
            definitions.putAll(generation.definitions());
        });
        var root = definitions.get(snapshot.entry().toString());
        var documents = new YamlSkillCatalog.CheckedDocuments(root == null ? List.of() : List.of(root), List.of());
        var checked = checkGraph(snapshot.entry().namespace(), documents, names, definitions, new ArrayList<>(), namespaceViews(snapshot.generations()));
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
        return new SkillAdmissionSnapshot(snapshot.entry(), snapshot.generations(), checked.executorBindings());
    }

    private CheckedSet checkGraph(String namespace, YamlSkillCatalog.CheckedDocuments documents,
            Map<String, CapabilityMetadata> names, Map<String, YamlSkillDefinition> definitions,
            List<SkillValidationIssue> issues, Map<String, NamespaceView> owners) {
        Map<YamlSkillDefinition, SkillInputContract> contracts = new LinkedHashMap<>();
        List<YamlSkillDefinition> reachable = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (YamlSkillDefinition root : documents.definitions())
            visit(root, namespace, owners, definitions, names, visited, reachable, issues, documents.issues());
        Map<String, SkillAdmissionSnapshot.ExecutorBinding> executorBindings = new LinkedHashMap<>();
        for (YamlSkillDefinition definition : reachable) {
            if (definition.form() == YamlSkillDefinition.Form.EXECUTOR) {
                try { executorBindings.put(definition.identity().toString(), resolveExecutor(definition, owners)); }
                catch (IllegalStateException ex) { issues.add(error(definition, "executor", ex.getMessage())); }
            }
            try { contracts.put(definition, inputs.resolveYamlCapability(definition)); }
            catch (IllegalStateException ex) { issues.add(error(definition, "input_schema", ex.getMessage())); }
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
        return new CheckedSet(namespace, documents.definitions(), Map.copyOf(contracts), Map.copyOf(schemas), List.copyOf(issues), Map.copyOf(executorBindings));
    }

    private record NamespaceView(ai.loomspan.api.NamespaceVisibility visibility,
            Map<String, ai.loomspan.internal.core.SkillExecutorRegistration> executors,
            Map<String, Object> configuration, String generationId) {}

    private static Map<String, NamespaceView> namespaceViews(Map<String, SkillGeneration> generations) {
        var result = new LinkedHashMap<String, NamespaceView>();
        generations.forEach((namespace, generation) -> result.put(namespace,
                new NamespaceView(generation.visibility(), generation.executors(), generation.executorConfiguration(), generation.id())));
        return result;
    }

    private record Reference(SkillIdentity identity, NamespaceView owner) {}

    /** Both reference roles share canonical identity and namespace access before role-specific shape checks. */
    private static Reference resolveReference(String authored, String sourceNamespace, String role,
            Map<String, NamespaceView> owners) {
        var identity = SkillIdentity.resolve(authored, sourceNamespace);
        var owner = owners.get(identity.namespace());
        if (owner == null) throw new IllegalStateException("Unknown " + role + " '" + identity + "'");
        if (!allowsNamespace(sourceNamespace, identity.namespace(), owner.visibility()))
            throw new IllegalStateException("Foreign namespace '" + identity.namespace() + "' is Private for " + role + " '" + identity + "'");
        return new Reference(identity, owner);
    }

    private static SkillAdmissionSnapshot.ExecutorBinding resolveExecutor(YamlSkillDefinition definition,
            Map<String, NamespaceView> owners) {
        var reference = resolveReference(definition.executor(), definition.identity().namespace(), "executor", owners);
        var owner = reference.owner();
        var registration = owner.executors().get(reference.identity().toString());
        if (registration == null) throw new IllegalStateException("Unknown executor '" + reference.identity() + "'");
        return new SkillAdmissionSnapshot.ExecutorBinding(registration,
                selectConfiguration(registration, owner.configuration()), owner.generationId());
    }

    /** Exact dot-path selection belongs to dependency binding, never invocation or domain validation. */
    private static Map<String, Object> selectConfiguration(ai.loomspan.internal.core.SkillExecutorRegistration registration,
            Map<String, Object> configuration) {
        String path = registration.config();
        if (path == null || path.isEmpty()) return Map.of();
        if (!path.equals(path.strip()) || path.isBlank() || java.util.Arrays.stream(path.split("\\.", -1)).anyMatch(String::isEmpty))
            throw new IllegalStateException("Invalid executor config path '" + path + "' for '" + registration.name() + "'");
        Object selected = configuration;
        for (String segment : path.split("\\.")) {
            if (!(selected instanceof Map<?, ?> map) || !map.containsKey(segment))
                throw new IllegalStateException("Executor config path '" + path + "' must select a map for '" + registration.name() + "'");
            selected = map.get(segment);
        }
        if (!(selected instanceof Map<?, ?> map))
            throw new IllegalStateException("Executor config path '" + path + "' must select a map for '" + registration.name() + "'");
        @SuppressWarnings("unchecked") var result = (Map<String, Object>) map;
        return ai.loomspan.internal.util.ImmutableContainers.copyMap(result);
    }

    private static void validateOutputBindings(YamlSkillDefinition definition, SkillInputContract input,
            Map<String, String> effectiveSchemas, List<SkillValidationIssue> issues) {
        if (definition.outputBindings().isEmpty()) return;
        var mapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree();
        var destinationSchema = definition.outputSchema().tree();
        var inputSchema = input == null ? null : input.schema().tree();
        for (var binding : definition.outputBindings()) {
            String label = "Output binding " + binding.destination().pointer() + ": ";
            try {
                var target = BindingSchemaCompatibility.select(destinationSchema, binding.destination(), true);
                tools.jackson.databind.JsonNode source;
                if (binding.sourceKind() == ai.loomspan.internal.runtime.input.ChildInputBinding.SourceKind.INPUT)
                    source = BindingSchemaCompatibility.select(inputSchema, binding.sourcePath(), false);
                else {
                    var producer = definition.allowedSkillConstraints().stream()
                            .filter(child -> child.identity().equals(binding.skillIdentity())).findFirst().orElseThrow(
                                    () -> new IllegalArgumentException("unknown direct producer '" + binding.skill() + "'"));
                    if (Integer.valueOf(0).equals(producer.maxTasks()) || producer.effectiveMinTasks() > 1)
                        throw new IllegalArgumentException("producer cannot have exactly one task '" + binding.skill() + "'");
                    String producerSchema = effectiveSchemas.get(binding.skill());
                    var tree = producerSchema == null ? null : mapper.readTree(producerSchema);
                    source = BindingSchemaCompatibility.select(tree, binding.sourcePath(), false);
                }
                BindingSchemaCompatibility.check(source, target, "selected value");
            } catch (IllegalArgumentException ex) {
                issues.add(error(definition, "output_bindings", label + ex.getMessage()));
            }
        }
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
        else if (definition.form() != YamlSkillDefinition.Form.EXECUTOR && definition.outputSchema() != null) {
            var mapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();
            schema = mapper.writeValueAsString(definition.outputSchema().providerTree());
        }
        visiting.remove(name);
        resolved.add(name);
        if (schema != null) schemas.put(name, schema);
        return schema;
    }

    private static SkillValidationIssue error(YamlSkillDefinition definition, String path, String message)
    {
        return new SkillValidationIssue(SkillValidationIssue.Severity.ERROR,
                definition.source().diagnosticName(), definition.identity().toString(), path, message);
    }

    private static void visit(YamlSkillDefinition source, String candidateNamespace,
            Map<String, NamespaceView> owners, Map<String, YamlSkillDefinition> definitions, Map<String, CapabilityMetadata> names,
            Set<String> visited, List<YamlSkillDefinition> reachable, List<SkillValidationIssue> issues,
            List<SkillValidationIssue> documentIssues) {
        if (!visited.add(source.identity().toString())) return;
        reachable.add(source);
        for (String child : source.allowedSkills()) {
            String targetNamespace = SkillIdentity.parse(child).namespace();
            boolean suppressed = targetNamespace.equals(candidateNamespace) && documentIssues.stream().anyMatch(issue ->
                    issue.severity() == SkillValidationIssue.Severity.ERROR && (issue.skillName() == null || child.equals(issue.skillName())));
            try { resolveReference(child, source.identity().namespace(), "child skill", owners); }
            catch (IllegalStateException ex) {
                if (!suppressed) issues.add(error(source, "allowed_skills", ex.getMessage()));
                continue;
            }
            if (!names.containsKey(child)) {
                if (!suppressed) issues.add(error(source, "allowed_skills", "Unknown child skill '" + child + "'"));
                continue;
            }
            YamlSkillDefinition target = definitions.get(child);
            if (target != null) visit(target, candidateNamespace, owners, definitions, names,
                    visited, reachable, issues, documentIssues);
        }
    }

}
