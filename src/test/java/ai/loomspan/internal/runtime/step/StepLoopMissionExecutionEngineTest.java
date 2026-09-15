package ai.loomspan.internal.runtime.step;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.LoomspanStackOverflowException;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceCompletion;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.internal.runtime.LoomspanMissionTimeoutException;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.planning.DefaultPlanningService;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.tool.DefaultCapabilityInvoker;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepLoopMissionExecutionEngineTest {

    private static final String BLOCK_UNTIL_INTERRUPTED = "__block_until_interrupted__";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration EXECUTION_CONFIGURATION =
            new EffectiveSkillExecutionConfiguration("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    @Test
    void capturesExactBindingAndRestoresReusedWorkerAfterSuccess() throws Exception {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan completedPlan = new ExecutionPlan(
                "completed", "rootVisibleSkill", Instant.parse("2026-03-15T12:00:00Z"), List.of());
        PlanningService planningService = new InitializingPlanningService(stateService, completedPlan);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-binding-success", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(binding);
            return new ai.loomspan.internal.model.ModelInteractionResult(
                    "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"done\"}",
                    Map.of(ai.loomspan.internal.core.ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY,
                            request.traceContext().nextAttempt()));
        };

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            String response = ExecutionBindingScope.supplyWith(binding, () -> engine.executeMission(
                    session, definition(), "objective", null, model, List.of(), true, null));

            assertThat(response).isEqualTo("done");
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void restoresReusedWorkerAfterStepModelFailure() throws Exception {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-binding-failure", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            ExecutionBinding workerBinding = ExecutionBindingScope.requireCurrent();
            assertThat(workerBinding).isNotSameAs(binding);
            assertThat(workerBinding.session()).isSameAs(binding.session());
            assertThat(workerBinding.mission()).isSameAs(binding.mission());
            assertThat(workerBinding.branch()).isNotSameAs(binding.branch());
            throw new IllegalStateException("step boom");
        };

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            assertThatThrownBy(() -> ExecutionBindingScope.runWith(binding, () -> engine.executeMission(
                    session, definition(), "objective", null, model,
                    List.of(tool("invoiceParser", "{}")), true, null)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("step boom");
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void rejectedStepLoopSubmissionLeavesSubmittingBindingUnchanged() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        ExecutorService missionExecutor = mock(ExecutorService.class);
        RejectedExecutionException rejection = new RejectedExecutionException("rejected");
        when(missionExecutor.submit(any(Callable.class))).thenThrow(rejection);
        StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-binding-rejected", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        ExecutionBindingScope.runWith(binding, () -> {
            assertThatThrownBy(() -> engine.executeMission(
                    session, definition(), "objective", null, new SequenceChatClient(), List.of(), true, null))
                    .isSameAs(rejection);
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(binding);
        });
        assertThat(binding.requireMission().lifecycle().state())
                .isEqualTo(ai.loomspan.internal.core.MissionLifecycle.State.CLOSED);
        assertThat(binding.requireMission().lifecycle().primaryCancellation().orElseThrow().cause()).isSameAs(rejection);
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }

    @Test
    void stepLoopCallerInterruptionRestoresWorkerAndCallerBindings() throws Exception {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        AtomicReference<Optional<ExecutionBinding>> callerBindingAfter = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean callerInterruptedAfter = new java.util.concurrent.atomic.AtomicBoolean();
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-binding-caller-interrupt", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            ExecutionBinding workerBinding = ExecutionBindingScope.requireCurrent();
            assertThat(workerBinding.session()).isSameAs(binding.session());
            assertThat(workerBinding.mission()).isSameAs(binding.mission());
            assertThat(workerBinding.branch()).isNotSameAs(binding.branch());
            started.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("blocking step model returned");
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("step worker interrupted", ex);
            }
            finally {
                exited.countDown();
            }
        };

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition(), Duration.ofSeconds(30));
            Thread caller = Thread.ofPlatform().start(() -> {
                try {
                    ExecutionBindingScope.runWith(binding, () -> engine.executeMission(
                            session, definition(), "objective", null, model,
                            List.of(tool("invoiceParser", "{}")), true, null));
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
    void recordsStepModelFailureBeforeItsModelFrameCloses() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        SequenceChatClient chatClient = new SequenceChatClient();
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-model-failure", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            assertThatThrownBy(() -> executeMission(engine, session, definition(), chatClient,
                    List.of(tool("invoiceParser", "unused"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more queued chat responses");
        }

        List<TraceRecord> records = readRecords(session);
        TraceRecord error = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                .findFirst()
                .orElseThrow();
        TraceRecord modelClose = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED)
                .filter(record -> record.frameType() == TraceFrameType.MODEL_CALL)
                .findFirst()
                .orElseThrow();
        assertThat(error.frameType()).isEqualTo(TraceFrameType.MODEL_CALL);
        assertThat(error.frameId()).isEqualTo(modelClose.frameId());
        assertThat(error.sequence()).isLessThan(modelClose.sequence());
        assertThat(error.data().path("diagnostics")).hasSize(1);
    }

    @Test
    void executesNewlyUnblockedTasksAcrossMultipleSteps() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = twoTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"expenseLookup","toolArguments":{"invoiceId":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Mission complete"}
                """);

        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-1", "test.entry", 3);
        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}"), tool("expenseLookup", "{\"matches\":[]}")));

            assertThat(response).isEqualTo("Mission complete");
        }

        ExecutionPlan finalPlan = session.getExecutionPlanSnapshot();
        assertThat(finalPlan.tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.COMPLETED);
        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
        assertThat(chatClient.systemMessagesSeen().get(1)).contains("ASSIGNED TASK", "ID: t-2");
        assertThat(chatClient.systemMessagesSeen().get(1)).doesNotContain("t-2: Look up expenses (waiting on: t-1)");
        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)).hasSize(3);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.STEP_COMPLETED)).hasSize(3);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.STEP_FAILED);
    }

    @Test
    void retriesInvalidActionBeforeProceeding() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Too early"}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-retry", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            YamlSkillDefinition definition = definitionWithPrompt();
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);

            String response = executeMission(
                    engine,
                    session,
                    definition,
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        List<TraceRecord> records = readRecords(session);
        assertThat(records).anyMatch(record -> record.recordType() == TraceRecordType.STEP_ACTION_REJECTED);
        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("YOUR PREVIOUS ACTION WAS INVALID")
                .containsOnlyOnce("STEP_PROMPT_SENTINEL");
    }

    @Test
    void stepLoopIncludesSkillPromptInStepAndFinalResponsePrompts() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-prompt", "test.entry", 3);
        YamlSkillDefinition definition = definitionWithPrompt();

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);

            String response = executeMission(
                    engine,
                    session,
                    definition,
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen()).allSatisfy(prompt -> assertThat(prompt)
                .startsWith("STEP_PROMPT_SENTINEL"));
        assertThat(chatClient.systemMessagesSeen().getFirst())
                .contains("You are executing one coordinator-assigned task");
        assertThat(chatClient.systemMessagesSeen().getLast())
                .contains("You are executing a planned mission step by step.");

    }

    @Test
    void retriesMissingActionFieldBeforeProceeding() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-missing-action", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        List<TraceRecord> records = readRecords(session);
        assertThat(records).anyMatch(record -> record.recordType() == TraceRecordType.STEP_ACTION_REJECTED
                && String.valueOf(record.metadata().get("reason")).contains("Step action type"));
        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
    }

    @Test
    void surfacesToolFailureAsExplicitTerminalFailure() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan(
                "plan-failure",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "Parse invoice", PlanTaskStatus.PENDING,
                                "invoiceParser", "Parse invoice", List.of(), List.of("parsed"), "batch", null),
                        new PlanTask("t-2", "Parse another invoice", PlanTaskStatus.PENDING,
                                "invoiceParser", "Parse another invoice", List.of(), List.of("parsed"), "batch", null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-failure", "test.entry", 3);
        IllegalStateException expectedFailure = new IllegalStateException("parser exploded");
        BoundCapability failingCapability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    throw expectedFailure;
                });

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            assertThatThrownBy(() -> executeMission(
                    engine,
                    session,
                    definitionWithConcurrency(false),
                    chatClient,
                    List.of(failingCapability)))
                    .isSameAs(expectedFailure);
        }

        List<TraceRecord> records = readRecords(session);
        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().findTask("t-1").orElseThrow().status())
                .isEqualTo(PlanTaskStatus.FAILED);
        assertThat(session.getExecutionPlanSnapshot().findTask("t-2").orElseThrow().status())
                .isEqualTo(PlanTaskStatus.PENDING);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.STEP_FAILED))
                .singleElement()
                .satisfies(record -> assertThat(record.metadata().get("failureId")).isNotNull());
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.ERROR_RECORDED
                && String.valueOf(record.data()).contains("deadlock"));
    }

    @Test
    void surfacesModelFailureAsExplicitTerminalFailure() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-model-failure", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            assertThatThrownBy(() -> executeMission(engine, session, definition(), new SequenceChatClient(),
                    List.of(tool("invoiceParser", "unused"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No more queued chat responses");
        }

        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.STEP_FAILED)).hasSize(1);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED)
                .filter(record -> record.frameType() == TraceFrameType.STEP_EXECUTION))
                .allSatisfy(record -> assertThat(record.metadata()).containsEntry("status", "failed"));
    }

    @Test
    void retriesFinalResponseUntilOutputSchemaPasses() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan().updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"plain text"}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"{\\\"result\\\":\\\"Finished\\\"}"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-schema", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService,
                    planningService,
                    missionExecutor,
                    definitionWithOutputSchema());

            String response = executeMission(
                    engine,
                    session,
                    definitionWithOutputSchema(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("{\"result\":\"Finished\"}");
        }

        assertThat(session.getLastOutputSchemaOutcome()).isPresent();
        assertThat(session.getLastOutputSchemaOutcome().orElseThrow().status()).isEqualTo(OutputSchemaOutcomeStatus.PASSED);
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().get(1)).contains("Final response violates output_schema");
    }

    @Test
    void evidenceRetriesDoNotConsumeOutputSchemaRetryBudget() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan().updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":{"result":"Finished","reasoning":"unsupported"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":{"result":"Finished"}}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-evidence", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        binding.requireMission().recordSuccessfulDirectSkill("invoiceParser");

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService,
                    planningService,
                    missionExecutor,
                    definitionWithOutputSchemaAndEvidenceContract());

            String response = ExecutionBindingScope.supplyWith(binding, () -> executeMission(
                    engine, session, definitionWithOutputSchemaAndEvidenceContract(), chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}"))));

            assertThat(response).isEqualTo("{\"result\":\"Finished\"}");
        }

        assertThat(session.getLastOutputSchemaOutcome()).isPresent();
        assertThat(session.getLastOutputSchemaOutcome().orElseThrow().attempt()).isEqualTo(1);
        assertThat(session.getLastOutputSchemaOutcome().orElseThrow().status()).isEqualTo(OutputSchemaOutcomeStatus.PASSED);
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("unsupported by gathered evidence")
                .contains("requires successful completion of 'expenseLookup'")
                .contains("successfully completed direct skills: [invoiceParser]");
        List<TraceRecord> evidenceRecords = new ArrayList<>();
        session.readTraceRecords(evidenceRecords::add);
        assertThat(evidenceRecords).filteredOn(record -> record.recordType() == TraceRecordType.EVIDENCE_VALIDATION_FAILED)
                .singleElement()
                .satisfies(record ->
                {
                    assertThat(record.data().path("requiredExpressions").path("reasoning").asText()).isEqualTo("expenseLookup");
                    assertThat(record.data().path("satisfiedSkills")).hasSize(1);
                    assertThat(record.data().path("issues").get(0).path("unsatisfiedRequirements")).hasSize(1);
                    assertThat(record.data().has("missingEvidence")).isFalse();
                });
    }

    @Test
    void finalOnlyModeWrapsBarePayloadAsFinalResponseEnvelope() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan().updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {
                  "result": "Finished"
                }
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-bare-final-payload", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService,
                    planningService,
                    missionExecutor,
                    definitionWithOutputSchema());

            String response = executeMission(
                    engine,
                    session,
                    definitionWithOutputSchema(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("{\"result\":\"Finished\"}");
        }

        assertThat(readRecords(session)).anyMatch(record -> record.recordType() == TraceRecordType.STEP_ACTION_VALIDATED
                && "FINAL_RESPONSE".equals(String.valueOf(record.metadata().get("stepAction"))));
    }

    @Test
    void completedPlanRepairsInvalidCallToolByConstrainingPromptToFinalResponse() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan().updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-complete-plan-repair", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().get(0)).contains("All required plan tasks are already COMPLETE");
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("All required plan tasks are already completed. Return FINAL_RESPONSE instead of CALL_TOOL.")
                .contains("You must return a FINAL_RESPONSE action");
        assertThat(readRecords(session)).anyMatch(record -> record.recordType() == TraceRecordType.STEP_ACTION_REJECTED
                && String.valueOf(record.metadata().get("reason")).contains("Return FINAL_RESPONSE instead of CALL_TOOL"));
    }

    @Test
    void retriesWhenConcreteToolSchemaRequiresMissingArguments() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-schema-aware-tool-args", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(toolWithSchema("invoiceParser", """
                            {
                              "type": "object",
                              "properties": {
                                "rawText": { "type": "string" }
                              },
                              "required": ["rawText"],
                              "additionalProperties": false
                            }
                            """, "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("missing_required")
                .contains("rawText")
                .contains("YOUR PREVIOUS ACTION WAS INVALID");
    }

    @Test
    void retriesWhenToolArgumentsContainPlaceholderSentinelValues() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"payload":"<canonical mission input>"}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"payload":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-placeholder-tool-args", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(toolWithSchema("invoiceParser", """
                            {
                              "type": "object",
                              "properties": {
                                "payload": { "type": "string" }
                              },
                              "required": ["payload"],
                              "additionalProperties": false
                            }
                            """, "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("unresolved placeholder values")
                .contains("payload")
                .contains("YOUR PREVIOUS ACTION WAS INVALID");
    }

    @Test
    void retriesToolArgumentValidationWithVerboseGuidanceAfterFirstFailure() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-verbose-retry", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(toolWithContract("invoiceParser", """
                            {
                              "type": "object",
                              "properties": {},
                              "additionalProperties": true
                            }
                            """, """
                            {
                              "type": "object",
                              "properties": {
                                "rawText": { "type": "string" }
                              },
                              "required": ["rawText"],
                              "additionalProperties": false
                            }
                            """, "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
        assertThat(chatClient.systemMessagesSeen().get(0)).doesNotContain("Required fields:");
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("YOUR PREVIOUS ACTION WAS INVALID")
                .contains("Required fields: rawText")
                .contains("`rawText` must be a string");
    }

    @Test
    void usesCanonicalMissionInputForPlanningAndStepUserMessages() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-7"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-mission-input", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition(),
                    "Execute YAML skill 'rootVisibleSkill' using the provided mission input object.",
                    Map.of("invoiceId", "INV-7"),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.userMessagesSeen()).isNotEmpty();
        assertThat(chatClient.userMessagesSeen().getFirst())
                .contains("Canonical mission input")
                .contains("\"invoiceId\" : \"INV-7\"");
    }

    @Test
    void stepLoopSendsAttachmentMediaOnExecutionSteps() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-7"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-attachment", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, attachmentDefinition());

            String response = executeMission(
                    engine,
                    session,
                    attachmentDefinition(),
                    "Extract ticket",
                    Map.of("image", imageResource("ticket.jpg", "SECRET_IMAGE_BYTES")),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.userMediaSeen()).hasSize(2);
        assertThat(chatClient.userMediaSeen()).allSatisfy(media -> assertThat(media.mimeType().toString()).isEqualTo("image/jpeg"));
        assertThat(chatClient.userMessagesSeen()).hasSize(2);
        assertThat(chatClient.userMessagesSeen()).allSatisfy(message -> assertThat(message)
                .contains("\"attachment\" : true", "\"contentType\" : \"image/jpeg\"")
                .doesNotContain("SECRET_IMAGE_BYTES")
                .doesNotContain("ByteArrayResource"));
    }

    @Test
    void retriesWhenModelUsesParentSkillNameInsteadOfBoundReadyTaskTool() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = twoTaskPlan()
                .updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"duplicateInvoiceChecker","toolArguments":{"payload":"INV-1"}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"expenseLookup","toolArguments":{"invoiceId":"INV-1"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-parent-skill-tool-confusion", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            String response = executeMission(
                    engine,
                    session,
                    definition("duplicateInvoiceChecker"),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}"), tool("expenseLookup", "{\"matches\":[]}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
        assertThat(chatClient.systemMessagesSeen().get(0))
                .contains("ASSIGNED TASK")
                .contains("Exact capability/tool: expenseLookup")
                .doesNotContain("Skill: duplicateInvoiceChecker");
        assertThat(chatClient.userMessagesSeen()).isNotEmpty();
        assertThat(chatClient.userMessagesSeen().get(0)).doesNotContain("duplicateInvoiceChecker");
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("Tool 'duplicateInvoiceChecker' is not in the available tools")
                .contains("Exact capability/tool: expenseLookup")
                .contains("Do not call the parent mission skill");
        assertThat(readRecords(session)).anyMatch(record -> record.recordType() == TraceRecordType.STEP_ACTION_REJECTED
                && String.valueOf(record.metadata().get("reason"))
                .contains("Tool 'duplicateInvoiceChecker' is not in the available tools"));
    }

    @Test
    void retriesFinalResponseUntilRegexLinterPasses() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan().updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"APPROVED: Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-linter", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService,
                    planningService,
                    missionExecutor,
                    definitionWithRegexLinter());

            String response = executeMission(
                    engine,
                    session,
                    definitionWithRegexLinter(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("APPROVED: Finished");
        }

        assertThat(session.getLastLinterOutcome()).isPresent();
        assertThat(session.getLastLinterOutcome().orElseThrow().status()).isEqualTo(LinterOutcomeStatus.PASSED);
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().get(1)).contains("Must start with APPROVED:");
    }

    @Test
    void honorsConfiguredRegexLinterRetriesAcrossMultipleFinalResponses() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan().updateTask("t-1", task -> task.complete("parsed"));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Finished"}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Still wrong"}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"APPROVED: Finished"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-linter-retries", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService,
                    planningService,
                    missionExecutor,
                    definitionWithRegexLinter(2));

            String response = executeMission(
                    engine,
                    session,
                    definitionWithRegexLinter(2),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("APPROVED: Finished");
        }

        assertThat(session.getLastLinterOutcome()).isPresent();
        assertThat(session.getLastLinterOutcome().orElseThrow().attempt()).isEqualTo(3);
        assertThat(session.getLastLinterOutcome().orElseThrow().maxRetries()).isEqualTo(2);
        assertThat(chatClient.systemMessagesSeen()).hasSize(3);
    }

    @Test
    void recordsTerminalFailureWhenInvalidActionRetriesAreExhausted() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Too early"}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Still too early"}
                """);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-invalid-exhausted", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);

            assertThatThrownBy(() -> executeMission(
                    engine,
                    session,
                    definition(),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Step action validation exhausted");
        }

        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED
                && String.valueOf(record.data()).contains("Step action validation exhausted"))).hasSize(1);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.STEP_FAILED)).hasSize(1);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED);
    }

    @Test
    void timeoutRecordsOnlyCallerFailureAndRestoresReusedWorker() throws Exception {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        SequenceChatClient chatClient = new SequenceChatClient(BLOCK_UNTIL_INTERRUPTED);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-timeout", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition(), Duration.ofMillis(100));

            assertThatThrownBy(() -> executeMission(engine, session, definition(), chatClient,
                    List.of(tool("invoiceParser", "unused"))))
                    .isInstanceOf(LoomspanMissionTimeoutException.class)
                    .hasMessageContaining("step-loop-timeout");
            assertThat(missionExecutor.submit(ExecutionBindingScope::current).get(2, TimeUnit.SECONDS)).isEmpty();
        }

        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED))
                .hasSize(1)
                .allSatisfy(record -> assertThat(String.valueOf(record.data()))
                        .contains(LoomspanMissionTimeoutException.class.getName()));
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.FRAME_CLOSED))
                .allSatisfy(record -> assertThat(record.metadata()).containsEntry("status", "aborted"));
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.STEP_FAILED
                || record.recordType() == TraceRecordType.STEP_COMPLETED);
    }

    @Test
    void cleanupFailureIsSuppressedWithoutReplacingTimeoutAndLogicalPlanCleanupCompletes()
    {
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup trace unavailable");
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK)
        {
            @Override
            public void storeAndLogPlanForCleanup(LoomspanSession session, ExecutionPlan plan,
                    ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition transition,
                    ai.loomspan.internal.core.MissionLifecycle.Cutoff cutoff)
            {
                super.storeAndLogPlanForCleanup(session, plan, transition, cutoff);
                throw cleanupFailure;
            }
        };
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        SequenceChatClient chatClient = new SequenceChatClient(BLOCK_UNTIL_INTERRUPTED);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-cleanup-failure", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition(), Duration.ofMillis(100));

            assertThatThrownBy(() -> executeMission(engine, session, definition(), chatClient,
                    List.of(tool("invoiceParser", "unused"))))
                    .isInstanceOf(LoomspanMissionTimeoutException.class)
                    .satisfies(failure -> assertThat(failure.getSuppressed()).contains(cleanupFailure));
        }

        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED);
    }

    @Test
    void failedAdmissionTraceDoesNotCommitPlanOrLifecycleEntries()
    {
        IllegalStateException traceFailure = new IllegalStateException("plan trace unavailable");
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK)
        {
            @Override
            public void logPlanUpdated(LoomspanSession session, ExecutionPlan plan,
                    ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition transition)
            {
                if (plan.tasks().stream().anyMatch(task -> task.status() == PlanTaskStatus.IN_PROGRESS))
                {
                    throw traceFailure;
                }
                super.logPlanUpdated(session, plan, transition);
            }
        };
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-admission-trace-failure", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding,
                    () -> executeMission(engine, session, definition(), new SequenceChatClient(
                            "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}"),
                            List.of(tool("invoiceParser", "unused")))))
                    .isSameAs(traceFailure);
        }

        assertThat(binding.requireMission().currentPlan()).get().satisfies(plan ->
                assertThat(plan.tasks()).extracting(PlanTask::status).containsExactly(PlanTaskStatus.PENDING));
        assertThat(binding.requireMission().lifecycle().closeNow().tasks()).isEmpty();
    }

    @Test
    void timeoutCutoffSuppressesLateWorkerWritesAfterCallerReturns() throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(
                stateService, groupedPlan("plan-timeout-cutoff", true));
        CountDownLatch lateWorkerStarted = new CountDownLatch(1);
        CountDownLatch releaseLateWorker = new CountDownLatch(1);
        CountDownLatch lateWorkerReturned = new CountDownLatch(1);
        AtomicInteger externalSideEffects = new AtomicInteger();
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if (!"t-2".equals(taskId)) return "early-" + taskId;
                    lateWorkerStarted.countDown();
                    boolean interrupted = false;
                    while (releaseLateWorker.getCount() > 0)
                    {
                        try
                        {
                            releaseLateWorker.await();
                        }
                        catch (InterruptedException ex)
                        {
                            interrupted = true;
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    externalSideEffects.incrementAndGet();
                    lateWorkerReturned.countDown();
                    return "late-t-2";
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-timeout-cutoff", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition, Duration.ofMillis(100));

            assertThatThrownBy(() -> executeMission(engine, session, definition, model, List.of(capability)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(lateWorkerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            List<TraceRecord> afterCutoff = readRecords(session);
            assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
            assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                    .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.FAILED, PlanTaskStatus.PENDING);
            assertThat(afterCutoff.stream().filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED))
                    .hasSize(2);
            String terminalFailureId = afterCutoff.stream()
                    .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                    .map(record -> String.valueOf(record.metadata().get("failureId")))
                    .findFirst().orElseThrow();
            session.finalizeTrace(new TraceCompletion(
                    TraceOutcome.ABORTED, SessionUsageSnapshot.empty(), terminalFailureId, Map.of()));
            List<TraceRecord> finalized = readRecords(session);
            assertThat(finalized.stream().filter(record -> record.recordType() == TraceRecordType.TRACE_COMPLETED))
                    .hasSize(1);

            releaseLateWorker.countDown();
            assertThat(lateWorkerReturned.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(externalSideEffects).hasValue(1);
            assertThat(readRecords(session)).containsExactlyElementsOf(finalized);
        }
    }

    @Test
    void timeoutDuringSerializedGroupedMemberFoldsCompletedFailedAndPendingTasks() throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(
                stateService, groupedPlan("plan-serialized-timeout", true));
        CountDownLatch lateMemberStarted = new CountDownLatch(1);
        CountDownLatch releaseLateMember = new CountDownLatch(1);
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-1".equals(taskId)) return "first-complete";
                    lateMemberStarted.countDown();
                    while (releaseLateMember.getCount() > 0)
                    {
                        try { releaseLateMember.await(); }
                        catch (InterruptedException ignored) { }
                    }
                    return "late-result";
                });
        YamlSkillDefinition definition = definitionWithConcurrency(false);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-serialized-timeout", "test.entry", 3);

        try (ExecutorService missionExecutor = new SerializedMemberTimeoutExecutor(lateMemberStarted))
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition, Duration.ofMillis(100));
            assertThatThrownBy(() -> executeMission(engine, session, definition, model, List.of(capability)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(lateMemberStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
            assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                    .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.FAILED, PlanTaskStatus.PENDING);
            List<TraceRecord> updates = readRecords(session).stream()
                    .filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                    .toList();
            assertThat(updates).hasSize(4);
            assertTransition(updates.get(0), "ADMISSION", List.of("t-1"), "batch", false, null);
            assertTransition(updates.get(1), "JOIN", List.of("t-1"), "batch", null, "COMPLETED");
            assertTransition(updates.get(2), "ADMISSION", List.of("t-2"), "batch", false, null);
            assertTransition(updates.get(3), "JOIN", List.of("t-2"), "batch", null, "FAILED");
            releaseLateMember.countDown();
        }
    }

    @Test
    void timeoutDuringFinalSynthesisPreservesCompletedTaskAndMarksPlanStale()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        SequenceChatClient model = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}",
                BLOCK_UNTIL_INTERRUPTED);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-final-synthesis-timeout", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition(), Duration.ofMillis(100));
            assertThatThrownBy(() -> executeMission(engine, session, definition(), model,
                    List.of(tool("invoiceParser", "complete-before-final"))))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
        }

        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.COMPLETED);
    }

    @Test
    void taskReturningAfterTimeoutDoesNotStartFinalSynthesis() throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        SequenceChatClient model = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        CountDownLatch capabilityStarted = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    capabilityStarted.countDown();
                    try
                    {
                        new CountDownLatch(1).await();
                        throw new AssertionError("capability unexpectedly returned without interruption");
                    }
                    catch (InterruptedException ex)
                    {
                        interrupted.set(true);
                        return "completed-after-timeout";
                    }
                });
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-final-gate", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition(), Duration.ofMillis(100));
            assertThatThrownBy(() -> executeMission(engine, session, definition(), model, List.of(capability)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
        }

        assertThat(capabilityStarted.getCount()).isZero();
        assertThat(interrupted).isTrue();
        assertThat(model.systemMessagesSeen()).hasSize(1);
    }

    @Test
    void taskReturningAfterTimeoutDoesNotPreflightAnotherUnitOrRecordAnotherFailure()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, twoTaskPlan());
        SequenceChatClient model = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        AtomicBoolean interrupted = new AtomicBoolean();
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    try
                    {
                        new CountDownLatch(1).await();
                        throw new AssertionError("capability unexpectedly returned without interruption");
                    }
                    catch (InterruptedException ex)
                    {
                        interrupted.set(true);
                        return "completed-after-timeout";
                    }
                });
        YamlSkillDefinition definition = definitionWithMaxSteps(2);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-next-unit-gate", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newSingleThreadExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition, Duration.ofMillis(100));
            assertThatThrownBy(() -> executeMission(engine, session, definition, model, List.of(capability)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
        }

        assertThat(interrupted).isTrue();
        assertThat(model.systemMessagesSeen()).hasSize(1);
        assertThat(readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED))
                .singleElement()
                .satisfies(record -> assertThat(String.valueOf(record.data())).doesNotContain("exhausted 2 steps"));
    }

    @Test
    void timeoutStopsSubmittingRemainingAdmittedGroupMembers()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(
                stateService, groupedPlan("plan-stop-submission", false));
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-stop-submission", "test.entry", 3);

        try (PauseAfterFirstGroupSubmissionExecutor missionExecutor = new PauseAfterFirstGroupSubmissionExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition, Duration.ofMillis(250));
            assertThatThrownBy(() -> executeMission(engine, session, definition, model,
                    List.of(tool("invoiceParser", "unused"))))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(missionExecutor.firstGroupMemberSubmitted()).isTrue();
            assertThat(missionExecutor.submissions()).isEqualTo(2);
        }
    }

    @Test
    void partialGroupSubmissionRejectionFoldsAvailableOutcomeAndKeepsExactCause()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(
                stateService, groupedPlan("plan-partial-rejection", true));
        RejectedExecutionException rejection = new RejectedExecutionException("reject second group member");
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-partial-rejection", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser",
                "t-3", "invoiceParser"), new AtomicReference<>());

        try (RejectThirdSubmissionExecutor missionExecutor = new RejectThirdSubmissionExecutor(rejection))
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition, Duration.ofSeconds(5));

            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding,
                    () -> executeMission(engine, session, definition, model,
                            List.of(tool("invoiceParser", "completed-before-rejection")))))
                    .isSameAs(rejection);
            assertThat(missionExecutor.submissions()).isEqualTo(3);
        }

        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.FAILED, PlanTaskStatus.PENDING);
        assertThat(binding.requireMission().lastToolResult()).contains("completed-before-rejection");
        assertThat(readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                .filter(record -> String.valueOf(record.data()).contains("reject second group member")))
                .hasSize(1);
        List<TraceRecord> records = readRecords(session);
        List<TraceRecord> updates = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .toList();
        assertThat(updates).hasSize(2);
        assertTransition(updates.get(0), "ADMISSION", List.of("t-1", "t-2"), "batch", true, null);
        assertTransition(updates.get(1), "JOIN", List.of("t-1", "t-2"), "batch", null, "FAILED");
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)
                .filter(record -> record.metadata().containsKey("assignedTaskId")))
                .singleElement()
                .satisfies(record -> assertThat(record.metadata()).containsEntry("assignedTaskId", "t-1"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = true)
    void enabledGroupedTasksOverlapOnlyAfterAtomicAdmission(@Nullable Boolean configuredConcurrency) throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan(
                "plan-concurrent-admission",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "First", PlanTaskStatus.PENDING,
                                "invoiceParser", "first", List.of(), List.of(), "batch", null),
                        new PlanTask("t-2", "Second", PlanTaskStatus.PENDING,
                                "invoiceParser", "second", List.of(), List.of(), "batch", null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger invocations = new AtomicInteger();
        Set<Object> workerBranches = ConcurrentHashMap.newKeySet();
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    invocations.incrementAndGet();
                    workerBranches.add(ExecutionBindingScope.requireCurrent().branch());
                    ExecutionPlan admitted = stateService.currentPlan().orElseThrow();
                    assertThat(admitted.tasks()).extracting(PlanTask::status)
                            .containsExactly(PlanTaskStatus.IN_PROGRESS, PlanTaskStatus.IN_PROGRESS);
                    bothStarted.countDown();
                    try
                    {
                        assertThat(bothStarted.await(3, TimeUnit.SECONDS))
                                .as("both grouped callbacks must start before either returns")
                                .isTrue();
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("concurrent group probe interrupted", ex);
                    }
                    return "result-" + taskId;
                });
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            String prompt = request.systemPrompt();
            String response;
            if (prompt.contains("--- ASSIGNED TASK ---\nID: t-1"))
            {
                response = "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}";
            }
            else if (prompt.contains("--- ASSIGNED TASK ---\nID: t-2"))
            {
                response = "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-2\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}";
            }
            else
            {
                response = "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"done\"}";
            }
            return new ai.loomspan.internal.model.ModelInteractionResult(response, Map.of(
                    ai.loomspan.internal.core.ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY,
                    request.traceContext().nextAttempt()));
        };
        YamlSkillDefinition definition = configuredConcurrency == null
                ? definition()
                : definitionWithConcurrency(configuredConcurrency);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-concurrent-admission", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newFixedThreadPool(3))
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThat(executeMission(engine, session, definition, model, List.of(capability))).isEqualTo("done");
            assertExecutorBindingsCleared(missionExecutor, 3);
        }

        assertThat(invocations).hasValue(2);
        assertThat(workerBranches).hasSize(2);
        List<TraceRecord> updates = readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .toList();
        assertThat(updates).hasSize(2);
        assertTransition(updates.get(0), "ADMISSION", List.of("t-1", "t-2"), "batch", true, null);
        assertTransition(updates.get(1), "JOIN", List.of("t-1", "t-2"), "batch", null, "COMPLETED");
    }

    @Test
    void ungroupedReadyTasksRemainSequentialWhenConcurrencyIsEnabled()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan(
                "plan-ungrouped-sequential",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "First", PlanTaskStatus.PENDING,
                                "invoiceParser", "first", List.of(), List.of(), null, null),
                        new PlanTask("t-2", "Second", PlanTaskStatus.PENDING,
                                "invoiceParser", "second", List.of(), List.of(), null, null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        Set<Thread> callbackThreads = ConcurrentHashMap.newKeySet();
        List<String> taskOrder = java.util.Collections.synchronizedList(new ArrayList<>());
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    callbackThreads.add(Thread.currentThread());
                    taskOrder.add(taskId);
                    return "result-" + taskId;
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-ungrouped-sequential", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThat(executeMission(engine, session, definition, model, List.of(capability))).isEqualTo("done");
        }

        assertThat(taskOrder).containsExactly("t-1", "t-2");
        assertThat(callbackThreads)
                .as("ungrouped assignments must stay on the single coordinator task")
                .hasSize(1);
        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)
                .filter(record -> record.metadata().containsKey("assignedTaskId")))
                .allSatisfy(record -> assertThat(record.metadata())
                        .containsEntry("effectiveConcurrency", false)
                        .doesNotContainKey("parallelGroup"));
        List<TraceRecord> updates = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .toList();
        assertThat(updates).hasSize(4);
        assertTransition(updates.get(0), "ADMISSION", List.of("t-1"), null, false, null);
        assertTransition(updates.get(1), "JOIN", List.of("t-1"), null, null, "COMPLETED");
        assertTransition(updates.get(2), "ADMISSION", List.of("t-2"), null, false, null);
        assertTransition(updates.get(3), "JOIN", List.of("t-2"), null, null, "COMPLETED");
    }

    @Test
    void reverseCompletionFoldsParentStateInTaskOrderAndPublishesOnce()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = groupedPlan("plan-reverse-fold", false);
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        CountDownLatch laterFinished = new CountDownLatch(1);
        AtomicReference<String> finalPrompt = new AtomicReference<>();
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), finalPrompt);
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-1".equals(taskId))
                    {
                        try
                        {
                            assertThat(laterFinished.await(3, TimeUnit.SECONDS)).isTrue();
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("reverse completion probe interrupted", ex);
                        }
                        var mission = ExecutionBindingScope.requireCurrent().requireMission();
                        assertThat(mission.lastToolResult()).isEmpty();
                        assertThat(mission.executionSummary()).isEmpty();
                        assertThat(stateService.currentPlan().orElseThrow().tasks()).extracting(PlanTask::status)
                                .containsExactly(PlanTaskStatus.IN_PROGRESS, PlanTaskStatus.IN_PROGRESS);
                    }
                    else
                    {
                        laterFinished.countDown();
                    }
                    return "result-" + taskId;
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-reverse-fold", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThat(executeMission(engine, session, definition, model, List.of(capability))).isEqualTo("done");
        }

        assertThat(finalPrompt.get())
                .contains("Step 1: Called invoiceParser for task t-1 -> result-t-1")
                .contains("Step 2: Called invoiceParser for task t-2 -> result-t-2")
                .contains("--- LAST TOOL RESULT ---\nresult-t-2");
        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)).hasSize(2);
        List<TraceRecord> updates = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .toList();
        assertTransition(updates.get(0), "ADMISSION", List.of("t-1", "t-2"), "batch", true, null);
        assertTransition(updates.get(1), "JOIN", List.of("t-1", "t-2"), "batch", null, "COMPLETED");
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)
                .filter(record -> record.metadata().containsKey("assignedTaskId")))
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.metadata())
                        .containsEntry("parallelGroup", "batch")
                        .containsEntry("effectiveConcurrency", true));
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)
                .filter(record -> !record.metadata().containsKey("assignedTaskId")))
                .singleElement()
                .satisfies(record -> assertThat(record.metadata())
                        .doesNotContainKeys("parallelGroup", "effectiveConcurrency"));
    }

    @Test
    void ordinaryFailureDoesNotCancelSiblingAndFoldsEveryOutcome() throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = groupedPlan("plan-ordinary-failure", true);
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        IllegalStateException expectedFailure = new IllegalStateException("first failed");
        CountDownLatch firstMayFail = new CountDownLatch(1);
        CountDownLatch siblingFinished = new CountDownLatch(1);
        AtomicInteger laterUnitCalls = new AtomicInteger();
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser",
                "t-3", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-1".equals(taskId))
                    {
                        try
                        {
                            assertThat(firstMayFail.await(3, TimeUnit.SECONDS)).isTrue();
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("failure probe interrupted", ex);
                        }
                        throw expectedFailure;
                    }
                    if ("t-2".equals(taskId))
                    {
                        firstMayFail.countDown();
                        siblingFinished.countDown();
                        return "sibling-result";
                    }
                    laterUnitCalls.incrementAndGet();
                    return "later-result";
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-ordinary-failure", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        try (ExecutorService missionExecutor = Executors.newFixedThreadPool(3))
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding,
                    () -> executeMission(engine, session, definition, model, List.of(capability))))
                    .isSameAs(expectedFailure);
            assertExecutorBindingsCleared(missionExecutor, 3);
        }

        assertThat(siblingFinished.getCount()).isZero();
        assertThat(laterUnitCalls).hasValue(0);
        assertThat(binding.requireMission().lastToolResult()).contains("sibling-result");
        assertThat(binding.requireMission().executionSummary())
                .hasValueSatisfying(summary -> assertThat(summary).contains("task t-2 -> sibling-result"));
        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.COMPLETED, PlanTaskStatus.PENDING);
        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED))
                .hasSize(2);
    }

    @Test
    void nestedMissionTimeoutIsAnOrdinaryMemberFailureAndDoesNotCancelSibling()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = groupedPlan("plan-nested-timeout", false);
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        LoomspanMissionTimeoutException expectedFailure = new LoomspanMissionTimeoutException(
                "child-session", "nestedPlanner", Duration.ofSeconds(1), new IllegalStateException("child timed out"));
        CountDownLatch siblingFinished = new CountDownLatch(1);
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-1".equals(taskId))
                    {
                        try
                        {
                            assertThat(siblingFinished.await(3, TimeUnit.SECONDS)).isTrue();
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("nested timeout probe interrupted", ex);
                        }
                        throw expectedFailure;
                    }
                    siblingFinished.countDown();
                    return "sibling-result";
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-nested-timeout", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding,
                    () -> executeMission(engine, session, definition, model, List.of(capability))))
                    .isSameAs(expectedFailure);
        }

        assertThat(siblingFinished.getCount()).isZero();
        assertThat(binding.requireMission().lastToolResult()).contains("sibling-result");
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.COMPLETED);
    }

    @Test
    void nestedDepthFailureIsAnOrdinaryMemberFailureAndDoesNotCancelSibling()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = groupedPlan("plan-nested-depth", false);
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        LoomspanStackOverflowException expectedFailure = new LoomspanStackOverflowException(
                "step-loop-nested-depth", 3, "nestedPlanner");
        CountDownLatch siblingFinished = new CountDownLatch(1);
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-1".equals(taskId))
                    {
                        try
                        {
                            assertThat(siblingFinished.await(3, TimeUnit.SECONDS)).isTrue();
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("nested depth probe interrupted", ex);
                        }
                        throw expectedFailure;
                    }
                    siblingFinished.countDown();
                    return "sibling-result";
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-nested-depth", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding,
                    () -> executeMission(engine, session, definition, model, List.of(capability))))
                    .isSameAs(expectedFailure);
        }

        assertThat(siblingFinished.getCount()).isZero();
        assertThat(binding.requireMission().lastToolResult()).contains("sibling-result");
        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.COMPLETED);
        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED))
                .hasSize(2);
    }

    @Test
    void followingUnitWaitsForEveryConcurrentGroupMember() throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = groupedPlan("plan-following-unit", true);
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        CountDownLatch bothGroupMembersStarted = new CountDownLatch(2);
        CountDownLatch releaseLaterMember = new CountDownLatch(1);
        CountDownLatch followingUnitStarted = new CountDownLatch(1);
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser",
                "t-3", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-1".equals(taskId))
                    {
                        bothGroupMembersStarted.countDown();
                        try
                        {
                            assertThat(bothGroupMembersStarted.await(3, TimeUnit.SECONDS)).isTrue();
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("following unit probe interrupted", ex);
                        }
                        return "first-result";
                    }
                    if ("t-2".equals(taskId))
                    {
                        bothGroupMembersStarted.countDown();
                        try
                        {
                            assertThat(releaseLaterMember.await(3, TimeUnit.SECONDS)).isTrue();
                        }
                        catch (InterruptedException ex)
                        {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("following unit release interrupted", ex);
                        }
                        return "second-result";
                    }
                    followingUnitStarted.countDown();
                    return "following-result";
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-following-unit", "test.entry", 3);
        ExecutionBinding binding = TestExecutionBindings.missionBinding(session);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor();
             ExecutorService callerExecutor = Executors.newSingleThreadExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            Future<String> result = callerExecutor.submit(() -> ExecutionBindingScope.supplyWith(binding,
                    () -> executeMission(engine, session, definition, model, List.of(capability))));

            assertThat(bothGroupMembersStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(followingUnitStarted.await(250, TimeUnit.MILLISECONDS))
                    .as("the following singleton must remain blocked while any group member is running")
                    .isFalse();
            assertThat(binding.requireMission().currentPlan().orElseThrow().findTask("t-3").orElseThrow().status())
                    .isEqualTo(PlanTaskStatus.PENDING);

            releaseLaterMember.countDown();
            assertThat(result.get(3, TimeUnit.SECONDS)).isEqualTo("done");
        }

        assertThat(followingUnitStarted.getCount()).isZero();
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.COMPLETED, PlanTaskStatus.COMPLETED);
    }

    @Test
    void multipleFailuresPropagateEarliestTaskListThrowableRegardlessOfCompletionOrder()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = groupedPlan("plan-multiple-failures", false);
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        IllegalStateException firstFailure = new IllegalStateException("first failure");
        IllegalArgumentException secondFailure = new IllegalArgumentException("second failure");
        CountDownLatch secondFailed = new CountDownLatch(1);
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    if ("t-2".equals(taskId))
                    {
                        secondFailed.countDown();
                        throw secondFailure;
                    }
                    try
                    {
                        assertThat(secondFailed.await(3, TimeUnit.SECONDS)).isTrue();
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("multiple failure probe interrupted", ex);
                    }
                    throw firstFailure;
                });
        YamlSkillDefinition definition = definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-multiple-failures", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThatThrownBy(() -> executeMission(engine, session, definition, model, List.of(capability)))
                    .isSameAs(firstFailure);
        }

        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.FAILED, PlanTaskStatus.FAILED);
        assertThat(readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                .map(record -> String.valueOf(record.data())))
                .anyMatch(data -> data.contains("first failure"))
                .anyMatch(data -> data.contains("second failure"));
    }

    @Test
    void disabledGroupedTasksRemainSequentialAndRetainParallelGroup() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "Parse first invoice", PlanTaskStatus.PENDING,
                                "invoiceParser", "Parse invoice A", List.of(), List.of("parsedA"), "invoice-batch", null),
                        new PlanTask("t-2", "Parse second invoice", PlanTaskStatus.PENDING,
                                "invoiceParser", "Parse invoice B", List.of(), List.of("parsedB"), "invoice-batch", null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"invoiceParser","toolArguments":{"rawText":"INV-2"}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":"INV-1"}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"invoiceParser","toolArguments":{"rawText":"INV-2"}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Mission complete"}
                """);
        AtomicInteger routerCalls = new AtomicInteger();
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-real-tool", "test.entry", 3);
        BoundCapability realWrappedTool = realToolCallback(stateService, planningService, routerCalls, session);
        YamlSkillDefinition definition = definitionWithConcurrency(false);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);

            String response = executeMission(
                    engine,
                    session,
                    definition,
                    chatClient,
                    List.of(realWrappedTool));

            assertThat(response).isEqualTo("Mission complete");
        }

        assertThat(routerCalls.get()).isEqualTo(2);
        ExecutionPlan finalPlan = session.getExecutionPlanSnapshot();
        assertThat(finalPlan.findTask("t-1").orElseThrow().status()).isEqualTo(PlanTaskStatus.COMPLETED);
        assertThat(finalPlan.findTask("t-2").orElseThrow().status()).isEqualTo(PlanTaskStatus.COMPLETED);
        assertThat(finalPlan.tasks()).extracting(PlanTask::parallelGroup)
                .containsExactly("invoice-batch", "invoice-batch");

        long linkedTask2Calls = readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.TOOL_CALL_STARTED)
                .filter(record -> "t-2".equals(record.metadata().get("linkedTaskId")))
                .count();
        assertThat(linkedTask2Calls).isEqualTo(1);
        assertThat(readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)
                .filter(record -> record.metadata().containsKey("assignedTaskId"))
                .toList())
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.metadata())
                        .containsEntry("parallelGroup", "invoice-batch")
                        .containsEntry("effectiveConcurrency", false));
    }

    @Test
    void executesDependencyReadyTasksInAcceptedOrderAndCorrectsWrongAssignmentWithinOneStep() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan(
                "plan-ordered",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "Parse invoice", PlanTaskStatus.PENDING,
                                "invoiceParser", "Parse the invoice", List.of(), List.of("parsed"), null, null),
                        new PlanTask("t-2", "Look up expenses", PlanTaskStatus.PENDING,
                                "expenseLookup", "Find expenses", List.of(), List.of("matches"), null, null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"expenseLookup","toolArguments":{}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{}}
                """,
                """
                {"stepAction":"CALL_TOOL","taskId":"t-2","toolName":"expenseLookup","toolArguments":{}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Mission complete"}
                """);
        List<String> invocationOrder = new ArrayList<>();
        BoundCapability invoiceParser = new BoundCapability(tool("invoiceParser", "parsed").metadata(),
                (arguments, taskId) -> {
                    invocationOrder.add(taskId);
                    return "parsed";
                });
        BoundCapability expenseLookup = new BoundCapability(tool("expenseLookup", "matches").metadata(),
                (arguments, taskId) -> {
                    invocationOrder.add(taskId);
                    return "matches";
                });
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-ordered-assignment", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            assertThat(executeMission(engine, session, definition(), chatClient, List.of(invoiceParser, expenseLookup)))
                    .isEqualTo("Mission complete");
        }

        assertThat(invocationOrder).containsExactly("t-1", "t-2");
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("This worker is assigned task 't-1'. Return CALL_TOOL with exactly that taskId.");
        List<TraceRecord> records = readRecords(session);
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_ACTION_PROPOSED)
                .findFirst()
                .orElseThrow()
                .metadata())
                .containsEntry("planId", "plan-ordered")
                .containsEntry("stepNumber", 1)
                .containsEntry("assignedTaskId", "t-1")
                .containsEntry("taskId", "t-2");
        assertThat(records.stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_ACTION_REJECTED)
                .findFirst()
                .orElseThrow()
                .metadata())
                .containsEntry("assignedTaskId", "t-1")
                .containsEntry("stepNumber", 1);
    }

    @Test
    void preservesExplicitNullArgumentsInTheStepLoopToolPath()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan("plan-null", "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"), PlanStatus.VALID, List.of(new PlanTask("t-1", "Parse invoice", PlanTaskStatus.PENDING,
                        "invoiceParser", "Parse invoice", List.of(), List.of("parsed"), null, null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                """
                {"stepAction":"CALL_TOOL","taskId":"t-1","toolName":"invoiceParser","toolArguments":{"rawText":null}}
                """,
                """
                {"stepAction":"FINAL_RESPONSE","finalResponse":"Mission complete"}
                """);
        AtomicReference<Map<String, Object>> observedArguments = new AtomicReference<>();
        BoundCapability template = tool("invoiceParser", "unused");
        BoundCapability capability = new BoundCapability(template.metadata(), (arguments, taskId) -> {
            observedArguments.set(arguments);
            return "parsed";
        });
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-null", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor);
            assertThat(executeMission(engine, session, definition(), chatClient, List.of(capability)))
                    .isEqualTo("Mission complete");
        }

        assertThat(observedArguments.get()).containsEntry("rawText", null);
    }

    @Test
    void rejectsWholeGroupedUnitBeforeAdmissionWhenFinalSynthesisWouldNotFit()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan("plan-budget", "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"), PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "First", PlanTaskStatus.PENDING, "invoiceParser", "first",
                                List.of(), List.of(), "batch", null),
                        new PlanTask("t-2", "Second", PlanTaskStatus.PENDING, "invoiceParser", "second",
                                List.of(), List.of(), "batch", null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        AtomicInteger invocations = new AtomicInteger();
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(),
                (arguments, taskId) -> {
                    invocations.incrementAndGet();
                    return "done";
                });
        YamlSkillDefinition definition = definitionWithMaxSteps(2);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-group-budget", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThatThrownBy(() -> executeMission(engine, session, definition, new SequenceChatClient(), List.of(capability)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exhausted 2 steps");
        }

        assertThat(invocations).hasValue(0);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.PENDING, PlanTaskStatus.PENDING);
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_UPDATED);
    }

    @Test
    void admitsExactlyNAssignmentsPlusFinalAtMaxStepsNPlusOne()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = new ExecutionPlan("plan-exact-budget", "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"), PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "First", PlanTaskStatus.PENDING, "invoiceParser", "first",
                                List.of(), List.of(), null, null),
                        new PlanTask("t-2", "Second", PlanTaskStatus.PENDING, "expenseLookup", "second",
                                List.of(), List.of(), null, null)));
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}",
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-2\",\"toolName\":\"expenseLookup\",\"toolArguments\":{}}",
                "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"done\"}");
        YamlSkillDefinition definition = definitionWithMaxSteps(3);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-exact-budget", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, missionExecutor, definition);
            assertThat(executeMission(engine, session, definition, chatClient,
                    List.of(tool("invoiceParser", "first"), tool("expenseLookup", "second"))))
                    .isEqualTo("done");
        }

        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.STEP_STARTED))
                .extracting(record -> record.metadata().get("stepNumber"))
                .containsExactly(1, 2, 3);
        assertThat(readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)
                .filter(record -> record.metadata().containsKey("assignedTaskId")))
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.metadata())
                        .containsEntry("effectiveConcurrency", false)
                        .doesNotContainKey("parallelGroup"));
    }

    @Test
    void rejectsNonPositiveMaxStepsBeforeLoopStarts() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan plan = singleTaskPlan();
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        SequenceChatClient chatClient = new SequenceChatClient();
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setMaxSteps(0);
        YamlSkillDefinition invalidDefinition = new YamlSkillDefinition(
                new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-loop-max-steps-zero", "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService,
                    planningService,
                    missionExecutor,
                    invalidDefinition);

            assertThatThrownBy(() -> executeMission(
                    engine,
                    session,
                    invalidDefinition,
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("max_steps > 0")
                    .hasMessageContaining("was 0");
        }
    }

    private static StepLoopMissionExecutionEngine engine(DefaultExecutionStateService stateService,
                                                         PlanningService planningService,
                                                         ExecutorService missionExecutor) {
        return engine(stateService, planningService, missionExecutor, definition());
    }

    private static String executeMission(StepLoopMissionExecutionEngine engine,
                                         LoomspanSession session,
                                         YamlSkillDefinition definition,
                                         ai.loomspan.internal.model.ModelInteraction chatClient,
                                         List<BoundCapability> visibleTools) {
        return executeMission(engine, session, definition, "Check duplicate invoices", null, chatClient, visibleTools);
    }

    private static String executeMission(StepLoopMissionExecutionEngine engine,
                                         LoomspanSession session,
                                         YamlSkillDefinition definition,
                                         String objective,
                                         @Nullable Map<String, Object> missionInput,
                                         ai.loomspan.internal.model.ModelInteraction chatClient,
                                         List<BoundCapability> visibleTools) {
        ExecutionBinding binding = ExecutionBindingScope.current()
                .orElseGet(() -> TestExecutionBindings.missionBinding(session));
        try
        {
            return ExecutionBindingScope.supplyWith(binding, () -> engine.executeMission(
                    session, definition, objective, missionInput, chatClient, visibleTools, true, null));
        }
        finally
        {
            var mission = binding.requireMission();
            session.replaceRetainedRootState(
                    mission.currentPlan().orElse(null),
                    mission.lastLinterOutcome().orElse(null),
                    mission.lastOutputSchemaOutcome().orElse(null));
        }
    }

    private static void assertExecutorBindingsCleared(ExecutorService executor, int workerCount) throws Exception
    {
        CountDownLatch allWorkersOccupied = new CountDownLatch(workerCount);
        List<Future<Optional<ExecutionBinding>>> probes = new ArrayList<>(workerCount);
        for (int index = 0; index < workerCount; index++)
        {
            probes.add(executor.submit(() -> {
                allWorkersOccupied.countDown();
                assertThat(allWorkersOccupied.await(3, TimeUnit.SECONDS))
                        .as("every reusable executor thread must participate in binding cleanup verification")
                        .isTrue();
                return ExecutionBindingScope.current();
            }));
        }
        for (Future<Optional<ExecutionBinding>> probe : probes)
        {
            assertThat(probe.get(3, TimeUnit.SECONDS)).isEmpty();
        }
    }

    private static StepLoopMissionExecutionEngine engine(DefaultExecutionStateService stateService,
                                                         PlanningService planningService,
                                                         ExecutorService missionExecutor,
                                                         YamlSkillDefinition definition) {
        return engine(stateService, planningService, missionExecutor, definition, Duration.ofSeconds(5));
    }

    private static StepLoopMissionExecutionEngine engine(DefaultExecutionStateService stateService,
                                                         PlanningService planningService,
                                                         ExecutorService missionExecutor,
                                                         YamlSkillDefinition definition,
                                                         Duration missionTimeout) {
        return new StepLoopMissionExecutionEngine(
                planningService,
                stateService,
                mock(ai.loomspan.internal.core.CapabilityRegistry.class),
                new StubYamlSkillCatalog(definition),
                missionTimeout,
                missionExecutor,
                new NoOpSessionUsageService());
    }

    private static YamlSkillDefinition definition() {
        return definition("rootVisibleSkill");
    }

    private static YamlSkillDefinition definition(String name) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition definitionWithMaxSteps(int maxSteps)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setMaxSteps(maxSteps);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition definitionWithConcurrency(boolean concurrency)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setConcurrency(concurrency);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition definitionWithPrompt() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setPrompt("STEP_PROMPT_SENTINEL");
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition attachmentDefinition() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
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
        byte[] marker = content.getBytes(StandardCharsets.UTF_8);
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

    private static YamlSkillDefinition definitionWithOutputSchema() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setOutputSchemaMaxRetries(1);
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType("object");
        schema.setAdditionalProperties(false);
        YamlSkillManifest.OutputSchemaManifest resultField = new YamlSkillManifest.OutputSchemaManifest();
        resultField.setType("string");
        schema.setProperties(Map.of("result", resultField));
        schema.setRequired(List.of("result"));
        manifest.setOutputSchema(schema);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition definitionWithOutputSchemaAndEvidenceContract() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setOutputSchemaMaxRetries(1);
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType("object");
        schema.setAdditionalProperties(false);
        YamlSkillManifest.OutputSchemaManifest resultField = new YamlSkillManifest.OutputSchemaManifest();
        resultField.setType("string");
        YamlSkillManifest.OutputSchemaManifest reasoningField = new YamlSkillManifest.OutputSchemaManifest();
        reasoningField.setType("string");
        schema.setProperties(Map.of(
                "result", resultField,
                "reasoning", reasoningField));
        schema.setRequired(List.of("result"));
        manifest.setOutputSchema(schema);
        Map<String, String> contract = Map.of(
                "result", "invoiceParser",
                "reasoning", "expenseLookup");
        return new YamlSkillDefinition(
                new ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(contract));
    }

    private static YamlSkillDefinition definitionWithRegexLinter() {
        return definitionWithRegexLinter(1);
    }

    private static YamlSkillDefinition definitionWithRegexLinter(int maxRetries) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        YamlSkillManifest.LinterManifest linter = new YamlSkillManifest.LinterManifest();
        linter.setType("regex");
        linter.setMaxRetries(maxRetries);
        YamlSkillManifest.RegexManifest regex = new YamlSkillManifest.RegexManifest();
        regex.setPattern("^APPROVED:.*$");
        regex.setMessage("Must start with APPROVED:");
        linter.setRegex(regex);
        manifest.setLinter(linter);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static ExecutionPlan singleTaskPlan() {
        return new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(new PlanTask("t-1", "Parse invoice", PlanTaskStatus.PENDING,
                        "invoiceParser", "Extract invoice data", List.of(), List.of("parsedInvoice"), null, null)));
    }

    private static ExecutionPlan twoTaskPlan() {
        return new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                List.of(
                        new PlanTask("t-1", "Parse invoice", PlanTaskStatus.PENDING,
                                "invoiceParser", "Extract invoice data", List.of(), List.of("parsedInvoice"), null, null),
                        new PlanTask("t-2", "Look up expenses", PlanTaskStatus.PENDING,
                                "expenseLookup", "Find matching expenses", List.of("t-1"), List.of("expenses"), null, null)));
    }

    private static ExecutionPlan groupedPlan(String planId, boolean includeLaterUnit)
    {
        List<PlanTask> tasks = new ArrayList<>(List.of(
                new PlanTask("t-1", "First", PlanTaskStatus.PENDING,
                        "invoiceParser", "first", List.of(), List.of(), "batch", null),
                new PlanTask("t-2", "Second", PlanTaskStatus.PENDING,
                        "invoiceParser", "second", List.of(), List.of(), "batch", null)));
        if (includeLaterUnit)
        {
            tasks.add(new PlanTask("t-3", "Later", PlanTaskStatus.PENDING,
                    "invoiceParser", "later", List.of("t-1"), List.of(), null, null));
        }
        return new ExecutionPlan(planId, "rootVisibleSkill", Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID, tasks);
    }

    private static BoundCapability tool(String name, String result) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.capability(name).metadata(),
                (arguments, linkedTaskId) -> result);
    }

    private static BoundCapability toolWithSchema(String name, String inputSchema, String result) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.capability(name, inputSchema).metadata(),
                (arguments, linkedTaskId) -> result);
    }

    private static BoundCapability toolWithContract(String name, String inputSchema, String contractSchema, String result) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.contractAware(name, inputSchema, contractSchema).metadata(),
                (arguments, linkedTaskId) -> result);
    }

    private static BoundCapability failingTool(String name) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.capability(name).metadata(),
                (arguments, linkedTaskId) -> { throw new IllegalStateException("parser exploded"); });
    }

    private static BoundCapability realToolCallback(DefaultExecutionStateService stateService,
                                                 PlanningService planningService,
                                                 AtomicInteger routerCalls,
                                                 LoomspanSession session) {
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:invoiceParser",
                "invoiceParser",
                "invoiceParser",
                SkillExecutionDescriptor.from(EXECUTION_CONFIGURATION), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("invoiceParser", "invoiceParser"),
                null);
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        when(router.execute(eq(capability), any(), eq(session), any())).thenAnswer(invocation -> {
            routerCalls.incrementAndGet();
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) invocation.getArgument(1);
            return Map.of("invoice", arguments.get("rawText"));
        });
        DefaultCapabilityInvoker factory = new DefaultCapabilityInvoker(router, planningService, stateService);
        return factory.bind(session, definition(), List.of(capability), null).getFirst();
    }

    private static List<TraceRecord> readRecords(LoomspanSession session) {
        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        return records;
    }

    private static final class InitializingPlanningService implements PlanningService {

        private final DefaultExecutionStateService stateService;
        private final DefaultPlanningService delegate;
        private final ExecutionPlan initialPlan;

        private InitializingPlanningService(DefaultExecutionStateService stateService, ExecutionPlan initialPlan) {
            this.stateService = stateService;
            this.delegate = new DefaultPlanningService(stateService);
            this.initialPlan = initialPlan;
        }

        @Override
        public Optional<ExecutionPlan> initializePlan(LoomspanSession session,
                                                      String objective,
                                                      @Nullable Map<String, Object> missionInput,
                                                      YamlSkillDefinition definition,
                                                      ai.loomspan.internal.model.ModelInteraction chatClient,
                                                      List<BoundCapability> visibleTools) {
            stateService.storePlan(initialPlan);
            stateService.logPlanCreated(session, initialPlan, Map.of(
                    "attemptId", "attempt-initial-plan",
                    "retrySequenceId", "retry-initial-plan"));
            return Optional.of(initialPlan);
        }

        @Override
        public Optional<String> markToolStarted(LoomspanSession session,
                                                ai.loomspan.internal.core.CapabilityMetadata capability) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ExecutionPlan> markToolCompleted(LoomspanSession session,
                                                         String taskId,
                                                         String capabilityName) {
            return delegate.markToolCompleted(session, taskId, capabilityName);
        }

        @Override
        public Optional<ExecutionPlan> markToolFailed(LoomspanSession session,
                                                      String taskId,
                                                      String capabilityName,
                                                      RuntimeException ex) {
            return delegate.markToolFailed(session, taskId, capabilityName, ex);
        }
    }

    private static final class StubYamlSkillCatalog extends YamlSkillCatalog {

        private final YamlSkillDefinition definition;

        private StubYamlSkillCatalog(YamlSkillDefinition definition) {
            super(new ai.loomspan.autoconfigure.LoomspanProperties(),
                    new ai.loomspan.autoconfigure.LoomspanProperties.Skills());
            this.definition = definition;
        }

        @Override
        public YamlSkillDefinition getSkill(String name) {
            return definition.manifest().getName().equals(name) ? definition : null;
        }
    }

    private static final class SequenceChatClient implements ai.loomspan.internal.model.ModelInteraction {

        private final Deque<String> responses = new ArrayDeque<>();
        private final List<String> systemMessagesSeen = new ArrayList<>();
        private final List<String> userMessagesSeen = new ArrayList<>();
        private final List<CapturedMedia> userMediaSeen = new ArrayList<>();

        private SequenceChatClient(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        List<String> systemMessagesSeen() {
            return systemMessagesSeen;
        }

        List<String> userMessagesSeen() {
            return userMessagesSeen;
        }

        List<CapturedMedia> userMediaSeen() {
            return userMediaSeen;
        }

        @Override
        public ai.loomspan.internal.model.ModelInteractionResult call(
                ai.loomspan.internal.model.ModelInteractionRequest request) {
            systemMessagesSeen.add(request.systemPrompt());
            userMessagesSeen.add(request.input().userText());
            request.input().attachments().forEach(attachment -> userMediaSeen.add(
                    new CapturedMedia(MimeType.valueOf(attachment.contentType()), attachment.resource())));
            if (responses.peekFirst() == BLOCK_UNTIL_INTERRUPTED) {
                responses.removeFirst();
                try {
                    new java.util.concurrent.CountDownLatch(1).await();
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("model interaction interrupted", ex);
                }
            }
            String next = responses.pollFirst();
            if (next == null) {
                throw new IllegalStateException("No more queued chat responses");
            }
            return new ai.loomspan.internal.model.ModelInteractionResult(next, Map.of(
                    ai.loomspan.internal.core.ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY,
                    request.traceContext().nextAttempt()));
        }

        private record CapturedMedia(MimeType mimeType, Resource resource) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertTransition(TraceRecord record, String kind, List<String> taskIds,
            @Nullable String parallelGroup, @Nullable Boolean effectiveConcurrency, @Nullable String outcome)
    {
        assertThat(record.metadata()).containsKey("transition");
        Map<String, Object> transition = (Map<String, Object>) record.metadata().get("transition");
        assertThat(transition)
                .containsEntry("kind", kind)
                .containsEntry("taskIds", taskIds)
                .containsEntry("parallelGroup", parallelGroup);
        if (kind.equals("ADMISSION"))
        {
            assertThat(transition).containsOnlyKeys("kind", "taskIds", "parallelGroup", "effectiveConcurrency")
                    .containsEntry("effectiveConcurrency", effectiveConcurrency);
        }
        else
        {
            assertThat(transition).containsOnlyKeys("kind", "taskIds", "parallelGroup", "outcome")
                    .containsEntry("outcome", outcome);
        }
    }

    private static final class RejectThirdSubmissionExecutor extends AbstractExecutorService
    {
        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final RejectedExecutionException rejection;
        private final CountDownLatch firstWorkerReturned = new CountDownLatch(1);
        private final AtomicInteger submissions = new AtomicInteger();

        private RejectThirdSubmissionExecutor(RejectedExecutionException rejection)
        {
            this.rejection = rejection;
        }

        int submissions() { return submissions.get(); }

        @Override
        public void execute(Runnable command)
        {
            int ordinal = submissions.incrementAndGet();
            if (ordinal == 3)
            {
                try
                {
                    if (!firstWorkerReturned.await(2, TimeUnit.SECONDS))
                    {
                        throw new AssertionError("first group worker did not return before rejection");
                    }
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("rejection coordination was interrupted", ex);
                }
                throw rejection;
            }
            if (ordinal == 2)
            {
                delegate.execute(() -> {
                    try { command.run(); }
                    finally { firstWorkerReturned.countDown(); }
                });
                return;
            }
            delegate.execute(command);
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
        {
            return delegate.awaitTermination(timeout, unit);
        }
    }

    private static final class PauseAfterFirstGroupSubmissionExecutor extends AbstractExecutorService
    {
        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final CountDownLatch firstGroupMemberSubmitted = new CountDownLatch(1);
        private final AtomicInteger submissions = new AtomicInteger();

        int submissions() { return submissions.get(); }

        boolean firstGroupMemberSubmitted() { return firstGroupMemberSubmitted.getCount() == 0; }

        @Override
        public void execute(Runnable command)
        {
            int ordinal = submissions.incrementAndGet();
            delegate.execute(command);
            if (ordinal != 2) return;
            firstGroupMemberSubmitted.countDown();
            try
            {
                new CountDownLatch(1).await();
            }
            catch (InterruptedException ignored)
            {
                // Return from submit with the interrupt cleared so the lifecycle gate, not the thread flag, stops dispatch.
            }
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
        {
            return delegate.awaitTermination(timeout, unit);
        }
    }

    private static final class TaskAddressedModel implements ai.loomspan.internal.model.ModelInteraction
    {
        private final Map<String, String> taskTools;
        private final AtomicReference<String> finalPrompt;

        private TaskAddressedModel(Map<String, String> taskTools, AtomicReference<String> finalPrompt)
        {
            this.taskTools = Map.copyOf(taskTools);
            this.finalPrompt = finalPrompt;
        }

        @Override
        public ai.loomspan.internal.model.ModelInteractionResult call(
                ai.loomspan.internal.model.ModelInteractionRequest request)
        {
            String prompt = request.systemPrompt();
            String response = taskTools.entrySet().stream()
                    .filter(entry -> prompt.contains("--- ASSIGNED TASK ---\nID: " + entry.getKey()))
                    .findFirst()
                    .map(entry -> "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"%s\",\"toolName\":\"%s\",\"toolArguments\":{}}"
                            .formatted(entry.getKey(), entry.getValue()))
                    .orElseGet(() -> {
                        finalPrompt.set(prompt);
                        return "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"done\"}";
                    });
            return new ai.loomspan.internal.model.ModelInteractionResult(response, Map.of(
                    ai.loomspan.internal.core.ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY,
                    request.traceContext().nextAttempt()));
        }
    }

    /** Triggers cutoff only after the second serialized member actually enters application work. */
    private static final class SerializedMemberTimeoutExecutor extends java.util.concurrent.AbstractExecutorService
    {
        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final CountDownLatch started;
        private SerializedMemberTimeoutExecutor(CountDownLatch started) { this.started = started; }
        @Override protected <T> java.util.concurrent.RunnableFuture<T> newTaskFor(java.util.concurrent.Callable<T> callable)
        {
            return new java.util.concurrent.FutureTask<>(callable) {
                @Override public T get(long timeout, TimeUnit unit) throws InterruptedException, java.util.concurrent.TimeoutException
                {
                    if (!started.await(5, TimeUnit.SECONDS)) throw new AssertionError("Serialized member did not start");
                    throw new java.util.concurrent.TimeoutException("controlled serialized member timeout");
                }
            };
        }
        @Override public void execute(Runnable command) { delegate.execute(command); }
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
        { return delegate.awaitTermination(timeout, unit); }
    }
}
