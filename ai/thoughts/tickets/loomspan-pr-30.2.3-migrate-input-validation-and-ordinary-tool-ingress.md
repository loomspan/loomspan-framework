# PR 30.2.3 — Migrate input validation and ordinary tool ingress

## Outcome

Replace custom structural validation. Implement ordinary JSON compatibility, open-property checks, runtime map-key rejection and marked Java runtime-value adaptation. Preserve validation/resolution/binding ordering and generic-reference behavior. Fix callback decimal precision, whole-document parsing and object-root enforcement.

## Scope and sequencing

**Dependencies:** PR 30.2.1 and PR 30.2.2.

**Expected build state:** Input and callback paths should compile and pass focused checks; output/planning consumers may still be broken.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own input-validation ingress and callback decoding. Reuse generation from PR 30.2.2. PR 30.2.4 owns attachment-only materialization; PR 30.2.5 owns binding projection/assembly migration. Preserve existing ordering at their seams. The explicit runtime adapter here is not an attachment descriptor adapter.

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

- [ ] Invalid ordinary JSON values, non-string keys and contract violations fail before invocation; permitted null/absence and exact values remain unchanged across authored/generated inputs.
- [ ] Callback arguments must be exactly one JSON object with lossless decimals; invalid roots and trailing content invoke zero target side effects, while whitespace and valid object controls pass.
- [ ] Marked Java runtime values use separate leaf checks followed by a private structural view; no placeholders or weakened schemas reach provider requests or diagnostics.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Public map invocation and actual callback/tool-manager tests: unchanged inputs, precise decimals, invalid roots/suffixes, no side effects on rejection, authorization, marked/unmarked references, runtime leaves and parent/sibling constraints.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

**Ordinary tool-callback decimal precision — agreed audit correction:** decode raw
tool-argument JSON losslessly in the existing internal callback adapter before invoking
BoundCapability. Preserve tool definitions, result conversion, authorization, shared
input validation and Spring AI tool-loop ownership. Do not globally reconfigure Spring
AI's mapper or introduce a public callback API. Fixing step-action parsing does not
fix this separate ordinary-tool ingress.

Add focused callback invocation tests with long fractional/exponent-form decimals in
nested objects and arrays. Assert exact values delivered to BoundCapability and, through
the actual tool pipeline, Java execution. Add migrated numeric-boundary cases proving
an originally out-of-range value cannot become valid through rounding and invokes no
Java side effect. Retain schema/name fidelity, result conversion, authorization and
invalid-input regressions. A local probe through the actual Loomspan adapter delivered
0.12345678901234568 as Double for JSON 0.12345678901234567890123456789. This reproduces
callback-level loss, not full migrated-pipeline or live-provider behavior. Implementation
and focused acceptance remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java:22`
  (`adapt`) builds FunctionToolCallback with Map<String,Object> input at line 26;
  its lambda forwards already-decoded arguments to BoundCapability.
- `src/main/java/ai/loomspan/internal/runtime/tool/DefaultCapabilityInvoker.java:172`
  routes the decoded arguments to execution;
  `src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:62`
  (`executeInput`) validates them afterward, too late to recover lost digits.
- `src/test/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapterTest.java:18`
  (`adaptsUnconstrainedSchemaVerbatimToSpringAiToolCallback`) checks schema metadata
  without invoking the callback. `src/test/java/ai/loomspan/internal/core/CapabilityExecutionRouterTest.java:30`
  (`assembledBoundValuesFailBeforeDispatchWithoutCoercionAndStillRequireAuthorization`)
  supplies validation/authorization controls, not raw callback decimal coverage.
- Inspected Spring AI sources for both 2.0.0 and 2.1.0-RC2:
  spring-ai-model's `org/springframework/ai/tool/function/FunctionToolCallback.java:53`
  creates the default JsonHelper and line 108 decodes toolInput. spring-ai-commons'
  `org/springframework/ai/util/JsonHelper.java:47` selects JacksonUtils' default mapper;
  `org/springframework/ai/util/JacksonUtils.java:41` builds it without lossless decimal
  handling in RC2. Sources are in the corresponding local Maven source artifacts.
  This is source-inspected target behavior, not a new RC2 pipeline test result.

### Contract 2

#### TBD-08: Native strict tool-argument enforcement

**Status: Resolved — native strict tools deferred beyond v1.** Native final-output
and planning formatting remain in scope. Sharing standard schema syntax does not
require native strict generation of tool arguments.

#### V1 requirements

- Send the standard tool-input schema through the existing driver and tool loop.
  Use only the context-specific, evidence-backed request mappings allowed by TBD-07;
  deferring strictness is not permission to strip schemas or accept adapter field loss.
- Keep native tool strictness disabled. Explicitly send `strict: false` where the
  integration exposes that setting and requires an explicit override; otherwise use
  its supported non-strict tool path. Verify actual serialized requests and defaults.
  Do not infer a portable strict flag exists in all three APIs. If an integration
  cannot honor this policy, report the concrete incompatibility for discussion.
- Validate model-supplied arguments against the canonical input contract at the
  existing input-validation boundary before dispatching execution. Preserve existing
  binding/resource-materialization order; changes to those semantics belong to TBD-13.
  Invalid arguments must never invoke the target tool or cause its execution side effects.
- Retain canonical tool authorization and availability checks independently of schema
  validation. A schema-valid request is not permission to invoke an unauthorized tool.
- Preserve Spring AI ChatClient/tool-loop ownership. Do not add a second tool loop,
  speculative strict-tool capability discovery, or a separate retry mechanism.
- Add no v1 strict-tool configuration knob, automatic opt-in, provider-specific
  strictness fallback, or incidental enablement through SDK helpers or defaults.
- Preserve the existing invalid-argument reporting path. Any change to correction
  history, retry ownership, or repeated tool execution must be decided under TBD-11;
  this decision does not authorize automatic re-execution.
- Document the tradeoff: models can still generate invalid arguments. Local validation
  protects execution correctness, while native strictness may later reduce failures.
  Revisit after the output migration using concrete application needs and provider tests.

#### Mandatory ticket handoff and acceptance

Future tickets must explicitly include these requirements, the relevant driver/API
settings, and the tests below. Do not hand the implementing LLM a generic instruction
to "support structured tools" or let it choose strictness or correction behavior.
Unverified driver behavior must be stated as evidence work, not guessed.

Verify actual requests for each integration carry the tool schema without enabling
native strictness. Prove valid authorized arguments reach execution, while invalid
arguments never invoke the target (including a side-effect sentinel). Verify that
schema-valid unauthorized/unavailable tool calls remain rejected. Check the existing
tool loop handles validation feedback without introducing another loop or retries.
Cover affected binding/resource-input paths with their existing semantics; any gap
requiring a new policy must be flagged under the owning TBD. Retain local validation
even if a provider happens to constrain arguments without an explicit strict flag.

**Ordinary tool-callback trailing content — agreed audit correction:** require the
entire argument string to contain one JSON object, permitting trailing whitespace but
rejecting additional JSON values or garbage before BoundCapability invocation. Enable
FAIL_ON_TRAILING_TOKENS in the local callback reader already required for decimal
preservation. Keep existing error handling, authorization, input validation and loop
ownership; do not introduce a new retry or batch-rollback policy.

Add focused callback and actual tool-manager tests for a valid object followed by an
object, scalar or malformed suffix, asserting zero target-handler calls. Retain valid
object and trailing-whitespace controls and existing planner-control rejection tests.
A local probe through CanonicalToolCallingManager, the actual adapter and the existing
input validator invoked its handler for both {"value":1} {"value":2} and
{"value":1} trailing garbage. Only the parsed prefix reached validation. This reproduces
local callback execution from invalid complete argument text; migrated full-pipeline
and live-provider acceptance remain pending. Implementation and focused tests are pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java:22`
  delegates argument decoding to FunctionToolCallback; the lambda invokes BoundCapability
  with the decoded map. `src/main/java/ai/loomspan/internal/springai/CanonicalToolCallingManager.java:60`
  retains raw call.arguments() in its execution copy, without a whole-document parse.
- Inspected Spring AI 2.0.0/2.1.0-RC2 sources: FunctionToolCallback.java:108 calls
  JsonHelper.fromJson; spring-ai-commons' `org/springframework/ai/util/JacksonUtils.java:42`
  explicitly disables FAIL_ON_TRAILING_TOKENS. The corresponding local Maven source
  artifacts retain this evidence; RC2 behavior was inspected, not newly run end to end.
- `src/test/java/ai/loomspan/internal/springai/CanonicalToolCallingManagerTest.java:24`
  (`rejectsMalformedBoundControlArguments`) covers trailing JSON for planner inability,
  whose strict reader is at `src/main/java/ai/loomspan/internal/springai/PlannerInabilityTool.java:38`.
  It does not exercise ordinary callback decoding.
- `src/test/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapterTest.java:18`
  (`adaptsUnconstrainedSchemaVerbatimToSpringAiToolCallback`) checks metadata without
  calling the callback. Extend the ordinary invocation coverage rather than assuming
  the planner-control tests establish this boundary.

**Ordinary tool-callback root shape — agreed audit correction:** require an actual
JSON object at the ordinary callback decoding boundary, before conversion to a map or
BoundCapability invocation. JSON null and scalar values must not become empty argument
objects. Preserve {} for contracts permitting empty arguments and permitted null values
inside objects. Keep this check local to model callback ingress; do not change public
invocation overloads or unrelated internal null-map handling.

Add callback and actual tool-manager tests rejecting null, strings (including the empty
string), arrays, numbers and booleans with zero target-handler calls. Retain valid {},
ordinary object and permitted nested-null controls, with normal contract validation.
A local probe through the real manager/adapter and input validator delivered {} to its
handler for {}, null and "", invoking once for each; [] and 1 were rejected without
invocation. This reproduces callback-level conversion inconsistent with the agreed
object-root requirement. Implementation, full migrated-pipeline verification and live
acceptance remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java:22`
  (`adapt`) delegates to Spring AI's Map decoder, then invokes BoundCapability.
  Inspected spring-ai-commons 2.0.0/2.1.0-RC2 JacksonUtils enables
  ACCEPT_EMPTY_STRING_AS_NULL_OBJECT; JSON null also decodes to null.
- `src/main/java/ai/loomspan/internal/runtime/tool/BoundCapability.java:56`
  (`invokeAssigned`) replaces null arguments with Map.of() before calling its handler;
  subsequent map validation cannot recover the original JSON root kind.
- `src/test/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapterTest.java:18`
  checks schema metadata without invoking the callback.
  `src/test/java/ai/loomspan/internal/springai/CanonicalToolCallingManagerTest.java:24`
  (`rejectsMalformedBoundControlArguments`) rejects non-object roots for the special
  planner control, not ordinary callback ingress. Extend ordinary invocation coverage;
  these inspected tests do not establish its root-shape enforcement.

### Contract 3

#### Unconstrained Object input values — agreed November 11, 2026

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

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:99` (`else if (raw == Object.class)`) already emits an empty schema; `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:50` (`javaObjectGeneratesUnconstrainedProviderSchema`) tests non-null JSON values, while `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:154` (`publishesDescriptionsAndBindsUnconstrainedReflectedInputs`) exercises descriptions and binding. These do not establish the new required-present-null behavior or live route acceptance.

**Open-property JSON compatibility — acceptance detail agreed November 12, 2026:**
Apply the existing ordinary-input JSON-compatibility requirement to undeclared properties
under explicit non-generic contracts with additionalProperties: true or omitted, as well
as schema-valued additionalProperties: {}. Recursively reject arbitrary POJOs, Resources,
InputStreams, byte arrays and non-finite numbers before conversion/invocation; do not
read streams or serialize Java internals to make these values acceptable. Preserve JSON
null, exact numeric values, literal ref:// strings, separate generic-contract behavior
and explicitly marked runtime-resource leaves. This refines existing policy rather
than introducing a new restriction or retaining the custom validator in migration.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-open-properties/README.md)
reproduces twelve direct/nested invalid-value cases: true and omitted openness accept
them, while an explicit empty value schema rejects them. An isolated candidate applies
the existing recursive JSON check at open branches for non-generic contracts; all 17
existing validator test methods pass. Resource/stream sentinels are not consumed.
Add equivalent generated/authored acceptance cases and assert rejection before Java
invocation with useful input paths and unchanged input. Public invocation, migrated
NetworkNT integration and live-provider capability were not established by this local
validator probe; production implementation and full acceptance remain pending.

Evidence anchors (audit-time lines and symbols):

- `src/main/java/ai/loomspan/internal/runtime/input/SkillInputValidator.java:165`
  (`validateObject`) validates schema-valued extras; `:185` (`validateOpenBranch`)
  otherwise copies arbitrary values, unlike `:75` (`validateUnconstrained`).
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:165`
  (`prepareMap`) and `src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:62`
  call input validation before execution; the probe does not exercise these callers.
- `src/test/java/ai/loomspan/internal/runtime/input/SkillInputValidatorTest.java:472`
  (`rejectsNonJsonValuesAndUnknownSchemaKindsExplicitly`) covers explicit unconstrained
  values, not undeclared open properties. The runtime-map-key correction addresses
  key identity rather than this value-validation branch.

### Contract 4

**Runtime map keys — agreed audit correction:** reject non-string keys in runtime
object/map inputs before normalization or JSON-tree conversion. Reuse the existing
string-key check rather than stringifying keys; do not allow numeric 1 and string "1"
to collapse into one property. This is separate from skill-document YAML ingestion.
Preserve valid string-keyed maps and existing runtime-leaf adapters; do not redesign
the public object/DTO invocation overload as part of this correction.

Add focused typed-map and public map-invocation tests for numeric/boolean keys, nested
occurrences (including maps in arrays), and numeric 1 alongside string "1". Assert
rejection before execution, useful input-path diagnostics and unchanged original input.
Retain valid string-key controls and existing unconstrained-value rejection. A local
validator probe accepted a typed string-valued map with both keys and returned only
the string-key entry; the same data under an unconstrained leaf was rejected. This
reproduces validator-level loss, not a full public-invocation test. Implementation and
focused invocation verification remain pending; no live capability is inferred.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/runtime/input/SkillInputValidator.java:157`
  (`validateObject`) applies String.valueOf to keys before inserting normalized values;
  its `validateUnconstrained` branch at line 110 already checks for string keys.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:165`
  (`prepareMap`) validates public map inputs through this path before admission.
- `src/test/java/ai/loomspan/internal/runtime/input/SkillInputValidatorTest.java:316`
  (`validatesMapLikeAdditionalPropertiesAgainstNestedSchema`) checks invalid values
  using string keys; it does not cover key conversion or collisions. Extend this
  coverage and public map-invocation tests rather than treating YAML-key tests as
  proof of runtime behavior.

### Contract 5

#### Non-attachment runtime-reference metadata — agreed November 11, 2026

Retain the existing boolean `x-loomspan-runtime-ref-capable` marker as internal
Java-input metadata and preserve its existing conversion behavior. The Java
postprocessor generates string schemas with this marker for Resource, InputStream
and byte[] parameters, including nested properties and collection items. Under
explicit contracts, RefResolver uses marked nodes to select reference resolution;
ordinary unmarked strings remain strings. Preserve the separate existing generic
input resolution behavior. This agreement does not add an authored YAML feature or
approve arbitrary framework annotations.

Keep the marker in canonical generated contracts and internal metadata. Remove only
this recognized annotation from provider schema copies, walking actual schema nodes
and retaining their string type, description and standard assertions. A data property
named `x-loomspan-runtime-ref-capable` must remain a data property.

For example, the canonical generated leaf remains
`{"type":"string","description":"Provide a ref:// URI for the resource content.","x-loomspan-runtime-ref-capable":true}`;
its provider copy contains the same type and description without the annotation.
The caller's reference string is validated before resolution and subsequently passed
through existing authorized resolution and Java conversion. Preserve conversion and
stream cleanup, including existing direct runtime-object support.

Rationale: SkillInputValidator currently accepts Resource, InputStream and byte[]
at marked string leaves. Replacing it with ordinary JSON string validation would
break those inputs; resolving strings first would likewise present non-JSON objects
to the validator. Preserve this Java invocation concern separately from attachment
materialization. Do not automatically apply attachment descriptors, MIME checks or
repeatability requirements. The separately agreed direct Java-value validation
mechanism follows below.

Mandatory ticket acceptance tests: marked versus unmarked reference strings;
string validation before resolution; direct Resource, InputStream and byte[] inputs;
nested properties and array items; existing conversion success/failure and stream
cleanup; authorization and generic-resolution regressions; canonical marker retention;
provider-copy annotation removal with standard assertions, descriptions and similarly
named data properties intact. Preserve original values and canonical schemas. Existing
source/test inspection is evidence of behavior, not a new test-run acceptance claim.

### Contract 6

#### Direct Java runtime-value validation — agreed November 11, 2026

Use a narrow internal adapter for non-attachment marked Java-input leaves. First
validate each actual JSON value at a marked leaf against its canonical leaf schema;
for a direct Resource, InputStream or byte[], retain the existing accepted-type check.
Do not read resource contents or consume streams during validation. Java binding
continues to own conversion, conversion failures and stream cleanup; accepting a
runtime type at this stage does not promise every target Java type can bind it.

Then validate the surrounding input with NetworkNT using a private JSON view and
private schema copy. Replace accepted direct runtime objects with null in this view,
and replace marked leaf schemas with {} only after their separate checks succeed.
Check every occurrence, including all array items, before weakening a shared items
schema. Actual string values retain canonical standard validation before reference
resolution. Original nulls must pass their canonical leaf schema; they cannot acquire
permission from the private placeholder or empty leaf schema. Missing fields remain
missing. Preserve all properties, positions and parent constraints, including required,
additionalProperties and array bounds, and validate ordinary siblings normally.

Rationale: these objects already have a Java invocation path but no attachment
descriptor. This small internal adaptation preserves that behavior while the shared
validator checks the surrounding standard contract. Null is only an internal structural
placeholder after runtime-type acceptance, never a replacement reference or a claim
that the caller supplied null. Do not serialize Java object internals, apply attachment
materialization rules, or broaden the adapter to ordinary/unmarked fields.

Pass original objects to binding and leave canonical input/schema unchanged. Neither
private view nor private schema may be sent to providers, returned as rewritten input,
or exposed as diagnostic evidence. Diagnostics retain original paths and content;
do not report the internal placeholder as the caller's value.

Mandatory ticket acceptance tests:

- Direct Resource, InputStream and byte[] values at marked leaves pass the existing
  runtime-type gate and reach Java binding unchanged; incompatible target conversions
  still fail through existing binding behavior, with existing stream cleanup.
- Validation does not open/read Resources, consume InputStreams or stringify object
  internals. Unmarked fields receive no runtime-object exception.
- Actual strings undergo canonical assertions before resolution; invalid strings fail
  without resolution or invocation. Mixed arrays of strings and runtime objects check
  every element, including a later invalid string after an accepted runtime object.
- Missing required fields fail; optional absence stays absent; actual permitted null
  passes and prohibited null fails. Test required nullable presence and nested/array
  nulls independently of runtime placeholders. Generated nullability policy is separate.
- Nested objects/arrays retain property names and array positions. Invalid ordinary
  siblings, undeclared properties under closed objects and array-bound violations fail
  even when valid direct objects are present. Failed validation prevents invocation.
- Original input/object identity and canonical schemas remain unchanged. Provider
  requests and diagnostic evidence contain no private placeholders or weakened schemas;
  failures refer to original paths/values. Retain the preceding marker/conversion tests.

This is a design agreement, not implementation or passing-test evidence. Recursive input types are settled below; unconstrained Object input support is agreed below;
declaration authority is preserved in the accepted developer model below.

## Reproduction anchors

Historical open-property reproduction:

```powershell
./docs/maintainers/probes/tbd-13-open-properties/run.ps1
```

Twelve invalid-value cases and 17 existing validator methods were retained historically;
the candidate did not establish public invocation or migrated NetworkNT integration.
Public/callback dispatch evidence required by this ticket must exercise those seams.

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
