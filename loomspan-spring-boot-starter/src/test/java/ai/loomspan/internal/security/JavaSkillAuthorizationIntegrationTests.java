package ai.loomspan.internal.security;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSkillAuthorizationIntegrationTests
{
    @Test
    void matchesRealSpringClassProxyWithDefaultCustomAndEmptyPrefix() throws Exception
    {
        for (Class<?> configuration : List.of(DefaultSecurity.class, CustomPrefix.class, EmptyPrefix.class))
        {
            try (var context = new AnnotationConfigApplicationContext(configuration))
            {
                PolicyService bean = context.getBean(PolicyService.class);
                String prefix = context.getBeanProvider(GrantedAuthorityDefaults.class)
                        .getIfAvailable(() -> new GrantedAuthorityDefaults("ROLE_")).getRolePrefix();
                SkillRoleEvaluator evaluator = new SkillRoleEvaluator(new GrantedAuthorityDefaults(prefix),
                        context.getBeanProvider(RoleHierarchy.class).getIfAvailable());
                for (String methodName : List.of("restricted", "permitted", "denied", "empty", "prefixed", "inherited"))
                {
                    Method method = PolicyService.class.getMethod(methodName);
                    SkillAccessPolicy policy = new SkillAccessPolicyResolver().resolve(method, PolicyService.class);
                    new Jsr250EnforcementVerifier(context).verify(bean, method, method, PolicyService.class, policy, "service");
                    for (Authentication authentication : new Authentication[] {null, auth("WRONG"),
                            auth(prefix + "READ"), auth(prefix + "WRITE"), auth(prefix + "ADMIN"),
                            auth(prefix + "ROLE_READ")})
                    {
                        boolean actual = invokeAllowed(bean, method, authentication);
                        assertThat(evaluator.canAccess(policy, authentication)).as("%s/%s/%s", prefix, methodName, authentication)
                                .isEqualTo(actual);
                        if (policy.kind() == SkillAccessPolicy.Kind.ROLES && !policy.roles().isEmpty())
                        {
                            assertThat(evaluator.canAccess(SkillAccessPolicy.yamlRoles(policy.roles()), authentication))
                                    .isEqualTo(actual);
                        }
                    }
                }
                PolicyService target = (PolicyService) ((org.springframework.aop.framework.Advised) bean).getTargetSource().getTarget();
                assertThat(target.deniedCalls).isZero();
            }
        }
    }

    @Test
    void matchesInterfacePolicyOnJdkProxyAndMethodOverride() throws Exception
    {
        try (var context = new AnnotationConfigApplicationContext(JdkSecurity.class))
        {
            Contract bean = context.getBean(Contract.class);
            for (String name : List.of("restricted", "permitted"))
            {
                Method method = Contract.class.getMethod(name);
                SkillAccessPolicy policy = new SkillAccessPolicyResolver().resolve(method, ContractImpl.class);
                new Jsr250EnforcementVerifier(context).verify(bean, method, method, ContractImpl.class, policy, "contract");
                for (Authentication authentication : new Authentication[] {null, auth("ROLE_READ"), auth("OTHER")})
                {
                    assertThat(new SkillRoleEvaluator(null, null).canAccess(policy, authentication))
                            .isEqualTo(invokeAllowed(bean, method, authentication));
                }
            }
        }
    }

    @Test
    void springScannerRejectsAmbiguousPolicies() throws Exception
    {
        var resolver = new SkillAccessPolicyResolver();
        assertThatThrownBy(() -> resolver.resolve(Conflicting.class.getMethod("execute"), Conflicting.class))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> resolver.resolve(Multiple.class.getMethod("execute"), Multiple.class))
                .isInstanceOf(RuntimeException.class);
        assertThat(resolver.resolve(Plain.class.getMethod("execute"), Plain.class))
                .isEqualTo(SkillAccessPolicy.unrestricted());
    }

    @Test
    void duplicateEquivalentInterfacePoliciesFollowSpringsAmbiguityRule() throws Exception
    {
        var method = Equivalent.class.getMethod("execute");
        assertThatThrownBy(() -> new SkillAccessPolicyResolver().resolve(method, Equivalent.class))
                .isInstanceOf(org.springframework.core.annotation.AnnotationConfigurationException.class);
        try (var context = new AnnotationConfigApplicationContext(EquivalentSecurity.class))
        {
            var bean = context.getBean(EqualLeft.class);
            try (var ignored = new ScopedAuthentication(null).open(auth("ROLE_READ")))
            {
                assertThatThrownBy(bean::execute)
                        .isInstanceOf(org.springframework.core.annotation.AnnotationConfigurationException.class);
            }
        }
    }

    static Authentication auth(String... roles)
    {
        return UsernamePasswordAuthenticationToken.authenticated("user", "unused", AuthorityUtils.createAuthorityList(roles));
    }

    private static boolean invokeAllowed(Object bean, Method method, Authentication authentication) throws Exception
    {
        try (var ignored = new ScopedAuthentication(null).open(authentication))
        {
            try { method.invoke(bean); return true; }
            catch (InvocationTargetException exception)
            {
                if (exception.getCause() instanceof org.springframework.security.access.AccessDeniedException
                        || exception.getCause() instanceof org.springframework.security.core.AuthenticationException) return false;
                throw exception;
            }
        }
    }

    @RolesAllowed("READ")
    public static class Parent { public String inherited() { return "ok"; } }
    @RolesAllowed("READ")
    public static class PolicyService extends Parent
    {
        int deniedCalls;
        @RolesAllowed({"READ", "WRITE"}) public String restricted() { return "ok"; }
        @PermitAll public String permitted() { return "ok"; }
        @DenyAll public String denied() { deniedCalls++; return "bad"; }
        @RolesAllowed({}) public String empty() { return "bad"; }
        @RolesAllowed("ROLE_READ") public String prefixed() { return "ok"; }
    }
    @RolesAllowed("READ")
    interface Contract { String restricted(); @PermitAll String permitted(); }
    public static class ContractImpl implements Contract
    {
        public String restricted() { return "ok"; }
        public String permitted() { return "ok"; }
    }
    interface Left { @RolesAllowed("A") String execute(); }
    interface Right { @RolesAllowed("B") String execute(); }
    public static class Conflicting implements Left, Right { public String execute() { return "ok"; } }
    interface EqualLeft { @RolesAllowed("READ") String execute(); }
    interface EqualRight { @RolesAllowed("READ") String execute(); }
    public static class Equivalent implements EqualLeft, EqualRight { public String execute() { return "ok"; } }
    public static class Multiple { @PermitAll @DenyAll public String execute() { return "ok"; } }
    public static class Plain { public String execute() { return "ok"; } }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true, proxyTargetClass = true)
    static class DefaultSecurity
    {
        @Bean PolicyService service() { return new PolicyService(); }
        @Bean static RoleHierarchy hierarchy() { return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_READ"); }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true, proxyTargetClass = true)
    static class CustomPrefix
    {
        @Bean PolicyService service() { return new PolicyService(); }
        @Bean static GrantedAuthorityDefaults defaults() { return new GrantedAuthorityDefaults("APP_"); }
        @Bean static RoleHierarchy hierarchy() { return RoleHierarchyImpl.fromHierarchy("APP_ADMIN > APP_READ"); }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true, proxyTargetClass = true)
    static class EmptyPrefix
    {
        @Bean PolicyService service() { return new PolicyService(); }
        @Bean static GrantedAuthorityDefaults defaults() { return new GrantedAuthorityDefaults(""); }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true)
    static class JdkSecurity { @Bean Contract contract() { return new ContractImpl(); } }
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true)
    static class EquivalentSecurity { @Bean EqualLeft equivalent() { return new Equivalent(); } }
}
