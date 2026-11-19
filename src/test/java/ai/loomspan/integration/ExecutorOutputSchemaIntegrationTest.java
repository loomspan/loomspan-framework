package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.outputschema.LoomspanOutputSchemaValidationException;
import ai.loomspan.internal.outputschema.OutputSchemaOutcomeStatus;
import ai.loomspan.testkit.FixtureExecutor;
import ai.loomspan.testkit.ResponsesFixtures;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.*;

class ExecutorOutputSchemaIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = """
            output_schema:
              type: object
              properties:
                repeatContactProbability: {type: number, minimum: 0, maximum: 1}
              required: [repeatContactProbability]
              additionalProperties: false
            """;
    private static final String TRANSFORM = "{\"repeatContactProbability\": .answers.repeat.noul}";
    private static final String RAW = "{\"answers\":{\"repeat\":{\"noul\":0.95}}}";
    private static final String PUBLISHED = "{\"repeatContactProbability\":0.95}";
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {" {\"repeatContactProbability\": 0} \n", "{\"repeatContactProbability\":1}",
            "{\"repeatContactProbability\":0.95000000000000000000000000000001}"})
    void schemaOnlyPublishesExactValidatedText(String raw) throws Exception {
        write("triage", executor("triage") + SCHEMA);
        var observed = new ObservedExecutor(call -> raw);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("default-triage", Map.of())).isEqualTo(raw);
            assertOutcome(observed, OutputSchemaOutcomeStatus.PASSED);
            assertThat(observed.calls).hasSize(1);
            assertThat(completion(observed.records()).data().path("executorOutput").textValue()).isEqualTo(raw);
            assertThat(observed.records()).anyMatch(record -> record.data() != null && record.data().has("skillOutput")
                    && raw.equals(record.data().path("skillOutput").textValue()));
        });
    }

    @Test void validatesTransformedObjectInsteadOfRawExecutorShape() throws Exception {
        write("triage", executor("triage") + transform(TRANSFORM) + SCHEMA);
        var observed = new ObservedExecutor(call -> RAW);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("default-triage", Map.of())).isEqualTo(PUBLISHED);
            assertOutcome(observed, OutputSchemaOutcomeStatus.PASSED);
            assertThat(completion(observed.records()).data().path("executorOutput").textValue()).isEqualTo(RAW);
            assertThat(completion(observed.records()).data().path("outputTransformStatus").textValue()).isEqualTo("succeeded");
        });
    }

    @Test void sharedExecutorLeavesOwnDifferentResultContracts() throws Exception {
        write("accepted", executor("accepted") + SCHEMA);
        write("rejected", executor("rejected") + SCHEMA.replace("maximum: 1", "maximum: 0.5"));
        var observed = new ObservedExecutor(call -> PUBLISHED);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var skills = context.getBean(SkillTemplate.class);
            assertThat(skills.invoke("default-accepted", Map.of())).isEqualTo(PUBLISHED);
            assertThatThrownBy(() -> skills.invoke("default-rejected", Map.of()))
                    .hasStackTraceContaining("default-rejected").hasStackTraceContaining("maximum");
            assertThat(observed.calls).hasSize(2);
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            " |invalid_json", "plain|invalid_json", "{broken|invalid_json",
            "{} {}|invalid_json", "{} trailing|invalid_json", "0.95|type", "[]|type", "null|type",
            "{\"repeatContactProbability\":\"high\"}|type", "{\"repeatContactProbability\":-0.01}|minimum",
            "{\"repeatContactProbability\":1.01}|maximum", "{\"repeatContactProbability\":null}|type",
            "{}|required", "{\"misspelled\":0.95}|required",
            "{\"repeatContactProbability\":0.95,\"extra\":true}|additionalProperties"
    }, delimiter = '|', emptyValue = "")
    void invalidCandidatesFailOnceWithLocationAndConstraint(String raw, String constraint) throws Exception {
        write("triage", executor("triage") + SCHEMA);
        var observed = new ObservedExecutor(call -> raw == null ? "" : raw);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var failure = catchThrowable(() -> context.getBean(SkillTemplate.class).invoke("default-triage", Map.of()));
            assertThat(failure).isInstanceOf(SkillException.class).hasStackTraceContaining("default-triage");
            var validation = schemaFailure(failure);
            assertThat(validation.getRawOutput()).isEqualTo(raw == null ? "" : raw);
            assertThat(validation.getValidationIssues()).anySatisfy(issue -> {
                assertThat(issue.code()).isEqualTo(constraint);
                assertThat(issue.path()).isNotBlank();
            });
            assertOutcome(observed, OutputSchemaOutcomeStatus.EXHAUSTED);
            assertThat(observed.calls).hasSize(1);
            assertThat(observed.records()).filteredOn(record -> record.recordType() == TraceRecordType.STRUCTURED_OUTPUT_RECORDED)
                    .hasSize(1);
            assertThat(observed.records()).noneMatch(record -> record.data() != null && record.data().has("skillOutput"));
            assertThat(observed.records().getLast().metadata()).containsEntry("outcome", "FAILED").containsKey("terminalFailureId");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"high\"", "1.1", "null"})
    void transformedInvalidValuesRetainRawEvidenceWithoutPublishing(String value) throws Exception {
        write("triage", executor("triage") + transform(TRANSFORM) + SCHEMA);
        String raw = value.equals("null") ? "{\"answers\":{}}" : "{\"answers\":{\"repeat\":{\"noul\":" + value + "}}}";
        var observed = new ObservedExecutor(call -> raw);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var failure = catchThrowable(() -> context.getBean(SkillTemplate.class).invoke("default-triage", Map.of()));
            assertThat(schemaFailure(failure).getRawOutput()).isEqualTo("{\"repeatContactProbability\":" + value + "}");
            assertOutcome(observed, OutputSchemaOutcomeStatus.EXHAUSTED);
            var metadata = completion(observed.records());
            assertThat(metadata.data().path("executorOutput").textValue()).isEqualTo(raw);
            assertThat(metadata.data().path("outputTransformStatus").textValue()).isEqualTo("succeeded");
            assertThat(observed.records()).noneMatch(record -> record.data() != null && record.data().has("skillOutput"));
        });
    }

    @Test void omittedPublishedKeyFailsRequired() throws Exception {
        write("triage", executor("triage") + transform("{\"misspelled\": .answers.repeat.noul}") + SCHEMA);
        var observed = new ObservedExecutor(call -> RAW);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var failure = catchThrowable(() -> context.getBean(SkillTemplate.class).invoke("default-triage", Map.of()));
            assertThat(schemaFailure(failure).getValidationIssues()).anyMatch(issue -> "required".equals(issue.code()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "[1,null]"})
    void transformOnlyAllowsOtherJsonRootsWithoutSchemaOutcome(String value) throws Exception {
        write("triage", executor("triage") + transform("."));
        var observed = new ObservedExecutor(call -> value);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("default-triage", Map.of())).isEqualTo(value);
            assertThat(observed.sessions.getLast().getLastOutputSchemaOutcome()).isEmpty();
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void diagnosticFaultsPreserveSchemaAcceptanceAndMismatch(boolean valid) throws Exception {
        write("triage", executor("triage") + SCHEMA);
        var observed = new ObservedExecutor(call -> {
            ExecutorDiagnosticTestSupport.failExecutorCompletionRecord(ExecutionBindingScope.requireCurrent().session());
            ExecutorDiagnosticTestSupport.failOutputSchemaOutcomeRecord(ExecutionBindingScope.requireCurrent().session());
            return valid ? PUBLISHED : "{}";
        });
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var skills = context.getBean(SkillTemplate.class);
            if (valid) assertThat(skills.invoke("default-triage", Map.of())).isEqualTo(PUBLISHED);
            else assertThat(schemaFailure(catchThrowable(() -> skills.invoke("default-triage", Map.of()))))
                    .isNotNull();
            assertThat(observed.calls).hasSize(1);
        });
    }

    @Test void schemaAcceptanceWorksWithTracePersistenceDisabled() throws Exception {
        write("triage", executor("triage") + SCHEMA);
        var observed = new ObservedExecutor(call -> PUBLISHED);
        runner(observed).withPropertyValues("loomspan.execution-trace.persistence=never").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("default-triage", Map.of())).isEqualTo(PUBLISHED);
            assertOutcome(observed, OutputSchemaOutcomeStatus.PASSED);
        });
    }

    @Test void executorAndTransformFailuresSkipSchemaValidation() throws Exception {
        write("executorFailure", executor("executorFailure") + SCHEMA);
        write("transformFailure", executor("transformFailure") + transform("error(\"original transform failure\")") + SCHEMA);
        var original = new RecoverableSkillException("original executor failure");
        var observed = new ObservedExecutor(call -> {
            if (call.skillName().endsWith("executorFailure")) throw original;
            return RAW;
        });
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var skills = context.getBean(SkillTemplate.class);
            assertThatThrownBy(() -> skills.invoke("default-executorFailure", Map.of())).isSameAs(original);
            assertThatThrownBy(() -> skills.invoke("default-transformFailure", Map.of()))
                    .hasStackTraceContaining("original transform failure");
            assertThat(observed.sessions).allSatisfy(session -> assertThat(session.getLastOutputSchemaOutcome()).isEmpty());
        });
    }

    @Test void allCallersReceiveTheValidatedCandidate() throws Exception {
        write("triage", executor("triage") + transform(TRANSFORM) + SCHEMA);
        write("forward", planned("forward", 1) + children(false) + "output_from: {skill: default-triage}\n");
        write("ordinary", ExplicitJavaFailureHandlingIntegrationTest.parent("ordinary", "default-triage", false));
        var observed = new ObservedExecutor(call -> RAW);
        try (var provider = new MockWebServer()) {
            provider.enqueue(plan("forward", task("triage")));
            provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.tool("default-triage"));
            provider.enqueue(ResponsesFixtures.text("ordinary done"));
            provider.start();
            withProvider(runner(observed), provider).run(context -> {
                assertThat(context).hasNotFailed();
                var skills = context.getBean(SkillTemplate.class);
                assertThat(skills.invoke("default-triage", Map.of())).isEqualTo(PUBLISHED);
                assertThat(skills.invoke("default-forward", Map.of())).isEqualTo(PUBLISHED);
                assertThat(skills.invoke("default-ordinary", Map.of())).isEqualTo("ordinary done");
                assertThat(observed.calls).hasSize(3);
                assertThat(observed.records()).filteredOn(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED)
                        .singleElement().satisfies(record -> assertThat(record.data().at("/details/result").textValue()).isEqualTo(PUBLISHED));
            });
            assertThat(provider.getRequestCount()).isEqualTo(3);
            provider.takeRequest(5, TimeUnit.SECONDS);
            provider.takeRequest(5, TimeUnit.SECONDS);
            var transcript = ResponsesFixtures.transcript(JSON.readTree(provider.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8()));
            assertThat(StreamSupport.stream(transcript.spliterator(), false).filter(item -> "tool".equals(item.path("role").textValue())))
                    .singleElement().satisfies(item -> assertThat(item.path("content").textValue()).isEqualTo(PUBLISHED));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"direct", "ordinary", "planned"})
    void schemaMismatchIsTerminalAcrossCallerRoutes(String route) throws Exception {
        write("triage", executor("triage") + SCHEMA);
        boolean direct = route.equals("direct");
        if (route.equals("planned")) write("planned", planned("planned", 1) + children(false) + "output_from: {skill: default-triage}\n");
        if (route.equals("ordinary")) write("ordinary", ExplicitJavaFailureHandlingIntegrationTest.parent("ordinary", "default-triage", false));
        var observed = new ObservedExecutor(call -> "{\"repeatContactProbability\":\"high\"}");
        try (var provider = new MockWebServer()) {
            provider.enqueue(route.equals("planned") ? plan("planned", task("triage")) : ExplicitJavaFailureHandlingIntegrationTest.tool("default-triage"));
            provider.start();
            withProvider(runner(observed), provider).run(context -> {
                assertThat(context).hasNotFailed();
                var events = new ArrayList<SkillExecutionView>();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke(direct ? "default-triage" : "default-" + route, Map.of(), events::add))
                        .isInstanceOf(SkillException.class).hasStackTraceContaining("default-triage");
                assertThat(observed.calls).hasSize(1);
                assertThat(events).singleElement().satisfies(view -> assertThat(view.events()).anySatisfy(event -> {
                    assertThat(event.type()).isEqualTo("SKILL_FINISHED");
                    assertThat(event.details()).containsEntry("status", "failed");
                }));
                assertOutcome(observed, OutputSchemaOutcomeStatus.EXHAUSTED);
                var records = observed.records();
                assertThat(records).noneMatch(record -> record.recordType() == TraceRecordType.TOOL_CALL_COMPLETED);
                assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.ERROR_RECORDED && "default-triage".equals(record.route())).hasSize(1);
                assertThat(records.getLast().metadata()).containsEntry("outcome", "FAILED").containsKey("terminalFailureId");
            });
            assertThat(provider.getRequestCount()).isEqualTo(direct ? 0 : 1);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void dependentChildOnlyConsumesValidProducerResults(boolean valid) throws Exception {
        write("triage", executor("triage") + transform(TRANSFORM) + SCHEMA);
        write("observe", executor("observe") + """
                input_schema:
                  type: object
                  properties: {probability: {type: number}}
                  required: [probability]
                  additionalProperties: false
                """);
        write("assemble", planned("assemble", 2) + children(true) + "output_from: {skill: default-observe}\n");
        var observed = new ObservedExecutor(call -> call.skillName().equals("default-triage") ? (valid ? RAW : "{}") : "observed");
        try (var provider = new MockWebServer()) {
            provider.enqueue(plan("assemble", task("triage"), task("observe", "triage")));
            provider.start();
            withProvider(runner(observed), provider).run(context -> {
                assertThat(context).hasNotFailed();
                var skills = context.getBean(SkillTemplate.class);
                if (valid) {
                    assertThat(skills.invoke("default-assemble", Map.of())).isEqualTo("observed");
                    assertThat(observed.calls).hasSize(2);
                    assertThat(observed.calls.getLast().input()).isEqualTo(Map.of("probability", new BigDecimal("0.95")));
                } else {
                    assertThatThrownBy(() -> skills.invoke("default-assemble", Map.of())).hasStackTraceContaining("default-triage");
                    assertThat(observed.calls).extracting(SkillExecutorInvocation::skillName).containsExactly("default-triage");
                }
            });
            assertThat(provider.getRequestCount()).isEqualTo(1);
        }
    }

    @Test void retainedInvocationUsesCapturedSchemaAndInvalidPreparationPreservesActiveState() {
        var observed = new ObservedExecutor(call -> PUBLISHED);
        runner(observed).run(context -> {
            assertThat(context).hasNotFailed();
            var reloader = context.getBean(SkillReloader.class);
            var config = new ExecutionConfiguration("loomspan: {}");
            reloader.publish(reloader.prepare("default", List.of(), config, NamespaceVisibility.PUBLIC));
            var old = reloader.prepare("work", List.of(new SkillDocument("leaf.yaml", executor("leaf") + SCHEMA)), config);
            reloader.publish(old);
            var retained = context.getBean(SkillInvocationHandoff.class).handoff("work-leaf", Map.of());
            try {
                var replacement = reloader.prepare("work", List.of(new SkillDocument("leaf.yaml", executor("leaf") + SCHEMA.replace("maximum: 1", "maximum: 0.5"))), config);
                reloader.publish(replacement);
                for (String invalid : List.of("{type: string}", "{type: array, items: {type: string}}",
                        "{type: object, properties: {value: {type: number, x-loomspan-evidence: child}}}"))
                    assertThatThrownBy(() -> reloader.prepare("work", List.of(new SkillDocument("invalid.yaml", executor("leaf") + "output_schema: " + invalid)), config))
                            .isInstanceOf(SkillReloadException.class).hasMessageContaining("output_schema");
                assertThat(observed.calls).isEmpty();
                assertThat(reloader.snapshot("work").generationId()).isEqualTo(replacement.generationId());
                assertThat(retained.generationId()).isEqualTo(old.generationId());
                assertThat(retained.invoke()).isEqualTo(PUBLISHED);
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("work-leaf", Map.of()))
                        .hasStackTraceContaining("maximum");
                assertThat(observed.calls).hasSize(2);
            } finally { retained.release(); }
        });
    }

    @Test void startupRejectsInvalidExecutorSchema() throws Exception {
        write("invalid", executor("invalid") + "output_schema: {type: array, items: {type: number}}\n");
        runner(new ObservedExecutor(call -> PUBLISHED)).run(context -> assertThat(context).hasFailed());
    }

    private static LoomspanOutputSchemaValidationException schemaFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause())
            if (current instanceof LoomspanOutputSchemaValidationException validation) return validation;
        throw new AssertionError("Missing schema validation failure", failure);
    }

    private static void assertOutcome(ObservedExecutor observed, OutputSchemaOutcomeStatus status) {
        var outcome = observed.sessions.getLast().getLastOutputSchemaOutcome().orElseThrow();
        assertThat(outcome.skillName()).endsWith("triage");
        assertThat(outcome.status()).isEqualTo(status);
        assertThat(outcome.attempt()).isEqualTo(1);
        assertThat(outcome.retryCount()).isZero();
        assertThat(outcome.maxRetries()).isZero();
    }

    private static TraceRecord completion(List<TraceRecord> records) {
        var matches = records.stream().filter(record -> record.recordType() == TraceRecordType.FRAME_METADATA_RECORDED && record.data().has("executorOutput")).toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private void write(String name, String yaml) throws Exception { Files.writeString(directory.resolve(name + ".yaml"), yaml); }
    private static String executor(String name) { return "name: " + name + "\ndescription: Publish validated output\nexecutor: default-fixture\n"
            + (name.equals("triage") ? "input_schema: {type: object, additionalProperties: false}\n" : ""); }
    private static String transform(String expression) { return "output_transform:\n  language: jslt\n  expression: |\n" + expression.indent(4); }
    private static String planned(String name, int steps) { return "name: " + name + "\ndescription: Consume validated output\nmodel: local\nplanning_mode: true\nmax_steps: " + steps + "\n"; }
    private static String children(boolean dependent) {
        return "allowed_skills:\n  - name: default-triage\n    required: true\n    max_tasks: 1\n" + (dependent ? """
                  - name: default-observe
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /probability: {from: child_result, skill: default-triage, path: /repeatContactProbability}
                """ : "");
    }
    private static Map<String, Object> task(String name, String... dependencies) {
        var task = new LinkedHashMap<>(ExplicitJavaFailureHandlingIntegrationTest.task("default-" + name));
        task.put("taskId", name);
        task.put("dependsOn", List.of(dependencies));
        return task;
    }
    private static MockResponse plan(String parent, Object... tasks) {
        return ResponsesFixtures.text(JSON.writeValueAsString(Map.of("capabilityName", "default-" + parent,
                "createdAt", "2026-10-10T00:00:00Z", "status", "VALID", "tasks", List.of(tasks))));
    }
    private ApplicationContextRunner runner(ObservedExecutor observed) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                LoomspanJacksonAutoConfiguration.class, LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml", "loomspan.session.mission-timeout=20s",
                        "loomspan.execution-trace.persistence=always")
                .withBean(FixtureExecutor.class, () -> new FixtureExecutor(observed::invoke));
    }
    private static ApplicationContextRunner withProvider(ApplicationContextRunner runner, MockWebServer provider) {
        return runner.withPropertyValues("loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + provider.url("/v1"),
                "loomspan.connections.local.api-key=fixture", "loomspan.connections.local.provider-retry.enabled=false",
                "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=planner");
    }
    private static class ObservedExecutor {
        final Function<SkillExecutorInvocation, String> result;
        final List<SkillExecutorInvocation> calls = new CopyOnWriteArrayList<>();
        final List<LoomspanSession> sessions = new CopyOnWriteArrayList<>();
        ObservedExecutor(Function<SkillExecutorInvocation, String> result) { this.result = result; }
        String invoke(SkillExecutorInvocation call) {
            calls.add(call);
            sessions.add(ExecutionBindingScope.requireCurrent().session());
            return result.apply(call);
        }
        List<TraceRecord> records() { return ExecutorDiagnosticTestSupport.logicalRecords(sessions.getLast()); }
    }
}
