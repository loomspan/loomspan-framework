# PR 6.1 — Coherent Skill Generations Implementation Plan

## Overview

Introduce an internal immutable skill-generation aggregate and make it the single
authority for capability metadata, YAML/REST definitions, discovery projections,
and generation identity. Prepare complete candidates without touching active state,
activate an already prepared candidate with one atomic reference swap, and capture
the selected generation before root input validation so the same generation follows
delayed, nested, and parallel execution.

This is the internal foundation for PR 6.2. It deliberately exposes no public reload
service, generation accessor, REST metadata, or new SPI.

## Current State Analysis

Startup constructs one mutable declaration universe in two independent objects.
`YamlSkillCatalog` discovers and validates YAML resources into mutable maps, while
`SkillMethodBeanPostProcessor` registers Java metadata directly into the additive
`InMemoryCapabilityRegistry` (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-130`,
`src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:161-218`).
`YamlSkillCapabilityRegistrar` mutates that live registry with YAML/REST metadata and
only afterwards validates exact child references
(`src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:37-66`).
That ordering is sufficient for fail-fast startup, but it cannot prepare a replacement
without partially mutating the active universe.

Runtime code rereads the singleton registry/catalog pair at multiple boundaries.
`DefaultSkillTemplate` selects the root capability before input validation
(`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:133-157`),
but `ExecutionCoordinator` later performs independent name lookups against its injected
singleton registry and YAML catalog (`src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:77-103`,
`:465-488`). `DefaultSkillVisibilityResolver` joins those same singletons again for
child resolution and authorization filtering
(`src/main/java/ai/loomspan/internal/skill/DefaultSkillVisibilityResolver.java:27-53`).
An activation between those reads could therefore mix schemas, definitions, child
visibility, policies, and execution settings.

`ExecutionBinding` is already the immutable execution identity copied into worker
threads and forks, but currently contains only the session, mission, and physical
branch (`src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:9-42`).
`DefaultSkillInvocationHandoff` retains its prepared metadata and authentication in
final fields even after release or completed invocation
(`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:57-91`).
The framework has no generation candidate, generation ID, active atomic reference,
activation operation, or historical generation archive.

## Desired End State

- One immutable internal `SkillGeneration` owns a fresh opaque process-local string ID,
  a complete exact-name capability map, the corresponding YAML/REST definitions, and
  immutable public and observability discovery projections.
- `SkillGenerationManager.prepare()` rereads and validates the entire configured YAML
  set against one fixed Java/dependency snapshot and returns a detached candidate. It
  never changes active state. `activate(candidate)` installs that exact object and ID
  with a single `AtomicReference` write and performs no I/O or declaration validation.
- Startup prepares and activates the first generation through the same code path. Invalid
  startup still fails context creation; invalid later preparation leaves the active
  reference untouched.
- Root validation, synchronous invocation, and handoff preparation capture the active
  generation before object conversion or schema validation. Executing roots install it
  in `ExecutionBinding`; `withMission` and `forkBranch` preserve it automatically.
- All root and child definition, schema, visibility, access-policy, and execution-setting
  reads come from the binding's generation. Current authentication and authorization
  checks still execute at their existing validation and dispatch boundaries.
- Application-retained catalog snapshots contain copied discovery data only. Prepared
  candidate owners, the active reference, delayed handles, and live bindings retain
  generations only for their legitimate lifetimes; handoff terminal paths clear their
  retained payload and execution scopes restore normally.
- No production archive, previous-generation link, weak-reference lifecycle, reference
  counter, two-generation cap, drain-before-activation gate, or GC notification exists.

Verification is complete when the focused generation, startup, execution,
authorization, lifecycle, discovery, and architecture tests pass, followed by the
full Maven `verify` lifecycle.

### Key Discoveries:

- The existing `YamlSkillDefinition` and `YamlSkillSource` already defensively copy
  manifests, nested values, constraints, and source bytes, so candidate independence can
  build on those value semantics (`src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java:14-52`,
  `:78-135`; `src/main/java/ai/loomspan/internal/skill/YamlSkillSource.java:13-37`).
- `FrameworkExecutionLifecycle.AdmittedRoot` already owns single-use handoff admission,
  release, completion, and cutoff (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:55-98`,
  `:202-224`, `:259-310`); generation ownership should accompany this lifecycle rather
  than create a second admission registry.
- Worker and planning concurrency already capture and restore `ExecutionBinding`, and
  parallel members fork it (`src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-116`,
  `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:428-470`,
  `:600-625`). Adding generation identity there covers nested and parallel propagation
  without a new ambient context.
- `StepLoopMissionExecutionEngine` retains registry/catalog constructor state that its
  execution methods do not read (`src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:92-104`,
  `:106-212`). Removing those obsolete dependencies is in scope and avoids carrying the
  superseded singleton authority forward.
- `DefaultSkillCatalog` and `DefaultRegisteredSkillCatalog` are already eager immutable
  projection patterns, but both currently trigger/join startup registration independently
  (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:20-36`,
  `src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:23-53`).

## What We're NOT Doing

- No public `SkillReloader`, `PreparedSkillUpdate`, `SkillCatalog.generationId()`, public
  prepare/publish/snapshot operation, or publication eligibility contract; PR 6.2 owns
  those APIs.
- No generation field on `RestSkillInvocation`, public execution views, traces, metrics,
  Console REST/SSE/NDJSON, or other diagnostics; PR 6.2 owns those coordinated protocol
  changes.
- No file watcher, partial/per-skill reload, Java bean rediscovery, Java hot swapping,
  connection/model replacement, REST handler replacement, rollback API, or distributed
  publication protocol.
- No application artifact registry, cleanup notification, retirement callback, cleanup
  SPI, historical execution service, or guarantee that publishing makes application
  artifacts safe to delete.
- No compatibility overloads, aliases, deprecated constructors, dual registries, or
  fallback readers for obsolete internal wiring.

## Skill-Authoring Documentation Impact

**Impact**: Affected

- **Rationale**: This change adds an author-visible execution invariant without adding
  syntax: one invocation tree uses one coherent set of schemas, definitions, allowed
  children, manifest RBAC, and execution settings, while a later root may use a newer
  set. Existing authentication and authorization still run for every dispatch. Public
  update workflow and visible IDs remain deferred to PR 6.2.
- **Documents to update**:
  - `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`
  - `agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md`
  - `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`
  - `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`
  - `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md`
  - `agent-skills/loomspan-docs/references/skill-authoring/planning-concurrency.md`
  - `agent-skills/loomspan-docs/references/skill-authoring/README.md`
- **Supporting evidence**: New focused generation assembly and execution integration
  tests will establish whole-candidate validation, source independence, root capture,
  old-child execution, policy/schema consistency, and nested/parallel propagation.
  Existing input, authorization, REST, and concurrency tests remain supporting anchors.
- **Coverage table update**: Required. Update the existing mental-model, input,
  authorization, REST, model-selection, and planning rows to identify coherent
  invocation-generation behavior as source-verified; do not add a reload-workflow topic
  before PR 6.2 exposes that workflow.
- **LLM-first usability**: Put the primary invariant and terminology in the mental model,
  then add only the local consequence needed by each routed topic. State explicitly that
  authors cannot select a generation and that the framework supplies no public reload or
  retirement signal in this PR. Use the new focused tests/classes as stable anchors and
  avoid duplicating future prepare/publish guidance.

The checked-out guidance and executable behavior are currently **aligned** for the
static runtime. The absence of generation guidance is a future behavior gap, not current
documentation drift. Implementation and documentation must land atomically so the final
classification remains aligned.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | `SkillTemplate`, `SkillInvocationHandoff`, `AdmittedSkillInvocation`, and `SkillCatalog` retain their signatures. Their behavior becomes generation-coherent; an injected catalog remains the immutable startup snapshot (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:27-53`; `README.md:141`). | Preserve all allowlisted signatures and observable validation/authorization behavior. Add no generation accessor until PR 6.2. |
| Supported SPI | `RestSkillHandler` remains the sole SPI. The same fixed handler bean is captured by every prepared REST definition; no cleanup/reload SPI is added (`src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:69-91`). | Preserve handler contract and cardinality rule. No new SPI or replacement point. |
| Configuration and manifest contracts | Existing `loomspan.skills.locations`, model/connection configuration, YAML syntax, validation, defaults, exact names, schemas, roles, execution settings, and child references apply independently to every whole candidate. No new property or manifest field is introduced. | Preserve syntax and diagnostics. Accept complete additions/removals/empty YAML; reject a candidate with duplicates, invalid schemas/handlers, or unresolved remaining children without changing active state. |
| Persisted or serialized contracts | Public catalog values remain copied immutable snapshots. Console adapter payloads, fixtures, compatibility marker, REST/SSE/NDJSON, and trace serialization gain no generation field in PR 6.1. | Preserve current serialized shapes and marker. No Java-to-Go or fixture delta. |
| Ephemeral diagnostic formats | Existing startup/validation errors and current execution diagnostics remain useful, but they do not expose generation identity yet. | Preserve accurate current-run failure visibility, authorization boundaries, and redaction; defer generation diagnostics to PR 6.2. |
| Internal or accidentally exposed implementation | Registry/catalog/registrar beans, constructors, `ExecutionBinding`, coordinator lookup, visibility resolution, handoff payload, step-engine constructors, and auto-configuration wiring change substantially. Architecture tests classify these as internal/Spring machinery (`LoomspanPublicSurfaceArchitectureTest.java:236-274`). | Remove obsolete singleton registry/registrar paths and constructors atomically. Update all in-repository callers/tests; do not retain shims or dual behavior. |

- **Evidence of supported contracts**: The closed public/SPI allowlists in
  `LoomspanPublicSurfaceArchitectureTest`, README application guidance, authoring
  documentation, configuration metadata/tests, and the ticket define the protected
  surfaces. Public modifiers on internal types and Spring bean methods are not separate
  compatibility promises.
- **Intentional compatibility changes**: Internal constructors, beans, registry mutation,
  and registrar lifecycle are intentionally broken/removed under the ticket's `Pipeline
  notes`. No supported API, SPI, manifest syntax, or serialized contract is intentionally
  broken.
- **In-repository consumers to update**: Auto-configuration, Java discovery, YAML loading,
  public/observability catalog projection, facade/handoff, session runner, execution
  binding/scope users, coordinator/router, visibility/tool surface, step-engine wiring,
  unit/integration fixtures, architecture allowlists/reasons, and routed authoring docs.
- **Public-surface delta**: None in `ai.loomspan.api`; no new supported SPI. Internal and
  auto-configuration signatures may be added or removed but must remain absent from
  supported API signatures.
- **Shim decision**: **No shim.** The changed types are internal or Spring integration
  machinery, and the ticket expressly authorizes atomic removal of obsolete contracts.
- **Java-to-Go boundary coordination**: **Not required.** PR 6.1 changes no consumed
  application-adapter REST/SSE, acquisition, problem, NDJSON, or compatibility-marker
  field. Existing fixture corpus tests remain regression coverage.
- **Pipeline notes alignment**: **Aligned.** The planned no-shim internal refactor and
  unchanged public/protocol surface are exactly the intentional boundary described by
  the ticket; no broader compatibility break is planned.

## Implementation Approach

Use one generation object as the semantic authority and ordinary strong reachability as
the ownership model:

```text
fixed Java declarations + fixed REST handler candidates + configured resources
    -> SkillGenerationManager.prepare()
        -> fresh immutable SkillGeneration(id, capabilities, definitions, snapshots)
             [no active-state mutation]
    -> SkillGenerationManager.activate(the same generation)
        -> AtomicReference.set(generation)

root facade captures active generation before validation
    -> PreparedInput(generation, capability, normalized input)
    -> ExecutionBinding(session, mission, branch, generation)
        -> coordinator / visibility / nested router / worker forks use binding generation
```

Keep candidate construction local and side-effect free. `SkillMethodBeanPostProcessor`
will own a one-time immutable snapshot of fixed Java metadata instead of registering into
a live global registry. Capture the fixed set of REST handler bean candidates once during
generation-manager initialization; preserve the existing rule that cardinality is
validated only when a candidate contains REST manifests. Every preparation may reread
YAML sources, but it must reuse those fixed dependencies and must not scan Java skills or
handler beans again.

Refactor `YamlSkillCatalog` into a per-preparation loader/value that freezes its maps and
captured bytes. Build candidate capability metadata in a local exact-name map, validate
cross-source collisions and all child references there, then create `SkillGeneration`.
Eliminate the mutable singleton `CapabilityRegistry`/`InMemoryCapabilityRegistry` and
`YamlSkillCapabilityRegistrar` authority instead of wrapping them in another layer.

The manager retains only the active generation. A prepared but unpublished generation
is retained by its owner; replaced generations are retained only by older snapshots,
handoffs, or execution bindings. `ExecutionBinding.withMission()` and `forkBranch()` copy
the same generation. No explicit retirement operation is needed. The handoff wrapper is
the one current lifecycle leak: replace final payload fields with an atomic single-use
payload that is cleared on invoke claim or release, while the invocation stack/binding
retains the generation for exactly as long as running work needs it.

## Phase 1: Build Immutable Generations and Atomic Activation

### Overview

Replace additive singleton registration with detached whole-generation assembly and one
active atomic owner, while preserving every declaration validation rule and fixed Spring
dependency.

### Changes Required:

#### 1. Immutable generation aggregate and manager
**Files**:
- `src/main/java/ai/loomspan/internal/skill/SkillGeneration.java` (new)
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java` (new)

**Changes**:
- Add an immutable generation object with a nonblank opaque ID, unmodifiable exact-name
  capability and YAML-definition maps, exact lookup/list methods, and prebuilt immutable
  `DefaultSkillCatalog` and `DefaultRegisteredSkillCatalog` projections.
- Generate a new UUID string only after the complete candidate validates successfully.
  Keep that ID on the candidate through activation; do not regenerate it in `activate`.
- Add an idempotent initialization gate that snapshots fixed dependencies, prepares the
  initial generation, and activates it. Invalid startup propagates and prevents context
  creation.
- Implement `prepare()` as a detached full build and `activate(SkillGeneration)` as the
  sole `AtomicReference` swap. Failed preparation cannot observe or mutate the active
  reference. Do not keep candidates or replaced generations in a manager collection.
- Keep these operations internal and suitable for package-level focused tests and PR
  6.2 composition; do not add public API or publication eligibility yet.

#### 2. Per-candidate YAML loading and fixed declaration inputs
**Files**:
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java`
- `src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java`
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java` (remove)
- `src/main/java/ai/loomspan/internal/core/CapabilityRegistry.java` (remove)
- `src/main/java/ai/loomspan/internal/core/InMemoryCapabilityRegistry.java` (remove)

**Changes**:
- Make YAML discovery/loading create a fresh immutable catalog per preparation. Preserve
  deterministic resource ordering, exact parse/validation diagnostics, captured source
  bytes, an empty result for missing/empty configured locations, and all existing schema,
  REST, evidence, linter, planning, model, and allowed-child validation.
- Change Java skill discovery to collect/collision-check fixed `CapabilityMetadata`
  internally and expose an immutable snapshot after `completeDiscovery()`. Continue
  forcing relevant lazy beans, resolving canonical methods/policies, and verifying final
  proxy enforcement exactly once.
- During manager initialization, capture the sorted fixed REST handler candidate set
  once without rejecting zero/multiple handlers unless a prepared generation contains
  REST manifests. Candidate assembly resolves the sole captured handler when needed.
- Move YAML/REST metadata construction, input-contract resolution, cross-source duplicate
  detection, REST invoker creation, and final exact child validation into detached
  generation assembly. Validate after all fixed Java and candidate YAML/REST metadata are
  present.
- Delete the live mutable registry and registrar rather than retaining a parallel
  authority. Update diagnostics to retain declaration/resource locations.

#### 3. Discovery projections belong to the generation
**Files**:
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java`
- `src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java`
- `src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java`

**Changes**:
- Build both projections from one already validated generation/candidate input without
  triggering registration or consulting another global object.
- Ensure projection values copy only discovery data and do not themselves authorize or
  enable execution against an old generation.
- Keep every previously returned projection immutable. The injected application
  `SkillCatalog` remains the startup generation snapshot; the observability registrar
  consumes the generation's corresponding registered-skill snapshot without changing
  its serialized response shape.

#### 4. Spring composition and internal cleanup
**Files**:
- `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`
- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java`
- affected internal construction helpers and tests

**Changes**:
- Replace singleton registry/catalog/registrar beans with the generation manager and
  wire the application catalog to its initial generation snapshot.
- Create `SkillMethodBeanPostProcessor` without a live registry dependency and supply
  the codecs/input resolver needed to produce fixed metadata.
- Remove unused capability-registry and YAML-catalog fields and constructor parameters
  from `StepLoopMissionExecutionEngine`, updating all callers atomically and without
  compatibility constructors.
- Update architecture and auto-configuration tests to recognize the new internal
  generation machinery while proving it is not an application bean-replacement SPI.

### Success Criteria:

#### Automated Verification:
- [x] Startup creates one valid initial generation with a nonblank ID and preserves
  existing Java/YAML/REST behavior.
- [x] Two successful preparations over identical YAML have different IDs; activation
  retains the prepared ID and exact captured contents.
- [x] Editing or deleting source files after preparation cannot affect candidate
  activation, execution lookups, or discovery snapshots.
- [x] Additions, edits, removals, empty YAML, cross-source duplicates, REST-handler
  cardinality, and unresolved-child cases have focused success/failure coverage.
- [x] Invalid startup fails; invalid replacement preparation leaves the active generation
  object and its snapshots unchanged.
- [x] The manager retains no generation history, and three or more independently held
  prepared/active generation objects can coexist without eviction.
- [x] Focused assembly/startup tests pass with the command in the testing plan.

---

## Phase 2: Capture and Propagate One Generation per Invocation Tree

### Overview

Capture active state at the earliest facade boundary, transfer it through admission into
the existing immutable execution binding, and make every invocation-dependent lookup use
that bound generation.

### Changes Required:

#### 1. Root capture before validation
**Files**:
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java`
- `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java`

**Changes**:
- Inject `SkillGenerationManager` into the template. At the start of both object and map
  preparation, read `active()` once before object conversion, null normalization, schema
  lookup, or validation.
- Extend internal `PreparedInput` to retain the generation alongside capability metadata
  and normalized validation. Capability lookup must be against that captured generation.
- Pass the same generation into both newly admitted and already admitted session calls.
  Require `LoomspanSessionRunner` root methods to install a generation-bearing root
  binding; remove obsolete no-generation internal overloads and update callers/tests.
- Keep standalone `validate` local: it uses one captured generation for schema and policy,
  then retains nothing for a future invocation.

#### 2. Binding propagation and coherent execution lookup
**Files**:
- `src/main/java/ai/loomspan/internal/core/ExecutionBinding.java`
- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java`
- `src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java`
- `src/main/java/ai/loomspan/internal/skill/DefaultSkillVisibilityResolver.java`
- `src/main/java/ai/loomspan/internal/runtime/tool/DefaultToolSurfaceService.java`
- related binding/test factories

**Changes**:
- Add the non-null `SkillGeneration` to `ExecutionBinding`. Make `sessionOnly`,
  `withMission`, and `forkBranch` preserve the exact same object, and keep existing
  session/mission/branch consistency checks.
- Remove registry/catalog fields from the coordinator and visibility resolver. Resolve
  root/nested metadata, YAML definitions, exact allowed children, schemas, policy, and
  execution configuration from `ExecutionBindingScope.requireCurrent().generation()`.
- Route the already selected `CapabilityMetadata` into coordinator execution and reject
  metadata that is not the exact capability owned by the bound generation. This prevents
  a stale/new-generation capability from being injected into another tree while keeping
  the existing access check before execution.
- Continue filtering each parent generation's exact allowed-child set using the current
  invocation authentication. A manifest policy change affects later roots only, but
  authentication/authorization are still evaluated when old-tree work dispatches.
- Update worker, nested, and parallel tests/builders so every installed binding has an
  explicit generation and every fork proves identity preservation.

#### 3. Delayed handoff transfer and deterministic release
**Files**:
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java`
- `src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java` only if a
  small package-private ownership hook is needed to align payload clearing with claim
- `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- `src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`

**Changes**:
- Replace the admitted wrapper's permanent prepared/authentication fields with one
  atomic single-use payload containing skill name, prepared input/generation, and caller
  authentication.
- Invocation atomically claims/clears that payload before executing; the local call and
  binding retain it while needed. Release atomically clears the payload and closes the
  existing admitted root. Repeated/concurrent invoke/release remains rejected by the
  existing admission semantics and cannot leak the generation through an application-held
  terminal handle.
- Exercise success, input/policy failure, execution failure, cancellation/cutoff,
  explicit release, and concurrent claim/release without sleeps. Assert cleared holder,
  empty framework lifecycle ownership, and restored binding scope as deterministic
  ownership evidence; do not assert GC timing.

### Success Criteria:

#### Automated Verification:
- [x] A root captured before input validation continues with the same generation even
  when another generation activates before admission or execution.
- [x] A delayed handoff uses its captured generation; release and every terminal invoke
  path clear the handle's framework-owned payload.
- [x] Old work can execute a child removed from the active generation, while a new root
  immediately uses the new schema, definition, visibility, policy, and execution settings.
- [x] Nested direct/planning calls and parallel branches preserve exact generation object
  identity under controlled latch/barrier coordination.
- [x] Existing authentication capture, policy filtering, execution-time authorization,
  mission lifecycle, cancellation, and cutoff tests remain green.
- [x] Three or more generations can be simultaneously retained by legitimate active,
  candidate, handoff, and running owners without forced eviction.

---

## Phase 3: Align Documentation and Verify Boundaries

### Overview

Document only the PR 6.1 author-facing invariant, update executable boundary tests, and
run focused plus full verification without prematurely documenting PR 6.2's workflow.

### Changes Required:

#### 1. LLM-first authoring guidance
**Files**:
- `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`
- `agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md`
- `agent-skills/loomspan-docs/references/skill-authoring/authorization.md`
- `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`
- `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md`
- `agent-skills/loomspan-docs/references/skill-authoring/planning-concurrency.md`
- `agent-skills/loomspan-docs/references/skill-authoring/README.md`

**Changes**:
- Add one concise mental-model section defining coherent invocation generations and the
  new-root/old-tree behavior.
- In each routed topic, state the local consequence: input schemas and contracts,
  manifest RBAC and visibility, effective model/execution configuration, REST manifest
  behavior with fixed handler dependencies, and nested/parallel propagation all come
  from the root's captured coherent generation.
- Explicitly distinguish generation-stable policy from authentication: the latter still
  comes from trusted invocation/session scope and is checked at dispatch.
- State the PR 6.1 limitations: no author-selectable ID, public reload workflow,
  historical invocation, or safe external-artifact retirement signal.
- Update coverage notes/confidence and stable source/test anchors. Avoid documenting the
  deferred public preparation/publication workflow.

#### 2. Supported-surface and protocol regression coverage
**Files**:
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`
- `src/test/java/ai/loomspan/architecture/LoomspanAutoConfigurationBoundaryTest.java`
- `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`
- existing Console fixture corpus tests

**Changes**:
- Keep the fifteen-type application API allowlist and `RestSkillHandler`-only SPI closed.
  Classify any technically public generation/autoconfiguration types as internal or
  framework integration and recursively prove no internal type leaks into API signatures.
- Assert no new conditional bean replacement surface is introduced.
- Preserve supported facade/catalog behavior and current Console serialized fixtures;
  no compatibility-marker or Java-to-Go fixture update is expected.

### Success Criteria:

#### Automated Verification:
- [x] `LoomspanPublicSurfaceArchitectureTest` passes with no public API/SPI delta.
- [x] Auto-configuration and supported-surface integration tests pass with the generation
  manager as internal infrastructure.
- [x] Existing Console fixture corpus remains byte/shape compatible.
- [x] Every changed authoring claim names focused production and test evidence, the README
  coverage table is updated, and the routed documents satisfy the LLM-first standard.
- [x] Full repository verification passes: `.\mvnw.cmd verify`.

## Testing Strategy

### Unit Tests:

- Build valid/invalid complete generations, prove fresh IDs for identical content,
  source-byte independence, empty/removal behavior, exact child validation, fixed Java
  metadata, and REST handler cardinality.
- Prove atomic activation installs the prepared object/ID and failed preparation leaves
  the active object unchanged.
- Prove generation-bearing bindings preserve identity across mission changes and forks,
  reject mixed-generation capability metadata, and restore scope after all exits.
- Prove delayed handles clear retained payload on invoke/release/failure/cutoff without
  GC or arbitrary timing.

### Integration Tests:

- Use controlled latches/barriers to activate a new generation between root capture and
  admission/execution, during a delayed handoff, during nested child dispatch, and while
  parallel workers run. Assert old trees retain removed children and old schema/policy/
  settings while new roots see the replacement.
- Cover additions, edits, removals, empty YAML, source mutation after preparation,
  immutable discovery snapshots, authorization enforcement, and three-plus legitimate
  simultaneous generation owners.
- Preserve startup, public surface, supported facade, and Console fixture behavior.

Full test names, red-test sequencing, commands, and exit criteria are in the companion
testing plan.

## Performance Considerations

Preparation is expected to be O(configured resources + fixed Java declarations + child
references) and may allocate a complete candidate; it is not on the invocation hot path.
Activation is one atomic reference write. Root capture and all generation lookups remain
O(1), and binding forks share the immutable generation rather than copying it.

Memory use intentionally scales with the number and size of generations legitimately
retained by the active reference, candidate owners, snapshots, handoffs, and running
work. There is no unbounded framework archive. Tests should inspect ownership state and
scope cleanup, not force GC or assert collection latency.

## Migration Notes

There is no application migration or data migration in PR 6.1. Public application calls,
manifest syntax, properties, REST handler SPI, and serialized protocols remain unchanged.
Internal constructors, Spring bean composition, and test fixtures move atomically to the
generation authority with no compatibility adapters, as explicitly authorized by the
ticket. PR 6.2 will expose the public update workflow and generation metadata on top of
this foundation.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-6.1-coherent-skill-generations.md`
- Related research: `ai/thoughts/research/2026-09-15-loomspan-pr-6-1-coherent-skill-generations.md`
- Phase roadmap: `ai/thoughts/phases/loomspan-phase-6-reloadable-skills.md`
- Framework design lens: `ai/thoughts/framework-feature-design-lens.md`
- Similar propagation mechanism: `src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:9-42`
- Similar immutable discovery projection: `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:15-49`
