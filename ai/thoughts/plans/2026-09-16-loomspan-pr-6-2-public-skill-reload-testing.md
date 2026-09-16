# PR 6.2 Public Skill Reload Testing Plan

## Change Summary

Expose a deliberately supported two-stage skill update service and immutable candidate handle. Add generation identity to catalogs, REST invocations, and current execution diagnostics. Preserve complete generation capture and validation while coordinating publication with shutdown, then update the Java/Go/Console diagnostic consumers and documentation together. The companion implementation plan classifies all affected contracts and authorizes no compatibility shim.

## Impacted Areas

- API shape and Spring wiring: `ai.loomspan.api`, `LoomspanAutoConfiguration`, public-surface architecture tests, API value tests, supported-surface integration.
- Candidate assembly/publication: `SkillGenerationManager`, `SkillGeneration`, `DefaultSkillCatalog`, new framework-owned reloader/handle, `FrameworkExecutionLifecycle`.
- Execution and retention: `DefaultSkillTemplate`, `DefaultSkillInvocationHandoff`, `ExecutionBinding`, `LoomspanSessionRunner`, REST handler invocation.
- Diagnostics: `TRACE_STARTED`, observation factories/projector, active/finalized catalog/REST DTOs, active discovery endpoints, Java fixture corpus.
- Console: Go observability DTO validation, trace analysis/acquisition, MCP contracts/output schemas, TypeScript contracts/UI fixtures and focused tests.
- Documentation: README, Java API knowledge base, skill-authoring mental model, REST topic, traces topic, and coverage/routing table.

## Risk Assessment

1. A candidate might validate incompletely or read mutable YAML after prepare; resource changes or removed children must not leak into published behavior. Startup validation and fixed Java/REST dependency rules are protected configuration/manifest paths.
2. Two candidates or a concurrent shutdown might cause stale overwrite, double publication, or activation after admission closes. Checking state outside the lifecycle gate is insufficient; tests need controlled interleavings, not sleeps.
3. An old handoff/root/nested/parallel call might fetch current schemas, policies, handler configuration, or diagnostic ID after publication. This is both compatibility and authorization risk. Trusted generation ID must not be supplied by model input.
4. Retained candidates, catalogs, handoffs, and observability state might retain unnecessary executable generations. Correctness assertions should inspect explicit reference-owning fields/cleanup actions or observable ownership, not wait for GC.
5. Diagnostic updates can diverge between trace, live/finalized REST, Go analysis, MCP/browser contracts, or fixtures. The current-run ephemeral format must remain useful, accurately ordered, redacted, and guarded by the exact release marker; obsolete fixture shapes should be updated, not dual-read.
6. The application-facing API is intentionally breaking pre-1.0: catalog ID and the three-component REST record replace the old shape without aliases/overloads. `RestSkillHandler` remains the only supported SPI; no internal type may leak.

## Existing Test Coverage

- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java` already covers full assembly, fresh UUIDs, candidate isolation, additions/removals/empty YAML, invalid references, changed source bytes, and fixed handler reuse; it does **not** exercise public prepare/publish ownership/base/shutdown.
- `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java` covers capture before conversion and old/new root consistency after internal activation; it does not assert REST generation-keyed config or public publication.
- `src/test/java/ai/loomspan/api/ApplicationApiValueTest.java` and `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` encode the old exact API shape and closed allowlist.
- `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` covers application-visible YAML/Java/REST behavior, handler input immutability, auth, denial and failures, but no reloader.
- `src/test/java/ai/loomspan/internal/runtime/trace/ConsoleTraceFixtureCorpusTest.java`, `src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java`, Java observability integration tests, Go trace/DTO/MCP tests, and TypeScript component tests protect the current diagnostic chain. Existing fixtures lack generation ID.
- `FrameworkExecutionLifecycle` and handoff tests protect root admission/cutoff/release; they need update-publication boundary coverage. Existing authorization tests remain required regression gates.

## Bug Reproduction / Failing Test First

- **Type**: integration, with a small API compilation boundary.
- **Location**: new `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java`.
- **Arrange**: start a Spring context with a mutable test YAML resource and one fixed REST handler; obtain the injected startup `SkillCatalog`, `SkillReloader`, and `SkillTemplate`. The handler reads a test map keyed by `invocation.generationId()`.
- **Act**: get `snapshot().generationId()`, edit YAML, call `prepare()`, stage candidate-keyed configuration, publish, then invoke old admitted work and a new root.
- **Assert**: startup catalog stays old; candidate and published snapshots have the same fresh ID; old handler sees A and new sees B; no current-active lookup substitutes B for A. On the pre-fix checkout, the public service and ID accessors do not exist, so the test fails at compilation. Keep this red evidence separately from the subsequent full implementation build; do not claim a runtime failure from a noncompiling test.

## Tests to Add or Update

### 1) `publicReloadContractIsClosedAndFrameworkOwned`

- **Type/Location**: unit/architecture; `ApplicationApiValueTest.java`, `LoomspanPublicSurfaceArchitectureTest.java`, and `LoomspanAutoConfigurationTests.java`.
- **Proves**: exact three new allowlisted types, exact `SkillReloader` methods, catalog/REST ID signatures, unchecked `SkillReloadException`, one injected framework service, no internal signature leakage or additional SPI/replaceable bean contract; null API arguments use normal validation. Obsolete two-component constructor/shape is absent.
- **Fixtures/Mocks**: reflection and application context; no external mocks for contract assertions.
- **Surface/Expectation**: Application API and Supported SPI; intentional ticket-approved no-shim change with sole handler SPI preserved.

### 2) `prepareFreezesCompleteCandidateWithoutActivation`

- **Type/Location**: unit/integration; extend `SkillGenerationManagerTest.java` and add focused public reloader test under `src/test/java/ai/loomspan/internal/skill/`.
- **Proves**: complete model/REST/Java validation, collisions, exact child references, handler cardinality including first REST addition, malformed/read-failed resource diagnostic cause, add/edit/delete/rename/empty-set behavior, unchanged active state on failure, and frozen bytes/definitions after modify/delete/invalid overwrite. Two same-content successful preparations have distinct IDs and matching candidate catalogs; no content/no-op branch. Empty YAML retains Java declarations. Startup validation remains covered by existing tests.
- **Fixtures/Mocks**: temporary YAML resources, fixed fake Java metadata, one or zero handler beans; count resource reads to prove publish/snapshot perform none. Use actual validation classes, not mocked validators.
- **Surface/Expectation**: Configuration/manifest and internal assembly; preserve documented syntax/validation and ticketed complete-set semantics.

### 3) `publishChecksOwnerBaseOneShotAndPreparedContents`

- **Type/Location**: unit plus public integration; new `SkillReloaderTest.java`, `PublicSkillReloadIntegrationTest.java`.
- **Proves**: candidate from a second Spring framework instance is foreign; fabricated `PreparedSkillUpdate` implementation is rejected; candidate prepared on A is stale after B publishes; repeated publication fails even while B remains current; rejected attempts do not change active; publication uses frozen candidate, retains its ID, and does not reread/revalidate. Abandonment is simply dropping a reference, with no discard/registry call.
- **Fixtures/Mocks**: two independent managers/lifecycles or contexts, mutable source with read counter; no sleeps.
- **Surface/Expectation**: Application API and internal publication; protected active-state invariant, no alternate old path.

### 4) `publicationAndShutdownShareOneBoundary`

- **Type/Location**: deterministic unit concurrency; `SkillReloaderTest.java` and `FrameworkExecutionLifecycleTest.java`.
- **Proves**: prepare calls serialize with each other; publish calls serialize with each other; publication can proceed while another prepare is blocked in resource read and causes that preparation to be stale; snapshots and ordinary root admissions continue during prepare; shutdown begun before prepare start or before prepare returns rejects success; publish after closure rejects; a publish already activated before closure succeeds; no activation after closure. Assert both orderings with `CountDownLatch`/barriers and bounded futures, not wall-clock sleeps. Keep lock-held actions short and assert no deadlock.
- **Fixtures/Mocks**: blocking resource supplier and activation hooks/latches, controlled executor; use actual lifecycle gate.
- **Surface/Expectation**: Internal lifecycle/concurrency; protected shutdown boundary.

### 5) `retainedTreesUseCapturedRESTGenerationAndPolicies`

- **Type/Location**: integration; extend `SkillGenerationExecutionIntegrationTest.java`, `SupportedSurfaceIntegrationTest.java`, and focused handoff/nested/parallel tests.
- **Proves**: capture occurs before input conversion and validation; delayed handoff invokes A after B publishes; nested/parallel REST children and removed child references continue under A with A schema/visibility/model settings/policy, while new roots use B. A generation-keyed handler chooses A/B configuration even when declarations are identical and only external config differs. An `input` key named `generationId` cannot overwrite the record component. Root and child auth/role checks remain enforced, caller auth is restored on handler thread, denial/null/failure behavior unchanged.
- **Fixtures/Mocks**: deterministic fake model interaction to request child skills, barriers for delayed/parallel work, configuration map keyed by ID, existing authorization fixtures. Avoid a live network service.
- **Surface/Expectation**: Application API, Supported SPI, configuration/manifest auth, internal execution; preserved security and intentional metadata addition.

### 6) `referencesReleaseOnEveryTerminationPath`

- **Type/Location**: unit/integration; handoff and runner lifecycle tests plus new reload retention test.
- **Proves**: active A, retained old work, B candidate, and active C can coexist (three or more IDs); completion, failure, cancellation, pending handoff release and shutdown cutoff clear framework-owned pending/binding references promptly without another prepare. Retained catalog/candidate may hold data, but a catalog does not allow historical invocation or require executable runtime retention. No hard two-generation limit or archive/retirement API.
- **Fixtures/Mocks**: test hooks/inspection of explicit payload and binding cleanup, deterministic task gates; no `System.gc()`, `WeakReference` timing, or finalizer assertion.
- **Surface/Expectation**: Internal lifecycle and Application API negative boundary.

### 7) `observabilityDiscoveryAndExecutionDiagnosticsUseCorrectGeneration`

- **Type/Location**: Java integration/unit; `ObservabilityRestIntegrationTest.java`, `ObservabilityDtoMapperTest.java`, `LiveActivityProjectorTest.java`, `ExecutionTraceHandleTest.java`, fixture corpus.
- **Proves**: status/list/detail discovery uses one active catalog per request and follows publication; one list request does not mix A/B under concurrent publish; across requests no snapshot-pagination guarantee is asserted. `TRACE_STARTED.metadata.generationId`, live active DTO and finalized trace DTO carry the captured A for old work even when B publishes before completion. ID is present in failures as early as start and contains no sensitive configuration. Exact version marker remains enforced.
- **Fixtures/Mocks**: barrier around catalog access, old execution held until after publish, emitted canonical trace and REST fixture bytes; assert source-path/YAML redaction rules remain.
- **Surface/Expectation**: Ephemeral diagnostics and internal discovery; current-run writer/reader coherence.

### 8) `consoleProjectsGenerationIdentityWithoutLegacyFallback`

- **Type/Location**: Go unit/integration in `loomspan-console/internal/{observability,traceanalysis,mcpadapter,browserapi,console}` and web Vitest/TypeScript in `loomspan-console/web/src`.
- **Proves**: Go DTO decode/validation and trace analysis retain the ID; MCP execution schemas/output and browser API contracts expose it consistently; active/finalized UI fixtures render/use it where appropriate. A malformed/missing ID in the *new* current fixture fails when required, while exact resolved marker mismatch/missing marker still rejects and dual development performs ordinary complete validation only. No old-reader/fallback branch. Security/redaction and ordering assertions continue to pass.
- **Fixtures/Mocks**: regenerate/update Java-produced current corpus and Go/TS fixtures; use existing test servers and fake trace inputs, no external service.
- **Surface/Expectation**: Ephemeral diagnostics and Java-to-Go boundary; coherent current version only.

### 9) `documentationClaimsMatchExecutableContract`

- **Type/Location**: source/documentation review supported by focused tests, not prose-only test additions.
- **Proves**: README example's startup ID readiness, prepare/stage/publish, stale rejection, fixed handler and A/B retention match tests 2–7. Skill-authoring guidance describes immutable tree capture, REST handler selection, and trace ID accurately; no new YAML syntax or historical cleanup claim. Update old exact-shape reflection/tests instead of retaining obsolete paths.
- **Fixtures/Mocks**: search and targeted review of docs/README; cited production/test anchors.
- **Surface/Expectation**: Application API and skill-authoring claims; intentional documentation update.

## How to Run

Windows repository root (the wrapper is present):

```powershell
.\mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest,SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest test
.\mvnw.cmd -Dtest=PublicSkillReloadIntegrationTest,SkillReloaderTest,FrameworkExecutionLifecycleTest,SupportedSurfaceIntegrationTest test
.\mvnw.cmd -Dtest=ObservabilityRestIntegrationTest,ObservabilityDtoMapperTest,LiveActivityProjectorTest,ExecutionTraceHandleTest,ConsoleTraceFixtureCorpusTest,ConsoleRestFixtureCorpusTest test
.\mvnw.cmd test
```

Run actual class names after implementation (a proposed new test name that changes during coding must be reflected in the verification report). In `loomspan-console`: `go test ./...`. In `loomspan-console/web`: `npm run typecheck`, `npm test`, and `npm run build:web`. Run `npm run test:e2e` only if browser dependencies are available and relevant changed UI flows warrant it; report NOT RUN honestly otherwise. Fixture generation/verification should use the repository's existing Java corpus tests and Go fixture consumers rather than hand-editing generated binary outputs. No network credentials or production services should be required.

## Exit Criteria

- [ ] The public integration red test fails pre-fix because the contract is absent; after implementation all named new and updated tests pass.
- [ ] Every ticket acceptance criterion has an automated assertion or an explicitly explained non-automatable observation; no required check remains unresolved.
- [ ] Concurrency tests deterministically exercise both publish/shutdown orderings and prepare/publish overlap without sleeps, deadlocks, or GC timing.
- [ ] Complete candidate validation, frozen publication, fresh IDs, catalog immutability/order, foreign/stale/repeated rejection, and no-read snapshot/publish all pass.
- [ ] Old/new retained execution covers delayed, nested, and parallel work, trusted REST ID, matching schemas/policies/visibility, and existing authorization.
- [ ] Explicit cleanup paths release framework ownership; three or more needed generations coexist; no archive, external cleanup, or safe-delete API is added.
- [ ] Architecture test passes after production type changes; only the handler remains SPI; no public internal-type leakage or obsolete constructor/shim remains.
- [ ] Current trace/REST Java writers, Go readers/MCP and browser/TypeScript consumers, fixture corpora, exact marker rejection, redaction, and failure visibility agree.
- [ ] README and skill-authoring/Java API guidance are updated atomically and each changed author-facing claim is supported by source and focused tests. The knowledge-base coverage table and routing satisfy the LLM-first standard.
- [ ] Full Maven, Go, and web suites/checks pass, or an actual environmental failure is reported as such with its residual risk; no unexplained failed verification is called complete.

## Optional Developer Checks

If the developer has a production-like deployment, they may observe application-specific external artifact staging/retention and traffic-readiness ordering. This is optional and nonblocking: the repository tests prove Loomspan's contract but cannot prove the application's configuration-store retention policy.
