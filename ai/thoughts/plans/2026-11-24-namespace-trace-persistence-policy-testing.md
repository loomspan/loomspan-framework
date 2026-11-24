# Namespace trace persistence policy testing plan

## Change summary

Namespace rows in the authenticated instance endpoint and its Console consumers gain required `tracePersistencePolicy`. The Framework captures policy, generation ID and nullable publication version from one active generation before any invocation. The top-level field retains active-default semantics and its `ONERROR` fallback.

This plan governs Step 4 of the confirmed Full 5-Step Pipeline together with `2026-11-24-namespace-trace-persistence-policy.md`. Planning performed read-only source inspection and ran no tests.

## Impacted areas and risks

| Area | Risk and required evidence |
| --- | --- |
| Manager snapshot and publication monitor | A row might combine catalog identity from one publication and policy from another. Force overlap during extraction with distinguishable tuples. |
| Authenticated instance serialization | Empty namespaces, null versions or `NEVER` could omit the field. Assert property presence and exact values in real HTTP responses before execution. |
| Top-level policy | An aggregate or another namespace could replace the default policy. Test default replacement, removal and runtime absence. |
| Go, MCP and browser consumers | Typed decoding or explicit projections could discard the field. Use Java fixtures, decoder validation, schema/text tests and table assertions. |
| Application boundary and release marker | An internal change could accidentally extend Java API or alter release matching. Run the architecture test and existing exact-release tests, inspect marker diff. |

The closed Application API is protected by its executable allowlists. There is no supported SPI affected. Configuration and manifest contracts retain existing policy parsing and defaults. The instance payload is an existing same-release serialized diagnostic contract, coordinated atomically across all in-repository consumers. Canonical trace and journal formats have no field delta. Internal constructors and the replaced catalog enumeration require updated callers, with no compatibility shims.

## Existing coverage

- `NamespacePublicationObservationIntegrationTest` starts an authenticated real servlet engine and exercises empty namespaces before execution, preparation, abandoned/stale candidates, replacements, exact/null labels, removal and independent engines.
- `ObservabilityRestIntegrationTest` covers authentication, instance wire casing and namespace discovery. `ObservabilityHostSecurityIntegrationTest` covers host security integration.
- `SkillGenerationManagerTest` has independent runtime configuration and captured generation fixtures. Lightweight manager construction permits runtime-less generations, covering the existing fallback condition.
- `ConsoleRestFixtureCorpusTest` serializes actual Java DTOs and compares committed REST JSON. Go `internal/observability/dto_test.go` decodes it and tests namespace wire validation.
- MCP instance/schema/parity/SDK tests, the tools-list snapshot, `Overview.test.tsx` and `ObservabilityProvider.test.tsx` cover current consumer projections.

The current Java namespace DTO has no policy component, so a JSON presence assertion provides a reliable pre-fix failure. Existing publication tests are sequential and need deterministic overlap coverage.

## Failing test first

Extend `NamespacePublicationObservationIntegrationTest` with a focused test such as `instanceReportsEffectiveNamespacePoliciesBeforeInvocation`.

1. Start the existing servlet engine with API key and no skill files. Its installed default namespace uses `ONERROR`.
2. Publish an empty namespace with complete execution configuration selecting `ALWAYS`, labeled `P1`.
3. Take one authenticated instance snapshot before invoking anything. Assert the default row includes `ONERROR`, the installed row includes `ALWAYS`, each has the exact active generation ID and version, and both execution/trace counts are zero.
4. Assert `row.has("tracePersistencePolicy")` and exact string value.

Run this test before changing production code. Expected failure is absence of per-row `tracePersistencePolicy`. Existing APIs suffice for this red test, avoiding a compile-only failure from a proposed internal type.

## Tests to add or update

### 1. Authenticated namespace policy and publication matrix

Type is integration. Location is `src/test/java/ai/loomspan/internal/observability/web/NamespacePublicationObservationIntegrationTest.java`.

Use existing `engine`, `read` and public `SkillReloader` patterns. Add a helper for complete minimal execution configuration with `loomspan.execution-trace.persistence` selecting each policy, and extend the row assertion helper to check required policy.

Prove these cases through authenticated HTTP, with no invocation.

- Default `ONERROR` and a second namespace `ALWAYS` appear in one snapshot with their exact IDs and versions.
- A namespace using `NEVER` has the field with a null publication version. Assert JSON null presence separately from empty/exact string version cases.
- A prepared replacement with a different version and policy leaves the prior tuple visible until publication. Publication then replaces all tuple values together. Existing abandoned and stale preparation assertions continue to hold.
- Top-level policy is `ONERROR` while another namespace uses `ALWAYS`. Replacing default with `NEVER` updates the top-level field to `NEVER`. Removing default while the `ALWAYS` namespace remains restores top-level `ONERROR`.
- Namespace sort order, empty inventory and authenticated response headers retain existing assertions. Existing unauthorized endpoint checks remain in the REST suite.

Fixtures use real empty generations and no model provider calls. The surface is serialized diagnostic behavior with existing authentication and configuration semantics.

### 2. Deterministic publication overlap

Type is unit/concurrency. Location is `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`.

Create a prior generation with tuple `(id-old, version-old, ONERROR)` and replacement `(id-new, version-new, ALWAYS)`. Use the existing internal generation constructor and a mock `SkillCatalog` matching the generation ID/namespace. A controlled `publicationVersion()` answer can signal that snapshot field extraction has begun and wait on a release latch. Use real manager publication and the real snapshot method. Mock runtime policy values only, avoiding provider creation.

Start snapshot extraction in one executor. After it reaches the latch, start replacement publication in another executor and signal its attempt immediately before `publish`. Release extraction and await both operations with bounded timeouts. Assert the retained row contains the complete old tuple, and a later snapshot contains the complete new tuple. With extraction held under the manager monitor, publication cannot interleave its fields. Ensure all latches are released and executor/tasks closed in `finally` blocks, and propagate worker exceptions to the test thread.

Also retain a completed immutable snapshot across a subsequent replacement and assert its values remain the prior tuple. This verifies stability through controller serialization after selection. No production hooks or sleeps are needed. The affected surface is internal implementation supporting the required serialized invariant.

### 3. Missing generation and missing runtime fallback

Type is unit. Location is `SkillGenerationManagerTest.java`.

Assert `activeTracePersistence()` returns `ONERROR` with default absent, including when another runtime-backed namespace uses `ALWAYS`. Publish a lightweight runtime-less default and assert the same fallback. For the new namespace projection, assert a runtime-less installed row has required `ONERROR` with exact ID and null version. Use current lightweight constructors and manager helpers. This establishes the explicitly supported internal null condition without adding test-only production behavior.

### 4. Java wire fixture and internal caller coherence

Type is serialization/unit. Locations include `ConsoleRestFixtureCorpusTest`, `ObservabilityRuntimeTest`, `ObservabilityActivationCoordinatorTest`, `ObservabilityApiKeyFilterTest` and the existing `SkillReloaderTest` enumeration assertion.

Update every namespace record construction and namespace supplier type. Give the shared instance fixture representative `ONERROR`, `ALWAYS` and `NEVER` rows while preserving exact/null version cases. Regenerate using the existing opt-in property, inspect affected fixture diff, then run equality without regeneration. The fixture must establish camelCase property spelling, enum serialization and explicit version null.

Run `LoomspanPublicSurfaceArchitectureTest` after production type changes. Keep the supported application API allowlist closed. If the implementation chooses a public top-level internal type instead of the planned nested record, it must deliberately document and update only the technical internal allowlist with a specific rationale.

### 5. Go namespace wire contract

Type is unit/fixture integration. Location is `loomspan-console/internal/observability/dto_test.go` plus current affected service/browser test fixtures.

Decode the committed Java fixture and assert all three policies survive. Extend `TestInstanceNamespaceWireValidation` with valid `NEVER`, `ONERROR`, `ALWAYS`, null versions and empty arrays. Reject missing, null, empty and unknown policy values. Preserve existing ID/name/version/duplicate validation cases. Update all fake endpoint JSON and typed namespace constructions so they represent the complete current contract.

Existing Go exact resolved-release rejection tests run as part of `go test ./...`. Their semantics remain required, with the current compatibility version.

### 6. MCP and browser propagation

Type is Go unit/schema and browser component. Locations are the current MCP instance tests and tools-list snapshot, `web/src/observability/Overview.test.tsx` and `ObservabilityProvider.test.tsx`.

Assert MCP structured output and compact text expose each row's policy, and its closed output schema requires the field with the allowed enum values. Regenerate the tools-list snapshot using `LOOMSPAN_UPDATE_SNAPSHOTS=1` only for the applicable snapshot test, then run normally. Update all namespace fixtures and schema fixtures discovered by compilation/tests.

Assert the Overview namespace table renders different policies for different rows, including `NEVER` with an absent label, alongside exact generation IDs and existing version displays. Keep top-level policy rendering covered. Type checking discovers every TypeScript namespace construction requiring the field. Existing e2e fixtures should be made current, while new full browser e2e infrastructure is unnecessary for this field projection because real HTTP, Go transport/schema and focused component coverage connect the path.

## Commands

Run from repository root unless a directory is stated. Quote Maven system-property arguments in PowerShell.

```powershell
.\mvnw.cmd test '-Dtest=NamespacePublicationObservationIntegrationTest#instanceReportsEffectiveNamespacePoliciesBeforeInvocation' '-DfailIfNoTests=false'
.\mvnw.cmd test '-Dtest=SkillGenerationManagerTest,SkillReloaderTest,NamespacePublicationObservationIntegrationTest,ObservabilityRestIntegrationTest,ObservabilityHostSecurityIntegrationTest,ObservabilityRuntimeTest,ObservabilityActivationCoordinatorTest,ObservabilityApiKeyFilterTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false'
```

The first command is the red check and later the focused green check. Adapt only its method selector if the descriptive test name changes. The second command is the final required Java regression set and includes the required architecture check. It exercises existing HTTP authentication and release assertions as well as changed internal suppliers.

Regenerate Java REST fixtures after intentional DTO edits, then rerun the equality test without the property.

```powershell
.\mvnw.cmd test '-Dtest=ConsoleRestFixtureCorpusTest' '-Dloomspan.console.fixtures.regenerate=true' '-DfailIfNoTests=false'
.\mvnw.cmd test '-Dtest=ConsoleRestFixtureCorpusTest' '-DfailIfNoTests=false'
```

From `loomspan-console` run the standard required Console checks.

```powershell
go test ./...
go run ./internal/buildtool verify
```

For the changed MCP tools-list snapshot, update the exact serialized byte expectation in `internal/mcpadapter/server_test.go` to the measured output and retain the discovery-budget assertion. Run the existing snapshot test with its supported opt-in, then remove the setting before the standard suite.

```powershell
$env:LOOMSPAN_UPDATE_SNAPSHOTS = '1'
go test ./internal/mcpadapter -run '^TestCompatible2025ProtocolInitializesListsAndCallsRealRuntimeTool$' -count=1
Remove-Item Env:LOOMSPAN_UPDATE_SNAPSHOTS
go test ./internal/mcpadapter -run '^TestCompatible2025ProtocolInitializesListsAndCallsRealRuntimeTool$' -count=1
```

Restore an existing environment value if one was present before the command. Regeneration is limited to the changed discovery schema snapshot.

From `loomspan-console/web` run the changed browser contract checks.

```powershell
npm run typecheck
npm test -- src/observability/Overview.test.tsx src/observability/ObservabilityProvider.test.tsx
```

`go run ./internal/buildtool verify` owns the standard Console build checks. No Go concurrency code changes are planned, so a Go race run is unnecessary for the Java publication invariant. Add a race check only if implementation changes Go synchronization.

## Acceptance mapping and exit criteria

| Ticket criterion | Required evidence |
| --- | --- |
| Pre-invocation effective policy and complete row for all policies/null version | Authenticated policy matrix and Java/Go fixture assertions. |
| Prepared updates hidden and per-row tuple consistent during publication | Authenticated sequential visibility assertions, deterministic manager overlap and retained snapshot stability. |
| Top-level default semantics and `ONERROR` fallback | Authenticated default replacement/removal with foreign `ALWAYS`, plus runtime-less manager unit case. |
| Existing endpoint boundary, closed Java API and current compatibility version | Architecture test, real authenticated route tests, coordinated Go/MCP/browser contract checks and diff inspection of release marker sources. |

- [x] The focused red test fails on missing per-row policy before production edits.
- [x] All required post-implementation commands pass, with failed attempts diagnosed and resolved.
- [x] Concurrency evidence uses controlled overlap and bounded waits, and verifies entire distinguishable tuples.
- [x] All policies and nullable publication versions have explicit wire evidence.
- [x] Supported Java API allowlists and compatibility release value satisfy the ticket.
- [x] Go, MCP, browser fixtures and documentation describe the same required field.
- [x] Updated diagnostic guidance cites the implemented focused tests, and the coverage table records its evidence.
- [x] Existing working-tree changes are preserved and the superseded internal enumeration has no callers.
- [x] Step 4 records exact commands/results and maps each ticket criterion to passing evidence for fresh Step 5 review.

Optional developer checks are none. No required verification depends on manual observation or an external service.
