package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;

import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import org.springframework.core.env.Environment;
import ai.loomspan.api.SkillValidationIssue;
import ai.loomspan.api.SkillValidationResult;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.ValidatedSkill;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.core.SkillSource;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.DisposableBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private final LoomspanProperties startupProperties;
    private final Environment environment;
    private final BiFunction<LoomspanProperties, TracePersistencePolicy, ExecutionRuntime> runtimeFactory;
    private final Map<String, SkillGeneration> active = new LinkedHashMap<>();
    private boolean startupComplete;
    private boolean destroyed;
    private final Object generationMonitor = new Object();
    private final Map<String, PublishedGeneration> published = new HashMap<>();
    private final List<Registration> retirementListeners = new ArrayList<>();
    private volatile BooleanSupplier deliveryEnabled = () -> true;
    // A counter guarantees no reuse within this manager; the namespace avoids coupling to another instance.
    private final String generationNamespace = UUID.randomUUID().toString();
    private final AtomicLong issuedGenerationIds = new AtomicLong();
    private volatile List<CapabilityMetadata> fixedJavaCapabilities;
    private volatile List<ai.loomspan.internal.core.SkillExecutorRegistration> fixedExecutors;

    public SkillGenerationManager(SkillMethodBeanPostProcessor javaSkills,
            Supplier<YamlSkillCatalog> yamlCatalogFactory,
            SkillInputContractResolver inputs)
    {
        this(javaSkills, yamlCatalogFactory, inputs, null, null, null);
    }

    public SkillGenerationManager(SkillMethodBeanPostProcessor javaSkills,
            Supplier<YamlSkillCatalog> yamlCatalogFactory,
            SkillInputContractResolver inputs,
            LoomspanProperties startupProperties, Environment environment,
            BiFunction<LoomspanProperties, TracePersistencePolicy, ExecutionRuntime> runtimeFactory)
    {
        this.javaSkills = Objects.requireNonNull(javaSkills, "javaSkills must not be null");
        this.yamlCatalogFactory = Objects.requireNonNull(yamlCatalogFactory, "yamlCatalogFactory must not be null");
        this.inputs = Objects.requireNonNull(inputs, "inputs must not be null");
        this.startupProperties = startupProperties;
        this.environment = environment;
        this.runtimeFactory = runtimeFactory;
    }

    @Override public void afterSingletonsInstantiated() { initializeStartup(); }

    /** Startup is one-shot and independent of whether any namespace is subsequently withdrawn. */
    public synchronized void initializeStartup() {
        if (startupComplete) return;
        initializeFixedDependencies();
        List<SkillGeneration> built = new ArrayList<>();
        try {
            LoomspanProperties properties = startupProperties == null ? new LoomspanProperties() : startupProperties;
            YamlSkillCatalog catalog = Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null");
            var configured = catalog.checkedConfigured(true);
            built.add(prepareCatalog(check("default", configured, NamespaceVisibility.PRIVATE, Map.of(), properties.getExecutorConfig()), properties,
                    properties.getExecutionTrace().getPersistence(), NamespaceVisibility.PRIVATE));
            for (String namespace : java.util.stream.Stream.concat(fixedJavaCapabilities.stream().map(cap -> cap.identity().namespace()),
                    fixedExecutors.stream().map(registration -> registration.identity().namespace())).distinct().toList()) {
                if (namespace.equals("default")) continue;
                built.add(prepareCatalog(check(namespace, new YamlSkillCatalog(properties).checkedSupplied(namespace, List.of(), true),
                        NamespaceVisibility.PRIVATE, Map.of(), properties.getExecutorConfig()), properties, properties.getExecutionTrace().getPersistence(), NamespaceVisibility.PRIVATE));
            }
            synchronized (generationMonitor) {
                if (destroyed) throw new IllegalStateException("Framework has shut down");
                for (SkillGeneration generation : built) {
                    active.put(generation.namespace(), generation);
                    published.put(generation.id(), new PublishedGeneration(generation));
                }
                startupComplete = true;
            }
        } catch (RuntimeException | Error failure) {
            built.forEach(generation -> { try { generation.close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); } });
            throw failure;
        }
    }

    public SkillGeneration active(String namespace) {
        String owner = SkillIdentity.normalizeNamespace(namespace);
        synchronized (generationMonitor) {
            SkillGeneration generation = active.get(owner);
            if (generation == null) throw new SkillReloadException("No active namespace '" + owner + "'");
            return generation;
        }
    }

    /** Existing host inspection is unfiltered and includes every active namespace. */
    public ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog registeredSkillCatalog()
    {
        java.util.List<ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog> catalogs;
        synchronized (generationMonitor)
        {
            catalogs = active.values().stream().map(SkillGeneration::registeredSkillCatalog).toList();
        }
        return new ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog(catalogs);
    }

    /** Immutable independently usable metadata; no runtime/client/invoker is read during validation. */
    public record PreparationView(String namespace, String baseId, Map<String, SkillGeneration> namespaces) {}

    public PreparationView capturePreparation(String namespace) {
        String owner = SkillIdentity.normalizeNamespace(namespace);
        synchronized (generationMonitor) {
            if (destroyed) throw new SkillReloadException("Framework has shut down");
            SkillGeneration base = active.get(owner);
            return new PreparationView(owner, base == null ? null : base.id(), Map.copyOf(active));
        }
    }

    public SkillGeneration prepare(PreparationView view, List<SkillDocument> documents, ExecutionConfiguration configuration,
            NamespaceVisibility visibility, Map<String, String> credentialValues) {
        requireInitialized();
        Objects.requireNonNull(visibility, "visibility must not be null");
        var parsed = ExecutionConfigurationParser.parse(view.namespace(), configuration, environment, false);
        var checked = check(view.namespace(), new YamlSkillCatalog(parsed.properties()).checkedSupplied(view.namespace(), documents, true),
                visibility, view.namespaces(), parsed.executorConfig());
        checked.requireValid();
        parsed = credentialValues == null ? ExecutionConfigurationParser.parse(view.namespace(), configuration, environment, true)
                : ExecutionConfigurationParser.parse(view.namespace(), configuration, credentialValues);
        return prepareCatalog(checked, parsed.properties(), parsed.tracePersistence(), visibility);
    }

    public SkillValidationResult validate(PreparationView view, List<SkillDocument> documents,
            ExecutionConfiguration configuration, NamespaceVisibility visibility) {
        requireInitialized();
        Objects.requireNonNull(visibility, "visibility must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        try {
            var parsed = ExecutionConfigurationParser.parse(view.namespace(), configuration, environment, false);
            return validationResult(check(view.namespace(), new YamlSkillCatalog(parsed.properties()).checkedSupplied(view.namespace(), documents, false),
                    visibility, view.namespaces(), parsed.executorConfig()));
        } catch (RuntimeException ex) {
            return new SkillValidationResult(List.of(new SkillValidationIssue(SkillValidationIssue.Severity.ERROR,
                    "<configuration>", null, "configuration", ex.getMessage())), List.of());
        }
    }

    private SkillDependencyValidator.CheckedSet check(String namespace, YamlSkillCatalog.CheckedDocuments documents,
            NamespaceVisibility visibility, Map<String, SkillGeneration> external, ExecutorConfiguration configuration) {
        return new SkillDependencyValidator(inputs, fixedJavaCapabilities).check(namespace, documents, visibility, external,
                fixedExecutors.stream().filter(registration -> registration.identity().namespace().equals(namespace)).toList(),
                configuration.namespaces().getOrDefault(namespace, Map.of()));
    }

    private void requireInitialized() {
        if (fixedJavaCapabilities == null || fixedExecutors == null)
            throw new IllegalStateException("Skill validation requires completed framework startup");
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

    public TracePersistencePolicy activeTracePersistence() {
        var generation = active("default");
        return generation.runtime() == null ? TracePersistencePolicy.ONERROR : generation.runtime().tracePersistence();
    }

    private SkillValidationResult validationResult(SkillDependencyValidator.CheckedSet checked)
    {
        List<SkillValidationIssue> issues = checked.issues();
        if (issues.stream().anyMatch(issue -> issue.severity() == SkillValidationIssue.Severity.ERROR))
            return new SkillValidationResult(issues, List.of());
        List<ValidatedSkill> skills = new ArrayList<>();
        for (CapabilityMetadata javaCapability : fixedJavaCapabilities.stream().filter(cap -> cap.identity().namespace().equals(checked.namespace())).toList())
            skills.add(new ValidatedSkill(javaCapability.name(), javaCapability.kind()));
        for (YamlSkillDefinition definition : checked.definitions())
            skills.add(new ValidatedSkill(definition.identity().toString(),
                    definition.form() == YamlSkillDefinition.Form.EXECUTOR ? SkillKind.JAVA
                            : definition.typesafe() ? SkillKind.TYPESAFE : SkillKind.YAML));
        skills.sort(Comparator.comparing(ValidatedSkill::name));
        return new SkillValidationResult(issues, skills);
    }

    private SkillGeneration prepareCatalog(SkillDependencyValidator.CheckedSet checked, LoomspanProperties properties, TracePersistencePolicy policy, NamespaceVisibility visibility)
    {
        checked.requireValid();
        long ordinal = issuedGenerationIds.incrementAndGet();
        if (ordinal <= 0) throw new IllegalStateException("Skill generation ID space exhausted");
        String generationId = generationNamespace + "-" + ordinal;
        List<YamlSkillDefinition> definitions = checked.definitions();
        LinkedHashMap<String, CapabilityMetadata> capabilities = new LinkedHashMap<>();
        for (CapabilityMetadata metadata : fixedJavaCapabilities.stream().filter(cap -> cap.identity().namespace().equals(checked.namespace())).toList()) putCapability(capabilities, metadata);

        LinkedHashMap<String, YamlSkillDefinition> definitionsByName = new LinkedHashMap<>();
        for (YamlSkillDefinition definition : definitions)
        {
            String name = definition.identity().toString();
            definitionsByName.put(name, definition);
            String description = definition.manifest().getDescription();
            var contract = checked.contracts().get(definition);
            CapabilityMetadata metadata = new CapabilityMetadata(
                    "yaml:" + definition.source().diagnosticName(),
                    definition.identity(), description,
                    definition.form() == YamlSkillDefinition.Form.EXECUTOR ? SkillExecutionDescriptor.none()
                            : SkillExecutionDescriptor.from(definition.requireExecutionConfiguration()),
                    SkillAccessPolicy.yamlRoles(definition.rbacRoles()),
                    arguments -> { throw new IllegalStateException("YAML skills require model execution"); },
                    definition.form() == YamlSkillDefinition.Form.EXECUTOR ? SkillKind.JAVA
                            : definition.typesafe() ? SkillKind.TYPESAFE : SkillKind.YAML,
                    new CapabilityToolDescriptor(name, description, inputs.toJsonSchema(contract), checked.outputSchemas().get(name)),
                    contract, new SkillSource(definition.source().diagnosticName(), null, null));
            putCapability(capabilities, metadata);
        }
        ExecutionRuntime runtime = runtimeFactory == null ? null : runtimeFactory.apply(properties, policy);
        try { return new SkillGeneration(checked.namespace(), generationId, capabilities, definitionsByName, null, null, runtime, visibility,
                fixedExecutors.stream().filter(registration -> registration.identity().namespace().equals(checked.namespace()))
                        .collect(java.util.stream.Collectors.toMap(ai.loomspan.internal.core.SkillExecutorRegistration::name, registration -> registration)),
                properties.getExecutorConfig().namespaces().getOrDefault(checked.namespace(), Map.of())); }
        catch (RuntimeException | Error ex) { if (runtime != null) runtime.close(); throw ex; }
    }

    /** The caller dispatches the returned notification only after leaving publication locks. */
    private Retirement activateAndSelect(SkillGeneration candidate)
    {
        Objects.requireNonNull(candidate, "candidate must not be null");
        synchronized (generationMonitor)
        {
            SkillGeneration previous = active.put(candidate.namespace(), candidate);
            published.put(candidate.id(), new PublishedGeneration(candidate));
            if (previous == null) return null;
            PublishedGeneration old = published.get(previous.id());
            old.superseded = true;
            return selectIfRetired(old);
        }
    }

    public Retirement publish(SkillGeneration candidate, String expectedBaseId) {
        synchronized (generationMonitor) {
            if (destroyed) throw new SkillReloadException("Framework has shut down");
            var previous = active.get(candidate.namespace());
            if (!Objects.equals(expectedBaseId, previous == null ? null : previous.id()))
                throw new SkillReloadException("Skill update is stale; target generation changed since preparation");
            return activateAndSelect(candidate);
        }
    }

    public Retirement remove(String namespace, String expectedGenerationId) {
        String owner = SkillIdentity.normalizeNamespace(namespace);
        Objects.requireNonNull(expectedGenerationId, "expectedGenerationId must not be null");
        if (expectedGenerationId.isBlank()) throw new IllegalArgumentException("expectedGenerationId must not be blank");
        synchronized (generationMonitor) {
            if (destroyed) throw new SkillReloadException("Framework has shut down");
            var previous = active.get(owner);
            if (previous == null || !previous.id().equals(expectedGenerationId))
                throw new SkillReloadException("Missing namespace or unexpected active generation for '" + owner + "'");
            active.remove(owner);
            var old = published.get(previous.id());
            old.superseded = true;
            return selectIfRetired(old);
        }
    }

    public Capture capture(SkillIdentity entry)
    {
        Objects.requireNonNull(entry, "entry must not be null");
        synchronized (generationMonitor)
        {
            if (destroyed) throw new SkillReloadException("Framework has shut down");
            Map<String, SkillGeneration> selected = new LinkedHashMap<>();
            var pending = new java.util.ArrayDeque<SkillIdentity>();
            var visited = new java.util.HashSet<SkillIdentity>();
            pending.add(entry);
            while (!pending.isEmpty()) {
                var identity = pending.removeFirst();
                var generation = selected.computeIfAbsent(identity.namespace(), namespace -> {
                    var current = active.get(namespace);
                    if (current == null) throw new SkillReloadException("No active namespace '" + namespace + "'");
                    return current;
                });
                if (generation.capability(identity.toString()) == null)
                    throw new ai.loomspan.api.SkillException("Unknown skill '" + identity + "'");
                if (!visited.add(identity)) continue;
                var definition = generation.definition(identity.toString());
                if (definition != null) {
                    definition.allowedSkills().forEach(child -> pending.add(SkillIdentity.parse(child)));
                    if (definition.form() == YamlSkillDefinition.Form.EXECUTOR) {
                        var executor = SkillIdentity.resolve(definition.executor(), identity.namespace());
                        selected.computeIfAbsent(executor.namespace(), namespace -> {
                            var current = active.get(namespace);
                            if (current == null) throw new SkillReloadException("No active namespace '" + namespace + "'");
                            return current;
                        });
                    }
                }
            }
            var snapshot = new SkillAdmissionSnapshot(entry, selected);
            var states = selected.values().stream().map(generation -> published.get(generation.id())).toList();
            var owner = new OwnerLease(states);
            states.forEach(state -> state.owners++);
            return new Capture(snapshot, owner);
        }
    }

    /** Expensive policy checks use retained immutable metadata, outside the registry monitor. */
    public SkillAdmissionSnapshot validateAdmission(SkillAdmissionSnapshot snapshot) {
        return new SkillDependencyValidator(inputs, fixedJavaCapabilities).checkAdmission(snapshot);
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
            destroyed = true;
            active.clear();
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

    public record Capture(SkillAdmissionSnapshot snapshot, OwnerLease lease) {}

    public final class OwnerLease implements AutoCloseable
    {
        private final List<PublishedGeneration> states;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OwnerLease(List<PublishedGeneration> states) { this.states = states; }

        @Override public void close()
        {
            if (!closed.compareAndSet(false, true)) return;
            List<Retirement> retirements = new ArrayList<>();
            synchronized (generationMonitor)
            {
                for (var state : states) {
                    state.owners--;
                    var retirement = selectIfRetired(state);
                    if (retirement != null) retirements.add(retirement);
                }
            }
            retirements.forEach(SkillGenerationManager::dispatch);
        }
    }

    public record Retirement(String id, SkillGeneration generation, List<Consumer<String>> listeners) {}

    private synchronized void initializeFixedDependencies()
    {
        if (fixedJavaCapabilities != null) return;
        fixedJavaCapabilities = List.copyOf(javaSkills.capabilities());
        fixedExecutors = List.copyOf(javaSkills.executors());
    }

    private static void putCapability(Map<String, CapabilityMetadata> capabilities, CapabilityMetadata metadata)
    {
        CapabilityMetadata existing = capabilities.putIfAbsent(metadata.name(), metadata);
        if (existing != null)
            throw new IllegalStateException("Capability with name '" + metadata.name()
                    + "' is already registered at " + existing.id()
                    + "; conflicting declaration at " + metadata.id());
    }

}
