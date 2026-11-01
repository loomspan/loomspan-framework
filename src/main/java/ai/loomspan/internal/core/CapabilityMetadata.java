package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import org.springframework.lang.Nullable;

import ai.loomspan.internal.security.SkillAccessPolicy;

import java.util.Objects;

public record CapabilityMetadata(
        String id,
        SkillIdentity identity,
        String description,
        SkillExecutionDescriptor skillExecution,
        SkillAccessPolicy accessPolicy,
        CapabilityInvoker invoker,
        SkillKind kind,
        CapabilityToolDescriptor tool,
        SkillInputContract inputContract,
        @Nullable SkillSource source)
{
    public CapabilityMetadata
    {
        id = requireNonBlank(id, "id");
        Objects.requireNonNull(identity, "identity must not be null");
        String name = identity.toString();
        description = requireNonBlank(description, "description");
        skillExecution = skillExecution == null ? SkillExecutionDescriptor.none() : skillExecution;
        accessPolicy = accessPolicy == null ? SkillAccessPolicy.unrestricted() : accessPolicy;
        invoker = Objects.requireNonNull(invoker, "invoker must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        tool = tool == null ? CapabilityToolDescriptor.generic(name, description) : tool;
        if (!name.equals(tool.name()))
        {
            throw new IllegalArgumentException("tool.name must match the registered skill name");
        }
        inputContract = inputContract == null ? SkillInputContract.genericObject() : inputContract;

    }

    public String name() { return identity.toString(); }

    public CapabilityMetadata(String id, String name, String description,
            SkillExecutionDescriptor skillExecution, SkillAccessPolicy accessPolicy,
            CapabilityInvoker invoker, SkillKind kind, CapabilityToolDescriptor tool,
            SkillInputContract inputContract, @Nullable SkillSource source)
    {
        this(id, SkillIdentity.parse(name), description, skillExecution, accessPolicy, invoker, kind, tool, inputContract, source);
    }

    public CapabilityMetadata(String id,
            String name,
            String description,
            SkillExecutionDescriptor skillExecution,
            SkillAccessPolicy accessPolicy,
            CapabilityInvoker invoker,
            SkillKind kind,
            CapabilityToolDescriptor tool,
            @Nullable SkillSource source)
    {
        this(id, name, description, skillExecution, accessPolicy, invoker, kind, tool, null, source);
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
