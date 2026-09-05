package ai.loomspan.internal.skill;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;

class YamlSkillCapabilityRegistrarTests {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                LoomspanJacksonAutoConfiguration.class, LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withPropertyValues("loomspan.skills.locations=classpath:/no-java-wrappers/*.yaml");
    }
    @Test void discoversLazyAnnotationOnlySkillAndInvokesRootWithoutModel() {
        runner().withUserConfiguration(LazySkills.class).run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(CapabilityRegistry.class);
            assertThat(registry.getAllCapabilities()).extracting(CapabilityMetadata::name).containsExactly("explicitEcho");
            assertThat(context.getBean(SkillTemplate.class).invoke("explicitEcho", java.util.Map.of("text", "hello")))
                    .isEqualTo("\"hello\"");
        });
    }
    @Test void rejectsDuplicateJavaNamesWithBothDeclarations() {
        runner().withUserConfiguration(DuplicateSkills.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("explicitEcho")
                    .hasStackTraceContaining("first").hasStackTraceContaining("second");
        });
    }
    @Test void validatesExactNamesWithoutTrimming() {
        runner().withUserConfiguration(InvalidSkills.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Invalid skill name");
        });
    }
    @Test void aliasesDoNotPublishDuplicateDeclarations() {
        runner().withUserConfiguration(AliasedSkills.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CapabilityRegistry.class).getAllCapabilities())
                    .extracting(CapabilityMetadata::name).containsExactly("explicitEcho");
            assertThat(context.getBean("primary")).isSameAs(context.getBean("alias"));
        });
    }
    @Test void discoversTypeExposedLazyFactoryProduct() {
        runner().withUserConfiguration(FactorySkills.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("explicitEcho", java.util.Map.of("text", "factory")))
                    .isEqualTo("\"factory\"");
        });
    }
    @Configuration(proxyBeanMethods=false) static class AliasedSkills {
        @Bean(name={"primary", "alias"}) @Lazy Echo echo() { return new Echo(); }
    }
    @Configuration(proxyBeanMethods=false) static class FactorySkills {
        @Bean @Lazy org.springframework.beans.factory.FactoryBean<Echo> echoFactory() {
            return new org.springframework.beans.factory.FactoryBean<>() {
                public Echo getObject() { return new Echo(); }
                public Class<?> getObjectType() { return Echo.class; }
                public boolean isSingleton() { return true; }
            };
        }
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private ApplicationContextRunner withYaml(String name, String body) throws Exception {
        var file = directory.resolve(name + ".yaml");
        java.nio.file.Files.writeString(file, "name: " + name + "\ndescription: Test skill\n" + body);
        return runner().withPropertyValues("loomspan.skills.locations=" + file.toUri(),
                "loomspan.connections.test.driver=openai", "loomspan.connections.test.api-key=test",
                "loomspan.models.test.connection=test", "loomspan.models.test.provider-model=test");
    }
    @Test void resolvesJavaChildrenOnlyAfterLazyDiscovery() throws Exception {
        withYaml("parent", "model: test\nallowed_skills: [{name: explicitEcho}]\n")
                .withUserConfiguration(LazySkills.class).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CapabilityRegistry.class).getAllCapabilities())
                            .extracting(CapabilityMetadata::name).containsExactlyInAnyOrder("parent", "explicitEcho");
                });
    }
    @Test void rejectsCrossSourceCollisionWithBothLocations() throws Exception {
        withYaml("explicitEcho", "model: test\n").withUserConfiguration(LazySkills.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("explicitEcho.yaml")
                    .hasStackTraceContaining("first#");
        });
    }
    @Test void rejectsUnresolvedExactChildAtStartup() throws Exception {
        withYaml("parent", "model: test\nallowed_skills: [{name: ExplicitEcho}]\n")
                .withUserConfiguration(LazySkills.class).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("ExplicitEcho")
                            .hasStackTraceContaining("parent.yaml");
                });
    }
    @Test void rejectsAllLegacyMappingFormsBeforeModelValidation() throws Exception {
        for (String mapping : java.util.List.of("null", "{}", "scalar", "{target_id: 'bean#method'}")) {
            withYaml("legacy", "mapping: " + mapping + "\n").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("mapping is no longer supported")
                        .hasStackTraceContaining("@SkillMethod");
            });
        }
    }
    @Configuration(proxyBeanMethods=false) static class LazySkills {
        @Bean @Lazy Echo first() { return new Echo(); }
    }
    @Configuration(proxyBeanMethods=false) static class DuplicateSkills {
        @Bean Echo first() { return new Echo(); }
        @Bean Echo second() { return new Echo(); }
    }
    @Configuration(proxyBeanMethods=false) static class InvalidSkills {
        @Bean Invalid invalid() { return new Invalid(); }
    }
    static class Echo {
        @SkillMethod(name="explicitEcho", description="Echo text")
        public String echo(String text) { return text; }
    }
    static class Invalid {
        @SkillMethod(name=" padded ", description="Invalid") public String invalid() { return "bad"; }
    }
}
