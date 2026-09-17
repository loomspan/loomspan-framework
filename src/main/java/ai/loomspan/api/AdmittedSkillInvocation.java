package ai.loomspan.api;

import java.util.function.Consumer;

/**
 * A single-use root invocation whose ownership has already been transferred to Loomspan.
 * Exactly one execution may claim the admission. Calls that lose to execution, release, or
 * framework cutoff fail without starting skill work. Applications must {@link #release()} an
 * admission they abandon before execution; release is idempotent and harmless after execution
 * has claimed the admission.
 */
public interface AdmittedSkillInvocation
{
    /**
     * Returns the nonblank, process-local ID of the generation captured during preparation,
     * before input conversion and validation. The ID remains available after execution,
     * failure, release, or framework cutoff, even if a newer generation is published.
     * Reading it has no admission or ownership side effects and does not guarantee that an
     * application-owned mapping for this ID is still available.
     *
     * @return the captured generation ID
     */
    String generationId();

    /**
     * Executes the admitted invocation on the calling thread.
     *
     * @return the skill result as text
     * @throws org.springframework.security.access.AccessDeniedException if the captured caller is not authorized
     * @throws SkillException if the admission is no longer executable or execution otherwise fails
     */
    String invoke();

    /**
     * Executes the admitted invocation and delivers the available completed view before framework
     * ownership is released.
     *
     * @param observer callback for the available completed execution view; may be {@code null}
     * @return the skill result as text
     * @throws org.springframework.security.access.AccessDeniedException if the captured caller is not authorized
     * @throws SkillException if the admission is no longer executable or execution otherwise fails
     */
    String invoke(Consumer<SkillExecutionView> observer);

    /** Releases this admission if it is still pending. */
    void release();
}
