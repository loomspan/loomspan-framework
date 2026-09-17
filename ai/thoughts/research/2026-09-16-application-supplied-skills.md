---
date: 2026-09-16T21:52:26-07:00
researcher: Codex
git_commit: d7d0ef03aefe44c613ff1d8591bce2a30318a6bf
branch: main
repository: loomspan-framework
topic: "PR 7 application-supplied YAML skill preparation"
tags: [research, codebase, skill-reload, yaml-skills, public-api]
status: complete
last_updated: 2026-09-16
last_updated_by: Codex
---

# Research: PR 7 application-supplied YAML skill preparation

**Date**: 2026-09-16T21:52:26-07:00  
**Researcher**: Codex  
**Git Commit**: d7d0ef03aefe44c613ff1d8591bce2a30318a6bf  
**Branch**: main  
**Repository**: loomspan-framework

## Research Question

Map the current code, tests, configuration, diagnostics, and documentation relevant to `ai/thoughts/tickets/loomspan-pr-7-application-supplied-skills.md`: preparing a complete YAML skill set from application-held `SkillDocument` values while retaining configured-resource startup and the existing two-stage publication lifecycle.

## Summary

The supported `SkillReloader` currently exposes only `prepare()`, `publish`, and `snapshot`; no `SkillDocument` type or supplied-content overload exists (`src/main/java/ai/loomspan/api/SkillReloader.java:4-14`). Startup and each preparation create a fresh `YamlSkillCatalog` from fixed configured locations and model bindings, validate a complete generation together with fixed Java skills and REST handler bindings, and assign a process-local ID (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:164-205`; `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:55-111`). The catalog currently discovers Spring `Resource` objects, reads their bytes once, validates the YAML declarations, and retains source bytes for inspection (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-227`). Publication uses the already prepared generation without resource reads and rejects foreign, stale, repeated, and shutdown-time candidates (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:28-85`).

The current inspection path derives a safe relative `sourcePath` from the resource and configured pattern; Java's observability adapter and the Console's Go/TypeScript consumers carry that field as diagnostic text (`src/main/java/ai/loomspan/internal/runtime/observation/catalog/SkillSourcePathResolver.java:33-65`; `loomspan-console/internal/observability/service.go:280-296`; `loomspan-console/web/src/api/contracts.ts:125-137`). This is the existing cross-component boundary most directly related to arbitrary logical source labels.

## Detailed Findings

### Supported application API and publication coordinator

- `SkillReloader` is a supported interface with no-argument `prepare()`, `publish(PreparedSkillUpdate)`, and `snapshot()`; `PreparedSkillUpdate` exposes a candidate `generationId()` and `snapshot()` (`src/main/java/ai/loomspan/api/SkillReloader.java:4-14`; `src/main/java/ai/loomspan/api/PreparedSkillUpdate.java:4-9`). The closed architecture allowlist contains these two types and the sole supported SPI, `RestSkillHandler` (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-47`). The README identifies eighteen supported types and the internal/autoconfigure boundary (`README.md:189`).
- `DefaultSkillReloader.prepare()` captures the active base ID while admission is open, serializes preparations, delegates to the generation manager, checks admission again, and returns a private candidate containing owner identity, base ID, and generation (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:28-40,87-119`). Operational preparation errors become `SkillReloadException` with a cause. `publish()` serializes publication, checks owner, one-shot use, active-base equality, and admission status before activation (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:42-69`). `snapshot()` reads the active immutable catalog (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:72-73`).
- `SkillReloaderTest` exercises detached preparation, owner/fabricated/stale/repeated rejection, absence of reads during snapshot/publication, overlapping preparation and publication, competing publications, and shutdown during preparation (`src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:29-210`). `PublicSkillReloadIntegrationTest` exercises supported bean injection, generation-keyed REST staging, file mutation after preparation, preserved old admitted work, unchanged injected startup catalog, failure recovery, and an empty YAML set (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:27-105`).

### Configured discovery, YAML validation, and freezing

- `LoomspanProperties.Skills.locations` defaults to `classpath:/skills/**/*.yaml`; null or empty setter input restores that default (`src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:382-388`). Auto-configuration snapshots the locations, named connections, and model aliases and supplies a fresh catalog factory to the manager (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:164-205`). Its `SkillCatalog` bean captures the startup catalog; `SkillReloader` is separately wired to the manager (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:222-235`).
- `YamlSkillCatalog.afterPropertiesSet()` clears its maps, discovers configured resources, loads each definition, and rejects duplicate declared skill names (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-115`). Discovery ignores missing classpath roots, includes existing resources, and sorts by resource description (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:133-162`). Focused tests cover missing and empty roots (`src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:402-420`).
- Each discovered resource is read into bytes once. The catalog parses the YAML tree, checks required and unknown fields, validates REST or model-backed declarations, resolves configured model aliases, and stores a `YamlSkillSource` with cloned bytes (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:164-227,255-305`; `src/main/java/ai/loomspan/internal/skill/YamlSkillSource.java:10-37`). REST declarations allow input schema and roles but reject model/planning/output fields; model-backed declarations require a configured model (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:181-219,307-324`). Diagnostic errors include the declared name when available, resource description, and field path (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:1019-1046,1100-1110`).
- `SkillGenerationManager.prepare()` assigns a UUID-namespace/counter ID, initializes the fresh catalog, combines YAML definitions with fixed Java capabilities, resolves input contracts and role policies, requires one fixed REST handler if REST declarations exist, checks duplicate capability names and every `allowed_skills` child reference, and builds a detached immutable generation (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:55-111,120-158`). Fixed Java capabilities and REST handler bean names are captured once (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:118-143`). Startup calls this preparation path and activates it after singleton initialization (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:55-59`).
- `SkillGeneration` copies capability and definition maps, creates immutable public and inspection catalogs, and retains exact generation-owned capability identities (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:18-69`). Manager tests cover fresh IDs, fixed Java preservation, failed candidate isolation, whole-set addition/removal/empty YAML, frozen file content, and fixed REST handler reuse (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:37-186`).

### Execution, REST staging, and diagnostics

- Root preparation captures `generationManager.active()` before input conversion or validation; the prepared input carries the generation into invocation (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-154,189-223`). Atomic handoff retains that prepared input and admits the root (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:29-73`). REST invocations receive the generation ID captured when the capability was assembled, and the handler result must be non-null (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:82-95,153-158`). The public `RestSkillInvocation` validates its ID (`src/main/java/ai/loomspan/api/RestSkillInvocation.java:10-19`).
- `DefaultRegisteredSkillCatalog` builds inspection entries from frozen source bytes and a `SkillSourcePathResolver`, preserving YAML text and distinguishing `YAML`, `REST`, and `JAVA` (`src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:20-54`). The resolver currently uses a resource filename for an exact location or relativizes its URI against a configured pattern root, then rejects blank, absolute, scheme-like, or unsafe path segments (`src/main/java/ai/loomspan/internal/runtime/observation/catalog/SkillSourcePathResolver.java:33-65,100-123`). The inspection entry requires a nonblank `sourcePath` for YAML and REST (`src/main/java/ai/loomspan/internal/runtime/observation/catalog/RegisteredSkillEntry.java:27-43`). Focused tests cover this source-path derivation and verbatim YAML preservation (`src/test/java/ai/loomspan/internal/runtime/observation/catalog/SkillSourcePathResolverTest.java:17-77`; `src/test/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalogTest.java:29-69`).
- The observability DTO mapper exposes `sourcePath` and `yaml`; the route registrar reads the active generation's inspection catalog (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityDtoMapper.java:13-25`; `src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:134`). The Java/Console fixture corpus includes YAML and REST skill summary/detail records with `sourcePath` (`src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java:154-167`; `loomspan-console-fixtures/application-rest/skills-page.json`). Console Go validation requires the field for YAML and REST, and its web contract and views treat it as display text (`loomspan-console/internal/observability/service.go:280-296`; `loomspan-console/web/src/api/contracts.ts:125-137`; `loomspan-console/web/src/observability/SkillDetail.tsx:69-78`). Console guidance identifies it as an untrusted search hint, not a filesystem instruction (`loomspan-console/README.md:308-312`).

### Current public documentation and checked-out skill guide

- The README documents default YAML discovery, optional `.yml` location configuration, immutable catalog snapshots, and the existing no-argument two-stage reload flow (`README.md:61-80,137-141,196-216`). It states that no-argument preparation reads configured files, publication does not reread, empty YAML retains Java skills, and applications own REST staging/readiness and old-resource retention (`README.md:216`).
- The checked-out `loomspan-docs` skill declares version `1.0.0-beta.5-SNAPSHOT`, matching `pom.xml:9` and the current source checkout (`agent-skills/loomspan-docs/SKILL.md:1-23`). Its Java API router points to `java-api/skill-reload.md` and `java-api/rest-skills.md`; its authoring router points to the mental model, REST, model-selection, and authorization topics (`agent-skills/loomspan-docs/references/java-api/README.md`; `agent-skills/loomspan-docs/references/skill-authoring/README.md`). The reload topic describes file-backed preparation only, complete generations, fixed bindings, one-shot publication, and application-owned readiness (`agent-skills/loomspan-docs/references/java-api/skill-reload.md`). The authoring mental model places tree relationships in exact declared names and `allowed_skills`, not source paths (`agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`). The REST and model topics describe the same manifest distinction and model alias chain as the production path (`agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md`; `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`). **Drift classification: aligned for current executable behavior.** Supplied-content preparation is outside current documentation because the corresponding API and implementation do not yet exist.

## Contract Surface Inventory

| Category | Current technical exposure and evidence | Current contract treatment |
| --- | --- | --- |
| Application API | `SkillReloader`, `PreparedSkillUpdate`, `SkillCatalog`, and `RestSkillInvocation` signatures; exact architecture allowlist; README and Java API guide; supported-surface integration test | Deliberately supported Java API. The requested `SkillDocument` and overload are absent in this checkout. |
| Supported SPI | `RestSkillHandler` is in the allowlist; manager fixes the handler bean for REST declarations | Sole supported SPI. No internal bean replacement surface is documented. |
| Configuration and manifest contracts | `loomspan.skills.locations` default, configured model/connection aliases, YAML `name`, `model`, `rest`, `allowed_skills`, and `rbac_roles`; catalog validation and fixtures | User-visible configuration and authoring behavior documented by README and checked-out skill guide. |
| Persisted or serialized contracts | Public supplied documents and framework IDs have no storage format in current code. Inspection REST DTOs serialize `sourcePath` and `yaml`; committed Java/Console fixtures and Go/TS consumers exist. | Inspection DTOs are a protected cross-component protocol for the coordinated Console; no framework durability contract for skill content or IDs is evidenced. |
| Ephemeral diagnostic formats | YAML parser errors, capability source IDs, source-path inspection entries, Console display/search hints | Current-run diagnostic representations; useful source identity and protocol consumer behavior are observable. |
| Internal or accidentally exposed implementation | `YamlSkillCatalog`, `YamlSkillSource`, `SkillGenerationManager`, `DefaultSkillReloader`, `SkillSourcePathResolver`, constructors and Spring beans | Public modifiers and Spring wiring provide technical exposure but are classified as implementation details by AGENTS.md, README, and the architecture allowlist. |

The ticket does not request a new `loomspan.*` property, manifest field, persistent format, or Console schema field. The existing `sourcePath` string semantics and path resolver are an affected diagnostic and cross-component consideration because source names in the requested API are logical labels without path or extension requirements.

## Architecture Documentation

The existing path is `configured locations → Spring Resource discovery → YAML parse and validation → complete detached SkillGeneration → public candidate → publication → active generation`. A separate immutable inspection catalog is constructed as part of each generation. Startup uses the same manager preparation path and can activate an empty YAML generation. Root admission captures a generation; nested and admitted work continue with that captured generation while later roots see the newly active one. Model aliases, Java declarations, and REST handler bindings are fixed at startup; YAML declarations are replaced as a complete set.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-7-application-supplied-skills.md` records the agreed `SkillDocument(String sourceName, String yaml)` and overload, exact duplicate source-name rejection, logical labels, complete replacement, no loading mode, ordinary startup, and application-owned durable recovery/readiness. It is a requested change, not current behavior.
- `ai/thoughts/tickets/loomspan-pr-8-generation-retirement-notification.md` is a separate untracked companion ticket about safe retirement of generation-keyed external resources. Its requested notification API is not present in this checkout and remains separate from PR 7.
- `ai/thoughts/framework-feature-design-lens.md` supplies the six surface classifications used above and distinguishes technical exposure from deliberately supported contracts. No prior research or plan artifact for this exact ticket was found under `ai/thoughts/research/` or `ai/thoughts/plans/`.

## Related Research

None found for this ticket in the current `ai/thoughts/research/` tree.

## Open Questions

- The planning step must resolve how arbitrary logical source names can be represented in current source diagnostics and the inspection catalog: `SkillSourcePathResolver` derives and validates physical relative paths, while the existing Console protocol expects nonblank `sourcePath` text. The ticket requires labels without path, extension, or resource-locator semantics.
