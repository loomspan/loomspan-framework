package ai.loomspan.internal.provider;

import ai.loomspan.internal.runtime.usage.ModelUsageRecord;
import java.util.Map;

/** Original evidence retained at the existing adapter boundary when its SDK throws. */
public interface ProviderResponseEvidence
{
    Map<String, Object> responseEvidence();
    ModelUsageRecord responseUsage();

    static ProviderResponseEvidence find(Throwable failure)
    {
        var seen = new java.util.IdentityHashMap<Throwable, Boolean>();
        for (var current = failure; current != null && seen.put(current, true) == null; current = current.getCause())
            if (current instanceof ProviderResponseEvidence evidence) return evidence;
        return null;
    }
}
