---
date: 2026-09-16T00:21:19-07:00
researcher: GPT-5
git_commit: 79b6bf5c8185eb8c69046fe80432f6f6b372ffe9
branch: main
repository: loomspan-framework
topic: "PR 6.2 — Prepare and publish externally managed skills"
tags: [research, codebase, skill-reload, generations, public-api, observability]
status: complete
last_updated: 2026-09-16
last_updated_by: GPT-5
---

# Research: PR 6.2 — Prepare and publish externally managed skills

**Date**: 2026-09-16T00:21:19-07:00
**Researcher**: GPT-5
**Git Commit**: 79b6bf5c8185eb8c69046fe80432f6f6b372ffe9
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current codebase owners and cross-component contracts relevant to implementing `ai/thoughts/tickets/loomspan-pr-6.2-public-skill-reload.md`, after PR 6.1's coherent generation foundation, without implementing the feature.

## Summary

PR 6.1's internal foundation is present. `SkillGenerationManager` constructs a detached, complete generation from fixed Java declarations, fixed REST handler selection, and a newly read YAML catalog; assigns every successful preparation a UUID; validates cross-skill child references; and atomically replaces a single active reference. `SkillGeneration` freezes capability and definition maps and eagerly constructs both the application catalog and observability catalog. Root preparation captures the active generation before conversion and input validation, handoffs retain it, and `ExecutionBinding` carries the same generation through nested and parallel runtime work.

The PR 6.2 public surface does not exist in the current checkout: there is no `SkillReloader`, `PreparedSkillUpdate`, or `SkillReloadException`; `SkillCatalog` has no generation accessor; and `RestSkillInvocation` contains only skill name and input. Preparation is not serialized, activation has no ownership/base/single-use/shutdown checks, and the execution lifecycle exposes root-admission shutdown behavior only. The injected `SkillCatalog` is the immutable startup generation's catalog, while observability currently captures one registered-skill catalog when its runtime is created.

Generation-aware execution diagnostics are also absent. The trace is initialized while `LoomspanSession` is constructed, before the execution binding is installed, and `TRACE_STARTED` currently records trace path, console compatibility version, entry skill, and optional limits. Live execution projection, observability REST DTOs, Go acquisition/validation, trace analysis, MCP projections, TypeScript contracts, browser fixtures, and the Java/Go fixture corpus currently have no skill-generation field.

The repository-local `loomspan-docs` package is version-aligned with the checkout (`1.0.0-beta.4-SNAPSHOT`). Its descriptions of immutable generation capture, catalog immutability, REST handler semantics, and current diagnostic compatibility agree with executable evidence. Its statements that reload is not public and that REST invocations contain only name/input will intentionally become stale when this ticket is implemented; this is expected future documentation work, not a present source/documentation conflict.

## Detailed Findings

### 1. Current generation construction and immutable data

- `SkillGenerationManager` is the current generation assembly and activation owner (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:25-35`). It stores the active generation in an `AtomicReference`, with Java capabilities and REST-handler discovery captured as fixed dependencies.
- Startup initializes fixed dependencies, prepares a generation, and activates it when no active generation exists (`SkillGenerationManager.java:48-53`). `active()` lazily invokes that same initialization path if needed (`SkillGenerationManager.java:55-64`).
- `prepare()` obtains a fresh `YamlSkillCatalog`, runs its full initialization/validation, combines fixed Java capabilities with all YAML/REST definitions, resolves input contracts and effective execution descriptors, checks duplicate names, validates every exact child reference, and returns a new `SkillGeneration` with a random UUID (`SkillGenerationManager.java:66-105`). It does not change `active`.
- `YamlSkillCatalog.afterPropertiesSet()` clears its local maps and loads all configured matching resources in deterministic resource-description order (`src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-115`, `:133-162`). Each resource is read fully into bytes before manifest parsing, validation, effective model resolution, and `YamlSkillSource` construction (`YamlSkillCatalog.java:164-227`). Missing resource roots yield an empty set; resource discovery or reads can fail with `IllegalStateException` and a cause (`YamlSkillCatalog.java:133-175`).
- Auto-configuration snapshots skill locations, model aliases, connection names/drivers, provider model names, and thinking levels once before constructing the generation manager. Every later internal `prepare()` uses a new catalog over that fixed loading/model snapshot (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:160-203`). Java beans, connection runtime objects, and handler beans are therefore not rediscovered as candidate-specific dependencies.
- REST definitions trigger exact-one-handler validation on each relevant preparation, but the bean-name set and resolved handler instance are fixed once (`SkillGenerationManager.java:75-97`, `:113-135`). A first generation without REST definitions does not resolve a handler; a later generation with REST definitions applies the captured bean-name cardinality and resolves the fixed instance then.
- `SkillGeneration` validates its identity and exact map relationships, copies capabilities and definitions into unmodifiable maps, sorts capabilities by exact name, and eagerly creates immutable application and registered-skill catalogs (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:15-55`). It exposes the internal ID, exact capability/definition lookups, both catalogs, and identity-based capability ownership (`SkillGeneration.java:58-69`).
- `DefaultSkillCatalog` copies descriptors into a `TreeMap`, provides exact-name ordering and lookup, and exposes immutable list/map views (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:14-47`). The current constructor receives only capabilities, so generation identity is not represented in the catalog.
- Focused tests already cover fresh IDs for identical preparations, Java/YAML completeness, invalid-candidate isolation, additions/removals/empty YAML, deleted-child rejection, file change/removal after preparation, more than two caller-owned generations, and reuse of one REST handler instance (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:37-185`).

### 2. Current activation, concurrency, ownership, and shutdown boundaries

- Internal activation is an unconditional atomic `active.set(candidate)` after a null check (`SkillGenerationManager.java:108-111`). It has no expected-base comparison, framework-instance ownership check, one-shot state, or shutdown eligibility check.
- Calls to `prepare()` are not synchronized. Only fixed-dependency initialization and handler resolution are synchronized (`SkillGenerationManager.java:66-69`, `:113-135`). Calls to `activate()` are also not serialized beyond the atomic write.
- There is no candidate wrapper or candidate registry. Existing callers can retain `SkillGeneration` directly, but that type is internal and deliberately classified as implementation detail (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:266-267`).
- Generation lifetime currently follows ordinary strong references. The active manager reference retains the current generation; `PreparedInput` retains the captured generation; an admitted handoff retains `PreparedInput`; and `ExecutionBinding` retains the generation during execution (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:292-295`; `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:59-101`; `src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:10-45`).
- Handoff release or pending shutdown termination clears its payload, removing that framework-owned reference (`DefaultSkillInvocationHandoff.java:71-74`, `:83-101`). Root execution removes the binding scope in `finally` and completes lifecycle ownership in another `finally`, including failure paths (`src/main/java/ai/loomspan/internal/core/ExecutionBindingScope.java:59-76`; `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:252-307`). There is no generation archive, weak map, finalizer, or explicit generation reference counter.
- `FrameworkExecutionLifecycle` owns the authoritative root admission/shutdown boundary. `admitRoot()` and `closeAdmission()` synchronize on the same monitor, so no new root can be admitted after admission closes (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:22-65`, `:116-133`). Shutdown then waits within one deadline, publishes cutoff if necessary, clears pending handoff payloads, signals running missions, and stops the executor (`FrameworkExecutionLifecycle.java:135-167`, `:169-248`).
- The lifecycle has no public/internal query or callback specifically for skill-update eligibility. `admissionClosed` and the coordination monitor are private (`FrameworkExecutionLifecycle.java:30-35`). Consequently current activation is independent of shutdown.

### 3. Invocation-generation capture and consistency

- `DefaultSkillTemplate` reads `generationManager.active()` before object conversion and before map validation, then stores the generation, capability, and validation result in `PreparedInput` (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-164`).
- The same captured generation is passed to `LoomspanSessionRunner` for new or already-admitted sessions (`DefaultSkillTemplate.java:207-224`). The runner installs `ExecutionBinding.sessionOnly(session, generation)` for the whole root action and session finalization (`LoomspanSessionRunner.java:252-307`).
- `ExecutionBinding.withMission()` and `forkBranch()` preserve the same generation (`src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:32-45`). `DefaultSkillVisibilityResolver` resolves the current YAML definition and exact allowed children from the bound generation, then reapplies authorization (`src/main/java/ai/loomspan/internal/skill/DefaultSkillVisibilityResolver.java:23-50`).
- `CapabilityExecutionRouter` checks access, revalidates input, requires the explicit session's binding, and rejects a capability object that the captured generation does not own (`src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:39-73`). Nested and parallel invokers execute through that router while the propagated binding is current.
- `SkillGenerationExecutionIntegrationTest` verifies capture before object conversion/input validation and verifies an admitted old root continues with its old capability/policy after activation while a new root uses the replacement (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:50-124`).
- The existing REST invoker closure is assembled per generation, but it constructs `RestSkillInvocation(name, arguments)` without generation identity (`SkillGenerationManager.java:86-96`, `:147-151`). Because execution occurs inside the captured binding, the actual generation is available internally at invocation time, but is not currently surfaced to the handler.

### 4. Supported Java API and Spring wiring

- The supported Java API is a closed 15-type allowlist. `SkillCatalog`, `RestSkillInvocation`, and `SkillException` are Application API; `RestSkillHandler` is both allowlisted and the sole Supported SPI (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:27-53`, `:334-375`). Public API signatures are recursively checked against `internal` and `autoconfigure` types.
- Current `SkillCatalog` has only `skills()` and `skill(String)` (`src/main/java/ai/loomspan/api/SkillCatalog.java:6-16`). API reflection tests assert that exact shape (`src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:17-47`).
- Current `RestSkillInvocation` is a two-component record, recursively freezes map/list containers, preserves other leaves, and rejects null name/input (`src/main/java/ai/loomspan/api/RestSkillInvocation.java:10-44`). Reflection/value tests assert the exact two components and the single `RestSkillHandler.handle(RestSkillInvocation)` method (`ApplicationApiValueTest.java:66-117`).
- Current `SkillException` is unchecked and has message and message/cause constructors (`src/main/java/ai/loomspan/api/SkillException.java:1-14`; `ApplicationApiValueTest.java:184-192`). No reload-specific subtype exists.
- Auto-configuration exposes one infrastructure `SkillGenerationManager`, one startup `SkillCatalog` obtained from `generationManager.active().skillCatalog()`, one `SkillTemplate`, and one `SkillInvocationHandoff` (`LoomspanAutoConfiguration.java:160-174`, `:220-224`, `:288-314`). These framework beans are not `@ConditionalOnMissingBean` extension points.
- `LoomspanAutoConfigurationTests` assert a single manager and single public catalog, while the supported-surface integration test retrieves the public catalog/template/handoff and exercises YAML, Java, and REST behavior through public types (`src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java:143-165`; `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:90-190`).
- The ticket's public delta is explicitly authorized as a development-time no-shim change: add `SkillReloader`, `PreparedSkillUpdate`, and `SkillReloadException`; add `SkillCatalog.generationId()`; and add `RestSkillInvocation.generationId()`. The affected in-repository exact-shape allowlists, reflection tests, supported integration tests, README type count, Java API knowledge set, constructors, fixtures, and handler examples are all current consumers of the old shape.

### 5. Public catalog and REST handler behavior

- The injected `SkillCatalog` is the catalog instance of the startup generation. Later internal activation does not mutate it because each generation eagerly owns a separate immutable catalog (`LoomspanAutoConfiguration.java:220-225`; `SkillGeneration.java:50-64`).
- Catalog discovery is deliberately unfiltered and does not authorize execution (`SkillCatalog.java:6-10`). `DefaultSkillCatalogTest` protects exact-name sort, all three kinds, exact schema strings, lookup, and immutability (`src/test/java/ai/loomspan/internal/skillapi/DefaultSkillCatalogTest.java:18-51`).
- REST manifest validation, fixed handler cardinality, root/nested authorization, input validation, and result/failure behavior already use normal generation and execution paths. The supported integration test verifies immutable handler input, caller authentication, denial behavior, failure wrapping, and observation (`SupportedSurfaceIntegrationTest.java:121-190`, `:250-277`).
- Handler configuration is application-owned. The current handler receives no session identity or generation identity; the README currently documents exactly `skillName()` and `input()` (`README.md:444-466`).

### 6. Diagnostics, observability discovery, and Console consumers

- Every generation already owns an immutable `RegisteredSkillCatalog`, including exact YAML bytes/source information (`SkillGeneration.java:50-64`; `src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:19-78`).
- Observability runtime creation currently evaluates `generations.active().registeredSkillCatalog()` once and stores that catalog for the lifetime of `ObservabilityRuntime` (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:127-150`; `src/main/java/ai/loomspan/internal/observability/ObservabilityRuntime.java:20-85`). Instance status, skill list pagination, and skill detail all read that stored catalog (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRestController.java:53-100`). Thus current Console discovery does not follow later internal activation.
- Current skill pagination uses exact-name cursors tied to the observability runtime instance, validates the prior name against the current stored catalog, and pages from the immutable map (`ObservabilityRestController.java:67-93`; `RegisteredSkillCatalog.java:8-15`). There is no generation identifier or cross-request catalog snapshot in the REST page shape.
- A session's observation handle and trace handle are created inside the `LoomspanSession` constructor using only `sessionId` and `entrySkill` (`src/main/java/ai/loomspan/internal/core/LoomspanSession.java:219-264`). The runner creates that session before it installs the execution binding (`LoomspanSessionRunner.java:263-290`).
- `InternalExecutionTraceHandleFactory` and `ExecutionObservationHandleFactory` likewise accept no generation identity (`src/main/java/ai/loomspan/internal/core/InternalExecutionTraceHandleFactory.java:7-15`; `src/main/java/ai/loomspan/internal/runtime/observation/ExecutionObservationHandleFactory.java:1-6`).
- `DefaultExecutionTraceHandle.initialize()` emits `TRACE_STARTED` immediately with `tracePath`, `consoleCompatibilityVersion`, `entrySkill`, and optional configured limits (`src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:298-315`). It does not record generation identity.
- Live projection creates `ActiveExecutionSnapshot` from the execution projection state, which currently retains entry skill but no generation ID (`src/main/java/ai/loomspan/internal/runtime/observation/ExecutionProjectionState.java:19-38`; `src/main/java/ai/loomspan/internal/runtime/observation/LiveActivityProjector.java:187-202`). Observability active-execution and trace catalog DTOs expose entry skill but no generation ID (`src/main/java/ai/loomspan/internal/observability/web/dto/ObservabilityDtos.java:105-133`).
- Console's Go observability DTO validation, MCP execution contracts/output schemas, TypeScript API contracts, active and trace UI fixtures, and Java/Go trace fixture processors currently model entry skill and/or configured limits without generation identity. Representative owners include `loomspan-console/internal/observability/dto.go:71-104`, `loomspan-console/internal/mcpadapter/contracts.go:100-105`, `loomspan-console/internal/mcpadapter/output_schemas.go:218-220`, `loomspan-console/web/src/api/contracts.ts:113-199`, and `loomspan-console/internal/traceanalysis/processor.go:120-155`.
- The Java fixture corpus asserts `TRACE_STARTED.metadata.entrySkill` and emits the cross-language NDJSON corpus; Go trace analysis extracts that metadata and the configured-limit snapshot (`src/test/java/ai/loomspan/internal/runtime/trace/ConsoleTraceFixtureCorpusTest.java:198-215`, `:818-829`; `loomspan-console/internal/traceanalysis/processor.go:120-155`, `:427-452`).
- The diagnostic boundary is classified as an Ephemeral diagnostic format, not a persisted application contract. Complete saved traces are portable only between an exact matching framework/Console `consoleCompatibilityVersion`; dual `development` is best effort. The current marker is the Maven/project version `1.0.0-beta.4-SNAPSHOT`, not an independent schema counter (`pom.xml:9`; `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`).

### 7. Surface classification under the framework feature design lens

| Surface | Current evidence and classification | Ticketed delta / affected consumers |
| --- | --- | --- |
| `SkillCatalog`, `RestSkillInvocation`, `SkillException` | **Application API** through the closed allowlist, README, public value tests, and supported-surface integration | Add generation metadata directly and add reload-specific operations/errors; ticket explicitly authorizes no compatibility shim |
| `RestSkillHandler` | **Supported SPI**, the sole application extension point | Signature remains one handler method; its invocation value gains trusted framework metadata, without a new replaceable bean contract |
| `SkillReloader`, `PreparedSkillUpdate`, `SkillReloadException` | Absent today | New allowlisted Application API types; framework-created service/handle rather than SPI |
| `loomspan.skills.locations`, YAML/REST syntax, validation, configured models/connections | **Configuration and manifest contracts** | No new syntax/property; existing whole-set validation is reused against fixed configured dependencies |
| REST/SSE observability DTOs, canonical NDJSON trace metadata, fixture corpus, Go acquisition/analysis, MCP/browser projections | **Ephemeral diagnostic formats** with protected current Java/Console consumers | Actual execution generation must become coherent across current writer/readers/projections; exact marker decision remains a planning item |
| External staged REST configuration | Application-owned data outside Loomspan | Keyed by public generation ID; no Loomspan persistence/retirement/cleanup contract |
| `SkillGeneration`, `SkillGenerationManager`, `DefaultSkillCatalog`, lifecycle and observation factories | **Internal or accidentally exposed implementation** despite public Java modifiers on some types | Existing authorities to extend; no compatibility shim or consumer-facing recommendation required |
| Candidate handles and catalog snapshots retained by application code | Application-owned in-memory references, not durable serialized contracts | Opaque process/framework-instance-local ownership; no serialization, registry, expiry, historical invocation, or cleanup SPI |

There is no new **Persisted or serialized contract** intended by the ticket. Trace files remain version-matched diagnostic artifacts, and prepared handles/IDs carry no persistence or restart meaning.

### 8. Documentation alignment and author-facing impact

- The checked-out `loomspan-docs` skill declares version `1.0.0-beta.4-SNAPSHOT`, matching `pom.xml`; there is no version mismatch.
- **Aligned:** the skill-tree mental model describes exact names, complete immutable generation capture before conversion/validation, old-tree consistency, local visibility, fixed REST definitions/handlers, and absence of a current public reload API (`agent-skills/loomspan-docs/references/skill-authoring/mental-model.md:25-33`, `:92-103`; `agent-skills/loomspan-docs/references/skill-authoring/rest-skills.md:32-44`). Production and focused tests match those statements.
- **Aligned:** Java API documentation describes the current startup-only catalog and two-component REST invocation, and architecture tests enforce the stated closed 15-type API (`agent-skills/loomspan-docs/references/java-api/catalog-and-validation.md`; `agent-skills/loomspan-docs/references/java-api/rest-skills.md`; `LoomspanPublicSurfaceArchitectureTest.java:27-44`).
- **Aligned:** trace guidance describes `TRACE_STARTED.metadata.consoleCompatibilityVersion`, same-version portability, `entrySkill`, and non-durable diagnostic semantics (`agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md:34-52`). Current Java writer, Go readers, fixtures, and Console behavior match it.
- The ticket does not change skill-author manifest syntax, child visibility rules, schema semantics, authorization rules, or model selection. Its skill-authoring impact is limited to documenting that generation capture now has a public activation mechanism while execution consistency remains the same. Its larger documentation impact is application-facing: startup readiness, prepare/stage/publish, generation-keyed REST configuration, errors, shutdown, snapshots, stale updates, fresh IDs, source stability, and external retention ownership.

## Code References

- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:48-111` — startup, detached preparation, UUID allocation, validation, and unconditional activation.
- `src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:15-69` — immutable generation, catalogs, and ownership checks.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:100-227` — full resource discovery, byte capture, manifest validation, and definition freezing.
- `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:160-224` — fixed loading/model snapshot, generation manager, and startup catalog bean.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-224` — capture before conversion/validation and root execution with the captured generation.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:41-101` — admitted ownership and release of captured prepared input.
- `src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:10-45` — generation propagation through mission and branch bindings.
- `src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:55-133` — atomic admission/shutdown boundary used by roots today.
- `src/main/java/ai/loomspan/api/SkillCatalog.java:6-16` — current public catalog shape.
- `src/main/java/ai/loomspan/api/RestSkillInvocation.java:10-44` — current REST invocation shape and deep container freezing.
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:27-53` — closed public surface and framework-integration classifications.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:37-185` — existing PR 6.1 generation acceptance coverage.
- `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:50-124` — capture timing and old/new root generation behavior.
- `src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:298-315` — current run-start diagnostic metadata.
- `src/main/java/ai/loomspan/internal/observability/web/ObservabilityRestController.java:53-100` — current registered-skill discovery reads the runtime's stored catalog.
- `loomspan-console/internal/traceanalysis/processor.go:120-155` — current Console extraction of run-start metadata.
- `README.md:139-194` and `README.md:444-466` — current public catalog/invocation/API and REST handler documentation.

## Architecture Documentation

Current execution has one authority for declaration coherence: `SkillGeneration`. Assembly occurs before activation; execution captures one object identity and uses it for capability ownership, definitions, schemas, visibility, policies, and effective model configuration. The active generation is a single atomic reference, while old generations live only as long as active/caller/execution references keep them reachable.

Shutdown authority is separate: `FrameworkExecutionLifecycle` closes root admission under its own monitor and manages cutoff. Observability authority is also separate: a runtime-local registered-skill catalog is captured during observability activation, and per-session diagnostic identity is established during session construction before the execution binding is installed.

The ticket's trusted generation ID is runtime metadata rather than business input. The existing internal source of truth is `ExecutionBinding.generation().id()`. Public REST metadata and execution diagnostics must therefore originate from the generation captured for the invocation tree, not from `SkillGenerationManager.active()` at a later call site. Catalog identity originates from the generation that eagerly created the catalog.

The current public bean pattern is framework-owned infrastructure without replacement hooks. The sole application extension is `RestSkillHandler`; every other public/internal Spring bean is composition machinery, not an SPI.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-6.1-coherent-skill-generations.md` — defines and is reflected in the current immutable generation, capture, execution, and ownership foundation; it explicitly deferred public reload, REST generation metadata, publication eligibility, and diagnostics to PR 6.2.
- `ai/thoughts/phases/loomspan-phase-6-reloadable-skills.md` — records the two-stage prepare/stage/publish contract, application ownership of external artifacts, process-local IDs, expected-base publication, and no retirement/history API.
- `ai/thoughts/framework-feature-design-lens.md` — supplies the exact surface classifications, current pre-1.0 no-shim posture, sole-authority principle, trusted-metadata boundary, and current diagnostic compatibility rules used above.
- `ai/thoughts/tickets/loomspan-pr-6.2-public-skill-reload.md` — explicitly authorizes direct `SkillCatalog`/`RestSkillInvocation` changes without constructors, aliases, fallback readers, or dual behavior and requires coherent in-repository consumer updates.

## Related Research

No earlier research artifact exists under `ai/thoughts/research/` for this feature. The PR 6.1 ticket, Phase 6 roadmap, and current source/tests are the available historical and executable context.

## Open Questions

These are planning decisions that can be resolved from the ticket and current owners; none requires developer escalation at the research stage.

1. Which internal coordination object owns the one authoritative prepare/publish/shutdown boundary while preserving the ticket's required prepare-vs-publish interleaving. The current manager owns active generation state, while the lifecycle owns shutdown admission and exposes no update-specific operation.
2. How the opaque prepared handle represents framework-instance ownership, expected-base ID, candidate generation, and a race-safe single-publication state without creating the prohibited global candidate registry.
3. Where operational failures are translated to `SkillReloadException` so preparation diagnostics and causes remain useful, null arguments retain ordinary validation, startup validation remains unchanged, and JVM `Error` is not caught.
4. The precise current diagnostic projections that carry `generationId`. The ticket requires the captured ID in execution diagnostics and coherent Java/Go/Console consumers; current run-start trace, live active snapshot, trace catalog DTO, trace analysis, MCP/browser contracts, and fixture corpus are the candidate affected boundaries.
5. Whether adding `generationId` to current diagnostic shapes changes `consoleCompatibilityVersion`. The existing policy uses the coordinated Maven/project version and forbids independent legacy readers; planning must record the explicit marker decision requested by the ticket.
6. How observability endpoints capture exactly one active registered-skill catalog per operation while retaining current exact-name pagination semantics and the ticket's explicit absence of a cross-request pagination snapshot guarantee.
7. The exact documentation organization for the new public types and application lifecycle example across README and the version-aligned Java API/skill-authoring knowledge sets.
