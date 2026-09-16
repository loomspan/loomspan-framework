package ai.loomspan.api;

/** Operational failure while preparing or publishing a skill generation. */
public final class SkillReloadException extends SkillException
{
    public SkillReloadException(String message) { super(message); }
    public SkillReloadException(String message, Throwable cause) { super(message, cause); }
}
