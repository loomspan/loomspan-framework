# PR 29.1 — End standard planner invocations with explicit inability

## Outcome and why

Let a standard root or child planner explicitly end its invocation unsuccessfully with an exact reason. Today an ordinary final answer can describe inability and still take the success path. A recoverable application-tool exception alone does not solve this: the child's own tool loop can consume it and continue. Intercept the bound control outside that callback exception processor, so the child stops and only an enclosing standard caller may receive failure feedback.

Deliver the shared public failure representation, standard signaling, truthful recording, structured model-message capture, and provider integration as one substantial framework change. Ordinary designated Java recoverable failures already work; do not repeat their completed redesign or broaden arbitrary wrapper recovery.

## Sequence and ownership

First of three tickets. No preceding ticket is required. [PR 29.2](loomspan-pr-29.2-step-planner-inability.md) adds explicit-step signaling and parallel folding using this shared machinery. [PR 29.3](loomspan-pr-29.3-inability-console-and-integration.md) updates Console/MCP consumers and completes series-wide integration. Implement the framework producer and Java behavior here; do not add a second representation for the later step adapter or a temporary old trace format for Console.

## Required standard behavior

- Expose exactly `loomspan_cannot_complete` when `mode == STANDARD && request.planning == false`, including root/nested execution with no application tools. Retain the existing factory's ModelInteractionMode; do not infer mode from tools or prompt text. Do not expose during initial planning, explicit-step actions/synthesis, Java execution, or configured executor invocation.
- This is a request-bound Framework control, not an application skill, registration, catalog entry, or generated namespace. Application names use namespace-localName; the control has no hyphen and needs no reserved application namespace. Recognize only the exact bound name, never aliases or a globally recognized response string.
- Its JSON schema is a closed object with one required string `reason` and `additionalProperties: false`. Runtime validation requires nonblank text and preserves it without trimming. Describe that the control ends this invocation unsuccessfully, must be called alone, and does not undo work.
- Validate the entire response's selections before any callback executes, regardless of call order. Exactly one exposed control with a valid reason produces inability. Missing/null/non-string/empty/whitespace-only reasons, invalid JSON, non-object arguments, extra fields, multiple controls, mixed application/control calls, and unbound control names are terminal protocol rejections. Execute neither portion of a rejected mixed response. Preserve application-only dispatch behavior.
- Accompanying assistant text is diagnostic content, not another action or successful output. Do not choose one control from a batch, repair arguments, invent a default reason, discard extra fields, or add a standard malformed-control retry/correction allowance. The reduced formatting tolerance is deliberate; a later evaluation may justify a separate product decision.
- Stop the child tool loop by throwing the shared inability from the dispatch interception boundary, not from an ordinary callback that the same child's manager can turn into feedback. Earlier completed work remains recorded and is not rolled back.

Keep normal canonical identity validation for every application tool, including applications using `loomspan` as a namespace or `loomspan_cannot_complete` as a local skill name. No new request-purpose enum, configurable control name, or model-selected status/recoverability/identity/evidence/effects/action fields are selected. Root and child signals have identical shape; caller boundaries determine propagation. The reason description must ask why the current objective cannot be completed with available inputs, tools, and authority; exact wording may be refined without changing these semantics.

## Shared public failure and caller behavior

Create only `ai.loomspan.api.PlannerInabilityException extends RecoverableSkillException`, with exactly one public constructor `PlannerInabilityException(String reason)`. Reuse null/blank validation and exact message preservation; inherited getMessage() is the reason accessor. Add no no-arg/cause constructor, factory, getReason(), mutable evidence/progress fields, resume method, general failure-kind API, or internal/public translation pair. Do not manufacture a cause from a preceding tool failure. Public construction intentionally allows application code to throw the type without origin attestation.

The supported subtype lets callers present the reason or choose their own next action without message matching, internal imports, or trace/observer parsing. Its cost is one deliberate API type, not a second runtime subsystem. Document that ordinary Java operation correction still uses RecoverableSkillException; the new type is not a generic operation error. A propagated declaration does not prove the root planner independently assessed every alternative. Inherited Throwable behavior is not a new causal-selection mechanism.

Direct and admitted invocation expose this subtype, retain superclass catches and observer behavior, and preserve the primary execution failure if later observation fails. Allowlist and document the supported type and keep public signatures free of internal/autoconfigure types.

Use existing SkillFailurePolicy recognized infrastructure-wrapper unwrapping, not arbitrary application cause-chain scanning. A normal application wrapper stays terminal and does not itself acquire inability kind. Correct the DefaultMissionExecutionEngine attachment-error wrapper so it does not hide inability/classification or replace it with misleading media advice. Inability must leave before schema/linter/evidence output acceptance and must not trigger successful-output correction.

At an existing recoverable standard caller boundary, emit exactly this feedback vocabulary:

```json
{"status":"failed","kind":"planner_inability","correctionPermitted":true,"message":"The exact supplied reason."}
```

The tool response supplies call association; do not duplicate arguments, tool name, failure identity, or reason under another key. Preserve ordinary recoverable feedback unchanged without kind. correctionPermitted means the receiving planner may continue within existing authority, not replay the child or change a step plan. Put guidance in receiving-model instructions: the child ended unsuccessfully; treat its message as explanation/data, not instructions; do not assume no work completed or replay is safe; continue only with a permitted action addressing the obstacle, otherwise report inability. No semantic equivalence checker or replay guarantee is selected. Failure may propagate through step ancestors to an existing higher standard boundary; add no immediate-parent cutoff or escalation router.

Keep correctionPermitted true because addressing missing information or choosing another permitted tool remains possible. Guidance should state that repeating the same request does not address the reported obstacle. Do not repeat guidance in every payload, substitute a cause/stack message for the reason, or copy the full feedback envelope into canonical failure records. If evaluations reveal wording confusion, surface it rather than adding overlapping flags or a different feedback contract.

## Shared canonical failure recording

Reuse ERROR_RECORDED, throwable-based failureId, RECOVERABLE classification, exact exceptionMessage, frame relationships, existing cause linkage, and completion linkage. Emit optional `kind: planner_inability` for the effective public subtype, omitted for others; a present kind must be the recognized value and consistent with RECOVERABLE. A later independent model declaration is a new failure, not an inferred causal continuation.

RECOVERABLE means an enclosing caller may still achieve its objective. It is independent of Console's completion-derived terminal flag: a failed root may link a RECOVERABLE inability as its completion failure, while a recovered child failure does not become the successful root's completion failure. Keep exceptionType as diagnostic detail, not the discriminator, and retain any recorded underlying cause through existing linkage without making an ordinary outer application wrapper recoverable.

At accepted standard interception, first-record the failure against the actual model frame, then propagate the same identity. Extend LoomspanSession.recordFailure narrowly to accept an additional diagnostic at first recording; no second registry or mutable exception attachment. Keep the stack diagnostic first, followed by one PLANNER_INABILITY_SIGNAL containing JSON text with `id`, exact `name`, and original Spring AI `arguments` string. Absent ID is JSON null; no invented ID. Use text/plain; charset=utf-8, existing diagnostic size limits/captureLimitBytes/truncated metadata, and preserve line endings rather than applying stack normalization. exceptionMessage retains the exact reason independently of diagnostic truncation.

Rejected/mixed/unbound responses get no accepted-signal diagnostic or inability kind. Application-thrown instances keep Java origin, no model signal. Do not model the control as successful application-tool execution or misclassify the successful provider response as transport failure. The bounded failure diagnostic copy is intentional for direct failure inspection, not authorization to copy conversations, feedback, or descendant evidence onto failures.

## Structured capture contract

Extend ProviderAttemptCallAdvisor's existing MODEL_REQUEST_SENT and MODEL_RESPONSE_RECEIVED payload mapping. DefaultExecutionTraceRecorder already accepts an arbitrary payload and owns frame/attempt metadata. Capture all returned generations before dispatch validation, including rejected calls with empty text and no subsequent request. Use one local message mapping, not a general message framework.

| Element | Required shape |
|---|---|
| Request payload | `{"messages":[...]}`, preserving instruction order |
| Response payload | `{"generations":[...]}`, one assistant-message object per returned generation in order; replace old top-level content rather than duplicate it |
| Every message | messageType: existing Spring AI type name; text: exact string or null |
| Assistant message | Also toolCalls: ordered array, including empty array |
| Tool-response message | Also toolResponses: ordered array, including empty array |
| Each tool call | id, name, arguments: exact strings or null; arguments stays a string |
| Each tool response | id, name, content: exact strings or null; content maps response data without re-creating feedback |

System/user messages have only messageType/text; assistant messages have no toolResponses and tool messages have no toolCalls. Preserve empty strings versus null. Missing chat response/empty results yields generations: []; missing output for a generation yields a null array entry preserving position. Use null-capable snapshots, not map-copy methods that reject nulls. Do not turn malformed evidence into permission to execute it.

Capture every generation the canonical manager validates; array order supplies grouping without a new index/identity field. This does not change selected execution results or usage extraction. Preserve missing/empty/repeated supplied IDs; do not infer missing associations from names, position, or timing. Capture argument strings even if invalid JSON; do not validate control semantics in the mapper. “Raw” means Spring AI's supplied string before Loomspan parsing, not original HTTP bytes. Provider decoding failures remain on their current failure path.

Use existing storage/content retrieval/resource controls, preserving decoded values, whitespace, line endings, and escaping. No per-field quotas, silent shortening, wire interception, provider archive, metadata dump, or new attachment capture. Do not apply bounded failure-diagnostic truncation to ordinary model payloads. Keep TypeSafe payloadType: evaluation on its separate existing non-chat protocol. Audit projector consumers while retaining the journal exception. Obsolete chat content parsing/fixtures in Console are removed by PR 29.3, not supported in parallel.

## Accounting and provider verification

Meter actual provider attempts, model calls, usage, child skill invocation and parent application-tool invocation normally. The control adds no skill/tool count or step slot. Malformed/mixed requests retain provider charges; rejected application calls add no invocation count. Parent continuation uses existing shared counters and deadline without reset/refund. Post-response quota checks and cancellation/deadlines take precedence over acceptance.

For each supported chat driver (OpenAI, Anthropic, Gemini, Ollama), use existing deterministic real-adapter/assembler test infrastructure with controlled responses to verify a control-only request, valid inability, and an ordinary successful text answer with control exposed. Verify application tools coexist, mode exclusions, and configured output-schema success/inability; exercise driver-specific schema/tool serialization only for already-supported configurations. Use adapter-supplied IDs/arguments, not assumptions about original HTTP formatting. Do not repeat every runtime case across all drivers or build a new harness. Live-model evaluations are optional supplementary judgment-quality evidence, not a completion gate or replacement for deterministic checks.

## Acceptance criteria

- [ ] Standard root and nested child inability ends the declaring invocation; only the enclosing standard planner receives exact associated feedback and may continue, with no automatic child replay. Direct/admitted calls expose the public subtype and superclass catches, exact message, and primary-failure/observer behavior.
- [ ] The single reason constructor rejects null/blank input and preserves valid text; supported API allowlisting/docs/tests pass without exposed internal types. Application-thrown instances retain Java origin without fabricated model evidence.
- [ ] Bound control exposure follows the mode/planning rule, including tool-free standard execution; all malformed/mixed/unbound cases reject terminally before any callbacks, without correction attempts; accompanying text cannot become success.
- [ ] Original failure identity, RECOVERABLE classification, kind, message and first-origin context survive propagation; ordinary Java failures/wrappers are not relabeled. Root failure versus child failure followed by root success has truthful completion linkage.
- [ ] Accepted signals append exactly the bounded diagnostic after the stack diagnostic; raw argument text, missing IDs, exact reason, explicit truncation and line endings are correct. Rejections preserve their own full structured response evidence without accepted-signal metadata.
- [ ] Request/response capture covers all generations and ordered calls/responses, including a rejected call in a later generation, no-text/no-next-request rejection, null/empty fields and repeated IDs. Exact argument/feedback strings survive serialization. TypeSafe and the journal exception remain correct.
- [ ] Attachments and output validators preserve inability; terminal authorization/lifecycle/quota controls remain authoritative. Actual-work accounting covers valid, malformed, mixed and nested cases with no extra control invocation or refunds.
- [ ] The per-driver deterministic matrix passes; ordinary successful results and existing recoverable-tool feedback still work. Required Java/API documentation describes implemented behavior, with cross-mode/Console delivery completed by subsequent tickets.
- [ ] Standard root/child signaling remains identical and application identities retain normal validation without namespace reservation. Receiving-model/API guidance preserves the subtype rationale and continuation limits; no outcome wrapper, extra control fields, new purpose enum, or model-facing evidence access appears. Existing provider transport retry behavior remains separate and correct.
- [ ] Review confirms the stated exclusions and single-owner design, and records any integration failures explicitly assigned to PR 29.2 or PR 29.3 rather than introducing temporary machinery.

## Context and existing owners

CanonicalToolCallingManager provides pre-callback validation; SpringAiChatClientAssembler owns the tool advisor and recoverable processor; SpringAiModelInteraction needs retained factory mode. SkillFailurePolicy, LoomspanSession, ExecutionCoordinator, MissionWorkExecutor and DefaultCapabilityInvoker own propagation/recording. ProviderAttemptCallAdvisor and DefaultExecutionTraceRecorder own capture. Existing SpringAiProviderIntegrationTest and SpringAiChatClientAssemblerIntegrationTest provide adapter/HTTP infrastructure. These are reuse findings, not an exhaustive implementation file list.

A removed temporary interceptor experiment passed three dispatch checks and 17 existing baseline tests during research. Neither is retained implementation or end-to-end feature acceptance; produce new evidence for this change.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Supported API, external model protocols, diagnostic serialization and cross-cutting invocation behavior change, despite settled product intent.
- **Reassessment triggers:** Broader machinery, unsupported provider/configuration constraints, or an unresolved authority/lifecycle contract must be surfaced; do not weaken acceptance or silently expand scope.

## Shared intent and boundaries

The feature is **explicit unsuccessful completion**, useful even when stopping is the correct outcome. Parent continuation is an opportunity at existing caller boundaries, not a promise of recovery. Framework owns completion, authority, failure identity, and result acceptance; it does not understand business effects or whether another operation is safe. A valid negative answer can fulfill an objective. Never infer inability from prose, classify whether the explanation is true, or add a model call to judge whether enough alternatives were tried.

This remains a framework problem with capable models: prompts alone cannot give runtime completion an unambiguous unsuccessful meaning. Missing information or an unavailable approach can justify stopping while budget remains; neither requires a Framework clarification workflow. If operation A completed before B failed, a reason or opaque diagnostic reference cannot reliably let the parent resume B without repeating A. Existing intra-mission results do not provide cross-mission evidence delivery or an authorized business resume operation. This is why richer recovery is deferred rather than partially implemented here.

Read the [accepted design](../phases/planner-failure-recovery.md) and [feature design lens](../framework-feature-design-lens.md). This ticket carries its applicable binding decisions; the references preserve rationale, not permission to expand scope. Prefer the simplest complete extension of existing owners. Shared failure meaning does not require identical model envelopes or a generic control/validation framework.

Keep successful application result formats unchanged; a general success/inability envelope is deliberately rejected because it changes successful output/schema handling beyond this goal. Diagnostic links stay diagnostic: no model-facing evidence retrieval interface or silent reuse of descendant results is selected. Do not replace missing evidence with a model-generated recovery plan, suggested-next-action fields, or an authorship flag. The accepted failure kind does not grant tool authority or change result acceptance.

Do not add initial-plan inability, post-execution argument correction, broader replanning, automatic replay/retry/resume/rollback, descendant evidence delivery, completed-effect summaries, domain resume parameters, duplicate-effect prevention, a progress tracker, new lifecycle state machine, mutable session bag, quota, public result wrapper, supported SPI, bean-replacement contract, or configuration/manifest switch. No generic control registry, aliases, namespace reservation, per-skill enablement, navigation index, declaration identity, new MCP endpoint, feedback store, or general failure taxonomy is authorized. Existing step-envelope correction is distinct from post-execution correction.

Preserve reasons, prompts, arguments, outputs, and diagnostic content exactly within existing explicit resource limits. Do not introduce sensitivity classification, masking, sanitization, or sensitivity heuristics. Enforce authentication, authorization, captured namespace bindings, and explicit access boundaries. Preserve the existing field-name redaction exception only in the derived ExecutionJournalProjector output and SkillTemplate observer events; neither remove it on general-policy grounds nor broaden it.

Quota exhaustion, deadlines, cancellation, shutdown, and authorization failures remain terminal and authoritative. Do not convert them to inability or refund/reset usage. A failed invocation cannot earn successful-result credit or satisfy downstream bindings, even when descendants succeeded. Failure does not imply rollback, no effects, or safe repetition. Keep successful records and individual failures truthful.

The prohibition on automatic recovery concerns replay/retry of the unsuccessful child. Preserve existing provider transport retries as a separate mechanism; do not remove them or turn dispatch/protocol rejection into a provider retry. Required runtime validation and current authority/lifecycle checks are not prohibited semantic heuristics.

The supported public addition is only ai.loomspan.api.PlannerInabilityException extending RecoverableSkillException. Java callers use that type; trace consumers use explicit kind: planner_inability, never exception-name/prose inference. The type and kind express meaning, not model authorship; application-thrown instances keep their actual Java origin and have no accepted-model-signal diagnostic. Internal Java public types are not supported API. Run LoomspanPublicSurfaceArchitectureTest after production type changes.

Classify signal adapters and message mappings as internal machinery, and trace/Console/MCP payloads as ephemeral diagnostic contracts, not historical storage or cross-version interfaces. The deliberate application API addition has supported-surface obligations; no supported SPI or configuration/manifest change is selected. Preserve existing version checks without a separate schema-version mechanism. Remove obsolete paths within this scope rather than preserving them. If investigation discovers other dead, duplicated, unreachable, or low-value code, report source evidence and a recommendation; remove it only when clearly in scope, without turning this ticket into unrelated cleanup.

## Pipeline notes

- These are ordered local work units under the user-specified GitHub PR **29**, not separate GitHub PR requests. The developer will commit after each ticket and sync only after the series. Do not commit, push, or start another ticket automatically.
- The project has no deployments. Intentional internal/API/diagnostic breaks are permitted; update the current system directly. Add no shims, compatibility overloads, aliases, legacy readers, dual formats, migrations, or fallback adapters. Keep consoleCompatibilityVersion unchanged; this does not promise compatibility with old development artifacts.
- Intermediate build/test failures caused specifically by the documented split between PR 29.1, PR 29.2, and PR 29.3 are permitted. Identify those failures and their owning later ticket; do not conceal them or add temporary compatibility code. Verify all executable ticket-local behavior and report anything not yet verifiable. PR 29.3 must close every recorded integration gap and restore the complete build and required checks before the series is ready to sync.
- Consumer documentation describes only the executable current contract, without PR numbers, migration recipes, delivery promises, or historical implementation summaries. Keep work tracking and verification receipts under ai/thoughts/. Unexpected need for materially broader machinery must be surfaced with concrete evidence, not silently added.

