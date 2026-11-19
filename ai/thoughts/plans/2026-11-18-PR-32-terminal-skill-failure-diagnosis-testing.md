# Terminal skill contract failure diagnosis testing plan

## Change summary

Verify the optional supported `SkillException#getFailureDiagnosis()` contract, exact schema issue pointers, four-stage attribution, and agreement with the canonical selected terminal cause. Cover direct preparation, handoff preparation and admitted execution with diagnostics `NEVER`, including corrected attempts and losing branches that must not contribute diagnosis.

This is Step 3 in the developer-approved Full 5-Step Pipeline. The governing implementation plan is `ai/thoughts/plans/2026-11-18-PR-32-terminal-skill-failure-diagnosis.md`. Planning performed no test execution or source edits. Commands below are required verification instructions, not passing receipts.

## Impacted areas

- Public exception constructors, optional getter, three immutable values, exact allowlist and supported signature checks.
- Root preparation, generated Java input contracts, runtime child input validation and planner argument correction.
- YAML input/output transformation and executor publication.
- Output validator, ordinary model correction, planning final response, projected output contribution and assembled output.
- Session failure identity/cause records, coordinator primary cancellation selection, fallback completion and facade callback/catch handling.
- README and bundled API/skill-authoring failure interpretation references and coverage tables.

## Risk assessment

The largest risks are attributing a recorded losing schema failure to an unrelated terminal error, retaining an earlier rejected attempt after correction, changing retry/cancellation behavior, and losing pointer fidelity by deriving public values from bounded diagnostic text. Session-local ownership must also reject a previously diagnosed exception rethrown by application code in another invocation, including simultaneous uses.

Protected Application API includes both legacy `SkillException` constructors, the existing public input exception and its issues, invocation/handoff signatures, observer failure precedence, and authorization exception behavior. No supported SPI exists. Internal signatures and test doubles update atomically. Configuration and manifest behavior remains the existing baseline. Current canonical diagnostic IDs, paths and wrapper links must stay coherent. There is no serialized protocol change or Java-to-Go verification requirement.

## Existing test coverage

- `ApplicationApiValueTest` covers public constructor and issue copying behavior.
- `LoomspanPublicSurfaceArchitectureTest` covers the closed API type set, signatures, constructor exposure and technical-public internal classification.
- `DefaultSkillTemplateTest` covers pre-session validation, direct/handoff execution, exception identity, observers, finalization failure and diagnostics retention.
- `SupportedSurfaceIntegrationTest` provides real Spring application composition with a local protocol-compatible model.
- `ExecutorOutputSchemaIntegrationTest`, `ExecutorOutputTransformIntegrationTest` and `ExecutorInputTransformIntegrationTest` cover terminal output, transform behavior, call counts and child dependency flow.
- `StepActionValidatorTest`, `StepLoopMissionExecutionEngineTest`, `OutputSchemaCallAdvisorTest` and binding integration suites cover correction and assembly.
- `LoomspanSessionTest`, `ExecutionCoordinatorTest`, `FrameworkExecutionLifecycleTest` and `MissionLifecycleTest` cover cause linkage, failure order, cancellation and fallback lifetime behavior.

Extend these fixtures wherever they can prove the new behavior directly. A focused new integration test class is appropriate if combining the public/canonical/parallel checks in existing suites would require substantial duplication. Keep public consumer assertions isolated from internal exception inspection, while separate diagnostic assertions may use internal test helpers.

## Failing test first

Start with the existing transformed-output dependency scenario in `ExecutorOutputSchemaIntegrationTest#dependentChildOnlyConsumesValidProducerResults` or a directly adjacent test.

- Arrange a planning root `parent`, child `default-tsTriageStructured`, successful executor output transform publishing `{"repeatContactProbability":null}`, and a dependent child that increments an invocation counter.
- Invoke through `SkillInvocationHandoff`, claim the admitted handle, and use diagnostics `NEVER` with no observer.
- Catch the outer public `SkillException`, assert diagnosis skill `default-tsTriageStructured`, stage `OUTPUT_SCHEMA`, issue `/repeatContactProbability`, code `type`, nonblank explanation and zero dependent invocations.
- Before production behavior exists, the consumer assertion fails because the getter/value shape is missing or diagnosis is absent. Capture that pre-fix result. If adding the required API initially causes a compilation failure, record it explicitly and then establish the behavioral red assertion once the shape compiles.
- After implementation, run the same test unchanged as the green result.

## Tests to add or update

### 1. Public values and constructor compatibility

**Type and location.** Unit tests in `src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`, architecture assertions in `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`.

Prove exact record components and four enum values, immutable copied issue list, null-list normalization, required fields, exact root/escaped/unusual paths and complete messages. Both old SkillException constructors and old input exception constructor remain usable and produce absent diagnosis. The input issue list remains independently immutable. Additive constructors retain the original cause. Test `(message, null)` source usage. Architecture checks classify only the three approved new API types and necessary internal collaborators, and reject internal signature leakage or public mutators/carrier interfaces.

Affected surface is Application API. Preserve existing supported constructor behavior and deliberate new values.

### 2. Exact validator issues and diagnostic path preservation

**Type and location.** Unit tests in `internal/outputschema/OutputSchemaValidatorTest.java` and `OutputBindingCompositionTest.java`, plus existing `internal/runtime/input/SkillInputValidatorTest.java` where needed.

Use actual compiled schemas. Test root scalar/null/type failure with pointer `""`, property `a/b~c` with `/a~1b~0c`, nested arrays, an empty property name, keys with leading/trailing whitespace or control characters, and a key longer than the diagnostic path bound. Assert the new exact issue pointer and explanation from the actual verdict. Assert existing root `$` and bounded diagnostic path behavior independently. Malformed, blank and trailing-token JSON yields exact public root pointer and code `invalid_json`. Validator operational exceptions propagate without a completed schema diagnosis.

For a selected bound destination, use a schema-invalid nested value and escaped destination key. Assert `/value` wrapper rebasing produces the published object's destination pointer and original keyword. Both projected contribution and complete assembled verdicts retain exact public issues. Missing sources/evidence and synthetic override errors remain without schema diagnosis. Avoid private method tests or mocked validator results when actual small schemas suffice.

Affected surfaces are Internal implementation and current-run Ephemeral diagnostics.

### 3. Root admission and generated Java input contracts

**Type and location.** Unit tests in `internal/skillapi/DefaultSkillTemplateTest.java` and integration assertions in `integration/SupportedSurfaceIntegrationTest.java` or a new focused supported failure suite.

Use a declared YAML input schema and a generated Java `@SkillMethod` contract. Invoke both Map and Object overloads where conversion is relevant. For invalid root input, assert the public input exception remains catchable, `getIssues()` remains original and equals the diagnosis issue values, receiving canonical name and `INPUT_SCHEMA`. Verify rejection before session creation, observer invocation or executor/Java entry. Perform equivalent handoff preparation and verify it rejects before a handle/session exists. Cover the existing explicit null-input rejection without inventing a validator issue code, and preserve generic/empty-contract null admission rules.

Use public API types in consumer assertions and normal Spring declarations. Existing unit mocks may count session/router calls. Affected surfaces are Application API and manifest input contracts.

### 4. Assembled and runtime receiving child input

**Type and location.** Integration tests in `integration/DeclaredChildInputBindingsIntegrationTest.java` and targeted router/invoker tests.

Supply an accepted producer value that violates the receiving child's assembled input contract. Assert receiver canonical name, `INPUT_SCHEMA`, original path/code/message, no receiver handler entry and unchanged binding source selection. Repeat through an ordinary child route where practical. Missing/ambiguous sources and binding authority rejection yield no four-stage diagnosis. No extra dispatch or schema reevaluation may be introduced for reporting.

Affected surfaces are Application API and configuration/manifest behavior.

### 5. Planner argument correction before dispatch

**Type and location.** Unit tests in `internal/runtime/step/StepActionValidatorTest.java` and engine execution tests in `StepLoopMissionExecutionEngineTest.java`, supplemented by one public integration assertion.

Use an assigned child with a number argument and fake model actions repeatedly supplying null or text. Exhaust the existing invalid action budget. Assert receiving child's `INPUT_SCHEMA`, original issue and zero dispatch. Assert the same action/correction/model-call count as existing budget behavior.

Controls must use a later valid argument and a successful child, then an invalid schema attempt followed by an unrelated terminal rejection such as wrong assigned tool or exhausted malformed action parsing. Success has no failure. Unrelated terminal failure has absent diagnosis. Also cover schema correction followed by planner inability or a non-schema final validation failure. Each fresh attempt's typed verdict must replace the prior value.

Affected surfaces are Internal implementation and Application API reporting. Correction mechanics retain baseline behavior.

### 6. Input and output transform attribution

**Type and location.** Existing executor transform unit/integration suites.

Input transformation returning null, array or scalar reports canonical YAML owner and `INPUT_TRANSFORM` before executor entry. JSON admission/evaluation errors use the same stage. Output transform receiving malformed successful handler text or evaluating `error("Incomplete triage judgment")` reports `OUTPUT_TRANSFORM` after exactly one executor call, with the authored explanation retained. Transform diagnoses have an empty issue list. A successful transform publishing a schema-invalid value reports `OUTPUT_SCHEMA`. Ordinary handler exception, transform compilation/declaration rejection and authorization failure have no four-stage diagnosis.

Cover direct, ordinary child and planned child routes through existing parameterized fixtures. Add admitted/NEVER coverage at the public integration boundary rather than multiplying every low-level case. Affected surfaces are Application API and existing transform/manifest behavior.

### 7. Executor, ordinary model and planning output schemas

**Type and location.** `ExecutorOutputSchemaIntegrationTest`, `OutputSchemaCallAdvisorTest`, `ExecutionCoordinatorOutputSchemaIntegrationTest`, and `StepLoopMissionExecutionEngineTest`.

The same schema-invalid value yields the same public issue shape and publisher stage across executor, ordinary-model and explicit-planning completion. Include malformed text at an ordinary schema boundary with `invalid_json`. Corrected model candidates reach success within unchanged retry counts. A schema rejection followed by terminal linter/evidence rejection has no diagnosis for that earlier attempt. Executor failure remains one call and zero semantic correction. Use original validator issues rather than testing formatted exception text.

Affected surfaces are Application API and existing validation/retry behavior.

### 8. Fully bound, mixed and assembled outputs

**Type and location.** `DeclaredOutputBindingsOrdinaryIntegrationTest`, `DeclaredOutputBindingsPlanningIntegrationTest` and `OutputBindingCompositionTest`.

Cover selected bound destination violation before synthesis, projected model contribution schema failure on exhaustion, complete assembled output schema failure and a fully bound final schema verdict. Attribute every actual terminal schema rejection to the publishing skill, with pointers in its published object. Test a passing schema followed by evidence/linter failure and missing source/authority errors as absent diagnoses. Preserve skipped synthesis and existing call counts where output is fully bound.

Affected surfaces are Application API, configuration/manifest behavior and Internal implementation.

### 9. Canonical failure identity and wrappers

**Type and location.** `internal/core/LoomspanSessionTest.java` plus a facade integration scenario.

Record an owned schema failure, transparent wrappers, and an ordinary causal application wrapper. Assert same identity for transparent wrappers and existing `causedByFailureId` for the application wrapper, with inherited diagnosis. The selected wrapper's diagnosis agrees with its canonical cause. Suppressed-only schema errors do not confer diagnosis. An earlier distinct schema failure does not confer diagnosis on a later unrelated application failure. Preserve cycle/depth/concurrency/session-isolation behavior.

Test original schema failure before a diagnostic append failure and root finalization/mapping failure. The public diagnosis survives with existing primary/suppressed behavior. No trace reading or observer callback is used to compute it.

Affected surface is Internal implementation and current-run Ephemeral diagnostics.

### 10. Parallel accepted order and primary cancellation

**Type and location.** Extend `StepLoopMissionExecutionEngineTest#parallelFailureSelectionUsesClassificationThenAcceptedOrder`, core lifecycle/coordinator tests and a complete supported invocation integration fixture.

Use deterministic latches, no timing sleeps. Accept task A before B, hold A until B's schema failure is recorded, then release A to produce another schema failure. Assert recorded error order B/A and canonical terminal selection A. Through the public facade, assert A's skill/issues. Repeat with an ordinary causal wrapper around A and verify wrapper linkage plus public agreement.

In a separate run record a schema failure in one branch while an unrelated terminal application failure wins under existing classification/order. Assert absent public diagnosis despite the schema event. In another run establish primary cancellation with its failure ID while a schema exception propagates. Assert root canonical cancellation ID and absent public diagnosis. Cover the lifecycle path that supplies a primary cancellation cause before an ID exists. Verify no accepted-order, cleanup, interruption or cancellation behavior changes.

Affected surfaces are Internal implementation, lifecycle/concurrency baseline and public reporting.

### 11. Direct and admitted facade, observers and diagnostics NEVER

**Type and location.** `DefaultSkillTemplateTest` and `SupportedSurfaceIntegrationTest` or a focused new public failure integration suite.

Exercise both SkillTemplate and `SkillInvocationHandoff`/`AdmittedSkillInvocation` with no observer and trace persistence `NEVER`. Public catch/getter/value assertions require no internal imports or exception-cause parsing. Run a matched retained-diagnostics scenario separately and compare public diagnosis with canonical terminal ID/cause evidence. Assert the requested root name remains application-owned context and diagnosis contains the actual child name.

Preserve existing plain SkillException identity when diagnosis is absent, authorization identity, fatal Error behavior, successful observer exception identity and original execution failure precedence. An observer throwing after a diagnosed failure is suppressed and cannot replace diagnosis. Capture occurs before view mapping, so unavailable history leaves diagnosis intact.

Affected surface is Application API.

### 12. Exception reuse and concurrent invocation isolation

**Type and location.** Facade/core integration tests near the supported failure fixture and session ownership unit tests.

Capture a Framework-origin diagnosed failure from run one. Have an ordinary application handler rethrow that same object in run two. The selected failure has no current contract verdict, so the outer public failure has absent diagnosis and keeps the original throwable as its diagnostic cause. Repeat with an application-created SkillException containing a public diagnosis, and with two simultaneous handlers throwing the same exception while only one invocation independently selects a real contract failure. Assert the public results are immutable independent snapshots and the original exception is never mutated. Also verify a causal wrapper around a genuinely current owned failure retains diagnosis.

Affected surfaces are Application API and Internal implementation. This is a concrete regression case for newly introduced diagnosis state.

## Acceptance criterion mapping

| Ticket criterion | Required executable groups |
| --- | --- |
| Root, Java-generated and bound child input with public exception compatibility | 1, 3, 4, 11 |
| Undispatched assigned child schema exhaustion and corrected/unrelated controls | 5 |
| parent/default-tsTriageStructured transformed null, skipped dependent, NEVER, canonical agreement | Failing test, 6, 7, 10, 11 |
| Executor/model/planning/assembled shape, corrected attempts, invalid_json, exact pointers | 2, 7, 8 |
| Non-object input transform, malformed/error output transform, single executor call | 6 |
| Reverse accepted-order schema recording, causal wrapper, unrelated winner/cancellation absence | 9, 10 |
| README, supported signatures/entry points, closed allowlist | 1, 11 and documentation verification |

## How to run

Use Maven with Java 21. `mvn -v` in this environment resolves the configured JBR SDK even if a standalone `java` command is unavailable. Local fake models and loopback HTTP fixtures need no credentials or live provider. Do not change build configuration solely for PATH setup.

Run targeted groups as their production phases become complete.

```powershell
mvn "-Dtest=ApplicationApiValueTest,OutputSchemaValidatorTest,OutputBindingCompositionTest,LoomspanPublicSurfaceArchitectureTest" test
mvn "-Dtest=DefaultSkillTemplateTest,DeclaredChildInputBindingsIntegrationTest,StepActionValidatorTest,ExecutorInputTransformTest,ExecutorInputTransformIntegrationTest,ExecutorOutputTransformTest,ExecutorOutputTransformIntegrationTest" test
mvn "-Dtest=ExecutorOutputSchemaIntegrationTest,OutputSchemaCallAdvisorTest,ExecutionCoordinatorOutputSchemaIntegrationTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest" test
mvn "-Dtest=StepLoopMissionExecutionEngineTest,LoomspanSessionTest,ExecutionCoordinatorTest,FrameworkExecutionLifecycleTest,MissionLifecycleTest,SupportedSurfaceIntegrationTest" test
mvn test
git diff --check
```

If implementation adds a focused integration test class, add its actual class name to the appropriate targeted command and record that exact command in implementation results. Do not silently omit newly introduced tests. The complete Maven suite includes the architecture test after all production type edits. No Console/frontend suite is required because the plan changes no consumed protocol or UI.

Fresh review reconstructs the diff independently and runs sufficient relevant checks in its own context. Prior passing receipts alone do not establish fresh clean review.

## Documentation verification

Inspect the final README and the exact documentation paths listed in the implementation plan. Verify supported type counts/lists against the architecture allowlist, API examples against compiled signatures, stage/absence claims against groups above, and executor schema wording against the existing publication integration tests. Verify routing and coverage cells name actual focused tests. Apply the bundled knowledge base's LLM-first acceptance questions. Search for obsolete twenty-seven-type inventories and the stale executor enforcement statement, updating only directly affected inventories and claims.

## Exit criteria

- [x] The initial red assertion and its actual failure are recorded, or any infeasibility is explained with equivalent baseline evidence.
- [x] Every ticket acceptance criterion maps to passing executable evidence in the table above.
- [x] Exact JSON Pointers and original explanations survive the diagnostic representation bounds.
- [x] Planner correction, model correction, executor call counts, skipped dependents, authorization, cancellation and accepted-order selection retain baseline behavior.
- [x] Losing branches, corrected attempts, unrelated failures and reused exceptions cannot populate stale diagnosis.
- [x] Direct preparation, handoff preparation and admitted execution expose supported values with diagnostics `NEVER` and no observer.
- [x] Legacy public constructors/input issue access and undecorated failure passthrough tests pass. Additive signatures leak no internal types.
- [x] All new/updated targeted tests and the complete `mvn test` pass, including `LoomspanPublicSurfaceArchitectureTest` after final production changes.
- [x] `git diff --check` passes.
- [x] Documentation matches implemented behavior, type inventories and focused evidence, with narrow known documentation drift corrected.
- [x] Implementation results record actual commands, attribution of any failures, and no unresolved required checks.

Optional developer checks are none. No acceptance criterion depends on an unavailable external environment.


## Executed verification

Step 4 completed the entire Maven suite from an isolated exact source snapshot to avoid IDE/JDT writes into the watched checkout's build output. `mvn -q test` completed 2,370 tests with zero failures/errors and two existing opt-in PR18 diagnostic skips. All changed/new source, test and documentation files matched the snapshot by SHA-256. The implementation plan's final verification and acceptance table record the exact evidence, earlier environment failures and material adaptations. `git -c core.safecrlf=false diff --check` passed. Optional developer checks are none.
