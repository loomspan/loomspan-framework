# PR 20 — Structured Logging Coverage

## Status

Proposed ticket brief. Depends on PR 10.

## Outcome

Establish consistent structured logging across the loomspan-console Go backend
so that every silent failure path emits a diagnostic log line with sufficient
context to identify the scope, endpoint, and root cause without a debugger.

## Background

PR 10 introduced `slog.Error` calls at two critical boundaries in
`applicationclient/client.go` (`Get`) and `target/scope.go` (`Upstream`).
These calls capture the diagnostic context that was previously discarded when
mapping upstream failures to `consolecore.Error` values. However, the rest of
the console backend has no logging at all — no `log`, `slog`, or any logging
framework is used outside of those two files.

## In scope

- Configure the `slog` handler at application startup in `console/service.go`
  (JSON or text output, minimum level, output destination).
- Add `slog.Error` / `slog.Warn` calls at remaining silent failure paths:
  - `applicationclient/client.go` `Probe` method (transport errors, non-200
    responses, body read failures).
  - `target/context.go` `commitFailureLocked` (probe failures, authentication
    state transitions, connection state changes).
  - `browserapi/observability.go` handlers (decode failures, service errors
    before `writeDomainError`).
  - `browserapi/target.go` handlers (connect, credential, recheck failures).
  - `browserapi/router.go` (session validation failures, CSRF failures).
  - `observability/service.go` (JSON unmarshal failures, pagination clamp
    rejections).
- Add `slog.Info` calls for significant lifecycle events:
  - Target selection, connection, disconnection.
  - Session creation, expiration.
  - Pairing exchange.
- Add request-scoped attributes where practical (request ID, session ID) so
  log lines from a single browser interaction can be correlated.
- Document the logging conventions (required fields, log levels) in the
  developer guide.

## Guardrails

- Do not introduce a third-party logging framework. `log/slog` is the standard
  library structured logging package and is sufficient.
- Do not log credentials, API keys, session tokens, or pairing secrets.
- Do not log at `slog.Debug` for production-relevant diagnostics; use `slog.Info`
  or `slog.Warn`.
- Do not change the error messages returned to the browser. Logging is
  server-side only; the frontend error envelope remains unchanged.

## Acceptance signals

- Every `writeDomainError` and `writeError` call site has a corresponding
  `slog` call that logs the error code, scope ID (when available), and
  operation context.
- A user reporting `LIMIT_EXCEEDED` can be traced to the specific endpoint,
  maxBytes value, and scope from server logs alone.
- `go test ./...` passes with no regressions.
- Log output is structured (JSON or key=value text) and includes timestamps.

## Out of scope

- Frontend logging or telemetry.
- Log aggregation, shipping, or rotation configuration.
- Audit logging (separate concern, future PR).
- Metrics or tracing (separate concern, future PR).
