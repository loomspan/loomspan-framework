# Atomic Invocation Handoff Testing Plan

## Change Summary

- Add supported `SkillInvocationHandoff` and `AdmittedSkillInvocation` application API types without changing `SkillTemplate`.
- Transfer a prepared invocation and its captured authentication into one pending framework root, then allow exactly one execute/release/cutoff outcome.
- Preserve the single shutdown deadline, direct invocation behavior, authorization, security-context restoration, nested work, exceptions, trace finalization, and observer delivery.
- Replace the generic blocked-writer evidence gap with a real `DefaultExecutionTraceHandle` `TRACE_COMPLETED` append blocked through package-private test seams.

## Impacted Areas

- `ai.loomspan.api`: new handoff facade and admitted handle; exact closed public catalog grows from thirteen to fifteen.
- `DefaultSkillTemplate` and a new internal handoff implementation/shared invocation collaborator: preparation, captured authentication, safe errors, execution, and observer mapping.
- `FrameworkExecutionLifecycle`: pending versus executing ownership, release, cutoff invalidation, active-root accounting, and the one deadline.
- `LoomspanSessionRunner`: direct admission and pre-admitted execution converge on one session/finalization/completion path.
- `LoomspanAutoConfiguration`: one non-replaceable infrastructure bean for the new facade.
- Lifecycle/facade/public-surface/Spring integration tests and a test-only trace-package helper.
- README, Java API knowledge set, release note/readiness, `AGENTS.md`, and Console documentation-package assertions.

## Risk Assessment

- Highest risk: a release/execute/cutoff race starts duplicate work, leaks a pending root, removes an executing root early, or starts a session after cutoff.
- A direct invocation could accidentally admit twice or change its pre-session rejection, exception, observer, or nested-work semantics during runner refactoring.
- Authentication could be read from the later worker thread instead of captured at handoff, be shared mutably across workers, bypass root authorization, or fail to restore the worker's prior context.
- Shutdown could gain a second wait/grace interval, pending handles could retain `activeRoots` after cutoff, or real finalization/observer delivery could fall outside root ownership.
- A blocked trace writer could deadlock cutoff if lifecycle and trace/mission locks acquire in the wrong order.
- Public signatures or Spring wiring could accidentally expose an internal type or create a second supported SPI/replacement bean.
- Exact public-type counts can drift across architecture tests, root docs, bundled Java API docs, release notes, and Console packaging assertions.
- Protected paths: all existing `SkillTemplate.invoke()`/`validate()` overloads, direct auth/error/observer behavior, the sole `RestSkillHandler` SPI, `loomspan.shutdown.timeout`, and current trace coherence.
- Intentionally changed internal paths: the root's close-only state is replaced by pending/claim/release/completion transitions; the runner's one private admit-and-run method is factored into direct and pre-admitted entry paths that share one execution body. No obsolete fallback or dual lifecycle authority remains.
- Skill-authoring claims requiring evidence: none. Java application-developer claims require the API, facade, lifecycle, security, timeout, and integration tests below.

## Existing Test Coverage

- `FrameworkExecutionLifecycleTest#owningCloseEventAtomicallyRejectsLateRootsAndDoesNotWait` covers direct admission versus close and idempotent close, but has no pending/claimed state.
- `FrameworkExecutionLifecycleTest#runnerCompletionOccursAfterBindingRestorationAndBeforeRootRelease`, `#failureCompletionOccursOnceAfterBindingRestorationAndBeforeRootRelease`, and `#shutdownWaitsForSlowFailureCompletionUnderOneRoot` cover final completion/observer ownership for direct roots.
- `FrameworkExecutionLifecycleTest#frameworkCutoffDoesNotWaitForMissionLockHeldByWriter` blocks a generic mission write guard; it does not execute `DefaultExecutionTraceHandle.finalizeTrace` or its `TRACE_COMPLETED` append.
- `FrameworkShutdownIntegrationTest` covers rejection before session construction, validation without admission, asynchronous multicasters, both existing listener orders, fallback destruction, and resource phase lifetime, but not an application-to-framework handoff.
- `DefaultSkillTemplateTest` protects object/map preparation, calling authentication, authorization failures, safe errors, observer delivery, and fatal errors for direct calls.
- `SupportedSurfaceIntegrationTest` exercises real public beans across YAML, Java, REST, authorization, and observation; it currently knows only `SkillTemplate` and `SkillCatalog`.
- `ApplicationApiValueTest` and `LoomspanPublicSurfaceArchitectureTest` protect supported shapes, exact allowlists, and internal-type exclusion; they currently assert the thirteen-type surface.
- Gap: no deterministic handle state/race matrix, no pre-admitted runner evidence, no captured-handoff authentication test, no real blocked completion write under shutdown, and no supported-consumer gate example test.

## Bug Reproduction / Failing Test First

- Type: integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- Proposed name: `handedOffInvocationStartsAfterFrameworkCloseWithoutReacquiringAdmission`
- Arrange/Act/Assert outline:
  - Arrange a real lifecycle/facade fixture, an application gate, and a worker paused after successful public handoff but before handle invocation.
  - While the handle is pending, close framework admission and verify the pending root owns one active reservation.
  - Release the application gate/worker and invoke during the remaining budget.
  - Assert the skill runs once using that root, no second admission is attempted, and root count reaches zero only after finalization/observer completion.
- Expected failure (pre-fix): the new public types do not exist. If only the API skeleton is introduced, current `LoomspanSessionRunner` attempts a fresh root at execution and rejects after admission closes, directly demonstrating the ownership gap.

## Tests to Add/Update

### 1) `apiPackageContainsExactlyFifteenApprovedPublicTypes`
- Type: architecture
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`
- What it proves: only the existing thirteen types plus `SkillInvocationHandoff` and `AdmittedSkillInvocation` are public top-level application API; their recursive signatures leak no internal/autoconfigure types and no `.spi` package or unintended extension point appears.
- Fixtures/data: compiled production classes.
- Mocks: none.
- Affected surface: Application API / Supported SPI.
- Compatibility expectation: additive protected path; `RestSkillHandler` remains the sole SPI.

### 2) `handoffAndAdmissionHandleExposeOnlyTheSupportedSingleUseShape`
- Type: unit/reflection
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`
- What it proves: exact overload parameter/return types are as planned; `SkillTemplate` retains exactly its existing methods; handle methods are `invoke()`, observer `invoke`, and `release()` with no internal types, serialization, executor, or cancellation API.
- Fixtures/data: reflection over API classes.
- Mocks: none.
- Affected surface: Application API.
- Compatibility expectation: protected existing facade plus additive new contract.

### 3) `pendingAdmissionExecutesExactlyOnceAndCompletesOneRoot`
- Type: unit
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- What it proves: pending-to-executing claim succeeds once; repeated/concurrent execution claims lose deterministically; release after claim cannot remove the executing root; completion releases exactly once.
- Fixtures/data: lifecycle, latches/barriers, atomic counters, virtual threads.
- Mocks: no framework mocks; a minimal runner action records execution.
- Affected surface: Internal implementation supporting Application API.
- Compatibility expectation: new protected API behavior.

### 4) `pendingAdmissionReleaseIsIdempotentAndPreventsExecution`
- Type: unit
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- What it proves: first release removes the pending root, repeated/concurrent releases are harmless, later execution cannot construct a session, and root count does not leak.
- Fixtures/data: lifecycle and controlled admission.
- Mocks: none.
- Affected surface: Internal implementation supporting Application API.
- Compatibility expectation: new protected API behavior.

### 5) `cutoffInvalidatesAndReleasesPendingAdmission`
- Type: unit
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- What it proves: shutdown close establishes the normal deadline while pending work may still start; deadline cutoff atomically invalidates/removes a still-pending root even if its caller never releases; later release is harmless and invoke is rejected.
- Fixtures/data: controllable `nanoTime`, latches, lifecycle root count.
- Mocks: none.
- Affected surface: Internal implementation supporting Application API.
- Compatibility expectation: new protected API behavior and preserved one-budget shutdown.

### 6) `releaseExecutionAndCutoffRacesHaveOneWinnerWithoutLeaks`
- Type: unit/concurrency
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- What it proves: latch-controlled schedules for execution-before-release, release-before-execution, cutoff-before-execution, and execution-before-cutoff never duplicate work, never release executing ownership early, and end with zero roots after completion/cutoff.
- Fixtures/data: deterministic barriers/latches; no probabilistic stress loops as the primary evidence.
- Mocks: none.
- Affected surface: Internal implementation supporting Application API.
- Compatibility expectation: new protected API behavior.

### 7) `handoffCapturesPreparedInputAndCallingAuthentication`
- Type: unit
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- What it proves: object and map overloads use the existing exact lookup/input-validation path; handoff captures the current authentication; invocation on a worker with a different/empty context authorizes and executes with the captured identity; the exact prior worker context is restored.
- Fixtures/data: existing capability registry/input-validator/security strategy fixture and two authentications.
- Mocks: existing mocked router/runner collaborators, extended to accept a pre-admitted root.
- Affected surface: Application API / security boundary.
- Compatibility expectation: new protected path; no authorization bypass.

### 8) `handoffFailuresAndHandleMisusePreserveFacadeErrorRules`
- Type: unit
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- What it proves: invalid/null input and unknown skill fail before admission; framework-close rejection creates no session; access denial remains the original `AccessDeniedException`; runtime failures map to safe `SkillException`; repeated invoke and invoke-after-release/cutoff fail without work; JVM `Error` is not swallowed; abandoned paths release in `finally`.
- Fixtures/data: existing facade failure fixtures plus controllable lifecycle.
- Mocks: focused collaborator failures.
- Affected surface: Application API.
- Compatibility expectation: protected direct error taxonomy extended coherently to handoff.

### 9) `handedOffObserverRunsOnceBeforeRootRelease`
- Type: unit/integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- What it proves: success and post-session failure map/deliver available history once after binding restoration; observer failure ordering/suppression matches direct invocation; a release racing the callback cannot change the handle's executing ownership. The lifecycle and trace tests separately assert the root remains active through the callback.
- Fixtures/data: real runner/lifecycle fixture with latches in completion/observer and the existing focused view mapper failures.
- Mocks: capability execution only where needed to force the failure path; no lifecycle mock.
- Affected surface: Application API / Ephemeral diagnostics.
- Compatibility expectation: protected observer behavior and current-run diagnostic coherence.

### 10) `handedOffInvocationStartsAfterFrameworkCloseWithoutReacquiringAdmission`
- Type: integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- What it proves: a handoff that wins before framework close may begin on the application worker during the remaining shutdown budget, constructs one session, and owns exactly one root; close winning first rejects before session/work.
- Fixtures/data: Spring context/lifecycle, application gate, latches, real public handoff facade.
- Mocks: existing minimal runner action or registered Java skill; no lifecycle mock.
- Affected surface: Application API / Configuration behavior.
- Compatibility expectation: new path plus preserved `loomspan.shutdown.timeout` semantics.

### 11) `externalAndFrameworkCloseListenerOrdersPreserveOwnershipBoundary`
- Type: integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- What it proves: for both application-listener registration orders, (a) successful handoff before close remains framework-owned, (b) framework close before handoff rejects, and (c) external dispatch close before handoff causes the application to discard without calling Loomspan. No assertion requires identical results for different winning schedules or listener priority.
- Fixtures/data: two context configurations/listener registration orders, gate, deterministic latches, execution counter.
- Mocks: none beyond a minimal skill action.
- Affected surface: Application API / Internal lifecycle integration.
- Compatibility expectation: new protected ownership contract.

### 12) `supportedSurfaceCanHandoffAuthenticatedInvocationAndNestedWork`
- Type: integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`
- What it proves: an application obtains real `SkillInvocationHandoff` and `SkillTemplate` beans, uses only supported types/standard Spring Security facilities, invokes Java/REST/YAML paths including nested work, receives the documented result/observer view, and retains direct invoke/validate behavior.
- Fixtures/data: existing supported-surface local protocol fixture and registered skills.
- Mocks: local protocol server only, as existing.
- Affected surface: Application API / Supported SPI.
- Compatibility expectation: protected old and additive public paths.

### 13) `actualTraceCompletionWriteRemainsUnderRootUntilReleased`
- Type: unit/integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java` with `src/test/java/ai/loomspan/internal/runtime/trace/BlockingTraceHandleTestSupport.java`
- What it proves: a real `DefaultExecutionTraceHandle` reaches its `TraceRecordWriter.append` with `TRACE_COMPLETED`; while blocked, the root is active and normal shutdown waits; after release within budget, finalization, observer delivery, and root release complete in order.
- Fixtures/data: temporary trace path, real handle, writer delegating all records except a latch-blocked completion record, real session runner, observer latch.
- Mocks: no public trace mock; test-only package peer around the existing package-private writer constructor.
- Affected surface: Ephemeral diagnostics / Internal implementation.
- Compatibility expectation: current-run diagnostic coherence and ownership evidence.

### 14) `blockedActualTraceCompletionCannotExtendShutdownBudget`
- Type: unit/integration
- Location: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java` with the same trace test helper.
- What it proves: if the real completion append remains blocked past the shared deadline, lifecycle stop publishes cutoff and returns within that original budget with no grace period; resources are not torn down before cutoff, and releasing the writer later allows safe caller cleanup without a second wait promise.
- Fixtures/data: short deterministic lifecycle duration, latches, temporary trace, monotonic elapsed bounds with generous test timeout.
- Mocks: test-only controllable writer around real handle.
- Affected surface: Ephemeral diagnostics / Configuration behavior / Internal lifecycle.
- Compatibility expectation: current-run diagnostic coherence and preserved one-budget shutdown.

### 15) `javaApiDocumentationPackageNamesTheCompleteHandoffContract`
- Type: Go unit/content contract
- Location: `loomspan-console/internal/buildtool/projectdeclarations_test.go`
- What it proves: the bundled Java API package includes both new types, fifteen-type wording, ownership/release/authentication/timeout guidance, and the sole-SPI boundary.
- Fixtures/data: checked-in `agent-skills/loomspan-docs/references/java-api` files.
- Mocks: none.
- Affected surface: Application API documentation.
- Compatibility expectation: protected docs/package coherence.

## How to Run

- Red test first: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=FrameworkShutdownIntegrationTest#handedOffInvocationStartsAfterFrameworkCloseWithoutReacquiringAdmission' test`
- Lifecycle/race/finalization: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test`
- Facade/public supported surface: `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ApplicationApiValueTest,DefaultSkillTemplateTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest' test`
- Console documentation package: `go -C loomspan-console test ./internal/buildtool`
- Version check: `python scripts/loomspan_version.py check`
- Version-script regression: `python -m unittest discover -s scripts/tests -p "test_*.py"`
- Full reactor: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify`
- Nonpublishing release packaging: `.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests '-Dgpg.skip=true' verify`
- Hygiene: `git diff --check` and `git status --short`
- No environment variables, external services, snapshot installation, Sidecar checkout changes, tags, or publication are required or authorized. The existing supported-surface test uses its local protocol-compatible fixture.

## Exit Criteria

- [x] The targeted test is captured failing before implementation for the missing/incorrect pre-admitted path, then passes post-fix.
- [x] All handoff schedules are deterministic: handoff wins before close, framework close wins before handoff, external close wins before handoff, and both listener registration orders are covered.
- [x] Exactly one of execution, release, or cutoff owns each pending handle; duplicate/rejected calls start no session or work and every terminal schedule leaves no pending root leak.
- [x] Pending work may start only during the remaining shared shutdown budget; cutoff invalidates it, while executing work remains tracked through actual trace finalization and observer delivery.
- [x] Authentication is captured at handoff, authorization is enforced during execution, nested work receives the existing trusted identity, and the application worker's prior security context is restored.
- [x] Existing direct invocation, validation, exception, observer, nested-work, and configuration tests pass without weakening their assertions.
- [x] A real `TRACE_COMPLETED` writer append is blocked in both release-within-budget and cutoff-at-budget schedules; the generic mission-write test alone is not counted as acceptance evidence.
- [x] Public architecture tests recognize exactly fifteen deliberate API types, expose no internal/autoconfigure signature, and create no SPI/replacement surface beyond `RestSkillHandler`.
- [x] README, Java API knowledge set, release notes/readiness, `AGENTS.md`, and Console content assertions agree with executable behavior; the skill-authoring coverage table remains unchanged for the documented no-impact reason.
- [x] `1.0.0-beta.4-SNAPSHOT` remains unchanged and full Maven, version, Python, Go, release-profile, and diff-hygiene checks pass.
- [x] The release-readiness record distinguishes new framework evidence from the pending snapshot reinstall and Sidecar adaptation/rerun, and does not claim Sidecar SC5's public-contract blocked-trace criterion complete.
- [x] No legacy admission behavior, fallback second admission, second deadline, public trace extension, test-only production hook, snapshot install, Sidecar change, tag, or publication is present.
- [x] Non-automatable observations are unnecessary; all ticket acceptance evidence in this repository is automated.
