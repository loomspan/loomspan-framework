# PR 5.6 — Atomically hand external invocations to framework ownership

## Outcome

Allow an application with its own dispatch gate to hand a request to Loomspan
atomically and promptly, then execute it outside that gate. Closing application
dispatch must prevent any still-application-owned request from becoming a new
framework root. Work already handed off remains governed by Loomspan's existing
execution lifecycle and single shutdown budget.

Loomspan Sidecar exposes the need: its worker marks a request running under its
gate, releases the gate, establishes authentication, and calls
`SkillTemplate.invoke()`. Sidecar can close dispatch between those operations
while framework admission is still open. The framework correctly protects its
own admission, but the supported API cannot currently make the external
ownership transition atomic. `validate()` is advisory and reserves no admission.

## Requirements

- Provide the smallest generally useful supported admission contract in
  `ai.loomspan.api`. Handoff must register framework ownership atomically with
  framework admission closure, without executing the skill, waiting for its
  completion, or performing blocking trace I/O under the application's gate.
- Before handoff succeeds, the application owns the request and can discard it.
  After success, Loomspan owns the root, including pending execution. Reject
  handoff if framework admission has closed, without constructing a session or
  starting skill work. Execution must use that admission rather than acquire a
  second root.
- Support execution on the application's existing worker after releasing its
  gate. Preserve the direct invocation contract for results, exceptions,
  authorization, security-context restoration, observation, and framework-owned
  nested work. Document authentication handling and when mission timeouts begin;
  admission must never act as an authorization bypass.
- An admission is single-use. Provide explicit, idempotent release of admitted
  work abandoned before execution. Release, execution, and shutdown races must
  not permit duplicate execution or prematurely release an executing root.
  Shutdown cutoff must invalidate and release pending admissions even if the
  caller never resumes. A pending admission must not retain an active-root
  reservation indefinitely after cutoff or execute after release/cutoff.
- Continue using the one framework shutdown deadline for admitted work,
  finalization, observer delivery, and cleanup. Handoff must not add a grace
  period. Preserve resources until completion or cutoff; bounded shutdown does
  not promise physical termination of arbitrary uncooperative I/O or callbacks.
- Keep the application close listener prompt and independent of framework
  listener ordering. Either ordering must obey the same ownership rules; racing
  requests need not have identical outcomes when admission closure wins in one
  schedule and handoff wins in another. Queued application work remains the
  application's responsibility to discard.
- Preserve existing `SkillTemplate.invoke()` and `validate()` behavior and
  supported API compatibility. Document the new ownership and cleanup contract
  and deliberately include its public types in supported-surface checks. Do not
  expose internal types, create bean-replacement contracts, introduce another
  executor/queue solely for handoff, or depend on listener priorities.

## Trace verification and scope

Strengthen framework-owned lifecycle evidence for an actual blocked trace
write/finalization, using internal test seams. Prove that admitted-root tracking
covers finalization and observer delivery and that blocked finalization does
not extend shutdown beyond the existing budget. Blocking a generic mission
write guard alone is insufficient evidence for actual trace finalization.

Do not add a public trace writer, sink, lifecycle event, or test-only production
hook solely to satisfy Sidecar's testing requirements. A production trace
extension is a separate capability decision and is outside this ticket.

Sidecar consumption and its real caller/client/observer integration tests are a
follow-up in that repository. The existing SC5 criterion requiring Sidecar to
block trace persistence through public contracts cannot be claimed satisfied
by framework tests. The handoff must explicitly identify that criterion for
separate disposition; this ticket does not silently revise or complete SC5.

## Acceptance criteria

- [x] An external dispatch gate can atomically accept or discard a request
  without remaining locked during skill execution. Deterministic race evidence
  covers successful handoff before close, framework close before handoff, and
  external dispatch close before handoff, including both close-listener orders.
- [x] Successfully handed-off work can start during the remaining shutdown
  budget; rejected, released, already-consumed, or cut-off admissions cannot
  start a new execution. Each accepted execution owns exactly one root.
- [x] Abandonment, failures between handoff and execution, and concurrent
  release/execution/cutoff leave no pending reservation leak or duplicate work.
  An executing root remains tracked through finalization and observer delivery.
- [x] Existing direct invocation, validation, security, exception/observer, and
  nested-work behavior remain compatible, and new invocation behavior is
  documented and verified through the supported public surface.
- [x] Actual blocked trace finalization is exercised in framework lifecycle
  tests; completion and cutoff obey the original single budget without an early
  teardown or an additional wait period. No public trace extension is added.
- [x] Consumer documentation provides a short gate/handoff/execute/release
  example using only supported APIs and standard Java/Spring facilities.
  Architecture checks recognize only the deliberately added public contract.
- [x] Release evidence clearly separates framework verification from pending
  snapshot installation and Sidecar verification. Version remains
  `1.0.0-beta.4-SNAPSHOT`; no tag, publication, or release overwrite occurs.

## Context and sequencing

Research against framework revision
`e62b7769a93d98d74ed56a63d4b0ed4b8b641d1f` found that the internal
`FrameworkExecutionLifecycle` already supplies atomic admission and shared
deadline propagation. `LoomspanSessionRunner` holds a root through completion
and observer mapping. A single-use admitted-invocation handle is the preferred
starting point, not a mandated API shape; reuse this lifecycle rather than
creating a separate shutdown authority.

The Sidecar source and review are at:

- `C:/opendev/code/loomspan-sidecar/src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java`
- `C:/opendev/code/loomspan-sidecar/ai/thoughts/reviews/2026-09-13-sidecar-packaging-release-review-3.md`
- `C:/opendev/code/loomspan-sidecar/ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md`

The existing Sidecar race test pauses before `begin()`, not after its successful
return. The reported gap must not be considered repaired merely because that
test continues passing. The initial framework baseline passed 31 tests across
`FrameworkExecutionLifecycleTest`, `FrameworkShutdownIntegrationTest`, and
`LoomspanPublicSurfaceArchitectureTest`; this is not evidence for the new API.

Keep changes in this repository. Reconcile the stale AGENTS.md statement that
there is no supported SPI with the README and executable allowlist, which
already recognize `RestSkillHandler`; this is not authorization for a broader
extension system.

Preserve the existing local modification to
`ai/thoughts/release-readiness/1.0.0-beta.4.md`. After implementation, update that
record with the implemented revision and actual framework verification, and
record the required local snapshot reinstall and Sidecar adaptation/rerun.
Do not install, publish, or run the Sidecar pipeline as an implicit part
of this ticket. Do not mark Sidecar evidence current before the rebuilt
snapshot has actually been installed and the affected Sidecar checks rerun.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change deliberately adds a supported API and changes
  cross-boundary admission, lifecycle, cancellation, and security-context
  handling. Research, compatibility analysis, planning, and independent review
  are required even though the intended capability is narrow.
- **Reassessment triggers:** Evidence that the solution requires a compatibility
  break, a general extension system, or a change to the one-budget shutdown
  model requires developer direction rather than expanding this ticket.
