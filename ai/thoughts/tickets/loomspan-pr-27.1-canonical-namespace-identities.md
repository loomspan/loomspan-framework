# PR 27.1 — Establish canonical namespace identities and catalogs

> Superseded input rule: [PR 27.6](loomspan-pr-27.6-optional-namespace-references.md)
> permits local host input in `default` and owner-relative local authored references.
> Its boundary rules supersede conflicting mandatory-qualification or rejection
> requirements here. Internal identities, catalog lookup, model/runtime selections,
> access, publication and observation contracts remain unchanged.

## Outcome

Give every skill one unambiguous `namespace-skillName` identity across definitions, catalogs, and references, providing the foundation for independently published namespaces.

## Requirements

- Use one internal parser/formatter and structured identity at resolution and authorization boundaries. Namespace and local components start with an ASCII letter or underscore and contain only ASCII letters, digits, or underscores. Normalize only namespace case to lowercase; preserve case-sensitive skill names. Do not trim or replace characters. Namespace length is at most 32 characters; combined identity length, including its single hyphen, is at most 64.
- Declaration `name` and `@SkillMethod.name` remain local components matching `[A-Za-z_][A-Za-z0-9_]{0,63}`. Construct identities from authoritative ownership and enforce the combined limit even for skills never offered as tools. Add `@SkillMethod.namespace`, defaulting to `default`, with the same namespace normalization and limits.
- Require qualification for every reference, including same-namespace and default references, authored children, allowed skills, `output_from`, and input/output bindings. Reject unqualified, slash/colon-separated, whitespace-containing, oversized, or otherwise malformed references. No shorthand, aliases, imports, wildcard search, fallback lookup, encoding, or implicit Public-namespace search.
- Keep `SkillGeneration` as one namespace's immutable executable generation. Capability keys equal canonical `CapabilityMetadata.name()`; definition keys equal the identity constructed from generation ownership and the local manifest name. Validate ownership and use resolved identities for duplicates and binding comparisons.
- Keep immutable, deterministically ordered, unfiltered generation catalogs. Add canonical `SkillCatalog.namespace()`; retain the generation's opaque ID. Descriptor and validated-skill names are qualified. Qualified catalog lookup stays within that catalog, returning the existing empty result for unknown identities, including another namespace's identity; malformed arguments fail. Empty catalogs identify their namespace.
- `SkillReloader.snapshot(namespace)` selects the current namespace generation once; missing/removed namespaces fail. Remove the injected startup `SkillCatalog` bean and no-argument acquisition path. Retained catalogs remain unchanged and hold no executable resource lease. No aggregate/live catalog, enumeration feature, cache, or new public identity type.
- Prepared candidate catalogs and validation feedback cover only the target namespace's supplied declarations and fixed Java registrations. Validation feedback allocates/reserves no generation, and errors retain existing incomplete-proposal semantics. Candidate IDs remain distinct from base IDs.
- Establish shared identity handling for later root/model dispatch work without rewriting raw prompts or diagnostic content. Update relevant supported API documentation, examples, and surface tests directly.
- Parse authored declarations/references during preparation, and host arguments once at their operation boundary; retain structured identities rather than repeatedly splitting strings during execution. Apply namespace normalization and the independent 32-character limit to management arguments as well as qualified references. A namespace of length N leaves `63 - N` characters for the local component; the standalone declaration grammar does not waive this combined limit.
- `PreparedSkillUpdate.snapshot()` returns its immutable candidate namespace catalog and `PreparedSkillUpdate.generationId()` identifies that candidate, not its captured base. `ValidatedSkill.name()` identifies the checked proposal's qualified skill. A catalog is metadata, not authorization or an execution reservation; do not create a global catalog revision, synchronization service, or additional catalog lifecycle.
- Namespace renaming means ordinary creation of a new namespace followed by removal of the old one, with explicit reference updates. Do not add identity-preserving rename or namespace inheritance.

## Acceptance criteria

- [ ] Two namespaces can declare `search` with distinct canonical generation keys and descriptor names; namespace-case normalization detects duplicates while skill-name case and `a_b-c` versus `a-b_c` remain distinct.
- [ ] Namespace lengths 32/33 and combined lengths 64/65 exercise acceptance/rejection independently across declarations, Java registrations, and references, including optional children and unoffered skills. Invalid diagnostics identify the declaration/reference, field/source, length, and limit.
- [ ] Catalogs, including empty ones, identify their owning namespace and generation; foreign/unknown lookup returns empty, malformed lookup fails, and retained catalogs do not change after replacement/removal.
- [ ] Candidate feedback contains only target supplied/fixed skills, preserves validation side-effect rules, and reports canonical semantic identities without modifying raw diagnostic content.
- [ ] Boundary coverage includes startup loading, publication validation/preparation, Java/REST declarations, same-namespace references, and namespace-only management arguments. Catalog lookups normalize namespace case while preserving local-name case. Newly acquired catalogs select current generations; retained catalogs keep their original metadata and candidate ID semantics.
- [ ] No rename/inheritance operation or extra catalog lifecycle/revision is introduced; documentation explains explicit reference updates for namespace replacement under a new name.
- [ ] The public surface and README express the changed contracts without internal types, duplicate local accessors, aliases, or an injected startup catalog. Run `LoomspanPublicSurfaceArchitectureTest`; record any downstream build blocker for rerun rather than treating it as a pass.

## Context and sequencing

First of five sequential implementation tickets, PR 27.1–27.5, sized for GPT 6.1 Sol. The accepted [namespace design](../phases/skill-namespaces.md), especially Identity rules, Generation layering, Catalog contracts, and Common tool/reference acceptance coverage, supplies the detailed contract. PR 27.2 connects catalog acquisition to the active registry; PR 27.3 integrates root admission; PR 27.4 completes model/child dispatch; PR 27.5 updates observation consumers. Do not introduce temporary compatibility infrastructure to bridge these slices.

Use the simplest complete implementation and existing machinery. Supported Java API stays in the closed `ai.loomspan.api` allowlist; `RestSkillHandler` remains the sole SPI. No Cloud integration, optional Java implementation configuration, new Console features, or new resource measurement belongs to this series. Measurement is [post-implementation follow-up](../phases/skill-namespaces-followup.md).

The linked design's accepted contracts and acceptance scenarios are normative, not optional background. This ticket owns identity parsing, generation/catalog invariants, annotation/API shape, and their focused checks. PR 27.2 owns actual active-registry acquisition, startup grouping, and management-boundary checks; PR 27.3 owns descriptor-driven root behavior; PR 27.4 owns model/provider boundaries. Those named dependencies may defer integration checks, not silently drop requirements. Implementation planning may choose concrete internal types/signatures where the design leaves them open; it must not reopen settled behavior because a ticket summarizes it.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Changes supported API semantics and identity invariants across production consumers.
- **Reassessment triggers:** Reassess only if scope materially changes; integration types and source locations remain pipeline research/planning work.

## Pipeline notes

- Destructive API and behavior changes are authorized; remove superseded paths directly without compatibility shims or dual formats.
- A local commit follows each ticket, with no sync between tickets. Intermediate code may be broken because downstream slices are incomplete. Document exact downstream verification blockers instead of adding temporary bridges or claiming checks passed. Each ticket owns its meaningful verification; PR 27.5 must close all series integration gaps before completion.
