# TBD-06 research: a standard JSON Schema foundation

Continuation: read the roadmap's [November 11 handoff](spring-ai-responses-migration.md#continuation-handoff--november-11-2026)
for current decisions, rationale, evidence boundaries and remaining TBD-13/14 work.
Accepted roadmap decisions supersede historical research proposals below.

Resource-limit follow-up: [Validation resource-limit evidence and v1 decision](#validation-resource-limit-evidence-and-v1-decision).

Research date: November 10, 2026. **Draft 2020-12 and NetworkNT Jackson 3 are now
agreed, as are the simple v1 scope, standard format assertions without conversion,
root-object contracts, and unlimited evaluation depth/steps. TBD-06 policy decisions
are resolved; implementation and verification remain outstanding.**
Companion to [the migration roadmap](spring-ai-responses-migration.md#tbd-06-initial-json-schema-profile-and-validation-behavior).
No migration implementation, dependency change, implementation tickets, model downloads,
or paid/cloud model calls were performed. Other TBD decisions are not resolved here.

## Recommendation and evidence boundary

Recommend **JSON Schema Draft 2020-12**, authored as YAML, represented internally as
a JSON-compatible tree, with **NetworkNT `com.networknt:json-schema-validator:3.0.9`**
as the first implementation candidate. Use the same parser, schema compiler, and
instance validator for invocation inputs, tool arguments, model contributions,
composed outputs, and planning. The user subsequently accepted this foundation,
with 3.0.9 as the verified starting version; the final pin still requires full
framework platform validation. Other recommendations below remain proposals unless
explicitly resolved in the roadmap.

Keep standard semantics, including optional properties, explicit null unions, open
objects, nested arrays, typed maps, and composition. Usage restrictions
belong at the consuming boundary. Do not turn OpenAI strict-output requirements into
a framework-wide authoring policy. A useful constraint remains useful when generation
does not enforce it, provided local acceptance does. Rejected keywords and ignored
keywords require different handling.

The most consequential source findings are adapter limitations:

* Spring AI 2.1.0-RC2 can carry an OpenAI Responses output schema without rebuilding
  its properties. Its function tools explicitly default to non-strict mode.
* Anthropic's tool conversion copies only root `properties` and `required`,
  losing ordinary root constraints such as `additionalProperties`.
* Gemini's RC2 adapter converts output schemas to the SDK's narrower `Schema` class
  before setting `responseJsonSchema`. Tools additionally pass through an OpenAPI
  converter. The SDK itself has raw JSON-schema fields. Provider support therefore
  exceeds what this adapter path can faithfully represent.

These are reasons to verify or repair narrowly scoped adapter serialization, not to
invent a second schema language, flatten every schema, or ban useful standard features.
Prefer an upstream fix or a suitable fixed Spring AI release. No fix is implemented
or authorized by this report. See [adapter evidence](#adapter-and-sdk-evidence).

Repository evidence is from working tree based on commit
`c8fdebf2061bf4d1b361ab1c5a6ce1330e79c9b7`, including existing untracked roadmap/probe
documents. Both repository AGENTS files and the entire roadmap were read first.
Published source artifacts were inspected, not just current `main` branches.
Provider documentation was fetched on the research date; it is moving documentation,
not a versioned conformance certificate. No provider/schema combination was live-tested.

## Repository inventory

Paths below are relative to the repository root; method names identify inspected
behavior. This inventory includes declaration sources, generated contracts, consumers,
and regression surfaces, rather than only files named “schema.”

### Declarations and current validation

| Surface | Repository evidence | Findings and migration relevance |
| --- | --- | --- |
| YAML model/executor skills | [YamlSkillManifest](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java), [YamlSkillCatalog](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java), [YamlSkillDefinition](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java) | Separate typed `InputSchemaManifest` and `OutputSchemaManifest`; six ordinary types, input-only `attachment`, string enums; output-only `nullable` and `evidence`. Root objects required; nested arrays rejected; required names must be declared properties; case-folded duplicate property names rejected. Output objects acquire `additionalProperties: false` when omitted; input objects remain open by default. |
| Actual YAML fixtures | [input-schema-skill](../../../src/test/resources/skills/valid/input-schema-skill.yaml), [output-schema-complex-skill](../../../src/test/resources/skills/valid/output-schema-complex-skill.yaml), [attachment-input-skill](../../../src/test/resources/skills/valid/attachment-input-skill.yaml), [property evidence](../../../src/test/resources/skills/valid/output-schema-property-evidence-types-skill.yaml) | Payload string/optional boolean; invoice extraction with optional dates/notes and nested line-item objects; image references; evidence on immediate output properties. These are real uses to retain, not hypothetical schema benchmarks. |
| Input contract conversion | [SkillInputContractResolver](../../../src/main/java/ai/loomspan/internal/runtime/input/SkillInputContractResolver.java), [SkillInputSchemaNode](../../../src/main/java/ai/loomspan/internal/runtime/input/SkillInputSchemaNode.java) | Converts YAML/custom nodes and Java JSON schemas into a reduced tree. Array-valued `type` is not retained as a union; unrecognized keywords disappear. `dispatchProofSupported` records uncertainty separately. Replacing only the validator leaves lossy conversion in place. |
| Input acceptance | [SkillInputValidator](../../../src/main/java/ai/loomspan/internal/runtime/input/SkillInputValidator.java) | Validates maps, coerces integer/number/boolean strings, normalizes dates, and admits selected runtime objects. Author-bound paths use an exact policy with different coercion rules. Required null is generally reported as missing; numeric Java representation affects integer acceptance. |
| Output acceptance/correction | [OutputSchemaValidator](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaValidator.java), [OutputSchemaPromptAugmentor](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaPromptAugmentor.java), [OutputSchemaCallAdvisor](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java) | Handwritten structure checks, case-insensitive output property matching, custom null handling, prose schema rendering, bounded correction and composition. `format` is not an output assertion. Keep correction policy and diagnostics; replace custom structural semantics. |
| Loading/parsing | [LoomspanJacksonCodecs](../../../src/main/java/ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java) | Jackson 3 (`tools.jackson`); YAML duplicate detection and typed-manifest unknown-property rejection. A raw schema tree needs meta-schema validation; preserving arbitrary keys alone does not validate a declaration. |

Search census, not a count of independently valid schemas: under `src/test/resources`,
8 files contain `input_schema:` and 31 contain `output_schema:`; under
`src/main/resources`, neither declaration occurs. Twenty Java test files contain
embedded input/output YAML declarations. `agent-skills` has four files containing
input examples (10 occurrences) and four containing output examples (7 occurrences).
The fixture corpus also embeds schema metadata in JSON/YAML strings. Counts include
intentional invalid fixtures and exclude dynamically generated schemas.

The invalid YAML corpus explicitly exercises unsupported keywords, array roots,
nested arrays, object enums, duplicate-case fields, required names without properties,
attachment metadata, and evidence placement. Those expectations need deliberate
replacement; they are not proof that standard features are undesirable.

### Java, bindings, attachments, runtime references, and planning

| Surface | Repository evidence | Findings and migration relevance |
| --- | --- | --- |
| Java-generated tool inputs | [LoomspanMethodInputSchemaGenerator](../../../src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java), [SkillMethodBeanPostProcessor](../../../src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java) | Generator already labels schemas 2020-12. Produces closed parameter/bean objects, required properties, string enums, arrays, schema-valued `additionalProperties` for maps, and `{}` for Java `Object`. Recursive Java types currently terminate as an unconstrained object; report unsupported recursive types rather than silently weaken contracts. `SkillParam` changes names/descriptions/requiredness. Runtime-capable types are rewritten to strings with `x-loomspan-runtime-ref-capable`. Generated and authored schemas should enter the same standard compiler; Java reflection remains a declaration source. |
| Child argument bindings | [ChildInputBindingProjection](../../../src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java), [ChildInputBindingDeclarations](../../../src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingDeclarations.java), [ChildInputBindingAssembler](../../../src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingAssembler.java) | Project model arguments, reserve framework-owned destinations, select parent input or accepted child results, then validate assembled receiving input. The advertised schema currently inserts boolean `false` at reserved properties. Closed/all-bound shape proof controls dispatch eligibility. Ordinary JSON Schema validation is not schema containment proof or binding-path analysis. |
| Output ownership | [OutputBindingProjection](../../../src/main/java/ai/loomspan/internal/outputschema/OutputBindingProjection.java), [OutputBindingComposition](../../../src/main/java/ai/loomspan/internal/outputschema/OutputBindingComposition.java), [SkillDependencyValidator](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java) | Removes bound properties and adjusts required ancestors/nullability; checks prohibited model overrides; composes exact values. `output_from` delegates result/schema authority to a producer. Projection assumes a simple property tree and case-insensitive output paths. Open objects, unions, and cross-property assertions complicate projection. |
| Runtime references | [RefResolver](../../../src/main/java/ai/loomspan/internal/vfs/RefResolver.java), Java postprocessor above | `ref://` is a runtime execution value, separate from schema syntax. Generic input recursively resolves refs; explicit contracts traverse selected properties/items/typed maps and resolve marked nodes. Resolved `Resource`, `InputStream`, and byte arrays are not JSON instances. Do not validate them by inventing JSON Schema types. |
| Attachments | [DefaultMissionInputMaterializer](../../../src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java), attachment fixture above | Consumes input schema metadata, resolves a resource/reference, checks content type/media signature/size, and builds model attachments. JSON Schema can constrain the reference string, not perform resource authorization or MIME/materialization checks. Preserve these services separately. |
| Tool exposure/execution | [BoundCapability](../../../src/main/java/ai/loomspan/internal/runtime/tool/BoundCapability.java), [SpringAiToolCallbackAdapter](../../../src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java), [ExecutionCoordinator](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java) | Schema strings cross capability metadata into provider tools; local validation and canonical authorization remain essential. The same child contract is also described to planning/step execution. |
| Planner control tool | [PlannerInabilityTool](../../../src/main/java/ai/loomspan/internal/springai/PlannerInabilityTool.java) | Handwritten JSON tool schema plus dedicated argument/control handling. Include in serializer fixtures; it is not a skill output schema. |
| Planning generation | [DefaultPlanningService](../../../src/main/java/ai/loomspan/internal/runtime/planning/DefaultPlanningService.java), [ExecutionPlan](../../../src/main/java/ai/loomspan/internal/core/ExecutionPlan.java), [PlanTask](../../../src/main/java/ai/loomspan/internal/core/PlanTask.java) | Currently a prompt example, JSON/YAML parser, fence unwrapping, status aliases, and tree-to-record conversion. Runtime generates `planId`. Initial contract requests root `capabilityName`, `createdAt`, `status: VALID`, and tasks with `taskId`, `title`, `status: PENDING`, `capabilityName`, `intent`, `dependsOn`, `expectedOutputs`, `parallelGroup`, `note`. |
| Planning semantics | [PlanStructureValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanStructureValidator.java), [PlanTaskConstraintValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanTaskConstraintValidator.java), [PlanInputBindingDependencyValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanInputBindingDependencyValidator.java), [PlanOutputBindingProducerValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanOutputBindingProducerValidator.java) | Unique task IDs, allowed capabilities, dependency ordering/cycles, contiguous parallel units, task counts, binding producers/dependencies, and evidence coverage are not replaced by structural validity. Initial plan schema must not permit every later execution status simply because the record enum does. |
| Evidence-backed outputs | [EvidenceBackedOutputValidator](../../../src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceBackedOutputValidator.java), [EvidenceContractCallAdvisor](../../../src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceContractCallAdvisor.java) | Validate present output claims against successful skill evidence after structural acceptance. Move custom schema metadata deliberately, but retain evidence expression parsing, claim coverage, and correction handling; a standard schema cannot establish that a tool actually succeeded. |

### TypeSafe, Console, and documentation consumers

* [TypeSafeDeclaration](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java)
  requires JSON-compatible request state, substitutes `${/input/...}` references, and
  derives descriptive output schemas from `noul`, `choice`, and `score` questions.
  Fixed criteria produce enumerated choices/probability keys; dynamic criteria produce
  open maps; score results include a legend. Probability bounds currently appear in
  descriptions, not structural assertions. Its generated result tree uses the old
  `OutputSchemaManifest` and therefore must migrate too.
  [TypeSafeExecutor](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java)
  remains a separate native evaluator, not a chat-model adapter. The catalog requires
  TypeSafe input schemas, rejects attachments, and rejects authored output-schema
  configuration for TypeSafe skills. Do not add chat structured-output options or
  a new authored TypeSafe result contract incidentally.
* [DefaultSkillCatalog](../../../src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java)
  exposes input/output schema metadata; [DefaultRegisteredSkillCatalog](../../../src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java)
  and [ObservabilityDtoMapper](../../../src/main/java/ai/loomspan/internal/observability/web/ObservabilityDtoMapper.java)
  expose original YAML and effective output metadata to Console.
  [SkillDetail.tsx](../../../loomspan-console/web/src/observability/SkillDetail.tsx)
  displays them inertly; [TraceRecords.tsx](../../../loomspan-console/web/src/observability/TraceRecords.tsx)
  displays schema outcomes/issues. Preserve original author text and diagnostic
  content; update contracts/fixtures when diagnostic shapes change. Inspection found
  display/source handling here, not a separate browser JSON Schema validator.
* Console's [MCP output schemas](../../../loomspan-console/internal/mcpadapter/output_schemas.go)
  use `github.com/google/jsonschema-go/jsonschema` for reflected DTO validation and
  compact advertised schemas. These are Console MCP response contracts, not Loomspan's
  custom skill dialect. Do not replace the Go validator with Java or treat the MCP
  schema builder as an obsolete chat integration. Its schema strings and diagnostic
  DTO consumers still need regression coverage when the framework format changes.
  The Console agent-eval JSON schemas are also separate contracts, not migration targets
  merely because they are schemas.
* Migrate [output-contracts](../../../agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md),
  [input-bindings](../../../agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md),
  [evidence-contracts](../../../agent-skills/loomspan-docs/references/skill-authoring/evidence-contracts.md),
  [typesafe-skills](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md),
  executor examples, README, and fixture expectations with the implementation.
  `OutputBindingCompositionTest`, `DeclaredOutputBindingsPlanningIntegrationTest`,
  `LoomspanMethodInputSchemaGeneratorTest`, `OutputSchemaValidatorTest`, and Console
  fixture/parity tests are existing behavior evidence, not tests run by this research.

## Semantic changes and proposed examples

All “after” snippets in this section are **proposals**, not accepted syntax changes
to framework metadata or implementations. Standard-schema behavior itself follows
the already settled decision.

| Existing behavior | Standard-schema consequence |
| --- | --- |
| Output objects default closed; input objects default open | Omitted `additionalProperties` permits extras in both. Explicitly add `false` to migrated output declarations that intend closure; do not silently inject it at runtime. |
| Output field matching ignores case; loader disallows case-only names | `name` and `Name` are distinct. Required `name` is not satisfied by `Name`; openness determines whether the latter is otherwise permitted. Update binding/evidence path matching too. |
| `nullable: true` bypasses output node validation for null | Use `type: [string, "null"]` or a standard union. `required` controls presence only. If `enum` is also present, include null in the enum to permit it. |
| Numeric/boolean strings and flexible input dates are normalized | JSON string `"3"` is not integer `3`; validation does not coerce it. Format validation never reformats a value. Java binding is a later execution step and must not conceal a failed JSON contract. |
| Integer checks depend on Java/Jackson integral representation | Standard integer is a number with no fractional component; JSON `1.0` satisfies integer semantics. Test numeric precision and enum equality. |
| Nested arrays, non-string enums, undeclared required names rejected | Standard semantics allow them. A required name need not occur in `properties`; it may be covered by an open or other applicable schema. Some combinations are unsatisfiable, not malformed JSON Schema. |
| `format` input date check versus output annotation | Agreed: standard format assertions consistently for inputs and outputs, without date normalization or other conversion. The initial annotation-only recommendation was not adopted. |
| `type: attachment`, `media_type`, `allowed_content_types`, property `evidence` | These are framework execution metadata, not standard structural keywords/types. Retain behavior in a separate metadata layer; attachment placement is the agreed x-loomspan-attachment annotation; evidence placement is the agreed x-loomspan-evidence annotation under TBD-13. |
| Java-generated schemas lose unsupported keywords in conversion | Preserve the canonical tree; do not round-trip through `SkillInputSchemaNode` and claim the original contract was validated. |

**Proposal A — preserve an intended closed, optional, nullable output.**

Before (representative of the current output dialect):

```yaml
output_schema:
  type: object
  properties:
    notes: {type: string, nullable: true}
```

After:

```yaml
output_schema:
  $schema: https://json-schema.org/draft/2020-12/schema
  type: object
  properties:
    notes: {type: [string, "null"]}
  additionalProperties: false
```

Both `{}` and `{"notes": null}` satisfy the proposed contract. Adding
`required: [notes]` is a separate author decision. This optional contract is useful
locally and for Anthropic; it is not directly compatible with OpenAI strict output.
Do not convert absence into null behind the author's back.

**Proposal B — replace coercion with an explicit data contract.**

```yaml
input_schema:
  type: object
  required: [count]
  properties:
    count: {type: integer, minimum: 1}
    deliveryDate: {type: string, format: date}
```

The old integer validator could accept `count: "3"`; the proposed contract requires
`count: 3`. `deliveryDate: "11/10/2026"` must not become `"2026-11-10"` as a validation
side effect. Under annotation-only `format`, neither spelling is rejected by that
keyword alone; under the alternative assertion policy, the nonstandard spelling
fails. This distinction must appear in migrated tests and author documentation.

**Attachment declaration — accepted replacement (TBD-13).**

```yaml
input_schema:
  type: object
  required: [image]
  properties:
    image:
      type: string
      x-loomspan-attachment:
        media_type: image
        allowed_content_types: [image/jpeg]
  additionalProperties: false
```

This supersedes the earlier separate input_attachments map and pattern-based example.
Metadata remains beside its node and follows properties/items for nested/array inputs.
The provider schema copy omits the framework annotation; standard assertions remain.
Preserve direct Resource inputs through the attachment-only validation adapter and
existing materialization checks described in TBD-13. No fake references or Resource
stringification. Native generated attachments are deferred beyond v1.

**Proposal D — bindings and planning.**

A skill with model-owned `/summary` and framework-owned `/invoice` should retain one
full authored output contract. For a simple explicit property tree, derive a model
contribution schema excluding `/invoice`, reject model attempts to supply it, insert
the accepted producer value, and validate the full result. If `/invoice` is introduced
through `allOf` or participates in `dependentRequired`, deleting a `properties` entry
is not a correct projection. Restrict ambiguous binding combinations or request an
explicit contribution contract in a later design discussion; do not build an arbitrary
schema algebra to guess one.

For planning, a framework-owned standard schema can describe the existing initial
plan object and task array. Use exact initial status enums (`[VALID]`, `[PENDING]`),
string arrays for dependencies/expected outputs, and a string-or-null parallel group
with the existing pattern. Exclude runtime-generated `planId`. A shared native-output
planning contract may deliberately author all fields as required and represent absent
group/note values as null, with closed objects. That is an explicit change to this
one framework-owned contract for OpenAI compatibility, not an implicit rewrite of
user schemas. Requiredness of `note`, `intent`, `createdAt`, and `parallelGroup` needs
review against existing constructor/parser behavior. Keep semantic plan validators.

## Proposed local feature profile

Use the standard draft meta-schema and standard instance semantics; do not advertise
every provider as supporting the draft. The earlier broad local-feature proposal is
superseded by the user's simpler v1 scope below. Historical research and probe coverage
of additional keywords is evidence about the libraries, not product-scope approval.

### V1 schema scope — agreed

**Root shapes — agreed for v1:** input, output, tool-argument, and planning
contracts require `type: object` at the root. Arrays and scalars remain supported
as nested properties under the approved v1 profile. Apply this rule consistently to
authored and generated contracts; reject unsupported root shapes at schema loading
or declaration registration. Do not automatically wrap or convert root values.
Broader root shapes are deferred until a concrete use case justifies them.

V1 uses simple, readable property-tree schemas with roughly the expressiveness of
current skill contracts, expressed in standard JSON Schema. This scope applies to
input, output, and framework-generated planning schemas. Provider compatibility is
the goal; adopting the full JSON Schema vocabulary is not. Use the shared validator,
without building a custom dialect or a general schema transformation system.

| Feature | V1 scope |
| --- | --- |
| Objects | Named `properties`, nested objects, `required`, and boolean `additionalProperties`, including explicit `false`. Ordinary inputs also permit schema-valued `additionalProperties`; native output/planning does not. Preserve standard omission semantics. |
| Types | Strings, integers, numbers, booleans, objects, and arrays; explicit nullability via a type plus `null`, for example `type: [string, "null"]`. General mixed-type unions are deferred. |
| Arrays | Homogeneous `items`, including nested objects and arrays. |
| Choices and guidance | Simple scalar `enum` choices and field `description`. |
| Formats | Use NetworkNT's standard format validators with assertions enabled and no conversion. Reject unsupported format names at schema loading/registration; no custom formats. |
| Simple bounds | `minimum`, `maximum`, `minLength`, `maxLength`, `minItems`, and `maxItems`. Validate locally and document native provider coverage; do not promise uniform native enforcement. |

Defer general `anyOf`, `oneOf`, `allOf`, conditionals, tuple schemas, and pattern-based
property rules. Schema references and `default` remain excluded as already decided.
Other features from the broader research proposal are not implicitly approved for v1;
flag a concrete existing use or implementation need for discussion before adding one.
The validator library supporting a keyword does not make it a supported Loomspan feature.
Report out-of-scope constructs clearly rather than silently ignoring them.

**Typed maps are supported for ordinary input contracts — agreed November 11, 2026.**
Permit schema-valued `additionalProperties` in generated and authored input schemas,
including ordinary tool arguments. Value schemas use the otherwise approved input
features; this does not authorize references, general composition or arbitrary keywords.
Preserve existing SkillMethod typed-map parameters and Java binding. Do not replace
value constraints with boolean openness, convert maps to key/value arrays, or change
method signatures merely for this migration. Native structured output/planning typed
maps remain deferred. This supersedes the earlier blanket input/output/planning ban.

Rationale: strict output restrictions do not establish ordinary non-strict tool-input
restrictions. RC2 Responses preserves schemas with explicit tool strict=false;
Anthropic preserves nested map schemas but loses unrelated root constraints; Gemini's
OpenAPI/SDK Schema tool conversion loses map constraints. Google SDK raw
parametersJsonSchema preserves them in offline captures. Fix schema transport instead
of deleting existing input functionality. These captures do not prove live acceptance.
See TBD-13's accepted developer model and mandatory tests for implementation scope.

Flag existing generated unconstrained schemas or internal projection needs outside
this baseline; they do not automatically expand authoring scope.

**Root shapes — agreed for v1:** input, output, tool-argument, and planning
contracts require `type: object` at the root. Arrays and scalars remain supported
as nested properties under the approved v1 profile. Apply this rule consistently to
authored and generated contracts; reject unsupported root shapes at schema loading
or declaration registration. Do not automatically wrap or convert root values.
Broader root shapes are deferred until a concrete use case justifies them.

The earlier proposal to accept arbitrary JSON roots is superseded.

**Format decision — agreed November 10, 2026:** enable standard format assertions
consistently in the shared validator for input and output schemas, without automatic
conversion, coercion, or normalization. The earlier annotation-only recommendation
was not adopted. Only declared formats are checked; a date-like field name does not
add a constraint. Plain strings remain available for alternate representations;
arbitrary patterns are outside the approved v1 baseline. Calendar/representation validity is not business correctness.
Use NetworkNT's standard format validators with assertions enabled. Reject unknown
or unsupported format names at schema loading or Java declaration registration,
including nested schemas; do not silently accept them as annotations. Document and
test the supported list against the pinned NetworkNT version. Add no custom formats
or different input/output policies. Native provider enforcement remains a separate concern.

Schemas are authored inline. Schema-reference support and its resolution/preflight
work are excluded by user decision. Reject unsupported constructs clearly. Preserve
strings and numeric precision; reject non-JSON YAML values, duplicate keys and
alias cycles. Do not sanitize schema descriptions.

Schema `default` support is deferred beyond v1 for input, output, and planning
contracts. Do not implement automatic value insertion or advertise annotation-only
support. Treat the keyword as unsupported with a clear schema-loading diagnostic,
rather than silently accepting a declaration developers may expect to apply values.
Revisit after building more applications with the framework. Future consideration
must distinguish absent values from explicit null/uncertainty, application defaults
from factual model output, and define validation order, nested-object behavior,
binding interactions, and diagnostic provenance. The earlier annotation-only
recommendation and subsequent automatic-default proposal are not adopted v1 behavior.
This deferral concerns schema values, not model-option or configuration defaults.

## Provider feature matrix

Legend: **D** documented support; **R** documented rejection/restriction; **I** documented
ignored/weaker constraint; **U** not established. None means live-tested. Tables concern
schema-bearing final output/planning unless specified. SDK/adapter fidelity is a separate
gate below. Sources: [OpenAI](https://developers.openai.com/api/docs/guides/structured-outputs),
[Anthropic](https://platform.claude.com/docs/en/build-with-claude/structured-outputs),
[Gemini guide](https://aistudio.google.com/docs/structured-output), and the versioned
[Google SDK 1.73.0 source](https://repo.maven.apache.org/maven2/com/google/genai/google-genai/1.73.0/google-genai-1.73.0-sources.jar).

| Feature | OpenAI strict output | Anthropic JSON output | Gemini raw JSON-schema output |
| --- | --- | --- | --- |
| Ordinary types | D | D, including null | D |
| Root | Object; no root `anyOf` | U for unrestricted roots | Object/array examples; other roots probe |
| Optional properties | R: all properties required | D | D |
| Null union | D | D | D; adapter issue below |
| `additionalProperties` | Must be false | Must be false | D boolean/schema |
| Enum | D; complex-value cases U | D scalar values; R complex | D string/number; others U |
| Arrays/items | D | D | D including `prefixItems` |
| `minItems` / `maxItems` | D, model caveat below | Only min 0/1; otherwise R | D |
| Numeric bounds / `multipleOf` | D | R | Bounds D; `multipleOf` U |
| String `pattern` | D | D restricted regex | U on raw endpoint |
| String length | U for full ordinary-model coverage | R | I |
| Property counts | U | U | I |
| `uniqueItems` / `contains` and bounds | U | R array constraints | I |
| `format` | D enumerated formats | D enumerated formats | D limited formats |
| `default` | U native treatment | D | Annotation; no insertion claim |
| `anyOf` | D nested | D | D |
| `allOf` | R | D for inline schemas; verify combinations | U |
| `oneOf` | U | U | I: SDK contract says treated as `anyOf` |
| `not`, conditionals, dependent keywords | R listed keywords | U | U |
| `unevaluated*`, boolean schemas | U | U | U |

For OpenAI, fine-tuned models have additional string/numeric/array restrictions.
Check the exact model; ordinary-model documentation is not a fine-tuned guarantee.
The table does not establish support for unlisted formats, arbitrary regex dialects,
or unlimited schema size. U is not permission to silently strip keywords.
OpenAI's listed formats are date-time, time, date, duration, email, hostname, IPv4,
IPv6, and UUID; Anthropic additionally lists URI. These are provider lists, not a
proposed reduction of the local validator's standard formats.

Anthropic documents the same subset for strict tools and JSON output.
Unsupported constraints return errors,
so numeric constraints cannot be described as “accepted but ignored.”

Gemini's current guide uses **Interactions** examples, while the selected Spring AI
adapter uses **generateContent**. Do not assume the selected generateContent adapter supports the newest guide. The ignored length/count/array constraints above are guide evidence;
confirm them on the selected generateContent model before claiming that behavior.

### The four routes behind `openai`

| Route | Output evidence | Tool evidence and model/route limit |
| --- | --- | --- |
| OpenAI direct | Strict subset above through `text.format` | Non-strict tool schemas are distinct from strict generation. RC2 explicitly sends false unless configured; TBD-08 now defers native strict tools beyond v1. |
| Meta direct | [Responses schema reference](https://dev.meta.ai/docs/api-reference/responses/schemas) defines `TextResponseFormatJsonSchema`, schema, and strict subset, but does not establish a keyword-by-keyword parity table. [Structured-output tutorial](https://dev.meta.ai/docs/structured-output) uses Chat Completions with `muse-spark-1.3`; do not substitute that for Responses acceptance. | Responses function parameters are JSON Schema; documented strict default is true, overridden by RC2's explicit false. Optional/null/open/composition/constraint behavior remains U for the selected Responses model. |
| OpenRouter | [Structured-output guide](https://openrouter.ai/docs/guides/features/structured-outputs) documents `text.format`; native enforcement, translation, and hint-only behavior vary by upstream. No universal keyword table is justified. | Record exact model and endpoint/provider route. `require_parameters` can constrain routing but does not establish keyword enforcement. A hint-only upstream cannot pass native-output acceptance merely because local validation succeeds. No registry or automatic switch is proposed. |
| Ollama | [Compatibility docs](https://docs.ollama.com/api/openai-compatibility) describe stateless Responses. [Pinned converter](https://github.com/ollama/ollama/blob/8a89b76cc7359805e48357ba7e5b99e845a4c211/openai/responses.go) forwards `text.format.schema` to native `ChatRequest.Format`; this does not prove grammar keyword enforcement. | The earlier 0.40.16 / `qwen3.5:4b-q4_K_M` probe tested tool/reasoning replay, not structured output. Keyword semantics, schema-plus-tools, and strictness remain U on that installed combination. No Ollama runtime branch is proposed. |

### Input and tool contexts

Root invocation input is a **local** validation concern; it is not sent to a provider
as a response schema. Model-generated child arguments use the projected receiving
contract, then local assembly/validation. Do not impose output restrictions on these
inputs by analogy.

| Context | What is established | Consequence |
| --- | --- | --- |
| OpenAI Responses tools | RC2 copies schema entries into SDK `FunctionTool.Parameters`, strict false by default | Broad local input semantics can coexist with non-strict model arguments. Passing a schema does not prove model enforcement. |
| Anthropic tools | RC2 rebuilds root `properties`/`required` and omits other root constraints | Nested literal schemas may survive while root openness or composition disappear. Fix/verify serialization before claiming the tool contract is carried. Native strict tools are deferred beyond v1 under TBD-08. |
| Gemini tools | Provider [FunctionDeclaration](https://aistudio.google.com/api-reference/caching#Resource:FunctionDeclaration) has `parametersJsonSchema` for object arguments; SDK 1.73.0 exposes it | Spring AI still uses `parameters(Schema)` and an OpenAPI conversion wrapper. Raw output support does not prove raw tool support. |
| TypeSafe | Native evaluation declaration and result processing | Shared local input schema and derived standard metadata; no chat output/tool strictness setting. |

## Adapter and SDK evidence

The repository pins Java 21, Boot 4.1.0, Spring AI 2.0.0, and Jackson 3. The roadmap's
candidate is 2.1.0-RC2, not an accepted upgrade. Published module POMs/source JARs show:

| Integration | Spring AI 2.0.0 SDK dependency | Spring AI 2.1.0-RC2 SDK dependency |
| --- | --- | --- |
| OpenAI | `openai-java-core:4.39.1` | `openai-java-core:4.80.0` |
| Anthropic | `anthropic-java-core:2.40.1` | `anthropic-java-core:3.4.0` |
| Gemini | `google-genai:1.58.0` | `google-genai:1.73.0` |

These are **published dependencies**, not a newly resolved Loomspan effective tree.
Boot overrides/transitives still require Phase 1 verification. Source inspection of
2.0.0 confirms the Anthropic root-field loss and Gemini typed-schema routes already
exist there; the RC2 bump alone does not fix them.

1. **OpenAI:** `responses/ResponsesRequestBuilder.format()` parses the schema into
   `ResponseFormatTextJsonSchemaConfig.Schema`, sets `text.format` with name and strict
   (default true). SDK 4.80.0's schema is an arbitrary-property map. `functionTool()`
   copies root entries into `FunctionTool.Parameters` and sets strict explicitly.
   This is source evidence of transport capability, not validation or all-keyword
   provider support. Use the authored schema, not SDK class-derived strictification.
2. **Anthropic output:** native `outputConfig(...)` carries an intact raw schema.
   Preserve existing effort when replacing the per-call format; executable evidence
   below confirms this option-combination requirement.
3. **Anthropic tools:** `AnthropicChatModel.toAnthropicTool()` hardcodes a new input
   schema from properties and required, with no copy of other root entries and no
   strict setting. A local output-config workaround does not repair this tool mapper.
   Prefer an upstream mapper fix; quantify dropped fields with a mock HTTP fixture.
4. **Gemini output:** `GoogleGenAiChatModel.jsonToSchema()` deserializes into
   `com.google.genai.types.Schema`. That class has a single `Type`, `nullable`, string
   enums, and a finite property set, lacking `additionalProperties`.
   `responseJsonSchema(jsonToSchema(...))` therefore cannot be assumed to preserve a
   standard raw schema. Exact drop-versus-deserialization-error behavior needs a fixture.
5. **Gemini tools:** the model wraps the manager in `GoogleGenAiToolCallingManager`;
   `resolveToolDefinitions()` calls `JsonSchemaConverter.convertToOpenApiSchema()`,
   changes type casing, and later uses `parameters(Schema)`.
   SDK 1.73.0 `GenerateContentConfig.responseJsonSchema(Object)` and
   `FunctionDeclaration.parametersJsonSchema(Object)` can carry raw maps. A focused
   upstream use of those fields is preferable to expanding the OpenAPI converter.
   No ready Loomspan options-only workaround is established for both paths.

Exact source artifacts:

* [Spring AI OpenAI RC2](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.1.0-RC2/spring-ai-openai-2.1.0-RC2-sources.jar)
  and [POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.1.0-RC2/spring-ai-openai-2.1.0-RC2.pom).
* [Spring AI Anthropic RC2](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-anthropic/2.1.0-RC2/spring-ai-anthropic-2.1.0-RC2-sources.jar)
  and [POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-anthropic/2.1.0-RC2/spring-ai-anthropic-2.1.0-RC2.pom).
* [Spring AI Gemini RC2](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-google-genai/2.1.0-RC2/spring-ai-google-genai-2.1.0-RC2-sources.jar)
  and [POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-google-genai/2.1.0-RC2/spring-ai-google-genai-2.1.0-RC2.pom).
* [OpenAI SDK 4.80.0](https://repo.maven.apache.org/maven2/com/openai/openai-java-core/4.80.0/openai-java-core-4.80.0-sources.jar),
  [Anthropic SDK 3.4.0](https://repo.maven.apache.org/maven2/com/anthropic/anthropic-java-core/3.4.0/anthropic-java-core-3.4.0-sources.jar),
  and [Google SDK 1.73.0](https://repo.maven.apache.org/maven2/com/google/genai/google-genai/1.73.0/google-genai-1.73.0-sources.jar).
* Baseline published sources: [OpenAI 2.0.0](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.0.0/spring-ai-openai-2.0.0-sources.jar),
  [Anthropic 2.0.0](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-anthropic/2.0.0/spring-ai-anthropic-2.0.0-sources.jar),
  [Gemini 2.0.0](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-google-genai/2.0.0/spring-ai-google-genai-2.0.0-sources.jar).
  Embedded `META-INF/maven/.../pom.xml` supplies each baseline SDK version.

## Validator comparison and proposed configuration

| Candidate | Draft/platform | Diagnostics and integration | Integration/maintenance/license assessment |
| --- | --- | --- | --- |
| **NetworkNT 3.0.9** | Drafts 4/6/7/2019-09/2020-12; Java 17+, Jackson 3. Published POM uses Jackson 3.1.8. | Instance/evaluation/schema locations, keyword and details; configurable format assertions; explicit execution limits in release documentation. | Best fit for existing Jackson trees and Java 21. Apache-2.0. Current 3.x line; do not assume 2.x snippets are API-compatible. Requires regex selection and diagnostic adaptation. |
| **json-sKema 0.25.0** | 2020-12; Kotlin implementation, JVM target 1.8 in published POM, own JSON value model and optional YAML support. | Validation failure tree with locations; loaders and URI pre-registration; configurable format policy. | Credible modern alternative, MIT. Adds Kotlin runtime and tree conversion/parsing versus direct Jackson integration. Older-draft migration support is not needed here. |
| **dev.harrel json-schema 1.9.1** (README version) | Drafts 4 through 2020-12; Java 8 core, pluggable providers including a Jackson 3 provider. | Structured errors/annotations and internationalization; provider API needs a small integration spike. | Credible maintained alternative, MIT, small dependency core. Additional provider setup; verify published Jackson 3 module/version alignment. Documentation site could not be fetched in this research, so diagnostic API detail remains less verified than NetworkNT. |
| **Everit** (maintenance line) | Drafts 4/6/7, `org.json` tree; no 2020-12 | Exceptions with pointer/cause detail; custom ref client and format/default configuration | Apache-2.0; project explicitly says maintenance mode and superseded by json-sKema. Not recommended for a new 2020-12 foundation. |

Sources: [NetworkNT release README](https://github.com/networknt/json-schema-validator/blob/3.0.9/README.md),
[3.0.9 POM](https://repo.maven.apache.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9.pom),
[Maven metadata](https://repo.maven.apache.org/maven2/com/networknt/json-schema-validator/maven-metadata.xml),
[json-sKema README](https://github.com/erosb/json-sKema),
[0.25.0 POM](https://repo.maven.apache.org/maven2/com/github/erosb/json-sKema/0.25.0/json-sKema-0.25.0.pom),
[Harrel repository](https://github.com/harrel56/json-schema),
[Everit maintenance notice](https://github.com/everit-org/json-schema).
NetworkNT metadata reports 3.0.9, last updated `20261103201741`; in the initial research no performance
benchmark or library execution was performed (see executable follow-up). Maintenance observations are current
published artifacts/activity, not promises of future support.

Why 2020-12 rather than draft-07: the Java generator already declares it;
modern tuple syntax and evaluated-property semantics have coherent
standard definitions. Provider subset differences do not disappear by relabeling the
contract draft-07. Draft-07 plus a mature older validator is viable only if those
features are deliberately unwanted; it buys no demonstrated simplification here.
See the official [validation specification](https://json-schema.org/draft/2020-12/json-schema-validation)
and [core specification](https://json-schema.org/draft/2020-12/json-schema-core).

Proposed integration behavior:

* Parse YAML once to a JSON-compatible canonical tree. Select 2020-12 by default when
  `$schema` is omitted; reject a different declared draft for this initial single-draft
  profile rather than interpreting it as 2020-12. Compile/check schemas on loading,
  including meta-schema validation and rejection of unsupported constructs. Unknown standard extension
  annotations are not assertions; specifically detect obsolete dialect fields so a
  typo such as `nullable` cannot appear to work through permissive keyword handling.
* Validate actual JSON values without default insertion or coercion. Retain canonical
  schemas intact across providers and use them for final acceptance. Reuse immutable
  compiled schemas by definition generation; use independent validation execution
  contexts so limits, errors, and annotations cannot leak across invocations.
* Convert library errors into existing outcome/issue handling with instance JSON Pointer,
  schema location, keyword, and useful original detail. Group union errors for readability
  without discarding diagnostic evidence. Invalid schema, invalid instance, unresolved
  schema construct, provider incompatibility, and exhausted validation resources are distinct.
* Use bundled standard meta-schemas without external retrieval. NetworkNT's default JDK regex
  implementation is not ECMA-262; evaluate its Joni or GraalJS option against the actual
  identifier/pattern fixtures. Joni is the lighter candidate, not proof of perfect regex
  equivalence. Document differences rather than claiming arbitrary Java regex is standard
  JSON Schema. The later v1 decision leaves evaluation depth and steps unlimited;
  no numeric evaluator thresholds are to be selected during implementation. Pattern
  engine research is not a v1 requirement absent an approved feature needing it.
* Do not change public API/SPI to expose the library. Test the selected Java 21 /
  Boot / Jackson dependency graph in the later implementation task; research has not
  established startup, binary compatibility, throughput, or full draft conformance.

## Necessary transformations versus avoidable machinery

| Operation | Assessment |
| --- | --- |
| YAML syntax to canonical JSON tree, then provider request envelope | Necessary serialization, not a new schema dialect. Preserve types, numbers, descriptions, and absent keywords. |
| Java declaration to standard JSON Schema | Necessary existing declaration generation; report unsupported recursive types and preserve declared nullability. Do not pass generated schemas through the legacy reduced model. |
| Model contribution projection for child inputs and outputs | Necessary ownership operation when bindings exist. Keep unbound schemas direct. Ambiguous composition/open objects are TBD-10/13 design questions, not permission for a general rewriting engine. |
| Runtime reference/attachment adaptation | Necessary execution boundary. Separate JSON wire value validation from resource binding and resolved-type checks. Runtime resource resolution remains separate. |
| Native output field envelope | Necessary: Responses `text.format`, Anthropic `output_config.format`, Gemini generateContent JSON-schema config. Per-call isolation and continuation propagation need fixtures. |
| Removing a rejected assertion from a provider copy | Potentially justified only for specific evidenced keywords, retaining canonical local enforcement and documenting weaker generation. Example: Anthropic numeric bounds. Permitted by resolved TBD-07 only with an exact evidenced rule and ticket acceptance tests. |
| Constraints accepted but ignored | No removal needed. Send authored constraint and validate locally. Gemini's documented ignored lengths/counts are a candidate for this treatment, subject to endpoint verification. |
| All-required conversion, nullable insertion, global closure | Not acceptable automatic transformations. They change the accepted instance set. Authors/framework-owned planning declarations must make the choice explicitly. |
| JSON-only/prompt-only fallback or route switching | Not allowed. An ignored keyword is different from lack of native schema generation altogether. |

For binding projection, propose a bounded first implementation for explicit object
property paths. Reject only
the binding/schema combination that cannot be projected or proven, not the feature
everywhere. Do not treat compiled instance validation as proof of all possible bound
values. For example, removing `/invoice` can invalidate an `anyOf` discriminator,
leave it permitted through `additionalProperties`, or change `unevaluatedProperties`
evaluation. Full-result validation is necessary but cannot alone stop a model from
writing a framework-owned value. Preserve explicit ownership checks. Fully bound output
generation and required-work handling are settled under TBD-10; preserve existing behavior.

## Original bounded verification proposal

These are research fixtures/probes to close evidence gaps, not implementation tickets.
The executable follow-up below completes the isolated validator/serialization subset;
the other fixtures remain proposals.
Offline fixtures should come first; cloud probes require separate authorization.

| Fixture/probe | Bounded cases and observable evidence |
| --- | --- |
| Local validator semantics | A small table of schemas with 2–4 valid/invalid instances each: omitted/required/null; open/closed/typed maps; exact case; integer `1.0` versus string; mixed enum/null; nested arrays/tuples; bounds/length/pattern; format assertions; clear rejection of unsupported schema `default`. Assert input unchanged. |
| Composition | Overlapping `oneOf`, `allOf` with closure, conditional/dependent assertions, and evaluated properties. Test bounded evaluation failures distinctly. |
| YAML parsing | Quoted `"null"` versus YAML null, duplicate keys, precision, aliases/cycles, non-string keys. Preserve JSON-compatible text and data; no masking. |
| Actual RC2 serialization | Mock HTTP transport, no credentials: capture each integration's tool and output schema. Test type array, schema-valued openness, root constraints, boolean reserved properties; compare against canonical tree. Establish exact Gemini reject/drop behavior and Anthropic root omissions. |
| SDK versus adapter | Serialize the same raw map directly with candidate SDK field builders, then through Spring AI. This separates SDK capability from adapter loss and provides minimal upstream reproductions. No network model call needed. |
| Real framework lifecycle | Initial planning, skill output, one tool continuation, one correction; distinct schemas for concurrent skills. Verify authored canonical schema persists and request schema belongs to the correct call. History policy is still TBD-11. |
| Bindings/runtime values | Parent input to child; accepted child output to another input; bound output composition; reserved null/equal-value override; open object; nested optional ancestor; ambiguous union path rejected clearly. Attachment ref versus direct Resource and Java runtime ref resolve in existing boundaries. |
| Metadata/Console/TypeSafe | Standard derived TypeSafe choice/score maps; input substitutions; original YAML plus effective schema display; output diagnostics and MCP/REST fixture parity. Confirm no independent chat adapter is introduced. |
| Later live smoke | Per explicitly selected model/route: one closed simple schema; one schema with tools and final output; one planning schema; one documented ignored or weakened constraint paired with deliberately invalid local fixture. Historical initial proposal, superseded by the accepted TBD-14 bounded matrix below; its request/token/cost caps govern. Wall-clock settings are agreed below. |

For live results record endpoint protocol, model snapshot, OpenRouter upstream route,
Ollama server/model digest, Spring AI/SDK versions, exact sent schema, status/refusal,
raw result, local validation, request count, and usage. Do not prompt a model to violate
a constraint and infer universal enforcement from one conforming answer. A deterministic
invalid-result fixture proves local rejection; a bounded live call establishes only
observed compatibility. Do not run or install a different Ollama model to fill a matrix
cell silently. A missing credential or rejected schema remains a gap, not a skipped
test reported as success.

## Decision status and continuation

TBD-01 through TBD-13 are resolved as policy; implementation and acceptance are not
complete. The roadmap's numbered decisions and ticket requirements are authoritative.
Attachment input design under TBD-13 is also settled, including annotation placement,
direct Resource validation and null semantics. Native generated attachments are deferred.
TBD-14 is resolved as policy below, including testing ownership, protected suite,
host coverage, bounded cases, models/routes/effort, USD 5 ceiling and timeouts.
Implementation and acceptance gates remain open; no further policy choice is identified.
See the roadmap's [continuation handoff](spring-ai-responses-migration.md#continuation-handoff--november-11-2026).
No implementation tickets have been created in this conversation.

Downloaded public sources/POMs are inspection-only under `target/research/tbd-06`
(existing OpenAI RC2 sources remain under `target/research/spring-ai-2.1.0-RC2`).
The initial report and concise TBD-06 roadmap addition were the only original
document edits; verification subsequently adds the linked standalone probe and evidence.
This research neither accepts recommendations nor completes migration
or native structured-output acceptance.

## Executable verification follow-up — November 10, 2026

**At the time of verification no recommendation had been accepted.** The user has
since accepted the draft/library foundation and resolved the v1 policies recorded above. The isolated
[probe and handoff](../../../docs/maintainers/probes/tbd-06/README.md) now supply
reproducible code, commands, dependency trees, test reports, exception evidence,
and captured loopback HTTP requests. This updates the earlier source-only evidence;
it does not implement the migration or establish hosted-provider enforcement.

**Verified local behavior:** NetworkNT 3.0.9 resolves, compiles and passes the
representative validator fixtures on Java 21.0.2 with both Boot 4.2.0-RC2 / Jackson
3.1.8 and Boot 4.1.0 / Jackson 3.1.4. Both complete probe runs report **20 tests,
zero failures/errors/skips**, including assertions reproducing adapter defects.
Spring AI is 2.1.0-RC2; SDKs are OpenAI 4.80.0, Anthropic 3.4.0 and Google GenAI
1.73.0. Full resolved trees, including SDK Jackson 2 dependencies, are retained.
These are independent BOM-managed probes, not the framework's effective tree or
Boot startup acceptance.

Validator fixtures cover 2020-12/meta-schema checks, required/omitted/null,
standard object openness and typed maps, exact property names, precise numbers and
integer `1.0`, mixed enums, nested arrays, composition, formats, unchanged values,
diagnostics and bounded evaluation failures. Precision tests use Jackson trees
parsed with `USE_BIG_DECIMAL_FOR_FLOATS`. Retained probe snapshots include broader
historical tests; they do not define the narrowed implementation scope.

**Configuration surprises and remaining validator gaps:** meta-schema checking is
separate from compilation. Use bundled standard meta-schemas without enabling
external retrieval. JDK and optional Joni 2.2.6 pass the
representative identifier/search/Unicode fixtures; Java `\Q...\E` works only in
the JDK engine. Neither full ECMA-262 equivalence nor a regex wall-clock bound is
established. Execution limits classify exhaustion distinctly and default to unlimited;
the probe thresholds are fixtures, not proposed product restrictions.

| Actual serialization boundary | Verified outcome |
| --- | --- |
| OpenAI Responses | Complete authored schema survives `text.format.schema` and function parameters. ChatClient retains effort/token defaults; a direct call with fresh typed options loses them. |
| Anthropic native outputConfig | Intact raw schema survives. Copy existing OutputConfig and replace format to preserve effort; a fresh per-call OutputConfig replaces default effort even through ChatClient. |
| Anthropic tools | Root additionalProperties and other root constraints disappear; nested property content survives. Direct SDK raw input schema preserves all tested fields. |
| Gemini output | Type arrays fail before HTTP; typed additionalProperties silently disappear. minLength/pattern/minimum/minProperties survive; multipleOf disappears. |
| Gemini tools | Nullable union becomes STRING + nullable; string/integer union fails before HTTP. Typed maps, multipleOf and minProperties disappear; minLength/pattern/minimum survive. |
| Gemini direct SDK | All four raw fixtures survive responseJsonSchema and parametersJsonSchema, locating the losses above the SDK raw builders. |

**Existing paths and plausible fixes:** use the existing OpenAI Responses/ChatClient
path and Anthropic native OutputConfig with deliberate nested-option preservation.
Anthropic tool mapping needs all root entries copied into SDK InputSchema. Gemini
needs raw-map serialization for both schema fields, bypassing the narrowing OpenAPI
and typed-Schema conversion on that route. These are upstream-fix candidates, not
implemented adapter repairs. Google SDK client-level `HttpOptions.extraBody` does
carry a fixed output schema through Spring AI, but is not a per-request Spring
schema option and cannot repair pre-HTTP tool conversion failure. No complete
options-only path for both changing Gemini schema contexts was established. HTTP
transport rewriting is not claimed as a ready schema integration. Gemini ChatClient
combination retains thinkingBudget, output-token and temperature defaults.

**Handoff:** NetworkNT remains a viable candidate, not an adopted dependency or a
complete draft-conformance result. Confirmed adapter failures do not justify global
schema bans or silent rewriting. Format policy is now resolved as shared assertions
without conversion. Remaining original-chat choices include
regex/limits, upstream fixes/version strategy, and the related
provider, binding, history and acceptance TBDs. YAML ingestion, full framework
startup/lifecycle/concurrency, later upstream versions and live provider enforcement
remain unverified. No production dependencies/code, tickets or acceptance statuses
were changed; pre-existing working-tree work was preserved.

## Spring AI implementation blockers and upstream contribution handoff

Recorded November 10, 2026 following user review. These are implementation/acceptance
blockers for the affected schema paths. They do not block independent migration work
or justify globally banning schema features. Upstream status below was checked via
GitHub and must be refreshed before contributing or choosing a release. No issues,
comments, or PRs have been submitted by this work.

Reproduction baseline: Spring AI 2.1.0-RC2, OpenAI SDK 4.80.0, Anthropic SDK 3.4.0,
Google GenAI SDK 1.73.0. Run the [standalone probe](../../../docs/maintainers/probes/tbd-06/README.md)
with either tested Boot BOM. Source is under that project's `src/test/java`; retained
requests/exceptions are under `evidence/boot-4.2.0-RC2` and `evidence/boot-4.1.0`.
Passing defect-reproduction tests demonstrate the defect, not adapter correctness.
When fixing upstream, replace the defect expectation with schema-fidelity assertions.

| ID / affected boundary | Reproduction and evidence | Upstream status / candidate action | Gate to clear |
| --- | --- | --- | --- |
| SAI-02 Anthropic output configuration replaces default effort | `AdapterTest.anthropicConvenienceAndNativeAndOptions` and `CustomizationTest.anthropicMutatingDefaultsPreservesEffortAndNativeSchema`; `anthropic-preserve-default-config.json`. Fresh per-request OutputConfig loses default effort even through ChatClient. | No exact matching upstream issue found in the search; this is confirmed replacement behavior, not yet an upstream-classified bug. Copy existing OutputConfig and replace only format. Consider documenting or improving nested merge semantics upstream. | Schema, reasoning effort, and token defaults survive initial and per-call option composition. A supported local construction path exists; waiting for an upstream change is not required. |
| SAI-03 Anthropic tool root-schema loss | `AdapterTest.anthropicConvenienceAndNativeAndOptions`; `anthropic-native.json`, direct control `anthropic-sdk.json`. Root openness and other constraints disappear. | No exact matching issue found; the ordinary root-constraint loss is independently demonstrated. Candidate fix: populate SDK InputSchema with all authored root entries rather than only properties/required. No options-only bypass established. | Actual tool request preserves the canonical schema. Native strict tools are deferred beyond v1 under TBD-08; schema fidelity and local validation remain required. |
| SAI-04 Gemini tool schema conversion | `AdapterTest.geminiAdapterMatrix`, `AdditionalAdapterTest.geminiNonNullableUnion`; `gemini-tool-mixed-union-exception.txt`, `gemini-tool-map.json`, `gemini-tool-constraints.json`. Mixed unions fail before HTTP; other constraints disappear. | No exact matching upstream fix established for these remaining inline-schema failures. Candidate action: use raw parametersJsonSchema for ordinary schemas rather than narrow conversion. | Fidelity for approved v1 fixtures, including nullable types and retained constraints. Typed-map input fidelity is now a v1 implementation gate; general mixed unions remain historical defect evidence outside v1. |
| SAI-05 Gemini output schema conversion | `AdapterTest.geminiAdapterMatrix` and `geminiDirectSdkRawSchemas`; `gemini-output-union-exception.txt`, `gemini-output-map.json`, `gemini-output-constraints.json`, `gemini-sdk-*.json`. Type arrays fail; maps/multipleOf are dropped. | No exact matching issue found for this complete output path. Candidate fix: send raw maps through GenerateContentConfig.responseJsonSchema instead of narrowing to SDK Schema. | Different per-call v1 skill/planning schemas survive intact with option defaults preserved, subject only to approved TBD-07 mappings. Nullable type arrays remain relevant; typed maps/multipleOf are outside v1 and not standalone gates. Fixed client extraBody is not a per-request solution. |

The raw SDK controls preserve the tested fields, locating these losses in Spring AI
rather than proving provider acceptance. Provider subset restrictions remain separate.
Prefer a focused upstream fix or suitable fixed version, and rerun the same fidelity
fixtures against the proposed fix. A merged PR alone is not proof it ships in the
selected dependency. Record the tested upstream commit/release and exact fix coverage.
Do not introduce a second model adapter, agent loop, transport-body rewrite, or schema
weakening as an incidental workaround. Bring any required architecture departure back
for discussion. The user plans to explore Spring AI contributions; retain these
minimal reproductions and distinguish exact matches from merely related upstream work.

## Validation resource-limit evidence and v1 decision

**V1 resource-limit decision — agreed:** leave NetworkNT evaluation depth and
step limits unlimited (`maxEvaluationDepth = 0`, `maxEvaluationSteps = 0`) for each
validation. Retain existing parser, payload, and execution limits. Add no new public
configuration knobs, independent error-count limits, or validation timeouts under
this decision. Preserve the probes for future workload-driven review. Unlimited
evaluation is not a CPU/memory guarantee, and existing limits do not cover all
validation work. Failure to complete validation is an operational failure, never a
successful or partially accepted validity result.

The following records the investigation before that decision.

Investigated November 10, 2026 (America/Los_Angeles). **At investigation time, no product
thresholds, new configuration, timeouts, schema restrictions, or production behavior
were selected or implemented.** The later v1 decision is recorded above. Earlier broad recommendations to configure evaluation limits are not
an accepted numeric policy or proof that limits are necessary for every v1 workload.
This investigation uses the agreed simple v1 scope; it does not reopen references,
composition, typed maps, patterns, or defaults. The isolated POM's new YAML test
dependency is only for reading existing repository fixtures.

### Existing limits and coverage

This inventory describes the current production implementation, which still uses
legacy input/output validators, separately from the proposed NetworkNT path.
Repository source is authoritative for explicit limits; parser defaults are library
behavior, not Loomspan settings or guarantees for every ingress route.

| Boundary and source | Current limit / ownership | Applies to / uncovered work |
| --- | --- | --- |
| [YamlValues](../../../src/main/java/ai/loomspan/internal/serialization/YamlValues.java) | Explicit `Integer.MAX_VALUE` code points and collection aliases; 1,000,000 expanded values after decoding; recursive-container rejection. The expanded count includes containers, map keys and values, and repeated logical alias expansions. | Both configured file skills and API-supplied `SkillDocument` YAML. This is not a byte/string-length cap: one scalar counts once. Parsing/allocation precedes the expanded-value walk. No numeric YAML nesting guard is configured. |
| SnakeYAML Engine **3.0.1**, `LoadSettingsBuilder` and `Composer` | Library defaults: 3 × 1,024 × 1,024 code points and 50 collection aliases. Loomspan explicitly overrides both above; the inspected engine has no configurable numeric nesting limit. | Do not claim the usual 3 MiB/50-alias defaults protect skill loading. The later recursive expansion check is also not a parser nesting limit. |
| [YamlSkillCatalog](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java), `loadDefinition`, `checkSuppliedDocuments`, `readManifest` | File loading calls `readAllBytes`; supplied YAML is already a String and is copied to UTF-8 bytes. Both go through `YamlValues.read`, then `yamlObjectMapper.valueToTree`. No explicit document-byte, document-count, property-count or schema-depth rejection found. | File/API skill paths share YAML controls, but allocation occurs before them. Depth 4 / property count 12 / required count 8 are **output authoring warnings**, not rejection thresholds. |
| [LoomspanJacksonCodecs](../../../src/main/java/ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java) | No explicit `StreamReadConstraints` overrides. Jackson **3.1.4** (Boot 4.1.0) and **3.1.8** (probe Boot RC2) default to nesting 500, number length 1,000, string length 100,000,000, property-name length 50,000; document length and token count are unlimited (`-1`). String limits are approximate buffer checks, not exact total-payload bounds. | JSON text parsed by schema/planning codecs, including model output and JSON schema strings, has parser constraints. These are not a universal bound on prebuilt trees, maps or token-buffer conversion. The probe demonstrates raw JSON nesting 501 rejected but SnakeYAML → `YAMLMapper.valueToTree` at 501 succeeding. It mirrors ingress settings; it is not a full catalog registration test. |
| [LoomspanMethodInputSchemaGenerator](../../../src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java) | Builds `ObjectNode` trees directly. A visiting-class set breaks recursive object expansion; no explicit property-count, total-size, finite-depth or evaluation budget. | Java-generated schemas bypass raw YAML/JSON parsing while being constructed. Later serialization/parsing may encounter library constraints, but cannot bound generation that already happened. No new Java schema generation or API behavior is exercised by this probe. |
| [SkillInputValidator](../../../src/main/java/ai/loomspan/internal/runtime/input/SkillInputValidator.java) | Recurses through Java maps/lists; accumulates issues. No independent traversal depth, step or error-count budget. | API invocation inputs, tool/child inputs and local input checks are not universally protected by text-parser constraints. Upstream parsing applies only where text actually enters through that parser. |
| [OutputSchemaValidator](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaValidator.java) | Parses the complete response String with the schema-tree mapper, then recursively validates and collects issues. No explicit raw response-byte, evaluation-step or validation-error-count cap. | Model output is already materialized before local parsing/validation. Provider token/context limits are route/model dependent, not a shared local JSON payload limit. The existing Anthropic thinking option raises `maxTokens` to at least twice the thinking budget; it is not a cross-provider byte bound. |
| [OutputValidationFeedback](../../../src/main/java/ai/loomspan/internal/outputvalidation/OutputValidationFeedback.java), [OutputSchemaValidationIssue](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaValidationIssue.java), [OutputSchemaCallAdvisor](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java) | Four issues in feedback/outcomes; 2,048 correction code points; per-issue reason 240, fragment 120, fact/path 256, message 512 code points. YAML output corrections default to 2, permitted 0–3; linter retries 0–3. | These bound selected diagnostic representation and repeat attempts **after** validation, not the initial error list, parsing or validator traversal. They do not establish NetworkNT diagnostic behavior. |
| [LoomspanProperties.Session](../../../src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java), [DefaultSessionUsageService](../../../src/main/java/ai/loomspan/internal/runtime/usage/DefaultSessionUsageService.java) | Explicit configurable defaults: invocation stack depth 32, mission timeout 60 s; quotas: 64 skills, 128 tools, 32 linter retries, 64 model calls, 192 provider attempts, 200,000 usage units. Zero disables a quota. Model-call/usage accounting is updated on response. | Runtime invocation limits, not JSON nesting/schema frames/fields. No per-validation CPU/memory guarantee, especially before session execution during schema registration. A mission wait/cancellation is not a bound on all synchronous parser/validator work. |
| [StepLoopMissionExecutionEngine](../../../src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java) and connection/retry properties | Default 10 execution steps, skill `max_steps` override; connection `request-timeout` optional; provider retry default 3 attempts (configuration permits 1–10). | Count execution/planning/provider activity, not JSON Schema evaluation steps; network request duration does not bound local validation. |
| [DefaultMissionInputMaterializer](../../../src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java) | Configurable attachment maximum, default 20 MiB, checked against known size and streamed bytes. | Attachment materialization only, not an inline JSON/string/schema maximum. |
| Observability and Console | Framework [BoundedJsonPageWriter](../../../src/main/java/ai/loomspan/internal/observability/web/BoundedJsonPageWriter.java): page size ≤5,000, response ≤16 MiB. Console [observability service](../../../loomspan-console/internal/observability/service.go): collection 16 MiB, skill/active detail 4 MiB, trace/instance detail 1 MiB. | Limits on reading/representing execution information; they do not constrain skill registration, invocation inputs or provider results before validation. Trace-storage, SSE and Console agent-skill packaging limits likewise are not schema-ingress guards. |

No common finite total schema/input/output byte cap was found across these paths.
An application's own transport limits or customized conversion mapper may add
constraints, but are not a framework-wide guarantee. The YAML logical-value cap
does not count string characters or validator dispatches, and does not cover
Java-generated schemas or directly supplied invocation maps.

### NetworkNT 3.0.9 control semantics

Source inspected: the published
[3.0.9 sources JAR](https://repo.maven.apache.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9-sources.jar),
particularly `ExecutionConfig`, `ExecutionContext`, `Schema`,
`ValidationLimitExceededException`, `keyword/FormatValidator`, `keyword/RequiredValidator`,
and `format/{Formats,PatternFormat,EmailFormat,DateTimeFormat,UriFormat}`.
The exact binary/source hashes are retained in
[networknt-sha256.txt](../../../docs/maintainers/probes/tbd-06/evidence/resource-limits/networknt-sha256.txt).
The tagged [ExecutionConfig documentation](https://github.com/networknt/json-schema-validator/blob/3.0.9/src/main/java/com/networknt/schema/ExecutionConfig.java)
agrees with the inspected artifact. Parser defaults were checked against the published
[Jackson 3.1.4 sources](https://repo.maven.apache.org/maven2/tools/jackson/core/jackson-core/3.1.4/jackson-core-3.1.4-sources.jar)
and [SnakeYAML 3.0.1 sources](https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/3.0.1/snakeyaml-engine-3.0.1-sources.jar),
plus executable checks on both probe platforms.

- **Depth** is the number of simultaneously active `Schema.validate`/walk frames,
  including the root. A keyword dispatch does not itself add a frame; entering a
  child schema does. It is neither Jackson nesting depth nor Java call-stack depth.
  In this v1 corpus a fully evaluated chain of 128 containers plus a scalar needs
  129 frames. The same deep instance below an open object's extra property needs
  only one frame because no child schema traverses it. Missing nested values can
  also reduce depth (invoice valid 5 versus invalid 4).
- **Steps** admit one schema entry and one dispatch per keyword validator actually
  executed. `Schema.validate` calls `admitKeyword` immediately before dispatch.
  Internal loops, characters, enum comparisons, required entries and emitted errors
  do not individually consume steps. Annotation dispatches can count too: the
  repository output's `description` contributes work even with annotation collection
  off. `type` + `format` at a scalar root takes three steps, regardless of string
  length. A flat record with N present constrained integers takes `5 + 4N` here;
  the same schema with all required properties absent takes five steps. These are
  fixture-specific counts, not general fields-to-steps formulas.
- **Lifetime/reset:** convenience `schema.validate(node, customizer)` creates a fresh
  execution context. Repeated calls with the same cached schema each get their full
  budget, including a call after an earlier exhaustion. Successful outermost exit
  clears accounting state; nested calls share it. A context that exhausts is terminal:
  reusing that explicit context rethrows. `getErrors()` also rethrows after exhaustion,
  preventing consumers from treating partial errors as a completed verdict. Tests
  verify repeated exact-budget success, recovery with a fresh context, and explicit
  exhausted-context rejection. This is not a concurrency stress test.
- **Defaults/boundary:** both limits default to zero (unlimited); negatives are rejected.
  A budget of N permits N admissions; the next admission fails. Depth is checked
  before admitting the new schema frame. Settings are snapshotted on first entry;
  changing configuration mid-execution is not a way to replenish accounting.
- **Exhaustion:** `ValidationLimitExceededException`, not an ordinary schema error or
  an invalid verdict. It exposes kind, configured capacity, admitted steps, active
  depth and schema/instance/evaluation locations. Unwinding preserves the abort.
  Exact-budget tests record a scalar URI denied at step 3 with two admitted steps
  and one active frame. No production exception/retry policy was chosen here.
- **Loading/compilation:** `getSchema` and `initializeValidators` have no evaluation
  budget. Lazy `getValidators()` can also run inside an admitted schema entry before
  keyword admission; construction work is not metered individually. The probe fully
  initializes a 5,000-property schema, then proves a one-step evaluation fails. Limits
  therefore do not bound schema parsing, construction, caching or initialization.
  Meta-schema validation, if separately invoked, is another validation execution;
  it is not an implicit resource-bounded compilation step. No reference/remote-loading
  requirement is added to v1 by this observation.
- **Formats:** the format keyword invokes the selected standard validator after one
  dispatch admission. There is no character-level accounting or preemption inside
  that call. Implementations include JDK regex-backed formats, email checks and date/
  URI parsers; the resolved dependency tree includes `com.ethlo.time:itu:1.17.0` for
  date-time handling. The new probe leaves the existing JDK regex factory unchanged.
  Limits can bound the number of format-bearing child evaluations, but not the work
  of one admitted format assertion. This is relevant even without authored patterns.

### Reproduction and measurement method

From the repository root, with Java 21 in `JAVA_HOME` and Maven on PATH:

```powershell
./docs/maintainers/probes/tbd-06/run-resource-limits.ps1
./docs/maintainers/probes/tbd-06/run-resource-limits.ps1 -BootVersion 4.1.0
```

Equivalent command (change `boot.version` for the second platform):

```text
mvn -f docs/maintainers/probes/tbd-06/pom.xml test dependency:tree -Dboot.version=4.2.0-RC2 -Dtest=ResourceLimitsTest,ValidatorTest,ValidatorBoundaryTest "-DargLine=-Xmx512m -Xss2m" -DoutputFile=target/resource-dependency-tree.txt
```

Sources can be retrieved without modifying the framework:

```text
mvn -f docs/maintainers/probes/tbd-06/pom.xml dependency:get -Dartifact=com.networknt:json-schema-validator:3.0.9:jar:sources -Dtransitive=false
mvn -f docs/maintainers/probes/tbd-06/pom.xml dependency:get -Dartifact=tools.jackson.core:jackson-core:3.1.4:jar:sources -Dtransitive=false
mvn -f docs/maintainers/probes/tbd-06/pom.xml dependency:get -Dartifact=org.snakeyaml:snakeyaml-engine:3.0.1:jar:sources -Dtransitive=false
```

Executable definitions are in
[ResourceLimitsTest.java](../../../docs/maintainers/probes/tbd-06/src/test/java/ResourceLimitsTest.java).
Retained snapshots:
[Boot 4.2.0-RC2](../../../docs/maintainers/probes/tbd-06/evidence/resource-limits/boot-4.2.0-RC2/)
and [Boot 4.1.0](../../../docs/maintainers/probes/tbd-06/evidence/resource-limits/boot-4.1.0/).
Each has the workload CSV, environment, accounting and parser/exhaustion evidence,
exact resolved dependency tree, and three Surefire reports. Generated files under
`target` are replaced on subsequent runs; retained snapshots are separate.

Environment: Windows 11 amd64, Intel Core i7-10875H at nominal 2.30 GHz, 16 logical
processors, 34,064,392,192 bytes physical RAM; JetBrains OpenJDK 21.0.2+13-b375.1;
Maven 3.9.6. Each test JVM uses `-Xmx512m -Xss2m`; no machine-exhaustion progression.
NetworkNT 3.0.9 and Spring AI 2.1.0-RC2 remain fixed. Boot 4.2.0-RC2 resolves Jackson
3.1.8; Boot 4.1.0 resolves 3.1.4. YAML uses SnakeYAML Engine 3.0.1 on both.
**Four new tests plus seven existing validator regression tests pass on each platform.**
The 90 workload rows have identical minimum budgets, error counts, diagnostic byte
counts, and candidate outcomes across platforms. Older regression tests include
historical broader-schema fixtures; those are not part of the new v1 workload table.

For every workload, the schema and instance pass a default JSON text round-trip
before measurement. Validation asserts formats, collects all errors (`failFast`
default false), uses default output and no optional annotation collection. Minimum
**positive completion budgets** are found by binary search independently: depth
with steps at 1,000,000; steps with depth at 512. A completed invalid verdict counts
as completion. The bound immediately below each minimum is tested to exhaust.
These search ceilings are harness settings, not selected product thresholds.

Timing reuses the schema and prebuilt instance, after the searches and 12 additional
warm-up validations, then records nine individual calls with `System.nanoTime` at
depth 512 / steps 1,000,000. The median is the fifth sample; CSV `p95_us` is the
nearest-rank ninth sample, therefore the observed maximum, not a reliable tail-latency
estimate. `load_first_us` includes `getSchema` plus the first validation (including
lazy initialization), excludes text parsing and is a single observation, not a cold-JVM
benchmark. Error creation is in validation timing; `errors.toString()` UTF-8 byte
measurement is outside it. Zero errors render as `[]` (two bytes).

This is an exploratory local microprobe, not JMH, a concurrency benchmark, an SLO,
allocation/peak-memory measurement, or a worst-case complexity proof. Fixed workload
order, prior tests, JIT, GC, frequency scaling and other machine activity affect
timings. There are no repetitions across fresh processes for statistical comparison,
and no measured production/correction/provider latency. Do not interpret differences
between the two platform timings as a Jackson performance ranking. No live calls run.

### V1 workload results

Repository schemas are read directly from `src/test/resources/skills/valid/`:
`input-schema-skill.yaml`, `output-schema-skill.yaml`, and
`output-schema-complex-skill.yaml`. Their selected schema subtrees already use the
v1 keywords tested; no dialect conversion or automatic schema strengthening occurs.
The output payload `{"vendorName":"Acme","totalAmount":42.5}` is copied exactly
from `OutputSchemaCallAdvisorTest.returnsPassingResponseWithoutRetryWhenJsonMatchesSchema`.
The input payload and generated invoices are explicit synthetic companions to the
repository schemas, not captured user/model traffic. The complex invoice has nested
`lineItems[].details.taxable`; invalid lines have a wrong SKU type, wrong amount type
and missing required taxable flag. Its date fields are ordinary strings as authored;
format-specific fixtures below are separate.

| Workload family | Bounded progression and characteristics |
| --- | --- |
| Repository input/output | Valid and invalid named records, required/unknown fields, descriptions |
| Invoice line items | 1, 10, 100, 1,000; valid and three errors per invalid line |
| Flat records | 10, 100, 1,000, 5,000 named integer properties with minimum/maximum; valid, all below minimum, or all required fields absent |
| Arrays of records | 10, 100, 1,000, 5,000; closed named objects, integer minimum, scalar enum, nullable string with description/maxLength, minItems/maxItems; three errors per invalid record |
| Alternating object/array nesting | 1, 4, 16, 32, 64, 128 containers with a valid/invalid scalar leaf; open-object control skips the same deep extra value |
| Standard formats | URI path and date-time fractional seconds with 32, 256, 1,024, 4,096, 16,384 repeated characters, valid and invalid; email, hostname, UUID, date, IPv4 with invalid strings at those same sizes |

The date-time lengths are stress fixtures with long fractional seconds, not a claim
that such values are typical invoices. Invalid URI appends a space plus text; invalid
date-time replaces terminal `Z` with `X`. The other five formats use `!` repetitions
and may reject early; they are not worst-case format inputs. Other supported standard
formats, alternative regex engines and pathological regex-shaped strings were not
benchmarked. This selects test coverage, not a product supported-format list.

Selected rows from the RC2 snapshot follow; the CSVs retain **all 90 rows**, both
valid and invalid variants, all progression sizes, input/schema bytes and first-use
times. Time columns are microseconds. `D/S` is minimum completion depth/steps.

| Workload | Input bytes | D/S | Errors | Diagnostic bytes | Median µs | Observed max µs |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| repo-input-valid | 41 | 2/9 | 0 | 2 | 13.700 | 42.000 |
| repo-output-valid | 40 | 2/9 | 0 | 2 | 4.500 | 24.400 |
| invoice-valid-10 | 717 | 5/163 | 0 | 2 | 28.300 | 45.400 |
| invoice-valid-100 | 5667 | 5/1423 | 0 | 2 | 269.300 | 430.000 |
| invoice-valid-1000 | 55167 | 5/14023 | 0 | 2 | 2007.500 | 4095.300 |
| invoice-invalid-1000 | 42167 | 4/12023 | 3000 | 168670 | 2462.100 | 3351.500 |
| flat-valid-100 | 791 | 2/405 | 0 | 2 | 208.700 | 973.700 |
| records-valid-100 | 3701 | 3/1505 | 0 | 2 | 424.400 | 642.900 |
| flat-valid-1000 | 8891 | 2/4005 | 0 | 2 | 1124.300 | 1562.900 |
| records-valid-1000 | 37001 | 3/15005 | 0 | 2 | 1394.700 | 1923.000 |
| flat-valid-5000 | 48891 | 2/20005 | 0 | 2 | 1821.200 | 2259.000 |
| flat-invalid-5000 | 53891 | 2/20005 | 5000 | 198890 | 2744.000 | 4921.900 |
| missing-5000 | 2 | 1/5 | 5000 | 193890 | 944.800 | 1237.700 |
| records-valid-5000 | 185001 | 3/75005 | 0 | 2 | 7159.300 | 9499.400 |
| records-invalid-5000 | 190001 | 3/75005 | 15000 | 826670 | 5591.800 | 7244.800 |
| nested-valid-16 | 97 | 17/66 | 0 | 2 | 4.500 | 5.900 |
| nested-valid-32 | 193 | 33/130 | 0 | 2 | 7.900 | 8.500 |
| nested-valid-64 | 385 | 65/258 | 0 | 2 | 16.600 | 26.500 |
| nested-valid-128 | 769 | 129/514 | 0 | 2 | 30.200 | 31.000 |
| open-nested-128 | 779 | 1/3 | 0 | 2 | 2.300 | 2.600 |
| uri-valid-32 | 55 | 1/3 | 0 | 2 | 15.700 | 66.000 |
| date-time-valid-32 | 55 | 1/3 | 0 | 2 | 44.800 | 62.900 |
| uri-valid-16384 | 16407 | 1/3 | 0 | 2 | 156.700 | 259.000 |
| date-time-valid-16384 | 16407 | 1/3 | 0 | 2 | 180.700 | 193.100 |
| email-invalid-16384 | 16386 | 1/3 | 1 | 69 | 226.800 | 249.100 |

The 5,000-record invalid array generates 15,000 diagnostics (826,670 rendered UTF-8
bytes) from a 190,001-byte input and a 327-byte schema. With a 1,000-step budget,
evaluation stops at `/66/id`, with 198 errors accumulated (10,530 rendered bytes)
before the abort. For this measurement only, the probe captures the error-list
reference **before** evaluation; calling `getErrors()` after exhaustion is verified
to throw. Partial errors are not a completed validity result or a proposed production
diagnostic route. Conversely, 5,000 missing fields generate 193,890 diagnostic bytes
from `{}` in five steps: budget accounting is not a diagnostic-volume cap.

### Candidate outcomes and tradeoffs — no selected policy

The following **illustrative** pairs are comparison points only: A = depth 8 /
1,000 steps; B = depth 32 / 10,000 steps; C = depth 128 / 100,000 steps. They are not
recommended defaults or tiers. `Complete` permits a validity verdict: valid inputs
are accepted and invalid inputs are rejected normally. `Steps`/`Depth` means
operational exhaustion, so no validity verdict was reached. Exact results for each
row and both validity variants are in the CSV.

| Workload | A | B | C |
| --- | --- | --- | --- |
| Repository input/output, invoices 1/10, flat records 10/100, record arrays 10 | Complete | Complete | Complete |
| Invoices 100, record arrays 100, flat records 1,000 | Steps | Complete | Complete |
| Invoices 1,000, record arrays 1,000/5,000, flat records 5,000 | Steps | Steps | Complete |
| All required fields missing, 10–5,000 | Complete (invalid) | Complete (invalid) | Complete (invalid) |
| Nested 1/4 containers | Complete | Complete | Complete |
| Nested 16 containers | Depth | Complete | Complete |
| Nested 32/64 containers | Depth | Depth | Complete |
| Nested 128 containers | Depth | Depth | Depth |
| Open object's deep extra value; all tested formats/lengths | Complete | Complete | Complete |

**Demonstrated benefit:** independent step limits bound admitted schema/keyword
traversal and can reduce subsequent per-item checks and accumulated errors for wide
arrays. Independent depth limits stop nested schema evaluation earlier than the
JSON parser's 500-container ceiling for these evaluated property trees. All workload
instances/schemas fit the tested JSON parser defaults; neither scalar length nor
nesting controls prevent wide traversal. Thus parser limits are not equivalent to
validator limits, and existing controls leave distinct paths/work uncovered.

**Capability cost:** the same budgets stop ordinary valid workloads, not only invalid
ones. A excludes the 100-line invoice; B excludes the 1,000-line invoice and records;
C excludes a 128-container chain whose instance is only 769 bytes and needs 129
evaluation frames. The tested workloads completed promptly with the harness ceilings;
this experiment does not demonstrate that production must reject any of them or
that a particular threshold produces a useful latency/memory guarantee.

**Sufficiency assessment:** existing parser/payload controls cannot establish a
shared validation-resource bound for simple v1, particularly for API maps/generated
trees and total width. Independent evaluator controls provide the traversal benefit
above, even without deferred schema features. Whether that benefit warrants a product
limit, and what legitimate workload sizes should fit, remains a product decision.
There is no observed v1 explosion here that independently selects a policy.

**Gaps evaluator limits do not address:** raw text/tree allocation, YAML expansion
before its post-parse check, schema construction/compilation/cache size, total
concurrent validations, individual keyword loops and scalar work, and diagnostic
rendering/storage. Long formats retain depth 1 / steps 3 while their work can grow;
requiredness can emit many errors within one dispatch. Enum comparisons, string
length checks and open-object scans similarly are internal keyword work rather than
per-field accounting. A schema's `maxLength`/`maxItems` expresses a validity constraint,
not a general pre-parser allocation cap; with all-error collection an earlier bounds
error does not promise later keyword work will be skipped. No timeout, custom
validator, new public knob or extra schema restriction is introduced to close these
gaps. The subsequent user decision leaves evaluation limits unlimited for v1, as recorded
above. These gaps are documented limitations, not newly authorized implementation work.



## Accepted TBD-07 implementation policy

The user accepted narrow driver mappings, as specified in the roadmap's
[TBD-07 requirements and mandatory ticket handoff](spring-ai-responses-migration.md#tbd-07-selecting-provider-differences-without-a-capability-registry).
Those requirements supersede broader mapping proposals in this research. Preserve
canonical schemas; send supported/ignored keywords unchanged; omit only evidenced
rejected simple-bound assertions or exact standard formats from request copies.
No structural transformations, route/model detection, registry, automatic fallback,
or error-driven keyword stripping are authorized. Adapter losses remain defects,
not provider rejection evidence. Future tickets must include exact mapping entries,
evidence, implementation boundaries, and acceptance cases; the implementing LLM
must not invent policy. Strict tools and failure timing are separately resolved under TBD-08/09.

## Accepted TBD-08 implementation policy

Native strict tool enforcement is deferred beyond v1. The roadmap's
[TBD-08 requirements and mandatory ticket handoff](spring-ai-responses-migration.md#tbd-08-native-strict-tool-argument-enforcement)
are authoritative: send tool schemas, keep strictness disabled, validate canonical
arguments before tool execution, retain authorization and existing binding/resource
semantics, and preserve the existing Spring AI tool loop. Add no strict-mode option,
automatic enablement, new loop, or retry mechanism. Future tickets must include the
exact driver settings and acceptance tests, especially proof that invalid arguments
cannot execute side effects. Native final-output/planning formatting remains in scope.
Earlier proposals to investigate or enable strict tools during v1 are superseded.

## Accepted TBD-09 failure policy

The roadmap's [TBD-09 failure policy and mandatory ticket handoff](spring-ai-responses-migration.md#tbd-09-unsupported-schemasmodels-and-failure-timing)
are authoritative. Reject invalid declarations locally at loading/registration;
reject known driver incompatibility before the affected request; surface explicit
provider schema/support rejection without unchanged-request retries or fallback.
Canonical-invalid output uses bounded correction; transient transport failures retain
the existing retry policy; incomplete validation is an operational failure. No live
registration probes, capability registry, rollback promise, or new public error API
is authorized. Tickets must carry the complete failure table, diagnostic requirements,
retry ownership and acceptance cases. Untested routes may be attempted without a
native-support guarantee. TBD-11/12 retain their separate history/refusal decisions.

## Accepted TBD-10 binding migration policy

Preserve existing binding/projection/composition behavior while migrating to standard
JSON Schema and shared validation. The roadmap's [TBD-10 implementation requirements
and mandatory ticket handoff](spring-ai-responses-migration.md#tbd-10-output-binding-projection-and-fully-bound-outputs)
are authoritative. Exact property-name matching intentionally replaces legacy
case-insensitive behavior. Retain object-only paths, whole-value transfers, ownership
rejection, source snapshots, required-work checks, full-result validation, and skipping
final synthesis when no model contribution remains. Do not add a new binding system,
array-element/wildcard paths, coercion, or a general schema-transformation engine.
Tickets must carry the concrete preserved behaviors and acceptance cases rather than
leaving policy to the implementing LLM. Earlier binding proposals are superseded by
this preservation decision; correction history follows the separately resolved TBD-11 requirements.

## Accepted TBD-11 targeted correction changes

The roadmap's [TBD-11 requirements and mandatory integration tests](spring-ai-responses-migration.md#tbd-11-correction-history-tool-availability-and-side-effects)
authorize only two ordinary-advisor changes: enforce no tool execution during final-
output correction, and retain completed tool exchanges/results in correction requests.
Preserve schema correction's latest-candidate policy and existing linter/evidence
feedback behavior. Preserve planning, step-loop final correction, budgets, retry
ownership, binding snapshots and all unrelated behavior. The findings are from source
and test inspection; tickets must include end-to-end reproductions with captured
requests and side-effect counts, not assume mocked advisor tests prove integration.
No second loop, new retry policy, public knobs, or speculative history redesign.

## Accepted TBD-12 targeted response-status changes

The roadmap's [TBD-12 requirements and mandatory ticket tests](spring-ai-responses-migration.md#tbd-12-refusals-incomplete-responses-and-partial-tool-calls)
authorize three targeted changes: explicit refusal/incomplete checks before downstream
acceptance/correction; zero tool dispatch from incomplete responses; and preserved
status/content diagnostics with usage counted once on rejection. Use provider metadata,
not prose heuristics. Preserve cancellation, transport retry policy, normal tool turns,
authorization and existing OpenRouter error-completion handling. Tickets must carry
exact adapter mappings/evidence and integration tests. Source inspection alone is not
an end-to-end proof for every SDK; missing status metadata is an integration blocker,
not permission to guess or add a competing transport or recovery system.

## TBD-13 attachment investigation — November 11, 2026

Historical investigation evidence; subsequent accepted decisions settle attachment
placement and defer native generated attachments beyond v1. No production code or dependencies changed, no hosted calls
or uploads performed. Pinned Spring AI 2.1.0-RC2 sources and an executable mock-HTTP
probe distinguish framework behavior, adapter serialization, and provider support.

### Existing framework behavior

`YamlSkillCatalog` accepts input-only `type: attachment`, with mandatory `media_type`
(image/pdf/audio/video/file) and nonempty `allowed_content_types`. TypeSafe rejects
attachment nodes. `DefaultMissionInputMaterializer` walks declared properties/items,
accepts strict `ref://` strings or repeatable Spring Resource values, resolves resources
through existing services, checks allowed MIME types, checks image signatures, and
checks the configured size limit (20 MiB default) using known size and streamed digest
reading. It produces attachment descriptors plus resources. Filename detection covers
common images, PDF, TXT, JSON and CSV; otherwise a single allowed MIME type supplies
the type. The media-type enum does not establish provider acceptance.

`SpringAiModelInteraction` sends each attachment via user-message `media(mime, resource)`.
This is an inline resource path, not an implemented provider Files API upload/download
lifecycle. Java Resource/InputStream/byte[] tool parameters use existing runtime
reference conversion; they are not automatically provider-native attachments merely
because they contain binary data. Preserve that distinction when choosing declarations.

### Reproducible adapter verification

Run with Java 21 JAVA_HOME and Maven:

```text
mvn -f docs/maintainers/probes/tbd-06/pom.xml test -Dtest=AttachmentProbeTest
```

[AttachmentProbeTest.java](../../../docs/maintainers/probes/tbd-06/src/test/java/AttachmentProbeTest.java)
uses the same ChatClient user-media Resource API as Loomspan. Two tests pass on Boot
4.2.0-RC2/Jackson 3.1.8, Spring AI 2.1.0-RC2, OpenAI SDK 4.80.0, Anthropic SDK 3.4.0,
Google GenAI SDK 1.73.0, Java 21. The input test covers 18 driver/MIME combinations.
The mock endpoint deliberately returns 400 after capturing serialization; synthetic
fixture bytes are not valid representative media. This proves adapter transport shape
and pre-HTTP rejection, NOT actual file decoding, model acceptance or interpretation.
No live route, full framework execution, attachment-plus-tools/schema combination,
streaming, or alternate Boot platform was validated by this probe.

Retained [evidence](../../../docs/maintainers/probes/tbd-06/evidence/attachments/)
includes request JSON, exceptions, summary CSV, output results, test report and source
JAR hashes. Pinned source: ResponsesItemMapper, AnthropicChatModel, GoogleGenAiChatModel.

| Resource MIME | OpenAI Responses | Anthropic user-media path | Gemini |
| --- | --- | --- | --- |
| image/png | input_image, inline data URL | image/base64 | inlineData |
| application/pdf | input_file, inline file_data | document/base64 | inlineData |
| text/plain, application/json | input_file | Rejected before HTTP | inlineData |
| audio/wav | Rejected before HTTP | Rejected before HTTP | inlineData |
| video/mp4 | Serialized as input_file; NOT proof of video API support | Rejected before HTTP | inlineData |

Images/PDFs are the shared tested serialization baseline. Do not infer that every
image subtype or every provider/model route works from this PNG fixture. Anthropic's
standard Media mapper specifically supports PNG/JPEG/GIF/WebP and PDF; SDK/native
text-document features do not imply they work through Loomspan's current Media path.
The Responses mapper rejects audio explicitly. Passing MP4 through its generic file
branch does not establish API support. Gemini passes MIME/data through; endpoint/model
support must still be verified. Do not add automatic modality/protocol fallback.

### Attachments generated as output

Synthetic HTTP responses prove the pinned OpenAI Responses adapter converts an
image_generation_call result to AssistantMessage Media, and Gemini converts inlineData
to Media; tests assert MIME and exact decoded bytes. They do not enable image-generation
tools, prove live generation, or establish schema-output coexistence. OpenAI's mapper
explicitly skips generated assistant media on replay; Gemini has a media replay path.
Any future multi-turn generated-media feature must account for this difference.

Loomspan's `SpringAiModelInteraction` extracts only first-generation getText() and
returns ModelInteractionResult(String content, context). It does not expose assistant
media or implement generated-file retrieval/storage. Thus Spring AI media parsing is
not end-to-end Loomspan attachment-output support. Existing tool-produced resource
references are a separate feature, not proof of native model-media output.

Official provider documentation identifies distinct output mechanisms:
- [OpenAI image-generation tool](https://developers.openai.com/api/docs/guides/tools-image-generation)
  returns base64 image data in Responses tool output.
- [Gemini image generation](https://aistudio.google.com/docs/image-generation) supports
  image output with suitable models. The pinned adapter handles inline binary parts.
- [Anthropic Files API](https://platform.claude.com/docs/en/build-with-claude/files)
  supports downloading files produced by hosted code execution/skills. This is a
  tool/file lifecycle, not an ordinary native image-output part. The pinned Anthropic
  mapper retains other native content as UnknownPart rather than downloading files.

Current official [OpenAI file-input docs](https://developers.openai.com/api/docs/guides/file-inputs)
and [Spring AI multimodality docs](https://docs.spring.io/spring-ai/reference/api/multimodality.html)
provide context; pinned code and captures define the tested adapter boundary.

### Decisions still required

Preserving attachment input execution is compatible with changing declaration metadata,
and the user subsequently chose the inline x-loomspan-attachment annotation.
Do not adopt the old pattern-based example: pattern is outside the approved v1 baseline.
Do not silently remove direct Resource input support to force JSON-only validation.
Generated-attachment output would require an explicit scope decision about enabling
provider tools/modalities, extracting results, storage/reference ownership, retrieval,
result exposure, and replay. It is additional functionality, not a necessary consequence
of migrating input/output schema syntax. No such implementation is authorized here.

## Accepted TBD-13 attachment placement and v1 output boundary

Preserve attachment inputs and defer native generated attachments beyond v1. Use
`x-loomspan-attachment` on the relevant standard input-schema node, retaining existing
`media_type` and `allowed_content_types` fields inside it. Nested/array attachment
locations follow properties/items, without a separate path map. Strip this recognized
framework annotation from provider schema copies while retaining canonical metadata
and standard assertions. The earlier `input_attachments` proposal is superseded.
Exact [ticket requirements](spring-ai-responses-migration.md#attachment-input-declaration--agreed-november-11-2026)
are in TBD-13. Direct Java Resource validation is now settled through the attachment-only adapter
below; do not stringify resources, fabricate placeholders, or remove existing support.
Non-attachment runtime-reference marker retention is also agreed below; evidence
evidence annotation placement and behavior are settled below; effective-schema display is settled below; TypeSafe migration boundaries are settled below; declaration authority is preserved below. Direct Java-value validation
uses the separately agreed adapter below.

## Accepted non-attachment runtime-reference metadata

Retain the existing boolean `x-loomspan-runtime-ref-capable` as internal generated
Java-input metadata. This does not introduce an authored YAML feature. Canonical
generated string schemas retain the marker; provider copies remove the annotation
at actual schema nodes while retaining type, description and standard assertions.
Similarly named data properties are preserved.

Source inspection: SkillMethodBeanPostProcessor applies the marker to Resource,
InputStream and byte[] leaves, including nested properties/collection items;
RefResolver selects marked leaves under explicit contracts; SkillInputValidator
accepts direct Resource, InputStream and byte[] at those leaves. Java binding owns
conversion and stream cleanup. Ordinary unmarked strings under explicit contracts
are not automatically resolved; generic input resolution has separate existing behavior.

Before and after, a canonical generated Resource leaf is
`{"type":"string","description":"Provide a ref:// URI for the resource content.","x-loomspan-runtime-ref-capable":true}`.
The migrated provider copy omits only the marker. Validate actual string inputs
before reference resolution, then preserve authorization, resolution, conversion,
cleanup and direct runtime-object support. A plain JSON validator would reject these
direct objects against string schemas, which is the concrete migration gap.

Direct-object validation uses the separately agreed adapter below. Do not automatically reuse
attachment descriptors, MIME checks or repeatability requirements. The exact rationale,
implementation boundary and mandatory ticket tests are recorded in
[TBD-13](spring-ai-responses-migration.md#non-attachment-runtime-reference-metadata--agreed-november-11-2026).
Tickets must cover marked/unmarked strings, validation before resolution, direct
objects, nesting/arrays, conversion failures and cleanup, authorization/generic
resolution, canonical retention and provider extraction preserving data properties.
No production changes or new test-run evidence accompany this agreement.

## Accepted direct Java runtime-value validation

Preserve the existing runtime-type gate at non-attachment marked Java-input leaves:
Resource, InputStream and byte[] are accepted without reading their content, while
actual JSON values are checked against the canonical leaf schema. Java binding owns
subsequent conversion failures and cleanup. Acceptance by the gate is not a guarantee
that every accepted runtime type converts to every declared Java target.

After all marked leaf occurrences pass their separate checks, validate surrounding
structure through NetworkNT with a private JSON view: direct runtime objects become
null placeholders and marked leaf schemas become {} in a private schema copy. Every
array occurrence must be checked before relaxing shared items schemas. Keep actual
strings subject to canonical assertions before resolution; actual nulls must satisfy
canonical nullability. Missing fields remain absent. Preserve properties, array
positions, requiredness, openness, bounds and ordinary sibling validation.

For example, an original input containing a Resource at `payload` and integer `count`
has an internal view with `payload: null` and the unchanged `count`. Only the private
payload schema becomes {}; the original Resource is passed to Java binding. A caller's
actual `payload: null` is independently rejected when its canonical schema forbids it.
This adaptation avoids inventing attachment descriptors for non-attachment objects.

Never mutate canonical input/schema, consume streams for validation, serialize object
internals, or expose the private view/schema to providers, callers or diagnostic evidence.
Diagnostics retain original paths/content. Do not broaden this exception to unmarked
fields or apply attachment MIME/repeatability requirements.

Mandatory ticket tests cover all three runtime types and unchanged binding identity;
existing conversion failures/cleanup; no validation-time reads; unmarked rejection;
canonical string checks before resolution; mixed arrays with later invalid elements;
required/optional absence and allowed/prohibited null, including nested/array cases;
ordinary sibling errors, closed-object extras and array bounds; no invocation after
validation failure; canonical immutability and no private placeholders/weakened schemas
in provider requests or diagnostic evidence. The authoritative detailed requirements
are in [TBD-13](spring-ai-responses-migration.md#direct-java-runtime-value-validation--agreed-november-11-2026).
No implementation or new test-run acceptance is claimed. Existing declaration authority
is preserved below; recursive types are settled below and unconstrained Object input support is agreed below.

## Accepted direct Resource attachment boundary

#### Direct Resource inputs and attachment validation — agreed

Retain direct repeatable Spring Resource inputs for v1 alongside existing ref://
inputs. There is no supported public resource-registration API: the internal VFS
resolves session-local files, while SkillTemplate creates a fresh session and exposes
no pre-invocation registration operation. Do not require applications to use internal
session/VFS types, or add a public registration API merely to accommodate JSON validation.

Keep the validation adapter limited to declared attachment nodes. Preserve the original
input and canonical schema. Validate reference strings against their standard schema
and existing reference syntax before resolution; preserve existing resource checks for
direct Resource values and resolved resources. String constraints govern reference
strings, not resource filenames/bytes. Existing attachment size settings govern bytes.
Use existing materialized attachment descriptors in an internal JSON validation view
and the corresponding descriptor contract at attachment leaves in a private schema
copy. Preserve every property, array position, and parent constraint. Missing and null
values must not be manufactured into descriptors. Validate ordinary sibling values,
requiredness, additionalProperties and array bounds through the shared validator.
Neither this view nor its schema is sent to providers or returned as rewritten caller
input. Do not stringify Resources, invent reference placeholders, or skip surrounding
validation. Do not expand this adapter to ordinary JSON fields or automatically reuse
it for separately marked Java resource-parameter conversion.

Tickets must include both reference and direct-Resource cases, nested/array cases,
missing required fields, invalid ordinary siblings, array bounds, undeclared fields,
invalid MIME/signatures, nonrepeatable resources, unreadable resources and size failures.
Verify canonical inputs/schemas remain unchanged and standard provider schemas do not
contain internal descriptor contracts. Retain current execution routing: planning
currently receives attachment descriptors, while execution receives media; changing
planner access to file bytes requires a separate decision, not an incidental migration.

**Nullable attachment handling — agreed:** absent optional attachment fields produce
no media. Explicit null produces no media only where permitted by the standard schema;
otherwise it fails validation. Required nullable fields must still be present. Preserve
nulls in the input/validation view, and never manufacture descriptors for missing or
null values. Update materialization to skip permitted null attachment values. Tickets
must test absent optional, absent required, permitted null, prohibited null, and nested/
array-item null cases. This is a targeted migration adjustment, not permission to
ignore nulls or relax requiredness globally. Preserve descriptor-only planning and
media-bearing execution; remaining attachment work is verification as listed below.

Remaining verification is implementation acceptance, not authority to invent policies:
verify real supported files on selected provider/model routes, multiple attachment
association/order, attachment-plus-schema/tool calls, and attachment preservation in
correction requests under TBD-11. The existing mock MIME matrix proves serialization
only. Unsupported media follows TBD-09, without conversion/protocol fallback. Preserve
the existing per-attachment size limit; no new count/aggregate limit is selected here.


### Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026

Preserve the existing developer-facing roles and signatures. SkillMethod remains a
Java-first callable declaration: Java parameters, SkillParam metadata and existing
runtime-reference handling produce its canonical standard JSON Schema. It already
emits standard schema vocabulary; the migration removes lossy internal conversion and
custom structural validation, not its Java authoring model. SkillExecutor remains a
reference-only implementation accepting one SkillExecutorInvocation; its YAML skill
owns the business-input contract. Migrate that YAML to standard schema syntax without
reflecting the invocation/context/configuration record into a competing input schema.
Preserve duplicate callable-name rejection, executor handoff and configuration behavior.
No new annotations, signature redesign, schema override/merge system or public API is
selected. Existing ownership is a preservation requirement, not a new policy mechanism.

Compile generated and authored input contracts through the same standard pipeline.
Validate before Java binding; conversion cannot rescue an invalid contract. Preserve
map parameters and existing resource handling with the separately agreed adapters.
Already agreed exact-name/no-coercion and omission/null rules still apply: preserving
signatures does not preserve acceptance of numeric strings as integer inputs.
Recursive Java input types are rejected as agreed below; unconstrained Object input support is agreed below;
this agreement does not promise every existing signature is supported.

For example, Map<String, Integer> quantities retains a named object property whose
additionalProperties is {type: integer}. NetworkNT validates each entry before Java
binding. Allow that input vocabulary for authored schemas too, rather than creating a
Java-only exception. Maps used for execution/configuration/internal state are unaffected.
Keep existing runtime-reference traversal of map-value schemas and whole-value binding
transfers. Do not introduce arbitrary-key binding paths or general schema algebra.

Provider implementation scope:

- Responses: preserve the full input schema and explicitly send tool strict=false.
  OpenAI, Meta, OpenRouter and Ollama share this mapper; route acceptance is separate.
- Anthropic: preserve nested typed-map schemas and fix the already identified root
  constraint loss. This root fix is independently required for ordinary contracts.
- Gemini: preserve the canonical schema through raw parametersJsonSchema, bypassing
  both GoogleGenAiToolCallingManager's OpenAPI conversion and the final parameters(Schema)
  conversion. A final-setter change alone is insufficient. Raw SDK serialization is
  evidenced; the exact Spring AI integration remains an implementation gate, not an
  established options-only workaround. Keep the existing tool manager/loop ownership.

This is the simplest evidenced direction: retain useful Java declarations and fix
concrete schema-fidelity gaps. It needs no second validator, new tool loop, map conversion,
provider registry or silent schema weakening. Output/planning feature restrictions stay
separate. Relevant provider contracts: [OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling),
[Anthropic strict tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use),
[Google FunctionDeclaration](https://aistudio.google.com/api-reference/caching#Resource:FunctionDeclaration).

Mandatory ticket acceptance tests:

- Generate and validate string-, integer- and record-valued maps, nested maps and maps
  inside arrays/records. Preserve value constraints, descriptions, requiredness and
  closed record properties. Invalid entries fail with precise paths before invocation;
  integer-valued maps reject numeric strings under the agreed no-coercion policy.
- Equivalent authored and generated input contracts have equivalent standard validation.
  Preserve input values, Java binding, runtime-reference traversal and whole-map binding
  transfer without expanding binding path syntax. Retain direct runtime-value adapter
  tests for marked leaves reached through map-value schemas.
- SkillMethod parameter metadata remains effective. YAML executor input validation
  precedes invocation; SkillExecutorInvocation input/configuration/context handoff and
  duplicate callable-name rejection remain unchanged. Do not generate a business-input
  contract from the executor's invocation record.
- Capture actual requests through all three integrations. Assert complete nested map
  schemas and ordinary root constraints, explicit Responses strict=false, and Gemini
  parametersJsonSchema fidelity without prior lossy conversion. Preserve options and
  canonical schemas; no fallback or replacement of constrained values with open maps.
- Verify selected live provider/model routes, including a typed-map tool alongside a
  separate supported structured final-output contract. Shared mapper proof does not
  prove Meta/OpenRouter/Ollama route acceptance. Missing credentials or rejected features
  remain explicit gates, not passing results. Full framework validation must prevent
  invalid tool arguments from invoking Java side effects.
- Continue rejecting typed-map native output/planning contracts under their deferred
  scope; input support must not silently enable excluded output features.

Existing AdapterTest sources/captures and RC2 source inspection support these findings;
no new live calls, production implementation or ticket creation accompany this agreement.

## Recursive Java input types — agreed November 11, 2026

Reject recursive types exposed through generated skill-input contracts at registration,
before publishing the capability. Replace the current visiting-class fallback to
{type: object} with a clear error identifying the method, parameter/property path and
recursive type cycle. That fallback avoids endless generation but silently drops the
recursive node's field constraints; later Java binding is not equivalent validation.
Faithful arbitrary-depth recursive contracts require references or another recursive
validation mechanism. Schema references remain excluded from v1; do not add a separate
Java validator, schema truncation or an invented expansion-depth limit.

Detect direct and mutual recursion, including cycles through collection items and
map-value types. Track the active expansion path using resolved type information:
repeated classes in independent branches are not cycles, and finite generic nesting
such as Box<Box<String>> must not fail merely because the raw Box class repeats.
This concerns exposed input contracts, not recursive application structures behind
an executor or ordinary acyclic nested DTOs. Runtime values and standalone Object
acceptance are separate concerns.

Mandatory ticket acceptance tests:

- Reject direct self-reference and mutual recursion with method, input path and cycle
  diagnostics; reject before capability publication or provider invocation.
- Reject recursion through arrays/collections and typed-map values, including nested
  combinations; do not emit an open-object fallback, partial contract or depth cutoff.
- Accept repeated DTO types in sibling properties and separate parameters, acyclic
  nested DTOs, and finite resolved generic nesting such as Box<Box<String>> with all
  leaf constraints retained. Include a genuinely recursive generic declaration to
  ensure resolved-type handling does not miss cycles.
- Preserve unrelated valid SkillMethod declarations and YAML-owned executor contracts;
  do not inspect/restrict recursive data structures internal to executor implementations.
- Replace the existing recursiveDtoFallbackRemainsBoundedAndObjectShaped acceptance
  expectation with explicit registration rejection and nonrecursive regression cases.

Source evidence: LoomspanMethodInputSchemaGenerator.schemaFor currently uses a set of
raw classes and emits only type: object when a class repeats; the existing generator
unit test explicitly asserts that fallback. This agreement is not a production change
or new passing-test claim.


## Unconstrained Object input values — agreed November 11, 2026

Preserve unconstrained JSON-compatible values in ordinary generated and authored input
contracts. Java Object maps to an empty schema {}, optionally with description metadata;
this applies to named parameters, nested fields, collection items and map values.
Invocation roots remain objects with named arguments. Unlike the recursive fallback,
Object deliberately declares no value constraints, so the empty schema is faithful.
Do not replace it with type: object or type: string, stringify values, infer constraints
from runtime contents, or introduce a separate Java-only schema feature. Native
output/planning scope is unchanged; this agreement concerns ordinary inputs.

Retain JSON-compatibility checks before conversion: JSON scalar values, finite numbers,
string-keyed maps and lists are accepted recursively; arbitrary POJOs, Resources,
InputStreams, binary arrays and non-finite floating-point values do not acquire
permission merely because the declared Java target is Object. Do not serialize Java
internals to manufacture acceptable JSON. Existing explicitly marked resource adapters
remain separate. Under explicit contracts, ref:// strings in unconstrained values stay
ordinary strings, including nested values; preserve separate generic-contract behavior.

Preserve descriptions and surrounding requiredness, object openness and array bounds.
Under standard semantics {} permits actual null. Required controls presence: a required
Object value may be null but must be present. Replace the old ordinary-validation
required-null-as-missing behavior here; optional absence stays absent. Keep canonical
input/schema intact and preserve Java binding after successful validation. Provider
transport and selected live routes must carry/accept unconstrained leaves without
silent narrowing; existing mapper evidence is not live acceptance proof.

Mandatory ticket acceptance tests:

- Accept string, boolean, finite numeric, object, array and null values, including
  mixed nested JSON structures, through equivalent generated/authored input schemas.
  Cover standalone Object parameters, DTO fields, collection items and map values.
- Required absent fails, required present null passes, optional absence remains absent
  and optional present null passes. Keep invocation roots object-shaped.
- Reject non-string object keys, non-finite floats/doubles, arbitrary POJOs, Resource,
  InputStream and byte[] values, including nested occurrences, before Java invocation.
  Do not consume streams or serialize object internals during these checks.
- Preserve literal ref:// strings under explicit unconstrained contracts, unchanged
  values, descriptions and Java binding. Retain distinct marked-resource and generic
  input resolution regressions.
- Verify ordinary sibling failures, closed-object extra fields, required properties and
  surrounding array bounds still fail; unconstrained leaves grant no parent bypass.
- Capture all three integrations' actual tool requests with unconstrained leaves and
  descriptive-only leaves. No inferred type, substituted string/object contract, lost
  description or canonical mutation. Include selected live-route acceptance alongside
  local invalid-input/non-invocation fixtures; untested routes remain explicit gaps.

Existing generator and postprocessor tests demonstrate intended JSON-value acceptance
and binding. No production changes, new test-run acceptance or tickets accompany this
agreement.


## Output evidence metadata — agreed November 11, 2026

Replace the old property-level evidence keyword with x-loomspan-evidence on the same
immediate root output property. For example, properties.likelyCause retains type: string
and changes evidence: classifyIncident and (investigateNetwork or investigateApp) to
x-loomspan-evidence: classifyIncident and (investigateNetwork or investigateApp).
Keep one nonblank string expression, not a metadata object or a separate path map.
Reject legacy annotation syntax; ordinary data properties named evidence or
x-loomspan-evidence remain legitimate properties. This authorizes only this recognized
output annotation, not arbitrary extensions or nested evidence declarations.

Preserve existing placement restrictions (immediate root output properties only),
expression grammar, namespace/direct-child resolution, original authored text and
1-based diagnostic columns. Compile into the existing EvidenceContract separately
from structural schema validation. Canonical schemas retain the annotation; provider
schema copies remove it at schema-node positions without altering standard assertions
or similarly named data properties. Keep existing runtime evidence instructions and
correction behavior; removing the annotation from provider schemas does not remove
framework evidence enforcement.

Rationale: schema validity cannot prove that a direct child executed successfully.
Evidence already has a separate execution-history validator; the migration changes
its declaration vocabulary rather than introducing a new evidence system. Keeping
metadata beside the claim follows the attachment design without a second path language.
Preserve planning coverage of every annotated claim (including optional claims), final
coverage of present claims only, successful-direct-child credit, failed/cancelled-call
non-credit, nested-mission isolation, bound-output checks and TBD-11 correction rules.
A permitted null is still a present claim and needs evidence; optional absence does not.
Evidence proves execution supportability, not factual correctness or value provenance.

Apply already agreed exact property-name semantics to evidence lookup: remove the
current lowercase/trim claim normalization, while retaining existing expression/operator
and namespace rules. Do not conflate claim-name matching with skill-identity parsing.

Mandatory ticket acceptance tests:

- Accepted annotation on immediate root output properties; rejection at root, nested
  properties, array items and input schemas. Reject blank/non-string expressions and
  legacy evidence annotation syntax without aliases or coercion.
- Preserve and/or precedence, parentheses, direct-child identity/namespace validation,
  unknown/wrong-case reference diagnostics, original expression text and token columns;
  diagnostic schema paths use x-loomspan-evidence.
- Preserve planning coverage including optional claims, successful direct-child credit,
  failure/cancellation non-credit, repeated-call set behavior and nested isolation.
- Final validation evaluates present claims after structural acceptance: optional absent
  claims need no credit; present null and non-null claims do. Preserve required-field
  failures, bound-output evidence checks and existing correction budgets/no-tool rules.
- Exact-case and whitespace-distinct claim names remain distinct; do not trim or fold
  property names. Unannotated properties acquire no inferred evidence requirement.
- Canonical metadata and authored source retain the annotation/text. Provider copies
  omit only the recognized schema annotation, retaining standard constraints and data
  properties named evidence or x-loomspan-evidence, including nested property trees.
- Retain existing evidence expression, catalog, planning, binding, correction and
  successful-child boundary regressions. Update authoring examples and diagnostic
  fixtures with the migration; schema display and TypeSafe consumers are reviewed next.

No production implementation, new test-run acceptance or ticket creation is claimed.


## Effective-schema display and catalog metadata — agreed November 11, 2026

Preserve effective output schemas as structural result metadata, distinct from canonical
skill declarations and execution-local evidence contracts. SkillDependencyValidator
currently prepares this metadata, follows output_from to its producer, and deliberately
excludes child-local evidence annotations. DefaultSkillCatalog and
DefaultRegisteredSkillCatalog expose the prepared schema; ObservabilityDtoMapper passes
it through and Console SkillDetail renders it as inert text beside original YAML.
TypeSafe contributes its generated descriptive result schema through the same path.

Migrate this representation to standard JSON Schema. Replace the old fixed-field
reconstruction with a faithful structural copy removing only recognized execution
annotations at schema-node positions. Preserve all supported standard constraints,
descriptions and similarly named data properties. Do not use a provider-adapted request
copy for catalog metadata: provider-specific omissions must not weaken the displayed
contract. Canonical declarations, original YAML and compiled evidence contracts retain
their metadata; the structural view omits x-loomspan-evidence deliberately.

Rationale: a forwarding skill can expose its producer's output structure without
appearing to inherit that producer's private child-execution requirements. Retaining
canonical annotations does not require propagating them into every derived view.
Preserve existing forwarding/binding semantics, source text fidelity, catalog snapshots,
public descriptor signatures and Console display behavior. No browser validator, new
schema UI or extra public API is required. Keep Unspecified when no output schema exists.

Mandatory ticket acceptance tests:

- Direct and output_from-forwarded effective schemas retain standard assertions,
  descriptions, nullability and supported nested structure without legacy nullable
  vocabulary or fixed-field keyword loss. Preserve forwarding chains and cycle errors.
- Exclude child-local x-loomspan-evidence from structural catalog/display metadata,
  while canonical declarations, original YAML and compiled evidence contracts retain
  annotation/expression content. No producer evidence requirements become parent rules.
- Preserve data properties named evidence or x-loomspan-evidence, including nested
  properties. Schema-node extraction must not delete arbitrary matching data names.
- Public catalog, observability DTO and Console agree on the effective schema. Preserve
  exact original YAML separately and inert text rendering; absent schemas remain
  Unspecified. Cover TypeSafe-derived schema propagation and forwarding.
- Provider request adaptations do not mutate effective/catalog/canonical schemas.
  Preserve generation snapshot coherence and existing binding/output authority tests.
- Update affected Java and Console fixtures/contracts as needed without changing
  public descriptor signatures or adding an independent client-side schema validator.

No production implementation, new test-run acceptance or ticket creation is claimed.


## TypeSafe migration boundary — agreed November 11, 2026

Migrate TypeSafe's shared schema representations while preserving native evaluation
behavior. Authored input_schema adopts standard JSON Schema and shared validation,
including the agreed exact-name/no-coercion, nullability and ordinary-input feature
rules. Keep the required input schema and reject attachments using the new annotation
through all supported schema locations, including map-value schemas. TypeSafe request
state still requires JSON-compatible values; unconstrained input does not admit Java
resource objects or bypass native request checks.

Replace TypeSafeDeclaration's OutputSchemaManifest dependency with the standard schema
representation for its generated descriptive result metadata. Preserve noul, choice
and score shapes, fixed-criteria enums/named probability fields, requiredness,
descriptions and explicit existing object openness. Dynamic probability maps and score
legends remain open objects. Keep probability/confidence/score ranges descriptive;
do not add new numeric assertions or typed-map constraints to these result schemas.
Expose the migrated metadata through the existing catalog/forwarding/display path.

The generated result schema is not currently enforced against TypeSafe responses.
TypeSafeExecutor parses native response JSON, requires an object-valued answers field
and returns that object; preserve this acceptance behavior. Do not introduce generated
result-schema enforcement, authored output schemas, chat structured-output options or
schema-repair retries. Keep native typesafe declaration validation, input substitution,
post-substitution request validation, immutable request reuse, connection/model binding,
transport, retries, cancellation, accounting, diagnostics and answer extraction.
Successful TypeSafe children continue to participate in parent evidence under existing
rules; no TypeSafe-local output evidence declaration is introduced.

Rationale: this removes the legacy schema dependency and integrates shared input
validation without redesigning a working native evaluator. Result-schema enforcement
would add new rejection behavior and policy questions and is outside this migration.
It is not required to display standard descriptive metadata or validate caller inputs.

Mandatory ticket acceptance tests:

- Equivalent descriptive standard schemas for noul, fixed/dynamic choice and
  fixed/dynamic score: preserve enums, named fields, requiredness, explicit openness,
  descriptions and legends; no newly asserted numerical bounds or legacy dialect.
- Standard authored input validation precedes native calls; invalid input prevents
  provider invocation. Preserve required input-schema and attachment rejection,
  including nested/items/map-value declarations and non-JSON native request inputs.
- Preserve template substitution, literal/reference handling, post-substitution native
  request checks and exact request content/reuse across transport retries. Generated
  result metadata must not be sent as chat output configuration or native request fields.
- Preserve acceptance of object-valued answers, including additional answer fields
  and objects not satisfying the descriptive result schema. Malformed JSON and absent,
  null or non-object answers still fail through existing response handling. Do not
  introduce schema repair or additional calls for these failures.
- Preserve response diagnostics, usage recorded for successful HTTP responses even
  when answer extraction fails, cancellation and existing retry ownership/accounting.
- Preserve authored output-schema/unsupported execution-field rejection and empty
  TypeSafe-local evidence contracts; successful child credit remains unchanged.
- Catalog, forwarded effective schemas and Console expose the migrated descriptive
  schema consistently, with original source text preserved and no provider-copy mutation.

No production implementation, new test-run acceptance or ticket creation is claimed.


## Acceptance ownership, suite gaps and mandatory gates — agreed November 11, 2026

Use loomspan-framework for deterministic implementation coverage and the neighboring
loomspan-sidecar-test-suite for deployed migration acceptance. The latter already hosts
both an embedded Java Framework application and a Python/SDK/Sidecar application, plus
separate Java/Python SDK consumers. It is not merely an HTTP-only Sidecar test harness.
Reuse its capability-oriented suites, runtime orchestration, artifact recording and
provider-disabled restoration; do not create a second live acceptance harness here.
Fix tracking stays in the owning product repository, not PR-specific suite partitions.

| Acceptance area | Framework repository | Sidecar test suite |
| --- | --- | --- |
| Schema semantics and Java declarations | Exhaustive compiler/validator, generation, binding, recursion errors and resource checks | Representative deployed input/output and real Java declarations with equivalent Sidecar callbacks |
| Provider contracts | Actual request captures for all three integrations, schema/options fidelity, ordered replay and tool identities | Selected live endpoint/model/route compatibility through deployed integrations |
| Repairs/status/side effects | Deterministic refusal/incomplete, repair history, authorization and duplicate-side-effect prevention | Small controlled scenarios verifying deployed wiring and observable behavior |
| Planning/bindings/evidence | Detailed semantic, projection and isolation regressions | Existing composed workflows plus targeted missing integration cases |
| Attachments | Validation, direct Java resource handling, materialization and serialized requests | Real supported files on selected live routes through supported host entry points |
| TypeSafe | Declaration/metadata and native executor unit tests | Existing native protocol, passthrough, forwarding, bindings and policy regressions |
| Catalog/Console | Metadata and Console contract/rendering tests in framework/loomspan-console | Relevant external integration assertions only |

Inspected suite gaps to close:

1. fixtures/server.py serves /v1/chat/completions and proxies live chat to OpenRouter's
   Chat endpoint. fixtures/replay.py matches messages/reasoning_effort. Migrate request
   matching, response envelopes, controlled responses, usage reporting and live proxy
   handling to Responses for the migrated openai driver. Preserve maintained reference
   packs; derive new protocol fixtures separately. Reusing accepted business answers
   proves business regression, not ordered reasoning/tool replay fidelity.
2. Existing chat live orchestration is OpenRouter-specific. Add bounded selections for
   OpenAI, Meta, OpenRouter, Ollama, Anthropic and Gemini through existing orchestration.
   Host coverage is agreed below; run budgets are agreed below; model/route selections and timeouts are finalized below.
   Do not multiply the entire equipment workflow across every provider to prove protocol
   compatibility; use small capability-oriented cases.
3. Migrate maintained YAML and its authoring generators/contracts together, including
   explicit intended openness and accepted metadata vocabulary. Preserve business
   behavior and Java/Sidecar equivalence. Regeneration must not restore the old dialect.
4. Add focused scenarios for typed-map/unconstrained inputs, native schema-plus-tools,
   response refusal/incompleteness, and attachments where existing business scenarios
   do not establish these contracts. Keep exhaustive variants in framework tests.
5. Stage coherent migrated Framework artifacts in both Java and Sidecar hosts. Reuse
   build recording to identify actual artifacts/configuration and relevant source/suite
   revisions. A shared mutable snapshot coordinate alone is not migration acceptance.

The TypeSafe suite already covers fixed/dynamic questions, substitution, forwarding,
bindings, malformed envelopes, extra answers and native passthrough. Migrate its schemas
and chat-parent fixtures and rerun that coverage rather than invent a new native test
suite. New paid TypeSafe evaluation is not inherently required for this migration.
SDK TLS/mTLS and service-commitment checks are affected-path regressions, not tests to
repeat for every model. The suite's --suite all covers its business suites, not every
SDK/transport/commitment check; reports must name actual selections.

Mandatory acceptance gates for later tickets:

- Passing applicable deterministic framework tests, including the previously agreed
  numbered TBD cases, real factory/advisor/tool pipeline captures and public-surface
  architecture checks after production type changes. Isolated SDK proof is insufficient.
- Coherent migrated dependencies/artifacts in both deployed hosts, migrated authoring
  sources and provider-free business/TypeSafe integration regression with maintained
  fixtures preserved. Report selected checks and uncovered paths explicitly.
- Responses fixture and live-path readiness before claiming migrated integration
  coverage; no Chat fallback, old runtime implementation or successful legacy replay
  counted as proof of the new path. Native Anthropic/Gemini paths remain native.
- Selected live route cases for the required provider endpoints, with exact model/route,
  versions, schemas/requests, outcomes, local validation, usage, retries and limits
  recorded. Host coverage is agreed below; cases and budgets are agreed below; model/route and effort selections are finalized below; wall-clock settings are agreed below.
- Resolve relevant replay/schema mapper defects, including Anthropic root constraints
  and Gemini raw tool-schema fidelity. A successful final answer cannot substitute for
  preserved protocol state and schema evidence. Do not silently drop required features.
- Missing credentials, unavailable endpoints, unresolved dependencies/mapper defects,
  and failing required features remain explicit open completion gates. Independent work
  may continue; skipped/untested cases are not passes and trigger no automatic fallback.
- Keep deterministic correctness, deployed integration, live compatibility and observed
  quality/performance evidence distinct. Prior suite reports apply only to their recorded
  builds. Publish evidence references from this roadmap even when tests run elsewhere.

Inspection sources: ../loomspan-sidecar-test-suite/AGENTS.md, docs/project-agreement.md,
docs/coverage.md, docs/runtime.md, docs/typesafe-ai.md, fixtures/server.py,
fixtures/replay.py, scripts/run_suite.py and scripts/suites/registry.py. Inspection was
read-only: no tests, builds, runtime changes or paid calls were performed. Future paid
scope is not authorized by this documentation update; no tickets are created here.


## Live host coverage — agreed November 11, 2026

Use reduced live duplication within loomspan-sidecar-test-suite. Both hosts below are
owned by that suite: its embedded Java Framework app and its Python/SDK/Sidecar app.
Do not interpret Java-host acceptance as a new live harness in loomspan-framework.

| Coverage | Required host selection |
| --- | --- |
| Provider-free business/schema/callback and TypeSafe regression | Both suite hosts |
| Full bounded provider-compatibility cases for OpenAI, Meta, OpenRouter, Ollama, Anthropic and Gemini | Embedded Java host |
| Representative live tools-plus-structured-output case for each of the three integrations (OpenAI Responses, Anthropic, Gemini) | Python/Sidecar host |
| Host-specific or unexplained discrepancy | Reproduce on both hosts as needed to resolve it |

Meta, OpenRouter and Ollama receive full selected live-case coverage on the Java host;
their Sidecar configuration is checked deterministically. Use the OpenAI endpoint for
the representative Sidecar Responses case. Cases and run budgets are agreed below; timeout
settings are agreed below. The full matrix means the agreed bounded cases,
not every model, every schema keyword or the entire equipment workflow per endpoint.

Rationale: verify all required endpoints through deployed Framework and all three
integration paths through Sidecar without repeating the whole live matrix twice.
This does not establish live acceptance of every provider/host combination. Reports
must explicitly distinguish live verification from deterministic configuration checks.

Mandatory acceptance requirements: both-host provider-free results; Java-host live
results for all six selected endpoint routes; Sidecar live tools-plus-structured-output
results for the three selected integration representatives; deterministic Sidecar
configuration checks for remaining Responses routes; and resolved or explicitly open
host-specific discrepancies. Record host, artifact/configuration identity and route
for every result. Missing required cells remain open gates under the preceding rules;
do not infer live Sidecar route acceptance from the Java host's success.

No paid run or implementation is authorized or performed by this decision.


## Protected framework acceptance suite and Meta Contributor — agreed November 11, 2026

Maintain a durable framework-acceptance suite inside loomspan-sidecar-test-suite,
separate from business examples and SDK/transport suites. Own its scenarios, skills,
fixtures, provider/host matrix and expected results in dedicated suite locations using
the existing suite layout and explicit runner selection. This is framework contract
coverage, not a disposable migration-specific folder or PR partition.

Protect coverage through a checked-in manifest identifying required contracts and
host/provider cells. Verification must fail when a required case disappears or is
silently skipped. Business generators and fixture-refresh commands must not modify
framework-acceptance assets. Keep maintained accepted fixtures separate from fresh
run output; changing expectations requires a deliberate update with recorded rationale.
Shared runtime/reporting infrastructure remains reusable; changes to it require the
framework suite's applicable provider-free regressions. Protection means stable contract
coverage and reviewed updates, not permanently frozen provider IDs or protocol files.

Mandatory acceptance tests: explicit suite selection/discovery; required-case manifest
completeness; missing/skipped-cell failure; isolation from business authoring/refresh
commands; refusal to overwrite maintained fixtures with run output; independent fresh
result directories; framework regression selection for shared harness changes; and
reports distinguishing current tested artifacts, live/provider-free cells and gaps.
Keep existing business assignments and accepted reference packs unchanged.

Select muse-spark-1.3-contributor for direct Meta Responses acceptance, replacing the
previous standard-mode proposal. Meta documents the same model at discounted prices
in exchange for permission to use submitted content and responses for training.
Use deliberately authored synthetic acceptance data; this is not a content-masking
mechanism. Contributor does not offer max reasoning effort; use a supported setting
for continuation tests. Account access and actual route acceptance remain unverified.

Published prices checked November 11, 2026: Contributor input $0.10 and output $0.20
per million tokens, versus Standard $1.25/$4.25; cached Contributor input $0.002.
Source: https://dev.meta.ai/docs/pricing-rate-limits and
https://dev.meta.ai/docs/choose-a-model. Recheck pricing before an authorized run.
The earlier exclusion of Contributor was not a necessary compatibility boundary.

The final endpoint model selections below supersede earlier proposals. Bounded
cases, request/token caps and USD 5 ceiling are agreed below; timeout
settings are agreed below. Run deterministic checks first; use compact synthetic skills and tool
results, small real media files, and physical-provider-request accounting. No new paid
TypeSafe evaluation is required. No paid calls, production edits or tickets are
performed or authorized by this document update.


## Bounded live cases, token budgets and acceptance rules — agreed November 11, 2026

These are initial acceptance-run ceilings in the protected framework-acceptance suite,
not production defaults or targets. Execute only after deterministic readiness checks
and separate explicit paid-run authorization. Use tiny purpose-built skills, synthetic
identifiers, short tool-result objects and small real files. Do not substitute a full
business workflow or repeatedly sample until a desired outcome appears.

| Case | Required evidence | Maximum physical requests per route | Maximum generated tokens per request |
| --- | --- | --- | --- |
| Small structured result | Native output configuration, required/nullable fields, local acceptance | 1 | 1,024 |
| Two-tool chain to structured result | Typed-map and unconstrained arguments, tool-result use, ordered continuation, no duplicate execution | 3 | 4,096 |
| Small planned task | Actual planner contract, deterministic child execution, bindings/evidence handling, final result | 4 | 4,096 |
| Two small images plus tool | File association/order, media-bearing execution, tool continuation, structured result | 2 | 2,048 |
| One-page PDF plus tool | Real PDF acceptance, attachment continuation, structured result | 2 | 2,048 |
| Full route total | All cases above, through real framework execution | 12 | 37,888 aggregate generated tokens |

Run all five cases on six Java-host endpoints: at most 72 requests including 12 local
Ollama requests. Run only the two-tool-chain case on the Sidecar OpenAI, Anthropic and
Gemini representatives: at most nine additional requests. Overall ceiling is 81 requests,
of which 69 are cloud requests. These count physical sends, including planner and
continuation calls, not logical tests or skills. A request that fails or times out still
consumes its send allowance; do not assume it was unbilled.

Limit every request to 8,192 total input tokens, counting framework instructions,
schemas, retained history and media accounting. Across cloud requests the mathematical
maximum is 565,248 input tokens and 226,304 generated tokens (approximately 566,000 and
227,000 respectively). Generation allowances include reasoning and visible output;
verify each provider/adapter's budget semantics before running. Final visible responses
should ordinarily be under 200 tokens, but this is a fixture design target, not a new
production response restriction. Do not truncate protocol history or schema content to
force a request below the cap; simplify fixtures or record a blocked case.

The total cloud spending ceiling is USD 5 for this initial matrix, not USD 5 per route
or rerun. Recheck selected-route pricing at preflight and calculate a conservative
estimate from actual fixtures. Before sending, reserve the maximum estimated request
cost including input, media, replay and maximum billed generation/reasoning; reconcile
with usage without counting reasoning twice. Keep reservations for uncertain billed
outcomes. Stop before a send if remaining allowance is insufficient. If pricing/media
accounting cannot be bounded reliably, leave the case pending. This ceiling does not
guarantee matrix completion. Do not rely only on after-the-fact usage totals, assume
cache discounts, silently reset the ledger, or add paid provider tools. These are suite
run controls, not new Framework public configuration requirements.

Disable automatic transport retries and automatic schema-repair attempts for this
initial compatibility batch. Existing production retry/repair ownership remains intact
and is tested deterministically. Stop a failing case and diagnose offline; no automatic
increase of caps, effort, retries, model changes, fallback or weakened assertions.
Any subsequent rerun must retain previous spend accounting and have an explicit scope;
this agreement itself authorizes no paid sends.

Acceptance rules and mandatory ticket tests:

- Test request/token/cost reservations and stop behavior without paid calls: exact caps,
  one-over rejection, concurrent reservation safety if parallel sends are used, failed
  sends, uncertain billing, no implicit retries/repairs and no ledger resets on rerun.
- Demonstrate actual tool/planner/native-output paths and retained request evidence.
  Short successful text alone is not schema or reasoning-continuation proof. If no
  replayable reasoning is emitted, that assertion remains unverified, not passed.
- Unsupported PDF/media or combinations remain explicit gaps. Do not convert files,
  change protocols/models or silently skip cells into success. Missing access, adapter
  defects and exhausted budgets likewise leave required cells open.
- Pair live compatibility with deterministic invalid-result/argument rejection and
  side-effect checks. Refusal/incomplete responses, malformed arguments, repair history,
  and attachment preservation during corrections use controlled fixtures first.
- Preserve original requests, schema and diagnostics; compare accepted constraints and
  required item/order fidelity. Record host/route/model, actual usage/cost, attempts,
  local validation, artifacts and unverified assertions separately from task quality.
- A capped/incomplete generation is not successful validation. Initial batch failures
  cannot establish that the provider fundamentally lacks a capability without diagnosis.

Model/route, effort and wall-clock timeout settings are finalized below. Provider availability, credentials,
final dependency pins, adapter repairs and actual passing evidence are implementation/
execution gates, not facts established by this agreement. No paid calls have been made.


## Terra acceptance selection — agreed November 11, 2026

Use gpt-6.1-terra for direct OpenAI Responses acceptance and openai/gpt-6.1-terra
through OpenRouter's OpenAI standard endpoint (tag openai), replacing the prior Sol
proposal. Use the same direct Terra model for the Sidecar representative. Retain
explicit route restriction, disabled fallback and resolved-endpoint verification.
Business Sol/Luna assignments remain unchanged.

Official OpenAI documentation lists reasoning, function calling, image input and
structured outputs; tool calling requires Responses. The public OpenRouter endpoint
listing inspected November 11 advertises reasoning, tools and structured outputs for
its openai route, with text/image/file inputs. Both list standard short-context prices
of USD 1 per million input and USD 5 per million output tokens. This is half the
previous Sol proposal's USD 2/10 rates. Recheck prices/access before authorized runs.
Sources: https://developers.openai.com/api/docs/models/gpt-6.1-terra and
https://openrouter.ai/api/v1/models/openai/gpt-6.1-terra/endpoints.

Rationale: compact protocol/schema acceptance does not require Sol's model selection.
Retain all assertions, request/token limits and the total USD 5 ceiling; no automatic
fallback to Sol or budget increase if a case fails. Published capabilities are not
passing integration evidence; account access, PDF handling and exact combination
acceptance remain gates. No inference calls or runtime configuration changes occurred.


## Final model, route and effort manifest — agreed November 11, 2026

| Endpoint | Acceptance model | Protocol / route | Reasoning |
| --- | --- | --- | --- |
| OpenAI | `gpt-6.1-terra` | Direct Responses | Explicit medium |
| OpenRouter | `openai/gpt-6.1-terra` | Responses; OpenAI standard endpoint tag `openai` only; fallback disabled | Explicit medium |
| Meta | `muse-spark-1.3-contributor` | Direct Responses | Explicit medium |
| Ollama | `qwen3.5:4b-q4_K_M` | Local Responses | Thinking enabled; boolean-only model, not medium |
| Anthropic | `claude-sonnet-5-5` | Native Messages | Native output effort medium |
| Gemini | `gemini-3.8-flash` | Native Gemini Developer API | Native thinking level MEDIUM |

All six receive the bounded Java matrix; the three agreed Sidecar representatives
use the same direct OpenAI, Anthropic and Gemini selections. Business model assignments
remain unchanged. These selections supersede earlier proposals, not recorded historical
probe results. Medium is a provider-specific effort setting, not equal compute across
providers. Do not silently use defaults, lower effort, change models or expand budgets.

The inspected local baseline is Ollama 0.40.16, model digest
`2a654d98e6fba55d452b7043684e9b57a947e393bbffa62485a7aac05ee4eefd`.
Record/recheck the actual server version and digest before execution. The model reports
boolean thinking support; do not manufacture a numeric medium mapping. Verify the shared
Responses route can express enabled thinking; inability to do so remains a blocker.
OpenRouter must restrict routing to the standard OpenAI endpoint, disable fallback,
require support for requested parameters and record the resolved endpoint; a different
provider, Flex or priority route is not equivalent acceptance evidence.

Concrete code gap: SpringAiChatOptionsContributor currently maps medium to a 4096-token
thinking budget for Anthropic/Gemini. Anthropic additionally sets maxTokens to at least
8192. That is not native medium effort and exceeds these cases' generation caps.
Sonnet 5.5 rejects explicit thinking budgets. During implementation, use supported native
options for the selected models, retain the agreed total generation caps (including
reasoning), and capture the serialized request. Do not globally change legacy-model
behavior merely to configure this suite. If pinned adapters cannot express these options,
resolve that integration gap before paid acceptance; do not silently raise token limits.

Mandatory ticket acceptance tests:

- Offline manifest checks pin all six model/route/effort combinations, the three Sidecar
  representatives and Ollama digest; preserve business suites and their assignments.
- Capture actual adapter requests proving native medium on the five cloud endpoints,
  enabled thinking on Ollama, and unchanged per-case generation caps. Cover initial,
  tool-continuation and planner calls, including option combination/model overrides.
- Prove Anthropic sends no rejected thinking budget and does not inflate max_tokens;
  Gemini sends native MEDIUM rather than the legacy numeric budget. Unsupported option
  combinations fail preflight without paid sends or implicit fallback.
- Prove OpenRouter routing restrictions and resolved-endpoint evidence; reject missing
  parameter support or route drift. Published capability flags alone do not pass a case.
- Record requested and effective settings, actual usage and incomplete statuses in live
  evidence. Missing reasoning material leaves continuity unverified; timeout or capped
  output is not a pass. Retain the USD 5 ledger and zero automatic retries/repairs.

Provider references inspected November 11, 2026:
[OpenAI Terra](https://developers.openai.com/api/docs/models/gpt-6.1-terra),
[OpenRouter endpoint inventory](https://openrouter.ai/api/v1/models/openai/gpt-6.1-terra/endpoints),
[Meta reasoning](https://dev.meta.ai/docs/reasoning),
[Sonnet migration guide](https://platform.claude.com/docs/en/models/sonnet-5-5/migration-guide),
[Gemini 3.8 Flash](https://aistudio.google.com/docs/models/gemini-3.8-flash).
These are configuration evidence, not executed acceptance. Wall-clock settings are
agreed below. No production changes, tickets or paid calls are authorized here.

## Acceptance timeout profile — agreed November 11, 2026

Use one acceptance-only profile for every selected provider and both suite hosts:

| Boundary | Limit |
| --- | --- |
| Each physical provider request | 180 seconds |
| Fixture proxy upstream request | 210 seconds |
| Each case's mission | 900 seconds |
| Runner deadline including evidence collection grace | 960 seconds |

Rationale: the largest bounded case permits four physical requests, leaving 180 seconds
for orchestration after four 180-second requests. The same profile gives local Ollama
headroom without separate provider tuning. The proxy has 30 seconds of margin beyond
the framework request boundary; the runner has 60 seconds beyond the mission boundary
for terminal-state observation and evidence collection. These are maximum waits, not
performance targets or authorization to send additional requests.

Implement as a protected framework-acceptance suite overlay using existing timeout and
runner mechanisms. The inspected compose.runtime.yaml business profile remains
480/510/2400 seconds for provider/proxy/mission. Do not change production defaults,
business suites, TypeSafe timeouts or callback limits solely to configure this matrix.
Record effective values in run evidence. Verify actual adapter timeout semantics;
a socket inactivity timeout alone must not be reported as a total elapsed deadline.
The runner's polling deadline must account for in-flight polling/collection work, not
merely check elapsed time before starting another blocking poll. Reuse existing
cancellation paths; do not introduce a new public Framework timeout API for this suite.

Mandatory ticket acceptance tests (provider-free, using controlled delays/clock control
or scaled test durations rather than waiting for the full live limits):

- Effective configuration proves 180/210/900/960 seconds for the selected suite on both
  hosts and all selected routes, including option combination, model overrides,
  planner and tool-continuation requests. Business profiles remain unchanged.
- A stalled provider request terminates at its configured boundary without automatic
  retry, schema repair, model fallback or a subsequent tool dispatch from a late reply.
  A timely response still follows the normal tool/result pipeline.
- Mission expiry prevents further provider sends/tool dispatch for that case and uses
  existing cancellation/terminal-state behavior. Preserve already completed tool effects;
  do not claim cancellation can undo a tool or guarantee the provider stopped billing.
- Runner timeout and evidence-collection work are bounded by the outer deadline;
  pending HTTP polls cannot silently extend it. Timeout/collection failure remains a
  failed or incomplete case, never a successful validation or silently skipped cell.
- Preserve available request, status, timing and diagnostic evidence on every timeout.
  Count the physical send, retain uncertain-cost reservations and previous spend, and
  prove no automatic rerun, token-cap increase or budget reset follows timeout.
- Check the proxy/framework boundary ordering and provider-disabled restoration after
  failure. A missing terminal result or incomplete artifact is reported explicitly.

The request/token/USD 5 ceilings still govern independently. No paid sends, production
changes or tickets were performed or authorized by this documentation decision.

TBD-14 review outcome: testing ownership, protected-suite isolation, host coverage,
bounded cases, models/routes/effort, budgets, timeout settings and completion rules are
resolved as policy. Implementation and acceptance remain pending. Remaining gates are
actual dependency/adapter readiness (including native effort and cap fidelity), suite
fixture/proxy migration, coherent deployed artifacts, deterministic regression results,
account/route availability, conservative pricing preflight, separately authorized live
execution and complete evidence for every required cell. A failed or unavailable cell
is an open gate; changing scope, limits or expectations requires a deliberate decision.
There is no additional unresolved TBD-14 policy choice identified by this review.
