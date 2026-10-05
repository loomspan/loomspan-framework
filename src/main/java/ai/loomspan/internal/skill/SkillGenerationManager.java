package ai.loomspan.internal.skill;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import org.springframework.core.env.Environment;
import ai.loomspan.api.SkillValidationIssue;
import ai.loomspan.api.SkillValidationResult;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.ValidatedSkill;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.core.SkillSource;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.DisposableBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;

/** Prepares detached complete skill generations and atomically owns the active one. */
public final class SkillGenerationManager implements SmartInitializingSingleton, DisposableBean
{
    private static final Logger log = LoggerFactory.getLogger(SkillGenerationManager.class);
    private final SkillMethodBeanPostProcessor javaSkills;
    private final Supplier<YamlSkillCatalog> yamlCatalogFactory;
    private final SkillInputContractResolver inputs;
    private final ListableBeanFactory beans;
    private final LoomspanProperties startupProperties;
    private final Environment environment;
    private final BiFunction<LoomspanProperties, TracePersistencePolicy, ExecutionRuntime> runtimeFactory;
    private final AtomicReference<SkillGeneration> active = new AtomicReference<>();
    private final Object generationMonitor = new Object();
    private final Map<String, PublishedGeneration> published = new HashMap<>();
    private final List<Registration> retirementListeners = new ArrayList<>();
    private volatile BooleanSupplier deliveryEnabled = () -> true;
    // A counter guarantees no reuse within this manager; the namespace avoids coupling to another instance.
    private final String generationNamespace = UUID.randomUUID().toString();
    private final AtomicLong issuedGenerationIds = new AtomicLong();
    private volatile List<CapabilityMetadata> fixedJavaCapabilities;
    private volatile List<String> fixedRestHandlerBeanNames;
    private volatile RestSkillHandler fixedRestHandler;

    public SkillGenerationManager(SkillMethodBeanPostProcessor javaSkills,
            Supplier<YamlSkillCatalog> yamlCatalogFactory,
            SkillInputContractResolver inputs,
            ListableBeanFactory beans)
    {
        this(javaSkills, yamlCatalogFactory, inputs, beans, null, null, null);
    }

    public SkillGenerationManager(SkillMethodBeanPostProcessor javaSkills,
            Supplier<YamlSkillCatalog> yamlCatalogFactory,
            SkillInputContractResolver inputs, ListableBeanFactory beans,
            LoomspanProperties startupProperties, Environment environment,
            BiFunction<LoomspanProperties, TracePersistencePolicy, ExecutionRuntime> runtimeFactory)
    {
        this.javaSkills = Objects.requireNonNull(javaSkills, "javaSkills must not be null");
        this.yamlCatalogFactory = Objects.requireNonNull(yamlCatalogFactory, "yamlCatalogFactory must not be null");
        this.inputs = Objects.requireNonNull(inputs, "inputs must not be null");
        this.beans = Objects.requireNonNull(beans, "beans must not be null");
        this.startupProperties = startupProperties;
        this.environment = environment;
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public synchronized void afterSingletonsInstantiated()
    {
        initializeFixedDependencies();
        if (active.get() == null) activate(prepare());
    }

    public SkillGeneration active()
    {
        SkillGeneration generation = active.get();
        if (generation == null)
        {
            afterSingletonsInstantiated();
            generation = active.get();
        }
        return generation;
    }

    public SkillGeneration prepare()
    {
        initializeFixedDependencies();
        YamlSkillCatalog catalog = currentCatalog();
        return prepareCatalog(check(catalog.checkedConfigured(true)), currentProperties(), currentPolicy());
    }

    public SkillGeneration prepare(List<SkillDocument> documents)
    {
        initializeFixedDependencies();
        YamlSkillCatalog catalog = currentCatalog();
        return prepareCatalog(check(catalog.checkedSupplied(documents, true)), currentProperties(), currentPolicy());
    }

    public SkillGeneration prepare(List<SkillDocument> documents, ExecutionConfiguration configuration)
    {
        return prepare(documents, configuration, null);
    }

    public SkillGeneration prepare(List<SkillDocument> documents, ExecutionConfiguration configuration,
            Map<String, String> credentialValues)
    {
        initializeFixedDependencies();
        ExecutionConfigurationParser.Parsed parsed = ExecutionConfigurationParser.parse(configuration, environment, false);
        parsed.properties().setSkills(startupProperties.getSkills());
        YamlSkillCatalog catalog = new YamlSkillCatalog(parsed.properties());
        CheckedSet checked = check(catalog.checkedSupplied(documents, true));
        checked.requireValid();
        // Resolve references only after all authored configuration and skills have passed validation.
        parsed = credentialValues == null
                ? ExecutionConfigurationParser.parse(configuration, environment, true)
                : ExecutionConfigurationParser.parse(configuration, credentialValues);
        parsed.properties().setSkills(startupProperties.getSkills());
        return prepareCatalog(checked, parsed.properties(), parsed.tracePersistence());
    }

    public SkillValidationResult validate(List<SkillDocument> documents, ExecutionConfiguration configuration)
    {
        requireInitialized();
        try
        {
            ExecutionConfigurationParser.Parsed parsed = ExecutionConfigurationParser.parse(configuration, environment, false);
            parsed.properties().setSkills(startupProperties.getSkills());
            return validationResult(check(new YamlSkillCatalog(parsed.properties()).checkedSupplied(documents, false)));
        }
        catch (RuntimeException ex)
        {
            return new SkillValidationResult(List.of(new SkillValidationIssue(
                    SkillValidationIssue.Severity.ERROR, "<configuration>", null, "configuration", ex.getMessage())), List.of());
        }
    }

    private LoomspanProperties currentProperties()
    {
        SkillGeneration generation = active.get();
        return generation == null || generation.runtime() == null ? startupProperties : generation.runtime().properties();
    }

    private YamlSkillCatalog currentCatalog()
    {
        return runtimeFactory == null ? Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null")
                : new YamlSkillCatalog(currentProperties());
    }

    private TracePersistencePolicy currentPolicy()
    {
        SkillGeneration generation = active.get();
        return generation == null || generation.runtime() == null
                ? startupProperties == null ? TracePersistencePolicy.ONERROR : startupProperties.getExecutionTrace().getPersistence()
                : generation.runtime().tracePersistence();
    }

    public LoomspanProperties.Session.Quotas quotasForGeneration(String generationId)
    {
        synchronized (generationMonitor)
        {
            PublishedGeneration generation = published.get(generationId);
            if (generation == null || generation.generation.runtime() == null)
                return startupProperties.getSession().getQuotas();
            return generation.generation.runtime().properties().getSession().getQuotas();
        }
    }

    public TracePersistencePolicy activeTracePersistence()
    {
        return currentPolicy();
    }

    public SkillValidationResult validate()
    {
        requireInitialized();
        YamlSkillCatalog catalog = currentCatalog();
        return validationResult(check(catalog.checkedConfigured(false)));
    }

    public SkillValidationResult validate(List<SkillDocument> documents)
    {
        requireInitialized();
        YamlSkillCatalog catalog = currentCatalog();
        return validationResult(check(catalog.checkedSupplied(documents, false)));
    }

    private void requireInitialized()
    {
        if (fixedJavaCapabilities == null || fixedRestHandlerBeanNames == null)
            throw new IllegalStateException("Skill validation requires completed framework startup");
    }

    private record CheckedSet(List<YamlSkillDefinition> definitions,
            Map<YamlSkillDefinition, SkillInputContract> contracts, Map<String, String> outputSchemas, List<SkillValidationIssue> issues)
    {
        void requireValid()
        {
            issues.stream().filter(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR)
                    .findFirst().ifPresent(issue -> { throw new IllegalStateException(issue.message()); });
        }
    }

    private SkillValidationResult validationResult(CheckedSet checked)
    {
        List<SkillValidationIssue> issues = checked.issues();
        if (issues.stream().anyMatch(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR))
            return new SkillValidationResult(issues, List.of());
        List<ValidatedSkill> skills = new ArrayList<>();
        for (CapabilityMetadata javaCapability : fixedJavaCapabilities)
            skills.add(new ValidatedSkill(javaCapability.name(), javaCapability.kind().publicKind()));
        for (YamlSkillDefinition definition : checked.definitions())
            skills.add(new ValidatedSkill(definition.manifest().getName(),
                    definition.rest() ? SkillKind.REST : SkillKind.YAML));
        skills.sort(Comparator.comparing(ValidatedSkill::name));
        return new SkillValidationResult(issues, skills);
    }

    private CheckedSet check(YamlSkillCatalog.CheckedDocuments documents)
    {
        List<SkillValidationIssue> issues = new ArrayList<>(documents.issues());
        Map<YamlSkillDefinition, SkillInputContract> contracts = new LinkedHashMap<>();
        LinkedHashMap<String, CapabilityMetadata> names = new LinkedHashMap<>();
        for (CapabilityMetadata javaCapability : fixedJavaCapabilities)
            names.put(javaCapability.name(), javaCapability);
        for (YamlSkillDefinition definition : documents.definitions())
        {
            String name = definition.manifest().getName();
            CapabilityMetadata existing = names.get(name);
            if (existing != null)
                issues.add(error(definition, "name", "Capability with name '" + name
                        + "' is already registered at " + existing.id() + "; conflicting declaration at "
                        + (definition.rest() ? "rest:" : "yaml:") + definition.source().diagnosticName()));
            else names.put(name, null);
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
        for (YamlSkillDefinition definition : documents.definitions())
            for (String child : definition.allowedSkills())
                if (!names.containsKey(child) && !unknownFailedName && !failedNames.contains(child))
                    issues.add(error(definition, "allowed_skills", "Unknown child skill '" + child
                            + "' in allowed_skills of '" + definition.manifest().getName() + "' at "
                            + definition.source().diagnosticName()));
        Map<String, YamlSkillDefinition> definitions = new LinkedHashMap<>();
        documents.definitions().forEach(definition -> definitions.put(definition.manifest().getName(), definition));
        Map<String, SkillInputContract> receiverContracts = new LinkedHashMap<>();
        fixedJavaCapabilities.forEach(capability -> receiverContracts.put(capability.name(), capability.inputContract()));
        contracts.forEach((definition, contract) -> receiverContracts.put(definition.manifest().getName(), contract));
        for (var definition : documents.definitions()) {
            var bindingIssues = ai.loomspan.internal.runtime.input.ChildInputBindingDeclarations.validate(
                    contracts.get(definition), definition.allowedSkillConstraints(), receiverContracts);
            bindingIssues.forEach(message -> issues.add(error(definition, "allowed_skills.input_bindings", message)));
        }
        Map<String, String> schemas = new LinkedHashMap<>();
        Set<String> resolved = new HashSet<>();
        for (YamlSkillDefinition definition : documents.definitions())
            resolveOutputSchema(definition, definitions, names.keySet(), schemas, resolved,
                    new LinkedHashSet<>(), issues, unknownFailedName, failedNames);
        for (YamlSkillDefinition definition : documents.definitions())
            validateOutputBindings(definition, contracts.get(definition), schemas, issues);
        return new CheckedSet(documents.definitions(), Map.copyOf(contracts), Map.copyOf(schemas), List.copyOf(issues));
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
                            .filter(child -> child.name().equals(binding.skill())).findFirst().orElseThrow(
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
        String name = definition.manifest().getName();
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
                definition.source().diagnosticName(), definition.manifest().getName(), path, message);
    }

    private SkillGeneration prepareCatalog(CheckedSet checked, LoomspanProperties properties, TracePersistencePolicy policy)
    {
        checked.requireValid();
        long ordinal = issuedGenerationIds.incrementAndGet();
        if (ordinal <= 0) throw new IllegalStateException("Skill generation ID space exhausted");
        String generationId = generationNamespace + "-" + ordinal;
        List<YamlSkillDefinition> definitions = checked.definitions();
        LinkedHashMap<String, CapabilityMetadata> capabilities = new LinkedHashMap<>();
        for (CapabilityMetadata metadata : fixedJavaCapabilities) putCapability(capabilities, metadata);

        List<YamlSkillDefinition> restDefinitions = definitions.stream().filter(YamlSkillDefinition::rest).toList();
        RestSkillHandler restHandler = restDefinitions.isEmpty() ? null : requireRestHandler();
        LinkedHashMap<String, YamlSkillDefinition> definitionsByName = new LinkedHashMap<>();
        for (YamlSkillDefinition definition : definitions)
        {
            String name = definition.manifest().getName();
            definitionsByName.put(name, definition);
            String description = definition.manifest().getDescription();
            var contract = checked.contracts().get(definition);
            boolean rest = definition.rest();
            RestSkillHandler handler = restHandler;
            CapabilityMetadata metadata = new CapabilityMetadata(
                    (rest ? "rest:" : "yaml:") + definition.source().diagnosticName(),
                    name, description,
                    rest ? SkillExecutionDescriptor.none()
                            : SkillExecutionDescriptor.from(definition.requireExecutionConfiguration()),
                    SkillAccessPolicy.yamlRoles(definition.rbacRoles()),
                    rest ? arguments -> invokeRest(handler, name, generationId, arguments)
                            : arguments -> { throw new IllegalStateException("YAML skills require model execution"); },
                    rest ? CapabilityKind.REST_SKILL : CapabilityKind.YAML_SKILL,
                    new CapabilityToolDescriptor(name, description, inputs.toJsonSchema(contract), checked.outputSchemas().get(name)),
                    contract, new SkillSource(definition.source().diagnosticName(), null, null));
            putCapability(capabilities, metadata);
        }
        ExecutionRuntime runtime = runtimeFactory == null ? null : runtimeFactory.apply(properties, policy);
        try { return new SkillGeneration(generationId, capabilities, definitionsByName, null, null, runtime); }
        catch (RuntimeException | Error ex) { if (runtime != null) runtime.close(); throw ex; }
    }

    public void activate(SkillGeneration candidate)
    {
        dispatch(activateAndSelect(candidate));
    }

    /** The caller dispatches the returned notification only after leaving publication locks. */
    public Retirement activateAndSelect(SkillGeneration candidate)
    {
        Objects.requireNonNull(candidate, "candidate must not be null");
        synchronized (generationMonitor)
        {
            SkillGeneration previous = active.getAndSet(candidate);
            published.put(candidate.id(), new PublishedGeneration(candidate));
            if (previous == null) return null;
            PublishedGeneration old = published.get(previous.id());
            old.superseded = true;
            return selectIfRetired(old);
        }
    }

    public Capture capture()
    {
        active();
        synchronized (generationMonitor)
        {
            SkillGeneration generation = active.get();
            PublishedGeneration state = published.get(generation.id());
            state.owners++;
            return new Capture(generation, new OwnerLease(state));
        }
    }

    public void deliveryEnabled(BooleanSupplier condition)
    {
        deliveryEnabled = Objects.requireNonNull(condition, "condition must not be null");
    }

    @Override public void destroy()
    {
        List<Retirement> retirements = new ArrayList<>();
        synchronized (generationMonitor)
        {
            for (PublishedGeneration state : List.copyOf(published.values()))
            {
                state.superseded = true;
                Retirement retirement = selectIfRetired(state);
                if (retirement != null) retirements.add(retirement);
            }
        }
        retirements.forEach(SkillGenerationManager::dispatch);
    }

    public AutoCloseable onGenerationRetired(Consumer<String> listener)
    {
        Objects.requireNonNull(listener, "listener must not be null");
        Registration registration = new Registration(listener);
        synchronized (generationMonitor) { retirementListeners.add(registration); }
        return () -> {
            if (registration.closed.compareAndSet(false, true))
                synchronized (generationMonitor) { retirementListeners.remove(registration); }
        };
    }

    private Retirement selectIfRetired(PublishedGeneration state)
    {
        if (!state.superseded || state.owners != 0) return null;
        published.remove(state.id);
        return new Retirement(state.id, state.generation,
                deliveryEnabled.getAsBoolean() ? retirementListeners.stream().map(entry -> entry.listener).toList() : List.of());
    }

    public static void dispatch(Retirement retirement)
    {
        if (retirement == null) return;
        try { retirement.generation.close(); }
        catch (Throwable failure) { log.warn("Skill generation resource close failed for {}", retirement.id, failure); }
        for (Consumer<String> listener : retirement.listeners)
        {
            try { listener.accept(retirement.id); }
            catch (Throwable failure) { log.warn("Skill generation retirement listener failed for {}", retirement.id, failure); }
        }
    }

    private static final class PublishedGeneration
    {
        private final String id;
        private final SkillGeneration generation;
        private int owners;
        private boolean superseded;

        private PublishedGeneration(SkillGeneration generation) { this.id = generation.id(); this.generation = generation; }
    }

    private static final class Registration
    {
        private final Consumer<String> listener;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(Consumer<String> listener) { this.listener = listener; }
    }

    public record Capture(SkillGeneration generation, OwnerLease lease) {}

    public final class OwnerLease implements AutoCloseable
    {
        private final PublishedGeneration state;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OwnerLease(PublishedGeneration state) { this.state = state; }

        @Override public void close()
        {
            if (!closed.compareAndSet(false, true)) return;
            Retirement retirement;
            synchronized (generationMonitor)
            {
                state.owners--;
                retirement = selectIfRetired(state);
            }
            dispatch(retirement);
        }
    }

    public record Retirement(String id, SkillGeneration generation, List<Consumer<String>> listeners) {}

    private synchronized void initializeFixedDependencies()
    {
        if (fixedJavaCapabilities != null) return;
        fixedJavaCapabilities = javaSkills.capabilities();
        String[] names = beans.getBeanNamesForType(RestSkillHandler.class, true, false);
        Arrays.sort(names);
        fixedRestHandlerBeanNames = List.of(names);
    }

    private synchronized RestSkillHandler requireRestHandler()
    {
        if (fixedRestHandler == null)
            fixedRestHandler = beans.getBean(fixedRestHandlerBeanNames.getFirst(), RestSkillHandler.class);
        return fixedRestHandler;
    }

    private static void putCapability(Map<String, CapabilityMetadata> capabilities, CapabilityMetadata metadata)
    {
        CapabilityMetadata existing = capabilities.putIfAbsent(metadata.name(), metadata);
        if (existing != null)
            throw new IllegalStateException("Capability with name '" + metadata.name()
                    + "' is already registered at " + existing.id()
                    + "; conflicting declaration at " + metadata.id());
    }

    private static String invokeRest(RestSkillHandler handler, String name, String generationId,
            Map<String, Object> arguments)
    {
        String result = handler.handle(new RestSkillInvocation(name, arguments, generationId));
        if (result == null) throw new IllegalStateException("REST skill '" + name + "' handler returned null");
        return result;
    }
}
