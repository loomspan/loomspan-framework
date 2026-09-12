# PR 5.4 — Document beta 4 and establish gated framework release readiness

## Outcome

Humans and coding agents can author REST skills and use the supported SPI,
catalog, pre-check and observation contracts without internal APIs. Framework
release preparation is complete, and final release-commit verification remains
explicitly gated on real Sidecar integration against the development snapshot.
Readiness must not be mistaken for permission to tag or publish early.

## Requirements — binding

- Complete README guidance for REST manifest syntax: only `rest: true`, required
  name/description, optional input schema/roles, generic-object default and all
  forbidden leaf fields (`model`, `prompt`, `thinking_level`, `allowed_skills`,
  `planning_mode`, `concurrency`, `max_steps`, `linter`, `output_schema`,
  `output_schema_max_retries`), including forbidden null/empty presence.
  Explain shared exact-name registration, normal input/reference processing,
  root/nested authorization, success credit and direct execution without a model.
- Document `RestSkillHandler`/`RestSkillInvocation`, the REST-only exactly-one
  bean rule, no framework default handler, immutable nested input containers
  with permitted nulls and Resource-content limits, scoped SecurityContext
  rather than identity/session SPI arguments, empty-result success/null-result
  failure and REST's propagated failures versus Java's adapter behavior.
  Handler owns routing/URLs/headers/credentials; the model sees schema-permitted
  inputs. No new sanitization guarantee for input, result, errors or history.
- Document the eager immutable, unfiltered public catalog and its exact model
  input schemas, both `validate` overloads including their different null rules,
  matching exceptions, no-session/no-trace pre-check and execution-time recheck.
  Validation neither reserves shutdown admission nor checks future children.
- Document observer success/failure history, no callback before session creation,
  at-most-once completed delivery after binding restoration, available-history
  limits, original execution-failure precedence and unchanged success-observer
  exceptions. History uses the public view, not a new retrieval API.
- Document positive-duration `loomspan.shutdown.timeout: 30s`, its one overall
  framework-owned budget including observation/cleanup, continuing mission
  deadlines/nested work and root rejection. Explain independent prompt host/
  framework close-event gates and the subsequent framework lifecycle wait with
  required resources alive until completion/cutoff. Sidecar immediately stops
  dispatch/discards queued work, has no drain timer and no shared listener
  priority convention. Uncooperative work is fenced/interrupted; the framework
  does not bound unrelated application hooks or halt the JVM.
- Update supported-surface guidance for the two SPI types, three catalog types,
  both methods and observer behavior. Consumer guidance exposes no internal or
  autoconfigure types. Only the named handler SPI is deliberately supported;
  neither catalog injection nor public Spring machinery grants bean replacement.
- Add/update `agent-skills/loomspan-docs` REST skill-authoring and java-api
  references, invocation/observation/error and Java comparison guidance, and
  knowledge-set README routing. Keep `metadata.loomspan-version` coordinated
  with POM/skill versions by existing `scripts/loomspan_version.py` tooling.
- Verify combined coverage in existing `SupportedSurfaceIntegrationTest`:
  local OpenAI-compatible stub, YAML planner, REST test-handler leaf and Java
  leaf; catalog/validate via public API and observation only as
  `SkillExecutionView`, including failure history without masking its exception.
  PRs 5.1–5.3 own their feature tests/documentation as they land. Reuse their
  fixture; no duplicate harness or later hardening substitute for correctness.
- Produce beta compatibility/release notes distinguishing additive public types
  from `SkillTemplate` source/binary impact, failure-observer and shutdown
  behavioral changes, REST manifest/handler startup rules and Console protocol
  semantics. Carry forward PR 5.2's project-version marker rationale, matching
  beta 4 framework/Console versions and exact-version-only diagnostic policy;
  no schema counter, legacy readers or internal compatibility shims.
- Correct README release command examples to use release `v1.0.0-beta.4`, never
  a SNAPSHOT tag. Use existing coordinated version tooling and release workflow;
  no new release/version abstraction. Prepare and run local README dry-run
  checks: version check, version-script unit tests, `clean verify` and
  release-profile verify. Final local and manual Console Release/Maven Central
  Release validation runs must pass on the final release commit after the
  integration gate below. Validation mode must not publish.

## Dependencies and release gate

Depends on [PR 5.3](loomspan-pr-5.3-external-caller-api.md) and transitively
[PR 5.2](loomspan-pr-5.2-rest-skills-and-console.md) and
[PR 5.1](loomspan-pr-5.1-framework-lifecycle.md). Owns all five FW4 criteria.
Documentation and local snapshot preparation can finish before Sidecar work;
the final release-verification criterion stays pending until SC5 snapshot
integration. Do not manufacture a cycle by requiring a published framework
before Sidecar development, or mark complete based on preparation alone.

During authorized implementation, install
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.4-SNAPSHOT` locally with
`mvn install`. Record the actual implemented framework commit for Sidecar CI
to build/install before Sidecar; reinstall after changes. The historical
grounding commit `1e4eb455` does not contain these features and is not a usable
feature pin. Introduce no snapshot repository.

Before final framework release verification, Sidecar's SC5 integration must
prove pre-dispatch validation/authorization; inbound JWT context propagation
through asynchronous invocation and a YAML planner to the REST handler and
host callback; selected failed-execution history under `ONERROR`/`ALWAYS`
and absence under `NEVER`; plus its required packaged shutdown/listener/resource
proof. Framework tests alone cannot prove HTTP/JWT/route/queue/container wiring.
Resolve integration-discovered framework gaps here in the framework repository,
reinstall the snapshot, reverify integration and rerun affected checks.

Run final release-commit local/workflow validation only after that gate.
Subsequent separately authorized release work publishes framework
`v1.0.0-beta.4` first, then pins Sidecar to the released artifact, verifies
against that dependency and releases Sidecar. Never overwrite released versions.
This ticket prepares and verifies readiness; it does not authorize tagging or
publication in either repository.

## Acceptance criteria

- [ ] README covers every new manifest/API/configuration/observer behavior and
  all limits above accurately, including the new named supported SPI and
  security/immutability/failure distinctions, without recommending internal
  types or unintended replacement surfaces.
- [ ] Agent authoring and java-api references and knowledge routing cover the
  same contracts; bootstrap/version coupling passes `loomspan_version.py check`.
  Release examples use the actual non-SNAPSHOT release version.
- [ ] Existing public-surface integration coverage combines YAML planner,
  REST and Java leaves, catalog and both pre-check semantics through
  `ai.loomspan.api` only, observing only the public view. Feature-unit proof
  remains with its owner rather than duplicated in a new harness.
- [ ] Cumulative public proof demonstrates failure history and exception
  precedence, including no callback on pre-session rejection and unchanged
  success-observer behavior. Compatibility notes accurately separate additive
  APIs, intentional breaks and the coordinated Console marker decision.
- [ ] Local version/script/full-build/release-profile dry runs pass. The actual
  development commit and snapshot integration evidence are recorded; after
  SC5 integration and any resulting fixes, final local checks and manual
  Console Release/Maven Central Release validation workflows pass on the final
  release commit. Until then this criterion remains pending, with no early tag
  or publication and no claim that old grounding tests prove beta 4.

## Context and exclusions

Current requirements are the [roadmap](../phases/beta4-rest-skills-and-sidecar-roadmap.md)
and [FW4](../phases/phase-fw4.md); [readiness](../beta4-ticket-readiness.md) assigns
ownership. [Lens](../framework-feature-design-lens.md),
[review](../beta4-design-review.md) and [grounding](../beta4-code-grounding.md)
provide rationale and historical evidence, not unresolved superseded decisions.
Exact commands/options and source locations are verified by the pipeline using
current repository instructions; existing tooling/fixture names are reuse hints.

Sidecar implementation, documentation, workflow seeding and tickets are out of
scope. Its eventual tickets belong only in
`C:\opendev\code\loomspan-sidecar\ai\thoughts\tickets`. Sidecar remote access,
release credentials and registry provisioning were not verified during planning;
they are future CI/release inputs, not missing framework product intent.
Keep reload, retries, extra handlers, token exchange, new sanitization and
historical/cross-version compatibility excluded. No GitHub PR creation,
tagging, publishing or Sidecar release action is authorized by this ticket.
