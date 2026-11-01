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
