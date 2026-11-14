package ai.loomspan.internal.runtime.trace;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.chat.ProviderAttemptCallAdvisor;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.TestExecutionBindings;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.DefaultMissionExecutionEngine;
import ai.loomspan.internal.runtime.SimpleChatClient;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.planning.DefaultPlanningService;
import ai.loomspan.internal.runtime.planning.PlanningServiceTestFactory;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.usage.DefaultSessionUsageService;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.NoOpUsageMetricsRecorder;
import ai.loomspan.internal.provider.AttemptOwnership;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.provider.ProviderFailureDetails;
import ai.loomspan.internal.provider.ProviderRetryPolicy;
import ai.loomspan.internal.springai.SpringAiModelInteraction;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionTraceContractTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
    private static final EffectiveSkillExecutionConfiguration EXECUTION_CONFIGURATION =
            new EffectiveSkillExecutionConfiguration("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");

    @Test
    void structuredChatCapturePreservesOrderNullsEmptyValuesAndExactContent() {
        String system = "  system \r\n\u96ea  ";
        String user = "  user\t\n ";
        String rawArguments = "{ invalid JSON \r\n" + "\u96ea\ud83d\ude00".repeat(5000) + " } " + (char) 0xDC00;
        String feedback = "  {\"message\":\"verbatim\"}\r\n ";
        var nullFields = new AssistantMessage.ToolCall(null, "function", null, null);
        var emptyFields = new AssistantMessage.ToolCall("", "function", "", "");
        var repeatedId = new AssistantMessage.ToolCall("same", "function", "one-tool", rawArguments);
        var sameIdDifferentCall = new AssistantMessage.ToolCall("same", "function", "two-tool", "  {}\n");
        var assistant = AssistantMessage.builder().toolCalls(List.of(nullFields, emptyFields, repeatedId, sameIdDifferentCall)).build();
        var emptyAssistant = AssistantMessage.builder().content("").build();
        var tool = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("same", "one-tool", feedback),
                new ToolResponseMessage.ToolResponse("same", "two-tool", ""),
                new ToolResponseMessage.ToolResponse(null, null, null))).build();
        var missingOutput = mock(Generation.class);
        var response = new ChatResponse(List.of(new Generation(assistant), missingOutput, new Generation(emptyAssistant)),
                ChatResponseMetadata.builder().usage(new DefaultUsage(1, 2, 3)).build());
        var session = captureChatExchange(new Prompt(List.of(new SystemMessage(system), new UserMessage(user), assistant,
                tool, emptyAssistant, ToolResponseMessage.builder().build())), response);

        var records = modelRecords(session);
        assertThat(records).hasSize(2).allSatisfy(record -> assertThat(record.metadata()).containsEntry("payloadChunked", true));
        var mapper = new ObjectMapper();
        var messages = records.getFirst().data().path("messages");
        assertThat(records.getFirst().data().size()).isOne();
        assertThat(messages.size()).isEqualTo(6);
        assertThat(messageView(messages.get(0))).isEqualTo(mapper.valueToTree(Map.of("messageType", "SYSTEM", "text", system)));
        assertThat(messageView(messages.get(1))).isEqualTo(mapper.valueToTree(Map.of("messageType", "USER", "text", user)));
        var expectedAssistant = mapper.createObjectNode().put("messageType", "ASSISTANT").put("text", "");
        var calls = expectedAssistant.putArray("toolCalls");
        calls.addObject().putNull("id").putNull("name").putNull("arguments");
        calls.addObject().put("id", "").put("name", "").put("arguments", "");
        calls.addObject().put("id", "same").put("name", "one-tool").put("arguments", rawArguments);
        calls.addObject().put("id", "same").put("name", "two-tool").put("arguments", "  {}\n");
        assertThat(messageView(messages.get(2))).isEqualTo(expectedAssistant);
        var expectedTool = mapper.createObjectNode().put("messageType", "TOOL").put("text", "");
        var responses = expectedTool.putArray("toolResponses");
        responses.addObject().put("id", "same").put("name", "one-tool").put("content", feedback);
        responses.addObject().put("id", "same").put("name", "two-tool").put("content", "");
        responses.addObject().putNull("id").putNull("name").putNull("content");
        assertThat(messageView(messages.get(3))).isEqualTo(expectedTool);
        var expectedEmptyAssistant = mapper.valueToTree(Map.of("messageType", "ASSISTANT", "text", "", "toolCalls", List.of()));
        assertThat(messageView(messages.get(4))).isEqualTo(expectedEmptyAssistant);
        assertThat(messageView(messages.get(5))).isEqualTo(mapper.valueToTree(Map.of("messageType", "TOOL", "text", "", "toolResponses", List.of())));
        var payload = records.getLast().data();
        assertThat(payload.size()).isEqualTo(3);
        var generations = payload.path("generations");
        assertThat(generations.size()).isEqualTo(3);
        assertThat(messageView(generations.get(0))).isEqualTo(expectedAssistant);
        assertThat(generations.get(1).isNull()).isTrue();
        assertThat(messageView(generations.get(2))).isEqualTo(expectedEmptyAssistant);
        assertThat(session.getSessionUsage().orElseThrow().usageUnits()).isEqualTo(3);
    }

    @Test
    void missingOrEmptyChatResponseHasAnEmptyGenerationsArray() {
        for (ChatResponse response : java.util.Arrays.asList(null, new ChatResponse(List.of()))) {
            var payload = modelRecords(captureChatExchange(new Prompt("user"), response)).getLast().data();
            assertThat(payload.path("generations")).isEqualTo(new ObjectMapper().valueToTree(List.of()));
            assertThat(payload.path("generationMetadata")).isEqualTo(new ObjectMapper().valueToTree(List.of()));
            assertThat(payload.path("metadata").size()).isEqualTo(response == null ? 0 : 2);
        }
    }

    private static tools.jackson.databind.JsonNode messageView(tools.jackson.databind.JsonNode node) {
        var copy = (tools.jackson.databind.node.ObjectNode) node.deepCopy();
        copy.remove("parts"); copy.remove("metadata");
        return copy;
    }

    @Test void orderedPartsAndStatusMetadataRetainExactProviderEvidence() {
        var opaque = new org.springframework.ai.chat.messages.part.OpaquePayload("openai.responses", "encrypted_content", "  opaque\r\n\u96ea");
        var message = AssistantMessage.builder().parts(List.of(
                new org.springframework.ai.chat.messages.part.ReasoningPart("thought", "summary", opaque, Map.of("id", "rs_1")),
                new org.springframework.ai.chat.messages.part.TextPart("commentary", null, Map.of("phase", "commentary")),
                new org.springframework.ai.chat.messages.part.ToolCallPart(new AssistantMessage.ToolCall("call_1", "function", "test-lookup", "  {}\n"), null, Map.of("id", "fc_1")),
                new org.springframework.ai.chat.messages.part.UnknownPart("fixture", "future", "{\"number\":1.0000000000000001}", null, Map.of("exact", "yes")))).build();
        var generation = new Generation(message, org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder()
                .finishReason("completed").metadata("providerStatus", "completed").build());
        var response = new ChatResponse(List.of(generation), ChatResponseMetadata.builder().id("resp_1")
                .model("fixture").keyValue("openai.responses.status", "completed").build());
        var data = modelRecords(captureChatExchange(new Prompt("test"), response)).getLast().data();
        var parts = data.at("/generations/0/parts");
        assertThat(parts.size()).isEqualTo(4);
        assertThat(parts.get(0).path("type").asText()).isEqualTo("reasoning");
        assertThat(parts.get(0).at("/payload/data").asText()).isEqualTo("  opaque\r\n\u96ea");
        assertThat(parts.get(0).at("/attributes/id").asText()).isEqualTo("rs_1");
        assertThat(parts.get(1).at("/attributes/phase").asText()).isEqualTo("commentary");
        assertThat(parts.get(2).at("/toolCall/id").asText()).isEqualTo("call_1");
        assertThat(parts.get(2).at("/toolCall/arguments").asText()).isEqualTo("  {}\n");
        assertThat(parts.get(3).path("rawJson").asText()).isEqualTo("{\"number\":1.0000000000000001}");
        assertThat(data.at("/metadata/id").asText()).isEqualTo("resp_1");
        assertThat(data.at("/metadata/openai.responses.status").asText()).isEqualTo("completed");
        assertThat(data.at("/generationMetadata/0/providerStatus").asText()).isEqualTo("completed");
    }

    private static LoomspanSession captureChatExchange(Prompt prompt, ChatResponse response) {
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("structured-chat", "default-test_entry", 3);
        var usage = new DefaultSessionUsageService(new LoomspanProperties().getSession().getQuotas(), new NoOpUsageMetricsRecorder());
        var state = new DefaultExecutionStateService(FIXED_CLOCK, usage);
        var retry = new LoomspanProperties.ProviderRetryProperties();
        retry.setEnabled(false);
        var runtime = ProviderConnectionRuntime.chat(mock(ChatModel.class), AiDriver.OPENAI,
                AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP, ProviderRetryPolicy.from(retry), ignored -> ProviderFailureDetails.unknown());
        var advisor = new ProviderAttemptCallAdvisor(runtime, state, new ModelUsageExtractor(), usage);
        var chain = mock(CallAdvisorChain.class);
        var request = new ChatClientRequest(prompt, Map.of(ModelTraceContext.REQUEST_CONTEXT_KEY,
                new ModelTraceContext(ModelExecutionIdentity.from(EXECUTION_CONFIGURATION), "default-test_entry", "mission")));
        when(chain.copy(advisor)).thenReturn(chain);
        when(chain.nextCall(request)).thenReturn(response == null ? null : ChatClientResponse.builder().chatResponse(response).build());
        TestExecutionBindings.callWithSession(session, () -> {
            state.openFrame(session, TraceFrameType.MODEL_CALL, "default-test_entry#model", Map.of());
            return advisor.adviseCall(request, chain);
        });
        return session;
    }

    @Test
    void engineCallSitesDoNotEmitDuplicateOuterModelEvents() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);

        LoomspanSession planningSession = ai.loomspan.internal.core.TestLoomspanSessions.withId("planning-trace", "default-test_entry", 3);
        TestExecutionBindings.callWithSession(planningSession, () -> planningService.initializePlan(
                planningSession,
                "hello",
                null,
                rootDefinition(),
                new SimpleChatClient(plan("plan-1"), "done"),
                List.<BoundCapability>of()));

        List<TraceRecord> planningModelRecords = modelRecords(planningSession);

        LoomspanSession missionSession = ai.loomspan.internal.core.TestLoomspanSessions.withId("mission-trace", "default-test_entry", 3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                new DefaultPlanningService(stateService),
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), executor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));

            String missionResponse = TestExecutionBindings.callWithSession(missionSession, () -> engine.executeMission(
                    missionSession,
                    rootDefinition(),
                    "hello",
                    null,
                    new SimpleChatClient(null, "mission complete"),
                    List.of(),
                    false,
                    null));

            assertThat(missionResponse).isEqualTo("mission complete");
        }

        List<TraceRecord> missionModelRecords = modelRecords(missionSession);

        assertThat(planningModelRecords).isEmpty();
        assertThat(missionModelRecords).isEmpty();
    }

    @Test
    void providerFailureMessagesRemainOutOfFrameMetadataAndAppearInDeliberateErrorDiagnostics() {
        String endpointSentinel = "http://127.0.0.1:1/SENTINEL-BASE";
        RuntimeException providerFailure = new IllegalStateException(
                "I/O error on POST request for \"" + endpointSentinel + "/v1/responses\"");
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);

        LoomspanSession planningSession = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "planning-provider-failure", "default-test_entry", 3);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);

        assertThatThrownBy(() -> TestExecutionBindings.callWithSession(planningSession, () -> planningService.initializePlan(
                planningSession,
                "hello",
                null,
                rootDefinition(),
                new SequencePlanningChatClient(providerFailure),
                List.of())))
                .isInstanceOf(RuntimeException.class);

        assertSafeFailureRecords(readRecords(planningSession), endpointSentinel, "Planning model invocation failed");

        LoomspanSession missionSession = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "mission-provider-failure", "default-test_entry", 3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultMissionExecutionEngine engine = new DefaultMissionExecutionEngine(
                new DefaultPlanningService(stateService),
                stateService,
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, Duration.ofSeconds(5), executor, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));

            assertThatThrownBy(() -> TestExecutionBindings.callWithSession(missionSession, () -> engine.executeMission(
                    missionSession,
                    rootDefinition(),
                    "hello",
                    null,
                    new SequencePlanningChatClient(providerFailure),
                    List.of(),
                    false,
                    null)))
                    .isInstanceOf(RuntimeException.class);
        }

        assertSafeFailureRecords(readRecords(missionSession), endpointSentinel, "Model invocation failed");
    }

    private static void assertSafeFailureRecords(List<TraceRecord> records, String sentinel, String safeMessage) {
        assertThat(records)
                .filteredOn(record -> record.recordType() == TraceRecordType.FRAME_CLOSED
                        && "failed".equals(record.metadata().get("status")))
                .isNotEmpty()
                .allSatisfy(record -> {
                    assertThat(record.metadata()).containsEntry("exceptionType", IllegalStateException.class.getName());
                    assertThat(record.metadata()).containsEntry("message", safeMessage);
                    assertThat(record.metadata().toString()).doesNotContain(sentinel);
                });
        assertThat(records)
                .filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                .singleElement()
                .satisfies(record -> assertThat(record.data().toString())
                        .contains(sentinel, "JAVA_STACK_TRACE", "captureLimitBytes"));
    }

    @Test
    void planCreationIsOwnedByPlanningFrameNotNestedModelFrame() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("planning-owner-trace", "default-test_entry", 3);

        TestExecutionBindings.callWithSession(session, () -> planningService.initializePlan(
                session,
                "hello",
                null,
                rootDefinition(),
                new SimpleChatClient(plan("plan-1"), "done"),
                List.<BoundCapability>of()));

        TraceRecord planCreated = readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .findFirst()
                .orElseThrow();

        assertThat(planCreated.frameType()).isEqualTo(TraceFrameType.PLANNING);
        assertThat(planCreated.route()).isEqualTo("default-rootVisibleSkill#planning");
        assertThat(planCreated.data()).isNotNull();
        assertThat(planCreated.data().path("createdAt").isTextual()).isTrue();
        assertThat(planCreated.data().path("createdAt").asText()).isEqualTo("2026-03-15T12:00:00Z");
    }

    @Test
    void evidenceRetryEventsStayUnderThePlanningFrameAndLinkAttempts() {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("planning-quality-trace", "default-test_entry", 3);

        TestExecutionBindings.callWithSession(session, () -> planningService.initializePlan(
                session,
                "check invoice duplicates",
                null,
                duplicateInvoiceDefinition(),
                new SequencePlanningChatClient(weakPlanJson(), correctedPlanJson()),
                List.of(tool("default-invoiceParser", "Extract invoice fields from source documents"),
                        tool("default-expenseLookup", "Look up related expenses for comparison"))));

        List<TraceRecord> records = readRecords(session);
        assertThat(records).anyMatch(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED
                && record.frameType() == TraceFrameType.PLANNING
                && record.metadata().containsKey("severity")
                && List.of("evidence-coverage").equals(record.metadata().get("issueCodes"))
                && record.metadata().containsKey("retryCount"));
        assertThat(records).anyMatch(record -> record.recordType() == TraceRecordType.PLAN_RETRY_REQUESTED
                && record.frameType() == TraceFrameType.PLANNING);
        TraceRecord rejected = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED)
                .findFirst()
                .orElseThrow();
        TraceRecord created = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .findFirst()
                .orElseThrow();
        assertThat(created.metadata().get("attemptId")).isNotEqualTo(rejected.metadata().get("attemptId"));
        assertThat(created.metadata().get("retrySequenceId")).isEqualTo(rejected.metadata().get("retrySequenceId"));
    }

    @Test
    void planChainsRemainDistinctAndJoinableAcrossRejectedAttemptsAndFrameTransitions() {
        DefaultSessionUsageService usageService = new DefaultSessionUsageService(
                new LoomspanProperties().getSession().getQuotas(),
                new NoOpUsageMetricsRecorder());
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK, usageService);
        Deque<String> planIds = new ArrayDeque<>(List.of(
                "rejected-candidate-plan",
                "framework-primary-plan",
                "framework-nested-plan"));
        DefaultPlanningService planningService = PlanningServiceTestFactory.withPlanIds(stateService, planIds::removeFirst);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                "multi-plan-lineage", "default-duplicateInvoiceChecker", 5);
        List<BoundCapability> tools = List.of(
                tool("default-invoiceParser", "Extract invoice fields from source documents"),
                tool("default-expenseLookup", "Look up related expenses for comparison"));

        ExecutionBinding primaryBinding = TestExecutionBindings.missionBinding(session);
        ExecutionFrame[] roots = new ExecutionFrame[2];
        ExecutionPlan[] plans = new ExecutionPlan[2];
        ExecutionBindingScope.runWith(primaryBinding, () -> {
        ExecutionFrame primaryRoot = roots[0] = stateService.openMissionFrame(session, "default-duplicateInvoiceChecker", Map.of());
        ExecutionPlan primary = plans[0] = planningService.initializePlan(
                        session,
                        "check invoice duplicates",
                        null,
                        duplicateInvoiceDefinition(),
                        providerBackedInteraction(stateService, usageService, weakPlanJson(), correctedPlanJson()),
                        tools).orElseThrow();
        ExecutionPlan primaryFirstUpdate = primary.withStatus(PlanStatus.STALE);
        stateService.storePlan(primaryFirstUpdate);
        stateService.logPlanUpdated(session, primaryFirstUpdate, null);

        ExecutionPlan nested = plans[1] = TestExecutionBindings.callWithCurrentSessionMission(() -> {
        ExecutionFrame nestedRoot = roots[1] = stateService.openMissionFrame(session, "default-duplicateInvoiceChecker", Map.of());
        ExecutionPlan nestedPlan = planningService.initializePlan(
                        session,
                        "check invoice duplicates",
                        null,
                        duplicateInvoiceDefinition(),
                        providerBackedInteraction(stateService, usageService, correctedPlanJson()),
                        tools).orElseThrow();
        ExecutionPlan nestedFinal = nestedPlan.withStatus(PlanStatus.STALE);
        stateService.storePlan(nestedFinal);
        stateService.logPlanUpdated(session, nestedFinal, null);
        stateService.closeMissionFrame(session, nestedRoot);
        return nestedPlan;
        });

        ExecutionPlan primaryFinal = stateService.currentPlan().orElseThrow().withStatus(PlanStatus.INVALID);
        stateService.storePlan(primaryFinal);
        stateService.logPlanUpdated(session, primaryFinal, null);
        stateService.closeMissionFrame(session, primaryRoot);
        });
        ExecutionFrame primaryRoot = roots[0];
        ExecutionFrame nestedRoot = roots[1];
        ExecutionPlan primary = plans[0];
        ExecutionPlan nested = plans[1];

        List<TraceRecord> records = readRecords(session);
        List<TraceRecord> creations = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED)
                .toList();
        assertThat(creations).hasSize(2);
        TraceRecord primaryCreation = creations.stream()
                .filter(record -> primaryRoot.frameId().equals(record.parentFrameId()))
                .findFirst()
                .orElseThrow();
        TraceRecord nestedCreation = creations.stream()
                .filter(record -> nestedRoot.frameId().equals(record.parentFrameId()))
                .findFirst()
                .orElseThrow();
        assertThat(primaryCreation.route()).isEqualTo(nestedCreation.route());
        assertThat(primary.planId()).isEqualTo("framework-primary-plan");
        assertThat(nested.planId()).isEqualTo("framework-nested-plan");
        assertCreationIdentity(primaryCreation, primary.planId());
        assertCreationIdentity(nestedCreation, nested.planId());

        TraceRecord rejectedPrimary = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(rejectedPrimary.metadata().get("attemptId"))
                .isNotEqualTo(primaryCreation.metadata().get("attemptId"));
        assertThat(rejectedPrimary.metadata().get("retrySequenceId"))
                .isEqualTo(primaryCreation.metadata().get("retrySequenceId"));
        List<TraceRecord> modelResponses = records.stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                .toList();
        assertThat(modelResponses).hasSize(3);
        TraceRecord rejectedResponse = modelResponses.stream()
                .filter(record -> rejectedPrimary.metadata().get("attemptId").equals(record.metadata().get("attemptId")))
                .findFirst()
                .orElseThrow();
        TraceRecord acceptingResponse = modelResponses.stream()
                .filter(record -> primaryCreation.metadata().get("attemptId").equals(record.metadata().get("attemptId")))
                .findFirst()
                .orElseThrow();
        assertThat(rejectedResponse.metadata()).doesNotContainKey("planId");
        assertThat(rejectedResponse.data().path("generations").get(0).path("text").asText())
                .isEqualTo(weakPlanJson());
        assertThat(planChain(records, "rejected-candidate-plan")).isEmpty();
        assertThat(acceptingResponse.metadata().get("retrySequenceId"))
                .isEqualTo(primaryCreation.metadata().get("retrySequenceId"));

        List<TraceRecord> primaryChain = planChain(records, primary.planId());
        List<TraceRecord> nestedChain = planChain(records, nested.planId());
        assertThat(primaryChain).hasSize(3);
        assertThat(nestedChain).hasSize(2);
        assertThat(primaryChain.stream().filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED))
                .extracting(TraceRecord::frameId)
                .containsOnly(primaryRoot.frameId());
        assertThat(nestedChain.stream().filter(record -> record.recordType() == TraceRecordType.PLAN_UPDATED))
                .extracting(TraceRecord::frameId)
                .containsOnly(nestedRoot.frameId());
        assertThat(primaryChain.getLast().data().path("status").asText()).isEqualTo(PlanStatus.INVALID.name());
        assertThat(nestedChain.getLast().data().path("status").asText()).isEqualTo(PlanStatus.STALE.name());
        assertThat(primaryBinding.requireMission().currentPlan().orElseThrow().status())
                .isEqualTo(PlanStatus.INVALID);
    }

    private static void assertCreationIdentity(TraceRecord creation, String planId) {
        assertThat(creation.metadata())
                .containsEntry("planId", planId)
                .containsKeys("attemptId", "retrySequenceId");
        assertThat(creation.data().path("planId").asText()).isEqualTo(planId);
    }

    private static List<TraceRecord> planChain(List<TraceRecord> records, String planId) {
        return records.stream()
                .filter(record -> record.recordType() == TraceRecordType.PLAN_CREATED
                        || record.recordType() == TraceRecordType.PLAN_UPDATED)
                .filter(record -> planId.equals(record.metadata().get("planId")))
                .sorted(java.util.Comparator.comparingLong(TraceRecord::sequence))
                .peek(record -> assertThat(record.data().path("planId").asText()).isEqualTo(planId))
                .toList();
    }

    private static SpringAiModelInteraction providerBackedInteraction(
            DefaultExecutionStateService stateService,
            DefaultSessionUsageService usageService,
            String... responses) {
        QueueChatModel model = new QueueChatModel(responses);
        LoomspanProperties.ProviderRetryProperties retry = new LoomspanProperties.ProviderRetryProperties();
        retry.setEnabled(false);
        ProviderConnectionRuntime runtime = ProviderConnectionRuntime.chat(
                model,
                AiDriver.OPENAI,
                AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                ProviderRetryPolicy.from(retry),
                ignored -> ProviderFailureDetails.unknown());
        ChatClient client = ChatClient.builder(model)
                .defaultOptions(org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().model("openai/gpt-5"))
                .defaultAdvisors(new ProviderAttemptCallAdvisor(
                        runtime, stateService, new ModelUsageExtractor(), usageService))
                .build();
        return new SpringAiModelInteraction(client, ai.loomspan.internal.model.ModelInteractionMode.STANDARD);
    }

    private static final class QueueChatModel implements ChatModel {
        private final Queue<String> responses;

        private QueueChatModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
            return org.springframework.ai.openai.responses.OpenAiResponsesChatOptions.builder().model("openai/gpt-5").build();
        }

        @Override
        public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
            String content = responses.remove();
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(content))),
                    ChatResponseMetadata.builder()
                            .usage(new DefaultUsage(1, 1, 2))
                            .build());
        }
    }

    private static void assertEquivalentEnvelope(TraceRecord planningRecord, TraceRecord missionRecord) {
        assertThat(planningRecord.metadata().keySet()).containsExactlyElementsOf(missionRecord.metadata().keySet());
        assertThat(planningRecord.metadata()).containsEntry("frameworkModel", "gpt-5");
        assertThat(missionRecord.metadata()).containsEntry("frameworkModel", "gpt-5");
        assertThat(planningRecord.metadata()).containsEntry("connection", "test-connection");
        assertThat(missionRecord.metadata()).containsEntry("connection", "test-connection");
        assertThat(planningRecord.metadata()).containsEntry("driver", AiDriver.OPENAI.name());
        assertThat(missionRecord.metadata()).containsEntry("driver", AiDriver.OPENAI.name());
        assertThat(planningRecord.metadata()).containsEntry("providerModel", "openai/gpt-5");
        assertThat(missionRecord.metadata()).containsEntry("providerModel", "openai/gpt-5");
        assertThat(planningRecord.metadata()).containsEntry("skillName", "default-rootVisibleSkill");
        assertThat(missionRecord.metadata()).containsEntry("skillName", "default-rootVisibleSkill");
    }

    private static ExecutionPlan plan(String planId) {
        return new ExecutionPlan(
                planId,
                "default-rootVisibleSkill",
                Instant.parse("2026-03-15T12:00:00Z"),
                List.of());
    }

    private static YamlSkillDefinition rootDefinition() {
        return definition("default-rootVisibleSkill");
    }

    private static YamlSkillDefinition duplicateInvoiceDefinition() {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("duplicateInvoiceChecker");
        manifest.setDescription("default-duplicateInvoiceChecker");
        manifest.setModel("gpt-5");
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default",
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(
                        Map.of("isDuplicate", "default-invoiceParser and default-expenseLookup")));
    }

    private static YamlSkillDefinition definition(String name) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(ai.loomspan.internal.core.SkillIdentity.parse(name).localName());
        manifest.setDescription(name);
        manifest.setModel("gpt-5");
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default",
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                EXECUTION_CONFIGURATION);
    }

    private static List<TraceRecord> modelRecords(LoomspanSession session) {
        return readRecords(session).stream()
                .filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT
                        || record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                .toList();
    }

    private static List<TraceRecord> readRecords(LoomspanSession session) {
        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        return records;
    }

    private static BoundCapability tool(String name, String description) {
        return ai.loomspan.testkit.TestBoundCapabilities.describedCapability(name, description);
    }

    private static String weakPlanJson() {
        return """
                {

                  "capabilityName": "default-duplicateInvoiceChecker",
                  "createdAt": "2026-03-15T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {"taskId": "t-1", "title": "Parse invoice", "status": "PENDING", "capabilityName": "default-invoiceParser", "intent": "Extract invoice fields", "dependsOn": [], "expectedOutputs": ["parsed"], "parallelGroup": null, "note": ""},
                    {"taskId": "t-2", "title": "Check duplicates", "status": "PENDING", "capabilityName": "default-invoiceParser", "intent": "Check for matching expenses", "dependsOn": ["t-1"], "expectedOutputs": ["matches"], "parallelGroup": null, "note": ""},
                    {"taskId": "t-3", "title": "Final report", "status": "PENDING", "capabilityName": "default-invoiceParser", "intent": "Summarize duplicate findings", "dependsOn": ["t-2"], "expectedOutputs": ["report"], "parallelGroup": null, "note": ""}
                  ]
                }
                """;
    }

    private static String repeatedCapabilityPlanJson() {
        return """
                {

                  "capabilityName": "compareTransport",
                  "createdAt": "2026-08-22T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {"taskId": "search-outbound", "title": "Search outbound trains", "status": "PENDING", "capabilityName": "searchTrains", "intent": "Find outbound options", "dependsOn": [], "expectedOutputs": ["Outbound options"], "parallelGroup": null, "note": ""},
                    {"taskId": "search-return", "title": "Search return trains", "status": "PENDING", "capabilityName": "searchTrains", "intent": "Find return options", "dependsOn": [], "expectedOutputs": ["Return options"], "parallelGroup": null, "note": ""},
                    {"taskId": "rank-options", "title": "Rank transport options", "status": "PENDING", "capabilityName": "rankTransportOptions", "intent": "Compare collected options", "dependsOn": ["search-outbound", "search-return"], "expectedOutputs": ["Final selected outbound and return options"], "parallelGroup": null, "note": ""}
                  ]
                }
                """;
    }

    private static String correctedPlanJson() {
        return """
                {

                  "capabilityName": "default-duplicateInvoiceChecker",
                  "createdAt": "2026-03-15T12:00:00Z",
                  "status": "VALID",
                  "tasks": [
                    {"taskId": "t-1", "title": "Parse invoice", "status": "PENDING", "capabilityName": "default-invoiceParser", "intent": "Extract invoice fields", "dependsOn": [], "expectedOutputs": ["parsed"], "parallelGroup": null, "note": ""},
                    {"taskId": "t-2", "title": "Look up matches", "status": "PENDING", "capabilityName": "default-expenseLookup", "intent": "Find matching expenses", "dependsOn": ["t-1"], "expectedOutputs": ["matches"], "parallelGroup": null, "note": ""},
                    {"taskId": "t-3", "title": "Compare evidence", "status": "PENDING", "capabilityName": "default-expenseLookup", "intent": "Compare invoice and expenses", "dependsOn": ["t-2"], "expectedOutputs": ["decision"], "parallelGroup": null, "note": ""}
                  ]
                }
                """;
    }

    private static final class SequencePlanningChatClient implements ai.loomspan.internal.model.ModelInteraction {

        private final Deque<String> responses = new ArrayDeque<>();
        private final RuntimeException failure;

        private SequencePlanningChatClient(String... responses) {
            this.failure = null;
            this.responses.addAll(List.of(responses));
        }

        private SequencePlanningChatClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public ai.loomspan.internal.model.ModelInteractionResult call(
                ai.loomspan.internal.model.ModelInteractionRequest request) {
            if (failure != null) {
                throw failure;
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
