package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SkillIdentityTest {
    @Test void canonicalizesOnlyNamespace() {
        assertThat(SkillIdentity.parse("Tax-search")).isEqualTo(SkillIdentity.parse("tax-search"));
        assertThat(SkillIdentity.parse("tax-Search")).isNotEqualTo(SkillIdentity.parse("tax-search"));
        assertThat(SkillIdentity.parse("a_b-c")).isNotEqualTo(SkillIdentity.parse("a-b_c"));
        assertThat(SkillIdentity.normalizeNamespace("I")).isEqualTo("i");
        assertThat(SkillIdentity.parse("_A-_B1").toString()).isEqualTo("_a-_B1");
    }
    @ParameterizedTest @ValueSource(strings={"", "search", "a/b", "a:b", "a-b-c", "-b", "a-", " a-b", "a-b ", "a-b c", "1a-b", "a-1b", "a-*", "a-%62", "a-\u00e9"})
    void rejectsMalformedWithoutRepair(String value) {
        assertThatThrownBy(() -> SkillIdentity.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void enforcesIndependentAndCombinedLimits() {
        String owner = "n".repeat(32);
        assertThat(new SkillIdentity(owner, "s".repeat(31)).toString()).hasSize(64);
        assertThat(new SkillIdentity("n", "s".repeat(62)).toString()).hasSize(64);
        assertThatThrownBy(() -> new SkillIdentity(owner + "n", "x")).hasMessageContaining("length 33").hasMessageContaining("limit 32");
        assertThatThrownBy(() -> new SkillIdentity(owner, "s".repeat(32))).hasMessageContaining("length 65").hasMessageContaining("limit 64");
        assertThatThrownBy(() -> SkillIdentity.normalizeNamespace(owner + "n")).hasMessageContaining("limit 32");
        SkillIdentity.validateLocalName("s".repeat(64));
        assertThatThrownBy(() -> new SkillIdentity("default", "s".repeat(57))).hasMessageContaining("length 65");
        assertThat(new SkillIdentity("default", "s".repeat(56)).toString()).hasSize(64);
    }
}
