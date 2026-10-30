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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(GuardrailType.class)
    void eachQuotaEnforcesTheEntryLimitWhileExecutingInAnotherNamespace(GuardrailType category) {
        var settings = new LoomspanProperties();
        var limits = quotas(0, 0, 0, 0, 0); limits.setMaxProviderAttempts(0);
        switch (category) {
            case MAX_SKILL_INVOCATIONS -> limits.setMaxSkillInvocations(2);
            case MAX_TOOL_INVOCATIONS -> limits.setMaxToolInvocations(2);
            case MAX_LINTER_RETRIES -> limits.setMaxLinterRetries(2);
            case MAX_MODEL_CALLS -> limits.setMaxModelCalls(2);
            case MAX_PROVIDER_ATTEMPTS -> limits.setMaxProviderAttempts(2);
            case MAX_USAGE_UNITS -> limits.setMaxUsageUnits(2);
        }
        settings.getSession().setQuotas(limits);
        var childSettings = new LoomspanProperties(); childSettings.getSession().setQuotas(quotas(100,100,100,100,100));
        var entry = generation("orders", settings); var child = generation("pricing", childSettings);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("entry" + category, "orders-root", 8);
        var snapshot = new ai.loomspan.internal.skill.SkillAdmissionSnapshot(ai.loomspan.internal.core.SkillIdentity.parse("orders-root"), Map.of("orders",entry,"pricing",child));
        var parent = ai.loomspan.internal.core.ExecutionBinding.sessionOnly(session,snapshot).withMission(new ai.loomspan.internal.core.MissionContext(session,"orders-root","parent",null));
        parent.branch().push(new ExecutionFrame("frame",null,OperationType.CAPABILITY,TraceFrameType.ROOT_MISSION,"orders-root",Map.of(),Instant.now()));
        var nested = new ai.loomspan.internal.core.ExecutionBinding(session,new ai.loomspan.internal.core.MissionContext(session,"pricing-child","child",parent.mission()),parent.branch(),child,snapshot);
        var service = new DefaultSessionUsageService(childSettings.getSession().getQuotas(),new NoOpUsageMetricsRecorder());
        Runnable consume = () -> {
            switch (category) {
                case MAX_SKILL_INVOCATIONS -> service.recordMissionStart(session,"pricing-child");
                case MAX_TOOL_INVOCATIONS -> service.recordToolCall(session,"orders-root","pricing-child");
                case MAX_LINTER_RETRIES -> service.recordLinterOutcome(session,outcome(LinterOutcomeStatus.RETRYING,0,1));
                case MAX_PROVIDER_ATTEMPTS -> service.reserveProviderAttempt(session,"pricing-child");
                case MAX_MODEL_CALLS, MAX_USAGE_UNITS -> service.recordModelResponse(session,"pricing-child",IDENTITY,new ModelUsageRecord(1,0,1,UsagePrecision.EXACT,null));
            }
        };
        ExecutionBindingScope.runWith(parent,consume);
        ExecutionBindingScope.runWith(nested, () -> {
            consume.run();
            assertThatThrownBy(consume::run).isInstanceOf(LoomspanQuotaExceededException.class)
                    .extracting("guardrailType","limit","observed").containsExactly(category,2L,3L);
        });
    }

    @Test void namespaceChildrenShareEveryCounterAndUseOnlyCapturedEntryQuotas() {
        var entryProperties = new LoomspanProperties();
        entryProperties.getSession().setQuotas(quotas(20, 20, 20, 20, 40));
        entryProperties.getSession().getQuotas().setMaxProviderAttempts(20);
        var childProperties = new LoomspanProperties();
        childProperties.getSession().setQuotas(quotas(1, 1, 1, 1, 2));
        childProperties.getSession().getQuotas().setMaxProviderAttempts(1);
        var entry = generation("orders", entryProperties);
        var child = generation("pricing", childProperties);
        var snapshot = new ai.loomspan.internal.skill.SkillAdmissionSnapshot(
                ai.loomspan.internal.core.SkillIdentity.parse("orders-root"), Map.of("orders", entry, "pricing", child));
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("shared", "orders-root", 8);
        var parent = ai.loomspan.internal.core.ExecutionBinding.sessionOnly(session, snapshot)
                .withMission(new ai.loomspan.internal.core.MissionContext(session, "orders-root", "parent", null));
        var nested = new ai.loomspan.internal.core.ExecutionBinding(session,
                new ai.loomspan.internal.core.MissionContext(session, "pricing-child", "child", parent.mission()), parent.branch(), child, snapshot);
        parent.branch().push(new ExecutionFrame("frame", null, OperationType.CAPABILITY,
                TraceFrameType.ROOT_MISSION, "orders-root", Map.of(), Instant.now()));
        var service = new DefaultSessionUsageService(childProperties.getSession().getQuotas(), new NoOpUsageMetricsRecorder());
        Runnable consume = () -> {
            service.recordMissionStart(session, "orders-root");
            service.recordToolCall(session, "orders-root", "pricing-child");
            service.recordModelResponse(session, "pricing-child", IDENTITY, new ModelUsageRecord(1, 1, 2, UsagePrecision.EXACT, null));
            service.reserveProviderAttempt(session, "pricing-child");
            service.recordLinterOutcome(session, outcome(LinterOutcomeStatus.RETRYING, 0, 1));
        };
        ExecutionBindingScope.runWith(parent, () -> { for (int i = 0; i < 6; i++) consume.run(); });
        ExecutionBindingScope.runWith(nested, () -> { for (int i = 0; i < 3; i++) consume.run(); });
        assertThat(service.snapshot(session)).isEqualTo(new SessionUsageSnapshot(9, 9, 9, 9, 9, 9, 9, 18, 9, 0, 0));
        // A direct entry into pricing uses pricing's own captured quota.
        var directSession = ai.loomspan.internal.core.TestLoomspanSessions.withId("direct", "pricing-child", 8);
        var direct = ai.loomspan.internal.core.ExecutionBinding.sessionOnly(directSession,
                new ai.loomspan.internal.skill.SkillAdmissionSnapshot(ai.loomspan.internal.core.SkillIdentity.parse("pricing-child"), Map.of("pricing", child)));
        ExecutionBindingScope.runWith(direct, () -> {
            service.recordModelResponse(directSession, "pricing-child", IDENTITY, new ModelUsageRecord(0, 0, 0, UsagePrecision.EXACT, null));
            assertThatThrownBy(() -> service.recordModelResponse(directSession, "pricing-child", IDENTITY,
                    new ModelUsageRecord(0, 0, 0, UsagePrecision.EXACT, null))).isInstanceOf(LoomspanQuotaExceededException.class);
        });
        ExecutionBindingScope.runWith(nested, () -> {
            for (int i = 9; i < 20; i++) consume.run();
            assertThatThrownBy(consume::run).isInstanceOf(LoomspanQuotaExceededException.class)
                    .extracting("guardrailType", "limit").containsExactly(GuardrailType.MAX_SKILL_INVOCATIONS, 20L);
        });
    }

    private static ai.loomspan.internal.skill.SkillGeneration generation(String namespace, LoomspanProperties properties) {
        var registry = new ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry(Map.of(),
                org.mockito.Mockito.mock(ai.loomspan.internal.springai.SpringAiProviderIntegration.class));
        var runtime = new ai.loomspan.internal.skill.ExecutionRuntime(properties,
                ai.loomspan.internal.core.TracePersistencePolicy.NEVER, registry);
        return new ai.loomspan.internal.skill.SkillGeneration(namespace, namespace + "-captured", Map.of(), Map.of(), null, null, runtime);
    }

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
