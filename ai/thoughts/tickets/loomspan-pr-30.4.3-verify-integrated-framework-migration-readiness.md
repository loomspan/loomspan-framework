# PR 30.4.3 — Verify integrated Framework migration readiness

## Outcome

Run the complete agreed deterministic acceptance set against the final artifacts. Close cross-ticket integration failures, record dependency/fix identities and map requirements to evidence. Produce the handoff for later test-suite implementation.

## Scope and sequencing

**Dependencies:** All preceding tickets.

**Expected build state:** Green is mandatory.

No new intentional build failure is authorized at this milestone. PR 30.4.3 is the final integrated verification gate, not a waiver of this ticket's checks.

Own final integrated assurance and concrete cross-ticket corrections. Do not silently absorb missing major features or weaken requirements to obtain green checks. Test-suite fixtures/proxies, deployed hosts, budgets and live execution remain a later workstream.

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

- [ ] Final coherent artifacts pass the full applicable deterministic Framework/Console suite and public-surface checks, including previously blocked checks from earlier tickets.
- [ ] Every numbered migration requirement has code/test evidence or an explicitly later deployed/live owner; no required Framework failure or skipped check is represented as acceptance.
- [ ] The handoff records exact versions/fixes, protocol/schema/removal evidence and later suite gates without claiming reasoning quality gains or live capability from deterministic fixtures.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Full applicable Framework/Console checks, public-surface architecture tests, real adapter/pipeline captures, isolation, retries, status/usage, schema fidelity, attachments and removal evidence. Clearly identify remaining deployed/live gates.

## Completion and downstream handoff

Reconcile every ticket's blocked-verification ledger against the final checkout.
No isolated mock/SDK proof, old Chat replay or successful final text substitutes for
real migrated factory/advisor/canonical-tool captures. No passing test count alone proves
all preserved contracts. Verify exact dependency/fix identities and code removal.
Do not infer improved reasoning/task quality from ciphertext or valid JSON.

The later suite owns both embedded Java and Python/SDK/Sidecar hosts. Required future
gates include both-host provider-free/business/TypeSafe regressions, full bounded
Java live cases on six selected endpoints, Sidecar tool-chain cases on direct OpenAI,
Anthropic and Gemini, deterministic Sidecar configuration checks for remaining Responses
routes, coherent artifact identities and explicit failed/missing cells. It owns protected
fixtures/manifests, proxy/Responses envelope migration, authoring generators, reservations,
pricing/access preflight, deadlines and authorized paid execution. Do not create a second
live harness here. Preserve the unresolved earlier OpenRouter non-native-upstream and
same-Meta direct/routed comparison question without expanding the initial matrix.

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

#### Phase 3 Verify protocol and framework behavior

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

#### Phase 4 Deployed and live acceptance — separate follow-up

After Framework implementation, create and execute tickets in the test-suite project
using its [migration acceptance roadmap](../../../../loomspan-sidecar-test-suite/MIGRATION-ACCEPTANCE-ROADMAP.md). That document owns the complete
agreed suite implementation scope and acceptance gates for both migration workstreams.
Framework implementation completion does not establish deployed or live acceptance.
Overall roadmap acceptance remains pending until those required gates pass.

#### Later production stabilization outside this ticket

The following roadmap checklist is a future production follow-up, not a requirement
to wait for GA or expand this Framework deterministic acceptance ticket.

- [ ] Track upstream fixes relevant to this integration and move to a suitable GA
  version when available. Re-run the compatibility suite on each selected upgrade.
- [ ] Reassess any remaining prerelease dependency before production; development
  acceptance of non-GA versions does not decide the eventual production policy.
- [ ] Remove temporary workarounds superseded by upstream support and finalize
  configuration migration and release documentation. Complete the removal audit below.

### Contract 2

#### Required acceptance evidence

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

#### TBD: audit findings and decisions to resolve

This register preserves the roadmap audit for sequential discussion and later ticket
creation. Recommendations below are proposals, not accepted requirements. Resolve
one item at a time with the user, update the affected roadmap sections, and record
the decision here. Retain resolved entries as handoff history. Do not infer approval
of a proposal from its inclusion in this list. All entries start open.

#### TBD-01: Reconcile validation evidence summaries

**Status: Resolved — agreed November 10, 2026.** Corrected the early evidence summary
and RC2 investigation introduction to distinguish the initial source-only research
from subsequent successful isolated RC2 compilation, deterministic replay tests, and
the Ollama live tool/reasoning round trip. Full framework migration and platform
validation, other-provider live Responses acceptance, and native structured-output
validation remain pending. This reconciles recorded evidence without changing scope
or treating isolated adapter success as full framework acceptance.

#### TBD-02: Align scope, title, and completion criteria

**Status: Resolved — agreed November 10, 2026.** Renamed the roadmap to "Provider
Consolidation and Standard Schema Migration" while retaining the filename for link
stability. Updated the scope, problem statement, phase summaries, and overall
completion criteria to require both provider consolidation and standard schemas with
native structured output. Qualified the provider-expansion restriction as additional
Responses providers; Anthropic/Gemini schema work remains authorized scope. Historical
Responses rationale is retained. These edits clarify agreed scope without expanding it.

#### TBD-03: Ollama removal versus provider-specific schema mappings

**Status: Resolved — agreed November 10, 2026.** Ollama uses the shared `openai`
implementation with no Ollama-specific runtime branches, including schema mappings
or dispatch. Document limitations discovered during validation. If supporting Ollama
requires special implementation code, flag the concrete issue for discussion before
adding it. This rule takes precedence over the general permission for narrow provider
mappings; no hypothetical exception or disguised native adapter is authorized.

### Contract 3

#### TBD-14: Live acceptance matrix and implementation gates

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

### Contract 4

#### Evidence, limitations and outstanding gates

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

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

#### Executable verification follow-up — November 10, 2026

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
| OpenAI Responses | The tested schema fixture survives `text.format.schema` and function parameters. This did not establish numeric precision: the November 12 follow-up below reproduces rounded decimal bounds/enums in both paths. ChatClient retains effort/token defaults; a direct call with fresh typed options loses them. |
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
