package ai.loomspan.api;

import java.util.List;
import java.util.Optional;

/**
 * An eager, immutable snapshot of one namespace’s registered skills, sorted by exact name.
 * Discovery is unfiltered; callers must use {@link SkillTemplate#validate} or invoke a skill
 * to enforce authorization.
 */
public interface SkillCatalog
{
    /** Canonical lowercase owning namespace, including for an empty catalog. */
    String namespace();

    /** Opaque process-local identity of this immutable generation. */
    String generationId();

    /** Host-defined label supplied for this generation, preserved exactly when present. */
    Optional<String> publicationVersion();

    List<SkillDescriptor> skills();

    /** Exact local-name lookup in this namespace; malformed names fail and absent names return empty. */
    Optional<SkillDescriptor> skill(String localName);
}
