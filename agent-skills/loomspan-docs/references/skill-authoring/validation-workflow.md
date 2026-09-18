---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: focused-source-verified
---

# Validate a draft skill set

## Applies when

An application edits YAML skills and needs feedback before staging a complete replacement generation. Use the [Java update API](../java-api/skill-reload.md) to integrate this workflow. For individual manifest rules, load the relevant topic for [models](model-selection-and-connections.md), [REST skills](rest-skills.md), [inputs](input-contracts.md), or [outputs](output-contracts.md).

## Enforced workflow

1. Supply **every proposed YAML document** to `SkillReloader.validate(Collection<SkillDocument>)`. An empty collection proposes removal of all YAML and REST skills; fixed Java skills remain. If editing configured files, call `validate()` to reread configured resource locations on each call.
2. Check `SkillValidationResult.valid()` before using `skills()` as the complete draft candidate. Each immutable `ValidatedSkill` has the exact callable name and `SkillKind` (`YAML`, `REST`, or fixed `JAVA`), sorted by exact case-sensitive name. Successful warning-only results keep the complete metadata. Any `ERROR` makes `valid()` false and `skills()` empty, so an empty list by itself does not distinguish failure from a valid empty candidate. Both result lists are immutable and detached from later framework operations.
3. Inspect `issues()` for severity, source name, optional skill name, optional field path, and message. `<documents>` marks a malformed supplied entry; `<configured-resources>` marks discovery failure. Source names in supplied documents are exact-unique diagnostic labels, not paths or callable skill names.
4. Correct errors and review warnings. The checker applies preparation's manifest, model, schema, name, child-reference, input-contract, and REST handler declaration rules. Schema-complexity warnings appear in the result without repeated editor-warning logs. Independent documents can each report a problem; an invalid document may stop its own checks, and dependent child-reference diagnostics may be deferred.
5. When ready, call `prepare()` or `prepare(documents)` with the current complete set. Stage application-owned resources from the prepared candidate's `snapshot()` under its new generation ID, then `publish` that candidate.

## Limits

Validation is advisory. It creates no candidate or generation ID and does not change the active catalog, fixed caches, REST handler instance, or retirement state. Preparation checks and freezes its own current input; files or documents edited after validation can fail preparation. Successful validation does not guarantee resource staging or publication. Validation does not invoke a skill or model, construct a REST handler, test service connectivity, or authorize publication. Bean construction, shutdown, ownership, staleness, and one-shot publication remain later lifecycle checks. Use the [Java update API](../java-api/skill-reload.md) for the publication lifecycle.

## Evidence

- `YamlSkillCatalogTests` covers independent-document diagnostics, source and path fields, complexity warnings, and suppressed editor warning logs.
- `SkillGenerationManagerTest` covers whole-set parity, fixed declarations, ID continuity, handler construction, and premature validation rejection.
- `SkillReloaderTest` and `PublicSkillReloadIntegrationTest` protect candidate and public publication behavior.
- `YamlSkillCatalog.checkedConfigured/checkedSupplied`, `SkillGenerationManager.check`, and `DefaultSkillReloader.validate` own the implementation.
