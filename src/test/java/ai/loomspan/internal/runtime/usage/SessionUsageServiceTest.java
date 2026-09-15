package ai.loomspan.internal.runtime.usage;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.OperationType;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.runtime.LoomspanQuotaExceededException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionUsageServiceTest {

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

        var binding = TestExecutionBindings.missionBinding(session);
        ExecutionBindingScope.runWith(binding, () -> service.recordToolCall(session, "root.skill", "tool.one"));

        assertThatThrownBy(() -> ExecutionBindingScope.runWith(
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

    @Test
    void snapshotsAccumulatedUsage() {
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 10, 10, 10, 100), new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-4", "test.entry", 3);

        ExecutionBindingScope.runWith(TestExecutionBindings.missionBinding(session), () -> {
            service.recordMissionStart(session, "root.skill");
            service.recordToolCall(session, "root.skill", "tool.one");
            service.recordModelResponse(session, "root.skill", IDENTITY, new ModelUsageRecord(3, 4, 7, UsagePrecision.HEURISTIC, null));
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 0, 1));
        });

        assertThat(service.snapshot(session)).isEqualTo(new SessionUsageSnapshot(1, 1, 1, 1, 0, 3, 4, 7, 0, 1, 0));
    }

    @Test
    void reservesProviderAttemptsAtomicallyAndDoesNotIncrementWhenBlocked() {
        LoomspanProperties.Session.Quotas quotas = quotas(10, 10, 10, 10, 100);
        quotas.setMaxProviderAttempts(2);
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas, new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-provider-attempts", "test.entry", 3);

        service.reserveProviderAttempt(session, "root.skill");
        service.reserveProviderAttempt(session, "root.skill");

        assertThatThrownBy(() -> service.reserveProviderAttempt(session, "root.skill"))
                .isInstanceOf(LoomspanQuotaExceededException.class)
                .extracting("guardrailType", "limit", "observed")
                .containsExactly(GuardrailType.MAX_PROVIDER_ATTEMPTS, 2L, 3L);
        assertThat(service.snapshot(session).providerAttempts()).isEqualTo(2);
    }

    @Test
    void simultaneousProviderAttemptReservationsRespectTheLimit() throws Exception {
        LoomspanProperties.Session.Quotas quotas = quotas(0, 0, 0, 0, 0);
        quotas.setMaxProviderAttempts(3);
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas, new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-simultaneous-provider-attempts", "test.entry", 3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(3, TimeUnit.SECONDS)).isTrue();
                    try {
                        service.reserveProviderAttempt(session, "root.skill");
                        admitted.incrementAndGet();
                    }
                    catch (LoomspanQuotaExceededException expected) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
        }

        assertThat(admitted).hasValue(3);
        assertThat(rejected).hasValue(9);
        assertThat(service.snapshot(session).providerAttempts()).isEqualTo(3);
    }

    @Test
    void simultaneousUsageUpdatesLoseNoIncrements() throws Exception {
        DefaultSessionUsageService service = new DefaultSessionUsageService(
                quotas(0, 0, 0, 0, 0), new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-simultaneous-usage", "test.entry", 3);
        var binding = TestExecutionBindings.missionBinding(session);
        int workers = 8;
        int iterations = 25;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(3, TimeUnit.SECONDS)).isTrue();
                    ExecutionBindingScope.runWith(binding, () -> {
                        for (int iteration = 0; iteration < iterations; iteration++) {
                            service.recordMissionStart(session, "root.skill");
                            service.recordModelResponse(session, "root.skill", IDENTITY,
                                    new ModelUsageRecord(1, 1, 2, UsagePrecision.EXACT, null));
                            service.recordToolCall(session, "root.skill", "tool.one");
                            service.recordLinterOutcome(session,
                                    outcome(LinterOutcomeStatus.RETRYING, iteration, iteration + 1));
                        }
                    });
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
        }

        int total = workers * iterations;
        assertThat(service.snapshot(session)).isEqualTo(
                new SessionUsageSnapshot(total, total, total, total, 0, total, total, total * 2, total, 0, 0));
    }

    @Test
    void doesNotEnforceDisabledQuotas() {
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(0, 0, 0, 0, 0), new NoOpUsageMetricsRecorder());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-5", "test.entry", 3);

        ExecutionBindingScope.runWith(TestExecutionBindings.missionBinding(session), () -> {
            service.recordMissionStart(session, "root.skill");
            service.recordMissionStart(session, "root.skill");
            service.recordToolCall(session, "root.skill", "tool.one");
            service.recordToolCall(session, "root.skill", "tool.two");
            service.recordModelResponse(session, "root.skill", IDENTITY, new ModelUsageRecord(4, 5, 9, UsagePrecision.HEURISTIC, null));
            service.recordModelResponse(session, "root.skill", IDENTITY, new ModelUsageRecord(4, 5, 9, UsagePrecision.HEURISTIC, null));
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 0, 1));
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 1, 2));
        });

        assertThat(service.snapshot(session)).isEqualTo(new SessionUsageSnapshot(2, 2, 2, 2, 0, 8, 10, 18, 0, 2, 0));
    }

    @Test
    void recordsToolAccuracyFromTerminalLinterOutcomeForCurrentFrame() {
        RecordingUsageMetricsRecorder recorder = new RecordingUsageMetricsRecorder();
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 10, 10, 10, 100), recorder);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-6", "root.skill", 3);
        ExecutionBindingScope.runWith(TestExecutionBindings.missionBinding(session), () -> {
            service.recordToolCall(session, "root.skill", "tool.one");
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 0, 1));
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.EXHAUSTED, 1, 2));
        });

        assertThat(recorder.toolAccuracySamples).containsExactly("root.skill|regex|inaccurate");
    }

    @Test
    void recordsToolAccuracyForEachToolInvocationInCurrentFrame() {
        RecordingUsageMetricsRecorder recorder = new RecordingUsageMetricsRecorder();
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 10, 10, 10, 100), recorder);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-6b", "root.skill", 3);
        ExecutionBindingScope.runWith(TestExecutionBindings.missionBinding(session), () -> {
            service.recordToolCall(session, "root.skill", "tool.one");
            service.recordToolCall(session, "root.skill", "tool.two");
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.EXHAUSTED, 1, 2));
        });

        assertThat(recorder.toolAccuracySamples)
                .containsExactly("root.skill|regex|inaccurate", "root.skill|regex|inaccurate");
    }

    @Test
    void doesNotRecordToolAccuracyWithoutToolActivityForCurrentFrame() {
        RecordingUsageMetricsRecorder recorder = new RecordingUsageMetricsRecorder();
        DefaultSessionUsageService service = new DefaultSessionUsageService(quotas(10, 10, 10, 10, 100), recorder);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-7", "root.skill", 3);
        ExecutionBindingScope.runWith(TestExecutionBindings.missionBinding(session), () ->
                service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.PASSED, 0, 1)));

        assertThat(recorder.toolAccuracySamples).isEmpty();
    }

    @Test
    void closedBindingSuppressesUsageAndMetricsAtomically()
    {
        RecordingUsageMetricsRecorder recorder = new RecordingUsageMetricsRecorder();
        DefaultSessionUsageService service = new DefaultSessionUsageService(
                quotas(10, 10, 10, 10, 100), recorder);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "closed-usage", "root.skill", 3);
        var binding = TestExecutionBindings.missionBinding(session);

        ExecutionBindingScope.runWith(binding, () -> {
            binding.requireMission().lifecycle().closeNow();
            service.recordModelResponse(session, "root.skill", IDENTITY,
                    new ModelUsageRecord(1, 1, 2, UsagePrecision.EXACT, null));
            service.recordProviderAttemptOutcome(session, "root.skill", IDENTITY, "failed", null, null);
            service.recordToolOutcome(session, "root.skill", "tool", "failure");
            assertThatThrownBy(() -> service.recordMissionStart(session, "root.skill"))
                    .isInstanceOf(ai.loomspan.internal.core.MissionWriteRevokedException.class);
        });

        assertThat(service.snapshot(session)).isEqualTo(SessionUsageSnapshot.empty());
        assertThat(recorder.samples).isZero();
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
        return new LinterOutcome("root.skill", "regex", attempt, retryCount, 4, status, "Return YAML");
    }

    private static final class RecordingUsageMetricsRecorder implements UsageMetricsRecorder {

        private final java.util.List<String> toolAccuracySamples = new java.util.ArrayList<>();
        private int samples;

        @Override
        public void recordSkillInvocation(String skillName) {
            samples++;
        }

        @Override
        public void recordModelUsage(String skillName, ModelExecutionIdentity identity, ModelUsageRecord usageRecord) {
            samples++;
        }

        @Override
        public void recordProviderAttempt(String skillName, ModelExecutionIdentity identity, String outcome,
                ai.loomspan.internal.provider.ProviderFailureCategory category,
                ai.loomspan.internal.provider.ProviderRetryDecision decision) {
            samples++;
        }

        @Override
        public void recordToolInvocation(String skillName, String toolName, String outcome) {
            samples++;
        }

        @Override
        public void recordToolAccuracy(String skillName, String linterType, String outcome) {
            toolAccuracySamples.add(skillName + "|" + linterType + "|" + outcome);
            samples++;
        }

        @Override
        public void recordLinterOutcome(LinterOutcome outcome) {
            samples++;
        }

        @Override
        public void recordGuardrailTrip(String skillName, GuardrailType guardrailType) {
            samples++;
        }
    }
}
