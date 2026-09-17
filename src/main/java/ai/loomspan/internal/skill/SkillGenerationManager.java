package ai.loomspan.internal.skill;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.core.SkillSource;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Prepares detached complete skill generations and atomically owns the active one. */
public final class SkillGenerationManager implements SmartInitializingSingleton
{
    private final SkillMethodBeanPostProcessor javaSkills;
    private final Supplier<YamlSkillCatalog> yamlCatalogFactory;
    private final SkillInputContractResolver inputs;
    private final ListableBeanFactory beans;
    private final AtomicReference<SkillGeneration> active = new AtomicReference<>();
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
        catalog.afterPropertiesSet();
        return prepareCatalog(catalog);
    }

    public SkillGeneration prepare(List<SkillDocument> documents)
    {
        initializeFixedDependencies();
        YamlSkillCatalog catalog = Objects.requireNonNull(yamlCatalogFactory.get(), "yamlCatalogFactory returned null");
        catalog.loadSupplied(documents);
        return prepareCatalog(catalog);
    }

    private SkillGeneration prepareCatalog(YamlSkillCatalog catalog)
    {
        long ordinal = issuedGenerationIds.incrementAndGet();
        if (ordinal <= 0) throw new IllegalStateException("Skill generation ID space exhausted");
        String generationId = generationNamespace + "-" + ordinal;
        List<YamlSkillDefinition> definitions = catalog.getSkills();
        LinkedHashMap<String, CapabilityMetadata> capabilities = new LinkedHashMap<>();
        for (CapabilityMetadata metadata : fixedJavaCapabilities) putCapability(capabilities, metadata);

        List<YamlSkillDefinition> restDefinitions = definitions.stream().filter(YamlSkillDefinition::rest).toList();
        RestSkillHandler restHandler = restDefinitions.isEmpty() ? null : requireRestHandler(restDefinitions);
        LinkedHashMap<String, YamlSkillDefinition> definitionsByName = new LinkedHashMap<>();
        for (YamlSkillDefinition definition : definitions)
        {
            String name = definition.manifest().getName();
            definitionsByName.put(name, definition);
            String description = definition.manifest().getDescription();
            var contract = inputs.resolveYamlCapability(definition);
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
        for (YamlSkillDefinition definition : definitions)
            for (String child : definition.allowedSkills())
                if (!capabilities.containsKey(child))
                    throw new IllegalStateException("Unknown child skill '" + child + "' in allowed_skills of '"
                            + definition.manifest().getName() + "' at " + definition.source().diagnosticName());

        return new SkillGeneration(generationId, capabilities, definitionsByName);
    }

    public void activate(SkillGeneration candidate)
    {
        active.set(Objects.requireNonNull(candidate, "candidate must not be null"));
    }

    private synchronized void initializeFixedDependencies()
    {
        if (fixedJavaCapabilities != null) return;
        fixedJavaCapabilities = javaSkills.capabilities();
        String[] names = beans.getBeanNamesForType(RestSkillHandler.class, true, false);
        Arrays.sort(names);
        fixedRestHandlerBeanNames = List.of(names);
    }

    private synchronized RestSkillHandler requireRestHandler(List<YamlSkillDefinition> definitions)
    {
        if (fixedRestHandlerBeanNames.isEmpty())
        {
            String resources = definitions.stream().map(definition -> definition.source().diagnosticName())
                    .sorted().reduce((left, right) -> left + ", " + right).orElseThrow();
            throw new IllegalStateException("REST skill manifests require exactly one RestSkillHandler bean; found none for " + resources);
        }
        if (fixedRestHandlerBeanNames.size() > 1)
            throw new IllegalStateException("REST skill manifests require exactly one RestSkillHandler bean; found "
                    + String.join(", ", fixedRestHandlerBeanNames));
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
