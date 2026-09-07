# PR 21 — Make Console Failures Diagnosable

## Outcome

Make unexpected failures in the Loomspan Console Go backend diagnosable from
server logs without a debugger. Logs should identify the failed operation,
affected target scope, and safe diagnostic cause while keeping normal use quiet.

## Requirements

- Use standard-library `log/slog` with explicit startup configuration. Write
  timestamped structured records to stderr, using JSON or key=value text with a
  consistent field schema. Production-relevant diagnostics must be visible at
  the default level; Debug may hold optional detail. Do not introduce a
  third-party logging framework.
- Cover unexpected failures across browser and MCP operations, target probing
  and connection management, upstream requests and response decoding, live
  streams, artifact acquisition/storage/import/download, and trace analysis.
  Include failures in background work and failures after a streaming response
  has begun, not just failures that produce an HTTP error response.
- Give each failure a clear logging owner. Emit one primary actionable
  diagnostic for a failed operation rather than repeating the same failure at
  every layer. Additional records must convey distinct information and share
  correlation context.
- Include a stable operation name, error code or failure classification, and
  safe cause information. Include target scope when one exists, the upstream
  endpoint identity for upstream failures, and the applicable limit name and
  value for limit failures. Endpoint identity must not require logging a raw
  URL or query string.
- Correlate records for a browser or MCP interaction using a safe request or
  operation identifier. Retain relevant operation and scope context for
  background and streaming diagnostics. Authentication tokens must never serve
  as log correlation identifiers.
- Preserve accurate internal diagnostic causes before they are reduced to
  user-facing errors. In particular, distinguish a response-body read failure
  from an actual size-limit violation in logs. Do not change browser error
  messages, error envelopes, HTTP status behavior, or MCP response contracts
  as part of this work.
- Use severity to distinguish unexpected failures from expected outcomes.
  Ordinary caller cancellation, target rotation, stale cursors, and routine
  request validation must not produce Warning or Error logs. Log significant
  target selection, connection/disconnection, authentication-state, browser
  session, and successful pairing lifecycle transitions at Info. Log state
  transitions rather than unchanged polling results.
- Bound repeated failure logging during retries, reconnects, or repeated
  security rejections. Preserve the first actionable failure and meaningful
  changes or recovery without emitting an unbounded stream of identical
  diagnostics. Exact suppression mechanics belong to implementation planning.
- Never log credentials, API keys, cookies, session tokens, pairing secrets,
  authorization headers, or request/response and trace content. Apply this
  constraint to nested errors and existing logging too; use safe classifications
  and metadata when raw errors could disclose sensitive values.
- Document required fields, severity rules, logging ownership, correlation,
  output defaults, secret exclusion, and repeated-failure behavior in the
  developer documentation.

## Acceptance criteria

- [ ] Normal startup produces consistently structured, timestamped diagnostics
  on stderr through `slog`; actionable failures are visible with default logging
  settings.
- [ ] Unexpected failures across the included browser, MCP, upstream, target,
  streaming, artifact, and analysis paths have an actionable primary diagnostic
  identifying the operation, classification, safe cause, and available scope.
  Background and post-response streaming failures are covered.
- [ ] Related records can be correlated without authentication material, and a
  failure propagated through several layers does not create duplicate primary
  diagnostics.
- [ ] A reported limit failure can be attributed to its operation, applicable
  limit name/value, endpoint identity for upstream calls, and scope when present.
  A body read failure is distinguishable from an actual size-limit violation.
- [ ] Expected cancellations, target rotations, stale cursors, and routine
  validation do not generate warnings or errors. Significant lifecycle
  transitions are visible at Info without logging unchanged polling state.
- [ ] Repeated failures have bounded log volume while retaining the first
  actionable event and meaningful state changes or recovery.
- [ ] Focused automated verification demonstrates diagnostic fields,
  correlation, severity, duplicate/suppression behavior, and exclusion of secrets
  and content, including values embedded in errors.
- [ ] Existing browser and MCP response contracts and browser error messages
  remain unchanged. Developer documentation describes the resulting logging
  conventions and defaults.

## Context and scope

This replaces the proposed console PR 20 structured-logging ticket. Its original
PR 10 prerequisite is already represented in the current code; there is no
separate prerequisite PR to wait for. PR 21 is a proposed ticket identifier, not
an assertion that a GitHub PR exists.

The original ticket described logging in only two files. Logging now also
exists in live streaming, artifact handling, and trace analysis, but coverage
and context remain inconsistent. Some expected cancellations are logged as
errors. The upstream bounded-body reader also collapses reader errors and
size-limit violations into the same diagnostic. This work must improve the
quality of existing records as well as fill missing diagnostic coverage.

The deliberate goal is actionable failure and lifecycle logging, not a logging
call beside every `writeError` or `writeDomainError`. Preserve standard-library
logging and the current consumer-facing behavior. New logging configuration
flags or keys are not required. The JSON-versus-text choice and internal logger
wiring are implementation decisions.

Frontend logging/telemetry, audit logging, metrics, distributed tracing, log
aggregation/shipping, and rotation configuration are out of scope. No Java API
or console compatibility-version change is intended.
