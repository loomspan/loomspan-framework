# PR 24 Direct Dispatch Testing Plan

## Change Summary

Automatically construct an accepted assigned CALL_TOOL with empty model arguments when the pinned projected receiving contract proves no possible model contribution. Parent planning remains; noneligible dispatch, child reasoning and completion remain. This is observable behavior change, not a pure refactor.

Governing implementation plan: `ai/thoughts/plans/2026-10-05-loomspan-pr-24-direct-dispatch.md`. Full pipeline approved. Developer explicitly requires no compatibility shims. All verification is deterministic/offline; no paid evaluation or sibling reference-suite changes.

## Impacted Areas

- Input schema resolver/node, binding projection and BoundCapability.
- Assigned step generation/validation/invocation and lifecycle fencing.
- Provider usage versus task/tool/mission limits, access and generation checks.
- Canonical trace/live/journal observations and Console step/action raw-record details.
- Current Java/Go trace fixture corpus, root README and routed authoring guides.

## Risk Assessment

False eligibility is the primary risk: requiredness is not completeness, and unsupported raw schema keywords are lost today. Nested destinations remain conservative fallback; whole bound nested values may qualify. No proof may erase optional-field, null or ancestor-presence choices.

Removing provider sends also removes their incidental interruption check, usage admission and model advisors. Independently applicable task/session/deadline/repetition/auth checks must still execute. Distinguish provider quota (fewer real sends) from tool/mission/depth/max_steps quotas (unchanged). Use latches/fake clocks at actual boundaries rather than timing races or assertions that mirror implementation.

Protected paths are the allowlisted API, RestSkillHandler, existing manifest semantics, truthful observer lifecycles and complete input/security boundaries. Intentionally obsolete parent dispatch request counts/events and internal constructor shapes change atomically. No old/new dual behavior, historical trace migration, dummy request or new SPI is required.

## Existing Test Coverage

- `ChildInputBindingProjectionTest`: required unbound siblings, omitted bound ancestors and extension schemas; add eligibility proof matrix.
- `ChildInputBindingAssemblerTest`: exact source selection, detached containers, absence/null and ambiguity.
- `StepActionValidatorTest`: exact assignment, current state, argument contract and override rejection.
- `StepLoopMissionExecutionEngineTest`: offline queued model interactions, unit admission, grouped joins, failures, cutoff, task order and evidence isolation.
- `DeclaredChildInputBindingsIntegrationTest`: local MockWebServer through SkillTemplate, nine model/Java/REST combinations, exact large values, optional reasoning and child output correction. Existing producer becomes eligible; update queues/counts accordingly.
- `DeclaredChildInputBindingsTraceTest`: raw-model/effective-input separation and invocation provenance.
- `ConcurrentGroupedExecutionIntegrationTest`, `JavaSkillAuthenticationScopeIntegrationTests`, `JavaSkillLifecycleIntegrationTest`, `MissionLifecycleTest`, `SessionUsageServiceTest`: authentication, captured generation, quotas and lifecycle.
- `ConsoleTraceFixtureCorpusTest`, Go fixture/parser/plan tests, `TraceRecords.step.test.tsx`, `TraceRecords.stepAction.test.tsx`: current canonical/consumer behavior.

Gaps are proof provenance for unsupported raw schema, direct-path no-request accounting, mid-admission cancellation/access and misleading Console preselection language.

## Bug Reproduction / Failing Test First

- **Name:** `fullyBoundAssignedTaskSkipsParentDispatchModelInteraction`.
- **Type/location:** unit, `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`.
- **Arrange:** accepted one-task plan, explicit closed object input with its only field wholly bound from owning mission input, normal BoundCapability invocation capture, and ordinary final response completion. Fake provider supplies planning and final response only; distinguish interaction segments so an extra assigned dispatch cannot silently consume the final response.
- **Act/assert:** execute mission; expect planner and final synthesis calls, one child invocation with exact assembled input and correct result, no parent assigned-step model call. Use an injected planning fake if existing unit patterns do so; then assert only final model interaction and independently assert planner service invocation.
- **Pre-fix failure:** current `executeOneStep` always sends assigned request, so call count/segment assertion fails or the fake reports an unexpected request. Record actual red command/output before production change, then post-fix pass. Do not fabricate an unrun red result.

## Tests to Add/Update

### 1) `directDispatchEligibilityUsesEntireEffectiveContract`

- **Type/location:** parameterized unit, `ChildInputBindingProjectionTest` and `SkillInputContractResolverTest` (create focused resolver test if absent).
- **Proves:** closed all-root-fields-bound and explicit closed empty root qualify; all-required-bound with optional unbound field does not; same child under a second parent's partial bindings remains model-owned. Wholly bound optional fields qualify because binding requires source selection even when optional. Type/model/name do not determine eligibility.
- **Fixtures:** object schemas with required/optional fields, explicit additionalProperties false/true/omitted/typed, unspecified/blank/generic input, a whole bound object and array. Verify reason codes and shared empty-argument validation.
- **Mocks:** none.
- **Surface/expectation:** manifest/input semantics and internal implementation; deliberate current rule, no new keys.

### 2) `unsupportedSchemaAndNestedPresenceRemainModelDispatched`

- **Type/location:** parameterized unit, resolver/projection tests; one engine fallback case.
- **Proves:** `$ref`, allOf/anyOf/oneOf, defaults/const/conditions/property-count/unknown keywords, nullable union and malformed keyword shapes retain unsupported provenance even beneath bound fields. Root unknown/open contract stays conservative. Nested destinations always return ambiguous/unsupported, including required/optional closed ancestors, empty objects, unbound sibling and absent/null ancestor cases; empty-input-valid alone never means eligible.
- **Fixtures:** raw schemas and destination `/context/value`; wholly bound `/context` is a separate eligible case with exact value transfer.
- **Mocks:** offline ModelInteraction only for fallback dispatch.
- **Surface/expectation:** internal proof and manifest behavior; preserve model dispatch for unsupported proof shapes. No new JSON Schema solver or value synthesis.

### 3) `directAndModelActionsShareValidationAndInvocation`

- **Type/location:** unit, engine and StepActionValidator tests.
- **Proves:** exact task/tool identity, IN_PROGRESS and visible pinned capability are checked; constructed invalid action fails without correction/send. Direct empty arguments and equivalent valid model empty arguments assemble identical complete input/results/provenance via the ordinary invoker. Noneligible model action still receives its current single correction and cannot change assignment or finalize early.
- **Fixtures/mocks:** accepted plan, capturing BoundCapability/real DefaultCapabilityInvoker where existing helpers support it, fake responses for fallback only.
- **Surface/expectation:** internal execution and existing assignment semantics; one normal execution path, no direct business handler bypass.

### 4) `eligibleBindingFailureNeverRequestsModelRepair`

- **Type/location:** unit/integration, engine, assembler and binding trace tests.
- **Proves:** missing source key, ambiguous producer, rejected complete receiving value and failed/unavailable dependency produce attributable structured failure before child side effects, zero parent dispatch/repair calls and no successful tool completion. Invalid dependency graph is rejected before assignment; runtime source failures are not rewritten as plan repair. Source selection remains invocation-local.
- **Fixtures:** explicit null versus absent source, large integer/decimal, ordered arrays, JSON-looking strings and nested optional fields selected as whole bound subtree; exact task/skill provenance and detached input containers.
- **Mocks:** fake provider and child invocation counter; no model-generated source text.
- **Surface/expectation:** manifest binding semantics and canonical diagnostics; preserve exact values and ordinary failure codes/content.

### 5) `directDispatchRechecksCancellationDeadlineAndAccessBeforeSideEffects`

- **Type/location:** engine boundary tests plus focused facade/security integration in existing lifecycle/security test classes or a dedicated `DirectDispatchIntegrationTest`.
- **Proves:** admitted-but-not-yet-invoked direct task cannot start after lifecycle cancellation, mission deadline/cutoff or worker interruption. Revoke access after planning/admission and before actual routing; child execution denies without side effect. Trusted caller scope survives direct invocation. A handler/proxied application approval guard denies when approval absent and runs once when approved; Framework does not invent an approval gate.
- **Fixtures:** CountDownLatch/controllable executor gate, fake clock where supported, mutable authority supplier using existing access mechanisms, Java/REST handler counter guarded by ordinary business policy. Bound source and policy belong to captured generation.
- **Mocks:** local provider planning, real router/coordinator/lifecycle wherever the control is owned. Never merely verify a mocked guard was called.
- **Surface/expectation:** API invocation, RestSkillHandler/security, lifecycle; preserve controls reached incidentally through removed sends at an appropriate independent boundary.

### 6) `directDispatchPreservesTaskToolMissionAndRepetitionLimits`

- **Type/location:** engine/usage/integration tests, extend existing limit/repetition owners rather than introducing a parallel counter.
- **Proves:** max_steps still reserves whole-unit width/completion; tool and mission/depth limits reject before extra child execution even with zero parent sends. Tight provider quota now allows work when only eliminated sends would exceed it, but actual planning/child/final attempts still enforce quota. Repeated accepted task identities follow existing validation/invocation repetition protections; no new child side-effect retries are introduced after failure. Explicit distinct accepted tasks to the same capability retain current semantics, not an exactly-once promise.
- **Fixtures/mocks:** finite configured limits, admitted direct tasks, child failure counter, explicit provider-attempt records and queued fake responses. Investigate the existing repetition/session admission owner and test that owner on the new route, rather than assuming provider reservation implements business controls.
- **Surface/expectation:** documented limits and internal accounting; actual usage changes intentionally, independent limits remain.

### 7) `mixedWorkflowRemovesOnlyEligibleParentCalls`

- **Type/location:** integration, `DeclaredChildInputBindingsIntegrationTest` and/or `DirectDispatchIntegrationTest`.
- **Proves:** exact call-count reduction with one eligible Java/REST producer, one eligible model child, one optional-input consumer and accepted dependencies. Parent planning and noneligible dispatch remain; model child sends its own request and output correction; final synthesis/forwarding/full-output assembly follows existing ownership. Correct result and complete source values remain across nine child kind combinations.
- **Fixtures:** local MockWebServer or existing deterministic dispatcher labels requests by skill/segment. Return plan, actual child reasoning/correction, optional consumer dispatch and final response only as required; assert separate counts per segment, not just total.
- **Mocks:** local provider only; supported SkillTemplate and actual child invoker.
- **Surface/expectation:** public invocation/manifest contracts and observations; intentional parent call-count change, no pricing/timing assertion.

### 8) `eligibleGroupedTasksPreserveJoinOrderIsolationAndGeneration`

- **Type/location:** engine and `ConcurrentGroupedExecutionIntegrationTest`.
- **Proves:** enabled direct/model mixed groups admit atomically, overlap without increasing configured concurrency, fully join before dependent consumers, and fold reversed completions in accepted order. Disabled grouping serializes. One failure retains successful sibling but blocks later units; cutoff fences physically late writes. Activate another generation while workers are gated; admitted eligibility, bindings and invocation still use captured generation.
- **Fixtures/mocks:** existing latches/controllable executor and immutable pre-unit snapshots; no sleeps used as correctness oracle.
- **Surface/expectation:** planning/lifecycle manifest semantics; regression protection on the optimized route.

### 9) `frameworkDispatchTraceHasNoFictitiousModelEvidence`

- **Type/location:** trace/observer integration, `DeclaredChildInputBindingsTraceTest`, engine, `ExecutionJournalProjectorTest`, `LiveActivityProjectorTest` and usage tests.
- **Proves:** STEP_STARTED records origin/reason/exact assignment even on binding failure before a child frame. Normal validated step/tool start/terminal and provenance remain. No eliminated MODEL_CALL frame, sent/received/raw response/token/cost/retry/correction exists; child frames retain their true events/usage. Observers retain coherent task/tool/skill terminal states; model-specific hooks see only actual requests.
- **Fixtures/mocks:** direct success, direct binding failure, noneligible corrected model action and model-backed child. Assert canonical inputs/content unchanged; preserve existing journal-only redaction tests without expanding them.
- **Surface/expectation:** supported observer semantics and ephemeral diagnostics; current-run honesty and fidelity.

### 10) `consoleShowsAuthoritativeFrameworkDispatch`

- **Type/location:** Java `ConsoleTraceFixtureCorpusTest`; Go fixture/raw/parser tests; `TraceRecords.step.test.tsx` and `TraceRecords.stepAction.test.tsx`.
- **Proves:** an assigned step with no parent model frame parses/queries correctly, keeps accepted plan relationships, and exact raw records deliver origin/reason. Browser step details show selected assignment and Framework dispatch rather than claiming no task selected; action detail does not invent model proposal. Reason/identity render as inert text. Direct failure before tool frame is discoverable. Existing release marker rejection remains unchanged.
- **Fixtures/mocks:** current Java-written direct-dispatch fixture and exact raw record range browser mocks. Regenerate only current intentional repository corpus, never historical captures.
- **Surface/expectation:** coordinated current diagnostic consumers; no new enum, legacy reader or marker policy change.

### 11) `publicSurfaceAndAuthoringExamplesRemainCoherent`

- **Type/location:** `LoomspanPublicSurfaceArchitectureTest`; `DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration`.
- **Proves:** no API/internal type leakage, new supported extension point or Spring override surface. Existing complete three-file example loads and demonstrates producer eligibility/consumer optional fallback; documentation references focused tests and describes narrow nested exclusion, real child reasoning, accounting and no new mode.
- **Fixtures/mocks:** existing authoring example fences, architecture source/class inspection; none additional.
- **Surface/expectation:** closed API and manifest/documentation; no compatibility shim.

## How to Run

Use Java 21 and the repository Maven wrapper. Research could not resolve `java` from this shell; locate existing installed JDK and set process-local JAVA_HOME/PATH if necessary before claiming results. No installation or paid credentials should be necessary.

From repository root (PowerShell):

```powershell
.\mvnw.cmd test "-Dtest=SkillInputContractResolverTest,ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,StepActionValidatorTest,StepLoopMissionExecutionEngineTest"
.\mvnw.cmd test "-Dtest=DeclaredChildInputBindingsIntegrationTest,DeclaredChildInputBindingsTraceTest,BindingIsolationIntegrationTest,ConcurrentGroupedExecutionIntegrationTest"
.\mvnw.cmd test "-Dtest=MissionLifecycleTest,JavaSkillAuthenticationScopeIntegrationTests,JavaSkillLifecycleIntegrationTest,SessionUsageServiceTest,ExecutionJournalProjectorTest,LiveActivityProjectorTest"
.\mvnw.cmd test "-Dtest=ConsoleTraceFixtureCorpusTest" "-DfailIfNoTests=false"
.\mvnw.cmd test "-Dtest=LoomspanPublicSurfaceArchitectureTest"
.\mvnw.cmd test
```

Replace newly proposed test class names in commands with actual implemented locations when consolidating tests. Record exact commands actually executed; select the red test with `"-Dtest=StepLoopMissionExecutionEngineTest#fullyBoundAssignedTaskSkipsParentDispatchModelInteraction"` if implemented under that name.

From `loomspan-console`:

```powershell
go test ./...
go run ./internal/buildtool verify
```

If the current corpus is intentionally extended, use root `ConsoleTraceFixtureCorpusTest` with `"-Dloomspan.console.fixtures.regenerate=true"` once, inspect changed LF fixtures, then rerun without regeneration. No race detector, live provider run, historical replay or sibling suite rerun is an acceptance prerequisite. Required normal checks remain completion gates; an environmental blocker must be reported with exact failure and residual risk.

## Exit Criteria

- [x] A meaningful no-parent-dispatch red test is run against pre-change production behavior and its failure recorded.
- [x] Proof matrix excludes optional/open/unknown/unsupported/nested ambiguous contracts and admits clear closed fully bound/zero-input contracts.
- [x] Shared assigned validation, invocation-local sources and complete exact validation are established by executable tests.
- [x] Cancellation/deadline/interruption/access/approval/limit/repetition protections hold on direct execution before side effects; no business retry is introduced.
- [x] Mixed local workflow demonstrates exact segment-attributed reduction with actual child reasoning/correction and correct results.
- [x] Group scheduling, joins, deterministic folding, failure propagation and generation pinning pass on direct/mixed routes.
- [x] Canonical trace, observers, real usage and Console/browser detail views are coherent with no invented model evidence.
- [x] Architecture, focused verification, full Maven suite and Console standard verification pass; tests/call queues/fixtures encode the new behavior only.
- [x] Documentation/coverage accurately describe implemented subset and supported examples; no new execution switch or public SPI.
- [x] Captured evidence, unrelated work and sibling reference-suite artifacts remain untouched.
- [ ] Fresh independent review completes; any non-automatable observations are optional and not completion gates.

## Optional Developer Checks

None required. Exact behavior and call-count acceptance is testable offline; production timing/cost observation is outside the ticket.

## Implementation verification receipt

Step 4 implementation and verification completed on 2026-10-05. See [implementation receipt](2026-10-05-loomspan-pr-24-direct-dispatch-implementation.md) for exact acceptance mapping, commands/results, resolved test failures and bounded implementation decisions. Full Maven: 1476 tests, 0 failures/errors, 2 existing skips. Console standard verification: 525 browser tests plus build/type checks and Go packages. Independent Step 5 review remains pending.
