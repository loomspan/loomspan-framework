package ai.loomspan.internal.core;

public enum JournalEntryType
{
    SKILL_STARTED,
    SKILL_FINISHED,
    THOUGHT,
    PLAN_CREATED,
    PLAN_UPDATED,
    LINTER,
    OUTPUT_SCHEMA,
    MODEL_ATTEMPT_FAILURE,
    TOOL_CALL,
    UNPLANNED_TOOL_EXECUTION,
    TOOL_FAILURE,
    TOOL_RESULT,
    STEP_FAILURE,
    ERROR
}
