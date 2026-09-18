# PR 10.1 Validation Candidate Metadata Testing Plan

## Change Summary

The two `SkillReloader.validate` overloads gain a complete, immutable, exact-name-sorted `skills()` projection of a successfully checked proposal. Entries expose only `ValidatedSkill.name()` and public `SkillKind`. Warnings retain metadata; any error preserves diagnostics and returns no metadata. `valid()` remains the no-`ERROR` test. The supported `SkillValidationResult` record constructor changes intentionally without a shim.

## Impacted Areas

- Public values: `src/main/java/ai/loomspan/api/SkillValidationResult.java`, new `ValidatedSkill.java`.
- Shared check and validation-only projection: `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`.
- Parser's obsolete partial conversion: `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`.
- Existing focused tests: `SkillGenerationManagerTest`, `SkillReloaderTest`, `YamlSkillCatalogTests`, `PublicSkillReloadIntegrationTest`, `LoomspanPublicSurfaceArchitectureTest`.
- Supported documentation: root README, Java API knowledge set, skill-authoring validation workflow and coverage index.

## Risk Assessment

- **Completeness and identity:** Projection must use checked callable names, not source labels or the active catalog, and include fixed Java with YAML/REST. Supplied input replaces all YAML/REST; equivalent configured/supplied inputs have equivalent names/kinds.
- **Failure semantics:** Parser failures can leave some successfully parsed definitions. Name conflicts, duplicate YAML names, invalid input contracts, unknown children, and REST-handler declaration errors must all yield no metadata and retain useful issues. An empty valid list and an empty error list require `valid()` to distinguish them.
- **Isolation:** Validation cannot issue an ID, instantiate/invoke a REST handler, touch fixed caches, activate a generation, notify retirement, or change a prepared candidate's publishability. Earlier result lists cannot change after later validation, preparation, publication, or caller list edits.
- **Ordering and value contract:** Natural `String` ordering is case-sensitive; no silent deduplication; both collections and `ValidatedSkill` entries are immutable.
- **Compatibility:** Application API record-shape break is expressly authorized in `Pipeline notes`; do not retain the old constructor. Preserve the `SkillReloader` method signatures and `RestSkillHandler` SPI. Existing YAML/properties and preparation/publication semantics remain protected. Parser `CheckedDocuments.result()` is an internal obsolete path to remove. There is no persisted/serialized or ephemeral trace format change.
- **Authoring claims requiring evidence:** The updated `validation-workflow.md` must state complete-draft validation, valid-first metadata use, warning/error behavior, fixed Java retention, and advisory preparation limits based on the focused manager and public integration tests. Current guidance is aligned for the old feedback shape; Java API supported-type counts are existing documentation drift to correct.

## Existing Test Coverage

- `SkillGenerationManagerTest.validationIsRepeatableAndLeavesIdsHandlersAndFixedSnapshotUntouched` already checks startup precondition, ID continuity, fixed capability caching, and REST bean construction (`SkillGenerationManagerTest.java:39-69`).
- Its conflict/reference, REST handler-count, empty-supplied, and configured/supplied tests cover the checker and preparation paths (`SkillGenerationManagerTest.java:73-180`).
- `SkillReloaderTest.validationValuesAreImmutableAndPreparedCandidateRetainsItsBase` covers issue detachment and prepared-candidate publishability but constructs the old one-argument result (`SkillReloaderTest.java:33-55`).
- `YamlSkillCatalogTests` currently call the parser's `result()` method to check diagnostics and warning silence (`YamlSkillCatalogTests.java:37-77`); those tests need internal `issues()` assertions.
- `PublicSkillReloadIntegrationTest` verifies supported validation/prepare/publish composition, but does not extract proposed REST names from validation (`PublicSkillReloadIntegrationTest.java:36-70`).
- `LoomspanPublicSurfaceArchitectureTest` closes the API set and checks public signature boundaries (`LoomspanPublicSurfaceArchitectureTest.java:27-50,317-333`).

## Bug Reproduction / Failing Test First

This is an additive feature with an intentionally changed public record, not a pre-existing wrong-result bug. First add `SkillGenerationManagerTest.validationReturnsCompleteSortedKindsForProposedCandidate`: arrange a fixed Java capability, a supplied model-backed YAML declaration, and a REST declaration with one declared handler; call `validate(supplied)`. Assert `skills()` equals exact-name-sorted `ValidatedSkill` values for `JAVA`, `YAML`, and `REST` and differs from the active snapshot. **Pre-fix failure:** the test does not compile because `ValidatedSkill` and `SkillValidationResult.skills()` do not exist. After the API compiles, it fails if projection remains absent or partial. Use the existing manager helper and `SkillDocument` values, not mocks of the checker.

## Tests to Add/Update

### 1) Complete proposed candidate, exact identities, kinds, and ordering

- **Type / location:** Unit, `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`.
- **What it proves:** Mixed fixed Java, model-backed YAML, and REST result uses exact callable names, correct public kinds, unique entries, and natural case-sensitive order; active catalog still describes the old generation. Include names whose case distinguishes lexical ordering and source labels unlike callable names. Repeat with a supplied replacement that removes one declaration and changes one name's kind; previous result is unchanged.
- **Fixtures/data:** Existing `javaSkill` helper, supplied YAML strings, one REST handler bean declaration. Use existing model test properties or equivalent manager fixture so model-backed YAML checks validly.
- **Mocks:** Existing mocked `SkillMethodBeanPostProcessor`; handler bean supplier records construction but returns a no-op handler.
- **Affected surface / expectation:** Application API, configuration/manifest semantics, internal projection; protected checker behavior with the new result contract.

### 2) Warning-only, parser errors, duplicate names, and framework conflicts

- **Type / location:** Unit, `SkillGenerationManagerTest.java` and `YamlSkillCatalogTests.java`.
- **What it proves:** A warning-only checked result has `valid() == true` and complete metadata; malformed YAML, two documents with the same callable name, Java/YAML conflict, unknown child, invalid input contract, and invalid REST-handler declaration each produce `valid() == false`, useful issue source/path/message, and `skills().isEmpty()`. Include a good document beside a bad one to prove no partial metadata. Keep configured discovery-failure diagnostic checks where already covered.
- **Fixtures/data:** Existing output-schema-complex fixture and model properties for warning case; existing malformed, conflict, child-reference, REST declaration inputs. Reuse fixtures rather than duplicate large YAML.
- **Mocks:** Existing Java capability and bean-factory test fixtures only.
- **Affected surface / expectation:** Application API and configuration/manifest behavior; protected diagnostics, current result coherence, no partial candidate.

### 3) Empty supplied and configured/supplied parity

- **Type / location:** Unit, `SkillGenerationManagerTest.java`.
- **What it proves:** `validate(List.of())` returns only fixed Java metadata, and returns a valid empty list when no Java skills exist. For equivalent configured file and supplied string, both overloads yield the same names/kinds despite different source labels. A configured file edit changes only later configured results. No result is inferred from the active catalog.
- **Fixtures/data:** `@TempDir` configured YAML file, corresponding `SkillDocument`, separate managers with zero and one fixed Java capability.
- **Mocks:** Existing Java processor mock.
- **Affected surface / expectation:** Application API plus protected configured/supplied contract.

### 4) Public value immutability and error invariant

- **Type / location:** Unit, `SkillReloaderTest.java` (or focused API value test beside it).
- **What it proves:** Mutable source lists for issues and skills can be changed afterward without changing the result; both accessor lists reject mutation; `ValidatedSkill` validates nonblank name and nonnull kind and has no mutable nested value; warning-only result remains valid; an error result has empty metadata and unchanged issues. Update every direct constructor call to the new canonical shape, with no old overload.
- **Fixtures/data:** Mutable `ArrayList<SkillValidationIssue>` and `ArrayList<ValidatedSkill>`; warning and error issue values.
- **Mocks:** None.
- **Affected surface / expectation:** Application API; intentional old-constructor removal, new protected immutable value behavior.

### 5) Validation lifecycle isolation and direct preparation

- **Type / location:** Unit, `SkillGenerationManagerTest.java`, `SkillReloaderTest.java`.
- **What it proves:** Repeated validation leaves active ID/catalog, next generation ordinal, fixed processor call count, prototype handler construction and invocation counts, and retirement callback count unchanged. A candidate prepared before validation still publishes when no other publication intervenes. Preparation/publication without prior validation still succeeds and checks current input; a changed/invalid input after validation can fail preparation. A source/code-path assertion or review establishes that `prepareCatalog` never calls the public metadata projection (avoid a test that mirrors private implementation).
- **Fixtures/data:** Existing prototype handler, `AtomicInteger` counters, prepared candidate, retirement listener, changing in-memory/configured document inputs.
- **Mocks:** Existing Java processor mock and bean factory; no external services or model invocation.
- **Affected surface / expectation:** Application API and internal lifecycle; protected preparation/publication and advisory validation paths.

### 6) Public-only REST-name extraction example

- **Type / location:** Integration, `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`.
- **What it proves:** An application using supported `SkillReloader`, `SkillDocument`, `SkillValidationResult`, `ValidatedSkill`, and `SkillKind` checks `valid()`, filters `skills()` to `REST`, and extracts exact names for application route comparison. The active snapshot remains unchanged until preparation/publication; the prepared candidate's snapshot is authoritative for resource staging. A warning/error check may be added if this fixture naturally supports it, while focused tests own the full matrix.
- **Fixtures/data:** Existing `ApplicationContextRunner`, `RestConfiguration` handler bean, supplied REST YAML with an opaque diagnostic source label.
- **Mocks:** No internal framework bean replacement; existing application handler only.
- **Affected surface / expectation:** Supported Application API; new consumer-visible behavior and unchanged lifecycle.

### 7) Public boundary and obsolete parser conversion

- **Type / location:** Architecture/unit, `LoomspanPublicSurfaceArchitectureTest.java`, `YamlSkillCatalogTests.java`.
- **What it proves:** `ValidatedSkill` is deliberately allowlisted; existing reflection/ArchUnit checks find no leaked `internal`/`autoconfigure` type or additional SPI. Parser tests inspect `CheckedDocuments.issues()` directly and do not require the removed incomplete public-result conversion. A repository search confirms no old result constructor or parser conversion remains.
- **Fixtures/data:** Existing architecture importer and parser fixtures.
- **Mocks:** None.
- **Affected surface / expectation:** Application API and obsolete internal implementation; intentional removal authorized by the ticket.

## How to Run

Run on the repository's Java 21/Maven wrapper toolchain; no external service or credentials are required for these focused tests. On Windows PowerShell:

1. Add the mixed-candidate test and run `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillGenerationManagerTest test` before implementation; record the expected compile failure, then implement.
2. Run `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillGenerationManagerTest,SkillReloaderTest,YamlSkillCatalogTests,PublicSkillReloadIntegrationTest,LoomspanPublicSurfaceArchitectureTest test`.
3. Run `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` for required framework checks.
4. Run `.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease -DskipTests -Dgpg.skip=true verify`, matching the README's release-profile check.
5. After verified tests, run `.\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests install` and check that the installed artifact is `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.5-SNAPSHOT`.

The Java-to-Go Console boundary is untouched, so no Go fixture or Console verification is required for this ticket.

## Exit Criteria

- [ ] The first mixed-candidate test is red before implementation and passes afterward.
- [ ] All focused and full framework checks pass, including `LoomspanPublicSurfaceArchitectureTest`; the beta.5 snapshot installs locally.
- [ ] Mixed, warning, error, empty, replacement, configured/supplied parity, exact ordering, uniqueness, and detachment cases are covered with executable assertions.
- [ ] Every error category in the focused matrix retains useful issues and returns no candidate metadata; valid empty is distinguished by `valid()`.
- [ ] Lifecycle assertions show validation neither prepares nor publishes, and direct preparation/publication remains functional; source review confirms no unused public projection in preparation.
- [ ] The supported API allowlist and docs match the new 22-type surface; no one-argument constructor or parser-level partial conversion remains.
- [ ] Updated authoring and Java API guidance is backed by the focused tests, routed clearly, and states that the prepared snapshot is authoritative for staging.
- [ ] No optional non-automatable developer checks are required for this change.
