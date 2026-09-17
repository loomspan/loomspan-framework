package ai.loomspan.integration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillDocument;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicSkillReloadIntegrationTest
{
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
                    var admittedOld = handoff.handoff("reloadLeaf", Map.of("message", "delayed"));

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
                    assertThat(admittedOld.invoke()).isEqualTo("A:delayed");
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

        @Bean
        RestSkillHandler handler()
        {
            return invocation -> config.get(invocation.generationId()) + ":" + invocation.input().get("message");
        }
    }
}
