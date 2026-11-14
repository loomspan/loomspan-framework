---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: local-canonical-bindings
---

# Output Contracts

Authored child references and binding producers MAY be local to the containing skill's namespace or explicitly qualified; local `search` in `sales` compiles to `sales-search`. Mixed spellings compare canonically and never search other namespaces. Runtime/model names remain qualified. See [canonical namespace identity](mental-model.md#canonical-namespace-identity) for grammar, limits and boundary rules. Declarations keep local `name` components.

## Applicability

For accepted declaration vocabulary, nullability, openness, assertions, formats
and annotations, read [standard schemas](standard-schemas.md). The shared
compiler and binding projection/composition have focused local coverage. The current checkout cannot
build the complete application while schema consumers remain inconsistent.
Binding-owned checks use canonical schemas and the shared validator. General
forwarding, engine completion, guidance, correction, provider request attachment
and effective metadata integration remain incomplete. Their retained test anchors
are not passing application guarantees. Local composition checks alone do not
restore those consumers.

[TypeSafe leaves](typesafe-skills.md) have a generated descriptive output schema
and return the complete provider answers without answer validation or repair.
The authored `output_schema` rules below apply to ordinary chat skills. A parent
can bind from or forward a TypeSafe child's result under the existing rules.

Use this topic to choose final synthesis, declared output assembly with `output_bindings`, or exact designated-child completion with `output_from` for a model-backed YAML skill. An output contract describes the intended structured result and supplies a local standard validator; runtime guidance and semantic correction require the consumer integration described above. Java skills have no YAML `output_schema`; their null/void results become empty text, Strings remain exact, and other results become JSON. See [Java results](../java-api/java-skills.md#inputs-results-and-lifecycle).

Explicit provider refusal/incompleteness terminates before schema, linter or
evidence correction, even when the returned JSON matches the contract. No
correction allowance can accept that partial result or replay the generation.
Normal complete output still uses the configured correction budgets. See
[explicit status and adapter limits](model-selection-and-connections.md#explicit-response-status).

The root schema MUST have exact `type: object`; see the shared topic for the supported nested types and assertions.

A [planner inability declaration](mental-model.md#planner-inability) fails the
invocation before output binding composition, schema validation, evidence
validation, or linting. Prior successful task results remain available in the trace.

## Choose who produces the result

| Responsibility | Declaration | Completion |
| --- | --- | --- |
| Generate the whole output | Omit `output_from` and `output_bindings`; author a parent `output_schema` when structured output is required | The parent synthesizes and owns its final-output validation. |
| Preserve selected input/child values in every output field | Declare `output_bindings` covering a closed output object | Framework assembles and validates it with no final model request. |
| Preserve evidence while generating reasoning | Bind preserved fields and leave reasoning fields unbound | The model contributes only unbound fields; Framework validates the complete assembly. |
| Orchestrate work while one direct child already supplies the complete intended answer | `output_from: {skill: default-finish}` | Runtime returns that accepted task's existing Framework String unchanged after all accepted work succeeds. |

Forwarding retains model planning, input preparation, explicit unbound child arguments, [author-declared input bindings](input-bindings.md), dependencies, and genuine orchestration duties. Authors SHOULD reconsider a wrapper with no additional responsibility. Do not instruct a forwarding parent to copy, quote, or reconstruct the child's answer.

## Forwarding declaration and completion

`output_from` MAY appear only on a model-backed YAML skill with explicit `planning_mode: true` and a configured `model`. It MUST be an object containing exactly one `skill` field whose value is an exact, nonblank, case-sensitive direct-child name. No aliases, trimming, selectors, grandchildren, or multiple invocation selection are supported.

The selected `allowed_skills` entry MUST explicitly declare `required: true` and `max_tasks: 1`. `min_tasks: 1` alone is insufficient. Existing bounds validation still applies. The complete generation MUST resolve the child; self and multi-skill forwarding cycles are rejected. Ordinary recursive child visibility without forwarding edges is not a forwarding cycle.

A forwarding parent MUST NOT declare `output_schema`, `output_schema_max_retries`, or `linter`, including explicit null declarations. Put final-output schema, evidence, linting, and correction policies on the actual producing model child, or use an ordinary synthesis parent.

The plan MUST contain exactly one task bound to the designated child. Required-child visibility, plan validation and its single combined correction remain applicable. Runtime retains that accepted task ID and executes every accepted task, including optional work and work ordered after the selected task. Full joins, authorization, limits, failure and cancellation remain enforced. Missing, ambiguous, failed, stale, or mismatched selection fails without fallback synthesis.

Successful completion returns the complete retained String without parsing, serialization, field extraction, citation changes, or transport unwrapping. Empty and whitespace text, exact Java Strings, JSON from non-string Java values, long values, and array order remain intact. Forwarding creates no parent final model request, final-output validation, correction or synthetic completion step. N accepted tasks need N `max_steps` slots; ordinary synthesis needs N+1. Planning and physical-provider budgets remain separate. See [planning task constraints](planning-task-constraints.md), [concurrency](planning-concurrency.md), and [forwarding diagnostics](traces-and-debugging.md#designated-child-completion).

## Minimal forwarding examples

These illustrative fragments belong in separate skill files; they omit surrounding model/connection configuration. `assistant` MUST be a configured model alias. The parent prompts identify orchestration duties; child input arguments still follow each child's own contract.

Model producer with its own structured-output contract:

```yaml
name: prepareReport
model: assistant
description: Prepare the report request and delegate its completion.
planning_mode: true
max_steps: 1
prompt: Determine the report scope and supply explicit arguments to finishReport.
allowed_skills:
  - {name: default-finishReport, required: true, max_tasks: 1}
output_from: {skill: default-finishReport}
```

```yaml
name: finishReport
model: assistant
description: Produce the complete report.
prompt: Produce the complete report for the supplied request.
output_schema:
  type: object
  properties:
    report: {type: string}
  required: [report]
  additionalProperties: false
```

Annotation-only Java producer, registered on an application Spring bean:

```java
@SkillMethod(name = "finishReport", description = "Produce the complete report")
public String finishReport() { return "Report complete"; }
```

```yaml
name: prepareReport
model: assistant
description: Determine when to produce the report.
planning_mode: true
max_steps: 1
prompt: Determine the report request and invoke finishReport.
allowed_skills:
  - {name: default-finishReport, required: true, max_tasks: 1}
output_from: {skill: default-finishReport}
```

The returned Framework text is `Report complete`, without JSON quotes. No output schema is inferred from the Java return type.

A chain `prepareReport -> coordinateReport -> finishReport` works when each forwarding parent independently declares its selected direct child as required and unique. Every level completes its entire accepted plan and forwards its own selected task result; no level may select a grandchild directly.

## Declared output assembly

Top-level `output_bindings` uses the same destination-keyed map and descriptors as [input bindings](input-bindings.md). Its destination is this skill's output. `from: input` selects this invocation's validated input; `from: child_result` selects exactly one successful accepted direct-child task, with `skill` naming the producer. `path: ""` selects the whole source. A nonempty source path selects an object subtree. Object JSON Pointers support `~0` and `~1`; array traversal, transforms and interpolation are unsupported. Entire selected arrays retain their order.

A model-backed skill MUST declare an object `output_schema`. `output_from` and `output_bindings` MUST NOT coexist, including explicit null declarations. Destination paths MUST be nonempty and nonoverlapping. Duplicate YAML keys and unknown descriptor fields are rejected. Known source/destination paths and compatible types are checked against the captured complete generation. Open or unspecified source schemas require runtime checks; Java return types and executor result text never invent producer contracts.

Source and destination property names match exactly; `/Name` and `/name` are
distinct keys. Publication checks preserve omitted object openness, equivalent to
explicit true at both root and nested objects; explicit false still rejects
statically impossible paths and transfers. Enum overlap compares numeric values
exactly (`1`, `1.0`, `1e0` and `1.00` are equivalent), with type-sensitive equality
for other scalar values. These bounded structural checks do not prove future
runtime values or general schema implication.

This full assembly combines two direct-child objects with the current input identifier. `assistant` must be a configured model alias; declare each producer separately with its own input contract. The selected child results must be objects compatible with these destination schemas.

```yaml
name: assembleCase
model: assistant
planning_mode: true
max_steps: 2
prompt: Run assessEquipment and planResolution with the case details.
input_schema:
  type: object
  properties:
    caseId: {type: string}
  required: [caseId]
  additionalProperties: false
allowed_skills:
  - {name: default-assessEquipment, required: true, max_tasks: 1}
  - {name: default-planResolution, required: true, max_tasks: 1}
output_schema:
  type: object
  properties:
    caseId: {type: string}
    equipmentAssessment: {type: object, additionalProperties: true}
    recommendation: {type: object, additionalProperties: true}
  required: [caseId, equipmentAssessment, recommendation]
  additionalProperties: false
output_bindings:
  /caseId: {from: input, path: /caseId}
  /equipmentAssessment: {from: child_result, skill: default-assessEquipment, path: ""}
  /recommendation: {from: child_result, skill: default-planResolution, path: ""}
```

Every bound child producer must occur exactly once in the accepted plan, even when its allowed entry is optional. Impossible declared counts fail configuration validation. These output requirements add no dependency or false ordering; independent producers may run in parallel. All accepted work must succeed before assembly, including work unrelated to these bindings. Planning, access, invocation limits and cancellation still apply.

An ordinary skill can bind only from its own input. This mixed contract preserves assessment evidence and asks the model for `summary` only:

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
  /assessment: {from: input, path: /assessment}
```

Initial and corrective output instructions describe only the projected unbound contract. Full input and accepted-result evidence remain available for reasoning. Required unbound siblings remain enforced, including inside optional ancestors created by binding insertion. A bound ancestor owns its entire subtree. The model MUST NOT supply a bound destination, even an equal value or null, or a blocking scalar/array/null ancestor. Such contribution errors can consume the normal bounded output correction budget; accepted children are not replayed.

Framework resolves every binding, including optional destinations, and checks each
known selected destination contract before model contribution. It validates the
model contribution against its separate projected contract before inserting exact
selected values, then validates the complete assembly against the unchanged
original contract. Bounds and asserted formats remain effective at each applicable
boundary. Selected values retain exact numbers, strings, null and ordered arrays
without coercion or date rewriting. JSON-looking nested strings remain strings;
plain-text child results remain text. Newly assembled JSON need not preserve source
lexical whitespace. Existing `output_from` retains exact full-text semantics.

Missing paths differ from explicit null. Unavailable, ambiguous, failed, cross-invocation or contract-invalid sources fail with diagnostics and never fall back to model copying. Source snapshots and the captured generation remain stable across correction. Fully bound output skips final synthesis only when no optional/required declared field or open field space remains model-owned. Omitted `additionalProperties` is open just as true is, at both root and partially bound nested ancestors. A wholly bound object owns its entire subtree even when that object's schema is open. A planning full assembly costs N task steps; a mixed output costs N+1. Required accepted work and source checks still apply before synthesis can be skipped.

`RESULT_ASSEMBLED` identifies ownership and whether a model contribution was required. Its payload is the validated assembled output, while original model and child records retain their evidence. See [assembly diagnostics](traces-and-debugging.md#declared-output-assembly).

Local implementation/test anchors: `OutputBindingProjection`,
`OutputBindingComposition`, `ChildInputBindingAssembler`, `BindingSchemaCompatibility` and their focused tests protect projection, bounded publication analysis,
ownership conflicts, exact insertion and complete validation. `OutputBindingIsolationTest`
retains mission/plan isolation controls. `DeclaredOutputBindingsOrdinaryIntegrationTest`
and `DeclaredOutputBindingsPlanningIntegrationTest` retain facade completion and
correction, root/nested omitted/true/false openness, required-work completion and
model/Java/executor source cases; those full-graph checks are currently blocked.
The provider protocol tests retain generated false-property schema fidelity controls;
local schema serialization is not provider acceptance. `ConsoleTraceFixtureCorpusTest`
retains the Java assembly authority fixture for Console readers.

## Effective output metadata

TypeSafe producers expose a declaration-derived schema, inherited unchanged by
forwarding chains. Fixed criteria supply fixed Choice/probability keys; dynamic
criteria keep maps open without specializing catalog metadata per call. Native
answers, including empty/extended objects, are not validated against that schema.
Parent output-binding registration uses the same partial compatibility checks;
actual selected values remain subject to the destination's runtime contract.

`DeclaredOutputBindingsPlanningIntegrationTest#nativePassthroughIsEnforcedOnlyByConsumingBindingContract`
protects fixed and dynamic Choice receiver enums, accepted singleton `type`
bindings, and exact missing/cased probability lookup. Dynamic values register
against open metadata and fail at the receiving runtime contract when incompatible;
fully bound success and failure require no final synthesis or repair request.

Public `SkillDescriptor.outputSchema()`, planner tool descriptions and Console skill detail are consumers of captured output metadata. Their canonical-schema integration is incomplete. The foundation retains exact authored standard assertions and nullable type arrays; its provider copies remove recognized Loomspan annotations only. This does not certify public catalog rendering or provider-native response formats.

Java and model producers without a schema leave this metadata null, never a fabricated `{}`. Derived metadata is immutable and does not become a parent's authored validator, evidence requirement, linter, or retry policy. The actual model producer retains its own validation and correction; Java/executor do not gain output-validation guarantees. Metadata and structured validation do not establish factual correctness.

### Prepared contracts and fresh admission

Catalogs describe immutable prepared contracts even when current dependencies make an
entry inadmissible. Admission and root validation recompute reachable effective outputs
and existing binding/forwarding constraints against captured targets without changing
catalog metadata. They compare schema structures: formatting and object-key order alone
do not change the contract. There is no dependency-revision or historical-child-schema pin.

A fully forwarded output change, including an added field or a change through multiple
forwarding hops, requires republication of each affected caller. Ordinary child schema
changes can pass, and an unrelated added bound-child output field can pass, when declared
constraints and caller-derived contracts still hold. Input/output bindings retain their
existing partial static checks and runtime obligations; this is no general compatibility
engine. Caller publication checks its own target base only: deleting a dependency, making
it Private, or changing its contract after caller preparation does not make publication
stale, but can reject subsequent admission. Prior admitted work retains its old graph.

Evidence: `SkillDependencyValidator#checkAdmission`, `SkillAdmissionContractsTest`, and
`SkillReachableAdmissionTest`. Captured child dispatch is implemented; canonical observation consumers are covered by `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest`.

## Declaration rules

[Standard schemas](standard-schemas.md) is the complete declaration reference.
Presence uses the parent object's `required` list; nullability uses a nested type
array such as `type: [string, "null"]`. Omitted `additionalProperties` remains
open. Nested arrays, scalar enums, inclusive numeric bounds, string/array length
bounds and the listed asserted formats are supported by the local compiler.
Legacy `nullable` and schema-node `evidence` are rejected. Use
`x-loomspan-evidence` only on immediate root output properties.

## Guidance, validation, and retries

For ordinary model execution without `output_bindings`, Loomspan places the complete effective contract in the first request. For ordinary synthesis in planning mode without output bindings, it places the same contract in the first `FINAL_RESPONSE` request, after required plan tasks complete; tool-call steps do not receive final-output guidance. The renderer includes every normalized node, required or optional presence, nullable or non-null values, effective object openness, array items, enum, format, and description. It does not include evidence expressions, mappings, retry settings, or provider metadata.

Loomspan then parses and validates the returned JSON. It does not silently coerce, insert, remove, or repair candidate fields and does not request a provider-native JSON Schema response format. With `output_schema_max_retries: N`, a non-planning output allows one initial validated response plus at most `N` semantic corrections. Planning final-response validation uses its planning retry path. For physical-attempt identity, accounting, correction-message composition, and trace diagnosis, read [traces-and-debugging.md](traces-and-debugging.md).

## Authoring procedure

1. Define the root object and decide whether it is closed or open.
2. For every property, decide presence and nullability separately.
3. Define object children and array items recursively; use `required` only on the owning object.
4. Prefer closed objects. Set `additionalProperties: true` only where intentionally heterogeneous fields are accepted.
5. Use scalar `enum` and supported `format` for local assertions; use `description` for meaning rather than business validation.
6. Add immediate-root evidence separately when supportability must be enforced; do not duplicate evidence grammar here.
7. Test missing, null, wrong-type, unknown-property, array-item, and enum cases, plus any domain recovery policy.

## Known limitations

- Complete runtime consumer integration is unavailable; see Applicability.
- Composition, references, conditionals, tuple schemas, `pattern` and unlisted
  assertions are outside the supported declaration profile.
- There is no minimum-useful-content rule. An object without required children
  can accept `{}`.
- `description` does not validate business meaning or factual correctness.
- Output schemas permit only Boolean `additionalProperties`; schema-valued
  additional values are an INPUT role feature.

## Forwarding evidence

- `YamlSkillCatalogTests` protects strict `output_from` declarations and conflicts; `SkillGenerationManagerTest` protects complete-set chains, cycles and unspecified schemas.
- `DefaultSkillCatalogTest`, `PlanningServiceTest` and `LoomspanPublicSurfaceArchitectureTest` protect generation-bound metadata and the five-component supported descriptor.
- `StepLoopMissionExecutionEngineTest` protects exact accepted-task selection, N-slot admission, all-work completion and failure/cutoff behavior.
- `DesignatedChildResultIntegrationTest#forwardsModelJavaAndExecutorResultsThroughSkillTemplate` and `#forwardingChainRetainsProducerValidationAndExactResult` exercise model, Java, executor YAML, unspecified schema and chained result boundaries through `SkillTemplate`, with producer-owned validation and no parent final model request. `#parallelRootsForwardOnlyTheirOwnTaskResults` and `#runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload` protect invocation and generation isolation; `#ordinarySynthesisRemainsAvailable` protects the ordinary completion path.

## Implementation and test anchors

- `StandardSchemaCompiler`, `CanonicalSchema`, `StandardSchemaCompilerTest` and
  `CanonicalSchemaTest` define and exercise the current declaration foundation;
  see [standard schemas](standard-schemas.md#executable-anchors).
- `YamlSkillCatalog`, `YamlSkillManifest` and `YamlSkillDefinition` wire canonical
  declarations into registration and generation ownership.
- `OutputSchemaValidator`, `OutputSchemaPromptAugmentor`, `OutputSchemaCallAdvisor`,
  `StepPromptBuilder` and the mission engine are runtime consumers whose canonical
  integration is incomplete. Their retained tests do not certify the new schema
  representation until the application builds and those checks execute.

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. Namespace updates support preparation
and installation, retained reachable admission and captured cross-namespace execution.
Captured canonical observation identities and existing Console consumers are covered by `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest`.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`.
