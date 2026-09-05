package ai.loomspan.internal.security;

import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSkillSecurityStartupTests
{
    @Test
    void rejectsAbsentAndDisabledJsr250IncludingPermitAndDeny() throws Exception
    {
        for (Class<?> configuration : List.of(Absent.class, Disabled.class))
        {
            for (String name : List.of("restricted", "permitted", "denied"))
            {
                try (var context = new AnnotationConfigApplicationContext())
                {
                    context.register(configuration);
                    var bean = new JavaSkillAuthorizationIntegrationTests.PolicyService();
                    var method = bean.getClass().getMethod(name);
                    var policy = new SkillAccessPolicyResolver().resolve(method, bean.getClass());
                    context.registerBean("service", JavaSkillAuthorizationIntegrationTests.PolicyService.class, () -> bean);
                    context.registerBean("verify", org.springframework.beans.factory.SmartInitializingSingleton.class,
                            () -> () -> new Jsr250EnforcementVerifier(context).verify(context.getBean("service"), method, method,
                                    bean.getClass(), policy, "service"));
                    assertThatThrownBy(context::refresh)
                            .hasMessageContaining("service#").hasMessageContaining("jsr250Enabled = true");
                }
            }
        }
    }

    @Test
    void unrelatedAdviceCannotStandInForJsr250() throws Exception
    {
        try (var context = new AnnotationConfigApplicationContext(JavaSkillAuthorizationIntegrationTests.DefaultSecurity.class))
        {
            var factory = new ProxyFactory(new JavaSkillAuthorizationIntegrationTests.PolicyService());
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
            var method = JavaSkillAuthorizationIntegrationTests.PolicyService.class.getMethod("permitted");
            assertThatThrownBy(() -> new Jsr250EnforcementVerifier(context).verify(factory.getProxy(), method, method,
                    JavaSkillAuthorizationIntegrationTests.PolicyService.class, SkillAccessPolicy.permitAll(), "service"))
                    .hasMessageContaining("applicable Spring JSR-250 advice");
        }
    }

    @Test
    void unannotatedNeedsNoAdviceAndFinalClassProxyMethodIsRejected() throws Exception
    {
        var verifier = new Jsr250EnforcementVerifier(new StaticListableBeanFactory());
        var plain = new JavaSkillAuthorizationIntegrationTests.Plain();
        assertThatCode(() -> verifier.verify(plain, plain.getClass().getMethod("execute"), plain.getClass().getMethod("execute"), plain.getClass(),
                SkillAccessPolicy.unrestricted(), "plain")).doesNotThrowAnyException();
        var factory = new ProxyFactory(new FinalMethod());
        factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
        assertThatThrownBy(() -> verifier.verify(factory.getProxy(), FinalMethod.class.getMethod("execute"), FinalMethod.class.getMethod("execute"),
                FinalMethod.class, SkillAccessPolicy.permitAll(), "finalMethod"))
                .hasMessageContaining("proxyable method");
    }

    public static class FinalMethod { @PermitAll public final String execute() { return "ok"; } }
    @Configuration(proxyBeanMethods = false) static class Absent { }
    @Configuration(proxyBeanMethods = false) @EnableMethodSecurity(jsr250Enabled = false) static class Disabled { }
}
