package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.vfs.SessionLocalVirtualFileSystem;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.Resource;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Application I/O composed with real startup ingestion, facade and standard tool dispatch. */
class ExecutorApplicationIntegrationTest {
    @TempDir Path directory;

    @Test void httpExecutorCallsApplicationThroughStartupConfiguration() throws Exception {
        try (var application = new MockWebServer()) {
            application.enqueue(new MockResponse().setBody("  application result\n")); application.start();
            writeConfiguration(application);
            var beans = new ApplicationExecutors();
            runner(beans).run(context -> {
                assertThat(context).hasNotFailed();
                var skills = context.getBean(SkillTemplate.class);
                var views = new ArrayList<SkillExecutionView>();
                assertThat(skills.invoke("default-http", Map.of("message", "hello"), views::add))
                        .isEqualTo("  application result\n");
                assertThat(beans.httpInvocation.skillName()).isEqualTo("default-http");
                assertThat(beans.httpInvocation.skillConfig()).containsEntry("path", "/operation");
                var catalog = context.getBean(SkillReloader.class).snapshot("default");
                assertThat(catalog.skill("default-http").orElseThrow().kind()).isEqualTo(SkillKind.JAVA);
                assertThat(catalog.skill("default-httpExecutor")).isEmpty();
                assertThat(beans.httpInvocation.executorConfigGenerationId()).isEqualTo(catalog.generationId());
                assertThat(views).hasSize(1);
                assertThat(views.getFirst().events()).isNotEmpty();
                assertThatThrownBy(() -> skills.invoke("default-http", Map.of("message", 42)))
                        .isInstanceOf(SkillInputValidationException.class);
                assertThatThrownBy(() -> skills.invoke("default-http", Map.of()))
                        .isInstanceOf(SkillInputValidationException.class);
            });
            var request = application.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getPath()).isEqualTo("/operation");
            assertThat(request.getBody().readUtf8()).isEqualTo("hello");
            assertThat(application.getRequestCount()).isOne();
        }
    }

    @Test void standardChildResolvesSessionResourceBeforeHttpExecution() throws Exception {
        try (var application = new MockWebServer(); var provider = new MockWebServer()) {
            application.enqueue(new MockResponse().setBody("uploaded")); application.start();
            writeConfiguration(application);
            Files.writeString(directory.resolve("parent.yaml"), """
                    name: parent
                    description: Upload a session file
                    model: local
                    allowed_skills: [{name: default-writeFile}, {name: default-http}]
                    """);
            provider.enqueue(tool("default-writeFile", "{}", "write"));
            provider.enqueue(tool("default-http", "{\"message\":\"upload\",\"attachment\":\"ref://payload.txt\"}", "upload"));
            provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.text("DONE")); provider.start();
            var beans = new ApplicationExecutors();
            runner(beans).withBean(FileWriter.class, () -> new FileWriter())
                    .withPropertyValues("loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url=" + provider.url("/v1"),
                            "loomspan.connections.local.api-key=fixture", "loomspan.connections.local.provider-retry.enabled=false",
                            "loomspan.models.local.connection=local", "loomspan.models.local.provider-model=fixture")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var writer = context.getBean(FileWriter.class);
                        writer.vfs = context.getBean(SessionLocalVirtualFileSystem.class);
                        try {
                            var views = new ArrayList<SkillExecutionView>();
                            assertThat(context.getBean(SkillTemplate.class).invoke("default-parent", Map.of(), views::add)).isEqualTo("DONE");
                            assertThat(beans.httpInvocation.input().get("attachment")).isInstanceOf(Resource.class);
                            assertThat(beans.httpInvocation.skillName()).isEqualTo("default-http");
                            assertThat(views).hasSize(1);
                            assertThat(views.getFirst().events()).anySatisfy(event -> assertThat(event.route()).isEqualTo("default-http"));
                            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-http",
                                    Map.of("message", "missing", "attachment", "ref://unknown.txt")))
                                    .isInstanceOf(SkillException.class).hasStackTraceContaining("Unknown ref");
                        } finally { writer.cleanup(); }
                    });
            assertThat(application.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8()).isEqualTo("session file bytes");
            assertThat(application.getRequestCount()).isOne();
            assertThat(provider.getRequestCount()).isEqualTo(3);
            provider.takeRequest(2, TimeUnit.SECONDS);
            assertThat(provider.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8()).contains("ref://payload.txt");
            assertThat(provider.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8()).contains("uploaded");
        }
    }

    @Test void sqlShapedDataStaysSeparateAndDomainChecksAreInvocationOnly() throws Exception {
        try (var application = new MockWebServer()) {
            application.start(); writeConfiguration(application);
            var beans = new ApplicationExecutors();
            runner(beans).run(context -> {
                assertThat(context).hasNotFailed();
                var skills = context.getBean(SkillTemplate.class);
                assertThat(skills.invoke("default-query", Map.of("id", 7))).isEqualTo("archive|select :id|7");
                var call = beans.sqlInvocation;
                assertThat(call.skillConfig()).containsEntry("database", "archive").containsEntry("nullable", null);
                assertThat(call.executorConfig()).containsEntry("sql", "select :id").containsEntry("nullable", null);
                assertThat(call.skillConfig().get("literal.dot")).isEqualTo(Arrays.asList(null, Map.of(), List.of()));
                assertThat(call.executorConfig()).containsEntry("Upper", "A").containsEntry("upper", "B");
                var reloader = context.getBean(SkillReloader.class);
                var candidate = reloader.prepare("default", List.of(new SkillDocument("bad", "name: bad\ndescription: invalid domain\nexecutor: sqlExecutor\nconfig: {database: invalid}\n")),
                        new ExecutionConfiguration("loomspan: {executor-config: {default: {http: {}, sql: {sql: select}}}}"));
                assertThat(beans.sqlCalls).isEqualTo(1);
                reloader.publish(candidate);
                skills.validate("default-bad", Map.of());
                assertThat(beans.sqlCalls).isEqualTo(1);
                assertThatThrownBy(() -> skills.invoke("default-bad", Map.of()))
                        .isInstanceOf(SkillException.class).hasStackTraceContaining("Application database unavailable");
                assertThat(beans.sqlCalls).isEqualTo(2);
            });
        }
    }

    private void writeConfiguration(MockWebServer application) throws Exception {
        Files.writeString(directory.resolve("application.yml"), """
                loomspan:
                  executor-config:
                    default:
                      http: {endpoint: '%s'}
                      sql: {sql: 'select :id', database: infrastructure, nullable: null, Upper: A, upper: B, 'literal.dot': [null, {}, []]}
                """.formatted(application.url("/")));
        Files.writeString(directory.resolve("http.yaml"), """
                name: http
                description: Send a message or session file to the application
                executor: httpExecutor
                config: {path: /operation}
                input_schema:
                  type: object
                  properties:
                    message: {type: string}
                    attachment: {type: attachment, media_type: file, allowed_content_types: [text/plain]}
                  required: [message]
                  additionalProperties: false
                """);
        Files.writeString(directory.resolve("query.yaml"), """
                name: query
                description: Demonstrate application-owned SQL data
                executor: sqlExecutor
                config: {database: archive, sql: operation-data, nullable: null, 'literal.dot': [null, {}, []]}
                """);
    }

    private ApplicationContextRunner runner(ApplicationExecutors beans) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                        LoomspanJacksonAutoConfiguration.class, LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withInitializer(new ConfigDataApplicationContextInitializer()).withBean(ApplicationExecutors.class, () -> beans)
                .withPropertyValues("spring.config.location=" + directory.resolve("application.yml").toUri(),
                        "loomspan.skills.locations=" + directory.toUri() + "*.yaml", "loomspan.session.mission-timeout=10s");
    }

    private static MockResponse tool(String name, String arguments, String id) {
        return ExplicitJavaFailureHandlingIntegrationTest.response(Map.of("role", "assistant", "content", "", "tool_calls", List.of(
                Map.of("id", id, "type", "function", "function", Map.of("name", name, "arguments", arguments)))), "tool_calls");
    }

    public static class ApplicationExecutors {
        volatile SkillExecutorInvocation httpInvocation;
        volatile SkillExecutorInvocation sqlInvocation;
        int sqlCalls;
        @SkillExecutor(name = "httpExecutor", config = "http")
        public String http(SkillExecutorInvocation call) throws Exception {
            httpInvocation = call;
            byte[] body;
            if (call.input().get("attachment") instanceof Resource resource) {
                try (var stream = resource.getInputStream()) { body = stream.readAllBytes(); }
            } else { body = call.input().get("message").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8); }
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                var request = HttpRequest.newBuilder(URI.create(call.executorConfig().get("endpoint").toString())
                                .resolve(call.skillConfig().get("path").toString())).timeout(Duration.ofSeconds(2))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            }
        }
        @SkillExecutor(name = "sqlExecutor", config = "sql")
        public String sql(SkillExecutorInvocation call) {
            sqlCalls++; sqlInvocation = call;
            if ("invalid".equals(call.skillConfig().get("database"))) throw new IllegalStateException("Application database unavailable");
            return call.skillConfig().get("database") + "|" + call.executorConfig().get("sql") + "|" + call.input().get("id");
        }
    }

    public static class FileWriter {
        SessionLocalVirtualFileSystem vfs;
        Path file;
        @SkillMethod(name = "writeFile", description = "Create the fixture session file")
        public String write() throws Exception {
            file = vfs.sessionRoot(ExecutionBindingScope.requireCurrent().session()).resolve("payload.txt");
            Files.createDirectories(file.getParent()); Files.writeString(file, "session file bytes");
            return "ref://payload.txt";
        }
        void cleanup() {
            if (file != null) {
                try { Files.deleteIfExists(file); Files.deleteIfExists(file.getParent()); }
                catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            }
        }
    }
}
