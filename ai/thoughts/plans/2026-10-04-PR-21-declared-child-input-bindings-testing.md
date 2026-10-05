# PR 21 Declared Child Input Bindings Testing Plan

## Change Summary

Verify author-declared parent-input/direct-result bindings, projected model
contracts, exact assembled receiving values, declaration-driven dependencies,
mission isolation and attributable canonical evidence. Profile is confirmed
`full`. The developer welcomes destructive internal changes and prohibits
compatibility shims; tests must track the coherent new internal design.

Governing plan:
`ai/thoughts/plans/2026-10-04-PR-21-declared-child-input-bindings.md`.
Research and original ticket are referenced there. No unresolved design question
remains. This stage writes plans only; no implementation or test command was run.

## Impacted Areas

- Manifest tree parsing, normalized allowed-child records and complete generation.
- Internal recursive input projection, validation, rendering and reserved paths.
- Planner descriptions, structural/count/dependency validation and one correction.
- Mission input ownership, accepted result decoding, pre-unit snapshots, assembly,
  common router and model/Java/REST dispatch.
- Joined execution groups, exact assignment correction, authorization, invocation
  limits, captured generations and PR 20 forwarding.
- Tool-frame provenance, raw model evidence, canonical reader and derived journal.
- README and routed skill-authoring guidance, complete fixture-backed example.

## Risk Assessment

Primary risks are weakening required/closed/typed-additional constraints when
projecting nested fields, permitting overrides under open/generic parents,
coercing bound numeric/date values, aliasing mutable nested containers, collapsing
null into absence, selecting wrong/rejected/failed producer results, and reading
same-unit results because physical dispatch happened sequentially.

Protected paths: closed application API, sole `RestSkillHandler` SPI, ordinary
unbound input normalization, task-count and join semantics, authentication,
limits, generation capture, existing file/attachment references and raw
`output_from` returns. These are ticket/policy/documented contracts, not inferred
from internal constructor exposure. Internal records and wiring may be replaced
atomically; do not retain old/new constructor behavior merely to keep tests.

Trace additions stay inside existing extensible tool-frame payloads. Test
current-version writer/reader/projector fidelity, not historical trace migration.
No closed NDJSON envelope, REST/SSE/problem/acquisition or compatibility-marker
change is planned; no Go production change is required for this design.

## Existing Test Coverage

- `YamlSkillCatalogTests`, `SkillGenerationManagerTest`: strict declaration and
  complete-generation resolution; currently no binding support.
- `SkillInputContractResolverTest`, `SkillInputValidatorTest`,
  `SkillInputPromptRendererTest`: recursive schema/validation/guidance.
- `PlanStructureValidatorTest`, `PlanTaskConstraintValidatorTest`,
  `PlanningServiceTest`: earlier-unit topology, exact counts, composed correction.
- `StepActionValidatorTest`, `StepPromptBuilderTest`,
  `StepLoopMissionExecutionEngineTest`: assigned action checks, complete evidence,
  correction, pre-unit snapshot and full joins.
- `ConcurrentGroupedExecutionIntegrationTest`,
  `PlannerEvidenceFlowIntegrationTest`, `DesignatedChildResultIntegrationTest`:
  real offline dispatch, nested/concurrent results, raw forwarding and reload.
- `CapabilityExecutionRouterTest`, authentication/authorization suites and usage
  tests: common access/generation/input/limit boundaries.
- `ExecutionTraceContractTest`, `NdjsonTraceRecordWriterTest`,
  `NdjsonExecutionTraceReaderTest`, `ConsoleTraceFixtureCorpusTest`,
  `ExecutionJournalProjectorTest`: canonical/current diagnostic coherence.
- `LoomspanPublicSurfaceArchitectureTest`: closed allowlist authority.

Gaps are all binding-specific declaration, projection, assembly, dependency and
provenance behaviors. Existing tests supply fixtures/fakes and protected paths.

## Bug Reproduction / Failing Test First

Add a catalog-level test to `YamlSkillCatalogTests` named
`acceptsParentInputBindingsWithObjectPointersForPlanningChildren`.

- Type: unit/catalog integration, using the existing in-memory YAML fixture setup.
- Arrange a valid planner with parent `/requestId`, child `/caseId`, and
  `input_bindings: {/caseId: {from: input, path: /requestId}}`.
- Act: load/validate the complete catalog.
- Assert: declaration is accepted and the normalized binding retains exact source
  kind/path/destination; the full receiving schema is unchanged.
- Expected pre-fix failure: `input_bindings` is rejected as an unknown allowed
  child entry field. Run this before enabling parsing and record the real failure.

Then add a behavioral public integration test
`deliversLargeBoundEvidenceFromEmptyModelArguments` as runtime proof. Pre-runtime
assembly it fails on missing required child input or absent effective projection.
The first minimal catalog red test is sufficient for the recorded red-first gate;
do not invent claims that a compilation failure proves transfer mechanics.

## Tests to Add/Update

### 1) Declaration grammar and complete-generation validation

- Type: unit; locations `YamlSkillCatalogTests`, `SkillGenerationManagerTest`,
  and a focused internal pointer/binding contract test as appropriate.
- Proves both source kinds; renamed fields; source root `""`; `~0`/`~1` escaping;
  literal dotted/numeric object keys; selected arrays; exact child name matching.
- Negative matrix: unknown/null fields, wrong `from`, missing/extraneous skill,
  malformed pointer/escape, empty destination, duplicate raw YAML keys, duplicate
  and overlapping destination pointers, nonplanning applicability, unknown/self/
  cyclic producer graph, impossible count, known nonobject/closed destination
  ancestors and known absent source fields.
- Unknown output/open/generic schema is accepted where runtime must decide;
  do not assert a fabricated producer output schema.
- Fixtures: existing memory YAML resources, fixed Java/REST declarations,
  both valid and invalid complete sets; no model mock necessary.
- Surface: configuration/manifest and internal implementation. Opt-in addition;
  ordinary declarations remain protected; internal helpers update atomically.

### 2) Effective argument contracts and actual corrective requests

- Type: unit plus engine integration; locations new focused projection test,
  `SkillInputPromptRendererTest`, `StepActionValidatorTest`,
  `StepPromptBuilderTest`, `StepLoopMissionExecutionEngineTest`.
- Proves `{}` for all-bound closed objects; partially supplied ancestor objects;
  required unbound siblings; optional reasoning/open extension; typed additional
  properties; nested arrays/items in unbound branches; enums/formats/descriptions;
  full receiver schema unchanged. Preserve every constraint supported by the
  resolved contract vocabulary; do not silently flatten object/array structure.
- Test originally optional ancestor made present by a binding: unbound required
  siblings are required. Test required ancestor with no remaining model-required
  descendants: ancestor may be omitted. Optional unbound values remain optional.
- Serialized model schema structurally forbids bound fields even under open and
  generic objects. Explicit action checks reject bound exact/equal/null values,
  descendant assignments and scalar/null/array ancestors, while accepting ancestor
  containers holding only unbound siblings. Generic skip cannot bypass this guard.
- Capture initial planning description, compact/verbose assigned requests and an
  invalid-override corrective request with deterministic fake responses. Assert
  every contract uses the same projected rules and correction never requests the
  model to reconstruct bound evidence. Invalid action causes zero child calls;
  corrected sibling-only action causes exactly one assigned child call.
- Surface: manifest/model argument behavior and internal validation, protected
  unbound contract behavior and current raw correction evidence.

### 3) Exact assembly, decoding and mutation isolation

- Type: unit; locations focused assembler/decoder tests, `MissionContextTest`,
  `SkillInputValidatorTest`, `CapabilityExecutionRouterTest`.
- Fixtures: source tree with absent optional key, explicit null at an allowed
  unconstrained leaf, nested arrays/maps, JSON-looking ordinary strings, empty
  and whitespace strings, large monetary integer > 2^53, BigInteger beyond long,
  precise decimal, valid noncanonical date spelling and coercible numeric/Boolean
  strings. Include generic/open objects and arrays without item schemas.
- Assert values and optional presence recursively, not only JSON shape. Missing
  optional-target binding source still fails; allowed null transfers; typed null
  fails receiver unchanged. Bound numeric/Boolean strings fail typed contracts
  instead of coercing; bound accepted dates retain spelling; unbound fields retain
  existing normalization. Wrong types, enums, closed fields and required siblings
  fail complete receiving validation before child invocation.
- Decode full JSON object/array/scalar/null and Java JSON-quoted String once.
  Preserve lossless numeric values. Plain text/malformed JSON stays raw text;
  root selection can use it as a String, nested field selection fails. Nested
  JSON-looking strings remain Strings. Never repair accepted content. Raw retained
  result and `output_from` text compare byte-for-byte with original String.
- Attempt mutation of retained parent/results and mutate or attempt mutation of
  a consumer's nested map/list. Assert original sources and another consumer are
  unchanged. Assert two consumer values are detached, not shared mutable aliases.
- Mocks: child invoker counters and existing router coordinator fake; no provider.
- Surface: manifest receiving behavior and internal state. Protected unbound
  coercion/reference behavior; exact bound behavior is the new contract.

### 4) Unique producer selection and dependency correction

- Type: unit/planning integration; locations focused binding dependency validator,
  `PlanningServiceTest`, `StepLoopMissionExecutionEngineTest`.
- Fixtures: consumer plus one producer, zero/two producer tasks, omitted direct
  dependsOn, forward order, same parallel group, unrelated group members,
  transitive-only edge, optional absent consumer and ambiguous producer results.
- Assert consumer-present plans require exactly one direct producer, explicit
  producer task edge and earlier unit; consumer-absent optional work does not add
  unconditional producer requirements. Same-unit use fails for true/false
  concurrency. Exact names/task IDs remain authoritative.
- Capture one corrected planning attempt composed with count/evidence/structure
  feedback and exhausted failure with no `PLAN_CREATED`/stored plan/dispatch.
  Declaration cycles fail generation validation. Runtime unavailable/failed/
  duplicate result preflight fails closed even with a manually constructed plan.
- Preserve independent groups. Use latches/barriers to prove overlap and full
  join without sleeps or inferred timing. A later consumer begins after every
  producer-group member joins, and sees accepted results only.
- Surface: authored dependencies and internal lifecycle; protected joined-unit
  concurrency and one-total-correction behavior.

### 5) Public model/Java/REST transfer, ownership and limits

- Type: deterministic offline integration; location new
  `src/test/java/ai/loomspan/integration/DeclaredChildInputBindingsIntegrationTest.java`.
- Use existing `SkillTemplate` harness and local fake model/HTTP fixtures,
  registered Java methods and REST handler. Follow designated-result and planner
  evidence integration patterns; no real provider credentials or paid calls.
- Exercise model/Java/REST as producers and consumers, including Java quoted
  String, arbitrary REST JSON/plain text and no declared producer output schema.
  Assert actual consumer typed values and zero calls on invalid assembled input.
- Representative large case: parent records/assessment and producer result with
  many nested fields; fake assigned action arguments `{}` or only
  `/context/candidateReasoning`. Parse captured raw action to assert bound payload
  absent. Compare actual delivered tree exactly with selected sources.
- Concurrent and nested parents reuse identical skill names and different input
  markers. Assert no root/ancestor/session-global leakage. Reject a model foreign
  case/result identifier override. A consumer result correction candidate cannot
  replace the producer's accepted result. Failed/late work supplies no source.
- Reload while producer group is blocked, then release it. Assert existing root
  retains original binding/receiver definitions and generation identity while
  subsequent root uses new generation.
- Preserve caller identity/child authorization; denied child does not dispatch
  even with available bound data. Preserve tool invocation/provider/depth budgets
  via focused existing suites and a bound-call counter assertion.
- Combine bindings with `output_from`: all accepted tasks complete, exact consumer
  returned String forwards, no parent final synthesis or duplicate output check.
- Surface: Application API/SPI, manifests and isolation. No new API/SPI support.

### 6) Current canonical evidence and authoring example

- Type: engine/trace integration; locations binding integration test and existing
  `ExecutionTraceContractTest`, `NdjsonExecutionTraceReaderTest`,
  `ExecutionJournalProjectionContractTest`/`ConsoleTraceFixtureCorpusTest` if a
  representative fixture is generated.
- Assert original raw model action arguments remain inspectable; tool frame
  `arguments` is complete delivered input; `inputBindings` identifies destination,
  source kind/path, exact parent frame and source task/skill, with linked consumer.
  Original accepted producer String stays available. Provenance omits duplicate
  bound payload copies. Failure records distinguish source versus override versus
  assembled contract failure before dispatch. Existing caller/generation evidence
  remains linked through invocation.
- Round-trip current canonical writer/reader and journal projection. Preserve
  existing explicit resource limits and existing journal redaction tests; add no
  sensitivity classifier or canonical rewriting tests.
- Complete authoring example should reuse tested fixture shapes and include full
  parent/producer/receiver schemas, both sources, renamed paths, unbound reasoning,
  unique earlier producers and forwarding. All exact guide claims map to focused
  test/source anchors. Update coverage routing with actual implemented limits.
- Surface: ephemeral diagnostics and documented manifest behavior; current-run
  coherence. No new top-level trace contract or compatibility marker.

## How to Run

Use Java 21+ and repository wrapper from repository root. Maven installation
`C:\hamdev\maven\bin\mvn.cmd` is an acceptable environment fallback if the wrapper
cannot run. Do not silently waive a command after wrapper failure. Commands use
PowerShell quoting for comma-separated test filters. Dependencies may use Maven's
normal repository; the tests themselves use local fakes and no paid providers.

Red test before declaration implementation:

```powershell
.\mvnw.cmd '-Dtest=YamlSkillCatalogTests#acceptsParentInputBindingsWithObjectPointersForPlanningChildren' test
```

Focused verification after implementation (include new classes in these filters
under their actual names; update this plan atomically if names differ):

```powershell
.\mvnw.cmd '-Dtest=YamlSkillCatalogTests,SkillGenerationManagerTest,SkillInputContractResolverTest,SkillInputValidatorTest,SkillInputPromptRendererTest,ChildInputBindingTest,ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,AcceptedResultDecoderTest' test
.\mvnw.cmd '-Dtest=PlanStructureValidatorTest,PlanTaskConstraintValidatorTest,PlanInputBindingDependencyValidatorTest,PlanningServiceTest,StepActionValidatorTest,StepPromptBuilderTest,StepLoopMissionExecutionEngineTest,MissionContextTest,CapabilityExecutionRouterTest' test
.\mvnw.cmd '-Dtest=DeclaredChildInputBindingsIntegrationTest,BindingIsolationIntegrationTest,DeclaredChildInputBindingsTraceTest,DesignatedChildResultIntegrationTest,PlannerEvidenceFlowIntegrationTest,ConcurrentGroupedExecutionIntegrationTest,JavaSkillAuthenticationScopeIntegrationTests,JavaSkillAuthorizationIntegrationTests,SkillGenerationExecutionIntegrationTest,SessionUsageServiceTest' test
.\mvnw.cmd '-Dtest=LoomspanPublicSurfaceArchitectureTest,ExecutionTraceContractTest,NdjsonTraceRecordWriterTest,NdjsonExecutionTraceReaderTest,ConsoleTraceFixtureCorpusTest,ExecutionJournalProjectionContractTest,ExecutionJournalProjectorTest' test
.\mvnw.cmd test
git diff --check
```

Do not enable external-provider profiles or refresh frozen sibling suite replay
fixtures. Full Maven test includes the mandatory public architecture check; the
explicit targeted command makes it reviewable independently. There is no separate
repository formatter configured in `pom.xml`; `git diff --check` verifies whitespace.

## Exit Criteria

- [x] Minimal catalog red test was actually observed failing pre-fix, with exact
  command/result recorded by implementation; it passes post-fix.
- [x] All required focused and full offline suites pass, with actual commands
  and failures/retries reported honestly. Changed test names match commands.
- [x] Both source kinds, pointer grammar, duplicate/overlap declaration checks,
  unknown producers/cycles and runtime unknown-schema values have coverage.
- [x] Structural projection, preserved constraints, bottom-up requiredness,
  generic/open exclusions and initial/corrective argument behavior are proven.
- [x] Exact payload equality, absence/null, large integers/decimals, nonrecursive
  decoding, strict bound validation and deep isolation are proven before dispatch.
- [x] Unique direct accepted sources, explicit earlier-unit edges and independent
  producer overlap/full joins pass in both concurrency modes.
- [x] Model/Java/REST, nested/concurrent parents, reload, authorization and limits
  retain their current protected behavior.
- [x] `output_from`, ordinary unbound input coercion and existing references pass;
  no superseded internal shim/constructor path was introduced.
- [x] Raw/effective/provenance evidence and current canonical reader/projector
  coherence pass; public architecture allowlist remains closed.
- [x] Updated guides and complete example describe precisely tested behavior;
  coverage/routing tables change, and no live reliability claim is made.
- [x] No paid calls, sibling baseline mutation, replay refresh or release action.

Optional developer checks: none required. A later live-model comparison may be
separately authorized; it is not an acceptance gate or evidence supplied here.

## Implementation Verification Receipt (2026-10-05)

All required offline classes were exercised by the focused filters below and the
full suite. Planned adjacent focused filters were consolidated into larger filters;
no required class was waived. Final production passed the full suite; the last
focused run also covers the two subsequent test-only additions.

| Command | Actual result |
| --- | --- |
| `.\mvnw.cmd '-Dtest=YamlSkillCatalogTests#acceptsParentInputBindingsWithObjectPointersForPlanningChildren' test` | Expected pre-fix FAIL: one assertion, unknown `input_bindings`; post-fix included in passing catalog runs |
| `.\mvnw.cmd '-Dtest=DeclaredChildInputBindingsIntegrationTest,DesignatedChildResultIntegrationTest,BoundCapabilityTest,DefaultCapabilityInvokerTest' test` | Initial FAIL: 23 tests, 9 fixture failures and 5 invoker errors; next attempt 4 duplicate-declaration fixture failures. All classes pass subsequent full/final runs. |
| `.\mvnw.cmd '-Dtest=DeclaredChildInputBindingsIntegrationTest,YamlSkillCatalogTests,SkillGenerationManagerTest,SkillInputContractResolverTest,SkillInputValidatorTest,SkillInputPromptRendererTest,ChildInputBindingTest,ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,AcceptedResultDecoderTest,PlanStructureValidatorTest,PlanTaskConstraintValidatorTest,PlanInputBindingDependencyValidatorTest,PlanningServiceTest,StepActionValidatorTest,StepPromptBuilderTest,StepLoopMissionExecutionEngineTest,MissionContextTest,CapabilityExecutionRouterTest' test` | Initial FAIL: 446 tests, 1 assertion failure and 1 duplicate-key parser error; both repaired. Full/final runs pass all listed classes. |
| `.\mvnw.cmd '-Dtest=YamlSkillCatalogTests,ChildInputBindingTest,ChildInputBindingProjectionTest,PlanInputBindingDependencyValidatorTest,SkillGenerationManagerTest' test` | PASS: initial 180 tests, followed by expanded 243-test run below. |
| `.\mvnw.cmd '-DskipTests' test` | PASS: production and all test sources compiled; intentionally did not execute tests |
| `C:\hamdev\maven\bin\mvn.cmd '-Dtest=SkillInputValidatorTest,AcceptedResultDecoderTest,ChildInputBindingAssemblerTest' test` | PASS: initial 22 tests; later enhancements pass full/final focused |
| `.\mvnw.cmd '-Dtest=YamlSkillCatalogTests,ChildInputBindingTest,ChildInputBindingProjectionTest,PlanInputBindingDependencyValidatorTest,SkillGenerationManagerTest,PlanningServiceTest,DeclaredChildInputBindingsTraceTest' test` | PASS: 243 tests; `target/pr21-declarations-tests.log` |
| `C:\hamdev\maven\bin\mvn.cmd '-Dtest=BindingIsolationIntegrationTest,StepLoopMissionExecutionEngineTest#boundOverrideCorrectionKeepsProjectedGuidanceAndInvokesOnlyAcceptedSiblingAction,CapabilityExecutionRouterTest' test` | PASS: 14 tests, including all 5 public isolation/failure/quota tests |
| `.\mvnw.cmd '-Dtest=DeclaredChildInputBindingsIntegrationTest,BindingIsolationIntegrationTest,DeclaredChildInputBindingsTraceTest,DesignatedChildResultIntegrationTest,PlannerEvidenceFlowIntegrationTest,ConcurrentGroupedExecutionIntegrationTest,JavaSkillAuthenticationScopeIntegrationTests,JavaSkillAuthorizationIntegrationTests,SkillGenerationExecutionIntegrationTest,SessionUsageServiceTest,LoomspanPublicSurfaceArchitectureTest,ExecutionTraceContractTest,NdjsonTraceRecordWriterTest,NdjsonExecutionTraceReaderTest,ConsoleTraceFixtureCorpusTest,ExecutionJournalProjectionContractTest,ExecutionJournalProjectorTest' test` | Initial FAIL: 103/104 behavioral checks passed, architecture internal reasons map stale; corrected map verified by full and final focused runs |
| `.\mvnw.cmd test` | PASS: 1374 total, 0 failures/errors, 2 existing optional PR18 diagnostic skips; `target/pr21-full-tests.log` |
| `.\mvnw.cmd '-Dtest=DeclaredChildInputBindingsIntegrationTest,YamlSkillCatalogTests,SkillGenerationManagerTest,SkillInputContractResolverTest,SkillInputValidatorTest,SkillInputPromptRendererTest,ChildInputBindingTest,ChildInputBindingProjectionTest,ChildInputBindingAssemblerTest,AcceptedResultDecoderTest,PlanStructureValidatorTest,PlanTaskConstraintValidatorTest,PlanInputBindingDependencyValidatorTest,PlanningServiceTest,StepActionValidatorTest,StepPromptBuilderTest,StepLoopMissionExecutionEngineTest,MissionContextTest,CapabilityExecutionRouterTest,LoomspanPublicSurfaceArchitectureTest' test` | Final PASS: 458 tests, 0 failures/errors/skips; `target/pr21-final-focused.log` |
| `git diff --check` | PASS after trimming one new blank EOF line; ordinary CRLF conversion notices are informational |

Earlier implementation-only runs failed fixture/test issues as documented in the
implementation plan: missing YAML array item schemas, duplicate Java/YAML names,
duplicate-key parser attribution, misplaced correction evidence assertion and
Windows guide regex line breaks. All are resolved. The original integrated
446-test filter's remaining classes and corrected cases passed full/final focused.
No required verification remains unrun. Existing optional `pr18.diagnostic`
replay diagnostics were NOT RUN; no paid provider, replay refresh, sibling suite,
publication or release action occurred. Independent Step 5 review is next.
