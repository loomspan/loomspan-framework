---
date: 2026-11-20T15:01:10-08:00
researcher: Codex GPT-6.1
git_commit: 8f73de81b38f30555814c91537a96d2af1fa588f
branch: main
repository: loomspan-framework
topic: "PR 33 namespace publication version and current Console discovery"
tags: [research, codebase, publication, namespaces, java-api, console, observability]
status: complete
last_updated: 2026-11-20
last_updated_by: Codex GPT-6.1
---

# Research for PR 33 namespace publication version

## Research question and execution context

Map the existing namespace preparation, publication, metadata, authenticated observation, Console reader and browser behavior relevant to `ai/thoughts/tickets/loomspan-pr-33-namespace-publication-version.md`.

This is Step 1 in pipeline mode of `0_run_pipeline.md`, using the Full 5-Step Pipeline. The developer confirmed the Step 0 recommendation with “proceed”. Step 0 observed clean tracked files and only the untracked ticket at the commit recorded above. This research changes only this artifact. The ticket permits coordinated current wire-shape changes, explicitly keeps `consoleCompatibilityVersion` unchanged, and excludes compatibility shims, migration paths and legacy readers. Existing Java preparation calls and their behavior remain explicit requirements.

Research checklist completed

- Read the ticket, root and Console policies, automation protocol, design lens and documentation evidence protocol.
- Trace Java preparation, immutable generation metadata, active selection, publication, removal and candidate disposal.
- Trace Java observation wiring and authentication through the Go reader and browser API.
- Inspect browser target scoping, Overview rendering and refresh/reconnect paths.
- Locate focused tests, shared fixtures, documentation and verification entry points.
- Compare version-aligned documentation with executable evidence.

## Summary

`DefaultSkillReloader` captures the target generation or absence before preparing a detached `SkillGeneration`. The candidate carries that generation and the captured base. `SkillGenerationManager.publish` checks the base and atomically replaces the active generation under `generationMonitor`. Candidate and active catalog snapshots already carry namespace and process-local generation ID, including empty catalogs. There is currently no `publicationVersion` field or preparation argument.

The observation adapter receives a supplier of the manager's flattened `RegisteredSkillCatalog`. This projection preserves registered skill metadata across active namespaces, including Private namespaces, but empty namespaces supply zero entries. The existing authenticated `GET /_loomspan/observability/v1/instance` returns current instance status and counts. Console's Go `observability.Service.GetInstance` reads and validates that response, and its scoped browser API supplies it to the Instance Overview. Overview has a manual refresh control, and the globally mounted activity provider also requests authoritative instance refresh on stream connection, reconnect recovery and periodic baseline updates.

The bundled publication and HTTP guidance agrees with the researched baseline. Planning must select the small Java preparation addition and the concrete current observation shape, including which existing programmatic Console surface will carry namespace discovery.

## Java publication and immutable metadata

### Application-facing types and calls

- `src/main/java/ai/loomspan/api/SkillReloader.java:8` declares the framework-owned update service. Its four `prepare` signatures accept namespace, complete documents and `ExecutionConfiguration`, optionally visibility, a credential map, or both. The existing order is visibility followed by credential values. `publish(PreparedSkillUpdate)`, `snapshot(namespace)`, `remove(namespace, expectedGenerationId)` and retirement registration are separate calls.
- `src/main/java/ai/loomspan/api/SkillCatalog.java:12` exposes `namespace()`, `generationId()`, `skills()` and qualified lookup. It returns immutable discovery metadata and includes namespace/ID even when skills are empty.
- `src/main/java/ai/loomspan/api/PreparedSkillUpdate.java:7` exposes the candidate ID and candidate snapshot. Closing an unpublished candidate releases its resources. The catalog carries no executable lease.
- `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java:16` is the single production implementation found for `SkillCatalog`. It eagerly builds sorted descriptors and checks that all capabilities belong to the owning namespace. In-repository direct constructors occur in its unit tests and `TestSkillGenerations`.
- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java:24` is the single production implementation found for `SkillReloader`.

### Preparation and publication flow

`DefaultSkillReloader.prepare` overloads at lines 54–76 converge on `prepareInternal`. Omitted visibility supplies `PRIVATE`. Omitted credential values select Environment resolution. An explicit map is required non-null and copied before use, and its resolution has no Environment fallback. `prepareInternal` normalizes namespace, copies documents, captures a preparation view while admission is open, builds through the generation manager, and checks admission again after construction. Construction/check failures dispose the candidate runtime.

`SkillGenerationManager.capturePreparation` at line 143 captures the target's active ID or null plus `Map.copyOf(active)` under `generationMonitor`. `prepare` at line 152 parses configuration for validation, checks complete supplied declarations against that view, resolves credentials and builds an independent candidate. `prepareCatalog` at line 215 increments an `AtomicLong` and prefixes it with the manager's random UUID. Each ID is process-local and distinct across manager lifetimes. Runtime construction and immutable catalog construction happen before publication.

`SkillGeneration` at `src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:18` contains final namespace, ID, visibility, capability/definition maps, immutable catalog projections, runtime and executor metadata. Its normal catalog creation is at line 98. A provided catalog is required to agree with the generation namespace and ID. Its constructors are internal collaboration/test construction surfaces.

`DefaultSkillReloader.Candidate` retains framework owner identity, captured base ID and the candidate generation. Its `snapshot()` returns that generation's catalog immediately. `DefaultSkillReloader.snapshot` at line 145 selects `generations.active(namespace).skillCatalog()`. Prepared state has no effect on this active lookup.

`DefaultSkillReloader.publish` at line 100 checks candidate ownership, serializes against candidate close/publication and calls publication while lifecycle admission is open. `SkillGenerationManager.publish` at line 283 compares only the captured target base/absence to the current target. It invokes `activateAndSelect` at line 268 under the same monitor. Active replacement and published-generation ownership are installed together. Retirement dispatch occurs outside publication/lifecycle locks. Failed stale or shutdown publication disposes the candidate. Already-published candidate closing has no effect on its installed generation.

`SkillGenerationManager.remove` at line 294 checks the expected active ID, removes that namespace and retires its old generation when owners drain. Startup at line 85 is one-shot and creates Private default and fixed-Java namespaces. Restart constructs new manager UUIDs and startup state. Runtime publications have no restoration store.

## Observation boundary and Console consumption

### Java current state and access

`SkillGenerationManager.registeredSkillCatalog` at line 130 captures the active generations' registered catalogs under `generationMonitor`, then combines them. `DefaultRegisteredSkillCatalog(Collection)` at `src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:58` copies each catalog's entries into one sorted map. Every empty catalog contributes no rows. The projection has no independent namespace list.

`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:127` creates the observation runtime. It wires `generations::registeredSkillCatalog`, generates an instance UUID and supplies current-generation quota/persistence functions. `src/main/java/ai/loomspan/internal/observability/ObservabilityRuntime.java:31` stores the catalog supplier, and `skills()` at line 124 invokes it on each read. Observability activation probes that supplier before enabling routes.

`ObservabilityRestController.instance` at `src/main/java/ai/loomspan/internal/observability/web/ObservabilityRestController.java:54` requires `INSTANCE_READ`, rejects query arguments, captures a current skill catalog and serializes `ObservabilityDtos.InstanceStatus`. `skills()` and `skill()` use the same current catalog path for their respective reads. `src/main/java/ai/loomspan/internal/observability/web/dto/ObservabilityDtos.java:22` defines the current status fields. It carries instance identity, compatibility version, observation time, live availability, counts and retention/persistence metadata.

`src/main/java/ai/loomspan/internal/observability/web/ObservabilityAccessService.java:7` names the observation operations. `require` accepts authenticated `ObservabilityOperatorAuthentication`. The existing API-key filter/observation configuration owns that boundary. Namespace callable visibility and skill execution roles are separate from observation access. Authenticated observation headers and no-store behavior are documented and tested by the existing HTTP integration suite.

### Go readers and browser API

- `loomspan-console/internal/applicationclient/client.go:152` probes the application instance with configured credentials. `decodeInstance` at line 200 requires exact release compatibility, a header/body matching UUID, an observation timestamp and explicit live availability. Its private probe DTO reads a subset of instance response fields. This probe establishes target connection/identity and does not return skill counts or namespace metadata.
- `loomspan-console/internal/observability/dto.go:9` defines the complete Console instance status DTO. `GetInstance` at `loomspan-console/internal/observability/service.go:33` reads the instance endpoint through a captured target scope, decodes status, validates identity/metadata, adds targetScopeId, and requires that the scope is still current before returning.
- `validateInstanceStatus` at `loomspan-console/internal/observability/service.go:247` checks instance/header association, compatibility/time, nonnegative counts and retention metadata. There is currently no namespace metadata validation.
- `loomspan-console/internal/browserapi/observability.go:18` captures the selected target scope, calls `GetInstance` and writes scoped JSON. The router registers `/api/console/v1/observability/instance` through `withSession` at `loomspan-console/internal/browserapi/router.go:160`. The browser calls it through `getObservabilityInstance` at `loomspan-console/web/src/api/client.ts:166`.
- The existing MCP `LOOMSPAN_get_runtime` at `loomspan-console/internal/mcpadapter/runtime.go:14` returns target selection, connection, authentication, compatibility, runtime identity, live-monitoring status and observation time. It consumes a `consolecore.StatusSnapshot` provider. Current MCP `RuntimeOutput` has no instance metadata or namespace inventory. MCP tool outputs use explicit schemas in `output_schemas.go` and explicit DTOs in `contracts.go`.

### Browser presentation and freshness

`loomspan-console/web/src/api/contracts.ts:110` defines the TypeScript instance status. `ObservabilityProvider.loadInstance` at `loomspan-console/web/src/observability/ObservabilityProvider.tsx:76` fetches it and verifies the target scope. Request counters and scope-generation checks discard old responses. Scope changes reset reducer state.

`loomspan-console/web/src/observability/Overview.tsx:10` renders Instance Overview. The selected target must have established authentication/identity and compatible Java/Go status to expose refresh. Current content includes skill/execution/trace counts, live availability and instance configuration. The Refresh button calls `loadInstance`.

`loomspan-console/web/src/activity/ActivityProvider.tsx:94` coalesces authoritative refresh requests. Its connection handler at line 133 requests instance, active-execution and trace snapshots when connected. Replay-gap recovery and periodic baseline callbacks request the same refresh set. This is mounted globally and supports opening the UI and reconnecting without waiting for an execution event. Target-context E2E tests exercise selection/replacement, identity changes and stale-scope handling.

## Contract classification and technical exposure inventory

| Surface | Design-lens category and evidence | Current exposure/consumers |
| --- | --- | --- |
| `SkillReloader`, `SkillCatalog`, `PreparedSkillUpdate`, existing preparation arguments | Application API. Explicit exact allowlist, root README and Java API guidance. | Host applications, supported-surface integration tests and namespace tests. Existing signatures and behavior are protected by this ticket. |
| `SkillGeneration`, manager, default implementations and observation runtime | Internal or accidentally exposed implementation. Root AGENTS.md classifies all internal packages, including public constructors. | Cross-package runtime wiring and direct test helpers. Their visibility creates no supported Java SPI. |
| `LoomspanAutoConfiguration.skillGenerationManager` and `.skillReloader` | Internal or accidentally exposed implementation. Spring machinery is explicitly outside the extension API. | Framework bean construction at lines 155 and 224. No supported bean-replacement surface is established. |
| Complete configuration, credential references and visibility defaults | Configuration and manifest contracts. Public preparation API, parser tests and publication guidance describe them. | Environment resolution, explicit map callers, startup and managed hosts. Publication version is absent from current YAML/configuration parsing. |
| Java observation REST DTOs and Go DTOs | Ephemeral diagnostic formats, with a deliberately consumed same-release external protocol. | Go application client, observability service, browser adapter and Java/Go shared fixtures. The ticket authorizes current producer/reader/schema/fixture coordination with the release marker unchanged. |
| Console browser status and React contracts | Internal or accidentally exposed implementation, consumed within the same Console release. | Scoped browser session API and Overview/provider/reducer tests. |
| MCP runtime output DTO/schema | Ephemeral diagnostic format and deliberate current Console client contract. | MCP clients and explicit runtime schema/contract tests. Current output is connection status only. |
| Canonical execution traces and captured namespace generation maps | Ephemeral diagnostic formats for current executions and exact-version portable artifacts. | Java trace writer, Java/Go trace fixture corpus, Console readers. They describe captured invocations rather than current publication state. |
| Publication persistence/history | No current persisted or serialized publication contract found. | Manager state is in memory. Restart builds startup generations and requires host reinstallation. |
| Supported SPI | No supported SPI is exposed. | Closed API policy and architecture test are explicit. |

`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29` owns the executable API allowlist. It separately lists integration machinery and technically public internal types, and checks public API signatures for forbidden internal/autoconfigure exposure. Production-type changes require running it.

## Tests, fixtures and verification entry points

- `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java:27` covers target-only replacement, omitted visibility/configuration reset and empty catalogs. Tests starting at lines 49, 109, 134 and 147 cover captured bases/absence, concurrent detached preparation, competing publications, and foreign/closed/published/shutdown rejection. Later tests cover credential resolution, resource disposal and retirement.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java` covers generation ownership and retained-work retirement. `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillCatalogTest.java` covers immutable metadata, normalized namespace and empty catalogs.
- `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` exercises consumer composition through supported public types. `NamespaceStartupIntegrationTest` covers startup/restart and managed namespaces. `NamespaceObservationIntegrationTest` covers captured generation identity through removal/recreation, plus unfiltered host inspection after default withdrawal. These are current executable anchors, not evidence that invocation is needed for discovery.
- `src/test/java/ai/loomspan/internal/observability/web/ObservabilityRestIntegrationTest.java:73` checks the authenticated instance response and count. Other focused HTTP/security/context-path/actuator/activation tests cover route/access behavior.
- `src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java:137` constructs current REST fixtures using production DTOs. Its instance fixture begins at line 153. `-Dloomspan.console.fixtures.regenerate=true` regenerates committed fixture content, otherwise the test checks agreement. Shared instance JSON is `loomspan-console-fixtures/application-rest/instance-status.json`.
- `loomspan-console/internal/observability/dto_test.go:11` decodes the shared instance fixture. `service_test.go:89` checks reading it through the actual service. Browser API and Console integration tests exercise scoped reads, selected targets and auth. `applicationclient/client_test.go` protects probe compatibility and response identity.
- `loomspan-console/web/src/observability/Overview.test.tsx` uses typed status fixtures for presentation and refresh. `ObservabilityProvider.test.tsx`, activity provider tests and `web/e2e/target-context.spec.ts` cover freshness and scope handling.
- Root Java uses `mvnw.cmd`. Console AGENTS.md names `go test ./...`, `go run ./internal/buildtool verify`, and optional race verification with MSYS2 gcc/cgo. `internal/buildtool/pipeline.go:34` shows verify includes toolchain checks, locked dependency install, skill/eval validation, frontend typecheck/coverage, frontend build/asset manifest and Go tests.
- `loomspan-console/web/package.json` provides `typecheck`, `test`, `test:coverage`, `test:e2e` and `build:web`. Playwright config uses Chromium and retains traces on failure. Orchestrator environment discovery found Java/javac, Go 1.26.1, Node 24.22.1, npm 12.0.2 and existing node_modules. Its browser cache discovery found `C:/Users/mgiacomi/AppData/Local/ms-playwright` with Chromium/headless-shell 1234. An explicit `PLAYWRIGHT_BROWSERS_PATH` may be needed. These are availability observations, not executed verification results.

No tests ran in research. The metadata script was attempted with default bash and explicit Git Bash. The sandbox rejected WSL access and Git Bash object initialization, respectively. Equivalent native PowerShell/Git reads supplied the exact time, commit and branch above. No humanlayer thoughts identity was available from that script, so researcher is the executing model.

## Documentation evidence and drift assessment

The bundled `agent-skills/loomspan-docs/SKILL.md` router was read after production/test inventory, followed by the Java API index, `references/java-api/skill-reload.md`, skill-authoring routing index and `references/skill-authoring/observability-http.md`. The skill and Maven both identify `1.0.0-beta.10-SNAPSHOT`, and the files come from this checkout.

Drift classification is **aligned** for the researched baseline. The reload guide describes detached complete candidates, Environment or explicit-map credentials, Private defaults, captured target base/absence, one-shot publication, empty catalogs, process-local IDs and host reinstallation after restart. HTTP guidance describes current all-namespace skill metadata and independent observation authorization. It currently says no namespace enumeration route is supplied, matching the production route and DTO inventory. No existing publication-version behavior is documented or implemented.

Author-facing impact is Java host integration and current observation/debugging guidance. The label concerns complete skill documents and runtime configuration, while existing manifest syntax, executor data and execution/admission semantics remain their current contracts. Documentation touchpoints identified by the ticket are root `README.md` publication section around lines 190–256, `agent-skills/loomspan-docs/references/java-api/skill-reload.md`, the catalog guidance, `agent-skills/loomspan-docs/references/skill-authoring/observability-http.md`, and `loomspan-console/README.md` observation/refresh guidance. The knowledge-base coverage tables summarize these topics and their source anchors.

## Architecture documentation and historical context

The active namespace map is the current-publication authority. Immutable candidates and their catalogs are usable metadata before installation. Publication synchronizes target base checking and active replacement, while retained invocation ownership and retirement are handled separately. Observation reads active catalog suppliers on demand. Console captures target identity for each operation and discards stale responses in Go and browser layers.

`ai/thoughts/framework-feature-design-lens.md` supplies the classifications used above and distinguishes supported application API from internal public declarations. The current AGENTS.md closed surface and narrow journal exception are explicit repository policy.

`ai/thoughts/future/skill-namespaces-followup.md` records deferred namespace resource measurement. It describes independent generation runtimes and retained candidates/work, and explicitly defers benchmarking. It creates no measurement gate for this ticket. No existing PR 33 research or implementation plan was found.

## Open questions for implementation planning

- Select the concrete minimal supported preparation addition and catalog accessor representation that allow a label with either credential-resolution choice and visibility while preserving the existing preparation signatures. Current overloads and their null checks are documented above.
- Define the current namespace observation DTO and its absence representation, including exact preservation of empty, whitespace and other supplied string content. The ticket owns the semantics of labels and omission. The current instance DTO and Go validation contain no such field.
- Identify the programmatic Console client surface that fulfills the ticket. Existing Framework observation HTTP and scoped Console browser API carry instance status, while current MCP runtime output carries connection status. Any selected MCP changes involve its DTO, schema, deterministic text and focused contract tests together.
- Map acceptance criteria to executable coverage for independent engines, empty namespaces before invocation, candidate versus active state, abandoned/failed/stale publication, unlabeled replacement, fresh reads/reconnect and restart/reinstall. Current tests and fixture entry points are listed above.

These are ordinary design and verification details for Steps 2 and 3. Research found no decision requiring developer escalation.
