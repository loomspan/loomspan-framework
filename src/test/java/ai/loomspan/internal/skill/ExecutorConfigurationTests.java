package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExecutorConfigurationTests {
    private ExecutionConfigurationParser.Parsed parse(String section) {
        return ExecutionConfigurationParser.parse("sales", new ExecutionConfiguration("loomspan:\n" + section), new StandardEnvironment(), false);
    }
    @Test void namespaceDataIsImmutableAndDefensivelyCopied() {
        var nested = new LinkedHashMap<String, Object>();
        var list = new ArrayList<Object>(); list.add(null); list.add(Map.of());
        nested.put("List", list); nested.put("Nil", null); nested.put("literal.dot", 7);
        var source = new LinkedHashMap<String, Object>(); source.put("Sales", nested);
        var value = ExecutorConfiguration.from(source);
        nested.clear(); list.add("mutated"); source.clear();
        assertThat(value.namespaces()).containsOnlyKeys("sales");
        assertThat(value.namespaces().get("sales")).containsEntry("Nil", null).containsEntry("literal.dot", 7);
        assertThat((List<?>)value.namespaces().get("sales").get("List")).hasSize(2);
        assertThatThrownBy(() -> value.namespaces().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> value.namespaces().get("sales").clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void aliasedConfigurationDoesNotExpandIntoAnExponentialContainerTree() {
        var yaml = new StringBuilder("  executor-config:\n    sales:\n      v0: &v0 [leaf]\n");
        for (int level = 1; level <= 14; level++)
            yaml.append("      v" + level + ": &v" + level + " [*v" + (level - 1) + ", *v" + (level - 1) + "]\n");
        var data = parse(yaml.toString()).executorConfig().namespaces().get("sales");
        for (int level = 1; level <= 14; level++) {
            var items = (List<?>) data.get("v" + level);
            assertThat(items.get(0)).isSameAs(items.get(1)).isSameAs(data.get("v" + (level - 1)));
            assertThatThrownBy(items::clear).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat((List<?>)data.get("v0")).isEqualTo(List.of("leaf"));
    }
    @Test void aliasExpansionIsBoundedBeforeDownstreamTreeProcessing() {
        var yaml = new StringBuilder("sales:\n  v0: &v0 [leaf]\n");
        for (int level = 1; level <= 24; level++)
            yaml.append("  v" + level + ": &v" + level + " [*v" + (level - 1) + ", *v" + (level - 1) + "]\n");
        assertThatThrownBy(() -> ai.loomspan.internal.serialization.YamlValues.read(yaml.toString()))
                .hasMessageContaining("expanded value count", "limit");
    }
    @Test void publicationEnforcesTargetAndNeverInherits() {
        var configured = parse("  executor-config: {Sales: {database: arbitrary}}\n");
        assertThat(configured.executorConfig().namespaces().get("sales")).containsEntry("database", "arbitrary");
        assertThat(configured.properties().getExecutorConfig()).isSameAs(configured.executorConfig());
        assertThat(parse("  executor-config: {}\n").executorConfig().namespaces()).isEmpty();
        assertThat(parse("  models: {}\n").executorConfig().namespaces()).isEmpty();
        assertThatThrownBy(() -> parse("  executor-config: {foreign: {}}\n")).hasMessageContaining("foreign").hasMessageContaining("target");
    }
    @Test void structuralRulesDoNotInterpretDomainData() {
        assertThatThrownBy(() -> parse("  executor-config: {sales: &cycle {self: *cycle}}\n"))
                .isInstanceOf(IllegalArgumentException.class);
        for (String value : List.of("null", "[]", "true", "7", "{sales: null}", "{sales: []}", "{' sales': {}}", "{Sales: {}, sales: {}}"))
            assertThatThrownBy(() -> parse("  executor-config: " + value + "\n")).hasMessageContaining("executor-config");
        var parsed = parse("  executor-config: {sales: {api-key-ref: missing.property, sql: '${UNSET_EXECUTOR_VALUE}', Nil: null, empty: [], Upper: A, upper: B, literal.dot: kept}}\n");
        assertThat(parsed.executorConfig().namespaces().get("sales")).containsEntry("api-key-ref", "missing.property")
                .containsEntry("sql", "${UNSET_EXECUTOR_VALUE}").containsEntry("Nil", null)
                .containsEntry("Upper", "A").containsEntry("upper", "B").containsEntry("literal.dot", "kept");
        assertThatThrownBy(() -> ExecutionConfigurationParser.parse("sales", new ExecutionConfiguration("loomspan: {executor-config: {sales: {api-key-ref: unused}}}"), Map.of("unused", "value")))
                .hasMessageContaining("unused reference");
        assertThat(ExecutorConfiguration.from(Map.of("sales", Map.of(), "infrastructure", Map.of())).namespaces()).hasSize(2);
    }
}
