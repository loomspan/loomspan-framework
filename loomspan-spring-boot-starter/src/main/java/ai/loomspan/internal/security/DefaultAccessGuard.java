package ai.loomspan.internal.security;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Objects;

public class DefaultAccessGuard implements AccessGuard
{
    private final SkillRoleEvaluator evaluator;

    public DefaultAccessGuard()
    {
        this(new SkillRoleEvaluator(null, null));
    }

    public DefaultAccessGuard(SkillRoleEvaluator evaluator)
    {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }
    @Override
    @Nullable
    public Authentication resolveAuthentication(@Nullable Authentication invocationAuthentication, LoomspanSession session)
    {
        Objects.requireNonNull(session, "session must not be null");
        // Explicit invocation auth is authoritative; session auth is a fallback for nested or detached execution.
        return invocationAuthentication != null ? invocationAuthentication : session.getAuthentication().orElse(null);
    }

    @Override
    public boolean canAccess(CapabilityMetadata capability, LoomspanSession session, @Nullable Authentication invocationAuthentication)
    {
        Objects.requireNonNull(capability, "capability must not be null");
        Authentication authentication = resolveAuthentication(invocationAuthentication, session);

        return evaluator.canAccess(capability.accessPolicy(), authentication);
    }

    @Override
    public void checkAccess(CapabilityMetadata capability, LoomspanSession session, @Nullable Authentication invocationAuthentication)
    {
        if (!canAccess(capability, session, invocationAuthentication))
        {
            throw new AccessDeniedException("Access denied for capability '" + capability.name() + "'");
        }
    }

}
