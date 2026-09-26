# PR 11 Execution Configuration Publication Implementation Plan

## Overview

Extend the existing `SkillReloader` generation transaction so a host can validate, prepare, and publish a complete skill set and its execution settings together. A root captures one generation at the existing handoff boundary; every descendant and physical provider attempt uses that generation's models, limits, tracing policy, and resources.

## Current State Analysis

`SkillReloader` accepts only documents (`src/main/java/ai/loomspan/api/SkillReloader.java:8`); `PreparedSkillUpdate` has no disposal operation. `SkillGenerationManager` checks manifests using a catalog factory fixed to startup models and activates a `SkillGeneration` under the same monitor used for root capture (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:89`, `:241`). The generation has skills only (`SkillGeneration.java:16`). Root preparation captures its lease before input conversion, and `FrameworkExecutionLifecycle` retains that lease through registered physical work (`DefaultSkillTemplate.java:113`; research document, Root admission and execution lifetime). Provider clients, model resolver, quotas, attachment size, mission timeout, depth, and trace persistence are presently startup-wired singleton settings (`LoomspanAiAutoConfiguration.java:28`; `LoomspanAutoConfiguration.java:143`, `:250`, `:299`, `:342`). The trace key is currently top-level `execution-trace` (`ExecutionTraceProperties.java:8`).

## Desired End State

`SkillReloader.validate(documents, configuration)` and `prepare(documents, configuration)` accept a complete authored configuration containing only `loomspan.connections`, `loomspan.models`, `loomspan.session`, and `loomspan.execution-trace.persistence`. A new allowlisted `ai.loomspan.api.ExecutionConfiguration` is a defensively copied authored YAML value; it carries no resolved secret or mutable autoconfigure object. Strict parsing rejects other sections, duplicate/unknown fields, direct credential values, and unsupported process settings with safe field paths. Supplied documents are always the complete proposed YAML/REST set. Existing overloads remain and freeze a copy of the currently active execution settings, so a skill-only update cannot regress settings to startup values. `prepare()` still rereads configured skill resources. Every successful preparation owns a complete effective config and provider clients. `publish` swaps a single generation; a closed or stale candidate cannot activate. Abandoned candidates close explicitly. Published resources retire after the last generation owner and physical worker return. Startup `application.yml` remains valid, using the canonical nested trace key and the same defaults.

### Key discoveries

- Candidate ownership, stale checks, and one-shot publication already live in `DefaultSkillReloader.java:33-106`; extend this coordinator.
- Generation capture and retirement share one monitor (`SkillGenerationManager.java:241-339`), avoiding a second version authority.
- `YamlSkillCatalog` takes model information from `LoomspanProperties` (`YamlSkillCatalog.java:65-108`); create it from the candidate model/connection snapshot for both validation and preparation.
- `NamedAiConnectionRegistry` already constructs and rolls back clients on failure (`NamedAiConnectionRegistry.java:16-101`); adapt this behavior to generation ownership.
- Sidecar PR 7 explicitly waits for a supported framework candidate and disposal contract (`../loomspan-sidecar/ai/thoughts/plans/loomspan-pr-7-publish-framework-configuration.md`). Its integration is a separate repository task after installation of this artifact.

## What We're NOT Doing

No new SPI, bean-replacement API, secret store, credential editor, live rotation, process setting publication, REST handler transport redesign, Go Console protocol change, or Sidecar implementation in this framework PR. Provider connectivity checks and billable requests are not preparation checks.

## Skill-Authoring Documentation Impact

**Impact:** Affected.

- **Rationale:** Model availability, connection and retry selection, session limits, and trace persistence now vary by captured generation. Existing model guidance incorrectly says connections remain application/Spring dependencies rather than per-generation copies (`agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`). This is prospective documentation drift when implementation lands; currently it is aligned with source.
- **Documents to update:** `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`, `validation-workflow.md`, `mental-model.md`, `traces-and-debugging.md`, `README.md`, and `agent-skills/loomspan-docs/references/java-api/skill-reload.md`; update the top-level `README.md` too.
- **Supporting evidence:** New public publication, overlap, credential, quota and trace tests below; existing `PublicSkillReloadIntegrationTest`, `SkillGenerationExecutionIntegrationTest`, `ModelAttemptCallAdvisorIntegrationTest`, and `LoomspanPropertiesTest`.
- **Coverage table update:** Required. Model publication and complete execution limits gain verified guidance; update the current foundational limits row only to the extent focused tests support it.
- **LLM-first usability:** Keep model syntax and publication workflow in their routed topics, link to the Java API lifecycle, distinguish startup YAML from authored publication, and state what validation does not check. Avoid duplicating limit lists across topics.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | `SkillReloader`, `PreparedSkillUpdate` are allowlisted (`LoomspanPublicSurfaceArchitectureTest.java:20-47`); Sidecar must use them. | Preserve all current overloads; add complete-candidate overloads and one allowlisted `ExecutionConfiguration`; add a default `close()` to `PreparedSkillUpdate` and implement real candidate disposal. No `internal`/`autoconfigure` signatures. |
| Supported SPI | `RestSkillHandler` is sole SPI (AGENTS.md). Generation ID in `RestSkillInvocation` remains. | Preserve signatures and behavior; add no SPI or bean override contract. |
| Configuration and manifest | `loomspan.*` is strict; trace uses top-level key; model aliases are manifest references. | Move trace to `loomspan.execution-trace.persistence` as expressly authorized, with no legacy alias. Preserve meanings/default `ONERROR`; validate documents against candidate aliases. |
| Persisted or serialized | Framework has no durable publication schema; Sidecar owns its snapshots (research, Contract classification). | No framework migration. Public authored YAML remains reference-only and may be stored by host; no resolved secret serialization. |
| Ephemeral diagnostics | Generation ID is emitted in trace start and handed to REST handler (research, Root admission). | Preserve current-run correlation; prevent secret values in validation/issues/catalogs/logs. No historical format guarantee is added. |
| Internal or accidentally exposed | Properties, registries, providers, and Spring beans are integration machinery per AGENTS.md. | Replace fixed singleton settings paths coherently; do not keep internal compatibility layers solely for exposure. |

- **Evidence of supported contracts:** Closed architecture allowlist, README API section, public integration tests, and Sidecar's supported API boundary.
- **Intentional compatibility changes:** The trace YAML key moves without alias, authorized by Pipeline notes. Existing consumers must change their YAML; incidents demo is a known external update when it adopts the artifact. No other public Java or manifest break is authorized.
- **In-repository consumers:** README examples, tests/fixtures using `execution-trace`, Spring binding tests, Java API and skill-authoring docs, sample application configuration if found by repository-wide search.
- **Public-surface delta:** Add `ExecutionConfiguration` in `ai.loomspan.api`; overload `SkillReloader.validate/prepare` with `(Collection<SkillDocument>, ExecutionConfiguration)`; add `PreparedSkillUpdate.close()` as a default `AutoCloseable` method. Keep catalog/generation accessors and existing signatures. No new extension point.
- **Shim decision:** **Shim.** Existing skill-only overloads and the default `close()` preserve source/binary behavior of allowlisted interfaces. The default method is permanent while these interfaces are supported; internal candidates override it for resource cleanup. No trace-key alias.
- **Java-to-Go boundary coordination:** **Not required.** The REST/SSE/NDJSON application adapter protocol is unchanged.
- **Pipeline notes alignment:** **Aligned.** Only the specifically authorized trace key moves; internal paths may change while public API stays compatible.

## Implementation Approach

Use one strict authored YAML document in `ExecutionConfiguration` to avoid exposing many Spring binding classes or a second DTO hierarchy. The document contains only the publishable `loomspan` subtree; direct `api-key`, `gemini.credentials-uri`, and literal `headers` are rejected for publication. Use explicit `api-key-ref`, `gemini.credentials-ref`, and `header-refs` fields, with references resolved from the deployment's Spring `Environment` during preparation. A static header such as an Anthropic beta setting can be provided as an external property reference. Missing/blank references fail safely. The authored value and validation feedback contain reference identifiers only; resolved values exist solely in the prepared internal connection snapshot and provider clients. `Environment` is an infrastructure dependency, not a public SPI.

Normalize candidate and startup settings into one internal immutable effective execution value, applying the same defaults and cross-field constraints. Startup binding can accept direct secrets from `application.yml` as before; explicit publication requires references. Parse the candidate strictly before any provider construction. Keep provider-client construction as a second preparation phase after skill/config validation, with rollback on partial failure. Validation itself must never create clients or send requests. `PreparedSkillUpdate.close()` releases unpublished clients; publish transfers ownership into the generation. An invalid/stale publish closes an unpublished candidate while preserving active state. A repeated publish of an already published candidate remains a harmless rejection and must not close active resources.

Bind one effective execution runtime to each `SkillGeneration`: models/connection clients and retry policies; depth, timeout, all quotas, attachment max size, and trace persistence. Thread this captured runtime through `LoomspanSession`, execution binding, mission engines, materializer, usage service, and model resolver rather than reading current global properties. Reuse stateless singleton services and existing lease/physical-work lifecycle. The startup generation is built by the same path and owns startup clients; avoid duplicate Spring ownership and double close.

The alternatives are fixed startup singletons (fails overlapping versions) and a separate mutable settings registry (creates a mixed-version window). A larger public typed tree would replicate Spring configuration with many new permanent API types. The one authored value plus strict parser is the smallest public contract that Sidecar can persist and supply.

## Phase 1: Public candidate and normalization

**Files:** `src/main/java/ai/loomspan/api/ExecutionConfiguration.java`, `SkillReloader.java`, `PreparedSkillUpdate.java`; new internal parser/effective configuration classes; `LoomspanProperties.java`, `ExecutionTraceProperties.java`, `LoomspanAutoConfiguration.java`; architecture allowlist.

Add the complete-candidate overloads, documented field schema and `close()` lifecycle. Deeply freeze authored input at construction; never expose a mutable or resolved object. Parse only allowed fields with strict duplicate/unknown rejection, a bounded input size, canonical error paths and redaction. Reuse existing validation for connections/models/retries/session constraints, applying defaults identically for startup and publication. Move trace binding under `loomspan` and remove separate top-level binding. Ensure unsupported process fields fail before any active mutation.

**Automated success:** `mvn -Dtest=LoomspanPublicSurfaceArchitectureTest,LoomspanPropertiesTest,LoomspanSessionPropertiesTest test` passes; new parser tests prove strict subsets, defaults, direct-secret rejection and safe messages.

## Phase 2: Complete generation preparation and resource lifetime

**Files:** `SkillGeneration.java`, `SkillGenerationManager.java`, `DefaultSkillReloader.java`, `YamlSkillCatalog.java`, `NamedAiConnectionRegistry.java`, `SpringAiProviderIntegration.java`, Spring autoconfiguration.

Make manager validation/prepare accept the candidate's effective models; construct provider clients only after complete validation. Retain the existing base-ID, owner and one-shot checks. Give each candidate a one-way state transition: prepared → published (ownership transferred), or prepared → closed (clients destroyed); stale rejection closes the latter. Make generation retirement close its clients after owner count reaches zero, independently of optional host retirement notifications; failure to close one client must not skip others. Close unactivated construction products on any failure. Ensure startup and shutdown close each client at most once within the existing lifecycle budget and that a candidate prepared after admission closes is discarded.

**Automated success:** focused manager/reloader/registry tests pass; injected construction and close faults leave the active generation intact and all owned clients accounted for.

## Phase 3: Captured execution settings end to end

**Files:** `DefaultSkillTemplate.java`, `LoomspanSessionRunner.java`, `LoomspanSession.java`, `ExecutionBinding.java`, model resolver/interaction factory, usage service, mission work and step engines, input materializer, trace construction and corresponding autoconfiguration.

Use the captured generation for every per-root setting and resource. Avoid a thread-local global selector: pass the generation/runtime via the already explicit execution binding and session to nested, parallel and delayed work. Count provider attempts with the captured retry and quota policies. Read trace persistence on session creation from the captured runtime; keep process-level observability retention separate. Preserve generation ID in trace and public handoff. Audit any singleton that still closes over startup execution properties and replace it with generation-scoped access.

**Automated success:** new overlap integration tests exercise delayed handoff, child work, retry, quota, attachment, timeout, depth, trace policy and client retirement; existing shutdown and execution suites pass.

## Phase 4: Documentation, migration and cross-repository handoff

**Files:** top-level README, routed `agent-skills/loomspan-docs` topics and coverage table, all in-repository `execution-trace` examples/tests.

Document complete candidate schema, reference provisioning, `close()` responsibility, root selection boundary, default behavior, process setting exclusions, credential revocation limitation, restart/startup YAML, generation correlation and deliberate trace-key migration. State that Sidecar PR 7 must be updated and verified against an installed `mvn install` artifact using only `ai.loomspan.api`; the incidents demo YAML needs the nested key when updated. Do not implement Sidecar here.

**Automated success:** repository search finds no old top-level key in examples/tests; architecture and focused documentation-backed tests pass; `mvn test` and `mvn install -DskipTests` succeed so Sidecar can consume the local artifact.

## Testing Strategy

See `2026-09-25-pr-11-execution-publication-testing.md` for test cases and exit criteria. Begin with a public integration test proving a skill can reference an alias introduced in the same unpublished candidate, which fails against current fixed startup model lookup. Add lifecycle/concurrency cases before changing the runtime wiring. Run focused tests during each phase and the full Maven test suite after the final source change.

## Performance Considerations

Provider clients are built per prepared candidate; validation remains inexpensive and free of provider traffic. A host should close unused candidates promptly. Old generations may retain clients as long as physical work runs, which is required for correctness. Avoid acquiring generation locks while constructing or closing provider clients; lock only atomic activation, capture and owner accounting.

## Migration Notes

Change application YAML from `execution-trace.persistence` to `loomspan.execution-trace.persistence`. No fallback key is planned. Embedded startup remains a complete source of initial defaults and provider config. Explicit host publications must include the full execution settings value and externally resolvable credential references. Sidecar's durable selection and development-data migration are owned by PR 7 after this framework artifact is installed.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-11-publish-execution-configuration.md`
- Research: `ai/thoughts/research/2026-09-25-pr-11-execution-publication.md`
- Companion Sidecar ticket: `../loomspan-sidecar/ai/thoughts/tickets/loomspan-pr-7-publish-framework-configuration.md`
