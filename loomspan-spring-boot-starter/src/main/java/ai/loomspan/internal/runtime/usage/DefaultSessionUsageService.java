package ai.loomspan.internal.runtime.usage;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.runtime.LoomspanQuotaExceededException;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderRetryDecision;

import java.util.Objects;
import java.util.function.UnaryOperator;

public class DefaultSessionUsageService implements SessionUsageService
{
    private final LoomspanProperties.Session.Quotas quotas;
    private final UsageMetricsRecorder usageMetricsRecorder;

    public DefaultSessionUsageService(LoomspanProperties.Session.Quotas quotas, UsageMetricsRecorder usageMetricsRecorder)
    {
        this.quotas = Objects.requireNonNull(quotas, "quotas must not be null");
        this.usageMetricsRecorder = Objects.requireNonNull(usageMetricsRecorder, "usageMetricsRecorder must not be null");
    }

    @Override
    public SessionUsageSnapshot snapshot(LoomspanSession session)
    {
        Objects.requireNonNull(session, "session must not be null");
        return session.getSessionUsage().orElse(SessionUsageSnapshot.empty());
    }

    @Override
    public void recordMissionStart(LoomspanSession session, String skillName)
    {
        Objects.requireNonNull(session, "session must not be null");
        requireWritable(session, () -> {
            SessionUsageSnapshot updated = update(session, SessionUsageSnapshot::incrementSkillInvocations);
            usageMetricsRecorder.recordSkillInvocation(skillName);
            enforce(session, skillName, GuardrailType.MAX_SKILL_INVOCATIONS, quotas.getMaxSkillInvocations(), updated.skillInvocations());
            return null;
        });
    }

    @Override
    public void recordModelResponse(LoomspanSession session, String skillName, ModelExecutionIdentity identity,
            ModelUsageRecord usageRecord)
    {
        Objects.requireNonNull(session, "session must not be null");
        runIfWritable(session, () -> {
            SessionUsageSnapshot updated = update(session, snapshot -> snapshot.recordModelUsage(Objects.requireNonNull(usageRecord, "usageRecord must not be null")));
            usageMetricsRecorder.recordModelUsage(skillName, Objects.requireNonNull(identity, "identity must not be null"), usageRecord);
            enforce(session, skillName, GuardrailType.MAX_MODEL_CALLS, quotas.getMaxModelCalls(), updated.modelCalls());
            enforce(session, skillName, GuardrailType.MAX_USAGE_UNITS, quotas.getMaxUsageUnits(), updated.usageUnits());
        });
    }

    @Override
    public void reserveProviderAttempt(LoomspanSession session, String skillName)
    {
        Objects.requireNonNull(session, "session must not be null");
        requireWritable(session, () -> {
            int limit = quotas.getMaxProviderAttempts();
            final boolean[] rejected = {false};
            SessionUsageSnapshot updated = update(session, snapshot -> {
                if (limit > 0 && snapshot.providerAttempts() >= limit) { rejected[0] = true; return snapshot; }
                return snapshot.incrementProviderAttempts();
            });
            if (rejected[0])
            {
                usageMetricsRecorder.recordGuardrailTrip(skillName, GuardrailType.MAX_PROVIDER_ATTEMPTS);
                throw new LoomspanQuotaExceededException(session.getSessionId(), GuardrailType.MAX_PROVIDER_ATTEMPTS,
                        limit, (long) updated.providerAttempts() + 1L);
            }
            return null;
        });
    }

    @Override
    public void recordProviderAttemptOutcome(LoomspanSession session, String skillName, ModelExecutionIdentity identity, String outcome,
            ProviderFailureCategory category, ProviderRetryDecision decision)
    {
        runIfWritable(session, () -> usageMetricsRecorder.recordProviderAttempt(
                skillName, identity, outcome, category, decision));
    }

    @Override
    public void recordToolCall(LoomspanSession session, String skillName, String capabilityName)
    {
        Objects.requireNonNull(session, "session must not be null");
        requireWritable(session, () -> {
            SessionUsageSnapshot updated = update(session, SessionUsageSnapshot::incrementToolInvocations);
            session.markToolActivity(requireBinding(session).branch().requireLeaf().frameId());
            enforce(session, skillName, GuardrailType.MAX_TOOL_INVOCATIONS, quotas.getMaxToolInvocations(), updated.toolInvocations());
            return null;
        });
    }

    @Override
    public void recordToolOutcome(LoomspanSession session, String skillName, String capabilityName, String outcome)
    {
        runIfWritable(session, () -> usageMetricsRecorder.recordToolInvocation(
                skillName, capabilityName, outcome));
    }

    @Override
    public void recordLinterOutcome(LoomspanSession session, LinterOutcome outcome)
    {
        Objects.requireNonNull(session, "session must not be null");
        LinterOutcome recordedOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
        runIfWritable(session, () -> {
            SessionUsageSnapshot updated = recordedOutcome.status() == LinterOutcomeStatus.RETRYING
                    ? update(session, SessionUsageSnapshot::incrementLinterRetries) : snapshot(session);
            usageMetricsRecorder.recordLinterOutcome(recordedOutcome);
            if (recordedOutcome.status() == LinterOutcomeStatus.RETRYING)
            {
                enforce(session, recordedOutcome.skillName(), GuardrailType.MAX_LINTER_RETRIES,
                        quotas.getMaxLinterRetries(), updated.linterRetries());
                return;
            }
            ExecutionFrame currentFrame = requireBinding(session).branch().requireLeaf();
            int count = recordedOutcome.terminal() && currentFrame.route().equals(recordedOutcome.skillName())
                    ? session.consumeToolActivity(currentFrame.frameId()) : 0;
            for (int i = 0; i < count; i++) usageMetricsRecorder.recordToolAccuracy(
                    recordedOutcome.skillName(), recordedOutcome.linterType(),
                    recordedOutcome.passed() ? "accurate" : "inaccurate");
        });
    }

    private SessionUsageSnapshot update(LoomspanSession session, UnaryOperator<SessionUsageSnapshot> updater)
    {
        return session.updateSessionUsage(existing -> Objects.requireNonNull(updater.apply(existing == null ? SessionUsageSnapshot.empty() : existing),
                "updated session usage must not be null"))
                .orElseThrow();
    }

    private void enforce(LoomspanSession session, String skillName, GuardrailType guardrailType, long limit, long observed)
    {
        if (limit <= 0)
        {
            return;
        }
        if (observed <= limit)
        {
            return;
        }

        usageMetricsRecorder.recordGuardrailTrip(skillName, guardrailType);

        throw new LoomspanQuotaExceededException(session.getSessionId(), guardrailType, limit, observed);
    }

    private ExecutionBinding requireBinding(LoomspanSession session)
    {
        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        if (binding.session() != session)
        {
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        }
        return binding;
    }

    private <T> T requireWritable(LoomspanSession session, java.util.function.Supplier<T> action)
    {
        var current = ExecutionBindingScope.current();
        if (current.isEmpty()) return action.get();
        ExecutionBinding binding = requireBinding(session);
        return binding.requireWritable(action);
    }

    private void runIfWritable(LoomspanSession session, Runnable action)
    {
        var current = ExecutionBindingScope.current();
        if (current.isEmpty()) { action.run(); return; }
        requireBinding(session).runIfWritable(action);
    }
}
