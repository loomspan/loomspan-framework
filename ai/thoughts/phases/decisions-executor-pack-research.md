# Decisions Executor Pack: provider research and distribution design

Status: dated provider research and agreed v1 design; not an implemented API.
Research date: 2026-11-15.
Settled-decision consistency review: 2026-11-16; provider research was not refreshed.

The five-issue follow-up is resolved. Read the roadmap's
[current handoff](executor-packs-and-typesafe-extraction-roadmap.md#current-handoff-after-the-five-issue-follow-up-review)
for the user-confirmed clarifications, existing-behavior test evidence, and next steps.

This document supports the [extraction roadmap](executor-packs-and-typesafe-extraction-roadmap.md).
The target is a separate project published as a Maven dependency. Framework owns
generic observable execution; the pack owns decision-provider integration.

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

## Source review findings

The [TypeSafe migration review](typesafe-executor-pack-migration-review.md) maps the
current implementation to v1. Keep existing executor configuration numeric handling
for v1: loss of native exact-decimal configuration preservation is an accepted,
documented migration difference, not a blocker. No precision mode or special numeric
syntax is required. Credential lookup now uses `skillConfig.api_key` first, then
`executorConfig.api_key`, with an error if missing or invalid. Later domain-validation
timing is accepted as pack-owned invocation validation. Response handling stays
pack-owned; existing Framework runtime binding checks remain. The TypeSafe executor
does not enforce provider response schemas or answer semantics. Reduced output
metadata is accepted for v1. The review also distinguishes HTTP
inactivity timeout from total deadlines; it does not automatically approve other
behavior reductions.

## Evidence and limitations

The TypeSafe baseline below comes from its live documentation reviewed during
the design discussion and the current repository implementation. No live provider
accuracy, latency, cost, or account-availability comparison was run. Recheck source
documentation and capture representative wire fixtures before implementation.
Documentation examples do not prove identical calibration or behavior between models.

## Discussion conclusions to preserve

The initial discussion considered a provider-neutral decisions feature, first in
core and then in a pack. The agreed v1 supersedes that approach: one optional
`loomspan-decisions` artifact contains the TypeSafe SkillExecutor in v1, using its
provider request semantics and returning the complete native JSON response.
Gemini and OpenAI are planned follow-up additions to this same extension. Their unique
requirements will be reviewed after the repository/structure, native TypeSafe removal,
and one solid completed TypeSafe example are in place. Only their future inclusion
is decided; no request/response contracts, capabilities, or delivery requirements
are specified for them here. There is no unified Decisions request/result API
or legacy answers-only mode in v1. Do not add native decisions syntax to Framework.

Two findings materially changed the discussion:

- TypeSafe's text-only limitation applies to our current integration, not its
  entire current service: its image support is experimental. Model and configured
  capabilities matter more than a permanent provider-wide text-only flag.
- Preserve TypeSafe's native confidence meanings. Example thresholds are illustrative;
  live application evaluation is needed for production-quality recommendations, not
  for completing the example or implementation tickets.

The initial extraction concern was loss of native execution visibility and
accounting. The agreed v1 restores visibility through supported diagnostics but
deliberately defers provider-attempt admission and authoritative usage accounting.
Cancellation is cooperative through simple status/deadline accessors; physical
request cancellation callbacks are deferred. Packs must not access internal
session/trace services to recreate the omitted facilities.

The catalog proposal complements Maven distribution; it does not replace Maven
dependency resolution. Pack installation makes reference-only executors available.
Application-owned YAML explicitly exposes callable skills. Runtime installation
of executable JARs, custom UI plugins, and an automatic fallback between decision
providers were not selected for the initial design.

The agreed starting direction is a separate `loomspan-extensions` repository with
independently released packs, beginning with `loomspan-decisions`. The directory
convention below is a starting point, not a frozen specification. Exact Maven
coordinates, public signatures, media scope, and support policy
remain to be settled. No repository or artifact has been created by this work;
do not present proposed coordinates as an available dependency.

## Agreed v1 scope and extension contract

Keep v1 small enough to run, learn from feedback, and prune. Prove one dependency,
one consuming application, one runnable recipe, useful Console diagnostics, and
a small documentation index before building a broader catalog platform. TypeSafe
is the complete v1 provider scope. Gemini and OpenAI are future additions to the
same extension, not v1 deliverables or reasons to build speculative abstractions.

Use one Loomspan extension contract version covering the executor handoff,
operation/evidence reporting, and diagnostic presentation interpretation. Do not
introduce independently selected execution and presentation schema versions.
The pack still has its own ordinary release version. Descriptive documentation
changes do not require a contract version change unless they alter interpretation
or execution behavior.

Framework recognizes the exact new v1 invocation contract. Versioned packages allow
future evolution, but supporting multiple versions or the old unversioned handoff is
outside this effort. A version field alone is insufficient; implement the new Java
contract directly. Optional future display hints may have generic fallback; required
unsupported behavior must be rejected. No general SPI or bean-replacement surface
is introduced.

Console is an optional development, debugging, and troubleshooting tool, not a
runtime tool or dependency. Packs must run fully without it installed or connected.
Framework owns execution and diagnostic recording. Console inspects those records;
presentation never controls execution, budgets, cancellation, or result semantics.

## Existing namespace relationship retained

Leave the existing executor/YAML skill namespace relationship unchanged. One executor
can serve multiple YAML skills using existing namespace visibility, configuration
ownership, reference resolution, and publication rules. No global registration,
automatic visibility, or extension-specific publication behavior is added. Namespace
placement is an ordinary configuration choice, not a migration roadblock. Extension
examples use the `default` namespace and focus on installation, configuration, and
invocation. Namespace concepts and visibility rules belong in `loomspan-framework`
documentation; do not duplicate that guidance in `loomspan-decisions` or other
`loomspan-extensions` subprojects. No new namespace relationship is introduced.

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

The companion roadmap is the explicit enhancement list. Its current additions are
versioned invocation handoffs, generic execution diagnostics/Console/MCP inspection,
and simple cancellation/deadline accessors. Other findings do not expand that list.

## Versioned invocation and minimal runtime contract

Use versioned Java invocation packages. The proposed spelling is
`ai.loomspan.api.extensions.v1.SkillExecutorInvocation`; the versioned-package
approach is agreed, while exact public names/signatures remain design work. Keep
contract-specific capability types, including diagnostics, with that version.
Framework recognizes the exact supported parameter type during executor registration
and supplies the matching handoff. Reject unsupported parameter types before
execution; a package name alone does not make an arbitrary class supported. No
separate version annotation or per-call version branching is required. The Java
parameter selects the invocation contract; no separate manifest declaration or
manifest validation is required.

The v1 handoff contains the existing input, skill configuration, executor
configuration, skill name, and executor configuration generation ID, plus diagnostics
and simple read-only cancellation/deadline accessors. `isCancelled()` and
`remainingTime()` are illustrative names. Input/configuration remain captured values;
cancellation and remaining time reflect the live execution. Prefer a Framework-owned
class with stable accessors over an expanding public record. Finalize Java declarations
as implementation work under the settled cancellation contract below; do not treat
unfinished declarations as an open lifecycle-policy decision.
Do not expose an internal lifecycle/state object or mutable cancellation controls.

The agreed cancellation/deadline contract is live read-only cancellation status
and remaining execution time. Conceptual signatures are `boolean isCancelled()`
and `Optional<Duration> remainingTime()`; final public declarations remain to be
implemented. Empty remaining time means no deadline; an expired deadline returns
zero, never a negative duration. These accessors do not abort external I/O.

The pack checks cancellation, interruption, and remaining time before requests and
retries, and cooperates during retry waits. It owns a total HTTP request timeout,
capped by remaining execution time when present, plus any client-specific inactivity
timeouts. An inactivity timeout alone is not a total deadline. Choose and verify
HTTP-client behavior inside the pack; Framework gains no HTTP-specific API.

Existing Framework interruption and late-write fencing remain. Cancellation during
an in-flight request may not stop physical work immediately: it remains subject to
client timeout/interruption behavior. V1 adds no physical-request cancellation
registration API and promises neither immediate remote inference cancellation nor
billing cancellation. Do not expose internal mutable lifecycle state.

This cooperative behavior is settled. Cancellation, interruption, or exhausted
execution time prevents another send/retry; preserve interruption rather than
continuing retries. Reuse existing Framework cancellation authority, failure handling,
late-write fencing, and resource retention. No new cancellation state machine,
Framework timeout scheduler, public exception hierarchy, or physical-I/O callback
is required. Thread-safe reads, exact declarations, client choice, and timeout/retry
defaults are implementation tasks within this boundary.

Source guidance: `MissionWorkExecutor` enforces mission timeout using timed
`Future.get(...)`; the deadline in `MissionLifecycle` also serves cancellation-cleanup
grace, and `FrameworkExecutionLifecycle` owns shutdown timing. Expose the applicable
execution budget, not cleanup grace or a new budget beginning at executor entry.
Use minimal bookkeeping alongside existing enforcement and preserve applicable
parent/shutdown constraints. These internal owners must not be imported by the pack.

Follow the roadmap's [settled cancellation contract and ticket handoff](executor-packs-and-typesafe-extraction-roadmap.md#settled-cancellation-contract-and-mandatory-ticket-handoff).
Every relevant ticket/subticket must carry its concrete behavior, code anchors,
exclusions, and applicable acceptance checks in its body; a link or "finalize semantics" is not
enough. Verify live/no-deadline/expired reads, no budget reset, cancellation/interruption
before sends and during waits, capped total timeouts with progressing responses,
and late completion/resource retention using existing tests plus the external consumer.
No post-return restriction or monitoring guarantee is newly approved. Future audits
must not reopen settled behavior because implementation tasks remain unchecked;
require an explicit scope change or concrete contradictory implementation evidence.

Current repository policy only supports allowlisted top-level types directly in
`ai.loomspan.api`. Implementing versioned subpackages therefore requires an explicit
policy/architecture-allowlist update, README documentation, and supported-surface
tests. This planning decision does not itself change the current allowlist. Implement
the direct replacement of the existing unversioned invocation record and all call
sites, without compatibility constructors or adapters. Implement only the new v1.
No new provider SPI or bean-replacement contract is introduced.

## Explicit TypeSafe behavior changes in v1

These are accepted scope reductions for the extracted pack, not accidental gaps or
remaining requirements for native parity. They take effect with extraction, not by
silently changing the existing native implementation during this documentation work.

| Behavior | Current native TypeSafe | Extracted Decisions pack v1 |
| --- | --- | --- |
| Output metadata | Generated descriptive schema, not enforced against provider answers | No replacement schema hook or provider response-schema validation; return the complete native response and retain existing runtime binding checks |
| Configuration decimals | Lossless native declaration decoding | Existing executor YAML numeric handling; high-precision decimals may round before pack execution |
| Result shape | Extracted `answers` object | Complete native JSON response; `/repeat/noul` becomes `/answers/repeat/noul` |
| Provider-attempt budget | Reserves each physical attempt against the mission-wide provider budget | Pack requests/retries do not consume that native shared provider-attempt budget |
| Authoritative usage | Reports response usage into Framework accounting | Usage may be diagnostic data; it does not automatically enter authoritative totals |
| Physical HTTP cancellation | Framework lifecycle is connected to request cancellation | Status/deadline polling, existing interruption, and pack-owned client timeouts; no equivalent direct cancellation hook |
| Retry policy | Native provider runtime owns policy and admission | Pack owns bounded retries, delays, and SDK retry configuration |
| Invocation controls | Common Framework lifecycle/access controls | Existing invocation-level controls, interruption, and late-write fencing remain |
| Client lifetime | Native generation-owned resources | Reusable pack-owned HTTP transport, per-request settings, application shutdown cleanup; no client cache |

Diagnostic attempt/usage events are observations, not enforcement or accounting.
Keep existing usage calculations and fields unchanged. Console labels totals
"Framework-accounted usage"; API documentation and MCP usage descriptions explain
that executor-managed provider calls are excluded. A zero Framework counter means
zero within that accounting scope, not measured zero executor usage. Preserve native
usage in full responses and diagnostics when captured, without aggregating it into
Framework totals. V1 adds no completeness indicator or executor usage-reporting API.
No generic provider-accounting API is required
for v1; reconsider an optional facility only after real pack usage demonstrates a
need. Verify the new guarantees as part of the coordinated replacement. Delete old
YAML/configuration support and obsolete enum values under the agreed
breaking-change policy; no legacy migration support is required.

## TypeSafe provider baseline

Gemini and OpenAI research and requirements are deferred to their follow-up work.
Their planned inclusion in this extension does not establish contracts for them.

### TypeSafe

The native endpoint is `/v1/systemone`. Instructions and criteria can carry
structured JSON. Current Loomspan supports the text/JSON Choice/Noul/Score subset,
requires object-valued answers, and passes those answers through without enforcing
their descriptive schema. See the [HTTP reference](https://docs.typesafe.ai/api/system-one)
and [current Loomspan guide](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

TypeSafe is no longer strictly text-only: images in state, instructions, and
criteria are documented behind `TypeSafe-Danger-Zone-Images: true`. The docs call
this experimental and not production-ready. Jev 1.13/1.14 accept images; Box
localization requires Jev 1.14. Assets use references and uploaded/base64 image
data. Treat this as a model/version/opt-in capability, not universal support.
[Image documentation](https://docs.typesafe.ai/images.md)

Choice confidence adjusts the highest probability relative to an even split;
Score confidence also considers distance across levels. Noul has no separate
confidence. For a two-option distribution of 0.75/0.25, Choice confidence is 0.50.
[Confidence definitions](https://docs.typesafe.ai/confidence)

## TypeSafe executor and native JSON results

V1 provides one TypeSafe SkillExecutor in the Decisions artifact. Its exact executor
name and request-binding configuration remain to be designed. The Loomspan
extension contract governs invocation and diagnostics only; it is not a common
Decisions API.

- Keep provider field names, question identities, value types, score ordering,
  confidence meanings, refusal structures, and usage fields intact.
- Return the complete native JSON response body for a successful request, including
  the outer envelope and additional provider fields. Do not extract only answers,
  rename fields, add a normalized result wrapper, or discard unknown fields.
- HTTP/transport failures remain execution failures with diagnostic evidence; returning
  native JSON does not turn failed HTTP requests into successful skill results.
  Preserve provider-declared refusals within successful response bodies as native data.
- Pack code owns request construction, provider-specific request validation, bounded retries,
  and complete-response handling through the HTTP client/existing JSON tooling.
  No separate JSON parsing contract is required. Keep documentation focused on Loomspan configuration/input
  binding and use provider documentation for request/response semantics.
- Keep credentials, diagnostics, and transport utilities focused on the TypeSafe
  implementation; future providers do not require shared abstractions in v1.
- Keep batching and provider selection explicit. Do not split requests or substitute
  providers implicitly. Initial modality support remains an implementation-scope
  decision; unsupported capabilities must produce clear errors, not altered content.

For TypeSafe migration, native Framework currently returns the extracted `answers`
object. The pack returns the whole response, so `/repeat/noul` becomes
`/answers/repeat/noul`. Replace repository consumer bindings and examples with the new shape. V1 has no
compatibility mode that retains the old answers-only result. Complete response means
preserving the JSON content/structure, not promising identical whitespace or bytes.
Diagnostic size limits must not truncate the business result.

For the TypeSafe executor specifically, build, validate, and serialize the request
once before the first send and reuse the same body bytes across retries. Resolve
endpoint/credentials once from captured configuration; materialize any supported
local resource content while building the request. Remote URL content is not frozen.
Cancellation, interruption, remaining time, and per-attempt timeout remain live checks.
This is pack-internal TypeSafe implementation policy, not a generalized SkillExecutor
rule or Framework snapshot API. Other executors choose their own implementation;
identical request bodies do not guarantee identical responses or remote deduplication.

## Java implementation: independent Maven project

Create `loomspan-extensions` and establish its code/test/example structure before
generating implementation tickets. Each extension owns a small working example
exercised by its integration tests. For TypeSafe, that example is the external consumer
proof relative to Framework; no third consumer repository is required.

Use `loomspan-decisions` as the artifact name; group coordinates and release version
are undecided. Document its Spring Boot auto-configuration behavior rather than
encoding that detail in a longer artifact name.

Starting repository convention (directories can be added when needed):

```text
loomspan-extensions/
  pom.xml                       # Aggregate local builds
  tooling/
    catalog/
  extensions/
    decisions/
      pom.xml                   # Independent pack version and release
      README.md
      CHANGELOG.md
      src/
      docs/
        configuration.md
        results.md
        console.md
        troubleshooting.md
      examples/
        support-triage/
      agent/
        index.md
```

Each pack owns its POM and publication. The root build must not force all packs
onto one release version. Empty tooling or documentation scaffolding is not a v1
deliverable; introduce it as the working example needs it.

The project owns:

- The TypeSafe executor and native request/response handling; no shared
  normalized Decisions request/result model.
- HTTP/SDK clients, explicit retry policy, timeouts, capability validation, and
  configuration interpretation.
- Spring bean construction and shutdown cleanup for a reusable pack-owned HTTP
  transport. Apply captured URL/API key and execution-specific timeout per request;
  no authenticated-client cache or generation-specific transport replacement.
- `@SkillExecutor` entry points and integration with the planned public execution
  handoff. Provider-attempt admission and authoritative usage reporting are
  intentionally absent from v1; diagnostics do not provide accounting parity.
- Provider fixtures, package contract tests, and consumer tests against supported
  Framework versions. Use live evaluations separately for model behavior.

Depend only on supported Loomspan application API. Do not use internal clients,
session bindings, trace services, or provider registries. Any new Framework API
needed by the pack must be designed and allowlisted explicitly in Framework.

Start with one artifact containing the TypeSafe executor, with Gemini and OpenAI
planned as future additions to that same extension. Split modules only when
dependency weight or actual maintenance needs justify it. Record the targeted
extension contract and tested Framework/Java/Boot combinations without creating
an execution-versus-presentation compatibility matrix.

Use normal Maven repositories for binaries and dependency resolution. Spring Boot
auto-configuration can register beans from the dependency. Installation makes
executors available; applications explicitly declare callable skills. No runtime
JAR download or classloader-based hot replacement is part of this design.
[Maven dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html),
[Spring Boot external auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)

## Agreed v1 response handling and runtime binding checks

TypeSafe already returns JSON. The v1 requirement is to return its complete native
JSON response, preserving its content, including unknown fields and native refusals,
without extracting or reshaping it. Conversion from HTTP response bytes to the Java
return value is an implementation detail handled by the HTTP client or existing JSON
tooling; do not add a second parsing step when the client already supplies that value.
No separate JSON specification or new Framework capability is required.
Preserve valid JSON through the actual Java executor return path, including JSON
null and string responses. JSON `null` is not an empty HTTP body. The existing
Java invoker converts Java null to empty output and passes Java strings through
as raw text; the pack must choose a return representation that preserves JSON
values across that boundary. This requires no new Framework behavior, parsing
contract, or provider response-schema enforcement.
Classify transport failures and HTTP status before applying successful-response JSON
decoding. Retry eligible failures only under the pack's bounded retry and lifecycle
policy; otherwise fail execution. Error-body diagnostic capture or decoding must not
change that classification: a retryable HTTP failure with an HTML or malformed JSON
body remains eligible under the same policy. For successful HTTP responses, decode
and return the complete native JSON; empty or malformed JSON fails execution without
a retry solely for decoding failure. HTTP/transport failures are not successful results.
Do not enforce a provider response schema, answer completeness, confidence ranges,
or other answer semantics. Applications interpret returned data. Diagnostic truncation
must never truncate the business result. Provider-specific request validation remains.

Output correctness here means faithful return of the complete response. V1 introduces
no executor output-schema YAML field, generated metadata hook, or Framework output
validation feature. Accept reduced catalog/forwarded output metadata and rely on
existing runtime binding checks, documented result paths, and consumer tests.
Those runtime checks do not establish full result-schema conformance. Retain all
existing generic checks; do not add TypeSafe-specific validation to replace the
native descriptive schema. The TypeSafe executor returns its complete native JSON response; the
answers-envelope path migration is independent of the absence of metadata.

## Agreed v1 credential lookup

The Decisions pack checks for `api_key` in the invocation's captured `skillConfig`
first, then in captured `executorConfig` only if the skill key is absent. If absent
from both maps, fail before any provider request. A present null, blank, or non-string
value fails validation rather than triggering fallback. Skill configuration wins
when both contain valid keys; preserve the selected valid value unchanged.

The application supplies resolved values. V1 adds no automatic `${...}` or
`api-key-ref` resolution, implicit environment fallback, or native connection-registry
lookup. Both maps remain publishable through their owning namespaces; credentials
are not limited to startup Spring properties. No Framework credential API is needed.
Apply effective per-skill overrides on each request through the shared transport;
do not store credentials in mutable shared client defaults. Do not apply this credential-specific precedence
rule to every other pack setting without an explicit configuration design.

## Agreed v1 endpoint lookup

For the TypeSafe executor, resolve `base_url` by key presence:

1. Use `skillConfig.base_url` if present.
2. Otherwise use `executorConfig.base_url` if present.
3. Otherwise use that executor's provider default.

A present null, blank, non-string, or invalid URL fails before transmission; do not
fall back from an invalid override. Resolve using captured invocation configuration.
Preserve the base URL path prefix when adding the provider endpoint path. Retain
TypeSafe's configurable prefix plus `/v1/systemone` behavior; alternative routes
such as the existing OpenRouter setup require protocol compatibility and verification.
This is not provider discovery, route negotiation, or automatic fallback. The rule
also permits application proxies and test servers without a Framework enhancement.
Apply the effective base URL and credentials on each request.

## Agreed v1 HTTP transport lifetime

Reuse a pack-owned HTTP transport and connection pool, closed at application shutdown.
Set the URL, API key/authorization header, and timeout on each request. Do not build
an authenticated-client cache keyed by URL/key or generation, and do not mutate shared
client defaults to implement invocation overrides. Request configuration comes from
the captured invocation maps; remaining time is live and caps the pack-owned total
request timeout. Preserve the distinction between total and inactivity timeouts.

Publication requires no transport replacement: old work keeps its captured settings,
new invocations receive newly published settings, and concurrent requests remain
independent. Test this explicitly, including distinct keys/endpoints. Existing
Framework generation retention still protects captured configuration; the shared
transport does not need generation retirement callbacks. Reuse the HTTP pool rather
than recreating it per invocation. This is pack implementation work, not a Framework
feature or a promise to share every transport across incompatible connection settings.

## YAML skills and cookbook: application behavior

Keep reusable transport/provider code in Java. Keep task-specific inputs,
questions, criteria, output bindings, and access policy in authored skill recipes
where supported by the package contract. Core continues to understand ordinary
`executor:` and `config:` declarations, not a new decisions grammar.

Start with one runnable recipe, such as support triage, showing invocation,
refusal/error handling, and application-chosen thresholds. Verify child composition
and output bindings in consumer tests; separate cookbook examples can follow when
useful. Relevance scoring and severity assessment are future recipe candidates,
not v1 delivery requirements.

Every supported recipe should include:

1. Exact pack dependency and tested Framework version(s).
2. Local stub setup and test credential values for the repeatable integration run.
   Document application-supplied credentials/service setup for optional live use
   separately; no real credentials or automatic placeholder resolution are part of
   the integration test path.
3. Complete YAML and any application-side Java needed to run it, using the `default`
   namespace without duplicating Framework namespace guidance.
4. Sample invocation inputs and clearly labeled illustrative outputs.
5. Output paths, composition/binding examples, and capability constraints.
6. Expected Console evidence and troubleshooting steps.
7. A repeatable verification command and links to matching source/version.

Run the recipe in `loomspan-extensions` integration tests using a local stub HTTP
provider and deterministic mocked model responses, exercising actual pack request/
response handling and supported-API invocation. No real API keys, provider account,
or live service are required to pass; use test credential values for the stub.
This repeatable example verifies integration and catches documentation drift, not
model quality. Label example thresholds as illustrative application choices without
production-quality claims. Framework tickets do not wait for these downstream tests.
Users may copy and customize recipes; integration fixes should arrive through
dependency updates rather than requiring copied HTTP implementations to be patched.
Keep the initial recipe with its pack release; a separate recipe versioning system
is unnecessary for v1.

## Website catalog: discovery and adoption

The eventual developer guide should offer a unified catalog of packs and examples.
For v1, a small index linking to release-aligned documentation and one runnable
example is sufficient. Maven delivers code; the catalog explains installation and
use. A searchable site and automated aggregation are follow-on work driven by need.

V1 uses the POM for artifact identity, release version, and Framework dependency.
The executor's Java parameter identifies the invocation contract. Standard pack
directories locate documentation and examples; the README describes the pack,
tested Framework/Java/Boot combinations, and required service setup. Consumer tests
verify the packaged TypeSafe executor against the identified Framework build/version. No separate
manifest, manifest schema, or build/runtime manifest validation is required.
Application-owned executors likewise need no manifest.

Keep documentation with the pack. A future central index can aggregate
entries without hosting binaries. Use the same documentation for humans and AI:
a small routing skill/index can progressively load a relevant pack, compatible
release, recipe, and detailed reference. Do not require a separate AI documentation
corpus or a sophisticated discovery service for v1.

The first website flow is: discover pack, select compatible release, add dependency,
configure service, select recipe, and run the example. Developers can optionally
inspect its execution in Console for debugging and troubleshooting.
Later CLI or Console tooling may scaffold dependency/configuration changes using
the POM and directory conventions. Those are optional conveniences; manual installation remains
complete. No automatic installation or execution follows from viewing a catalog.

## Console integration requirements

V1 uses automatic execution-boundary capture plus a logging-style diagnostics
call accepting a plain message and optional structured JSON data. V1 has three fixed
levels selected by the method: `info`, `warn`, and `error`; each accepts a message
and optional JSON data. There is no missing/default level or custom level mechanism.
These levels apply only to developer-authored calls from within a SkillExecutor.
Framework-generated capture-failure and limit notices are separate system notices
without these levels; standard Java fallback logging remains separate. Severity
describes the developer diagnostic only and never changes outcome or triggers retries.
Exact Java declarations remain open. Structured data means JSON objects
with string keys, arrays/lists, strings, numbers, booleans, and nulls. Arbitrary DTO
serialization and implicit `toString()` conversion are deferred.

Framework captures the following without extension-authored logging:

- Execution start and stop, elapsed time, and outcome.
- Invocation/skill/executor identity and executor configuration generation ID.
- Executor input and output through the existing capture paths and controls.
- Escaping failures: exception type, message, diagnostic cause, and the existing
  failure classification. A failed execution has no fabricated successful output.

`RecoverableSkillException` is a corrective-feedback signal, not an automatic
retry instruction. Standard tool calling permits model correction; direct invocation
propagates it and explicit step planning terminates. Other application failures are
terminal under the existing policy. Caught provider failures and internal retries
are invisible at the boundary unless the pack explicitly reports them. Preserve
these semantics rather than inventing a retryable/terminal exception API.
Keep automatic failure classification and recording unchanged. Do not map escaping
exceptions to diagnostic levels or emit duplicate `warn`/`error` diagnostic calls.
The executor may explicitly call `warn` for a caught failure it intends to retry;
Framework records any failure that escapes the executor through its existing path.

The pack reports what happens in the middle with plain messages and optional JSON
data, such as provider identity, request/response evidence, or retry details.
Each captured diagnostic call produces its own trace record within the existing
execution frame, rather than opening a new frame or appending to one combined message.
Framework adds a timestamp and invocation/frame association, uses the existing trace
sequence, and snapshots supported data at capture time. Console groups records beneath
the execution and displays readable
messages with expandable JSON alongside the automatic boundary evidence. Severity
does not change execution status: an `error` diagnostic does not fail the skill.
The earlier execution/evidence/presentation layers remain a useful ownership model;
v1 needs no pack-authored layout, display hints, or presentation descriptor/schema.
All diagnostics remain part of the single extension contract version.

The SkillExecutor developer determines diagnostic messages, severity, and supplied
JSON; Console displays them as recorded without creating explanatory error messages
or interpreting their meaning. Framework records execution facts and its own capture
notices separately; those notices have no developer severity. Console displays capture
states only when explicitly recorded by Framework. Missing evidence does not establish
whether capture was disabled, failed, or never attempted. Notice delivery is best effort;
if recording fails and no notice survives, Console has no cause to explain.

Console reports recorded facts; it never synthesizes results or infers execution
outcomes from diagnostic levels, message text, payload fields, or timing. It displays
Framework-recorded outcome/classification and developer-authored messages/JSON as
recorded. Developers interpret caught request failures, retries, and negative business
decisions from that evidence. V1 adds no typed provider-attempt tracking or provider-
specific interpretation. Grouping, filtering, and factual aggregates remain presentation,
not new semantic outcomes. This follows the authoritative-facts boundary in the
[Framework feature design lens](../framework-feature-design-lens.md#12-keep-semantic-authority-at-the-owning-boundary).

MCP integration is settled: reuse `LOOMSPAN_query_trace_records` and
`LOOMSPAN_read_trace_content`. Existing record-type/frame filters, bounded pages,
continuation, logical/physical representations, optional inline content, and content
references already provide retrieval. Reuse existing trace/frame summaries, retention,
and access controls. No new tools, diagnostic stream, query service, indexes/stores,
cursor formats, live-tailing contract, severity filters, or retention policy are needed.

Add the diagnostic and system-notice record types to the coordinated Java/Go/TypeScript
vocabularies and affected schemas; Console currently rejects unknown record types.
Developer severity belongs to the diagnostic record, not its execution outcome;
system notices have no developer severity. Store the message, severity, optional JSON,
and explicit capture state in the existing record `data` content path. Arbitrary metadata
is not automatically exposed by the MCP record-summary DTO. Reuse existing content
descriptors and reads rather than inventing new retrieval or summary fields. Existing
filters apply normally to notices too; callers can request both record types together.
Do not interpret an `error` diagnostic as an `ERROR_RECORDED` execution failure.

The roadmap's [settled MCP reuse contract](executor-packs-and-typesafe-extraction-roadmap.md#settled-decision-reuse-existing-mcp-record-inspection)
contains the implementation code anchors and acceptance checks. Every derived ticket
touching recording, trace consumption, Console, or MCP must carry those details,
permitted changes, exclusions, and applicable checks in its own body; a link alone is
insufficient. Separate shared context and checks owned elsewhere from this ticket's
acceptance checks and explicit dependencies under the ownership rules below.
Verify mixed existing/diagnostic/notice records through the existing tools, including
type/frame filters, continuation, content reads, fidelity, recorded capture-state visibility,
and unchanged execution outcomes. Do not reopen this as a separate inspection design;
any concrete implementation limitation requires a separately justified scope decision.

Bound message/data bytes, nesting, collection traversal, event count, and total
capture per invocation. Detect cycles and unsupported values without calling their
`toString()`. Framework represents known capture states explicitly in recorded capture
metadata or notices, with best-effort notice delivery. Console only displays those
recorded facts; it does not infer states from missing evidence. Keep records valid
JSON; do not pass malformed partial JSON to Console. Ordinary
diagnostic failures must not change the executor result. Define concurrent writes
and writes after invocation closure, and avoid unbounded UI rendering. These are
resource controls, not sensitivity classification or masking. Use existing canonical
capture paths and preserve the narrow derived-journal exception.

### Conceptual diagnostics API and usage

The agreed interaction is `invocation.diagnostics()` with a plain message and optional
structured data. This sketch records the discussion, not final Java declarations:

```java
// Framework-created, invocation-scoped handle; the pack does not implement it.
// Agreed methods: info/warn/error; exact structured-data parameter types remain open.
var diagnostics = invocation.diagnostics();
diagnostics.info("Sending provider request", Map.of("provider", "typesafe"));
diagnostics.info("Provider response received", responseJsonData);
// responseJsonData is JSON-compatible data, not an arbitrary provider DTO.
```

Do not require the executor to log its own start/stop, input/output, or escaping
exception. Use explicit messages for caught failures/internal retries that Framework
cannot see. Automatic capture should reuse existing observation boundaries without
duplicating records. Diagnostic serialization failure is non-breaking; serialization
of the actual business result is still part of successful execution and may fail it.

The earlier three-layer concept maps to this simple v1: Framework owns execution
facts, packs supply intermediate evidence, and Console owns generic presentation.
No separate presentation descriptor is required to achieve that separation.

### Non-breaking capture and fallback behavior

Settled scope decision: the non-breaking guarantee covers the new
`diagnostics.info/warn/error` facility and any new diagnostic recording introduced
by this work, including Framework-authored diagnostic notices. Ordinary failures
in these paths must neither change an executor result nor replace its original
failure. Automatic start/stop, input/output, and escaping-exception capture reuse
existing Framework observation paths with their existing execution/failure semantics;
the guarantee is not retroactive and does not authorize an observability rewrite.
Business request/result preparation and serialization may still fail execution.
Caller argument evaluation before entering a diagnostic method and JVM-wide failures
are outside the guarantee.

This boundary is agreed, not an unresolved audit finding. Apply the roadmap's
[settled boundary](executor-packs-and-typesafe-extraction-roadmap.md#settled-decision-diagnostic-failure-containment-boundary)
in implementation and future audits. Reopen only for an explicit scope change or
concrete implementation evidence of a violation, not because diagnostic signatures,
limits, or concurrency details remain to be implemented.

Ordinary diagnostics validation, serialization, recording, and limit failures must
not propagate into executor execution. Use Jackson for the bounded v1 JSON path.
When a payload cannot be captured:

1. Stop processing that payload and return normally after best-effort reporting.
2. Emit a small Framework-generated diagnostic notice stating the reason, such as
   unsupported value, serialization failure, or per-event size limit. Preserve the
   original message when it fits; distinguish partial truncation from no capture.
3. Write a bounded warning through the standard Java logging path.

The fallback must use a separate, fixed envelope, not recursively call the failed
diagnostics serializer or stringify the rejected object. Contain ordinary failures
in the fallback and standard logging path as well. Reserve bounded capacity for a
single invocation-limit notice; once the invocation budget is exhausted, suppress
further diagnostic calls. Rate-limit or coalesce repeated fallback warnings so
failure handling cannot flood Java logs or Console. Disabled capture is an expected
state, not a reason to emit a warning on every call.

If recording infrastructure is unavailable, delivery of the Console/MCP notice is
best effort. Do not promise recovery from JVM-wide resource failure. Java evaluates
arguments before entering the diagnostics method, so exceptions or expensive work
in caller expressions such as `response.toJsonData()` are outside this guarantee.

Automatic input/output capture must reuse the existing supported evidence handling,
including resource-valued inputs; it is not permission to reflectively traverse any
Java object or eagerly read arbitrary resources. The exact JSON-value validation
and snapshot mechanism remains implementation work.

Console is only for development, debugging, and troubleshooting. Extensions run
without it installed or connected, and presentation cannot affect execution. Richer
operation handles, nested spans, arbitrary Java-object serialization, custom UI code,
and presentation hints require demonstrated need after v1.

Provider-attempt admission and authoritative usage reporting are deferred from v1,
as is a physical-request cancellation callback. Diagnostic records cannot substitute
for those facilities. Verification covers the explicit v1 changes above, not full
native TypeSafe parity, with acceptance owned by the relevant repository as below.

## Implementation verification and repository ownership

### Settled repository-local acceptance and ticket requirements

Framework tickets complete on applicable tests/fixtures and Console/public-surface
checks within `loomspan-framework`. Extensions tickets own the TypeSafe integration
and mocked consuming-example tests in `loomspan-extensions`. Genuine dependencies
are explicit: Extensions needs an identified Framework build, but the upstream
Framework ticket does not wait for the downstream example or consumer test run.
The shared checklist tracks overall delivery across those repositories; it is not
an acceptance gate copied into every ticket.

Broader integration tests in `loomspan-sidecar-test-suite` are separately tracked
future follow-up. They are not gates for any ticket, phase, native removal, or overall
completion in this roadmap. Live provider/model-quality evaluation is likewise not
an implementation gate. Mocked responses prove integration, not accuracy, calibration,
latency, or cost; evaluate application tasks before making quality/threshold claims.

Every derived ticket/subticket must carry its owning repository, local acceptance
commands, explicit dependencies, applicable mocked/no-real-credentials requirement,
and explicit exclusion of sidecar-suite/live-evaluation gates in its own body.
Carry the roadmap's [settled ownership and ticket handoff](executor-packs-and-typesafe-extraction-roadmap.md#settled-test-ownership-and-mandatory-ticket-handoff),
not just a link. Assign external-consumer checks below to Extensions tickets and
generic Framework checks to Framework tickets; do not turn this checklist into a
requirement that prevents Framework work from completing locally.

Every mandatory ticket handoff distinguishes shared implementation context from
ticket acceptance. Include applicable contracts, constraints, and exclusions in the
body, and separately list the ticket's own acceptance checks and explicit dependencies.
Checks owned by other tickets or repositories remain contextual guidance with their
ownership identified; copying them into the ticket does not make them completion
gates. A Framework diagnostic-recording ticket must preserve the MCP contract but
does not wait for the Extensions consuming example to pass. All checks remain required
in their owning tickets or the overall delivery checklist.

Overall v1 delivery is implementation complete, using the shared
[completion checklist](executor-packs-and-typesafe-extraction-roadmap.md#v1-completion-gate-implementation-complete).
The TypeSafe executor, the Framework replacement, Console/MCP evidence inspection,
execution without Console, the Extensions-owned consumer example with deterministic
mocked provider fixtures and no real API keys, one
runnable recipe/current documentation, and required public-surface/compatibility tests
must be complete against identified builds. Locally installed or staged Maven artifacts
suffice. TypeSafe is the complete v1 provider scope; Gemini and OpenAI are follow-up
work in the same extension, with requirements deferred. Track publication/product releases
separately; no simultaneous release or automated cross-repository pipeline is required.
Do not claim public dependency availability before publication.

- Verify wire mappings, question identity, numeric fidelity, score ordering, and
  refusal/error behavior with deterministic fixtures for the TypeSafe executor.
- Test unsupported capabilities before sends, retry cardinality, diagnostic usage,
  cooperative cancellation/deadlines, and lifecycle integration from the mocked
  consuming example in `loomspan-extensions`. Verify that diagnostic usage is not authoritative accounting
  and that pack retries do not reserve the native shared provider-attempt budget.
  Include a mixed mission proving that only Framework-accounted calls contribute to
  totals and that Console/API/MCP descriptions clearly state the accounting scope.
- Verify one-record-per-captured-call ordering within existing execution frames,
  existing MCP record queries/content reads (no new retrieval machinery), and that
  ordinary failures in new diagnostic capture/fallback paths preserve the executor
  result or original failure, without changing existing automatic-observation semantics. Exercise limits,
  disabled/unavailable capture, and warning coalescing without recursive reporting.
- Live evaluation is outside implementation acceptance. Before making model-quality
  or production-threshold recommendations, evaluate representative labeled application
  tasks and record evidence. The working example uses illustrative thresholds and
  mocked responses; delivering it requires no live evaluation or benchmark project.
- Recheck documentation/model availability and route-specific behavior, including
  direct TypeSafe and configured protocol-compatible prefixes such as OpenRouter.
  Configurable prefixes are agreed; current service availability is not guaranteed.
- Verify complete native response preservation and migrated TypeSafe answer paths.
  Include JSON null and string fixtures through the actual invocation return path
  in the Extensions-owned consumer tests, rather than checking only HTTP decoding.
  Settle provider request binding, initial media scope, coordinates, and release/support
  policy before publishing examples; no unified API or legacy response mode is in v1.

## Follow-up research: bounded Java-object serialization

Research date: 2026-11-15. This is a source/documentation review, not a benchmark
or proof of hard resource isolation. It does not expand the agreed v1 JSON-only
payload contract or select a new dependency.

| Candidate | Useful existing mechanism | Limit for Loomspan |
| --- | --- | --- |
| Jackson Core | Streaming JSON generation and configurable output nesting depth | Needs additional byte, traversal, and event budgets; does not sandbox arbitrary object code |
| Gson | Ordinary reflective object serialization reads fields rather than bean getters | Circular references are documented as unsupported; custom adapters and collection behavior still need control |
| Datadog Java debugger serializer | Bounded object capture with depth, field/collection/string limits, timeout checks, and known collection handling | Agent-internal implementation with product dependencies, not an established standalone library contract |

Jackson is already a dependency in the root POM (`tools.jackson.core:jackson-databind`).
Its reviewed `StreamWriteConstraints` implementation limits nesting depth; read-side
string/document limits must not be mistaken for object-serialization limits. Use
instance-local constraints, not global defaults. A candidate v1 implementation is a
small bounded JSON-value walker feeding Jackson's streaming generator and a capped
output sink. Avoid constructing an unlimited JSON string/tree and truncating it
afterward. This is an implementation recommendation to validate, not code already
implemented. [Jackson write constraints](https://github.com/FasterXML/jackson-core/blob/3.x/src/main/java/tools/jackson/core/StreamWriteConstraints.java)

Gson is a useful example of avoiding ordinary bean getters through field-based
serialization, but its user guide explicitly warns about circular-reference
recursion. Switching serializers would not itself solve bounded object capture.
[Gson user guide](https://google.github.io/gson/UserGuide.html)

Datadog's `SerializerWithLimits` is a closer example of diagnostic object inspection:
it checks budgets during traversal and distinguishes known collections from arbitrary
implementations. Study those resource-control techniques, not wholesale adoption of
its product-specific policies or agent dependencies. This review did not establish a
supported standalone Maven artifact for that helper.
[Datadog serializer source](https://github.com/DataDog/dd-trace-java/blob/master/dd-java-agent/agent-debugger/src/main/java/com/datadog/debugger/util/SerializerWithLimits.java)

A timeout check cannot interrupt a getter, iterator, custom serializer, or `toString()`
that never returns. Java task cancellation only attempts interruption; a timed-out
wait does not prove the work stopped. Consequently, a worker timeout alone is not
hard CPU/memory isolation. This is an inference from the execution model, not a
claim that all serializers are unsafe.
[Java Future cancellation contract](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/util/concurrent/Future.html)

Recommendation: retain plain messages and structured JSON for v1, reuse Jackson,
and add bounded capture around it. Even `Map`/`List` are interfaces whose custom
implementations can execute arbitrary code; define supported concrete containers
or a controlled JSON-value representation before promising predictable traversal.
Do not implicitly invoke arbitrary `Number` subclasses or custom adapters either.
Arbitrary-object support can be investigated later with explicit acceptance limits.
No reviewed library establishes a universal in-process guarantee for arbitrary
objects, and no new library is justified for v1 by this review.
