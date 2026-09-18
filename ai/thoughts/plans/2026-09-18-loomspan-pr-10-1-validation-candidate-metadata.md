# PR 10.1 Validation Candidate Metadata Implementation Plan

## Overview

Extend standalone `SkillReloader.validate` feedback with a narrow, immutable list of exact callable names and public `SkillKind` values for the complete checked candidate. This lets applications compare proposed REST skills with application-owned routes without preparing or publishing a generation. The ticket deliberately changes the supported `SkillValidationResult` record shape and authorizes no compatibility constructor.

## Current State Analysis

`SkillValidationResult` contains only immutable issues, and `valid()` checks for the absence of `ERROR` (`src/main/java/ai/loomspan/api/SkillValidationResult.java:6-17`). Both manager validation overloads call the common `check` method and return its result (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:100-172`). That method already combines parser-checked definitions and fixed Java names for conflicts, contracts, REST handler declarations, and child references. Preparation uses the same check, then separately allocates a generation ID and resolves runtime dependencies (`SkillGenerationManager.java:181-216`). The parser's `CheckedDocuments.result()` currently exposes incomplete feedback before fixed Java and manager checks (`YamlSkillCatalog.java:75-88`).

## Desired End State

Both validation overloads return `SkillValidationResult(issues, skills)` where `skills` is a name-sorted, unique `List<ValidatedSkill>` for the complete effective candidate if `valid()` is true, including warnings. Any error yields an empty list with all useful issues retained. The list contains fixed Java plus the configured or supplied YAML/REST declarations. Empty supplied input contains only fixed Java declarations. Results are detached and immutable; validation retains every existing lifecycle isolation property. Preparation continues to check independently and does not construct validation metadata.

### Key Discoveries

- Fixed Java `CapabilityMetadata.name()` and `kind().publicKind()` plus checked definition manifest names and `rest()` provide all projection facts (`SkillGenerationManager.java:130-172`, `CapabilityKind.java:5-19`, `YamlSkillDefinition.java:120-123`).
- The public `SkillCatalog` is an immutable exact-name-sorted snapshot, providing the ordering convention (`SkillCatalog.java:6-18`, `DefaultSkillCatalog.java:20-36`).
- `DefaultSkillReloader` already copies supplied collections before validating and separately copies them for preparation; publication accepts only owner-bound prepared candidates (`DefaultSkillReloader.java:34-100`).
- Existing manager tests measure generation-ID continuity, fixed cache reuse, and REST handler construction (`SkillGenerationManagerTest.java:39-180`).

## What We're NOT Doing

- No Sidecar route or HTTP-client validation, Console/Go changes, serialization contract, or compatibility-marker change.
- No new manifest syntax, rule implementation, SPI, bean replacement point, cache, lifecycle stage, candidate token, full descriptor, or generation-shaped validation catalog.
- No `prepare()` call from validation, and no one-argument `SkillValidationResult` compatibility constructor.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: Manifest syntax and validation rules stay the same, but the routed authoring `validation-workflow.md` explicitly teaches complete-draft validation feedback. It must explain when `skills()` is complete and usable, warning/error behavior, and exact callable identities. Current guidance and executable behavior are aligned for the pre-change feature; the missing metadata is the intended addition. The Java API index's nineteen-type list is existing **documentation drift** against the 21-type architecture allowlist and root README.
- **Documents to update**: `agent-skills/loomspan-docs/references/skill-authoring/validation-workflow.md`; `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, `README.md`, and `compatibility-and-boundaries.md`; root `README.md`.
- **Supporting evidence**: `SkillGenerationManager.check` and its focused tests for complete checking; new public-API integration test for REST-name extraction; `SkillValidationResult` and `ValidatedSkill` constructors for immutable values.
- **Coverage table update**: Required in both knowledge-set indexes: change exact supported-type count and add candidate-metadata coverage to their existing validation/update topic notes. Keep routing to `validation-workflow.md` and `skill-reload.md`.
- **LLM-first usability**: Put the `valid()` gate and exact-name/kind semantics in the routed workflow; distinguish enforcement from advisory limits; link to the Java update API for prepared snapshot staging without duplicating its lifecycle narrative.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add `ValidatedSkill(String name, SkillKind kind)` and `SkillValidationResult.skills()`; change the existing public record constructor and components. `SkillReloader.validate` return semantics expand. The closed allowlist and README establish supported status (`LoomspanPublicSurfaceArchitectureTest.java:27-50`, `README.md:192,228`). | Intentional ticket-authorized record-shape break; update in-repo callers atomically. Preserve method signatures, `valid()` no-ERROR semantics, and `issues()` behavior. |
| Supported SPI | `RestSkillHandler` remains the sole SPI; validation checks its bean declaration count without instantiation (`SkillGenerationManager.java:153-162`). | Preserve signature and no-construction behavior. Add no extension point. |
| Configuration and manifest contracts | Existing configured discovery, supplied complete replacement, names, kinds, and checks are reused (`YamlSkillCatalog.java:144-199`, `SkillGenerationManager.java:130-172`). | Preserve authoring syntax, diagnostics, and validation behavior. |
| Persisted or serialized contracts | Result is an application Java value, with no durable or wire format in the traced path. | No change; no migration. |
| Ephemeral diagnostic formats | Existing `SkillValidationIssue` feedback is retained; no trace format changes. | Preserve current diagnostic usefulness and source labels. |
| Internal or accidentally exposed implementation | `CheckedSet` and `CheckedDocuments` are internal despite technical visibility. The parser-level public-result conversion is incomplete (`YamlSkillCatalog.java:75-88`). | Remove `CheckedDocuments.result()` and update parser tests; keep checked definitions and issues internal. |

- **Evidence of supported contracts**: `AGENTS.md` declares the API allowlist authoritative; the architecture test and README identify the 21 supported types. `RestSkillHandler` alone is the supported SPI. Sidecar is the motivating application consumer described by the ticket, not a framework protocol change.
- **Intentional compatibility changes**: The public `SkillValidationResult` one-argument constructor and record shape break, exactly as authorized in the ticket's `Pipeline notes`. Callers recompile against the two-component record; no old/new dual behavior.
- **In-repository consumers to update**: `SkillGenerationManager`, `YamlSkillCatalog` imports/conversion, `YamlSkillCatalogTests`, `SkillReloaderTest`, manager/integration tests, public-surface allowlist, root README, and routed knowledge-set documents and indexes. Search all constructor references before finalizing.
- **Public-surface delta**: One new supported top-level record, a `skills()` accessor and two-component canonical constructor on the existing result; no new interface method, Spring extension point, internal type in a public signature, or SPI.
- **Shim decision**: **No shim.** The ticket explicitly authorizes the constructor/record break, and an atomic repository update is practical.
- **Java-to-Go boundary coordination**: **Not applicable.** This is a Java application API value and does not affect Console REST/SSE, acquisition, problem, or consumed NDJSON boundaries.
- **Pipeline notes alignment**: **Aligned.** The only deliberate break is the specifically authorized `SkillValidationResult` shape change.

## Implementation Approach

Use the existing shared checker as the sole authority. Keep `CheckedSet` internal with checked definitions, contracts, and immutable issues. Its validity check remains the preparation gate. On the validation return path only, project fixed Java capabilities and checked definitions into `ValidatedSkill`, then exact-name sort. Error feedback skips projection and returns an empty skills list. The public records copy constructor inputs; `ValidatedSkill` rejects null/blank names and null kinds, following `SkillDescriptor`'s narrow value validation. `SkillValidationResult` defensively copies both lists and enforces empty skills when issues contain an error. Do not allocate metadata or sort in `check` or `prepareCatalog`.

This adds one narrow public concept because neither active `SkillCatalog` nor prepared `PreparedSkillUpdate.snapshot()` describes an unprepared draft. The alternative of preparing for discovery violates advisory isolation; a parser-level projection omits fixed Java and framework-wide checks. No duplicate validation authority or persistent state is introduced. The obsolete parser conversion is removed in scope; no other dead or low-value abstraction was identified by the focused research.

## Phase 1: Public values and validation projection

### Overview

Define the supported metadata value and construct it only from a successful complete shared check.

### Changes Required

1. **Public records** — `src/main/java/ai/loomspan/api/ValidatedSkill.java`, `SkillValidationResult.java`: add the two-field immutable value; add `skills` as the second result component, copy both collections, retain `valid()`'s no-ERROR rule, and ensure error results expose no metadata. Javadoc says callers must check `valid()` and that metadata describes a checked proposal, not a prepared generation.
2. **Manager** — `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`: retain `List<SkillValidationIssue>` in `CheckedSet`; create a private conversion invoked only by both `validate` methods. On no errors, map fixed Java `CapabilityMetadata` to `JAVA` through its existing public kind mapping and checked definitions to `REST` or `YAML` via `rest()`, then `Comparator.comparing(ValidatedSkill::name)` with natural case-sensitive order. Existing duplicate checks remain authoritative; do not silently deduplicate. Do not change `prepareCatalog` beyond using the internal issues holder.
3. **Parser cleanup** — `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`: delete `CheckedDocuments.result()` and its `SkillValidationResult` import; retain `issues()` and parser `requireValid()` for internal load paths. Update parser test callers to inspect severity directly.

### Success Criteria

#### Automated Verification

- [x] Focused manager, parser, and value tests from the testing plan pass: `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillGenerationManagerTest,SkillReloaderTest,YamlSkillCatalogTests test`.
- [x] A source search finds no `CheckedDocuments.result()` or old one-argument result construction.

## Phase 2: Public integration and compatibility boundary

### Overview

Exercise complete candidate metadata through the supported API and deliberately classify the new type.

### Changes Required

1. **Supported integration** — `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`: add a public-only example that validates a complete supplied set, checks `valid()`, filters `skills()` by `SkillKind.REST`, and extracts names. Cover additions/removals/kind changes against a distinct active snapshot and candidate isolation.
2. **Focused lifecycle tests** — `SkillGenerationManagerTest.java`, `SkillReloaderTest.java`: cover mixed Java/YAML/REST, warning/error/empty behavior, ordering, immutability, saved-result detachment, and no lifecycle effects. Preserve direct preparation/publication tests.
3. **Architecture** — `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`: add `ValidatedSkill` to the closed API set, verify no additional SPI/signature leakage through existing architecture assertions.

### Success Criteria

#### Automated Verification

- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SkillGenerationManagerTest,SkillReloaderTest,YamlSkillCatalogTests,PublicSkillReloadIntegrationTest,LoomspanPublicSurfaceArchitectureTest test` passes.
- [x] Integration example uses only supported `ai.loomspan.api` values in its validation flow.

## Phase 3: Documentation and framework verification

### Overview

Explain the supported contract and install the verified beta.5 snapshot locally.

### Changes Required

1. **Root `README.md`**: include the new type in the closed API list (22 total), describe complete candidate metadata, valid-first use, warning/error behavior, fixed Java retention, exact ordering and immutability, advisory limitations, and prepared snapshot authority for resource staging.
2. **Knowledge-set documents**: update the Java API index/compatibility list and `skill-reload.md`; update skill-authoring `validation-workflow.md` and its index coverage note. Correct the pre-existing nineteen-versus-21 drift while adding the 22nd type. Keep the API and authoring topics focused and cross-linked.
3. **Verification/install**: run focused checks, full `clean verify`, the README's release-profile verify without signing, then install the updated `1.0.0-beta.5-SNAPSHOT` locally after successful checks.

### Success Criteria

#### Automated Verification

- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` passes.
- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease -DskipTests -Dgpg.skip=true verify` passes.
- [x] `.\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests install` succeeds after verification and the local artifact version is `1.0.0-beta.5-SNAPSHOT`.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes after production type changes.
- [x] Updated authoring guidance is supported by the focused tests and remains routed and self-contained under its README standard.

## Testing Strategy

Use a failing manager test first for mixed candidate metadata and a public integration example for the supported consumer path. Extend existing focused tests for negative/error cases, immutable values, ordering, and lifecycle isolation. The dedicated testing plan gives the case matrix and commands.

## Performance Considerations

Projection and `O(n log n)` sorting occur only when validation returns a valid result. Error paths return no list. Preparation retains its existing checking and generation cost, with no unused public metadata allocation.

## Migration Notes

Java consumers directly constructing or destructuring `SkillValidationResult` must move to `(issues, skills)` and recompile. No compatibility constructor or migration adapter is provided. Existing `SkillReloader` method signatures, YAML files, configured properties, and prepared/publication candidates need no migration.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-10.1-validation-candidate-metadata.md`
- Research: `ai/thoughts/research/2026-09-18-loomspan-pr-10-1-validation-candidate-metadata.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
