# PR 20 — Return a designated child result without model synthesis

## Outcome

Allow a planning skill to declare that its output is the result of one designated
child task. The parent still orchestrates meaningful work: earlier tasks gather
or prepare inputs, and a later task produces the complete answer. Once required
work succeeds, Framework returns that answer directly instead of making a model
call whose only responsibility is to reproduce it.

This is an explicit alternative to parent synthesis, applicable to model, Java
and REST children. It removes an unnecessary copying opportunity and model call;
it does not claim to improve the designated child's reasoning or business accuracy.

## Configuration and behavior

Introduce this author-facing YAML declaration for planning skills:

```yaml
name: prepareRecommendation
planning_mode: true

# Existing input_schema, orchestration prompt and other allowed skills remain.
allowed_skills:
  - name: compareOptions
    required: true
    max_tasks: 1

output_from:
  skill: compareOptions
```

The example shows only the relevant configuration. Its selected child is one of
the parent's directly allowed skills, not a global result lookup or a reference
to an arbitrary descendant.

- `output_from` and an authored parent `output_schema` are mutually exclusive.
  Existing skills without `output_from` retain their current completion and
  output-validation behavior. This feature is opt-in and requires no migration
  of existing definitions.
- The designated child must be declared with `required: true` and `max_tasks: 1`.
  Validate that configuration and enforce exactly one matching task in the
  parent's plan. Select the concrete task result within that parent execution,
  not by a global skill-name match or model-generated free-text reference.
  Retries/corrections within the one task remain subject to existing behavior;
  they do not create multiple selectable task results.
- Support a designated model, Java or REST skill uniformly through its Framework
  task result. Forward the actual result exposed by that skill's existing
  execution contract, including existing Java/REST result handling; do not invent
  an envelope, unwrap a business field or return raw transport metadata instead.
- The designated task and the parent's required work must complete successfully
  before parent success. Do not return early merely because the selected child
  has finished, skip required work, or bypass normal lifecycle, authorization,
  failure, cancellation or execution-limit behavior.
- Missing, failed or ambiguous designated results cannot produce parent success.
  Do not silently fall back to model synthesis, choose the first/last match or
  substitute another child's result. Reject invalid declarations/plans through
  the appropriate existing configuration/planning validation path.
- Forward the exact selected result without model synthesis, transformation,
  field filtering, citation augmentation or content rewriting. For structured
  values this means decoded-value fidelity, including array order, rather than
  a promise about JSON whitespace. Preserve the existing result representation
  and semantics for other result types.

## Output contract metadata and validation

Derive the parent's effective output-schema metadata from the designated child
when that child has an available schema. This describes what callers, tooling and
other planners can expect; it is not an additional validation stage and does not
require authors to duplicate the schema.

The child retains its existing validation and correction behavior. Do not run
the same schema validator again on the unchanged forwarded result or request a
parent output-correction response. Do not assume Java or REST skills have a
declared output schema or currently validate their output. When no schema is
available, forwarding still works and the effective output schema is unspecified;
do not fabricate a schema, validation guarantee or new child-validation policy.

Support chained forwarding: a parent's selected child can itself use
`output_from`. Derive effective metadata consistently through that chain and
reject cyclic forwarding definitions rather than recursing indefinitely. Bind
result selection and metadata to the definitions used by the execution so that
unrelated executions or configuration changes cannot substitute another result.
Implementation mechanisms remain for the pipeline to determine.

## Skill documentation library

Update the repository's skill documentation library, including the LLM-facing
authoring guidance and applicable YAML/reference/examples, as part of this feature.
The guidance must explain:

- Choosing direct forwarding when one child produces the complete parent answer,
  versus retaining synthesis and a parent output schema when the parent must
  combine or interpret results.
- The orchestration value of a parent before completion, and why a wrapper with
  no meaningful responsibility should be reconsidered rather than automatically
  receiving another reasoning layer.
- The new syntax, mutual exclusion, unique required child, direct-child scope,
  completion/failure semantics and unchanged-result guarantee.
- Model, Java and REST examples; schemas as derived metadata, no duplicate
  validation, and forwarding when the child's output schema is unspecified.
- Removing instructions to reproduce the final child result from forwarding
  skills while retaining their genuine orchestration responsibilities.

Keep examples and recommendations general and model-independent. Do not include
the equipment-service test case, provider-specific workarounds or worked test
answers in reusable authoring guidance. Documentation must describe the shipped
behavior and its limitations, not promise universal model accuracy.

## Acceptance criteria

- [ ] A planning parent can perform prerequisite work and return its unique
  designated model, Java or REST child's result unchanged, with no parent final
  synthesis call. A forwarding chain preserves the same result semantics.
- [ ] Parent success waits for required work and child success. Missing/failed
  results, ambiguous selection, cancellation and ordinary execution failures do
  not produce a successful forwarded output or silently trigger synthesis.
- [ ] Invalid or conflicting declarations and plans are rejected, including an
  unavailable/non-direct child, missing uniqueness requirements, both output
  declarations, and cyclic forwarding. Concurrent executions select their own
  task results; applicable definition identity and access boundaries are retained.
- [ ] Effective schema metadata is derived when available and remains unspecified
  otherwise. The child's normal validation remains intact; forwarding adds no
  duplicate validation, parent correction loop or new Java/REST validation policy.
- [ ] Existing skills using ordinary synthesis behave as before. No unsupported
  public Java API or extension surface is introduced incidentally.
- [ ] Diagnostics explain that completion used the designated task's result and
  identify that task without attributing a nonexistent parent model response.
- [ ] The skill documentation library and relevant references/examples teach the
  supported configuration, selection rules, schema semantics, limitations and
  choice between forwarding and synthesis for all three child types.

## Context and scope

A reference-suite observation motivating this proposal was a parent adding four
valid citations to a completed child's decision despite an exact-copy requirement.
The parent still had useful upstream orchestration responsibilities. This is an
observed copying deviation, not proof that direct forwarding is already supported
or a verified fix. Broader business reasoning errors remain outside this feature.

The initial scope excludes multiple-invocation selectors, field extraction,
transformation expressions, aggregation, automatic detection of forwarding intent,
and automatic fallback to synthesis. Authors opt in through `output_from`.
No reference-suite prompt tuning, replay refresh or application migration is part
of this ticket. The pipeline should discover implementation locations and concrete
verification commands; this ticket fixes product intent, not internal architecture.

PR 20 is the user-supplied correlation identifier. Creating this local ticket does
not create or establish the state of a GitHub pull request.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The feature changes user-visible YAML and planning/completion
  semantics across skill types, with lifecycle, result selection and schema
  metadata implications. Research, planning, verification design and independent
  review are warranted even though the intended behavior is settled.
- **Reassessment triggers:** Discoveries concerning existing forwarding behavior,
  configuration versioning, lifecycle or supported API compatibility must be
  assessed during pipeline triage. They do not authorize silently changing the
  agreed output behavior or introducing new public extension contracts.
