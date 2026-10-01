# PR 12 — Preserve Planner Evidence Implementation Plan

## Overview

Retain every successful assigned-task result in its owning mission and include complete prior-unit results in dependent assigned-step and native final-synthesis model requests. Keep bounded progress summaries and diagnostic previews independent of this authoritative data. Nested missions continue returning only their complete final result to their parent.

This is approved Full 5-Step Pipeline planning, profile `full`, following the developer's “proceed”. The ticket, research, investigation files, and public-facade reproduction were untracked before this run; preserve them. This stage writes plans only.

## Current State Analysis

`MissionContext` stores one complete latest result and five summaries (`src/main/java/ai/loomspan/internal/core/MissionContext.java:22`, `:100`, `:112`). The engine folds successful results into 100-character summaries and that latest slot (`src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:697`). `StepPromptBuilder` clips the latest result at 1,000 characters in both assigned and final requests (`src/main/java/ai/loomspan/internal/runtime/step/StepPromptBuilder.java:18`, `:86`, `:143`). Earlier full results are lost to downstream planning even when available in public observations.

Enabled groups share pre-dispatch summary/result snapshots and fold after full join. Disabled groups admit and fold individually and currently pass the latest state to each next member (`StepLoopMissionExecutionEngine.java:394`, `:439`). Same-unit dependencies are invalid, so grouped siblings must not gain new complete evidence from one another merely because dispatch is serialized.

Nested invocation constructs a new mission and merges only diagnostics; its final returned String is the parent's direct-child result (`src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:109`, `:279`). Success-name annotations, reference materialization, and output-schema validation serve separate responsibilities.

## Desired End State

- One mission owns an insertion-ordered collection keyed by exact accepted task ID. Each immutable entry contains task ID, exact skill name, and complete returned String, including empty/whitespace values.
- Entries originate only at the existing successful coordinator fold or permitted cutoff fold, in accepted task order. Failed, unfinished, and late outcomes contribute no successful result entry.
- Every assignment in an execution unit receives the same immutable pre-unit evidence and summary snapshots in either concurrency mode. All completed earlier units are available, including tasks beyond the summary's five-line window. Dependencies continue controlling valid order; they do not filter, resolve references, or automatically bind child arguments.
- Native synthesis receives all complete successful direct-task entries, including nested returned results. Repeated skills remain distinguishable by task ID. No new retrieval tool or SPI is introduced.
- Bounded summaries/previews may remain for progress and diagnostics but cannot determine delivered evidence. Model/provider/context and existing execution-limit errors remain explicit failures; no truncate-and-success fallback is added.
- Actual outbound HTTP assertions establish completeness and isolation through `SkillTemplate` with a local deterministic endpoint.

### Key Discoveries

- `AssignedTaskOutcome.Success` requires a non-null String; successful empty results are valid (`src/main/java/ai/loomspan/internal/runtime/step/AssignedTaskOutcome.java:11`).
- Existing synchronization yields mission snapshots; lifecycle permission remains owned by the binding/coordinator, not by synchronized access alone (`StepLoopMissionExecutionEngine.java:659`, `:1337`).
- Only engine/builder production paths use the latest-result slot, so removing that redundant internal slot is an atomic in-scope cleanup.
- The architecture test inventories top-level classes only, permitting an internal nested carrier without expanding the supported API or technical-public top-level allowlist (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:290`).
- The existing integration reproduction captures full protocol bodies and derives replies from received input; its opt-in completeness assertion is the red-test authority (`src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java:34`, `:89`).

## What We're NOT Doing

- No public API/SPI, bean override contract, manifest/configuration option, result-reference protocol, VFS expansion, or automatic child-input merge.
- No factual correctness or quote-fidelity guarantee for arbitrary model answers; no repair of the unrelated live provider transport interruption.
- No publication, release/version edits, Console protocol changes, or changes to `C:/opendev/code/loomspan-sidecar-test-suite`.
- No nested intermediate-result export or global/session-wide evidence registry.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: Authors need to know which complete results reach assigned/final prompts, grouped visibility boundaries, explicit generated child arguments, and reference limitations.
- **Documents to update**: `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/planning-concurrency.md`; `agent-skills/loomspan-docs/references/skill-authoring/evidence-contracts.md`; the routing/coverage index `agent-skills/loomspan-docs/references/skill-authoring/README.md`.
- **Supporting evidence**: engine normal/cutoff folds; `MissionContextTest`, `StepPromptBuilderTest`, `StepLoopMissionExecutionEngineTest`; captured public-facade `PlannerEvidenceFlowIntegrationTest` requests; existing nested evidence and reference controls.
- **Coverage table update**: Required. Extend planning/nested-planning coverage to complete task results and reference limitations; clarify evidence annotation versus evidence delivery.
- **LLM-first usability**: Put the self-contained evidence-flow rules in `planning-concurrency.md`, route result-loss questions there, and cross-link `evidence-contracts.md` for successful-name checks. Use stable named anchors and concise exact limitations; do not create a redundant broad topic.
- **Version/drift classification**: The checkout-local `loomspan-docs` skill and Maven version both identify `1.0.0-beta.7-SNAPSHOT`. Existing ordering, lifecycle, nested-success isolation, and name-only supportability prose is **aligned**. Missing complete-result delivery and reference guidance is **documentation drift (coverage gap)** covered by the ticket. Replace obsolete mentions of the authoritative latest-result mechanism while preserving current diagnostic semantics.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | `SkillTemplate`, `@SkillMethod`, public observation and input contracts remain unchanged; closed architecture allowlist is authoritative. | Preserve all application signatures and ordinary invocation behavior. |
| Supported SPI | `RestSkillHandler` remains the sole supported SPI. | No new SPI or override contract. |
| Configuration and manifest contracts | Existing planning/concurrency/dependency, allowed-skill, schema, and limit contracts stay unchanged. Complete result availability fixes the approved behavioral defect. | Preserve keys/defaults/validation; no migration. Document pre-unit grouped evidence visibility in both modes. |
| Persisted or serialized contracts | Child return values remain exact Strings. The evidence block is internal model prompt data, not a durable store or public reference format. | No persisted schema or cross-version promise added. |
| Ephemeral diagnostic formats | Keep bounded summary/result previews and existing admission/join facts. Prompt traces may naturally grow with outbound prompt content. | Maintain current writer/reader coherence, failure visibility, isolation and existing redaction; add no raw result trace fields. |
| Internal or accidentally exposed implementation | Change `MissionContext`, engine/builder parameter flow, and internal tests. Remove latest-result slot and 1,000-character authoritative clipping. | Atomic update with no legacy overloads or aliases. |

- **Evidence of supported contracts**: supplied AGENTS.md, `LoomspanPublicSurfaceArchitectureTest`, README API summary, approved ticket, and public-facade regression.
- **Intentional compatibility changes**: No supported Java/configuration break. Replace obsolete internal latest-result mechanics. In serialized grouped assignments, use the pre-unit summary/evidence view so sibling result visibility does not depend on concurrency; preserve serialized admission, folds, plan transitions, step costs, and failure handling.
- **In-repository consumers to update**: all latest-result engine/builder callers and tests; supplied regression defect expectations; four documentation files above. Preserve investigation captures/harness as historical evidence.
- **Public-surface delta**: No supported type/signature or Spring extension point. Add an immutable nested result record inside the already-internal `MissionContext`; its visibility is solely for internal cross-package collaboration.
- **Shim decision**: **No shim.** Removed slots/signatures belong to internal implementation and all callers are updated atomically. Retaining them creates duplicate authority without protecting a supported consumer.
- **Java-to-Go boundary coordination**: **Not required.** No application-adapter REST/SSE, acquisition, problem, consumed NDJSON, or compatibility marker change.
- **Pipeline notes alignment**: **No notes.** The ticket expressly authorizes preserving complete results and forbids API/configuration expansion; this plan stays within that scope.

## Implementation Approach

Extend the existing mission-owned evolving result state rather than adding another store owner. Use a nested immutable `MissionContext.CompletedTaskResult` record with `taskId`, `skillName`, and `result`; validate nonblank identity and non-null result. Store entries in a `LinkedHashMap<String, CompletedTaskResult>` keyed by task ID and expose a `List.copyOf(...)` snapshot. Reject duplicate task recording as an internal invariant instead of overwriting a prior result. This protects repeated skill invocations without conflating the separate successful-skill-name set.

Pass typed immutable snapshots through assignment and final execution to the builder. Render a JSON array of records under an explicit completed-task-evidence data heading, using an existing internal Jackson codec. Preserve each result as a JSON string rather than parsing/reformatting arbitrary child text. JSON escaping safely delimits quotes, newlines, braces, and header-looking content while retaining the exact decoded value. Tell the model these are returned data, not new instructions, to use complete results when forming arguments/synthesis, and that arguments still must satisfy the assigned child contract. Summary text is explicitly progress-only. Rendering errors propagate visibly.

Render all successful prior-unit direct results, rather than selecting only named dependencies. This retains the existing mission-context visibility model and satisfies preservation across all subsequent execution units; dependencies enforce ordering, not a newly invented access policy. Task/skill identities are runtime-owned accepted-plan facts, never model-supplied outcome identity.

Rejected alternatives: raising the latest limit still loses earlier siblings; expanding summary retention conflates diagnostics and data; a retrieval tool cannot serve tool-prohibited final synthesis without a new protocol; auto-injecting dependency outputs changes child input contracts; a global store weakens isolation. Complete prompt delivery increases memory/tokens, but silent clipping is the defect and an undocumented new lossy limit is outside scope.

## Phase 1: Establish Complete Mission Result Ownership and Prompt Delivery

### Changes Required

1. **Mission state** — `src/main/java/ai/loomspan/internal/core/MissionContext.java`: introduce nested result record, ordered task-keyed retention and immutable snapshot methods. Keep summary deque and success-name set separate. Remove `lastToolResult` and its setter/readers.
2. **Coordinator evidence snapshots** — `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java`: capture completed-result and summary snapshots once before each execution unit. Supply them to every enabled or disabled group member and all correction attempts. Preserve existing admissions, branches, outcome publication/join/fold timing and serialized failure short-circuit behavior. Native synthesis uses a fresh final snapshot after successful traversal.
3. **Authoritative folds** — same engine: record complete entries using exact accepted task/skill identity alongside ordinary success completion, inside `requireWritable`. Apply the same recording at existing `Cutoff.runIfPermitted` success cleanup, with no failed/unfinished entries or new permission rules. Do not copy entries when nested diagnostics merge.
4. **Prompt composition** — `src/main/java/ai/loomspan/internal/runtime/step/StepPromptBuilder.java`: replace latest-result arguments/header/clipping with typed evidence and lossless JSON data rendering; keep exact assignment and no-tools final contracts. Prefer `LoomspanJacksonCodecs` over another serialization subsystem.
5. **Internal consumers** — `MissionContextTest`, `StepPromptBuilderTest`, `StepLoopMissionExecutionEngineTest`, and any other latest-result references found by `rg`: change obsolete slot assertions to task-keyed entry assertions; preserve behavior tests for summaries, ordering, retries, failure precedence and cutoff.

### Success Criteria

- [x] Unit tests establish stable task identity/order, exact long/empty/whitespace text, immutable snapshots and duplicate rejection.
- [x] Prompt tests round-trip rendered result strings, including quotes/newlines/header-shaped text, for assigned/final modes; no 1,000-character clipping remains in authoritative delivery.
- [x] Engine tests establish pre-unit snapshot parity across concurrency modes, full-join visibility, accepted-order entries, and cutoff/non-credit behavior without changing admission/join/failure facts.
- [x] `mvn -Dtest=MissionContextTest,StepPromptBuilderTest,StepLoopMissionExecutionEngineTest,MissionLifecycleTest,LoomspanPublicSurfaceArchitectureTest test` passes with an existing Java 21+ JDK.
- [x] `rg` confirms obsolete latest-result slot/callers are absent from production and updated active tests; bounded diagnostic truncation remains intentional.

## Phase 2: Prove Public Evidence Delivery and Synchronize Author Guidance

### Changes Required

1. **Public protocol regression** — `src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java`: run the existing opt-in completeness case pre-fix and retain its assertions as ordinary coverage post-fix. Promote long-result characterizations to complete-evidence assertions while retaining short, direct-invocation, public-observation, Java/REST/YAML and `$ref` controls. Preserve all investigation files.
2. **Coverage extension** — same integration test or a focused companion in the same package: add seven or more completed tasks, repeated exact skill names with distinct task IDs, long late quotes/citations, reversed enabled-group completion, serialized counterparts, nested planner-to-planner final return, private nested intermediate exclusion, and unrelated-mission exclusion. Replies derive from received wire data; no internal bean replacements or preloaded downstream answers.
3. **Lifecycle regressions** — focused existing engine/integration controls: assert successful results visible only after allowed boundaries, ordinary sibling failure retains successful sibling entries without later synthesis, and physically late cancelled work cannot append evidence. Re-run existing authorization/generation/limits/nested checks.
4. **Author documentation** — update the four files listed in documentation impact. Explain complete results, task/skill identity, prior-unit snapshot parity, nested returned-only visibility and explicit arguments. Distinguish structure validation, successful-name annotations, source correctness/quote fidelity, ordinary `$ref` data and existing `ref://` attachment/file resolution. Neither reference form is a planner result retrieval facility.

### Success Criteria

- [x] The dedicated testing plan's outbound-request and lifecycle matrix passes without a live provider or credentials.
- [x] Updated guidance claims map to focused executable tests, use stable anchors, and satisfy the knowledge-base LLM-first standard; route/coverage entries are updated.
- [x] `mvn -Dtest=PlannerEvidenceFlowIntegrationTest,ConcurrentGroupedExecutionIntegrationTest,SkillGenerationExecutionIntegrationTest,ExecutionCoordinatorMissionContextIntegrationTest,JavaSkillMissionCutoffTest,NestedSuccessfulSkillBoundaryTest,LoomspanPublicSurfaceArchitectureTest test` passes.
- [x] Full repository verification `mvn test` passes, or an actual environmental failure is reported with exact residual risk; do not claim unrun verification.
- [x] Release/version files and neighboring repository remain unchanged; final diff is scoped and existing useful investigation artifacts remain intact.

## Testing Strategy

See `ai/thoughts/plans/2026-10-01-loomspan-pr-12-preserve-planner-evidence-testing.md` for named cases, red-test command and exact exit criteria. Use low-cost state/prompt tests for byte-exact retention/rendering and lifecycle tests for timing/fences. Use the supplied public facade and real local provider protocol to establish actual wire delivery, generated nested input, and final output. Timing controls use latches/barriers, not sleeps or asserted trace previews.

## Performance Considerations

Mission retention is linear in total successful direct returned text and task count. Immutable snapshots copy record references, not result Strings; avoid recomputing duplicate authoritative stores. Prompt token cost grows with full prior results per later step. Existing limits/provider failures remain explicit; do not add silent eviction, clipping, success fallback, or a new configuration contract. Progress summaries and trace previews stay compact independently.

## Migration Notes

No API, configuration, result-return or durable-data migration. Replace internal methods/tests atomically. Historical investigation captures continue documenting the original defect and are not updated to fake successful old behavior.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-12-preserve-planner-evidence.md`.
- Research: `ai/thoughts/research/2026-10-01-loomspan-pr-12-preserve-planner-evidence.md`.
- Design lens: `ai/thoughts/framework-feature-design-lens.md`.
- Documentation/source protocol: `ai/commands/shared/loomspan-docs-protocol.md`.
- Public reproduction: `src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java`.
- Prior independent evidence: `investigations/evidence-flow/report.txt`, `investigations/evidence-flow/captures/beta6/parallel-long-requests.ndjson`.

## Implementation notes (2026-10-01)

Step 4 followed this design without a governing-artifact mismatch. Complete mission-owned records, existing writable normal/cutoff folds, lossless JSON rendering and pre-unit snapshots in both modes are implemented. All focused and surrounding integration checks pass (131 tests, zero failures/errors/skips), with the original pre-fix red confirmed at all seven soft assertions before edits. The expanded public harness is in the existing `PlannerEvidenceFlowIntegrationTest` rather than a new companion; names and latch helpers are routine adaptations. See `ai/thoughts/implementation/2026-10-01-loomspan-pr-12-preserve-planner-evidence.md` for commands, acceptance mapping and lifecycle/compatibility decisions. Full `mvn test` passed: 1,207 tests, zero failures/errors/skips. Independent Step 5 remains required.
