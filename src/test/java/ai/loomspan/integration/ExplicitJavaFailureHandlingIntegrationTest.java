package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** Shared managed Java declaration fixture through the supported facade and production tool manager. */
class ExplicitJavaFailureHandlingIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String EXACT = "  correct this\n\u96ea\t";
    @TempDir Path directory;

    @Test
    void declaringChildStopsAndOnlyItsStandardCallerReceivesAssociatedFeedback() throws Exception {
        Files.writeString(directory.resolve("child.yaml"), parent("child", "default-callable", false));
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", "default-child", false));
        for (boolean parentDeclaresInability : List.of(false, true)) {
            var behavior = new Behavior();
            behavior.kind = "success";
            try (var server = new MockWebServer()) {
                server.enqueue(tool("default-child"));
                server.enqueue(tool("default-callable"));
                String rawArguments = "{ \r\n \"reason\" : " + JSON.writeValueAsString(EXACT) + " \n }";
                server.enqueue(tool("loomspan_cannot_complete", "child-declaration", rawArguments, "Ignored candidate"));
                String parentReason = "Parent independently stops";
                server.enqueue(parentDeclaresInability ? inability(parentReason) : text("DONE"));
                server.start();
                runner(server, behavior).run(context -> {
                    assertThat(context).hasNotFailed();
                    var template = context.getBean(SkillTemplate.class);
                    if (parentDeclaresInability) {
                        assertThatThrownBy(() -> template.invoke("default-standard", Map.of()))
                                .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(parentReason);
                    } else {
                        assertThat(template.invoke("default-standard", Map.of())).isEqualTo("DONE");
                    }
                    assertThat(behavior.calls).hasValue(1);
                    assertThat(behavior.parent.get().successfulDirectSkills()).containsExactly("default-callable");
                    var session = behavior.session.get();
                    var usage = session.getSessionUsage().orElseThrow();
                    assertThat(usage.providerAttempts()).isEqualTo(4);
                    assertThat(usage.modelCalls()).isEqualTo(4);
                    assertThat(usage.usageUnits()).isEqualTo(8);
                    assertThat(usage.toolInvocations()).isEqualTo(2);
                    assertThat(usage.skillInvocations()).isEqualTo(3);

                    var records = new ArrayList<TraceRecord>();
                    session.readTraceRecords(records::add);
                    var failures = records.stream().filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED).toList();
                    assertThat(failures).hasSize(parentDeclaresInability ? 2 : 1);
                    var childFailure = failures.getFirst();
                    assertThat(childFailure.frameType()).isEqualTo(TraceFrameType.MODEL_CALL);
                    assertThat(childFailure.route()).isEqualTo("default-child#mission-model");
                    assertThat(childFailure.data().path("kind").asString()).isEqualTo("planner_inability");
                    assertThat(childFailure.data().path("classification").asString()).isEqualTo("RECOVERABLE");
                    assertThat(childFailure.data().path("exceptionMessage").asString()).isEqualTo(EXACT);
                    assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED)
                            .singleElement().satisfies(record -> assertThat(record.route()).isEqualTo("default-callable"));
                    assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.TOOL_CALL_FAILED)
                            .singleElement().satisfies(record -> {
                                assertThat(record.route()).isEqualTo("default-child");
                                assertThat(record.data().path("failure").path("classification").asString()).isEqualTo("RECOVERABLE");
                                assertThat(records).anySatisfy(mission -> {
                                    assertThat(mission.recordType()).isEqualTo(TraceRecordType.FRAME_OPENED);
                                    assertThat(mission.frameId()).isEqualTo(childFailure.parentFrameId());
                                    assertThat(mission.parentFrameId()).isEqualTo(record.frameId());
                                });
                            });
                    assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.MODEL_ATTEMPT_FAILED);

                    var completion = records.getLast();
                    assertThat(completion.recordType()).isEqualTo(TraceRecordType.TRACE_COMPLETED);
                    if (parentDeclaresInability) {
                        var parentFailure = failures.getLast();
                        assertThat(parentFailure.metadata()).doesNotContainKey("causedByFailureId");
                        assertThat(parentFailure.metadata().get("failureId")).isNotEqualTo(childFailure.metadata().get("failureId"));
                        assertThat(completion.metadata().get("terminalFailureId")).isEqualTo(parentFailure.metadata().get("failureId"));
                    } else {
                        assertThat(completion.metadata()).containsEntry("outcome", "SUCCEEDED").doesNotContainKey("terminalFailureId");
                    }
                    var traceRequest = records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT)
                            .toList().getLast();
                    var traceFeedback = traceRequest.data().path("messages").get(traceRequest.data().path("messages").size() - 1)
                            .path("toolResponses").get(0);
                    assertThat(traceFeedback.path("id").asString()).isEqualTo("failed-call");
                    assertThat(traceFeedback.path("name").asString()).isEqualTo("default-child");
                    for (int turn = 0; turn < 3; turn++) server.takeRequest();
                    var wire = JSON.readTree(server.takeRequest().getBody().readUtf8());
                    var messages = ai.loomspan.testkit.ResponsesFixtures.transcript(wire);
                    assertThat(messages.get(0).path("content").asString()).contains("ended unsuccessfully", "explanation and data",
                            "replay may be unsafe", "Repeating the same request does not address", "existing authority");
                    var feedback = messages.get(messages.size() - 1);
                    assertThat(feedback.path("call_id").asString()).isEqualTo("failed-call");
                    assertThat(feedback.path("content").asString()).isEqualTo(traceFeedback.path("content").asString());
                    assertThat(JSON.readTree(feedback.path("content").asString())).isEqualTo(JSON.valueToTree(Map.of(
                            "status", "failed", "kind", "planner_inability", "correctionPermitted", true, "message", EXACT)));
                });
                assertThat(server.getRequestCount()).isEqualTo(4);
            }
        }
    }

    @Test
    void standardChildInabilityPropagatesThroughAnExplicitStepAncestor() throws Exception {
        Files.writeString(directory.resolve("child.yaml"), parent("child", "default-callable", false));
        Files.writeString(directory.resolve("step.yaml"), parent("step", "default-child", true));
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", "default-step", false));
        var behavior = new Behavior();
        behavior.kind = "success";
        try (var server = new MockWebServer()) {
            server.enqueue(tool("default-step"));
            server.enqueue(text(JSON.writeValueAsString(Map.of("capabilityName", "default-step",
                    "createdAt", "2026-11-01T00:00:00Z", "status", "VALID", "tasks", List.of(task("default-child"))))));
            server.enqueue(text(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL", "taskId", "child",
                    "toolName", "default-child", "toolArguments", Map.of()))));
            server.enqueue(tool("default-callable"));
            server.enqueue(inability(EXACT));
            server.enqueue(text("DONE"));
            server.start();
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SkillTemplate.class).invoke("default-standard", Map.of())).isEqualTo("DONE");
                var records = new ArrayList<TraceRecord>();
                behavior.session.get().readTraceRecords(records::add);
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                        .singleElement().satisfies(failure -> {
                            assertThat(failure.route()).isEqualTo("default-child#mission-model");
                            assertThat(failure.data().path("exceptionMessage").asString()).isEqualTo(EXACT);
                        });
                assertThat(records.getLast().metadata()).containsEntry("outcome", "SUCCEEDED").doesNotContainKey("terminalFailureId");
            });
            assertThat(server.getRequestCount()).isEqualTo(6);
            for (int turn = 0; turn < 5; turn++) server.takeRequest();
            var messages = ai.loomspan.testkit.ResponsesFixtures.transcript(JSON.readTree(server.takeRequest().getBody().readUtf8()));
            var feedback = messages.get(messages.size() - 1);
            assertThat(feedback.path("call_id").asString()).isEqualTo("failed-call");
            assertThat(JSON.readTree(feedback.path("content").asString()).path("message").asString()).isEqualTo(EXACT);
        }
    }

    @Test
    void directAndAdmittedPlannerFailureBypassResultValidatorsAndKeepTheObserverSecondary() throws Exception {
        Files.writeString(directory.resolve("checked.yaml"), """
                name: checked
                description: Validate the accepted result
                model: local
                allowed_skills: [{name: default-callable}]
                output_schema_max_retries: 1
                output_schema:
                  type: object
                  properties:
                    answer: {type: string, x-loomspan-evidence: default-callable}
                  required: [answer]
                  additionalProperties: false
                linter:
                  type: REGEX
                  max_retries: 1
                  regex:
                    pattern: '^\\{.*\\}$'
                    message: Return an object
                input_schema:
                  type: object
                  required: [image]
                  properties:
                    image: {type: string, x-loomspan-attachment: {media_type: image, allowed_content_types: [image/jpeg]}}
                """);
        var image = new org.springframework.core.io.ByteArrayResource(new byte[] {(byte) 0xff, (byte) 0xd8,
                (byte) 0xff, 0x00, (byte) 0xd9}) {
            @Override public String getFilename() { return "fixture.jpg"; }
        };
        var input = Map.<String, Object>of("image", image);
        var behavior = new Behavior();
        behavior.kind = "success";
        String candidate = "{\"answer\":\"The requested item is unavailable.\"}";
        try (var server = new MockWebServer()) {
            String arguments = JSON.writeValueAsString(Map.of("reason", EXACT));
            server.enqueue(tool("loomspan_cannot_complete", "signal", arguments, candidate));
            server.enqueue(tool("loomspan_cannot_complete", "signal", arguments, candidate));
            server.enqueue(tool("default-callable"));
            server.enqueue(text(candidate));
            server.start();
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                var template = context.getBean(SkillTemplate.class);
                var direct = new ArrayList<SkillExecutionView>();
                assertThatThrownBy(() -> template.invoke("default-checked", input, direct::add))
                        .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(EXACT);
                var observerFailure = new IllegalStateException("Observer failed");
                var admitted = context.getBean(SkillInvocationHandoff.class).handoff("default-checked", input);
                var observed = new ArrayList<SkillExecutionView>();
                try {
                    admitted.invoke(view -> { observed.add(view); throw observerFailure; });
                    fail("Expected the declaring invocation to fail");
                } catch (RecoverableSkillException failure) {
                    assertThat(failure).isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(EXACT);
                    assertThat(failure.getSuppressed()).containsExactly(observerFailure);
                }
                for (var views : List.of(direct, observed)) {
                    assertThat(views).singleElement().satisfies(view -> assertThat(view.events())
                            .filteredOn(event -> event.type().equals("ERROR")).singleElement().satisfies(event ->
                                    assertThat(event.details()).containsEntry("classification", "RECOVERABLE")
                                            .containsEntry("exceptionMessage", EXACT)));
                }
                assertThat(behavior.calls).hasValue(0);
                assertThat(template.invoke("default-checked", input)).isEqualTo(candidate);
                assertThat(behavior.calls).hasValue(1);
            });
            assertThat(server.getRequestCount()).isEqualTo(4);
            for (int turn = 0; turn < 4; turn++) {
                var wire = JSON.readTree(server.takeRequest().getBody().readUtf8());
                assertThat(wire.path("tools")).extracting(tool -> tool.path("name").asString())
                        .containsExactlyInAnyOrder("default-callable", "loomspan_cannot_complete");
                assertThat(wire.path("input").toString()).contains("image_url");
            }
        }
    }

    @Test
    void assignedInabilityReachesDirectJavaCaller() throws Exception {
        Files.writeString(directory.resolve("child.yaml"), parent("child", "default-callable", false));
        Files.writeString(directory.resolve("step.yaml"), parent("step", "default-child", true));
        var behavior = new Behavior();
        behavior.kind = "success";
        try (var server = new MockWebServer()) {
            server.enqueue(plan("default-step", "default-child"));
            server.enqueue(text(stepInability(EXACT)));
            server.start();
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                var views = new ArrayList<SkillExecutionView>();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-step", Map.of(), views::add))
                        .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(EXACT);
                assertThat(views).singleElement().satisfies(view -> assertThat(view.events())
                        .filteredOn(event -> event.type().equals("ERROR")).singleElement().satisfies(event ->
                                assertThat(event.details()).containsEntry("classification", "RECOVERABLE")
                                        .containsEntry("exceptionMessage", EXACT)));
                assertThat(behavior.calls).hasValue(0);
            });
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertNoWireTools(server.takeRequest());
            assertNoWireTools(server.takeRequest());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void admittedSynthesisInabilityHonorsPostResponseQuota(boolean quota) throws Exception {
        Files.writeString(directory.resolve("step.yaml"), stepSynthesis("step"));
        var behavior = new Behavior();
        behavior.kind = "success";
        String declaration = stepInability(EXACT);
        try (var server = new MockWebServer()) {
            server.enqueue(plan("default-step", "default-callable"));
            server.enqueue(text(declaration));
            server.start();
            var runner = runner(server, behavior);
            if (quota) runner = runner.withPropertyValues("loomspan.session.quotas.max-model-calls=1");
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                var admitted = context.getBean(SkillInvocationHandoff.class).handoff("default-step", Map.of());
                var views = new ArrayList<SkillExecutionView>();
                if (quota) {
                    assertThatThrownBy(() -> admitted.invoke(views::add))
                            .isExactlyInstanceOf(SkillException.class)
                            .hasCauseInstanceOf(ai.loomspan.internal.runtime.LoomspanQuotaExceededException.class)
                            .hasStackTraceContaining("MAX_MODEL_CALLS");
                } else {
                    try {
                        admitted.invoke(views::add);
                        fail("Expected synthesis to fail");
                    } catch (RecoverableSkillException failure) {
                        assertThat(failure).isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(EXACT);
                    }
                }
                assertThat(views).hasSize(1);
                assertThat(behavior.calls).hasValue(1);
                assertThat(behavior.parent.get().completedTaskResults()).extracting(MissionContext.CompletedTaskResult::result)
                        .containsExactly("EARLIER WORK");
                var session = behavior.session.get();
                var records = new ArrayList<TraceRecord>();
                session.readTraceRecords(records::add);
                var completion = records.getLast();
                assertThat(completion.recordType()).isEqualTo(TraceRecordType.TRACE_COMPLETED);
                assertThat(completion.metadata()).containsEntry("outcome", "FAILED");
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                        .singleElement().satisfies(failure -> {
                            assertThat(completion.metadata().get("terminalFailureId")).isEqualTo(failure.metadata().get("failureId"));
                            if (quota) {
                                assertThat(failure.data().path("classification").asString()).isEqualTo("TERMINAL");
                                assertThat(failure.data().has("kind")).isFalse();
                                assertThat(failure.data().path("diagnostics")).hasSize(1);
                            } else assertStepSignal(failure, "default-step#step-2", declaration);
                        });
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                        .anySatisfy(record -> assertThat(record.data().path("generations").get(0).path("text").asString())
                                .isEqualTo(declaration));
                assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.STEP_ACTION_PROPOSED))
                        .hasSize(quota ? 0 : 1);
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.STEP_COMPLETED)
                        .singleElement().satisfies(record -> assertThat(record.metadata()).containsEntry("assignedTaskId", "child"));
                var usage = session.getSessionUsage().orElseThrow();
                assertThat(usage.providerAttempts()).isEqualTo(2);
                assertThat(usage.modelCalls()).isEqualTo(2);
                assertThat(usage.usageUnits()).isEqualTo(4);
                assertThat(usage.toolInvocations()).isOne();
                assertThat(usage.skillInvocations()).isEqualTo(2);
            });
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertNoWireTools(server.takeRequest());
            assertNoWireTools(server.takeRequest());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void stepChildInabilityUsesExistingStandardFeedbackThroughStepAncestors(boolean interveningStep) throws Exception {
        Files.writeString(directory.resolve("child.yaml"), stepSynthesis("child"));
        Files.writeString(directory.resolve("step.yaml"), parent("step", "default-child", true));
        String standardTarget = interveningStep ? "default-step" : "default-child";
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", standardTarget, false));
        var behavior = new Behavior();
        behavior.kind = "success";
        String declaration = stepInability(EXACT);
        try (var server = new MockWebServer()) {
            server.enqueue(tool(standardTarget));
            if (interveningStep) {
                server.enqueue(plan("default-step", "default-child"));
                server.enqueue(text(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL", "taskId", "child",
                        "toolName", "default-child", "toolArguments", Map.of()))));
            }
            server.enqueue(plan("default-child", "default-callable"));
            server.enqueue(text("{\"stepAction\":\"CANNOT_COMPLETE\",\"reason\":\"Invalid candidate\",\"taskId\":null}"));
            server.enqueue(text(declaration));
            server.enqueue(text("DONE"));
            server.start();
            int requests = interveningStep ? 7 : 5;
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SkillTemplate.class).invoke("default-standard", Map.of())).isEqualTo("DONE");
                assertThat(behavior.calls).hasValue(1);
                var caller = behavior.parent.get().parent().orElseThrow();
                assertNoAcceptedResult(caller, "default-child");
                if (interveningStep) assertNoAcceptedResult(caller.parent().orElseThrow(), "default-step");
                var session = behavior.session.get();
                var records = new ArrayList<TraceRecord>();
                session.readTraceRecords(records::add);
                var failures = records.stream().filter(record -> record.recordType() == TraceRecordType.ERROR_RECORDED).toList();
                assertThat(failures).singleElement().satisfies(failure -> assertStepSignal(failure, "default-child#step-2", declaration));
                Object failureId = failures.getFirst().metadata().get("failureId");
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.STEP_FAILED)
                        .hasSize(interveningStep ? 2 : 1).allSatisfy(record ->
                                assertThat(record.metadata()).containsEntry("failureId", failureId));
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.TOOL_CALL_FAILED)
                        .hasSize(interveningStep ? 2 : 1).allSatisfy(record -> assertThat(
                                record.data().path("failure").path("classification").asString()).isEqualTo("RECOVERABLE"));
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.FRAME_CLOSED
                                && record.frameType() == TraceFrameType.ROOT_MISSION && "failed".equals(record.metadata().get("status")))
                        .hasSize(interveningStep ? 2 : 1).allSatisfy(record ->
                                assertThat(record.metadata()).containsEntry("failureId", failureId));
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED)
                        .singleElement().satisfies(record -> assertThat(record.route()).isEqualTo("default-callable"));
                assertThat(records.getLast().metadata()).containsEntry("outcome", "SUCCEEDED").doesNotContainKey("terminalFailureId");
                var usage = session.getSessionUsage().orElseThrow();
                assertThat(usage.providerAttempts()).isEqualTo(requests);
                assertThat(usage.modelCalls()).isEqualTo(requests);
                assertThat(usage.usageUnits()).isEqualTo(2L * requests);
                assertThat(usage.toolInvocations()).isEqualTo(interveningStep ? 3 : 2);
                assertThat(usage.skillInvocations()).isEqualTo(interveningStep ? 4 : 3);
                var sent = records.stream().filter(record -> record.recordType() == TraceRecordType.MODEL_REQUEST_SENT).toList().getLast();
                var messages = sent.data().path("messages");
                var feedback = messages.get(messages.size() - 1).path("toolResponses").get(0);
                assertThat(feedback.path("id").asString()).isEqualTo("failed-call");
                assertThat(feedback.path("name").asString()).isEqualTo(standardTarget);
                assertThat(JSON.readTree(feedback.path("content").asString())).isEqualTo(JSON.valueToTree(Map.of(
                        "status", "failed", "kind", "planner_inability", "correctionPermitted", true, "message", EXACT)));
            });
            assertThat(server.getRequestCount()).isEqualTo(requests);
            server.takeRequest();
            for (int turn = 1; turn < requests - 1; turn++) assertNoWireTools(server.takeRequest());
            var messages = ai.loomspan.testkit.ResponsesFixtures.transcript(JSON.readTree(server.takeRequest().getBody().readUtf8()));
            var feedback = messages.get(messages.size() - 1);
            assertThat(feedback.path("call_id").asString()).isEqualTo("failed-call");
            assertThat(JSON.readTree(feedback.path("content").asString())).isEqualTo(JSON.valueToTree(Map.of(
                    "status", "failed", "kind", "planner_inability", "correctionPermitted", true, "message", EXACT)));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void applicationThrownInabilityRetainsJavaOriginWithoutModelSignal(boolean stepped) throws Exception {
        var behavior = new Behavior();
        behavior.kind = "inability";
        try (var server = new MockWebServer()) {
            if (stepped) {
                Files.writeString(directory.resolve("step.yaml"), parent("step", "default-callable", true));
                server.enqueue(plan("default-step", "default-callable"));
            }
            server.start();
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke(
                        stepped ? "default-step" : "default-callable", Map.of()))
                        .isExactlyInstanceOf(PlannerInabilityException.class).hasMessage(EXACT);
                var records = new ArrayList<TraceRecord>();
                behavior.session.get().readTraceRecords(records::add);
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED)
                        .singleElement().satisfies(failure -> {
                            assertThat(failure.frameType()).isNotEqualTo(TraceFrameType.MODEL_CALL);
                            assertThat(failure.data().path("kind").asString()).isEqualTo("planner_inability");
                            assertThat(failure.data().path("diagnostics").size()).isOne();
                            assertThat(records.getLast().metadata().get("terminalFailureId"))
                                    .isEqualTo(failure.metadata().get("failureId"));
                        });
            });
            assertThat(server.getRequestCount()).isEqualTo(stepped ? 1 : 0);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothJavaFormsFailTruthfullyAcrossDirectStandardAndStep(boolean executor) throws Exception {
        String child = executor ? "default-leaf" : "default-callable";
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Java leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", child, false));
        Files.writeString(directory.resolve("step.yaml"), parent("step", child, true));
        var behavior = new Behavior();
        try (var server = new MockWebServer()) {
            var requests = new AtomicInteger();
            var feedback = new AtomicReference<String>();
            var dispatcherFailure = new AtomicReference<Throwable>();
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    requests.incrementAndGet();
                    try {
                        var wire = JSON.readTree(request.getBody().readUtf8());
                        String system = ai.loomspan.testkit.ResponsesFixtures.transcript(wire).get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan")) {
                            return text(JSON.writeValueAsString(Map.of("capabilityName", "default-step", "createdAt", "2026-11-01T00:00:00Z",
                                    "status", "VALID", "tasks", List.of(task(child)))));
                        }
                        if (system.contains("Exact capability/tool:")) return text(JSON.writeValueAsString(Map.of(
                                "stepAction", "CALL_TOOL", "taskId", "child", "toolName", child, "toolArguments", Map.of())));
                        for (var message : ai.loomspan.testkit.ResponsesFixtures.transcript(wire)) {
                            if (message.path("role").asText().equals("tool")) {
                                assertThat(message.path("call_id").asText()).isEqualTo("failed-call");
                                feedback.set(message.path("content").asText());
                                var data = JSON.readTree(feedback.get());
                                assertThat(data.path("status").asText()).isEqualTo("failed");
                                assertThat(data.path("correctionPermitted").asBoolean()).isTrue();
                                assertThat(data.path("message").asText()).isEqualTo(EXACT);
                                assertThat(data.has("kind")).isFalse();
                                // This is checked on the next provider request, before any enclosing success.
                                assertFailedAttempt(behavior.session.get(), child);
                                assertNoAcceptedResult(behavior.parent.get(), child);
                                return text("DONE");
                            }
                        }
                        return tool(child);
                    } catch (Throwable ex) { dispatcherFailure.set(ex); return new MockResponse().setResponseCode(500); }
                }
            });
            server.start();
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                var template = context.getBean(SkillTemplate.class);
                for (String kind : List.of("recoverable", "terminal", "transparent", "application")) {
                    behavior.kind = kind;
                    boolean recoverable = kind.equals("recoverable") || kind.equals("transparent");
                    for (String route : List.of(child, "default-standard", "default-step")) {
                        requests.set(0); feedback.set(null); behavior.calls.set(0); behavior.session.set(null);
                        var views = new ArrayList<SkillExecutionView>();
                        if (route.equals("default-standard") && recoverable) {
                            assertThat(template.invoke(route, Map.of(), views::add)).isEqualTo("DONE");
                            assertThat(feedback.get()).isNotNull();
                            assertThat(requests).hasValue(2);
                            var usage = behavior.session.get().getSessionUsage().orElseThrow();
                            assertThat(usage.providerAttempts()).isEqualTo(2);
                            assertThat(usage.modelCalls()).isEqualTo(2);
                            assertThat(usage.toolInvocations()).isOne();
                            assertThat(usage.skillInvocations()).isEqualTo(2);
                            assertThat(usage.usageUnits()).isEqualTo(4);
                        } else {
                            assertThatThrownBy(() -> template.invoke(route, Map.of(), views::add))
                                    .isInstanceOf(SkillException.class).hasStackTraceContaining(recoverable ? EXACT : "terminal outer")
                                    .hasStackTraceContaining("diagnostic cause");
                            assertThat(feedback.get()).isNull();
                            assertThat(requests.get()).isEqualTo(route.equals(child) ? 0 : route.equals("default-step") ? (executor ? 2 : 1) : 1);
                        }
                        assertThat(behavior.calls).hasValue(1);
                        assertThat(views).hasSize(1);
                        assertThat(views.getFirst().events()).anySatisfy(event -> {
                            assertThat(event.type()).isEqualTo("ERROR");
                            assertThat(event.details()).containsEntry("classification", recoverable ? "RECOVERABLE" : "TERMINAL");
                        });
                        assertThat(dispatcherFailure.get()).isNull();
                    }
                }
            });
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void terminalStandardFailureNeverBecomesFeedback(boolean executor) throws Exception {
        String child = executor ? "default-leaf" : "default-callable";
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Java leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", child, false));
        var behavior = new Behavior(); behavior.kind = "terminal";
        try (var server = new MockWebServer()) {
            server.enqueue(tool(child)); server.enqueue(text("BROAD FEEDBACK CONTINUED")); server.start();
            runner(server, behavior).run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-standard", Map.of()))
                        .isInstanceOf(SkillException.class).hasStackTraceContaining("terminal outer").hasStackTraceContaining("diagnostic cause");
            });
            assertThat(server.getRequestCount()).isOne();
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualStandardCallbackKeepsSecurityCancellationAndShutdownTerminal(boolean executor) throws Exception {
        String child = executor ? "default-leaf" : "default-callable";
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Java leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", child, false));
        for (String control : List.of("security", "cancellation", "shutdown")) {
            var behavior = new Behavior(); behavior.kind = control;
            try (var server = new MockWebServer()) {
                server.enqueue(tool(child)); server.enqueue(text("MUST NOT CONTINUE")); server.start();
                runner(server, behavior).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-standard", Map.of()))
                            .hasStackTraceContaining(switch (control) {
                                case "security" -> "AccessDeniedException";
                                case "cancellation" -> "CancellationException";
                                default -> "FrameworkShutdownException";
                            });
                });
                assertThat(server.getRequestCount()).isOne();
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void normalReattemptAndAlternateSelectionCreditOnlyActualSuccess(boolean executor) throws Exception {
        String child = executor ? "default-leaf" : "default-callable";
        String alternate = executor ? "default-otherLeaf" : "default-otherCallable";
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Java leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("other.yaml"), "name: otherLeaf\ndescription: Other leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", child, false).replace("}]", "}, {name: " + alternate + "}]"));
        for (boolean changeTool : List.of(false,true)) {
            var behavior = new Behavior(); behavior.kind = "once";
            var sends = new AtomicInteger();
            var observed = new AtomicReference<Throwable>();
            try (var server = new MockWebServer()) {
                server.setDispatcher(new Dispatcher() {
                    @Override public MockResponse dispatch(RecordedRequest request) {
                        try {
                            int turn = sends.incrementAndGet();
                            if (turn == 1) return tool(child);
                            if (turn == 2) { assertFailedAttempt(behavior.session.get(), child); assertNoAcceptedResult(behavior.parent.get(), child); return tool(changeTool ? alternate : child); }
                            var body = request.getBody().readUtf8();
                            assertThat(body).contains("REAL RESULT");
                            return text("DONE");
                        } catch (Throwable ex) { observed.set(ex); return new MockResponse().setResponseCode(500); }
                    }
                }); server.start();
                runner(server, behavior).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SkillTemplate.class).invoke("default-standard", Map.of())).isEqualTo("DONE");
                    assertThat(observed.get()).isNull(); assertThat(behavior.calls).hasValue(2);
                    var records = new ArrayList<TraceRecord>(); behavior.session.get().readTraceRecords(records::add);
                    assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.TOOL_CALL_FAILED)).hasSize(1);
                    assertThat(records.stream().filter(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED)).hasSize(1);
                    var usage = behavior.session.get().getSessionUsage().orElseThrow();
                    assertThat(usage.toolInvocations()).isEqualTo(2); assertThat(usage.skillInvocations()).isEqualTo(3);
                    assertThat(usage.providerAttempts()).isEqualTo(3); assertThat(usage.modelCalls()).isEqualTo(3); assertThat(usage.usageUnits()).isEqualTo(6);
                });
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void failedContinuationConsumesEveryExistingBudget(boolean executor) throws Exception {
        String child = executor ? "default-leaf" : "default-callable";
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Java leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", child, false));
        for (String meter : List.of("tool-invocations", "skill-invocations", "provider-attempts", "model-calls", "usage-units")) {
            var behavior = new Behavior(); behavior.kind = "recoverable";
            try (var server = new MockWebServer()) {
                server.setDispatcher(new Dispatcher() {
                    @Override public MockResponse dispatch(RecordedRequest request) { return tool(child); }
                }); server.start();
                int limit = meter.equals("skill-invocations") || meter.equals("usage-units") ? 2 : 1;
                runner(server, behavior).withPropertyValues("loomspan.session.quotas.max-" + meter + "=" + limit).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-standard", Map.of()))
                            .hasStackTraceContaining("MAX_" + meter.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
                    assertThat(behavior.calls).hasValue(1);
                    var usage = behavior.session.get().getSessionUsage().orElseThrow();
                    assertThat(usage.toolInvocations()).isGreaterThanOrEqualTo(1);
                    assertThat(usage.skillInvocations()).isGreaterThanOrEqualTo(2);
                    assertThat(usage.modelCalls()).isGreaterThanOrEqualTo(1);
                    assertThat(usage.providerAttempts()).isEqualTo(server.getRequestCount());
                    assertThat(usage.usageUnits()).isGreaterThanOrEqualTo(2);
                    var records = new ArrayList<TraceRecord>(); behavior.session.get().readTraceRecords(records::add);
                    assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED);
                    assertThat(records.getLast().metadata().get("terminalFailureId")).isNotNull();
                });
                assertThat(server.getRequestCount()).isEqualTo(meter.equals("provider-attempts") ? 1 : 2);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void feedbackKeepsOriginalParentDeadline(boolean executor) throws Exception {
        String child = executor ? "default-leaf" : "default-callable";
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Java leaf\nexecutor: default-execute\n");
        Files.writeString(directory.resolve("standard.yaml"), parent("standard", child, false));
        var behavior = new Behavior(); behavior.kind = "recoverable"; behavior.delayMillis = 450;
        try (var server = new MockWebServer()) {
            server.enqueue(tool(child));
            server.enqueue(text("LATE SUCCESS").setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS));
            server.start();
            runner(server, behavior).withPropertyValues("loomspan.session.mission-timeout=800ms").run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-standard", Map.of()))
                        .hasStackTraceContaining("timed out");
                assertThat(behavior.calls).hasValue(1);
                var records = new ArrayList<TraceRecord>(); behavior.session.get().readTraceRecords(records::add);
                var completion = records.getLast();
                assertThat(completion.recordType()).isEqualTo(TraceRecordType.TRACE_COMPLETED);
                assertThat(completion.metadata().get("outcome")).isNotEqualTo("SUCCEEDED");
                assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED);
            });
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    static void assertNoAcceptedResult(MissionContext mission, String child) {
        assertThat(mission.successfulDirectSkills()).doesNotContain(child);
        assertThat(mission.completedTaskResults()).noneMatch(result -> result.skillName().equals(child));
        var binding = new ChildInputBinding(ObjectFieldPath.parse("/value", false), ChildInputBinding.SourceKind.CHILD_RESULT,
                ObjectFieldPath.parse("", true), child);
        assertThatThrownBy(() -> new ChildInputBindingAssembler().assemble(Map.of(), List.of(binding), Map.of(),
                mission.completedTaskResults(), mission.missionFrameId())).hasMessageContaining("binding_source_unavailable");
    }

    static void assertFailedAttempt(LoomspanSession session, String child) {
        var records = new ArrayList<TraceRecord>(); session.readTraceRecords(records::add);
        assertThat(records).anySatisfy(record -> assertThat(record.recordType()).isEqualTo(TraceRecordType.TOOL_CALL_FAILED));
        assertThat(records).anySatisfy(record -> {
            assertThat(record.recordType()).isEqualTo(TraceRecordType.ERROR_RECORDED);
            assertThat(record.data().path("exceptionMessage").asText()).isEqualTo(EXACT);
            assertThat(record.data().path("classification").asText()).isEqualTo("RECOVERABLE");
        });
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED);
        assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.FRAME_CLOSED
                && child.equals(record.route()) && "completed".equals(record.metadata().get("status")));
    }
    ApplicationContextRunner runner(MockWebServer server, Behavior behavior) {
        var proxy = new ProxyFactory(behavior); proxy.setProxyTargetClass(true);
        proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) call -> {
            if ("security".equals(behavior.kind) && (call.getMethod().getName().equals("callable") || call.getMethod().getName().equals("execute")))
                throw new org.springframework.security.access.AccessDeniedException("Application method advice denied execution");
            return call.proceed();
        });
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withBean("failures", Behavior.class, () -> (Behavior) proxy.getProxy())
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                        "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                        "loomspan.connections.local.api-key=fixture", "loomspan.connections.local.provider-retry.enabled=false",
                        "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=fixture",
                        "loomspan.session.mission-timeout=10s");
    }
    public static class Behavior {
        volatile String kind;
        volatile long delayMillis;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<LoomspanSession> session = new AtomicReference<>();
        final AtomicReference<MissionContext> parent = new AtomicReference<>();
        String fail() {
            calls.incrementAndGet(); session.set(ExecutionBindingScope.requireCurrent().session());
            var mission = ExecutionBindingScope.requireCurrent().requireMission(); parent.set(mission.parent().orElse(mission));
            if (kind.equals("success")) return "EARLIER WORK";
            if (kind.equals("once") && calls.get() > 1) return "REAL RESULT";
            if (delayMillis > 0) { try { Thread.sleep(delayMillis); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted", ex); } }
            var recoverable = new RecoverableSkillException(EXACT, new Exception("diagnostic cause"));
            throw switch (kind) {
                case "recoverable", "once" -> recoverable;
                case "transparent" -> new java.lang.reflect.UndeclaredThrowableException(new java.lang.reflect.InvocationTargetException(recoverable));
                case "application" -> new IllegalStateException("terminal outer", recoverable);
                case "inability" -> new PlannerInabilityException(EXACT);
                case "cancellation" -> new java.util.concurrent.CancellationException("Application cancellation");
                case "shutdown" -> new FrameworkShutdownException();
                default -> new IllegalStateException("terminal outer", new Exception("diagnostic cause"));
            };
        }
        @SkillMethod(name="callable", description="Fails explicitly") public String callable() { return fail(); }
        @SkillMethod(name="otherCallable", description="Alternative") public String otherCallable() { return fail(); }
        @SkillExecutor(name="execute") public String execute(SkillExecutorInvocation value) { return fail(); }
    }
    static String parent(String name, String child, boolean step) { return "name: " + name + "\ndescription: Failure route\nmodel: local\nplanning_mode: " + step
            + "\nmax_steps: 1\nallowed_skills: [{name: " + child + (step ? ", required: true, max_tasks: 1" : "") + "}]\n" + (step ? "output_from: {skill: " + child + "}\n" : ""); }
    static Map<String,Object> task(String child) {
        var value = new LinkedHashMap<String,Object>();
        value.put("taskId", "child"); value.put("title", "Child"); value.put("status", "PENDING"); value.put("capabilityName", child);
        value.put("intent", "Run child"); value.put("dependsOn", List.of()); value.put("expectedOutputs", List.of("result"));
        value.put("parallelGroup", null); value.put("note", ""); return value;
    }
    static MockResponse tool(String name) { return tool(name, "failed-call", "{}", ""); }
    private static MockResponse plan(String skill, String child) {
        return text(JSON.writeValueAsString(Map.of("capabilityName", skill, "createdAt", "2026-11-01T00:00:00Z",
                "status", "VALID", "tasks", List.of(task(child)))));
    }
    private static String stepSynthesis(String skill) {
        return "name: " + skill + "\ndescription: Synthesize after completing Java work\nmodel: local\nplanning_mode: true\n"
                + "max_steps: 2\nallowed_skills: [{name: default-callable, required: true, max_tasks: 1}]\n";
    }
    private static String stepInability(String reason) {
        return JSON.writeValueAsString(Map.of("stepAction", "CANNOT_COMPLETE", "reason", reason));
    }
    private static void assertNoWireTools(RecordedRequest request) {
        assertThat(JSON.readTree(request.getBody().readUtf8()).path("tools").size()).isZero();
    }
    private static void assertStepSignal(TraceRecord failure, String route, String declaration) {
        assertThat(failure.frameType()).isEqualTo(TraceFrameType.STEP_EXECUTION);
        assertThat(failure.route()).isEqualTo(route);
        assertThat(failure.data().path("classification").asString()).isEqualTo("RECOVERABLE");
        assertThat(failure.data().path("kind").asString()).isEqualTo("planner_inability");
        assertThat(failure.data().path("exceptionMessage").asString()).isEqualTo(EXACT);
        var diagnostics = failure.data().path("diagnostics");
        assertThat(diagnostics).extracting(node -> node.path("kind").asString())
                .containsExactly("JAVA_STACK_TRACE", "PLANNER_INABILITY_SIGNAL");
        assertThat(diagnostics.get(1).path("text").asString()).isEqualTo(declaration);
        assertThat(failure.metadata()).doesNotContainKey("causedByFailureId");
    }
    static MockResponse tool(String name, String id, String arguments, String content) {
        var output = new java.util.ArrayList<Map<String,Object>>();
        if (!content.isEmpty()) output.add(ai.loomspan.testkit.ResponsesFixtures.textItem(content));
        output.add(ai.loomspan.testkit.ResponsesFixtures.functionCall(name, id, arguments));
        return ai.loomspan.testkit.ResponsesFixtures.response("fixture", "fixture", output);
    }
    static MockResponse inability(String reason) {
        return tool("loomspan_cannot_complete", "signal", JSON.writeValueAsString(Map.of("reason", reason)), "Invalid candidate");
    }
    static MockResponse text(String value) { return ai.loomspan.testkit.ResponsesFixtures.text(value); }

}
