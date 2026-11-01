package ai.loomspan.internal.skillapi;

import ai.loomspan.api.SkillKind;
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
        String executorSchema = "{\n  \"type\": \"object\"\n}";
        List<CapabilityMetadata> capabilities = List.of(
                metadata("default-beta", SkillKind.JAVA, "{\"type\":\"object\"}", SkillAccessPolicy.unrestricted()),
                metadata("default-Alpha", SkillKind.YAML, yamlSchema, SkillAccessPolicy.yamlRoles(java.util.Set.of("ADMIN"))),
                metadata("default-zExecutorLeaf", SkillKind.JAVA, executorSchema, SkillAccessPolicy.denied()),
                metadata("default-nativeLeaf", SkillKind.TYPESAFE, yamlSchema, SkillAccessPolicy.unrestricted()));

        DefaultSkillCatalog catalog = new DefaultSkillCatalog("default", "test-generation", capabilities);

        assertThat(catalog.generationId()).isEqualTo("test-generation");

        assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                .containsExactly("default-Alpha", "default-beta", "default-nativeLeaf", "default-zExecutorLeaf");
        assertThat(catalog.skills()).extracting(descriptor -> descriptor.kind())
                .containsExactly(SkillKind.YAML, SkillKind.JAVA, SkillKind.TYPESAFE, SkillKind.JAVA);
        assertThat(catalog.skill("default-Alpha")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("default-zExecutorLeaf")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(executorSchema);
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
                metadata("default-same", SkillKind.YAML, "{}", SkillAccessPolicy.unrestricted()),
                metadata("default-same", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted()));

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
                metadata("other-search", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted()))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void normalizesLookupNamespaceAndPreservesLocalCase() {
        var catalog = new DefaultSkillCatalog("Tax", "id", List.of(
                metadata("tax-search", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted())));
        assertThat(catalog.skill("TAX-search")).isPresent();
        assertThat(catalog.skill("tax-Search")).isEmpty();
        assertThat(catalog.skill("other-search")).isEmpty();
        assertThatThrownBy(() -> catalog.skill("tax/search")).isInstanceOf(IllegalArgumentException.class);
    }

    private CapabilityMetadata metadata(String name, SkillKind kind, String schema,
            SkillAccessPolicy accessPolicy)
    {
        return new CapabilityMetadata("test:" + name + ":" + kind, name, "Description " + name,
                SkillExecutionDescriptor.none(), accessPolicy, arguments -> "ok", kind,
                new CapabilityToolDescriptor(name, "Description " + name, schema, kind == SkillKind.TYPESAFE ? schema : null), null);
    }
}
