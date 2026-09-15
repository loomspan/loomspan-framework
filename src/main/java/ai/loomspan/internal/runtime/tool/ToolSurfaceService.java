package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ToolSurfaceService
{
    List<CapabilityMetadata> visibleToolsFor(String rootSkillName,
            LoomspanSession session,
            @Nullable Authentication authentication);
}
