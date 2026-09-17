# Application-Supplied Skills Implementation Plan

## Overview

Add the agreed `SkillDocument(String sourceName, String yaml)` public value type and `SkillReloader.prepare(Collection<SkillDocument>)`. The overload prepares an entire YAML replacement set from application-held strings while preserving configured-resource startup, no-argument preparation, and the existing detached candidate and publication rules. This permits an application to restore its own durable snapshot after ordinary startup without writing live skill files.

## Current State Analysis

`SkillReloader` exposes only `prepare()`, `publish`, and `snapshot` (`src/main/java/ai/loomspan/api/SkillReloader.java:4-14`). `DefaultSkillReloader` owns base capture, serialization, shutdown checks, and one-shot publication (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:27-68`). The manager uses a factory for configured-resource catalogs, then combines YAML with fixed Java capabilities and REST handler bindings (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:70-112`). `YamlSkillCatalog` currently discovers resources and validates each manifest in the same loop (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-227`). Inspection derives `sourcePath` from a physical resource (`src/main/java/ai/loomspan/internal/runtime/observation/catalog/SkillSourcePathResolver.java:33-65`), which would reject or misrepresent an arbitrary logical label.

The developer intentionally specified a collection of records, no loading mode, and ordinary configured startup. The companion generation-retirement ticket remains separate. No durable storage implementation exists in the framework.

## Desired End State

Both preparation methods produce identical candidate and publication behavior. Supplied documents are copied, source names are checked for nonblank exact uniqueness, and their YAML bytes pass through the same manifest parser and generation assembly as discovered files. Each candidate has its own process-local ID and frozen inspection YAML. Supplied source names appear as diagnostic labels, including in the existing `sourcePath` inspection field, without file access or path validation. Preparing an empty supplied collection retains Java skills and removes YAML on publication. A later `prepare()` still reads configured locations. Public-only integration tests prove restoration, REST staging, execution, and alternating sources.

### Key Discoveries

- Startup already accepts missing and empty configured skill roots and builds an active generation (`YamlSkillCatalogTests.java:402-420`; `SkillGenerationManager.java:53-56`).
- `YamlSkillDefinition` and downstream generation diagnostics currently carry a `Resource`, while frozen inspection bytes live in `YamlSkillSource` (`YamlSkillDefinition.java:12-39`; `YamlSkillSource.java:7-37`). An internal in-memory resource can preserve the common parser without adding a public Spring type.
- `sourcePath` is already descriptive, untrusted text in Console, not an instruction to read a file (`loomspan-console/README.md:307-313`); its field name is a historical wire name. The UI currently says “Source path” (`loomspan-console/web/src/observability/SkillDetail.tsx:75-77`).
- `LoomspanPublicSurfaceArchitectureTest.java:29-47` is the closed supported API allowlist; it must add exactly `SkillDocument` while retaining `RestSkillHandler` as the sole SPI.

## What We're NOT Doing

- No startup mode, deferred activation, initialization switch, framework traffic gate, or change to configured default locations.
- No application database, active-pointer recovery, route/client persistence, Sidecar UI/authentication/imports, or distributed transaction.
- No Java skill hot swap, model/connection reload, REST handler replacement, or generation-retirement callback.
- No new YAML manifest field, source-name-to-skill hierarchy, public resource abstraction, or filesystem interpretation of logical labels.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: The YAML declaration format and runtime skill identity stay the same, but skill authors and builders can now encounter in-memory source labels in diagnostics. They must understand that `sourceName` is diagnostic only, may lack an extension or path shape, and cannot replace YAML `name` or `allowed_skills`. Existing REST authoring guidance currently calls every diagnostic location a “YAML resource path” (`agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md:44`), which becomes stale after implementation.
- **Documents to update**: `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`, `rest-skills.md`, and `README.md` coverage notes. Keep application integration steps in `references/java-api/skill-reload.md` rather than copying them into authoring guidance.
- **Supporting evidence**: New supplied-content catalog and integration tests, existing `YamlSkillCatalogTests`, `SkillGenerationManagerTest`, `DefaultRegisteredSkillCatalogTest`, and `SkillSourcePathResolverTest`, plus the implemented source-label projection. The checked-out `loomspan-docs` package and `pom.xml` both identify `1.0.0-beta.5-SNAPSHOT`; existing guidance is aligned with current file-only executable behavior. The REST “resource path” sentence is future documentation drift induced by this ticket, not an existing framework defect.
- **Coverage table update**: Required. Update the mental-model/source-identity and REST diagnostic coverage notes to include supplied labels.
- **LLM-first usability**: Keep the mental-model rule near exact skill identity, use one concise REST diagnostic note, route integration details to the Java API topic, and distinguish enforced source-name validation from authoring advice. Preserve the existing terminology `sourcePath` only when referring to the wire field.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add agreed `SkillDocument` record and `SkillReloader.prepare(Collection<SkillDocument>)`; `SkillReloader` is allowlisted and documented (`LoomspanPublicSurfaceArchitectureTest.java:29-47`; `README.md:189-216`). Existing no-arg caller behavior is protected. | Additive overload and type; retain `prepare()`, `publish`, and `snapshot`. The interface addition is source/binary sensitive for application implementations of `SkillReloader`, but ticket explicitly requires the overload and no application implementation SPI is supported. No default-method shim. |
| Supported SPI | `RestSkillHandler` remains the sole allowlisted SPI (`AGENTS.md`; architecture test). Supplied REST skills still use its fixed binding. | Preserve existing handler signature and generation-keyed invocation. Add no second SPI or bean override contract. |
| Configuration and manifest contracts | `loomspan.skills.locations` startup/no-arg discovery and model/connection aliases remain; supplied YAML uses identical `name`, `rest`, `allowed_skills`, schema, and role rules (`LoomspanProperties.java:382-388`; `YamlSkillCatalog.java:164-227`). | Preserve existing configuration and manifest behavior. No new key or YAML schema. |
| Persisted or serialized contracts | The framework gains no durable format; its current inspection REST DTO has `sourcePath` and `yaml`, consumed by Go/TypeScript Console (`ObservabilityDtoMapper.java:13-25`; `loomspan-console/internal/observability/service.go:280-296`). | Keep JSON field and types; broaden the *value* to a logical source label for supplied documents. Coordinate Java fixture, Go validation, UI wording, and documentation. Keep exact coordinated release-version validation; no independent schema marker or legacy reader. |
| Ephemeral diagnostic formats | Parser errors and capability IDs currently use resource description/URI; source inspection derives physical paths (`YamlSkillCatalog.java:1019-1046`; `SkillGenerationManager.java:93-110`; `SkillSourcePathResolver.java:33-65`). | Display a useful supplied label consistently in errors, capability diagnostics, and inspection. Preserve physical-path derivation for discovered resources. Treat labels as untrusted display data; never dereference or normalize them as paths. |
| Internal or accidentally exposed implementation | Catalog, manager, reloader, source, resolver, and Spring wiring are implementation details despite public modifiers (`AGENTS.md`; `README.md:189`). | Refactor internally as needed. No compatibility aliases for their constructors or methods solely due to technical exposure; update repository tests/callers atomically. |

- **Evidence of supported contracts**: The ticket’s exact API requirement, architecture allowlist, README Java API section, `PublicSkillReloadIntegrationTest`, and `agent-skills/loomspan-docs/references/java-api/skill-reload.md`.
- **Intentional compatibility changes**: `SkillReloader` receives a new abstract overload. This is the ticket’s requested additive API, and a custom application implementation must implement it on recompilation. The inspection `sourcePath` value may be a logical label rather than a relative file path for supplied sources; the ticket explicitly requires arbitrary labels and useful diagnostics.
- **In-repository consumers to update**: Java API allowlist and public integration tests; catalog/manager/reloader tests; inspection tests and Java/Console fixture corpus; Console wording and focused tests; README and checked-out Java API and authoring guidance.
- **Public-surface delta**: One public top-level `SkillDocument` record and one `SkillReloader` overload using JDK `Collection`; no new Spring extension point. README supported-type count increases from eighteen to nineteen. `SkillDocument` should remain the exact two-component record specified by the ticket; validate inputs at preparation rather than adding extra public methods or constructors.
- **Shim decision**: **No shim.** The existing no-arg method already preserves its protected caller path. A default overload or replacement interface would imply an unsupported bean-replacement contract and cannot implement the required supplied-content behavior for arbitrary external implementations.
- **Java-to-Go boundary coordination**: **Required.** The existing `sourcePath` JSON field stays, but a new logical-label value reaches Console. Add a generated/committed Java fixture with a non-path label, check Go acceptance and presentation as untrusted text, update UI wording and tests, retain exact release-string rejection, and update relevant docs together. No protocol shape or compatibility-marker version change is warranted because the field was already descriptive text and consumers require only a nonblank string.
- **Pipeline notes alignment**: **No notes.** The ticket itself deliberately authorizes both the overload and arbitrary logical-label semantics; no broader break is planned.

## Implementation Approach

Create one internal supplied-document entry path into `YamlSkillCatalog`; reuse its byte-to-manifest validator and the manager’s complete-generation assembly. Use an internal memory resource solely as a parser carrier if convenient, but carry `sourceName` explicitly in `YamlSkillSource` so inspection does not infer it from `Resource#getFilename` or URI. The record’s strings are immutable; copy the collection before parsing, reject null elements/fields, blank names, and exact duplicates, and retain UTF-8 bytes of the supplied YAML in the generation. Keep source ordering deterministic (supplied iteration order or sorted names, with no behavioral precedence). Route both overloads through the same `DefaultSkillReloader` lifecycle locks and candidate class. Avoid a second publication coordinator or source mode stored on the active generation.

Alternatives considered: temporary files or custom providers violate the public-input requirement; separate YAML schema or parser risks semantic drift; making `sourceName` a fake path conflicts with allowed arbitrary labels; a loading mode would change startup semantics expressly excluded by the ticket. The narrow record plus overload adds two public concepts while reusing existing validation, authorization, and execution ownership. Any new internal source carrier is a replacement for physical-resource assumptions, not a second semantic authority.

## Phase 1: Public Input and Shared Catalog Validation

### Overview

Define and validate the supplied input, then feed it through the existing manifest parser and catalog rules.

### Changes Required

1. **Public API** — `src/main/java/ai/loomspan/api/SkillDocument.java`, `SkillReloader.java`, `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`: add the exact record and overload; add the record to the allowlist and assert generic signature safety. Keep the no-arg method unchanged.
2. **Catalog input** — `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`: add an internal supplied-input entry point accepting a copied list; validate null fields, nonblank source names, and exact duplicate names before manifest loading. Convert strings to UTF-8 once. Refactor `loadDefinition` to consume common source bytes and an internal display identity, so configured resources and supplied documents hit the same `readManifest`, REST/model/schema checks, duplicate declared-name check, and downstream generation validation. Preserve configured discovery sorting and missing-root handling.
3. **Source identity** — `src/main/java/ai/loomspan/internal/skill/YamlSkillSource.java`, `YamlSkillDefinition.java`, and `src/main/java/ai/loomspan/internal/runtime/observation/catalog/SkillSourcePathResolver.java`: carry an optional explicit logical label for supplied content. Resolver returns that label unchanged for supplied content and follows its existing physical-resource path rules for configured content. Ensure label is never used as a URI, filename, or read target. Where errors and capability IDs need source identity, use the same explicit diagnostic label instead of an invented resource URI.

### Success Criteria

#### Automated Verification

- [x] `mvn -Dtest=YamlSkillCatalogTests,SkillSourcePathResolverTest,DefaultRegisteredSkillCatalogTest,LoomspanPublicSurfaceArchitectureTest test` passes.
- [x] Focused tests show arbitrary nonblank labels (including no extension and non-path punctuation), exact duplicate rejection, null/blank rejection, frozen YAML, and identical manifest validation for both source types.
- [x] Configured-resource source path tests continue to pass.

---

## Phase 2: Generation Preparation and Publication

### Overview

Use the copied supplied set to build a detached generation and share one publication lifecycle across both methods.

### Changes Required

1. **Manager** — `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`: add internal `prepare(List<SkillDocument>)` (or an equivalent source factory) and factor the existing generation assembly so fixed Java skills, model/REST bindings, child references, duplicate capability checks, policies, and fresh IDs are identical. Do not mutate `yamlCatalogFactory` or `loomspan.skills.locations`.
2. **Reloader** — `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`: copy the collection during `prepare(Collection)` while holding preparation serialization; route both methods through the same base-ID capture, admission checks, error wrapping, and private `Candidate`. Preserve publication’s owner, stale, repeated, and shutdown checks. Invalid input must fail without activating a partial generation.
3. **Focused lifecycle tests** — extend `SkillGenerationManagerTest` and `SkillReloaderTest` for complete replacement, empty set, cross-source competing candidates, alternating source methods, mutated caller collection after preparation, failure recovery, and shutdown/foreign/repeated cases across the new overload.

### Success Criteria

#### Automated Verification

- [x] `mvn -Dtest=SkillGenerationManagerTest,SkillReloaderTest test` passes.
- [x] Existing no-arg behavior, immutable startup catalog, and captured old-generation execution remain protected.
- [x] Failed supplied preparation and rejected publication leave `snapshot().generationId()` unchanged.

---

## Phase 3: Supported Integration, Diagnostics, and Documentation

### Overview

Prove the supported consumer flow and keep Java/Console diagnostics and documentation coherent.

### Changes Required

1. **Public integration** — extend `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java` or add a focused companion using only supported APIs. Start with an empty default-location generation, provide in-memory model-backed and REST YAML, stage REST configuration under candidate ID, publish, execute, retain old admitted work, then alternate back to configured `prepare()`. Include a restart-style second context that resubmits the application-owned snapshot and observes a fresh framework ID; use a local protocol-compatible model endpoint for actual model execution where needed.
2. **Inspection and Console** — update `DefaultRegisteredSkillCatalogTest`, `ConsoleRestFixtureCorpusTest`, committed Console fixtures, and focused Go/TypeScript tests to show supplied labels verbatim in `sourcePath` and unchanged YAML. Keep the Go validator’s nonblank string rule and exact release-string rejection. In `loomspan-console/web/src/observability/SkillDetail.tsx`, change the visible field label to “Source label” (or similarly precise wording) and test that it remains plain text; revise `loomspan-console/README.md` to explain file-derived paths and supplied logical labels.
3. **Consumer guidance** — update `README.md` (nineteen API types, overload, source rules, complete replacement, configured startup, persistence/readiness, and a supported-only restart sketch); `agent-skills/loomspan-docs/references/java-api/skill-reload.md` and `compatibility-and-boundaries.md`; and the authoring documents identified above. State explicitly that the absence of a startup switch, deferred activation, and framework traffic gate is intentional. Keep companion retirement behavior out of this change.

### Success Criteria

#### Automated Verification

- [x] `mvn -Dtest=PublicSkillReloadIntegrationTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest test` passes.
- [x] Relevant Console Go and web tests pass using their repository-standard commands; new labels remain untrusted display text and existing version rejection still passes.
- [x] `mvn test` passes after all production changes.
- [x] Authoring claims match the focused tests and source; the authoring README coverage table and Java API routing are coherent.

---

## Testing Strategy

### Unit Tests

Begin with a compile-failing public integration test for `SkillDocument` and the overload. Add catalog tests for source validation and parser parity; generation/reloader tests for complete sets, freeze, failure isolation, cross-source races, and shutdown. Keep physical path resolver tests unchanged and add explicit logical-label cases.

### Integration Tests

Use the Spring context runner and supported beans for empty startup, REST staging, model-backed YAML, publication, old admitted work, source alternation, and restart resubmission. Exercise the inspection DTO and Console consumer with a non-path source label. Full detail is in the companion testing plan.

## Performance Considerations

Copying the collection and UTF-8 encoding content once is proportional to supplied data size, as resource `readAllBytes()` already is. Parsing and generation assembly remain synchronous preparation work. Do not reread or re-encode at publication. Do not retain redundant complete copies beyond the existing frozen source bytes and manifest structures when avoidable.

## Migration Notes

Existing directory-based applications need no change. Applications using supplied content must retain and select their own durable snapshot, stage generation-keyed REST resources, and gate their own traffic. Generation IDs are process-local and must be restaged after restart. Custom implementations of the supported `SkillReloader` interface need to implement the new overload when recompiling; there is no supported bean-replacement contract or adapter shim.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-7-application-supplied-skills.md`
- Research: `ai/thoughts/research/2026-09-16-application-supplied-skills.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Companion scope: `ai/thoughts/tickets/loomspan-pr-8-generation-retirement-notification.md`
