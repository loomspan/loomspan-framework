# PR 10 Side-Effect-Free Skill Validation Testing Plan

## Change Summary

Add two public `SkillReloader.validate` overloads and immutable, structured diagnostics. Extract common document and whole-set authoring checks so validation gives repeatable feedback while preparation still creates a frozen candidate and publication retains its existing lifecycle rules. The companion implementation plan classifies the additions as Application API, existing YAML/configuration behavior as protected, diagnostic result shape as current-run feedback, and manager/catalog classes as internal.

## Impacted Areas

- `src/main/java/ai/loomspan/api/SkillReloader.java`, `SkillValidationResult.java`, `SkillValidationIssue.java` — public signatures, immutable result/issue contract.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java` — configured/supplied document reads, parser errors, warnings, independent-document collection.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java` — fixed-declaration checks, conflicts, references, handler names, ID issuance and binding separation.
- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java` — validation entry points versus preparation/publication lifecycle.
- `README.md`, `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, and authoring workflow/index — supported usage claims.

## Risk Assessment

- **Primary side-effect risk**: validation accidentally calls `active()`, `initializeFixedDependencies()`, `javaSkills.capabilities()` before startup, `getBean`, ID increment, or candidate/publication code (`SkillGenerationManager.java:65-138,248-271`; `SkillMethodBeanPostProcessor.java:56-99`). Verify before and after normal startup where practical; premature calls must fail without mutation rather than return an incomplete valid result.
- **Parity risk**: validation and preparation disagree on model/REST/schema or complete-set rules. Run the same input corpus through both and compare validation errors with preparation failure; preserve `SkillReloadException` cause/message behavior for preparation.
- **Diagnostics risk**: source, skill, or path is lost when Jackson mapping fails; warnings still log during repeated validation; a failed document produces misleading missing-child issues. Assert exact metadata and order, but avoid brittle full prose comparison except for protected preparation messages.
- **Input risk**: collection null/element null, blank/exact-duplicate labels, null YAML, duplicate callable names, empty set, labels resembling paths, configured resource changes.
- **Compatibility risk**: candidate owner, stale base, one-shot publish, shutdown rejection, process-local IDs, retirement notification, frozen bytes, and fixed REST handler reuse are protected paths (`README.md:224-226`; existing reloader/manager tests). No intentionally removed supported path. Internal helper tests may be updated to follow the refactor rather than preserve obsolete internals.
- **Documentation claims**: the new authoring topic must accurately distinguish complete-set authoring diagnostics from execution testing, and validation feedback from preparation/publication authorization. Existing manifest guidance is aligned with executable evidence; the new workflow requires focused new tests.

## Existing Test Coverage

- `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java` covers source labels, malformed YAML, model/REST failures, schema warnings, and corrected reload; current warnings are asserted as log output (`:36-75,912-935`).
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java` covers complete replacement, ID freshness, empty sets, frozen bytes, and handler reuse (`:36-75,143-176,205-226`).
- `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java` covers candidate owner/base/one-shot, shutdown, and retirement (`:190-227` and adjacent cases).
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` enforces the closed API and signature package boundary (`:29-49,349-366,520`).
- `PublicSkillReloadIntegrationTest` exercises framework wiring, configured and supplied publication, REST routing, and immutable startup snapshot. None currently covers public skill-set validation.

## Bug Reproduction / Failing Test First

- **Type**: unit plus API compile boundary.
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java` (and a tiny use of the new public signature in `LoomspanPublicSurfaceArchitectureTest`).
- **Arrange/Act/Assert**: Construct a reloader with a manager whose active generation and fixed caches are observable; call `validate(List.of(valid SkillDocument))` twice, including a REST declaration. Assert both results valid, identical diagnostics, unchanged active ID and next preparation ordinal, no REST bean construction, no retirement callback, and an already prepared candidate remains publishable.
- **Expected pre-fix failure**: the `validate` method and result types do not compile. A temporary call to `prepare` would compile but consume IDs and bind the REST handler; this is the behavioral red condition. Commit a real public test against the requested API rather than retaining the temporary call.

## Tests to Add/Update

### 1) `validatesIndependentDocumentsAndReturnsStructuredIssues`

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`.
- **What it proves**: an invalid YAML document, a separate bad model document, and a valid document yield independently attributed errors while the valid definition survives for meaningful full-set checks; malformed-field issues identify source, optional skill, and field path. Result list order follows document order. Do not require exhaustive errors inside one document.
- **Fixtures/data**: inline `SkillDocument` values adapted from current valid/invalid fixture syntax.
- **Mocks**: existing catalog test properties/resolver.
- **Affected surface**: Configuration or manifest behavior; Ephemeral diagnostics.
- **Compatibility expectation**: protected authoring rules; current-run diagnostic coherence.

### 2) `returnsSchemaComplexityWarningsWithoutValidationLogNoise`

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`.
- **What it proves**: depth, property-count, required-count, and object-array warnings appear as `WARNING` with source/skill/path; no `ERROR` means `valid() == true`; two validation calls do not emit authoring warning logs. Preserve the existing preparation warning behavior if it remains intended.
- **Fixtures/data**: existing complex supported output schema fixture or the inline case at `YamlSkillCatalogTests.java:912-935`.
- **Mocks**: test log appender only for absence/preservation checks.
- **Affected surface**: Ephemeral diagnostics; skill-authoring guidance.
- **Compatibility expectation**: current-run diagnostic coherence.

### 3) `validatesSuppliedSourceLabelsAndCompleteEmptySet`

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java`.
- **What it proves**: blank/duplicate labels, null elements, and null YAML produce actionable issues with known source sentinel where needed; case-different labels are distinct; a null collection remains a caller contract failure; empty collection is valid and retains fixed Java skills when subsequently prepared. A label that looks like a path is never opened.
- **Fixtures/data**: inline complete sets and a nonexistent/poison configured location.
- **Mocks**: resource resolver/manager as needed, with read counters.
- **Affected surface**: Application API; Configuration or manifest behavior.
- **Compatibility expectation**: existing supplied-document semantics protected.

### 4) `validationMatchesPreparationForWholeSetRules`

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`.
- **What it proves**: duplicate YAML names, YAML/Java name conflict, unknown child, bad YAML input contract, zero/multiple handler declarations with REST skills, and no-REST handler indifference are found by validation; each same input still fails or succeeds correspondingly in `prepare`. If a document failed parsing, suppress only child-reference errors whose target could be the failed document, while retaining provable independent errors.
- **Fixtures/data**: existing manager test manifests and a fixed Java capability.
- **Mocks**: `SkillMethodBeanPostProcessor`, `ListableBeanFactory`, and input resolver; verify handler bean lookup by name is non-eager and `getBean` occurs only during successful preparation requiring REST.
- **Affected surface**: Supported SPI; Configuration or manifest behavior; Internal implementation.
- **Compatibility expectation**: protected preparation behavior and handler declaration rule.

### 5) `repeatedValidationDoesNotChangeGenerationOrCandidateState`

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java` and `SkillGenerationManagerTest.java`.
- **What it proves**: successful and invalid repeated validation consume no generation ordinal; do not activate or replace active generation, update fixed caches, construct/invoke REST handlers, invoke model/skills, or select retirement callbacks. A candidate prepared before validation remains publishable with the same base. A validation call before startup initialization rejects without activating or priming caches.
- **Fixtures/data**: valid REST, invalid schema, existing prepared candidate; consecutive preparations around validations to observe ordinal continuity.
- **Mocks**: spy manager/bean factory, constructor counter for prototype REST handler, retirement listener counter.
- **Affected surface**: Application API; Supported SPI; Internal implementation.
- **Compatibility expectation**: new no-state guarantee plus protected candidate/publication behavior.

### 6) `validationRereadsConfiguredInputButNeverReadsSuppliedLabels`

- **Type**: integration.
- **Location**: `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java` (use actual package/path of existing class).
- **What it proves**: `validate()` observes changed configured resource contents on a later call; `validate(documents)` uses only in-memory YAML and neither reads nor writes configured files; both report the same manifest rules. The `prepare()` following a good validation sees later bad edits and fails, while a candidate prepared before an edit publishes its frozen definition.
- **Fixtures/data**: temporary configured YAML resource; complete supplied set with path-looking labels.
- **Mocks**: real Spring context where feasible; filesystem timestamps/content assertions.
- **Affected surface**: Application API; Configuration or manifest behavior.
- **Compatibility expectation**: protected configured/supplied workflows; new advisory behavior.

### 7) `publicValidationSurfaceIsClosedAndImmutable`

- **Type**: architecture/unit.
- **Location**: `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` and a focused result-value test near `SkillReloaderTest`.
- **What it proves**: only deliberate result/issue top-level types join the allowlist; no signature leaks `internal` or `autoconfigure`; no new SPI/bean override point; result makes a defensive immutable issue list and immutable issue fields; errors invalidate, warnings alone do not.
- **Fixtures/data**: constructed issue list and reflection/ArchUnit scan.
- **Mocks**: none.
- **Affected surface**: Application API; Supported SPI.
- **Compatibility expectation**: additive closed public boundary.

### 8) `preparationAndPublicationRegression`

- **Type**: unit/integration.
- **Location**: existing `SkillReloaderTest`, `SkillGenerationManagerTest`, `PublicSkillReloadIntegrationTest`.
- **What it proves**: operational preparation failures still become `SkillReloadException` with cause; successful prepare freezes its bytes and gets a fresh process-local ID; publish rejects foreign, repeated, stale, and shutdown-time candidates; retirement and REST routing remain generation scoped.
- **Fixtures/data**: existing test setups; extend only where refactor changes assertions.
- **Mocks**: existing lifecycle and handler fakes.
- **Affected surface**: Application API; Supported SPI; Configuration or manifest behavior.
- **Compatibility expectation**: protected path. No old/new dual behavior or shim is authorized.

## How to Run

- Red test: `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillReloaderTest test` (expected compile failure before API implementation; record as red).
- Focused catalog and manager: `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=YamlSkillCatalogTests,SkillGenerationManagerTest,SkillReloaderTest test`.
- Public integration and architecture: `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=PublicSkillReloadIntegrationTest,LoomspanPublicSurfaceArchitectureTest test`.
- Full verification: `./mvnw.cmd --batch-mode --no-transfer-progress clean verify`.
- No external service credentials or profiles should be required for these test doubles and local resources.

## Exit Criteria

- [ ] The new public red test fails before implementation for the missing API, then passes after it.
- [ ] Both overloads return correct immutable error/warning results; independent documents are reported and downstream noise is suppressed.
- [ ] Same authoring/whole-set inputs yield matching validation and preparation decisions, while preparation still wraps failures and freezes its own input.
- [ ] Repeated validation has no generation, cache, handler, active, candidate, or retirement effects; configured rereads and supplied isolation pass.
- [ ] Protected prepare/publish, `RestSkillHandler`, configuration, and manifest tests pass.
- [ ] The architecture allowlist/signature test and full `clean verify` pass.
- [ ] Each new authoring guidance claim has focused source/test evidence; the authoring index accurately labels focused workflow coverage.
- [ ] No optional manual observation is needed as a correctness gate.
