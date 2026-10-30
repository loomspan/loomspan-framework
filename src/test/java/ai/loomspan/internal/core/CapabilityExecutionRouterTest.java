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
    void assembledBoundValuesFailBeforeDispatchWithoutCoercionAndStillRequireAuthorization() {
        var coordinator = mock(ExecutionCoordinator.class);
        var router = new CapabilityExecutionRouter(new StaticListableBeanFactory(java.util.Map.of("executionCoordinator", coordinator))
                .getBeanProvider(ExecutionCoordinator.class), new DefaultAccessGuard());
        var session = ai.loomspan.internal.core.TestLoomspanSessions.create("bound-dispatch", "default-bound", 3);
        var contract = new SkillInputContractResolver().resolveJavaCapability("""
                {"type":"object","properties":{"count":{"type":"integer"}},"required":["count"],"additionalProperties":false}
                """);
        var metadata = new CapabilityMetadata("java:bound", "default-bound", "bound", SkillExecutionDescriptor.none(),
                ai.loomspan.internal.security.SkillAccessPolicy.unrestricted(), arguments -> "unused", CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic("default-bound", "bound"), contract, null);
        var binding = new ai.loomspan.internal.runtime.input.ChildInputBinding(
                ai.loomspan.internal.runtime.input.ObjectFieldPath.parse("/count", false),
                ai.loomspan.internal.runtime.input.ChildInputBinding.SourceKind.INPUT,
                ai.loomspan.internal.runtime.input.ObjectFieldPath.parse("/source", true), null);
        var assembled = new ai.loomspan.internal.runtime.input.ChildInputBindingAssembler().assemble(Map.of(), List.of(binding),
                Map.of("source", "123"), List.of(), "default-parent");
        assertThatThrownBy(() -> TestExecutionBindings.callWithGeneration(session,
                ai.loomspan.testkit.TestSkillGenerations.of(metadata),
                () -> router.executeAssembled(metadata, assembled.arguments(), session, null, assembled.exactPaths())))
                .isInstanceOf(ai.loomspan.api.SkillInputValidationException.class).hasMessageContaining("binding_assembled_input_invalid");
        var denied = new CapabilityMetadata("java:bound-denied", "default-bound_denied", "bound", SkillExecutionDescriptor.none(),
                ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of("ALLOWED")), arguments -> "unused",
                CapabilityKind.JAVA_SKILL, CapabilityToolDescriptor.generic("default-bound_denied", "bound"), contract, null);
        var deniedSession = ai.loomspan.internal.core.TestLoomspanSessions.create("bound-denied", "default-bound_denied", 3);
        assertThatThrownBy(() -> TestExecutionBindings.callWithGeneration(deniedSession,
                ai.loomspan.testkit.TestSkillGenerations.of(denied),
                () -> router.executeAssembled(denied, Map.of("count", 123), deniedSession,
                UsernamePasswordAuthenticationToken.authenticated("user", "pw", AuthorityUtils.createAuthorityList("ROLE_OTHER")),
                assembled.exactPaths()))).isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(coordinator);
    }

    @Test
    void routesNestedYamlSynchronouslyUsingCurrentParentBinding() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-test_entry", 2);
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "default-childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("default-childLlmSkill", "child"),
                null);
        when(coordinator.execute(eq(capability), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), eq(session), eq(null)))
                .thenReturn("child result");

        MissionContext parent = new MissionContext(session, "default-test_entry", "parent-frame", null);
        Object result = ExecutionBindingScope.supplyWith(
                ai.loomspan.testkit.TestSkillGenerations.binding(session, parent, new PhysicalBranchContext(session), generation(capability)),
                () -> router.execute(capability, Map.of("topic", "mars"), session, null));

        assertThat(result).isEqualTo("child result");
    }

    @Test
    void deniesProtectedCapabilityWithoutMatchingAuthority() {
        RefResolver refResolver = mock(RefResolver.class);
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                new StaticListableBeanFactory().getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-childLlmSkill", 2);
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "default-childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of("ALLOWED")),
                arguments -> "unused", CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic("default-childLlmSkill", "child"), null);

        assertThatThrownBy(() -> TestExecutionBindings.callWithGeneration(session, generation(capability), () -> router.execute(
                capability,
                Map.of("topic", "mars"),
                session,
                UsernamePasswordAuthenticationToken.authenticated(
                        "user",
                        "pw",
                        AuthorityUtils.createAuthorityList("ROLE_OTHER")))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("default-childLlmSkill");
    }

    @Test
    void authorizesNestedYamlDelegationUsingSessionFallback() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-test_entry", 2);
        session.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user",
                "pw",
                AuthorityUtils.createAuthorityList("ROLE_ALLOWED")));
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "default-childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of("ALLOWED")),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("default-childLlmSkill", "child"),
                null);
        when(coordinator.execute(eq(capability), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap(), eq(session), eq(null)))
                .thenReturn("child result");

        MissionContext parent = new MissionContext(session, "default-test_entry", "parent-frame", null);
        Object result = ExecutionBindingScope.supplyWith(
                ai.loomspan.testkit.TestSkillGenerations.binding(session, parent, new PhysicalBranchContext(session), generation(capability)),
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
                "default-childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("default-childLlmSkill", "child"),
                null);
        TestCapabilityRegistry capabilityRegistry = mock(TestCapabilityRegistry.class);
        when(capabilityRegistry.getCapability("default-childLlmSkill")).thenReturn(capability);

        ai.loomspan.internal.skill.YamlSkillManifest manifest = new ai.loomspan.internal.skill.YamlSkillManifest();
        manifest.setName("childLlmSkill");
        manifest.setDescription("default-childLlmSkill");
        manifest.setModel("gpt-5");
        ai.loomspan.internal.skill.YamlSkillDefinition definition = new ai.loomspan.internal.skill.YamlSkillDefinition("default",
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium"));
        ai.loomspan.internal.skill.YamlSkillCatalog catalog = mock(ai.loomspan.internal.skill.YamlSkillCatalog.class);
        when(catalog.getSkill("default-childLlmSkill")).thenReturn(definition);

        ai.loomspan.internal.model.ModelInteractionFactory chatClientFactory =
                (ignored, mode) -> request -> ai.loomspan.internal.model.ModelInteractionResult.content("unused");

        ai.loomspan.internal.runtime.MissionExecutionEngine engine = (session, skillDefinition, objective, missionInput, chatClient, visibleTools, planningEnabled, authentication) -> {
            MissionContext child = ExecutionBindingScope.requireCurrent().requireMission();
            assertThat(child.successfulDirectSkills()).isEmpty();
            child.recordSuccessfulDirectSkill("default-expense_match_search");
            return "child result";
        };
        ExecutionCoordinator coordinator = new ExecutionCoordinator(
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
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-test_entry", 2);
        MissionContext parent = new MissionContext(session, "default-test_entry", "parent-frame", null);
        parent.recordSuccessfulDirectSkill("default-parsed_invoice");
        PhysicalBranchContext branch = new PhysicalBranchContext(session);
        Object result = ExecutionBindingScope.supplyWith(ai.loomspan.testkit.TestSkillGenerations.binding(session, parent, branch, generation(capability)), () -> {
            ExecutionFrame parentFrame = stateService.openMissionFrame(session, "default-test_entry", Map.of("objective", "default-parent"));
            try {
                return router.execute(capability, Map.of("topic", "mars"), session, null);
            }
            finally {
                stateService.closeMissionFrame(session, parentFrame);
            }
        });

        assertThat(result).isEqualTo("child result");
        assertThat(parent.successfulDirectSkills()).containsExactly("default-parsed_invoice");
    }

    @Test
    void nestedYamlDelegationPassesCanonicalMissionInputWithoutSerializingItIntoObjective() {
        RefResolver refResolver = mock(RefResolver.class);
        ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("executionCoordinator", coordinator));
        CapabilityExecutionRouter router = new CapabilityExecutionRouter(
                beanFactory.getBeanProvider(ExecutionCoordinator.class),
                new DefaultAccessGuard());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-test_entry", 2);
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:child",
                "default-childLlmSkill",
                "child",
                SkillExecutionDescriptor.from(new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                        "gpt-5",
                        "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                        "openai/gpt-5",
                        "medium")), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> "unused",
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("default-childLlmSkill", "child"),
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
        when(coordinator.execute(eq(capability), eq("Fulfill the mission for skill 'default-childLlmSkill' using the provided mission input object."),
                eq(Map.of("invoiceId", "INV-7")), eq(session), eq(null)))
                .thenReturn("child result");

        MissionContext parent = new MissionContext(session, "default-test_entry", "parent-frame", null);
        Object result = ExecutionBindingScope.supplyWith(
                ai.loomspan.testkit.TestSkillGenerations.binding(session, parent, new PhysicalBranchContext(session), generation(capability)),
                () -> router.execute(capability, Map.of("invoiceId", "INV-7"), session, null));

        assertThat(result).isEqualTo("child result");
        verify(refResolver, never()).resolveArguments(
                org.mockito.ArgumentMatchers.any(), eq(session), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void javaCapabilityAcceptsDirectRefBackedObjectsThroughRootCoordinator() {
        RefResolver refResolver = mock(RefResolver.class);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-binaryTool", 2);
        ByteArrayResource payload = new ByteArrayResource(new byte[]{1, 2, 3});
        CapabilityMetadata capability = new CapabilityMetadata(
                "yaml:binaryTool",
                "default-binaryTool",
                "binary tool",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> { assertThat(arguments.get("payload")).isSameAs(payload); return "binary-result"; }, CapabilityKind.JAVA_SKILL,
                new CapabilityToolDescriptor("default-binaryTool", "binary tool", """
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
                        """, null),
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
        Object result = TestExecutionBindings.callWithGeneration(session, generation(capability),
                () -> router.execute(capability, Map.of("payload", payload), session, null));

        assertThat(result).isEqualTo("binary-result");
        verify(refResolver).resolveArguments(any(), eq(session), eq(capability.inputContract()));
    }

    @Test
    void javaCapabilityPreservesRefStringsInsideUnconstrainedValues() {
        RefResolver refResolver = (value, ignoredSession) -> {
            throw new AssertionError("Unconstrained strings must not be resolved as VFS references");
        };
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.create("session-1", "default-genericMapTarget", 2);
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
                "default-genericMapTarget",
                "generic map target",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(java.util.Set.of()),
                arguments -> { received.set(arguments); return "unchanged"; }, CapabilityKind.JAVA_SKILL,
                new CapabilityToolDescriptor("default-genericMapTarget", "generic map target", schema, null),
                contractResolver.resolveFromToolSchema(schema), null);
        Map<String, Object> input = Map.of(
                "value", "ref://artifacts/missing-value.txt",
                "options", List.of(Map.of("identifier", "ref://artifacts/missing-option.txt")));

        CapabilityExecutionRouter router = javaRouter(capability, refResolver);
        Object result = TestExecutionBindings.callWithGeneration(session, generation(capability),
                () -> router.execute(capability, input, session, null));

        assertThat(result).isEqualTo("unchanged");
        assertThat(received.get()).isEqualTo(input);
    }

    private static CapabilityExecutionRouter javaRouter(CapabilityMetadata capability, RefResolver refResolver)
    {
        TestCapabilityRegistry registry = mock(TestCapabilityRegistry.class);
        when(registry.getCapability(capability.name())).thenReturn(capability);
        var coordinator = new ExecutionCoordinator(
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

    private static ai.loomspan.internal.skill.SkillGeneration generation(CapabilityMetadata capability)
    {
        var configuration = new ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration(
                "gpt-5", "test-connection", ai.loomspan.autoconfigure.AiDriver.OPENAI,
                "openai/gpt-5", "medium");
        var capabilities = new java.util.LinkedHashMap<String, CapabilityMetadata>();
        var definitions = new java.util.LinkedHashMap<String, ai.loomspan.internal.skill.YamlSkillDefinition>();
        capabilities.put(capability.name(), capability);
        if (capability.kind() == CapabilityKind.YAML_SKILL) {
            var manifest = new ai.loomspan.internal.skill.YamlSkillManifest();
            manifest.setName(capability.identity().localName());
            manifest.setDescription(capability.description());
            manifest.setModel("gpt-5");
            definitions.put(capability.name(), new ai.loomspan.internal.skill.YamlSkillDefinition("default",
                    new ByteArrayResource(new byte[0]), manifest, configuration));
        }
        // These nested-call fixtures have an explicit model-backed source with exactly this child.
        var parent = new ai.loomspan.internal.skill.YamlSkillManifest();
        parent.setName("test_entry"); parent.setDescription("Parent fixture"); parent.setModel("gpt-5");
        parent.setAllowedSkills(List.of(new ai.loomspan.internal.skill.YamlSkillManifest.AllowedSkillManifest(
                capability.name(), null, null, null)));
        var source = new ai.loomspan.internal.skill.YamlSkillDefinition("default",
                new ByteArrayResource(new byte[0]), parent, configuration);
        capabilities.put(source.identity().toString(), new CapabilityMetadata("yaml:parent", source.identity().toString(), "Parent fixture",
                SkillExecutionDescriptor.from(configuration), ai.loomspan.internal.security.SkillAccessPolicy.unrestricted(),
                input -> "unused", CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic(source.identity().toString(), "Parent fixture"), null));
        definitions.put(source.identity().toString(), source);
        return ai.loomspan.testkit.TestSkillGenerations.of(capabilities, definitions);
    }
}
