---
date: 2026-09-06T19:02:51-07:00
researcher: GPT-6
git_commit: 8c464eaebc349c3398a2ca35a4bca680ead13a6c
branch: main
repository: loomspan-framework
topic: "PR 21 Console diagnostic logging"
tags: [research, codebase, console, logging]
status: complete
last_updated: 2026-09-06
last_updated_by: GPT-6
---

# Research: Console diagnostic logging

**Date:** 2026-09-06T19:02:51-07:00  
**Researcher:** GPT-6  
**Git Commit:** 8c464eaebc349c3398a2ca35a4bca680ead13a6c  
**Branch:** main  
**Repository:** loomspan-framework

## Research Question

Map the current Go Console failure paths, diagnostic ownership, lifecycle state, correlation opportunities, security boundaries, and protected response behavior for `ai/thoughts/tickets/loomspan-pr-21-console-diagnostic-logging.md`.

## Research Checklist

- [x] Read ticket, pipeline protocols, repository instructions, and design lens.
- [x] Inventory startup and existing logs.
- [x] Trace browser/MCP, upstream/target, live/background, artifact, and analysis boundaries.
- [x] Identify safe classifications, errors, contracts, fixtures, and test anchors.
- [x] Consult version-aligned documentation router and assess authoring impact.
- [x] Record metadata and findings.

## Summary

The backend already imports standard-library `log/slog` in seven production files, but startup does not install a handler. Error chains through `applicationclient.Failure` and `consolecore.Error` retain internal causes while preserving fixed consumer-facing text. Existing operations cross request, target lifetime, and joined acquisition contexts: browser/MCP request logging alone cannot describe all background or post-header failures.

## Detailed Findings

### Startup and composition

`loomspan-console/cmd/loomspan-console/main.go:29` obtains embedded assets, constructs dependencies, establishes signal cancellation, and runs Console. Fatal errors currently use `fmt.Fprintln(os.Stderr, ...)`; the final returned error is printed directly. `run` at line 61 validates flags/assets/config and includes caller-supplied arguments in one error. No production `slog.SetDefault` or handler construction exists.

`internal/console/service.go:53` owns workspace/profile acquisition, lifecycle coordinator, target/live/artifact/analysis and adapter wiring. Profile/workspace safety monitors start as background goroutines at lines 133–134. Its stdout contains a workspace banner (line 81), the deliberately user-visible secret-bearing pairing URL (line 231), and browser-opening guidance (line 299). These interactive outputs are distinct from diagnostic logging; pairing URLs contain authentication material.

### Existing diagnostic inventory

All paths below are under `loomspan-console/`:

| File | Existing log behavior |
| --- | --- |
| `internal/applicationclient/client.go:127` | Error-body read, non-200 status, and bounded successful-body failures logged at Error; read failures include raw error. |
| `internal/applicationclient/artifact.go:190` | Artifact non-200 and problem-body read diagnostics, including raw read error. |
| `internal/target/scope.go:54` | Instance mismatch, GET/open-stream failures, and caller/scope cancellation logged at Error. |
| `internal/live/service.go:237` | Stream-open errors logged again after scope propagation. Read, handshake/JSON validation, cursor, and event-size logs occur at 484–640. Several include raw error/cursor. |
| `internal/artifact/acquire.go:446` | Fatal storage failure logs owner ID. |
| `internal/traceanalysis/processor.go:105` | Deferred Warn for classified content rejection; `scopeId` variable is assigned `req.Metadata.TraceID`, not target scope. |
| `internal/browserapi/artifact_download.go:89` | Post-header upstream reader failure logs raw error and caller-derived trace ID. |

A single non-200 operation can therefore log in applicationclient, scope, and live. No shared suppression or logging correlation state exists in those paths. `internal/artifact/storage_test.go:467` already captures the slog default handler and checks fatal storage logging for sensitive data.

### Error preservation and safe classification

`internal/consolecore/errors.go` defines the shared domain codes and `Details` fields, including limit name/value, transport category, compatibility versions, and raw-download availability. `Error.cause` is private and exposed by `Unwrap`; adapters serialize explicit DTO fields rather than the cause.

`internal/applicationclient/errors.go:40` defines `Failure` with a closed failure kind, transport category, expected/observed version, retryability, and private cause. Its `ConsoleError` at line 78 fixes the public messages/codes and retains the failure as cause. Transport classification at `client.go:282` uses errors.As/Is for DNS, timeout, TLS issuer/hostname/date/handshake, and connection failures without requiring raw error strings.

`client.go:245` currently returns the same new `upstream body exceeds limit` error when `io.ReadAll` fails or observed size exceeds `maxBytes`. The original reader cause is lost there. GET uses the same outward limit failure for successful-body read failure and actual overflow. Probe and problem-body paths also call this helper. These outward behaviors and the retained internal distinction are separate facts.

`internal/traceanalysis/errors.go` wraps a closed `InvalidityCategory` in an internal error and uses `errors.Join` when another cause exists. All content categories map to the same outward `INVALID_ARTIFACT` message. `categoryOf` extracts the internal reason. Filesystem failures can include paths and nested OS errors; application transport failures can include raw URLs through `url.Error`; JSON/parser errors and supplied identifiers can include content. A safe top-level message alone does not make nested errors safe to log.

### Browser interaction boundary

`internal/browserapi/router.go:97` establishes security checks and a closed switch of operation paths. Raw download is routed separately. Normal handlers receive `*http.Request`, so request context spans domain service calls. `exchange` creates a session after pairing consumption; `bootstrap` registers tabs; release and heartbeat follow later in this file. Session IDs, CSRF tokens, cookies, and pairing secrets are authentication data.

`internal/browserauth/sessions.go:57` owns session creation; Bootstrap at 87 owns tab registration; ReleaseTab at 136, Close at 169, and expireLocked at 205 own lifecycle transitions. Repeated authentication checks may trigger expiry. The registry has the exact state needed to distinguish creation from repeated bootstrap/heartbeat.

`internal/browserapi/errors.go:28` writes the browser error envelope; `writeJSON` handles serialization failure, while `writeJSONBytes` ignores returned write errors. `internal/browserapi/observability.go` contains domain-to-HTTP mapping. Logging beside all `writeError` calls would include routine validation/security outcomes and would miss causes already reduced to strings.

`internal/browserapi/activity.go:38` currently ignores SSE JSON/write errors; the activity stream begins its HTTP 200 at line 102 and later writes lifecycle/activity records. Several late write errors return without an error response. `artifact_download.go:22` directly streams upstream raw evidence without local cache; upstream open errors return shared domain responses, but after headers read/write failure ends the body. HTTP status cannot be replaced after that commit.

### MCP boundary

`internal/mcpadapter/server.go:55` registers runtime, skill, execution, activity and trace tools with the SDK. It uses stateless Streamable HTTP, JSON responses, maximum body size, and request cancellation propagation. `SecurityHandler` in `security.go:22` checks host/origin, enablement, authorization, admission generation, request size, and timeout. Its admission context is passed into the SDK handlers. The bearer token and admitted authentication generation are security state, not request identity.

`checkedDomainFailure` at `server.go:106` validates authentication generation before returning a domain failure. `contracts.go` defines explicit result/error envelopes; MCP details are a selected subset of domain details. `runtime.go:42` has separate runtime output/error generation. Budget handling also lives in `response_budget.go`. Existing tests protect text-plus-structured outputs, error envelopes, body/response limits, generation invalidation, schemas and trace semantic parity.

### Target state and background retries

`internal/target/context.go:323` serializes probes and commits results while holding target state ownership. Its current state includes target selection, connection, authentication, compatibility, instance identity, and live availability. Successful probes activate registered scope owners only on initial activation. `commitFailureLocked` at 433 maps upstream failures into status/domain errors. `rotateLocked` at 480 closes the previous client and cancels the old scope before installing a new random scope ID.

`scheduleRetryLocked` at 517 uses bounded exponential delay with jitter, retry generation, and a timer callback. The callback probes with `context.Background()` (line 548). New scope contexts also start from Background (line 501). Caller metadata therefore does not automatically survive these boundaries. Ordinary caller cancellation is explicitly distinguished from scope invalidation before error mapping.

`internal/target/scope.go` wraps caller context with scope cancellation for GET, activity open, and artifact open. It revalidates instance mismatches and protects publication against target changes. Upstream endpoint strings can contain encoded caller identifiers/query cursors; they are not safe diagnostic labels without classification.

### Live streams

`internal/live/service.go:195` starts stream and baseline-refresh workers from the service parent context rather than an individual browser/MCP request. `run` at 215 initializes baseline, opens the activity stream, handles stale cursors/resets/revalidation, retries with backoff, and publishes connection facts. `consume` at 473 parses stream frames, validates handshake/activity/cursors, and detects protocol/size conditions. `publishConnectionFor` at 386 owns connection changes. `refreshBaseline` at 417 calls the wired authoritative baseline loader; repeated refresh failures can originate in ordinary upstream/observability code without any browser call.

### Acquisition, import, storage, and analysis

`internal/artifact/service.go:151` joins Acquire calls by evidence owner and trace ID. At line 215 it constructs a leader context from service lifetime plus scope cancellation; waiters cancel independently. `acquire.go:48` runs the leader, loads metadata, opens the upstream artifact, installs/processes the stream, and publishes one shared result. `failAcquisition` owns joined failure completion. Copy, sync, close, cleanup, capacity and processor errors can be distinct stages of this operation; cleanup can join additional failures.

`internal/artifact/import.go:15` is a separate synchronous import boundary. It validates import limits, preflights the first record, creates an imported owner entry, and uses service lifetime for installation context. Imported evidence has an owner but no selected target requirement. `markImportError` adjusts domain presentation. `expiry.go`, `capacity.go`, `lease.go`, and storage helpers also perform cleanup outside the initial request.

`internal/traceanalysis/processor.go:96` validates/indexes whole artifacts before publication. `service.go:63` delegates processing; query files load manifests/indexes/components and return domain errors. Many query validations already carry limit fields. Processor physical-line and JSON-depth categories are internal classifications rather than independent outward envelopes. `ProcessRequest.Context` already carries a context, but current log scope is taken from trace metadata instead.

## Contracts and Compatibility Inventory

| Category | Existing surface and evidence |
| --- | --- |
| Application API | Closed Java `ai.loomspan.api` allowlist in LoomspanPublicSurfaceArchitectureTest. Ticket has no Java API change. |
| Supported SPI | Repository policy declares none. Go interfaces, constructors and exported names in internal packages are internal seams. |
| Configuration and manifest contracts | Console documented flags/config and `internal/config/documentation_test.go`; no new flag is requested. Skill manifests/execution behavior are unaffected by server logging. |
| Persisted or serialized contracts | Browser/MCP error messages, status, DTO/envelope shape, and Java↔Go REST/SSE/problem boundary are explicitly preserved by ticket. Saved NDJSON exact-version validation remains unchanged. |
| Ephemeral diagnostic formats | Server log records are the ticket's changed diagnostic surface. Trace/analysis representations remain current-process/exact-version diagnostics. |
| Internal or accidentally exposed implementation | Failure wrappers, services, contexts, exported Go internal-package constructors/interfaces, logger wiring, retry state. No Java beans or autoconfiguration signatures are implicated. |

Protected consumers are the TypeScript Console browser, MCP clients, Go applicationclient consuming Java REST/SSE/problem responses, and artifact processor consuming NDJSON. Executable protection includes browser `contracts_test.go`, `errors_test.go`, activity/download/security tests; MCP `contracts_test.go`, `parity_test.go`, `response_budget_test.go`, `dual_output_budget_test.go`, security/runtime/trace tests; applicationclient get/activity/artifact/probe tests; target context/scope tests; live tests; artifact storage/acquisition/import tests; and traceanalysis fixture corpus tests with `loomspan-console-fixtures/`. No protocol or compatibility marker change is proposed in the ticket.

## Architecture Documentation and Version Alignment

The installed loomspan-docs router declares `0.1.0-SNAPSHOT`; exact installed-copy revision is not established. Repository-local `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `source-verification.md`, and `traces-and-debugging.md` are the matching-checkout documentation used for semantics. The trace guide describes exact-version transient evidence, protected client behavior, and treating returned data as potentially sensitive. Those facts align with the inspected import, processor, and MCP boundaries: **aligned** for the relevant comparison. Existing guide coverage concerns execution diagnosis, not a Go server logging schema.

Skill-authoring impact assessment: server diagnostic changes do not alter manifest syntax, execution, planning, trace content, query semantics, API behavior, or author debugging workflow. Console developer logging documentation belongs with `loomspan-console/README.md`; the matching authoring guide does not currently prescribe a server logging schema. No guide semantic change is established by this ticket.

## Historical Context

The ticket explicitly replaces proposed PR 20, and states the former PR 10 prerequisite already exists. The current working tree contains the user-deleted old PR 20 ticket and user-added PR 21 ticket. No matching pre-existing research or plan artifact was found. `ai/thoughts/framework-feature-design-lens.md` supplies the contract categories and current-run diagnostic/security intent.

## Verification Entry Points

Research was read-only except this artifact. `bash ai/scripts/spec_metadata.sh` succeeded. No tests were run for research. Console guidance lists `go test ./...`, `go run ./internal/buildtool verify`, and the race suite with MSYS2 gcc/CGO enabled. Java production type changes require LoomspanPublicSurfaceArchitectureTest; the ticket has none planned.

## Open Questions

No developer decision is required. Planning must select the internal structured schema and ownership/correlation/suppression design, including joined acquisition correlation, safe late-stream failures, expected-outcome severity, accurate read-versus-limit metadata, and bounded recovery handling. These are implementation choices expressly delegated by the ticket, not unresolved consumer semantics.
