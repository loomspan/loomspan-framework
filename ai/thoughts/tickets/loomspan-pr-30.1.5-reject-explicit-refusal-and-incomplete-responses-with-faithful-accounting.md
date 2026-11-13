# PR 30.1.5 — Reject explicit refusal and incomplete responses with faithful accounting

## Outcome

Implement TBD-12 across all three adapters: required evidence retention, explicit-status classification, rejection before correction/planning/dispatch, zero partial-batch execution, failed outcomes and usage counted once. Preserve evidenced transport retry behavior.

## Scope and sequencing

**Dependencies:** PR 30.1.2, PR 30.1.3 and PR 30.1.4.

**Expected build state:** Provider boundary should compile and pass focused tests. Later schema migrations may temporarily disturb the wider build.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own status/evidence retention and exactly-once accounting, not final-repair history. Reuse the canonical parts work from PR 30.1.2. PRs 30.3.1/30.3.2 must exercise the same classifier through their newly migrated output/planning paths; do not duplicate classifiers in those consumers.

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

- [ ] Explicit refusal/incomplete replies terminate before output correction, planner parsing or any tool dispatch, while complete tool turns and prose controls still follow normal behavior.
- [ ] Rejected returned and SDK-thrown responses retain available original evidence and actual usage exactly once; transport retry, quota and cancellation behavior remain covered.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Controlled HTTP responses through real pipelines: valid-looking incomplete JSON, refusals, incomplete multi-tool responses, complete tool turns, prose controls, failed lifecycle evidence, missing/zero usage and quota/cancellation collisions.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Response status and retry ownership need explicit handling

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

### Contract 2

#### TBD-12: Refusals, incomplete responses, and partial tool calls

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

## Reproduction anchors

Historical status/usage reproduction, using Java 21 and Maven:

```powershell
./docs/maintainers/probes/tbd-12-status/run-current.ps1
mvn -f docs/maintainers/probes/tbd-12-status/rc2/pom.xml test '-Dtest=StatusProbeTest'
```

Current harnesses and the three RC2 tests/15 fixtures are separate demonstrations,
not one migrated pipeline. No candidate evidence-retention repair was verified by
those historical runs. The production integration must close that remaining gate.

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
