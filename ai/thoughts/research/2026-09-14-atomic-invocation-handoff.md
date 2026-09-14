---
date: 2026-09-14T10:33:30-07:00
researcher: matt-loomspan-ai
model: GPT-5
git_commit: af9f6653da2be764b1f612f0a5d5f0cc998c7df0
branch: main
repository: loomspan-framework
topic: "PR 5.6 — Atomically hand external invocations to framework ownership"
tags: [research, codebase, public-api, admission, lifecycle, shutdown, security, tracing]
status: complete
last_updated: 2026-09-14
last_updated_by: matt-loomspan-ai
---

# Research: PR 5.6 — Atomically hand external invocations to framework ownership

**Date**: 2026-09-14T10:33:30-07:00
**Researcher**: matt-loomspan-ai
**Model**: GPT-5
**Git Commit**: af9f6653da2be764b1f612f0a5d5f0cc998c7df0
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current framework and Sidecar seams relevant to
`ai/thoughts/tickets/loomspan-pr-5.6-atomic-invocation-handoff.md`: supported
skill invocation, atomic root admission, root lifetime and cutoff, security
context behavior, mission timeout timing, trace finalization and observation,
listener ordering, compatibility boundaries, existing tests, and release
evidence. This is pipeline Step 1 research only; it does not choose or implement
the new API shape.

## Summary

Loomspan currently has one internal authority for root admission and shutdown:
`FrameworkExecutionLifecycle`. Its `admitRoot()` and `closeAdmission()` methods
use the same monitor, so a direct root is either inserted in `activeRoots` or
rejected before session construction. `LoomspanSessionRunner` acquires that
root itself for every direct call, keeps it through session construction,
execution, trace completion, execution-binding restoration, public-view mapping,
and observer delivery, and releases it in `finally`.

The supported `SkillTemplate` API has four `invoke` and two advisory `validate`
overloads. It exposes no way to acquire framework admission separately from
execution and no way to execute against a previously acquired admission. The
internal admitted-root object is an idempotently closeable reservation, but it
has no pending/claimed/released public state machine: closing it removes the
root even if execution is using it, while shutdown cutoff signals registered
missions but does not itself remove an unclosed admitted root from
`activeRoots`.

The motivating Sidecar worker holds its own gate while `begin()` decides whether
the request remains host-owned, then releases the gate, installs authentication,
and calls `SkillTemplate.invoke()`. The interval after successful `begin()` and
before `invoke()` is not covered by either gate as one atomic ownership
transition. The current Sidecar race test pauses before `begin()`, matching the
later review's finding that it does not exercise this interval.

Existing framework tests cover direct admission versus shutdown, advisory
validation, root ownership through completion/observation, listener order, and
a generic blocked mission write guard. They do not currently exercise a
separately handed-off pending admission or an actual blocked canonical
`TRACE_COMPLETED` write. The trace implementation already has internal-only
factory and writer seams that focused tests use without exposing a public trace
extension.

The bundled `loomspan-docs` Java API guidance matches the checked-out source for
current invocation, validation, security, error, and observation behavior
(**aligned**). It has no handoff contract because none exists. Root `AGENTS.md`
still says Loomspan exposes no supported SPI, while the executable allowlist,
README, and version-aligned knowledge set identify `RestSkillHandler` as the sole
supported SPI; that statement is **documentation drift**. The committed release
readiness record says SC5 found no framework defect, but the later Sidecar review
records the atomic-transition and real-lifecycle evidence gaps; the ticket
explicitly carries that later evidence forward.

## Detailed Findings

### 1. Supported application invocation surface

- `SkillTemplate` is a supported Application API interface with six abstract
  methods: two advisory `validate` overloads and four synchronous `invoke`
  overloads (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11-25`).
- The `Object` and `Map` overloads converge on `PreparedInput`, which contains
  internal capability metadata and normalized validation output. Object
  conversion, exact capability lookup, null handling, and input validation all
  occur before the session runner is called
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:113-157`).
- `validatePrepared` captures the current `Authentication` and evaluates the
  root role policy without creating a session or reserving admission
  (`DefaultSkillTemplate.java:159-174`).
- `invokePrepared` captures the current `Authentication` immediately before it
  calls `LoomspanSessionRunner.callWithNewSession`. It supplies execution and
  completion callbacks; the completion callback maps and synchronously delivers
  the optional public view (`DefaultSkillTemplate.java:176-200`).
- The public interface contains no admission, prepared-invocation, cancellation,
  release, or asynchronous-execution type or method. Repository production use
  of `SkillTemplate` is its interface, one internal implementation, and one
  auto-configured bean; in-repository application use is in integration tests.
  The motivating non-repository consumer is Sidecar's `ExecutionCoordinator`.
- All existing `SkillTemplate` methods are abstract. The beta 4 release note
  records that the earlier addition of the two abstract `validate` methods was
  source- and binary-sensitive for custom implementations, fakes, and
  hand-written mocks (`docs/releases/1.0.0-beta.4.md:23-29`).

### 2. Current root admission and shutdown ordering

- `FrameworkExecutionLifecycle` owns an `activeRoots` concurrent set,
  `admissionClosed`, `cutoff`, and one monotonic `deadlineNanos`
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:20-33`).
- `admitRoot()` synchronizes on `monitor`, rejects when `admissionClosed` is set,
  creates an `AdmittedRoot`, and inserts it into `activeRoots` before returning
  (`FrameworkExecutionLifecycle.java:53-63`).
- `closeAdmission()` uses the same monitor. On the first close it establishes
  exactly one deadline and publishes it to every currently active root, then
  returns without waiting (`FrameworkExecutionLifecycle.java:73-85`). The
  owning `ContextClosedEvent` calls this method and the listener explicitly opts
  out of asynchronous execution (`FrameworkExecutionLifecycle.java:88-95`).
- Spring lifecycle `stop()` closes admission, waits for active roots only until
  the shared remaining budget, shuts down the mission executor, waits for that
  executor only within the same budget, and publishes cutoff if necessary
  (`FrameworkExecutionLifecycle.java:101-179`). `destroy()` is a non-waiting
  fallback that closes admission and publishes cutoff
  (`FrameworkExecutionLifecycle.java:181-187`).
- `AdmittedRoot` registers mission lifecycles, propagates an already established
  deadline or cutoff to late registrations, and releases itself idempotently on
  `close()` (`FrameworkExecutionLifecycle.java:213-250`). Cutoff marks the root
  and signals its registered missions; it does not call the owner's `release`
  method. An admitted root with no registered mission therefore remains in
  `activeRoots` until its holder closes it.
- The current internal admitted root has only an idempotent `closed` flag and a
  cutoff flag. It does not distinguish pending, execution-claimed, executing,
  or released states. Its `close()` does not inspect mission or execution state
  before removing the root from `activeRoots` (`FrameworkExecutionLifecycle.java:213-250`).

### 3. Direct execution uses one root from admission through observation

- Every `LoomspanSessionRunner` root entry method converges on `executeRoot`
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:187-217`).
- `executeRoot` admits before constructing `LoomspanSession`; a closed framework
  therefore rejects before the observation handle or trace handle is created.
  It attaches the admitted root to the new session, establishes the execution
  binding, executes the supplied action, finalizes the session in the binding,
  restores the binding, invokes the completion callback, and closes the root in
  `finally` (`LoomspanSessionRunner.java:215-266`).
- A session creates its observation handle and trace handle during construction,
  then stores the supplied authentication
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:233-270`).
  `attachAdmittedRoot` permits exactly one attachment
  (`LoomspanSession.java:273-281`).
- Each new `MissionContext` registers its lifecycle with the session's admitted
  root. This connects top-level and nested mission cutoff/deadline propagation
  to the same root (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionContext.java:46-53`).
- Top-level capability execution finalizes the trace before returning from the
  action (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:229-259`).
  The runner's finalization path is idempotent when the trace is already complete
  (`LoomspanSessionRunner.java:281-359`).
- The runner completion callback occurs only after execution binding restoration
  and before root release. `DefaultSkillTemplate` uses it for public-view mapping
  and the observer, so both remain inside root ownership
  (`FrameworkExecutionLifecycleTest.java:100-119`,
  `FrameworkExecutionLifecycleTest.java:122-159`).

### 4. Authentication and authorization flow

- `DefaultSkillTemplate.invoke` captures the authentication present on the
  execution-calling thread; it does not use authentication captured by an
  earlier `validate` call (`DefaultSkillTemplate.java:176-187`).
- `CapabilityExecutionRouter` enforces access against the session/caller before
  dispatching to the execution coordinator, repeats normalized input validation,
  and requires the current execution binding
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:39-68`).
- The execution coordinator opens the mission frame and checks root access
  before direct or model-backed work. For Java and REST skills it resolves the
  caller, runs through `MissionWorkExecutor`, installs a fresh security context
  only around the application proxy/handler call, and restores the exact prior
  context through `ScopedAuthentication.Scope.close()`
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:99-166`;
  `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/ScopedAuthentication.java:9-33`).
- The current tests verify that `invoke` captures the current authentication,
  `validate` checks current calling authentication without executing, and
  `AccessDeniedException` remains the same instance across the facade
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:53-171`).
- Bundled guidance states that trusted authentication comes from the caller,
  execution-time authentication has priority over session fallback, actual Java
  and REST workers receive an isolated context, and nested/parallel work carries
  that identity without sharing mutable contexts
  (`agent-skills/loomspan-docs/references/skill-authoring/authorization.md`,
  “Local visibility and caller scope”). This is aligned with the current source.

### 5. Mission timeout begins with execution, not root admission

- Direct Java/REST and ordinary YAML missions go through `MissionWorkExecutor`.
  It constructs a mission callable, submits it to the mission executor, registers
  the owning future, and then performs `Future.get(missionTimeout)`
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-72`).
- Step-loop YAML execution similarly submits its owning mission future and calls
  `Future.get(missionTimeout)` after submission
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:284-320`).
- Root admission itself stores only lifecycle ownership/deadline state. It does
  not create a mission or start a mission timeout. Framework shutdown can,
  however, establish its separate shared deadline on an admitted root before a
  mission is later registered (`FrameworkExecutionLifecycle.java:223-244`).

### 6. Trace finalization and observer delivery

- `LoomspanSession.finalizeTrace` holds the session lock while it projects the
  execution journal and asks the trace handle to finalize. It unlocks before
  closing the observation handle; optional observation-close failures are
  ignored and cannot change canonical finalization
  (`LoomspanSession.java:704-820`).
- `DefaultExecutionTraceHandle.finalizeTrace` appends the canonical
  `TRACE_COMPLETED` record through its `TraceRecordWriter`, marks the trace
  completed, and then retains/deletes or describes the artifact according to
  persistence policy (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:366-405`).
- The production writer is `NdjsonTraceRecordWriter`, whose `append` opens the
  file, serializes the record, and writes the terminating newline
  (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/trace/NdjsonTraceRecordWriter.java:30-45`).
- Trace injection is internal: `LoomspanSessionRunner` has a package-private
  `InternalExecutionTraceHandleFactory`, and `DefaultExecutionTraceHandle` has a
  package-private constructor accepting the package-private
  `TraceRecordWriter` (`InternalExecutionTraceHandleFactory.java:7-15`;
  `DefaultExecutionTraceHandle.java:137-155`;
  `TraceRecordWriter.java:7-10`). `ExecutionTraceHandleTest` already uses a
  controllable writer to target selected writes
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/trace/ExecutionTraceHandleTest.java:237-285`).
- `FrameworkExecutionLifecycleTest#frameworkCutoffDoesNotWaitForMissionLockHeldByWriter`
  blocks a generic `MissionLifecycle.runIfWritable` action while holding the
  mission write/ancestry guard, then proves cutoff remains bounded
  (`FrameworkExecutionLifecycleTest.java:385-425`). It does not call
  `DefaultExecutionTraceHandle.finalizeTrace` and does not block a
  `TRACE_COMPLETED` writer append.
- Current lifecycle tests do cover a slow completion callback and prove it stays
  under one active root and on the caller thread
  (`FrameworkExecutionLifecycleTest.java:161-222`).

### 7. Motivating Sidecar ownership interval

- Sidecar admission, retention capacity, queue insertion, and queue-byte/count
  accounting happen under its `ReentrantLock` gate
  (`C:/opendev/code/loomspan-sidecar/src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:82-105`).
- A dequeued worker calls a test seam, then `begin()`. `begin()` reacquires the
  Sidecar gate, releases the queue reservation, rejects if dispatch is closed or
  the record disappeared, and otherwise marks the record running before
  releasing the gate (`ExecutionCoordinator.java:133-147`, `285-288`).
- After successful `begin()`, the worker creates and installs the JWT security
  context and only then calls `SkillTemplate.invoke()`
  (`ExecutionCoordinator.java:289-300`). Thus the successful Sidecar gate
  transition and framework root admission are two separately locked events.
- Sidecar's current deterministic race test blocks the second task in
  `dispatchHandoff` before `begin()`, publishes close, and then lets the task
  reach the gate (`C:/opendev/code/loomspan-sidecar/src/test/java/ai/loomspan/sidecar/execution/ExecutionCoordinatorTest.java:366-420`).
  The later independent review records that this does not pause after a
  successful `begin()` and before framework admission
  (`C:/opendev/code/loomspan-sidecar/ai/thoughts/reviews/2026-09-13-sidecar-packaging-release-review-3.md:24-48`).
- The Sidecar ticket requires actual listener/client/caller wiring, both listener
  orders, active observers, blocked trace writes, and public APIs/standard Spring
  facilities only (`C:/opendev/code/loomspan-sidecar/ai/thoughts/tickets/2026-09-13-sidecar-packaging-release.md:68-100`). Its local acceptance boxes at lines 150-162 were marked complete before
  review cycle 3 identified the missing atomic interval and lifecycle evidence.
- Sidecar consumption and its repaired integration tests remain outside this
  framework checkout. The controlling framework ticket names them as a later
  separate disposition rather than framework acceptance evidence.

### 8. Spring wiring and extension boundaries

- `LoomspanAutoConfiguration` creates one internal `FrameworkExecutionLifecycle`
  from the owning application context, `loomspan.shutdown.timeout`, and the
  framework mission executor (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:363-378`).
- The same auto-configuration passes that lifecycle to `LoomspanSessionRunner`
  (`LoomspanAutoConfiguration.java:149-166`) and constructs the sole
  `SkillTemplate` implementation with the runner and optional
  `SecurityContextHolderStrategy` (`LoomspanAutoConfiguration.java:279-296`).
- These bean methods are infrastructure wiring and do not use
  `@ConditionalOnMissingBean`. The supported docs expressly state that
  `SkillTemplate` is an application facade and that internal beans are not a
  replacement contract.
- `FrameworkExecutionLifecycle`, `LoomspanSessionRunner`, `DefaultSkillTemplate`,
  `InternalExecutionTraceHandleFactory`, and trace writers are Internal or
  accidentally exposed implementation under the repository's classification,
  regardless of Java visibility.

### 9. Public-surface and compatibility inventory

The current affected surfaces classify as follows under
`ai/thoughts/framework-feature-design-lens.md`:

| Category | Current evidence and treatment |
| --- | --- |
| Application API | `SkillTemplate` and the other public top-level types in `ai.loomspan.api` are deliberately supported by the architecture allowlist, root README, package Javadoc, Java API knowledge set, API-shape tests, and supported-surface integration test. A new handoff contract requested in `ai.loomspan.api` will belong to this category. |
| Supported SPI | `RestSkillHandler` is the sole documented and allowlisted SPI. The admission feature is an application invocation/lifecycle contract, not an application implementation replacement point in current evidence. |
| Configuration and manifest contracts | `loomspan.shutdown.timeout` remains the documented positive duration and one overall framework budget. No new configuration key or manifest syntax exists or is requested. |
| Persisted or serialized contracts | No current admission object is serialized or persisted. The ticket does not request an external wire/storage format. |
| Ephemeral diagnostic formats | Canonical/current-version trace records, finalized artifacts, projected journals, and public execution events cover diagnostics. The ticket asks for stronger lifecycle evidence but forbids creating a public trace extension solely for testing. |
| Internal or accidentally exposed implementation | Lifecycle root state, session runner entry methods, mission registration, trace factories/writers, Spring infrastructure beans, and their constructors are internal and may be reshaped without compatibility shims. |

- `LoomspanPublicSurfaceArchitectureTest` currently allowlists exactly thirteen
  public top-level API types (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-42`,
  `285-288`). It recursively rejects public signatures that expose internal or
  autoconfigure types (`LoomspanPublicSurfaceArchitectureTest.java:339-383`).
- The test named `noSupportedSpiPackageOrTypeExists` only asserts that no package
  name contains `.spi` (`LoomspanPublicSurfaceArchitectureTest.java:332-337`),
  while its allowlist includes `RestSkillHandler`. The README and Java API
  knowledge set explicitly call that handler the sole supported SPI.
- `ApplicationApiValueTest` verifies the current catalog, validation, REST SPI,
  and public value shapes. For `SkillTemplate` it currently checks only that the
  two `validate` overloads exist and return `void`
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:17-47`).
- `SupportedSurfaceIntegrationTest` obtains real `SkillTemplate` and
  `SkillCatalog` beans, invokes YAML, Java, and REST skills using public types,
  observes views, and checks authorization. It is the existing consumer-level
  fixture for supported composition
  (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:97-180`).
- Root README line 169, `agent-skills/loomspan-docs/references/java-api/README.md`,
  `compatibility-and-boundaries.md`, and the beta 4 release note all state the
  current exact thirteen-type count. Console's build-tool test also checks that
  the bundled Java API index names the existing supported types
  (`loomspan-console/internal/buildtool/projectdeclarations_test.go:191-204`).

### 10. Protocol, documentation, and skill-authoring impact

- The requested handoff is an in-process Java API/lifecycle change. It does not
  currently touch Loomspan Console REST/SSE acquisition, problem responses,
  consumed NDJSON, canonical trace schema, or Console compatibility markers.
  No Java-to-Go protocol consumer or fixture is implicated by current source
  unless implementation changes one of those formats.
- Root README documents direct invocation and advisory validation at lines
  139-176 and the one-budget shutdown behavior at lines 494-523. The Java API
  knowledge set routes exact invocation, validation, observation, error, and
  compatibility semantics.
- Documentation comparison for the current behavior is **aligned** across
  `SkillTemplate`, focused tests, README, and the bundled version-aligned
  `loomspan-docs` topics. The absent handoff API is new ticket scope, not drift in
  documentation of existing behavior.
- Root `AGENTS.md:10` says Loomspan currently exposes no supported Java SPI. The
  executable allowlist includes `RestSkillHandler`, and README line 169 plus the
  Java API knowledge set classify it as the sole supported SPI. This is
  **documentation drift** that the controlling ticket explicitly names.
- Skill-authoring impact is currently **none identified**: the requested feature
  changes how an embedding application transfers root ownership before invoking
  an already registered skill. It does not change manifests, planner/tool
  visibility, skill input/output contracts, nested authorization, trace content,
  or author-visible skill semantics. Java application-integration guidance is
  affected because callers need the ownership, authentication, timeout, and
  release contract.

### 11. Existing verification and release state

- `FrameworkExecutionLifecycleTest` covers admission closure, deadline/cutoff,
  root release, slow completion, late mission registration, interruption,
  fallback destruction, and the generic blocked write guard
  (`FrameworkExecutionLifecycleTest.java:28-425`).
- `FrameworkShutdownIntegrationTest` covers rejection before session
  construction, `validate` not reserving admission, synchronous admission close
  under an asynchronous multicaster, independent listener registration order,
  failed/skipped delivery fallback, and Spring phase/resource lifetime
  (`FrameworkShutdownIntegrationTest.java:40-301`).
- The committed release readiness record retains historical PASS evidence for
  the thirteen-type API and 23 lifecycle tests, and claims the Sidecar snapshot
  gate passed with no framework defect
  (`ai/thoughts/release-readiness/1.0.0-beta.4.md:91-157`). Review cycle 3 is later
  evidence and says no implementation artifacts were changed while identifying
  the handoff and real-lifecycle gaps
  (`C:/opendev/code/loomspan-sidecar/ai/thoughts/reviews/2026-09-13-sidecar-packaging-release-review-3.md:78-80`).
- Current version declarations remain `1.0.0-beta.4-SNAPSHOT` in the root and
  starter POMs (`pom.xml:9`; `loomspan-spring-boot-starter/pom.xml:10`).
- At research time the checkout is `main` at
  `af9f6653da2be764b1f612f0a5d5f0cc998c7df0`. The only worktree item before this
  research artifact was the untracked controlling ticket. Commit `af9f665`
  changed only `ai/thoughts/release-readiness/1.0.0-beta.4.md` relative to the
  ticket's research revision `e62b776`; there are no intervening production or
  test changes.

## Code References

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11-25` — complete supported facade signature.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:113-200` — input preparation, validation authorization, execution authentication capture, and observer mapping.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:53-85` — atomic internal root admission versus closure.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:101-179` — one-budget wait, executor shutdown, and cutoff.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:213-250` — current admitted-root deadline, cutoff, and idempotent release behavior.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:215-266` — one direct root from admission through completion callback.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:704-820` — journal projection, trace finalization, and observation close.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:366-405` — actual canonical completion write and artifact handling.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-72` — direct mission submission and timeout start.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:100-222` — ownership through success/failure completion and slow callbacks.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:385-425` — existing generic blocked writer-guard test.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:68-167` — pre-session rejection, advisory validation, asynchronous multicaster, and listener order.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-42` — closed API allowlist.
- `C:/opendev/code/loomspan-sidecar/src/main/java/ai/loomspan/sidecar/execution/ExecutionCoordinator.java:285-300` — current Sidecar begin/authenticate/invoke interval.

## Architecture Documentation

The existing execution architecture has three distinct ownership scopes:

1. The embedding application may own queueing and dispatch policy before a
   framework call. Loomspan currently has no public token representing the
   transition from that scope.
2. `FrameworkExecutionLifecycle` owns admitted roots and the single shutdown
   deadline. Root admission and framework closure are atomic inside its monitor.
3. Each admitted session registers mission lifecycles with its root. Mission
   timeouts, nested-work checks, cutoff fencing, finalization, view mapping, and
   observer delivery occur downstream while the runner still owns the root.

The current direct path is deliberately singular: all root runner entry methods
converge on `executeRoot`; Java and REST use the common mission worker; YAML uses
the ordinary or step-loop engine; all return through the same session and root
completion. Auto-configuration wires one lifecycle authority and one mission
executor. There is no public executor, handoff queue, trace writer, lifecycle
event, shutdown controller, or internal bean-replacement surface.

The new ticket changes the first-to-second-scope boundary, while the existing
third-scope behavior supplies the lifecycle that handed-off work must eventually
enter. Existing internal state does not yet model the public handle's required
single-use release/execute/cutoff races.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-5.1-framework-lifecycle.md` established the
  single internal root operation, atomic direct admission versus shutdown,
  one shutdown budget, prompt independent listeners, and root ownership through
  existing success observation. It explicitly excluded a public cancellation or
  worker-tracking surface.
- `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md` deliberately
  introduced `RestSkillHandler` and `RestSkillInvocation` as the first supported
  SPI types and replaced the earlier no-SPI posture only for that named contract.
- `ai/thoughts/tickets/loomspan-pr-5.3-external-caller-api.md` added advisory
  `validate` overloads and failure observation. Its contract explicitly says
  validation reserves no admission and may still be followed by shutdown
  rejection.
- `ai/thoughts/tickets/loomspan-pr-5.4-framework-documentation-and-readiness.md`
  supplied current README/knowledge-set coverage and the release-readiness
  record.
- `ai/thoughts/release-readiness/1.0.0-beta.4.md` contains the committed snapshot
  preparation and Sidecar evidence that predate the later cycle-3 review finding.
- `ai/thoughts/tickets/loomspan-pr-5.6-atomic-invocation-handoff.md` is the current
  controlling ticket and explicitly preserves the one-budget model, existing
  direct invocation/validation behavior, public compatibility, Sidecar SC5's
  separate disposition, and the no-public-trace-extension boundary.

## Related Research

No earlier durable document exists in `ai/thoughts/research/` for this topic at
the researched revision. The source-grounding and lifecycle history instead
reside in the tickets and phase documents listed above.

## Open Questions

The planning step must resolve these contract-shape questions from the ticket and
current evidence:

1. What exact supported Application API shape represents the single-use admitted
   invocation while preserving source and binary compatibility for existing
   `SkillTemplate` implementers and mocks? The current interface has only
   abstract methods, and the ticket allows the preferred handle idea without
   mandating its signature.
2. Which internal state transition atomically claims an admitted root for
   execution versus idempotent release, while making cutoff invalidate and
   remove still-pending admissions without allowing release to untrack an
   executing root?
3. At what exact public method boundary are skill name/input preparation and
   current-thread authentication captured? Current `validate` is advisory,
   current `invoke` captures authentication at execution time, and the Sidecar
   installs authentication only after releasing its gate.
4. Which existing internal trace seam will connect a real blocked
   `TRACE_COMPLETED` writer append to a framework lifecycle test without adding
   a public writer, sink, bean override, lifecycle event, or production test-only
   hook?
5. Which exact public-surface count/name assertions and consumer documentation
   require updates for the chosen type count, including root README, Java API
   knowledge set, release note/readiness wording, API tests, architecture tests,
   and Console's documentation-package checks?

