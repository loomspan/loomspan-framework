package ai.loomspan.internal.core;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ai.loomspan.api.SkillMethod;
import org.junit.jupiter.api.Test;
import ai.loomspan.api.SkillParam;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillMethodTargetDiscoveryIntegrationTests
{
    @Test
    void discoversInterfaceDeclaredAnnotationAndParameterContractOnJdkProxyExactlyOnce() throws Exception
    {
        InterfaceSkill proxy = jdkProxy(new InterfaceSkillImpl(), null);
        TestHarness harness = process(proxy, "interfaceSkill");

        assertThat(harness.registry().getAllCapabilities())
                .extracting(CapabilityMetadata::name)
                .containsExactly("execute");
        CapabilityMetadata target = harness.registry().getCapability("execute");
        JsonNode schema = new ObjectMapper().readTree(target.tool().inputSchema());
        assertThat(schema.path("properties").has("internalInput")).isFalse();
        assertThat(schema.path("properties").path("externalInput").path("description").asText())
                .isEqualTo("Interface optional input");
        assertThat(schema.path("required"))
                .noneMatch(node -> "externalInput".equals(node.asText()) || "internalInput".equals(node.asText()));
        assertThat(target.invoker().invoke(Map.of("externalInput", "alpha")))
                .isEqualTo("\"interface:alpha\"");
    }

    @Test
    void canonicalizesGenericBridgeMethodWithoutFalseOverload()
    {
        GenericSkill<String> proxy = jdkProxy(new StringGenericSkill(), null);
        TestHarness harness = process(proxy, "genericSkill");

        assertThat(harness.registry().getAllCapabilities())
                .extracting(CapabilityMetadata::name)
                .containsExactly("convert");
        CapabilityMetadata target = harness.registry().getCapability("convert");
        assertThat(target.tool().inputSchema()).contains("genericInput").doesNotContain("implementationInput");
        assertThat(target.invoker().invoke(Map.of("genericInput", "alpha")))
                .isEqualTo("\"generic:alpha\"");
    }

    @Test
    void discoversCglibProxiedImplementationOnceAndInvokesThroughAdvice()
    {
        AtomicInteger adviceCalls = new AtomicInteger();
        ConcreteSkill proxy = classProxy(new ConcreteSkill(), adviceCalls);
        TestHarness harness = process(proxy, "concreteSkill");

        assertThat(harness.registry().getAllCapabilities())
                .extracting(CapabilityMetadata::name)
                .containsExactly("execute");
        assertThat(harness.registry().getCapability("execute").invoker().invoke(Map.of("input", "alpha")))
                .isEqualTo("\"class:alpha\"");
        assertThat(adviceCalls).hasValue(1);
    }

    @Test
    void acceptsExplicitlyNamedOverloadsAfterProxyCanonicalization()
    {
        OverloadedConcreteSkill proxy = classProxy(new OverloadedConcreteSkill(), new AtomicInteger());
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        SkillMethodBeanPostProcessor processor = new SkillMethodBeanPostProcessor(registry);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("overloadedSkill", proxy);
        processor.setBeanFactory(beanFactory);

        processor.postProcessAfterInitialization(proxy, "overloadedSkill");
        assertThat(registry.getAllCapabilities()).extracting(CapabilityMetadata::name)
                .containsExactlyInAnyOrder("executeText", "executeNumber");
        assertThat(registry.getCapability("executeText").invoker().invoke(Map.of("input", "alpha")))
                .isEqualTo("\"alpha\"");
        assertThat(registry.getCapability("executeNumber").invoker().invoke(Map.of("input", 42)))
                .isEqualTo("\"42\"");
    }

    @Test
    void selectsAnnotatedInterfaceMethodWhenJdkProxyExposesBroaderOverload()
    {
        Object proxy = jdkProxy(new CompetingInterfaceSkillImpl(), null);
        TestHarness harness = process(proxy, "competingSkill");

        assertThat(harness.registry().getCapability("execute").invoker()
                .invoke(Map.of("input", "alpha")))
                .isEqualTo("\"annotated:alpha\"");
    }

    @Test
    void rejectsConflictingAnnotatedInterfaceContracts()
    {
        Object proxy = jdkProxy(new ConflictingContractSkillImpl(), null);
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        SkillMethodBeanPostProcessor processor = new SkillMethodBeanPostProcessor(registry);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("conflictingContractSkill", proxy);
        processor.setBeanFactory(beanFactory);

        assertThatThrownBy(() -> processor.postProcessAfterInitialization(proxy, "conflictingContractSkill"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflictingContractSkill")
                .hasMessageContaining("execute")
                .hasMessageContaining("incompatible method or parameter metadata")
                .hasMessageContaining("one public interface contract");
        assertThat(registry.getAllCapabilities()).isEmpty();
    }

    @Test
    void acceptsEquivalentAnnotatedInterfaceContracts()
    {
        Object proxy = jdkProxy(new EquivalentContractSkillImpl(), null);
        TestHarness harness = process(proxy, "equivalentContractSkill");

        assertThat(harness.registry().getCapability("execute").invoker()
                .invoke(Map.of("publicInput", "alpha")))
                .isEqualTo("\"alpha\"");
    }

    @Test
    void doesNotTurnProxyAccessDeniedIntoAResult()
    {
        var denial = new org.springframework.security.access.AccessDeniedException("denied by proxy");
        var authenticationFailure = new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("no caller");
        for (RuntimeException failure : new RuntimeException[] {denial, authenticationFailure,
                new IllegalStateException("proxy wrapper", denial), new IllegalStateException("proxy wrapper", authenticationFailure)})
        {
            AtomicInteger businessCalls = new AtomicInteger();
            ProxyFactory factory = new ProxyFactory(new DeniedSkill(businessCalls));
            factory.setProxyTargetClass(true);
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> { throw failure; });
            TestHarness harness = process(factory.getProxy(), "deniedSkill");
            RuntimeException expected = failure.getCause() instanceof RuntimeException cause ? cause : failure;
            assertThatThrownBy(() -> harness.registry().getCapability("denied").invoker().invoke(Map.of()))
                    .isSameAs(expected);
            assertThat(businessCalls).hasValue(0);
        }
    }

    static class DeniedSkill
    {
        private final AtomicInteger calls;
        DeniedSkill(AtomicInteger calls) { this.calls = calls; }
        @SkillMethod(description = "Denied") public String denied() { calls.incrementAndGet(); return "should never run"; }
    }

    private TestHarness process(Object bean, String beanName)
    {
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        SkillMethodBeanPostProcessor processor = new SkillMethodBeanPostProcessor(registry);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(beanName, bean);
        processor.setBeanFactory(beanFactory);
        processor.postProcessAfterInitialization(bean, beanName);
        return new TestHarness(registry);
    }

    @SuppressWarnings("unchecked")
    private <T> T jdkProxy(T target, AtomicInteger adviceCalls)
    {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(false);
        if (adviceCalls != null)
        {
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                adviceCalls.incrementAndGet();
                return invocation.proceed();
            });
        }
        return (T) factory.getProxy();
    }

    private ConcreteSkill classProxy(ConcreteSkill target, AtomicInteger adviceCalls)
    {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            adviceCalls.incrementAndGet();
            return invocation.proceed();
        });
        return (ConcreteSkill) factory.getProxy();
    }

    private OverloadedConcreteSkill classProxy(OverloadedConcreteSkill target, AtomicInteger adviceCalls)
    {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            adviceCalls.incrementAndGet();
            return invocation.proceed();
        });
        return (OverloadedConcreteSkill) factory.getProxy();
    }

    interface InterfaceSkill
    {
        @SkillMethod(description = "Interface skill")
        String execute(@SkillParam(description = "Interface optional input", required = false) String externalInput);
    }

    static class InterfaceSkillImpl implements InterfaceSkill
    {
        @Override
        public String execute(String internalInput)
        {
            return "interface:" + internalInput;
        }
    }

    interface FirstContractSkill
    {
        @SkillMethod(description = "Conflicting skill")
        String execute(@SkillParam(description = "First contract", required = false) String publicInput);
    }

    interface SecondContractSkill
    {
        @SkillMethod(description = "Conflicting skill")
        String execute(@SkillParam(description = "Second contract") String alternateInput);
    }

    static class ConflictingContractSkillImpl implements FirstContractSkill, SecondContractSkill
    {
        @Override
        public String execute(String internalInput)
        {
            return internalInput;
        }
    }

    interface FirstEquivalentContractSkill
    {
        @SkillMethod(description = "Equivalent skill")
        String execute(@SkillParam(description = "Public input") String publicInput);
    }

    interface SecondEquivalentContractSkill
    {
        @SkillMethod(description = "Equivalent skill")
        String execute(@SkillParam(description = "Public input") String publicInput);
    }

    static class EquivalentContractSkillImpl implements FirstEquivalentContractSkill, SecondEquivalentContractSkill
    {
        @Override
        public String execute(String internalInput)
        {
            return internalInput;
        }
    }

    interface GenericSkill<T>
    {
        @SkillMethod(description = "Generic skill")
        T convert(T genericInput);
    }

    static class StringGenericSkill implements GenericSkill<String>
    {
        @Override
        public String convert(String implementationInput)
        {
            return "generic:" + implementationInput;
        }
    }

    interface AnnotatedStringSkill
    {
        @SkillMethod(description = "Annotated string skill")
        String execute(String input);
    }

    interface BroadOverloadSkill
    {
        String execute(CharSequence input);
    }

    static class CompetingInterfaceSkillImpl implements AnnotatedStringSkill, BroadOverloadSkill
    {
        @Override
        public String execute(String input)
        {
            return "annotated:" + input;
        }

        @Override
        public String execute(CharSequence input)
        {
            return "broad:" + input;
        }
    }

    static class ConcreteSkill
    {
        @SkillMethod(description = "Concrete skill")
        public String execute(String input)
        {
            return "class:" + input;
        }
    }

    static class OverloadedConcreteSkill
    {
        @SkillMethod(name = "executeText", description = "String skill")
        public String execute(String input)
        {
            return input;
        }

        @SkillMethod(name = "executeNumber", description = "Integer skill")
        public String execute(Integer input)
        {
            return String.valueOf(input);
        }
    }

    private record TestHarness(InMemoryCapabilityRegistry registry)
    {
    }
}
