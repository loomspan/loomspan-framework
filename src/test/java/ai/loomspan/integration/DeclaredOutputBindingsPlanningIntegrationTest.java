package ai.loomspan.integration;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillMethod;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class DeclaredOutputBindingsPlanningIntegrationTest {
    @ParameterizedTest @ValueSource(strings={"allowed", "disallowed", "missing-probability", "case-source", "dynamic-allowed", "dynamic-disallowed", "dynamic-missing-probability", "singleton"})
    void nativePassthroughIsEnforcedOnlyByConsumingBindingContract(String scenario) throws Exception {
        boolean dynamic = scenario.startsWith("dynamic-");
        boolean probability = scenario.contains("probability") || scenario.equals("case-source");
        String pointer = probability ? "/category/probabilities/" + (scenario.equals("case-source") ? "KeepCase" : dynamic ? "RuntimeKey" : "A") : scenario.equals("singleton") ? "/category/type" : "/category/choice";
        String output = probability ? "type: number" : scenario.equals("singleton") ? "type: string, enum: [choice]" : "type: string, enum: [A, Z]";
        Files.writeString(directory.resolve("root.yaml"), """
                name: root
                description: Assemble one named native answer
                model: local
                planning_mode: true
                max_steps: 1
                input_schema:
                  type: object
                  properties: {options: {type: object, additionalProperties: true}}
                  required: [options]
                  additionalProperties: false
                allowed_skills:
                  - name: default-evaluation
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /options: {from: input, path: /options}
                output_schema:
                  type: object
                  properties:
                    selected: {%s}
                  required: [selected]
                  additionalProperties: false
                output_bindings:
                  /selected: {from: child_result, skill: default-evaluation, path: %s}
                """.formatted(output, pointer));
        Files.writeString(directory.resolve("evaluation.yaml"), """
                name: evaluation
                description: Native producer
                model: native
                input_schema: {type: object, properties: {options: {type: object, additionalProperties: true}}, required: [options]}
                typesafe:
                  state: ${/input}
                  questions:
                    category: {type: choice, criteria: %s}
                """.formatted(dynamic ? "'${/input/options}'" : scenario.equals("case-source") ? "{KeepCase: null}" : "{A: null, B: null}"));
        var calls = new AtomicInteger();
        try (var server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    calls.incrementAndGet();
                    if (request.getPath().equals("/api/v1/systemone")) {
                        String answer = scenario.endsWith("allowed") && !scenario.endsWith("disallowed") ? "{\"choice\":\"A\"}" : scenario.endsWith("disallowed") ? "{\"choice\":\"B\"}"
                                : scenario.equals("singleton") ? "{\"type\":\"choice\"}" : scenario.equals("case-source") ? "{\"probabilities\":{\"keepcase\":0.8}}" : "{\"probabilities\":{}}";
                        return new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"answers\":{\"category\":"+answer+"}}");
                    }
                    return response(JSON.writeValueAsString(Map.of("capabilityName", "default-root", "createdAt", "2026-10-10T00:00:00Z", "status", "VALID", "tasks", List.of(task("evaluation")))));
                }
            }); server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues(
                    "spring.main.web-application-type=none", "loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                    "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                    "loomspan.connections.local.api-key=fixture", "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=planner",
                    "loomspan.connections.native.driver=typesafe", "loomspan.connections.native.base-url=" + server.url("/api"),
                    "loomspan.connections.native.api-key=fixture", "loomspan.models.native.connection=native", "loomspan.models.native.provider-model=jev"
            ).run(context -> {
                assertThat(context).hasNotFailed(); var template = context.getBean(SkillTemplate.class);
                var input = Map.<String,Object>of("options", scenario.equals("dynamic-missing-probability") ? Map.of("RuntimeKey", "first") : Map.of("A", "first", "B", "second"));
                String registeredSchema = context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default").skill("default-evaluation").orElseThrow().outputSchema();
                if (dynamic) assertThat(JSON.readTree(registeredSchema).at("/properties/category/properties/choice").has("enum")).isFalse();
                if (scenario.endsWith("allowed") && !scenario.endsWith("disallowed") || scenario.equals("singleton"))
                    assertThat(JSON.readTree(template.invoke("default-root", input)).path("selected").asText()).isEqualTo(scenario.equals("singleton") ? "choice" : "A");
                else org.assertj.core.api.Assertions.assertThatThrownBy(() -> template.invoke("default-root", input))
                        .hasStackTraceContaining(probability ? "binding_source_unavailable" : "binding_output_validation");
                assertThat(context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default").skill("default-evaluation").orElseThrow().outputSchema()).isEqualTo(registeredSchema);
            });
            assertThat(calls).hasValue(2);
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    @TempDir Path directory;
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration static class App {}
    private static final String EXACT = "{\"assessment\":{\"number\":123456789012345678901234567890,\"text\":\"{looks:json}\"}}";
    public static class JavaProducer {
        @SkillMethod(name="first", description="Produce exact assessment")
        public Map<String,Object> first() {
            return Map.of("assessment",Map.of("number",new java.math.BigInteger("123456789012345678901234567890"),"text","{looks:json}"));
        }
    }

    @ParameterizedTest @CsvSource({"false,rest", "true,rest", "false,java", "true,java", "false,model", "true,model"})
    void assemblesUniqueProducersAfterAllWorkAndCorrectsOnlyModelFields(boolean mixed, String kind) throws Exception {
        String schema = """
                name: root
                description: Assemble direct results
                model: local
                planning_mode: true
                max_steps: %d
                prompt: Assemble the exact results and complete unrelated accepted work.
                input_schema:
                  type: object
                  properties:
                    caseId: {type: string}
                  required: [caseId]
                  additionalProperties: false
                allowed_skills:
                  - {name: first, max_tasks: 2}
                  - {name: second, max_tasks: 2}
                  - {name: default-later, required: true, max_tasks: 1}
                output_bindings:
                  /caseId: {from: input, path: /caseId}
                  /assessment: {from: child_result, skill: default-first, path: /assessment}
                  /rows: {from: child_result, skill: second, path: ''}
                output_schema_max_retries: 1
                output_schema:
                  type: object
                  properties:
                    caseId: {type: string}
                    assessment:
                      evidence: first
                      type: object
                      additionalProperties: true
                    rows:
                      type: array
                      items: {type: integer}
                """.formatted(mixed ? 4 : 3) + (mixed ? "    summary: {type: string}\n" : "")
                + "  required: [caseId, assessment, rows" + (mixed ? ", summary" : "") + "]\n  additionalProperties: false\n";
        Files.writeString(directory.resolve("root.yaml"), schema);
        for (String child : List.of("second", "later")) Files.writeString(directory.resolve(child+".yaml"),
                "name: " + child + "\ndescription: Direct source\nrest: true\n");
        if (kind.equals("rest")) Files.writeString(directory.resolve("first.yaml"),"name: first\ndescription: Direct source\nrest: true\n");
        if (kind.equals("model")) Files.writeString(directory.resolve("first.yaml"),"name: first\ndescription: Direct model source\nmodel: local\nprompt: EXACT_PRODUCER. Return the assessment object.\n");
        AtomicInteger plans = new AtomicInteger(), finals = new AtomicInteger(), later = new AtomicInteger(), children = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
                try {
                    String system = JSON.readTree(request.getBody().readUtf8()).path("messages").get(0).path("content").asText();
                    if(system.contains("EXACT_PRODUCER")) return response(EXACT);
                    if (system.contains("Create an ordered flight plan")) {
                        int attempt = plans.incrementAndGet();
                        assertThat(system).contains("exactly one direct task", "first", "second");
                        var tasks = attempt == 1 ? List.of(task("later")) : List.of(task("first"),task("second"),task("later"));
                        return response(JSON.writeValueAsString(Map.of("capabilityName","default-root","createdAt","2026-10-05T00:00:00Z","status","VALID","tasks",tasks)));
                    }
                    if (system.contains("coordinator-assigned task")) {
                        var match = Pattern.compile("Exact capability/tool: ([A-Za-z0-9_-]+)").matcher(system);
                        assertThat(match.find()).isTrue(); String child=match.group(1);
                        return response(JSON.writeValueAsString(Map.of("stepAction","CALL_TOOL","taskId",child.substring("default-".length()),"toolName",child,"toolArguments",Map.of())));
                    }
                    assertThat(mixed).isTrue(); assertThat(later.get()).isEqualTo(1);
                    assertThat(system).contains("summary");
                    int attempt=finals.incrementAndGet();
                    return response(JSON.writeValueAsString(Map.of("stepAction","FINAL_RESPONSE","finalResponse",
                            attempt == 1 ? Map.of("summary",9) : Map.of("summary","Finished"))));
                } catch(Throwable ex) { failure.compareAndSet(null,ex); return new MockResponse().setResponseCode(500); }
            }});
            server.start();
            ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(App.class).withBean(RestSkillHandler.class, () -> invocation -> {
                children.incrementAndGet();
                return switch(invocation.skillName()) {
                    case "default-first" -> EXACT;
                    case "default-second" -> "[3,1,2]";
                    default -> { later.incrementAndGet(); yield "unrelated completed"; }
                };
            }).withPropertyValues("spring.main.web-application-type=none","loomspan.skills.locations="+directory.toUri()+"*.yaml",
                    "loomspan.connections.local.driver=openai","loomspan.connections.local.base-url="+server.url("/v1"),
                    "loomspan.connections.local.api-key=local-test","loomspan.connections.local.provider-retry.enabled=false",
                    "loomspan.models.local.connection=local","loomspan.models.local.provider-model=deterministic","loomspan.session.mission-timeout=30s");
            if(kind.equals("java")) runner = runner.withBean(JavaProducer.class,JavaProducer::new);
            runner.run(context -> {
                        assertThat(context).hasNotFailed();
                        var result=JSON.readTree(context.getBean(SkillTemplate.class).invoke("default-root",Map.of("caseId","C-1")));
                        assertThat(result.path("caseId").asText()).isEqualTo("C-1");
                        assertThat(result.path("assessment").path("number").asText()).isEqualTo("123456789012345678901234567890");
                        assertThat(result.path("assessment").path("text").asText()).isEqualTo("{looks:json}");
                        assertThat(result.path("rows").toString()).isEqualTo("[3,1,2]");
                        if(mixed) assertThat(result.path("summary").asText()).isEqualTo("Finished");
                    });
        }
        assertThat(failure.get()).isNull(); assertThat(plans.get()).isEqualTo(2);
        assertThat(finals.get()).isEqualTo(mixed ? 2 : 0); assertThat(children.get()).isEqualTo(kind.equals("rest") ? 3 : 2);
    }
    private static Map<String,Object> task(String name) {
        return Map.of("taskId",name,"title",name,"status","PENDING","capabilityName","default-" + name,"intent","work","dependsOn",List.of(),"expectedOutputs",List.of("result"));
    }
    private static MockResponse response(String content) {
        return new MockResponse().setHeader("Content-Type","application/json").setBody(JSON.writeValueAsString(Map.of(
                "id","test","object","chat.completion","created",1,"model","deterministic",
                "choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop")),
                "usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
    }
}
