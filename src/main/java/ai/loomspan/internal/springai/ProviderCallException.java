package ai.loomspan.internal.springai;

import ai.loomspan.internal.provider.ProviderFailureDetails;

final class ProviderCallException extends RuntimeException implements ai.loomspan.internal.provider.ProviderResponseEvidence
{
    private final ProviderFailureDetails details;
    private final java.util.Map<String, Object> evidence;
    private final ai.loomspan.internal.runtime.usage.ModelUsageRecord usage;

    ProviderCallException(String message, ProviderFailureDetails details)
    {
        this(message, details, java.util.Map.of(), null);
    }

    ProviderCallException(String message, ProviderFailureDetails details, java.util.Map<String, Object> evidence,
            ai.loomspan.internal.runtime.usage.ModelUsageRecord usage)
    {
        super(message);
        this.details = details;
        this.evidence = evidence;
        this.usage = usage;
    }

    ProviderFailureDetails details() { return details; }
    @Override public java.util.Map<String, Object> responseEvidence() { return evidence; }
    @Override public ai.loomspan.internal.runtime.usage.ModelUsageRecord responseUsage() { return usage; }
}
