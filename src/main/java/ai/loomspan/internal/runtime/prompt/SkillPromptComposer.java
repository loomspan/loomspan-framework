package ai.loomspan.internal.runtime.prompt;

import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

public final class SkillPromptComposer
{
    private static final String DEFAULT_EXECUTION_PROMPT =
            "Execute the mission using only the visible YAML tools when needed.";

    private SkillPromptComposer()
    {
    }

    public static SkillPromptComposition composeDefaultExecutionPrompt(YamlSkillDefinition definition)
    {
        return compose(definition.prompt(), DEFAULT_EXECUTION_PROMPT,
                "default_execution_prompt",
                "skill_prompt_plus_default_execution_prompt");
    }

    public static SkillPromptComposition composePlannedExecutionPrompt(YamlSkillDefinition definition, String plannedPrompt)
    {
        return compose(definition.prompt(), plannedPrompt,
                "planned_execution_prompt",
                "skill_prompt_plus_planned_execution_prompt");
    }

    public static SkillPromptComposition composePlanningPrompt(YamlSkillDefinition definition, String planningPrompt)
    {
        String skillPrompt = definition.prompt();
        String callPrompt = StringUtils.hasText(skillPrompt)
                ? "Use the skill instructions above to decide the right plan.\n\n" + planningPrompt
                : planningPrompt;
        return compose(skillPrompt, callPrompt,
                "planning_prompt",
                "skill_prompt_plus_planning_prompt");
    }

    public static SkillPromptComposition composeStepExecutionPrompt(YamlSkillDefinition definition, String stepPrompt)
    {
        return compose(definition.prompt(), stepPrompt,
                "step_execution_prompt",
                "skill_prompt_plus_step_execution_prompt");
    }

    private static SkillPromptComposition compose(@Nullable String skillPrompt,
            String callPrompt,
            String defaultDescriptor,
            String skillPromptDescriptor)
    {
        if (!StringUtils.hasText(skillPrompt))
        {
            return new SkillPromptComposition(callPrompt, false, null, defaultDescriptor);
        }
        return new SkillPromptComposition(skillPrompt + "\n\n" + callPrompt, true, skillPrompt, skillPromptDescriptor);
    }

    public static String defaultExecutionPrompt()
    {
        return DEFAULT_EXECUTION_PROMPT;
    }
}
