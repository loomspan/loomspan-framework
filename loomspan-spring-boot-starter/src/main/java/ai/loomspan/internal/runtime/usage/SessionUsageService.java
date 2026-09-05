package ai.loomspan.internal.runtime.usage;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderRetryDecision;

public interface SessionUsageService
{
    SessionUsageSnapshot snapshot(LoomspanSession session);

    void recordMissionStart(LoomspanSession session, String skillName);

    void reserveProviderAttempt(LoomspanSession session, String skillName);

    void recordProviderAttemptOutcome(LoomspanSession session, String skillName, ModelExecutionIdentity identity, String outcome,
            ProviderFailureCategory category, ProviderRetryDecision decision);

    void recordModelResponse(LoomspanSession session, String skillName, ModelExecutionIdentity identity, ModelUsageRecord usageRecord);

    void recordToolCall(LoomspanSession session, String skillName, String capabilityName);

    void recordToolOutcome(LoomspanSession session, String skillName, String capabilityName, String outcome);

    void recordLinterOutcome(LoomspanSession session, LinterOutcome outcome);
}
