---
date: 2026-10-01T07:49:35-07:00
researcher: Codex
model: GPT-6
git_commit: b920e34d8772b13b327f017c0c37bd3198313e2a
branch: main
repository: loomspan-framework
topic: "PR 12: complete evidence delivery in planner missions"
tags: [research, codebase, planner, evidence, concurrency, mission-lifecycle]
status: complete
last_updated: 2026-10-01
last_updated_by: Codex
---

# Research: PR 12 planner evidence delivery

## Research question and execution context

Map the current direct-task result path, dependent request composition, nested boundaries, concurrency joins, lifecycle fencing, contracts, tests, and author documentation relevant to `ai/thoughts/tickets/loomspan-pr-12-preserve-planner-evidence.md`.

The developer approved the **Full 5-Step Pipeline** with “proceed” after Step 0. This is pipeline Step 1, profile `full`; the approval is specific to this run and does not replace future profile selection. Implementation is not performed in this stage. At entry, HEAD was `b920e34d8772b13b327f017c0c37bd3198313e2a`, branch `main`, with no tracked modifications. The ticket, `investigations/`, and `src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java` were pre-existing untracked investigation artifacts and must be preserved. Production release versions and `C:/opendev/code/loomspan-sidecar-test-suite` are outside the authorized change.

Metadata was collected using `bash ai/scripts/spec_metadata.sh`. The checkout-local documentation skill declares `1.0.0-beta.7-SNAPSHOT`, matching `pom.xml`; documentation and executable evidence below come from the same checkout.

## Summary

The coordinator owns one mission per invocation. Each assigned task returns a complete String through its outcome carrier, but normal fold retains only the latest complete result plus a five-line progress summary. Summary payloads contain the first 100 characters. Assigned and final prompts use those summaries and at most 1,000 characters from the latest result. Earlier complete sibling results are not stored in a planner evidence collection.

Concurrent groups take shared pre-dispatch snapshots, publish worker outcomes, join every ordinary outcome, then fold in accepted task order under the mission write fence. Serialized groups currently admit and fold one member at a time. Nested missions have independent mission state and return only their complete final result through the parent's capability invocation. Evidence annotations separately validate successful direct skill names; they do not validate result content, correctness, or quote fidelity.

## Detailed findings

### Mission state and result retention

- `src/main/java/ai/loomspan/internal/core/MissionContext.java:20` declares mutable state owned by one mission. It retains the accepted plan, ordered successful direct-skill names, diagnostic outcomes, a summary deque, and a nullable latest tool-result String.
- `MissionContext.java:22` sets summary capacity to five. `appendExecutionSummary` at line 100 evicts old lines; `lastToolResult`/`setLastToolResult` at lines 112/117 preserve the latest String without shortening it. Synchronized readers return snapshots for sets and assembled summary text. No task-keyed result collection currently exists.
- Searches of all production usages found summary/latest-result access only in `StepLoopMissionExecutionEngine` and `StepPromptBuilder`. The slots are not application-facing lookup APIs or a general reference store. `MissionContextTest#startsEmptyAndPreservesFiveLineSummaryAndEvidenceOrder` characterizes summary eviction and preservation of an empty latest result.
- `successfulDirectSkills` is a name-only set. Repeated calls to one skill have one success-set entry; this is deliberately separate from task identity.

### Accepted order, concurrency, and folds

- `StepLoopMissionExecutionEngine.java:347` obtains the bound mission and partitions the accepted task list with `ExecutionUnit.partition`. `ExecutionUnit.java:22` treats ungrouped tasks as singleton units and consecutive same-group tasks as a grouped unit. Tasks execute in accepted unit/member order, not readiness-selection or completion order.
- `StepLoopMissionExecutionEngine.java:374` reserves room for the entire unit and final synthesis before preflight or admission. `preflightUnit` validates visible bindings and earlier dependency completion before work starts; final synthesis also checks its step budget.
- For enabled concurrent groups, lines 394-434 admit all assignments before dispatch, snapshot the latest result and summary once, fork isolated worker branches, publish outcomes through the lifecycle, await all futures, and fold only after join.
- `joinAssignedTasks` at line 609 awaits entries in accepted order. Ordinary member failures are outcome values, so another started sibling continues; interruption/exceptional future termination starts mission cancellation.
- `foldOutcomes` at line 659 runs `foldWritableOutcomes` within `ExecutionBinding.requireWritable`. The latter checks increasing accepted member positions and `IN_PROGRESS` task status before folding. Successful outcomes at lines 697-708 complete the task, record the exact successful skill/task identity, append a 100-character result preview to the summary, and set the latest complete result. Failed outcomes fail the task and stale the plan; they do not append successful result state. The joined plan/transition is published after the fold.
- Disabled grouped execution at lines 439-469 admits, runs, and folds each member individually. The next member currently receives the state after the previous member, including its summary/latest result. This is an existing difference from enabled-group snapshots, despite same-unit dependencies being prohibited. Planning must explicitly account for this when defining new authoritative-evidence snapshots; no assumption that both modes already share a group-wide prompt snapshot is justified.
- `propagateFirstFailure` uses accepted-order outcomes. Later units and native final synthesis occur only after successful joins.

### Actual assigned and native-final requests

- `StepPromptBuilder.java:18` defines `MAX_LAST_RESULT_CHARS = 1000`. Assigned prompts at lines 86-96 and final prompts at lines 143-154 append the summary and clipped latest result under separate headers. Their accepted-plan/task context contains status, task ID/title, intent, expected outputs, and exact capability, not complete historic result values.
- `StepLoopMissionExecutionEngine.java:801` chooses the assigned/final prompt builder; correction retries rebuild from the same supplied prior-result/summary values. Skill-private instructions and validation guidance are added before calling the model.
- `callModelForStep` at line 961 passes a `ModelInteractionRequest` containing the composed system prompt and rendered mission input, with an empty tool list and tools disabled. The assigned model returns `CALL_TOOL` JSON; it does not directly have a result-retrieval tool. Native final synthesis requires `FINAL_RESPONSE` and prohibits tools.
- `executeToolAction` at line 1007 invokes the accepted visible capability using model-generated arguments and the trusted linked task ID. Complete returned text is retained in `StepResult`, while `STEP_COMPLETED.resultPreview` is separately limited to 200 characters. Argument binding does not automatically insert dependency outputs.
- The explicit execution-limit, invalid-action, provider, output-schema, evidence-coverage, and linter failures continue to propagate through their current paths; there is no planner result-retrieval fallback.

### Nested missions, identity, authorization, and generations

- `ExecutionCoordinator.java:109` creates a new `MissionContext` for every invocation, with the current mission as parent; `ExecutionBinding.withMission` keeps the physical branch and exact captured `SkillGeneration`. Mission state is not inherited from the parent. Constructor checks reject a parent from another session.
- `ExecutionBinding.java:42` forks a branch while retaining mission/session/generation identity. Binding scopes restore prior state. The generation is checked against capability ownership by `ExecutionCoordinator` before execution.
- `DefaultCapabilityInvoker#bind` captures the authorized visible capability metadata and authentication; its invocation delegates through `CapabilityExecutionRouter`. For a bound planned assignment, it defers task completion/success credit to coordinator fold rather than independently mutating the parent plan.
- Nested invocation returns its complete final text through the same capability return path as Java and REST children. `ExecutionCoordinator.java:279` merges only nested diagnostics back to the parent/worker branch under the parent write fence. Nested private successful-child sets, plans, and intermediate results do not bubble to the parent.
- `DefaultExecutionStateService.java:383` records name-only successful-child evidence under `runIfWritable`, with linked task metadata for trace diagnostics. This success annotation is not retained result content.

### Cancellation and late-write boundary

- `MissionLifecycle` owns OPEN/CANCELLING/CLOSED state, admitted task identities, futures, outcome publication, bounded cutoff, and ancestor-aware write permission. `ExecutionBinding.requireWritable` and `runIfWritable` delegate to it. Admission and worker start are fenced separately from outcome publication.
- `StepLoopMissionExecutionEngine.java:1337` performs cleanup through `Cutoff.runIfPermitted`. It folds only outcomes available at cutoff in task order, fails admitted unfinished work, marks the plan stale, and drains frames. Available successes currently receive the same summary/latest-result fold as ordinary successes; there is no later downstream synthesis after cancellation.
- New retained evidence will need the same authority as the existing coordinator fold/cleanup boundary. Synchronization alone is not equivalent to the lifecycle's ancestor-aware permission.

### Reference behavior

- `DefaultMissionInputMaterializer.java:139` resolves strict `ref://` attachment fields through `RefResolver` to Resources. `ExecutionCoordinator.java:464` also recognizes strict refs during Java input conversion. `ai.loomspan.internal.vfs.DefaultRefResolver` belongs to the session-local virtual-file facility.
- Generated objects such as `{"$ref":"sourceA.result"}` are ordinary input data. The supplied integration reproduction verifies that object survives into nested input; it is not resolved to the source result. No supported API or configuration currently creates a task-result reference store.

## Contract inventory

| Design-lens category | Current exposure and authority |
| --- | --- |
| Application API | Closed `ai.loomspan.api` allowlist in `LoomspanPublicSurfaceArchitectureTest`; `SkillTemplate` invocation and Java `@SkillMethod` are existing consumer entry points. README lists the supported surface. No signature change is requested. |
| Supported SPI | `RestSkillHandler` is the sole supported SPI. Internal invocation/binding/model/state interfaces and constructors are technically exposed for framework wiring, not supported replacements. |
| Configuration and manifest contracts | Existing `planning_mode`, `concurrency`, `max_steps`, `allowed_skills`, input/output schemas, and evidence annotations; definitions/settings come from the captured generation. The ticket requests evidence behavior within these existing contracts and no migration. |
| Persisted or serialized contracts | No new durable result protocol is requested. Skill output text remains the child's return contract. Existing public observation DTOs and Console boundaries are not planner result lookup paths. |
| Ephemeral diagnostic formats | `resultPreview`, progress summaries, prompt traces, evidence trace metadata, admission/join transitions; their diagnostic role differs from complete result delivery. No Console REST/SSE, acquisition/problem, or NDJSON marker change is established by the current path inventory. |
| Internal or accidentally exposed implementation | `MissionContext`, outcome records, engine, prompt builder, binding, state services, and Spring machinery. Current public visibility/bean construction is not support evidence. No compatibility shim requirement follows merely from changing these types. |

The architecture test separately inventories technically public internal types; a new public internal top-level result carrier could require a deliberate internal allowlist update without becoming supported API. Existing internal types need no compatibility preservation solely for their visibility.

## Documentation assessment

Applied checkout-local `agent-skills/loomspan-docs/SKILL.md` as the skill-authoring documentation router after executable inventory. Read the routing/coverage index, `planning-concurrency.md`, `evidence-contracts.md`, and `source-verification.md` completely. This implementation has author-facing impact because it changes what data dependent planner steps and final synthesis receive.

- **aligned:** documented task ordering, grouped fork/join, generation capture, cutoff fencing, direct-child evidence-name isolation, and the distinction between evidence supportability and factual correctness agree with the examined paths/tests.
- **documentation drift (coverage gap):** the guide's planning coverage and README describe later steps seeing results/evidence but do not state the executable 100-character summary, five-summary retention, and 1,000-character prompt-preview limits. Complete authoritative-evidence delivery and `$ref`/`ref://` limitations have no dedicated current guidance. This gap is already within the approved ticket's documentation requirement; it introduces no unresolved scope escalation.
- The ordinary output-schema contract validates output structure; the name-only evidence annotations require successful direct children. Neither validates all source facts, quote fidelity, or citation correctness. New evidence-flow prose must retain these distinctions.

## Tests and executable evidence

- Existing untracked `PlannerEvidenceFlowIntegrationTest.java:34` uses public `SkillTemplate`, real auto-configuration, Java `@SkillMethod`, REST leaves, model-backed YAML, and a local OpenAI-compatible `MockWebServer`. It captures actual `/v1/chat/completions` bodies, not shortened trace previews, and uses input-received fact extraction to generate dependent and final replies.
- `characterizesActualWireEvidence` parameterizes concurrency on/off and long/short results. Long characterizations assert the existing loss; short controls assert complete facts. Direct public invocation and observation payload controls establish that complete results exist outside planner prompt composition. Preserve controls while replacing defect-characterization expectations with desired behavior.
- `completeEvidenceMustReachDependentAndFinalRequests` is currently opt-in via `loomspan.evidence.requireComplete`; it checks multiple sibling tail facts in assigned/nested requests and nested tail data in native final synthesis. It is not ordinary green coverage yet. It does not cover more than five completions, repeated task/skill identities, reversed finish order, or nested planner-to-planner isolation; those acceptance dimensions remain for test planning.
- `StepLoopMissionExecutionEngineTest` covers ordinary failures, retries, step limits, all-unit admission, reversed completion folding, following-unit join gating, disabled serialization, dispatch rejection, nested failures, timeout cleanup, late-result suppression, and exact binding restoration.
- `ConcurrentGroupedExecutionIntegrationTest` covers real nested direct/planning missions, authorization/frame propagation, mission/diagnostic isolation, quotas, and physically late nested work. `SkillGenerationExecutionIntegrationTest`, `ExecutionCoordinatorMissionContextIntegrationTest`, `MissionLifecycleTest`, `JavaSkillMissionCutoffTest`, `NestedSuccessfulSkillBoundaryTest`, `StepPromptBuilderTest`, and architecture tests provide focused related controls.
- No tests were run during this documentation-only research stage. Maven is installed (`c:/hamdev/maven/bin/mvn.cmd`); `java` is not on the current PowerShell PATH. A later verification stage must discover/configure an existing JDK. `pom.xml` requires Java 21 or later and uses Surefire 3.5.2; it is not evidence of an unavailable JDK installation.

## Historical context and code references

`investigations/evidence-flow/report.txt`, captured beta.6 requests, and the released-artifact harness provide the ticket's independent prior reproduction. The ticket reports seven failing completeness assertions on both beta.6 and HEAD and passing public-surface checks; these are historical results, not newly executed research verification. The live sidecar transport interruption is a separate event and is outside this ticket.

Primary anchors: `MissionContext.java:20`, `StepPromptBuilder.java:18`, `StepLoopMissionExecutionEngine.java:347`, `:394`, `:439`, `:659`, `:801`, `:961`, `:1007`, `:1337`, `ExecutionCoordinator.java:109`, `:279`, `ExecutionBinding.java:42`, and `DefaultExecutionStateService.java:383` (all under `src/main/java/ai/loomspan/internal/` with package paths as detailed above). The checkout had no earlier research or plan documents to use as implementation authority.

## Open questions for planning

These are ordinary internal design decisions resolvable from the approved ticket and executable evidence, not developer escalation requests:

1. Select the mission-owned complete-result carrier and rendering that preserve task ID, exact skill name, complete String content, stable task order, empty results, immutable snapshots, and separate summary semantics without exposing a new API/SPI.
2. Specify the allowed evidence snapshot for grouped tasks in both concurrency modes. Enabled groups already share a pre-group snapshot; disabled groups currently fold per member. The ticket forbids newly exposing sibling evidence before its permitted join boundary and requires concurrent/serialized completeness equivalence downstream.
3. Map normal joins and cutoff cleanup to the complete-result authority, proving failed/unfinished and late outcomes cannot earn successful data delivery or escape nested/mission isolation.
4. Expand local outbound-request coverage for more than five results, repeated skills with distinct task IDs, reverse completion, nested planner returned quotes/citations and private intermediate isolation. Preserve the supplied reproduction's application assertions and no-internal-replacement contract.
5. Choose the documentation home/routes for evidence delivery and reference limitations, and record whether any now-unused internal latest-result slot remains after the final design. All production usages are confined to the paths identified above.
