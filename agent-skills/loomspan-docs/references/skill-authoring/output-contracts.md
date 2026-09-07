---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Output Contracts

## Applicability

Use this topic to author or diagnose `output_schema` for an LLM-backed YAML skill. An output contract gives the model provider-neutral instructions, validates the returned JSON exactly, and can drive semantic retries. Java skills have no YAML `output_schema`; their returned-value behavior is Jackson serialization of the Java result.

The root schema MUST have `type: object`. Supported node types are `object`, `array`, `string`, `integer`, `number`, and `boolean`.

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

For ordinary model execution, Loomspan places the complete effective contract in the first request. For planning mode, it places the same contract in the first `FINAL_RESPONSE` request, after required plan tasks complete; tool-call steps do not receive final-output guidance. The renderer includes every normalized node, required or optional presence, nullable or non-null values, effective object openness, array items, enum, format, and description. It does not include evidence expressions, mappings, retry settings, or provider metadata.

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

## Implementation and test anchors

- `YamlSkillManifest.OutputSchemaManifest`, `YamlSkillCatalog#validateOutputSchema`, and its recursive `#validateSchemaNode` path define and normalize the accepted vocabulary. `YamlSkillCatalogTests#defaultsAdditionalPropertiesToFalseAtEveryObjectDepth` and `#failsStartupWhenOutputSchemaContainsNestedArrayItems` protect the documented default and nested-array limitation.
- `OutputSchemaValidator` defines exact recursive validation. `OutputSchemaValidatorTest#treatsPresenceAndNullabilityAsIndependentConstraints`, `#respectsAdditionalPropertiesForNestedObjects`, `#validatesArrayItemsRecursivelyWithIndexedPaths`, `#escapesPathSignificantPropertyNamesWithoutCollisions`, and `#doesNotEnforceFormatOrDescription` protect the central rules and unambiguous diagnostics.
- `OutputSchemaPromptAugmentor#renderContract` defines the shared effective-contract text. `OutputSchemaPromptAugmentorTest#augmentsPromptWithCompleteRecursiveEffectiveContractInDeclaredOrder` protects complete deterministic rendering, JSON-quoted enums, canonical escaped paths, and metadata isolation.
- `OutputSchemaCallAdvisor` owns ordinary schema retries, while `StepPromptBuilder` and `StepLoopMissionExecutionEngine#validateOutputSchema` integrate the same contract with planning final responses. Their focused tests protect both prompt paths and retry behavior.
