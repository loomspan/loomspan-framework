package ai.loomspan.integration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
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
    void publicValidationRereadsConfiguredResourcesAndKeepsSuppliedInputIsolated(@TempDir Path directory)
            throws Exception
    {
        Path configured = directory.resolve("configured.yaml");
        Files.writeString(configured, "name: configured\ndescription: configured\nrest: true\n");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(RestConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    String initial = reloader.snapshot().generationId();
                    SkillValidationResult configuredFeedback = reloader.validate();
                    assertThat(configuredFeedback.valid()).isTrue();
                    assertThat(configuredFeedback.skills().stream()
                            .filter(skill -> skill.kind() == SkillKind.REST)
                            .map(ValidatedSkill::name).toList()).containsExactly("configured");
                    String supplied = "name: supplied\ndescription: supplied\nrest: true\n";
                    var documents = List.of(new SkillDocument(directory.resolve("absent.yaml").toString(), supplied));
                    SkillValidationResult proposed = reloader.validate(documents);
                    assertThat(proposed.valid()).isTrue();
                    List<String> proposedRestNames = proposed.skills().stream()
                            .filter(skill -> skill.kind() == SkillKind.REST)
                            .map(ValidatedSkill::name).toList();
                    assertThat(proposedRestNames).containsExactly("supplied");
                    assertThat(Files.exists(directory.resolve("absent.yaml"))).isFalse();
                    assertThat(reloader.snapshot().generationId()).isEqualTo(initial);
                    assertThat(reloader.snapshot().skill("configured")).isPresent();
                    assertThat(reloader.snapshot().skill("supplied")).isEmpty();
                    PreparedSkillUpdate candidate = reloader.prepare(documents);
                    assertThat(candidate.snapshot().skill("supplied")).isPresent();
                    try { Files.writeString(configured, "name: broken\ndescription: broken\nrest: true\nmodel: forbidden\n"); }
                    catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                    assertThat(reloader.validate().valid()).isFalse();
                    assertThat(reloader.validate(documents).valid()).isTrue();
                    assertThatThrownBy(reloader::prepare).isInstanceOf(SkillReloadException.class);
                    reloader.publish(candidate);
                    assertThat(reloader.snapshot().skill("supplied")).isPresent();
                    assertThat(reloader.snapshot().skill("broken")).isEmpty();
                });
        assertThat(Files.readString(configured)).contains("model: forbidden");
    }
    @Test
    void retirementWaitsForPendingOldGenerationThenCleansUpAfterRelease(@TempDir Path directory)
    {
        String yaml = "name: leaf\ndescription: REST leaf\nrest: true\n";
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(RestConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                    List<String> retired = new CopyOnWriteArrayList<>();
                    try (AutoCloseable registration = reloader.onGenerationRetired(id -> {
                        retired.add(id);
                        RestConfiguration.config.remove(id);
                    }))
                    {
                        String initial = reloader.snapshot().generationId();
                        RestConfiguration.config.put(initial, "initial");
                        PreparedSkillUpdate first = reloader.prepare(List.of(new SkillDocument("A", yaml)));
                        RestConfiguration.config.put(first.generationId(), "A");
                        reloader.publish(first);
                        assertThat(retired).containsExactly(initial);
                        var pending = handoff.handoff("leaf", Map.of("message", "old"));
                        assertThat(pending.generationId()).isEqualTo(first.generationId()).isNotBlank();
                        PreparedSkillUpdate second = reloader.prepare(List.of(new SkillDocument("B", yaml)));
                        RestConfiguration.config.put(second.generationId(), "B");
                        reloader.publish(second);
                        assertThat(retired).doesNotContain(first.generationId());
                        assertThat(RestConfiguration.config).containsKey(first.generationId());
                        assertThat(pending.generationId()).isEqualTo(first.generationId());
                        pending.release();
                        assertThat(retired).containsExactly(initial, first.generationId());
                        assertThat(pending.generationId()).isEqualTo(first.generationId());
                        assertThat(RestConfiguration.config).doesNotContainKey(first.generationId());
                        assertThat(RestConfiguration.config).containsKey(second.generationId());
                        var invokedPending = handoff.handoff("leaf", Map.of("message", "older"));
                        assertThat(invokedPending.generationId()).isEqualTo(second.generationId());
                        PreparedSkillUpdate third = reloader.prepare(List.of(new SkillDocument("C", yaml)));
                        RestConfiguration.config.put(third.generationId(), "C");
                        reloader.publish(third);
                        assertThat(retired).doesNotContain(second.generationId());
                        assertThat(invokedPending.invoke()).isEqualTo("B:older");
                        assertThat(RestConfiguration.lastInvocation.get().generationId())
                                .isEqualTo(invokedPending.generationId());
                        assertThat(retired).containsExactly(initial, first.generationId(), second.generationId());
                        assertThat(invokedPending.generationId()).isEqualTo(second.generationId());
                        assertThat(RestConfiguration.config).containsKey(third.generationId());
                        var failingPending = handoff.handoff("leaf", Map.of("message", "fail"));
                        assertThat(failingPending.generationId()).isEqualTo(third.generationId());
                        PreparedSkillUpdate fourth = reloader.prepare(List.of(new SkillDocument("D", yaml)));
                        RestConfiguration.config.put(fourth.generationId(), "D");
                        reloader.publish(fourth);
                        assertThatThrownBy(failingPending::invoke).isInstanceOf(ai.loomspan.api.SkillException.class);
                        assertThat(RestConfiguration.lastInvocation.get().generationId())
                                .isEqualTo(failingPending.generationId());
                        assertThat(failingPending.generationId()).isEqualTo(third.generationId());
                        assertThat(retired).containsExactly(initial, first.generationId(),
                                second.generationId(), third.generationId());
                    }
                });
        RestConfiguration.config.clear();
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
                        PreparedSkillUpdate candidate = reloader.prepare(List.of(new SkillDocument("model revision", """
                                name: modelFromMemory
                                description: Model from supplied YAML
                                model: integration
                                planning_mode: false
                                """)));
                        reloader.publish(candidate);
                        assertThat(context.getBean(SkillTemplate.class).invoke("modelFromMemory", Map.of()))
                                .isEqualTo("done");
                    });
            assertThat(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void preparesSuppliedYamlWithoutFilesThroughSupportedApis(@TempDir Path directory)
    {
        String yaml = "name: suppliedLeaf\ndescription: Supplied REST skill\nrest: true\n";
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                        LoomspanAutoConfiguration.class,
                        ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(RestConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    SkillCatalog startup = context.getBean(SkillCatalog.class);
                    assertThat(startup.skills()).isEmpty();
                    PreparedSkillUpdate candidate = reloader.prepare(List.of(new SkillDocument("opaque label", yaml)));
                    assertThat(candidate.snapshot().skill("suppliedLeaf")).isPresent();
                    assertThat(reloader.snapshot().skills()).isEmpty();
                    RestConfiguration.config.put(candidate.generationId(), "S");
                    reloader.publish(candidate);
                    assertThat(context.getBean(SkillTemplate.class).invoke("suppliedLeaf", Map.of("message", "hi")))
                            .isEqualTo("S:hi");
                    String activeId = reloader.snapshot().generationId();
                    assertThatThrownBy(() -> reloader.prepare(List.of(new SkillDocument("bad source", "invalid: ["))))
                            .isInstanceOf(SkillReloadException.class).hasStackTraceContaining("bad source");
                    assertThat(reloader.snapshot().generationId()).isEqualTo(activeId);
                    assertThat(startup.skills()).isEmpty();
                    var admitted = context.getBean(SkillInvocationHandoff.class)
                            .handoff("suppliedLeaf", Map.of("message", "old"));
                    PreparedSkillUpdate replacement = reloader.prepare(List.of(new SkillDocument("other label", yaml)));
                    RestConfiguration.config.put(replacement.generationId(), "T");
                    reloader.publish(replacement);
                    assertThat(admitted.invoke()).isEqualTo("S:old");
                    assertThat(context.getBean(SkillTemplate.class).invoke("suppliedLeaf", Map.of("message", "new")))
                            .isEqualTo("T:new");
                    PreparedSkillUpdate configured = reloader.prepare();
                    assertThat(configured.snapshot().skills()).isEmpty();
                    PreparedSkillUpdate competingSupplied = reloader.prepare(List.of(new SkillDocument("again", yaml)));
                    reloader.publish(configured);
                    assertThat(reloader.snapshot().skills()).isEmpty();
                    assertThatThrownBy(() -> reloader.publish(competingSupplied))
                            .isInstanceOf(SkillReloadException.class).hasMessageContaining("stale");
                });
        RestConfiguration.config.clear();
    }

    @Test
    void resubmitsApplicationSnapshotAfterRestart(@TempDir Path directory)
    {
        List<SkillDocument> saved = List.of(new SkillDocument("durable revision", """
                name: restored
                description: Restored REST skill
                rest: true
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
                .withUserConfiguration(RestConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    assertThat(reloader.snapshot().skills()).isEmpty();
                    PreparedSkillUpdate candidate = reloader.prepare(saved);
                    generation[0] = candidate.generationId();
                    RestConfiguration.config.put(generation[0], "R");
                    reloader.publish(candidate);
                    assertThat(context.getBean(SkillTemplate.class).invoke("restored", Map.of()))
                            .isEqualTo("R:null");
                });
        RestConfiguration.config.clear();
        return generation[0];
    }

    @Test
    void preparesStagesAndPublishesThroughSupportedBeans(@TempDir Path directory) throws Exception
    {
        Path yaml = directory.resolve("reload.yaml");
        String valid = """
                name: reloadLeaf
                description: Initial description
                rest: true
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
                .withUserConfiguration(RestConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SkillReloader.class);
                    SkillReloader reloader = context.getBean(SkillReloader.class);
                    SkillTemplate template = context.getBean(SkillTemplate.class);
                    SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                    SkillCatalog startup = context.getBean(SkillCatalog.class);
                    String initial = startup.generationId();
                    RestConfiguration.config.put(initial, "A");
                    assertThat(template.invoke("reloadLeaf", Map.of("message", "hello"))).isEqualTo("A:hello");
                    assertThat(template.invoke("reloadLeaf", Map.of("message", "trusted", "generationId", "spoofed")))
                            .isEqualTo("A:trusted");
                    var admittedOld = handoff.handoff("reloadLeaf", Map.of("message", "delayed", "generationId", "spoofed"));
                    assertThat(admittedOld.generationId()).isEqualTo(initial).isNotBlank();

                    PreparedSkillUpdate candidate = reloader.prepare();
                    assertThat(candidate.generationId()).isNotEqualTo(initial);
                    assertThat(candidate.snapshot().generationId()).isEqualTo(candidate.generationId());
                    RestConfiguration.config.put(candidate.generationId(), "B");
                    try { Files.writeString(yaml, "invalid: ["); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    reloader.publish(candidate);

                    assertThat(startup.generationId()).isEqualTo(initial);
                    assertThat(startup.skill("reloadLeaf")).isPresent();
                    assertThat(reloader.snapshot().generationId()).isEqualTo(candidate.generationId());
                    assertThat(admittedOld.generationId()).isEqualTo(initial);
                    assertThat(admittedOld.invoke()).isEqualTo("A:delayed");
                    assertThat(RestConfiguration.lastInvocation.get().generationId()).isEqualTo(initial);
                    var admittedNew = handoff.handoff("reloadLeaf", new ReloadRequest("new"));
                    assertThat(admittedNew.generationId()).isEqualTo(candidate.generationId());
                    assertThat(admittedNew.invoke()).isEqualTo("B:new");
                    assertThat(RestConfiguration.lastInvocation.get().generationId())
                            .isEqualTo(admittedNew.generationId());
                    assertThat(template.invoke("reloadLeaf", Map.of("message", "world"))).isEqualTo("B:world");
                    assertThatThrownBy(() -> reloader.publish(candidate)).isInstanceOf(SkillReloadException.class);
                    assertThatThrownBy(reloader::prepare).isInstanceOf(SkillReloadException.class);

                    try { Files.writeString(yaml, valid); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    PreparedSkillUpdate sameYaml = reloader.prepare();
                    PreparedSkillUpdate competing = reloader.prepare();
                    assertThat(sameYaml.generationId()).isNotEqualTo(candidate.generationId())
                            .isNotEqualTo(competing.generationId());
                    RestConfiguration.config.put(sameYaml.generationId(), "C");
                    reloader.publish(sameYaml);
                    assertThat(template.invoke("reloadLeaf", Map.of("message", "again"))).isEqualTo("C:again");
                    assertThatThrownBy(() -> reloader.publish(competing)).isInstanceOf(SkillReloadException.class)
                            .hasMessageContaining("stale");

                    try { Files.delete(yaml); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                    PreparedSkillUpdate empty = reloader.prepare();
                    assertThat(empty.snapshot().skills()).isEmpty();
                    reloader.publish(empty);
                    assertThat(reloader.snapshot().skills()).isEmpty();
                    assertThat(startup.skill("reloadLeaf")).isPresent();
                });
        RestConfiguration.config.clear();
    }

    @Configuration(proxyBeanMethods = false)
    static class RestConfiguration
    {
        static final Map<String, String> config = new ConcurrentHashMap<>();
        static final AtomicReference<RestSkillInvocation> lastInvocation = new AtomicReference<>();

        @Bean
        RestSkillHandler handler()
        {
            return invocation -> {
                lastInvocation.set(invocation);
                if ("fail".equals(invocation.input().get("message")))
                    throw new IllegalStateException("handler failed");
                return config.get(invocation.generationId()) + ":" + invocation.input().get("message");
            };
        }
    }

    private record ReloadRequest(String message) {}
}
