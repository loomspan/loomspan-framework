package ai.loomspan.internal.security;

import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Verifies the standard Spring JSR-250 advisor is actually attached to this callable. */
public final class Jsr250EnforcementVerifier
{
    private static final String INTERCEPTOR = "jsr250AuthorizationMethodInterceptor";
    private final BeanFactory beanFactory;

    public Jsr250EnforcementVerifier(BeanFactory beanFactory)
    {
        this.beanFactory = beanFactory;
    }

    public void verify(Object bean, Method method, Method invocableMethod, Class<?> targetClass, SkillAccessPolicy policy, String beanName)
    {
        if (!policy.annotated()) return;
        String message = "Java skill '" + beanName + "#" + method.toGenericString()
                + "' requires applicable Spring JSR-250 advice; enable @EnableMethodSecurity(jsr250Enabled = true)"
                + " and expose a proxyable method on the Spring bean";
        int modifiers = method.getModifiers();
        if (Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)
                || (AopUtils.isCglibProxy(bean) && Modifier.isFinal(modifiers))
                || !(bean instanceof Advised advised) || !beanFactory.containsBean(INTERCEPTOR)
                || !beanFactory.containsBean("_jsr250MethodSecurityConfiguration")
                || !invocableMethod.getDeclaringClass().isInstance(bean))
        {
            throw new IllegalStateException(message);
        }
        Object interceptor = beanFactory.getBean(INTERCEPTOR);
        for (Advisor advisor : advised.getAdvisors())
        {
            if ((advisor == interceptor || advisor.getAdvice() == interceptor)
                    && advisor instanceof PointcutAdvisor pointcutAdvisor
                    && pointcutAdvisor.getPointcut().getClassFilter().matches(targetClass)
                    && pointcutAdvisor.getPointcut().getMethodMatcher().matches(invocableMethod, targetClass))
            {
                return;
            }
        }
        throw new IllegalStateException(message);
    }
}
