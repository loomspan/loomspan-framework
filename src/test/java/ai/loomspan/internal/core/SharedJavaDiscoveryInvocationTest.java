package ai.loomspan.internal.core;

import ai.loomspan.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.DenyAll;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

class SharedJavaDiscoveryInvocationTest
{
    static final SkillExecutorInvocation INPUT = new SkillExecutorInvocation(Map.of("value", "input"),
            Map.of("value", "skill"), Map.of("value", "executor"), "sales-create", "generation");

    static SkillMethodBeanPostProcessor process(Object bean)
    {
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("applicationBean", bean);
        var processor = new SkillMethodBeanPostProcessor(); processor.setBeanFactory(factory);
        processor.postProcessAfterInitialization(bean, "applicationBean");
        return processor;
    }

    static Supplier<String> invocation(SkillMethodBeanPostProcessor processor, boolean executor)
    {
        if (executor) return () -> processor.executors().stream().filter(e -> e.name().equals("default-result"))
                .findFirst().orElseThrow().invoker().apply(INPUT);
        return () -> (String) processor.capabilities().stream().filter(c -> c.name().equals("default-result"))
                .findFirst().orElseThrow().invoker().invoke(Map.of());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sharedResultsAndPropagationUseTheManagedProxy(boolean executor)
    {
        var bean = new Results();
        var calls = new AtomicInteger();
        var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true);
        proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) call -> { calls.incrementAndGet(); return call.proceed(); });
        var processor = process(proxy.getProxy());
        var invoke = invocation(processor, executor);
        for (String text : List.of("", " \t\n", "hello", "{\"value\":2}", "ERROR: preserve exact text"))
        {
            bean.value = text; assertThat(invoke.get()).isEqualTo(text);
        }
        bean.value = null; assertThat(invoke.get()).isEmpty();
        bean.value = Map.of("number", 2); assertThat(invoke.get()).isEqualTo("{\"number\":2}");
        bean.value = List.of(true, 4); assertThat(invoke.get()).isEqualTo("[true,4]");
        bean.value = 7; assertThat(invoke.get()).isEqualTo("7");
        assertThat(calls).hasValue(9);
        var recoverable = new RecoverableSkillException("  correct this\n", new Exception("details"));
        for (Throwable failure : List.of(recoverable, new IllegalStateException("application wrapper", recoverable),
                new SkillException("terminal", recoverable), new java.util.concurrent.CancellationException("cancelled"),
                new org.springframework.security.access.AccessDeniedException("denied"), new AssertionError("fatal")))
        {
            bean.failure = failure;
            assertThatThrownBy(invoke::get).isSameAs(failure);
            assertThat(SkillFailurePolicy.recoverable(failure)).isEqualTo(failure == recoverable ? recoverable : null);
        }
        bean.failure = null; bean.value = new BrokenResult();
        assertThatThrownBy(invoke::get).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize").hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
    }

    static class Results
    {
        Object value; Throwable failure;
        Object result() { if (failure instanceof RuntimeException ex) throw ex; if (failure instanceof Error ex) throw ex; return value; }
        @SkillMethod(name = "result", description = "Callable result") public Object callable() { return result(); }
        @SkillExecutor(name = "result") public Object executor(SkillExecutorInvocation invocation)
        {
            assertThat(invocation).isSameAs(INPUT);
            assertThat(invocation.input().get("value")).isEqualTo("input");
            assertThat(invocation.skillConfig().get("value")).isEqualTo("skill");
            assertThat(invocation.executorConfig().get("value")).isEqualTo("executor");
            return result();
        }
    }
    static class BrokenResult { public String getValue() { throw new IllegalStateException("serializer getter failure"); } }

    @Test void callableOpenedStreamsCloseOnSuccessInvocationFailureAndSerializationFailure()
    {
        var bean = new StreamResults(); var processor = process(bean);
        var callable = processor.capabilities().getFirst();
        for (int mode : List.of(0, 1, 2))
        {
            bean.mode = mode;
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            var stream = new java.io.ByteArrayInputStream(new byte[] {1}) {
                @Override public void close() { closed.set(true); }
            };
            if (mode == 0) assertThat(callable.invoker().invoke(Map.of("value", stream))).isEqualTo("ok");
            else assertThatThrownBy(() -> callable.invoker().invoke(Map.of("value", stream))).isInstanceOf(IllegalStateException.class);
            assertThat(closed).isTrue();
        }
    }
    static class StreamResults {
        int mode;
        @SkillMethod(description="Stream") public Object result(java.io.InputStream value) {
            if (mode == 1) throw new IllegalStateException("invocation failed");
            return mode == 2 ? new BrokenResult() : "ok";
        }
    }

    @Test void separateRoleIndexesAllowSameNameAndRejectDuplicatesWithinRole()
    {
        var processor = process(new Results());
        assertThat(processor.capabilities()).extracting(CapabilityMetadata::name).containsExactly("default-result");
        assertThat(processor.executors()).extracting(SkillExecutorRegistration::name).containsExactly("default-result");
        assertThat(processor.executors()).isEqualTo(processor.executors());
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(new ExecutorDuplicate(), "secondBean"))
                .isInstanceOf(CapabilityCollisionException.class).hasMessageContaining("default-result")
                .hasMessageContaining("applicationBean").hasMessageContaining("secondBean");
        var other = process(new ExecutorOnly());
        assertThat(other.capabilities()).isEmpty();
        assertThat(other.executors()).extracting(SkillExecutorRegistration::name).containsExactly("tax-Run");
        assertThat(other.executors().getFirst().config()).isEqualTo("connections.selected");
        assertThat(other.executors().getFirst().invoker().apply(INPUT)).isEmpty();
    }
    static class ExecutorDuplicate { @SkillExecutor(name="result") public void execute(SkillExecutorInvocation value) {} }
    static class ExecutorOnly { @SkillExecutor(namespace="Tax", name="Run", config="connections.selected") public void run(SkillExecutorInvocation value) {} }

    @Test void lazyExecutorOnlyBeanUsesTheSameCompletionPassWithoutExecutingIt()
    {
        var created = new AtomicInteger();
        try (var context = new AnnotationConfigApplicationContext())
        {
            context.registerBean(SkillMethodBeanPostProcessor.class);
            context.registerBean("lazyExecutor", ExecutorOnly.class, () -> { created.incrementAndGet(); return new ExecutorOnly(); },
                    definition -> definition.setLazyInit(true));
            context.refresh(); assertThat(created).hasValue(0);
            var processor = context.getBean(SkillMethodBeanPostProcessor.class);
            assertThat(processor.executors()).hasSize(1);
            assertThat(processor.capabilities()).isEmpty();
            assertThat(processor.executors()).hasSize(1); assertThat(created).hasValue(1);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void executorGenericInterfaceBridgeAndEquivalentContractsUseProxyAdvice(boolean cglib)
    {
        var calls = new AtomicInteger();
        var proxy = new ProxyFactory(new GenericExecutor()); proxy.setProxyTargetClass(cglib);
        proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) call -> { calls.incrementAndGet(); return call.proceed(); });
        var processor = process(proxy.getProxy());
        assertThat(processor.capabilities()).isEmpty();
        assertThat(processor.executors()).hasSize(1);
        assertThat(processor.executors().getFirst().invoker().apply(INPUT)).isEqualTo("sales-create");
        assertThat(calls).hasValue(1);
    }
    interface GenericContract<T> { @SkillExecutor String execute(T value); }
    interface EquivalentContract { @SkillExecutor String execute(SkillExecutorInvocation differentParameterName); }
    static class GenericExecutor implements GenericContract<SkillExecutorInvocation>, EquivalentContract
    { public String execute(SkillExecutorInvocation invocation) { return invocation.skillName(); } }

    @Test void rejectsEverySplitRoleAndIncompatibleInterfaceContract()
    {
        for (Object bean : List.of(new DualRole(), new SplitForward(), new SplitReverse(), new SplitInterfaces(), new GenericSplit()))
            assertThatThrownBy(() -> process(bean)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Conflicting").hasMessageContaining("SkillMethod").hasMessageContaining("SkillExecutor")
                    .hasMessageContaining("applicationBean");
        assertThatThrownBy(() -> process(new ConflictingExecutor())).hasMessageContaining("incompatible").hasMessageContaining("applicationBean");
        assertThatThrownBy(() -> process(new HiddenCallableConflict())).hasMessageContaining("incompatible").hasMessageContaining("applicationBean");
    }
    static class DualRole { @SkillMethod(description="both") @SkillExecutor public void execute(SkillExecutorInvocation value) {} }
    interface CallableContract { @SkillMethod(description="callable") void execute(SkillExecutorInvocation value); }
    interface ExecutorContract { @SkillExecutor void execute(SkillExecutorInvocation value); }
    static class SplitForward implements CallableContract { @SkillExecutor public void execute(SkillExecutorInvocation value) {} }
    static class SplitReverse implements ExecutorContract { @SkillMethod(description="callable") public void execute(SkillExecutorInvocation value) {} }
    static class SplitInterfaces implements CallableContract, ExecutorContract { public void execute(SkillExecutorInvocation value) {} }
    static class GenericSplit implements GenericContract<SkillExecutorInvocation>
    { @SkillMethod(description="callable") public String execute(SkillExecutorInvocation value) { return ""; } }
    interface ExecutorA { @SkillExecutor(config="a") void execute(SkillExecutorInvocation value); }
    interface ExecutorB { @SkillExecutor(config="b") void execute(SkillExecutorInvocation value); }
    static class ConflictingExecutor implements ExecutorA, ExecutorB
    { @SkillExecutor public void execute(SkillExecutorInvocation value) {} }
    interface CallableA { @SkillMethod(name="a",description="same") void execute(); }
    interface CallableB { @SkillMethod(name="b",description="same") void execute(); }
    static class HiddenCallableConflict implements CallableA, CallableB { @SkillMethod(description="impl") public void execute() {} }

    @Test void rejectsInvalidExecutorArgumentsAndIdentities()
    {
        for (Object bean : List.of(new NoArguments(), new TooManyArguments(), new WrongArgument(), new VarargsArgument()))
            assertThatThrownBy(() -> process(bean)).hasMessageContaining("exactly one SkillExecutorInvocation").hasMessageContaining("applicationBean");
        for (Object bean : List.of(new BadName(), new BadNamespace(), new TooLongIdentity()))
            assertThatThrownBy(() -> process(bean)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SkillExecutor");
    }
    static class NoArguments { @SkillExecutor void execute() {} }
    static class TooManyArguments { @SkillExecutor void execute(SkillExecutorInvocation a, SkillExecutorInvocation b) {} }
    static class WrongArgument { @SkillExecutor void execute(Map<String,Object> value) {} }
    static class VarargsArgument { @SkillExecutor void execute(SkillExecutorInvocation... value) {} }
    static class BadName { @SkillExecutor(name="bad-name") void execute(SkillExecutorInvocation value) {} }
    static class BadNamespace { @SkillExecutor(namespace="nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn") void execute(SkillExecutorInvocation value) {} }
    static class TooLongIdentity { @SkillExecutor(namespace="nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn",name="ssssssssssssssssssssssssssssssss") void execute(SkillExecutorInvocation value) {} }

    @Test void rejectsUnsafeProxyDispatchForExecutors()
    {
        for (Object bean : List.of(new FinalExecutor(), new StaticExecutor(), new PrivateExecutor()))
        {
            var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true);
            proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) call -> call.proceed());
            assertThatThrownBy(() -> process(proxy.getProxy()).executors()).hasMessageContaining("cannot safely invoke");
        }
        var proxy = new ProxyFactory(new HiddenExecutor()); proxy.setInterfaces(Runnable.class);
        assertThatThrownBy(() -> process(proxy.getProxy()).executors()).isInstanceOf(IllegalStateException.class);
    }
    static class FinalExecutor { @SkillExecutor public final void execute(SkillExecutorInvocation value) {} }
    static class StaticExecutor { @SkillExecutor public static void execute(SkillExecutorInvocation value) {} }
    static class PrivateExecutor { @SkillExecutor private void execute(SkillExecutorInvocation value) {} }
    static class HiddenExecutor implements Runnable { public void run() {} @SkillExecutor public void execute(SkillExecutorInvocation value) {} }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void realMethodSecurityIsPreservedForBothRoles(boolean jdk)
    {
        try (var context = new AnnotationConfigApplicationContext())
        {
            context.register(jdk ? JdkSecurity.class : ClassSecurity.class);
            context.registerBean("secured", Secured.class); context.registerBean(SkillMethodBeanPostProcessor.class);
            context.refresh();
            var processor = context.getBean(SkillMethodBeanPostProcessor.class);
            assertThat(processor.capabilities()).hasSize(3); assertThat(processor.executors()).hasSize(3);
            for (boolean executor : List.of(false,true))
            {
                Supplier<String> call = () -> executor ? processor.executors().stream().filter(e -> e.name().equals("default-secured"))
                        .findFirst().orElseThrow().invoker().apply(INPUT) : (String) processor.capabilities().stream()
                        .filter(c -> c.name().equals("default-secured")).findFirst().orElseThrow().invoker().invoke(Map.of());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "n/a", List.of(new SimpleGrantedAuthority("ROLE_READ"))));
                assertThat(call.get()).isEqualTo("authorized");
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OTHER"))));
                assertThatThrownBy(call::get).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                if (executor)
                {
                    assertThat(processor.executors().stream().filter(e -> e.name().equals("default-permit")).findFirst().orElseThrow().invoker().apply(INPUT)).isEqualTo("permitted");
                    assertThatThrownBy(() -> processor.executors().stream().filter(e -> e.name().equals("default-deny")).findFirst().orElseThrow().invoker().apply(INPUT))
                            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                }
            }
            assertThat(context.getBean(SecuredContract.class).calls()).isEqualTo(2);
        }
        finally { SecurityContextHolder.clearContext(); }
    }
    @Test void classLevelExecutorSecurityUsesTheSameResolverAndActualAdvice()
    {
        try (var context = new AnnotationConfigApplicationContext())
        {
            context.register(ClassSecurity.class); context.registerBean(ClassSecuredExecutor.class);
            context.registerBean(SkillMethodBeanPostProcessor.class); context.refresh();
            var registration = context.getBean(SkillMethodBeanPostProcessor.class).executors().getFirst();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OTHER"))));
            assertThatThrownBy(() -> registration.invoker().apply(INPUT)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "n/a", List.of(new SimpleGrantedAuthority("ROLE_READ"))));
            assertThat(registration.invoker().apply(INPUT)).isEqualTo("authorized");
        }
        finally { SecurityContextHolder.clearContext(); }
    }
    @RolesAllowed("READ") static class ClassSecuredExecutor {
        @SkillExecutor public String execute(SkillExecutorInvocation value) { return "authorized"; }
    }

    @Test void executorPolicyWithoutApplicableSecurityAdviceFailsCompletion()
    {
        assertThatThrownBy(() -> process(new Secured()).executors()).hasMessageContaining("requires applicable Spring JSR-250 advice");
    }
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(jsr250Enabled=true,proxyTargetClass=true) static class ClassSecurity {}
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(jsr250Enabled=true,proxyTargetClass=false) static class JdkSecurity {}
    interface SecuredContract {
        String callable();
        @SkillExecutor(name="secured") @RolesAllowed("READ") String execute(SkillExecutorInvocation invocation);
        String permitCallable(); @SkillExecutor(name="permit") @PermitAll String permit(SkillExecutorInvocation invocation);
        String denyCallable(); @SkillExecutor(name="deny") @DenyAll String deny(SkillExecutorInvocation invocation); int calls();
    }
    static class Secured implements SecuredContract {
        int count;
        @SkillMethod(name="secured",description="secured") @RolesAllowed("READ") public String callable() { count++; return "authorized"; }
        public String execute(SkillExecutorInvocation invocation) { count++; return "authorized"; }
        @SkillMethod(name="permit",description="permit") @PermitAll public String permitCallable() { return "permitted"; }
        public String permit(SkillExecutorInvocation invocation) { return "permitted"; }
        @SkillMethod(name="deny",description="deny") @DenyAll public String denyCallable() { throw new AssertionError(); }
        public String deny(SkillExecutorInvocation invocation) { throw new AssertionError(); }
        public int calls() { return count; }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualJdkAndCglibWrappersRemainTransparentButApplicationWrappersDoNot(boolean cglib) throws Exception
    {
        var recoverable = new RecoverableSkillException("correct input");
        var target = new Wrapped(); target.failure = recoverable;
        Throwable reflectionWrapper = catchThrowable(() -> Wrapped.class.getMethod("callable").invoke(target));
        assertThat(reflectionWrapper).isInstanceOf(InvocationTargetException.class);
        assertThat(SkillFailurePolicy.recoverable(reflectionWrapper)).isSameAs(recoverable);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(cglib);
        var processor = process(proxy.getProxy());
        for (boolean executor : List.of(false,true))
            assertThatThrownBy(invocation(processor,executor)::get).isSameAs(recoverable);
        var applicationWrapper = new IllegalStateException("application",recoverable); target.failure=applicationWrapper;
        for (boolean executor : List.of(false,true)) {
            Throwable thrown = catchThrowable(invocation(processor,executor)::get);
            assertThat(thrown).isSameAs(applicationWrapper); assertThat(SkillFailurePolicy.recoverable(thrown)).isNull();
        }
        var checked = new Exception("checked advice");
        var adviceProxy = new ProxyFactory(target); adviceProxy.setProxyTargetClass(cglib);
        adviceProxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) call -> { throw checked; });
        var adviceProcessor=process(adviceProxy.getProxy());
        for (boolean executor : List.of(false,true)) {
            Throwable thrown=catchThrowable(invocation(adviceProcessor,executor)::get);
            assertThat(thrown).isInstanceOf(UndeclaredThrowableException.class).hasCause(checked);
            assertThat(SkillFailurePolicy.recoverable(thrown)).isNull();
        }
        assertThat(SkillFailurePolicy.recoverable(new InvocationTargetException(new UndeclaredThrowableException(recoverable)))).isSameAs(recoverable);
        assertThat(SkillFailurePolicy.recoverable(new InvocationTargetException(applicationWrapper))).isNull();
        assertThat(SkillFailurePolicy.recoverable(new InvocationTargetException(null))).isNull();
        assertThat(SkillFailurePolicy.recoverable(null)).isNull();
    }
    interface WrappedContract { Object callable(); Object execute(SkillExecutorInvocation invocation); }
    static class Wrapped implements WrappedContract {
        RuntimeException failure;
        @SkillMethod(name="result",description="wrapped") public Object callable() { throw failure; }
        @SkillExecutor(name="result") public Object execute(SkillExecutorInvocation invocation) { throw failure; }
    }
}
