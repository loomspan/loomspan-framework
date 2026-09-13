---
date: 2026-09-12T19:05:53-07:00
researcher: Codex (GPT-5)
git_commit: 44a2f75b18f0a4dd811c3769d0252e0605e48baa
branch: main
repository: loomspan-framework
topic: "PR 5.3 — Expose catalog, pre-checks and failure history to external callers"
tags: [research, codebase, public-api, catalog, validation, authorization, observation, lifecycle]
status: complete
last_updated: 2026-09-12
last_updated_by: Codex (GPT-5)
---

# Research: PR 5.3 — Expose catalog, pre-checks and failure history to external callers

**Date**: 2026-09-12 19:05:53 PDT
**Researcher**: Codex (GPT-5)
**Git Commit**: `44a2f75b18f0a4dd811c3769d0252e0605e48baa`
**Branch**: `main`
**Repository**: `loomspan-framework`

## Research Question

Document the current codebase surfaces and execution flow that PR 5.3 must extend so external callers can inject a public skill catalog, run session-free root request validation and authorization, and receive available completed execution history when an invocation fails. This is pipeline Step 1 research for `ai/thoughts/tickets/loomspan-pr-5.3-external-caller-api.md`; no implementation changes are included.

## Summary

The current registry already contains every datum required by the requested public snapshot: `CapabilityMetadata` holds the exact registered name, description, internal kind, access policy, input contract, and `CapabilityToolDescriptor`, whose `inputSchema` string is also passed to model tool definitions. Java discovery and YAML/REST registration converge in `YamlSkillCapabilityRegistrar.completeRegistration()`. The existing operator catalog demonstrates the eager-completion and exact-name-sorted immutable-snapshot pattern, but its entries deliberately include source-specific operator fields and are not a public application contract.

`DefaultSkillTemplate` currently performs exact lookup, Map-null normalization, and input validation before it captures authentication or enters `LoomspanSessionRunner`. The Object overload rejects null and performs Jackson conversion before delegating to the Map path. Root authorization is currently evaluated later, inside the session, by `CapabilityExecutionRouter` through `DefaultAccessGuard`; the underlying `SkillRoleEvaluator` is already session-free and implements the same prefix/hierarchy semantics used for YAML roles and Java JSR-250 policy. Auto-configuration currently creates the evaluator inline inside the access-guard bean rather than exposing it as a separate bean.

PR 5.1 already established one root ownership interval in `LoomspanSessionRunner`: admission occurs before session construction, execution/finalization occurs under an `ExecutionBindingScope`, the successful completion callback runs after binding restoration, and root ownership is released afterward in `finally`. That completion callback currently runs only after success. On execution failure, the session is nevertheless finalized and retains a finalized journal when projection succeeds, but the original exception exits without presenting the session to the facade. This is the precise current gap for failure-history delivery.

The checkout contains no prior research or plan artifacts under `ai/thoughts/research/` or `ai/thoughts/plans/`. The roadmap, FW2 phase, readiness handoff, design review, grounding report, and PR 5.1/5.2 tickets supply historical intent; live source at `44a2f75` is the executable evidence used here.

## Detailed Findings

### 1. Closed public surface and current consumer contract

- `SkillTemplate` is a supported Application API interface with four `invoke` overloads and no validation-only methods today (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillTemplate.java:10-18`). Its class-level documentation currently promises completed views only after successful execution (`SkillTemplate.java:6-9`).
- The architecture allowlist currently contains ten public top-level `ai.loomspan.api` types, including `SkillTemplate`, `RestSkillHandler`, and `RestSkillInvocation` (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29-39`). It verifies that the API package contains exactly the allowlisted set and recursively rejects internal/autoconfigure types in public signatures (`LoomspanPublicSurfaceArchitectureTest.java:280-306`, `:335-379`).
- The root README names the same ten types, identifies `RestSkillHandler` as the sole supported SPI, and states that internal and autoconfigure types are not application extension API (`README.md:163-170`).
- `SkillExecutionView` contains only a session ID and immutable event list (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillExecutionView.java:6-14`). `SkillExecutionEvent` remains the current-version diagnostic value; there is no public session, trace handle, or retrieval API.
- `ApplicationApiValueTest` is the focused public-value proof for record shapes and immutability (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:14-113`). `SupportedSurfaceIntegrationTest` is the existing public-only integration harness: it obtains one `SkillTemplate`, invokes a YAML planner that calls Java and REST leaves, directly invokes REST, and observes a successful execution (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:36-144`).
- The new `SkillCatalog`, `SkillDescriptor`, and `SkillKind`, and the two `SkillTemplate.validate` methods, therefore represent a deliberate Application API expansion. The ticket explicitly classifies the new catalog types as additive and the interface-method additions as an intentional pre-1.0 compatibility-sensitive change without a shim.

### 2. Registry metadata already owns catalog facts

- `CapabilityMetadata` is the internal record that owns `name`, `description`, `accessPolicy`, `kind`, `tool`, and `inputContract` for every registered capability (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityMetadata.java:10-20`). Its constructor verifies nonblank names/descriptions and exact agreement between the registered name and tool name (`CapabilityMetadata.java:22-38`).
- `CapabilityToolDescriptor` stores name, description, and the registered schema string. Its generic schema is the exact string `{"type":"object","additionalProperties":true}` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityToolDescriptor.java:5-21`).
- `CapabilityRegistry.getAllCapabilities()` is the registry-wide enumeration (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityRegistry.java:6-13`). `InMemoryCapabilityRegistry` stores exact names in a concurrent map and returns a detached `List.copyOf(...)`; it does not sort that list (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/InMemoryCapabilityRegistry.java:8-10`, `:45-49`).
- Java skill registration builds its reflected schema with the application conversion mapper and stores that serialized string directly in `CapabilityToolDescriptor` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java:202-218`, `:325-355`).
- YAML and REST registration resolve the manifest contract and store `inputs.toJsonSchema(contract)` in the same descriptor (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:37-59`). Omitted schemas resolve to the generic object contract; explicit YAML schemas are serialized from the resolved internal contract (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/input/SkillInputContractResolver.java:39-50`, `:66-76`).
- Model tool binding reads the registered descriptor rather than regenerating a schema (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java:21-29`). Consequently, `CapabilityMetadata.tool().inputSchema()` is the current byte-for-byte source for the string shown to models.
- `CapabilityKind` already distinguishes `YAML_SKILL`, `REST_SKILL`, and `JAVA_SKILL` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityKind.java:3-6`). No public kind projection exists.

### 3. Existing eager immutable catalog pattern and separate operator scope

- `YamlSkillCapabilityRegistrar` is a `SmartInitializingSingleton`; its synchronized, idempotent `completeRegistration()` completes Java discovery, registers all YAML/REST definitions, validates children, and then marks registration complete (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skill/YamlSkillCapabilityRegistrar.java:13-18`, `:35-66`).
- `DefaultRegisteredSkillCatalog` explicitly calls `registrar.completeRegistration()` in its constructor before it reads the registry (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalog.java:23-31`). It builds a `TreeMap`, rejects duplicates, and exposes an unmodifiable navigable map, giving exact Java string ordering and immutable snapshot state (`DefaultRegisteredSkillCatalog.java:30-52`).
- The operator projection maps YAML/REST definitions to source path plus manifest text and Java definitions to bean plus method. Its source label conversion is inline and exhaustive over the three internal kinds (`DefaultRegisteredSkillCatalog.java:35-48`). This is the existing kind-translation site; there is no shared internal translator today.
- `DefaultRegisteredSkillCatalog` is constructed by the optional observability route registrar (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/observability/web/ObservabilityRouteRegistrar.java:136-151`). It is not an always-available application bean, and its `RegisteredSkillEntry` shape contains operator-only source details.
- `DefaultRegisteredSkillCatalogTest` verifies that registration completion precedes registry enumeration and covers YAML/REST/Java source projection (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/runtime/observation/catalog/DefaultRegisteredSkillCatalogTest.java:28-123`).
- `LoomspanAutoConfiguration` currently creates the registry, registrar, and a single `SkillTemplate` bean, but no public catalog bean (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:74-79`, `:175-189`, `:262-278`). These framework beans do not use `@ConditionalOnMissingBean`; the documented supported surface does not promise replacement.

### 4. Current facade input preparation and error mapping

- The Object observer overload rejects null unconditionally, then calls `ObjectMapper.convertValue` to a `Map<String,Object>`. Conversion runtime failures become `SkillException("Skill '<name>' execution failed.", cause)` before exact skill lookup occurs (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:73-89`). It then delegates to the Map observer overload.
- The Map observer overload performs, in order: `requireSkill`, contract acquisition, `normalizeNullInput`, `inputValidator.validate`, and public issue conversion (`DefaultSkillTemplate.java:92-113`). `requireSkill` returns `SkillException("Unknown skill '<name>'")` for absent registrations and rejects inconsistent custom registry metadata (`DefaultSkillTemplate.java:153-166`).
- Map null is normalized to `Map.of()` only for a generic or empty-permitting contract. Other contract-backed skills receive `SkillInputValidationException("Skill input must not be null for contract-backed skills.", List.of())` (`DefaultSkillTemplate.java:169-182`).
- Invalid validation results become `SkillInputValidationException` with copied public `path`, `code`, and `message` issues, and a detailed message assembled by `buildValidationMessage` (`DefaultSkillTemplate.java:101-109`, `:184-191`).
- Pre-session runtime failures in this preparation block preserve `AccessDeniedException` and existing `SkillException`; other runtime failures are wrapped as the same safe `SkillException` form (`DefaultSkillTemplate.java:111-119`).
- Only after successful input preparation does the facade read the calling thread's `SecurityContextHolderStrategy` and enter `sessionRunner.callWithNewSession` (`DefaultSkillTemplate.java:121-129`). `DefaultSkillTemplateTest` covers caller authentication capture, exception preservation/wrapping, exact-name rejection, Map/Object null differences, Object-to-Map delegation, successful observation, and no observer on invalid input (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:51-306`).

### 5. Authorization is session-free at its policy core but session-bound at invocation

- `SkillRoleEvaluator.canAccess(policy, authentication)` depends only on the internal policy and the supplied `Authentication`. It implements unrestricted, denied, and roles cases, using `ROLE_` by default or `GrantedAuthorityDefaults`, plus an optional `RoleHierarchy` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/SkillRoleEvaluator.java:9-28`).
- `DefaultAccessGuard` owns a `SkillRoleEvaluator`. Its public internal methods first resolve explicit invocation authentication with session authentication as fallback, then apply the evaluator; denial uses the exact message `Access denied for capability '<name>'` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/security/DefaultAccessGuard.java:11-49`).
- Auto-configuration currently constructs the evaluator inline when it creates `AccessGuard`; it does not declare a standalone evaluator bean (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:205-213`).
- Root invocation does not check authorization in the pre-session facade block. `CapabilityExecutionRouter.execute` checks access after session construction, then validates input again, then requires the current execution binding before delegating to the coordinator (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:40-69`). Execution-time access and validation are therefore current independent safeguards.
- `JavaSkillAuthorizationIntegrationTests` compares `SkillRoleEvaluator` results with real Spring JSR-250 proxy outcomes under the default, custom, and empty role prefixes and a hierarchy, and also covers interface policy (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/security/JavaSkillAuthorizationIntegrationTests.java:27-83`). `DefaultAccessGuardTest` covers unrestricted, missing-auth, session fallback, and explicit-auth precedence (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/security/DefaultAccessGuardTest.java:19-62`).
- All capability kinds store an internal `SkillAccessPolicy`: YAML/REST use manifest roles in the registrar, while Java registration uses `SkillAccessPolicyResolver` over JSR-250 declarations (`YamlSkillCapabilityRegistrar.java:51-59`; `SkillMethodBeanPostProcessor.java:202-218`).

### 6. Why a pre-check can remain session-, trace-, quota-, and binding-free

- Lookup, null normalization, Jackson conversion, and initial validation all currently occur before `callWithNewSession` (`DefaultSkillTemplate.java:73-123`). No trace/session/quota state exists at that point.
- Session construction, observation-handle creation, trace-handle creation, root attachment, and `ExecutionBindingScope.supplyWith(...)` are all inside `LoomspanSessionRunner.executeRoot` (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:216-247`).
- `SkillRoleEvaluator` requires neither `LoomspanSession` nor `ExecutionBindingScope`, so the same registered access policy and calling-thread authentication can be evaluated without entering the runner (`SkillRoleEvaluator.java:20-28`).
- The current invocation path must still enter the router after a future successful pre-check because the router owns execution-time access and input revalidation (`CapabilityExecutionRouter.java:40-69`). A successful pre-check has no connection to `FrameworkExecutionLifecycle.admitRoot()` and therefore cannot reserve later admission.
- Root shutdown rejection occurs at `FrameworkExecutionLifecycle.admitRoot()` before session construction (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:48-59`; `LoomspanSessionRunner.java:221-225`). `FrameworkShutdownIntegrationTest#closeEventRejectsLateRootBeforeSessionConstruction` is the existing executable proof for late-root rejection (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:56-94`).

### 7. Current success observation and failed-session availability

- `DefaultSkillTemplate` packages the execution result and session in an internal `ExecutionResult`; its successful runner completion callback maps the session and calls the optional observer before returning the text (`DefaultSkillTemplate.java:123-129`, `:145-150`, `:193-195`).
- The runner admits one root, creates and binds one session, executes the action, finalizes the session in the bound action's `finally`, invokes the completion callback after `ExecutionBindingScope.supplyWith` returns, and then releases the root in an outer `finally` (`LoomspanSessionRunner.java:216-254`). Thus the current success callback is on the caller thread, after binding restoration, while `FrameworkExecutionLifecycle.activeRoots` still contains the root.
- `FrameworkExecutionLifecycleTest#runnerCompletionOccursAfterBindingRestorationAndBeforeRootRelease` explicitly asserts both ordering facts (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:99-118`).
- Successful completion-callback runtime failures are wrapped as the internal `CompletionPhaseFailure`; `DefaultSkillTemplate` unwraps its original runtime exception before the facade's generic runtime mapping (`LoomspanSessionRunner.java:248-260`; `DefaultSkillTemplate.java:131-143`). `DefaultSkillTemplateTest#observerExceptionPropagatesAfterExecutionCompletes` protects unchanged observer propagation (`DefaultSkillTemplateTest.java:266-286`).
- When the action throws, `executeRoot` remembers the original `RuntimeException` or `Error`, calls `completeSession(session, failure)` in `finally`, and rethrows the original. Because normal control never reaches `completion.apply`, the facade receives no session and the observer is not called (`LoomspanSessionRunner.java:241-250`).
- `completeSession` records/finalizes the failure. A cleanup failure is suppressed on the original failure unless the session already recorded a failure-recording failure; a cleanup failure replaces only an otherwise successful action (`LoomspanSessionRunner.java:263-340`).
- `LoomspanSession.finalizeTrace` projects the journal before canonical trace finalization and stores it in `finalizedExecutionJournal` only when projection and finalization both succeed (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/LoomspanSession.java:690-743`). `getExecutionJournal()` returns that retained journal after completion (`LoomspanSession.java:491-510`). This state is independent of the optional Console `FinalizedTraceCatalog`.
- Projection or finalization failure leaves no retained finalized journal and raises a finalization failure (`LoomspanSession.java:713-785`). This is the current source-level basis for the ticket's “available history” rather than guaranteed history language.
- `SkillExecutionViewMapper` maps directly from the session's execution journal to immutable public events and does not query Console storage or trace persistence (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java:23-61`). Mapping can throw through Jackson conversion or public event validation.
- Today an execution failure therefore preserves its original facade mapping but yields no public callback. `DefaultSkillTemplateTest#wrapsOtherRuntimeFailureWithSafeSkillExceptionAndCause` asserts both the safe wrapping and absent observer (`DefaultSkillTemplateTest.java:103-117`).

### 8. Root lifetime and shutdown overlap already cover the successful completion phase

- `FrameworkExecutionLifecycle` owns an exact admitted-root set, closes admission once, establishes one monotonic deadline, waits for roots, then shuts down/cuts off the mission executor (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/FrameworkExecutionLifecycle.java:21-116`).
- `AdmittedRoot.close()` removes ownership exactly once (`FrameworkExecutionLifecycle.java:213-247`). Because runner completion precedes this call, slow successful mapping/observation already remains inside the shared root ownership interval.
- The same ordering means a failure-history callback must occur at the runner/facade completion boundary to retain the same shutdown semantics. Current failed execution skips that boundary, even though the root remains owned until stack unwinding reaches the runner's outer `finally`.
- Pre-session failures—including unknown skill, invalid input, calling-thread security-context lookup failure, and shutdown rejection before session construction—do not have a completed session to map. The current code produces no callback for those paths.

### 9. Test and fixture topology relevant to this ticket

- `DefaultSkillTemplateTest` is the focused facade suite for overload behavior, exception mapping, security-context capture, validation, and observer cardinality (`DefaultSkillTemplateTest.java:44-354`).
- `LoomspanSessionRunnerTest` covers failure finalization, retained ONERROR traces, failure-recording precedence, observation finalization, and trace projection/finalization failures (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java:43-640`).
- `FrameworkExecutionLifecycleTest` and `FrameworkShutdownIntegrationTest` own admission/root lifetime and Spring shutdown ordering (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:30-326`; `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:26-267`).
- `SkillExecutionViewMapperTest` owns mapping shapes for object, textual, array, scalar, and null payloads (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapperTest.java:15-115`).
- `YamlSkillCapabilityRegistrarTests`, `SkillMethodBeanPostProcessorTest`, and `DefaultRegisteredSkillCatalogTest` provide existing metadata/schema/kind/registration-order coverage. `LoomspanAutoConfigurationTests` verifies the single facade bean; `SupportedSurfaceIntegrationTest` is the cumulative public-only harness.
- This ticket does not change the Console application-adapter REST/SSE, acquisition, problem, or consumed-NDJSON wire boundary. The public observer reads the retained in-process journal, so there is no current Java-to-Go protocol consumer or Console fixture that must change for the catalog/pre-check/failure-callback feature itself. Operator projection remains separately covered by its existing Java/Go/TypeScript fixtures from PR 5.2.

### 10. In-scope internal cleanup anchors

- `CapabilityExecutionRouter.objectiveFor` still accepts a `Map<String,Object> arguments` parameter that it does not use (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/core/CapabilityExecutionRouter.java:68-74`). The ticket permits removing it only if that path is edited.
- `StepLoopMissionExecutionEngine.executeToolAction` still contains the recorded redundant exception-type branch before identical propagation. The ticket permits removing it only if that path is edited; the PR 5.3 feature does not otherwise require changes to the step loop (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:1036-1065`).

## Contract Surface Classification

| Surface | Lens category | Current evidence and protected consumers |
| --- | --- | --- |
| Existing `SkillTemplate` and future `validate` methods | Application API | `ai.loomspan.api`, architecture allowlist, README, Java API knowledge set, application injection, and supported-surface test establish supported status. Adding methods is intentionally compatibility-sensitive and ticketed with no shim. |
| Future `SkillCatalog`, `SkillDescriptor`, `SkillKind` | Application API | The ticket deliberately requires `ai.loomspan.api`, allowlist, README, and public proof. They do not exist at this revision. Signatures are constrained to JDK/public API types. |
| Observer callback behavior on execution failure | Application API behavioral contract | Observer overloads and public view/event records are supported. Current docs/tests state success-only behavior; the ticket intentionally expands it while preserving success exception propagation and execution-failure precedence. |
| `RestSkillHandler` / `RestSkillInvocation` | Supported SPI | Existing sole supported SPI, unchanged by this ticket. Catalog and validation do not create another handler or bean-replacement SPI. |
| YAML/REST manifests and `loomspan.*` settings | Configuration and manifest contracts | This ticket adds no syntax, key, default, or reload behavior. It reads the already-registered resolved contract and access policy. |
| New durable storage or interchange | Persisted or serialized contracts | None. The catalog is one in-memory snapshot and validation returns no token. There is no new persisted history or cross-version format. |
| `SkillExecutionView` / `SkillExecutionEvent` contents | Ephemeral diagnostic formats | Current-version, immutable diagnostic values already supported for observation. Shape is unchanged; callback availability expands to post-session execution failures when mapping succeeds. |
| Registry, metadata, registrar, role evaluator, access guard, runner, session, mapper, auto-configuration methods, catalog implementation | Internal or accidentally exposed implementation | All live below `internal` or are Spring integration machinery. Their visibility/constructors are implementation collaboration, not protected consumer extension points. Atomic internal signature changes need no shim. |

## Architecture Documentation

The feature crosses three existing ownership boundaries without requiring a new execution engine:

```text
registration completion
  -> CapabilityRegistry / CapabilityMetadata / exact tool schema
  -> immutable public catalog snapshot

SkillTemplate caller thread
  -> exact lookup -> overload-specific normalization/conversion -> input validation
  -> session-free SkillRoleEvaluator against captured calling Authentication
  -> return void (no admission/session/binding/trace/quota/observer)

SkillTemplate invoke
  -> same preparation -> FrameworkExecutionLifecycle.admitRoot
  -> session + binding -> execution-time access and validation -> execution
  -> session failure/success finalization -> retained journal
  -> binding restoration -> map/deliver at most once on caller
  -> release the same admitted root
```

The public catalog and operator catalog are different projections of the same internal registration authority. The public projection requires only name, description, public kind, and exact registered schema; the operator projection additionally owns declaration paths/text or bean/method fields. Neither projection is an authorization authority, mutable registry, refresh lifecycle, or replacement point.

The validation-only flow uses the same trusted caller authentication source as invocation but stops before the runner. Its access result is advisory for dispatch: invocation retains in-session validation/access enforcement and shutdown admission, so a later policy or lifecycle outcome may differ. Nested authorization remains owned by execution when a child is actually selected.

For observation, the existing root runner is already the lifecycle authority spanning caller-side completion. The missing current behavior is not trace creation or journal retention; it is a failure-aware transfer of the completed session to the existing facade mapping/callback stage while retaining the original throwable as primary.

## Loomspan Documentation Assessment

Selected knowledge set: `java-api`. The repository-bundled index and compatibility baseline route this work to `invocation.md` and `observation-and-errors.md`. Those documents currently describe the ten-type allowlist, four invoke overloads, pre-session input validation, success-only synchronous observers, and facade error mapping (`agent-skills/loomspan-docs/references/java-api/README.md`; `compatibility-and-boundaries.md`; `invocation.md`; `observation-and-errors.md`).

The installed `loomspan-docs` skill reports version `0.1.0-SNAPSHOT`, while the checkout POM and repository-bundled skill report `1.0.0-beta.4-SNAPSHOT` (`C:/Users/mgiacomi/.codex/skills/loomspan-docs/SKILL.md:6`; `pom.xml:9`; `agent-skills/loomspan-docs/SKILL.md:6`). The installed copy was therefore used only as the documentation router and not for version-sensitive behavior. Exact claims use the version-aligned repository-bundled documentation and executable source/tests.

**Drift classification: aligned.** At `44a2f75`, the repository-bundled Java API guidance agrees with the current success-only observer and four-overload `SkillTemplate` implementation. The PR 5.3 ticket intentionally requests future API and behavioral changes; the absence of those future descriptions is not current documentation drift. Implementation will need to update the allowlist counts, invocation/catalog guidance, observation/error behavior, README, and their named source anchors atomically with the feature.

## Historical Context (from `ai/thoughts/`)

- `ai/thoughts/tickets/loomspan-pr-5.3-external-caller-api.md` is the binding delivery ticket. It owns all FW2 catalog, validation, failure-history, public proof, lifecycle overlap, and documentation requirements.
- `ai/thoughts/phases/phase-fw2.md` records the same three-part outcome and identifies registry metadata, session-free role evaluation, null-overload differences, and retained finalized journals as reuse anchors.
- `ai/thoughts/phases/beta4-rest-skills-and-sidecar-roadmap.md` establishes Sidecar as the first consumer, public-API-only access, startup-only catalog lifecycle, asynchronous pre-dispatch validation, and `NEVER`/`ONERROR`/`ALWAYS` history policy to be implemented later in the Sidecar repository.
- `ai/thoughts/beta4-ticket-readiness.md` groups catalog, both validation overloads, and failure observation into this single coherent unit and keeps framework documentation/readiness as the following PR 5.4 unit.
- `ai/thoughts/tickets/loomspan-pr-5.1-framework-lifecycle.md` delivered the single admitted-root lifetime and shutdown deadline that this ticket extends through failure observation.
- `ai/thoughts/tickets/loomspan-pr-5.2-rest-skills-and-console.md` delivered the third registered kind and coordinated operator projection so this catalog can expose YAML, Java, and REST atomically.
- `ai/thoughts/beta4-design-review.md` records accepted S1/S4/S5 decisions: one root completion path, one eager catalog snapshot, separate public/operator projections, and reuse of the supported-surface fixture.
- `ai/thoughts/beta4-code-grounding.md` describes the older `1e4eb455` baseline. Its proposed reuse anchors remain historically relevant, but PR 5.1 and PR 5.2 are now implemented at `44a2f75`; this research uses current source as authority.
- `ai/thoughts/framework-feature-design-lens.md` classifies the affected public, configuration, diagnostic, and internal surfaces and requires a deliberate no-shim decision for this pre-1.0 API change.

## Related Research

No earlier research documents exist in `ai/thoughts/research/` at this revision.

## Open Questions

No developer decision is required before planning. The ticket settles observable behavior, compatibility posture, catalog contents/lifecycle, callback precedence, and exclusions.

The planning step still needs to choose and record internal implementation details that do not change those contracts:

1. the narrow failure-aware completion representation/signature between `LoomspanSessionRunner` and `DefaultSkillTemplate`;
2. the internal location for shared `CapabilityKind` translation while keeping public and operator DTOs independent;
3. the focused test placement for mapper/finalization failure injection, callback cardinality, and pre-check/session/shutdown proof; and
4. whether the permitted `objectiveFor` and redundant step-loop cleanup paths are actually edited by the feature changes.

Those choices can be resolved from the ticket, current source, future roadmap/ticket context in `ai/thoughts/tickets/`, and phase references in `ai/thoughts/phases/`; they are not escalation items.
