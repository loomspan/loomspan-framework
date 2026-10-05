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
                  - {name: later, required: true, max_tasks: 1}
                output_bindings:
                  /caseId: {from: input, path: /caseId}
                  /assessment: {from: child_result, skill: first, path: /assessment}
                  /rows: {from: child_result, skill: second, path: ''}
                output_schema_max_retries: 1
                output_schema:
                  type: object
                  properties:
                    caseId: {type: string}
                    assessment:
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
                        return response(JSON.writeValueAsString(Map.of("capabilityName","root","createdAt","2026-10-05T00:00:00Z","status","VALID","tasks",tasks)));
                    }
                    if (system.contains("coordinator-assigned task")) {
                        var match = Pattern.compile("Exact capability/tool: (\\w+)").matcher(system);
                        assertThat(match.find()).isTrue(); String child=match.group(1);
                        return response(JSON.writeValueAsString(Map.of("stepAction","CALL_TOOL","taskId",child,"toolName",child,"toolArguments",Map.of())));
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
                    case "first" -> EXACT;
                    case "second" -> "[3,1,2]";
                    default -> { later.incrementAndGet(); yield "unrelated completed"; }
                };
            }).withPropertyValues("spring.main.web-application-type=none","loomspan.skills.locations="+directory.toUri()+"*.yaml",
                    "loomspan.connections.local.driver=openai","loomspan.connections.local.base-url="+server.url("/v1"),
                    "loomspan.connections.local.api-key=local-test","loomspan.connections.local.provider-retry.enabled=false",
                    "loomspan.models.local.connection=local","loomspan.models.local.provider-model=deterministic","loomspan.session.mission-timeout=30s");
            if(kind.equals("java")) runner = runner.withBean(JavaProducer.class,JavaProducer::new);
            runner.run(context -> {
                        assertThat(context).hasNotFailed();
                        var result=JSON.readTree(context.getBean(SkillTemplate.class).invoke("root",Map.of("caseId","C-1")));
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
        return Map.of("taskId",name,"title",name,"status","PENDING","capabilityName",name,"intent","work","dependsOn",List.of(),"expectedOutputs",List.of("result"));
    }
    private static MockResponse response(String content) {
        return new MockResponse().setHeader("Content-Type","application/json").setBody(JSON.writeValueAsString(Map.of(
                "id","test","object","chat.completion","created",1,"model","deterministic",
                "choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop")),
                "usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
    }
}
