# PR 12 — Preserve Planner Evidence Testing Plan

## Change Summary

Complete successful task results become task-keyed mission evidence in assigned/final model prompts. Every grouped assignment uses the same pre-unit snapshot in either concurrency mode. Summaries and previews remain compact diagnostics; nested parents receive only the child's complete final return. Existing supported APIs, configuration, authorization, generations, dependency enforcement and lifecycle fencing remain intact.

Governing implementation plan: `ai/thoughts/plans/2026-10-01-loomspan-pr-12-preserve-planner-evidence.md`. Requirements and reproduction: `ai/thoughts/tickets/loomspan-pr-12-preserve-planner-evidence.md`; research: `ai/thoughts/research/2026-10-01-loomspan-pr-12-preserve-planner-evidence.md`. This stage designs tests and writes no test/source changes.

## Impacted Areas

- `MissionContext`: ordered immutable task/skill/result records, snapshot independence, summary separation and nested ownership.
- `StepPromptBuilder`: exact result serialization, assigned argument guidance and tool-prohibited final synthesis.
- `StepLoopMissionExecutionEngine`: pre-unit snapshot capture, assignment retries, normal/cutoff success folds, failed/unfinished exclusion and branch visibility.
- Real auto-configuration, public `SkillTemplate`, Java `@SkillMethod`, `RestSkillHandler` and YAML skills via `PlannerEvidenceFlowIntegrationTest`.
- Documentation routing/coverage, planning concurrency and evidence annotation guidance.

## Risk Assessment

- Raising the latest-result limit or preserving only the newest record would pass a simple long-tail test while still failing earlier sibling, retention and identity criteria. Require multiple full records and more than five completions.
- Completion-order mutation would change authoritative ordering and expose sibling values before full join. Use controlled latches and inspect assignment requests plus post-join snapshots.
- Serialized groups can accidentally read prior member results because existing per-member fold remains; explicitly assert identical pre-unit evidence/summary views in both modes while preserving serialized lifecycle transitions.
- A synchronized collection alone does not fence writes. Assert cutoff/ancestor permissions at engine integration boundaries and observe after physical late return.
- JSON may escape content without losing it. Test decoded result equality, not only raw substring search; wire tails alone are insufficient for quote fidelity.
- Empty/whitespace results must remain real successful entries. Duplicate task IDs must not overwrite prior content; repeated skill names must retain separate task identities.
- Nested intermediate state, unrelated mission data and authorization/generation boundaries must stay isolated. Testing a returned child result is distinct from exporting private child state.
- Complete prompt delivery increases context cost. Preserve explicit step/quota/provider failure behavior and prohibit fallback truncation.

**Protected compatibility paths**: closed public Java allowlist, `SkillTemplate` invocation/observation, Java reflected inputs, REST handler SPI, existing planning/concurrency/manifests and output/evidence validation behavior. **Intentional obsolete paths**: internal latest-result slot and authoritative 1,000-character clipping; old truncation assertions are replaced, not preserved behind aliases. Summaries retain their five-line/100-character progress characterizations independently. No durable/Console protocol or release-marker change is required.

**Authoring claims requiring evidence**: complete prior-unit direct results, distinct task/skill identity, serialized/concurrent group snapshot parity, final synthesis completeness, nested returned-only boundary, explicit child arguments, ordinary `$ref` objects, `ref://` file/attachment scope, and the separation of schema validity/success annotations from factual correctness. Reference limitations remain supported by the existing materializer path and public raw-object control; do not invent a new reference protocol test.

## Existing Test Coverage

- `PlannerEvidenceFlowIntegrationTest#characterizesActualWireEvidence`: four public HTTP cases (concurrent/serial, long/short) plus Java/REST direct returns, observations and ordinary `$ref` nested data. Long cases currently assert the defect and must become desired regressions.
- `PlannerEvidenceFlowIntegrationTest#completeEvidenceMustReachDependentAndFinalRequests`: opt-in actual-request completeness assertion, currently red at earlier/tail boundaries. Preserve all its requirements; remove the opt-in after production is fixed.
- `MissionContextTest`: five-line summary eviction, immutable successful-name snapshots, diagnostic merges and cross-session-parent rejection.
- `StepPromptBuilderTest`: assignment identity/correction guidance, tool-input contract shape, output-schema metadata isolation, and current obsolete latest-result truncation assertion.
- `StepLoopMissionExecutionEngineTest`: enabled all-member admission, reverse-order fold, following-unit join gate, ordinary failures, serialized groups, retries, step limits, dispatch rejection and timeout/late writes.
- `ConcurrentGroupedExecutionIntegrationTest`: real nested planner/direct execution, authentication, mission/diagnostic isolation, depth/quota/timeout behavior and physically late nested work.
- `SkillGenerationExecutionIntegrationTest`, `ExecutionCoordinatorMissionContextIntegrationTest`, `MissionLifecycleTest`, `JavaSkillMissionCutoffTest`, `NestedSuccessfulSkillBoundaryTest`, and `LoomspanPublicSurfaceArchitectureTest` protect adjacent supported paths and internal invariants.

Gaps: task-keyed historical result retention, byte-exact prompt record round-trip, serialized pre-unit evidence boundaries, seven-plus completions, repeated skill/task identities on the wire, returned nested planner quote/citation tails, and absence of nested/private/unrelated data in actual parent requests.

## Bug Reproduction / Failing Test First

- **Type**: public integration over a local protocol-compatible HTTP endpoint.
- **Location**: existing `src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java`.
- **Arrange**: real auto-configured `SkillTemplate`, Java source A, REST sources B/C, model-backed assessment, nested YAML/REST result, deterministic `MockWebServer` OpenAI `/v1/chat/completions`. Place distinct facts after character 100 and after 1,000 in independent source results; use only neutral root case input.
- **Act**: invoke the public root; capture actual complete HTTP bodies. Script assessment arguments and final answers by extracting only facts actually present in incoming messages.
- **Assert**: all A/B/C tail facts occur in the dependent assigned-step request and actual nested assessment input; N tail occurs in native final request. Keep seven existing soft assertions intact.
- **Expected failure pre-fix**: A/B/C tail facts absent in assigned and assessment requests, and N tail absent from native final request. Historical investigation reports seven failures, but implementation must execute the command against the current pre-fix checkout and report its actual outcome.
- **Command**: `mvn -Dtest=PlannerEvidenceFlowIntegrationTest#completeEvidenceMustReachDependentAndFinalRequests -Dloomspan.evidence.requireComplete=true test`.

Run this before production edits. No new test code is required to obtain red evidence. If an environmental error prevents the test, resolve the existing JDK/runtime setup; do not report the error as reproduction of the defect.

## Tests to Add/Update

### 1) `retainsCompleteTaskResultsIndependentlyOfProgressSummaries`

- **Type/location**: unit, `src/test/java/ai/loomspan/internal/core/MissionContextTest.java`.
- **Proves**: seven-plus successful records survive summary eviction; same skill under different task IDs retains distinct complete values in accepted insertion order; empty/whitespace results remain; snapshots do not change after later recording and reject mutation; duplicate task recording rejects overwrite; invalid identity/null result rejects.
- **Data/mocks**: direct internal mission fixture, long Strings, repeated skill name, no model mocks.
- **Surface/expectation**: internal implementation; replace latest-result assertions atomically while preserving the name-set and summary controls.

### 2) `rendersLosslessTaskEvidenceForAssignedAndFinalPrompts`

- **Type/location**: unit, `src/test/java/ai/loomspan/internal/runtime/step/StepPromptBuilderTest.java`.
- **Proves**: both prompt modes encode every supplied task/skill/result without clipping. Parse the evidence JSON and compare returned String values exactly, including late quotation/citation fields, escaped quotes/backslashes, Unicode, line breaks, delimiter/header-looking text, empty/whitespace results and repeated skill names. Assignment/action/final no-tools guidance remains valid. Empty snapshot emits no invented result.
- **Data/mocks**: typed immutable records and existing accepted-plan/tool contract fixtures. No HTTP needed.
- **Surface/expectation**: internal model prompt representation; remove the old “latest result truncated” expectation and signature helpers, with no legacy overload.

### 3) `groupMembersUseOnlyPreUnitEvidenceInEitherConcurrencyMode`

- **Type/location**: unit, `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`.
- **Proves**: after an initial unit returns a long sentinel, both members of the next group see that complete result but neither sees sibling result content in evidence or progress summary, regardless of concurrency flag. A later dependent unit sees both complete joined results. Disabled execution still admits/folds per member in order and uses existing transition counts. A correction retry uses the same immutable snapshot.
- **Data/mocks**: existing task-addressed model captures full `ModelInteractionRequest` prompts, accepted grouped plan, bound capabilities and barriers. First member returns a distinctive result; second asserts absence from its input.
- **Surface/expectation**: internal/lifecycle semantics and documented concurrency behavior; preserve serialization, accepted dependencies, admissions and exact-assignment retry contract.

### 4) `reverseCompletionRetainsAcceptedOrderAndWaitsForEveryMember`

- **Type/location**: extend existing reverse-fold/following-unit tests in `StepLoopMissionExecutionEngineTest.java`.
- **Proves**: worker outcomes finishing in reverse order do not mutate parent evidence before join; after join the record order is accepted task order, and later/final prompts contain both complete results. Successful completion waits for all members; completion timing never keys identity.
- **Data/mocks**: existing latch-controlled tasks with same skill and different IDs, long outputs, full request capture. No sleeps.
- **Surface/expectation**: internal/current-run diagnostics; keep atomic admission and one enabled-group joined-plan publication assertions.

### 5) `completeEvidenceMustReachDependentAndFinalRequests` and long/short wire controls

- **Type/location**: integration, `PlannerEvidenceFlowIntegrationTest.java`.
- **Proves**: existing seven completeness assertions become ungated ordinary coverage. Replace defect-only expectations in long characterizations with middle/tail presence in assigned and child-input requests, final nested tail presence, and public completion. Retain short-result, direct invocation, full observation, correct protocol path and `$ref` ordinary-data assertions.
- **Data/mocks**: retain the existing local HTTP harness and public auto-configuration. All mock replies derive from received input, not missing source facts supplied by the test.
- **Surface/expectation**: protected application API/SPI/configuration paths; no internal replacements or weakened result assertions.

### 6) `earlierResultsSurviveSevenCompletionsAndRepeatedSkillCalls`

- **Type/location**: integration, same class or focused companion in `src/test/java/ai/loomspan/integration/`.
- **Proves**: at least seven source tasks complete before a dependent model-backed assessment. Two or more tasks use the same skill name with unique task IDs and distinct result facts. Actual assigned/final request JSON evidence includes each task's exact skill identity and decoded full source String; nested assessment receives every required fact. Earliest output remains after summary eviction.
- **Data/mocks**: Java/REST skill input discriminator produces task-specific long text. Accept a generated plan with correct earlier-unit dependencies and adequate existing `max_steps`. Neutral root input contains no downstream answer. Parse assigned identity rather than assuming task ID equals tool name.
- **Surface/expectation**: application/configuration behavior; complete retention and identity, no new storage/reference contract.

### 7) `wireCompletenessIsIndependentOfConcurrencyAndFinishOrder`

- **Type/location**: parameterize the integration harness above.
- **Proves**: completeness in concurrent normal finish order, concurrent reversed finish order, and disabled serialized groups. Assert sibling-assignment requests exclude newly completed same-group sibling data, and downstream requests include all joined records. Preserve short-result controls for enabled/disabled execution.
- **Data/mocks**: latches/barriers coordinate leaf completion without blocking the provider request dispatcher; capture known task completion order separately. Serialized mode necessarily completes in accepted order; reversed finish order is asserted only for overlapping enabled groups.
- **Surface/expectation**: protected concurrency/ordering contract, complete-evidence parity.

### 8) `nestedPlannerReturnsCompletePublicResultWithoutIntermediateLeakage`

- **Type/location**: integration, same public HTTP harness.
- **Proves**: a root assigned task invokes a planning YAML child that calls its own Java/REST sources. The child receives its complete intermediate results, then returns a large schema-valid result with late authoritative quote and citation fields derived from those received results. Parent native synthesis contains the child's returned value exactly and completes through `SkillTemplate`. Child-private intermediate sentinel and task records absent from its return never appear in parent evidence/model requests.
- **Data/mocks**: distinguish root and nested planning/synthesis by their instructions/capability and accepted identities; ensure assertions inspect the root final request, not the child's final request. Script the child final response by selecting received public quote/citation data and omitting its private sentinel; do not inject an unseen quote or preload root answers. Include a separate root invocation returning an unrelated unique source marker and assert no cross-mission contamination in either direction.
- **Surface/expectation**: application API and internal isolation; child returns cross the existing boundary, private collections do not merge. Ordinary name-only nested success credit remains unchanged.

### 9) `failedUnfinishedAndLateOutcomesNeverBecomeSuccessfulEvidence`

- **Type/location**: extend existing failure/dispatch/cutoff tests in `StepLoopMissionExecutionEngineTest.java`, plus focused `ConcurrentGroupedExecutionIntegrationTest` late-nested control where needed.
- **Proves**: a joined successful sibling retains its exact task-keyed result when another fails, but no later dependent/final requests occur; failed task has no success entry. At partial dispatch/cutoff, only permissible published successful outcomes enter evidence; pending/admitted-unfinished tasks do not. After caller returns and interrupted/ignoring child work physically returns, retained evidence is unchanged and no late success result or nested private output is added.
- **Data/mocks**: existing lifecycle bindings, cutoff clock/latches, ordinary failure causes and late-return barriers. Assert state at return and after deterministic physical release; preserve exact cause and frame cleanup assertions.
- **Surface/expectation**: internal lifecycle/current-run diagnostics and application failure behavior; preserve existing fences rather than adding synchronization-only permission.

### 10) Existing authorization, generations, schemas, references and architecture controls

- **Type/location**: existing focused suites named under coverage, including `NestedSuccessfulSkillBoundaryTest` and `LoomspanPublicSurfaceArchitectureTest`.
- **Proves**: authorized visibility/generation capture remain exact; denied/failed work earns no result/success credit; execution/step/quota failures remain explicit; successful direct skill-name annotations remain distinct from task data; public signatures leak no internal types and no new supported SPI/configuration surface is introduced.
- **Data/mocks**: existing repository fixtures and local model fakes; no new implementation-mirroring tests for unchanged behavior.
- **Surface/expectation**: protected API/SPI/configuration plus current lifecycle safeguards. Run all architecture checks after production changes. No obsolete internal slot compatibility tests survive.

## How to Run

Use installed Maven (`c:/hamdev/maven/bin/mvn.cmd` resolves as `mvn` in this checkout). Discover an existing Java 21+ JDK and set process-scoped `JAVA_HOME` and `PATH` if necessary; research found Java absent from the current PowerShell PATH, not absent from the machine. Do not install dependencies/JDK speculatively or edit release configuration. No live provider URL, credentials or neighboring repository are needed.

1. Pre-fix reproduction: `mvn -Dtest=PlannerEvidenceFlowIntegrationTest#completeEvidenceMustReachDependentAndFinalRequests -Dloomspan.evidence.requireComplete=true test`.
2. State/prompt/engine/lifecycle/architecture: `mvn -Dtest=MissionContextTest,StepPromptBuilderTest,StepLoopMissionExecutionEngineTest,MissionLifecycleTest,LoomspanPublicSurfaceArchitectureTest test`.
3. Public wire and surrounding integrations: `mvn -Dtest=PlannerEvidenceFlowIntegrationTest,ConcurrentGroupedExecutionIntegrationTest,SkillGenerationExecutionIntegrationTest,ExecutionCoordinatorMissionContextIntegrationTest,JavaSkillMissionCutoffTest,NestedSuccessfulSkillBoundaryTest,LoomspanPublicSurfaceArchitectureTest test` (add any companion regression class to the filter).
4. Full regression: `mvn test`. This includes compilation and default Surefire suites. Use existing repository build configuration; do not invent a separate lint gate that the project does not provide.
5. Scope check: `git diff --check`; `git status --short`; inspect the actual diff for untouched production version/compatibility files. Preserve pre-existing untracked investigation artifacts; verify the neighboring repository remains untouched without repairing any unrelated state.

On PowerShell, quote the complete `-Dtest=...` argument if the shell requires it. Reports must show the exact executed command, JDK environment discovery, real red failure, and actual pass/fail counts. Historical runs are context only. Provider capture files may live in `target/evidence-flow` and are diagnostic build artifacts; unique labels should distinguish matrix cases rather than overwrite useful evidence.

## Exit Criteria

- [x] Current pre-fix completeness test fails on missing outbound evidence, then the same full requirements pass post-fix without the opt-in gate.
- [x] Actual local outbound requests contain distinct earlier sibling facts past character 100/1,000 and complete decoded result values, repeated skill/task identities and more than five completed results.
- [x] Result-derived nested inputs contain all required facts; mock replies contain no missing-fact injection and root inputs contain no preloaded downstream answers.
- [x] Native root final requests retain complete nested returned results and exact late quote/citation fields; public facade reports completion without live credentials.
- [x] Enabled normal/reversed completion and disabled serialized groups satisfy completeness and pre-unit visibility; accepted order and existing join/admission semantics remain protected.
- [x] Nested private intermediates and unrelated mission markers never leak into parent evidence; cutoff/late-write and failed/unfinished controls pass.
- [x] Short-result, authorization, captured-generation, reference, output/evidence annotation, schema, quota/step-limit and failure controls pass.
- [x] State/prompt unit tests prove exact arbitrary-string encoding, empty results, immutable snapshots and duplicate rejection; obsolete latest-result paths are removed atomically.
- [x] Public-surface architecture and full Maven regression pass; any actual environmental failure is explicitly reported and resolved or escalated, never counted as a pass.
- [x] Documentation claims have executable anchors, routing/coverage are synchronized and schema validity/success supportability are clearly separated from source correctness.
- [x] Production release/version strings and neighboring acceptance-suite repository remain unchanged; supplied investigation artifacts are preserved.
- [ ] Independent review reconstructs current requirements/diff and reruns sufficient focused/public/architecture checks using local fakes; existing full-suite results may be assessed without unnecessary repetition unless review changes justify it.

## Optional Developer Checks

None required. Live-provider quality or quote fidelity for arbitrary outputs is outside this correctness contract and is not a completion gate.

## Implementation verification (2026-10-01)

Step 4 confirmed the unchanged pre-fix regression fails at all seven original soft assertions, then ran ordinary ungated post-fix completeness and the expanded wire/lifecycle matrix. Final focused/adjacent verification passed 131 tests; full `mvn test` passed 1,207 tests with no failures/errors/skips. Exact commands and acceptance mapping are recorded in `ai/thoughts/implementation/2026-10-01-loomspan-pr-12-preserve-planner-evidence.md`. Independent review remains for Step 5; no required implementation check is unrun.
