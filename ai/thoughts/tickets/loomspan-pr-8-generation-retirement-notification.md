# PR 8 — Notify applications when a skill generation is safe to retire

GitHub ticket: #8.

## Outcome

An application can use the existing `SkillReloader` to learn when an obsolete
published generation can no longer execute, then clean up resources it owns for
that generation. One fixed `RestSkillHandler` continues selecting routes and HTTP
clients using `RestSkillInvocation.generationId()`.

Keep this feature small. Applications own resource creation, destruction, cleanup
retries, and readiness; Loomspan owns the safe-retirement signal.

## Agreed approach and scope

- Add callback registration to the supported `SkillReloader` API. The intended
  shape is `AutoCloseable onGenerationRetired(Consumer<String> listener)`, returning
  a registration handle. The callback receives the existing process-local
  generation ID. Use standard Java types; do not introduce a new service SPI,
  replaceable framework bean, Spring event contract, or REST resource manager.
- Reuse existing generation capture, admission, pending-release, execution
  completion, and physical-task-return paths. Today, old generations remain alive
  through ordinary references; there is no explicit safe-retirement notification
  system. Add only the internal generation ownership accounting needed to make the
  signal correct. Do not build a parallel execution registry or general lifecycle
  subsystem.
- This project is in development. Breaking/destructive changes within this work's
  scope are welcome when they simplify the design. Do not retain obsolete paths,
  compatibility shims, or duplicate APIs solely for backward compatibility.
  Preserve generation capture and authorization behavior. Keep the supported API
  deliberate, documented, and covered by public-surface tests.

## Safety contract during normal operation

- Successful publication alone is not retirement. Notify only after a published
  generation cannot be captured for new execution and every existing owner has
  released it. The initial configured generation follows the same rule.
- Ownership must cover capture before root admission, pending admissions, running
  roots, and nested/parallel descendants. Publication racing with input preparation
  or admission must not leave an untracked old-generation invocation.
- A root may invoke another REST child later. A quiet interval between REST calls
  does not establish safety. Likewise, failure, cancellation, future completion,
  or deadline expiry does not prove that physical work has returned. Descendants
  still executing after cancellation must retain ownership until they return.
- Release ownership on failed preparation of an invocation, failed execution,
  explicit release of a pending admission, and actual completion, as appropriate.
  Dropping an unreleased admission handle does not promise automatic reclamation;
  no new garbage-collection hooks, admission expiry, or leak-recovery mechanism is
  required.

## Registration and delivery

- Document one initialization sequence: register the listener, stage resources for
  the initial generation, then enable application traffic and publication. For
  updates, prepare the candidate, stage its resources by ID, and publish it.
  The application coordinates initialization; no framework readiness gate is needed.
- With the registration kept active and initialization followed, attempt one
  callback per retired published generation during normal operation. Include
  unused generations superseded immediately by later publications. Notification
  may occur before the publishing call returns; resources must already be staged.
- No historical replay, durable delivery, automatic retries, or ordering across
  different generations is promised. A later generation may retire first. Late
  registration does not recover earlier notifications.
- Keep delivery lightweight. Call listeners outside framework lifecycle and
  publication locks. Document the actual callback threads and possible concurrent
  delivery; listeners must be thread-safe and return promptly. Slow resource cleanup
  belongs on an application-owned executor. Do not add a framework delivery executor
  or queue merely to run application cleanup.
- Isolate and report notification-consumer failures so they cannot alter invocation
  results, undo publication, prevent other listeners from being attempted, or stop
  subsequent retirement processing. Cleanup retries remain application-owned.
- Closing the registration removes it from future notification selection, without
  waiting for callbacks already selected or running. Document that race explicitly;
  no callback-drain protocol is required.
- Never-published candidates do not receive retirement notifications. The application
  cleans up unused staged resources when it discards a candidate or publication is
  rejected before activation. A repeated publish rejection does not make an already
  published candidate unused or safe to clean up.

## Shutdown boundary

Delivery is guaranteed only during normal operation. Once framework shutdown begins,
notifications may be omitted, including for the active generation. Loomspan must not
wait for notification delivery or application cleanup, add a shutdown drain budget,
or promise delivery after context destruction. An already-running callback is not
forcibly drained. Any notification actually delivered must still mean safe retirement;
shutdown, interruption, or a deadline must never manufacture that condition.

Application shutdown cleanup is application-owned. Do not add a forced-cleanup
policy or resource manager to Loomspan as part of this work.

## Acceptance criteria

- [ ] The supported `SkillReloader` registration API and a short application example
  show one fixed REST handler, generation-keyed resources, initialization ordering,
  and cleanup without internal imports.
- [ ] Focused tests prove no early notification when publication races with capture
  before admission, a pending admission, or running nested/parallel work.
- [ ] After supersession and final ownership release, the registered application
  receives the generation ID. Failed invocation paths and explicit pending release
  release ownership correctly.
- [ ] Rapid successive publications, including unused generations, deliver the
  expected callbacks through the documented initialization sequence.
- [ ] Uncooperative work retaining resources after cancellation or deadline expiry
  prevents a premature retirement notification until it actually returns.
- [ ] Throwing consumers cannot corrupt publication, invocation outcomes, other
  listener attempts, or later retirement processing. Registration removal follows
  the documented in-flight callback semantics.
- [ ] Shutdown coverage demonstrates no added notification wait or drain budget and
  no false safe-retirement signal. Delivery during or after shutdown is not required.
- [ ] Public documentation states registration, delivery, threading, error, shutdown,
  and unused-candidate cleanup semantics. Run `LoomspanPublicSurfaceArchitectureTest`
  after production changes and keep the supported API allowlist accurate.

## Context and implementation discipline

Sidecar needs this signal to clean up generation-keyed external resources after
publishing skill updates. This ticket contains framework work only: no Sidecar
implementation, backup-history retention, polling API, or historical invocation API.

Coordinate with [PR 7](loomspan-pr-7-application-supplied-skills.md): configured startup
and updates prepared from either configured resources or application-supplied content
must share the same published-generation retirement semantics. No special startup
mode or second lifecycle is required.

Use a focused design review of ownership and publication races, then implement and
run the relevant lifecycle and public-surface tests. A mandatory five-step pipeline
is not part of this ticket. Prefer the smallest implementation that establishes the
safety contract; extra infrastructure needs a concrete correctness justification.
