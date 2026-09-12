# Framework Lifecycle Shutdown Implementation Plan

## Overview

Implement one framework-owned lifecycle boundary that atomically stops new root skill invocations, lets admitted skill trees and their caller-side success observation finish within one monotonic `loomspan.shutdown.timeout`, and cuts off remaining missions and executor work without blocking Spring shutdown on uncooperative code or trace I/O. The design extends the existing session and mission authorities instead of adding a public cancellation API, observer worker, scheduler, or Sidecar dependency.

## Current State Analysis

`DefaultSkillTemplate` validates input before creating a session, calls `LoomspanSessionRunner`, then maps the finalized journal and invokes the optional success observer synchronously after the runner has already returned (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130`). The runner duplicates construction, binding, failure capture, and finalization between `runWithNewSession` and `callWithNewSession`, and has no framework admission or ownership concept (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:150-234`).

Each mission has an independent `MissionLifecycle`. It already owns mission-local futures, all-or-none ancestry admission, a first cancellation cause, a 250 ms cleanup grace, and late-write fencing, but cancellation and closure acquire ancestry locks that are also held while trace I/O runs (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:63-238`, `:302-408`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/state/DefaultExecutionStateService.java:71-117`, `:330-400`, `:457-463`). Consequently, a lifecycle-stop thread cannot safely use the current cancellation API at a shared deadline.

Spring currently creates one virtual-thread-per-task executor with `destroyMethod = "close"`; Java 21 executor close can wait indefinitely, and Loomspan has no close-event listener or `SmartLifecycle` waiter (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:338-389`). Configuration is strict and has no shutdown group (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:21-104`), while the closest validation pattern is the positive, defaulted mission timeout (`LoomspanProperties.java:274-302`).

## Desired End State

One internal framework lifecycle owner provides the atomic root-admission boundary, admitted-root registry, mission association, monotonic shutdown deadline, Spring close listener, lifecycle-stop wait, and executor shutdown coordination. A root lease begins before session construction and is released exactly once after binding restoration, session finalization, public-view mapping, and synchronous observer delivery (or the corresponding failure). Close-event handling only closes admission and starts the deadline; lifecycle stop waits for root completion and executor cleanup until that same deadline; destruction performs only idempotent, non-waiting fallback.

At the deadline, the owner publishes a lock-free framework cutoff to every registered mission and interrupts known owning/task futures without acquiring mission ancestry locks or recording trace failures. Current and future mission admission/write checks consult that root cutoff. The application caller or mission worker—not the shutdown waiter—performs existing failure recording and cutoff-authorized frame cleanup when it observes cancellation. Direct and step-loop owning-future paths both preserve the installed primary framework-shutdown failure.

The end state is verified by deterministic unit tests for admission, registration, cutoff, ownership, and configuration; adversarial direct/step-loop tests for blocked writers and owning-future cancellation; and Spring context tests covering event ordering, multicasting, context ownership, lifecycle phases, shorter Spring timeouts, repeat/fallback paths, and retained resources. The full starter suite and supported-surface architecture tests remain green.

### Key Discoveries:

- The facade's current catch block ends before mapping and observer delivery, so observer exceptions propagate unchanged and must remain outside execution wrapping (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:94-128`; `DefaultSkillTemplateTest.java:243-263`).
- `ExecutionCoordinator` is the authoritative top-level-versus-nested classifier, and every `MissionContext` currently constructs its own lifecycle (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:77-103`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionContext.java:17-58`). The session/root lease is therefore the coherent association point for all descendant missions.
- Future registration already follows the required race pattern: publish under lifecycle synchronization, then cancel outside the lock when cancellation was already visible (`MissionLifecycle.java:63-82`, `:136-148`). The framework-level registration path should preserve this add-then-recheck property using a root cutoff signal that never waits for mission locks.
- Spring Context 7.0.8 publishes `ContextClosedEvent` before lifecycle stop and bean destruction. `ApplicationListener.supportsAsyncExecution()` must return `false`, and `SmartLifecycle.stop(Runnable)` may perform the framework's own bounded synchronous wait before Spring begins its phase-latch timeout (`pom.xml:49-59`; research section 7).
- Auto-configuration architecture tests explicitly classify bean factory methods as internal and maintain no supported override allowlist (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanAutoConfigurationBoundaryTest.java:28-80`, `:112-135`).

## What We're NOT Doing

- No REST transport, Sidecar application/listener/resource implementation, queued-job draining, host gate, or external caller protocol.
- No new failure-history observation; PR 5.3 owns failure callbacks and public `validate` race proof.
- No new public shutdown/cancellation API, supported SPI, bean replacement seam, public worker registry, or API signature.
- No observer executor, handoff registry, extra scheduler, per-mission shutdown budget, Spring-timeout-derived budget, or JVM halt.
- No trace schema, persisted format, Console REST/SSE, acquisition, problem, or consumed-NDJSON change.
- No unrelated internal cleanup. If `StepLoopMissionExecutionEngine#executeToolAction` is touched while adding cutoff propagation, remove its already-identified redundant exception-type branch; otherwise leave it for separate work.

## Skill-Authoring Documentation Impact

**Impact**: No impact

- **Rationale**: This feature changes embedded application shutdown admission, resource lifetime, and operations configuration. It does not change manifest syntax, skill input/output contracts, planning, evidence, nested capability visibility, authorization, model selection, quotas, trace interpretation, or skill-author testing guidance.
- **Documents to update**: `None` under `agent-skills/loomspan-docs/references/skill-authoring/`; update the application-facing root `README.md` and generated Spring configuration metadata instead.
- **Supporting evidence**: `SkillTemplate` and `DefaultSkillTemplate` remain the same supported invocation surface; `ExecutionCoordinator` continues to route admitted root and nested missions through existing contracts; focused lifecycle/context tests and configuration binding tests will establish only the new application-operations behavior.
- **Coverage table update**: Not required. The skill-authoring README routes skill-tree construction topics and currently marks complete execution-limit coverage as incomplete; this ticket neither adds authoring guidance nor changes that coverage boundary.
- **LLM-first usability**: Not applicable because no skill-authoring document changes. The installed `loomspan-docs` skill is version `0.1.0-SNAPSHOT` while this checkout is `1.0.0-beta.4-SNAPSHOT`, so it was not used for version-sensitive shutdown claims. Existing invocation/observation guidance is **aligned**; comparison of the new shutdown semantics to the stale installed skill remains **unresolved** but does not affect scope because shutdown is outside the skill-authoring knowledge-set boundary.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | No type or signature change to the eight allowlisted `ai.loomspan.api` types. `SkillTemplate` callers gain deliberate rejection of new top-level invocations after shutdown admission closes; successful observer context, timing on the caller, and exception propagation remain protected (`LoomspanPublicSurfaceArchitectureTest.java:30-37`, `:276-303`; ticket Pipeline notes). | Preserve signatures and successful observer behavior. Apply the ticket-authorized beta 4 behavioral break atomically and document it; do not retain a legacy admission path. |
| Supported SPI | None exists; architecture tests reject Loomspan SPI types and supported bean override seams (`LoomspanPublicSurfaceArchitectureTest.java:324-329`; `LoomspanAutoConfigurationBoundaryTest.java:53-65`). | Preserve the absence of an SPI. Keep lifecycle types and bean methods internal/integration-only. |
| Configuration and manifest contracts | Add `loomspan.shutdown.timeout`, a positive duration defaulting to `30s`. No manifest syntax or skill-author semantics change (`LoomspanProperties.java:21-104`, `:274-302`). | Add and document one coherent setting; reject null/zero/negative values at startup. No alias or deprecated property. |
| Persisted or serialized contracts | No schema or durable format changes. Trace finalization remains part of an admitted root lifetime (`LoomspanSession.java:677-752`). | Preserve current formats and update no compatibility marker. |
| Ephemeral diagnostic formats | No event/field change. Deadline cutoff may end current-run traces earlier, while accuracy, failure visibility, and late-write fencing remain required. | Keep writer, finalizer, projection, and public mapping coherent. Do not perform trace I/O on the shutdown waiter. |
| Internal or accidentally exposed implementation | Runner overload decomposition, mission constructors/lifecycle methods, executor destruction, and auto-configuration wiring change. These types are internal even when technically public (`LoomspanPublicSurfaceArchitectureTest.java:263-267`; `LoomspanAutoConfigurationBoundaryTest.java:68-135`). | Update all repository callers/tests atomically and remove obsolete paths. Do not add compatibility constructors, adapters, or duplicate lifecycle authorities. |

- **Evidence of supported contracts**: The `LoomspanPublicSurfaceArchitectureTest` allowlist, README invocation guidance, `SkillTemplate`, `DefaultSkillTemplateTest#observerExceptionPropagatesAfterExecutionCompletes`, the binding ticket, and its Pipeline notes.
- **Intentional compatibility changes**: New top-level work is rejected once framework shutdown starts. The ticket explicitly authorizes this beta 4 behavior and rejects a legacy admission path; callers should treat context shutdown as closed admission.
- **In-repository consumers to update**: `DefaultSkillTemplate`, `LoomspanSessionRunner`, `LoomspanSession`, `ExecutionCoordinator`, `MissionContext`, `MissionLifecycle`, both mission engines, auto-configuration, property/metadata tests, architecture allowlists, runner/facade tests, mission/cutoff tests, new Spring context fixtures, and README operations/invocation guidance.
- **Public-surface delta**: None. No `ai.loomspan.api` type, signature, constructor, or supported Spring extension point is added or removed. New technically public internal types needed across internal packages must be explicitly classified by the architecture test and remain outside `ai.loomspan.api`.
- **Shim decision**: **No shim.** The changed constructors, overloads, bean factory methods, and lifecycle paths are internal or auto-configuration machinery; the ticket requires one coherent root path and authorizes atomic in-repository updates.
- **Java-to-Go boundary coordination**: **Not required.** No application-adapter REST/SSE, acquisition, problem, or consumed-NDJSON boundary changes.
- **Pipeline notes alignment**: **Aligned.** The only intentional observable break is late root rejection after admission closure, exactly covered by the note. Existing success-observer behavior is explicitly preserved.

## Implementation Approach

Add one internal `FrameworkExecutionLifecycle` (final name may follow local package vocabulary) that owns a small lock/condition for root admission and completion, a monotonic deadline, active root leases, the registered mission set for each root, and the mission executor. Its `ContextClosedEvent` listener only establishes the atomic gate/deadline, filters by exact owning `ApplicationContext`, and opts out of async delivery. Its `SmartLifecycle` stop uses the already-started deadline to wait for root releases, then shuts down/awaits the executor only within remaining time; at the deadline it publishes framework cutoff, cancels known futures, calls `shutdownNow`, and returns. Its destroy/factory fallback repeats only close/cutoff/`shutdownNow` and never waits.

Use an `AdmittedRoot` lease acquired before `LoomspanSession` construction. The lease owns a concurrent mission registry and one atomic cutoff signal. Mission registration adds the mission then rechecks the signal, so a concurrent or late registration observes cutoff. Framework cutoff installs an internal `FrameworkShutdownException` as the mission's primary cause and exposes the shared absolute deadline without acquiring mission ancestry locks or recording failures. Owning/task future references must be safely observable/cancellable without taking a trace-contended lifecycle lock, while ordinary local cancellation continues to use the existing mission authority. Every new-work/write path checks both local ancestry state and the root cutoff; local cleanup grace becomes `min(now + 250ms, sharedDeadline)`.

Consolidate the runner's run/call bodies into one generic internal root operation. Add a narrow package-private completion callback that executes only after `ExecutionBindingScope` restoration but before the root lease is released. The facade supplies mapping and success observation through that callback. Preserve the current exception boundary with an internal phase marker/unwrap (or an equivalently narrow internal mechanism): construction/execution/finalization runtime failures still receive the facade's safe wrapping, while public-view mapping and observer runtime failures propagate unchanged. No callback is scheduled or invoked by the lifecycle owner.

## Phase 1: Configuration and Framework Root Authority

### Overview

Create the single monotonic framework lifecycle authority and its configuration without yet changing mission internals.

### Changes Required:

#### 1. Shutdown configuration
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java`
**Changes**: Add a validated `Shutdown` group with `timeout = Duration.ofSeconds(30)`, getter/setter binding, and the existing positive-duration rejection pattern. Keep strict unknown-field behavior.

#### 2. Internal root lifecycle owner
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java` (new)
**Changes**: Implement atomic `OPEN -> CLOSING` admission, one saturating monotonic deadline, active-root leases with exactly-once close, root completion signalling, and idempotent cutoff/destruction state. Implement `ApplicationListener<ContextClosedEvent>` with exact-context filtering and `supportsAsyncExecution() == false`; implement `SmartLifecycle` so event handling stays prompt and waiting happens only in stop. Keep the Spring stop callback in `finally` and make repeated stop/close/destroy calls converge on the same deadline and cutoff.

```java
AdmittedRoot root = frameworkLifecycle.admitRoot();
try {
    // construct, execute, finalize, map, observe
}
finally {
    root.close();
}
```

#### 3. Focused lifecycle/configuration tests
**Files**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java` (new), `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionPropertiesTest.java`, `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/ConfigurationMetadataTest.java`
**Changes**: Use injected monotonic time and controllable roots to prove admission ordering, one deadline, exactly-once release, default/explicit/invalid binding, and metadata presence.

### Success Criteria:

#### Automated Verification:
- [x] Lifecycle and property tests pass: `mvn -pl loomspan-spring-boot-starter -Dtest=FrameworkExecutionLifecycleTest,LoomspanSessionPropertiesTest,ConfigurationMetadataTest test`
- [x] New roots cannot be admitted after the close linearization point, and a root admitted before it remains tracked.
- [x] Repeated event/stop/destroy calls neither extend the deadline nor double-release roots.

---

## Phase 2: One Root Operation Through Success Observation

### Overview

Move root ownership into the session entry path and keep it through caller-side mapping and success observation without changing the public facade contract.

### Changes Required:

#### 1. Consolidated runner operation
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java`
**Changes**: Replace duplicated run/call implementations with one generic operation that admits before constructing `LoomspanSession`, binds and finalizes once, restores the binding, invokes a narrow completion callback, and closes the root lease in `finally`. Update internal constructors/callers atomically rather than retaining compatibility overload paths solely for tests.

#### 2. Session/root association and facade completion
**Files**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java`, `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java`
**Changes**: Carry the admitted-root identity in the live internal session. Supply public-view mapping and observer delivery as the runner completion callback, after binding restoration. Preserve null-input/validation timing before admission and preserve existing exception categories: observer and mapping failures leave ownership exactly once but are not transformed as execution failures.

#### 3. Runner/facade regression tests
**Files**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java`, `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
**Changes**: Add admission/release cardinality probes for success, construction, action, finalization, mapping, and observer failures; assert binding restoration before mapping/callback; retain the exact observer exception instance; and prove a blocked mapper/observer remains an active root but is never run by the shutdown waiter.

### Success Criteria:

#### Automated Verification:
- [x] Runner and facade tests pass: `mvn -pl loomspan-spring-boot-starter -Dtest=LoomspanSessionRunnerTest,DefaultSkillTemplateTest test`
- [x] Both run/call entry methods exercise one admission-to-release implementation, with no separate observer job or ownership handoff.
- [x] Invalid caller input still fails before root/session construction, and successful observer exceptions propagate unchanged after binding restoration.

---

## Phase 3: Deadline-Safe Mission Cutoff

### Overview

Associate descendant missions and futures with the admitted root and make the framework deadline capable of fencing/cancelling them without waiting for trace-contended ancestry locks.

### Changes Required:

#### 1. Mission registration and effective fence
**Files**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionContext.java`, `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java`, `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java`
**Changes**: Register every created root/child mission with its session's admitted root. Add a lock-free, idempotent framework-cutoff signal and effective ancestry checks that consider both local mission state and root cutoff. Use add-then-recheck registration so missions and futures racing with cutoff are interrupted/fenced. Keep ordinary mission cleanup snapshots on caller/worker paths; the shutdown waiter only publishes state and cancels future references. Clamp local `CLEANUP_GRACE` to the root's remaining absolute deadline.

#### 2. Owning-future cancellation parity
**Files**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java`, `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java`
**Changes**: Install one internal framework-shutdown primary cause before cancelling futures. Add direct-path `CancellationException` handling matching the step-loop cleanup/primary propagation contract; make both paths record/clean up on their own execution/caller path when permitted, never on the shutdown waiter. Preserve existing timeout/interruption first-cause semantics and cleanup suppression. Remove only the redundant exception-type branch in `executeToolAction` if this work edits that method.

#### 3. Adversarial mission tests
**Files**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/MissionLifecycleTest.java`, `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/JavaSkillMissionCutoffTest.java`, `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`
**Changes**: Prove cutoff publication and shutdown return while a writer holds an ancestry lock; after release, assert no success credit or nested admission. Cover cancellation-failure recording, concurrent mission/future registration, direct and step-loop owning-future cancellation, primary cause identity, cleanup once, unchanged ordinary mission timeouts, and uncooperative worker return.

### Success Criteria:

#### Automated Verification:
- [x] Mission cutoff tests pass: `mvn -pl loomspan-spring-boot-starter -Dtest=MissionLifecycleTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest test`
- [x] Framework cutoff takes no mission ancestry lock and invokes no trace/session/observer/mapping callback.
- [x] Direct and step-loop cancellations propagate the previously installed primary cause and perform cutoff-authorized cleanup at most once.

---

## Phase 4: Spring Lifecycle and Nonblocking Executor Teardown

### Overview

Wire the owner into Spring's event, lifecycle-stop, dependency/phase, and destruction stages so the one framework deadline includes executor cleanup and retained framework resources.

### Changes Required:

#### 1. Auto-configuration wiring and executor destruction
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`
**Changes**: Register the lifecycle owner as an infrastructure bean with the owning application context, configured timeout, and mission executor. Inject it into the session runner. Use the highest framework lifecycle phase so its bounded wait begins before lower-phase resources stop, and preserve explicit dependencies on the executor/runtime resources needed by admitted roots. Replace `destroyMethod = "close"` and inferred close with explicit idempotent non-waiting `shutdownNow` fallback. On an early clean drain, call `shutdown` and await termination only for the shared deadline's remaining duration; at deadline call `shutdownNow` and return without a second wait.

#### 2. Framework context lifecycle fixture
**File**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java` (new; exact package may follow existing context-test conventions)
**Changes**: Build small application contexts with controlled roots, listeners, multicaster executors, lifecycle resources, and executor workers. Exercise independent host-gate listener before/after the framework listener, prompt event return before lifecycle waiting, standard async multicaster with framework sync opt-out, exact owning-context filtering, parent/child close events, repeated close, failed startup, skipped/throwing event delivery, idempotent destruction, resource availability, and a framework timeout longer than Spring's per-phase timeout.

#### 3. Auto-configuration boundary tests
**Files**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanAutoConfigurationBoundaryTest.java`, `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`, `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`
**Changes**: Update exact internal bean/type classifications and assert no new API/SPI or conditional replacement seam. Verify normal and failed-startup contexts receive nonblocking executor cleanup.

### Success Criteria:

#### Automated Verification:
- [x] Spring lifecycle tests pass: `mvn -pl loomspan-spring-boot-starter -Dtest=FrameworkShutdownIntegrationTest,LoomspanAutoConfigurationTests,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest test`
- [x] A shorter Spring phase timeout does not truncate the framework owner's already-running remaining budget.
- [x] Listener failure or omission still reaches bounded, idempotent, non-waiting destruction without `ExecutorService.close()`.
- [x] Both listener orders and an asynchronous multicaster preserve independent prompt gate closure.

---

## Phase 5: Documentation and Full Verification

### Overview

Document the new application operations contract and run the complete supported-surface and starter verification.

### Changes Required:

#### 1. Application-facing operations guidance
**File**: `README.md`
**Changes**: Document `loomspan.shutdown.timeout` default/validation; atomic new-root rejection; admitted nested work and existing mission limits; one budget across execution, trace completion, mapping/observer, cutoff, and executor cleanup; independent close listeners and retained-resource lifetime; and the fact that the bound excludes unrelated hooks/JVM shutdown. Preserve current warnings about diagnostic data and observer behavior.

#### 2. Configuration metadata
**Files**: generated metadata from `LoomspanProperties`, plus `loomspan-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json` only if the processor cannot express an exact description/default
**Changes**: Ensure IDE metadata exposes the positive duration and `30s` default without duplicating authority unnecessarily.

### Success Criteria:

#### Automated Verification:
- [x] Public surface remains closed: `mvn -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest,LoomspanAutoConfigurationBoundaryTest test`
- [x] Full starter suite passes: `mvn -pl loomspan-spring-boot-starter test`
- [x] Parent verification passes: `mvn verify`
- [x] README and generated metadata contain the same property name, default, and scope asserted by configuration tests.

---

## Testing Strategy

### Unit Tests:

- Fail first on atomic admission/closure, root release cardinality, monotonic deadline reuse, positive-duration binding, listener filtering/async opt-out, mission registration after cutoff, and direct owning-future cancellation.
- Use latches and injected monotonic time instead of sleep-sensitive timing for admission races, blocked mapping/observer, blocked ancestry writers, cleanup grace clamping, and repeated close.
- Assert both state and forbidden side effects: no session construction for rejected roots, no post-cutoff successful-skill credit/nested admission, no shutdown-thread callbacks or trace writes, and no blocking `close()` path.

### Integration Tests:

- Exercise real Spring context close sequencing with controlled `ContextClosedEvent` listener orders, async multicasting, lifecycle phases/dependencies, failed/skipped event delivery, and explicit executor fallback.
- Execute representative direct Java and step-loop roots through `SkillTemplate`, including nested work, observer blocking/failure, framework cutoff, ordinary mission timeout, and uncooperative work.
- Run architecture, configuration metadata, auto-configuration, focused lifecycle, and complete starter suites.

**Note**: The dedicated testing plan produced by `3_testing_plan.md` contains the exact failing-test order, fixtures, commands, and exit criteria.

## Performance Considerations

Admission and release add one short framework-owner critical section per root invocation. Mission registration uses a concurrent root-local set and an atomic cutoff recheck; no global scan occurs during ordinary mission writes beyond a cheap root-cutoff read. Deadline processing scans only active roots/missions and known futures. The shutdown path must never wait on mission ancestry locks; normal trace/write locking remains unchanged except for the additional effective fence check.

## Migration Notes

Applications receive the new default `30s` shutdown budget automatically. Configure `loomspan.shutdown.timeout` with any positive Spring duration when a different budget is required. Calls racing after framework shutdown begins now fail before a root session is constructed; applications must stop issuing new roots when their context is closing. No Java signature, manifest, trace, persisted-data, or Console migration is required, and no compatibility alias or shim is provided.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-5.1-framework-lifecycle.md`
- Related research: `ai/thoughts/research/2026-09-12-framework-lifecycle-shutdown.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Shared lifecycle requirements: `ai/thoughts/phases/phase-fw1.md:161-221`
- Accepted one-root-operation design and listener correction: `ai/thoughts/beta4-design-review.md:44-67`, `:190-233`
- Current root runner/facade: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:150-313`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130`
- Current mission authority: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:21-408`
- Current executor wiring: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:338-389`
