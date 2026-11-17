package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.testkit.ResponsesFixtures;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real facade/native-tool execution; executor transport is application owned, without Console. */
class ExecutorAccountingIntegrationTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void mixedMissionCountsOnlyNativeCallsUnderExactAttemptQuota() throws Exception {
        mixedMission(false);
    }

    @Test void missingNativeReportedUsageRetainsHeuristicEvidenceBesideExecutorUsage() throws Exception {
        mixedMission(true);
    }

    private void mixedMission(boolean unavailable) throws Exception {
        try (var application = new MockWebServer(); var nativeProvider = new MockWebServer()) {
            application.enqueue(new MockResponse().setResponseCode(503).setBody("retry me"));
            application.enqueue(new MockResponse().setBody("{\"result\":\"approved\"}"));
            application.start(); nativeProvider.start();
            var bean = new Executor(application, false);
            Files.writeString(directory.resolve("parent.yaml"), "name: parent\ndescription: Native parent\nmodel: local\nallowed_skills: [{name: default-leaf}]\n");
            nativeProvider.enqueue(nativeResponse(List.of(ResponsesFixtures.functionCall("default-leaf", "child", "{}")), unavailable));
            nativeProvider.enqueue(nativeResponse(List.of(ResponsesFixtures.textItem("DONE")), false));
            runner(bean).withPropertyValues("loomspan.connections.local.driver=openai",
                    "loomspan.connections.local.base-url=" + nativeProvider.url("/v1"),
                    "loomspan.connections.local.api-key=fixture", "loomspan.connections.local.provider-retry.enabled=false",
                    "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=fixture",
                    "loomspan.session.quotas.max-provider-attempts=2").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SkillTemplate.class).invoke("default-parent", Map.of())).isEqualTo("DONE");
                var usage = bean.session.getSessionUsage().orElseThrow();
                assertThat(usage.providerAttempts()).isEqualTo(2);
                assertThat(usage.modelCalls()).isEqualTo(2);
                if (unavailable) assertThat(usage.promptUnits()).isGreaterThan(3);
                else assertThat(usage.promptUnits()).isEqualTo(6);
                assertThat(usage.completionUnits()).isEqualTo(unavailable ? 2 : 4);
                if (unavailable) assertThat(usage.usageUnits()).isEqualTo(usage.promptUnits() + usage.completionUnits());
                else assertThat(usage.usageUnits()).isEqualTo(10);
                assertThat(usage.unavailableModelResponses()).isZero();
                assertThat(usage.heuristicModelResponses()).isEqualTo(unavailable ? 1 : 0);
                assertThat(usage.exactModelResponses()).isEqualTo(unavailable ? 1 : 2);
                var records = bean.records();
                assertThat(records).filteredOn(r -> r.recordType() == TraceRecordType.MODEL_REQUEST_SENT).hasSize(2);
                assertThat(records).noneMatch(r -> r.recordType() == TraceRecordType.ERROR_RECORDED);
                assertThat(records.getLast().metadata()).containsEntry("outcome", "SUCCEEDED");
                assertDiagnostics(bean);
                if (unavailable) assertThat(records).anySatisfy(r -> {
                    assertThat(r.recordType()).isEqualTo(TraceRecordType.MODEL_RESPONSE_RECEIVED);
                    assertThat(r.metadata().toString()).contains("HEURISTIC");
                });
            });
            assertThat(nativeProvider.getRequestCount()).isEqualTo(2);
            assertThat(application.getRequestCount()).isEqualTo(2);
        }
    }

    @Test void executorOnlyHasZeroNativeAccountingAndRetainsUnknownUsage() throws Exception {
        try (var application = new MockWebServer()) {
            application.enqueue(new MockResponse().setResponseCode(503));
            application.enqueue(new MockResponse().setBody("{\"result\":\"approved\"}")); application.start();
            var bean = new Executor(application, false);
            runner(bean).withPropertyValues("loomspan.session.quotas.max-provider-attempts=1").run(context -> {
                assertThat(context.getBean(SkillTemplate.class).invoke("default-leaf", Map.of())).isEqualTo("approved");
                var usage = bean.session.getSessionUsage().orElseThrow();
                assertThat(usage.providerAttempts()).isZero(); assertThat(usage.modelCalls()).isZero();
                assertThat(usage.usageUnits()).isZero(); assertThat(usage.unavailableModelResponses()).isZero();
                assertDiagnostics(bean);
            });
            assertThat(application.getRequestCount()).isEqualTo(2);
        }
    }

    @Test void successfulHttpThenDecodeFailureRetainsUnknownUsageAndDesignatedFailure() throws Exception {
        try (var application = new MockWebServer()) {
            application.enqueue(new MockResponse().setBody("not JSON 雪")); application.start();
            var bean = new Executor(application, true);
            runner(bean).run(context -> {
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-leaf", Map.of()))
                        .isSameAs(bean.designated);
                var records = bean.records();
                assertThat(records).anySatisfy(r -> {
                    assertThat(r.recordType()).isEqualTo(TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED);
                    assertThat(r.data().path("message").asText()).isEqualTo("HTTP response could not be decoded");
                    assertThat(r.data().at("/data/httpStatus").asInt()).isEqualTo(200);
                    assertThat(r.data().at("/data/usage").isNull()).isTrue();
                });
                assertThat(records).anySatisfy(r -> {
                    assertThat(r.recordType()).isEqualTo(TraceRecordType.ERROR_RECORDED);
                    assertThat(r.data().path("classification").asText()).isEqualTo("RECOVERABLE");
                    assertThat(r.data().path("exceptionMessage").asText()).isEqualTo("Invalid application response");
                });
                assertThat(bean.session.getSessionUsage().orElseThrow().providerAttempts()).isZero();
                assertThat(bean.session.getSessionUsage().orElseThrow().usageUnits()).isZero();
            });
            assertThat(application.getRequestCount()).isOne();
        }
    }

    private static void assertDiagnostics(Executor bean) {
        var diagnostics = bean.records().stream().filter(r -> r.recordType() == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED).toList();
        assertThat(diagnostics).hasSize(3);
        assertThat(diagnostics.getFirst().data().at("/data/usage/totalUnits").asInt()).isEqualTo(999999);
        assertThat(diagnostics.get(1).data().path("severity").asText()).isEqualTo("ERROR");
        assertThat(diagnostics.getLast().data().at("/data/usage").isNull()).isTrue();
        assertThat(diagnostics.getLast().data().at("/data/outcome").asText()).isEqualTo("FAILED");
    }

    private ApplicationContextRunner runner(Executor bean) throws Exception {
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Non AI application request\nexecutor: execute\n");
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withBean(Executor.class, () -> bean).withPropertyValues(
                        "loomspan.skills.locations=" + directory.toUri() + "*.yaml", "loomspan.execution-trace.persistence=always");
    }

    private static MockResponse nativeResponse(List<?> output, boolean unavailable) {
        var body = JSON.readTree(ResponsesFixtures.body("native", "fixture", output));
        var object = (tools.jackson.databind.node.ObjectNode) body;
        if (unavailable) object.remove("usage");
        else object.set("usage", JSON.valueToTree(Map.of("input_tokens", 3, "output_tokens", 2, "total_tokens", 5)));
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(body));
    }

    public static class Executor {
        final MockWebServer application; final boolean decodeFailure;
        final RecoverableSkillException designated = new RecoverableSkillException("Invalid application response");
        volatile LoomspanSession session;
        Executor(MockWebServer application, boolean decodeFailure) { this.application = application; this.decodeFailure = decodeFailure; }
        @SkillExecutor(name = "execute") public String execute(SkillExecutorInvocation call) throws Exception {
            session = ExecutionBindingScope.requireCurrent().session();
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                if (!decodeFailure) call.diagnostics().info("application request", Map.of("usage", Map.of("totalUnits", 999999)));
                var response = client.send(HttpRequest.newBuilder(application.url("/operation").uri()).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 503) {
                    call.diagnostics().error("caught request failure; retry", Map.of("httpStatus", 503, "retry", true));
                    response = client.send(HttpRequest.newBuilder(application.url("/operation").uri()).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                }
                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("httpStatus", response.statusCode()); evidence.put("usage", null); evidence.put("outcome", "FAILED");
                try {
                    String result = JSON.readTree(response.body()).path("result").asText();
                    call.diagnostics().info("application response", evidence); return result;
                } catch (RuntimeException ex) {
                    call.diagnostics().error("HTTP response could not be decoded", evidence); throw designated;
                }
            }
        }
        List<TraceRecord> records() { return ExecutorDiagnosticTestSupport.logicalRecords(session); }
    }
}
