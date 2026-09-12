# Phase FW2 — Public API additions for external callers

Part of [beta 4 roadmap](beta4-rest-skills-and-sidecar-roadmap.md). Depends
on FW1 (the REST kind must be representable). Rechecked at `1e4eb455`;
see [grounding evidence](../beta4-code-grounding.md#fw2--additions-justified-existing-semantics-are-more-specific).

## Goal

Give an external, asynchronous caller (Sidecar is the first) everything it
needs from the closed public API without re-parsing manifests or
re-implementing validation: a read-only catalog of registered skills and a
pre-check that runs input validation and root-skill authorization without
executing.

## In scope

- A read-only, injectable catalog view in `ai.loomspan.api` exposing, per
  registered skill: exact name, description, kind (YAML, Java, REST), and the
  input JSON schema the framework already presents to models.
- `SkillTemplate.validate(String skillName, Map<String, Object> input)` and
  `validate(String skillName, Object input)`: perform the same lookup, input
  normalization, input validation, and root-skill access check that `invoke`
  performs, then return without creating a session or executing.
- Allowlist, README, supported-surface tests for the new types and methods.
- Extend the public observer contract to supply available execution history
  on failure as well as success, supporting Sidecar's `ONERROR` and `ALWAYS`
  diagnostic-history modes without access to framework internals.

## Out of scope

- Any mutable or replacement surface; the catalog is read-only.
- Exposing `rbac_roles`, `allowed_skills`, prompts, model names, manifest
  text, bean names, or resource paths. The view carries only what a caller
  needs to construct a request. (The observability API already exposes
  operator detail under its own key.)
- Pre-checking nested child authorization (an execution-time outcome).
- Cancellation, execution status, or a separate trace-retrieval API.
- Hot reload, catalog replacement/versioning, or a reload API. The initial
  Sidecar release applies skill and route edits by restarting; the existing
  startup catalog lifecycle remains sufficient.

## Current code-grounding notes

- `CapabilityRegistry.getAllCapabilities()` returns every registered
  `CapabilityMetadata` (YAML and Java today; REST after FW1) with `name()`,
  `description()`, `kind()`, and `tool().inputSchema()` — a JSON string that
  is generic `{"type":"object","additionalProperties":true}` when no
  contract is declared, the resolved YAML `input_schema` otherwise, or the
  reflected schema built by `SkillMethodBeanPostProcessor.buildInputSchema`
  for Java skills. This is the schema handed to models via `BoundCapability`.
- Registration completes in `YamlSkillCapabilityRegistrar.completeRegistration()`
  (`SmartInitializingSingleton`); `DefaultRegisteredSkillCatalog` calls it
  explicitly in its constructor before reading the registry. The public
  catalog uses the same eager completion pattern before building its one
  immutable snapshot. Do not add lazy readiness state or first-use loading.
- `DefaultSkillTemplate.invoke(name, Map, observer)` does, in order:
  `requireSkill` (unknown → `SkillException("Unknown skill '<name>'")`),
  `normalizeNullInput` (null input allowed only for generic/empty-permitting
  contracts, else `SkillInputValidationException`), `inputValidator.validate`
  (→ `SkillInputValidationException` with issues), capture `Authentication`
  from `securityContextStrategy`, then `sessionRunner.callWithNewSession`.
  The `Object` overload rejects null unconditionally before conversion;
  otherwise it converts through Jackson, with conversion failure →
  `SkillException`. `AccessDeniedException` and `SkillException` pass
  through unwrapped; any other `RuntimeException` → `SkillException`.
- **The root access check currently happens inside the session**:
  `CapabilityExecutionRouter.execute` calls `accessGuard.checkAccess` (→
  `AccessDeniedException("Access denied for capability '<name>'")`), which
  needs a `LoomspanSession` only as an authentication fallback.
  `SkillRoleEvaluator.canAccess(policy, authentication)` is session-free, so
  `validate` can evaluate `capability.accessPolicy()` against the calling
  thread's `Authentication` without creating a session.

## Binding decisions

### Catalog view

- New allowlisted types (working names; content may not shrink):
  - `SkillCatalog` — `List<SkillDescriptor> skills()` sorted by exact name;
    `Optional<SkillDescriptor> skill(String name)`.
  - `SkillDescriptor` — record `(String name, String description, SkillKind
    kind, String inputSchema)`.
  - `SkillKind` — enum `YAML`, `JAVA`, `REST`. It is a public projection of
    internal `CapabilityKind`; internal kinds never appear in signatures.
- `inputSchema` is exactly `CapabilityMetadata.tool().inputSchema()` — the
  same JSON models receive. No new dialect, no reformatting.
- The catalog is a stable snapshot taken after registration completes; it
  does not filter by caller authorization. Authorization is enforced by
  `validate`/`invoke`.
- Reuse registry metadata and the existing registration authority. Keep
  public and operator projections separate; neither needs refresh, old
  snapshots, or a new catalog lifecycle.
- Injectable like `SkillTemplate`; replacing its bean is unsupported.

### `SkillTemplate.validate`

- `validate(name, Map)` runs `requireSkill`, `normalizeNullInput`, and
  `inputValidator.validate` exactly as `invoke` does, then evaluates
  `capability.accessPolicy()` against the calling thread's `Authentication`
  and throws `AccessDeniedException` with the same message form as
  `DefaultAccessGuard.checkAccess`. Returns `void` on success.
- `validate(name, Object)` converts through the same Jackson path and error
  mapping as `invoke(name, Object)`, including unconditional null rejection.
  The Map overload's conditional null acceptance is intentionally different.
- Exception mapping is identical to `invoke`: unknown skill →
  `SkillException`; bad input → `SkillInputValidationException` with the same
  issues `invoke` would raise; denied → `AccessDeniedException` unwrapped;
  other runtime failures → `SkillException`.
- `validate` creates no session, records no trace, calls no observer,
  consumes no quota, and does not touch `ExecutionBindingScope`.
- `validate` and `invoke` share the lookup, normalization, conversion, and
  input-validation implementation. Reuse the same role-policy evaluator for
  authorization: `validate` calls it without a session, while `invoke`
  retains its in-session access check. Do not add an extra access or
  validation pass within `invoke` merely to reuse `validate`. Sidecar's
  separate pre-check does not replace validation or authorization at execution.

### Observer history on execution failure

- Today the observer receives a `SkillExecutionView` only after successful
  execution. Extend the public contract so an observer also receives the
  available session history when execution fails after session creation.
- `invoke` continues to throw the original execution exception with its
  existing type and error mapping. Delivering diagnostics must not mask or
  replace that failure if the observer itself throws.
- Pre-execution rejection without a session produces no observer callback;
  `validate` remains observer-free. This is completed history, not streaming
  or a separate trace lookup API.
- Use FW1's single root operation to capture the existing session across
  failure at the runner/facade boundary;
  the current success-only `ExecutionResult` cannot deliver a failed session.
  `LoomspanSession.finalizeTrace` retains the projected journal before trace
  cleanup, so no separate retrieval API or history readiness state is needed.
  Map/deliver it after session completion and execution binding restoration,
  before that same root ownership is released, preserving the original failure if mapping
  or callback fails. Preserve current success-observer exception propagation.
  Document the changed public behavior and add supported-surface tests.
- Deliver at most one callback per invocation, after session completion;
  do not retry a successful-execution callback that throws by treating it as
  an execution failure. Keep mapping in the facade and delivery on the caller
  within FW1's root lifetime, with no second root admission or observer
  registry. A narrow internal completion callback may connect the existing
  owners; this is not another public API or execution engine.

## Acceptance criteria

- [ ] `SkillCatalog`, `SkillDescriptor`, `SkillKind` are public in
  `ai.loomspan.api`, allowlisted, README-documented, and expose only JDK types.
- [ ] The catalog lists every registered YAML, Java, and REST skill in name
  order with the correct kind and an `inputSchema` string equal to the
  registered tool descriptor's schema; `skill("missing")` is empty.
- [ ] The catalog is available for injection in an application context and
  reflects registrations completed by `YamlSkillCapabilityRegistrar`.
- [ ] `validate` throws `SkillInputValidationException` with the same issues
  `invoke` produces for the same input, for both Map and Object overloads,
  including the null-input rules.
- [ ] `validate` throws `AccessDeniedException` for a caller lacking a
  required YAML `rbac_roles` role or Java `@RolesAllowed` role, and returns
  for an authorized caller; the message form matches `invoke`.
- [ ] `validate` for an unknown skill throws `SkillException` with the same
  message as `invoke`.
- [ ] `validate` leaves no session, trace artifact, or observer callback
  (asserted through the observability/trace facilities or execution counters
  available in tests).
- [ ] `LoomspanPublicSurfaceArchitectureTest` and the full suite pass.
- [ ] Through public API only, an observer receives available history after
  an execution failure, including preceding events, while `invoke` still
  throws the expected exception. An observer failure does not mask the
  execution failure; rejection before session creation yields no callback.
- [ ] Success and execution-failure paths each call the observer once when
  history can be mapped; mapper failure does not mask the execution failure.
  A success observer's exception propagates unchanged with no second callback.
  Slow mapping/callback and shutdown overlap obey FW1's shared deadline.
- [ ] Observer delivery occurs after execution binding restoration, under
  the same root ownership; the success observer's exception is not wrapped
  by moving delivery inside execution-error mapping.

## Ticket boundary

The external caller API unit in the [planning handoff](../beta4-ticket-readiness.md)
owns the catalog, both pre-check overloads and failure observation together,
with their supported-surface documentation/tests. Reuse FW1's root operation
and extend its shutdown proof; do not introduce an observer tracking path.

## Flagged for the developer (design-lens §1)

- `CapabilityRegistry`'s Javadoc says implementations "must reject metadata
  whose kind is not `YAML_SKILL`", but Java skills register there today and
  REST will too. FW1 owns this correction; FW2 verifies it rather than
  treating it as a second cleanup task.
- `DefaultRegisteredSkillCatalog` treats every non-YAML kind as Java (FW1
  owns the correction). Share kind translation without making the public
  catalog depend on operator DTOs; they intentionally expose different data.
- `CapabilityExecutionRouter.objectiveFor` has an unused argument-map
  parameter. Remove it if editing that shared path; it is internal cleanup.

## Pipeline notes for the tickets

- Growing the public allowlist is intentional.
- Adding methods to `SkillTemplate` is an intentional, beta-permitted change
  to a supported API type; no compatibility shim.
- Performing the root access check before session creation in `validate`
  is intentional; `invoke` keeps its existing in-session check.
