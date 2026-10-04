---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: migrated-readme-contract
---

# Observability HTTP diagnostics

Use when diagnosing a direct observability request or trace artifact transfer.
For ordinary AI investigation, use the version-matched Console skill and MCP tools.
These are same-version diagnostic contracts, not supported application Java APIs.
Enable the adapter with `loomspan.observability.enabled: true` and configure
`loomspan.observability.auth.api-key` before using these routes. It is servlet-only
and disabled by default. The target key is separate from the Console MCP key.

The API root is `/_loomspan/observability/v1/`. Every request must present
exactly one `X-loomspan-Api-Key` header. Authenticated responses use
`Cache-Control: no-store` and identify the current process with
`X-loomspan-Instance-Id`.

The current routes are `instance`, `skills`, `skills/{registeredName}`,
`active-executions`, `active-executions/{sessionId}`, `activity`, `traces`,
`traces/{traceId}`, and `traces/{traceId}/artifact` beneath the API root. The
artifact route accepts only GET with no query, range, or conditional headers
and an absent, wildcard, or NDJSON-compatible `Accept` header:

```bash
curl -H "X-loomspan-Api-Key: $LOOMSPAN_OBSERVABILITY_API_KEY" \
  -H "Accept: application/x-ndjson" \
  -OJ "http://localhost:8081/_loomspan/observability/v1/traces/$TRACE_ID/artifact"
```

Active execution list/detail responses expose `activeBranches`, never a single
truncated path. Each branch is one current open leaf with a complete ordered
root-to-leaf `path` and nullable `planId`, `taskId`, `stepNumber`,
`parallelGroup`, and `effectiveConcurrency` assignment facts inherited from the
nearest assigned frame. Shared prefixes are identical, leaves are ordered by
their frame-open sequence, and an empty array means no frame is currently open.
These live and finalized-trace shapes are same-version diagnostic contracts,
not application APIs.

A successful download is
`application/x-ndjson; charset=utf-8`, has the exact cataloged
`Content-Length`, and uses a standards-encoded attachment disposition whose
`filename`/`filename*` values represent
`loomspan-trace-<traceId>.ndjson`. Clients should use the decoded attachment
filename rather than comparing the serialized header text.
The process admits eight downloads independently from the 16 SSE subscriptions;
a ninth receives `429/LIMIT_EXCEEDED` without queuing. Transfers time out after
five minutes. A transfer admitted before expiration may finish, but new
requests for unknown, expired, deleted, or raced resources receive
`404/NOT_FOUND`.

The body is the exact finalized diagnostic file: Loomspan does not parse,
rewrite, normalize, redact, compress, or buffer it in full. Authenticated traces
may contain application business data and paths already recorded by canonical
diagnostics. Ordinary DTOs, lookup identifiers, response headers, and safe
download filenames never expose or derive from the internal artifact path.

## Verification anchors

The preserved contracts are exercised by `ObservabilityArtifactIntegrationTest`,
`ObservabilityRestIntegrationTest`, and `ObservabilityHostSecurityIntegrationTest`.
Use matching source when diagnosing an exact protocol edge case. Live branch
interpretation is documented in [traces-and-debugging.md](traces-and-debugging.md).
