# PR 5.1 — Bound framework shutdown across execution and observation

## Outcome

Embedded applications can shut down Loomspan within one configured framework
budget while admitted skill trees finish safely. New roots stop entering
atomically, and execution, trace completion, and existing success observation
share one lifetime. This is framework protection independent of Sidecar.

## Requirements — binding

- Consolidate run/call session-runner entry methods around one internal root
  operation: admit, construct/bind, execute, finalize the session, restore
  execution binding, deliver available history, release ownership in `finally`.
  Admit and release exactly once, including construction, execution,
  finalization, mapping, and observer failures. Keep public-view mapping in the
  facade and observation on the caller after binding restoration. Preserve
  current success-observer exception propagation unchanged, outside execution
  exception wrapping. Failure-history expansion belongs to PR 5.3.
- Add documented `loomspan.shutdown.timeout`, a positive YAML duration with
  default `30s`; invalid values fail startup. Start one monotonic deadline when
  framework admission closes, covering execution, observation, cutoff and
  executor cleanup. No new budget per mission, worker, executor, or stage.
- At the authoritative root/session-entry boundary, order shutdown and root
  admission atomically. Reject new top-level work without constructing or
  executing a root session after closure. Admission is independent of optional
  observability and of host gates. A pre-check is never a reservation; PR 5.3
  proves this with its new public `validate` methods.
- Already-admitted roots may invoke nested skills until completion or their
  applicable deadline. Existing quotas, depth, mission timeouts and late-write
  fences continue. Associate existing mission lifecycles and owning futures
  with the admitted root, including missions registered concurrently with
  cutoff; registration must observe an already-reached cutoff.
- Use independent, prompt `ContextClosedEvent` listeners. The framework closes
  root admission and starts its deadline. A future Sidecar listener independently
  closes admission/dispatch and discards queued work. Neither listener waits,
  calls the other, or depends on relative priority. Filter to the owning context
  and opt out of asynchronous event execution.
- The framework waits in the subsequent Spring lifecycle-stop stage, before
  resources required by active work stop. Use ordinary Spring lifecycle
  phases/dependencies to retain caller workers, handler clients and the mission
  executor through completion/cutoff. A shorter Spring lifecycle-phase timeout
  must not reduce the remaining framework budget. No second configured timer.
- At the shared deadline, signal cancellation and fence remaining direct and
  step-loop missions without indefinite waits for uncooperative code or trace
  I/O. Clamp the existing 250ms cancellation grace to the remaining budget.
  Cutoff must not block indefinitely on mission ancestry locks held by trace
  writers, including cancellation-failure recording. Resumed writers cannot
  restore success credit or admit nested work after cutoff. Preserve primary
  failure and cleanup when an owning future raises `CancellationException`.
- Replace blocking mission-executor `close()` destruction and disable inferred
  close. Supply idempotent, non-waiting destruction fallback that closes
  admission and cancels even after failed startup or skipped/failed event
  delivery, without starting another wait. Do not run arbitrary skill,
  observer, mapping, or trace-I/O code on the shutdown waiter. The bound covers
  framework-owned shutdown, not unrelated hooks or the entire JVM; never halt
  an embedding application's JVM.
- Deliver configuration/behavior documentation and required tests with this
  feature. Preserve existing observation; do not defer its lifetime correctness
  to PR 5.3. Keep one root path, with no observer job, handoff registry, second
  scheduler, public cancellation API, or public worker-tracking surface.

## Acceptance criteria

- [ ] Both runner entry methods share one admission-to-release operation.
  Success and construction/execution/finalization/observer failures release
  ownership exactly once. Success callbacks retain their prior context and
  unchanged exception behavior; blocked mapping/callback stays owned through
  completion or cutoff and never runs on the shutdown waiter.
- [ ] Shutdown/admission races reject late roots before session construction
  or execution, while admitted roots can still perform required nested work
  within their deadlines. Concurrent mission registration respects cutoff.
- [ ] A blocked trace writer holding an ancestry lock cannot extend shutdown
  past the shared deadline. Resumption cannot regain execution credit or new
  nested work. Cancellation-failure recording and destruction fallback satisfy
  the same bound and fencing.
- [ ] Direct and step-loop missions are cancelled/fenced at cutoff, including
  cancellation of an owning future, with primary-failure and cleanup semantics
  intact. Uncooperative work does not cause indefinite executor destruction.
- [ ] Default `30s`, explicit positive durations and invalid-value startup
  diagnostics are covered. Ordinary mission timeouts still apply, nested work
  and necessary resources survive until completion/cutoff, and all framework
  waits/cleanup use one budget apart from bounded scheduling overhead.
- [ ] Framework context tests exercise both relative orders with an independent
  host-gate listener, prompt event return before lifecycle waiting, a standard
  asynchronous multicaster, owning-context filtering, repeated close, failed
  startup, skipped/failed event delivery and bounded idempotent fallback. A
  framework budget longer than Spring's phase timeout is preserved. These are
  framework proofs; actual Sidecar resource/listener wiring remains SC5 proof.
- [ ] Documentation states admission rejection, nested-work behavior, timeout
  scope, independent listeners/resource lifetime and observer lifetime.
  `LoomspanPublicSurfaceArchitectureTest` and the full starter suite pass with
  no new application API/SPI or dependency on optional observability.

## Context, dependencies and scope

No feature dependency. This owns FW1's first seven lifecycle/shutdown criteria
and the shared architecture/full-starter check. PR 5.2 depends on this ticket;
PR 5.3 extends this same operation with failure observation and the real
pre-check/shutdown race. Actual Sidecar lifecycle/resource proof belongs to
SC5, not this ticket, and does not excuse incomplete framework guarantees.

Current requirements are the [roadmap](../phases/beta4-rest-skills-and-sidecar-roadmap.md)
and [FW1](../phases/phase-fw1.md); [ticket readiness](../beta4-ticket-readiness.md)
owns delivery boundaries. The [design lens](../framework-feature-design-lens.md)
guides simplicity. [Review](../beta4-design-review.md) and
[grounding](../beta4-code-grounding.md) explain accepted S1/R1/G3 decisions and
old-code evidence at `1e4eb455`; their earlier listener-order proposal is
superseded, and the historical 219-test run does not prove this feature.

Existing runner, mission lifecycle, work executor and facade are reuse anchors,
not frozen helper signatures. A narrow internal completion callback is a
nonbinding implementation suggestion. Exact phases, synchronization, fixtures
and commands belong to pipeline research. Remove the redundant exception
condition if editing its step-loop path; avoid unrelated cleanup.

No REST implementation, new failure-history behavior, Sidecar application,
HTTP transport, public shutdown API, release or publishing work. Internal and
autoconfigure Java signatures are not compatibility contracts; do not preserve
obsolete paths with shims. Supported API signatures must not expose them.

## Pipeline notes

- Rejecting new top-level invocation during shutdown is an intentional beta 4
  behavioral change. Document the new configuration/default and caller impact;
  no legacy admission path or compatibility shim is required. Existing
  success-observer behavior must remain compatible.
