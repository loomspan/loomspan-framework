package ai.loomspan.internal.skillapi;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.ExecutionCoordinator;
import ai.loomspan.internal.core.FrameworkExecutionLifecycle;
import ai.loomspan.internal.core.LoomspanSessionRunner;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.TestCapabilityRegistry;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputSchemaNode;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.trace.ImmediateCompletionRetention;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.security.SkillAccessPolicy;
import ai.loomspan.internal.security.SkillRoleEvaluator;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.testkit.TestSkillGenerations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGenerationExecutionIntegrationTest
{
    @Test
    @SuppressWarnings("unchecked")
    void capturePrecedesObjectConversionAndInputValidation()
    {
        CapabilityMetadata oldCapability = capability(
                "old", SkillAccessPolicy.unrestricted(), "payload");
        CapabilityMetadata replacementCapability = capability(
                "replacement", SkillAccessPolicy.unrestricted(), "replacementPayload");
        TestCapabilityRegistry registry = new TestCapabilityRegistry();
        registry.register(oldCapability.name(), oldCapability);
        var generations = registry.manager();
        var replacementGeneration = TestSkillGenerations.of(replacementCapability);
        ObjectMapper mapper = new ObjectMapper()
        {
            @Override
            public <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef)
            {
                generations.activate(replacementGeneration);
                return (T) Map.of("payload", "hello");
            }
        };
        var template = new DefaultSkillTemplate(generations, mock(CapabilityExecutionRouter.class),
                mock(LoomspanSessionRunner.class), mapper, new SkillInputValidator(),
                new SkillRoleEvaluator(null, null), null);

        template.validate("invoiceParser", new Object());
        assertThatThrownBy(() -> template.validate("invoiceParser", Map.of("payload", "hello")))
                .isInstanceOf(ai.loomspan.api.SkillInputValidationException.class);
    }

    @Test
    void capturedHandoffUsesOneGenerationAfterActivationAndNewRootUsesReplacement()
    {
        CapabilityMetadata oldCapability = capability("old", SkillAccessPolicy.unrestricted());
        CapabilityMetadata replacementCapability = capability("replacement", SkillAccessPolicy.denied());
        TestCapabilityRegistry registry = new TestCapabilityRegistry();
        registry.register(oldCapability.name(), oldCapability);
        var generations = registry.manager();
        var oldGeneration = generations.active();
        var replacementGeneration = TestSkillGenerations.of(replacementCapability);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        when(coordinator.execute(eq(oldCapability), any(), any(), any(), eq(null))).thenAnswer(invocation -> {
            assertThat(ExecutionBindingScope.requireCurrent().generation()).isSameAs(oldGeneration);
            return "old-result";
        });
        var provider = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator))
                .getBeanProvider(ExecutionCoordinator.class);
        var router = new CapabilityExecutionRouter(provider, new DefaultAccessGuard());
        var context = new StaticApplicationContext();
        var executor = Executors.newSingleThreadExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(2), executor);
        var codecs = LoomspanJacksonCodecs.defaults();
        var runner = new LoomspanSessionRunner(4, ai.loomspan.internal.core.TracePersistencePolicy.NEVER,
                Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC),
                NoOpExecutionObservationHandleFactory.INSTANCE, ImmediateCompletionRetention.INSTANCE,
                new LoomspanProperties.Session.Quotas(), codecs.canonicalTrace(), lifecycle);
        var template = new DefaultSkillTemplate(generations, router, runner, new ObjectMapper(),
                new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        var admitted = new DefaultSkillInvocationHandoff(template, lifecycle)
                .handoff("invoiceParser", Map.of("payload", "hello"));

        generations.activate(replacementGeneration);

        try
        {
            assertThat(admitted.invoke()).isEqualTo("old-result");
            assertThatThrownBy(() -> template.invoke("invoiceParser", Map.of("payload", "hello")))
                    .isInstanceOf(AccessDeniedException.class);
            verify(coordinator).execute(eq(oldCapability), any(), any(), any(), eq(null));
            verify(coordinator, never()).execute(eq(replacementCapability), any(), any(), any(), eq(null));
        }
        finally
        {
            lifecycle.destroy();
        }
    }

    private static CapabilityMetadata capability(String id, SkillAccessPolicy policy)
    {
        return capability(id, policy, "payload");
    }

    private static CapabilityMetadata capability(String id, SkillAccessPolicy policy, String requiredField)
    {
        SkillInputContract input = new SkillInputContract(
                SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new SkillInputSchemaNode("object",
                        Map.of(requiredField, new SkillInputSchemaNode("string", Map.of(), List.of(), null,
                                null, List.of(), null, null, false)),
                        List.of(requiredField), Boolean.FALSE, null, List.of(), null, null, false));
        return new CapabilityMetadata(id, "invoiceParser", "Invoice parser",
                SkillExecutionDescriptor.none(), policy, arguments -> "unused", CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic("invoiceParser", "Invoice parser"), input, null);
    }
}
