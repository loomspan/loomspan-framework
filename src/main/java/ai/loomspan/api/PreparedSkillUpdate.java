package ai.loomspan.api;

/** Opaque caller-held candidate; only a framework-created instance can be published. */
public interface PreparedSkillUpdate
{
    String generationId();
    SkillCatalog snapshot();
}
