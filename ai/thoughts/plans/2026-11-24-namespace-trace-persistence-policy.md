# Namespace trace persistence policy implementation plan

## Overview

Expose each installed namespace's effective `tracePersistencePolicy` in the authenticated instance snapshot. Capture namespace, generation ID, nullable publication version and policy together from the active generation. Carry the fact through the existing Console contracts and diagnostic views.

Selected execution profile is the Full 5-Step Pipeline, confirmed by the developer. This plan covers implementation and verification for Step 4 and supplies requirements to the fresh Step 5 review.

## Current state and evidence

- `SkillGenerationManager#namespaceCatalogs` selects active immutable catalogs under `generationMonitor` and sorts them by namespace. `publish` replaces the active generation under the same monitor. Each generation's final runtime reference owns its effective policy through `ExecutionRuntime#tracePersistence`. See `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:141`, `:222`, `:294` and `SkillGeneration.java:113`.
- The instance controller maps those catalogs into `ObservabilityDtos.NamespaceStatus`, whose fields are namespace, generation ID and publication version. `publicationVersion` explicitly serializes null. See `src/main/java/ai/loomspan/internal/observability/web/ObservabilityRestController.java:53` and `web/dto/ObservabilityDtos.java:35`.
- `ObservabilityRuntime` and `ObservabilityRouteRegistrar` connect the catalog supplier to the endpoint. The top-level policy uses `SkillGenerationManager#activeTracePersistence`, which reads only active `default` and returns `ONERROR` if its generation or runtime is absent.
- Go `NamespacePublication`, TypeScript `NamespacePublication`, MCP `instanceText` and the closed instance output schema enumerate the namespace fields. The Overview table displays those fields. The Java REST fixture corpus is decoded by Go tests. See `loomspan-console/internal/observability/dto.go:27`, `internal/mcpadapter/instance.go:58`, `web/src/api/contracts.ts:125` and `web/src/observability/Overview.tsx:67`.
- `NamespacePublicationObservationIntegrationTest` supplies a real authenticated servlet engine and prepared/published empty namespace scenarios. `SkillGenerationManagerTest` supplies direct generation and runtime construction patterns. `ConsoleRestFixtureCorpusTest` owns the committed REST fixture generation.

## Desired result

Every installed namespace row contains `namespace`, `generationId`, `publicationVersion` and `tracePersistencePolicy`. Policy is one of `NEVER`, `ONERROR` and `ALWAYS` and is present even when the version is null. Rows remain sorted, including empty and Private namespaces, and available before invocation. Preparation alone leaves active reads unchanged.

Each row is an immutable value copied from one selected generation. Publication overlapping construction yields that generation's entire tuple or the replacement's entire tuple. The contract requires consistency within each row. Global counters and the top-level default policy keep their existing independent read boundaries.

The top-level field reads the active `default` runtime policy and uses its existing `ONERROR` fallback for missing generation or runtime. Other namespace policies do not affect it.

## Design decisions

1. Replace the internal catalog enumeration used for observation with an immutable namespace snapshot projection owned by `SkillGenerationManager`. Use a nested public record such as `NamespaceSnapshot(String namespace, String generationId, String publicationVersion, TracePersistencePolicy tracePersistencePolicy)`. It is an internal cross-package value, and nesting avoids a new public top-level implementation type. A dedicated projection is necessary because the supported `SkillCatalog` application API deliberately excludes runtime policy.
2. Build every field of each projection from the same `SkillGeneration` while holding `generationMonitor`. Read policy from that generation's runtime, using `ONERROR` for internal runtime-less constructions. Preserve namespace sorting and immutable list semantics. No runtime lease is necessary because policy is final and remains readable after runtime resource retirement.
3. Change the observation supplier and its callers atomically to the projection. Remove the superseded `namespaceCatalogs` method after updating its direct test consumer. Avoid retaining an unused internal compatibility path. Keep `activeTracePersistence` as the top-level authority.
4. Add the enum field to the Java namespace DTO and map it directly from the projection. The controller must perform no fresh per-namespace active lookup when assembling a row.
5. Carry the field through Go, MCP and TypeScript so the existing Console paths preserve the complete Framework fact. The Go namespace decoder should require a valid policy value using the existing enum spellings. Add it to MCP structured output, required schema properties, compact text and tool schema snapshot. Add a Trace persistence column to the existing namespace table and update typed fixtures. These are coordinated consumers of the existing instance contract.
6. Keep `consoleCompatibilityVersion` at its current release-derived value. The ticket explicitly directs this additive field update within the current development release. Existing exact-release rejection behavior remains the verification baseline.

The manager monitor and immutable generation are the existing authority and synchronization mechanism. A controller join between catalog and a later active lookup could pair values from different publications. Adding policy to the supported catalog API would expand the application API for an observability concern. The internal projection expresses the required fact at its owner with no new mutable state or extension point.

## Scope boundaries

The deliverable is the current instance endpoint and its in-repository Console consumers, fixtures and diagnostic guidance. Cloud reconciliation, restart detection and trace retention/execution semantics are outside the ticket. The existing working-tree deletion of `ai/thoughts/future/namespace-trace-persistence-console-snapshot.md` belongs to the starting checkout and must be preserved.

## Skill-Authoring Documentation Impact

Impact is **Affected**. Skill authors use observability to diagnose effective trace storage policy before execution. The same-checkout `loomspan-docs` skill matches Maven `1.0.0-beta.10-SNAPSHOT`. Its routing index and `observability-http.md` agree with the current executable three-field row. Drift classification is **aligned** before implementation.

Update `agent-skills/loomspan-docs/references/skill-authoring/observability-http.md` to describe the four required row fields, policy values, one-generation pairing, publication visibility and top-level default policy/fallback. Update the Observability HTTP diagnostics coverage row in its `README.md` with the new focused evidence. Update the corresponding explicit field inventories in root `README.md` and `loomspan-console/README.md`, including the MCP/table descriptions. Keep existing routing and describe the resulting supported contract directly.

Evidence comes from the authenticated publication integration tests, deterministic manager overlap test, `activeTracePersistence` fallback tests and Java-to-Go fixture tests. No authoring syntax or persistence configuration defaults change. Keep the topic self-contained and focused on diagnostic interpretation, with stable named test/source anchors and no delivery-history prose.

## Contract and Compatibility Impact

| Surface | Impact and evidence | Treatment |
| --- | --- | --- |
| Application API | The closed supported surface in `ai.loomspan.api`, including `SkillCatalog`, needs no additions. | Verify `LoomspanPublicSurfaceArchitectureTest`. |
| Supported SPI | Repository guidance exposes no supported SPI or internal bean replacement contract. | Add no customization point. |
| Configuration and manifest contracts | Effective existing configuration is observed. Parsing, defaults, candidate completeness and invocation policy selection retain their semantics. | Exercise policies through existing configuration entry points. |
| Persisted or serialized contracts | The instance REST payload, Go/browser DTOs and MCP output gain a required per-row field within the existing same-release diagnostic boundary. Verified consumers are all in this repository. | Update Java, Go, TypeScript, fixtures, schemas, tests and documentation together. |
| Ephemeral diagnostic formats | Instance diagnostics gain an authoritative fact. Canonical NDJSON and execution journal representations are unaffected. | Preserve access control, diagnostic fidelity and existing release validation. |
| Internal or accidentally exposed implementation | Manager projection, runtime supplier/constructors and DTO record signatures change. Public modifiers enable internal cross-package wiring. | Update callers atomically and remove the obsolete internal enumeration path. |

The ticket and current Console usages establish the endpoint contract. The public-surface delta is limited to a nested internal value and internal method/signature changes. **No shim** is required under the ticket's explicit development-stage instruction. Java-to-Go coordination is **Required** for the instance payload and MCP/browser consumers. The release marker decision is explicit above and aligns with the ticket. New top-level application types, Spring extension beans and supported API allowlist entries are unnecessary.

No dead or duplicated production path requiring separate scope was established. Replacing the observation-only catalog enumeration removes the internal path superseded by this ticket.

## Phase 1 — Capture and expose namespace policy

1. Add the internal immutable projection and replace the manager enumeration with a method such as `namespaceSnapshots`. Capture all row values under `generationMonitor`, sort the copied rows by namespace and return an immutable list.
2. Update both `ObservabilityRuntime` constructor namespace supplier types, `namespaces()` and registrar wiring. Update direct constructor tests and the existing manager enumeration test.
3. Extend `ObservabilityDtos.NamespaceStatus` with `TracePersistencePolicy tracePersistencePolicy`, keep explicit null publication-version serialization, and map the four fields directly in the controller.
4. Implement Java coverage from the testing plan. Start with the authenticated missing-policy red test, then cover generation overlap and fallback.

Success criteria are passing focused Java tests and the public-surface architecture test, with all namespace facts sourced from one generation and the top-level default behavior verified.

## Phase 2 — Coordinate Console consumers and fixtures

1. Add `TracePersistencePolicy` to Go `NamespacePublication`, validate the required enum in its decoder and update all wire/typed test fixtures containing namespace rows. Preserve null and exact supplied publication labels.
2. Extend MCP instance text, required closed schema, description as needed and focused parity/SDK tests. Regenerate its tools-list snapshot through the existing opt-in snapshot mechanism and update the exact serialized discovery-byte expectation in `server_test.go` to the measured result while retaining its response-budget assertion.
3. Extend the TypeScript namespace type and existing Overview table. Update typed browser fixtures, focused component tests and any namespace fixture in e2e helpers. Avoid unrelated UI work.
4. Extend Java `ConsoleRestFixtureCorpusTest` construction rows to include all three policies across representative rows and regenerate only affected REST fixtures through its supported property. Run the corpus again without regeneration, then verify the Go consumer.

Success criteria are passing Java fixture equality, Go tests and build verification, browser type checking and focused Overview/provider tests. Current exact release mismatch tests must continue to pass within the Go suite.

## Phase 3 — Document and verify the resulting contract

1. Apply the documentation updates listed in the authoring-impact section. Preserve LF in root README and committed fixtures.
2. Run the complete required command set in the testing plan. Record exact commands/results, any recoverable attempts and acceptance-criterion mapping in the implementation result or governing plan.
3. Check the diff for the current compatibility marker, closed application API, obsolete internal enumeration references, and unrelated edits. Preserve starting checkout changes.

Success criteria are all testing-plan exit checks satisfied, with every acceptance criterion mapped to executable evidence and substantive artifacts ready for independent review.

## Testing strategy

Use real authenticated HTTP for the pre-invocation namespace policy matrix and publication visibility. Use deterministic latches around generation snapshot extraction for publication overlap, with a direct manager fixture that supplies distinguishable prior and replacement tuples. Test missing default and missing-runtime fallback separately. The REST fixture bridges Java serialization to the Go decoder. Existing MCP schema tests and browser components verify that the fact survives the complete current instance path.

Detailed cases and commands are in `ai/thoughts/plans/2026-11-24-namespace-trace-persistence-policy-testing.md`. Optional developer checks are none.

## Performance and state

The snapshot adds one enum read and a small immutable value per installed namespace inside the existing monitor. Sorting remains outside the selection critical section. The design adds no persistent state, resource ownership or runtime leases. Monitor work remains bounded by namespace enumeration.

## References

- Ticket `ai/thoughts/tickets/namespace-trace-persistence-policy.md`
- Research `ai/thoughts/research/2026-11-24-namespace-trace-persistence-policy.md`
- Design lens `ai/thoughts/framework-feature-design-lens.md`
- Repository and Console `AGENTS.md`

## Step 4 implementation results

Implemented the immutable `SkillGenerationManager.NamespaceSnapshot` projection under the publication monitor, the authenticated namespace field and coordinated Go/MCP/browser consumers. The supported Java API and release marker sources have zero diff. Diagnostic documentation and its coverage table describe the resulting contract. Documentation drift classification is **aligned** against the implemented source, focused tests and Java wire fixture.

The MCP instance tool uses a compact description and an enum-only policy schema to keep discovery within the existing 26 KiB response budget. The enum restricts values to the three policy strings. The measured response is 26,616 bytes, and the existing budget assertion is retained.

### Acceptance evidence

| Criterion | Executable evidence |
| --- | --- |
| Policies before invocation, including NEVER and null version | `NamespacePublicationObservationIntegrationTest#instanceReportsEffectiveNamespacePoliciesBeforeInvocation`, Java REST fixture equality, Go `TestInstanceStatusDecodesFromFixture` and `TestInstanceNamespaceWireValidation`. |
| Prepared replacement visibility and concurrent tuple consistency | Authenticated policy test, `SkillGenerationManagerTest#namespaceSnapshotRetainsOneGenerationDuringPublication`, existing publication integration scenarios and `SkillReloaderTest`. |
| Top-level default policy and ONERROR fallback | Authenticated policy test replaces/removes default while another namespace uses ALWAYS. `SkillGenerationManagerTest#defaultPolicyFallsBackWhenGenerationOrRuntimeIsAbsent` covers absent/runtime-less default and a complete runtime-less namespace row. |
| Closed API, endpoint boundary and current release marker | `LoomspanPublicSurfaceArchitectureTest`, existing authenticated REST/security tests, Go exact-release tests, MCP schema/SDK/parity tests, browser type checking and focused components. Release marker and supported API source diffs are empty. |

### Verification recorded during implementation

- Expected red failure from `.\mvnw.cmd test '-Dtest=NamespacePublicationObservationIntegrationTest#instanceReportsEffectiveNamespacePoliciesBeforeInvocation' '-DfailIfNoTests=false'`. The namespace row lacked `tracePersistencePolicy` before the production edit.
- Passed `.\mvnw.cmd test '-Dtest=SkillGenerationManagerTest,SkillReloaderTest,NamespacePublicationObservationIntegrationTest,ObservabilityRestIntegrationTest,ObservabilityHostSecurityIntegrationTest,ObservabilityRuntimeTest,ObservabilityActivationCoordinatorTest,ObservabilityApiKeyFilterTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' '-Dloomspan.console.fixtures.regenerate=true' '-DfailIfNoTests=false'` with 73 tests.
- Passed the same Java command without `-Dloomspan.console.fixtures.regenerate=true`, with 73 tests and committed fixture equality.
- Passed `go test ./...` from `loomspan-console`.
- Passed `go test ./internal/mcpadapter -run '^TestCompatible2025ProtocolInitializesListsAndCallsRealRuntimeTool$' -count=1` after opt-in snapshot generation and again with snapshot generation disabled.
- Passed `npm run typecheck` and `npm test -- src/observability/Overview.test.tsx src/observability/ObservabilityProvider.test.tsx` from `loomspan-console/web`, with 24 component tests.
- Passed `git diff --check`. The replaced internal `namespaceCatalogs` enumeration has no source callers.

The initial sandbox Java regression attempt was interrupted after a JVM attach named-pipe access failure. Native Windows execution completed the required tests. Initial sandbox Go tests failed Windows protected-DACL checks, and native execution passed. An early Go fixture read ran before Java fixture regeneration completed and was resolved by the regenerated corpus. The initial browser run exposed the required fixture annotation and row-name updates, which were corrected before the passing run.

The standard Console build command rejected host Node.js 24.22.1 against its pinned 24.18.0 requirement. A workspace-local Node.js 24.18.0 executable and launcher for the existing npm 12.0.2 were prepared under ignored `target/namespace-policy-node` for verification. The first launcher selected bundled npm 11.19.1, then was corrected to the installed npm 12.0.2. Passed `go run ./internal/buildtool verify` with the pinned toolchains. Its 49 frontend files and 610 tests passed with coverage, generated assets passed verification, and its full Go suite passed.

Optional developer checks are none.


The pinned build verification runs from `loomspan-console` with this process-local setup. The ignored directory already contains the downloaded binary and launcher.

```powershell
$nodeTools = (Resolve-Path '..\target\namespace-policy-node').Path
$env:PATH = $nodeTools + ';' + $env:PATH
go run ./internal/buildtool verify
```

All three implementation phases and all required testing-plan exit checks are complete. No required verification remains pending. The final fresh Step 5 review establishes pipeline completion.
