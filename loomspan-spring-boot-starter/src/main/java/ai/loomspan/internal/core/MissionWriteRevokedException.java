package ai.loomspan.internal.core;

/** Internal control flow used when work attempts to start after mission cutoff. */
public final class MissionWriteRevokedException extends RuntimeException
{
    public MissionWriteRevokedException(String skillName)
    {
        super("Loomspan execution is no longer writable for mission '" + skillName + "'.");
    }
}
