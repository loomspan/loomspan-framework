# Framework Lifecycle Shutdown Testing Plan

## Change Summary

- Add strict `loomspan.shutdown.timeout` configuration with a positive `30s` default.
- Introduce one internal framework lifecycle owner that atomically closes root admission, retains admitted roots through session finalization and caller-side success observation, starts one monotonic deadline, and coordinates bounded mission/executor cutoff.
- Associate every descendant mission and owning/task future with its admitted root so concurrent registration observes framework cutoff.
- Make framework cutoff lock-free with respect to mission ancestry/trace locks, preserve local mission timeout behavior, and add direct owning-future `CancellationException` parity with the step-loop path.
- Replace blocking/inferred executor close with lifecycle-managed cleanup and an idempotent, non-waiting destruction fallback.
- Preserve the supported Java API and success-observer behavior while intentionally rejecting new top-level invocations after shutdown begins.

## Impacted Areas

- Root/session entry and observation: `LoomspanSessionRunner`, `LoomspanSession`, `DefaultSkillTemplate`, `SkillExecutionViewMapper`.
- Mission creation, admission, cancellation, cleanup, and writes: `ExecutionCoordinator`, `MissionContext`, `MissionLifecycle`, `MissionWorkExecutor`, `StepLoopMissionExecutionEngine`, `DefaultExecutionStateService`.
- Spring ownership: new internal framework lifecycle owner, `LoomspanAutoConfiguration`, the `LoomspanMissionExecutor` bean, event multicasting, lifecycle phases/dependencies, and destruction.
- Configuration and docs: `LoomspanProperties`, generated/additional configuration metadata, and `README.md`.
- Boundary enforcement: `LoomspanPublicSurfaceArchitectureTest`, `LoomspanAutoConfigurationBoundaryTest`, and auto-configuration context tests.
- Existing focused regression suites: `LoomspanSessionRunnerTest`, `DefaultSkillTemplateTest`, `MissionLifecycleTest`, `JavaSkillMissionCutoffTest`, and `StepLoopMissionExecutionEngineTest`.

## Risk Assessment

- **Highest risk — deadline linearization**: admission, close-event delivery, root release, mission registration, future registration, cutoff, executor termination, repeated close, and destruction must share one ordering and never create a new deadline.
- **Highest risk — lock contention**: the shutdown waiter must not acquire mission ancestry locks or run failure/trace callbacks; otherwise a blocked trace writer can still defeat the configured bound.
- **Highest risk — lifetime gap**: mapping and success observers run after binding restoration today. Releasing the root at runner return would let Spring tear down resources while caller-side completion is still active.
- **High risk — exception compatibility**: success-observer and mapping runtime exceptions must remain unwrapped and run on the application caller, while construction/execution/finalization failures retain current facade wrapping and cleanup precedence.
- **High risk — cancellation parity**: `Future.cancel(true)` makes `get()` throw `CancellationException`; the direct path currently has no catch and could bypass primary failure/cleanup. The step-loop path must not regress its existing behavior.
- **High risk — resource order**: event listeners must return promptly, the later lifecycle phase must retain the executor and other work resources, and Spring's shorter phase timeout must not replace the framework deadline.
- **Race edges**: mission or future registered as cutoff is published; root finishes as deadline expires; writer enters before cutoff then resumes after; observer fails or blocks; session/trace construction fails; event is filtered, repeated, skipped, or interrupted by another listener; failed startup reaches destruction only.
- **Protected compatibility paths**: the eight-type `ai.loomspan.api` allowlist and signatures, validation before session admission, caller-thread/binding-restored success observation, unchanged observer exception identity, ordinary mission timeouts, canonical current-run trace coherence, and no SPI/bean override seam.
- **Intentional change/removal**: post-close top-level admission is removed with no fallback or shim; duplicate runner implementation and blocking executor `close()` destruction must be absent, not retained beside new behavior. Internal constructor/bean-method tests should be updated atomically rather than requiring both decompositions.
- **Skill-authoring claims**: none. The implementation plan classifies the change as application operations rather than skill authoring, so no `agent-skills/loomspan-docs/references/skill-authoring/` test/doc evidence is required. Installed-skill version mismatch remains disclosed and is not used for shutdown assertions.

## Existing Test Coverage

- `LoomspanSessionRunnerTest` covers successful/failing standalone finalization, construction failure after observation registration, failure-recording failure precedence, optional-observation isolation, concurrency, and binding cleanup (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java:55-144`, `:343-476`). It does not count framework root admission/release or cover completion after binding restoration.
- `DefaultSkillTemplateTest#observerExceptionPropagatesAfterExecutionCompletes` protects unwrapped success-observer failure; adjacent tests protect observer suppression for invalid input and ordinary successful delivery (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:203-283`). There is no blocked mapping/observer ownership proof.
- `MissionLifecycleTest` covers monotonic local close, first-cancellation ownership, all-or-none ancestry admission, late future cancellation, physical return, cleanup ordering, and the current fact that `closeNow` waits behind an admitted writer (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/MissionLifecycleTest.java:24-282`). It has no framework-root signal or shared-deadline test.
- `JavaSkillMissionCutoffTest` protects timeout/interruption fences against uncooperative direct Java work (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/JavaSkillMissionCutoffTest.java:25-107`). It does not exercise externally cancelled owning futures.
- `StepLoopMissionExecutionEngineTest` already covers caller interruption, timeout cleanup, late writes, grouped cutoff, and a `CancellationException` branch. It lacks a framework-root cutoff racing mission/task registration.
- `LoomspanSessionPropertiesTest` and `ConfigurationMetadataTest` demonstrate the binding/default/startup-failure/metadata patterns for `loomspan.session.mission-timeout`, but there is no shutdown group.
- `LoomspanAutoConfigurationBoundaryTest` maintains the exact framework bean method sets and empty replacement allowlist. `LoomspanPublicSurfaceArchitectureTest` protects the closed eight-type API and rejects an SPI.
- No production `ContextClosedEvent`, `ApplicationListener`, or `SmartLifecycle` implementation and no framework shutdown fixture exist, so listener independence, context filtering, Spring timeout overlap, resource lifetime, failed startup, and destruction fallback are untested.

## Bug Reproduction / Failing Test First

- **Name**: `closeEventRejectsLateRootBeforeSessionConstruction`
- **Type**: integration
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- **Arrange**: Start a minimal Loomspan application context with a deterministic Java root and a trace/session-construction probe. Publish the owning context's `ContextClosedEvent` directly (without yet invoking lifecycle stop), wait only for synchronous event return, then invoke the root through `SkillTemplate`.
- **Act**: Call `SkillTemplate.invoke` after the close event's gate-closing linearization point.
- **Assert**: The invocation fails as shutdown rejection, the Java skill body is never called, and the trace/session-construction probe remains unchanged. Event publication returns promptly and no lifecycle wait, mapper, observer, or trace callback runs on the publisher thread.
- **Expected failure (pre-fix)**: The current repository has no close-event listener or root admission gate; the invocation constructs a session and executes successfully, so the no-construction/no-execution assertions fail deterministically. This is preferable to beginning with a long deadline timing test because it isolates the missing authoritative admission boundary.

After this red test, add the lower-level lifecycle-owner unit tests before implementing the broader deadline/cutoff behavior. Keep the integration test red until the actual event-to-admission wiring is complete.

## Tests to Add/Update

### 1) Shutdown Configuration Contract

- **Names**: `shutdownTimeoutDefaultsToThirtySeconds`, `shutdownTimeoutBindsPositiveDuration`, `zeroShutdownTimeoutFailsStartup`, `negativeShutdownTimeoutFailsStartup`, `shutdownTimeoutAppearsInGeneratedMetadata`
- **Type**: unit/context metadata
- **Locations**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionPropertiesTest.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/ConfigurationMetadataTest.java`
- **What it proves**: `loomspan.shutdown.timeout` is exactly `30s` by default, accepts Spring duration syntax such as `125ms`/`45s`, and rejects null/zero/negative values during binding/startup with a property-specific diagnostic. Metadata exposes the same name/type/default/description.
- **Fixtures/data**: `ApplicationContextRunner` property values; generated `spring-configuration-metadata.json`.
- **Mocks**: None.
- **Affected surface**: Configuration behavior.
- **Compatibility expectation**: New protected configuration contract; no alias or old property path.

### 2) Atomic Root Admission, Deadline, and Release

- **Names**: `closeAndAdmissionHaveOneAtomicOrdering`, `admittedRootRemainsTrackedUntilExactlyOneRelease`, `repeatedCloseReusesFirstMonotonicDeadline`, `rootReleaseRacingDeadlineSignalsWaiterOnce`, `destroyBeforeStartClosesAdmissionAndDoesNotWait`
- **Type**: unit/concurrency
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- **What it proves**: Each root is either admitted before closure or rejected; no lease is lost/double-counted; the first close owns the sole saturating monotonic deadline; close/event/stop/destroy repetitions do not renew it; destruction is non-waiting even without normal startup/event delivery.
- **Fixtures/data**: Injected `LongSupplier`, controllable `ExecutorService`, `CountDownLatch`, `CyclicBarrier`, and counters. Run a repeated race loop with a bounded JUnit timeout, asserting invariants rather than thread winner.
- **Mocks**: Minimal executor/context stubs only; prefer real futures and latches.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: New internal authority; obsolete no-gate behavior intentionally removed.

### 3) Listener Promptness, Async Opt-Out, and Context Ownership

- **Names**: `owningCloseEventClosesAdmissionWithoutWaiting`, `listenerOptsOutOfAsyncExecution`, `foreignAndChildContextEventsDoNotCloseOwner`, `repeatedOwningEventIsIdempotent`
- **Type**: unit/integration
- **Locations**: `FrameworkExecutionLifecycleTest.java`; `FrameworkShutdownIntegrationTest.java`
- **What it proves**: Exact owning-context filtering, `supportsAsyncExecution() == false`, immediate admission closure/deadline start, prompt event return while a root remains blocked, and idempotence. Parent/child contexts do not accidentally close one another's owner.
- **Fixtures/data**: Parent and child `AnnotationConfigApplicationContext`; standard `SimpleApplicationEventMulticaster` backed by a named executor; blocked admitted root.
- **Mocks**: None for Spring event dispatch; use a fake root body only.
- **Affected surface**: Internal implementation / configuration behavior.
- **Compatibility expectation**: Ticket-required lifecycle behavior; no listener-priority contract.

### 4) Independent Host-Gate Listener Orders

- **Names**: `hostGateBeforeFrameworkListenerIsIndependent`, `frameworkListenerBeforeHostGateIsIndependent`, `throwingEarlierListenerStillGetsNonwaitingDestructionFallback`
- **Type**: integration
- **Location**: `FrameworkShutdownIntegrationTest.java`
- **What it proves**: A test-only host gate and the framework listener each close their own admission promptly, neither calls/waits on the other, and behavior is correct in both relative orders. If an earlier listener prevents framework event delivery, later lifecycle/destruction still closes framework admission, cuts off, and returns within the original/newly established single fallback deadline rules without blocking destruction.
- **Fixtures/data**: Ordered test listeners used only to select both permutations; listener call logs; blocked root; throwing-listener variant.
- **Mocks**: Test-only independent gate, not a Sidecar type.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Framework proof only; actual Sidecar resource/listener wiring remains out of scope.

### 5) One Root Lease Through Runner Completion

- **Names**: `runAndCallShareOneAdmissionReleaseOperation`, `constructionFailureReleasesRootOnce`, `actionFailureReleasesRootOnceAfterFinalization`, `finalizationFailureReleasesRootOnce`, `completionRunsAfterBindingRestorationBeforeRelease`, `completionFailureReleasesRootOnce`
- **Type**: unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java`
- **What it proves**: Both existing runner entry shapes delegate to one implementation; admission precedes session/observation/trace construction; every terminal path releases exactly once; session finalization precedes completion; the completion callback sees no leaked current binding and remains owned until return/failure.
- **Fixtures/data**: Recording lifecycle owner/root lease; existing failing trace/observation handle factories; latches for blocked completion; exact call-order list.
- **Mocks**: Existing mock `ExecutionTraceHandle` patterns and a narrow recording lifecycle fake.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Internal overload decomposition may change; current action/finalization failure precedence remains protected by behavior tests, not by constructor compatibility.

### 6) Facade Mapping and Success Observer Compatibility

- **Names**: `blockedViewMappingRemainsOwnedAndRunsOnCaller`, `blockedObserverRemainsOwnedAndRunsOnCaller`, `observerExceptionIdentityRemainsUnchangedAndReleasesOwnership`, `mappingFailureRemainsOutsideExecutionWrapping`, `invalidInputStillDoesNotAdmitOrObserve`
- **Type**: unit/concurrency
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves**: Mapping and observer delivery run after binding restoration on the invoking thread, remain within the root lease, are never invoked by shutdown/lifecycle threads, and release ownership in `finally`. Observer and mapper runtime exceptions are the same instances and are not wrapped in `SkillException`; invalid input remains a pre-admission failure.
- **Fixtures/data**: Blocking/custom application-conversion `ObjectMapper`, observer latches, thread identity/reference capture, recording root count.
- **Mocks**: Existing Mockito `CapabilityExecutionRouter`; real mapper where practical.
- **Affected surface**: Application API / internal implementation.
- **Compatibility expectation**: Protected success-observer and validation behavior; no failure-observer expansion.

### 7) Concurrent Mission and Future Registration at Framework Cutoff

- **Names**: `missionRegisteredAfterRootCutoffIsImmediatelyFenced`, `owningFutureRegisteredAfterCutoffIsImmediatelyCancelled`, `taskFutureRegisteredAfterCutoffIsImmediatelyCancelled`, `missionRegistrationRacingCutoffHasNoOpenOutcome`, `cleanupGraceIsClampedToSharedDeadline`, `localCancellationBeforeFrameworkCutoffRetainsFirstCause`
- **Type**: unit/concurrency
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/MissionLifecycleTest.java`
- **What it proves**: Root-local mission registration and all future registration use add-then-recheck semantics; no race leaves a mission open; effective new-work/write fences include root cutoff; `min(local 250ms, shared deadline)` is used; pre-existing local primary cancellation is not replaced incorrectly.
- **Fixtures/data**: Root lease/session-aware `MissionContext` factory, `FutureTask`, barriers, injected nanos, direct parent/child bindings.
- **Mocks**: None.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Preserve ordinary local lifecycle semantics while adding the required root cutoff.

### 8) Blocked Trace Writer Cannot Hold Shutdown

- **Names**: `frameworkCutoffDoesNotWaitForAncestryLockHeldByTraceWriter`, `resumedWriterCannotRestoreSuccessCreditOrAdmitNestedWork`, `frameworkCutoffDoesNotRunFailureRecorderOnWaiter`
- **Type**: unit/integration concurrency
- **Locations**: `MissionLifecycleTest.java`; `JavaSkillMissionCutoffTest.java`; optionally a focused state-service fixture if needed.
- **What it proves**: A writer admitted under `runIfWritable` and blocked inside the trace recorder may retain the ancestry lock, yet the framework owner reaches cutoff and returns by the shared deadline. The shutdown thread never calls `ExecutionStateService.recordFailure`. After unblocking, subsequent `recordSuccessfulSkill`, mission creation/new-work admission, and trace/state writes remain revoked.
- **Fixtures/data**: Blocking `ExecutionTraceHandle`/trace recorder, latches for lock acquisition and release, thread capture, a nanos deadline with bounded wall-clock watchdog only to detect hangs.
- **Mocks**: Mock/recording trace handle to create the exact contention; real `MissionLifecycle` and bindings.
- **Affected surface**: Ephemeral diagnostics / internal implementation.
- **Compatibility expectation**: Current-run diagnostic coherence and late-write fencing; no historical fixture requirement.

### 9) Direct Owning-Future Framework Cancellation

- **Names**: `frameworkCutoffCancelsDirectOwningFutureAndPropagatesInstalledPrimary`, `directCancellationPerformsCleanupOnce`, `uncooperativeDirectWorkerDoesNotDelayLifecycleOrExecutorDestruction`, `ordinaryDirectMissionTimeoutStillUsesItsOwnPrimaryAndGrace`
- **Type**: integration/concurrency
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/JavaSkillMissionCutoffTest.java`
- **What it proves**: The new `MissionWorkExecutor` `CancellationException` branch retrieves the previously installed framework primary, performs cutoff-authorized frame cleanup once, and propagates that cause. An interrupt-ignoring worker cannot hold lifecycle stop or explicit nonblocking destruction; current timeout/interruption behavior remains intact.
- **Fixtures/data**: Existing uncooperative Java work fixture, controlled executor, framework root lease/cutoff, failure and trace-record captures.
- **Mocks**: Existing mocked YAML catalog/model paths; real executor/futures.
- **Affected surface**: Internal implementation / ephemeral diagnostics.
- **Compatibility expectation**: Preserve existing mission timeout and trace cleanup; add framework cutoff parity.

### 10) Step-Loop Framework Cancellation and Grouped Work

- **Names**: `frameworkCutoffCancelsStepLoopOwningFutureAndPropagatesInstalledPrimary`, `groupedFutureRegisteredDuringCutoffIsCancelled`, `frameworkCutoffStopsLaterUnitsAndFinalSynthesis`, `stepLoopCleanupUsesSharedDeadlineAndRunsOnce`, `ordinaryConcurrentFailureStillFoldsSiblingsBeforePropagating`
- **Type**: unit/integration concurrency
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`
- **What it proves**: Existing `CancellationException` cleanup works with the new framework-installed primary; grouped task registration cannot escape cutoff; later work and synthesis remain fenced; deadline clamping does not change ordinary ordered failure folding. If `executeToolAction` is edited, update/remove only the redundant exception-type branch and keep its observable propagation test.
- **Fixtures/data**: Existing planning/model fakes, controlled grouped executor, barriers at submission/start/publication, root cutoff with injected nanos.
- **Mocks**: Existing test fakes preferred over new broad mocks.
- **Affected surface**: Internal implementation / ephemeral diagnostics.
- **Compatibility expectation**: Preserve ordinary step-loop failure/diagnostic semantics while intentionally adding framework cutoff.

### 11) One Budget Across Lifecycle Wait and Executor Cleanup

- **Names**: `cleanRootsDrainAndExecutorTerminatesWithinRemainingBudget`, `deadlineCutsOffRootsAndCallsShutdownNowWithoutSecondWait`, `springPhaseTimeoutShorterThanFrameworkBudgetDoesNotTruncateWait`, `resourcesRemainRunningUntilRootCompletesOrCutsOff`, `observerTimeConsumesTheSameDeadline`
- **Type**: Spring integration
- **Location**: `FrameworkShutdownIntegrationTest.java`
- **What it proves**: Close starts one absolute deadline; root execution, nested work, trace finalization, mapping/observer, cutoff, graceful executor termination, and forced executor cleanup consume that deadline rather than stage-local timeouts. `SmartLifecycle.stop(Runnable)` honors the remaining framework time even when Spring's phase timeout is shorter. Required test resources stop only after framework completion/cutoff.
- **Fixtures/data**: Configured short durations, blocked/nested root, blocked observer, recording `SmartLifecycle` resources in adjacent phases/dependency order, instrumented executor. Use generous outer `@Timeout` and event/order assertions; timing tolerance is only for bounded scheduling overhead, never the primary semantic assertion.
- **Mocks**: Real Spring lifecycle processor and multicaster; recording resources/executor wrappers.
- **Affected surface**: Configuration behavior / internal implementation.
- **Compatibility expectation**: New protected shutdown contract; no second timer or Sidecar ordering convention.

### 12) Repeated Close, Failed Startup, and Destruction Fallback

- **Names**: `repeatedContextCloseDoesNotRenewDeadlineOrDoubleCancel`, `failedStartupUsesNonwaitingExecutorFallback`, `skippedCloseEventStillClosesAdmissionDuringStop`, `failedCloseListenerStillGetsDestructionFallback`, `executorBeanNeverUsesBlockingClose`
- **Type**: Spring integration/architecture
- **Locations**: `FrameworkShutdownIntegrationTest.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`; `LoomspanAutoConfigurationBoundaryTest.java`
- **What it proves**: All teardown paths are idempotent and bounded; an application startup failure after executor/owner creation cannot leak or block; lifecycle stop can establish the first deadline if event delivery was skipped; destruction never waits or invokes arbitrary work; bean metadata has no inferred/explicit `close` destroy method.
- **Fixtures/data**: Failing startup bean, manually invoked lifecycle stop/destroy, throwing listener, recording executor, bean-definition inspection.
- **Mocks**: Minimal executor spy for `shutdown`, `awaitTermination`, `shutdownNow`, and forbidden `close` observation.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Intentional removal of blocking destruction; no parallel legacy cleanup.

### 13) Auto-Configuration and Supported-Surface Boundaries

- **Names**: update exact bean-method set; `supportedApiSurfaceRemainsClosed`, `noLoomspanSpecificSpiPackageExists`, `coreInfrastructureDoesNotExposeSupportedReplacementSeams`
- **Type**: architecture/context
- **Locations**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanAutoConfigurationBoundaryTest.java`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`; `LoomspanAutoConfigurationTests.java`
- **What it proves**: New lifecycle beans are explicitly classified as framework-owned infrastructure; there are still exactly eight supported public API types, no API signature leaks `internal`/`autoconfigure`, no SPI package, and no `@ConditionalOnMissingBean` replacement point. Normal contexts wire one lifecycle owner and one shared executor into both engines.
- **Fixtures/data**: ArchUnit/reflection/bean-definition scans already used by these tests.
- **Mocks**: None.
- **Affected surface**: Application API / Supported SPI / internal implementation.
- **Compatibility expectation**: Protected closed API and absence of SPI; internal bean allowlists update atomically.

### 14) Documentation and Current-Run Trace Coherence

- **Names**: `shutdownMetadataMatchesDocumentedDefault` (extend metadata assertion); retain existing trace finalization, observer view, failure precedence, and late-write tests in full suite.
- **Type**: metadata/regression
- **Locations**: `ConfigurationMetadataTest.java`; existing runner/facade/mission/trace tests; `README.md` reviewed against assertions.
- **What it proves**: Documentation and metadata agree on name/default/scope; admitted successful roots still produce one completed trace and immutable public view; cutoff roots do not gain post-cutoff success evidence; no event schema or console compatibility fixture change is introduced.
- **Fixtures/data**: Existing NDJSON readers and public view mapper tests; no historical trace fixtures.
- **Mocks**: Existing trace handles only where failure injection is required.
- **Affected surface**: Configuration behavior / Ephemeral diagnostics / Persisted behavior (no format delta).
- **Compatibility expectation**: Current-run writer/finalizer/mapper coherence and unchanged serialized schema; no cross-version promise.

## How to Run

No external service, credential, profile, environment variable, or network access should be required. Use Java 21+ and Maven 3.9+ as enforced by the parent build. Run from the repository root.

1. Red admission proof before implementation:
   `mvn -pl loomspan-spring-boot-starter -Dtest=FrameworkShutdownIntegrationTest#closeEventRejectsLateRootBeforeSessionConstruction test`
2. Root owner, configuration, runner, and facade:
   `mvn -pl loomspan-spring-boot-starter -Dtest=FrameworkExecutionLifecycleTest,LoomspanSessionPropertiesTest,ConfigurationMetadataTest,LoomspanSessionRunnerTest,DefaultSkillTemplateTest test`
3. Mission cutoff and both engines:
   `mvn -pl loomspan-spring-boot-starter -Dtest=MissionLifecycleTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest test`
4. Spring wiring and boundaries:
   `mvn -pl loomspan-spring-boot-starter -Dtest=FrameworkShutdownIntegrationTest,LoomspanAutoConfigurationTests,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest test`
5. Complete starter suite:
   `mvn -pl loomspan-spring-boot-starter test`
6. Full parent verification:
   `mvn verify`

When diagnosing concurrency failures, run the specific method repeatedly using Surefire's method selector rather than weakening latches or increasing production deadlines. Tests may use short configured durations, but deterministic state/barrier assertions must establish ordering; wall-clock assertions should only prove the outer bound with explicit scheduling tolerance.

## Exit Criteria

- [ ] The failing admission test is committed first and demonstrably fails on the pre-fix code because a post-event invocation constructs/executes a root.
- [ ] `loomspan.shutdown.timeout` defaults to `30s`, binds positive durations, rejects zero/negative values at startup, and appears consistently in metadata and README.
- [ ] Admission and close have one atomic ordering; rejected roots construct no session/trace/observation and admitted roots release exactly once on every success/failure path.
- [ ] Mapping and success observation run on the caller after binding restoration, remain owned through return/failure, never run on the shutdown waiter, and preserve exception identity/placement.
- [ ] All descendant missions and owning/task futures observe a concurrent or already-reached root cutoff; local mission timeouts and first-cause behavior remain intact.
- [ ] The shutdown waiter meets the shared deadline while a mission ancestry lock is held by blocked trace I/O, performs no trace/failure/mapping/observer code, and resumed work cannot gain success credit or admit nested work.
- [ ] Direct and step-loop owning-future cancellation propagate the installed primary and perform permitted cleanup once; uncooperative work cannot create blocking executor destruction.
- [ ] One monotonic deadline covers root execution, nested work, finalization, observation, cutoff, graceful executor cleanup, and forced cleanup. No mission/worker/stage gets a fresh shutdown budget.
- [ ] Owning-context filtering, async opt-out, prompt event return, both independent listener orders, parent/child contexts, repeated close, failed startup, skipped/failed event delivery, and idempotent fallback all pass.
- [ ] A Spring lifecycle-phase timeout shorter than the framework budget does not truncate the framework's remaining wait; required resources remain available until root completion/cutoff.
- [ ] The executor bean has no blocking/inferred `close()` destruction; destruction fallback is explicit, idempotent, non-waiting, and never halts the JVM.
- [ ] The eight-type supported API remains unchanged, no internal/autoconfigure type leaks into API signatures, no SPI/replacement seam appears, and obsolete internal runner/destruction paths are absent rather than retained behind shims.
- [ ] Current trace writer/finalizer/public-view behavior remains coherent and secure with no schema, persisted-format, Console boundary, or compatibility-marker delta.
- [ ] The focused suites, complete starter suite, and `mvn verify` all pass with no unexplained timeout, hang, or flaky rerun.
- [ ] No skill-authoring document or README coverage-table update is present because the change has no skill-authoring impact; application-facing README operations guidance is complete.
- [ ] Optional developer checks: none. All ticket acceptance behavior is automatable in unit or Spring context tests.
