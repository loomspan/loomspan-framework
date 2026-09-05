package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.vfs.RefResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityExecutionRouterTest {

    @Test
    void routesNestedYamlSynchronouslyUsingCurrentParentBinding() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = new LoomspanSession("session-1", "test.entry", 2);
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("childLlmSkill", "child"),
                null);
        when(coordinator.execute(eq("childLlmSkill"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), eq(session), eq(null)))
                .thenReturn("child result");

        MissionContext parent = new MissionContext(session, "test.entry", "parent-frame", null);
        Object result = ExecutionBindingScope.supplyWith(
                new ExecutionBinding(session, parent, new PhysicalBranchContext(session)),
                () -> router.execute(capability, Map.of("topic", "mars"), session, null));

        assertThat(result).isEqualTo("child result");
    }

    @Test
    void deniesProtectedCapabilityWithoutMatchingAuthority() {
        RefResolver refResolver = mock(RefResolver.class);
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                new StaticListableBeanFactory().getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = new LoomspanSession("session-1", "test.entry", 2);
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of("ALLOWED")),
                arguments -> "unused", CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic("childLlmSkill", "child"), null);

        assertThatThrownBy(() -> router.execute(
                capability,
                Map.of("topic", "mars"),
                session,
                UsernamePasswordAuthenticationToken.authenticated(
                        "user",
                        "pw",
                        AuthorityUtils.createAuthorityList("ROLE_OTHER"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("childLlmSkill");
    }

    @Test
    void authorizesNestedYamlDelegationUsingSessionFallback() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = new LoomspanSession("session-1", "test.entry", 2);
        session.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user",
                "pw",
                AuthorityUtils.createAuthorityList("ROLE_ALLOWED")));
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of("ALLOWED")),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("childLlmSkill", "child"),
                null);
        when(coordinator.execute(eq("childLlmSkill"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), eq(session), eq(null)))
                .thenReturn("child result");

        MissionContext parent = new MissionContext(session, "test.entry", "parent-frame", null);
        Object result = ExecutionBindingScope.supplyWith(
                new ExecutionBinding(session, parent, new PhysicalBranchContext(session)),
                () -> router.execute(capability, Map.of("topic", "mars"), session, null));

        assertThat(result).isEqualTo("child result");
        verify(refResolver, never()).resolveArguments(
                org.mockito.ArgumentMatchers.any(), eq(session), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nestedYamlDelegationStartsWithFreshEvidenceAndRestoresParentEvidenceAfterward() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionStateService stateService = new ai.loomspan.internal.runtime.state.DefaultExecutionStateService(
                java.time.Clock.fixed(java.time.Instant.parse("2026-03-15T12:00:00Z"), java.time.ZoneOffset.UTC));
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("childLlmSkill", "child"),
                null);
        CapabilityRegistry capabilityRegistry = mock(CapabilityRegistry.class);
        when(capabilityRegistry.getCapability("childLlmSkill")).thenReturn(capability);

        ai.loomspan.internal.skill.YamlSkillManifest manifest = new ai.loomspan.internal.skill.YamlSkillManifest();
        manifest.setName("childLlmSkill");
        manifest.setDescription("childLlmSkill");
        manifest.setModel("gpt-5");
        ai.loomspan.internal.skill.YamlSkillDefinition definition = new ai.loomspan.internal.skill.YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium"));
        ai.loomspan.internal.skill.YamlSkillCatalog catalog = mock(ai.loomspan.internal.skill.YamlSkillCatalog.class);
        when(catalog.getSkill("childLlmSkill")).thenReturn(definition);

        ai.loomspan.internal.model.ModelInteractionFactory chatClientFactory =
                (ignored, mode) -> request -> ai.loomspan.internal.model.ModelInteractionResult.content("unused");

        ai.loomspan.internal.runtime.MissionExecutionEngine engine = (session, skillDefinition, objective, missionInput, chatClient, visibleTools, planningEnabled, authentication) -> {
            MissionContext child = ExecutionBindingScope.requireCurrent().requireMission();
            assertThat(child.successfulDirectSkills()).isEmpty();
            child.recordSuccessfulDirectSkill("expense_match_search");
            return "child result";
        };
        ExecutionCoordinator coordinator = new ExecutionCoordinator(
                catalog,
                capabilityRegistry,
                chatClientFactory,
                (skillName, session, authentication) -> java.util.List.of(),
                (session, skillDefinition, capabilities, authentication) -> java.util.List.of(),
                engine,
                engine,
                stateService,
                new DefaultAccessGuard(),
                (value, session) -> value,
                new ai.loomspan.internal.security.ScopedAuthentication(null),
                new ai.loomspan.internal.runtime.MissionWorkExecutor(stateService, java.time.Duration.ofSeconds(5), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = new LoomspanSession("session-1", "test.entry", 2);
        MissionContext parent = new MissionContext(session, "test.entry", "parent-frame", null);
        parent.recordSuccessfulDirectSkill("parsed_invoice");
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        Object result = ExecutionBindingScope.supplyWith(new ExecutionBinding(session, parent, branch), () -> {
            ExecutionFrame parentFrame = stateService.openMissionFrame(session, "parent.visible.skill", Map.of("objective", "parent"));
            try {
                return router.execute(capability, Map.of("topic", "mars"), session, null);
            }
            finally {
                stateService.closeMissionFrame(session, parentFrame);
            }
        });

        assertThat(result).isEqualTo("child result");
        assertThat(parent.successfulDirectSkills()).containsExactly("parsed_invoice");
    }

    @Test
    void nestedYamlDelegationPassesCanonicalMissionInputWithoutSerializingItIntoObjective() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = new LoomspanSession("session-1", "test.entry", 2);
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("childLlmSkill", "child"),
                new SkillInputContractResolver().resolveFromToolSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "invoiceId": { "type": "string" }
                          },
                          "required": ["invoiceId"],
                          "additionalProperties": false
                        }
                        """),
                null);
        when(coordinator.execute(eq("childLlmSkill"), eq("Execute skill 'childLlmSkill' using the provided mission input object."),
                eq(Map.of("invoiceId", "INV-7")), eq(session), eq(null)))
                .thenReturn("child result");

        MissionContext parent = new MissionContext(session, "test.entry", "parent-frame", null);
        Object result = ExecutionBindingScope.supplyWith(
                new ExecutionBinding(session, parent, new PhysicalBranchContext(session)),
                () -> router.execute(capability, Map.of("invoiceId", "INV-7"), session, null));

        assertThat(result).isEqualTo("child result");
        verify(refResolver, never()).resolveArguments(
                org.mockito.ArgumentMatchers.any(), eq(session), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void javaCapabilityAcceptsDirectRefBackedObjectsThroughRootCoordinator() {
        RefResolver refResolver = mock(RefResolver.class);
        LoomspanSession session = new LoomspanSession("session-1", "binaryTool", 2);
        ByteArrayResource payload = new ByteArrayResource(new byte[]{1, 2, 3});
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:binaryTool",
                "binaryTool",
                "binary tool",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> { assertThat(arguments.get("payload")).isSameAs(payload); return "binary-result"; }, CapabilityKind.JAVA_SKILL,
                new CapabilityToolDescriptor("binaryTool", "binary tool", """
                        {
                          "type": "object",
                          "properties": {
                            "payload": {
                              "type": "string",
                              "description": "Provide a ref:// URI for binary content or an inline string value when appropriate.",
                              "x-loomspan-runtime-ref-capable": true
                            }
                          },
                          "required": ["payload"],
                          "additionalProperties": false
                        }
                        """),
                new SkillInputContractResolver().resolveFromToolSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "payload": {
                              "type": "string",
                              "description": "Provide a ref:// URI for binary content or an inline string value when appropriate.",
                              "x-loomspan-runtime-ref-capable": true
                            }
                          },
                          "required": ["payload"],
                          "additionalProperties": false
                        }
                        """), null);

        when(refResolver.resolveArguments(any(), eq(session), eq(capability.inputContract())))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CapabilityExecutionRouter router = javaRouter(capability, refResolver);
        Object result = ExecutionBindingScope.supplyWith(ExecutionBinding.sessionOnly(session),
                () -> router.execute(capability, Map.of("payload", payload), session, null));

        assertThat(result).isEqualTo("binary-result");
        verify(refResolver).resolveArguments(any(), eq(session), eq(capability.inputContract()));
    }

    @Test
    void javaCapabilityPreservesRefStringsInsideUnconstrainedValues() {
        RefResolver refResolver = (value, ignoredSession) -> {
            throw new AssertionError("Unconstrained strings must not be resolved as VFS references");
        };
        LoomspanSession session = new LoomspanSession("session-1", "genericMapTarget", 2);
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> received = new java.util.concurrent.atomic.AtomicReference<>();
        SkillInputContractResolver contractResolver = new SkillInputContractResolver();
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": {},
                    "options": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": {}
                      }
                    }
                  },
                  "required": ["value", "options"],
                  "additionalProperties": false
                }
                """;
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:genericMapTarget",
                "genericMapTarget",
                "generic map target",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> { received.set(arguments); return "unchanged"; }, CapabilityKind.JAVA_SKILL,
                new CapabilityToolDescriptor("genericMapTarget", "generic map target", schema),
                contractResolver.resolveFromToolSchema(schema), null);
        Map<String, Object> input = Map.of(
                "value", "ref://artifacts/missing-value.txt",
                "options", List.of(Map.of("identifier", "ref://artifacts/missing-option.txt")));

        CapabilityExecutionRouter router = javaRouter(capability, refResolver);
        Object result = ExecutionBindingScope.supplyWith(ExecutionBinding.sessionOnly(session),
                () -> router.execute(capability, input, session, null));

        assertThat(result).isEqualTo("unchanged");
        assertThat(received.get()).isEqualTo(input);
    }

    private static CapabilityExecutionRouter javaRouter(CapabilityMetadata capability, RefResolver refResolver)
    {
        CapabilityRegistry registry = mock(CapabilityRegistry.class);
        when(registry.getCapability(capability.name())).thenReturn(capability);
        var coordinator = new ExecutionCoordinator(
                mock(ai.loomspan.internal.skill.YamlSkillCatalog.class),
                registry,
                (definition, mode) -> { throw new AssertionError("Java must not create a model interaction"); },
                (name, session, authentication) -> List.of(),
                (session, definition, capabilities, authentication) -> List.of(),
                (session, definition, objective, input, model, tools, planning, authentication) -> { throw new AssertionError("No Java model engine"); },
                (session, definition, objective, input, model, tools, planning, authentication) -> { throw new AssertionError("No Java planning engine"); },
                new ai.loomspan.internal.runtime.state.DefaultExecutionStateService(java.time.Clock.systemUTC()),
                new DefaultAccessGuard(),
                refResolver,
                new ai.loomspan.internal.security.ScopedAuthentication(null),
                new ai.loomspan.internal.runtime.MissionWorkExecutor(new ai.loomspan.internal.runtime.state.DefaultExecutionStateService(java.time.Clock.systemUTC()), java.time.Duration.ofSeconds(5), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
        return new CapabilityExecutionRouter(
                new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator)).getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
    }
}
