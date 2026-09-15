package ai.loomspan.internal.core;

/** Declaration location for diagnostics; never an invocation alias. */
public record SkillSource(String sourcePath, String beanName, String method) {}
