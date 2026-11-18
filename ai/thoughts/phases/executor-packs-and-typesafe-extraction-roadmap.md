# Executor packs and TypeSafe extraction roadmap

Status: agreed v1 direction and implementation roadmap; implementation is not completed by this planning work.
Original design date: 2026-11-15. Decision reviews and document consolidation: 2026-11-16.

## How to use these documents

This roadmap is the authoritative home for agreed decisions, implementation scope,
acceptance criteria, ticket requirements, and delivery tracking. Each topic lives
in its owning section below; phase checklists reference those contracts.
The [research document](decisions-executor-pack-research.md) owns dated external
evidence and design rationale. The [migration review](typesafe-executor-pack-migration-review.md)
owns current-code findings and source/test anchors. Neither companion restates the
implementation contracts. Update the owning section when a decision changes and
update links or evidence only where needed.

Future tickets must still be self-contained under the ticket requirements below.
That requirement applies to tickets, not to duplicating decisions across these documents.

## Outcome

Remove native TypeSafe execution from Loomspan Framework and deliver it through
`loomspan-decisions`, an optional, independently maintained Maven artifact in the separate
`loomspan-extensions` repository. Framework owns generic observable execution;
the pack owns provider integration; applications own callable YAML skills and policy.
TypeSafe is the complete v1 provider scope. Gemini and OpenAI are planned follow-up
additions to this same extension, with requirements deferred until the repository/
structure, native removal, and one solid TypeSafe example are complete.

V1 proves one dependency, one consuming application, one runnable recipe, useful
diagnostics, and a small documentation index. No unified Decisions request/result
API or native decisions grammar is introduced. See Phase 5 for the pack contract.

## Handoff and verification history

The earlier six-gap review and five-issue follow-up are resolved. Their decisions
are incorporated into the owning sections, not maintained as parallel summaries.
Unchecked implementation tasks or unselected signatures/defaults do not reopen
settled decisions. Future audits need concrete conflicting text or implementation
evidence, or an explicit scope change, rather than restarting those discussions.

The five-issue follow-up inspected executor registration/reference visibility and ran
`./mvnw.cmd -q -Dtest=ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest test`:
12 tests passed with no failures, errors, or skips. This proved existing namespace
behavior, not extraction. Its documentation checks covered 62 local file/heading
links, fence balance, stale wording, and `git diff --check`; the earlier review
checked 60 links. No full suite or refreshed provider research was run.

The current audit's JSON return-boundary clarification is agreed and incorporated
in Phase 5. The redirect decision and acceptance test are now settled in
[Automatic redirects](#automatic-redirects); current native evidence is in the
migration review. The timeout-scheduler audit note concerned duplicated
migration-review wording removed by consolidation; Phase 4's existing authoritative
contract remains unchanged. Consolidation itself selected no new timeout policy.

The post-consolidation fidelity recheck compared all three saved originals with the
consolidated documents, including qualifications unique to a companion document.
It restored omitted or weakened details in their owning sections. The breaking-change
policy, namespace contract, completion gate, full MCP and cancellation handoffs,
and Phase 7 scope were also checked for exact preservation. This was a documentation
audit, not production testing or a refresh of provider research.

Earlier planning/review work created no production implementation, tickets, extension
repository, Maven release, site, or live provider evaluation. The user subsequently
created `loomspan-extensions`; its local scaffold status is recorded in Phase 5.
Recheck Git and repository state when resuming; historical handoffs are not live status reports.
Read repository guidance and `loomspan-console/AGENTS.md` before implementation.
Repository setup is complete at scaffold level. The user subsequently authorized
PR 31.x.x implementation tickets, held and executed from Framework even when they
edit the sibling Extensions checkout. Ticket creation does not start implementation.

### Local state at context handoff

Observed 2026-11-16: Framework is on `main`, two commits ahead of its local
`origin/main` reference, with these three planning documents modified. Extensions
is on `codex/scaffold-extensions`; its scaffold files are still untracked and its
HEAD is the initial repository commit `60a53ca`. This context made no commits or
pushes. Preserve the working files and recheck both repositories before proceeding;
the existing Framework commits are not evidence that these document edits were committed.

The detailed fidelity review covered 55 source groups spanning all 2,460 backup
lines. After the redirect update, all 76 local planning-document links and code
fences passed validation, as did `git diff --check`. Scaffold descriptor checks
are recorded with the setup evidence in Phase 5; they are not implementation tests.

For recovery or comparison only, the pre-consolidation copies, fidelity-review
ledger, and comparison scripts are in the local temporary directory
`C:/Users/mgiacomi/AppData/Local/Temp/loomspan-phase-dedup-f180a064062245728e9e038c8b7d658d`.
These temporary backups may be removed by system cleanup; the consolidated documents
remain authoritative and contain the retained decisions.

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
Keep `consoleCompatibilityVersion` unchanged for this undeployed development work.
Update Java, Go, TypeScript,
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
> tests, fixtures, and documentation to the new design. Coordinate current
> Framework/Console contracts without incrementing consoleCompatibilityVersion. Implement only
> the explicitly listed generic SkillExecutor enhancements; do not add TypeSafe-
> specific Framework behavior. Preserve the existing executor/YAML skill namespace
> relationship. Versioned invocation packages start with the new v1 only; no legacy
> invocation support is required.

Each ticket must identify its affected layers and acceptance checks. Inapplicable
layers need no changes, but omission of a layer from one ticket does not authorize
leaving it inconsistent across the overall work. No extra compatibility approval
step is required for changes already covered by this policy.

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
output-schema hook, provider registry, or provider-accounting API is authorized.
Pack validation, response handling, and numeric behavior are specified in Phase 5.
Accepted reductions are limited to those explicitly recorded here; extraction does
not automatically approve other behavior reductions.

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
Do not copy internal Framework services into the external pack. Provider integration
is reimplemented in pack-owned code.

Console is optional and observational: packs run fully without it installed or
connected. Framework owns execution and diagnostic recording; presentation cannot
control execution, budgets, cancellation, or result semantics.

The phases below organize the work, not a requirement to build a complete extension
platform before trying it. Prove the smallest working Decisions slice first. Use a
non-AI fixture to check generality; a second published pack, rich catalog, presentation
component library, and speculative future-version adapters are not v1 prerequisites.

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

## Ticket preparation and completion evidence

### Settled test ownership and mandatory ticket handoff

Create `loomspan-extensions` before generating implementation tickets, so extension
code, integration tests, and examples have concrete ownership. This documentation
decision does not itself create that repository.

Repository-local ticket acceptance and overall roadmap delivery are different:

All PR 31.x.x tickets are stored and executed from `loomspan-framework/ai/thoughts/tickets/`.
The pipeline may edit the sibling `loomspan-extensions` checkout directly; separate
ticket files or pipeline launches there are unnecessary. Code, tests, examples, and
documentation remain in their owning repository. Commit ticket-owned changes after
each ticket in each affected repository, without pushing or syncing. Intermediate
build breaks are allowed; record blocked checks and their restoration owner rather
than adding compatibility shims. All required checks must pass for final delivery.

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
acceptance gates. Before making model-quality or production-threshold recommendations,
evaluate representative labeled application tasks and record the evidence. This is
not required to complete implementation tickets or the mocked example; no live
evaluation or benchmark project is an implementation gate.

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
diagnostics; Console/MCP contract updates at the existing compatibility version; pack transport/config/
lifecycle; TypeSafe executor and external consumer; native removal and current
documentation. They may be combined or reordered coherently. Do not turn Phase 7
possibilities into v1 tickets. Require the existing public-surface test for production
type changes and the relevant Console/fixture checks described by repository guidance.

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

- [x] Implement the agreed generic Framework capabilities and remove native TypeSafe support.
- [x] Provide the separate Decisions Maven artifact with the TypeSafe executor.
- [x] In `loomspan-extensions`, verify the artifact through its small consuming example
  against identified Framework/Console builds, using a local stub HTTP provider and
  deterministic mocked responses without real API keys or live provider access.
- [x] Verify Console/MCP inspection of recorded evidence and execution without Console.
- [x] Provide one runnable recipe and current documentation matching those tested builds.
- [x] Pass required tests, including public-surface and Framework/Console compatibility checks.

### Final local qualification evidence

Implementation checks above and in Phases 1–6 are complete against native-free
Framework/Console production source `fa1ca190d393b63314846e604a6bbd8157ed8eab`
and Decisions production source `4a52c709080ecff70ca363aacad9a02ee707666f`, with
Extensions-owned advanced qualification added by PR 31.3.2. The unchanged marker is
`1.0.0-beta.10-SNAPSHOT`. No push, sync, publication or live inference is included.

| Roadmap items | Named final evidence |
| --- | --- |
| Phase 1: exact invocation/API and removal inventory | Framework full suite (2,226 tests; two unchanged opt-in skips), `LoomspanPublicSurfaceArchitectureTest`, `VersionedExecutorInvocationTest`, `ExecutorGenerationExecutionIntegrationTest`; 31.3.1 removal inventory and current JAR inspection |
| Phase 2: bounded diagnostics and contained faults | `BoundedExecutorDiagnosticCaptureTest`, `DefaultExecutorDiagnosticsTest`, `ExecutorDiagnosticsIntegrationTest`; actual pack `DiagnosticFaultConsumerTest` |
| Phase 3: current record vocabulary/Console/MCP, factual states and totals | Java trace corpus (18 cases), canonical Console build (604 frontend tests and full Go checks); actual imported pack traces in `ConsoleConsumerTest` |
| Phase 4: live budget/cancellation, physical retention and accounting reductions | Framework lifecycle/accounting suites; installed-pack `LifecycleConsumerTest`, `PublicationConsumerTest`, `AccountingConsumerTest` |
| Phase 5: separate artifact, supported questions/bindings/response/transport, recipe/index | Pack verify (77 tests), complete installed-JAR consumer (18 tests with Console), executed `TriageApplication`, pack README/config/transport/tested-build references |
| Phase 6: removal and coherent current consumers | 31.3.1 execution evidence plus final full Framework/corpora/Console checks and post-removal packaged consumer; no obsolete native classes in installed JAR |

Exact commands, artifact SHA-256 values, qualification source fingerprint, instrumented
probe boundaries and prior-debt closure are recorded in the
[delivery ticket execution notes](../tickets/loomspan-pr-31.3.2-executor-pack-delivery-closure.md#execution-notes--2026-11-16)
and the sibling Extensions historical reference at
`77e7e29:extensions/decisions/docs/tested-builds.md`.
Fresh pipeline review and local commits follow Step 4 qualification; this evidence
records implementation verification rather than claiming that review has already run.

Track artifact publication and product releases separately from this implementation gate.
No simultaneous release, automated cross-repository pipeline, or public Maven publication
is required to pass it. Maintain coherent contracts and compatibility versions in the
tested builds. Do not describe a dependency as publicly available until it is published.

## Phase 1 — Finalize the reviewed public contract and removal inventory

- [x] Review the current TypeSafe implementation and agree dispositions for all eight
  findings. Evidence is in the source review; no implementation/test completion is implied.
- [x] Complete the cross-layer removal inventory using the finished source review;
  check native TypeSafe coupling across Framework, Console, MCP, fixtures,
  documentation, configuration, dependency management, and public catalog values.
- [x] Carry the scope rule above into future implementation tickets. Tie each
  Framework API change to an explicitly listed generic roadmap enhancement; keep
  TypeSafe-specific checks and provider behavior in the pack.
- [x] Carry the already-agreed required/changed/retired behavior into the removal
  inventory and acceptance checks. Include direct/child invocation, answer fidelity, retry ownership,
  physical cancellation, generation retention, and exact/unavailable usage.
- [x] Finalize diagnostic declarations under the Phase 2 contract.
- [x] Define the versioned package contract, provisionally
  `ai.loomspan.api.extensions.v1.SkillExecutorInvocation`, and colocate its public
  capability types. Recognize exact supported parameter types at registration;
  reject unsupported types. The Java parameter selects the invocation contract;
  no separate pack manifest or manifest validation is required.
  No separate version annotation or per-call version switch is needed.
- [x] Implement exact v1 contract recognition without future-version adapters or
  legacy support. Retain existing `@SkillExecutor` annotation-based registration
  and use shared internal execution machinery.
- [x] Exercise the design against decisions and a non-AI example, such as an HTTP
  request or document conversion. Avoid provider-shaped generic abstractions.
- [x] Design a Framework-owned invocation class with stable read-only accessors,
  rather than an expanding public record. Retain captured input, skill configuration,
  executor configuration, skill name, and executor configuration generation ID; add
  diagnostics and the Phase 4 live accessors. Captured values and live lifecycle reads
  are distinct. Settle construction without exposing mutable/internal lifecycle state.
- [x] Replace the existing unversioned invocation record and registration path.
  Update every construction/use site and test; add no old-constructor shim or adapter.
- [x] Explicitly update public-surface policy and architecture allowlists for the
  selected versioned packages: current policy only permits allowlisted top-level
  types directly in `ai.loomspan.api`. Add README and supported-surface tests;
  arbitrary classes in a versioned package do not become supported API.

One extension contract version covers the invocation handoff, diagnostics, and
diagnostic presentation interpretation; pack release versions remain separate.
Do not introduce independently selected execution/presentation schema versions.
Descriptive documentation changes need no contract bump unless interpretation or
execution behavior changes. Future optional display hints may use generic fallback;
required unsupported behavior must be rejected. A version field/package name alone
does not establish support for an arbitrary class. Framework supplies the exact
recognized handoff; the pack does not implement it.

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
For example, exceptions or expensive work in a caller expression such as
`response.toJsonData()` occur before entry into the diagnostic method.

This boundary is agreed, not an open design question. Future audits and tickets must
apply it rather than reopen whether the new guarantee covers existing automatic
observation. Revisit only for an explicit scope change or concrete implementation
evidence that violates this boundary. Remaining diagnostic signature, limit, and
concurrency choices do not reopen it.

- [x] Reuse existing observation boundaries without duplicate records; executors
  need not log their own start/stop, input/output, or escaping failure.
- [x] Automatically capture executor start/stop, elapsed time, outcome, invocation/
  skill/executor identity, and executor configuration generation ID.
- [x] Capture input/output through existing evidence paths and controls, including
  existing handling of resource-valued inputs; do not add arbitrary object traversal
  or eager reads of arbitrary resources.
- [x] Capture escaping failure type, message, diagnostic cause, and existing failure
  classification. Preserve `RecoverableSkillException` semantics: corrective feedback
  in standard tool calling, propagation on direct invocation, and termination in
  explicit step planning. It never automatically replays work. Other application
  failures remain terminal; caught failures/retries need explicit diagnostic events.
  Do not introduce a new retryable/terminal exception API.
  Keep existing automatic failure recording/classification; do not map escaping
  exceptions to diagnostic levels or add duplicate `warn`/`error` diagnostic calls.
  A failed execution has no fabricated successful output.
- [x] Record one diagnostic record per captured call within the existing execution
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
- [x] Define supported JSON containers/scalars: objects with string keys,
  arrays/lists, strings, numbers, booleans, and nulls. Do not invoke arbitrary custom
  collection implementations, arbitrary `Number` subclasses, custom adapters, or
  implicit `toString()`.
  Arbitrary DTOs are deferred. Use Jackson with instance-local constraints; consult
  the [serialization research](decisions-executor-pack-research.md#follow-up-research-bounded-java-object-serialization).
  Validate/snapshot within budgets rather than building an unlimited tree/string
  and truncating afterward. Jackson alone is not an object-execution sandbox.
- [x] Bound message/data bytes, nesting, collection traversal, event counts, and total
  capture per invocation. Detect cycles/unsupported values without implicit conversion.
  Keep valid JSON and explicit unavailable/truncated/capture-failure states.
- [x] Use Jackson for bounded JSON capture. Contain ordinary validation, serialization,
  recording, and limit failures so diagnostics never change the business result.
- [x] On capture failure, emit a small Framework notice with the reason and original
  message when it fits, plus a bounded warning through standard Java logging.
  Reasons include unsupported values, serialization failures, and per-event size
  limits; distinguish partial truncation from no capture. Use a
  separate fixed fallback envelope; stop processing the rejected payload and return
  normally after best-effort reporting. Never recursively serialize or stringify
  rejected data. Contain ordinary failures in both fallback reporting paths.
- [x] Reserve bounded capacity for one invocation-limit notice, then suppress further
  diagnostics. Coalesce/rate-limit repeated warnings.
  Framework notices distinguish known capture conditions when
  recorded; delivery remains best effort, and Console does not infer missing states.
- [x] Document best-effort notice delivery when recording fails, and the limits of the
  guarantee: caller argument evaluation and JVM-wide failures are outside it. Defer
  arbitrary DTO serialization, extension-managed operation handles, and nested spans
  beyond v1; richer capabilities require demonstrated need. Future arbitrary-object
  support also needs explicit acceptance limits.
- [x] Preserve content fidelity. Do not add sensitivity classification or automatic
  masking. Authentication, authorization, capture controls, and resource limits
  remain the boundaries. Retain the existing `ExecutionJournalProjector` exception
  only for the derived journal and observer callback; do not broaden it.
- [x] Test isolation between invocations, concurrent operations, closure/failure,
  fidelity, and explicit truncation using public invocation.
- [x] Verify the new diagnostics preserve successful executor results and original
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

## Phase 3 — Generic Console integration

Console is solely a development and troubleshooting tool. Framework records the
execution independently; Console observes it when installed and connected. The
v1 view combines automatic boundary evidence and extension-authored message/JSON
records under the same extension contract version. No presentation descriptor is
required from the pack.
The factual-presentation boundary follows the
[Framework feature design lens](../framework-feature-design-lens.md#12-keep-semantic-authority-at-the-owning-boundary).

- [x] Walk through successful execution, recoverable/terminal failures, and a caught
  provider failure followed by retry using the simple diagnostic records.
- [x] Carry operations and evidence through trace serialization, authenticated
  inspection routes, and existing MCP/Console readers where applicable.
- [x] Integrate new diagnostic record types and content into the existing
  `LOOMSPAN_query_trace_records` and `LOOMSPAN_read_trace_content` tools, following
  the settled contract below. Reuse existing summaries and retrieval behavior;
  do not add diagnostic-specific tools, severity filters, or pagination machinery.
- [x] Display automatic timing, generation ID, input/output, and failure evidence
  alongside individual chronological diagnostic records with expandable JSON, grouped
  beneath the existing execution frame. Bound rendering work; grouping must not merge records.
- [x] Console reports recorded facts; it never synthesizes results or infers execution
  outcomes from diagnostic levels, message text, arbitrary payload fields, or timing.
  Display Framework-recorded invocation outcome/classification and executor-authored
  messages/JSON as recorded. Developers use that evidence to distinguish a caught
  request failure/retry from an invocation failure or a negative business decision.
  No typed provider-attempt tracking or provider-specific interpretation is needed in v1.
  Grouping, filtering, and factual aggregates do not create new semantic outcomes.
- [x] Display executor-authored diagnostic messages, severity, and JSON as recorded;
  the SkillExecutor developer determines their content and meaning. Display Framework
  execution facts and capture notices separately; capture notices have no developer
  severity. Console does not generate explanatory error messages or infer capture
  state from missing evidence. Display unavailable, truncated, or failed
  capture only when explicitly recorded by Framework. Notice delivery is best effort:
  if recording fails and no notice survives, Console has no cause to explain.
- [x] Apply the accounting descriptions specified in Phase 4.
- [x] Keep the Framework-to-Console compatibility version unchanged and update
  Java/Go/TypeScript transport, MCP readers, Console, and fixtures together. Remove
  obsolete shapes under the breaking-change policy. Console currently uses its exact
  release string for `consoleCompatibilityVersion`; use actual repository release/version
  machinery rather than inventing independent negotiation.
- [x] Verify Console absence or presentation failure cannot affect execution/results.
  Defer display hints, custom layouts, and extension-specific renderers beyond v1.
- [x] Add representative fixtures and reader/UI tests, including a non-AI executor.
- [x] Verify existing MCP type/frame filtering, pagination, inline content, and content
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

- [x] Implement the accessors and verify the settled cancellation acceptance checks above.
- [x] Verify late physical return, fencing, and resource lifetime. Keep work within
  the invocation until physically complete, or explicitly manage retained client
  resources; untracked background I/O may outlive generation retention.
- [x] In Extensions, verify bounded retries, SDK retry configuration, and cooperative
  cancellation through the consuming example; apply Phase 5 transport requirements.
- [x] Verify a mixed mission: only Framework-accounted model calls contribute to
  totals; executor-managed requests/retries do not reserve native attempt budget,
  and diagnostic usage does not affect totals. Check Console/API/MCP scope descriptions.
  Include unavailable usage, successful-HTTP decoding failure, and Console absence.
- [x] Document accepted lifecycle/accounting reductions and removals in current
  usage docs/release notes under the breaking-change policy.

Exit: minimal lifecycle facilities and the explicitly reduced guarantees are verified.
Full native accounting/physical-cancellation parity is not a completion gate.

## Phase 5 — Prove the separate Decisions Executor Pack

### Repository, packaging, and discovery

Create `loomspan-extensions` with concrete code/test/example ownership before ticket
generation. Use one independently versioned `loomspan-decisions` artifact with its
own POM; publication coordinates and release version remain provisional.
The root build may aggregate local builds but must not force all packs to one version.
Add modules only for practical dependency-weight or maintenance needs.

Setup evidence (2026-11-16): the user created
`https://github.com/loomspan/loomspan-extensions`; the local sibling checkout is
`C:/opendev/code/loomspan-extensions`, with scaffold work on
`codex/scaffold-extensions`. It contains a root aggregator, an independent Decisions
POM, main/test source and resource directories with `.gitkeep` markers, and a support-triage example
location. Initial unpublished coordinates are
`ai.loomspan:loomspan-decisions:0.1.0-SNAPSHOT`, with Java 21 as the starting compile
target. Root `mvn -B validate` and standalone
`mvn -B -f extensions/decisions/pom.xml validate` passed using Maven 3.9.6 / Java 21.0.2.
These validate descriptors only: no executor, runnable example, Framework/Boot
integration, consumer tests, or publication is complete. The new Framework dependency
will be selected when an identified build supplies the supported v1 invocation API.

Starting directory convention, not a frozen specification:

```text
loomspan-extensions/
  pom.xml
  tooling/
    catalog/
  extensions/
    decisions/
      pom.xml
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

Empty tooling/documentation scaffolding is not a deliverable. Each pack owns its
publication. Maven supplies binaries and dependency resolution; Spring Boot
auto-configuration registers pack beans. Document that behavior rather than lengthening
the artifact name. Installation makes reference-only executors available; applications
explicitly declare callable skills under the existing namespace rules.

Use the POM for artifact identity, version, and Framework dependency; the Java
parameter identifies the extension contract. Record the targeted extension contract,
exact installation details, and tested Framework/Java/Boot combinations in release-aligned
documentation. The README describes the pack, tested combinations, and required
service setup. No separate manifest/schema/validation is required, including for
application-owned executors.

Keep documentation with the pack. A small index linking release-aligned docs and one
runnable example suffices. The same docs serve humans and progressive AI discovery
through a routing skill/index; no separate AI corpus or discovery service is needed.
The intended discovery flow is pack → compatible release → dependency → service
configuration → recipe → run, with optional Console inspection. Viewing a catalog
does not install or execute anything. A generated website/searchable catalog is not
a v1 gate; Maven manual installation remains complete. Publication follows the
separate release policy in the completion gate.

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

### Transport and HTTP outcome handling

Reuse a pack-owned transport/connection pool, with Spring bean construction and
application-shutdown cleanup. Set effective URL, authorization, and timeout per
request. Do not cache authenticated clients by URL/key/generation, mutate shared
credential defaults, or recreate transport per invocation/publication. Generation
retention still protects captured configuration, but this shared pool needs no
generation-retirement callback. This does not promise one transport can serve every
incompatible connection setting. Apply Phase 4's total-versus-inactivity timeout and
cooperative lifecycle contract; bounded retries and SDK retry settings belong to the pack.
HTTP timeout handling adds no Framework HTTP API.

The conceptual TypeSafe flow reports the request with a plain-message/JSON diagnostic,
caught failures/retries as useful, and response evidence as useful through the
invocation-scoped facility. Preserve supplied source content within the Phase 2
capture bounds. This is pack diagnostic usage, not a typed provider-attempt contract;
Framework still owns automatic boundaries and escaping-failure evidence.

Classify transport failures and HTTP status before successful-response JSON decoding.
Retry eligible failures only under bounded policy and live lifecycle checks; otherwise
fail execution. Error-body capture/decoding must not change that classification:
HTML or malformed JSON on a retryable HTTP error does not remove retry eligibility.
Carry endpoint/header validation, retryable status handling, bounded error capture,
and retry limits into pack-owned code without importing internal policy classes.

#### Automatic redirects

Keep automatic HTTP redirects disabled in the TypeSafe pack, matching the native
client. Send to the endpoint selected from captured configuration. Treat redirect
responses as unsuccessful HTTP responses through the ordinary failure/diagnostic
path; do not follow their `Location` headers, issue requests to redirect targets,
or send credentials to those targets. Applications configure a different endpoint
explicitly when needed. This decision settles redirect following only; the broader
bounded retry policy remains a pack implementation choice.

Acceptance (Extensions-owned consuming-example test): configure a local stub endpoint
that always returns a redirect with a `Location` pointing to a second stub endpoint.
Invoke the actual pack with test credentials and verify that the redirect target
receives no request, and the invocation fails through the ordinary failure path.
Use deterministic local fixtures with no real credentials or live provider access.

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

### Recipe and consumer acceptance

Keep reusable integration/transport code in Java; task-specific inputs, questions,
criteria, output bindings, access policy, and business actions belong in application
recipes where the pack contract supports them. Start with one runnable recipe such
as support triage, including refusal/error handling and illustrative application
thresholds. Relevance/severity recipes are future candidates, not deliverables.
The scaffold's `support-triage` directory is a provisional application name, not a
Maven/Loomspan convention or a committed choice of example use case. It can be renamed
or replaced when the first recipe is selected.

Every supported recipe includes:

1. Exact dependency and tested Framework versions.
2. Local stub setup and test credentials for repeatable integration; separately
   document application-supplied credentials/service setup for optional live use.
3. Complete YAML and needed application Java, using `default` under the namespace rule.
4. Sample invocation inputs and clearly labeled illustrative outputs.
5. Result paths, composition/binding examples, and capability constraints.
6. Expected Console evidence and troubleshooting guidance.
7. A repeatable verification command and links to matching source/version.

Keep the recipe with its pack release; no separate recipe versioning system is
needed. Users may copy/customize it; integration fixes arrive through dependency
updates rather than copied HTTP implementation patches. Settle request binding,
coordinates, and release/support policy before publishing examples; examples must
stay within the supported text/JSON capability set above.

Extensions owns the following checks through its small consuming example, using the
packaged artifact against identified builds and deterministic local HTTP stubs with
test credentials. Apply the repository-local ownership and completion rules above.

- [x] Verify registration and supported-API-only consumption, direct invocation,
  child composition, valid/invalid parent bindings, and Console-free execution.
  Verify source-preserving pack diagnostics and Console/MCP inspection of those same
  recorded diagnostics against the identified builds.
- [x] Verify wire mappings, question identity, received numeric fidelity and accepted
  config conversion, score ordering, selected binding syntax/null/omission behavior,
  and unsupported capabilities/local validation before sends.
- [x] Exercise Choice, Noul, and Score individually and in a single mixed-question
  request through the actual consuming application and packaged executor. Use
  deterministic local HTTP fixtures to verify text/JSON state, instructions, and
  criteria in the native forms supported by each question type, request question
  identity and score ordering, and complete response preservation through invocation.
  Verify unsupported image and Box localization requests fail before any provider send.
- [x] Verify complete response preservation through actual invocation, including
  unknown fields, refusals, JSON null/string values, malformed/empty successful
  responses, and no decoding-only retries. Do not check only HTTP decoding.
- [x] Verify retry cardinality/limits and identical retry bodies, including a retryable
  HTTP failure with HTML/malformed JSON; error capture must not change retry eligibility.
- [x] Pass the [automatic-redirect acceptance test](#automatic-redirects).
- [x] Verify key/endpoint precedence, invalid present overrides, defaults and prefixed
  routes, captured request headers, concurrent distinct endpoints/keys, publication
  during requests, stable old-work settings, and updated new-work settings.
- [x] Verify transport cleanup and the Phase 4 lifecycle/accounting checks, including
  slow progressing responses and resource lifetime. Verify diagnostic failures under
  Phase 2's containment boundary without changing existing automatic observation semantics.
- [x] Provide the runnable recipe, current release-aligned docs, and small index.
  Verify the pre-implementation provider-contract refresh and representative fixtures,
  including model availability and route-specific behavior. Mocked integration is
  not evidence of accuracy, calibration, latency, or cost.

Exit: the external artifact works in its consuming application. Final coordinated
delivery includes Phase 6 removal; no transitional release is required. Public
publication and product release are separate from implementation completion.

## Phase 6 — Remove native TypeSafe implementation from core

- [x] Delete native `typesafe:` declarations/parser branches, connection driver/model
  configuration support, dispatch, clients, resource coupling, and dependencies used
  exclusively by native TypeSafe. Reimplement provider behavior in the pack.
- [x] Keep executor-backed leaves on existing `JAVA` classification.
- [x] Remove `SkillKind.TYPESAFE` and other obsolete public/internal surfaces; update
  all usages and allowlists. No deprecated aliases, translators, or compatibility shims.
- [x] Replace old examples and consumer docs with current executor-pack usage. Remove
  obsolete TypeSafe-specific Console/MCP handling and historical shape assumptions.
- [x] Apply the mandatory ticket policy across the coordinated Framework/Console/MCP
  changes, keeping the compatibility version unchanged and updating matching fixtures.
- [x] Update fixtures and supported-surface tests to describe the final design.
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

These are implementation details, not approval gates or invitations to reopen
settled decisions:

1. Phase 1: exact versioned package names, invocation/accessor declarations, and construction.
2. Phase 2: diagnostic signatures, supported concrete JSON containers/numbers,
   snapshot mechanism, concurrent/late-write behavior, and resource-limit values.
3. Phase 4: thread-safe lifecycle reads and minimal execution-deadline bookkeeping.
4. Phase 5: executor name, request binding/model selection, validation details/error
   messages, retry/default settings, total-timeout/client implementation.
   Illustrative JSON Pointer builders are not an approved shared Decisions language.
5. Packaging: Maven group coordinates, maintainer/release ownership, release/support
   policy, and tested Framework/Java/Boot combinations; artifact name is settled.
6. Sequencing and coordinated contract updates at the unchanged compatibility version;
   publication/releases remain separate from implementation completion.

The resolved audit decisions are tracked in the handoff; this list approves no new
policy. Provider research is dated: recheck it at
implementation. No unselected detail authorizes compatibility layers, normalization,
precision modes, startup validation hooks, schema hooks, caches, or namespace changes.
