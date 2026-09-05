package ai.loomspan.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Invokes registered Java or YAML skills by exact name with a fresh execution session.
 * Results are text; observers receive a completed view only after successful execution.
 */
public interface SkillTemplate
{
    String invoke(String skillName, Object input);

    String invoke(String skillName, Map<String, Object> input);

    String invoke(String skillName, Object input, Consumer<SkillExecutionView> observer);

    String invoke(String skillName, Map<String, Object> input, Consumer<SkillExecutionView> observer);
}
