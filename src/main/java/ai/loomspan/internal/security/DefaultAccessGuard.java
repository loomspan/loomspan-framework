package ai.loomspan.internal.security;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.skill.SkillDependencyValidator;
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

        return hasCallAuthority(capability, session) && evaluator.canAccess(capability.accessPolicy(), authentication);
    }

    @Override
    public void checkAccess(CapabilityMetadata capability, LoomspanSession session, @Nullable Authentication invocationAuthentication)
    {
        Objects.requireNonNull(capability, "capability must not be null");
        if (!hasCallAuthority(capability, session))
            throw new AccessDeniedException("Call to capability '" + capability.name() + "' is not authorized from the current captured source");
        evaluator.checkAccess(capability.name(), capability.accessPolicy(),
                resolveAuthentication(invocationAuthentication, session));
    }

    private boolean hasCallAuthority(CapabilityMetadata target, LoomspanSession session)
    {
        var binding = ExecutionBindingScope.requireCurrent();
        if (binding.session() != session) return false;
        var selected = binding.snapshot().generations().get(target.identity().namespace());
        if (selected == null || !selected.owns(target)) return false;
        if (binding.mission() == null)
            return binding.isEntryTarget(target);
        var source = binding.generation().definition(binding.mission().skillName());
        return source != null && source.allowedSkills().contains(target.name())
                && SkillDependencyValidator.allowsNamespace(binding.currentNamespace(), selected.namespace(), selected.visibility());
    }

}
