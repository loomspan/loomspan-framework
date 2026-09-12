# Phase FW1 — REST skill kind and `RestSkillHandler` SPI

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). First
framework phase; no dependencies. Phases FW2–FW4 build on it.
Source-grounded at `1e4eb455`; see [grounding evidence](../beta4-code-grounding.md#fw1--feasible-through-the-existing-lifecycle-with-corrected-assumptions).
G1 is resolved: reuse existing skill input processing and hand the resulting
arguments to the handler; no separate REST input mechanism.

## Goal

A YAML manifest can declare a leaf skill with `rest: true`. The framework
registers it in the exact-name catalog as `CapabilityKind.REST_SKILL`,
validates its inputs, enforces `rbac_roles`, and executes it through the
existing direct-invocation lifecycle by delegating to exactly one
application-supplied `RestSkillHandler` Spring bean. The handler is the first
deliberately supported Loomspan SPI and lives in `ai.loomspan.api`.

At the end of this phase an embedded Spring Boot application can write one
`RestSkillHandler` bean and a `rest: true` manifest per endpoint, and a YAML
planner can call those skills as children with full authorization and
tracing. Nothing in this phase knows about HTTP; the handler owns the call.

## In scope

- Public API: `RestSkillHandler` and `RestSkillInvocation`, allowlisted in
  `LoomspanPublicSurfaceArchitectureTest`, documented in the README's
  supported-surface section, covered by supported-surface tests.
- Manifest: the `rest` field, its validation, and its interaction with every
  existing manifest field.
- Registration: `CapabilityKind.REST_SKILL`, catalog registration alongside
  YAML and Java skills, exact-name collision rules unchanged.
- Handler bean resolution and startup validation.
- Execution: reuse of the Java direct-invocation path for the root skill and
  for nested invocation as a planner tool; caller `Authentication` scope;
  input resolution; result and failure semantics; trace diagnostics.
- Framework-owned bounded shutdown of active executions and its mission
  executor, configured by `loomspan.shutdown.timeout` (default `30s`).
- Keeping the existing opt-in observability catalog functional for the new
  kind, with the full Console contract/rendering checklist in FW3 delivered
  in the same REST skills and Console unit.

## Out of scope (owned elsewhere or deferred)

- Any HTTP client, target configuration, URL binding, or auth mode
  (Sidecar SC4 or the application's own handler).
- Public catalog read API and `SkillTemplate.validate` (FW2).
- Console presentation and source-detail requirements are enumerated in FW3
  and delivered with the REST unit; they are not a deferred correctness gap.
- `agent-skills/loomspan-docs` authoring references (FW4); FW1 carries only
  the README API documentation that `AGENTS.md` requires for new API types.
- Routing metadata in the manifest, multiple handler beans, passing
  `Authentication` or session identifiers as SPI parameters, a
  framework-shipped default handler, `output_schema` for REST skills,
  retries (all deferred by the roadmap).

## Binding decisions

### SPI shape

```java
package ai.loomspan.api;

/** Executes REST skills. Exactly one bean is required when any REST skill is registered. */
public interface RestSkillHandler
{
    /** Returns the skill's text result. Any thrown exception fails the skill. */
    String handle(RestSkillInvocation invocation);
}

/** Immutable description of one REST skill invocation. */
public record RestSkillInvocation(String skillName, Map<String, Object> input) {}
```

- Callers and planners supply input exactly as for other skills. Reuse the
  existing validation, normalization, and reference-resolution pipeline.
  Java skills bind the resulting arguments to method parameters; REST skills
  hand them to the single handler as `input`, alongside `skillName`. This map
  is an argument representation, not a context bag or a new input mechanism.
- `RestSkillInvocation` defensively copies `input` and rejects null
  record components. Null values inside inputs remain valid wherever the
  input contract permits them. Nested maps/lists must also be immutable;
  existing normalization is not sufficient for every input (generic maps
  are shallow copies). Reuse/narrow existing immutable-copy logic for the
  handoff rather than introduce competing conversion or validation systems.
  Do not use a copy operation that rejects legitimate null values.
  Signatures use only JDK types.
- This container-immutability guarantee belongs to the framework's public
  invocation value, including direct construction by embedded applications.
  Sidecar does not supply it. Sidecar separately enforces JSON-only transport;
  existing reference resolution may produce Resource handles, whose backing
  content is not made immutable by copying the input containers.
- The caller's Spring `Authentication` is not a parameter. The handler runs
  inside the `ScopedAuthentication` context the framework already installs
  for Java skills and reads `SecurityContextHolder` when it needs identity or
  a forwardable credential.
- A `null` return is a skill failure ("handler returned null"), not an empty
  result. Empty string is a valid result.
- REST handler exceptions deliberately propagate through the coordinator
  and facade; do not reuse Java's exception-to-text adapter:
  `AccessDeniedException` remains an authorization failure at the facade;
  other runtime failures are wrapped as `SkillException`, while an existing
  `SkillException` retains its message. This feature adds no data or
  exception-message sanitization; do not label arbitrary messages inherently safe.
  The trace records a
  bounded stack and message as it does for Java skill failures.

### Manifest contract

- `rest` accepts only the boolean `true`. `false`, null, strings, or any
  other value fail startup with a diagnostic naming the resource and telling
  the author to omit the field for non-REST skills.
- With `rest: true`, `model` must be absent. The existing "model required"
  rule applies only to non-REST YAML skills.
- Reuse raw-tree field checks and declared-field tracking in the existing
  manifest loader. Update `YamlSkillDefinition`'s constructor invariant as
  well as model resolution and registration: it currently requires execution
  configuration for every definition. Do not create dummy model configuration.
- With `rest: true`, these fields fail startup if declared at all (including
  explicit null or empty forms): `model`, `prompt`, `thinking_level`,
  `allowed_skills`, `planning_mode`, `concurrency`, `max_steps`, `linter`,
  `output_schema`, `output_schema_max_retries`. The diagnostic names the
  resource, the skill name, and the offending field.
- Allowed with `rest: true`: `name`, `description`, `input_schema`,
  `rbac_roles`. `name` and `description` remain required and follow the
  existing exact-name rules and namespace shared with YAML and Java skills.
- No `input_schema` yields the generic object contract, as for YAML skills
  today.

### Registration and handler resolution

- REST manifests register as `CapabilityKind.REST_SKILL` with
  `SkillExecutionDescriptor.none()`, `SkillAccessPolicy.yamlRoles(...)`, the
  YAML resource as `SkillSource`, and a real `CapabilityInvoker` that builds a
  `RestSkillInvocation` and calls the handler. `requireExecutionConfiguration`
  is never called for REST skills.
- Reuse `SkillSource`'s existing declaration-location fields. The REST kind
  belongs to `CapabilityMetadata.kind`; do not duplicate that authority in
  a new source type or add handler-detail fields the framework cannot use.
- When at least one REST manifest is registered, exactly one
  `RestSkillHandler` bean must exist in the application context. Zero beans
  fails startup listing every REST manifest resource; more than one fails
  startup listing the bean names. With no REST manifests, handler beans are
  neither required nor validated.
- Duplicate-name rules are unchanged: REST/YAML, REST/Java, and REST/REST
  collisions fail startup with both locations.
- `allowed_skills` in a YAML parent may name a REST skill; unknown references
  still fail startup.

### Execution

- Root invocation through `SkillTemplate` and nested invocation as a planner
  tool both route through the existing `ExecutionCoordinator` direct
  branch. The branch condition changes from "is Java" to "is directly
  invocable" (Java or REST). No new engine, plan, or model interaction.
- A successful REST child earns plan/task and `evidence` credit exactly as a
  Java child; denial, cancellation, and failure earn none.
- Session quotas, depth, mission timeout, and write fencing apply unchanged.
- `SkillExecutionView` observers see a REST execution with the same shape as
  a Java execution.

### Shared framework shutdown

- Add the documented YAML duration setting `loomspan.shutdown.timeout`,
  default `30s`, for one overall framework shutdown budget including cleanup.
  This is a framework configuration contract, not a Sidecar timer or a new
  Java API/SPI. Use a positive duration; invalid values fail startup.
- When framework shutdown starts, close admission for new top-level
  executions at the authoritative session-entry boundary. Admission and
  shutdown must have one atomic ordering: a request is either admitted before
  shutdown or rejected without creating/executing a new root session. An
  earlier `validate` success does not reserve admission. Existing sessions'
  nested calls remain allowed until completion or their applicable deadline.
  This protection applies to embedded callers as well as Sidecar; it does
  not depend on Sidecar checking a shutdown flag.
- Already-admitted executions may finish while existing mission timeouts
  continue to apply. Shutdown must preserve required nested work/resources
  for those executions until completion or the shared shutdown deadline;
  do not close the mission executor while admitted sessions still need it.
- At the deadline, request cancellation using existing cutoff/late-write
  fencing semantics and finish framework teardown without an indefinite
  wait for uncooperative work. Replace the current blocking executor `close()`
  destruction. No fresh timeout per mission, worker, or executor.
- Sidecar closes its own dispatch gate immediately; it does not drain queued
  jobs into the framework. Each component independently handles
  `ContextClosedEvent` and returns promptly: the framework closes root
  admission and starts its deadline; Sidecar closes admission/dispatch and
  discards queued work. Neither waits or calls the other, and their relative
  listener order does not matter. Opt out of asynchronous event execution
  and filter to the owning context. The framework's bounded wait runs in
  Spring's subsequent lifecycle-stop stage, before resources needed by active
  executions stop. Use ordinary Spring phases/dependencies for that resource
  lifetime, without a cross-repository listener-priority convention. See G3
  for source evidence. The framework does not forcibly halt an embedding application's
  JVM or introduce a public cancellation/worker-tracking surface.
- Consolidate the runner's run/call entry methods around one internal root
  operation: admit → execute → finalize session → deliver available history
  → release ownership in `finally`. Both entry methods use this same path;
  there is one root admission and release, including construction/finalization
  failures. Do not add a separate observer job, handoff registry, or second
  execution path. Associate existing mission lifecycles with
  that root for cutoff, including concurrent nested registration. Clamp the
  existing 250ms cancellation grace to the shared deadline. Disable inferred
  executor `close()` and provide idempotent non-waiting destruction fallback
  for failed startup. Do not execute arbitrary user code or trace I/O on the
  shutdown waiter. The setting bounds framework work, not unrelated application
  shutdown hooks or the whole JVM's shutdown duration.
- R1 is resolved by independent event listeners and a later framework-owned
  lifecycle wait. Preserve the full remaining framework budget even if Spring's
  lifecycle-phase timeout is shorter; do not add a second configured timer.
  Resource cleanup must not stop caller workers or HTTP clients early.
- Bounded cutoff must not acquire locks indefinitely. Today mission ancestry
  locks cover trace writes, and `beginCancellation` records a failure while
  holding those locks. Clamping the grace alone is insufficient. Adapt the
  existing authority so shutdown can signal cutoff without waiting on trace
  I/O, and preserve late-write fencing when blocked writers resume.
- FW2 adds observer delivery within that same root operation's lifetime.
  Session-runner return currently precedes mapping/callback; extend the
  existing completion boundary rather than track the observer separately.
  Keep public-view mapping in the facade and delivery on the caller after
  execution binding restoration, within the same shutdown budget. Preserve
  observer exception behavior; do not invoke it on the shutdown waiter.

### Observability during FW1

- The existing opt-in observability adapter and `DefaultRegisteredSkillCatalog`
  must not fail on REST registrations. Minimum: the skill lists with its
  registered name, YAML resource path/text, and a kind value that
  distinguishes REST. Any change to the framework/Console compatibility
  contract must carry the required framework and Console consumer/fixture
  updates together, including when it first lands in FW1. Assess
  `consoleCompatibilityVersion` using protocol semantics, including new kind
  values, not only JSON shape; record the bump/no-bump rationale during
  grounding. Remaining Console presentation work belongs to FW3.

### Flagged for cleanup in this phase (design-lens §1)

- `CapabilityRegistry`'s Javadoc claims implementations "must reject
  metadata whose kind is not `YAML_SKILL`"; Java skills already register
  there and REST will too. Correct the comment in the REST skills and Console unit.
- `DefaultRegisteredSkillCatalog` labels every non-YAML kind `"JAVA"`.
  Its `RegisteredSkillEntry` and Console consumers also reject a third source.
  Correct the source projection and validators together, not just the label.
  Share internal/public kind translation without coupling the public catalog
  to observability source-detail DTOs.
- Remove the unused private `YamlSkillManifest.normalizeStringListMap` /
  `normalizeStringList` helper chain when editing the manifest; source lookup
  found no live callers. No compatibility shim for private dead code.

## Acceptance criteria

- [ ] Run/call entry methods share one root lifecycle implementation, with
  exactly one admission/release per invocation. Success, execution failure,
  construction/finalization failure, and observer failure do not leak root
  ownership or create a separately admitted observer operation.
- [ ] A trace writer deliberately blocked while holding a mission ancestry
  lock cannot hold shutdown beyond its shared deadline. Resuming it after
  cutoff cannot restore execution credit or admit new nested work. Exercise
  cancellation-failure recording and destruction fallback too.
- [ ] Framework-triggered cancellation covers direct missions as well as
  step-loop missions. `MissionWorkExecutor` currently lacks the latter's
  `CancellationException` handling; preserve primary failure and cleanup
  semantics when shutdown cancels an owning future.
- [ ] A blocked observer/view-mapping path remains covered by shutdown
  ownership until completion or the shared deadline, without invoking it on
  the shutdown waiter. Coordinate this proof with FW2.
- [ ] Close-event tests cover both relative listener orders, prompt return
  before the framework lifecycle wait, asynchronous standard
  multicasting, owning-context filtering, repeated close, failed startup,
  nested admission during shutdown, and a budget longer than Spring's
  lifecycle-phase timeout. Needed resources stay usable through completion
  or cutoff. A skipped/failed listener still has bounded,
  idempotent destruction fallback; no guarantee covers unrelated hooks.
- [ ] `loomspan.shutdown.timeout` defaults to `30s` and accepts an explicit
  positive duration. Existing executions may finish, including nested calls,
  but framework-owned shutdown waits never exceed the shared budget apart
  from bounded scheduling overhead. Uncooperative work is interrupted/fenced
  without indefinite executor destruction. No application JVM halt is used.
- [ ] New top-level invocations after framework shutdown begins are rejected
  before root session execution, including admission races and callers that
  previously passed `validate`. Already-admitted executions can make nested
  calls within their deadlines. No Sidecar-specific gate is needed for this
  framework guarantee.
- [ ] `RestSkillHandler` and `RestSkillInvocation` are public in
  `ai.loomspan.api`, present in the architecture test allowlist, documented in
  the README supported-surface list, and expose only JDK types.
- [ ] A manifest with `rest: true`, `name`, `description`, `input_schema`,
  and `rbac_roles` registers as `REST_SKILL`; `SkillTemplate.invoke` returns
  the handler's text and the handler received the validated input map.
- [ ] Each forbidden field with `rest: true`, and `rest` with any value other
  than `true`, fails startup with a diagnostic naming the resource, skill, and
  field.
- [ ] `rest: true` without `model` starts; a non-REST manifest without
  `model` still fails as today.
- [ ] Zero handler beans with a REST manifest fails startup naming the
  manifests; two handler beans fail startup naming both beans; zero REST
  manifests with zero handler beans starts normally.
- [ ] A YAML planner with a REST child in `allowed_skills` calls it as a tool,
  the child's success satisfies a `required: true` constraint and an
  `evidence` expression, and the trace shows the leaf's existing direct
  mission/tool frames under its skill name. No new frame-kind field is required.
- [ ] `rbac_roles` on a REST skill denies an unauthorized caller for root and
  nested invocation with the same `AccessDeniedException` behavior as Java
  skills.
- [ ] Inside the handler, `SecurityContextHolder` yields the caller's
  `Authentication` for both root and nested invocation, and the previous
  context is restored afterward.
- [ ] Handler runtime failures surface as `SkillException` at the facade,
  except `AccessDeniedException`, which remains unwrapped, with a
  bounded trace failure record; a `null` return fails the skill; an empty
  string succeeds.
- [ ] Name collisions involving REST skills fail startup naming both
  declarations.
- [ ] With observability enabled, the skills list and detail routes succeed
  for a REST skill and report a REST kind; any compatibility-contract change
  includes coordinated framework/Console updates and an explicit
  `consoleCompatibilityVersion` decision.
- [ ] `LoomspanPublicSurfaceArchitectureTest` and the full starter test
  suite pass.
- [ ] Handler inputs preserve contract-permitted null values and prevent
  mutation of nested maps/lists according to the value contract resolved in
  G1, including direct construction of the public invocation value.

## Ticket boundary

Use the two FW1 units in the [planning handoff](../beta4-ticket-readiness.md):
framework lifecycle first, then REST skills and Console. The lifecycle unit
owns the shared root operation and bounded shutdown, including existing
success observation. The REST unit includes nested/authentication/failure
proof and the entire required FW3 contract/rendering change as it lands.
No later hardening ticket supplies missing correctness. FW2 extends the same
root operation to failure observation and tests the new pre-check race.

## Pipeline notes for the tickets

- Growing the `LoomspanPublicSurfaceArchitectureTest` allowlist by the two
  named SPI types is intentional; this phase deliberately introduces the first
  supported SPI.
- Adding a required-bean rule that fails startup is intentional and applies
  only when REST manifests exist.
- Changing the `ExecutionCoordinator` kind check from Java-only to
  "directly invocable" is intentional; do not add a parallel REST branch.
- The shared shutdown lifecycle is source-grounded in G3. Include its
  configuration, bounded executor destruction,
  and lifecycle/timeout tests as coherent framework work; do not hide it in
  Sidecar packaging or treat the earlier two-PR sketch as a fixed constraint.
