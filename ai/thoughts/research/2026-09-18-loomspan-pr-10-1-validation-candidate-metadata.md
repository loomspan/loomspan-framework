---
date: 2026-09-18T09:14:31-07:00
researcher: Codex
git_commit: 27c4f081a3e140ad24de9cce1a13ad7303749602
branch: main
repository: loomspan-framework
topic: "PR 10.1 validation candidate metadata: current API, checking path, and lifecycle"
tags: [research, codebase, validation, skill-reload, java-api]
status: complete
last_updated: 2026-09-18
last_updated_by: Codex
---

# Research: PR 10.1 validation candidate metadata

**Date**: 2026-09-18T09:14:31-07:00  
**Researcher**: Codex  
**Git Commit**: `27c4f081a3e140ad24de9cce1a13ad7303749602`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research Question

Document the current code paths, public contracts, tests, and documentation relevant to the ticket `ai/thoughts/tickets/loomspan-pr-10.1-validation-candidate-metadata.md`. This records the checkout before implementation. At the start of research, Git status showed only the ticket as untracked.

## Summary

`SkillReloader` exposes configured-resource and supplied-document validation; both delegate to `SkillGenerationManager` and return a `SkillValidationResult` containing only issues. The manager's shared `check` path already combines parser-checked YAML/REST definitions with fixed Java declarations for name, input-contract, child-reference, and REST-handler declaration checks. Preparation uses that same checked set, then allocates an ID and builds an executable generation. Validation returns before those preparation and publication operations. The public catalog's existing kind mapping and exact-name ordering provide the current reference semantics for registered skills.

## Detailed Findings

### Supported application API and existing public values

- `SkillReloader` declares `validate()` and `validate(Collection<SkillDocument>)`, followed by separate `prepare`, `publish`, `snapshot`, and retirement-listener operations (`src/main/java/ai/loomspan/api/SkillReloader.java:6-38`). `SkillValidationResult` is currently a one-component record with an immutable copy of `issues`; `valid()` means no issue of severity `ERROR` (`src/main/java/ai/loomspan/api/SkillValidationResult.java:6-17`). `SkillKind` already contains `YAML`, `JAVA`, and `REST` (`src/main/java/ai/loomspan/api/SkillKind.java:3-7`).
- The supported API is closed by the `API_TYPES` set in `LoomspanPublicSurfaceArchitectureTest`, which includes `SkillValidationResult`, `SkillReloader`, `SkillKind`, `SkillCatalog`, and `SkillDescriptor` (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:27-50`). The README lists 21 supported top-level types and identifies `RestSkillHandler` as the sole supported SPI (`README.md:192`). The proposed new record is not present in the current allowlist or source.
- The existing `SkillCatalog` contract is an immutable, exact-name-sorted snapshot of registered skills, and each `SkillDescriptor` includes name, description, kind, and input schema (`src/main/java/ai/loomspan/api/SkillCatalog.java:6-18`; `src/main/java/ai/loomspan/api/SkillDescriptor.java:5-21`). Its implementation uses a `TreeMap` keyed by exact names, creates immutable descriptors, and rejects duplicate names (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:20-36`). Internal `CapabilityKind` maps YAML, REST, and Java capabilities to the corresponding public `SkillKind` (`src/main/java/ai/loomspan/internal/core/CapabilityKind.java:5-19`).

### Parsing, diagnostics, and shared checking

- `YamlSkillCatalog.CheckedDocuments` stores immutable copies of definitions and issues. It currently exposes a `result()` method that constructs the public one-component `SkillValidationResult` from parser issues, plus `requireValid()` for errors (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:75-88`). `YamlSkillCatalogTests` calls that conversion for error and warning checks (`src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:37-77`).
- Configured validation resets the catalog, discovers configured resources, collects each document, and returns checked definitions and issues; discovery failure receives the `<configured-resources>` diagnostic source (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:144-162`). Supplied validation similarly resets and checks the passed list, treating source labels as diagnostics rather than paths, reporting malformed/null/duplicate entries, and decoding YAML from each supplied string (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:164-199`). `addDefinition` rejects duplicate callable names (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:242-253`).
- `SkillGenerationManager.check` starts with parser issues and the cached fixed Java capabilities. It checks callable-name conflicts with Java, resolves input contracts, checks the declared number of REST-handler beans when REST definitions exist, and checks `allowed_skills` references while suppressing follow-on noise for failed documents (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:130-172`). The current `CheckedSet` holds definitions, contracts, and a public issue-only result; `requireValid()` checks for errors before preparation (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:120-128`). The parsed definitions carry source and manifest name; `YamlSkillDefinition.rest()` distinguishes REST from model-backed YAML (`src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java:120-123`).

### Validation, preparation, and publication lifecycle

- Both manager validation overloads call `requireInitialized()`, obtain a new `YamlSkillCatalog`, invoke either `checkedConfigured(false)` or `checkedSupplied(documents, false)`, and return the result of the shared check (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:100-117`). Startup initializes fixed Java capabilities and discovers REST handler bean names once; validation requires this completed startup snapshot (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:68-73,325-332`).
- Preparation independently obtains a catalog and runs the same shared check with warning logging enabled (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:86-98`). `prepareCatalog` checks errors, increments the generation counter, assembles fixed Java plus parsed YAML/REST capabilities, resolves a REST handler only when needed, and creates `SkillGeneration` (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:181-216`). The generated public catalog is sorted by capability name (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:50-55`).
- The reloader copies a caller's supplied collection before manager validation and uses a separate copied collection during preparation (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:34-59`). Preparation captures the active base ID and returns an owner-bound candidate (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:62-71,128-144`). Publication verifies owner, single use, current base ID, and admission state before activation; retirement dispatch follows the publication lock (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:75-100`). Generation-manager activation changes active state and selects retirement; neither validation overload calls it (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:218-235`).

### Tests and executable examples

- `SkillGenerationManagerTest` covers startup precondition, repeatable validation, generation-ID continuity, handler nonconstruction, fixed-Java cache reuse, conflict and child-reference parity with preparation, empty supplied input, REST-handler bean-count checks, and supplied versus configured preparation (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:39-180`).
- `SkillReloaderTest` currently constructs the one-argument result and checks issue-list immutability; it also prepares before validating and publishes the existing candidate afterward (`src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:33-55`). `YamlSkillCatalogTests` exercises independent parser diagnostics and warning-only feedback via `CheckedDocuments.result()` (`src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java:37-77`).
- `PublicSkillReloadIntegrationTest` uses supported `SkillReloader`, `SkillDocument`, and `PreparedSkillUpdate` types to show configured and supplied validation isolation, a prepared candidate surviving later invalid configured resources, and successful publication (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:36-70`). Its subsequent tests cover retirement and captured work (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:72-120`).

## Code References

- `src/main/java/ai/loomspan/api/SkillValidationResult.java:6-17` — current public feedback shape and validity rule.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:75-88` — parser-level checked-document conversion.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:100-172` — both validation paths and the common checker.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:181-216` — preparation boundary and generation allocation.
- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:34-100` — public delegation and candidate publication safeguards.
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-50` — supported API allowlist.
- `README.md:192,228` — consumer-facing API list and current validation narrative.

## Architecture Documentation

Under `ai/thoughts/framework-feature-design-lens.md`, the touched supported surface is **Application API**: `SkillValidationResult`, `SkillReloader` return semantics, existing `SkillKind`, and the ticket's proposed `ValidatedSkill` record. The ticket explicitly authorizes changing the current result record shape without a compatibility constructor (`ai/thoughts/tickets/loomspan-pr-10.1-validation-candidate-metadata.md:133-137`). The `RestSkillHandler` **Supported SPI** is involved as a declaration-count check and later runtime dependency, but its signature is unchanged. Existing skill YAML and `loomspan.*` properties are **Configuration and manifest contracts** checked through the current parser and manager paths. The ticket declares no **Persisted or serialized contracts** or Console compatibility-marker change; the traced API path is Java values and internal generation state, not a Console REST/SSE, acquisition, problem, or NDJSON boundary. No **Ephemeral diagnostic formats** change is described; `SkillValidationIssue` remains the current diagnostic value. `YamlSkillCatalog.CheckedDocuments`, `SkillGenerationManager.CheckedSet`, `SkillGeneration`, and `DefaultSkillReloader` are **Internal or accidentally exposed implementation** under the repository's API policy (`AGENTS.md:3-15`).

The current checker has the authoritative callable-name inputs in one place: fixed Java `CapabilityMetadata.name()` and each parsed manifest `name`; `YamlSkillDefinition.rest()` and `CapabilityKind` provide the existing kind distinctions. The parser conversion occurs before fixed Java and manager checks. The public catalog is a prepared/published generation view with richer descriptors and a generation ID, whereas validation currently returns feedback only.

The supplied and configured paths continue to use the same parser rules, with different input sources. Validation uses `false` warning logging, and preparation uses `true` (`SkillGenerationManager.java:86-111`). This API addition is application-side metadata; no skill-author manifest syntax, execution, planning, authorization, or authoring guidance changes were found in the traced paths.

## Documentation and Drift Classification

The checkout's `agent-skills/loomspan-docs/SKILL.md` declares `1.0.0-beta.5-SNAPSHOT`, matching `pom.xml:9`. The Java API `skill-reload.md` and skill-authoring `validation-workflow.md` agree with the current executable behavior on advisory validation, complete supplied replacement, empty-input Java retention, warning/error semantics, and separation from preparation/publication: **aligned**. Their absence of proposed candidate metadata reflects the pre-implementation API, not an existing behavior discrepancy. The Java API index claims a nineteen-type allowlist and omits `SkillValidationResult` and `SkillValidationIssue`, while the live architecture allowlist and README contain 21: **documentation drift** in `agent-skills/loomspan-docs/references/java-api/README.md`, present before this ticket's code change. The ticket's new public contract will also require corresponding documentation updates after implementation.

## Historical Context (from ai/thoughts/)

The only matching ticket/research/plan path found under `ai/thoughts/` is the current PR 10.1 ticket. Its `Pipeline notes` explicitly identify the record-shape break as intentional and exclude compatibility shims. `ai/thoughts/framework-feature-design-lens.md` supplies the contract categories above and the design questions for supported-surface changes.

## Related Research

No prior research document on this validation metadata feature was found in `ai/thoughts/research/`.

## Open Questions

No developer decision is required by the codebase inventory. The planning step still needs to specify the exact public result construction boundary, where the checked set retains internal issues and definitions, and the verification matrix for metadata completeness and lifecycle isolation. Those are implementation-planning details within the ticket's settled behavior.
