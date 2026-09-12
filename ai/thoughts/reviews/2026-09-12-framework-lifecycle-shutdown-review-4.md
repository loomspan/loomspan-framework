## Code Review Findings

No actionable findings remain after the fixes applied in this review context.

## Findings Resolved in This Context

### [P1] Preserve a completed application failure when framework cutoff races future collection

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:85`
- **Evidence:** Both the direct-work and step-loop `ExecutionException` paths replaced every completed future failure with the installed primary cancellation. A framework cutoff can be published after an application future has already completed exceptionally but before the caller executes `Future.get()`, so the original application exception was reachable and was incorrectly masked.
- **Trigger:** Application work completes exceptionally, framework cutoff is installed before the caller collects the completed future, and `get()` raises `ExecutionException` with the application cause.
- **Impact:** Callers receive `FrameworkShutdownException` instead of the actual completed application failure, violating first-cause/primary-failure semantics and obscuring diagnostics.
- **Resolution:** Framework shutdown now replaces only the cutoff-fence `MissionWriteRevokedException`; locally installed mission cancellations retain their existing primary-cause cleanup behavior. Added `frameworkCutoffDoesNotReplaceAnAlreadyCompletedApplicationFailure` and retained the framework-primary cancellation regression proof.

### [P1] Complete framework-cancelled root traces with a recorded failure and aborted outcome

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:167`
- **Evidence:** Framework cutoff installs a primary cancellation without a failure id because the shutdown waiter is prohibited from running trace I/O. The coordinator reused that null id, then required it while building close metadata. This added a spurious suppressed `NullPointerException`, prevented root-frame cleanup, and could make final trace validation fail. `FrameworkShutdownException` was also absent from cancellation classification, producing `failed` instead of `aborted` metadata.
- **Trigger:** A direct or step-loop root is interrupted by the shared framework cutoff before mission-local cancellation has recorded a failure.
- **Impact:** Shutdown returned the right primary exception only with misleading suppressed failure, while root frame/trace closure could be incomplete or fail its terminal-failure invariant.
- **Resolution:** The caller records the framework cancellation through cutoff-authorized cleanup after the waiter has returned, close metadata tolerates a missing id if recording itself fails, and framework shutdown is classified as cancellation. `preservesFrameworkShutdownPrimaryWhenCutoffCancelsDirectMission` now asserts no suppressed exception, an aborted root frame, and an `ABORTED` trace completion.

### [P2] Prevent a lost cutoff signal from extending mission cleanup beyond the shared deadline

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:248`
- **Evidence:** Framework deadline publication deliberately uses `tryLock()` to avoid blocking behind ancestry/trace locks. If it failed to obtain the mission lock after the cleanup waiter evaluated its predicate but before `awaitNanos`, the best-effort signal was lost and the waiter could sleep until the older mission-local 250 ms grace expired.
- **Trigger:** Framework cutoff races `awaitCutoff()` while another thread briefly owns the mission lock at the signal point and the new shared deadline is shorter than the local grace deadline.
- **Impact:** Caller-side cleanup could exceed the single shared framework budget by nearly the full local grace interval.
- **Resolution:** Cleanup waits re-evaluate the effective framework deadline at bounded 1 ms intervals, preserving nonblocking cutoff publication while preventing a lost signal from materially extending the shared deadline.

## Open Questions and Assumptions

- None. Ticket scope explicitly permits internal lifecycle changes and intentionally changes late-root admission during shutdown.

## Verification Results

- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,MissionLifecycleTest,JavaSkillMissionCutoffTest,ExecutionCoordinatorTest,LoomspanSessionRunnerTest,DefaultSkillTemplateTest,LoomspanSessionPropertiesTest,ConfigurationMetadataTest,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest" test` (110 tests before review fixes; established the reviewed baseline)
- FAIL — `mvn -pl loomspan-spring-boot-starter "-Dtest=JavaSkillMissionCutoffTest,ExecutionCoordinatorTest,StepLoopMissionExecutionEngineTest,MissionLifecycleTest" test`: the final exception-precedence refinement referenced `FrameworkShutdownException` without importing it in the step-loop engine; fixed immediately.
- PASS — `mvn -pl loomspan-spring-boot-starter clean "-Dtest=JavaSkillMissionCutoffTest,ExecutionCoordinatorTest,StepLoopMissionExecutionEngineTest,MissionLifecycleTest" test` (89 tests; clean production and test compilation plus focused behavior)
- PASS — `mvn -pl loomspan-spring-boot-starter test` (1,084 tests)
- PASS — `mvn verify` (full two-module reactor; 1,084 tests and jar packaging)
- PASS — `git diff --check` (no whitespace errors; Git emitted only existing line-ending normalization warnings)

Intermediate targeted runs also exposed and guided the trace terminal-failure-id correction and preservation of locally installed cancellation semantics; both are covered by the final clean focused run and full verification above.

## Requirements and Plan Conformance

- **Implemented:** Both runner entry methods share a single admitted root operation whose lease spans construction, execution, finalization, caller-side mapping, and success observation; validation remains before admission and all completion paths release in `finally`.
- **Implemented:** Root admission and shutdown are atomically ordered; admitted roots retain nested mission admission until cutoff; concurrent mission/future registration observes an already-published cutoff.
- **Implemented:** One monotonic framework deadline covers active roots, observation, mission cutoff, and executor cleanup. Cutoff is lock-independent, fences resumed writers/nested work, cancels direct and step-loop futures, and preserves completed application failures plus cancellation-authorized cleanup.
- **Implemented:** The owning-context, synchronous event listener returns promptly; lifecycle stop performs the bounded wait; ordinary lifecycle phase timeout does not replace the configured framework deadline; destruction fallback is explicit, idempotent, non-waiting, and uses no inferred blocking executor close.
- **Implemented:** `loomspan.shutdown.timeout` defaults to `30s`, accepts positive durations, rejects invalid values at startup, and is aligned across binding, metadata, README, and tests.
- **Implemented:** Integration coverage includes listener orders, asynchronous multicasting, context ownership, repeated close, startup/event failure fallback, observer lifetime, blocked trace ownership, direct and step-loop cancellation, and required resource lifetime.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** Framework-cancellation failure recording is intentionally deferred from the shutdown waiter to the original caller and performed only under established cutoff cleanup authority; this satisfies both the no-trace-I/O-on-waiter rule and the terminal trace failure-id invariant.
- **Compatibility review:** No allowlisted `ai.loomspan.api` signature changed, no internal/autoconfigure type leaks into supported API, no SPI or bean-replacement seam was added, and no compatibility shim is warranted for internal implementation changes. `LoomspanPublicSurfaceArchitectureTest` and `LoomspanAutoConfigurationBoundaryTest` pass in the complete suite. Late-root rejection is the ticket's intentional beta 4 behavioral change; existing success-observer exception propagation remains intact.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact
- **Rationale:** The diff changes embedded-application shutdown admission, Spring lifecycle wiring, internal mission cutoff, and application-facing configuration. It does not alter skill manifests, model selection, capabilities, execution-plan contracts visible to authors, evidence, input/output, RBAC, attachments, virtual files, quotas, or author-facing trace/debug formats.
- **Documents reviewed:** `C:/Users/mgiacomi/.codex/skills/loomspan-docs/references/skill-authoring/README.md`; `C:/Users/mgiacomi/.codex/skills/loomspan-docs/references/skill-authoring/source-verification.md`
- **Evidence checked:** Actual production/test diff, README configuration guidance, public-surface architecture tests, focused lifecycle tests, and full starter verification. Installed docs skill version `0.1.0-SNAPSHOT` differs from checkout `1.0.0-beta.4-SNAPSHOT`; no version-sensitive authoring claim was used.
- **Coverage table:** Not applicable
- **LLM-first usability:** Not applicable

## Residual Risks and Optional Developer Checks

- None. All acceptance behavior identified by the ticket and testing plan is covered by executable tests in the passing starter suite.

## Disposition

- **Candidate clean; fresh review required** — three actionable findings (P1: 2, P2: 1) were fixed in this context; the final internal review found no remaining actionable findings and all final verification passed.

STATUS: complete
ARTIFACTS: ai/thoughts/reviews/2026-09-12-framework-lifecycle-shutdown-review-4.md
VERIFICATION: PASS — `mvn -pl loomspan-spring-boot-starter clean "-Dtest=JavaSkillMissionCutoffTest,ExecutionCoordinatorTest,StepLoopMissionExecutionEngineTest,MissionLifecycleTest" test` (89 tests); PASS — `mvn -pl loomspan-spring-boot-starter test` (1,084 tests); PASS — `mvn verify` (1,084 tests, reactor/package); PASS — `git diff --check`
OPTIONAL_DEVELOPER_CHECKS: None.
SUMMARY: Candidate clean; fresh review required. Resolved P1: 2, P2: 1; remaining actionable findings: 0.
REVIEW_RESULT: fixes-applied
