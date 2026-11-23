package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SkillIdentityTest {
    @Test void acceptsPortableHyphenatedPair() {
        SkillIdentity identity = new SkillIdentity("order-support", "lookup-3pl");
        assertThat(identity.namespace()).isEqualTo("order-support");
        assertThat(identity.localName()).isEqualTo("lookup-3pl");
        assertThat(identity.toString()).isEqualTo("order-support:lookup-3pl");
        assertThat(SkillIdentity.parse(identity.toString())).isEqualTo(identity);
        assertThat(SkillIdentity.resolve("lookup-3pl", "orders")).isEqualTo(new SkillIdentity("orders", "lookup-3pl"));
        assertThat(SkillIdentity.resolve("warehouse:lookup", "orders")).isEqualTo(new SkillIdentity("warehouse", "lookup"));
    }
    @Test void rejectsNamespaceCaseChanges() {
        assertThatThrownBy(() -> new SkillIdentity("Orders", "lookup")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("namespace", "Orders");
    }
    @ParameterizedTest @ValueSource(strings={"a", "lookup", "lookup-3pl", "order-support", "a0", "a-0"})
    void acceptsPortableComponents(String value) {
        assertThat(SkillIdentity.requireNamespace(value)).isEqualTo(value);
        SkillIdentity.validateLocalName(value);
    }
    @ParameterizedTest @ValueSource(strings={"", "3pl-lookup", "Lookup", "LOOKUP", " lookup", "lookup ", "look up", "lookup\n", "lookup\r\n", "_lookup", "lookup_name", "-lookup", "lookup-", "look--up", "look/up", "look:up", "é", "myclaudeskill", "anthropic-tools"})
    void rejectsMalformedComponentsWithoutRepair(String value) {
        assertThatThrownBy(() -> new SkillIdentity(value, "lookup")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("namespace", value);
        assertThatThrownBy(() -> new SkillIdentity("orders", value)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("local name", value);
    }
    @Test void rejectsNullComponents() {
        assertThatThrownBy(() -> new SkillIdentity(null, "lookup")).hasMessageContaining("namespace");
        assertThatThrownBy(() -> new SkillIdentity("orders", null)).hasMessageContaining("local name");
        assertThatThrownBy(() -> SkillIdentity.parse(null)).hasMessageContaining("reference");
    }
    @ParameterizedTest @ValueSource(strings={"", "search", "a-b", "a/b", ":b", "a:", "a:b:c", " a:b", "a:b ", "1a:b", "a:1b", "a:*", "a:%62", "a:é"})
    void rejectsMalformedQualifiedReferences(String value) {
        assertThatThrownBy(() -> SkillIdentity.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void preservesDistinctHyphenBearingPairs() {
        var first = new SkillIdentity("a-b", "c");
        var second = new SkillIdentity("a", "b-c");
        assertThat(first).isNotEqualTo(second);
        assertThat(first.toString()).isNotEqualTo(second.toString());
        assertThat(SkillIdentity.parse(first.toString())).isEqualTo(first);
        assertThat(SkillIdentity.parse(second.toString())).isEqualTo(second);
    }
    @Test void enforcesIndependentAndCombinedLimits() {
        SkillIdentity.requireNamespace("n".repeat(64));
        SkillIdentity.validateLocalName("s".repeat(64));
        assertThatThrownBy(() -> SkillIdentity.requireNamespace("n".repeat(65))).hasMessageContaining("length 65", "limit 64");
        assertThatThrownBy(() -> SkillIdentity.validateLocalName("s".repeat(65))).hasMessageContaining("length 65", "limit 64");
        assertThat(new SkillIdentity("order-support", "s".repeat(49)).toString()).hasSize(63);
        assertThatThrownBy(() -> new SkillIdentity("order-support", "s".repeat(50))).hasMessageContaining("length 65", "limit 64");
        assertThat(new SkillIdentity("n", "s".repeat(61)).toString()).hasSize(63);
        assertThat(new SkillIdentity("n".repeat(61), "s").toString()).hasSize(63);
        assertThatThrownBy(() -> new SkillIdentity("n", "s".repeat(62))).hasMessageContaining("length 65");
    }
}
