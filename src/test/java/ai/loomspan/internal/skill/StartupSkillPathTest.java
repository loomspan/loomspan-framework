package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.LoomspanProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StartupSkillPathTest
{
    private static final String YAML = "name: lookup\ndescription: fixture\nexecutor: fixture\n";
    @TempDir Path directory;

    @Test
    void rejectsUppercaseExtensionOmittedByConfiguredPattern() throws Exception
    {
        Path nested = Files.createDirectories(directory.resolve("orders"));
        Files.writeString(nested.resolve("lookup.YAML"), "name: lookup\ndescription: fixture\nexecutor: fixture\n");
        LoomspanProperties properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of(directory.toUri() + "**/*.yaml"));

        var checked = new YamlSkillCatalog(properties).checkedConfigured(false);

        assertThat(checked.issues()).anySatisfy(issue -> assertThat(issue.message())
                .contains("orders/lookup.YAML", "lookup.YAML"));
    }

    @ParameterizedTest
    @CsvSource({"Orders/lookup.yaml, Orders", "orders/lookup.YAML, lookup.YAML", "orders/Lookup.yaml, Lookup.yaml"})
    void rejectsActualFilesystemSpelling(String relative, String component) throws Exception
    {
        write(directory.resolve(relative), "invalid: [");
        assertRejected(check(directory.toUri() + "**/*.yaml"), relative, component);
    }

    @Test
    void acceptsLowercasePathsAndPreservesPatternExtensionAndOrder() throws Exception
    {
        Path root = Files.createDirectories(directory.resolve("Root With Spaces"));
        write(root.resolve("orders/lookup.yaml"), YAML);
        write(root.resolve("first.yaml"), YAML.replace("lookup", "first"));
        write(root.resolve("orders/ignored.yml"), YAML.replace("lookup", "ignored"));
        write(root.resolve("readme.txt"), "invalid: [");
        var checked = check(root.toUri() + "**/*.yaml");
        assertThat(checked.issues()).isEmpty();
        assertThat(checked.definitions()).extracting(definition -> definition.identity().toString())
                .containsExactly("default:first", "default:lookup");
        assertThat(check(root.toUri() + "**/*.yml").definitions()).hasSize(1);
        assertThat(check(root.resolve("absent").toUri() + "**/*.yaml").issues()).isEmpty();
    }

    @Test
    void decodesRelativeUriEscapesBeforeCheckingCase() throws Exception
    {
        Path file = directory.resolve("orders/café.yaml");
        write(file, YAML);
        assertThat(check(directory.toUri() + "**/*.yaml").issues()).isEmpty();
        assertThat(check(file.toUri().toString()).issues()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath:", "classpath*:"})
    void validatesExplodedClasspathCandidates(String prefix) throws Exception
    {
        Path root = Files.createDirectories(directory.resolve("Classpath Root"));
        Path file = root.resolve("skills/orders/lookup.yaml");
        write(file, YAML);
        try (var loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null))
        {
            assertThat(check(prefix + "/skills/**/*.yaml", new PathMatchingResourcePatternResolver(loader)).issues()).isEmpty();
            Files.delete(file);
            Files.delete(file.getParent());
            write(root.resolve("skills/Orders/lookup.yaml"), "invalid: [");
            assertRejected(check(prefix + "/skills/**/*.yaml", new PathMatchingResourcePatternResolver(loader)), "Orders/lookup.yaml", "Orders");
            Files.delete(root.resolve("skills/Orders/lookup.yaml"));
            Files.delete(root.resolve("skills/Orders"));
            write(root.resolve("skills/orders/lookup.YAML"), "invalid: [");
            assertRejected(check(prefix + "/skills/**/*.yaml", new PathMatchingResourcePatternResolver(loader)), "orders/lookup.YAML", "lookup.YAML");
        }
    }

    @ParameterizedTest
    @CsvSource({"classpath:,orders/lookup.yaml,true", "classpath:,Orders/lookup.yaml,false", "classpath:,orders/lookup.YAML,false",
            "classpath*:,orders/lookup.yaml,true", "classpath*:,Orders/lookup.yaml,false", "classpath*:,orders/lookup.YAML,false",
            "jar:,orders/lookup.yaml,true", "jar:,Orders/lookup.yaml,false", "jar:,orders/lookup.YAML,false",
            "jar:,orders/café.yaml,true"})
    void validatesPackagedJarEntrySpelling(String prefix, String relative, boolean valid) throws Exception
    {
        Path jar = directory.resolve("Skills With Spaces.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar)))
        {
            for (String entry : List.of("skills/", "skills/" + relative.substring(0, relative.indexOf('/') + 1), "skills/" + relative))
            {
                output.putNextEntry(new JarEntry(entry));
                if (!entry.endsWith("/")) output.write((valid ? YAML : "invalid: [").getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null))
        {
            String location = prefix.equals("jar:") ? "jar:" + jar.toUri() + "!/skills/**/*.yaml" : prefix + "/skills/**/*.yaml";
            var checked = check(location, new PathMatchingResourcePatternResolver(loader));
            if (valid)
            {
                assertThat(checked.issues()).isEmpty();
                assertThat(checked.definitions()).hasSize(1);
            }
            else assertRejected(checked, relative, relative.startsWith("Orders") ? "Orders" : "lookup.YAML");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lookup.yaml", "**/Orders/*.yaml", "**/*.YAML"})
    void validatesCallerRelativeSpellingBeforeLookup(String suffix)
    {
        var resolver = new CountingResolver();
        var checked = check(directory.toUri() + suffix, resolver);
        assertThat(checked.issues()).anySatisfy(issue -> assertThat(issue.message()).contains(suffix));
        assertThat(resolver.lookups).isZero();
    }

    @Test
    void literalLocationsCheckOnlyFilename() throws Exception
    {
        Path file = directory.resolve("Orders/lookup.yaml");
        write(file, YAML);
        assertThat(check(file.toUri().toString()).issues()).isEmpty();
        try (var loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, null))
        {
            write(directory.resolve("skills/Orders/lookup.yaml"), YAML);
            assertThat(check("classpath:/skills/Orders/lookup.yaml", new PathMatchingResourcePatternResolver(loader)).issues()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath:", "classpath*:"})
    void classpathLiteralNamesRetainPercentCharacters(String prefix) throws Exception
    {
        write(directory.resolve("skills/lookup%thing.yaml"), YAML);
        try (var loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, null))
        {
            assertThat(check(prefix + "/skills/lookup%thing.yaml", new PathMatchingResourcePatternResolver(loader)).issues()).isEmpty();
        }
        var resolver = new CountingResolver();
        assertRejected(check(prefix + "/skills/lookup%2F.yaml", resolver), "lookup%2F.yaml", "lookup%2F.yaml");
        assertThat(resolver.lookups).isZero();
    }

    @Test
    void literalLookupChecksActualFilenameOnCaseInsensitiveFilesystem() throws Exception
    {
        write(directory.resolve("Lookup.yaml"), "invalid: [");
        Path supplied = directory.resolve("lookup.yaml");
        assumeTrue(Files.exists(supplied), "filesystem uses case-sensitive lookup");
        assertRejected(check(supplied.toUri().toString()), "Lookup.yaml", "Lookup.yaml");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Orders import #42", "Orders/lookup.yaml"})
    void suppliedDocumentSourceRetainsLabelAndBytes(String label)
    {
        var checked = new YamlSkillCatalog(new LoomspanProperties()).checkedSupplied("orders",
                List.of(new ai.loomspan.api.SkillDocument(label, YAML)), false);
        assertThat(checked.issues()).isEmpty();
        var source = checked.definitions().getFirst().source();
        assertThat(new SkillSourcePathResolver().resolve(source)).isEqualTo(label);
        assertThat(source.suppliedLabel()).isEqualTo(label);
        assertThat(source.bytes()).isEqualTo(YAML.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void literalSymlinkKeepsItsLogicalFilename() throws Exception
    {
        Path target = directory.resolve("Target.YAML");
        write(target, YAML);
        Path link = directory.resolve("lookup.yaml");
        try
        {
            Files.createSymbolicLink(link, target.getFileName());
        }
        catch (IOException | UnsupportedOperationException ex)
        {
            assumeTrue(false, "filesystem cannot create symbolic links");
        }
        var checked = check(link.toUri().toString());
        assertThat(checked.issues()).isEmpty();
        assertThat(new SkillSourcePathResolver().resolve(checked.definitions().getFirst().source())).isEqualTo("lookup.yaml");
    }

    private YamlSkillCatalog.CheckedDocuments check(String location)
    {
        return check(location, new PathMatchingResourcePatternResolver());
    }

    private YamlSkillCatalog.CheckedDocuments check(String location, PathMatchingResourcePatternResolver resolver)
    {
        resolver.setUseCaches(false);
        LoomspanProperties properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of(location));
        return new YamlSkillCatalog(properties, resolver, ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().skillYaml()).checkedConfigured(false);
    }

    private static void write(Path file, String contents) throws IOException
    {
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }

    private static void assertRejected(YamlSkillCatalog.CheckedDocuments checked, String relative, String component)
    {
        assertThat(checked.definitions()).isEmpty();
        assertThat(checked.issues()).singleElement().satisfies(issue -> assertThat(issue.message())
                .contains(relative, "component '" + component + "'", "uppercase"));
    }

    private static class CountingResolver extends PathMatchingResourcePatternResolver
    {
        int lookups;
        @Override public Resource[] getResources(String location) throws IOException
        {
            lookups++;
            return super.getResources(location);
        }
    }
}
