## Code Review Findings

### [P1] Release a pending handoff's generation when lifecycle cutoff wins

- **Location:** `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:73`
- **Evidence:** The candidate captured the complete `SkillGeneration` in a handoff payload and cleared that payload only when `invoke()` or `release()` was called. `FrameworkExecutionLifecycle.stop()` could terminate a still-pending admitted root without notifying the handoff, so an application-held handoff retained the generation after cutoff. Calling `invoke()` later happened to clear the payload, but cleanup depended on an optional later caller action rather than the terminal lifecycle transition.
- **Trigger:** Prepare a handoff, retain the returned handle, stop or destroy the framework before invoking or releasing it, and never call the handle again.
- **Impact:** The stopped handoff retains its generation and all generation-owned catalogs, capability metadata, Java targets, and REST handler dependencies for as long as the handle is retained, violating the ticket's cleanup requirement and making obsolete generations unnecessarily long-lived.
- **Recommendation:** Give a pending admitted root a termination callback and clear the handoff payload when lifecycle cutoff releases the root, with registration and cutoff coordinated under the lifecycle lock so neither race can miss cleanup.

### [P2] Pin the REST handler dependency once for the generation manager

- **Location:** `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:124`
- **Evidence:** The candidate froze the list of `RestSkillHandler` bean names, but called `BeanFactory.getBean(...)` during every candidate preparation. A prototype-scoped handler therefore produced a different handler object in successive generations even though the ticket requires candidates to reuse fixed non-skill dependencies captured by the manager.
- **Trigger:** Configure a REST skill and expose the sole `RestSkillHandler` bean with prototype scope, then prepare two generations.
- **Impact:** Generation behavior can silently change between reload candidates for reasons unrelated to skill definitions, defeating the fixed-dependency boundary and potentially splitting handler-owned state or resources across generations.
- **Recommendation:** Resolve the sole handler lazily only when a REST manifest needs it, then cache and reuse that exact instance for every subsequent preparation. Preserve the existing behavior that applications without REST skills need no handler.

### [P2] Snapshot generation-loading configuration at manager construction

- **Location:** `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:168`
- **Evidence:** The candidate's catalog supplier closed over mutable bound `LoomspanProperties` and constructed each `YamlSkillCatalog` from its then-current skill locations, connection driver map, and model aliases. Mutating those objects after the manager was built changed later candidate inputs despite the ticket requiring fixed model/connection/source configuration dependencies.
- **Trigger:** Construct the manager, mutate the bound skills locations, connection driver, model connection, provider model, or thinking level, and then prepare another candidate.
- **Impact:** A reload can incorporate configuration changes that were not part of the skill source update, so generations are no longer deterministic snapshots of skill definitions against stable supporting configuration.
- **Recommendation:** Deep-copy the generation-loading portion of the bound properties when the manager is constructed and build all candidate catalogs from that fixed snapshot.

## Findings Resolved in This Context

- **[P1] Pending handoff cleanup:** Added a race-safe pending-termination callback to `FrameworkExecutionLifecycle.AdmittedRoot`, invoked callbacks outside the lifecycle monitor on release/cutoff, and registered payload clearing from `DefaultSkillInvocationHandoff`. The test now proves the payload is null immediately after cutoff, before any later invocation.
- **[P2] REST handler identity:** Added lazy synchronized caching of the resolved `RestSkillHandler` and a prototype-bean regression test proving two prepared generations use one exact handler instance.
- **[P2] Fixed configuration inputs:** Added a deep snapshot of skill locations, connection drivers, model connections, provider model names, and thinking levels at manager construction, plus a mutation regression test.
- Added activation-boundary integration coverage proving an admitted old handoff completes against its captured generation after activation, a new root observes the replacement authorization policy, capture occurs before object conversion/input validation, and forked bindings preserve exact generation identity.

## Open Questions and Assumptions

- None. The ticket and its pipeline notes explicitly authorize the internal registry replacement and require no compatibility shim for removed internal types.

## Verification Results

- FAIL — `.\mvnw.cmd -Dtest=SkillGenerationManagerTest,DefaultSkillTemplateTest,ExecutionBindingTest,CapabilityExecutionRouterTest,ExecutionCoordinatorTest,SkillVisibilityResolverTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,DefaultSkillCatalogTest,DefaultRegisteredSkillCatalogTest,LoomspanAutoConfigurationTests,SupportedSurfaceIntegrationTest,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest test`: PowerShell parsed the unquoted comma-separated test selector before Maven; no tests ran.
- PASS — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,DefaultSkillTemplateTest,ExecutionBindingTest,CapabilityExecutionRouterTest,ExecutionCoordinatorTest,SkillVisibilityResolverTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,DefaultSkillCatalogTest,DefaultRegisteredSkillCatalogTest,LoomspanAutoConfigurationTests,SupportedSurfaceIntegrationTest,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest' test` (126 tests before review fixes).
- FAIL — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,DefaultSkillTemplateTest,ExecutionBindingTest,LoomspanAutoConfigurationTests,FrameworkExecutionLifecycleTest' test`: the newly added prototype-handler test initially had an ambiguous lambda target; corrected with an explicit `RestSkillHandler` cast.
- PASS — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,DefaultSkillTemplateTest,ExecutionBindingTest,LoomspanAutoConfigurationTests,FrameworkExecutionLifecycleTest' test` (68 tests after correction).
- PASS — `.\mvnw.cmd clean`.
- PASS — `.\mvnw.cmd '-Dtest=SkillGenerationExecutionIntegrationTest#capturedHandoffUsesOneGenerationAfterActivationAndNewRootUsesReplacement' test` (primary scenario, 1 test).
- PASS — `.\mvnw.cmd '-Dtest=SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest,SkillGenerationAuthorizationIntegrationTest,DefaultSkillTemplateTest,ExecutionBindingTest,CapabilityExecutionRouterTest,ExecutionCoordinatorTest,SkillVisibilityResolverTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,DefaultSkillCatalogTest,DefaultRegisteredSkillCatalogTest,LoomspanAutoConfigurationTests,SupportedSurfaceIntegrationTest,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest' test` (130 tests; the authorization behavior is exercised by `SkillGenerationExecutionIntegrationTest` and existing security suites).
- PASS — `.\mvnw.cmd '-Dtest=ConsoleRestFixtureCorpusTest,ConsoleTraceFixtureCorpusTest,ExecutionTraceContractTest' test` (22 protocol/fixture tests).
- PASS — `.\mvnw.cmd '-Dtest=LoomspanPublicSurfaceArchitectureTest' test` (8 supported-surface tests).
- PASS — `git diff --check` (no whitespace errors; Git emitted line-ending notices only).
- PASS — `.\mvnw.cmd verify` (1,135 tests, 0 failures, 0 errors, 0 skipped; build success).

## Requirements and Plan Conformance

- **Implemented:** Complete immutable generations with fresh IDs; detached prepare and atomic activation; whole-set add/remove and invalid-candidate preservation; root capture before conversion/validation; delayed, nested, and parallel binding propagation; generation-scoped authorization/visibility/routing; pending and claimed lifecycle ownership cleanup; immutable catalog views; fixed generation dependencies; closed supported API surface; author guidance; and the testing plan's primary pre/post-activation semantics.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** The testing plan named a separate `SkillGenerationAuthorizationIntegrationTest`; equivalent old-versus-new authorization behavior is covered in `SkillGenerationExecutionIntegrationTest` alongside the exact primary scenario, with existing security suites retained. This is a test organization difference, not a behavioral omission.
- **Compatibility review:** No allowlisted `ai.loomspan.api` type or supported SPI was added, removed, or changed; `RestSkillHandler` remains the sole supported Java SPI. Configuration keys, manifest syntax, serialized session/trace shapes, and Console fixtures remain compatible. Removed registries/registrars are `ai.loomspan.internal` implementation details, and the ticket explicitly authorizes their replacement without shims. `LoomspanPublicSurfaceArchitectureTest`, auto-configuration boundary tests, supported-surface integration, and Console fixture contracts pass.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected
- **Rationale:** Generation capture changes when skill definitions, model routing, authorization, input/output contracts, allowed-skill planning constraints, and REST handler selection become fixed for an execution. The changed guidance accurately describes root snapshot behavior and activation boundaries without exposing internal types as extension APIs.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/references/java-api/java-skills.md`; `agent-skills/loomspan-docs/references/java-api/rest-skills.md`; `agent-skills/loomspan-docs/references/skill-authoring/README.md`; `authorization.md`; `input-contracts.md`; `mental-model.md`; `model-selection-and-connections.md`; `planning-concurrency.md`; `rest-skills.md`
- **Evidence checked:** Matching production capture, binding, catalog, visibility, routing, auto-configuration, and lifecycle source; focused generation/activation tests; supported-surface and Console fixtures; project version `1.0.0-beta.4-SNAPSHOT` matches the documentation skill.
- **Coverage table:** Current
- **LLM-first usability:** Pass

## Residual Risks and Optional Developer Checks

- No required environment or external-service checks remain. All ticket-scoped behavior is internal/in-process and was exercised by focused and full repository verification.
- Optional developer checks: none.

## Disposition

- **Candidate clean; fresh review required** — all three actionable findings were fixed in this context, the final internal re-review found no remaining actionable issue, and the full build passes. A new Step 5 context must independently certify the modified candidate.
