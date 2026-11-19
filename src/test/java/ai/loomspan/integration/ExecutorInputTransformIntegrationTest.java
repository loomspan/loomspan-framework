package ai.loomspan.integration;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.ExecutorDiagnosticTestSupport;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.skill.SkillGenerationManager;
import ai.loomspan.internal.vfs.SessionLocalVirtualFileSystem;
import ai.loomspan.testkit.FixtureExecutor;
import ai.loomspan.testkit.ResponsesFixtures;
import com.schibsted.spt.data.jslt.JsltException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.loomspan.testkit.ExecutorTransformFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ExecutorInputTransformIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    @Test void directAndPlannedChildDeliverTheSameNativeObjectAndRetainBusinessDiagnostics() throws Exception {
        String leaf = """
                name: evaluation
                description: Evaluate business input
                executor: configured
                config: {marker: skill-only}
                input_schema:
                  type: object
                  properties:
                    context: {type: object, additionalProperties: true}
                    policy: {type: object, additionalProperties: true}
                    candidates: {type: object, additionalProperties: true}
                    parameters: {type: object, additionalProperties: true}
                  required: [context, policy, candidates, parameters]
                  additionalProperties: false
                """ + transform(acceptanceExpression());
        Files.writeString(directory.resolve("evaluation.yaml"), leaf);
        Files.writeString(directory.resolve("configBoundary.yaml"), """
                name: configBoundary
                description: Observe the complete expression input
                executor: configured
                config: {marker: skill-only}
                """ + transform("."));
        Files.writeString(directory.resolve("root.yaml"), """
                name: root
                description: Bind executor business input
                model: local
                planning_mode: true
                max_steps: 1
                input_schema: {type: object, additionalProperties: true}
                allowed_skills:
                  - name: default-evaluation
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /context: {from: input, path: /context}
                      /policy: {from: input, path: /policy}
                      /candidates: {from: input, path: /candidates}
                      /parameters: {from: input, path: /parameters}
                output_from: {skill: default-evaluation}
                """);
        var settings = Files.writeString(directory.resolve("application.yml"),
                "loomspan: {executor-config: {default: {settings: {marker: executor-only}}}}\n");
        var executor = new ConfiguredExecutor();
        var input = businessInput();
        var expected = executorOutput();
        try (var provider = new MockWebServer()) {
            String plan = JSON.writeValueAsString(Map.of("capabilityName", "default-root",
                    "createdAt", "2026-10-10T00:00:00Z", "status", "VALID",
                    "tasks", List.of(ExplicitJavaFailureHandlingIntegrationTest.task("default-evaluation"))));
            provider.enqueue(ResponsesFixtures.text(plan));
            provider.enqueue(ResponsesFixtures.text(plan));
            provider.start();
            withProvider(runner(), provider).withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues("spring.config.location=" + settings.toUri())
                    .withBean(ConfiguredExecutor.class, () -> executor).run(context -> {
                assertThat(context).hasNotFailed();
                var template = context.getBean(SkillTemplate.class);
                var invalid = Map.of("context", "invalid", "policy", input.get("policy"),
                        "candidates", input.get("candidates"), "parameters", input.get("parameters"));
                assertThatThrownBy(() -> template.invoke("default-evaluation", invalid))
                        .isInstanceOf(SkillInputValidationException.class);
                assertThatThrownBy(() -> template.invoke("default-root", invalid))
                        .hasStackTraceContaining("binding_assembled_input_invalid");
                assertThat(executor.calls).isEmpty();

                assertThat(template.invoke("default-evaluation", input)).isEqualTo("accepted");
                assertThat(template.invoke("default-root", input)).isEqualTo("accepted");
                assertThat(executor.calls).hasSize(2).allSatisfy(call -> {
                    assertThat(call.input()).isEqualTo(expected);
                    assertThat(call.skillConfig()).isEqualTo(Map.of("marker", "skill-only"));
                    assertThat(call.executorConfig()).isEqualTo(Map.of("marker", "executor-only"));
                });
                assertThat(input).isEqualTo(businessInput());
                assertThat(executor.remainingTimes).hasSize(2).allSatisfy(remaining ->
                        assertThat(remaining).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20)));

                var descriptor = context.getBean(SkillReloader.class).snapshot("default").skill("default-evaluation").orElseThrow();
                assertThat(descriptor.kind()).isEqualTo(SkillKind.JAVA);
                var schema = JSON.readTree(descriptor.inputSchema());
                assertThat(schema.path("properties").propertyNames())
                        .containsExactlyInAnyOrder("context", "policy", "candidates", "parameters");
                var catalogEntry = context.getBean(SkillGenerationManager.class).registeredSkillCatalog()
                        .find("default-evaluation").orElseThrow();
                assertThat(catalogEntry.yaml()).isEqualTo(leaf);
                assertThat(catalogEntry.sourcePath()).contains("evaluation.yaml");
                assertThat(executor.sessions).hasSize(2).allSatisfy(session ->
                        assertThat(ExecutorDiagnosticTestSupport.logicalRecords(session).stream()
                                .filter(record -> record.recordType() == TraceRecordType.FRAME_OPENED
                                        && record.route().equals("default-evaluation")
                                        && record.data().has("missionInput")).toList())
                                .singleElement().satisfies(record ->
                                        assertThat(record.data().path("missionInput")).isEqualTo(JSON.valueToTree(input))));

                var configBoundaryInput = Map.<String, Object>of("business", "case");
                assertThat(template.invoke("default-configBoundary", configBoundaryInput)).isEqualTo("accepted");
                assertThat(executor.calls).hasSize(3);
                var configBoundaryCall = executor.calls.getLast();
                assertThat(configBoundaryCall.input()).isEqualTo(configBoundaryInput);
                assertThat(configBoundaryCall.skillConfig()).isEqualTo(Map.of("marker", "skill-only"));
                assertThat(configBoundaryCall.executorConfig()).isEqualTo(Map.of("marker", "executor-only"));
            });
            assertThat(provider.getRequestCount()).isEqualTo(2);
            assertThat(provider.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
            var request = provider.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(JSON.readTree(request.getBody().readUtf8()).path("instructions").textValue())
                    .contains("Declared child input dependencies", "\"context\":false", "\"policy\":false",
                            "\"candidates\":false", "\"parameters\":false");
        }
    }

    @Test void rejectsUnsupportedResolvedInputAndBadResultsBeforeTheExecutorRuns() throws Exception {
        writeLeaf("identity", ".");
        writeLeaf("scalar", "42");
        writeLeaf("failure", "error(\"fixture evaluation failure\")");
        Files.writeString(directory.resolve("parent.yaml"), """
                name: parent
                description: Pass a resolved file to the executor
                model: local
                allowed_skills: [{name: default-writeFile}, {name: default-identity}]
                """);
        var calls = new AtomicInteger();
        var writer = new ExecutorApplicationIntegrationTest.FileWriter();
        try (var provider = new MockWebServer()) {
            provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.tool("default-writeFile", "write", "{}", ""));
            provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.tool("default-identity", "consume",
                    "{\"file\":\"ref://payload.txt\"}", ""));
            provider.start();
            withProvider(runner(), provider).withBean(ExecutorApplicationIntegrationTest.FileWriter.class, () -> writer)
                    .withBean(FixtureExecutor.class, () -> new FixtureExecutor(call -> {
                        calls.incrementAndGet();
                        return "unexpected";
                    })).run(context -> {
                assertThat(context).hasNotFailed();
                writer.vfs = context.getBean(SessionLocalVirtualFileSystem.class);
                var template = context.getBean(SkillTemplate.class);
                assertThatThrownBy(() -> template.invoke("identity", Map.of("file", new ByteArrayResource(new byte[0]))))
                        .isInstanceOf(SkillException.class)
                        .hasStackTraceContaining("Input at /file must be a JSON value; found org.springframework.core.io.ByteArrayResource");
                assertThatThrownBy(() -> template.invoke("scalar", Map.of()))
                        .isInstanceOf(SkillException.class).hasStackTraceContaining("result must be a JSON object")
                        .satisfies(error -> assertThat(((SkillException) error).getFailureDiagnosis()).hasValueSatisfying(diagnosis -> {
                            assertThat(diagnosis.skillName()).isEqualTo("default-scalar");
                            assertThat(diagnosis.stage()).isEqualTo(ai.loomspan.api.SkillContractStage.INPUT_TRANSFORM);
                            assertThat(diagnosis.issues()).isEmpty();
                        }));
                var events = new ArrayList<SkillExecutionView>();
                assertThatThrownBy(() -> template.invoke("failure", Map.of(), events::add))
                        .isInstanceOf(SkillException.class).hasRootCauseInstanceOf(JsltException.class)
                        .hasStackTraceContaining("default-failure' input_transform failed")
                        .hasStackTraceContaining("fixture evaluation failure");
                assertThat(events).singleElement().satisfies(view -> assertThat(view.events()).anySatisfy(event -> {
                    assertThat(event.type()).isEqualTo("SKILL_FINISHED");
                    assertThat(event.details()).containsEntry("status", "failed");
                }));
                try {
                    assertThatThrownBy(() -> template.invoke("parent", Map.of()))
                            .isInstanceOf(SkillException.class)
                            .hasStackTraceContaining("Input at /file must be a JSON value; found org.springframework.core.io.FileSystemResource");
                    assertThat(writer.file).isNotNull().exists();
                } finally {
                    writer.cleanup();
                }
                assertThat(calls).hasValue(0);
            });
            assertThat(provider.getRequestCount()).isEqualTo(2);
        }
    }

    @Test void retainsTheLeafExpressionAndRejectsInvalidPreparationWithoutChangingActiveState() throws Exception {
        var calls = new CopyOnWriteArrayList<SkillExecutorInvocation>();
        runner().withBean(FixtureExecutor.class, () -> new FixtureExecutor(call -> {
            calls.add(call);
            return (String) call.input().get("version");
        })).run(context -> {
            assertThat(context).hasNotFailed();
            var reloader = context.getBean(SkillReloader.class);
            var template = context.getBean(SkillTemplate.class);
            var handoffs = context.getBean(SkillInvocationHandoff.class);
            var config = new ExecutionConfiguration("loomspan: {}");
            reloader.publish(reloader.prepare("default", List.of(), config, NamespaceVisibility.PUBLIC));
            var old = reloader.prepare("work", List.of(versioned("{\"version\": \"old\"}")), config);
            reloader.publish(old);
            var retained = handoffs.handoff("work-leaf", Map.of("business", "captured"));
            try {
                var current = reloader.prepare("work", List.of(versioned("{\"version\": \"new\"}")), config);
                reloader.publish(current);
                assertThat(calls).isEmpty();

                assertThatThrownBy(() -> reloader.prepare("work", List.of(versioned("{")), config))
                        .isInstanceOf(SkillReloadException.class).hasMessageContaining("input_transform.expression");
                var invalidShape = new SkillDocument("leaf.yaml", versioned("{}").yaml().replace("language: jslt", "language: false"));
                assertThatThrownBy(() -> reloader.prepare("work", List.of(invalidShape), config))
                        .isInstanceOf(SkillReloadException.class).hasMessageContaining("input_transform.language");
                assertThat(calls).isEmpty();
                assertThat(retained.generationId()).isEqualTo(old.generationId());
                assertThat(reloader.snapshot("work").generationId()).isEqualTo(current.generationId()).isNotEqualTo(old.generationId());
                assertThat(template.invoke("work-leaf", Map.of())).isEqualTo("new");
                assertThat(retained.invoke()).isEqualTo("old");
            } finally {
                retained.release();
            }
            assertThat(calls).hasSize(2);
            assertThat(calls.getFirst().executorConfigGenerationId()).isEqualTo(calls.getLast().executorConfigGenerationId());
        });
    }

    private static SkillDocument versioned(String expression) {
        return new SkillDocument("leaf.yaml", "name: leaf\ndescription: Captured expression\nexecutor: default-fixture\n"
                + transform(expression));
    }

    private void writeLeaf(String name, String expression) throws Exception {
        Files.writeString(directory.resolve(name + ".yaml"), "name: " + name
                + "\ndescription: Transform executor input\nexecutor: fixture\n" + transform(expression));
    }

    private static String transform(String expression) {
        return "input_transform:\n  language: jslt\n  expression: |\n" + expression.indent(4);
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                        "loomspan.session.mission-timeout=20s", "loomspan.execution-trace.persistence=always");
    }

    private static ApplicationContextRunner withProvider(ApplicationContextRunner runner, MockWebServer provider) {
        return runner.withPropertyValues("loomspan.connections.local.driver=openai",
                "loomspan.connections.local.base-url=" + provider.url("/v1"), "loomspan.connections.local.api-key=fixture",
                "loomspan.connections.local.provider-retry.enabled=false", "loomspan.models.local.connection=local",
                "loomspan.models.local.provider-model=planner");
    }

    public static class ConfiguredExecutor {
        final List<SkillExecutorInvocation> calls = new CopyOnWriteArrayList<>();
        final List<LoomspanSession> sessions = new CopyOnWriteArrayList<>();
        final List<Duration> remainingTimes = new CopyOnWriteArrayList<>();

        @SkillExecutor(name = "configured", config = "settings")
        public String execute(SkillExecutorInvocation call) {
            assertThat(call.isCancelled()).isFalse();
            remainingTimes.add(call.remainingTime().orElseThrow());
            calls.add(call);
            sessions.add(ExecutionBindingScope.requireCurrent().session());
            return "accepted";
        }
    }
}
