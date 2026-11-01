package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.io.TempDir;
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
                        String system = wire.path("messages").get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan")) {
                            return text(JSON.writeValueAsString(Map.of("capabilityName", "default-step", "createdAt", "2026-11-01T00:00:00Z",
                                    "status", "VALID", "tasks", List.of(task(child)))));
                        }
                        if (system.contains("Exact capability/tool:")) return text(JSON.writeValueAsString(Map.of(
                                "stepAction", "CALL_TOOL", "taskId", "child", "toolName", child, "toolArguments", Map.of())));
                        for (var message : wire.path("messages")) {
                            if (message.path("role").asText().equals("tool")) {
                                assertThat(message.path("tool_call_id").asText()).isEqualTo("failed-call");
                                feedback.set(message.path("content").asText());
                                var data = JSON.readTree(feedback.get());
                                assertThat(data.path("status").asText()).isEqualTo("failed");
                                assertThat(data.path("correctionPermitted").asBoolean()).isTrue();
                                assertThat(data.path("message").asText()).isEqualTo(EXACT);
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
            if (kind.equals("once") && calls.get() > 1) return "REAL RESULT";
            if (delayMillis > 0) { try { Thread.sleep(delayMillis); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted", ex); } }
            var recoverable = new RecoverableSkillException(EXACT, new Exception("diagnostic cause"));
            throw switch (kind) {
                case "recoverable", "once" -> recoverable;
                case "transparent" -> new java.lang.reflect.UndeclaredThrowableException(new java.lang.reflect.InvocationTargetException(recoverable));
                case "application" -> new IllegalStateException("terminal outer", recoverable);
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
    static MockResponse tool(String name) { return response(Map.of("role", "assistant", "content", "", "tool_calls", List.of(
            Map.of("id", "failed-call", "type", "function", "function", Map.of("name", name, "arguments", "{}")))), "tool_calls"); }
    static MockResponse text(String value) { return response(Map.of("role", "assistant", "content", value), "stop"); }
    static MockResponse response(Map<String,Object> message, String reason) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(Map.of(
                "id", "fixture", "object", "chat.completion", "created", 1, "model", "fixture", "choices", List.of(
                        Map.of("index", 0, "message", message, "finish_reason", reason)),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2))));
    }
}
