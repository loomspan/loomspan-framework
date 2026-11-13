# PR 30.3.1 — Apply native contracts to ordinary skill output

## Outcome

Carry canonical/projected output contracts through internal requests and all three provider mappings. Implement evidence-backed omission rules, local lossless validation, standard prompt openness and full composed-output acceptance. Preserve ordinary text mode and existing correction budgets.

## Scope and sequencing

**Dependencies:** Phase 1, PR 30.2.1 and PR 30.2.5.

**Expected build state:** Ordinary output path should compile and pass. Planning/step and remaining metadata consumers may still be broken.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own ordinary output schema propagation, request mappings and validation/composition. The broad migration checklist below is context: complete this boundary, not other tickets. PR 30.3.2 owns planner/step consumers; PR 30.3.3 owns completed-history/tool-free repair enforcement; PR 30.3.4 owns evidence metadata extraction. Reuse adapter fixes from Phase 1.

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

- [ ] Every ordinary schema-bearing generation and correction carries the correct canonical or projected contract through native formatting on three adapters; text-only calls remain text-only.
- [ ] Only evidenced allowed assertion omissions affect request copies; originals, similarly named data properties and local enforcement remain intact.
- [ ] Precise invalid candidate values enter existing bounded correction and cannot become accepted through rounding; composition preserves untouched provider parts and keeps raw evidence distinct.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Actual initial and correction requests on all three adapters; projected versus canonical contracts; local enforcement of omitted assertions; schema isolation; precision; unsupported-contract failures without downgrade or retry-based weakening.

## Request-copy mapping entries and evidence boundaries

The following bounded entries carry the November 10 provider research into this ticket.
Verify their applicability against the selected API/adapter before installing a rule;
unverified contexts remain explicit evidence work, not permission to guess. Provider
rejection is distinct from SDK field loss. No live calls are authorized by this work.

| Driver/context | Exact assertion entry | Request-copy action and evidence limit |
| --- | --- | --- |
| anthropic native output/planning | minimum, maximum, minLength, maxLength, maxItems; minItems other than 0 or 1 | The retained Anthropic structured-output documentation reports rejection. Omit only these assertions in that context after confirming the selected API contract; preserve all locally. Keep minItems 0/1. This does not authorize omission from non-strict tool schemas. |
| anthropic non-strict tools | All approved assertions | Preserve unchanged. The historical strict-tool subset does not establish non-strict rejection; no tool omission entry is established. |
| openai shared output/planning/tools | All approved assertions | Preserve unchanged unless rejection evidence supports one shared permitted entry. No route-specific omission table is established; do not infer one from model names or URLs. |
| gemini generateContent output/planning/tools | All approved assertions | Preserve unchanged. Guide claims of ignored length/count constraints are not rejection and the Interactions guide does not establish generateContent enforcement. Repair SDK loss rather than omit. |
| all drivers, format | Exact named standard format only | No blanket format omission. Retained provider lists include date-time/time/date/duration/email/hostname/ipv4/ipv6/uuid for OpenAI and additionally uri for Anthropic; absence from a list alone is not a verified rejection entry. Keep unknown applicability explicit and obtain exact rejection evidence before any omission. |

For each installed entry retain driver, usage context, exact keyword/value or format,
source/API version, rejection evidence and local/wire tests. If applicability cannot
be established, keep faithful transmission and explicit provider-rejection failure;
do not claim a completed omission rule. A requirement for a forbidden structural
rewrite or route-specific exception is an evidence-backed escalation, not autonomous
scope expansion. Root openness/all-required provider incompatibilities must fail
clearly rather than strengthening canonical contracts.

## Composition and ordinary parsing seams

OutputSchemaValidator.validate currently parses through the supplied schema mapper;
use a local lossless reader before precise comparisons. Its ordinary default mapper
already rejects trailing JSON values/garbage and permits trailing whitespace; do not
misapply the separate callback trailing-content finding here. OutputSchemaCallAdvisor
currently rebuilds composed AssistantMessage from text/tools/media; preserve untouched
ordered parts/attributes while deliberately replacing application-facing text. Preserve
raw provider evidence for PR 30.3.3 instead of reconstructing history from composed text.

## Deterministic option fixtures for later acceptance

Use the agreed selected routes as offline fixture identities: direct gpt-6.1-terra,
OpenRouter openai/gpt-6.1-terra restricted to standard endpoint tag openai with fallback
disabled and require_parameters enabled, Meta muse-spark-1.3-contributor (all explicit
medium); Ollama qwen3.5:4b-q4_K_M with enabled boolean thinking, not a fabricated medium;
Anthropic claude-sonnet-5-5 native output effort medium; Gemini gemini-3.8-flash native
thinking level MEDIUM. These are accepted fixtures, not fresh availability claims.
The inspected Ollama baseline was 0.40.16 with digest
2a654d98e6fba55d452b7043684e9b57a947e393bbffa62485a7aac05ee4eefd.

Capture unchanged total generation caps (reasoning plus visible output): 1024 for a
small structured result, 4096 for tool-chain/planning requests and 2048 for attachment
requests. Cover initial, continuation, planner and model-override option combination
as those consumers become available. Phase 1 proves adapter/factory behavior; PRs
30.3.1/30.3.2 prove migrated consumer wiring; PR 30.4.3 reconciles the full matrix.
Preserve legacy-model behavior. Unsupported options fail without paid sends, implicit
fallback, increased limits or invented settings. Routing support must use existing
extension facilities, not endpoint/model guessing or another provider abstraction.

The later suite uses a 180-second physical request boundary, 210-second proxy boundary,
900-second mission boundary and 960-second runner deadline. Preserve configurable
timeout semantics; do not mistake socket inactivity for a total elapsed deadline.
Suite overlays, reservation ledgers and runner changes belong to the suite, not here.
Its total ceiling is USD 5, 81 physical requests (69 cloud), zero automatic transport
retries/schema repairs in the initial live batch, and separate paid-run authorization.
These are not new production defaults. No sends are authorized here.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Shared baseline and documented provider differences

Define a useful common baseline verified across `openai`, `anthropic`, and `gemini`.
Do not make the lowest common denominator an immutable ceiling. A standard schema
feature may be supported when it benefits the framework even if only two integrations
enforce it natively and the third ignores it. This is explicit product permission
to consider such differences, not evidence that any particular feature works today.

For each supported feature, document its semantics, local validation, provider/model
or route coverage, SDK mapping, and whether it is enforced, ignored, rejected, or
unverified. Distinguish annotations such as descriptions from validation constraints.
Where a provider ignores a constraint, retain it in the canonical contract and enforce
it locally before accepting results; bounded corrections may still be necessary.
Document the resulting difference in generation guarantees and possible retries.
Never claim uniform native enforcement merely because all providers accept a request.

If a provider rejects a keyword, that is different from ignoring it. Prefer faithful
pass-through. Any necessary omission from the provider request must be a narrow,
explicitly documented mapping with tests; it must not remove the constraint from
local validation or change the application-visible contract. Flag mappings that
change meaning, cannot be validated locally, or require disproportionate machinery
for discussion. Do not build a speculative capability registry, automatic provider
switching, or a general schema-rewriting framework. Model/route differences within
the shared OpenAI driver also need evidence, especially OpenRouter and Ollama.

The agreed Ollama consolidation boundary takes precedence over permission for narrow
provider mappings: Ollama uses the shared `openai` implementation with no
Ollama-specific runtime branches, including schema mappings or provider dispatch.
Document limitations found during validation. If compatibility requires special
implementation code, flag the concrete issue for discussion before adding it;
do not pre-authorize hypothetical exceptions or hide a native adapter in shared code.

Documented keyword-level differences do not authorize silent downgrade of an entire
schema request to `json_object`, prompt-only output, or another protocol. Unsupported
contracts should fail with useful diagnostics rather than retry unchanged forever.

#### Implementation and removal checklist

- [ ] Inventory input/output declarations and all schema consumers before conversion.
  Use the agreed Draft 2020-12 and NetworkNT Jackson 3 foundation, with 3.0.9 as the
  verified starting version. Document the supported baseline and justified differences;
  confirm the exact dependency pin against the complete framework platform. Feature
  policy decisions are resolved under TBD-06; isolated probe success is not framework
  startup or full draft conformance acceptance.
- [ ] Establish one internal schema representation for inputs, outputs, and planning,
  with shared parsing, schema validation, structural value validation, and diagnostics.
  Use the authored contract as the source of truth. Replace custom schema definitions
  and structural-validation code rather than layering a second system over them.
  Keep necessary projection/composition and semantic checks. Avoid a new public API/SPI.
  Keep provider-specific serialization at the existing integration
  boundary rather than spreading it through planners, advisors, or skill execution.
- [ ] Carry the schema explicitly through internal model requests and option mapping,
  including initial calls, tool continuations, and correction attempts. Use one path
  for planning and schema-bearing outputs, with per-call contracts that cannot leak
  across skills, models, or concurrent invocations.
- [ ] Map the contract to OpenAI Responses `text.format` with `type: json_schema`
  and strict adherence where supported, Anthropic's native
  `output_config.format` JSON-schema output, and Gemini's native JSON output/schema
  options. Verify the exact options and wire fields in the selected Spring AI/SDK
  versions; provider documentation alone does not establish adapter support.
- [ ] Apply the schema to each schema-bearing skill generation and correction
  request. For output bindings, send the projected model-contribution schema;
  framework-owned destinations must not become model-generated fields. Compose the
  result and validate the complete output against the original contract.
- [ ] Define the planning response schema from the existing plan contract and apply
  it to initial planning and correction calls. Planning must not inherit the skill's
  final-output schema. Retain validation of capability references, dependencies,
  task constraints, evidence coverage, and output-binding producers; structural
  correctness does not establish a valid or useful plan.
- [ ] Migrate old required/optional/null/additional-property semantics explicitly
  into the newly selected standard contract, updating declarations and tests together.
  Thereafter preserve that contract through provider mappings and local validation,
  including documented constraints enforced locally when ignored by a provider.
  Do not silently require extra properties or substitute null for omission at runtime.
- [ ] Do not automatically downgrade `json_schema` to `json_object` or prompt-only
  output. JSON-object mode only promises JSON syntax and is not a substitute for
  schema adherence. Leave ordinary text calls in text mode. Do not add JSON-object
  mode unless a concrete schema-free JSON use case requires it.
- [ ] Keep shared parsing, local schema validation, composition validation, and
  bounded correction handling. Distinguish provider rejection, refusal, incomplete
  output, malformed output, and semantic validation failures. Unsupported schemas
  are not repaired by repeatedly retrying the same provider request. Preserve the
  history/retry ownership decisions elsewhere in this document.
- [ ] Review duplicated schema instructions and provider-specific output handling
  for simplification once native enforcement is verified. Retain task instructions,
  schema descriptions, and semantic checks; remove only proven redundant formatting
  machinery. Do not retain parallel legacy implementations solely for compatibility.
- [ ] Remove the old input/output dialect parsers, custom schema model structures,
  handwritten structural validators superseded by the shared validator, legacy
  keyword handling, and duplicated contract renderers. Audit `YamlSkillManifest`,
  `SkillInputContractResolver`, `OutputSchemaValidator`, `OutputSchemaPromptAugmentor`,
  schema metadata generation, and their consumers as starting points, not an exhaustive
  deletion list. Preserve authorization, runtime reference/attachment behavior,
  bindings, and diagnostic fidelity in their appropriate framework layers; these are
  not automatically expressible as JSON Schema. Keep framework execution metadata
  separate from standard structural constraints and document any needed annotations.
- [ ] Update skill loading, invocation validation, child/tool argument schemas,
  output binding projection/composition, planning, Console editing/display,
  README/examples, skill-authoring guidance, and relevant tests to the same format.
  Audit schema-bearing TypeSafe declarations as consumers of the shared authoring
  format without changing TypeSafe's separate native evaluation protocol.
- [ ] Simplify planning's model-output parser to the selected JSON contract. Remove
  YAML fallback, Markdown-fence repair, and enum/status aliases when superseded by
  native schema enforcement. Keep framework-generated plan identity and semantic
  plan checks. Skill files remaining YAML does not require model plan responses to
  support YAML.
- [ ] Record removal evidence and evaluate representative planning/output cases for
  structural failures, corrections, latency, token usage, and task success. Do not
  infer better reasoning from cleaner JSON or preserve old runtime code for benchmarks.

Completion: inputs and outputs share one standard schema format; the old dialect
and implementation are gone; schema-bearing skill calls and planning use native
provider formatting through all three integrations with shared local acceptance
checks. The supported profile and provider differences are documented and tested,
including local enforcement of constraints a provider ignores. No dual-format path
or legacy runtime compatibility layer remains.
This does not enable strict tool-argument schemas automatically; function-tool
contracts and final-output contracts remain separate concerns.

Provider references: [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs),
[Anthropic structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs),
and [Gemini structured outputs](https://aistudio.google.com/docs/structured-output).
These establish provider features, not completed Loomspan integration or uniform
schema support across every model and route.

### Contract 2

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

**Output prompt openness — agreed audit correction:** migrate the existing output
contract renderer to standard object openness: omitted additionalProperties means
open, as does explicit true, at root and nested objects. Remove the blanket instruction
that unknown fields require explicit permission. Keep prompt guidance consistent with
the canonical or binding-projected contract supplied to the renderer; do not change
ownership projection, provider adaptation or correction-history policy.

Add omitted/true/false root and nested-object renderer cases, plus focused ordinary
advisor and step-final prompt assertions. Explicit false must still describe a closed
object; omission must never be presented as false. Preserve existing descriptions,
requiredness and final-phase-only step guidance. A local renderer probe printed false
for omission and false, and true for explicit true at both object depths. This is a
pending migration compatibility correction, not a defect under legacy closed-by-default
output semantics. Implementation and focused acceptance remain pending; no live model
behavior was tested.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaPromptAugmentor.java:62`
  (`renderNode`) interprets only explicit true as open; line 21 contains the blanket
  explicit-permission instruction.
- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:97`
  (`adviseCall`) injects this guidance into ordinary requests, including projected
  output contracts. `src/main/java/ai/loomspan/internal/runtime/step/StepPromptBuilder.java:218`
  (`appendOutputSchemaGuidance`) uses the same renderer for final synthesis.
- `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaPromptAugmentorTest.java:16`
  (`augmentsPromptWithCompleteRecursiveEffectiveContractInDeclaredOrder`) covers
  explicit true/false. `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java:422`
  (`firstRequestIncludesRecursiveTravelContractBeforeValidation`) checks ordinary
  prompt delivery; `src/test/java/ai/loomspan/internal/runtime/step/StepPromptBuilderTest.java:688`
  (`buildStepPromptShowsOutputSchemaInFinalResponseMode`) checks step-final guidance.
  These inspected assertions do not establish standard omission behavior.

### Contract 3

**Ordinary output-validation decimal precision — agreed audit correction:** parse
model output decimals losslessly at the ordinary output-validation boundary when
introducing the shared validator. Keep the original response text unchanged; validation
must evaluate its numeric value rather than a rounded replacement. Use a local reader
with arbitrary-precision decimal handling, without changing unrelated shared codecs.
The step-action, callback, TypeSafe and YAML corrections concern separate ingress paths.

Add precise minimum/maximum boundary tests, including long fractional/exponent-form
values and nested fields, with valid boundary controls. Add an ordinary-advisor test
proving an originally out-of-range candidate enters existing bounded correction and
is not returned as valid because parsing rounded it. Preserve original candidate text,
correction budgets and final acceptance behavior. An offline probe combined the current
output parsing operation with NetworkNT 3.0.9: {"value":1.0000000000000001} against
maximum:1 became {"value":1.0} and passed; lossless parsing retained the value and failed.
This demonstrates a migration integration risk, not a currently implemented maximum
check failing in production. Implementation, migrated-advisor tests and live acceptance
remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaValidator.java:58`
  (`validate`) parses raw output with the supplied mapper; its default constructor
  uses schemaTree(), built without lossless decimal handling at
  `src/main/java/ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java:39`.
- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:112`
  (`adviseCall`) validates the ordinary candidate through this boundary.
- `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaValidatorTest.java:140`
  (`distinguishesArraysScalarsNumbersAndJsonNull`) checks numeric type distinctions,
  not exact bound comparisons. `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java:148`
  (`retriesWithCanonicalIssuesAfterSchemaMismatch`) covers correction, not precision
  at migrated numeric boundaries. These are inspected tests, not new acceptance runs.

### Contract 4

#### TBD-07: Selecting provider differences without a capability registry

**Status: Resolved — explicit driver mappings agreed.** Keep one authoritative
standard schema and small, explicit request mappings at the existing `openai`,
`anthropic`, and `gemini` integration boundaries. This is not a capability registry
or a general schema-conversion system.

#### Required implementation behavior

1. Preserve the authored/generated canonical schema. Local input, model-contribution,
   composed-output, and planning validation must use their applicable canonical
   contracts, retaining every approved constraint. Provider preparation operates on
   a separate request copy; it must not mutate cached schemas or leak changes across
   calls/providers. Existing binding projection remains governed by TBD-10.
2. Send supported keywords unchanged. Send accepted-but-ignored keywords unchanged
   and enforce them locally. Do not remove a keyword merely because native enforcement
   is absent, uncertain, or inconvenient for an SDK serializer. Adapter field loss is
   a defect to fix, not an approved provider omission.
3. Allow omission only for these v1 assertion keywords when rejection is established
   for the applicable provider and request context: `minimum`, `maximum`, `minLength`,
   `maxLength`, `minItems`, `maxItems`, and `format`. For `format`, name the exact
   affected standard formats; do not omit all formats because one is unsupported.
   Rules may depend on an explicitly verified keyword value restriction, but must
   not infer capabilities from model names or endpoint URLs. Preserve all original
   assertions in local validation. No other omission is authorized by this decision.
4. Implement only an explicit, evidenced rule table within each existing driver.
   Each entry must name the keyword/value or format, context (output/planning or
   tool arguments), provider requirement, source/test evidence, and omission action.
   Tool entries do not authorize enabling strict tools; TBD-08 owns that decision.
   Traverse only actual schema nodes in the approved v1 tree: the root, `properties`
   values, `items`, and schema-valued `additionalProperties` for ordinary inputs/tool
   arguments. Native output/planning typed maps remain deferred. Never recursively
   delete matching keys from arbitrary JSON: a property named `minimum` or `format`
   is application data.
5. Never strengthen or reshape the canonical contract to satisfy a provider. In
   particular, do not make optional fields required, insert nullability, substitute
   null for absence, close an open object, change types/enums, wrap values, or convert
   maps to arrays. Report incompatibility when the authored contract cannot be sent
   using the approved mappings. Exact failure timing belongs to TBD-09.
6. Use one shared OpenAI mapping for all routes behind `openai`. Do not detect or
   special-case OpenAI, Meta, OpenRouter, or Ollama by URL/model name. If that shared
   mapping cannot support a route, document the concrete limitation and bring the
   issue back for a product decision. No route-specific switches, compatibility
   aliases, new capability registry, or speculative configuration knobs are authorized.
7. Never discover mappings by retrying errors with successively removed keywords.
   Never switch providers/protocols or downgrade to `json_object`/prompt-only output.
   Local validation failures continue through the separately defined bounded correction
   path; provider schema rejection is not permission to weaken the contract.
8. Document each approved omission and its weaker native generation guarantee. Make
   the canonical contract and actual transmitted schema distinguishable in existing
   diagnostics, preserving content fidelity. Reuse existing diagnostics rather than
   inventing a new observability subsystem. Do not claim provider enforcement for a
   locally checked constraint.

#### Required verification and ticket handoff

Implementation must satisfy these requirements together with the exact per-driver
rule entries and acceptance cases specified in this ticket. A link to this section
alone or a direction to "handle provider differences" is insufficient. Ticket authors
must identify unverified mappings as unresolved evidence work; the implementing LLM
must not invent omission rules, structural conversions, configuration, or fallbacks.
If evidence is insufficient or implementation needs an exception, flag the concrete
case for discussion before adding it. Do not treat a related SDK bug as evidence of
provider rejection. Preserve source versions and separate mock serialization evidence
from live endpoint acceptance/enforcement evidence.

Acceptance coverage must demonstrate:

- Canonical schemas remain unchanged across repeated calls and different drivers.
- Supported and ignored keywords survive the actual serialized request.
- Only approved rejected keywords/values/formats are omitted, at root and nested
  schema nodes; similarly named application properties remain intact.
- Local validation still rejects instances violating every omitted assertion;
  bounded correction never turns an invalid result into accepted output.
- Optional/required, nullability, object openness, types, and enums remain unchanged;
  incompatible structural requirements produce clear failures, not transformations.
- No model-name/URL dispatch, route-specific implementation, fallback, or error-driven
  keyword stripping is introduced.

The bounded v1 mapping work is approved; the request-copy table in this ticket
identifies retained entries and the applicability evidence still to verify. TBD-08 through TBD-11 are separately resolved below; this decision alone does not
assert native support for untested models/routes.

### Contract 5

#### TBD-09: Unsupported schemas/models and failure timing

**Status: Resolved — failure policy agreed.** Use the following boundaries without
introducing capability discovery, implicit fallback, or a competing retry system.

| Failure | Required timing and behavior |
| --- | --- |
| Invalid declaration or feature outside the approved v1 profile | Reject during skill loading or Java declaration registration, with skill/declaration identity, schema location and reason. Include unsupported roots, keywords and format names. |
| Known incompatibility with the selected driver | Reject before sending the affected model request using explicit evidenced driver rules. Do not infer capabilities from URLs/model names. If the driver is selected only at execution time, do not pretend this was verifiable at registration. |
| Provider rejects the schema or does not support requested native structured output | Fail the call clearly. Do not retry the unchanged rejected schema, experiment with keyword removal, switch provider/protocol, or downgrade to JSON-only/prompt-only output. |
| Generated output violates the canonical contract | Use the bounded output-correction path; fail if correction is exhausted. Never accept invalid output. Correction history and side-effect handling remain TBD-11. |
| Transient transport/provider failure | Retain the existing transport-retry policy, separate from schema correction. Do not classify deterministic schema incompatibility as transient. |
| Validation cannot complete | Report an operational failure, not an invalid-instance verdict or partially accepted result. |

Loading/registration performs local checks only: do not make live model calls to
probe compatibility, add automatic startup capability discovery, or introduce a
runtime capability registry. Document tested provider/model/route combinations and
the scope of that evidence. Untested combinations may be attempted; neither local
schema validity nor endpoint accessibility promises native structured-output support.
A provider accepting a request alone is not proof it enforces the schema.

Use existing error/outcome mechanisms. Include skill/declaration identity, driver
and model when available, relevant schema/instance location, and original provider
error details when available. Preserve diagnostic content fidelity. Distinguish
invalid declarations, known/provider-reported incompatibility, invalid generated
output, and operational failures. Do not invent a new public exception hierarchy,
error API, or configuration surface without demonstrating a concrete need and
bringing it back for discussion. Unrecognized provider errors retain their original
details; do not guess a capability diagnosis from incidental message text.

"Before sending" means before the affected request, not before every previous step
in a multi-step execution. Do not promise rollback of already executed tools or add
compensating actions. Existing authentication/authorization failures remain separate;
this policy does not authorize retries or fallback for them.

#### Mandatory ticket handoff and acceptance

Tickets must reproduce the failure table, local-only registration rule, diagnostic
requirements, and no-fallback/no-rollback boundaries. Each affected path must identify
its existing error/outcome and retry owner. Do not leave the implementing LLM to
choose failure timing, invent error categories/public types, or treat every provider
400/error as either retryable or a schema incompatibility. Evidence-backed error
classification and any unresolved provider behavior must be explicit in the ticket.

Required acceptance cases: invalid declarations fail without network calls; known
driver incompatibilities send no affected request; explicit schema/support rejection
is surfaced once without correction/transport retries or fallback; transient failures
retain the existing retry policy; canonical-invalid generated output follows bounded
correction and fails on exhaustion; incomplete validation is never accepted. Verify
useful original diagnostics and that earlier successful tool actions are not falsely
reported as rolled back. Keep unsupported-route evidence distinct from approved
keyword mappings under TBD-07. TBD-11/12 continue to own correction history and
refusal/incomplete-response behavior respectively.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

#### Provider feature matrix

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

#### The four routes behind `openai`

| Route | Output evidence | Tool evidence and model/route limit |
| --- | --- | --- |
| OpenAI direct | Strict subset above through `text.format` | Non-strict tool schemas are distinct from strict generation. RC2 explicitly sends false unless configured; TBD-08 now defers native strict tools beyond v1. |
| Meta direct | [Responses schema reference](https://dev.meta.ai/docs/api-reference/responses/schemas) defines `TextResponseFormatJsonSchema`, schema, and strict subset, but does not establish a keyword-by-keyword parity table. [Structured-output tutorial](https://dev.meta.ai/docs/structured-output) uses Chat Completions with `muse-spark-1.3`; do not substitute that for Responses acceptance. | Responses function parameters are JSON Schema; documented strict default is true, overridden by RC2's explicit false. Optional/null/open/composition/constraint behavior remains U for the selected Responses model. |
| OpenRouter | [Structured-output guide](https://openrouter.ai/docs/guides/features/structured-outputs) documents `text.format`; native enforcement, translation, and hint-only behavior vary by upstream. No universal keyword table is justified. | Record exact model and endpoint/provider route. `require_parameters` can constrain routing but does not establish keyword enforcement. A hint-only upstream cannot pass native-output acceptance merely because local validation succeeds. No registry or automatic switch is proposed. |
| Ollama | [Compatibility docs](https://docs.ollama.com/api/openai-compatibility) describe stateless Responses. [Pinned converter](https://github.com/ollama/ollama/blob/8a89b76cc7359805e48357ba7e5b99e845a4c211/openai/responses.go) forwards `text.format.schema` to native `ChatRequest.Format`; this does not prove grammar keyword enforcement. | The earlier 0.40.16 / `qwen3.5:4b-q4_K_M` probe tested tool/reasoning replay, not structured output. Keyword semantics, schema-plus-tools, and strictness remain U on that installed combination. No Ollama runtime branch is proposed. |

#### Input and tool contexts

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
