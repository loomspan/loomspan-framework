package ai.loomspan.api;

/** Framework-owned two-stage update service for the complete skill set. */
public interface SkillReloader
{
    /** Validates and freezes a candidate without changing the active generation. */
    PreparedSkillUpdate prepare();

    /** Publishes a candidate prepared by this framework instance exactly once. */
    void publish(PreparedSkillUpdate update);

    /** Returns the current immutable catalog without reading skill resources. */
    SkillCatalog snapshot();
}
