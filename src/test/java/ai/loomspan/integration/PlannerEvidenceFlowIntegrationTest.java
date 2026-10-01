package ai.loomspan.integration;

import ai.loomspan.api.RestSkillHandler;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Public facade + real OpenAI HTTP protocol. No framework bean replacements or trace-based prompt assertions. */
class PlannerEvidenceFlowIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FACT = Pattern.compile("FACT_(?:[ABCN]|B[0-9])_(?:EARLY|MIDDLE|TAIL)");
    @TempDir Path directory;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    public static class JavaEvidence {
        private final String value;
        JavaEvidence(String value) { this.value = value; }
        @SkillMethod(description = "Return independent source A")
        public Map<String, Object> sourceA() { return Map.of("text", value); }
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void characterizesActualWireEvidence(boolean concurrent, boolean longResults) throws Exception {
        Capture capture = run(concurrent, longResults);
        String assigned = capture.requestContaining("Exact capability/tool: assess");
        String child = capture.requestContaining("ASSESS_ONLY_RECEIVED_INPUT");
        String terminal = capture.requestContaining("All required plan tasks are already COMPLETE");
        assertThat(assigned).contains("FACT_A_EARLY", "FACT_B_EARLY", "FACT_C_EARLY");
        assertThat(child).contains("FACT_A_EARLY", "FACT_B_EARLY", "FACT_C_EARLY");
        assertThat(capture.requestWithToolResult()).contains("FACT_N_TAIL");
        assertThat(capture.nestedCompletion).contains("FACT_N_TAIL");
        assertThat(capture.observed.get()).isNotNull();
        assertThat(JSON.writeValueAsString(capture.observed.get().events()))
                .contains("FACT_A_TAIL", "FACT_B_TAIL", "FACT_C_TAIL", "FACT_N_TAIL");
        assertThat(child).contains("$ref", "sourceA.result");
        for (String source : List.of("A", "B", "C")) {
            assertThat(assigned).contains("FACT_" + source + "_TAIL");
            assertThat(child).contains("FACT_" + source + "_TAIL");
            assertThat(terminal).contains("FACT_" + source + "_TAIL");
        }
        assertThat(terminal).contains("FACT_N_TAIL");
        assertThat(capture.result).contains("FACT_N_TAIL");

    }

    /** Completeness is asserted against actual outbound requests. */
    @Test
    void completeEvidenceMustReachDependentAndFinalRequests() throws Exception {
        Capture capture = run(true, true);
        org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
        for (String source : List.of("A", "B", "C")) {
            softly.assertThat(capture.requestContaining("Exact capability/tool: assess"))
                    .as("assigned dependent request: " + source).contains("FACT_" + source + "_TAIL");
            softly.assertThat(capture.requestContaining("ASSESS_ONLY_RECEIVED_INPUT"))
                    .as("nested assessment input: " + source).contains("FACT_" + source + "_TAIL");
        }
        softly.assertThat(capture.requestContaining("All required plan tasks are already COMPLETE"))
                .as("native final synthesis").contains("FACT_N_TAIL");
        softly.assertAll();
    }

    private Capture run(boolean concurrent, boolean longResults) throws Exception {
        return run(concurrent, longResults, false, false);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false"})
    void earlierResultsSurviveSevenCompletionsAndRepeatedSkillCalls(boolean concurrent, boolean reversed) throws Exception {
        Capture capture = run(concurrent, true, true, reversed);
        String assigned = capture.requestContaining("Exact capability/tool: assess");
        JsonNode records = evidenceRecords(assigned);
        assertThat(records.size()).isEqualTo(7);
        List<String> ids = List.of("sourceA", "B0", "B1", "B2", "B3", "B4", "sourceC");
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            String source = id.equals("sourceA") ? "A" : id.equals("sourceC") ? "C" : id;
            assertThat(records.get(index).path("taskId").asText()).isEqualTo(id);
            assertThat(records.get(index).path("skillName").asText()).isEqualTo(id.startsWith("B") ? "sourceB" : id);
            assertThat(records.get(index).path("result").asText())
                    .isEqualTo(JSON.writeValueAsString(Map.of("text", evidence(source, true))));
            assertThat(capture.requestContaining("ASSESS_ONLY_RECEIVED_INPUT")).contains("FACT_" + source + "_TAIL");
        }
        for (String request : capture.requests) {
            String system = JSON.readTree(request).path("messages").get(0).path("content").asText();
            if (system.contains("coordinator-assigned task") && !system.contains("Exact capability/tool: assess")
                    && !system.contains("Exact capability/tool: nested") && !system.contains("Exact capability/tool: nestedSource"))
                assertThat(system).doesNotContain("COMPLETED TASK EVIDENCE", "FACT_A_TAIL", "FACT_B0_TAIL", "FACT_C_TAIL");
        }
        String terminal = capture.requests.stream().filter(request -> request.contains("All required plan tasks are already COMPLETE"))
                .filter(request -> !request.contains("NESTED_PLANNER_PUBLIC_RETURN")).findFirst().orElseThrow();
        JsonNode finalRecords = evidenceRecords(terminal);
        for (int index = 0; index < records.size(); index++) assertThat(finalRecords.get(index)).isEqualTo(records.get(index));
        String childResult = finalRecords.get(finalRecords.size() - 1).path("result").asText();
        assertThat(childResult).isEqualTo(capture.nestedCompletion);
        JsonNode childReturn = JSON.readTree(childResult);
        assertThat(capture.requests).allSatisfy(request -> assertThat(request).doesNotContain("UNRELATED_MISSION_SENTINEL"));
        assertThat(capture.requests.stream().filter(request -> request.contains("All required plan tasks are already COMPLETE"))
                .filter(request -> request.contains("NESTED_PLANNER_PUBLIC_RETURN")).findFirst().orElseThrow())
                .contains("CHILD_PRIVATE_SENTINEL", "FACT_N_TAIL");
        assertThat(childReturn.path("quote").asText()).isEqualTo(evidence("N", true));
        assertThat(childReturn.path("citation").asText()).isEqualTo("citation:FACT_N_TAIL");
        assertThat(terminal).doesNotContain("CHILD_PRIVATE_SENTINEL", "UNRELATED_MISSION_SENTINEL");
        assertThat(capture.result).contains("FACT_N_TAIL");
        if (reversed) assertThat(capture.leafReturns).containsExactly("sourceC", "B4", "B3", "B2", "B1", "B0", "sourceA");
    }

    private static JsonNode evidenceRecords(String request) {
        String system = JSON.readTree(request).path("messages").get(0).path("content").asText();
        String block = system.substring(system.indexOf("--- COMPLETED TASK EVIDENCE ---"));
        return JSON.readTree(block.substring(block.indexOf("\n[") + 1).split("\n\n", 2)[0]);
    }

    private Capture run(boolean concurrent, boolean longResults, boolean expanded, boolean reversed) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("skills"));
        Files.writeString(skills.resolve("root.yaml"), """
                name: root
                description: Investigate evidence flow
                model: local
                planning_mode: true
                concurrency: %s
                max_steps: 12
                prompt: Gather three independent sources, assess all their evidence, then call nested and preserve its complete result.
                output_schema:
                  type: object
                  properties:
                    facts: {type: string}
                  required: [facts]
                  additionalProperties: false
                allowed_skills:
                  - {name: sourceA, required: true, max_tasks: 1}
                  - {name: sourceB, required: true, max_tasks: 5}
                  - {name: sourceC, required: true, max_tasks: 1}
                  - {name: assess, required: true, max_tasks: 1}
                  - {name: nested, required: true, max_tasks: 1}
                """.formatted(concurrent));
        Files.writeString(skills.resolve("assess.yaml"), """
                name: assess
                description: Assess supplied evidence
                model: local
                prompt: ASSESS_ONLY_RECEIVED_INPUT. Return only the facts actually received.
                input_schema:
                  type: object
                  properties:
                    upstream: {type: string}
                    upstreamReference: {type: object, additionalProperties: true}
                  required: [upstream]
                """);
        Files.writeString(skills.resolve("nested.yaml"), """
                name: nested
                description: Complete a nested mission
                model: local
                prompt: NESTED_COPY_SOURCE. Call nestedSource and return its exact complete result.
                allowed_skills:
                  - {name: nestedSource}
                """);
        if (expanded) Files.writeString(skills.resolve("nested.yaml"), """
                name: nested
                description: A nested planner returning public quote and citation
                model: local
                planning_mode: true
                max_steps: 3
                prompt: NESTED_PLANNER_PUBLIC_RETURN. Gather nestedSource and return its public quote and citation, omitting private data.
                output_schema:
                  type: object
                  properties:
                    quote: {type: string}
                    citation: {type: string}
                  required: [quote, citation]
                  additionalProperties: false
                allowed_skills:
                  - {name: nestedSource, required: true}
                """);
        for (String name : List.of("sourceB", "sourceC", "nestedSource")) {
            Files.writeString(skills.resolve(name + ".yaml"), """
                    name: %s
                    description: Return independent evidence
                    rest: true
                    """.formatted(name));
        }
        Capture capture = new Capture();
        List<String> returnOrder = List.of("sourceC", "B4", "B3", "B2", "B1", "B0", "sourceA");
        var admittedLeaves = new java.util.concurrent.CountDownLatch(7);
        Map<String, java.util.concurrent.CountDownLatch> leafGates = new LinkedHashMap<>();
        for (String id : returnOrder) leafGates.put(id, new java.util.concurrent.CountDownLatch(1));
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        String body = request.getBody().readUtf8();
                        capture.requests.add(body);
                        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
                        JsonNode wire = JSON.readTree(body);
                        String text = wire.path("messages").toString();
                        Object content;
                        if (text.contains("Create an ordered flight plan")) {
                            List<Object> tasks = new ArrayList<>();
                            boolean nestedPlan = text.contains("NESTED_PLANNER_PUBLIC_RETURN");
                            List<String> ids = expanded ? List.of("sourceA", "B0", "B1", "B2", "B3", "B4", "sourceC")
                                    : List.of("sourceA", "sourceB", "sourceC");
                            if (nestedPlan) tasks.add(task("nestedSource", List.of(), null));
                            else {
                                for (String id : ids) {
                                    Map<String, Object> task = task(id, List.of(), "sources");
                                    if (id.startsWith("B")) task.put("capabilityName", "sourceB");
                                    tasks.add(task);
                                }
                                tasks.add(task("assess", ids, null));
                                tasks.add(task("nested", List.of("assess"), null));
                            }
                            content = Map.of("capabilityName", nestedPlan ? "nested" : "root", "createdAt", "2026-10-01T00:00:00Z", "status", "VALID", "tasks", tasks);
                        } else if (text.contains("coordinator-assigned task")) {
                            String system = wire.path("messages").get(0).path("content").asText();
                            var matcher = Pattern.compile("Exact capability/tool: (\\w+)").matcher(system);
                            assertThat(matcher.find()).isTrue();
                            String tool = matcher.group(1);
                            var idMatcher = Pattern.compile("--- ASSIGNED TASK ---\\nID: (\\w+)").matcher(system);
                            assertThat(idMatcher.find()).isTrue();
                            String id = idMatcher.group(1);
                            content = Map.of("stepAction", "CALL_TOOL", "taskId", id, "toolName", tool,
                                    "toolArguments", tool.equals("assess") ? Map.of("upstream", facts(text),
                                            "upstreamReference", Map.of("$ref", "sourceA.result")) : id.startsWith("B") ? Map.of("source", id) : Map.of());
                        } else if (text.contains("All required plan tasks are already COMPLETE")) {
                            if (expanded && text.contains("NESTED_PLANNER_PUBLIC_RETURN")) {
                                JsonNode returned = JSON.readTree(evidenceRecords(body).get(0).path("result").asText());
                                capture.nestedCompletion = JSON.writeValueAsString(Map.of("quote", returned.path("quote").asText(),
                                        "citation", returned.path("citation").asText()));
                                content = Map.of("stepAction", "FINAL_RESPONSE", "finalResponse", JSON.readTree(capture.nestedCompletion));
                            } else content = Map.of("stepAction", "FINAL_RESPONSE", "finalResponse", Map.of("facts", facts(text)));
                        } else if (text.contains("ASSESS_ONLY_RECEIVED_INPUT")) {
                            content = Map.of("facts", facts(text));
                        } else if (text.contains("NESTED_COPY_SOURCE")) {
                            JsonNode toolResult = null;
                            for (JsonNode message : wire.path("messages"))
                                if (message.path("role").asText().equals("tool")) toolResult = message.path("content");
                            if (toolResult == null) return response(Map.of("role", "assistant", "content", "", "tool_calls",
                                    List.of(Map.of("id", "call-nested", "type", "function", "function",
                                            Map.of("name", "nestedSource", "arguments", "{}")))), "tool_calls");
                            capture.nestedCompletion = toolResult.asText();
                            return response(Map.of("role", "assistant", "content", capture.nestedCompletion), "stop");
                        } else throw new AssertionError("Unexpected request: " + body);
                        return response(Map.of("role", "assistant", "content", JSON.writeValueAsString(content)), "stop");
                    } catch (Throwable failure) {
                        capture.failure.compareAndSet(null, failure);
                        return new MockResponse().setResponseCode(500).setBody("Unexpected test request");
                    }
                }
            });
            server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withBean(JavaEvidence.class, () -> new JavaEvidence(evidence("A", longResults)) {
                        @Override public Map<String, Object> sourceA() {
                            if (expanded && reversed) {
                                admittedLeaves.countDown(); await(admittedLeaves); await(leafGates.get("B0"));
                                capture.leafReturns.add("sourceA"); leafGates.get("sourceA").countDown();
                            }
                            return super.sourceA();
                        }
                    })
                    .withBean(RestSkillHandler.class, () -> invocation -> {
                        if (Boolean.TRUE.equals(invocation.input().get("unrelated")))
                            return "UNRELATED_MISSION_SENTINEL";
                        String id = invocation.skillName().equals("sourceB") ? String.valueOf(invocation.input().getOrDefault("source", "sourceB")) : invocation.skillName();
                        String source = id.equals("sourceB") ? "B" : id.equals("sourceC") ? "C" : id.equals("nestedSource") ? "N" : id;
                        if (expanded && reversed && !id.equals("nestedSource")) {
                            admittedLeaves.countDown();
                            await(admittedLeaves);
                            int index = returnOrder.indexOf(id);
                            if (index > 0) await(leafGates.get(returnOrder.get(index - 1)));
                            capture.leafReturns.add(id);
                            leafGates.get(id).countDown();
                        }
                        if (expanded && id.equals("nestedSource")) return JSON.writeValueAsString(Map.of(
                                "quote", evidence("N", true), "citation", "citation:FACT_N_TAIL", "private", "CHILD_PRIVATE_SENTINEL"));
                        return JSON.writeValueAsString(Map.of("text", evidence(source, longResults)));
                    })
                    .withPropertyValues("spring.main.web-application-type=none",
                            "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                            "loomspan.connections.local.driver=openai",
                            "loomspan.connections.local.base-url=" + server.url("/v1"),
                            "loomspan.connections.local.api-key=local-dummy-not-a-credential",
                            "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local",
                            "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.mission-timeout=30s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillTemplate facade = context.getBean(SkillTemplate.class);
                        // Public direct invocation returns full text; it is not a planner evidence reader.
                        if (!expanded) {
                            assertThat(facade.invoke("sourceA", Map.of())).contains("FACT_A_TAIL");
                            assertThat(facade.invoke("sourceB", Map.of())).contains("FACT_B_TAIL");
                        } else assertThat(facade.invoke("sourceB", Map.of("unrelated", true))).isEqualTo("UNRELATED_MISSION_SENTINEL");
                        capture.result = facade.invoke("root", Map.of("caseId", "local-case"), capture.observed::set);
                        if (expanded) assertThat(facade.invoke("sourceB", Map.of("unrelated", true)))
                                .isEqualTo("UNRELATED_MISSION_SENTINEL");
                    });
        } finally {
            Path output = Path.of(System.getProperty("evidence.output", "target/evidence-flow"));
            Files.createDirectories(output);
            String label = (expanded ? "seven-nested-" : "") + (reversed ? "reversed-" : "") + (concurrent ? "parallel" : "serial") + (longResults ? "-long" : "-short");
            Files.writeString(output.resolve(label + "-requests.ndjson"), String.join("\n", capture.requests) + "\n");
            Files.writeString(output.resolve(label + "-result.json"), JSON.writeValueAsString(Map.of(
                    "result", String.valueOf(capture.result), "nestedCompletion", String.valueOf(capture.nestedCompletion))));
            Map<String, String> sources = new LinkedHashMap<>();
            for (String source : List.of("A", "B", "C", "N")) sources.put(source, evidence(source, longResults));
            Files.writeString(output.resolve(label + "-sources.json"), JSON.writeValueAsString(sources));
        }
        assertThat(capture.failure.get()).isNull();
        assertThat(capture.requests).hasSize(expanded ? 15 : 10);
        return capture;
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try { assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    private static String evidence(String source, boolean longResults) {
        return "FACT_" + source + "_EARLY " + (longResults ? "x".repeat(200) : "")
                + " FACT_" + source + "_MIDDLE " + (longResults ? "y".repeat(1100) : "")
                + " FACT_" + source + "_TAIL";
    }

    private static String facts(String received) {
        return FACT.matcher(received).results().map(match -> match.group()).distinct().sorted()
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    private static Map<String, Object> task(String name, List<String> dependencies, String group) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskId", name); task.put("title", name); task.put("status", "PENDING");
        task.put("capabilityName", name); task.put("intent", "Use complete evidence");
        task.put("dependsOn", dependencies); task.put("expectedOutputs", List.of("evidence"));
        task.put("parallelGroup", group); task.put("note", "");
        return task;
    }

    private static MockResponse response(Object message, String reason) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(Map.of(
                "id", "local-completion", "object", "chat.completion", "created", 1, "model", "deterministic",
                "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", reason)),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2))));
    }

    private static class Capture {
        final List<String> leafReturns = Collections.synchronizedList(new ArrayList<>());
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<SkillExecutionView> observed = new AtomicReference<>();
        String result;
        String nestedCompletion;
        String requestContaining(String value) {
            return requests.stream().filter(request -> request.contains(value)).findFirst().orElseThrow();
        }
        String requestWithToolResult() {
            return requests.stream().filter(request -> JSON.readTree(request).path("messages").toString()
                    .contains("\"role\":\"tool\"")).findFirst().orElseThrow();
        }
    }
}
