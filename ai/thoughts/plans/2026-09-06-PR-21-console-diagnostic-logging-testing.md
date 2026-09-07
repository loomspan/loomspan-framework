# Console Diagnostic Logging Testing Plan

## Change Summary

Implement the paired `2026-09-06-PR-21-console-diagnostic-logging.md` plan. Verify structured default-visible server diagnostics, precise safe causes, single primary ownership, browser/MCP and background correlation, expected-outcome severity, lifecycle transitions and bounded retries. Preserve all browser/MCP messages, envelopes and status behavior.

## Impacted Areas

- New `loomspan-console/internal/diagnostics`; startup `cmd/loomspan-console/main.go`.
- applicationclient error/read/protocol handling and target scope/probe/retries.
- Browser and MCP operations/security/response encoding and response budgets.
- Live stream, baseline refresh, browser relay and raw artifact download.
- Shared artifact acquisition, import, storage/capacity/cleanup and trace analysis errors.
- Console/profile/workspace lifecycle, browser sessions/pairing and MCP authentication lifecycle.

## Risk Assessment

- Error enrichment can accidentally change DTO messages/details or erase errors.Is cancellation; prove both internal distinction and protected outward behavior.
- Shared error claims and suppression can hide unrelated failures or race; test one primary for one shared failure, but separate primary records for distinct write/cleanup failures.
- Ambient defaults can contaminate parallel tests. Use a synchronized in-memory slog handler installed on context, and reserve global default replacement for serial startup tests with cleanup restoration.
- Context copying can change service lifetime or retain credentials; test only safe immutable metadata travels to workers, and use existing lifetime/cancellation tests.
- Upstream URLs, TLS/OS/parser errors, malicious enum values and trace identifiers can disclose content even when a top-level error is safe. Assert the entire rendered record stream excludes canaries, including Debug.
- Distinguish expected caller cancellation/deadline and target rotation from configured internal operation timeout while the caller remains active.
- Retry suppression cannot rely on time.Sleep or unbounded maps. Use fake clocks/timers and fixed reason cardinality; test changed cause, recovery and scope retirement.
- Browser/MCP serialized responses are explicitly protected by ticket; existing exact-version/current trace corpus stays unchanged. Java API/SPI and config/manifest semantics do not change. Internal old log text is intentionally removed, with no tests requiring both formats.
- Skill-authoring impact is No impact. Relevant matching-checkout trace sensitivity/version guidance is aligned; Console README alone gains conventions.

## Existing Test Coverage

Paths below are relative to `loomspan-console/`.

- `internal/applicationclient/get_test.go`: TestGetReturnsFailureLimitExceededWhenBodyExceedsMaxBytes, TestGetReturnsProtocolFailureForOversizedErrorBody, problem mapping, redirects/encoding/instance IDs. Missing reader-cause retention and log metadata.
- applicationclient client/activity/artifact tests cover request and stream contracts; target context/scope tests cover rotation/cancellation/retries.
- `internal/artifact/storage_test.go`: TestFatalStorageErrorLogsDoNotLeakPathsOrCredentials, ENOSPC/cleanup, shutdown, rotation, capacity races. Extend actual fault injection, do not replace with helper-only tests.
- live service/coordinator tests cover stale cursor reconnect, late baseline fencing, stream events and lifecycle publication. Add log count/context assertions using existing fakes.
- browser contracts/errors/request-policy/security/activity/artifact-download/import/trace-analysis tests protect adapter behavior, including committed streams.
- MCP contracts/runtime/security/response-budget/dual-output/parity/trace-semantic/joined-adapter tests protect every tool family and unchanged result semantics.
- browserauth sessions/pairing tests provide entropy and fake clock seams; Console integration tests cover host composition and shutdown.
- traceanalysis parser/processor/fixture-corpus/query tests establish exact-version validation and classifications.

## Bug Reproduction / Failing Test First

### Minimal causal reproduction

- **Name:** `TestReadBoundedPreservesReaderFailure`.
- **Type/location:** unit, `internal/applicationclient/client_test.go` (or new diagnostics_test.go in that package).
- Arrange a reader returning a small byte prefix and a sentinel error whose text contains a secret canary, under a comfortably larger byte limit.
- Act: call existing readBounded.
- Assert `errors.Is(err, sentinel)` is true. This test can be written against existing APIs before production edits.
- **Expected pre-fix failure:** readBounded discards sentinel and returns generic `upstream body exceeds limit` at client.go:245.
- After implementation add companion overflow case (`limit+1` bytes): cause body_limit; reader case cause body_read; max-byte metadata is available without presenting body_read as actual overflow. An exact-limit successful body remains accepted.

### Observable logging reproductions

Capture the current target.Scope caller-cancellation path and assert zero Warn/Error: existing unconditional scope logs should fail. Inject a malformed upstream response into a browser or MCP operation and assert exactly one primary actionable structured record with request/operation ID and safe cause: current missing schema/ownership should fail. Record observed failures in implementation report; do not call an unrun test a demonstrated reproduction.

## Tests to Add/Update

All entries specify current-run diagnostic coherence unless stated otherwise; none requires historical log compatibility.

### 1) Structured startup and safe fatal errors

- **Name:** `TestStartupDiagnosticsJSONDefaults`, `TestStartupFailureExcludesArgumentsAndNestedErrors`.
- **Type/location:** unit/integration, `cmd/loomspan-console/main_test.go`, `internal/diagnostics/diagnostics_test.go`.
- **Proves:** each stderr diagnostic parses as JSON, timestamp parses, Info/Error visible and Debug absent at defaults; stable operation/cause and independent operation ID; invalid assets/config/listener and returned monitor cause are safe and not duplicated. Version stdout behavior stays unchanged; pairing secret output is not copied to diagnostics.
- **Fixtures/mocks:** writer-taking setup, injected verify/serve errors and synthetic config/CLI canaries; existing dependency seams. Test normal successful startup via Console integration seam without a real target.
- **Surface/expectation:** ephemeral diagnostics; config/CLI behavior preserved.

### 2) Safe metadata extraction and primary claims

- **Name:** `TestDiagnosticFactsExcludeNestedContent`, `TestPrimaryClaimSharedAcrossWrappers`, `TestDistinctFailuresRemainDistinct`.
- **Type/location:** unit, `internal/diagnostics/diagnostics_test.go` and consolecore/applicationclient error tests.
- **Proves:** allowlisted enums/numbers and static message only; unknown/malicious string enums fall back safely; error chain remains inspectable; concurrent reports of one attached failure yield one primary; a second distinct error is not suppressed by a context-wide flag.
- **Fixtures/mocks:** errors.Join and wrapped url.Error, net.DNSError, x509 errors, os.PathError, JSON errors and arbitrary error/LogValuer canaries. No arbitrary Error or LogValue output may enter logs.
- **Surface/expectation:** internal and ephemeral diagnostics, atomic replacement of previous ad hoc logging.

### 3) Upstream failure facts with unchanged response mapping

- **Name:** `TestUpstreamDiagnosticCauseAndEndpoint`, `TestBodyReadFailurePreservesPublicMapping`.
- **Type/location:** unit/integration, applicationclient `get_test.go`, `client_test.go`, `artifact_test.go`, `activity_test.go`; browser/MCP diagnostics tests for final records.
- **Proves:** body-read versus overflow, malformed/trailing JSON, invalid instance header, unsupported encoding, HTTP problem read/decode, redirects, DNS/TLS/connection/timeout produce safe accurate facts and endpoint family. Limits include exact names/values. Problem/probe/GET outward mappings unchanged, including GET body-read FailureLimitExceeded and existing Details.
- **Fixtures/mocks:** httptest server or same-package RoundTripper with failing readers; max-1/max/max+1 payloads; credential and URL/query canaries; malformed protocol bodies containing canary text.
- **Surface/expectation:** current diagnostics and protected browser/MCP/upstream behavior. Transport helpers alone emit no primary; final owner emits exactly one.

### 4) Browser and MCP interaction coverage

- **Name:** `TestBrowserFailureDiagnosticsPreserveContract`, `TestMCPFailureDiagnosticsPreserveContract`, `TestMCPToolCorrelationIsDistinct`.
- **Type/location:** integration, new `internal/browserapi/diagnostics_test.go`, `internal/mcpadapter/diagnostics_test.go` using existing adapter fixtures/fakes.
- **Proves:** inject one domain/internal failure for target, observability, acquisition/import, analysis and MCP-management browser families; test MCP runtime, skill, execution, activity, trace inventory/query/read, response-budget and raw SDK error paths. Every failure has a single primary with named operation/request/operation IDs, classification/cause and scope when present. Request IDs differ across ingress requests, tools get distinct child operations, and related records share ingress ID. No credential IDs or user-provided header is adopted.
- **Fixtures/mocks:** existing fake analysis/status/target services and SDK httptest transport. Compare status/body/envelope (including text-plus-structured MCP result) to existing fixtures and explicit message expectations.
- **Surface/expectation:** protected serialized behavior plus diagnostics. Cover budget limit metadata even where the public budget failure is expected and logged only at Debug.

### 5) Expected outcomes and security throttling

- **Name:** `TestExpectedOutcomesNeverWarnOrError`, `TestSecurityRejectionLogsAreBounded`.
- **Type/location:** table-driven unit/integration across browserapi, mcpadapter, target and diagnostics tests.
- **Proves:** caller cancellation/deadline, scope rotation, stale/invalid cursor, ordinary request validation, unavailable/expired resources, invalid user import, auth generation rotation and repeated bad authentication never Warn/Error. Internal timeout with active caller is actionable. Debug rejection series bounded to first plus one summary/minute/reason; Info default produces none.
- **Fixtures/mocks:** canceled contexts, active parent + forced client timeout, invalid bodies/credentials/cursors; fake clock and thousands of distinct attacker-controlled values sharing the same closed reason. Verify constant-size limiter state, no payload keys and accurate suppression count.
- **Surface/expectation:** preserved security and domain behavior; diagnostic severity changes intentional.

### 6) Target transitions and retry series

- **Name:** `TestProbeDiagnosticFirstChangeRecovery`, `TestTargetLifecycleLogsOnlyTransitions`.
- **Type/location:** unit, `internal/target/context_test.go` and `scope_test.go`.
- **Proves:** first failure logged, 100 identical retries silent, changed transport category logged once, success emits one recovery with suppressed count. Auth required/accepted, selected, connected/disconnected and scope rotation Info occur only on changes; unchanged Recheck/polls do not log. New scope resets state, old worker cannot report as new scope. Timer work carries operation/scope context without requester cancellation or credentials.
- **Fixtures/mocks:** existing probe client, fake retry timer/jitter/clock, deterministic scope source, concurrent select/recheck where supported.
- **Surface/expectation:** internal target state and current diagnostics; outward snapshots/rotation behavior preserved.

### 7) Live worker and baseline failures

- **Name:** `TestLiveFailureDiagnosticsAcrossReconnect`, `TestBaselineRefreshDiagnosticContext`.
- **Type/location:** unit/integration, `internal/live/service_test.go` and coordinator tests.
- **Proves:** open/read failure, bad handshake, malformed JSON, invalid cursor protocol and oversize event have one safe record with endpoint/scope/cause and event limit. Baseline refresh failure has named background operation. Repeat/change/recovery count rules hold. Stale-cursor rebaseline and normal cancellation remain below Warn, same connection state emits no Info.
- **Fixtures/mocks:** existing stream servers/fake baseline loader with canary contents/cursors, fake timers; assert no raw values appear at Debug either.
- **Surface/expectation:** current stream diagnostics with existing SSE/target fencing behavior unchanged.

### 8) Post-header browser response failures

- **Name:** `TestDownloadLateFailureDiagnostic`, `TestActivityRelayLateFailureDiagnostic`, `TestResponseEncodingAndWriteDiagnostic`.
- **Type/location:** integration/unit, browserapi artifact_download/activity/errors tests.
- **Proves:** forced upstream read and downstream write failures after 200 retain 200 and existing bytes/headers, do not append an envelope, produce one classified record with request/operation/scope and artifact/activity endpoint. Distinguish source read and response write. Unexpected encoder errors are visible; broken writer with canceled request stays quiet. Separate primary domain error and secondary write failure both appear because they convey distinct failures and share context.
- **Fixtures/mocks:** custom failing ResponseWriter preserving Flusher where needed; failing readers after initial bytes; marshal-unsupported value through helper unit seam; current relay integration fixture.
- **Surface/expectation:** protected browser status/stream semantics plus late-failure diagnostics.

### 9) Shared acquisition, import and storage stages

- **Name:** `TestJoinedAcquisitionOnePrimaryWithWaiterLinks`, `TestArtifactStorageDiagnosticStages`, `TestImportDiagnosticScope`.
- **Type/location:** unit/integration, artifact acquire/storage/import/expiry/lease tests and MCP trace_joined_adapters tests.
- **Proves:** browser and MCP simultaneous waiters produce one primary acquisition failure and at most one link each; link fields identify shared operation, not handles/secrets. Canceling one waiter leaves other work alive; cancel-all/scope rotation emits no erroneous warning. Import with no target omits scope. Copy/sync/close/remove/capacity/expiry failures include operation, stage, safe OS category and correct limit. Fatal storage propagated to coordinator/main is not duplicated; distinct cleanup failure is separately identified with correlation.
- **Fixtures/mocks:** existing artifact fake workspace, ENOSPC/partial cleanup failure, fake clock/entropy, gated reader for deterministic joins. Canary trace ID/path/handle/content.
- **Surface/expectation:** current diagnostics and preserved artifact lifetime/capacity/cancellation/response behavior.

### 10) Analysis rejection and query diagnosis

- **Name:** `TestAnalysisFailureCategoryReachesOwner`, `TestAnalysisQueryDiagnosticsExcludeTraceContent`.
- **Type/location:** unit/integration, traceanalysis processor/query diagnostics and adapter diagnostics tests.
- **Proves:** physical-line/depth/content invalidity categories and applicable limit name/value survive processor error wrapping to acquisition/import owner; processor itself no longer logs duplicate Warn or trace-as-scope. Missing/corrupt manifest/index/payload storage gives safe stage/cause at requesting owner. Imported invalid user data is expected; unusable upstream artifact is distinguishable. Existing output errors stay byte/semantically unchanged.
- **Fixtures/mocks:** current invalid/corpus fixtures and faulting component reader/sink. Use a trace ID canary and known target scope to prove scope identity is authoritative. Do not regenerate corpus merely for logs.
- **Surface/expectation:** ephemeral diagnostic coherence; current exact-version reader/projection semantics preserved.

### 11) Session, pairing and process lifecycle

- **Name:** `TestBrowserLifecycleDiagnosticsExcludeAuthenticationIDs`, `TestConsoleBackgroundFatalDiagnostic`.
- **Type/location:** unit/integration, browserauth sessions/pairing tests; console security/target integration tests; MCP lifecycle tests.
- **Proves:** one Info per successful pairing/session/tab create, actual expiry/release/close and MCP auth state transition; no unchanged bootstrap/auth/heartbeat polling records. Correlation uses new diagnostic IDs, never auth values. Profile/workspace monitor failures and shutdown cleanup show known operation/stage and one primary despite propagation.
- **Fixtures/mocks:** fake entropy/clock and monitor callbacks; existing owned temporary workspace fixtures. Scan captured stderr across startup, failure and teardown, not just operation bodies.
- **Surface/expectation:** current diagnostics with authentication/session lifetime semantics unchanged.

## How to Run

No external services, keys or live targets required. Tests should not write application/trace data to logs. Run from `loomspan-console/` unless otherwise noted.

1. Establish the minimal pre-fix reproduction before production changes:

   `go test ./internal/applicationclient -run TestReadBoundedPreservesReaderFailure -count=1`

2. While implementing, focused affected suites:

   `go test ./internal/diagnostics ./internal/applicationclient ./internal/target ./internal/live ./internal/artifact ./internal/traceanalysis ./internal/browserapi ./internal/mcpadapter ./internal/browserauth ./internal/console ./cmd/loomspan-console`

3. Full Go suite: `go test ./...`.
4. Canonical build/frontend/typecheck/Go verification: `go run ./internal/buildtool verify` (uses declared exact toolchains/locked dependency graph).
5. Concurrency verification on this Windows host, following Console AGENTS:

   ```powershell
   $env:PATH = "C:\msys64\mingw64\bin;" + $env:PATH
   $env:CGO_ENABLED = "1"
   go test -race ./...
   ```

6. `gofmt` changed Go files; review `git diff --check` at root and the production log inventory (`rg -n 'slog\.|log\.|Fprint.*Stderr' loomspan-console --glob '*.go'`). Inventory is a review aid, not a proof that all failure paths are covered.

Java production types are not planned. If any change, run root `./mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=LoomspanPublicSurfaceArchitectureTest" "-DfailIfNoTests=false"`. No Java/Go boundary or fixture content changes are planned, so a new Java corpus run is not necessary just for Go diagnostic wiring; existing Go corpus/semantic tests remain required. If implementation finds a need to change those contracts, stop and revise scope rather than silently updating fixtures.

## Exit Criteria

- [x] Minimal reader reproduction observed failing pre-fix and passing after; failure cause recorded accurately.
- [x] All focused, full, canonical and race commands above pass; actual commands/results recorded in implementation Step Report.
- [x] Every included boundary is exercised by a representative injected unexpected failure, with schema, safe cause, available scope and operation correlation assertions.
- [x] Limit tests prove exact limit metadata and body-read/overflow distinction without changed outward mappings.
- [x] Duplicate counts, shared waiter links, retry/change/recovery, fixed security throttling and true lifecycle transitions are verified deterministically.
- [x] Cancellation/rotation/cursor/validation behavior remains quiet at Warn/Error.
- [x] Entire captured logs exclude every secret/content canary, including wrapped/joined errors and late/background paths at Debug.
- [x] Browser/MCP fixtures/messages/status/security behavior and exact-version Go corpus remain unchanged and pass.
- [x] Obsolete duplicate/raw logging paths are removed; no fallback/compatibility logger retained.
- [x] README states tested fields, defaults, ownership, correlation, secret exclusion and suppression rules; authoring topics need no update.
- [x] No manual observation is a completion gate. Optional developer checks: none required; automated capture and injection cover acceptance.

## References

- `ai/thoughts/tickets/loomspan-pr-21-console-diagnostic-logging.md`.
- `ai/thoughts/research/2026-09-06-PR-21-console-diagnostic-logging.md`.
- Paired implementation plan and `ai/thoughts/framework-feature-design-lens.md`.

Implementation verification is recorded below and in the paired implementation plan.

## Implemented verification coverage (2026-09-06)

The scenarios above are consolidated into owner-focused regression tests plus the unchanged contract, corpus, lifetime and cancellation suites:

| Boundary | Executed test evidence |
| --- | --- |
| Safe schema/defaults/nested content/claims | `diagnostics.TestDiagnosticFactsExcludeNestedContent`, `TestPrimaryClaimSharedAcrossWrappers`, `TestDefaultJSONAndExpectedOutcomes`, `TestServerLogExcludesEmergencyMessageContent` |
| Bounded reader and endpoint identity | `applicationclient.TestReadBoundedPreservesReaderFailure` (observed red before fix), `TestBodyReadFailurePreservesPublicMapping`, `TestEndpointFamiliesNeverIncludeResourceIdentifiers`; existing problem, TLS, redirect, encoding, SSE and header fixtures |
| Detached lifetime and bounded repeat/security | `diagnostics.TestDetachedContextRetainsOnlyDiagnosticMetadata`, `TestRepeatFirstChangeRecovery`, `TestSecurityRejectionLogsAreBounded`; MCP rejection integration |
| Browser operation families | `browserapi.TestBrowserFailureDiagnosticsPreserveContract` covers acquisition, import, target/imported analysis, MCP management and unavailable target dependency; existing browser response/security contracts |
| Browser post-header failures | `TestDownloadLateFailureDiagnostic`, `TestActivityRelayLateFailureDiagnostic`, `TestResponseEncodingAndWriteDiagnostic`, `TestCanceledResponseWriteDiagnosticIsQuiet` |
| MCP ingress/tool correlation | `mcpadapter.TestMCPFailureDiagnosticsPreserveContract`, `TestMCPImportedTraceDiagnosticOmitsFencingScope`, `TestMCPSecurityRejectionDiagnosticsAreBounded`; response-budget diagnostic assertions and existing thirteen-tool contracts |
| Target/live owners | `target.TestProbeDiagnosticFirstChangeRecovery`, `live.TestBaselineRefreshDiagnosticContext`, `TestLiveReadFailureDiagnosticAcrossReconnect`; existing rotation/cancellation/fencing suites |
| Shared artifact and storage/import | `artifact.TestJoinedAcquisitionOnePrimaryWithWaiterLinks`, `TestArtifactStorageDiagnosticStages`, `TestImportDiagnosticOmitsScopeAndExpectedInvalidity`, updated fatal-storage canary test; existing cancellation, capacity, cleanup, expiry and lease tests |
| Analysis and lossy discovery | `traceanalysis.TestAnalysisFailureCategoryReachesOwner`, existing invalidity/corruption/manifest/query/corpus tests, `traceinventory.TestIncompleteInventoryFailureHasOwner` |
| Lifecycle/process | `browserauth.TestBrowserLifecycleDiagnosticsExcludeAuthenticationIDs`, `mcpadapter.TestMCPAuthenticationDiagnosticTransitions`, `main.TestStartupFailureExcludesArgumentsAndNestedErrors`, `console.TestConsoleBackgroundFatalDiagnostic`; existing startup/shutdown integration suites |

No Java production or protocol fixture content changed. The existing Go exact-version fixture corpus remains the cross-component regression gate for this diagnostics-only change.

## Final verification results

Commands run from `loomspan-console/` unless stated otherwise:

- **Observed expected pre-fix failure:** `go test ./internal/applicationclient -run TestReadBoundedPreservesReaderFailure -count=1` — sentinel reader cause was discarded.
- **PASS after fix:** the same reader regression is included in passing applicationclient, full and race suites.
- **PASS:** `go test ./internal/diagnostics ./internal/applicationclient ./internal/target ./internal/live ./internal/artifact ./internal/traceanalysis ./internal/browserapi ./internal/mcpadapter ./internal/browserauth ./internal/console ./cmd/loomspan-console`.
- **PASS:** `go test ./...`.
- **PASS:** `go run ./internal/buildtool verify` — TypeScript typecheck, 46 frontend test files / 497 tests, web asset build and complete Go verification. The unchanged frontend bundle-size advisory is nonfatal.
- **PASS:** `$env:PATH = "C:\msys64\mingw64\bin;" + $env:PATH` followed by `$env:CGO_ENABLED = "1"` and `go test -race ./...`. Trace-analysis race suite completed in 121.475 seconds; every package passed.
- **PASS:** root `git diff --check`; `gofmt -l` produces no output for changed/untracked Go files. Unchanged baseline Go formatting differences were left untouched.
- **Reviewed:** root `rg -n 'slog\.|log\.|Fprint.*Stderr' loomspan-console --glob '*.go' --glob '!**/*_test.go'` — runtime output is centralized in diagnostics and explicit startup configuration. The separate build CLI retains its build-error output; `catalog` search matches are not logging calls.
- **Reviewed:** no Java types, TypeScript, protocol fixtures, configuration keys or compatibility markers changed. Root Java public-surface test is not applicable to this Go-only production diff.

Optional developer checks: none. All implementation children have finished; no background edits or verification processes remain active.
