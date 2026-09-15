package ai.loomspan.testkit;

import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.tool.BoundCapability;

import java.util.Set;

public final class TestBoundCapabilities {
    private TestBoundCapabilities() {
    }

    public static BoundCapability capability(String name) {
        return capability(name, "{}", SkillInputContract.genericObject());
    }

    public static BoundCapability capability(String name, String inputSchema) {
        return capability(name, inputSchema, new SkillInputContractResolver().resolveFromToolSchema(inputSchema));
    }

    public static BoundCapability describedCapability(String name, String description) {
        return capability(name, description, "{}", SkillInputContract.genericObject());
    }

    public static BoundCapability contractAware(String name, String inputSchema, String contractSchema) {
        return capability(name, inputSchema, new SkillInputContractResolver().resolveFromToolSchema(contractSchema));
    }

    private static BoundCapability capability(String name, String inputSchema, SkillInputContract contract) {
        return capability(name, name, inputSchema, contract);
    }

    private static BoundCapability capability(String name, String description, String inputSchema, SkillInputContract contract) {
        CapabilityMetadata metadata = new CapabilityMetadata(
                "test:" + name,
                name,
                description == null || description.isBlank() ? name : description,
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()),
                input -> null,
                CapabilityKind.YAML_SKILL,
                new CapabilityToolDescriptor(name, description == null || description.isBlank() ? name : description, inputSchema),
                contract,
                null);
        return new BoundCapability(metadata, (arguments, linkedTaskId) -> null);
    }
}
