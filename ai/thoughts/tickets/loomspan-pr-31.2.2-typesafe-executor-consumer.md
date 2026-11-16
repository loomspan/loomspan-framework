# PR 31.2.2 — Deliver the TypeSafe executor and runnable consumer proof

## Outcome

An application installs loomspan-decisions, declares ordinary executor/config YAML
skills, and invokes Choice, Noul and Score through the supported versioned API.
A runnable local-stub recipe proves real request construction, complete native JSON
results, composition and recorded diagnostics. Advanced consumer lifecycle, accounting,
publication and MCP qualification is assigned to 31.3.2 to keep implementation focused.

## Owning repository, affected layers and dependencies

**Owner/layers:** Extensions production executor, auto-configuration, example, integration fixtures/tests and usage documentation; all ticket execution starts here in Framework.

**Dependencies:** Requires 31.2.1 transport and 31.1.1–31.1.2 supported invocation/diagnostics. The agreed execution order also places 31.1.3 first, but Console is not a runtime dependency. 31.3.1 later removes native code; 31.3.2 owns advanced consumer qualification and the final native-free build verification.

## Ticket scope

- Register a reference-only executor through Spring Boot auto-configuration; install
  dependency alone does not create callable skills. Use default namespace in examples
  and retain existing namespace visibility/configuration publication rules.
- Choose and document the executor name, request/input binding and model-selection syntax.
  These are implementation choices, not a shared Decisions language or legacy expression
  translator. Preserve question identity, native value types, score ordering, confidence,
  refusal and usage meanings. No implicit request splitting, provider fallback or discovery.
- Support Choice, Noul and Score with native text/JSON state, instructions and criteria,
  individually and mixed in one request. Images/Box are future scope; unsupported capabilities
  fail before sending. Refresh evidence from 31.2.1 only if a concrete remaining contract
  uncertainty requires it; do not restart completed provider research.
- Validate pack config and resolved requests every invocation with configuration-path
  errors before any send. No startup/publication validation hook, separate validator API,
  compiled-config cache or Framework changes. Local validation errors never cause retries.
- Build/validate/serialize request once, snapshot selected endpoint/credentials from the
  captured maps, materialize supported local content once and reuse identical retry bytes.
  Do not fetch remote URLs merely to freeze them. Lifecycle reads stay live.
- Preserve values as received. Keep Framework executor-config numeric decoding unchanged;
  document its accepted precision loss without adding precision modes, string conversion
  or additional response/request rounding.
- Return complete native successful JSON including unknown fields, refusals and usage.
  No answers-only extraction, wrapping, renaming, answer-schema/completeness/confidence
  enforcement. Preserve JSON null/string through actual Java invocation using a suitable
  return representation. Do not change Framework null/string conversion semantics.
- Adapt bindings to /answers/...; retain generic runtime checks without generated output
  metadata or output_schema support for executor leaves. Use descriptive errors with the
  existing RecoverableSkillException policy only if deliberately chosen; it is not required.
- Implement one runnable recipe (support triage is a provisional scaffold, not mandatory)
  with app-owned criteria, bindings, policy, refusal/error handling and illustrative thresholds.
  Reusable transport stays in Java. Exercise the packaged artifact, not implementation mocks.
- Establish the runnable consumer and complete the functional checks listed below.
  Keep the harness with Extensions and provide a repeatable command. Implement correct
  integration with the transport/lifecycle/diagnostic contracts now; 31.3.2 adds the
  advanced publication, concurrency, lifecycle, accounting, diagnostic-fault and Console/MCP
  scenarios to this same harness. Do not require those downstream tests to close this ticket.
  Do not rebuild the harness or transport in 31.3.2; leave concrete extension points and commands.
- Show the supported application-owned path to resolved credentials/configuration in docs;
  do not imply native credential bindings or environment fallback now work in executor maps.

## Acceptance criteria — this ticket only

- [ ] Ordinary dependency installation registers a reference-only executor. A consumer
  using only supported API runs direct calls, child composition, valid/invalid parent bindings,
  default-namespace config and Console-free execution.
- [ ] Local stub tests exercise each Choice/Noul/Score type and one mixed request, native
  text/JSON forms, score order, numeric fidelity as received, accepted config conversion,
  binding null/omission and pre-send invalid/unsupported-capability rejection.
- [ ] Actual invocation preserves complete response structure, unknown fields, refusals,
  usage, JSON null and strings. Malformed/empty success fails without decoding-only retry.
  Result fidelity is independent of diagnostic truncation; no schema enforcement is added.
- [ ] Consumer tests verify transport retry count/body identity, HTML/malformed retryable
  errors, redirects never reaching a second endpoint, exact auth/endpoint precedence,
  invalid overrides and prefix preservation. Concurrent distinct-setting consumer
  qualification belongs to 31.3.2; transport isolation tests already belong to 31.2.1.
- [ ] Actual pack invocation records useful request/response/caught retry evidence;
  Console-free execution and normal diagnostic fidelity are verified. Advanced
  fault-injection, accounting and Console/MCP consumer checks are assigned to 31.3.2.
- [ ] Recipe includes complete YAML/needed app Java, stub setup/test credentials, optional
  live service setup, inputs/illustrative outputs, result paths, composition, expected evidence,
  capability limits, exact versions and repeatable verification. This ticket's functional
  acceptance suite passes; advanced qualification remains explicitly assigned to 31.3.2.

## Local verification commands

From Framework, install the identified tested build with `./mvnw.cmd -DskipTests install`.
From Extensions: `mvn -B -f extensions/decisions/pom.xml verify`.
Make this command exercise the packaged-artifact consumer, or document and run the
additional explicit example/harness command alongside it; verify cannot be only unit tests.
Record the consumer build/command and normal trace evidence. 31.3.2 adds the Console/MCP
consumer harness and records its exact build/command. No real API keys or live provider calls.

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

The functional consumer checks listed in this ticket's acceptance belong here. The embedded recipe contract includes downstream qualification: concurrent endpoint/key isolation, publication during requests, lifecycle/resource lifetime, mixed-mission accounting, diagnostic-fault injection and actual Console/MCP inspection belong to 31.3.2. Transport-level tests remain 31.2.1. Generic Framework/Console implementation remains owned by earlier tickets; no new Framework feature or upstream external-consumer gate is authorized.

The following contract extracts carry the roadmap's decisions into this ticket.
Checklist markers are removed to distinguish shared context from this ticket's
acceptance above. The current compatibility marker stays unchanged. Names/defaults
explicitly left to implementation are selected once by their owning ticket. Shared
checks assigned elsewhere are not completion gates here.

### Repository, packaging, and discovery

Create `loomspan-extensions` with concrete code/test/example ownership before ticket
generation. Use one independently versioned `loomspan-decisions` artifact with its
own POM; publication coordinates and release version remain provisional.
The root build may aggregate local builds but must not force all packs to one version.
Add modules only for practical dependency-weight or maintenance needs.

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

- Verify registration and supported-API-only consumption, direct invocation,
  child composition, valid/invalid parent bindings, and Console-free execution.
  Verify source-preserving pack diagnostics and Console/MCP inspection of those same
  recorded diagnostics against the identified builds.
- Verify wire mappings, question identity, received numeric fidelity and accepted
  config conversion, score ordering, selected binding syntax/null/omission behavior,
  and unsupported capabilities/local validation before sends.
- Exercise Choice, Noul, and Score individually and in a single mixed-question
  request through the actual consuming application and packaged executor. Use
  deterministic local HTTP fixtures to verify text/JSON state, instructions, and
  criteria in the native forms supported by each question type, request question
  identity and score ordering, and complete response preservation through invocation.
  Verify unsupported image and Box localization requests fail before any provider send.
- Verify complete response preservation through actual invocation, including
  unknown fields, refusals, JSON null/string values, malformed/empty successful
  responses, and no decoding-only retries. Do not check only HTTP decoding.
- Verify retry cardinality/limits and identical retry bodies, including a retryable
  HTTP failure with HTML/malformed JSON; error capture must not change retry eligibility.
- Pass the [automatic-redirect acceptance test](../phases/executor-packs-and-typesafe-extraction-roadmap.md#automatic-redirects).
- Verify key/endpoint precedence, invalid present overrides, defaults and prefixed
  routes, captured request headers, concurrent distinct endpoints/keys, publication
  during requests, stable old-work settings, and updated new-work settings.
- Verify transport cleanup and the Phase 4 lifecycle/accounting checks, including
  slow progressing responses and resource lifetime. Verify diagnostic failures under
  Phase 2's containment boundary without changing existing automatic observation semantics.
- Provide the runnable recipe, current release-aligned docs, and small index.
  Verify the pre-implementation provider-contract refresh and representative fixtures,
  including model availability and route-specific behavior. Mocked integration is
  not evidence of accuracy, calibration, latency, or cost.

Exit: the external artifact works in its consuming application. Final coordinated
delivery includes Phase 6 removal; no transitional release is required. Public
publication and product release are separate from implementation completion.

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
- **Rationale:** Implements provider request/result semantics and public executor integration with functional consumer proof. Advanced consumer qualification is a separate, later ticket; external protocol and API integration still require full assurance.
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

