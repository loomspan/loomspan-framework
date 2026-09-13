# PR 5.3 External Caller API Implementation Plan

## Overview

Add the closed, supported Java API that an asynchronous external host needs to discover registered skills, validate and authorize a root request before dispatch, and receive completed diagnostic history when an invoked skill fails. The implementation will project one eager immutable catalog from the existing registry, add session-free `SkillTemplate.validate` overloads without weakening execution-time checks, and extend PR 5.1's single admitted-root completion boundary so failure history is mapped and delivered before root release while the original execution failure remains primary.

The current ticket is the binding unit. The beta 4 roadmap, FW2 phase, and future PR 5.4 ticket confirm that Sidecar consumption, broader beta-readiness documentation, release gating, and host-level `NEVER`/`ONERROR`/`ALWAYS` storage policy remain future work.

## Current State Analysis

- `SkillTemplate` exposes four invocation overloads and documents successful observation only; it has no pre-check operation (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:6-18`).
- `CapabilityMetadata` already owns the exact registered name, description, internal kind, access policy, input contract, and `CapabilityToolDescriptor`; the descriptor's schema string is validated but not reformatted (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityMetadata.java:10-38`; `CapabilityToolDescriptor.java:5-21`).
- `DefaultRegisteredSkillCatalog` establishes the eager registration-completion, exact-name `TreeMap`, duplicate rejection, and immutable snapshot pattern, but its operator entries deliberately contain paths, manifest text, bean names, and methods (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:19-78`).
- `DefaultSkillTemplate` currently performs exact lookup, Map-null normalization, input validation, and exception mapping before opening a session. Its Object overload rejects null and converts with the application Jackson mapper before delegating to the Map path (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:60-143`, `:153-191`).
- Root authorization remains inside `CapabilityExecutionRouter`, while its underlying `SkillRoleEvaluator` needs only a policy and caller authentication. Auto-configuration currently constructs that evaluator inline for `DefaultAccessGuard` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:39-68`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/SkillRoleEvaluator.java:9-28`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:205-213`).
- `LoomspanSessionRunner` finalizes the session under `ExecutionBindingScope`, restores the binding, invokes a success-only completion function, then releases the admitted root. An action failure skips the completion function even though a finalized journal may be retained (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:216-260`; `LoomspanSession.java:690-785`).
- The closed allowlist and root README currently enumerate ten supported types. `ApplicationApiValueTest`, `DefaultSkillTemplateTest`, `FrameworkExecutionLifecycleTest`, and `SupportedSurfaceIntegrationTest` provide the existing focused and cumulative proof to extend (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:27-39`, `:280-379`; `README.md:139-170`).

## Desired End State

After implementation:

- applications can inject `SkillCatalog` and obtain one unfiltered, immutable, exact-name-sorted snapshot of all YAML, Java, and REST registrations, with byte-for-byte registered input schemas and no operator-only or authorization fields;
- `SkillTemplate.validate` mirrors each corresponding invocation overload's lookup, conversion, null, input-validation, authorization, and facade-error behavior, but does not admit a root or create execution state and does not replace execution-time checks;
- an invocation observer receives at most one mappable completed view after success or a post-session execution failure, on the caller after binding restoration and while the same admitted root is owned;
- execution failure remains the primary throwable if mapping or failure observation also fails, while successful-observer exceptions retain their existing unwrapped behavior;
- the public allowlist, Spring wiring, supported-surface fixture, README, and version-aligned Java API knowledge set describe and prove the new contract without introducing another SPI or replacement surface.

### Key Discoveries

- `CapabilityMetadata.tool().inputSchema()` is the exact string consumed by model tool binding, so the public catalog must copy this value directly rather than serialize `inputContract` again (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java:21-29`).
- Registration is already converged and idempotently completed by `YamlSkillCapabilityRegistrar.completeRegistration()`; both the existing operator catalog and the new public catalog can safely force that authority before snapshot construction (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:35-66`; `DefaultRegisteredSkillCatalog.java:23-31`).
- Finalized journals are retained independently of Console persistence only when projection/finalization succeeds. This supplies “available,” not guaranteed, failure history without a new store or retrieval lifecycle (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:491-510`, `:713-743`).
- The existing lifecycle test already proves successful completion after binding restoration and before root release; the same runner boundary should own the failure callback rather than adding observer tracking (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:99-118`).
- Roadmap/FW2/PR 5.4 sequencing confirms that this PR owns feature-level public docs and tests, while PR 5.4 later completes cumulative beta guidance and release readiness (`ai/thoughts/phases/phase-fw2.md`; `ai/thoughts/tickets/loomspan-pr-5.4-framework-documentation-and-readiness.md`).

## What We're NOT Doing

- No Sidecar HTTP, queue, status, cancellation, authentication-store, history-retention, or diagnostics-selection implementation.
- No catalog refresh, readiness state, retained old snapshots, mutable catalog, authorization-filtered discovery, or public prepared-request token.
- No role, allowed-child, prompt, model, manifest text/path, bean, method, or operator DTO fields in the public catalog.
- No removal of invocation-time validation/access enforcement and no pre-check of nested children.
- No observer registry, streaming history, trace lookup API, Console-storage dependency, new persistence, sanitization, or historical/cross-version diagnostic promise.
- No supported bean replacement contract or SPI beyond the existing `RestSkillHandler`.
- No `StepLoopMissionExecutionEngine` cleanup. The unused `CapabilityExecutionRouter.objectiveFor` argument also remains unchanged because the feature does not require editing that execution path; neither cleanup warrants unrelated churn.
- No PR 5.4 release-readiness work, tagging, publishing, or Sidecar integration gate.

## Skill-Authoring Documentation Impact

**Impact**: No impact

- **Rationale**: The change affects application-facing discovery, root pre-dispatch validation, and invocation observation. It does not change manifest syntax, input-schema semantics, role declarations, planning/execution semantics visible to skill authors, or any skill-authoring default or limit.
- **Documents to update**: None under `agent-skills/loomspan-docs/references/skill-authoring/`. The separate application-developer `java-api` knowledge set is affected and is included in Phase 4.
- **Supporting evidence**: Catalog values are projected from existing `CapabilityMetadata` and tool descriptors; validation reuses existing input contracts and role policies; execution keeps the current router checks (`CapabilityMetadata.java:10-38`; `DefaultSkillTemplate.java:92-129`; `CapabilityExecutionRouter.java:39-68`).
- **Coverage table update**: Not required for the skill-authoring README because no authoring topic changes coverage. The Java API knowledge-set coverage and routing must be updated separately.
- **LLM-first usability**: Not applicable to skill-authoring. Java API routing will gain a focused catalog/pre-check route and observation guidance rather than duplicating the same contract across unrelated topics.

Documentation comparison used the installed `loomspan-docs` skill only as a router because its metadata is `0.1.0-SNAPSHOT`, while the checkout and repository-bundled skill are `1.0.0-beta.4-SNAPSHOT`. Exact claims use the version-aligned repository-bundled Java API documents and executable source/tests. **Drift classification: aligned** for the pre-change revision: the current ten-type/four-overload/success-only prose matches current code, and this ticket intentionally changes that future state.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Add `SkillCatalog`, `SkillDescriptor`, and `SkillKind`; add two abstract methods to supported `SkillTemplate`; expand observer behavior to post-session execution failures. The ticket, closed allowlist, README, and public tests establish this as protected API. | Add the three named types atomically. Apply the ticket-authorized pre-1.0 source/binary-sensitive `SkillTemplate` change and behavioral expansion with no legacy mode; preserve all existing invocation signatures and success semantics. |
| Supported SPI | `RestSkillHandler`/`RestSkillInvocation` remain the sole SPI and are not changed. | Preserve. Catalog injection and internal Spring beans do not create replacement contracts. |
| Configuration and manifest contracts | No key, YAML syntax, schema dialect, role declaration, default, or reload behavior changes. Existing resolved schemas and policies are read. | Preserve exactly; add no aliases or migration path. |
| Persisted or serialized contracts | No durable store, request token, catalog wire format, or historical trace format is added. | No impact. Keep catalog and validation in-process and current-snapshot only. |
| Ephemeral diagnostic formats | `SkillExecutionView`/`SkillExecutionEvent` shapes are unchanged; availability expands to mappable post-session failures. | Keep current writer/finalizer/journal/mapper coherent, retain ordering and sensitive-data warnings, and do not promise history when finalization or mapping fails. |
| Internal or accidentally exposed implementation | Registry, registrar, internal kind, role evaluator/access guard, facade implementation, runner completion signature, session, mapper, and auto-configuration wiring change. | Update atomically without compatibility overloads or shims; these types remain classified internal/autoconfigure implementation. |

- **Evidence of supported contracts**: `AGENTS.md`, `LoomspanPublicSurfaceArchitectureTest`, root README, the repository-bundled Java API knowledge set, ticket requirements, and `SupportedSurfaceIntegrationTest` establish the application API. The same sources state that internal/autoconfigure types are not supported extension contracts.
- **Intentional compatibility changes**: adding abstract `SkillTemplate.validate` methods is source/binary sensitive; failure observers are an intentional behavioral expansion. Both are explicitly authorized by the ticket's `Pipeline notes`; no compatibility mode is wanted.
- **In-repository consumers to update**: `DefaultSkillTemplate`, all direct constructor users/tests, Spring auto-configuration, architecture/value/facade/lifecycle/auto-configuration/supported-surface tests, root README, and repository-bundled Java API references.
- **Public-surface delta**: add public `SkillCatalog`, `SkillDescriptor`, `SkillKind`, and the two `void SkillTemplate.validate(...)` signatures. Do not add/remove constructors or Spring extension points in supported API, and do not add another supported SPI.
- **Shim decision**: **No shim.** The ticket expressly authorizes the beta interface and observer changes; in-repository consumers will move atomically, and preserving success-only observation or an old `SkillTemplate` shape would create two contracts.
- **Java-to-Go boundary coordination**: **Not required.** No application-adapter REST/SSE, acquisition, problem, consumed NDJSON, or Console DTO boundary changes; the public observer maps the in-process retained journal.
- **Pipeline notes alignment**: **Aligned.** Planned changes are exactly the three named additive types, two no-shim interface methods, session-free root pre-check, and failure-observer expansion described by the notes. No broader break is introduced.

## Implementation Approach

Implement three cohesive changes over existing authorities rather than adding parallel concepts:

1. Project the public catalog once from completed registry metadata. Add the public enum translation as a single exhaustive mapping on internal `CapabilityKind`, and reuse it in both public and operator projections while keeping their DTOs and data scopes separate.
2. Refactor `DefaultSkillTemplate` into shared Object conversion and Map preparation helpers. Both `invoke` and `validate` use them, but only `validate` performs session-free pre-dispatch access and only `invoke` enters the runner/router, preserving the deliberate double enforcement across dispatch time and execution time.
3. Replace the runner's internal success-only completion shape with one failure-aware completion callback invoked after session completion/binding restoration and before admitted-root release. On success, preserve current completion-exception propagation. On execution failure, suppress mapping/callback failures onto the primary failure (guarding against self-suppression) and rethrow that original failure for the facade's unchanged mapping.
4. Update focused tests, the existing cumulative integration fixture, closed-surface proof, and application-developer documentation in the same change.

## Phase 1: Add the Eager Public Catalog

### Overview

Define the deliberately supported JDK-only catalog contract and one internal immutable snapshot implementation over the completed capability registry.

### Changes Required

#### 1. Public catalog value and service types
**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillCatalog.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillDescriptor.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillKind.java`

**Changes**:

- Add `SkillCatalog.skills()` returning `List<SkillDescriptor>` and exact-name `skill(String)` returning `Optional<SkillDescriptor>`.
- Add the exact four-component `SkillDescriptor(name, description, kind, inputSchema)` record and validate required values consistently with existing public records/registered metadata.
- Add exactly `YAML`, `JAVA`, and `REST` enum constants. Keep every public signature limited to JDK and allowlisted API types.
- Document immutability, exact-name lookup/order, unfiltered discovery, snapshot lifecycle, and the fact that validation/invocation—not discovery—enforces authorization.

#### 2. One internal kind mapping and separate projections
**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityKind.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillCatalog.java`

**Changes**:

- Add an exhaustive internal-to-public kind conversion on `CapabilityKind`; use the public enum name for the operator kind label so YAML/REST/Java mapping has one authority without making public API depend on operator DTOs.
- Implement `DefaultSkillCatalog` by calling `YamlSkillCapabilityRegistrar.completeRegistration()`, iterating every `CapabilityMetadata`, rejecting duplicate exact names, and building a natural-order immutable map/list snapshot.
- Copy `metadata.name()`, `description()`, translated kind, and `metadata.tool().inputSchema()` directly. Do not inspect roles or `YamlSkillCatalog`, reserialize schemas, or retain registry/registrar readiness state after construction.
- Keep `DefaultRegisteredSkillCatalog`'s source-specific mapping intact except for using the shared kind conversion; do not merge catalog interfaces or entry records.

#### 3. Spring-owned injection
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`

**Changes**:

- Register exactly one infrastructure `SkillCatalog` bean backed by `DefaultSkillCatalog` and dependent on the registry/registrar.
- Do not use `@ConditionalOnMissingBean` or expose the implementation as a supported replacement point.

### Success Criteria

#### Automated Verification

- [x] Catalog value/implementation tests prove the exact enum/record/interface shape, exact-name sorting and lookup, immutable list state, all three kinds, unfiltered restricted entries, duplicate rejection, and byte-for-byte schema preservation.
- [x] Auto-configuration and supported-surface tests prove one injectable `SkillCatalog` after YAML/Java/REST registration.
- [x] `LoomspanPublicSurfaceArchitectureTest` recognizes only the three ticketed additions and finds no leaked internal/autoconfigure signature type.

---

## Phase 2: Add Session-Free Root Validation

### Overview

Expose both validation overloads by sharing invocation's preparation and role-policy logic while retaining all execution-time enforcement and avoiding session/admission state.

### Changes Required

#### 1. Supported facade signatures and shared input preparation
**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java`

**Changes**:

- Add `void validate(String, Map<String,Object>)` and `void validate(String, Object)` with Javadoc that distinguishes advisory pre-dispatch validation from execution admission.
- Extract shared helpers/records for Object-to-Map conversion and Map preparation so lookup, Map-null normalization, validation issue conversion/message construction, and runtime wrapping cannot drift between validate/invoke.
- Preserve ordering: Object null rejection before conversion and conversion before lookup; Map exact lookup before null normalization and contract validation; access after successful validation.
- Keep Map null conditionally accepted for generic/empty-permitting contracts and Object null unconditionally rejected.
- `validate` captures the calling thread's authentication only after successful preparation, checks the root policy, and returns `void`. It never calls `LoomspanSessionRunner`, `CapabilityExecutionRouter`, an observer, or a capability invoker.
- `invoke` reuses preparation but does not call public `validate`; it still opens one root session and relies on the router for independent execution-time input/access enforcement and nested authorization.

#### 2. Shared role evaluator and exact denial behavior
**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/SkillRoleEvaluator.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/DefaultAccessGuard.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java`

**Changes**:

- Add a narrow evaluator operation that throws `AccessDeniedException("Access denied for capability '<name>'")` from the same policy decision used by `canAccess`.
- Promote one framework-owned, unconditional internal `SkillRoleEvaluator` bean configured with `GrantedAuthorityDefaults`/`RoleHierarchy`; inject that exact instance into both `DefaultAccessGuard` and `DefaultSkillTemplate`.
- Replace the internal facade constructor signature atomically and update all in-repository callers; do not retain an overload as an unsupported internal compatibility shim.
- Preserve `DefaultAccessGuard`'s explicit invocation-authentication precedence and session fallback, but delegate the final policy/message decision to the shared evaluator.

### Success Criteria

#### Automated Verification

- [x] Focused facade tests compare validate/invoke unknown-name, Object conversion, Map/Object null, validation message/issues, runtime wrapping, and access-denial behavior.
- [x] YAML/REST manifest roles and Java `@RolesAllowed` pass/fail with the calling authentication, custom role prefixes, and hierarchies through the existing evaluator semantics.
- [x] Tests prove validation leaves the current `ExecutionBindingScope` unchanged and creates no observation/session/root, execution, quota, trace, or callback side effect.
- [x] A shutdown integration test proves prior successful validation reserves nothing: closing admission causes the later invoke to fail before session construction.

---

## Phase 3: Deliver Available Failure History Within One Root Lifetime

### Overview

Extend the existing runner/facade completion boundary so a finalized failed session can reach the existing public mapper/observer without changing result/error types or creating a second ownership path.

### Changes Required

#### 1. Failure-aware runner completion
**File**: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java`

**Changes**:

- Replace the internal success-only `BiFunction` completion overload with a narrow nested failure-aware completion function carrying the nullable action result, completed session, and nullable execution/finalization failure. Update the runner's own simple overloads and all in-repository uses atomically; do not preserve an internal compatibility overload.
- Keep admission and session construction unchanged. Complete/finalize inside the bound action, then invoke the completion function exactly once only after `ExecutionBindingScope.supplyWith` has restored the caller binding and before `AdmittedRoot.close()`.
- On success, return the completion result; wrap only completion `RuntimeException` in the existing `CompletionPhaseFailure` so the facade continues to propagate a successful observer exception unchanged. Let `Error` retain its current fatal behavior.
- On action/finalization failure, invoke completion for available-history mapping, ignore its return, suppress any distinct mapping/callback `RuntimeException` or `Error` on the original failure, and rethrow the original. Guard against adding a throwable to itself.
- Preserve `completeSession`'s existing failure-recording/finalization precedence, trace retention policy, and observation close behavior.

#### 2. Facade mapping and observer delivery
**Files**:

- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java`
- `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java`

**Changes**:

- Use the failure-aware completion callback to map the retained journal and call the optional observer for both success and failure. Do not query `FinalizedTraceCatalog` or check persistence policy.
- Simplify the internal action result to only the returned text; the runner already supplies the session to completion.
- Add a package-private mapper-injection construction seam for deterministic failure tests while keeping the auto-configured constructor responsible for the normal mapper. This is internal testability, not a bean or SPI.
- If no finalized/mappable journal exists, mapping fails and no observer is called; on a failed invocation that mapping failure is suppressed behind the original execution failure. Never retry an observer.

#### 3. Lifecycle and failure precedence proof
**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`

**Changes**:

- Replace the obsolete “execution failure has no observer” expectation with public-view delivery containing preceding/terminal events while retaining the same outward exception mapping.
- Cover callback cardinality, caller thread, binding restoration, success observer exception behavior, execution-failure precedence over mapper/observer failure, finalization-without-history behavior, and all persistence policies.
- Extend the root-lifecycle proof to both success and failure completion, including a blocked callback overlapping shutdown and release exactly once within the shared deadline.
- Preserve no-callback tests for unknown skill, invalid input, security-context lookup failure, validate denial, and shutdown rejection before session construction.

### Success Criteria

#### Automated Verification

- [x] Success and mappable execution-failure paths call the observer exactly once after binding restoration on the caller and before the same root is released.
- [x] Failure views contain preceding events in journal order and are available under `NEVER`, `ONERROR`, and `ALWAYS` without Console storage.
- [x] Mapper or failure-observer exceptions never replace the original execution throwable or its existing public facade mapping; unavailable finalized history yields no callback.
- [x] Successful-observer exceptions remain unwrapped, cause no retry, and release the root once.
- [x] Shutdown waits for slow success/failure completion within the existing shared root deadline and uses no waiter-thread callback or second root.

---

## Phase 4: Close the Supported Surface and Documentation

### Overview

Make the new API deliberate, publicly testable, and version-aligned in the current ticket while leaving PR 5.4's cumulative readiness/release gate intact.

### Changes Required

#### 1. Closed API and value-shape tests
**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`
- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`

**Changes**:

- Add only the three new public top-level types to the allowlist, update stale count/test naming, and retain recursive signature safety checks.
- Assert exact `SkillDescriptor` components, `SkillKind` constants, `SkillCatalog` methods, immutable catalog results, and both exact `SkillTemplate.validate` signatures.
- Confirm no internal/autoconfigure types, operator DTOs, accidental SPI package, or replacement interfaces escape through public signatures.

#### 2. Existing application integration fixture
**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java`
- `loomspan-spring-boot-starter/src/test/resources/skills/integration/supported-surface-skill.yml`
- `loomspan-spring-boot-starter/src/test/resources/skills/integration/supported-rest-leaf.yml`

**Changes**:

- Extend, rather than duplicate, the local OpenAI-compatible YAML/Java/REST fixture to inject `SkillCatalog`, validate Map and Object input, prove catalog entries/schemas/kinds/order, and prove authorized/denied pre-checks without handler/model dispatch.
- Apply a Java role declaration compatible with the fixture's authorized caller so both Java annotation and manifest policy paths are exercised.
- Add a deterministic application-owned failing leaf/root scenario and observe only `SkillExecutionView`; assert failure history arrives while the expected facade exception remains primary and pre-session rejection yields none.
- Keep host storage/diagnostics policy and Sidecar HTTP/JWT/queue/container proof for the future Sidecar repository and PR 5.4 integration gate.

#### 3. Auto-configuration and application-developer guidance
**Files**:

- `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`
- `README.md`
- `agent-skills/loomspan-docs/references/java-api/README.md`
- `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md`
- `agent-skills/loomspan-docs/references/java-api/invocation.md`
- `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`
- `agent-skills/loomspan-docs/references/java-api/catalog-and-validation.md`

**Changes**:

- Prove single public catalog/facade beans and shared internal evaluator wiring without asserting a replacement surface.
- Update the root README's supported type list and application example/guidance for catalog injection, both pre-check overloads, no-reservation semantics, execution-time recheck, and success/failure observation/precedence.
- Add a focused Java API catalog/pre-check topic, route it from the knowledge-set README, and update the coverage table and compatibility list from ten to thirteen types.
- Update invocation and observation/error topics to distinguish pre-session rejection from post-session failure history, document at-most-once caller-thread delivery, available-history limits, unchanged successful-observer exceptions, and sensitive current-version diagnostic boundaries.
- Keep guidance explicit that the catalog is unfiltered/read-only/nonreplaceable, validation is advisory and root-only, and neither feature exposes internal machinery.

### Success Criteria

#### Automated Verification

- [x] Focused API, catalog, facade, role, runner, lifecycle, auto-configuration, architecture, and supported-surface tests pass.
- [x] `mvn clean verify` passes the complete reactor with no failures, errors, or skipped required checks.
- [x] The allowlist contains exactly thirteen supported types and all public signatures remain JDK/public-API-only.
- [x] README and repository-bundled Java API guidance match executable semantics and retain version `1.0.0-beta.4-SNAPSHOT`; skill-authoring documents remain unchanged.

## Testing Strategy

### Unit Tests

- Public enum/record/interface shape, immutability, exact schema preservation, sorting, lookup, unfiltered scope, and eager registration.
- Validate/invoke parity for lookup, conversion, null handling, structured issues, caller authentication, role prefix/hierarchy, exact denial, and exception mapping.
- Session-free/no-side-effect validation and independent execution-time revalidation/access.
- Failure-aware runner completion, finalization/mapping availability, callback cardinality, and exception precedence.

### Integration Tests

- Spring bean injection and shared role evaluator wiring.
- Root lifetime across slow success/failure completion and post-validation shutdown rejection before session construction.
- Existing public-only YAML planner + Java + REST fixture extended for catalog, both pre-checks, and failure history.

Full case names, fixtures, mocks, and commands are specified in `ai/thoughts/plans/2026-09-12-loomspan-pr-5.3-external-caller-api-testing.md`.

## Performance Considerations

- Snapshot construction is one startup-time traversal plus exact-name sorting; calls are immutable in-memory list/map reads with no registry or manifest reparsing.
- Validation performs the same conversion/contract work as invocation plus one role evaluation, but intentionally does not reserve capacity. A later invoke repeats validation/access because dispatch-time success cannot guarantee execution-time admission or policy state.
- Failure view mapping adds work only when an observer is supplied and a completed journal is available. It stays synchronous on the caller and inside the existing root shutdown budget, so documentation must warn that slow observers consume that budget.
- No new background executor, cache refresh, history store, or unbounded retained state is introduced.

## Migration Notes

- Application implementations/mocks of `SkillTemplate` must implement the two new abstract `validate` methods and recompile. This is an intentional beta source/binary-sensitive change; there is no default-method shim.
- Applications that pass an observer will now receive a callback for mappable post-session failures. They should make callbacks idempotent and expect sensitive current-version diagnostic data, but no success-only compatibility mode is provided.
- Catalog injection is additive and read-only. It does not authorize callers and does not grant a supported way to replace framework registration or catalog beans.
- Internal runner/facade/role-evaluator constructor and callback changes are updated atomically with no migration shim because they are not supported contracts.

## References

- Original ticket: `ai/thoughts/tickets/loomspan-pr-5.3-external-caller-api.md`
- Related research: `ai/thoughts/research/2026-09-12-loomspan-pr-5.3-external-caller-api.md`
- FW2 phase: `ai/thoughts/phases/phase-fw2.md`
- Beta 4 roadmap: `ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md`
- Future cumulative readiness unit: `ai/thoughts/tickets/loomspan-pr-5.4-framework-documentation-and-readiness.md`
- Existing catalog pattern: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:23-78`
- Existing facade preparation: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:73-143`
- Existing root completion: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:216-260`
