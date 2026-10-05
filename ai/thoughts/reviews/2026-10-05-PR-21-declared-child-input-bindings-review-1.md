# PR 21 Independent Review 1

## Code Review Findings

### [P2] Exercise declared bindings across a real grouped producer join

- **Location:** `src/test/java/ai/loomspan/integration/BindingIsolationIntegrationTest.java:38` (new regression location).
- **Evidence:** The candidate's binding integration plans have singleton producers. Existing grouped engine tests protect prompt evidence, scheduling and mission state, but their tool lambdas do not assert the newly supplied `sourceResults` argument. The new production bridge is `StepLoopMissionExecutionEngine#executeToolAction`, which passes the pre-unit result snapshot to `BoundCapability#invokeAssigned` and ultimately to the assembler.
- **Trigger:** A bound consumer depends on multiple producers in one earlier parallel group, with either concurrency enabled or disabled.
- **Impact:** The ticket's grouped binding acceptance criterion lacked executable end-to-end evidence. A regression dropping the snapshot or admitting the consumer before the producer join could pass the existing tests despite breaking declared result transfer.
- **Recommendation:** Add a latch-controlled public invocation regression with two grouped bound producers and a consumer using both accepted results. Prove enabled overlap, disabled serialization, full join and exact delivered values.

This finding was resolved in this context. Final internal rereview: **No actionable findings.** No production defect was established.

## Findings Resolved in This Context

- P2 grouped binding verification gap: added `BindingIsolationIntegrationTest#groupedBoundProducersJoinBeforeDeliveringExactInputs`, parameterized over both concurrency modes. Both producer handlers receive the bound parent marker; enabled handlers overlap before either can return; disabled execution prevents the second handler from starting until the first is released. The consumer stays undispatched while a producer is blocked and then receives both exact accepted JSON subtrees, including an integer above 2^53. The test uses the supported facade, real catalog/planner/engine/invoker/router, local provider responses and REST handler; no paid provider calls.
- Initial regression fixture had an indentation error in generated YAML. The first focused run failed its two new cases at startup. Replaced that fixture interpolation with explicit indentation; all seven isolation cases pass.
- Only this test source and this review artifact were changed by the reviewer. No production, governing plan, ticket, documentation or sibling repository was edited.

## Open Questions and Assumptions

- None requiring developer intent. Full profile remains appropriate for authored input contracts, dataflow, execution and diagnostics.
- The developer's recorded direction permits destructive internal edits without compatibility shims within ticket scope. This does not authorize changes to supported API/SPI, isolation or ordinary unbound behavior.

## Exact Review Scope

- Branch `main`, HEAD `cb37a28e0acf892744c2bbafd6c7cb657be757b1`; no staged diff or additional committed comparison scope. Reviewed the complete working-tree candidate against HEAD, including untracked implementation/test/documentation files. Original checkout attribution identifies only the ticket as pre-existing untracked work; the remaining candidate is pipeline work.
- Production inventory: manifest DTO/parser/normalized constraint and complete-generation validation; eight new internal input/planning collaborators; separate model argument contracts on bound capabilities; planner dependency guidance/validation; action and prompt integration; immutable mission input and decoded accepted results; common router exact validation; assigned-worker snapshot delivery; existing tool frames with provenance. Deleted unused internal `CapabilityInvoker` is coherently removed from callers and architecture classification.
- Test inventory: declaration/generation, projection, exact validation/assembly/decoder, dependency/planner, action/prompt/engine, routing, isolation and nine model/Java/REST combinations, trace roundtrip, mandatory architecture checks and adjacent protected suites.
- Documentation inventory: README, new input-binding topic, routing/coverage and seven connected authoring guides. No configuration keys, dependencies, migration, deployment or closed Console transport envelope changes.
- Prior review artifacts were neither located for content nor read. Independent defect, failure-boundary, security, mutability, lifecycle, concurrency, resource and documentation review was completed before applying this test-only fix; final rereview revisited these same paths and the added regression.

## Verification Results

- PASS — `.\mvnw.cmd test *> target/pr21-review1-full.log`: unchanged candidate, 1375 tests, 0 failures/errors, 2 existing optional PR18 diagnostic skips, BUILD SUCCESS. Includes `LoomspanPublicSurfaceArchitectureTest` and current canonical writer/reader/journal/corpus checks.
- FAIL — `.\mvnw.cmd '-Dtest=BindingIsolationIntegrationTest' test *> target/pr21-review1-grouped.log`: 7 tests, 2 failures in the newly added parameterized cases, caused by reviewer-authored fixture YAML indentation; no production failure. Corrected before rerun.
- PASS — `.\mvnw.cmd '-Dtest=BindingIsolationIntegrationTest' test *> target/pr21-review1-grouped-final.log`: final test-only state, 7 tests, 0 failures/errors/skips, BUILD SUCCESS. All production and test sources compiled after the change.
- PASS — `git diff --check` and final `git -c core.safecrlf=false diff --check`: no whitespace errors; informational CRLF notices on the former command.
- NOT RUN — optional `pr18.diagnostic` recorded replay mode: deliberately disabled and unrelated to declared bindings; existing two skips are not required acceptance coverage.

The full suite predates this review's test-only edit. The focused rerun verifies the entire changed test class afterward; production did not change, so repeating unrelated full-suite tests would add no necessary evidence.

## Requirements and Plan Conformance

| Ticket criterion | Independently checked evidence |
| --- | --- |
| Both source kinds, renamed paths and complete authoring example | Strict catalog parsing, complete-generation path/graph validation, pointer/binding tests; complete three-file guide example loaded by public integration |
| Large evidence with empty/reasoning-only arguments | Nine producer/consumer kinds in `DeclaredChildInputBindingsIntegrationTest`; exact 250-record equality, large integers/decimals, absent bound content from raw action arguments |
| Separate contracts and corrective actions | Projection preserves unbound schema nodes/requiredness; structural forbidden properties plus independent override guard; actual bound-override engine correction and compact/verbose prompt tests |
| Exact values and isolation | One lossless complete-document decode with original String retained; assembler resolves every declared source, copies nested values, and exact receiving policy blocks coercion/date rewriting; decoder/assembler/input/mission/router tests |
| Invalid declarations/results/conflicts | Raw duplicates, overlapping pointers, closed/nonobject paths, unknown/self/cyclic producers, impossible counts; unique plan producers/direct edges/earlier units; missing/ambiguous-source and override failures before dispatch |
| Dependencies, join and invocation scope | Real new two-mode grouped binding regression; existing grouped scheduling/failure/cutoff suites; concurrent/nested/reload public binding integration; mission owner identity and immutable pre-unit snapshots |
| Model/Java/REST, access and budgets | Nine-kind integration and undeclared Java/REST outputs; unchanged router access and generation ownership checks, bound access-denial and tool-budget zero-dispatch checks; authentication/authorization/usage suites |
| Raw/effective/provenance evidence | Actual invoker canonical writer/reader roundtrip: raw proposed action versus normalized effective frame input, parent/source task/path identity and no duplicate payload in provenance; retained original result and decoder serialization exclusion |
| Unbound/reference/forwarding/API behavior | Full offline suite; designated-child result integration, ordinary validation/reference tests; supported application allowlist unchanged and mandatory architecture suite passes |
| Offline mechanics and precise documentation | Local model HTTP/REST/Java fixtures, exact guide claims checked against runtime and focused tests, no live reliability claim or external provider/replay mutation |

- Implemented: all acceptance criteria and plan phases; final grouped test supplies the missing composition proof rather than trusting completed checkboxes.
- Partial/Missing: none in final state.
- Safe deviations: unused internal interface removed atomically; no shim. Existing extensible frame payloads carry provenance without new record types or transport fields. Full candidate verification plus final focused test-only verification is sufficient.
- Compatibility review: new opt-in manifest semantics are documented; full receiving metadata and descriptor contracts remain intact. All new public-modifier collaborators stay below `ai.loomspan.internal` and are classified as implementation by architecture reasons, with no application allowlist/SPI expansion. No `@ConditionalOnMissingBean` override surface introduced. Ordinary unbound contracts, `ref://`, identity, quotas and `output_from` remain protected. Console compatibility marker and Java-to-Go closed boundaries are unchanged because additions occur within existing arbitrary frame payload maps. No history reader, migration or dual behavior added.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** New author-selected pointer bindings, separate effective model schemas, strict receiving values, direct producer edges/uniqueness, invocation scope and provenance change skill authoring.
- **Documents reviewed:** repository-local `agent-skills/loomspan-docs/SKILL.md`; authoring index, mental model and source-verification protocol; `input-bindings.md`, `input-contracts.md`, `input-contract-design.md`, `planning-task-constraints.md`, `planning-concurrency.md`, `output-contracts.md`, `traces-and-debugging.md`, repository README.
- **Evidence checked:** parser/generation/source-path and cycle checks; projection/action/prompt/dependency code and focused tests; exact decoder/assembler/validator; public dispatch/isolation and guide-generation tests; actual canonical trace roundtrip. Matching checkout skill and POM identify `1.0.0-beta.8-SNAPSHOT`.
- **Coverage table:** Current; new binding route and affected input/planning/evidence entries updated.
- **LLM-first usability:** Pass. Topic starts with applicability and exact syntax, includes complete schemas, separates enforced rules from recommendations, states unsupported expressions/array traversal/aggregation, and routes reference/forwarding/debugging topics. No unsupported live quality claim.
- **Drift classification:** Aligned. Existing journal redaction exception remains confined to the established derived projection; no sensitivity classifier or masking introduced in canonical prompts, results, inputs or evidence.

## Residual Risks and Optional Developer Checks

- No required verification remains unavailable. The optional recorded PR18 diagnostic mode is outside scope. These deterministic fakes establish transfer/execution mechanics; live provider/model reliability and business reasoning were not evaluated or claimed.
- Optional developer checks: none.

## Disposition

**Candidate clean; fresh review required.** One P2 verification finding fixed; P0/P1/P3 findings: zero. Final internal rereview finds zero remaining actionable issues. This context changed a test, so `REVIEW_RESULT: fixes-applied`; the next fresh reviewer supplies the pipeline completion gate.
