package ai.loomspan.api;

import java.util.Collection;
import java.util.function.Consumer;

/** Framework-owned two-stage update service for the complete skill set. */
public interface SkillReloader
{
    /** Rereads configured resources and checks the complete set without changing framework state. */
    SkillValidationResult validate();

    /** Checks a complete proposed replacement set. Feedback is advisory; prepare checks again. */
    SkillValidationResult validate(Collection<SkillDocument> documents);

    /** Validates and freezes a candidate without changing the active generation. */
    PreparedSkillUpdate prepare();

    /** Validates and freezes a complete replacement YAML set held by the application. */
    PreparedSkillUpdate prepare(Collection<SkillDocument> documents);

    /** Publishes a candidate prepared by this framework instance exactly once. */
    void publish(PreparedSkillUpdate update);

    /** Returns the current immutable catalog without reading skill resources. */
    SkillCatalog snapshot();

    /**
     * Registers a listener for a published generation after it is superseded and all captured
     * invocations and their physical work have returned. Register before staging the initial
     * generation. The process-local ID has no replay, durable delivery, ordering, or retry guarantee.
     * Notifications during shutdown may be omitted. Callbacks may run concurrently on publication,
     * invocation, admission-release, or physical-return threads; they should return promptly and
     * arrange application-owned cleanup elsewhere. Listener failures are reported and isolated.
     * Closing the handle prevents future selection without waiting for an already selected callback.
     *
     * @return a registration handle that removes the listener when closed
     */
    AutoCloseable onGenerationRetired(Consumer<String> listener);
}
