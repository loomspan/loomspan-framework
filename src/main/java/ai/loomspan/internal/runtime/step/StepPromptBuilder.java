package ai.loomspan.internal.runtime.step;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.MissionInputMessageFormatter;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.outputschema.OutputSchemaPromptAugmentor;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputPromptRenderer;
import ai.loomspan.internal.runtime.input.SkillInputSchemaNode;
import ai.loomspan.internal.skill.YamlSkillManifest;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a concise, task-focused system prompt for each iteration of the plan-step execution loop.
 */
final class StepPromptBuilder
{
    private static final int MAX_LAST_RESULT_CHARS = 1000;
    private static final SkillInputPromptRenderer INPUT_PROMPT_RENDERER = new SkillInputPromptRenderer();
    private static final OutputSchemaPromptAugmentor OUTPUT_SCHEMA_PROMPT_AUGMENTOR = new OutputSchemaPromptAugmentor();

    private StepPromptBuilder()
    {
    }

    static String buildAssignedStepPrompt(ExecutionPlan plan,
            PlanTask assignedTask,
            String objective,
            @Nullable Map<String, Object> missionInput,
            int stepNumber,
            @Nullable String lastToolResult,
            @Nullable String executionSummary,
            List<BoundCapability> visibleTools,
            boolean forceVerboseToolArgumentGuidance)
    {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(assignedTask, "assignedTask must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        String toolName = assignedTask.capabilityName() == null ? "unspecified" : assignedTask.capabilityName();
        String missionContext = MissionInputMessageFormatter.buildMissionContext(objective, null, plan.capabilityName());
        String acceptedPlan = plan.tasks().stream()
                .map(task -> "  - [%s] %s: %s".formatted(task.status(), task.taskId(), task.title()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("  (none)");

        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are executing one coordinator-assigned task from an accepted mission plan.
                Mission context:
                %s
                Step: %d
                Plan: %s (status: %s)

                --- ACCEPTED PLAN CONTEXT (non-actionable) ---
                %s

                --- ASSIGNED TASK ---
                ID: %s
                Title: %s
                Intent: %s
                Expected outputs: %s
                Exact capability/tool: %s
                """.formatted(
                missionContext,
                stepNumber,
                plan.planId(),
                plan.status(),
                acceptedPlan,
                assignedTask.taskId(),
                assignedTask.title(),
                assignedTask.intent() == null ? "(none)" : assignedTask.intent(),
                assignedTask.expectedOutputs().isEmpty() ? "(none)" : String.join(", ", assignedTask.expectedOutputs()),
                toolName));

        String toolArgumentGuidance = formatToolArgumentGuidance(
                assignedTask, visibleTools, forceVerboseToolArgumentGuidance);
        if (toolArgumentGuidance != null)
        {
            sb.append("\n\n--- TOOL ARGUMENT SHAPE ---\n").append(toolArgumentGuidance);
        }
        if (executionSummary != null && !executionSummary.isBlank())
        {
            sb.append("\n\n--- EXECUTION SUMMARY ---\n").append(executionSummary);
        }
        if (lastToolResult != null && !lastToolResult.isBlank())
        {
            String trimmedResult = lastToolResult.length() > MAX_LAST_RESULT_CHARS
                    ? lastToolResult.substring(0, MAX_LAST_RESULT_CHARS) + "... (truncated)"
                    : lastToolResult;
            sb.append("\n\n--- LAST TOOL RESULT ---\n").append(trimmedResult);
        }

        sb.append("""


                --- AVAILABLE TOOL FOR THIS ASSIGNMENT ---
                - %s

                --- YOUR TASK ---
                Return ONLY valid JSON - no markdown, no explanation, no code fences.

                {
                  "stepAction": "CALL_TOOL",
                  "taskId": "%s",
                  "toolName": "%s",
                  "toolArguments": { <arguments for this tool> }
                }

                Rules:
                - Return CALL_TOOL for exactly the assigned task and tool shown above.
                - Do not call the parent mission skill or invent a new tool name.
                - Return raw JSON only.
                """.formatted(toolName, assignedTask.taskId(), toolName));
        return sb.toString();
    }

    static String buildFinalResponsePrompt(ExecutionPlan plan,
            String objective,
            @Nullable Map<String, Object> missionInput,
            int stepNumber,
            @Nullable String lastToolResult,
            @Nullable String executionSummary,
            @Nullable YamlSkillManifest.OutputSchemaManifest outputSchema)
    {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        String missionContext = MissionInputMessageFormatter.buildMissionContext(objective, null, plan.capabilityName());

        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are executing a planned mission step by step.
                Mission context:
                %s
                Step: %d
                Plan: %s (status: %s)
                """.formatted(missionContext, stepNumber, plan.planId(), plan.status()));

        if (executionSummary != null && !executionSummary.isBlank())
        {
            sb.append("\n\n--- EXECUTION SUMMARY ---\n").append(executionSummary);
        }

        if (lastToolResult != null && !lastToolResult.isBlank())
        {
            String trimmedResult = lastToolResult.length() > MAX_LAST_RESULT_CHARS
                    ? lastToolResult.substring(0, MAX_LAST_RESULT_CHARS) + "... (truncated)"
                    : lastToolResult;
            sb.append("\n\n--- LAST TOOL RESULT ---\n").append(trimmedResult);
        }

        sb.append("""


                --- YOUR TASK ---
                All required plan tasks are already COMPLETE.
                Return ONLY valid JSON - no markdown, no explanation, no code fences.
                You must return a FINAL_RESPONSE action. CALL_TOOL is not allowed anymore.

                {
                  "stepAction": "FINAL_RESPONSE",
                  "finalResponse": { <your complete response to the mission objective> }
                }
                """);

        appendOutputSchemaGuidance(sb, outputSchema);
        sb.append("""

                Rules:
                - Do NOT call any tool.
                - finalResponse must be a raw JSON object matching the required schema, not a string containing escaped JSON.
                - Return raw JSON only.
                """);

        return sb.toString();
    }

    public static String buildStepUserMessage(ExecutionPlan plan, String objective)
    {
        return buildStepUserMessage(plan, objective, null);
    }

    public static String buildStepUserMessage(ExecutionPlan plan, String objective, @Nullable Map<String, Object> missionInput)
    {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        return MissionInputMessageFormatter.buildUserMessage(
                MissionInputMessageFormatter.buildMissionContext(objective, null, plan.capabilityName()),
                missionInput);
    }

    private static void appendOutputSchemaGuidance(StringBuilder sb,
            @Nullable YamlSkillManifest.OutputSchemaManifest outputSchema)
    {
        if (outputSchema == null)
        {
            return;
        }
        sb.append("\n\n--- REQUIRED FINAL RESPONSE SHAPE ---\n");
        sb.append(OUTPUT_SCHEMA_PROMPT_AUGMENTOR.renderContract(outputSchema)).append('\n');
    }

    @Nullable
    private static String formatToolArgumentGuidance(PlanTask task,
            List<BoundCapability> visibleTools,
            boolean forceVerboseToolArgumentGuidance)
    {
        if (task.capabilityName() == null || task.capabilityName().isBlank())
        {
            return null;
        }

        BoundCapability tool = visibleTools.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> task.capabilityName().equals(candidate.name()))
                .findFirst()
                .orElse(null);

        if (tool == null)
        {
            return null;
        }

        SkillInputContract contract = tool.metadata().inputContract();
        if (contract.isGeneric())
        {
            return null;
        }

        SkillInputPromptRenderer.DetailLevel detailLevel = forceVerboseToolArgumentGuidance || useVerboseDetail(contract.schema())
                ? SkillInputPromptRenderer.DetailLevel.VERBOSE
                : SkillInputPromptRenderer.DetailLevel.COMPACT;

        return """
                Task %s / tool %s:
                %s
                """.formatted(task.taskId(), task.capabilityName(), INPUT_PROMPT_RENDERER.renderToolArgumentsExample(contract, detailLevel));
    }

    private static boolean useVerboseDetail(SkillInputSchemaNode schema)
    {
        return maxDepth(schema, 1) > 2 || countProperties(schema) > 6;
    }

    private static int maxDepth(SkillInputSchemaNode schema, int depth)
    {
        if (schema == null)
        {
            return depth;
        }
        if (schema.isArray())
        {
            return maxDepth(schema.items(), depth + 1);
        }
        if (!schema.isObject())
        {
            return depth;
        }

        int propertyDepth = schema.properties().values().stream()
                .mapToInt(child -> maxDepth(child, depth + 1))
                .max()
                .orElse(depth);

        int additionalDepth = schema.additionalPropertiesSchema() == null
                ? depth
                : maxDepth(schema.additionalPropertiesSchema(), depth + 1);

        return Math.max(propertyDepth, additionalDepth);
    }

    private static int countProperties(SkillInputSchemaNode schema)
    {
        if (schema == null || !schema.isObject())
        {
            return 0;
        }

        return schema.properties().size()
                + schema.properties().values().stream().mapToInt(StepPromptBuilder::countProperties).sum()
                + countProperties(schema.additionalPropertiesSchema());
    }
}
