# PR 10 — Validate skills without changing framework state

## Outcome

Applications need to validate skill edits repeatedly and present actionable feedback before committing to a skill update. Add programmatic validation that leaves Loomspan state unchanged. When editing is complete, the application continues to use `prepare()` followed by `publish()`.

Support both application-held YAML documents and skills read from configured local resource locations.

## Requirements

- Add `SkillValidationResult validate()` and `SkillValidationResult validate(Collection<SkillDocument> documents)` to the framework-owned `SkillReloader` API. The no-argument method rereads configured skill resources. The collection overload validates the complete proposed replacement YAML set, using the same source-label and document rules as `prepare(documents)`, without reading labels as paths or writing files.
- Validate against fixed framework configuration and Java skill declarations. Reuse the existing preparation rules, including manifest parsing, schemas, model configuration, REST manifest restrictions, duplicate names, conflicts with Java skills, child-skill references, and REST handler declaration availability. An empty collection is a valid proposed removal of all YAML and REST skills while retaining fixed Java skills.
- Extract a shared internal validation pipeline used by both validation and preparation. Preparation must consume the validated definitions from its own pass, rather than validate and then reread the documents. Do not maintain separate copies of authoring rules.
- Validation must not allocate generation IDs, create publishable candidates, activate generations, update shared dependency caches, instantiate REST handlers, invoke skills or models, notify retirement listeners, or alter pending candidates or their publication eligibility. Avoid accessing lazy initialization paths that activate a generation. Temporary parsing objects and read-only resource access are expected.
- Return an immutable validation result exposing `valid()` and `issues()`. Issues must provide severity, source name, optional skill name, field path when available, and a human-readable message. Ordinary invalid skill content is reported as diagnostics rather than requiring callers to parse exception messages. Errors make the result invalid; warnings alone do not.
- Surface existing schema-complexity warnings in the result rather than emitting them only to logs. Repeated editor validation must not emit those authoring warnings to logs.
- Collect diagnostics across independent documents rather than abandoning the entire set on the first invalid document. Exhaustive reporting of every error within an invalid document is not required; parsing and dependent checks may stop when earlier failures prevent meaningful validation. Avoid misleading follow-on reference errors caused solely by documents that failed parsing or validation.
- `prepare()` must always validate its current input, regardless of previous validation calls. A validation result is feedback, not a frozen candidate or a guarantee of later preparation/publication success. Handler construction and lifecycle checks remain preparation concerns; stale-candidate and publication checks remain publication concerns.
- Preserve supported preparation and publication behavior, including `SkillReloadException` failure semantics, frozen candidates, process-local generation identity, ownership, staleness, shutdown handling, and retirement notifications. No compatibility break is authorized.
- Keep implementation types internal. Deliberately add the result and issue types to the closed public API allowlist, document the new supported API in the README, and provide supported-surface coverage. This feature must not introduce a new SPI or bean-replacement contract.

## Acceptance criteria

- [ ] Applications can validate either configured resources or an in-memory complete replacement set and receive immutable, structured errors and warnings suitable for editor feedback.
- [ ] Both overloads apply the same authoring and whole-set rules as preparation, including fixed Java declarations, model configuration, REST handler declarations, name conflicts, child references, source-label constraints, and empty-set semantics.
- [ ] Invalid documents do not prevent feedback on independent documents. Warning-only results remain valid, and existing schema-complexity warnings are returned without editor-validation log noise.
- [ ] Repeated successful and unsuccessful validation leaves framework state unchanged: no generation IDs are consumed, handlers are not instantiated or invoked, active catalogs and dependency caches are unchanged, retirement callbacks do not run, and already-prepared candidates remain equally publishable.
- [ ] The supplied-document overload does not read configured skill files or interpret source labels as paths; neither overload writes skill files. The no-argument overload observes edits to configured resources on subsequent calls.
- [ ] Validation and preparation share the underlying checks. Preparation independently validates and freezes its own input, so edits after validation are checked and edits after preparation cannot change its candidate.
- [ ] Existing preparation/publication compatibility and lifecycle behavior remain intact, and a successful validation is not represented as authorization or a guarantee to publish.
- [ ] The README explains both overloads, complete-set semantics, diagnostics, the no-state-change guarantee, and the validate/edit then prepare/publish workflow. Public signatures expose no internal or autoconfiguration types, and `LoomspanPublicSurfaceArchitectureTest` passes with the deliberate API additions.

## Context

Validation currently spans `YamlSkillCatalog` document loading and `SkillGenerationManager` whole-set preparation. Simply calling `prepare()` and discarding the result does not satisfy the requirement: preparation allocates generation IDs, initializes cached dependencies, and can instantiate a REST handler. Accessing the active generation can also trigger initialization. Existing authoring checks generally throw on the first failure, and schema-complexity warnings currently go to logs.

The intended internal separation is document validation and complete-set checks, followed only during preparation by runtime binding and generation construction. The exact internal class structure is left to planning. Validation checks REST handler declarations without constructing the handler; actual bean construction can still fail later during preparation.

Single-document validation against an implicit merge with the active YAML set, runtime execution testing, external service connectivity checks, an editor UI, and publishing a validation result are outside this ticket. The application supplies the complete proposed set when it needs cross-skill validation for in-memory edits.

PR 10 is the user-assigned correlation label for this ticket; this file does not create a GitHub issue or pull request.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This adds supported public API and refactors validation across document loading and generation preparation. Preserving compatibility and demonstrating absence of lifecycle and shared-state effects require research, planning, verification, and independent review.
- **Reassessment triggers:** Reassess scope if the current checkout introduces additional validation side effects or public consumers that change the compatibility analysis. The supported API change already requires the full profile.
