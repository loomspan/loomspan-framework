# PR22 Declared Output Bindings — Independent Review 2

## Code Review Findings

No actionable findings remain in the final internal review.

The unchanged candidate had one confirmed P2 defect, described below and fixed in this context. This review independently inspected the full current ticket diff and did not read earlier review documents.

### [P2] Validate output policies against retained assembled text

- **Location:** `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:556`, with the same defect in mixed final validation near line 948.
- **Evidence:** Both planning completion paths wrapped the assembled string in a `StepAction` after reading it with the default schema JsonNode mapper. `validateFinalResponseForSkill` serialized that node again for schema and regex checks. Numeric tree decoding rounded precise decimals even though `OutputBindingComposition` retained their exact BigDecimal values.
- **Trigger:** Bind the accepted direct producer decimal `0.12345678901234567890123456789` into `/report` and configure a regex requiring that exact number. Both entirely bound and mixed planning outputs failed linting.
- **Impact:** A valid exact assembly failed completion; mixed execution could exhaust final correction on a value Framework owns and the model cannot repair.
- **Recommendation implemented:** Pass the authoritative assembled string directly to output-schema and regex validation. Keep the parsed node only for evidence checks. Update the internal validation signature and both callers atomically, without an overload or shim.

## Findings Resolved in This Context

- **P2 — Exact decimal policy validation:** Fixed in `StepLoopMissionExecutionEngine`. New parameterized `assembledOutputPoliciesValidateExactBoundDecimals` regression exercises full and mixed planning completion, asserts the exact returned decimal and successful linter outcome. Both variants failed against unchanged production code and passed after the fix.
- Complete post-fix review revisited declaration, projection, immutable sources, both engines, correction, accepted-work checks, diagnostics, Console consumers, supported-surface classification and authoring guidance. No further actionable defect was established.

## Open Questions and Assumptions

- No developer question remains. The approved profile is Full 5-Step Pipeline.
- The developer explicitly authorizes development breaks and forbids compatibility shims. Internal changes were made in place; ticket guarantees for ordinary synthesis, existing forwarding, authorization and isolation remain protected.
- No paid model calls, sidecar-suite edits, commits, pushes or releases were performed.

## Exact Scope and Review Method

- Base: working tree against HEAD `1b5c1a63bf0beb538119be05a8b5b361c96f8e72` on `main`. No staged changes or additional committed branch delta.
- Reviewed tracked production changes, tracked tests/documentation, and untracked implementation files explicitly; Git diff alone does not include the new projection/composition/producer validator, integration/unit tests, Go assembly authority decoder/tests or assembly fixtures.
- Inventory: manifest/catalog/complete-generation validation; output projection/composition/advisor; mission/ordinary/planning/step completion; trace/state/journal/live records; Go analysis/browser/MCP/live and TypeScript presentation/contracts; architecture and deterministic integration/unit/corpus tests; skill-authoring and repository guides.
- Preserved unrelated untracked PR23 ticket and pipeline artifacts. Prior review files were excluded and never read.
- Finished the independent defect, conformance and documentation-impact review on the untouched candidate before adding the reproducer or applying a production fix.
- Read repository and Console AGENTS instructions, the design lens, supplied ticket/research/implementation/testing plans and shared review/documentation protocols. Applied the same-checkout `loomspan-docs` skill only as a router after executable behavior inventory; its version matches `1.0.0-beta.8-SNAPSHOT`.

## Verification Results

- PASS — `.\mvnw.cmd -q test`: untouched candidate, 181 suites and 1,435 reported cases, zero failures/errors; two pre-existing opt-in skips.
- FAIL (expected behavioral red) — `.\mvnw.cmd -q '-Dtest=StepLoopMissionExecutionEngineTest#assembledOutputPoliciesValidateExactBoundDecimals' test *> target-pr22-review2-decimal-red.log`: both variants failed output linting against the unchanged production path. The first invocation had a test-authoring Optional accessor compile error; it was corrected before establishing this genuine behavioral red.
- PASS — `.\mvnw.cmd -q '-Dtest=StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsPlanningIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test *> target-pr22-review2-focused.log`: post-fix, 101 cases; zero failures/errors/skips.
- PASS — `.\mvnw.cmd -q test *> target-pr22-review2-final.log`: post-fix, 181 suites and 1,437 reported cases (1,435 executed); zero failures/errors, two pre-existing opt-in skips.
- PASS — `go run ./internal/buildtool verify` from `loomspan-console/`: declared toolchain and locked dependencies, TypeScript checking, 46 frontend suites / 524 tests, production build, all Go packages.
- PASS — `go test -count=1 ./internal/traceanalysis ./internal/live ./internal/browserapi ./internal/mcpadapter` from `loomspan-console/`: all four affected packages passed without cache.
- PASS — `git diff --check`: no whitespace errors.

The Java corpus was verified with regeneration disabled throughout this review. No canonical fixture was refreshed to make a test pass. Original and final full regressions include `LoomspanPublicSurfaceArchitectureTest`; the post-fix focused command also explicitly executed its eight checks.

## Requirements and Plan Conformance

| Ticket requirement | Final executable evidence |
| --- | --- |
| PR21 destination map and input/direct-child whole/subtree semantics | Shared catalog descriptor checks; `YamlSkillDefinition.outputBindings`; generation path/type checking; composition and public-facade model/Java/REST tests |
| Two producers plus current input, exact arrays/numbers, no final synthesis and all-work completion | Six planning integration variants; full/mixed slot handling; latch-controlled two-producer join and unrelated-work step tests |
| Ordinary mixed evidence/reasoning, projected initial/corrective contract and required nested siblings | Ordinary integration requests/assertions and composition projection tests; open/optional unbound space keeps a model call |
| Mutual exclusion; preserved forwarding and ordinary synthesis | Catalog rejection of both fields including null; unchanged forwarding grammar; full `DesignatedChildResultIntegrationTest` and ordinary regression |
| Declaration/static/schema and dynamic source failures | Catalog/generation/producer validator suites; missing/ambiguous/null/type composition cases; retained-task identity/stale-plan step cases; bound failures have no fallback or assembly success |
| Equal/null/case/blocking model override; bounded correction and no child replay | Composition and ordinary override correction; planning wrong-summary correction with invocation counters; projected validation before detached insertion; new decimal policy regressions |
| Ownership, concurrency, reload and authorization | Mission-local immutable input/task results/memoized composition; nested same-name/task-ID isolation; concurrent ordinary roots; held reload correction; enabled/disabled producer joins; invisible producer and existing lifecycle/RBAC regression |
| Source provenance/raw evidence/assembled output/skipped synthesis | Owner-frame `RESULT_ASSEMBLED`; source task/skill/frame provenance without selected values; Java canonical and SSE corpus; strict Go task/plan ownership and browser/MCP/live/UI checks |
| Complete author-facing modes/examples and deterministic evidence | Output contracts full/mixed examples and routing; input/planning/concurrency/trace references; README and complete local verification |

- **Implemented:** All runtime acceptance requirements have corresponding source and executable coverage.
- **Partial/Missing:** None in current implementation. Historical pre-fix declaration red evidence was not established during implementation and remains explicitly disclosed in the testing plan; this review established a separate genuine red/green regression for its policy fix.
- **Safe deviations:** Provisional separate test class names were consolidated into composition/isolation and ordinary/planning integration suites. The existing PR21 descriptor name was retained and reused rather than introducing a second grammar. Neither affects behavior.
- **Compatibility review:** No supported application API/SPI addition or signature change; architecture additions inventory three internal collaboration types only. New manifest syntax is additive and mutual exclusion explicit. Existing unbound and exact-forwarding behavior remains executable. Current Java/Go/TypeScript diagnostic vocabulary and fixtures move together; existing exact release-string rejection is unchanged. No new bean override, Java-return-schema inference, historical reader, migration, fallback or compatibility shim was introduced.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** New manifest syntax, source ownership, full/mixed completion, projection/correction, all-work joins, N versus N+1 step costs and authoritative assembly diagnostics.
- **Documents reviewed:** `agent-skills/loomspan-docs/SKILL.md`; skill-authoring `README.md`, `source-verification.md`, `mental-model.md`, `input-bindings.md`, `output-contracts.md`, `planning-task-constraints.md`, `planning-concurrency.md`, `rest-skills.md`, `traces-and-debugging.md`; repository and fixture READMEs.
- **Evidence checked:** Matching manifest/generation/runtime paths; focused binding and facade tests; output-schema native validation; exact-value decoder/copies; Java/Go canonical and live corpora; Console record facts, payload descriptors and UI.
- **Coverage table:** Current. Output, planning and diagnostic coverage includes assembly.
- **LLM-first usability:** Pass. Routing, decision table and complete schemas explain enforced ownership/failure behavior without unsupported inference or paid reliability claims.
- **Drift classification:** Aligned in the final state. The policy fix restores the documented exact-bound-value contract; no new authoring choice or syntax is necessary.

## Residual Risks and Optional Developer Checks

- Deterministic local tests establish mechanics, source ownership and validation. Business reasoning quality on paid providers was not evaluated and is outside this ticket.
- The two full-suite skipped cases are pre-existing opt-in PR18 diagnostics, unrelated to this feature.
- Canonical Console verification reports existing dependency audit and bundle-size notices; this ticket changes no dependency versions.
- No optional manual check is required.
- Existing authentication/access/lifetime/size controls remain in force. Canonical inputs, raw responses, child text and invoke results retain fidelity. The only content-rewriting path remains the existing derived-journal exception; this change does not add or broaden classification/redaction.

## Disposition

**Candidate clean; fresh review required.** One P2 finding was resolved, zero findings remain after internal re-review, and verification is sufficient. This context edited one production file and its focused test, so `REVIEW_RESULT: fixes-applied` applies. A fresh independent context must verify the final candidate.

## Step Report: 5_code_review
STATUS: complete
ARTIFACTS:
  - ai/thoughts/reviews/2026-10-05-loomspan-pr-22-declared-output-bindings-review-2.md
  - src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java
  - src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java
SUMMARY: One P2 exact-decimal policy-validation defect was reproduced and fixed; zero findings remain after complete internal re-review. Final Java regression and current Console verification pass. Candidate clean; fresh review required.
DECISIONS:
  - Validate schema/regex policies against exact retained assembled text; keep parsed nodes only for evidence, with atomic internal signature changes and no compatibility shim.
DEVELOPER QUESTION: none
EVIDENCE: none
RECOMMENDATION: none
VERIFICATION:
- PASS — `.\mvnw.cmd -q test`: untouched candidate, 181 suites and 1,435 reported cases, zero failures/errors; two pre-existing opt-in skips.
- FAIL (expected behavioral red) — `.\mvnw.cmd -q '-Dtest=StepLoopMissionExecutionEngineTest#assembledOutputPoliciesValidateExactBoundDecimals' test *> target-pr22-review2-decimal-red.log`: both variants failed output linting against the unchanged production path. The first invocation had a test-authoring Optional accessor compile error; it was corrected before establishing this genuine behavioral red.
- PASS — `.\mvnw.cmd -q '-Dtest=StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsPlanningIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test *> target-pr22-review2-focused.log`: post-fix, 101 cases; zero failures/errors/skips.
- PASS — `.\mvnw.cmd -q test *> target-pr22-review2-final.log`: post-fix, 181 suites and 1,437 reported cases (1,435 executed); zero failures/errors, two pre-existing opt-in skips.
- PASS — `go run ./internal/buildtool verify` from `loomspan-console/`: declared toolchain and locked dependencies, TypeScript checking, 46 frontend suites / 524 tests, production build, all Go packages.
- PASS — `go test -count=1 ./internal/traceanalysis ./internal/live ./internal/browserapi ./internal/mcpadapter` from `loomspan-console/`: all four affected packages passed without cache.
- PASS — `git diff --check`: no whitespace errors.
OPTIONAL_DEVELOPER_CHECKS:
  - none
REVIEW_RESULT: fixes-applied
NEXT: Launch a fresh complete independent Step 5 review of the final candidate.

