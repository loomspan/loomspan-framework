package ai.loomspan.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Invokes registered Java, REST, or model-backed YAML skills by exact name with a fresh execution session.
 * Results are text; observers receive an available completed view after successful execution or
 * an execution failure that occurs after session creation.
 */
public interface SkillTemplate
{
    /** Validates and authorizes a root request without reserving execution admission. */
    void validate(String skillName, Object input);

    /** Validates and authorizes a root request without reserving execution admission. */
    void validate(String skillName, Map<String, Object> input);

    String invoke(String skillName, Object input);

    String invoke(String skillName, Map<String, Object> input);

    String invoke(String skillName, Object input, Consumer<SkillExecutionView> observer);

    String invoke(String skillName, Map<String, Object> input, Consumer<SkillExecutionView> observer);
}
