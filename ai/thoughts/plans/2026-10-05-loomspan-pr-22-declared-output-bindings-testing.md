# PR22 Declared Output Bindings Testing Plan

## Change Summary

Top-level `output_bindings` assigns exact current-input/direct-child values to output object paths. A projected model contract owns only remaining fields; complete assembly validates the original contract. Fully bound outputs skip final synthesis after all required/accepted work succeeds. Both runtime paths and current Java/Console diagnostics must agree.

Approved Full 5-Step Pipeline. Developer authorizes destructive development changes and **no compatibility shims**. Use deterministic providers only; no paid runs, sidecar-suite changes, evaluator changes or business-reasoning claims.

## Impacted Areas

- Manifest/raw declaration validation, `YamlSkillDefinition`, complete-generation output metadata resolution.
- Shared PR21 binding descriptors/pointers/accepted-result decoder/copy and insertion logic.
- Native output projection, source resolution, complete assembly and output schema validator.
- Ordinary advisor/model engine, planning validation/prompt building and step-loop completion.
- Immutable mission input, task-keyed results, captured generations, full joins and lifecycle fencing.
- Java trace/state/journal and current Console closed enums, live/browser/MCP readers/DTOs/fixtures.
- Authoring output/input/planning/diagnostic references and README routing/coverage.

## Risk Assessment

- Complete schemas leaking into mixed-output correction can force copying or allow override.
- Optional ancestors can conceal required unbound siblings; optional unbound fields and open objects must not be treated as fully bound.
- Native nullable and case-insensitive output matching can be lost by input-schema conversion or case alias overrides.
- Ambiguous producer selection, unfinished unrelated work, concurrent branches, nested missions or reload can select incorrect/stale values.
- Source/bound validation failures must never enter model fallback/replay, while model contribution defects remain correctable.
- Advisor order must make evidence/linter validation inspect complete assembly without modifying model ownership in retries.
- New authoritative trace vocabulary must reach strict current Go readers and schemas, with exact provenance and existing payload limits.
- **Protected paths:** explicit ticket guarantees for ordinary no-bindings output, exact `output_from`, public invocation and sole REST SPI, authorization and limits. Their existing tests remain.
- **Intentionally obsolete internals:** old shared-helper names/constructors/fixtures can be removed atomically; no tests demand compatibility overloads or both old/new diagnostic dialects. Closed supported API allowlist does not expand.

## Existing Test Coverage

`YamlSkillCatalogTests`, `SkillGenerationManagerTest`, `ChildInputBindingProjectionTest`, `ChildInputBindingAssemblerTest`, `AcceptedResultDecoderTest`, `PlanInputBindingDependencyValidatorTest`, `OutputSchemaValidatorTest`, `OutputSchemaPromptAugmentorTest`, `OutputSchemaCallAdvisorTest`, `PlanningServiceTest`, `StepLoopMissionExecutionEngineTest`, `DeclaredChildInputBindingsIntegrationTest`, `BindingIsolationIntegrationTest`, `DesignatedChildResultIntegrationTest`, `DeclaredChildInputBindingsTraceTest`, `SkillGenerationExecutionIntegrationTest`, and `LoomspanPublicSurfaceArchitectureTest` establish prerequisite behavior. New output binding declarations, projection, output-source ownership and assembly diagnostics are absent.

Research ran the focused prerequisite/architecture baseline successfully. This plan itself has not run implementation tests. Existing guide/source comparison is **aligned**; new assembly claims require new executable evidence.

## Bug Reproduction / Failing Test First

- **Type:** integration (feature acceptance), preceded by a minimal catalog unit test.
- **Location:** `src/test/java/ai/loomspan/internal/skill/YamlSkillCatalogTests.java` and new `src/test/java/ai/loomspan/integration/DeclaredOutputBindingsIntegrationTest.java`.
- **Minimal red test:** `acceptsInputOnlyOutputBindingsWithDeclaredObjectContract` loads the ticket's mixed example and asserts a normalized binding declaration. Current strict YAML mapping rejects unknown `output_bindings`.
- **Behavioral red test:** `assemblesTwoDirectChildrenAndInputWithoutFinalModelRequest` plans two required unique independent producers plus unrelated accepted work. Inputs include a case ID; producers return exact large-number/nested-string/ordered-array evidence. Assert assembled object and exact values, all tasks completed, and no final synthesis request.
- **Expected pre-fix failure:** declaration fails because output bindings are absent; after declaration support alone, assembly/provider-count assertions fail. Capture actual red evidence in the implementation report before completing production integration. Do not describe compile failure for nonexistent methods as behavioral proof; write the first red test through existing public/manifest entry points.

## Tests to Add/Update

### 1) Strict declarations and captured-generation checks

- **Type/location:** unit; existing catalog and generation tests, new internal output-binding declaration tests.
- **Proves:** identical descriptor grammar to PR21; current input permitted without planning; child source requires explicit planning/allowed direct producer; required object output contract; nonempty map; explicit null invalid; `output_from` conflict names both fields.
- **Data:** valid destination maps, empty/root destinations, escaped slash/tilde/empty keys, malformed escape, descriptor unknown/null fields, duplicate raw YAML keys, overlap, case-alias destination collision, array traversal, unknown/disallowed/self producer, `max_tasks: 0`, effective `min_tasks > 1`, input-only schema mismatch, selected declared child subtree/forwarding-chain metadata, enum/nullability and scalar/array/object mismatch. Unknown Java/REST/open shapes are accepted for runtime validation. Integer-to-number is compatible; no inferred schemas.
- **Mocks:** ordinary in-memory manifest fixtures; existing complete candidate generation builders.
- **Surface/expectation:** configuration/manifest, intentional internal update; existing forwarding declarations remain protected. `max_tasks > 1` remains declaration-valid if exactly one task is possible, with plan-time enforcement.

### 2) Projected ownership and full-bound classification

- **Type/location:** unit; new `src/test/java/ai/loomspan/internal/outputschema/OutputBindingProjectionTest.java`; augment prompt/schema tests.
- **Proves:** owned fields removed from required contributions and prohibited in initial/corrective guidance; optional ancestors containing required unbound siblings become contribution-required; binding-owned ancestor blocks model descendants; source/evidence context is retained.
- **Data:** root required/optional bound fields; entirely bound closed output; optional unbound field; nested required sibling below optional parent; nullable/blocking ancestor; open root; reachable unbound open object; fully bound open subtree; enum/items/description/format/evidence metadata. Full-bound true only for closed empty residual field space; open root or unbound optional field must keep a model call.
- **Mocks:** none.
- **Surface/expectation:** configuration and internal output ownership; current native nullable/property validation retained.

### 3) Exact values, detached assembly and protection

- **Type/location:** unit; new `src/test/java/ai/loomspan/internal/outputschema/OutputBindingAssemblerTest.java`; reuse decoder and PR21 assembler tests.
- **Proves:** selected whole sources/subtrees retain BigInteger/BigDecimal value, strings (including JSON-looking text), Boolean, explicit nullable null, arrays/order and escaped keys; no coercion/date rewriting. Each binding resolves even for optional destination; absent source differs from null.
- **Data:** decimal beyond double precision; integer beyond long; plain/malformed JSON result; JSON-quoted Java string; nested JSON-looking string; absent property; nullable and nonnullable destinations; object-only numeric keys and rejected array indexing; source result list missing/duplicate; malformed/nonobject contribution; equal/null/ancestor/case-alias override; valid unbound siblings. Mutate one returned copy and assert retained source/other assembled result unchanged.
- **Mocks:** immutable mission/result fixtures with exact task and owner IDs.
- **Surface/expectation:** internal execution and output contracts; source failures are terminal, model conflict classified separately, complete invalid assembly cannot publish success.

### 4) Producer planning and complete-work execution

- **Type/location:** unit; `PlanningServiceTest`, step engine tests and output producer validator tests.
- **Proves:** every output producer has exactly one accepted task, even if its allowed child entry is optional; zero/two producer tasks consume existing combined correction budget; no output-induced false edge/order; multiple mappings reuse one selected task. All accepted required and optional work completes before assembly; failure/cutoff/stale result prevents publication.
- **Data:** optional producer entries with min=0/max=2; valid exactly-one task; missing/duplicate producer; independent grouped producers; later unrelated task; failed unrelated task; wrong retained skill/task ID, plan changed after acceptance, owner mismatch, no retained result. Existing input-bound consumer still needs producer dependency and earlier execution unit.
- **Mocks:** deterministic engine/model fakes and latch-controlled tasks; use latches/barriers rather than sleeps for joins.
- **Surface/expectation:** configuration, planning/lifecycle and internal ownership; existing authorization/invocation limits and no-bindings/forwarding tests remain.

### 5) Ordinary full/mixed output and correction through SkillTemplate

- **Type/location:** integration; new `DeclaredOutputBindingsIntegrationTest`; unit augment `OutputSchemaCallAdvisorTest` and `OutputSchemaPromptAugmentorTest`.
- **Proves:** fully bound input-only skill calls no provider; mixed ticket example produces only summary while binding exact assessment. Initial and every corrective request have projected fields/examples, retain needed input evidence, and never request reconstruction. Wrong unbound type/missing sibling/invalid JSON/override correct within existing budget; repeated invalid contribution exhausts the budget. Bound source failure calls no fallback; source values remain constant.
- **Data:** closed output full-bound case; mixed assessment/summary; one model override followed by valid summary; nullable null override; wrong unbound field; nested optional ancestor/required sibling; open root contribution case; source contract mismatch. Evidence and regex-linter cases ensure checks see complete assembled output and cannot drive bound-value reconstruction.
- **Mocks:** local MockWebServer scripted provider queue; assert exact request count/content and original response text in diagnostics.
- **Surface/expectation:** public invocation with unchanged API; configuration/diagnostic semantics; no paid provider requests.

### 6) Planning assembly, direct producer varieties and output recovery

- **Type/location:** integration; new output binding integration class and existing step-loop tests.
- **Proves:** two child results plus current input assemble with no final call; mixed assembly has one final contribution phase; model/Java/REST whole and selected-subtree sources work where actual result shape permits. N tasks fit N slots fully bound; mixed output requires N+1. Correction changes only model fields and never invokes children again. Exact `output_from` and ordinary synthesis remain unchanged.
- **Data:** ticket three-field full assembly; model producer with declared shape, Java producer returning a Jackson-quoted string/object, fixed REST handler returning JSON/plain text; null versus absence; accepted unrelated task after producers; full output evidence/linter policies. Whole plain text binds to string, selected nested path in plain text fails.
- **Mocks:** local scripted providers, fixed Java bean/REST handler, existing invocation counters.
- **Surface/expectation:** public invocation and supported REST SPI; manifest semantics; no invented producer schema or new extension point.

### 7) Isolation, reload, authorization and lifecycle

- **Type/location:** integration; extend `BindingIsolationIntegrationTest`, `SkillGenerationExecutionIntegrationTest` or dedicated output integration tests; reuse authorization fixtures.
- **Proves:** concurrent roots/nested parents with same producer name select their own input/task/results; parent cannot select a grandchild; no cross-parent source search. Reload while tasks or correction are blocked keeps captured binding/schema/model generation; next invocation uses new generation. Unauthorized producers fail, binding grants no access; canceled/failed work cannot append successful assembly.
- **Data:** distinguishable source values per root; nested same-name direct producers; bound schema/path changed by reload; mismatching task/result IDs; roles denying producer. Assert every retained selected text remains unchanged.
- **Mocks:** barriers/latches, local provider queue, deterministic auth and reload service; avoid timing-sensitive sleeps.
- **Surface/expectation:** authorization/isolation/lifecycle and current generation correctness; deliberately protected safeguards.

### 8) Authoritative assembly diagnostics and current Console coherence

- **Type/location:** Java trace integration (`src/test/java/ai/loomspan/internal/runtime/trace/DeclaredOutputBindingsTraceTest.java` or existing trace test package); Console `internal/traceanalysis`, `internal/live`, `internal/browserapi`, `internal/mcpadapter` focused tests and current protocol fixtures.
- **Proves:** one successful owner-frame `RESULT_ASSEMBLED` contains exact provenance, `modelContributionRequired`, optional plan identity and assembled payload through normal descriptors; original model/child evidence unchanged; no duplicate selected values in provenance. Terminal failure emits no assembly success. Go readers/projectors/filter enums/browser/MCP/live mappings expose authoritative facts; exact release-marker rejection and development validation remain unchanged.
- **Data:** full-bound and mixed trace fixtures; escaped destinations, input source with no task ID and multiple child sources with exact IDs; nested owner; large payload/chunks/inline omitted with exact byte read; missing/malformed assembly fields; existing forwarded-result fixture. Regenerate current discovery contracts rather than introducing old-reader fixtures.
- **Mocks:** trace fixture exporters and existing Go acquisition/query harnesses.
- **Surface/expectation:** ephemeral/current portable diagnostic coherence; no historical compatibility. Preserve existing `ExecutionJournalProjector` exception tests; add no sensitivity classifier/content masking to canonical paths.

### 9) Supported surface and authoring claim evidence

- **Type/location:** architecture and source/fixture review; `LoomspanPublicSurfaceArchitectureTest` plus documentation changes.
- **Proves:** zero supported API/SPI expansion or leaked internal signature types; necessary internal allowlist inventory updated atomically. Every updated guide claim is supported by the preceding executable test groups.
- **Data/mocks:** existing architecture scanner; complete illustrative schema examples grounded in integration fixtures.
- **Surface/expectation:** protected application/SPI boundary; intentional internal changes need no compatibility shims. Tests verify runtime behavior, not prose wording.

## Authoring Claims Requiring Evidence

| Claim | Evidence group |
| --- | --- |
| PR21 destination map/from/skill/path grammar, current input without planning and mutual exclusion | 1 |
| Object schema, native nullability, source/destination known checks and unknown-shape runtime limits | 1–3 |
| Projected initial/corrective ownership, required siblings and optional/open full-bound decision | 2, 5–6 |
| Exact model/Java/REST values, null/absence, whole/subtree values and detached containers | 3, 6–7 |
| Exactly one output producer, no false dependencies, N/N+1 step costs and all-work joins | 4, 6 |
| Source/bound failures terminal; model-owned errors correct without replay | 3–6 |
| Captured generation, nested/concurrent isolation and authorization | 7 |
| Raw versus assembled diagnostics, exact source provenance and skipped synthesis | 8 |

## How to Run

Use Java 21 through the Maven wrapper; research established `java` is absent from normal PowerShell PATH but the wrapper selects the available JBR Java 21 installation. Maven Surefire executes integration-named tests in `test`. Use existing local fixtures and no paid credentials.

1. Red evidence: `./mvnw.cmd -q '-Dtest=YamlSkillCatalogTests#acceptsInputOnlyOutputBindingsWithDeclaredObjectContract' test` (PowerShell accepts the wrapper path; prefer `.\mvnw.cmd`). Record actual expected failure.
2. Focused new mechanics: `.\mvnw.cmd -q '-Dtest=YamlSkillCatalogTests,SkillGenerationManagerTest,OutputBindingProjectionTest,OutputBindingAssemblerTest,PlanningServiceTest,StepLoopMissionExecutionEngineTest,OutputSchemaCallAdvisorTest,OutputSchemaPromptAugmentorTest,DeclaredOutputBindingsIntegrationTest,DeclaredOutputBindingsTraceTest,BindingIsolationIntegrationTest,DesignatedChildResultIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test`. Update test names to actual final internal names once, preserving equivalent coverage; do not let missing tests silently pass.
3. Complete deterministic Java regression: `.\mvnw.cmd -q test`.
4. Required surface gate, independently visible in the report: `.\mvnw.cmd -q '-Dtest=LoomspanPublicSurfaceArchitectureTest' test` after all production changes, or cite its execution in the final full suite if no later production edits occur.
5. Focused Console verification from `loomspan-console/`: `go test ./internal/traceanalysis ./internal/live ./internal/browserapi ./internal/mcpadapter`.
6. Canonical Console verification from `loomspan-console/`: `go run ./internal/buildtool verify`. This checks declared Go/Node/npm versions, locked frontend graph, type check/tests/assets and all Go tests. Do not substitute ad-hoc generated production assets. If unavailable tooling prevents this command, report the exact failure/NOT RUN and residual risk; do not silently call focused tests sufficient for changed unverified front-end/schema boundaries.
7. Review final diff with `git diff --check` and verify generated current contract fixtures match production schemas. No dependency/release upgrades merely to satisfy unrelated host mismatch.

No paid evaluations or live external model calls are required. No optional manual observation is a completion gate.

## Exit Criteria

- [ ] Pre-fix behavioral red evidence was not established: concurrent incomplete builds prevented the assertion. The red test was written pre-fix and the post-fix test passes; see implementation execution notes. This historical limitation is explicitly reported, not a pending runtime check.
- [x] Declaration/source compatibility and projected ownership tests cover both runtime paths and every ticket failure category.
- [x] Exact selected values, no final synthesis for fully bound closed output, and retained model work for optional/open unbound output are asserted.
- [x] All accepted work joins; failure/cancellation/ambiguous/stale/cross-owner source cannot publish assembly success.
- [x] Correction budget applies only to model contributions; captured values unchanged and child invocation counters prove no replay.
- [x] Model/Java/REST sources, authorization, concurrent/nested executions and reload have deterministic executable evidence.
- [x] Existing forwarding, normal synthesis and PR21 behavior pass; obsolete internal paths removed without aliases/shims.
- [x] Architecture test passes with no supported API/SPI leak or expansion.
- [x] Java/current-Go diagnostics, same-release acceptance/rejection, discovery fixtures and canonical Console verification pass or a precise external tooling limitation is explicitly reported with remaining verification risk.
- [x] Routed docs, complete examples, README routing/coverage and evidence links match implemented semantics; no unresolved drift remains.
- [x] Full Java regression and appropriate Console regression pass; `git diff --check` clean.
- [x] No paid model run, sidecar edit, commit/push/release or unrelated destructive change occurred.

## Optional Developer Checks

None required. Deterministic verification establishes binding mechanics; any later business-reasoning evaluation is separate work requiring explicit authorization.


Implementation verification and acceptance mapping: see the implementation plan's final execution notes. Provisional test names were consolidated into OutputBindingCompositionTest, OutputBindingIsolationTest, DeclaredOutputBindingsOrdinaryIntegrationTest and DeclaredOutputBindingsPlanningIntegrationTest plus expanded canonical/live/step suites. All required current-behavior checks passed; the historical red-evidence limitation remains explicitly unchecked above.
