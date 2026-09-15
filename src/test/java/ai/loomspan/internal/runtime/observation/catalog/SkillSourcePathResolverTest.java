package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.skill.YamlSkillSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSourcePathResolverTest
{
    @TempDir
    Path tempDir;

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
