package ai.loomspan.api;

/** Application-provided handler for YAML-declared REST leaf skills. */
@FunctionalInterface
public interface RestSkillHandler
{
    String handle(RestSkillInvocation invocation);
}
