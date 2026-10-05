package ai.loomspan.internal.runtime.input;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ChildInputBindingTest {
    @Test void parsesExactObjectKeysAndRoot() {
        assertThat(ObjectFieldPath.parse("/a~1b/~0/.dot/01/", false).tokens()).containsExactly("a/b", "~", ".dot", "01", "");
        assertThat(ObjectFieldPath.parse("", true).tokens()).isEmpty();
        for (String invalid : List.of("", "a.b", "/~", "/~2"))
            assertThatThrownBy(() -> ObjectFieldPath.parse(invalid, false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsProducerCyclesImpossibleCountsAndKnownInvalidPaths() {
        var parent = new SkillInputContractResolver().resolveFromToolSchema("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},\"additionalProperties\":false}");
        var consumer = new Allowed("consumer", result("/data", "producer"));
        var constraints = List.of(consumer.constraint(), new ai.loomspan.internal.skill.AllowedSkillConstraint("producer", 2, null, false, List.of(result("/x", "consumer"))));
        var issues = ChildInputBindingDeclarations.validate(parent, constraints, Map.of("consumer", parent));
        assertThat(issues).anyMatch(issue -> issue.contains("closed object")).anyMatch(issue -> issue.contains("exactly one")).anyMatch(issue -> issue.contains("cyclic"));
    }
    @Test void skipsConditionalProducerCardinalityWhenConsumerCannotBePlanned() {
        var consumer = new ai.loomspan.internal.skill.AllowedSkillConstraint("consumer", null, 0, false, List.of(result("/data", "producer")));
        var producer = new ai.loomspan.internal.skill.AllowedSkillConstraint("producer", 2, null, false, List.of());
        assertThat(ChildInputBindingDeclarations.validate(SkillInputContract.genericObject(), List.of(consumer, producer), Map.of())).isEmpty();
    }
    private record Allowed(String name, ChildInputBinding binding) {
        ai.loomspan.internal.skill.AllowedSkillConstraint constraint() { return new ai.loomspan.internal.skill.AllowedSkillConstraint(name, null, null, false, List.of(binding)); }
    }
    private static ChildInputBinding result(String target, String producer) {
        return new ChildInputBinding(ObjectFieldPath.parse(target, false), ChildInputBinding.SourceKind.CHILD_RESULT, ObjectFieldPath.parse("", true), producer);
    }
}
