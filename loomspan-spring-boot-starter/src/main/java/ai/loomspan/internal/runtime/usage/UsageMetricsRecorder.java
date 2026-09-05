package ai.loomspan.internal.runtime.usage;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderRetryDecision;

public interface UsageMetricsRecorder
{
    void recordSkillInvocation(String skillName);

    void recordModelUsage(String skillName, ModelExecutionIdentity identity, ModelUsageRecord usageRecord);

    void recordProviderAttempt(String skillName, ModelExecutionIdentity identity, String outcome,
            ProviderFailureCategory category, ProviderRetryDecision decision);

    void recordToolInvocation(String skillName, String toolName, String outcome);

    void recordToolAccuracy(String skillName, String linterType, String outcome);

    void recordLinterOutcome(LinterOutcome outcome);

    void recordGuardrailTrip(String skillName, GuardrailType guardrailType);
}
