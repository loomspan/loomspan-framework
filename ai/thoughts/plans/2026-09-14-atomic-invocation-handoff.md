# Atomic Invocation Handoff Implementation Plan

## Overview

Add a narrow supported application API that lets an embedding application transfer one prepared root invocation to Loomspan while holding its own dispatch gate, then execute that invocation on the same application worker after releasing the gate. The implementation will reuse the existing `FrameworkExecutionLifecycle` root registry and single shutdown deadline, preserve all direct `SkillTemplate.invoke()` and `validate()` behavior, and add deterministic lifecycle evidence including a real blocked `TRACE_COMPLETED` write.

## Current State Analysis

`SkillTemplate` exposes only four synchronous `invoke` overloads and two advisory `validate` overloads; every method is abstract (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:11-25`). `DefaultSkillTemplate` prepares and validates input before capturing the current Spring Security `Authentication` and asking `LoomspanSessionRunner` to create a new root (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:90-200`). There is no supported token that separates framework admission from execution.

`FrameworkExecutionLifecycle.admitRoot()` and `closeAdmission()` serialize against the same monitor, but the returned internal root only has idempotent close and cutoff flags. It cannot distinguish a pending admission from an executing root, and cutoff does not remove an abandoned pending root (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:53-85`, `213-250`). `LoomspanSessionRunner.executeRoot()` always acquires its own root, holds it through trace completion and the completion callback, and releases it in `finally` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:215-266`).

The existing lifecycle tests prove caller-thread completion ownership and a bounded generic mission-write lock, but not a separately handed-off admission or a blocked canonical completion write (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:100-222`, `385-425`). The package-private `InternalExecutionTraceHandleFactory`, `DefaultExecutionTraceHandle` writer constructor, and `TraceRecordWriter` already provide internal/test-package seams for exact trace-write control without a supported trace extension.

The checked-in Java API guidance, tests, and production source are **aligned** for current direct invocation, validation, security, errors, and observation. The missing handoff material is new scope rather than drift. `AGENTS.md:11` says there is no supported Java SPI, while the allowlist, README, and Java API guidance identify `RestSkillHandler` as the sole supported SPI; this is **documentation drift** explicitly included in the ticket.

## Desired End State

Applications can inject a new `SkillInvocationHandoff` application facade. Its two `handoff` overloads accept the exact skill name and either an object or map input, perform the existing preparation/validation path, capture the current authentication, and atomically register a pending framework root before returning an `AdmittedSkillInvocation`. The handle exposes `invoke()`, `invoke(Consumer<SkillExecutionView>)`, and idempotent `release()`.

The public state contract is single-use: exactly one of execution, explicit release, or framework cutoff may claim a pending handle. A successful execution claim runs the prepared invocation against the already-admitted root and retains that root through finalization and observer delivery. Release after execution has claimed the handle is harmless and cannot untrack the executing root. Repeated or losing `invoke()` calls fail as `SkillException` without constructing another session. Cutoff releases pending reservations and makes them permanently non-executable; executing roots receive the existing deadline/cutoff behavior and release only from the runner's completion path.

Handoff performs no skill execution, session/trace creation, executor submission, or wait. Authentication is captured at the handoff boundary, so an application must establish its trusted security context before calling `handoff` while its gate is held; execution restores that captured identity through the existing session/worker security machinery and restores the worker's prior context. Input preparation and root authorization retain their current division: input lookup/conversion/validation occurs before admission; authorization is re-enforced during execution against the captured identity and cannot be bypassed by admission. Mission timeouts begin only when the ordinary mission work is submitted; a framework shutdown deadline may already be running while a handle remains pending.

### Key Discoveries:

- The existing lifecycle already owns the required admission/close serialization and shared deadline, so the feature needs a pending/claimed state transition rather than another queue, executor, or shutdown owner (`FrameworkExecutionLifecycle.java:53-85`, `101-179`).
- A separate facade preserves source and binary compatibility for custom `SkillTemplate` implementations and hand-written mocks; adding another abstract `SkillTemplate` method would repeat the compatibility-sensitive change documented for `validate` (`docs/releases/1.0.0-beta.4.md:23-29`).
- The runner completion callback is after execution-binding restoration but before root release, which is the existing seam that keeps public observer delivery under ownership (`LoomspanSessionRunner.java:243-266`; `FrameworkExecutionLifecycleTest.java:100-159`).
- Mission timeout accounting starts at future submission/get rather than root admission (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-72`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:284-320`).

## What We're NOT Doing

- Changing, removing, or adding methods to `SkillTemplate`, or changing direct `invoke()`/`validate()` semantics.
- Adding a supported SPI, bean replacement point, caller-supplied executor, framework queue, listener priority dependency, grace period, or second shutdown deadline.
- Adding public cancellation, trace writer/sink, lifecycle events, or production test-only hooks.
- Changing manifests, `loomspan.*` configuration keys, canonical trace schemas, Console REST/SSE/acquisition/problem/NDJSON protocols, or compatibility markers.
- Installing a rebuilt snapshot, modifying the Sidecar repository, running its pipeline, or claiming its SC5 public-contract trace criterion complete.
- Promising physical termination of arbitrary uncooperative I/O or callbacks after cutoff.

## Skill-Authoring Documentation Impact

**Impact**: No impact

- **Rationale**: The feature changes application-owned dispatch-to-framework ownership before an existing registered entry skill runs. It does not change manifest syntax, skill identity, planner/tool visibility, inputs or outputs, nested execution semantics, author-visible authorization rules, traces, debugging semantics, limits, or skill testing guidance.
- **Documents to update**: `None` under `agent-skills/loomspan-docs/references/skill-authoring/`. Application-developer documents under `agent-skills/loomspan-docs/references/java-api/` are updated separately in Phase 3.
- **Supporting evidence**: `SkillTemplate.java`, `DefaultSkillTemplate.java`, `CapabilityExecutionRouter.java`, and the focused handoff tests planned below establish that execution continues through the existing validated capability path. The skill-authoring routing and coverage inventory in `agent-skills/loomspan-docs/references/skill-authoring/README.md` has no application dispatch/handoff topic.
- **Coverage table update**: Not required because no skill-authoring topic boundary, coverage level, or author-facing claim changes.
- **LLM-first usability**: Not applicable to the skill-authoring knowledge set. The separate Java API guidance will route the handoff contract from its Java API index and keep the gate example, ownership states, authentication timing, timeout timing, and limitations self-contained.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add `SkillInvocationHandoff` and `AdmittedSkillInvocation` to the closed `ai.loomspan.api` surface. Existing thirteen types and all `SkillTemplate` signatures remain unchanged (`LoomspanPublicSurfaceArchitectureTest.java:29-42`). | Additive supported API; document and allowlist exactly the two new types. Preserve existing facade implementations/mocks by using a separate bean rather than changing `SkillTemplate`. |
| Supported SPI | No new implementation/replacement point. `RestSkillHandler` remains the sole supported SPI. | Preserve. Rename the misleading architecture test if useful and reconcile `AGENTS.md`; do not make the new facade conditional or application-replaceable. |
| Configuration and manifest contracts | `loomspan.shutdown.timeout` remains the one positive framework budget; no new property or manifest field (`README.md:494-523`). | Preserve exactly. |
| Persisted or serialized contracts | Admissions are in-process, non-serializable handles and are never persisted. | No impact. |
| Ephemeral diagnostic formats | No schema change. Tests will exercise an actual current-version `TRACE_COMPLETED` append and existing observer projection under root ownership (`DefaultExecutionTraceHandle.java:366-405`). | Preserve writer/reader/projector coherence and security; use internal test-package seams only. |
| Internal or accidentally exposed implementation | Reshape `FrameworkExecutionLifecycle.AdmittedRoot`, add a pre-admitted runner path, and share facade preparation/execution internals. Auto-configuration method signatures/classes may change as internal wiring. | Atomic internal update with no shim and no retained duplicate lifecycle path. |

- **Evidence of supported contracts**: `LoomspanPublicSurfaceArchitectureTest`, README's closed API list, `agent-skills/loomspan-docs/references/java-api/`, `ApplicationApiValueTest`, and `SupportedSurfaceIntegrationTest`. The ticket explicitly requires the new admission contract to be supported Application API.
- **Intentional compatibility changes**: Add two supported API types and one injectable application facade. There is no break to an existing supported signature or behavior.
- **In-repository consumers to update**: auto-configuration, API/architecture tests, lifecycle and supported-surface integration tests, root README, `ai.loomspan.api` package Javadoc if it enumerates types, Java API knowledge-set index/invocation/compatibility/observation guidance, beta 4 release notes, Console documentation-package assertions, `AGENTS.md`, and the release-readiness record.
- **Public-surface delta**: exactly `ai.loomspan.api.SkillInvocationHandoff` and `ai.loomspan.api.AdmittedSkillInvocation`; no constructor, annotation, configuration type, or Spring extension point is added. The closed API count changes from thirteen to fifteen.
- **Shim decision**: **No shim.** Existing `SkillTemplate` is untouched, so there is no protected old contract requiring bridging. The new facade is additive; internal state and runner paths are updated atomically rather than retaining parallel authorities.
- **Java-to-Go boundary coordination**: **Not required.** No application-adapter REST/SSE, acquisition, problem, consumed NDJSON, trace schema, or compatibility-marker boundary changes. Console changes are documentation-package assertions only.
- **Pipeline notes alignment**: **No notes.** The ticket itself explicitly authorizes the additive supported API and AGENTS drift correction, while prohibiting broader extension or shutdown-model changes.

## Implementation Approach

Keep the ownership state in `FrameworkExecutionLifecycle`, the only component that can atomically arbitrate admission, close, release, execution claim, and cutoff. Model each root as `PENDING`, `EXECUTING`, or terminal (`RELEASED`/`CUT_OFF`) with transitions serialized by the lifecycle's existing monitor or an equivalently race-safe single authority. Under cutoff, detach all pending roots from `activeRoots` and invalidate their handles; signal executing roots through the existing mission cutoff path without removing them. Perform mission signaling outside any application gate and avoid lock-order inversion by not calling back into the lifecycle monitor while holding a per-root lock.

Refactor the runner so direct calls admit and claim one root immediately, while the new path receives and claims the already-admitted root before session construction. Both paths then use one common root-execution body and one completion `finally`; neither can acquire a second root. Keep input preparation and exception mapping shared with `DefaultSkillTemplate`. The handoff facade prepares input, captures authentication, admits the pending root last, and returns a handle bound to those immutable framework-owned references. Its `invoke` overloads claim once and call the common prepared-invocation path; `release` only transitions a still-pending handle.

## Phase 1: Public Contract and Atomic Lifecycle State

### Overview

Define the additive API and make the existing lifecycle correctly own pending, executing, released, and cut-off admissions.

### Changes Required:

#### 1. Supported handoff API
**Files**:
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillInvocationHandoff.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java`

**Changes**:
- Define two `handoff` overloads for `Object` and `Map<String, Object>` inputs.
- Define `invoke()`, `invoke(Consumer<SkillExecutionView>)`, and idempotent `release()` on the returned single-use handle.
- Document exact-name behavior, ownership transfer, captured authentication, authorization enforcement, single-use/losing-call failures, timeout timing, observer behavior, release obligations, and cutoff invalidation.
- Keep every public signature within `ai.loomspan.api` plus JDK types.

```java
public interface SkillInvocationHandoff {
    AdmittedSkillInvocation handoff(String skillName, Object input);
    AdmittedSkillInvocation handoff(String skillName, Map<String, Object> input);
}

public interface AdmittedSkillInvocation {
    String invoke();
    String invoke(Consumer<SkillExecutionView> observer);
    void release();
}
```

#### 2. Root ownership state machine
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java`

**Changes**:
- Add an atomic execution-claim operation distinct from pending release and execution completion.
- Make release idempotently remove only a pending root; it must be harmless once execution has won.
- On cutoff, atomically invalidate/remove pending roots, notify shutdown waiters, and signal executing roots without releasing them early.
- Preserve late deadline/cutoff propagation to missions and the original deadline calculation.
- Keep direct root admission possible through the same state machine and add package-private state/count access only where existing internal tests need it.

### Success Criteria:

#### Automated Verification:
- [x] Lifecycle state/race tests pass: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest' test`
- [x] Public signatures expose no internal/autoconfigure types: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest' test`

---

## Phase 2: Shared Direct and Handed-Off Execution

### Overview

Wire the new facade to the current preparation, session, execution, finalization, exception, observer, and security paths while guaranteeing exactly one root.

### Changes Required:

#### 1. Pre-admitted runner path
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java`

**Changes**:
- Extract the current post-admission execution body.
- Add an internal path that claims a supplied pending root and then constructs/runs the session; direct entry methods admit and immediately claim before entering the same body.
- Preserve rejection before session construction, completion callback ordering, original/suppressed failure behavior, binding restoration, trace/observation finalization, and root completion in `finally`.

#### 2. Handoff implementation and shared preparation
**Files**:
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java` (or an equivalent focused internal collaborator)
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`

**Changes**:
- Extract or share `PreparedInput`, safe exception mapping, execution routing, view mapping, and security-context strategy rather than duplicating facade behavior.
- At handoff, complete lookup/conversion/input validation, capture the current `Authentication`, then perform the atomic pending-root admission. If pre-admission preparation fails, no root exists; if admission loses to close, no session or work begins.
- Bind the existing validator's normalized input result and the captured authentication reference to the returned handle. Do not retain the caller's top-level mutable map or consult the execution thread's ambient authentication; preserve the existing treatment of supported nested runtime values such as resources rather than inventing new serialization semantics.
- Execute through the supplied admitted root and the same capability router. Preserve `AccessDeniedException`, `SkillException`, fatal `Error`, observer ordering, nested authorization, and worker context restoration.
- Register one infrastructure bean assignable to `SkillInvocationHandoff`; do not use `@ConditionalOnMissingBean` or expose a replacement contract.

#### 3. Deterministic facade and integration coverage
**Files**:
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`

**Changes**:
- Add exact success/rejection/release/already-consumed/cutoff and concurrent claim/release tests.
- Exercise authentication capture before the application gate is released, changed/empty worker context, authorization denial, restoration, exceptions, observers, and nested work through real public beans.
- Model an application dispatch gate/listener and cover handoff-before-close, framework-close-before-handoff, external-close-before-handoff, and both listener registration orders without relying on listener priority.

### Success Criteria:

#### Automated Verification:
- [x] Facade and supported consumer tests pass: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=DefaultSkillTemplateTest,SupportedSurfaceIntegrationTest' test`
- [x] Shutdown/listener race integration passes: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=FrameworkShutdownIntegrationTest' test`
- [x] Existing direct invocation and validation tests remain unchanged in meaning and pass.

---

## Phase 3: Real Finalization Evidence and Supported-Surface Documentation

### Overview

Prove lifecycle ownership across actual canonical finalization/observation and update every supported-surface description coherently.

### Changes Required:

#### 1. Block the actual canonical completion append
**Files**:
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/trace/BlockingTraceHandleTestSupport.java` (test-only helper, name may follow local convention)

**Changes**:
- Use a test class in the trace package to construct a real `DefaultExecutionTraceHandle` with a controllable package-private `TraceRecordWriter`; expose only test-source helper methods needed by the core lifecycle test.
- Block specifically when the writer receives `TraceRecordType.TRACE_COMPLETED`, assert the root remains active through the blocked append and subsequent observer delivery, and prove normal completion waits when released within budget.
- Add the cutoff schedule: let the shared budget expire while the actual append is blocked, assert lifecycle stop returns within that original budget with no additional wait, then release the writer and verify cleanup remains safe. Do not replace this with the generic mission guard test.

#### 2. Closed public surface and docs
**Files**:
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/package-info.java` (if its inventory requires update)
- `README.md`
- `agent-skills/loomspan-docs/references/java-api/README.md`
- `agent-skills/loomspan-docs/references/java-api/invocation.md`
- `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`
- `agent-skills/loomspan-docs/references/java-api/catalog-and-validation.md`
- `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`
- `docs/releases/1.0.0-beta.4.md`
- `loomspan-console/internal/buildtool/projectdeclarations_test.go`
- `AGENTS.md`

**Changes**:
- Change the exact public catalog from thirteen to fifteen and assert the new method shapes, single-use handle boundary, and absence of leaked internals/unintended SPI packages.
- Add a short standard-Java/Spring gate/handoff/execute/finally-release example using only supported types.
- State that authentication must be installed before handoff and is captured there, authorization still occurs during execution, mission timeout starts at work submission, the shutdown deadline may already be active, pending handles are invalidated at cutoff, and observer delivery remains within ownership.
- Preserve the advisory meaning of `validate` and direct invocation behavior.
- Correct `AGENTS.md` narrowly to recognize `RestSkillHandler` as the sole supported SPI while retaining the prohibition on any broader SPI or internal bean replacement contract.

### Success Criteria:

#### Automated Verification:
- [x] Actual trace-finalization lifecycle tests pass: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest' test`
- [x] Closed fifteen-type public surface and supported integration pass: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest,SupportedSurfaceIntegrationTest' test`
- [x] Console documentation-package assertions pass: `go -C loomspan-console test ./internal/buildtool`
- [x] No stale exact-count/API references remain: `rg -n "thirteen|13 types|SkillInvocationHandoff|AdmittedSkillInvocation|sole supported SPI|no supported Java SPI" README.md AGENTS.md docs/releases agent-skills/loomspan-docs/references loomspan-console/internal/buildtool`

---

## Phase 4: Full Verification and Release Evidence

### Overview

Run the repository's focused and full checks and update the existing readiness record without performing external release or Sidecar work.

### Changes Required:

#### 1. Release-readiness record
**File**: `ai/thoughts/release-readiness/1.0.0-beta.4.md`

**Changes**:
- Preserve all historical evidence and user-owned content.
- Add the implemented revision/worktree state and only commands actually run with exact results.
- Supersede the stale “no framework defect” disposition with the later-review-driven handoff remediation while keeping historical claims clearly dated.
- Record local snapshot reinstall and Sidecar adaptation/rerun as pending. Explicitly keep Sidecar SC5's public-contract blocked-trace criterion separate and unsatisfied by internal framework trace tests.
- Keep `1.0.0-beta.4-SNAPSHOT`; do not tag, install, publish, overwrite, or edit Sidecar.

### Success Criteria:

#### Automated Verification:
- [x] Focused suites pass: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ApplicationApiValueTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest' test`
- [x] Coordinated version remains correct: `python scripts/loomspan_version.py check`
- [x] Version-script tests pass: `python -m unittest discover -s scripts/tests -p "test_*.py"`
- [x] Full reactor passes: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify`
- [x] Nonpublishing release-profile packaging passes: `.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests '-Dgpg.skip=true' verify`
- [x] Worktree review confirms no unexpected or Sidecar changes: `git status --short` and `git diff --check`

## Testing Strategy

### Unit Tests:

- Public API shape and exact fifteen-type allowlist.
- Pending/executing/released/cut-off state transitions and deterministic losing races.
- Handoff preparation, authentication capture, single execution, idempotent release, exception mapping, observer behavior, and direct-facade regression behavior.
- Real `DefaultExecutionTraceHandle` `TRACE_COMPLETED` blocking under the lifecycle budget.

### Integration Tests:

- Real Spring beans and supported-only application consumption for Java/YAML/REST invocation, authorization, nested work, and observation.
- Application dispatch listener versus framework close in both listener orders and each winning schedule.
- Shutdown start while a successful admission is pending, followed by execution inside the remaining budget or invalidation at cutoff.

Full details, exact test names, fixtures, commands, and exit criteria are in `ai/thoughts/plans/2026-09-14-atomic-invocation-handoff-testing.md`.

## Performance Considerations

Handoff adds only input preparation/authentication capture and one lifecycle state insertion while the application gate is held. It must not submit work, construct sessions/traces, invoke observers, or wait. State transitions should remain constant-time and avoid new per-invocation executors, queues, polling, or callback fan-out. Tests should assert prompt listener return and bounded shutdown rather than brittle absolute throughput.

## Migration Notes

Existing `SkillTemplate` consumers, implementations, fakes, and mocks require no changes. Applications that need atomic ownership inject `SkillInvocationHandoff`, establish authentication before calling `handoff` inside their dispatch gate, release the gate, invoke on their existing worker, and call `release()` in every path that abandons the pending handle. Sidecar adoption and its repaired caller/client/observer tests occur in its own repository only after this framework snapshot is rebuilt and installed by an explicitly authorized step.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-5.6-atomic-invocation-handoff.md`
- Related research: `ai/thoughts/research/2026-09-14-atomic-invocation-handoff.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Existing lifecycle authority: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:53-250`
- Existing root execution path: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:215-266`
- Existing trace writer seam: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:137-155`, `366-405`
