# Beta 4 code grounding

This report records source evidence and its limits. Current requirements are
the roadmap and phase contracts; the [ticket-preparation handoff](beta4-ticket-readiness.md)
assigns implementation and acceptance ownership. Its boundaries supersede
older illustrative PR groupings; no test result here proves future features.

Baseline: `1e4eb455478a979b4c2d4aa7152a34abe752f188`, framework
`1.0.0-beta.4-SNAPSHOT`. All nine phase documents were checked against this
checkout. This is source/test grounding of existing behavior, not feature
implementation or proof of the future Sidecar. No Sidecar implementation
exists in this checkout. No sibling repository existed during the original
grounding; the developer subsequently created `C:/opendev/code/loomspan-sidecar`
with README/license files and a remote. The ticket-preparation handoff records
its current bootstrap status. Its application wiring, image, and end-to-end checks must
still be proved when implemented. No tickets have been written.

Source anchors below are repository-relative. Java production paths start
at `loomspan-spring-boot-starter/src/main/java/ai/loomspan/`; corresponding
tests are under `src/test/java/ai/loomspan/` unless stated otherwise.

## Fresh recheck — 2026-09-12

HEAD remains `1e4eb455`; this pass independently read the cited code rather
than treating the previous report as proof. Product policies G1–G3 stand.
G3's earlier ordered-listener proposal is superseded by independent event
listeners and a later framework lifecycle wait, as settled in
[fresh-review R1](beta4-design-review.md#r1--independent-shutdown-listeners--resolved).
The lock and completion findings below remain required implementation work.

The subsequent accepted simplicity pass narrows the implementation design:
one shared runner operation owns admission through session finalization and
observer delivery, followed by one release; no separate observer job or
tracking registry. Sidecar uses one admission/accounting owner, one executor
queue, and one retained-record store. Initial SC2/SC3 API and JWT work is
delivered together. Reuse existing declaration locations, eager registration
completion, and integration fixtures. These decisions do not change the
current-code evidence or establish that the future implementation works.

- FW1: rechecked definition/model invariants, registration completion,
  direct invocation/reference resolution/authentication scope, generic
  shallow copying, and diagnostic kind projection. Those change points
  remain real; REST is not implemented. `DefaultRegisteredSkillCatalog`
  still assumes every non-YAML registration is Java; the Go validator still
  accepts only YAML/JAVA.
- FW2: rechecked Map/Object null behavior, facade exception mapping,
  session-free role evaluation, and observer placement. The observer runs
  after runner completion. Shutdown ownership confined to the runner would
  end before view mapping/callback finishes. FW1/FW2 must coordinate this
  lifetime within the existing shared deadline.
- Shutdown: `MissionLifecycle#runWithAncestry` obtains ordinary locks and
  runs the supplied action under them; `beginCancellation` calls the failure
  recorder there too. `DefaultExecutionStateService#recordStepEvent` and
  `recordFailure` reach `LoomspanSession#appendTraceRecord` under this fencing.
  A blocked trace write can therefore block cutoff lock acquisition itself.
  Neither `awaitCutoff`'s timer nor moving I/O off the shutdown thread alone
  fixes waiting for a lock held by that I/O. Implementation must provide
  deadline-safe cutoff signalling and retain fencing after blocked writes
  resume. FW1 now requires this adversarial test explicitly.
- `MissionWorkExecutor` does not currently catch `CancellationException`,
  unlike `StepLoopMissionExecutionEngine`. New externally initiated shutdown
  cancellation must preserve primary-failure/cleanup semantics for direct
  missions too; test this path rather than assuming the two engines are
  interchangeable. No public cancellation API is implied.
- `StepLoopMissionExecutionEngine#executeToolAction` propagates failures;
  `executeAssignedTask` collects ordinary concurrent failures, and
  `propagateFirstFailure` propagates after folding the unit. The existing
  `ordinaryFailureDoesNotCancelSiblingAndFoldsEveryOutcome` test confirms
  the parent fails after sibling completion. No speculative recovery policy
  is needed. Its redundant exception-type check before identical throws is
  a small cleanup candidate if that method is edited.

Independently checked Spring 7.0.8's
[close sequence](https://github.com/spring-projects/spring-framework/blob/v7.0.8/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java)
and [standard multicaster](https://github.com/spring-projects/spring-framework/blob/v7.0.8/spring-context/src/main/java/org/springframework/context/event/SimpleApplicationEventMulticaster.java).
Events precede lifecycle stop/destruction, and async opt-out is honored.
But a listener exception can stop delivery; context close then proceeds to
teardown. R1 avoids a listener-order dependency by keeping both event
handlers prompt and moving the wait to the subsequent lifecycle-stop stage.
These sources do not make framework lock acquisition bounded. Failed/skipped listener cleanup must
retain bounded destruction fallback; arbitrary application hooks remain
outside the framework timeout guarantee.

Fresh command: `./mvnw.cmd -pl loomspan-spring-boot-starter
-Dtest=LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,StepLoopMissionExecutionEngineTest,JavaSkillMissionCutoffTest,MissionLifecycleTest,SkillInputValidatorTest,RefResolverTest,YamlSkillCatalogTests,YamlSkillCapabilityRegistrarTests,DefaultRegisteredSkillCatalogTest
test -q`. **219 tests passed**, zero failures/errors/skips. Existing behavior
only; no new shutdown or Sidecar integration tests were implemented. No
production code, fixtures, release tags, or tickets changed.

## Phase findings

### FW1 — feasible through the existing lifecycle, with corrected assumptions

- `internal/core/CapabilityKind` contains only YAML and Java; the architecture
  allowlist contains the existing eight API types. REST and its SPI do not
  exist yet. Deliberate additions remain required.
- `internal/skill/YamlSkillCatalog#readManifest` already reads a raw tree and
  rejects unknown fields. `YamlSkillManifest` tracks declared fields even
  when setters normalize null/empty values. Reuse those boundaries to enforce
  boolean `rest: true` and forbidden-field presence before ordinary model
  validation. `YamlSkillDefinition` currently rejects a null execution
  configuration despite its nullable component annotation; catalog loading,
  definition invariants, and registration all need coherent REST handling.
  A model-required exemption in the registrar alone is insufficient.
- `YamlSkillCapabilityRegistrar#completeRegistration` completes Java
  discovery, registers YAML definitions, then checks child references. It is
  synchronized/idempotent and already called by the diagnostic catalog.
  Preserve that registration authority; handler construction must not require
  a completed public catalog. Validate Sidecar's route/catalog correspondence
  after registration, without remote probes.
- `internal/core/ExecutionCoordinator#executeBound` runs Java invokers inside
  `MissionWorkExecutor` and `ScopedAuthentication`, resolves inputs, and
  opens/closes the same mission frames as YAML. Generalizing its Java branch
  is the appropriate reuse point. `internal/runtime/tool/DefaultCapabilityInvoker`
  owns child success/task/evidence credit after the router returns; thrown
  failures take its failure path. Do not create a parallel REST engine.
- **Correction:** `SkillMethodBeanPostProcessor#invokeSkillMethod` converts
  many ordinary Java runtime exceptions into text through
  `LoomspanExceptionTransformer`. It rethrows security failures and some
  argument failures. REST's agreed thrown-failure policy must deliberately
  bypass that Java-specific adapter. `DefaultSkillTemplate` preserves
  `AccessDeniedException`/`SkillException`, wraps other runtime exceptions,
  and does not catch JVM `Error`. The direct coordinator also turns a null
  invoker result into the text `"null"`; the REST adapter must explicitly
  reject a null handler return before that conversion.
- **Correction:** generic `SkillInputValidator` normalization copies only the
  outer map. Schema-backed recursion and `RefResolver` freeze some nested
  structures, but neither establishes deep immutability for every accepted
  value or for direct construction of the new public invocation record.
  `SkillExecutionEvent` contains a JSON-like immutable-copy implementation
  worth reusing/narrowing, but it rejects Resource values. Use the resolved G1 boundary below
  when narrowing shared copy logic; do not promise immutable Resource contents.
- `RefResolver#resolveArguments` resolves generic `ref://` values recursively;
  schema-backed resolution is selective for attachment/runtime-ref nodes.
  `SessionLocalVirtualFileSystem` resolves to a Spring `FileSystemResource`
  inside the current session namespace. Java parameter binding subsequently
  converts Resources to strings, streams, bytes, or typed values; the proposed
  REST map handler has no such parameter binding. See G1.

Evidence: `YamlSkillCatalogTests`, `YamlSkillCapabilityRegistrarTests`,
`RefResolverTest`, `SkillInputValidatorTest`, `JavaSkillLifecycleIntegrationTest`,
`JavaSkillMissionCutoffTest`, `JavaSkillAuthenticationScopeIntegrationTests`,
and `DefaultSkillTemplateTest`.

### FW2 — additions justified; existing semantics are more specific

- `CapabilityMetadata.tool().inputSchema()` and registry enumeration provide
  the proposed catalog data. The existing diagnostic catalog deliberately
  has different operator fields; reuse registration/metadata, not its DTO
  shape. An immutable public snapshot is sufficient. Public enum projection
  must not force public API code to depend on observability.
- `DefaultSkillTemplate` normalizes/validates before session creation; the
  router checks access and validates again, and the coordinator checks access
  again at its mission boundary. Reuse preparation and `SkillRoleEvaluator`
  for the new session-free pre-check without adding another invoke pass or
  removing execution-time checks as an incidental cleanup.
- **Correction:** a null `Object` is always rejected before conversion;
  a null `Map` is allowed only for generic/empty-permitting contracts.
  `validate` must preserve each overload's own behavior, not unify them.
- The observer is currently called synchronously only after success, outside
  the facade's wrapping try/catch. `observerExceptionPropagatesAfterExecutionCompletes`
  protects unchanged propagation of a successful observer's exception.
  On failure the current `ExecutionResult` is never returned, so FW2 needs
  internal session capture through failure as well as success; not a new
  public session handle or retrieval API. Capture at the existing runner/
  facade boundary and invoke the observer after session completion. Preserve
  the primary failure if history mapping or the failure callback also fails.
- `LoomspanSession#finalizeTrace` projects and retains the journal before
  finalizing/deleting the canonical file. `SkillExecutionViewMapper` maps
  that journal, not the Console store. This supports the agreed immediate
  terminal GET history independently of trace persistence. Projection itself
  can fail; available-history wording remains necessary. No streaming or
  asynchronous history preparation is needed.
- Public events are already documented as current-version diagnostics in
  `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`.
  Reuse that contract, including its limited sanitization guarantee.

### FW3 — contract changes begin with FW1

- `DefaultRegisteredSkillCatalog` branches YAML versus everything else as
  Java. `RegisteredSkillEntry#validateLocation` and the observability DTO
  constructors reject sources other than YAML/JAVA. Merely changing the
  label fails validation; REST must carry YAML source path/text without Java
  bean/method fields.
- `loomspan-console/internal/observability/service.go#validateSkillSummary`
  also rejects a third source. Its `dto.go` checks source-specific fields.
  `web/src/api/contracts.ts`, `SkillCatalog.tsx`, and `SkillDetail.tsx` encode
  two alternatives. All required Java/Go/TypeScript/fixture changes belong
  with the first REST protocol change, per the accepted D1 decision.
- **Correction:** `ConsoleRestFixtureCorpusTest` owns `application-rest`
  fixtures. `ConsoleTraceFixtureCorpusTest` owns trace/analysis fixtures;
  use it only if those inputs or projections change. Both use the existing
  regeneration flag. There is no reason to regenerate unrelated corpora.
- **Correction:** `consoleCompatibilityVersion` is currently the Maven
  project version, injected through
  `src/main/resources-filtered/META-INF/loomspan-release.properties` and
  loaded by `LoomspanReleaseVersion`; it is not an independent schema number.
  `scripts/loomspan_version.py` coordinates versions across framework,
  Console fixtures/source, and skills. Record that REST expands protocol
  semantics within beta 4 and release matching framework/Console versions.
  Do not invent a separately incremented schema version. If the project
  requires incompatibility detection between snapshots with the same marker,
  that is a separate versioning-policy decision, not already supplied here.
- Mission trace frames currently contain route/parameters, not a dedicated
  capability-kind field. Do not add one solely to label a catalog entry or
  infer historical kind from the current catalog. Existing mission lifecycle
  events suffice for direct REST execution; add semantic data only if a
  specific diagnostic requirement needs it.
- The diagnostic catalog currently exposes no description field. FW1's
  promise to add descriptions there was unnecessary; descriptions belong in
  the new public catalog, while existing observability source fields suffice.

### FW4 — concrete documentation and release anchors exist

- `SupportedSurfaceIntegrationTest` already uses a local OpenAI-compatible
  endpoint, YAML parent, Java leaf, and public observer. Extend that fixture
  rather than build another supported-surface test harness.
- Java API guidance lives under `agent-skills/loomspan-docs/references/java-api/`;
  authoring under `references/skill-authoring/`. Update invocation,
  observation/errors, Java skill comparisons, API allowlists, and README
  alongside the new REST material, including the Java/REST exception difference.
- `scripts/loomspan_version.py` directly checks the root/module POM and the
  loomspan/loomspan-docs skill version markers and updates tracked version
  occurrences. There is no need to clone this machinery into Sidecar without
  its own set of coupled version artifacts.
- `.github/workflows/publish.yml` provides manual verification and automatic
  publication on a matching non-SNAPSHOT tag; `console-release.yml` handles
  Console packaging and `console-ci.yml` validates Console/skills. Framework
  release readiness can precede the Sidecar integration gate, but tagging
  cannot. Release dry runs and remote workflow runs are future acceptance
  work, not performed by this investigation.
- README's example tag commands currently include the development SNAPSHOT
  string even though its release prose/workflow reject it. Correct concrete
  release examples in FW4; do not copy them uncritically into Sidecar docs.

### SC1 — platform and startup assumptions

- Root POM pins Java 21, Boot 4.1.0, Spring AI 2.0.0. The Boot BOM pins
  Framework 7.0.8 and Security 7.1.0. Use these concrete versions initially;
  do not implicitly upgrade from current online documentation.
- The starter uses Jackson 3 (`tools.jackson` databind), with Jackson
  annotations in `com.fasterxml.jackson.annotation`. Sidecar should use
  Boot's configured mapper, not copy the starter's internal codec beans.
- `LoomspanProperties.Skills` and `YamlSkillCatalog` use Spring resource
  pattern resolution; `file:` skill locations are supported. Separate
  directory scanning and route parsing require no filename exception.
  Empty model/connection maps are allowed, but a YAML planner needs an
  actual configured model/connection; mount-only examples must supply that
  configuration or a documented local model stub too.
- The starter's POM does not provide a complete JWT/Actuator application.
  Sidecar must declare its web, resource-server JWT/JOSE, Actuator, and
  HTTP-client/SSL integration dependencies explicitly under the matching BOM.
  Confirm its eventual dependency tree and application-context startup.

### SC2 — public facade supports the design, not unlimited physical concurrency

- The HTTP store/queue/ownership logic is new Sidecar code, not a wrapper
  around an existing public job API. FW2 is a real dependency: there is no
  current public catalog, pre-check, or failure-observer path.
- Propagate the verified authentication to the Sidecar thread calling
  `invoke`. The facade captures it and the coordinator reinstalls it around
  the actual invoker on a separate framework executor. Root and nested
  authentication tests protect this existing chain.
- Measure the retained HTTP input object's JSON size; `validate` returns
  void, not a replacement normalized map. Normalization happens again during
  execution. Do not invent an additional public normalized-request type for
  accounting.
- `MissionWorkExecutor` submits work to the framework virtual-thread
  executor. Timeout/cancellation can return the facade while uncooperative
  work still runs; `JavaSkillMissionCutoffTest` deliberately proves this.
  Therefore `max-concurrent` bounds Sidecar calls into the facade, not every
  nested/lingering HTTP operation. Sidecar can release its own references on
  worker exit but cannot promise other threads have released them. See G3.
- Existing quotas bound invocation/model/usage counts, not request-body,
  final-result, or whole-observer-history bytes. See G2.

### SC3 — identity propagation aligns; configuration requires explicit wiring

- `SkillRoleEvaluator` uses the default `ROLE_` or Spring's
  `GrantedAuthorityDefaults`, and optionally a `RoleHierarchy`.
  A Sidecar `role-prefix` claim converter alone does not change the
  framework evaluator. Wire the same configured prefix through standard
  Spring role configuration; no Loomspan internal bean replacement.
- Sidecar's custom `loomspan-sidecar.auth.jwt.*` prefix is not automatically
  consumed by Boot's `spring.security.oauth2.resourceserver.jwt.*` binding.
  Sidecar must connect its configuration to the standard decoder/validators.
  Apply issuer, audience, lifetime, and required-claim rules for all three
  key-source modes. There is no current Sidecar code proving that wiring.
- The opt-in Console namespace is independently protected by Loomspan's API
  key filter, not JWT. README requires application security to permit that
  namespace through to the filter. Scope Sidecar JWT rules to `/v1/**` and
  preserve the documented operator path if observability is enabled. Health
  remains separately controlled on the management listener. JWT-only inbound
  execution does not remove Console's operator authentication mechanism.

### SC4 — fixed HTTP mapping is viable; attachments are not JSON conversion

- Name-based routes can use FW2's catalog after registration, without reading
  manifests or depending on internal registry types.
- Spring's RestClient/SSL facilities support the intended client approach.
  Pin/build one client configuration per target with deliberate timeout,
  redirect, and SSL settings. Use a response stream bounded while reading;
  materializing an unbounded body and then checking length does not enforce
  the agreed response cap. Avoid default error-body buffering when creating
  the agreed bounded HTTP error messages.
- No generic Resource-to-JSON, multipart, base64, or attachment upload contract
  exists in the phase design. Do not silently add one. See G1.
- Actual URL encoding, charset/media-type handling, per-target TLS/timeout
  composition, and host callback verification need the SC4 stub/context tests
  against its pinned dependencies; source review is not that integration proof.

### SC5 — bounded shutdown is not established

- Sidecar stops dispatch immediately rather than draining queued jobs.
  Framework work runs on an independent
  virtual-thread executor. `LoomspanAutoConfiguration#LoomspanMissionExecutor`
  declares `destroyMethod = "close"`. JDK 21 `ExecutorService.close()` waits
  for termination; interruption does not make uncooperative work terminate.
  The current framework supplies no bounded destruction of that bean. G3 now
  specifies the source-grounded lifecycle fix and its required integration tests.
- Container termination grace must accommodate the framework shutdown budget;
  ordinary Boot web graceful shutdown alone does not close Sidecar dispatch
  or implement bounded framework executor termination.
  Do not claim a bounded JVM exit based only on worker interruption.
- Image choice, mounted file permissions, local issuer/model/REST stubs, probes,
  released dependency resolution, and image publication still need Sidecar
  implementation and acceptance checks. The existing framework release
  workflow demonstrates the release pin/tag discipline; it does not prove
  a Sidecar image or workflow that does not yet exist.

## Decisions exposed by grounding

### G1 — Shared skill input processing — resolved

The developer confirmed REST should receive inputs through the same pipeline
as other skills. Reuse validation, normalization, and reference resolution.
Java binds the resulting arguments to method parameters; RestSkillHandler
receives them as a map alongside skillName. This is the handler's argument
representation, not a new input mechanism or context abstraction.

The framework owns immutable input containers at that handoff and for direct
construction of its public invocation value. Reuse/narrow existing copy logic;
normalization alone is not a complete defensive copy. Do not create another
schema validator, conversion system, or REST-specific input vocabulary.
Existing reference resolution can produce Resource handles; container copying
does not make their backing content immutable.

Sidecar v1 independently enforces JSON-only transport and visibly rejects
non-JSON values before outbound calls. It adds no uploads, multipart/base64
conversion, or separate attachment subsystem. JSON/text responses stay as
agreed. No additional product decision remains for G1.
### G2 — Request/result/history byte limits (SC2) — resolved

The developer agreed to a configurable 1MB incoming JSON limit, enforced
while reading and before parsing/validation; oversize returns 413 without
execution. The key is loomspan-sidecar.executions.max-input-size.

The developer rejected final-result and diagnostic-history byte caps.
Sidecar retains and returns produced results and selected history intact;
no size-based omission, truncation, or result-too-large failure. Existing
diagnostic selection, record-count, and completion-TTL limits remain.
The earlier 256KB/1MB diagnostic cap and final-result cap proposals are
superseded. No total-heap guarantee or trace-projection redesign is implied.
The existing per-target HTTP response cap in SC4 is a separate transport
setting and is unchanged by this final-result/history decision.
### G3 — Shutdown ownership and deadline — resolved and source-grounded

The developer also agreed the framework itself must reject new top-level
executions after shutdown starts. Root admission and shutdown need an atomic
boundary before root session execution; pre-check success is not a reservation.
Already-admitted sessions may continue nested work within their deadlines.
Sidecar discards queued work directly rather than submitting it for rejection;
the framework gate catches races and also protects embedded applications.

Sidecar immediately closes admission and dispatch on shutdown. It discards
queued work and releases accounting/input/security references; no queue
draining or Sidecar timer. Synchronize dispatch closure against dequeues so
only work already handed to the framework continues. Do not interrupt those
caller threads immediately or destroy handler clients beneath active work.

The framework owns the configurable overall shutdown budget:
loomspan.shutdown.timeout: 30s by default. Existing mission timeouts continue
to apply; active executions may finish, including nested work, before that
shared deadline. At the deadline cancel/fence remaining work and bound
executor teardown; replace the current indefinitely blocking close() path.
No additional per-worker grace, public shutdown SPI, or library JVM halt.
FW1 owns this internal lifecycle/configuration work; FW4 documents it; SC5
proves immediate dispatch stop and correct lifecycle/resource ordering.

Use the following lifecycle stages for the agreed change, incorporating
the developer's R1 simplification. This is a proposed implementation design;
the shutdown facility does not exist yet.

1. Both components independently listen for `ContextClosedEvent` and return
   promptly. The framework closes root admission; Sidecar closes its own
   admission/dispatch gates and discards queued work. Either listener may run
   first. Neither waits for executions, calls the other, or depends on the
   other's priority. Filter events to the owning context and opt out of async
   listener execution. Do not close handler clients or interrupt waiting
   callers here. Framework shutdown also works without Sidecar.
2. Start one monotonic deadline when the framework gate closes. Register root
   execution ownership atomically with admission at `LoomspanSessionRunner`,
   before session construction. Consolidate both entry methods around one
   internal root operation. Finalize the session, restore execution binding,
   and deliver available history on the caller before releasing that same
   ownership in `finally`, including failure paths. Keep public-view mapping
   in the facade and observer exception semantics intact; no second root
   admission or observer registry. Do not hold
   its lock while executing skill code, finalizing traces, or waiting.
   Observation registries are optional and
   must not become shutdown's execution registry.
3. Perform the framework's bounded wait in Spring's subsequent lifecycle-stop
   stage, before resources needed by active work stop. Use standard Spring
   lifecycle phases/dependencies for resource ownership, not a shared listener
   priority convention. Keep caller workers, HTTP clients, and the mission
   executor usable while admitted roots and observation finish. Honor the
   remaining monotonic deadline without starting another budget; a shorter
   Spring lifecycle-phase timeout must not cut the framework wait short.
   Extend existing mission registration/cancellation to associate active `MissionLifecycle`
   instances with their admitted root. Registration must observe an already
   reached root cutoff, including missions created concurrently with shutdown.
   Reuse existing future cancellation and ancestry write fences; do not add
   a second task scheduler or a public worker-tracking surface.
4. At cutoff, fence/cancel remaining missions and stop the executor. Clamp the
   existing 250ms mission cleanup grace to the remaining shared budget; neither
   nested missions nor executor cleanup receive another grace period. Shutdown
   must not call arbitrary skill/observer/trace-I/O code synchronously on its
   waiting thread or wait indefinitely for it. Cancellation cannot force
   uncooperative Java code to return; release shutdown's wait at the deadline.
5. Replace the mission executor's `destroyMethod="close"` with explicit bounded
   ownership and disable inferred executor destruction. A destruction fallback
   must be idempotent, close admission and cancel without starting a new wait
   (also covering failed context startup). Once the framework lifecycle wait
   completes or reaches cutoff, subsequent resource lifecycle stop and bean
   destruction can release Sidecar caller workers and HTTP clients.
   Their cleanup must not add an executor `close()`
   or another Sidecar grace period.

Why separate stages are viable (subject to implementation verification and
the lock/completion gaps above): Spring Framework **7.0.8**, pinned by the
current Boot BOM, publishes `ContextClosedEvent` before calling the lifecycle
processor and destroying beans. Its standard event multicaster honors
`supportsAsyncExecution() == false`. Both gate listeners therefore finish
before lifecycle stopping starts, regardless of their relative order.
`DefaultLifecycleProcessor` invokes stop methods before awaiting phase
completion callbacks. A synchronous, deadline-bounded framework stop can
finish its own wait and signal completion without relying on that separate
phase timeout. Select resource phases/dependencies coherently and test the
pinned application wiring; merely moving an asynchronous wait into a lifecycle
callback would not establish the required budget. No second timer setting or
public shutdown interface is needed. Sources:
[AbstractApplicationContext#doClose](https://github.com/spring-projects/spring-framework/blob/v7.0.8/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java),
[SimpleApplicationEventMulticaster](https://github.com/spring-projects/spring-framework/blob/v7.0.8/spring-context/src/main/java/org/springframework/context/event/SimpleApplicationEventMulticaster.java),
and [DefaultLifecycleProcessor](https://github.com/spring-projects/spring-framework/blob/v7.0.8/spring-context/src/main/java/org/springframework/context/support/DefaultLifecycleProcessor.java).

Local reuse anchors: `LoomspanSessionRunner` owns both root-entry methods and
trace completion; `MissionWorkExecutor` and `StepLoopMissionExecutionEngine`
already register owning futures and handle cancellation;
`MissionLifecycle#beginCancellation` currently starts a fixed 250ms grace and
`awaitCutoff` waits against it. These are changes to existing authorities,
not behavior that can be obtained by setting a Spring property today.

Required implementation evidence in FW1/SC5: race root admission with close;
allow a pre-admitted root to start a nested mission during the grace window;
keep handler clients and Sidecar waiting callers alive until completion/cutoff;
discard queued entries without dispatch; bound uncooperative work and trace
cleanup; verify repeated close and failed-startup destruction; verify a
configured duration above Spring's lifecycle phase timeout is still honored.
Include an asynchronous standard event multicaster to verify the shutdown
listeners remain synchronous and prompt. Exercise both relative listener
orders, prove both gates close before the lifecycle wait, and keep resource
cleanup after completion/cutoff. R1 is resolved without a cross-repository
listener-order contract; G3's product policy remains settled.

The timeout bounds Loomspan-owned execution shutdown, not all application
shutdown hooks, unrelated Spring phases, or the entire process. Sidecar's
event/client/executor wiring remains an integration acceptance test because
that application does not exist yet.
The existing max-concurrent limit bounds Sidecar facade invocations, not
all nested/lingering physical operations; no worker-tracking API is added.
## Code health and scope

- Confirmed stale `CapabilityRegistry` YAML-only Javadoc: cleanup belongs in
  FW1. Its supported status is internal; no shim.
- `CapabilityExecutionRouter#objectiveFor` takes an unused arguments map.
  Drop that unused parameter if the shared path is edited in FW2; no API shim.
- `YamlSkillManifest#normalizeStringListMap` and its sole private helper
  `normalizeStringList` have no external call sites and no callers outside
  that unused helper chain. Remove with FW1 manifest work after checking the
  final branch; do not retain an unused abstraction for compatibility.
- Do not merge all kind projections into the observability catalog. Reuse
  one internal-to-public kind mapping where appropriate, while source-specific
  DTO validation remains at its owner. Public catalog and operator source
  details are different projections, not duplicate authorities to erase.

## Verification and limits

- Focused Maven run: **164 tests passed**, zero failures/errors/skips, covering
  public architecture, supported-surface integration, facade, input/ref,
  catalog/registrar, Java lifecycle/cutoff/auth scope, diagnostic catalog,
  and REST fixture corpus. Tests exercise existing behavior, not future REST.
- `scripts/loomspan_version.py check` passed; **9 version-script tests passed**.
- Targeted Console Go observability/application-client tests passed.
- No feature code, fixtures, public API, release tags, or tickets changed.
  No full release build, browser e2e suite, image build, or Sidecar end-to-end
  test was claimed or run.

External reference checks: [JDK 21 ExecutorService close](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html#close())
confirms blocking executor termination. [Spring Security JWT reference](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
supports standard decoder/claim-converter reuse. [Boot REST client reference](https://docs.spring.io/spring-boot/reference/io/rest-client.html)
supports RestClient/SSL-bundle reuse; its versioned URL redirected to 4.1.1,
so it is family-level guidance, not proof of exact 4.1.0 client composition.
Local POM/BOM and the eventual pinned Sidecar tests remain version authority.
