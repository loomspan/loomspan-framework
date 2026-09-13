package ai.loomspan.internal.skill;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.RestSkillHandler;
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
    private ApplicationContextRunner withYamlPair(String directoryName, String firstBody, String secondBody) throws Exception {
        var manifests = java.nio.file.Files.createDirectories(directory.resolve(directoryName));
        java.nio.file.Files.writeString(manifests.resolve("first.yaml"),
                "name: sharedName\ndescription: First declaration\n" + firstBody);
        java.nio.file.Files.writeString(manifests.resolve("second.yaml"),
                "name: sharedName\ndescription: Second declaration\n" + secondBody);
        return runner().withPropertyValues("loomspan.skills.locations=" + manifests.toUri() + "*.yaml",
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
    @Test void rejectsEveryRestCollisionWithBothDeclarationLocations() throws Exception {
        withYaml("explicitEcho", "rest: true\n")
                .withUserConfiguration(LazySkills.class, RestHandlerConfiguration.class).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("explicitEcho.yaml")
                            .hasStackTraceContaining("first#");
                });
        withYamlPair("rest-model", "rest: true\n", "model: test\n")
                .withUserConfiguration(RestHandlerConfiguration.class).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("first.yaml")
                            .hasStackTraceContaining("second.yaml");
                });
        withYamlPair("rest-rest", "rest: true\n", "rest: true\n")
                .withUserConfiguration(RestHandlerConfiguration.class).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("first.yaml")
                            .hasStackTraceContaining("second.yaml");
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
    @Test void registersAndInvokesRestSkillWithoutModel() throws Exception {
        withYaml("restLookup", "rest: true\ninput_schema:\n  type: object\n  properties:\n    value: {type: string}\n  required: [value]\n")
                .withUserConfiguration(RestHandlerConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed();
                    var metadata = context.getBean(CapabilityRegistry.class).getCapability("restLookup");
                    assertThat(metadata.kind()).isEqualTo(CapabilityKind.REST_SKILL);
                    assertThat(metadata.skillExecution().configured()).isFalse();
                    assertThat(context.getBean(SkillTemplate.class).invoke("restLookup", java.util.Map.of("value", "ok")))
                            .isEqualTo("restLookup:ok");
                });
    }
    @Test void requiresExactlyOneHandlerOnlyWhenRestManifestsExist() throws Exception {
        withYaml("restLookup", "rest: true\n").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("exactly one RestSkillHandler")
                    .hasStackTraceContaining("restLookup.yaml");
        });
        withYaml("restLookup", "rest: true\n").withUserConfiguration(MultipleRestHandlers.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("firstRestHandler")
                    .hasStackTraceContaining("secondRestHandler");
        });
        runner().withUserConfiguration(MultipleRestHandlers.class).run(context -> assertThat(context).hasNotFailed());
    }
    @Test void restHandlerEmptyAndNullResultsUseTicketedBoundary() throws Exception {
        withYaml("restLookup", "rest: true\n").withUserConfiguration(EmptyRestHandler.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("restLookup", java.util.Map.of())).isEmpty();
        });
        withYaml("restLookup", "rest: true\n").withUserConfiguration(NullRestHandler.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("restLookup", java.util.Map.of()))
                    .isInstanceOf(ai.loomspan.api.SkillException.class)
                    .hasRootCauseMessage("REST skill 'restLookup' handler returned null");
        });
    }
    @Test void restHandlerFailuresCrossTheFacadeWithoutJavaTextAdaptation() throws Exception {
        withYaml("restLookup", "rest: true\n").withUserConfiguration(SkillExceptionRestHandler.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("restLookup", java.util.Map.of()))
                    .isSameAs(SkillExceptionRestHandler.FAILURE);
        });
        withYaml("restLookup", "rest: true\n").withUserConfiguration(RuntimeFailureRestHandler.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("restLookup", java.util.Map.of()))
                    .isInstanceOf(ai.loomspan.api.SkillException.class)
                    .hasMessage("Skill 'restLookup' execution failed.")
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("handler detail");
        });
    }
    @Configuration(proxyBeanMethods=false) static class RestHandlerConfiguration {
        @Bean RestSkillHandler restSkillHandler() {
            return invocation -> invocation.skillName() + ":" + invocation.input().get("value");
        }
    }
    @Configuration(proxyBeanMethods=false) static class MultipleRestHandlers {
        @Bean RestSkillHandler firstRestHandler() { return invocation -> "one"; }
        @Bean RestSkillHandler secondRestHandler() { return invocation -> "two"; }
    }
    @Configuration(proxyBeanMethods=false) static class EmptyRestHandler {
        @Bean RestSkillHandler restSkillHandler() { return invocation -> ""; }
    }
    @Configuration(proxyBeanMethods=false) static class NullRestHandler {
        @Bean RestSkillHandler restSkillHandler() { return invocation -> null; }
    }
    @Configuration(proxyBeanMethods=false) static class SkillExceptionRestHandler {
        static final ai.loomspan.api.SkillException FAILURE = new ai.loomspan.api.SkillException("application-safe");
        @Bean RestSkillHandler restSkillHandler() { return invocation -> { throw FAILURE; }; }
    }
    @Configuration(proxyBeanMethods=false) static class RuntimeFailureRestHandler {
        @Bean RestSkillHandler restSkillHandler() { return invocation -> { throw new IllegalStateException("handler detail"); }; }
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
