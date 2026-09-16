package ai.loomspan.integration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicSkillReloadIntegrationTest
{
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
