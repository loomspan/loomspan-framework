package ai.loomspan.api;

import java.util.Objects;

/** Explicit application signal carrying exact, nonblank corrective feedback. */
public class RecoverableSkillException extends SkillException
{
    public RecoverableSkillException(String message)
    {
        super(requireMessage(message));
    }

    public RecoverableSkillException(String message, Throwable cause)
    {
        super(requireMessage(message), cause);
    }

    private static String requireMessage(String message)
    {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        return message;
    }
}
