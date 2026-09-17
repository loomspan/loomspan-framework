package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.core.SkillSource;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGenerationManagerTest
{
    @Test
    void suppliedSetIsFrozenAndCanAlternateWithConfiguredDiscovery(@TempDir Path directory) throws Exception
    {
        String configuredYaml = "name: configured\ndescription: configured\nmodel: model\n";
        Path file = directory.resolve("configured.yaml");
        Files.writeString(file, configuredYaml);
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill("fixedJava")));
        SkillGenerationManager manager = manager(javaSkills, () -> new YamlSkillCatalog(loadingProperties(directory)));
        String suppliedYaml = "name: supplied\ndescription: supplied\nmodel: model\n";
        java.util.ArrayList<SkillDocument> caller = new java.util.ArrayList<>();
        caller.add(new SkillDocument("opaque label", suppliedYaml));
        SkillGeneration supplied = manager.prepare(caller);
        caller.clear();
        Files.delete(file);
        manager.activate(supplied);
        assertThat(supplied.skillCatalog().skill("supplied")).isPresent();
        assertThat(supplied.skillCatalog().skill("configured")).isEmpty();
        assertThat(supplied.registeredSkillCatalog().find("supplied")).get()
                .satisfies(entry -> {
                    assertThat(entry.sourcePath()).isEqualTo("opaque label");
                    assertThat(entry.yaml()).isEqualTo(suppliedYaml);
                });
        SkillGeneration empty = manager.prepare(List.of());
        assertThat(empty.capabilities()).extracting(CapabilityMetadata::name).containsExactly("fixedJava");
        assertThat(manager.prepare(List.of(new SkillDocument("opaque label", suppliedYaml))).id())
                .isNotEqualTo(supplied.id());
        assertThatThrownBy(() -> manager.prepare(List.of(new SkillDocument("orphan label", """
                name: orphan
                description: orphan
                model: model
                allowed_skills:
                  - name: missingChild
                """))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missingChild")
                .hasMessageContaining("orphan label");
        assertThat(manager.prepare().definitions()).isEmpty();
    }

    @Test
    void preparationsAreFreshImmutableCompleteGenerations()
    {
        CapabilityMetadata javaSkill = javaSkill("fixedJava");
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill));
        YamlSkillDefinition yaml = yamlSkill("yamlSkill", List.of("fixedJava"));
        SkillGenerationManager manager = manager(javaSkills, () -> catalog(yaml));

        SkillGeneration first = manager.prepare();
        SkillGeneration second = manager.prepare();

        assertThat(first.id()).isNotBlank().isNotEqualTo(second.id());
        assertThat(first.capabilities()).extracting(CapabilityMetadata::name)
                .containsExactly("fixedJava", "yamlSkill");
        assertThat(first.capability("fixedJava")).isSameAs(javaSkill);
        assertThat(first.definition("yamlSkill")).isSameAs(yaml);
        assertThat(first.skillCatalog().skills()).extracting(skill -> skill.name())
                .containsExactly("fixedJava", "yamlSkill");
        assertThat(first.registeredSkillCatalog().registeredSkillCount()).isEqualTo(2);
        manager.activate(first);
        assertThat(manager.active()).isSameAs(first);
        verify(javaSkills).capabilities();
    }

    @Test
    void invalidCandidateNeverChangesActiveGeneration()
    {
        CapabilityMetadata javaSkill = javaSkill("collision");
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill));
        AtomicInteger attempt = new AtomicInteger();
        SkillGenerationManager manager = manager(javaSkills, () -> attempt.getAndIncrement() == 0
                ? catalog(yamlSkill("validYaml", List.of()))
                : catalog(yamlSkill("collision", List.of())));
        SkillGeneration active = manager.prepare();
        manager.activate(active);

        assertThatThrownBy(manager::prepare)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
        assertThat(manager.active()).isSameAs(active);
        assertThat(manager.active().capability("validYaml")).isNotNull();
        assertThat(manager.active().skillCatalog().skill("validYaml")).isPresent();
    }

    @Test
    void supportsWholeSetAdditionRemovalAndEmptyYaml()
    {
        CapabilityMetadata javaSkill = javaSkill("fixedJava");
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of(javaSkill));
        AtomicInteger attempt = new AtomicInteger();
        SkillGenerationManager manager = manager(javaSkills, () -> switch (attempt.getAndIncrement())
        {
            case 0 -> catalog(yamlSkill("first", List.of()));
            case 1 -> catalog(yamlSkill("second", List.of("fixedJava")));
            case 2 -> catalog();
            default -> catalog(yamlSkill("parent", List.of("removedChild")));
        });

        assertThat(manager.prepare().capability("first")).isNotNull();
        assertThat(manager.prepare().capability("second")).isNotNull();
        SkillGeneration emptyYaml = manager.prepare();
        assertThat(emptyYaml.capabilities()).containsExactly(javaSkill);
        assertThat(emptyYaml.definitions()).isEmpty();
        assertThatThrownBy(manager::prepare)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown child skill 'removedChild'");
    }

    @Test
    void preparedGenerationDoesNotRereadChangedOrDeletedSources(@TempDir Path directory) throws Exception
    {
        Path manifest = directory.resolve("captured.yaml");
        String original = """
                name: captured
                description: Original description
                model: model
                prompt: Original prompt
                """;
        Files.writeString(manifest, original, StandardCharsets.UTF_8);
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        LoomspanProperties properties = loadingProperties(directory);
        SkillGenerationManager manager = manager(javaSkills, () -> new YamlSkillCatalog(properties));

        SkillGeneration candidate = manager.prepare();
        Files.writeString(manifest, original.replace("Original", "Replacement"), StandardCharsets.UTF_8);
        Files.delete(manifest);
        manager.activate(candidate);

        assertThat(manager.active()).isSameAs(candidate);
        assertThat(candidate.definition("captured").prompt()).isEqualTo("Original prompt");
        assertThat(candidate.skillCatalog().skill("captured")).get()
                .extracting(skill -> skill.description()).isEqualTo("Original description");
        assertThat(candidate.registeredSkillCatalog().find("captured")).get()
                .extracting(entry -> entry.yaml()).isEqualTo(original);
        assertThat(manager.prepare().definitions()).isEmpty();
    }

    @Test
    void moreThanTwoLegitimatelyOwnedGenerationsCoexistWithoutEviction()
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        AtomicInteger attempt = new AtomicInteger();
        SkillGenerationManager manager = manager(javaSkills,
                () -> catalog(yamlSkill("generation" + attempt.incrementAndGet(), List.of())));

        SkillGeneration first = manager.prepare();
        SkillGeneration second = manager.prepare();
        SkillGeneration prepared = manager.prepare();
        SkillGeneration active = manager.prepare();
        manager.activate(active);

        assertThat(List.of(first.id(), second.id(), prepared.id(), active.id())).doesNotHaveDuplicates();
        assertThat(first.capability("generation1")).isNotNull();
        assertThat(second.capability("generation2")).isNotNull();
        assertThat(prepared.capability("generation3")).isNotNull();
        assertThat(manager.active()).isSameAs(active);
        assertThat(active.capability("generation4")).isNotNull();
    }

    @Test
    void capturesOneRestHandlerInstanceAcrossPreparedGenerations()
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        AtomicInteger instances = new AtomicInteger();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(RestSkillHandler.class);
        definition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        definition.setInstanceSupplier(() -> {
            int instance = instances.incrementAndGet();
            return (RestSkillHandler) invocation -> "handler-" + instance;
        });
        beans.registerBeanDefinition("restHandler", definition);
        SkillGenerationManager manager = new SkillGenerationManager(javaSkills,
                () -> catalog(restSkill("restSkill")), new SkillInputContractResolver(), beans);

        SkillGeneration first = manager.prepare();
        SkillGeneration second = manager.prepare();

        assertThat(first.capability("restSkill").invoker().invoke(java.util.Map.of()))
                .isEqualTo("handler-1");
        assertThat(second.capability("restSkill").invoker().invoke(java.util.Map.of()))
                .isEqualTo("handler-1");
        assertThat(instances).hasValue(1);
    }

    private static SkillGenerationManager manager(SkillMethodBeanPostProcessor javaSkills,
            java.util.function.Supplier<YamlSkillCatalog> catalogs)
    {
        return new SkillGenerationManager(javaSkills, catalogs, new SkillInputContractResolver(),
                new StaticListableBeanFactory());
    }

    private static LoomspanProperties loadingProperties(Path directory)
    {
        LoomspanProperties properties = new LoomspanProperties();
        LoomspanProperties.Skills skills = new LoomspanProperties.Skills();
        skills.setLocations(List.of(directory.toUri() + "*.yaml"));
        properties.setSkills(skills);
        LoomspanProperties.ConnectionProperties connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.OPENAI);
        properties.setConnections(Map.of("connection", connection));
        LoomspanProperties.ModelCatalogEntry model = new LoomspanProperties.ModelCatalogEntry();
        model.setConnection("connection");
        model.setProviderModel("provider-model");
        model.setThinkingLevels(java.util.Set.of("medium"));
        properties.setModels(Map.of("model", model));
        return properties;
    }

    private static YamlSkillCatalog catalog(YamlSkillDefinition... definitions)
    {
        YamlSkillCatalog catalog = mock(YamlSkillCatalog.class);
        when(catalog.getSkills()).thenReturn(List.of(definitions));
        return catalog;
    }

    private static CapabilityMetadata javaSkill(String name)
    {
        return new CapabilityMetadata("java:" + name, name, name, SkillExecutionDescriptor.none(),
                SkillAccessPolicy.unrestricted(), arguments -> name, CapabilityKind.JAVA_SKILL,
                CapabilityToolDescriptor.generic(name, name),
                new SkillSource(null, "testBean", name + "()"));
    }

    private static YamlSkillDefinition yamlSkill(String name, List<String> allowed)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setModel("model");
        manifest.setAllowedSkills(allowed.stream()
                .map(child -> new YamlSkillManifest.AllowedSkillManifest(child, null, null, null)).toList());
        byte[] yaml = ("name: " + name + "\ndescription: " + name + "\nmodel: model\n")
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(yaml, name + ".yaml")
        {
            @Override public String getFilename() { return name + ".yaml"; }
        };
        return new YamlSkillDefinition(resource, manifest,
                new EffectiveSkillExecutionConfiguration("model", "connection", AiDriver.OPENAI,
                        "provider-model", "medium"));
    }

    private static YamlSkillDefinition restSkill(String name)
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName(name);
        manifest.setDescription(name);
        manifest.setRest(true);
        byte[] yaml = ("name: " + name + "\ndescription: " + name + "\nrest: true\n")
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(yaml, name + ".yaml")
        {
            @Override public String getFilename() { return name + ".yaml"; }
        };
        return new YamlSkillDefinition(resource, manifest, null);
    }
}
