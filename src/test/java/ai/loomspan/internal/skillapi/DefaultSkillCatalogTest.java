package ai.loomspan.internal.skillapi;

import ai.loomspan.api.SkillKind;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSkillCatalogTest
{
    @Test
    void buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas()
    {
        String yamlSchema = "{ \"type\" : \"object\", \"x\" : 1 }";
        String restSchema = "{\n  \"type\": \"object\"\n}";
        List<CapabilityMetadata> capabilities = List.of(
                metadata("beta", CapabilityKind.JAVA_SKILL, "{\"type\":\"object\"}", SkillAccessPolicy.unrestricted()),
                metadata("Alpha", CapabilityKind.YAML_SKILL, yamlSchema, SkillAccessPolicy.yamlRoles(java.util.Set.of("ADMIN"))),
                metadata("restLeaf", CapabilityKind.REST_SKILL, restSchema, SkillAccessPolicy.denied()));

        DefaultSkillCatalog catalog = new DefaultSkillCatalog("test-generation", capabilities);

        assertThat(catalog.generationId()).isEqualTo("test-generation");

        assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                .containsExactly("Alpha", "beta", "restLeaf");
        assertThat(catalog.skills()).extracting(descriptor -> descriptor.kind())
                .containsExactly(SkillKind.YAML, SkillKind.JAVA, SkillKind.REST);
        assertThat(catalog.skill("Alpha")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("restLeaf")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(restSchema);
        assertThat(catalog.skill("missing")).isEmpty();
        assertThatThrownBy(() -> catalog.skills().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateNamesDefensively()
    {
        List<CapabilityMetadata> capabilities = List.of(
                metadata("same", CapabilityKind.YAML_SKILL, "{}", SkillAccessPolicy.unrestricted()),
                metadata("same", CapabilityKind.JAVA_SKILL, "{}", SkillAccessPolicy.unrestricted()));

        assertThatThrownBy(() -> new DefaultSkillCatalog("test-generation", capabilities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate registered skill name 'same'");
    }

    private CapabilityMetadata metadata(String name, CapabilityKind kind, String schema,
            SkillAccessPolicy accessPolicy)
    {
        return new CapabilityMetadata("test:" + name + ":" + kind, name, "Description " + name,
                SkillExecutionDescriptor.none(), accessPolicy, arguments -> "ok", kind,
                new CapabilityToolDescriptor(name, "Description " + name, schema), null);
    }
}
