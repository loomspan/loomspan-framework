package ai.loomspan.api;

/** Opaque caller-held candidate; only a framework-created instance can be published. */
public interface PreparedSkillUpdate extends AutoCloseable
{
    String generationId();
    SkillCatalog snapshot();

    /** Releases resources held by an unpublished candidate. Published generations own their resources. */
    @Override default void close() {}
}
