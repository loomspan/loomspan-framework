---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: canonical-input-admission
---

# Reflected Java Input Contracts

## Applicability

For authored YAML schemas, read [standard schemas](standard-schemas.md). Java
registration generates and compiles canonical input schemas using that same
compiler. Registration, JSON-bound validation and registered Java binding have
focused executable coverage. Local runtime-value admission and reference selection
are also covered using the real validator and traversal sources. The complete
application build, public invocation and actual callback/manager graph remain
unavailable because surrounding binding, attachment, output and planner consumers
are inconsistent with canonical contracts. Local composition does not establish
full dispatch or provider acceptance.

Use this topic for an annotation-defined Java skill. Loomspan reflects its Java signature and `@SkillParam` metadata as the provider-facing schema, planner guidance, and root/child runtime validation contract. There is no companion YAML or input-contract inheritance layer.

For executor YAML input contracts, use [executor leaf declarations](executor-skills.md): their optional `input_schema` uses the shared standard declaration compiler.

This topic covers reflected Java contracts; [standard schemas](standard-schemas.md) defines the authored YAML language.

## Type Decision Table

| Java declaration | Enforced reflected meaning | Authoring decision |
| --- | --- | --- |
| `Object` | Any JSON-compatible value, including null, string, boolean, finite number, object, or array | Use only when the value is deliberately shape-free |
| `Map<String, Object>` | A JSON object with arbitrary string keys whose values may be any JSON-compatible value | Use for genuinely open metadata or heterogeneous dictionaries |
| `Map<String, T>` | A JSON object whose every entry value follows the reflected contract for `T` | Use when keys are open but values have one stable type |
| Record or DTO | A closed JSON object with reflected named properties | SHOULD be the default when planners need discoverable field names and types |
| Array or `List<T>` | A JSON array whose items follow the reflected contract for `T` | Choose `T` to communicate the real item shape |
| UUID | String without an added format assertion; malformed text can still fail Java conversion | Supply a valid UUID string |
| Enum | String choices use Java constant names, honoring effective `JsonProperty` names | Use exactly an advertised choice |
| Recursive DTO | Registration rejects direct, mutual, collection-item and map-value cycles | Use an acyclic exposed input declaration |
| Resource, InputStream or byte[] | Marked string schema recognized before bean traversal | Use the existing runtime-reference path; direct handles require its runtime adapter |

`@SkillParam(required = false)` controls omission at the method-parameter boundary. It does not make a typed value accept JSON null generally. Optional primitive parameters are invalid because omission binds `null`; use the boxed type. A required unconstrained Object parameter must be present but may be null; optional omission stays absent in the input contract. Required controls presence, not a non-null constraint.

## Enforced Semantics

- Java `Object` is emitted as the JSON Schema empty schema `{}` at that value node. The canonical contract retains this meaning and any description; it does not rewrite it to `type: object`.
- Unconstrained leaves declare JSON values, not arbitrary Java objects. Local admission rejects non-JSON objects, non-string map keys and non-finite numbers before conversion, including open undeclared properties and nested lists/maps. Numeric and string keys never collapse into one property. Standard validation preserves values without coercion.
- `Map<String, Object>` does not infer keys, property names, or domain constraints. Scalars, nested objects, arrays, and JSON null values may appear under its string keys.
- Typed maps remain typed. String, integer, number, boolean, enum, DTO, required-field and closed-object constraints apply to every entry value. Numeric strings do not satisfy integer schemas; validation does not coerce values.
- Planner examples and verbose rules label unconstrained leaves as `any JSON value`. An open generic map is not a no-argument tool.
- Root invocation and receiving validation use the same reflected contract. A planning parent with [declared input bindings](input-bindings.md) publishes a separate projected argument contract for that child call. Java does not declare a YAML `input_schema`.
- A root captures its complete skill generation before object conversion and input validation. That captured contract remains authoritative for the admitted invocation and all descendants; a later generation affects only later roots.

## Java declaration and binding fidelity

- Property names come from the application binding mapper, including naming
  strategies, explicit property names and mixins. The dedicated schema mapper
  handles schema JSON. Supply exactly the published names, including nested names.
- Inherited method parameters and DTO members use their resolved generic types.
  Finite nesting such as `Box<Box<String>>` and repeated DTOs in independent
  branches remain valid. Recursive exposed declarations reject at registration
  with method, input path and type-cycle diagnostics; no open-object fallback is
  published. Recursive state inside an executor implementation is not inspected.
- Map declarations permit String keys and raw/Object keys accepting JSON string
  names. Resolved numeric, enum and custom key types reject at registration,
  including nested and inherited generic locations. Custom key deserializers do
  not make those declarations supported. Arbitrary application key-transforming
  customization is not modeled as a schema feature.
- Effective enabled `JsonUnwrapped` rejects at registration, including field,
  getter, mixin, prefix/suffix and nested/container declarations. Disabled
  annotations preserve ordinary nested binding.
- Interface contract parameter names, descriptions and requiredness are selected
  by parameter position, even when implementation names overlap or are swapped.
- Resource, InputStream and byte[] leaves retain internal
  `x-loomspan-runtime-ref-capable` metadata at direct, property, item and map-value
  locations. A nonblank `SkillParam.description` is retained exactly; absent or
  blank text uses the existing reference guidance. Provider schema copies remove
  the marker at actual schema nodes while preserving data properties with the
  same name, descriptions and standard constraints.
- Binding a supported Resource-valued list, map or array retains assignable
  Resource handles without serializing their internals or reading their content.
  Converting a Resource to text/bytes still reads it; managed streams retain
  success/failure cleanup. The local runtime adapter accepts marked direct handles without reads, checks actual strings/nulls against their canonical leaves, then validates surrounding structure using a private view. Original handles reach binding unchanged. Reference selection follows marked leaves; explicit unconstrained/open values keep literal ref:// strings. Complete dispatch remains subject to the integration limitation above.

Minimal Java skill:

```java
@SkillMethod(name = "rankTransportOptions", description = "Rank transport choices.")
Map<String, Object> rank(
        @SkillParam(description = "Compact transport choices.")
        List<Map<String, Object>> options) {
    // deterministic implementation
}
```

The exact callable name is `default-rankTransportOptions`; use it in `SkillTemplate` or a YAML parent's `allowed_skills`. No YAML wrapper is required or accepted.

## Generated Input Guidance

Assigned-tool examples illustrate declared structure; they are not actual argument
values. Optional declared fields may be omitted. Both compact and verbose guidance
list required and optional fields at each object location, including objects inside
arrays and schema-constrained additional values. For ordinary unbound input, child requirements apply when the containing object is supplied. A binding creates its ancestors; remaining required unbound siblings then become required model contributions. See [declared bindings](input-bindings.md).

- At `$` (top level), a closed rule permits only that object's listed keys. Nested
  closed rules apply only at their stated paths. An empty closed root requires `{}`.
- Open objects explicitly permit additional fields with any JSON value. Their
  illustrative fields are not exhaustive; an empty illustration may receive fields.
- Typed additional values follow their own type and nested rules at `.*`. These
  restrictions apply to unlisted fields, not to declared properties with different
  types. `[]` denotes array items; quoted bracket paths distinguish literal dotted
  keys from nested properties. `<key>` is an illustrative key, not a required name.
- Generic contracts omit structured guidance. Verbose correction adds type detail
  while retaining the same object boundaries as ordinary compact guidance.
- Assigned-task guidance explicitly distinguishes required unbound fields, optional
  contributions for contracts that accept `{}`, and exact-empty arguments for
  proven contribution-free tools. Unknown/unsupported summaries retain neutral
  guidance. Read [declared bindings](input-bindings.md#two-contracts-and-dependencies)
  for ownership and optional authored contributions; examples never override task
  instructions or make bound destinations model-owned.
- For supported structured summaries, nonblank authored descriptions appear once per described node in a separate
  section in both compact and verbose assigned-tool guidance, including corrective
  requests. This shared resolved-contract behavior also covers declared YAML
  inputs. Root/object descriptions, properties, array items and typed additional
  values retain their own paths; ancestor descriptions are not copied to children.
  Paths and description text are JSON-quoted, preserving multiline text, whitespace,
  punctuation and literal key characters through escaping. Blank or absent
  descriptions add no text. A described empty closed root retains its empty-object
  rule; generic contracts still omit structured guidance.
  Unknown/unsupported shapes retain their schema text and authored descriptions
  without a structured field summary.

These are descriptions of the resolved contract, not new validation or dataflow
rules. Skill instructions must identify required business data, including any
undeclared fields in open objects. Openness does not require copying all available
context, and Loomspan does not infer or automatically forward business values.
Descriptions explain meaning, units, provenance or intended use; they do not add
validation rules, defaults, data bindings or permission to invent absent values.

`SkillInputPromptRendererTest` protects recursive scoped rules and examples;
`StepPromptBuilderTest#compactAndVerboseSelectionsKeepObjectRulesAcrossPropertyThreshold`
protects detail selection. `StepLoopMissionExecutionEngineTest#preservesScopedGuidanceAndEvidenceThroughObjectBoundaryCorrection`
captures ordinary and corrective requests and checks evidence fidelity and
validation before execution. `SkillInputValidatorTest#ordinaryUnconstrainedLeavesDoNotBypassParentChecks`
protects unchanged enforcement separately from model guidance. This subsection
describes generated guidance for resolved contracts; it does not document the
complete pure-YAML schema language.

`SkillInputPromptRendererTest#preservesDescriptionsAtExactPathsInBothModes` checks
lossless path/text association; `rendersYamlDescriptionsThroughSharedResolvedNodes`
checks manifest resolution. `StepLoopMissionExecutionEngineTest#sendsAuthoredDescriptionsInCompactVerboseAndCorrectiveModelRequests`
captures actual deterministic model requests and validation before execution;
`SkillInputValidatorTest#acceptsEquivalentAuthoredGeneratedJsonValuesAndPresenceWithoutCoercion` covers described unconstrained values without changing admission. Retained prompt tests identify expected prompt coverage,
not measured model accuracy. Live model comparisons are optional and are not a
prerequisite for relying on this rendering behavior.

## Design Guidance

Read [Designing useful input contracts](input-contract-design.md) for choosing
required data, nested object boundaries, and verification that separates structural
validity from source fidelity and model decision quality.

Use a record or DTO when the planner should discover stable fields such as `operator`, `price`, and `durationMinutes`. A generic map truthfully communicates that keys and value kinds are open; a detailed parameter description can give examples, but examples do not become enforced property schemas. Do not use `Object` or a generic map merely to avoid modeling a stable contract.

Use a typed map when arbitrary keys are part of the domain but all values share a type. For example, `Map<String, Integer>` communicates and validates integer values while retaining open keys.

## Known Boundaries

- Loomspan does not infer fixed fields or domain rules from generic-map usage.
- This contract supports JSON-compatible values; arbitrary Java objects and resource/container types are not accepted under unconstrained leaves. Attachment and runtime-reference inputs use their dedicated reflected contracts.
- Requiredness and typed-null behavior outside deliberate unconstrained leaves are unchanged.
- Authored YAML syntax and local validation are documented in [standard schemas](standard-schemas.md).

## Executable Evidence and Implementation Anchors

- `LoomspanMethodInputSchemaGeneratorTest` covers canonical Object/map semantics,
  exact typed-map values and recursive declaration rejection.
- `JavaInputDeclarationTest` covers actual registration, canonical JSON validation,
  effective naming/generics, enum/UUID binding, declaration restrictions,
  descriptions, Resource identity, proxy advice and executor handoff. Runtime
  handles in these fixtures are direct/already resolved binder inputs.
- `SkillMethodBeanPostProcessorTest` preserves registration, conversion, failure
  and stream cleanup controls using the real postprocessor.
- `StandardSchemaCompilerTest` covers standard presence/null rules, described
  unconstrained leaves, role restrictions, parent bounds and exact assertions.
- `LoomspanMethodInputSchemaGenerator`, `SkillMethodBeanPostProcessor` and
  `CanonicalSchema` own canonical Java declaration publication and binding.
  The validator, local reference selection and argument reader have focused coverage; full resolver/session, callback/manager and prompt-renderer integration remain unavailable;
  retained integration tests named in the guidance section identify consumers rather
  than a green complete runtime path.
