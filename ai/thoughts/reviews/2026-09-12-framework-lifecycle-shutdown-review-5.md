# Framework Lifecycle Shutdown — Code Review 5

## Code Review Findings

No actionable findings remain after the fix described below.

## Findings Resolved in This Context

### [P2] Exercise late-root rejection through the session-construction boundary

- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- **Evidence:** The test named `closeEventRejectsLateRootBeforeSessionConstruction` published the owning close event and then invoked `FrameworkExecutionLifecycle#admitRoot` directly. It did not invoke `LoomspanSessionRunner`, so it could not detect a regression that moved admission after `LoomspanSession` construction or action dispatch.
- **Trigger:** A future refactor constructs session observation/trace state before consulting the lifecycle owner while leaving `admitRoot` itself correct.
- **Impact:** The ticket's primary shutdown boundary could regress while its specifically named integration test remained green, allowing rejected work to allocate observable session state or execute code after shutdown begins.
- **Resolution:** The test now invokes the lifecycle-aware `LoomspanSessionRunner` after the close event and asserts `RejectedExecutionException`, zero observation-handle creations, and no action invocation.
- **Verification:** The focused strengthened test and the complete reactor both pass.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=FrameworkExecutionLifecycleTest,LoomspanSessionPropertiesTest,ConfigurationMetadataTest,LoomspanSessionRunnerTest,DefaultSkillTemplateTest,MissionLifecycleTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest,FrameworkShutdownIntegrationTest,LoomspanAutoConfigurationTests,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest,ExecutionCoordinatorTest" test` — 176 focused tests before the review fix; established the starting candidate behavior.
- PASS — `mvn -pl loomspan-spring-boot-starter test` — 1,084 tests before the review fix; production code was unchanged afterward.
- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=FrameworkShutdownIntegrationTest#closeEventRejectsLateRootBeforeSessionConstruction" test` — strengthened boundary test in the final repository state.
- PASS — `mvn verify` — 1,084 tests and reactor packaging in the final repository state.
- PASS — `git diff --check`.

## Requirements and Plan Conformance

- **Implemented:** One lifecycle-aware runner operation admits before session construction, restores binding before caller-side completion, and releases the root in `finally`; the facade retains caller-thread mapping/observation and exception identity.
- **Implemented:** `loomspan.shutdown.timeout` binds as a positive duration with a documented `30s` default; one monotonic deadline is established when admission closes and is reused by root drain, mission cutoff, cancellation grace, and executor cleanup.
- **Implemented:** Root admission and close are atomic; admitted roots propagate the shared deadline to existing and newly registered missions; mission and future registration recheck framework cutoff; post-cutoff ordinary writes and new work are fenced.
- **Implemented:** Owning-context close events are synchronous and non-waiting; `SmartLifecycle` performs bounded waiting at the highest phase; fallback destruction is non-waiting; the mission executor disables inferred `close()` destruction.
- **Implemented:** Direct and step-loop cancellation paths preserve the installed primary where framework fencing/cancellation wins, retain already-completed application failures, and perform caller/worker-side cleanup without trace I/O on the shutdown waiter.
- **Implemented:** README and configuration metadata describe the setting, admission behavior, nested work, observation lifetime, listener independence, and framework-only scope.
- **Implemented:** Supported public API remains the eight-type allowlist, no supported SPI or replacement seam was introduced, and the new technically public lifecycle types are explicitly classified as internal.
- **Partial:** The testing plan's historical instruction to commit a red test before implementation cannot be reconstructed from the current uncommitted working tree. The final strengthened test directly protects the required pre-construction boundary; this is an audit-history limitation, not a remaining runtime defect.
- **Missing:** None.
- **Safe deviations:** Focused tests consolidate some planned scenarios instead of using every proposed method name; the assertions cover the same observable boundaries without introducing test-only production seams.
- **Compatibility review:** Application API signatures are unchanged. Late top-level rejection is the ticket-authorized beta-4 behavioral change and is documented without a legacy path. `loomspan.shutdown.timeout` is a new configuration contract; internal runner, lifecycle, constructor, and bean wiring changes require no shim. Trace schema, persisted formats, Console boundaries, and compatibility markers are unchanged.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact.
- **Rationale:** The change affects embedded-application shutdown admission, resource lifetime, and operations configuration. It does not alter manifest syntax, skill inputs/outputs, planning, evidence, capability visibility, authorization, model selection, quotas, or trace interpretation for skill authors.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`, and `agent-skills/loomspan-docs/references/skill-authoring/source-verification.md`.
- **Evidence checked:** `DefaultSkillTemplate`, `LoomspanSessionRunner`, `ExecutionCoordinator`, `MissionLifecycle`, lifecycle/configuration tests, trace regression tests in the full suite, README, and generated metadata.
- **Coverage table:** Not applicable.
- **LLM-first usability:** Not applicable.
- **Drift classification:** Aligned for the existing invocation and common mission-lifecycle guidance. The installed personal `loomspan-docs` skill reports version `0.1.0-SNAPSHOT` while the checkout is `1.0.0-beta.4-SNAPSHOT`, so it was not used for version-sensitive shutdown claims.

## Residual Risks and Optional Developer Checks

- None. All ticket acceptance behavior is locally automatable, and the final reactor verification passed.

## Disposition

- **Candidate clean; fresh review required** — one P2 verification finding was fixed, the resulting implementation was re-reviewed, no actionable findings remain, and this context changed a test artifact.
