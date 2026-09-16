# PR 6.1 — Coherent Skill Generations Testing Plan

## Change Summary

- Replace the mutable singleton YAML catalog/capability registry pair with detached,
  complete, immutable `SkillGeneration` candidates and one atomic active-generation
  owner.
- Preserve fixed Java declarations, model/connection runtimes, Spring beans, and REST
  handler candidates across preparations while rereading the complete configured YAML
  set for each candidate.
- Capture the active generation before root input validation, carry it through admission
  and `ExecutionBinding`, and use it for all schema, definition, child visibility,
  capability-policy, and execution-configuration decisions in nested and parallel work.
- Clear delayed-handoff ownership on invoke/release and rely on normal strong-reference
  reachability rather than a history, reference count, weak map, or GC callback.
- Preserve supported APIs/SPI, manifest/property syntax, catalog immutability,
  authorization enforcement, and all current serialized diagnostic protocols.

## Impacted Areas

- Whole-set loading and validation:
  `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`,
  `SkillMethodBeanPostProcessor`, replacement generation assembler/manager, and removal
  of `YamlSkillCapabilityRegistrar`, `CapabilityRegistry`, and
  `InMemoryCapabilityRegistry`.
- Immutable discovery:
  `DefaultSkillCatalog`, `DefaultRegisteredSkillCatalog`, and
  `ObservabilityRouteRegistrar`.
- Root facade and delayed admission:
  `DefaultSkillTemplate`, `DefaultSkillInvocationHandoff`,
  `LoomspanSessionRunner`, and `FrameworkExecutionLifecycle`.
- Invocation-tree propagation:
  `ExecutionBinding`, `ExecutionBindingScope`, `CapabilityExecutionRouter`,
  `ExecutionCoordinator`, `DefaultSkillVisibilityResolver`, tool-surface binding,
  mission workers, and parallel plan branches.
- Composition and internal cleanup:
  `LoomspanAutoConfiguration`, `StepLoopMissionExecutionEngine`, internal test factories,
  and auto-configuration/architecture tests.
- Authoring guidance:
  the skill-authoring README plus mental model, input, authorization, model selection,
  REST, and planning-concurrency topics named in the implementation plan.

## Risk Assessment

- **Highest risk — mixed-generation execution**: a root could validate against one
  schema and later resolve its definition, visible children, policy, or execution
  settings from another. Tests must activate replacements at controlled lifecycle
  boundaries, not merely compare static candidates.
- **Highest risk — authorization regression**: generation-stable manifest policy must not
  freeze or bypass trusted caller authentication. Old-tree work uses its captured policy
  but still runs visibility and execution checks with the captured/current invocation
  authentication according to existing rules.
- **High risk — incomplete candidate mutation**: duplicate names, invalid REST handler
  cardinality, bad schemas, or removed children could partially alter live state if any
  old registrar mutation remains. Failure tests must assert active object identity and
  usability after every invalid candidate.
- **High risk — lifetime leaks**: an application-held completed/released handoff could
  retain a whole generation, or a worker binding could remain installed after failure,
  cancellation, timeout, or cutoff. Use explicit holder/scope/lifecycle assertions;
  do not use sleeps, `System.gc()`, weak-reference collection, or timing as proof.
- **High risk — concurrency propagation**: nested planning and parallel forks could omit
  the generation or consult active state after activation. Use latches/barriers to prove
  exact object identity and old-child execution while new roots observe the replacement.
- **Medium risk — fixed dependency drift**: candidate preparation must not rediscover
  Java skills or REST handler beans, yet a later REST addition must validate against the
  fixed startup handler candidate set.
- **Medium risk — source independence**: activation must use captured definitions/source
  bytes even when files are edited, deleted, or reordered after prepare.
- **Medium risk — catalog authority**: public and observability snapshots must correspond
  to one generation, remain immutable after activation, and never authorize historical
  execution.
- **Medium risk — internal no-shim migration**: removing registries/registrar and unused
  step-engine constructor arguments touches many tests. Updated tests must not preserve
  old and new authorities simultaneously.
- **Protected compatibility paths**: the fifteen allowlisted application API types,
  `RestSkillHandler` SPI, existing YAML/properties, exact validation diagnostics where
  asserted, injected startup `SkillCatalog`, supported facade behavior, authentication/
  authorization, and Console serialized fixture corpus.
- **Intentionally removed obsolete paths**: additive global capability registration,
  singleton YAML runtime lookup, registrar-triggered discovery, no-generation runner/
  binding constructors, and unused step-engine registry/catalog constructor state. The
  ticket's `Pipeline notes` authorize removal without shims.

## Existing Test Coverage

- `YamlSkillCatalogTests` covers configured resource discovery, empty/missing locations,
  duplicates, manifest shape, schema/model/evidence/linter/concurrency validation, and
  captured sources.
- `YamlSkillCapabilityRegistrarTests` covers the behavior to migrate into generation
  assembly tests: lazy Java discovery, cross-source collisions, exact child references,
  REST handler cardinality, REST invocation, and legacy mapping rejection.
- `DefaultSkillCatalogTest` and `DefaultRegisteredSkillCatalogTest` cover eager immutable
  projections, all capability kinds, sorting, exact lookup, schemas, source bytes, and
  Java diagnostic locations.
- `DefaultSkillTemplateTest` covers root input normalization/validation, handoff-captured
  normalized input and authentication, single-use execution, release, and cutoff.
- `ExecutionBindingTest`, `MissionWorkExecutor` tests, and
  `StepLoopMissionExecutionEngineTest` cover binding installation/restoration, mission
  derivation, worker capture, forked branches, cancellation, timeout, and cutoff.
- `ExecutionCoordinatorTest`, `SkillVisibilityResolverTest`, and
  `ConcurrentGroupedExecutionIntegrationTest` cover nested/direct/planning dispatch,
  local child visibility, policy filtering, parallel work, caller identity, and mission
  isolation.
- `FrameworkExecutionLifecycleTest` and `FrameworkShutdownIntegrationTest` cover root
  admission, single claim, pending release, executing completion, cutoff, and shutdown.
- `JavaSkillAuthorizationIntegrationTests`, `JavaSkillAuthenticationScopeIntegrationTests`,
  and `JavaSkillSecurityStartupTests` cover Spring proxy/prefix/hierarchy policy and
  worker-thread authentication restoration.
- `LoomspanAutoConfigurationTests`, `SupportedSurfaceIntegrationTest`,
  `LoomspanAutoConfigurationBoundaryTest`, and
  `LoomspanPublicSurfaceArchitectureTest` cover bean composition and supported surface.
- Console fixture corpus tests protect the unchanged Java-to-Go/TypeScript protocol
  shapes.

### Coverage Gaps

- No test can currently prepare a detached replacement, assign or retain its identity,
  atomically activate it, or prove invalid preparation leaves live state intact.
- No invocation carrier records the selected declaration universe, so no test proves
  one root retains coherent state across activation.
- No test covers additions/removals/empty replacement sets or activation after source
  mutation/deletion.
- No deterministic ownership test proves a terminal application-held handoff no longer
  retains its prepared execution state.
- No test exercises three or more simultaneously legitimate generation owners.

## Bug Reproduction / Failing Test First

- **Name**: `capturedHandoffUsesOneGenerationAfterActivationAndNewRootUsesReplacement`
- **Type**: integration
- **Location**:
  `src/test/java/ai/loomspan/internal/skill/SkillGenerationExecutionIntegrationTest.java`
- **Arrange/Act/Assert outline**:
  1. Prepare/activate generation A with parent `planner`, child `oldChild`, an A-specific
     parent input contract, role policy, and execution setting.
  2. Create an admitted handoff for `planner`; this completes generation capture and
     input validation but intentionally delays root execution.
  3. Prepare/activate generation B with a changed parent contract/policy/setting and with
     `oldChild` removed (optionally adding `newChild`).
  4. Invoke the admitted A handoff under authorized authentication and make its model/tool
     path call `oldChild`. Assert the call succeeds using A's definition, visible child,
     policy, input contract, and execution configuration.
  5. Invoke a new root and assert it uses B: it rejects A-only input/visibility and accepts
     B's contract/child/policy.
- **Expected failure (pre-fix)**: The current checkout has no detached candidate or atomic
  activation seam, and execution rereads singleton registry/catalog state. Once the
  minimal preparation seam is introduced, the old handoff would either resolve the
  removed child/definition from B or require mutation of the single registry, proving
  the coherence defect before binding propagation is implemented.

This is the primary red test because it crosses the earliest capture boundary, delayed
admission, execution lookup, child visibility, and new-root semantics in one controlled
scenario. Lower-level assembly tests should be added first as scaffolding, but this test
must remain red until Phase 2 is complete.

## Tests to Add/Update

### 1) `preparationsAreFreshImmutableCompleteGenerations`
- **Type**: unit
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`
- **What it proves**: Two valid preparations of byte-identical YAML receive distinct
  nonblank IDs; each contains fixed Java plus complete YAML/REST metadata; activation
  retains the exact candidate object and ID rather than rebuilding it.
- **Fixtures/data**: Temporary configured YAML directory, one Java capability snapshot,
  fixed REST handler candidate, deterministic schema/input resolver.
- **Mocks**: Resource resolver/codecs as existing catalog tests use; inject an ID supplier
  only if the production design already needs one for deterministic collision testing,
  otherwise assert UUID inequality without mocking randomness.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Intentional removal of the additive singleton registry;
  no protected compatibility path for internal constructors.

### 2) `invalidCandidateNeverChangesActiveGeneration`
- **Type**: unit / parameterized
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`
- **What it proves**: Duplicate YAML names, Java/YAML collisions, malformed schema,
  unknown model, unresolved child after removal, and zero/multiple REST handlers for a
  REST candidate all fail preparation while the previously active generation and its
  catalog snapshots remain identical and usable.
- **Fixtures/data**: Valid active generation plus one invalid complete candidate per
  validation family; reuse existing invalid YAML fixtures where practical.
- **Mocks**: Fixed Java/handler snapshot; no live registry.
- **Affected surface**: Configuration or manifest behavior / Internal implementation.
- **Compatibility expectation**: Protected validation and useful diagnostics; detached
  invalid state is discarded.

### 3) `supportsWholeSetAdditionRemovalAndEmptyYaml`
- **Type**: unit
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`
- **What it proves**: Complete additions, edits, renames, removals, and an empty YAML set
  work while fixed Java declarations remain. A remaining exact reference to a removed
  child rejects the whole candidate.
- **Fixtures/data**: Successive temp-directory manifest sets and fixed Java declarations.
- **Mocks**: None beyond fixed collaborators.
- **Affected surface**: Configuration or manifest behavior.
- **Compatibility expectation**: Protected manifest semantics applied to each candidate;
  atomic replacement is the new intended behavior.

### 4) `preparedGenerationDoesNotRereadChangedOrDeletedSources`
- **Type**: integration-style unit
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`
- **What it proves**: After prepare returns, edit then delete the source files; activation,
  lookup, original source text, execution settings, and both discovery projections still
  match captured candidate data.
- **Fixtures/data**: `@TempDir` filesystem YAML and a candidate snapshot retained before
  mutation.
- **Mocks**: None; use the real resource resolver and YAML mapper.
- **Affected surface**: Configuration or manifest behavior / Internal implementation.
- **Compatibility expectation**: New candidate immutability requirement.

### 5) `fixedDependenciesAreCapturedOnceAcrossCandidates`
- **Type**: unit / Spring integration
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`
  and migrated `SkillMethodBeanPostProcessor` focused tests.
- **What it proves**: Lazy Java declarations are discovered/proxy-verified once; later
  candidates reuse the same Java invoker/policy/schema objects and fixed REST handler
  candidate set. A later REST addition succeeds with the one startup handler, while no
  initial REST manifest does not itself reject zero/multiple handlers.
- **Fixtures/data**: Counting bean factory/probe beans, REST-free initial set, later REST
  candidate, secured Java method.
- **Mocks**: Spy/counting bean factory where a real context is unnecessary; retain a real
  Spring context for proxy enforcement.
- **Affected surface**: Supported SPI / Internal implementation.
- **Compatibility expectation**: Preserve `RestSkillHandler` cardinality and Java
  declaration behavior; no bean rediscovery or new SPI.

### 6) `generationCatalogsAreCoherentImmutableDiscoveryOnly`
- **Type**: unit
- **Location**:
  `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillCatalogTest.java` and
  `src/test/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalogTest.java`
- **What it proves**: Both projections are built from the same candidate, remain sorted
  and immutable, preserve exact schemas/source/Java locations, and do not change after
  another generation activates. Holding a snapshot gives no manager operation to invoke
  the historical generation.
- **Fixtures/data**: Java, YAML, and REST metadata in generations A/B.
- **Mocks**: None.
- **Affected surface**: Application API / Persisted or serialized behavior / Internal
  diagnostics.
- **Compatibility expectation**: Protected immutable snapshot behavior and unchanged
  serialized fields; public generation accessor remains absent.

### 7) `capturePrecedesObjectConversionAndInputValidation`
- **Type**: unit with controlled concurrency
- **Location**: `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves**: Both object and map overloads read active generation first. A
  blocking converter/`SkillInputValidator` coordinates activation of B after capture but
  before validation; validation still uses A's capability contract and policy.
- **Fixtures/data**: Generations with mutually exclusive required fields/policies;
  `CountDownLatch` or barrier with bounded awaits.
- **Mocks**: Test subclass/spies for converter or `SkillInputValidator`; no sleeps.
- **Affected surface**: Application API.
- **Compatibility expectation**: Protected facade behavior strengthened with coherent
  generation capture.

### 8) `bindingPreservesGenerationAcrossMissionAndForks`
- **Type**: unit
- **Location**: `src/test/java/ai/loomspan/internal/core/ExecutionBindingTest.java`
- **What it proves**: Root, `withMission`, worker capture, and every `forkBranch` preserve
  exact generation object identity; different-session checks remain; scope restoration
  removes the binding on normal and exceptional exits.
- **Fixtures/data**: Two generation fixtures, session, mission, and physical branch.
- **Mocks**: None; add a shared `TestSkillGenerations` fixture builder so all binding
  tests construct explicit minimal immutable generations consistently.
- **Affected surface**: Internal implementation.
- **Compatibility expectation**: Intentional removal of no-generation internal binding
  construction.

### 9) `routerRejectsCapabilityFromDifferentGeneration`
- **Type**: unit
- **Location**: `src/test/java/ai/loomspan/internal/core/CapabilityExecutionRouterTest.java`
- **What it proves**: The router/coordinator accepts only the exact capability owned by
  the bound generation, still validates input and checks access, and visibly rejects
  metadata from an older/newer generation even when the name matches.
- **Fixtures/data**: Generations A/B with same name but distinct metadata/policy.
- **Mocks**: Existing coordinator provider/access guard/input-validator fakes.
- **Affected surface**: Internal implementation / security boundary.
- **Compatibility expectation**: Protected authorization; current-generation coherence.

### 10) `oldTreeUsesRemovedChildWhileNewRootUsesReplacement`
- **Type**: integration
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationExecutionIntegrationTest.java`
- **What it proves**: Primary red scenario plus direct nested execution: generation A
  work invokes a child removed from active B, while a new B root cannot discover or invoke
  it and can see B's addition.
- **Fixtures/data**: Controllable model interaction/tool calls, two manifest sets, fixed
  Java leaf where useful.
- **Mocks**: Scripted model interactions consistent with existing coordinator tests.
- **Affected surface**: Configuration or manifest behavior / Internal implementation.
- **Compatibility expectation**: Captured tree protected; obsolete global lookup removed.

### 11) `manifestPolicyChangeAffectsNewRootsWithoutBypassingAuthorization`
- **Type**: integration
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationAuthorizationIntegrationTest.java`
- **What it proves**: An admitted/running A tree keeps A's YAML/REST role policy and local
  visibility after B activates; a new root uses B's changed policy. Both paths evaluate
  actual captured caller authentication, prefix/hierarchy rules, and execution-time
  authorization; denied methods never run.
- **Fixtures/data**: A/B role policies, authorized/unauthorized authentications, secured
  Java/REST child probes.
- **Mocks**: Real Spring Security context where prefix/hierarchy/proxy behavior matters.
- **Affected surface**: Application API / Supported SPI / Configuration or manifest
  behavior.
- **Compatibility expectation**: Protected authorization paths; no model-selected or
  globally substituted identity.

### 12) `nestedAndParallelWorkRetainsCapturedGeneration`
- **Type**: integration / controlled concurrency
- **Location**:
  `src/test/java/ai/loomspan/internal/runtime/step/ConcurrentGroupedExecutionIntegrationTest.java`
- **What it proves**: Direct child, nested planner, both concurrent group workers, and a
  later unit observe the exact root generation despite B/C activations while blocked.
  Definitions, visible tools, and execution settings remain A; new independent roots
  observe the latest active generation.
- **Fixtures/data**: Three generations and existing scripted planning/direct model setup;
  latches for root start, worker start, activation, and release with bounded awaits.
- **Mocks**: Existing model/executor test doubles; no arbitrary sleeps.
- **Affected surface**: Configuration or manifest behavior / Internal implementation.
- **Compatibility expectation**: Current-run coherent execution; existing planning order,
  cancellation, authentication, and state isolation preserved.

### 13) `handoffClearsRetainedGenerationOnEveryTerminalPath`
- **Type**: unit / concurrency
- **Location**:
  `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`,
  a package-private payload-holder test if extracted, and
  `src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- **What it proves**: Successful invocation, execution failure, completion failure,
  explicit release, shutdown cutoff, and concurrent invoke/release each clear the
  admitted payload exactly once; running work keeps its local/bound generation until it
  exits; admission maps and binding scope are empty afterwards.
- **Fixtures/data**: Generation A, admitted root, throwing/blocking actions, latches and
  bounded joins.
- **Mocks**: Lifecycle/session collaborators as current tests use.
- **Affected surface**: Application API / Internal lifecycle.
- **Compatibility expectation**: Protected single-use/release behavior plus new
  deterministic ownership release. No GC-based assertion.

### 14) `threeOrMoreLegitimateGenerationsCoexistWithoutEviction`
- **Type**: integration
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationExecutionIntegrationTest.java`
- **What it proves**: Generation A remains held by blocked running work, B by an admitted
  handoff, C by a prepared candidate owner, and D as active. Each legitimate owner sees
  its own immutable contents; releasing/completing A/B removes framework ownership
  without a later prepare call, while C/D remain valid.
- **Fixtures/data**: Four small distinguishable manifest sets, controlled executors and
  latches, package-visible holder state/scope/lifecycle observations.
- **Mocks**: Scripted execution only; no reference queue, weak reference, `System.gc()`,
  or collection timeout.
- **Affected surface**: Internal lifecycle / Application handoff behavior.
- **Compatibility expectation**: New ownership requirement; no archive or fixed
  generation limit.

### 15) `startupAndAutoConfigurationUseInitialGeneration`
- **Type**: Spring integration
- **Location**: `src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`
- **What it proves**: Valid startup exposes supported facade/catalog beans backed by the
  initial active generation; invalid initial declarations fail; Java-only and no-YAML
  contexts work; obsolete registry/catalog/registrar beans are absent; no conditional
  replacement SPI appears.
- **Fixtures/data**: Existing application context runners and startup fixtures migrated
  to generation assertions.
- **Mocks**: Existing test configurations.
- **Affected surface**: Application API / Configuration or manifest behavior / Internal
  Spring integration.
- **Compatibility expectation**: Protected startup behavior; intentional removal of
  internal beans.

### 16) `publicSurfaceRemainsClosedAfterGenerationRefactor`
- **Type**: architecture
- **Location**:
  `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` and
  `LoomspanAutoConfigurationBoundaryTest.java`
- **What it proves**: The application API allowlist is unchanged, `RestSkillHandler`
  remains the only SPI, no internal/autoconfigure generation type leaks into supported
  signatures, and new infrastructure beans are classified as integration machinery only.
- **Fixtures/data**: Compiled production classes.
- **Mocks**: None.
- **Affected surface**: Application API / Supported SPI / Internal implementation.
- **Compatibility expectation**: Protected path; zero public-surface delta.

### 17) `currentDiagnosticProtocolsRemainUnchanged`
- **Type**: fixture corpus / integration
- **Location**: Existing Console REST fixture, trace contract, and observability catalog
  tests, including `ConsoleRestFixtureCorpusTest` and `ConsoleTraceFixtureCorpusTest`.
- **What it proves**: No generation field or compatibility-marker change leaks into PR
  6.1 REST/SSE/NDJSON/trace payloads; discovery still returns the same field shapes and
  source content.
- **Fixtures/data**: Existing checked-in fixture corpus; updated construction only where
  internal test setup changes.
- **Mocks**: Existing fixtures/fakes.
- **Affected surface**: Persisted or serialized behavior / Ephemeral diagnostics.
- **Compatibility expectation**: Protected unchanged protocol; current-run coherence only.

### 18) `authoringGuidanceMatchesExecutableGenerationSemantics`
- **Type**: documentation verification / source review backed by focused tests
- **Location**: Routed documents listed in the implementation plan and their cited tests.
- **What it proves**: Each exact author-facing claim maps to a production path and focused
  test; docs distinguish generation-stable manifests/settings from live authentication,
  and do not claim a public reload workflow, historical invocation, or retirement signal.
- **Fixtures/data**: README routing/coverage table and stable implementation anchors.
- **Mocks**: None.
- **Affected surface**: Configuration or manifest behavior / Application guidance.
- **Compatibility expectation**: Aligned documentation for new behavior; PR 6.2 scope
  remains absent.

## Tests to Remove or Rewrite

- Replace `InMemoryCapabilityRegistryTest` with generation-map/assembly collision and
  immutability tests; do not retain tests for additive live registration.
- Migrate `YamlSkillCapabilityRegistrarTests` into generation assembly/manager tests and
  delete expectations that registration mutates a singleton before child validation.
- Rewrite test constructors/factories that instantiate `ExecutionBinding`,
  `LoomspanSessionRunner`, `ExecutionCoordinator`, visibility, or the step engine so they
  require one explicit generation. Do not add compatibility constructors for tests.
- Update auto-configuration bean-count assertions to remove `CapabilityRegistry`,
  singleton `YamlSkillCatalog`, and registrar expectations and assert the generation
  manager instead.
- Preserve tests for protected external behavior even when their setup changes; do not
  retain simultaneous old/new internal paths simply to keep old test helpers compiling.

## How to Run

All commands run from the repository root on Windows PowerShell.

1. Establish the primary red test after the Phase 1 preparation seam exists:

   `.\mvnw.cmd -Dtest=SkillGenerationExecutionIntegrationTest#capturedHandoffUsesOneGenerationAfterActivationAndNewRootUsesReplacement test`

2. Run focused generation assembly, facade, binding, lifecycle, execution,
   authorization, discovery, startup, and architecture coverage:

   `.\mvnw.cmd -Dtest=SkillGenerationManagerTest,SkillGenerationExecutionIntegrationTest,SkillGenerationAuthorizationIntegrationTest,DefaultSkillTemplateTest,ExecutionBindingTest,CapabilityExecutionRouterTest,ExecutionCoordinatorTest,SkillVisibilityResolverTest,FrameworkExecutionLifecycleTest,ConcurrentGroupedExecutionIntegrationTest,DefaultSkillCatalogTest,DefaultRegisteredSkillCatalogTest,LoomspanAutoConfigurationTests,SupportedSurfaceIntegrationTest,LoomspanAutoConfigurationBoundaryTest,LoomspanPublicSurfaceArchitectureTest test`

3. Run current serialized-diagnostic regression suites explicitly:

   `.\mvnw.cmd -Dtest=ConsoleRestFixtureCorpusTest,ConsoleTraceFixtureCorpusTest,ExecutionTraceContractTest test`

4. Run the required public-surface architecture test independently for a clear receipt:

   `.\mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test`

5. Run full repository verification:

   `.\mvnw.cmd verify`

No special profile, external service, environment variable, network dependency, or
production test data should be required. Concurrency tests must use the repository's
controlled executors, latches/barriers, and bounded awaits.

## Exit Criteria

- [ ] The primary failing test is committed red before Phase 2 and fails for mixed/
  missing generation propagation, then passes after the implementation.
- [ ] Every successful complete preparation receives a fresh nonblank ID, including
  byte-identical YAML; activation retains the candidate's exact object, ID, definitions,
  metadata, and discovery contents.
- [ ] Invalid startup fails with useful diagnostics, and every invalid replacement case
  leaves the active generation and its supported operations unchanged.
- [ ] Complete additions, edits, removals, renames, and empty YAML sets work against fixed
  Java/model/connection/handler dependencies; unresolved remaining children fail.
- [ ] File edits/deletion after preparation cannot affect candidate activation or either
  discovery projection.
- [ ] Capture demonstrably occurs before object conversion/input validation for every
  public facade/handoff overload.
- [ ] Input preparation, delayed admission, root execution, nested direct/planning calls,
  and parallel forks use the exact same generation for schema, definition, child
  visibility, capability policy, and execution settings.
- [ ] New roots use the latest active generation while older trees can still execute
  children removed from that active generation.
- [ ] Existing authentication and authorization checks still run at validation,
  visibility, and execution boundaries; policy changes affect only newly captured roots,
  and denied application code does not execute.
- [ ] Success, failure, cancellation, cutoff, and handoff release clear unnecessary
  framework ownership without a later prepare call, demonstrated by explicit payload,
  lifecycle, and scope state rather than GC timing.
- [ ] Three or more legitimate generations coexist without forced eviction, an archive,
  a previous-generation chain, or a two-generation cap.
- [ ] Existing catalog snapshots remain immutable/coherent and do not authorize historical
  invocation.
- [ ] Removed registries/registrar/no-generation constructors are absent rather than
  retained behind fallbacks or dual behavior.
- [ ] Updated authoring claims are supported by cited production paths and focused tests;
  README routing/coverage remains LLM-first and public reload/retirement promises remain
  deferred.
- [ ] `LoomspanPublicSurfaceArchitectureTest` confirms no application API or SPI delta and
  no internal/autoconfigure type leak.
- [ ] Existing Console protocol fixtures pass unchanged; no generation field or
  compatibility-marker delta is introduced.
- [ ] All focused commands and `.\mvnw.cmd verify` pass.
- [ ] No non-automatable developer observation is required for completion.
