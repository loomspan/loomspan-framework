package ai.loomspan.integration;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillTemplate;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Supported facade and actual model HTTP requests, without replacement framework beans. */
class DesignatedChildResultIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL_RESULT = "  {\"report\":\"\u5b8c\u6574 report\"}  ";
    private static final String REST_RESULT = "  {\"status\":\"business-failure\",\"rows\":[3,1,2]}\n";
    private static final String JAVA_RESULT = "Quoted \"report\"\n";
    @TempDir Path directory;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    public static class JavaReport {
        @SkillMethod(name = "producer", description = "Produce one exact Java report")
        public String producer() { return JAVA_RESULT; }
    }

    @ParameterizedTest
    @ValueSource(strings = {"model", "java", "rest", "no-schema"})
    void forwardsModelJavaAndRestResultsThroughSkillTemplate(String kind) throws Exception {
        Capture capture = run(kind, false);
        assertThat(capture.result).isEqualTo(expected(kind));
        assertThat(capture.laterCalls.get()).isEqualTo(1);
        assertThat(capture.requests).noneMatch(body -> body.contains("All required plan tasks are already COMPLETE"));
        assertThat(capture.producerCalls.get()).isEqualTo(kind.equals("model") ? 2 : kind.equals("no-schema") ? 1 : 0);
        assertThat(capture.observed.get()).isNotNull();
    }

    @Test
    void forwardingChainRetainsProducerValidationAndExactResult() throws Exception {
        Capture capture = run("model", true);
        assertThat(capture.result).isEqualTo(MODEL_RESULT);
        assertThat(capture.producerCalls.get()).isEqualTo(2);
        assertThat(capture.laterCalls.get()).isEqualTo(1);
        assertThat(capture.requests).noneMatch(body -> body.contains("All required plan tasks are already COMPLETE"));
        assertThat(capture.requests.stream().filter(body -> body.contains("Create an ordered flight plan"))).hasSize(2);
    }

    @Test
    void ordinarySynthesisRemainsAvailable() throws Exception {
        Capture capture = run("synthesis", false);
        assertThat(JSON.readTree(capture.result).path("report").asText()).isEqualTo("PARENT_SYNTHESIS");
        assertThat(capture.requests.stream().filter(body -> body.contains("All required plan tasks are already COMPLETE"))).hasSize(1);
        assertThat(capture.laterCalls.get()).isEqualTo(1);
    }

    private Capture run(String kind, boolean chain) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("skills"));
        String rootYaml = parent("root", chain ? "middle" : "producer", true);
        if (kind.equals("synthesis")) rootYaml = rootYaml.replace("output_from: {skill: producer}\n", "").replace("max_steps: 2", "max_steps: 3");
        write(skills, "root", rootYaml);
        if (chain) write(skills, "middle", parent("middle", "producer", false));
        write(skills, "later", "name: later\ndescription: Complete later accepted work\nrest: true\n");
        if (kind.equals("rest") || kind.equals("synthesis")) write(skills, "producer", "name: producer\ndescription: Produce report\nrest: true\n");
        if (kind.equals("model") || kind.equals("no-schema")) write(skills, "producer", """
                name: producer
                description: Produce the complete report
                model: local
                prompt: PRODUCER_ONLY. Produce the complete report for the supplied request.
                """ + (kind.equals("model") ? """
                output_schema_max_retries: 1
                output_schema:
                  type: object
                  properties:
                    report: {type: string}
                  required: [report]
                  additionalProperties: false
                """ : ""));
        Capture capture = new Capture();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        String body = request.getBody().readUtf8();
                        capture.requests.add(body);
                        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
                        JsonNode wire = JSON.readTree(body);
                        String system = wire.path("messages").get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan")) {
                            boolean middle = system.contains("MIDDLE_ORCHESTRATION");
                            String parent = middle ? "middle" : "root";
                            String child = middle || !chain ? "producer" : "middle";
                            List<Object> tasks = new ArrayList<>();
                            tasks.add(task(child, List.of()));
                            if (!middle) tasks.add(task("later", List.of(child)));
                            return response(JSON.writeValueAsString(Map.of("capabilityName", parent,
                                    "createdAt", "2026-10-04T00:00:00Z", "status", "VALID", "tasks", tasks)));
                        }
                        if (system.contains("coordinator-assigned task")) {
                            var matcher = Pattern.compile("Exact capability/tool: (\\w+)").matcher(system);
                            assertThat(matcher.find()).isTrue();
                            String child = matcher.group(1);
                            return response(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL",
                                    "taskId", child, "toolName", child, "toolArguments", Map.of())));
                        }
                        if (kind.equals("synthesis") && system.contains("All required plan tasks are already COMPLETE"))
                            return response(JSON.writeValueAsString(Map.of("stepAction", "FINAL_RESPONSE", "finalResponse", Map.of("report", "PARENT_SYNTHESIS"))));
                        assertThat(system).contains("PRODUCER_ONLY");
                        int call = capture.producerCalls.incrementAndGet();
                        if (kind.equals("model") && call == 1) return response("{\"report\":9}");
                        assertThat(call).isLessThanOrEqualTo(kind.equals("model") ? 2 : 1);
                        return response(expected(kind));
                    } catch (Throwable failure) {
                        capture.failure.compareAndSet(null, failure);
                        return new MockResponse().setResponseCode(500).setBody("Unexpected test request");
                    }
                }
            });
            server.start();
            ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withBean(RestSkillHandler.class, () -> invocation -> {
                        if (invocation.skillName().equals("later")) {
                            capture.laterCalls.incrementAndGet();
                            return "LATER_WORK_COMPLETE";
                        }
                        assertThat(invocation.skillName()).isEqualTo("producer");
                        return REST_RESULT;
                    })
                    .withPropertyValues("spring.main.web-application-type=none",
                            "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                            "loomspan.connections.local.driver=openai",
                            "loomspan.connections.local.base-url=" + server.url("/v1"),
                            "loomspan.connections.local.api-key=local-test",
                            "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local",
                            "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.mission-timeout=30s");
            if (kind.equals("java")) runner = runner.withBean(JavaReport.class, JavaReport::new);
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                var catalog = context.getBean(SkillCatalog.class);
                String schema = catalog.skill("root").orElseThrow().outputSchema();
                if (kind.equals("model")) {
                    assertThat(JSON.readTree(schema).path("properties").path("report").path("type").asText()).isEqualTo("string");
                    assertThat(schema).isEqualTo(catalog.skill("producer").orElseThrow().outputSchema());
                    if (chain) assertThat(schema).isEqualTo(catalog.skill("middle").orElseThrow().outputSchema());
                } else assertThat(schema).isNull();
                capture.result = context.getBean(SkillTemplate.class).invoke("root", Map.of(), capture.observed::set);
            });
        }
        assertThat(capture.failure.get()).isNull();
        return capture;
    }

    @Test
    void parallelRootsForwardOnlyTheirOwnTaskResults() throws Exception {
        exerciseIsolation(false);
    }

    @Test
    void runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload() throws Exception {
        exerciseIsolation(true);
    }

    private void exerciseIsolation(boolean reload) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("isolation"));
        String oldRoot = parent("root", "oldProducer", false);
        String oldLeaf = isolatedProducer("old");
        write(skills, "root", oldRoot);
        write(skills, "oldProducer", oldLeaf);
        CountDownLatch heldProducer = new CountDownLatch(1);
        CountDownLatch releaseProducer = new CountDownLatch(1);
        CountDownLatch parallelProducers = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        JsonNode wire = JSON.readTree(request.getBody().readUtf8());
                        String system = wire.path("messages").get(0).path("content").asText();
                        String messages = "";
                        for (JsonNode message : wire.path("messages")) messages += message.path("content").asText();
                        var markerMatch = Pattern.compile("\"marker\"\\s*:\\s*\"([^\"]+)\"").matcher(messages);
                        assertThat(markerMatch.find()).isTrue();
                        String marker = markerMatch.group(1);
                        boolean newer = system.contains("newProducer") || system.contains("PRODUCER_new");
                        String producer = newer ? "newProducer" : "oldProducer";
                        if (system.contains("Create an ordered flight plan"))
                            return response(JSON.writeValueAsString(Map.of("capabilityName", "root", "createdAt", "2026-10-04T00:00:00Z",
                                    "status", "VALID", "tasks", List.of(task(producer, List.of())))));
                        if (system.contains("coordinator-assigned task"))
                            return response(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL", "taskId", producer,
                                    "toolName", producer, "toolArguments", Map.of("marker", marker))));
                        assertThat(system).contains("PRODUCER_");
                        if (marker.equals("held")) { heldProducer.countDown(); await(releaseProducer); }
                        else if (!reload) { parallelProducers.countDown(); await(parallelProducers); }
                        return response(JSON.writeValueAsString(Map.of(newer ? "newReport" : "oldReport", marker)));
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                        return new MockResponse().setResponseCode(500).setBody("Unexpected isolation request");
                    }
                }
            });
            server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withPropertyValues("spring.main.web-application-type=none", "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                            "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                            "loomspan.connections.local.api-key=local-test", "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.mission-timeout=30s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillTemplate facade = context.getBean(SkillTemplate.class);
                        SkillReloader reloader = context.getBean(SkillReloader.class);
                        SkillCatalog capturedCatalog = reloader.snapshot();
                        String oldSchema = capturedCatalog.skill("root").orElseThrow().outputSchema();
                        assertThat(oldSchema).contains("oldReport");
                        try (var executor = Executors.newFixedThreadPool(2)) {
                            if (reload) {
                                var oldResult = executor.submit(() -> facade.invoke("root", Map.of("marker", "held")));
                                try {
                                    await(heldProducer);
                                    try (var update = reloader.prepare(List.of(
                                            new SkillDocument("new-root", parent("root", "newProducer", false)),
                                            new SkillDocument("new-leaf", isolatedProducer("new"))))) {
                                        reloader.publish(update);
                                    }
                                    assertThat(reloader.snapshot().generationId()).isNotEqualTo(capturedCatalog.generationId());
                                    assertThat(reloader.snapshot().skill("root").orElseThrow().outputSchema()).contains("newReport");
                                    assertThat(capturedCatalog.skill("root").orElseThrow().outputSchema()).isEqualTo(oldSchema);
                                    assertThat(JSON.readTree(facade.invoke("root", Map.of("marker", "fresh"))).path("newReport").asText()).isEqualTo("fresh");
                                } finally { releaseProducer.countDown(); }
                                assertThat(JSON.readTree(oldResult.get(15, TimeUnit.SECONDS)).path("oldReport").asText()).isEqualTo("held");
                            } else {
                                var a = executor.submit(() -> facade.invoke("root", Map.of("marker", "A")));
                                var b = executor.submit(() -> facade.invoke("root", Map.of("marker", "B")));
                                assertThat(JSON.readTree(a.get(15, TimeUnit.SECONDS)).path("oldReport").asText()).isEqualTo("A");
                                assertThat(JSON.readTree(b.get(15, TimeUnit.SECONDS)).path("oldReport").asText()).isEqualTo("B");
                            }
                        } catch (Exception ex) { throw new AssertionError(ex); }
                    });
        } finally { releaseProducer.countDown(); }
        assertThat(failure.get()).isNull();
    }

    private static String isolatedProducer(String generation) {
        return """
                name: %sProducer
                description: Produce the generation-specific report
                model: local
                prompt: PRODUCER_%s. Produce the report from the supplied marker.
                output_schema:
                  type: object
                  properties:
                    %sReport: {type: string}
                  required: [%sReport]
                  additionalProperties: false
                """.formatted(generation, generation, generation, generation);
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    private static String parent(String name, String child, boolean later) {
        return """
                name: %s
                description: Prepare a report request and orchestrate its completion
                model: local
                planning_mode: true
                max_steps: %d
                prompt: %s. Determine the scope and supply explicit child arguments; complete all accepted work.
                output_from: {skill: %s}
                allowed_skills:
                  - {name: %s, required: true, max_tasks: 1}
                """.formatted(name, later ? 2 : 1, name.equals("middle") ? "MIDDLE_ORCHESTRATION" : "ROOT_ORCHESTRATION", child, child)
                + (later ? "  - {name: later, required: true, max_tasks: 1}\n" : "");
    }

    private static String expected(String kind) {
        return switch (kind) {
            case "model" -> MODEL_RESULT;
            case "java" -> JSON.writeValueAsString(JAVA_RESULT);
            case "rest" -> REST_RESULT;
            default -> " \nUNSTRUCTURED_REPORT \u5b8c\u6574\n ";
        };
    }

    private static void write(Path directory, String name, String yaml) throws Exception {
        Files.writeString(directory.resolve(name + ".yaml"), yaml);
    }

    private static Map<String, Object> task(String child, List<String> dependencies) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskId", child); task.put("title", child); task.put("status", "PENDING");
        task.put("capabilityName", child); task.put("intent", "Fulfill assigned responsibility");
        task.put("dependsOn", dependencies); task.put("expectedOutputs", List.of("complete result"));
        task.put("parallelGroup", null); task.put("note", "");
        return task;
    }

    private static MockResponse response(String content) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(Map.of(
                "id", "local-completion", "object", "chat.completion", "created", 1, "model", "deterministic",
                "choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", content), "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2))));
    }

    private static class Capture {
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<SkillExecutionView> observed = new AtomicReference<>();
        final AtomicInteger producerCalls = new AtomicInteger();
        final AtomicInteger laterCalls = new AtomicInteger();
        String result;
    }
}
