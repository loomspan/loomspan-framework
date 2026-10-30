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
                metadata("default-beta", CapabilityKind.JAVA_SKILL, "{\"type\":\"object\"}", SkillAccessPolicy.unrestricted()),
                metadata("default-Alpha", CapabilityKind.YAML_SKILL, yamlSchema, SkillAccessPolicy.yamlRoles(java.util.Set.of("ADMIN"))),
                metadata("default-restLeaf", CapabilityKind.REST_SKILL, restSchema, SkillAccessPolicy.denied()),
                metadata("default-nativeLeaf", CapabilityKind.TYPESAFE_SKILL, yamlSchema, SkillAccessPolicy.unrestricted()));

        DefaultSkillCatalog catalog = new DefaultSkillCatalog("default", "test-generation", capabilities);

        assertThat(catalog.generationId()).isEqualTo("test-generation");

        assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                .containsExactly("default-Alpha", "default-beta", "default-nativeLeaf", "default-restLeaf");
        assertThat(catalog.skills()).extracting(descriptor -> descriptor.kind())
                .containsExactly(SkillKind.YAML, SkillKind.JAVA, SkillKind.TYPESAFE, SkillKind.REST);
        assertThat(catalog.skill("default-Alpha")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("default-restLeaf")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(restSchema);
        assertThat(catalog.skill("default-nativeLeaf")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("default-nativeLeaf")).get().extracting(descriptor -> descriptor.outputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("default-missing")).isEmpty();
        assertThatThrownBy(() -> catalog.skills().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateNamesDefensively()
    {
        List<CapabilityMetadata> capabilities = List.of(
                metadata("default-same", CapabilityKind.YAML_SKILL, "{}", SkillAccessPolicy.unrestricted()),
                metadata("default-same", CapabilityKind.JAVA_SKILL, "{}", SkillAccessPolicy.unrestricted()));

        assertThatThrownBy(() -> new DefaultSkillCatalog("default", "test-generation", capabilities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate registered skill name 'default-same'");
    }

    @Test
    void rejectsUnqualifiedLookup()
    {
        var catalog = new DefaultSkillCatalog("default", "red-test", List.of());
        assertThatThrownBy(() -> catalog.skill("unqualified")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void identifiesEmptyCatalogAndRejectsForeignOwnership() {
        var empty = new DefaultSkillCatalog("Tax", "empty", List.of());
        assertThat(empty.namespace()).isEqualTo("tax");
        assertThat(empty.skill("tax-missing")).isEmpty();
        assertThat(empty.skill("other-search")).isEmpty();
        assertThatThrownBy(() -> new DefaultSkillCatalog("tax", "id", List.of(
                metadata("other-search", CapabilityKind.JAVA_SKILL, "{}", SkillAccessPolicy.unrestricted()))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void normalizesLookupNamespaceAndPreservesLocalCase() {
        var catalog = new DefaultSkillCatalog("Tax", "id", List.of(
                metadata("tax-search", CapabilityKind.JAVA_SKILL, "{}", SkillAccessPolicy.unrestricted())));
        assertThat(catalog.skill("TAX-search")).isPresent();
        assertThat(catalog.skill("tax-Search")).isEmpty();
        assertThat(catalog.skill("other-search")).isEmpty();
        assertThatThrownBy(() -> catalog.skill("tax/search")).isInstanceOf(IllegalArgumentException.class);
    }

    private CapabilityMetadata metadata(String name, CapabilityKind kind, String schema,
            SkillAccessPolicy accessPolicy)
    {
        return new CapabilityMetadata("test:" + name + ":" + kind, name, "Description " + name,
                SkillExecutionDescriptor.none(), accessPolicy, arguments -> "ok", kind,
                new CapabilityToolDescriptor(name, "Description " + name, schema, kind == CapabilityKind.TYPESAFE_SKILL ? schema : null), null);
    }
}
