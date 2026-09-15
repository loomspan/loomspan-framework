package ai.loomspan.internal.runtime.step;

/**
 * The set of valid action types a model may propose during a single step of a plan-step execution loop.
 */
enum StepActionType
{
    /**
     * The model wants to invoke a tool against a specific plan task.
     */
    CALL_TOOL,

    /**
     * The model believes the mission is complete and proposes a final response.
     */
    FINAL_RESPONSE
}
