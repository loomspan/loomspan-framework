# PR 30.2.1 — Introduce canonical standard schemas and lossless ingestion

## Outcome

Replace legacy schema declaration/parsing structures with the agreed internal Draft 2020-12 representation and NetworkNT foundation. Implement profile checks, formats, standard semantics, annotation placement/extraction and lossless YAML/JSON handling. Reject non-JSON YAML keys/numbers and obsolete syntax.

## Scope and sequencing

**Dependencies:** PR 30.1.1.

**Expected build state:** May intentionally break downstream consumers until their owning tickets migrate them. No dual-format reader.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own canonical representation, compilation, profile checks, lossless skill-document ingestion and shared diagnostics. Define recognized annotation syntax/placement here; execution consumers belong to PRs 30.2.2–30.3.4. Do not keep legacy DTOs alive solely to make intermediate consumers compile. Preserve separate executor configuration handling.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

## Shared migration constraints

Exactly three chat integrations remain: shared OpenAI Responses for OpenAI, Meta,
OpenRouter and Ollama; native Anthropic; native Gemini. TypeSafe remains a separate
native evaluator. Retain Spring AI ChatClient/tool-loop ownership. No second adapter,
transport, agent loop, capability registry, conversation database, protocol selector,
automatic fallback or invented public API is authorized.

Canonical inputs, prompts, outputs, tool arguments, traces and diagnostics retain
content fidelity. Do not classify sensitivity, scan secrets, mask or sanitize content.
Enforce authentication, authorization and explicit access boundaries. Existing size
limits and explicit truncation are resource controls. Preserve only the existing
ExecutionJournalProjector field-name redaction exception for the derived journal and
SkillTemplate observer; do not broaden it to canonical traces, Console or invoke results.

The supported Java API is the closed allowlist of public top-level ai.loomspan.api
types in LoomspanPublicSurfaceArchitectureTest. Internal public types are implementation
details; autoconfigure signatures are not an SPI. User-visible keys/behavior require
deliberate migration. Do not expose internal/autoconfigure types in public signatures.
Run LoomspanPublicSurfaceArchitectureTest after production type changes; record it as
blocked if intentional intermediate breakage prevents execution and rerun on restoration.

The accepted standard profile is Draft 2020-12 with NetworkNT Jackson 3 (3.0.9 is the
verified starting candidate, not an irrevocable final pin). Complete contracts have
object roots; property trees support ordinary types, explicit nullable type arrays,
scalar enums/descriptions, homogeneous nested arrays, boolean openness, standard formats
and minimum/maximum/minLength/maxLength/minItems/maxItems. Ordinary inputs additionally
support typed maps and unconstrained {} leaves. Native output/planning typed maps,
general unions/composition, references, defaults, arbitrary patterns and other unapproved
features remain excluded. Internally generated false property schemas preserve binding
ownership. Exact names, standard omission/null/openness and no coercion apply. No
automatic all-required conversion, null insertion, closure, wrapping or map reshaping.
Native strict tools are deferred; retain non-strict schemas, local pre-dispatch validation
and authorization. Retain standard local checks even when a provider ignores assertions.

Deferred Java declaration exploration (Optional wrappers, polymorphic inputs,
array-shaped DTOs and delegating creators) is excluded. Do not restore removed probes
or invent their restrictions. The retained map-key, JsonUnwrapped and UUID corrections
remain owned by PR 30.2.2. Native generated attachments are excluded.

No live provider calls, model downloads, external issue/PR submissions or test-suite
implementation are authorized by this ticket. All verification here is deterministic
or local build/startup. The neighboring suite owns later deployed/live acceptance;
missing live evidence is not permission to weaken Framework requirements.

## Acceptance criteria

- [ ] Equivalent input/output/planning contracts use one internal standard compiler/validator with exact-name, no-coercion, omission/null/openness and lossless numeric semantics.
- [ ] Unsupported/legacy declarations and malformed recognized annotations fail locally with locations; standard format assertions use a documented pinned list, with unlimited evaluation depth/steps and existing resource controls preserved.
- [ ] YAML literal precision survives ingestion; non-string keys and non-finite numeric values fail before JSON conversion without altering executor configuration semantics.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Focused compiler/validator tests: exact names, omission/null/openness, integer and enum semantics, precise bounds, supported formats, unsupported declarations, canonical immutability and diagnostic fidelity.

## Recognized annotations and migration seams

The only relevant framework annotations are x-loomspan-attachment at input string
schema leaves (media_type and allowed_content_types), generated internal boolean
x-loomspan-runtime-ref-capable at Java resource string leaves, and x-loomspan-evidence
as a nonblank expression on immediate root output properties. Preserve them canonically;
strip recognized annotations only at schema-node positions in provider copies. A data
property with the same name is untouched. No arbitrary x-* stripping or authored runtime
marker feature. Detailed extraction/execution semantics are owned by later consumers.

### Attachment declaration contract and acceptance

Keep attachment metadata on the input string schema node using this exact shape:

```yaml
input_schema:
  type: object
  required: [image]
  properties:
    image:
      type: string
      description: Ticket image.
      x-loomspan-attachment:
        media_type: image
        allowed_content_types: [image/jpeg]
  additionalProperties: false
```

Retain the existing media_type and allowed_content_types names, supported values,
and validation requirements inside the annotation. It is execution metadata, not a
new JSON type, permission to access a resource, or proof of provider support.
Reject the removed type: attachment dialect and separate input_attachments path-map
proposal; do not introduce aliases or a dual-format reader.

Place nested annotations on the nested property schema. For an array of attachments,
place the annotation on its string items schema; for an array of objects, place it
under items.properties on the attachment property. Apply the same schema-node walk
to schema-valued additionalProperties for supported input maps. No wildcard paths,
duplicate metadata tree or new path-resolution engine are needed. Retain standard
requiredness, explicit nullability and surrounding object/array constraints. The
string schema describes JSON references; do not stringify direct Java Resource values
or implement their validation/materialization adapter here (PR 30.2.4 owns it).

Required declaration tests in this ticket:

- Accept equivalent single, nested, array-item, array-of-object and typed-map
  declarations; retain their metadata and surrounding standard constraints.
- Reject misplaced or malformed metadata, including missing/empty content-type lists
  and blank entries, and reject legacy annotation syntax. Keep attachments input-only.
- Preserve canonical annotations and similarly named ordinary data properties. In
  provider-copy extraction, remove only recognized annotations at actual schema nodes
  and retain the standard keywords. Consumer wire captures remain downstream checks.

PR 30.2.4 owns the additional malformed-MIME syntax correction using the media MIME
parser, Resource validation, materialization and runtime tests. PR 30.3.4 owns native
TypeSafe integration, including rejecting attachments throughout its input schema.
Those consumers must reuse these declaration rules, not introduce another parser.

### Evidence declaration contract and acceptance

Replace the old property-level evidence annotation with one nonblank string at the
same immediate root output property. For example:

```yaml
output_schema:
  type: object
  properties:
    likelyCause:
      type: string
      x-loomspan-evidence: classifyIncident and (investigateNetwork or investigateApp)
```

Required declaration tests in this ticket:

- Accept x-loomspan-evidence on immediate root output properties only. Reject it at
  the schema root, nested properties, array items and input schemas.
- Reject blank/non-string expressions and legacy evidence annotation syntax without
  aliases or coercion. Ordinary data properties named evidence or x-loomspan-evidence
  remain legitimate, including nested data properties.
- Retain original expression text and exact property names, including case and
  whitespace distinctions. Diagnostics identify the x-loomspan-evidence schema path.
- Preserve the annotation canonically; provider copies omit only the recognized
  schema annotation without changing assertions or similarly named data properties.

PR 30.3.4 owns extraction into the existing EvidenceContract, expression grammar and
namespace/direct-child resolution (including original text and 1-based token columns),
exact claim lookup and runtime evidence integration. Keep that compilation separate
from structural schema validation. Its placement tests are integration regressions
for the rules established here, not a second implementation of those rules.

Use the existing declaration generation snapshots for immutable schema ownership and
independent validation execution contexts. Meta-schema checking is distinct from
compilation. Standard bundled meta-schemas must not require external retrieval; v1
schema references are excluded. Validation operational failure is not an invalid-instance
verdict or successful partial validation. Diagnostics retain schema/instance locations
and original details through existing error/outcome mechanisms, not new public types.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Settled schema decisions and rationale

Both input and output schema declarations use the same standard JSON Schema format
and supported vocabulary. YAML remains the skill authoring format; authors do not
need separate JSON files. A schema's role determines where it is used, not a different
schema language. Input schemas validate invocation/tool arguments; output schemas
describe model results and final composed skill results. Planning uses the same
schema machinery with its own framework-owned contract.

One format does not require identical restrictions in every usage context. Share
schema vocabulary, representation, parsing, and validation machinery across invocation
inputs, tool arguments, model outputs, and planning. Document and check additional
restrictions according to how a schema is used, including provider requirements for
native generation. Report incompatible schemas clearly; do not silently rewrite them
or introduce another dialect. Exact omission/null/object-openness rules are tracked
separately in TBD-05, and native strict tool enforcement remains TBD-08.

This choice is intended to make skills familiar to developers and coding models,
reduce custom interpretation and prose-based schema instructions, and allow one
contract to drive provider requests and local structural validation. We have not
measured a reduction in retries, tokens, or failures yet. Native constraints improve
structural reliability; they do not prove factual correctness or plan usefulness.

The repository is still in development, with no customers or implementations needing
compatibility. Migrate existing declarations, examples, fixtures, authoring guidance,
and consumers together. Do not introduce a legacy mode, automatic old-dialect
translation, dual-format support, deprecated schema aliases, or a retained legacy
validator. Earlier instructions to preserve every old schema semantic are superseded
by this deliberate contract migration. Once the new contract is chosen, do not
silently reinterpret it at runtime to fit a provider.

Follow standard JSON Schema semantics for omission, null, required properties,
additional properties, and exact case-sensitive property names. Use standard nullable
types (for example `type: [string, "null"]`) instead of custom `nullable`. Authors
control these choices through their schemas; Loomspan must not impose a blanket
required-field or closed-object policy, or require explicit `additionalProperties`
when the standard defines behavior for its omission. Validate declarations as written.
Apply verified API/adapter restrictions at the relevant usage boundary and document
them; do not silently rewrite schemas, insert values, or substitute null for absence.
Any additional limitation proposed for developer/model benefit must have a concrete
justification and be discussed under the roadmap-wide simplicity principle. Inventory
actual declarations and migrate old dialect semantics explicitly.

Enable standard `format` assertions consistently in shared local validation for
input and output schemas, including planning/model contributions where declared.
Validate the author's declared format; do not infer formats from property names.
Do not coerce, normalize, reformat, or otherwise convert values automatically.
Report failures with the instance location and validation details through the existing
input rejection or bounded output-correction path. These checks establish format
validity, not business truth. A plain string remains unconstrained by `format`;
alternate representations can remain plain strings. Arbitrary `pattern` support is
not in the approved v1 baseline. Use NetworkNT's standard format validators with
assertions enabled; add no custom formats. Reject unknown or unsupported format
names at schema loading or Java declaration registration, including nested schemas,
rather than silently treating them as annotations. Document and test the supported
list against the pinned NetworkNT version; do not claim universal format coverage. Provider enforcement remains separately verified, and
local assertions still apply when a provider ignores the keyword.

Schemas are authored inline. Schema-reference support and its resolution/preflight
work are excluded from this roadmap by explicit user decision; their complexity does
not justify the current benefit. Reject unsupported schema-reference constructs at
skill loading rather than silently ignoring them. Java-generated schemas must follow
the same boundary and report unsupported recursive types rather than weaken their
contracts. This does not remove runtime resource references or capability bindings.

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

#### V1 schema scope — agreed

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

Retain internally generated `false` property schemas for framework-bound child-input
destinations. Preserve existing override checks and assembled-input validation, and
include this generated shape in adapter schema-fidelity tests. This does not expand
authored-schema scope.

Preservation evidence: `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java:56` (`properties.put(tokens.get(i), false)`) emits the false property schema; `src/test/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java:59` (`void allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden`) and `src/test/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java:72` (`void preservesTypedExtensionsArrayItemsAndFormattingWhileExcludingReservedNames`) assert its serialization and ownership protection. These local assertions are not provider acceptance evidence.

Flag existing generated unconstrained schemas or internal projection needs outside
this baseline; they do not automatically expand authoring scope.

### Contract 2

#### TBD-04: Shared schema format versus identical usage restrictions

**Status: Resolved — agreed November 10, 2026.** Use one standard schema format and
shared vocabulary, representation, parsing, and validation machinery. Allow documented
restrictions based on usage (invocation inputs, tool arguments, model outputs, or
planning), checked where the schema is used. Report incompatibility clearly without
silent schema rewriting or a second dialect. This does not settle omission/null rules
(TBD-05), the complete feature profile (TBD-06), or strict tool enforcement (TBD-08).

#### TBD-05: Omission, explicit null, and object openness

**Status: Resolved — agreed November 10, 2026.** Adopt standard JSON Schema semantics
rather than a Loomspan policy for omission, nullability, or object openness. Authors
express required properties, permitted nulls, and additional properties in one schema
format; standard behavior applies when a keyword is omitted. No blanket requirement
for all fields to be present, all objects to be closed, or `additionalProperties` to
be explicitly declared. Check verified API/adapter restrictions where applicable,
document them, and report incompatibilities without silent rewriting. The earlier
proposed portable-output restrictions were not adopted. Propose any further limit
only with a concrete compatibility, simplicity, developer, or model benefit and
discuss its tradeoffs; never impose it solely on general principle.

### Contract 3

#### TBD-06: Initial JSON Schema profile and validation behavior

**Status: Resolved — v1 policy decisions agreed.** Adopt JSON
Schema Draft 2020-12 and NetworkNT's Jackson 3 line, with 3.0.9 as the verified
starting version and the final pin confirmed against the complete framework build.
Keep the validator internal and shared across input, output, and planning contracts.
The simple v1 feature baseline, including simple bounds, is agreed in
[V1 schema scope](../phases/spring-ai-responses-migration.md#v1-schema-scope--agreed). General composition is deferred.
Typed maps are supported for ordinary inputs; native output/planning typed maps remain deferred. Other concrete uses outside the baseline must be flagged.
Standard `format` assertions are agreed; schema `default` support is deferred for v1.
Recommendation:
implement the agreed simple v1 profile using the selected NetworkNT foundation;
do not infer full JSON Schema support from library capabilities. Document local versus native
enforcement and test ignored-provider constraints locally.

Research completed November 10, 2026; subsequent user decisions close this policy item. See
[TBD-06 standard JSON Schema research](../phases/standard-json-schema-research.md) for the
repository inventory, proposed semantic migrations, provider/context feature matrix,
versioned Spring AI/SDK findings, validator comparison, and bounded verification plan.
The draft/library foundation is now agreed. The broader research feature proposal is superseded by the agreed simple v1 scope. Standard omission/null/openness semantics were
already agreed under TBD-05. The initial annotation-only `format` recommendation
was superseded by the user's decision: enable standard format assertions for both
inputs and outputs, with no automatic conversion or normalization. Apply the same
shared policy to declared formats in planning and model contributions; document
the pinned NetworkNT version's supported standard formats. Unknown or unsupported
format names are rejected at schema loading/registration, with no custom formats.
Validation evaluation depth and steps are agreed unlimited for v1. Revisit regex-engine
choices only if an agreed v1 feature needs them; arbitrary pattern support is not
part of the approved baseline.

Resource-limit investigation, November 10, 2026: see
[Validation resource-limit evidence and v1 decision](../phases/standard-json-schema-research.md#validation-resource-limit-evidence-and-v1-decision).
The isolated v1 probe records existing ingress/execution limits, NetworkNT 3.0.9
accounting semantics, 90 bounded workloads on two platforms, and illustrative
budget outcomes. The subsequent user decision is recorded below; illustrative
budgets in the evidence are not product limits.

**V1 resource-limit decision — agreed:** leave NetworkNT evaluation depth and
step limits unlimited (`maxEvaluationDepth = 0`, `maxEvaluationSteps = 0`) for each
validation. Retain existing parser, payload, and execution limits. Add no new public
configuration knobs, independent error-count limits, or validation timeouts under
this decision. Preserve the probes for future workload-driven review. Unlimited
evaluation is not a CPU/memory guarantee, and existing limits do not cover all
validation work. Failure to complete validation is an operational failure, never a
successful or partially accepted validity result.

**Root shapes — agreed for v1:** input, output, tool-argument, and planning
contracts require `type: object` at the root. Arrays and scalars remain supported
as nested properties under the approved v1 profile. Apply this rule consistently to
authored and generated contracts; reject unsupported root shapes at schema loading
or declaration registration. Do not automatically wrap or convert root values.
Broader root shapes are deferred until a concrete use case justifies them.

### Contract 4

**Skill YAML decimal precision — agreed audit correction:** preserve finite decimal
literals losslessly at skill-document YAML ingestion, before converting decoded values
to Jackson trees. Default CoreSchema numeric construction in YamlValues currently
rounds these literals to Double; later request/response parsing fixes cannot recover
the lost digits. Add focused fractional/exponent-literal tests, literal TypeSafe data
tests, and migrated minimum/maximum boundary-validation tests using exact numeric
assertions. Preserve duplicate-key, alias and non-JSON-value checks. Account for the
helper's shared executor-configuration use without changing unrelated Boot property
decoding. Existing scalar-fidelity coverage supplies BigDecimal(1.25) programmatically
and does not establish literal YAML precision. A local ingestion probe converted
9007199254740993.0 to 9007199254740992 and rounded a long fractional literal. The
ingestion loss is reproduced; incorrect enforcement of migrated bounds is a pending
migration risk, not a claim about currently supported bound validation. Implementation
and focused tests remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/YamlValues.java:14` (`setSchema(new CoreSchema())`) constructs YAML numbers before `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:412` (`yamlObjectMapper.valueToTree(decoded)`); `src/main/java/ai/loomspan/internal/autoconfigure/ExecutorYamlPropertySourceLoader.java:9` (`YamlValues`) is the shared configuration consumer to preserve.
- `src/test/java/ai/loomspan/internal/skill/TypeSafeDeclarationTests.java:93` (`void parsedYamlScalarFidelityAndResolvedCopiesRemainExact`) creates its BigDecimal programmatically; it does not test decimal YAML literals or migrated numeric bounds.

**Non-string YAML keys — agreed audit correction:** reject non-string mapping keys
in JSON-bound skill-document content before valueToTree conversion, with source/path
diagnostics. Preserve quoted numeric strings as valid property names and keep executor
configuration's separate handling intact. Add focused catalog tests for numeric and
boolean keys, nested occurrences, numeric 1 alongside quoted "1", and valid quoted-key
controls. YAML distinguishes those two keys, but a local loadSupplied probe accepted
both and retained only the integer schema associated with "1" after JSON conversion.
Existing duplicate-key tests cover identical YAML keys, not conversion collisions.
This corrects the migration's non-JSON-YAML rejection boundary before the standard
validator sees the schema. Implementation and focused tests remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/YamlValues.java:36` (`private static long check`) checks expansion, including keys, without enforcing string keys; `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:412` (`yamlObjectMapper.valueToTree(decoded)`) is the conversion boundary where numeric/string key collisions occur.
- `src/test/java/ai/loomspan/internal/skill/TypeSafeDeclarationTests.java:45` (`void strictNativeManifestParsingRejectsDuplicateAndUnknownFields`) rejects duplicate YAML keys; `src/test/java/ai/loomspan/internal/skill/TypeSafeDeclarationTests.java:693` (`void nonJsonInputsAreRejectedBeforeSerialization`) checks runtime input keys. Neither covers distinct YAML keys colliding during tree conversion.

### Contract 5

**Non-finite YAML numbers — agreed audit correction:** reject non-finite numeric
values in JSON-bound skill-document content before conversion. Add catalog tests
for .nan, .inf and -.inf, including nested schema values, while preserving explicitly
quoted "NaN" and "Infinity" as ordinary strings. Keep executor configuration's separate
handling intact. A local catalog-loading probe accepted enum: [.nan, .inf, -.inf]
and published the string enum ["NaN", "Infinity", "-Infinity"] through the legacy
List<String> conversion. Existing TypeSafe non-JSON tests reject non-finite runtime
inputs but do not establish schema YAML rejection. This implements the migration's
non-JSON-YAML boundary without changing authored values into replacements.
Implementation and focused tests remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:404` (`Object decoded = ai.loomspan.internal.serialization.YamlValues.read`) precedes valueToTree and treeToValue; `src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java:514` (`private List<String> enumValues`) is the legacy enum coercion destination.
- `src/test/java/ai/loomspan/internal/skill/TypeSafeDeclarationTests.java:693` (`void nonJsonInputsAreRejectedBeforeSerialization`) rejects runtime non-finite values, not catalog schema literals; `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:922` (`void loadsYamlSkillInputSchemaWhenPresent`) is the catalog input-schema fixture to extend with nested non-finite values and quoted-string controls.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

#### Declarations and current validation

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

#### Existing limits and coverage

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

#### NetworkNT 3.0.9 control semantics

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

## Reproduction anchors

Historical validator/resource-limit reproduction, using Java 21 and Maven:

```powershell
./docs/maintainers/probes/tbd-06/run-resource-limits.ps1
./docs/maintainers/probes/tbd-06/run-resource-limits.ps1 -BootVersion 4.1.0
```

Ninety workload rows and four new plus seven regression tests per platform are retained.
Illustrative depth/step thresholds are not product settings; v1 remains unlimited.
Microprobe timings are not production SLOs, worst-case proofs or concurrency acceptance.

## Pipeline notes

- Destructive updates are encouraged: this repository is in development with no
  deployments to support. Remove superseded implementations in place; do not add
  legacy readers, aliases, dormant paths, compatibility shims or fallback solely to
  keep intermediate commits building. Respect the supported public API boundary above.
- The developer commits between tickets without syncing. The stated intermediate
  build failures are authorized; identify each affected consumer/test and restoration
  owner. Do not mark blocked tests passed, remove assertions or skip required checks
  into success. Final integrated acceptance requires green checks.
- Preserve unrelated worktree changes and retained probes. Resource stream closure
  in SkillMethodBeanPostProcessor is already repaired, with success/failure/nested
  tests; preserve it rather than treating it as missing migration implementation.
- Source anchors and retained reports below are historical navigation/evidence, not
  fresh passing-test claims. Verify hints against the current checkout. Before claiming
  a new defect, trace callers, validation, execution and tests against the accepted
  requirement. Routine implementation choices are autonomous; broader product or
  architecture departures require a concrete evidence-backed question.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket changes or provides outstanding assurance for a cross-cutting migration involving serialized contracts, external protocols or execution lifecycle behavior. Settled intent does not eliminate integration research, test planning or independent review.
- **Reassessment triggers:** A materially changed scope or checkout should be reassessed under the shared protocol; this recommendation does not waive any required review or verification.

## Reference navigation

- [Agreed overview](../phases/migration-ticket-overview.md)
- [Coverage ledger](../phases/migration-ticket-coverage.md)
- [Migration roadmap](../phases/spring-ai-responses-migration.md)
- [Historical schema research](../phases/standard-json-schema-research.md)
- [TBD-06 reproduction and retained evidence](../../../docs/maintainers/probes/tbd-06/README.md)
