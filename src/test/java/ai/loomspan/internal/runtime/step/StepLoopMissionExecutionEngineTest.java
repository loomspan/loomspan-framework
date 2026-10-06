package ai.loomspan.internal.runtime.step;

import ai.loomspan.testkit.CorrectionEvidenceFixtures;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.LoomspanStackOverflowException;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.TestLoomspanSessions;
import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.core.ModelTraceContext;
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
import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.runtime.input.ObjectFieldPath;

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
import java.util.LinkedHashMap;
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

    @Test
    void fullyBoundAssignedTaskSkipsParentDispatchModelInteraction() {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var model = new SequenceChatClient("{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"Finished\"}");
        var binding = new ChildInputBinding(ObjectFieldPath.parse("/value", false),
                ChildInputBinding.SourceKind.INPUT, ObjectFieldPath.parse("/source", true), null);
        var calls = new AtomicInteger();
        var tool = new BoundCapability(toolWithSchema("invoiceParser",
                "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"required\":[\"value\"],\"additionalProperties\":false}", "unused").metadata(),
                List.of(binding), (arguments, taskId, sources) -> {
                    assertThat(arguments).isEmpty();
                    assertThat(taskId).isEqualTo("t-1");
                    calls.incrementAndGet();
                    return "exact child result";
                });
        var session = TestLoomspanSessions.withId("direct-dispatch", "entry", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor),
                    session, definition(), "Review source", Map.of("source", "exact"), model, List.of(tool))).isEqualTo("Finished");
        }
        assertThat(calls).hasValue(1);
        assertThat(model.systemMessagesSeen()).hasSize(1).allSatisfy(prompt -> assertThat(prompt).doesNotContain("--- ASSIGNED TASK ---"));
        assertThat(readRecords(session)).noneMatch(record -> "rootVisibleSkill#step-1-model".equals(record.route())
                || "rootVisibleSkill#step-1".equals(record.route()) && record.recordType() == TraceRecordType.STEP_ACTION_PROPOSED);
        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.STEP_STARTED))
                .anySatisfy(record -> assertThat(record.metadata()).containsEntry("dispatchOrigin", "framework")
                        .containsEntry("dispatchReason", "eligible").containsEntry("assignedTaskId", "t-1"));
    }

    private static final String BLOCK_UNTIL_INTERRUPTED = "__block_until_interrupted__";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void directDispatchRechecksCancellationAndInterruptionAfterValidationBeforeInvocation(boolean cancel) {
        var sideEffects = new AtomicInteger();
        var reachedBoundary = new AtomicBoolean();
        var state = new DefaultExecutionStateService(FIXED_CLOCK) {
            @Override public void recordStepEvent(LoomspanSession session, ai.loomspan.internal.core.ExecutionFrame frame,
                    TraceRecordType type, Map<String, Object> metadata, Object payload) {
                super.recordStepEvent(session, frame, type, metadata, payload);
                if (type == TraceRecordType.STEP_ACTION_VALIDATED && "framework".equals(metadata.get("dispatchOrigin"))) {
                    reachedBoundary.set(true);
                    if (cancel) {
                        var binding = ExecutionBindingScope.requireCurrent();
                        var failure = new IllegalStateException("cancel at dispatch boundary");
                        binding.requireMission().lifecycle().beginCancellation(binding, failure,
                                () -> recordFailure(session, failure, Map.of("message", failure.getMessage())), false);
                    } else Thread.currentThread().interrupt();
                }
            }
        };
        var tool = new BoundCapability(directTool("invoiceParser", "unused").metadata(), List.of(),
                (arguments, taskId, sources) -> { sideEffects.incrementAndGet(); return "unexpected"; });
        var session = TestLoomspanSessions.withId("direct-boundary-" + cancel, "entry", 3);
        var model = new SequenceChatClient();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor),
                    session, definition(), model, List.of(tool))).isInstanceOf(RuntimeException.class);
        }
        assertThat(reachedBoundary).isTrue();
        assertThat(sideEffects).hasValue(0);
        assertThat(model.systemMessagesSeen()).isEmpty();
    }
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration EXECUTION_CONFIGURATION =
            new EffectiveSkillExecutionConfiguration("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    @Test
    void boundOverrideCorrectionKeepsProjectedGuidanceAndInvokesOnlyAcceptedSiblingAction() {
        String schema = """
                {"type":"object","properties":{"context":{"type":"object",
                 "properties":{"evidence":{},"reasoning":{"type":"string"}},"required":["evidence"],"additionalProperties":true}},
                 "required":["context"],"additionalProperties":false}
                """;
        var binding = new ChildInputBinding(ObjectFieldPath.parse("/context/evidence", false),
                ChildInputBinding.SourceKind.INPUT, ObjectFieldPath.parse("/source", true), null);
        for (String override : List.of("{\"context\":{\"evidence\":\"equal\"}}", "{\"context\":{\"evidence\":null}}",
                "{\"context\":\"scalar\"}", "{\"context\":null}")) {
            String rejected = "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":" + override + "}";
            var client = new SequenceChatClient(rejected,
                    "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{\"context\":{\"reasoning\":\"new\"}}}",
                    "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"Finished\"}");
            var calls = new AtomicInteger();
            var tool = new BoundCapability(toolWithSchema("invoiceParser", schema, "unused").metadata(), List.of(binding),
                    (arguments, taskId, sources) -> {
                        assertThat(client.systemMessagesSeen()).hasSize(2);
                        assertThat(arguments).isEqualTo(Map.of("context", Map.of("reasoning", "new")));
                        calls.incrementAndGet();
                        return "accepted";
                    });
            var state = new DefaultExecutionStateService(FIXED_CLOCK);
            var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("binding-correction-" + override.hashCode(), "entry", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor),
                        session, definition(), "Review source", Map.of("source", "equal"), client, List.of(tool))).isEqualTo("Finished");
            }
            assertThat(calls).hasValue(1);
            for (String request : client.systemMessagesSeen().subList(0, 2))
                assertThat(request).contains("Supply only unbound arguments", "Effective model argument schema: " + tool.inputSchema())
                        .doesNotContain("Required fields: [evidence]", "Required fields: [context]");
            assertThat(client.systemMessagesSeen().get(1)).contains("YOUR PREVIOUS ACTION WAS INVALID");
            assertThat(client.userMessagesSeen().get(1)).contains("Binding override", "/context/evidence");
            assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(client.userMessagesSeen().get(1))).isEqualTo(rejected);
        }
    }

    @Test
    void forwardsSelectedTaskResultWithoutFinalModelCall() {
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planning = new InitializingPlanningService(state, singleTaskPlan());
        YamlSkillManifest manifest = definitionWithMaxSteps(1).manifest();
        YamlSkillManifest.OutputFromManifest selector = new YamlSkillManifest.OutputFromManifest();
        selector.setSkill("invoiceParser");
        manifest.setOutputFrom(selector);
        YamlSkillDefinition forwarding = new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
        SequenceChatClient model = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("forward-one", "rootVisibleSkill", 3);
        String exact = "  [\"quoted\", {\"secret\":\"preserved\"}]\n";
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(state, planning, executor, forwarding), session, forwarding, model,
                    List.of(tool("invoiceParser", exact)))).isEqualTo(exact);
        }
        assertThat(readRecords(session).stream().filter(record -> record.recordType().name().equals("RESULT_FORWARDED")))
                .hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\"quoted Java String\"", "null", "{\"finalResponse\":\"business value\"}", "[3,2,1]"})
    void forwardingPreservesEveryDirectResultString(String exact) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var definition = forwardingDefinition(1, false);
        var model = new TaskAddressedModel(Map.of("t-1", "invoiceParser"), new AtomicReference<>());
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("exact-forward", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition),
                    session, definition, model, List.of(tool("invoiceParser", exact)))).isEqualTo(exact);
        }
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.STRUCTURED_OUTPUT_RECORDED
                || record.recordType() == TraceRecordType.LINTER_RECORDED || record.recordType() == TraceRecordType.EVIDENCE_VALIDATION_PASSED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forwardingWaitsForWholeSelectedUnitAndLaterWork(boolean concurrent) throws Exception {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var tasks = List.of(
                new PlanTask("t-1", "Answer", PlanTaskStatus.PENDING, "invoiceParser", "answer", List.of(), List.of(), "batch", null),
                new PlanTask("t-2", "Required sibling", PlanTaskStatus.PENDING, "expenseLookup", "finish", List.of(), List.of(), "batch", null),
                new PlanTask("t-3", "Accepted optional work", PlanTaskStatus.PENDING, "optional", "finish", List.of(), List.of(), null, null));
        var plan = new ExecutionPlan("forward-complete", "rootVisibleSkill", Instant.EPOCH, PlanStatus.VALID, tasks);
        var definition = forwardingDefinition(3, concurrent);
        CountDownLatch siblingStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger later = new AtomicInteger();
        AtomicReference<String> unexpectedFinal = new AtomicReference<>();
        var model = new TaskAddressedModel(Map.of("t-1", "invoiceParser", "t-2", "expenseLookup", "t-3", "optional"), unexpectedFinal);
        var selected = directTool("invoiceParser", "x".repeat(8000) + " selected");
        var sibling = new BoundCapability(tool("expenseLookup", "unused").metadata(), java.util.List.of(), (arguments, taskId, sourceResults) -> {
            siblingStarted.countDown();
            try { assertThat(release.await(3, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            return "sibling";
        });
        var optional = new BoundCapability(directTool("optional", "unused").metadata(), java.util.List.of(), (arguments, taskId, sourceResults) -> { later.incrementAndGet(); return "optional"; });
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("join-forward", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<String> result = caller.submit(() -> executeMission(engine(state, new InitializingPlanningService(state, plan), executor, definition),
                    session, definition, model, List.of(selected, sibling, optional)));
            try {
                assertThat(siblingStarted.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                assertThat(later).hasValue(0);
                assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_FORWARDED);
            } finally { release.countDown(); }
            assertThat(result.get(3, TimeUnit.SECONDS)).isEqualTo("x".repeat(8000) + " selected");
        }
        assertThat(later).hasValue(1);
        assertThat(unexpectedFinal).hasNullValue();
        assertThat(session.getExecutionPlanSnapshot().tasks()).allMatch(task -> task.status() == PlanTaskStatus.COMPLETED);
        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.STEP_STARTED)).hasSize(3);
        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.RESULT_FORWARDED)).singleElement()
                .satisfies(record -> {
                    assertThat(record.frameType()).isEqualTo(TraceFrameType.ROOT_MISSION);
                    assertThat(record.metadata()).containsEntry("skillName", "rootVisibleSkill").containsEntry("planId", "forward-complete")
                            .containsEntry("linkedTaskId", "t-1").containsEntry("capabilityName", "invoiceParser");
                });
        assertThat(session.getExecutionJournal().getEntriesSnapshot()).anyMatch(entry -> entry.type() == ai.loomspan.internal.core.JournalEntryType.RESULT_FORWARDED);
    }

    @Test
    void forwardingRunsPrerequisiteBeforeSelectedTask() {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var tasks = List.of(
                new PlanTask("prep", "Prepare", PlanTaskStatus.PENDING, "prepare", "prepare input", List.of(), List.of(), null, null),
                new PlanTask("t-1", "Answer", PlanTaskStatus.PENDING, "invoiceParser", "answer", List.of("prep"), List.of(), null, null));
        var plan = new ExecutionPlan("forward-prerequisite", "rootVisibleSkill", Instant.EPOCH, PlanStatus.VALID, tasks);
        var definition = forwardingDefinition(2, false);
        var delegate = new TaskAddressedModel(Map.of("prep", "prepare", "t-1", "invoiceParser"), new AtomicReference<>());
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            if (request.systemPrompt().contains("--- ASSIGNED TASK ---\nID: t-1"))
                assertThat(request.systemPrompt()).contains("EXACT_PREREQUISITE_RESULT");
            return delegate.call(request);
        };
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("prerequisite-forward", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(state, new InitializingPlanningService(state, plan), executor, definition),
                    session, definition, model, List.of(tool("prepare", "EXACT_PREREQUISITE_RESULT"), tool("invoiceParser", "selected-answer"))))
                    .isEqualTo("selected-answer");
        }
    }

    @Test
    void rejectsWholeForwardingUnitWhenAssignmentsDoNotFit() {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var tasks = List.of(
                new PlanTask("t-1", "Answer", PlanTaskStatus.PENDING, "invoiceParser", "answer", List.of(), List.of(), "batch", null),
                new PlanTask("t-2", "Sibling", PlanTaskStatus.PENDING, "expenseLookup", "finish", List.of(), List.of(), "batch", null));
        var plan = new ExecutionPlan("forward-budget", "rootVisibleSkill", Instant.EPOCH, PlanStatus.VALID, tasks);
        var definition = forwardingDefinition(1, true);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("budget-forward", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> executeMission(engine(state, new InitializingPlanningService(state, plan), executor, definition),
                    session, definition, new SequenceChatClient(), List.of(tool("invoiceParser", "answer"), tool("expenseLookup", "sibling"))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("exhausted");
        }
        assertThat(session.getExecutionPlanSnapshot().tasks()).allMatch(task -> task.status() == PlanTaskStatus.PENDING);
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_UPDATED || record.recordType() == TraceRecordType.RESULT_FORWARDED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forwardingFailureNeverProducesParentSuccess(boolean selectedFails) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var definition = forwardingDefinition(2, false);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("failure-forward", "rootVisibleSkill", 3);
        var model = new TaskAddressedModel(Map.of("t-1", "invoiceParser", "t-2", "expenseLookup"), new AtomicReference<>());
        var selected = selectedFails ? failingTool("invoiceParser") : tool("invoiceParser", "retained-selected");
        var later = selectedFails ? tool("expenseLookup", "later") : failingTool("expenseLookup");
        var binding = TestExecutionBindings.missionBinding(session);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding, () -> executeMission(
                    engine(state, new InitializingPlanningService(state, twoTaskPlan()), executor, definition),
                    session, definition, model, List.of(selected, later))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("parser exploded");
        }
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_FORWARDED);
        if (!selectedFails) assertThat(binding.requireMission().completedTaskResult("t-1")).isPresent();
    }

    @Test
    void forwardingRejectsAmbiguousOrMissingAcceptedTask() {
        for (var plan : List.of(groupedPlan("ambiguous", false),
                new ExecutionPlan("missing", "rootVisibleSkill", Instant.EPOCH, PlanStatus.VALID, List.of()))) {
            var state = new DefaultExecutionStateService(FIXED_CLOCK);
            var definition = forwardingDefinition(3, true);
            var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("invalid-forward", "rootVisibleSkill", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThatThrownBy(() -> executeMission(engine(state, new InitializingPlanningService(state, plan), executor, definition),
                        session, definition, new SequenceChatClient(), List.of(tool("invoiceParser", "unused"))))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one");
            }
            assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_FORWARDED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong-skill", "stale"})
    void forwardingRejectsInvalidRetainedCompletion(String corruption) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var completed = singleTaskPlan().updateTask("t-1", task -> new PlanTask(task.taskId(), task.title(),
                PlanTaskStatus.COMPLETED, task.capabilityName(), task.intent(), task.dependsOn(), task.expectedOutputs(), task.parallelGroup(), task.note()));
        if (corruption.equals("stale")) completed = new ExecutionPlan(completed.planId(), completed.capabilityName(), completed.createdAt(), PlanStatus.STALE, completed.tasks());
        var planning = new InitializingPlanningService(state, completed);
        var definition = forwardingDefinition(1, false);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("retained-forward", "rootVisibleSkill", 3);
        var binding = TestExecutionBindings.missionBinding(session);
        if (!corruption.equals("missing")) binding.requireMission().recordCompletedTaskResult("t-1",
                corruption.equals("wrong-skill") ? "other" : "invoiceParser", "result");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding, () -> executeMission(engine(state, planning, executor, definition),
                    session, definition, new SequenceChatClient(), List.of(tool("invoiceParser", "unused")))))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_FORWARDED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong-skill", "stale"})
    void outputAssemblyRejectsInvalidRetainedCompletion(String corruption) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var completed = singleTaskPlan().updateTask("t-1", task -> new PlanTask(task.taskId(), task.title(),
                PlanTaskStatus.COMPLETED, task.capabilityName(), task.intent(), task.dependsOn(), task.expectedOutputs(), task.parallelGroup(), task.note()));
        if (corruption.equals("stale")) completed = new ExecutionPlan(completed.planId(), completed.capabilityName(), completed.createdAt(), PlanStatus.STALE, completed.tasks());
        var planning = new InitializingPlanningService(state, completed);
        var definition = outputBindingDefinition(1, false);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("retained-assembly", "rootVisibleSkill", 3);
        var binding = TestExecutionBindings.missionBinding(session);
        if (!corruption.equals("missing")) binding.requireMission().recordCompletedTaskResult("t-1",
                corruption.equals("wrong-skill") ? "other" : "invoiceParser", "result");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding, () -> executeMission(engine(state, planning, executor, definition),
                    session, definition, new SequenceChatClient(), List.of(tool("invoiceParser", "unused")))))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_ASSEMBLED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void outputAssemblyJoinsWholeProducerUnitAndUnrelatedWork(boolean concurrent) throws Exception {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var tasks = List.of(
                new PlanTask("t-1", "Bound answer", PlanTaskStatus.PENDING, "invoiceParser", "answer", List.of(), List.of(), "batch", null),
                new PlanTask("t-2", "Sibling", PlanTaskStatus.PENDING, "expenseLookup", "finish", List.of(), List.of(), "batch", null),
                new PlanTask("t-3", "Later", PlanTaskStatus.PENDING, "optional", "finish", List.of(), List.of(), null, null));
        var plan = new ExecutionPlan("assembled-complete", "rootVisibleSkill", Instant.EPOCH, PlanStatus.VALID, tasks);
        var definition = twoProducerOutputBindingDefinition(3, concurrent);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger later = new AtomicInteger();
        AtomicReference<String> unexpectedFinal = new AtomicReference<>();
        var model = new TaskAddressedModel(Map.of("t-1", "invoiceParser", "t-2", "expenseLookup", "t-3", "optional"), unexpectedFinal);
        var sibling = new BoundCapability(tool("expenseLookup", "unused").metadata(), List.of(), (arguments, taskId, sources) -> {
            held.countDown();
            try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            return "sibling";
        });
        var optional = new BoundCapability(directTool("optional", "unused").metadata(), List.of(), (arguments, taskId, sources) -> { later.incrementAndGet(); return "later"; });
        var session = TestLoomspanSessions.withId("join-assembly", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<String> result = caller.submit(() -> executeMission(engine(state, new InitializingPlanningService(state, plan), executor, definition),
                    session, definition, model, List.of(directTool("invoiceParser", "exact answer"), sibling, optional)));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse(); assertThat(later).hasValue(0);
                assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_ASSEMBLED);
            } finally { release.countDown(); }
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("{\"report\":\"exact answer\",\"other\":\"sibling\"}");
        }
        assertThat(later).hasValue(1); assertThat(unexpectedFinal).hasNullValue();
        assertThat(readRecords(session).stream().filter(record -> record.recordType() == TraceRecordType.RESULT_ASSEMBLED)).singleElement()
                .satisfies(record -> assertThat(record.metadata()).containsEntry("modelContributionRequired", false).containsEntry("planId", "assembled-complete"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedProducerOrUnrelatedWorkCannotPublishOutputAssembly(boolean producerFails) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var definition = outputBindingDefinition(2, false);
        var session = TestLoomspanSessions.withId("failed-assembly", "rootVisibleSkill", 3);
        var model = new TaskAddressedModel(Map.of("t-1", "invoiceParser", "t-2", "expenseLookup"), new AtomicReference<>());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> executeMission(engine(state, new InitializingPlanningService(state, twoTaskPlan()), executor, definition), session,
                    definition, model, List.of(producerFails ? failingTool("invoiceParser") : tool("invoiceParser", "retained"),
                            producerFails ? tool("expenseLookup", "later") : failingTool("expenseLookup"))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("parser exploded");
        }
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_ASSEMBLED);
    }

    @Test
    void outputBindingDoesNotGrantAccessToAnInvisibleProducer() {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var definition = outputBindingDefinition(1, false);
        var session = TestLoomspanSessions.withId("invisible-output-producer", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition),
                    session, definition, new SequenceChatClient(), List.of())).isInstanceOf(IllegalStateException.class).hasMessageContaining("not eligible");
        }
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT
                || record.recordType() == TraceRecordType.RESULT_ASSEMBLED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assembledOutputPoliciesValidateExactBoundDecimals(boolean mixed) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var manifest = outputBindingDefinition(mixed ? 2 : 1, false).manifest();
        var number = new YamlSkillManifest.OutputSchemaManifest(); number.setType("number");
        var properties = new LinkedHashMap<String, YamlSkillManifest.OutputSchemaManifest>();
        properties.put("report", number);
        if (mixed) {
            var summary = new YamlSkillManifest.OutputSchemaManifest(); summary.setType("string");
            properties.put("summary", summary);
        }
        manifest.getOutputSchema().setProperties(properties);
        manifest.getOutputSchema().setRequired(mixed ? List.of("report", "summary") : List.of("report"));
        String decimal = "0.12345678901234567890123456789";
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(0);
        var regex = new YamlSkillManifest.RegexManifest();
        regex.setPattern(".*" + java.util.regex.Pattern.quote("\"report\":" + decimal) + ".*");
        linter.setRegex(regex); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
        var model = new SequenceChatClient(mixed ? new String[] {
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}",
                "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":{\"summary\":\"reasoned\"}}"} : new String[] {
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}"});
        var session = TestLoomspanSessions.withId("decimal-assembly", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition),
                    session, definition, model, List.of(tool("invoiceParser", decimal))))
                    .contains("\"report\":" + decimal);
        }
        assertThat(session.getLastLinterOutcome().orElseThrow().status()).isEqualTo(ai.loomspan.internal.linter.LinterOutcomeStatus.PASSED);
    }

    @Test
    void preservesIndependentPlanningValidatorCountersAndShortCircuitOrder() {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var base = definitionWithOutputSchemaAndEvidenceContract();
        var regex = new YamlSkillManifest.RegexManifest(); regex.setPattern(".*GOOD.*"); regex.setMessage("Use GOOD");
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(1); linter.setRegex(regex);
        var manifest = base.manifest(); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(base.resource(), manifest, EXECUTION_CONFIGURATION, base.evidenceContract());
        var client = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}",
                finalObjectEnvelope("{\"result\":3}"), finalObjectEnvelope("{\"result\":\"BAD\",\"reasoning\":\"unsupported\"}"),
                finalObjectEnvelope("{\"result\":\"BAD\"}"), finalObjectEnvelope("{\"result\":\"GOOD\"}"));
        var calls = new AtomicInteger();
        var producer = new BoundCapability(tool("invoiceParser", "unused").metadata(), List.of(),
                (arguments, taskId, sourceResults) -> { calls.incrementAndGet(); return "AUTHORITATIVE_RESULT"; });
        var session = TestLoomspanSessions.withId("combined-validation", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition),
                    session, definition, client, List.of(producer))).isEqualTo("{\"result\":\"GOOD\"}");
        }
        assertThat(calls).hasValue(1);
        var records = readRecords(session);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.STRUCTURED_OUTPUT_RECORDED)
                .map(r -> r.data().path("attempt").asInt())).containsExactly(1, 2, 2, 2);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.EVIDENCE_VALIDATION_FAILED || r.recordType() == TraceRecordType.EVIDENCE_VALIDATION_PASSED)
                .map(r -> r.metadata().get("attempt"))).containsExactly(1, 2, 2);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.LINTER_RECORDED)
                .map(r -> r.data().path("attempt").asInt())).containsExactly(1, 2);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.STEP_ACTION_REJECTED)
                .map(r -> r.metadata().get("stepNumber"))).containsExactly(2, 2, 2);
        assertThat(client.userMessagesSeen().get(4) + client.systemMessagesSeen().get(4)).contains("AUTHORITATIVE_RESULT", "Use GOOD").doesNotContain("unsupported");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 2})
    void fullBoundLinterFailureRecordsConfiguredBudgetButNeverCorrects(int budget) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var manifest = outputBindingDefinition(1, false).manifest();
        var regex = new YamlSkillManifest.RegexManifest(); regex.setPattern("^NEVER$");
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(budget); linter.setRegex(regex); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
        var model = new SequenceChatClient("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        var session = TestLoomspanSessions.withId("bound-lint-terminal", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> executeMission(engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition),
                    session, definition, model, List.of(tool("invoiceParser", "\"exact\""))))
                    .hasMessageContaining("binding_output_contract");
        }
        var outcome = session.getLastLinterOutcome().orElseThrow();
        assertThat(outcome.attempt()).isEqualTo(1); assertThat(outcome.retryCount()).isZero();
        assertThat(outcome.maxRetries()).isEqualTo(budget);
        assertThat(outcome.status()).isEqualTo(budget == 0 ? ai.loomspan.internal.linter.LinterOutcomeStatus.EXHAUSTED : ai.loomspan.internal.linter.LinterOutcomeStatus.RETRYING);
        assertThat(outcome.detail()).isEqualTo("Final response did not match the configured regex linter.");
        assertThat(model.userMessagesSeen()).hasSize(1);
        assertThat(readRecords(session)).noneMatch(r -> r.recordType() == TraceRecordType.RESULT_ASSEMBLED);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"schema,0,false", "schema,2,false", "schema,2,true", "evidence,0,false", "evidence,2,false", "evidence,2,true", "linter,0,false", "linter,2,false", "linter,2,true"})
    void preservesPlanningTerminalBudgets(String validator, int budget, boolean terminalPass) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var base = definitionWithOutputSchemaAndEvidenceContract();
        var manifest = base.manifest(); manifest.setOutputSchemaMaxRetries(budget);
        var regex = new YamlSkillManifest.RegexManifest(); regex.setPattern(".*GOOD.*"); regex.setMessage(" ");
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(budget); linter.setRegex(regex); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(base.resource(), manifest, EXECUTION_CONFIGURATION, base.evidenceContract());
        String invalid = switch (validator) {
            case "schema" -> "{\"result\":3}";
            case "evidence" -> "{\"result\":\"GOOD\",\"reasoning\":\"unsupported\"}";
            default -> "{\"result\":\"BAD\"}";
        };
        var responses = new ArrayList<String>();
        responses.add("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        for (int attempt = 0; attempt <= budget; attempt++) responses.add(finalObjectEnvelope(terminalPass && attempt == budget ? "{\"result\":\"GOOD\"}" : invalid));
        var model = new SequenceChatClient(responses.toArray(String[]::new));
        var session = TestLoomspanSessions.withId("terminal-" + validator, "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var engine = engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition);
            if (terminalPass) assertThat(executeMission(engine, session, definition, model, List.of(tool("invoiceParser", "accepted"))))
                    .isEqualTo("{\"result\":\"GOOD\"}");
            else assertThatThrownBy(() -> executeMission(engine, session, definition, model, List.of(tool("invoiceParser", "accepted"))))
                    .hasMessageContaining("Final response validation exhausted at step 2");
        }
        assertThat(model.userMessagesSeen()).hasSize(budget + 2);
        var records = readRecords(session);
        var policyRecords = records.stream().filter(r -> switch (validator) {
            case "schema" -> r.recordType() == TraceRecordType.STRUCTURED_OUTPUT_RECORDED;
            case "evidence" -> r.recordType() == TraceRecordType.EVIDENCE_VALIDATION_FAILED || r.recordType() == TraceRecordType.EVIDENCE_VALIDATION_PASSED;
            default -> r.recordType() == TraceRecordType.LINTER_RECORDED;
        }).toList();
        assertThat(policyRecords).hasSize(budget + 1);
        if (!terminalPass) {
            assertThat(records).noneMatch(r -> r.recordType() == TraceRecordType.STEP_COMPLETED && "FINAL_RESPONSE".equals(r.metadata().get("stepAction")));
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.STEP_ACTION_REJECTED)).hasSize(budget + 1);
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.STEP_FAILED)).hasSize(1);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void contributionFailureUsesSchemaBudgetAndCompleteAssemblyPolicies(boolean corrected) {
        var state = new DefaultExecutionStateService(FIXED_CLOCK);
        var manifest = outputBindingDefinition(2, false).manifest(); manifest.setOutputSchemaMaxRetries(1);
        var scalar = new YamlSkillManifest.OutputSchemaManifest(); scalar.setType("string");
        manifest.getOutputSchema().setProperties(Map.of("report", scalar, "summary", scalar, "reasoning", scalar));
        manifest.getOutputSchema().setRequired(List.of("report", "summary"));
        var regex = new YamlSkillManifest.RegexManifest(); regex.setPattern("(?s)(?=.*AUTHORITATIVE)(?=.*GOOD).*"); regex.setMessage("Use GOOD");
        var linter = new YamlSkillManifest.LinterManifest(); linter.setType("regex"); linter.setMaxRetries(1); linter.setRegex(regex); manifest.setLinter(linter);
        var definition = new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(Map.of("report", "invoiceParser", "reasoning", "expenseLookup")));
        var responses = new ArrayList<String>();
        responses.add("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        responses.add(finalObjectEnvelope("{\"report\":null,\"summary\":\"BAD\"}"));
        if (corrected) {
            responses.add(finalObjectEnvelope("{\"summary\":\"BAD\",\"reasoning\":\"unsupported\"}"));
            responses.add(finalObjectEnvelope("{\"summary\":\"BAD\"}"));
            responses.add(finalObjectEnvelope("{\"summary\":\"GOOD\"}"));
        } else responses.add(finalObjectEnvelope("{\"report\":\"AUTHORITATIVE\",\"summary\":\"BAD\"}"));
        var model = new SequenceChatClient(responses.toArray(String[]::new));
        var calls = new AtomicInteger();
        var producer = new BoundCapability(tool("invoiceParser", "unused").metadata(), List.of(),
                (arguments, taskId, sources) -> { calls.incrementAndGet(); return "\"AUTHORITATIVE\""; });
        var session = TestLoomspanSessions.withId("mixed-policy", "rootVisibleSkill", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var engine = engine(state, new InitializingPlanningService(state, singleTaskPlan()), executor, definition);
            if (corrected) assertThat(executeMission(engine, session, definition, model, List.of(producer))).contains("AUTHORITATIVE", "GOOD").doesNotContain("unsupported");
            else assertThatThrownBy(() -> executeMission(engine, session, definition, model, List.of(producer)))
                    .hasMessageContaining("Final response validation exhausted");
        }
        assertThat(calls).hasValue(1);
        assertThat(model.userMessagesSeen()).hasSize(corrected ? 5 : 3);
        assertThat(model.userMessagesSeen().get(2)).contains("Final model contribution violates output bindings");
        var records = readRecords(session);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.RESULT_ASSEMBLED)).hasSize(corrected ? 1 : 0);
        var schemas = records.stream().filter(r -> r.recordType() == TraceRecordType.STRUCTURED_OUTPUT_RECORDED).toList();
        assertThat(schemas.stream().map(r -> r.data().path("attempt").asInt())).containsExactlyElementsOf(corrected ? List.of(1, 2, 2, 2) : List.of(1, 2));
        assertThat(schemas.getFirst().data().path("issues").get(0).path("code").asText()).isEqualTo("binding_model_override");
        if (!corrected) assertThat(records).noneMatch(r -> r.recordType() == TraceRecordType.LINTER_RECORDED || r.recordType() == TraceRecordType.EVIDENCE_VALIDATION_FAILED || r.recordType() == TraceRecordType.EVIDENCE_VALIDATION_PASSED);
    }

    private static YamlSkillDefinition outputBindingDefinition(int maxSteps, boolean concurrent) {
        var manifest = definitionWithMaxSteps(maxSteps).manifest();
        manifest.setConcurrency(concurrent);
        var descriptor = new YamlSkillManifest.InputBindingManifest();
        descriptor.setFrom("child_result"); descriptor.setSkill("invoiceParser"); descriptor.setPath("");
        manifest.setOutputBindings(Map.of("/report", descriptor));
        var schema = new YamlSkillManifest.OutputSchemaManifest(); schema.setType("object"); schema.setAdditionalProperties(false);
        var report = new YamlSkillManifest.OutputSchemaManifest(); report.setType("string");
        schema.setProperties(Map.of("report", report)); schema.setRequired(List.of("report")); manifest.setOutputSchema(schema);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition twoProducerOutputBindingDefinition(int maxSteps, boolean concurrent) {
        var manifest = outputBindingDefinition(maxSteps, concurrent).manifest();
        var descriptor = new YamlSkillManifest.InputBindingManifest();
        descriptor.setFrom("child_result"); descriptor.setSkill("expenseLookup"); descriptor.setPath("");
        var bindings = new LinkedHashMap<>(manifest.getOutputBindings()); bindings.put("/other",descriptor); manifest.setOutputBindings(bindings);
        var fields = new LinkedHashMap<>(manifest.getOutputSchema().getProperties());
        var other = new YamlSkillManifest.OutputSchemaManifest(); other.setType("string"); fields.put("other",other);
        manifest.getOutputSchema().setProperties(fields); manifest.getOutputSchema().setRequired(List.of("report","other"));
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]),manifest,EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition forwardingDefinition(int maxSteps, boolean concurrent) {
        var manifest = definitionWithMaxSteps(maxSteps).manifest();
        manifest.setConcurrency(concurrent);
        var selector = new YamlSkillManifest.OutputFromManifest();
        selector.setSkill("invoiceParser");
        manifest.setOutputFrom(selector);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

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
        assertThat(chatClient.userMessagesSeen().get(1))
                .contains("Step-action validation failed", "Task 't-1' is assigned", "Too early")
                .doesNotContain("Parser reason", "JSON parsing failed");
    }

    @Test
    void syntheticTrailingBraceRecoveryReplaysShortAndLongCandidatesWithoutRejectedToolCalls()
    {
        // Self-contained synthetic fixtures reproduce the captured defect shape; they are not live captures.
        for (int payloadSize : List.of(5, 16_000))
        {
            String payload = payloadSize > 8192 ? CorrectionEvidenceFixtures.equipmentComparison("TAIL_SENTINEL")
                    : "short \ud83d\ude80TAIL_SENTINEL";
            String valid = LoomspanJacksonCodecs.defaults().planningJson().writeValueAsString(Map.of(
                    "stepAction", "CALL_TOOL", "taskId", "t-1", "toolName", "invoiceParser",
                    "toolArguments", Map.of("rawText", payload)));
            String rejected = valid + "}";
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
            SequenceChatClient chatClient = new SequenceChatClient(rejected, valid,
                    "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"Finished\"}");
            AtomicInteger calls = new AtomicInteger();
            BoundCapability countedTool = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                    (arguments, taskId, sourceResults) -> {
                        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
                        calls.incrementAndGet();
                        assertThat(arguments).containsEntry("rawText", payload);
                        return "parsed";
                    });
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "synthetic-brace-" + payloadSize, "test.entry", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
            {
                StepLoopMissionExecutionEngine engine = engine(stateService, planningService, executor, definitionWithPrompt());
                assertThat(executeMission(engine, session, definitionWithPrompt(), chatClient, List.of(countedTool)))
                        .isEqualTo("Finished");
            }
            assertThat(calls).hasValue(1);
            assertThat(chatClient.systemMessagesSeen().get(1))
                    .contains("one complete corrected action", "CALL_TOOL envelope", "STEP_PROMPT_SENTINEL")
                    .doesNotContain("Do NOT call any tool");
            String evidence = chatClient.userMessagesSeen().get(1);
            assertThat(evidence).startsWith(chatClient.userMessagesSeen().getFirst())
                    .contains("TAIL_SENTINEL", "Unexpected close marker", "Parser reason")
                    .doesNotContain("at ai.loomspan", "java.lang", "configuration");
            assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(evidence)).isEqualTo(rejected);
        }
    }

    @Test
    void repeatedSyntheticMalformedActionsExhaustOneRetryWithoutToolExecution()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, singleTaskPlan());
        String malformed = CorrectionEvidenceFixtures.equipmentComparison("MALFORMED") + "}";
        SequenceChatClient chatClient = new SequenceChatClient(malformed, malformed);
        AtomicInteger calls = new AtomicInteger();
        BoundCapability countedTool = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> { calls.incrementAndGet(); return "unexpected"; });
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("brace-exhaustion", "test.entry", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(stateService, planningService, executor, definition());
            assertThatThrownBy(() -> executeMission(engine, session, definition(), chatClient, List.of(countedTool)))
                    .hasMessageContaining("failed to produce a valid step action after 2 attempts");
        }
        assertThat(calls).hasValue(0);
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(chatClient.userMessagesSeen().get(1))).isEqualTo(malformed);
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED);
    }

    @Test
    void invalidCorrectedToolIdentityStillExhaustsWithoutExecution() {
        for (String invalidField : List.of("taskId", "toolName")) {
            DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
            PlanningService planning = new InitializingPlanningService(state, singleTaskPlan());
            String rejected = CorrectionEvidenceFixtures.equipmentComparison("IDENTITY_REJECTED") + "}";
            Map<String, Object> action = new java.util.LinkedHashMap<>(Map.of("stepAction", "CALL_TOOL",
                    "taskId", "t-1", "toolName", "invoiceParser", "toolArguments", Map.of("rawText", "INV-1")));
            action.put(invalidField, "unassigned");
            SequenceChatClient client = new SequenceChatClient(rejected,
                    LoomspanJacksonCodecs.defaults().planningJson().writeValueAsString(action));
            AtomicInteger toolCalls = new AtomicInteger();
            BoundCapability countedTool = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                    (arguments, taskId, sourceResults) -> { toolCalls.incrementAndGet(); return "unexpected"; });
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("invalid-corrected-" + invalidField, "test.entry", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                StepLoopMissionExecutionEngine engine = engine(state, planning, executor, definition());
                assertThatThrownBy(() -> executeMission(engine, session, definition(), client, List.of(countedTool)))
                        .hasMessageContaining("Step action validation exhausted");
            }
            assertThat(toolCalls).hasValue(0);
            assertThat(client.userMessagesSeen()).hasSize(2);
            assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(client.userMessagesSeen().get(1))).isEqualTo(rejected);
            assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED);
        }
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
        BoundCapability failingCapability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1)).contains("Final response violates output_schema");
    }

    @Test
    void largeFinalSchemaRetriesReplaceCandidateAndRetainCompletedEvidence() {
        for (boolean pass : List.of(true, false)) {
            DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
            ExecutionPlan plan = singleTaskPlan();
            PlanningService planning = new InitializingPlanningService(state, plan);
            YamlSkillDefinition baseDefinition = definitionWithOutputSchema();
            YamlSkillManifest manifest = baseDefinition.manifest();
            manifest.setOutputSchemaMaxRetries(2);
            YamlSkillDefinition definition = new YamlSkillDefinition(baseDefinition.resource(), manifest, baseDefinition.executionConfiguration());
            String first = finalEnvelope(CorrectionEvidenceFixtures.equipmentComparison("FINAL_ONE"));
            String second = finalEnvelope(CorrectionEvidenceFixtures.equipmentComparison("FINAL_TWO"));
            String terminal = finalEnvelope(pass ? "{\"result\":\"Finished\"}"
                    : CorrectionEvidenceFixtures.equipmentComparison("FINAL_TERMINAL"));
            SequenceChatClient client = new SequenceChatClient(
                    "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{\"rawText\":\"INV-1\"}}",
                    first, second, terminal);
            AtomicInteger toolCalls = new AtomicInteger();
            BoundCapability tool = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                    (arguments, taskId, sourceResults) -> { toolCalls.incrementAndGet(); return "AUTHORITATIVE_RESULT"; });
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("large-final-" + pass, "test.entry", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                StepLoopMissionExecutionEngine engine = engine(state, planning, executor, definition);
                if (pass) assertThat(executeMission(engine, session, definition, client, List.of(tool))).isEqualTo("{\"result\":\"Finished\"}");
                else assertThatThrownBy(() -> executeMission(engine, session, definition, client, List.of(tool)))
                        .hasMessageContaining("Final response validation exhausted");
            }
            assertThat(toolCalls).hasValue(1);
            assertThat(client.userMessagesSeen()).hasSize(4);
            assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(client.userMessagesSeen().get(2))).isEqualTo(first);
            assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(client.userMessagesSeen().get(3))).isEqualTo(second);
            assertThat(client.userMessagesSeen().get(3) + client.systemMessagesSeen().get(3))
                    .contains("AUTHORITATIVE_RESULT", "FINAL_RESPONSE envelope", "Final response violates output_schema")
                    .doesNotContain("FINAL_ONE");
            assertThat(session.getLastOutputSchemaOutcome().orElseThrow().status())
                    .isEqualTo(pass ? OutputSchemaOutcomeStatus.PASSED : OutputSchemaOutcomeStatus.EXHAUSTED);
            if (!pass) assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED
                    && "FINAL_RESPONSE".equals(record.metadata().get("stepAction")));
        }
    }

    @Test
    void completeStepCorrectionPropagatesContextLimitWithoutToolExecutionOrFallback() {
        DefaultExecutionStateService state = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planning = new InitializingPlanningService(state, singleTaskPlan());
        String rejected = CorrectionEvidenceFixtures.equipmentComparison("CONTEXT_LIMIT") + "}";
        IllegalStateException contextFailure = new IllegalStateException("context_length_exceeded: complete correction exceeds provider capacity");
        SequenceChatClient client = new SequenceChatClient(rejected);
        client.failureAfterResponses = contextFailure;
        AtomicInteger toolCalls = new AtomicInteger();
        BoundCapability tool = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> { toolCalls.incrementAndGet(); return "unexpected"; });
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("step-context-limit", "test.entry", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StepLoopMissionExecutionEngine engine = engine(state, planning, executor, definitionWithPrompt());
            assertThatThrownBy(() -> executeMission(engine, session, definitionWithPrompt(), client, List.of(tool)))
                    .isSameAs(contextFailure);
        }
        assertThat(client.userMessagesSeen()).hasSize(2);
        assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(client.userMessagesSeen().get(1))).isEqualTo(rejected);
        assertThat(toolCalls).hasValue(0);
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.STEP_COMPLETED);
        assertThat(readRecords(session)).anyMatch(record -> record.recordType() == TraceRecordType.STEP_FAILED);
    }

    private static String finalObjectEnvelope(String response) {
        return "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":" + response + "}";
    }

    private static String finalEnvelope(String response) {
        return LoomspanJacksonCodecs.defaults().planningJson().writeValueAsString(Map.of(
                "stepAction", "FINAL_RESPONSE", "finalResponse", response));
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
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1))
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
        assertThat(chatClient.systemMessagesSeen().get(0) + chatClient.userMessagesSeen().get(0)).contains("All required plan tasks are already COMPLETE");
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1))
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
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1))
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
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1))
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
        assertThat(chatClient.systemMessagesSeen().get(0)).contains("At `$` (top level): Required fields: [rawText]");
        assertThat(chatClient.systemMessagesSeen().get(1))
                .contains("YOUR PREVIOUS ACTION WAS INVALID")
                .contains("At `$` (top level): Required fields: [rawText]")
                .contains("`$.rawText` must be a string");
        for (String assigned : chatClient.systemMessagesSeen().subList(0, 2)) {
            assertThat(assigned).contains("required unbound arguments at the top level: [\"rawText\"]",
                    "empty argument object is incomplete", "\"taskId\": \"t-1\"", "\"toolName\": \"invoiceParser\"")
                    .doesNotContain("The empty toolArguments object is illustrative only", "toolArguments: {} is valid");
        }
    }

    @Test
    void sendsAuthoredDescriptionsInCompactVerboseAndCorrectiveModelRequests() {
        for (boolean complex : List.of(false, true)) {
            String schema = complex ? """
                    {"type":"object","description":"Root meaning","properties":{
                      "optional":{"type":"object","description":"Optional container","properties":{
                        "rows":{"type":"array","description":"Rows","items":{"type":"object","description":"Each record",
                          "properties":{"literal.dot":{"type":"string","description":"Line one\\nLine two; `units`"}},
                          "required":["literal.dot"],"additionalProperties":{"type":"string","description":"Additional value"}}}},
                        "required":["rows"],"additionalProperties":false},
                      "count":{"type":"integer","description":"Number of records"}},
                     "required":["count"],"additionalProperties":false}
                    """ : """
                    {"type":"object","description":"Root meaning","properties":{
                      "count":{"type":"integer","description":"Number of records"}},
                     "required":["count"],"additionalProperties":false}
                    """;
            var validArguments = Map.<String, Object>of("count", 2);
            SequenceChatClient client = new SequenceChatClient(
                    "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}",
                    "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{\"count\":2}}",
                    "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"Finished\"}");
            AtomicInteger calls = new AtomicInteger();
            BoundCapability tool = new BoundCapability(toolWithSchema("invoiceParser", schema, "unused").metadata(), java.util.List.of(),
                    (arguments, taskId, sourceResults) -> {
                        assertThat(client.systemMessagesSeen()).hasSize(2);
                        assertThat(arguments).isEqualTo(validArguments);
                        calls.incrementAndGet();
                        return "accepted";
                    });
            var state = new DefaultExecutionStateService(FIXED_CLOCK);
            var planning = new InitializingPlanningService(state, singleTaskPlan());
            var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("description-correction-" + complex, "test.entry", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executeMission(engine(state, planning, executor), session, definition(), "Supply records", null, client, List.of(tool)))
                        .isEqualTo("Finished");
            }
            assertThat(calls).hasValue(1);
            for (String system : client.systemMessagesSeen().subList(0, 2)) {
                assertThat(system).contains("Description at \"$\": \"Root meaning\"",
                        "Description at \"$.count\": \"Number of records\"", "Required fields: [count]");
                if (complex) assertThat(system).contains(
                        "Description at \"$.optional\": \"Optional container\"",
                        "Description at \"$.optional.rows\": \"Rows\"",
                        "Description at \"$.optional.rows[]\": \"Each record\"",
                        "Description at \"$.optional.rows[].*\": \"Additional value\"",
                        "Description at \"$.optional.rows[][\\\"literal.dot\\\"]\": \"Line one\\nLine two; `units`\"",
                        "Required child fields do not require an optional parent");
            }
            if (complex) assertThat(client.systemMessagesSeen().getFirst()).contains("`$.count` must be a integer");
            else assertThat(client.systemMessagesSeen().getFirst()).doesNotContain("`$.count` must be a integer");
            assertThat(client.systemMessagesSeen().get(1)).contains("YOUR PREVIOUS ACTION WAS INVALID", "`$.count` must be a integer");
            assertThat(client.systemMessagesSeen().getLast()).doesNotContain("Description at");
        }
    }

    @Test
    void preservesScopedGuidanceAndEvidenceThroughObjectBoundaryCorrection() {
        String schema = """
                {"type":"object","properties":{
                  "shipmentId":{"type":"string"},
                  "details":{"type":"object","properties":{"routingEvidence":{}},
                    "required":["routingEvidence"],"additionalProperties":true},
                  "closed":{"type":"object","additionalProperties":false}},
                 "required":["shipmentId","details"],"additionalProperties":false}
                """;
        var evidence = new java.util.LinkedHashMap<String, Object>();
        evidence.put("text", "unchanged evidence α");
        evidence.put("nullable", null);
        evidence.put("list", List.of("east", Map.of("quote", 73)));
        for (String invalidLocation : List.of("root", "closed")) {
            var validArguments = Map.<String, Object>of("shipmentId", "S-42",
                    "details", Map.of("routingEvidence", evidence, "unlisted", evidence));
            var invalidArguments = new java.util.LinkedHashMap<>(validArguments);
            if (invalidLocation.equals("root")) invalidArguments.put("forbidden", evidence);
            else invalidArguments.put("closed", Map.of("forbidden", evidence));
            String rejected = LoomspanJacksonCodecs.defaults().planningJson().writeValueAsString(Map.of(
                    "stepAction", "CALL_TOOL", "taskId", "t-1", "toolName", "invoiceParser", "toolArguments", invalidArguments));
            String valid = LoomspanJacksonCodecs.defaults().planningJson().writeValueAsString(Map.of(
                    "stepAction", "CALL_TOOL", "taskId", "t-1", "toolName", "invoiceParser", "toolArguments", validArguments));
            SequenceChatClient client = new SequenceChatClient(rejected, valid,
                    "{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"Finished\"}");
            AtomicInteger calls = new AtomicInteger();
            BoundCapability tool = new BoundCapability(toolWithSchema("invoiceParser", schema, "unused").metadata(), java.util.List.of(),
                    (arguments, taskId, sourceResults) -> {
                        assertThat(client.systemMessagesSeen()).hasSize(2);
                        assertThat(arguments).isEqualTo(validArguments);
                        calls.incrementAndGet();
                        return "parsed";
                    });
            var state = new DefaultExecutionStateService(FIXED_CLOCK);
            var planning = new InitializingPlanningService(state, singleTaskPlan());
            var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("scope-correction-" + invalidLocation, "test.entry", 3);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executeMission(engine(state, planning, executor), session, definition(), "Route shipment", Map.of("evidence", evidence), client, List.of(tool)))
                        .isEqualTo("Finished");
            }
            assertThat(calls).hasValue(1);
            for (String system : client.systemMessagesSeen().subList(0, 2)) {
                assertThat(system).contains("At `$` (top level)", "Only these fields are allowed: [closed, details, shipmentId]",
                        "At `$.details`: Required fields: [routingEvidence]", "Additional fields are allowed with any JSON value",
                        "At `$.closed`", "Only these fields are allowed: []");
            }
            assertThat(CorrectionEvidenceFixtures.decodedStepCandidate(client.userMessagesSeen().get(1))).isEqualTo(rejected);
            assertThat(client.userMessagesSeen().get(1)).startsWith(client.userMessagesSeen().getFirst()).contains("unchanged evidence α", "nullable", "quote");
        }
    }

    @Test
    void usesCanonicalMissionInputForPlanningAndStepUserMessages() {
        String objective = "Execute YAML skill 'rootVisibleSkill' using the provided mission input object."
                + " Compare rootVisibleSkillArchive and retain the audit explanation.";
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
                    objective,
                    Map.of("invoiceId", "INV-7"),
                    chatClient,
                    List.of(tool("invoiceParser", "{\"vendor\":\"Acme\"}")));

            assertThat(response).isEqualTo("Finished");
        }

        assertThat(chatClient.userMessagesSeen()).isNotEmpty();
        assertThat(chatClient.userMessagesSeen().getFirst())
                .contains("Canonical mission input")
                .contains("\"invoiceId\" : \"INV-7\"");
        assertThat(chatClient.userMessagesSeen()).hasSize(2).allSatisfy(message -> {
            assertThat(message).contains(objective, "\"invoiceId\" : \"INV-7\"");
            assertThat(message.split("Canonical mission input:", -1)).hasSize(2);
        });
        assertThat(chatClient.systemMessagesSeen()).hasSize(2).allSatisfy(message ->
                assertThat(message).contains(objective).doesNotContain("Canonical mission input:"));
        assertThat(chatClient.systemMessagesSeen().getFirst()).contains("Do not call the parent mission skill");
        assertThat(chatClient.systemMessagesSeen().getLast()).contains("Do NOT call any tool");
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
        assertThat(chatClient.systemMessagesSeen().get(0) + chatClient.userMessagesSeen().get(0))
                .contains("ASSIGNED TASK")
                .contains("Exact capability/tool: expenseLookup")
                .doesNotContain("Skill: duplicateInvoiceChecker");
        assertThat(chatClient.userMessagesSeen()).isNotEmpty();
        assertThat(chatClient.userMessagesSeen().get(0)).doesNotContain("duplicateInvoiceChecker");
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1))
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
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1)).contains("Must start with APPROVED:");
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void timeoutCutoffSuppressesLateWorkerWritesAfterCallerReturns(boolean forwarding) throws Exception
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan original = groupedPlan("plan-timeout-cutoff", true);
        ExecutionPlan plan = forwarding ? new ExecutionPlan(original.planId(), original.capabilityName(), original.createdAt(), original.status(),
                original.tasks().stream().map(task -> new PlanTask(task.taskId(), task.title(), task.status(),
                        task.taskId().equals("t-1") ? "invoiceParser" : "expenseLookup", task.intent(), task.dependsOn(), task.expectedOutputs(),
                        task.parallelGroup(), task.note())).toList()) : original;
        PlanningService planningService = new InitializingPlanningService(stateService, plan);
        CountDownLatch lateWorkerStarted = new CountDownLatch(1);
        CountDownLatch releaseLateWorker = new CountDownLatch(1);
        CountDownLatch lateWorkerReturned = new CountDownLatch(1);
        AtomicReference<MissionContext> retainedMission = new AtomicReference<>();
        AtomicInteger externalSideEffects = new AtomicInteger();
        TaskAddressedModel model = new TaskAddressedModel(Map.of(
                "t-1", "invoiceParser",
                "t-2", forwarding ? "expenseLookup" : "invoiceParser"), new AtomicReference<>());
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
                    retainedMission.set(ExecutionBindingScope.requireCurrent().requireMission());
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
        YamlSkillDefinition definition = forwarding ? forwardingDefinition(3, true) : definitionWithConcurrency(true);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "step-loop-timeout-cutoff", forwarding ? "rootVisibleSkill" : "test.entry", 3);

        try (ExecutorService missionExecutor = Executors.newVirtualThreadPerTaskExecutor())
        {
            StepLoopMissionExecutionEngine engine = engine(
                    stateService, planningService, missionExecutor, definition, Duration.ofMillis(100));

            assertThatThrownBy(() -> executeMission(engine, session, definition, model, forwarding ? List.of(capability, new BoundCapability(tool("expenseLookup", "unused").metadata(), java.util.List.of(),
                            (arguments, taskId, sourceResults) -> capability.invoke(arguments, taskId))) : List.of(capability)))
                    .isInstanceOf(LoomspanMissionTimeoutException.class);
            assertThat(lateWorkerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(retainedMission.get().completedTaskResults()).containsExactly(
                    new MissionContext.CompletedTaskResult("t-1", "invoiceParser", "early-t-1"));
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
            assertThat(finalized).noneMatch(record -> record.recordType() == TraceRecordType.RESULT_FORWARDED);
            assertThat(finalized.stream().filter(record -> record.recordType() == TraceRecordType.TRACE_COMPLETED))
                    .hasSize(1);

            releaseLateWorker.countDown();
            assertThat(lateWorkerReturned.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(externalSideEffects).hasValue(1);
            assertThat(readRecords(session)).containsExactlyElementsOf(finalized);
        }
        // Executor close waits for physical branch return, beyond the capability's release signal.
        assertThat(retainedMission.get().completedTaskResults()).containsExactly(
                new MissionContext.CompletedTaskResult("t-1", "invoiceParser", "early-t-1"));
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        BoundCapability capability = new BoundCapability(directTool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(model.systemMessagesSeen()).isEmpty();
    }

    @Test
    void taskReturningAfterTimeoutDoesNotPreflightAnotherUnitOrRecordAnotherFailure()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        PlanningService planningService = new InitializingPlanningService(stateService, twoTaskPlan());
        SequenceChatClient model = new SequenceChatClient(
                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"t-1\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}");
        AtomicBoolean interrupted = new AtomicBoolean();
        BoundCapability capability = new BoundCapability(directTool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(model.systemMessagesSeen()).isEmpty();
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
                    List.of(directTool("invoiceParser", "unused"))))
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
                            List.of(directTool("invoiceParser", "completed-before-rejection")))))
                    .isSameAs(rejection);
            assertThat(missionExecutor.submissions()).isEqualTo(3);
        }

        assertThat(session.getExecutionPlanSnapshot().status()).isEqualTo(PlanStatus.STALE);
        assertThat(session.getExecutionPlanSnapshot().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.COMPLETED, PlanTaskStatus.FAILED, PlanTaskStatus.PENDING);
        assertThat(binding.requireMission().completedTaskResults()).extracting(MissionContext.CompletedTaskResult::result).contains("completed-before-rejection");
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
        BoundCapability capability = new BoundCapability(directTool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void groupMembersUseOnlyPreUnitEvidenceInEitherConcurrencyMode(boolean concurrent)
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        ExecutionPlan grouped = groupedPlan("snapshot-plan", true);
        List<PlanTask> tasks = new ArrayList<>();
        tasks.add(new PlanTask("prep", "Prepare", PlanTaskStatus.PENDING, "invoiceParser", "prepare",
                List.of(), List.of(), null, null));
        tasks.addAll(grouped.tasks());
        ExecutionPlan plan = new ExecutionPlan("snapshot-plan", "rootVisibleSkill", Instant.EPOCH, PlanStatus.VALID, tasks);
        var finalPrompt = new AtomicReference<String>();
        var delegate = new TaskAddressedModel(Map.of("prep", "invoiceParser", "t-1", "invoiceParser",
                "t-2", "invoiceParser", "t-3", "invoiceParser"), finalPrompt);
        Map<String, List<String>> prompts = new java.util.concurrent.ConcurrentHashMap<>();
        AtomicInteger secondAttempts = new AtomicInteger();
        ai.loomspan.internal.model.ModelInteraction model = request -> {
            String prompt = request.systemPrompt();
            for (String id : List.of("prep", "t-1", "t-2", "t-3")) {
                if (prompt.contains("--- ASSIGNED TASK ---\nID: " + id)) {
                    prompts.computeIfAbsent(id, ignored -> java.util.Collections.synchronizedList(new ArrayList<>())).add(prompt);
                    if (id.equals("t-2") && secondAttempts.getAndIncrement() == 0)
                        return new ai.loomspan.internal.model.ModelInteractionResult(
                                "{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"wrong\",\"toolName\":\"invoiceParser\",\"toolArguments\":{}}",
                                Map.of(ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, request.traceContext().nextAttempt()));
                }
            }
            return delegate.call(request);
        };
        String padding = "x".repeat(1800);
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> padding + "COMPLETE_" + taskId);
        var definition = definitionWithConcurrency(concurrent);
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("snapshot-" + concurrent, "test.entry", 3);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(executeMission(engine(stateService, new InitializingPlanningService(stateService, plan), executor, definition),
                    session, definition, model, List.of(capability))).isEqualTo("done");
        }
        for (String id : List.of("t-1", "t-2"))
            assertThat(prompts.get(id)).allSatisfy(prompt -> assertThat(prompt).contains(padding + "COMPLETE_prep")
                    .doesNotContain("COMPLETE_t-1", "COMPLETE_t-2"));
        assertThat(prompts.get("t-2")).hasSize(2);
        assertThat(prompts.get("t-3").getFirst()).contains(padding + "COMPLETE_t-1", padding + "COMPLETE_t-2");
        assertThat(finalPrompt.get()).contains(padding + "COMPLETE_prep", padding + "COMPLETE_t-1", padding + "COMPLETE_t-2");
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
                        assertThat(mission.completedTaskResults()).isEmpty();
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
                .contains("COMPLETED TASK EVIDENCE", "\"taskId\":\"t-1\"", "\"taskId\":\"t-2\"");
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(binding.requireMission().completedTaskResults()).extracting(MissionContext.CompletedTaskResult::result).contains("sibling-result");
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(binding.requireMission().completedTaskResults()).extracting(MissionContext.CompletedTaskResult::result).contains("sibling-result");
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(binding.requireMission().completedTaskResults()).extracting(MissionContext.CompletedTaskResult::result).contains("sibling-result");
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        BoundCapability invoiceParser = new BoundCapability(tool("invoiceParser", "parsed").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
                    invocationOrder.add(taskId);
                    return "parsed";
                });
        BoundCapability expenseLookup = new BoundCapability(tool("expenseLookup", "matches").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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
        assertThat(chatClient.systemMessagesSeen().get(1) + chatClient.userMessagesSeen().get(1))
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
        BoundCapability capability = new BoundCapability(template.metadata(), java.util.List.of(), (arguments, taskId, sourceResults) -> {
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
        BoundCapability capability = new BoundCapability(tool("invoiceParser", "unused").metadata(), java.util.List.of(),
                (arguments, taskId, sourceResults) -> {
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

    private static BoundCapability directTool(String name, String result) {
        return toolWithSchema(name, "{\"type\":\"object\",\"additionalProperties\":false}", result);
    }

    private static BoundCapability tool(String name, String result) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.capability(name).metadata(), java.util.List.of(),
                (arguments, linkedTaskId, sourceResults) -> result);
    }

    private static BoundCapability toolWithSchema(String name, String inputSchema, String result) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.capability(name, inputSchema).metadata(), java.util.List.of(),
                (arguments, linkedTaskId, sourceResults) -> result);
    }

    private static BoundCapability toolWithContract(String name, String inputSchema, String contractSchema, String result) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.contractAware(name, inputSchema, contractSchema).metadata(), java.util.List.of(),
                (arguments, linkedTaskId, sourceResults) -> result);
    }

    private static BoundCapability failingTool(String name) {
        return new BoundCapability(
                ai.loomspan.testkit.TestBoundCapabilities.capability(name).metadata(), java.util.List.of(),
                (arguments, linkedTaskId, sourceResults) -> { throw new IllegalStateException("parser exploded"); });
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
        private RuntimeException failureAfterResponses;
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
                if (failureAfterResponses != null) throw failureAfterResponses;
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
