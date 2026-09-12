# PR 5.3 — Expose catalog, pre-checks and failure history to external callers

## Outcome

External asynchronous hosts can discover skill inputs, reject invalid or
unauthorized root requests before dispatch, and obtain available completed
history on execution failure using only `ai.loomspan.api`. Sidecar is the first
consumer; it must not parse manifests, duplicate validation or reach into
framework internals.

## Requirements — binding

- Add an injectable read-only public catalog with the following content and
  operations (type names are the phase's working names): `SkillCatalog` has
  `List<SkillDescriptor> skills()` sorted by exact name and
  `Optional<SkillDescriptor> skill(String name)`; `SkillDescriptor` is a record
  of `String name`, `String description`, `SkillKind kind`, `String inputSchema`;
  `SkillKind` has `YAML`, `JAVA`, `REST`. Keep signatures JDK-only and add all
  three types to the closed architecture allowlist, README and public proof.
- Build one eager immutable snapshot after existing registration completion,
  reusing the diagnostic catalog's eager-completion pattern and registry
  metadata. Expose every registered skill without caller filtering; lookup of
  a missing name returns empty. `inputSchema` is exactly the registered tool
  descriptor's JSON string as shown to models, without reformatting/new dialect
  (generic object without a schema, resolved YAML schema, reflected Java schema).
  No lazy readiness, refresh, retained snapshots or replacement contract.
- Public catalog contains only name, description, kind and input schema.
  No roles, allowed children, prompts, model names, manifest text, paths or bean
  names. Operator projection remains separately protected; share kind/metadata
  translation without making public API depend on observability DTOs.
- Add `void SkillTemplate.validate(String skillName, Map<String,Object> input)`
  and `void validate(String skillName, Object input)`. Map behavior shares
  `invoke`'s lookup, null normalization and input validation, in that order,
  then evaluates root access policy with the calling thread's authentication
  using the existing role evaluator. Object behavior shares invocation's
  Jackson conversion/error path and unconditional null rejection before
  conversion. Map null remains accepted only for generic/empty-permitting
  contracts. Do not unify these intentionally different null rules.
- Preserve invocation-equivalent error mapping: unknown skill is
  `SkillException("Unknown skill '<name>'")`; bad input is
  `SkillInputValidationException` with matching issues; denied access is
  unwrapped `AccessDeniedException("Access denied for capability '<name>'")`;
  other runtime/conversion failures become `SkillException` under the same
  rules as invocation. Existing `SkillException` passes through.
- Pre-check creates no session/trace, invokes no skill/model/observer, consumes
  no quota and does not touch `ExecutionBindingScope`. It checks root access
  only. Share input preparation and role evaluation, but keep execution-time
  input/access checks in `invoke` and nested authorization at execution. Do not
  add another invoke pass merely by calling public `validate` internally.
  Successful validation reserves no admission and does not prevent later
  execution denial or shutdown rejection; no prepared-request token API.
- Extend observation from success to available history when execution fails
  after session creation. Capture the existing session across failure through
  PR 5.1's one root operation; finalize history, restore execution binding, map
  in the facade and deliver on the caller before releasing the same ownership.
  Use the retained finalized journal, independently of Console storage or trace
  persistence; no new lookup/history readiness state or observer registry.
- Deliver at most one callback per invocation, once on success/failure when
  history can be mapped. Rejection before session creation and all pre-checks
  produce none. This is completed available history, not guaranteed history
  after mapping/finalization failure, streaming or a separate retrieval API.
- Preserve the original execution exception and its existing facade mapping if
  history mapping or a failure observer also fails. A successful-execution
  observer exception propagates unchanged, is not wrapped as execution failure,
  and never triggers a second callback. Slow mapping/observation stays under
  the same root ownership and shutdown deadline without running on the waiter.
- Add tests and README/API documentation with the feature, including changed
  observer behavior, injection, catalog scope, both overloads and reservation
  limits. Extend existing facade/lifecycle and supported-surface fixtures; no
  duplicate end-to-end harness. Remove the unused router `objectiveFor`
  argument and redundant step-loop exception condition if editing those paths;
  do not commission separate cleanup or redo PR 5.2's registry correction.

## Acceptance criteria

- [ ] An application injects the eager catalog after registration and sees all
  YAML/Java/REST descriptors sorted by exact name with correct description/kind
  and byte-for-byte registered schema strings; missing lookup is empty.
  Snapshot/list state is immutable, unfiltered and contains no operator-only
  fields. There is no new refresh/replacement lifecycle.
- [ ] Both validate overloads match invoke's unknown/input/conversion errors,
  messages and validation issues, including conditional Map-null acceptance
  and unconditional Object-null rejection. Success returns void.
- [ ] Authorized callers pass and unauthorized callers get matching unwrapped
  access denial for YAML/REST roles and Java `@RolesAllowed`, using the calling
  authentication and existing policy evaluator. Nested access remains an
  execution outcome; invoke still revalidates and checks access at execution.
- [ ] Pre-checks leave no session, trace artifact, observer callback, execution,
  quota consumption or execution-binding change. A caller that validated before
  shutdown is rejected at root admission afterward without a new session.
- [ ] Public observers receive preceding available history after execution
  failure while invoke throws its expected original exception. Pre-session
  rejection has no callback; mapper/failure-observer exceptions never replace
  execution failure. Trace-persistence choice does not introduce a separate
  history retrieval dependency.
- [ ] Success and failure callbacks occur once when mappable, after session
  completion and binding restoration on the caller. A success observer's
  exception propagates unchanged without wrapping/retry/second callback.
  Mapping failure and slow mapping/callback during shutdown preserve one
  root ownership/release and PR 5.1's shared deadline/resource guarantees.
- [ ] The three public catalog types, both methods and observer behavior are
  documented and tested through the supported surface. The existing local
  model/YAML/REST/Java fixture includes catalog, validate and failure-history
  proof. `LoomspanPublicSurfaceArchitectureTest` and the full suite pass, with
  no internal/autoconfigure types exposed or unintended SPI/bean overrides.

## Context, dependencies and exclusions

Depends on [PR 5.2](loomspan-pr-5.2-rest-skills-and-console.md), which depends on
[PR 5.1](loomspan-pr-5.1-framework-lifecycle.md). Owns all eleven FW2 criteria
and the new pre-check/shutdown and failure-observation extensions to the root
lifetime proof. PR 5.4 verifies cumulative coverage; it is not a hardening
dependency. Sidecar implements its own authenticated queue/store and public
view projection later, including `NEVER`, `ONERROR`, `ALWAYS` diagnostics;
this ticket supplies history without implementing those host policies.

Current requirements: [roadmap](../phases/beta4-rest-skills-and-sidecar-roadmap.md)
and [FW2](../phases/phase-fw2.md); ownership: [readiness](../beta4-ticket-readiness.md).
The [lens](../framework-feature-design-lens.md), [review](../beta4-design-review.md)
and [grounding](../beta4-code-grounding.md) provide rationale/evidence. Earlier
test runs establish existing behavior only. Exact private helper signatures
and fixture placement are pipeline choices; a narrow internal completion
callback is a suggestion, not a new public contract.

No HTTP API, cancellation/status API, trace retrieval, reload, mutable catalog,
authorization-filtered discovery, streaming, generalized context or normalized
request DTO. No new sanitization or cross-version diagnostic compatibility.
Internal/autoconfigure signatures are implementation detail; no preservation
shims or unsupported internal-bean replacement surface.

## Pipeline notes

- Adding methods to supported `SkillTemplate` is an intentional pre-1.0
  source/binary compatibility-sensitive change with no compatibility shim.
  Update affected in-repository implementations, consumers, tests and guidance
  atomically and document the impact separately from additive catalog types.
- Failure-observer callbacks are an intentional public behavioral expansion.
  Preserve success-observer exception behavior and original execution-failure
  precedence; no compatibility mode for success-only callbacks.
- Session-free root access in `validate` is intentional. Invocation retains
  its in-session access checks and the public allowlist grows only by the
  three named catalog types in this unit.
