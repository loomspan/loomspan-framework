package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** Completes both declaration sources before references or snapshots are resolved. */
public class YamlSkillCapabilityRegistrar implements SmartInitializingSingleton
{
    private final CapabilityRegistry registry;
    private final SkillMethodBeanPostProcessor javaSkills;
    private final YamlSkillCatalog catalog;
    private final SkillInputContractResolver inputs;
    private boolean complete;

    public YamlSkillCapabilityRegistrar(CapabilityRegistry registry, SkillMethodBeanPostProcessor javaSkills,
            YamlSkillCatalog catalog, SkillInputContractResolver inputs)
    {
        this.registry = registry;
        this.javaSkills = javaSkills;
        this.catalog = catalog;
        this.inputs = inputs;
    }

    @Override
    public void afterSingletonsInstantiated() { completeRegistration(); }

    public synchronized void completeRegistration()
    {
        if (complete) return;
        javaSkills.completeDiscovery();
        for (YamlSkillDefinition definition : catalog.getSkills())
        {
            String name = definition.manifest().getName();
            String description = definition.manifest().getDescription();
            var contract = inputs.resolveYamlCapability(definition);
            registry.register(name, new CapabilityMetadata("yaml:" + definition.resource().getDescription(),
                    name, description, SkillExecutionDescriptor.from(definition.requireExecutionConfiguration()),
                    SkillAccessPolicy.yamlRoles(definition.rbacRoles()),
                    arguments -> { throw new IllegalStateException("YAML skills require model execution"); },
                    CapabilityKind.YAML_SKILL, new CapabilityToolDescriptor(name, description, inputs.toJsonSchema(contract)),
                    contract, new SkillSource(definition.resource().getDescription(), null, null)));
        }
        for (YamlSkillDefinition definition : catalog.getSkills())
            for (String child : definition.allowedSkills())
                if (registry.getCapability(child) == null)
                    throw new IllegalStateException("Unknown child skill '" + child + "' in allowed_skills of '"
                            + definition.manifest().getName() + "' at " + definition.resource().getDescription());
        complete = true;
    }
}
