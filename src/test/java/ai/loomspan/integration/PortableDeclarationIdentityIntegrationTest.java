package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PortableDeclarationIdentityIntegrationTest {
    private static final ExecutionConfiguration EMPTY = new ExecutionConfiguration("loomspan: {}");
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(App.class)
                .withPropertyValues("loomspan.skills.locations=classpath:/portable-declaration-no-files/*.yaml");
    }
    private static SkillDocument document(String source, String name) {
        return new SkillDocument(source, "name: '" + name + "'\ndescription: fixture\nexecutor: fixture\n");
    }
    @Test void nestedStartupYamlAlwaysBelongsToDefault(@TempDir Path directory) throws Exception {
        Path nested = Files.createDirectories(directory.resolve("other"));
        Files.writeString(nested.resolve("lookup.yaml"), document("unused", "lookup-3pl").yaml());
        runner().withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "**/*.yaml").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillReloader.class).snapshot("default").skills())
                    .extracting(SkillDescriptor::name).contains("default:lookup-3pl");
        });
    }
    @Test void suppliedYamlUsesExactOwnerAndEnforcesNamesAndLimits() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var reloader = context.getBean(SkillReloader.class);
            for (String owner : List.of("order-support", "other")) {
                var documents = List.of(document("Orders import #42", "lookup-3pl"));
                var result = reloader.validate(owner, documents, EMPTY);
                assertThat(result.valid()).as(result.issues().toString()).isTrue();
                assertThat(result.skills()).extracting(ValidatedSkill::name).contains(owner + ":lookup-3pl");
                try (var candidate = reloader.prepare(owner, documents, EMPTY)) {
                    assertThat(candidate.snapshot().namespace()).isEqualTo(owner);
                    assertThat(candidate.snapshot().skills()).extracting(SkillDescriptor::name).contains(owner + ":lookup-3pl");
                }
            }
            var accepted = List.of(document("limit", "s".repeat(49)));
            assertThat(reloader.validate("order-support", accepted, EMPTY).valid()).isTrue();
            try (var candidate = reloader.prepare("order-support", accepted, EMPTY)) {
                assertThat(candidate.snapshot().skills()).extracting(SkillDescriptor::name).contains("order-support:" + "s".repeat(49));
            }
            for (String name : List.of("Lookup", " lookup", "lookup ", "lookup_name", "3pl-lookup", "lookup--3pl", "myclaudeskill", "s".repeat(50))) {
                var invalid = List.of(document("Orders/lookup.yaml", name));
                var result = reloader.validate("order-support", invalid, EMPTY);
                assertThat(result.valid()).as(name).isFalse();
                assertThat(result.issues()).anySatisfy(issue -> assertThat(issue.sourceName()).isEqualTo("Orders/lookup.yaml"));
                assertThatThrownBy(() -> reloader.prepare("order-support", invalid, EMPTY)).isInstanceOf(SkillReloadException.class);
            }
            for (String owner : List.of("Orders", " orders", "orders_", "claude")) {
                assertThatThrownBy(() -> reloader.validate(owner, List.of(), EMPTY)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> reloader.prepare(owner, List.of(), EMPTY)).isInstanceOf(IllegalArgumentException.class);
            }
        });
    }
    @Test void callableCollisionsSpanYamlAndJavaWhileExecutorIndexIsIndependent() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var reloader = context.getBean(SkillReloader.class);
            var duplicateYaml = List.of(document("first", "lookup"), document("second", "lookup"));
            assertThat(reloader.validate("other", duplicateYaml, EMPTY).valid()).isFalse();
            assertThatThrownBy(() -> reloader.prepare("other", duplicateYaml, EMPTY)).isInstanceOf(SkillReloadException.class);
            var duplicateJava = List.of(document("collision", "fixed"));
            assertThat(reloader.validate("default", duplicateJava, EMPTY).valid()).isFalse();
            assertThatThrownBy(() -> reloader.prepare("default", duplicateJava, EMPTY)).isInstanceOf(SkillReloadException.class);
            var crossRole = List.of(document("cross-role", "fixture"));
            assertThat(reloader.validate("default", crossRole, EMPTY).valid()).isTrue();
            try (var candidate = reloader.prepare("default", crossRole, EMPTY)) {
                assertThat(candidate.snapshot().skills()).extracting(SkillDescriptor::name).contains("default:fixture", "default:fixed");
            }
            assertThat(reloader.validate("other", duplicateJava, EMPTY).valid()).isTrue();
        });
    }
    @ParameterizedTest
    @ValueSource(strings = {"Orders import #42", "Orders/lookup.yaml"})
    void suppliedLabelsRemainExactThroughValidationAndPreparation(String label) {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var reloader = context.getBean(SkillReloader.class);
            var documents = List.of(document(label, "lookup"));
            var result = reloader.validate("orders", documents, EMPTY);
            assertThat(result.valid()).as(result.issues().toString()).isTrue();
            try (var candidate = reloader.prepare("orders", documents, EMPTY)) {
                assertThat(candidate.snapshot().namespace()).isEqualTo("orders");
            }
            try (var candidate = reloader.prepare("orders", documents, EMPTY, Map.of())) {
                assertThat(candidate.snapshot().namespace()).isEqualTo("orders");
            }
            var invalid = List.of(document(label, "Lookup"));
            assertThat(reloader.validate("orders", invalid, EMPTY).issues())
                    .anySatisfy(issue -> assertThat(issue.sourceName()).isEqualTo(label));
            assertThatThrownBy(() -> reloader.prepare("orders", invalid, EMPTY))
                    .isInstanceOf(SkillReloadException.class).hasMessageContaining(label);
            assertThatThrownBy(() -> reloader.validate("Orders", documents, EMPTY))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reloader.prepare("Orders", documents, EMPTY))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }
    @Test void startupRejectsInvalidAndDuplicateCallableDeclarations(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("lookup.yaml");
        for (String name : List.of("Lookup", " lookup ", "lookup_name", "anthropic-tools", "fixed")) {
            Files.writeString(file, document("unused", name).yaml());
            runner().withPropertyValues("loomspan.skills.locations=" + file.toUri()).run(context -> assertThat(context).hasFailed());
        }
        Files.writeString(file, document("unused", "fixture").yaml());
        runner().withPropertyValues("loomspan.skills.locations=" + file.toUri()).run(context -> assertThat(context).hasNotFailed());
        Files.writeString(directory.resolve("duplicate.yaml"), document("unused", "fixture").yaml());
        runner().withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml")
                .run(context -> assertThat(context).hasFailed());
    }
    @Configuration(proxyBeanMethods = false)
    static class App {
        @Bean Methods methods() { return new Methods(); }
    }
    static class Methods {
        @SkillMethod(name="fixed", description="fixed") String callable() { return "fixed"; }
        @SkillExecutor(name="fixture") String local(SkillExecutorInvocation invocation) { return "ok"; }
        @SkillExecutor(namespace="order-support", name="fixture") String orders(SkillExecutorInvocation invocation) { return "ok"; }
        @SkillExecutor(namespace="other", name="fixture") String other(SkillExecutorInvocation invocation) { return "ok"; }
        @SkillExecutor(namespace="orders", name="fixture") String orderFixture(SkillExecutorInvocation invocation) { return "ok"; }
    }
}
