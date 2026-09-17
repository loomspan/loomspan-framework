# Application-Supplied Skills Testing Plan

## Change Summary

Add a supported `SkillDocument` record and `SkillReloader.prepare(Collection<SkillDocument>)` so callers can validate and freeze a complete YAML set already in memory. Preserve configured-resource startup/reload, fixed Java/model/REST bindings, generation capture, and one-shot publication. Project logical source labels as diagnostic text in the existing `sourcePath` inspection field.

## Impacted Areas

- Public API and architecture allowlist: `SkillDocument`, `SkillReloader`, `LoomspanPublicSurfaceArchitectureTest`.
- YAML source loading, parsing, validation, and source diagnostics: `YamlSkillCatalog`, `YamlSkillSource`, `YamlSkillDefinition`, `SkillSourcePathResolver`, `DefaultRegisteredSkillCatalog`.
- Complete generation and publication: `SkillGenerationManager`, `DefaultSkillReloader`.
- Supported integration: `PublicSkillReloadIntegrationTest`, Spring auto-configuration, `SkillTemplate`, `SkillInvocationHandoff`, fixed `RestSkillHandler`.
- Serialized inspection and Console: Java DTO/fixture corpus, Go validator, TypeScript contracts/components/tests, `sourcePath` documentation.
- Consumer guidance: README and checked-out `loomspan-docs` Java API and skill-authoring topics.

## Risk Assessment

- A separate supplied parser could accept YAML that file loading rejects, skip model/child/RBAC validation, or accidentally allow conflicting Java names. Tests must exercise the same executable path and representative invalid declarations.
- Copying too late or storing caller collection references could alter a prepared candidate. Publication must not read caller state or configured resources.
- The public overload might accidentally change no-arg discovery, empty startup, startup catalog immutability, or later alternation back to configured locations.
- Cross-source candidates could bypass the existing base-ID, owner, one-shot, shutdown, and preparation serialization guarantees.
- Physical-path derivation may reject arbitrary labels. A fake URI or filename could distort errors or imply file access. Inspection and Console must treat labels as untrusted text.
- The interface addition is compatibility-sensitive for custom `SkillReloader` implementations, but is explicitly required by the ticket. There is no supported bean-replacement SPI to preserve with a shim. The protected no-arg API and `RestSkillHandler` path must continue to pass.
- The `sourcePath` wire field remains a string, but its value semantics broaden. Java/Console fixtures and consumers must remain coherent; exact release-version rejection must continue to work. No old schema reader or independent protocol marker is introduced.

## Existing Test Coverage

- `SkillReloaderTest` covers detached preparation, foreign/fabricated/stale/repeated candidates, competing publications, overlapping preparation/publication, and shutdown (`src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:29-210`). These currently use only configured resources.
- `SkillGenerationManagerTest` covers fresh IDs, fixed Java/REST bindings, invalid candidate isolation, complete replacement/empty set, and frozen file content (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:37-186`).
- `YamlSkillCatalogTests` covers missing/empty configured roots, REST and model YAML, duplicate declared names, unknown models, fields, schemas, and other authoring rules (`src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:60-1010`).
- `PublicSkillReloadIntegrationTest` covers supported bean use, generation-keyed REST staging, old admitted work, and unchanged injected startup catalog (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:27-105`).
- `SkillSourcePathResolverTest`, `DefaultRegisteredSkillCatalogTest`, `ConsoleRestFixtureCorpusTest`, and Console Go/TS tests cover physical source paths and existing inspection DTO shape. None cover arbitrary supplied labels.
- `LoomspanPublicSurfaceArchitectureTest` asserts the exact API allowlist, sole supported SPI, and no internal types in public signatures. It must be updated for the agreed new type.

## Bug Reproduction / Failing Test First

- **Type**: integration compile failure, followed by a runtime test.
- **Location**: `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java` (or focused companion).
- **Arrange/Act/Assert**: Start the context with an empty configured root and a fixed `RestSkillHandler`; construct `new SkillDocument("opaque label", validRestYaml)`; call `reloader.prepare(List.of(document))`; assert the candidate has the REST skill while the active startup snapshot remains empty, stage `candidate.generationId()`, publish, and invoke through `SkillTemplate`.
- **Expected failure (pre-fix)**: The test does not compile because `SkillDocument` and the overload are absent. After adding signatures but before implementation, it fails because no supplied-content catalog path exists. This is the smallest supported-surface proof that files are not required.

## Tests to Add/Update

### 1) `preparesSuppliedYamlWithoutFilesThroughSupportedApis`

- **Type**: integration.
- **Location**: `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`.
- **What it proves**: Empty configured startup is valid; candidate is detached; a REST document with a logical, extensionless label prepares, stages under the fresh generation ID, publishes, and executes through public APIs. `RestSkillInvocation.generationId()` selects staged configuration. The injected startup catalog stays unchanged.
- **Fixtures/data**: Inline minimal REST YAML and a missing configured root. No skill files.
- **Mocks**: Application-owned in-memory map for REST configuration; no framework bean replacement.
- **Affected surface**: Application API, Supported SPI, Configuration behavior.
- **Compatibility expectation**: Additive public path; preserve no-arg startup and fixed handler.

### 2) `preparesModelAndRestDocumentsWithIdenticalManifestRules`

- **Type**: unit plus integration for one model invocation.
- **Location**: `YamlSkillCatalogTests.java`, `SkillGenerationManagerTest.java`, and a supported integration test using the existing local protocol-compatible model-server pattern from `SupportedSurfaceIntegrationTest`.
- **What it proves**: Supplied model-backed and REST declarations use the same parser, configured model alias, input schema, child references, RBAC roles, and REST field restrictions as discovered YAML. A model-backed entry can execute without a skill file. Unknown model, unknown child, duplicate declared skill name, and invalid REST/model fields fail preparation with useful source and field diagnostics; active ID is unchanged.
- **Fixtures/data**: Existing minimal manifest patterns and named model configuration; local fake protocol response for the integration case.
- **Mocks**: Local protocol-compatible HTTP server for model execution only.
- **Affected surface**: Configuration and manifest behavior, Application API.
- **Compatibility expectation**: Same manifest semantics across source types.

### 3) `rejectsInvalidSuppliedDocumentSetBeforeActivation`

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java` and `SkillReloaderTest.java`.
- **What it proves**: Null collection, null element, null source name, null YAML, blank name, exact duplicate source names, empty/invalid YAML, and duplicate declared skill names are rejected. Distinguish duplicate source identity from duplicate YAML `name`; labels differing only by case are distinct. A corrected candidate can prepare afterward without restarting.
- **Fixtures/data**: Small lists with duplicate labels and valid/invalid inline YAML.
- **Mocks**: Existing catalog/reloader fakes only.
- **Affected surface**: Application API, Internal implementation, Ephemeral diagnostics.
- **Compatibility expectation**: Required new input validation; configured YAML duplicate-name behavior remains.

### 4) `suppliedCandidateFreezesContentAndReplacesCompleteYamlSet`

- **Type**: unit.
- **Location**: `SkillGenerationManagerTest.java`, `SkillReloaderTest.java`.
- **What it proves**: Mutating the caller's collection after `prepare` cannot change candidate catalog, inspection YAML, or publication. Publishing an empty supplied set removes all YAML/REST declarations while fixed Java declarations remain. Repreparing the same strings issues another ID. Configured resources are not read during supplied preparation or publication.
- **Fixtures/data**: Mutable list holding immutable records; file-resource resolver or read counter from existing tests; one fixed Java skill.
- **Mocks**: Existing resource read counter where available.
- **Affected surface**: Application API, Internal implementation.
- **Compatibility expectation**: Complete replacement and immutable candidate; no directory-mode regression.

### 5) `alternatesSuppliedAndConfiguredPreparationWithSharedPublicationRules`

- **Type**: unit/integration.
- **Location**: `SkillReloaderTest.java`, `PublicSkillReloadIntegrationTest.java`.
- **What it proves**: Supplied publication does not change configured locations; later `prepare()` rereads configured YAML. Candidates from opposite methods but the same base compete and exactly one publishes. Foreign, fabricated, stale, repeated, and shutdown-time rejection apply to supplied candidates too. Slow supplied preparation plus concurrent publication cannot return a viable stale candidate; snapshots stay readable.
- **Fixtures/data**: Mutable configured test resource and supplied list with different skill names; latches for concurrency cases.
- **Mocks**: Existing reloader test lifecycle/resource fakes.
- **Affected surface**: Application API, Internal lifecycle.
- **Compatibility expectation**: Existing owner/base/one-shot/admission guarantees shared by both overloads.

### 6) `oldAdmittedTreeKeepsGenerationAfterSuppliedPublication`

- **Type**: integration.
- **Location**: `PublicSkillReloadIntegrationTest.java`.
- **What it proves**: A handed-off REST invocation admitted under A still uses A and its staged configuration after B is published from supplied documents; a new root uses B. This also proves framework publication does not retire application-owned A resources.
- **Fixtures/data**: Existing `SkillInvocationHandoff` and generation-keyed REST map setup.
- **Mocks**: In-memory application REST configuration map.
- **Affected surface**: Application API, Supported SPI, lifecycle.
- **Compatibility expectation**: Preserve captured-generation execution.

### 7) `resubmitsDurableSnapshotAfterOrdinaryRestart`

- **Type**: integration.
- **Location**: `PublicSkillReloadIntegrationTest.java` or focused companion.
- **What it proves**: Two independent Spring contexts each start normally from configured locations, then the application supplies the same saved string documents. Their prepared generation IDs differ; REST configuration is staged under each new ID; publication does not alter configured discovery. No framework persistence is assumed.
- **Fixtures/data**: Application-owned immutable list reused across contexts, empty configured root, REST map reset between contexts.
- **Mocks**: In-memory application snapshot and REST map; no database.
- **Affected surface**: Application API, lifecycle.
- **Compatibility expectation**: Process-local IDs and application-owned restoration.

### 8) `projectsLogicalLabelsWithoutPhysicalPathInterpretation`

- **Type**: unit and serialized-fixture integration.
- **Location**: `SkillSourcePathResolverTest.java`, `DefaultRegisteredSkillCatalogTest.java`, `ConsoleRestFixtureCorpusTest.java`, relevant Console Go service and `SkillDetail.test.tsx` tests.
- **What it proves**: Supplied labels such as `opaque label`, `support/triage.yaml`, and a scheme-like non-path label survive unchanged in `sourcePath`; YAML text is frozen verbatim; parser errors identify the label and field. Configured sources still produce safe relative physical paths. Go accepts nonblank logical labels and rejects missing ones; UI renders label as escaped, non-clickable text with accurate wording. Exact release-string mismatch tests continue to reject unsupported pairs.
- **Fixtures/data**: A Java-generated fixture with an extensionless logical label, consumed by Console fixture tests; existing physical fixtures retained.
- **Mocks**: None.
- **Affected surface**: Persisted or serialized inspection behavior, Ephemeral diagnostics.
- **Compatibility expectation**: Current-version writer/reader/UI coherence; no old-trace or dual-schema reader.

### 9) `publicSurfaceAllowsOnlyAgreedAddition`

- **Type**: architecture.
- **Location**: `LoomspanPublicSurfaceArchitectureTest.java`.
- **What it proves**: Exactly nineteen supported API types include `SkillDocument`; the overload exposes only JDK and allowlisted types; no new SPI or public internal/autoconfigure signature leak is introduced.
- **Fixtures/data**: Updated allowlist and reflection assertions.
- **Mocks**: None.
- **Affected surface**: Application API and Supported SPI.
- **Compatibility expectation**: Exact closed public boundary.

## Authoring Documentation Evidence

- `mental-model.md`: Exact declared skill name and `allowed_skills` remain execution identity and relationships. Prove with model/REST supplied tests that source labels do not alter lookup or child resolution.
- `rest-skills.md`: REST can come from supplied YAML and inspection may show a logical label. Prove with public REST integration and source projection tests.
- `skill-authoring/README.md`: Update coverage notes for source identity and REST diagnostics after tests establish these semantics. The checked-out guide is aligned with current file-only behavior; the planned update prevents new drift.
- Keep application recovery/readiness instructions in `java-api/skill-reload.md`, supported by public integration tests. Do not test prose itself.

## How to Run

- Red test before implementation: `mvn -Dtest=PublicSkillReloadIntegrationTest test` (expected compilation failure for the absent API).
- Focused Java: `mvn -Dtest=YamlSkillCatalogTests,SkillSourcePathResolverTest,DefaultRegisteredSkillCatalogTest,SkillGenerationManagerTest,SkillReloaderTest,PublicSkillReloadIntegrationTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest test`.
- Full Java: `mvn test`.
- Console Go, from `loomspan-console`: `go test ./...`.
- Console web, from `loomspan-console/web`: `npm run typecheck`, `npm test` (the repository's Vitest script runs once), and `npm run build:web` if UI wording/contracts change.
- No external credentials or database are needed. Model execution uses the existing local protocol-compatible test server pattern; tests must not call a live provider.

## Exit Criteria

- [x] The public red test fails before implementation for the missing type/overload, then passes after implementation.
- [x] All focused Java tests, `LoomspanPublicSurfaceArchitectureTest`, and `mvn test` pass.
- [x] Console Go tests and web typecheck/test/build pass for any Console changes; Java-generated fixtures agree with committed JSON.
- [x] Null/blank/duplicate inputs, arbitrary labels, invalid YAML, parser/model/child/RBAC rules, complete replacement, freeze, and empty supplied set are covered.
- [x] Protected configured startup/no-arg preparation, fixed Java/handler bindings, old admitted work, and owner/base/one-shot/shutdown paths pass across source methods.
- [x] Public signatures contain no internal/autoconfigure types and no extra SPI is added; obsolete physical-path assumptions are removed for supplied sources without a fallback loading mode.
- [x] Current-version diagnostic writer, Go reader, UI projector, and exact version check remain coherent; no label is treated as a filesystem locator.
- [x] New authoring guidance is supported by focused executable tests and the coverage table is updated.
- [x] No non-automatable developer check is required for correctness. An application team may optionally verify its own durable snapshot and traffic-readiness sequencing in its environment; that is outside this framework change.
