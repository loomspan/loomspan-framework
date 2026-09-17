---
date: 2026-09-16T23:08:04-07:00
researcher: Codex
git_commit: b5c5cca39e1e1b02ccaa6e1b6becba9d6db1803a
branch: main
repository: loomspan-framework
topic: "PR 8 — generation retirement notification: current publication, capture, admission, and physical execution lifecycle"
tags: [research, codebase, generation, reload, lifecycle, public-api]
status: complete
last_updated: 2026-09-16
last_updated_by: Codex
---

# Research: PR 8 — generation retirement notification

**Date**: 2026-09-16T23:08:04-07:00  
**Researcher**: Codex  
**Git Commit**: `b5c5cca39e1e1b02ccaa6e1b6becba9d6db1803a`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research Question

Map the existing generation publication, root capture and admission, nested and parallel physical work, shutdown, supported API, wiring, tests, and documentation relevant to `ai/thoughts/tickets/loomspan-pr-8-generation-retirement-notification.md`. This document describes the checkout before implementation.

## Summary

The supported `SkillReloader` currently prepares and publishes complete generations and exposes the active catalog; it has no retirement registration method. `SkillGenerationManager` holds one atomic active generation and prepares process-local IDs for both configured YAML and application-supplied documents. Publication in `DefaultSkillReloader` validates candidate identity, freshness, single use, and open admission before replacing that reference. There is no explicit retirement count or notification path.

`DefaultSkillTemplate` captures the active `SkillGeneration` before object conversion and input validation, while `DefaultSkillInvocationHandoff` prepares before it obtains a pending root admission. `FrameworkExecutionLifecycle` tracks pending and executing roots and shares its monitor with publication and shutdown admission closure. An execution binding carries the captured generation through nested and parallel branches. Mission lifecycle tracks callable physical start and return separately from future or cancellation state, including after its cleanup grace ends. The current README tells applications to retain old generation-keyed REST configuration according to their own cleanup policy.

## Detailed Findings

### Supported API and publication

- `SkillReloader` offers `prepare()` for configured resources, `prepare(Collection<SkillDocument>)` for application-supplied documents, `publish(PreparedSkillUpdate)`, and `snapshot()` (`src/main/java/ai/loomspan/api/SkillReloader.java:5-19`). `PreparedSkillUpdate` exposes a generation ID and immutable snapshot (`src/main/java/ai/loomspan/api/PreparedSkillUpdate.java:3-8`). No listener method exists in this checkout.
- `DefaultSkillReloader.prepareWith` serializes preparation, reads the active ID while admission is open, prepares the candidate, checks that admission remains open, and retains the base ID and generation in a private candidate (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:46-56`, `:104-120`).
- `publish` serializes publishers, checks candidate origin, then runs its repeated/stale checks, `generations.activate`, and published flag update inside `FrameworkExecutionLifecycle.whileAdmissionOpen` (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:59-82`). A stale or repeated candidate is rejected; a previously published candidate remains marked published. The current operation does not traverse or retain superseded generations.
- `SkillGenerationManager.afterSingletonsInstantiated` creates the initial active generation; `active()` initializes lazily if necessary (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:53-69`). Both preparation paths use `prepareCatalog`; it issues namespace-plus-counter process-local IDs, builds a complete capability map, and captures that ID in REST handler invocations (`:71-126`). Activation is an atomic reference set (`:129-132`). The fixed REST handler is resolved once for REST declarations (`:134-156`), and `invokeRest` passes the generation ID to `RestSkillInvocation` (`:168-174`).
- `SkillGeneration` is an immutable executable declaration view with its ID, capability and definition maps, public catalog, and registered catalog (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:15-37`). Ordinary Java references in prepared inputs and execution bindings keep old instances reachable after activation.

### Capture, preparation, and pending admission

- `DefaultSkillTemplate.prepareObject` reads `generationManager.active()` before null checking or object-to-map conversion; `prepareMap` also reads it before validation. `PreparedInput` retains the generation, capability, and validation result (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-164`, `:292-295`). Direct invocation calls preparation before `invokePrepared` and session-runner admission (`:90-112`, `:207-223`). `validate` uses the same preparation functions (`:78-88`); validation itself is not an execution root.
- `DefaultSkillInvocationHandoff.handoff` prepares the input before `admit` captures authentication and calls `lifecycle.admitRoot()` (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:29-57`). The pending implementation stores the prepared payload and root. Its release clears the payload and closes the pending root; invoke atomically claims the payload and passes the root to the template; pending termination clears the payload (`:59-101`). The supported API documents caller release of an abandoned admission (`src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:5-11`, `:36-38`).
- The lifecycle monitor serializes root admission and publication's admission-open action, and also closes admission for shutdown (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:55-77`, `:128-145`). Pending roots are in `activeRoots` until release, cutoff, or claimed execution completion (`:79-125`, `:230-259`). `onPendingTermination` invokes cleanup after leaving the monitor (`:91-115`).
- `LoomspanSessionRunner.executeRoot` obtains a root admission before `executeAdmittedRoot`; the latter claims it, creates the session, installs an `ExecutionBinding` containing the generation, executes action and completion, and calls `root.completeExecution()` in `finally` (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:237-313`). Completion observers run before that root completion (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:211-223`).

### Nested and parallel work, cancellation, and physical return

- `ExecutionBinding` contains the captured generation. `withMission` and `forkBranch` preserve it (`src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:10-45`). `CapabilityExecutionRouter` checks that the capability belongs to the binding generation before executing (`src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:62-73`). A root can therefore enter later child calls against its captured generation while its binding remains active.
- `StepLoopMissionExecutionEngine` captures the binding for a mission callable, starts it on the mission executor, and calls `lifecycle.owningReturned()` from the callable's `finally` (`src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:198-209`, `:246-256`). Concurrent grouped members fork the binding, submit worker callables, and call `taskReturned` in the worker `finally` (`:384-415`). Sequential members also call `taskReturned` in `finally` (`:430-452`).
- `MissionLifecycle` records `owningStarted`/`owningReturned` and each admitted task's started/returned state (`src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:47-50`, `:72-103`, `:127-181`). Its `allPhysicalWorkReturnedLocked()` checks these booleans rather than `Future.isDone()` (`:448-452`). Cancellation can cancel registered futures (`:79-92`, `:146-158`, `:403-431`); `awaitCutoff` waits only through the existing grace/deadline and may close with physical work outstanding (`:240-265`, `:454-469`). The `CutoffTask` snapshot explicitly records physical start and return (`:514-517`).
- Root completion currently happens when the runner's action and completion finish, irrespective of later physical return of worker callables that can outlive cancellation cleanup (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:287-312`; `src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:240-265`). This is a description of the current distinct lifecycle boundaries, not a retirement behavior: no retirement signaling exists yet.

### Shutdown and wiring

- `FrameworkExecutionLifecycle.closeAdmission` establishes one deadline without waiting; `stop` waits for roots and the executor only within that budget, then publishes cutoff if needed; `destroy` is a non-waiting fallback (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:128-145`, `:163-269`). Cutoff removes pending admissions but does not mark executing roots complete (`:230-259`).
- Auto-configuration provides the current startup `SkillCatalog`, the `SkillReloader` implementation, the `DefaultSkillTemplate`, the handoff facade, and one framework lifecycle wired to the mission executor (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:222-235`, `:298-324`, `:389-405`). These are framework integration beans, not an application bean-replacement contract.

### Existing tests, fixtures, documentation, and usage

- `SkillReloaderTest` covers two-stage publication, fresh IDs, rejected candidates, stale concurrent publishers, and shutdown during preparation (`src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:28-231`). `PublicSkillReloadIntegrationTest` exercises both configured and supplied YAML via supported beans, a fixed REST handler keyed by generation ID, old pending handoff after publication, repeated and stale rejection, and startup catalog immutability (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:74-118`, `:161-250`).
- `SkillGenerationExecutionIntegrationTest` verifies capture before object conversion and a handoff that uses the old generation after activation, while new invocation uses the replacement (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:50-125`). `DefaultSkillTemplateTest` covers handoff authentication and release/cutoff behavior (`src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:54-130`).
- `FrameworkExecutionLifecycleTest` covers publication/shutdown ordering, pending release, cutoff, execution claim races, observer completion under root ownership, shared shutdown budget, interruption, and uncooperative executor work (`src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:29-599`). `ConcurrentGroupedExecutionIntegrationTest` includes nested and parallel branches and timeout behavior (`src/test/java/ai/loomspan/internal/runtime/step/ConcurrentGroupedExecutionIntegrationTest.java:89-421`). These existing tests do not assert retirement notifications, which are absent from production.
- README explains the supported API allowlist and sole `RestSkillHandler` SPI (`README.md:189`), the current generation staging pattern and fixed handler example (`README.md:196-215`), and the present application cleanup policy after publication (`README.md:216`). It documents restart resubmission of application-supplied content (`README.md:219-225`).
- No generation retirement configuration key, manifest field, persisted format, serialized protocol, or trace event was found in the matching implementation. Existing generation IDs flow through the API catalogs, prepared updates, REST invocations, sessions, and diagnostics; this ticket's proposed callback concerns the application Java boundary. The skill-authoring topic index covers immutable generations and REST declarations (`agent-skills/loomspan-docs/references/skill-authoring/README.md:56-61`), but the requested callback does not change skill-author manifest syntax or execution semantics. Skill-authoring impact classification: **aligned / no authoring behavior delta identified** against the current source and index.

## Contract Classification

| Surface | Category | Evidence and current treatment |
| --- | --- | --- |
| `SkillReloader`, `PreparedSkillUpdate`, `SkillCatalog`, `SkillInvocationHandoff`, `AdmittedSkillInvocation` | Application API | Explicit architecture allowlist at `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:28-48`, README list at `README.md:189`, and public integration usage. The callback proposed by the ticket would be an addition to this deliberate API. |
| `RestSkillHandler` and `RestSkillInvocation` | Supported SPI and Application API value | Sole supported SPI is stated in `README.md:189`; invocation data, including trusted generation ID, is described at `README.md:500`, produced at `SkillGenerationManager.java:168-174`, and consumed in `PublicSkillReloadIntegrationTest.java:240-249`. |
| `DefaultSkillReloader`, `SkillGenerationManager`, `SkillGeneration`, `DefaultSkillTemplate`, `DefaultSkillInvocationHandoff`, `LoomspanSessionRunner`, `FrameworkExecutionLifecycle`, `MissionLifecycle` | Internal or accidentally exposed implementation | They are under `ai.loomspan.internal`; public modifiers serve framework package/auto-configuration collaboration. Architecture reasons include `LoomspanPublicSurfaceArchitectureTest.java:116-118`, `:271-278`. No supported internal bean replacement surface is established. |
| `LoomspanAutoConfiguration` and `loomspan.shutdown.timeout` | Framework integration; Configuration and manifest contracts for the documented key | Bean factory methods at `LoomspanAutoConfiguration.java:222-235`, `:298-324`, `:399-405`; shutdown deadline at `FrameworkExecutionLifecycle.java:128-145`. No new key is proposed by the ticket. |
| Trace/session generation ID and runtime diagnostics | Ephemeral diagnostic formats | Session creation records captured `generation.id()` at `LoomspanSessionRunner.java:269-285`. No retirement record exists. No persisted or serialized retirement contract was identified. |

The architecture test also checks every technically public internal type has an allowlist reason and that API signatures do not leak internal Loomspan types (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:306-336`, `:514-523`). The test and README are separate evidence that `SkillReloader` is supported; its Java `public` modifier alone would not establish that classification.

## Architecture Documentation

The checkout has one declaration authority (`SkillGenerationManager.active`), one publication coordinator (`DefaultSkillReloader`), one root admission and shutdown authority (`FrameworkExecutionLifecycle`), and mission-local physical return state (`MissionLifecycle`). Publication and root admission share the framework lifecycle monitor. Root preparation occurs outside that monitor before direct runner admission or handoff admission. The captured generation travels in the prepared input, then an immutable execution binding and its forks. Normal publication replaces only the active reference; old generations remain usable by holders of those objects. Shutdown uses one deadline for admitted roots and the executor, with cutoff behavior distinct from actual callable return.

## Historical Context (from `ai/thoughts/`)

The PR 8 ticket states the target callback and safety, registration, delivery, and shutdown rules (`ai/thoughts/tickets/loomspan-pr-8-generation-retirement-notification.md`). It references a PR 7 application-supplied-skills ticket, but that linked ticket is not present in this checkout. Application-supplied preparation is already implemented in `SkillGenerationManager.prepare(List<SkillDocument>)` and covered by `PublicSkillReloadIntegrationTest`. There are no prior research or plan documents under `ai/thoughts/research/` or `ai/thoughts/plans/` in this checkout.

## Related Research

None in the current checkout.

## Open Questions

- The planning step needs to select the exact internal ownership handoff between generation capture, pending or direct root admission, root completion, and the later physical worker-return callbacks. The current code exposes these as distinct boundaries; this research records their locations without choosing a design.
- The planning step needs to decide where the callback selection state lives so registration close, publication, and ownership release can be synchronized while consumer calls occur outside lifecycle and publication locks. No listener state exists in this checkout.
