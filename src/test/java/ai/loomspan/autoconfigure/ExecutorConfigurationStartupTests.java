package ai.loomspan.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutorConfigurationStartupTests {
    @TempDir Path directory;

    @Test
    void startupYamlAndPublishedYamlPreserveEquivalentExecutorData() throws Exception {
        Path file = directory.resolve("application.yml");
        Files.writeString(file, """
                loomspan:
                  executor-config:
                    default:
                      operations:
                        Nil: null
                        EmptyMap: {}
                        EmptyList: []
                        List: [null, {}, [], 7, 2.5, true, "7"]
                        Upper: A
                        upper: B
                        "literal.dot": {value: kept}
                        literal: {dot: {value: separate}}
                        api-key-ref: missing.external.property
                        placeholder: "${UNSET_EXECUTOR_VALUE}"
                    infrastructure:
                      databases: {inventory: {database: arbitrary, sql: "select :input"}}
                """);
        SpringApplication application = new SpringApplication(Binding.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        try (var context = application.run("--spring.config.location=" + file.toUri(), "--spring.main.banner-mode=off")) {
            var startup = context.getBean(LoomspanProperties.class).getExecutorConfig();
            assertThat(startup.namespaces()).containsOnlyKeys("default", "infrastructure");
            String publication = Files.readString(file).substring(0, Files.readString(file).indexOf("    infrastructure:"));
            var published = ai.loomspan.internal.skill.ExecutionConfigurationParser.parse("default",
                    new ai.loomspan.api.ExecutionConfiguration(publication), new org.springframework.core.env.StandardEnvironment(), false);
            assertThat(startup.namespaces().get("default")).isEqualTo(published.executorConfig().namespaces().get("default"));
            var data = (java.util.Map<String, Object>)startup.namespaces().get("default").get("operations");
            assertThat(data).containsEntry("Nil", null).containsEntry("Upper", "A").containsEntry("upper", "B")
                    .containsEntry("literal.dot", java.util.Map.of("value", "kept"))
                    .containsEntry("api-key-ref", "missing.external.property").containsEntry("placeholder", "${UNSET_EXECUTOR_VALUE}");
            assertThat((java.util.Map<?, ?>)data.get("EmptyMap")).isEmpty();
            assertThat((java.util.List<?>)data.get("EmptyList")).isEmpty();
            var list = (java.util.List<?>)data.get("List");
            assertThat(list.get(0)).isNull();
            assertThat(list.get(3)).isInstanceOf(Integer.class).isEqualTo(7);
            assertThat(list.get(4)).isInstanceOf(Double.class).isEqualTo(2.5);
            assertThat(list.get(5)).isEqualTo(true);
            assertThat(list.get(6)).isEqualTo("7");
        }
    }

    private org.springframework.context.ConfigurableApplicationContext start(Path file, String... extra) {
        var application = new SpringApplication(Binding.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        var arguments = new java.util.ArrayList<String>();
        arguments.add("--spring.config.location=" + file.toUri());
        arguments.add("--spring.main.banner-mode=off");
        arguments.addAll(java.util.List.of(extra));
        return application.run(arguments.toArray(String[]::new));
    }

    @Test void profilesImportsEmptyDocumentsAndOrdinaryPropertiesKeepBootSemantics() throws Exception {
        Path imported = directory.resolve("imported.yaml");
        Files.writeString(imported, "loomspan: {executor-config: {default: {selected: imported, Nil: null}}}\n");
        Path file = directory.resolve("application.yaml");
        Files.writeString(file, """
                ---
                {}
                ---
                host: {value: actual, date: !!timestamp 2026-11-01}
                copied: ${host.value}
                spring.config.import: imported.yaml
                loomspan:
                  session: {max-depth: 6}
                  executor-config: {default: {selected: base}}
                ---
                spring.config.activate.on-profile: unused
                loomspan:
                  executor-config: []
                ---
                spring.config.activate.on-profile: selected
                loomspan:
                  executor-config: {default: {selected: profile, empty: []}}
                """);
        try (var context = start(file)) {
            assertThat(context.getEnvironment().getProperty("copied")).isEqualTo("actual");
            assertThat(context.getEnvironment().getProperty("host.date", java.util.Date.class)).isNotNull();
            assertThat(context.getBean(LoomspanProperties.class).getSession().getMaxDepth()).isEqualTo(6);
            assertThat(context.getBean(LoomspanProperties.class).getExecutorConfig().namespaces().get("default"))
                    .containsEntry("selected", "imported").containsEntry("Nil", null);
        }
        try (var context = start(file, "--spring.profiles.active=selected")) {
            assertThat(context.getBean(LoomspanProperties.class).getExecutorConfig().namespaces().get("default"))
                    .containsEntry("selected", "profile").containsEntry("empty", java.util.List.of()).doesNotContainKey("Nil");
        }
    }

    @Test void invalidActiveShapeAndDuplicateKeysFailStartup() throws Exception {
        for (String value : java.util.List.of("null", "[]", "{default: null}", "{default: []}")) {
            Path file = directory.resolve("invalid.yml");
            Files.writeString(file, "loomspan:\n  executor-config: " + value);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> start(file)).hasStackTraceContaining("executor-config");
        }
        Path duplicate = directory.resolve("duplicate.yml");
        Files.writeString(duplicate, "loomspan: {executor-config: {default: {key: A, key: B}}}");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> start(duplicate)).hasStackTraceContaining("duplicate");
    }

    @Test void leadingNullAndSequenceDocumentsDoNotBreakAssociation() throws Exception {
        for (String empty : java.util.List.of("[]", "~", "null", "''")) {
            Path file = directory.resolve("documents.yml");
            Files.writeString(file, "---\n" + empty + "\n---\nloomspan:\n  executor-config: {default: {selected: retained}}\n");
            try (var context = start(file)) {
                assertThat(context.getBean(LoomspanProperties.class).getExecutorConfig().namespaces().get("default"))
                        .containsEntry("selected", "retained");
            }
        }
    }

    @Test void yamlNullSpellingsAndAliasedContainersStayValuesAtBothIngresses() throws Exception {
        Path file = directory.resolve("values.yml");
        String yaml = """
                loomspan:
                  executor-config:
                    default:
                      template: &shared {Nil: ~, Values: [null, {}, []]}
                      copied: *shared
                      literal: "~"
                """;
        Files.writeString(file, yaml);
        try (var context = start(file)) {
            var startup = context.getBean(LoomspanProperties.class).getExecutorConfig().namespaces().get("default");
            var published = ai.loomspan.internal.skill.ExecutionConfigurationParser.parse("default",
                    new ai.loomspan.api.ExecutionConfiguration(yaml), new org.springframework.core.env.StandardEnvironment(), false);
            assertThat(startup).isEqualTo(published.executorConfig().namespaces().get("default"));
            assertThat(startup.get("copied")).isEqualTo(startup.get("template")).isInstanceOf(java.util.Map.class);
            assertThat((java.util.Map<String, Object>) startup.get("copied")).containsEntry("Nil", null);
            assertThat(startup).containsEntry("literal", "~");
        }
    }

    @org.springframework.context.annotation.Import(ai.loomspan.internal.autoconfigure.ExecutorConfigurationBinding.class)
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoomspanProperties.class)
    static class Binding {}
}
