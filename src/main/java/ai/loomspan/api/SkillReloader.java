package ai.loomspan.api;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/** Framework-owned two-stage update service for complete namespace generations. */
public interface SkillReloader
{
    /** Checks a complete namespace replacement without creating provider clients; visibility defaults to Private. */
    SkillValidationResult validate(String namespace, Collection<SkillDocument> documents, ExecutionConfiguration configuration);
    SkillValidationResult validate(String namespace, Collection<SkillDocument> documents, ExecutionConfiguration configuration,
            NamespaceVisibility visibility);

    /** Prepares a detached complete replacement; omitted visibility is Private even on replacement. */
    PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents, ExecutionConfiguration configuration);
    PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents, ExecutionConfiguration configuration,
            NamespaceVisibility visibility);
    /** Uses a copied credential map without Environment fallback. */
    PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents, ExecutionConfiguration configuration,
            Map<String, String> credentialValues);
    PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents, ExecutionConfiguration configuration,
            NamespaceVisibility visibility, Map<String, String> credentialValues);

    /** Withdraws exactly the expected active generation without waiting for retained work. */
    void remove(String namespace, String expectedGenerationId);

    /** Publishes a candidate prepared by this framework instance exactly once. */
    void publish(PreparedSkillUpdate update);

    /** Selects the normalized namespace once; missing namespaces fail. Returned metadata remains immutable. */
    SkillCatalog snapshot(String namespace);

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
