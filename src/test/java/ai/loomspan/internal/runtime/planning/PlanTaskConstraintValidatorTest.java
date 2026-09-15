package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.skill.AllowedSkillConstraint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTaskConstraintValidatorTest
{
    private final PlanTaskConstraintValidator validator = new PlanTaskConstraintValidator();

    @Test
    void countsExactBindingsAndAcceptsInclusiveBoundaries()
    {
        ExecutionPlan plan = plan(task("one", "invoiceParser"), task("two", "invoiceParser"),
                task("wrong-case", "InvoiceParser"), task("blank", ""), task("null", null));

        PlanTaskConstraintValidationResult result = validator.validate(plan,
                List.of(new AllowedSkillConstraint("invoiceParser", 2, 2, false),
                        new AllowedSkillConstraint("optionalLookup", null, 1, false)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void requiredAndMaximumViolationsRetainManifestOrderAndStructuredFacts()
    {
        ExecutionPlan plan = plan(task("one", "overused"), task("two", "overused"));

        PlanTaskConstraintValidationResult result = validator.validate(plan,
                List.of(new AllowedSkillConstraint("requiredChild", null, null, true),
                        new AllowedSkillConstraint("overused", null, 1, false)));

        assertThat(result.issues()).extracting(PlanTaskConstraintIssue::code)
                .containsExactly(PlanTaskConstraintValidator.MINIMUM_NOT_MET,
                        PlanTaskConstraintValidator.MAXIMUM_EXCEEDED);
        assertThat(result.issues()).extracting(PlanTaskConstraintIssue::skillName)
                .containsExactly("requiredChild", "overused");
        assertThat(result.issues().getFirst().effectiveMinTasks()).isEqualTo(1);
        assertThat(result.issues().getFirst().actualTaskCount()).isZero();
        assertThat(result.issues().getLast().maxTasks()).isEqualTo(1);
        assertThat(result.issues().getLast().actualTaskCount()).isEqualTo(2);
    }

    @Test
    void taskProseAndStatusDoNotAffectCounts()
    {
        PlanTask misleading = new PlanTask("one", "Use another skill", PlanTaskStatus.COMPLETED,
                "exactChild", "Never mention exactChild", List.of("missing"), List.of("unrelated"), null, "ignored");

        assertThat(validator.validate(plan(misleading),
                List.of(new AllowedSkillConstraint("exactChild", 1, 1, false))).issues()).isEmpty();
    }

    private static ExecutionPlan plan(PlanTask... tasks)
    {
        return new ExecutionPlan("plan", "root", Instant.parse("2026-08-22T00:00:00Z"), List.of(tasks));
    }

    private static PlanTask task(String id, String capabilityName)
    {
        return new PlanTask(id, "title", PlanTaskStatus.PENDING, capabilityName,
                "intent", List.of(), List.of(), null, null);
    }
}
