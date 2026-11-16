# PR 31.1.1 — Provide versioned executor invocation and cooperative lifecycle

## Outcome

Replace the unversioned executor handoff with a Framework-owned v1 class and
live read-only lifecycle observations. Finalize the diagnostic declarations as
part of the same supported API, so subsequent work implements a coherent contract.

## Owning repository, affected layers and dependencies

**Owner/layers:** Framework: supported API, registration, invocation construction, lifecycle bookkeeping and architecture tests.

**Dependencies:** First ticket. No downstream acceptance dependency. 31.1.2 implements the diagnostic capability declared here; 31.2.1/31.2.2 prove pack behavior later.

## Ticket scope

- Complete the cross-layer native removal inventory from the existing source review;
  record affected surfaces and required/changed/retired behavior in execution notes for
  31.3.1. Include direct/child invocation, retries, precision, output metadata,
  generation retention, physical cancellation and authoritative/unavailable usage.
- Retain captured input, skillConfig, executorConfig, skill name and executor config
  generation ID. Add diagnostics access and the settled lifecycle accessors. Use a
  Framework-created class with stable read-only accessors, not an expanding public record.
  Final construction/accessor names and the provisional package
  `ai.loomspan.api.extensions.v1` are implementation choices.
- Replace registration and all current invocation construction/use sites. Accept only
  the exact supported parameter type. Retain annotation-based registration and shared
  internal execution. Remove the unversioned record without adapters or constructor aliases.
- Choose final diagnostic signatures and JSON value surface using the embedded Phase 2
  contract. Implementation of bounded capture/recording is 31.1.2; do not present a
  temporary inert implementation as completed diagnostics. Intermediate compilation
  breaks may be explicitly handed to 31.1.2 rather than hidden by compatibility plumbing.
- Implement thread-safe live cancellation/deadline reads using existing lifecycle owners.
  Preserve parent/shutdown limits and distinguish execution budget from cleanup grace.
  No separate timeout scheduler, state machine, physical cancellation callback or accounting API.
- Deliberately update public-surface policy, allowlists, README and supported-surface tests
  for the exact versioned types. Extend policy to these selected subpackages only.
- Exercise the supported invocation with decisions-shaped and non-AI executor fixtures.
  The lifecycle portion of this ticket is a real implementation, not deferred API stubs.

## Acceptance criteria — this ticket only

- [ ] Exact v1 registration succeeds; old/unsupported parameter types are rejected;
  the old invocation surface is absent and no internal/autoconfigure types leak.
- [ ] Public invocation tests preserve captured input/configuration and generation
  identity, namespace reference/access behavior, direct and child execution.
- [ ] Live lifecycle tests cover no deadline, zero expiry, cancellation/interruption,
  parent/shutdown constraints, execution budget versus cleanup grace, and no budget reset
  at executor entry. Existing late-write fencing/resource retention remain intact.
- [ ] Diagnostic declarations support the Phase 2 semantics without arbitrary DTO or
  provider-shaped API; 31.1.2 is explicitly the recording implementation owner.
- [ ] The cross-layer removal inventory and README/public API policy are updated.
  Run `LoomspanPublicSurfaceArchitectureTest` and relevant existing executor/lifecycle
  tests. Record deferred verification with an owner if 31.1.2 compilation temporarily blocks it.

## Local verification commands

From Framework: `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest,ExecutorDependencyResolutionTest,ExecutorGenerationExecutionIntegrationTest test`.
Run the focused lifecycle/accessor tests added by this ticket and record their exact
command in execution notes. If the shared build is temporarily blocked, name 31.1.2
as the owner to restore diagnostics-related compilation and rerun these checks.

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

The Phase 1 inventory/API and generic Phase 4 lifecycle checks are this ticket's acceptance. Phase 2 recording and its failure-injection tests belong to 31.1.2; pack sends/retries/transport lifecycle tests belong to 31.2.1/31.2.2. Embedded downstream checks do not gate this ticket.

The following contract extracts carry the roadmap's decisions into this ticket.
Checklist markers are removed to distinguish shared context from this ticket's
acceptance above. The current compatibility marker stays unchanged. Names/defaults
explicitly left to implementation are selected once by their owning ticket. Shared
checks assigned elsewhere are not completion gates here.

## Phase 1 — Finalize the reviewed public contract and removal inventory

- Review the current TypeSafe implementation and agree dispositions for all eight
  findings. Evidence is in the source review; no implementation/test completion is implied.
- Complete the cross-layer removal inventory using the finished source review;
  check native TypeSafe coupling across Framework, Console, MCP, fixtures,
  documentation, configuration, dependency management, and public catalog values.
- Carry the scope rule above into future implementation tickets. Tie each
  Framework API change to an explicitly listed generic roadmap enhancement; keep
  TypeSafe-specific checks and provider behavior in the pack.
- Carry the already-agreed required/changed/retired behavior into the removal
  inventory and acceptance checks. Include direct/child invocation, answer fidelity, retry ownership,
  physical cancellation, generation retention, and exact/unavailable usage.
- Finalize diagnostic declarations under the Phase 2 contract.
- Define the versioned package contract, provisionally
  `ai.loomspan.api.extensions.v1.SkillExecutorInvocation`, and colocate its public
  capability types. Recognize exact supported parameter types at registration;
  reject unsupported types. The Java parameter selects the invocation contract;
  no separate pack manifest or manifest validation is required.
  No separate version annotation or per-call version switch is needed.
- Implement exact v1 contract recognition without future-version adapters or
  legacy support. Retain existing `@SkillExecutor` annotation-based registration
  and use shared internal execution machinery.
- Exercise the design against decisions and a non-AI example, such as an HTTP
  request or document conversion. Avoid provider-shaped generic abstractions.
- Design a Framework-owned invocation class with stable read-only accessors,
  rather than an expanding public record. Retain captured input, skill configuration,
  executor configuration, skill name, and executor configuration generation ID; add
  diagnostics and the Phase 4 live accessors. Captured values and live lifecycle reads
  are distinct. Settle construction without exposing mutable/internal lifecycle state.
- Replace the existing unversioned invocation record and registration path.
  Update every construction/use site and test; add no old-constructor shim or adapter.
- Explicitly update public-surface policy and architecture allowlists for the
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
- **Rationale:** Changes the supported Java contract, registration and concurrent lifecycle observations; full planning and independent review remain warranted despite settled product intent.
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

