package ai.loomspan.internal.skill;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillDocument;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Prepares detached complete skill generations and atomically owns the active one. */
public final class SkillGenerationManager implements SmartInitializingSingleton
{
    private static final Logger log = LoggerFactory.getLogger(SkillGenerationManager.class);
    private final SkillMethodBeanPostProcessor javaSkills;
    private final Supplier<YamlSkillCatalog> yamlCatalogFactory;
    private final SkillInputContractResolver inputs;
    private final ListableBeanFactory beans;
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
        this.javaSkills = Objects.requireNonNull(javaSkills, "javaSkills must not be null");
        this.yamlCatalogFactory = Objects.requireNonNull(yamlCatalogFactory, "yamlCatalogFactory must not be null");
        this.inputs = Objects.requireNonNull(inputs, "inputs must not be null");
        this.beans = Objects.requireNonNull(beans, "beans must not be null");
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
        YamlSkillCatalog catalog = Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null");
        return prepareCatalog(check(catalog.checkedConfigured(true)));
    }

    public SkillGeneration prepare(List<SkillDocument> documents)
    {
        initializeFixedDependencies();
        YamlSkillCatalog catalog = Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null");
        return prepareCatalog(check(catalog.checkedSupplied(documents, true)));
    }

    public SkillValidationResult validate()
    {
        requireInitialized();
        YamlSkillCatalog catalog = Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null");
        return validationResult(check(catalog.checkedConfigured(false)));
    }

    public SkillValidationResult validate(List<SkillDocument> documents)
    {
        requireInitialized();
        YamlSkillCatalog catalog = Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null");
        return validationResult(check(catalog.checkedSupplied(documents, false)));
    }

    private void requireInitialized()
    {
        if (fixedJavaCapabilities == null || fixedRestHandlerBeanNames == null)
            throw new IllegalStateException("Skill validation requires completed framework startup");
    }

    private record CheckedSet(List<YamlSkillDefinition> definitions,
            Map<YamlSkillDefinition, SkillInputContract> contracts, List<SkillValidationIssue> issues)
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
        return new CheckedSet(documents.definitions(), Map.copyOf(contracts), List.copyOf(issues));
    }

    private static SkillValidationIssue error(YamlSkillDefinition definition, String path, String message)
    {
        return new SkillValidationIssue(SkillValidationIssue.Severity.ERROR,
                definition.source().diagnosticName(), definition.manifest().getName(), path, message);
    }

    private SkillGeneration prepareCatalog(CheckedSet checked)
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
                    new CapabilityToolDescriptor(name, description, inputs.toJsonSchema(contract)),
                    contract, new SkillSource(definition.source().diagnosticName(), null, null));
            putCapability(capabilities, metadata);
        }
        return new SkillGeneration(generationId, capabilities, definitionsByName);
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
            published.put(candidate.id(), new PublishedGeneration(candidate.id()));
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
        if (!deliveryEnabled.getAsBoolean()) return null;
        return new Retirement(state.id, retirementListeners.stream().map(entry -> entry.listener).toList());
    }

    public static void dispatch(Retirement retirement)
    {
        if (retirement == null) return;
        for (Consumer<String> listener : retirement.listeners)
        {
            try { listener.accept(retirement.id); }
            catch (Throwable failure) { log.warn("Skill generation retirement listener failed for {}", retirement.id, failure); }
        }
    }

    private static final class PublishedGeneration
    {
        private final String id;
        private int owners;
        private boolean superseded;

        private PublishedGeneration(String id) { this.id = id; }
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

    public record Retirement(String id, List<Consumer<String>> listeners) {}

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
