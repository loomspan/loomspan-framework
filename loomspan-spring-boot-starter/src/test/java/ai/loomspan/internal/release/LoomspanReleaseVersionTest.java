package ai.loomspan.internal.release;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoomspanReleaseVersionTest
{
    @Test
    void loadsCompleteFilteredMavenReleaseIncludingQualifier()
    {
        assertThat(LoomspanReleaseVersion.load()).isEqualTo("1.0.0-beta.2");
    }
}
