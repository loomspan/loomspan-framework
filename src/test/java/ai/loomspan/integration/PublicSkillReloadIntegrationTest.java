package ai.loomspan.integration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.testkit.FixtureExecutor;
import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.SkillValidationResult;
import ai.loomspan.api.ValidatedSkill;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicSkillReloadIntegrationTest
{
    @Test
    void embeddedStartupOpenRouterHonorsHeaderAndActiveBodyDeadline(@TempDir Path directory) throws Exception
    {
        for (boolean chunks : List.of(false, true))
            for (boolean expires : List.of(false, true))
                try (MockWebServer server = new MockWebServer())
                {
                    var response = modelResponse("embedded-success");
                    if (chunks) response.setChunkedBody(" ".repeat(1000) + response.getBody().readUtf8(), 50)
                            .throttleBody(50, 75, java.util.concurrent.TimeUnit.MILLISECONDS);
                    else response.setHeadersDelay(expires ? 1800 : 150, java.util.concurrent.TimeUnit.MILLISECONDS);
                    server.enqueue(response);
                    timeoutContext(directory).withPropertyValues(
                            "loomspan.connections.candidate.driver=openai",
                            "loomspan.connections.candidate.base-url=" + server.url("/v1"),
                            "loomspan.connections.candidate.api-key=local-fake-key",
                            "loomspan.connections.candidate.request-timeout=" + (expires ? "1s" : "4s"),
                            "loomspan.connections.candidate.openai.compatibility-profile=openrouter",
                            "loomspan.connections.candidate.provider-retry.max-attempts=1",
                            "loomspan.models.selected.connection=candidate",
                            "loomspan.models.selected.provider-model=compact",
                            "loomspan.session.mission-timeout=10s").run(context -> {
                        assertThat(context).hasNotFailed();
                        var reloader = context.getBean(SkillReloader.class);
                        reloader.publish(reloader.prepare("default", List.of(new SkillDocument("embedded", """
                                name: candidateSkill
                                description: embedded model
                                model: selected
                                planning_mode: false
                                """)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))));
                        var template = context.getBean(SkillTemplate.class);
                        if (expires) assertThatThrownBy(() -> template.invoke("default-candidateSkill", Map.of()))
                                .isInstanceOf(ai.loomspan.api.SkillException.class)
                                .satisfies(PublicSkillReloadIntegrationTest::assertProviderTimeout);
                        else assertThat(template.invoke("default-candidateSkill", Map.of())).isEqualTo("embedded-success");
                        assertThat(server.getRequestCount()).isEqualTo(1);
                    });
                }
    }
    @Test
    void delayedDescendantAndItsRetryRetainCapturedProviderBudget(@TempDir Path directory) throws Exception
    {
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer())
        {
            first.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id":"tool","object":"chat.completion","created":1,"model":"compact",
                     "choices":[{"index":0,"message":{"role":"assistant","content":null,
                       "tool_calls":[{"id":"child-call","type":"function","function":{"name":"default-child","arguments":"{}"}}]},
                       "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """));
            first.enqueue(modelResponse("too-late").setHeadersDelay(3000, java.util.concurrent.TimeUnit.MILLISECONDS));
            first.enqueue(modelResponse("child-return").setHeadersDelay(1500, java.util.concurrent.TimeUnit.MILLISECONDS));
            first.enqueue(modelResponse("old-tree-finished"));
            second.enqueue(modelResponse("new-too-late").setHeadersDelay(1500, java.util.concurrent.TimeUnit.MILLISECONDS));
            var documents = List.of(new SkillDocument("root", """
                    name: parent
                    description: calls child
                    model: selected
                    planning_mode: false
                    allowed_skills:
                      - name: default-child
                    """), new SkillDocument("child", """
                    name: child
                    description: child model
                    model: selected
                    planning_mode: false
                    """));
            timeoutContext(directory).run(context -> {
                var reloader = context.getBean(SkillReloader.class);
                var a = reloader.prepare("default", documents, timeoutConfiguration(first, "2s", 2), Map.of("candidate.key", "local-A"));
                reloader.publish(a);
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("default-parent", Map.of());
                var b = reloader.prepare("default", documents, timeoutConfiguration(second, "1s", 1), Map.of("candidate.key", "local-B"));
                reloader.publish(b);
                assertThat(pending.generationId()).isEqualTo(a.generationId());
                assertThat(pending.invoke()).isEqualTo("old-tree-finished");
                assertThat(first.getRequestCount()).isEqualTo(4);
                assertThat(second.getRequestCount()).isZero();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-child", Map.of()))
                        .isInstanceOf(ai.loomspan.api.SkillException.class)
                        .satisfies(PublicSkillReloadIntegrationTest::assertProviderTimeout);
                assertThat(second.getRequestCount()).isEqualTo(1);
            });
            var rootRequest = first.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(rootRequest).isNotNull();
            String childFirst = first.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getBody().readUtf8();
            assertThat(first.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getBody().readUtf8()).isEqualTo(childFirst);
            assertThat(first.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getBody().readUtf8()).contains("child-return");
        }
    }
    @Test
    void longerProviderBudgetCannotExtendMissionOrCallerInterruption(@TempDir Path directory) throws Exception
    {
        for (boolean interrupt : List.of(false, true))
        {
            try (MockWebServer server = new MockWebServer())
            {
                var entered = new java.util.concurrent.CountDownLatch(1);
                var release = new java.util.concurrent.CountDownLatch(1);
                var returned = new java.util.concurrent.CountDownLatch(1);
                server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                    @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request)
                            throws InterruptedException
                    {
                        entered.countDown();
                        try {
                            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                throw new AssertionError("response release was not signaled");
                            return modelResponse("late-success");
                        }
                        finally { returned.countDown(); }
                    }
                });
                var documents = List.of(new SkillDocument("candidate",
                        "name: candidateSkill\ndescription: model\nmodel: selected\nplanning_mode: false\n"));
                try
                {
                    timeoutContext(directory).run(context -> {
                        assertThat(context).hasNotFailed();
                        var reloader = context.getBean(SkillReloader.class);
                        var configuration = new ExecutionConfiguration(timeoutConfiguration(server, "4s", 3).yaml()
                                + "  session:\n    mission-timeout: " + (interrupt ? "10s" : "1s") + "\n");
                        var a = reloader.prepare("default", documents, configuration, Map.of("candidate.key", "local-key"));
                        reloader.publish(a);
                        var retired = new java.util.concurrent.CountDownLatch(1);
                        try (var registration = reloader.onGenerationRetired(id -> {
                            if (id.equals(a.generationId())) retired.countDown();
                        }))
                        {
                            var failure = new AtomicReference<Throwable>();
                            var view = new AtomicReference<ai.loomspan.api.SkillExecutionView>();
                            var callerInterrupted = new java.util.concurrent.atomic.AtomicBoolean();
                            Thread caller = Thread.ofPlatform().unstarted(() -> {
                                try { context.getBean(SkillTemplate.class).invoke("default-candidateSkill", Map.of(), view::set); }
                                catch (Throwable ex) { failure.set(ex); callerInterrupted.set(Thread.currentThread().isInterrupted()); }
                            });
                            try
                            {
                                caller.start();
                                assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                                if (interrupt) caller.interrupt();
                                caller.join(3000);
                                assertThat(caller.isAlive()).isFalse();
                                assertThat(failure.get()).isInstanceOf(ai.loomspan.api.SkillException.class)
                                        .hasStackTraceContaining("LoomspanMissionTimeoutException");
                                assertThat(callerInterrupted.get()).isEqualTo(interrupt);
                                assertThat(view.get()).isNotNull();
                                assertThat(view.get().events()).noneSatisfy(event ->
                                        assertThat(event.details().toString()).contains("late-success"));
                                reloader.publish(reloader.prepare("default", documents, configuration, Map.of("candidate.key", "local-key")));
                                release.countDown();
                                assertThat(returned.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                                assertThat(retired.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                                assertThat(server.getRequestCount()).isEqualTo(1);
                            }
                            finally { release.countDown(); caller.interrupt(); caller.join(3000); }
                        }
                    });
                }
                finally { release.countDown(); }
            }
        }
    }
    @Test
    void publishedTimeoutCapturesOldRootAcrossCompleteReplacements(@TempDir Path directory) throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(modelResponse("old").setHeadersDelay(1800, java.util.concurrent.TimeUnit.MILLISECONDS));
            for (int i = 0; i < 3; i++) server.enqueue(modelResponse("late")
                    .setHeadersDelay(1800, java.util.concurrent.TimeUnit.MILLISECONDS));
            var documents = List.of(new SkillDocument("candidate",
                    "name: candidateSkill\ndescription: model\nmodel: selected\nplanning_mode: false\n"));
            timeoutContext(directory).withPropertyValues("candidate.key=local-key").run(context -> {
                assertThat(context).hasNotFailed();
                var reloader = context.getBean(SkillReloader.class);
                var oldConfig = timeoutConfiguration(server, "4s", 1);
                assertThat(reloader.validate("default", documents, oldConfig).valid()).isTrue();
                var a = reloader.prepare("default", documents, oldConfig); // Environment path
                assertThat(server.getRequestCount()).isZero();
                reloader.publish(a);
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("default-candidateSkill", Map.of());
                var b = reloader.prepare("default", documents, timeoutConfiguration(server, "1s", 1), Map.of("candidate.key", "host-local-key")); // Host-map path
                assertThat(server.getRequestCount()).isZero();
                reloader.publish(b);
                assertThat(pending.generationId()).isEqualTo(a.generationId());
                assertThat(pending.invoke()).isEqualTo("old");
                var startup = context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class);
                startup.getSession().setMissionTimeout(java.time.Duration.ofMillis(1));
                for (int i = 0; i < 3; i++)
                {
                    if (i > 0) reloader.publish(reloader.prepare("default", documents, timeoutConfiguration(server, "1s", 1), Map.of("candidate.key", "host-local-key")));
                    assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-candidateSkill", Map.of()))
                            .isInstanceOf(ai.loomspan.api.SkillException.class)
                            .satisfies(PublicSkillReloadIntegrationTest::assertProviderTimeout);
                }
                assertThat(server.getRequestCount()).isEqualTo(4);
            });
        }
    }

    @Test
    void invalidTimeoutCandidatesRejectAtomicallyWithConfigurationCause(@TempDir Path directory)
            throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            var documents = List.of(new SkillDocument("candidate",
                    "name: candidateSkill\ndescription: model\nmodel: selected\nplanning_mode: false\n"));
            timeoutContext(directory).withPropertyValues("candidate.key=environment-local-key").run(context -> {
                var reloader = context.getBean(SkillReloader.class);
                String active = reloader.snapshot("default").generationId();
                for (String invalid : List.of("0ms", "-1ms", "PT0.0015S", "2147483648ms", "malformed-secret-sentinel"))
                {
                    var candidate = timeoutConfiguration(server, invalid, 1);
                    var validation = reloader.validate("default", documents, candidate);
                    assertThat(validation.valid()).isFalse();
                    assertThat(validation.issues()).anySatisfy(issue -> assertThat(issue.message())
                            .contains("loomspan.connections.candidate.request-timeout").doesNotContain("secret-sentinel"));
                    assertThatThrownBy(() -> reloader.prepare("default", documents, candidate))
                            .isInstanceOf(SkillReloadException.class);
                    assertThatThrownBy(() -> reloader.prepare("default", documents, candidate, Map.of("candidate.key", "host-secret-sentinel")))
                            .isInstanceOf(SkillReloadException.class)
                            .hasCauseInstanceOf(RuntimeException.class).hasMessageContaining("request-timeout");
                    assertThat(reloader.snapshot("default").generationId()).isEqualTo(active);
                }
                assertThat(server.getRequestCount()).isZero();
            });
        }
    }

    private static ApplicationContextRunner timeoutContext(Path directory)
    {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class,
                ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml");
    }

    private static void assertProviderTimeout(Throwable failure)
    {
        assertThat(java.util.stream.Stream.iterate(failure, java.util.Objects::nonNull, Throwable::getCause)
                .anyMatch(cause -> cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.io.InterruptedIOException
                        && cause.getMessage() != null
                        && cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timeout")))
                .as("actual provider timeout cause").isTrue();
    }

    private static ExecutionConfiguration timeoutConfiguration(MockWebServer server, String timeout, int attempts)
    {
        return new ExecutionConfiguration(candidateConfiguration(server, "candidate.key", attempts).yaml()
                .replace("driver: openai", "driver: openai\n      request-timeout: " + timeout
                        + "\n      openai:\n        compatibility-profile: openrouter"));
    }
    @Test
    void publicHostMapPublishesReplacementKeysWithoutDeploymentProperties(@TempDir Path directory) throws Exception
    {
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer())
        {
            first.enqueue(modelResponse("first"));
            second.enqueue(modelResponse("second"));
            var documents = List.of(new SkillDocument("candidate",
                    "name: candidateSkill\ndescription: selected model\nmodel: selected\nplanning_mode: false\n"));
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                            LoomspanAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                    .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillReloader reloader = context.getBean(SkillReloader.class);
                        SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                        var firstConfiguration = candidateConfiguration(first, "host.key");
                        assertThatThrownBy(() -> reloader.prepare("default", documents, firstConfiguration, Map.of()))
                                .isInstanceOf(SkillReloadException.class).hasMessageContaining("api-key-ref");
                        PreparedSkillUpdate a = reloader.prepare("default", documents, firstConfiguration, Map.of("host.key", "host-A"));
                        assertThat(first.getRequestCount()).isZero();
                        reloader.publish(a);
                        var pending = handoff.handoff("default-candidateSkill", Map.of());
                        assertThat(pending.generationId()).isEqualTo(a.generationId());
                        PreparedSkillUpdate b = reloader.prepare("default", documents, candidateConfiguration(second, "host.key"), Map.of("host.key", "host-B"));
                        assertThat(second.getRequestCount()).isZero();
                        reloader.publish(b);
                        assertThat(pending.invoke()).isEqualTo("first");
                        assertThat(context.getBean(SkillTemplate.class).invoke("default-candidateSkill", Map.of()))
                                .isEqualTo("second");
                    });
            assertThat(first.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getHeader("Authorization"))
                    .isEqualTo("Bearer host-A");
            assertThat(second.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getHeader("Authorization"))
                    .isEqualTo("Bearer host-B");
        }
    }
    @Test
    void pendingRootKeepsProviderRetryPolicyAfterPublication(@TempDir Path directory) throws Exception
    {
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer())
        {
            first.enqueue(new MockResponse().setResponseCode(503).setBody("temporary"));
            first.enqueue(modelResponse("retried"));
            second.enqueue(new MockResponse().setResponseCode(503).setBody("temporary"));
            second.enqueue(modelResponse("unexpected-retry"));
            var documents = List.of(new SkillDocument("candidate",
                    "name: retrySkill\ndescription: selected model\nmodel: selected\nplanning_mode: false\n"));
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                            LoomspanAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                    .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                            "candidate.a=external-a", "candidate.b=external-b")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillReloader reloader = context.getBean(SkillReloader.class);
                        SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                        reloader.publish(reloader.prepare("default", documents, candidateConfiguration(first, "candidate.a", 2)));
                        var pending = handoff.handoff("default-retrySkill", Map.of());
                        reloader.publish(reloader.prepare("default", documents, candidateConfiguration(second, "candidate.b", 1)));
                        assertThat(pending.invoke()).isEqualTo("retried");
                        assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default-retrySkill", Map.of()))
                                .isInstanceOf(ai.loomspan.api.SkillException.class);
                    });
            assertThat(first.getRequestCount()).isEqualTo(2);
            assertThat(second.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void pendingRootKeepsItsConnectionAcrossPublication(@TempDir Path directory) throws Exception
    {
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer())
        {
            first.enqueue(modelResponse("first"));
            second.enqueue(modelResponse("second"));
            String yaml = "name: candidateSkill\ndescription: selected model\nmodel: selected\nplanning_mode: false\n";
            var documents = List.of(new SkillDocument("candidate", yaml));
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                            LoomspanAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                    .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                            "candidate.a=external-a", "candidate.b=external-b")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillReloader reloader = context.getBean(SkillReloader.class);
                        SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                        String initial = reloader.snapshot("default").generationId();
                        assertThat(reloader.validate("default", documents, candidateConfiguration(first, "missing.key")).valid()).isTrue();
                        assertThatThrownBy(() -> reloader.prepare("default", documents, candidateConfiguration(first, "missing.key")))
                                .isInstanceOf(SkillReloadException.class).hasMessageNotContaining("external-a");
                        assertThat(reloader.snapshot("default").generationId()).isEqualTo(initial);
                        PreparedSkillUpdate a = reloader.prepare("default", documents, candidateConfiguration(first, "candidate.a"));
                        assertThat(first.getRequestCount()).isZero();
                        reloader.publish(a);
                        var pending = handoff.handoff("default-candidateSkill", Map.of());
                        assertThat(pending.generationId()).isEqualTo(a.generationId());
                        PreparedSkillUpdate b = reloader.prepare("default", documents, candidateConfiguration(second, "candidate.b"));
                        assertThat(second.getRequestCount()).isZero();
                        reloader.publish(b);
                        assertThat(pending.invoke()).isEqualTo("first");
                        assertThat(context.getBean(SkillTemplate.class).invoke("default-candidateSkill", Map.of())).isEqualTo("second");
                        assertThatThrownBy(() -> reloader.prepare("default", documents, new ExecutionConfiguration("loomspan: {}")))
                                .isInstanceOf(SkillReloadException.class);
                        PreparedSkillUpdate complete = reloader.prepare("default", documents, candidateConfiguration(second, "candidate.b"));
                        assertThat(complete.snapshot().skill("default-candidateSkill")).isPresent();
                        reloader.publish(complete);
                        assertThat(complete.generationId()).isNotEqualTo(b.generationId());
                    });
            assertThat(first.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getHeader("Authorization"))
                    .isEqualTo("Bearer external-a");
            assertThat(second.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS).getHeader("Authorization"))
                    .isEqualTo("Bearer external-b");
        }
    }

    private static MockResponse modelResponse(String content)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"candidate","object":"chat.completion","created":1,"model":"compact",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(content));
    }

    private static ExecutionConfiguration candidateConfiguration(MockWebServer server, String keyReference)
    {
        return candidateConfiguration(server, keyReference, 3);
    }

    private static ExecutionConfiguration candidateConfiguration(MockWebServer server, String keyReference,
            int maxAttempts)
    {
        return new ExecutionConfiguration("""
                loomspan:
                  connections:
                    candidate:
                      driver: openai
                      base-url: %s
                      api-key-ref: %s
                      provider-retry:
                        max-attempts: %d
                        initial-backoff: 0ms
                        max-backoff: 0ms
                        jitter: 0
                  models:
                    selected:
                      connection: candidate
                      provider-model: compact
                """.formatted(server.url("/v1"), keyReference, maxAttempts));
    }

    @Test
    void publishesNewModelAliasAndSkillAsOneCandidate(@TempDir Path directory) throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id":"candidate","object":"chat.completion","created":1,"model":"candidate-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"candidate"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """));
            String document = "name: candidateSkill\ndescription: candidate skill\nmodel: candidate-model\nplanning_mode: false\n";
            ExecutionConfiguration configuration = new ExecutionConfiguration("""
                    loomspan:
                      connections:
                        candidate:
                          driver: openai
                          base-url: %s
                          api-key-ref: candidate.key
                      models:
                        candidate-model:
                          connection: candidate
                          provider-model: candidate-model
                      session:
                        quotas:
                          max-model-calls: 1
                      execution-trace:
                        persistence: always
                    """.formatted(server.url("/v1")));
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                            LoomspanAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                    .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml", "candidate.key=external-key")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillReloader reloader = context.getBean(SkillReloader.class);
                        var documents = List.of(new SkillDocument("candidate", document));
                        assertThat(reloader.validate("default", documents, configuration).valid()).isTrue();
                        assertThat(server.getRequestCount()).isZero();
                        try (PreparedSkillUpdate prepared = reloader.prepare("default", documents, configuration))
                        {
                            assertThat(prepared.snapshot().skill("default-candidateSkill")).isPresent();
                            assertThat(server.getRequestCount()).isZero();
                            reloader.publish(prepared);
                        }
                        assertThat(context.getBean(SkillTemplate.class).invoke("default-candidateSkill", Map.of()))
                                .isEqualTo("candidate");
                    });
            assertThat(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
        }
    }
    @Test
    void publicValidationRereadsConfiguredResourcesAndKeepsSuppliedInputIsolated(@TempDir Path directory)
            throws Exception
    {
        Path configured = directory.resolve("configured.yaml");
        Files.writeString(configured, "name: configured\ndescription: configured\nexecutor: fixture\n");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(ExecutorConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    String initial = reloader.snapshot("default").generationId();
                    SkillValidationResult configuredFeedback = reloader.validate("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(configuredFeedback.valid()).isTrue();
                    assertThat(configuredFeedback.skills().stream()
                            .filter(skill -> skill.kind() == SkillKind.JAVA)
                            .map(ValidatedSkill::name).toList()).containsExactly("default-configured");
                    String supplied = "name: supplied\ndescription: supplied\nexecutor: fixture\n";
                    var documents = List.of(new SkillDocument(directory.resolve("absent.yaml").toString(), supplied));
                    SkillValidationResult proposed = reloader.validate("default", documents, ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(proposed.valid()).isTrue();
                    List<String> proposedExecutorNames = proposed.skills().stream()
                            .filter(skill -> skill.kind() == SkillKind.JAVA)
                            .map(ValidatedSkill::name).toList();
                    assertThat(proposedExecutorNames).containsExactly("default-supplied");
                    assertThat(Files.exists(directory.resolve("absent.yaml"))).isFalse();
                    assertThat(reloader.snapshot("default").generationId()).isEqualTo(initial);
                    assertThat(reloader.snapshot("default").skill("default-configured")).isPresent();
                    assertThat(reloader.snapshot("default").skill("default-supplied")).isEmpty();
                    PreparedSkillUpdate candidate = reloader.prepare("default", documents, ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(candidate.snapshot().skill("default-supplied")).isPresent();
                    try { Files.writeString(configured, "name: broken\ndescription: broken\nexecutor: fixture\nmodel: forbidden\n"); }
                    catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                    assertThat(reloader.validate("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))).valid()).isFalse();
                    assertThat(reloader.validate("default", documents, ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))).valid()).isTrue();
                    assertThatThrownBy(() -> reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)))).isInstanceOf(SkillReloadException.class);
                    reloader.publish(candidate);
                    assertThat(reloader.snapshot("default").skill("default-supplied")).isPresent();
                    assertThat(reloader.snapshot("default").skill("default-broken")).isEmpty();
                });
        assertThat(Files.readString(configured)).contains("model: forbidden");
    }
    @Test
    void retirementWaitsForPendingOldGenerationThenCleansUpAfterRelease(@TempDir Path directory)
    {
        String yaml = "name: leaf\ndescription: EXECUTOR leaf\nexecutor: fixture\n";
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(ExecutorConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                    List<String> retired = new CopyOnWriteArrayList<>();
                    try (AutoCloseable registration = reloader.onGenerationRetired(id -> {
                        retired.add(id);
                        ExecutorConfiguration.config.remove(id);
                    }))
                    {
                        String initial = reloader.snapshot("default").generationId();
                        ExecutorConfiguration.config.put(initial, "initial");
                        PreparedSkillUpdate first = reloader.prepare("default", List.of(new SkillDocument("A", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                        ExecutorConfiguration.config.put(first.generationId(), "A");
                        reloader.publish(first);
                        assertThat(retired).containsExactly(initial);
                        var pending = handoff.handoff("default-leaf", Map.of("message", "old"));
                        assertThat(pending.generationId()).isEqualTo(first.generationId()).isNotBlank();
                        PreparedSkillUpdate second = reloader.prepare("default", List.of(new SkillDocument("B", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                        ExecutorConfiguration.config.put(second.generationId(), "B");
                        reloader.publish(second);
                        assertThat(retired).doesNotContain(first.generationId());
                        assertThat(ExecutorConfiguration.config).containsKey(first.generationId());
                        assertThat(pending.generationId()).isEqualTo(first.generationId());
                        pending.release();
                        assertThat(retired).containsExactly(initial, first.generationId());
                        assertThat(pending.generationId()).isEqualTo(first.generationId());
                        assertThat(ExecutorConfiguration.config).doesNotContainKey(first.generationId());
                        assertThat(ExecutorConfiguration.config).containsKey(second.generationId());
                        var invokedPending = handoff.handoff("default-leaf", Map.of("message", "older"));
                        assertThat(invokedPending.generationId()).isEqualTo(second.generationId());
                        PreparedSkillUpdate third = reloader.prepare("default", List.of(new SkillDocument("C", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                        ExecutorConfiguration.config.put(third.generationId(), "C");
                        reloader.publish(third);
                        assertThat(retired).doesNotContain(second.generationId());
                        assertThat(invokedPending.invoke()).isEqualTo("B:older");
                        assertThat(ExecutorConfiguration.lastInvocation.get().executorConfigGenerationId())
                                .isEqualTo(invokedPending.generationId());
                        assertThat(retired).containsExactly(initial, first.generationId(), second.generationId());
                        assertThat(invokedPending.generationId()).isEqualTo(second.generationId());
                        assertThat(ExecutorConfiguration.config).containsKey(third.generationId());
                        var failingPending = handoff.handoff("default-leaf", Map.of("message", "fail"));
                        assertThat(failingPending.generationId()).isEqualTo(third.generationId());
                        PreparedSkillUpdate fourth = reloader.prepare("default", List.of(new SkillDocument("D", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                        ExecutorConfiguration.config.put(fourth.generationId(), "D");
                        reloader.publish(fourth);
                        assertThatThrownBy(failingPending::invoke).isInstanceOf(ai.loomspan.api.SkillException.class);
                        assertThat(ExecutorConfiguration.lastInvocation.get().executorConfigGenerationId())
                                .isEqualTo(failingPending.generationId());
                        assertThat(failingPending.generationId()).isEqualTo(third.generationId());
                        assertThat(retired).containsExactly(initial, first.generationId(),
                                second.generationId(), third.generationId());
                    }
                });
        ExecutorConfiguration.config.clear();
    }

    @Test
    void invokesSuppliedModelSkillThroughSupportedApi(@TempDir Path directory) throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"id":"done","object":"chat.completion","created":1,"model":"integration-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """));
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            ConfigurationPropertiesAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                            LoomspanAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                    .withPropertyValues(
                            "loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                            "loomspan.connections.integration.driver=openai",
                            "loomspan.connections.integration.base-url=" + server.url("/v1"),
                            "loomspan.connections.integration.api-key=integration-key",
                            "loomspan.models.integration.connection=integration",
                            "loomspan.models.integration.provider-model=integration-model",
                            "loomspan.session.mission-timeout=15s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        SkillReloader reloader = context.getBean(SkillReloader.class);
                        PreparedSkillUpdate candidate = reloader.prepare("default", List.of(new SkillDocument("model revision", """
                                name: modelFromMemory
                                description: Model from supplied YAML
                                model: integration
                                planning_mode: false
                                """)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                        reloader.publish(candidate);
                        assertThat(context.getBean(SkillTemplate.class).invoke("default-modelFromMemory", Map.of()))
                                .isEqualTo("done");
                    });
            assertThat(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void preparesSuppliedYamlWithoutFilesThroughSupportedApis(@TempDir Path directory)
    {
        String yaml = "name: suppliedLeaf\ndescription: Supplied EXECUTOR skill\nexecutor: fixture\n";
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(ExecutorConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    SkillCatalog startup = context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default");
                    assertThat(startup.skills()).isEmpty();
                    PreparedSkillUpdate candidate = reloader.prepare("default", List.of(new SkillDocument("opaque label", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(candidate.snapshot().skill("default-suppliedLeaf")).isPresent();
                    assertThat(reloader.snapshot("default").skills()).isEmpty();
                    ExecutorConfiguration.config.put(candidate.generationId(), "S");
                    reloader.publish(candidate);
                    assertThat(context.getBean(SkillTemplate.class).invoke("default-suppliedLeaf", Map.of("message", "hi")))
                            .isEqualTo("S:hi");
                    String activeId = reloader.snapshot("default").generationId();
                    assertThatThrownBy(() -> reloader.prepare("default", List.of(new SkillDocument("bad source", "invalid: [")), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class))))
                            .isInstanceOf(SkillReloadException.class).hasStackTraceContaining("bad source");
                    assertThat(reloader.snapshot("default").generationId()).isEqualTo(activeId);
                    assertThat(startup.skills()).isEmpty();
                    var admitted = context.getBean(SkillInvocationHandoff.class)
                            .handoff("default-suppliedLeaf", Map.of("message", "old"));
                    PreparedSkillUpdate replacement = reloader.prepare("default", List.of(new SkillDocument("other label", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    ExecutorConfiguration.config.put(replacement.generationId(), "T");
                    reloader.publish(replacement);
                    assertThat(admitted.invoke()).isEqualTo("S:old");
                    assertThat(context.getBean(SkillTemplate.class).invoke("default-suppliedLeaf", Map.of("message", "new")))
                            .isEqualTo("T:new");
                    PreparedSkillUpdate configured = reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(configured.snapshot().skills()).isEmpty();
                    PreparedSkillUpdate competingSupplied = reloader.prepare("default", List.of(new SkillDocument("again", yaml)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    reloader.publish(configured);
                    assertThat(reloader.snapshot("default").skills()).isEmpty();
                    assertThatThrownBy(() -> reloader.publish(competingSupplied))
                            .isInstanceOf(SkillReloadException.class).hasMessageContaining("stale");
                });
        ExecutorConfiguration.config.clear();
    }

    @Test
    void resubmitsApplicationSnapshotAfterRestart(@TempDir Path directory)
    {
        List<SkillDocument> saved = List.of(new SkillDocument("durable revision", """
                name: restored
                description: Restored EXECUTOR skill
                executor: fixture
                """));
        String first = restoreInFreshContext(directory, saved);
        String second = restoreInFreshContext(directory, saved);
        assertThat(second).isNotEqualTo(first);
    }

    private String restoreInFreshContext(Path directory, List<SkillDocument> saved)
    {
        final String[] generation = new String[1];
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(ExecutorConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    assertThat(reloader.snapshot("default").skills()).isEmpty();
                    PreparedSkillUpdate candidate = reloader.prepare("default", saved, ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    generation[0] = candidate.generationId();
                    ExecutorConfiguration.config.put(generation[0], "R");
                    reloader.publish(candidate);
                    assertThat(context.getBean(SkillTemplate.class).invoke("default-restored", Map.of()))
                            .isEqualTo("R:null");
                });
        ExecutorConfiguration.config.clear();
        return generation[0];
    }

    @Test
    void preparesStagesAndPublishesThroughSupportedBeans(@TempDir Path directory) throws Exception
    {
        Path yaml = directory.resolve("reload.yaml");
        String valid = """
                name: reloadLeaf
                description: Initial description
                executor: fixture
                input_schema:
                  type: object
                  properties:
                    message:
                      type: string
                    generationId:
                      type: string
                  required: [message]
                """;
        Files.writeString(yaml, valid);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(ExecutorConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SkillReloader.class);
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    SkillTemplate template = context.getBean(SkillTemplate.class);
                    SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                    SkillCatalog startup = context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default");
                    String initial = startup.generationId();
                    ExecutorConfiguration.config.put(initial, "A");
                    assertThat(template.invoke("default-reloadLeaf", Map.of("message", "hello"))).isEqualTo("A:hello");
                    assertThat(template.invoke("default-reloadLeaf", Map.of("message", "trusted", "generationId", "spoofed")))
                            .isEqualTo("A:trusted");
                    var admittedOld = handoff.handoff("default-reloadLeaf", Map.of("message", "delayed", "generationId", "spoofed"));
                    assertThat(admittedOld.generationId()).isEqualTo(initial).isNotBlank();

                    PreparedSkillUpdate candidate = reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(candidate.generationId()).isNotEqualTo(initial);
                    assertThat(candidate.snapshot().generationId()).isEqualTo(candidate.generationId());
                    ExecutorConfiguration.config.put(candidate.generationId(), "B");
                    try { Files.writeString(yaml, "invalid: ["); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    reloader.publish(candidate);

                    assertThat(startup.generationId()).isEqualTo(initial);
                    assertThat(startup.skill("default-reloadLeaf")).isPresent();
                    assertThat(reloader.snapshot("default").generationId()).isEqualTo(candidate.generationId());
                    assertThat(admittedOld.generationId()).isEqualTo(initial);
                    assertThat(admittedOld.invoke()).isEqualTo("A:delayed");
                    assertThat(ExecutorConfiguration.lastInvocation.get().executorConfigGenerationId()).isEqualTo(initial);
                    var admittedNew = handoff.handoff("default-reloadLeaf", new ReloadRequest("new"));
                    assertThat(admittedNew.generationId()).isEqualTo(candidate.generationId());
                    assertThat(admittedNew.invoke()).isEqualTo("B:new");
                    assertThat(ExecutorConfiguration.lastInvocation.get().executorConfigGenerationId())
                            .isEqualTo(admittedNew.generationId());
                    assertThat(template.invoke("default-reloadLeaf", Map.of("message", "world"))).isEqualTo("B:world");
                    assertThatThrownBy(() -> reloader.publish(candidate)).isInstanceOf(SkillReloadException.class);
                    assertThatThrownBy(() -> reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)))).isInstanceOf(SkillReloadException.class);

                    try { Files.writeString(yaml, valid); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    PreparedSkillUpdate sameYaml = reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    PreparedSkillUpdate competing = reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(sameYaml.generationId()).isNotEqualTo(candidate.generationId())
                            .isNotEqualTo(competing.generationId());
                    ExecutorConfiguration.config.put(sameYaml.generationId(), "C");
                    reloader.publish(sameYaml);
                    assertThat(template.invoke("default-reloadLeaf", Map.of("message", "again"))).isEqualTo("C:again");
                    assertThatThrownBy(() -> reloader.publish(competing)).isInstanceOf(SkillReloadException.class)
                            .hasMessageContaining("stale");

                    try { Files.delete(yaml); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    PreparedSkillUpdate empty = reloader.prepare("default", ai.loomspan.testkit.PublicationFixtures.documents(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)), ai.loomspan.testkit.PublicationFixtures.configuration(context.getBean(ai.loomspan.autoconfigure.LoomspanProperties.class)));
                    assertThat(empty.snapshot().skills()).isEmpty();
                    reloader.publish(empty);
                    assertThat(reloader.snapshot("default").skills()).isEmpty();
                    assertThat(startup.skill("default-reloadLeaf")).isPresent();
                });
        ExecutorConfiguration.config.clear();
    }

    @Configuration(proxyBeanMethods = false)
    static class ExecutorConfiguration
    {
        static final Map<String, String> config = new ConcurrentHashMap<>();
        static final AtomicReference<SkillExecutorInvocation> lastInvocation = new AtomicReference<>();

        @Bean
        FixtureExecutor handler()
        {
            return new FixtureExecutor(invocation -> {
                lastInvocation.set(invocation);
                if ("fail".equals(invocation.input().get("message")))
                    throw new IllegalStateException("handler failed");
                return config.get(invocation.executorConfigGenerationId()) + ":" + invocation.input().get("message");
            });
        }
    }

    private record ReloadRequest(String message) {}
}
