# Executor packs and TypeSafe extraction roadmap

Status: agreed v1 direction and implementation roadmap; no implementation completed.
Date: 2026-11-15.

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
records the three-provider comparison, packaging direction, cookbook separation,
and website catalog proposal. Provider support and pack publication are work for
the separate project; this repository owns the framework changes and migration.

## Source review findings

The [TypeSafe migration review](typesafe-executor-pack-migration-review.md) maps the
current implementation to v1. Keep existing executor configuration numeric handling
for v1: loss of native exact-decimal configuration preservation is an accepted,
documented migration difference, not a blocker. No precision mode or special numeric
syntax is required. Credential lookup now uses `skillConfig.api_key` first, then
`executorConfig.api_key`, with an error if missing or invalid. Later domain-validation
timing is accepted as pack-owned invocation validation. Output validation stays
executor-owned; reduced output metadata and existing runtime binding checks are
also accepted for v1. The review also distinguishes HTTP
inactivity timeout from total deadlines; it does not automatically approve other
behavior reductions.

## Handoff to the next working context

Read this roadmap first, including its mandatory ticket policy. Then read the
[design/research](decisions-executor-pack-research.md) for provider evidence,
packaging, diagnostics, and catalog details, and the
[source review](typesafe-executor-pack-migration-review.md) for code/test anchors.
The roadmap's latest agreed scope and breaking-change policy take precedence over
historical native behavior described for comparison. The word "migration" in review
filenames means replacement analysis, not a migration-support deliverable.

Only these planning documents have been changed in this conversation. No production
implementation, tickets, new repository, Maven release, site, or live provider
evaluation has been created. Local links/formatting have been checked; production
tests were not run. The phase documents are currently untracked in Git and no commit
was made. Recheck status and local instructions when resuming; do not overwrite other
work or mistake existing native code/tests for a decision to retain that behavior.

Direction established by the user's roadmap request:

- Remove TypeSafe-specific functionality from core Framework.
- Develop the Decisions Executor Pack in its own project, distributed as a Maven
  dependency, containing three provider-specific executors for TypeSafe, Gemini,
  and OpenAI. Return complete native JSON responses; no unified Decisions API in v1.
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

Recommended next work: turn the agreed scope into concrete tickets and finalize the
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
| Provider API | Three provider-specific executors; complete native JSON responses | Provider docs stay useful; capabilities/confidence differ, so normalization would imply portability we cannot promise |
| Public handoff | Versioned package/type identifies one extension contract; captured maps/identity plus diagnostics and live cancellation/deadline accessors | Avoid a separate execution/presentation compatibility matrix; implement only new v1 and replace old APIs directly |
| Console | Optional development/debugging/troubleshooting observer | No Console presence, rendering, or logging failure may govern runtime execution |
| Diagnostics | Automatic boundaries plus plain messages/structured JSON, one frame per call | Framework knows both sides of the call; the pack explains intermediate work with a familiar logging interface |
| MCP | Summary, bounded frame pages/filtering, focused detail | An AI debugger can retrieve selected evidence without repeatedly loading one enormous transcript |
| Capture failure | Bounded separate notices and standard logging; ordinary failures never escape | Logging must not break work or recursively flood diagnostics |
| Serialization | Jackson, restricted JSON data, bounded capture; no arbitrary DTO or implicit `toString()` | Serializer depth limits do not sandbox getters, iterators, or custom serializers |
| Credentials/endpoints | Presence-based skill config override, then executor config; key missing means error, endpoint missing means provider default | Both maps are publishable; callers choose shared or per-skill settings without a Framework credential feature |
| Numeric values | Existing executor decoding stays | No demonstrated need for high-precision configuration justifies a new contract |
| Validation | Pack validates each invocation before sending | General executors cannot inherit native provider-specific startup checks; publish success is not domain validation |
| Output validation / metadata | Decisions pack validates JSON only and returns the complete response; existing runtime bindings remain | No duplicated provider response schema or generated output metadata |
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

Every ticket must contain the mandatory verbatim policy above, its owner/layers,
concrete behavior, explicit exclusions, dependencies, and relevant acceptance checks.
Keep Framework changes limited to the three listed generic enhancements. Native
removal, public-surface updates, and coordinated Console/MCP changes are necessary
parts of delivering those enhancements and the extraction, not optional cleanup.

Suggested work groups follow Phases 1–6: new public contract; boundary capture and
diagnostics; Console/MCP and compatibility-version updates; pack transport/config/
lifecycle; three provider executors and external consumer; native removal and current
documentation. They may be combined or reordered coherently. Do not turn Phase 7
possibilities into v1 tickets. Require the existing public-surface test for production
type changes and the relevant Console/fixture checks described by repository guidance.

Console currently uses its exact release string for `consoleCompatibilityVersion`.
Use the actual repository release/version machinery when updating the relationship;
do not invent independent version negotiation. New Java/Go/TypeScript readers,
writers, fixtures, and MCP tools must agree. Successful evidence includes an external
consumer using only supported API, correct full native responses, safe diagnostics
with Console absent, and Console/MCP inspection of the same diagnostic frames.

## V1 completion gate: implementation complete

V1 is complete when the following engineering checklist passes across the repositories.
Locally installed or staged Maven artifacts are sufficient; record the exact tested
builds/versions. The TypeSafe-first slice is an intermediate proof, not completion
of the agreed three-provider scope.

- [ ] Implement the agreed generic Framework capabilities and remove native TypeSafe support.
- [ ] Provide the separate Decisions Maven artifact with TypeSafe, Gemini, and OpenAI executors.
- [ ] Verify the artifact in a consumer application against identified Framework/Console
  builds, including deterministic provider fixtures.
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
placement is an ordinary configuration choice, not a migration roadblock. Examples
should explain the existing behavior rather than introduce a new relationship.

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
  frame per call, bounded non-breaking fallbacks, and Console/MCP inspection support.
- Simple read-only cancellation status and remaining-deadline accessors.

Existing invocation, configuration publication, and generation retirement facilities
are reused. No TypeSafe-specific startup validator, credential resolver, generated
output-schema hook, provider registry, or provider-accounting API is authorized by
this extraction. Numeric handling remains unchanged. Reduced output metadata and
runtime binding checks are accepted for v1; output validation belongs to the executor, not a new Framework enhancement.

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
- The Decisions pack validates response JSON only: decode and faithfully return the
  complete successful HTTP response, including unknown fields and native refusals.
  Empty or malformed JSON fails execution, with no retry solely for decoding failure.
  HTTP/transport failures remain failures. Do not enforce provider response schemas,
  answer completeness, confidence ranges, or other answer semantics. Applications
  interpret returned data; diagnostic limits must never truncate the business result.
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
- [ ] Classify each behavior as required in the replacement, intentionally changed,
  or retired. Include direct/child invocation, answer fidelity, retry ownership,
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
- [ ] Record one frame per captured diagnostic call with plain message, optional
  structured JSON data, Framework-supplied timestamp, invocation association, and
  stable sequence. Snapshot data; define concurrent ordering and writes after closure.
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
  per-call warnings. Distinguish truncation, no capture, and unavailable recording.
- [ ] Document best-effort notice delivery when recording fails, and the limits of the
  guarantee: caller argument evaluation and JVM-wide failures are outside it. Defer
  arbitrary DTO serialization and nested operation handles.
- [ ] Preserve content fidelity. Do not add sensitivity classification or automatic
  masking. Authentication, authorization, capture controls, and resource limits
  remain the boundaries. Retain the existing `ExecutionJournalProjector` exception
  only for the derived journal and observer callback; do not broaden it.
- [ ] Test isolation between invocations, concurrent operations, closure/failure,
  disabled capture, fidelity, and explicit truncation using public invocation.
- [ ] Verify successful executor results survive ordinary serialization, recording,
  fallback, and standard-logging failures. Test diagnostic-budget exhaustion, bounded notices,
  warning coalescing, and absence of recursive error reporting.

Exit: a separate consumer can report useful operation evidence using only the
supported API, even with Console absent.

## Phase 3 — Generic Console integration

Console is solely a development and troubleshooting tool. Framework records the
execution independently; Console observes it when installed and connected. The
v1 view combines automatic boundary evidence and extension-authored message/JSON
frames under the same extension contract version. No presentation descriptor is
required from the pack.

- [ ] Walk through successful execution, recoverable/terminal failures, and a caught
  provider failure followed by retry using the simple diagnostics frames.
- [ ] Carry operations and evidence through trace serialization, authenticated
  inspection routes, and existing MCP/Console readers where applicable.
- [ ] Provide MCP execution summaries, bounded diagnostic-frame pages, invocation/
  severity filtering, and stable sequence/cursor continuation. Support focused frame
  detail retrieval without requiring the entire execution transcript. Follow existing
  inspection conventions; exact tool names and payloads remain open. Severity filters
  apply to developer-authored frames, not automatic failure classification or system notices.
- [ ] Display automatic timing, generation ID, input/output, and failure evidence
  alongside individual chronological frames with expandable JSON, grouped beneath
  the execution. Bound rendering work; grouping must not merge the stored frames.
- [ ] Console reports recorded facts; it never synthesizes results or infers execution
  outcomes from diagnostic levels, message text, arbitrary payload fields, or timing.
  Display Framework-recorded invocation outcome/classification and executor-authored
  messages/JSON as recorded. Developers use that evidence to distinguish a caught
  request failure/retry from an invocation failure or a negative business decision.
  No typed provider-attempt tracking or provider-specific interpretation is needed in v1.
  Grouping, filtering, and factual aggregates do not create new semantic outcomes.
- [ ] Make absent, disabled, truncated, or incomplete evidence visible as such.
- [ ] Label totals "Framework-accounted usage" in Console and explain that executor-managed
  provider calls are excluded. Document the same scope in API and MCP usage descriptions;
  retain existing usage fields and calculations without a new completeness indicator.
- [ ] Change Framework-to-Console compatibility version as required and update
  Java/Go/TypeScript transport, MCP readers, Console, and fixtures together. Remove
  obsolete shapes; no historical readers, old-version support, or negotiation.
- [ ] Verify Console absence or presentation failure cannot affect execution/results.
  Defer display hints, custom layouts, and extension-specific renderers beyond v1.
- [ ] Add representative fixtures and reader/UI tests, including a non-AI executor.
- [ ] Verify MCP pagination/filtering/detail retrieval and consistent visibility of
  capture-failure and limit notices in Console and MCP, including concurrent events.

Exit: a developer can troubleshoot an external pack without a provider-specific
Console screen, and the same pack runs without Console. Pack-supplied UI code,
custom renderer loading, and a broad presentation component library are deferred.

## Phase 4 — Cooperative lifecycle and explicit migration behavior

V1 supplies simple cancellation/deadline accessors, not provider-attempt admission,
authoritative usage reporting, or physical-request cancellation registration.

- [ ] Implement live read-only cancellation status and remaining-time accessors;
  conceptual signatures are `boolean isCancelled()` and
  `Optional<Duration> remainingTime()`. Empty means no deadline; expiry returns zero,
  never negative. Finalize concurrency and post-completion behavior.
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

- [ ] Create `loomspan-decisions` in the separate `loomspan-extensions` repository,
  using only supported Loomspan API, its own POM, and independent versioning.
  Publication is tracked separately from the implementation-complete gate.
  Start with one artifact; add modules only when practical needs justify them.
- [ ] Implement the TypeSafe executor first, then separate Gemini and OpenAI
  executors. Preserve provider request semantics and complete native JSON responses.
  Share generic utilities only; no unified Decisions request/result API in v1.
  Follow the companion research document and recheck current provider contracts.
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
  bindings using existing runtime behavior. Verify pack-owned JSON-only response
  validation, preservation of unknown fields/refusals, and no decoding-only retries;
  do not add schema metadata hooks or Framework output enforcement for this pack.
- [ ] Provide one runnable recipe using complete native responses and update all
  examples/bindings to the new paths. Verify additional provider fields are retained;
  provide no legacy result mode or migration-support deliverable.
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

Exit: core has no native TypeSafe execution dependency. The new pack usage is tested
and documented, without legacy or migration support.
Overall v1 completion requires the shared implementation-complete checklist above;
public artifact publication and product releases are tracked separately.

## Phase 7 — Grow the pack ecosystem after v1 feedback

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

## Decisions still open

1. Exact versioned package names and invocation class/accessor signatures. Versioned
   parameter types select the new contract; old handoff support is not required.
2. Final cancellation/deadline declarations and post-completion/concurrency semantics.
   No deadline is empty remaining time; expiry is zero. HTTP total timeouts belong
   to the pack. Provider accounting and physical cancellation callbacks remain deferred.
3. Implementation sequencing across repositories and coordinated compatibility-version
   updates. Publication/releases are tracked separately from the settled implementation-
   complete gate. Removal policy is settled: no native TypeSafe or compatibility support
   remains in the final design.
4. Exact Maven group coordinates, ownership, release policy, and tested Framework/
   Java/Boot combinations. The starting artifact name is `loomspan-decisions`.
5. Pack request-validation details and error messages. Response validation is settled:
   JSON only, without provider response-schema enforcement or decoding-only retries.
   Request-validation timing is settled: validate pack
   configuration and resolved requests at invocation before sending; no Framework
   registration hook or TypeSafe-specific startup checks are part of v1.
6. Exact diagnostics signatures (the three levels `info`, `warn`, and `error` are settled),
   supported JSON containers/numbers, diagnostic snapshot mechanism,
   late/concurrent-write behavior, and resource-limit values.
   Presentation hints and arbitrary Java-object conversion are deferred.
7. Pack-owned provider request binding/model selection, initial modality scope,
   retry settings/defaults, and total-timeout settings/client implementation. Native
   provider semantics, full responses, and TypeSafe's internal build-once/reuse decision
   are settled; illustrative request builders
   and JSON Pointer handling are not an approved shared Decisions language.

These are implementation design choices within settled scope. They are not reasons
to reopen rejected compatibility layers, provider normalization, precision changes,
startup domain-validation hooks, output schemas, caches, or namespace changes.
