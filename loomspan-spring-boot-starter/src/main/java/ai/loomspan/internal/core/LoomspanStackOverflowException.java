package ai.loomspan.internal.core;

public class LoomspanStackOverflowException extends RuntimeException
{
    public LoomspanStackOverflowException(String sessionId, int maxDepth, String route)
    {
        super("Session " + sessionId + " exceeded max depth " + maxDepth + " while opening route " + route + ".");
    }
}
