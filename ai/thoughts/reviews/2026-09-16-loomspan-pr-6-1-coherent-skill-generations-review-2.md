## Code Review Findings

### [P2] Exercise prepared-source independence and multi-generation coexistence with executable tests

- **Location:** `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:109`
- **Evidence:** The initial candidate marked both behaviors complete, but its manager tests used mocked catalogs and retained at most two distinct generations. They did not prove that a prepared generation remains usable after its actual YAML source is edited or deleted, or that more than two legitimately owned generations coexist without eviction.
- **Trigger:** A regression that lazily rereads YAML after preparation, or a reintroduction of a hidden two-generation retention cap.
- **Impact:** The ticket's central immutability and ownership guarantees could regress while the focused suite remained green.
- **Recommendation:** Use a real temporary YAML source to test edit/delete independence, and retain at least four distinct generations simultaneously while asserting each remains intact.

### [P3] Remove duplicate session-runner argument-order overloads

- **Location:** `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:201`
- **Evidence:** The initial candidate added both `(authentication, generation)` and `(generation, authentication)` forms for the same internal operations. Production and tests selected different forms, creating two equivalent call shapes for the new generation contract without a compatibility requirement.
- **Trigger:** Any future session-runner call site or signature change involving authentication and generation capture.
- **Impact:** Equivalent overloads invite call-site divergence and make it easier to update one path without the other, contrary to the ticket's coherent single-contract design.
- **Recommendation:** Keep one generation-first shape and update callers to use it.

## Findings Resolved in This Context

- **P2 — missing executable coverage:** Added a real temporary-directory YAML test that prepares a generation, edits and deletes the source, activates the prepared object, and proves its prompt, description, and registered YAML remain the original values. Added a four-generation ownership test that proves all retained generations remain distinct and usable without eviction. The focused post-fix suite and full `verify` pass.
- **P3 — duplicate internal overloads:** Removed the authentication-first duplicates from `LoomspanSessionRunner` and updated `DefaultSkillTemplate` to use the single generation-first contract. The focused runner/template suite, architecture test, and full `verify` pass.

## Open Questions and Assumptions

- None.

## Verification Results

- FAIL — `.\mvnw.cmd -Dtest=SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest,DefaultSkillTemplateTest,ExecutionBindingTest,CapabilityExecutionRouterTest,ExecutionCoordinatorTest,SkillVisibilityResolverTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest test`: PowerShell parsed the unquoted comma-separated property as syntax; this was a command invocation error, not a product failure.
- PASS — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest,DefaultSkillTemplateTest,ExecutionBindingTest,CapabilityExecutionRouterTest,ExecutionCoordinatorTest,SkillVisibilityResolverTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest' test` (90 tests before fixes).
- PASS — `.\mvnw.cmd '-Dtest=DefaultSkillCatalogTest,DefaultRegisteredSkillCatalogTest,LoomspanAutoConfigurationTests,SupportedSurfaceIntegrationTest,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest,ConsoleRestFixtureCorpusTest,ConsoleTraceFixtureCorpusTest,ExecutionTraceContractTest' test` (62 tests before fixes).
- PASS — `.\mvnw.cmd verify` (1,135 tests before fixes).
- FAIL — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,LoomspanSessionRunnerTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest' test`: the new test initially referenced nonexistent `RegisteredSkillEntry.manifestText()`; corrected to the existing `yaml()` accessor.
- PASS — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,LoomspanSessionRunnerTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest' test` (65 tests after correction).
- PASS — `.\mvnw.cmd '-Dtest=LoomspanPublicSurfaceArchitectureTest' test` (8 tests after fixes).
- PASS — `.\mvnw.cmd verify` (1,137 tests after fixes).
- PASS — `git diff --check` (exit 0; only line-ending normalization warnings).

## Requirements and Plan Conformance

- **Implemented:** Startup assembles a detached generation before atomic activation; each successful preparation receives a fresh identity; failed preparation leaves the active generation unchanged; generations own immutable YAML and Java execution/visibility state; execution capture occurs before input conversion and is propagated through sessions, nested calls, forks, and grouped workers; capability resolution is constrained to the captured generation; payloads release generation references on invoke, explicit release, or mission cutoff; no archive, weak-reference policy, or hidden two-generation cap remains; discovery catalogs project only the active generation; documentation describes startup snapshot and in-flight consistency behavior. Focused and integration tests provide executable evidence for these criteria.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** Test placement and helper naming differ mechanically from plan prose without changing coverage or behavior.
- **Compatibility review:** The allowlisted top-level `ai.loomspan.api` application API is unchanged, `RestSkillHandler` remains the sole supported Java SPI, public signatures expose no internal/autoconfigure types, and no new bean-replacement SPI was introduced. The removed registries and registrar are internal implementation details, and the ticket expressly authorizes replacing that obsolete model without shims. YAML keys and documented author behavior remain compatible. Console REST/SSE/NDJSON observable contracts and fixtures are unchanged. `LoomspanPublicSurfaceArchitectureTest` passes.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need to understand that validated YAML and Java skills form an immutable startup generation and that admitted executions keep that generation even if a later generation becomes active. The checked-in guidance states those semantics without promising the future public reload API.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/SKILL.md`; `agent-skills/loomspan-docs/references/java-api/java-skills.md`; `agent-skills/loomspan-docs/references/java-api/rest-skills.md`; `agent-skills/loomspan-docs/references/skill-authoring/README.md`; `authorization.md`; `input-contracts.md`; `mental-model.md`; `model-selection-and-connections.md`; `planning-concurrency.md`; `rest-skills.md`; and the source-verification guidance referenced by the docs protocol.
- **Evidence checked:** Generation manager, YAML catalog, Java bean discovery, execution binding/router/coordinator, visibility resolution, invocation handoff, session runner, auto-configuration, focused tests, integration tests, console fixtures, and full repository verification.
- **Coverage table:** Current.
- **LLM-first usability:** Pass.

## Residual Risks and Optional Developer Checks

- None.

## Disposition

- **Candidate clean; fresh review required** — all findings found in this context were fixed, but this context changed implementation and test artifacts.
