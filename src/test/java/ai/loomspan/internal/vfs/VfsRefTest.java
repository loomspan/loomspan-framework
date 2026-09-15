package ai.loomspan.internal.vfs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VfsRefTest {

    @Test
    void parseRejectsMissingSchemeOrEmptyPayload() {
        assertThatThrownBy(() -> VfsRef.parse("artifacts/file.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed ref")
                .hasMessageContaining("ref://");

        assertThatThrownBy(() -> VfsRef.parse("ref://"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed ref")
                .hasMessageContaining("must not be empty");
    }

    @Test
    void parsePreservesCanonicalLogicalPathForValidRefs() {
        VfsRef ref = VfsRef.parse("ref://artifacts/reports/2026/summary.txt");

        assertThat(ref.raw()).isEqualTo("ref://artifacts/reports/2026/summary.txt");
        assertThat(ref.relativePath()).isEqualTo("artifacts/reports/2026/summary.txt");
    }

    @Test
    void parseCanonicalizesEquivalentLogicalPaths() {
        VfsRef ref = VfsRef.parse("ref://artifacts/reports/./2026\\drafts/../summary.txt");

        assertThat(ref.relativePath()).isEqualTo("artifacts/reports/2026/summary.txt");
    }

    @Test
    void parseRejectsRefsThatCollapseToEmptyLogicalPaths() {
        assertThatThrownBy(() -> VfsRef.parse("ref://./"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed ref")
                .hasMessageContaining("must not be empty");

        assertThatThrownBy(() -> VfsRef.parse("ref://artifacts/.."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed ref")
                .hasMessageContaining("must not be empty");
    }
}
