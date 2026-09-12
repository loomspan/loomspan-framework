## Code Review Findings

No actionable findings remain after the fixes applied in this review cycle.

## Findings Resolved in This Context

### [P1] Serialize mission registration with framework deadline and cutoff publication

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:199`
- **Evidence:** `AdmittedRoot.register`, `establishDeadline`, and `cutoff` previously updated the same volatile deadline and iterated the concurrent mission set without one ordering boundary. A registration that captured `Long.MAX_VALUE` could resume after close propagation and overwrite the shared deadline; the weakly consistent iteration also made the intended add-then-recheck contract unnecessarily dependent on collection traversal timing.
- **Trigger:** A descendant `MissionContext` is constructed concurrently with the owning close event or framework cutoff.
- **Impact:** The mission could retain an unbounded framework deadline during local cancellation and consume its own cleanup grace beyond the shared framework budget.
- **Fix:** Serialized root-local registration, deadline publication, and cutoff publication without taking any mission ancestry lock. Added a regression proving a mission registered after admission close inherits the exact shared deadline.

### [P1] Make framework and mission cancellation share one first-cause/deadline authority

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:191`
- **Evidence:** Framework cutoff and local cancellation wrote separate causes without an atomic first-writer decision. In addition, `awaitCutoff` waited only on the original mission-local deadline, and framework deadline publication did not wake an existing condition waiter.
- **Trigger:** Local timeout/interruption cancellation overlaps framework cutoff, especially when local cleanup began before root admission closed.
- **Impact:** A later local failure could displace the framework primary, or an existing cancellation wait could outlive the shared shutdown deadline and continue cleanup after required resources begin teardown.
- **Fix:** Added an atomic primary-cause authority, returned the effective minimum deadline, made `awaitCutoff` use that effective deadline, and used a nonblocking `tryLock` signal so shutdown never waits for trace-contended ancestry locks. Added regressions for both first-cause orders and clamping an existing local cancellation.

### [P2] Preserve safe wrapping for security-context lookup failures

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:122`
- **Evidence:** Refactoring moved `securityContextStrategy.getContext().getAuthentication()` outside both existing exception-translation blocks.
- **Trigger:** A custom `SecurityContextHolderStrategy` throws while resolving the caller context.
- **Impact:** Internal runtime details would escape as a raw exception instead of the established safe `SkillException` boundary.
- **Fix:** Moved lookup into the execution exception boundary and added a regression asserting the safe wrapper, original cause, and no router invocation.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,LoomspanSessionPropertiesTest,ConfigurationMetadataTest,LoomspanSessionRunnerTest,DefaultSkillTemplateTest,MissionLifecycleTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest,FrameworkShutdownIntegrationTest,LoomspanAutoConfigurationTests,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest' test` (135 tests)
- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,MissionLifecycleTest,DefaultSkillTemplateTest' test` (31 tests)
- PASS — `mvn -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,MissionLifecycleTest,DefaultSkillTemplateTest,JavaSkillMissionCutoffTest,StepLoopMissionExecutionEngineTest' test` (83 tests)
- PASS — `mvn -pl loomspan-spring-boot-starter test` (1,067 tests)
- PASS — `mvn verify` (1,067 tests; reactor successful)
- PASS — `git diff --check`

## Requirements and Plan Conformance

- **Implemented:** The final code has one auto-configured root admission owner and one deadline, owns sessions through caller-side mapping/observation, registers descendant mission/future work, performs lock-free framework fencing/cancellation, uses lifecycle-managed executor shutdown with a nonwaiting destruction fallback, binds and documents the positive `30s` default, and keeps the supported API/SPI allowlists closed.
- **Partial:** The testing plan's named adversarial cases are represented by focused lifecycle, mission, engine, facade, configuration, architecture, and full-suite coverage, though several cases are consolidated rather than implemented under every proposed method name.
- **Missing:** None found in the final repository state.
- **Safe deviations:** Existing internal convenience constructors remain for repository-local focused tests; the auto-configured application path always injects the lifecycle owner, and these internal signatures are neither supported API nor SPI.
- **Compatibility review:** The eight allowlisted `ai.loomspan.api` types are unchanged and architecture tests pass. `loomspan.shutdown.timeout` is a deliberate new configuration contract. Late-root rejection is the ticket-authorized beta-4 behavior break; no legacy admission path, public cancellation API, supported bean replacement seam, or compatibility shim was added. Trace and Console formats are unchanged.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact
- **Rationale:** The diff changes embedded-application admission, shutdown resource lifetime, caller-side observation lifetime, and operations configuration. It does not change manifest syntax, skill inputs/outputs, planning, evidence, capability visibility, RBAC, attachments, model selection, quotas, or trace interpretation for skill authors.
- **Documents reviewed:** `README.md`; no skill-authoring knowledge-base documents are affected.
- **Evidence checked:** Root/session runner, facade, mission lifecycle, both mission engines, auto-configuration, configuration metadata, architecture tests, and full-suite trace/observer regressions.
- **Coverage table:** Not applicable
- **LLM-first usability:** Not applicable

## Residual Risks and Optional Developer Checks

- None. The relevant behavior is executable locally and required no external services or credentials.

## Disposition

- **Candidate clean; fresh review required** — all findings found in this context were fixed, but this context changed implementation artifacts.
