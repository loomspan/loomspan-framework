# Executor packs and TypeSafe extraction roadmap

Status: agreed v1 direction and implementation roadmap; no implementation completed.
Date: 2026-11-15.
Settled-decision consistency review: 2026-11-16.

## Mandatory breaking-change policy for this work

The user explicitly authorizes destructive source/API/wire changes for this
pre-release development effort. Remove the existing native TypeSafe implementation
and reimplement its provider integration as a SkillExecutor in the Decisions pack,
alongside the other explicitly listed generic executor enhancements. There are no
backward-compatibility or migration-support requirements for the replaced behavior.

Do not add compatibility shims, old constructors/accessor aliases, legacy invocation
adapters, deprecated compatibility surfaces, transitional YAML/config translators,
dual execution paths, historical trace readers, protocol negotiation, or migration
tooling. Update or remove old public types/enum values, declarations, configuration,
call sites, tests, fixtures, examples, and documentation together. Tests must describe
the new behavior, not force preservation of the removed implementation.

This policy includes Framework, Loomspan Console, MCP/inspection contracts, trace
formats, and the Framework-to-Console `consoleCompatibilityVersion` relationship.
Make the required compatibility-version change and update Java, Go, TypeScript,
readers/writers, fixtures, and Console expectations coherently. Do not retain support
for older Framework/Console combinations or old trace shapes for this change.

Versioned invocation packages remain the chosen new contract structure. Implement
only the new v1; remove/replace the current unversioned handoff without an adapter.
Earlier discussion of future version overlap is not a requirement to implement
multiple versions or legacy support in this effort. Any future overlap policy is
separate work, not a reason to preserve the implementation being replaced.

Destructive changes here mean changes to the scoped implementation and contracts;
they do not expand the feature scope or change the agreed executor/YAML namespace
relationship. Public-surface allowlists and README still must be updated deliberately,
and required checks still run. The TypeSafe-specific Framework expansion prohibition
and the agreed v1 behavior reductions remain in force.

### Required text in every ticket derived from this roadmap

Include the following policy verbatim in every implementation, testing, documentation,
Framework, pack, Console, or MCP ticket, including subtickets. A link alone is not
sufficient for a coding agent working from an isolated ticket:

> Breaking-change policy: This is pre-release development. Destructive changes to
> the scoped Java APIs, configuration, traces, MCP contracts, and Loomspan Console
> are authorized and preferred. Remove the replaced native TypeSafe implementation
> and obsolete contracts; do not add compatibility shims, legacy adapters, dual
> paths, migration support, or historical readers. Update all affected consumers,
> tests, fixtures, and documentation to the new design. Coordinate required
> Framework-to-Console compatibility-version changes with Console. Implement only
> the explicitly listed generic SkillExecutor enhancements; do not add TypeSafe-
> specific Framework behavior. Preserve the existing executor/YAML skill namespace
> relationship. Versioned invocation packages start with the new v1 only; no legacy
> invocation support is required.

Each ticket must identify its affected layers and acceptance checks. Inapplicable
layers need no changes, but omission of a layer from one ticket does not authorize
leaving it inconsistent across the overall work. No extra compatibility approval
step is required for changes already covered by this policy.

## Outcome

Remove native TypeSafe execution from Loomspan Framework and deliver decision
evaluation through an independently maintained Decisions Executor Pack in its own
project, distributed as a Maven dependency. Give executor implementations a small,
supported way to report their work into Framework execution records and Loomspan
Console. Core owns execution infrastructure; packs own integration behavior.
Console is an optional development, debugging, and troubleshooting tool, not a
runtime tool. Extensions must execute fully without Console installed or connected.

The companion [provider research and pack design](decisions-executor-pack-research.md)
records the TypeSafe baseline, packaging direction, cookbook separation,
and website catalog proposal. Provider support and pack publication are work for
the separate project; this repository owns the framework changes and migration.

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

## Handoff to the next working context

### Current handoff after the five-issue follow-up review

The 2026-11-16 follow-up reviewed and resolved five additional wording/ownership
issues across all three documents. Preserve these user-confirmed decisions:

1. Console displays recorded facts only. SkillExecutor developers author diagnostic
   messages, severity, and JSON; Framework owns execution facts and separate capture
   notices without developer severity. Console does not synthesize explanations or
   infer capture state from missing evidence. Notice delivery remains best effort.
2. Classify transport failures and HTTP status before successful-response JSON
   decoding. Retry eligible failures under the pack's bounded policy and live lifecycle
   checks. An HTML or malformed JSON error body does not change HTTP retry eligibility.
   Empty/malformed JSON in a successful HTTP response fails without a decoding-only retry.
3. Response handling stays pack-owned; existing Framework runtime binding checks
   remain. The TypeSafe executor does not enforce provider response schemas or answer
   semantics. Applications interpret the complete native response.
4. Existing executor namespaces and visibility are retained. Source and tests confirm
   that private namespaces permit same-namespace references and public namespaces
   permit foreign references, subject to authorization. Executors remain reference-only.
   This is verified existing behavior, not a claim about original design intent.
   Extension examples use `default`. Namespace concepts belong in Framework docs;
   do not duplicate tutorials across `loomspan-extensions` subprojects. The original
   registration concern was narrowed to this documentation clarification, not a new
   namespace feature, design workstream, or acceptance gate.
5. Mandatory ticket handoffs carry shared implementation context separately from
   each ticket's own acceptance checks and explicit dependencies. Copying another
   owner's checks does not turn them into this ticket's completion gates. Keep each
   required check assigned to its owner and retain the overall delivery checklist.

Verification in this follow-up: inspected `SkillExecutor.namespace()`, executor
registration, and `SkillDependencyValidator` reference/visibility resolution. Ran
`./mvnw.cmd -q -Dtest=ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest test`:
12 tests passed, with zero failures, errors, or skips. These prove existing behavior,
not the proposed extraction. No full suite or live provider research was run.
The final documentation pass checked 62 local file/heading links, fenced-block balance,
stale wording, and `git diff --check`; all checks passed. Only these three planning files were edited; no
production implementation, repository, tickets, release, site, or commit was created
in this follow-up. Recheck Git and repository state when resuming.

The five issues are resolved; do not repeat their design discussion merely because
implementation remains unchecked. The existing remaining-choices section still owns
unfinished signatures, limits, request binding, modality, transport defaults, and
release details. Next planned work remains repository setup before ticket generation,
subject to the user's next instruction; this handoff does not authorize starting it.

### Earlier six-gap consistency review

The six audit topics are resolved: TypeSafe-only v1; complete native JSON responses
without reshaping or a separate parsing contract; failure containment scoped to new
diagnostics; diagnostic records within existing frames using existing MCP retrieval;
cooperative cancellation using existing Framework authority; and repository-local
ticket acceptance with an Extensions-owned mocked example. Their detailed contracts
and mandatory ticket handoffs below are the implementation authority, not suggestions
to reconsider. Gemini/OpenAI and broader sidecar-suite testing are future work; neither
is a gate for this roadmap. Live provider evaluation is not an implementation gate.

The user specifically wants to avoid repeated audits that reopen settled decisions
or add machinery that later needs removal, especially in MCP. An unchecked task or
unselected Java signature/default is unfinished implementation, not an unsettled
product requirement. Future reviews should identify concrete conflicting text or
implementation evidence, apply the settled decision, and correct stale wording.
Do not restart the six design discussions without an explicit scope change.

The earlier 2026-11-16 consistency pass reviewed all three documents, corrected stale wording,
and checked 60 local links/heading references, code fences, and `git diff --check`.
It did not run production tests, revalidate live provider documentation, or implement
the design. That earlier context changed only these three planning documents; it did not
create the extension repository, tickets, releases, or a commit. Recheck current Git
and repository state rather than treating this historical handoff as live status.

Next planned work is to establish `loomspan-extensions` and its code/test/example
structure before generating implementation tickets. Carry the detailed settled
contracts, exclusions, local acceptance checks, and required policy text into ticket
bodies; links alone are insufficient. Follow the user's next request to determine
which step to execute; this handoff is not a request to begin implementation by itself.

Read this roadmap first, including its mandatory ticket policy. Then read the
[design/research](decisions-executor-pack-research.md) for provider evidence,
packaging, diagnostics, and catalog details, and the
[source review](typesafe-executor-pack-migration-review.md) for code/test anchors.
The roadmap's latest agreed scope and breaking-change policy take precedence over
historical native behavior described for comparison. The word "migration" in review
filenames means replacement analysis, not a migration-support deliverable.

Only these planning documents have been changed in this conversation. No production
implementation, tickets, new repository, Maven release, site, or live provider
evaluation has been created. Verification is recorded in the current handoff above;
the proposed implementation remains untested. Recheck Git status and local instructions when resuming; do not overwrite other
work or mistake existing native code/tests for a decision to retain that behavior.

Direction established by the user's roadmap request:

- Remove TypeSafe-specific functionality from core Framework.
- Develop the Decisions Executor Pack in its own project, distributed as a Maven
  dependency, containing the TypeSafe executor in v1. Gemini and OpenAI are planned
  follow-up additions to this same extension; their requirements and contracts are
  deferred. Return the complete native TypeSafe JSON response; no unified Decisions API in v1.
- Provide a supported way for executor code to produce information visible in
  Loomspan Console.
- Separate Java integration code from YAML cookbook examples, with a website
  catalog that explains installation and use of supported recipes.
- Use `loomspan-extensions` as the starting repository direction, with independently
  released subprojects and `loomspan-decisions` as the first artifact name. The
  companion document records the accepted starting directory convention.
- Use one extension contract version for the executor handoff, diagnostic reporting,
  and diagnostic presentation interpretation. Pack release versions remain separate.
- Implement only the new v1 versioned invocation contract. Future version-overlap
  policy is outside this effort; do not retain the old unversioned handoff.
- Keep v1 to a working dependency, consuming application, one runnable recipe,
  useful diagnostics, and a small documentation index. Expand based on feedback.
- Keep Console optional and observational. Presentation cannot control execution,
  budget enforcement, cancellation, or result semantics.

The agreed v1 uses versioned invocation packages, with the executor method's exact
parameter type identifying its contract at registration. Add simple live cancellation
status and remaining-time accessors alongside captured input/configuration and
diagnostics. Provider-attempt admission, authoritative usage reporting, and physical
request cancellation callbacks are deferred. These are deliberate TypeSafe behavior
changes, detailed below and in the companion research document. Exact signatures
and package spelling remain open; legacy compatibility is explicitly excluded;
no general Java SPI or bean-replacement contract is introduced.

Recommended next work: create `loomspan-extensions` and establish its code, test, and
example structure before generating implementation tickets. Then turn the agreed
scope into repository-owned tickets and finalize the
few open signatures/settings listed at the end. The source review is complete; reuse
its eight dispositions instead of restarting the provider-abstraction or parity debate.
Read root guidance and `loomspan-console/AGENTS.md`, then verify relevant code anchors
when implementing. Build the smallest external consumer and a non-AI diagnostic
fixture to prove generality. Removing native support is authorized; phase ordering
is work organization, not a requirement for a compatible transition release. Do not
copy internal Framework services into the external pack or treat conceptual API
examples as already implemented.

Keep these documents synchronized as decisions are made: record what is approved,
which behavior intentionally changes, and evidence for completed phases. The
provider research is a dated baseline; consult its linked live documentation when
implementation starts.

## Decision summary and rationale

| Area | Settled v1 decision | Why / practical consequence |
| --- | --- | --- |
| Packaging | Separate `loomspan-extensions` repo; independent subproject POM/releases; `loomspan-decisions` artifact | Extend Framework by dependency after generic capabilities exist; long starter naming and premature module splits add little |
| Provider API | TypeSafe executor with complete native JSON responses in v1; Gemini and OpenAI planned later in the same extension | Complete the TypeSafe foundation before designing each follow-up provider's unique requirements; no unified Decisions API |
| Public handoff | Versioned package/type identifies one extension contract; captured maps/identity plus diagnostics and live cancellation/deadline accessors | Avoid a separate execution/presentation compatibility matrix; implement only new v1 and replace old APIs directly |
| Console | Optional development/debugging/troubleshooting observer | No Console presence, rendering, or logging failure may govern runtime execution |
| Diagnostics | Automatic boundaries plus plain messages/structured JSON, one record per captured call within the existing execution frame | Framework knows both sides of the call; the pack explains intermediate work with a familiar logging interface |
| MCP | Reuse existing record-query and content-read tools, filters, pagination, and content references | New record types require vocabulary/content integration, not new retrieval machinery |
| Capture failure | New diagnostics contain ordinary capture/recording/fallback failures; existing automatic observation semantics remain unchanged | Settled scope: no broader observability rewrite; diagnostic failures must not replace an executor result or failure |
| Serialization | Jackson, restricted JSON data, bounded capture; no arbitrary DTO or implicit `toString()` | Serializer depth limits do not sandbox getters, iterators, or custom serializers |
| Credentials/endpoints | Presence-based skill config override, then executor config; key missing means error, endpoint missing means provider default | Both maps are publishable; callers choose shared or per-skill settings without a Framework credential feature |
| Numeric values | Existing executor decoding stays | No demonstrated need for high-precision configuration justifies a new contract |
| Validation | Pack validates each invocation before sending | General executors cannot inherit native provider-specific startup checks; publish success is not domain validation |
| Response handling / metadata | Return TypeSafe's complete native JSON response without extracting or reshaping it; existing Framework runtime binding checks remain | Pack-owned response handling does not enforce provider response schemas or answer semantics; no generated output metadata |
| Lifecycle | Live cancellation and remaining time; pack owns total HTTP timeout | Inactivity timeout is insufficient; no Framework physical-request callback in v1 |
| Transport | Shared pack-owned pool; URL/key/timeout per request; shutdown cleanup; no client cache | Reuse connections without per-generation cache eviction and retirement complexity |
| Accounting | No native provider-attempt reservations or authoritative usage reporting | General-purpose executors should not be forced into an AI-provider accounting model |
| Namespaces | Existing executor/YAML skill relationship unchanged | Multiple skills already share executors under current visibility/configuration rules |
| Catalog / AI discovery | POM identity/version, standard documentation directories, runnable example, index; no separate manifest | Existing Maven and Java contracts suffice; richer catalog automation waits for feedback |
| Replacement policy | Delete native TypeSafe/obsolete contracts across Framework and Console; no legacy support | Pre-release project explicitly permits destructive changes; future tickets must carry this policy |

Only `api_key` and `base_url` override rules are agreed; do not infer precedence for
all future settings. Check presence, not truthiness: an invalid present skill value
fails instead of falling back. Config maps contain supplied values, not automatic
`${...}`/`api-key-ref` resolution. Changing skill config versus executor config means
publishing the corresponding owning namespace; old invocations keep captured values.

## Ticket preparation and completion evidence

### Settled test ownership and mandatory ticket handoff

Create `loomspan-extensions` before generating implementation tickets, so extension
code, integration tests, and examples have concrete ownership. This documentation
decision does not itself create that repository.

Repository-local ticket acceptance and overall roadmap delivery are different:

- `loomspan-framework` tickets cover generic executor capabilities, native TypeSafe
  removal, and the associated Console/MCP work in this repository. Complete them
  using applicable repository-local unit tests, integration tests, fixtures, public-
  surface checks, and Console checks. Do not make their completion depend on the
  extension example, an external consumer run, or sidecar-suite testing.
- `loomspan-extensions` owns the TypeSafe pack and its small working consuming
  example. Each extension should include a small example exercised by its integration
  tests. For TypeSafe, use a local stub HTTP provider with deterministic mocked model
  responses to exercise real request construction, response handling, supported-API
  invocation, and relevant composition/diagnostic/lifecycle behavior. Do not mock away
  the pack itself. These tests require no real API keys, provider account, or live
  inference service; any credential field needed by the stub uses a test value.
  Keep the example and repeatable test command with the extension. This is the external
  consumer proof relative to Framework, not a requirement for a third consumer repository.
- Broader integration tests in `loomspan-sidecar-test-suite` are future follow-up.
  They are not a prerequisite or acceptance gate for any ticket, phase, native removal,
  or overall completion under this roadmap. Track that future work separately.

Each ticket names its owning repository and local acceptance checks. Express genuine
implementation dependencies explicitly (for example, the extension consumes an identified
Framework build containing the new invocation API). Such dependencies do not make the
upstream Framework ticket wait for downstream validation. The overall checklist tracks
delivery across Framework and Extensions; do not copy it as every ticket's acceptance
gate. Assign external-consumer checks in later phase lists to Extensions tickets and
corresponding generic Framework checks to Framework tickets.

In every ticket, distinguish shared implementation context from ticket acceptance.
Carry applicable shared contracts, constraints, and exclusions in the ticket body,
but list its own acceptance checks and explicit dependencies separately. Checks owned
by other tickets or repositories remain contextual guidance with their ownership
identified; copying them into a handoff does not make them completion gates for this
ticket. For example, a Framework diagnostic-recording ticket must preserve the MCP
contract but does not wait for the Extensions consuming example to pass. This
distinction applies to every mandatory handoff below and does not remove any check
from its owning ticket or the overall delivery checklist.

Mocked tests verify integration behavior, not model accuracy, calibration, latency,
or cost. Example thresholds are illustrative application choices, not validated
production recommendations. Live model-quality evaluation is outside this roadmap's
acceptance gates; require appropriate evidence before making quality/threshold claims,
not before completing implementation tickets or the example.

**Mandatory ticket handoff:** Carry the owning repository, local acceptance commands,
explicit dependencies, mocked/no-real-credentials test requirement where applicable,
and the distinction between ticket acceptance and overall delivery into every derived
ticket/subticket. State explicitly that `loomspan-sidecar-test-suite` and live provider
evaluation are future/separate work and never gates for this roadmap. A link alone is
insufficient. Preserve the existing breaking-change and diagnostic/lifecycle handoffs.
This ownership decision is settled; do not recreate a cross-repository acceptance gate
on Framework tickets when translating phase-level evidence into work items.

Every ticket must contain the mandatory verbatim policy above, its owner/layers,
concrete behavior, explicit exclusions, dependencies, and relevant acceptance checks.
Keep Framework changes limited to the three listed generic enhancements. Native
removal, public-surface updates, and coordinated Console/MCP changes are necessary
parts of delivering those enhancements and the extraction, not optional cleanup.

Suggested work groups follow Phases 1–6: new public contract; boundary capture and
diagnostics; Console/MCP and compatibility-version updates; pack transport/config/
lifecycle; TypeSafe executor and external consumer; native removal and current
documentation. They may be combined or reordered coherently. Do not turn Phase 7
possibilities into v1 tickets. Require the existing public-surface test for production
type changes and the relevant Console/fixture checks described by repository guidance.

Console currently uses its exact release string for `consoleCompatibilityVersion`.
Use the actual repository release/version machinery when updating the relationship;
do not invent independent version negotiation. New Java/Go/TypeScript readers,
writers, fixtures, and MCP tools must agree. Successful evidence includes an external
consumer using only supported API, correct full native responses, safe diagnostics
with Console absent, and Console/MCP inspection of the same diagnostic records.

## V1 completion gate: implementation complete

Overall v1 delivery is complete when the following checklist passes across Framework
and Extensions. This is project tracking, not a shared acceptance gate for individual
tickets; apply the repository-local ownership rules above. Broader sidecar-suite tests
and live provider evaluation are expressly excluded from this checklist.
Locally installed or staged Maven artifacts are sufficient; record the exact tested
builds/versions. TypeSafe is the complete v1 provider scope. Gemini and OpenAI are
planned follow-up additions to the same extension, with separate requirements work
after the repository/structure, native TypeSafe removal, and one solid completed
TypeSafe example are in place. They impose no implementation or acceptance requirements
on v1; no further provider contracts are settled here.

- [ ] Implement the agreed generic Framework capabilities and remove native TypeSafe support.
- [ ] Provide the separate Decisions Maven artifact with the TypeSafe executor.
- [ ] In `loomspan-extensions`, verify the artifact through its small consuming example
  against identified Framework/Console builds, using a local stub HTTP provider and
  deterministic mocked responses without real API keys or live provider access.
- [ ] Verify Console/MCP inspection of recorded evidence and execution without Console.
- [ ] Provide one runnable recipe and current documentation matching those tested builds.
- [ ] Pass required tests, including public-surface and Framework/Console compatibility checks.

Track artifact publication and product releases separately from this implementation gate.
No simultaneous release, automated cross-repository pipeline, or public Maven publication
is required to pass it. Maintain coherent contracts and compatibility versions in the
tested builds. Do not describe a dependency as publicly available until it is published.

## Current implementation and constraints

- `src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java` owns the
  native declaration, input substitution, request checks, and descriptive output
  schema. Its current primitives are Choice, Noul, and Score.
- `TypeSafeExecutor.java` owns native evaluation attempts inside the captured
  mission: retries, provider-attempt reservations, response usage, trace evidence,
  and lifecycle checks. `TypeSafeClient.java` owns HTTP resources and connects
  cancellation to the physical request.
- `internal/provider/ProviderConnectionRuntime.java` directly references the
  TypeSafe client. Catalog parsing, driver configuration, dispatch, and diagnostics
  also recognize TypeSafe specially.
- Native results preserve the entire object-valued `answers` payload. Generated
  output metadata is descriptive, not enforced answer validation. Existing output
  bindings can depend on paths such as `/repeat/noul`.
- `api/SkillExecutorInvocation.java` is a supported five-component public record:
  input, skill configuration, executor configuration, skill name, and executor
  configuration generation ID. It exposes no provider accounting or trace writer.
- Executor-backed skills already participate in input/access checks, composition,
  captured configuration, and ordinary execution observation. Executor clients,
  retries, and resources remain application-owned; resource retirement can use the
  documented generation retirement notifications.

Source and behavior anchors include `TypeSafeSkillIntegrationTest`,
`TypeSafeDeclarationTests`, `TypeSafeRegisteredInspectionIntegrationTest`,
`LoomspanPublicSurfaceArchitectureTest`, and the current
[TypeSafe authoring guide](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

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

The currently agreed Framework enhancement scope is:

- Versioned invocation packages and exact supported parameter-type registration,
  including public-surface policy/tests and direct replacement of the old handoff.
- Invocation-scoped plain-message/JSON diagnostics, automatic boundary capture, one
  record per captured call, bounded non-breaking fallbacks, and existing Console/MCP inspection support.
- Simple read-only cancellation status and remaining-deadline accessors.

Existing invocation, configuration publication, and generation retirement facilities
are reused. No TypeSafe-specific startup validator, credential resolver, generated
output-schema hook, provider registry, or provider-accounting API is authorized by
this extraction. Numeric handling remains unchanged. Reduced output metadata and
runtime binding checks are accepted for v1. Response handling stays pack-owned;
existing Framework runtime binding checks remain. The TypeSafe executor does not
enforce provider response schemas or answer semantics.

## Architecture boundaries

| Owner | Responsibility |
| --- | --- |
| Framework | Invocation identity, access boundaries, operation lifecycle, capture controls, diagnostics, and live cancellation/deadline accessors; no new provider accounting API in v1 |
| Console | Optional developer inspection of operations, status, timing, usage, and evidence for debugging and troubleshooting; no runtime role |
| Executor pack | Provider clients, protocol mapping, capability checks, configuration interpretation, retries, and result contract |
| Application | Dependency selection, credentials/configuration, callable skill declarations, access policies, and business actions |
| Website catalog/cookbook | Discovery, compatibility information, executable examples, and installation guidance |

Keep executor declarations on the existing `executor:` / `config:` path. Do not
introduce a core `decisions:` grammar or require core to understand decision
questions. Package-specific configuration is interpreted by the package.

The proposed observation capability is an invocation-scoped public API, not a
provider SPI or internal bean replacement contract. No pack may import
`ai.loomspan.internal` or rely on autoconfiguration machinery as an extension API.

One extension contract version covers execution and diagnostic presentation;
extension authors must not manage independent execution/presentation compatibility
versions. Framework recognizes the new v1 contract rather than merely accepting a
version field. Legacy support and future overlap machinery are outside this effort.
Any future optional display hints may fall back to generic inspection; unsupported
required execution behavior must fail explicitly. Ordinary
descriptive documentation changes do not require a new contract version.

The phases below organize the work, not a requirement to build a complete extension
platform before trying it. Prove the smallest working Decisions slice first. Use a
non-AI fixture to check generality; a second published pack, rich catalog, presentation
component library, and speculative future-version adapters are not v1 prerequisites.

## Accepted v1 TypeSafe behavior changes

- Return the complete native TypeSafe JSON response, rather than extracting
  `answers`. Migrate `/repeat/noul` bindings to `/answers/repeat/noul`; v1 has no
  legacy answers-only mode or normalized multi-provider result.
- TypeSafe already returns JSON. Return its complete native JSON response, preserving
  its content, including unknown fields and native refusals, without extracting or
  reshaping it. HTTP-response-to-Java conversion is an implementation detail using
  the HTTP client or existing JSON tooling; no additional parsing step is needed if
  the client already supplies that value. No separate JSON specification or new
  Framework capability is required.
  Classify transport failures and HTTP status first; retry eligible failures only
  under the pack's bounded retry and lifecycle policy, otherwise fail execution.
  Error-body diagnostic capture or decoding must not change that classification.
  For successful HTTP responses, decode and return the complete native JSON; empty
  or malformed JSON fails execution with no retry solely for decoding failure.
  HTTP/transport failures are not successful results. Do not enforce provider response schemas,
  answer completeness, confidence ranges, or other answer semantics. Applications
  interpret returned data; diagnostic limits must never truncate the business result.
  Preserve valid JSON through the actual Java executor return path, including JSON
  null and string responses. JSON `null` is not an empty HTTP body. The existing
  Java invoker converts Java null to empty output and passes Java strings through
  as raw text; the pack must choose a return representation that preserves JSON
  values across that boundary. This requires no new Framework behavior, parsing
  contract, or provider response-schema enforcement.
  Accept loss of generated descriptive output metadata and some early binding checks;
  use existing runtime binding checks. No new schema declaration or enforcement.
- Keep existing executor configuration numeric handling. Precise native declaration
  decimals may round when moved into executor configuration; document this accepted
  difference without adding a precision mode or requiring exact-decimal parity.
- Pack requests and retries will not reserve the native mission-wide provider-attempt
  budget. Existing invocation-level controls remain; this is not equivalent quota coverage.
- Provider usage may appear in diagnostics but will not automatically contribute to
  authoritative Framework totals. Keep existing usage calculations and fields unchanged;
  label Console totals "Framework-accounted usage" and explain the same scope in API
  documentation and MCP usage descriptions. Executor-managed provider calls are excluded.
  A zero counter means zero Framework-accounted usage, not measured zero executor usage.
  No completeness indicator, executor usage-reporting API, or diagnostic aggregation in v1.
- Cancellation/deadline accessors support cooperation; polling cannot abort blocked
  I/O. Existing interruption and late-write fencing remain, but v1 has no replacement
  for the native lifecycle-to-HTTP cancellation callback. The pack owns client timeouts.
- The pack owns bounded retries and a reusable HTTP transport with shutdown cleanup.
  Apply captured URL/API key and timeout per request; no client cache or transport
  replacement on configuration generation changes.

These changes supersede the earlier native accounting/physical-cancellation parity
requirement. Test and document the new behavior. Native YAML, configuration, result
paths, and public enum values may be removed/replaced under the breaking-change
policy above. This document itself makes no runtime changes.

## Phase 1 — Finalize the reviewed public contract and removal inventory

- [x] Review the current TypeSafe implementation and agree dispositions for all eight
  findings. Evidence is in the source review; no implementation/test completion is implied.
- [ ] Complete the cross-layer removal inventory using the finished source review;
  check native TypeSafe coupling across Framework, Console, MCP, fixtures,
  documentation, configuration, dependency management, and public catalog values.
- [ ] Carry the scope rule above into future implementation tickets. Tie each
  Framework API change to an explicitly listed generic roadmap enhancement; keep
  TypeSafe-specific checks and provider behavior in the pack.
- [ ] Carry the already-agreed required/changed/retired behavior into the removal
  inventory and acceptance checks. Include direct/child invocation, answer fidelity, retry ownership,
  physical cancellation, generation retention, and exact/unavailable usage.
- [ ] Design the smallest invocation-scoped diagnostics call for plain messages
  and optional structured JSON data. Defer arbitrary objects, implicit `toString()`,
  custom presentation descriptors, and extension-managed operation handles.
- [ ] Define the versioned package contract, provisionally
  `ai.loomspan.api.extensions.v1.SkillExecutorInvocation`, and colocate its public
  capability types. Recognize exact supported parameter types at registration;
  reject unsupported types. The Java parameter selects the invocation contract;
  no separate pack manifest or manifest validation is required.
  No separate version annotation or per-call version switch is needed.
- [ ] Implement exact v1 contract recognition without future-version adapters or
  legacy support. Use shared internal execution machinery.
- [ ] Exercise the design against decisions and a non-AI example, such as an HTTP
  request or document conversion. Avoid provider-shaped generic abstractions.
- [ ] Design a Framework-owned invocation class with stable read-only accessors for
  existing captured data, diagnostics, and live cancellation/deadline information.
  Settle exact signatures and construction without exposing internal lifecycle state.
- [ ] Replace the existing unversioned invocation record and registration path.
  Update every construction/use site and test; add no old-constructor shim or adapter.
- [ ] Explicitly update public-surface policy and architecture allowlists for the
  selected versioned packages: current policy only permits allowlisted top-level
  types directly in `ai.loomspan.api`. Add README and supported-surface tests;
  arbitrary classes in a versioned package do not become supported API.

Exit: reviewed new API/behavior contract under the mandatory breaking-change policy. Names and
method signatures are not fixed by this roadmap.

## Phase 2 — Framework-managed executor observation

### Settled decision: diagnostic failure-containment boundary

The non-breaking guarantee applies to the new `diagnostics.info/warn/error`
facility and any new diagnostic recording introduced by this work. Ordinary
validation, capture, serialization, recording, limit, fallback-notice, and fallback-
logging failures must be contained: they must not change the executor's result or
replace its original failure. This covers new diagnostic recording wherever it is
called, including Framework-authored diagnostic notices.

Automatic start/stop, input/output, and escaping-exception capture reuse Framework's
existing observation paths and retain their existing execution/failure semantics.
This effort does not extend the new guarantee retroactively to that infrastructure
or authorize a broader observability rewrite. Business request/result preparation
and serialization remain execution work and may fail execution. Caller argument
evaluation before a diagnostic method is entered is also outside its guarantee;
JVM-wide failures remain outside it.

This boundary is agreed, not an open design question. Future audits and tickets must
apply it rather than reopen whether the new guarantee covers existing automatic
observation. Revisit only for an explicit scope change or concrete implementation
evidence that violates this boundary. Remaining diagnostic signature, limit, and
concurrency choices do not reopen it.

- [ ] Automatically capture executor start/stop, elapsed time, outcome, invocation/
  skill/executor identity, and executor configuration generation ID.
- [ ] Capture input/output through existing evidence paths and controls, including
  existing handling of resource-valued inputs; do not add arbitrary object traversal.
- [ ] Capture escaping failure type, message, diagnostic cause, and existing failure
  classification. Preserve `RecoverableSkillException` semantics: corrective feedback
  in standard tool calling, propagation on direct invocation, and termination in
  explicit step planning. It never automatically replays work. Other application
  failures remain terminal; caught failures/retries need explicit diagnostic events.
  Keep existing automatic failure recording/classification; do not map escaping
  exceptions to diagnostic levels or add duplicate `warn`/`error` diagnostic calls.
- [ ] Record one diagnostic record per captured call within the existing execution
  frame, with plain message, optional
  structured JSON data, Framework-supplied timestamp, invocation association, and
  existing trace sequence. Snapshot data; define concurrent capture and writes after
  closure without introducing a separate diagnostic sequence or execution scope.
  Use three fixed diagnostic levels selected by `info`, `warn`, and `error` methods,
  each accepting a message and optional JSON. No missing/default or custom levels.
  These levels apply only to developer-authored calls from within a SkillExecutor.
  Framework capture-failure and limit notices are separate system notices without
  these levels; standard Java fallback logging remains separate. Diagnostic severity
  must not change the execution outcome or trigger retries.
- [ ] Define supported JSON containers/scalars without invoking arbitrary custom
  collection implementations or serializers. Consult the companion serialization
  research; Jackson is a building block, not a hard object-execution sandbox.
- [ ] Bound message/data bytes, nesting, collection traversal, event counts, and total
  capture per invocation. Detect cycles/unsupported values without implicit conversion.
  Keep valid JSON and explicit unavailable/truncated/capture-failure states.
- [ ] Use Jackson for bounded JSON capture. Contain ordinary validation, serialization,
  recording, and limit failures so diagnostics never change the business result.
- [ ] On capture failure, emit a small Framework notice with the reason and original
  message when it fits, plus a bounded warning through standard Java logging. Use a
  separate fixed fallback envelope; never recursively serialize or stringify rejected
  data. Contain ordinary failures in both fallback reporting paths.
- [ ] Reserve bounded capacity for one invocation-limit notice, then suppress further
  diagnostics. Coalesce/rate-limit repeated warnings; disabled capture must not create
  per-call warnings. Framework notices distinguish known capture conditions when
  recorded; delivery remains best effort, and Console does not infer missing states.
- [ ] Document best-effort notice delivery when recording fails, and the limits of the
  guarantee: caller argument evaluation and JVM-wide failures are outside it. Defer
  arbitrary DTO serialization and nested operation handles.
- [ ] Preserve content fidelity. Do not add sensitivity classification or automatic
  masking. Authentication, authorization, capture controls, and resource limits
  remain the boundaries. Retain the existing `ExecutionJournalProjector` exception
  only for the derived journal and observer callback; do not broaden it.
- [ ] Test isolation between invocations, concurrent operations, closure/failure,
  disabled capture, fidelity, and explicit truncation using public invocation.
- [ ] Verify the new diagnostics preserve successful executor results and original
  executor failures despite ordinary diagnostic serialization, recording, fallback,
  and fallback-logging failures. Retain existing automatic-observation semantics;
  do not require a broader observability rewrite. Test diagnostic-budget exhaustion, bounded notices,
  warning coalescing, and absence of recursive error reporting.

Exit: Framework-local tests prove supported diagnostic reporting with Console absent.
The Extensions-owned consumer verifies the same behavior in its own integration tests;
that downstream verification does not hold Framework tickets open.

## Phase 3 — Generic Console integration

Console is solely a development and troubleshooting tool. Framework records the
execution independently; Console observes it when installed and connected. The
v1 view combines automatic boundary evidence and extension-authored message/JSON
records under the same extension contract version. No presentation descriptor is
required from the pack.

- [ ] Walk through successful execution, recoverable/terminal failures, and a caught
  provider failure followed by retry using the simple diagnostic records.
- [ ] Carry operations and evidence through trace serialization, authenticated
  inspection routes, and existing MCP/Console readers where applicable.
- [ ] Integrate new diagnostic record types and content into the existing
  `LOOMSPAN_query_trace_records` and `LOOMSPAN_read_trace_content` tools, following
  the settled contract below. Reuse existing summaries and retrieval behavior;
  do not add diagnostic-specific tools, severity filters, or pagination machinery.
- [ ] Display automatic timing, generation ID, input/output, and failure evidence
  alongside individual chronological diagnostic records with expandable JSON, grouped
  beneath the existing execution frame. Bound rendering work; grouping must not merge records.
- [ ] Console reports recorded facts; it never synthesizes results or infers execution
  outcomes from diagnostic levels, message text, arbitrary payload fields, or timing.
  Display Framework-recorded invocation outcome/classification and executor-authored
  messages/JSON as recorded. Developers use that evidence to distinguish a caught
  request failure/retry from an invocation failure or a negative business decision.
  No typed provider-attempt tracking or provider-specific interpretation is needed in v1.
  Grouping, filtering, and factual aggregates do not create new semantic outcomes.
- [ ] Display executor-authored diagnostic messages, severity, and JSON as recorded;
  the SkillExecutor developer determines their content and meaning. Display Framework
  execution facts and capture notices separately; capture notices have no developer
  severity. Console does not generate explanatory error messages or infer capture
  state from missing evidence. Display disabled, unavailable, truncated, or failed
  capture only when explicitly recorded by Framework. Notice delivery is best effort:
  if recording fails and no notice survives, Console has no cause to explain.
- [ ] Label totals "Framework-accounted usage" in Console and explain that executor-managed
  provider calls are excluded. Document the same scope in API and MCP usage descriptions;
  retain existing usage fields and calculations without a new completeness indicator.
- [ ] Change Framework-to-Console compatibility version as required and update
  Java/Go/TypeScript transport, MCP readers, Console, and fixtures together. Remove
  obsolete shapes; no historical readers, old-version support, or negotiation.
- [ ] Verify Console absence or presentation failure cannot affect execution/results.
  Defer display hints, custom layouts, and extension-specific renderers beyond v1.
- [ ] Add representative fixtures and reader/UI tests, including a non-AI executor.
- [ ] Verify existing MCP type/frame filtering, pagination, inline content, and content
  retrieval work for the new records and system notices, including concurrent events.
  Follow the acceptance checks and mandatory ticket handoff below.

Exit: Framework/Console-local fixtures prove generic executor troubleshooting and
Console-free execution. The Extensions-owned example supplies downstream pack proof
without blocking these tickets. Pack-supplied UI code,
custom renderer loading, and a broad presentation component library are deferred.

### Settled decision: reuse existing MCP record inspection

Executor diagnostics are new trace **records within existing execution frames**, not
new execution frames or a separate stream. `TraceFrameType` identifies an execution
scope; `TraceRecordType` identifies a recorded event. Add a diagnostic record type
(provisional spelling `EXECUTOR_DIAGNOSTIC_RECORDED`) for captured `info/warn/error`
calls, and distinguish Framework capture-failure/limit notices with a system-notice
record type. Final enum spellings are implementation details. System notices have no
developer severity. Use existing trace identity, frame association, timestamps, and
trace sequencing; do not open/close a new frame for every diagnostic call.

The existing MCP tools already supply the required retrieval behavior:

- `LOOMSPAN_query_trace_records` supports record-type/frame filters, bounded pages,
  continuation, logical/physical representations, and optional bounded inline content.
- `LOOMSPAN_read_trace_content` reads retained content through the existing opaque
  content reference and bounded byte-range/continuation machinery.
- Existing trace/frame summaries, artifact handling, retention, and access controls
  remain in use. No new execution summary, live tailing contract, or retention system
  is required for diagnostics. Existing cursor semantics remain unchanged.

Required implementation changes are limited to integrating the new record vocabulary
and content into those paths, plus corresponding validation, fixtures, and documentation:

1. Update Java/Go/TypeScript record-type vocabularies and affected schemas coherently.
   Console's Go reader rejects unknown record types; merely emitting a new Java enum
   value is insufficient. Keep the coordinated compatibility-version policy.
2. Put the diagnostic message, developer severity, optional supplied JSON, and explicit
   capture-state information in the record's existing `data` content path. Put system
   notice content there too, without developer severity. Preserve developer JSON as
   supplied within the bounded capture contract; do not derive execution outcomes
   from it. Choose the small payload shape during implementation.
3. Reuse existing content descriptors, inline-content limits, content references, and
   content reads. The MCP record-summary DTO does not automatically expose arbitrary
   metadata: storing the message or severity only in metadata would not make it
   available through the normal summary/content path. No new summary fields are
   required by this decision; any proposed addition needs a concrete demonstrated
   requirement rather than speculative convenience.
4. Keep developer `error` diagnostics and system notices distinct from automatic
   execution failures. Do not route them through `ERROR_RECORDED` failure semantics,
   synthesize provider attempts, or change status, retries, usage, or accounting.

Do not add MCP tools, diagnostic-only endpoints/indexes/stores, a parallel diagnostic
query service, new cursor/sequence formats, severity filtering, special filter bypass
for notices, or a second content-retrieval protocol. Existing filters apply normally:
to select diagnostics and notices together, request both record types. No new generic
metadata-query language or broad inspection refactor is part of v1. If implementation
reveals a concrete limitation, document the failing case and propose the smallest
change separately before expanding this agreed scope.

Source anchors to inspect before coding (current implementation, not public APIs):

- [Java record vocabulary](../../../src/main/java/ai/loomspan/internal/core/TraceRecordType.java)
  and [record envelope](../../../src/main/java/ai/loomspan/internal/core/TraceRecord.java).
- [Go closed vocabulary](../../../loomspan-console/internal/traceanalysis/enums.go)
  and [record parser](../../../loomspan-console/internal/traceanalysis/parser.go).
- [Existing record query/content descriptors](../../../loomspan-console/internal/traceanalysis/query_records.go).
- [MCP tool registration and mappings](../../../loomspan-console/internal/mcpadapter/traces.go),
  [record-summary DTO](../../../loomspan-console/internal/mcpadapter/trace_contracts.go),
  and [output schemas](../../../loomspan-console/internal/mcpadapter/output_schemas.go).

Acceptance: fixtures must show mixed existing records, developer diagnostics, and
system notices in one trace. Through the existing MCP tools, verify type/frame
selection, ordering and continuation without duplicates or omissions, bounded inline
content and content-reference reads, message/severity/JSON fidelity, and explicit
Framework-recorded capture truncation/unavailability. Verify Console displays authored
messages and recorded states without generating explanations for missing evidence;
absence of a diagnostic or notice establishes no capture-failure cause.
A retained complete notice about truncated capture
does not mean discarded evidence is recoverable. Verify `error` diagnostics do not
create execution failures, and no new tools or retrieval contracts are introduced.
Run applicable Console checks and the coordinated Java fixture corpus checks.

**Mandatory ticket handoff:** Every ticket or subticket touching diagnostic recording,
trace consumption, Console presentation, or MCP inspection must carry this section's
record-versus-frame distinction, named existing tools/code anchors, permitted changes,
explicit exclusions, and applicable acceptance checks in its own body, alongside the
existing verbatim breaking-change policy. Separate shared context and checks owned
elsewhere from this ticket's acceptance checks and explicit dependencies, as required
by the ownership rules above. A link alone is insufficient. Do not replace these
details with a vague instruction to "add diagnostic inspection" or "add pagination."
This is a settled reuse decision; future audits must not reintroduce a separate MCP
design merely because diagnostics are a new record type.

## Phase 4 — Cooperative lifecycle and explicit migration behavior

V1 supplies simple cancellation/deadline accessors, not provider-attempt admission,
authoritative usage reporting, or physical-request cancellation registration.

### Settled cancellation contract and mandatory ticket handoff

The following behavior is agreed, not an open product-design question:

- Framework supplies live, read-only cancellation status and remaining execution
  time through the versioned invocation. Conceptual signatures are
  `boolean isCancelled()` and `Optional<Duration> remainingTime()`.
- Empty remaining time means no deadline. Expiry returns zero, never a negative
  duration. These are observations of Framework execution state, not mutable
  cancellation controls or mechanisms that abort external I/O.
- The pack checks cancellation, thread interruption, and remaining time before
  requests and retries, and cooperates during retry waits. Observed cancellation,
  interruption, or exhausted execution time prevents another send/retry. Preserve
  interruption; do not swallow it and continue retrying.
- The pack owns a total HTTP request timeout capped by remaining execution time,
  with separate inactivity limits as needed. A progressing response must not evade
  the total timeout. Retry settings and HTTP-client selection are pack implementation
  choices; they do not authorize another Framework timeout subsystem.
- Existing Framework cancellation authority, interruption, failure handling,
  late-write fencing, and generation/resource retention remain in force. In-flight
  physical I/O may finish later, depending on client timeout/interruption behavior.
  Do not promise immediate remote inference or billing cancellation. Keep physical
  work/resource ownership accounted for as already required by this roadmap.
- No physical-request cancellation callback, provider-accounting API, internal
  lifecycle exposure, new public cancellation exception hierarchy, or restoration
  of native physical-cancellation parity is part of v1.

Implementation guidance from source inspection: `MissionWorkExecutor` currently
enforces mission timeout through timed `Future.get(...)`; `MissionLifecycle` also
tracks cancellation-cleanup grace, and `FrameworkExecutionLifecycle` owns shutdown
timing. Remaining execution time must represent the applicable execution budget,
not cleanup grace or a fresh budget starting at executor entry. Make the minimal
internal deadline bookkeeping needed alongside existing enforcement, preserving
existing parent/shutdown constraints and timeout behavior. Reuse these owners rather
than adding a parallel scheduler or cancellation state machine. These internal code
anchors are for Framework implementation, not imports permitted in the pack.

Acceptance checks must exercise live state changes, no-deadline/zero-expiry values,
cancellation or interruption before a send and during retry waits, timeout capping,
a slow progressing response, and late physical completion with existing fencing and
resource lifetime. Reuse relevant existing lifecycle tests and prove the pack behavior
through the external consumer. The test for remaining time must distinguish execution
budget from cleanup grace and ensure executor entry/retries do not reset that budget.

**Mandatory ticket handoff:** Every lifecycle/accessor/pack-transport/retry ticket and
subticket must carry the settled behavior, implementation guidance/code anchors,
exclusions, and applicable acceptance checks above in its own body, alongside the
existing breaking-change policy. A link alone or "finalize cancellation semantics"
is insufficient. Final Java declarations, thread-safe implementation, client choice,
and numeric timeout/retry defaults are bounded implementation tasks, not permission
to reopen the cooperative guarantee. No new post-return accessor restriction or
post-completion monitoring guarantee is approved here; any concrete need for either
must be identified separately rather than reopening the entire cancellation design.
Future audits must distinguish unfinished implementation from unsettled behavior;
revisit this contract only for an explicit scope change or concrete contradictory
implementation evidence.

- [ ] Implement live read-only cancellation status and remaining-time accessors;
  conceptual signatures are `boolean isCancelled()` and
  `Optional<Duration> remainingTime()`. Empty means no deadline; expiry returns zero,
  never negative. Implement thread-safe reads using existing execution ownership
  and the settled contract above; do not reopen the cooperative guarantee.
- [ ] Verify cancellation/interruption/deadline checks before requests and retries,
  and cooperative retry waits. Polling does not abort an already-blocked HTTP request.
- [ ] Keep HTTP timeout handling inside the pack: enforce a total request timeout
  capped by remaining execution time when present, with client-specific inactivity
  limits as needed. Test slow progressing responses; inactivity limits alone do not
  enforce total deadlines. Add no Framework HTTP API or cancellation callback.
- [ ] Document that in-flight cancellation may not stop work immediately and depends
  on client timeout/interruption behavior; do not promise remote inference/billing
  cancellation. Existing Framework interruption and late-write fencing remain.
- [ ] Preserve existing invocation-level controls and late-write fencing. Validate
  late physical return and resource lifetime without claiming remote cancellation.
- [ ] Assign bounded retry policy and SDK retry configuration to the pack. Verify
  no implicit cross-provider fallback; do not add native attempt reservations in v1.
- [ ] Keep optional diagnostic usage separate from authoritative totals. Preserve
  native usage details when captured; do not substitute zero for unreported executor usage.
  Native provider usage remains available in full responses and diagnostics when captured.
  A zero Framework counter is valid within its documented accounting scope.
- [ ] Verify a mixed mission: Framework-accounted model calls contribute to existing
  totals, executor-managed calls do not, and Console/API/MCP descriptions make this scope
  clear. Diagnostic usage must not affect totals; no new usage-reporting API is required.
- [ ] Reuse a pack-owned HTTP transport/pool and close it at application shutdown.
  Apply effective URL, authorization, and timeout per request; no authenticated-client
  cache, shared mutable credential defaults, or per-generation transport recreation.
- [ ] Verify concurrent distinct endpoints/keys and publication during active requests:
  old work retains captured settings, new work gets updated settings, and neither
  changes the other request. Generation-retirement transport cleanup is unnecessary.
- [ ] Verify accepted behavior changes in a consuming application: pack requests do
  not consume the native shared provider-attempt budget, diagnostics do not update
  authoritative usage, and cancellation guarantees are cooperative. Include response
  decoding failure after successful HTTP, unavailable usage, and Console absence.
- [ ] Document the new behavior and removals in current usage docs/release notes.
  Remove native TypeSafe as part of the coordinated replacement; no migration
  guide, transitional support, or period of supported coexistence is required.

Exit: the minimal lifecycle facilities and explicitly reduced v1 guarantees are
verified. Full native accounting/physical-cancellation parity is not a v1 completion gate.

## Phase 5 — Prove the separate Decisions Executor Pack

- [ ] Implement `loomspan-decisions` in the separate `loomspan-extensions` repository
  whose initial code/test/example structure is established before ticket generation,
  using only supported Loomspan API, its own POM, and independent versioning.
  Publication is tracked separately from the implementation-complete gate.
  Start with one artifact; add modules only when practical needs justify them.
- [ ] Implement the TypeSafe executor. Preserve its provider request semantics and
  complete native JSON responses; no unified Decisions request/result API in v1.
  Follow the companion research document and recheck the current TypeSafe contract.
  Gemini and OpenAI will be added to this same extension in follow-up work; do not
  design their requirements, implementations, or acceptance tests in this phase.
- [ ] In the TypeSafe executor, build/validate/serialize the request once before the
  first send and reuse its body bytes across retries. Resolve endpoint/credentials
  once from captured configuration; read supported local resource content during
  construction, not on retry. Remote URL content is not frozen. Keep cancellation,
  interruption, remaining-time checks, and timeout capping live per attempt. Verify
  identical retry bodies and stable settings during publication. This is internal
  TypeSafe executor design only, not a general SkillExecutor rule or Framework API;
  other executors choose their own design. It does not imply remote deduplication.
- [ ] Implement presence-based `api_key` lookup: captured skill configuration first,
  captured executor configuration second, otherwise error before send. Reject present
  null/blank/non-string values without fallback; preserve valid values. No implicit
  environment, placeholder, or native connection-registry resolution.
- [ ] Implement presence-based `base_url` lookup: captured skill configuration,
  captured executor configuration, then provider default. Reject invalid present
  overrides before sending; preserve path prefixes when appending endpoint paths.
  Keep provider/route selection explicit, with no discovery or automatic fallback.
- [ ] Test endpoint precedence, invalid overrides, provider defaults, prefixed URLs,
  and publication updates. Request construction must use the effective endpoint.
- [ ] Test skill/executor publication updates and distinct effective credentials;
  request headers must use captured credentials without changing shared defaults.
- [ ] Provide configuration checks, capability errors, source-preserving diagnostics,
  deterministic provider fixtures, and supported-version consumer tests.
- [ ] Prove direct invocation, child composition, output bindings, and Console
  observation from an application that imports a locally installed, staged, or published dependency.
- [ ] Document pack result shapes/paths and test successful and invalid parent
  bindings using existing runtime behavior. Verify complete native response
  preservation through the actual invocation return path, including unknown
  fields/refusals and JSON null/string responses, and no decoding-only retries for
  successful HTTP responses. Verify a retryable HTTP failure with an HTML or malformed
  JSON error body still follows the HTTP retry policy, subject to lifecycle limits;
  do not add schema metadata hooks or Framework output enforcement for this pack.
- [ ] Provide one runnable recipe using complete native responses and update all
  examples/bindings to the new paths. Verify additional provider fields are retained;
  provide no legacy result mode or migration-support deliverable.
  Use the `default` namespace; keep namespace guidance in Framework documentation.
  Keep the small working example in `loomspan-extensions` and exercise it as part of
  that extension's integration tests with a local stub HTTP provider and mocked model
  responses. No real credentials or live services are required to pass these tests.
- [ ] Use the POM for artifact identity, release version, and Framework dependency;
  use standard directories for documentation/examples and a small documentation index.
  Keep exact installation and tested environment details in release-aligned documentation.
  No separate manifest, manifest schema, or manifest validation is part of v1.
  Do not make a generated website or elaborate catalog tooling a v1 completion gate.

Exit: the external artifact works in a consuming application; the final coordinated
change removes native support. No transitional compatibility release is required.
This phase is coordinated across repositories; this roadmap does not create or
publish the separate project.

## Phase 6 — Remove native TypeSafe implementation from core

- [ ] Delete native `typesafe:` declarations/parser branches, connection driver/model
  configuration support, dispatch, clients, resource coupling, and dependencies used
  exclusively by native TypeSafe. Reimplement provider behavior in the pack.
- [ ] Remove `SkillKind.TYPESAFE` and other obsolete public/internal surfaces; update
  all usages and allowlists. No deprecated aliases, translators, or compatibility shims.
- [ ] Replace old examples and consumer docs with current executor-pack usage. Remove
  obsolete TypeSafe-specific Console/MCP handling and historical shape assumptions.
- [ ] Apply the mandatory ticket policy across the coordinated Framework/Console/MCP
  changes, including compatibility-version updates and matching fixtures.
- [ ] Update fixtures and supported-surface tests to describe the final design.
  Run `LoomspanPublicSurfaceArchitectureTest` after production type changes, plus
  focused executor, lifecycle, usage, binding, and Console checks.

Exit: core has no native TypeSafe execution dependency; Framework-local tests and
documentation reflect the replacement, without legacy or migration support. Pack
implementation and its example are verified by Extensions-owned tickets, not gates
on this removal phase or its Framework tickets.
Overall v1 completion requires the shared implementation-complete checklist above;
public artifact publication and product releases are tracked separately.

## Phase 7 — Grow the pack ecosystem after v1 feedback

Broader `loomspan-sidecar-test-suite` integration testing is separately tracked future
follow-up, not a gate for any work or overall completion in this roadmap.

- [ ] Plan Gemini and OpenAI additions to the same Decisions extension after the
  TypeSafe foundation is complete. Review each provider's unique requirements then;
  only their future inclusion in this extension is decided by this roadmap.

- [ ] Reconsider optional accounting or physical cancellation facilities only when
  actual pack use justifies a general contract; neither is a v1 prerequisite.

- [ ] Expand the documentation index only as adoption requires. Consider additional
  catalog metadata only for a demonstrated need; no manifest is planned for v1.
  Keep machine-interpreted diagnostic presentation within the single extension
  contract version; do not add a separately selected presentation schema version.
- [ ] Build catalog aggregation/search and additional cookbook examples when useful.
  Route progressive AI discovery through the same release-aligned documentation.
- [ ] Prove a second non-decisions pack can follow the conventions without core
  provider-specific changes.
- [ ] Add optional dependency/configuration scaffolding only when useful. Pin
  released versions and retain manual Maven installation as a complete path.

Runtime JAR downloading, hot replacement of executable Java code, a custom
dependency resolver, provider discovery, and automatic cross-provider fallback are
outside the initial scope.

## Remaining implementation choices within settled scope

The earlier six audit decisions and the five follow-up clarifications are settled.
The items below concern implementation details
and sequencing, not new approval gates or invitations to revisit those decisions.

1. Exact versioned package names and invocation class/accessor signatures. Versioned
   parameter types select the new contract; old handoff support is not required.
2. Implement the settled cancellation/deadline contract: finalize Java declarations,
   thread-safe reads, and minimal bookkeeping of the existing execution deadline.
   This is implementation work, not an open cancellation-policy decision. Apply the
   Phase 4 contract and ticket handoff; do not add post-return restrictions or a new
   monitoring facility as an assumed requirement.
3. Implementation sequencing across repositories and coordinated compatibility-version
   updates. Publication/releases are tracked separately from the settled implementation-
   complete gate. Removal policy is settled: no native TypeSafe or compatibility support
   remains in the final design.
4. Exact Maven group coordinates, maintainer/release ownership, release policy, and tested Framework/
   Java/Boot combinations. The starting artifact name is `loomspan-decisions`.
5. Pack request-validation details and error messages. Response handling is settled:
   return TypeSafe's complete native JSON response without extracting or reshaping it.
   HTTP-to-Java conversion is an implementation detail, not a separate JSON contract;
   no provider response-schema enforcement or decoding-only retries for successful
   HTTP responses. Error-body capture/decoding does not change HTTP retry eligibility.
   Request-validation timing is settled: validate pack
   configuration and resolved requests at invocation before sending; no Framework
   registration hook or TypeSafe-specific startup checks are part of v1.
6. Exact diagnostics signatures (the three levels `info`, `warn`, and `error` are settled),
   supported JSON containers/numbers, diagnostic snapshot mechanism,
   late/concurrent-write behavior, and resource-limit values.
   Presentation hints and arbitrary Java-object conversion are deferred.
7. Pack-owned TypeSafe request binding/model selection, initial modality scope,
   retry settings/defaults, and total-timeout settings/client implementation. Native
   provider semantics, full responses, and TypeSafe's internal build-once/reuse decision
   are settled; illustrative request builders
   and JSON Pointer handling are not an approved shared Decisions language.

These are implementation design choices within settled scope. They are not reasons
to reopen rejected compatibility layers, provider normalization, precision changes,
startup domain-validation hooks, output schemas, caches, or namespace changes.
