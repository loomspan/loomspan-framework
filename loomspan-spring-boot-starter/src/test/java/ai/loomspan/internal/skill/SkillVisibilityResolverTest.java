package ai.loomspan.internal.skill;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.InMemoryCapabilityRegistry;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.DefaultAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillVisibilityResolverTest {

    @Test
    void returnsOnlyAllowedYamlSkillsThatPassRbac() {
        YamlSkillCatalog catalog = catalog("classpath:/skills/valid/allowed-child-skill.yaml", "classpath:/skills/valid/allowed-disallowed-child.yaml", "classpath:/skills/valid/allowed-skills-root.yaml");
        catalog.afterPropertiesSet();
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        var targets = new ai.loomspan.internal.core.SkillMethodBeanPostProcessor(registry);
        new YamlSkillCapabilityRegistrar(registry, targets, catalog, new SkillInputContractResolver()).afterSingletonsInstantiated();

        DefaultSkillVisibilityResolver resolver = new DefaultSkillVisibilityResolver(catalog, registry, new DefaultAccessGuard());

        List<CapabilityMetadata> visible = resolver.visibleSkillsFor(
                "rootVisibleSkill",
                ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 2),
                UsernamePasswordAuthenticationToken.authenticated(
                        "user",
                        "pw",
                        AuthorityUtils.createAuthorityList("ROLE_ALLOWED")));

        assertThat(visible).extracting(CapabilityMetadata::name).containsExactly("allowedVisibleSkill");
    }

    @Test
    void doesNotExposeSkillsOutsideTheParentAllowlist() {
        YamlSkillCatalog catalog = catalog("classpath:/skills/valid/allowed-child-skill.yaml", "classpath:/skills/valid/allowed-disallowed-child.yaml", "classpath:/skills/valid/allowed-skills-root.yaml");
        catalog.afterPropertiesSet();
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        var targets = new ai.loomspan.internal.core.SkillMethodBeanPostProcessor(registry);
        new YamlSkillCapabilityRegistrar(registry, targets, catalog, new SkillInputContractResolver()).afterSingletonsInstantiated();

        DefaultSkillVisibilityResolver resolver = new DefaultSkillVisibilityResolver(catalog, registry, new DefaultAccessGuard());

        List<CapabilityMetadata> visible = resolver.visibleSkillsFor(
                "rootVisibleSkill",
                ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 2),
                UsernamePasswordAuthenticationToken.authenticated(
                        "user",
                        "pw",
                        AuthorityUtils.createAuthorityList("ROLE_ALLOWED")));

        assertThat(visible).extracting(CapabilityMetadata::name).containsExactly("allowedVisibleSkill");
    }

    @Test
    void hidesProtectedSkillsWhenAuthenticationIsMissing() {
        YamlSkillCatalog catalog = catalog("classpath:/skills/valid/allowed-child-skill.yaml", "classpath:/skills/valid/allowed-disallowed-child.yaml", "classpath:/skills/valid/allowed-skills-root.yaml");
        catalog.afterPropertiesSet();
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        var targets = new ai.loomspan.internal.core.SkillMethodBeanPostProcessor(registry);
        new YamlSkillCapabilityRegistrar(registry, targets, catalog, new SkillInputContractResolver()).afterSingletonsInstantiated();

        DefaultSkillVisibilityResolver resolver = new DefaultSkillVisibilityResolver(catalog, registry, new DefaultAccessGuard());

        List<CapabilityMetadata> visible = resolver.visibleSkillsFor(
                "rootVisibleSkill",
                ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 2),
                null);

        assertThat(visible).isEmpty();
    }

    @Test
    void usesSessionFallbackForProtectedSkillVisibility() {
        YamlSkillCatalog catalog = catalog("classpath:/skills/valid/allowed-child-skill.yaml", "classpath:/skills/valid/allowed-disallowed-child.yaml", "classpath:/skills/valid/allowed-skills-root.yaml");
        catalog.afterPropertiesSet();
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        var targets = new ai.loomspan.internal.core.SkillMethodBeanPostProcessor(registry);
        new YamlSkillCapabilityRegistrar(registry, targets, catalog, new SkillInputContractResolver()).afterSingletonsInstantiated();

        DefaultSkillVisibilityResolver resolver = new DefaultSkillVisibilityResolver(catalog, registry, new DefaultAccessGuard());
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-1", "test.entry", 2);
        session.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user",
                "pw",
                AuthorityUtils.createAuthorityList("ROLE_ALLOWED")));

        List<CapabilityMetadata> visible = resolver.visibleSkillsFor("rootVisibleSkill", session, null);

        assertThat(visible).extracting(CapabilityMetadata::name).containsExactly("allowedVisibleSkill");
    }

    private static YamlSkillCatalog catalog(String... locations) {
        LoomspanProperties models = new LoomspanProperties();
        LoomspanProperties.ConnectionProperties connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.OPENAI);
        connection.setApiKey("test-key");
        models.setConnections(Map.of("openai-main", connection));
        LoomspanProperties.ModelCatalogEntry entry = new LoomspanProperties.ModelCatalogEntry();
        entry.setConnection("openai-main");
        entry.setProviderModel("openai/gpt-5");
        entry.setThinkingLevels(Set.of("low", "medium", "high"));
        models.setModels(Map.of("gpt-5", entry));

        LoomspanProperties.Skills skills = new LoomspanProperties.Skills();
        skills.setLocations(List.of(locations));

        ObjectMapper mapper = YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return new YamlSkillCatalog(models, skills, new PathMatchingResourcePatternResolver(), mapper);
    }

}
