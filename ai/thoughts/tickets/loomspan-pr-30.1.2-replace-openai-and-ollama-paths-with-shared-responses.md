# PR 30.1.2 — Replace OpenAI and Ollama paths with shared Responses

## Outcome

Replace model construction and request options; remove native Ollama and legacy Chat implementation/configuration. Preserve credentials, headers, timeouts and retry ownership. Preserve ordered message parts during canonicalization. Integrate verified Responses schema-precision fixes. Cover effort, caps and OpenRouter routing requirements within the shared path.

## Scope and sequencing

**Dependencies:** PR 30.1.1.

**Expected build state:** Shared Responses path should compile and operate. Legacy fixtures outside its migrated coverage may remain broken.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own Responses transport/options, ordinary canonical-message part preservation, precision and legacy OpenAI/Ollama removal. PR 30.1.5 owns lifecycle classification/accounting; PR 30.3.1 owns schema attachment to ordinary outputs and composed-message preservation; PR 30.3.3 owns final-repair history. Establish reusable protocol fixtures without implementing those later policies here.

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

- [ ] Second and third real Framework requests preserve ordered required reasoning/tool parts, IDs, attributes and commentary phase through canonicalization without duplicate effects.
- [ ] All openai connections use Responses with store=false and no previous_response_id; native Ollama, Chat fallback and obsolete configuration are absent from the owned runtime path.
- [ ] Output/tool schema numeric values remain exact; model overrides retain effort, caps, headers and timeout semantics with SDK retries disabled.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Real factory/tool-manager multi-round captures: stateless replay, IDs, ordering, reasoning, canonical names, numeric schema precision, strict=false, option combinations and model overrides. No duplicate tool effects.

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

#### The problem we are solving

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

#### Why these choices were made

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

#### Tool-continuation, retry and isolation verification

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

### Contract 2

#### Phase 2 Integrate the Responses adapter

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

### Contract 3

#### What Spring AI already supplies

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

#### Preserve parts rather than reconstructing a message from views

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

#### Connection and request options must use the same protocol

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

### Contract 4

#### Observability must distinguish content from protocol state

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

#### Meta checks required for the same adapter

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

#### OpenRouter compatibility and stateless replay

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

#### Ollama consolidation and validation

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

- **Historical completed evidence:** Run an isolated RC2 fixture with three HTTP requests, two tool rounds, ordered
  reasoning summaries/payloads/IDs, call/result IDs, and stateless request checks.
  The [reproducible probe](../../../docs/maintainers/probes/ollama-responses/README.md)
  passed on November 10, 2026. It uses upstream ToolCallingManager, not Loomspan's
  canonical manager; the framework migration remains unimplemented.
- **Historical completed evidence:** Run the bounded live adapter probe with an installed thinking/tool model and
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

#### Remove the old OpenAI-compatible implementation

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

### Contract 5

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

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

#### The four routes behind `openai`

| Route | Output evidence | Tool evidence and model/route limit |
| --- | --- | --- |
| OpenAI direct | Strict subset above through `text.format` | Non-strict tool schemas are distinct from strict generation. RC2 explicitly sends false unless configured; TBD-08 now defers native strict tools beyond v1. |
| Meta direct | [Responses schema reference](https://dev.meta.ai/docs/api-reference/responses/schemas) defines `TextResponseFormatJsonSchema`, schema, and strict subset, but does not establish a keyword-by-keyword parity table. [Structured-output tutorial](https://dev.meta.ai/docs/structured-output) uses Chat Completions with `muse-spark-1.3`; do not substitute that for Responses acceptance. | Responses function parameters are JSON Schema; documented strict default is true, overridden by RC2's explicit false. Optional/null/open/composition/constraint behavior remains U for the selected Responses model. |
| OpenRouter | [Structured-output guide](https://openrouter.ai/docs/guides/features/structured-outputs) documents `text.format`; native enforcement, translation, and hint-only behavior vary by upstream. No universal keyword table is justified. | Record exact model and endpoint/provider route. `require_parameters` can constrain routing but does not establish keyword enforcement. A hint-only upstream cannot pass native-output acceptance merely because local validation succeeds. No registry or automatic switch is proposed. |
| Ollama | [Compatibility docs](https://docs.ollama.com/api/openai-compatibility) describe stateless Responses. [Pinned converter](https://github.com/ollama/ollama/blob/8a89b76cc7359805e48357ba7e5b99e845a4c211/openai/responses.go) forwards `text.format.schema` to native `ChatRequest.Format`; this does not prove grammar keyword enforcement. | The earlier 0.40.16 / `qwen3.5:4b-q4_K_M` probe tested tool/reasoning replay, not structured output. Keyword semantics, schema-plus-tools, and strictness remain U on that installed combination. No Ollama runtime branch is proposed. |

## Adapter fix acceptance

Prefer a focused upstream correction or a suitable verified release. Record the exact
tested artifact/commit and rerun fidelity cases against it. A merged PR is not proof
a fix ships. Do not deploy copied research classes, mutate SDK singleton mappers or
rewrite HTTP bodies to work around loss. If no acceptable artifact can satisfy the
owned contract, report that concrete dependency blocker; continue independent work
without declaring this ticket complete. External submissions are not authorized here.

## Reproduction anchors

Historical Responses numeric reproduction, using Java 21 and Maven:

```powershell
mvn -f docs/maintainers/probes/responses-schema-precision/baseline/pom.xml test '-Dtest=SchemaWireFixTest' '-Dpatched=false' '-Dboot.version=4.1.0'
mvn -f docs/maintainers/probes/responses-schema-precision/candidate/pom.xml test '-Dtest=SchemaWireFixTest,AdapterTest#openAiPreservesSchemaAndChatClientDefaults+openAiDirectTypedOptionsReplaceDefaults,OllamaResponsesTest#replaysOllamaReasoningAndToolsAcrossThreeRequests' '-Dboot.version=4.1.0'
```

Repeat the isolated comparison with boot.version=4.2.0-RC2 when relevant to the chosen
pin. Keep the explicit method selections: the copied Ollama source also contains a
live method that is excluded. New runs write target outputs, not retained snapshots.
Both output/tool schema paths need lossless comparisons; ordinary floating-point tree
equality can conceal rounding. Preserve numeric value, not lexical spelling/scale.

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
