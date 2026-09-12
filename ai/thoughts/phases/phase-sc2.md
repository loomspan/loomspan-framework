# Phase SC2 — Execution API

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Depends
on SC1 and the locally installed framework snapshot (FW1 REST kind, FW2
catalog, `validate`, and failure observer history). Framework assumptions
grounded at `1e4eb455`; see [SC2 evidence](../beta4-code-grounding.md#sc2--public-facade-supports-the-design-not-unlimited-physical-concurrency).
G2/G3 policy is resolved: 1MB incoming JSON, uncapped result/history payloads,
and immediate Sidecar dispatch stop with framework-owned bounded shutdown.
SC2 and SC3 remain responsibility checklists, but their initial execution API
and JWT authentication land together as one coherent delivery unit.

## Goal

Any HTTP client can list skills, start a skill execution, and read its
outcome, using only the framework's public API underneath.

## Routes (all require the JWT authentication delivered with SC3)

```text
GET  /v1/skills                        catalog from SkillCatalog
GET  /v1/skills/{name}                 one descriptor; 404 unknown
POST /v1/skills/{name}/executions      body = JSON input object; asynchronous only
GET  /v1/executions/{id}               owner-only; 404 unknown/expired/foreign
```

## Binding decisions

- Bound incoming JSON to `max-input-size` (default `1MB`) while reading,
  before parsing or framework validation. Oversize requests return `413`
  without an execution record. This is separate from waiting-queue accounting.
- `POST` flow on the request thread: resolve skill via catalog (404) →
  `SkillTemplate.validate(name, input)` → `400` with issues on
  `SkillInputValidationException`, `403` on `AccessDeniedException` →
  measure input payload → reserve capacity and create execution record →
  dispatch to a worker or waiting queue → `202`. Capacity rejection returns
  `429` without an accepted execution. POST requires a JSON object containing
  the input map; missing, null, malformed, or non-object JSON is `400`.
  An empty object proceeds through the skill's normal input validation.
- Worker: runs `SkillTemplate.invoke(name, input, observer)` under the
  propagated `SecurityContext` (Spring Security
  `DelegatingSecurityContextExecutor` or equivalent). Records `COMPLETED`
  with result text; or `FAILED` with
  `failure.kind` (`INPUT_VALIDATION`, `ACCESS_DENIED`, `SKILL_FAILURE`),
  message, and issues when applicable. Retain and include observer events
  according to `diagnostics` for either terminal outcome. FW2 supplies the
  public observer contract for failed executions.
- Classify the primary exception thrown by `invoke`; do not infer a different
  failure from history. Concurrent child failures use the framework's existing
  ordered primary-failure selection after the current unit joins, with no
  Sidecar-specific precedence for access denial over other failures.
- Execution representation: `id`, `skillName`, `status`
  (`QUEUED|RUNNING|COMPLETED|FAILED`), `createdAt`, `completedAt`, `result` (string,
  unparsed), `failure`, `events` (only when selected by `diagnostics`). `Location` header on
  `202`.
- v1 has no `wait` parameter or synchronous completion mode. Accepted POSTs
  return `202` without waiting for execution; clients poll GET for status
  and the eventual outcome. Client disconnect does not cancel accepted work.
- GET returns `200` when it retrieves a known, owned execution, including
  `FAILED`; the record describes the execution failure. `500` means Sidecar
  failed to handle the HTTP request itself. Unknown/expired/foreign records
  remain `404`. As soon as shutdown starts, new execution admission is
  rejected with `503`, readiness is down, and dispatch to Loomspan stops (SC5).
- Store: bounded `ConcurrentHashMap`; limits `max-retained` and
  `completed-ttl`; at capacity → `429`. `max-retained` counts all accepted
  records (queued, running, and completed/failed); TTL starts at completion.
- One internal admission/accounting owner coordinates one executor work
  queue and this one retained-record store. Add byte accounting to ordinary
  executor/queue facilities; do not add a staging queue or independent
  counters/semaphores representing the same capacity. Keep admission locks
  out of skill execution, observer delivery, and response serialization.
- Queued/running tasks own invocation input and captured authentication.
  The readable record holds status and ultimately one atomically published
  terminal outcome with selected events. No separate result/history store.
  Use one simple expiration sweep rather than a timer/future per record or
  a new cache subsystem. Reads enforce TTL and admission reclaims expired
  capacity; retain the existing limits and completion-based TTL.
- Completed records retain ownership, status/timestamps, result or failure,
  and selected diagnostic history. They do not retain invocation inputs or
  the execution's security context. Release worker-held inputs and security
  context when the worker exits; ownership needs only issuer and subject.
  This releases Sidecar-owned references; a framework worker that ignores
  interruption can outlive the facade and retain its own references (G3).
- Use an ordinary bounded executor with `max-concurrent` workers and a
  waiting queue limited by `max-queued` and `max-queued-input-size`. Worker
  saturation alone queues a request; a full queue or a queued-byte limit
  that would be exceeded rejects it with `429`. Accepted waiting requests
  have status `QUEUED`; status becomes `RUNNING` when a worker takes them.
- Measure the UTF-8 byte size of the JSON input map once after validation
  and before admission, and store the size with the request. This measures
  the input passed to `invoke`, not character count, attachment contents
  fetched during execution, or exact Java heap usage. Keep the input in
  memory without retaining an extra serialized copy solely for accounting.
- `max-queued-input-size` limits the sum of measured inputs waiting in the
  queue. Check and reserve queue count and bytes together under one
  synchronized admission/accounting operation. Allow a sum equal to the
  limit; reject an addition that would exceed it. Requests dispatched
  directly to available workers do not consume the waiting-queue budget.
- Release queued count/bytes exactly once when a worker takes a request or
  it is removed without execution. Roll back reservations and the record
  if dispatch fails. Worker capacity is freed when the worker exits, not
  merely when a timeout is reported. Rejected requests leave no record or
  capacity reservation behind. Queued requests retain the submitting
  identity/security context for execution by the worker.
- Accepted work retains the identity and roles verified at admission, even
  if the JWT expires while queued or running. Do not add a token-expiry
  recheck at worker handoff. Framework access checks still use the captured
  authentication; passthrough targets independently validate the token when
  called, as specified in SC3.
- Queue bytes exclude running inputs, retained results, and diagnostic
  history; this is not a total-process memory limit. No disk spill,
  periodic heap inspection, or custom scheduling system in this phase.
  Sidecar has no shutdown drain timer: SC5 immediately closes dispatch and
  discards requests not yet handed to Loomspan, releasing their reservations
  and references. Work already handed off belongs to framework shutdown.
- Grounding found the framework's independent mission executor can continue
  uncooperative work after a timeout returns from `invoke`. The proposed
  `max-concurrent` limit therefore bounds Sidecar facade calls, not all nested
  or lingering downstream operations. The framework owns its bounded shutdown
  lifecycle. No Sidecar byte cap is added to final results or diagnostic
  history; retain and return what the framework produced according to the
  selected diagnostics mode and existing record-count/TTL policy. These are
  not total-heap guarantees.
- Ownership: record a small internal immutable value with explicit `issuer`
  and `subject` fields from the verified JWT at `POST`. Share its definition
  with SC3; compare both fields on reads. A different issuer or subject →
  `404`; a renewed token for the same pair retains access. No joined identity
  string, auth-kind hierarchy, or framework API addition. Use this same owner
  value with SC3 from the first implementation. Ordinary test JWTs and mocks
  are sufficient for focused tests; no transitional production authentication
  implementation, owner type, or migration adapter.
- Errors: RFC 9457 `application/problem+json` for HTTP errors; execution
  exceptions use the failure representation. Preserve the message of the
  `SkillException` returned by the framework, allowing the representation
  wrapper without introducing message sanitization. Such messages are not
  sanitized. Sidecar adds no data sanitization for inputs, results, exception
  messages, or diagnostic history. Stack traces are not part
  of the ordinary failure envelope; diagnostic history is separately
  controlled by `diagnostics`.
- Diagnostic history: `loomspan-sidecar.executions.diagnostics` uses the
  framework trace-persistence mode names: `NEVER` (default), `ONERROR`,
  `ALWAYS`. `NEVER` retains and returns no observer events; `ONERROR`
  retains and returns events only for `FAILED` executions; `ALWAYS` retains
  and returns events for both `COMPLETED` and `FAILED` executions. Events
  remain in the execution representation's `events` field alongside the
  result or failure, available through polling.
  The same GET response supplies both the terminal outcome and selected
  history; there is no separate history endpoint or readiness state. History
  shares the record's `completed-ttl` (default 15 minutes after success or
  failure). Reads do not extend that TTL. Expiry removes the entire record;
  restarting the instance loses it sooner. History has no separate store or
  retention lifecycle. Selected events may themselves contain business input
  data even though the original invocation input/security context is released.
  Pre-dispatch validation/authentication/authorization errors have no
  execution history. This setting controls Sidecar response diagnostics,
  independently of the framework's trace persistence setting.
- Result is never parsed, transformed, or truncated by a Sidecar size policy.
  Selected history is not omitted or truncated because of its size. There
  is no result-too-large failure or history-omitted-size marker.
- No reload route or execution/catalog version binding in the initial
  release. Skills and REST routes are fixed for the lifetime of the
  application instance; disk edits take effect only after restart, which
  loses this in-memory execution store.
- Catalog visibility: the framework's `SkillCatalog` does not filter by
  caller authorization, so any authenticated caller sees every skill's name,
  description, kind, and input schema, including skills its roles cannot
  invoke. Accepted for beta 4; Sidecar adds no role-based filtering.
  Authorization is enforced at `POST` via `validate`.

## Configuration defaults

```yaml
loomspan-sidecar:
  executions:
    max-input-size: 1MB
    max-retained: 1000
    completed-ttl: 15m
    max-concurrent: 32
    max-queued: 128
    max-queued-input-size: 64MB
    diagnostics: NEVER # NEVER, ONERROR, or ALWAYS
```

## Acceptance criteria

- [ ] Concurrent mixed child failures retain all available failure history
  when selected, while `failure.kind` matches the facade's primary exception.
  The execution does not become terminal before its selected observer
  events have been attached to the same published record.
- [ ] Invalid execution limits/TTL fail startup; expiry removes entire
  terminal records and releases retained capacity even without a GET for
  those records. Reads do not extend TTL or race into partial terminal data.
- [ ] Incoming JSON exceeding `max-input-size` returns `413` before parsing,
  validation, admission, or execution; input at the byte limit is allowed.
- [ ] Large produced results and selected history are returned intact, without
  Sidecar output/history byte rejection or truncation.
- [ ] Catalog routes return every registered skill with kind and input schema.
- [ ] Bad input → `400` with issues before any execution; missing role →
  `403`; unknown skill → `404`.
- [ ] Valid `POST` → `202` with id and `Location`; `GET` reaches `COMPLETED`
  with the exact `SkillTemplate` text.
- [ ] GET of a known, owned `FAILED` execution returns `200` with its failure
  representation. New admission during shutdown returns `503`.
- [ ] With all workers occupied, accepted requests report `QUEUED`, then
  `RUNNING` when a worker becomes available. Running executions never
  exceed `max-concurrent`; worker saturation alone does not return `429`.
- [ ] Queue count, queued-input bytes, and retained-record capacity each
  independently reject excess admission with `429`. Boundary tests include
  multibyte UTF-8 input, equality with the byte limit, and simultaneous
  submissions; configured limits cannot be exceeded by admission races.
- [ ] Dequeue and dispatch-rejection tests prove exact accounting cleanup
  and no orphan record. Queued bytes are released on worker handoff, not
  completion; worker capacity remains occupied until the worker exits.
- [ ] Accepted POSTs return `202` without waiting for completion. Only JSON
  objects are accepted as input bodies; malformed, missing, null, and
  non-object bodies are rejected before admission.
- [ ] Completed records do not retain invocation inputs or security context;
  worker-held references are released on exit while owner-only reads work.
- [ ] An asynchronously executed skill with `rbac_roles` sees the inbound
  identity, including after waiting in the queue (test proves
  `SecurityContext` propagation to the worker).
- [ ] A JWT expiring after admission does not prevent queued work from
  starting with the captured identity and roles; no Sidecar expiry recheck
  is introduced. Framework role checks remain in force.
- [ ] Nested child access denial and handler failure appear as `FAILED` with
  the correct `failure.kind`.
- [ ] A framework `SkillException` message is preserved in the failure
  representation without a new sanitization layer.
- [ ] Another identity reading an execution gets `404`; expired executions
  get `404`; capacity → `429`.
- [ ] Default / `NEVER`: events are absent for both successful and failed
  executions. `ONERROR`: events are present only for failed executions.
  `ALWAYS`: events are present for both outcomes. Failure history includes
  events recorded before the failure and is readable by the owner through
  polling; pre-dispatch errors have no history.
- [ ] Selected history and the terminal outcome are returned together by
  GET and expire together after `completed-ttl`; reads do not extend expiry.
- [ ] ArchUnit public-API-only test still passes.

## Ticket boundary

SC2 and SC3 form the authenticated execution API unit in the
[planning handoff](../beta4-ticket-readiness.md). Deliver the real JWT path,
one shared owner value, queue/store accounting, diagnostics and independent
shutdown gate together, with application proof. Reuse one Sidecar fixture;
SC5 adds actual packaged lifecycle/resource proof, not the first gate implementation.

