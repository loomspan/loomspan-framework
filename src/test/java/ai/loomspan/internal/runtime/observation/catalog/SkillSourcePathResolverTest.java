package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.skill.YamlSkillSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSourcePathResolverTest
{
    @ParameterizedTest
    @ValueSource(strings = {"opaque label", "support/triage.yaml", "scheme:value <untrusted>"})
    void suppliedLabelIsReturnedVerbatimWithoutPathInterpretation(String label)
    {
        YamlSkillSource source = new YamlSkillSource(new ByteArrayResource(new byte[0], label),
                new byte[0], label);
        assertThat(new SkillSourcePathResolver().resolve(source)).isEqualTo(label);
    }

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"classpath:", "classpath*:"})
    void exactClasspathLocationUsesOnlyFilename(String prefix) throws Exception
    {
        String location = prefix + "skills/valid/prompt-skill.yaml";
        PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();
        Resource[] matches = resources.getResources(location);
        assertThat(matches).hasSize(1);
        YamlSkillSource source = new YamlSkillSource(matches[0], location, matches[0].getContentAsByteArray());

        assertThat(new SkillSourcePathResolver(resources).resolve(source)).isEqualTo("prompt-skill.yaml");
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath:", "classpath*:"})
    void wildcardClasspathLocationProducesRootRelativePath(String prefix) throws Exception
    {
        String location = prefix + "skills/**/prompt-skill.yaml";
        PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();
        Resource[] matches = resources.getResources(location);
        assertThat(matches).hasSize(1);
        YamlSkillSource source = new YamlSkillSource(matches[0], location, matches[0].getContentAsByteArray());

        assertThat(new SkillSourcePathResolver(resources).resolve(source)).isEqualTo("valid/prompt-skill.yaml");
    }

    @Test
    void exactFileUsesOnlyFilenameAndSourceBytesAreDefensive() throws Exception
    {
        Path file = Files.writeString(tempDir.resolve("skill.yaml"), "name: skill\r\n");
        byte[] bytes = Files.readAllBytes(file);
        YamlSkillSource source = new YamlSkillSource(
                new FileSystemResource(file),
                file.toUri().toString(),
                bytes);
        bytes[0] = 'X';

        assertThat(new SkillSourcePathResolver().resolve(source)).isEqualTo("skill.yaml");
        assertThat(source.bytes()).startsWith((byte) 'n');
        byte[] returned = source.bytes();
        returned[0] = 'Y';
        assertThat(source.bytes()).startsWith((byte) 'n');
    }

    @Test
    void filesystemPatternProducesRootRelativeNormalizedPath() throws Exception
    {
        Path nested = Files.createDirectories(tempDir.resolve("nested"));
        Path file = Files.writeString(nested.resolve("skill.yaml"), "name: skill\n");
        String root = tempDir.toUri().toString();
        YamlSkillSource source = new YamlSkillSource(
                new FileSystemResource(file),
                root + "**/*.yaml",
                Files.readAllBytes(file));

        assertThat(new SkillSourcePathResolver().resolve(source)).isEqualTo("nested/skill.yaml");
    }
}
