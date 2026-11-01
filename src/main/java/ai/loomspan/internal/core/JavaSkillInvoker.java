package ai.loomspan.internal.core;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** One final managed-proxy invocation and successful-result conversion for both Java roles. */
final class JavaSkillInvoker
{
    private final ObjectMapper objectMapper;

    JavaSkillInvoker(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    String invoke(BeanFactory beanFactory, String beanName, Method method, Method contractMethod,
            String name, Object[] arguments)
    {
        if (beanFactory == null) throw new IllegalStateException("BeanFactory has not been set for Java declaration '" + name + "'");
        Object bean = beanFactory.getBean(beanName);
        Method invocableMethod = selectRuntimeInvocableMethod(method, contractMethod, bean.getClass());
        Object result;
        try
        {
            ReflectionUtils.makeAccessible(invocableMethod);
            result = invocableMethod.invoke(bean, arguments);
        }
        catch (InvocationTargetException ex) { throw SkillFailurePolicy.propagate(ex); }
        catch (IllegalAccessException ex) { throw new IllegalStateException("Cannot invoke Java declaration '" + name + "'", ex); }
        if (result == null) return "";
        if (result instanceof String text) return text;
        try { return objectMapper.writeValueAsString(result); }
        catch (JacksonException ex) { throw new IllegalStateException("Failed to serialize Java skill result for " + name, ex); }
    }

    static Method selectRuntimeInvocableMethod(Method canonicalMethod, Method contractMethod, Class<?> runtimeClass)
    {
        try
        {
            return AopUtils.selectInvocableMethod(canonicalMethod, runtimeClass);
        }
        catch (IllegalStateException ex)
        {
            if (contractMethod.getDeclaringClass().isInterface()
                    && contractMethod.getDeclaringClass().isAssignableFrom(runtimeClass))
            {
                return contractMethod;
            }

            List<Method> candidates = ClassUtils.getAllInterfacesForClassAsSet(runtimeClass).stream()
                    .flatMap(type -> java.util.Arrays.stream(type.getMethods()))
                    .filter(candidate -> mapsToCanonicalMethod(
                            candidate,
                            canonicalMethod.getDeclaringClass(),
                            canonicalMethod))
                    .sorted(java.util.Comparator.comparing(Method::toGenericString))
                    .toList();
            if (candidates.size() == 1)
            {
                return candidates.getFirst();
            }
            if (!candidates.isEmpty() && candidates.stream().allMatch(candidate ->
                    java.util.Arrays.equals(candidate.getParameterTypes(), candidates.getFirst().getParameterTypes())))
            {
                return candidates.getFirst();
            }
            throw ex;
        }
    }

    static boolean mapsToCanonicalMethod(Method interfaceMethod, Class<?> targetClass, Method canonicalMethod)
    {
        Method implementationMethod = ReflectionUtils.findMethod(
                targetClass,
                interfaceMethod.getName(),
                interfaceMethod.getParameterTypes());
        return implementationMethod != null
                && BridgeMethodResolver.findBridgedMethod(implementationMethod).equals(canonicalMethod);
    }

}
