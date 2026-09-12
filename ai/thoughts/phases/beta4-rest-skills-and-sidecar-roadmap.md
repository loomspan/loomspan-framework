# Beta 4 roadmap — REST skills and Loomspan Sidecar

## Purpose

Beta 4 extends Loomspan's reach beyond applications that embed the Spring
Boot starter. It delivers two related but separately owned capabilities:

1. **REST skills** — a third skill kind in the starter. A YAML manifest
   declares a leaf skill whose invocation is delegated to one
   application-supplied `RestSkillHandler` Spring bean. The framework owns
   the catalog, input validation, authorization, execution lifecycle, and
   tracing; the handler owns everything about the outbound call. Any
   Loomspan application can give a skill tree access to existing REST
   services without writing one `@SkillMethod` per endpoint.
2. **Loomspan Sidecar** — a standalone Spring Boot application, in its own
   repository, that runs the starter and exposes skill execution over REST.
   It ships the generic, configuration-driven `RestSkillHandler`. Any web
   application, in any language, can start a skill by HTTP and give the
   agent its own REST endpoints as skills without writing Java.

Beta 4 is complete when the last PR in both repositories lands. Work is
organized into phases; each phase has a boundary agreed here and is detailed
in its own phase file before its PR tickets are written for the pipeline.

This document is the map. It records product decisions the pipeline cannot
recover from the repository. It does not plan implementation.

The [ticket-preparation handoff](../beta4-ticket-readiness.md) assigns coherent
delivery units, acceptance ownership, evidence limits and repository bootstrap.
It supersedes earlier illustrative PR splits. This roadmap and phase contracts
are the current requirements; review/grounding reports retain rationale and
source evidence, including explicitly superseded historical proposals.

The documentary review decisions are recorded in
[the beta 4 design review](../beta4-design-review.md). All phases, including
FW1 and FW2, have now received source grounding at `1e4eb455`; see
[the grounding report](../beta4-code-grounding.md). G1–G3 product policies are
resolved. R1 now uses independent, prompt shutdown-event listeners followed
by the framework's bounded lifecycle wait, without a cross-repository
listener-order contract. Required cutoff/observer-lifetime corrections remain;
see the review and grounding report's fresh-recheck sections.
The accepted simplicity pass requires one root completion path, one Sidecar
admission/accounting owner, initial API/JWT delivery together, and reuse of
existing declaration metadata, catalog completion, and test fixtures.
Grounding existing code is not proof of future
Sidecar application wiring or end-to-end behavior.

## Agreed product decisions

### Ownership and repositories

- REST skills (manifest kind, SPI, lifecycle) live in
  `loomspan-spring-boot-starter` in this repository. The starter ships **no**
  default `RestSkillHandler`; it only defines the contract and the lifecycle.
- Sidecar lives in a new repository, `loomspan-sidecar`, with its own
  version coordination and release workflow. Beta 4 is a two-repository
  release: the framework tags `v1.0.0-beta.4` first; Sidecar then pins that
  release and tags its own.
- During development Sidecar depends on
  `ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` from a
  local `mvn install`. Sidecar CI builds the framework at a pinned commit
  before building Sidecar. No snapshot repository is introduced.

### Sidecar uses only the public Java API

Sidecar compiles against `ai.loomspan.api` only. An ArchUnit test in Sidecar
forbids imports of `ai.loomspan.internal..` and `ai.loomspan.autoconfigure..`.
Sidecar configures the framework only through documented `loomspan.*` keys.
Sidecar's generic REST handler is an ordinary implementation of the public
`RestSkillHandler` SPI.

If Sidecar needs something the closed API does not provide, that is evidence
for a deliberate framework API addition following `AGENTS.md`: add to the
allowlist in `LoomspanPublicSurfaceArchitectureTest`, document in the README,
add supported-surface tests. Sidecar never reaches for an internal type to
avoid that process.

### The `RestSkillHandler` SPI is a deliberate public API addition

`AGENTS.md` states Loomspan has no supported SPI today and must not create
one accidentally. Beta 4 creates one **deliberately**, following the
prescribed process: new types live in `ai.loomspan.api`, are added to the
closed allowlist, are documented in the README, and have supported-surface
tests. This is the first supported SPI; it does not open a general
bean-override surface, and no other internal bean becomes replaceable.

Shape (final signatures are an implementation decision within this intent):

```java
package ai.loomspan.api;

public interface RestSkillHandler {
    String handle(RestSkillInvocation invocation);   // text result; any throw is a skill failure
}

public record RestSkillInvocation(
        String skillName,
        Map<String, Object> input) {}                // validated, reference-resolved inputs
```

- Exactly **one** `RestSkillHandler` bean must exist when any `rest` manifest
  is registered. Zero or more than one fails startup with a diagnostic naming
  the manifests and the beans found. With no `rest` manifests the bean is
  optional and unused.
- The caller's Spring `Authentication` is **not** a parameter. The handler
  runs inside the same scoped `SecurityContext` that Java skills receive and
  reads it from `SecurityContextHolder` when it needs caller identity or a
  forwardable credential. The existing chain is: `DefaultSkillTemplate`
  captures the `Authentication` from the thread calling `invoke()`, the
  session carries it, and `ExecutionCoordinator` re-installs it through
  `ScopedAuthentication` around the invoker on whichever thread runs it,
  including nested leaves called by a YAML planner. A JWT verified by Spring's
  resource-server support arrives as `JwtAuthenticationToken`, whose
  `getToken().getTokenValue()` is the forwardable bearer credential.
- The handler receives **validated inputs**, not raw text, so `input_schema`
  keeps its meaning. REST reuses the same validation, normalization, and
  reference resolution as other skills; the handler's map is simply the
  resulting arguments before Java-style method-parameter binding. Reference
  resolution can supply Spring Resource handles.
  Public API signatures expose
  only JDK and Spring types, never `internal` or `autoconfigure` types.
- The framework owns `RestSkillInvocation`'s immutable-container guarantee;
  it must hold for embedded application callers too. Sidecar's JSON-only
  transport policy is separate. A Resource handle does not guarantee that
  its backing contents are immutable.

### REST skill manifest and semantics

- A REST skill is declared by `rest: true` in an otherwise ordinary manifest
  with `name`, `description`, optional `input_schema`, and optional
  `rbac_roles`. Nothing else about the outbound call is declared in YAML.
- **Routing is the handler's responsibility, keyed by skill name.** The
  manifest deliberately carries no target, route, method, or path. This is a
  conscious departure from the design lens preference for locally visible
  dependencies, accepted so the framework stays minimal and the handler has
  full freedom (mapping tables, signing, encryption, transport choice). Do
  not add routing keys to the manifest without new evidence.
- A REST skill is a **leaf**. `model`, `prompt`, `allowed_skills`,
  `planning_mode`, `concurrency`, `max_steps`, `thinking_level`, `linter`,
  `output_schema`, and `output_schema_max_retries` are rejected at startup with a diagnostic naming the
  manifest location.
- Execution reuses the existing Java-skill direct-invocation path in
  `ExecutionCoordinator` (access check, caller `Authentication` scope,
  invoker, text result). An internal invoker adapts REST registrations to the
  single handler. Catalog/definition invariants and input-value handling also
  need coherent changes; there is no third execution engine. Ordinary Java
  adapter exceptions can become text today; REST deliberately propagates
  failures instead, preserving the agreed visible-failure contract.
- It shares the exact-name catalog, `SkillTemplate` invocation, and
  `allowed_skills` child eligibility with YAML and Java skills. A successful
  REST child earns plan/task and evidence credit exactly as a Java child does.
- The model and the caller supply inputs permitted by `input_schema`; an
  omitted schema allows a generic object, as specified in FW1. Configured
  routing URLs, headers, and credentials are not exposed as model inputs;
  the handler owns them. This is not a guarantee about arbitrary business
  inputs or endpoint response text.
- Any exception from the handler is a **visible skill failure** with bounded
  diagnostics in the trace. The framework performs no retries and no repair.
- Console displays the kind as REST with manifest location; there are no
  handler internals to display.
- Changes affecting the framework/Console compatibility contract carry the
  required framework and Console producer, consumer, and fixture updates
  together. Assess the marker against trace and observability REST semantics,
  including kind values, not only shape. Record the bump/no-bump decision
  where the first contract change lands; FW1 is not exempt.

### Sidecar's generic handler

Sidecar implements `RestSkillHandler` once, driven by configuration. All
Sidecar-owned application properties live under the `loomspan-sidecar.*` prefix, kept separate
from the framework's documented `loomspan.*` contract so ownership of every
key is visible. None of the following is framework configuration:

- `loomspan-sidecar.rest-routes-location`, default
  `file:/sidecar/rest-routes.yaml`, points to a Sidecar-owned YAML file whose
  root contains `targets` and `routes`. `targets.<name>` defines `base-url`,
  `auth` (`mode` plus static `headers`), `ssl-bundle`, `connect-timeout`,
  `read-timeout`, and `max-response-size`. `routes.<skillName>` defines
  `target`, `method`, and `path`. Multiple microservices are supported through
  named targets; routes share target settings. Neither map is duplicated in
  application properties. Boot SSL bundle definitions stay in standard Boot
  configuration and are referenced by name from the file.
- Resolve environment-backed `${NAME}` placeholders in parsed string values
  explicitly through Spring's existing placeholder facilities before
  validation. Missing required placeholders fail startup without printing
  resolved secrets. This is a separate Sidecar file format, not automatic
  application-property binding or a new expression/secret-provider system.
- Sidecar validates routes against the public catalog after framework skill
  registration and before readiness: every REST skill has a route, every
  route names a REST skill and configured target, and method/path values are
  valid. Construct the handler independently of the catalog to avoid a
  circular startup dependency. These checks validate local configuration;
  they do not probe remote endpoints.
- Fixed input binding: `{name}` path variables bind from same-named inputs;
  remaining inputs become query parameters for `GET` and a JSON
  object body for `POST`. Sidecar v1 supports only `GET` and `POST`;
  all other route methods, including `PUT`, `PATCH`, and `DELETE`, fail
  startup. No templating language. This restriction applies to Sidecar's
  generic handler, not application-supplied framework handlers.
- Path variables and GET query values must be non-null strings, numbers, or
  booleans; missing path values, nulls, arrays, and objects fail visibly.
  POST bodies preserve schema-permitted nulls and nested values. Inputs
  cannot change the configured target or escape its base path.
- Sidecar v1 input transport is JSON-only. It does not upload or convert
  attachments, Resource handles, files, or streams. Non-JSON values produced
  by reference resolution fail before an outbound call. This does not change
  the agreed JSON/text response handling or application-written handlers.
- URI construction preserves each target's base path: base
  `https://example.com/api` plus route `/expenses` yields
  `https://example.com/api/expenses`. Use a URI builder, normalize the joining
  slash, and encode substituted values as individual path segments.
- Auth modes: `none`, `static` (configured headers), and
  `caller-passthrough` (forward the bearer credential found on the scoped
  `Authentication`). A passthrough target invoked without a bearer credential
  is a visible skill failure.
- Any successful bodyless response, including `204`, returns empty text
  without requiring a content type. For nonempty successful bodies, accepted
  content types are JSON and `text/*`; the body becomes the result text.
  Non-2xx, timeout, connection failure, oversized response, or unsupported
  content type on a nonempty body throws and therefore fails the skill.

### Sidecar execution and inbound authentication

- Execution is **asynchronous only in v1**: `POST` creates an execution and
  returns `202` with an id; `GET` reads status and, when complete, the result
  and `SkillExecutionView`-derived events. There is no `wait` parameter or
  synchronous completion mode; clients poll GET.
- **The inbound `SecurityContext` must reach the thread that calls
  `SkillTemplate.invoke`.** The framework captures the caller
  `Authentication` from that thread; the default holder strategy is
  thread-local, so Sidecar's background executor must propagate the
  request's context (for example Spring Security's
  `DelegatingSecurityContextExecutor`). This is an SC2 acceptance criterion
  with a test proving `rbac_roles` and passthrough see the inbound identity
  on an asynchronously executed skill.
- Inbound authentication is **JWT only** for the initial release, for both
  user and service identities. The host application or its trusted issuer
  supplies identity and roles; Sidecar verifies the token and creates a
  Spring `Authentication` whose authorities drive existing `rbac_roles`
  and `@RolesAllowed` checks. Sidecar maintains no user-role assignments.
  Plain API keys and API-key identity delegation are deferred.
- JWT issuer is configuration, not assumption: any issuer Spring's
  resource-server support can verify (discovery, JWKS URI or public key), so the host
  application may mint its own tokens or delegate to an IdP. Roles come from
  a configured claim with a configured prefix. Audience is validated; for
  passthrough to work the token's audience must be accepted by the target as
  well as by Sidecar. Every key-resolution mode validates the configured
  trusted issuer, signature, audience, and lifetime and requires nonblank
  `iss` and `sub` plus expiration; explicit keys do not bypass issuer checks.
- The primary example is host app → Sidecar → host app: forward a suitable
  access token to Sidecar, retain its authentication through queued/nested
  execution, and forward the same JWT through `caller-passthrough`. The
  callback host verifies it and establishes the user's security context.
  A session-based host backend can obtain or issue a suitable signed JWT;
  browser cookies and OIDC ID tokens are not assumed to be API credentials.
  Client libraries are not required for beta 4; no token-issuing subsystem
  is added to Sidecar.
- Token lifetime is the caller's responsibility. Sidecar never refreshes,
  exchanges, or mints tokens. A verified request authorizes its accepted
  execution using the captured identity and roles, including after queueing.
  Token expiry does not revoke accepted local work; Sidecar adds no expiry
  recheck at worker handoff or during execution. Framework skill access
  checks still apply using that captured authentication. Queue time also
  consumes token lifetime. A
  token that expires before a passthrough call is rejected by the target
  and surfaces as a visible REST skill failure.
- Mutual TLS is transport-only: inbound via Spring Boot
  `server.ssl.client-auth`, outbound via per-target `ssl-bundle`. Certificate
  identity is not mapped to an `Authentication`.
- Default mounted layout: `/sidecar/skills/` contains skill manifests and
  `/sidecar/rest-routes.yaml` contains Sidecar routes. The existing
  `loomspan.skills.locations` defaults in Sidecar are
  `file:/sidecar/skills/**/*.yaml` and `file:/sidecar/skills/**/*.yml`.
  The framework scans only the skills directory; Sidecar reads its route
  file separately. No Sidecar-specific filename exclusion is added to the
  framework. Both locations can be overridden through their owning settings.
- **Initial Sidecar release (v1) loads skills and routes at startup only.**
  Edit files on disk, then restart to apply changes. Skills and routes must
  validate before readiness; invalid configuration fails startup with useful
  diagnostics. There is no file watcher, reload endpoint, live catalog
  replacement, or retained catalog-version lifecycle. Restart uses the
  immediate dispatch stop in SC5 and framework-owned shutdown, and loses the in-memory execution store;
  callers should finish active work and retrieve needed results beforehand.
  Queued work is discarded on shutdown. Work still running at the framework
  shutdown deadline may be interrupted.
- Console observability works through the existing opt-in
  `loomspan.observability` adapter.
- **Execution store is a bounded in-JVM `ConcurrentHashMap`.** It holds, per
  execution id: status, timestamps, the starting caller identity (only that
  caller may read it), and on completion the result text or failure plus
  `SkillExecutionView`-derived events. Completed records do not retain inputs
  or security context; worker-held references are released when the worker
  exits. Ownership retains only issuer and subject. Two limits: maximum retained
  executions (all queued, running, and terminal records) and a TTL after
  completion. Accepted consequences: a restart
  loses in-flight and unfetched executions, and results are readable only
  from the Sidecar instance that accepted the `POST`. This matches the
  one-Sidecar-per-application-instance topology; no database, cache, or
  file store is introduced.
- **Execution admission uses a bounded in-memory queue and worker pool.**
  One internal owner coordinates admission/accounting for one executor work
  queue and one retained-record store. Do not add a staging queue or duplicate
  capacity authorities. Tasks own pending input/authentication; records publish
  terminal outcomes and selected events together. Use a simple store sweep
  for expiration, with TTL enforcement on reads and expired-capacity reclamation
  on admission, rather than per-record timers or a separate cache subsystem.
  Under `loomspan-sidecar.executions`, `max-concurrent` limits workers
  (default 32), `max-queued` limits waiting requests (128), and
  `max-queued-input-size` limits their combined input payload (64MB).
  After validation, measure each input map's JSON UTF-8 byte size once and
  retain that size with the request. Reserve waiting count and bytes
  together; reject with `429` if either would exceed its limit. A full
  worker pool alone queues work rather than rejecting it. Accepted waiting
  work is `QUEUED`, becoming `RUNNING` when a worker takes it and releases
  its queued-byte reservation. Direct worker handoff does not consume the
  waiting budget. Dispatch failure rolls back reservations and the record;
  worker capacity is released when the worker exits. Retain caller identity
  and security context through queueing. Use ordinary executor facilities;
  no disk spill or custom scheduling system. This byte budget measures
  waiting JSON payloads, not heap usage, fetched attachments, running
  inputs, or retained results/events. SC5 immediately stops dispatch and
  discards queued work on shutdown; it has no drain timer. Work already
  handed to Loomspan is governed by framework shutdown. The queue is not durable.
- **Execution API shape** (all routes require inbound authentication):
  `GET /v1/skills` (catalog), `GET /v1/skills/{name}`,
  `POST /v1/skills/{name}/executions`, `GET /v1/executions/{id}`. The `POST`
  body must be a JSON object containing the skill input map exactly as passed
  to `SkillTemplate.invoke`, with no envelope. Missing, null, malformed, or
  non-object bodies are `400`; an empty object uses normal skill validation.
  `POST` returns `202` with the execution representation and a `Location`
  header without waiting for completion. Clients poll GET; client disconnect
  does not cancel accepted work. The representation carries `id`, `skillName`,
  `status` (`QUEUED` | `RUNNING` | `COMPLETED` | `FAILED`), timestamps, `result` as the
  unparsed text the framework returned, or `failure` with `kind`,
  `message`, and validation `issues`. HTTP-level errors use RFC 9457 problem
  details: `401` unauthenticated, `404` unknown skill or unknown/expired/
  foreign execution, `400` invalid input body, `429` retained-record
  capacity, queue count, or queued-input byte capacity exceeded.
- GET returns `200` for a known, owned execution, including `FAILED`; `500`
  means Sidecar failed to handle the HTTP request itself. During shutdown,
  readiness goes down and new execution admission returns `503`; accepted
  dispatch to Loomspan stops immediately and queued requests are discarded.
  Already-dispatched executions may finish under existing mission timeouts
  and the framework-owned `loomspan.shutdown.timeout` (default `30s`, a
  positive YAML duration). This is one framework budget including cleanup;
  at its deadline remaining work is cancelled/fenced without an unbounded
  executor destruction wait. There is no Sidecar drain timer or new public
  shutdown API. Both components independently close their gates on Spring's
  shutdown event and return promptly; neither waits or calls the other.
  Their relative listener order does not matter. The framework waits during
  Spring's subsequent lifecycle-stop stage, with required resources alive
  until completion/cutoff. FW1 owns the framework lifecycle change; SC5
  verifies both listener orders and resource lifetime without a shared
  listener-priority convention.
- Framework shutdown atomically closes new top-level execution admission,
  independent of Sidecar. A previous `validate` success does not reserve a
  place. Already-admitted executions may still invoke nested skills within
  their deadlines. Sidecar discards its queue directly; it does not submit
  queued work just to get rejections. Framework rejection covers dispatch races.
- `loomspan-sidecar.executions.max-input-size` defaults to `1MB`. Enforce it
  while reading incoming JSON, before parsing/validation; oversize is `413`
  with no execution record. Sidecar adds no byte cap to final results or
  selected diagnostic history: return what the framework produced without
  size-based truncation, omission, or a result-too-large failure. Record-count
  and completion-TTL limits remain; they are not a bound on total heap usage.
- Preserve framework `SkillException` messages in the failure representation.
  Beta 4 adds no data sanitization for inputs, results, exception messages,
  or diagnostic history. Wrapping in the response envelope does not imply
  sensitive data is removed; there is no blanket guarantee for responses or
  logs. Existing framework redaction remains unchanged. Ordinary failures do not
  include stack traces; diagnostic history is independently controlled.
- **Diagnostic history uses the framework's trace-persistence mode names.**
  `loomspan-sidecar.executions.diagnostics` accepts `NEVER` (default),
  `ONERROR`, and `ALWAYS`. `NEVER` retains and returns no observer events;
  `ONERROR` retains and returns events only for failed executions; `ALWAYS`
  retains and returns events for both successful and failed executions.
  Selected history appears in the execution representation's `events`
  field alongside its result or failure, including when read by polling.
  No additional history request is required: selected history and the
  terminal outcome are available together. Both share the execution record's
  completion TTL (default 15 minutes, not extended by reads), and both are
  lost on restart. History has no separate store or retention lifecycle.
  Pre-dispatch errors have no execution history. FW2 extends the public
  observer contract to expose available history on execution failure while
  preserving the exception thrown by `invoke`. This setting is independent
  of framework trace persistence and defaults to `NEVER` because events are
  development/debugging data that may contain business data.
- **Execution ownership is by caller identity.** Execution ids are random
  and unguessable but are not bearer secrets. Each execution records the
  starting identity as a small internal immutable value with explicit JWT
  `issuer` and `subject` fields, shared by SC2 and SC3;
  reads by any other identity return `404`, indistinguishable from an
  unknown id. Ownership follows identity, not the exact token, so a renewed
  JWT with the same issuer and subject can read results. Compare the two
  fields directly; no delimiter-joined string or auth-kind hierarchy is
  needed. This is Sidecar-owned data, not a framework public API addition.
- **Request errors are HTTP errors; execution errors are execution
  outcomes.** Before dispatching, Sidecar calls the framework's public
  pre-check (`SkillTemplate.validate`, added in FW2) on the request thread
  with the inbound `SecurityContext`, mapping `SkillInputValidationException`
  to `400` with issues and `AccessDeniedException` to `403`. Only then does
  the `POST` return `202`. Failures that arise during execution, including
  access denial for nested child skills and `SkillException`, surface in the
  execution status as `FAILED` with `failure.kind`. Sidecar does not run its
  own schema validation (rejected as a second authority for input
  semantics).

## Review status and defaults

SC2 defaults are agreed: 32 workers, 128 queued requests, 64MB queued input,
1,000 retained records, a 15-minute completion TTL, and diagnostics `NEVER`.
See [the design review](../beta4-design-review.md) for settled decisions,
including R1's independent-listener simplification. Incoming JSON is capped at `1MB`; final results
and selected history have no Sidecar byte cap. Framework shutdown defaults to
`30s` through `loomspan.shutdown.timeout`; Sidecar has no drain period.
G1–G3 product policy remains settled. G3 now separates independent, prompt
close-event handlers from the framework's later lifecycle wait; no shared
listener order is required. FW1/FW2 must also
cover blocked trace locks and observer completion within the shared deadline;
SC5 proves the eventual Sidecar wiring.

## Deferred beyond beta 4

Recorded so later phases do not reopen them without new evidence:

- Data and exception-message sanitization are outside beta 4. Preserve
  existing `SkillException` messages; no new sanitization layer is introduced.
- Plain API-key inbound authentication and API-key-based identity delegation.
  JWT is the single initial authentication path; no compatibility shim is
  needed for the superseded, unimplemented API-key proposal.
- Hot reload of skills and REST routes (candidate for a later Sidecar v2,
  not a committed implementation). No speculative reload API, catalog
  versioning, reference counting, cleanup service, or publishing machinery
  is introduced in beta 4. If revisited, evaluate atomic activation and
  consistency for running executions against the design lens.
- A framework-shipped default `RestSkillHandler`. Sidecar's handler is the
  candidate if evidence shows embedded applications want the same generic
  behavior.
- Routing metadata in the `rest` manifest block.
- Passing the caller `Authentication` or trace/session identifiers as SPI
  parameters; the scoped `SecurityContext` is the contract for now.
- A public execution cancellation API, and any Sidecar cancel or delete
  route built on it.
- An administrative identity that can read all executions.
- Multiple or per-skill handler beans.
- OpenAPI import generating REST skills or Sidecar mappings automatically.
- `output_schema` validation or response mapping for REST skills.
- Framework-level retries for REST skills.
- Live per-execution event streaming (SSE) from Sidecar.
- Persistent execution store; multi-instance Sidecar coordination.
- Sidecar-hosted Java skills.
- Sidecar-initiated token refresh or exchange (confused-deputy exposure; if
  ever needed, RFC 8693 token exchange, not a Sidecar-owned refresh path).
- X.509 client-certificate identity as an inbound authentication mode.

## Phases

### Framework repository

FW1 also owns the shared root-admission and bounded-shutdown lifecycle,
including `loomspan.shutdown.timeout`; FW2 extends that same root operation
through observer delivery. Consolidate the runner's run/call implementations:
admit, execute, finalize, restore binding, deliver history, release once.
No separate observer execution path or ownership registry. These are required
phase deliverables alongside REST.

| Phase | Outcome | Boundary and dependencies |
| --- | --- | --- |
| FW1 — REST skill kind and SPI | `rest: true` manifest marker with leaf-only validation; `RestSkillHandler` and `RestSkillInvocation` in `ai.loomspan.api` with allowlist, README, and supported-surface tests; `CapabilityKind.REST_SKILL` registered by `YamlSkillCapabilityRegistrar` with an invoker delegating to the single handler bean; exactly-one-bean startup validation; reuse of the Java direct-invocation path; failure semantics and bounded trace diagnostics. | No dependencies. |
| FW2 — Public API additions for external callers | (a) A read-only, allowlisted `ai.loomspan.api` view of registered skills: exact name, description, kind (YAML, Java, REST), and input JSON schema; injectable like `SkillTemplate`; no internal types in signatures. (b) `SkillTemplate.validate(skillName, input)`: runs the existing input validation and root-skill access check without executing, throwing `SkillInputValidationException` or `AccessDeniedException`, so an asynchronous caller can reject bad requests before dispatch. These exist because Sidecar must neither re-parse manifests nor re-implement validation. (c) Extend the public observer contract to expose available history on execution failure while preserving the thrown exception, supporting Sidecar diagnostic-history modes. | Depends on FW1 (REST kind must be representable). |
| FW3 — Observability and Console | Existing declaration locations and observability REST skill detail projected from the REST capability kind; Console renders the third source kind; fixtures (`skill-rest-detail.json`); Explicit `consoleCompatibilityVersion` decision for trace or observability REST contract changes; required framework/Console updates land together, including in FW1 when applicable. | Depends on FW1. Cross-language (Java, Go, React). |
| FW4 — Documentation and supported-surface test | README REST skill, SPI, catalog, and `validate` sections; `agent-skills/loomspan-docs` skill-authoring and java-api references; integration test running a YAML planner → REST leaf through `SkillTemplate` with a test `RestSkillHandler`; catalog view asserted through the public API only. | Depends on FW1–FW3. Establishes framework release readiness; tagging waits for verified Sidecar integration against the local snapshot. |

### Sidecar repository

| Phase | Outcome | Boundary and dependencies |
| --- | --- | --- |
| SC1 — Scaffold | New repository; Spring Boot 4 app on the starter; ArchUnit public-API-only test; `file:` skill loading; environment-driven configuration; local-install dependency strategy; CI building the framework at a pinned commit; Maven/release version checks and a recorded framework pin; no extra script without coupled artifacts. | Starts after framework implementation, using the locally installed snapshot before release. |
| SC2 — Execution API | Async-only execution endpoints with polling, in-memory TTL store, catalog endpoint, problem-detail error mapping, `SkillExecutionView` projection. | Depends on SC1 and the framework snapshot; initial delivery is grouped with SC3 JWT authentication. |
| SC3 — Inbound authentication | JWT-only resource server → verified identity and roles → Spring `Authentication`; explicit issuer/subject ownership; same-token passthrough to the host app. | Initial delivery is grouped with SC2 after SC1. |
| SC4 — Generic REST handler | `RestSkillHandler` implementation: one `rest-routes.yaml` containing `targets` and `routes`, selected by `rest-routes-location`, with startup-only loading and validation, fixed input binding, `none`/`static`/`caller-passthrough` auth, SSL bundles, response handling and failure semantics. | Depends on SC3. Implements the FW1 SPI against the local snapshot during development. |
| SC5 — Packaging and release | Container image, health/readiness, release workflow pinned to framework `v1.0.0-beta.4`, README quick-start with a sample skill tree calling a stub REST service. | Depends on SC1–SC4. Last PR of beta 4. |

### Sequencing

SC2 and SC3 remain responsibility checklists but their initial implementation
lands together: the first usable execution API includes its real JWT
authentication. Reuse standard JWT facilities, one owner value, and common
test fixtures; no transitional production authentication implementation.

Framework implementation first, then Sidecar integration against the locally
installed snapshot. The grounded cross-phase decisions are recorded in G1–G3.
When implementation is authorized, implement FW1–FW4, then develop SC1–SC4 and prepare SC5,
iterating across both repositories as integration reveals gaps. Framework
release readiness does not require an immediate tag.

```text
FW1 lifecycle -> [FW1 REST + FW3] -> FW2 -> FW4 -> SC1
  -> [SC2 + SC3] -> SC4 -> SC5 preparation
  -> resolve any framework gaps and reverify snapshot integration
  -> integration verified -> framework release -> Sidecar release pin
  -> verify against released dependency -> SC5 release
```

During development, run `mvn install` in the framework repository and build
Sidecar against `1.0.0-beta.4-SNAPSHOT` from the local Maven repository.
Reinstall after framework changes. Sidecar CI builds and installs the
framework at its recorded commit before building against the snapshot; no
snapshot repository is needed. Framework API gaps are resolved deliberately
in this repository before release, never by Sidecar reaching for internals.

Before tagging the framework, prove the Sidecar integration end to end:
pre-dispatch validation and authorization, asynchronous caller-context
propagation through a YAML planner to a REST leaf, and diagnostic history
for failed executions under `ONERROR` and `ALWAYS` (absent under `NEVER`).
Complete framework release checks on the final integrated commit, then tag
and publish `v1.0.0-beta.4`. Pin Sidecar to that released dependency, verify
again, and complete its release in SC5. Released versions are not overwritten.

## Next step

The design and ticket-preparation passes are complete; no substantive review
decision remains. The [handoff](../beta4-ticket-readiness.md) maps all phase
acceptance criteria to delivery units and records Sidecar bootstrap prerequisites.
The cutoff/observer gaps remain implementation work owned by FW1/FW2, not
an excuse to add another execution path. Framework tickets can be created
when requested using `ai/commands/write_ticket.md`; this planning pass has
not created tickets or started implementation.
Implementation must still prove the phase acceptance criteria, particularly
Sidecar application startup, HTTP/JWT integration, limits, and shutdown.
The Sidecar checkout now exists at `C:/opendev/code/loomspan-sidecar`.
Seed its workflow before creating the scaffold ticket. All Sidecar tickets,
including the scaffold ticket, must be created in
`C:\opendev\code\loomspan-sidecar\ai\thoughts\tickets`. Transfer its
phase documents as specified in the handoff and replace
these local copies with links, avoiding two editable sets of requirements.
