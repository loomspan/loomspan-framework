package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class NamespaceExecutionAuthorityIntegrationTest {
    @Test void foreignTypeSafeLeafUsesItsRetainedNativeConnection(@TempDir Path directory) throws Exception {
        try (var chat = new MockWebServer(); var nativeServer = new MockWebServer(); var replacement = new MockWebServer()) {
            chat.enqueue(tool("Risk-judge")); chat.enqueue(text("done"));
            nativeServer.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody("{\"answers\":{}}"));
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                var leaf = new SkillDocument("judge","name: judge\ndescription: judgment\nmodel: selected\ninput_schema: {type: object, additionalProperties: true}\ntypesafe:\n  state: fixed\n  questions: {q: {type: noul, instructions: decide}}\n");
                var nativeConfiguration = new ExecutionConfiguration(config(nativeServer,"native",1,"never").yaml().replace("driver: openai","driver: typesafe"));
                var old = reload.prepare("risk",List.of(leaf),nativeConfiguration,NamespaceVisibility.PUBLIC,Map.of("key","native-old"));
                reload.publish(old);
                reload.publish(reload.prepare("orders",List.of(model("root","risk-judge")),config(chat,"orders",20,"always"),Map.of("key","chat")));
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("orders-root",Map.of());
                var nextConfiguration = new ExecutionConfiguration(config(replacement,"replacement",1,"never").yaml().replace("driver: openai","driver: typesafe"));
                reload.publish(reload.prepare("risk",List.of(leaf),nextConfiguration,NamespaceVisibility.PUBLIC,Map.of("key","native-new")));
                assertThat(pending.invoke()).isEqualTo("done");
            });
            assertThat(nativeServer.getRequestCount()).isEqualTo(1);
            assertThat(replacement.getRequestCount()).isZero();
            var request = nativeServer.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer native-old");
            assertThat(request.getBody().readUtf8()).contains("native-provider");
        }
    }
    @Test void publicChildCannotEnableEntryTracePersistence(@TempDir Path directory) throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(tool("tax-lookup")); server.enqueue(text("done"));
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                reload.publish(reload.prepare("tax", List.of(rest()), config(server,"tax",1,"always"), NamespaceVisibility.PUBLIC,Map.of("key","tax")));
                reload.publish(reload.prepare("orders",List.of(model("root","tax-lookup")),config(server,"orders",20,"never"),Map.of("key","orders")));
                Captures.values.clear();
                assertThat(context.getBean(SkillTemplate.class).invoke("orders-root",Map.of())).isEqualTo("done");
                assertThat(Captures.values).hasSize(1);
                assertThat(Captures.values.getFirst().persistence).isEqualTo(TracePersistencePolicy.NEVER);
                assertThat(Captures.values.getFirst().configuredLimits).containsEntry("maxModelCalls",20);
            });
        }
    }
    @Test void queuedHandoffStartsMissionTimeoutOnlyWhenExecuted(@TempDir Path directory) throws Exception {
        try (var server = new MockWebServer()) {
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                var configuration = new ExecutionConfiguration(config(server, "tax", 1, "never").yaml().replace("mission-timeout: 10s", "mission-timeout: 100ms"));
                var prepared = reload.prepare("tax", List.of(rest()), configuration, Map.of("key", "tax"));
                reload.publish(prepared);
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("tax-lookup", Map.of());
                try { Thread.sleep(180); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                reload.remove("tax", prepared.generationId());
                assertThat(pending.invoke()).isEqualTo("looked-up");
                assertThat(pending.generationId()).isEqualTo(prepared.generationId());
            });
        }
    }
    @Test void parallelForeignBranchesIsolateCallerContextsOnSuccessAndFailure(@TempDir Path directory) throws Exception {
        for (boolean fails : List.of(false, true)) try (var server = new MockWebServer()) {
            server.enqueue(text("""
                    {"capabilityName":"orders-root","createdAt":"2026-10-30T00:00:00Z","status":"VALID","tasks":[
                     {"taskId":"tax","title":"Tax","status":"PENDING","capabilityName":"tax-lookup","intent":"lookup","dependsOn":[],"expectedOutputs":[],"parallelGroup":"both","note":""},
                     {"taskId":"rates","title":"Rates","status":"PENDING","capabilityName":"rates-lookup","intent":"lookup","dependsOn":[],"expectedOutputs":[],"parallelGroup":"both","note":""}]}
                    """));
            if (!fails) server.enqueue(text("{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"joined\"}"));
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                var zero = new SkillDocument("lookup", rest().yaml() + "input_schema: {type: object, properties: {}, additionalProperties: false}\n");
                for (String namespace : List.of("tax", "rates")) reload.publish(reload.prepare(namespace, List.of(zero),
                        config(server, namespace, 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", namespace)));
                var root = new SkillDocument("root", "name: root\ndescription: parallel\nmodel: selected\nplanning_mode: true\nconcurrency: true\nmax_steps: 3\n"
                        + "allowed_skills: [{name: tax-lookup}, {name: rates-lookup}]\n");
                reload.publish(reload.prepare("orders", List.of(root), config(server, "orders", 20, "always"), Map.of("key", "orders")));
                var previous = SecurityContextHolder.getContext();
                var security = SecurityContextHolder.createEmptyContext();
                var caller = UsernamePasswordAuthenticationToken.authenticated("parallel", "", List.of()); security.setAuthentication(caller);
                SecurityContextHolder.setContext(security);
                Captures.values.clear(); Captures.barrier = new java.util.concurrent.CyclicBarrier(2);
                Captures.failureName = fails ? "tax-lookup" : null;
                try {
                    var template = context.getBean(SkillTemplate.class);
                    if (fails) assertThatThrownBy(() -> template.invoke("orders-root", Map.of())).isInstanceOf(SkillException.class);
                    else assertThat(template.invoke("orders-root", Map.of())).isEqualTo("joined");
                    assertThat(ExecutionBindingScope.current()).isEmpty();
                    assertThat(SecurityContextHolder.getContext()).isSameAs(security);
                    assertThat(Captures.values).hasSize(2);
                    var first = Captures.values.getFirst(); var second = Captures.values.getLast();
                    assertThat(first.binding.currentNamespace()).isNotEqualTo(second.binding.currentNamespace());
                    assertThat(first.binding.session()).isSameAs(second.binding.session());
                    assertThat(first.binding.snapshot()).isSameAs(second.binding.snapshot());
                    assertThat(first.binding.mission().parent()).isEqualTo(second.binding.mission().parent());
                    assertThat(first.caller).isSameAs(caller); assertThat(second.caller).isSameAs(caller);
                    assertThat(first.security).isNotSameAs(second.security).isNotSameAs(security);
                } finally { Captures.barrier = null; Captures.failureName = null; SecurityContextHolder.setContext(previous); }
            });
        }
    }
    @Test void plannedAssignedAndAutomaticCallsUseCanonicalForeignIdentity(@TempDir Path directory) throws Exception {
        for (boolean automatic : List.of(false, true)) try (var server = new MockWebServer()) {
            String plan = """
                    {"capabilityName":"Orders-root","createdAt":"2026-10-30T00:00:00Z","status":"VALID",
                     "tasks":[{"taskId":"task","title":"Lookup","status":"PENDING","capabilityName":"Tax-lookup",
                     "intent":"lookup","dependsOn":[],"expectedOutputs":[],"parallelGroup":null,"note":""}]}
                    """;
            server.enqueue(text(plan));
            if (!automatic) {
                server.enqueue(text("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"task\",\"toolName\":\"tax-Lookup\",\"toolArguments\":{\"value\":\"unchanged\"}}"));
                server.enqueue(text("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"task\",\"toolName\":\"Tax-lookup\",\"toolArguments\":{\"value\":\"unchanged\"}}"));
            }
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                String schema = automatic ? "input_schema:\n  type: object\n  properties: {}\n  additionalProperties: false\n"
                        : "input_schema:\n  type: object\n  properties:\n    value: {type: string}\n  required: [value]\n";
                reload.publish(reload.prepare("tax", List.of(new SkillDocument("lookup", rest().yaml() + schema)),
                        config(server, "tax", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "tax")));
                String root = "name: root\ndescription: planner\nmodel: selected\nplanning_mode: true\nmax_steps: 3\n"
                        + "allowed_skills: [{name: tax-lookup, required: true, max_tasks: 1}]\noutput_from: {skill: tax-lookup}\n";
                reload.publish(reload.prepare("orders", List.of(new SkillDocument("root", root)), config(server, "orders", 20, "always"), Map.of("key", "orders")));
                Captures.values.clear();
                assertThat(context.getBean(SkillTemplate.class).invoke("orders-root", Map.of())).isEqualTo("looked-up");
                assertThat(Captures.values).hasSize(1);
                assertThat(Captures.values.getFirst().invocation.skillName()).isEqualTo("tax-lookup");
                if (!automatic) assertThat(Captures.values.getFirst().invocation.input()).containsEntry("value", "unchanged");
            });
            assertThat(server.getRequestCount()).isEqualTo(automatic ? 1 : 3);
        }
    }
    @Test void retainedForeignModelsAndRestUseTargetContextAndEntryBudgetAfterRemoval(@TempDir Path directory) throws Exception {
        try (var ordersServer = new MockWebServer(); var pricingServer = new MockWebServer(); var replacementServer = new MockWebServer()) {
            ordersServer.enqueue(tool("Pricing-model"));
            ordersServer.enqueue(tool("orders-lookup"));
            ordersServer.enqueue(text("done"));
            pricingServer.enqueue(tool("Tax-lookup"));
            pricingServer.enqueue(text("priced"));
            context(directory).run(context -> {
                assertThat(context).hasNotFailed();
                var reload = context.getBean(SkillReloader.class);
                var tax = reload.prepare("tax", List.of(rest()), config(pricingServer, "tax", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "tax-key"));
                reload.publish(tax);
                var pricing = reload.prepare("pricing", List.of(model("model", "tax-lookup")), config(pricingServer, "pricing", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "pricing-key"));
                reload.publish(pricing);
                var orders = reload.prepare("orders", List.of(model("root", "pricing-model", "orders-lookup"), rest()),
                        config(ordersServer, "orders", 20, "always"), Map.of("key", "orders-key"));
                reload.publish(orders);
                var previous = SecurityContextHolder.getContext();
                var caller = UsernamePasswordAuthenticationToken.authenticated("alice", "", List.of());
                var security = SecurityContextHolder.createEmptyContext(); security.setAuthentication(caller);
                SecurityContextHolder.setContext(security);
                try {
                    var admitted = context.getBean(SkillInvocationHandoff.class).handoff("orders-root", Map.of());
                    assertThat(admitted.generationId()).isEqualTo(orders.generationId());
                    reload.publish(reload.prepare("pricing", List.of(model("model")), config(replacementServer, "new", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "new-key")));
                    reload.remove("tax", tax.generationId());
                    reload.remove("orders", orders.generationId());
                    Captures.values.clear();
                    assertThat(admitted.invoke()).isEqualTo("done");
                    assertThat(admitted.generationId()).isEqualTo(orders.generationId());
                    assertThat(ExecutionBindingScope.current()).isEmpty();
                    assertThat(SecurityContextHolder.getContext()).isSameAs(security);
                    assertThat(Captures.values).hasSize(2);
                    var foreign = Captures.values.getFirst(); var local = Captures.values.getLast();
                    assertThat(foreign.invocation.skillName()).isEqualTo("tax-lookup");
                    assertThat(foreign.invocation.generationId()).isEqualTo(tax.generationId());
                    assertThat(local.invocation.skillName()).isEqualTo("orders-lookup");
                    assertThat(local.invocation.generationId()).isEqualTo(orders.generationId());
                    assertThat(foreign.binding.entryGeneration().id()).isEqualTo(orders.generationId());
                    assertThat(foreign.binding.currentNamespace()).isEqualTo("tax");
                    assertThat(local.binding.currentNamespace()).isEqualTo("orders");
                    assertThat(foreign.binding.session()).isSameAs(local.binding.session());
                    assertThat(foreign.binding.snapshot()).isSameAs(local.binding.snapshot());
                    assertThat(foreign.caller).isSameAs(caller);
                    assertThat(local.caller).isSameAs(caller);
                    assertThat(foreign.binding.mission().parent().orElseThrow().skillName()).isEqualTo("pricing-model");
                    assertThat(local.binding.mission().parent().orElseThrow().skillName()).isEqualTo("orders-root");
                    assertThat(local.binding.session().getSessionUsage().orElseThrow().modelCalls()).isEqualTo(5);
                    assertThat(foreign.persistence).isEqualTo(TracePersistencePolicy.ALWAYS);
                    assertThat(foreign.maxDepth).isEqualTo(12);
                    assertThat(foreign.configuredLimits).hasSize(6).containsEntry("maxModelCalls", 20);
                } finally { SecurityContextHolder.setContext(previous); }
            });
            assertThat(ordersServer.getRequestCount()).isEqualTo(3);
            assertThat(pricingServer.getRequestCount()).isEqualTo(2);
            assertThat(replacementServer.getRequestCount()).isZero();
            var parentRequest = ordersServer.takeRequest();
            var childRequest = pricingServer.takeRequest();
            assertThat(parentRequest.getHeader("Authorization")).isEqualTo("Bearer orders-key");
            assertThat(childRequest.getHeader("Authorization")).isEqualTo("Bearer pricing-key");
            assertThat(parentRequest.getBody().readUtf8()).contains("orders-provider", "pricing-model", "orders-lookup");
            assertThat(childRequest.getBody().readUtf8()).contains("pricing-provider", "tax-lookup");
        }
    }

    private static ApplicationContextRunner context(Path directory) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                LoomspanJacksonAutoConfiguration.class, LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(Captures.class).withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml");
    }
    private static SkillDocument model(String name, String... children) {
        return new SkillDocument(name, "name: " + name + "\ndescription: model\nmodel: selected\nplanning_mode: false\n"
                + (children.length == 0 ? "" : "allowed_skills:\n" + Arrays.stream(children).map(child -> "  - name: " + child + "\n").reduce("", String::concat)));
    }
    private static SkillDocument rest() { return new SkillDocument("lookup", "name: lookup\ndescription: lookup\nrest: true\n"); }
    private static ExecutionConfiguration config(MockWebServer server, String provider, int calls, String persistence) {
        return new ExecutionConfiguration("""
                loomspan:
                  connections:
                    selected:
                      driver: openai
                      base-url: %s
                      api-key-ref: key
                      request-timeout: 5s
                      provider-retry:
                        max-attempts: 1
                  models:
                    selected:
                      connection: selected
                      provider-model: %s-provider
                  session:
                    max-depth: %d
                    mission-timeout: 10s
                    quotas:
                      max-model-calls: %d
                  execution-trace:
                    persistence: %s
                """.formatted(server.url("/v1"), provider, calls == 20 ? 12 : 1, calls, persistence));
    }
    private static MockResponse tool(String name) {
        return response("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":\"{}\"}}]}", "tool_calls");
    }
    private static MockResponse text(String value) { return response("{\"role\":\"assistant\",\"content\":"
            + ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion().writeValueAsString(value) + "}", "stop"); }
    private static MockResponse response(String message, String finish) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"message\":" + message
                + ",\"finish_reason\":\"" + finish + "\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}");
    }
    record Capture(RestSkillInvocation invocation, ExecutionBinding binding, org.springframework.security.core.Authentication caller,
                   org.springframework.security.core.context.SecurityContext security, TracePersistencePolicy persistence,
                   int maxDepth, Map<String,Object> configuredLimits) {}
    @Configuration(proxyBeanMethods = false) static class Captures {
        static final List<Capture> values = new CopyOnWriteArrayList<>();
        static volatile java.util.concurrent.CyclicBarrier barrier;
        static volatile String failureName;
        @SuppressWarnings("unchecked")
        @Bean RestSkillHandler handler() { return invocation -> {
            var binding = ExecutionBindingScope.requireCurrent();
            var records = new ArrayList<TraceRecord>(); binding.session().readTraceRecords(records::add);
            values.add(new Capture(invocation, binding, SecurityContextHolder.getContext().getAuthentication(), SecurityContextHolder.getContext(),
                    binding.session().getExecutionTrace().persistencePolicy(), binding.session().getMaxDepth(),
                    (Map<String,Object>) records.getFirst().metadata().get("configuredLimits")));
            var rendezvous = barrier;
            if (rendezvous != null) try { rendezvous.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception ex) { throw new IllegalStateException("Parallel branches did not meet", ex); }
            if (invocation.skillName().equals(failureName)) throw new IllegalStateException("child failed");
            return "looked-up";
        }; }
    }
}
