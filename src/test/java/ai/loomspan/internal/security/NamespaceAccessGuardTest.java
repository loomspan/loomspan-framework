package ai.loomspan.internal.security;

import ai.loomspan.api.SkillKind;
import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.skill.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamespaceAccessGuardTest {
    @Test void declaredPublicTargetStillRequiresRolesAndRetainedOwnership() {
        var source = capability("orders-root");
        var target = new CapabilityMetadata("tax", "tax-calculateTax", "tax", SkillExecutionDescriptor.none(),
                SkillAccessPolicy.yamlRoles(Set.of("TAX")), args -> "ok", SkillKind.YAML,
                CapabilityToolDescriptor.generic("tax-calculateTax", "tax"), null);
        var orders = generation("orders", NamespaceVisibility.PRIVATE, source, List.of(target.name()));
        var tax = generation("tax", NamespaceVisibility.PUBLIC, target, List.of());
        var session = TestLoomspanSessions.withId("roles", source.name(), 8);
        var snapshot = new SkillAdmissionSnapshot(source.identity(), Map.of("orders", orders, "tax", tax));
        var parent = new ExecutionBinding(session, new MissionContext(session, source.name(), "root", null),
                new PhysicalBranchContext(session), orders, snapshot);
        var guard = new DefaultAccessGuard();
        var caller = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "caller", "", org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_TAX"));
        ExecutionBindingScope.runWith(parent, () -> {
            assertThat(guard.canAccess(target, session, null)).isFalse();
            assertThat(guard.canAccess(target, session, caller)).isTrue();
            assertThat(guard.canAccess(capability(target.name()), session, caller)).isFalse();
            var discovery = new DefaultSkillVisibilityResolver(guard);
            assertThat(discovery.visibleSkillsFor(source.name(), session, caller)).containsExactly(target);
            var child = parent.withTarget(target, new MissionContext(session, target.name(), "child", parent.mission()));
            ExecutionBindingScope.runWith(child, () -> {
                assertThat(child.session()).isSameAs(parent.session());
                assertThat(child.snapshot()).isSameAs(snapshot);
                assertThat(child.entryGeneration()).isSameAs(orders);
                assertThat(child.generation()).isSameAs(tax);
                assertThat(guard.canAccess(source, session, caller)).isFalse();
            });
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
        });
    }

    @Test void retainedMembershipDoesNotAuthorizeUndeclaredCallsAndPrivateRootIsAllowed() {
        var source = capability("orders-root");
        var target = capability("tax-leaf");
        var orders = generation("orders", NamespaceVisibility.PRIVATE, source, List.of());
        var tax = generation("tax", NamespaceVisibility.PUBLIC, target, List.of());
        var session = TestLoomspanSessions.withId("undeclared", source.name(), 8);
        var snapshot = new SkillAdmissionSnapshot(source.identity(), Map.of("orders", orders, "tax", tax));
        var root = ExecutionBinding.sessionOnly(session, snapshot);
        ExecutionBindingScope.runWith(root, () -> {
            var guard = new DefaultAccessGuard();
            assertThat(guard.canAccess(source, session, null)).isTrue();
            assertThat(guard.canAccess(target, session, null)).isFalse();
            ExecutionBindingScope.runWith(root.withMission(new MissionContext(session, source.name(), "root", null)), () -> {
                assertThat(guard.canAccess(target, session, null)).isFalse();
                assertThat(new DefaultSkillVisibilityResolver(guard).visibleSkillsFor(source.name(), session, null)).isEmpty();
            });
        });
    }

    @Test void sameNamespacePrivateCallIsAllowedAndForeignGenerationCannotBeInstalled() {
        var source = capability("orders-root"); var target = capability("orders-private");
        var definition = generation("orders", NamespaceVisibility.PRIVATE, source, List.of(target.name())).definition(source.name());
        var generation = new SkillGeneration("orders", "captured", Map.of(source.name(), source, target.name(), target),
                Map.of(source.name(), definition), null,
                new ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog(List.of(), Map.of()));
        var session = TestLoomspanSessions.withId("same", source.name(), 8);
        var snapshot = new SkillAdmissionSnapshot(source.identity(), Map.of("orders", generation));
        var binding = ExecutionBinding.sessionOnly(session, snapshot).withMission(new MissionContext(session, source.name(), "root", null));
        ExecutionBindingScope.runWith(binding, () -> assertThat(new DefaultAccessGuard().canAccess(target, session, null)).isTrue());
        assertThatThrownBy(() -> new ExecutionBinding(session, null, binding.branch(),
                new SkillGeneration("orders", "other", Map.of(), Map.of()), snapshot)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void deniesPrivateCallbackFromActualCurrentNamespace() {
        var target = capability("orders-private");
        var bridge = capability("pricing-bridge");
        var orders = generation("orders", NamespaceVisibility.PRIVATE, target, List.of());
        var pricing = generation("pricing", NamespaceVisibility.PUBLIC, bridge, List.of(target.name()));
        var snapshot = new SkillAdmissionSnapshot(target.identity(), Map.of("orders", orders, "pricing", pricing));
        var session = TestLoomspanSessions.withId("callback", target.name(), 8);
        var mission = new MissionContext(session, bridge.name(), "bridge", null);
        var binding = new ExecutionBinding(session, mission, new PhysicalBranchContext(session), pricing, snapshot);
        ExecutionBindingScope.runWith(binding, () -> {
            var guard = new DefaultAccessGuard();
            assertThat(guard.canAccess(target, session, null)).isFalse();
            assertThatThrownBy(() -> guard.checkAccess(target, session, null))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        });
    }

    static CapabilityMetadata capability(String name) {
        return new CapabilityMetadata(name, name, name, SkillExecutionDescriptor.none(),
                SkillAccessPolicy.unrestricted(), args -> "ok", SkillKind.YAML,
                CapabilityToolDescriptor.generic(name, name), null);
    }
    static SkillGeneration generation(String namespace, NamespaceVisibility visibility, CapabilityMetadata capability, List<String> children) {
        var definition = mock(YamlSkillDefinition.class);
        var manifest = new YamlSkillManifest(); manifest.setName(capability.identity().localName());
        when(definition.identity()).thenReturn(capability.identity());
        when(definition.manifest()).thenReturn(manifest);
        when(definition.allowedSkills()).thenReturn(children);
        return new SkillGeneration(namespace, UUID.randomUUID().toString(), Map.of(capability.name(), capability),
                Map.of(capability.name(), definition), null,
                new ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog(List.of(), Map.of()), null, visibility);
    }
}
