# PR 27.6 — Make namespaces optional at authoring and host-entry boundaries

## Outcome

Let applications that do not need namespaces invoke skills by local name, and let skill authors reference other skills in their own namespace by local name. Preserve one canonical, fully qualified identity throughout the framework after these input boundaries.

The namespace plan and PR 27.1–27.5 incorrectly extended the requirement for canonical internal identities into a requirement for qualification everywhere. This ticket corrects that authoring and invocation contract while retaining namespace ownership, independent publication, and Public/Private visibility.

## Requirements

- Every `SkillTemplate` invocation and validation overload, including observer invocation, accepts a local name such as `search` as `default-search`. Apply the same rule to both input forms of `SkillInvocationHandoff`. Explicit qualified references retain their existing meaning. Host-entry shorthand always uses `default`, regardless of any ambient executing namespace.
- Resolve an unqualified authored reference against the owning namespace of the skill containing it. A skill published into `sales` referencing `search` calls `sales-search`; the same document loaded into `default` calls `default-search`. Explicit `shared-search` continues to identify that cross-namespace target. This applies equally to YAML, REST, and `@SkillMethod` targets where those references are supported.
- Canonicalize authored references in the shared loading/preparation path for startup files and API-supplied documents, before reference comparisons, dependency validation, and executable generation construction. Publication validation and preparation must agree; do not defer qualification until `publish()` or runtime dispatch. Ownership comes from existing startup/publication context, not inferred target availability.
- Cover every structured authored skill-reference position: `allowed_skills`, `output_from`, child-result producers in input/output bindings, and skill references in evidence expressions. Compile evidence references with namespace context while preserving expression semantics and source diagnostic positions; do not use global text replacement. Existing expression grammar, including reserved operators, remains unchanged.
- Preserve original document bytes, prompt prose, and raw diagnostic content. Qualification belongs in prepared semantic representations. Declaration names remain local components; existing Java namespace assignment and its `default` value remain intact.
- Keep strict canonical parsing and fully qualified identities downstream. Generation keys, lookup, execution, dependency contracts, authorization, provider tool names, model-generated selections, REST handoffs, traces, and Java/Go observation structures retain the existing canonical contract. Do not add runtime aliases, implicit namespace searches, fallback lookup, or a second internal identity format. The deliberate implementation scope is authored-reference compilation plus host-entry argument resolution, not a redesign of execution or observation.
- A missing local target fails in its resolved namespace even if a matching local name exists in `default` or a Public namespace elsewhere. Qualification grants no authority: preserve existing declared-child restrictions, same-namespace access, cross-namespace Public/Private checks, RBAC, and captured-snapshot behavior.
- Preserve namespace normalization, case-sensitive local names, grammar, and size limits. Enforce combined identity length after qualification. Malformed qualified references must fail rather than being interpreted as local names. Compare canonical identities so local and qualified spellings of the same child are duplicates and bindings can mix those spellings consistently.
- Existing qualified callers and authored documents continue to work. No public API signature, new public identity type, SPI, or wire-format change is required. Local lookup through `SkillCatalog.skill(...)` is outside this ticket; retain its existing qualified-reference contract. Namespace management/publication API shapes and defaults are also outside scope.
- Update the namespace design, README, affected API documentation, and authoring examples to distinguish optional input qualification from mandatory internal qualification. Record this correction as superseding the conflicting requirements in the earlier tickets; those historical instructions must not restore rejection of local names in the newly supported boundaries. Keep unrelated namespace contracts unchanged.

## Acceptance criteria

- [ ] Local and explicit `default-` host references identify the same skill across invocation, validation, observer invocation, and handoff, with both input forms. Explicit non-default references retain their behavior, and missing default targets do not resolve elsewhere.
- [ ] Startup files and API validation/preparation accept local authored references. Publishing the same document into different namespaces produces references to each respective owner, including same-namespace Java skills and Private same-namespace composition.
- [ ] Allowed children, forwarding, input/output bindings, and evidence expressions resolve consistently with local, qualified, and mixed spellings. Canonically equivalent duplicate children are rejected, and unresolved local targets fail without fallback.
- [ ] Existing malformed-reference, case, namespace-length, and combined-length rules still hold, including identities that exceed the limit only after the owner namespace is supplied. Source bytes, prompts, and evidence diagnostic positions remain faithful to authored content.
- [ ] Prepared/runtime identities remain qualified. Existing qualified execution, model tool/plan selection, REST identity, and observation contracts continue to work without downstream shorthand handling or Java/Go contract changes. Public/Private, RBAC, declared-call restrictions, and captured execution behavior remain intact.
- [ ] Supported signatures, catalog lookup semantics, and management API behavior remain unchanged. Documentation clearly describes the new boundary rules and supersedes conflicting design requirements. Required supported-surface verification, including `LoomspanPublicSurfaceArchitectureTest` after production-type changes, passes alongside affected behavioral checks.

## Context and sequencing

This is a corrective follow-up to PR 27.1–27.5, not a replacement namespace design. See [Skill Namespaces](../phases/skill-namespaces.md), [PR 27.1](loomspan-pr-27.1-canonical-namespace-identities.md), and [PR 27.3](loomspan-pr-27.3-reachable-admission-snapshots.md) for the original mandatory-qualification requirements being corrected.

Prior source review identified `YamlSkillCatalog` as the shared authored-document path and `DefaultSkillTemplate` preparation as the shared host boundary also used by handoff. These are implementation leads to verify, not prescribed source edits. Keep the change concentrated at those boundaries; relaxing the global strict parser or teaching downstream execution to resolve local names would defeat the selected simplification.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The implementation direction is bounded and settled, but the change deliberately expands the supported invocation contract and authored reference semantics across binding and evidence compilation. The shared protocol classifies supported API contract changes as a full-profile trigger.
- **Reassessment triggers:** Unexpected downstream dependence on raw authored reference strings, additional structured reference positions, or a need to alter public signatures or serialized contracts must be assessed against the boundary-only scope before broadening the work.

## Pipeline notes

- Accepting local references at the specified boundaries intentionally supersedes earlier requirements and tests that reject all unqualified references. Preserve strict rejection in internal canonical-only paths and the unchanged catalog contract.
- PR 27.6 is the developer-assigned proposed ticket identifier. Creating this ticket does not authorize starting the implementation pipeline.
