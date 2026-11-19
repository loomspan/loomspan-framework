# PR 31.4.4 — Let YAML executor skills validate the result they publish

## Outcome

A YAML skill backed by a SkillExecutor can optionally declare the JSON contract its callers can rely on after a successful invocation. The schema belongs to the individual skill. Skills sharing an executor may publish different contracts, and authors who only need pass-through or a projection can omit it. This builds on [PR 31.4.3](loomspan-pr-31.4.3-executor-output-transform.md).

For example, support triage can publish a repeat-contact probability from a TypeSafe response. The expression selects the value and the schema checks the promise, so the author can omit a duplicate JSLT guard.

```yaml
output_transform:
  language: jslt
  expression: |
    {"repeatContactProbability": .answers.repeat.noul}
output_schema:
  type: object
  properties:
    repeatContactProbability:
      type: number
      minimum: 0
      maximum: 1
  required: [repeatContactProbability]
  additionalProperties: false
```

## Requirements and acceptance criteria

The callable child declares `output_schema` at the root of its own skill YAML, alongside `executor` and optional `output_transform`. Each child using the same Java handler owns its result contract independently.

| Fields declared on the child | Rule for a successful result |
|---|---|
| Neither | Publish the executor's ordinary result text exactly, including empty or non-JSON text. |
| `output_schema` only | Parse the executor result text as one complete JSON value. It must be an object satisfying the schema. Publish the original validated text. |
| `output_transform` only | Parse the executor result text as one complete JSON value for JSLT's `.`. Publish the transform result as JSON text. Any JSON root is valid, and string results retain their JSON quotes. |
| Both | Parse the executor result text as one complete JSON value, run JSLT, then validate the transformed object against the schema before publishing it. The raw executor JSON may have a different shape. |

- [ ] Accept optional root-level `output_schema` on YAML executor leaves using Framework's existing authored output-schema compiler and validator. The existing profile requires `type: object` at the schema root. Scalar-root and array-root schema declarations fail during startup or namespace preparation. An invalid schema fails preparation without replacing the active generation. An in-flight invocation uses its captured skill generation.
- [ ] At the existing executor result publication boundary, pass the candidate result text (raw or transformed) and the leaf's compiled schema to the existing output validator once. On success, publish that same text, preserving formatting and JSON types. Executor and transform failures follow their existing paths.
- [ ] With the example above, numeric 0 and 1 both pass. A string such as `"high"`, a number outside the range, or a projected null fails. A missing Jev field produces a present null rejected by `type`; a misspelled or omitted published key fails `required`. With a schema alone, malformed or non-JSON result text also fails.
- [ ] Direct callers, ordinary tool callers, forwarding parents and downstream child-result bindings all consume the same validated result. On mismatch, fail the producing skill once and identify the skill, offending location and violated constraint in diagnostics. No invalid result or raw fallback reaches a caller as success, and a dependent child does not execute. Preserve the existing distinction between raw executor evidence and published output under the usual trace limits.
- [ ] A schema mismatch is terminal for the leaf. Schema validation issues no further executor or model call. Keep `output_schema_max_retries` forbidden on executor leaves. Reject `x-loomspan-evidence` during preparation on an executor's own schema: its claims refer to child work, and an executor leaf has no declared children.
- [ ] Omitting the schema adds no validation. A transform alone can still publish any JSON value, including a bare `0.95` or null. To validate a probability, the skill publishes an object such as `{"repeatContactProbability":0.95}` under the example schema. A leaf omitting both fields still passes through its original text, including empty and non-JSON text.
- [ ] Expose the declared structural schema through the existing effective output-schema metadata for the leaf and a parent that forwards its result. Existing consumers of that metadata, including binding checks, use their ordinary rules.
- [ ] Authoring guidance shows the optional schema with and without a transform and explains the failure boundary. Scope is Framework support for the existing schema contract on YAML executor skills. Executor annotations, shared extension schemas, new publish-time analysis of parent bindings, and migration of TypeSafe skills are outside this ticket.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change joins schema preparation and reload semantics with a runtime failure boundary shared by direct and planned callers after potentially side-effecting work.
- **Reassessment triggers:** Reassess if the implementation requires a supported Java API change or changes model-backed output validation.
