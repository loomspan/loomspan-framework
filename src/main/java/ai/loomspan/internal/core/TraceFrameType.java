package ai.loomspan.internal.core;

public enum TraceFrameType
{
    ROOT_MISSION,
    SKILL_EXECUTION,
    MODEL_CALL,
    TOOL_INVOCATION,
    RETRY,
    PLANNING,
    STEP_EXECUTION
}
