package ai.loomspan.api;

import java.util.List;
import java.util.Optional;

/**
 * An eager, immutable snapshot of all registered skills, sorted by exact name.
 * Discovery is unfiltered; callers must use {@link SkillTemplate#validate} or invoke a skill
 * to enforce authorization.
 */
public interface SkillCatalog
{
    /** Opaque process-local identity of this immutable generation. */
    String generationId();

    List<SkillDescriptor> skills();

    Optional<SkillDescriptor> skill(String name);
}
