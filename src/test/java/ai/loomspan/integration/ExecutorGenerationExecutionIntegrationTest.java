package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ExecutorGenerationExecutionIntegrationTest {
    @Test void startupAndPublishedConfigurationDeliverTheSameSelectedData(@TempDir Path directory) throws Exception {
        String data = "{Upper: A, upper: B, 'literal.dot': [null, {}, [], 7, 2.5, true, '7'], placeholder: '${UNSET_EXECUTOR_VALUE}', api-key-ref: ordinary}";
        Path settings = directory.resolve("application.yml");
        Files.writeString(settings, "loomspan: {executor-config: {infra: {settings: {}}, default: {settings: " + data + "}}}\n");
        var document = leaf("startup", "configured");
        Files.writeString(directory.resolve("startup.yaml"), document.yaml());
        context(directory).withUserConfiguration(DefaultBeans.class).withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + settings.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            var bean = context.getBean(DefaultExecutor.class);
            var template = context.getBean(SkillTemplate.class);
            assertThat(template.invoke("startup", Map.of())).isEqualTo("selected");
            var startup = bean.invocation;
            var reloader = context.getBean(SkillReloader.class);
            var published = publish(reloader, "default", List.of(document),
                    new ExecutionConfiguration("loomspan: {executor-config: {default: {settings: " + data + "}}}"), NamespaceVisibility.PRIVATE);
            assertThat(template.invoke("startup", Map.of())).isEqualTo("selected");
            assertThat(bean.invocation.executorConfig()).isEqualTo(startup.executorConfig())
                    .containsEntry("placeholder", "${UNSET_EXECUTOR_VALUE}").containsEntry("api-key-ref", "ordinary");
            assertThat(bean.invocation.executorConfigGenerationId()).isEqualTo(published.generationId())
                    .isNotEqualTo(startup.executorConfigGenerationId());
        });
    }
    @Test void retainedWorkUsesCapturedConfigAndIdentityAcrossReplacementRemovalAndRecreation(@TempDir Path directory) throws Exception {
        Path settings = directory.resolve("application.yml");
        Files.writeString(settings, "loomspan: {executor-config: {infra: {settings: {version: startup, 'literal.dot': [null, {}, []]}}}}\n");
        var destroyed = new AtomicInteger();
        context(directory).withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + settings.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            var bean = context.getBean(Executors.class); bean.destroyed = destroyed;
            var reloader = context.getBean(SkillReloader.class);
            var template = context.getBean(SkillTemplate.class);
            var handoff = context.getBean(SkillInvocationHandoff.class);
            assertThat(reloader.snapshot("infra").skills()).isEmpty();
            var startupId = reloader.snapshot("infra").generationId();
            var infra1 = publish(reloader, "infra", List.of(leaf("same", "configured")), config("startup"), NamespaceVisibility.PUBLIC);
            assertThat(reloader.snapshot("infra").skill("infra-same").orElseThrow().kind()).isEqualTo(SkillKind.JAVA);
            publish(reloader, "caller", List.of(new SkillDocument("leaf", leaf("leaf", "infra-configured").yaml()
                            + "input_schema: {type: object, properties: {attachment: {type: attachment, media_type: file, allowed_content_types: [application/octet-stream]}}, additionalProperties: true}\n"), leaf("empty", "infra-plain")),
                    new ExecutionConfiguration("loomspan: {}"), NamespaceVisibility.PRIVATE);
            assertThat(bean.invocations).isEmpty();
            var principal = UsernamePasswordAuthenticationToken.authenticated("reader", "unused", List.of());
            SecurityContextHolder.getContext().setAuthentication(principal);
            var retired = new CopyOnWriteArrayList<String>();
            reloader.onGenerationRetired(retired::add);
            try {
                var resource = new org.springframework.core.io.ByteArrayResource(new byte[] {1, 2, 3}) {
                    @Override public String getFilename() { return "attachment.bin"; }
                };
                var old = handoff.handoff("caller-leaf", Map.of("value", "old", "attachment", resource));
                var pendingAtRemoval = handoff.handoff("caller-leaf", Map.of("value", "removed"));
                var infra2 = publish(reloader, "infra", List.of(), config("second"), NamespaceVisibility.PUBLIC);
                assertThat(retired).doesNotContain(infra1.generationId());
                assertThat(template.invoke("caller-leaf", Map.of("value", "fresh"))).isEqualTo("second");
                assertThat(bean.invocations.getLast().executorConfigGenerationId()).isEqualTo(infra2.generationId());
                reloader.remove("infra", infra2.generationId());
                assertThatThrownBy(() -> handoff.handoff("caller-leaf", Map.of())).isInstanceOf(SkillException.class);
                assertThat(pendingAtRemoval.invoke()).isEqualTo("startup");
                var infra3 = publish(reloader, "infra", List.of(), config("third"), NamespaceVisibility.PUBLIC);
                assertThat(template.invoke("caller-leaf", Map.of("value", "new"))).isEqualTo("third");
                assertThat(old.invoke()).isEqualTo("startup");
                var invocation = bean.invocations.getLast();
                assertThat(invocation.input()).containsEntry("value", "old");
                assertThat(invocation.input().get("attachment")).isSameAs(resource);
                assertThat(invocation.skillConfig()).containsEntry("operation", "echo");
                assertThat(invocation.executorConfig()).containsEntry("version", "startup").containsKey("literal.dot");
                assertThat(invocation.skillName()).isEqualTo("caller-leaf");
                assertThat(invocation.executorConfigGenerationId()).isEqualTo(infra1.generationId()).isNotEqualTo(startupId);
                assertThat(bean.authentications).allSatisfy(authentication -> assertThat(authentication).isSameAs(principal));
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(principal);
                assertThat(retired).containsOnlyOnce(infra1.generationId());
                assertThat(destroyed).hasValue(0);
                assertThat(template.invoke("caller-empty", Map.of())).isEqualTo("plain");
                assertThat(bean.invocations.getLast().executorConfig()).isEmpty();
                assertThat(bean.invocations.getLast().executorConfigGenerationId()).isEqualTo(infra3.generationId());
                var views = new ArrayList<SkillExecutionView>();
                assertThat(template.invoke("caller-leaf", Map.of(), views::add)).isEqualTo("third");
                assertThat(views).hasSize(1);
                assertThat(views.getFirst().events()).isNotEmpty();
            } finally { SecurityContextHolder.clearContext(); }
        });
        assertThat(destroyed).hasValue(1);
    }

    @Test void preparationDoesNotPinForeignRuntimeAndFreshAdmissionUsesCurrentVisibility(@TempDir Path directory) throws Exception {
        Path settings = directory.resolve("application.yml");
        Files.writeString(settings, "loomspan: {executor-config: {infra: {settings: {}}}}\n");
        context(directory).withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + settings.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            var reloader = context.getBean(SkillReloader.class);
            var infra = publish(reloader, "infra", List.of(), config("one"), NamespaceVisibility.PUBLIC);
            var candidate = reloader.prepare("caller", List.of(leaf("leaf", "infra-configured")), new ExecutionConfiguration("loomspan: {}"));
            var retired = new ArrayList<String>(); reloader.onGenerationRetired(retired::add);
            var replacement = publish(reloader, "infra", List.of(), config("two"), NamespaceVisibility.PRIVATE);
            assertThat(retired).contains(infra.generationId());
            reloader.publish(candidate);
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).validate("caller-leaf", Map.of())).isInstanceOf(SkillException.class);
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("caller-leaf", Map.of())).isInstanceOf(SkillException.class);
            assertThat(context.getBean(Executors.class).invocations).isEmpty();
            assertThat(replacement.generationId()).isNotEqualTo(infra.generationId());
            reloader.remove("infra", replacement.generationId());
            assertThat(retired).contains(replacement.generationId());
        });
    }

    @Test void executorUsesSharedCancellationAndFencesLateCompletion(@TempDir Path directory) throws Exception {
        Path settings = directory.resolve("application.yml");
        Files.writeString(settings, "loomspan: {executor-config: {infra: {settings: {}}}}\n");
        context(directory).withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=" + settings.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            var bean = context.getBean(Executors.class);
            var reloader = context.getBean(SkillReloader.class);
            publish(reloader, "infra", List.of(), config("one"), NamespaceVisibility.PUBLIC);
            publish(reloader, "caller", List.of(leaf("blocked", "infra-plain")), new ExecutionConfiguration("loomspan: {}"), NamespaceVisibility.PRIVATE);
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var view = new java.util.concurrent.atomic.AtomicReference<SkillExecutionView>();
            Thread caller = Thread.ofPlatform().unstarted(() -> {
                try { context.getBean(SkillTemplate.class).invoke("caller-blocked", Map.of("block", true), view::set); }
                catch (Throwable ex) { failure.set(ex); }
            });
            try {
                caller.start();
                assertThat(bean.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                caller.interrupt(); caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
                assertThat(failure.get()).isInstanceOf(SkillException.class).hasStackTraceContaining("LoomspanMissionTimeoutException");
                assertThat(view.get()).isNotNull();
                assertThat(view.get().events()).noneSatisfy(event -> assertThat(event.details().toString()).contains("late-result"));
            } catch (InterruptedException ex) { throw new AssertionError(ex); }
            finally {
                bean.release.countDown();
                try { assertThat(bean.finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); caller.join(5000); }
                catch (InterruptedException ex) { throw new AssertionError(ex); }
            }
        });
    }
    private static ApplicationContextRunner context(Path directory) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                LoomspanJacksonAutoConfiguration.class, LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(Beans.class).withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml");
    }
    private static SkillDocument leaf(String name, String executor) {
        return new SkillDocument(name, "name: " + name + "\ndescription: leaf\nexecutor: " + executor + "\nconfig: {operation: echo}\n");
    }
    private static ExecutionConfiguration config(String version) {
        return new ExecutionConfiguration("loomspan: {executor-config: {infra: {settings: {version: " + version + ", 'literal.dot': [null, {}, []]}}}}\n");
    }
    private static PreparedSkillUpdate publish(SkillReloader reloader, String namespace, List<SkillDocument> docs,
            ExecutionConfiguration config, NamespaceVisibility visibility) {
        var candidate = reloader.prepare(namespace, docs, config, visibility); reloader.publish(candidate); return candidate;
    }
    @Configuration(proxyBeanMethods = false) static class Beans { @Bean Executors executors() { return new Executors(); } }
    @Configuration(proxyBeanMethods = false) static class DefaultBeans { @Bean DefaultExecutor defaultExecutor() { return new DefaultExecutor(); } }
    static class DefaultExecutor {
        SkillExecutorInvocation invocation;
        @SkillExecutor(name = "configured", config = "settings") public String execute(SkillExecutorInvocation value) {
            invocation = value; return "selected";
        }
    }
    static class Executors {
        AtomicInteger destroyed = new AtomicInteger();
        final List<SkillExecutorInvocation> invocations = new CopyOnWriteArrayList<>();
        final List<org.springframework.security.core.Authentication> authentications = new CopyOnWriteArrayList<>();
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        @SkillExecutor(namespace = "infra", name = "configured", config = "settings")
        public String configured(SkillExecutorInvocation invocation) {
            invocations.add(invocation); authentications.add(SecurityContextHolder.getContext().getAuthentication());
            return String.valueOf(invocation.executorConfig().get("version"));
        }
        @SkillExecutor(namespace = "infra", name = "plain")
        public String plain(SkillExecutorInvocation invocation) {
            invocations.add(invocation);
            if (Boolean.TRUE.equals(invocation.input().get("block"))) {
                entered.countDown();
                try {
                    boolean released = false;
                    while (!released) {
                        try { released = release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (InterruptedException ignored) { }
                    }
                    return "late-result";
                } finally { finished.countDown(); }
            }
            return "plain";
        }
        @PreDestroy public void destroy() { destroyed.incrementAndGet(); }
    }
}
