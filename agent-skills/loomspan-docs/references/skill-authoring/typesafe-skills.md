---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: generic-executor-boundary-and-pack-reference
---

# TypeSafe through the Decisions pack

Use the independently versioned Loomspan Decisions pack for Choice, Noul and
Score judgments. Framework supplies ordinary executor execution, authorization,
captured configuration and observation. Applications own callable YAML skills
and policy; the pack owns provider requests, validation, transport and retries.

## Dependency and declaration

The unpublished local artifact is `ai.loomspan:loomspan-decisions:0.1.0-SNAPSHOT`.
Install it using the matching
pack README (`../../../../../loomspan-extensions/extensions/decisions/README.md` in the sibling checkout)
and add it as a normal Maven dependency. Its Boot auto-configuration registers
reference-only `default-typesafe`; installation creates no callable skills.
Pack internal/autoconfigure Java types are not supported application APIs.

Supply the selected executor map, even if empty, through YAML Config Data or
complete namespace publication. Applications must supply resolved values;
executor maps do not resolve `${...}`, `api-key-ref` or Framework connection aliases.
This is a local test configuration fragment:

```yaml
loomspan:
  executor-config:
    default:
      typesafe:
        api_key: local-test-key
        base_url: http://127.0.0.1:8089/proxy
```

Declare the callable leaf separately:

```yaml
name: repeatContact
description: Judge whether this is a repeat contact
executor: typesafe
input_schema:
  type: object
  properties:
    ticket:
      type: string
  required: [ticket]
config:
  request:
    model: jev-latest
    state: null
    questions:
      repeat:
        type: noul
        instructions: Has this customer contacted support more than once?
  bindings:
    /state: /ticket
```

Invoke `repeatContact` through `SkillTemplate` or expose it as an ordinary direct
child. It executes as `JAVA`, with YAML declaration provenance. Existing local
and qualified executor references, namespace visibility, authorization,
configuration ownership, publication and retirement rules apply unchanged;
see [executor leaves](executor-skills.md).

## Configuration and result contract

`config.request` contains the provider-native `model`, `state` and `questions`.
The model is a provider ID, not a Framework model alias. `config.bindings` maps
request-target RFC 6901 JSON Pointers to input-source pointers; an empty source
pointer selects the whole input. The pack preserves present null, removes object
targets for missing sources and rejects missing-source array omission. It builds,
validates and serializes once before sending and reuses the same retry bytes.
Read the owning executor reference (`../../../../../loomspan-extensions/extensions/decisions/docs/executor.md` in the sibling checkout)
for exact question forms, pointers, limits and resource handling.

For `api_key` and `base_url`, a present skill `config` value wins over the selected
executor namespace map. Present null, blank or non-string values fail rather than
falling back. Missing credentials fail; absent base URL defaults to
`https://api.typesafe.ai`. Valid selected credentials remain unchanged. A configured
base URL prefix remains when `/v1/systemone` is appended. Alternate routes require
protocol compatibility and appropriate account/model access; configuration alone
does not establish availability. Other settings do not inherit these override rules.

The result is complete provider JSON as text: envelope, unknown fields, refusals,
usage, arrays/scalars, JSON null and quoted strings remain intact. For a named
Noul answer use `/answers/repeat/noul` from the complete response root.
A parent may bind selected values or forward the entire text unchanged. Empty or
malformed successful HTTP content fails; a decoding-only failure causes no retry.
Diagnostic resource limits never truncate the business result.

## Framework boundaries and accepted limits

- Pack configuration and resolved requests are validated at every invocation
  before send. Successful Framework startup, validation or publication does not
  certify provider-domain configuration. There is no provider validation hook.
- Executor leaves publish no generated output schema, so catalog forwarding and
  early binding path/type checks cannot use provider-derived metadata. Generic
  runtime binding/destination checks remain. There is no executor `output_schema`,
  answer-schema, completeness or confidence enforcement, or answer repair.
- Ordinary executor `config` numeric decoding is unchanged. Precise declaration
  decimals may already have rounded before pack execution; the pack cannot
  recover digits and adds no precision mode, rounding or string conversion.
- Applications supply resolved credentials and endpoints in captured maps.
  Updating a skill map or executor map requires publication by its owning namespace;
  admitted work retains captured values, and new work receives the replacement.
- Pack requests/retries do not reserve Framework-native provider-attempt quota.
  Provider usage remains in results and captured diagnostics without contributing
  to Framework-accounted totals. Zero Framework-accounted usage does not mean
  measured zero executor usage; missing provider usage is not replaced with zero.
- Cancellation/deadline observations are live and read-only. The pack cooperates
  before sends, retries and waits and caps its total request timeout by remaining
  execution time. In-flight I/O can finish later. Immediate remote inference or
  billing cancellation is not guaranteed. Framework interruption, late-write
  fencing and generation/resource retention remain in force. See the
  pack transport reference (`../../../../../loomspan-extensions/extensions/decisions/docs/transport.md` in the sibling checkout)
  for exact retry and timeout settings.

## Evidence and inspection

Pack-authored request, response, failure and retry messages use existing bounded
executor diagnostics within the owning execution frame. Messages, developer
severity, supplied JSON and recorded capture states use the record's `data`
content. Framework capture notices have no developer severity. `error` severity
alone creates no automatic execution failure, retry or usage contribution.

Use existing `LOOMSPAN_query_trace_records` and `LOOMSPAN_read_trace_content` for
bounded queries, continuation, inline content and retained content references;
see [bounded executor diagnostics](traces-and-debugging.md#bounded-executor-diagnostics).
No extra stream/frame/tool or retrieval protocol is introduced. Absence of a record
establishes no capture-failure cause; notices cannot recover discarded evidence.
Canonical content remains unchanged; only the established derived journal/observer
projection has its field-name redaction exception.

Framework behavior is grounded in `ExecutorDeclarationTests`,
`ExecutorConfigurationTests`, `ExecutorDependencyResolutionTest`,
`ExecutorGenerationExecutionIntegrationTest`, `ExecutorInvocationLifecycleIntegrationTest`,
`ExecutorAccountingIntegrationTest`, `DeclaredChildInputBindingsIntegrationTest`,
`DeclaredOutputBindingsPlanningIntegrationTest` and `DesignatedChildResultIntegrationTest`.
Provider behavior and installed-JAR consumer evidence belong to the matching pack's
`TypeSafeConsumerTest` and `CompositionConsumerTest`, extended by
`PublicationConsumerTest`, `LifecycleConsumerTest`, `AccountingConsumerTest` and
`DiagnosticFaultConsumerTest` for captured concurrent settings, real lifecycle,
physical-return/resource retention, native-only accounting and pack diagnostic
faults. `ConsoleConsumerTest` starts the actual same-version Console process,
imports those generated traces and verifies existing browser/MCP record queries,
pagination and content reads. Explicit test probes delegate to the installed pack;
they create no application customization API. Exact build identities, commands and
instrumentation boundaries are in the pack's `docs/tested-builds.md`.
Deterministic local tests establish integration, not live model quality,
calibration, latency, cost or access.
