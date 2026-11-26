package ai.loomspan.integration;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;

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
    @Test void foreignModelLeafUsesItsRetainedConnection(@TempDir Path directory) throws Exception {
        try (var chat = new MockWebServer(); var providerServer = new MockWebServer(); var replacement = new MockWebServer()) {
            chat.enqueue(tool("risk--judge")); chat.enqueue(text("done"));
            providerServer.enqueue(text("evaluated"));
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                var leaf = new SkillDocument("judge","name: judge\ndescription: judgment\nmodel: selected\nprompt: Evaluate evidence\ninput_schema: {type: object, additionalProperties: true}\n");
                var providerConfiguration = new ExecutionConfiguration(config(providerServer,"captured",1,"never").yaml());
                var old = reload.prepare("risk",List.of(leaf),providerConfiguration,NamespaceVisibility.PUBLIC,Map.of("key","captured-old"));
                reload.publish(old);
                reload.publish(reload.prepare("orders",List.of(model("root","risk:judge")),config(chat,"orders",20,"always"),Map.of("key","chat")));
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("orders", "root",Map.of());
                var nextConfiguration = new ExecutionConfiguration(config(replacement,"replacement",1,"never").yaml());
                reload.publish(reload.prepare("risk",List.of(leaf),nextConfiguration,NamespaceVisibility.PUBLIC,Map.of("key","captured-new")));
                assertThat(pending.invoke().text()).isEqualTo("done");
            });
            assertThat(providerServer.getRequestCount()).isEqualTo(1);
            assertThat(replacement.getRequestCount()).isZero();
            var request = providerServer.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer captured-old");
            assertThat(request.getBody().readUtf8()).contains("captured-provider");
        }
    }
    @Test void publicChildCannotEnableEntryTracePersistence(@TempDir Path directory) throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(tool("tax--lookup")); server.enqueue(text("done"));
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                reload.publish(reload.prepare("tax", List.of(executor()), config(server,"tax",1,"always"), NamespaceVisibility.PUBLIC,Map.of("key","tax")));
                reload.publish(reload.prepare("orders",List.of(model("root","tax:lookup")),config(server,"orders",20,"never"),Map.of("key","orders")));
                Captures.values.clear();
                assertThat(context.getBean(SkillTemplate.class).invoke("orders", "root",Map.of()).text()).isEqualTo("done");
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
                var prepared = reload.prepare("tax", List.of(executor()), configuration, Map.of("key", "tax"));
                reload.publish(prepared);
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("tax", "lookup", Map.of());
                try { Thread.sleep(180); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                reload.remove("tax", prepared.generationId());
                assertThat(pending.invoke().text()).isEqualTo("looked-up");
                assertThat(pending.generationId()).isEqualTo(prepared.generationId());
            });
        }
    }
    @Test void parallelForeignBranchesIsolateCallerContextsOnSuccessAndFailure(@TempDir Path directory) throws Exception {
        for (boolean fails : List.of(false, true)) try (var server = new MockWebServer()) {
            server.enqueue(text("""
                    {"capabilityName":"orders:root","createdAt":"2026-10-30T00:00:00Z","status":"VALID","tasks":[
                     {"taskId":"tax","title":"Tax","status":"PENDING","capabilityName":"tax:lookup","intent":"lookup","dependsOn":[],"expectedOutputs":[],"parallelGroup":"both","note":""},
                     {"taskId":"rates","title":"Rates","status":"PENDING","capabilityName":"rates:lookup","intent":"lookup","dependsOn":[],"expectedOutputs":[],"parallelGroup":"both","note":""}]}
                    """));
            if (!fails) server.enqueue(text("{\"stepAction\":\"FINAL_RESPONSE\",\"finalResponse\":\"joined\"}"));
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                var zero = new SkillDocument("lookup", executor().yaml() + "input_schema: {type: object, properties: {}, additionalProperties: false}\n");
                for (String namespace : List.of("tax", "rates")) reload.publish(reload.prepare(namespace, List.of(zero),
                        config(server, namespace, 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", namespace)));
                var root = new SkillDocument("root", "name: root\ndescription: parallel\nmodel: selected\nplanning_mode: true\nconcurrency: true\nmax_steps: 3\n"
                        + "allowed_skills: [{name: tax:lookup}, {name: rates:lookup}]\n");
                reload.publish(reload.prepare("orders", List.of(root), config(server, "orders", 20, "always"), Map.of("key", "orders")));
                var previous = SecurityContextHolder.getContext();
                var security = SecurityContextHolder.createEmptyContext();
                var caller = UsernamePasswordAuthenticationToken.authenticated("parallel", "", List.of()); security.setAuthentication(caller);
                SecurityContextHolder.setContext(security);
                Captures.values.clear(); Captures.barrier = new java.util.concurrent.CyclicBarrier(2);
                Captures.failureName = fails ? "tax:lookup" : null;
                try {
                    var template = context.getBean(SkillTemplate.class);
                    if (fails) assertThatThrownBy(() -> template.invoke("orders", "root", Map.of())).isInstanceOf(SkillException.class);
                    else assertThat(template.invoke("orders", "root", Map.of()).text()).isEqualTo("joined");
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
    @Test void plannedAssignedAndAutomaticCallsUseExactQualifiedIdentity(@TempDir Path directory) throws Exception {
        for (boolean automatic : List.of(false, true)) try (var server = new MockWebServer()) {
            String plan = """
                    {"capabilityName":"orders:root","createdAt":"2026-10-30T00:00:00Z","status":"VALID",
                     "tasks":[{"taskId":"task","title":"Lookup","status":"PENDING","capabilityName":"order-support:find-order",
                     "intent":"lookup","dependsOn":[],"expectedOutputs":[],"parallelGroup":null,"note":""}]}
                    """;
            server.enqueue(text(plan));
            if (!automatic) {
                server.enqueue(text("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"task\",\"toolName\":\"order-support--find-order\",\"toolArguments\":{\"value\":\"unchanged\"}}"));
                server.enqueue(text("{\"stepAction\":\"CALL_TOOL\",\"taskId\":\"task\",\"toolName\":\"order-support:find-order\",\"toolArguments\":{\"value\":\"unchanged\"}}"));
            }
            context(directory).run(context -> {
                var reload = context.getBean(SkillReloader.class);
                String schema = automatic ? "input_schema:\n  type: object\n  properties: {}\n  additionalProperties: false\n"
                        : "input_schema:\n  type: object\n  properties:\n    value: {type: string}\n  required: [value]\n";
                reload.publish(reload.prepare("order-support", List.of(new SkillDocument("find-order", executor().yaml().replace("name: lookup", "name: find-order") + schema)),
                        config(server, "tax", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "tax")));
                String root = "name: root\ndescription: planner\nmodel: selected\nplanning_mode: true\nmax_steps: 3\n"
                        + "allowed_skills: [{name: order-support:find-order, required: true, max_tasks: 1}]\noutput_from: {skill: order-support:find-order}\n";
                reload.publish(reload.prepare("orders", List.of(new SkillDocument("root", root)), config(server, "orders", 20, "always"), Map.of("key", "orders")));
                Captures.values.clear();
                assertThat(context.getBean(SkillTemplate.class).invoke("orders", "root", Map.of()).text()).isEqualTo("looked-up");
                assertThat(Captures.values).hasSize(1);
                assertThat(Captures.values.getFirst().invocation.skillName()).isEqualTo("order-support:find-order");
                if (!automatic) assertThat(Captures.values.getFirst().invocation.input()).containsEntry("value", "unchanged");
            });
            assertThat(server.getRequestCount()).isEqualTo(automatic ? 1 : 3);
            server.takeRequest();
            if (!automatic) {
                String worker = server.takeRequest().getBody().readUtf8();
                assertThat(worker).contains("Exact capability/tool: order-support:find-order", "order-support:find-order");
                var wire = new tools.jackson.databind.ObjectMapper().readTree(worker);
                assertThat(wire.path("tools").isMissingNode() || wire.path("tools").isEmpty()).isTrue();
            }
        }
    }
    @Test void retainedForeignModelsAndExecutorUseTargetContextAndEntryBudgetAfterRemoval(@TempDir Path directory) throws Exception {
        try (var ordersServer = new MockWebServer(); var pricingServer = new MockWebServer(); var replacementServer = new MockWebServer()) {
            ordersServer.enqueue(tool("pricing--model"));
            ordersServer.enqueue(tool("orders--lookup"));
            ordersServer.enqueue(text("done"));
            pricingServer.enqueue(tool("tax--lookup"));
            pricingServer.enqueue(text("priced"));
            context(directory).run(context -> {
                assertThat(context).hasNotFailed();
                var reload = context.getBean(SkillReloader.class);
                var tax = reload.prepare("tax", List.of(executor()), config(pricingServer, "tax", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "tax-key"));
                reload.publish(tax);
                var pricing = reload.prepare("pricing", List.of(model("model", "tax:lookup")), config(pricingServer, "pricing", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "pricing-key"));
                reload.publish(pricing);
                var orders = reload.prepare("orders", List.of(model("root", "pricing:model", "orders:lookup"), executor()),
                        config(ordersServer, "orders", 20, "always"), Map.of("key", "orders-key"));
                reload.publish(orders);
                var previous = SecurityContextHolder.getContext();
                var caller = UsernamePasswordAuthenticationToken.authenticated("alice", "", List.of());
                var security = SecurityContextHolder.createEmptyContext(); security.setAuthentication(caller);
                SecurityContextHolder.setContext(security);
                try {
                    var admitted = context.getBean(SkillInvocationHandoff.class).handoff("orders", "root", Map.of());
                    assertThat(admitted.generationId()).isEqualTo(orders.generationId());
                    reload.publish(reload.prepare("pricing", List.of(model("model")), config(replacementServer, "new", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "new-key")));
                    reload.remove("tax", tax.generationId());
                    reload.remove("orders", orders.generationId());
                    Captures.values.clear();
                    assertThat(admitted.invoke().text()).isEqualTo("done");
                    assertThat(admitted.generationId()).isEqualTo(orders.generationId());
                    assertThat(ExecutionBindingScope.current()).isEmpty();
                    assertThat(SecurityContextHolder.getContext()).isSameAs(security);
                    assertThat(Captures.values).hasSize(2);
                    var foreign = Captures.values.getFirst(); var local = Captures.values.getLast();
                    assertThat(foreign.invocation.skillName()).isEqualTo("tax:lookup");
                    assertThat(foreign.invocation.executorConfigGenerationId()).isEqualTo(tax.generationId());
                    assertThat(local.invocation.skillName()).isEqualTo("orders:lookup");
                    assertThat(local.invocation.executorConfigGenerationId()).isEqualTo(orders.generationId());
                    assertThat(foreign.binding.entryGeneration().id()).isEqualTo(orders.generationId());
                    assertThat(foreign.binding.currentNamespace()).isEqualTo("tax");
                    assertThat(local.binding.currentNamespace()).isEqualTo("orders");
                    assertThat(foreign.binding.session()).isSameAs(local.binding.session());
                    assertThat(foreign.binding.snapshot()).isSameAs(local.binding.snapshot());
                    assertThat(foreign.caller).isSameAs(caller);
                    assertThat(local.caller).isSameAs(caller);
                    assertThat(foreign.binding.mission().parent().orElseThrow().skillName()).isEqualTo("pricing:model");
                    assertThat(local.binding.mission().parent().orElseThrow().skillName()).isEqualTo("orders:root");
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
            assertThat(parentRequest.getBody().readUtf8()).contains("orders-provider", "pricing--model", "orders--lookup");
            assertThat(childRequest.getBody().readUtf8()).contains("pricing-provider", "tax--lookup");
        }
    }

    @Test void exactProviderOfferExecutesResolvedChildAndKeepsRawEvidence(@TempDir Path directory) throws Exception {
        Path traces = directory.resolve("traces");
        for (String returned : List.of("order-support--find-order", "order-support:find-order", "order-support--unoffered")) {
            try (var server = new MockWebServer()) {
                String arguments = "{ \"note\" : \"雪 order-support--find-order order-support:find-order\", \"nested\": {\"value\":null} }";
                server.enqueue(ai.loomspan.testkit.ResponsesFixtures.response("test", "test", List.of(
                        ai.loomspan.testkit.ResponsesFixtures.functionCall(returned, "exact-call", arguments))));
                server.enqueue(text("done"));
                context(directory).withPropertyValues("loomspan.execution-trace.directory=" + traces).run(context -> {
                    var reload = context.getBean(SkillReloader.class);
                    reload.publish(reload.prepare("order-support", List.of(new SkillDocument("find-order",
                            "name: find-order\ndescription: lookup\nexecutor: fixture\ninput_schema: {type: object, additionalProperties: true}\n"),
                            new SkillDocument("unoffered", "name: unoffered\ndescription: omitted child\nexecutor: fixture\n")),
                            config(server, "support", 1, "never"), NamespaceVisibility.PUBLIC, Map.of("key", "support")));
                    reload.publish(reload.prepare("orders", List.of(model("root", "order-support:find-order"),
                            new SkillDocument("find-order", "name: find-order\ndescription: same local name\nexecutor: fixture\n")),
                            config(server, "orders", 20, "always"), Map.of("key", "orders")));
                    Captures.values.clear();
                    var template = context.getBean(SkillTemplate.class);
                    if (returned.equals("order-support--find-order")) {
                        assertThat(template.invoke("orders", "root", Map.of()).text()).isEqualTo("done");
                        assertThat(Captures.values).singleElement().satisfies(capture -> {
                            assertThat(capture.invocation.skillName()).isEqualTo("order-support:find-order");
                            assertThat(capture.invocation.input()).containsEntry("note", "雪 order-support--find-order order-support:find-order");
                            var records = new ArrayList<TraceRecord>();
                            capture.binding.session().readTraceRecords(records::add);
                            assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                                    .anySatisfy(record -> {
                                        var call = record.data().path("generations").path(0).path("toolCalls").path(0);
                                        assertThat(call.path("name").asString()).isEqualTo(returned);
                                        assertThat(call.path("arguments").asString()).isEqualTo(arguments);
                                    });
                            assertThat(records).anySatisfy(record -> assertThat(record.metadata())
                                    .containsEntry("capabilityName", "order-support:find-order"));
                        });
                    } else {
                        assertThatThrownBy(() -> template.invoke("orders", "root", Map.of())).isInstanceOf(SkillException.class);
                        assertThat(Captures.values).isEmpty();
                    }
                });
                var wire = new tools.jackson.databind.ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
                assertThat(wire.path("tools")).anySatisfy(tool -> assertThat(tool.path("name").asString())
                        .isEqualTo("order-support--find-order"));
                if (returned.equals("order-support--find-order"))
                    assertThat(server.takeRequest().getBody().readUtf8()).contains("order-support--find-order", "exact-call");
                var recorded = new ArrayList<tools.jackson.databind.JsonNode>();
                var json = new tools.jackson.databind.ObjectMapper();
                try (var files = java.nio.file.Files.walk(traces)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".ndjson")).toList())
                        for (String line : java.nio.file.Files.readAllLines(file)) recorded.add(json.readTree(line));
                }
                assertThat(recorded).filteredOn(record -> record.path("recordType").asString().equals("MODEL_RESPONSE_RECEIVED"))
                        .anySatisfy(record -> {
                            var call = record.path("data").path("generations").path(0).path("toolCalls").path(0);
                            assertThat(call.path("name").asString()).isEqualTo(returned);
                            assertThat(call.path("arguments").asString()).isEqualTo(arguments);
                        });
            }
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
    private static SkillDocument executor() { return new SkillDocument("lookup", "name: lookup\ndescription: lookup\nexecutor: fixture\n"); }
    private static ExecutionConfiguration config(MockWebServer server, String provider, int calls, String persistence) {
        return new ExecutionConfiguration("""
                loomspan:
                  connections:
                    selected:
                      driver: openai
                      base-url: %s
                      api-key: {$var: key}
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
        return ai.loomspan.testkit.ResponsesFixtures.response("test", "test", List.of(
                ai.loomspan.testkit.ResponsesFixtures.functionCall(name, "call", "{}")));
    }
    private static MockResponse text(String value) { return ai.loomspan.testkit.ResponsesFixtures.text(value); }
    record Capture(SkillExecutorInvocation invocation, ExecutionBinding binding, org.springframework.security.core.Authentication caller,
                   org.springframework.security.core.context.SecurityContext security, TracePersistencePolicy persistence,
                   int maxDepth, Map<String,Object> configuredLimits) {}
    static class CapturingExecutors {
        @SkillExecutor(namespace="order-support", name="fixture") public String support(SkillExecutorInvocation invocation) { return Captures.capture(invocation); }
        @SkillExecutor(namespace="tax", name="fixture") public String tax(SkillExecutorInvocation invocation) { return Captures.capture(invocation); }
        @SkillExecutor(namespace="rates", name="fixture") public String rates(SkillExecutorInvocation invocation) { return Captures.capture(invocation); }
        @SkillExecutor(namespace="orders", name="fixture") public String orders(SkillExecutorInvocation invocation) { return Captures.capture(invocation); }
    }
    @Configuration(proxyBeanMethods = false) static class Captures {
        static final List<Capture> values = new CopyOnWriteArrayList<>();
        static volatile java.util.concurrent.CyclicBarrier barrier;
        static volatile String failureName;
        @SuppressWarnings("unchecked")
        @Bean CapturingExecutors executors() { return new CapturingExecutors(); }
        private static String capture(SkillExecutorInvocation invocation) {
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
        }
    }
}
