package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.skill.*;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SharedDeclarativeAuthorizationIntegrationTest {
    static Authentication auth(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated("caller","unused",AuthorityUtils.createAuthorityList(authorities));
    }
    static ApplicationContextRunner context(Path directory, boolean jdk, ExecutorService worker) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                LoomspanJacksonAutoConfiguration.class,LoomspanAutoConfiguration.class,LoomspanAiAutoConfiguration.class))
                .withUserConfiguration(Beans.class,jdk ? JdkSecurity.class : ClassSecurity.class)
                .withInitializer(context -> context.addBeanFactoryPostProcessor(factory ->
                        factory.registerSingleton("LoomspanMissionExecutor",worker)))
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml");
    }
    static SkillDocument leaf(String name, String roles) {
        return new SkillDocument(name,"name: " + name + "\ndescription: secured leaf\nexecutor: default-write\nrbac_roles: [" + roles + "]\n");
    }
    static void publish(SkillReloader reloader, List<SkillDocument> documents) {
        reloader.publish(reloader.prepare("default",documents,new ExecutionConfiguration("""
                loomspan:
                  connections: {local: {driver: ollama, base-url: 'http://localhost:11434'}}
                  models: {model: {connection: local, provider-model: fixture}}
                """)));
    }
    static void restored(ExecutorService worker, SecurityContext sentinel, SecurityContext caller) throws Exception {
        assertThat(worker.submit(SecurityContextHolder::getContext).get(5,TimeUnit.SECONDS)).isSameAs(sentinel);
        assertThat(SecurityContextHolder.getContext()).isSameAs(caller);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualArgumentAdviceAndWorkerCallerScopesApplyToBothJavaForms(boolean jdk, @TempDir Path directory) throws Exception {
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var sentinel = SecurityContextHolder.createEmptyContext(); sentinel.setAuthentication(auth("ROLE_SENTINEL"));
            worker.submit(() -> SecurityContextHolder.setContext(sentinel)).get(5,TimeUnit.SECONDS);
            context(directory,jdk,worker).run(context -> {
                assertThat(context).hasNotFailed();
                var state = context.getBean(State.class);
                var template = context.getBean(SkillTemplate.class);
                var handoff = context.getBean(SkillInvocationHandoff.class);
                publish(context.getBean(SkillReloader.class),List.of(leaf("leaf","SALES")));
                var previous = SecurityContextHolder.getContext();
                var caller = SecurityContextHolder.createEmptyContext();
                var principal = auth("ROLE_SALES","ROLE_DATABASE_WRITE"); caller.setAuthentication(principal);
                SecurityContextHolder.setContext(caller);
                try {
                    for (String skill : List.of("default-callable","default-leaf")) {
                        template.validate(skill,Map.of("value","reject"));
                        var pending = handoff.handoff(skill,Map.of("value","ok"));
                        assertThat(state.calls).isZero();
                        var ambient = SecurityContextHolder.createEmptyContext(); ambient.setAuthentication(auth("ROLE_OTHER"));
                        SecurityContextHolder.setContext(ambient);
                        assertThat(pending.invoke()).isEqualTo("ok");
                        assertThat(state.authentication).isSameAs(principal);
                        assertThat(state.thread).isNotSameAs(Thread.currentThread());
                        restored(worker,sentinel,ambient);
                        SecurityContextHolder.setContext(caller);
                        assertThat(template.invoke(skill,Map.of("value","ok"))).isEqualTo("ok");
                        assertThat(state.authentication).isSameAs(principal);
                        restored(worker,sentinel,caller);
                        int before = state.calls;
                        var views = new ArrayList<SkillExecutionView>();
                        assertThatThrownBy(() -> template.invoke(skill,Map.of("value","reject"),views::add))
                                .isInstanceOf(AccessDeniedException.class);
                        assertThat(state.calls).isEqualTo(before);
                        assertThat(views).hasSize(1);
                        assertThat(views.getFirst().events()).anySatisfy(event -> {
                            assertThat(event.type()).isEqualTo("SKILL_FINISHED");
                            assertThat(event.details()).containsEntry("status","failed");
                        });
                        restored(worker,sentinel,caller);
                        assertThatThrownBy(() -> template.invoke(skill,Map.of("value","fail")))
                                .isInstanceOf(SkillException.class).hasStackTraceContaining("business failure");
                        restored(worker,sentinel,caller);
                        state.calls = 0;
                    }
                } finally { SecurityContextHolder.setContext(previous); }
            });
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void validationVisibilityAndDispatchAgreeAndRecheckTheAuthoritativeCaller(boolean jdk, @TempDir Path directory) throws Exception {
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var sentinel = SecurityContextHolder.createEmptyContext(); sentinel.setAuthentication(auth("ROLE_SENTINEL"));
            worker.submit(() -> SecurityContextHolder.setContext(sentinel)).get(5,TimeUnit.SECONDS);
            context(directory,jdk,worker).run(context -> {
                assertThat(context).hasNotFailed();
                var state = context.getBean(State.class);
                var reload = context.getBean(SkillReloader.class);
                publish(reload,List.of(leaf("leaf","SALES"),leaf("yamlDenied","OTHER"),new SkillDocument("root","""
                        name: root
                        description: parent
                        model: model
                        allowed_skills: [{name: default-leaf}, {name: default-yamlDenied}, {name: default-callable}]
                        """)));
                var template = context.getBean(SkillTemplate.class);
                var handoff = context.getBean(SkillInvocationHandoff.class);
                var manager = context.getBean(SkillGenerationManager.class);
                var resolver = context.getBean(SkillVisibilityResolver.class);
                var router = context.getBean(CapabilityExecutionRouter.class);
                var coordinator = context.getBean(ExecutionCoordinator.class);
                var previous = SecurityContextHolder.getContext();
                var caller = SecurityContextHolder.createEmptyContext();
                var both = auth("ROLE_SALES","ROLE_DATABASE_WRITE"); caller.setAuthentication(both);
                SecurityContextHolder.setContext(caller);
                try {
                    for (var denied : List.of(auth("ROLE_SALES"),auth("ROLE_DATABASE_WRITE"))) {
                        caller.setAuthentication(denied);
                        var observations = new ArrayList<SkillExecutionView>();
                        assertThatThrownBy(() -> template.validate("leaf",Map.of())).isInstanceOf(AccessDeniedException.class);
                        assertThatThrownBy(() -> handoff.handoff("leaf",Map.of())).isInstanceOf(AccessDeniedException.class);
                        assertThatThrownBy(() -> template.invoke("leaf",Map.of(),observations::add)).isInstanceOf(AccessDeniedException.class);
                        assertThat(observations).isEmpty();
                        template.validate("root",Map.of()); // child policy is checked when that child is considered/called
                    }
                    caller.setAuthentication(both);
                    template.validate("leaf",Map.of("value","reject"));
                    assertThat(reload.snapshot("default").skills()).extracting(SkillDescriptor::name)
                            .contains("default-leaf","default-yamlDenied","default-callable").doesNotContain("default-write");
                    var capture = manager.capture(SkillIdentity.parse("default-root"));
                    try {
                        var snapshot = manager.validateAdmission(capture.snapshot());
                        var root = snapshot.resolve(snapshot.entry());
                        var leaf = snapshot.resolve(SkillIdentity.parse("default-leaf"));
                        var yamlDenied = snapshot.resolve(SkillIdentity.parse("default-yamlDenied"));
                        var session = TestLoomspanSessions.withId("shared-authorization","default-root",8);
                        session.setAuthentication(both);
                        var binding = ExecutionBinding.sessionOnly(session,snapshot)
                                .withTarget(root,new MissionContext(session,root.name(),"source-frame",null));
                        ExecutionBindingScope.runWith(binding,() -> {
                            assertThat(resolver.visibleSkillsFor(root.name(),session,null)).extracting(CapabilityMetadata::name)
                                    .containsExactly("default-leaf","default-callable");
                            assertThat(resolver.visibleSkillsFor(root.name(),session,auth("ROLE_SALES"))).isEmpty();
                            assertThat(state.calls).isZero();
                            for (var denied : List.of(auth("ROLE_SALES"),auth("ROLE_DATABASE_WRITE"))) {
                                assertThatThrownBy(() -> router.execute(leaf,Map.of("value","ok"),session,denied)).isInstanceOf(AccessDeniedException.class);
                                assertThatThrownBy(() -> router.executeAssembled(leaf,Map.of("value","ok"),session,denied,List.of()))
                                        .isInstanceOf(AccessDeniedException.class);
                                assertThatThrownBy(() -> coordinator.execute(leaf,"run",Map.of("value","ok"),session,denied))
                                        .isInstanceOf(AccessDeniedException.class);
                            }
                            assertThatThrownBy(() -> router.execute(yamlDenied,Map.of(),session,both)).isInstanceOf(AccessDeniedException.class);
                            assertThat(state.calls).isZero();
                            assertThat(router.execute(leaf,Map.of("value","ok"),session,null)).isEqualTo("ok");
                            assertThat(state.authentication).isSameAs(both);
                            assertThat(session.getAuthentication().orElseThrow()).isSameAs(both);
                            var callable = snapshot.resolve(SkillIdentity.parse("default-callable"));
                            assertThat(router.execute(callable,Map.of("value","ok"),session,both)).isEqualTo("ok");
                            for (var target : List.of(leaf,callable)) {
                                int before = state.calls;
                                assertThatThrownBy(() -> router.execute(target,Map.of("value","reject"),session,both))
                                        .isInstanceOf(AccessDeniedException.class);
                                assertThat(state.calls).isEqualTo(before);
                                assertThatThrownBy(() -> router.execute(target,Map.of("value","fail"),session,both))
                                        .isInstanceOf(IllegalStateException.class).hasMessage("business failure");
                                var unrelated = auth("ROLE_OTHER");
                                session.setAuthentication(unrelated);
                                assertThat(router.execute(target,Map.of("value","ok"),session,both)).isEqualTo("ok");
                                assertThat(state.authentication).isSameAs(both);
                                assertThat(session.getAuthentication().orElseThrow()).isSameAs(unrelated);
                                session.setAuthentication(both);
                                try { restored(worker,sentinel,caller); }
                                catch (Exception ex) { throw new AssertionError(ex); }
                            }
                        });
                    } finally { capture.lease().close(); }
                } finally { SecurityContextHolder.setContext(previous); }
            });
        }
    }
    static class State { int calls; Authentication authentication; Thread thread; }
    interface Business {
        String callable(String value);
        String execute(SkillExecutorInvocation invocation);
    }
    static class Secured implements Business {
        final State state; Secured(State state) { this.state=state; }
        String run(String value) {
            state.calls++; state.authentication=SecurityContextHolder.getContext().getAuthentication(); state.thread=Thread.currentThread();
            if ("fail".equals(value)) throw new IllegalStateException("business failure");
            return value;
        }
        @SkillMethod(name="callable",description="secured callable") @RolesAllowed("DATABASE_WRITE")
        @PreAuthorize("#p0 != 'reject'") public String callable(String value) { return run(value); }
        @SkillExecutor(name="write") @RolesAllowed("DATABASE_WRITE")
        @PreAuthorize("#p0.input()['value'] != 'reject'") public String execute(SkillExecutorInvocation invocation) {
            return run((String)invocation.input().get("value"));
        }
    }
    @Configuration(proxyBeanMethods=false) static class Beans {
        @Bean State state() { return new State(); }
        @Bean Business business(State state) { return new Secured(state); }
    }
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(jsr250Enabled=true,proxyTargetClass=false) static class JdkSecurity {}
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(jsr250Enabled=true,proxyTargetClass=true) static class ClassSecurity {}
}
