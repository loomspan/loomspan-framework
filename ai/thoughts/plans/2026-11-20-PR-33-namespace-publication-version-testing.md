# Namespace publication version testing plan

## Change summary

Verify host-supplied labels on immutable candidate and active namespace catalogs, authenticated engine instance discovery, selected-target Console MCP discovery and Instance Overview before invocation. The implementation plan at `ai/thoughts/plans/2026-11-20-PR-33-namespace-publication-version.md` settles the API and current wire shape.

This is Step 3 in the confirmed Full 5-Step Pipeline. Planning ran source investigation only. The commands below are required implementation/review work, without claimed results.

## Impacted areas and risks

- Supported Java SkillReloader/SkillCatalog methods and internal generation construction. Risks are changed legacy defaults, accidental null/empty conversion, discarded labels, or labels visible before successful publication.
- Authenticated instance DTOs and active map projection. Risks are empty namespace loss, mixed-generation ID/label pairs, removed namespaces remaining visible, or stale candidates leaking into active reads.
- Go current reader, MCP DTO/schema/text and browser contract. Risks are null/empty conflation, missing required namespace fields accepted as empty, stale selected-target results, and inconsistent output shapes.
- Overview initial load, refresh, reconnect, target switch and reopen. Risks are invocation-dependent inventory, prior-target data, or labels rendered as markup.
- Current docs and explicit tool inventories. Java/Go current producer-reader agreement and exact release-string rejection are protected. The ticket authorizes atomic current wire updates and keeps the marker unchanged.

## Existing coverage and gaps

`SkillReloaderTest` covers empty replacement, captured base/absence, simultaneous candidates, failure disposal, visibility reset and Environment/explicit-map credentials. `DefaultSkillCatalogTest` covers immutable empty metadata. `SupportedSurfaceIntegrationTest`, `NamespaceStartupIntegrationTest` and `NamespaceObservationIntegrationTest` provide real framework composition and restart/captured-generation anchors. The missing cases are publication labels and current discovery before any invocation.

`ObservabilityRestIntegrationTest` and the related HTTP security/context suites protect authenticated instance reads. `ConsoleRestFixtureCorpusTest` generates the shared current REST shape. Go observability DTO/service tests and browser/Console integration tests consume that shape and protect target scoping. MCP schema, SDK server, security and parity tests establish the current fourteen-tool inventory after this change. Frontend Overview/provider/activity tests and target-context E2E supply rendering and freshness anchors.

## Failing test first

Add an authenticated HTTP integration assertion to the existing namespace observation test or a focused companion in the same web/integration test conventions. Install an empty namespace through the existing preparation API, execute no skill, then read instance status and require a namespaces row with its active generation ID and null publicationVersion. The baseline endpoint lacks namespaces, so this fails as a behavioral assertion without relying on uncompilable new API calls. Run that focused test before production edits and record the failure. Then extend it to the labeled overload once the API exists.

This is a feature addition. The red test establishes the missing current-state discovery. Separate lifecycle tests prove the label and publication boundary after compilation supports the new methods.

## Tests to add or update

### Immutable publication labels

- Type is unit and supported-surface integration.
- Locations are `src/test/java/ai/loomspan/internal/skill/SkillReloaderTest.java`, `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillCatalogTest.java` and `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`.
- Prepare and publish P7 on an empty namespace. Candidate and active catalogs preserve namespace, ID and Optional label. Hold the old active snapshot, prepare P8 and assert candidate is new while active/held snapshots retain P7. Publish P8 and assert active changes, held snapshots stay immutable.
- Parameterize exact labels over `P7`, empty string, whitespace, Unicode, line breaks and markup-like content. Null is absent. Do not normalize, trim or infer meaning.
- Abandon and close a labeled candidate, reject foreign/closed/shutdown candidates, and reject a stale labeled candidate after another wins. Verify active ID/label still refer to the winner. Existing resource-disposal assertions continue to hold.
- Extend the competing-candidate test with distinct labels and assert the winning pair, including a concurrent reader when practical. Use latches and existing virtual-thread helpers, avoiding sleeps.
- Replace a labeled generation through each existing prepare signature and assert no inherited label. Retain visibility reset and both credential-resolution assertions. Exercise both new overloads, map copying and no Environment fallback.
- Data uses existing empty configuration and credential fixtures. Mock provider resources as existing tests do, with no model requests.
- Affected surface is Application API and internal lifecycle. Expect existing signatures/defaults to retain behavior and new metadata to be generation immutable.

### Independent engines and restart installation

- Type is Java integration using two independent framework contexts and a restarted context.
- Location is existing `NamespaceObservationIntegrationTest` or a focused `NamespacePublicationObservationIntegrationTest` under the same integration test setup.
- East and West each publish the same empty namespace with P7. Assert distinct process-local generation IDs and instance identities. Read authenticated HTTP before any invocation and compare each row to that engine's active Java snapshot.
- Prepare P8 on East and assert HTTP remains P7. Publish East P8 and make a fresh HTTP request/client to observe P8 while West remains P7. Simulate the lost acknowledgment by obtaining state from discovery without relying on the publish return.
- Reject a stale update after a winner and verify HTTP matches the winning Java snapshot. Abandon another candidate and repeat. Remove the namespace and require absence. Reinstall and require a fresh ID.
- Restart an engine context, reinstall the same namespace with P7 and read it before invocation. Require a fresh process/active generation identity and P7.
- Host-supplied skill documents are empty. Configuration is complete but provider-free. Use the existing Spring HTTP/security integration harness and API-key header.
- Affected surfaces are Application API and current diagnostic REST. Expect authenticated current state without an invocation or historical trace dependency.

### Current Java/Go fixture agreement

- Type is fixture contract and reader validation.
- Locations are `ConsoleRestFixtureCorpusTest`, `loomspan-console-fixtures/application-rest/instance-status.json`, `loomspan-console/internal/observability/dto_test.go` and `service_test.go`.
- Current generated fixture includes at least a labeled namespace, an unlabeled namespace and an empty namespace distinct from skill-derived inventory. Additional cases cover zero installed namespaces, empty supplied label and exact whitespace/Unicode.
- Assert required namespace array/row properties, canonical namespace, unique names, nonblank generation IDs and nullable-string label. Reject missing/null namespaces array, duplicate names, invalid namespace, missing/blank generation ID, missing publicationVersion and non-string non-null labels.
- Verify current compatibility marker, response/header identity and no-store/auth tests still pass. Extend Go read fixtures and all current HTTP mock instance responses to the required shape, avoiding a missing-field fallback.
- Keep committed generated fixtures LF and regenerate with the existing property. Do not hand-normalize meaningful label content.
- Affected surface is ephemeral diagnostic protocol. Expect one coherent current shape and unchanged exact release-string validation.

### MCP programmatic discovery and parity

- Type is unit, SDK protocol and assembled Console integration.
- Locations are new instance tool tests under `loomspan-console/internal/mcpadapter`, existing `server_test.go`, `output_schemas_test.go`, `parity_test.go`, schema/golden tool inventory, and Console integration tests as needed.
- Ordinary tools/list advertises LOOMSPAN_get_instance with read-only annotations, exact empty-input schema and explicit output schema within the established discovery budget. Reject unknown arguments.
- Call through a fresh authenticated MCP session against East and West selected targets with empty labeled namespaces. Every row and upstream observedAt/instanceId agrees with browser/Go service reads. Exercise repeated reads after label replacement and a new session after reconnect.
- Structured and deterministic text output preserve exact string/null facts. Test an omitted label separately from empty string. Test no installed namespaces and multiple rows without executions.
- Exercise wrong bearer key, browser/MCP credential realm separation, no target, incompatible target, upstream invalid metadata, identity replacement and a target change while the upstream read is blocked. Reuse current scope and authentication-generation guards and error envelopes.
- Verify resource-limit failures are explicit, with no partial successful inventory or content replacement. Use the existing output-limit mechanism where applicable and check its actual behavior for this new result.
- Update current inventory assertions in agent eval/conformance fixtures, skill docs and tool-list goldens. Preserve archived run records.
- Affected surfaces are current Console client protocol and internal target/auth boundaries.

### Browser rendering and freshness

- Type is frontend unit/provider and Playwright E2E.
- Locations are `web/src/observability/Overview.test.tsx`, `ObservabilityProvider.test.tsx`, existing activity provider tests, and `web/e2e/target-context.spec.ts` or a focused namespace publication E2E spec reusing `consoleProcess`.
- Render namespace, generation ID and label rows for empty and populated namespaces. Null renders “Not supplied”, supplied empty string is indicated explicitly, exact whitespace remains inspectable, and markup-like labels render as text. Show the empty-inventory state.
- Extend typed fixtures with the required namespaces array. Keep current request sequencing tests and add namespace values so old target or older request responses cannot replace newer inventory.
- Run the actual built Console with two controllable authenticated upstreams. With zero executions and skills, select East P7, prepare-state response unchanged, refresh after East P8, reopen, select West P7 and switch back. Repeat with unlabeled replacement and a restart identity/new generation. The browser fixture represents current wire output, while Java integration proves preparation/publication semantics.
- Exercise reconnect with an updated instance response and no invocation event. Confirm the existing ActivityProvider connection/recovery path requests the current instance snapshot. Add a targeted trigger correction if the new test establishes a gap.
- Assert accessible table headers/row associations and no stale prior-target label. Use existing browser controls and test server state mutation without arbitrary time sleeps.
- Affected surface is internal Console presentation and current selected-target freshness.

## Acceptance mapping

| Ticket criterion | Executable evidence |
| --- | --- |
| East/West P7 empty namespace before execution | Real two-context authenticated Java reads, MCP SDK/Console reads and two-target browser E2E |
| P8 candidate hidden until publish, fresh read after missed acknowledgment | Reloader snapshots, HTTP before/after publication, fresh MCP session/read and browser refresh/reopen |
| Abandoned/failed/stale candidates preserve winning pair | Reloader disposal/concurrency tests and authenticated HTTP winner assertion |
| Existing overloads retain behavior, unlabeled replacement clears label | All old prepare paths, credential/default regression assertions, null wire/MCP and Overview state |
| Restart and host P7 reinstall before execution | Restarted Java context read plus target identity/reinstallation browser scenario |
| README/guidance and Java/Go/UI agreement | Production-generated current fixture, Go reader, MCP schema/text, frontend/E2E and documentation source-anchor review |

## How to run

Use PowerShell from the repository root unless a working directory is given. Quote Maven property arguments because PowerShell can split punctuation unexpectedly.

```powershell
# Focused pre-change red test, using the chosen new behavioral test method name.
.\mvnw.cmd test '-Dtest=NamespacePublicationObservationIntegrationTest#emptyPublishedNamespaceIsDiscoverableBeforeInvocation' '-DfailIfNoTests=false'

# Focused Java checks after implementation.
.\mvnw.cmd test '-Dtest=SkillReloaderTest,SkillGenerationManagerTest,DefaultSkillCatalogTest,SupportedSurfaceIntegrationTest,NamespaceStartupIntegrationTest,NamespaceObservationIntegrationTest,NamespacePublicationObservationIntegrationTest,ObservabilityRestIntegrationTest,ObservabilityHostSecurityIntegrationTest,ConsoleRestFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false'

# Regenerate deliberately changed current REST fixture, then run it without regeneration.
.\mvnw.cmd test '-Dtest=ConsoleRestFixtureCorpusTest' '-Dloomspan.console.fixtures.regenerate=true' '-DfailIfNoTests=false'
.\mvnw.cmd test '-Dtest=ConsoleRestFixtureCorpusTest' '-DfailIfNoTests=false'

# Full Java verification, including current trace fixtures and architecture tests.
.\mvnw.cmd test
```

If the integration scenario is added to an existing class, substitute that exact class/method and record it in the implementation verification notes. The test must execute, not merely be permitted to select zero tests.

```powershell
# From loomspan-console. Use the pinned local Node toolchain verified by the orchestrator.
$env:PATH = 'C:/opendev/code/loomspan-framework/target/pr33-tools/node-v24.18.0-win-x64;' + $env:PATH
go test ./internal/observability ./internal/applicationclient ./internal/browserapi ./internal/mcpadapter ./internal/console ./internal/agentskills ./internal/agenteval
go test ./...
go run ./internal/buildtool verify
go run ./internal/buildtool mcp-conformance

# Build from the verified current assets with the current compatibility version.
go build -trimpath '-ldflags=-X=github.com/loomspan/loomspan-framework/loomspan-console/internal/release.productVersion=1.0.0-beta.10-SNAPSHOT' -o build/loomspan-console.exe ./cmd/loomspan-console
$env:PLAYWRIGHT_BROWSERS_PATH = 'C:/Users/mgiacomi/AppData/Local/ms-playwright'
npm.cmd --prefix web run test:e2e -- e2e/target-context.spec.ts
```

Include any new focused browser spec in the last command. Console buildtool verify already includes frontend typecheck, coverage, build/manifest verification, Go tests and deterministic skill/eval validation. Avoid rerunning those phases independently without a failure or new change requiring it. If a new MCP conformance dependency is unavailable, diagnose and safely restore it before declaring completion.

Run Go race checks for the affected target/observation/MCP path if the changed code introduces synchronization or the concurrent tests expose a concern. Existing Windows compiler setup is documented in Console AGENTS.md. Broad repeated race execution is unnecessary for a pure immutable-metadata propagation once required checks pass.

## Documentation claims and evidence

| Claim | Evidence |
| --- | --- |
| Label supplied during prepare, exact and optional | Both new overloads, Optional catalog tests, credential-mode tests |
| Candidate label is separate from active publication | Prepared/active snapshot and before-publish HTTP tests |
| Active ID and label are one immutable generation | Winning/stale/concurrent publication assertions |
| Empty/Private installed namespaces are discoverable under existing observation authorization | Authenticated HTTP integration and current fixture |
| Omission belongs to each replacement | Legacy preparation paths and null reader/UI tests |
| Current MCP and browser reads work before invocation and reconnect | SDK/Console integration and actual browser E2E |
| Restart requires host label reinstallation | Restarted framework integration |

Baseline documentation comparison is aligned. Update Java API catalog/reload topics and coverage, skill-authoring HTTP topic and coverage, root/Console READMEs, and affected Console skill tool guidance. Remove the now-obsolete statement that namespace enumeration is unavailable. Explain current behavior without ticket tracking history.

## Exit criteria

- [x] A pre-change empty-namespace current-read test failed for the missing behavior and passed after implementation.
- [x] Every acceptance row has actual verification results and no required unrun check.
- [x] All affected tests and required full Java/Console verification pass.
- [x] Existing preparation signatures, visibility defaults and both credential modes retain behavior.
- [x] No new API type leaks internal/autoconfigure signatures, and architecture tests pass.
- [x] Active ID/label pairing survives preparation, abandonment, rejection, concurrency and restart installation.
- [x] Java/Go current protocol fixtures, MCP schemas/text and UI fixtures agree on null, empty strings and empty namespaces.
- [x] Fresh authenticated MCP reads and browser connection/refresh/reopen/reconnect work before execution.
- [x] Updated guidance matches tested semantics, routing and current inventory.

Optional developer checks are none. The acceptance behavior has executable evidence planned. Fresh independent Step 5 verification determines pipeline completion.

## Implementation verification completion

All planned acceptance coverage is implemented and passed. The final full Java run
passed 2,375 tests with zero failures and two optional property-gated diagnostic skips.
Console verify passed the complete Go suite and 608 frontend tests, typecheck, build
and packaged inventory checks. MCP conformance passed. All 26 affected browser tests
passed with the current Console executable. Exact commands, intermediate failure
attribution and acceptance evidence are recorded in the implementation plan.
Independent Step 5 review remains the final pipeline stage.
