# PR 31.3.1 — Remove native TypeSafe and obsolete Framework contracts

## Outcome

Framework has no native TypeSafe execution path or dependency. TypeSafe is invoked
through ordinary executor-backed JAVA leaves, with current docs and consumers describing
the replacement rather than preserving the retired contracts.

## Owning repository, affected layers and dependencies

**Owner/layers:** Framework production/parser/config/dispatch/dependencies/API, obsolete Console/MCP handling, fixtures and Framework consumer docs.

**Dependencies:** Run after 31.2.2 in the agreed sequence, using 31.1.1's removal inventory and the established replacement syntax. Acceptance is Framework-local and must not wait for an Extensions run or public artifact publication.

## Ticket scope

- Finish and apply the cross-layer removal inventory from 31.1.1 against the current
  checkout. Delete native typesafe declarations/parser branches, connection driver/model
  handling, dispatch/executors/clients/resource coupling and exclusively used dependencies.
  Remove obsolete TypeSafe-specific Console/MCP classifications and trace assumptions.
- Remove SkillKind.TYPESAFE and obsolete public/internal types/enum values, updating every
  current usage and supported-surface allowlist. Keep generic executor-backed leaves JAVA.
  No shims, deprecated aliases, config translators, dual paths or historical readers.
- Replace native examples/guides with the established pack usage and /answers/... bindings.
  Align Framework docs and any owning policy text with deliberately versioned supported API,
  existing namespace behavior, same compatibility version, and the accepted behavior changes.
- Preserve generic executor config numeric decoding. Keep reference-only executor namespace,
  visibility, authorization and captured configuration/publication/retirement behavior unchanged.
  Do not remove generic machinery still used by other providers/executors.
- Document the accepted differences precisely: invocation-time domain validation, full
  response envelope, lost generated output metadata/early checks, config decimal decoding,
  app-resolved captured credentials, non-native attempt/usage accounting and cooperative
  rather than immediate physical cancellation. These are not requests for parity restoration.
- Update current Java/Go/TypeScript readers/writers and fixtures coherently as affected,
  without incrementing consoleCompatibilityVersion. No migration of historical traces.
- Source review is analysis evidence, not a requirement to retain native files or maintain
  permanent historical links. Update misleading current consumer references; do not build
  an evidence-preservation project or delete planning history merely because it mentions native code.

## Acceptance criteria — this ticket only

- [x] Production no longer accepts/dispatches native TypeSafe declarations or native
  driver/model configuration, and no native TypeSafe implementation/dependency/resource
  coupling remains. Ordinary generic executor JAVA leaves continue to work.
- [x] Obsolete public/internal values and old unversioned invocation surfaces are absent;
  supported-surface policy/allowlists and all current consumers match the final API.
- [x] Fixtures/tests assert the new contracts without requiring old API/config/wire shapes.
  Current parser/config errors do not silently translate old declarations.
- [x] Generic direct/child invocation, binding, namespace visibility/auth, captured config,
  generation lifetime, cancellation and Framework-accounted usage behavior remain covered.
- [x] Current docs/examples use pack declarations and complete-response paths and explain
  all explicitly accepted reductions. No unsupported internal types are recommended to consumers.
- [x] Applicable Framework and Console tests, public-surface checks and Java/Go fixture
  corpus pass with existing matching version markers. Extensions consumer rerun is owned
  by 31.3.2, not a completion gate here; record the new local Framework build identity.

## Local verification commands

From Framework: `./mvnw.cmd test`, including
`LoomspanPublicSurfaceArchitectureTest` and applicable executor/lifecycle/usage/binding
checks; `./mvnw.cmd -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false test`.
From Console when affected: `go test ./...` and `go run ./internal/buildtool verify`;
use the AGENTS.md race command for changed concurrent Go paths. Run focused tests
during edits and the required final local suites when removal is coherent.

## Shared intent and binding workflow decisions

This ticket is part of GitHub PR 31's TypeSafe extraction, not a separate GitHub PR.
The PR 31.x.x identifier orders local implementation tickets. Target coder: GPT 6.1 Sol.
Replace native TypeSafe with the independently versioned `loomspan-decisions` Maven
artifact in `loomspan-extensions`. Framework owns generic execution and observation;
the pack owns TypeSafe integration; applications own callable YAML skills and policy.
TypeSafe is the complete v1 provider scope. Gemini/OpenAI are later additions to the
same extension; their requirements are deferred. No shared Decisions language,
native `decisions:` grammar, provider discovery, fallback platform, or manifest.

**Latest user decisions supersede older roadmap/process wording where it differs:**
- Destructive scoped source/API/wire changes are welcome. No compatibility shims,
  aliases, legacy adapters, translators, dual paths, historical readers, or migration support.
- **Do not increment `consoleCompatibilityVersion` for this work.**
  There are no deployments. Update current Java/Go/TypeScript contracts and fixtures
  coherently using the existing matching version. Keep exact-version checks; do not
  add negotiation or preserve old Framework/Console combinations.
- All seven tickets live and run from `loomspan-framework/ai/thoughts/tickets/`.
  The same pipeline may edit the sibling `../loomspan-extensions` checkout directly.
  No second set of tickets or separate pipeline launch in that repository is required.
  Code/tests/examples still belong to their actual owning repository.
- Execute tickets in the numbered order listed below. Commit ticket-owned changes
  after each ticket in each affected repository; do not push, sync, or publish.
  Preserve unrelated dirty/untracked files. The Extensions scaffold is initially
  untracked: inspect and deliberately include needed scaffold files, never blindly stage all.
- Intermediate commits may break builds or fixtures. Do not add compatibility code
  merely to keep intermediate commits green. Run feasible local checks, record
  exact blocked/failing commands and causes, and name the later ticket that restores
  them. An expected downstream break is not a new approval gate and must not be
  reported as a test pass. The sequence must end coherent with all required checks passing.
  Carry outstanding verification forward in the ticket's execution notes.
- No disabled-capture feature, setting, or special acceptance requirement is added.
  Reuse existing Framework recording/observation behavior.
- No requirement to preserve historical source links after native code removal.

## Mandatory breaking-change policy

> Breaking-change policy: This is pre-release development. Destructive changes to
> the scoped Java APIs, configuration, traces, MCP contracts, and Loomspan Console
> are authorized and preferred. Remove the replaced native TypeSafe implementation
> and obsolete contracts; do not add compatibility shims, legacy adapters, dual
> paths, migration support, or historical readers. Update all affected consumers,
> tests, fixtures, and documentation to the new design. Coordinate current
> Framework/Console contracts without incrementing consoleCompatibilityVersion. Implement only
> the explicitly listed generic SkillExecutor enhancements; do not add TypeSafe-
> specific Framework behavior. Preserve the existing executor/YAML skill namespace
> relationship. Versioned invocation packages start with the new v1 only; no legacy
> invocation support is required.

The policy above matches the updated roadmap verbatim. The no-increment decision
applies to the Framework/Console compatibility marker, not a new blanket freeze on
independently selected pack artifact coordinates or versions.

## Ownership, boundaries, and evidence

Framework scope is limited to versioned invocation/exact parameter registration,
bounded invocation-scoped message/JSON diagnostics and existing inspection integration,
and read-only cancellation/deadline accessors, plus native removal and coordinated
consumer/documentation updates. No TypeSafe-specific startup validator, credential
resolver, generated output schema, provider registry, accounting API, cache, or
internal-bean replacement SPI. Public signatures never expose internal/autoconfigure
types. Deliberately allowlist the selected versioned API packages and document them;
Java `public` alone does not make a type supported.

Preserve all prompt/input/output/tool/evidence content. Do not classify sensitivity,
mask, sanitize, or add secret scanners. Authentication, authorization and explicit
access boundaries apply. Resource limits/truncation are explicit controls. Retain
only the existing `ExecutionJournalProjector` field-name redaction for the derived
journal/observer callback; never broaden it to canonical traces or business results.

Keep executor/YAML namespace behavior unchanged: reference-only executors may serve
multiple callable YAML skills under existing visibility, authorization, configuration
ownership and publication rules. No global registration or automatic visibility.
Extension examples use `default`; namespace teaching stays in Framework docs.

Each ticket's acceptance below is local to its owner. Embedded contracts and checks
owned by other tickets are implementation context, not additional completion gates.
Framework tickets never wait for the Extensions example. Extensions tests use the
real packaged executor, supported Framework API and deterministic local HTTP stubs
with test credentials, never a mocked-away pack or real provider credentials.
`loomspan-sidecar-test-suite` and live provider/model-quality evaluation are separate
future work, never gates for any ticket or overall v1 completion. Mock tests establish
integration, not accuracy/calibration/latency/cost. Thresholds are illustrative.

Overall delivery additionally needs the separate artifact, consumer proof against
identified Framework/Console builds, Console/MCP evidence, Console-free execution,
runnable recipe, release-aligned docs/index and required passing checks. Locally
installed/staged artifacts suffice; record exact versions and source commits.
Public Maven publication, product releases, coordinated release automation and
live inference are not required; never claim an unpublished dependency is released.

## Sequence and dependency ownership

1. **31.1.1** — Versioned invocation and cooperative lifecycle.
2. **31.1.2** — Bounded diagnostics and canonical trace recording.
3. **31.1.3** — Console/MCP inspection and accounting scope.
4. **31.2.1** — Pack-owned TypeSafe transport and captured settings.
5. **31.2.2** — TypeSafe executor and runnable consumer proof.
6. **31.3.1** — Native TypeSafe removal and current consumer updates.
7. **31.3.2** — Advanced consumer qualification, documentation and final verification.

The order is the execution plan; it is not a requirement for upstream tickets to
wait for downstream acceptance. Real API/build dependencies are identified in each
ticket. Later verification uses the final native-free Framework build.

## Required execution handoff

At completion, record selected public signatures, diagnostic payload/enum spellings,
pack configuration/defaults, tested build coordinates and repeatable commands that
this ticket actually establishes in its execution notes and owning API/docs. Record
remaining build/test debt with the exact command/cause and restoring ticket. Later
tickets consume these choices rather than researching or designing them again.
This is a handoff record, not an additional implementation feature or approval gate.

## Source context and remaining choices

The [roadmap](../phases/executor-packs-and-typesafe-extraction-roadmap.md) owns settled
decisions; [migration review](../phases/typesafe-executor-pack-migration-review.md)
owns native-code evidence; [research](../phases/decisions-executor-pack-research.md)
owns dated external evidence. This ticket embeds applicable contracts so the coder
does not need to reconstruct intent. Read source to implement and verify facts,
not to reopen settled decisions. Native observations do not imply parity obligations.

Read applicable `AGENTS.md` files in both checkouts and `loomspan-console/AGENTS.md`
for work there. Remaining API names/construction, supported concrete JSON types,
snapshot/concurrency implementation, limit values, request binding/model syntax,
retry/default settings, HTTP client choice and release/support documentation are
bounded implementation choices, not new approval gates. They cannot expand scope.
Once an owning earlier ticket selects a declaration, payload shape, default or command,
later tickets consume that decision; do not select it independently or reopen it.
Read the earlier ticket execution notes and implemented supported contract as the handoff.
Provider documentation/wire-fixture refresh before pack implementation is still
required; dated research is not a current service guarantee. No live inference is needed.


## Embedded settled contracts — ownership matters

This ticket owns native removal and Framework-local checks/docs. The copied pack contracts explain the replacement and accepted losses; pack implementation and functional proof remain 31.2.1/31.2.2 ownership, with advanced qualification in 31.3.2. The MCP handoff constrains any obsolete-shape removal. No downstream consumer gate is added.

The following contract extracts carry the roadmap's decisions into this ticket.
Checklist markers are removed to distinguish shared context from this ticket's
acceptance above. The current compatibility marker stays unchanged. Names/defaults
explicitly left to implementation are selected once by their owning ticket. Shared
checks assigned elsewhere are not completion gates here.

## Phase 6 — Remove native TypeSafe implementation from core

- Delete native `typesafe:` declarations/parser branches, connection driver/model
  configuration support, dispatch, clients, resource coupling, and dependencies used
  exclusively by native TypeSafe. Reimplement provider behavior in the pack.
- Keep executor-backed leaves on existing `JAVA` classification.
- Remove `SkillKind.TYPESAFE` and other obsolete public/internal surfaces; update
  all usages and allowlists. No deprecated aliases, translators, or compatibility shims.
- Replace old examples and consumer docs with current executor-pack usage. Remove
  obsolete TypeSafe-specific Console/MCP handling and historical shape assumptions.
- Apply the mandatory ticket policy across the coordinated Framework/Console/MCP
  changes, with the existing compatibility version and matching fixtures.
- Update fixtures and supported-surface tests to describe the final design.
  Run `LoomspanPublicSurfaceArchitectureTest` after production type changes, plus
  focused executor, lifecycle, usage, binding, and Console checks.

Exit: core has no native TypeSafe execution dependency; Framework-local tests and
documentation reflect the replacement, without legacy or migration support. Pack
implementation and its example are verified by Extensions-owned tickets, not gates
on this removal phase or its Framework tickets.
Overall v1 completion requires the shared implementation-complete checklist above;
public artifact publication and product releases are tracked separately.

### Request construction and validation

Before implementing provider integration, recheck source documentation and capture
representative wire fixtures. This is provider-contract research under the existing
mocked-test and live-evaluation boundaries, not a live inference requirement.

Implement TypeSafe using only supported Loomspan API, pack-owned HTTP/SDK clients,
and ordinary `executor:` / `config:` declarations. Keep provider field names, question
identity, value types, score ordering, confidence meanings, refusals, and usage intact.
Keep batching and provider/route selection explicit: do not split requests or
substitute providers implicitly. Provider discovery, route negotiation, and automatic
cross-provider fallback are outside v1.
The minimum v1 capability set is Choice, Noul, and Score, with text/JSON state,
instructions, and criteria according to each question type's native semantics.
Support requests containing multiple question types together. Images and Box
localization are optional future scope, not v1 requirements.
Exact executor name/request-binding/model-selection syntax remain implementation
choices. Unsupported capabilities fail clearly before
send rather than altering content. No shared Decisions language or legacy expression
compatibility is required. Use provider docs for native request/response semantics;
pack docs explain Loomspan input/configuration binding.
Keep credentials, diagnostics, and transport utilities focused on TypeSafe; future
providers do not require shared abstractions in v1.

Validate pack configuration and resolved requests at every invocation before any
send, using clear configuration-path errors. Local validation failures do not trigger
provider retries. Publish success does not certify provider configuration; loss of
native startup/publication domain checks is accepted. No validation SPI, separate
validation API, compiled-configuration cache, or Framework-specific TypeSafe checks
are required. Domain errors need not become `RecoverableSkillException`; its
existing correction semantics remain as described in Phase 2.

Build, validate, and serialize once before the first send; reuse identical body bytes
across retries. Resolve endpoint/credentials once from captured maps. Materialize
supported local resource content during construction, not on retry. A referenced
remote URL remains a URL; its remote content is not frozen. Keep lifecycle reads
and per-attempt timeout capping live.
This is TypeSafe pack policy, not a general executor rule or Framework snapshot API.
It promises neither identical responses nor remote deduplication.

Keep existing executor configuration numeric decoding. High-precision declaration
decimals moved into executor config may already have rounded before pack execution;
this accepted difference requires documentation, not a precision mode/special syntax
or native exact-decimal parity. Preserve values as received; this is not permission
to add rounding or string conversion elsewhere in request/response mapping.

### Captured credentials and endpoints

Only `api_key` and `base_url` have agreed override rules; do not generalize their
precedence to future settings without configuration design. Check key presence:

| Setting | First | If absent | If absent from both maps |
| --- | --- | --- | --- |
| `api_key` | `skillConfig.api_key` | `executorConfig.api_key` | Fail before send |
| `base_url` | `skillConfig.base_url` | `executorConfig.base_url` | Provider default |

Present null, blank, or non-string values fail rather than fall back; an invalid URL
also fails before transmission. Preserve selected valid credential values unchanged.
Applications supply resolved values: no implicit environment fallback, `${...}` or
`api-key-ref` resolution, or native connection-registry lookup.

Both maps remain publishable through their owning namespaces. Changing skill config
versus executor config means publishing the corresponding owner; old work retains
captured values, new work receives updates. These are not startup-only properties.
Preserve base URL path prefixes when appending `/v1/systemone`. Application proxies,
test servers, and alternative prefixes such as the existing OpenRouter setup are
permitted where protocol-compatible. Recheck route-specific documentation/fixtures
before support claims; configured routes do not guarantee service availability.

### Complete response and Java return boundary

On successful HTTP, return the complete native JSON response, including the outer
envelope, unknown fields, native refusals, and usage. Do not extract only `answers`,
rename fields, reshape/wrap the response, or enforce provider response schemas, answer
completeness, confidence ranges, or other answer semantics. Applications interpret
the data. HTTP/transport failures remain execution failures, not successful results.
Empty/malformed JSON on successful HTTP fails without a decoding-only retry.

HTTP-to-Java conversion uses the chosen client/existing JSON tooling; no second
parsing step is needed if the client already supplies that value. Preserve JSON
content/structure, not identical whitespace or bytes. Diagnostic limits never truncate
the business result. Business request/result serialization can still fail execution.

Preserve valid JSON through the actual Java executor return path, including JSON null
and string responses. JSON `null` is not an empty HTTP body. The existing Java invoker
converts Java null to empty output and passes Java strings through as raw text; the
pack chooses a return representation preserving JSON across that boundary. This
requires no new Framework behavior, parsing contract, or response-schema enforcement.

Replace `/repeat/noul` bindings with `/answers/repeat/noul`; there is no answers-only
legacy mode. Accept loss of generated descriptive output metadata and some early
path/type incompatibility checks/catalog forwarding. Retain existing generic runtime
binding checks; they do not prove full result-schema conformance. No executor
output-schema YAML field, generated metadata hook, or new Framework enforcement
is added. Native metadata was descriptive, not provider-answer validation.

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

### Accounting and accepted lifecycle reductions

Pack requests/retries do not reserve the native mission-wide provider-attempt budget.
Existing invocation-level controls remain; they are not equivalent quota coverage.
Provider usage may appear in native responses and diagnostics but does not contribute
to authoritative Framework totals. Keep existing usage fields/calculations unchanged;
label Console totals "Framework-accounted usage" and explain the same scope in API
documentation and MCP usage descriptions. Zero means zero Framework-accounted usage,
not measured zero executor usage. Preserve native usage when captured; do not
substitute zero for unreported usage. No completeness indicator, diagnostic usage
aggregation, or executor usage-reporting API is part of v1.

- Implement the accessors and verify the settled cancellation acceptance checks above.
- Verify late physical return, fencing, and resource lifetime. Keep work within
  the invocation until physically complete, or explicitly manage retained client
  resources; untracked background I/O may outlive generation retention.
- In Extensions, verify bounded retries, SDK retry configuration, and cooperative
  cancellation through the consuming example; apply Phase 5 transport requirements.
- Verify a mixed mission: only Framework-accounted model calls contribute to
  totals; executor-managed requests/retries do not reserve native attempt budget,
  and diagnostic usage does not affect totals. Check Console/API/MCP scope descriptions.
  Include unavailable usage, successful-HTTP decoding failure, and Console absence.
- Document accepted lifecycle/accounting reductions and removals in current
  usage docs/release notes under the breaking-change policy.

Exit: minimal lifecycle facilities and the explicitly reduced guarantees are verified.
Full native accounting/physical-cancellation parity is not a completion gate.

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

## Native source evidence already established

These are native baseline findings, not replacement parity requirements. Verify
anchors against the current checkout; do not redo the design research or import
these internal implementations into the pack.

## 1. Invocation registration

`SkillMethodBeanPostProcessor.registerExecutor` accepts exactly the unversioned
`SkillExecutorInvocation`; `SkillExecutorRegistration` stores a function typed to it,
and `ExecutionCoordinator` constructs the five-component public record. Its fields
are input, skill config, executor config, skill name, and executor config generation ID;
it exposes neither accounting nor a trace writer. A proposed versioned parameter
currently fails registration.

Sources: [registration](../../../src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java),
[registration record](../../../src/main/java/ai/loomspan/internal/core/SkillExecutorRegistration.java),
[handoff construction](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java).
Disposition: [Phase 1](../phases/executor-packs-and-typesafe-extraction-roadmap.md#phase-1--finalize-the-reviewed-public-contract-and-removal-inventory).

## 2. Configuration numeric decoding

Native TypeSafe uses the lossless skill-document number path. However,
`SkillDocumentValues.decode` separately reads executor `config` through
`YamlValues.read`, the ordinary numeric constructor. Namespace `executor-config`
uses the ordinary path too. `YamlSkillCatalog` then installs that raw map as skill
configuration. Consequently, moving precise literal criteria/state from `typesafe:`
to `config:` can convert decimals to floating point before pack execution. A pack
Jackson mapper cannot recover digits already lost.

Sources: [skill decoding](../../../src/main/java/ai/loomspan/internal/serialization/SkillDocumentValues.java),
[YAML constructors](../../../src/main/java/ai/loomspan/internal/serialization/YamlValues.java),
[catalog config binding](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java).
Evidence: `TypeSafeDeclarationTests.numericSubstitutionsAndRepeatedDetachedReadsPreserveArbitraryPrecision`
and `TypeSafeSkillIntegrationTest.nestedDecimalsRemainExactInRetryBodiesAnswersAndTraceEvidence`.

Disposition: [request construction and numeric behavior](../phases/executor-packs-and-typesafe-extraction-roadmap.md#request-construction-and-validation).

## 3. Native credential/model resolution

Native skills select `model: alias`, resolved through Framework model/connection
configuration. Executor leaves reject `model`; the invocation supplies configuration
maps, not a provider registry/client or resolved native credentials.

Executor maps deliberately preserve literal `${...}` and `api-key-ref` values.
`ExecutionConfigurationParser` resolves credential references in native connections,
not executor maps; supplied credential values used only by executor maps can be
rejected as unused. Moving native connection YAML under `executor-config` is not
an equivalent setup.

Sources: [configuration parser](../../../src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java),
[executor configuration tests](../../../src/test/java/ai/loomspan/autoconfigure/ExecutorConfigurationStartupTests.java),
[native configuration guide](../../../agent-skills/loomspan-docs/references/skill-authoring/typesafe-skills.md).

Disposition: [captured credentials and endpoints](../phases/executor-packs-and-typesafe-extraction-roadmap.md#captured-credentials-and-endpoints).

## 4. Declaration validation and binding

Native compilation rejects malformed question declarations and input expressions
when a skill is loaded. Generic executor configuration only checks structural maps,
references, and selected configuration paths. It has no supported pack validation
hook during `SkillReloader.validate/prepare`.

The native resolver is a source reference for whole-value JSON Pointer
substitution, scalar-only embedded substitution, escaped expressions, and
null/omission behavior. These are evidence for designing the pack binding, not a
requirement to preserve legacy expression syntax.

Sources: [declaration resolver](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeDeclaration.java),
[dependency validation](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java).

Disposition: [request construction and validation](../phases/executor-packs-and-typesafe-extraction-roadmap.md#request-construction-and-validation).

## 5. Output metadata and Java result conversion

Native `TypeSafeDeclaration` derives a descriptive output schema, exposed through
catalog inspection and parent forwarding. Executor leaves reject `output_schema`,
and `SkillDependencyValidator.resolveOutputSchema` excludes executor definitions.
A pack cannot publish equivalent generated metadata through the proposed invocation.

Native output metadata is descriptive; it does not enforce answer semantics.
The ordinary Java executor path also has a return-conversion boundary:
[`JavaSkillInvoker`](../../../src/main/java/ai/loomspan/internal/core/JavaSkillInvoker.java)
converts Java null to empty output, passes Java strings through as raw text, and
serializes other return values. This is the source evidence behind the agreed
JSON null/string consumer checks, not a new Framework behavior requirement.

Sources: [output schema selection](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java),
[dependency validation](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java),
[binding checks](../../../src/main/java/ai/loomspan/internal/skill/BindingSchemaCompatibility.java),
[inspection test](../../../src/test/java/ai/loomspan/internal/observability/web/TypeSafeRegisteredInspectionIntegrationTest.java).

Disposition: [complete response and Java return boundary](../phases/executor-packs-and-typesafe-extraction-roadmap.md#complete-response-and-java-return-boundary).

## 6. Native response and endpoint handling

Native success extracts the entire object-valued `answers` payload, so current
bindings can use `/repeat/noul`. The client supports a configured prefix plus
`/v1/systemone`, including existing OpenRouter configuration where protocol-compatible.
These source facts do not establish current external service availability.

The current client explicitly disables automatic retries and redirect handling.
The replacement decision and its acceptance test are recorded in the roadmap's
[Automatic redirects](../phases/executor-packs-and-typesafe-extraction-roadmap.md#automatic-redirects) section.

Sources: [native execution](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeExecutor.java),
[HTTP client tests](../../../src/test/java/ai/loomspan/internal/typesafe/TypeSafeClientTest.java),
[integration tests](../../../src/test/java/ai/loomspan/integration/TypeSafeSkillIntegrationTest.java).

Disposition: [HTTP handling](../phases/executor-packs-and-typesafe-extraction-roadmap.md#transport-and-http-outcome-handling)
and [response contract](../phases/executor-packs-and-typesafe-extraction-roadmap.md#complete-response-and-java-return-boundary).

## 7. Timeout and cancellation evidence

The current Apache client response/socket timeout is an inactivity bound. The test
`responseTimeoutIsInactivityWhileProgressingChunksMayExceedIt` demonstrates a response
lasting beyond the configured timeout while chunks keep arriving. Copying the client
and setting its timeout to `remainingTime()` would therefore not guarantee a total
request deadline. Native lifecycle cancellation currently provides the stronger abort.

`MissionWorkExecutor` enforces mission timeout with timed `Future.get(...)`;
`MissionLifecycle` also tracks cancellation-cleanup grace, while
`FrameworkExecutionLifecycle` owns shutdown timing. Execution budget and cleanup
grace are therefore distinct source concepts. Implementation guidance and acceptance
checks are in the roadmap's settled contract rather than duplicated here.

Sources: [client](../../../src/main/java/ai/loomspan/internal/typesafe/TypeSafeClient.java),
[timeout and cancellation tests](../../../src/test/java/ai/loomspan/internal/typesafe/TypeSafeClientTest.java).

Disposition: [cancellation contract](../phases/executor-packs-and-typesafe-extraction-roadmap.md#settled-cancellation-contract-and-mandatory-ticket-handoff).

## 8. Resource lifetime and namespaces

The native client constructs an Apache HTTP client/pool per instance; native
generation ownership connects those resources to retirement. Existing executor
generations retain captured configuration for active work, and public retirement
notifications are available for generation-owned resources.

Executor-backed skills already participate in input/access checks, composition,
captured configuration, and ordinary observation. They use the `JAVA` classification.
Multiple YAML skills can reference an executor. Source/tests confirm private
namespaces allow same-namespace references and public namespaces permit foreign
references subject to authorization. Executors are reference-only. This verifies
existing behavior, not original design intent; the earlier concern became a
documentation clarification, not a new namespace workstream or acceptance gate.
The follow-up inspected `SkillExecutor.namespace()`, executor registration, and
`SkillDependencyValidator` reference/visibility resolution; its test-run evidence
is recorded in the roadmap's verification history.

Sources: [executor binding](../../../src/main/java/ai/loomspan/internal/skill/SkillDependencyValidator.java),
[public retirement API](../../../src/main/java/ai/loomspan/api/SkillReloader.java),
[resource ownership guide](../../../agent-skills/loomspan-docs/references/java-api/skill-reload.md),
[generation tests](../../../src/test/java/ai/loomspan/integration/ExecutorGenerationExecutionIntegrationTest.java).

Dispositions: [namespace relationship](../phases/executor-packs-and-typesafe-extraction-roadmap.md#existing-namespace-relationship-retained)
and [transport lifetime](../phases/executor-packs-and-typesafe-extraction-roadmap.md#transport-and-http-outcome-handling).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Destructively removes cross-cutting native behavior, public/configuration contracts and serialized consumers; the closed API and current integrations need full assurance.
- **Reassessment triggers:** New evidence of work beyond the embedded scope requires a separate explicit decision; ordinary names, implementation details and already authorized compatibility breaks do not. The full profile already retains research, planning, testing and independent review.

## Pipeline notes

- Intentional source/API/config/trace breaks are authorized. Do not stop to request
  compatibility approval or preserve replaced behavior. Keep the existing compatibility
  version; update current contracts and fixtures rather than negotiating old versions.
- Commit after this ticket, but do not push/sync. Expected intermediate build failures
  are permitted only with precise evidence and a named later restoration owner; final
  delivery must pass its complete required verification. No compatibility stubs to mask debt.
- Working in the sibling Extensions checkout from this Framework-held ticket is authorized.
  Keep repository-owned artifacts there; separate ticket creation/pipeline execution is unnecessary.
- Execute only when this ticket is passed to the pipeline. Creating this file does not
  authorize starting implementation, committing current planning changes, or publishing.


## Execution notes — 2026-11-16

### Selected route, scope and decisions

- User selected **Full 5-Step Pipeline** with “proceed.” Initial Framework checkout was clean at `83b7dadc2adee9fa3f6ccd90d43925d18f952456` on `main`; no unrelated changes were identified. Research and both plans are ticket-owned. Step 4 is implemented and verified; independent Step 5 and the root-owned commit follow. No push, sync or publication was performed.
- Removed the native declaration/compiler/client/executor, TYPESAFE form/kind/driver, dispatch and registry resource coupling, direct Apache dependency, native-only tests and evaluation corpus/renderer. Generic `NativeOutputContract` and provider retry/cleanup machinery remain. `SkillKind` now contains exactly `YAML, JAVA`.
- Generic binding and forwarding tests use real annotated `FixtureExecutor` registrations and complete envelopes with `/answers/...` consuming pointers. The retained foreign connection test uses an ordinary OpenAI leaf and proves old endpoint/credential/model capture after replacement. Ordinary authored schema tests retain early generic compatibility/catalog checks; executors retain absent output metadata. Generic JSON-bound numeric/schema tests replace obsolete declaration examples; the actual ordinary executor config decoder is unchanged.
- Ordinary provider cleanup now explicitly covers DisposableBean/AutoCloseable, suppressed failures and idempotent destruction. Namespace/reference-only visibility, auth, captured configuration/publication/retirement, lifecycle and Framework-only accounting remain unchanged and covered.
- Current Java/Go/TypeScript/MCP contracts and fixtures were updated together. `consoleCompatibilityVersion` and artifact version remain `1.0.0-beta.10-SNAPSHOT`; exact-version checks remain. No legacy readers, translators, aliases or shims were added.
- Current docs use the external pack and describe all seven accepted limits: invocation-time validation; complete response; absent generated metadata/early provider checks; ordinary config decimals; application-resolved captured credentials; non-native attempt/usage accounting; cooperative physical cancellation. Bundled-skill self-containment checks reject sibling Markdown links, so owning sibling pack references are explicit code paths rather than links escaping the installed skill. Historical planning evidence remains intact. Drift classification after edits: **aligned**.

### Final contract handoff

No new public signature or provider setting was introduced. Exact registration remains `ai.loomspan.api.extensions.v1.SkillExecutorInvocation`, with `input()`, `skillConfig()`, `executorConfig()`, `skillName()`, `executorConfigGenerationId()`, `diagnostics()`, `boolean isCancelled()` and `Optional<Duration> remainingTime()`. The only supported versioned types remain that class and `ExecutorDiagnostics`. The unversioned invocation is absent.

Diagnostics retain `EXECUTOR_DIAGNOSTIC_RECORDED` and `EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED` as records in existing frames, using the existing `data` content path. No record payload/default/retrieval changes were made here. Existing `LOOMSPAN_query_trace_records` / `LOOMSPAN_read_trace_content`, bounds, fidelity, notices and failure/accounting distinction remain covered by the existing corpus and Console suites.

Pack declarations/defaults are the owning earlier tickets' established contract: local unpublished `ai.loomspan:loomspan-decisions:0.1.0-SNAPSHOT`, reference-only `default-typesafe`, `executor: typesafe`, `config.request` and RFC 6901 `config.bindings`, and complete `/answers/...` results. Captured `api_key` / `base_url` override by key presence; no Framework credential alias resolution. Provider default base URL remains `https://api.typesafe.ai`. No pack transport/retry defaults were changed. Extensions remains at the previously identified source `4a52c70`; no Extensions file was changed or consumer suite rerun by this ticket. Advanced consumer qualification belongs to **31.3.2**.

### Verification actually performed in Step 4

- Expected RED — `./mvnw.cmd '-Dtest=ExecutorDeclarationTests#nativeDeclarationIsUnknownForEveryValueAndForm,ExecutionConfigurationParserTest#rejectsRemovedTypeSafeDriver' test`: 2 failures before removal. The native driver was accepted, and the declaration used native validation (`must be an object`) rather than unknown-field rejection. Post-removal cases cover null, empty and populated native declarations across forms, plus accepted arbitrary nested executor domain keys.
- PASS — `./mvnw.cmd clean '-Dtest=ExecutorDeclarationTests,ExecutionConfigurationParserTest,LoomspanPublicSurfaceArchitectureTest,NamedAiConnectionRegistryTests,SkillDocumentValuesTest,DeclaredChildInputBindingsIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,DesignatedChildResultIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,SkillGenerationManagerTest,LoomspanPropertiesTest,ConfigurationMetadataTest,DefaultSkillCatalogTest,YamlSkillCatalogTests,ConsoleTraceFixtureCorpusTest,ConsoleRestFixtureCorpusTest' test`: **338 tests**, no failures/errors/skips. Log: `$env:TEMP/loomspan-31.3.1-focused.log`.
- PASS — `./mvnw.cmd test`: **2226 tests, 0 failures, 0 errors, 2 skipped**. The two unchanged `Pr18RecordedHandoffDiagnosticTest` opt-in cases require `pr18.diagnostic`; they are not ticket acceptance gates. Log: `$env:TEMP/loomspan-31.3.1-full.log`.
- PASS — `./mvnw.cmd '-Dtest=LoomspanPublicSurfaceArchitectureTest,RegisteredSkillEntryTest' test`: 9 tests, including final retired catalog-kind rejection. Log: `$env:TEMP/loomspan-31.3.1-boundary-final.log`.
- PASS — `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test`: 8 tests. Log: `$env:TEMP/loomspan-31.3.1-architecture.log`.
- PASS — `./mvnw.cmd -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false test`: 18 tests; unchanged-corpus Java/Go comparison, no regeneration. Log: `$env:TEMP/loomspan-31.3.1-corpus.log`. REST fixture corpus also passes (2 tests) within full/focused runs.
- PASS, from Console — `go test ./...`. Log: `$env:TEMP/31-console-all-clean.log`.
- PASS, from Console — `$env:PATH = "$env:TEMP\loomspan-pr29.3-node-24.18.0\package\bin;" + $env:PATH; go run ./internal/buildtool verify`: pinned Node **24.18.0**, npm **12.0.2**; frontend **604/604 tests**, typecheck/coverage/assets, skill checks and all Go checks. Log: `$env:TEMP/31-console-verify-final.log`.
- PASS — `./mvnw.cmd -DskipTests install`: packaged and installed the local native-free snapshot after the passing suites. Tests are intentionally skipped for this packaging command, not claimed as additional test evidence. Log: `$env:TEMP/loomspan-31.3.1-install.log`.
- PASS — JAR inspection: no `internal/typesafe/*.class` or old unversioned invocation; exact v1 invocation and diagnostics classes present. Empty directory entries are not implementation classes.
- PASS — `git diff --check`, scoped current-native-reference inventory, current doc link checks and bundled skill self-containment checks. Remaining native names are negative tests, current external-pack usage or historical planning evidence.
- NOT RUN — Go race detector: no concurrent Go production behavior changed; enum/schema and fixture updates do not trigger the conditional race requirement.
- NOT RUN — Extensions consumer qualification: explicitly owned by 31.3.2, not a Framework-local gate. No live inference or remote publication was run.

Transient implementation failures were corrected before completion: an initial test compilation retained two catalog helper references, a first clean attempt hit a Windows target deletion lock (retry succeeded), converted schema/count/UI expectations needed updates, global Node 24.22 was rejected by pinned tooling, and sibling Markdown links violated bundled skill containment. One unchanged `TraceRecords.results.test.tsx` assertion observed `Loading details…`; an unchanged focused rerun (45 tests) and final full Console rerun passed. No unrelated UI implementation was changed to hide it. No unresolved required check or ticket-owned build/test debt remains.

### Acceptance evidence

| Ticket criterion | Evidence |
| --- | --- |
| Native production/dependency/resource path absent; JAVA executor works | Source inventory, JAR inspection, rejection tests; executor direct/child integration; ordinary provider cleanup |
| Obsolete API/internal values absent; supported boundary current | Exact enum assertions, architecture checks, current reader/schema tests; v1 JAR inspection |
| Fixtures/current errors match final shapes without translation | Both corpus suites, startup/publication driver rejection, generic unknown-field tests, Console retired-kind rejection |
| Generic behavior retained | Full suite includes input/output bindings, namespace auth/publication, captured generations, executor lifecycle/cancellation, mission lifecycle and accounting/diagnostics |
| Current docs/examples and accepted reductions | Updated Framework/Console routed topics and coverage; source/test links, bundled-skill checks and pack-owned contract references |
| Local Framework/Console verification and identity | Full/explicit suites above; installed coordinates and hashes below; Extensions rerun separately assigned |

### Local Framework build identity for 31.3.2

- Coordinates: `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.10-SNAPSHOT`.
- Built/installed on 2026-11-16 with Java 21.0.2 and Maven wrapper 3.9.11.
- Source base commit: `83b7dadc2adee9fa3f6ccd90d43925d18f952456`; ticket production diff SHA-256: `8fdff76947db1b673b2f009c8ded9396f7292b2bcbd42ebe009405ef942f4d42`. Reproduce the source fingerprint by SHA-256 hashing the raw bytes from `git diff --binary 83b7dadc2adee9fa3f6ccd90d43925d18f952456 -- pom.xml src/main`. The final ticket commit is supplied by the pipeline orchestrator after a clean independent review, avoiding a circular self-reference here.
- JAR: `target/loomspan-spring-boot-starter-1.0.0-beta.10-SNAPSHOT.jar`, **1139218 bytes**, SHA-256 `3f4bd57de312f65012d717ba987dcfa8dba4fc9902206c4949413bc6e7fe52eb`.
- Installed at `C:/Users/mgiacomi/.m2/repository/ai/loomspan/loomspan-spring-boot-starter/1.0.0-beta.10-SNAPSHOT/`.
- Repeat build: `./mvnw.cmd test`, explicit architecture/corpus commands above, then `./mvnw.cmd -DskipTests install`; inspect with `Get-FileHash target/loomspan-spring-boot-starter-1.0.0-beta.10-SNAPSHOT.jar -Algorithm SHA256`.
- If independent review changes production code, rebuild/install and refresh this identity before 31.3.2 consumes it. No optional developer-only checks are necessary for Framework-local completion.
