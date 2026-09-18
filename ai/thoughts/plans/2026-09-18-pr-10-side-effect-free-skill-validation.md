# PR 10 Side-Effect-Free Skill Validation Implementation Plan

## Overview

Add `SkillReloader.validate()` and `validate(Collection<SkillDocument>)` for repeatable editor feedback. A shared internal authoring check produces validated, frozen definitions and structured diagnostics. Only `prepare` binds handlers, issues a generation ID, and creates a candidate.

## Current State Analysis

`DefaultSkillReloader` exposes only preparation/publication and preparation first calls `generations.active()` (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:33-59,98-105`). `SkillGenerationManager.prepareCatalog` increments the ID before complete-set checks and resolves a REST handler bean (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:99-138,257-271`). `YamlSkillCatalog` parses one document at a time and throws on the first failure; complexity warnings go straight to the log (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:104-165,1021-1046`).

`SkillMethodBeanPostProcessor.capabilities()` itself calls `completeDiscovery()`, which can obtain Spring beans (`src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:56-99`). Validation must use the fixed snapshot established during normal startup, not call this method or `active()` as a shortcut. `SkillGenerationManager.afterSingletonsInstantiated()` establishes that snapshot before activating startup generation (`SkillGenerationManager.java:65-80,248-255`). A premature call before this initialization should fail explicitly without mutating startup state; it must not return an incomplete success result.

## Desired End State

Either validation overload returns an immutable result with `valid()` and immutable `issues()`. An issue has `ERROR` or `WARNING`, source name, nullable skill name and field path, and a useful message. Warning-only input is valid. Repeated validation parses fresh input and leaves active generation, candidate eligibility, generation counter, fixed dependency caches, REST handler instances, and retirement callbacks untouched. `prepare` runs the same checks on its own current input and uses those definitions without rereading source bytes. Existing preparation failure wrapping and publication behavior remain.

### Key Discoveries

- Supplied source labels are prechecked, are exact-unique diagnostic text, and never resolved as paths (`YamlSkillCatalog.java:118-150`).
- Model, REST, schema, and evidence checks already converge in `loadDefinition` (`YamlSkillCatalog.java:223-280`); extracting their diagnostic sink avoids a second rulebook.
- Java/YAML conflicts and child references are currently checked during generation construction (`SkillGenerationManager.java:99-138`), so these checks must move ahead of handler binding and ID issuance.
- The supported Java surface is a closed allowlist (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-49`).

## What We're NOT Doing

- No partial-document merge with the active YAML set, editor UI, runtime invocation, remote connectivity probe, validation-result publication, or new bean replacement point.
- No guarantee that successful validation guarantees later `prepare` or `publish`: files, configuration, bean construction, lifecycle, and active base can change.
- No compatibility shim for internal implementation classes.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: Manifest rules do not change, but skill authors gain a new way to validate complete drafts and consume warnings before staging an update. The current authoring index says testing skill trees is not documented (`agent-skills/loomspan-docs/references/skill-authoring/README.md:71`).
- **Documents to update**: Add a focused `agent-skills/loomspan-docs/references/skill-authoring/validation-workflow.md`; route it and mark focused coverage in the authoring `README.md`. Update `agent-skills/loomspan-docs/references/java-api/skill-reload.md` for application integration. Update repository `README.md` for the supported API.
- **Supporting evidence**: New `SkillReloaderTest`, `SkillGenerationManagerTest`, `YamlSkillCatalogTests`, public integration tests, and `LoomspanPublicSurfaceArchitectureTest`, alongside the shared production path. Existing model, REST, output-contract, and identity guidance is aligned with current source and tests; absence of the new API is expected, not documentation drift.
- **Coverage table update**: Required. Change only the testing/validation workflow entry; do not claim runtime skill testing is comprehensively documented.
- **LLM-first usability**: Route “validate a draft skill set” to a short topic distinguishing enforced complete-set rules, warnings, and limitations. Link to Java API integration and existing manifest topics rather than copying their rules. State that validation is advisory and cannot test execution or authorize publication.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add overloads to allowlisted `SkillReloader` and two allowlisted result/issue types (`SkillReloader.java:7-29`; architecture test). | Additive only; immutable record-like values and public signatures without internal types. |
| Supported SPI | `RestSkillHandler` remains sole SPI (`AGENTS.md`; `README.md:192`). | Inspect bean names without creating handler during validation; preserve construction and invocation during preparation/runtime. |
| Configuration and manifest contracts | Existing locations, labels, schemas, model aliases, Java declarations, REST restrictions, and empty-set behavior (`YamlSkillCatalog.java:104-165,223-280`; `README.md:224-226,423-506`). | Reuse rules; no syntax break; configured validation rereads sources each call. |
| Persisted or serialized contracts | No result wire/persistence format; `SkillDocument` is application-held (`README.md:226-234`). | No migration or protocol change. |
| Ephemeral diagnostic formats | Existing errors and warnings are exception/log text (`YamlSkillCatalog.java:1021-1094`). | Return structured current-run issues with accurate source/skill/path, deterministic ordering, and no YAML body/secret echo; retain preparation exception semantics. |
| Internal or accidentally exposed implementation | Catalog, manager, and reloader internals change (`src/main/java/ai/loomspan/internal/skill/`). | Refactor atomically; no shim based on Java visibility. |

- **Evidence of supported contracts**: `AGENTS.md`, `README.md:192,199-234`, and the architecture allowlist; tests establish current behavior, not extra public promises.
- **Intentional compatibility changes**: None. Prepare/publish behavior, `SkillReloadException` wrapping, candidate freezing, ID identity, ownership, staleness, shutdown, and retirement are protected.
- **In-repository consumers to update**: `SkillReloader` implementation, manager/catalog tests, public integration/architecture tests, README, and routed skill documentation.
- **Public-surface delta**: Two `SkillReloader` methods and top-level `SkillValidationResult` / `SkillValidationIssue`. Put `Severity` as a nested enum on the issue, so no third top-level API type is added. No constructor or Spring extension-point addition.
- **Shim decision**: **No shim.** Existing supported methods remain; internal refactors are not protected API.
- **Java-to-Go boundary coordination**: **Not applicable.** No Console REST/SSE or NDJSON boundary changes.
- **Pipeline notes alignment**: **No notes.** The ticket authorizes only additive API and no compatibility break.

## Implementation Approach

Use a per-call internal validation context: collected issues, validated definitions, and an exact set of successfully parsed names. Keep the existing parser/schema functions as the single rule owners. Replace their string-only invalid-content exceptions with an internal typed exception carrying source, optional skill, and path while preserving the current human-readable preparation message; the catalog's validation mode catches that exception per document and continues. Retain collection-level prechecks, but return corresponding issues for editor validation. Treat null collection as a caller contract error rather than a malformed document; use a synthetic source label such as `<documents>` for null elements/blank labels, and `<configured-resources>` for discovery failures. These sentinels must be documented. Catch only expected authoring/discovery failures; do not disguise programming faults as invalid content.

After parsing, run shared complete-set checks over fixed Java capability metadata and valid YAML definitions: exact-name conflicts, handler-name cardinality when REST exists, input-contract resolution, and child references. Preserve deterministic order: supplied collection order/configured resource order, then whole-set checks. Do not emit a missing-child issue when the missing name could belong to a failed document; still report independently provable conflicts. The checks must not call `getBean` or mutate manager caches. Preparation passes the validated definitions directly into generation binding, which alone resolves the REST handler, issues the ID, and builds immutable capabilities. Maintain the existing `preparationLock` and publication checks. An ordinary validation result is not cached.

The simplest alternative, `prepare` then discard, fails the side-effect criteria. A separate validator that duplicates parser or whole-set rules risks drift. Refactoring the existing owners retains one source of truth at the cost of a scoped internal result/diagnostic representation. There is no new persisted state or implicit dataflow. No obsolete public concept or dead code was found that warrants unrelated removal.

## Phase 1: Public diagnostics and document checks

### Changes Required

1. **Public API**: In `src/main/java/ai/loomspan/api/SkillReloader.java`, add both validation signatures and Javadoc explaining complete-set/advisory semantics. Add immutable `SkillValidationResult` and `SkillValidationIssue` with copy-on-construction `issues`, nested `Severity { ERROR, WARNING }`, null checks for required fields, and nullable optional skill/path fields. Add both top-level types to `LoomspanPublicSurfaceArchitectureTest`.
2. **Catalog**: In `YamlSkillCatalog.java`, route configured/supplied loading through one per-call document collection path. Continue past independent document failures, return typed authoring issues, and retain validated definitions. Keep preparation's legacy fail-fast exception message by projecting the first error from this same pass. Convert `warnOnSchemaComplexity` into a warning sink; validation suppresses warning logs while preparation can retain its logging behavior. Preserve source-label and UTF-8 semantics.

### Success Criteria

#### Automated Verification

- [x] Focused catalog tests show independent-document collection, structured fields, deterministic issue order, warning-only validity, and existing prepare failure messages.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=YamlSkillCatalogTests,LoomspanPublicSurfaceArchitectureTest test` passes (covered by the broader focused test command).

## Phase 2: Shared whole-set validation and side-effect-free entry points

### Changes Required

1. **Manager**: In `SkillGenerationManager.java`, extract a shared validation/check result used by both `validate` and `prepare`. Read the already established fixed Java capability and handler-name snapshots without `active()`, `initializeFixedDependencies()`, or bean construction in validation. Reject premature invocation explicitly without changing initialization state. Check full-set conflicts, references, handler declaration count, and input contracts before preparation side effects. Consume the same validated definitions in `prepareCatalog`; keep handler binding and generation construction there.
2. **Reloader**: In `DefaultSkillReloader.java`, implement both overloads with per-call input snapshots and no `readWhileOpen()` or candidate creation. Preserve `prepareWith` synchronization and lifecycle behavior. Ensure validation cannot alter an existing candidate or its base ID.

### Success Criteria

#### Automated Verification

- [x] Focused manager/reloader tests show identical authoring decisions between validation and preparation and zero ID, handler, active, cache, candidate, or retirement effects from repeated validation.
- [x] Prepared candidates freeze their own validated bytes; edits between validate and prepare are caught.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillGenerationManagerTest,SkillReloaderTest test` passes (covered by the broader focused test command).

## Phase 3: Public integration and documentation

### Changes Required

1. **Integration and boundary**: Extend `PublicSkillReloadIntegrationTest` for both public overloads, configured rereads, in-memory isolation from configured locations, and prepare/publish after validation. Verify the architecture allowlist and no leaked internal types.
2. **Guidance**: Update `README.md`, `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, new authoring workflow topic, and authoring index/coverage table. Explain diagnostics, complete replacement, warning semantics, no-state guarantee, validation limitations, and validate/edit → prepare → stage → publish.

### Success Criteria

#### Automated Verification

- [x] Public integration and architecture tests pass and document claims match their focused evidence.
- [x] Authoring route/topic meets the `LLM-First Authoring Standard`; coverage does not overstate execution testing.
- [x] `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` passes.

## Testing Strategy

Start with the failing public API test and a low-level side-effect test, then cover diagnostics, complete-set parity, and old preparation/publication behavior. The companion testing plan has exact cases and commands.

## Performance Considerations

Validation reparses current input each call as requested. It may accumulate one issue per failing independent document plus warnings, bounded by the supplied/configured set. Avoid repeat reads within one preparation and avoid handler construction during editor calls. No persistent validation cache.

## Migration Notes

No migration. Existing `prepare`/`publish` callers continue to work. Apps may opt into the validation feedback API.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-10-side-effect-free-skill-validation.md`
- Research: `ai/thoughts/research/2026-09-18-pr-10-skill-validation.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
