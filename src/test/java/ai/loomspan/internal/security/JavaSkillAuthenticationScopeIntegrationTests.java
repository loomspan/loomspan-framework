package ai.loomspan.internal.security;

import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static ai.loomspan.internal.security.JavaSkillAuthorizationIntegrationTests.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSkillAuthenticationScopeIntegrationTests
{
    @Test
    void restoresExactContextsAcrossNestedSuccessAndDenialOnReusedWorker() throws Exception
    {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfiguration.class);
                var executor = Executors.newSingleThreadExecutor())
        {
            var bean = context.getBean(IdentityService.class);
            var strategy = context.getBean(SecurityContextHolderStrategy.class);
            var scope = new ScopedAuthentication(strategy);
            executor.submit(() -> {
                SecurityContext sentinel = new SecurityContextImpl(auth("ROLE_READ"));
                strategy.setContext(sentinel);
                Authentication outerAuth = auth("ROLE_READ");
                try (var outer = scope.open(outerAuth))
                {
                    SecurityContext outerContext = strategy.getContext();
                    assertThat(outerContext).isNotSameAs(sentinel);
                    assertThat(bean.identity()).isSameAs(outerAuth);
                    try (var inner = scope.open(auth("OTHER")))
                    {
                        assertThatThrownBy(bean::identity).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                    }
                    assertThat(strategy.getContext()).isSameAs(outerContext);
                    assertThat(bean.identity()).isSameAs(outerAuth);
                    try (var inner = scope.open(null))
                    {
                        assertThatThrownBy(bean::identity).isInstanceOf(org.springframework.security.core.AuthenticationException.class);
                    }
                    assertThat(strategy.getContext()).isSameAs(outerContext);
                }
                assertThat(strategy.getContext()).isSameAs(sentinel);
                assertThat(sentinel.getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_READ");
                return null;
            }).get(10, TimeUnit.SECONDS);
            executor.submit(() -> {
                SecurityContext before = strategy.getContext();
                try (var ignored = scope.open(auth("ROLE_READ")))
                {
                    assertThatThrownBy(bean::fail).isInstanceOf(IllegalStateException.class);
                }
                assertThat(strategy.getContext()).isSameAs(before);
                strategy.clearContext();
            }).get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void simultaneousWorkersUseCapturedCallerWithoutSharingMutableContexts() throws Exception
    {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfiguration.class);
                var executor = Executors.newFixedThreadPool(2))
        {
            var bean = context.getBean(IdentityService.class);
            var strategy = context.getBean(SecurityContextHolderStrategy.class);
            var scope = new ScopedAuthentication(strategy);
            var barrier = new CyclicBarrier(2);
            Authentication first = auth("ROLE_READ");
            Authentication second = auth("ROLE_WRITE");
            var one = executor.submit(() -> checkConcurrent(bean, strategy, scope, barrier, first));
            var two = executor.submit(() -> checkConcurrent(bean, strategy, scope, barrier, second));
            assertThat(one.get(10, TimeUnit.SECONDS)).isSameAs(first);
            assertThat(two.get(10, TimeUnit.SECONDS)).isSameAs(second);
        }
    }

    private static Authentication checkConcurrent(IdentityService bean, SecurityContextHolderStrategy strategy,
            ScopedAuthentication scope, CyclicBarrier barrier, Authentication caller) throws Exception
    {
        SecurityContext sentinel = strategy.getContext();
        try (var ignored = scope.open(caller))
        {
            barrier.await(10, TimeUnit.SECONDS);
            assertThat(bean.identity()).isSameAs(caller);
            barrier.await(10, TimeUnit.SECONDS);
            return bean.identity();
        }
        finally
        {
            assertThat(strategy.getContext()).isSameAs(sentinel);
            strategy.clearContext();
        }
    }

    public static class IdentityService
    {
        private final SecurityContextHolderStrategy strategy;
        IdentityService(SecurityContextHolderStrategy strategy) { this.strategy = strategy; }
        @RolesAllowed({"READ", "WRITE"}) public Authentication identity() { return strategy.getContext().getAuthentication(); }
        @RolesAllowed("READ") public String fail() { throw new IllegalStateException("business failure"); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true, proxyTargetClass = true)
    static class SecurityConfiguration
    {
        @Bean static SecurityContextHolderStrategy strategy() { return new IsolatedStrategy(); }
        @Bean IdentityService identityService(SecurityContextHolderStrategy strategy) { return new IdentityService(strategy); }
    }

    static class IsolatedStrategy implements SecurityContextHolderStrategy
    {
        private final ThreadLocal<SecurityContext> contexts = ThreadLocal.withInitial(SecurityContextImpl::new);
        public void clearContext() { contexts.remove(); }
        public SecurityContext getContext() { return contexts.get(); }
        public void setContext(SecurityContext context) { contexts.set(context); }
        public SecurityContext createEmptyContext() { return new SecurityContextImpl(); }
    }
}
