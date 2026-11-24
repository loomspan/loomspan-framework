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
        ExecutionPlan plan = plan(task("one", "default:invoice-parser"), task("two", "default:invoice-parser"),
                task("wrong-case", "default:Invoice-parser"), task("blank", ""), task("null", null));

        PlanTaskConstraintValidationResult result = validator.validate(plan,
                List.of(new AllowedSkillConstraint("default:invoice-parser", 2, 2, false, java.util.List.of()),
                        new AllowedSkillConstraint("default:optional-lookup", null, 1, false, java.util.List.of())));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void requiredAndMaximumViolationsRetainManifestOrderAndStructuredFacts()
    {
        ExecutionPlan plan = plan(task("one", "default:overused"), task("two", "default:overused"));

        PlanTaskConstraintValidationResult result = validator.validate(plan,
                List.of(new AllowedSkillConstraint("default:required-child", null, null, true, java.util.List.of()),
                        new AllowedSkillConstraint("default:overused", null, 1, false, java.util.List.of())));

        assertThat(result.issues()).extracting(PlanTaskConstraintIssue::code)
                .containsExactly(PlanTaskConstraintValidator.MINIMUM_NOT_MET,
                        PlanTaskConstraintValidator.MAXIMUM_EXCEEDED);
        assertThat(result.issues()).extracting(PlanTaskConstraintIssue::skillName)
                .containsExactly("default:required-child", "default:overused");
        assertThat(result.issues().getFirst().effectiveMinTasks()).isEqualTo(1);
        assertThat(result.issues().getFirst().actualTaskCount()).isZero();
        assertThat(result.issues().getLast().maxTasks()).isEqualTo(1);
        assertThat(result.issues().getLast().actualTaskCount()).isEqualTo(2);
    }

    @Test
    void taskProseAndStatusDoNotAffectCounts()
    {
        PlanTask misleading = new PlanTask("one", "Use another skill", PlanTaskStatus.COMPLETED,
                "default:exact-child", "Never mention exactChild", List.of("missing"), List.of("unrelated"), null, "ignored");

        assertThat(validator.validate(plan(misleading),
                List.of(new AllowedSkillConstraint("default:exact-child", 1, 1, false, java.util.List.of()))).issues()).isEmpty();
    }

    private static ExecutionPlan plan(PlanTask... tasks)
    {
        return new ExecutionPlan("plan", "default:root", Instant.parse("2026-08-22T00:00:00Z"), List.of(tasks));
    }

    private static PlanTask task(String id, String capabilityName)
    {
        return new PlanTask(id, "title", PlanTaskStatus.PENDING, capabilityName,
                "intent", List.of(), List.of(), null, null);
    }
}
