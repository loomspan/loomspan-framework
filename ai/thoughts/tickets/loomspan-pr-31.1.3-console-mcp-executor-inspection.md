# PR 31.1.3 — Expose executor diagnostics through existing Console and MCP inspection

## Outcome

Developers can inspect chronological executor diagnostics and Framework notices
through existing Console views and MCP tools, while execution outcomes and usage
remain authoritative Framework facts.

## Owning repository, affected layers and dependencies

**Owner/layers:** Framework repository: Console Go/TypeScript/UI/MCP, corresponding Java transport fixtures, usage descriptions and generic accounting tests.

**Dependencies:** Requires 31.1.2's record vocabulary/data shape and 31.1.1's API. No Extensions consumer dependency; 31.3.2 qualifies actual pack traces through Console/MCP later.

## Ticket scope

- Implement the full embedded Phase 3 contract in current readers, DTO/schema validation,
  UI and fixtures. Preserve existing query/filter/pagination/content retrieval semantics.
- Show boundary timing/generation/input/output/failure evidence alongside expandable
  diagnostic JSON within the same execution frame. Bound rendering work without merging records.
- Keep authored severity separate from execution outcomes and Framework notices.
  Never infer a missing capture cause, provider attempts, retry outcomes or business decisions.
- Label totals "Framework-accounted usage" and explain identical scope in API docs and
  MCP usage descriptions. Do not change existing totals/fields or add completeness flags.
- Add generic mixed-mission accounting fixtures/tests using a non-AI or stub executor:
  executor-managed requests/retries and diagnostic usage do not consume native provider
  attempt budget or authoritative usage totals. Retain unknown usage as unknown in evidence.
- Align Java/Go/TypeScript and fixtures using the unchanged compatibility version.
  Restore the expected consumer/fixture breaks reported by 31.1.2.

## Acceptance criteria — this ticket only

- [ ] Existing LOOMSPAN_query_trace_records and LOOMSPAN_read_trace_content expose
  mixed normal/diagnostic/system records with faithful message/severity/JSON and
  explicit capture states through existing data/content references.
- [ ] Type/frame filters, ordering, continuation, bounded inline content and content
  reads work without duplicates/omissions, including concurrent events. Selecting both
  diagnostics and notices uses both record types; no notice filter bypass.
- [ ] UI shows one chronological item per record and recorded outcome/classification,
  without deriving execution failures from error diagnostics or synthesizing missing-state explanations.
- [ ] A successful call, recoverable/terminal failures, caught failure followed by retry,
  truncation/unavailability, and a non-AI fixture are represented. No diagnostic-specific
  tools/endpoints/stores, severity filtering or new presentation descriptor system exists.
- [ ] Mixed-mission totals include only Framework-accounted calls. Unavailable usage,
  successful-HTTP decoding failure and Console absence are covered. API/MCP/UI descriptions
  agree; no synthetic zero replaces unreported provider usage in evidence.
- [ ] Go/UI/build verification and Java fixture corpus pass with unchanged matching
  compatibility markers. Optional Console/presentation failure cannot change business execution.

## Local verification commands

From `loomspan-console`: `go test ./...` and `go run ./internal/buildtool verify`.
From Framework: `./mvnw.cmd -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false test`,
`./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test` when production Java
types change, plus focused generic usage tests. For changed concurrent Go code,
run `go test -race ./...` with `C:\msys64\mingw64\bin` on PATH and
`CGO_ENABLED=1` as documented in Console AGENTS.md. Keep corpus/expected files LF.

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

Phase 3 and generic accounting descriptions/fixtures are this ticket's acceptance. Actual TypeSafe transport checks are owned by 31.2.1 and consumer accounting checks by 31.3.2; their absence does not hold this ticket open.

The following contract extracts carry the roadmap's decisions into this ticket.
Checklist markers are removed to distinguish shared context from this ticket's
acceptance above. The current compatibility marker stays unchanged. Names/defaults
explicitly left to implementation are selected once by their owning ticket. Shared
checks assigned elsewhere are not completion gates here.

## Phase 3 — Generic Console integration

Console is solely a development and troubleshooting tool. Framework records the
execution independently; Console observes it when installed and connected. The
v1 view combines automatic boundary evidence and extension-authored message/JSON
records under the same extension contract version. No presentation descriptor is
required from the pack.
The factual-presentation boundary follows the
[Framework feature design lens](../framework-feature-design-lens.md#12-keep-semantic-authority-at-the-owning-boundary).

- Walk through successful execution, recoverable/terminal failures, and a caught
  provider failure followed by retry using the simple diagnostic records.
- Carry operations and evidence through trace serialization, authenticated
  inspection routes, and existing MCP/Console readers where applicable.
- Integrate new diagnostic record types and content into the existing
  `LOOMSPAN_query_trace_records` and `LOOMSPAN_read_trace_content` tools, following
  the settled contract below. Reuse existing summaries and retrieval behavior;
  do not add diagnostic-specific tools, severity filters, or pagination machinery.
- Display automatic timing, generation ID, input/output, and failure evidence
  alongside individual chronological diagnostic records with expandable JSON, grouped
  beneath the existing execution frame. Bound rendering work; grouping must not merge records.
- Console reports recorded facts; it never synthesizes results or infers execution
  outcomes from diagnostic levels, message text, arbitrary payload fields, or timing.
  Display Framework-recorded invocation outcome/classification and executor-authored
  messages/JSON as recorded. Developers use that evidence to distinguish a caught
  request failure/retry from an invocation failure or a negative business decision.
  No typed provider-attempt tracking or provider-specific interpretation is needed in v1.
  Grouping, filtering, and factual aggregates do not create new semantic outcomes.
- Display executor-authored diagnostic messages, severity, and JSON as recorded;
  the SkillExecutor developer determines their content and meaning. Display Framework
  execution facts and capture notices separately; capture notices have no developer
  severity. Console does not generate explanatory error messages or infer capture
  state from missing evidence. Display unavailable, truncated, or failed
  capture only when explicitly recorded by Framework. Notice delivery is best effort:
  if recording fails and no notice survives, Console has no cause to explain.
- Apply the accounting descriptions specified in Phase 4.
- Keep the current Framework-to-Console compatibility version unchanged and update
  Java/Go/TypeScript transport, MCP readers, Console, and fixtures together. Remove
  obsolete shapes under the breaking-change policy. Console currently uses its exact
  release string for `consoleCompatibilityVersion`; use actual repository release/version
  machinery rather than inventing independent negotiation.
- Verify Console absence or presentation failure cannot affect execution/results.
  Defer display hints, custom layouts, and extension-specific renderers beyond v1.
- Add representative fixtures and reader/UI tests, including a non-AI executor.
- Verify existing MCP type/frame filtering, pagination, inline content, and content
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

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Changes serialized cross-language consumption, MCP contracts and user-visible evidence interpretation; closed vocabularies and fixture parity require full assurance.
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

