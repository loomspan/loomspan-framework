package ai.loomspan.testkit;

import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.SkillExecutorInvocation;
import java.util.function.Function;

/** Real annotated executor for tests whose assertions concern orchestration and input binding. */
public class FixtureExecutor {
    private final Function<SkillExecutorInvocation, String> action;
    public FixtureExecutor(Function<SkillExecutorInvocation, String> action) { this.action = action; }
    @SkillExecutor(name = "fixture")
    public String execute(SkillExecutorInvocation invocation) { return action.apply(invocation); }
}
