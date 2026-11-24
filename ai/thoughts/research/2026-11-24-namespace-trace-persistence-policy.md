---
date: 2026-11-24T10:46:29-08:00
researcher: Codex GPT-6.1-sol
git_commit: b6a895955916b7ad55a4c1aab6589d5475ef3086
branch: main
repository: loomspan-framework
topic: "Namespace trace persistence policy in the instance snapshot"
tags: [research, codebase, observability, namespaces, trace-persistence]
status: complete
last_updated: 2026-11-24
last_updated_by: Codex GPT-6.1-sol
---

# Research — Namespace trace persistence policy in the instance snapshot

## Research question

Map the current implementation and verification boundaries for `ai/thoughts/tickets/namespace-trace-persistence-policy.md`. The ticket requires each installed namespace's effective policy, generation ID and publication version before invocation, generation-consistent rows during publication, the existing default-namespace top-level policy semantics, and the current Java–Go release marker.

## Summary

`SkillGenerationManager` owns the active namespace map behind `generationMonitor`. Namespace enumeration selects immutable generation catalogs while holding that monitor. Successful publication replaces the generation under the same monitor. Each generation also holds an `ExecutionRuntime` whose final `tracePersistence` field is the effective policy used when an invocation captures that generation. The current enumeration exposes public `SkillCatalog` snapshots, which contain identity and publication version but do not expose policy.

The authenticated instance controller currently maps those catalogs into three-field `NamespaceStatus` records. Its top-level policy comes from a separate manager supplier that reads only active `default`, with `ONERROR` when its generation or runtime is absent. The Console has concrete Go and TypeScript namespace contracts and a closed MCP result schema. A Java-generated REST fixture links the Java wire contract to Go tests.

Research found no unresolved product decision. Planning must choose the internal projection and the extent of coordinated Console presentation within the ticket's endpoint-focused scope.

## Current execution and publication flow

### Generation-owned effective policy

- `src/main/java/ai/loomspan/internal/core/TracePersistencePolicy.java` defines exactly `NEVER`, `ONERROR`, and `ALWAYS`.
- `ExecutionConfigurationParser` accepts `loomspan.execution-trace.persistence` in the supplied execution configuration. Its `Parsed` result includes the effective `TracePersistencePolicy` separately from the copied properties (`src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:134`, `:161`). An omitted configuration uses the built-in property default. A publication supplies complete execution configuration, independently of preceding generations.
- `SkillGenerationManager.prepare` resolves candidate variables, parses the configuration, validates declarations, and passes `parsed.tracePersistence()` to `prepareCatalog` (`src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:157`, `:174`). `prepareCatalog` creates an execution runtime using that policy and builds a detached generation (`:246`).
- `ExecutionRuntime` stores policy in a final field and returns it through `tracePersistence()` (`src/main/java/ai/loomspan/internal/skill/ExecutionRuntime.java:16`, `:30`). It copies selected execution properties and owns a provider registry. Its policy remains readable after resource closing because closing destroys the registry without changing the policy field.
- `SkillGeneration` stores final namespace, ID, catalog and runtime references. Its internally constructed `DefaultSkillCatalog` captures the exact optional publication version. It exposes `namespace()`, `id()`, `skillCatalog()` and `runtime()` (`src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:113`). Runtime may be null in internal lightweight constructions.
- `LoomspanSessionRunner` uses a captured entry runtime's policy when present (`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:330`). This establishes the effective execution policy independently of whether a trace or invocation has already occurred.

### Startup, preparation and publication

- Startup initializes `default` and Java-assigned namespaces with independent runtimes using the startup persistence setting. All built generations enter `active` and `published` together under `generationMonitor` (`SkillGenerationManager.java:90`). Empty namespaces are represented by complete generations.
- `capturePreparation` captures a base generation ID and an immutable map of installed generations while holding the monitor (`SkillGenerationManager.java:153`). Preparing a candidate does not add it to the active namespace map.
- `publish` checks destruction and the expected base ID while holding the monitor, then calls `activateAndSelect`. The replacement enters `active` and `published` under that monitor (`SkillGenerationManager.java:294`, `:308`). Superseded runtime retirement follows existing ownership leases.
- Removal deletes the active row under the same monitor and marks the old generation superseded (`SkillGenerationManager.java:318`). The supplied expected generation ID controls removal.
- `namespaceCatalogs()` captures each active generation's catalog under the monitor and sorts the result by namespace afterward (`SkillGenerationManager.java:141`). Sorting uses immutable catalogs. An independent lookup of `active(namespace)` after this method returns can observe a newer publication.
- `namespaceCatalogs()` has one production consumer, the observability runtime supplier, and a direct test consumer in `SkillReloaderTest`. It is an internal manager method even though its return values implement supported `SkillCatalog`.

## Endpoint and wiring

- `ObservabilityRestController.instance` requires `INSTANCE_READ`, validates the query and reads the activated observability runtime. It creates `InstanceStatus` with global counts, the top-level policy, TTLs and the namespace list (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRestController.java:53`). It currently maps namespace, generation ID and nullable publication version from each `SkillCatalog` (`:66`).
- `ObservabilityDtos.NamespaceStatus` currently has three fields. `publicationVersion` has explicit `JsonInclude.ALWAYS`, preserving JSON null (`src/main/java/ai/loomspan/internal/observability/web/dto/ObservabilityDtos.java:35`). `InstanceStatus` already has an enum-typed top-level `tracePersistencePolicy` (`:22`).
- `ObservabilityRuntime` currently receives `Supplier<List<SkillCatalog>> namespaces` and a separate `Supplier<TracePersistencePolicy> activeTracePersistence` (`src/main/java/ai/loomspan/internal/observability/ObservabilityRuntime.java:34`, `:39`). Its two constructors differ by whether dynamic generation quota/policy suppliers are supplied. Both are internal signatures.
- `ObservabilityRouteRegistrar` supplies `generations::namespaceCatalogs` and `generations::activeTracePersistence` to the runtime (`src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:151`). Other runtime constructor calls are in `ObservabilityRuntimeTest`, `ObservabilityActivationCoordinatorTest`, and `ObservabilityApiKeyFilterTest`.
- `activeTracePersistence` reads only `active.get("default")` while holding `generationMonitor`. Missing default or missing runtime returns `ONERROR`. Other installed namespace policies have no role in this method (`SkillGenerationManager.java:222`). The top-level read and namespace enumeration are separate monitor acquisitions today.
- The API key filter and host-security integration tests protect existing endpoint access. The ticket adds a fact inside the existing authenticated response. No additional Java application extension point appears in this path.

## Protected consumers and serialized evidence

### Go observation and browser boundary

- `loomspan-console/internal/observability/dto.go:12` defines `InstanceStatus`, including the top-level policy and `[]NamespacePublication`. `NamespacePublication` currently contains namespace, generation ID and `*string` publication version (`:27`).
- Custom unmarshalling requires a non-null namespace array, valid namespace spellings, nonblank generation IDs, presence of nullable publication version, and unique namespaces. JSON decoding currently ignores unmodeled namespace properties.
- The observation service obtains current target state and carries these typed DTOs into browser and MCP consumers. The browser `InstanceStatus` contract uses `NamespacePublication[]`, and its TypeScript namespace type currently has the same three fields (`loomspan-console/web/src/api/contracts.ts:125`).
- `ObservabilityOverview` renders those three namespace facts in a table and renders the top-level policy in Instance configuration (`loomspan-console/web/src/observability/Overview.tsx:67`). It has no per-namespace policy column today. Its fixtures occur in `Overview.test.tsx` and `ObservabilityProvider.test.tsx`, with live publication scenarios under `web/e2e/namespace-publications.spec.ts`.

### MCP instance tool

- `LOOMSPAN_get_instance` forwards the typed namespace slice in its structured `instanceResult` (`loomspan-console/internal/mcpadapter/instance.go:17`). It also produces a compact text representation.
- `instanceText` explicitly emits each existing namespace field (`instance.go:58`). `instanceOutputSchema` explicitly declares and requires the current three fields and uses a closed compact object (`:76`). The returned Go type, text and schema are independently specified artifacts.
- The tools-list snapshot is committed at `loomspan-console/internal/mcpadapter/testdata/tools-list-response.json`. Instance output schema, parity and SDK tests inspect this contract.

### Shared Java–Go fixture

- `ConsoleRestFixtureCorpusTest.generatedCorpusMatchesCommittedFixturesByteForByte` generates Java DTO JSON and compares it with `loomspan-console-fixtures/application-rest`. The explicit `loomspan.console.fixtures.regenerate` system property enables regeneration (`src/test/java/ai/loomspan/internal/observability/web/ConsoleRestFixtureCorpusTest.java:40`).
- `fixtures()` constructs `instance-status.json` with default, empty and unlabeled namespace rows (`ConsoleRestFixtureCorpusTest.java:153`). This is an executable construction site for a changed Java namespace record signature.
- `loomspan-console/internal/observability/dto_test.go:12` decodes the shared instance fixture. `TestInstanceNamespaceWireValidation` separately tests valid null, empty and exact supplied labels, malformed identities, missing fields, wrong types and duplicate rows (`:416`).
- Go MCP `instance_test.go` covers current reads before execution, exact labels, fresh SDK sessions, empty inventories, response budgets, and target/authentication races. These are distinct from generation-publication races inside Framework.

## API and contract classification

| Category | Current evidence and exposure |
| --- | --- |
| Application API | `SkillCatalog` is allowlisted and deliberately supported. Its existing signatures expose namespace, generation ID, optional publication version and skills. The ticket explicitly requires no API extension. |
| Supported SPI | Repository policy exposes no supported Java SPI or bean-replacement surface. No such contract is established by this flow. |
| Configuration and manifest contracts | `loomspan.execution-trace.persistence` is already supported per-generation execution configuration. The ticket consumes the effective value without changing syntax or defaults. |
| Persisted or serialized contracts | The endpoint's typed wire shape is consumed by Go and browser clients. The exact release compatibility marker and shared fixtures establish coordinated same-release protocol expectations. No cross-version or durable migration behavior is established. |
| Ephemeral diagnostic formats | Current instance observations and Console projections represent active process state. The change concerns current diagnostics before invocation. Canonical trace format and invocation-captured metadata are separate existing paths. |
| Internal or accidentally exposed implementation | Manager methods, generations, runtimes, web DTOs, internal constructors and suppliers are internal collaborators. Public modifiers support cross-package wiring. Existing overloads do not establish a supported consumer compatibility obligation. |

`LoomspanPublicSurfaceArchitectureTest` is the executable authority for supported public types and deliberately public internal types (`src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:54`, `:362`, `:378`). Production-type changes require running it. New technically public top-level internal types are also subject to its internal allowlist.

`pom.xml:9` currently declares `1.0.0-beta.10-SNAPSHOT`. `src/main/resources-filtered/META-INF/loomspan-release.properties` contains `consoleCompatibilityVersion=${project.version}`. Existing endpoint tests assert that release string. There is no separate numeric protocol counter in the inspected path.

## Existing verification anchors

- `NamespacePublicationObservationIntegrationTest` starts real authenticated servlet engines. It covers empty namespace reads before execution, prepared candidates, abandoned/stale candidates, replacement publication, null/empty/whitespace/exact labels, removal and independent engines. The helper `read` verifies 200, no-store and instance identity headers. Its `EMPTY` configuration uses built-in defaults. It currently asserts identity and label, with sequential publications (`src/test/java/ai/loomspan/internal/observability/web/NamespacePublicationObservationIntegrationTest.java:29`).
- `ObservabilityRestIntegrationTest` covers authenticated instance reads and empty namespace discovery, preserving camelCase under host SNAKE_CASE configuration (`src/test/java/ai/loomspan/internal/observability/web/ObservabilityRestIntegrationTest.java:59`, `:80`).
- `SkillGenerationManagerTest` has runtime factory fixtures and publication/capture resource ownership tests. Its `hostCredentialsAndExecutionSettingsRemainWithCapturedGeneration` demonstrates independently prepared effective runtimes and retained generation settings (`src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java:442`).
- `ConsoleRestFixtureCorpusTest` provides deterministic DTO serialization evidence. `ObservabilityRuntimeTest`, activation and key-filter tests cover internal constructor consumers.
- Applicable verification command families are Maven focused tests, required `LoomspanPublicSurfaceArchitectureTest`, Console `go test ./...`, Console `go run ./internal/buildtool verify`, and web `npm run typecheck` / focused Vitest when TypeScript changes. The Console AGENTS file describes MSYS2 GCC configuration for any Go race check. This research stage ran no tests.

## Documentation alignment and authoring impact

The same-checkout `agent-skills/loomspan-docs/SKILL.md` reports `1.0.0-beta.10-SNAPSHOT`, matching Maven. Its skill-authoring index routes HTTP diagnosis to `observability-http.md`. The topic describes required sorted namespace rows containing identity and nullable version, visibility after successful publication and availability before execution. `README.md:282` and `loomspan-console/README.md:281` describe the same current behavior.

Drift classification is **aligned** for the current checkout. The existing documents and current executable row shape agree. The ticket adds diagnostic information useful to a skill author inspecting effective trace storage policy. The relevant documentation surface is the HTTP topic and its index coverage row, together with Framework/Console endpoint descriptions where their row inventories are kept explicit. This authoring impact concerns inspection and debugging. No manifest, invocation, trace-retention or persistence execution semantics change is implied by this ticket.

## Historical context

`ai/thoughts/future/skill-namespaces-followup.md` records the independent generation-runtime ownership model and defers resource-sharing measurements. It establishes no measurement or optimization gate for namespace correctness work. The developer's starting checkout includes a deleted predecessor future note and the new untracked ticket. Both are explicitly preserved in the ticket's Execution notes. No previous research or plan artifact for this exact ticket exists in the current checkout.

## Metadata collection

The requested `bash ai/scripts/spec_metadata.sh` failed because the default Windows Bash entry point was denied. Running the available MSYS2 Bash directly also failed during its named-object initialization. Equivalent read-only PowerShell/Git metadata supplied the actual timestamp, branch, repository and commit recorded above. No thoughts-status identity was available, so the researcher is the executing Codex model.

## Open questions for planning

- Choose the internal immutable namespace observation projection that carries generation ID, nullable version and effective runtime policy together under the existing publication ownership boundary. Public `SkillCatalog` currently has no policy field.
- Decide how far the added endpoint fact is carried through the existing Console typed DTO, MCP schema/text and browser presentation. The current consumers and their exact field declarations are mapped above. The ticket directly requires the authenticated Framework snapshot and preserves the existing release version.
- Select deterministic coverage for an overlapping snapshot/publication and missing-runtime fallback. The existing authenticated publication test currently performs sequential reads and publications.

These are implementation and verification choices that can be resolved from the ticket and inspected source. No developer escalation is required by this research.
