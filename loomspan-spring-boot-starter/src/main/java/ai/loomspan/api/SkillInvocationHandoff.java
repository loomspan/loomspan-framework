package ai.loomspan.api;

import java.util.Map;

/**
 * Atomically transfers a prepared root invocation to Loomspan without executing it.
 * Input lookup, conversion, and validation happen before admission. The current authentication is
 * captured at handoff and authorization is still enforced during execution. A pending admission
 * may execute during the remaining framework shutdown budget, but is invalidated at cutoff.
 */
public interface SkillInvocationHandoff
{
    /**
     * Prepares and admits an exact-name invocation with object input.
     *
     * @param skillName exact registered skill name
     * @param input object input to convert and validate
     * @return one pending, single-use admitted invocation
     * @throws SkillException if preparation fails or framework admission is closed
     */
    AdmittedSkillInvocation handoff(String skillName, Object input);

    /**
     * Prepares and admits an exact-name invocation with map input.
     *
     * @param skillName exact registered skill name
     * @param input map input to validate
     * @return one pending, single-use admitted invocation
     * @throws SkillException if preparation fails or framework admission is closed
     */
    AdmittedSkillInvocation handoff(String skillName, Map<String, Object> input);
}
