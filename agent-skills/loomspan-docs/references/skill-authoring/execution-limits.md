---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: focused-source-verified
---

# Execution limits and shutdown

Use when configuring execution budgets or diagnosing a timeout, quota failure,
or application shutdown. Connection-level transport budgets are separate; see
[model-selection-and-connections.md](model-selection-and-connections.md).

`loomspan.session` provides execution safeguards. Defaults are a 60-second mission timeout, maximum depth 32, 64 skill invocations, 128 tool invocations, 32 linter retries, 64 model calls, 192 physical provider attempts, and 200,000 usage units. Attachments default to a 20 MB maximum size.

```yaml
loomspan:
  shutdown:
    timeout: 30s
  session:
    mission-timeout: 60s
    max-depth: 32
    attachments:
      max-size: 20MB
    quotas:
      max-skill-invocations: 64
      max-tool-invocations: 128
      max-linter-retries: 32
      max-model-calls: 64
      max-provider-attempts: 192
      max-usage-units: 200000
  execution-trace:
    persistence: ONERROR # NEVER, ONERROR, or ALWAYS
```

## Application shutdown

`loomspan.shutdown.timeout` defaults to `30s` and must be positive. When the owning
Spring application context begins closing, Loomspan rejects new top-level
invocations. Already admitted roots may continue nested work within their existing
mission limits and the remaining shutdown budget.

The single budget includes admitted execution, trace finalization, public-view
mapping and observer delivery, cutoff, and framework executor cleanup. At cutoff,
framework work is interrupted and fenced from late framework writes. This is not
rollback of external side effects or a guarantee that uncooperative application
code has physically stopped. Unrelated application shutdown hooks and JVM exit
are outside this budget. For application dispatch gates and pending invocation
ownership, read [invocation](../java-api/invocation.md).

When Micrometer is on the classpath, Loomspan records usage metrics automatically.
Use traces and the `SkillTemplate` observer to inspect completed work; see
[observation and errors](../java-api/observation-and-errors.md).

## Verification anchors

Defaults are defined in `LoomspanProperties.Session`, `LoomspanProperties.Shutdown`,
and `ExecutionTraceProperties`. Shutdown ownership is covered by
`FrameworkShutdownIntegrationTest`. For the distinction between model calls and
physical attempts, use the [connection reference](model-selection-and-connections.md).
