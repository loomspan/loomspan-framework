package ai.loomspan.api;

import java.util.Map;

/**
 * Atomically transfers a prepared root invocation to Loomspan without executing it.
 * Every target supplies its exact namespace and local name separately, using the
 * component grammar and combined limit described by {@link SkillTemplate}. Reachable generation capture,
 * conversion, dependency/input validation
 * and entry authorization happen before admission. The current authentication is
 * captured at handoff and authorization is still enforced during execution. A pending admission
 * may execute during the remaining framework shutdown budget, but is invalidated at cutoff.
 */
public interface SkillInvocationHandoff
{
    /**
     * Prepares and admits an exact-name invocation with object input.
     *
     * @param namespace exact owning namespace
     * @param localName exact local skill name
     * @param input object input to convert and validate
     * @return one pending, single-use admitted invocation
     * @throws SkillException if preparation fails or framework admission is closed
     */
    AdmittedSkillInvocation handoff(String namespace, String localName, Object input);

    /**
     * Prepares and admits an exact-name invocation with map input.
     *
     * @param namespace exact owning namespace
     * @param localName exact local skill name
     * @param input map input to validate
     * @return one pending, single-use admitted invocation
     * @throws SkillException if preparation fails or framework admission is closed
     */
    AdmittedSkillInvocation handoff(String namespace, String localName, Map<String, Object> input);
}
