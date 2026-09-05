package ai.loomspan.internal.runtime.observation.catalog;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface RegisteredSkillCatalog
{
    Optional<RegisteredSkillEntry> find(String registeredName);

    List<RegisteredSkillEntry.Summary> listAfter(@Nullable String exclusiveName, int limit);

    int registeredSkillCount();
}
