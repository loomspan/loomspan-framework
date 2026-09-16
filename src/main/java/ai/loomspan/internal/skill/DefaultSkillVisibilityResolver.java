package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.security.AccessGuard;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class DefaultSkillVisibilityResolver implements SkillVisibilityResolver
{
    private final AccessGuard accessGuard;

    public DefaultSkillVisibilityResolver(AccessGuard accessGuard)
    {
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard must not be null");
    }

    @Override
    public List<CapabilityMetadata> visibleSkillsFor(String currentSkillName, LoomspanSession session, @Nullable Authentication authentication)
    {
        SkillGeneration generation = ExecutionBindingScope.requireCurrent().generation();
        YamlSkillDefinition currentSkill = generation.definition(currentSkillName);

        if (currentSkill == null)
        {
            throw new IllegalArgumentException("Unknown YAML skill '" + currentSkillName + "'");
        }

        LinkedHashSet<CapabilityMetadata> visible = new LinkedHashSet<>();

        for (String allowedSkillName : currentSkill.allowedSkills())
        {
            if (currentSkillName.equals(allowedSkillName))
            {
                continue;
            }
            CapabilityMetadata metadata = generation.capability(allowedSkillName);
            if (metadata == null || !accessGuard.canAccess(metadata, session, authentication))
            {
                continue;
            }
            visible.add(metadata);
        }

        return List.copyOf(visible);
    }
}
