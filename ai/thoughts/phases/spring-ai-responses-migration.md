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
The broader conversation-history audit remains a linked investigation; its decisions
needed for migrated correction paths must be resolved before accepting those paths.
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
See the [initial Meta investigation](../../../docs/maintainers/meta-compatibility-spike.md)
for its sources and the limits of its evidence.

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

### Settled scope versus history decisions still to make

Required state replay across ordinary tool continuations is agreed. Full history
means the history belonging to that interaction under its intended semantics, not
all prior work by the invocation or every abandoned attempt. The linked
[history audit](model-provider-enhancements.md#audit-conversation-history-across-calls-and-retries)
requests review and agreement, not a finding that existing behavior is wrong.

| Boundary | Starting observation or decision | Verification needed |
| --- | --- | --- |
| Ordinary/parallel tools and recoverable tool errors | Continue the active conversation, preserving required ordered assistant items and tool results. | Actual outbound second/third requests; canonical names, authorization, IDs, order, opaque fields, and no duplicate tool execution. |
| Output-schema corrections | Current inspection suggests baseline plus candidate text and feedback. | Agree whether this is a fresh attempt or continuation; preserve exactly the history that decision requires. |
| Lint corrections | Current inspection suggests amended instructions. | Agree which previous messages/reasoning belong in the corrected request and test the outbound request. |
| Evidence-contract and other retries | Ordinary advisor gaps reviewed under TBD-11. | Implement only its two targeted changes; preserve planning, step-loop and existing retry ownership. Verify actual requests. |
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
- [ ] Cover schema/lint correction boundaries according to the history audit's agreed
  semantics, plus terminal/incomplete responses, malformed responses, 429/5xx errors,
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

## Phase 4 Live Responses and native structured-output validation

- [ ] Run opt-in bounded live probes with explicit model IDs and account credentials.
  Cover text, supported reasoning settings, tool round trips, multiple tool rounds,
  parallel calls where supported, and output validation.
- [ ] Verify that reasoning items are returned and correctly replayed for each
  provider. Record unsupported options and real protocol differences; add narrow
  compatibility handling only when needed.
- [ ] Evaluate representative tasks for correctness, repeated tool work, latency,
  usage, and context growth. Use recorded pre-migration results or the old revision
  for comparison; do not retain a second runtime path just for benchmarking.
  Keep model quality findings distinct from protocol correctness.
- [ ] Validate OpenRouter with explicit model and upstream route identities, including
  a route whose upstream lacks native Responses. Compare the same Meta model direct
  and through OpenRouter where available. Record actual replay fields and limitations;
  acceptance does not require identical answers or promise equivalent model quality.
- [ ] Publish supported configuration examples, limitations, and reproducible results.
  The earlier Meta Chat Completions smoke test is not evidence of Responses compatibility.

Responses acceptance: OpenAI, Meta, OpenRouter, and Ollama pass the agreed live
cases through the same Spring AI adapter with stateless replay.

Structured-output acceptance additionally requires bounded live schema-constrained
skill-output and planning cases for OpenAI, Meta, OpenRouter, Ollama, Anthropic,
and Gemini, with explicit model/route identities and documented schema limitations.
Verify native formatting alongside tools where the framework combines them.
The existing Ollama reasoning/tool probe did not test native structured output.

Completion requires both Responses acceptance and native structured-output
acceptance; success in one workstream does not complete this phase.

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
- [ ] Verify a non-native-Responses upstream works through the same adapter. Use
  fixtures for its documented wire shape and a bounded live route where available.
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
   Traverse only actual schema nodes in the approved v1 tree (`properties` values
   and `items`), including the root. Never recursively delete matching keys from
   arbitrary JSON: a property named `minimum` or `format` is application data.
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

### TBD-13: Framework input features and generated schemas

**Status: Resolved as policy; implementation and acceptance remain pending.**
Attachment inputs, runtime references, Java declaration authority, typed-map and
unconstrained inputs, recursive-type rejection, output evidence metadata, effective
schema display and TypeSafe boundaries are agreed below. Preserve existing behavior
except the explicit changes recorded here. Resume policy discussion with TBD-14.

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

Attachment investigation completed November 11, 2026: see the
[TBD-13 attachment evidence](standard-json-schema-research.md#tbd-13-attachment-investigation--november-11-2026).
Offline captures verify the existing Resource-to-media path through all three pinned
adapters (18 input cases), plus OpenAI/Gemini generated-image parsing (two output
cases). Images/PDFs share a serialization path; other MIME types differ. No live
acceptance is claimed. Loomspan currently extracts model response text, not generated
media. Subsequent decisions below/above settle attachment-input design and defer native
generated attachments. This evidence does not authorize new provider tools, media
generation, file storage or public API.

### TBD-14: Live acceptance matrix and implementation gates

**Status: Resolved as policy — testing ownership, suite gaps, protected suite, host
coverage, bounded cases/budgets, models/routes/effort, timeouts and completion gates
agreed. Implementation and acceptance remain pending.**
Keep fixture proof, isolated adapter proof, deployed framework proof and observed
quality/performance distinct. No implementation or live acceptance is claimed.

#### Acceptance ownership, suite gaps and mandatory gates — agreed November 11, 2026

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

#### Live host coverage — agreed November 11, 2026

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

#### Protected framework acceptance suite and Meta Contributor — agreed November 11, 2026

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

#### Bounded live cases, token budgets and acceptance rules — agreed November 11, 2026

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

#### Terra acceptance selection — agreed November 11, 2026

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

#### Final model, route and effort manifest — agreed November 11, 2026

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

#### Acceptance timeout profile — agreed November 11, 2026

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

## Continuation handoff — November 11, 2026

### Read first and resume point

This roadmap is the authoritative decision/ticket handoff; the companion
[research report](standard-json-schema-research.md) retains code inventory, provider
sources, offline evidence and superseded proposals. Accepted decisions override older
proposals and historical probe coverage. Production implementation and ticket creation
have not been performed here. Existing worktree changes outside these research/docs
must not be discarded. Follow repository AGENTS.md and its closed public-API boundary.

TBD-01 through TBD-13 are resolved as policy, including attachment inputs,
non-attachment runtime values, generated Java contracts, evidence metadata,
effective-schema display and TypeSafe's native evaluation boundary. Native generated
attachments remain deferred. Implementation and acceptance are not complete.
TBD-14 is also resolved as policy; implementation/evidence gates are not completed
acceptance. The next chat is a fresh-context audit of both documents in full: check
consistency, stale proposals/statuses, code accuracy, implementation scope and mandatory
acceptance coverage. Inspect the neighboring suite and official provider sources where
needed. Report findings with file/line references before edits; discuss corrections one
at a time and update documents after agreement. Do not reopen settled policy without
concrete new evidence. No production implementation, ticket creation or paid calls yet.

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

### Settled TBD-14 acceptance plan

The detailed TBD-14 sections above remain authoritative, including all mandatory tests.
No further TBD-14 policy decision is identified. Preserve these agreements in the audit:

- Framework owns exhaustive deterministic semantics and real adapter/pipeline captures;
  Console owns applicable display tests. loomspan-sidecar-test-suite owns deployed/live
  acceptance through its embedded Java Framework host and Python/SDK/Sidecar host.
- Create a durable protected framework-acceptance suite in that neighboring repository,
  with dedicated scenarios/skills/fixtures/expectations and a required-coverage manifest.
  Missing/skipped cells fail completeness. Business generators/refresh commands cannot
  modify it; fresh run artifacts cannot overwrite accepted fixtures. Shared harness
  changes require applicable provider-free framework-suite regression.
- Both hosts run provider-free business/TypeSafe regression. Java runs all five bounded
  live cases on six endpoints. Sidecar runs only the representative two-tool chain on
  direct OpenAI, Anthropic and Gemini. Other Sidecar Responses routes get deterministic
  configuration checks; do not claim live coverage for those cells.

| Endpoint | Final model/route | Reasoning |
| --- | --- | --- |
| OpenAI | gpt-6.1-terra, direct Responses | medium |
| OpenRouter | openai/gpt-6.1-terra, standard OpenAI endpoint tag openai only, no fallback | medium |
| Meta | muse-spark-1.3-contributor, direct Responses | medium |
| Ollama | qwen3.5:4b-q4_K_M, local Responses | thinking on; boolean-only, not medium |
| Anthropic | claude-sonnet-5-5, native Messages | native medium effort |
| Gemini | gemini-3.8-flash, native Developer API | native MEDIUM thinking level |

Use the same direct cloud selections on Sidecar. Keep business Sol/Luna assignments
unchanged. Contributor's training-use terms are an accepted choice with purpose-built
synthetic fixtures, not content masking. Record/recheck the Ollama 0.40.16 baseline and
model digest from the final manifest above. Explicitly verify OpenRouter routing and
parameter support; no provider/Flex/priority fallback. Provider medium settings are not
a promise of equal compute.

| Java case per route | Maximum physical sends | Generated tokens per send |
| --- | --- | --- |
| Small structured result | 1 | 1,024 |
| Two-tool chain to structured result | 3 | 4,096 |
| Small planned task | 4 | 4,096 |
| Two small images plus tool | 2 | 2,048 |
| One-page PDF plus tool | 2 | 2,048 |

Per route: 12 sends and 37,888 generated tokens. Including the three Sidecar chains:
81 sends total, 69 cloud and 12 local. Every send has at most 8,192 total input tokens,
including instructions, schemas, replay and media. Cloud mathematical maxima are
565,248 input and 226,304 generated tokens; generation includes reasoning and visible
output. Short visible answers (ordinarily under 200 tokens) are a fixture target.

The initial matrix has a total USD 5 ceiling, not a per-route or rerun allowance.
Preflight current pricing/actual fixtures, reserve conservative worst-case cost before
each send, retain uncertain-billing reservations and prior spend. Insufficient budget
or unbounded pricing leaves a gap. Zero automatic transport retries/schema repairs;
no fallback, cap increase, history truncation, media conversion or weakened assertions.
Failed sends count; subsequent paid scope needs explicit authorization.

One acceptance-only timeout profile on both hosts/all routes: provider request 180 s,
proxy upstream 210 s, case mission 900 s, runner including collection grace 960 s.
Keep existing business 480/510/2400 s and unrelated production/TypeSafe/callback settings.
Test elapsed deadline semantics, bounded polling/collection, cancellation, no subsequent
sends/tool dispatch from late responses, retained diagnostics/cost reservations and
provider-disabled restoration. Do not claim cancellation reverses completed effects
or prevents provider billing. Timeouts, capped output and missing reasoning-continuity
evidence are failures/unverified assertions, not passes.

### Remaining implementation and acceptance gates

1. Align final dependencies and prove the actual factory/advisor/tool paths, schema
   fidelity, ordered replay, refusal/incomplete handling and supported API boundaries.
   Resolve relevant Anthropic root-constraint and Gemini raw tool-schema defects.
2. Fix selected-model option mapping: current SpringAiChatOptionsContributor maps medium
   to 4096 thinking tokens for Anthropic/Gemini and raises Anthropic output to at least
   8192. That violates bounded cases; Sonnet 5.5 rejects explicit thinking budgets.
   Capture native medium options and unchanged generation caps on initial, planner and
   continuation requests. Verify enabled thinking is expressible on Ollama Responses.
   Do not silently alter legacy model behavior or expand the suite budget.
3. Migrate suite Responses request matching/envelopes/usage/proxy routing, controlled
   responses and six-endpoint orchestration. Update YAML authoring generators together
   with their outputs. Preserve business reference packs; legacy Chat replay does not
   establish Responses acceptance. Add focused missing schema/media/status scenarios.
4. Stage identifiable coherent migrated artifacts on both hosts; run applicable
   deterministic framework, Console, protected-suite and both-host regressions. Keep
   SDK/transport checks scoped to affected paths rather than every provider.
5. Verify account access, actual route/parameter/media support, exact runtime/model
   identities, effective timeout/token settings and conservative cost preflight before
   separately authorized paid execution. Published capabilities are not passing tests.
6. Record evidence per required cell: host/build/configuration, model/route, original
   schema/request/diagnostics, ordered protocol state, local validation, attempts,
   usage/cost and limits. Missing cells, credentials, adapter support, incomplete
   artifacts and failed assertions remain open gates. Distinguish deterministic proof,
   deployed integration, live compatibility and observed quality/performance.

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
