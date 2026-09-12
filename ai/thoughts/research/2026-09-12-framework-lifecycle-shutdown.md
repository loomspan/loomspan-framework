---
date: 2026-09-12T14:11:33-07:00
researcher: Codex (GPT-5)
git_commit: 0ea96dc6e6aced04955c903f2deba0994e43f7e5
branch: main
repository: loomspan-framework
topic: "PR 5.1 — Bound framework shutdown across execution and observation"
tags: [research, codebase, lifecycle, shutdown, session-runner, mission-cancellation, spring]
status: complete
last_updated: 2026-09-12
last_updated_by: Codex (GPT-5)
---

# Research: PR 5.1 — Bound framework shutdown across execution and observation

**Date**: 2026-09-12 14:11:33 PDT
**Researcher**: Codex (GPT-5)
**Git Commit**: 0ea96dc6e6aced04955c903f2deba0994e43f7e5
**Branch**: main
**Repository**: loomspan-framework

## Research Question

Document the current execution, observation, mission cancellation, executor, configuration, and Spring lifecycle paths needed to implement `ai/thoughts/tickets/loomspan-pr-5.1-framework-lifecycle.md`. Identify the exact current owners, executable evidence, supported surfaces, and historical decisions without implementing the feature.

## Summary

The current application entry path is `SkillTemplate` -> `DefaultSkillTemplate` -> `LoomspanSessionRunner` -> `CapabilityExecutionRouter` -> `ExecutionCoordinator`. The runner has separate `runWithNewSession` and `callWithNewSession` implementations; each constructs a session before binding, executes/finalizes under `ExecutionBindingScope`, and returns after the binding is restored. The facade then maps the finalized session and invokes the optional success observer synchronously on the caller, outside its execution-wrapping `try/catch`. There is currently no framework-wide root-admission owner, active-root registry, shutdown deadline, `ContextClosedEvent` listener, or lifecycle-stop waiter.

Mission timeout and cutoff already have a local authority in `MissionLifecycle`. It tracks a mission's owning future and assigned-task futures, rejects new work once cancellation starts, waits up to a fixed 250 ms cleanup grace, snapshots cutoff state, and fences later writes across mission ancestry. Mission registration and future registration already cancel late-registered futures after local cancellation. That authority is created separately for every `MissionContext`; it is not associated with a framework root owner and its cancellation path acquires all ancestry locks while calling the failure recorder. Trace/state writers also execute while holding those ancestry locks, so current cutoff operations can wait behind trace I/O.

The mission executor is one auto-configured virtual-thread-per-task `ExecutorService` shared by direct and step-loop engines, declared with `destroyMethod = "close"`. Current shutdown therefore reaches blocking executor destruction only during singleton destruction, after Spring's lifecycle-stop stage. The current configuration has positive `loomspan.session.mission-timeout` but no `loomspan.shutdown.timeout`. The ticket adds a configuration/behavior contract while retaining the same eight-type supported Application API and the absence of any Supported SPI or bean-replacement surface.

## Detailed Findings

### 1. Root invocation and session construction

- The supported `SkillTemplate` API contains four invocation overloads and promises a fresh execution session; observer overloads use `Consumer<SkillExecutionView>` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:6-18`).
- `DefaultSkillTemplate` normalizes object input, resolves the exact capability, validates input, captures Spring Security authentication, and calls `LoomspanSessionRunner.callWithNewSession` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:60-114`). Input conversion and validation occur before session construction.
- `LoomspanSessionRunner.runWithNewSession` constructs a `LoomspanSession`, binds it with `ExecutionBindingScope.runWith`, captures an action failure, and finalizes in `finally` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:150-191`).
- `callWithNewSession` independently duplicates that construction, binding, failure capture, and finalization around a return value (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:193-234`).
- Session construction creates the optional observation handle and then the canonical trace handle. If trace-handle construction fails after observation registration, the constructor closes the observation before propagating (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:215-269`). Thus the admission lifetime described by the ticket includes failures that can occur before an action receives a session.
- `ExecutionBindingScope` stores one binding in a `ThreadLocal`, permits nesting only within the same session, and restores or removes the previous binding in `finally` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionBindingScope.java:8-77`). Both runner methods return only after that restoration.
- There is no shutdown check or reservation in either runner entry method. A repository-wide search finds no production `ContextClosedEvent`, `ApplicationListener`, `SmartLifecycle`, or framework root-admission type.

### 2. Facade mapping and observer lifetime

- After `callWithNewSession` returns, `DefaultSkillTemplate` maps the session to a public view and invokes the observer synchronously, then returns the textual result (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130`). This placement means mapping and observation run after binding restoration and on the caller thread, but today they are outside any runner-owned lifetime.
- The facade's `try/catch` ends before observer delivery. `AccessDeniedException` and existing `SkillException` instances propagate unchanged; other `RuntimeException`s from resolution, validation, construction, execution, or finalization are wrapped in a safe `SkillException`. Observer and public-view mapping exceptions occur afterward and are not wrapped by that catch (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:94-128`).
- `SkillExecutionViewMapper.map` reads the finalized `ExecutionJournal`, converts every entry through the application-conversion `ObjectMapper`, and constructs immutable public events (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java:16-82`). Mapping can therefore execute journal access and arbitrary mapper conversion work on the caller.
- `LoomspanSession.finalizeTrace` projects the journal and finalizes the trace under the session lock, releases that lock, and then closes the optional observation handle (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:677-752`). `LoomspanSessionRunner.completeSession` preserves the original action failure and suppresses an eligible cleanup failure instead of replacing it (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:287-313`).
- Existing tests establish successful observer delivery and invalid-input suppression (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:203-220`, `:265-283`). `observerExceptionPropagatesAfterExecutionCompletes` establishes that a success observer's exception propagates unchanged after skill execution (`DefaultSkillTemplateTest.java:243-263`).
- Runner tests cover action failure, trace finalization, construction failure after observation registration, optional-observation failure isolation, and finalization failure (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java:55-144`, `:343-476`). They do not currently assert framework root admission/release cardinality.

### 3. Root and nested mission creation

- `CapabilityExecutionRouter` requires the explicit session's current binding, revalidates normalized input, and delegates to `ExecutionCoordinator` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:39-68`).
- `ExecutionCoordinator` derives `topLevelInvocation` from the absence of a parent mission, verifies the session entry skill for a top-level call, constructs a new `MissionContext`, and binds it for the execution (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:77-103`). Nested skills use the same method with the currently bound mission as parent.
- Every `MissionContext` owns a new `MissionLifecycle` and holds its parent mission. This creates an ancestry chain but no root/framework lifecycle reference (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionContext.java:17-58`).
- Direct Java and model-backed missions both submit their owning callable to the shared `MissionWorkExecutor`; the step-loop engine performs equivalent owning submission itself. Nested admission is enforced through the mission ancestry, not through a framework root gate (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-62`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:223-310`).
- On ordinary coordinator completion, a mission frame is closed and the mission lifecycle is closed once. For top-level invocation, retained root state and the canonical trace are finalized (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:167-220`). The runner's standalone finalizer observes an already-completed trace and returns (`LoomspanSessionRunner.java:236-284`).

### 4. Existing mission admission, cancellation, and fences

- `MissionLifecycle` is the mission-bounded admission, cancellation, write-fence, and cleanup authority. Its states are `OPEN`, `CANCELLING`, and `CLOSED`; the first cancellation owns the cause, failure ID, and deadline (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:21-52`, `:172-205`).
- The cancellation deadline is currently `System.nanoTime()` plus the fixed `CLEANUP_GRACE` of 250 ms (`MissionLifecycle.java:24`, `:185-195`). `awaitCutoff` waits only for physically started work to return or that local deadline to expire (`MissionLifecycle.java:215-238`, `:386-408`).
- `registerOwningFuture` and `registerFuture` store the future under the lifecycle lock and cancel it immediately if the lifecycle is no longer open (`MissionLifecycle.java:63-82`, `:136-148`). This is the current executable pattern for registration concurrent with an already-reached local cutoff.
- Step-loop unit admission is all-or-none under the complete mission ancestry. Task starts require the complete ancestry to remain open; outcome publication and other writes are rejected after an ancestor closes (`MissionLifecycle.java:95-158`, `:302-365`).
- The step-loop engine calls `requireOpenForNewWork` before units, before each concurrent submission, and before final synthesis. It registers submitted futures with the mission lifecycle (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:387-455`, `:502-515`).
- Direct mission timeout/interruption begins cancellation, waits for cutoff, performs cutoff-authorized frame cleanup, and preserves the primary failure (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:60-97`, `:100-138`). The direct path has no `CancellationException` catch.
- The step-loop owning-future path handles `TimeoutException`, interruption, execution failure, and `CancellationException`. Its `CancellationException` branch expects a previously installed primary cancellation, performs cleanup, and propagates that primary (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:308-355`).
- `JavaSkillMissionCutoffTest` verifies a locally timed-out or interrupted direct Java root returns while uncooperative work remains blocked, fences its later write, and closes its trace once (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/JavaSkillMissionCutoffTest.java:25-107`).
- `MissionLifecycleTest` verifies monotonic close, first-cancellation ownership, atomic unit admission, immediate cancellation of a late-registered future, physical-return cutoff, ancestor fencing, and cleanup ordering (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/MissionLifecycleTest.java:24-243`). Step-loop tests cover caller interruption, timeout cleanup, late-write suppression, grouped-task cutoff, and stopping later work (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:172`, `:1027`, `:1059`, `:1132`, `:1201`, `:1274`, `:1313`, `:1354`).

### 5. Ancestry locking and trace-I/O interaction

- `runWithAncestry` obtains every `MissionLifecycle` lock from root to leaf and holds them while executing the supplied action (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:302-321`). `runWhileOpen` uses the same lock ordering for new-work admission (`MissionLifecycle.java:324-343`).
- `beginCancellation` uses `runWithAncestry` and invokes its supplied failure recorder while those locks are held (`MissionLifecycle.java:172-203`). Current failure recorders call `ExecutionStateService.recordFailure`, which writes to the session trace (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:64-80`; `DefaultExecutionStateService.java:457-463`).
- State and trace methods such as open/close frame, tool records, plan mutations, success credit, linter/output outcomes, and failure records wrap their work in `binding.runIfWritable` or `requireWritable`; the trace/session operation executes inside the ancestry-locked action (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/state/DefaultExecutionStateService.java:71-117`, `:277-283`, `:330-365`, `:387-463`).
- Current tests explicitly demonstrate that `closeNow` waits for a writer holding the lifecycle lock and that an ancestor close waits for child cleanup already admitted under ancestry locks (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/MissionLifecycleTest.java:76-101`, `:261-282`). This is the current lock behavior relevant to the ticket's blocked-trace-writer deadline proof.
- Successful direct-skill credit is also written through the same fence (`DefaultExecutionStateService.java:387-401`). Once a lifecycle closes, `runIfWritable` returns false, preventing resumed work from adding that credit.

### 6. Executor ownership and current destruction

- Auto-configuration creates one `Executors.newVirtualThreadPerTaskExecutor()` bean named `LoomspanMissionExecutor` with explicit `destroyMethod = "close"` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:338-343`).
- The same executor is injected into `MissionWorkExecutor` for direct/standard mission ownership and into `StepLoopMissionExecutionEngine` for owning missions and grouped workers (`LoomspanAutoConfiguration.java:345-389`).
- `ExecutorService.close()` is blocking executor destruction on Java 21. No framework component currently calls `shutdown`, `shutdownNow`, or `awaitTermination` during Spring lifecycle stop.
- Auto-configuration declares all framework beans as package-private factory methods and has no `@ConditionalOnMissingBean` seams. `LoomspanAutoConfigurationBoundaryTest` maintains an exact core-bean-factory set and asserts the supported bean-override allowlist is empty (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanAutoConfigurationBoundaryTest.java:28-80`, `:112-135`). Any new lifecycle bean becomes part of this internal framework-owned classification rather than an application SPI.

### 7. Current Spring lifecycle mechanics in the pinned dependency

- The parent build pins Spring Boot `4.1.0`; the resolved local dependency is Spring Context `7.0.8` (`pom.xml:49-59`; `C:/Users/mgiacomi/.m2/repository/org/springframework/spring-context/7.0.8/spring-context-7.0.8.jar`).
- Inspection of the matching `spring-context-7.0.8-sources.jar` shows `AbstractApplicationContext.doClose` publishes `ContextClosedEvent`, then invokes `LifecycleProcessor.onClose`, then destroys singleton beans. Listener exceptions are logged and do not skip the later lifecycle or destruction stages.
- `ApplicationListener.supportsAsyncExecution()` defaults to `true`. A listener that must execute synchronously on the publisher thread must override it to return `false`.
- `DefaultLifecycleProcessor` groups lifecycle beans by phase in descending shutdown order. Within `doStop`, dependent beans are stopped before the bean they depend on. For `SmartLifecycle`, it invokes `stop(Runnable)` before waiting on the phase latch.
- The per-phase timeout applies to the later latch wait. A synchronous `stop(Runnable)` implementation that performs its own bounded wait before invoking its callback consumes its own elapsed time before Spring begins the phase-latch timeout, which is the current dependency behavior behind the ticket's requirement that a shorter Spring timeout not truncate the framework's remaining deadline.
- `SmartLifecycle.DEFAULT_PHASE` is `Integer.MAX_VALUE`; executors/schedulers may use their own different default phase. There are no Loomspan lifecycle phases or dependencies in the current code.

### 8. Configuration and documentation surface

- `LoomspanProperties` is strict (`ignoreUnknownFields = false`) and validated. Its current root groups are `session`, `skills`, `observability`, `connections`, and `models`; there is no `shutdown` group (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:21-104`).
- The existing positive duration pattern is `Session.missionTimeout`, default 60 seconds, with a setter that rejects null, zero, and negative values (`LoomspanProperties.java:274-302`). `LoomspanSessionPropertiesTest` verifies default/explicit binding and zero-duration startup failure (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionPropertiesTest.java:27-86`).
- Spring configuration metadata is generated by the configuration processor and supplemented by `META-INF/additional-spring-configuration-metadata.json`. `ConfigurationMetadataTest` reads the generated metadata and asserts selected documented properties (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/ConfigurationMetadataTest.java:15-83`).
- README operations guidance currently documents only session safeguards and the 60-second mission timeout (`README.md:454-477`). Invocation guidance documents the closed API and successful observer behavior (`README.md:139-170`). There is no current shutdown configuration or admission-rejection guidance.

### 9. Contract and compatibility inventory

#### Application API

- The executable allowlist contains exactly eight public `ai.loomspan.api` types, including `SkillTemplate`, `SkillExecutionView`, and `SkillExecutionEvent` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:30-37`, `:276-303`).
- PR 5.1 changes no supported method signature. It deliberately changes top-level invocation behavior after framework shutdown begins, as authorized by the ticket's Pipeline notes. Existing success-observer exception propagation remains protected behavior.

#### Supported SPI

- None exists. `LoomspanPublicSurfaceArchitectureTest` rejects a Loomspan-specific SPI package/type (`LoomspanPublicSurfaceArchitectureTest.java:324-329`), and `LoomspanAutoConfigurationBoundaryTest` keeps the supported bean-override allowlist empty (`LoomspanAutoConfigurationBoundaryTest.java:53-65`).

#### Configuration and manifest contracts

- `loomspan.shutdown.timeout` is a new documented configuration contract required by the ticket. Current code, metadata, README, and tests do not contain it.
- No manifest syntax changes belong to this ticket. Existing skill manifests, model selection, quotas, depth, and `loomspan.session.mission-timeout` remain the executable path for admitted work.

#### Persisted or serialized contracts

- No new persisted format is requested. The existing canonical NDJSON trace finalization and public view mapping participate in the lifetime but their schema is not changed by this ticket.

#### Ephemeral diagnostic formats

- Canonical trace/journal writes and `SkillExecutionView` projection are current-run diagnostic behavior. The ticket changes fencing/lifetime around them, not the event schema. No Java-to-Go Console REST/SSE, problem, acquisition, or consumed-NDJSON boundary is changed, so there is no coordinated Console protocol fixture delta for PR 5.1.

#### Internal or accidentally exposed implementation

- `LoomspanSessionRunner`, `MissionLifecycle`, `MissionContext`, `MissionWorkExecutor`, `DefaultSkillTemplate`, `ExecutionCoordinator`, auto-configuration bean methods, and any new lifecycle owner are internal/integration implementation even where Java visibility is public. Current architecture tests explicitly classify these surfaces and do not establish compatibility obligations (`LoomspanPublicSurfaceArchitectureTest.java:263-267`; `LoomspanAutoConfigurationBoundaryTest.java:68-135`).
- The ticket authorizes one coherent internal root path without compatibility shims for obsolete internal overload/decomposition. In-repository constructors and tests are usage evidence, not supported Application API or SPI.

### 10. Documentation-skill comparison and drift classification

- The installed `loomspan-docs` skill declares `loomspan-version: 0.1.0-SNAPSHOT`, while this checkout's bundled skill declares `1.0.0-beta.4-SNAPSHOT` (`C:/Users/mgiacomi/.codex/skills/loomspan-docs/SKILL.md`; `agent-skills/loomspan-docs/SKILL.md`). The skill is therefore not version-aligned with this checkout and was not used for version-sensitive shutdown claims.
- For existing invocation/observation behavior, the installed Java API topics describe the same four overloads, validation-before-session path, synchronous post-success observer, and unchanged observer-exception propagation established by the live source and focused tests. Drift classification: **aligned** for those pre-existing semantics.
- The installed skill has no `loomspan.shutdown.timeout`, admission closure, or framework deadline coverage. Because it is revision-mismatched and predates this feature, drift classification for PR 5.1 shutdown semantics is **unresolved** as a comparison with the installed skill; the binding ticket and current checkout are the governing inputs.
- Skill-authoring impact is absent for manifest syntax, skill inputs, planning, evidence, models, capability visibility, or author testing. The change is application/framework operations guidance, so current repository documentation work belongs in README/configuration metadata rather than the skill-authoring knowledge set.

## Code References

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:150-234` — duplicated root run/call session operations.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:91-130` — facade wrapping boundary, mapping, observer, and result return.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:215-269` — observation/trace construction order and construction cleanup.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:77-103` — authoritative current top-level-versus-nested mission derivation.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:63-238` — future registration, cancellation ownership, fixed grace, and cutoff wait.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/MissionLifecycle.java:302-408` — ancestry locking, admission/write checks, and cutoff snapshot.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/MissionWorkExecutor.java:28-97` — direct mission owning future and timeout/cancellation path.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:295-355` — step-loop owning future and `CancellationException` handling.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:338-389` — shared executor and both engine consumers.
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:274-302` — existing positive-duration binding pattern.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/JavaSkillMissionCutoffTest.java:25-107` — direct uncooperative-worker fence proof.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/MissionLifecycleTest.java:24-282` — current mission lifecycle concurrency/cutoff proof.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:276-342` — closed API/SPI architecture checks.
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanAutoConfigurationBoundaryTest.java:28-135` — internal bean classification and no replacement seams.

## Architecture Documentation

The current system has three nested lifetime scopes:

1. A facade invocation creates one session in `LoomspanSessionRunner`.
2. `ExecutionCoordinator` creates a root `MissionContext`, and nested capability calls create child missions linked by parent references.
3. Each mission has its own `MissionLifecycle`, which owns mission-local futures, admission, cancellation, and late-write fencing.

Execution identity is propagated by an explicitly restored thread-local binding. The mission executor runs submitted work with a captured binding, while the application caller waits on the owning future. Canonical trace/session finalization happens before the runner returns. Public mapping and the optional observer remain synchronous on the application caller after the binding is restored.

Spring currently owns only bean construction and destruction for this path. The context-close sequence is event publication -> lifecycle stop -> singleton destruction. The shared mission executor is destroyed in the last stage by blocking `close()`. PR 5.1's durable architectural boundary is therefore between root-session admission/ownership, mission registration/cutoff, and Spring's event/lifecycle/destruction stages; Sidecar is not a dependency or participant in the framework owner.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/tickets/loomspan-pr-5.1-framework-lifecycle.md` is the binding delivery ticket. Its Pipeline notes explicitly authorize shutdown-time top-level rejection without a legacy path or shim and require success-observer compatibility.
- `ai/thoughts/phases/phase-fw1.md:161-221` records the shared framework shutdown design: one positive 30-second configuration, atomic root admission, admitted nested work, independent prompt listeners, later lifecycle waiting, one root operation through observer delivery, lock-safe cutoff, and nonblocking destruction.
- `ai/thoughts/beta4-code-grounding.md:312-329` records that bounded shutdown was not established at historical commit `1e4eb455`; `:366-479` records resolved G3 deadline ownership and current reuse anchors.
- `ai/thoughts/beta4-design-review.md:44-67` records accepted S1, one root operation through execution and observation; `:190-233` supersedes listener ordering with independent listeners and records the lock and observer-lifetime follow-through.
- `ai/thoughts/beta4-ticket-readiness.md:33-52` separates framework lifecycle from later REST/external-caller work; `:74-88` states existing tests did not prove the new lifecycle; `:94-114` classifies the new configuration and intentional root-rejection behavior.
- `ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md:34-46` identifies independent listeners and one root completion path as settled product decisions. Its Sidecar discussion remains historical context only; actual Sidecar wiring/proof is outside PR 5.1.

## Related Research

No prior document exists under `ai/thoughts/research/`. The source-grounding and design-review documents above are the related historical artifacts.

## Open Questions

These are planning-level implementation choices, not unresolved product decisions or developer escalations:

- Select the exact internal owner/type decomposition that atomically closes root admission, tracks admitted roots through caller-side mapping/observation, stores the one monotonic deadline, and exposes mission registration/cutoff without adding API or SPI.
- Select the internal cutoff representation that lets the shutdown waiter signal/fence roots and missions without waiting on ancestry locks or running failure-recording/trace I/O, while allowing caller/worker cleanup to preserve existing primary-failure semantics.
- Select the `SmartLifecycle` phase and bean dependencies that keep application caller workers, handler clients, and the mission executor usable until completion/cutoff across the supported embedded-context shapes. Spring 7.0.8's synchronous stop-before-phase-wait behavior permits the framework's remaining budget to be honored even when the phase timeout is shorter.
- Select the internal exception used when framework cutoff cancels an owning future. It must integrate with the existing step-loop `CancellationException` branch and add equivalent direct-path primary/cleanup handling without creating public cancellation API.
- Determine the focused test fixture decomposition for listener order, asynchronous multicaster opt-out, parent/child context filtering, repeated close, failed startup, skipped/failed event delivery, Spring timeout overlap, blocked trace I/O, observer/mapping ownership, and destruction fallback. The repository currently has no framework lifecycle fixture.

No open question changes the binding observable behavior, compatibility, security, correctness, or scope established by the ticket; step 2 can resolve these from the evidence above.
