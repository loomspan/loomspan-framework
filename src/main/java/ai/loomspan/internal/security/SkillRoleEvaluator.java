package ai.loomspan.internal.security;

import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.skill.SkillAdmissionSnapshot;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthoritiesAuthorizationManager;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

public final class SkillRoleEvaluator
{
    private final AuthoritiesAuthorizationManager manager = new AuthoritiesAuthorizationManager();
    private final String rolePrefix;

    public SkillRoleEvaluator(@Nullable GrantedAuthorityDefaults defaults, @Nullable RoleHierarchy hierarchy)
    {
        rolePrefix = defaults == null ? "ROLE_" : defaults.getRolePrefix();
        if (hierarchy != null) manager.setRoleHierarchy(hierarchy);
    }

    public boolean canAccess(SkillAccessPolicy policy, @Nullable Authentication authentication)
    {
        return switch (policy.kind())
        {
            case UNRESTRICTED -> true;
            case DENIED -> false;
            case ROLES -> authentication != null && manager.authorize(() -> authentication,
                    policy.roles().stream().map(role -> rolePrefix + role).toList()).isGranted();
        };
    }

    public boolean canAccess(CapabilityMetadata capability, SkillAdmissionSnapshot snapshot,
            @Nullable Authentication authentication)
    {
        var definition = snapshot.generation(capability.identity().namespace()).definition(capability.name());
        // Resolve the required admitted binding even when YAML denies: missing admission is an invariant failure.
        if (definition != null && definition.form() == YamlSkillDefinition.Form.EXECUTOR)
        {
            var executorPolicy = snapshot.executorBinding(capability.name()).registration().accessPolicy();
            return canAccess(capability.accessPolicy(), authentication) && canAccess(executorPolicy, authentication);
        }
        return canAccess(capability.accessPolicy(), authentication);
    }

    public void checkAccess(CapabilityMetadata capability, SkillAdmissionSnapshot snapshot,
            @Nullable Authentication authentication)
    {
        if (!canAccess(capability, snapshot, authentication))
        {
            throw new AccessDeniedException("Access denied for capability '" + capability.name() + "'");
        }
    }
}
