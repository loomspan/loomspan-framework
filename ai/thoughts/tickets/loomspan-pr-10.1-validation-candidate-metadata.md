# PR 10.1 — Expose candidate skill metadata from validation

## Outcome

Extend `SkillReloader.validate()` so applications can inspect the names and kinds
of every skill in a successfully checked candidate without preparing a
publishable generation. This is a focused improvement to the existing standalone
validation feature.

Loomspan Sidecar manages complete snapshots of skill YAML and application-owned
REST routes and targets. Diagnostics alone cannot tell it whether every proposed
REST skill has a route, every route names a proposed skill, and each route names
a REST skill rather than a model-backed or Java skill. The active catalog describes
the wrong configuration; preparing a generation solely for discovery introduces
effects that standalone validation deliberately avoids. Loomspan owns skill
identity and kind; Sidecar owns route and HTTP-client validation.

## Requirements

- Add an immutable `skills()` list to `SkillValidationResult`, containing only
  callable name and existing public `SkillKind`. Use a small public
  `ValidatedSkill(String name, SkillKind kind)` record under `ai.loomspan.api`.
  Model-backed YAML, Java, and REST skills use `YAML`, `JAVA`, and `REST`
  respectively. Names are exact callable identities, not document source labels.
- Successful results contain the complete effective candidate: supplied or
  configured YAML/model-backed and REST declarations, plus fixed Java skills.
  Supplied documents remain a complete replacement of YAML and REST declarations.
  Empty supplied input therefore retains only fixed Java skills, or returns an
  empty list if there are none.
- `valid()` retains its existing no-ERROR rule. Warnings preserve complete
  metadata. Any error yields an empty metadata list while retaining useful
  diagnostics; never expose a partial candidate as complete. Existing duplicate
  and conflict checks remain authoritative, and valid results have unique names.
- Sort metadata by exact, case-sensitive skill name, consistent with
  `SkillCatalog`. Both returned collections and their values are immutable and
  detached from caller collections and later validation, preparation, or
  publication. Both validation overloads have identical result semantics while
  retaining their existing input sources.
- Reuse the shared checking path and its checked definitions and fixed Java
  declaration information. Keep checked definitions and diagnostics internal;
  materialize the public metadata when returning validation feedback. Preparation
  must not build or sort an otherwise unused validation metadata list.
- Remove the parser-level `CheckedDocuments.result()` conversion to the public
  validation result and update its test callers to inspect internal diagnostics.
  That parser-level conversion cannot establish the new complete-candidate
  contract because it precedes fixed Java and framework-wide checks. This cleanup
  is in scope; do not preserve a competing partial public-result path.
- Validation must not allocate or advance generation IDs, construct a publishable
  candidate, alter active/publication/retirement state, initialize or modify fixed
  framework caches, construct or invoke REST handlers, or invoke skills, models,
  or external services. Preserve the existing completed-startup requirement.
- Preserve `prepare()` and `publish()` behavior and signatures. Validation remains
  optional and advisory. Preparation independently reads/checks/freezes its own
  input and resolves its runtime dependencies; publication retains ownership,
  freshness, single-use, activation, and retirement safeguards. Validation neither
  reserves input nor authorizes publication nor invalidates an existing prepared
  candidate. Do not accept a validation result as a preparation/publication token.
- Document that callers must check `valid()` before using metadata as a complete
  candidate. Empty metadata alone cannot distinguish an error from a valid empty
  configuration. Successful validation does not guarantee later preparation,
  resource staging, or publication. Applications staging resources for publication
  should use the prepared candidate's existing snapshot as the authoritative
  description of that frozen candidate.
- Deliberately allowlist and document the new supported API type and updated
  result. Include an integration example using only supported `ai.loomspan.api`
  types to extract candidate REST skill names after successful validation.

## Acceptance criteria

- [ ] A valid mixed candidate returns exact callable names and correct public
  kinds for model-backed, REST, and fixed Java skills. Metadata reflects proposed
  additions, removals, and kind changes rather than the active catalog.
- [ ] Warning-only results retain complete metadata; malformed input, duplicate
  names, and conflicts retain useful diagnostics and return no metadata.
- [ ] Empty supplied input returns only fixed Java metadata, including the case
  with no fixed Java skills. Configured-resource and supplied-document overloads
  provide equivalent semantics for equivalent inputs.
- [ ] Metadata has unique names and deterministic exact-name ordering. Returned
  collections cannot be mutated, and later caller changes or framework operations
  do not change earlier results.
- [ ] Validation preserves state isolation, including generation-ID continuity,
  fixed caches, zero handler construction/invocation, unchanged active catalog,
  and no retirement notifications. A candidate prepared before validation remains
  publishable afterward when no other operation makes it stale.
- [ ] Direct preparation/publication still works without validation and retains
  its existing checks and lifecycle behavior. Preparation checks its own input
  and does not materialize unused public validation metadata; public metadata
  comes from the complete shared check rather than a parallel parser or validator.
- [ ] Supported-surface checks, including `LoomspanPublicSurfaceArchitectureTest`,
  pass. Focused tests and a public-API integration example demonstrate the new
  contract, and affected in-repository callers and documentation use it coherently.
- [ ] Documentation explains completeness, warning/error behavior, ordering,
  immutability, fixed Java retention, advisory status, and unchanged preparation
  and publication responsibilities. Required framework checks pass and the updated
  `1.0.0-beta.5-SNAPSHOT` is installed locally.

## Context and scope

Evaluate implementation choices through
[the framework feature design lens](../framework-feature-design-lens.md).
Reuse framework-owned facts rather than making Sidecar interpret framework YAML.
The new record is a narrow immutable projection, with no description, schema,
executable handle, generation ID, or full runtime catalog. Do not add a parser,
validation rule implementation, SPI, bean-replacement contract, cache, lifecycle
stage, or generation-shaped validation catalog. Do not implement validation by
calling `prepare()`.

The affected supported contract is the application Java API. Skill-author YAML,
configuration semantics, invocation authorization, and publication protocols are
unchanged. No persisted/serialized contract or Console compatibility-marker
change is intended. Assess any newly discovered wider impact explicitly rather
than expanding this small feature silently.

Sidecar changes are excluded. Sidecar can adapt its existing route validator
after the updated framework snapshot is installed. No route or HTTP-client
validation moves into Loomspan. The prepared snapshot remains the application
integration point for actual resource staging.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The feature is bounded and the design direction is settled, but
  it deliberately changes a supported application API contract. The execution
  profile eligibility rules require the full route for that change even though
  preparation and publication behavior must remain unchanged.
- **Reassessment triggers:** Discovery of wider lifecycle, concurrency, security,
  configuration, or serialized-contract impact requires an explicit scope and
  design assessment; retain the full route.

## Pipeline notes

- PR 10.1 is the developer-assigned ticket identifier for this improvement; it
  does not assert that a GitHub pull request with that number exists.
- Breaking the existing `SkillValidationResult` constructor and its record shape
  is intentional. No backward compatibility is required for this change. Do not
  add a one-argument compatibility constructor, deprecated API, alias, adapter,
  fallback, or legacy behavior. Update all affected in-repository callers, tests,
  samples, and documentation atomically. Favor one correct coherent contract and
  minimum technical debt. Record the intentional API impact in execution artifacts.
