package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.skill.AllowedSkillConstraint;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PlanInputBindingDependencyValidatorTest {
    private final PlanInputBindingDependencyValidator validator = new PlanInputBindingDependencyValidator();
    private final List<AllowedSkillConstraint> constraints = List.of(new AllowedSkillConstraint("consumer", null, null, false,
            List.of(new ChildInputBinding(ObjectFieldPath.parse("/evidence", false), ChildInputBinding.SourceKind.CHILD_RESULT,
                    ObjectFieldPath.parse("", true), "producer"))));
    private static PlanTask task(String id, String skill, List<String> dependencies, String group) {
        return new PlanTask(id, id, PlanTaskStatus.PENDING, skill, "work", dependencies, List.of(), group, null);
    }
    private static ExecutionPlan plan(PlanTask... tasks) { return new ExecutionPlan("p", "parent", Instant.EPOCH, List.of(tasks)); }
    @Test void requiresUniqueProducerExplicitDirectEdgeAndEarlierUnit() {
        var producer = task("p1", "producer", List.of(), null);
        var consumer = task("c1", "consumer", List.of("p1"), null);
        assertThat(validator.validate(plan(producer, consumer), constraints)).isEmpty();
        assertThat(validator.validate(plan(consumer), constraints)).anyMatch(issue -> issue.contains("exactly one"));
        assertThat(validator.validate(plan(producer, task("p2", "producer", List.of(), null), consumer), constraints)).anyMatch(issue -> issue.contains("found 2"));
        assertThat(validator.validate(plan(producer, task("c1", "consumer", List.of(), null)), constraints)).anyMatch(issue -> issue.contains("explicitly"));
        assertThat(validator.validate(plan(consumer, producer), constraints)).anyMatch(issue -> issue.contains("earlier"));
        assertThat(validator.validate(plan(task("p1", "producer", List.of(), "group"), task("c1", "consumer", List.of("p1"), "group")), constraints)).anyMatch(issue -> issue.contains("earlier"));
    }
    @Test void permitsOptionalConsumerAbsenceAndIndependentProducerGroups() {
        assertThat(validator.validate(plan(task("x", "other", List.of(), null)), constraints)).isEmpty();
        assertThat(validator.validate(plan(task("p1", "producer", List.of(), "group"), task("other", "other", List.of(), "group"),
                task("c1", "consumer", List.of("p1"), null)), constraints)).isEmpty();
        assertThat(PlanInputBindingDependencyValidator.render(constraints)).contains("exactly one", "earlier execution unit", "effective argument schema");
    }
}
