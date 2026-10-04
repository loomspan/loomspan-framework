---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Output Contracts

## Applicability

Use this topic to choose final synthesis with `output_schema` or exact designated-child completion with `output_from` for a model-backed YAML skill. An output contract gives the model provider-neutral instructions, validates the returned JSON exactly, and can drive semantic retries. Java skills have no YAML `output_schema`; their returned-value behavior is Jackson serialization of the Java result.

The root schema MUST have `type: object`. Supported node types are `object`, `array`, `string`, `integer`, `number`, and `boolean`.

## Choose who produces the result

| Responsibility | Declaration | Completion |
| --- | --- | --- |
| Combine, interpret, or change child results | Omit `output_from`; author a parent `output_schema` when structured output is required | The parent synthesizes and owns its final-output validation. |
| Orchestrate work while one direct child already supplies the complete intended answer | `output_from: {skill: finish}` | Runtime returns that accepted task's existing Framework String unchanged after all accepted work succeeds. |

Forwarding retains model planning, input preparation, explicit child arguments, dependencies, and genuine orchestration duties. Authors SHOULD reconsider a wrapper with no additional responsibility. Do not instruct a forwarding parent to copy, quote, or reconstruct the child's answer.

## Forwarding declaration and completion

`output_from` MAY appear only on a model-backed YAML skill with explicit `planning_mode: true` and a configured `model`. It MUST be an object containing exactly one `skill` field whose value is an exact, nonblank, case-sensitive direct-child name. No aliases, trimming, selectors, grandchildren, or multiple invocation selection are supported.

The selected `allowed_skills` entry MUST explicitly declare `required: true` and `max_tasks: 1`. `min_tasks: 1` alone is insufficient. Existing bounds validation still applies. The complete generation MUST resolve the child; self and multi-skill forwarding cycles are rejected. Ordinary recursive child visibility without forwarding edges is not a forwarding cycle.

A forwarding parent MUST NOT declare `output_schema`, `output_schema_max_retries`, or `linter`, including explicit null declarations. Put final-output schema, evidence, linting, and correction policies on the actual producing model child, or use an ordinary synthesis parent.

The plan MUST contain exactly one task bound to the designated child. Required-child visibility, plan validation and its single combined correction remain applicable. Runtime retains that accepted task ID and executes every accepted task, including optional work and work ordered after the selected task. Full joins, authorization, limits, failure and cancellation remain enforced. Missing, ambiguous, failed, stale, or mismatched selection fails without fallback synthesis.

Successful completion returns the complete retained String without parsing, serialization, field extraction, citation changes, or transport unwrapping. Empty and whitespace text, Java JSON string quoting, long values, and array order remain intact. Forwarding creates no parent final model request, final-output validation, correction or synthetic completion step. N accepted tasks need N `max_steps` slots; ordinary synthesis needs N+1. Planning and physical-provider budgets remain separate. See [planning task constraints](planning-task-constraints.md), [concurrency](planning-concurrency.md), and [forwarding diagnostics](traces-and-debugging.md#designated-child-completion).

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
  - {name: finishReport, required: true, max_tasks: 1}
output_from: {skill: finishReport}
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
  - {name: finishReport, required: true, max_tasks: 1}
output_from: {skill: finishReport}
```

The returned Framework text is `"Report complete"`, including JSON quotes from the existing Jackson Java result boundary. No output schema is inferred from the Java return type.

REST producer using the same parent declaration:

```yaml
name: finishReport
description: Produce the complete report through the application handler.
rest: true
```

The application's sole `RestSkillHandler` returns non-null text. That text is forwarded unchanged, including any application envelope; null remains a failure. The leaf has unspecified output schema and no new Framework output validation. See [REST leaves](rest-skills.md).

A chain `prepareReport -> coordinateReport -> finishReport` works when each forwarding parent independently declares its selected direct child as required and unique. Every level completes its entire accepted plan and forwards its own selected task result; no level may select a grandchild directly.

## Effective output metadata

Public `SkillDescriptor.outputSchema()`, planner tool descriptions, and Console skill detail expose nullable normalized JSON-object schema text from the captured complete generation. Ordinary model skills expose their authored output shape; forwarding parents derive it through their selected-child chain. Shape, constraints, descriptions and provider-neutral `nullable` semantics are retained, while child-local `evidence` annotations are omitted. This text describes Loomspan's supported schema vocabulary; it is not a provider-native JSON Schema response format.

Java, REST, and model producers without a schema leave this metadata null, never a fabricated `{}`. Derived metadata is immutable and does not become a parent's authored validator, evidence requirement, linter, or retry policy. The actual model producer retains its own validation and correction; Java/REST do not gain output-validation guarantees. Metadata and structured validation do not establish factual correctness.

## Presence and nullability

Presence belongs to the parent object's `required` list. Nullability belongs to the selected child node's `nullable` value. They are independent.

| Parent requires property | Child has `nullable: true` | Meaning |
| --- | --- | --- |
| Yes | No | The property MUST be present and MUST NOT be JSON null. |
| Yes | Yes | The property MUST be present and MAY be JSON null. |
| No | No | The property MAY be omitted; if present, it MUST NOT be JSON null. Omit it when unknown. |
| No | Yes | The property MAY be omitted; if present, it MAY be JSON null. |

Every name in `required` MUST identify a declared sibling in `properties`. A present object with no required children may be `{}`. Object type validation alone does not prove useful business content. When unavailable information must not be invented, authors SHOULD make the property nullable and describe the domain recovery rule rather than relying on an empty object.

```yaml
output_schema:
  type: object
  properties:
    selection:
      type: object
      nullable: true
      description: Selected option; null when unavailable. Do not invent details.
      additionalProperties: true
  required: [selection]
  additionalProperties: false
```

## Supported node fields

| Field | Behavior |
| --- | --- |
| `type` | Enforced recursively against the JSON value. The root MUST be an object. |
| `properties` | Declares object children. Declaration order is retained in model guidance. |
| `required` | Enforces child-property presence; it does not make the child non-null. |
| `nullable` | Allows JSON null at that node; omission still depends on the parent. The effective default is non-null. |
| `additionalProperties` | Controls undeclared object children. Omission normalizes to `false` at every object depth; explicit `true` leaves the object open. |
| `items` | Defines and enforces one scalar or object item schema recursively for an array. |
| `enum` | Supported only for strings and enforced using the declared values. Declaration order is retained and values are JSON-quoted in guidance. |
| `format` | Model guidance only. Loomspan does not enforce format-specific syntax. |
| `description` | Model guidance only. Loomspan does not validate its business meaning. |
| `evidence` | Orchestration metadata on eligible immediate root properties, not a candidate field or schema constraint. Read [evidence-contracts.md](evidence-contracts.md). |

Array-item validation reports concrete indexed paths such as `$.rows[0].amount`. Model guidance uses the corresponding general path `$.rows[].amount`. Object properties use paths such as `$.transport.outbound`. Names containing path punctuation use JSON-quoted bracket segments such as `$["transport.mode"]`, keeping distinct properties unambiguous. These paths make the initial contract and retry feedback comparable.

## Guidance, validation, and retries

For ordinary model execution, Loomspan places the complete effective contract in the first request. For ordinary synthesis in planning mode, it places the same contract in the first `FINAL_RESPONSE` request, after required plan tasks complete; tool-call steps do not receive final-output guidance. The renderer includes every normalized node, required or optional presence, nullable or non-null values, effective object openness, array items, enum, format, and description. It does not include evidence expressions, mappings, retry settings, or provider metadata.

Loomspan then parses and validates the returned JSON. It does not silently coerce, insert, remove, or repair candidate fields and does not request a provider-native JSON Schema response format. With `output_schema_max_retries: N`, a non-planning output allows one initial validated response plus at most `N` semantic corrections. Planning final-response validation uses its planning retry path. For physical-attempt identity, accounting, correction-message composition, and trace diagnosis, read [traces-and-debugging.md](traces-and-debugging.md).

## Authoring procedure

1. Define the root object and decide whether it is closed or open.
2. For every property, decide presence and nullability separately.
3. Define object children and array items recursively; use `required` only on the owning object.
4. Prefer closed objects. Set `additionalProperties: true` only where intentionally heterogeneous fields are accepted.
5. Use `enum` for enforced string choices. Use `format` and `description` only as model guidance.
6. Add immediate-root evidence separately when supportability must be enforced; do not duplicate evidence grammar here.
7. Test missing, null, wrong-type, unknown-property, array-item, and enum cases, plus any domain recovery policy.

## Known limitations

- Nested arrays (`items.type: array`) are rejected by the current catalog.
- `anyOf`, `oneOf`, discriminators, conditional schemas, and `minProperties` are unsupported.
- There is no minimum-useful-content rule. An object without required children accepts `{}`.
- `format` and `description` are not validator-enforced.
- `additionalProperties: true` permits undeclared child shapes; it does not validate those unknown values against another schema.
- Prompt size grows with the complete schema. Loomspan does not silently truncate constraints.

## Forwarding evidence

- `YamlSkillCatalogTests` protects strict `output_from` declarations and conflicts; `SkillGenerationManagerTest` protects complete-set chains, cycles and unspecified schemas.
- `DefaultSkillCatalogTest`, `PlanningServiceTest` and `LoomspanPublicSurfaceArchitectureTest` protect generation-bound metadata and the five-component supported descriptor.
- `StepLoopMissionExecutionEngineTest` protects exact accepted-task selection, N-slot admission, all-work completion and failure/cutoff behavior.
- `DesignatedChildResultIntegrationTest#forwardsModelJavaAndRestResultsThroughSkillTemplate` and `#forwardingChainRetainsProducerValidationAndExactResult` exercise model, Java, REST, unspecified schema and chained result boundaries through `SkillTemplate`, with producer-owned validation and no parent final model request. `#parallelRootsForwardOnlyTheirOwnTaskResults` and `#runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload` protect invocation and generation isolation; `#ordinarySynthesisRemainsAvailable` protects the ordinary completion path.

## Implementation and test anchors

- `YamlSkillManifest.OutputSchemaManifest`, `YamlSkillCatalog#validateOutputSchema`, and its recursive `#validateSchemaNode` path define and normalize the accepted vocabulary. `YamlSkillCatalogTests#defaultsAdditionalPropertiesToFalseAtEveryObjectDepth` and `#failsStartupWhenOutputSchemaContainsNestedArrayItems` protect the documented default and nested-array limitation.
- `OutputSchemaValidator` defines exact recursive validation. `OutputSchemaValidatorTest#treatsPresenceAndNullabilityAsIndependentConstraints`, `#respectsAdditionalPropertiesForNestedObjects`, `#validatesArrayItemsRecursivelyWithIndexedPaths`, `#escapesPathSignificantPropertyNamesWithoutCollisions`, and `#doesNotEnforceFormatOrDescription` protect the central rules and unambiguous diagnostics.
- `OutputSchemaPromptAugmentor#renderContract` defines the shared effective-contract text. `OutputSchemaPromptAugmentorTest#augmentsPromptWithCompleteRecursiveEffectiveContractInDeclaredOrder` protects complete deterministic rendering, JSON-quoted enums, canonical escaped paths, and metadata isolation.
- `OutputSchemaCallAdvisor` owns ordinary schema retries, while `StepPromptBuilder` and `StepLoopMissionExecutionEngine#validateOutputSchema` integrate the same contract with planning final responses. Their focused tests protect both prompt paths and retry behavior.
