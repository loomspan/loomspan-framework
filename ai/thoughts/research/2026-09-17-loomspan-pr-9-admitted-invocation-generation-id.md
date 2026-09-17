---
date: 2026-09-17T12:30:52-07:00
researcher: Codex
git_commit: b133b10f5677ae66b9bcd0eb1208de6c2a0dc585
branch: main
repository: loomspan-framework
topic: "Captured generation identity on admitted invocations"
tags: [research, codebase, handoff, generation, reload, public-api]
status: complete
last_updated: 2026-09-17
last_updated_by: Codex
---

# Research: Captured generation identity on admitted invocations

**Date**: 2026-09-17 12:30:52 PDT  
**Researcher**: Codex  
**Git Commit**: `b133b10f5677ae66b9bcd0eb1208de6c2a0dc585`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research Question

Document the current handoff, captured generation, REST identity, ownership, retirement, shutdown, public surface, tests, and guidance relevant to `ai/thoughts/tickets/loomspan-pr-9-admitted-invocation-generation-id.md`.

## Summary

Both `SkillInvocationHandoff` overloads delegate to `DefaultSkillTemplate` preparation and return the same internal admitted-handle implementation. Preparation captures the active `SkillGeneration` and an owner lease before object conversion or map validation. The prepared generation and lease remain in an atomic payload until execution, release, or pending cutoff. `AdmittedSkillInvocation` currently declares two `invoke` methods and `release`; it does not declare `generationId()` (`src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:12-35`, `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:28-109`).

The captured `SkillGeneration.id()` is the same value installed in its catalog, passed to the root session and execution binding, and captured by generated REST capability invokers. Retirement follows owner leases and physical completion; shutdown can clear a pending handle's payload. The existing integration and focused tests exercise old-generation execution, REST routing, pending admission retirement, release, cutoff, and public method shape, but no admitted-handle ID accessor exists in the current checkout.

## Detailed Findings

### Public declarations and supported contract

- `SkillInvocationHandoff` has object and map overloads that return `AdmittedSkillInvocation`. Its class-level documentation describes preparation, authentication capture, pending execution during shutdown, and cutoff (`src/main/java/ai/loomspan/api/SkillInvocationHandoff.java:6-32`).
- `AdmittedSkillInvocation` currently exposes `invoke()`, `invoke(Consumer<SkillExecutionView>)`, and `release()`. Its documentation defines one execution claim, idempotent release, and cutoff behavior (`src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:6-35`).
- `SkillCatalog.generationId()` and `PreparedSkillUpdate.generationId()` expose generation identity on snapshot and candidate objects (`src/main/java/ai/loomspan/api/SkillCatalog.java:14`, `src/main/java/ai/loomspan/api/PreparedSkillUpdate.java:6`). `RestSkillInvocation` has a generation-ID record component and rejects null or blank IDs (`src/main/java/ai/loomspan/api/RestSkillInvocation.java:11-20`). `SkillExecutionView` consists of session ID and events; it has no generation-ID component (`src/main/java/ai/loomspan/api/SkillExecutionView.java:6`).
- `LoomspanPublicSurfaceArchitectureTest` allowlists `AdmittedSkillInvocation`, `SkillInvocationHandoff`, the catalog and reload types, `RestSkillHandler`, and `RestSkillInvocation` among nineteen supported public top-level `ai.loomspan.api` types. It classifies `DefaultSkillInvocationHandoff` and `SkillGenerationManager` as internal despite their public modifiers (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:28-48,272-278`). `ApplicationApiValueTest` asserts the exact current handoff and admitted-handle method shapes (`src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:50-66`).

### Capture, preparation, and handoff

- `SkillGenerationManager.capture()` reads the active generation under `generationMonitor`, increments that published generation's owner count, and returns a `Capture(generation, OwnerLease)` pair (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:155-166`).
- Object preparation calls `capture()` before null rejection and Jackson conversion. Map preparation calls `capture()` before exact skill lookup and input validation. Preparation closes the lease on failures and returns `PreparedInput(generation, capability, validation, lease)` on success (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-177,314-316`). `SkillGenerationExecutionIntegrationTest#capturePrecedesObjectConversionAndInputValidation` exercises publication during object conversion (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:39-87`).
- Both public handoff overloads pass their prepared input into `admit`. That method captures current authentication, passes the prepared lease to `FrameworkExecutionLifecycle.admitRoot`, and creates the admitted handle. Its error paths close the lease (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:28-64`). Auto-configuration exposes this implementation as a `SkillInvocationHandoff` bean (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:320-323`).
- The handle's `AtomicReference<Payload>` contains the prepared input and authentication. A pending termination callback sets that reference to null. `invoke` atomically clears it, then calls `template.invokePrepared`; `release` clears it and closes the admitted root (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:66-108`).

### Generation ID through execution and REST work

- `SkillGeneration` validates a nonblank ID at construction and uses it when constructing its default `SkillCatalog` (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:19-52,65`). `SkillGenerationManager.prepareCatalog` issues an ID from a manager-local UUID namespace and an increasing counter. It installs that ID in the new generation and captures it in every REST capability invoker assembled for that generation (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:43-46,101-140`).
- `DefaultSkillTemplate.invokePrepared` passes `prepared.generation()` into `LoomspanSessionRunner.callWithAdmittedSession` (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:228-247`). The runner constructs the root session with `generation.id()` and installs the same generation in `ExecutionBindingScope` for the root action (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:274-338`). Nested work uses the execution binding's generation, while REST capability invokers created with that generation call the fixed handler with a `RestSkillInvocation(name, arguments, generationId)` (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:122-132,282-288`).
- `SkillGenerationExecutionIntegrationTest#capturedHandoffUsesOneGenerationAfterActivationAndNewRootUsesReplacement` observes the old execution binding after activation of a replacement (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:90-139`). `PublicSkillReloadIntegrationTest` runs supported REST handoffs across publication and checks old-versus-new handler configuration selection (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:35-87,133-177,220-310`).

### Admission ownership, retirement, and shutdown

- `FrameworkExecutionLifecycle` holds admitted roots in `activeRoots`. Claim changes a pending root to executing; release removes a pending root, runs its pending termination callback, and releases generation ownership when safe. Execution completion likewise removes the root and checks ownership (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:60-135`).
- The admitted root stores the generation owner lease. Its release check waits until the root is no longer pending or executing and its registered missions have physically completed (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:309-352`). Closing an `OwnerLease` decrements the generation owner count and can select a superseded generation for retirement notification (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:178-196,211-238`).
- Framework close first closes admission and starts one deadline. At cutoff, pending roots move to `CUT_OFF`, their termination callbacks run, and ownership release is checked; running missions receive cutoff signals (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:138-170,248-274`). `DefaultSkillTemplateTest#releasedAndCutOffHandoffsCannotExecute` checks release and payload clearing after cutoff; `FrameworkShutdownIntegrationTest#handedOffInvocationStartsAfterFrameworkCloseWithoutReacquiringAdmission` checks the pending execution window (`src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:104-137`, `src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:41-84`).
- `PublicSkillReloadIntegrationTest#retirementWaitsForPendingOldGenerationThenCleansUpAfterRelease` checks retirement following release, success, and failure of old admitted REST work. Its application-owned map is keyed by `RestSkillInvocation.generationId()` (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:35-87,299-310`).

### Documentation, configuration, and other surfaces

- The README describes the supported nineteen-type Java surface, the handoff example and single-use behavior, current snapshot and candidate IDs, REST handler routing, process-local IDs, and retirement semantics (`README.md:141,169-221,503-505`). The handoff example currently releases only if its `invoked` flag remains false (`README.md:172-184`).
- The repository's `loomspan-docs` skill has matching Maven version `1.0.0-beta.5-SNAPSHOT` (`agent-skills/loomspan-docs/SKILL.md:5-12`, `pom.xml:8-9`). Its Java API invocation, reload, and REST topics describe the current handoff lifecycle, captured generation behavior, process-local IDs, and application-owned configuration (`agent-skills/loomspan-docs/references/java-api/invocation.md`, `skill-reload.md`, `rest-skills.md`). Its compatibility topic classifies the nineteen API types and `RestSkillHandler` as the sole supported SPI (`agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`).
- Drift classification: **aligned** for the existing capture, reload, REST, and handoff behavior compared above. The admitted-handle accessor in the ticket is absent from both implementation and guidance in this pre-change checkout. The change affects application code invoking Loomspan, with no observed skill-manifest syntax, skill-authoring input, or author-facing execution rule change; the skill-authoring topic set has no identified change surface from this ticket.
- The ticket does not change Spring property keys, YAML manifests, persistence schemas, serialized protocols, or trace formats. The observable delta requested by the ticket is a supported Application API interface method. `RestSkillHandler` remains the Supported SPI, while `DefaultSkillInvocationHandoff`, `SkillGeneration`, `SkillGenerationManager`, and lifecycle classes are Internal or accidentally exposed implementation. The existing configuration and manifest contracts, persisted or serialized contracts, and ephemeral diagnostic formats are distinct categories with no requested delta here (`ai/thoughts/framework-feature-design-lens.md`, `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`).

## Code References

- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-177` — capture precedes object conversion and map validation.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:28-109` — shared handoff path, atomic payload, invoke, and release.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:101-166,211-238,282-288` — ID creation, captured REST invokers, owner leases, and retirement.
- `src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:60-135,248-274,309-352` — admission state, cutoff, and owner release.
- `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:274-338` — captured generation in root execution.
- `src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:35-87,133-177,220-310` — supported API reload and REST fixtures.

## Architecture Documentation

The supported Java API is a closed type allowlist in `ai.loomspan.api`; public internal and auto-configuration Java signatures are not application extension contracts. The handoff handle is an application-facing interface backed by an internal implementation. Capture and ownership are managed by `SkillGenerationManager`, root admission and cutoff by `FrameworkExecutionLifecycle`, and execution by `LoomspanSessionRunner`. REST invokers close over their generation ID when a complete generation is prepared, so handler metadata follows the selected generation rather than a later active-catalog lookup.

## Historical Context (from ai/thoughts/)

- `ai/thoughts/framework-feature-design-lens.md` defines the contract categories and emphasizes the closed public API, process-local runtime facts, coherent in-repository consumer updates, and the distinction between internal visibility and supported application contracts.
- `ai/thoughts/tickets/loomspan-pr-9-admitted-invocation-generation-id.md` is the current request. Its pipeline note explicitly authorizes an abstract interface method without a compatibility shim during development. No earlier research artifact for this exact handoff-ID question was found in `ai/thoughts/research/`.

## Related Research

No related research document for this exact topic was found in the current `ai/thoughts/research/` tree.

## Open Questions

None requiring a developer decision at the research stage. The planning step will determine exact test placement and documentation wording from the mapped source and fixtures.
