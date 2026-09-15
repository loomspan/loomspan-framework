package ai.loomspan.internal.skill;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.Arrays;
import java.util.List;

/** Completes every declaration source before references or snapshots are resolved. */
public class YamlSkillCapabilityRegistrar implements SmartInitializingSingleton
{
    private final CapabilityRegistry registry;
    private final SkillMethodBeanPostProcessor javaSkills;
    private final YamlSkillCatalog catalog;
    private final SkillInputContractResolver inputs;
    private final ListableBeanFactory beans;
    private boolean complete;

    public YamlSkillCapabilityRegistrar(CapabilityRegistry registry, SkillMethodBeanPostProcessor javaSkills,
            YamlSkillCatalog catalog, SkillInputContractResolver inputs, ListableBeanFactory beans)
    {
        this.registry = registry;
        this.javaSkills = javaSkills;
        this.catalog = catalog;
        this.inputs = inputs;
        this.beans = beans;
    }

    @Override
    public void afterSingletonsInstantiated() { completeRegistration(); }

    public synchronized void completeRegistration()
    {
        if (complete) return;
        javaSkills.completeDiscovery();
        List<YamlSkillDefinition> definitions = catalog.getSkills();
        List<YamlSkillDefinition> restDefinitions = definitions.stream().filter(YamlSkillDefinition::rest).toList();
        RestSkillHandler restHandler = restDefinitions.isEmpty() ? null : requireRestHandler(restDefinitions);
        for (YamlSkillDefinition definition : definitions)
        {
            String name = definition.manifest().getName();
            String description = definition.manifest().getDescription();
            var contract = inputs.resolveYamlCapability(definition);
            boolean rest = definition.rest();
            RestSkillHandler handler = restHandler;
            registry.register(name, new CapabilityMetadata((rest ? "rest:" : "yaml:") + definition.resource().getDescription(),
                    name, description, rest ? SkillExecutionDescriptor.none()
                            : SkillExecutionDescriptor.from(definition.requireExecutionConfiguration()),
                    SkillAccessPolicy.yamlRoles(definition.rbacRoles()),
                    rest ? arguments -> invokeRest(handler, name, arguments)
                            : arguments -> { throw new IllegalStateException("YAML skills require model execution"); },
                    rest ? CapabilityKind.REST_SKILL : CapabilityKind.YAML_SKILL,
                    new CapabilityToolDescriptor(name, description, inputs.toJsonSchema(contract)),
                    contract, new SkillSource(definition.resource().getDescription(), null, null)));
        }
        for (YamlSkillDefinition definition : definitions)
            for (String child : definition.allowedSkills())
                if (registry.getCapability(child) == null)
                    throw new IllegalStateException("Unknown child skill '" + child + "' in allowed_skills of '"
                            + definition.manifest().getName() + "' at " + definition.resource().getDescription());
        complete = true;
    }

    private RestSkillHandler requireRestHandler(List<YamlSkillDefinition> definitions)
    {
        String[] names = beans.getBeanNamesForType(RestSkillHandler.class, true, false);
        Arrays.sort(names);
        if (names.length == 0)
        {
            String resources = definitions.stream().map(definition -> definition.resource().getDescription())
                    .sorted().reduce((left, right) -> left + ", " + right).orElseThrow();
            throw new IllegalStateException("REST skill manifests require exactly one RestSkillHandler bean; found none for " + resources);
        }
        if (names.length > 1)
        {
            throw new IllegalStateException("REST skill manifests require exactly one RestSkillHandler bean; found "
                    + String.join(", ", names));
        }
        return beans.getBean(names[0], RestSkillHandler.class);
    }

    private static String invokeRest(RestSkillHandler handler, String name, java.util.Map<String, Object> arguments)
    {
        String result = handler.handle(new RestSkillInvocation(name, arguments));
        if (result == null) throw new IllegalStateException("REST skill '" + name + "' handler returned null");
        return result;
    }
}
