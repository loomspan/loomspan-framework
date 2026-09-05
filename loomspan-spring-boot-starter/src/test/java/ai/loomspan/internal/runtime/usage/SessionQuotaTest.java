package ai.loomspan.internal.runtime.usage;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.runtime.LoomspanQuotaExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionQuotaTest {

    private static final ModelExecutionIdentity IDENTITY =
            new ModelExecutionIdentity("test-model", "test-connection", AiDriver.OPENAI, "provider-model");

    @Test
    void throwsWhenModelCallQuotaExceeded() {
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 10, 10, 1, 100), new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 3);

        service.recordMissionStart(session, "root.skill");
        service.recordModelResponse(session, "root.skill", IDENTITY, new ModelUsageRecord(1, 2, 3, UsagePrecision.EXACT, null));

        assertThatThrownBy(() -> service.recordModelResponse(session, "root.skill", IDENTITY, new ModelUsageRecord(1, 2, 3, UsagePrecision.EXACT, null)))
                .isInstanceOf(LoomspanQuotaExceededException.class)
                .extracting("guardrailType", "limit", "observed")
                .containsExactly(GuardrailType.MAX_MODEL_CALLS, 1L, 2L);
    }

    @Test
    void throwsWhenToolInvocationQuotaExceeded() {
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 1, 10, 10, 100), new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-2", "test.entry", 3);

        var binding = ai.loomspan.internal.core.TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                binding, () -> service.recordToolCall(session, "root.skill", "tool.one"));

        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                binding, () -> service.recordToolCall(session, "root.skill", "tool.one")))
                .isInstanceOf(LoomspanQuotaExceededException.class)
                .extracting("guardrailType")
                .isEqualTo(GuardrailType.MAX_TOOL_INVOCATIONS);
    }

    @Test
    void throwsWhenLinterRetryQuotaExceeded() {
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 10, 1, 10, 100), new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-3", "test.entry", 3);

        service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 0, 1));

        assertThatThrownBy(() -> service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 1, 2)))
                .isInstanceOf(LoomspanQuotaExceededException.class)
                .extracting("guardrailType", "observed")
                .containsExactly(GuardrailType.MAX_LINTER_RETRIES, 2L);
    }

    private static LoomspanProperties.Session.Quotas quotas(int maxSkills, int maxTools, int maxLinterRetries, int maxModelCalls, int maxUsageUnits) {
        LoomspanProperties.Session.Quotas quotas = new LoomspanProperties.Session.Quotas();
        quotas.setMaxSkillInvocations(maxSkills);
        quotas.setMaxToolInvocations(maxTools);
        quotas.setMaxLinterRetries(maxLinterRetries);
        quotas.setMaxModelCalls(maxModelCalls);
        quotas.setMaxUsageUnits(maxUsageUnits);
        return quotas;
    }

    private static LinterOutcome outcome(LinterOutcomeStatus status, int retryCount, int attempt) {
        return new LinterOutcome("lintedSkill", "regex", attempt, retryCount, 4, status, "Return YAML");
    }
}
