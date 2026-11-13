# Provider Consolidation and Standard Schema Migration

## Direction and scope

This roadmap has two required workstreams: consolidate compatible providers onto
the shared OpenAI Responses implementation, and standardize input/output schemas
with native structured output for planning and schema-bearing skill calls across
`openai`, `anthropic`, and `gemini`. Roadmap completion requires both workstreams,
their removal audits, and their acceptance evidence. The existing filename is
retained so links remain intact.

Migration direction agreed on November 10, 2026: prefer Spring AI's implementation
and keep Loomspan integration simple. Loomspan currently pins Spring AI 2.0.0;
the relevant upgrade is to the 2.1 line containing `OpenAiResponsesChatModel`.
Non-GA dependencies are acceptable during development. Production readiness is
expected to be at least two months away; this is not a commitment that an upstream
GA release will arrive within that period.

Use `OpenAiResponsesChatModel` for all connections using the existing `openai`
driver, targeting OpenAI, Meta, OpenRouter, and Ollama. Use stateless history replay for
all four: `store: false`, full invocation-local history, and ordered replay of
required reasoning and tool items. Retain Spring AI's ChatClient and tool loop.
Do not build a competing Responses client, a second agent loop, a conversation
database, a response-ID lifecycle, or a new public provider SPI.

The target architecture has exactly three chat-model implementations:

| Driver | Chat-model implementation and provider coverage |
| --- | --- |
| `openai` | Shared Spring AI Responses implementation for OpenAI, Meta, OpenRouter, and Ollama |
| `anthropic` | Native Anthropic integration |
| `gemini` | Native Gemini integration |

TypeSafe remains a separate native evaluation integration (`typesafe`), outside
these three chat-model implementations. Do not remove it or route it through a
chat-model adapter. Shared framework services, tools, tracing, configuration,
Console, and other application features are not additional chat-model implementations.

Native schema-constrained output is also an explicit goal of this roadmap across
all three chat-model implementations. Unify how Loomspan expresses an output
contract, and send that contract to the provider to constrain generation for
schema-bearing skill calls and model planning. Keep shared local validation as the
authority for accepted results. This expands the roadmap beyond the Responses
transport migration to structured-output integration for Anthropic and Gemini;
their native protocols remain in place. TypeSafe's native evaluation protocol is
unaffected; any shared schema declarations it consumes must adopt the new format.

Skill input and output declarations will both use standard JSON Schema vocabulary
authored in YAML, replacing Loomspan's custom schema dialect. This is an intentional
breaking change: there are no customers or deployed implementations to preserve.
Remove the old dialect and its implementation, with no dual-format reader or
compatibility layer. The shared schema baseline must work across all three chat
integrations, but need not be limited permanently to their lowest common denominator;
documented, evidenced provider differences are permitted as described in Phase 2b.

During implementation, review the touched provider paths and their dependencies
against this boundary. Flag any additional implementation, code, module, or feature
that appears unrelated to the agreed migration or inconsistent with this architecture
for discussion with the user. Record its location, purpose, references/dependencies,
and why it warrants discussion; do not silently remove, redesign, or absorb it into
the migration. Unrelated does not mean unused or obsolete. Continue independent
in-scope work, and discuss any finding that blocks the migration before making a
scope-expanding change. Removal of the legacy OpenAI and Ollama implementations
already specified here is authorized and does not need renewed approval.

Simplicity and reducing technical debt are the north star. Loomspan is still in
development: destructive changes are encouraged where they simplify this migration.
Across all decisions, favor a simple skill framework and familiar standard behavior.
Do not invent restrictions merely from a general preference for strictness, uniformity,
or a lowest common denominator. A limitation may be worthwhile when it addresses a
demonstrated API/adapter incompatibility or materially simplifies implementation,
skill authoring, diagnostics, or model behavior. Explain the concrete benefit, the
capability lost, and the alternatives to the user before adopting a new product
restriction. Recommendations should distinguish verified requirements from proposed
tradeoffs and untested hypotheses. This principle does not reopen explicitly agreed
boundaries such as removing the legacy dialect and Ollama runtime implementation.
No compatibility shims, deprecated aliases, legacy Chat Completions implementation,
protocol selector, or automatic Chat fallback are wanted. Replace the old path and
update callers, configuration, tests, and documentation together. Review any supported
API impact deliberately and update the closed allowlist and consumer documentation
if needed; this migration should not require new application-facing Java types.

Developers selecting OpenRouter models own the suitability of those models for
their application. Loomspan owns correct serialization, replay, tool execution,
and failure reporting. Do not create a capability registry or provider-specific
architecture to promise uniform model behavior.

This document records the agreed implementation direction; drafting it has not
performed the dependency or runtime migration. A subsequent implementation task
should follow these decisions without reopening settled scope merely because it
starts in a new context. Change direction only for concrete contrary evidence.
The subsequent Ollama investigation explicitly expands this scope to Ollama, using
the same `openai` driver. Remove all Ollama-specific implementation code with the
migration, including the separate `ollama` driver and native adapter. Ollama must
use the shared OpenAI Responses implementation exclusively. Do not retain compatibility
aliases, deprecated driver values, automatic configuration translation, dormant native
code, or fallback to Ollama's native API or Chat Completions. Validate the migrated
path before accepting the removal; validation is not a reason to retain a second path.
Adding Responses providers beyond OpenAI, Meta, OpenRouter, and Ollama requires a
later ticket and must not influence this design. Native structured-output work for
Anthropic and Gemini is already required scope, not additional Responses-provider
expansion.

## Decision rationale and handoff context

### The problem we are solving

The discussion started with direct Meta hosted-model access using the existing
OpenAI-compatible driver. Basic calling and tools already worked in the initial
spike. The remaining concern was using the provider API well, especially preserving
reasoning state when a model pauses to call tools. That original workstream addresses
API integration and history fidelity; different URLs and keys were never the
substantive issue. The agreed roadmap subsequently expanded to provider consolidation
and a breaking migration to standard input/output schemas, including native structured
output for planning and schema-bearing skill calls. Both workstreams aim to simplify
the framework and improve contract reliability, without assuming improved model
intelligence or introducing a new application-facing provider API.

Three concepts must stay separate:

- **Reasoning effort** controls how much computation a model uses for a request.
  Losing prior reasoning does not by itself disable `medium` or force a provider
  default. Effort support and serialization must be tested independently.
- **Conversation history** includes instructions, user/assistant messages, tool
  calls, and tool results. A model can receive this history without its private
  reasoning state. The existing tool loop does retain conversational history;
  this migration is not fixing an absence of all tool-call context.
- **Reasoning continuity** means returning the provider's replayable reasoning
  items with the continuation so it can resume an unfinished response after tools.
  Items may be encrypted or signed; a visible thinking summary is not a substitute.
  Loomspan preserves the required protocol data rather than interpreting it or
  converting it into additional natural-language instructions.

Meta's documented external Chat Completions behavior provides no replayable private
reasoning through `reasoning_content`; its Responses contract provides a replay
mechanism. That motivated the migration. Do not generalize this to every
OpenAI-compatible service: Spring AI's existing Chat adapter can replay nonempty
`reasoning_content`, and OpenRouter supports structured reasoning through Chat too.
The earlier initial Meta investigation was referenced at
`docs/maintainers/meta-compatibility-spike.md`, which is absent from this worktree.
Treat it as unavailable historical material, not accessible verification evidence; use
the retained source references and evidence below for the readiness review.

### Why these choices were made

| Decision | Reason and intended consequence |
| --- | --- |
| Reuse Spring AI Responses | Chat Completions and Responses are separate API contracts. Responses supplies ordered items suitable for tool/reasoning continuation. Spring AI 2.1 supplies the adapter, so this does not require abandoning Spring AI or writing a custom client/agent loop. This is a project-specific choice, not a claim that Responses is universally superior. |
| Accept a prerelease | The project is in development and the needed adapter is in the 2.1 line. Waiting for GA is not required; reproducible dependency selection and platform verification still are. RC2 was investigated, not irrevocably selected. |
| Include OpenRouter | The earlier OpenAI/Meta-only scope was explicitly expanded. Give routed models the same opportunity to preserve state through one integration. Do not assume direct Meta will produce better answers merely because an earlier OpenRouter path used Chat. |
| Use stateless replay for all four | Loomspan already owns history within its tool loop. Keeping that ownership avoids two state mechanisms, remote response-ID lifecycle/recovery, and a new persistence layer. It also fits OpenRouter and Ollama's stateless-only contracts. |
| Consolidate Ollama into `openai` | The user explicitly prefers one driver for these compatible providers. Remove native Ollama construction, options, dependency, and configuration branches after validating the shared Responses adapter. |
| Remove the old Chat path | Maintaining two protocols, option mappings, test suites, and fallback behaviors would work against simplicity. Development-stage breaking changes are intentional; old-revision benchmarks do not justify retaining legacy runtime code. |
| Limit provider-specific handling | Developers choose appropriate OpenRouter models; Loomspan must faithfully carry supported state and report failures. Add narrowly evidenced fixes, not a speculative universal provider abstraction. Future provider expansion is a separate decision. |

Stateless does not mean memoryless: each next request includes the deliberately
retained history. It adds request payload size and requires faithful item replay,
including potentially large opaque items. It does not imply free context or an
unbounded retention policy. Existing limits remain, and compaction/context-policy
redesign is outside this migration. The expected incremental complexity is in
preserving rich message parts, not in creating a new conversation subsystem.

More thinking is not automatically better. Continuation within an unfinished tool
loop has a protocol purpose; retaining reasoning from completed tasks or failed
correction attempts may have different effects. Visible thoughts can be misleading
as a quality measure, and opaque state cannot be evaluated by reading it. We have
not measured a quality benefit. Assess outcomes, repeated tool work, latency, usage,
and context growth separately from proving correct serialization.

### Tool-continuation, retry and isolation verification

Required state replay across ordinary tool continuations is agreed. Full history
means the history belonging to that interaction under its intended semantics, not
all prior work by the invocation or every abandoned attempt.

| Boundary | Starting observation or decision | Verification needed |
| --- | --- | --- |
| Ordinary/parallel tools and recoverable tool errors | Continue the active conversation, preserving required ordered assistant items and tool results. | Actual outbound second/third requests; canonical names, authorization, IDs, order, opaque fields, and no duplicate tool execution. |
| Transport/rate-limit retries | Retry the current provider request under Loomspan's existing retry ownership. | No accumulated duplicate history, nested SDK retries, or re-execution of completed tools. |
| Planning, step execution, child skills, separate interactions | No blanket decision to carry reasoning between these boundaries. | Verify intended isolation and connection/model binding; never share mutable continuation state accidentally. |

Other-provider thinking-block verification remains in the companion investigation,
not in this migration's provider acceptance scope. Keep those follow-ups without
letting them shape this implementation. Existing framework regression checks still
apply; excluding new provider work does not authorize breaking other drivers.

### What was actually established before implementation

- **Repository baseline:** Spring AI 2.0.0 and Boot 4.1.0 are pinned. The current
  OpenAI-compatible path uses `OpenAiChatModel`; migration code is not implemented.
- **Recorded Meta spike:** the earlier report records 16 passing focused tests,
  including a live tool round trip and synthetic parallel-call fixture. It used
  `muse-spark-1.3`, low reasoning effort, at most four calls, a 90-second per-call
  timeout, and a 2048-token budget. This proves neither medium-effort behavior nor
  private reasoning continuity nor the complete framework pipeline. These are
  recorded prior results, not tests rerun during this document audit.
- **Credential setup:** the user saved `LOOMSPAN_META_API_KEY` in the environment.
  A fresh process may need to load the Windows user environment; the spike's
  instructions explain how. Its live opt-in is `LOOMSPAN_META_SPIKE=true` and its
  optional model override is `META_MODEL`. Do not assume other account credentials
  exist. Update these instructions when the probe is replaced.
- **Spring AI evidence:** published RC2 sources/POMs were inspected, with concrete
  mapper and message-reconstruction risks recorded below. Subsequent isolated RC2
  compilation, deterministic replay tests, and an Ollama live tool/reasoning round
  trip passed, as recorded in the Ollama validation section. Full framework
  migration and platform validation, other-provider live Responses acceptance,
  and native structured-output validation remain pending. The isolated probe does
  not establish compatibility of the complete Loomspan execution pipeline.
- **OpenRouter evidence:** current documentation describes protocol adaptation,
  stateless operation, reasoning replay through both APIs, and routing rules.
  No live verification establishes exact Meta route behavior, native pass-through,
  mapper fidelity, or reasoning portability on fallback.

For a new implementation context, begin with dependency alignment and a focused
multi-round Responses fixture through the real factory, options contributor,
canonical tool manager, and advisor pipeline. Check the mapper's required replay
fields early; successful final text alone can conceal dropped state. Carry forward
these evidence distinctions and update the checklist as work is actually completed.

## Phase 1 Select and verify the dependency

- [x] Inspect published Spring AI 2.1.0-RC2 source artifacts and POMs from Maven
  Central. The adapter exists in `spring-ai-openai`; no new starter is needed.
- [ ] Resolve platform alignment before selecting the exact pin: RC2's upstream
  build uses Boot 4.2.0-RC2, Spring Framework 7.1.0-RC2, and Micrometer 1.18.0-RC2,
  while Loomspan pins Boot 4.1.0. Java 21 exceeds the upstream Java 17 baseline.
  Decide a coherent Boot/AI version pair and verify the effective dependency tree,
  compilation, startup, and tests. Do not assume changing only the AI property works.
- [ ] Prefer a reproducible milestone or release candidate over a moving snapshot.
  Use a snapshot only if a required fix warrants it and record the exact resolved build.
- [ ] Review migration changes to ordered message parts, options, metadata, usage,
  error types, tool execution, and the OpenAI SDK. Run the existing regression suite
  as an upgrade check without expanding the provider design scope.
- [ ] Establish the current test baseline and update the dependency in an isolated
  implementation change. Do not combine unrelated framework redesigns with this upgrade.

Completion: a reproducible dependency selection and passing existing checks, with
any newly exposed defects documented separately from intentional API changes.

## Phase 2 Integrate the Responses adapter

- [ ] Replace the existing OpenAI-driver Chat Completions path with Responses for
  OpenAI, Meta, OpenRouter, and Ollama. Do not add a protocol selection key or fallback.
  Document the intentional configuration behavior change; endpoints that only
  accept Chat Completions are no longer supported by this driver.
- [ ] Consolidate Ollama under `driver: openai` and remove all Ollama-specific
  implementation code, dependencies, configuration branches, and driver values.
  Update callers and configuration together; provide no compatibility alias,
  automatic translation of `driver: ollama`, or native/Chat fallback.
- [ ] Construct `OpenAiResponsesChatModel` in the existing provider factory and
  map options through `OpenAiResponsesChatOptions`. Loomspan constructs models
  itself, so Spring Boot's `spring.ai.openai.chat.api` switch alone is insufficient.
- [ ] Use Spring AI's stateless replay implementation. Keep conversation ownership
  local; server-managed history and a separate response-ID lifecycle are out of scope.
- [ ] Preserve ordered message parts when Loomspan validates tool names or rebuilds
  messages. Audit `CanonicalToolCallingManager` and output composition in particular;
  copying text, metadata, tools, and media alone may discard the new reasoning parts.
- [ ] Map Responses failure/status and usage data into existing tracing, execution
  limits, timeouts, and retry ownership. Disable nested SDK retries as on existing paths.
- [ ] Apply the agreed history semantics to any correction paths touched by the
  migration. Do not assume every retry must inherit all prior reasoning.

Completion: one existing execution pipeline can use the Responses adapter without
introducing a second tool loop or expanding the supported application-facing Java API.

## Phase 2b Standardize input/output schemas and native structured output

The user explicitly included this work to simplify the framework and encourage
provider outputs to conform to Loomspan's schemas. It is required roadmap scope,
not an incidental option change or a future optional enhancement.

### Settled schema decisions and rationale

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

Retain internally generated `false` property schemas for framework-bound child-input
destinations. Preserve existing override checks and assembled-input validation, and
include this generated shape in adapter schema-fidelity tests. This does not expand
authored-schema scope.

Preservation evidence: `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java:56` (`properties.put(tokens.get(i), false)`) emits the false property schema; `src/test/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java:59` (`void allBoundAncestorMayBeOmittedAndGenericFieldsAreStructurallyForbidden`) and `src/test/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java:72` (`void preservesTypedExtensionsArrayItemsAndFormattingWhileExcludingReservedNames`) assert its serialization and ownership protection. These local assertions are not provider acceptance evidence.

Flag existing generated unconstrained schemas or internal projection needs outside
this baseline; they do not automatically expand authoring scope.

### Shared baseline and documented provider differences

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

### Implementation and removal checklist

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

## Phase 3 Verify protocol and framework behavior

- [ ] Add deterministic protocol tests for multiple tool rounds, parallel tool calls,
  ordered encrypted reasoning replay, assistant phase, opaque items, and call/result IDs.
- [ ] Verify canonical tool authorization, recoverable tool failures, and independent
  concurrent invocation state. Provider retries must not repeat completed tool side effects.
- [ ] Cover terminal/incomplete responses, malformed responses, 429/5xx errors,
  timeout/cancellation, and per-attempt usage accounting.
- [ ] Migrate existing regression coverage to Responses, remove obsolete Chat
  fixtures, and run normal project checks and `LoomspanPublicSurfaceArchitectureTest`.
- [ ] Verify native output-schema serialization for all three implementations,
  planning versus skill schemas, projected output bindings, correction requests,
  unsupported schema behavior, and local semantic rejection of structurally valid
  outputs. Inspect actual outbound requests, not just successful JSON parsing.
- [ ] Test the shared input/output schema format, declaration rejection for obsolete
  dialect syntax, explicit changed omission/null semantics, exact field names,
  provider-specific keyword behavior, and local rejection when an ignored provider
  constraint is violated. Verify actual provider requests and canonical schemas
  separately where an explicitly documented mapping omits a keyword.

Completion: tests inspect the actual next outbound request, not merely final answer
text. They verify Responses replay and the standard schema contract across input,
planning, and output paths, including documented provider differences and local
validation. Replay and schema correctness do not by themselves prove a quality gain.

## Phase 4 Deployed and live acceptance — separate follow-up

After Framework implementation, create and execute tickets in the test-suite project
using its [migration acceptance roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md). That document owns the complete
agreed suite implementation scope and acceptance gates for both migration workstreams.
Framework implementation completion does not establish deployed or live acceptance.
Overall roadmap acceptance remains pending until those required gates pass.

## Phase 5 Stabilize before production

- [ ] Track upstream fixes relevant to this integration and move to a suitable GA
  version when available. Re-run the compatibility suite on each selected upgrade.
- [ ] Reassess any remaining prerelease dependency before production; development
  acceptance of non-GA versions does not decide the eventual production policy.
- [ ] Remove temporary workarounds superseded by upstream support and finalize
  configuration migration and release documentation. Complete the removal audit below.

## RC2 investigation findings

Inspected on November 10, 2026: versioned 2.1.0-RC2 documentation, the published
`spring-ai-openai`, `spring-ai-model`, and `spring-ai-client-chat` source JARs and
POMs, and the tagged upstream parent POM. Findings below distinguish source behavior
from changes proposed for Loomspan. The initial investigation was source-only;
subsequent isolated RC2 compilation, deterministic replay tests, and an Ollama live
tool/reasoning round trip passed. Full framework migration and platform validation,
other-provider live Responses acceptance, and native structured-output validation
remain pending. The earlier Meta live probe used Chat Completions and is not a
Responses acceptance test.

### Dependency selection is a platform decision

RC2 artifacts were successfully downloaded from Maven Central without adding a
snapshot repository. Its published OpenAI module uses `openai-java-core` 4.80.0.
The newer Spring and Micrometer versions above mean Loomspan's Boot dependency
management could select older libraries than RC2 was built against. This is a
compatibility question, not a proven failure. First produce an effective dependency
tree for the proposed pair and test it; prefer coherent platform alignment over a
collection of individual transitive overrides.

The 2.1 getting-started page still contains 2.0 version examples, and the Responses
page contains a memory-support note referring to an earlier milestone. Use the
published RC2 source and resolved dependencies as the executable evidence rather
than treating every versioned documentation paragraph as an RC2 guarantee.

### What Spring AI already supplies

`OpenAiResponsesChatModel.call()` builds a Responses request, calls the official
SDK once, and maps the result to one Spring AI generation. It performs no tool
execution. `ToolCallingAdvisor` retains per-loop history and calls the same tool
manager interface Loomspan already wraps. `DefaultToolCallingManager` appends the
assistant message object and tool response to history, preserving its parts.

`ResponsesRequestBuilder` sends `store: false`, requests encrypted reasoning,
maps the first system message to top-level `instructions`, and maps later system
messages to developer input items. It uses nested `reasoning.effort`,
`max_output_tokens`, and `text.format`. Tool definitions explicitly default to
`strict: false`; do not enable strict tool-argument mode as an incidental migration
change. Phase 2b explicitly adds native final-output schema enforcement; this is
separate from tool strictness and complements shared schema validation/correction.

No conversation database, previous-response-ID manager, custom SDK client, or
replacement tool loop is needed for the initial migration. Keep remote hosted
tools, background jobs, new streaming features, and compaction outside this scope.

### Preserve parts rather than reconstructing a message from views

`AssistantMessage.getParts()` is the authoritative ordered content. `getText()`,
`getToolCalls()`, and `getMedia()` are views, not a complete representation.
The mapper carries reasoning ciphertext, reasoning IDs/status, tool item IDs,
call IDs, and assistant phase through parts and their attributes. Metadata alone
does not carry this transcript.

Two concrete Loomspan sites need changes:

1. `CanonicalToolCallingManager.executeToolCalls()` currently constructs fresh
   assistant messages from text, metadata, media, and tool calls. That would omit
   reasoning and unknown parts and lose per-part attributes under RC2. Preserve
   the original message when no transformation is needed. When namespace
   canonicalization changes a tool name, replace only that `ToolCallPart`, keeping
   its payload, attributes, and position. Keep existing selection validation and
   planner-control behavior; do not weaken authorization to simplify copying.
2. `OutputSchemaCallAdvisor` constructs a new assistant message when assembling
   the final output. Decide which text represents composed output and preserve
   untouched parts explicitly. Keep original provider evidence separately from
   the application-facing composed text. A correction request intentionally
   synthesized from baseline/candidate/feedback is a separate history-policy decision.

RC2's `mutate()` copies parts and metadata, but `mutate().toolCalls(...)` creates
new attribute-free `ToolCallPart`s; `content(...)` similarly replaces text parts
with a new plain text part. Merely changing `builder()` to `mutate()` is therefore
not sufficient where item IDs, commentary phase, or tool-part attributes matter.

### Connection and request options must use the same protocol

`SpringAiProviderIntegration` should create the Responses model with the existing
key, base URL, headers, timeout, observation registry, and zero SDK retries.
`SpringAiChatOptionsContributor` must return Responses-specific options for this
connection; leaving it on `OpenAiChatOptions` would copy portable values but lose
Responses-specific reasoning configuration during combination.

`DefaultChatClientUtils.toChatClientRequest()` starts with the model's options and
combines the request customizer. In contrast, a direct `ChatModel.call()` with an
already-typed `OpenAiResponsesChatOptions` replaces model request defaults. Test
both pathways: a model-only override must not accidentally reset effort, timeout,
or request headers. Client construction and per-request options have different
lifetimes; credentials/base URL are configured on the SDK client.

No protocol configuration addition is needed: `openai` selects Responses throughout
model construction and request-option creation. Keep existing connection identity,
credentials, headers, and timeouts; do not infer protocol from model names or URLs.
Remove configuration that exists only to activate obsolete Chat behavior. Retain a
provider-specific option only if a demonstrated Responses difference requires it.

### Response status and retry ownership need explicit handling

HTTP failures still use the SDK's service exceptions. A successful HTTP response
with `status: failed` instead raises `OpenAiResponsesException`, carrying an error
code and message. Loomspan's current translator has no dedicated branch for it.
Classify observed provider codes using evidence, preserve their diagnostics, and
test retry counts instead of assuming every failed run is transient.

An `incomplete` response is returned rather than thrown. The mapper sets finish
reason `LENGTH`, `CONTENT_FILTER`, or `INCOMPLETE`. The default tool-loop eligibility
checks for tool calls, not completed status. Define and test handling before tool
execution or final success, particularly a response combining an incomplete status
with tool calls. `SpringAiModelInteraction` currently extracts final text without
examining that status. Do not accidentally label a truncated answer successful.

Keep `ProviderAttemptCallAdvisor` inside the tool loop and SDK retries at zero.
RC2's tool advisor aggregates usage over the loop; Loomspan already records usage
per provider attempt. Test that accumulated final usage is not counted a second time.

### Observability must distinguish content from protocol state

Responses details live on generation/response metadata, while replay data lives
in message parts. `ProviderAttemptCallAdvisor.messagePayload()` currently records
text and tool calls/results, and `responsePayload()` does not include generation
or response metadata. Extend the canonical evidence representation to capture the
ordered parts and relevant status/identity metadata without rewriting provider
content. Existing explicit resource limits still apply.

`ModelUsageExtractor` can continue reading portable input/output/total usage and
retaining native usage. Reasoning-token detail is in the native Responses usage
object; verify serialization and avoid adding it again to output totals. Its
text-only heuristic cannot estimate opaque reasoning, so do not present fallback
counts as exact reasoning usage.

### Meta checks required for the same adapter

RC2 always requests encrypted content and sends stateless requests, matching the
documented Meta replay pattern. Meta requires a summary array on reasoning input;
RC2 emits an empty array when no summary exists. Meta permits omission of reasoning
IDs on input, but RC2 requires an ID for replay; confirm actual Meta responses
provide the IDs the SDK mapper expects. Preserve commentary phase and call/result
pairing. Leave automatic truncation unset/disabled because Meta rejects `auto`.
See [Meta's Responses contract](https://dev.meta.ai/docs/protocols/responses).

The adapter tags replay payloads as `openai.responses` regardless of endpoint.
That identifies the protocol, not a credential, model, upstream route, or account.
Keep continuation state local to its invocation and captured connection; do not
interpret that tag as permission to replay ciphertext across accounts or models.

Known mapper limits also deserve fixtures: reasoning without ciphertext or an ID
is skipped; malformed unknown-item replay can be logged and omitted; multiple
reasoning summary fragments are joined into a single summary string. Establish
which items the chosen models return and verify required opaque data and ordering
survive. Do not claim byte-for-byte preservation of every response field.

### OpenRouter compatibility and stateless replay

OpenRouter exposes Responses even for upstream providers without native Responses;
it adapts protocols. Native pass-through and feature equivalence are not assumptions.
Its documented Responses contract rejects `store: true` and non-null
`previous_response_id` with HTTP 400. Both its Chat and Responses interfaces can
carry reasoning continuity; endpoint choice alone does not prove a quality gain.

- [ ] Assert every request uses `store: false`, omits `previous_response_id`, and
  contains the intended full history. Test second and third tool rounds on all
  four providers, including independent/concurrent invocation isolation.
- [ ] Test OpenRouter reasoning items with `content`, `summary`, `signature`, and
  `encrypted_content`, plus IDs and ordering. Check the actual RC2 mapper against
  each required field: ciphertext-only replay must not silently discard a required
  signed/plaintext reasoning item. Prefer an upstream fix over a second adapter;
  document a concrete blocker if the selected mapper cannot preserve the contract.
- [ ] Verify documented non-native-Responses upstream wire shapes with deterministic
  fixtures. Live coverage and the earlier extra-comparison scope question are owned
  by the [test-suite roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md#earlier-openrouter-checklist-scope-remains-an-audit-question).
- [ ] Exercise unsupported options with default routing and with
  `provider.require_parameters: true`, including no eligible route. If routing
  controls are needed, use existing Spring AI extension facilities rather than
  introducing a Loomspan capability abstraction. Record actual support in RC2.
- [ ] Verify failed lifecycle responses, top-level errors, `error_type`, incomplete
  responses, malformed items, 429/5xx, retry headers, and body deadlines. Replace
  the old `choices[].finish_reason == error` interceptor with Responses handling;
  retain a narrow workaround only where the SDK demonstrably loses needed evidence.
- [ ] Test route changes/fallback during continuation. Do not assume opaque state
  is portable between upstreams or models. Establish predictable failure behavior
  without silently dropping reasoning, changing models, or repeating completed tools.

Sources: [Responses contract](https://openrouter.ai/docs/api_reference/responses/overview),
[reasoning replay](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens),
[provider routing](https://openrouter.ai/docs/guides/routing/provider-selection),
[error handling](https://openrouter.ai/docs/api_reference/responses/error-handling),
and [API coverage](https://openrouter.ai/docs/faq). Documentation was reviewed on
November 10, 2026; live OpenRouter Responses compatibility remains unverified.

### Ollama consolidation and validation

The user explicitly selected one `openai` driver for Ollama as well. This extends
the original three-provider Responses scope; it does not move Anthropic, Gemini,
or TypeSafe to Responses. Anthropic and Gemini participate in the separate native
structured-output work in Phase 2b; TypeSafe's native evaluation protocol remains
unchanged, while shared schema declarations are included in the authoring migration.

Ollama documents stateless Responses beginning in v0.13.3. Source inspected at
commit `8a89b76cc7359805e48357ba7e5b99e845a4c211` emits reasoning IDs, summaries,
and `encrypted_content` containing plain thinking text. Input conversion uses
readable summary/content fields, not that encrypted-content field. The name therefore
does not establish encryption. `include` is ignored. The inspected request converter
supports `text.format` but notes that `tool_choice` is unsupported; reasoning-token
usage is a zero placeholder. These are source observations, not release guarantees.

RC2 preserves the output ID/payload/summary shape and reconstructs the summary on
replay, which appears compatible with that converter. A summary-only reasoning item
would still be skipped by RC2's replay mapper. Validate the selected server release
and model, not just the documented minimum endpoint version.

Sources: [Ollama compatibility documentation](https://docs.ollama.com/api/openai-compatibility#responses-api)
and [pinned Responses implementation](https://github.com/ollama/ollama/blob/8a89b76cc7359805e48357ba7e5b99e845a4c211/openai/responses.go).

- [x] Run an isolated RC2 fixture with three HTTP requests, two tool rounds, ordered
  reasoning summaries/payloads/IDs, call/result IDs, and stateless request checks.
  The [reproducible probe](../../../docs/maintainers/probes/ollama-responses/README.md)
  passed on November 10, 2026. It uses upstream ToolCallingManager, not Loomspan's
  canonical manager; the framework migration remains unimplemented.
- [x] Run the bounded live adapter probe with an installed thinking/tool model and
  record server/model versions and outbound replay. On November 10, 2026, Ollama
  0.40.16 with `qwen3.5:4b-q4_K_M` passed: two provider requests, a real lookup tool
  execution, correct fresh random result in the final answer, and ordered reasoning
  ID/summary/payload plus matching function call/result IDs in the next request.
  Both requests used `store: false` without `previous_response_id`. The combined
  fixture/live run reported 2 tests, no failures/errors/skips. See the probe README
  for the model digest, evidence location, and limits. This is upstream adapter
  acceptance, not full Loomspan pipeline acceptance or proof of effort-level semantics.
- [ ] Verify parallel calls, model-dependent effort, structured output, incomplete
  responses, and errors through the migrated framework pipeline. Do not infer that
  a provider enforces an option simply because it accepts the request.
- [ ] Replace `driver: ollama` configurations with `driver: openai`, an explicit
  compatible base URL such as `http://127.0.0.1:11434/v1`, and `api-key: ollama`
  for local use. Authenticated deployments require their actual credentials.
- [ ] Remove all Ollama-specific implementation code: `AiDriver.OLLAMA`,
  `OllamaChatModel`, `OllamaChatOptions`, native factory construction,
  `spring-ai-ollama`, driver-specific validation, thinking restrictions, timeout/key
  restrictions, failure-guidance branches, and configuration binding/metadata/hints.
  Replace native protocol fixtures and update examples and mixed-driver tests to use
  `driver: openai`. No compatibility aliases, deprecated driver values, automatic
  configuration translation, dormant native implementation, or native/Chat fallback
  may remain.
- [ ] Audit the repository for Ollama-specific implementation references after removal.
  Retain Ollama provider compatibility tests, shared-driver connection examples, and
  research/history documentation; these verify or describe provider support and must
  not introduce a separate runtime path or provider-specific dispatch.
- [ ] Document any Ollama limitations discovered in validation. If support requires
  Ollama-specific implementation code, bring the concrete incompatibility back for
  discussion before adding an exception; the default remains the shared `openai`
  implementation with no Ollama-specific runtime branches.
- [ ] Keep common headers, timeout, retry ownership, options, and diagnostics on the
  shared path. Native Ollama controls are not automatically available through Responses;
  inventory actual framework usage before removal rather than inventing replacements.

### Remove the old OpenAI-compatible implementation

Repository reference audit on November 10, 2026 found these migration surfaces.
Removal happens with the runtime migration, not by deleting the currently working
implementation during this planning change.

- [ ] Replace `OpenAiChatModel` and `OpenAiChatOptions` in
  `SpringAiProviderIntegration` and `SpringAiChatOptionsContributor`. Remove dead
  imports, builders, helpers, and dependencies that become unused.
- [ ] Remove the OpenRouter Chat error-completion interceptor (`inspectOpenRouter`
  and its body decoder). Review its classification helpers for shared use before
  deleting them; preserve required Responses error diagnostics in the normal path.
- [ ] Remove `openai.compatibility-profile` and its enum/binding/parser/metadata
  entries if Responses needs no explicit profile. Do not keep a no-op setting or
  alias. If a real Responses distinction requires configuration, document the
  evidence and keep only that narrow behavior.
- [ ] Update endpoint descriptions in Spring configuration metadata and all active
  setup/examples. Replace Chat request/response fixtures, including `choices`,
  `finish_reason`, and `/chat/completions` expectations, for migrated connections.
- [ ] Migrate provider/options/factory/protocol, autoconfiguration, observations,
  timeout, attempt/retry, output-schema, reload, planner, child-result, supported-
  surface, and mixed-driver integration tests. Test names containing another driver
  can still contain OpenAI fixtures; inspect their actual connection under test.
- [ ] Replace `MetaCompatibilitySpikeTest` with Responses acceptance coverage.
  Retain the earlier spike results only as clearly labeled historical evidence;
  update its run instructions when the old probe is removed.
- [ ] Final repository-wide search for `OpenAiChatModel`, `OpenAiChatOptions`,
  `chat/completions`, Chat-shaped fixtures, and the old profile confirms no active
  OpenAI-driver dependency remains. Historical notes may explain the old behavior;
  unrelated drivers and Spring AI's generic `ChatModel`/`ChatClient` abstractions
  are not the legacy implementation and should not be deleted.

Completion of the legacy provider removal requires one Responses implementation for the OpenAI-compatible driver,
with Ollama using `driver: openai` exclusively and no Ollama-specific implementation
code or dependency remaining. No dormant Chat/native path, compatibility alias,
automatic legacy configuration translation, or compatibility shim may remain.
Provider removal also requires updated documentation and fixtures and passing project
checks including the public-surface architecture test.

Overall roadmap completion additionally requires the standard input/output schema
migration, native structured output for planning and schema-bearing skill calls
across all three chat-model implementations, removal of the old schema dialect and
implementation, and the combined acceptance evidence below. Provider consolidation
alone is not completion of this roadmap.

- [ ] Audit the final provider structure: exactly three chat-model implementations
  (`openai`, `anthropic`, `gemini`), with TypeSafe retained separately for native
  evaluations. Verify that no additional or dormant chat-model implementation remains.
- [ ] Report unrelated or unexpected code, modules, and features found during the
  migration with locations and rationale for discussion. Record any resulting scope
  decisions explicitly; do not treat this migration as authorization for unrelated cleanup.

### Required acceptance evidence

| Test boundary | Evidence required |
| --- | --- |
| Dependency upgrade | Resolved Boot/Framework/AI/SDK versions, compilation, startup, project regressions, public-surface architecture check |
| Connection/options | Correct endpoint path; supported effort levels reach nested reasoning options; unsupported levels are tested explicitly; headers, timeout, and zero SDK retries survive option combination |
| Tool continuation | Second and third outbound requests retain ciphertext, item IDs, call IDs, ordering, and commentary phase after canonical validation |
| Output composition/correction | Unchanged parts survive composition; each correction path sends the deliberately selected history |
| Native structured output | All three chat-model implementations use the shared contract with documented keyword-level differences; live selected-provider/model cases demonstrate schema-constrained skill and planning outputs; no silent whole-request downgrade |
| Standard schema migration | Input and output declarations use the same documented standard JSON Schema profile; old dialect, implementation, and dual-format support are absent; examples, tooling, and consumers are migrated |
| Schema semantics and planning | Breaking semantic changes are explicit in migrated declarations; the new contract is enforced locally even where a provider ignores a constraint; model-contribution projection excludes framework-owned fields; semantic plan checks remain |
| Failures | HTTP failures, failed lifecycle status, incomplete outputs, cancellation, and retry counts behave as agreed; no duplicate completed tool effects |
| Evidence/usage | Ordered parts and lifecycle status visible in canonical records; per-attempt usage counted once with native details retained |
| Live validation | Explicit OpenAI, Meta, OpenRouter, and Ollama model/route IDs and Ollama server version, bounded multi-round probes, replay evidence, and no claim of reasoning quality based only on ciphertext presence |

## TBD: audit findings and decisions to resolve

This register preserves the roadmap audit for sequential discussion and later ticket
creation. Recommendations below are proposals, not accepted requirements. Resolve
one item at a time with the user, update the affected roadmap sections, and record
the decision here. Retain resolved entries as handoff history. Do not infer approval
of a proposal from its inclusion in this list. All entries start open.

### TBD-01: Reconcile validation evidence summaries

**Status: Resolved — agreed November 10, 2026.** Corrected the early evidence summary
and RC2 investigation introduction to distinguish the initial source-only research
from subsequent successful isolated RC2 compilation, deterministic replay tests, and
the Ollama live tool/reasoning round trip. Full framework migration and platform
validation, other-provider live Responses acceptance, and native structured-output
validation remain pending. This reconciles recorded evidence without changing scope
or treating isolated adapter success as full framework acceptance.

### TBD-02: Align scope, title, and completion criteria

**Status: Resolved — agreed November 10, 2026.** Renamed the roadmap to "Provider
Consolidation and Standard Schema Migration" while retaining the filename for link
stability. Updated the scope, problem statement, phase summaries, and overall
completion criteria to require both provider consolidation and standard schemas with
native structured output. Qualified the provider-expansion restriction as additional
Responses providers; Anthropic/Gemini schema work remains authorized scope. Historical
Responses rationale is retained. These edits clarify agreed scope without expanding it.

### TBD-03: Ollama removal versus provider-specific schema mappings

**Status: Resolved — agreed November 10, 2026.** Ollama uses the shared `openai`
implementation with no Ollama-specific runtime branches, including schema mappings
or dispatch. Document limitations discovered during validation. If supporting Ollama
requires special implementation code, flag the concrete issue for discussion before
adding it. This rule takes precedence over the general permission for narrow provider
mappings; no hypothetical exception or disguised native adapter is authorized.

### TBD-04: Shared schema format versus identical usage restrictions

**Status: Resolved — agreed November 10, 2026.** Use one standard schema format and
shared vocabulary, representation, parsing, and validation machinery. Allow documented
restrictions based on usage (invocation inputs, tool arguments, model outputs, or
planning), checked where the schema is used. Report incompatibility clearly without
silent schema rewriting or a second dialect. This does not settle omission/null rules
(TBD-05), the complete feature profile (TBD-06), or strict tool enforcement (TBD-08).

### TBD-05: Omission, explicit null, and object openness

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

### TBD-06: Initial JSON Schema profile and validation behavior

**Status: Resolved — v1 policy decisions agreed.** Adopt JSON
Schema Draft 2020-12 and NetworkNT's Jackson 3 line, with 3.0.9 as the verified
starting version and the final pin confirmed against the complete framework build.
Keep the validator internal and shared across input, output, and planning contracts.
The simple v1 feature baseline, including simple bounds, is agreed in
[V1 schema scope](#v1-schema-scope--agreed). General composition is deferred.
Typed maps are supported for ordinary inputs; native output/planning typed maps remain deferred. Other concrete uses outside the baseline must be flagged.
Standard `format` assertions are agreed; schema `default` support is deferred for v1.
Recommendation:
implement the agreed simple v1 profile using the selected NetworkNT foundation;
do not infer full JSON Schema support from library capabilities. Document local versus native
enforcement and test ignored-provider constraints locally.

Research completed November 10, 2026; subsequent user decisions close this policy item. See
[TBD-06 standard JSON Schema research](standard-json-schema-research.md) for the
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
[Validation resource-limit evidence and v1 decision](standard-json-schema-research.md#validation-resource-limit-evidence-and-v1-decision).
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

**Step-action decimal precision — agreed audit correction:** decode model-supplied
step-action decimals losslessly before validation, tool execution or final-response
serialization. Cover both toolArguments values and finalResponse trees. Keep the
correction local to this decoding boundary, using the existing arbitrary-precision
approach rather than changing unrelated shared mapper behavior. Preserve action
validation, authorization, correction budgets and the agreed parser migration.

Add focused step-engine tests with long fractional/exponent-form values, including
nested tool arguments and final-response fields. Assert exact values delivered to
callbacks and returned after final serialization, using precision-preserving assertions.
Include migrated numeric-boundary checks that reject the original out-of-range value
before callbacks or final acceptance; rounding must not make it valid. Retain bound
child-result precision and existing correction regressions. A local probe invoking
the actual action parser and final serializer changed 0.12345678901234567890123456789
to 0.12345678901234568 in both action kinds. This reproduces local precision loss;
full-engine acceptance and live behavior remain unverified. Implementation and focused
tests remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:1223`
  (`parseStepAction`) reads StepAction using the schema mapper; the tree-reading branch
  at line 1231 uses that mapper too. Production wiring supplies codecs.schemaTree()
  at `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:442`;
  `src/main/java/ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java:39`
  builds it without lossless decimal decoding.
- `src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java:171`
  validates already-decoded arguments. The engine passes those arguments onward at
  line 1179 and serializes final-response trees for validation/return at lines 991
  and 1051 (`serializeFinalResponse` at line 1474).
- `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:480`
  (`assembledOutputPoliciesValidateExactBoundDecimals`) covers decimals originating
  in bound child results, not model action JSON. Its model-owned contribution is a
  string. The test at line 1359 (`retriesFinalResponseUntilOutputSchemaPasses`) covers
  correction with string-valued results, not numeric fidelity. These are inspected
  tests, not newly run acceptance results.

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

Provider mapping, strict tools, binding projection, and runtime metadata remain
owned by TBD-07/08/10/13. Full framework dependency validation, applicable adapter
fixes, and provider acceptance tests remain implementation work; resolving TBD-06
does not claim that implementation or verification is complete.

Source inspection found concrete RC2 transport gaps: Anthropic's tool mapper
omits root constraints such as additionalProperties; Gemini's
output/tool paths still pass through a narrower SDK schema representation despite
raw JSON-schema fields in the SDK. These are adapter findings, not reasons to impose
global vocabulary limits. Binding projection, runtime references/attachments, and
provider keyword omissions need bounded design decisions under the related open TBDs.
No production code or dependency was changed, no model calls were run, and no
recommendation or other TBD was accepted or resolved by this research.

Executable follow-up, November 10, 2026: [verification summary](standard-json-schema-research.md#executable-verification-follow-up--november-10-2026)
and [reproducible probe / evidence / handoff](../../../docs/maintainers/probes/tbd-06/README.md).
Twenty isolated tests pass on each of Boot 4.2.0-RC2 / Jackson 3.1.8 and Boot
4.1.0 / Jackson 3.1.4 with Java 21 and Spring AI 2.1.0-RC2. NetworkNT 3.0.9 is
viable for the representative fixtures, with explicit precision, regex, and limit
configuration.
Actual mock HTTP captures confirm Anthropic root-schema losses and Gemini rejection,
conversion and silent field loss; direct SDK raw-schema controls preserve the fixtures.
Anthropic native outputConfig works when effort is deliberately preserved. Gemini's
client-level fixed-output extraBody path does not solve per-request schemas and
pre-HTTP tool failures. These tests establish serialization, not hosted enforcement.
**TBD-06 policy decisions are resolved; implementation acceptance remains outstanding.** Confirmed Spring AI schema losses are implementation blockers for the
affected paths, not reasons to narrow the standard schema globally. See the
[upstream blocker register](standard-json-schema-research.md#spring-ai-implementation-blockers-and-upstream-contribution-handoff)
for reproduction code, request evidence, candidate fixes, and acceptance gates. Prefer
upstream corrections or a suitable fixed release. Do not claim complete native
schema support until these paths pass fidelity tests; the working Anthropic output
configuration path must also preserve reasoning effort. No upstream issue/PR was
filed by this documentation update.

### TBD-07: Selecting provider differences without a capability registry

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

Future implementation tickets **must carry these requirements explicitly**, together
with the exact per-driver rule entries and acceptance cases. A link to this section
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

The bounded v1 mapping work is approved; exact evidence-backed rule entries must be
prepared in the later ticket-writing context. TBD-08 through TBD-11 are separately resolved below; this decision alone does not
assert native support for untested models/routes.

**Responses schema decimal fidelity — agreed audit correction, November 12, 2026:**
preserve numeric values when the Responses adapter decodes output and tool schema
JSON for SDK serialization. The pinned RC2 adapter rounds decimal bounds and enum
members before sending either schema. This violates the unchanged-contract requirement
above; it is not an approved provider omission. Existing YAML, callback, step-action,
output-validation and TypeSafe precision corrections do not repair this outbound boundary.

The [isolated probe](../../../docs/maintainers/probes/responses-schema-precision/README.md)
captures maximum 1.0000000000000001 as 1.0 and a long fractional minimum/enum member
as a rounded value. A candidate private SDK-compatible schema mapper with Jackson 2
USE_BIG_DECIMAL_FOR_FLOATS preserves both wire schemas without changing the SDK singleton.
Prefer a focused upstream correction or verified fixed release; do not ship the probe's
copied upstream class as a Loomspan override, add another adapter, mutate shared mappers,
stringify numbers or weaken constraints. Preserve numeric value, not lexical spelling.

On each Boot 4.1.0 and 4.2.0-RC2 platform, six baseline defect/control cases and six
candidate numeric cases passed; three existing schema/options and multi-round replay
regressions also passed with the candidate. Cases cover nested bounds/enums, fractional
and exponent values, changed per-call schemas and large-integer/exact-decimal controls.
Passing baseline tests reproduce the loss. Candidate success is isolated adapter proof,
not Framework implementation, full numeric conformance or live acceptance. No released
fix or upstream submission was verified. Production migration remains pending.

Tickets must retain the versioned reproduction, exact two decoding sites, candidate
mechanism and preservation controls. Add precise actual-request assertions for output
and tool schemas through the migrated Framework path; default floating-point comparisons
can conceal the defect. Retain strictness, options, canonical schemas, isolation and
tool/reasoning replay. No integer decoding change is justified by the tested controls.

Evidence anchors:

- Retained upstream ResponsesRequestBuilder.java:80, :228 and :262 under
  docs/maintainers/probes/responses-schema-precision/upstream/ show the shared mapper
  and the output/tool decoding branches respectively.
- The historical docs/maintainers/probes/tbd-06/src/test/java/AdapterTest.java:22,
  :44 and :51 use default-mapper equality and a fixture without precise decimal
  constraints. They prove their tested shapes, not numeric schema fidelity.
- The new baseline SchemaWirePrecisionTest.java:15 captures the original failure and
  SDK controls; candidate SchemaWireFixTest.java exercises both real wire schemas.
  Source hashes, exact commands, retained requests and reports are in the probe README.

### TBD-08: Native strict tool-argument enforcement

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

**Step-action null toolArguments — agreed audit correction:** reject explicitly null
toolArguments in a CALL_TOOL action before it becomes an empty map. Preserve valid {},
permitted null fields inside argument objects, existing omission behavior and bounded
step-action correction. Detect explicit null at decoding while its distinction from
omission is still available; do not globally reject internal null maps or change public
invocation behavior. Ordinary callback ingress fixes do not cover this separate path.

Add a focused step-engine test proving a CALL_TOOL with toolArguments:null invokes no
tool, enters existing bounded correction, and executes once after a valid {} correction
for a tool accepting empty arguments. Cover correction exhaustion with no side effects;
retain nested-null and omitted-argument behavior controls. A local decoding/validation
probe accepted both explicit null and {} against a closed empty-object contract.
Dispatch normalization was source-inspected, not exercised in a full engine reproduction.
Implementation, focused engine verification and live acceptance remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:1223`
  (`parseStepAction`) binds JSON to StepAction, collapsing explicit null and omission;
  `src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java:161`
  (`validateRequiredToolArguments`) substitutes Map.of() before ownership checks and
  does so again at line 170 for schema validation. The engine substitutes Map.of()
  at line 1179 before invocation.
- `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:3382`
  (`preservesExplicitNullArgumentsInTheStepLoopToolPath`) supplies {"rawText":null},
  a valid object, rather than a null argument root. Preserve this nested-null behavior;
  it does not establish enforcement of the object-root requirement.

### TBD-09: Unsupported schemas/models and failure timing

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

### TBD-10: Output-binding projection and fully bound outputs

**Status: Resolved — preserve existing binding behavior; migrate its schema representation.**
Do not introduce a new binding system or expand path capabilities. Use the existing
projection/composition authority with standard JSON Schema trees and the shared
validator in place of legacy manifest-schema classes and validators.

#### Required implementation behavior

- Preserve the full canonical output contract. Derive a separate model-contribution
  schema by excluding framework-bound destinations and adjusting required ancestors
  and nullability as existing projection does. This ownership projection is distinct
  from provider adaptation under TBD-07. Do not weaken the full final-output contract.
- Preserve object-only JSON Pointer traversal: tokens select named object fields;
  numeric tokens are literal object keys, not array indexes. Transfer whole arrays
  or objects as bound values. Do not add array-element traversal, wildcard selection,
  root-destination binding, or automatic wrapping/conversion.
- Apply the agreed standard exact property-name semantics consistently across schema
  lookup, path matching, ownership checks, compatibility checks, and composition.
  Existing case-insensitive matching is intentionally removed in this breaking
  migration; do not retain it through aliases or normalize user field names.
- Preserve loading/dependency checks for binding source/destination structural
  compatibility, producer relationships, and existing path/overlap restrictions.
  Static checks do not prove future runtime values; do not build a general JSON Schema
  implication solver. Flag any existing check that cannot be faithfully migrated.
- Preserve invocation-local source selection and snapshots, accepted-plan/producer
  identity checks, owning-frame/lifecycle checks, and bound-value provenance. Validate
  selected values against their destination contracts before the output model call.
  Internal destination-subschema validation may validate an array/scalar value; the
  root-object rule applies to complete declared contracts, not every subschema check.
- Reject model attempts to supply bound destinations even when values are equal to
  selected values or null. Validate the model contribution against its projected
  schema before inserting bound values. Preserve nested ancestor construction and
  requirements for unbound siblings; a nested binding must not erase those requirements.
- Compose exact selected values with the valid model contribution, then validate the
  complete result against the original canonical schema. Preserve existing complete-
  output policy checks and provenance/assembly diagnostics. Do not rewrite values to
  make them fit, or ask the model to repair framework-owned bound values.
- Preserve detection of whether model contribution is needed. If none remains,
  assemble from bindings and skip final-output model generation. Required accepted
  work must still complete; preserve source-availability checks and do not skip
  planning/tasks/tools merely because final synthesis is unnecessary. An open object
  can still have model-owned extra properties even when every declared field is bound.
- Carry all applicable approved v1 constraints, including new bounds/formats, through
  projection and local validation. Send the projected schema through the existing
  provider driver using only TBD-07 mappings. Preserve the canonical and projected
  contracts independently of the request copy. No competing projection engine or
  binding API expansion is authorized.

#### Mandatory ticket handoff and acceptance

Tickets must explicitly reproduce these behaviors and the intentional change to
exact property matching. Identify the existing implementation paths to migrate:
`OutputBindingProjection`, `OutputBindingComposition`, dependency validation,
`OutputSchemaCallAdvisor`, and both mission execution engines. Inventory their
existing tests and retain equivalent coverage in the standard-schema implementation;
do not treat a new validator's successful schema validation as proof of ownership
or execution correctness. No compatibility layer for the legacy schema classes.

Required acceptance cases include partial and fully bound outputs; nested bound
fields with required unbound siblings and nullable ancestors; whole-array/object
values; literal numeric property names; exact-case mismatches; model overrides with
equal/null values; invalid bound values; invalid model contributions; and complete-
output validation after composition. Include open objects that still permit model
contribution, unavailable/mismatched producers, changed plans, and required work
completion before skipping synthesis. Assert actual provider requests contain the
projected contract subject only to approved mappings, original schemas remain
unchanged, and existing bounds/formats are enforced at the appropriate boundaries.

**Omitted additionalProperties and synthesis — agreed audit correction:** migrate
OutputBindingProjection's model-contribution decision to standard object openness:
omitted additionalProperties leaves an object open, just as explicit true does.
Apply this at nested object ancestors as well as the root; preserve whole-subtree
ownership when the entire object is bound. Add omitted/true/false projection tests
and focused standard/step-engine tests proving final generation remains enabled when
model-owned space remains, while closed fully bound outputs still skip synthesis.
Retain required-work and source-availability checks. A local projection probe returned
modelContributionRequired=false for omission and false, but true for explicit true;
existing tests distinguish explicit true/false without establishing standard omission
behavior. This is pending migration work against the agreed openness policy, not an
existing defect under legacy closed-by-default output semantics. Implementation and
focused verification remain pending.

Implementation evidence for this correction (repository-relative paths; line numbers
are audit-time anchors, with symbols retained for navigation after edits):
- `src/main/java/ai/loomspan/internal/outputschema/OutputBindingProjection.java:43`,
  `hasSpace`: line 44 recognizes only explicit true as open; line 49 recurses into
  partially bound objects.
- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:99`
  skips final generation when `composition.modelContributionRequired()` is false;
  `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:379`
  derives the corresponding decision from the same projection.
- `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java:33`,
  `fullBindingAndOpenOrOptionalResidualSpace`, covers explicit closed/open objects;
  its `schema` helper at line 19 defaults to false, so omission is not exercised.

**Binding-validation openness — agreed audit correction:** remove the legacy
insertion of additionalProperties: false into output-schema copies used for binding
declaration validation. Omission must remain open for both producer and destination
schemas, including nested objects. Preserve existing explicit-false path rejection,
structural compatibility checks and runtime obligations for unknown/open branches;
do not infer future producer fields or expand binding path syntax.

Add focused publication-validation cases comparing omitted/true/false openness on
producer and destination objects, including nested paths and whole-object compatibility.
Omitted and true must have equivalent openness behavior; false must still reject
statically impossible paths/transfers. Retain runtime missing-source and invalid-bound-
value rejection, with no model fallback. A local helper probe of default insertion
followed by source-path selection rejected omission and false but accepted true at
root and nested objects. This is pending migration work under standard openness, not
a defect under legacy closed-by-default semantics. Full publication verification and
implementation remain pending; no provider capability is inferred.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:233`
  (`applyOutputDefaultOpenness`) recursively inserts false. Calls at lines 215 and
  229 apply it to producer schemas and destination metadata respectively;
  `selectBindingSchema` at line 243 then rejects undeclared closed-object paths.
  `checkBindingCompatibility` at line 265 also uses openness for object transfers.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:90`
  (`validatesOutputBindingPathsTypesAndProducerCountsAgainstCompleteGeneration`)
  covers closed source/destination rejection and an explicitly open producer at
  line 138, but not standard omitted-openness equivalence.
- `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java:49`
  (`missingAndInvalidSourcesFailBeforeModelButNullableNullSucceeds`) and
  `src/test/java/ai/loomspan/integration/DeclaredOutputBindingsOrdinaryIntegrationTest.java:111`
  (`dynamicBoundTypeMismatchFailsBeforeProviderAndWithoutAssembly`) supply runtime
  rejection controls to preserve. These are inspected tests, not new acceptance runs.

**Numeric enum equality — agreed audit correction:** use exact numeric-value
equality when comparing numeric enum members in binding compatibility checks, retaining
ordinary type-sensitive equality for other scalar values. Do not use floating-point
rounding, epsilon comparisons or string coercion. Preserve the existing overlap check
and runtime bound-value validation; do not introduce a schema implication solver.

Add focused publication-validation tests for equivalent spellings (1, 1.0, 1e0 and
1.00), genuinely different high-precision numbers, disjoint enums, and numeric versus
string values. Retain existing string-enum intersection and runtime rejection controls.
A local helper probe using lossless decimal parsing rejected 1 -> 1.0, 1 -> 1e0 and
1.0 -> 1.00 as statically incompatible. These are pending migration cases: legacy
catalog schemas restrict enums to strings, so the probe does not establish a currently
reachable numeric-enum publication defect. Implementation, migrated publication tests
and live provider acceptance remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:280`
  (`checkBindingCompatibility`) compares enum members with JsonNode.equals; the
  declaration-validation caller at line 218 can reject before runtime validation.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:938` and line 996
  restrict legacy output/input enums to string schemas. The v1 scalar-enum scope
  removes that restriction; replacing the value validator alone does not fix the
  separate binding compatibility comparison.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:36`
  (`nativeSchemasEnterExistingBindingCompatibilityAndCapturedCatalogs`) exercises
  overlapping/disjoint string enums and dynamic producer metadata, not numeric
  equivalence. `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java:49`
  (`missingAndInvalidSourcesFailBeforeModelButNullableNullSucceeds`) supplies runtime
  rejection controls. These are inspected tests, not newly run acceptance results.

If migration exposes an existing behavior outside the approved v1 feature scope or
requires a new binding policy, flag the concrete case for discussion. Do not let the
implementing LLM invent path semantics, value coercion, compatibility shims, or a
schema-transformation framework. Correction history remains owned by TBD-11.

### TBD-11: Correction history, tool availability, and side effects

**Status: Resolved — two targeted ordinary-advisor changes agreed November 11, 2026.**
Preserve existing correction behavior elsewhere. This is not approval to redesign
planning, step-loop execution, retry ownership, or the tool loop.

Source/test inspection found that ordinary output-schema correction reconstructs
baseline instructions plus the latest candidate and feedback; linter/evidence
advisors append feedback to their existing prompts. These builders do not explicitly
retain the completed tool exchanges from the downstream tool loop. Schema/evidence
feedback already says not to call tools again, but ordinary correction retains request
options and re-enters the downstream chain without a correction-phase execution
prohibition. The canonical tool manager checks bound/authorized selections, not a
correction-phase ban. These are source-inspection findings, not a completed end-to-end
reproduction. The step-loop final phase already rejects CALL_TOOL via validateFinal.

#### Change 1: enforce tool-free ordinary final-output correction

For ordinary output-schema, linter, and evidence correction, enforce no tool execution
in code once final-output correction begins. Do not rely on prompt instructions.
Remove executable tool availability from the correction request and enforce the
boundary before dispatch if the provider nevertheless emits a tool call. Report that
violation through existing failure mechanisms without executing any returned tool,
adding another recovery loop, or rerunning earlier work. Preserve authorization checks
for ordinary tool execution; they remain independent of this phase restriction.
Verify the provider request and execution boundary rather than assuming a copied
options object or SDK helper cleared callbacks/tool defaults correctly.

#### Change 2: retain completed tool context for ordinary correction

Make completed tool exchanges/results available in the actual correction request,
with the original instructions/input, latest rejected candidate, and applicable
validation feedback. Preserve tool-call IDs, result associations, and provider-required
replay items/metadata as governed by the existing stateless-history requirements.
Do not reduce tool context to an invented summary or reconstruct protocol history
from final composed text. Use the existing Spring AI tool-loop/history boundary;
do not create a second loop or cross-invocation memory store. If Spring AI cannot
expose the necessary history faithfully, flag that concrete integration blocker.

Keep the existing latest-candidate policy for schema corrections: do not accumulate
all rejected answers or duplicate completed tool exchanges on subsequent corrections.
For linter/evidence paths, retain existing feedback behavior while making completed
execution context available; do not use this task to redesign their prompt policy.
The scope is carrying completed work forward, not authorizing further work.

#### Explicit preservation boundary

Preserve planning correction, step-loop final correction, existing correction counts,
retry ownership/advisor ordering, binding source snapshots, and their existing failure
semantics. Do not change linter exhaustion behavior incidentally. Preserve ordinary
tool-argument feedback and normal tool continuation outside final-output correction.
Transport retries remain separate. No new public API, settings, strict-tool mode,
rollback, or general retry mechanism is authorized. Previously completed side effects
are neither repeated nor rolled back by these two changes.

#### Mandatory ticket details and acceptance tests

Tickets must carry both changes, the source-inspection evidence qualifier, and the
preservation boundary explicitly. Name the affected ordinary schema/linter/evidence
advisors, Spring AI history integration, and tool dispatch enforcement. Do not leave
the implementing LLM to invent a new correction architecture or change unrelated
semantics. Start with a focused integration reproduction; unit tests using a mocked
advisor chain alone do not prove tool-history retention or side-effect prevention.

Capture actual outgoing requests and callback execution counts for each affected path:

- An initial model turn calls a side-effect sentinel tool once, receives its result,
  then produces a final response needing correction. The correction receives the
  completed tool exchange/result and applicable feedback, with no executable tools.
- A correction response attempting a tool call cannot invoke the callback, even if
  authorized in the original request; fail without side effects or fallback.
- On second/third schema corrections, retain the latest candidate and completed
  history without accumulating stale candidates or duplicating tool exchanges.
- Preserve required replay metadata and call/result pairing across the supported
  integrations; prove serialization, not merely an internal message-list shape.
- Retain fixed bound values/provenance across correction and validate the full
  assembled output. No cross-invocation history/snapshot leakage.
- Existing planning, step-loop final-response rejection of tools, ordinary tool
  continuation, budgets/exhaustion, and transport retry tests continue to pass.
  No new behavior in those paths is authorized by this decision.

Evidence anchors (source/test inspection, not tests rerun during this backfill):

- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:243` (`private Prompt buildRetryPrompt`) rebuilds baseline plus candidate/feedback and keeps baseline options; `src/main/java/ai/loomspan/internal/linter/LinterCallAdvisor.java:173` (`private Prompt appendHint`) and `src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceContractCallAdvisor.java:115` (`private Prompt appendHint`) append hints before re-entering the chain.
- `src/main/java/ai/loomspan/internal/springai/CanonicalToolCallingManager.java:36` (`@Override public ToolExecutionResult executeToolCalls`) checks bound selections without a correction-phase prohibition; `src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java:38` (`static StepValidationResult validateFinal`) already rejects final-phase CALL_TOOL.
- `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java:91` (`void rebuildsThirdAttemptFromBaselineWithOnlyLatestCandidateAndCorrection`) covers latest-candidate behavior; `src/test/java/ai/loomspan/internal/runtime/evidence/EvidenceContractAdvisorAdditionalTest.java:31` (`void unsupportedRequiredClaimFailsAfterToolFreeRetriesAndPreservesExpressionDiagnostics`) uses a recording chain and asserts instructions, not dispatch prohibition.
- `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:288` (`void semanticRetriesWrapOneObservableAi2ToolLoop`) explicitly expects two callbacks across linter correction. Update that legacy expectation under the agreed no-tools correction policy while retaining physical-attempt accounting. `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:361` (`void outputSchemaRetryReusesCompletedToolResultWhenCorrectionReturnsJsonDirectly`) scripts a direct final answer and asserts one callback plus candidate/feedback in the retry request; despite its name, it does not assert completed tool-result content in that request.

#### Completed-tool-history integration — agreed November 12, 2026

The [preserved investigation/probes](../../../docs/maintainers/probes/tbd-11-history/README.md)
refine the implementation approach without changing either TBD-11 policy. Allocate one
private holder per ordinary SpringAiModelInteraction.call and pass the same reference
through existing advisor context and tool context. Do not store history on cached
clients/managers or share it across interactions. Preserve planning and step behavior.

After successful CanonicalToolCallingManager delegate execution, retain the appended
Message objects from ToolExecutionResult.conversationHistory, including assistant parts
and associated tool results. Keep completed exchanges separate from baseline instructions,
rejected candidates and feedback. Reuse the already agreed canonical-message parts repair;
the current text/tools/media reconstruction is not faithful RC2 replay.

Retain the latest raw provider AssistantMessage at ProviderAttemptCallAdvisor's returned-
response boundary before output composition, without adding retries or usage accounting.
Do not reconstruct provider history from the application-facing composed result.
The three existing correction advisors use one private request helper. Preserve their
baseline instructions/media, existing feedback behavior, budgets and exhaustion policy.
Explicitly track the inserted replay segment in request context so nested/repeated
corrections replace it with completed exchanges and the latest raw candidate instead of
duplicating history or retaining stale candidates. Do not infer segments from text.

Entering final repair sets a call-local terminal flag. Clear callbacks on copied typed
request options and enforce the flag in CanonicalToolCallingManager before delegation.
Keep normal canonical authorization intact. Actual serialized requests must have no
executable tool availability, including defaults; prompt guidance alone is insufficient.
No additional tool loop, public API, global mapper/context or persistence is authorized.

Evidence: an augmented current factory/advisor/default-tool-loop harness reproduces
missing tool context in schema and evidence corrections, and two tool executions across
linter correction. Its physical-attempt assertions pass. This is not the complete
assembler/canonical-manager pipeline. Five RC2/Boot 4.2.0-RC2 mock-HTTP mechanism tests
passed: ordered two-round replay through repeated corrections, a correction tool-call
guard with model-default callbacks, separate sequential-call isolation, nested synthetic
validators without duplicate replay, and raw-reply preservation through synthetic
composition. These use synthetic correction components, not migrated Framework advisors.

Implementation and full acceptance remain pending. Retain the mandatory tests above,
including real three-advisor/combined paths, all three adapters, concurrent/nested-call
isolation, attachments, bindings/snapshots, canonical names/parts, cancellation,
exhaustion and physical-attempt/usage controls. No live capability is established.
Tickets must carry the exact integration seams, replay-segment ownership and raw-versus-
composed distinction, plus these evidence limitations and reproduction commands.

Evidence anchors: SpringAiModelInteraction.java:41/:64/:67 creates each interaction and
binds tools/advisor context; CanonicalToolCallingManager.java:70 returns the delegate's
history; ProviderAttemptCallAdvisor.java:103–112 owns returned response/usage;
OutputSchemaCallAdvisor.java:125 composes application-facing text and :243 rebuilds
schema corrections; LinterCallAdvisor.java:116 and EvidenceContractCallAdvisor.java:94
retain their own feedback policies. All are under src/main/java/ai/loomspan/internal/
in their existing springai, chat, outputschema, linter or runtime/evidence packages.
The durable probe README identifies copied harnesses, exact commands and retained captures.

### TBD-12: Refusals, incomplete responses, and partial tool calls

**Status: Resolved — three targeted changes agreed November 11, 2026.** Preserve
existing behavior elsewhere. Source inspection identified missing explicit status
checks in returned-response handling and tool dispatch, and missing completion/status
metadata in the advisor response payload. These findings are not end-to-end reproduced
failures for every SDK: some SDKs may already throw for particular conditions.

#### Change 1: classify explicit refusal/incomplete status before downstream use

At the existing provider-response boundary, inspect explicit provider response and
generation status/refusal metadata before accepting output, passing it to output
correction, planning parsing, or allowing tool execution. An explicitly refused or
incomplete generation is a terminal failure, even when its text is valid JSON or
matches the schema. Do not request schema/lint/evidence correction, automatically
continue generation, increase token limits, or retry the unchanged generation under
transport retry policy. Do not infer refusal/incompleteness from response prose.

Each integration must document exact metadata/status mappings and the SDK behavior
that exposes them. Do not equate a normal complete tool-call turn with an incomplete
response merely because no final answer exists. Normal tool continuation is preserved.
Do not classify all provider failure statuses as transient; existing evidenced error
classification remains authoritative. If the SDK/adapter discards required metadata,
flag a concrete integration blocker rather than guessing from text or adding a second
transport. This decision does not authorize a new policy for undocumented statuses.

#### Change 2: prevent dispatch from explicitly incomplete responses

Check response completeness before dispatching any tool call from that response.
Execute none when the containing response is explicitly incomplete, even if one or
more individual argument objects parse and validate. Keep canonical authorization,
input validation, and normal complete tool-call execution unchanged. Refusal handling
above also terminates before dispatch. Do not attempt partial-batch execution, recovery
of a complete-looking subset, or compensation for work performed in earlier turns.

#### Change 3: retain response evidence and usage on rejection

Extend existing response/failure diagnostics to retain explicit status, finish/stop
reason, refusal/incomplete details when supplied, and returned content/tool-call
information. Preserve diagnostic fidelity and the existing access boundaries. Record
returned usage exactly once even when the response is rejected; do not throw before
capturing the response/usage or double-count through both success and failure paths.
Make the terminal failure distinguishable from successful output and ordinary schema
mismatch in existing outcomes. Do not create a new public exception hierarchy,
observability subsystem, or configuration surface as an incidental implementation.

#### Preservation boundary and mandatory ticket handoff

Preserve cancellation/interruption, transient transport retry classification/backoff,
normal tool continuation, authorization, existing OpenRouter error-completion handling,
and ordinary correction budgets. Translate those existing behaviors to the migrated
protocol where necessary without changing their policy. HTTP success alone does not
establish generation success. Retain partial content for diagnostics, never as an
accepted result. No rollback of previously completed actions is promised.

Tickets must explicitly include all three changes, integration-specific status
mappings/evidence, exact placement before downstream validation/dispatch, usage
ownership, the source-inspection qualifier, and the preservation boundary. The
implementing LLM must not invent status classifications, retries, text heuristics,
or recovery policies. Start with captured-response integration reproductions for
all three adapters; SDK-specific thrown exceptions and normally returned statuses
must both be represented where applicable.

Required tests:

- Explicitly incomplete response containing schema-valid JSON is not accepted and
  does not enter schema/lint/evidence correction or planning acceptance.
- Explicit refusal produces a terminal failure without correction or unchanged-
  generation retries, retaining original content and refusal details.
- Incomplete response containing otherwise valid, authorized tool calls invokes zero
  callbacks, including a multi-call response with complete-looking arguments.
- Complete tool-call turns still execute/continue normally; lack of final text alone
  is not a rejection reason. Plain text resembling refusal is not classified by prose.
- Rejected returned responses preserve status/content diagnostics and account for
  returned usage once; no phantom attempts or duplicate success/failure accounting.
- Existing cancellation, backoff, transient retries, OpenRouter error-completion,
  authorization, and ordinary tool-loop tests remain valid without policy changes.

Evidence anchors (source/test inspection, not tests rerun during this backfill):

- `src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:103` (`ChatClientResponse linkedResponse =`) records a returned response as successful and returns it without explicit completeness classification; `src/main/java/ai/loomspan/internal/chat/ProviderAttemptCallAdvisor.java:189` (`private Map<String, Object> responsePayload`) records message payloads without generation completion metadata.
- `src/main/java/ai/loomspan/internal/springai/CanonicalToolCallingManager.java:36` (`@Override public ToolExecutionResult executeToolCalls`) validates selections before delegating but has no explicit containing-response status check.
- `src/test/java/ai/loomspan/internal/springai/CanonicalToolCallingManagerTest.java:88` (`void rejectsEveryInvalidOrUnboundSelectionBeforeAnyCallbackExecutes`) proves selection rejection, not incomplete-response rejection. `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:207` (`void recordsEachAdvisorRetryAsOnePhysicalAttemptInTheSameRetrySequence`) covers attempt accounting; `src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java:946` (`void openRouterRetryableErrorCompletionRecoversWithTwoVisibleEndpointCalls`) is the existing provider error-completion regression to preserve. These do not prove all three adapters expose/refuse incomplete responses as required.

#### Status/usage integration and adapter prerequisites — agreed November 12, 2026

The [preserved probes](../../../docs/maintainers/probes/tbd-12-status/README.md) refine
the three agreed changes above; they do not introduce new status policy. A Framework-
only classifier cannot recover evidence discarded by the selected adapter. Prefer
narrow upstream corrections or a verified fixed release within the existing dependency
work: retain Gemini promptFeedback and candidate finish details in response/generation
metadata, and retain the original Responses failed-lifecycle response as accessible
exception evidence. Do not ship copied research classes, add a second transport, infer
status from empty output/prose, or weaken the evidence requirement.

Use one internal driver-specific explicit-status classifier at ProviderAttemptCallAdvisor,
before downstream correction, planning parsing or tool dispatch. Read the actual pinned
metadata: Responses generation status/refusal/incomplete reason; Anthropic finishReason
and native anthropic-response; Gemini candidate finishReason plus preserved prompt feedback.
Normal complete tool turns remain valid. Mapping a status does not authorize a new policy
for undocumented values or vendor-recommended continuation/fallback. The already documented
Responses failed-lifecycle error path retains evidenced error-code retry classification;
failed is not synonymous with refusal/incomplete or automatically transient.

For normally returned responses, retain relevant status/details/content/tool evidence and
use existing ModelUsageExtractor and session accounting once, outside the transport retry
catch. Record a failed provider outcome for terminal rejection instead of unconditional
success; link failure to the same physical attempt and throw without correction or transport
retry. MODEL_RESPONSE_RECEIVED records receipt, not successful acceptance. Reuse the same
decision in CanonicalToolCallingManager before any delegation; neither that guard nor outer
validators own usage. Preserve authorization and the separately agreed parts-preservation fix.
For SDK-thrown responses, account once for actual usage made accessible by the adapter repair,
without fabricating usage for transport failures or changing existing error retry policy.
Preserve quota/cancellation semantics, including failures raised by usage accounting itself.

Evidence: three RC2/Boot 4.2.0-RC2 tests passed with 15 mock HTTP fixtures. Responses
incomplete text/two-tool replies and explicit refusal retain metadata and usage; a failed
lifecycle fixture loses its supplied body/usage in the code/message-only exception.
Anthropic's six tested stop reasons retain finishReason, native response and usage. Gemini
MAX_TOKENS/SAFETY/STOP retain finishReason; a blocked-prompt fixture returns empty generations
and usage while losing its explicit promptFeedback.blockReason. Fixture token counts do
not establish live billing. Current Framework harnesses separately reproduce incomplete
output entering linter correction and two authorized callbacks executing on LENGTH. Existing
exact-usage assertions, invalid-selection rejection and OpenRouter error-completion retry
regression pass. These are not one complete migrated production pipeline.

Implementation remains pending, including verification of the candidate adapter repairs.
Retain all acceptance cases above across three real migrated pipelines: schema-valid
incomplete output, explicit refusal/prose controls, multi-call zero dispatch, normal tool
continuation, planning rejection before parsing, exactly-once usage/outcomes, missing/zero
usage, quota/cancellation collisions and existing transport/backoff behavior. No streaming,
comprehensive SDK-error matrix or live capability is established. Tickets must carry exact
metadata mappings, adapter prerequisites, accounting ownership and these limitations.

Evidence anchors: ProviderAttemptCallAdvisor.java:103/:189 records success and message-only
payloads; DefaultSessionUsageService.java:59 owns response usage and quota enforcement;
CanonicalToolCallingManager.java:36 is the dispatch boundary (all under
src/main/java/ai/loomspan/internal/ in chat, runtime/usage and springai respectively).
Preserved upstream files under docs/maintainers/probes/tbd-12-status/upstream/:
ResponsesItemMapper.java:525/:776/:815 maps status, usage and failed exceptions;
OpenAiResponsesChatModel.java:137 throws before response mapping;
AnthropicChatModel.java:1183/:1413 retains stop reason/native response;
GoogleGenAiChatModel.java:618/:743/:871 maps candidates/finish reason but omits feedback.
The probe README and investigation.json retain commands, fixtures and test limitations.

### TBD-13: Framework input features and generated schemas

**Status: Resolved as policy; implementation and acceptance remain pending.**
Attachment inputs, runtime references, Java declaration authority, typed-map and
unconstrained inputs, recursive-type rejection, output evidence metadata, effective
schema display and TypeSafe boundaries are agreed below. Preserve existing behavior
except the explicit changes recorded here. TBD-14 records the agreed implementation split.

#### Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026

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

#### Deferred Java declaration edge cases — scope reset November 12, 2026

Keep only this overview for possible follow-up:

- Optional input wrappers.
- Jackson polymorphic inputs (`@JsonTypeInfo`).
- Array-shaped DTO inputs (`@JsonFormat(shape=ARRAY)`).
- Delegating creator inputs (`@JsonCreator`).

These are outside the migration implementation and acceptance scope. This scope reset
supersedes the earlier registration-restriction agreements for the first three items;
the creator investigation is also deferred. It does not assert that these declarations
are supported. Detailed findings, implementation proposals, acceptance requirements and
probe artifacts have been removed deliberately. Do not reconstruct or expand them in
routine migration audits. Revisit only when a concrete application requirement or a
migration-critical acceptance case establishes a need, with explicit scope agreement.
The map-key, open-property and JsonUnwrapped corrections remain in scope.

#### Retained generated-input corrections

**Declared map key types — registration restriction agreed November 12, 2026:**
Reject generated Java map declarations whose resolved key type requires conversion of
JSON property names. Preserve String-keyed maps and raw/Object-keyed maps accepting
string keys; do not convert property names or introduce property-name schema features.
Use the agreed effective Java-type traversal, including nested DTOs, collection items,
map values and inherited generic bindings. Report the method, parameter/property path
and unsupported key type at registration, before publishing a misleading contract.
Runtime non-string-key rejection still applies separately to incoming values.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-map-key-types/README.md)
registers Map<Integer,String>, validates two distinct string keys "1" and "01", and
invokes the method with only one integer-keyed entry: the second value wins after
binding. A nonnumeric string key also validates but fails during Java binding. The
isolated registration-check candidate rejects direct and nested integer-key declarations;
String-keyed, raw and Object-keyed controls preserve both string keys. Four existing
generator and two binder test methods pass against it. This is actual local registration,
validation and invoker evidence, not public SkillTemplate, NetworkNT or live capability.

Implementation and full acceptance remain pending. Add registration tests for other
numeric, enum and custom key types, inherited resolved generics and full diagnostics;
retain valid nested maps and marked resource-map values. Inspect effective application
mapper behavior where custom key deserializers are configured; the probe does not
establish fidelity for arbitrary custom mapper configuration. Reuse existing registration
and binding machinery rather than designing a general serializer-to-schema system.

Evidence anchors (audit-time lines and symbols):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:64`
  (`schemaFor`) emits map value schemas without checking key types.
- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:596`
  (`materializeValue`) preserves keys; `:567` (`convertArgument`) converts to the
  declared JavaType, including key conversion.
- `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:70`
  (`typedMapSchemasRemainConstrainedAfterObjectBroadening`) tests typed values with
  String keys. Binder controls at `SkillMethodBeanPostProcessorTest.java:118` and
  `:154` do not cover declared numeric-key collisions. The runtime-map-key and
  open-property corrections do not cover this all-string-key counterexample.

**JsonUnwrapped declarations — agreed November 12, 2026:** reject enabled
@JsonUnwrapped in generated Java input contracts at registration, using effective
binding-mapper introspection and diagnostics identifying the method, parameter and
property path. Preserve disabled annotations and ordinary nested DTOs. This is an
explicit bounded declaration restriction: these annotated DTOs cannot be used directly
as generated SkillMethod inputs. Do not ignore the annotation, rewrite values or add
a general flattening/serializer system. Supporting prefixes/suffixes, collisions,
requiredness and runtime-reference mapping would require a separate design decision.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-unwrapped/README.md)
reproduces a published nested contract versus flattened Jackson binding: flattened
input is rejected, while schema-valid nested input executes with its supplied leaf
lost. A temporary rejection candidate preserves disabled-annotation/ordinary DTO
controls; four existing generator and two binding test methods pass. Existing naming,
enum, UUID, generic and resource corrections do not handle structural unwrapping.
This is local registration/validation/invoker evidence, not full public invocation,
migrated NetworkNT/provider or live acceptance. Implementation remains pending.

Add registration rejection cases for fields, getters, effective application mixins,
prefix/suffix settings and nested/items/map locations, with method/path diagnostics
and no publication/invocation. Preserve disabled annotations and existing DTO/binding
coverage. Evidence anchors: LoomspanMethodInputSchemaGenerator.java:114/:120/:121
introspects then emits/requires a nested named property; SkillMethodBeanPostProcessor.java:567
binds through the application mapper, with bean-map materialization at :623. Files are
under src/main/java/ai/loomspan/internal/serialization and core. Existing generator and
postprocessor tests do not cover unwrapping. The separate read-only-property probe found
correct exclusion by creation introspection; no read-only-property correction is selected.

**UUID input contracts — agreed November 12, 2026:** map java.util.UUID to the
existing generated string scalar shape, including nested properties, collection items
and map values. Preserve descriptions, requiredness, surrounding contracts, method
signatures and existing Java conversion failures. Do not add format assertions or a
general serializer-to-schema system incidentally. Existing naming/enum/generic/resource
corrections do not repair this scalar classification.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-uuid/README.md) uses actual
registration, validation and registered invokers: UUID strings fail the current published
object contract but bind successfully; advertised leastSignificantBits/mostSignificantBits
objects validate but fail binding before the method body. Nested record/list/map cases
show the same mismatch. An isolated string-mapping candidate fixes the tested shapes;
four existing generator and two binding tests pass. This is an existing local mismatch,
not a new validator defect. Full public-invocation, migrated-validator, adapter and live
acceptance remain pending, as does production implementation.

Add focused registration/validation/invocation tests for those shapes, unchanged
requiredness/descriptions/sibling validation and malformed binding controls. Evidence:
LoomspanMethodInputSchemaGenerator.java:93/:114 omits UUID from scalar classification
and introspects its bean properties; SkillMethodBeanPostProcessor.java:337/:567 generates
then binds through the application mapper. These are under src/main/java/ai/loomspan/internal/
serialization and core. Existing LoomspanMethodInputSchemaGeneratorTest.java:26/:50/:70
and SkillMethodBeanPostProcessorTest.java:118/:154 do not cover UUID. The probe README
retains commands, candidate scope and evidence limitations.

**Enum names and Java binding — agreed audit correction:** generated string-enum
choices must agree with Java binding for enum constants with JsonProperty names.
The current generator uses String.valueOf while Jackson binding honors the annotated
name: an isolated probe with JsonProperty("wire-ready") on READY showed that READY
passed schema validation but failed binding, while wire-ready bound successfully but
failed validation. Replacing the legacy validator alone does not fix this mismatch.
Align generation and binding for this bounded case without introducing a general
custom-serializer schema system or changing method signatures. Add a registration/
invocation regression proving the advertised value validates and reaches the intended
enum constant; retain ordinary enum coverage. The probe establishes the narrow mismatch,
not full invocation or live-provider acceptance. Implementation remains pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:90` (`values.add(String.valueOf(value))`) advertises Java string values; `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:567` (`return objectMapper.convertValue(materializedValue, parameterJavaType)`) binds using Jackson.
- `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:118` (`invokesCapabilityUsingEnvelopeMap`) is the existing registration/binding harness, but does not exercise an enum with JsonProperty. Add the annotated-enum case there; the isolated probe described above is not a committed regression test.

**Interface parameter-name remapping — agreed audit correction:** preserve the
existing support for SkillMethod declarations on interfaces implemented by Spring
beans. When interface contract names differ from implementation parameter names,
build the final properties and required list from the original generated parameter
schemas by position, rather than removing and reinserting names in the same mutable
map/list. Overlapping names must not overwrite a later parameter's schema or alter
its requiredness. Preserve the existing contract-selection rules, implementation
types, SkillParam descriptions/requiredness, runtime-reference handling and binding
by contract name; no signature restriction or new public API is needed.

Add a registration/validation/invocation regression with swapped interface and
implementation parameter names and different types, plus mixed required/optional
parameters. Assert both properties retain the correct types and descriptions, the
required list matches the selected contract metadata, and valid contract-name input
reaches the intended argument positions. Retain ordinary matching-name and existing
interface/proxy invocation coverage. A local registration probe published only
right:string for interface (String left, int right) implemented as (String right,
int left): intended input failed schema validation although direct binding returned
the expected result. The existing secured generic JDK-proxy integration test passed
through SkillTemplate.invoke(), establishing interface support but not collision
coverage. The remapping fix and its focused tests remain pending; no provider
capability is inferred from this local evidence.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:333` (`private String buildInputSchema`) mutates properties in place; `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:393` (`required.remove(index)`) mutates required names while remapping.
- `src/test/java/ai/loomspan/integration/JavaSkillIntegrationTest.java:109` (`void securedGenericJdkProxyRetainsCanonicalInputContractAndActualMethodAdvice`) proves interface/proxy invocation; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:137` (`handlesMissingOptionalParameterWithoutCrashing`) covers ordinary optional arguments. Neither exercises swapped names. The earlier passing proxy test is historical local evidence, not a rerun during this backfill.

**Java property naming and binding — agreed audit correction:** generate Java input
property names using the application binding mapper's property introspection, while
retaining the dedicated schema mapper for schema JSON handling. Production currently
generates properties with the schema mapper but binds with the application mapper;
an application naming strategy can therefore make the published contract disagree
with binding. Preserve default naming, explicit property-name annotations, required
lists and nested runtime-reference traversal under the same property names. This is
a naming-alignment correction, not a general custom-serializer schema system or a
change to framework-owned trace/observability serialization.

Add a registration/validation/invocation regression using a configured SNAKE_CASE
application mapper and a nested Java property. Assert the published schema and
resolved contract use first_name for Java firstName, valid input reaches the method
unchanged, and the unadvertised spelling is rejected under the closed contract.
Retain default-name coverage and exercise production's separate mapper wiring.
The local registration/binding probe found firstName passed validation but bound
to null, while first_name failed validation but bound to Ada when invoked directly.
Existing generator tests use the same mapper for generation and conversion; the
snake-case observability integration fixture does not establish skill-input binding
coverage. Implementation and focused verification remain pending; no provider
capability is inferred from this local evidence.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:129` (`private SkillMethodBeanPostProcessor(ObjectMapper objectMapper, ObjectMapper schemaMapper`) wires separate mappers; `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:114` (`.introspectBeanDescriptionForCreation(type)`) chooses schema property names; `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:638` (`private Map<String, JavaType> propertyTypes`) uses binding-side introspection for runtime leaves.
- `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:20` (`private final ObjectMapper objectMapper`) shares one mapper between generation and contract conversion; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:154` (`publishesDescriptionsAndBindsUnconstrainedReflectedInputs`) exercises ordinary input binding. Neither proves production wiring with a custom naming strategy.

**Inherited generic parameters — agreed audit correction:** resolve method parameter
types against the concrete bean class and reuse those resolved types for generated
schemas, runtime-reference marking and Java binding. Current context-free resolution
of an inherited T can collapse it to Object even when the bean extends Base<Integer>.
Preserve existing declaration selection and proxy invocation; no new signature or
public API is needed. Add a registration/validation/invocation regression for an
inherited T resolved to Integer: publish an integer contract, accept integer input,
and reject strings before entering the method. Include a resolved runtime-reference
parameter to verify marking and binding use the same type information. Retain existing
concrete-override generic-interface coverage and resolved generic recursion checks.
A local registration/invocation probe published value:{} for Base<T>.increment(T)
inherited by Base<Integer>'s concrete subclass: 4 validated and returned 5, while a
non-integer string validated then failed with ClassCastException. This establishes
the local mismatch, not provider behavior. Implementation and focused tests remain
pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:41` (`mapper.constructType(parameter.getParameterizedType())`), `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:354` (`applyRuntimeInputSemantics(parameterSchema, objectMapper.constructType(parameter.getParameterizedType()))`) and `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:538` (`JavaType parameterJavaType = objectMapper.constructType`) independently resolve without the concrete owning class.
- `src/test/java/ai/loomspan/integration/JavaSkillIntegrationTest.java:109` (`void securedGenericJdkProxyRetainsCanonicalInputContractAndActualMethodAdvice`) covers a concrete override; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:72` (`inheritedSkillUsesSpringDeclaringClassPolicySemantics`) checks inherited security policy, not inherited generic parameter resolution.

#### Recursive Java input types — agreed November 11, 2026

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

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java:104` (`else if (!visiting.add(raw))`) emits the open fallback; `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:90` (`recursiveDtoFallbackRemainsBoundedAndObjectShaped`) explicitly asserts that legacy fallback. Replace that expectation as agreed; `src/test/java/ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java:70` (`typedMapSchemasRemainConstrainedAfterObjectBroadening`) supplies nonrecursive map controls.

**Runtime-resource sequencing — agreed November 12, 2026:** recognize existing
Resource, InputStream and byte[] leaves before ordinary bean-property expansion and
recursive-contract rejection. Reuse the existing supported-type predicate/semantics,
including nested properties, collection items and map values, rather than maintaining
divergent type lists. These leaves expose marked string contracts, not their Java
implementation internals. Preserve strict rejection for genuinely recursive exposed
DTOs, resolved-type cycle tracking, finite generic nesting and the separately agreed
description, naming and container-binding corrections. No public API change is needed.

The [preserved probe](../../../docs/maintainers/probes/tbd-13-runtime-recursion/README.md)
uses actual registration: the current code registers direct/nested Resource inputs;
replacing the generator's fallback with rejection first fails on java.io.File before
runtime rewriting. An isolated candidate recognizing runtime leaves first preserves
identical published resource schemas and direct validation/invocation, while rejecting
real DTO cycles, including beside a Resource. Seven existing binding/cleanup test
methods passed with the candidate. This is a reproduced migration-order hazard, not
a current Resource-registration defect. The candidate retains raw-class cycle tracking;
it is not the complete agreed resolved-generic implementation or full/live acceptance.

Tickets must preserve this ordering and add combined registration cases for direct,
nested/items/map resource leaves, genuine DTO cycles alongside them, finite generic
nesting, metadata fidelity and existing binding/cleanup behavior. Evidence anchors:
SkillMethodBeanPostProcessor.java:337 generates before :354 applies runtime semantics;
:410/:457 rewrites resource leaves and :662 defines their type predicate.
LoomspanMethodInputSchemaGenerator.java:104/:114 handles cycles/bean traversal.
Both are under src/main/java/ai/loomspan/internal/ in core and serialization.
Existing SkillMethodBeanPostProcessorTest.java:330 verifies reference-friendly schemas;
LoomspanMethodInputSchemaGeneratorTest.java:90 still expects recursive fallback. Neither
combines strict recursion rejection with resource registration. Implementation is pending.

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

#### Output evidence metadata — agreed November 11, 2026

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

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:857` (`String evidence = entry.getValue().getEvidence()`) extracts root-property evidence; the parser/compiled-contract path immediately below it remains the authority. The existing lowercase claim index at `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:899` (`compiled.keySet().forEach`) must follow the agreed exact-name migration.
- `src/test/java/ai/loomspan/internal/skill/YamlSkillEvidencePropertyCatalogTest.java:41` (`void loadsEvidenceAnnotationFromImmediateRootOutputProperty`) and `src/test/java/ai/loomspan/internal/skill/YamlSkillEvidencePropertyCatalogTest.java:98` (`void rejectsEvidenceOutsideImmediateRootPropertiesWithFullPath`) establish legacy extraction/placement. Migrate their vocabulary and retain those semantics; these are not tests of the new annotation.

#### Effective-schema display and catalog metadata — agreed November 11, 2026

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

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java:359` (`private static Map<String, Object> outputSchemaMetadata`) reconstructs selected legacy fields and omits child evidence; resolveOutputSchema immediately above follows forwarding chains.
- `src/test/java/ai/loomspan/internal/skill/SkillDependencyValidatorTest.java:21` (`void transitiveReturnUsesCandidateDeclarationsContractsAndVisibility`) covers transitive contracts; `loomspan-console/web/src/observability/SkillDetail.test.tsx:49` (`TypeSafe detail retains source label, original declaration and descriptive schema`) covers inert display of existing schema text, not preservation of newly supported standard keywords.

#### TypeSafe migration boundary — agreed November 11, 2026

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

**TypeSafe response decimal precision — agreed audit correction:** parse native
response envelopes with lossless decimal handling before recording response evidence
and extracting answers. Keep this correction local to TypeSafe response parsing;
do not change shared mapper behavior or introduce result-schema validation. Reuse
the arbitrary-precision decoding approach already present in AcceptedResultDecoder.
Add focused mock-response integration tests with long fractional and exponent-form
decimal values, including nested answers, asserting exact numeric values in returned
answers and trace evidence using precision-preserving assertions. Retain object-answer
acceptance, usage accounted once and existing malformed-response handling.
The exact current codec/extraction operations rounded 0.12345678901234567890123456789
to 0.12345678901234568 in a local probe. Source inspection shows successful parsing
records the parsed envelope rather than the raw body; the full executor path was not
run with that fixture. Existing large/extended-answer tests use integer fixtures and
default-mapper tree comparisons, which do not establish decimal precision.
Implementation and focused tests remain pending; no live provider claim is made.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:89` (`envelope = codec.readTree(raw)`) parses with the default schema mapper; `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:97` (`responseEvidence.put("envelope", envelope)`) records the parsed value; `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:121` (`return codec.writeValueAsString(answers)`) returns extracted answers.
- `src/test/java/ai/loomspan/integration/TypeSafeSkillIntegrationTest.java:298` (`void completeExtendedAndLargeAnswersPassThroughBothRoutePrefixes`) uses integer/large-string fixtures and default-mapper equality. Reuse this mock HTTP harness with exact decimal assertions; `src/main/java/ai/loomspan/internal/runtime/input/AcceptedResultDecoder.java:12` (`USE_BIG_DECIMAL_FOR_FLOATS`) is the existing lossless-decoding implementation to reuse.

**TypeSafe request decimal precision — agreed audit correction:** make
ResolvedRequest.json() reparse captured request decimals losslessly while retaining
its detached-copy behavior. Template resolution can preserve a BigDecimal in jsonText
only for the subsequent default-mapper reparse to round it before the executor adds
the model and serializes the request body. Keep this correction local; preserve
embedded-string substitution and immutable request reuse across retries.
Add focused tests for long fractional/exponent-form numeric substitutions, including
nested values, repeated json() reads and mutation isolation, and captured HTTP request
bodies across retries using precision-preserving assertions. Existing scalar-fidelity
coverage uses 1.25 embedded into a string and does not establish this numeric path.
A local request-construction probe retained 0.12345678901234567890123456789 in the
resolved JSON text but emitted 0.12345678901234568 after json() and executor-style
serialization. No HTTP/provider call was made. This is separate from response parsing;
implementation and focused tests remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java:74` (`public record ResolvedRequest`) reparses with JSON.readTree; `src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java:36` (`ObjectNode request =`) consumes that tree before adding model and serializing the body.
- `src/test/java/ai/loomspan/internal/skill/TypeSafeDeclarationTests.java:93` (`void parsedYamlScalarFidelityAndResolvedCopiesRemainExact`) checks detached copies and embedded BigDecimal(1.25) text, not long decimal numeric substitutions. Extend it and the captured-request integration harness named in the response finding.

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

**Authored parameter descriptions — agreed audit correction:**
`SkillMethodBeanPostProcessor` currently applies `SkillParam.description` before
`rewriteAsRefFriendlyString` clears the node and installs generic reference guidance.
Retain a nonblank authored description when rewriting Resource, InputStream and byte[]
parameter schemas; use the existing generic guidance only when no nonblank description
is supplied. Preserve the authored text exactly, string schemas, runtime-reference
markers, requiredness, conversion and stream cleanup. This corrects an existing
metadata loss; it does not redesign Java declarations or resource binding.

Add focused registration tests for each of Resource, InputStream and byte[] with
an authored SkillParam description. Assert exact description retention in the published
tool schema and resolved input contract, alongside string type, runtime marker and
requiredness. Cover absent/blank descriptions retaining existing generic guidance.
Reuse existing conversion/cleanup regressions. Existing tests cover ordinary Object
descriptions and unannotated runtime-reference schemas separately; they do not prove
this combination. Source inspection establishes the loss; implementation and new
passing-test evidence remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:382` (`objectSchema.put("description", skillParam.description())`) writes the authored description before `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:457` (`private void rewriteAsRefFriendlyString`) clears the node and installs a generic description.
- `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:154` (`publishesDescriptionsAndBindsUnconstrainedReflectedInputs`) covers ordinary descriptions; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:330` (`publishesRefFriendlyInputSchemasForRefCapableParameters`) covers runtime markers/types. Neither combines an authored description with a rewritten runtime-reference parameter.

**Resource container binding — agreed audit correction:** preserve already assignable
Resource leaves through container binding without serializing their internals. Current
recursive materialization retains the Resource, but the subsequent whole-container
ObjectMapper.convertValue attempts to serialize it. A local registration/validation/
invocation probe accepted a Resource directly and preserved its identity, while the
same object in List<Resource> passed validation but failed binding when Jackson tried
to access its URI. This is not an incompatible target-type conversion.
Add focused validation/invocation tests for direct and reference-resolved List<Resource>
inputs, asserting original object identity and no content reads during binding. Retain
existing Resource-to-string/byte conversion and stream-cleanup tests. Existing typed
collection tests convert Resources to strings and do not prove Resource pass-through.
The list failure is reproduced; verify other affected container shapes before extending
the correction beyond demonstrated cases. Implementation and focused tests remain
pending; no new public API or general serializer system is selected.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:570` (`private Object materializeValue`) preserves Resource leaves, but `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:567` (`return objectMapper.convertValue(materializedValue, parameterJavaType)`) then converts the entire container.
- `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:306` (`passesResourceBackedRefsThroughResourceAndInputStreamParameters`) covers direct leaves; `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:373` (`materializesResourceLeavesInsideTypedCollectionParameters`) converts to strings, and `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:350` (`materializesNestedResourceLeavesInsideTypedRecordParameters`) converts to String/byte[] leaves. These do not prove a Resource-valued container remains intact.

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

#### Attachment input declaration — agreed November 11, 2026

Preserve attachment inputs; defer native generated attachments beyond v1. This does
not remove existing tool-produced resource references or authorize new provider image
creation tools, file downloads/storage, output media APIs, or generated-media replay.

Keep attachment metadata on its schema node using `x-loomspan-attachment` (Option A),
not a separate `input_attachments` path map and not `type: attachment`:

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

`type: string` describes the JSON reference form; it does not mean a direct Java
Resource must be stringified. Direct Resource validation follows the agreed attachment-only adapter below; do not
silently drop support or bypass structural checks.
The annotation identifies attachment execution metadata; it does not introduce a
custom JSON type or alter standard assertion meanings. Retain existing `media_type`
and `allowed_content_types` names, values and validation requirements inside it.
The annotation does not itself grant resource access or prove provider support.

For a nested attachment, place the annotation on the nested property schema. For
arrays of attachments, place it on the `items` schema; it applies to each item.
For arrays of objects containing attachments, place it on the relevant property under
`items.properties`. Walk actual schema nodes, not arbitrary field names. No wildcard
path syntax, duplicate metadata tree, or new path-resolution engine is needed.
Keep it input-only; TypeSafe's existing attachment rejection remains unchanged.

Retain the annotation in the canonical declaration for loading, framework handling,
and authoring tools. Explicitly remove this framework annotation from provider schema
copies while preserving the node's standard keywords. This is deliberate framework-
metadata extraction, separate from TBD-07 assertion omission rules. Do not strip
arbitrary `x-*` keys or expand this into an undocumented extension mechanism.

Tickets must carry the exact syntax, nesting/items semantics, provider-copy extraction,
and the agreed Resource-validation adapter explicitly. Migrate existing YAML
fixtures, parsing/schema models, consumers and Console authoring/display to this
shape; reject the removed dialect, with no dual-format compatibility path. Preserve
resource resolution/access boundaries, repeatability, MIME/image-signature checks,
size limits, materialization, descriptors and existing Java resource conversions.
No automatic provider upload, media conversion, or protocol fallback is authorized.

Required tests: equivalent single/nested/array attachment declarations; requiredness
and surrounding object/array structure; malformed metadata; legacy-dialect rejection;
TypeSafe rejection; canonical annotation retention; provider annotation removal with
standard keywords and similarly named data properties intact; existing reference and
Resource materialization regressions. Tickets must not prescribe fake reference values,
Resource serialization or an alternative validation strategy beyond the agreed adapter.

**Attachment MIME declarations — agreed audit correction:** validate every declared
allowed_content_types entry during loading with the same MIME parser used by media
construction. Report the skill, schema location and invalid entry for malformed MIME
syntax. Preserve existing nonblank/nonempty checks; do not introduce a new MIME
allowlist or infer provider support from syntactic validity. Add focused rejection
tests for malformed nonblank entries, including one malformed entry among valid
entries, and valid-MIME controls. A local probe loaded allowed_content_types:
[not-a-mime] and materialized that content type before the media MIME parser rejected
it for lacking a slash. Existing catalog tests cover missing content types and
misplaced metadata, not this late failure. This implements the agreed malformed-
metadata/load-time rejection boundary; implementation and focused tests remain
pending, and no provider call was made by the probe.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:1009` (`private void validateAttachmentInputSchema`) validates declarations but checks allowed entries only for nonblank text; `src/main/java/ai/loomspan/internal/springai/SpringAiModelInteraction.java:57` (`MimeTypeUtils.parseMimeType(attachment.contentType())`) parses MIME values for media construction.
- `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:962` (`requiresAllowedContentTypesForAttachmentInputAndRejectsMediaFieldsElsewhere`) covers missing lists/placement, not malformed MIME syntax in an otherwise nonempty list.

**Attachment MIME capitalization — agreed audit correction:** use parsed MIME values
consistently for allowed-type comparison and normalized type/subtype selection for
image-signature checks. Preserve the authored declaration; do not lowercase arbitrary
parameter values or add wildcard/provider-support rules. Add focused lowercase,
uppercase and mixed-case tests with identical valid JPEG bytes, plus controls for
genuinely different MIME types and invalid signatures. Cover filename detection and
the single-allowed-type fallback. A local materializer probe accepted image/jpeg but
rejected IMAGE/JPEG for the same JPEG, although Spring's MIME parser considers them
equal. The declaration parser correction alone does not fix this runtime mismatch.
Implementation and focused tests remain pending; no provider capability is inferred.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:174` (`if (!schema.allowedContentTypes().contains(contentType))`) compares raw strings; `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:248` (`boolean valid = switch (contentType)`) matches lowercase image types only.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:54` (`rejectsAttachmentWhenContentTypePolicyDoesNotMatch`) and `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:76` (`rejectsImageAttachmentWhenFilenameAndBytesDoNotMatch`) cover real mismatches, not equivalent MIME type/subtype capitalization.

**Attachment map-value traversal — agreed audit correction:** materialization and
ExecutionCoordinator's mission-input trace traversal must
select a named property schema when present, otherwise schema-valued
additionalProperties, and recurse through that selected schema. The current
DefaultMissionInputMaterializer copies unnamed properties unchanged, so migrating
attachment-valued maps requires this traversal change. ExecutionCoordinator likewise
selects only named properties and passes unnamed values through unchanged before
opening the mission frame. Apply the same schema selection there so attachment map
values receive descriptors in FRAME_OPENED; later materialization or descriptor
records do not repair that earlier input record. Reuse existing reference
resolution, descriptor construction, resource checks and media collection; do not
interpret boolean openness or unmarked values as attachment declarations.

Add focused map-value materialization tests for reference strings and direct repeatable
Resources, permitted/prohibited nulls, and invalid resources. Assert original map keys,
descriptor placement and collected media associations, including nested map values.
Verify named properties take precedence over additionalProperties, ordinary siblings
remain validated, and existing MIME/signature/repeatability/size checks still apply.
Permitted nulls produce no media and survive immutable container copying. Preserve
canonical inputs/schemas and descriptor-only planning versus media-bearing execution.
Add focused coordinator tests for reference and direct repeatable Resource map values,
including nested values and named-property precedence. Assert original keys and
descriptor placement in FRAME_OPENED without Resource serialization; preserve explicit
nulls under the coordinator null-recording requirement below. Existing named-field
materialization and coordinator tests do not establish map-value coverage; this
is pending work for the agreed typed-map scope, not a claim of current YAML support.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:108` (`SkillInputSchemaNode childSchema = schema.properties().get(childName)`) selects only named children; `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:431` (`private Object traceSafeNode`) likewise traverses only named properties before frame recording.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:32` (`materializesRefAttachmentToDescriptorAndResource`) and `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) cover named image fields, not map-value declarations or named-property precedence.

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

**Unreadable attachments — agreed audit correction:** an IOException during the
existing bounded digest read must fail materialization with the attachment field path
and original cause, rather than returning a null hash and accepting the Resource.
Unknown content length remains acceptable when the subsequent bounded read succeeds;
reuse that read without adding a separate readability pass or changing MIME/size policy.
Add focused tests for stream-opening and mid-read failures, a readable unknown-length
control, and failure preventing model invocation. The local materializer probe accepted
an unreadable named PDF Resource while the readable control produced a hash; this
reproduces the materializer defect, not provider behavior. Implementation and full
pipeline/live acceptance remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:311` (`private String digest`) catches IOException and returns null after the bounded read; `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:188` (`String digest = digest(resource, path)`) accepts that result into a descriptor.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:119` (`rejectsMissingRefWithFieldSpecificMessage`) checks resolution failure; `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:76` (`rejectsImageAttachmentWhenFilenameAndBytesDoNotMatch`) checks signatures. Neither tests an unreadable resolved non-image Resource; use a document fixture to reach the digest failure itself.

**Bounded attachment size reads — agreed audit correction:** avoid the unconditional
Resource.contentLength() pre-read, which can consume the entire stream before applying
the size limit. Use the existing bounded digest pass to calculate both actual byte
count and hash, retaining them in the descriptor. Preserve the configured per-attachment
limit and stop oversized reads after bounded excess detection; do not add another limit
or a separate counting pass. Add counting-stream tests for oversized repeatable
Resources with inherited stream-reading contentLength(), including unknown-length
resources, and accepted inputs at/below the limit with accurate size/hash metadata.
Assert bounded bytes consumed before rejection and no model invocation on failure.
The local helper probe consumed all 65,536 bytes before rejecting under a 16-byte
limit; it demonstrated excessive reading, not acceptance of oversized input. Existing
ByteArrayResource size tests prove rejection but not bounded reading. Implementation
and full pipeline/live acceptance remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:181` (`Long size = contentLength(resource)`) invokes the unbounded pre-read before `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:311` (`private String digest`) performs bounded reading.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:132` (`rejectsAttachmentAboveConfiguredSizeLimit`) uses ByteArrayResource with a cheap known length and asserts rejection only, not bytes read. Extend with the counting repeatable Resource described above.

**Attachment failure diagnostics — agreed audit correction:** remove the unconditional
"Use a model/driver that supports the declared attachment media" advice from
DefaultMissionExecutionEngine's generic attachment-failure wrapper. Retain factual
skill/model/attachment context and the original exception as cause; unknown failures
do not establish media incompatibility. Evidenced incompatibility handling remains
governed by TBD-09, without adding a new classifier or changing retry behavior.
Update the focused wrapper test to assert preserved context/cause and absence of
unsupported-media advice for a generic failure; retain the cancellation-preservation
regression. The existing wrapsProviderFailureWithAttachmentContext test expects that
advice for a fixture throwing only IllegalStateException("boom"). This finding is
established by source/test inspection, not live provider evidence. Implementation and
focused verification remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:162` (`Use a model/driver that supports`) appends unsupported-media advice to the generic wrapper.
- `src/test/java/ai/loomspan/internal/skill/MissionExecutionEngineTest.java:393` (`wrapsProviderFailureWithAttachmentContext`) asserts that advice for boom; `src/test/java/ai/loomspan/internal/skill/MissionExecutionEngineTest.java:420` (`attachmentContextDoesNotReplaceACancellationFailure`) is the cancellation control to retain.

**Missing attachment filenames — agreed audit correction:** repeatable direct
Resources need not supply a filename. Preserve a missing name through both
ExecutionCoordinator's mission-input descriptor and LoomspanAttachment.descriptor
without inventing a filename or relaxing resource checks. Both currently insert a
nullable name and call Map.copyOf, which rejects null entries. Use immutable
null-preserving descriptor copies. Existing single-allowed-MIME detection remains
applicable when filename detection cannot supply a type.

Add focused materializer and coordinator regressions using a repeatable Resource
with no filename, valid signature bytes and one allowed MIME type. Assert successful
descriptor creation, retained missing name, collected resource/media and normal
coordinator progression; retain named-resource and MIME/signature/repeatability/size
failure regressions. The local materializer probe reproduced a NullPointerException
for a missing name while the named control passed; the coordinator path was inspected,
not reproduced. This is an existing defect distinct from permitted-null input values.
Implementation and full pipeline/live acceptance remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/LoomspanAttachment.java:29` (`public Map<String, Object> descriptor()`) inserts name before Map.copyOf; `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:466` (`private Map<String, Object> attachmentPlaceholder`) does the same for direct Resource names.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:32` (`materializesRefAttachmentToDescriptorAndResource`) uses a named file; `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) uses the named-field reference path. Neither proves unnamed direct Resources survive descriptor copying.

**Nullable attachment handling — agreed:** absent optional attachment fields produce
no media. Explicit null produces no media only where permitted by the standard schema;
otherwise it fails validation. Required nullable fields must still be present. Preserve
nulls in the input/validation view, and never manufacture descriptors for missing or
null values. Update materialization to skip permitted null attachment values. Tickets
must test absent optional, absent required, permitted null, prohibited null, and nested/
array-item null cases. This is a targeted migration adjustment, not permission to
ignore nulls or relax requiredness globally. Preserve descriptor-only planning and
media-bearing execution; remaining attachment work is verification as listed below.

**Coordinator null recording — agreed audit correction:** preserve explicit null
as null in ExecutionCoordinator's recorded mission input. Its current attachment
placeholder branch manufactures an attachment descriptor for null before materialization;
fixing materialization alone does not correct the recorded input. Validation continues
to decide whether null is permitted; recording must not invent a descriptor or substitute
a replacement value. Add focused coordinator tests asserting that permitted null stays
null in recorded input and produces no descriptor/media, while prohibited null still
fails validation. The helper-level probe reproduced the incorrect descriptor; full
coordinator verification and implementation remain pending.

Implementation note: skipping a permitted null attachment at its leaf is not sufficient.
`DefaultMissionInputMaterializer.materializeNode` currently uses `Map.copyOf` and
`List.copyOf` for surrounding containers, and `RenderedMissionInput` uses `Map.copyOf`
for its input map; these copies reject null entries. Preserve permitted nulls through
these copies while retaining immutable containers, missing-field semantics, property
names and array positions. Reuse existing null-preserving container-copy machinery
where appropriate. The already required null cases must exercise materialization and
`RenderedMissionInput`, not only schema validation, while retaining ordinary sibling
validation and existing resource checks. This names implementation work for the agreed
null policy; it does not broaden that policy or authorize production changes here.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:431` (`private Object traceSafeNode`) enters the attachment branch before checking the value; `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:466` (`private Map<String, Object> attachmentPlaceholder`) manufactures the placeholder. `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:118` (`return Map.copyOf(traceSafe)`), `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:127` (`return List.copyOf(traceSafe)`) and `src/main/java/ai/loomspan/internal/runtime/attachment/RenderedMissionInput.java:14` (`traceSafeInput =`) are the null-rejecting copies.
- `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:60` (`nestedChildPreservesNullContextThroughValidationExecutionAndCanonicalTrace`) covers ordinary null context; `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) covers a non-null attachment. Neither tests a null attachment through recording and materialization.

**Unambiguous attachment paths — agreed audit correction:** use JSON Pointer for
attachment descriptor fieldPath in both coordinator recording and materialization.
Escape object-name tokens (~ as ~0 and / as ~1) and represent array positions as
index tokens. Preserve original input keys; no new path-resolution engine or input
name restriction is needed. The current dot/bracket concatenation gives a literal
a.b property and nested a/b properties the same fieldPath. A local materializer probe
reproduced two different attachments both labeled a.b; the original nested input tree
remained distinct, so this is ambiguous descriptor location metadata, not evidence of
swapped media. Add coordinator/materializer tests distinguishing literal dotted and
bracketed names from nested properties and array items, including / and ~ escaping.
Assert the same unambiguous locations survive descriptor recording and the planned
journal/observer and Console views. Existing single-field tests do not cover path
collisions. Implementation and focused verification remain pending.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializer.java:341` (`private String join`) and `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:431` (`private Object traceSafeNode`) build dotted properties/bracketed indexes; both must use the agreed pointer spelling.
- `src/test/java/ai/loomspan/internal/runtime/attachment/DefaultMissionInputMaterializerTest.java:32` (`materializesRefAttachmentToDescriptorAndResource`) and `src/test/java/ai/loomspan/internal/core/ExecutionCoordinatorTest.java:928` (`redactsDeclaredAttachmentInputBeforeOpeningMissionFrame`) use a simple image key and cannot expose literal dotted/bracketed-key collisions.

**Attachment descriptor propagation — agreed audit correction:** after successful
materialization, record the existing attachment descriptors once per owning mission
frame in both standard and step engines, before planning or a possible no-model
completion. Reuse FRAME_METADATA_RECORDED with an attachments collection in its data
payload and existing internal trace recording. Record descriptors only, never Resource
objects or file contents. Preserve original references, field paths, available names,
MIME/media types, sizes, hashes and metadata; direct Resources remain resources, with
no invented reference or filename. A later mission failure retains this evidence;
failed materialization must not manufacture successful descriptors.

Console consumes canonical trace artifacts, not the execution journal. Add an
Attachments detail view for these records using its existing record/content endpoints
and logical payload reconstruction, including chunked payloads. Display the recorded
descriptor fields without parsing model prompts or dereferencing files. Existing raw
record inspection remains available. No new public API or endpoint is required.
Project the same per-frame descriptors into the existing SKILL_FINISHED journal entry,
including failed missions after successful materialization, so the existing observer
mapping carries them too. Retain the journal's existing field-name redaction exception
without broadening it or applying it to canonical traces or Console. Live attachment
display is deferred: the bounded scalar live-activity projection does not carry this
collection automatically.

Add focused tests for both engines recording descriptors once on the correct mission
frame, including no-model completion and failure after materialization; failed
materialization must not emit successful descriptors. Cover reference and direct
Resource inputs, multiple/nested attachment association, absent names/optional fields,
and absence of file bytes/Resource serialization. Verify journal and observer details
retain the collection with existing journal redaction behavior. Console tests must
render descriptor fields from completed traces, including chunked payloads and missing
optional fields, using existing endpoints without resolving references or loading file
contents. Existing raw-record/chunked-content tests establish reusable transport and
rendering behavior, not attachment-view coverage. Source inspection and the isolated
journal projection probe establish the propagation gap; implementation, these focused
tests and live acceptance remain pending.

Remaining verification is implementation acceptance, not authority to invent policies:
verify real supported files on selected provider/model routes, multiple attachment
association/order, attachment-plus-schema/tool calls, and attachment preservation in
correction requests under TBD-11. The existing mock MIME matrix proves serialization
only. Unsupported media follows TBD-09, without conversion/protocol fallback. Preserve
the existing per-attachment size limit; no new count/aggregate limit is selected here.

Attachment investigation completed November 11, 2026: see the
[TBD-13 attachment evidence](standard-json-schema-research.md#tbd-13-attachment-investigation--november-11-2026).
Offline captures verify the existing Resource-to-media path through all three pinned
adapters (18 input cases), plus OpenAI/Gemini generated-image parsing (two output
cases). Images/PDFs share a serialization path; other MIME types differ. No live
acceptance is claimed. Loomspan currently extracts model response text, not generated
media. Subsequent decisions below/above settle attachment-input design and defer native
generated attachments. This evidence does not authorize new provider tools, media
generation, file storage or public API.

Evidence anchors (source/test inspection; new acceptance remains pending):

- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:88` (`RenderedMissionInput renderedInput =`) and `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:236` (`RenderedMissionInput renderedInput =`) are the successful-materialization insertion points; retain their existing descriptor helpers. `src/main/java/ai/loomspan/internal/runtime/trace/ExecutionJournalProjector.java:103` (`details.put("skillName", record.route())`) builds lifecycle details without attachments.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:252` (`observer.accept(executionViewMapper.map(session))`) and `src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java:42` (`List<SkillExecutionEvent> events =`) show the existing journal-to-observer route.
- `src/test/java/ai/loomspan/internal/skill/MissionExecutionEngineTest.java:342` (`planningReceivesDescriptorsAndMissionTraceRedactsAttachmentBytes`) and `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:1894` (`stepLoopSendsAttachmentMediaOnExecutionSteps`) prove existing planning/media behavior, not frame-metadata recording; `src/test/java/ai/loomspan/internal/runtime/trace/ExecutionJournalProjectorTest.java:24` (`projectsMissionLifecycleWithoutExposingInputOrAuthentication`) covers lifecycle projection without this collection.
- `loomspan-console/web/src/observability/TraceRecords.tsx:212` (`async function readCompleteContent`) reconstructs content ranges, while its generic raw-record action is the existing fallback. `loomspan-console/web/src/observability/TraceRecords.raw.test.tsx:101` (`loads all ranges and pretty prints the complete raw record envelope`) proves chunked raw transport, not an Attachments view.

### TBD-14: Live acceptance matrix and implementation gates

**Status: Resolved as policy — testing ownership, suite gaps, protected suite, host
coverage, bounded cases/budgets, models/routes/effort, timeouts and completion gates
agreed. Implementation and acceptance remain pending.**
Keep fixture proof, isolated adapter proof, deployed framework proof and observed
quality/performance distinct. No implementation or live acceptance is claimed.

#### Implementation split — agreed November 11, 2026

Framework implementation and test-suite implementation are separate workstreams.
Complete production migration, deterministic Framework tests, real adapter/pipeline
request captures, public-surface checks and applicable Console changes/tests here.
Create test-suite tickets and implement them afterward in the neighboring project.

The [test-suite roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md) is the sole implementation plan for that project's
fixture/proxy and authoring migration, protected suite, deployed scenarios, host coverage,
model/route/effort manifest, request/token/cost controls, timeouts and evidence gates.
Those agreed requirements have moved there; they are not removed or completed.

Framework implementation can finish before deployed/live acceptance. Overall migration
acceptance still requires the suite roadmap's gates. Keep deterministic correctness,
historical evidence, pending implementation and unverified live capability distinct.
The suite's earlier OpenRouter checklist-scope question is retained there for resolution;
this split does not expand the agreed initial live matrix or authorize paid calls.

#### Framework prerequisites for later acceptance

Retain all numbered TBD deterministic acceptance requirements, including real
factory/advisor/tool-path captures, schema/options fidelity, ordered replay, status
handling and side-effect prevention. Resolve applicable Anthropic root-constraint and
Gemini raw-schema defects; isolated SDK proof does not establish Framework integration.

Concrete code gap: SpringAiChatOptionsContributor currently maps medium to a 4096-token
thinking budget for Anthropic/Gemini. Anthropic additionally sets maxTokens to at least
8192. That is not native medium effort and exceeds these cases' generation caps.
Sonnet 5.5 rejects explicit thinking budgets. During implementation, use supported native
options for the selected models, retain the agreed total generation caps (including
reasoning), and capture the serialized request. Do not globally change legacy-model
behavior merely to configure this suite. If pinned adapters cannot express these options,
resolve that integration gap before paid acceptance; do not silently raise token limits.

Capture native effort and unchanged generation caps on initial, tool-continuation,
planner and model-override requests. Preserve option defaults where applicable and
legacy-model behavior. Test unsupported combinations without paid calls or fallback.
Use the suite roadmap's exact selected models/settings as fixtures, rather than copying
its manifest here. Missing adapter support blocks the affected later acceptance cell.

## Document audit history

Subsequent schema-design agreement on November 10, 2026: standard JSON Schema
authored in YAML replaces the custom dialect for both inputs and outputs. The user
explicitly authorized breaking changes without customer compatibility, complete
legacy removal, and a common baseline with useful documented provider differences
rather than a fixed lowest common denominator. Phase 2b captures the rationale,
implementation/removal surfaces, semantic decisions still to resolve, and acceptance
evidence so later tickets can be written without this conversation. This update
does not implement schema conversion or establish live structured-output support;
the successful Ollama probe remains limited to reasoning/tool continuation.

Reviewed against the provider conversation on November 10, 2026. The audit added
explicit decision rationale, corrected the earlier blanket "continuity is open"
wording, distinguished proven spike results from source/documentation findings,
and captured retry-boundary questions and superseded alternatives. No production
changes or live tests were performed in this document audit. The checklist remains
the implementation record; checked source research is not runtime acceptance.

## Continuation handoff — November 12, 2026

### Read first and resume point

This roadmap is the authoritative Framework decision/ticket handoff; the companion
[research report](standard-json-schema-research.md) retains code inventory, provider
sources, offline evidence and superseded proposals. Accepted decisions override older
proposals and historical probe coverage. Migration implementation and ticket creation
remain pending. One separately authorized stream-closure repair is implemented in the
worktree, as recorded below. Preserve all existing worktree changes. Follow repository
AGENTS.md and its closed public-API boundary. Read this handoff and its evidence guardrail
first, then both migration documents fully before resuming the audit.

TBD-01 through TBD-13 are resolved as policy, including attachment inputs,
non-attachment runtime values, generated Java contracts, evidence metadata,
effective-schema display and TypeSafe's native evaluation boundary. Native generated
attachments remain deferred. Implementation and acceptance are not complete.
TBD-14 is also resolved as policy; implementation/evidence gates are not completed
acceptance. The next chat performs the bounded readiness review below in a fresh context,
not another open-ended search for Java declaration edge cases. Discuss concrete new
findings one at a time before edits. Migration implementation, ticket creation and live
acceptance have not been completed by this document audit.

### Next session — bounded migration readiness review

Status: handoff prepared; this readiness review has not yet been performed. No further
established, undocumented migration gap is currently queued for agreement; that is not
proof the migration is complete or gap-free. Most outstanding work is already agreed
implementation and verification, not unresolved product policy.

After reading AGENTS.md, this handoff and its evidence-before-findings guardrail, read
both migration documents fully and the relevant neighboring test-suite roadmap sections.
Check existing agreed corrections first. Review whether the known migration blockers
have a concrete implementation delta, prerequisites, sufficient acceptance detail and
an explicit owner/workstream. Use current code/tests to resolve missing evidence; do
not rerun every historical probe or mistake isolated probes for integrated acceptance.

| Readiness area | Bounded question and owning material |
| --- | --- |
| Provider schema/request fidelity | Do TBD-07/TBD-13 and the research blocker register specify the actual Anthropic root-schema and Gemini raw input/output schema integration, canonical-schema preservation, options preservation and real request-capture tests? Exclude deferred schema features from blocker criteria. |
| Tool history, rejection and usage | Do TBD-11/TBD-12 specify implementable call-local integration, adapter evidence-retention prerequisites, captured repair requests, dispatch/side-effect prevention and usage counted once? Preserve existing retry and loop ownership. |
| Model effort and generation caps | Do TBD-14 Framework prerequisites and the suite's selected model/settings fixtures identify native option support, unchanged total caps and captures across initial, continuation, planner and override requests? Investigate adapter gaps without paid calls or scope expansion. |
| Dependency alignment | Is there a concrete full-Framework build/test gate for the final dependency combination and exact pin, distinct from historical isolated RC2/Boot probe evidence? |
| Acceptance ownership and remaining scope question | Are Framework prerequisites separated from later test-suite implementation/live acceptance? Resolve or explicitly retain the suite roadmap's earlier OpenRouter checklist-scope question; do not expand the initial matrix or budget. |

Deliver a concise readiness assessment: ready to ticket/implement, or blocked by named
missing decisions/evidence. For each actual blocker, cite exact code/test/document
anchors, what is already covered, the smallest remaining action and its evidence limit.
Distinguish agreed policy, historical evidence, pending implementation and unverified
live capability. Do not classify planned implementation itself as an undocumented gap.

Stop after these known areas are assessed. If their handoffs are sufficient, recommend
moving to implementation planning rather than searching for more edge cases. Do not
create tickets, edit production code or make live provider calls during this review.
The deferred Java declaration overview remains out of scope; do not restore its removed
details/probes. Preserve all other worktree changes and retained probes. New findings
must satisfy the evidence guardrail and be discussed before document edits.

### Agreed audit corrections and continuation status

The November 11–12 audit recorded agreed corrections and focused test requirements
under their owning TBDs, with code/test paths, audit-time line numbers and symbols.
Those sections are authoritative; do not duplicate their implementation wording here
or in the research report. Earlier findings have been backfilled with evidence anchors.
The next auditor must check existing agreed corrections before reporting a new gap.

Latest completed documentation update: **fresh-context readiness-review handoff**.
The next session follows the bounded review above; the prior scope reset remains in force.
The [deferred overview](#deferred-java-declaration-edge-cases--scope-reset-november-12-2026)
is the only retained record of the deferred edge cases. Their earlier detailed sections,
mandatory restrictions/acceptance work and probe artifacts were deliberately removed.
Do not restore them or continue annotation-by-annotation exploration during this audit.
Focus on migration-critical provider request fidelity, validation boundaries, tool
execution/history, status/usage handling and acceptance gates. Reopen deferred work only
under the concrete-need and explicit-scope conditions in the overview.
The preceding update was **TBD-13 declared map-key registration restriction**,
with preserved baseline/candidate probes. Implementation and full acceptance remain pending.
The preceding update was **TBD-13 open-property JSON compatibility**,
with preserved baseline/candidate probes. This acceptance detail refines the agreed
ordinary-input policy; implementation and full acceptance remain pending.
The preceding update was **TBD-13 JsonUnwrapped declaration rejection**,
with preserved baseline/candidate probes. Implementation and full acceptance remain pending.
The preceding update was **TBD-13 UUID input contracts**, with preserved
baseline/candidate probes. The bounded string mapping is agreed; implementation and
full acceptance remain pending. The preceding update was **TBD-13 runtime-resource sequencing**, with
preserved baseline/rejection/candidate registration probes. Recognize supported runtime
leaves before recursive DTO expansion; implementation and full acceptance remain pending.
The preceding update was **TBD-12 status/usage integration and adapter
prerequisites**, with durable current-behavior and three-adapter RC2 probes. Its classifier,
usage ownership and adapter evidence-retention approach are agreed; implementation and
full acceptance remain pending. Check these agreed corrections during the bounded
readiness review. The preceding update was **TBD-11 completed-tool-history integration**,
with a durable current-behavior harness and RC2 mechanism probe. The agreed approach
uses call-local state, existing tool-result/raw-response capture boundaries and a
shared private repair helper; implementation and full acceptance remain pending.
An earlier update was **Responses
schema decimal fidelity** under TBD-07, with its durable baseline/candidate adapter probe.
The preceding completed update was **ordinary output-validation decimal precision**
under TBD-06, including local lossless parsing, focused validation/advisor tests and
code/test references. It is agreed pending implementation, not a completed production
fix. Nearby agreed work includes ordinary callback and step-action precision (TBD-06),
callback trailing-content/root-shape and step-action null arguments (TBD-08), binding
openness/numeric enum equality (TBD-10), and runtime map-key validation (TBD-13).
No further established finding is awaiting agreement at this handoff. Continue with
the bounded readiness review, not an open-ended defect hunt.

**Completed narrow code repair, separate from migration implementation:** the user
authorized try-with-resources in `SkillMethodBeanPostProcessor.convertToBytes` and
`convertResourceToString` (`src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:686`
and `:720`). The worktree includes success, failure and nested-conversion closure checks
in `src/test/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java:214`,
`:232`, `:250` and `:350`. The earlier focused run passed 21 binder tests and eight
`LoomspanPublicSurfaceArchitectureTest` tests; the retained Surefire reports were
inspected at handoff, not rerun. Preserve these source/test edits and do not ticket
this repair as missing. They do not satisfy the broader migration acceptance gates.

**Restored historical probe evidence:** the latest reported missing-artifact gap is
closed. At restoration time, the user restored `docs/maintainers/probes/` and all 13
then-referenced probe links across the two migration documents resolved. The TBD-06 project, named tests, scripts and
retained evidence are present. Inspected historical reports match 20 tests on each
Boot platform, 11 resource-limit/regression tests on each platform, and two attachment
tests. No probes were rerun to verify the restoration, and no live capability is
established by it. Retain the reproduction instructions and historical qualifications;
do not apply the previously proposed removal of those restored links. This preservation
requirement excludes the later deliberately deleted deferred-edge-case probes. The directory
is currently untracked in Git; preserve it with the other worktree changes.

An additional investigation found that ordinary `OutputSchemaValidator` already
rejects trailing JSON values and trailing garbage with its current default mapper,
while accepting trailing whitespace (local probe, no provider calls). Do not copy
the ordinary tool-callback trailing-content finding onto this boundary: the upstream
callback mapper has different settings. This is not a new output-validation gap.

The original broad StepAction/native-schema finding was withdrawn after tracing
existing behavior and requirements: it did not establish a new missing requirement.
Do not revive it or turn it into tickets without new concrete evidence. Existing
native-schema migration work remains pending as already specified.

The agreed implementation split moves all test-suite implementation and detailed
acceptance planning to the [test-suite roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md). Create its tickets after
Framework implementation. Its earlier OpenRouter checklist-scope question remains
unresolved; the move neither expands the initial live matrix nor claims acceptance.
Resume the document audit under the evidence-before-findings guardrail below.

### Evidence-before-findings guardrail for audits and tickets

Evidence anchors in the agreed findings use repository-relative paths and audit-time
line numbers (verified November 12, 2026), with symbols/test names for navigation after
edits. Backfilled anchors are source/test inspection, not new passing-test results.
Earlier isolated probe outcomes retain their stated limits; temporary target probes
are not durable acceptance tests. Each finding's focused tests remain implementation
requirements unless an explicit completed result is recorded.

Apply this guardrail in every continuation, document audit and ticket-writing context.
TBD-01 through TBD-14 remain settled policy; this guardrail changes review discipline,
not migration decisions. Preserve existing behavior unless a concrete gap against an
accepted requirement justifies a change.

Before presenting an actionable finding or prescribing implementation, establish:

| Required evidence | Question to answer |
| --- | --- |
| Existing behavior | What does the complete relevant code path do, including callers, parsing, fallbacks, validation, execution and failure handling? Cite the code and relevant tests. |
| Agreed migration requirement | Which exact accepted requirement changes or adds something at this boundary? Cite its location. |
| Concrete difference | What is missing, incompatible or incorrect after comparing existing behavior with that requirement? Distinguish pending migration work from a defect or omission in this handoff. |
| Existing coverage | Which tests already prove the behavior, and precisely what remains unproved? Distinguish inspected assertions from tests actually run and mock evidence from live capability. |
| Minimal correction | What is the smallest necessary change, which existing implementation should be reused, and what behavior must remain unchanged? |
| Evidence strength | Is the finding reproduced, established by source inspection, or still an investigation hypothesis? State limitations and tie severity to demonstrated consequences. |

Do not turn an untraced code path, an absent implementation detail or unfamiliar
behavior into a missing product decision. Complete the relevant code-and-test trace
first. If no concrete contradiction or uncovered requirement is established, record
an investigation question rather than an actionable finding. A plausible implementation
risk alone does not establish a defect or justify high severity. Label documentation
clarifications as clarifications, not application failures.

Tickets must describe the delta from existing behavior. Do not prescribe rebuilding
working functionality, invent restrictions or response shapes, or add acceptance
requirements already covered without identifying the specific missing proof. If evidence
is incomplete, scope bounded investigation rather than a speculative production redesign.
Keep agreed policy, historical evidence, pending implementation and unverified live
capability distinct. Reopen settled policy only with concrete contrary evidence.

Discuss concrete gaps and proposed corrections one at a time. Report evidence before
edits; change migration decisions only after explicit agreement. Do not carry an earlier
audit finding into a ticket merely because it was previously reported: re-evaluate it
against this guardrail and withdraw or narrow unsupported conclusions.

### Product priorities and why these decisions were made

- Loomspan is in development with no customer compatibility obligation for this
  migration. Remove the old schema dialect and Ollama implementation completely;
  no aliases, fallback, dual formats, or internal compatibility shims. Public API
  changes still require deliberate design under repository guidance.
- Three chat implementations only: openai (including Ollama/Meta/OpenRouter routes),
  anthropic, gemini. TypeSafe stays separate. Flag unrelated modules/features for
  discussion rather than deleting them or expanding scope implicitly.
- Simplicity must have a concrete developer/model benefit. Prefer preserving working
  behavior; do not invent restrictions from general principles or redesign existing
  framework features just because standard schemas are being introduced.
- Input/output/planning share Draft 2020-12 and NetworkNT Jackson 3. V1 is simple
  property trees, root objects, ordinary types, nullability, scalar enums, descriptions,
  homogeneous arrays, boolean openness, standard formats and the six simple bounds.
  References, general composition/mixed unions, defaults, arbitrary
  patterns and other unapproved advanced features are out of v1 scope. Reject
  unsupported declarations clearly. Typed maps are supported for ordinary inputs; native output/planning maps remain
  deferred because strict-output compatibility differs; defaults were deferred because missing-vs-null and
  value insertion need application experience. Keep standard exact-name/no-coercion
  semantics and previously agreed omission/null/openness behavior.
- Use NetworkNT standard format assertions, no custom formats, reject unsupported
  names. Evaluation depth/steps are unlimited for v1: measured simple workloads did
  not justify capability restrictions and those counters do not bound scalar work,
  compilation or diagnostic volume. Preserve existing resource controls; never call
  incomplete validation successful. Do not let an implementing LLM invent thresholds.
- TBD-07 allows only evidenced omission of rejected simple bounds/exact formats from
  driver request copies. Accepted-but-ignored keywords stay. Canonical local checks
  remain authoritative. No structural rewriting, route guessing or fallback.
- Native strict tools are deferred: preserve tool schemas, pre-execution validation,
  authorization and existing loop ownership. Provider incompatibility and explicit
  refusal/incomplete generation are not ordinary schema-repair opportunities.

### Targeted implementation changes, not redesigns

TBD-09 defines loading/request/provider failure timing and retry separation. TBD-10
preserves binding projection/composition, snapshots, whole-value object/array transfer,
fully bound synthesis skipping and required-work checks; exact case replaces old
case-insensitive matching. No general schema algebra or expanded binding paths.

TBD-11 authorizes only two ordinary-advisor corrections: enforce no tool execution
in final-output repair and retain completed tool exchanges/results in the actual repair
request. Preserve latest-candidate schema repair, existing linter/evidence feedback,
planning, step-loop behavior, budgets and retry ownership. Existing prompt-only "do
not call tools" instructions are not dispatch enforcement. Source inspection is not
an end-to-end reproduction; tickets must include captured-request and side-effect tests.

TBD-12 authorizes only explicit status checks before downstream use, zero tool dispatch
from incomplete responses, and status/content diagnostics with usage counted once on
rejection. Preserve cancellation, normal complete tool turns, transport retry policy,
authorization and existing OpenRouter error handling. Never classify from prose.
Missing adapter metadata is a concrete integration blocker, not permission to guess.

### Attachment decisions and rationale to preserve

Use x-loomspan-attachment beside the string schema node, containing media_type and
allowed_content_types. This keeps metadata with the field, naturally handles nested
properties/items, and avoids a second metadata tree/path language. Remove only this
recognized annotation from provider copies; keep it in the canonical declaration.
Preserve existing reference resolution, Resource materialization, repeatability,
MIME/image signatures, size checks and access boundaries. Permitted nulls/absent
optional values produce no media; requiredness and prohibited nulls still fail.

Direct Resource support is retained because **there is no supported public resource-
registration API**. Internal session-local VFS tests create files using internal
session IDs; SkillTemplate creates a fresh session without a registration hook.
Requiring references would add a public lifecycle API rather than simplify the caller.
Do not recommend internal session/VFS beans to consumers.

The agreed narrow adapter validates reference-string leaves normally and Resource
leaves through existing attachment checks, then uses actual materialized descriptors
and corresponding private leaf schemas to validate surrounding JSON structure.
Preserve array positions, properties, requiredness, bounds and ordinary sibling checks;
never synthesize descriptors for null/missing values, invent placeholder references,
serialize Resource internals, or mutate original input/schema. String constraints
apply to references, not resource bytes/names. Provider schemas never receive the
internal descriptor contracts. Keep this adaptation attachment-only; the separately
marked Java runtime-value decisions under TBD-13 are already settled.

Input support does not imply native output support. Spring AI can parse generated
image media on OpenAI/Gemini, but Loomspan currently extracts text only. Native output
media requires generation configuration, result exposure, storage/download/replay
policy; those are deferred, not authorized by this migration. Existing tool-produced
resource references remain separate. Preserve descriptor-only planning versus media-
bearing execution. No automatic Files API upload, media conversion or protocol fallback.

### Evidence, limitations and outstanding gates

- [TBD-06 probe](../../../docs/maintainers/probes/tbd-06/): earlier 20-test schema/adapter
  evidence on Boot 4.1.0 and 4.2.0-RC2; defect-reproduction success does not mean fixes.
- [Resource-limit evidence](../../../docs/maintainers/probes/tbd-06/evidence/resource-limits/):
  90 workloads on both platforms, four new plus seven regression tests per platform.
  Historical recursive/broader fixtures do not expand the v1 feature scope.
- [Attachment evidence](../../../docs/maintainers/probes/tbd-06/evidence/attachments/):
  AttachmentProbeTest passed two tests, covering 18 input MIME/driver cases and two
  synthetic generated-image responses, on Boot 4.2.0-RC2 only. Source hashes and
  captured requests/exceptions retained. Synthetic bytes/HTTP responses prove mapping,
  not real file parsing, live acceptance, schema-plus-tools combinations or full
  framework behavior. Images/PDFs serialize on all three; other media differ.
- Pinned inspection baseline: Spring AI 2.1.0-RC2; OpenAI SDK 4.80.0, Anthropic 3.4.0,
  Google GenAI 1.73.0; NetworkNT 3.0.9; Java 21. Full framework dependency alignment
  and exact final pin remain implementation gates.
- Applicable Spring AI fidelity issues remain tracked in the research blocker register.
  Do not require fixes solely for excluded output/planning maps, general unions,
  references or multipleOf. Ordinary-input typed maps remain supported.
  Nullable type arrays, ordinary root constraints and option preservation remain
  relevant. An available native configuration path need not wait for upstream fixes.
- Ollama's earlier live result proves tool/reasoning continuation only, not native
  structured schema enforcement. No new live model calls were made in this handoff.

### Settled TBD-13 boundaries to retain

- SkillMethod keeps existing developer-facing signatures/annotations and Java-generated
  schema authority; remove lossy legacy-dialect conversion. SkillExecutor keeps its
  fixed invocation signature and YAML-owned contract. Do not redesign either feature.
- Ordinary authored/generated inputs support typed maps and unconstrained Object (`{}`).
  Native output/planning restrictions are separate. Reject recursive Java input types
  clearly rather than silently opening them; distinguish recursion from finite generic
  nesting. Preserve supported runtime markers and separately checked direct Java values;
  private null placeholders apply only after runtime leaf checks, not to provider data.
- Canonical output evidence uses x-loomspan-evidence on immediate root properties only,
  with exact claim names; strip it from provider copies and preserve existing evidence
  behavior. Effective-schema display retains structural metadata without propagating
  child-local evidence; do not reconstruct schemas through a lossy fixed-field DTO.
- TypeSafe remains a native executor. Migrate declarations/metadata to standard schemas
  without newly enforcing its descriptive output schema. Preserve native answer
  extraction, object-valued answers, extra answers, forwarding and existing behavior.
  Reuse the suite's native regression coverage; no new paid native evaluation is required.

### Ticket discipline

No tickets yet. Later tickets must reproduce the applicable numbered TBD requirements,
why the boundary exists, exact implementation scope, evidence-backed provider rules,
acceptance cases and preservation constraints. A generic instruction or roadmap link
alone is insufficient. The implementing LLM must not guess unresolved product policy,
new public APIs, settings, conversions, fallbacks or exceptions. Flag concrete gaps for
user discussion. Policy resolution is not implementation completion.

## Sources and reproducibility

Primary inspected source artifacts:

- [OpenAI RC2 sources](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.1.0-RC2/spring-ai-openai-2.1.0-RC2-sources.jar): `OpenAiResponsesChatModel`, `OpenAiResponsesChatOptions`, `ResponsesRequestBuilder`, `ResponsesItemMapper`, `OpenAiResponsesException`.
- [Model RC2 sources](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-model/2.1.0-RC2/spring-ai-model-2.1.0-RC2-sources.jar): `AssistantMessage`, `ToolCallPart`, `DefaultToolCallingManager`.
- [Chat client RC2 sources](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-client-chat/2.1.0-RC2/spring-ai-client-chat-2.1.0-RC2-sources.jar): `ToolCallingAdvisor`, `DefaultChatClientUtils`.
- [Tagged upstream POM](https://github.com/spring-projects/spring-ai/blob/v2.1.0-RC2/pom.xml) and [published OpenAI POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.1.0-RC2/spring-ai-openai-2.1.0-RC2.pom).
- [Responses documentation](https://docs.spring.io/spring-ai/reference/2.1/api/chat/openai-responses-chat.html) and [2.1 upgrade notes](https://docs.spring.io/spring-ai/reference/2.1/upgrade-notes.html).

Downloaded sources and POMs are cached under `target/research/spring-ai-2.1.0-RC2`
for inspection only; they are not build dependencies or tracked implementation.
