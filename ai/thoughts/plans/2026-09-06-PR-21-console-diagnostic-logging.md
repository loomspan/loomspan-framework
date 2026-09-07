# Console Diagnostic Logging Implementation Plan

## Overview

Make Console backend failures actionable from default stderr logs while preserving browser/MCP responses and keeping routine use quiet. Implement standard-library slog JSON output, safe typed diagnostic metadata, explicit operation ownership, correlation across service lifetimes, and bounded repeated-failure reporting.

## Current State Analysis

The current seven logging sites neither share a schema nor cover all failure boundaries. `loomspan-console/internal/applicationclient/client.go:127`, `internal/target/scope.go:54`, and `internal/live/service.go:237` can report the same propagated failure. Reader errors are discarded at `internal/applicationclient/client.go:245`. Startup prints raw errors at `cmd/loomspan-console/main.go:33,56`. Analysis logs a trace identifier as scope at `internal/traceanalysis/processor.go:97`. Browser response writes and background cleanup have coverage gaps.

All shortened paths below are relative to `loomspan-console/`. Research and source were inspected on commit `8c464eaebc349c3398a2ca35a4bca680ead13a6c`. The user's deleted PR 20 ticket and replacement PR 21 ticket must remain untouched.

## Desired End State

Each unexpected failed operation has one primary default-visible record with stable operation, classification, safe cause, available scope, and relevant endpoint/limit metadata. Ordinary cancellation, target rotation, cursor/validation rejection, and unchanged polls generate no Warn/Error records. Lifecycle transitions produce Info records. Background retry, shared acquisition, and streams retain their own lifetime context. No log serializes arbitrary error text or client/upstream content.

### Key Discoveries

- `applicationclient.Failure` and `consolecore.Error` retain private causes; explicit adapter DTO construction permits enriching private causes without changing responses (`internal/applicationclient/errors.go:40,78`, `internal/consolecore/errors.go`).
- Request cancellation and scope cancellation already have separate semantics in target/acquisition. Keep those semantics, changing only diagnostics (`internal/artifact/service.go:215,242`).
- Retry timers start Background contexts (`internal/target/context.go:548`); live workers use service lifetime (`internal/live/service.go:195`); acquisition leaders are shared (`internal/artifact/service.go:151`). Request middleware alone cannot own these failures.
- Session registry and target commit methods own transitions; handlers must not infer transitions from repeated snapshots (`internal/browserauth/sessions.go:57,87,205`, `internal/target/context.go:324,435,480`).

## What We're NOT Doing

- Java API, SPI, REST/SSE/NDJSON/MCP schema or compatibility-version changes.
- New flags, configuration keys, dependencies, frontend telemetry, metrics, tracing, shipping, audit logs, or rotation.
- Changing outward body-read error messages/status/codes, even where the current mapping resembles a limit failure.
- Logging trace/session/cursor/content identifiers supplied by users or upstream, raw URLs, query strings, filesystem paths, credentials, cookies, headers, keys, pairing URLs, or nested error messages.

## Skill-Authoring Documentation Impact

**Impact: No impact.**

- **Rationale:** Only Console server operational diagnosis changes. Manifest, runtime, trace query/reader, and author debugging workflow semantics remain unchanged.
- **Documents to update:** No skill-authoring topic; add logging conventions to `loomspan-console/README.md`.
- **Supporting evidence:** Applicationclient/adapter explicit errors, existing traceanalysis corpus and MCP semantic tests; matching-checkout `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md` describes sensitive current-run evidence, not a server log schema.
- **Coverage table update:** Not required; topic coverage/confidence is unchanged.
- **LLM-first usability:** Not applicable to unchanged authoring guidance. Keep Console developer guidance self-contained with fields, owners, defaults and limitations.
- **Version/drift assessment:** Installed loomspan-docs declares 0.1.0-SNAPSHOT but its exact revision is unverified. Used its router and matching repository README/source-verification/traces-and-debugging topics for semantic comparison. Relevant evidence sensitivity and exact-version claims are **aligned**; no changed authoring claim or scope-affecting drift found.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | No Java API changes; root AGENTS and LoomspanPublicSurfaceArchitectureTest close the surface | Preserve; run architecture test if Java production types unexpectedly change |
| Supported SPI | None exists; Go internal exported seams do not create one | No added SPI |
| Configuration and manifest contracts | Flags, YAML and environment behavior unchanged; config documentation tests | Preserve keys/defaults; no logger flags |
| Persisted or serialized contracts | Ticket explicitly protects browser messages/status/envelopes and MCP results; Java-to-Go protocol untouched | Preserve byte/semantic contract fixtures; no migrations |
| Ephemeral diagnostic formats | Replace current stderr and slog records; no historical log reader contract | One current schema, safe defaults and precise causal metadata |
| Internal or accidentally exposed implementation | Errors, context metadata, operation ownership and retry/session bookkeeping | Change constructors/callers atomically where needed; remove obsolete logs |

- **Evidence of supported contracts:** Explicit PR 21 requirements, Java allowlist, browser/MCP DTO and fixture tests, exact-version reader behavior.
- **Intentional compatibility changes:** Existing ephemeral server log text/fields replaced. No consumer contract breaks.
- **In-repository consumers to update:** All existing Go log sites, affected internal callers and tests; Console README. No TypeScript/Java or corpus regeneration expected.
- **Public-surface delta:** No supported types/signatures or Spring extension points. New Go helper package stays under internal.
- **Shim decision: No shim.** Internal changes and ephemeral logs move atomically; protected responses stay unchanged.
- **Java-to-Go boundary coordination: Not required.** No REST/SSE/problem/acquisition/NDJSON representation changes; retain exact-release rejection and run existing Go contract/corpus suites. No compatibility marker bump.
- **Pipeline notes alignment: No notes.** Ticket expressly delegates JSON choice/logger wiring and suppression; this plan stays within that scope.

## Implementation Approach

Reuse existing error chains and authoritative lifecycle owners. Add a small `internal/diagnostics` package depending only on the standard library, used by the existing packages without import cycles. It owns structured output, immutable context metadata, safe diagnostic facts, primary-report claims, and repeat state. It must not become a second domain-error taxonomy: retain consolecore codes, applicationclient failure/transport categories and analysis invalidity categories, attaching a closed safe cause/stage where current errors lose information.

### Output and safe schema

Use `slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelInfo})` explicitly at process startup before asset loading, through a small writer-taking constructor for tests. Set the process default once; request operations obtain the logger through context (falling back to default), avoiding logger plumbing in every service constructor. Do not mutate the default in concurrent tests.

Required on diagnostics: standard `time`, `level`, static `msg`; `operation` (constant); `operation_id` (process-generated, noncredential). Failures additionally carry `classification` (domain code or closed internal failure kind) and `cause` (closed safe reason such as body_read, body_limit, response_decode, connection, timeout, storage_write). Use `request_id` for browser/MCP ingress, `parent_operation_id` for detached child work, `scope_id` only from trusted target scope, `endpoint` as a constant endpoint family (instance, skills.list, skills.get, executions.list/get, traces.list/get, artifact.download, activity.stream), `status` numeric when relevant, `limit_name`/`limit_value` for applicable enforced limits, and `stage` when distinguishing processing/storage suboperations. Lifecycle records include closed `state`/`previous_state`; repeat summaries include numeric `suppressed_count`.

Unknown errors map to `internal`/`unknown` plus a known operation/stage, never `err.Error()`, `%v`, reflection of arbitrary values, `slog.Any(err)`, or arbitrary LogValuer. Diagnostic adapters explicitly extract trusted enums/numbers using errors.As/Is; even exported string-backed enums must be allowlisted before emission. Do not emit `Failure.Observed` or arbitrary Details strings. Keep raw nested causes internally for errors.Is/As, never serialize them. Endpoint identity is assigned where the route is known, not by emitting/scrubbing a URL. All claimed limit failures include the actual limit constant/config value; routine validation limits may stay quiet or Debug.

IDs can use a process-local monotonic counter with a nonsecret process prefix; uniqueness within a process is sufficient. Do not reuse process/session/auth tokens, headers, trace IDs or artifact handles. Do not trust incoming request IDs. Imported operations omit scope rather than mislabel trace ID as scope.

### Ownership and correlation

Use typed context metadata and an internal diagnostic attachment on error chains containing safe facts and a concurrency-safe primary-report claim. An owner reports its result only if unclaimed, then retains the claim through domain wrapping. Claiming an expected/suppressed result also prevents an adapter from reclassifying it. Do not use one context-wide boolean: separate failures such as cleanup and response writes are distinct events. For shared acquisition, one terminal error/claim is shared by all waiters.

| Boundary | Primary failure owner | Lower-layer responsibility |
| --- | --- | --- |
| Browser synchronous operations | Named handler completion before reducing a domain error; response encoder/writer owns distinct encode/write failures | Attach safe causes and endpoint/limit facts, preserve domain envelope |
| MCP tool operations | One common tool wrapper or centralized completion path covering every registered tool, runtime and budgets | Context from SecurityHandler; avoid logging both wrapper and checkedDomainFailure |
| Target probe (manual or timer) | target probe/commit owner | applicationclient and Scope preserve annotated causes, never emit primary logs |
| Live open/read/protocol and baseline refresh | live worker owns each retry series | consume returns typed reason/metadata; no independent helper log |
| Joined artifact acquisition | leader terminal completion | waiter adapters do not repeat its failure; processing/storage attach causes |
| Import | synchronous import completion | processor supplies invalidity category without logging itself |
| Artifact cleanup/expiry/storage fatal outside request | artifact service at owning cleanup/fatal boundary | distinguish an additional cleanup failure from original install failure |
| Analysis query | requesting browser/MCP operation | traceanalysis returns safe storage/invalidity/limit facts |
| Browser SSE/raw download after headers | relay/download operation at final failure | preserve status/body contract, classify upstream read versus downstream write |
| Startup, profile/workspace monitors, shutdown | Console lifecycle boundary, final main reports only unclaimed terminal failures | preserve monitor stage and suppress duplicate returned cause |

Browser ingress creates request and operation context before security evaluation; static route dispatch selects a stable operation (unknown route uses a constant). MCP HTTP ingress creates request context, and each tool creates its own child operation with a constant tool name, so multiple tool calls cannot share one claim. SecurityHandler rejections use its own bounded classified reporting path.

Detached probe/live/cleanup work copies only diagnostic values onto its existing lifetime context; it must not inherit requester cancellation/deadline or security admission credentials. Each retry series retains operation ID/scope until recovery or scope retirement. Shared acquisition gets an independent leader operation ID plus initiating request/parent ID. On failure, each waiter emits at most an Info `operation.link` containing its request/operation ID and the shared operation ID, with no repeated cause; these distinct records make joined browser/MCP failures traceable at default level. Successful joins remain quiet. Cancellation of one waiter remains quiet and cannot cancel surviving waiters.

### Severity and repeated failures

Error: unexpected transport/protocol/decoding, invariant/local storage, internal encoding, unexpected stream and shutdown failures. Warn: nonfatal unusable upstream artifact/content or significant upstream incompatibility. Info: actual selection, connected/disconnected, authentication-state, pairing success, session/tab create/expire/release/close, and meaningful recovery. Invalid user import is an expected rejection: safe reason may be Debug without warning. Routine invalid requests, auth rejections, stale cursors, unavailable/expired resources, target rotation and ordinary caller cancellation never Warn/Error. A configured operation timeout when the caller is still active is actionable; caller deadline/cancellation is expected. Classify using the owning contexts before domain mapping, not code alone.

For probe/reconnect/baseline retry series keep bounded per-owner state keyed by closed operation+cause+endpoint+limit facts within the current scope. First failure emits immediately, identical repetitions increment a counter without emission; changed failure emits immediately with prior suppressed count; recovery emits one Info summary and clears state. No periodic identical-failure heartbeat is required. Scope retirement/shutdown clears state and may include its suppressed count on the lifecycle record without claiming recovery. Fixed operation domains and one current scope keep memory bounded; never key on request IDs, raw errors or caller data.

Repeated security rejections are Debug-only and throttled independently by fixed reason+adapter buckets: first occurrence and at most one summary per minute per bucket with count. At default Info they remain quiet. This fixed set prevents untrusted cardinality growth. Independent user requests are separate failed operations and are not globally suppressed, except retries/polling owned by the above series. Lifecycle emission compares state under the existing owner lock and emits only when it changes; no polling heartbeat records.

## Phase 1: Safe diagnostics and preserved causes

### Changes Required

1. Add `internal/diagnostics` output/context/annotation/repeat helpers and focused tests. Use immutable metadata, atomic primary claims and owner-local synchronized repeat state, without global maps or user-controlled attributes.
2. In `internal/applicationclient/client.go`, `errors.go`, `artifact.go`, `activity.go`, preserve endpoint, protocol stage and body reader cause before mapping. Split readBounded's reader error from actual overflow; errors.Is must reach original reader cause. Keep GET's existing outward FailureLimitExceeded for both cases, but diagnostic cause is body_read versus body_limit. Probe/problem-body mappings likewise remain unchanged. Cover bounded successful, problem and probe bodies and stream header/JSON decoding.
3. Enrich error causes in `internal/observability`, `traceinventory`, `traceresolution`, `artifact`, and `traceanalysis` at lossy conversion boundaries as needed. Use actual limit constants from producers, including response budgets, parser line/depth, artifact capacity and request budgets; no invented limits or trace content fields.

### Success Criteria

- [x] Focused diagnostics/applicationclient tests pass; pre-fix body reader reproduction fails for the intended reason.
- [x] Existing outward error code/message/details/status fixtures remain identical.
- [x] Safe unknown/nested error tests demonstrate no raw text reaches any log field.

## Phase 2: Request and process owners

### Changes Required

1. Configure startup in `cmd/loomspan-console/main.go`; replace raw fatal stderr with safe classified structured records. Keep --version stdout and interactive pairing/prompt output functioning. Preserve safe startup stage in wrappers for argument/config/assets/profile/workspace/listener failures. Never duplicate pairing output into diagnostics.
2. Add named browser context and result reporting in `internal/browserapi/router.go` and operation handlers, including target, artifact import/download, analysis and MCP-management calls. Update internal response helpers and callers atomically to expose encode/write errors with context. Avoid logging every writeError and avoid inferring errors from HTTP status alone.
3. Cover every tool in `internal/mcpadapter` through one completion path, including runtime, auth-generation handling, response budgets and failures returned as SDK errors. Wire security rejection throttling without changing admission, JSON-RPC or response contracts.
4. In `internal/console/service.go` and relevant lifecycle callbacks preserve operation/stage for startup, background profile/workspace monitor fatal and healthy shutdown cleanup. Log significant MCP authentication state transitions at their existing lifecycle owner. Returned fatal causes share their primary claim with main.

### Success Criteria

- [x] Startup tests parse JSON with timestamp/default-visible failures and no secret-bearing arguments/errors.
- [x] Browser/MCP representative unexpected failures have one primary, request+operation ID, safe facts and unchanged responses.
- [x] Routine security/validation/cancellation cases produce no Warn/Error, even when mapped to an outward availability error.

## Phase 3: Background, stream, artifact and lifecycle completeness

### Changes Required

1. Update `internal/target/context.go` and `scope.go`: authoritative transitions, annotated errors, probe retry state and detached metadata; remove previous unconditional scope logs.
2. Update `internal/live/service.go`: series contexts, open/read/decode/handshake/cursor/size failures and baseline refresh ownership; remove raw errors/cursors. Preserve stale-cursor recovery and target fencing. Connection state records come from publishConnectionFor only on change.
3. Update `internal/artifact/service.go`, `acquire.go`, `import.go`, `expiry.go`, `capacity.go`, `lease.go` and cleanup helpers: leader identity/claim, waiter links, import ownership, storage-stage facts, background cleanup context and distinct secondary cleanup failure. Keep lifetime/cancellation/accounting intact.
4. Remove processor's independent Warn and trace-as-scope log in `internal/traceanalysis/processor.go`; carry invalidity category to owners. Do not alter existing outward scope/message fields while correcting log scope. Analysis queries report failures via adapters, including corrupt/missing bundle components and budget failures.
5. Update `internal/browserapi/activity.go` and `artifact_download.go` for post-header read/write/encode failures, expected disconnect handling and shared request context. No error-envelope writes after headers.
6. In `internal/browserauth/sessions.go`, pairing/exchange and MCP lifecycle, emit authoritative Info transitions with independent diagnostic identifiers where correlation is useful. Never use session/tab/CSRF/pairing/MCP credentials. Heartbeat, Authenticate and repeated bootstrap emit only real transitions, including actual expiry once.

### Success Criteria

- [x] Deterministic retry tests prove first/change/recovery behavior and bounded storage/log volume.
- [x] Joined acquisition race tests show one primary across browser/MCP waiters with links and preserved cancellation semantics.
- [x] Background and post-header injected failures carry operation, scope where available, safe cause and limits/endpoint where relevant.
- [x] Existing live/artifact/analysis/browserauth suites pass without semantics changes.

## Phase 4: Documentation and complete verification

### Changes Required

Document the schema, level defaults, owners, correlation and shared-operation links, expected outcomes, suppression/recovery, secret/content exclusion and stderr-versus-interactive-output boundary in `loomspan-console/README.md`. Include minimal synthetic JSON examples with no live values. Audit every production slog/raw stderr site and remaining discarded errors within ticket scope; each failure must have an owner or an explicit expected-outcome reason. Remove superseded diagnostic code rather than retaining both paths.

### Success Criteria

- [x] Dedicated testing plan matrix complete; Go suite and canonical verify pass.
- [x] Race suite passes with Windows gcc/CGO prerequisites.
- [x] No unexplained raw error/content/URL/identifier logging remains in Console production code.
- [x] Documentation matches tested schema/defaults; no authoring guide/compatibility changes.

## Testing Strategy

See `2026-09-06-PR-21-console-diagnostic-logging-testing.md` for concrete red tests, failure-injection matrix and commands. Test actual owner paths through typed errors and response fixtures, not only helper output. Capture structured records with a synchronized test handler; test concurrency and real response-writer errors separately from ordinary cancellation. No live credentials or real target required.

## Performance Considerations

Normal successful requests do not log. IDs and immutable metadata add small per-operation allocation; avoid trace payload formatting entirely. Repeat state is bounded to current service owners and fixed security reasons. Do not hold shared state locks across unnecessarily expensive diagnostic construction; snapshot immutable transition fields at commit, maintaining ordering as appropriate to the owner.

## Migration Notes

No data/config migration, shim or compatibility marker change. Consumers see unchanged responses. Operators receive the new stderr JSON records immediately on startup. Preserve the user-managed ticket replacement.

## Planning Checklist

- [x] Read requirements, research, source, protocol and compatibility lens.
- [x] Verify existing owners, async context losses and test seams.
- [x] Choose safe schema, correlation, severity, suppression and no-shim design.
- [x] Assess matching-checkout authoring guidance and drift.
- [x] Write implementation and dedicated testing plans; no source implementation performed.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-21-console-diagnostic-logging.md`.
- Research: `ai/thoughts/research/2026-09-06-PR-21-console-diagnostic-logging.md`.
- Root `AGENTS.md`, `loomspan-console/AGENTS.md`, and `ai/thoughts/framework-feature-design-lens.md`.
- Existing safe-storage logging test: `loomspan-console/internal/artifact/storage_test.go:468`.

## Implementation notes (2026-09-06)

- Phase 1 reader regression was executed before production edits and failed because `readBounded` discarded the sentinel. It passes with the original reader cause retained; existing GET outward `FailureLimitExceeded` message/details remain unchanged for both reader errors and actual overflow.
- Keep concrete internal `*applicationclient.Failure` return values while attaching diagnostic metadata to private causes before publication. Existing direct type assertions remain valid without overloads, aliases or compatibility paths. Domain constructors initialize shared pointer claims so intentional envelope copies retain primary ownership.
- Browser response writers carry the immutable operation context through response helpers and retain Flusher capability; each distinct encode/write failure owns a separate primary. MCP tools use the common validated tool wrapper and domain completion; expected generation changes stay quiet.
- An imported resolver result can carry a target Scope solely for publication fencing. MCP diagnostic scope comes from its evidence Reference, so imported evidence never inherits that incidental target scope.
- Inventory and browser enrichment own failures they reduce to incomplete/successful results. These lossy boundaries now report before reducing the error, with request correlation retained through the enrichment helper's internal context parameter.
- `webhost` routes net/http emergency logging through a content-discarding standard log adapter, preventing raw panic/error text from bypassing the safe diagnostic boundary. The build-tool CLI's stderr messages are build diagnostics outside the Console server runtime.
- Matching-checkout authoring semantics remain aligned: the diff changes neither trace contents nor queries, evidence lifetime, manifest/configuration behavior, Java API/SPI or browser/MCP serialized contracts. Only the Console README needs documentation changes. No fixture regeneration or compatibility marker change is required.
- No user-managed ticket contents or deletion state were changed.

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
