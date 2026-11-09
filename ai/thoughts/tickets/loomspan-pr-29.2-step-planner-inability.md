# PR 29.2 — Report explicit-step inability with truthful parallel failure precedence

## Outcome and why

Let an explicit-step worker say that its assigned task cannot complete, and let final synthesis say the objective cannot be fulfilled, without pretending either is successful output. Required task failure makes the mission unsuccessful even if siblings succeeded. Preserve a standard ancestor's existing opportunity to continue, but do not add a recovery loop to an explicit-step parent.

Implement this as a substantial extension of the existing action parser/validator, step lifecycle, and admit/join/fold coordinator. The deliberate sibling policy avoids a new fail-fast cancellation state machine and avoids hiding terminal failures behind recoverable inability. It accepts that already-admitted work can continue consuming resources or causing effects while the coordinator settles the unit.

## Sequence and ownership

Depends on [PR 29.1](loomspan-pr-29.1-standard-planner-inability.md), which supplies the public exception, shared failure kind/feedback, first-record diagnostic extension, standard control, structured model capture, and provider checks. Reuse those owners directly. [PR 29.3](loomspan-pr-29.3-inability-console-and-integration.md) completes Console/MCP consumption and final series verification. Do not rebuild the standard control or introduce another inability type.

## Accepted action and validation

Use exactly the same closed two-field action in assigned-task and final-synthesis contexts:

```json
{"stepAction":"CANNOT_COMPLETE","reason":"Required information is unavailable."}
```

Require exact uppercase action name, an object, and a nonblank string reason preserved unchanged. Reject every extra field, including null taskId/toolName/toolArguments/finalResponse fields. Framework supplies task/mission attribution; a model-selected task ID or cancellation target adds no authority and is not allowed.

Omitting taskId deliberately avoids model copying and two context-dependent inability shapes. Successful completion of every tool does not prove that the returned information can fulfill the objective, which is why synthesis must retain the same inability option. Acceptance records the planner's declaration, not proof that completion was objectively impossible.

Validate original JSON before unknown fields can be discarded or values coerced. StepAction currently ignores unknown properties; merely adding a nullable reason is insufficient. Reuse current parsing/validation owners and do not tighten or redesign unrelated successful action envelopes. Validation is structural/contextual, not an assessment of whether the reason is true, the planner tried enough tools, or another approach exists. Existing assignment/active-lifecycle checks remain required.

Recognize inability before successful final-response processing, output binding composition, and schema/linter/evidence correction. Keep existing bare final-output behavior; a bare reason object or prose is not inability. Accepted inability invokes no application tool and yields no successful final result.

Update initial instructions in StepPromptBuilder, contextual rejection guidance in StepActionValidator, and bounded correction guidance in StepActionCorrection together. Assigned workers may return CALL_TOOL or CANNOT_COMPLETE; synthesis may return FINAL_RESPONSE or CANNOT_COMPLETE. An invalid action may be corrected to valid inability using only the existing malformed-action allowance; exhausted correction is terminal. No extra allowance, post-tool-execution correction, or generic validation/prompt framework is selected.

## Placement and propagation

| Context | Required behavior |
|---|---|
| Model generates arguments for its assigned task | Accept inability instead of CALL_TOOL; record a failed assigned-task outcome |
| Model performs final synthesis | Accept inability instead of FINAL_RESPONSE; fail invocation while preserving completed task results |
| Initial plan generation | No new inability signal/checkpoint |
| Framework-dispatched task with fully supplied arguments | No extra model call/checkpoint |
| After tool failure | Preserve existing failure propagation; no new model correction opportunity |

An assigned worker reports inability only for its Framework-owned assignment. It cannot stop arbitrary siblings or ancestors, replace tools, revise dependencies, or decide required work is optional. In sequential execution, fail without admitting subsequent tasks. After any unsuccessful execution unit, admit no later unit and perform no successful final synthesis.

Throw the existing public PlannerInabilityException with the exact reason. Direct/admitted root invocation exposes it; a standard parent receives the shared feedback through its ordinary child callback, including when a higher standard ancestor is reached through explicit-step ancestors. Explicit-step parents propagate rather than gaining a post-failure recovery loop. Add no ancestor-search mechanism.

The shared feedback is `{"status":"failed","kind":"planner_inability","correctionPermitted":true,"message":"<exact reason>"}`, associated with the existing failed tool response. correctionPermitted allows a standard recipient to continue within authority, not replay or modify an accepted step plan. Reasons are data, effects may already have occurred, and an unjustifiable continuation may correctly end in another inability. Add no evidence/progress/retry fields and do not manufacture cause links between earlier failures and the declaration.

## Admitted parallel siblings and failure selection

Keep existing admit/join/fold ownership. An inability declaration fails its task but does not itself initiate sibling cancellation. Let all admitted siblings settle under existing deadlines and terminal controls, including queued work not yet physically started. Do not introduce scheduling-sensitive cancellation of queued siblings. Preserve completed siblings as successes, all individual failures as failures, and refuse failed outputs as dependency data.

Select the propagated failure in this order:

1. Preserve an authoritative lifecycle cancellation cause when present.
2. Otherwise select a terminal failure before any recoverable failure using shared classification.
3. Within the same classification, select by accepted task order, never completion timing.
4. Keep every individual failure recorded while propagating one selected throwable. No aggregate exception, concatenated reasons, or invented causal chain.

The current first-failure-in-task-order behavior must change to terminal precedence for the shared fold, not only a special case for inability. Unknown effects remain unknown. Waiting does not extend deadlines or guarantee physical termination of external work; terminal timeout/shutdown/cancellation controls remain authoritative. Do not introduce partial-progress snapshots, replay, rollback, or new cancellation states.

Parent feedback may wait for admitted siblings. This latency/resource tradeoff is intentional: reuse existing coordination and avoid unnecessarily interrupted operations with uncertain effects. Do not replace it with fail-fast cancellation to make inability appear more responsive.

## Failure evidence and accounting

At acceptance, first-record the effective exception in the existing failure registry using the actual step context and Framework-owned task assignment or synthesis context. Reuse that failureId through propagation. Append one PLANNER_INABILITY_SIGNAL after the stack diagnostic, containing original model-response text before parsing/fence unwrapping/normalization. No fabricated tool-call ID, new declaration identity, event family, registry, or mutable exception field.

This one bounded response copy is intentional even though model-response capture already exists: it supports direct failure inspection without a new exact-source linking protocol. Use text/plain; charset=utf-8 and existing diagnostic captureLimitBytes/truncated metadata, preserving line endings within limits. The exact reason remains independently in exceptionMessage. Do not copy conversations, descendant evidence, or parent feedback onto the failure.

Reuse ERROR_RECORDED with RECOVERABLE classification, optional kind: planner_inability and existing completion linkage. A RECOVERABLE inability may be the failed root trace's completion-linked failure; classification is independent of root terminal linkage. Ordinary failures passing through a planner do not acquire kind. Application-thrown instances retain Java origin without an accepted-model-signal diagnostic. Malformed step actions remain rejected-action evidence, not accepted declarations.

A declaration uses the already-admitted task/synthesis slot, adds no tool invocation/step, and changes no max_steps semantics. Meter all actual model/correction/provider calls and admitted sibling work normally. Keep already-counted child skill and parent tool invocations, shared counters, and continuing deadlines. No refunds, reset budgets, or control-specific quotas.

Existing post-response quota, cancellation, and deadline checks retain precedence over accepting a step declaration. Preserve ordinary STEP_ACTION_PROPOSED and model records alongside the bounded diagnostic copy; do not replace them with new attribution events or derive accepted origin from the nearest record.

## Acceptance criteria

- [ ] Both assigned-task and synthesis contexts accept exactly the two-field action and preserve the reason; wrong types, blank/missing reason, extra fields including null, and malformed JSON use only existing bounded correction, then terminal failure on exhaustion.
- [ ] Initial prompts, validation errors, and correction instructions agree on permitted actions. A malformed action can correct directly to inability without an intervening tool call or successful response; unrelated existing action/bare-final-output behavior remains correct.
- [ ] Valid inability bypasses all success/output-binding/schema/linter/evidence acceptance, invokes no application tool, grants no successful-result/dependency credit, and creates no extra slot/model checkpoint for framework-dispatched work or after a tool throws.
- [ ] Direct/admitted step roots expose the shared subtype and exact reason. A step child fails to a standard parent through exact associated shared feedback; explicit-step ancestors do not gain recovery/replanning and preserve failure identity to an existing higher standard boundary.
- [ ] Sequential failure prevents later admission. Parallel admitted siblings, including queued tasks, settle under current deadlines; their successes/failures are retained, with no later unit/successful synthesis or new cancellation behavior caused solely by inability.
- [ ] Folding preserves authoritative cancellation, terminal-over-recoverable precedence, then accepted task order within classification regardless of completion order. Multiple inability declarations remain individually recorded while one failure propagates without aggregate/causal fabrication.
- [ ] First failure recording retains actual assignment/synthesis origin and bounded original response diagnostic after the stack diagnostic, including exact line endings and explicit truncation. Propagation adds no new signal and an application-thrown subtype acquires no fabricated model action.
- [ ] Actual-work counts, max_steps, shared quota/deadline and terminal authorization/cancellation/shutdown controls remain correct for valid, corrected, nested and parallel cases; no control invocation count/refund or failed dependency acceptance.
- [ ] An otherwise valid declaration is not accepted over an authoritative post-response quota/deadline/cancellation failure or inactive assignment. Existing proposed-action/model evidence remains available, and completed tasks do not force synthesis success when the model declares inability.
- [ ] Existing successful step behavior and ordinary failure propagation remain correct. Supported-surface checks pass after production changes; current Java guidance documents the step limitation and semantics without history/migration language.
- [ ] Review confirms all exclusions and records only genuine later-ticket integration gaps for PR 29.3. No extra abstractions are added merely to express inability.

## Context and existing owners

StepLoopMissionExecutionEngine owns action parsing, task admission, joins, outcome folding, and final synthesis. StepAction, StepActionValidator, StepPromptBuilder, and StepActionCorrection own the existing protocol. MissionLifecycle owns authoritative cancellation. SkillFailurePolicy and LoomspanSession from PR 29.1 own classification/identity; ExecutionCoordinator, MissionWorkExecutor, and DefaultCapabilityInvoker already carry failures through callers. Existing completedTaskResults/StepPromptBuilder are intra-mission facilities, not a cross-mission evidence API.

The research experiment tested a standard dispatch seam, not this step implementation or concurrency behavior. Do not claim its three checks or the 17 baseline tests prove this ticket.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This changes lifecycle/concurrency failure selection and the explicit-step action protocol across nested invocation boundaries.
- **Reassessment triggers:** A newly required lifecycle model, scheduler/cancellation policy, recovery opportunity, or different task authority would change settled scope and must be surfaced.

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

