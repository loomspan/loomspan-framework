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

[Automatic assigned dispatch](input-bindings.md#automatic-assigned-dispatch)
removes only eligible parent model calls and their physical provider attempts.
Actual planning, child reasoning, final synthesis and provider retries still
consume their configured budgets. Each accepted task still consumes its step
slot, tool invocation and applicable skill/depth budgets. Request-specific hooks
run only for actual model requests; independent authorization, lifecycle and
application approval checks still apply before side effects.

## Captured entry and current settings

One admitted root retains one session across namespace calls:

| Settings | Captured owner |
| --- | --- |
| Skill/tool invocations, model calls, provider attempts, linter retries, usage units | Entry generation; shared accumulated counters |
| Maximum depth, trace persistence and recorded configured quota limits | Entry generation |
| Definition, model, connection/credentials, mission timeout | Current executing target generation |
| Attachment maximum size | Current skill materializing that input |

Six orders model calls plus three pricing calls consume nine of orders' limit of
twenty. Pricing's session quota applies when pricing is the entry. A smaller child
quota neither resets nor reduces the entry budget. The trace limit snapshot contains
the six session quotas only. Publication after admission cannot change these settings.

Each mission timeout starts under existing mission execution behavior; host queue time
does not create a deadline. A parent timeout keeps running while a child starts its
own timeout. A 60-second parent that calls a 30-second child after 50 seconds still
expires on its original budget. Timeout return does not prove arbitrary external work
stopped or permit early resource retirement. Provider request timeout/retries retain
their connection behavior. Inputs accepted by a parent can fail child materialization
under a smaller attachment limit; limits are not combined into a session attachment budget.

Evidence: `DefaultSessionUsageService#quotas`, `LoomspanSessionRunner#executeAdmittedRoot`,
`MissionWorkExecutor#timeout`, `DefaultMissionInputMaterializer#maxSizeBytes`,
`SessionUsageServiceTest#namespaceChildrenShareEveryCounterAndUseOnlyCapturedEntryQuotas`,
and `NamespaceExecutionAuthorityIntegrationTest`.

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

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. Namespace updates support preparation
and installation, retained reachable admission and captured target dispatch.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`.
