package ai.loomspan.api;

/**
 * A YAML skill document supplied by the application for a complete skill update.
 * {@code sourceName} is an exact diagnostic label, such as {@code Orders import #42}
 * or {@code Orders/lookup.yaml}. It does not determine the skill's namespace or name.
 * A host loading a file or archive checks its definition path before constructing this document.
 */
public record SkillDocument(String sourceName, String yaml)
{
}
