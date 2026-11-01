package ai.loomspan.internal.security;

import ai.loomspan.internal.skill.SkillAdmissionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class SkillRoleEvaluatorTest {
    record Case(SkillAccessPolicy yaml, SkillAccessPolicy java, String authorities, boolean allowed) {}
    static SkillAccessPolicy roles(String... roles) { return SkillAccessPolicy.roles(List.of(roles)); }
    static Stream<Case> policies() {
        var y = SkillAccessPolicy.yamlRoles(List.of("SALES", "SUPPORT"));
        var j = roles("DATABASE_WRITE", "DATABASE_ADMIN");
        var u = SkillAccessPolicy.unrestricted();
        return Stream.of(
            new Case(y,j,"ROLE_SALES,ROLE_DATABASE_WRITE",true),
            new Case(y,j,"ROLE_SUPPORT,ROLE_DATABASE_ADMIN",true),
            new Case(y,j,"ROLE_SALES",false), new Case(y,j,"ROLE_DATABASE_WRITE",false),
            new Case(roles("SALES"),roles("SALES"),"ROLE_SALES",true),
            new Case(u,j,"ROLE_OTHER",false), new Case(u,j,null,false),
            new Case(y,SkillAccessPolicy.permitAll(),null,false),
            new Case(y,u,"ROLE_OTHER",false), new Case(u,SkillAccessPolicy.permitAll(),null,true),
            new Case(u,u,null,true), new Case(y,SkillAccessPolicy.denied(),"ROLE_SALES,ROLE_DATABASE_WRITE",false),
            new Case(u,SkillAccessPolicy.denied(),"ROLE_DATABASE_WRITE",false),
            new Case(u,roles(),"ROLE_DATABASE_WRITE",false),
            new Case(SkillAccessPolicy.yamlRoles(List.of()),j,"ROLE_DATABASE_WRITE",true),
            new Case(roles("ROLE_SALES"),u,"ROLE_SALES",false),
            new Case(roles("ROLE_SALES"),u,"ROLE_ROLE_SALES",true));
    }
    static Authentication auth(String authorities) {
        return authorities == null ? null : UsernamePasswordAuthenticationToken.authenticated("caller", "unused",
                AuthorityUtils.commaSeparatedStringToAuthorityList(authorities));
    }
    @ParameterizedTest @MethodSource("policies")
    void evaluatesCapturedConstituentsConjunctively(Case test) {
        var f = ExecutorPolicyFixture.create(test.yaml(),test.java());
        var evaluator = new SkillRoleEvaluator(null,null);
        assertThat(evaluator.canAccess(f.capability(),f.snapshot(),auth(test.authorities()))).isEqualTo(test.allowed());
        if (test.allowed()) evaluator.checkAccess(f.capability(),f.snapshot(),auth(test.authorities()));
        else assertThatThrownBy(() -> evaluator.checkAccess(f.capability(),f.snapshot(),auth(test.authorities())))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access denied for capability 'caller-leaf'");
    }
    @Test void prefixesAndHierarchyApplyToBothConstituents() {
        var f = ExecutorPolicyFixture.create(roles("SALES"),roles("DATABASE_WRITE"));
        for (String prefix : List.of("ROLE_", "APP_", "")) {
            var hierarchy = RoleHierarchyImpl.fromHierarchy(prefix + "ADMIN > " + prefix + "SALES\n"
                    + prefix + "ADMIN > " + prefix + "DATABASE_WRITE");
            var evaluator = new SkillRoleEvaluator(new GrantedAuthorityDefaults(prefix),hierarchy);
            assertThat(evaluator.canAccess(f.capability(),f.snapshot(),auth(prefix+"ADMIN"))).isTrue();
            assertThat(evaluator.canAccess(f.capability(),f.snapshot(),auth(prefix+"SALES"))).isFalse();
            assertThat(evaluator.canAccess(f.capability(),f.snapshot(),null)).isFalse();
        }
    }
    @Test void executorWithoutAdmittedBindingFailsExplicitly() {
        var f = ExecutorPolicyFixture.create(SkillAccessPolicy.unrestricted(),SkillAccessPolicy.unrestricted());
        var missing = new SkillAdmissionSnapshot(f.snapshot().entry(),f.snapshot().generations());
        assertThatThrownBy(() -> new SkillRoleEvaluator(null,null).canAccess(f.capability(),missing,null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No admitted executor binding");
    }
    @Test void annotationOnlyJavaAndNonExecutorKindsKeepTheirSingleConstituent() {
        for (var kind : ai.loomspan.api.SkillKind.values()) {
            var capability = new ai.loomspan.internal.core.CapabilityMetadata("test", "default-leaf", "leaf", null,
                    SkillAccessPolicy.roles(List.of("READ")), arguments -> { throw new AssertionError(); },kind,null,null);
            var generation = new ai.loomspan.internal.skill.SkillGeneration("default","single",Map.of(capability.name(),capability),
                    Map.of(),null,new ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog(List.of(),Map.of()));
            var snapshot = new SkillAdmissionSnapshot(capability.identity(),Map.of("default",generation));
            var evaluator = new SkillRoleEvaluator(null,null);
            assertThat(evaluator.canAccess(capability,snapshot,auth("ROLE_READ"))).isTrue();
            assertThat(evaluator.canAccess(capability,snapshot,auth("ROLE_OTHER"))).isFalse();
        }
    }

}
