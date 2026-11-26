package ai.loomspan.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Invokes registered skills with a fresh execution session. Every overload requires the exact
 * namespace and local name separately, independent of any ambient execution namespace.
 * Both components follow {@code [a-z][a-z0-9]*(?:-[a-z0-9]+)*}, contain 1–64 ASCII characters,
 * exclude {@code anthropic} and {@code claude}, and together with two separator characters
 * have a maximum length of 64.
 * Each operation captures the entry's declared reachable namespace generations and checks dependencies,
 * input and entry authorization before execution. Catalog and validation metadata expose both components.
 * Results contain exact text and root session identity. Observers receive an available completed view
 * after successful execution or an execution failure that occurs after session creation.
 * {@link SkillException#getSessionId()} identifies failures after root identity allocation.
 * Post-session access denials expose identity through {@link SkillAccessDeniedException}.
 */
public interface SkillTemplate
{
    /** Checks reachable dependencies, input and entry access with temporary ownership, without reserving admission. */
    void validate(String namespace, String localName, Object input);

    /** Checks reachable dependencies, input and entry access with temporary ownership, without reserving admission. */
    void validate(String namespace, String localName, Map<String, Object> input);

    SkillInvocationResult invoke(String namespace, String localName, Object input);

    SkillInvocationResult invoke(String namespace, String localName, Map<String, Object> input);

    SkillInvocationResult invoke(String namespace, String localName, Object input, Consumer<SkillExecutionView> observer);

    SkillInvocationResult invoke(String namespace, String localName, Map<String, Object> input, Consumer<SkillExecutionView> observer);
}
