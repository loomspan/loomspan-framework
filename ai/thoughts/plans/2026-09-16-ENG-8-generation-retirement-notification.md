# PR 8 — Generation Retirement Notification Implementation Plan

## Overview

Add `SkillReloader.onGenerationRetired(Consumer<String>)` so an application can discard generation-keyed resources only after a superseded published generation cannot execute again. Loomspan owns the signal; the application owns staging, cleanup, retry, and readiness. The implementation must bridge generation capture before admission, pending and executing roots, and physical descendants that outlive cancellation.

## Current State Analysis

- `SkillReloader` has prepare, publish, and snapshot only (`src/main/java/ai/loomspan/api/SkillReloader.java:5-19`). `DefaultSkillReloader.publish` serializes publication and checks admission openness before `SkillGenerationManager.activate`, but does not retain the old generation or signal its retirement (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:59-82`).
- `DefaultSkillTemplate` captures a generation before object conversion or validation, outside the root-admission monitor (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-164`). The handoff prepares before admission and can retain a pending root (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:29-101`). Capture must therefore acquire ownership atomically with publication, before conversion begins.
- `FrameworkExecutionLifecycle` owns pending/executing root transitions and shutdown admission (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:55-125`, `:128-145`). `LoomspanSessionRunner` completes the root in `finally`, which may precede actual worker return after cancellation (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:237-313`).
- `MissionContext` registers every nested mission on its admitted root (`src/main/java/ai/loomspan/internal/core/MissionContext.java:51-53`). `MissionLifecycle` already records owning and task physical starts/returns and has `allPhysicalWorkReturnedLocked()` (`src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:72-103`, `:127-181`, `:448-452`). This is the existing authority to reuse; `Future.isDone()` is not proof of return.
- The fixed REST handler already receives the root's captured process-local ID (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:168-174`). README and bundled skill guidance presently tell applications there is no safe-deletion signal (`README.md:196-216`; `agent-skills/loomspan-docs/references/java-api/skill-reload.md`; `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md`).

## Desired End State

A listener registered before initial resource staging is selected once per safely retired published generation during normal operation, including an unused generation replaced immediately. The callback can run on a publishing, invocation, admission-release, or physical-return thread, outside framework lifecycle/publication/mission locks. A slow or throwing listener cannot affect the operation that retired the generation or suppress other listeners or later retirements. Closure stops future selection but does not wait for an already selected callback. No replay, ordering, shutdown drain, or notification for never-published candidates exists.

### Key Discoveries

- Publication and shutdown admission already serialize on `FrameworkExecutionLifecycle.whileAdmissionOpen` (`DefaultSkillReloader.java:66-79`); the generation capture/retirement lock must nest inside this publication action, not create a second publication authority.
- A quiet gap between REST calls is possible because `ExecutionBinding` carries the same generation through mission and branch forks (`src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:10-45`). Root ownership must span the whole invocation, not each REST call.
- Cancellation/grace cutoff can complete the facade while physical callables continue (`MissionLifecycle.java:240-265`; `StepLoopMissionExecutionEngine.java:207-256`, `:399-415`). Final root release must be gated by mission physical-return state.

## What We're NOT Doing

- No Sidecar code, historical polling/replay, persisted notification, retry queue, delivery executor, resource manager, readiness gate, new SPI, Spring event, or extra configuration key.
- No automatic reclamation of an abandoned `AdmittedSkillInvocation`; callers must release it.
- No changes to REST route selection, input contracts, RBAC, shutdown budget, generation ID format, Console protocol, or trace schema.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: The manifest syntax and execution semantics stay the same, but authors and application developers need the new rule for retaining generation-keyed REST configuration. Current same-checkout prose explicitly says no retirement/safe-deletion API; this is planned documentation drift once the code changes, not a pre-existing framework defect.
- **Documents to update**: `agent-skills/loomspan-docs/references/java-api/skill-reload.md`; focused references in `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md` and `mental-model.md`; the routing/coverage descriptions in both knowledge-set `README.md` files. Update `README.md` and `SkillReloader` Javadoc with the same contract. The Java API index also has a pre-existing “eighteen-type” typo alongside its nineteen-type list; correct while editing the index.
- **Supporting evidence**: `SkillReloaderTest`, `PublicSkillReloadIntegrationTest`, new capture/physical-return tests, `SkillGenerationManager#capture`/publication, `FrameworkExecutionLifecycle.AdmittedRoot`, `MissionLifecycle` return callbacks, and fixed handler generation routing in `SkillGenerationManager#invokeRest`.
- **Coverage table update**: Required. Two-stage update and REST topic notes must mention safe retirement; topic routing remains through the existing reload and REST documents.
- **LLM-first usability**: Keep one short initialization/update sequence in `skill-reload.md`, link the REST and mental-model topics to it, and distinguish runtime guarantees from application obligations and shutdown limitations. No new topic is needed.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add one `SkillReloader` method with standard Java `AutoCloseable` and `Consumer<String>`; the type is explicitly allowlisted by `LoomspanPublicSurfaceArchitectureTest.java:28-48` and documented at `README.md:189`. `PreparedSkillUpdate.generationId()` remains the callback key. | Deliberate additive method; document closure, thread, delivery, and error semantics. No new top-level API type. |
| Supported SPI | Fixed `RestSkillHandler` and `RestSkillInvocation.generationId()` remain the only supported handler boundary (`README.md:189`, `:196-215`). | Preserve one handler and generation-keyed selection; no second SPI. |
| Configuration and manifest contracts | Neither YAML syntax nor `loomspan.shutdown.timeout` changes. | Preserve. Update guidance for resource retention only. |
| Persisted or serialized contracts | Process-local IDs and callback attempts are not durable; no serialized protocol changes. | No migration or historical reader. |
| Ephemeral diagnostic formats | No trace/schema addition required. Listener failures need visible logging without altering session outcomes. | Log listener failure with generation ID and continue; avoid sensitive application payloads. |
| Internal or accidentally exposed implementation | `SkillGenerationManager`, `DefaultSkillReloader`, template/handoff, root lifecycle, mission lifecycle, and runner change. Their public modifiers are internal wiring (`LoomspanPublicSurfaceArchitectureTest.java:116-118`, `:271-278`). | Change internal signatures atomically and update in-repository callers/tests; no compatibility shim. |

- **Evidence of supported contracts**: Architecture allowlist, README supported-surface statement, public integration test, and ticket's explicit callback/API shape.
- **Intentional compatibility changes**: Internal method/constructor adjustments only. No deliberate breaking change to supported API or SPI. The ticket permits removal of obsolete internal paths but does not require one.
- **In-repository consumers to update**: Auto-configuration wiring, direct internal unit-test builders, `DefaultSkillTemplate` and handoff callers, runner/root/mission tests, README, bundled Java API and skill-authoring guides.
- **Public-surface delta**: One new abstract method on existing supported `SkillReloader`; no new public top-level type, constructor contract, bean override point, or SPI.
- **Shim decision**: **No shim.** The new API is additive; changed implementation types are internal and this development checkout requires atomic updates rather than duplicate paths.
- **Java-to-Go boundary coordination**: **Not applicable.** No application-adapter REST/SSE, acquisition, problem, or consumed NDJSON contract changes.
- **Pipeline notes alignment**: **No notes.** The ticket itself expressly permits internal simplification while preserving capture and authorization.

## Implementation Approach

Keep one small ownership state alongside the active generation in `SkillGenerationManager`: published generation ID, active/superseded status, owner count, and whether retirement was selected. Use one manager monitor for *both* capture-with-owner increment and activation/supersession. Publication still enters through the lifecycle admission-open action; capture does not need that monitor because it is serialized with activation by the manager monitor. A selected retirement snapshots listener registrations under the manager monitor, marks the generation selected exactly once, and invokes each listener only after every framework lock has been exited. Keep listener registration/closure on the same manager monitor. Keep an initially published state and create state only when a candidate activates; never-published candidates never enter retirement accounting.

Use one owner lease per captured invocation, transferred from preparation to its admitted root. The lease begins before conversion/validation and is released on preparation failure or pure `validate`. Direct and handoff admission take that same lease atomically into `AdmittedRoot`; admission failure releases it. Pending release/cutoff returns it. Execution return releases it only after logical root completion *and* every registered mission reports no physical started work outstanding. A mission can report this after its normal close/cancellation cutoff and physical owning/worker `finally` paths; queued work that never started cannot execute after the closed admission fence. This reuses the root's existing mission set and `MissionLifecycle`'s start/return authority. Do not classify safety from `Future.isDone()`, grace expiry, or a root's logical completion alone. Make release idempotent at all competing transitions.

Avoid callbacks under locks by separating state transitions from delivery: each transition produces a small immutable notification snapshot, then dispatches after leaving manager, publication, framework lifecycle, and mission locks. A retired generation state can be removed after selection, avoiding a growing history; active state plus still-owned superseded states suffice. Catch and log consumer failures per listener. Wire the existing lifecycle's atomic admission-closed state into selection, without acquiring its monitor from inside the manager monitor. Shutdown admission closure suppresses future selection/delivery without manufacturing retirement; already selected/running callbacks are not drained.

The developer problem is externally owned REST resources tied to immutable generation IDs; only the runtime can know when a captured tree and its physical descendants are done. The proposed state is narrow and run-local, with no model-controlled input or new mutable application contract. Doing nothing leaves applications unable to remove resources safely; tracking REST calls alone misses later child calls and cancellation survivors; a delivery executor adds lifecycle complexity without making the safety signal more correct.

## Phase 1: Published Generation Accounting and API

### Changes Required

1. **`src/main/java/ai/loomspan/api/SkillReloader.java`**: Add `AutoCloseable onGenerationRetired(Consumer<String> listener)` with Javadoc for process-local ID, normal-operation guarantee, no replay/order/retry, callback thread/concurrency, close race, shutdown boundary, and application-owned cleanup.
2. **`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`**: Replace bare activation for publication with a synchronized capture/activate/retire state transition. Initialize the configured startup generation as published. Add a package/internal owner lease that closes exactly once; count capture before leaving the same monitor used by publication. Select and remove safely retired superseded state once, including zero-owner generations. Registration close removes from future snapshots. Provide a dispatch path that cannot throw into publication or invocation and logs listener failure.
3. **`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`**: Delegate registration; keep candidate identity/base/one-shot checks. Perform activation under existing publication/admission serialization, then dispatch any retirement snapshot after the publication lock and lifecycle action return. A repeated/stale rejection must not dispose a candidate that was already published earlier.

### Success Criteria — Automated Verification

- [x] Manager/reloader tests show one notification for initial and immediately superseded unused generations; no event before supersession; no event for rejected never-published candidates; late registration does not replay.
- [x] Concurrent registration removal and throwing-listener tests establish selection and exception isolation.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes with no new supported type or internal signature leak.

## Phase 2: Capture, Admission, and Physical Ownership

### Changes Required

1. **`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java`**: Replace raw `active()` reads for execution preparation with atomic owned capture. Release on null input, conversion, lookup, validation, authentication, and runner/admission failure; pure `validate` closes after its check. Pass/transfer the lease with prepared input. Preserve generation selection before conversion and all existing authorization behavior.
2. **`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java` and `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java`**: Transfer lease to admitted root for direct and pending paths, with no gap between preparation ownership and root ownership. Ensure release on pending explicit release, cutoff, rejected claim, and every execution/failure path. Preserve single-use handoff and current exception mapping.
3. **`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java`**: Store the transferred lease on `AdmittedRoot`; let pending termination and logical completion converge on one idempotent release gate. Reuse registered mission set to wait for physical return after a root completes. Run lease close and any listener delivery outside its monitor. Do not alter shutdown wait budget.
4. **`src/main/java/ai/loomspan/internal/core/MissionLifecycle.java` and `ExecutionCoordinator.java`**: Expose a one-time physical-safety completion hook based on `state == CLOSED` plus existing `allPhysicalWorkReturnedLocked()`. Drain the hook outside its own lock from `closeNow`, `awaitCutoff`, `owningReturned`, and `taskReturned`. This handles late physical return without polling, future-state inference, or extending cleanup grace. Keep no callback when a mission remains open or started work is still running. Ensure `ExecutionCoordinator` closes a normally unwinding mission even if frame/trace cleanup throws before its current `closeNow()` call (`ExecutionCoordinator.java:178-194`), so a finished failure path cannot strand ownership.

### Success Criteria — Automated Verification

- [x] Deterministic latch tests cover capture-before-admission publication race, pending release/invoke, nested and parallel descendants, failed preparation/execution, cancellation/deadline with uncooperative started work, and shutdown cutoff.
- [x] Existing generation capture, auth, concurrency, shutdown-budget, and lifecycle tests still pass.
- [x] No listener executes while any framework lifecycle, publication, manager, or mission lock is held; a listener can safely call `snapshot` or register/close another listener.

## Phase 3: Public Example, Guide Sync, and Verification

### Changes Required

1. **`README.md`**: Replace the current cleanup-policy text with a compact complete example: register; stage initial snapshot ID; open application traffic/publication; prepare and stage candidate ID; publish; one fixed handler uses `RestSkillInvocation.generationId()`; callback schedules prompt application-owned cleanup. State rejected never-published candidate cleanup, repeated publish distinction, callback concurrency/failure/close race, shutdown omission, and process-local IDs.
2. **Bundled guides**: Update `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, `skill-authoring/rest-skills.md`, `skill-authoring/mental-model.md`, and their two index coverage entries to match tests and production. Remove their “no retirement API” claims. Keep skill-author manifest and execution topics concise, linking to the full Java API guide.
3. **Verification**: Run focused tests from the testing plan, `LoomspanPublicSurfaceArchitectureTest`, then `./mvnw.cmd --batch-mode --no-transfer-progress test` on Windows. Inspect `git diff --check` and changed public signatures/docs before handoff.

### Success Criteria — Automated Verification

- [x] Public integration test uses only supported imports and proves fixed REST handler plus generation-keyed staging/cleanup.
- [x] Every documented delivery/safety claim maps to a focused test and production path; both guide indexes route to current behavior.
- [x] Full Maven test suite and architecture test pass.

## Testing Strategy

See `ai/thoughts/plans/2026-09-16-ENG-8-generation-retirement-notification-testing.md` for red tests, deterministic synchronization, test locations, exact commands, and exit criteria. Start with a red public integration assertion: after B replaces A while an A handoff is pending, A must not retire until release or invocation physically returns. Follow with lower-level race and cancellation tests before broad verification.

## Performance Considerations

Capture and publication each take a short manager lock for owner-count updates; no callback runs there. Retain only active and still-owned superseded states, and remove retired states after one selection. Mission hooks run only at existing close/return edges, avoiding polling and a delivery thread/queue. Applications should hand slow cleanup to their executor.

## Migration Notes

The new callback is additive to the supported Java API. Applications can retain their existing resource retention policy or adopt the documented registration sequence. Process-local IDs and no replay mean each process must register and stage anew. No persisted data or protocol migration exists.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-8-generation-retirement-notification.md`
- Research: `ai/thoughts/research/2026-09-16-ENG-8-generation-retirement-lifecycle.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Similar ownership authorities: `FrameworkExecutionLifecycle.AdmittedRoot` and `MissionLifecycle#allPhysicalWorkReturnedLocked`
