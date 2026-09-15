package ai.loomspan.internal.skillapi;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.internal.core.LoomspanSessionRunner;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityInvoker;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.InMemoryCapabilityRegistry;
import ai.loomspan.internal.core.FrameworkExecutionLifecycle;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.trace.ImmediateCompletionRetention;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.springframework.context.support.StaticApplicationContext;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputSchemaNode;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.security.SkillRoleEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSkillTemplateTest {

    @Test
    void handoffCapturesPreparedInputAndCallingAuthentication()
    {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityMetadata metadata = yamlSkillMetadata();
        registry.register(metadata.name(), metadata);
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(2), executor);
        var codecs = LoomspanJacksonCodecs.defaults();
        var runner = new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS,
                fixedClock(), NoOpExecutionObservationHandleFactory.INSTANCE,
                ImmediateCompletionRetention.INSTANCE, new LoomspanProperties.Session.Quotas(),
                codecs.canonicalTrace(), lifecycle);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry, router, runner, new ObjectMapper(), new SkillInputValidator(),
                new SkillRoleEvaluator(null, null), null);
        DefaultSkillInvocationHandoff handoff = new DefaultSkillInvocationHandoff(template, lifecycle);
        var captured = new UsernamePasswordAuthenticationToken(
                "captured", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ALLOWED")));
        var worker = new UsernamePasswordAuthenticationToken("worker", "n/a", List.of());
        AtomicReference<Object> observedAuthentication = new AtomicReference<>();
        when(router.execute(eq(metadata), eq(Map.of("payload", "hello")), any(), eq(null)))
                .thenAnswer(invocation -> {
                    ai.loomspan.internal.core.LoomspanSession session = invocation.getArgument(2);
                    observedAuthentication.set(session.getAuthentication().orElse(null));
                    return "ok";
                });
        SecurityContextHolder.getContext().setAuthentication(captured);
        Map<String, Object> mutableInput = new LinkedHashMap<>();
        mutableInput.put("payload", "hello");
        var admitted = handoff.handoff("invoiceParser", mutableInput);
        mutableInput.clear();
        SecurityContextHolder.getContext().setAuthentication(worker);

        assertThat(admitted.invoke()).isEqualTo("ok");
        assertThat(observedAuthentication).hasValue(captured);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(worker);
        admitted.release();
        assertThatThrownBy(admitted::invoke)
                .isInstanceOf(SkillException.class)
                .hasMessage("Skill 'invoiceParser' execution failed.");
        lifecycle.destroy();
    }

    @Test
    void releasedAndCutOffHandoffsCannotExecute()
    {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        registry.register("invoiceParser", yamlSkillMetadata());
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(1), executor);
        var codecs = LoomspanJacksonCodecs.defaults();
        var runner = new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.NEVER,
                fixedClock(), NoOpExecutionObservationHandleFactory.INSTANCE,
                ImmediateCompletionRetention.INSTANCE, new LoomspanProperties.Session.Quotas(),
                codecs.canonicalTrace(), lifecycle);
        DefaultSkillInvocationHandoff handoff = new DefaultSkillInvocationHandoff(
                new DefaultSkillTemplate(registry, router, runner, new ObjectMapper(),
                        new SkillInputValidator(), new SkillRoleEvaluator(null, null), null), lifecycle);
        var released = handoff.handoff("invoiceParser", Map.of("payload", "hello"));
        released.release();
        released.release();
        assertThatThrownBy(released::invoke).isInstanceOf(SkillException.class);

        var cutOff = handoff.handoff("invoiceParser", Map.of("payload", "hello"));
        lifecycle.stop();
        assertThatThrownBy(cutOff::invoke).isInstanceOf(SkillException.class);
        verify(router, never()).execute(any(), any(), any(), any());
        lifecycle.destroy();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void capturesCurrentSecurityContextAuthenticationForRootInvocation() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        CapabilityMetadata yamlSkill = yamlSkillMetadata();
        registry.register("invoiceParser", yamlSkill);
        var authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ALLOWED")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AtomicReference<Object> observedAuthentication = new AtomicReference<>();
        when(router.execute(eq(yamlSkill), eq(Map.of("payload", "hello")), any(), eq(null)))
                .thenAnswer(invocation -> {
                    ai.loomspan.internal.core.LoomspanSession session = invocation.getArgument(2);
                    observedAuthentication.set(session.getAuthentication().orElse(null));
                    return "\"ok\"";
                });

        template.invoke("invoiceParser", Map.of("payload", "hello"));

        assertThat(observedAuthentication.get()).isSameAs(authentication);
    }

    @Test
    void preservesAccessDeniedExceptionInstance() {
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = templateWithRegisteredSkill(router);
        AccessDeniedException failure = new AccessDeniedException("denied");
        when(router.execute(any(), any(), any(), eq(null))).thenThrow(failure);

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello")))
                .isSameAs(failure);
    }

    @Test
    void preservesExistingSkillExceptionInstance() {
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = templateWithRegisteredSkill(router);
        SkillException failure = new SkillException("safe");
        when(router.execute(any(), any(), any(), eq(null))).thenThrow(failure);

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello")))
                .isSameAs(failure);
    }

    @Test
    void executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure() {
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = templateWithRegisteredSkill(router);
        IllegalStateException failure = new IllegalStateException("internal provider details");
        when(router.execute(any(), any(), any(), eq(null))).thenAnswer(invocation -> {
            ai.loomspan.internal.core.LoomspanSession session = invocation.getArgument(2);
            session.recordFailure(new IllegalStateException("preceding failure"),
                    Map.of("message", "preceding history"), null);
            throw failure;
        });
        AtomicInteger observerCalls = new AtomicInteger();
        AtomicReference<SkillExecutionView> observed = new AtomicReference<>();

        assertThatThrownBy(() -> template.invoke(
                "invoiceParser", Map.of("payload", "hello"), view -> {
                    observerCalls.incrementAndGet();
                    observed.set(view);
                }))
                .isInstanceOf(SkillException.class)
                .hasMessage("Skill 'invoiceParser' execution failed.")
                .hasCause(failure);
        assertThat(observerCalls).hasValue(1);
        assertThat(observed.get().events()).extracting(event -> event.type())
                .containsExactly("ERROR", "ERROR");
        assertThat(observed.get().events()).extracting(event -> event.details().get("message"))
                .containsExactly("preceding history", "Session execution failed");
    }

    @Test
    void validateUsesInvocationPreparationAndCallingAuthenticationWithoutExecution()
    {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        CapabilityMetadata restricted = new CapabilityMetadata(
                "yaml:invoiceParser", "invoiceParser", "Invoice parser",
                SkillExecutionDescriptor.none(),
                ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of("ALLOWED")),
                noopInvoker(), CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("invoiceParser", "Invoice parser"),
                yamlSkillMetadata().inputContract(), null);
        registry.register("invoiceParser", restricted);
        LoomspanSessionRunner sessionRunner = mock(LoomspanSessionRunner.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry, router,
                sessionRunner,
                new ObjectMapper(), new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);

        assertThatThrownBy(() -> template.validate("invoiceParser", Map.of("payload", "hello")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied for capability 'invoiceParser'");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ALLOWED"))));

        ai.loomspan.internal.core.LoomspanSession existingSession =
                new ai.loomspan.internal.core.LoomspanSession(4, "existing");
        ai.loomspan.internal.core.ExecutionBinding existingBinding =
                ai.loomspan.internal.core.ExecutionBinding.sessionOnly(existingSession);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(existingBinding, () -> {
            template.validate("invoiceParser", Map.of("payload", "hello"));
            template.validate("invoiceParser", new InvoiceRequest("hello"));
            assertThat(ai.loomspan.internal.core.ExecutionBindingScope.requireCurrent()).isSameAs(existingBinding);
        });

        verify(router, never()).execute(any(), any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(sessionRunner);
    }

    @Test
    void validateMatchesInvokeValidationAndObjectConversionFailures()
    {
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = templateWithRegisteredSkill(router);

        Throwable validateInputFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> template.validate("invoiceParser", Map.of()));
        Throwable invokeInputFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> template.invoke("invoiceParser", Map.of()));
        assertThat(validateInputFailure).isInstanceOf(SkillInputValidationException.class);
        assertThat(invokeInputFailure).isInstanceOf(SkillInputValidationException.class);
        assertThat(validateInputFailure.getMessage()).isEqualTo(invokeInputFailure.getMessage());
        assertThat(((SkillInputValidationException) validateInputFailure).getIssues())
                .isEqualTo(((SkillInputValidationException) invokeInputFailure).getIssues());

        Throwable validateConversionFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> template.validate("missing", (Object) "not-an-object"));
        Throwable invokeConversionFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> template.invoke("missing", (Object) "not-an-object"));
        assertThat(validateConversionFailure).isInstanceOf(SkillException.class)
                .hasMessage("Skill 'missing' execution failed.")
                .hasCauseInstanceOf(RuntimeException.class);
        assertThat(invokeConversionFailure).isInstanceOf(SkillException.class)
                .hasMessage(validateConversionFailure.getMessage())
                .hasCauseInstanceOf(validateConversionFailure.getCause().getClass());
        verify(router, never()).execute(any(), any(), any(), any());
    }

    @Test
    void validatePreservesDistinctNullRulesAndUnknownFailure()
    {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        registry.register("generic", new CapabilityMetadata(
                "yaml:generic", "generic", "Generic", SkillExecutionDescriptor.none(),
                ai.loomspan.internal.security.SkillAccessPolicy.unrestricted(), noopInvoker(),
                CapabilityKind.YAML_SKILL, CapabilityToolDescriptor.generic("generic", "Generic"), null));
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry, router, new LoomspanSessionRunner(4), new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);

        template.validate("generic", (Map<String, Object>) null);
        assertThatThrownBy(() -> template.validate("generic", (Object) null))
                .isInstanceOf(SkillInputValidationException.class)
                .hasMessage("Skill input must not be null.");
        assertThatThrownBy(() -> template.validate("missing", Map.of()))
                .isInstanceOf(SkillException.class)
                .hasMessage("Unknown skill 'missing'");
        verify(router, never()).execute(any(), any(), any(), any());
    }

    @Test
    void failureObserverFailureDoesNotMaskExecutionFailure()
    {
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = templateWithRegisteredSkill(router);
        IllegalStateException executionFailure = new IllegalStateException("execution failed");
        IllegalArgumentException observerFailure = new IllegalArgumentException("observer failed");
        when(router.execute(any(), any(), any(), eq(null))).thenThrow(executionFailure);

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello"), view -> {
            throw observerFailure;
        }))
                .isInstanceOf(SkillException.class)
                .hasCause(executionFailure);
        assertThat(executionFailure.getSuppressed()).contains(observerFailure);
    }

    @Test
    void mapperFailureSkipsObserverAndDoesNotMaskExecutionFailure()
    {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        CapabilityMetadata metadata = yamlSkillMetadata();
        registry.register(metadata.name(), metadata);
        IllegalStateException executionFailure = new IllegalStateException("execution failed");
        IllegalArgumentException mapperFailure = new IllegalArgumentException("mapping failed");
        when(router.execute(any(), any(), any(), eq(null))).thenThrow(executionFailure);
        SkillExecutionViewMapper mapper = mock(SkillExecutionViewMapper.class);
        when(mapper.map(any(ai.loomspan.internal.core.LoomspanSession.class))).thenThrow(mapperFailure);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry, router, new LoomspanSessionRunner(4), new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null, mapper);
        AtomicBoolean observerCalled = new AtomicBoolean();

        assertThatThrownBy(() -> template.invoke(metadata.name(), Map.of("payload", "hello"),
                view -> observerCalled.set(true)))
                .isInstanceOf(SkillException.class)
                .hasCause(executionFailure);
        assertThat(observerCalled).isFalse();
        assertThat(executionFailure.getSuppressed()).contains(mapperFailure);
    }

    @Test
    void wrapsSecurityContextLookupFailureWithSafeSkillExceptionAndCause() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        SecurityContextHolderStrategy strategy = mock(SecurityContextHolderStrategy.class);
        IllegalStateException failure = new IllegalStateException("security context unavailable");
        when(strategy.getContext()).thenThrow(failure);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), strategy);
        registry.register("invoiceParser", yamlSkillMetadata());

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello")))
                .isInstanceOf(SkillException.class)
                .hasMessage("Skill 'invoiceParser' execution failed.")
                .hasCause(failure);
        verify(router, never()).execute(any(), any(), any(), any());
    }

    @Test
    void doesNotCatchError() {
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = templateWithRegisteredSkill(router);
        AssertionError failure = new AssertionError("fatal");
        when(router.execute(any(), any(), any(), eq(null))).thenThrow(failure);

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello")))
                .isSameAs(failure);
    }

    @Test
    void rejectsImplementationTargetIdsAsUnknownYamlSkills() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);

        assertThatThrownBy(() -> template.invoke("missingSkill", Map.of()))
                .isInstanceOf(SkillException.class)
                .hasMessageContaining("Unknown skill");
        assertThatThrownBy(() -> template.invoke("javaSkill", Map.of()))
                .isInstanceOf(SkillException.class)
                .hasMessageContaining("Unknown skill");
        assertThatThrownBy(() -> template.invoke("bean#javaSkill", Map.of()))
                .isInstanceOf(SkillException.class)
                .hasMessageContaining("Unknown skill");
    }

    @Test
    void rejectsCustomRegistryMetadataThatDoesNotMatchRequestedYamlName() {
        CapabilityRegistry registry = mock(CapabilityRegistry.class);
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        CapabilityMetadata otherSkill = yamlSkillMetadata();
        when(registry.getCapability("requested.skill")).thenReturn(otherSkill);

        assertThatThrownBy(() -> template.invoke("requested.skill", Map.of("payload", "hello")))
                .isInstanceOf(SkillException.class)
                .hasMessageContaining("invoiceParser")
                .hasMessageContaining("requested.skill");
        verify(router, never()).execute(any(), any(), any(), any());
    }

    @Test
    void skillTemplateNullInputAndObserverLifecycle() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        CapabilityMetadata yamlSkill = new CapabilityMetadata(
                "yaml:invoiceParser",
                "invoiceParser",
                "Invoice parser",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                noopInvoker(),
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("invoiceParser", "Invoice parser"),
                new SkillInputContract(
                        SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                        new SkillInputSchemaNode(
                                "object",
                                Map.of("payload", new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of(), null, null, false)),
                                List.of("payload"),
                                Boolean.FALSE,
                                null,
                                List.of(),
                                null,
                                null,
                                false)),
                null);
        registry.register("invoiceParser", yamlSkill);
        when(router.execute(eq(yamlSkill), eq(Map.of("payload", "hello")), any(), eq(null))).thenReturn("\"ok\"");

        assertThatThrownBy(() -> template.invoke("invoiceParser", (Object) null))
                .isInstanceOf(SkillInputValidationException.class);
        assertThatThrownBy(() -> template.invoke("invoiceParser", (Map<String, Object>) null))
                .isInstanceOf(SkillInputValidationException.class);

        AtomicReference<SkillExecutionView> observed = new AtomicReference<>();
        String result = template.invoke("invoiceParser", Map.of("payload", "hello"), observed::set);

        assertThat(result).isEqualTo("\"ok\"");
        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().sessionId()).isNotBlank();
        assertThat(observed.get().events()).isNotNull();

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of(), observed::set))
                .isInstanceOf(SkillInputValidationException.class);
        verify(router, never()).execute(eq(yamlSkill), eq(Map.of()), any(), eq(null));
    }

    @Test
    void objectOverloadDelegatesThroughValidatedMapPath() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        CapabilityMetadata yamlSkill = yamlSkillMetadata();
        registry.register("invoiceParser", yamlSkill);
        when(router.execute(eq(yamlSkill), eq(Map.of("payload", "hello")), any(), eq(null))).thenReturn("\"ok\"");

        String result = template.invoke("invoiceParser", new InvoiceRequest("hello"));

        assertThat(result).isEqualTo("\"ok\"");
        verify(router).execute(eq(yamlSkill), eq(Map.of("payload", "hello")), any(), eq(null));
    }

    @Test
    void observerExceptionPropagatesAfterExecutionCompletes() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        CapabilityMetadata yamlSkill = yamlSkillMetadata();
        registry.register("invoiceParser", yamlSkill);
        when(router.execute(eq(yamlSkill), eq(Map.of("payload", "hello")), any(), eq(null))).thenReturn("\"ok\"");

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello"), view -> {
            throw new IllegalStateException("observer failed");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observer failed");
        verify(router).execute(eq(yamlSkill), eq(Map.of("payload", "hello")), any(), eq(null));
    }

    @Test
    void invalidInputDoesNotInvokeObserver() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityExecutionRouter router = mock(CapabilityExecutionRouter.class);
        DefaultSkillTemplate template = new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        CapabilityMetadata yamlSkill = yamlSkillMetadata();
        registry.register("invoiceParser", yamlSkill);
        AtomicBoolean observerCalled = new AtomicBoolean(false);

        assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of(), view -> observerCalled.set(true)))
                .isInstanceOf(SkillInputValidationException.class);

        assertThat(observerCalled.get()).isFalse();
        verify(router, never()).execute(eq(yamlSkill), eq(Map.of()), any(), eq(null));
    }

    private CapabilityMetadata yamlSkillMetadata() {
        return new CapabilityMetadata(
                "yaml:invoiceParser",
                "invoiceParser",
                "Invoice parser",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                noopInvoker(),
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("invoiceParser", "Invoice parser"),
                new SkillInputContract(
                        SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                        new SkillInputSchemaNode(
                                "object",
                                Map.of("payload", new SkillInputSchemaNode("string", Map.of(), List.of(), null, null, List.of(), null, null, false)),
                                List.of("payload"),
                                Boolean.FALSE,
                                null,
                                List.of(),
                                null,
                                null,
                                false)),
                null);
    }

    private DefaultSkillTemplate templateWithRegisteredSkill(CapabilityExecutionRouter router) {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        registry.register("invoiceParser", yamlSkillMetadata());
        return new DefaultSkillTemplate(
                registry,
                router,
                new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.ALWAYS, fixedClock()),
                new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
    }

    private record InvoiceRequest(String payload) {
    }

    private CapabilityInvoker noopInvoker() {
        return arguments -> "\"ok\"";
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-03-30T12:00:00Z"), ZoneOffset.UTC);
    }
}
