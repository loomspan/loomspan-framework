# PR 23 Shared Output-Validation Policy Testing Plan

## Change Summary

Pure internal extraction of retry/status arithmetic, outcome construction, issue rendering and regex checks used by ordinary advisors, planning final validation, binding contribution rejection and immutable full-bound completion. No observable behavior changes. The implementation plan is `ai/thoughts/plans/2026-10-05-loomspan-pr-23-shared-output-validation-policy.md`; the approved pipeline profile is `full`, baseline HEAD is `35cded1dfe30ff467869139f5f69ee872cbe41a9`, and the developer's preexisting expanded ticket diff must remain preserved.

This is Step 3 in the same context as Step 2. Source and test inventory preceded matching-checkout authoring guidance. Planning writes artifacts only; no test/build execution is claimed here.

## Impacted Areas

- `src/main/java/ai/loomspan/internal/outputvalidation/OutputValidationPolicy.java` and `OutputValidationFeedback.java` (new internal utilities).
- Ordinary `OutputSchemaCallAdvisor`, `EvidenceContractCallAdvisor`, `LinterCallAdvisor`; resolver/chain ordering exercised through real wiring.
- `DefaultMissionExecutionEngine` full-bound completion and mixed-bound exhausted-linter fence.
- `StepLoopMissionExecutionEngine` final validators, contribution-failure accounting, full-bound completion and final-step retries.
- Architecture internal-public allowlist; unchanged current trace writer/reader/projector and assembly fixture corpus.
- Existing binding composition/projection/source snapshot and forwarding suites are regression boundaries, not algorithms being replaced.

## Risk Assessment

- Ordinary nested advisors reset downstream counters on outer retry; planning keeps independent counters in one final step. A shared loop would erase this distinction.
- Retryable failure is attempt <= maxRetries; maxRetries N permits N corrections and N+1 validated responses. Success at N+1 is PASSED, not automatically exhausted. Outcome retryCount is attempt-1.
- Ordinary unbound linter exhaustion returns text; ordinary mixed bound exhaustion rejects completion; ordinary full-bound failure is forced EXHAUSTED at attempt 1 with no correction; planning rejects terminal final validation, and full-bound planning rejects immediately even if recorded status is RETRYING.
- Ordinary schema records four issues; planning records the complete list. Ordinary correction, log rendering and planning rejection summaries have different formats and limits.
- Schema correction retains baseline plus latest candidate only; evidence/linter accumulate hints; step correction retains complete latest action and completed results. Formatting equivalence includes escaping, whitespace, omission counts, failure text and resource bounds.
- Full assembly must preserve repeated complete-schema validation, actual evidence invocation and sparse/rich metadata differences. Invalid sources may fail during composition capture before later policy recording; tests must assert the actual failure boundary rather than invent an unreachable later-validation scenario.
- Mixed composition needs one invocation-local source snapshot, projected guidance and ownership rejection; evidence/regex see assembled output, not only contribution or envelope.
- No accepted task replay, new provider fallback, final policy on assigned actions, parent forwarding validation, changed step costs, extra RESULT_ASSEMBLED, or lost failure event may be introduced.

### Protected compatibility and removed internal paths

Protect SkillTemplate returns, supported API signatures, sole RestSkillHandler SPI, documented manifest/default/retry semantics and current-run diagnostic coherence. No intentionally removed public/configuration/serialized path exists. Displaced internal helper implementations should disappear atomically without legacy tests demanding old signatures; tests of execution behavior remain. New helper Java-public exposure is internal and allowlisted with reasons, never promoted to supported API or a bean override.

No Java-to-Go field/protocol/compatibility-marker change is planned. ConsoleTraceFixtureCorpusTest checks the existing assembly authority, without historical readers/fixtures. Existing derived journal exception stays unchanged. Fidelity assertions apply to canonical traces, prompts, candidates and invocation output without introducing sensitivity classification.

## Existing Test Coverage

| Existing test location/anchor | Evidence to retain | Additional gap |
| --- | --- | --- |
| `src/test/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisorTest.java` | schema success/correction/exhaustion, precise/parser feedback, latest-only replay, Unicode bounds, exception candidate, four-issue outcome, logs and recorder failure | exact extracted-renderer equivalence and combined advisor nesting |
| `src/test/java/ai/loomspan/internal/linter/LinterCallAdvisorTest.java` | success, hint accumulation, exhausted response return, recorded attempts, managed recorder failure | explicit paired ordinary/planning terminal behavior |
| `src/test/java/ai/loomspan/internal/runtime/evidence/EvidenceContractAdvisorAdditionalTest.java:31` | tool-free retries, expression diagnostics and terminal exception | successful correction and repeated-hint/budget characterization where missing |
| `src/test/java/ai/loomspan/internal/chat/SkillAdvisorResolverEvidenceTraceTest.java` | real resolver evidence events | combined schema/evidence/linter ordering/reset |
| `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:973`, `:1010`, `:1080`, `:1584`, `:1621` | schema correction/exhaustion, complete candidate/work retention, evidence independent budget, linter corrections | complete combined-counter event sequence, exhausted evidence/linter, exact planning issue list/summary |
| Same step test `:106`, `:327`, `:348`, `:385`, `:414` | bound overrides/guidance, retained result/plan checks, full joins, producer failure and decimal full/mixed output | contribution-failure full issue recording, full-bound positive-budget status, mixed lint/evidence corrections |
| `src/test/java/ai/loomspan/integration/DeclaredOutputBindingsOrdinaryIntegrationTest.java` | full no-provider, source/reload/concurrency isolation, full-bound lint failure, immutable type/evidence failure, aliases/ancestors, optional/open fields | mixed bound exhaustion, complete-output policy matching and outcome details |
| `src/test/java/ai/loomspan/integration/DeclaredOutputBindingsPlanningIntegrationTest.java` | full/mixed model/Java/REST production, accepted work counts, provenance | retain through extraction; use step tests for focused diagnostics |
| `src/test/java/ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java`, `OutputBindingIsolationTest.java` | exact numbers/values, source/projection ownership and capture | no new algorithm tests solely for policy extraction |
| `src/test/java/ai/loomspan/integration/DesignatedChildResultIntegrationTest.java` and step forwarding tests | exact producer result, all-work success, no parent final call, producer-owned validation | unchanged bypass regression |
| `src/test/java/ai/loomspan/internal/runtime/trace/ConsoleTraceFixtureCorpusTest.java`, `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` | current trace assembly facts and closed supported/internal surface | new internal helper exposure entries only |

## Bug Reproduction / Failing Test First

Not applicable: the ticket is a behavior-preserving refactor. Add missing characterization and run it against unchanged production first; it should pass. Preserve baseline assertion output as implementation verification evidence. Do not assert new helper existence as a contrived red test. If characterization reveals an actual defect, distinguish a bad test expectation from behavior outside the ticket; do not silently fix observable semantics under this scope.

## Tests to Add/Update

Names below identify required behavioral cases; combine parameterized cases with existing tests where this improves clarity. Avoid redundant test classes that reproduce already sufficient coverage. Use real validators and existing deterministic fixtures; mock only provider/chain boundaries and external transport. No external service credentials or network provider required.

### 1) `preservesNestedAdvisorOrderAndResetsDownstreamAttempts`

- **Type/location:** integration, new `src/test/java/ai/loomspan/internal/chat/OutputValidationAdvisorOrderingTest.java`, using real DefaultSkillAdvisorResolver advisors and Spring AI chain ordering.
- **Proves:** unbound evaluation order evidence -> schema -> linter; inner corrections complete before outer checks; outer retry recreates downstream advisor counters. With budgets 1, use four responses: unsupported optional evidence; supported evidence but wrong schema type; schema-valid supported text failing regex; all valid. Expected evidence attempts across callbacks/traces are 1 failed, 2 passed, 1 passed, 1 passed; schema attempts 1 failed, 2 passed, 1 passed; linter attempts 1 failed, 2 passed. Assert complete ordered events and actual request content, not only total model calls.
- **Fixtures/mocks:** minimal required scalar plus optional evidence property, compiled direct-child contract, successful-child mission binding, regex over a required scalar, deterministic candidate queue and recording model. Match exact fixture shape to real validator behavior before hardcoding event expectations. Use recorder callbacks/trace payloads and existing fixed clock.
- **Also:** parameterize binding mode or add a companion `bindingSchemaAssemblyRunsBeforeEvidenceAndLinter`, where a bound field is necessary for evidence/regex success. Assert evidence/linter candidate is complete assembly, schema order minus 70, and original model contribution retained in its trace.
- **Surface/expectation:** manifest behavior and ephemeral diagnostics; preserved behavior/current-run coherence. No changes to real ordering to satisfy the test.

### 2) `preservesIndependentPlanningValidatorCountersAndShortCircuitOrder`

- **Type/location:** integration within `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`.
- **Proves:** after one accepted tool task, four final candidates cause schema failure, then evidence failure, then lint failure, then success. Schema attempts are 1 failed, 2 passed, 2 passed, 2 passed; evidence 1 failed, 2 passed, 2 passed; linter 1 failed, 2 passed. Short-circuit means no later-validator event on a failed earlier check. All final retries use the same step number; tool/task runs once; action-invalid allowance is unchanged.
- **Fixtures/mocks:** existing SequenceChatClient, singleTaskPlan, definitionWithOutputSchemaAndEvidenceContract, explicit regex, fixed state/clock and tool-call counter. Capture requests and readRecords; evidence is optional unsupported claim that can be omitted after correction, without weakening required schema.
- **Assertions:** exact failure prefixes, current rejected action decoding, completed evidence presence and no older candidate; schema guidance only on final requests. Action envelopes/tool calls never get parent's final-output validation/guidance.
- **Surface/expectation:** manifest execution behavior and ephemeral diagnostics; preserved final-step ownership.

### 3) `preservesZeroAndPositiveBudgetTerminalBehavior`

- **Type/location:** parameterized unit/integration additions in existing schema/linter/evidence advisor tests and StepLoopMissionExecutionEngineTest.
- **Proves:** budgets 0 and 2 permit 1 and 3 candidate evaluations. Schema/evidence ordinary throw their existing exception with full candidate/issues, attempt/budget/failure facts; ordinary unbound lint returns terminal response with EXHAUSTED. Planning schema/evidence/lint terminal paths throw existing final-exhaustion exception and record rejection before step failure/closure, with no successful final step. Test pass exactly at final allowed attempt too.
- **Fixtures/mocks:** deterministic invalid candidates and terminal valid candidate, existing session helpers/chains; no extra tools; authored blank/nonblank linter messages exercise planning fallback and original ordinary detail. Retain managed recorder failure and out-of-managed-session existing tests.
- **Counters:** helper decision tests must not enforce one nextAttempt rule; integration retains schema/linter failure advance and exhausted evidence no-advance source branch. Do not expose private planning result records solely to inspect an unobservable terminal next counter.
- **Surface/expectation:** manifest behavior, current diagnostics and exceptions; original path distinctions.

### 4) `preservesFeedbackFormatsIssueLimitsAndCandidateFidelity`

- **Type/location:** unit, new `src/test/java/ai/loomspan/internal/outputvalidation/OutputValidationFeedbackTest.java`; strengthen existing OutputSchemaCallAdvisorTest and planning integration assertions where needed.
- **Proves:** ordinary whole/contribution correction strings exactly match baseline including heading, tail, newlines, JSON escaping, invalid-JSON locations/fragments, max four whole bullets, omitted count and <=2,048 code points. Ordinary log special unknown-property formatting stays intact. Planning uses max three messages with canonicalField before path and its exact empty fallback. Evidence retains ordinary four-message hint versus planning complete newline feedback.
- **Fixtures/mocks:** existing CorrectionEvidenceFixtures plus typed issue objects with quotes, slash/backslash, controls and supplementary Unicode; five or more issues and oversized issue text. Snapshot only deterministic stable rendered text; no timestamps/IDs.
- **Integration evidence:** ordinary recorded schema/trace issues max four while terminal exception has complete issues; planning recorded schema issues complete while rejection summary contains only three. Complete candidates/raw latest actions and canonical traces preserve authored values and names, with diagnostic bounds separate. Existing journal projection behavior stays unchanged.
- **Surface/expectation:** ephemeral diagnostics, prompt/return fidelity; current format equivalence and explicit resource controls.

### 5) `contributionFailureUsesSchemaBudgetAndRetainsOtherCounters`

- **Type/location:** integration in StepLoopMissionExecutionEngineTest and ordinary declared-binding suite.
- **Proves:** equal/null bound-destination overrides and blocking ancestor rejection consume only normal schema budget, record unchanged full planning issues or four ordinary issues, use the binding-specific planning prefix or contribution-specific ordinary correction tail, and advance schema attempt before success/exhaustion. Evidence/linter are skipped until contribution composition succeeds. Accepted tasks and source snapshot remain unchanged; exact complete output is validated afterward.
- **Fixtures/mocks:** existing outputBindingDefinition/projected-guidance and aliasesAndBlockingAncestors fixtures, long authoritative value, bound mission/input, rejected override then valid model-only contribution; terminal override sequence for exhaustion. Count task calls/final provider requests and read canonical outcomes/events.
- **Surface/expectation:** manifest output ownership, diagnostic coherence and invocation result; protected current semantics.

### 6) `fullBoundPoliciesPreserveImmediateFailureAndRecordingDifferences`

- **Type/location:** integration in DeclaredOutputBindingsOrdinaryIntegrationTest and StepLoopMissionExecutionEngineTest.
- **Proves:** valid fully bound outputs record schema/evidence/linter success at attempt 1, no final model call/correction, and exactly one RESULT_ASSEMBLED after policies. Ordinary evidence metadata is sparse skillName-only; planning includes claims/attempt/maxRetries. Exact schema/linter payload fields and ordinary assembled versus planning final-response detail strings remain unchanged.
- **Failure:** regex '^NEVER$' with budget 2 records ordinary EXHAUSTED at attempt 1/retryCount 0, planning RETRYING at attempt 1/retryCount 0; both reject immediately, no correction/fallback/event. Assert ordinary binding_linter_validation versus planning binding_output_contract text. Also cover budget 0 (both EXHAUSTED). Retain source-schema/immutable-evidence failures occurring before provider work, with their real boundary and no assembly publication.
- **Fixtures/mocks:** full input binding for ordinary; completed accepted child-result binding for planning; counting fake providers that fail test if a final call occurs; existing successful evidence contract where satisfiable. Do not inject impossible source states merely to make later evidence failure reachable.
- **Repeated checks:** retain planning composeEmpty followed by normal final validators. Assert resulting outcome records and current call path; no demand to remove a check after successful composition, and no production seam solely for counting validator calls.
- **Surface/expectation:** manifest full completion and ephemeral diagnostics; preserve intentional per-path status/details.

### 7) `mixedBoundEvidenceAndLinterValidateCompleteAssembly`

- **Type/location:** integration in both declared-output-binding suites or step engine for focused planning evidence.
- **Proves:** regex succeeds only when the bound decimal/string and model summary coexist; an optional unbound unsupported evidence claim is corrected using gathered child names; linter correction does not rerun accepted work or recapture sources. Exhausted mixed ordinary lint rejects via binding_linter_validation, never returns the assembled exhausted candidate; planning final exhaustion rejects with its existing path. No RESULT_ASSEMBLED on any failure, one on success.
- **Fixtures/mocks:** existing exact-bound-decimal test, minimal open input/closed output binding schema, immutable source sentinel across generation reload, model-only candidate queue, successful direct producer credit and optional unsupported claim. Capture projected initial/corrective guidance and complete output provenance.
- **Surface/expectation:** manifest behavior, return fidelity, diagnostics; protected complete-output validation, captured generation and source ownership.

### 8) `sharedPolicyBudgetBoundariesAndSurfaceRemainClosed`

- **Type/location:** unit `src/test/java/ai/loomspan/internal/outputvalidation/OutputValidationPolicyTest.java`; architecture update in existing LoomspanPublicSurfaceArchitectureTest.
- **Proves:** pass/retry/exhausted at attempts 1 and N+1, retryCount arithmetic, explicit immutable-bound failure, schema issue-list selection (four/all), existing failure-mode/detail preservation, regex matches entire string including authored flags/empty-string case. Nullable planning text is false; ordinary extraction's empty string behavior remains at caller boundary. Utility never owns next-attempt counters or recording.
- **Fixtures/mocks:** parameterized decision/outcome inputs, existing typed validation issues/results, compiled Pattern; no mocks required.
- **Architecture:** new utilities listed as internal-public cross-package types only. Existing closed API/integration lists and no-extra-SPI/leaked-internal-signature tests continue unchanged. Delete tests coupled to removed private algorithms or migrate them to the named shared renderer; do not preserve compatibility overloads.
- **Surface/expectation:** internal implementation and Application API boundary; atomic extraction with no supported surface delta.

## How to Run

From repository root in PowerShell, use the checked-in Maven wrapper; Java >=21 and Maven >=3.9.0 are required (POM enforcer). Existing local environment research found Java 21 and Maven 3.9.6. No special execution profile/env vars beyond the normal build and local fixture/mock provider configuration. Wrapper bootstrap may need the normal Maven repository access. Do not skip assertions or required suites due to wrapper/bootstrap trouble; report environment failures and resolve using the available standard Maven installation if necessary, recording exact actual commands.

Baseline characterization before source changes (new ordering test added in Phase 1):

```powershell
.\mvnw.cmd '-Dtest=OutputValidationAdvisorOrderingTest,OutputSchemaCallAdvisorTest,LinterCallAdvisorTest,EvidenceContractAdvisorAdditionalTest,StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest' test
```

After extraction, focused policy/advisor tests:

```powershell
.\mvnw.cmd '-Dtest=OutputValidationPolicyTest,OutputValidationFeedbackTest,OutputValidationAdvisorOrderingTest,OutputSchemaCallAdvisorTest,LinterCallAdvisorTest,EvidenceContractAdvisorAdditionalTest,SkillAdvisorResolverEvidenceTraceTest' test
```

Planning/binding/forwarding/evidence regression and required public-surface check:

```powershell
.\mvnw.cmd '-Dtest=StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,OutputBindingCompositionTest,OutputBindingIsolationTest,EvidencePlanningIntegrationTest,PlannerEvidenceFlowIntegrationTest,DesignatedChildResultIntegrationTest,ConsoleTraceFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' test
```

Final broad verification (compiles production/tests and runs all Surefire tests including integration-named tests):

```powershell
.\mvnw.cmd test
git diff --check
```

No separate formatter/linter plugin was established by the POM inventory; do not claim an unrun lint command. Complete test suite plus diff check is the final check standard. Adapt selected test names only when combining the proposed cases into existing suites, retaining equivalent executed coverage and recording exact commands. No need to repeat successful suites without subsequent changes/new uncertainty.

## Exit Criteria

- [x] Missing characterization passes before production edits; post-extraction tests establish equivalent existing behavior, not a fabricated pre-fix bug.
- [x] Success, correction, exhaustion and final-allowed-attempt success for schema/evidence/linter are covered with zero/positive budgets, exact attempt/retryCount/status and exception/outcome facts.
- [x] Ordinary nested order/downstream resets and planning order/independent counters/short-circuit behavior are proven.
- [x] Correction text, issue limits, parser/Unicode resource bounds, diagnostics, complete candidates and invocation data fidelity remain unchanged.
- [x] Contribution failures and full-bound schema/evidence/linter integration delegate shared mechanics while retaining budget/status/recording differences and repeated checks.
- [x] Full outputs have no final model/correction/fallback; mixed corrections retain snapshots, projected ownership, complete-output policy checks and terminal rejection.
- [x] Tasks are never replayed, envelopes/assigned actions stay outside final validation/guidance, step counts/provenance/events remain identical, and forwarding retains exact producer-owned output.
- [x] Focused suites, architecture check, Console current fixture corpus and complete Maven test suite pass; build compiles; git diff --check passes.
- [x] New public internal types are explicitly classified; supported API/SPI/configuration/lifecycle/concurrency/transport remain unchanged. Obsolete internal implementations are removed without dual routes/shims.
- [x] Stable authoring anchors resolve, semantics remain aligned, README topic coverage unchanged, preexisting expanded ticket diff preserved.
- [x] Actual executed commands/results are persisted by implementation/review. No planning-stage test execution is implied.

## Optional Developer Checks

None required. All ticket correctness obligations have executable evidence; pipeline completion must not wait for a subjective/manual comparison.

## Step 4 executed coverage

Characterization was added before production edits and passed against the existing implementations: 157 tests in the primary baseline command, then 101 planning-engine tests after the final contribution characterization. Exact commands and subsequent extraction verification are recorded in the implementation plan's Step 4 evidence section.

The proposed cases were combined with existing coverage instead of creating duplicate suites:

- `OutputValidationAdvisorOrderingTest#preservesNestedAdvisorOrderAndResetsDownstreamAttempts` uses the real resolver and Spring AI chain. It asserts the complete nine-event attempt/status sequence and correction/reset request contents.
- `StepLoopMissionExecutionEngineTest#preservesIndependentPlanningValidatorCountersAndShortCircuitOrder` asserts schema attempts 1/2/2/2, evidence 1/2/2, lint 1/2, three rejections in step 2, one task invocation and retained completed evidence.
- `#preservesPlanningTerminalBudgets` covers schema/evidence/lint zero and positive terminal budgets and positive last-allowed-attempt success, with existing final-exhaustion text and no final-step success on failure.
- `EvidenceContractAdvisorAdditionalTest#preservesTerminalBudgetAndAccumulatedHints` covers ordinary evidence zero/positive budgets, terminal correction success, hint accumulation and trace attempt/status. Existing schema and linter tests retain ordinary full-candidate exceptions, budget boundaries and exhausted-linter response return.
- `#fullBoundLinterFailureRecordsConfiguredBudgetButNeverCorrects` asserts planning attempt-1 budget/status/detail and no final request/event. `DeclaredOutputBindingsOrdinaryIntegrationTest#boundOnlyLinterFailureIsTerminalWithoutProviderFallbackOrAssemblyEvent` explicitly asserts ordinary EXHAUSTED at attempt 1/retryCount 0 with budget 2 and its distinct assembled-output detail.
- `#contributionFailureUsesSchemaBudgetAndCompleteAssemblyPolicies` covers null/equal bound overrides, schema-only accounting until composition succeeds, evidence then regex corrections on complete assembly, one producer invocation, binding-specific correction prefix, and assembly event count.
- `DeclaredOutputBindingsOrdinaryIntegrationTest#mixedLinterChecksCompleteAssemblyAndRejectsExhaustion` requires immutable source plus model field for regex success, and asserts two requests and one/zero publication events on success/exhaustion. Existing binding reload/isolation and facade planning tests retain snapshots, projected guidance and model/Java/REST provenance.
- `OutputValidationPolicyTest` and `OutputValidationFeedbackTest` verify pure budget/outcome mechanics, explicit immutable completion, four/all issue selection, full regex matching, exact whole/contribution/parser correction text, Unicode bound, ordinary log format and planning three-issue canonical-field summary.
- Existing forwarding, evidence truth-set, composition/projection, recorder-failure, Console corpus and architecture suites remain part of executed regression/full-suite checks. No production seams were added just to count validators or expose private planning counters.

Initial baseline failures were mistakes in new test fixtures/enum names, corrected without production changes. The passing baseline distinguishes characterization from bug reproduction. No material design or semantic mismatch was found.


Final verification: policy/advisor/architecture command passed 54 tests; planning/binding/forwarding/evidence/Console/architecture command passed 175 tests; `.\mvnw.cmd test` passed with 1,464 tests reported, zero failures/errors and two preexisting property-gated PR18 diagnostics skipped (`pr18.diagnostic` absent). All required PR23 suites executed. `git diff --check` passed. Exact selector commands and baseline/implementation decisions are recorded in the implementation plan. No optional developer check is required; fresh Step 5 review follows.
