---
date: 2026-09-18T07:45:49-07:00
researcher: Unknown
git_commit: 629ad1126dcb4e8158fdafb1af8598dfaae002f4
branch: main
repository: loomspan-framework
topic: "PR 10 — side-effect-free skill validation"
tags: [research, codebase, skill-validation, skill-reload, public-api]
status: complete
last_updated: 2026-09-18
last_updated_by: Unknown
---

# Research: PR 10 — side-effect-free skill validation

**Date**: 2026-09-18T07:45:49-07:00  
**Researcher**: Unknown (thoughts status unavailable)  
**Git Commit**: `629ad1126dcb4e8158fdafb1af8598dfaae002f4`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research Question

Map the existing validation, preparation, publication, diagnostics, and supported surface for the side-effect-free validation feature described in `ai/thoughts/tickets/loomspan-pr-10-side-effect-free-skill-validation.md`.

## Summary

The supported `SkillReloader` currently has two `prepare` overloads and `publish`, with no validation result API (`src/main/java/ai/loomspan/api/SkillReloader.java:7-29`). YAML document and manifest rules live in `YamlSkillCatalog`; complete-set conflicts, child references, REST handler cardinality, generation identity, and binding live in `SkillGenerationManager`. `DefaultSkillReloader` wraps preparation failures, owns candidates, and checks publication eligibility. Preparation calls active generation access and creates a new generation, so it is distinct from read-only validation (`DefaultSkillReloader.java:48-59`; `SkillGenerationManager.java:72-138`).

## Detailed Findings

### Public entry and Spring wiring

- `SkillDocument` is a public record of `sourceName` and `yaml` (`src/main/java/ai/loomspan/api/SkillDocument.java:4`). `SkillReloader` exposes `prepare()`, `prepare(Collection<SkillDocument>)`, `publish`, retirement listener registration, and `snapshot` (`src/main/java/ai/loomspan/api/SkillReloader.java:7-29`). `PreparedSkillUpdate` is the frozen candidate contract.
- Auto-configuration registers `SkillGenerationManager` and `SkillReloader` as framework infrastructure beans. The manager's catalog factory receives a snapshot of skill locations, connection drivers, and model entries; each catalog uses a fresh resource resolver and YAML codec (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:163-204,230-236`). It also registers the initial `SkillCatalog` from `active()` (`:222-228`). These Spring-facing types and beans are integration machinery, not an application replacement contract under `AGENTS.md`.
- `DefaultSkillReloader.prepare(Collection)` copies the caller collection into a list inside its preparation path (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:39-46`). Both overloads call `prepareWith`, which obtains the active base ID, invokes generation preparation, checks shutdown, then returns a candidate tied to the reloader's private owner object (`:48-59,114-129`). Runtime preparation failures become `SkillReloadException` with the original cause (`:55`).

### Document loading and per-document validation

- Configured discovery loops over `loomspan.skills.locations`, resolves matching resources, keeps existing files, sorts by resource description, reads bytes, and loads definitions (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:104-115,176-220`). A missing classpath root means no skills; other discovery/read failures throw.
- Supplied loading requires a non-null list and elements, nonblank exact-unique `sourceName` values, and non-null YAML. It encodes text as UTF-8 and creates in-memory `ByteArrayResource`/`YamlSkillSource` objects using labels as diagnostics; it does not resolve labels as paths (`YamlSkillCatalog.java:118-150`). An empty list leaves its YAML catalog empty (`:118-165`). `YamlSkillCatalogTests.java:36-75` covers labels, duplicates, malformed YAML, invalid REST fields, unknown models, and a subsequent corrected load.
- `loadDefinition` parses once, then checks required name/description and input schema. REST definitions reject declared model/prompt/planning/output fields and return without model binding; model-backed definitions check concurrency, model presence, output schema, evidence, linter, model catalog entry, and effective thinking level, then store resolved execution configuration (`YamlSkillCatalog.java:223-280`). `readManifest` performs raw-tree checks before binding, including public name, REST, concurrency, and child declarations (`:303-353`). Schema validation is recursive (`:678-1012`).
- Invalid content currently throws, often from `invalidSkill`/`invalidNamedSkill` messages that contain resource, optional skill name, field path, and detail (`YamlSkillCatalog.java:1067-1094`). Mapping exceptions derive a field path from Jackson references (`:1096-1136`). `loadSupplied` does a whole-list precheck, then stops at the first runtime failure in the document loop (`:118-150`). `afterPropertiesSet` likewise stops on the first failed discovered document (`:104-115`).
- Output-schema complexity warnings are emitted directly by `warnOnSchemaComplexity`: nesting depth above 4, more than 12 properties, more than 8 required fields, and arrays of object items (`YamlSkillCatalog.java:49-51,1021-1046`). `YamlSkillCatalogTests.java:912-935` checks log output for a complex supported schema.

### Complete-set checks and stateful preparation

- `SkillGenerationManager.prepare()` initializes fixed dependencies, constructs a catalog, and loads configured resources. Its supplied overload does the same with provided documents (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:83-97`). `initializeFixedDependencies` caches reflected Java capabilities and sorted `RestSkillHandler` bean names in manager fields (`:248-255`). It obtains handler bean names with `getBeanNamesForType(..., true, false)`, which avoids eager factory initialization.
- `prepareCatalog` increments `issuedGenerationIds` before it assembles capabilities. It inserts Java capabilities, then each YAML/REST capability, resolving its input contract and checking exact-name collisions; finally it verifies every child reference against the complete capability map (`SkillGenerationManager.java:99-138,273-280`). Therefore invalid whole-set declarations can occur after an ID was issued. The process-local namespace is a UUID and the ordinal is an `AtomicLong` (`:48-49`).
- REST definitions invoke `requireRestHandler`, which requires exactly one fixed handler name and lazily obtains the bean with `beans.getBean(...)`, caching that instance (`SkillGenerationManager.java:257-271`). `SkillGenerationManagerTest.java:205-226` demonstrates a prototype handler constructed once across preparations. REST invocation closures are constructed during preparation, while actual handler execution is in `invokeRest` (`SkillGenerationManager.java:118-135,282-287`).
- `active()` lazily calls `afterSingletonsInstantiated()` if no generation is active; that path initializes dependencies, prepares, and activates a generation (`SkillGenerationManager.java:65-80`). `DefaultSkillReloader.prepareWith` calls `active()` to read the base ID (`DefaultSkillReloader.java:98-105`).
- `publish` checks owner identity, one-shot use, active-base staleness, and lifecycle admission; activation and retirement selection happen under publication synchronization, then callbacks dispatch outside the lock (`DefaultSkillReloader.java:61-87`; `SkillGenerationManager.java:141-159,197-205`). `SkillReloaderTest.java:190-227` protects owner/base/one-shot and snapshot behavior. `SkillGenerationManagerTest.java:36-75,143-176` protects complete supplied-set semantics and frozen source bytes.

### Surface classification and consumers

- **Application API:** `SkillReloader`, `SkillDocument`, and `PreparedSkillUpdate` are named in the closed allowlist and README (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-49,292-295`; `README.md:192,199-234`). The new result and issue types in the ticket are not present in the current allowlist or production source.
- **Supported SPI:** `RestSkillHandler` alone is documented as the supported SPI (`AGENTS.md`; `README.md:192`). Its declared bean names are examined during preparation, and the handler instance is obtained only for REST definitions (`SkillGenerationManager.java:248-271`).
- **Configuration and manifest contracts:** skill locations, model aliases and connection drivers are snapshotted by auto-configuration (`LoomspanAutoConfiguration.java:163-204`); YAML syntax and validation are described in `README.md:423-506`. `YamlSkillCatalogTests` and `src/test/resources/skills/valid/` / `invalid/` provide executable fixtures. Source labels and empty-set behavior are documented at `README.md:226`.
- **Persisted or serialized contracts:** no validation result exists or crosses a wire in the current path. Application-held YAML is represented by `SkillDocument` but persistence is application-owned (`README.md:226-234`). The ticket does not identify a Console protocol change.
- **Ephemeral diagnostic formats:** manifest exceptions and schema warnings currently serve authoring feedback (`YamlSkillCatalog.java:1021-1094`). The ticket requests structured result diagnostics; the current exception text is not a separate supported serialized format.
- **Internal or accidentally exposed implementation:** `DefaultSkillReloader`, `YamlSkillCatalog`, `SkillGenerationManager`, and their public Java methods are below `ai.loomspan.internal`; their modifiers and constructors do not establish application contracts (`AGENTS.md`; `ai/thoughts/framework-feature-design-lens.md`).
- Public surface tests also inspect API signatures for leaked internal/framework types (`LoomspanPublicSurfaceArchitectureTest.java:349-366,520`). No verified external Java consumer appears in this repository; README examples and framework tests are in-repository usage.

### Authoring documentation alignment

- The checked-out Maven version is `1.0.0-beta.5-SNAPSHOT` (`pom.xml:9`), matching the bundled `agent-skills/loomspan-docs/SKILL.md` metadata. Relevant routed topics are `references/skill-authoring/rest-skills.md`, `model-selection-and-connections.md`, `output-contracts.md`, and `references/java-api/skill-reload.md`; their existing manifest, fixed-handler, complete-set, and prepare/publish descriptions match the source and focused tests. Classification: **aligned** for current documented behavior.
- The authoring guide has no current `validate` API description, consistent with its absence from code. The guide's testing coverage is explicitly incomplete (`agent-skills/loomspan-docs/references/skill-authoring/README.md:60-75`). This is current coverage, not an observed behavioral discrepancy.

## Code References

- `src/main/java/ai/loomspan/api/SkillReloader.java:7-29` — supported reload methods.
- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:33-129` — preparation and publication coordinator.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:104-165,223-280,1021-1094` — resource/document loading, manifest checks, warnings, and exception diagnostics.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:65-138,248-280` — lazy activation, dependency caching, ID allocation, whole-set rules, REST handler lookup.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:36-75,205-226` — complete replacement/frozen data and handler reuse.
- `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:190-227` — candidate ownership, staleness, one-shot, and snapshot.
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-49,292-295,520` — API allowlist and signature boundary.

## Architecture Documentation

The live path is `SkillReloader` → `DefaultSkillReloader` → `SkillGenerationManager` → new `YamlSkillCatalog`. Catalog construction consumes either configured resource bytes or supplied UTF-8 strings, validates and normalizes each manifest, and retains definitions. The manager combines those definitions with fixed Java capabilities, binds input contracts and REST handlers, checks complete-set references, and creates a detached generation. The reloader then pairs that generation with the active base ID and owner identity for a later publication check. Startup obtains a generation through the manager's lazy initialization path.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-10-side-effect-free-skill-validation.md` is the current ticket and states the proposed result, diagnostics, side-effect boundaries, and compatibility requirements; it was untracked when research began.
- `ai/thoughts/framework-feature-design-lens.md` defines the six surface classifications used above and distinguishes technical exposure from deliberately supported contracts. No prior research artifact for this ticket was found in `ai/thoughts/research/`.

## Related Research

None found in `ai/thoughts/research/` at research time.

## Open Questions

- Which internal diagnostic representation will carry source, optional skill, and field path across parsing and complete-set checks while preserving existing preparation exception behavior? This is a planning design choice; the current code has exception text and log messages rather than structured diagnostics.
- How will validation read fixed Java declarations and REST handler names before the manager's startup initialization without triggering its active-generation path or updating its fixed-dependency caches? The current manager caches both through `initializeFixedDependencies()`.
