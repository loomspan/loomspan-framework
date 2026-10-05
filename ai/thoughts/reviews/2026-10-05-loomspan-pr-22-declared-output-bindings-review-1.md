# PR22 Declared Output Bindings — Independent Review 1

## Code Review Findings

### [P2] Match binding destinations with the native output property rule

- **Location:** `src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java:126` (initial candidate).
- **Evidence:** New output binding canonicalization, overlap detection, projection, override checks, insertion and static compatibility used `String.equalsIgnoreCase`. The existing catalog and `OutputSchemaValidator#validateObject` instead normalize keys with `toLowerCase(Locale.ROOT)`. These rules differ for valid Unicode keys: `i` and dotless `ı` compare equal with `equalsIgnoreCase`, but remain separate native output properties.
- **Trigger:** A declared output schema contains distinct `i` and `ı` fields, or an open schema binds `/i` while the model contributes the distinct field `ı`.
- **Impact:** `/ı` can be canonicalized to `/i`, two valid destinations can be rejected as overlapping, residual model space can be classified as fully bound, or a legitimate model contribution can be rejected as an override. This violates the native output contract and exact destination ownership.
- **Recommendation:** Use the native output normalization consistently in every new output binding name comparison; retain exact source-key lookup.

### [P2] Make the assembly SSE golden fixture deterministic

- **Location:** `src/test/java/ai/loomspan/internal/observability/web/ConsoleSseFixtureCorpusTest.java:48` (initial candidate).
- **Evidence:** The added assembly activity constructs its three detail fields with `Map.of`, then compares serialized SSE bytes against the committed fixture. Java immutable-map iteration order varies across JVM runs. The independent full regression failed on the assembly fixture: the generated ordering was `skillName`, `modelContributionRequired`, `owningMissionFrameId`, while committed bytes used `owningMissionFrameId`, `modelContributionRequired`, `skillName`.
- **Trigger:** A JVM iteration seed produces a different iteration order than the JVM that regenerated the fixture.
- **Impact:** The required deterministic Java regression fails without any contract change, obscuring real Java-to-Go boundary regressions. Running with fixture regeneration hides this problem.
- **Recommendation:** Construct detail fields in explicit insertion order, as the adjacent forwarding fixture already does.

## Findings Resolved in This Context

- **P2, Unicode matching:** Added internal `OutputSchemaValidator.propertyNamesMatch`, matching the validator's `Locale.ROOT` normalization. Updated all new output-specific comparisons in declaration overlap, destination canonicalization, static compatibility, projection and composition. Added `OutputBindingCompositionTest#distinctUnicodeOutputPropertiesRetainNativeOutputMatching` and `YamlSkillCatalogTests#outputBindingsKeepUnicodePropertiesDistinctUnderNativeOutputMatching`. Existing ASCII alias protection remains tested. Source paths remain exact object-key selection.
- **P2, SSE fixture order:** Replaced the new assembly detail `Map.of` with `LinkedHashMap` in the committed fixture's order. No protocol payload or fixture bytes changed.
- Completed a second internal review of the resulting configuration/runtime/diagnostic/documentation change. **No actionable findings remain.** No unrelated implementation files, PR23 ticket, commits, releases, or paid-provider runs were changed.

## Open Questions and Assumptions

- No developer question remains. The approved profile is Full 5-Step Pipeline.
- Development/no-shim authorization is recorded in research and the implementation plan. It permits atomic internal changes; the ticket's explicit forwarding, ordinary synthesis, authorization, isolation and content-fidelity guarantees remain requirements.

## Verification Results

- PASS — `.\mvnw.cmd -q '-Dtest=OutputBindingCompositionTest,OutputBindingIsolationTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,PlanOutputBindingProducerValidatorTest,YamlSkillCatalogTests,SkillGenerationManagerTest,LoomspanPublicSurfaceArchitectureTest' test`: independently verified the initial candidate's focused behaviors and supported-surface inventory.
- FAIL — `.\mvnw.cmd -q test *> target-pr22-review-1-java.log`: initial candidate ran 1,433 reported cases, with one failure in `ConsoleSseFixtureCorpusTest`, zero errors, and two existing opt-in diagnostic skips. Failure attributed to the nondeterministic assembly SSE detail order described above; all other suites passed.
- PASS — `.\mvnw.cmd -q '-Dtest=OutputBindingCompositionTest,YamlSkillCatalogTests,SkillGenerationManagerTest,OutputBindingIsolationTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,OutputSchemaValidatorTest,LoomspanPublicSurfaceArchitectureTest' test *> target-pr22-review-1-fixed.log`: Unicode regressions and both execution paths passed after the matching fix; no failures/errors.
- PASS — `go run ./internal/buildtool verify` from `loomspan-console/`: declared toolchain and locked frontend graph, TypeScript typecheck, 46 frontend test files / 524 tests, production assets and all Go packages passed in this review context. Review fixes do not change Console source or serialized protocol bytes.
- PASS — `git diff --check *> target-pr22-review-1-diff.log`: no whitespace errors; Git emits line-ending normalization notices.
- FAIL — `.\mvnw.cmd -q '-Dtest=ConsoleSseFixtureCorpusTest,ConsoleTraceFixtureCorpusTest,StepLoopMissionExecutionEngineTest,LoomspanPublicSurfaceArchitectureTest' test *> target-pr22-review-1-boundaries.log`: 110 cases, sole failure SSE fixture ordering. First deterministic builder order did not match stored bytes; aligned insertion order with committed bytes before final verification. All other boundary and architecture cases passed.
- PASS — `.\mvnw.cmd -q '-Dtest=ConsoleSseFixtureCorpusTest,ConsoleTraceFixtureCorpusTest,StepLoopMissionExecutionEngineTest,LoomspanPublicSurfaceArchitectureTest' test *> target-pr22-review-1-boundaries-final.log`: final canonical SSE/NDJSON byte comparisons, output/forwarding/parallel step-loop behavior and supported-surface architecture all passed without fixture regeneration; 110 cases, zero failures/errors/skips. No production changes followed these checks.

## Requirements and Plan Conformance

### Scope and independent reconstruction

Reviewed the working-tree change on `main` at the supplied research baseline (`1b5c1a63bf0beb538119be05a8b5b361c96f8e72`). No staged diff or new committed branch work is involved. Included untracked production composition/projection/producer-validation classes, tests, SSE/NDJSON/expected fixtures. Excluded prior reviews (none read) and unrelated untracked PR23 ticket. Research/plans are contextual evidence, not proof of implementation correctness.

Production inventory: strict YAML catalog/manifest/definition and complete-generation validation; output projection/composition; invocation-local MissionContext snapshot; ordinary engine/advisor and planning validation/step-loop; trace/state/journal/activity; current Go analysis/live/browser/MCP facts and schemas; frontend presentation. Test/config/documentation inventory: declaration/generation, exact composition/isolation, deterministic facade integrations, step lifecycle, canonical Java-to-Go corpus, discovery contracts, frontend tests, authoring guide and README. No dependency, migration, authentication route, supported Java API or SPI additions.

### Acceptance mapping

| Ticket criterion | Executable evidence examined |
| --- | --- |
| Shared destination map/from/skill/path, whole/subtree and input/model/Java/REST sources | Shared `YamlSkillCatalog#validateRawBindings`, generation schema checks, `ChildInputBindingAssembler`/decoder reuse, catalog/generation tests, six planning integration variants |
| Two children plus input, exact numbers/arrays and no final synthesis | Planning integration full cases; step engine full-bound admission and latch-held two-producer join test; subsequent unrelated task completion |
| Mixed ordinary output, projected initial/corrective ownership and nested required siblings | Native projection and composition tests; ordinary integration request assertions, override recovery, optional-ancestor and optional/open residual cases |
| Mutual exclusion, forwarding and ordinary no-binding preservation | Raw conflict validation naming both fields; unchanged explicit forwarding path and ordinary contracts; full initial regression covered existing forwarding/PR21/ordinary suites |
| Invalid static/dynamic sources, null versus absence, ambiguity, stale/cross-owner and no fallback | Catalog/generation/count tests; source-validation and isolation tests; step retained result/stale/invisible producer tests; pre-model dynamic type/evidence failures |
| Overrides, bounded correction, stable values and no child replay | Composition presence/alias/ancestor checks; ordinary integration corrections/reload; planning final correction counter asserts accepted children run once; complete original schema validation |
| Parallel/nested/concurrent/reload and access | Mission-local captured immutable selections and explicit binding ownership; nested same-name/task-ID helper test; concurrent roots, held correction reload; joined execution units with concurrency on/off; existing lifecycle/RBAC/generation suites and invisible-producer gate |
| Exact provenance/raw-versus-assembled evidence/skipped synthesis | Owner-frame `RESULT_ASSEMBLED`; Java corpora, Go strict decoder and accepted-plan ownership validation, browser/MCP/live mappings, raw/source/assembled payload fixtures and frontend details |
| Authoring examples/modes/failures and deterministic verification | Complete full/mixed schemas and four-mode table; routed guides and README; local scripted providers only |

- **Partial/missing runtime requirements:** None identified.
- **Safe deviations:** Final test names combine projection/assembler and trace concerns into composition/isolation and existing canonical suites; behavior is checked rather than provisional class names. Source selection uses existing `ChildInputBinding` machinery without a redundant descriptor rename. Historical pre-fix red evidence was not established, explicitly recorded in the implementation/testing artifacts; this review establishes post-change behavior independently and does not claim historical red evidence.
- **Compatibility review:** Closed application-facing allowlist unchanged; three new technically public collaboration types remain internal. Sole `RestSkillHandler` SPI unchanged; no internal types leak into supported signatures, new replacement beans, aliases or shims. New manifest semantics and current `RESULT_ASSEMBLED` protocol update repository consumers together. Exact compatibility-marker rules and development-validation behavior remain unchanged; no historical reader or marker bump is introduced.

### Risk lenses checked

Traced ordinary fast/mixed and planned full/mixed paths through access checking, captured generation, immutable input/task results, final admission/join, contribution correction, complete schema/evidence/linter validation and owner-frame success publication. Checked error labels, non-fallback source failures, no child replay, null/absence, alias override protection, detachment, late-write fencing and current diagnostic size/payload boundaries. No new authorization grant, shared global result lookup, service call, unbounded retry, provider-native schema, inferred producer schema or persistence migration is introduced. Existing journal-only field-name redaction is preserved; canonical content, prompts, child results and invoke output are not classified or rewritten.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected; **drift classification: aligned** after checking executable behavior.
- **Rationale:** Adds output manifest syntax, field ownership, producer counts, full/mixed completion, step cost, correction and diagnostic facts.
- **Documents reviewed:** same-checkout `agent-skills/loomspan-docs/SKILL.md`; skill-authoring `README.md`, `source-verification.md`, `output-contracts.md`, and changed input/planning/concurrency/mental-model/REST/trace guidance; repository README.
- **Evidence checked:** Catalog/generation/composition/isolation tests, both facade integration suites, planning/step source, Java trace/SSE corpus, Go decoder/processor/projectors, frontend assembly display.
- **Coverage table:** Current. Routing and coverage distinguish synthesis, whole forwarding, full assembly and mixed output, with required object schemas and native nullable/property semantics.
- **LLM-first usability:** Pass. Self-contained authoring examples and enforced rules route adjacent concerns; no promise of live business-reasoning improvement, format enforcement, inferred Java/REST contracts or historical trace interchange. Unicode fix preserves the already documented native output contract rather than introducing another authoring dialect.

## Residual Risks and Optional Developer Checks

- No paid evaluation or business-reasoning reliability claim is part of this task; providers in executable coverage are deterministic local fixtures.
- Initial full regression is recorded as failed, with its sole failure fixed and rechecked by the final boundary suite; do not describe that initial command as a pass.
- Two opt-in `Pr18RecordedHandoffDiagnosticTest` cases remain skipped without their external diagnostic property, unrelated to this change.
- No optional developer observation is required for these mechanics. A fresh independent review is required because this context changed implementation artifacts.

## Disposition

**Candidate clean; fresh review required.** Two P2 findings resolved; P0/P1/P3 findings: zero. Final internal review has no actionable findings. `REVIEW_RESULT: fixes-applied`.
