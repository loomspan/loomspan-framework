# PR 31.1.2 — Record bounded executor diagnostics without changing execution outcomes

## Outcome

Make the versioned invocation's message/JSON diagnostics usable and bounded,
with one record per captured call in the existing execution frame and best-effort
system notices. Ordinary failures in the new diagnostic path cannot change execution.

## Owning repository, affected layers and dependencies

**Owner/layers:** Framework: invocation diagnostics implementation, automatic evidence integration, canonical trace vocabulary/content and Java tests.

**Dependencies:** Requires 31.1.1's selected v1 API and lifecycle integration. 31.1.3 owns Go/TypeScript/MCP/UI consumption; do not wait for it.

## Ticket scope

- Implement the complete embedded Phase 2 contract, including supported concrete JSON
  containers/scalars, capture-time snapshots, concurrent/late writes, finite budgets,
  cycle detection and bounded nonrecursive fallbacks.
- Integrate developer diagnostics and separate system notices into canonical trace data
  using existing frame, identity, timestamp and sequence machinery. No per-event frame,
  extra diagnostic sequence, parallel store or provider-attempt abstraction.
- Reuse automatic boundaries; add missing executor identity/generation evidence through
  existing observation paths without duplicate start/stop/failure records. Preserve their
  existing failure semantics and resource-valued input handling.
- Implement data payloads compatible with the embedded MCP contract, including message,
  severity, supplied JSON and explicit capture states in the existing data content path.
  Final payload names/enum spellings are implementation decisions to carry to 31.1.3.
- Do not add a capture-disable mechanism or make diagnostics depend on Console connection.
  Preserve content and the narrow derived-journal exception.
- Resolve temporary diagnostic compilation debt from 31.1.1 and run its blocked
  supported-surface/lifecycle checks. Hand any expected unknown-record failures in
  Go/TypeScript fixtures to 31.1.3 with concrete evidence; no version increment.

## Acceptance criteria — this ticket only

- [ ] Public invocation tests prove snapshots, invocation isolation, concurrent
  ordering, closure/late-write behavior, fidelity and explicit bounded truncation.
- [ ] Unsupported/cyclic values, oversized messages/data, nesting/traversal/event/total
  limits never trigger arbitrary adapters, collection code, Number conversions or toString.
  Capture work is bounded before building an unlimited representation.
- [ ] Injected ordinary capture/recording/fallback/fallback-logging failures preserve
  successful results and original executor exceptions. Caller expression failures and
  JVM-wide failures are correctly outside this guarantee.
- [ ] One captured diagnostic becomes one record; system notices have no developer
  severity. Limit-notice capacity is reserved, warning floods are bounded/coalesced,
  and reporting never recursively serializes rejected payloads.
- [ ] Boundary input/output, timing, outcome, identity, generation and failure evidence
  retain existing semantics. RecoverableSkillException behavior and no fabricated
  successful output are verified; Console absence does not affect execution.
- [ ] Java fixtures contain mixed existing records, diagnostics and notices in the
  same trace; payload data supports the existing content descriptors/retrieval path.
  Diagnostics never create ERROR_RECORDED failure semantics or alter accounting.
- [ ] Public-surface checks and relevant Framework tests pass, including checks
  blocked by 31.1.1; any Console fixture failures are explicitly handed to 31.1.3.

## Local verification commands

From Framework: `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test`;
run focused diagnostics, trace, executor and lifecycle tests with exact selectors
recorded in execution notes. Run `./mvnw.cmd -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false test`
to identify the Java/Go fixture boundary; 31.1.3 owns expected unknown-vocabulary
failures until its consumer updates land.

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

All Phase 2 generic recording acceptance belongs here. The embedded MCP contract constrains emitted records; Go/MCP/UI acceptance belongs to 31.1.3. Pack diagnostics and functional consumer proof belong to 31.2.2; advanced Console/MCP qualification belongs to 31.3.2.

The following contract extracts carry the roadmap's decisions into this ticket.
Checklist markers are removed to distinguish shared context from this ticket's
acceptance above. The current compatibility marker stays unchanged. Names/defaults
explicitly left to implementation are selected once by their owning ticket. Shared
checks assigned elsewhere are not completion gates here.

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
For example, exceptions or expensive work in a caller expression such as
`response.toJsonData()` occur before entry into the diagnostic method.

This boundary is agreed, not an open design question. Future audits and tickets must
apply it rather than reopen whether the new guarantee covers existing automatic
observation. Revisit only for an explicit scope change or concrete implementation
evidence that violates this boundary. Remaining diagnostic signature, limit, and
concurrency choices do not reopen it.

- Reuse existing observation boundaries without duplicate records; executors
  need not log their own start/stop, input/output, or escaping failure.
- Automatically capture executor start/stop, elapsed time, outcome, invocation/
  skill/executor identity, and executor configuration generation ID.
- Capture input/output through existing evidence paths and controls, including
  existing handling of resource-valued inputs; do not add arbitrary object traversal
  or eager reads of arbitrary resources.
- Capture escaping failure type, message, diagnostic cause, and existing failure
  classification. Preserve `RecoverableSkillException` semantics: corrective feedback
  in standard tool calling, propagation on direct invocation, and termination in
  explicit step planning. It never automatically replays work. Other application
  failures remain terminal; caught failures/retries need explicit diagnostic events.
  Do not introduce a new retryable/terminal exception API.
  Keep existing automatic failure recording/classification; do not map escaping
  exceptions to diagnostic levels or add duplicate `warn`/`error` diagnostic calls.
  A failed execution has no fabricated successful output.
- Record one diagnostic record per captured call within the existing execution
  frame, with plain message, optional
  structured JSON data, Framework-supplied timestamp, invocation association, and
  existing trace sequence. Snapshot data at capture time; define concurrent capture and writes after
  closure without introducing a separate diagnostic sequence or execution scope.
  Use three fixed diagnostic levels selected by `info`, `warn`, and `error` methods,
  each accepting a message and optional JSON. No missing/default or custom levels.
  These levels apply only to developer-authored calls from within a SkillExecutor.
  Framework capture-failure and limit notices are separate system notices without
  these levels; standard Java fallback logging remains separate. Diagnostic severity
  must not change the execution outcome or trigger retries.
- Define supported JSON containers/scalars: objects with string keys,
  arrays/lists, strings, numbers, booleans, and nulls. Do not invoke arbitrary custom
  collection implementations, arbitrary `Number` subclasses, custom adapters, or
  implicit `toString()`.
  Arbitrary DTOs are deferred. Use Jackson with instance-local constraints; consult
  the [serialization research](../phases/decisions-executor-pack-research.md#follow-up-research-bounded-java-object-serialization).
  Validate/snapshot within budgets rather than building an unlimited tree/string
  and truncating afterward. Jackson alone is not an object-execution sandbox.
- Bound message/data bytes, nesting, collection traversal, event counts, and total
  capture per invocation. Detect cycles/unsupported values without implicit conversion.
  Keep valid JSON and explicit unavailable/truncated/capture-failure states.
- Use Jackson for bounded JSON capture. Contain ordinary validation, serialization,
  recording, and limit failures so diagnostics never change the business result.
- On capture failure, emit a small Framework notice with the reason and original
  message when it fits, plus a bounded warning through standard Java logging.
  Reasons include unsupported values, serialization failures, and per-event size
  limits; distinguish partial truncation from no capture. Use a
  separate fixed fallback envelope; stop processing the rejected payload and return
  normally after best-effort reporting. Never recursively serialize or stringify
  rejected data. Contain ordinary failures in both fallback reporting paths.
- Reserve bounded capacity for one invocation-limit notice, then suppress further
  diagnostics. Coalesce/rate-limit repeated warnings.
  Framework notices distinguish known capture conditions when
  recorded; delivery remains best effort, and Console does not infer missing states.
- Document best-effort notice delivery when recording fails, and the limits of the
  guarantee: caller argument evaluation and JVM-wide failures are outside it. Defer
  arbitrary DTO serialization, extension-managed operation handles, and nested spans
  beyond v1; richer capabilities require demonstrated need. Future arbitrary-object
  support also needs explicit acceptance limits.
- Preserve content fidelity. Do not add sensitivity classification or automatic
  masking. Authentication, authorization, capture controls, and resource limits
  remain the boundaries. Retain the existing `ExecutionJournalProjector` exception
  only for the derived journal and observer callback; do not broaden it.
- Test isolation between invocations, concurrent operations, closure/failure,
  fidelity, and explicit truncation using public invocation.
- Verify the new diagnostics preserve successful executor results and original
  executor failures despite ordinary diagnostic serialization, recording, fallback,
  and fallback-logging failures. Retain existing automatic-observation semantics;
  do not require a broader observability rewrite. Test diagnostic-budget exhaustion, bounded notices,
  warning coalescing, and absence of recursive error reporting.

Conceptual usage (not final Java declarations):

```java
// Framework-created, invocation-scoped handle; the pack does not implement it.
var diagnostics = invocation.diagnostics();
diagnostics.info("Sending provider request", Map.of("provider", "typesafe"));
diagnostics.info("Provider response received", responseJsonData);
// responseJsonData is supported JSON data, not an arbitrary provider DTO.
```

Exit: Framework-local tests prove supported diagnostic reporting with Console absent.
The Extensions-owned consumer verifies the same behavior in its own integration tests;
that downstream verification does not hold Framework tickets open.

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

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Introduces serialized trace contracts, bounded capture and concurrent recording with failure containment across multiple execution paths.
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

