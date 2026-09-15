package ai.loomspan.internal.core;

/** Internal primary failure installed when the framework shutdown budget expires. */
public final class FrameworkShutdownException extends RuntimeException
{
    public FrameworkShutdownException()
    {
        super("Loomspan framework shutdown deadline reached");
    }
}
