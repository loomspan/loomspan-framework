# PR 37.6 — Carry qualified skill identities through Console

## Outcome

Make Framework observation data and its Console consumers agree on the skill selected at runtime.

## Requirements

- Follow each skill identity through Framework's Console-facing producers, serialized contracts, Go consumers and UI. Include catalogs, observations, trace inspection, lookups, filters and diagnostic displays wherever they carry or display an identity. Expose namespace and local name in catalog results and render qualified references with a colon.
- Update stored identity-bearing samples and contract fixtures according to the original target of each field. Preserve raw provider names, tool arguments, diagnostic content and evidence verbatim. Update Console help, setup and contract documentation alongside the behavior.

## Acceptance criteria

- [x] A Console catalog result distinguishes two owners of the same local name and exposes reusable components. A lookup or drill-down selects the intended pair, and qualified labels agree with Framework.
- [x] Trace inspection displays the colon-qualified resolved skill and retains the actual double-hyphen function name in raw provider data. Fixtures verify that unrelated content survives unchanged.
- [x] Console-facing samples, help and contract examples match the producer and consumer behavior.

## Context

Follows PR 37.5 in the local PR 37 series. PR 37.7 audits documentation across the rollout. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Serialized observation contracts must agree across Java, Go and the UI.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.


## Completion record

### Implemented boundary

Catalog entries derive their colon-qualified key from validated namespace and local-name components. Java REST DTOs, Go observation DTOs, compact MCP results/schema/text and TypeScript list/detail contracts carry all three fields. Consumers reject missing, malformed or inconsistent tuples. Skill Detail displays the reusable components. Exact registered-name lookup and encoded navigation distinguish `orders:lookup` and `warehouse:lookup`.

The shared Go validator now matches Framework component grammar, reserved substrings and `namespace + 2 + localName <= 64`. Captured entry owners and generations are exact. Publication owners remain independently valid up to 64 characters. Recorded frame filters use explicit skill facts and preserve complete qualified names.

The product version remains the coordinated unreleased `1.0.0-beta.10-SNAPSHOT`. Its compatibility marker is derived from the root POM and linked Console version. Exact unequal/missing-version rejection remains covered. No compatibility alias, reader or fallback was introduced.

### Acceptance evidence

| Criterion | Evidence |
| --- | --- |
| Same local name under two owners, reusable components and exact drill-down | `DefaultRegisteredSkillCatalogTest#sameLocalNamesKeepReusableComponentsAndExactLookup`, JSON tuple rejection, Go `TestCatalogIdentityTupleIsRequiredAndExact`, MCP `TestCatalogSelectsEachOwnerOfTheSameLocalName`, `SkillCatalog.test.tsx` and the built Console `target-context.spec.ts` owner-navigation case. |
| Qualified semantic trace and exact raw evidence | Regenerated `namespace-captured-context` retains `orders:search` and `tax:search`, adding raw `tax--search` with original argument text and an identity-like business string. `TestNamespaceCorpusKeepsNativeProviderNameAndResolvedSkill` reads the persisted content, and `TraceRecords.model.test.tsx` renders the native name. The real `NamespaceExecutionAuthorityIntegrationTest` passes in the final Java gate. Existing exact same-local frame-filter tests now use `orders:search` and `tax:search`. |
| Current samples, help and contracts | Authoritative unflagged Java generators and Go byte comparisons pass. README/setup, Console runtime references/MCP guidance and authoring coverage use the final tuple and raw/resolved forms. Canonical verification/build and browser workflows pass. |

### Semantic conversion and content audit

- Java declaration/source fixtures and their resolved identities were converted by original owner and local target. Examples include `default-CheckDns` to `default:check-dns`, `default-test_entry` to `default:test-entry`, `orders-search` to `orders:search` and `tax-search` to `tax:search`. The diagnostic labels, Java methods, business values and source-specific fields keep their authored meaning.
- REST/SSE and canonical trace/expected corpora were regenerated through their Java producers. Artifact metadata was regenerated after canonical bytes. Browser fixture values match their Go marshalled producers byte-for-byte and use LF. MCP snapshots include both new components and exact deterministic text.
- Parsed comparisons against pre-ticket HEAD found **zero changes to existing MODEL_REQUEST_SENT/MODEL_RESPONSE_RECEIVED data payloads**, matching by record type, frame and occurrence. A separate recursive comparison found **zero changed existing toolCalls/toolResponses values**. The added native exchange is new evidence. Existing raw single-hyphen and arbitrary/rejected names remain recorded verbatim.
- The new argument sentinel retains `{ "businessReference": "warehouse-lookup", "token": "original" }`. Existing Unicode, diagnostic JSON, evidence, Authorization/token data, source YAML CRLF and the journal projection exception remain protected by the corpus and final Java/Go/UI gates. Captured generation IDs such as `orders-old`, `tax-retired` and `audit-unexecuted` retain their original values.
- Current evaluation fixture checksums were refreshed with `scripts/loomspan_version.py::refresh_fixture_metadata`. Dated replay results and their archived package have no diff. Canonical verification includes replay validation.
- Required catalog fields initially increased tools/list above its 26 KiB discovery ceiling. Equivalent tool descriptions were shortened while retaining accounting, continuation and evidence guidance. The verified current response is **26,605 bytes**, below 26,624. Source-specific schemas and existing 32 KiB response limits remain enforced. Exact current dual-output baselines were updated.

### Verification

The final unflagged Java boundary/corpus/architecture command passed **146 tests**, zero failures/errors/skips, in `target/pr376-java-final.log`.

```powershell
.\mvnw.cmd test '-Dtest=DefaultRegisteredSkillCatalogTest,RegisteredSkillEntryTest,ObservabilityDtoMapperTest,ObservabilityJsonCodecContractTest,ObservabilityRestIntegrationTest,ObservabilitySseIntegrationTest,ObservabilityArtifactIntegrationTest,ActiveProviderAttemptObservabilityIntegrationTest,NamespacePublicationObservationIntegrationTest,NamespaceObservationIntegrationTest,EntrySkillIdentityTest,LiveActivityProjectorTest,DefaultExecutionObservationHandleTest,ExecutionObservationConcurrencyTest,ExecutionTraceContractTest,ExecutionJournalProjectorTest,ConsoleRestFixtureCorpusTest,ConsoleSseFixtureCorpusTest,ConsoleArtifactFixtureCorpusTest,ConsoleTraceFixtureCorpusTest,NamespaceExecutionAuthorityIntegrationTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

A subsequent formatting-only production edit was verified by **18 passing tests** in `target/pr376-java-final-format.log`.

```powershell
.\mvnw.cmd test '-Dtest=DefaultRegisteredSkillCatalogTest,RegisteredSkillEntryTest,ObservabilityJsonCodecContractTest,LoomspanPublicSurfaceArchitectureTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
.\mvnw.cmd test-compile '-DskipTests'
```

Whole test compilation passed in `target/pr376-test-compile-final.log`. The baseline `go test ./internal/skillidentity` failed at `TestPortableCapture` before the validator edit and passes afterward.

From `loomspan-console`, the following all passed with the pinned cached Go 1.26.5, Node 24.18.0 and npm 12.0.2. Native Windows test execution resolves the sandbox's existing DACL-protection failures. Command-local `npm_config_cache` points at the prepared ignored cache and `npm_config_offline=true` uses its locked packages.

```powershell
$pr37NodeBin = 'C:/opendev/code/loomspan-framework/loomspan-console/target/pr37-tools/node-24.18.0/package/bin'
$env:PATH = "$pr37NodeBin;$env:PATH"
$env:npm_config_cache = 'C:/opendev/code/loomspan-framework/loomspan-console/target/pr37-tools/npm-cache'
$env:npm_config_offline = 'true'
go test ./...
go run ./internal/buildtool verify
go run ./internal/buildtool build
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $env:USERPROFILE 'AppData/Local/ms-playwright'
npm.cmd --prefix web run test:e2e -- target-context.spec.ts record-context.spec.ts portable-trace-import.spec.ts live-executions.spec.ts activity-stream.spec.ts namespace-publications.spec.ts
$env:PATH = "C:/msys64/mingw64/bin;$env:PATH"
$env:CGO_ENABLED = '1'
go test -race ./...
```

Logs are `loomspan-console/target/pr37-tools/pr376-go-final.log`, `pr376-verify-final.log`, `pr376-build-final.log`, `pr376-e2e.log` and `pr376-race-final.log`. Both canonical frontend runs passed **610 tests across 49 files** and their coverage thresholds. All **15 selected Playwright tests** passed against the newly built versioned executable. The complete race suite passed, including traceanalysis at 225 seconds.

A final same-local-owner MCP test was then added and passed normally and with the race detector.

```powershell
go test ./internal/mcpadapter -run TestCatalogSelectsEachOwnerOfTheSameLocalName -count=1
go test -race ./internal/mcpadapter -run TestCatalogSelectsEachOwnerOfTheSameLocalName -count=1
```

The focused UI gate passed 177 tests, and the added native-name rendering case passed in its 13-test model-record class and both canonical 610-test runs. Final `git -c core.safecrlf=false diff --check`, required LF checks and current fixture metadata checks passed.

### Earlier deferrals and remaining series work

| Check | Disposition |
| --- | --- |
| PR 37.5 namespace observation, REST/trace corpora and Console contracts/Go/UI | Closed by the 146-test Java gate, full Go/corpus suites, canonical verification/build and selected browser/race checks. |
| Shared variable fixture and `ExecutorGenerationExecutionIntegrationTest#workedVariableCorpusReachesProviderAndActualExecutor`, `StartupVariableResolutionIntegrationTest` | Remain assigned to PR 37.8. Run those exact classes/method after reconciling the `read_record` worked fixture with portable declaration grammar. |
| Complete integrated Framework build | Remains assigned to PR 37.8, including `.\mvnw.cmd verify` with the local Mockito agent when needed. Console's complete canonical build already passes here. |
| Repository and downstream documentation inventory | PR 37.7 owns the broad audit and PR 37.8 owns the Sidecar handoff. |

No required PR 37.6 implementation check remains open. Independent Step 5 review is next. Optional developer checks are none.
