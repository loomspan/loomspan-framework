package ai.loomspan.internal.security;

import org.springframework.lang.Nullable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthoritiesAuthorizationManager;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;

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
}
