# PR 31.3.2 — Qualify the native-free consumer and finish delivery documentation

## Outcome

A developer can follow release-aligned instructions from dependency installation
through the runnable TypeSafe recipe and optional Console inspection. The complete v1
checklist is backed by passing checks against the final native-free local builds.
Extend the existing consumer harness with the advanced integration cases deliberately
assigned here; do not reimplement the executor, transport or functional recipe.

## Owning repository, affected layers and dependencies

**Owner/layers:** Extensions advanced consumer tests/Console harness and delivery docs; Framework-held ticket coordinates final verification and Framework documentation. Production transport/executor implementations belong to earlier tickets.

**Dependencies:** Requires 31.1.1 through 31.3.1. This ticket closes overall delivery evidence without retroactively adding downstream gates to earlier tickets.

## Delivery coverage and acceptance ownership

This matrix assigns evidence; it does not make each row a gate on every ticket.
The final ticket closes the overall checklist using these owners and final build identities.

| Settled contract | Implementation / first acceptance owner | Final evidence |
| --- | --- | --- |
| Exact versioned public API, supported surface, captured identity/config | 31.1.1; blocked API checks restored by 31.1.2 | Framework checks plus functional consumer |
| Live cancellation/deadline and execution-budget bookkeeping | 31.1.1 | 31.3.2 consumer lifetime/deadline tests |
| Bounded JSON diagnostics, failure containment, ordinary boundary semantics | 31.1.2 | 31.3.2 pack fault tests and trace evidence |
| Existing Console/MCP retrieval, record vocabulary, factual presentation | 31.1.3 | 31.3.2 actual pack trace inspection |
| Framework-accounted usage descriptions and generic totals | 31.1.3 | 31.3.2 mixed-mission consumer tests |
| Captured credentials/endpoints, shared pool, redirects, retries/total timeout | 31.2.1 transport tests | 31.2.2 functional calls; 31.3.2 concurrent/lifecycle tests |
| Choice/Noul/Score, mixed requests, invocation validation and request binding | 31.2.2 | Rerun same consumer suite on native-free build |
| Complete native JSON, null/string return boundary, precision and binding changes | 31.2.2 | Rerun same consumer suite on native-free build |
| Recipe/app-owned policy, minimal index, supported API only, Console absence | 31.2.2 functional recipe; 31.3.2 final docs | Packaged artifact and documented commands |
| Publication isolation, physical resource lifetime, advanced pack diagnostics/accounting | 31.3.2 using existing harness | First full qualification here |
| Native declarations/config/dispatch/resources/API/Console removal | 31.3.1 | Local removal checks and native-free consumer build |
| Unchanged compatibility marker, no shims, content fidelity, namespace rules | Every affected ticket | Coherent final builds and required fixtures |

## Ticket scope

- Complete Framework/pack current documentation, small index and runnable recipe
  instructions using the implementation's selected names, syntax, defaults and exact
  tested Framework/Console/Java/Boot builds. No separate AI corpus, manifest, catalog
  platform, generated website or publishing task.
- Pack README describes service setup, capability minimum, independent artifact version,
  install path, Spring registration and reference-only semantics; focused docs cover
  configuration, results, diagnostics and troubleshooting. Use one routing index for
  humans and AI. Namespace teaching stays in Framework; examples use default.
- Record maintainer/release/support ownership and tested combinations without inventing
  service guarantees or claiming public availability. Current unpublished scaffold
  coordinates are ai.loomspan:loomspan-decisions:0.1.0-SNAPSHOT; use actual tested POM
  coordinates. Local install/staging is sufficient; no release-version increment required.
- Document request binding and resolved credential setup, whole native response and
  /answers/... bindings, JSON null/string behavior, no output metadata/schema enforcement,
  accepted config precision difference, invocation-time validation, bounded retries,
  redirects disabled, cooperative cancellation/total timeout and Framework-accounted
  usage. Preserve unknown provider usage in evidence. No unsupported native parity claims.
- Confirm the minimum Choice/Noul/Score individually and mixed-request tests exist and
  pass; images/Box remain future scope. Example thresholds/outputs are illustrative,
  optional live setup is separate from deterministic no-real-credentials verification.
- Preserve the now-aligned roadmap decisions: unchanged compatibility marker, central
  ticket execution, repository-owned code/tests and permitted intermediate build breaks.
  Mark implementation checklists complete only where evidence establishes completion.
- Extend 31.2.2's consumer harness with concurrent distinct endpoints/keys and publication
  during active requests: old work retains captured settings, new work receives the update.
- Add actual consumer cancellation/interruption before send and during retry waits,
  remaining-time capping, slow progressing responses, late physical completion/fencing,
  resource lifetime and shared transport/application-shutdown cleanup checks. Reuse
  31.2.1's stub mechanisms without duplicating its helper-level test suite.
- Add mixed-mission consumer checks for native attempt budgets and Framework-accounted
  usage, including unavailable usage and successful-HTTP decoding failure. Provider
  diagnostics do not contribute totals and unreported usage is not fabricated as zero.
- Add pack diagnostic fault-injection checks within the exact containment boundary.
  Business serialization and caller argument evaluation remain outside that guarantee;
  do not retrofit new non-breaking guarantees into automatic observation.
- Add the actual Console/MCP consumer harness using identified local builds and existing
  tools, and verify those same pack traces. Keep it in Extensions; no separate repository,
  sidecar suite, live provider account or pipeline is required. Console absence changes no result.
- Run the final packaged consumer against the Framework build after 31.3.1 removal.
  Close every deferred verification entry from intermediate commits with passing evidence.
  Record exact commits/versions/commands in execution notes and tested-build docs;
  do not infer success from earlier tests of a pre-removal build.
- This is advanced integration qualification and delivery closure, not a new feature
  or broad production redesign. If verification exposes a production bug within the
  settled contracts, fix it with focused regression evidence and identify the owning
  earlier ticket/scope. Product scope changes still require an explicit separate decision.
  Do not waive failures, silently expand scope or claim overall v1 complete while required
  evidence is outstanding.

## Acceptance criteria — this ticket only

- [x] Current docs/index/recipe cover every item in the embedded recipe checklist with
  actual commands, supported API, no placeholders and consistent exact tested builds.
  Manual Maven installation is complete; publication and live evaluation are not claimed.
- [x] Final Framework public-surface/executor/lifecycle/usage/binding and applicable
  Console/fixture checks pass. Every previously deferred/blocked check has a recorded
  passing result or is shown inapplicable because its obsolete behavior was deliberately removed.
- [x] Independently packaged Decisions artifact passes its complete deterministic consumer
  suite against the native-free Framework: minimum question set/mixed requests, config
  publication, fidelity/null/string, redirects/retries, lifecycle/resource lifetime and
  accounting. Newly added advanced tests prove concurrent distinct settings, stable
  old-work/new-work publication, total deadline despite progressing bodies, late physical
  completion/resource ownership, and diagnostic-fault containment. Recorded pack evidence
  is inspected through existing Console/MCP tools; Console-free invocation also passes.
- [x] The updated roadmap agrees with unchanged compatibility version, commits without
  syncing, permissible intermediate breaks and centrally managed tickets with extension-owned code.
  Existing source research is treated as evidence, not reopened design.
- [x] All overall v1 completion items are backed by named evidence and exact builds.
  Sidecar-suite work, live quality/latency/cost evaluation, public publication and Gemini/OpenAI
  remain separate future work. No compatibility/shim or historical-link preservation work is added.

## Local verification commands

From Framework: `./mvnw.cmd test`, then install the same build locally with
`./mvnw.cmd -DskipTests install`. Run
`./mvnw.cmd -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false test`.
From Console: `go test ./...` and `go run ./internal/buildtool verify`.
From Extensions: `mvn -B validate` and
`mvn -B -f extensions/decisions/pom.xml verify`, plus the exact consumer/Console
harness command finalized by this ticket, extending 31.2.2's existing consumer,
if not included in Maven verify.
These commands describe the required final evidence. Reuse passing owner-ticket results
only when their exact source/build identity is unchanged; do not rerun an identical full
suite merely to produce a new log. The post-removal consumer run and newly added advanced
checks must execute here. Rerun checks affected by later changes or unresolved failures.
Run the documented recipe from its instructions. Record source/build identities
and all exit results; no live provider keys or calls. Validate documentation links
and `git diff --check` in each changed checkout.

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

This ticket owns the advanced consumer scenarios explicitly assigned here, the actual Console/MCP consumer harness, and overall final delivery evidence after native removal. Functional executor/request/result/redirect/retry consumer tests remain owned by 31.2.2 and are rerun, not redesigned. Generic Framework/Console and transport unit tests remain owned by earlier tickets. No downstream gate is retroactively added to Framework tickets.

The following contract extracts carry the roadmap's decisions into this ticket.
Checklist markers are removed to distinguish shared context from this ticket's
acceptance above. The current compatibility marker stays unchanged. Names/defaults
explicitly left to implementation are selected once by their owning ticket. Shared
checks assigned elsewhere are not completion gates here.

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

- Implement the agreed generic Framework capabilities and remove native TypeSafe support.
- Provide the separate Decisions Maven artifact with the TypeSafe executor.
- In `loomspan-extensions`, verify the artifact through its small consuming example
  against identified Framework/Console builds, using a local stub HTTP provider and
  deterministic mocked responses without real API keys or live provider access.
- Verify Console/MCP inspection of recorded evidence and execution without Console.
- Provide one runnable recipe and current documentation matching those tested builds.
- Pass required tests, including public-surface and Framework/Console compatibility checks.

Track artifact publication and product releases separately from this implementation gate.
No simultaneous release, automated cross-repository pipeline, or public Maven publication
is required to pass it. Maintain coherent contracts and compatibility versions in the
tested builds. Do not describe a dependency as publicly available until it is published.

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

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket now owns first-time cross-component assurance of lifecycle, concurrent publication, accounting and diagnostic faults through the real packaged consumer, plus the Console/MCP harness. The outstanding integration assurance warrants full planning and independent review.
- **Reassessment triggers:** Full already covers scoped test design and production fixes needed to satisfy settled contracts. A new API, provider capability, accounting facility or other product scope expansion requires a separate explicit decision.

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

### Route, scope and implementation decisions

User approved **Full 5-Step Pipeline** with “proceed.” Initial Framework source
was `fa1ca190d393b63314846e604a6bbd8157ed8eab`, Extensions source was
`4a52c709080ecff70ca363aacad9a02ee707666f`; both working trees were clean. Research,
both plans, code and documentation below are ticket-owned. Step 4 changes only
Extensions tests/POM/documentation and Framework documentation/planning artifacts.
No production correction, API addition, configuration/wire contract change, marker
increment, compatibility shim, push, sync or publication was needed. Fresh Step 5
review and root-owned local commits follow this implementation evidence.

- Extended the existing independent installed-JAR `support-triage` consumer. Observer
  views are thread-safe, concurrent traces select a particular session, and worker
  authentication is explicit. Startup limits are configurable; publishable mission/
  quota settings use the existing nested `session` map. Shutdown remains startup-only.
- `PublicationConsumerTest` interlocks an old active request with new admissions on
  distinct endpoints/keys, separately tests both configuration owners, preserves old
  retry bytes/settings, and observes the same transport bean across publication.
- `LifecycleConsumerTest` proves real cancellation/interruption before first send
  and during retry waits, no second send after expiry, original parent budget despite
  delayed planning/progressing body, zero remaining time, late-result/diagnostic
  fencing, generation retention until physical return and application-shutdown cleanup.
  The supported retirement callback establishes retention; no Framework internal
  imports or bean replacement contract were introduced.
- `ConsumerProbe` discovers the installed executor's annotated method with ordinary
  Spring/Java reflection and delegates to the real transport. For late physical return
  it holds a real completed pack result until released. This proves local retained
  work, not remote inference cancellation. Normal live/config accessors remain real.
  Mockito instrumentation only forwards the real invocation while targeting diagnostic
  faults or forwarding diagnostics with a retry-entry signal. It establishes no
  consumer customization API. The pack uses INFO/WARN; Framework's existing tests
  cover ERROR/recorder/notice/fallback containment. Caller/business serialization and
  existing automatic observation keep their exact original boundary.
- Framework cleanup grace is fixed at 250ms, not a configurable larger test setting.
  The consumer directly observes zero budget while physical work is retained and
  generic `MissionLifecycleTest` establishes grace separation. No synthetic lifecycle
  value substitutes for actual cancellation or deadline acceptance.
- `AccountingConsumerTest` uses a real native parent with exact two-attempt quota,
  actual packaged leaf retries, reported/heuristic native usage, complete unknown
  provider usage, missing provider usage and successful-HTTP decoding failure.
  Only native model calls contribute; canonical `totalUnits` is authoritative.
- `ConsoleConsumerTest` runs the canonical stamped executable under the explicit
  `console` Maven profile/property, normal cookie/Origin/tab/CSRF pairing and MCP
  Bearer access. It imports the same unmodified actual pack traces, checks all thirteen
  tools and scope descriptions, logical/physical mixed type/frame filters, bounded
  page/range continuation without loss/duplicates, browser/MCP parity and retained
  data fidelity. Real Unicode message truncation and oversized response unavailability
  produce explicit notices without severity; authored ERROR alone creates no failure.
- Windows Console ownership requires a user-owned temporary parent rather than
  shared checkout inherited Modify ACLs. The helper uses OS `TEMP` for isolated
  profile/workspace, retains process logs in example `target/`, and closes process/
  client ownership after use. Security checks remain enforced without bypass.
- Matching repository-local `loomspan-docs` routes were consulted after executable
  inventory. Pre-removal build references and incomplete qualification language were
  documentation drift; updated pack/current Framework topics are now **aligned**.
  No source/doc semantic conflict or new product decision remained.

### Exact final builds and verification

Coordinates/versions, source fingerprint and recipe commands are owned by sibling
`extensions/decisions/docs/tested-builds.md`. Production source remains at the two
exact commits above. Artifact SHA-256 values actually consumed:

| Build | SHA-256 |
| --- | --- |
| Native-free Framework starter JAR | `3f4bd57de312f65012d717ba987dcfa8dba4fc9902206c4949413bc6e7fe52eb` |
| Installed Decisions JAR | `5b93cbf94ca0daca7d4ce0cb767dd5993d96f66ceb7bf17f64b5d9900490be6e` |
| Canonically rebuilt Windows Console | `2c897cc1fb76379daf0268ae5de773fefa73362b6a61f0c13d49da3e64dfa68c` |
| Consumer POM plus 15 source/resource files, algorithm in tested-builds | `f677b1d099275c0cbb98a55a99d0102f9e399a418d56d3b5625b0c836f09cc89` |

Runtime: JetBrains Java 21.0.2, Boot 4.2.0-RC2, Windows 11 amd64; Maven 3.9.6,
Framework wrapper 3.9.11; Console Go 1.26.5, Node 24.18.0, npm 12.0.2.
The final commit IDs are reported after root commits; no circular commit hash is
invented in these files. Exact production bytes/source and consumer fingerprint
identify the tested pre-commit tree.

PASS receipts (exit 0 unless the explicitly expected negative case):

| Working directory / exact command | Result / log |
| --- | --- |
| Framework `./mvnw.cmd test` | 2,226 tests, 0 failures/errors, two unchanged opt-in `Pr18RecordedHandoffDiagnosticTest` skips; `$env:TEMP/loomspan-framework-3132.log` |
| Framework `./mvnw.cmd -DskipTests install` | Native-free build locally installed; packaging intentionally skips tests; `$env:TEMP/loomspan-framework-3132-install.log` |
| Framework `./mvnw.cmd -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false test` | 18 cases, no failures/errors/skips; `$env:TEMP/loomspan-corpus-3132.log` |
| Console `$env:PATH = "$env:TEMP\loomspan-pr29.3-node-24.18.0\package\bin;" + $env:PATH; go run ./internal/buildtool build` | Verified exact toolchain; typecheck, 604 frontend tests/coverage, asset/skill checks and `go test ./...`, then versioned binary; `$env:TEMP/loomspan-console-build-3132.log` |
| Extensions `mvn -B validate` | Descriptor validation; `$env:TEMP/loomspan-extensions-3132-validate.log` |
| Extensions `mvn -B -f extensions/decisions/pom.xml verify` | 77 tests, no failures/errors/skips; `$env:TEMP/loomspan-pack-3132-verify.log` |
| Extensions `mvn -B -f extensions/decisions/pom.xml -DskipTests install` | Independent pack installed; prior verify supplies tests; `$env:TEMP/loomspan-pack-3132-install.log` |
| Extensions `mvn -B -f extensions/decisions/examples/support-triage/pom.xml verify` | 17 executed passing cases, one explicit Console opt-in exclusion; `$env:TEMP/loomspan-consumer-3132.log` |
| Extensions `mvn -B -f extensions/decisions/examples/support-triage/pom.xml -Pconsole "-Dloomspan.console.executable=$((Resolve-Path ../loomspan-framework/loomspan-console/build/loomspan-console.exe).Path)" verify` | Final installed native-free build: all 18 tests, no failures/errors/skips; `$env:TEMP/loomspan-final-consumer-3132.log` |
| Extensions `mvn -B -f extensions/decisions/examples/support-triage/pom.xml -DskipTests compile exec:java` | Complete native fixture and expected illustrative routing policy; no Console/real credentials; `$env:TEMP/loomspan-recipe-3132.log` |
| Extensions `mvn -B -f extensions/decisions/examples/support-triage/pom.xml -Pconsole -Dtest=ConsoleConsumerTest test` without executable | Expected exit 1, clear required-property assertion, zero skips; proves explicit qualification cannot silently skip; `$env:TEMP/loomspan-console-required-3132.log` |
| Current doc local links/anchors and `git diff --check` in both checkouts | 118 links/anchors across seven current docs valid; no whitespace errors; final expanded link check includes roadmap execution-note link |

The canonical Console build executes the same verify checks and full Go suite.
Separate identical `go test ./...` and buildtool `verify` reruns were not performed
merely to make another log; this is the ticket/plan's permitted exact-source reuse.
No Go concurrency production change requires race detection. No live inference,
sidecar suite or public release was run or claimed.

Initial targeted harness iterations had compile/nested-config/expected-field
assertion errors, all corrected before the passing complete suites. The initial
Console shared-checkout profile hit Windows ownership protection; the user-temp
fixture resolved that without any production/security change. No production bug
or unresolved required failure remains.

### Prior debt closure

| Prior handoff | Final disposition |
| --- | --- |
| 31.1.1 temporary diagnostic/API compilation gates | Restored in 31.1.2, current full suite includes exact v1/surface/executor/lifecycle checks |
| 31.1.2 Go corpus `UNSUPPORTED_VALUE` from new diagnostics | Restored by 31.1.3; current canonical Console full Go checks and 18-case Java/Go corpus pass without marker rewriting |
| Earlier native TypeSafe tests/fixtures/contracts | Deliberately removed by 31.3.1; no requirement to restore obsolete native declarations, generated metadata, readers or direct client tests. Current executor/binding/corpus and native-rejection checks pass |
| 31.2.1 helper transport checks | Current packaged artifact verify passes all 77 cases; advanced real consumer cases additionally pass |
| 31.2.2 advanced qualification and Console deferred here | Publication/lifecycle/accounting/fault tests and actual Console browser/MCP process pass; functional suite rerun on final native-free installed build |
| 31.3.1 post-removal Extensions qualification not run | Final 18-case installed consumer and executed recipe now pass against identified final source/JAR |
| Opt-in Pr18 diagnostics, Go race, sidecar/live/provider publication | Two unchanged diagnostic opt-ins are unrelated to acceptance; no Go production concurrency delta; sidecar/live evaluation/publication are explicitly separate future work |

### Acceptance mapping

| Ticket criterion | Concrete evidence |
| --- | --- |
| Current docs/index/recipe and complete manual Maven install | Pack README routing, focused executor/transport/provider/tested-build docs, recipe full YAML/Java and executed install/recipe commands; maintainer/support/unpublished scope explicit |
| Generic Framework/supported surface/lifecycle/usage/binding and Console/fixture checks | Current 2,226-case Framework run, architecture test in full suite, 18-case corpus and canonical Console build/Go/604 frontend checks; prior debt table above |
| Independently packaged complete consumer and same recorded evidence inspected | Installed-JAR assertion plus `TypeSafeConsumerTest`, `CompositionConsumerTest`, `PublicationConsumerTest`, `LifecycleConsumerTest`, `AccountingConsumerTest`, `DiagnosticFaultConsumerTest`, `ConsoleConsumerTest`; final all-18 command above and Console-free suite |
| Roadmap coherent with current policy | Phases 1–6/checklist updated with named evidence; unchanged marker and central tickets/owning-repo code, local commits without sync, permitted historical intermediate breaks preserved |
| Overall v1 named evidence/exact builds, bounded future work | Tested-build identity/evidence matrix plus roadmap mapping; no compatibility work, public publication, live provider evaluation, sidecar suite or later-provider feature added |

All seven recipe items are covered: exact dependency/build table; local stub/test key
and separate optional live setup; linked complete YAML/Java in `default`; concrete
input and labeled illustrative output; full `/answers/...`/composition paths and
capability limits; expected Console evidence/troubleshooting; executed repeatable
commands with matching source/version. No optional developer check is needed.
