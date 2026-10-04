# PR 20 — Designated Child Result Testing Plan

## Change Summary

- Add strict opt-in planning `output_from`, resolve schema metadata through acyclic direct-child forwarding chains in the captured generation, and return the selected accepted task's existing String result unchanged after complete successful work.
- Forwarding performs no parent final model response, duplicate schema/evidence/linter validation or parent output correction. Ordinary synthesis remains unchanged.
- Expose effective metadata through the existing descriptor/tool/catalog owners and Console skill detail. Emit authoritative `RESULT_FORWARDED` diagnostics rather than reconstructing completion from missing model records.
- Execution mode: pipeline; profile: `full`. This is a test design artifact only; no tests or production changes are performed in Steps 2/3. The implementation plan is `ai/thoughts/plans/2026-10-04-PR-20-forward-designated-child-result.md`.
- User decisions: minimize technical debt while fully meeting the goal; necessary Java–Go changes are authorized; “we are in development, destructive changes welcome, so no compatibility shims”. The supported descriptor constructor/record shape changes atomically. Tests must establish the new shape and absence of an obsolete constructor, not demand old/new dual behavior.

## Impacted Areas

- `src/main/java/ai/loomspan/internal/skill/`: manifest presence/type checks, definition copies, complete-set graph resolution, generation publication and metadata construction.
- `internal/core/CapabilityToolDescriptor`, `api/SkillDescriptor`, public/internal catalogs, bound tools and `DefaultPlanningService` visible tool descriptions.
- `internal/runtime/step/StepLoopMissionExecutionEngine`, mission-owned results, state transitions and lifecycle fencing.
- Producing model validation/advisors, annotation-only Java serialization, REST handler returns and common authorization/execution limits.
- Canonical trace recorder, live/journal projections, Java observability DTOs, Console trace/live/skill DTOs and outward browser/MCP/TypeScript views.
- Trace, application REST and SSE fixture corpora and expected projections; documentation library and supported-surface assertions.

## Risk Assessment

1. Derived metadata accidentally feeding a parent's validator, evidence ledger or correction path; Java/REST metadata falsely claiming validated output.
2. Result selection based on child name globally, completion order, latest active generation or a truncated preview instead of accepted mission task ID.
3. Early success when the selected child finishes before required siblings/later units; stale/missing/ambiguous results incorrectly falling back to synthesis.
4. Forwarding retaining the N+1 reservation, changing ordinary synthesis admission, or charging fictitious provider/step usage.
5. Timeout/cancellation races permitting a forwarding event or success after ancestor cutoff; group failures losing ordinary join/failure ordering.
6. Child result reserialization, Java String unquoting, REST envelope extraction, array order changes or unintended citation/content additions.
7. Cyclic graph recursion, inconsistent startup/validate/reload errors, mutable metadata or generation publication substituting current definitions.
8. Java/Go/TS vocabulary drift; metadata omitted from typed projections; invented parent model response; loss of exact-version/access behavior.

Protected contracts are ordinary synthesis, existing YAML schemas/retries/evidence/linter, `SkillTemplate` String return, `RestSkillHandler` signatures/auth/generation behavior, local allowlists and execution-time RBAC, normal limits and lifecycle, same-version portable diagnostic transfer and existing journal projection exception. The descriptor's old constructor/record arity is intentionally replaced under user authorization. Internal construction shapes and obsolete fixture/projection assumptions change atomically; no historical trace reader is required.

## Existing Test Coverage

- `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`, `YamlSkillDefinitionTest.java`, `SkillGenerationManagerTest.java`: strict declarations, field presence/REST applicability, defensive copies, complete-set identities, missing child and reload validation.
- `src/test/java/ai/loomspan/internal/runtime/planning/PlanningServiceTest.java`, `PlanTaskConstraintValidatorTest.java`, `PlanStructureValidatorTest.java`: exact generated-task cardinality, authorized visibility, combined single correction, storage exclusion and structural grouping/dependency validation.
- `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java`: task assignment/correction, complete results, ordered joins, failure/cutoff, nested depth and usage failures. `rejectsWholeGroupedUnitBeforeAdmissionWhenFinalSynthesisWouldNotFit` and `admitsExactlyNAssignmentsPlusFinalAtMaxStepsNPlusOne` protect ordinary synthesis accounting.
- `src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java`: real local HTTP model requests through `SkillTemplate`, Java/REST/model composition, full text, repeated identities, nested isolation. Use its `ApplicationContextRunner`, `MockWebServer`, actual declaration and request-capture patterns, not replacement framework beans.
- `ConcurrentGroupedExecutionIntegrationTest`, `SuccessfulSkillCompletionBoundaryTest`, `SkillGenerationExecutionIntegrationTest`, `ExecutionCoordinatorOutputSchemaIntegrationTest`: real nested auth/lifecycle/validation/generation paths.
- `ConsoleTraceFixtureCorpusTest`, `ConsoleRestFixtureCorpusTest`, `ConsoleSseFixtureCorpusTest` plus Go corpus readers, observability/live/browser/MCP contract tests and frontend presentation tests protect synchronized diagnostic semantics.
- `LoomspanPublicSurfaceArchitectureTest` is mandatory after production type changes and governs deliberate API classification.

Gaps: no forwarding syntax/path/metadata resolver currently exists, no exact task-based alternate completion, no N-slot forwarding admission, and no authoritative forwarding diagnostic/corpus. Existing tests establish reuse patterns, not proof that the requested feature works.

## Bug Reproduction / Failing Test First

This is a requested new behavior, not a pure refactor or an attempt to reproduce the original application/provider observation.

**First red test:** `acceptsPlanningOutputFromForUniqueRequiredDirectChild`, unit test in `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java`.

- Arrange an explicit model-backed planner with one `{name: finish, required: true, max_tasks: 1}` entry, `output_from: {skill: finish}`, and configured test model; no parent schema/linter/retries.
- Act through the real configured catalog loader with matching child definition or complete-set preparation where necessary.
- Assert a valid immutable parent definition containing the exact selected child name.
- Current checkout fails on unsupported/unknown `output_from`, establishing the missing declaration path without requiring uncompilable new accessors. Record actual failure before implementing Phase 1.

**Runtime red after Phase 1, before Phase 2:** `forwardsSelectedTaskResultWithoutFinalModelCall`, unit test in `StepLoopMissionExecutionEngineTest.java`.

- Arrange a real validated forwarding definition, a two-task accepted plan with prerequisite and `finish`, max_steps 3 (so old N+1 admission does not hide the synthesis failure), and bound callbacks returning distinct known Strings.
- Script exactly the two assigned `CALL_TOOL` responses; fail immediately on any parent `FINAL_RESPONSE` request. Use existing initializing planning service and real mission/binding/state/lifecycle harness.
- Assert returned String equals `finish` text exactly and both tasks completed.
- Before Phase 2, old runtime requests synthesis or fails when the script is exhausted. After Phase 2, it returns the child text. Add the N-slot case separately to prove changed accounting.

## Tests to Add/Update

Each named group below represents focused cases or a parameterized family. Exact method splitting can follow existing class conventions; assertions and boundaries are mandatory.

### 1) Strict forwarding declarations and defensive definition copies

- **Type/location:** Unit; `internal/skill/YamlSkillCatalogTests.java`, `YamlSkillDefinitionTest.java` under `src/test/java/ai/loomspan/`.
- **Names:** `acceptsPlanningOutputFromForUniqueRequiredDirectChild`; `rejectsInvalidOutputFromDeclarations`; `preservesOutputFromPresenceAndDefensiveCopies`.
- **Proves:** Valid model planner with exact direct child accepted; reject absent/false/null planning mode, REST declaration, scalar/list/null/empty block, missing or unknown field, null/blank/invalid/whitespace/case-mismatched skill, nonallowlisted target, missing/false required, absent/0/>1 max_tasks, and both output declarations. `min_tasks: 1` alone does not satisfy required true. Explicit null schema/linter/retry fields still conflict. Ordinary nonforwarding declarations keep current applicability rules.
- **Fixtures/data:** Table-driven minimal YAML on existing configured test model; precise `output_from`, `output_from.skill`, conflicting-field or indexed child path assertions; mutated manifest copies after construction.
- **Mocks:** Existing model catalog configuration only; use real YAML codecs/loader. No provider requests.
- **Affected surface/expectation:** Configuration and manifest; new enforced contract plus protected ordinary declarations.

### 2) Complete-set forwarding graph and schema ownership

- **Type/location:** Unit; `internal/skill/SkillGenerationManagerTest.java` and resolver tests if a helper is introduced.
- **Names:** `resolvesForwardingSchemasAcrossCompleteCandidateGeneration`; `rejectsCyclicForwardingDefinitions`; `forwardingWithoutTerminalSchemaRemainsUnspecified`.
- **Proves:** Single hop/multihop copies terminal normalized metadata; declaration order independent. Self and two/three-node forwarding cycles fail with useful source/field path before preparation/activation. An ordinary recursive allowed-child graph without forwarding edges is not newly rejected. Missing/non-direct target fails; failed-document suppression avoids misleading cascades. Java/REST/no-schema model targets resolve null; no inferred object schema. Child evidence annotations do not become parent schema constraints/evidence requirements. Parent authored schema accessor remains null and terminal validators retain original authored schema.
- **Fixtures/data:** Complete candidate maps across all three types, terminal nested properties/arrays/nullable/enum/open-object schema, child-local evidence, missing/cyclic cases.
- **Mocks:** Existing test fixed Java capabilities/REST handler/runtime factory; real complete-set checking and schema normalization.
- **Affected surface/expectation:** Configuration, internal generation and metadata; deliberate strictness, no new Java/REST validation guarantee.

### 3) Public catalog, bound tools and planning metadata

- **Type/location:** Unit; `internal/skillapi/DefaultSkillCatalogTest.java`, `internal/runtime/planning/PlanningServiceTest.java`; supported-surface integration/architecture assertions.
- **Names:** `catalogExposesEffectiveOutputSchemaWithoutNewApiTypes`; `planningPromptDescribesGenerationBoundOutputMetadata`; `skillDescriptorHasOnlyCurrentCanonicalConstructor`.
- **Proves:** Descriptor has five supported public components with String metadata or null; no internal/autoconfigure types leak, no new top-level supported type or SPI, no four-argument compatibility constructor. In-repository construction/equality assertions use current shape. Original input schemas and eager/unfiltered/sorted immutable discovery stay unchanged. Authorized visible tools expose available output schema without changing provider input schema or exposing hidden tools. Metadata is advertised as output information, not parent validation. Forwarding intent/cardinality appears in planner requests.
- **Fixtures/data:** Ordinary model, one-hop/chain, Java and REST/no-schema entries; normalized metadata checks using JSON values, no whitespace contract; unauthorized selected child.
- **Mocks:** Real catalog and bound descriptors, existing deterministic planning-model capture.
- **Affected surface/expectation:** Application API intentional constructor/record break; protected discovery/access/input semantics; internal atomic updates.

### 4) Cardinality, visibility and corrective planning

- **Type/location:** Unit; `PlanningServiceTest`, `PlanTaskConstraintValidatorTest`, `PlanStructureValidatorTest`.
- **Names:** `forwardingPlansRequireExactlyOneDesignatedVisibleTask`; `forwardingCountCorrectionUsesExistingSingleRetry`.
- **Proves:** Zero/two exact selected tasks fail existing min/max validation, one succeeds independent of title/intent/group/order. First invalid plan can correct once; exhausted invalidity stores no plan/emits no PLAN_CREATED. Unauthorized selected child fails before model call; declaration never expands visibility. Selected task ID is a normal unique structural ID; unrelated repeated child uses are permitted when their own bounds allow them.
- **Fixtures/data:** Exact/case-mismatched names, zero/one/two selected tasks, valid groups/dependencies and combined invalid structure/count feedback.
- **Mocks:** Existing scripted planner; real validators/state and current correction policy.
- **Affected surface/expectation:** Configuration/planning and authorization; protected retry/local access boundaries.

### 5) Exact completion and result fidelity

- **Type/location:** Unit; `internal/runtime/step/StepLoopMissionExecutionEngineTest.java`, `internal/core/MissionContextTest.java` if exact-ID access changes.
- **Names:** `forwardsSelectedTaskResultWithoutFinalModelCall`; `forwardsExactRetainedTextForAllRepresentations`; `rejectsMissingAmbiguousOrMismatchedForwardedResult`.
- **Proves:** Return exact selected String, not prerequisite/latest/sibling/descendant/global-name result. Include empty, whitespace, escaped quotes/newlines, long payload beyond preview/summary bounds, JSON object/array/null text, ordered arrays and fields that must remain intact. No schema/linter/evidence parent validator event or correction, synthesis request, result parsing/unquoting/envelope extraction or content additions. Defensive runtime selection rejects a bypassed invalid plan, missing retained result, skill mismatch or stale plan; no fallback. Mission duplicate/null-result prohibitions remain.
- **Fixtures/data:** Distinct per-task markers, same registered names in nested independent missions, direct-result Strings of varied shape. Corrupt internal state only in narrow unit harness to test fail-closed defenses; do not introduce production mutation APIs.
- **Mocks:** Existing scripted ModelInteraction and bound callback harness; fail-on-final request; real result retention/state.
- **Affected surface/expectation:** Internal runtime and application return semantics; new forwarding behavior, protected String representation.

### 6) Complete work and step admission

- **Type/location:** Unit; `StepLoopMissionExecutionEngineTest`.
- **Names:** `forwardingWaitsForLaterRequiredWork`; `forwardingWaitsForWholeSelectedParallelUnit`; `admitsExactlyNForwardingAssignmentsAtMaxStepsN`; `rejectsWholeForwardingUnitWhenAssignmentsDoNotFit`.
- **Proves:** Selected task can complete first but parent remains pending until every accepted task completes. All accepted optional tasks also execute. Group reverse completion and concurrency false retain normal preunit snapshots/folding. N assignments fit N slots without fictitious completion slot; N-1 fails atomically before oversized group admission. Ordinary N+1 synthesis and correction-within-assignment tests remain unchanged. Planning/provider/depth/tool limits still apply.
- **Fixtures/data:** Singleton before/after selected, grouped selected with held required sibling, N task boundaries and same manifests with output_from absent.
- **Mocks:** Scripted assignments, real executor and CountDownLatch ordering, bounded waits. No arbitrary sleeps.
- **Affected surface/expectation:** Configuration, lifecycle and internal admission; protected ordinary synthesis, intentional forwarding accounting.

### 7) Failure, interruption, timeout and late writes

- **Type/location:** Unit and integration; `StepLoopMissionExecutionEngineTest`, `ConcurrentGroupedExecutionIntegrationTest`, `SuccessfulSkillCompletionBoundaryTest`.
- **Names:** `forwardingFailureNeverProducesParentSuccess`; `forwardingCancellationFencesCompletionAndLateOutcomes`.
- **Proves:** Selected failure, required sibling/later failure, multiple group failures, denied child, invalid input, provider quota/depth failure, mission timeout, caller interruption and dispatch rejection never yield successful forwarded result or fallback model call. Ordinary failing member does not cancel started sibling and earliest task-list failure stays primary. Selected success retained amid later failure is diagnostic evidence only. Hold a selected child/sibling through cutoff, release physically late, assert no parent success/RESULT_FORWARDED/state/usage write after finalization; authentication/binding/executor thread context restores normally.
- **Fixtures/data:** Existing nested/cutoff harnesses and real auth, shared quotas, latches; exact original failure causes and plan statuses.
- **Mocks:** Only external model/capability behavior. Keep existing production lifecycle rather than mock its gates.
- **Affected surface/expectation:** Protected authorization/lifecycle/limits and current diagnostics; success-only credit unchanged.

### 8) Supported facade across child kinds and forwarding chains

- **Type/location:** Integration; new `src/test/java/ai/loomspan/integration/DesignatedChildResultIntegrationTest.java`, modeled on `PlannerEvidenceFlowIntegrationTest` and `ExecutionCoordinatorOutputSchemaIntegrationTest`.
- **Names:** `forwardsModelJavaAndRestResultsThroughSkillTemplate`; `forwardingChainRetainsProducerValidationAndExactResult`; `ordinarySynthesisRemainsAvailable`.
- **Proves:** Real registered planning root executes prerequisite and returns exact producer contract through supported SkillTemplate. Model child with schema gets normal validation/correction (invalid then corrected); corrected child output is parent output, with no second validator invocation/event/request. Child evidence satisfied in child's mission does not become parent evidence. Java returns map/array/quoted String/null JSON as its current Jackson path; REST handler text including business-looking envelope remains unchanged and null return still fails. No-schema model/Java/REST forwarding works without fabricated schema. Chain eliminates synthesis at each forwarding parent, retaining any actual producing child's existing synthesis. Ordinary parent still final-synthesizes and validates its authored schema/linter/evidence.
- **Fixtures/data:** Actual annotation bean and RestSkillHandler, local model HTTP dispatcher with per-skill request classification and unexpected-call failure; general application-neutral YAML. Count parent planning/assigned requests separately from producing child requests.
- **Mocks:** MockWebServer replaces external provider only; real Spring wiring, public facade, coordinator and registered declarations. No internal bean replacement as consumer API.
- **Affected surface/expectation:** Application API/SPI, configuration and result representation; all protected existing producer paths plus requested forwarding.

### 9) Concurrent roots, reload capture and candidate validation

- **Type/location:** Integration; `internal/skillapi/SkillGenerationExecutionIntegrationTest.java`, `DesignatedChildResultIntegrationTest`; unit complete-set tests.
- **Names:** `parallelRootsForwardOnlyTheirOwnTaskResults`; `runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload`; `invalidForwardingCandidateCannotActivate`.
- **Proves:** Two roots using same names/IDs and distinct markers cannot exchange selected outputs. Block old-generation execution, publish a candidate with different target/schema and then release; old root returns old contract/result, new root uses new generation. Nested/parallel children retain same capture, handler receives trusted generation ID. Validate, startup and public prepare reject cycles/missing target consistently; invalid prepare leaves active catalog untouched. Previously captured catalog metadata remains immutable after activation.
- **Fixtures/data:** Generation-keyed terminal schemas/targets/returns and deterministic publication latches; current supported reload API and candidate documents.
- **Mocks:** Existing external HTTP dispatcher, annotation/handler fixtures; real generation capture/publication/access checks.
- **Affected surface/expectation:** Protected captured-generation/application reload/SPI boundaries; no dynamic catalog substitution.

### 10) Canonical, journal and live forwarding provenance

- **Type/location:** Unit/integration; trace recorder tests, `ExecutionJournalProjectorTest`, `ExecutionJournalProjectionContractTest`, `LiveActivityProjectorTest`, new integration trace capture.
- **Names:** `recordsForwardingAtParentCompletionBoundary`; `projectsForwardingWithoutParentModelAttribution`; `failedForwardingHasNoSuccessProvenance`.
- **Proves:** Exactly one RESULT_FORWARDED per successful forwarding mission after final successful join and before mission frame close; exact parent route/plan/task/child identity in nested and reversed-group runs. No synthetic final step, model frame/request/response, usage increment or final-validation record. Normal child model events remain attributed to child. Journal observer and live summary identify forwarding. Existing journal transformation behavior remains exactly as documented; result and canonical prompt/tool payload fields are preserved, including field names that the journal already transforms. Diagnostic bounds are tested as explicit resource limits, not classification.
- **Fixtures/data:** Single/nested chain; failed/cancelled selected/later work; long/String/structured content; bounded scalar provenance.
- **Mocks:** Fixed clock and actual record/projection path; do not infer mode from ordering or missing calls.
- **Affected surface/expectation:** Ephemeral diagnostics and protected journal exception/access boundaries; current-version coherence.

### 11) Java-written trace/REST/SSE corpora and Go projections

- **Type/location:** Integration; Java Console corpus writers under `internal/runtime/trace` and `internal/observability/web`; `loomspan-console/internal/traceanalysis/fixture_corpus_test.go`, observability/applicationclient/live contract tests.
- **Names:** Corpus cases `forwarded-child-result`, `nested-forwarded-child-result`, `forwarding-skill-detail`, `forwarded-result-activity`; matching Go expected projection tests.
- **Proves:** Closed record/activity vocabulary accepts current forwarding event; generic and detailed record/index queries preserve authoritative identity and use accurate presentation. Skill detail includes normalized outputSchema String or null for unspecified, original YAML unchanged. Java/Go exact fields/nulls/typed validation agree. Malformed identity/schema JSON fields reject where typed contracts require them; access/acquisition/problem semantics remain. Committed fixtures match real Java writer bytes with LF and Go expected projection.
- **Fixtures/data:** New corpus NDJSON, expected JSON, REST skill-detail and SSE activity fixtures; update declared case sets/byte hashes when corpus build requires them.
- **Mocks:** Corpus and existing local HTTP servers; no hand-authored replacement for canonical writer evidence.
- **Affected surface/expectation:** Ephemeral formats; atomic current Java–Go boundary, no historical reader/dual schema.

### 12) Exact version rejection and diagnostic transfer

- **Type/location:** Existing focused integration families in Console applicationclient/traceanalysis and Java fixture corpus.
- **Names:** Retain and extend existing missing/mismatched compatibility marker and portable transfer validation cases for corpus containing forwarding.
- **Proves:** Exact current release String required; absent/blank/unequal marker rejects acquisition/import/adapter discovery per current contract. Matching producer/consumer accepts current forwarding corpus; dual development attempts normal complete validation, unknown/malformed current records still fail without fallback. No inferred compatible version, range or legacy reader.
- **Fixtures/data:** Same forwarding trace with controlled required marker mutation; resolved current project String and deliberately different String.
- **Mocks:** Existing local HTTP/import fixture harness.
- **Affected surface/expectation:** Narrow same-version serialized diagnostic transfer and ephemeral adapter; protected exact-version behavior. Retaining beta.8-SNAPSHOT does not assert snapshot immutability.

### 13) Browser and MCP typed output/presentation

- **Type/location:** Go contract unit tests under `internal/mcpadapter`/`internal/browserapi`; Vitest in `web/src/activity`/`web/src/observability`/skill views; a focused Playwright scenario if necessary for selected-task navigation.
- **Names:** `forwardingRecordAndActivityValidateAgainstOutputSchemas`; `rendersForwardingTaskIdentityAndEffectiveSchema`.
- **Proves:** New enum accepted by schema validators and TS types; activity/record details identify source task and mode, not FINAL_RESPONSE. Skill detail renders available schema and unspecified state without promising validation or unwrapping results. Browser/MCP transport preserves canonical identity/nullable metadata; a task link, if added, targets the accepted task of the correct parent plan. Existing source-location variants and pagination remain.
- **Fixtures/data:** Same Java-written forwarding corpus and validated REST/SSE fixtures, single/nested plan IDs and null schema cases.
- **Mocks:** Existing API/transport fixtures; do not add broad redundant UI snapshots or unrelated end-to-end flows.
- **Affected surface/expectation:** Ephemeral Console typed boundary and diagnostic usability; atomic enum/DTO updates.

### 14) Authoring claims and supported surface evidence

- **Type/location:** Underlying unit/integration cases above; architecture test plus documentation review in Step 4/5. No tests that merely assert prose.
- **Proves:** Every routed guide claim corresponds to shipped behavior. Maintain the following evidence map:

| Authoring claim | Required executable evidence |
| --- | --- |
| Exact syntax, mutual exclusion, parent linter/retry applicability | Group 1 catalog validation and accepted illustrative YAML fixtures |
| Unique required direct child, visibility and one retry | Groups 1/4 and current validators |
| Meaningful orchestration, no early return or fallback | Groups 5/6/7/8 |
| Exact model/Java/REST result and no transport unwrapping | Groups 5/8 |
| Chained metadata, missing schema, no inherited evidence/no duplicate validation | Groups 2/3/8/9 |
| N forwarding slots vs N+1 synthesis | Group 6 |
| Runtime task identity/generation/access isolation | Groups 4/7/9 |
| Accurate forwarding trace/activity/journal and current-version constraints | Groups 10/11/12/13 |
| Existing public descriptor extended in place, no new API/SPI/shim | Group 3 and architecture assertions |

- **Fixtures/data/mocks:** Reuse executed examples and named anchors; no live provider or equipment-service worked answer. Documentation review checks consistency, applicability and model-independent limitations.
- **Affected surface/expectation:** Configuration/Application API/ephemeral guidance; source-aligned documentation. Current guide is aligned with existing mandatory synthesis; new behavior updates those claims atomically.

## How to Run

Commands below are planned, not results from this stage. Run from repository root unless noted. Java requires configured JDK 21+ and Maven wrapper prerequisites. Provider tests are offline/local HTTP; no external model credentials are required. Use normal test profile/settings already in the checkout.

1. Red declaration test before Phase 1:
   `./mvnw.cmd -Dtest=YamlSkillCatalogTests#acceptsPlanningOutputFromForUniqueRequiredDirectChild test`
2. Runtime red after metadata support, before Phase 2:
   `./mvnw.cmd -Dtest=StepLoopMissionExecutionEngineTest#forwardsSelectedTaskResultWithoutFinalModelCall test`
3. Configuration/metadata/planning:
   `./mvnw.cmd "-Dtest=YamlSkillCatalogTests,YamlSkillDefinitionTest,SkillGenerationManagerTest,DefaultSkillCatalogTest,PlanningServiceTest,PlanTaskConstraintValidatorTest,PlanStructureValidatorTest" test`
4. Runtime/producer/generation:
   `./mvnw.cmd "-Dtest=StepLoopMissionExecutionEngineTest,MissionContextTest,DesignatedChildResultIntegrationTest,PlannerEvidenceFlowIntegrationTest,ConcurrentGroupedExecutionIntegrationTest,SuccessfulSkillCompletionBoundaryTest,ExecutionCoordinatorOutputSchemaIntegrationTest,SkillGenerationExecutionIntegrationTest" test`
5. Supported surface:
   `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test`
6. Intentionally regenerate affected committed fixture corpus after updating writer cases:
   `./mvnw.cmd "-Dtest=ConsoleTraceFixtureCorpusTest,ConsoleRestFixtureCorpusTest,ConsoleSseFixtureCorpusTest" -Dloomspan.console.fixtures.regenerate=true test`
   Confirm each writer's existing regenerate option and outputs; retain LF and review fixture diffs. Then run the same corpus suite without the regenerate property so a read-only verification detects stale committed evidence:
   `./mvnw.cmd "-Dtest=ConsoleTraceFixtureCorpusTest,ConsoleRestFixtureCorpusTest,ConsoleSseFixtureCorpusTest,ExecutionJournalProjectorTest,ExecutionJournalProjectionContractTest,LiveActivityProjectorTest" test`
7. Full Java suite: `./mvnw.cmd test`.
8. From `loomspan-console`, focused Go boundary suite:
   `go test ./internal/traceanalysis ./internal/observability ./internal/applicationclient ./internal/live ./internal/mcpadapter ./internal/browserapi`
9. From `loomspan-console`, standard required verification: `go test ./...` then `go run ./internal/buildtool verify` (includes repo-standard browser/dependency checks). For focused frontend iteration from `loomspan-console/web`, `npm run typecheck` and `npm test`; use existing locked dependency/bootstrap workflow from buildtool rather than arbitrary installations. Run `npm run test:e2e -- <focused-spec>` if a new navigation path requires an added spec; standard verify must still cover its normal suite.
10. Coordinated package/version consistency from root: `python scripts/loomspan_version.py check`; `python -m unittest discover scripts/tests -v` because skill/documentation packaging references and corpus metadata are touched.

If a race run is warranted by modifications to shared generation/result/lifecycle state, use the existing Console AGENTS Windows recipe: task-local command prepends `C:\msys64\mingw64\bin` to PATH and sets `CGO_ENABLED=1`, then `go test -race ./...` from Console. The plan does not change shared-state ownership and normal Go tests/build verify are mandatory. Record a compiler/environment limitation accurately rather than claim a race check passed. Do not broaden testing merely to repeat already passing checks without a new change or concern.

## Exit Criteria

- [x] First declaration red failure is recorded on current code; runtime no-synthesis red failure is recorded before completion implementation, then both pass after implementation.
- [x] Strict declarations, graph/cycle/missing-schema behavior, ordinary synthesis, task uniqueness/visibility/retry and source-associated diagnostics pass.
- [x] All three child types, child validation/correction, forwarding chain and no-schema behavior pass through supported SkillTemplate with exact result Strings and no parent final request/validator.
- [x] N-slot forwarding/N+1 synthesis, all-work joins, failure/cancellation/limit handling, late-write fencing, concurrent roots and generation publication are covered and pass.
- [x] Public architecture/signature tests pass on the deliberate five-component descriptor shape; old constructor and unintended new APIs/SPIs/internal leaks are absent.
- [x] Canonical/observer/live provenance has exact selected identity and timing, no phantom model/provider accounting; existing journal exception remains limited and result/canonical fidelity is retained.
- [x] Java-written current trace/REST/SSE corpus matches committed LF fixtures; Go projection/outward schemas and TypeScript presentation agree. Exact marker/access/acquisition/error semantics pass; no compatibility fallback/legacy format is retained.
- [x] Full Java suite, full Go suite, Console buildtool verify and coordinated package/version checks pass or any genuine environment blocker is explicitly reported with residual risk before completion.
- [x] Guidance and README coverage/routing use implemented behavior, named test anchors and general model/Java/REST examples; there are no unconditional synthesis claims for forwarding or unsupported accuracy/validation guarantees.
- [x] No implementation-related uncertainty or unverified correctness criterion is shifted into an optional developer check. No optional nonautomatable check is required for this deterministic feature; a live application observation may be offered later only as nonblocking supplemental feedback.

## References

Ticket, research and implementation plan listed above; repository/Console AGENTS; feature design lens; Steps 2/3 and shared automation/docs protocols; repository `loomspan-docs` router and relevant skill-authoring/Java API references. This artifact carries the verification design, not claimed execution results.


## Step 4 Verification Receipt

All exit criteria above are complete. Exact acceptance-to-test anchors and actual red/fix/full outcomes are recorded in the implementation plan's Step 4 Execution Evidence. Final required commands passed: `./mvnw.cmd test` (1,317 tests, zero failures/errors, 2 skipped); Java corpus regeneration (19 tests) and read-only corpus/journal/live projection (49 tests); `go test ./...`; `go run ./internal/buildtool verify` (frontend typecheck, 522 tests/coverage, build and corpus checks); `python scripts/loomspan_version.py check`; `python -m unittest discover scripts/tests -v` (13 tests); `git diff --check`. The unchanged invalid-action exhaustion test transiently timed out in one full run, passed isolated (1/1), and then passed in the unchanged full rerun; no invariant was weakened. Conditional navigation/race checks were not required by the final change and were not run. Full-profile completion awaits fresh Step 5 review.
