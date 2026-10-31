package ai.loomspan.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Invokes registered skills with a fresh execution session. Every overload accepts a local name
 * in {@code default}, or an explicit qualified {@code namespace-localName}. Local input always
 * selects {@code default}, independent of any ambient execution namespace; there is no fallback.
 * Each operation captures the entry's declared reachable namespace generations and checks dependencies,
 * input and entry authorization before execution. Catalog descriptor names pass directly to this API.
 * Results are text; observers receive an available completed view after successful execution or
 * an execution failure that occurs after session creation.
 */
public interface SkillTemplate
{
    /** Checks reachable dependencies, input and entry access with temporary ownership, without reserving admission. */
    void validate(String skillName, Object input);

    /** Checks reachable dependencies, input and entry access with temporary ownership, without reserving admission. */
    void validate(String skillName, Map<String, Object> input);

    String invoke(String skillName, Object input);

    String invoke(String skillName, Map<String, Object> input);

    String invoke(String skillName, Object input, Consumer<SkillExecutionView> observer);

    String invoke(String skillName, Map<String, Object> input, Consumer<SkillExecutionView> observer);
}
