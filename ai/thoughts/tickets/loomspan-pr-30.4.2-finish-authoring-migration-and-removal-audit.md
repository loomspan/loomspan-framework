# PR 30.4.2 — Finish authoring migration and removal audit

## Outcome

Complete README, examples and authoring guidance; reconcile remaining active declarations and fixtures. Audit removal of legacy schema structures, Chat/native Ollama paths, obsolete configuration and redundant formatting machinery. Preserve historical evidence and separate Console contracts.

## Scope and sequencing

**Dependencies:** Phases 1 through 3 and PR 30.4.1.

**Expected build state:** Green; no intentional new breakage.

No new intentional build failure is authorized at this milestone. PR 30.4.3 is the final integrated verification gate, not a waiver of this ticket's checks.

Own remaining authoring/documentation conversion and the removal audit. Earlier tickets own functional implementation and their necessary regression fixtures. Fix concrete residue, but do not treat incomplete major features as this ticket's generic cleanup scope.

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

- [ ] All active Framework authoring guidance/examples/fixtures describe the new contract and explicit intended semantics; no generator or guidance reintroduces the old dialect.
- [ ] Removal evidence shows exactly three chat implementations and separate native TypeSafe, with no active legacy OpenAI/Ollama/schema path, aliases or compatibility readers.
- [ ] Historical research/probes remain clearly qualified and preserved; unrelated modules are reported rather than silently removed.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Repository reference audits, affected examples/fixtures and documentation checks. Verify exactly three chat implementations, separate native TypeSafe and no aliases, fallback or dual-format support.

## Authoring semantics to document

Document shared standard schema syntax, exact names, no coercion, root objects,
nullable type arrays, required presence versus permitted null, omitted openness,
typed-map/unconstrained ordinary inputs, standard asserted formats and the six bounds.
Explain usage-specific provider restrictions and evidenced omissions without promising
uniform native enforcement. Input support does not enable native output typed maps.
Document x-loomspan-attachment, generated runtime markers and x-loomspan-evidence with
their placement/ownership rules, direct Resource support and deferred generated media.
Preserve binding paths, whole-value transfers, ownership and TypeSafe descriptive output.

Migrate agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md,
input-bindings.md, evidence-contracts.md, typesafe-skills.md, executor examples,
README, configuration metadata and all affected active declarations/generators. Remove
nullable/evidence/type:attachment legacy vocabulary as schema annotations, not ordinary
data properties with matching names. Historical documents/probes remain qualified evidence.
Native Ollama examples become explicit openai base-URL configurations; local dummy
credentials do not replace real credentials in authenticated deployments.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Direction and scope

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

### Contract 2

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

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

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

* Migrate [output-contracts](../../../agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md),
  [input-bindings](../../../agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md),
  [evidence-contracts](../../../agent-skills/loomspan-docs/references/skill-authoring/evidence-contracts.md),
  [typesafe-skills](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md),
  executor examples, README, and fixture expectations with the implementation.
  `OutputBindingCompositionTest`, `DeclaredOutputBindingsPlanningIntegrationTest`,
  `LoomspanMethodInputSchemaGeneratorTest`, `OutputSchemaValidatorTest`, and Console
  fixture/parity tests are existing behavior evidence, not tests run by this research.

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
