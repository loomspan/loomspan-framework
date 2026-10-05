package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PlanOutputBindingProducerValidatorTest {
    private final PlanOutputBindingProducerValidator validator = new PlanOutputBindingProducerValidator();
    private final List<ChildInputBinding> bindings = List.of(binding("/first", "producer"), binding("/second", "producer"));
    private static ChildInputBinding binding(String path, String producer) {
        return new ChildInputBinding(ObjectFieldPath.parse(path, false), ChildInputBinding.SourceKind.CHILD_RESULT,
                ObjectFieldPath.parse("", true), producer);
    }
    private static PlanTask task(String id, String skill) {
        return new PlanTask(id, id, PlanTaskStatus.PENDING, skill, "work", List.of(), List.of(), "parallel", null);
    }
    private static ExecutionPlan plan(PlanTask... tasks) { return new ExecutionPlan("p", "parent", Instant.EPOCH, List.of(tasks)); }
    @Test void uniqueProducerIsUnconditionalAndRepeatedBindingsReuseIt() {
        assertThat(validator.validate(plan(task("a", "other")), bindings)).singleElement().asString().contains("found 0");
        assertThat(validator.validate(plan(task("a", "producer"), task("b", "producer")), bindings)).singleElement().asString().contains("found 2");
        assertThat(validator.validate(plan(task("a", "producer"), task("b", "other")), bindings)).isEmpty();
        assertThat(PlanOutputBindingProducerValidator.render(bindings)).contains("even when", "no dependency edges");
    }
    @Test void inputOnlyDoesNotRequireProducer() {
        var input = new ChildInputBinding(ObjectFieldPath.parse("/id", false), ChildInputBinding.SourceKind.INPUT,
                ObjectFieldPath.parse("/id", true), null);
        assertThat(validator.validate(plan(), List.of(input))).isEmpty();
    }
}
