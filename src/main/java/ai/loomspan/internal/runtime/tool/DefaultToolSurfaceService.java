package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.skill.SkillVisibilityResolver;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Objects;

public class DefaultToolSurfaceService implements ToolSurfaceService
{
    private final SkillVisibilityResolver skillVisibilityResolver;

    public DefaultToolSurfaceService(SkillVisibilityResolver skillVisibilityResolver)
    {
        this.skillVisibilityResolver = Objects.requireNonNull(skillVisibilityResolver, "skillVisibilityResolver must not be null");
    }

    @Override
    public List<CapabilityMetadata> visibleToolsFor(String rootSkillName, LoomspanSession session, @Nullable Authentication authentication)
    {
        return skillVisibilityResolver.visibleSkillsFor(rootSkillName, session, authentication);
    }
}
