# PR 6.2 public skill reload — independent review 2

## Code Review Findings

### [P3] Directly verify both coordination boundaries

- **Location:** `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:70`
- **Evidence:** The candidate's tests covered preparation/publication overlap, two competing publications, and shutdown during resource reading, but did not directly assert that two preparations cannot read concurrently. Nor did they hold the publication gate while shutdown attempted to close admission. The ticket explicitly requires controlled concurrency verification of serialization and the activation/shutdown boundary.
- **Trigger:** A future change narrows or removes the preparation monitor, or moves activation outside the lifecycle monitor while leaving the previously tested interleavings intact.
- **Impact:** A concurrency regression could satisfy the existing focused suite despite violating the supported update contract.
- **Recommendation:** Add controlled tests for a blocked first preparation and competing second preparation, and for shutdown attempting closure during an in-progress activation gate.

## Findings Resolved in This Context

- **P3 — coordination-boundary verification:** Added `SkillReloaderTest#preparationsSerializeWithoutBlockingSnapshots` and `FrameworkExecutionLifecycleTest#updateActivationGateAndShutdownClosureHaveOneOrdering`. The former verifies the second caller blocks while the first candidate reads and snapshots remain usable; the latter verifies shutdown cannot close admission inside an already-entered activation gate, that activation succeeds first, and that later activation is rejected. Focused tests pass (26 tests, no failures). The final internal re-review found no additional actionable issue.

## Open Questions and Assumptions

- None that affects this change. The initial release is still the checked-out `1.0.0-beta.4-SNAPSHOT`; the coordinated diagnostic field change uses the current-format policy rather than a legacy reader.

## Verification Results

- PASS — `.\mvnw.cmd '-Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest,SkillReloaderTest,PublicSkillReloadIntegrationTest,SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest' test` (31 tests, before the two additional coordination tests).
- PASS — `.\mvnw.cmd '-Dtest=ObservabilityRestIntegrationTest,ObservabilityDtoMapperTest,LiveActivityProjectorTest,ExecutionTraceHandleTest,ConsoleTraceFixtureCorpusTest,ConsoleRestFixtureCorpusTest' test` (61 tests).
- PASS — `.\mvnw.cmd test` (1,142 tests, before the two additional coordination tests).
- PASS — `go test ./...` from `loomspan-console`.
- PASS — `npm run typecheck` from `loomspan-console/web`.
- PASS — `npm test -- --run` from `loomspan-console/web` (47 files, 521 tests).
- PASS — `npm run build:web` from `loomspan-console/web`.
- PASS — `.\mvnw.cmd '-Dtest=SkillReloaderTest,FrameworkExecutionLifecycleTest' test` (26 tests, after the final test edit).
- PASS — `git diff --check` (line-ending normalization warnings only).
- FAIL — `.\mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest,SkillReloaderTest,PublicSkillReloadIntegrationTest,SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest test`: PowerShell parsed unquoted commas as separators before Maven ran; corrected in the passing quoted command above.
- NOT RUN — `npm run test:e2e`: no changed browser interaction needs an end-to-end browser assertion beyond the typecheck, component suite, and production web build; browser installation was not established.

## Requirements and Plan Conformance

- **Implemented:** The closed API now includes the one `SkillReloader`, opaque `PreparedSkillUpdate`, and unchecked `SkillReloadException`, with IDs on catalogs and REST invocations. `LoomspanPublicSurfaceArchitectureTest` passes and the auto-configuration supplies one framework-owned bean without a replacement hook. `RestSkillHandler` remains the sole supported SPI.
- **Implemented:** `SkillGenerationManager` reuses full candidate assembly and validation, issues fresh IDs, freezes catalogs/definitions, and closes REST invokers over the candidate ID. The reloader holds only the expected base ID, uses distinct preparation/publication locks, checks ownership and one-shot state, and activates inside `FrameworkExecutionLifecycle.whileAdmissionOpen`. Snapshot and publication do not read YAML. Focused manager/reloader and public integration tests cover detached preparation, frozen publication after invalid file replacement, changed/empty YAML, distinct same-content IDs, foreign/fabricated/stale/repeated rejection, shutdown, and delayed REST handoff.
- **Implemented:** Existing generation capture through root preparation, handoff, bindings, nested/parallel execution, and release remains in place. `LoomspanSessionRunner` supplies its captured generation ID to session construction. The trace writer, live projection, finalized trace catalog, REST diagnostics, Go processor/acquisition/MCP/browser projections, TypeScript contracts, and current fixtures carry that ID. Discovery captures one active registered-skill catalog per REST operation. Focused fixtures and suites pass; no historical catalog or external-retirement API appeared.
- **Implemented:** README and version-aligned Java API/skill-authoring topics describe startup readiness, prepare → stage → publish, fixed generation-keyed REST handler configuration, stale/repeated/foreign/shutdown errors, fresh IDs, file stability, empty deletion, immutable snapshots, and application-owned retention. Skill-authoring routing and coverage remain coherent.
- **Partial/Missing:** None identified after the coordination test additions. The testing plan's proposed red-first compile evidence is historical implementation-process evidence, not a current merge-time contract and cannot be reconstructed from this review checkout.
- **Safe deviations:** The internal trace/session test constructors still synthesize an ID for direct standalone test construction; production `LoomspanSessionRunner` always passes the captured generation. These internal signatures are not supported API and are not runtime fallback readers.
- **Compatibility review:** `SkillCatalog` and `RestSkillInvocation` are deliberately supported Application API, and their direct signature changes are authorized by the ticket's Pipeline notes with an explicit no-shim decision. The three new top-level API types are allowlisted; no internal type leaks through API signatures. YAML/property syntax and fixed Java/model/REST dependency rules remain unchanged. Diagnostic formats are current-run ephemeral formats: Java/Go/TypeScript consumers and fixtures were updated together, with exact release-string validation retained and no legacy reader. The existing snapshot release marker remains project-derived for this unreleased development change.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected; aligned after the update.
- **Rationale:** Skill authors now need the public activation mental model, captured REST generation metadata, and trace generation interpretation, but no new manifest syntax. Application staging and safe-retention responsibilities belong in the Java API topic.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `mental-model.md`, `rest-skills.md`, `traces-and-debugging.md`, `source-verification.md`; Java API `README.md`, `catalog-and-validation.md`, `rest-skills.md`, `compatibility-and-boundaries.md`, and new `skill-reload.md`; repository `README.md`.
- **Evidence checked:** `SkillGenerationManager`, `DefaultSkillReloader`, `LoomspanSessionRunner`, `DefaultExecutionTraceHandle`, `LiveActivityProjector`, focused Java tests, Go processor/fixture tests, and TypeScript component checks.
- **Coverage table:** Current. Reload routing and mental-model/REST coverage descriptions match the implemented contract; no new YAML syntax is claimed.
- **LLM-first usability:** Pass. The Java API topic owns the update sequence and limitations; authoring topics link to it for details and keep their local execution, REST, and diagnostic claims concise. The repository-local knowledge base was used directly because no `loomspan-docs` skill was exposed in this review context.

## Residual Risks and Optional Developer Checks

- The repository cannot prove a consuming application's external configuration store keeps old-generation entries long enough. In a production-like application, optionally observe initial readiness gating and retention while a delayed old invocation finishes after publication. This is application-owned and nonblocking for the framework change.
- The full Maven suite ran before the two final test-only additions; the changed test classes passed after the edit. No production source changed in this review context.

## Disposition

**Candidate clean; fresh review required.** One P3 verification gap was fixed in this context, with no open findings after internal re-review. Because this context changed implementation artifacts (tests), the pipeline requires another fresh Step 5 review.
