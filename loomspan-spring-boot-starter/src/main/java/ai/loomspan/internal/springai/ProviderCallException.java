package ai.loomspan.internal.springai;

import ai.loomspan.internal.provider.ProviderFailureDetails;

final class ProviderCallException extends RuntimeException
{
    private final ProviderFailureDetails details;

    ProviderCallException(String message, ProviderFailureDetails details)
    {
        super(message);
        this.details = details;
    }

    ProviderFailureDetails details() { return details; }
}
