# PR23 shared output-validation policy — independent review 1

## Code Review Findings

**No actionable findings.** Finding counts: P0 0, P1 0, P2 0, P3 0.

## Findings Resolved in This Context

None. No implementation artifact was changed during this review.

## Open Questions and Assumptions

- None affecting correctness or completion. The local expanded ticket governs this review; no assumption about remote PR23 contents or status was needed.

## Exact Review Scope and Independent Analysis

- Pipeline Step 5, selected profile `full`, fresh review number 1. Baseline and current HEAD: `35cded1dfe30ff467869139f5f69ee872cbe41a9` on `main`, containing prerequisite PR22. No committed PR23 changes or staged changes exist; implementation is unstaged/untracked.
- Changed production: `LinterCallAdvisor`, `OutputSchemaCallAdvisor`, `OutputSchemaValidator`, `DefaultMissionExecutionEngine`, `EvidenceContractCallAdvisor`, and `StepLoopMissionExecutionEngine`. New production: `internal/outputvalidation/OutputValidationPolicy` and `OutputValidationFeedback`.
- Changed tests: architecture allowlist, ordinary declared-binding integration, evidence advisor, and step engine. New tests: combined advisor ordering plus policy/feedback utilities. Research and both plans are untracked pipeline artifacts. The expanded ticket diff is preexisting user work and was preserved. No configuration, dependency, migration, supported API, Spring bean, or Console source change exists.
- Reconstructed scope with `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git diff --stat`, `git diff`, `git diff --cached --stat`, `git diff --cached`, and `git ls-files --others --exclude-standard`. Read all new implementation/test files. No prior review artifact was read.
- Independent defect review preceded acceptance/plan comparison. Traced full changed methods and their caller/callee context, including resolver ordering, step-mode advisor exclusion, invocation-local composition, full/mixed completion, final-action extraction, validator short-circuiting, state recording, forwarding, and publication boundaries.
- Shared arithmetic preserves failure retry while `attempt <= maxRetries`, success at the last allowed attempt, and `retryCount = attempt - 1`. Integrations retain their next-attempt counters: schema/linter advance even on terminal planning failure; terminal evidence does not. Ordinary nested retries reset downstream advisors; planning final retries keep independent counters.
- Ordinary unbound linter still returns terminal response; mixed-bound ordinary exhaustion fails before assembly publication. Immutable ordinary lint failure remains EXHAUSTED at attempt 1 despite a positive budget; immutable planning lint failure retains its normal RETRYING status with that budget but immediately fails completion. No final provider request/correction/fallback was added to full assembly.
- Compared relocated feedback against baseline removed implementations: ordinary correction headings, tails, JSON quoting, parser facts, whole-bullet selection and 2,048-code-point bound are preserved. Ordinary log/evidence formats and planning three-issue canonical-field summaries remain distinct. Candidate replay, exceptions, complete planning issue lists, ordinary four-issue recording, response metadata, and recorder error handling remain with the original owners.
- Reviewed security/content fidelity, concurrency/lifecycle, performance, failure ordering, and diagnostic coherence. The new helpers are stateless and introduce no new access, input, mutable-session, resource, or transport boundary. Composition/projection authorities, evidence truth sets, model work, source snapshots, forwarding and journal projection are unchanged. No sensitivity classification or content masking was added.

## Verification Results

- PASS — `.\mvnw.cmd '-Dtest=OutputValidationPolicyTest,OutputValidationFeedbackTest,OutputValidationAdvisorOrderingTest,OutputSchemaCallAdvisorTest,LinterCallAdvisorTest,EvidenceContractAdvisorAdditionalTest,SkillAdvisorResolverEvidenceTraceTest,LoomspanPublicSurfaceArchitectureTest' test`: 54 tests, zero failures/errors/skips. Includes all 8 required public-surface architecture tests.
- PASS — `.\mvnw.cmd '-Dtest=StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,OutputBindingCompositionTest,OutputBindingIsolationTest,EvidencePlanningIntegrationTest,PlannerEvidenceFlowIntegrationTest,DesignatedChildResultIntegrationTest,ConsoleTraceFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' test`: 175 tests, zero failures/errors/skips.
- PASS — `.\mvnw.cmd test *> "$env:TEMP\loomspan-pr23-review1-full-test.log"`: BUILD SUCCESS; 1,464 tests reported, zero failures/errors, two skipped. Reviewed final log output and process exit code 0. Production/test compilation completed. Skips are the existing `Pr18RecordedHandoffDiagnosticTest` tests guarded by `@EnabledIfSystemProperty(named = "pr18.diagnostic", matches = "true")`; no required PR23 suite was skipped.
- PASS — `git diff --check`: no whitespace errors; Git's CRLF-to-LF notices are advisory.
- PASS — source search of the five migrated integrations for `attempt > maxRetries`, `attempt <= maxRetries`, `attempt - 1`, direct schema/linter outcome construction and `.matcher(` found no surviving duplicate mechanics (rg exit 1 means no matches).
- Verification is fresh in this context. Historical baseline/implementation test claims in the plans were not used as substitutes for these runs.

## Requirements and Plan Conformance

| Ticket acceptance criterion | Evidence and assessment |
| --- | --- |
| Cohesive shared policy, path-owned execution | Both utility classes are used by ordinary advisors and planning; full-bound ordinary and contribution-failure paths use shared outcomes/decisions. Existing validators/composition remain the sole authorities. No universal retry loop or duplicate displaced renderer remains. Implemented. |
| Success/correction/exhaustion, ordering/budgets/attempts/exceptions/outcomes | Real resolver/chain ordering test asserts nine ordered mutation facts and downstream resets. Planning combined test asserts schema 1/2/2/2, evidence 1/2/2 and lint 1/2; parameterized terminal tests and existing advisor tests cover terminal behavior and exceptions. Implemented. |
| Explicit ordinary/planning lint difference and feedback fidelity | Existing unbound linter exhaustion test returns final text; full/mixed ordinary binding tests fail; planning terminal tests reject. Pure renderer exact strings plus existing candidate/Unicode/latest-only/exception tests protect fidelity. Implemented. |
| Contribution shared accounting with existing diagnostics | `validateContributionFailure` uses the policy and planning formatter with full issues and unchanged prefix/counters. Null/equal overrides test consumes only schema budget until composition succeeds; ordinary alias/blocking-ancestor tests exercise bounded correction. Implemented. |
| Full no-model immediate failure; mixed snapshot/ownership/complete checks | Full-bound ordinary/facade planning tests count zero final requests. Immutable source/evidence/lint failures publish no assembly. Generation-held ordinary correction, isolation tests and composition tests protect captured sources and ownership; mixed planning and ordinary lint tests require bound plus contributed data. Implemented. |
| Bound lint rejection, projected guidance, step costs/provenance/events | Ordinary mixed exhausted-lint fence is unchanged. Full-bound positive-budget status/detail assertions distinguish ordinary/planning paths. Facade planning model/Java/REST tests, composition/isolation tests and Console fixture corpus cover complete output and diagnostics. Implemented. |
| Extracted final payload only, no task replay, exact forwarding | Step-mode assembler still removes final-output advisors; final validators remain behind validated FINAL_RESPONSE and use extracted/assembled content. Combined/candidate replay tests count one task and preserve completed evidence. Forwarding branch remains an unchanged direct retained-result return; designated-child suite passed. Implemented. |
| Closed API/SPI/configuration/lifecycle/concurrency/transport | Supported API/integration allowlists unchanged; only the two planned internal-public types are classified. Architecture tests and full suite pass. No supported signature, bean extension, configuration, lifecycle, or wire-format change exists. Implemented. |

- Partial: none. Missing: none. Out-of-scope implementation: none identified.
- Safe deviations: new utility tests supplement existing suites rather than duplicating all proposed scenarios. Two validator issue-code constants became public only within an internal type so the moved renderer reuses the existing code authority; this creates no supported API.
- Compatibility review: protected application returns, manifest/configuration semantics, sole `RestSkillHandler` SPI and current diagnostic behavior remain unchanged. Technical internal-public exposure is explicitly allowlisted. No intentional supported break or shim is needed; displaced private helpers were removed atomically. No Java-to-Go protocol/compatibility-marker change is required because fields, event timing and authoritative assembly facts are unchanged.
- Profile reassessment: `full` remains appropriate for the cross-path production refactor; no profile or developer-intent escalation is required.

## Skill-Authoring Documentation Impact

- **Assessment:** No author-facing semantic impact.
- **Rationale:** Manifest syntax/defaults, model guidance, retry budgets, result values, full/mixed assembly, evidence supportability, final-step boundaries and forwarding are preserved. Stable integration anchors still resolve; extraction does not require authors to change skills.
- **Documents reviewed:** matching-checkout `agent-skills/loomspan-docs/SKILL.md`, skill-authoring `README.md`, `output-contracts.md`, `evidence-contracts.md`, and `source-verification.md`; relevant schema/step correction and anchor passages in `traces-and-debugging.md`.
- **Evidence checked:** changed production/test paths, resolver/assembler, composition/projection ownership, existing source/reload/isolation/forwarding tests, exact renderer tests and independently executed focused/regression/full suites. Skill and Maven versions align at `1.0.0-beta.8-SNAPSHOT`.
- **Drift classification:** aligned. No moved private helper is a stale documented named anchor; `OutputSchemaCallAdvisor` and `StepLoopMissionExecutionEngine#validateOutputSchema` remain integration owners.
- **Coverage table:** Current; no topic scope/confidence change requiring an edit.
- **LLM-first usability:** Pass for affected guidance; routing, self-contained rules and evidence anchors remain accurate.

## Residual Risks and Optional Developer Checks

- No required verification unavailable. Two optional, unrelated property-gated PR18 diagnostics did not execute in the standard suite.
- No optional developer check required. Deterministic provider/chain tests verify framework behavior; no live-provider behavior change is introduced by this extraction.

## Disposition

**Approve.** No actionable findings; fresh verification is sufficient. The review document is this context's only repository change. `REVIEW_RESULT: clean`.
