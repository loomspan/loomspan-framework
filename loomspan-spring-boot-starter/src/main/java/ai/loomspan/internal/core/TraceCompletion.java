package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record TraceCompletion(
        TraceOutcome outcome,
        SessionUsageSnapshot sessionUsageSnapshot,
        @Nullable String terminalFailureId,
        Map<String, Object> details)
{
    public TraceCompletion
    {
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        sessionUsageSnapshot = Objects.requireNonNull(sessionUsageSnapshot, "sessionUsageSnapshot must not be null");
        if (terminalFailureId != null && terminalFailureId.isBlank())
        {
            throw new IllegalArgumentException("terminalFailureId must not be blank");
        }
        if (outcome == TraceOutcome.SUCCEEDED && terminalFailureId != null)
        {
            throw new IllegalArgumentException("A succeeded trace must not have a terminalFailureId");
        }
        if (outcome != TraceOutcome.SUCCEEDED && terminalFailureId == null)
        {
            throw new IllegalArgumentException("A failed or aborted trace must have a terminalFailureId");
        }
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public Map<String, Object> metadata()
    {
        if (details.containsKey("sessionUsageSnapshot"))
        {
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("outcome", outcome.name());
            metadata.put("sessionUsageSnapshot", details.get("sessionUsageSnapshot"));
            if (terminalFailureId != null)
            {
                metadata.put("terminalFailureId", terminalFailureId);
            }
            details.forEach((key, value) ->
            {
                if (!"outcome".equals(key)
                        && !"sessionUsageSnapshot".equals(key)
                        && !"terminalFailureId".equals(key))
                {
                    metadata.put(key, value);
                }
            });
            return Collections.unmodifiableMap(metadata);
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(details);
        metadata.put("outcome", outcome.name());
        metadata.putIfAbsent("sessionUsageSnapshot", sessionUsageSnapshot.toTraceMap());
        if (terminalFailureId != null)
        {
            metadata.put("terminalFailureId", terminalFailureId);
        }
        return Collections.unmodifiableMap(metadata);
    }

    public TraceCompletion asFailed(String failureId)
    {
        return new TraceCompletion(TraceOutcome.FAILED, sessionUsageSnapshot, failureId, details);
    }
}
