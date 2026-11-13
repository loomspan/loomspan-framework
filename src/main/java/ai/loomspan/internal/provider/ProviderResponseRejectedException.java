package ai.loomspan.internal.provider;

/** An explicit provider refusal or incomplete generation, never a transport retry. */
public final class ProviderResponseRejectedException extends RuntimeException
{
    public ProviderResponseRejectedException(String reason) { super("Provider response rejected: " + reason); }
}
