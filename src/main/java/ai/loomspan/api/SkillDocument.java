package ai.loomspan.api;

/** A YAML skill document supplied by the application for a complete skill update. */
public record SkillDocument(String sourceName, String yaml)
{
}
