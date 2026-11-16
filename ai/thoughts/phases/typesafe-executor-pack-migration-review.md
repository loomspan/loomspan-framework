# TypeSafe to Decisions pack: v1 migration review

Date: 2026-11-15.
Settled-decision consistency review: 2026-11-16.
Status: completed source review with agreed v1 dispositions; no implementation changes.

This review tests the [research design](decisions-executor-pack-research.md) and
[extraction roadmap](executor-packs-and-typesafe-extraction-roadmap.md) against the
current native TypeSafe implementation. It inspects local production code and test
sources; tests and live provider calls were not run. Provider availability and wire
contracts were not re-researched. Proposed Java names below are not released API.

## Breaking-change policy

The user authorizes destructive changes throughout this pre-release effort. Remove
native TypeSafe support and replace it with the provider-specific executor pack;
replace obsolete Java APIs, YAML/configuration, and Framework/Console/MCP contracts
without compatibility shims, legacy adapters, migration support, or historical readers.
Update affected callers, tests, fixtures, documentation, Console, and the Framework-to-
Console compatibility version together. Versioned invocation packages start with the
new v1 only; do not preserve the old unversioned handoff or build future overlap adapters.

Every derived ticket must include the verbatim policy block in the
[roadmap](executor-packs-and-typesafe-extraction-roadmap.md#mandatory-breaking-change-policy-for-this-work).
This replaces earlier compatibility/cutover reservations. Existing behavior comparisons
below explain what is being replaced, not a requirement to provide migration support.
Generic enhancement scope and existing executor/YAML namespace behavior remain unchanged.

## Assessment

The proposed v1 is sufficient in principle for a pack-owned TypeSafe HTTP executor.
No finding requires reinstating provider accounting or a provider SPI. It is not a
mechanical relocation: native configuration resolution, declaration validation,
output metadata, and numeric decoding currently depend on Framework internals.

The implementation prerequisites are the already-planned versioned handoff,
diagnostics, and cancellation/deadline accessors. The numeric-handling decision is now settled: keep existing executor configuration
behavior for v1 and document its difference from native TypeSafe. The `api_key` lookup rule and invocation-time pack validation are also settled
below. Reduced output metadata and runtime binding checks are accepted for v1; output
validation remains executor-owned.

## Scope rule for implementation and future tickets

`SkillExecutor` is a general execution mechanism. Do not expand its API, registration,
validation, configuration, or lifecycle rules to accommodate TypeSafe.ai specifics.
The loss of native TypeSafe startup/publication domain checks is an accepted tradeoff:
Framework validates its generic contracts; the pack validates its own configuration
and resolved requests at invocation, before any provider send.

Future tickets must preserve this boundary explicitly. Framework enhancements must
be individually called out in the roadmap, with a provider-independent purpose and
acceptance criteria. A TypeSafe migration finding is not authorization to add a
Framework feature or restore native parity. If a new generic capability is proposed,
record it as a separate roadmap decision before treating it as implementation scope.
Pack-specific work stays in the pack; unsupported native behavior is documented as
an explicit migration difference rather than hidden in a Framework special case.

## Current path and ownership

| Stage | Current source | Pack migration |
| --- | --- | --- |
| Declaration parsing, model binding | `YamlSkillCatalog.loadDefinition`, `validateDeclarationForm` | Ordinary executor YAML plus pack-owned configuration interpretation |
| Request validation and input substitution | `TypeSafeDeclaration.compile/resolve` | Pack-owned compiler/resolver; no imports of internal schema/compiler types |
| Connection and client construction | `NamedAiConnectionRegistry`, `ProviderConnectionRuntime`, `TypeSafeClient` | Pack Spring configuration and pack-owned client/resource management |
| Dispatch | `ExecutionCoordinator.executeBound`, `MissionWorkExecutor.executeEvaluation` | Existing executor path with direct v1 invocation construction |
| Attempts, responses, evidence | `TypeSafeExecutor.execute` | Pack request/retry/response logic and v1 diagnostic records within existing execution frames; no authoritative accounting |
| Retirement | Native generation connection ownership | Shared pack transport closes on application shutdown, not generation retirement |

Source anchors: [catalog](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java),
[declaration](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java),
[native executor](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java),
[client](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeClient.java),
[registry](../../../src/main/java/ai/loomspan/internal/autoconfigure/NamedAiConnectionRegistry.java).

## Findings and recommended disposition

### 1. Versioned handoff needs Framework implementation — known prerequisite

`SkillMethodBeanPostProcessor.registerExecutor` accepts exactly the current
unversioned `SkillExecutorInvocation` class. `SkillExecutorRegistration` stores a
function typed to it, and `ExecutionCoordinator` constructs its five-component
record. A method accepting the proposed v1 package type will currently fail registration.

Recognize the exact supported v1 parameter type and supply the v1 invocation, including
diagnostics and live cancellation/deadline accessors. Update public-surface policy and tests for
the versioned packages and replace the existing public record without compatibility support. Keep the
annotation-based registration and ordinary executor YAML path.

The Java parameter identifies the invocation contract. Pack identity, release version,
and Framework dependency belong in the POM; documentation/examples use standard
directories. No separate manifest or manifest validation is required for v1.
Extensions-owned consumer tests verify that the packaged TypeSafe executor registers
and runs; Framework registration changes have their own repository-local tests.

Sources: [registration](../../../src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java),
[registration record](../../../src/main/java/ai/loomspan/internal/core/SkillExecutorRegistration.java),
[handoff construction](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java).

### 2. Existing numeric handling retained — accepted v1 difference

Native TypeSafe uses the lossless skill-document number path. However,
`SkillDocumentValues.decode` separately reads executor `config` through
`YamlValues.read`, the ordinary numeric constructor. Namespace `executor-config`
uses the ordinary path too. `YamlSkillCatalog` then installs that raw map as skill
configuration. Consequently, moving precise literal criteria/state from `typesafe:`
to `config:` can convert decimals to floating point before pack execution. A pack
Jackson mapper cannot recover digits already lost.

Decision: keep existing executor configuration numeric handling for v1. Do not add
an exact-decimal configuration mode, special recipe syntax, or a new Framework
numeric contract. No concrete first-pack requirement justifies these additions.
Document that precise decimal literals moved from native `typesafe:` declarations
into executor configuration can lose precision. This is an accepted migration
difference, not an extraction blocker. Revisit only for a demonstrated use case.

This decision concerns configuration decoding, not permission to add rounding or
string conversion elsewhere. Preserve values as received by the pack and avoid
unnecessary conversion in provider request/response mapping.

Sources: [skill decoding](../../../src/main/java/ai/loomspan/internal/serialization/SkillDocumentValues.java),
[YAML constructors](../../../src/main/java/ai/loomspan/internal/serialization/YamlValues.java),
[catalog config binding](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java).
Evidence: `TypeSafeDeclarationTests.numericSubstitutionsAndRepeatedDetachedReadsPreserveArbitraryPrecision`
and `TypeSafeSkillIntegrationTest.nestedDecimalsRemainExactInRetryBodiesAnswersAndTraceEvidence`.

### 3. Credentials use captured configuration — agreed lookup rule

Native skills select `model: alias`, resolved through Framework model/connection
configuration. Executor leaves reject `model`; the invocation supplies configuration
maps, not a provider registry/client or resolved native credentials.

Executor maps deliberately preserve literal `${...}` and `api-key-ref` values.
`ExecutionConfigurationParser` resolves credential references in native connections,
not executor maps; supplied credential values used only by executor maps can be
rejected as unused. Moving native connection YAML under `executor-config` is not
an equivalent setup.

The Decisions pack uses this agreed v1 lookup, based on key presence:

1. If `skillConfig` contains `api_key`, use that value.
2. Otherwise, use `executorConfig.api_key` if present.
3. If neither contains the key, fail with a clear configuration error before sending.

An explicitly present null, blank, or non-string value is an error; do not fall back
from an invalid skill override. If both contain valid values, skill configuration
wins. Do not add an implicit environment/Framework-connection fallback or interpret
`api-key-ref`. Applications supply resolved values; this is not automatic placeholder
resolution. Preserve valid values without rewriting them.

Both configuration maps can change through prepare/publish in their respective
namespaces. Resolve from the invocation's captured maps, not live configuration.
Credentials therefore are not restricted to startup Spring properties. This requires
no new Framework credential API or internal registry access. Endpoint precedence is agreed below; model/request binding remains pack-owned
configuration design.

Decision: use a reusable pack-owned HTTP transport with URL, API key, and timeout
applied per request. Do not cache authenticated clients by generation or effective
settings. Keep credentials out of shared mutable client defaults; publication does
not recreate the transport. Close the transport at application shutdown.

Sources: [configuration parser](../../../src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java),
[executor configuration tests](../../../src/test/java/ai/loomspan/autoconfigure/ExecutorConfigurationStartupTests.java),
[native configuration guide](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

### 4. Registration-time domain validation disappears — explicit v1 tradeoff

Native compilation rejects malformed question declarations and input expressions
when a skill is loaded. Generic executor configuration only checks structural maps,
references, and selected configuration paths. It has no supported pack validation
hook during `SkillReloader.validate/prepare`.

Decision: validate pack configuration and resolved request data on every invocation,
before any provider send. Malformed pack declarations may therefore publish and fail
only when invoked. Framework registration still checks generic contracts; it does
not certify provider configuration. Use clear configuration-path errors, and do not
retry provider calls for local validation failures. Runnable examples and consumer
tests provide earlier development feedback. No registration validation SPI, separate
validation API, or compiled-configuration cache is required for v1.

This later failure timing is an accepted consequence of a generalized executor.
Do not add TypeSafe-specific Framework checks to restore native startup behavior.

The existing resolver is a source reference for whole-value JSON Pointer substitution,
scalar-only embedded substitution, escaped expressions, and null/omission behavior.
Exact pack request-binding syntax remains to be designed; no legacy expression
compatibility or shared provider grammar is required. Preserve provider-significant
score ordering. The TypeSafe executor builds, validates, and serializes its request
once before the first send, then reuses the same body bytes across retries. Resolve
endpoint and credentials once from the captured configuration. If supported inputs
require local resource reads, materialize that content during request construction,
not on each retry. A referenced remote URL remains a URL; remote content is not frozen.
Recheck cancellation, interruption, and remaining time on each attempt and cap its
timeout accordingly. Verify identical retry bodies and stable settings during publication.
This is an internal TypeSafe executor design, not a general SkillExecutor contract,
Framework snapshot facility, or requirement for other provider implementations.
It does not promise identical responses or prevent duplicate remote processing.
Domain validation errors need
not become `RecoverableSkillException`; that exception has specific correction semantics.

Sources: [declaration resolver](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java),
[dependency validation](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java).

### 5. Output validation stays executor-owned — accepted v1 tradeoff

Native `TypeSafeDeclaration` derives a descriptive output schema, exposed through
catalog inspection and parent forwarding. Executor leaves reject `output_schema`,
and `SkillDependencyValidator.resolveOutputSchema` excludes executor definitions.
A pack cannot publish equivalent generated metadata through the proposed invocation.

Decision: TypeSafe already returns JSON. Return its complete native JSON response,
preserving its content, including unknown fields and native refusals, without extracting
or reshaping it. Conversion from HTTP response bytes to the Java return value is an
implementation detail handled by the HTTP client or existing JSON tooling; do not add
a second parsing step if the client already supplies that value. No separate JSON
specification or new Framework capability is required.
Empty or malformed JSON fails execution; do not retry solely for
JSON decoding failure. HTTP/transport failures remain failures. Do not enforce provider
response schemas, answer completeness, confidence ranges, or other answer semantics.
Applications interpret the result; diagnostic limits never truncate it. Provider-specific
request validation remains. V1 adds no executor output-schema declaration, metadata hook,
or Framework enforcement. Native TypeSafe metadata was descriptive, not enforced
against provider answers; do not describe its removal as losing response validation.

Accept existing runtime binding checks when producer metadata is unavailable.
Framework loses some early path/type incompatibility detection and automatic output
schema visibility/forwarding, not the ability to return JSON or compose skills.
Do not promise that runtime checks prove an entire result conforms to a schema.
Document result shapes and paths, exercise parent bindings in consumer tests, and
use captured output and failure diagnostics for troubleshooting. Existing generic
checks remain; no TypeSafe-specific replacement is needed.

Sources: [output schema selection](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java),
[dependency validation](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java),
[binding checks](../../../src/main/java/ai/loomspan/internal/skill/BindingSchemaCompatibility.java),
[inspection test](../../../src/test/java/ai/loomspan/internal/observability/web/TypeSafeRegisteredInspectionIntegrationTest.java).

### 6. Complete native responses and configurable endpoint routing agreed

Native Framework success currently extracts the object-valued `answers` payload.
Decision: v1 contains the TypeSafe executor, returning its complete native JSON
response, including the envelope and additional fields. Gemini and OpenAI are planned
follow-up additions to the same extension; their unique requirements remain deferred. There
is no unified Decisions API, normalized result, or answers-only compatibility mode
in v1. HTTP/transport failures still fail execution; native refusal data in successful
responses remains native response content.

Migrate TypeSafe bindings from `/repeat/noul` to `/answers/repeat/noul`. Provider
request semantics stay provider-specific; share only useful infrastructure. This is
a deliberate result-contract change, not a Framework enhancement. Endpoint routing follows the explicit pack-owned lookup below.

Endpoint decision: use `base_url` from captured `skillConfig` first, then captured
`executorConfig`, then the provider default, checking key presence. Present null,
blank, non-string, or invalid values fail before sending rather than falling back.
Preserve path prefixes when appending the provider endpoint. TypeSafe retains the
prefix plus `/v1/systemone` behavior, including configuration of alternative routes
such as OpenRouter where protocol-compatible. Recheck route support in consumer
fixtures/live documentation before making support claims; no discovery or fallback.

Carry endpoint/header validation, retryable status handling, bounded error capture,
and retry limits into pack-owned code without depending on internal policy classes.
Success response handling remains separate from diagnostic capture limits: a
truncated diagnostic must not truncate the returned business answer. Each request
must use its captured effective endpoint and credential overrides.

Sources: [native execution](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java),
[HTTP client tests](../../../src/test/java/ai/loomspan/internal/typesafe/TypeSafeClientTest.java),
[integration tests](../../../src/test/java/ai/loomspan/integration/TypeSafeSkillIntegrationTest.java).

### 7. Cooperative cancellation and pack-owned total HTTP timeout — agreed approach

The current Apache client response/socket timeout is an inactivity bound. The test
`responseTimeoutIsInactivityWhileProgressingChunksMayExceedIt` demonstrates a response
lasting beyond the configured timeout while chunks keep arriving. Copying the client
and setting its timeout to `remainingTime()` would therefore not guarantee a total
request deadline. Native lifecycle cancellation currently provides the stronger abort.

Decision: Framework supplies live `isCancelled()` and remaining execution time,
conceptually `Optional<Duration> remainingTime()`. Empty means no deadline; expiry
returns zero, never negative. The pack checks these and interruption before requests
and retries, and cooperates during retry waits.

Keep HTTP timeout handling inside the pack. Its chosen client must enforce a total
request timeout capped by remaining execution time when present; configure inactivity
timeouts separately as needed. Verify with a slow progressing response. Do not add a
Framework HTTP API or physical-request cancellation callback.

Accept that cancellation during an in-flight request may not stop work immediately;
client timeout/interruption behavior governs physical completion. Framework retains
interruption and late-write fencing. Do not promise immediate remote inference or
billing cancellation. Keep work within the invocation until physically complete, or
explicitly manage any retained client resources: untracked background I/O can outlive
the generation retention protecting it.

Sources: [client](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeClient.java),
[timeout and cancellation tests](../../../src/test/java/ai/loomspan/internal/typesafe/TypeSafeClientTest.java).

Settled boundary: these are implementation requirements, not questions to reopen in
another audit. Observed cancellation, interruption, or exhausted execution time stops
further sends/retries; preserve interruption. Retain existing Framework cancellation
authority, failure handling, fencing, and resource ownership. Do not add a parallel
timeout scheduler, cancellation state machine, public exception hierarchy, physical
request callback, or stronger remote-cancellation promise.

For Framework implementation, inspect `MissionWorkExecutor`'s timed `Future.get(...)`,
`MissionLifecycle`'s cancellation-cleanup grace, and `FrameworkExecutionLifecycle`'s
shutdown timing. Remaining time exposes the applicable execution budget, not cleanup
grace or a budget reset at executor entry/retry. Add only the deadline bookkeeping
needed to expose existing enforcement, preserving applicable parent/shutdown constraints.
The pack must not import these internal owners.

Use the roadmap's [settled contract and mandatory ticket handoff](executor-packs-and-typesafe-extraction-roadmap.md#settled-cancellation-contract-and-mandatory-ticket-handoff).
Relevant tickets/subtickets must include its behavior, code anchors, exclusions,
and acceptance checks in their bodies, not just a link or broad "finalize semantics"
task. Verify live/no-deadline/expired reads, execution-budget versus cleanup-grace
behavior, no budget reset, cancellation/interruption before sends and during waits,
progressing-response total timeouts, and late completion/resource retention. Reuse
existing lifecycle tests plus the external consumer. Exact declarations, thread-safe
implementation, client choice, and defaults do not reopen the cooperative guarantee.
No new post-return restriction or monitoring guarantee is approved. Revisit only for
an explicit scope change or concrete contradictory implementation evidence.

### 8. Resource lifetime and namespace placement already have supported building blocks

Decision: leave the existing executor/YAML skill namespace relationship unchanged.
Multiple YAML skills can reference one executor, with existing namespace visibility,
configuration ownership, reference resolution, and publication rules. No new global
executor registration, automatic visibility, or extension-specific publication rule
is part of this work. Namespace placement is ordinary pack/application configuration,
not a migration roadblock or Framework enhancement. Examples should demonstrate the
existing rules without prescribing a new namespace relationship.

The current TypeSafe client constructs an Apache HTTP client and connection pool
per instance. Reuse that expensive resource at pack/application scope rather than
creating it for every invocation. The agreed v1 applies URL, authorization header,
and timeout per request with no authenticated-client cache. Verify the chosen
transport supports the pack-owned total timeout; inactivity timeout is insufficient.

Close the shared transport at application shutdown, not generation retirement.
Framework already retains captured generations for active work; request settings
remain independent across publication and concurrent overrides. Existing public
retirement notifications remain available for future generation-owned resources,
but are not required for this shared transport. No Framework enhancement is needed.

Sources: [executor binding](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java),
[public retirement API](../../../src/main/java/ai/loomspan/api/SkillReloader.java),
[resource ownership guide](../../../agent-skills/loomspan-docs/references/java-api/skill-reload.md),
[generation tests](../../../src/test/java/ai/loomspan/integration/ExecutorGenerationExecutionIntegrationTest.java).

## Accepted changes that are not roadblocks

- No native shared provider-attempt reservations for pack calls or retries.
- No authoritative token totals from pack usage diagnostics. Keep existing usage
  calculations and fields; Console labels totals "Framework-accounted usage", and
  API documentation/MCP usage descriptions explain that executor-managed calls are
  excluded. Zero Framework-accounted usage does not mean measured zero executor usage.
  Native usage remains in full responses and diagnostics when captured. No completeness
  indicator, diagnostic aggregation, or executor usage-reporting API is added in v1.
  Adapt native accounting tests to verify this scope, including a mixed mission.
- No Framework-to-HTTP cancellation registration in v1; cooperation is weaker.
- Native model-attempt trace events become generic boundary evidence and diagnostic
  records within existing execution frames, not a new frame for each diagnostic call.
  Console/MCP must not reinterpret diagnostic usage as authoritative accounting.
  Console reports recorded facts and never synthesizes results or infers execution
  outcomes from severity, message text, payload fields, or timing. Developers interpret
  request/retry messages alongside Framework-recorded outcomes; no typed provider-attempt
  tracking or provider-specific Console interpretation is required for v1.
- Executor-backed leaves use the existing `JAVA` classification. Remove the public
  `SkillKind.TYPESAFE` value under the agreed breaking-change policy.

## Minimal pack flow to prove

The following is conceptual sequencing for the TypeSafe executor, not compilable
or approved API and not a generalized executor retry rule:

```text
@SkillExecutor method accepts versioned v1 invocation
  read captured skillConfig / executorConfig
  compile and resolve request from invocation.input()
  validate pack-specific capabilities before any send
  resolve api_key and base_url from captured maps using agreed precedence
  serialize the TypeSafe request once; reuse its body bytes across retries
  use the shared pack-owned HTTP transport; apply settings per request
  for each bounded pack-owned attempt:
    check cancellation, interruption, remaining time
    emit request diagnostic (plain message + JSON data)
    send using the pack's timeout/deadline policy
    emit response evidence or a caught-failure/retry diagnostic as useful
    obtain the complete native JSON response; fail without retry if it cannot be decoded
    retry only when policy and remaining time permit
  return the complete native provider JSON response
Framework captures invocation timing, input/output, generation ID, and escaping failures
```

No step uses internal Framework services. JSON event capture remains best effort;
request/result serialization remains business logic whose failure may fail the call.
The failure-containment boundary is settled: the new `diagnostics.info/warn/error`
facility and any new diagnostic recording introduced by this work, including Framework
notices and their fallback logging, contain ordinary failures without changing the
executor result or replacing its original failure. Automatic start/stop, input/output,
and escaping-exception capture reuse existing Framework observation paths and retain
their existing execution/failure semantics. No retroactive guarantee or broader
observability rewrite is required. Caller argument evaluation before entering a
diagnostic method and JVM-wide failures are outside the guarantee.
Future audits and tickets must apply this
[settled boundary](executor-packs-and-typesafe-extraction-roadmap.md#settled-decision-diagnostic-failure-containment-boundary),
not present it as unresolved. Revisit only for an explicit scope change or concrete
implementation evidence of a violation; remaining signatures, limits, and concurrency
choices do not reopen it. Verify both successful results and original executor failures
survive ordinary failures in the new diagnostic paths.
Diagnostics use the fixed `info`, `warn`, and `error` methods with a message and
optional JSON, only for developer-authored calls from within a SkillExecutor. Framework
capture-failure and limit notices are separate system notices without these levels;
standard Java fallback logging remains separate. Diagnostic levels do not change
invocation outcome or trigger retries. Preserve existing automatic exception recording
and recoverable/terminal classification without mapping exceptions to diagnostic levels
or emitting duplicate diagnostics. Caught failures/retries may be reported by the executor.

## Implementation work and verification under settled decisions

Settled ownership: create `loomspan-extensions` and establish its code/test/example
structure before generating implementation tickets. Framework tickets complete using
their applicable repository-local tests, fixtures, public-surface checks, and Console
checks. They do not wait for downstream consumer tests. Extensions owns the TypeSafe
pack and a small working example exercised by its integration tests, using a local
stub HTTP provider and deterministic mocked model responses without real API keys,
provider accounts, or live services. Exercise actual pack request/response handling
and supported-API invocation; use test credential values for the stub. This example
is the external consumer proof relative to Framework, not a third repository requirement.

The shared completion checklist tracks overall delivery across Framework and Extensions,
not acceptance of each ticket. Genuine implementation dependencies are explicit (the
extension needs an identified Framework build), without making upstream tickets wait
for downstream validation. Broader `loomspan-sidecar-test-suite` integration tests are
future follow-up only: never a gate for tickets, phases, native removal, or overall
roadmap completion. Live model-quality evaluation is also outside implementation gates;
mocked tests establish integration behavior, and example thresholds are illustrative
application choices without production-quality claims.

Every ticket/subticket must carry repository ownership, local acceptance commands,
explicit dependencies, applicable mocked/no-real-credentials testing, and these gate
exclusions in its own body. A link alone is insufficient. Apply the roadmap's
[settled ownership and handoff](executor-packs-and-typesafe-extraction-roadmap.md#settled-test-ownership-and-mandatory-ticket-handoff)
when assigning checks below: external-consumer work belongs to Extensions tickets,
generic Framework verification to Framework tickets. Do not reopen this as a shared
cross-repository acceptance gate for otherwise locally complete Framework work.

Settled MCP integration boundary: diagnostic calls and Framework capture-failure/limit
notices are new record types inside existing frames. Reuse existing trace sequencing,
frame association, and `LOOMSPAN_query_trace_records` / `LOOMSPAN_read_trace_content`.
Those tools already provide type/frame selection, bounded pagination/continuation,
inline content, and content-reference reads. Update the coordinated closed record-type
vocabularies and affected schemas; Console otherwise rejects unknown record types.
Put message, developer severity, optional JSON, and explicit capture state in the
existing record `data` path, since arbitrary metadata is not automatically exposed
by the MCP record-summary DTO. System notices have no developer severity. Neither
developer `error` diagnostics nor system notices become execution-failure records.

Do not add diagnostic tools, endpoints, streams, indexes/stores, query services,
sequence/cursor formats, severity filters, notice filter bypasses, or retention rules.
Existing filters and access/content/retention behavior remain unchanged. No new
summary fields or broad inspection refactor is required. Follow the roadmap's
[settled reuse contract and source anchors](executor-packs-and-typesafe-extraction-roadmap.md#settled-decision-reuse-existing-mcp-record-inspection).
Every derived recording/Console/MCP ticket must carry that section's distinctions,
code anchors, permitted changes, exclusions, and acceptance checks in its own body;
a link alone is insufficient. Verify mixed records through existing tools, including
filtering, continuation, fidelity, bounded content reads, explicit capture-state
visibility, and unchanged execution outcomes. Reopen only for a concrete limitation
requiring a separately justified scope decision, not because the record types are new.

1. Document and verify existing executor numeric handling as the accepted v1 behavior;
   do not require native lossless configuration-decimal parity.
2. Implement and verify the agreed `api_key` precedence, invalid/missing values,
   publication updates, and client handling for distinct effective credentials.
3. Implement pack-owned request validation and faithful return of the complete native JSON response;
   verify existing runtime parent bindings without adding output schemas
   or early provider-specific Framework checks.
4. Apply the agreed full-native-response result migration and `base_url` precedence.
   Implement the agreed cooperative cancellation and pack-owned total request timeout;
   implement accessor declarations and thread-safe reads under the settled contract,
   without reopening policy or adding provider-specific Framework behavior.
5. Implement only the planned v1 public capabilities, then prove one external consumer
   with stub HTTP and no internal/autoconfigure API imports.

Reuse existing fixtures as behavioral evidence, adapting only intentionally changed
expectations. Cover the accepted configuration numeric conversion and preservation
of received request/result values, chosen binding syntax and null/order semantics,
direct and parent invocation, default and overridden route prefixes, malformed responses,
retry limits and identical retry bodies, slow progressing responses, cancellation,
reload during work, credential/config selection, cleanup, and diagnostic failures.
Do not add live evaluation or a full provider-accounting layer to this proof.

A documentation-only review cannot prove external-pack viability. The mocked consuming
example is Extensions-owned implementation evidence, not a gate for Framework tickets;
settled dispositions need not be
reapproved. Remaining concrete design choices are listed in the roadmap.

## Delivery tracking and remaining implementation details

Overall v1 delivery is implementation complete under the roadmap's
[shared checklist](executor-packs-and-typesafe-extraction-roadmap.md#v1-completion-gate-implementation-complete):
generic Framework capabilities/native removal, the TypeSafe executor, a consumer
application with deterministic fixtures against identified builds, Console/MCP inspection
and Console-free execution, one runnable recipe/current documentation, and required
public-surface/compatibility tests. Locally installed or staged Maven artifacts suffice.
TypeSafe is the complete v1 provider scope. Gemini and OpenAI are planned for this same
extension after the repository/structure, native removal, and completed TypeSafe example
are in place; their requirements and delivery gates will be designed separately.
Track publication and product releases separately;
no simultaneous release or cross-repository automation is required for overall delivery.
The consumer/example tests live in Extensions and use mocked provider responses without
real credentials. Sidecar-suite testing and live evaluation are excluded from all
roadmap gates; repository-local ticket acceptance follows the settled ownership above.

All eight reviewed implementation areas now have a v1 disposition. No additional
TypeSafe-specific Framework capability is justified by this review. The new executor
contract and consuming application still need implementation and verification.

Cutover policy is settled: remove native `typesafe:` declarations, driver/model
configuration support, obsolete public enum values, and the old unversioned handoff.
Reimplement TypeSafe as a pack executor with no shims, legacy modes, translators,
migration tooling, or historical readers. Coordinate Framework/Console/MCP contract
and compatibility-version changes; every ticket must carry the roadmap policy.

Exact provider request-binding syntax, retry defaults, diagnostics limits, accessor
signatures, and artifact coordinates remain implementation/design details. Do not
present these implementation choices as already selected or introduce an implicit
transition adapter. The mocked consuming example remains Extensions-owned evidence
for overall delivery, not a gate on native removal or Framework ticket completion.
No backward-compatible transition or coexistence release is required.
