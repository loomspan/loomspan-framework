---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Planning Task Constraints

## Applicability

Use structured `allowed_skills` entries to declare a planning parent's direct children. Add task-count fields only when a `planning_mode: true` parent must unconditionally constrain how many generated plan tasks bind to a child.

```yaml
planning_mode: true
allowed_skills:
  - name: invoiceParser
    required: true
  - name: expenseLookup
    min_tasks: 1
    max_tasks: 2
  - name: optionalReviewer
```

Scalar entries and scalar flow lists are invalid. An unconstrained child is still an object with `name`.

## Fields and defaults

| Field | Required | Default | Enforced meaning |
| --- | --- | --- | --- |
| `name` | Yes | None | Exact, case-sensitive registered Java or YAML name of the direct child. |
| `min_tasks` | No | `0` | Minimum generated plan tasks bound to `name`. |
| `max_tasks` | No | Unbounded | Maximum generated plan tasks bound to `name`; zero is allowed. |
| `required` | No | `false` | When true, makes the effective minimum at least one. |

The effective minimum is `max(min_tasks if present else 0, required == true ? 1 : 0)`. Therefore `required: false` does not negate `min_tasks`, and `max_tasks: 1` permits zero or one task.

Names MUST be nonblank, unique within the parent, and match `^[A-Za-z_][A-Za-z0-9_]{0,63}$`. Bounds MUST be non-negative integers, `required` MUST be Boolean, and `max_tasks` MUST NOT be below the effective minimum. Unknown fields, null entries, mixed scalar/object sequences, and explicit null field values are rejected with an indexed `allowed_skills[index]...` path.

Declaring `min_tasks`, `max_tasks`, or `required` requires the parent to explicitly declare `planning_mode: true`; this includes `required: false`. A name-only object remains valid on an LLM-backed direct skill.

All exact child references are resolved at completed startup registration. Missing children fail startup independently of authorization. Java and YAML children use the same generated-plan counting and successful-completion rules.

## Runtime semantics

Loomspan renders every non-default bound in the initial planning prompt. After each parsed plan, it counts tasks whose non-null `PlanTask.capabilityName` exactly equals the declared child name. Matching is case-sensitive. Task title, intent, expected outputs, status, dependencies, child description, and runtime tool-call count do not affect the count.

A child with a positive effective minimum MUST already be in the authorized visible tool list. Otherwise planning fails before the model call. Maximum-only and unconstrained invisible children do not make the plan impossible and do not trigger this preflight. Constraints never expand visibility or bypass RBAC.

On the first count violation, Loomspan records `PLAN_VALIDATION_FAILED`, adds feedback with the exact child, bound, and actual count, records `PLAN_RETRY_REQUESTED`, and makes one corrective planning attempt. Count feedback composes with evidence-coverage feedback in that same retry. If either validator still fails, planning terminates before `PLAN_CREATED`, plan storage, or execution. Minimum and maximum failures use `PLAN_TASK_MINIMUM_NOT_MET` and `PLAN_TASK_MAXIMUM_EXCEEDED`.

## Boundary from evidence

Task constraints express unconditional generated-plan cardinality. Property-level `evidence` expressions express which successful direct children must support a particular output claim. Task constraints do not alter the evidence expression language or truth sets, and evidence does not imply a general minimum or maximum for an allowed child. Read [evidence-contracts.md](evidence-contracts.md) when output supportability is the requirement.

## Authoring procedure

1. List every direct child as an object with its exact `name`.
2. Add a positive minimum only when every execution must plan that child and current visibility/RBAC can satisfy it.
3. Use a maximum only when the generated plan must cap task bindings; remember that maximum-only children remain optional.
4. Check `effectiveMin <= max_tasks` and declare `planning_mode: true` for any task-count field.
5. Test the boundary counts, a corrected first violation, exhausted retry, and positive-minimum visibility failure.
6. Model property supportability separately with `evidence`.

## Known limits

Task-count constraints cannot express conditional branches, ordering, dependencies, cross-child groups, runtime invocation limits, input/output-dependent bounds, or task roles. Generated plans separately support validated `parallelGroup` and `dependsOn` metadata; read [planning-concurrency.md](planning-concurrency.md). Counts remain independent of those fields and retain ordinary exact `capabilityName` bindings.

## Implementation and test anchors

- `YamlSkillManifest.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java`) defines the structured entry shape; `YamlSkillCatalog.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`) validates and normalizes it.
- `PlanTaskConstraintValidator.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/planning/PlanTaskConstraintValidator.java`) defines exact counting and stable issue codes.
- `DefaultPlanningService.java` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/planning/DefaultPlanningService.java`) owns prompt rendering, visibility preflight, retry composition, trace facts, and terminal rejection.
- `YamlSkillCatalogTests`, `PlanTaskConstraintValidatorTest`, and `PlanningServiceTest` protect syntax/defaults, exact counts, prompt, preflight, retry, exhaustion, and plan-storage exclusion.
