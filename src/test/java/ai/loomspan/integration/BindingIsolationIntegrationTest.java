package ai.loomspan.integration;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillTemplate;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BindingIsolationIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration static class App {}

    @Test void concurrentParentsSelectOnlyTheirOwnAcceptedResults() throws Exception { exercise(false, false); }
    @Test void nestedConcurrentParentsUseTheirOwnValidatedInput() throws Exception { exercise(false, true); }
    @Test void blockedParentKeepsCapturedBindingsAfterReload() throws Exception { exercise(true, false); }

    @Test void invalidAssembledNumericStringNeverDispatchesBoundChild() throws Exception { dispatchGuard(false); }
    @Test void toolInvocationBudgetStillStopsBoundConsumerBeforeDispatch() throws Exception { dispatchGuard(true); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void groupedBoundProducersJoinBeforeDeliveringExactInputs(boolean concurrent) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("grouped"));
        String root = parent("root", false).replace("max_steps: 2", "max_steps: 3\nconcurrency: " + concurrent)
                .replace("  - name: consumer", "  - name: secondProducer\n"
                        + "    required: true\n    max_tasks: 1\n    input_bindings:\n"
                        + "      /marker: {from: input, path: /marker}\n  - name: consumer")
                .replace("      /evidence: {from: child_result, skill: producer, path: /data}",
                        "      /evidence: {from: child_result, skill: producer, path: /data}\n"
                                + "      /secondEvidence: {from: child_result, skill: secondProducer, path: /data}");
        Files.writeString(skills.resolve("root.yaml"), root);
        Files.writeString(skills.resolve("producer.yaml"), leaf("producer"));
        Files.writeString(skills.resolve("secondProducer.yaml"), leaf("secondProducer"));
        Files.writeString(skills.resolve("consumer.yaml"), leaf("consumer")
                .replace("  required: [marker, evidence]", "    secondEvidence: {type: object, additionalProperties: true}\n  required: [marker, evidence, secondEvidence]"));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        var consumerCalls = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        JsonNode wire = JSON.readTree(request.getBody().readUtf8());
                        String system = wire.path("messages").get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan")) {
                            var first = task("producer", List.of()); first.put("parallelGroup", "sources");
                            var second = task("secondProducer", List.of()); second.put("parallelGroup", "sources");
                            return response(JSON.writeValueAsString(Map.of("capabilityName", "root", "createdAt", "2026-10-04T00:00:00Z",
                                    "status", "VALID", "tasks", List.of(first, second, task("consumer", List.of("producer", "secondProducer"))))));
                        }
                        var matcher = Pattern.compile("Exact capability/tool: (\\w+)").matcher(system);
                        assertThat(matcher.find()).isTrue();
                        String skill = matcher.group(1);
                        return response(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL", "taskId", skill,
                                "toolName", skill, "toolArguments", Map.of())));
                    } catch (Throwable ex) { failure.set(ex); return new MockResponse().setResponseCode(500); }
                }
            }); server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withBean(RestSkillHandler.class, () -> invocation -> {
                        assertThat(invocation.input()).containsEntry("marker", "original");
                        if (invocation.skillName().equals("producer")) {
                            firstEntered.countDown(); await(releaseFirst);
                            firstFinished.countDown();
                            return "{\"data\":{\"amount\":9007199254740993,\"source\":\"first\"}}";
                        }
                        if (invocation.skillName().equals("secondProducer")) {
                            if (!concurrent) assertThat(firstFinished.getCount()).isZero();
                            secondEntered.countDown(); await(releaseSecond);
                            return "{\"data\":{\"rows\":[\"original\"],\"source\":\"second\"}}";
                        }
                        consumerCalls.incrementAndGet();
                        assertThat(firstFinished.getCount()).isZero();
                        assertThat(releaseSecond.getCount()).isZero();
                        assertThat(invocation.input().get("evidence")).isEqualTo(Map.of("amount", new java.math.BigInteger("9007199254740993"), "source", "first"));
                        assertThat(invocation.input().get("secondEvidence")).isEqualTo(Map.of("rows", List.of("original"), "source", "second"));
                        return "COMPLETE";
                    }).withPropertyValues("spring.main.web-application-type=none", "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                            "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                            "loomspan.connections.local.api-key=local-test", "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.mission-timeout=30s").run(context -> {
                        assertThat(context).hasNotFailed();
                        try (var worker = Executors.newSingleThreadExecutor()) {
                            var result = worker.submit(() -> context.getBean(SkillTemplate.class).invoke("root", Map.of("marker", "original")));
                            try {
                                await(firstEntered);
                                if (concurrent) await(secondEntered); // Both handlers are active before either may return.
                                else assertThat(secondEntered.getCount()).isEqualTo(1);
                                assertThat(consumerCalls).hasValue(0);
                                releaseFirst.countDown();
                                await(firstFinished); await(secondEntered);
                                assertThat(consumerCalls).hasValue(0); // One accepted producer cannot admit the consumer.
                            } finally { releaseFirst.countDown(); releaseSecond.countDown(); }
                            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("COMPLETE");
                        } catch (Exception ex) { throw new AssertionError(ex); }
                    });
        }
        assertThat(failure.get()).isNull();
        assertThat(consumerCalls).hasValue(1);
    }

    private void dispatchGuard(boolean quota) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("guard-skills"));
        Files.writeString(skills.resolve("root.yaml"), parent("root", false));
        Files.writeString(skills.resolve("producer.yaml"), quota ? leaf("producer") : leaf("producer").replace("marker: {type: string}", "marker: {type: integer}"));
        Files.writeString(skills.resolve("consumer.yaml"), leaf("consumer"));
        var producerCalls = new java.util.concurrent.atomic.AtomicInteger();
        var consumerCalls = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        JsonNode wire = JSON.readTree(request.getBody().readUtf8());
                        String system = wire.path("messages").get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan"))
                            return response(JSON.writeValueAsString(Map.of("capabilityName", "root", "createdAt", "2026-10-04T00:00:00Z",
                                    "status", "VALID", "tasks", List.of(task("producer", List.of()), task("consumer", List.of("producer"))))));
                        var matcher = Pattern.compile("Exact capability/tool: (\\w+)").matcher(system);
                        assertThat(matcher.find()).isTrue();
                        String skill = matcher.group(1);
                        return response(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL", "taskId", skill,
                                "toolName", skill, "toolArguments", Map.of())));
                    } catch (Throwable ex) { failure.set(ex); return new MockResponse().setResponseCode(500); }
                }
            }); server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withBean(RestSkillHandler.class, () -> invocation -> {
                        if (invocation.skillName().equals("producer")) {
                            producerCalls.incrementAndGet();
                            return JSON.writeValueAsString(Map.of("data", Map.of("marker", invocation.input().get("marker"))));
                        }
                        consumerCalls.incrementAndGet(); return "must not dispatch";
                    }).withPropertyValues("spring.main.web-application-type=none", "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                            "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                            "loomspan.connections.local.api-key=local-test", "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.quotas.max-tool-invocations=" + (quota ? 1 : 100), "loomspan.session.mission-timeout=30s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("root", Map.of("marker", "123")))
                                .hasStackTraceContaining(quota ? "MAX_TOOL_INVOCATIONS" : "binding_assembled_input_invalid");
                    });
        }
        assertThat(failure.get()).isNull();
        assertThat(producerCalls).hasValue(quota ? 1 : 0);
        assertThat(consumerCalls).hasValue(0);
    }

    private void exercise(boolean reload, boolean nested) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("skills"));
        Files.writeString(skills.resolve("root.yaml"), nested ? outer() : parent("root", false));
        if (nested) Files.writeString(skills.resolve("middle.yaml"), parent("middle", false));
        Files.writeString(skills.resolve("producer.yaml"), leaf("producer"));
        Files.writeString(skills.resolve("consumer.yaml"), leaf("consumer"));
        CountDownLatch entered = new CountDownLatch(reload ? 1 : 2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Map<String,Object>> delivered = Collections.synchronizedList(new ArrayList<>());
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        JsonNode wire = JSON.readTree(request.getBody().readUtf8());
                        String system = wire.path("messages").get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan")) {
                            boolean isOuter = system.contains("OUTER_ONLY");
                            String owner = isOuter || !nested ? "root" : "middle";
                            List<Object> tasks = isOuter ? List.of(task("middle", List.of()))
                                    : List.of(task("producer", List.of()), task("consumer", List.of("producer")));
                            return response(JSON.writeValueAsString(Map.of("capabilityName", owner,
                                    "createdAt", "2026-10-04T00:00:00Z", "status", "VALID", "tasks", tasks)));
                        }
                        assertThat(system).contains("coordinator-assigned task", "Supply only unbound arguments");
                        var matcher = Pattern.compile("Exact capability/tool: (\\w+)").matcher(system);
                        assertThat(matcher.find()).isTrue();
                        String skill = matcher.group(1);
                        return response(JSON.writeValueAsString(Map.of("stepAction", "CALL_TOOL", "taskId", skill,
                                "toolName", skill, "toolArguments", Map.of())));
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                        return new MockResponse().setResponseCode(500).setBody("Unexpected isolation request");
                    }
                }
            });
            server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withBean(RestSkillHandler.class, () -> invocation -> {
                        String marker = (String) invocation.input().get("marker");
                        if (invocation.skillName().equals("producer")) {
                            if (!reload || marker.equals("held")) {
                                entered.countDown();
                                await(entered);
                                if (reload) await(release);
                            }
                            return JSON.writeValueAsString(Map.of("data", Map.of("marker", marker)));
                        }
                        assertThat(invocation.skillName()).isEqualTo("consumer");
                        delivered.add(invocation.input());
                        assertThat(invocation.input().get("evidence")).isEqualTo(Map.of("marker", marker));
                        return JSON.writeValueAsString(Map.of("marker", marker));
                    }).withPropertyValues("spring.main.web-application-type=none",
                            "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                            "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                            "loomspan.connections.local.api-key=local-test", "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.mission-timeout=30s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillTemplate template = context.getBean(SkillTemplate.class);
                        try (var workers = Executors.newFixedThreadPool(2)) {
                            if (reload) {
                                var reloader = context.getBean(SkillReloader.class);
                                var captured = reloader.snapshot();
                                var held = workers.submit(() -> template.invoke("root", Map.of("marker", "held", "freshMarker", "incorrect")));
                                try {
                                    await(entered);
                                    try (var update = reloader.prepare(List.of(new SkillDocument("new-root", parent("root", true)),
                                            new SkillDocument("producer", leaf("producer")), new SkillDocument("consumer", leaf("consumer"))))) {
                                        reloader.publish(update);
                                    }
                                    assertThat(reloader.snapshot().generationId()).isNotEqualTo(captured.generationId());
                                    assertThat(JSON.readTree(template.invoke("root", Map.of("marker", "incorrect", "freshMarker", "fresh")))
                                            .path("marker").asText()).isEqualTo("fresh");
                                } finally { release.countDown(); }
                                assertThat(JSON.readTree(held.get(10, TimeUnit.SECONDS)).path("marker").asText()).isEqualTo("held");
                            } else {
                                Map<String,Object> first = nested ? Map.of("outerMarker", "one", "marker", "foreign-root") : Map.of("marker", "one");
                                Map<String,Object> second = nested ? Map.of("outerMarker", "two", "marker", "foreign-root") : Map.of("marker", "two");
                                var one = workers.submit(() -> template.invoke("root", first));
                                var two = workers.submit(() -> template.invoke("root", second));
                                assertThat(JSON.readTree(one.get(10, TimeUnit.SECONDS)).path("marker").asText()).isEqualTo("one");
                                assertThat(JSON.readTree(two.get(10, TimeUnit.SECONDS)).path("marker").asText()).isEqualTo("two");
                            }
                        } catch (Exception ex) { throw new AssertionError(ex); }
                    });
        }
        assertThat(failure.get()).isNull();
        assertThat(delivered).hasSize(2);
        assertThat(delivered).extracting(input -> input.get("marker")).containsExactlyInAnyOrder(reload ? "held" : "one", reload ? "fresh" : "two");
    }

    private static String parent(String name, boolean fresh) {
        String source = fresh ? "/freshMarker" : "/marker";
        return """
                name: %s
                description: Bind invocation-local accepted results
                model: local
                planning_mode: true
                max_steps: 2
                prompt: INNER_ONLY. Compose exact source evidence.
                input_schema:
                  type: object
                  properties:
                    marker: {type: string}
                    freshMarker: {type: string}
                  required: [marker]
                  additionalProperties: false
                allowed_skills:
                  - name: producer
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /marker: {from: input, path: %s}
                  - name: consumer
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /marker: {from: input, path: %s}
                      /evidence: {from: child_result, skill: producer, path: /data}
                output_from: {skill: consumer}
                """.formatted(name, source, source);
    }

    private static String outer() { return """
            name: root
            description: Bound nested invocation
            model: local
            planning_mode: true
            max_steps: 1
            prompt: OUTER_ONLY. Delegate nested work.
            input_schema:
              type: object
              properties:
                outerMarker: {type: string}
                marker: {type: string}
              required: [outerMarker, marker]
              additionalProperties: false
            allowed_skills:
              - name: middle
                required: true
                max_tasks: 1
                input_bindings:
                  /marker: {from: input, path: /outerMarker}
            output_from: {skill: middle}
            """; }

    private static String leaf(String name) { return """
            name: %s
            description: Exact invocation-local evidence
            rest: true
            input_schema:
              type: object
              properties:
                marker: {type: string}
            """.formatted(name) + (name.equals("consumer") ? "    evidence: {type: object, additionalProperties: true}\n  required: [marker, evidence]\n" : "  required: [marker]\n")
                + "  additionalProperties: false\n"; }

    private static Map<String,Object> task(String child, List<String> dependencies) {
        Map<String,Object> task = new LinkedHashMap<>();
        task.put("taskId", child); task.put("title", child); task.put("status", "PENDING"); task.put("capabilityName", child);
        task.put("intent", "Fulfill responsibility"); task.put("dependsOn", dependencies); task.put("expectedOutputs", List.of("complete result"));
        task.put("parallelGroup", null); task.put("note", ""); return task;
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }
    private static MockResponse response(String content) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(Map.of("id", "local",
                "object", "chat.completion", "created", 1, "model", "deterministic", "choices", List.of(Map.of("index", 0,
                "message", Map.of("role", "assistant", "content", content), "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2))));
    }
}
