# Namespace publication version implementation plan

## Overview

Expose the host's optional publication label on the existing immutable namespace catalog and through current authenticated instance observation. Console clients and Instance Overview will read the installed generation before any invocation, including empty namespaces. The active namespace map remains authoritative.

This plan governs Steps 4 and 5 of the Full 5-Step Pipeline. The developer confirmed the profile with “proceed”. Baseline is commit `8f73de81b38f30555814c91537a96d2af1fa588f` on `main`. Step 0 found only the untracked ticket. The research artifact and these plans are pipeline work. Preserve unrelated changes discovered later.

## Current state analysis

- `SkillReloader.java:16` has four protected preparation signatures. Omitted visibility selects Private, omitted credentials select Environment, and an explicit credential map is copied and has no Environment fallback. `DefaultSkillReloader.java:76` converges these paths before detached preparation.
- `SkillGenerationManager.java:143` captures the target base or absence before construction. Publication checks that base and selects the immutable candidate under `generationMonitor` at lines 268–292. Candidate and active Java snapshots already expose `SkillCatalog` metadata for empty catalogs.
- `DefaultSkillCatalog.java:16` owns immutable namespace metadata. `SkillGeneration.java:98` constructs it before publication. The manager's registered-skill projection at line 130 loses empty namespaces when it flattens skill entries.
- `ObservabilityRestController.java:54` serves authenticated instance status. `ObservabilityRuntime.java:31` receives live metadata suppliers. Go `observability.Service.GetInstance` reads that endpoint through a captured selected-target scope and verifies current scope before returning.
- `ObservabilityOverview` consumes the existing instance read. Manual refresh and `ActivityProvider` connection, recovery and baseline callbacks request authoritative instance status. The paired browser API and bearer-authenticated MCP are separate realms. `LOOMSPAN_get_runtime` currently reports local connection status without an upstream read.

## Desired end state

`publicationVersion` belongs to one immutable generation. A prepared snapshot can show it immediately. Active Java snapshots, authenticated instance reads, MCP instance reads and browser rows show the pair installed at successful publication. Every replacement supplies its own label or absence. Generation ordering, stale checks, removal, retained work and retirement keep their current generation-ID semantics.

The host defines equality and content meaning. Null means omitted. Every supplied string, including empty and whitespace-only strings, remains exact. Labels carry no framework validation, ordering, hashing or equality assertion.

## Scope boundaries

The ticket limits this work to current discovery. Publication history, framework restart persistence, content hashes, event replay, Cloud routing and Sidecar adoption remain outside this implementation. Reinstallation after restart supplies the label through preparation.

## Settled design decisions

### Java API

Add `Optional<String> publicationVersion()` to the already supported `SkillCatalog`. Add these two preparation overloads to the already supported `SkillReloader`.

```java
PreparedSkillUpdate prepare(String namespace, String publicationVersion,
        Collection<SkillDocument> documents, ExecutionConfiguration configuration,
        NamespaceVisibility visibility);
PreparedSkillUpdate prepare(String namespace, String publicationVersion,
        Collection<SkillDocument> documents, ExecutionConfiguration configuration,
        NamespaceVisibility visibility, Map<String, String> credentialValues);
```

The new label is adjacent to namespace. Explicit visibility on these overloads avoids adding four parallel convenience signatures. The first uses Environment resolution. The second copies a required non-null credential map and retains the existing explicit-map rules. Nullable publicationVersion represents omission. Existing signatures route through the same preparation implementation with a null label and retain their existing defaults and argument behavior. The distinct argument order preserves existing valid source calls, including ordinary empty collections and explicit maps. `publish(preparedUpdate)` and `PreparedSkillUpdate` signatures stay as they are.

Store the label once in the immutable generation catalog. Thread it through internal preparation and generation construction before returning a candidate. The manager's namespace observation reads that catalog. Avoid separate mutable label maps or updates after publication. Internal constructor signatures and their test callers may be changed atomically. The existing internal constructor convenience methods may route to the complete constructor as needed for their current uses, without adding compatibility-only methods.

### Current instance observation

Add required `namespaces` to `ObservabilityDtos.InstanceStatus`. Its value is a sorted array of objects containing required `namespace`, `generationId` and nullable `publicationVersion`. Emit null explicitly for omission. Emit `[]` when every namespace has been removed. The instance response continues to identify the process with `instanceId` and the header. Include every installed namespace under existing observation authorization, including Private and empty namespaces.

Add a manager read that captures the active generations' immutable `SkillCatalog` objects under `generationMonitor`, sorts by namespace, and returns an immutable list. Supply it to `ObservabilityRuntime` through the existing current-state wiring. The instance controller selects that list once per response and projects each row from one catalog. This keeps ID and label coherent without changing the existing flattened skill catalog or reconstructing namespaces from skill rows. Existing counters retain their diagnostic snapshot behavior.

Use the existing `INSTANCE_READ`, API-key authentication, no-store response and selected-target access checks. Keep `consoleCompatibilityVersion` unchanged as the ticket directs. Update current Java and Go types, readers, fixtures, schemas and documentation together.

Go represents publicationVersion as `*string` without `omitempty`, preserving null, empty string and exact content. Validate required namespace arrays and row structure, canonical normalized namespace identity, nonblank generation IDs and unique namespace names. Labels accept arbitrary strings and null. Required fields must be validated against raw JSON or an equivalent presence-aware decode where Go zero values would hide absence. Reject malformed current shapes without a legacy reader or inferred inventory.

### Programmatic Console client

Add read-only `LOOMSPAN_get_instance` with empty input, using the existing observation service and standard selected-target capture, domain-error envelope, publication/current-scope check and authentication-generation check. Return instance identity, upstream observedAt and the complete namespaces array, with explicit nullable publicationVersion in structured output. Deterministic text output must preserve the same facts, quoting exact labels and spelling out null. A small explicit MCP result DTO/schema can project these fields from `GetInstance`.

This reuses the authenticated engine instance observation operation. It supplies current state on every call, including fresh MCP sessions and reconnects. The existing browser route continues to use the same Go read. The runtime status tool continues to serve its current local status purpose. Ordinary MCP discovery must advertise the new tool, and schemas, tool-list golden files, inventories, agent skill guidance, conformance tests and deterministic evaluation inventories must agree on the fourteen-tool surface. Preserve historical evaluation results as records of their recorded runs. Update current inventories and inputs only where they purport to define the installed surface.

Apply the existing upstream instance byte limit and ordinary MCP result limit behavior where applicable. A bounded read must either return the complete array or an explicit domain error. It must never silently drop namespaces or alter labels. This work does not add a new paging protocol.

For Cloud/embedder integration, document the direct authenticated engine `GET /_loomspan/observability/v1/instance` path as well as selected-target MCP discovery. The browser paired-session route is a browser integration surface.

### Browser presentation and freshness

Add the namespace contract to TypeScript and render a compact semantic table in Instance Overview with namespace, active generation ID and publication version. Display “Not supplied” for null. Display an explicit “Empty string” indication for a supplied empty string and preserve whitespace with suitable rendering, so omission and supplied content remain distinct. Use React text rendering for labels, including markup-like input. Include a clear empty-inventory state.

Use the existing target context and authoritative refresh mechanism. Verify that initial established connection, reconnect, manual refresh, target switch and page reopen populate current rows before any execution event. If the current connection callback leaves an initial/reopen gap, correct that fetch trigger in the existing provider flow and keep its request sequencing and stale-scope defenses.

## Contract and compatibility impact

| Surface | Impact and evidence | Treatment |
| --- | --- | --- |
| Application API | SkillReloader and SkillCatalog are exact allowlisted types. The ticket protects all existing preparation calls, credential modes and defaults. | Add two prepare methods and one catalog accessor. Preserve current source/binary signatures and behavior. Framework-owned interfaces gain framework implementation methods. No new supported top-level type. |
| Supported SPI | AGENTS.md and architecture tests establish a closed facade and no Java SPI. | Keep framework implementations internal. Introduce no bean replacement contract. |
| Configuration and manifest behavior | Complete execution configuration, visibility and credential parsing already exist. | Keep their behavior. Label is a preparation argument and immutable metadata, with host-owned meaning. |
| Persisted or serialized contracts | Publication state is process-local, with no restoration store. | Host reinstallation supplies the label. No durable format added. |
| Ephemeral diagnostic formats | Authenticated instance REST and MCP current output gain namespace metadata. Java/Go are current-release coupled. | Atomically update current readers, schemas and fixtures. Retain exact release-string rejection and the current marker. Canonical invocation traces remain outside this metadata change. |
| Internal or accidentally exposed implementation | Manager, reloader implementation, catalog implementation, observation runtime, Go DTOs and React are internal. | Change signatures and callers atomically. Avoid compatibility adapters or duplicated authorities. |

- Supported-contract evidence is the API architecture allowlist, README, ticket requirements and supported-surface integration tests.
- Intentional current wire changes are explicitly authorized by Pipeline notes. All consumers in this repository move together.
- Public-surface delta is confined to methods on two existing allowlisted interfaces. Check signature types and technically public internal declarations with `LoomspanPublicSurfaceArchitectureTest`.
- Shim decision is **No shim**. Existing protected preparation methods remain ordinary supported methods with their existing behavior. The new observation shape has one current reader.
- Java-to-Go boundary coordination is **Required** across instance DTO production, Go validation, shared fixtures, MCP schema/text output, browser types, mocks and tests.
- Pipeline notes alignment is **Aligned**. The compatibility marker remains the current release string. Consumer documentation describes the current system directly.

## Skill-Authoring Documentation Impact

**Impact is Affected.** Current namespace inspection helps authors diagnose what the engine serves. Java host guidance also changes. The bundled loomspan-docs router, current source and research agree at baseline, classified **aligned**. The new feature requires corresponding additions rather than correcting a pre-existing defect in prose.

Update these concrete topics in the implementation.

- Root README publication section and supported Java surface summary.
- `agent-skills/loomspan-docs/references/java-api/skill-reload.md` for labeled preparation, candidate/active timing, credentials, omission and restart.
- `agent-skills/loomspan-docs/references/java-api/catalog-and-validation.md` for immutable Optional publication metadata and empty catalogs.
- Java API index coverage entries for catalog/update metadata.
- `agent-skills/loomspan-docs/references/skill-authoring/observability-http.md` for namespace rows on instance, exact label semantics and independent observation access. Replace its stale claim that namespace enumeration is unavailable.
- Skill-authoring index observation coverage entry and source anchors.
- Console README and current Console skill MCP guide/runtime model/routing where namespace discovery and the explicit tool inventory are described. Update current conformance documentation as affected.

Support exact behavioral claims with the publication tests, authenticated HTTP integration tests, shared REST fixture, Go reader/MCP parity tests and browser tests described in the testing plan. Keep routing progressive and topics self-contained. Examples use only supported Java types. Documentation distinguishes process identity, generation identity and host-owned publication labels and explains that omission belongs to each replacement.

## Implementation phases

### Phase 1 — Immutable labeled preparation

Change SkillReloader, SkillCatalog, DefaultSkillReloader, DefaultSkillCatalog, SkillGeneration and SkillGenerationManager preparation paths. Extend focused catalog/reloader and supported-surface tests to prove exact labels, candidate/active separation, stale rejection, unlabeled replacement and both credential modes. Preserve the current publication lock and lifecycle rules.

- [x] Catalog/reloader focused tests and supported-surface integration tests pass.
- [x] Independent manager lifetimes allocate distinct active IDs with the same label.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes.

### Phase 2 — Active namespace observation and Console readers

Add manager namespace snapshot, runtime wiring and instance DTO projection. Update current DTO construction sites and regenerate the REST fixture with labeled, unlabeled and empty namespace rows. Extend the authenticated Java instance tests before invocation. Extend Go instance DTO and validation, application reader fixtures and browser adapter coverage. Include auth/no-store/current-scope behavior in the integration assertions.

- [x] Java REST fixture corpus and HTTP namespace publication integration pass.
- [x] Go reader, browser API and Console integration tests pass with the current shapes.
- [x] Null/empty/exact strings are preserved and malformed namespace rows are rejected.

### Phase 3 — MCP and browser discovery

Add `LOOMSPAN_get_instance` using existing service patterns, explicit schema/text and scoped errors. Update current inventories and schema/golden fixtures atomically. Add Instance Overview rows and typed fixtures. Extend target-context browser coverage for East/West, refresh, reopen, unlabeled replacement and restart identity. Use existing freshness hooks, making a targeted correction only if the new tests expose a gap.

- [x] MCP SDK calls, schema agreement, text/structured parity, auth realm and stale-target tests pass.
- [x] TypeScript typecheck, frontend coverage and browser E2E pass.
- [x] Console buildtool and MCP conformance pass.

### Phase 4 — Documentation and full verification

Apply the documentation inventory above against tested behavior. Run the dedicated testing plan. Mark implementation checks only after actual verification, record exact commands and remaining optional observations, and hand off to independent review.

- [x] Updated knowledge-base guidance has named executable evidence and accurate coverage entries.
- [x] Full Java and Go checks, Console verify/build and affected browser E2E pass.
- [x] Every acceptance criterion is mapped to results in the implementation report.

## Testing strategy

The companion testing plan defines the exact cases and commands. Establish a pre-change red read for an empty published namespace, then cover Java publication semantics and exact current wire agreement. Use real framework contexts for before-invocation discovery and actual Console process/browser tests with controllable upstreams for target selection and freshness. Existing tests continue protecting credential and visibility behavior.

## Performance and lifecycle considerations

Namespace discovery copies immutable metadata under the existing publication lock and does no provider calls. Projection and sorting can happen after capturing the active list. The instance response uses its existing resource limit. Each namespace row obtains its ID and label from the same immutable catalog. Keep observations independent from retained invocation catalogs and avoid holding runtime leases just to return metadata.

## Tooling notes

Implementation adaptations are evidence-backed parts of the selected design.

- Removing every namespace exposed an existing instance-status failure in
  `activeTracePersistence()`, which required an active default namespace. The read now
  uses the existing ONERROR no-runtime fallback when default is absent, allowing the
  required current namespace inventory to be empty. The live HTTP integration covers it.
- The thirteen-tool MCP discovery snapshot measured 25,541 bytes under its 25 KiB
  test ceiling. The required fourteenth tool brings discovery to 26,512 bytes.
  The current inventory test ceiling is 26 KiB. Tool descriptions retain their useful
  controls and the ordinary MCP result limit continues to apply to complete reads.
- Console's declaration test expected twenty-seven supported Java types while the
  baseline architecture allowlist and Java index already list thirty. Update that
  stale assertion to the executable current count as part of current inventory coherence.
- The expanded browser fixture run exposed a stale End-key expectation in the
  already affected artifact-storage spec. HEAD's nested-frame fixture includes a
  final executor-child row and HEAD's timeline selects the final row for End.
  Actual focus was executor-child. Correct the test's older skill-row expectation
  to the current fixture. The assertion now follows the final row in the current fixture.
- The isolated baseline regression test uses HEAD plus the new existing-API HTTP test.
  It fails for the absent namespaces JSON path. Initial in-place attempts encountered
  stale compiled classes and sandbox Mockito attachment failure. Elevated Java execution
  resolves attachment and establishes the baseline behavioral failure.

The orchestrator checked a pinned Node executable at `target/pr33-tools/node-v24.18.0-win-x64`. Prepend its absolute path to PATH for Console buildtool. npm is 12.0.2 and Go from loomspan-console is 1.26.5. JAVA_HOME points to a working JDK and the local Maven cache contains the required relocated JSLT artifact. The matching Playwright cache is `C:/Users/mgiacomi/AppData/Local/ms-playwright`. These observations establish tooling availability, without claiming test results.

## References

- Ticket `ai/thoughts/tickets/loomspan-pr-33-namespace-publication-version.md`
- Research `ai/thoughts/research/2026-11-20-PR-33-namespace-publication-version.md`
- Testing plan `ai/thoughts/plans/2026-11-20-PR-33-namespace-publication-version-testing.md`
- Design lens `ai/thoughts/framework-feature-design-lens.md`

## Implementation and verification results

The implementation follows the settled two-overload Java design and keeps label
authority in the immutable generation catalog. Observation takes namespace metadata
from the active generation map. Current Java, Go, MCP and TypeScript shapes agree on
required namespace rows and explicit nullable labels. Existing preparation methods
still converge on a null label with their existing credential and visibility rules.

### Acceptance evidence

| Acceptance criterion | Implemented evidence |
| --- | --- |
| East and West expose empty P7 before invocation | `NamespacePublicationObservationIntegrationTest#independentEnginesExposeOnlySuccessfulPublicationsAndRestartInstallation` runs independent actual servlet engines and authenticated HTTP reads with distinct process-local generation IDs. MCP SDK and browser namespace-publications coverage read the selected target before execution. |
| Candidate P8 remains separate until publication and is freshly discoverable | The same HTTP test and `SkillReloaderTest#publicationLabelsBelongToTheImmutableWinningGeneration` check candidate/active separation. MCP fresh-session coverage connects after the new publication. Browser coverage refreshes, reopens and switches between East P8 and West P7. |
| Abandonment, failed/stale publication and concurrent winners preserve pairing | The HTTP test covers closed/stale candidates. `SkillReloaderTest#namespaceInventoryKeepsIdsAndLabelsTogetherAcrossConcurrentPublication` checks every observed ID against its immutable label while candidates compete. Existing lifecycle and rejection coverage remains in the full Java suite. |
| Existing calls preserve behavior and unlabeled replacement clears the label | Existing preparation tests remain and supported-surface integration publishes through the old signatures. Labeled credential-mode tests verify Environment and copied explicit maps, including missing/null-map rejection. HTTP/MCP/UI cover explicit null and “Not supplied”. |
| Restart and host reinstallation expose P7 with new identities | The actual HTTP test closes East and starts another engine, reinstalls P7 and checks new instance/generation IDs before invocation. Browser restart coverage reads the new process and reinstalled row. |
| Guidance and current cross-language fixtures agree | Root README, Java catalog/reload guidance, observation topic and coverage indexes, Console README and current MCP skill inventories are updated. `ConsoleRestFixtureCorpusTest` verifies the final default/unlabeled/empty rows byte-for-byte, Go validates the same fixture, and MCP/UI tests distinguish null, empty and exact strings. |

### Verification receipts

- The isolated HEAD regression command
  `.\mvnw.cmd -f target/pr33-baseline/pom.xml test '-Dtest=ObservabilityRestIntegrationTest#instanceDiscoversEmptyNamespaceBeforeInvocation' '-DfailIfNoTests=false'`
  failed as expected for the missing `namespaces` JSON path. This establishes the
  pre-change defect with source compiled from HEAD.
- The first full `.\mvnw.cmd test` ran 2,375 tests with one failure in the exact
  public-method assertion that needed the deliberately added accessor. The two
  skipped tests are optional `pr18.diagnostic`-gated diagnostics. The assertion was
  updated to the new exact method set and `Optional<String>` return type.
- `.\mvnw.cmd test '-Dtest=ApplicationApiValueTest,ConsoleRestFixtureCorpusTest' '-DfailIfNoTests=false'`
  passed all 15 tests after synchronization.
- The final `.\mvnw.cmd test` passed with 2,375 tests, zero failures, zero errors
  and the two optional diagnostic skips. This includes `LoomspanPublicSurfaceArchitectureTest`,
  current REST/trace fixture writers and the actual East/West/restart HTTP lifecycle.
  The final stable-source log is `target/pr33-java-full-final.log`.
- From Console, `go run ./internal/buildtool verify` passed typecheck, all 608
  frontend tests in 49 files, asset build/manifest validation, current skill/eval
  verification and the complete Go suite.
- From Console, `go run ./internal/buildtool mcp-conformance` passed all scenarios
  and exited zero. Its subprocess emitted the known Windows libuv shutdown assertion
  after writing successful result files. The existing buildtool policy handles it.
- From Console,
  `go test ./internal/mcpadapter ./internal/observability -run 'TestInstance|TestBrowserAndMCPPreserveSameNamespace' -count=1`
  passed. The fresh-session test also passed after connecting separately after each
  publication.
- From Console,
  `go build -trimpath '-ldflags=-X=github.com/loomspan/loomspan-framework/loomspan-console/internal/release.productVersion=1.0.0-beta.10-SNAPSHOT' -o build/loomspan-console.exe ./cmd/loomspan-console`
  passed and supplied the current browser test executable.
- From web, `npm.cmd run typecheck` and
  `npm.cmd test -- src/observability/Overview.test.tsx src/observability/ObservabilityProvider.test.tsx src/activity/ActivityProvider.test.tsx`
  passed, with 41 focused tests.
- From web,
  `npm.cmd run test:e2e -- e2e/namespace-publications.spec.ts e2e/target-context.spec.ts`
  passed all four tests.
- From web,
  `npm.cmd run test:e2e -- e2e/activity-stream.spec.ts e2e/artifact-storage.spec.ts e2e/live-executions.spec.ts`
  passed all 22 tests after correcting the baseline fixture's stale End-key oracle.
- `git diff --check` passed.

Java verification and Console protected-profile/browser runs needed elevated execution
because the sandbox blocked Mockito attachment and Windows profile DACL setup. Pinned
Node 24.18.0 and npm 12.0.2 were used for Console verification, with Playwright's existing
cache at `C:/Users/mgiacomi/AppData/Local/ms-playwright`. These are environment execution
adjustments. Product authentication and authorization continue to enforce the existing access boundaries.

Documentation assessment remains Affected, with updated claims aligned to the tested
current implementation. Optional developer checks are none. Independent Step 5 review
still determines pipeline completion.
