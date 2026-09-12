# Phase SC5 — Packaging and release

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Depends
on SC1–SC4. Last PR of beta 4. Framework/release assumptions grounded at
`1e4eb455`; see [SC5 evidence](../beta4-code-grounding.md#sc5--bounded-shutdown-is-not-established).
G3 is resolved as a framework lifecycle change in FW1. Sidecar stops dispatch
immediately and has no independent drain period.

## Goal

Sidecar is deployable next to any application as a container, documented
end to end, and released pinned to framework `v1.0.0-beta.4`.

## In scope

- Prepare packaging and release checks against the locally installed framework
  snapshot. Verify end-to-end integration before the framework tag, resolve
  any framework gaps, then release the framework first. Switch Sidecar to
  `1.0.0-beta.4`, verify against the published dependency, and only then tag
  and publish Sidecar.
- Container image (Spring Boot build-image or a Dockerfile; pick one and
  document) with `/sidecar` as the default configuration-files mount point
  (`skills/` and `rest-routes.yaml`) and all secrets via environment variables;
  non-root user; JVM defaults suited to a sidecar.
- Health/readiness endpoints on the management port for orchestrator probes.
- On shutdown, immediately mark readiness down, reject new execution `POST`s
  with `503`, and close dispatch into `SkillTemplate.invoke`. Pending queue
  entries are discarded, with count/byte reservations and input/security
  references released. Do not drain the queue or start a Sidecar timer.
  Synchronize the shutdown/dispatch boundary so a dequeued request cannot
  start a new invocation after dispatch closes.
- Discard queued work directly; do not run the queue through Loomspan merely
  to receive shutdown rejections. The framework independently rejects new
  top-level execution admission after its shutdown starts, catching dispatch
  races and protecting other callers too. Existing execution nesting remains
  framework-owned and is not classified as new root admission.
- Executions already handed to Loomspan may finish under existing mission
  timeouts and the framework's overall `loomspan.shutdown.timeout` (default
  `30s`). Sidecar shutdown must not immediately interrupt their waiting
  caller threads or tear down handler HTTP clients before the framework has
  completed or reached its deadline. At that deadline the framework cancels
  remaining work and bounds cleanup; no unbounded executor `close()` wait.
  Use an independent `ContextClosedEvent` listener that closes Sidecar's
  gates, discards queued work, and returns promptly. It does not wait, call
  the framework, or depend on framework listener priorities. Opt out of
  asynchronous event execution and filter to the owning context. The framework
  waits during Spring's subsequent lifecycle-stop stage. Keep handler clients
  and waiting caller workers alive through completion/cutoff, then use ordinary
  resource lifecycle stop/destruction with no unbounded executor `close()`.
  G3 records the pinned Spring source stages. No internal bean override,
  public shutdown API, cross-repository priority convention, or second grace period.
  Restart loses unfinished work and retained records; no durable recovery.
- Fresh-review R1 is resolved: either listener may run first. Framework
  lock-safe cutoff and observer completion are FW1/FW2 prerequisites for
  this phase's integration tests, not properties supplied by Spring alone.
- Release workflow: on `v<version>` tag, build, test, publish the image (and
  a jar archive) with checksums; verify the framework dependency is the
  released `1.0.0-beta.4`, not a SNAPSHOT.
- README quick start: run the image with a sample `/sidecar` directory
  (`skills/` with one YAML planner + two REST leaves, and `rest-routes.yaml`
  containing both `targets` and `routes`)
  against a bundled stub REST service, call it
  with `curl` using a JWT from a documented local test issuer/host backend;
  supply the planner's model/connection configuration (or a documented local
  model stub) as well as the REST service;
  demonstrate the callback host verifying the forwarded token and restoring
  the caller's identity and roles. No external IdP account or client SDK is
  required for the quick start. Kubernetes sidecar example
  manifest; configuration reference for all `loomspan-sidecar.*` keys and
  the unified target/route-file format, including location overrides and
  environment placeholders for secrets;
  security guidance (HTTPS/mTLS, issuer/key verification, token audiences,
  token lifetime including queue time, no refresh). Explain existing-token
  forwarding and backend-issued tokens for session-based applications.
- Optional Console pairing instructions via `loomspan.observability`.
- Document the update procedure: finish active work and retrieve needed
  results, edit skills and routes, then restart. Disk edits do not affect
  the running instance. Restart clears the in-memory execution store;
  queued work is discarded at shutdown; unfinished framework executions may
  be interrupted at the framework shutdown deadline. Invalid skills or
  routes prevent startup/readiness until corrected. No hot-reload workflow.

## Acceptance criteria

- [ ] Reuse the common Sidecar application fixture for HTTP/JWT/queue/route
  integration. Packaging checks prove image startup and the quick start;
  focused boundary tests own exhaustive binder and authentication cases.
- [ ] Shutdown integration covers active observer delivery as well as skill
  execution, blocked trace writes, management-context close events, and
  asynchronous standard event multicasting. Exercise both relative listener
  orders and prompt gate closure before the lifecycle wait through public
  contracts only, with clients/callers alive until completion/cutoff. A shorter
  Spring lifecycle-phase timeout does not shorten the framework's budget.
- [ ] Integration against the framework snapshot proves pre-dispatch checks,
  asynchronous identity propagation to nested REST calls, and all three
  diagnostic-history modes before the framework is tagged. Sidecar then
  passes verification against the published framework release.
- [ ] Image builds in CI and starts with only environment variables and a
  mounted `/sidecar` directory containing `skills/` and `rest-routes.yaml`.
- [ ] Quick-start commands succeed as written against the published image.
- [ ] Readiness fails until skills are registered and route validation is
  complete; liveness stays up during a long execution.
- [ ] Shutdown marks readiness down, rejects new admission with `503`, and
  immediately prevents further framework dispatch, including dequeue races.
  Queued requests are discarded and their accounting/references released.
- [ ] Already-dispatched work can complete under framework timeout rules;
  Sidecar introduces no drain timer or early interruption. Framework shutdown
  honors its configured deadline and application teardown adds no unbounded
  wait on its executor or on Sidecar caller workers.
- [ ] Tag workflow publishes image and archive with checksums and refuses a
  SNAPSHOT framework dependency.
- [ ] Configuration reference matches the bound properties exactly (test
  generated or asserted); examples load routes from the separate file and
  document restart-only activation.

## Ticket boundary

The Sidecar packaging/release unit in the [planning handoff](../beta4-ticket-readiness.md)
owns image/probes, resource-lifecycle integration, quick start, configuration
guidance and release workflow. The SC2/SC3 unit already supplies the gate and
accounting cleanup. Prepare against the snapshot, prove integration, complete
framework release verification/publication, then pin/verify/release Sidecar.

