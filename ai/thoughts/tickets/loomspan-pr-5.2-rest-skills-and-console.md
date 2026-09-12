# PR 5.2 — Execute REST leaf skills and display them in Console

## Outcome

An embedded application supplies one handler and ordinary YAML REST leaf
manifests. Callers and YAML planners invoke those skills through the existing
skill lifecycle, with validated input, trusted caller identity, authorization,
visible failure and useful Console diagnostics. REST and every required
framework/Console consumer land together; no merge exposes an unsupported
third kind.

## Requirements — binding

### Public SPI and immutable handoff

- Deliberately introduce the first supported SPI in `ai.loomspan.api`:
  `RestSkillHandler` with `String handle(RestSkillInvocation invocation)`, and
  record `RestSkillInvocation(String skillName, Map<String, Object> input)`.
  Allowlist both in `LoomspanPublicSurfaceArchitectureTest`, document them in
  the README supported surface, and prove use through public API. Their
  signatures use JDK types only. This does not open other bean replacement.
- Reuse existing input validation, normalization and reference resolution.
  The handler gets the resulting arguments before Java method-parameter
  binding, not raw input or a new context bag. Business inputs remain explicit;
  do not inherit/merge parent input implicitly.
- The public invocation constructor rejects null components and defensively
  copies/fixes nested map/list containers, including direct construction by
  embedded callers. Preserve schema-permitted null values inside containers.
  Generic normalization's shallow copy is insufficient. Reuse/narrow existing
  copy logic without another validator/converter or configurable copy framework.
  Resolved Resource handles are permitted; their backing contents are not
  promised immutable. Do not route values through diagnostic events to copy
  them. Sidecar's separate JSON-only transport rule does not restrict this SPI.
- Scoped caller `Authentication` reaches `SecurityContextHolder` for root and
  nested invocations on the actual execution thread and the previous context
  is restored. Authentication, description and session/trace IDs are not SPI
  parameters. Handler-owned URLs/headers/credentials are not manifest inputs;
  arbitrary business inputs and endpoint text have no new sanitization guarantee.

### Manifest and registration

- `rest` accepts only boolean `true`. False, null, strings and other values
  fail startup with resource/skill/field diagnostics instructing authors to
  omit `rest` for non-REST skills. Presence of any forbidden field fails even
  when its value is null or empty: `model`, `prompt`, `thinking_level`,
  `allowed_skills`, `planning_mode`, `concurrency`, `max_steps`, `linter`,
  `output_schema`, `output_schema_max_retries`.
- Only `name`, `description`, optional `input_schema` and optional `rbac_roles`
  accompany `rest: true`. Name and description remain required under existing
  rules. No schema means the existing generic object contract. Non-REST YAML
  still requires `model` and retains all existing validation.
- Reuse raw-tree checks and declared-field tracking. Coherently update the
  definition's execution-configuration invariant, model resolution and
  registration; no dummy model configuration for REST.
- Register `CapabilityKind.REST_SKILL`, no execution configuration
  (`SkillExecutionDescriptor.none()`), YAML role policy and a real invoker
  delegating to the handler. Never require model execution configuration for
  REST. Reuse `SkillSource`'s YAML declaration-location fields;
  `CapabilityMetadata.kind` alone owns kind. No new source subtype or
  handler-detail fields.
- If REST manifests exist, require exactly one handler bean. Zero fails
  startup listing every REST manifest resource; multiple fail naming the
  beans. With no REST manifests, handler beans are optional, unused and not
  validated. Keep registration's existing eager completion authority; handler
  construction must not require the future public catalog.
- Keep the shared exact-name namespace: REST/YAML, REST/Java and REST/REST
  collisions fail with both declaration locations. YAML `allowed_skills` may
  reference REST; unknown children still fail startup.

### Execution and diagnostics

- Generalize the coordinator's existing direct branch from Java to directly
  invocable Java/REST; use a REST invoker, no third engine/plan/model interaction.
  Root `SkillTemplate.invoke` and nested planner tools use it. Preserve quotas,
  depth, mission timeouts, write fencing and PR 5.1's root/shutdown ownership.
- Success earns the same plan/task, required-child and evidence credit as Java.
  Denial, cancellation and failure earn none. Preserve current sequential and
  concurrent parent failure behavior; do not reconstruct a primary failure
  from event order or invent access-denial precedence over other failures.
- REST exceptions propagate; bypass Java's exception-to-text adapter.
  `AccessDeniedException` remains unwrapped at the facade; other runtime
  failures become `SkillException`, preserving an existing `SkillException`
  message. Null return fails with "handler returned null"; empty string succeeds.
  Trace failures have the existing bounded stack/message diagnostics. No
  retries, repair or new sanitization; preserve current redaction and fidelity.
- Existing `SkillExecutionView` observation uses the same shape as Java.
  Trace direct mission/tool frames remain under the skill name with no model
  attempts. Do not add a kind field merely to label a catalog or infer trace
  kind from a current catalog. Where records already carry kind, REST stays
  distinct rather than aliased to Java/YAML.
- Observability list/detail DTOs, Java/Go validators, TypeScript unions and
  Console list/detail/trace rendering support REST atomically. Show label
  `REST`, YAML resource path and manifest text, identifying application handler
  execution without exposing unknown URLs/targets or handler internals. Keep
  operator detail separate from PR 5.3's consumer catalog; reuse metadata and
  kind translation without coupling public API to observability DTOs.
- Include `application-rest/skill-rest-detail.json` and affected list fixtures.
  Reuse `ConsoleRestFixtureCorpusTest` regeneration via
  `-Dloomspan.console.fixtures.regenerate=true`; a repeat must produce no diff.
  Regenerate trace/analysis corpora only if their content changes.
- Record the compatibility decision at this first REST protocol change: REST
  expands protocol semantics even if JSON shape stays unchanged. Keep
  `consoleCompatibilityVersion` derived from coordinated `${project.version}`,
  using existing version tooling and the beta 4 release pin, not a separate
  schema counter. Matching beta 4 framework/Console versions are required;
  no extra counter or snapshot-to-snapshot detection contract is introduced.
  Update affected current producers, consumers and fixtures atomically.
  Exact-version diagnostic compatibility remains: resolved missing/unequal
  markers are rejected; both `development` markers permit ordinary complete
  validation without a compatibility promise. No legacy reader, migration,
  fallback, historical catalog or cross-version fixture.
- Correct the registry's stale YAML-only Javadoc and remove the unused manifest
  `normalizeStringListMap`/`normalizeStringList` chain with the owning edits.
  Feature tests and README manifest/SPI/security/failure documentation land
  here. Extend the existing supported-surface integration fixture with a local
  model stub, YAML planner, REST test handler and Java leaf; no parallel harness.

## Acceptance criteria

- [ ] Public SPI use is allowlisted and README-documented with JDK-only
  signatures. Directly constructed and runtime-produced invocation values
  resist outer/nested map/list mutation, preserve permitted nulls and resolved
  Resource handles, and reject null components without claiming immutable
  Resource contents.
- [ ] Valid REST manifests start without a model, register REST and invoke the
  handler with validated/resolved input. Every invalid `rest` value and every
  forbidden field, including null/empty presence, fails with actionable
  resource/skill/field diagnostics. Non-REST validation remains intact.
- [ ] Handler-count checks have the specified diagnostics and are inactive
  without REST manifests. All cross-kind/name collisions report both locations;
  REST child references resolve and unknown references still fail startup.
- [ ] Public root and nested planner invocations prove REST role denial and
  authorized execution, scoped authentication and context restoration,
  required-child/evidence credit on success and none on denial/cancellation/
  failure. Existing quotas/depth/timeouts/fences and parent failure ordering
  hold through the shared direct lifecycle without model attempts for REST.
- [ ] Runtime exceptions, existing `SkillException`, authorization denial,
  null and empty results have the specified facade/trace outcomes. Observation
  retains Java-shaped public history, bounded diagnostics and current redaction
  without claiming messages are sanitized.
- [ ] With observability enabled, REST list/detail calls succeed with kind,
  declaration path/text and correct source validation. Console lists/details
  REST and renders its execution trace without error or misclassification.
  No new source-kind authority or unnecessary frame kind is introduced.
- [ ] REST fixtures regenerate deterministically and are consumed by Go tests.
  The recorded project-version compatibility rationale covers semantic kind
  changes; all affected Java/Go/TypeScript/fixture/rendering consumers agree
  under the same-version policy without legacy adapters.
- [ ] `LoomspanPublicSurfaceArchitectureTest`, the full starter suite and
  Console CI checks (Go, React and e2e) pass. The existing supported-surface
  fixture proves YAML-to-REST plus Java use through public APIs. Required
  documentation and owning cleanup are complete before this unit lands.

## Context, dependencies and exclusions

Depends on [PR 5.1](loomspan-pr-5.1-framework-lifecycle.md). Owns FW1's remaining
REST/SPI/registration/immutability criteria, the shared architecture/full-starter
check and all five FW3 criteria. PR 5.3 supplies catalog/pre-check/failure
observation; PR 5.4 supplies complete agent guidance and cumulative release
readiness, not missing feature correctness.

The [roadmap](../phases/beta4-rest-skills-and-sidecar-roadmap.md),
[FW1](../phases/phase-fw1.md) and [FW3](../phases/phase-fw3.md) are current
requirements; [readiness](../beta4-ticket-readiness.md) owns grouping.
[Lens](../framework-feature-design-lens.md), [review](../beta4-design-review.md)
and [grounding](../beta4-code-grounding.md) explain accepted reuse and prior
evidence, not an obligation to preserve old internal signatures or repeat
superseded decisions. Source anchors are hints; exact helper design and
verification layout remain pipeline choices under these binding constraints.

No HTTP client, default handler, routing manifest fields, per-skill/multiple
handlers, output-schema validation/response mapping, framework retries, token
exchange, new input vocabulary, reload/versioned catalogs or Sidecar-specific
Console behavior. Sidecar owns routes/transports/JWT/JSON-only outbound checks
and is implemented separately. No Sidecar ticket or release action here.

## Pipeline notes

- The two SPI additions deliberately replace the prior "no supported SPI"
  posture only for this named contract; update affected supported-surface
  guidance atomically. Internal/autoconfigure signatures gain no compatibility
  promise or replacement contract; no shims for obsolete internals.
- The REST-only required-bean startup rule and REST's visible exception
  propagation rather than Java's exception-to-text adaptation are intentional.
  Do not change existing Java failure semantics to make them uniform.
- REST's coordinated Console protocol expansion intentionally requires matching
  versions with no backward reader. Retain the project-version marker and
  document its beta 4 semantic rationale; no independently numbered schema.
