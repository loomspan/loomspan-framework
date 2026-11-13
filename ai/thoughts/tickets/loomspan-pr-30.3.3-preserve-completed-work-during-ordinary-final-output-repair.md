# PR 30.3.3 — Preserve completed work during ordinary final-output repair

## Outcome

Implement TBD-11 as one cohesive change across schema/linter/evidence advisors, the interaction context, provider capture and canonical tool manager. Use call-local state, raw replies, explicit replay-segment ownership and a terminal repair flag. Remove executable tools and enforce no dispatch.

## Scope and sequencing

**Dependencies:** PR 30.1.5, PR 30.3.1, PR 30.2.4 and PR 30.2.5.

**Expected build state:** Ordinary repair pipeline should pass integrated tests. This ticket should not introduce a new intentional build break.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own ordinary schema/linter/evidence correction history and tool prohibition as one change. Consume existing provider status guards, canonical parts, attachment and binding behavior. Preserve planning and step-loop policies; no alternative tool loop or cross-invocation memory.

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

- [ ] Each real ordinary correction path sends completed tool exchanges and the latest raw candidate without stale candidates, duplicate replay or composed-text substitution.
- [ ] No executable tools appear in repair requests, including model defaults, and attempted repair tool calls produce zero side effects.
- [ ] Repeated/combined corrections, concurrent/nested calls, attachments and fixed binding snapshots preserve isolation, budgets, cancellation and exactly-once attempt/usage behavior.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Actual captures for each advisor and combined paths on three adapters: completed exchanges, latest candidate, no duplicate history, zero repair side effects, default callbacks, concurrent/nested isolation, attachments, snapshots, usage and exhaustion.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### TBD-11: Correction history, tool availability, and side effects

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

## Reproduction anchors

Historical completed-history reproduction, using Java 21 and Maven:

```powershell
./docs/maintainers/probes/tbd-11-history/run-current.ps1
mvn -f docs/maintainers/probes/tbd-11-history/rc2/pom.xml test '-Dtest=CompletedHistoryProbeTest'
```

The current harness uses real factory/advisors and the default tool loop, not the whole
assembler/canonical-manager path. Five RC2 mechanism tests use synthetic correction
components and omit the separate numeric-schema patch. Real combined advisors, all
three adapters and concurrent/nested-call isolation remain this ticket's acceptance.

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
