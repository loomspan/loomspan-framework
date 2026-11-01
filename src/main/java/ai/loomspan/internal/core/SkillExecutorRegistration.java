package ai.loomspan.internal.core;

import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.internal.security.SkillAccessPolicy;
import java.util.function.Function;

/** Fixed reference-only registration from the shared Java discovery pass; never a callable tool. */
public record SkillExecutorRegistration(SkillIdentity identity, String config,
        SkillAccessPolicy accessPolicy, SkillSource source, Function<SkillExecutorInvocation, String> invoker)
{
    public String name() { return identity.toString(); }
}
