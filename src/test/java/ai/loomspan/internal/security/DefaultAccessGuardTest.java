package ai.loomspan.internal.security;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.api.SkillKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAccessGuardTest {

    private final DefaultAccessGuard accessGuard = new DefaultAccessGuard();

    @Test
    void allowsUnprotectedCapabilityWithoutAuthentication() {
        CapabilityMetadata capability = capability("default-publicSkill", Set.of());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", capability.name(), 2);

        assertThat(canAccess(capability, session, null)).isTrue();
        checkAccess(capability, session, null);
    }

    @Test
    void deniesProtectedCapabilityWithoutInvocationOrSessionAuthentication() {
        CapabilityMetadata capability = capability("default-protectedSkill", Set.of("ALLOWED"));
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", capability.name(), 2);

        assertThat(canAccess(capability, session, null)).isFalse();
        assertThatThrownBy(() -> checkAccess(capability, session, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("default-protectedSkill");
    }

    @Test
    void usesSessionAuthenticationWhenInvocationAuthenticationIsNull() {
        CapabilityMetadata capability = capability("default-protectedSkill", Set.of("ALLOWED"));
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", capability.name(), 2);
        session.setAuthentication(authentication("ROLE_ALLOWED"));

        assertThat(accessGuard.resolveAuthentication(null, session)).isEqualTo(session.getAuthentication().orElseThrow());
        assertThat(canAccess(capability, session, null)).isTrue();
    }

    @Test
    void prefersInvocationAuthenticationOverSessionAuthentication() {
        CapabilityMetadata capability = capability("default-protectedSkill", Set.of("ALLOWED"));
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", capability.name(), 2);
        session.setAuthentication(authentication("ROLE_ALLOWED"));
        Authentication invocationAuthentication = authentication("ROLE_OTHER");

        assertThat(accessGuard.resolveAuthentication(invocationAuthentication, session)).isEqualTo(invocationAuthentication);
        assertThat(canAccess(capability, session, invocationAuthentication)).isFalse();
        assertThat(canAccess(capability, session, authentication("ROLE_ALLOWED"))).isTrue();
    }

    @Test
    void executorJavaPolicyCannotBeBypassedByPermissiveYaml() {
        var fixture = ExecutorPolicyFixture.create(SkillAccessPolicy.unrestricted(), SkillAccessPolicy.roles(Set.of("JAVA_WRITE")));
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("composed", fixture.capability().name(), 2);
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                ai.loomspan.internal.core.ExecutionBinding.sessionOnly(session, fixture.snapshot()), () -> {
            assertThat(accessGuard.canAccess(fixture.capability(), session, authentication("ROLE_YAML_READ"))).isFalse();
            assertThatThrownBy(() -> accessGuard.checkAccess(fixture.capability(), session, authentication("ROLE_YAML_READ")))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining(fixture.capability().name());
            assertThat(accessGuard.canAccess(fixture.capability(), session, authentication("ROLE_JAVA_WRITE"))).isTrue();
        });
    }

    @Test
    void usesOneResolvedCallerForEveryConstituent() {
        var fixture = ExecutorPolicyFixture.create(SkillAccessPolicy.yamlRoles(Set.of("SALES")), SkillAccessPolicy.roles(Set.of("DATABASE_WRITE")));
        var session = ai.loomspan.internal.core.TestLoomspanSessions.withId("same-caller", fixture.capability().name(), 2);
        var both = authentication("ROLE_SALES", "ROLE_DATABASE_WRITE");
        var onlyYaml = authentication("ROLE_SALES");
        var onlyJava = authentication("ROLE_DATABASE_WRITE");
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var ambient = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        ambient.setAuthentication(both);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ambient);
        try {
            ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                    ai.loomspan.internal.core.ExecutionBinding.sessionOnly(session, fixture.snapshot()), () -> {
                session.setAuthentication(both);
                for (var explicit : java.util.List.of(onlyYaml, onlyJava)) {
                    assertThat(accessGuard.canAccess(fixture.capability(), session, explicit)).isFalse();
                    assertThatThrownBy(() -> accessGuard.checkAccess(fixture.capability(), session, explicit))
                            .isInstanceOf(AccessDeniedException.class);
                }
                assertThat(accessGuard.canAccess(fixture.capability(), session, null)).isTrue();
                session.setAuthentication(onlyJava);
                assertThat(accessGuard.canAccess(fixture.capability(), session, onlyYaml)).isFalse();
                assertThat(accessGuard.canAccess(fixture.capability(), session, both)).isTrue();
                assertThat(session.getAuthentication().orElseThrow()).isSameAs(onlyJava);
            });
            var empty = ai.loomspan.internal.core.TestLoomspanSessions.withId("no-caller", fixture.capability().name(), 2);
            ai.loomspan.internal.core.ExecutionBindingScope.runWith(
                    ai.loomspan.internal.core.ExecutionBinding.sessionOnly(empty, fixture.snapshot()), () ->
                    assertThat(accessGuard.canAccess(fixture.capability(), empty, null)).isFalse());
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext()).isSameAs(ambient);
        } finally { org.springframework.security.core.context.SecurityContextHolder.setContext(previous); }
    }

    private boolean canAccess(CapabilityMetadata capability, LoomspanSession session, Authentication authentication) {
        return ai.loomspan.internal.core.ExecutionBindingScope.supplyWith(rootBinding(capability, session),
                () -> accessGuard.canAccess(capability, session, authentication));
    }
    private void checkAccess(CapabilityMetadata capability, LoomspanSession session, Authentication authentication) {
        ai.loomspan.internal.core.ExecutionBindingScope.runWith(rootBinding(capability, session),
                () -> accessGuard.checkAccess(capability, session, authentication));
    }
    private ai.loomspan.internal.core.ExecutionBinding rootBinding(CapabilityMetadata capability, LoomspanSession session) {
        var generation = new ai.loomspan.internal.skill.SkillGeneration("default", "captured",
                java.util.Map.of(capability.name(), capability), java.util.Map.of(), null,
                new ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog(java.util.List.of(), java.util.Map.of()));
        return ai.loomspan.internal.core.ExecutionBinding.sessionOnly(session,
                new ai.loomspan.internal.skill.SkillAdmissionSnapshot(capability.identity(), java.util.Map.of("default", generation)));
    }

    private static CapabilityMetadata capability(String name, Set<String> roles) {
        return new CapabilityMetadata(
                "yaml:" + name,
                name,
                name,
                new SkillExecutionDescriptor("gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium"),
                SkillAccessPolicy.yamlRoles(roles),
                arguments -> "ok",
                SkillKind.YAML,
                CapabilityToolDescriptor.generic(name, name),
                null);
    }

    private static Authentication authentication(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "user",
                "pw",
                AuthorityUtils.createAuthorityList(authorities));
    }
}
