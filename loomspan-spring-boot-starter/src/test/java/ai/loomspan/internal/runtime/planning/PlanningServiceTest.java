package ai.loomspan.internal.runtime.planning;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.JournalEntry;
import ai.loomspan.internal.core.JournalEntryType;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.SimpleChatClient;
import ai.loomspan.internal.model.ModelInteractionResult;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.evidence.EvidenceCoverageValidator;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.AllowedSkillConstraint;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration EXECUTION_CONFIGURATION =
            new EffectiveSkillExecutionConfiguration("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    private static final String YAML_PLAN_WITH_LLM_STATUSES = """
            ---
            planId: 12345
            capabilityName: invoiceParser
            createdAt: 2023-03-15T14:30:00.000Z
            status: EXECUTED
            tasks:
              - taskId: "67890"
                title: Parse Invoice
                status: SUCCESS
                capabilityName: invoiceParser
                intent: Parse the invoice data
                dependsOn: []
                expectedOutputs: []
                parallelGroup: null
                note: Parsed successfully
            """;

    @Test
    void initializesPlanOnlyWhenInvoked() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 3);
        ExecutionPlan plan = plan("plan-1", PlanTaskStatus.PENDING);

        assertThat(planFor(session)).isEmpty();
        ExecutionPlan accepted = initializePlan(planningService,
                session, "hello", null, rootDefinition(), new SimpleChatClient(plan, "done"), defaultVisibleTools())
                .orElseThrow();
        assertThat(accepted.planId()).isNotEqualTo(plan.planId());
        assertThat(planFor(session)).contains(accepted);
    }

    @Test
    void acceptsPlanningResponseWithoutPlanIdAndGeneratesFrameworkIdentity() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-framework-plan-id", "test.entry", 3);
        String response = planJson("model-plan-id", PlanTaskStatus.PENDING)
                .replaceFirst("\\s*\"planId\"\\s*:\\s*\"[^\"]+\"\\s*,", "");

        ExecutionPlan accepted = initializePlan(planningService,
                session,
                "hello",
                null,
                rootDefinition(),
                request -> new ModelInteractionResult(response, Map.of(
                        ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY,
                        request.traceContext().nextAttempt())),
                defaultVisibleTools())
                .orElseThrow();

        TraceRecord created = readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .findFirst()
                .orElseThrow();
        assertThat(accepted.planId()).isNotBlank();
        assertThat(planFor(session)).contains(accepted);
        assertThat(created.metadata().get("planId")).isEqualTo(accepted.planId());
    }

    @Test
    void planCreatedLinksToTheAcceptingAttemptAndRetrySequence() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-plan-lineage", "test.entry", 3);
        Map<String, Object> attempt = Map.of(
                "retrySequenceId", "retry-sequence-accepted",
                "attemptId", "attempt-accepted",
                "attemptNumber", 2,
                "attemptReason", "SEMANTIC_RETRY",
                "providerAttemptNumber", 1);

        initializePlan(planningService,
                session,
                "hello",
                null,
                rootDefinition(),
                request -> new ModelInteractionResult(planJson("legacy-model-plan-id", PlanTaskStatus.PENDING), Map.of(
                        ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, attempt)),
                defaultVisibleTools());

        TraceRecord created = readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .findFirst()
                .orElseThrow();
        assertThat(created.metadata())
                .containsEntry("attemptId", "attempt-accepted")
                .containsEntry("retrySequenceId", "retry-sequence-accepted")
                .containsEntry("planId", created.data().get("planId").asText());
    }

    @Test
    void planningPromptDoesNotAskTheModelForPlanId() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        SimpleChatClient chatClient = new SimpleChatClient(plan("untrusted-plan", PlanTaskStatus.PENDING), "done");

        initializePlan(planningService,
                ai.loomspan.internal.core.TestLoomspanSessions.withId("session-prompt-identity", "test.entry", 3),
                "hello", null, rootDefinition(), chatClient, defaultVisibleTools());

        assertThat(chatClient.getSystemMessagesSeen().getFirst())
                .contains("\"taskId\": \"<unique string>\"")
                .doesNotContain("\"planId\"");
    }

    @Test
    void acceptsJsonAndYamlPlansWithoutPlanId() {
        List<String> payloads = List.of(
                planJson("remove-me", PlanTaskStatus.PENDING)
                        .replaceFirst("\\s*\"planId\"\\s*:\\s*\"[^\"]+\"\\s*,", ""),
                YAML_PLAN_WITH_LLM_STATUSES.replaceFirst("(?m)^planId:.*\\R", ""));

        for (int index = 0; index < payloads.size(); index++) {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            String expectedId = "framework-plan-" + index;
            DefaultPlanningService planningService = planningService(stateService, () -> expectedId);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-no-model-id-" + index, "test.entry", 3);

            ExecutionPlan accepted = initializePlan(planningService,
                    session,
                    "hello",
                    null,
                    rootDefinition(index == 0 ? "rootVisibleSkill" : "invoiceParser"),
                    new SimpleChatClient(null, payloads.get(index)),
                    List.of(toolCallback(index == 0 ? "allowedVisibleSkill" : "invoiceParser", "test")))
                    .orElseThrow();

            assertThat(accepted.planId()).isEqualTo(expectedId);
        }
    }

    @Test
    void overwritesUnsolicitedJsonAndYamlPlanId() {
        List<String> payloads = List.of(
                planJson("adversarial", PlanTaskStatus.PENDING),
                planJson("adversarial", PlanTaskStatus.PENDING).replace("\"adversarial\"", "12345"),
                planJson("adversarial", PlanTaskStatus.PENDING).replace("\"adversarial\"", "\"   \""),
                YAML_PLAN_WITH_LLM_STATUSES,
                YAML_PLAN_WITH_LLM_STATUSES.replace("planId: 12345", "planId: adversarial"),
                YAML_PLAN_WITH_LLM_STATUSES.replace("planId: 12345", "planId: '   '"));

        for (int index = 0; index < payloads.size(); index++) {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            String expectedId = "trusted-plan-" + index;
            DefaultPlanningService planningService = planningService(stateService, () -> expectedId);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-unsolicited-id-" + index, "test.entry", 3);
            ExecutionPlan accepted = initializePlan(planningService, session,
                    "hello",
                    null,
                    rootDefinition(index < 3 ? "rootVisibleSkill" : "invoiceParser"),
                    new SimpleChatClient(null, payloads.get(index)),
                    List.of(toolCallback(index < 3 ? "allowedVisibleSkill" : "invoiceParser", "test")))
                    .orElseThrow();

            assertThat(accepted.planId()).isEqualTo(expectedId);
        }
    }

    @Test
    void rejectsNullOrBlankFrameworkPlanId() {
        List<Supplier<String>> invalidSuppliers = List.of(() -> null, () -> "", () -> "   ");

        for (int index = 0; index < invalidSuppliers.size(); index++) {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            Supplier<String> invalidSupplier = invalidSuppliers.get(index);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-invalid-framework-id-" + index, "test.entry", 3);

            DefaultPlanningService planningService = planningService(stateService, invalidSupplier);
            assertThatThrownBy(() -> initializePlan(planningService, session,
                    "hello",
                    null,
                    rootDefinition(),
                    new SimpleChatClient(plan("untrusted", PlanTaskStatus.PENDING), "done"),
                    defaultVisibleTools()))
                    .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
            assertThat(planFor(session)).isEmpty();
            assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
        }
    }

    @Test
    void identicalAcceptedResponsesReceiveDistinctFrameworkPlanIds() {
        Deque<String> ids = new ArrayDeque<>(List.of("framework-plan-1", "framework-plan-2"));
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = planningService(stateService, ids::removeFirst);
        String response = planJson("same-untrusted-id", PlanTaskStatus.PENDING);

        ExecutionPlan first = initializePlan(planningService,
                ai.loomspan.internal.core.TestLoomspanSessions.withId("session-distinct-1", "test.entry", 3),
                "hello", null, rootDefinition(), new SimpleChatClient(null, response), defaultVisibleTools()).orElseThrow();
        ExecutionPlan second = initializePlan(planningService,
                ai.loomspan.internal.core.TestLoomspanSessions.withId("session-distinct-2", "test.entry", 3),
                "hello", null, rootDefinition(), new SimpleChatClient(null, response), defaultVisibleTools()).orElseThrow();

        assertThat(first.planId()).isEqualTo("framework-plan-1");
        assertThat(second.planId()).isEqualTo("framework-plan-2");
    }

    @Test
    void missingAcceptedAttemptContextFailsBeforePlanStorage() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-missing-attempt", "test.entry", 3);

        DefaultPlanningService planningService = planningService(stateService, () -> "candidate-plan");
        assertThatThrownBy(() -> initializePlan(planningService, session,
                "hello",
                null,
                rootDefinition(),
                request -> ModelInteractionResult.content(planJson("untrusted", PlanTaskStatus.PENDING)),
                defaultVisibleTools()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model attempt context");
        assertThat(planFor(session)).isEmpty();
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
    }

    @Test
    void invalidAcceptedAttemptContextFailsBeforePlanStorage() {
        List<Map<String, Object>> invalidContexts = List.of(
                Map.of(ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, "not-a-map"),
                Map.of(ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, Map.of(
                        "retrySequenceId", "retry-invalid",
                        "attemptNumber", 1,
                        "attemptReason", "INITIAL",
                        "providerAttemptNumber", 1)),
                Map.of(ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, Map.of(
                        "attemptId", "attempt-invalid",
                        "attemptNumber", 1,
                        "attemptReason", "INITIAL",
                        "providerAttemptNumber", 1)),
                Map.of(ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, Map.of(
                        "retrySequenceId", "retry-invalid",
                        "attemptId", " ",
                        "attemptNumber", 1,
                        "attemptReason", "INITIAL",
                        "providerAttemptNumber", 1)));

        for (int index = 0; index < invalidContexts.size(); index++) {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-invalid-attempt-" + index, "test.entry", 3);
            Map<String, Object> invalidContext = invalidContexts.get(index);

            DefaultPlanningService planningService = planningService(stateService, () -> "candidate-plan");
            assertThatThrownBy(() -> initializePlan(planningService, session,
                    "hello",
                    null,
                    rootDefinition(),
                    request -> new ModelInteractionResult(planJson("untrusted", PlanTaskStatus.PENDING), invalidContext),
                    defaultVisibleTools()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(planFor(session)).isEmpty();
            assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
        }
    }

    @Test
    void planningReceivesAttachmentDescriptorsButNoMedia() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-planning-attachment", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(planJson("plan-attachment", PlanTaskStatus.PENDING));

        initializePlan(planningService,
                session,
                "Extract ticket",
                Map.of("image", Map.of(
                        "attachment", true,
                        "name", "ticket.jpg",
                        "contentType", "image/jpeg",
                        "mediaType", "IMAGE")),
                rootDefinition(),
                chatClient,
                defaultVisibleTools());

        assertThat(chatClient.userMessagesSeen()).hasSize(1);
        assertThat(chatClient.userMessagesSeen().getFirst()).contains("\"attachment\" : true", "\"contentType\" : \"image/jpeg\"");
        assertThat(chatClient.userConsumerCalls()).isZero();
    }

    @Test
    void doesNotPerformDuplicateOuterPlanningUsageAccounting() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        RecordingSessionUsageService usageService = new RecordingSessionUsageService();
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-usage", "test.entry", 3);
        ExecutionPlan plan = plan("plan-usage", PlanTaskStatus.PENDING);

        assertThat(initializePlan(planningService, session, "hello", null, rootDefinition(), new SimpleChatClient(plan, "done"), defaultVisibleTools()))
                .isPresent();
        assertThat(usageService.lastSkillName).isNull();
        assertThat(usageService.snapshot(session).modelCalls()).isZero();
    }

    @Test
    void initializesPlanFromYamlWithNormalizedStatuses() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-yaml", "test.entry", 3);

        ExecutionPlan plan = initializePlan(planningService,
                        session,
                        "parse invoice",
                        null,
                        rootDefinition("invoiceParser"),
                        new SimpleChatClient(null, YAML_PLAN_WITH_LLM_STATUSES),
                        List.of(toolCallback("invoiceParser", "test")))
                .orElseThrow();

        assertThat(plan.planId()).isNotBlank().isNotEqualTo("12345");
        assertThat(plan.status()).isEqualTo(PlanStatus.VALID);
        assertThat(plan.findTask("67890")).isPresent();
        assertThat(plan.findTask("67890").orElseThrow().status()).isEqualTo(PlanTaskStatus.COMPLETED);
    }

    @Test
    void normalizesFailureLikeTaskStatusesToFailedAndRejectsLegacyBlockedTaskStatus() {
        for (String taskStatus : List.of("FAILED", "FAILURE", "ERROR", "INVALID", "STALE")) {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            DefaultPlanningService planningService = new DefaultPlanningService(stateService);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-task-status-" + taskStatus, "test.entry", 3);
            String payload = planJson("model-plan-id", PlanTaskStatus.PENDING)
                    .replace("\"status\": \"PENDING\"", "\"status\": \"" + taskStatus + "\"");

            ExecutionPlan accepted = initializePlan(planningService,
                    session, "hello", null, rootDefinition(), new SimpleChatClient(null, payload), defaultVisibleTools())
                    .orElseThrow();

            assertThat(accepted.tasks()).extracting(PlanTask::status)
                    .containsExactly(PlanTaskStatus.FAILED);
        }

        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-task-status-blocked", "test.entry", 3);
        String legacyPayload = planJson("model-plan-id", PlanTaskStatus.PENDING)
                .replace("\"status\": \"PENDING\"", "\"status\": \"BLOCKED\"");

        assertThatThrownBy(() -> initializePlan(planningService,
                session, "hello", null, rootDefinition(), new SimpleChatClient(null, legacyPayload), defaultVisibleTools()))
                .hasMessageContaining("Failed to parse planning response");
        assertThat(planFor(session)).isEmpty();

        DefaultExecutionStateService planStatusStateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planStatusPlanningService = new DefaultPlanningService(planStatusStateService);
        LoomspanSession planStatusSession = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-plan-status-blocked", "test.entry", 3);
        String planStatusPayload = planJson("model-plan-id", PlanTaskStatus.PENDING)
                .replace("\"status\": \"VALID\"", "\"status\": \"BLOCKED\"");

        assertThat(initializePlan(planStatusPlanningService,
                planStatusSession, "hello", null, rootDefinition(),
                new SimpleChatClient(null, planStatusPayload), defaultVisibleTools()).orElseThrow().status())
                .isEqualTo(PlanStatus.INVALID);
    }

    @Test
    void planningCodecRoleRejectsUnknownFieldsInJsonAndYaml()
    {
        for (String payload : List.of(
                planJson("plan-json-unknown", PlanTaskStatus.PENDING)
                        .replaceFirst("\\{", "{\"future\":true,"),
                YAML_PLAN_WITH_LLM_STATUSES + "future: true\n"))
        {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            DefaultPlanningService planningService = new DefaultPlanningService(stateService);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-plan-unknown-" + payload.charAt(0), "test.entry", 3);

            assertThatThrownBy(() -> initializePlan(planningService,
                    session,
                    "parse invoice",
                    null,
                    rootDefinition(payload.startsWith("{") ? "rootVisibleSkill" : "invoiceParser"),
                    new SimpleChatClient(null, payload),
                    List.of(toolCallback(payload.startsWith("{") ? "allowedVisibleSkill" : "invoiceParser", "test"))))
                    .hasMessageContaining("Failed to parse planning response");
        }
    }

    @Test
    void rejectsStaleAutoCompletableFieldAsUnknown() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-legacy-plan", "test.entry", 3);

        String stalePlan = planJson("plan-stale", PlanTaskStatus.PENDING)
                .replace("\"parallelGroup\": null", "\"auto" + "Completable\": false");

        assertThatThrownBy(() -> initializePlan(planningService,
                session,
                "hello",
                null,
                rootDefinition(),
                new SimpleChatClient(null, stalePlan),
                defaultVisibleTools()))
                .hasMessageContaining("Failed to parse planning response");
        assertThat(planFor(session)).isEmpty();
    }

    @Test
    void recordsPlanningTraceWithRealProviderMetadata() throws Exception {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-trace", "test.entry", 3);

        initializePlan(planningService,
                session,
                "hello",
                null,
                rootDefinition(),
                new SimpleChatClient(plan("plan-trace", PlanTaskStatus.PENDING), "done"),
                defaultVisibleTools());

        List<TraceRecord> records = readRecords(session);

        TraceRecord planningFrame = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.FRAME_OPENED
                        && record.frameType() == TraceFrameType.PLANNING)
                .findFirst()
                .orElseThrow();
        TraceRecord modelFrame = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.FRAME_OPENED
                        && record.frameType() == TraceFrameType.MODEL_CALL
                        && "rootVisibleSkill#planning-model".equals(record.route()))
                .findFirst()
                .orElseThrow();

        assertThat(modelFrame.parentFrameId()).isEqualTo(planningFrame.frameId());
        TraceRecord created = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .findFirst()
                .orElseThrow();
        assertThat(created.metadata()).containsKeys("planId", "attemptId", "retrySequenceId");
        assertThat(records).noneMatch(record ->
                record.recordType() == TraceRecordType.MODEL_REQUEST_SENT);
    }

    @Test
    void marksLinkedTaskStartedCompletedAndFailed() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 3);
        CapabilityMetadata capability = capability("allowedVisibleSkill");

        storePlan(stateService, session, new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(
                        new PlanTask("task-1", "Use tool", PlanTaskStatus.PENDING, "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null),
                        new PlanTask("task-2", "Summarize", PlanTaskStatus.PENDING, null))));

        String startedTaskId = markToolStarted(planningService, session, capability).orElseThrow();
        assertThat(startedTaskId).isEqualTo("task-1");
        ExecutionPlan started = currentPlan(stateService, session).orElseThrow();
        assertThat(started.findTask("task-1").orElseThrow().status()).isEqualTo(PlanTaskStatus.IN_PROGRESS);
        assertThat(session.getJournalSnapshot()).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.PLAN_UPDATED);

        ExecutionPlan completed = markToolCompleted(planningService, session, "task-1", capability.name()).orElseThrow();
        assertThat(completed.findTask("task-1").orElseThrow().status()).isEqualTo(PlanTaskStatus.COMPLETED);

        storePlan(stateService, session, new ExecutionPlan(
                "plan-2",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:01:00Z"),
                List.of(new PlanTask("task-3", "Use tool", PlanTaskStatus.PENDING, "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null))));
        String failedTaskId = markToolStarted(planningService, session, capability).orElseThrow();
        assertThat(markToolFailed(planningService, session, failedTaskId, capability.name(), new IllegalStateException("boom")))
                .isPresent()
                .get()
                .extracting(ExecutionPlan::status)
                .isEqualTo(PlanStatus.STALE);
        assertThat(planFor(session).orElseThrow().findTask("task-3").orElseThrow().status()).isEqualTo(PlanTaskStatus.FAILED);
        assertThat(session.getJournalSnapshot()).extracting(JournalEntry::type)
                .containsExactly(
                        JournalEntryType.PLAN_UPDATED,
                        JournalEntryType.PLAN_UPDATED,
                        JournalEntryType.PLAN_UPDATED,
                        JournalEntryType.PLAN_UPDATED);
        List<TraceRecord> updates = readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .toList();
        assertThat(updates).hasSize(4);
        assertPlanningTransition(updates.get(0), "ADMISSION", "task-1", false, null);
        assertPlanningTransition(updates.get(1), "JOIN", "task-1", null, "COMPLETED");
        assertPlanningTransition(updates.get(2), "ADMISSION", "task-3", false, null);
        assertPlanningTransition(updates.get(3), "JOIN", "task-3", null, "FAILED");
    }

    @Test
    void rejectsCompletingTaskWithMismatchedCapabilityBinding() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-mismatched-complete", "test.entry", 3);

        storePlan(stateService, session, new ExecutionPlan(
                "plan-explicit",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(new PlanTask("task-1", "Use tool once", PlanTaskStatus.IN_PROGRESS,
                        "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null))));

        assertThatThrownBy(() -> markToolCompleted(planningService, session, "task-1", "different.visible.skill"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task-1")
                .hasMessageContaining("allowedVisibleSkill")
                .hasMessageContaining("different.visible.skill");
    }

    @Test
    void rejectsFailingTaskWithMismatchedCapabilityBinding() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-mismatched-fail", "test.entry", 3);

        storePlan(stateService, session, new ExecutionPlan(
                "plan-explicit",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(new PlanTask("task-1", "Use tool once", PlanTaskStatus.IN_PROGRESS,
                        "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null))));

        assertThatThrownBy(() -> markToolFailed(planningService,
                session,
                "task-1",
                "different.visible.skill",
                new IllegalStateException("boom")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task-1")
                .hasMessageContaining("allowedVisibleSkill")
                .hasMessageContaining("different.visible.skill");
    }

    @Test
    void doesNotLogPlanUpdateWhenCompletedTaskIsMissing() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-missing-task", "test.entry", 3);

        storePlan(stateService, session, new ExecutionPlan(
                "plan-1",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(new PlanTask("task-1", "Use tool", PlanTaskStatus.PENDING, "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null))));

        assertThat(markToolCompleted(planningService, session, "missing-task", "allowedVisibleSkill")).isEmpty();
        assertThat(session.getJournalSnapshot()).isEmpty();
    }

    @Test
    void planningPromptIncludesToolDescriptionsAndAlignmentRules() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-tool-names", "test.entry", 3);
        SimpleChatClient chatClient = new SimpleChatClient(plan("plan-tools", PlanTaskStatus.PENDING), "done");

        BoundCapability tool1 = toolCallback("invoiceParser", "Extract invoice fields from source documents");
        BoundCapability tool2 = toolCallback("expenseLookup", "Look up prior expenses for a parsed invoice");

        initializePlan(planningService, session, "check invoice", null, rootDefinition("duplicateInvoiceChecker"), chatClient,
                List.of(tool1, tool2, defaultVisibleTools().getFirst()));

        String systemPrompt = chatClient.getSystemMessagesSeen().getFirst();
        assertThat(systemPrompt).contains("invoiceParser: Extract invoice fields from source documents");
        assertThat(systemPrompt).contains("expenseLookup: Look up prior expenses for a parsed invoice");
        assertThat(systemPrompt).contains("Available sub-skills");
        assertThat(systemPrompt).contains("Bind each task to the tool that best matches that task's intent.");
        assertThat(systemPrompt).contains("Gather enough evidence to support the final answer before the mission is complete.");
        assertThat(systemPrompt).contains("\"capabilityName\": \"duplicateInvoiceChecker\"");
        assertThat(systemPrompt)
                .contains("\"parallelGroup\": null")
                .contains("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
                .contains("List order defines execution-unit order")
                .contains("joins the complete unit before starting the next unit")
                .contains("positive assertion")
                .contains("Leave tasks ungrouped whenever")
                .contains("may reference only tasks in earlier execution units")
                .doesNotContain("auto" + "Completable");
    }

    @Test
    void planningPromptIncludesSkillPromptBeforePlanningContract() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-planning-prompt", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(planJson("plan-prompt", PlanTaskStatus.PENDING));

        BoundCapability tool = toolCallback("invoiceParser", "Short child tool description");
        initializePlan(planningService,
                session,
                "check invoice",
                null,
                rootDefinitionWithPrompt("PARENT_PROMPT_SENTINEL\nAlways verify totals before final response."),
                chatClient,
                List.of(tool, defaultVisibleTools().getFirst()));

        String systemPrompt = chatClient.systemMessagesSeen().getFirst();
        assertThat(systemPrompt).startsWith("PARENT_PROMPT_SENTINEL");
        assertThat(systemPrompt.indexOf("PARENT_PROMPT_SENTINEL"))
                .isLessThan(systemPrompt.indexOf("Create an ordered flight plan"));
        assertThat(systemPrompt).contains("invoiceParser: Short child tool description");
        assertThat(systemPrompt).doesNotContain("invoiceParser: PARENT_PROMPT_SENTINEL");

    }

    @Test
    void planningPromptIncludesEvidenceConstraints() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-evidence-constraints", "test.entry", 3);
        SimpleChatClient chatClient = new SimpleChatClient(plan("plan-tools", PlanTaskStatus.PENDING), "done");

        BoundCapability tool1 = toolCallback("invoiceParser", "Extract invoice fields from source documents");
        BoundCapability tool2 = toolCallback("expenseLookup", "Look up prior expenses for a parsed invoice");

        assertThatThrownBy(() -> initializePlan(planningService, session, "check invoice", null, duplicateInvoiceDefinition(), chatClient, List.of(tool1, tool2)))
                .isInstanceOf(IllegalStateException.class);

        String systemPrompt = chatClient.getSystemMessagesSeen().getFirst();
        assertThat(systemPrompt).contains("Evidence Constraints:");
        assertThat(systemPrompt).contains("The 'isDuplicate' output field requires tasks whose exact capability names satisfy: invoiceParser and expenseLookup");
        assertThat(systemPrompt).contains("The 'vendorName' output field requires tasks whose exact capability names satisfy: invoiceParser");
        assertThat(systemPrompt).doesNotContain("[expenseLookup, invoiceParser] tool(s)");
    }

    @Test
    void planningPromptPreservesAuthoredDescriptionsVerbatim() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-authored-descriptions", "test.entry", 3);
        SimpleChatClient chatClient = new SimpleChatClient(plan("plan-tools-verbatim", PlanTaskStatus.PENDING), "done");

        String authoredDescription = "Reads invoice PDFs exactly as-authored. Keep JSON keys `invoice_id`, `vendor_name`, and \"line_items\".";
        BoundCapability tool = toolCallback("invoiceParser", authoredDescription);

        initializePlan(planningService,
                session,
                "check invoice",
                null,
                rootDefinition("duplicateInvoiceChecker"),
                chatClient,
                List.of(tool, defaultVisibleTools().getFirst()));

        String systemPrompt = chatClient.getSystemMessagesSeen().getFirst();
        assertThat(systemPrompt).contains("invoiceParser: " + authoredDescription);
    }

    @Test
    void leavesPlanningRetryUsageToThePhysicalAttemptAdvisor() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        RecordingSessionUsageService usageService = new RecordingSessionUsageService();
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-weak-plan-retry-usage", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(
                weakSingleToolPlanJson(),
                correctedMultiToolPlanJson());

        BoundCapability invoiceParser = toolCallback("invoiceParser", "Extract invoice fields from source documents");
        BoundCapability expenseLookup = toolCallback("expenseLookup", "Look up related expenses for comparison");

        initializePlan(planningService,
                        session,
                        "check invoice duplicates",
                        null,
                        duplicateInvoiceDefinition(),
                        chatClient,
                        List.of(invoiceParser, expenseLookup))
                .orElseThrow();

        assertThat(usageService.lastSkillName).isNull();
        assertThat(usageService.snapshot(session).modelCalls()).isZero();
    }

    @Test
    void rendersTaskConstraintsAndAcceptsOneCorrectedRetry() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("constraint-retry", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(
                weakSingleToolPlanJson(), correctedMultiToolPlanJson());

        ExecutionPlan accepted = initializePlan(planningService,
                session,
                "check invoice duplicates",
                null,
                constrainedDefinition(new AllowedSkillConstraint("expenseLookup", 1, 2, false)),
                chatClient,
                List.of(toolCallback("invoiceParser", "parse"), toolCallback("expenseLookup", "lookup")))
                .orElseThrow();

        assertThat(accepted.tasks()).extracting(PlanTask::capabilityName).contains("expenseLookup");
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().getFirst())
                .contains("Plan Task Count Constraints", "expenseLookup: use in at least 1 plan task(s) and use in at most 2 plan task(s)")
                .doesNotContain("Planning quality rules");
        assertThat(chatClient.systemMessagesSeen().getLast())
                .contains("expenseLookup", "uses it 0 time(s)");
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_RETRY_REQUESTED)
                .hasSize(1);
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.metadata()).containsEntry("issueCodes", List.of("PLAN_TASK_MINIMUM_NOT_MET"));
                    assertThat(record.data().toString()).contains(
                            "expenseLookup", "\"configuredMinTasks\":1", "\"effectiveMinTasks\":1",
                            "\"maxTasks\":2", "\"actualTaskCount\":0");
                });
    }

    @Test
    void rejectsSecondConstraintViolationWithoutStoringPlan() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("constraint-exhausted", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(
                weakSingleToolPlanJson(), weakSingleToolPlanJson());

        assertThatThrownBy(() -> initializePlan(planningService,
                session,
                "check invoice duplicates",
                null,
                constrainedDefinition(new AllowedSkillConstraint("expenseLookup", 1, null, false)),
                chatClient,
                List.of(toolCallback("invoiceParser", "parse"), toolCallback("expenseLookup", "lookup"))))
                .hasMessageContaining("Plan validation failed")
                .hasMessageContaining("expenseLookup")
                .hasMessageContaining("at least 1");
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(currentPlan(stateService, session)).isEmpty();
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
    }

    @Test
    void positiveMinimumFailsVisibilityPreflightBeforeModelCall() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("constraint-preflight", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(weakSingleToolPlanJson());

        assertThatThrownBy(() -> initializePlan(planningService,
                session,
                "check invoice duplicates",
                null,
                constrainedDefinition(new AllowedSkillConstraint("expenseLookup", null, null, true)),
                chatClient,
                List.of(toolCallback("invoiceParser", "parse"))))
                .hasMessageContaining("expenseLookup")
                .hasMessageContaining("not visible");
        assertThat(chatClient.systemMessagesSeen()).isEmpty();
    }

    @Test
    void invisibleMaximumOnlyChildDoesNotFailPreflight() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("constraint-max-only", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(weakSingleToolPlanJson());

        initializePlan(planningService, session, "check invoice", null,
                constrainedDefinition(new AllowedSkillConstraint("expenseLookup", null, 1, false)),
                chatClient, List.of(toolCallback("invoiceParser", "parse")));

        assertThat(chatClient.systemMessagesSeen()).hasSize(1);
    }

    @Test
    void taskConstraintAndEvidenceFailuresShareOneCorrectiveAttempt() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("combined-validation", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(
                weakSingleToolPlanJson(), correctedMultiToolPlanJson());

        initializePlan(planningService, session, "check invoice", null, constrainedEvidenceDefinition(), chatClient,
                List.of(toolCallback("invoiceParser", "parse"), toolCallback("expenseLookup", "lookup")));

        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().getLast())
                .contains("expenseLookup", "at least 1 plan task", "invoiceParser and expenseLookup");
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_RETRY_REQUESTED)
                .hasSize(2);
    }

    @Test
    void completingTaskHoldsFenceAcrossPlanTraceAndEvidence() throws Exception {
        BlockingCompletedPlanStateService stateService = new BlockingCompletedPlanStateService();
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "session-atomic-completion", "test.entry", 3);
        var binding = ai.loomspan.internal.core.TestExecutionBindings.missionBinding(session);
        BINDINGS.put(session, binding);
        storePlan(stateService, session, new ExecutionPlan(
                "plan-atomic",
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(new PlanTask("task-1", "Use tool", PlanTaskStatus.IN_PROGRESS,
                        "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null))));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Optional<ExecutionPlan>> completion = executor.submit(() ->
                    ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding,
                            () -> planningService.markToolCompleted(
                                    session, "task-1", "allowedVisibleSkill")));
            assertThat(stateService.completedPlanStored.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> cutoff = executor.submit(() -> {
                stateService.cutoffStarted.countDown();
                return binding.requireMission().lifecycle().closeNow();
            });
            assertThat(stateService.cutoffStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> cutoff.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            stateService.releaseStore.countDown();
            assertThat(completion.get(2, TimeUnit.SECONDS)).isPresent();
            cutoff.get(2, TimeUnit.SECONDS);
        }

        assertThat(session.getJournalSnapshot()).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.PLAN_UPDATED);
        assertThat(readRecords(session))
                .filteredOn(record -> record.recordType() == TraceRecordType.PLAN_UPDATED
                        || record.recordType() == TraceRecordType.EVIDENCE_RECORDED)
                .extracting(TraceRecord::recordType)
                .containsExactly(TraceRecordType.PLAN_UPDATED, TraceRecordType.EVIDENCE_RECORDED);
        assertThat(binding.requireMission().successfulDirectSkills())
                .containsExactly("allowedVisibleSkill");
    }

    @Test
    void uniqueInProgressFallbackWinsAndMultipleMatchesAreAmbiguous()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "fallback-in-progress", "test.entry", 3);
        CapabilityMetadata capability = capability("allowedVisibleSkill");
        ExecutionPlan unique = new ExecutionPlan("plan-unique", "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"), List.of(
                        new PlanTask("task-1", "Admitted", PlanTaskStatus.IN_PROGRESS,
                                "allowedVisibleSkill", "work", List.of(), List.of(), null, null),
                        new PlanTask("task-2", "Ready", PlanTaskStatus.PENDING,
                                "allowedVisibleSkill", "work", List.of(), List.of(), null, null)));
        storePlan(stateService, session, unique);

        assertThat(markToolStarted(planningService, session, capability)).contains("task-1");
        assertThat(readRecords(session))
                .filteredOn(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .singleElement()
                .satisfies(record -> assertThat(record.metadata()).doesNotContainKey("transition"));

        ExecutionPlan ambiguous = unique.updateTask("task-2", task -> task.bindInProgress("admitted"));
        storePlan(stateService, session, ambiguous);
        assertThat(markToolStarted(planningService, session, capability)).isEmpty();
        assertThat(currentPlan(stateService, session).orElseThrow().tasks()).extracting(PlanTask::status)
                .containsExactly(PlanTaskStatus.IN_PROGRESS, PlanTaskStatus.IN_PROGRESS);
    }

    @Test
    void correctsStructuralIssuesBeforeTypedValidationAndPreservesTraceLineage()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        AtomicInteger constraintCalls = new AtomicInteger();
        AtomicInteger evidenceCalls = new AtomicInteger();
        PlanTaskConstraintValidator constraints = new PlanTaskConstraintValidator()
        {
            @Override
            PlanTaskConstraintValidationResult validate(ExecutionPlan plan, List<AllowedSkillConstraint> configured)
            {
                constraintCalls.incrementAndGet();
                return super.validate(plan, configured);
            }
        };
        EvidenceCoverageValidator evidence = new EvidenceCoverageValidator()
        {
            @Override
            public ai.loomspan.internal.runtime.evidence.EvidenceCoverageResult validatePlanCoverage(
                    ExecutionPlan plan, ai.loomspan.internal.runtime.evidence.EvidenceContract contract)
            {
                evidenceCalls.incrementAndGet();
                return super.validatePlanCoverage(plan, contract);
            }
        };
        LoomspanJacksonCodecs codecs = LoomspanJacksonCodecs.defaults();
        DefaultPlanningService planningService = new DefaultPlanningService(stateService, codecs.planningJson(), codecs.planningYaml(),
                constraints, evidence, () -> "accepted-structure-plan");
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "structure-corrected", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(
                invalidStructureYaml(), correctedMultiToolPlanJson());

        ExecutionPlan accepted = initializePlan(planningService,
                session, "check invoice", null, duplicateInvoiceDefinition(), chatClient,
                List.of(toolCallback("invoiceParser", "parse"), toolCallback("expenseLookup", "lookup")))
                .orElseThrow();

        assertThat(accepted.tasks().getFirst().parallelGroup()).isNull();
        assertThat(constraintCalls).hasValue(1);
        assertThat(evidenceCalls).hasValue(1);
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(chatClient.systemMessagesSeen().getLast())
                .contains("[parallel-group-type]", "[plan-dependency]");
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.metadata())
                            .containsEntry("retryCount", 0)
                            .containsEntry("validationStatus", "retrying")
                            .containsEntry("severity", "ERROR")
                            .containsEntry("issueCodes", List.of("parallel-group-type", "plan-dependency"))
                            .containsKeys("attemptId", "retrySequenceId");
                    assertThat(record.data().toString()).contains("parallel-group-type", "plan-dependency", "\"severity\":\"ERROR\"");
                });
        assertThat(readRecords(session)).anyMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
    }

    @Test
    void preservesAcceptedParallelGroupsWhenConcurrencyIsDisabled()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "concurrency-disabled-groups", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(groupedPlanJson());
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("rootVisibleSkill");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setConcurrency(false);
        YamlSkillDefinition definition = new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);

        ExecutionPlan accepted = initializePlan(planningService,
                session, "parse both invoices", null, definition, chatClient, defaultVisibleTools())
                .orElseThrow();

        assertThat(definition.concurrencyEnabled()).isFalse();
        assertThat(accepted.tasks()).extracting(PlanTask::parallelGroup)
                .containsExactly("invoice-batch", "invoice-batch");
        assertThat(currentPlan(stateService, session).orElseThrow().tasks())
                .extracting(PlanTask::parallelGroup)
                .containsExactly("invoice-batch", "invoice-batch");
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .singleElement()
                .satisfies(record -> assertThat(record.data().toString())
                        .contains("\"parallelGroup\":\"invoice-batch\""));

        ExecutionPlan admitted = accepted.updateTask("task-1", task -> task.bindInProgress("Starting tool allowedVisibleSkill"));
        storePlan(stateService, session, admitted);
        bound(session, () -> { stateService.logPlanUpdated(session, admitted,
                ai.loomspan.internal.core.ExecutionTraceRecorder.PlanExecutionTransition.admission(
                        List.of("task-1"), "invoice-batch", false)); return null; });
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_UPDATED)
                .last()
                .satisfies(record -> assertThat(record.data().toString())
                        .contains("\"parallelGroup\":\"invoice-batch\"")
                        .doesNotContain("auto" + "Completable"));
    }

    @Test
    void exhaustsStructuralCorrectionWithoutPlanStorageOrCreation()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "structure-exhausted", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(
                invalidStructureYaml(), invalidStructureYaml());

        assertThatThrownBy(() -> initializePlan(planningService,
                session, "check invoice", null, rootDefinition("duplicateInvoiceChecker"), chatClient,
                List.of(toolCallback("invoiceParser", "parse"))))
                .hasMessageContaining("Plan validation failed")
                .hasMessageContaining("parallel-group-type")
                .hasMessageContaining("plan-dependency");

        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        assertThat(currentPlan(stateService, session)).isEmpty();
        assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
        assertThat(readRecords(session)).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED)
                .last().satisfies(record -> assertThat(record.metadata())
                        .containsEntry("retryCount", 1)
                        .containsEntry("validationStatus", "exhausted"));
    }

    @Test
    void sharesOneRetryAcrossTypedThenStructuralAndStructuralThenTypedFailures()
    {
        for (List<String> responses : List.of(
                List.of(weakSingleToolPlanJson(), invalidStructureYaml()),
                List.of(invalidStructureYaml(), weakSingleToolPlanJson())))
        {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            DefaultPlanningService planningService = new DefaultPlanningService(stateService);
            SequencePlanningChatClient chatClient = new SequencePlanningChatClient(responses.toArray(String[]::new));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "shared-retry-" + responses.getFirst().charAt(0), "test.entry", 3);

            assertThatThrownBy(() -> initializePlan(planningService,
                    session, "check invoice", null,
                    constrainedDefinition(new AllowedSkillConstraint("expenseLookup", 1, null, false)),
                    chatClient,
                    List.of(toolCallback("invoiceParser", "parse"), toolCallback("expenseLookup", "lookup"))))
                    .hasMessageContaining("Plan validation failed");
            assertThat(chatClient.systemMessagesSeen()).hasSize(2);
            assertThat(currentPlan(stateService, session)).isEmpty();
        }
    }

    @Test
    void sharesOneRetryAcrossEvidenceThenStructuralAndStructuralThenEvidenceFailures()
    {
        for (List<String> responses : List.of(
                List.of(weakSingleToolPlanJson(), invalidStructureYaml()),
                List.of(invalidStructureYaml(), weakSingleToolPlanJson())))
        {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            DefaultPlanningService planningService = new DefaultPlanningService(stateService);
            SequencePlanningChatClient chatClient = new SequencePlanningChatClient(responses.toArray(String[]::new));
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "shared-evidence-retry-" + responses.getFirst().charAt(0), "test.entry", 3);

            assertThatThrownBy(() -> initializePlan(planningService,
                    session, "check invoice", null, duplicateInvoiceDefinition(), chatClient,
                    List.of(toolCallback("invoiceParser", "parse"), toolCallback("expenseLookup", "lookup"))))
                    .hasMessageContaining("Plan validation failed");
            assertThat(chatClient.systemMessagesSeen()).hasSize(2);
            assertThat(currentPlan(stateService, session)).isEmpty();
            assertThat(readRecords(session)).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
        }
    }

    @Test
    void planningPromptShowsNoneWhenNoToolsProvided() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-no-tools", "test.entry", 3);
        SimpleChatClient chatClient = new SimpleChatClient(emptyPlan("plan-no-tools"), "done");

        initializePlan(planningService, session, "check invoice", null, rootDefinition("duplicateInvoiceChecker"), chatClient, List.of());

        String systemPrompt = chatClient.getSystemMessagesSeen().getFirst();
        assertThat(systemPrompt).contains("(none)");
    }

    @Test
    void planningPromptHardcodesTopLevelCapabilityName() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-cap-name", "test.entry", 3);
        SimpleChatClient chatClient = new SimpleChatClient(plan("plan-cap", PlanTaskStatus.PENDING), "done");

        initializePlan(planningService, session, "check invoice", null, rootDefinition("duplicateInvoiceChecker"), chatClient, defaultVisibleTools());

        String systemPrompt = chatClient.getSystemMessagesSeen().getFirst();
        assertThat(systemPrompt).contains("\"capabilityName\": \"duplicateInvoiceChecker\"");
    }

    @Test
    void rejectsContractBackedPlanWhenRequiredEvidenceRemainsUncoveredAfterRetries() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-evidence-fail", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(weakSingleToolPlanJson(), weakSingleToolPlanJson());

        assertThatThrownBy(() -> initializePlan(planningService,
                session,
                "check duplicate invoice",
                null,
                duplicateInvoiceDefinition(),
                chatClient,
                List.of(toolCallback("invoiceParser", "Extract invoice fields"), toolCallback("expenseLookup", "Look up matching expenses"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Plan validation failed");
        assertThat(planFor(session)).isEmpty();
        assertThat(chatClient.systemMessagesSeen()).hasSize(2);
        List<TraceRecord> records = readRecords(session);
        assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED)
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.metadata())
                        .containsEntry("issueCodes", List.of("evidence-coverage")));
        assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_RETRY_REQUESTED)
                .hasSize(1);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.PLAN_CREATED);
    }

    @Test
    void acceptsContractBackedPlanWhenTaskBindingsCoverAllRequiredEvidence() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-evidence-pass", "test.entry", 3);
        SequencePlanningChatClient chatClient = new SequencePlanningChatClient(correctedMultiToolPlanJson());

        ExecutionPlan plan = initializePlan(planningService,
                        session,
                        "check duplicate invoice",
                        null,
                        duplicateInvoiceDefinition(),
                        chatClient,
                        List.of(toolCallback("invoiceParser", "Extract invoice fields"), toolCallback("expenseLookup", "Look up matching expenses")))
                .orElseThrow();

        assertThat(plan.tasks()).extracting(PlanTask::capabilityName)
                .contains("invoiceParser", "expenseLookup");
    }

    private static BoundCapability toolCallback(String name, String description) {
        return ai.loomspan.testkit.TestBoundCapabilities.describedCapability(name, description);
    }

    private static String weakSingleToolPlanJson() {
        return """
                {
                  "planId": "plan-weak",
                  "capabilityName": "duplicateInvoiceChecker",
                  "createdAt": "2026-03-15T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {
                      "taskId": "t-1",
                      "title": "Parse invoice",
                      "status": "PENDING",
                      "capabilityName": "invoiceParser",
                      "intent": "Extract invoice fields",
                      "dependsOn": [],
                      "expectedOutputs": ["parsed invoice"],
                      "parallelGroup": null,
                      "note": ""
                    },
                    {
                      "taskId": "t-2",
                      "title": "Check duplicates",
                      "status": "PENDING",
                      "capabilityName": "invoiceParser",
                      "intent": "Check the invoice against prior expenses",
                      "dependsOn": ["t-1"],
                      "expectedOutputs": ["duplicate matches"],
                      "parallelGroup": null,
                      "note": ""
                    },
                    {
                      "taskId": "t-3",
                      "title": "Final report",
                      "status": "PENDING",
                      "capabilityName": "invoiceParser",
                      "intent": "Summarize the duplicate invoice result",
                      "dependsOn": ["t-2"],
                      "expectedOutputs": ["final report"],
                      "parallelGroup": null,
                      "note": ""
                    }
                  ]
                }
                """;
    }

    private static String correctedMultiToolPlanJson() {
        return """
                {
                  "planId": "plan-strong",
                  "capabilityName": "duplicateInvoiceChecker",
                  "createdAt": "2026-03-15T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {
                      "taskId": "t-1",
                      "title": "Parse invoice",
                      "status": "PENDING",
                      "capabilityName": "invoiceParser",
                      "intent": "Extract invoice fields",
                      "dependsOn": [],
                      "expectedOutputs": ["parsed invoice"],
                      "parallelGroup": null,
                      "note": ""
                    },
                    {
                      "taskId": "t-2",
                      "title": "Look up matching expenses",
                      "status": "PENDING",
                      "capabilityName": "expenseLookup",
                      "intent": "Find matching expenses for the parsed invoice",
                      "dependsOn": ["t-1"],
                      "expectedOutputs": ["matching expenses"],
                      "parallelGroup": null,
                      "note": ""
                    },
                    {
                      "taskId": "t-3",
                      "title": "Compare evidence",
                      "status": "PENDING",
                      "capabilityName": "expenseLookup",
                      "intent": "Compare the parsed invoice against matching expenses",
                      "dependsOn": ["t-2"],
                      "expectedOutputs": ["duplicate decision"],
                      "parallelGroup": null,
                      "note": ""
                    }
                  ]
                }
                """;
    }

    private static CapabilityMetadata capability(String name) {
        return new CapabilityMetadata(
                "yaml:child",
                name,
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "ok", CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic(name, "child"), null);
    }

    private static YamlSkillDefinition duplicateInvoiceDefinition() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("duplicateInvoiceChecker");
        manifest.setDescription("duplicateInvoiceChecker");
        manifest.setModel("gpt-5");
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType("object");
        schema.setProperties(Map.of(
                "vendorName", scalarSchema("string"),
                "isDuplicate", scalarSchema("boolean")));
        schema.setRequired(List.of("vendorName", "isDuplicate"));
        schema.setAdditionalProperties(false);
        manifest.setOutputSchema(schema);
        manifest.setOutputSchemaMaxRetries(1);
        Map<String, String> contract = Map.of(
                "vendorName", "invoiceParser",
                "isDuplicate", "invoiceParser and expenseLookup");
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(contract));
    }

    private static YamlSkillDefinition constrainedEvidenceDefinition() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("duplicateInvoiceChecker");
        manifest.setDescription("duplicateInvoiceChecker");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setAllowedSkills(List.of(
                new YamlSkillManifest.AllowedSkillManifest("invoiceParser", null, null, null),
                new YamlSkillManifest.AllowedSkillManifest("expenseLookup", 1, null, null)));
        Map<String, String> contract = Map.of("isDuplicate", "invoiceParser and expenseLookup");
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(contract));
    }

    private static YamlSkillDefinition rootDefinition() {
        return rootDefinition("rootVisibleSkill");
    }

    private static YamlSkillDefinition rootDefinition(String name) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setModel("gpt-5");
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition constrainedDefinition(AllowedSkillConstraint... constraints) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("duplicateInvoiceChecker");
        manifest.setDescription("duplicateInvoiceChecker");
        manifest.setModel("gpt-5");
        manifest.setPlanningMode(true);
        manifest.setAllowedSkills(java.util.Arrays.stream(constraints)
                .map(constraint -> new YamlSkillManifest.AllowedSkillManifest(
                        constraint.name(), constraint.minTasks(), constraint.maxTasks(), constraint.required()))
                .toList());
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]), manifest, EXECUTION_CONFIGURATION);
    }

    private static YamlSkillDefinition rootDefinitionWithPrompt(String prompt) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("rootVisibleSkill");
        manifest.setDescription("Short planner-facing summary");
        manifest.setModel("gpt-5");
        manifest.setPrompt(prompt);
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION);
    }

    private static YamlSkillManifest.OutputSchemaManifest scalarSchema(String type) {
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType(type);
        return schema;
    }

    private static ExecutionPlan plan(String id, PlanTaskStatus status) {
        return new ExecutionPlan(
                id,
                "rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of(new PlanTask("task-1", "Use tool", status,
                        "allowedVisibleSkill", "Use tool", List.of(), List.of(), null, null)));
    }

    private static String invalidStructureYaml()
    {
        return """
                capabilityName: duplicateInvoiceChecker
                createdAt: 2026-03-15T12:00:00Z
                status: VALID
                tasks:
                  - taskId: t-1
                    title: Parse invoice
                    status: PENDING
                    capabilityName: invoiceParser
                    intent: Parse
                    dependsOn: t-0
                    expectedOutputs: []
                    parallelGroup: 7
                    note: ""
                """;
    }

    private static String groupedPlanJson()
    {
        return """
                {
                  "planId": "model-plan-id",
                  "capabilityName": "rootVisibleSkill",
                  "createdAt": "2026-03-15T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {
                      "taskId": "task-1",
                      "title": "Parse first invoice",
                      "status": "PENDING",
                      "capabilityName": "allowedVisibleSkill",
                      "intent": "Parse invoice A",
                      "dependsOn": [],
                      "expectedOutputs": ["parsed A"],
                      "parallelGroup": "invoice-batch",
                      "note": ""
                    },
                    {
                      "taskId": "task-2",
                      "title": "Parse second invoice",
                      "status": "PENDING",
                      "capabilityName": "allowedVisibleSkill",
                      "intent": "Parse invoice B",
                      "dependsOn": [],
                      "expectedOutputs": ["parsed B"],
                      "parallelGroup": "invoice-batch",
                      "note": ""
                    }
                  ]
                }
                """;
    }

    private static ExecutionPlan emptyPlan(String id) {
        return new ExecutionPlan(id, "rootVisibleSkill", Instant.parse("2026-03-15T12:00:00Z"), List.of());
    }

    private static List<BoundCapability> defaultVisibleTools() {
        return List.of(toolCallback("allowedVisibleSkill", "Default test capability"));
    }

    private static DefaultPlanningService planningService(
            DefaultExecutionStateService stateService,
            Supplier<String> planIdSupplier) {
        LoomspanJacksonCodecs codecs = LoomspanJacksonCodecs.defaults();
        return new DefaultPlanningService(stateService,
                codecs.planningJson(),
                codecs.planningYaml(),
                new PlanTaskConstraintValidator(),
                new EvidenceCoverageValidator(),
                planIdSupplier);
    }

    private static String planJson(String id, PlanTaskStatus status) {
        return """
                {
                  "planId": "%s",
                  "capabilityName": "rootVisibleSkill",
                  "createdAt": "2026-03-15T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {
                      "taskId": "task-1",
                      "title": "Use tool",
                      "status": "%s",
                      "capabilityName": "allowedVisibleSkill",
                      "intent": "Use tool",
                      "dependsOn": [],
                      "expectedOutputs": [],
                      "parallelGroup": null
                    }
                  ]
                }
                """.formatted(id, status.name());
    }

    private static final Map<LoomspanSession, ai.loomspan.internal.core.ExecutionBinding> BINDINGS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private static <T> T bound(LoomspanSession session, java.util.function.Supplier<T> action)
    {
        var binding = BINDINGS.computeIfAbsent(
                session, ai.loomspan.internal.core.TestExecutionBindings::missionBinding);
        return ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(binding, action);
    }

    private static Optional<ExecutionPlan> initializePlan(
            PlanningService delegate, LoomspanSession session, String objective,
            Map<String, Object> missionInput, YamlSkillDefinition definition,
            ai.loomspan.internal.model.ModelInteraction interaction,
            List<BoundCapability> visibleTools)
    {
        return bound(session, () -> delegate.initializePlan(
                session, objective, missionInput, definition, interaction, visibleTools));
    }

    private static Optional<String> markToolStarted(
            PlanningService delegate, LoomspanSession session,
            ai.loomspan.internal.core.CapabilityMetadata capability)
    {
        return bound(session, () -> delegate.markToolStarted(session, capability));
    }

    private static Optional<ExecutionPlan> markToolCompleted(
            PlanningService delegate, LoomspanSession session, String taskId,
            String capabilityName)
    {
        return bound(session, () -> delegate.markToolCompleted(session, taskId, capabilityName));
    }

    private static Optional<ExecutionPlan> markToolFailed(
            PlanningService delegate, LoomspanSession session, String taskId,
            String capabilityName, RuntimeException failure)
    {
        return bound(session, () -> delegate.markToolFailed(session, taskId, capabilityName, failure));
    }

    private static void storePlan(
            DefaultExecutionStateService stateService, LoomspanSession session, ExecutionPlan plan)
    {
        bound(session, () -> { stateService.storePlan(plan); return null; });
    }

    private static Optional<ExecutionPlan> currentPlan(
            DefaultExecutionStateService stateService, LoomspanSession session)
    {
        return bound(session, stateService::currentPlan);
    }

    private static final class BlockingCompletedPlanStateService extends DefaultExecutionStateService
    {
        private final CountDownLatch completedPlanStored = new CountDownLatch(1);
        private final CountDownLatch releaseStore = new CountDownLatch(1);
        private final CountDownLatch cutoffStarted = new CountDownLatch(1);

        private BlockingCompletedPlanStateService()
        {
            super(FIXED_CLOCK);
        }

        @Override
        public void storePlan(ExecutionPlan plan)
        {
            super.storePlan(plan);
            boolean completed = plan.tasks().stream()
                    .anyMatch(task -> task.status() == PlanTaskStatus.COMPLETED);
            if (!completed) return;
            completedPlanStored.countDown();
            try
            {
                if (!releaseStore.await(2, TimeUnit.SECONDS))
                {
                    throw new AssertionError("Timed out waiting to release the completed plan store");
                }
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to release the completed plan store", ex);
            }
        }
    }

    private static Optional<ExecutionPlan> planFor(LoomspanSession session)
    {
        return BINDINGS.containsKey(session)
                ? BINDINGS.get(session).requireMission().currentPlan()
                : Optional.empty();
    }

    private static List<TraceRecord> readRecords(LoomspanSession session) {
        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        return records;
    }

    @SuppressWarnings("unchecked")
    private static void assertPlanningTransition(
            TraceRecord record, String kind, String taskId, Boolean effectiveConcurrency, String outcome)
    {
        Map<String, Object> transition = (Map<String, Object>) record.metadata().get("transition");
        assertThat(transition)
                .containsEntry("kind", kind)
                .containsEntry("taskIds", List.of(taskId))
                .containsEntry("parallelGroup", null);
        if (kind.equals("ADMISSION"))
            assertThat(transition).containsOnlyKeys("kind", "taskIds", "parallelGroup", "effectiveConcurrency")
                    .containsEntry("effectiveConcurrency", effectiveConcurrency);
        else
            assertThat(transition).containsOnlyKeys("kind", "taskIds", "parallelGroup", "outcome")
                    .containsEntry("outcome", outcome);
    }

    private static final class RecordingSessionUsageService implements SessionUsageService {

        private String lastSkillName;

        @Override
        public SessionUsageSnapshot snapshot(LoomspanSession session) {
            return session.getSessionUsage().orElse(SessionUsageSnapshot.empty());
        }

        @Override
        public void recordMissionStart(LoomspanSession session, String skillName) {
        }

        @Override
        public void reserveProviderAttempt(LoomspanSession session, String skillName) {
        }

        @Override
        public void recordProviderAttemptOutcome(LoomspanSession session, String skillName,
                ai.loomspan.internal.core.ModelExecutionIdentity identity, String outcome,
                ai.loomspan.internal.provider.ProviderFailureCategory category,
                ai.loomspan.internal.provider.ProviderRetryDecision decision) {
        }

        @Override
        public void recordModelResponse(LoomspanSession session,
                                        String skillName,
                                        ai.loomspan.internal.core.ModelExecutionIdentity identity,
                                        ai.loomspan.internal.runtime.usage.ModelUsageRecord usageRecord) {
            lastSkillName = skillName;
            SessionUsageSnapshot existing = snapshot(session);
            session.setSessionUsage(existing.recordModelUsage(usageRecord));
        }

        @Override
        public void recordToolCall(LoomspanSession session, String skillName, String capabilityName) {
        }

        @Override
        public void recordToolOutcome(LoomspanSession session, String skillName, String capabilityName, String outcome) {
        }

        @Override
        public void recordLinterOutcome(LoomspanSession session, ai.loomspan.internal.linter.LinterOutcome outcome) {
        }
    }

    private static final class SequencePlanningChatClient implements ai.loomspan.internal.model.ModelInteraction {

        private final Deque<String> responses = new ArrayDeque<>();
        private final List<String> systemMessagesSeen = new ArrayList<>();
        private final List<String> userMessagesSeen = new ArrayList<>();
        private int userConsumerCalls;

        private SequencePlanningChatClient(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        private List<String> systemMessagesSeen() {
            return systemMessagesSeen;
        }

        private List<String> userMessagesSeen() {
            return userMessagesSeen;
        }

        private int userConsumerCalls() {
            return userConsumerCalls;
        }

        @Override
        public ai.loomspan.internal.model.ModelInteractionResult call(
                ai.loomspan.internal.model.ModelInteractionRequest request) {
            systemMessagesSeen.add(request.systemPrompt());
            userMessagesSeen.add(request.input().userText());
            if (!request.input().attachments().isEmpty()) {
                userConsumerCalls++;
            }
            String next = responses.pollFirst();
            if (next == null) {
                throw new IllegalStateException("No more queued chat responses");
            }
            return new ai.loomspan.internal.model.ModelInteractionResult(next, Map.of(
                    ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY,
                    request.traceContext().nextAttempt()));
        }
    }
}
