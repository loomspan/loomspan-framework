package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface CapabilityInvoker
{
    Object invoke(CapabilityMetadata capability,
            Map<String, Object> arguments,
            LoomspanSession session,
            YamlSkillDefinition definition,
            @Nullable Authentication authentication,
            @Nullable String linkedTaskId);
}
