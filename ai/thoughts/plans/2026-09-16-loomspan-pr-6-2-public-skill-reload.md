# PR 6.2 Public Skill Reload Implementation Plan

## Overview

Expose a two-stage, framework-owned skill update: prepare and freeze a complete candidate, let the application stage generation-keyed artifacts, then publish that exact candidate. Preserve invocation-tree capture while making generation identity visible to REST handlers and current diagnostics. The ticket is the specification; this plan selects the internal coordination and propagation mechanisms it leaves open.

## Current State Analysis

`SkillGenerationManager` already assembles complete detached generations, validates child references, issues UUIDs, and owns the atomic active reference (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:25-111`). Its `activate` is unconditional, preparation is not serialized, and it has no shutdown relationship. `FrameworkExecutionLifecycle` owns root admission and the synchronized close boundary (`src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:22-65,116-133`). `DefaultSkillTemplate` captures a generation before conversion/validation, and execution bindings carry it through nested work (`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:114-164`; `src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:32-45`). The injected catalog is an immutable startup snapshot, but has no ID (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:50-64`). Observability currently pins startup skill discovery (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:127-150`).

The application needs a future ID to stage REST configuration before activation. Only the framework knows the candidate's validated declarations and the invocation tree's captured generation; applications must not infer identity from mutable files or current state.

## Desired End State

One injectable `SkillReloader` provides `prepare()`, `publish(PreparedSkillUpdate)`, and `snapshot()`. A successful preparation yields a unique process-local ID and immutable candidate catalog without activation. Publication is one-shot, ownership/base/shutdown checked, atomic, and does not read files. Old roots, handoffs, catalogs, and REST calls retain the original generation; new roots capture the published one. Active observability discovery follows the active generation per operation, while execution diagnostics expose the captured ID. The application owns external staging, readiness, and retention.

### Key Discoveries

- Complete candidate assembly and fixed Java/handler dependencies already exist in `SkillGenerationManager.java:66-105,113-135`; reuse them rather than creating a second validator.
- Shutdown's synchronized monitor is the existing authoritative cutoff (`FrameworkExecutionLifecycle.java:55-65,116-133`); publication must activate inside a lifecycle-gated critical section rather than reading a shutdown flag before a separate activation.
- `RestSkillInvocation` currently has only name/input, while per-generation REST invokers are built in `SkillGenerationManager.java:86-96,147-151`; supply the generation captured at execution, not an active-state lookup.
- Trace startup occurs during session construction before its binding is installed (`LoomspanSessionRunner.java:263-290`; `LoomspanSession.java:219-264`), so pass the already-captured ID into session/trace/observation construction.
- The checked-out documentation package and Maven project both report `1.0.0-beta.4-SNAPSHOT`; its current no-public-reload statements match present executable behavior and will become documentation drift after implementation.

## What We're NOT Doing

No one-step reload/result/changed flag, comparison/no-op path, watcher, file transaction, manifest/property syntax, hot-swapped Java/beans/models, candidate registry/expiry/discard, historical invocation/catalog, rollback, distributed activation, retirement notification, external artifact manager, safe-deletion signal, or cross-request discovery pagination snapshot.

## Skill-Authoring Documentation Impact

**Impact**: Affected, narrowly.

- **Rationale**: Manifest syntax and authoring validation do not change, but the mental model and REST topic currently say there is no public reload. After this feature an author must understand that complete immutable generations can be prepared and activated publicly, old trees remain coherent, and a fixed REST handler can select generation-keyed application configuration. The application lifecycle details belong primarily in Java API documentation.
- **Documents to update**: `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`, `rest-skills.md`, `traces-and-debugging.md`, and their `README.md` routing/coverage text if its topic claims change; `agent-skills/loomspan-docs/references/java-api/catalog-and-validation.md`, `rest-skills.md`, and a focused reload API topic linked from the Java API index.
- **Supporting evidence**: New public reload and retained-execution integration tests; existing `SkillGenerationExecutionIntegrationTest`, `SkillGenerationManagerTest`, and REST authorization tests; trace and Java/Console fixture tests for diagnostic ID.
- **Coverage table update**: Required where the mental-model, REST, or traces coverage description changes to include public generation activation/identity. Do not imply new YAML syntax.
- **LLM-first usability**: Keep application staging in the Java API topic, authoring execution consistency in the mental model, REST handler-specific implications in the REST topic, and diagnostic interpretation in traces. Link across topics instead of repeating lifecycle prose; distinguish runtime guarantees from application responsibilities and absence of safe-delete signal.
- **Drift classification**: Aligned today; the current “no public reload” and two-component invocation guidance becomes documentation drift only once the production change lands and must be updated in the same implementation phase.

The `loomspan-docs` skill is not installed/exposed in this agent context, so the version-aligned checked-out knowledge base was read directly after the executable inventory per `ai/commands/shared/loomspan-docs-protocol.md`.

## Contract and Compatibility Impact

| Surface | Impact and evidence | Planned treatment |
| --- | --- | --- |
| Application API | Closed allowlist contains `SkillCatalog`, `RestSkillInvocation`, and `SkillException` (`LoomspanPublicSurfaceArchitectureTest.java:27-53`); exact shapes are tested (`ApplicationApiValueTest.java:17-117`). | Add `SkillReloader`, `PreparedSkillUpdate`, `SkillReloadException`, catalog ID, and invocation ID deliberately. Update allowlist, reflection tests, README, and examples atomically. |
| Supported SPI | `RestSkillHandler` remains the sole allowlisted SPI (`LoomspanPublicSurfaceArchitectureTest.java:334-375`). | Keep its single method; invocation value gains trusted runtime metadata. The service/handle are not application implementation or bean-replacement contracts. |
| Configuration and manifest contracts | Fixed locations/models/connections and full YAML/REST validation (`LoomspanAutoConfiguration.java:160-203`; `SkillGenerationManager.java:66-105`). | Preserve syntax, validation, roles, and fixed dependency/cardinality rules; allow complete add/edit/delete/empty sets. |
| Persisted or serialized contracts | Candidate handles and IDs are process-local; no durable application format is intended. | No persistence, migration, or restart contract. |
| Ephemeral diagnostic formats | `TRACE_STARTED`, live snapshots, REST DTOs, Java/Go fixtures, and Console projections currently lack generation ID. | Add actual captured ID coherently to current writer/readers/projections; preserve redaction/security and exact-version marker validation; no historical fallback. |
| Internal or accidentally exposed implementation | Generation manager, lifecycle, catalogs, Spring machinery, diagnostic DTOs and Go projections are implementation. | Update/remove constructors and internal paths atomically; no internal compatibility shims. |

- **Evidence of supported contracts**: Closed architecture allowlist, README public API section and handler example, public integration test, and explicit ticket requirements. Java `public` alone is not support.
- **Intentional compatibility changes**: Direct catalog method and REST record-component additions, with no old two-argument constructor; explicitly approved in ticket Pipeline notes. Current diagnostic schema evolves with its coordinated consumers.
- **In-repository consumers to update**: Java production constructors/callers, API and architecture tests, integration fixtures, README, Java API and skill-authoring guidance, observability DTO/fixture corpus, Go DTO/trace/MCP contracts and tests, TypeScript API contracts and browser fixtures/tests.
- **Public-surface delta**: Three allowlisted top-level types and two ID accessors; `RestSkillInvocation` becomes a three-component record. No supported Spring override point or extra SPI. `PreparedSkillUpdate` can be a public accessor-only interface backed by an internal immutable final implementation; `publish` verifies the exact owner identity, so an application-created implementation is never a publishable handle. Do not expose an internal type or public fabrication constructor in its API signature.
- **Shim decision**: **No shim.** Ticket explicitly authorizes the development-time API break and current diagnostic consumer update; old constructors/fallbacks would create misleading dual behavior.
- **Java-to-Go boundary coordination**: **Required.** Update Java trace and observability writers/DTOs, Java corpus and tests, Go acquisition/validation/trace-analysis/MCP projections, TypeScript contracts/browser tests, and documentation together. Ensure exact release-string rejection tests remain. No historical reader.
- **Pipeline notes alignment**: **Aligned.** No broader compatibility change is planned.
- **Compatibility marker decision**: Keep the existing project-derived `1.0.0-beta.4-SNAPSHOT` marker for this unreleased coordinated change. It is not an independent schema counter; all checked-out Java/Go consumers and fixtures move together. Same-version released artifacts still require exact matching strings; dual `development` remains best-effort only. Do not introduce a second marker or legacy parser. Reassess if the implementation changes an already released resolved version, which this checkout does not show.

## Implementation Approach

Keep one active-generation authority in `SkillGenerationManager` and one shutdown authority in `FrameworkExecutionLifecycle`. Add a lifecycle operation that executes a short action only while admission remains open, under the same monitor as `closeAdmission`. The framework-owned reloader serializes preparation using a preparation lock and publication using a separate publication lock. Preparation briefly checks shutdown and captures the base ID before filesystem I/O, builds via existing manager, then rechecks shutdown before returning a handle; it holds neither lifecycle nor publication lock over reads or application staging. Publication checks owner/one-shot/base and activates under the lifecycle-gated critical section, making activation and shutdown ordering atomic. Snapshot is an atomic active reference read, with no filesystem access or update lock.

Use an owner token held only by the reloader and its internal candidate implementation, not a global candidate map. The handle contains candidate generation and expected-base **ID only**, not the base generation. Under the publication lock, verify exact owner, not-yet-published state, and current base; mark successful publication only when activation succeeds. Distinct candidates prepared from the same base race deterministically: one wins, the other becomes stale. Reject foreign, stale, repeated, and shutdown attempts with `SkillReloadException`; use ordinary null validation for null arguments. Wrap operational preparation/validation failures with useful cause and context, without catching `Error`; leave startup's established error behavior untouched. Rejected candidates do not alter active state.

Preserve existing execution-reference ownership (`PreparedInput`, handoff payload, binding, and root finally cleanup). Ensure new candidate/catalog/observability references do not accidentally pin executable generations: discovery should retain the catalog data, not the runtime generation. No GC-timed correctness claim. Existing `activate` internal call sites/tests should be narrowed or adjusted so all post-startup publication goes through the checked path; startup remains a separate one-time initialization path.

Alternatives rejected: (1) a second update registry or global history duplicates the manager and violates abandonment; (2) CAS against active without lifecycle gating permits activation after shutdown; (3) holding one lock across preparation/staging blocks required interleavings; (4) deriving REST/trace IDs from current active state mislabels old work; (5) public constructors or bean replacement turn framework-owned data into accidental extension points. The only necessary new public concept beyond the ticketed service/error is the caller-held candidate handle that carries a future ID across application staging.

## Phase 1: Public contract and publication boundary

### Changes Required

1. `src/main/java/ai/loomspan/api/{SkillReloader,PreparedSkillUpdate,SkillReloadException,SkillCatalog}.java`: define exact documented methods, ID semantics, ownership limits, unchecked failure contract, and catalog ID. Keep handle implementation internal and reject fabricated implementations.
2. `src/main/java/ai/loomspan/internal/skill/{SkillGenerationManager,SkillGeneration,...}.java` and `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java`: propagate generation ID to immutable catalog, keep full assembly and UUID issuance, and add a checked publication path using a candidate handle/owner token and base ID. Do not retain base generation, compare content, or reread on publish.
3. `src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java`: add a small synchronous admission-open gate executed under the existing shutdown monitor; activation occurs inside it. No long I/O or application staging inside the gate.
4. `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`: wire one framework-owned reloader bean to the manager/lifecycle without `@ConditionalOnMissingBean` or an override contract; retain injected startup catalog as its original snapshot.

### Success Criteria — Automated Verification

- [x] Public signature/allowlist tests show exactly the ticketed types and no internal leakage or extra SPI.
- [x] Focused reload tests prove fresh IDs, frozen candidate catalogs, full validation, add/edit/delete/empty behavior, owner/base/one-shot checks, and no read on snapshot/publish.
- [x] Latch-based concurrency tests prove prepare serialization, publish serialization, prepare/publish overlap and staleness, and no activation past shutdown boundary.
- [x] `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest,SkillGenerationManagerTest test` passes on Windows.

## Phase 2: Captured REST and execution diagnostics

### Changes Required

1. `src/main/java/ai/loomspan/api/RestSkillInvocation.java` and the REST invocation construction in `SkillGenerationManager.java`: add validated `generationId` as a record component; derive it from the captured generation execution path (`ExecutionBinding` or an immutable per-generation closure), never model/business input/current active lookup. Preserve input freezing and auth behavior.
2. `src/main/java/ai/loomspan/internal/core/{LoomspanSessionRunner,LoomspanSession,InternalExecutionTraceHandleFactory}.java`, trace/observation factories, `DefaultExecutionTraceHandle`, `LiveActivityProjector`, active/finalized catalog models and DTO mapping: pass the captured ID into session construction so the first `TRACE_STARTED` record, live snapshot, finalized trace summary, and REST diagnostics agree even if publish occurs before completion. Do not retain executable generation in diagnostics just to report an ID.
3. `src/main/java/ai/loomspan/internal/observability/{ObservabilityRuntime,web/ObservabilityRouteRegistrar,web/ObservabilityRestController}.java`: replace the pinned startup registered-skill catalog with a supplier/manager read. Each status/list/detail operation captures one active catalog locally and uses it throughout that operation. Preserve exact-name cursor semantics without promising cross-request snapshot pagination.
4. `loomspan-console/internal/...`, `loomspan-console/web/src/...`, and Java/Go fixture corpus: carry the new current-run field through DTO validation, trace analysis, MCP/browser projections and tests; keep version rejection and redaction checks.

### Success Criteria — Automated Verification

- [x] Old/new identical-YAML generations route to different keyed handler configuration, including delayed handoff, nested, and parallel calls; model input cannot override the ID.
- [x] Active observability discovery follows publication and one request is internally coherent; old execution diagnostics keep their captured ID after publication.
- [x] Java/Go trace and REST fixture consumers agree on the field and exact compatibility marker; focused Java, Go, and web tests pass.

## Phase 3: Documentation and whole-repository verification

### Changes Required

1. `README.md` and Java API knowledge base: update supported type count and provide a complete startup readiness and prepare → stage → publish example using one fixed REST handler and generation-keyed configuration. Explain fresh IDs for same YAML, file stability during prepare, empty-set deletion, stale/repeated/foreign/shutdown failures, snapshots, and application-owned retention (publication is not safe deletion).
2. Skill-authoring knowledge base topics named above: replace outdated no-public-reload wording, retain exact local visibility and authorization guidance, and document captured diagnostic ID with source/test anchors. Update README routing/coverage precisely.
3. Remove superseded references to proposed one-step reload/result/changed semantics if any are present in current active docs/examples; do not invent a migration shim.

### Success Criteria — Automated Verification

- [x] Search confirms no active example/guidance recommends obsolete one-step reload, current-state fallback, or publication-triggered cleanup.
- [x] Architecture, targeted reload/generation/REST/observability/trace integration and fixture tests, full Maven suite, Go suite, and web checks pass; build commands are detailed in the testing plan.
- [x] Changed author-facing claims are backed by focused executable tests/source and satisfy the knowledge base's LLM-first routing/precision standard.

## Testing Strategy

Start with a public integration test that cannot compile/pass until `SkillReloader` exists, then separately test internal concurrency using deterministic latches rather than timing. Cover the complete candidate matrix, retained execution and REST identity, lifecycle cutoff, dynamic observability, diagnostic writer/reader/Console coherence, and public architecture. See the companion testing plan for named tests, fixtures, commands, and exit criteria.

## Performance Considerations

Filesystem reads and complete validation are intentionally synchronous during `prepare` but outside publication/shutdown locks. `snapshot` and normal execution remain nonblocking atomic reads. Publication holds only the short lifecycle gate/activation operation. Catalogs must not retain execution runtime state solely for discovery; process-local retained handles/catalogs can legitimately keep data alive. Do not use GC as a cleanup trigger.

## Migration Notes

Pre-1.0 in-repository callers of `RestSkillInvocation` update to the three-component constructor, and public catalog consumers can read the new ID. No compatibility constructor or persisted-data migration. Applications must stage initial generation configuration before admitting their own traffic, retain configuration while any old work may use it, and establish their own safe-deletion policy; Loomspan supplies no retirement signal.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-6.2-public-skill-reload.md`
- Research: `ai/thoughts/research/2026-09-16-loomspan-pr-6-2-public-skill-reload.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Existing patterns: `SkillGenerationManager.java:48-111`, `FrameworkExecutionLifecycle.java:55-65,116-133`, `DefaultSkillTemplate.java:114-164`, `ExecutionBinding.java:32-45`.
