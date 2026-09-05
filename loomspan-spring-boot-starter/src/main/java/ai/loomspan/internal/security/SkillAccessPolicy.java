package ai.loomspan.internal.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable effective policy; an empty declared RolesAllowed is still a role policy. */
public record SkillAccessPolicy(Kind kind, List<String> roles, boolean annotated)
{
    public enum Kind { UNRESTRICTED, DENIED, ROLES }

    public SkillAccessPolicy
    {
        Objects.requireNonNull(kind, "kind");
        roles = List.copyOf(roles);
        if (kind != Kind.ROLES && !roles.isEmpty())
        {
            throw new IllegalArgumentException("Only role policies can contain roles");
        }
    }

    public static SkillAccessPolicy unrestricted()
    {
        return new SkillAccessPolicy(Kind.UNRESTRICTED, List.of(), false);
    }

    public static SkillAccessPolicy permitAll()
    {
        return new SkillAccessPolicy(Kind.UNRESTRICTED, List.of(), true);
    }

    public static SkillAccessPolicy denied()
    {
        return new SkillAccessPolicy(Kind.DENIED, List.of(), true);
    }

    public static SkillAccessPolicy roles(Collection<String> roles)
    {
        return new SkillAccessPolicy(Kind.ROLES, List.copyOf(roles), true);
    }

    public static SkillAccessPolicy yamlRoles(Collection<String> roles)
    {
        return roles.isEmpty() ? unrestricted() : new SkillAccessPolicy(Kind.ROLES, List.copyOf(roles), false);
    }
}
