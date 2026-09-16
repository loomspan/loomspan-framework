---
date: 2026-09-15T22:29:48-07:00
researcher: matt-loomspan-ai
git_commit: d61c4d2dc5078784ca19d4934b67d66007d5f5ea
branch: main
repository: loomspan-framework
topic: "PR 6.1 — Execute against coherent skill generations"
tags: [research, codebase, skill-generations, catalog, execution, authorization, lifecycle]
status: complete
last_updated: 2026-09-15
last_updated_by: matt-loomspan-ai
---

# Research: PR 6.1 — Execute against coherent skill generations

**Date**: 2026-09-15 22:29:48 PDT
**Researcher**: matt-loomspan-ai
**Model**: GPT-5
**Git Commit**: d61c4d2dc5078784ca19d4934b67d66007d5f5ea
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current loading, registration, discovery, invocation, authorization,
concurrency, and framework-ownership paths that PR 6.1 must bring into coherent,
immutable skill generations. The requested outcome includes an initial generation,
whole-candidate preparation and validation, internal atomic activation, generation
capture before input validation, inheritance across delayed/nested/parallel work,
and release of framework-owned references without exposing PR 6.2's public reload
or generation-metadata API.

## Summary

The current runtime has one startup-built skill universe, represented across two
mutable construction objects and several later immutable snapshots. `YamlSkillCatalog`
discovers and parses configured YAML resources into defensive `YamlSkillDefinition`
values. `SkillMethodBeanPostProcessor` independently discovers Java declarations and
registers their `CapabilityMetadata` directly into `InMemoryCapabilityRegistry`.
`YamlSkillCapabilityRegistrar` then completes Java discovery, resolves the REST handler,
adds YAML/REST metadata to the same registry, and validates exact child references.
There is no candidate object, generation ID, active-generation reference, publication
operation, removal path, or generation archive today.

Execution reads this startup state at more than one lifecycle boundary. Root input
preparation looks up capability metadata before session admission; a delayed handoff
retains only prepared capability/input/authentication plus an admitted root; root and
nested execution look up definitions and metadata again through singleton catalog and
registry fields; child visibility combines both singleton sources and applies access
policy filtering. Once a mission has been assembled, its definition, visible bound
capabilities, execution configuration, and access policies are passed through direct,
planning, nested, and parallel execution. `ExecutionBinding` is the existing immutable
identity carrier copied into executor work and forked branches, while
`FrameworkExecutionLifecycle.AdmittedRoot` is the existing single-use admission owner
with explicit release, completion, and cutoff transitions.

The supported Java surface is a closed fifteen-type allowlist. `SkillCatalog`,
`SkillTemplate`, `SkillInvocationHandoff`, and `AdmittedSkillInvocation` are Application
API; `RestSkillHandler` is the sole Supported SPI. The YAML manifest and `loomspan.*`
properties are configuration and manifest contracts. The registries, catalogs,
definitions, execution bindings, lifecycle classes, Spring beans, and observability
catalogs are classified internal or Spring integration machinery. PR 6.1 explicitly
defers public reload operations, public catalog generation identity, REST invocation
generation metadata, and execution diagnostics to PR 6.2.

## Detailed Findings

### 1. Startup discovery and declaration validation

#### YAML and REST resource loading

- `LoomspanAutoConfiguration` creates a singleton `YamlSkillCatalog` from the configured
  `LoomspanProperties`, a resource pattern resolver, and the skill YAML mapper
  (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:171-177`).
- `YamlSkillCatalog.afterPropertiesSet()` clears its two internal linked maps, discovers
  every configured resource, loads each definition, and rejects duplicate YAML names
  while preserving deterministic resource-description ordering
  (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-115`,
  `:133-162`). Missing classpath roots and patterns with no matches produce an empty
  YAML set (`:137-157`), behavior protected by
  `YamlSkillCatalogTests#loadsNoSkillsWhenConfiguredClasspathRootIsMissing` and
  `#loadsNoSkillsWhenClasspathRootExistsButHasNoYamlMatches`.
- Loading reads each resource fully into bytes before parsing. The returned definition
  stores the parsed manifest, resolved execution configuration, evidence contract,
  compiled allowed-skill constraints, original resource, location pattern, and the
  captured bytes (`YamlSkillCatalog.java:164-227`). `YamlSkillSource` clones input and
  output byte arrays (`src/main/java/ai/loomspan/internal/skill/YamlSkillSource.java:13-37`),
  so the original manifest text already has an immutable in-memory representation.
- Model-backed declarations resolve their framework model, connection, driver, provider
  model, and effective thinking level during load. REST declarations reject model,
  prompt, planning, allowed-child, linter, and output-schema fields and have no execution
  configuration (`YamlSkillCatalog.java:181-227`).
- Manifest validation is concentrated in `YamlSkillCatalog`: exact public names,
  required fields, unknown fields, REST declaration shape, allowed-child entries,
  planning concurrency, model references, input/output schemas, evidence, and linter
  configuration all fail during definition loading with resource and field diagnostics
  (`YamlSkillCatalog.java:255-305`, `:326-470`, `:481-1017`).
- `YamlSkillDefinition` defensively copies its manifest at construction and on every
  `manifest()` access, copies allowed-child constraints, and returns copies of nested
  linter/output/input schema data (`src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java:14-52`,
  `:78-135`, `:169-190`). The catalog returns copied lists but is internally mutable
  during `afterPropertiesSet()` (`YamlSkillCatalog.java:117-130`).

#### Fixed Java declarations

- `SkillMethodBeanPostProcessor` discovers `@SkillMethod` declarations from managed
  Spring beans, canonicalizes proxy/bridge/interface methods, generates the reflected
  input schema, resolves JSR-250 access policy, creates the Java `CapabilityMetadata`,
  and immediately registers it in the shared registry
  (`src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:43-56`,
  `:161-219`).
- `completeDiscovery()` initializes relevant lazy beans and verifies the final proxy's
  JSR-250 enforcement before YAML registration completes
  (`SkillMethodBeanPostProcessor.java:58-91`). The current object retains declaration
  records for verification but exposes no immutable Java-declaration snapshot or
  rediscovery API.
- `YamlSkillCapabilityRegistrar.completeRegistration()` invokes `completeDiscovery()`
  first, which establishes the current ordering: fixed Java declarations exist before
  YAML/REST cross-source collision and child-reference validation
  (`src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:37-44`).

#### Shared capability registration and cross-source validation

- `InMemoryCapabilityRegistry` is a concurrent, additive name-to-metadata map. Registration
  validates exact name shape and uses `putIfAbsent`; it has no replace, remove, copy,
  candidate, or atomic whole-map activation operation
  (`src/main/java/ai/loomspan/internal/core/InMemoryCapabilityRegistry.java:8-49`).
- For every YAML definition, `YamlSkillCapabilityRegistrar` resolves the input contract,
  creates `CapabilityMetadata` containing kind, execution settings, access policy,
  invoker, tool schema, input contract, and source metadata, and registers it in the
  shared registry (`YamlSkillCapabilityRegistrar.java:41-60`). Model-backed invokers are
  placeholders because model execution is routed by the coordinator; REST invokers
  capture the selected handler and exact skill name (`:51-59`, `:87-91`).
- Exact child-reference validation happens only after all Java, YAML, and REST metadata
  has been added. Each `allowed_skills` name must resolve in the same registry
  (`YamlSkillCapabilityRegistrar.java:61-66`). This is the current whole-declaration
  validation boundary for cross-source references.
- The current registrar mutates the single registry before performing the final child
  loop. That is safe for startup because a failure aborts context creation; there is no
  current replacement path whose failure would need to preserve an active registry.
- REST handler discovery queries the bean factory only when the loaded set contains at
  least one REST definition. It requires exactly one `RestSkillHandler` bean and captures
  that bean in each REST invoker; zero or multiple handlers fail with declaration/resource
  diagnostics (`YamlSkillCapabilityRegistrar.java:41-44`, `:69-91`). With no REST
  manifests, handler beans do not trigger cardinality validation, as protected by
  `YamlSkillCapabilityRegistrarTests#requiresExactlyOneHandlerOnlyWhenRestManifestsExist`
  (`src/test/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrarTests.java:150-162`).
- Focused startup tests already cover lazy Java discovery, duplicate Java and cross-source
  names, exact unresolved children, REST collisions, REST handler cardinality, REST
  invocation, and legacy mapping rejection
  (`YamlSkillCapabilityRegistrarTests.java:20-55`, `:87-180`).

### 2. Current snapshots and discovery data

- The public `SkillCatalog` contract describes an eager, immutable, exact-name-sorted,
  unfiltered snapshot; discovery itself does not authorize execution
  (`src/main/java/ai/loomspan/api/SkillCatalog.java:6-15`).
- `DefaultSkillCatalog` forces registration completion, iterates all capability metadata,
  maps it to public `SkillDescriptor` values, and freezes a sorted navigable map and
  copied list (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:15-49`).
  It does not retain the source registry or YAML catalog after construction.
- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`
  protects exact schemas, all three skill kinds, exact lookup, sorting, and list
  immutability (`src/test/java/ai/loomspan/internal/skillapi/DefaultSkillCatalogTest.java:23-50`).
- The observability layer builds a separate immutable `DefaultRegisteredSkillCatalog`
  at route activation. It joins `CapabilityRegistry` metadata with `YamlSkillCatalog`
  definitions, captures Java bean/method locations, and decodes the already captured
  YAML source bytes (`src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:19-53`,
  `:81-94`). `ObservabilityRouteRegistrar` constructs that object once while activating
  the observability runtime (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:136-160`).
- The public catalog and internal observability catalog are therefore two independent
  immutable projections of the startup registry/catalog pair. Neither has generation
  identity, and neither is linked to an active-state owner.
- README consumer guidance explicitly calls `SkillCatalog` a snapshot, not a refresh or
  bean-replacement contract (`README.md:141`). The initially injected bean is created
  directly from the startup registry and registrar
  (`LoomspanAutoConfiguration.java:210-215`).

### 3. Root validation, invocation, and delayed handoff

- `DefaultSkillTemplate.prepareMap()` performs capability lookup first, then selects the
  capability's input contract, normalizes null rules, validates and deeply normalizes
  input, and returns a `PreparedInput` holding the selected `CapabilityMetadata` and
  validation result (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:133-157`,
  `:245-285`). Object input is converted to a map and follows the same path (`:113-131`).
- Standalone `validate` calls preparation and then checks the prepared capability's
  access policy against the current authentication; it creates no session or admission
  (`DefaultSkillTemplate.java:77-87`, `:159-174`).
- Direct `invoke` also prepares before it asks `LoomspanSessionRunner` to create/admit a
  root session (`DefaultSkillTemplate.java:90-110`, `:176-217`). Thus the lookup/input
  contract selected during preparation and the singleton structures later read by
  execution are separated by a lifecycle boundary even in the synchronous facade.
- `DefaultSkillInvocationHandoff` prepares input before calling
  `FrameworkExecutionLifecycle.admitRoot()`, captures the current `Authentication`, and
  returns a single-use object that strongly retains skill name, prepared capability and
  validation data, authentication, and admitted root
  (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:27-73`).
  Invocation later passes those retained values to the template; `release()` currently
  closes only the admitted root and does not clear the handle's other final fields
  (`:75-91`).
- `DefaultSkillTemplateTest#handoffCapturesPreparedInputAndCallingAuthentication`
  demonstrates that normalized input and authentication survive caller mutation/context
  changes and that the admission executes once
  (`src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:57-101`).
  `#releasedAndCutOffHandoffsCannotExecute` protects release/cutoff behavior (`:103-130`).
- `FrameworkExecutionLifecycle.AdmittedRoot` moves through `PENDING`, `EXECUTING`,
  `RELEASED`, or `CUT_OFF`. Release removes pending roots; execution completion removes
  executing roots; shutdown cutoff removes pending roots and signals registered missions
  (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:55-98`,
  `:202-224`, `:259-310`). This is the existing framework owner for delayed admission
  lifetime and its explicit terminal paths.
- `LoomspanSessionRunner.executeAdmittedRoot()` claims the root, creates the session,
  installs a session-only `ExecutionBinding`, performs the action and session completion,
  runs the completion callback after binding restoration, and always completes the root
  in `finally` (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:228-299`).

### 4. Root, nested, planning, and parallel execution dataflow

- `ExecutionCoordinator.execute()` currently looks up capability metadata from the
  singleton registry and, for model YAML, separately looks up its definition from the
  singleton YAML catalog (`src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:77-103`,
  `:465-488`). A nested tool call re-enters the same method through
  `CapabilityExecutionRouter`, so nested lookup is also against those singleton fields
  (`src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:39-69`).
- The coordinator creates a child `MissionContext` under the current mission and installs
  a derived immutable `ExecutionBinding` around all work (`ExecutionCoordinator.java:87-103`).
  `ExecutionBinding` contains session, current mission, and physical branch; `withMission`
  and `forkBranch` preserve the other identities
  (`src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:9-42`).
- `ExecutionBindingScope` is the sole ambient execution holder. It installs/restores the
  immutable binding in a `ThreadLocal`, rejects cross-session nesting, and removes the
  value in `finally` (`src/main/java/ai/loomspan/internal/core/ExecutionBindingScope.java:8-77`).
- After lookup, model execution passes the selected definition through model interaction
  construction, visible-tool binding, planning/direct execution, input materialization,
  output validation, and finalization (`ExecutionCoordinator.java:147-166`).
  `SpringAiChatClientAssembler` consumes that same definition for model connection,
  options, linter, output-schema, and evidence advisors
  (`src/main/java/ai/loomspan/internal/springai/SpringAiChatClientAssembler.java:68-103`).
- Child visibility is resolved by reading the current definition from `YamlSkillCatalog`,
  resolving each exact allowed name from `CapabilityRegistry`, and filtering metadata
  through `AccessGuard` (`src/main/java/ai/loomspan/internal/skill/DefaultSkillVisibilityResolver.java:27-54`).
  `DefaultToolSurfaceService` delegates directly to it
  (`src/main/java/ai/loomspan/internal/runtime/tool/DefaultToolSurfaceService.java:12-25`).
- `DefaultCapabilityInvoker.bind()` closes over the selected parent definition, each
  visible child's complete `CapabilityMetadata`, the session, and authentication.
  Invocation routes that exact child metadata through input validation and authorization
  before the coordinator performs its name-based nested lookup
  (`src/main/java/ai/loomspan/internal/runtime/tool/DefaultCapabilityInvoker.java:57-80`,
  `:100-133`).
- `MissionWorkExecutor` captures the current `ExecutionBinding`, installs it on its worker
  thread, and retains it through normal completion, timeout, interruption, cancellation,
  and cutoff cleanup (`src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-116`).
- The planning engine likewise captures the binding for the owning mission
  (`src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:242-281`).
  Concurrent plan members use `coordinatorBinding.forkBranch()` and install each worker
  binding around the assigned task (`:428-470`, `:600-625`). This is the current explicit
  propagation path for nested and parallel execution identity.
- The planning engine receives and passes the already selected definition and immutable
  visible bound-capability list. Its stored `capabilityRegistry` field is annotated unused,
  and its nullable `yamlSkillCatalog` field is assigned by several constructors but has
  no read call in the class (`StepLoopMissionExecutionEngine.java:92-104`, `:106-221`).
  These constructor variants are internal technical exposure, not supported API.

### 5. Authorization and manifest-policy ownership

- Java policies are resolved once into `CapabilityMetadata` during Java discovery;
  YAML/REST roles are converted into `SkillAccessPolicy.yamlRoles` during YAML capability
  registration (`SkillMethodBeanPostProcessor.java:202-218`;
  `YamlSkillCapabilityRegistrar.java:51-59`). Access policy therefore travels with the
  capability metadata selected from the registry.
- Root validation checks the prepared metadata's policy (`DefaultSkillTemplate.java:159-174`).
  Root and nested execution check access again through `CapabilityExecutionRouter` and
  `ExecutionCoordinator` (`CapabilityExecutionRouter.java:39-60`;
  `ExecutionCoordinator.java:132-145`).
- Visibility filtering is not treated as authorization: declared allowed children are
  first resolved, then `AccessGuard.canAccess` filters them for the invocation
  (`DefaultSkillVisibilityResolver.java:37-53`). `SkillVisibilityResolverTest` protects
  allowlist restriction, missing-auth hiding, and session-auth fallback
  (`src/test/java/ai/loomspan/internal/skill/SkillVisibilityResolverTest.java:28-109`).
- `DefaultAccessGuard` gives explicit invocation authentication priority over the session
  fallback and delegates both visibility and execution decisions to the same evaluator
  (`src/main/java/ai/loomspan/internal/security/DefaultAccessGuard.java:24-48`).
- Java and REST execution installs the resolved caller authentication in a scoped worker
  context before invoking application code (`ExecutionCoordinator.java:134-145`). Existing
  Java authorization integration tests protect Spring proxy/prefix/hierarchy behavior;
  concurrent integration tests protect caller identity across nested parallel work.

### 6. Current ownership and retention boundaries

- The singleton `YamlSkillCatalog`, `InMemoryCapabilityRegistry`, and registrar retain the
  only executable startup declarations for the full application-context lifetime.
  There is no current supersession or reclaimable historical generation.
- Eager `DefaultSkillCatalog` and `DefaultRegisteredSkillCatalog` snapshots copy only
  discovery values and do not retain the registry/candidate execution state after their
  constructors return (`DefaultSkillCatalog.java:20-36`;
  `DefaultRegisteredSkillCatalog.java:23-53`).
- A prepared synchronous call retains one `CapabilityMetadata` until validation or
  invocation returns. A delayed handoff retains the prepared metadata indefinitely if
  application code retains the admitted object, even after `release()` or completed
  execution, because its fields are final and never cleared
  (`DefaultSkillInvocationHandoff.java:57-91`).
- Running model missions retain their selected definition and bound child metadata in
  call/worker state. `ExecutionBinding` does not currently carry catalog, registry, or
  generation identity, so a quiet point between nested calls has no explicit reference
  to the entire skill universe (`ExecutionBinding.java:9-42`).
- Framework root ownership has explicit completion, failure, cancellation, release, and
  cutoff transitions. Mission cleanup uses `finally`, cutoff fences, and task-return
  accounting. The existing tests use `CountDownLatch`, controlled executors, and bounded
  joins to prove overlap and lifecycle order, notably
  `ConcurrentGroupedExecutionIntegrationTest`, `StepLoopMissionExecutionEngineTest`, and
  `FrameworkExecutionLifecycleTest`.
- No production use of `WeakHashMap`, finalizers, reference queues, GC notifications,
  generation histories, linked prior-state chains, fixed-generation eviction, or
  drain-before-publication exists in the current skill runtime.

### 7. Spring wiring and fixed dependencies

- Auto-configuration creates one registry, one Java-skill post-processor, one YAML
  catalog, one YAML registrar, one public catalog snapshot, one visibility resolver,
  one template, and one handoff facade. These beans are framework infrastructure and do
  not use `@ConditionalOnMissingBean` replacement points
  (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:79-105`,
  `:171-240`, `:269-306`, `:412-460`).
- `ExecutionCoordinator`, `DefaultSkillTemplate`, visibility, the step engine, public
  discovery, and observability are wired directly to the singleton registry/catalog
  pair (`LoomspanAutoConfiguration.java:210-240`, `:271-306`, `:412-460`).
- Model connection runtimes are copied into `DefaultSkillChatModelResolver` at
  construction and selected using the definition's pre-resolved execution configuration
  (`src/main/java/ai/loomspan/internal/chat/DefaultSkillChatModelResolver.java:9-33`).
  `SpringAiModelInteractionFactory` builds interactions from the passed definition and
  fixed collaborators (`src/main/java/ai/loomspan/internal/springai/SpringAiModelInteractionFactory.java:16-39`).
- The REST handler is the only supported application replacement/extension point. Its
  bean instance is captured into REST capability invokers; current registration performs
  bean-name discovery at registrar completion (`YamlSkillCapabilityRegistrar.java:69-91`).

### 8. Contract classification and protected consumers

| Surface | Design-lens classification | Current evidence and consumers | PR 6.1 boundary from ticket |
| --- | --- | --- | --- |
| `SkillTemplate`, `SkillInvocationHandoff`, `AdmittedSkillInvocation`, `SkillCatalog`, descriptors and errors | Application API | Closed allowlist in `LoomspanPublicSurfaceArchitectureTest`; README and supported-surface integration use them | Behavior must remain coherent; no new public reload/generation method in 6.1 |
| `RestSkillHandler` | Supported SPI | Closed allowlist, README, Java API docs; sole supported SPI | Bean/implementation fixed; no cleanup or reload SPI |
| YAML manifest fields, exact names, child references, schemas, roles, execution configuration | Configuration and manifest contracts | Catalog validation tests, fixtures, authoring docs | No new YAML syntax; existing validation applies to each complete candidate |
| `loomspan.skills.locations`, models, connections, session settings | Configuration and manifest contracts | `LoomspanProperties`, metadata/tests, README | No new property; configured sources/dependencies remain the preparation inputs |
| Public catalog values | Application API snapshot data | `SkillCatalog`, `DefaultSkillCatalogTest`, README | Existing snapshot objects remain immutable; public generation accessor deferred to 6.2 |
| Observability REST/SSE and Console skill catalog JSON | Persisted/serialized or current diagnostic protocol boundary as applicable | Java fixture corpus, `loomspan-console-fixtures`, Go/TS Console consumers | No generation field in 6.1; current payload shape/compatibility marker is unchanged |
| Trace records and execution views | Ephemeral diagnostic formats / Application API view | Trace contract tests, Console fixtures, public view types | Generation diagnostics explicitly deferred to 6.2 |
| Registry, YAML catalog/definitions, registrar, access policies, visibility resolver, coordinator, binding, lifecycle, internal observability catalog | Internal or accidentally exposed implementation | Internal package allowlist reasons; direct auto-configuration composition | May change atomically without compatibility shims under the ticket's pipeline note |
| `LoomspanAutoConfiguration` signatures and infrastructure beans | Spring integration machinery | Framework-integration allowlist and auto-configuration boundary tests | Not a supported bean-replacement SPI; wiring changes must remain internally coherent |

`LoomspanPublicSurfaceArchitectureTest` enumerates the fifteen Application API types and
seven framework-integration types (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:27-53`).
It explicitly classifies `DefaultSkillCatalog`, `YamlSkillCapabilityRegistrar`,
`YamlSkillCatalog`, `YamlSkillDefinition`, `DefaultSkillTemplate`, and
`DefaultSkillInvocationHandoff` as technically public internal composition types
(`:236-274`), rejects any additional SPI package, and recursively rejects internal or
autoconfigure types in API signatures (`:288-365`).

The ticket authorizes no-shim breaking changes to these internal paths. It also states
that public preparation/publication, `SkillCatalog.generationId()`, generation metadata
on `RestSkillInvocation`, publication eligibility, and public diagnostics belong to
PR 6.2. Consequently, the current Console application-adapter REST/SSE fixtures and Go
consumers are protected protocol consumers, but PR 6.1 has no requested serialized
field or compatibility-marker delta.

### 9. Documentation comparison

The repository skill package declares `loomspan-version: 1.0.0-beta.4-SNAPSHOT`, matching
the root Maven project version (`agent-skills/loomspan-docs/SKILL.md:6`; `pom.xml:9`).
The routed topics read for this research were:

- `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`
- `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`
- `agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md`
- `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`
- `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md`
- `agent-skills/loomspan-docs/references/skill-authoring/planning-concurrency.md`

**Drift classification: aligned for current behavior.** The documentation says all
declaration sources finish registration before exact child references resolve, local
visibility is filtered by authorization, nested YAML skills use their own definitions,
captured authentication crosses handoff and parallel execution, and planner branches
carry isolated execution identity. Those statements match the production paths and
focused tests cited above. The documentation does not describe reloadable generations;
that behavior is not present in the current executable revision, so its absence is an
undocumented future area rather than drift in current behavior.

PR 6.1 has skill-authoring impact in execution consistency rather than syntax: an
invocation tree is intended to retain one set of definitions, child visibility, schemas,
manifest policies, and execution settings while new roots may see a later set. The
ticket adds no author-facing field, default, or configuration key. PR 6.2 owns the public
prepare/publish workflow and generation-facing documentation.

### 10. Existing verification anchors

- Startup/loading: `YamlSkillCatalogTests`, `YamlSkillCapabilityRegistrarTests`,
  `LoomspanAutoConfigurationTests`, and `SupportedSurfaceIntegrationTest`.
- Immutable discovery: `DefaultSkillCatalogTest`,
  `DefaultRegisteredSkillCatalogTest`, catalog API value tests, and Console REST fixture
  corpus tests.
- Root preparation/handoff: `DefaultSkillTemplateTest`,
  `FrameworkExecutionLifecycleTest`, and `FrameworkShutdownIntegrationTest`.
- Nested/direct/planning execution: `ExecutionCoordinatorTest`,
  `SuccessfulSkillCompletionBoundaryTest`, `StepLoopMissionExecutionEngineTest`, and
  `ConcurrentGroupedExecutionIntegrationTest`.
- Authorization: `SkillVisibilityResolverTest`, `DefaultAccessGuardTest`,
  `JavaSkillAuthenticationScopeIntegrationTests`,
  `JavaSkillAuthorizationIntegrationTests`, and `JavaSkillSecurityStartupTests`.
- Lifecycle and ownership mechanics: `FrameworkExecutionLifecycleTest`,
  `MissionLifecycleTest`, `JavaSkillMissionCutoffTest`, and existing controlled-executor
  concurrency tests.
- Supported surface: `ApplicationApiValueTest`,
  `LoomspanAutoConfigurationBoundaryTest`, and
  `LoomspanPublicSurfaceArchitectureTest`.

The root Maven project is a single `loomspan-spring-boot-starter` artifact
(`pom.xml:8-9`). Historical verification records use focused `-Dtest=...` invocations
and full `./mvnw`/`mvnw.cmd` reactor verification; the ticket specifically requires the
public-surface architecture test after production type changes.

## Architecture Documentation

### Current authority graph

```text
LoomspanProperties + resource patterns
    -> YamlSkillCatalog (parsed definitions + captured source bytes)
Spring bean discovery
    -> SkillMethodBeanPostProcessor
        -> Java CapabilityMetadata
             \
              -> InMemoryCapabilityRegistry <- YamlSkillCapabilityRegistrar
             /                              (YAML/REST metadata + final child checks)
YamlSkillCatalog
    -> DefaultSkillCatalog (public immutable startup snapshot)
    -> DefaultRegisteredSkillCatalog (internal immutable observability snapshot)

SkillTemplate.prepare*
    -> registry capability + input contract + normalized input
    -> validate OR invoke/admit
        -> LoomspanSessionRunner
            -> ExecutionBinding(session, mission, branch)
                -> ExecutionCoordinator
                    -> registry capability + YAML catalog definition
                    -> visibility(registry + YAML catalog + access guard)
                    -> bound tools / model / direct / plan / nested / parallel work
```

There are currently three distinct kinds of state:

1. Startup construction state: mutable YAML maps and additive capability registry.
2. Immutable discovery state: public and observability snapshots copied from startup
   state.
3. Invocation state: prepared input, admission, session, mission, binding, bound tools,
   and worker branches.

The current code has no object that owns all three views for one generation. The ticket's
coherence requirement spans exactly the boundaries where the graph currently reads the
registry and YAML catalog separately: root preparation, coordinator lookup, child
visibility, nested lookup, and discovery projection construction.

### Existing lifecycle concepts available to the later plan

- `PreparedInput` is the current pre-admission carrier for exact capability metadata and
  normalized validated input.
- `AdmittedRoot` is the current delayed-root ownership token with single-use claim and
  explicit pending release.
- `LoomspanSessionRunner` owns root session creation and terminal completion ordering.
- `ExecutionBinding` is the immutable value explicitly captured into executor work and
  forked for parallel branches.
- `MissionContext`/`MissionLifecycle` own nested mission hierarchy, cancellation, cutoff,
  and branch-return accounting.
- `DefaultSkillCatalog` and `DefaultRegisteredSkillCatalog` establish existing immutable
  discovery projection patterns.

### Current code-health observations relevant to scope

- Registry and YAML catalog are duplicate runtime lookup authorities whose values are
  correlated only by startup ordering and validation; no single object enforces that a
  later pair is from the same assembly.
- `YamlSkillCapabilityRegistrar` combines one-time lifecycle orchestration, Java
  discovery, REST bean discovery, metadata construction, mutation of the live registry,
  and cross-reference validation.
- `StepLoopMissionExecutionEngine` retains constructor/field dependencies on
  `CapabilityRegistry` and `YamlSkillCatalog` that its execution methods do not read.
- The public and observability catalog constructors duplicate capability-to-discovery
  projection work and are independently tied to the startup authorities.
- A released or completed `DefaultAdmittedSkillInvocation` can remain application-held
  and currently retains its prepared metadata because release only closes the root.

These are descriptions of the current decomposition. The ticket explicitly leaves exact
internal classes and storage mechanisms to implementation planning and authorizes removal
of obsolete internal paths without shims.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/phases/loomspan-phase-6-reloadable-skills.md` defines the two-PR sequence:
  PR 6.1 establishes generation assembly, activation foundations, execution retention,
  immutable snapshots, and ownership release; PR 6.2 exposes public preparation,
  publication, metadata, stale/foreign/single-use checks, shutdown coordination, and
  application examples.
- `ai/thoughts/tickets/loomspan-pr-6.1-coherent-skill-generations.md` is the controlling
  ticket. It authorizes internal breaking changes without compatibility shims, fixes Java
  implementations/beans/model connections/REST handler beans across candidates, and
  excludes public reload and diagnostic metadata.
- `ai/thoughts/tickets/loomspan-pr-6.2-public-skill-reload.md` records the later consumer
  contract: caller-owned candidates, expected-base publication eligibility, public
  generation IDs, generation-keyed REST configuration, shutdown coordination, and active
  discovery behavior. It is context, not current executable behavior or PR 6.1 scope.
- `ai/thoughts/framework-feature-design-lens.md` supplies the contract classifications
  used above and treats internal public classes and Spring beans as technical exposure
  unless supported evidence says otherwise.
- `ai/thoughts/plans/1.0.0-beta.4.md` records earlier successful focused and full-suite
  verification for the current static catalog, handoff, lifecycle, REST, and public
  surface. It is historical evidence, not verification of the unimplemented ticket.

## Related Research

No earlier document in `ai/thoughts/research/` covers reloadable skill generations. The
Phase 6 roadmap and two tickets are the only repository history specifically describing
this feature.

## Open Questions

The ticket resolves observable behavior and leaves the following internal planning
choices open; none requires a developer escalation at the research step:

1. Which internal object owns the immutable generation and active atomic reference, and
   which existing registry/catalog/registrar types become builders, projections, or are
   removed.
2. How fixed Java metadata and the fixed REST handler availability/cardinality result are
   captured once so later candidate validation does not rediscover Spring beans while
   still allowing a later REST addition when a suitable startup bean exists.
3. Which existing lifecycle carrier holds the generation from pre-validation through a
   delayed admission, transfers it into running execution, and clears terminal handoff
   references even when the application retains the public admitted handle.
4. How package-private preparation and activation seams are exposed to focused PR 6.1
   tests without introducing the public reload API reserved for PR 6.2.
5. Whether the unused step-engine registry/catalog constructor paths are removed as part
   of the in-scope authority consolidation; they are internal and the ticket authorizes
   no-shim cleanup.
6. How generation ownership release is observed deterministically in tests without GC
   timing, while keeping correctness based on ordinary strong references rather than a
   production archive or reference-count API.

