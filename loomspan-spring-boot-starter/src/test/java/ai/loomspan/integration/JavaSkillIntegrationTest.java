package ai.loomspan.integration;

import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSkillIntegrationTest
{
    @TempDir Path directory;

    @Test
    void registersAndInvokesAnnotationOnlyJavaRootWithoutYaml() throws Exception
    {
        try (var server = new MockWebServer())
        {
            AtomicInteger calls = new AtomicInteger();
            runner(server).withBean(PlainSkill.class, () -> new PlainSkill(calls)).run(context -> {
                assertThat(context).hasNotFailed();
                var observed = new AtomicReference<SkillExecutionView>();
                var skills = context.getBean(SkillTemplate.class);
                assertThat(skills.invoke("plainEcho", Map.of("message", "hello"), observed::set))
                        .isEqualTo("{\"message\":\"hello\"}");
                assertThat(observed.get()).isNotNull();
                assertThat(observed.get().sessionId()).isNotBlank();
                assertThat(observed.get().events()).isNotEmpty();
                assertThat(calls).hasValue(1);
                assertThatThrownBy(() -> skills.invoke("plainEcho", Map.of()))
                        .isInstanceOf(SkillInputValidationException.class);
                assertThat(calls).hasValue(1);
            });
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void publicRootsEnforceRolesAndDenyAllAndRestoreCallerContext() throws Exception
    {
        try (var server = new MockWebServer())
        {
            AtomicInteger calls = new AtomicInteger();
            runner(server).withUserConfiguration(Enabled.class)
                    .withBean(SecuredSkills.class, () -> new SecuredSkills(calls)).run(context -> {
                        assertThat(context).hasNotFailed();
                        var skills = context.getBean(SkillTemplate.class);
                        SecurityContext before = SecurityContextHolder.getContext();
                        SecurityContext caller = new SecurityContextImpl(authentication("alice", "ROLE_READ"));
                        SecurityContextHolder.setContext(caller);
                        try
                        {
                            assertThat(skills.invoke("securedEcho", Map.of("message", "hello"))).isEqualTo("\"alice:hello\"");
                            assertThat(SecurityContextHolder.getContext()).isSameAs(caller);
                            assertThatThrownBy(() -> skills.invoke("alwaysDenied", Map.of())).isInstanceOf(AccessDeniedException.class);
                            assertThat(SecurityContextHolder.getContext()).isSameAs(caller);
                            SecurityContext empty = new SecurityContextImpl();
                            SecurityContextHolder.setContext(empty);
                            assertThatThrownBy(() -> skills.invoke("securedEcho", Map.of("message", "hidden")))
                                    .isInstanceOf(AccessDeniedException.class);
                            assertThat(skills.invoke("outsidePermit", Map.of())).isEqualTo("\"public\"");
                            assertThat(SecurityContextHolder.getContext()).isSameAs(empty);
                            assertThat(calls).hasValue(1);
                        }
                        finally { SecurityContextHolder.setContext(before); }
                    });
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void securedGenericJdkProxyRetainsCanonicalInputContractAndActualMethodAdvice() throws Exception
    {
        try (var server = new MockWebServer())
        {
            runner(server).withUserConfiguration(JdkEnabled.class).withBean(GenericStringSkill.class).run(context -> {
                assertThat(context).hasNotFailed();
                SecurityContext before = SecurityContextHolder.getContext();
                try
                {
                    SecurityContextHolder.setContext(new SecurityContextImpl(authentication("alice", "ROLE_READ")));
                    assertThat(context.getBean(SkillTemplate.class).invoke("genericEcho", Map.of("message", "hello")))
                            .isEqualTo("\"hello\"");
                    SecurityContextHolder.setContext(new SecurityContextImpl());
                    assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("genericEcho", Map.of("message", "denied")))
                            .isInstanceOf(AccessDeniedException.class);
                }
                finally { SecurityContextHolder.setContext(before); }
            });
        }
    }

    @Test
    void parallelPublicCallsCaptureConfiguredStrategyAndPreserveBothCallerIdentities() throws Exception
    {
        try (var server = new MockWebServer(); var callers = Executors.newFixedThreadPool(2))
        {
            runner(server).withUserConfiguration(ParallelSecurity.class).run(context -> {
                assertThat(context).hasNotFailed();
                var skills = context.getBean(SkillTemplate.class);
                var strategy = context.getBean(SecurityContextHolderStrategy.class);
                var probe = context.getBean(ParallelProbe.class);
                var alice = callers.submit(() -> invokeAsConfiguredCaller(skills, strategy, probe, "alice", "ROLE_READ"));
                var bob = callers.submit(() -> invokeAsConfiguredCaller(skills, strategy, probe, "bob", "ROLE_WRITE"));
                assertThat(alice.get(10, TimeUnit.SECONDS)).isEqualTo("\"alice:hello\"");
                assertThat(bob.get(10, TimeUnit.SECONDS)).isEqualTo("\"bob:hello\"");
                assertThat(probe.workerThreads).hasSize(2).doesNotContainAnyElementsOf(probe.callerThreads);
            });
            assertThat(server.getRequestCount()).isZero();
        }
    }

    private static String invokeAsConfiguredCaller(SkillTemplate skills, SecurityContextHolderStrategy strategy,
            ParallelProbe probe, String name, String role)
    {
        SecurityContext original = strategy.getContext();
        SecurityContext globalOriginal = SecurityContextHolder.getContext();
        SecurityContext caller = new SecurityContextImpl(authentication(name, role));
        strategy.setContext(caller);
        // A stale identity in Spring's global strategy must not override the configured strategy bean.
        SecurityContextHolder.setContext(new SecurityContextImpl(authentication("stale", "ROLE_OTHER")));
        probe.callerThreads.add(Thread.currentThread().threadId());
        try
        {
            return skills.invoke("parallelEcho", Map.of("message", "hello"));
        }
        finally
        {
            assertThat(strategy.getContext()).isSameAs(caller);
            assertThat(caller.getAuthentication().getName()).isEqualTo(name);
            strategy.setContext(original);
            SecurityContextHolder.setContext(globalOriginal);
        }
    }

    @Test
    void realRegistrationRejectsSupportedPoliciesWithoutJsr250() throws Exception
    {
        try (var server = new MockWebServer())
        {
            for (Class<?> beanType : List.of(RolesOnly.class, PermitOnly.class, DenyOnly.class))
            {
                for (boolean disabled : List.of(false, true))
                {
                    var runner = runner(server).withBean(beanType);
                    if (disabled) runner = runner.withUserConfiguration(Disabled.class);
                    runner.run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("jsr250Enabled = true")
                                .hasStackTraceContaining("exposed");
                    });
                }
            }
        }
    }

    @Test
    void rejectsUninterceptableClassProxySkillsButPreservesPlainBeanReflection() throws Exception
    {
        try (var server = new MockWebServer())
        {
            for (Object target : List.of(new FinalStateSkill(), new PrivateStateSkill(), new StaticStateSkill()))
            {
                var factory = new org.springframework.aop.framework.ProxyFactory(target);
                factory.setProxyTargetClass(true);
                factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
                runner(server).withBean("unsafeState", Object.class, factory::getProxy).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("unsafeState#")
                            .hasStackTraceContaining("exposed").hasStackTraceContaining("cannot safely invoke")
                            .hasStackTraceContaining("CGLIB proxy");
                });
                runner(server).withBean("plainState", Object.class, () -> target).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SkillTemplate.class).invoke("exposed", Map.of())).isEqualTo("\"initialized\"");
                });
            }
        }
    }

    @Test
    void yamlParentPublishesOnlyAuthorizedLocalChildrenAndPropagatesCallerToJava() throws Exception
    {
        Files.writeString(directory.resolve("parent.yml"), """
                name: securedParent
                description: Invoke an authorized local child.
                model: integration
                planning_mode: false
                allowed_skills:
                  - name: securedEcho
                  - name: alwaysDenied
                  - name: yamlSibling
                """);
        Files.writeString(directory.resolve("sibling.yml"), """
                name: yamlSibling
                description: A YAML child with the equivalent role policy.
                model: integration
                planning_mode: false
                rbac_roles: [READ]
                """);
        try (var server = new MockWebServer())
        {
            server.enqueue(jsonResponse("""
                    {"id":"child-call","object":"chat.completion","created":1,"model":"integration-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[
                       {"id":"call1","type":"function","function":{"name":"securedEcho","arguments":"{\\"message\\":\\"child hello\\"}"}}
                     ]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """));
            server.enqueue(finalResponse());
            server.enqueue(finalResponse());
            AtomicInteger calls = new AtomicInteger();
            runner(server).withUserConfiguration(Enabled.class)
                    .withBean(SecuredSkills.class, () -> new SecuredSkills(calls)).run(context -> {
                        assertThat(context).hasNotFailed();
                        SecurityContext before = SecurityContextHolder.getContext();
                        try
                        {
                            SecurityContextHolder.setContext(new SecurityContextImpl(authentication("alice", "ROLE_READ")));
                            assertThat(context.getBean(SkillTemplate.class).invoke("securedParent", Map.of("objective", "run child")))
                                    .isEqualTo("done");
                            assertThat(calls).hasValue(1);
                            SecurityContextHolder.setContext(new SecurityContextImpl());
                            assertThat(context.getBean(SkillTemplate.class).invoke("securedParent", Map.of("objective", "no restricted children")))
                                    .isEqualTo("done");
                            assertThat(calls).hasValue(1);
                        }
                        finally { SecurityContextHolder.setContext(before); }
                    });
            var first = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            assertThat(first.getBody().readUtf8()).contains("securedEcho", "yamlSibling")
                    .doesNotContain("alwaysDenied", "outsidePermit");
            var result = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(result).isNotNull();
            assertThat(result.getBody().readUtf8()).contains("alice:child hello");
            var anonymous = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(anonymous).isNotNull();
            assertThat(anonymous.getBody().readUtf8()).doesNotContain("securedEcho", "yamlSibling", "alwaysDenied", "outsidePermit");
        }
    }

    private ApplicationContextRunner runner(MockWebServer server)
    {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yml",
                        "loomspan.connections.integration.driver=openai",
                        "loomspan.connections.integration.base-url=" + server.url("/v1"),
                        "loomspan.connections.integration.api-key=integration-key",
                        "loomspan.models.integration.connection=integration",
                        "loomspan.models.integration.provider-model=integration-model",
                        "loomspan.session.mission-timeout=15s");
    }

    private static MockResponse jsonResponse(String body)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse finalResponse()
    {
        return jsonResponse("""
                {"id":"done","object":"chat.completion","created":1,"model":"integration-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """);
    }

    private static Authentication authentication(String name, String... authorities)
    {
        return UsernamePasswordAuthenticationToken.authenticated(name, "unused", AuthorityUtils.createAuthorityList(authorities));
    }

    public static class PlainSkill
    {
        private final AtomicInteger calls;
        PlainSkill(AtomicInteger calls) { this.calls = calls; }
        @SkillMethod(description = "Echo reflected input")
        public Map<String, Object> plainEcho(@SkillParam(description = "Message") String message)
        { calls.incrementAndGet(); return Map.of("message", message); }
    }

    public static class SecuredSkills
    {
        private final AtomicInteger calls;
        SecuredSkills(AtomicInteger calls) { this.calls = calls; }
        @SkillMethod(description = "Echo as the trusted caller") @RolesAllowed("READ")
        public String securedEcho(String message)
        { calls.incrementAndGet(); return SecurityContextHolder.getContext().getAuthentication().getName() + ":" + message; }
        @SkillMethod(description = "Never available") @DenyAll
        public String alwaysDenied() { calls.incrementAndGet(); return "bad"; }
        @SkillMethod(description = "Public but outside the parent allowlist") @PermitAll
        public String outsidePermit() { return "public"; }
    }
    public static class RolesOnly { @SkillMethod(description = "Roles") @RolesAllowed("READ") public String exposed() { return "ok"; } }
    public static class PermitOnly { @SkillMethod(description = "Permit") @PermitAll public String exposed() { return "ok"; } }
    public static class DenyOnly { @SkillMethod(description = "Deny") @DenyAll public String exposed() { return "ok"; } }
    public static class FinalStateSkill
    {
        private final String state = new String("initialized");
        @SkillMethod(description = "Final state read") public final String exposed() { return state; }
    }
    public static class PrivateStateSkill
    {
        private final String state = new String("initialized");
        @SkillMethod(description = "Private state read") private String exposed() { return state; }
    }
    public static class StaticStateSkill
    {
        @SkillMethod(description = "Static state read") public static String exposed() { return "initialized"; }
    }
    interface GenericSkill<T>
    {
        @SkillMethod(description = "Secured generic interface skill") @RolesAllowed("READ")
        T genericEcho(T message);
    }
    public static class GenericStringSkill implements GenericSkill<String>
    {
        @Override public String genericEcho(String message) { return message; }
    }
    static class ParallelProbe
    {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final java.util.Set<Long> callerThreads = ConcurrentHashMap.newKeySet();
        final java.util.Set<Long> workerThreads = ConcurrentHashMap.newKeySet();
    }
    public static class ParallelSecuredSkill
    {
        private final SecurityContextHolderStrategy strategy;
        private final ParallelProbe probe;
        ParallelSecuredSkill(SecurityContextHolderStrategy strategy, ParallelProbe probe)
        { this.strategy = strategy; this.probe = probe; }
        @SkillMethod(description = "Report trusted caller on concurrent execution") @RolesAllowed({"READ", "WRITE"})
        public String parallelEcho(String message) throws Exception
        {
            Authentication caller = strategy.getContext().getAuthentication();
            probe.workerThreads.add(Thread.currentThread().threadId());
            probe.barrier.await(5, TimeUnit.SECONDS);
            assertThat(strategy.getContext().getAuthentication()).isSameAs(caller);
            return caller.getName() + ":" + message;
        }
    }
    static class ConfiguredStrategy implements SecurityContextHolderStrategy
    {
        private final ThreadLocal<SecurityContext> contexts = ThreadLocal.withInitial(SecurityContextImpl::new);
        public void clearContext() { contexts.remove(); }
        public SecurityContext getContext() { return contexts.get(); }
        public void setContext(SecurityContext context) { contexts.set(context); }
        public SecurityContext createEmptyContext() { return new SecurityContextImpl(); }
    }
    @Configuration(proxyBeanMethods = false) @EnableMethodSecurity(jsr250Enabled = true, proxyTargetClass = true)
    static class ParallelSecurity
    {
        @Bean static SecurityContextHolderStrategy strategy() { return new ConfiguredStrategy(); }
        @Bean ParallelProbe probe() { return new ParallelProbe(); }
        @Bean ParallelSecuredSkill parallelSkill(SecurityContextHolderStrategy strategy, ParallelProbe probe)
        { return new ParallelSecuredSkill(strategy, probe); }
    }
    @Configuration(proxyBeanMethods = false) @EnableMethodSecurity(jsr250Enabled = true, proxyTargetClass = true) static class Enabled { }
    @Configuration(proxyBeanMethods = false) @EnableMethodSecurity(jsr250Enabled = true) static class JdkEnabled { }
    @Configuration(proxyBeanMethods = false) @EnableMethodSecurity(jsr250Enabled = false) static class Disabled { }
}
