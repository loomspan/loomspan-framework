package ai.loomspan.api;

import java.util.List;
import java.util.Objects;

public record SkillExecutionView(String sessionId, List<SkillExecutionEvent> events)
{
    public SkillExecutionView
    {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (sessionId.isBlank())
        {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        events = events == null ? List.of() : List.copyOf(events);
    }
}
