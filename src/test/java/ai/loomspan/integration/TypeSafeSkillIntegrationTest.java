package ai.loomspan.integration;

import static org.assertj.core.api.Assertions.*;

import ai.loomspan.api.*;
import ai.loomspan.internal.skill.SkillGenerationManager;

import okhttp3.mockwebserver.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Public facade, actual native HTTP and captured-generation evidence. */
class TypeSafeSkillIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    private final List<String> sessionIds = new CopyOnWriteArrayList<>();

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    private String leaf() {
        return """
               name: judgment
               description: Evaluate a mixed batch
               model: selected
               input_schema:
                 type: object
                 additionalProperties: true
               typesafe:
                 state: ${/input}
                 questions:
                   classification:
                     type: choice
                     criteria: ${/input/candidates}
                   severity:
                     type: score
                     criteria: [null, {description: high}]
                   escalation:
                     type: noul
                     criteria: {'true': [requested]}
               """;
    }

    private ApplicationContextRunner runner(MockWebServer server, String prefix, String document)
            throws Exception {
        Path skills = Files.createDirectories(directory.resolve(UUID.randomUUID().toString()));
        Files.writeString(skills.resolve("judgment.yaml"), document);
        return new ApplicationContextRunner()
                .withUserConfiguration(App.class)
                .withPropertyValues(
                        "spring.main.web-application-type=none",
                        "loomspan.skills.locations=" + skills.toUri() + "*.yaml",
                        "loomspan.connections.native.driver=typesafe",
                        "loomspan.connections.native.base-url=" + server.url(prefix),
                        "loomspan.connections.native.api-key=fixture-account-key",
                        "loomspan.connections.native.headers.X-Account=fixture-account",
                        "loomspan.connections.native.provider-retry.enabled=false",
                        "loomspan.models.selected.connection=native",
                        "loomspan.models.selected.provider-model=typesafe/jev-verbatim",
                        "loomspan.execution-trace.persistence=always",
                        "loomspan.session.mission-timeout=10s");
    }

    private static MockResponse success(String envelope) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(envelope);
    }

    private static Map<String, Object> input(String candidate) {
        return Map.of("caseId", candidate, "candidates", Map.of(candidate, List.of("description")));
    }

    private String invoke(SkillTemplate template, Map<String, Object> input) {
        return template.invoke("judgment", input, view -> sessionIds.add(view.sessionId()));
    }

    private List<JsonNode> traces(org.springframework.context.ApplicationContext context)
            throws Exception {
        List<JsonNode> records = new ArrayList<>();
        Path temp = Path.of(System.getProperty("java.io.tmpdir"));
        for (String id : sessionIds)
            try (var files = Files.newDirectoryStream(temp, id + ".*.execution-trace.ndjson")) {
                for (Path file : files)
                    for (String line : Files.readAllLines(file))
                        if (!line.isBlank()) records.add(JSON.readTree(line));
            }
        return records;
    }

    private static List<JsonNode> records(List<JsonNode> trace, String type) {
        return trace.stream().filter(r -> r.path("recordType").asText().equals(type)).toList();
    }

    private static JsonNode usage(List<JsonNode> trace) {
        return records(trace, "TRACE_COMPLETED")
                .getFirst()
                .path("metadata")
                .path("sessionUsageSnapshot");
    }

    private static JsonNode logicalData(List<JsonNode> trace, JsonNode record) throws Exception {
        if (!record.at("/metadata/payloadChunked").asBoolean()) return record.path("data");
        var chunks = records(trace, "PAYLOAD_CHUNK_APPENDED").stream()
                .filter(chunk -> chunk.at("/metadata/payloadId").equals(record.at("/metadata/payloadId")))
                .sorted(Comparator.comparingInt(chunk -> chunk.at("/metadata/chunkIndex").intValue())).toList();
        assertThat(chunks).hasSize(record.at("/metadata/chunkCount").intValue());
        var content = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).at("/metadata/chunkIndex").intValue()).isEqualTo(i);
            content.append(chunks.get(i).path("data").asText());
        }
        return JSON.readTree(content.toString());
    }

    private static java.util.stream.Stream<Arguments> nativeTerminalCases() {
        return java.util.stream.Stream.of("/", "/api/").flatMap(prefix ->
                java.util.stream.Stream.of(400, 401, 403, 404, 302).map(status -> Arguments.of(prefix, status)));
    }

    @ParameterizedTest
    @MethodSource("nativeTerminalCases")
    void nativeTerminalHttpErrorsDoNotRetryOrAccountErrorUsage(String prefix, int status) throws Exception {
        assertNativeHttpFailure(prefix, status, true, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/"})
    void disabledNativeRetryMakesOneSend(String prefix) throws Exception {
        assertNativeHttpFailure(prefix, 529, false, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/"})
    void nativeRetryExhaustionStopsAtConfiguredAttempts(String prefix) throws Exception {
        assertNativeHttpFailure(prefix, 529, true, 2);
    }

    private void assertNativeHttpFailure(String prefix, int status, boolean enabled, int attempts) throws Exception {
        String error = "{\"usage\":{\"input_tokens\":2,\"output_tokens\":3},\"token\":\"business evidence\"}";
        try (MockWebServer server = new MockWebServer()) {
            for (int i = 0; i < attempts; i++) server.enqueue(new MockResponse().setResponseCode(status)
                    .setHeader("Location", server.url("/follow-up")).setBody(error));
            server.enqueue(success("{\"answers\":{\"sentinel\":true}}"));
            runner(server, prefix, leaf()).withPropertyValues(
                    "loomspan.connections.native.provider-retry.enabled=" + enabled,
                    "loomspan.connections.native.provider-retry.max-attempts=2",
                    "loomspan.connections.native.provider-retry.initial-backoff=0ms",
                    "loomspan.connections.native.provider-retry.max-backoff=0ms",
                    "loomspan.connections.native.provider-retry.jitter=0").run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> invoke(context.getBean(SkillTemplate.class), input("A")))
                        .isInstanceOf(SkillException.class).hasStackTraceContaining("HTTP " + status);
                var trace = traces(context);
                var requests = records(trace, "MODEL_REQUEST_SENT");
                var failures = records(trace, "MODEL_ATTEMPT_FAILED");
                assertThat(requests).hasSize(attempts);
                assertThat(failures).hasSize(attempts);
                assertThat(records(trace, "MODEL_RESPONSE_RECEIVED")).isEmpty();
                for (int i = 0; i < attempts; i++) {
                    var failure = failures.get(i);
                    assertThat(failure.at("/metadata/httpStatus").intValue()).isEqualTo(status);
                    assertThat(failure.at("/metadata/failureClassification").asText())
                            .isEqualTo(status == 529 ? "TRANSIENT" : "PERMANENT");
                    String category = switch (status) {
                        case 401 -> "AUTHENTICATION";
                        case 403 -> "AUTHORIZATION";
                        case 529 -> "PROVIDER_OVERLOADED";
                        default -> "INVALID_REQUEST";
                    };
                    assertThat(failure.at("/metadata/failureCategory").asText()).isEqualTo(category);
                    assertThat(failure.at("/metadata/retryDecision").asText()).isEqualTo(
                            i < attempts - 1 ? "RETRY" : status == 529 && enabled ? "ATTEMPTS_EXHAUSTED" : "DO_NOT_RETRY");
                    assertThat(failure.at("/metadata/attemptId")).isEqualTo(requests.get(i).at("/metadata/attemptId"));
                    assertThat(failure.at("/metadata/providerAttemptNumber").intValue()).isEqualTo(i + 1);
                    assertThat(failure.at("/data/diagnostics/1/text").asText()).isEqualTo(error);
                    assertThat(requests.get(i).at("/data/request")).isEqualTo(requests.getFirst().at("/data/request"));
                }
                var counts = usage(trace);
                assertThat(counts.path("providerAttempts").intValue()).isEqualTo(attempts);
                for (String field : List.of("modelCalls", "promptUnits", "completionUnits", "totalUnits"))
                    assertThat(counts.path(field).intValue()).as(field).isZero();
                assertThat(records(trace, "TRACE_COMPLETED").getFirst().at("/metadata/outcome").asText())
                        .isEqualTo("FAILED");
            });
            assertThat(server.getRequestCount()).isEqualTo(attempts);
            String firstBody = null;
            for (int i = 0; i < attempts; i++) {
                var request = server.takeRequest(2, TimeUnit.SECONDS);
                assertThat(request.getMethod()).isEqualTo("POST");
                assertThat(request.getPath()).isEqualTo(prefix + "v1/systemone");
                String body = request.getBody().readUtf8();
                if (firstBody == null) firstBody = body;
                assertThat(body).isEqualTo(firstBody);
                assertThat(JSON.readTree(body).path("model").asText()).isEqualTo("typesafe/jev-verbatim");
            }
        }
    }

    private static java.util.stream.Stream<Arguments> nativeUsageCases() {
        return java.util.stream.Stream.of(
                Arguments.of("", false, 0, 0), Arguments.of("null", false, 0, 0),
                Arguments.of("42", false, 0, 0), Arguments.of("{}", false, 0, 0),
                Arguments.of("{\"input_tokens\":2}", false, 0, 0),
                Arguments.of("{\"input_tokens\":2,\"output_tokens\":3,\"cost\":0.25,\"extra\":{\"token\":\"kept\"}}", true, 2, 3),
                Arguments.of("{\"input_tokens\":0,\"output_tokens\":0}", true, 0, 0),
                Arguments.of("{\"input_tokens\":1.0,\"output_tokens\":2}", false, 0, 0),
                Arguments.of("{\"input_tokens\":1.5,\"output_tokens\":2}", false, 0, 0),
                Arguments.of("{\"input_tokens\":\"1\",\"output_tokens\":2}", false, 0, 0),
                Arguments.of("{\"input_tokens\":-1,\"output_tokens\":2}", false, 0, 0),
                Arguments.of("{\"input_tokens\":2147483648,\"output_tokens\":0}", false, 0, 0),
                Arguments.of("{\"input_tokens\":2147483647,\"output_tokens\":1}", false, 0, 0));
    }

    @ParameterizedTest
    @MethodSource("nativeUsageCases")
    void nativeUsagePrecisionAndResponseAccountingRemainExactOrUnavailable(
            String nativeUsage, boolean exact, int prompt, int completion) throws Exception {
        String envelope = "{\"answers\":{}" + (nativeUsage.isEmpty() ? "" : ",\"usage\":" + nativeUsage) + "}";
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(success(envelope));
            runner(server, "/api/", leaf()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(invoke(context.getBean(SkillTemplate.class), input("A"))).isEqualTo("{}");
                var trace = traces(context);
                assertThat(records(trace, "MODEL_REQUEST_SENT")).hasSize(1);
                assertThat(records(trace, "MODEL_RESPONSE_RECEIVED")).hasSize(1);
                var response = records(trace, "MODEL_RESPONSE_RECEIVED").getFirst();
                assertThat(response.at("/data/envelope")).isEqualTo(JSON.readTree(envelope));
                assertThat(response.at("/metadata/usage/precision").asText()).isEqualTo(exact ? "EXACT" : "UNAVAILABLE");
                var counts = usage(trace);
                assertThat(counts.path("providerAttempts").intValue()).isEqualTo(1);
                assertThat(counts.path("modelCalls").intValue()).isEqualTo(1);
                assertThat(counts.path("promptUnits").intValue()).isEqualTo(prompt);
                assertThat(counts.path("completionUnits").intValue()).isEqualTo(completion);
                assertThat(counts.path("totalUnits").intValue()).isEqualTo(prompt + completion);
                assertThat(counts.path("exactModelResponses").intValue()).isEqualTo(exact ? 1 : 0);
                assertThat(counts.path("unavailableModelResponses").intValue()).isEqualTo(exact ? 0 : 1);
                assertThat(counts.path("heuristicModelResponses").intValue()).isZero();
            });
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertThat(JSON.readTree(server.takeRequest().getBody().readUtf8()).path("questions").size()).isEqualTo(3);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/"})
    void oversizedNativeErrorRetainsBoundedTruthfulEvidenceOnBothRoutes(String prefix) throws Exception {
        String error = "distinctive-prefix:" + "é".repeat(600_000) + ":distinctive-suffix";
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(400).setBody(error));
            runner(server, prefix, leaf()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> invoke(context.getBean(SkillTemplate.class), input("A")))
                        .isInstanceOf(SkillException.class);
                var trace = traces(context);
                assertThat(records(trace, "MODEL_REQUEST_SENT")).hasSize(1);
                assertThat(records(trace, "MODEL_ATTEMPT_FAILED")).hasSize(1);
                var failure = records(trace, "MODEL_ATTEMPT_FAILED").getFirst();
                var diagnostic = logicalData(trace, failure).at("/diagnostics/1");
                assertThat(diagnostic.path("text").asText()).startsWith("distinctive-prefix:")
                        .doesNotContain("distinctive-suffix", "\uFFFD");
                assertThat(diagnostic.path("text").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                        .isLessThanOrEqualTo(1024 * 1024);
                assertThat(diagnostic.path("truncated").asBoolean()).isTrue();
                assertThat(diagnostic.path("captureLimitBytes").intValue()).isEqualTo(1024 * 1024);
                assertThat(failure.at("/metadata/httpStatus").intValue()).isEqualTo(400);
                assertThat(failure.at("/metadata/retryDecision").asText()).isEqualTo("DO_NOT_RETRY");
                var request = records(trace, "MODEL_REQUEST_SENT").getFirst();
                assertThat(request.at("/data/endpoint").asText()).endsWith(prefix + "v1/systemone");
                assertThat(failure.at("/metadata/attemptId")).isEqualTo(request.at("/metadata/attemptId"));
                assertThat(records(trace, "MODEL_RESPONSE_RECEIVED")).isEmpty();
                assertThat(usage(trace).path("providerAttempts").intValue()).isEqualTo(1);
                for (String field : List.of("modelCalls", "promptUnits", "completionUnits", "totalUnits"))
                    assertThat(usage(trace).path(field).intValue()).as(field).isZero();
            });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/"})
    void completeExtendedAndLargeAnswersPassThroughBothRoutePrefixes(String prefix)
            throws Exception {
        String large = "x".repeat(1024 * 1024 + 128);
        String answers =
                JSON.writeValueAsString(
                        Map.of(
                                "classification",
                                Map.of(
                                        "choice",
                                        "UNDECLARED",
                                        "confidence",
                                        -3,
                                        "extra",
                                        List.of(1, 2)),
                                "unexpected",
                                large,
                                "severity",
                                Map.of("legend", Map.of("0", List.of("structured")))));
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                    success(
                            "{\"answers\":"
                                    + answers
                                    + ",\"usage\":{\"input_tokens\":0,\"output_tokens\":0},\"model\":\"served\"}"));
            runner(server, prefix, leaf())
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(
                                                context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default")
                                                        .skill("judgment")
                                                        .orElseThrow()
                                                        .kind())
                                        .isEqualTo(SkillKind.TYPESAFE);
                                var entry =
                                        context.getBean(SkillGenerationManager.class)
                                                .active("default")
                                                .registeredSkillCatalog()
                                                .find("judgment")
                                                .orElseThrow();
                                assertThat(entry.source()).isEqualTo("TYPESAFE");
                                assertThat(entry.yaml()).isEqualTo(leaf());
                                assertThat(entry.sourcePath()).endsWith("judgment.yaml");
                                assertThat(entry.beanName()).isNull();
                                assertThat(entry.method()).isNull();
                                assertThat(entry.outputSchema())
                                        .isEqualTo(
                                                context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default")
                                                        .skill("judgment")
                                                        .orElseThrow()
                                                        .outputSchema());
                                String result =
                                        invoke(
                                                context.getBean(SkillTemplate.class),
                                                input("Original"));
                                assertThat(JSON.readTree(result)).isEqualTo(JSON.readTree(answers));
                                assertThat(result.length()).isGreaterThan(1024 * 1024);
                                var trace = traces(context);
                                assertThat(records(trace, "MODEL_REQUEST_SENT")).hasSize(1);
                                assertThat(records(trace, "MODEL_RESPONSE_RECEIVED")).hasSize(1);
                                assertThat(usage(trace).path("modelCalls").intValue()).isEqualTo(1);
                                assertThat(usage(trace).path("exactModelResponses").intValue())
                                        .isEqualTo(1);
                                assertThat(usage(trace).path("heuristicModelResponses").intValue())
                                        .isZero();
                            });
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request.getPath())
                    .isEqualTo(prefix.equals("/") ? "/v1/systemone" : "/api/v1/systemone");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer fixture-account-key");
            assertThat(request.getHeader("X-Account")).isEqualTo("fixture-account");
            var wire = JSON.readTree(request.getBody().readUtf8());
            assertThat(wire.path("model").asText()).isEqualTo("typesafe/jev-verbatim");
            assertThat(wire.at("/questions/severity/criteria/0").isNull()).isTrue();
            assertThat(wire.at("/questions/classification/criteria/Original").isArray()).isTrue();
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void invalidResolvedRequestsAndDeniedRolesConsumeNoProviderAttempt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            runner(server, "/", leaf())
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var template = context.getBean(SkillTemplate.class);
                                assertThatThrownBy(() -> invoke(template, Map.of()))
                                        .hasStackTraceContaining("missing input reference");
                                assertThatThrownBy(
                                                () ->
                                                        invoke(
                                                                template,
                                                                Map.of(
                                                                        "candidates",
                                                                        Map.of(
                                                                                "A", "yes", "a",
                                                                                "no"))))
                                        .hasStackTraceContaining("collides");
                                var trace = traces(context);
                                assertThat(records(trace, "MODEL_REQUEST_SENT")).isEmpty();
                                for (var completion : records(trace, "TRACE_COMPLETED"))
                                    assertThat(
                                                    completion
                                                            .at(
                                                                    "/metadata/sessionUsageSnapshot/providerAttempts")
                                                            .intValue())
                                            .isZero();
                            });
            runner(server, "/", leaf() + "rbac_roles: [ALLOWED]\n")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThatThrownBy(
                                                () ->
                                                        invoke(
                                                                context.getBean(
                                                                        SkillTemplate.class),
                                                                input("A")))
                                        .hasStackTraceContaining("denied");
                            });
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void overloadRetryUsesSameResolvedRequestAndAccountsOneSuccessfulResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(529).setBody("provider overloaded"));
            server.enqueue(
                    success("{\"answers\":{},\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}"));
            runner(server, "/", leaf())
                    .withPropertyValues(
                            "loomspan.connections.native.provider-retry.enabled=true",
                            "loomspan.connections.native.provider-retry.max-attempts=2",
                            "loomspan.connections.native.provider-retry.initial-backoff=0ms",
                            "loomspan.connections.native.provider-retry.max-backoff=0ms",
                            "loomspan.connections.native.provider-retry.jitter=0")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(invoke(context.getBean(SkillTemplate.class), input("A")))
                                        .isEqualTo("{}");
                                var trace = traces(context);
                                assertThat(records(trace, "MODEL_REQUEST_SENT")).hasSize(2);
                                assertThat(records(trace, "MODEL_ATTEMPT_FAILED")).hasSize(1);
                                assertThat(
                                                records(trace, "MODEL_ATTEMPT_FAILED")
                                                        .getFirst()
                                                        .at("/metadata/failureCategory")
                                                        .asText())
                                        .isEqualTo("PROVIDER_OVERLOADED");
                                var providerDiagnostic =
                                        records(trace, "MODEL_ATTEMPT_FAILED")
                                                .getFirst()
                                                .at("/data/diagnostics/1");
                                assertThat(providerDiagnostic.path("kind").asText())
                                        .isEqualTo("PROVIDER_ERROR");
                                assertThat(providerDiagnostic.path("text").asText())
                                        .isEqualTo("provider overloaded");
                                assertThat(providerDiagnostic.path("contentType").asText())
                                        .isNotBlank();
                                assertThat(providerDiagnostic.path("captureLimitBytes").intValue())
                                        .isEqualTo(1024 * 1024);
                                var counts = usage(trace);
                                assertThat(counts.path("providerAttempts").intValue()).isEqualTo(2);
                                assertThat(counts.path("modelCalls").intValue()).isEqualTo(1);
                                assertThat(counts.path("promptUnits").intValue()).isEqualTo(2);
                                assertThat(counts.path("completionUnits").intValue()).isEqualTo(3);
                                assertThat(counts.path("totalUnits").intValue()).isEqualTo(5);
                            });
            assertThat(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8())
                    .isEqualTo(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not JSON",
                "null",
                "[]",
                "42",
                "\"scalar\"",
                "{}",
                "{\"answers\":null}",
                "{\"answers\":[]}",
                "{\"answers\":\"text\"}"
            })
    void extractionFailuresAreTerminalButSuccessfulHttpUsageIsRecorded(String envelope)
            throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(success(envelope));
            runner(server, "/", leaf())
                    .withPropertyValues(
                            "loomspan.connections.native.provider-retry.enabled=true",
                            "loomspan.connections.native.provider-retry.max-attempts=3")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThatThrownBy(
                                                () ->
                                                        invoke(
                                                                context.getBean(
                                                                        SkillTemplate.class),
                                                                input("A")))
                                        .isInstanceOf(SkillException.class);
                                var trace = traces(context);
                                var response = records(trace, "MODEL_RESPONSE_RECEIVED").getFirst();
                                assertThat(response.at("/data/payloadType").asText())
                                        .isEqualTo("evaluation");
                                if (envelope.equals("not JSON"))
                                    assertThat(response.at("/data/rawBody").asText())
                                            .isEqualTo(envelope);
                                else
                                    assertThat(response.at("/data/envelope"))
                                            .isEqualTo(JSON.readTree(envelope));
                                var counts = usage(trace);
                                assertThat(counts.path("modelCalls").intValue()).isEqualTo(1);
                                assertThat(counts.path("unavailableModelResponses").intValue())
                                        .isEqualTo(1);
                                assertThat(counts.path("heuristicModelResponses").intValue())
                                        .isZero();
                            });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void extractionFailureRetainsExactSuccessfulHttpUsage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                    success(
                            "{\"answers\":null,\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}"));
            runner(server, "/", leaf())
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThatThrownBy(
                                                () ->
                                                        invoke(
                                                                context.getBean(
                                                                        SkillTemplate.class),
                                                                input("A")))
                                        .isInstanceOf(SkillException.class);
                                var counts = usage(traces(context));
                                assertThat(counts.path("modelCalls").intValue()).isEqualTo(1);
                                assertThat(counts.path("exactModelResponses").intValue())
                                        .isEqualTo(1);
                                assertThat(counts.path("promptUnits").intValue()).isEqualTo(2);
                                assertThat(counts.path("completionUnits").intValue()).isEqualTo(3);
                                assertThat(counts.path("totalUnits").intValue()).isEqualTo(5);
                            });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void providerAttemptQuotaPreventsRetrySend() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(529).setBody("overloaded"));
            runner(server, "/", leaf())
                    .withPropertyValues(
                            "loomspan.connections.native.provider-retry.enabled=true",
                            "loomspan.connections.native.provider-retry.max-attempts=3",
                            "loomspan.connections.native.provider-retry.initial-backoff=0ms",
                            "loomspan.connections.native.provider-retry.max-backoff=0ms",
                            "loomspan.session.quotas.max-provider-attempts=1")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThatThrownBy(
                                                () ->
                                                        invoke(
                                                                context.getBean(
                                                                        SkillTemplate.class),
                                                                input("A")))
                                        .hasStackTraceContaining("quota");
                            });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void concurrentDynamicCriteriaAndRunningGenerationStayIndependentAcrossReload()
            throws Exception {
        CountDownLatch arrived = new CountDownLatch(2), release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(
                    new Dispatcher() {
                        @Override
                        public MockResponse dispatch(RecordedRequest request) {
                            try {
                                var wire = JSON.readTree(request.getBody().readUtf8());
                                if (wire.get("state").isObject()) {
                                    arrived.countDown();
                                    if (!release.await(5, TimeUnit.SECONDS))
                                        throw new AssertionError("release missing");
                                }
                                return success(
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "answers",
                                                        Map.of(
                                                                "observed",
                                                                wire.get("state"),
                                                                "criteria",
                                                                wire.at(
                                                                        "/questions/classification/criteria")))));
                            } catch (Throwable error) {
                                failure.compareAndSet(null, error);
                                return new MockResponse().setResponseCode(500);
                            }
                        }
                    });
            runner(server, "/api", leaf())
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var template = context.getBean(SkillTemplate.class);
                                var oldSchema =
                                        context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default")
                                                .skill("judgment")
                                                .orElseThrow()
                                                .outputSchema();
                                var oldGeneration = context.getBean(SkillGenerationManager.class).active("default");
                                var oldClient = oldGeneration.runtime().connection("native").nativeClient();
                                var reloader = context.getBean(SkillReloader.class);
                                var retired = new CountDownLatch(1);
                                try (var listener = reloader.onGenerationRetired(id -> {
                                            if (id.equals(oldGeneration.id())) retired.countDown();
                                        });
                                        var threads = Executors.newFixedThreadPool(2)) {
                                    var first = threads.submit(() -> invoke(template, input("A")));
                                    var second = threads.submit(() -> invoke(template, input("B")));
                                    try {
                                        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
                                        reloader.publish(
                                                reloader.prepare("default", List.of(
                                                                new SkillDocument(
                                                                        " opaque | Source ",
                                                                        leaf().replace(
                                                                                        "state:"
                                                                                            + " ${/input}",
                                                                                        "state:"
                                                                                            + " 'new"
                                                                                            + " ${/input/caseId}'"))), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))));
                                        var supplied =
                                                context.getBean(SkillGenerationManager.class)
                                                        .active("default")
                                                        .registeredSkillCatalog()
                                                        .find("judgment")
                                                        .orElseThrow();
                                        assertThat(supplied.source()).isEqualTo("TYPESAFE");
                                        assertThat(supplied.sourcePath())
                                                .isEqualTo(" opaque | Source ");
                                        assertThat(supplied.beanName()).isNull();
                                        assertThat(supplied.method()).isNull();
                                        assertThat(supplied.yaml())
                                                .isEqualTo(
                                                        leaf().replace(
                                                                        "state: ${/input}",
                                                                        "state: 'new"
                                                                            + " ${/input/caseId}'"));
                                        assertThat(
                                                        JSON.readTree(invoke(template, input("C")))
                                                                .get("observed")
                                                                .asText())
                                                .isEqualTo("new C");
                                        assertThat(retired.getCount()).isEqualTo(1);
                                    } finally {
                                        release.countDown();
                                    }
                                    assertThat(
                                                    JSON.readTree(first.get(5, TimeUnit.SECONDS))
                                                            .at("/criteria/A")
                                                            .isArray())
                                            .isTrue();
                                    assertThat(
                                                    JSON.readTree(second.get(5, TimeUnit.SECONDS))
                                                            .at("/criteria/B")
                                                            .isArray())
                                            .isTrue();
                                    assertThat(
                                                    JSON.readTree(first.get())
                                                            .at("/observed/caseId")
                                                            .asText())
                                            .isEqualTo("A");
                                    assertThat(
                                                    JSON.readTree(second.get())
                                                            .at("/observed/caseId")
                                                            .asText())
                                            .isEqualTo("B");
                                    assertThat(
                                                    context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default")
                                                            .skill("judgment")
                                                            .orElseThrow()
                                                            .outputSchema())
                                            .isEqualTo(oldSchema);
                                    assertThat(retired.await(5, TimeUnit.SECONDS)).isTrue();
                                    assertThatThrownBy(() -> oldClient.send("{}", null))
                                            .hasStackTraceContaining("shut down");
                                }
                            });
            assertThat(failure.get()).isNull();
            assertThat(server.getRequestCount()).isEqualTo(3);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"typesafeDirect", "typesafeRouter"})
    void publishedTypeSafeExamplesExecuteDirectAndFullyBoundForwardingParent(String route) throws Exception {
        String docs =
                Files.readString(
                        Path.of(
                                "agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md"));
        var yamlMatcher =
                java.util.regex.Pattern.compile(
                                "```yaml\\R(.*?)```", java.util.regex.Pattern.DOTALL)
                        .matcher(docs);
        List<String> blocks = new ArrayList<>();
        while (yamlMatcher.find()) blocks.add(yamlMatcher.group(1));
        assertThat(blocks).hasSize(6);
        var yaml = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().skillYaml();
        JsonNode config = yaml.readTree(blocks.get(0));
        if (route.equals("typesafeRouter")) {
            ((tools.jackson.databind.node.ObjectNode) config.at("/loomspan/models"))
                    .set("typesafeDefault", yaml.readTree(blocks.get(1)).at("/loomspan/models/typesafeDefault"));
        }
        String requestedModel = route.equals("typesafeDirect") ? "jev-fixture-direct" : "typesafe/jev-fixture-router";
        ((tools.jackson.databind.node.ObjectNode) config.at("/loomspan/models/typesafeDefault"))
                .put("provider-model", requestedModel);
        var jsonMatcher =
                java.util.regex.Pattern.compile(
                                "```json\\R(.*?)```", java.util.regex.Pattern.DOTALL)
                        .matcher(docs);
        assertThat(jsonMatcher.find()).isTrue();
        String retained = JSON.writeValueAsString(JSON.readTree(jsonMatcher.group(1)));
        Map<String, Object> documentedInput =
                Map.of(
                        "context",
                        Map.of("message", "Again: checkout failed. Browsing works."),
                        "candidates",
                        Map.of("billing", "Charges", "technical", "Checkout failures"),
                        "levels",
                        List.of("All functions available", "A critical operation is blocked"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(
                    new Dispatcher() {
                        @Override
                        public MockResponse dispatch(RecordedRequest request) {
                            try {
                                JsonNode body = JSON.readTree(request.getBody().readUtf8());
                                if (request.getPath().endsWith("/v1/systemone")) {
                                    assertThat(request.getPath()).isEqualTo(route.equals("typesafeDirect") ? "/v1/systemone" : "/api/v1/systemone");
                                    assertThat(body.path("model").asText()).isEqualTo(requestedModel);
                                    assertThat(body.get("state"))
                                            .isEqualTo(
                                                    JSON.valueToTree(
                                                            documentedInput.get("context")));
                                    assertThat(body.at("/questions/department/criteria"))
                                            .isEqualTo(
                                                    JSON.valueToTree(
                                                            documentedInput.get("candidates")));
                                    assertThat(body.at("/questions/severity/criteria"))
                                            .isEqualTo(
                                                    JSON.valueToTree(
                                                            documentedInput.get("levels")));
                                    return success("{\"answers\":" + retained + "}");
                                }
                                assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
                                assertThat(body.at("/messages/0/content").asText())
                                        .contains("Create an ordered flight plan");
                                boolean assembly = body.toString().contains("assessFixedContact");
                                String child = assembly ? "assessFixedContact" : "assessContact";
                                Map<String, Object> task = new LinkedHashMap<>();
                                task.put("taskId", child);
                                task.put("title", "Assess contact");
                                task.put("capabilityName", child);
                                task.put("intent", "Assess contact");
                                task.put("status", "PENDING");
                                task.put("dependsOn", List.of());
                                task.put("expectedOutputs", List.of("assessment"));
                                task.put("parallelGroup", null);
                                task.put("note", "");
                                String plan =
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "capabilityName",
                                                        assembly ? "summarizeContact" : "assessReportedContact",
                                                        "createdAt",
                                                        "2026-10-10T00:00:00Z",
                                                        "status",
                                                        "VALID",
                                                        "tasks",
                                                        List.of(task)));
                                return success(
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "id",
                                                        "fixture",
                                                        "object",
                                                        "chat.completion",
                                                        "created",
                                                        1,
                                                        "model",
                                                        "gpt-4.1",
                                                        "choices",
                                                        List.of(
                                                                Map.of(
                                                                        "index",
                                                                        0,
                                                                        "message",
                                                                        Map.of(
                                                                                "role",
                                                                                "assistant",
                                                                                "content",
                                                                                plan),
                                                                        "finish_reason",
                                                                        "stop")),
                                                        "usage",
                                                        Map.of(
                                                                "prompt_tokens",
                                                                1,
                                                                "completion_tokens",
                                                                1,
                                                                "total_tokens",
                                                                2))));
                            } catch (Throwable error) {
                                failure.compareAndSet(null, error);
                                return new MockResponse().setResponseCode(500);
                            }
                        }
                    });
            for (String connection : List.of("typesafeDirect", "typesafeRouter", "chat")) {
                var entry =
                        (tools.jackson.databind.node.ObjectNode)
                                config.at("/loomspan/connections/" + connection);
                entry.put("api-key", "documented-fixture-key");
                entry.put(
                        "base-url",
                        server.url(
                                        connection.equals("chat")
                                                ? "/v1"
                                                : connection.equals("typesafeRouter")
                                                        ? "/api"
                                                        : "/")
                                .toString());
            }
            List<String> properties = new ArrayList<>();
            flattenProperties(config, "", properties);
            properties.add("loomspan.execution-trace.persistence=always");
            properties.add("spring.main.web-application-type=none");
            properties.add("loomspan.skills.locations=" + directory.toUri() + "*.yaml");
            new ApplicationContextRunner()
                    .withUserConfiguration(App.class)
                    .withPropertyValues(properties.toArray(String[]::new))
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var reloader = context.getBean(SkillReloader.class);
                                reloader.publish(
                                        reloader.prepare("default", List.of(
                                                        new SkillDocument(
                                                                "published leaf", blocks.get(2)),
                                                        new SkillDocument(
                                                                "published parent",
                                                                blocks.get(3)),
                                                        new SkillDocument("published fixed leaf", blocks.get(4)),
                                                        new SkillDocument("published binding parent", blocks.get(5))), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))));
                                var template = context.getBean(SkillTemplate.class);
                                String generation = reloader.snapshot("default").generationId();
                                assertThat(template.invoke("assessContact", documentedInput, v -> sessionIds.add(v.sessionId())))
                                        .isEqualTo(retained);
                                AtomicReference<SkillExecutionView> view = new AtomicReference<>();
                                assertThat(
                                                template.invoke(
                                                        "assessReportedContact",
                                                        documentedInput,
                                                        v -> { view.set(v); sessionIds.add(v.sessionId()); }))
                                        .isEqualTo(retained);
                                assertThat(view.get().events())
                                        .anyMatch(event -> event.type().equals("RESULT_FORWARDED"));
                                var all = traces(context);
                                assertThat(records(all, "TRACE_STARTED")).hasSize(2).allSatisfy(
                                        record -> assertThat(record.at("/metadata/generationId").asText()).isEqualTo(generation));
                                for (String id : sessionIds) {
                                    var trace = all.stream().filter(record -> record.path("sessionId").asText().equals(id)).toList();
                                    boolean nested = id.equals(view.get().sessionId());
                                    var requests = records(trace, "MODEL_REQUEST_SENT");
                                    assertThat(requests).hasSize(nested ? 2 : 1);
                                    var nativeRequest = requests.stream().filter(record -> record.at("/data/payloadType").asText().equals("evaluation")).toList();
                                    assertThat(nativeRequest).hasSize(1);
                                    var request = nativeRequest.getFirst();
                                    assertThat(request.path("frameId").asText()).isNotBlank();
                                    assertThat(request.at("/data/request/state")).isEqualTo(JSON.valueToTree(documentedInput.get("context")));
                                    assertThat(records(trace, "FRAME_OPENED")).anySatisfy(frame -> {
                                        assertThat(frame.path("frameId")).isEqualTo(request.path("frameId"));
                                        assertThat(frame.path("route").asText()).isEqualTo("assessContact");
                                    });
                                    var response = records(trace, "MODEL_RESPONSE_RECEIVED").stream().filter(record -> record.path("frameId").equals(request.path("frameId"))).toList();
                                    assertThat(response).hasSize(1);
                                    assertThat(logicalData(trace, response.getFirst()).at("/envelope/answers")).isEqualTo(JSON.readTree(retained));
                                    assertThat(usage(trace).path("providerAttempts").intValue()).isEqualTo(nested ? 2 : 1);
                                    assertThat(usage(trace).path("toolInvocations").intValue()).isEqualTo(nested ? 1 : 0);
                                    assertThat(records(trace, "RESULT_FORWARDED")).hasSize(nested ? 1 : 0);
                                }
                                assertThat(
                                                reloader.snapshot("default")
                                                        .skill("assessReportedContact")
                                                        .orElseThrow()
                                                        .outputSchema())
                                        .isEqualTo(
                                                reloader.snapshot("default")
                                                        .skill("assessContact")
                                                        .orElseThrow()
                                                        .outputSchema());
                                Map<String, Object> fixedInput = Map.of("context", documentedInput.get("context"));
                                assertThat(template.invoke("assessFixedContact", fixedInput)).isEqualTo(retained);
                                assertThat(JSON.readTree(template.invoke("summarizeContact", fixedInput)))
                                        .isEqualTo(JSON.valueToTree(Map.of(
                                                "department", "technical",
                                                "repeatProbability", 0.9,
                                                "severity", 0.75,
                                                "technicalProbability", 0.9)));
                            });
            assertThat(failure.get()).isNull();
            assertThat(server.getRequestCount()).isEqualTo(6);
        }
    }

    private static void flattenProperties(JsonNode node, String path, List<String> properties) {
        if (node.isObject())
            node.properties()
                    .forEach(
                            entry ->
                                    flattenProperties(
                                            entry.getValue(),
                                            path.isEmpty()
                                                    ? entry.getKey()
                                                    : path + "." + entry.getKey(),
                                            properties));
        else properties.add(path + "=" + node.asText());
    }

    @Test
    void concurrentDynamicScoreLevelsStayPrivateAndMetadataRemainsStable() throws Exception {
        String document =
                """
name: judgment
description: Score caller-supplied levels
model: selected
input_schema: {type: object, additionalProperties: true}
typesafe:
  state: '${/input/marker}'
  questions:
    severity: {type: score, criteria: '${/input/levels}'}
""";
        Map<String, Object> firstInput =
                Map.of(
                        "marker",
                        "three",
                        "levels",
                        Arrays.asList(null, Map.of("text", "middle"), List.of("high")));
        Map<String, Object> secondInput =
                Map.of(
                        "marker",
                        "five",
                        "levels",
                        Arrays.asList(
                                "lowest",
                                null,
                                Map.of("text", "medium"),
                                List.of("higher"),
                                "highest"));
        CountDownLatch arrived = new CountDownLatch(2), release = new CountDownLatch(1);
        Map<String, JsonNode> captured = new ConcurrentHashMap<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(
                    new Dispatcher() {
                        @Override
                        public MockResponse dispatch(RecordedRequest request) {
                            try {
                                var wire = JSON.readTree(request.getBody().readUtf8());
                                captured.put(wire.get("state").asText(), wire);
                                arrived.countDown();
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new AssertionError("release missing");
                                return success(
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "answers",
                                                        Map.of(
                                                                "marker",
                                                                wire.get("state"),
                                                                "levels",
                                                                wire.at(
                                                                        "/questions/severity/criteria"),
                                                                "extension",
                                                                Map.of("unrestricted", -5)))));
                            } catch (Throwable error) {
                                failure.compareAndSet(null, error);
                                return new MockResponse().setResponseCode(500);
                            }
                        }
                    });
            runner(server, "/", document)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var catalog = context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default");
                                var schema = catalog.skill("judgment").orElseThrow().outputSchema();
                                var template = context.getBean(SkillTemplate.class);
                                try (var threads = Executors.newFixedThreadPool(2)) {
                                    var first = threads.submit(() -> invoke(template, firstInput));
                                    var second =
                                            threads.submit(() -> invoke(template, secondInput));
                                    try {
                                        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
                                        assertThat(
                                                        catalog.skill("judgment")
                                                                .orElseThrow()
                                                                .outputSchema())
                                                .isEqualTo(schema);
                                        for (var input : List.of(firstInput, secondInput)) {
                                            assertThat(
                                                            captured.get(input.get("marker"))
                                                                    .at(
                                                                            "/questions/severity/criteria"))
                                                    .isEqualTo(
                                                            JSON.valueToTree(input.get("levels")));
                                        }
                                    } finally {
                                        release.countDown();
                                    }
                                    for (var pair :
                                            List.of(
                                                    Map.entry(firstInput, first),
                                                    Map.entry(secondInput, second))) {
                                        var result =
                                                JSON.readTree(
                                                        pair.getValue().get(5, TimeUnit.SECONDS));
                                        assertThat(result.get("marker").asText())
                                                .isEqualTo(pair.getKey().get("marker"));
                                        assertThat(result.get("levels"))
                                                .isEqualTo(
                                                        JSON.valueToTree(
                                                                pair.getKey().get("levels")));
                                        assertThat(result.at("/extension/unrestricted").intValue())
                                                .isEqualTo(-5);
                                    }
                                } finally {
                                    release.countDown();
                                }
                                assertThat(catalog.skill("judgment").orElseThrow().outputSchema())
                                        .isEqualTo(schema);
                            });
            assertThat(failure.get()).isNull();
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @Test
    void acceptedNormalizedAndExactBoundInputsProduceEqualNativeRequests() throws Exception {
        String document =
                """
name: judgment
description: Judge a numeric value
model: selected
input_schema:
  type: object
  properties: {count: {type: integer}}
  required: [count]
  additionalProperties: false
typesafe:
  state: {count: '${/input/count}'}
  questions: {q: {type: noul, instructions: decide}}
""";
        String parent =
                """
name: boundJudgment
description: Forward an exact caller value
model: planner
planning_mode: true
max_steps: 1
prompt: Have judgment evaluate the supplied value.
input_schema:
  type: object
  properties: {payload: {type: object, additionalProperties: true}}
  required: [payload]
  additionalProperties: false
allowed_skills:
  - name: judgment
    required: true
    max_tasks: 1
    input_bindings:
      /count: {from: input, path: /payload/count}
output_from: {skill: judgment}
""";
        List<JsonNode> nativeRequests = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(
                    new Dispatcher() {
                        @Override
                        public MockResponse dispatch(RecordedRequest request) {
                            try {
                                if (request.getPath().equals("/v1/systemone")) {
                                    nativeRequests.add(JSON.readTree(request.getBody().readUtf8()));
                                    return success("{\"answers\":{}}");
                                }
                                assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
                                Map<String, Object> task = new LinkedHashMap<>();
                                task.put("taskId", "judge");
                                task.put("title", "Judge value");
                                task.put("capabilityName", "judgment");
                                task.put("intent", "Judge value");
                                task.put("status", "PENDING");
                                task.put("dependsOn", List.of());
                                task.put("expectedOutputs", List.of("judgment"));
                                task.put("parallelGroup", null);
                                task.put("note", "");
                                String plan =
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "capabilityName",
                                                        "boundJudgment",
                                                        "createdAt",
                                                        "2026-10-10T00:00:00Z",
                                                        "status",
                                                        "VALID",
                                                        "tasks",
                                                        List.of(task)));
                                return success(
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "id",
                                                        "fixture",
                                                        "object",
                                                        "chat.completion",
                                                        "created",
                                                        1,
                                                        "model",
                                                        "gpt-4.1",
                                                        "choices",
                                                        List.of(
                                                                Map.of(
                                                                        "index",
                                                                        0,
                                                                        "message",
                                                                        Map.of(
                                                                                "role",
                                                                                "assistant",
                                                                                "content",
                                                                                plan),
                                                                        "finish_reason",
                                                                        "stop")),
                                                        "usage",
                                                        Map.of(
                                                                "prompt_tokens",
                                                                1,
                                                                "completion_tokens",
                                                                1,
                                                                "total_tokens",
                                                                2))));
                            } catch (Throwable error) {
                                failure.compareAndSet(null, error);
                                return new MockResponse().setResponseCode(500);
                            }
                        }
                    });
            runner(server, "/", document)
                    .withPropertyValues(
                            "loomspan.connections.chat.driver=openai",
                            "loomspan.connections.chat.api-key=fixture",
                            "loomspan.connections.chat.base-url=" + server.url("/v1"),
                            "loomspan.models.planner.connection=chat",
                            "loomspan.models.planner.provider-model=gpt-4.1")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var reloader = context.getBean(SkillReloader.class);
                                reloader.publish(
                                        reloader.prepare("default", List.of(
                                                        new SkillDocument("leaf", document),
                                                        new SkillDocument("parent", parent)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))));
                                var template = context.getBean(SkillTemplate.class);
                                assertThat(invoke(template, Map.of("count", "3"))).isEqualTo("{}");
                                assertThat(
                                                template.invoke(
                                                        "boundJudgment",
                                                        Map.of("payload", Map.of("count", 3)),
                                                        view -> sessionIds.add(view.sessionId())))
                                        .isEqualTo("{}");
                                assertThat(nativeRequests).hasSize(2);
                                assertThat(nativeRequests.get(0)).isEqualTo(nativeRequests.get(1));
                                assertThat(
                                                nativeRequests
                                                        .get(0)
                                                        .at("/state/count")
                                                        .isIntegralNumber())
                                        .isTrue();
                                sessionIds.clear();
                                assertThatThrownBy(
                                                () ->
                                                        template.invoke(
                                                                "boundJudgment",
                                                                Map.of(
                                                                        "payload",
                                                                        Map.of("count", "3")),
                                                                view ->
                                                                        sessionIds.add(
                                                                                view.sessionId())))
                                        .isInstanceOf(SkillException.class);
                                assertThat(nativeRequests).hasSize(2);
                                var rejectedTrace = traces(context);
                                assertThat(records(rejectedTrace, "MODEL_REQUEST_SENT"))
                                        .hasSize(1)
                                        .noneMatch(
                                                record ->
                                                        record.at("/data/payloadType")
                                                                .asText()
                                                                .equals("evaluation"));
                                assertThat(usage(rejectedTrace).path("providerAttempts").intValue())
                                        .isEqualTo(1);
                            });
            assertThat(failure.get()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"budget", "denied"})
    void nestedNativeFailureRetainsSharedAccountingWithoutSubstitute(String scenario) throws Exception {
        String document = """
                name: judgment
                description: Native judgment
                model: selected
                input_schema:
                  type: object
                  properties: {candidates: {type: object, additionalProperties: true}}
                  required: [candidates]
                  additionalProperties: false
                typesafe:
                  state: ${/input}
                  questions: {q: {type: choice, criteria: '${/input/candidates}'}}
                """ + (scenario.equals("denied") ? "rbac_roles: [ALLOWED]\n" : "");
        String parent = """
                name: parent
                description: Forward native result
                model: planner
                planning_mode: true
                max_steps: 1
                input_schema: {type: object, additionalProperties: true}
                allowed_skills:
                  - name: judgment
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /candidates: {from: input, path: /candidates}
                output_from: {skill: judgment}
                """;
        try (var server = new MockWebServer()) {
            var task = Map.of("taskId", "judge", "title", "Judge", "capabilityName", "judgment", "intent", "Judge", "status", "PENDING", "dependsOn", List.of(), "expectedOutputs", List.of("result"));
            String plan = JSON.writeValueAsString(Map.of("capabilityName", "parent", "createdAt", "2026-10-11T00:00:00Z", "status", "VALID", "tasks", List.of(task)));
            server.enqueue(success(JSON.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion", "created", 1, "model", "planner", "choices", List.of(
                    Map.of("index", 0, "message", Map.of("role", "assistant", "content", plan), "finish_reason", "stop")), "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)))));
            runner(server, "/", document).withPropertyValues(
                    "loomspan.connections.chat.driver=openai", "loomspan.connections.chat.api-key=fixture",
                    "loomspan.connections.chat.base-url=" + server.url("/v1"), "loomspan.connections.chat.provider-retry.enabled=false",
                    "loomspan.models.planner.connection=chat", "loomspan.models.planner.provider-model=planner",
                    "loomspan.session.quotas.max-provider-attempts=" + (scenario.equals("budget") ? 1 : 10))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var reloader = context.getBean(SkillReloader.class);
                        reloader.publish(reloader.prepare("default", List.of(new SkillDocument("leaf", document), new SkillDocument("parent", parent)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))));
                        assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("parent", Map.of("candidates", Map.of("A", "first")), v -> sessionIds.add(v.sessionId())))
                                .isInstanceOf(SkillException.class)
                                .hasStackTraceContaining(scenario.equals("budget") ? "MAX_PROVIDER_ATTEMPTS" : "judgment");
                        var trace = traces(context);
                        assertThat(records(trace, "MODEL_REQUEST_SENT")).noneMatch(record -> record.at("/data/payloadType").asText().equals("evaluation"));
                        assertThat(records(trace, "RESULT_FORWARDED")).isEmpty();
                        assertThat(usage(trace).path("providerAttempts").intValue()).isEqualTo(scenario.equals("budget") ? 1 : 0);
                        assertThat(usage(trace).path("modelCalls").intValue()).isEqualTo(scenario.equals("budget") ? 1 : 0);
                    });
            assertThat(server.getRequestCount()).isEqualTo(scenario.equals("budget") ? 1 : 0);
            if (server.getRequestCount() > 0) assertThat(server.takeRequest().getPath()).isEqualTo("/v1/chat/completions");
        }
    }

    @Test
    void ordinaryInputNormalizationPrecedesNativeResolution() throws Exception {
        String document =
                leaf().replace(
                                "additionalProperties: true",
                                "additionalProperties: true\n"
                                        + "  properties: {count: {type: number}}\n"
                                        + "  required: [count]");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(success("{\"answers\":{}}"));
            server.enqueue(success("{\"answers\":{}}"));
            runner(server, "/", document)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var template = context.getBean(SkillTemplate.class);
                                assertThat(
                                                invoke(
                                                        template,
                                                        Map.of(
                                                                "count",
                                                                "3",
                                                                "candidates",
                                                                Map.of("A", "yes"))))
                                        .isEqualTo("{}");
                                assertThat(
                                                invoke(
                                                        template,
                                                        Map.of(
                                                                "count",
                                                                3,
                                                                "candidates",
                                                                Map.of("A", "yes"))))
                                        .isEqualTo("{}");
                            });
            JsonNode first =
                    JSON.readTree(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
            JsonNode second =
                    JSON.readTree(server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
            assertThat(first.at("/state/count").isNumber()).isTrue();
            assertThat(first.at("/state/count").doubleValue()).isEqualTo(3);
            assertThat(second.at("/state/count").doubleValue()).isEqualTo(3);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"caller", "shutdown"})
    void callerInterruptionAndFrameworkShutdownCancelPendingNativeRequest(String mode)
            throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(success("{\"answers\":{}}").setHeadersDelay(3, TimeUnit.SECONDS));
            runner(server, "/", leaf())
                    .withPropertyValues("loomspan.shutdown.timeout=100ms")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                var template = context.getBean(SkillTemplate.class);
                                AtomicReference<Thread> caller = new AtomicReference<>();
                                try (var executor = Executors.newSingleThreadExecutor()) {
                                    var pending =
                                            executor.submit(
                                                    () -> {
                                                        caller.set(Thread.currentThread());
                                                        return invoke(template, input("A"));
                                                    });
                                    assertThat(server.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
                                    long start = System.nanoTime();
                                    if (mode.equals("caller")) caller.get().interrupt();
                                    else context.close();
                                    assertThatThrownBy(() -> pending.get(2, TimeUnit.SECONDS))
                                            .isInstanceOf(ExecutionException.class);
                                    assertThat(
                                                    TimeUnit.NANOSECONDS.toMillis(
                                                            System.nanoTime() - start))
                                            .isLessThan(2500);
                                    assertThat(records(traces(context), "MODEL_RESPONSE_RECEIVED"))
                                            .isEmpty();
                                }
                            });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missionCutoffAbortsNativeWaitAndPreventsLateResponseAccounting(boolean backoff)
            throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                    backoff
                            ? new MockResponse().setResponseCode(529).setBody("overloaded")
                            : success(
                                            "{\"answers\":{},\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}")
                                    .setHeadersDelay(3, TimeUnit.SECONDS));
            runner(server, "/", leaf())
                    .withPropertyValues(
                            "loomspan.session.mission-timeout=300ms",
                            "loomspan.connections.native.provider-retry.enabled=true",
                            "loomspan.connections.native.provider-retry.initial-backoff=5s",
                            "loomspan.connections.native.provider-retry.max-backoff=5s",
                            "loomspan.connections.native.provider-retry.jitter=0")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                long start = System.nanoTime();
                                assertThatThrownBy(
                                                () ->
                                                        invoke(
                                                                context.getBean(
                                                                        SkillTemplate.class),
                                                                input("A")))
                                        .isInstanceOf(SkillException.class);
                                assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                                        .isLessThan(2500);
                                assertThat(records(traces(context), "MODEL_RESPONSE_RECEIVED"))
                                        .isEmpty();
                                assertThat(usage(traces(context)).path("modelCalls").intValue())
                                        .isZero();
                            });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }
}
