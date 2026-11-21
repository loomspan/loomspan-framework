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
  additionalProperties: false
input_transform:
  language: jslt
  expression: |
    {
      "model": "jev-latest",
      "state": .ticket,
      "questions": {
        "repeat": {
          "type": "noul",
          "instructions": "Has this customer contacted support more than once?"
        }
      }
    }
```

Invoke `repeatContact` through `SkillTemplate` or expose it as an ordinary direct
child. It executes as `JAVA`, with YAML declaration provenance. Existing local
and qualified executor references follow the visibility, authorization,
configuration, publication and retirement rules for [executor leaves](executor-skills.md).

## Shared endpoint and namespace keys

Publish the executor owner as Public before publishing foreign leaves. Startup
configuration can supply both namespaces:

```yaml
loomspan:
  executor-config:
    default:
      typesafe:
        base_url: https://typesafe-gateway.example.com
    billing:
      typesafe:
        api_key: example-billing-key
```

In billing, declare `executor: default-typesafe` and optionally
`config: {max_attempts: 2}`. Two billing leaves share billing's key; a support
parent reaching either leaf still uses billing's key. The owner needs no key when
a consumer supplies one. Consumer-only publication rotates fresh calls while
retained handoffs keep both original maps. See the [publication example](../java-api/skill-reload.md#publish-consumer-executor-settings).
Pack `TypeSafeSettingsTest`, `TypeSafeTransportTest` and installed-JAR
`PublicationConsumerTest` cover destination, selected credentials, retry capture
and publication using local providers. Hosted quality and access remain untested.

## Native input, settings and results

The pack receives a complete native object with exactly `model`, `state` and
`questions`. `model` is a nonblank provider model ID. `state` takes text, an object
or an array. `questions` maps names to native Choice, Noul or Score definitions.
For direct native input, omit `input_transform` and send that object as the ordinary
skill input. For business input, use `input_transform` to produce the same native
object after Framework validates the leaf's ordinary input schema. Parent
`input_bindings` supply ordinary child input or select values from child results.

Leaf `config.max_attempts` accepts integers 1–3 including the first send; omission
defaults to 3. Null, noninteger, out-of-range and unknown leaf settings fail before
HTTP. Owner `executorConfig()` accepts `api_key` and `base_url`; consumer
`consumerExecutorConfig()` accepts only `api_key`. Select the consumer key by
presence, falling back to the owner key only when omitted. Present malformed
consumer keys fail without fallback. The selected key must be a nonblank string
of printable ASCII header characters and retains its exact text. An unselected
owner key need not be valid or present. Any consumer `base_url`, including null or
an owner-equal URL, fails at `consumerExecutorConfig.base_url`. Unknown consumer
fields fail at their path. Endpoint selection is owner-only.
Absent `base_url` defaults to `https://api.typesafe.ai`. A configured path prefix stays
in place when `/v1/systemone` is appended. The resolved key is used verbatim.
Alternate routes require protocol compatibility and appropriate account/model
access.

The pack validates and serializes native input once before sending and reuses
the same bytes across retries. See the owning executor reference
(`../../../../../loomspan-extensions/extensions/decisions/docs/executor.md` in the sibling checkout)
for question forms, JSON limits and supported local Resource inputs.

The executor returns complete provider JSON as text. With `output_transform` and
`output_schema` omitted, the leaf publishes that text with its envelope, unknown fields, refusals,
usage, arrays and scalars, JSON null and quoted strings. In that shape, a named
Noul answer is at `/answers/repeat/noul`. Use the
[executor output transform](executor-skills.md#publish-a-transformed-executor-result)
to publish a skill-owned shape. Parent bindings use the published shape and
`output_from` forwards the whole published text. Empty or malformed successful
HTTP content fails in the pack. A decoding-only failure causes no retry.
Diagnostic resource limits never truncate the business result.

## Framework boundaries and accepted limits

- Pack configuration and resolved requests are validated at every invocation
  before send. Successful Framework startup, validation or publication does not
  certify provider-domain configuration. There is no provider validation hook.
- A leaf MAY declare its own `output_schema` for terminal validation of the raw
  or transformed published object. Its declared structure enters ordinary
  forwarding metadata and binding checks. See
  [executor result validation](executor-skills.md#validate-the-published-executor-result).
  Framework supplies no provider-derived schema or answer repair. Structural
  validation does not establish factual completeness or confidence calibration.
- Native finite input and response numbers preserve their received values.
  Framework's JSLT transform accepts JSON values. See
  [executor input transforms](executor-skills.md#transform-executor-input) for
  the supported input and result types.
- Owner endpoint and consumer key settings belong to separate namespaces.
  Admitted work retains both captured maps across publication; fresh work selects
  the current leaf and owner generations.
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
Framework handoff is exercised by `ExecutorInputTransformIntegrationTest`.
Provider validation and captured setting rules are exercised by the pack's
`TypeSafeRequestTest` and `TypeSafeSettingsTest`. Installed-JAR evidence belongs to its
`TypeSafeConsumerTest` and `CompositionConsumerTest`, extended by
`PublicationConsumerTest`, `LifecycleConsumerTest`, `AccountingConsumerTest` and
`DiagnosticFaultConsumerTest` for captured concurrent settings, real lifecycle,
physical-return/resource retention, native-only accounting and pack diagnostic
faults. `ConsoleConsumerTest` can start a matching Console process,
imports those generated traces and verifies existing browser/MCP record queries,
pagination and content reads. Explicit test probes delegate to the installed pack;
they create no application customization API. Exact build identities, commands and
instrumentation boundaries are in the pack's `docs/tested-builds.md`.
Deterministic local tests establish integration, not live model quality,
calibration, latency, cost or access.
