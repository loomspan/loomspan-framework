package ai.loomspan.integration;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillTemplate;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual supported facade/dispatch with local provider responses. */
class DeclaredChildInputBindingsIntegrationTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"success", "missing", "approval", "revoked", "tool-quota", "provider-quota"})
    void directDispatchRetainsFailureAccessApprovalAndRealRequestBudgets(String scenario) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("direct"));
        String root = """
                name: root
                description: Assign approved work
                model: local
                planning_mode: true
                max_steps: 1
                prompt: ROOT
                input_schema:
                  type: object
                  properties:
                    requestId: {type: string}
                  additionalProperties: false
                allowed_skills:
                  - name: producer
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /caseId: {from: input, path: /requestId}
                output_from: {skill: producer}
                """;
        if (scenario.equals("tool-quota")) {
            root = root.replace("max_steps: 1", "max_steps: 2").replace("output_from: {skill: producer}", "output_from: {skill: consumer}")
                    .replace("output_from:", "  - {name: consumer, required: true, max_tasks: 1}\noutput_from:");
            Files.writeString(skills.resolve("consumer.yaml"), """
                    name: consumer
                    description: Second explicit task
                    rest: true
                    input_schema: {type: object, additionalProperties: false}
                    """);
        }
        Files.writeString(skills.resolve("root.yaml"), root);
        Files.writeString(skills.resolve("producer.yaml"), producer(scenario.equals("provider-quota") ? "model" : "rest") + "rbac_roles: [ALLOWED]\n");
        var revoked = new java.util.concurrent.atomic.AtomicBoolean();
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "caller", "unused", org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ALLOWED")) {
            @Override public Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return revoked.get() ? List.of() : super.getAuthorities();
            }
        };
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        var sideEffects = new java.util.concurrent.atomic.AtomicInteger();
        var observerCalls = new java.util.concurrent.atomic.AtomicInteger();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    sends.incrementAndGet();
                    String system = JSON.readTree(request.getBody().readUtf8()).path("messages").get(0).path("content").asText();
                    assertThat(system).contains("Create an ordered flight plan");
                    if (scenario.equals("revoked")) revoked.set(true);
                    return response(JSON.writeValueAsString(Map.of("capabilityName", "root", "createdAt", "2026-10-04T00:00:00Z",
                            "status", "VALID", "tasks", scenario.equals("tool-quota")
                                    ? List.of(task("producer", List.of()), task("consumer", List.of("producer")))
                                    : List.of(task("producer", List.of())))));
                }
            });
            server.start();
            new ApplicationContextRunner().withUserConfiguration(App.class).withBean(RestSkillHandler.class, () -> invocation -> {
                if (invocation.skillName().equals("producer")) assertThat(invocation.input()).containsEntry("caseId", "case-one");
                if (scenario.equals("approval")) throw new org.springframework.security.access.AccessDeniedException("Explicit application approval absent");
                sideEffects.incrementAndGet();
                return "COMPLETE";
            }).withPropertyValues("spring.main.web-application-type=none", "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                    "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + server.url("/v1"),
                    "loomspan.connections.local.api-key=local-test", "loomspan.connections.local.provider-retry.enabled=false",
                    "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=deterministic",
                    "loomspan.session.quotas.max-model-calls=1", "loomspan.session.quotas.max-provider-attempts=1",
                    "loomspan.session.quotas.max-tool-invocations=1")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var strategy = context.getBeanProvider(org.springframework.security.core.context.SecurityContextHolderStrategy.class)
                                .getIfAvailable(org.springframework.security.core.context.SecurityContextHolder::getContextHolderStrategy);
                        strategy.setContext(new org.springframework.security.core.context.SecurityContextImpl(authentication));
                        try {
                            var template = context.getBean(SkillTemplate.class);
                            Map<String, Object> input = scenario.equals("missing") ? Map.of() : Map.of("requestId", "case-one");
                            if (scenario.equals("success")) assertThat(template.invoke("root", input, view -> observerCalls.incrementAndGet())).isEqualTo("COMPLETE");
                            else org.assertj.core.api.Assertions.assertThatThrownBy(() -> template.invoke("root", input, view -> observerCalls.incrementAndGet()))
                                    .hasStackTraceContaining(switch (scenario) {
                                        case "missing" -> "binding_source_unavailable";
                                        case "approval" -> "Explicit application approval absent";
                                        case "revoked" -> "denied";
                                        default -> "quota";
                                    });
                        } finally { strategy.clearContext(); }
                    });
        }
        assertThat(sends).hasValue(1);
        assertThat(sideEffects).hasValue(scenario.equals("success") || scenario.equals("tool-quota") ? 1 : 0);
        assertThat(observerCalls).hasValue(1);
    }
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    @TempDir Path directory;
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration static class App {}
    public static class JavaProducer {
        @SkillMethod(name="producer", description="Assess one case")
        public Map<String,Object> producer(String caseId) { return Map.of("data", assessment(caseId)); }
    }
    public static class JavaConsumer {
        final AtomicReference<Map<String,Object>> received;
        JavaConsumer(AtomicReference<Map<String,Object>> received) { this.received = received; }
        @SkillMethod(name="consumer", description="Compare the supplied case evidence")
        public String consumer(String caseId, Map<String,Object> context) {
            received.set(Map.of("caseId",caseId,"context",context)); return "COMPLETE";
        }
    }
    @org.junit.jupiter.api.Test
    void completeAuthoringExampleLoadsAsOneValidatedGeneration() throws Exception {
        String topic=Files.readString(Path.of("agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md"));
        var blocks=java.util.regex.Pattern.compile("```yaml\\R(.*?)```",java.util.regex.Pattern.DOTALL).matcher(topic);
        Path skills=Files.createDirectory(directory.resolve("documented"));
        int count=0;
        while (blocks.find()) {
            String yaml=blocks.group(1).replace("model: assistant", "model: local");
            Files.writeString(skills.resolve("example" + count++ + ".yaml"),yaml);
        }
        assertThat(count).isEqualTo(3);
        new ApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues(
                "spring.main.web-application-type=none", "loomspan.skills.locations="+skills.toUri()+"*.yaml",
                "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=http://127.0.0.1:1",
                "loomspan.connections.local.api-key=local-test", "loomspan.models.local.connection=local",
                "loomspan.models.local.provider-model=deterministic").run(context -> {
            assertThat(context).hasNotFailed();
            var catalog=context.getBean(ai.loomspan.api.SkillCatalog.class);
            assertThat(catalog.skill("root")).isPresent();
            assertThat(catalog.skill("producer")).isPresent();
            assertThat(catalog.skill("consumer")).isPresent();
            assertThat(catalog.skill("consumer").orElseThrow().inputSchema()).contains("caseId", "records", "assessment");
        });
    }

    @ParameterizedTest
    @CsvSource({"model,model,false", "model,java,true", "model,rest,false", "java,model,true",
            "java,java,false", "java,rest,true", "rest,model,false", "rest,java,true", "rest,rest,false"})
    void deliversLargeBoundEvidenceFromEmptyModelArguments(String producerKind, String consumerKind, boolean reasoning) throws Exception {
        Path skills = Files.createDirectory(directory.resolve("skills"));
        Files.writeString(skills.resolve("root.yaml"), parent());
        if (!producerKind.equals("java")) Files.writeString(skills.resolve("producer.yaml"), producer(producerKind));
        if (!consumerKind.equals("java")) Files.writeString(skills.resolve("consumer.yaml"), consumer(consumerKind));
        AtomicReference<Map<String,Object>> received = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var modelProducerCalls = new java.util.concurrent.atomic.AtomicInteger();
        var plannerCalls = new java.util.concurrent.atomic.AtomicInteger();
        var consumerDispatchCalls = new java.util.concurrent.atomic.AtomicInteger();
        var consumerReasoningCalls = new java.util.concurrent.atomic.AtomicInteger();
        List<Map<String,Object>> actions = Collections.synchronizedList(new ArrayList<>());
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        List<Object> records = new ArrayList<>();
        for (int i=0;i<250;i++) records.add(Map.of("amount",new BigInteger("900719925474099312345"),
                "index",BigInteger.valueOf(i),"tags",List.of("source", "original"),"optionalText","{\"literal\":true}"));
        Map<String,Object> input = Map.of("requestId","case-one","records",records);
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    try {
                        String body=request.getBody().readUtf8(); requests.add(body);
                        JsonNode wire=JSON.readTree(body);
                        String system=wire.path("messages").get(0).path("content").asText();
                        if (system.contains("Create an ordered flight plan")) {
                            plannerCalls.incrementAndGet();
                            assertThat(system).contains("Declared child input dependencies", "exactly one", "Generate only inputs");
                            return response(JSON.writeValueAsString(Map.of("capabilityName","root","createdAt","2026-10-04T00:00:00Z",
                                    "status","VALID","tasks",List.of(task("producer",List.of()),task("consumer",List.of("producer"))))));
                        }
                        if (system.contains("coordinator-assigned task")) {
                            String child=system.contains("Exact capability/tool: producer") ? "producer" : "consumer";
                            assertThat(child).isEqualTo("consumer");
                            consumerDispatchCalls.incrementAndGet();
                            Map<String,Object> args=child.equals("consumer") && reasoning
                                    ? Map.of("context",Map.of("candidateReasoning","new reasoning")) : Map.of();
                            actions.add(args);
                            assertThat(system).contains("Framework supplies declared bound fields");
                            return response(JSON.writeValueAsString(Map.of("stepAction","CALL_TOOL","taskId",child,"toolName",child,"toolArguments",args)));
                        }
                        if (system.contains("PRODUCER")) {
                            if (modelProducerCalls.incrementAndGet() == 1) return response("{\"data\":9}");
                            return response(JSON.writeValueAsString(Map.of("data",assessment("case-one"))));
                        }
                        assertThat(system).contains("CONSUMER");
                        consumerReasoningCalls.incrementAndGet();
                        String user=wire.path("messages").get(wire.path("messages").size()-1).path("content").asText();
                        int start=user.indexOf("{"); int end=user.lastIndexOf("}");
                        received.set(JSON.readValue(user.substring(start,end+1),Map.class));
                        return response("COMPLETE");
                    } catch (Throwable ex) { failure.compareAndSet(null,ex); return new MockResponse().setResponseCode(500); }
                }
            }); server.start();
            ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(App.class)
                    .withBean(RestSkillHandler.class,()-> invocation -> {
                        if (invocation.skillName().equals("producer")) {
                            assertThat(invocation.input()).containsEntry("caseId","case-one");
                            return JSON.writeValueAsString(Map.of("data",assessment("case-one")));
                        }
                        received.set(invocation.input()); return "COMPLETE";
                    }).withPropertyValues("spring.main.web-application-type=none", "loomspan.skills.locations="+skills.toUri()+"*.yaml",
                            "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url="+server.url("/v1"),
                            "loomspan.connections.local.api-key=local-test", "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=deterministic",
                            "loomspan.session.mission-timeout=30s");
            if (producerKind.equals("java")) runner=runner.withBean(JavaProducer.class,JavaProducer::new);
            if (consumerKind.equals("java")) runner=runner.withBean(JavaConsumer.class,()->new JavaConsumer(received));
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                String result=context.getBean(SkillTemplate.class).invoke("root", input);
                assertThat(result).isEqualTo(consumerKind.equals("java") ? JSON.writeValueAsString("COMPLETE") : "COMPLETE");
            });
        }
        assertThat(failure.get()).isNull(); assertThat(actions).hasSize(1);
        assertThat(plannerCalls).hasValue(1);
        assertThat(consumerDispatchCalls).hasValue(1);
        assertThat(consumerReasoningCalls).hasValue(consumerKind.equals("model") ? 1 : 0);
        assertThat(requests).hasSize(2 + (producerKind.equals("model") ? 2 : 0) + (consumerKind.equals("model") ? 1 : 0));
        assertThat(modelProducerCalls).hasValue(producerKind.equals("model") ? 2 : 0);
        assertThat(actions).allSatisfy(args -> assertThat(JSON.writeValueAsString(args)).doesNotContain("900719925474099", "source", "case-one"));
        assertThat(received.get()).containsEntry("caseId","case-one");
        Map<String,Object> ctx=(Map<String,Object>)received.get().get("context");
        assertThat(ctx.get("records")).isEqualTo(records);
        assertThat(ctx.get("assessment")).isEqualTo(assessment("case-one"));
        assertThat(ctx.containsKey("candidateReasoning")).isEqualTo(reasoning);
        assertThat(requests).noneMatch(body->body.contains("All required plan tasks are already COMPLETE"));
    }
    private static Map<String,Object> assessment(String id) {
        return Map.of("case",id,"amount",new BigInteger("900719925474099312345"),
                "decimal",new BigDecimal("123456789.123456789123456789"),"rows",List.of(Map.of("value","unchanged")));
    }
    private static String parent() { return """
            name: root
            description: Transfer declared authoritative evidence
            model: local
            planning_mode: true
            max_steps: 2
            prompt: ROOT. Orchestrate evidence comparison.
            input_schema:
              type: object
              properties:
                requestId: {type: string}
                records: {type: array, items: {type: object, additionalProperties: true}}
              required: [requestId, records]
              additionalProperties: false
            allowed_skills:
              - name: producer
                required: true
                max_tasks: 1
                input_bindings:
                  /caseId: {from: input, path: /requestId}
              - name: consumer
                required: true
                max_tasks: 1
                input_bindings:
                  /caseId: {from: input, path: /requestId}
                  /context/records: {from: input, path: /records}
                  /context/assessment: {from: child_result, skill: producer, path: /data}
            output_from: {skill: consumer}
            """; }
    private static String producer(String kind) { return """
            name: producer
            description: Assess one case
            """ + (kind.equals("rest") ? "rest: true\n" : "model: local\nprompt: PRODUCER. Return the complete assessment.\n"
                    + "output_schema_max_retries: 1\noutput_schema:\n  type: object\n  properties:\n    data: {type: object, additionalProperties: true}\n  required: [data]\n  additionalProperties: false\n") + """
            input_schema:
              type: object
              properties:
                caseId: {type: string}
              required: [caseId]
              additionalProperties: false
            """; }
    private static String consumer(String kind) { return """
            name: consumer
            description: Compare complete evidence
            """ + (kind.equals("rest") ? "rest: true\n" : "model: local\nprompt: CONSUMER. Use the supplied evidence.\n") + """
            input_schema:
              type: object
              properties:
                caseId: {type: string}
                context:
                  type: object
                  properties:
                    records: {type: array, items: {type: object, additionalProperties: true}}
                    assessment: {type: object, additionalProperties: true}
                    candidateReasoning: {type: string}
                  required: [records, assessment]
                  additionalProperties: false
              required: [caseId, context]
              additionalProperties: false
            """; }
    private static Map<String,Object> task(String child,List<String> dependencies) {
        Map<String,Object> task=new LinkedHashMap<>(); task.put("taskId",child);task.put("title",child);task.put("status","PENDING");
        task.put("capabilityName",child);task.put("intent","Fulfill responsibility");task.put("dependsOn",dependencies);
        task.put("expectedOutputs",List.of("complete result"));task.put("parallelGroup",null);task.put("note","");return task;
    }
    private static MockResponse response(String content) {
        return new MockResponse().setHeader("Content-Type","application/json").setBody(JSON.writeValueAsString(Map.of("id","local",
                "object","chat.completion","created",1,"model","deterministic","choices",List.of(Map.of("index",0,
                "message",Map.of("role","assistant","content",content),"finish_reason","stop")),"usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
    }
}
