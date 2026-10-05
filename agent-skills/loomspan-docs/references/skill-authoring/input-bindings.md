---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Declared Child Input Bindings

Use `input_bindings` on a structured `allowed_skills` entry of an explicit
`planning_mode: true` parent to transfer authoritative values into a direct child.
Model, annotation-only Java, and REST children use the same feature. The complete
receiving input contract remains authoritative; the calling model generates only
unbound arguments under a separate projected contract. Binding grants no access
and does not change invocation limits or the captured configuration generation.

## Declaration and paths

Each destination key MUST be a nonempty object JSON Pointer. Each source record
MUST contain exactly `from` and `path`, plus `skill` for `child_result` only.

| Source | Ownership | Required fields |
| --- | --- | --- |
| `input` | Validated input of this exact parent invocation | `from: input`, `path` |
| `child_result` | One accepted successful direct task in this parent | `from: child_result`, `skill`, `path` |

`path: ""` selects the entire source. `/context/data` traverses named object keys;
`~0` encodes tilde and `~1` encodes slash. Dots and numeric-looking tokens are
literal key characters. Paths never index arrays, although a selected value MAY
be an entire array. Empty object keys use `/`. No wildcards, transforms,
interpolation, inference by name, cross-parent lookup or result aggregation exists.

Unknown/null fields, invalid escapes, duplicate raw YAML keys and duplicate or
overlapping destination paths are rejected. Known closed/type boundaries and
parent input source paths are checked against the complete generation. Open or
unconstrained source branches and undeclared producer output shapes require
runtime validation. No output shape is inferred from a Java return type or REST
text. Unknown, self-referencing and cyclic producers are rejected.

## Complete example

Save these three declarations as separate files. `assistant` MUST be a configured
model alias; connection configuration is outside these skill files. This example
uses the same schema and mapping shape as
`DeclaredChildInputBindingsIntegrationTest#deliversLargeBoundEvidenceFromEmptyModelArguments`.

```yaml
name: root
description: Transfer declared authoritative evidence
model: assistant
planning_mode: true
max_steps: 2
prompt: Orchestrate evidence comparison.
input_schema:
  type: object
  properties:
    requestId: {type: string}
    records: {type: array, items: {type: object, additionalProperties: true}}
  required: [requestId, records]
  additionalProperties: false
allowed_skills:
  - name: producer
    required: true
    max_tasks: 1
    input_bindings:
      /caseId: {from: input, path: /requestId}
  - name: consumer
    required: true
    max_tasks: 1
    input_bindings:
      /caseId: {from: input, path: /requestId}
      /context/records: {from: input, path: /records}
      /context/assessment: {from: child_result, skill: producer, path: /data}
output_from: {skill: consumer}
```

```yaml
name: producer
description: Assess one case
model: assistant
prompt: Return an assessment object under data for the supplied case.
input_schema:
  type: object
  properties:
    caseId: {type: string}
  required: [caseId]
  additionalProperties: false
output_schema:
  type: object
  properties:
    data: {type: object, additionalProperties: true}
  required: [data]
  additionalProperties: false
```

```yaml
name: consumer
description: Compare complete evidence
model: assistant
prompt: Compare the complete supplied records and assessment.
input_schema:
  type: object
  properties:
    caseId: {type: string}
    context:
      type: object
      properties:
        records: {type: array, items: {type: object, additionalProperties: true}}
        assessment: {type: object, additionalProperties: true}
        candidateReasoning: {type: string}
      required: [records, assessment]
      additionalProperties: false
  required: [caseId, context]
  additionalProperties: false
```

A valid plan has one `producer` task followed by one `consumer` task whose
`dependsOn` explicitly includes that producer task ID. Both calls MAY use
`toolArguments: {}`. The consumer MAY instead supply only
`{"context":{"candidateReasoning":"new interpretation"}}`. Its actual receiving
input contains the renamed `caseId`, complete original records and accepted
`data` assessment. After all accepted work succeeds, `output_from` forwards the
consumer's exact retained String without parent synthesis or duplicate output
validation. Read [output contracts](output-contracts.md) for forwarding ownership.

## Two contracts and dependencies

Bound fields are absent from required model contributions and examples, and the
serialized model schema forbids them, including below open/generic objects.
Unbound constraints remain intact. A binding-created ancestor MAY be omitted if
it needs no required model contribution. If it was originally optional but a
binding creates it, unbound required siblings MUST be supplied. Initial and
corrective requests use these same projected rules.

The model MUST NOT provide a bound destination, even an equal value or null, nor
a scalar/null/array ancestor that prevents insertion. Ancestor objects holding
only permitted unbound siblings are accepted. Framework never discards a conflict.

Whenever a consumer is planned, each declared result producer MUST have exactly
one planned task in an earlier execution unit and a direct `dependsOn` edge from
that consumer. An omitted optional consumer imposes no unconditional producer
requirement. No explicit maximum-one declaration is required for these conditional
rules; required/max fields remain useful for unconditional counts. Impossible
producer counts and declaration cycles fail configuration validation. The runtime
never repairs plan edges. Binding issues share the existing one-total planning
correction with structure, counts and evidence. Same-unit sources fail even with
`concurrency: false`; independent producers MAY share a valid group, with full join
before a consumer. See [planning concurrency](planning-concurrency.md).

## Exact values and failures

Sources come from immutable owning-parent input and accepted pre-unit results.
Corrections retain their assignment and snapshot. Nested and concurrent parents
cannot share sources by skill name. Missing keys differ from explicit null. Every
binding MUST resolve even if its receiving property is optional. Null transfers
only when the complete receiving contract accepts it.

Accepted result text is decoded once as a complete JSON value, preserving large
integers and decimal values. JSON-quoted Java strings decode once. Plain or
malformed JSON text stays verbatim text; selecting its nested fields fails.
Ordinary nested JSON-looking strings remain strings. Original accepted text stays
unchanged for diagnostics and forwarding. Arrays retain order and absent optional
fields remain absent. Each consumer receives detached containers.

Bound subtrees undergo full required/type/closed/enum/format checks without
numeric/Boolean string coercion or date rewriting. Valid dates retain their exact
spelling; invalid dates fail. Unbound inputs keep ordinary normalization.
Failures distinguish declaration, plan dependency, unavailable/ambiguous source,
model override and assembled-input contract errors. Overrides may use ordinary
action correction; unavailable sources and invalid assembled inputs fail before
child dispatch. Feedback never requests reconstruction of bound evidence.

This feature is separate from `ref://` attachment/file resolution and JSON Schema
`$ref`. Neither is a selector for accepted task results.

## Evidence and verification

Raw model actions remain the authority for model arguments. A bound tool frame's
`arguments` contains the complete assembled delivered input. `inputBindings`
records destination, source kind/path, owning parent mission frame ID and, for
results, exact source task ID and skill. It contains no duplicate selected payload.
Existing caller, generation and linked consuming-task evidence remains available.
Read [traces and debugging](traces-and-debugging.md) for diagnostic lifetime/limits.

Executable anchors: `ChildInputBindingDeclarations`, `ChildInputBindingProjection`,
`PlanInputBindingDependencyValidator`, `MissionContext`, `AcceptedResultDecoder`,
`ChildInputBindingAssembler`, `SkillInputValidator#validateExact`,
`DefaultCapabilityInvoker` and `CapabilityExecutionRouter#executeAssembled`.
Focused tests: `YamlSkillCatalogTests`, `ChildInputBindingTest`,
`ChildInputBindingProjectionTest`, `PlanInputBindingDependencyValidatorTest`,
`AcceptedResultDecoderTest`, `ChildInputBindingAssemblerTest`,
`StepActionValidatorTest`, `StepPromptBuilderTest`,
`DeclaredChildInputBindingsIntegrationTest` and `BindingIsolationIntegrationTest`.
These establish deterministic mechanics, not improved live model reliability or
business reasoning. Large evidence still consumes the receiving model's context.
