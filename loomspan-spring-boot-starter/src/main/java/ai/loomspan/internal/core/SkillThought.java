package ai.loomspan.internal.core;

import java.time.Instant;
import java.util.Objects;

record SkillThought(Instant timestamp, JournalLevel level, String content)
{
    public SkillThought
    {
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        level = Objects.requireNonNull(level, "level must not be null");
        content = Objects.requireNonNull(content, "content must not be null");
    }
}
