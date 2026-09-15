package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface CapabilityBindingFactory
{
    List<BoundCapability> bind(LoomspanSession session,
            YamlSkillDefinition definition,
            List<CapabilityMetadata> capabilities,
            @Nullable Authentication authentication);
}
