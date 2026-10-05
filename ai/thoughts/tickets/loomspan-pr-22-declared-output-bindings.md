# PR 22 — Assemble skill outputs through declared bindings

## Outcome

Add top-level `output_bindings` to model skill declarations so Framework can place
validated current input and accepted direct-child results into the skill's output
fields without asking a model to reproduce those values. Support an output tree
containing results from multiple children, and mixed outputs whose unbound fields
still require model reasoning. Entirely bound outputs require no final model
synthesis call.

Human comprehension is a primary design requirement: output bindings must mirror
PR21 `input_bindings` in vocabulary, mapping shape, source selection, value fidelity,
source isolation and override protection. The difference is the destination:
input bindings populate an allowed child's input; output bindings populate the
current skill's output. Do not introduce a destination-list dialect or `ref://`
syntax for this feature.

22 is the developer-assigned proposed PR identifier. This ticket does not assert
that GitHub PR 22 already exists.

## Requirements

### Declaration and source semantics

Use the existing destination-keyed mapping shape:

```yaml
# Existing child input binding, inside a planning parent:
allowed_skills:
  - name: planResolution
    required: true
    max_tasks: 1
    input_bindings:
      /context/equipmentAssessment:
        from: child_result
        skill: assessEquipment
        path: ""
```

The corresponding output declaration belongs at the current skill's top level:

```yaml
output_bindings:
  /caseId:
    from: input
    path: /caseId
  /equipmentAssessment:
    from: child_result
    skill: assessEquipment
    path: ""
  /recommendation:
    from: child_result
    skill: planResolution
    path: ""
```

- Each mapping key is a destination JSON Pointer into the current output.
  `from: input` selects the validated input of this exact skill invocation.
  `from: child_result` selects an accepted successful direct-child result owned
  by this invocation; `skill` identifies the producer. `path` selects inside
  the source and the empty string selects the whole source.
- Reuse input-binding descriptor validation and object-pointer semantics:
  nonempty destination pointers, escaped object keys, no array indexing,
  wildcards, transforms or interpolation. Whole arrays may be selected and must
  retain their order. Reject duplicate/overlapping destinations, duplicate YAML
  keys and malformed/unknown descriptor fields. Root replacement remains the
  job of `output_from`, not an empty destination binding.
- Reuse PR21 accepted-result decoding and exact-value semantics, including large
  numbers, null versus absence, nested JSON-looking strings, and plain-text child
  results. Preserve selected JSON values; do not require preserving the lexical
  whitespace of JSON embedded within a newly assembled object. Existing
  `output_from` exact retained-text behavior is unchanged.
- Input-only output bindings must also work for an ordinary model skill without
  planning children. This lets a comparison skill bind `/quotes` from its own
  `/context/quotes` and `/equipmentAssessment` from its own accepted input while
  generating a recommendation. Direct-child bindings require a planning parent
  and real child work; they cannot access grandchildren or another invocation.
- Require a declared object output contract for assembled output. Validate
  statically knowable destinations, source paths and type compatibility against
  the captured configuration generation; preserve PR21 runtime validation for
  open/unknown source shapes. Do not invent a producer schema from a Java return
  type or REST response text.

### Whole output versus assembled output

`output_from` and `output_bindings` are mutually exclusive. Reject a declaration
containing both during skill configuration validation, with a diagnostic naming
both fields; never choose precedence silently. Preserve existing `output_from`
syntax and behavior. Declaring neither retains normal model-generated output.

A fully bound output is assembled without a final model request. In a planning
parent this skips final synthesis, not planning or required child execution.
When schema projection leaves model-contributed output fields, retain the normal
model phase but request only those unbound fields. Projection must preserve all
unbound constraints, including optional ancestors and required unbound siblings,
using the same ownership principles as input bindings. A bound ancestor supplies
its whole subtree; the model cannot contribute additional fields inside it.

After collecting any model contribution, Framework creates detached output
containers, inserts all selected values, and validates the complete assembled
object against the original output contract before returning/publishing it.
Bound values must not undergo scalar coercion, date rewriting or other
normalization; unbound values retain ordinary output-contract behavior.

A minimal mixed example is:

```yaml
name: summarizeAssessment
model: assistant
prompt: Explain the supplied assessment in a short summary.
input_schema:
  type: object
  properties:
    assessment: {type: object, additionalProperties: true}
  required: [assessment]
  additionalProperties: false
output_schema:
  type: object
  properties:
    assessment: {type: object, additionalProperties: true}
    summary: {type: string}
  required: [assessment, summary]
  additionalProperties: false
output_bindings:
  /assessment:
    from: input
    path: /assessment
```

Here the model produces only `summary`; Framework supplies the exact original
`assessment`. Initial and corrective prompts, schemas and examples must agree
about which fields are model-owned. Do not remove evidence the model needs to
reason merely because the same evidence is bound into the output.

### Completion, protection and failures

- Every child-result binding requires exactly one successful accepted direct
  producer task in the owning parent. Ensure accepted plans include each needed
  producer and reject unavailable/ambiguous selections rather than picking a
  first or last result. Declared impossible counts fail configuration validation;
  plan violations use the existing planning validation/correction budget.
- Assemble only after all accepted required work succeeds. Independent producers
  may run in parallel and must be joined before output assembly; do not impose a
  false ordering between independent producers. Do not return a partial success
  because the bound producers finished while other accepted work failed.
- The model must not supply a bound destination, even an equal value or null,
  nor an ancestor value preventing insertion. Reject conflicts rather than
  silently replacing/discarding them. Ordinary bounded model-output correction
  may fix model contribution errors; it must never ask the model to reconstruct
  framework-owned values or re-run already accepted child work.
- Every declared binding resolves, even if its destination is optional. Missing
  differs from explicit null; null is valid only where the complete output
  contract permits it. Unavailable sources and invalid bound values fail with
  actionable diagnostics, not a fallback to model synthesis.
- Keep the invocation's source values and configuration generation stable across
  correction. Concurrent/nested invocations must not share results by skill name
  or mutate retained child results. Binding grants no authorization and does not
  change child invocation limits or access boundaries.
- Preserve original model responses and accepted child results in diagnostics.
  Record assembled output and binding provenance sufficient to identify the
  destination, source kind/path, owning invocation and exact source task/skill.
  Distinguish declaration, plan/source-selection, model-override and complete
  output-validation failures. Follow existing diagnostic lifetime/size controls
  and content-fidelity policy; do not duplicate selected payloads in provenance.

## Acceptance criteria

- [ ] Authoring examples use the same destination map, `from`, `skill` and `path`
  meanings as input bindings. Both whole-child and selected-subtree sources work,
  including current-input sources and model/Java/REST direct-child results.
- [ ] A parent combines outputs from two successful direct children plus an input
  identifier into one schema-valid object, exactly preserving selected values and
  arrays, with no final synthesis request. All other accepted work still completes.
- [ ] An input-only model skill returns bound evidence plus model-generated
  reasoning; initial/corrective model contracts require only unbound contributions.
  Nested required siblings remain enforced and bound subtrees remain protected.
- [ ] Both `output_from` and `output_bindings` together fail configuration validation.
  Existing forwarding and skills declaring neither retain their behavior.
- [ ] Invalid pointers/descriptors, duplicate/overlapping destinations, impossible
  producers and statically incompatible schemas fail clearly. Dynamic missing,
  ambiguous, failed, cross-invocation or contract-invalid sources cannot publish
  success or trigger a model fallback. Missing and nullable values stay distinct.
- [ ] Model overrides, including equal values and blocking ancestors, are rejected;
  recovery uses existing bounded correction without changing bound values or
  replaying child work. Complete assembled output is validated before publication.
- [ ] Parallel producers, nested/concurrent executions and configuration reloads
  preserve source ownership and snapshot consistency. Authorizations remain intact.
- [ ] Provenance identifies the exact sources and assembled output while preserving
  original model and child evidence. Skipped final synthesis is observable.
- [ ] Author-facing documentation explains whole forwarding, full assembly and mixed
  assembly with complete schema examples, mutual exclusion and failure semantics.
  Deterministic verification establishes these mechanics without paid model runs.

## Context and scope

PR21 input bindings and existing whole-result `output_from` are prerequisites.
Authoring references: [input bindings](../../../agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md)
and [output contracts](../../../agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md).
Reuse their semantics and mechanisms where applicable; the pipeline owns code
location discovery, implementation design, test planning and command selection.

The equipment-service comparison suite found exact input transfer after PR21,
yet root synthesis still rewrote accepted output. Luna made small wording edits;
DeepSeek Pro inserted an article inside the accepted equipment assessment and
rephrased its rationale, failing three exact-preservation checks without an
apparent meaning change. GLM Flash also compressed completed comparison details.
The remaining design burden is asking a model to copy values that Framework
already holds. Composition must preserve multiple child results, which forwarding
one whole child cannot express without making that child copy the others.

Local supporting evidence lives in the separate
`C:/opendev/code/loomspan-sidecar-test-suite/evidence/` workspace:
`pr21-luna-20261005/summary.md`, `pr21-glm-flash-20261005/summary.md`, and
`pr21-deepseek-20261005/pro-priority-final-differences.json`. These are optional
background; all required intent is included here. These runs do not prove that
output assembly will improve business reasoning or model reliability.

This is a Framework feature ticket, not authorization to modify or retune the
comparison suite, relax its evaluator, refresh replay, run paid evaluations, or
change business recommendations. Do not add runtime model-authored bindings,
references/files as task-result selectors, cross-parent joins, array-element
mapping, transforms, aggregation, or a new Java SPI. Keep existing supported API
and configuration compatibility; internal implementation choices belong to the
pipeline. Creating this ticket does not start implementation, commit, push or
release work.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This introduces an author-visible configuration contract and
  changes output ownership, validation, correction and finalization across model
  and planning execution. Concurrency isolation and diagnostic fidelity require
  research, planning and independent review even though the product intent is clear.
- **Reassessment triggers:** Confirm PR21 and existing forwarding semantics in the
  current checkout; a supported API, authorization or compatibility change beyond
  this additive feature requires explicit reassessment. This recommendation does
  not assert that implementation compatibility has already been verified.
