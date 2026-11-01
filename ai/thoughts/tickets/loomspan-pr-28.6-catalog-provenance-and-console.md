# PR 28.6 — Show Java execution and YAML provenance coherently in catalogs and Console

## Outcome

Catalogs describe one callable skill with its actual execution kind and declaration provenance. Executor-backed YAML skills appear as Java skills while retaining their authored YAML, executor binding, and Java implementation details throughout Framework and Console.

## Requirements

- Complete PR 28.3's separation of execution classification from declaration provenance. SkillMethod and executor-backed YAML both use `SkillKind.JAVA`; model-backed YAML uses `YAML`, and TypeSafe YAML uses `TYPESAFE`. Do not add an EXECUTOR kind or expose provenance as another public application API.
- Public catalogs list callable YAML skills, never reference-only executor registrations. Diagnostic details for executor-backed skills preserve original authored YAML/source location and identify the referenced executor and Java implementation. SkillMethod retains annotated bean/method provenance; model and TypeSafe declarations retain YAML provenance. Locations are diagnostics, not invocation identities.
- Replace the assumption that every Java entry must have only bean/method provenance and cannot contain YAML/source paths. Use actual registration provenance for definition lookup and source presentation; execution kind selects execution behavior/classification. Do not infer behavior from YAML presence or introduce an executor-specific catalog/lifecycle.
- Update Framework diagnostic metadata/catalog construction and validation, observability protocols, Go DTO/service validation, browser and MCP contracts/schemas/output, TypeScript types/rendering, fixtures, and guidance to the same final contract. Account for all consumers, not only the visible UI. Preserve existing access boundaries and content fidelity.
- Remove remaining REST execution-kind/catalog/source-format support and obsolete fixtures across these current contracts. A generic HTTP REST endpoint or REST API fixture name does not itself mean obsolete REST skill support; remove the obsolete skill concept without deleting unrelated transport functionality.
- Assess the Console exact-version compatibility marker under the existing policy. Current writer/reader/projection/fixture coherence is required; no aliases, fallback readers, schema adapters, historical compatibility fixtures, or migration path. Coordinate with PR 28.5's failure-contract changes without creating a second representation.
- Confirm PR 28.3's mandatory `CapabilityKind` assessment is reflected everywhere: remove remaining translation callers if it was redundant; if retained, implementation evidence must identify its distinct behavior. Do not reintroduce a duplicate enum to encode provenance.

## Acceptance criteria

- [x] Both Java forms are classified JAVA in public/diagnostic catalogs, while reference-only executors are absent from callable surfaces. Same-name callable/executor identities remain unambiguous.
- [x] Console skill details show unchanged executor-leaf YAML/source information plus executor identity and Java implementation; SkillMethod, model-backed YAML, and TypeSafe provenance still render correctly.
- [x] Framework, Go service/DTO validation, browser/MCP outputs and schemas, TypeScript contracts/UI, and current fixtures agree on provenance and classification. Tests reject obsolete assumptions that Java execution forbids YAML.
- [x] REST skill-kind/provenance branches and compatibility mappings are absent from current contracts/fixtures. `CapabilityKind` removal or concrete distinct-behavior justification remains traceable; no duplicate classification is reintroduced.
- [x] Relevant Java/Go/Console contract and fixture checks, supported-surface architecture checks for production type changes, and documentation updates pass. The exact-version compatibility-marker assessment is recorded.

## Context and sequencing

Depends on [PR 28.5](loomspan-pr-28.5-explicit-failure-handling-and-diagnostics.md) and builds on PR 28.3's generation/routing metadata. Owns finding 5's cross-component provenance completion and finding 8's Console REST cleanup in [Configured Java Skill Executors](../phases/configured-java-skill-executors.md). Apply the [feature design lens](../framework-feature-design-lens.md) and Console repository guidance.

Source recheck: `DefaultRegisteredSkillCatalog` branches on kind to choose YAML versus bean/method details. Console `internal/observability/service.go` and DTO validation reject Java source paths/YAML; MCP output schemas and formatting make the same distinction. Those are current protocol assumptions to replace directly, not compatibility contracts to maintain. Exact internal provenance types and wire field names remain implementation choices.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Shared Java/Go/TypeScript diagnostic contracts and catalog semantics change together.
- **Reassessment triggers:** Discovery of an additional current contract consumer; expand coherent updates within scope, not historical compatibility support.

## Pipeline notes

- Framework and Sidecar have no customers/deployments. Current protocol and enum changes are authorized, including broken intermediate builds; no shims or dual formats. Consumer-specific Sidecar migration is outside scope and cannot block REST removal.
- Catalog visibility remains unchanged; planner filtering belongs to PR 28.4. Do not create a new public provenance API or independently callable executor surface.
