package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.SkillSource;
import static org.mockito.Mockito.*;
import ai.loomspan.internal.skill.YamlSkillManifest;
import ai.loomspan.internal.skill.YamlSkillSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRegisteredSkillCatalogTest
{
    @TempDir
    Path tempDir;

    @Test
    void preservesYamlAndTraversesByExactRegisteredName() throws Exception
    {
        String alphaYaml = "# comment\r\nname: Alpha\r\ndescription: first\r\n";
        String betaYaml = "name: beta\ndescription: second\n";
        DefaultRegisteredSkillCatalog catalog = catalog(List.of(definition("beta", betaYaml), definition("Alpha", alphaYaml)), true);

        assertThat(catalog.listAfter(null, 10))
                .extracting(RegisteredSkillEntry.Summary::registeredName)
                .containsExactly("Alpha", "JavaLookup", "beta");
        assertThat(catalog.listAfter("Alpha", 10))
                .extracting(RegisteredSkillEntry.Summary::registeredName)
                .containsExactly("JavaLookup", "beta");
        assertThat(catalog.find("Alpha").orElseThrow().yaml()).isEqualTo(alphaYaml);
        assertThat(catalog.find("alpha")).isEmpty();
        assertThat(catalog.registeredSkillCount()).isEqualTo(3);
        assertThat(catalog.find("JavaLookup").orElseThrow()).isEqualTo(new RegisteredSkillEntry(
                "JavaLookup", "JAVA", null, "lookupBean", "example.Lookup.lookup(java.lang.String)", null));
    }

    @Test
    void rejectsInvalidUtf8OnlyWhenInspectionCatalogIsConstructed() throws Exception
    {
        YamlSkillDefinition definition = definition("skill", "name: skill\n");
        YamlSkillSource invalid = new YamlSkillSource(
                definition.resource(),
                definition.source().locationPattern(),
                new byte[] {(byte) 0xC3, (byte) 0x28});
        YamlSkillDefinition withInvalidSource = new YamlSkillDefinition(
                definition.resource(),
                definition.manifest(),
                definition.executionConfiguration(),
                EvidenceContract.empty(),
                invalid);

        assertThat(withInvalidSource.manifest().getName()).isEqualTo("skill");
        assertThatThrownBy(() -> catalog(List.of(withInvalidSource), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid UTF-8");
    }

    private DefaultRegisteredSkillCatalog catalog(List<YamlSkillDefinition> definitions, boolean includeJava)
    {
        CapabilityRegistry registry = mock(CapabilityRegistry.class);
        YamlSkillCatalog yaml = mock(YamlSkillCatalog.class);
        YamlSkillCapabilityRegistrar registrar = mock(YamlSkillCapabilityRegistrar.class);
        var entries = new java.util.ArrayList<CapabilityMetadata>();
        for (var definition : definitions)
        {
            var metadata = mock(CapabilityMetadata.class);
            when(metadata.name()).thenReturn(definition.manifest().getName());
            when(metadata.kind()).thenReturn(CapabilityKind.YAML_SKILL);
            when(yaml.getSkill(metadata.name())).thenReturn(definition);
            entries.add(metadata);
        }
        if (includeJava)
        {
            var metadata = mock(CapabilityMetadata.class);
            when(metadata.name()).thenReturn("JavaLookup");
            when(metadata.kind()).thenReturn(CapabilityKind.JAVA_SKILL);
            when(metadata.source()).thenReturn(new SkillSource(null, "lookupBean", "example.Lookup.lookup(java.lang.String)"));
            entries.add(metadata);
        }
        when(registry.getAllCapabilities()).thenReturn(entries);
        var result = new DefaultRegisteredSkillCatalog(registry, yaml, registrar);
        var order = inOrder(registrar, registry);
        order.verify(registrar).completeRegistration();
        order.verify(registry).getAllCapabilities();
        return result;
    }

    private YamlSkillDefinition definition(String name, String yaml) throws Exception
    {
        Path file = Files.writeString(tempDir.resolve(name + ".yaml"), yaml, StandardCharsets.UTF_8);
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription("description");
        manifest.setModel("model");
        return new YamlSkillDefinition(
                new FileSystemResource(file),
                manifest,
                new EffectiveSkillExecutionConfiguration(
                        "model", "connection", AiDriver.OPENAI, "provider-model", null),
                EvidenceContract.empty(),
                new YamlSkillSource(
                        new FileSystemResource(file),
                        file.toUri().toString(),
                        yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
