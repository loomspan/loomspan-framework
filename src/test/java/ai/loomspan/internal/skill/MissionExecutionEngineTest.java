package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.DefaultMissionExecutionEngine;
import ai.loomspan.internal.runtime.SimpleChatClient;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.core.TestLoomspanSessions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionExecutionEngineTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration EXECUTION_CONFIGURATION =
            new EffectiveSkillExecutionConfiguration("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    @Test
    void capturesExactBindingAndRestoresReusedWorkerAfterSuccess() throws Exception {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = TestLoomspanSessions.withId("binding-success", "test.entry", 2);
            ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
            SimpleChatClient chatClient = new SimpleChatClient(null, "mission complete") {
                @Override
                public ai.loomspan.internal.model.ModelInteractionResult call(
                        ai.loomspan.internal.model.ModelInteractionRequest request) {
                    assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(binding);
                    return super.call(request);
                }
            };

            String response = ExecutionBindingScope.supplyWith(binding, () -> engine.executeMission(
                    session, definition(), "hello", null, chatClient, List.of(), false, null));

            assertThat(response).isEqualTo("mission complete");
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void restoresReusedWorkerAfterModelFailure() throws Exception {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = TestLoomspanSessions.withId("binding-failure", "test.entry", 2);
            ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
            SimpleChatClient chatClient = new SimpleChatClient(null, "unused") {
                @Override
                public ai.loomspan.internal.model.ModelInteractionResult call(
                        ai.loomspan.internal.model.ModelInteractionRequest request) {
                    assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(binding);
                    throw new IllegalStateException("boom");
                }
            };

            assertThatThrownBy(() -> ExecutionBindingScope.runWith(binding, () -> engine.executeMission(
                    session, definition(), "hello", null, chatClient, List.of(), false, null)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void rejectedSubmissionLeavesSubmittingBindingUnchanged() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutorService missionExecutor = mock(ExecutorService.class);
        RejectedExecutionException rejection = new RejectedExecutionException("rejected");
        when(missionExecutor.submit(any(Callable.class))).thenThrow(rejection);
        DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
        LoomspanSession session = TestLoomspanSessions.withId("binding-rejected", "test.entry", 2);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        ExecutionBindingScope.runWith(binding, () -> {
            assertThatThrownBy(() -> engine.executeMission(
                    session, definition(), "hello", null, new MissionChatClient("unused"), List.of(), false, null))
                    .isSameAs(rejection);
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(binding);
        });
        assertThat(binding.requireMission().lifecycle().state())
                .isEqualTo(ai.loomspan.internal.core.MissionLifecycle.State.CLOSED);
        assertThat(binding.requireMission().lifecycle().primaryCancellation().orElseThrow().cause()).isSameAs(rejection);
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void callerInterruptionRestoresWorkerAndCallerBindings() throws Exception {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        AtomicReference<java.util.Optional<ExecutionBinding>> callerBindingAfter = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean callerInterruptedAfter = new java.util.concurrent.atomic.AtomicBoolean();
        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(30), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = TestLoomspanSessions.withId("binding-caller-interrupt", "test.entry", 2);
            ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
            SimpleChatClient chatClient = new SimpleChatClient(null, "unused") {
                @Override
                public ai.loomspan.internal.model.ModelInteractionResult call(
                        ai.loomspan.internal.model.ModelInteractionRequest request) {
                    assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(binding);
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                        throw new AssertionError("blocking model returned");
                    }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("worker interrupted", ex);
                    }
                    finally {
                        exited.countDown();
                    }
                }
            };
            Thread caller = Thread.ofPlatform().start(() -> {
                try {
                    ExecutionBindingScope.runWith(binding, () -> engine.executeMission(
                            session, definition(), "hello", null, chatClient, List.of(), false, null));
                }
                catch (Throwable failure) {
                    callerFailure.set(failure);
                    callerInterruptedAfter.set(Thread.currentThread().isInterrupted());
                }
                finally {
                    callerBindingAfter.set(ExecutionBindingScope.current());
                }
            });

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(2));
            assertThat(caller.isAlive()).isFalse();
            assertThat(exited.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(callerFailure.get()).isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(callerInterruptedAfter).isTrue();
            assertThat(callerBindingAfter.get()).isEmpty();
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void executesPlanningEnabledMissionLoop() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 2);
            ExecutionPlan plan = new ExecutionPlan(
                    "plan-1",
                    "rootVisibleSkill",
                    Instant.parse("2026-03-15T12:00:00Z"),
                    List.of(
                            new PlanTask("task-1", "Use tool", PlanTaskStatus.PENDING, "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null),
                            new PlanTask("task-2", "Failed", PlanTaskStatus.FAILED, null)));
            MissionChatClient chatClient = new MissionChatClient("mission complete");
            BoundCapability callback = ai.loomspan.testkit.TestBoundCapabilities.capability("allowedVisibleSkill");

            when(planningService.initializePlan(eq(session), eq("hello"), eq(null), any(YamlSkillDefinition.class), eq(chatClient), any()))
                    .thenAnswer(invocation -> {
                        stateService.storePlan(plan);
                        return java.util.Optional.of(plan);
                    });

            String response = executeMission(engine, session, definition(), "hello", null, chatClient, List.of(callback), true, null);

            assertThat(response).isEqualTo("mission complete");
            assertThat(chatClient.getSystemMessagesSeen().getFirst()).contains("plan-1", "Ready tasks", "Failed tasks");
        }
    }

    @Test
    void skipsPlanningForPlanningDisabledMission() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 2);
            MissionChatClient chatClient = new MissionChatClient("mission complete");

            String response = executeMission(engine, session, definition(), "hello", null, chatClient, List.of(), false, null);

            assertThat(response).isEqualTo("mission complete");
            assertThat(chatClient.getSystemMessagesSeen()).containsExactly("Execute the mission using only the visible YAML tools when needed.");
            verify(planningService, never()).initializePlan(eq(session), eq("hello"), eq(null), any(YamlSkillDefinition.class), eq(chatClient), any());
        }
    }

    @Test
    void prependsSkillPromptToSingleShotExecutionPrompt() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-prompt", "test.entry", 2);
            MissionChatClient chatClient = new MissionChatClient("mission complete");

            String response = executeMission(engine, session, definitionWithPrompt(), "hello", null, chatClient, List.of(), false, null);

            assertThat(response).isEqualTo("mission complete");
            assertThat(chatClient.getSystemMessagesSeen()).hasSize(1);
            assertThat(chatClient.getSystemMessagesSeen().getFirst())
                    .startsWith("Act as a careful parser.")
                    .contains("Execute the mission using only the visible YAML tools when needed.");

            assertThat(readRecords(session)).noneMatch(record ->
                    record.recordType() == TraceRecordType.MODEL_REQUEST_SENT);
        }
    }

    @Test
    void recordsFullMissionRequestPayloadWhenRequestIsSent() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-request-trace", "test.entry", 2);
            BoundCapability callback = ai.loomspan.testkit.TestBoundCapabilities.capability("allowedVisibleSkill");

            String response = executeMission(engine,
                    session,
                    definition(),
                    "hello",
                    null,
                    new MissionChatClient("mission complete"),
                    List.of(callback),
                    false,
                    null);

            assertThat(response).isEqualTo("mission complete");
            assertThat(readRecords(session)).noneMatch(record ->
                    record.recordType() == TraceRecordType.MODEL_REQUEST_SENT);
        }
    }

    @Test
    void sendsDeclaredImageAttachmentAsMediaInsteadOfTextOnlyUserMessage() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-attachment", "test.entry", 2);
            MissionChatClient chatClient = new MissionChatClient("mission complete");

            String response = executeMission(engine,
                    session,
                    attachmentDefinition(),
                    "Extract ticket",
                    Map.of("image", imageResource("ticket.jpg", "SECRET_IMAGE_BYTES")),
                    chatClient,
                    List.of(),
                    false,
                    null);

            assertThat(response).isEqualTo("mission complete");
            assertThat(chatClient.getUserMediaSeen()).hasSize(1);
            assertThat(chatClient.getUserMediaSeen().getFirst().mimeType().toString()).isEqualTo("image/jpeg");
            assertThat(chatClient.getUserMessagesSeen()).hasSize(1);
            assertThat(chatClient.getUserMessagesSeen().getFirst())
                    .contains("\"attachment\" : true", "\"contentType\" : \"image/jpeg\"")
                    .doesNotContain("SECRET_IMAGE_BYTES")
                    .doesNotContain("ByteArrayResource");
        }
    }

    @Test
    void planningReceivesDescriptorsAndMissionTraceRedactsAttachmentBytes() {
        PlanningService planningService = mock(PlanningService.class);
        AtomicReference<Map<String, Object>> planningInput = new AtomicReference<>();
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-attachment-trace", "test.entry", 2);
            MissionChatClient chatClient = new MissionChatClient("mission complete");
            ExecutionPlan plan = new ExecutionPlan(
                    "plan-attachment",
                    "rootVisibleSkill",
                    Instant.parse("2026-03-15T12:00:00Z"),
                    List.of(new PlanTask("task-1", "Inspect ticket", PlanTaskStatus.PENDING, null)));
            when(planningService.initializePlan(eq(session), eq("Extract ticket"), any(), any(YamlSkillDefinition.class), eq(chatClient), any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> input = (Map<String, Object>) invocation.getArgument(2);
                        planningInput.set(input);
                        stateService.storePlan(plan);
                        return java.util.Optional.of(plan);
                    });

            executeMission(engine,
                    session,
                    attachmentDefinition(),
                    "Extract ticket",
                    Map.of("image", imageResource("ticket.jpg", "SECRET_IMAGE_BYTES")),
                    chatClient,
                    List.of(),
                    true,
                    null);

            assertThat(planningInput.get().get("image")).isInstanceOf(Map.class);
            assertThat(String.valueOf(planningInput.get())).contains("attachment=true").doesNotContain("SECRET_IMAGE_BYTES");
            assertThat(chatClient.getUserMediaSeen()).hasSize(1);

            List<String> tracePayloads = readRecords(session).stream()
                    .map(String::valueOf)
                    .toList();
            assertThat(tracePayloads).isNotEmpty();
            assertThat(tracePayloads).allSatisfy(payload -> assertThat(payload)
                    .doesNotContain("SECRET_IMAGE_BYTES")
                    .doesNotContain("base64")
                    .doesNotContain("ByteArrayResource"));
        }
    }

    @Test
    void wrapsProviderFailureWithAttachmentContext() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));

            assertThatThrownBy(() -> executeMission(engine,
                    ai.loomspan.internal.core.TestLoomspanSessions.withId("session-provider-failure", "test.entry", 2),
                    attachmentDefinition(),
                    "Extract ticket",
                    Map.of("image", imageResource("ticket.jpg", "image bytes")),
                    new FailingMissionChatClient(),
                    List.of(),
                    false,
                    null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rootVisibleSkill")
                    .hasMessageContaining("openai/gpt-5")
                    .hasMessageContaining("IMAGE/image/jpeg")
                    .hasMessageContaining("supports the declared attachment media");
        }
    }

    @Test
    void timesOutBlockingMissionExecutionAndRestoresReusedWorker() throws Exception {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofMillis(25), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-timeout", "test.entry", 2);
            ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
            BlockingMissionChatClient chatClient = new BlockingMissionChatClient(interrupted);

            assertThatThrownBy(() -> ExecutionBindingScope.runWith(binding, () -> executeMission(engine,
                    session,
                    definition(),
                    "hello",
                    null,
                    chatClient,
                    List.of(),
                    false,
                    null)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class)
                    .hasMessageContaining("session-timeout")
                    .hasMessageContaining("rootVisibleSkill")
                    .hasMessageContaining("PT0.025S");
            awaitInterrupted(interrupted);
            awaitFramesRestored(binding);

            List<TraceRecord> records = readRecords(session);
            TraceRecord error = records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                    .findFirst()
                    .orElseThrow();
            assertThat(records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED))
                    .hasSize(1);
            TraceRecord frameClosed = records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED)
                    .findFirst()
                    .orElseThrow();
            assertThat(records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED)
                    .count()).isEqualTo(1);
            assertThat(frameClosed.metadata()).containsEntry("status", "aborted");
            assertThat(error.frameId()).isEqualTo(frameClosed.frameId());
            assertThat(error.frameType()).isEqualTo(TraceFrameType.MODEL_CALL);
            assertThat(records.indexOf(error)).isLessThan(records.indexOf(frameClosed));
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void directMissionCleanupFailureIsSuppressedWithoutReplacingTimeout() throws Exception
    {
        PlanningService planningService = mock(PlanningService.class);
        IllegalStateException cleanupFailure = new IllegalStateException("frame cleanup trace unavailable");
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK)
        {
            @Override
            public void closeFrameForCleanup(LoomspanSession session,
                    ai.loomspan.internal.core.ExecutionFrame frame, Map<String, Object> metadata,
                    ai.loomspan.internal.core.MissionLifecycle.Cutoff cutoff, boolean alreadyDrained)
            {
                super.closeFrameForCleanup(session, frame, metadata, cutoff, alreadyDrained);
                throw cleanupFailure;
            }
        };
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            started.countDown();
            while (release.getCount() > 0)
            {
                try { release.await(); }
                catch (InterruptedException ignored) { }
            }
            returned.countDown();
            return new ai.loomspan.internal.model.ModelInteractionResult("late", Map.of());
        };

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofMillis(25), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-cleanup-failure", "test.entry", 2);
            ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

            assertThatThrownBy(() -> ExecutionBindingScope.runWith(binding, () -> executeMission(engine,
                    session, definition(), "hello", null, model, List.of(), false, null)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class)
                    .satisfies(failure -> assertThat(failure.getSuppressed()).contains(cleanupFailure));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(binding.branch().depth()).isEqualTo(1);

            release.countDown();
            assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void timesOutWhilePlanningEnabledMissionInitializesPlan() {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        PlanningService planningService = new BlockingPlanningService(interrupted);
        ExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofMillis(25), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-timeout", "test.entry", 2);
            MissionChatClient chatClient = new MissionChatClient("mission complete");

            assertThatThrownBy(() -> executeMission(engine,
                    session,
                    definition(),
                    "hello",
                    null,
                    chatClient,
                    List.of(),
                    true,
                    null))
                    .isInstanceOf(LoomspanMissionTimeoutException.class)
                    .hasMessageContaining("session-timeout")
                    .hasMessageContaining("rootVisibleSkill")
                    .hasMessageContaining("PT0.025S");
            awaitInterrupted(interrupted);
            assertThat(java.util.Optional.ofNullable(session.getExecutionPlanSnapshot())).isEmpty();
        }
    }

    @Test
    void planningThatReturnsAfterTimeoutDoesNotStartMissionModel()
    {
        AtomicBoolean interrupted = new AtomicBoolean();
        PlanningService planningService = mock(PlanningService.class);
        when(planningService.initializePlan(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    try
                    {
                        new CountDownLatch(1).await();
                        throw new AssertionError("planning unexpectedly returned without interruption");
                    }
                    catch (InterruptedException ex)
                    {
                        interrupted.set(true);
                        return java.util.Optional.empty();
                    }
                });
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        AtomicInteger modelCalls = new AtomicInteger();
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            modelCalls.incrementAndGet();
            return new ai.loomspan.internal.model.ModelInteractionResult("must-not-run", Map.of());
        };

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofMillis(25), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = TestLoomspanSessions.withId(
                    "session-planning-timeout-gate", "test.entry", 2);

            assertThatThrownBy(() -> executeMission(
                    engine, session, definition(), "hello", null, model, List.of(), true, null))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
        }

        assertThat(interrupted).isTrue();
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void recordsFailedModelFrameStatusWhenMissionCallThrows() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-failure", "test.entry", 2);

            assertThatThrownBy(() -> executeMission(engine,
                    session,
                    definition(),
                    "hello",
                    null,
                    new FailingMissionChatClient(),
                    List.of(),
                    false,
                    null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("boom");

            List<TraceRecord> records = readRecords(session);
            TraceRecord frameClosed = records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED)
                    .filter(record -> "failed".equals(record.metadata().get("status")))
                    .findFirst()
                    .orElseThrow();
            TraceRecord error = records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                    .findFirst()
                    .orElseThrow();

            assertThat(frameClosed.metadata()).containsEntry("status", "failed");
            assertThat(frameClosed.metadata()).containsEntry("exceptionType", IllegalStateException.class.getName());
            assertThat(error.frameId()).isEqualTo(frameClosed.frameId());
            assertThat(error.frameType()).isEqualTo(TraceFrameType.MODEL_CALL);
            assertThat(error.data().path("diagnostics").get(0).path("kind").asText()).isEqualTo("JAVA_STACK_TRACE");
            assertThat(records.indexOf(error)).isLessThan(records.indexOf(frameClosed));
        }
    }

    @Test
    void recordsAndRethrowsErrorAtModelFrame() {
        PlanningService planningService = mock(PlanningService.class);
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        AssertionError failure = new AssertionError("fatal model failure");
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                planningService,
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), missionExecutor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-error", "test.entry", 2);

            assertThatThrownBy(() -> executeMission(engine,
                    session,
                    definition(),
                    "hello",
                    null,
                    new FailingMissionChatClient(failure),
                    List.of(),
                    false,
                    null))
                    .isSameAs(failure);

            List<TraceRecord> records = readRecords(session);
            TraceRecord error = records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                    .findFirst()
                    .orElseThrow();
            TraceRecord frameClosed = records.stream()
                    .filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED)
                    .findFirst()
                    .orElseThrow();

            assertThat(error.frameId()).isEqualTo(frameClosed.frameId());
            assertThat(error.frameType()).isEqualTo(TraceFrameType.MODEL_CALL);
            assertThat(frameClosed.metadata()).containsEntry("status", "failed");
            assertThat(records.indexOf(error)).isLessThan(records.indexOf(frameClosed));
        }
    }

    private static String executeMission(
            DefaultMissionExecutionEngine engine,
            LoomspanSession session,
            YamlSkillDefinition definition,
            String objective,
            Map<String, Object> missionInput,
            ai.loomspan.internal.model.ModelInteraction modelInteraction,
            List<BoundCapability> visibleTools,
            boolean planningEnabled,
            org.springframework.security.core.Authentication authentication)
    {
        ExecutionBinding binding = ExecutionBindingScope.current()
                .orElseGet(() -> TestExecutionBindings.missionBinding(session));
        return ExecutionBindingScope.supplyWith(binding, () -> engine.executeMission(
                session, definition, objective, missionInput, modelInteraction,
                visibleTools, planningEnabled, authentication));
    }

    private void awaitInterrupted(AtomicBoolean interrupted) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!interrupted.get() && System.nanoTime() < deadlineNanos) {
            Thread.onSpinWait();
        }
        assertThat(interrupted.get()).isTrue();
    }

    private void awaitFramesRestored(ExecutionBinding binding) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (binding.branch().depth() != 1 && System.nanoTime() < deadlineNanos) {
            Thread.onSpinWait();
        }
        assertThat(binding.branch().depth()).isEqualTo(1);
    }

    private static List<TraceRecord> readRecords(LoomspanSession session) {
        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        return records;
    }

    private static YamlSkillDefinition definition() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        return new YamlSkillDefinition(new org.springframework.core.io.ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition definitionWithPrompt() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPrompt("Act as a careful parser.");
        return new YamlSkillDefinition(new org.springframework.core.io.ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition attachmentDefinition() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setInputSchema(attachmentInputSchema());
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillManifest.InputSchemaManifest attachmentInputSchema() {
        YamlSkillManifest.InputSchemaManifest root = new YamlSkillManifest.InputSchemaManifest();
        root.setType("object");
        root.setRequired(List.of("image"));
        root.setAdditionalProperties(false);
        YamlSkillManifest.InputSchemaManifest image = new YamlSkillManifest.InputSchemaManifest();
        image.setType("attachment");
        image.setMediaType("image");
        image.setAllowedContentTypes(List.of("image/jpeg"));
        root.setProperties(Map.of("image", image));
        return root;
    }

    private static ByteArrayResource imageResource(String filename, String content) {
        byte[] marker = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bytes = new byte[marker.length + 4];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        System.arraycopy(marker, 0, bytes, 3, marker.length);
        bytes[bytes.length - 1] = (byte) 0xD9;
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static final class MissionChatClient extends SimpleChatClient {

        private MissionChatClient(String content) {
            super(null, content);
        }
    }

    private static final class BlockingMissionChatClient extends SimpleChatClient {

        private final AtomicBoolean interrupted;

        private BlockingMissionChatClient(AtomicBoolean interrupted) {
            super(null, "unused");
            this.interrupted = interrupted;
        }

        @Override
        public ai.loomspan.internal.model.ModelInteractionResult call(
                ai.loomspan.internal.model.ModelInteractionRequest request) {
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Latch await returned unexpectedly");
            }
            catch (InterruptedException ex) {
                interrupted.set(true);
                throw new IllegalStateException("provider interrupted", ex);
            }
        }
    }
    private static final class FailingMissionChatClient extends SimpleChatClient {

        private final Throwable failure;

        private FailingMissionChatClient() {
            this(new IllegalStateException("boom"));
        }

        private FailingMissionChatClient(Throwable failure) {
            super(null, "unused");
            this.failure = failure;
        }

        @Override
        public ai.loomspan.internal.model.ModelInteractionResult call(
                ai.loomspan.internal.model.ModelInteractionRequest request) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure);
        }
    }
    private static final class BlockingPlanningService implements PlanningService {

        private final AtomicBoolean interrupted;

        private BlockingPlanningService(AtomicBoolean interrupted) {
            this.interrupted = interrupted;
        }

        @Override
        public java.util.Optional<ExecutionPlan> initializePlan(LoomspanSession session,
                                                                String objective,
                                                                java.util.Map<String, Object> missionInput,
                                                                YamlSkillDefinition definition,
                                                                ai.loomspan.internal.model.ModelInteraction chatClient,
                                                                List<BoundCapability> visibleTools) {
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Latch await returned unexpectedly");
            }
            catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Planning interrupted", ex);
            }
        }

        @Override
        public java.util.Optional<String> markToolStarted(LoomspanSession session,
                                                          ai.loomspan.internal.core.CapabilityMetadata capability) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<ExecutionPlan> markToolCompleted(LoomspanSession session,
                                                                   String taskId,
                                                                   String capabilityName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<ExecutionPlan> markToolFailed(LoomspanSession session,
                                                                String taskId,
                                                                String capabilityName,
                                                                RuntimeException ex) {
            throw new UnsupportedOperationException();
        }
    }
}
