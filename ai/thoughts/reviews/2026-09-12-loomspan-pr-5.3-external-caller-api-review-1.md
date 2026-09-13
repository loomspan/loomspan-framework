## Code Review Findings

No actionable findings remain after the fixes applied in this review context.

## Findings Resolved in This Context

### [P2] Require retained finalized history before mapping a completion view

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java:34`
- **Evidence:** The mapper previously called `LoomspanSession#getExecutionJournal()`. When finalization failed, that accessor projected the live trace even though the runner had not retained a finalized journal. The execution-failure completion callback could consequently receive a non-finalized view, contrary to the requirement that it receive only available completed history. A regression test was added first and failed against that behavior.
- **Trigger:** Skill execution fails and session finalization also fails before a finalized journal is retained.
- **Impact:** The failure callback could observe history that had never completed the finalization boundary, making the public `SkillExecutionView` contract misleading and potentially inconsistent with lifecycle guarantees.
- **Resolution:** Added `LoomspanSession#getFinalizedExecutionJournal()` and made `SkillExecutionViewMapper` require that retained value. If it is unavailable, mapping fails and the runner preserves the original execution failure while suppressing the mapping/finalization failure. `SkillExecutionViewMapperTest#refusesToMapSessionWithoutRetainedFinalizedHistory` demonstrated the defect red before the fix and passed afterward; `LoomspanSessionRunnerTest#finalizationFailureLeavesHistoryUnavailableWhilePreservingActionFailure` protects the composed failure path.

### [P2] Make acceptance tests expose suppressed callbacks and lifecycle regressions

- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:106`
- **Evidence:** Assertions performed inside the failure-completion callback could be swallowed because observer failures are intentionally suppressed onto the primary skill failure. The candidate also lacked direct acceptance proof for validation/invocation conversion parity, Java `@RolesAllowed` validation through the supported surface, and shutdown overlap with a slow failure callback.
- **Trigger:** A failure callback receives the wrong view, validation drifts from invocation, authorization is skipped for validation, or shutdown races a callback that is still running.
- **Impact:** Meaningful regressions at the new public validation and failure-observation boundaries could pass the focused suite unnoticed.
- **Resolution:** Captured callback observations and asserted them outside the callback; added exact-once and ordered history assertions, validation/invocation parity checks, no-session/binding-preservation checks, supported-surface Java authorization coverage, and a slow failure-callback shutdown test that proves one active root remains until completion.

### [P3] Repair stale Java API source anchors

- **Location:** `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md:108`
- **Evidence:** The source anchors named a test method that no longer existed and described the invalid-input boundary as a failed-invocation observer lifecycle, even though the new failure callback deliberately expands that lifecycle.
- **Trigger:** A developer or agent follows the knowledge-set anchors to verify exception and observer behavior.
- **Impact:** The documentation sent readers to stale evidence and could lead them to infer that failure callbacks were not supported.
- **Resolution:** Replaced the stale anchor with the current failure-history test, added the finalized-history availability test, and described the no-callback case narrowly as the pre-session invalid-input boundary.

## Open Questions and Assumptions

- None. The review scope was the current `main` working tree: no staged changes, with the listed unstaged and untracked implementation files. The supplied ticket, implementation plan, and testing plan were the intent inputs; no prior review document was consulted.

## Verification Results

- FAIL — `mvn -pl loomspan-spring-boot-starter "-Dtest=SkillExecutionViewMapperTest#refusesToMapSessionWithoutRetainedFinalizedHistory" test`: expected pre-fix regression result; the mapper did not reject a session lacking retained finalized history.
- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=SkillExecutionViewMapperTest#refusesToMapSessionWithoutRetainedFinalizedHistory" test`: passed after the production fix.
- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=DefaultSkillTemplateTest#executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure" test`
- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,JavaSkillAuthorizationIntegrationTests,DefaultAccessGuardTest,LoomspanAutoConfigurationTests" test`: 52 tests, 0 failures, 0 errors, 0 skipped.
- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=LoomspanSessionRunnerTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,SkillExecutionViewMapperTest" test`: 46 tests, 0 failures, 0 errors, 0 skipped.
- PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest" test`: 9 tests, 0 failures, 0 errors, 0 skipped.
- PASS — `mvn clean verify`: reactor success; 1,134 tests, 0 failures, 0 errors, 0 skipped.
- PASS — `git diff --check`: no whitespace errors; Git reported only existing line-ending normalization warnings.

## Requirements and Plan Conformance

- **Implemented:** The application API exposes immutable catalog descriptors for eagerly completed YAML, Java, and REST capabilities, including leaf capabilities, with lookup and deterministic snapshot behavior. `DefaultSkillCatalogTest`, `ApplicationApiValueTest`, `LoomspanAutoConfigurationTests`, and `SupportedSurfaceIntegrationTest` exercise the contract and wiring.
- **Implemented:** Both `SkillTemplate#validate` overloads use the same name resolution, authorization, schema validation, and object-conversion paths as invocation without opening a session or executing the capability. Focused unit tests cover map/object success, invalid input, unknown or non-invokable names, Java authorization, conversion parity, and preservation of an existing execution binding.
- **Implemented:** Completion callbacks now receive retained finalized history for success and ordinary execution failure, run exactly once within the caller-owned root, preserve the primary failure if observation fails, emit no callback for pre-session invalid input, and do not fabricate a view after finalization failure. Runner, facade, lifecycle, shutdown, mapper, and supported-surface tests cover these boundaries.
- **Implemented:** The README, Java API knowledge set, public-surface allowlist, value-object tests, and supported-surface integration test were updated atomically for the new catalog, validation, and failure-history behavior.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** The implementation did not add the plan's optional `objectiveFor` cleanup because the relevant execution path was not otherwise changed. The mapper remains package-internal and testable without widening the supported surface.
- **Compatibility review:** `SkillCatalog`, `SkillDescriptor`, and `SkillKind` are deliberate additions to the closed `ai.loomspan.api` allowlist. The two new abstract `SkillTemplate#validate` methods and failure-callback expansion are source/binary or behavioral changes to supported API, but the ticket's Pipeline notes explicitly authorize the pre-1.0 no-shim break and require atomic repository migration. All in-repository implementations and callers are updated, architecture tests prevent internal/autoconfigure leakage, and no Java SPI or bean-replacement contract was introduced. The Console Java-to-Go application-adapter boundary is not changed.

## Skill-Authoring Documentation Impact

- **Assessment:** No skill-authoring impact; the Java application API knowledge set is affected and is aligned.
- **Rationale:** The change does not alter manifest syntax, author-declared roles, schemas, mappings, planning semantics, evidence contracts, attachments, limits, or other author-facing skill behavior. It adds application-side catalog discovery, preflight validation, and invocation observation behavior.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/java-api/README.md`, `catalog-and-validation.md`, `compatibility-and-boundaries.md`, `invocation.md`, `java-skills.md`, and `observation-and-errors.md`.
- **Evidence checked:** Matching production source, public-surface architecture tests, API value tests, catalog/facade tests, supported-surface integration tests, lifecycle tests, and the repository-bundled version-aligned `loomspan-docs` skill.
- **Coverage table:** Current for the affected Java API knowledge set; not applicable to skill-authoring guidance.
- **LLM-first usability:** Pass. Exact behavioral claims are routed from the Java API index to focused pages and backed by current source/test anchors.

## Residual Risks and Optional Developer Checks

- None. The focused suites and complete reactor verification exercise the available in-repository runtime, compatibility, lifecycle, and documentation evidence.

## Disposition

- **Candidate clean; fresh review required** — this context fixed two P2 findings and one P3 finding, then completed a full re-review with no remaining actionable findings. Because implementation artifacts changed, another fresh Step 5 context must validate the result.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-09-12-loomspan-pr-5.3-external-caller-api-review-1.md
SUMMARY: Candidate clean after resolving 2 P2 findings and 1 P3 finding; 0 findings remain. A fresh review is required because this context changed implementation artifacts.
DECISIONS:
  - Required public completion views to come only from a retained finalized journal so finalization failure cannot expose a live projection.
  - Treated the authorized pre-1.0 `SkillTemplate` break as intentional and verified atomic repository migration without adding compatibility shims or an SPI.
  - Classified the change as application-API documentation impact with no Loomspan skill-authoring contract impact.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
  - FAIL — `mvn -pl loomspan-spring-boot-starter "-Dtest=SkillExecutionViewMapperTest#refusesToMapSessionWithoutRetainedFinalizedHistory" test`: expected pre-fix regression result
  - PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=SkillExecutionViewMapperTest#refusesToMapSessionWithoutRetainedFinalizedHistory" test`
  - PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=DefaultSkillTemplateTest#executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure" test`
  - PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,JavaSkillAuthorizationIntegrationTests,DefaultAccessGuardTest,LoomspanAutoConfigurationTests" test`
  - PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=LoomspanSessionRunnerTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,SkillExecutionViewMapperTest" test`
  - PASS — `mvn -pl loomspan-spring-boot-starter "-Dtest=LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest" test`
  - PASS — `mvn clean verify`
  - PASS — `git diff --check`
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: fixes-applied
NEXT: Launch Step 5 review cycle 2 in a fresh context.
