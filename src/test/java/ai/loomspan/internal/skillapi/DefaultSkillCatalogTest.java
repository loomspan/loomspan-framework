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
                metadata("default:beta", SkillKind.JAVA, "{\"type\":\"object\"}", SkillAccessPolicy.unrestricted()),
                metadata("default:alpha", SkillKind.YAML, yamlSchema, SkillAccessPolicy.yamlRoles(java.util.Set.of("ADMIN"))),
                metadata("default:z-executor-leaf", SkillKind.JAVA, executorSchema, SkillAccessPolicy.denied()),
                metadata("default:model-leaf", SkillKind.YAML, yamlSchema, SkillAccessPolicy.unrestricted()));

        DefaultSkillCatalog catalog = new DefaultSkillCatalog("default", "test-generation", null, capabilities);

        assertThat(catalog.generationId()).isEqualTo("test-generation");

        assertThat(catalog.skills()).extracting(descriptor -> descriptor.qualifiedName())
                .containsExactly("default:alpha", "default:beta", "default:model-leaf", "default:z-executor-leaf");
        assertThat(catalog.skills()).extracting(descriptor -> descriptor.kind())
                .containsExactly(SkillKind.YAML, SkillKind.JAVA, SkillKind.YAML, SkillKind.JAVA);
        assertThat(catalog.skill("alpha")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("z-executor-leaf")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(executorSchema);
        assertThat(catalog.skill("model-leaf")).get().extracting(descriptor -> descriptor.inputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("model-leaf")).get().extracting(descriptor -> descriptor.outputSchema())
                .isEqualTo(yamlSchema);
        assertThat(catalog.skill("missing")).isEmpty();
        assertThatThrownBy(() -> catalog.skills().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateNamesDefensively()
    {
        List<CapabilityMetadata> capabilities = List.of(
                metadata("default:same", SkillKind.YAML, "{}", SkillAccessPolicy.unrestricted()),
                metadata("default:same", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted()));

        assertThatThrownBy(() -> new DefaultSkillCatalog("default", "test-generation", null, capabilities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate registered skill name 'default:same'");
    }

    @Test
    void namespaceScopedLookupSelectsOnlyTheExactLocalName()
    {
        var orders = new DefaultSkillCatalog("orders", "orders-generation", null, List.of(
                metadata("orders:lookup", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted()),
                metadata("orders:warehouse-lookup", SkillKind.YAML, "{}", SkillAccessPolicy.unrestricted())));
        var warehouse = new DefaultSkillCatalog("warehouse", "warehouse-generation", null, List.of(
                metadata("warehouse:lookup", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted())));
        var selected = orders.skill("lookup").orElseThrow();
        assertThat(selected.namespace()).isEqualTo("orders");
        assertThat(selected.localName()).isEqualTo("lookup");
        assertThat(selected.qualifiedName()).isEqualTo("orders:lookup");
        assertThat(warehouse.skill("lookup").orElseThrow().qualifiedName()).isEqualTo("warehouse:lookup");
        assertThat(orders.skill("warehouse-lookup").orElseThrow().qualifiedName()).isEqualTo("orders:warehouse-lookup");
        var removed = new DefaultSkillCatalog("orders", "removed", null, List.of());
        assertThat(removed.skill("warehouse-lookup")).isEmpty();
        assertThat(warehouse.skill("lookup")).isPresent();
        assertThat(orders.skill("missing")).isEmpty();
        assertThat(orders.skill("lookup-3pl")).isEmpty();
    }

    @Test
    void rejectsMalformedComponentsAndAppliesPairLimitEvenOnMissingNames()
    {
        var catalog = new DefaultSkillCatalog("order-support", "id", null, List.of());
        for (String invalid : List.of("", " ", " lookup", "lookup ", "Lookup", "lookup_name", "a--b", "1lookup", "\u00e9",
                "claude", "x-anthropic", "orders:lookup", "a:b:c", "orders--lookup", "a/b", "a".repeat(65)))
            assertThatThrownBy(() -> catalog.skill(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.skill(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.skill("a".repeat(49))).isEmpty();
        assertThatThrownBy(() -> catalog.skill("a".repeat(50))).isInstanceOf(IllegalArgumentException.class);
        for (String owner : List.of("Tax", " tax", "tax ", "tax_name", "claude", "1tax"))
            assertThatThrownBy(() -> new DefaultSkillCatalog(owner, "id", null, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifiesEmptyCatalogAndRejectsForeignOwnership()
    {
        var empty = new DefaultSkillCatalog("tax", "empty", null, List.of());
        assertThat(empty.namespace()).isEqualTo("tax");
        assertThat(empty.skill("missing")).isEmpty();
        assertThatThrownBy(() -> new DefaultSkillCatalog("tax", "id", null, List.of(
                metadata("other:search", SkillKind.JAVA, "{}", SkillAccessPolicy.unrestricted()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CapabilityMetadata metadata(String name, SkillKind kind, String schema,
            SkillAccessPolicy accessPolicy)
    {
        return new CapabilityMetadata("test:" + name + ":" + kind, name, "Description " + name,
                SkillExecutionDescriptor.none(), accessPolicy, arguments -> "ok", kind,
                new CapabilityToolDescriptor(name, "Description " + name, schema, kind == SkillKind.YAML ? schema : null), null);
    }
}
