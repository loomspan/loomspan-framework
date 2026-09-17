# PR 8 — Generation Retirement Notification: Review 1

## Code Review Findings

### [P2] Close a registered mission when its frame cannot open

- **Location:** `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:138` (candidate before this review's fix)
- **Evidence:** `MissionContext` registers its lifecycle with the admitted root before `executeBound` calls `ExecutionStateService.openMissionFrame`. The candidate closed that mission only in `executeBound`'s inner `finally`, entered after the frame opened.
- **Trigger:** Frame recording or trace initialization throws from `openMissionFrame`, or another setup operation before the inner `try` throws.
- **Impact:** The mission remains `OPEN`. Logical root completion sees a mission that is not physically complete and retains the generation lease indefinitely, so a superseded generation never receives its retirement notification even though no work remains.
- **Recommendation:** Guarantee mission closure around the whole bound execution, including frame setup, while preserving the existing close point before successful trace finalization.

## Findings Resolved in This Context

- **P2 — early frame-open failure leaves a mission open:** Wrapped `ExecutionBindingScope.supplyWith` in `ExecutionCoordinator.execute` with an outer `finally` that idempotently closes the registered mission. Added `ExecutionCoordinatorTest.failedMissionFrameOpenReleasesRetiredGeneration`, which makes frame opening throw, then verifies retirement after root completion. The focused test and full suite pass.

The final internal re-review found no remaining actionable findings. The changed outer close cannot release ownership while the root is executing; `AdmittedRoot.releaseGenerationIfSafe` also checks every registered mission's physical return. The existing inner close still runs before trace finalization on the ordinary path.

## Open Questions and Assumptions

- None. The review uses the ticket's explicit normal-operation callback and shutdown limits. The unrecorded pre-implementation red run in the testing plan is a process gap, not an unresolved behavior.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=SkillReloaderTest,PublicSkillReloadIntegrationTest,SkillGenerationExecutionIntegrationTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test` — 72 tests.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ExecutionCoordinatorTest' test` — 23 tests, including the regression.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress test` — 1,166 tests, zero failures, errors, or skips.
- PASS — `git diff --check` — exit 0; Git reported only line-ending conversion notices.
- FAIL — `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillReloaderTest,PublicSkillReloadIntegrationTest,SkillGenerationExecutionIntegrationTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,LoomspanPublicSurfaceArchitectureTest test` — PowerShell parsed the unquoted comma list as syntax; the quoted rerun passed.

## Requirements and Plan Conformance

- **Implemented:** The supported `SkillReloader` callback and public example use one fixed `RestSkillHandler`, trusted `RestSkillInvocation.generationId()`, generation-keyed resources, staging before publication, and application-owned cleanup. `SkillGenerationManager` serializes capture and publication, selects each superseded published ID once, excludes never-published candidates, and snapshots listeners before dispatch outside its lock. `DefaultSkillTemplate`, handoff, runner, root, and mission lifecycle connect pre-admission capture through pending admission, logical completion, and physical return. Focused tests cover pending release, capture during conversion, unused and rejected candidates, callback failure and removal, shutdown, and uncooperative physical work. Full verification and the public-surface architecture test pass.
- **Partial:** The testing plan's pre-implementation red run was not recorded. The reviewed regression and public integration tests now exercise the required behavior, and the full suite passes.
- **Missing:** None of the ticket's behavior or documentation criteria.
- **Safe deviations:** The physical-return test uses controlled root and mission lifecycles rather than extending the grouped execution integration fixture. It asserts the exact root and descendant ownership boundary; existing grouped execution integration tests remain green. The frame-open regression is an additional failure path found during review.
- **Compatibility review:** `SkillReloader` is deliberately supported by `LoomspanPublicSurfaceArchitectureTest` and README; adding a method is an intentional application API expansion using only standard Java types. `RestSkillHandler` remains the sole supported SPI and its trusted invocation ID and RBAC path are unchanged. Changed `internal` signatures are implementation details, with no shim needed. No configuration, manifest, persisted, Console, or cross-language protocol changes were found. No new replaceable bean or accidental API type appears. The ticket has no `Pipeline notes` exception to assess.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** REST skill authors and application integrators need the new safe-retirement rule for generation-keyed resources, although manifest syntax and invocation authorization do not change.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/SKILL.md`; `agent-skills/loomspan-docs/references/java-api/README.md`, `skill-reload.md`, and `compatibility-and-boundaries.md`; `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `mental-model.md`, `rest-skills.md`, and `source-verification.md`.
- **Evidence checked:** Matching checkout's `SkillGenerationManager`, `DefaultSkillReloader`, `DefaultSkillTemplate`, root and mission lifecycles, public integration and focused lifecycle tests, and architecture allowlist. The bundled skill version matches the Maven project version.
- **Coverage table:** Current; both Java API and skill-authoring indexes route to the updated guidance. The prior “no retirement API” claims were removed. Classification: **aligned** after the implementation and documentation changes.
- **LLM-first usability:** Pass; the full registration, staging, publication, callback, and shutdown sequence is in the Java API topic, with short links from authoring topics.

## Residual Risks and Optional Developer Checks

- No unrun required verification or optional developer check. The API intentionally does not promise replay, callback completion during shutdown, or cleanup retries. The testing plan's historical red-test evidence is unavailable, but current executable coverage passed.

## Disposition

- **Candidate clean; fresh review required.** This context fixed one P2 finding and found no additional actionable issue on internal re-review. A new independent review must certify the modified implementation.
