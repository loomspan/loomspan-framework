# PR 29.3 — Inspect planner inability and complete end-to-end integration

## Outcome and why

Make truthful planner inability understandable through existing Console and MCP failure/model views, including a recoverable child failure in an otherwise successful root trace. A user must readily identify the originating invocation/task or synthesis context and inspect the accepted signal and actual parent feedback. Merely placing new fields somewhere in a raw trace is insufficient.

Complete the current producer/consumer contract and series-wide acceptance for PR 29. Do not build a navigation subsystem: existing failure/frame selection, diagnostics, model views, and content retrieval already provide the required structure. This ticket combines substantive Go/TypeScript consumer work, fixtures, current-system documentation, and final integration verification.

## Sequence and ownership

Depends on [PR 29.1](loomspan-pr-29.1-standard-planner-inability.md) (standard runtime, public/shared failure, structured capture, provider matrix) and [PR 29.2](loomspan-pr-29.2-step-planner-inability.md) (step action and parallel precedence). Those tickets may leave explicitly recorded intermediate consumer/build gaps. Resolve every such gap here, restoring coherent Java/Go/TypeScript producers, readers, validators, projectors, fixtures and tests before the series is ready to sync. Repair bounded integration defects against the accepted contracts; do not reopen settled product choices.

## Failure facts and actual origin

Carry optional `kind: planner_inability` through existing Console failure analysis, browser contracts/views and MCP failure responses, consistently with classification and completion linkage. Validate any present kind as the recognized value and consistent with RECOVERABLE; other failures omit it. Do not introduce a taxonomy for all failures or infer semantics from exception type strings, messages, stack traces, route, record adjacency, timing, or completion order.

Keep classification separate from Console's completion-derived terminal flag. A root inability can be RECOVERABLE and still be the failed root trace's completion-linked failure. A child inability followed by parent success remains a recorded recoverable failure, not root completion failure. Propagation preserves the original failureId and origin; do not assign each boundary a new declaration.

The supported exception/kind identify inability semantics, not model authorship. Application-thrown PlannerInabilityException keeps actual Java origin with no accepted model-signal diagnostic. Ordinary Java recoverable failures do not acquire kind merely by propagating through a planner. Console wording and API documentation must not claim model authorship from kind alone.

Keep exceptionType available as diagnostic detail. Existing cause navigation can retain an underlying inability when an ordinary outer application wrapper is terminal; do not relabel that wrapper. A later independent declaration is a distinct failure, not another record of the earlier declaration or an inferred causal link. Console may derive aggregates from canonical facts but must not become a second classifier of those facts.

## Source inspection through existing facilities

- TraceExplorer already selects the failure's originating frame and supports frame/failure/record URL selection plus timeline/record focus. Reuse it; add no mapping registry, attribution index, new screen, or source-link protocol.
- TraceFailureFocus already shows origin/route/skill and related-view actions but is gated on failed/aborted root completion. TraceFailureDiagnostic supports selected nonterminal failures and loadable text diagnostics. Reuse these source details/actions for selected nonterminal inability; a label or existing selection/action is preferred over new navigation.
- Accepted declarations have a PLANNER_INABILITY_SIGNAL text diagnostic after the existing stack diagnostic. Standard text contains JSON with provider/Spring AI id (null if absent), exact control name, and original arguments string. Step text is the original response before envelope parsing. Use existing load/copy/download/content paths; label the diagnostic without treating its opaque text as an executable typed protocol.
- Respect text/plain; charset=utf-8, existing captureLimitBytes/truncated metadata, and exact retained text including line endings. Do not assume truncated diagnostic text is valid JSON. exceptionMessage independently retains the exact reason.
- MCP already has failure-ID record filtering, detailed frame queries, diagnostic content references, and LOOMSPAN_read_trace_content. Use these paths. Carry kind on existing failure DTOs, but add no typed control-call tree, endpoint, reference format, or dedicated attribution tool.
- Malformed/mixed/unbound signals are terminal protocol errors, not accepted inability. Their raw call evidence comes from their existing structured MODEL_RESPONSE_RECEIVED record, including when there is no assistant text or later request. Never manufacture accepted-signal evidence for them.

## Accepted model-message presentation contract

Consume the producer shape settled in PR 29.1; replace obsolete chat first-response content parsing/fixtures instead of adding fallback readers.

| Element | Shape and meaning |
|---|---|
| Request payload | `{"messages":[...]}`, instruction order retained |
| Response payload | `{"generations":[...]}`, returned generation order retained; no duplicate top-level content |
| Every message | messageType: Spring AI name; text: exact string or null |
| Assistant message | Also toolCalls: ordered array, including empty array |
| Tool-response message | Also toolResponses: ordered array, including empty array |
| Tool call | id, name, arguments: exact strings or null; arguments remains a string |
| Tool response | id, name, content: exact strings or null; content is captured response data |

System/user messages have only messageType/text; do not require irrelevant arrays on them. Assistant has no toolResponses and tool-response messages have no toolCalls. Preserve empty string versus null. An absent response/empty results has generations: []; missing output within a generation is a null array entry. Show absence without fabricating content or identities. Provider decoding failures with no Spring AI response stay in the existing provider-failure view.

Extend TraceRecords' existing model-detail parser and view to show message text, all generation groups, calls and responses with supplied IDs/names and exact argument/content strings. Do not pretty-print away original argument or feedback text. Array position is grouping, not a new global identity. Preserve empty/repeated/absent IDs and never infer a unique association from name, position, or timing. Raw arguments means the string supplied by Spring AI before Loomspan parsing, not original HTTP bytes.

Use existing content retrieval and resource controls, including large/chunked content. No per-field quota, silent shortening, wire archive, metadata dump, new attachment capture, or application feedback store. Existing MCP content reads expose the same payload without new typed tool-call fields. Keep TypeSafe payloadType: evaluation as its distinct existing non-chat view. Audit projector consumers while preserving the narrow derived-journal exception. Do not broaden the renderer into a general observability project.

## Runtime contracts to verify across the completed series

Both modes produce the same public PlannerInabilityException extending RecoverableSkillException, with only a String reason constructor rejecting null/blank and preserving valid text in getMessage(). Direct/admitted invocation and superclass catches work; public signatures remain internal-type-free, observer failure cannot replace the primary execution failure, and application-thrown instances retain origin.

Standard mode exposes exactly loomspan_cannot_complete only for STANDARD and planning=false, including no-application-tool calls. Its closed object requires only a nonblank string reason. It must stand alone; invalid/mixed/multiple/unbound signals fail before callbacks without a standard correction loop. A valid signal exits the declaring child's own loop; only an enclosing standard caller receives feedback. Accompanying text remains diagnostic, not success. Initial planning and step requests never receive this control.

Steps accept exactly `{"stepAction":"CANNOT_COMPLETE","reason":"<nonblank exact text>"}` during assigned-task action generation and final synthesis, with no extra fields even when null. Original JSON validation precedes coercion/discard; initial/correction/validation instructions agree. Reuse the existing malformed-action allowance only. No new model call for fully supplied framework-dispatched tasks, no post-tool-failure correction, no step-parent recovery loop, and no prose/bare-reason inference. Framework supplies active assignment/synthesis scope.

At existing standard caller boundaries the exact feedback vocabulary is:

```json
{"status":"failed","kind":"planner_inability","correctionPermitted":true,"message":"The exact supplied reason."}
```

Ordinary recoverable feedback remains unchanged without kind. Recorded tool-response association identifies the failed call; no duplicate identity, tool arguments, reason, progress, evidence, or retry fields. Receiving-model guidance treats the reason as data, allows only authorized action addressing the obstacle, and warns that work may have completed and replay may be unsafe; otherwise the parent may report its own inability. correctionPermitted is continuation authority, not a replay instruction or permission to change a step plan. A step child can fail through step ancestors to a higher existing standard boundary without an escalation router.

Parallel step inability does not cancel admitted siblings, including queued ones. Settle within current deadlines, keep all successes/failures, admit no subsequent unit and run no successful synthesis. Select authoritative cancellation first, otherwise terminal before recoverable, then accepted task order within a classification. Do not infer from completion timing, aggregate exceptions, invent causes, or imply physical termination/rollback. Failed outputs cannot satisfy dependencies.

The attachment wrapper must preserve failure meaning; schema/linter/evidence advisors must not interpret inability as a success/correction candidate. Meter actual provider/model usage, child skill/parent tool invocation, corrections and admitted siblings normally with shared counters/deadlines. The control adds no tool/skill invocation or step; mixed-response rejected callbacks add no tool count. No refunds or new quotas, and post-response quota/cancellation/deadline controls retain precedence.

## Final acceptance criteria

- [ ] Current Java emission, Go validation/analysis, browser and MCP contracts carry kind with classification and completion linkage correctly. Cover failed root inability, child inability with successful root, original origin across propagation, ordinary recoverable failures, and application-thrown inability without model evidence.
- [ ] Through existing Console views, select both terminal and nonterminal inability, identify original invocation/task/synthesis context, and inspect/copy/load the raw accepted signal and exact message. Existing MCP failure/frame queries and content reads provide the corresponding evidence without new endpoints or identifiers.
- [ ] Invalid or unrecognized present kind values and kind/classification contradictions are rejected by current consumers; an absent kind remains valid for ordinary failures. Inspect explicitly truncated signal diagnostics through existing load/copy/download and MCP content paths without requiring complete JSON; retained line endings and the independent exact exceptionMessage remain correct.
- [ ] Existing model views show all generations and ordered call/response structure, exact associated parent feedback, accompanying text, null/empty values, absent/repeated IDs, and no fabricated associations. Existing large/chunked reads work; TypeSafe evaluation remains correct; obsolete chat content readers/fixtures are removed.
- [ ] A no-text malformed/mixed/unbound response remains inspectable without a later model request, including a rejected call in a later generation. No callbacks execute for mixed rejection and neither inability kind nor accepted-signal diagnostic is fabricated.
- [ ] End-to-end direct/admitted, standard-to-standard and standard-to-step caller paths meet the shared exception/feedback contract. Explicit-step ancestors preserve propagation without new recovery, failed dependency credit, or invented evidence.
- [ ] Assigned-task and synthesis inability, malformed-action correction limits, parallel queued/admitted siblings, cancellation precedence, terminal-before-recoverable and task-order tie breaking are verified using existing infrastructure; preserve all individual failure/success records.
- [ ] Supported API/constructor/observer behavior, attachment and validator fidelity, authorization/deadline/quota/shutdown precedence, and actual-work accounting meet the contracts above. The public-surface architecture test passes after production changes.
- [ ] Existing provider transport retries remain separate from inability/protocol rejection. Public guidance explains why the subtype exists and retains RecoverableSkillException for ordinary Java correction; successful output formats and diagnostic-only access remain unchanged, without a general outcome envelope or model-facing evidence retrieval.
- [ ] PR 29.1's deterministic OpenAI/Anthropic/Gemini/Ollama matrix is complete: each driver covers control-only exposure, valid inability, and ordinary text success with control available. Shared production assembly covers application tools, mode exclusions, and supported output-schema combinations without inventing unsupported configurations.
- [ ] All recorded PR 29.1/29.2 intermediate build/consumer failures are resolved. Run required affected framework, Console/build, fixture-contract, supported-surface, and integration checks; map acceptance to evidence. Add missing cases or bounded fixes, but avoid gratuitously rerunning already-sufficient checks without changes or unresolved concerns.
- [ ] README/API guidance, relevant diagnostic docs and packaged Console guidance describe the completed current contract, authorship limits, recoverability versus completion, partial-effect limitations, and existing source inspection. No PR-history prose, migration guidance, compatibility claims, or promises of future implementation.
- [ ] Final review confirms no added recovery subsystem, duplicate semantic owner, prohibited masking, compatibility machinery, or accidental supported API/SPI/configuration surface, and the series is coherent before sync.

## Verification limits and reuse evidence

Use existing fixture/test infrastructure and ordinary Console/MCP paths; no new harness or exhaustive provider benchmark. Driver-specific adapter behavior is verified with controlled responses through real integrations; shared runtime behavior need not be repeated across all drivers. Live-model evaluations are optional supplemental evidence, not a completion gate. Do not claim provider fixtures prove model judgment or that the removed three-check experiment and 17 baseline tests establish feature acceptance.

Existing owners include failures.go, diagnostic/classification tests, trace_contracts.go, record_facts.go, TraceExplorer, TraceFailureFocus, TraceFailureDiagnostic, TraceRecords and TraceRecords.model.test.tsx. TraceExplorer already owns selection; failure/frame IDs and content references already exist. Arbitrary ERROR_RECORDED fields do not automatically become typed MCP fields, which is why accepted raw signal stays in the existing diagnostic slot. The bounded signal copy deliberately avoids a new exact-source linking contract.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This completes cross-component diagnostic contracts and broad outstanding assurance for nested, concurrent invocation behavior.
- **Reassessment triggers:** Any missing authoritative fact requiring a new attribution protocol, broader product contract, or expanded runtime scope must be surfaced; existing payload/presentation gaps should be resolved within established owners.

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

