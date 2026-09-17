# PR 8 — Generation Retirement Notification Testing Plan

## Change Summary

Add `SkillReloader.onGenerationRetired(Consumer<String>)` and internal owner accounting so a published generation is reported only after supersession and final invocation ownership release, including physical work after cancellation. Tests must establish the normal-operation callback contract without imposing replay, cross-generation ordering, shutdown delivery, or application cleanup guarantees.

## Impacted Areas

- `SkillGenerationManager` and `DefaultSkillReloader`: atomic capture/publication, listener selection, registration removal, unused generations, rejected candidates, failure isolation.
- `DefaultSkillTemplate`, `DefaultSkillInvocationHandoff`, and `LoomspanSessionRunner`: pre-admission preparation, validation-only capture, direct invocation, pending admission, failure/claim cleanup.
- `FrameworkExecutionLifecycle.AdmittedRoot`, `MissionLifecycle`, and `StepLoopMissionExecutionEngine`: logical root lifetime versus nested, parallel, and cancelled physical work.
- `SkillReloader` Javadoc, `README.md`, bundled `java-api/skill-reload.md`, `skill-authoring/rest-skills.md`, `skill-authoring/mental-model.md`, and both knowledge-set indexes: supported application sequence and exact limitations.
- `LoomspanPublicSurfaceArchitectureTest`: closed API boundary. No Console/Go fixture, manifest, serialized format, or trace format changes.

## Risk Assessment

- **Early retirement**: publication races after capture but before conversion/admission; pending admission persists; a root has a quiet interval before a later REST child; a nested/parallel callable survives a timeout or cancellation; a future completes before its callable returns.
- **Lost or duplicate retirement**: fast A→B→C publications, zero-use B, listener registration/removal races, duplicate release paths, repeated publish, stale/foreign candidate.
- **Callback interference/deadlock**: consumer throws or calls back into `snapshot`, listener registration, or another publication; listener is slow; concurrent worker returns select different generations. Delivery must happen outside locks and must not change invocation/publication results.
- **Shutdown**: close/cutoff must not generate a false safety signal or add waiting. Delivery after shutdown is optional, so tests should assert only safety and existing budget, not its occurrence.
- **Protected paths**: `SkillReloader` is the application API; `RestSkillHandler` is the sole supported SPI, `RestSkillInvocation.generationId()` keeps trusted generation selection, and existing skill invocation/authorization behavior remains. Internal signatures may change without compatibility tests. No obsolete public path is being removed.
- **Authoring claims needing executable evidence**: initial registration/staging order, fixed handler routing, old generation availability for admitted/nested work, safe retirement after physical return, and limits of shutdown delivery. Existing guide claims denying a retirement API are documentation drift relative to the intended new behavior; remove them in the same change.

## Existing Test Coverage

- `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java` covers two-stage publication, ID freshness, owner/base/one-shot rejection, concurrent preparation/publication, and shutdown during preparation, but no retirement.
- `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java` covers supplied/configured YAML, fixed generation-keyed REST handler, old pending handoff after publication, and startup snapshot immutability, but not safe cleanup.
- `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java` covers capture before object conversion, old handoff execution, and new-generation routing. `DefaultSkillTemplateTest` covers handoff release/cutoff and authentication.
- `src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java` covers pending release, shutdown cutoff/budget, root claim races, and uncooperative work. `src/test/java/ai/loomspan/internal/runtime/step/ConcurrentGroupedExecutionIntegrationTest.java` covers nested/parallel and timeout paths. None asserts generation retirement.
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` enforces the exact supported API allowlist and rejects internal type leakage.

## Bug Reproduction / Failing Test First

- **Type**: integration.
- **Location**: `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`.
- **Name**: `retirementWaitsForPendingOldGenerationThenCleansUpAfterRelease`.
- **Arrange**: In an `ApplicationContextRunner` with one fixed `RestSkillHandler`, register a listener before staging the initial generation; stage A by ID; create an A handoff; prepare/stage/publish B. Use a thread-safe list/latch for callbacks, not sleeps.
- **Act/Assert**: B is active, but A has not been delivered while the handoff is pending. Call `release()` on the handoff. Assert A is delivered exactly once and B's resource remains. The current checkout fails to compile at `onGenerationRetired`, establishing the missing API and lifecycle behavior; once the API exists but accounting is absent, the same assertion detects early or missing delivery.

## Tests to Add/Update

### 1) `publishingUnusedGenerationsRetiresEachSupersededPublishedId`

- **Type**: unit; **Location**: `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java`.
- **What it proves**: Listener registered before initial staging sees A after B publication and B after C publication exactly once, including zero-use B; C remains active; no callback occurs on prepare alone. Callback may run before `publish` returns, so assert after return without imposing timing/order beyond the explicit publication sequence.
- **Fixtures/data**: Existing empty catalog factory and fresh candidate IDs.
- **Mocks**: Existing mocked bean factory/lifecycle executor; no mocked listener needed.
- **Affected surface**: Application API.
- **Compatibility expectation**: New supported path; no replay or cross-generation ordering promise.

### 2) `rejectedCandidatesNeverRetireAndRepeatedRejectDoesNotInvalidatePublishedId`

- **Type**: unit; **Location**: `SkillReloaderTest.java`.
- **What it proves**: Stale/foreign/never-published candidate IDs produce no notification; a repeated publish rejection for a previously published B does not make B disposable; B retires only when C supersedes and owners release.
- **Fixtures/data**: Existing candidate ownership/base fixtures.
- **Mocks**: None beyond current setup.
- **Affected surface**: Application API/internal publication.
- **Compatibility expectation**: Preserve rejection behavior and the ticket's unused-candidate distinction.

### 3) `listenerFailureAndCloseDoNotChangePublicationOrLaterDelivery`

- **Type**: unit; **Location**: `SkillReloaderTest.java`.
- **What it proves**: A throwing listener is logged/isolated; another listener still receives the same ID, publication succeeds, and later retirements still notify. A closed registration is absent from *future selection*; use latches to hold a selected callback, close its handle, then release the latch and show closure neither waits nor cancels that selected callback. A listener may call `snapshot()` or register/close without deadlock. Separate test or assertion can show different retirements may dispatch concurrently.
- **Fixtures/data**: CountDownLatch and thread-safe collections; bounded joins only.
- **Mocks**: Optional log capture for failure visibility; no strict log wording.
- **Affected surface**: Application API and ephemeral diagnostics.
- **Compatibility expectation**: New delivery contract/current-run failure visibility.

### 4) `captureBeforeAdmissionPreventsEarlyRetirement`

- **Type**: integration; **Location**: `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java`.
- **What it proves**: Block object-to-map conversion after A is captured, publish B, assert A remains owned, then release conversion and run the A invocation; A retires only after the invocation and any physical work return. Also cover conversion throwing and invalid/null input so failed preparation releases ownership.
- **Fixtures/data**: Existing blocking object-conversion fixture/latches, or a narrow converter hook; no timing sleeps.
- **Mocks**: Existing test-local mapper/fake only.
- **Affected surface**: Application API execution and internal capture.
- **Compatibility expectation**: Preserve captured generation and authorization semantics.

### 5) `pendingAdmissionRetainsAndReleasesCapturedGeneration`

- **Type**: integration; **Location**: `PublicSkillReloadIntegrationTest.java` and/or `DefaultSkillTemplateTest.java`.
- **What it proves**: Pending A handoff suppresses retirement after B publication; `release()` triggers A once; alternatively `invoke()` routes to A and retires only on actual completion; rejected claim/cutoff frees pending ownership without allowing execution. Dropping the handle is deliberately untested because it promises no reclamation.
- **Fixtures/data**: One fixed REST handler keyed by ID, latches around handler execution.
- **Mocks**: Public integration uses real context; lower-level cutoff test may use existing mocks.
- **Affected surface**: Application API and internal admission.
- **Compatibility expectation**: Preserve handoff single-use, old-generation execution, and explicit release.

### 6) `nestedAndParallelDescendantsKeepGenerationUntilPhysicalReturn`

- **Type**: integration; **Location**: `src/test/java/ai/loomspan/internal/runtime/step/ConcurrentGroupedExecutionIntegrationTest.java` (extend existing controlled model/REST fixtures).
- **What it proves**: A root captured from A can invoke a later REST child after B publication even following a quiet interval; parallel branches all use A. A notification remains absent until every started branch returns, then occurs exactly once. Stage A/B resources and assert handler lookup by `RestSkillInvocation.generationId()`, not input.
- **Fixtures/data**: Existing grouped-plan model responses, two latch-controlled REST or Java children, fixed generation-keyed handler.
- **Mocks**: Existing model interaction test double; use real framework lifecycle.
- **Affected surface**: Supported SPI + Application API execution/internal lifecycle.
- **Compatibility expectation**: Preserve captured child routing and authorization; new retirement signal only after safe return.

### 7) `failedRootAndPreAdmissionFailuresReleaseGenerationOwnership`

- **Type**: unit/integration; **Location**: `DefaultSkillTemplateTest.java` and `SkillGenerationExecutionIntegrationTest.java`.
- **What it proves**: Invalid input, object conversion failure, authentication lookup failure, rejected direct admission, a throwing REST handler, and observer/completion failure all relinquish their capture or admitted-root ownership after actual work returns. B publication then leads to exactly one A callback. Keep each failure's existing public exception shape and authorization result.
- **Fixtures/data**: Existing validation/authentication and fixed REST fixtures; add controlled exceptions rather than broad mocking of lifecycle internals.
- **Mocks**: Current test-local mapper/security mocks where already used.
- **Affected surface**: Application API and internal capture/admission.
- **Compatibility expectation**: Preserve failure mapping, access control, and current observer behavior while adding correct release.

### 8) `timeoutAndCancellationWaitForUncooperativePhysicalReturn`

- **Type**: integration; **Location**: `ConcurrentGroupedExecutionIntegrationTest.java` plus focused root/mission unit assertion in `FrameworkExecutionLifecycleTest.java` if needed.
- **What it proves**: A started child ignores interruption after cancellation/deadline and can still hold A resources. Facade future/mission cleanup and root logical completion do not emit A retirement. Releasing the child latch lets its callable `finally` record physical return and only then delivers A. A queued child that never starts and is fenced by cutoff does not hold ownership forever. Existing mission cleanup grace and shutdown budget remain unchanged.
- **Fixtures/data**: Bounded latch-controlled uncooperative callable and existing short timeout configuration; assert state transitions via latches, not arbitrary sleeps.
- **Mocks**: Existing local model/handler doubles.
- **Affected surface**: Internal lifecycle plus Application API signal.
- **Compatibility expectation**: Current physical-return authority; no future-is-done shortcut.

### 9) `shutdownDoesNotInventRetirementOrWaitForCallback`

- **Type**: unit/integration; **Location**: `FrameworkExecutionLifecycleTest.java` and one reloader integration assertion.
- **What it proves**: Closing admission/cutoff while A remains active or while A's physical callable is outstanding does not report false retirement. A callback already selected and blocked does not delay `stop` or consume additional shutdown budget. No assertion requires a callback after shutdown begins. Include clean completion before shutdown as the positive control.
- **Fixtures/data**: Existing fake clock/executor and latches.
- **Mocks**: Existing executor/lifecycle mocks.
- **Affected surface**: Application API shutdown semantics/internal lifecycle.
- **Compatibility expectation**: Preserve documented `loomspan.shutdown.timeout` behavior; shutdown delivery is optional.

### 10) `publicSurfaceContainsOnlySkillReloaderMethodAndExistingRestSpi`

- **Type**: architecture; **Location**: `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`.
- **What it proves**: `SkillReloader` remains allowlisted, no new top-level API or SPI appears, callback signature contains only standard Java types, and no `internal`/`autoconfigure` type leaks. Existing architecture test may suffice without new assertions if it already checks these conditions; inspect before adding redundant tests.
- **Fixtures/data**: Classpath inspection already present.
- **Mocks**: None.
- **Affected surface**: Application API + Supported SPI.
- **Compatibility expectation**: Deliberate closed public surface.

## How to Run

Run from repository root on Windows, using the repo wrapper:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillReloaderTest,PublicSkillReloadIntegrationTest,SkillGenerationExecutionIntegrationTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,LoomspanPublicSurfaceArchitectureTest test
.\mvnw.cmd --batch-mode --no-transfer-progress test
git diff --check
```

No external service, account, profile, or environment variable is required for these tests; existing integration fixtures provide a local protocol-compatible endpoint or test doubles. If the selected test list exceeds the actual edited scenario locations, keep it as the focused verification set because each class protects a different ownership boundary. The full Maven `test` run checks cross-component regressions. Do not substitute `Future.isDone()` or time-based sleeps for physical-return assertions.

## Exit Criteria

- [ ] The red public integration test fails pre-fix because the API/behavior is absent, then passes post-fix.

The pre-fix red run was not recorded during Step 4. The checked-out pre-implementation `SkillReloader` lacked the method (recorded in the research artifact), and the new public integration test passes after implementation; the unchecked item preserves that test-first verification gap.
- [x] Every acceptance criterion has an executable assertion: capture race, pending admission, nested/parallel work, failed paths, unused and rapid publications, uncooperative cancellation/deadline, consumer failure/removal, shutdown, supported-only example/API.
- [x] Focused tests and full Maven `test` pass; `git diff --check` is clean.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes after production changes; no accidental new SPI, replaceable bean contract, or leaked internal signature exists.
- [x] Public example and skill guides match tested registration/staging, callback, shutdown, and candidate-cleanup semantics; both coverage indexes are current. The old “no retirement API” statements are removed.
- [x] Protected fixed handler, RBAC, generation capture, handoff, and shutdown-budget tests remain green. No obsolete internal path is retained solely for compatibility.
- [x] Listener exceptions are visibly reported while publication/invocation outcomes and later notifications remain unaffected.
- [x] No false safe-retirement signal occurs during shutdown; notification delivery during/after shutdown is not a completion requirement.
- [x] Optional developer checks: none. The targeted behavior is automatable with the existing test fixtures and bounded latches.
