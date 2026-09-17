# PR 9 — Admitted invocation generation ID testing plan

## Change Summary

Add `AdmittedSkillInvocation.generationId()` as a supported public method that returns the immutable identity captured during preparation. Both input overloads must use the same value as their captured catalog and REST work. Reading it must remain valid after execution, failure, release, retirement, or cutoff without affecting admission ownership.

## Impacted Areas

- `AdmittedSkillInvocation` method contract and exact supported signature.
- `DefaultSkillInvocationHandoff` handle construction and payload clearing.
- Generation capture timing in `DefaultSkillTemplate`, root/nested execution binding, REST handler metadata, and reload publication.
- Framework admission claim, lease release, retirement, and shutdown cutoff, which must retain their behavior.
- README and routed Java API invocation/reload guidance, whose claims must match the test evidence.

## Risk Assessment

- **Wrong identity**: reading the current catalog after handoff may return B even though the pending handle owns A. Object conversion can publish B before the handoff returns; test that specific timing.
- **Cleared payload**: deriving the ID from `payload` fails after invocation, release, or cutoff. Failure paths need the same lifetime guarantee.
- **Ownership regression**: retaining or reading a handle could accidentally keep A alive if the implementation retains a generation or lease rather than only its string ID.
- **Concurrency**: reads must stay stable while another thread executes, releases, or cuts off the handle; concurrent reads must not be interpreted as claims.
- **Boundary**: a new abstract Application API method is an intentional pre-1.0 source/binary change. The ticket authorizes direct implementation updates and no default-method shim. `RestSkillHandler` stays the sole Supported SPI, with unchanged signature and trusted `RestSkillInvocation` metadata. Configuration/manifest, persisted/serialized, and ephemeral diagnostic contracts have no delta; internal handle details do not become supported API.
- **Documentation**: application-owned mapping can be removed at normal retirement or cutoff. Correlation examples must handle missing mapping and release abandoned admissions, and must not imply that the process-local ID is a durable snapshot ID.

## Existing Test Coverage

- `ApplicationApiValueTest#handoffAndAdmissionHandleExposeOnlyTheSupportedSingleUseShape` checks exact methods but currently has no `generationId` method (`src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:50-66`).
- `SkillGenerationExecutionIntegrationTest#capturePrecedesObjectConversionAndInputValidation` publishes during conversion; `#capturedHandoffUsesOneGenerationAfterActivationAndNewRootUsesReplacement` checks captured definitions and authorization (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:39-139`). Neither checks the handle ID.
- `PublicSkillReloadIntegrationTest` has candidate publication, old admitted REST work, new work, handler routing, and retirement following release, success, and failure (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:35-87,242-310`). It currently infers handler generation from configuration selection rather than asserting direct ID equality.
- `SupportedSurfaceIntegrationTest#invokesYamlPlannerAndBothLeafKindsThroughSupportedSurface` hands off a YAML root and invokes a nested REST leaf through its local model fixture; the handler captures the last `RestSkillInvocation` (`src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:42-77,105-160,255-275`). Its slot is later overwritten by direct REST calls.
- `DefaultSkillTemplateTest#releasedAndCutOffHandoffsCannotExecute` verifies payload clearing and failure; `FrameworkShutdownIntegrationTest#handedOffInvocationStartsAfterFrameworkCloseWithoutReacquiringAdmission` checks pending work during shutdown (`src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:104-137`; `src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:41-84`).
- `LoomspanPublicSurfaceArchitectureTest` protects the nineteen supported types, sole SPI, and public signature boundaries. It must run after the production type changes.

## Bug Reproduction / Failing Test First

- **Type**: focused unit/API test.
- **Location**: `src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`, then a behavioral assertion in `PublicSkillReloadIntegrationTest`.
- **Arrange/Act/Assert**: add `generationId(): String` to the exact method-shape expectation and call it on a map handoff captured under generation A. Publish B, then assert the ID remains A and is nonblank.
- **Expected failure pre-fix**: the shape assertion fails because the method is absent; compiling a direct accessor call also fails. Once the method exists, the reload assertion catches an incorrect current-catalog implementation.
- **Execution sequence**: run the narrow red test first, record its actual failure, then add production code and run the focused green set.

## Tests to Add or Update

### 1) Public admitted-handle method shape

- **Type / location**: unit, `ApplicationApiValueTest#handoffAndAdmissionHandleExposeOnlyTheSupportedSingleUseShape`.
- **Proves**: exactly one new `String generationId()` method with no arguments; invoke and release signatures remain unchanged. Run `LoomspanPublicSurfaceArchitectureTest` to prove no new API type, SPI, or internal signature leak.
- **Fixtures / mocks**: reflection only; no mocks.
- **Affected surface / expectation**: Application API; intentional abstract-method addition authorized by ticket Pipeline notes, no shim.

### 2) Both overloads return their captured catalog/REST identity across publication

- **Type / location**: integration, extend `PublicSkillReloadIntegrationTest` with the existing application context, REST handler, candidate, and retirement fixtures.
- **Proves**: map and object handoffs under A return nonblank A equal to A's snapshot/candidate ID, remain A after B is published, and execute A definitions. New B handoffs report B and execute B definitions. Capture handler `RestSkillInvocation.generationId()` directly for each call and compare with the corresponding admitted ID, rather than inferring from result text alone. Assert no input-map `generationId` spoofing affects it using the existing spoofed-input pattern.
- **Fixtures / mocks**: existing `ApplicationContextRunner`, in-memory `SkillDocument`, `RestConfiguration` handler; use a small object record or existing object input to select the object overload explicitly.
- **Affected surface / expectation**: Application API, existing Supported SPI metadata; protect the captured identity path while keeping REST signature unchanged.

### 3) Object capture precedes conversion-time publication

- **Type / location**: integration, extend `SkillGenerationExecutionIntegrationTest#capturePrecedesObjectConversionAndInputValidation` or add a sibling using the same custom `ObjectMapper` publication pattern.
- **Proves**: an object handoff can return after B becomes current yet still expose A's ID and run A's prepared definition. A new map handoff reports B and follows B's authorization/definition; conversion and validation order remains intact. Ensure cleanup of every admitted handle and lifecycle.
- **Fixtures / mocks**: existing `TestCapabilityRegistry`, old/replacement generations, mapper override, execution coordinator, and lifecycle/runner patterns in that test class. Use successful input for the handoff rather than relying solely on `validate`.
- **Affected surface / expectation**: Application API and internal capture timing; protect the ticket's pre-conversion identity guarantee.

### 4) Stable reads across execution, failure, release, cutoff, and retirement

- **Type / location**: focused lifecycle and integration; extend `DefaultSkillTemplateTest#handoffCapturesPreparedInputAndCallingAuthentication`, `#releasedAndCutOffHandoffsCannotExecute`, and `PublicSkillReloadIntegrationTest#retirementWaitsForPendingOldGenerationThenCleansUpAfterRelease`. Add only the smallest sibling test needed for a failure path.
- **Proves**: repeated reads equal the original ID before and after successful execution, failed execution, idempotent release, and cutoff even after the payload is null. Invoke/release outcomes retain the current assertions. Retain released/completed A handles after B publication and assert retirement fires with those handles still reachable; further ID reads do not delay or change retirement and do not restore application mapping. At cutoff, assert the ID still reads while invocation stays rejected and shutdown completes.
- **Fixtures / mocks**: existing router mocks, lifecycle, retirement callback and `RestConfiguration` map; no new lifecycle scaffolding.
- **Affected surface / expectation**: Application API and internal lifecycle; stable ID with no ownership extension. Retirement notification, not GC timing, is the oracle.

### 5) Concurrent access has no claim or state effect

- **Type / location**: focused integration, add to `DefaultSkillTemplateTest` using its existing handoff/lifecycle setup or a sibling in `SkillGenerationExecutionIntegrationTest`.
- **Proves**: multiple threads reading `generationId()` concurrently all receive the original string while one thread executes or releases; readings alone leave a pending handle executable, and after a terminal state reads remain stable. Existing single-use loser behavior remains unchanged.
- **Fixtures / mocks**: bounded executor plus barriers/latches and a deterministic router barrier if needed; await futures with timeouts and clean up executor/lifecycle. Avoid sleeps and wall-clock ordering assumptions.
- **Affected surface / expectation**: Application API; thread-safe read-only semantics.

### 6) Nested REST handler receives the admitted root ID

- **Type / location**: integration, extend `SupportedSurfaceIntegrationTest#invokesYamlPlannerAndBothLeafKindsThroughSupportedSurface`.
- **Proves**: the handed-off YAML root's `generationId()` equals the startup catalog ID and the REST leaf's recorded `RestSkillInvocation.generationId()` after nested execution. Make the comparison immediately after the YAML root returns, before subsequent direct calls replace `lastInvocation`.
- **Fixtures / mocks**: existing `MockWebServer` scripted planner, `SupportedSkillConfiguration.lastInvocation`, and supported beans. No new model server or YAML fixture.
- **Affected surface / expectation**: Application API and existing Supported SPI metadata; protect identity propagation through nested work.

### 7) Shutdown pending window and authorization regressions

- **Type / location**: integration, extend `FrameworkShutdownIntegrationTest#handedOffInvocationStartsAfterFrameworkCloseWithoutReacquiringAdmission` and existing authorization assertions in `SkillGenerationExecutionIntegrationTest` / `SupportedSurfaceIntegrationTest` only where an added ID read can occur before the same action.
- **Proves**: reading the ID does not claim, execute, release, or extend a pending root; a handoff admitted before close can still start within the existing budget, and cutoff still rejects a pending handle. Captured caller authorization, denied new generation, and worker-context restoration remain as already asserted.
- **Fixtures / mocks**: existing lifecycle and security fixtures.
- **Affected surface / expectation**: Application API plus internal lifecycle/security; protect existing behavior.

## Documentation Evidence

The implementation and the tests above must support these exact guidance claims: capture occurs before conversion/validation; ID remains stable after payload and ownership end; nested REST metadata matches the root; reads have no admission/retirement side effect; IDs are process-local; application mapping may be absent after retention cleanup, including cutoff. Update `README.md`, public Javadoc, and `agent-skills/loomspan-docs/references/java-api/{invocation,skill-reload,compatibility-and-boundaries}.md`. The correlation example must record the application-owned snapshot association before invocation, explicitly handle a missing map entry, and always call `release()` in `finally` if lookup, recording, dispatch, or invocation fails. The version-aligned existing guidance is **aligned** with current executable behavior; the new accessor is absent before this change. Skill-authoring topic coverage is unaffected.

## How to Run

On Windows from repository root, using the checked-in Maven wrapper:

```powershell
./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ApplicationApiValueTest test
./mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,SkillGenerationExecutionIntegrationTest,PublicSkillReloadIntegrationTest,SupportedSurfaceIntegrationTest,FrameworkShutdownIntegrationTest test
./mvnw.cmd --batch-mode --no-transfer-progress test
git diff --check
```

The first command is the red/green focused check. No external credentials or Sidecar are required; the nested model fixture uses local `MockWebServer`. Run the full suite once focused tests pass. If Maven's Windows argument handling splits the comma-separated `-Dtest` value, quote that argument in PowerShell without changing the selected classes. Do not conflate an unrun suite with a pass.

## Exit Criteria

- [x] The pre-fix method-shape test fails for the missing method, then passes after the code change.
- [x] Both public overloads report the correct nonblank captured ID across publication, and old/new work uses old/new definitions.
- [x] Root and nested REST invocation metadata matches the admitted handle; caller spoofing cannot replace it.
- [x] Repeated and concurrent reads are stable through success, failure, release, retirement, and cutoff and leave invocation/release outcomes intact.
- [x] Retained handle reads do not delay retirement, reacquire ownership, prolong shutdown, or promise mapping availability.
- [x] Existing authorization, nesting, single-use, admission, shutdown, and retirement tests pass; `LoomspanPublicSurfaceArchitectureTest` passes.
- [x] New abstract method is documented for handwritten implementations; no SPI, registry, persistence contract, or fallback is introduced.
- [x] Javadoc, README, and Java API guidance are consistent with focused evidence; correlation examples record before invoke, handle missing mapping, and release abandoned admissions.
- [x] Full `./mvnw.cmd --batch-mode --no-transfer-progress test` and `git diff --check` pass. No optional non-automatable developer check is required.
