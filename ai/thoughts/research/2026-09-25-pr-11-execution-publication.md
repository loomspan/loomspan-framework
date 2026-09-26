---
date: 2026-09-25T19:56:52-07:00
researcher: Codex (GPT-6)
git_commit: ca46017ec06eec7e5a8f4b4ea1f4ca9aaad74915
branch: main
repository: loomspan-framework
topic: "PR 11 — publish execution configuration together with skills"
tags: [research, codebase, publication, execution-configuration, lifecycle]
status: complete
last_updated: 2026-09-25
last_updated_by: Codex (GPT-6)
---

# Research: execution configuration publication

**Date**: 2026-09-25T19:56:52-07:00  
**Researcher**: Codex (GPT-6)  
**Git Commit**: `ca46017ec06eec7e5a8f4b4ea1f4ca9aaad74915`  
**Branch**: `main`  
**Repository**: `loomspan-framework`

## Research question

Map the current supported publication path, configuration wiring, root capture and physical-work lifetime, connection creation, validation, traces, tests, and documentation relevant to `ai/thoughts/tickets/loomspan-pr-11-publish-execution-configuration.md`. This records the checkout before implementation.

## Summary

The supported `SkillReloader` currently validates, prepares, and publishes a complete skill set. Its `PreparedSkillUpdate` exposes a generation ID and candidate catalog. A `SkillGeneration` freezes skill declarations and capability metadata; publication swaps the active generation under the manager's monitor. Root preparation captures that generation with an ownership lease before input conversion and validation, and the framework lifecycle retains the lease through physical work. Connection clients, model resolution, session limits, attachment size, and trace persistence are separately constructed from startup properties. `ExecutionTraceProperties` currently binds top-level `execution-trace` with `ONERROR` as the default.

## Detailed findings

### Supported API and skill publication

- `SkillReloader` declares `validate()` and `validate(Collection<SkillDocument>)`, matching `prepare` overloads, `publish`, `snapshot`, and a retirement listener (`src/main/java/ai/loomspan/api/SkillReloader.java:8-39`). `PreparedSkillUpdate` exposes `generationId()` and `snapshot()` (`src/main/java/ai/loomspan/api/PreparedSkillUpdate.java:4-9`). These are on the closed API allowlist (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:20-47`); `RestSkillHandler` is the sole supported SPI per the repository guidance and README (`README.md:192`).
- `DefaultSkillReloader` copies supplied document collections, serializes preparation, records the active base ID, and wraps the prepared `SkillGeneration` in an opaque owner-bound candidate (`src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:33-72,126-144`). Publication checks same-owner, single-use, current-base, and open admission before activating; retirement notifications dispatch after its publication lock (`:76-106`). `snapshot()` reads the active generation's immutable skill catalog (`:109-110`).
- `SkillGenerationManager` creates catalog instances for configured or supplied documents and checks candidate declarations against fixed Java capabilities and the fixed REST handler (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:89-119,133-190`). Valid feedback contains fixed Java and proposed YAML/REST metadata; errors produce an empty skill list (`:121-131`). Preparation creates a fresh process-local ID, capability map, REST invokers carrying that ID, and immutable generation (`:199-234`). It does not build provider resources.
- `SkillGeneration` stores ID, capabilities, YAML/REST definitions, public catalog, and registered catalog in immutable maps (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:15-69`). `SkillGenerationManager.activateAndSelect` swaps the active reference and marks the previous published generation superseded while holding one monitor; `capture()` increments an owner count under that same monitor (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:241-265`). Retirement selection requires supersession and zero owners; `OwnerLease.close()` decrements owners and dispatches the notification (`:284-339`).

### Root admission and execution lifetime

- `DefaultSkillTemplate.prepareObject/prepareMap` captures a generation before object conversion and input validation, uses its capability/input contract, and closes the lease on failure (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:113-181`). Invocation passes the captured generation and lease to `LoomspanSessionRunner` (`:226-246`). `DefaultSkillInvocationHandoff` retains the prepared generation ID on the admitted public handle (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:69-110`).
- `FrameworkExecutionLifecycle.admitRoot` accepts a generation owner (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:55-70`). `AdmittedRoot` releases that owner when pending admission is abandoned or execution and registered physical work have completed; physical completion calls `releaseGenerationIfSafe` (`:305-354`). Shutdown closes admission, waits within one configured timeout, then publishes a cutoff (`:138-157,169-274`). `LoomspanSessionRunner` hands the generation owner to root admission and releases it if admission fails (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:238-271`).
- The captured generation is used for nested capability lookup and ownership checks (`src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:97,487-497`; `src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:67`). REST calls carry its process-local ID (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:220-229,368-373`). Trace start metadata also records `generationId` (`src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:406`).

### Startup configuration, model resolution, and resources

- `LoomspanProperties` binds strict `loomspan.*` startup configuration (`src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:27-52`). It contains `session`, `shutdown`, `skills`, `observability`, named `connections`, and model aliases (`:31-53`). Connection and model cross-checks occur in `afterPropertiesSet()` (`:124-202`). Session fields include depth, mission timeout, attachment size, and skill/tool/linter/model/provider/usage quotas (`:308-373`). Skills contain resource locations (`:380-386`). Observability contains enablement, auth, and retention (`:393-427`). Connections contain driver, key, headers, provider options and retry policy (`:430-532`); aliases name a connection, provider model, and thinking levels (`:534-554`).
- `LoomspanAutoConfiguration` enables `LoomspanProperties` and `ExecutionTraceProperties` (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:70-75`). It snapshots only skill locations, connection driver, and model alias data for `YamlSkillCatalog` (`:162-210`); this is the validation path for configured and supplied skill documents. The same configuration creates `LoomspanSessionRunner` with startup depth, quotas and persistence (`:143-160`), attachment materializer with startup max size (`:250-259`), usage service with startup quotas (`:299-306`), mission/step engines with startup mission timeout (`:342-385`), and lifecycle with startup shutdown timeout (`:328-339`).
- `LoomspanAiAutoConfiguration` creates `NamedAiConnectionRegistry` from `properties.getConnections()` and then model resolver and interaction factory (`src/main/java/ai/loomspan/autoconfigure/LoomspanAiAutoConfiguration.java:28-78`). The registry creates a runtime for each connection, verifies exact attempt ownership when retry is enabled, and cleans already built models after a construction failure (`src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java:16-51,81-101`). It owns normal Spring destruction of its models (`:74-101`). Provider creation passes configured keys into client builders and loads Gemini credential URI during construction (`src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:114-174,480-490`).
- `ExecutionTraceProperties` is a separate binding type with `@ConfigurationProperties(prefix = "execution-trace")`; null resets persistence to `ONERROR` (`src/main/java/ai/loomspan/autoconfigure/ExecutionTraceProperties.java:8-25`). The session runner receives that policy at bean construction (`src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:143-160`).

### Tests, fixtures, and documented behavior

- `PublicSkillReloadIntegrationTest` exercises public two-stage publication, complete supplied documents, pending handoff, generation correlation, shutdown, and stale candidates (`src/test/java/ai/loomspan/integration/PublicSkillReloadIntegrationTest.java:45-365`). `SkillReloaderTest` covers manager ownership, publication races, retirement and shutdown (`src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:33-460`). `SkillGenerationManagerTest` covers validation, fixed capabilities/handler, and fresh IDs (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:39-450`). `SkillGenerationExecutionIntegrationTest` covers execution across generation changes (`src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java:51`).
- Configuration coverage includes `LoomspanPropertiesTest`, `LoomspanSessionPropertiesTest`, `NamedAiConnectionRegistryTests`, `AiConnectionChatModelFactoryTests`, `ConnectionProtocolTest`, and `SensitiveConnectionDataRedactionTest` under `src/test/java/ai/loomspan/`. The architecture allowlist is `LoomspanPublicSurfaceArchitectureTest`; it separately classifies public `autoconfigure` and internal declarations (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:20-75,220-260`).
- README documents the current closed API, skill update sequence, immutable startup catalog, generation correlation, session defaults, and top-level `execution-trace` example (`README.md:192-240,548-572`). `agent-skills/loomspan-docs/references/java-api/skill-reload.md` documents the same two-stage skill-only contract, including candidate failure/retirement semantics. `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md` records alias, connection, and retry semantics; its README routes model selection, validation, and trace authoring topics. The skill says it applies to the bundled `1.0.0-beta.6-SNAPSHOT` revision (`agent-skills/loomspan-docs/SKILL.md:1-25`).

## Contract classification

| Surface | Lens category | Current evidence and consumer |
| --- | --- | --- |
| `SkillReloader`, `PreparedSkillUpdate`, `SkillDocument`, catalogs, handoff ID | Application API | Closed allowlist, README, Java API docs, and public integration tests establish support. |
| `RestSkillHandler` | Supported SPI | Sole allowlisted SPI, README and REST integration tests; its invocation carries captured generation ID. |
| `loomspan.*`, top-level `execution-trace.persistence`, YAML model/skill fields | Configuration and manifest contracts | Binders, validation, README/authoring guidance, and tests establish current syntax and defaults. The ticket explicitly authorizes the trace-key move. |
| Generation ID in trace start/live activity | Ephemeral diagnostic formats | Trace writer and live projector use it for current-run correlation; the ID is process-local. |
| `LoomspanProperties`, `ExecutionTraceProperties`, Spring beans, generation manager, registries, provider clients | Internal or accidentally exposed implementation | Autoconfigure and internal packages are outside the supported Java API per repository guidance; their current behavior is technically exposed in wiring and tests. |
| Durable publication snapshot | Persisted or serialized contracts | The framework has no persisted publication format in the inspected path. The host owns any durable snapshot associated with process-local generation IDs. |

The separate Go Console protocol is not the Sidecar authoring boundary identified by this ticket. Existing trace generation metadata is nevertheless observable by current-run trace readers and live activity projection (`DefaultExecutionTraceHandle.java:406`; `LiveActivityProjector.java:80-83`).

## Architecture documentation and historical context

The existing manager is the single active-generation authority and owns generation capture/retirement. Startup Spring properties feed separate fixed execution services and connection registry. The ticket's planned publication expands what a generation must hold; this research does not choose the public type shape or internal decomposition. `ai/thoughts/framework-feature-design-lens.md` supplies the above contract categories and requires supported-surface and consumer assessment. No earlier PR 11 research or plan artifact was found under `ai/thoughts/` in this checkout; the ticket is currently untracked. Its companion Sidecar PR 7 is in a sibling repository, referenced by the ticket, and was not treated as executable evidence for this framework checkout.

## Documentation alignment and skill-authoring impact

The bundled docs are **aligned** with the current skill-only publication implementation: they describe fixed startup model connections, immutable skill generations, and current retirement semantics. They do not describe the ticket's future execution-configuration publication; that is a prospective documentation change, not current drift. The ticket has skill-authoring impact because model aliases, connection selection, quotas, attachment limits, and trace policy affect author-facing behavior. Relevant routed topics are `model-selection-and-connections.md`, `validation-workflow.md`, `traces-and-debugging.md`, `mental-model.md`, and Java API `skill-reload.md`; complete execution limit configuration is marked foundational in the skill-authoring README. The exact post-change guidance needs source/test verification during implementation.

## Open questions for planning

1. Which minimal allowlisted public value type expresses a complete execution candidate while preserving existing `SkillReloader` calls? This is a design choice left explicitly to planning by the ticket.
2. How will candidate credential references be represented and resolved before provider construction, including non-Gemini key references and header values? Current startup `apiKey` is a direct value and Gemini has a `credentialsUri` loader.
3. Which concrete runtime objects must be generation-owned so delayed children, retries, quotas, attachment materialization and trace policy all use one captured version? Current consumers are separate startup-wired beans.
4. How should unused prepared candidates release provider clients deterministically within the public candidate lifecycle? The current candidate type has no close operation and no provider resources.
5. Which Sidecar PR 7 contract and installed-artifact verification commands are available in the sibling checkout? Its companion ticket exists outside this research scope.
