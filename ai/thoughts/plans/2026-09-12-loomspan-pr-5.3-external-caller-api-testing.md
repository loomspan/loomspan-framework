# PR 5.3 External Caller API Testing Plan

## Change Summary

- Add the three-type public, eager, immutable, unfiltered catalog projection over completed YAML/Java/REST registrations.
- Add both `SkillTemplate.validate` overloads with invocation-equivalent preparation/error behavior and session-free root authorization.
- Deliver one available completed public execution view after post-session failure without changing original failure precedence or successful-observer behavior.
- Extend the existing closed-surface and YAML/Java/REST integration proof; do not create a parallel end-to-end harness.

## Impacted Areas

- Public contract: `ai.loomspan.api.SkillCatalog`, `SkillDescriptor`, `SkillKind`, and `SkillTemplate`.
- Catalog projection/registration: `CapabilityKind`, new `DefaultSkillCatalog`, `DefaultRegisteredSkillCatalog`, `YamlSkillCapabilityRegistrar`, and `CapabilityRegistry` metadata/tool descriptors.
- Validation/security: `DefaultSkillTemplate`, `SkillInputValidator`, `SkillRoleEvaluator`, `DefaultAccessGuard`, and caller `SecurityContextHolderStrategy` capture.
- Completion/diagnostics: `LoomspanSessionRunner`, `LoomspanSession` finalized journal, `SkillExecutionViewMapper`, `ExecutionBindingScope`, and `FrameworkExecutionLifecycle` admitted-root ownership.
- Wiring/public proof: `LoomspanAutoConfigurationTests`, `LoomspanPublicSurfaceArchitectureTest`, `ApplicationApiValueTest`, and `SupportedSurfaceIntegrationTest` with its existing YAML/REST fixtures and Java bean.
- Application-developer documentation: root `README.md` and `agent-skills/loomspan-docs/references/java-api/`; no skill-authoring topic changes.

## Risk Assessment

- **High — exception precedence:** invoking the completion callback on failure could replace/wrap the original execution exception, accidentally turn a successful observer failure into an execution failure, or invoke an observer twice.
- **High — root lifetime:** callback/mapping could move outside admitted-root ownership or run while execution binding is still installed, causing shutdown to return early or callbacks to execute on a waiter.
- **High — authorization parity:** a separately constructed evaluator, wrong authentication source, or changed role-prefix/hierarchy behavior could make validate and invoke disagree. Successful validation must remain advisory and execution must recheck.
- **High — overload parity:** refactoring can accidentally unify the deliberate Object-null and Map-null difference, change conversion-before-lookup order for Object input, or alter validation issue/message mapping.
- **Medium — catalog fidelity:** reserializing schemas, caller filtering, incomplete registration, unstable order, or reused operator DTOs could violate the external caller contract or leak roles/paths/beans.
- **Medium — unavailable history:** projection/finalization/mapper failure must yield no callback and must not mask a post-session execution failure; persistence policy and Console availability must not control the public view.
- **Medium — accidental public expansion:** new internal callback/wiring types or Spring methods must not leak through the closed API or create a second SPI/replacement contract.
- **Protected compatibility paths:** all four existing `invoke` signatures, `RestSkillHandler` as sole SPI, success result/observer exception behavior, AccessDenied/SkillException mapping, manifest/config contracts, execution-time validation/access, event shapes/order/security warning, and root admission/deadline semantics remain protected.
- **Intentional changes/removals:** add two abstract `SkillTemplate` methods without a shim and replace success-only observer behavior with success-or-mappable-failure delivery, as authorized by `Pipeline notes`. Replace the internal runner/facade/evaluator signatures atomically; do not test simultaneous old/new behavior.
- **Authoring claims requiring evidence:** none. Java API application guidance changes, but manifest/skill-authoring semantics do not.

## Existing Test Coverage

- `DefaultRegisteredSkillCatalogTest` proves eager registration completion and YAML/REST/Java operator projection, but not the public descriptor shape, exact schema bytes, unfiltered scope, or application injection.
- `ApplicationApiValueTest` proves existing record immutability and exact supported SPI shapes, but has no catalog types or validate signatures.
- `DefaultSkillTemplateTest` proves current input preparation, security-context capture, facade exception mapping, successful observation, and no observer for pre-session invalid input. Its current `wrapsOtherRuntimeFailureWithSafeSkillExceptionAndCause` expectation explicitly demonstrates the missing failure callback.
- `JavaSkillAuthorizationIntegrationTests` and `DefaultAccessGuardTest` prove prefix/hierarchy and session fallback/explicit-auth behavior; they should remain the evaluator authority while new facade tests prove reuse.
- `LoomspanSessionRunnerTest` proves failure finalization, retained ONERROR traces, and finalization-failure precedence, but not a post-failure completion callback.
- `FrameworkExecutionLifecycleTest#runnerCompletionOccursAfterBindingRestorationAndBeforeRootRelease` proves the successful completion boundary only; shutdown integration proves late root rejection before session construction.
- `SkillExecutionViewMapperTest` proves payload/event mapping and order but not delivery precedence when mapping throws.
- `SupportedSurfaceIntegrationTest` already supplies the one public-only local model/YAML/Java/REST fixture and successful observer; it lacks catalog, validate, and failure-history proof.
- `LoomspanPublicSurfaceArchitectureTest` enforces the current ten-type allowlist and recursively rejects internal/autoconfigure signature leaks.

## Bug Reproduction / Failing Test First

- **Type:** unit
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **Test:** `executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure`
- **Arrange/Act/Assert outline:** register the existing contract-backed test skill; have the mocked router append one known journal record through the supplied session and then throw an `IllegalStateException`; invoke with an observer that records count/view; assert the public call still throws `SkillException("Skill 'invoiceParser' execution failed.", original)` and the observer ran exactly once with the same session's ordered preceding and terminal failure events.
- **Expected failure (pre-fix):** the current runner rethrows from `ExecutionBindingScope.supplyWith` before reaching its success-only completion function, so the observer count remains zero (`LoomspanSessionRunner.java:241-250`; current `DefaultSkillTemplateTest.java:103-117`).

The new catalog/validate signature tests will initially fail compilation until the deliberate API types/methods are added, but the observer test is the minimal executable red test for the existing behavioral gap.

## Tests to Add/Update

### 1. `publicCatalogTypesExposeOnlyTheTicketedJdkShape`

- **Type:** unit/reflection
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/api/ApplicationApiValueTest.java`
- **What it proves:** `SkillKind.values()` is exactly YAML/JAVA/REST; `SkillDescriptor` has exactly name/description/kind/inputSchema; `SkillCatalog` has only `skills()` and `skill(String)` with JDK/public types; `SkillTemplate` has both exact `void validate` overloads.
- **Fixtures/data:** constructed descriptors with ordinary strings and enum values.
- **Mocks:** none.
- **Affected surface:** Application API.
- **Compatibility expectation:** intentional additive types and ticket-authorized interface change; no extra public concepts.

### 2. `buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`

- **Type:** unit
- **Location:** new `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillCatalogTest.java`
- **What it proves:** registrar completion happens before enumeration; mixed exact names sort by Java natural order; YAML/Java/REST map correctly; restricted entries are still present; missing lookup is empty; returned list cannot mutate; schemas including whitespace/key order are byte-for-byte descriptor strings; public descriptors expose no operator fields.
- **Fixtures/data:** mocked/real registry metadata for all three kinds, one role-restricted entry, deliberately noncanonical schema strings, and names such as `Alpha`/`beta`.
- **Mocks:** mock registrar to verify `completeRegistration()` ordering; registry metadata may be concrete values. Add a duplicate-name defensive test using a custom registry result because the normal registry already rejects duplicates.
- **Affected surface:** Application API and internal implementation.
- **Compatibility expectation:** new protected catalog contract; operator catalog remains separate.

### 3. `autoConfiguresOneCompletedPublicCatalogAndOneSharedRoleEvaluator`

- **Type:** Spring integration
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/autoconfigure/LoomspanAutoConfigurationTests.java`
- **What it proves:** application contexts contain one `SkillCatalog` and `SkillTemplate`; the public snapshot is available after registration; `DefaultAccessGuard` and `DefaultSkillTemplate` use the same configured internal evaluator with custom prefix/hierarchy inputs; application beans do not create backoff/replacement semantics.
- **Fixtures/data:** existing `ApplicationContextRunner`, model-free YAML locations, mapped Java target, optional role prefix/hierarchy configuration.
- **Mocks:** none; inspect internal fields only in this internal wiring test.
- **Affected surface:** Application API plus internal/autoconfigure implementation.
- **Compatibility expectation:** protected injection availability with no supported replacement contract.

### 4. `validateMapMatchesInvokePreparationAndErrors`

- **Type:** unit
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves:** exact unknown message, registry-name mismatch, conditional null acceptance, required-contract null rejection, normalized valid input, invalid message/issues, existing `SkillException` passthrough, other runtime wrapping, and input-validation ordering match Map invocation.
- **Fixtures/data:** generic, empty-permitting, required explicit contracts; valid/invalid maps; custom mismatching registry; captured exceptions from validate and invoke for direct equality of type/message/issues.
- **Mocks:** router mocked only to make the invoke comparator deterministic; verify validate never calls it.
- **Affected surface:** Application API.
- **Compatibility expectation:** new protected pre-check matching existing invoke behavior.

### 5. `validateObjectMatchesInvokeConversionAndKeepsDistinctNullRule`

- **Type:** unit
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves:** Object null fails before lookup/conversion even for generic skills, DTO conversion precedes exact lookup and then shares Map validation, non-map conversion failure uses the exact safe `SkillException` mapping, while a null Map remains accepted only where the contract permits empty input.
- **Fixtures/data:** existing `InvoiceRequest`, null Object/Map, non-map-convertible input, generic and explicit contracts.
- **Mocks:** spy/mock application conversion mapper where ordering or conversion failure needs deterministic proof.
- **Affected surface:** Application API.
- **Compatibility expectation:** protected overload distinction required by the ticket.

### 6. `validateUsesCallingAuthenticationAndSharedRoleSemantics`

- **Type:** focused integration
- **Location:** extend `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/security/JavaSkillAuthorizationIntegrationTests.java` and facade coverage in `DefaultSkillTemplateTest.java`
- **What it proves:** authorized/denied YAML/REST policies and Java `@RolesAllowed` use the caller thread's authentication, default/custom/empty prefixes and hierarchy agree with real JSR-250 behavior, denial is the unwrapped `AccessDeniedException("Access denied for capability '<name>'")`, and invoke still independently rechecks.
- **Fixtures/data:** existing authorization proxy contexts/policies plus concrete registry metadata for manifest and Java policies.
- **Mocks:** mock router in facade unit tests; real Spring security proxy/evaluator in authorization integration tests.
- **Affected surface:** Application API and internal security implementation.
- **Compatibility expectation:** new protected validation path; existing execution authorization remains protected.

### 7. `validateCreatesNoExecutionStateAndPreservesBinding`

- **Type:** unit
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves:** successful and denied pre-checks do not call the session runner/router/capability invoker, create observation handles or trace files, consume session quota, call an observer, or replace/clear a pre-existing `ExecutionBindingScope` value.
- **Fixtures/data:** recording observation factory/invoker counters, one explicit binding for scoped assertion, authorized and denied authentications.
- **Mocks:** runner/router verification plus real evaluator/input validator.
- **Affected surface:** Application API and internal implementation.
- **Compatibility expectation:** protected no-session/no-reservation pre-check contract.

### 8. `validationDoesNotReserveAdmissionAndLateInvokeCreatesNoSession`

- **Type:** lifecycle integration
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java`
- **What it proves:** validate succeeds while admission is open; framework admission then closes; invoke is rejected at `admitRoot()` before session/observation construction even though validation previously passed; callback and execution counters remain zero.
- **Fixtures/data:** manual framework lifecycle/runner/facade with a registered valid unrestricted skill and recording observation factory.
- **Mocks:** router/capability invoker counters; no model.
- **Affected surface:** Application API and internal lifecycle.
- **Compatibility expectation:** protected no-reservation semantics and existing shutdown rejection.

### 9. `executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure`

- **Type:** unit (the red test)
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves:** a post-session runtime failure yields one ordered completed public view and still maps to the same safe `SkillException` with the original cause; `AccessDeniedException`, existing `SkillException`, and JVM `Error` retain their existing outward categories.
- **Fixtures/data:** router appends a known trace record before throwing; parameterize runner persistence as NEVER/ONERROR/ALWAYS to prove journal delivery is independent of retained Console artifacts.
- **Mocks:** mocked router; real runner/session/finalizer/mapper.
- **Affected surface:** Application API and ephemeral diagnostics.
- **Compatibility expectation:** ticket-authorized observer expansion with current-run diagnostic coherence.

### 10. `failureObserverOrMapperFailureNeverMasksExecutionFailure`

- **Type:** unit
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves:** observer RuntimeException/Error and injected mapper failure are suppressed behind the original post-session execution throwable; facade type/message/cause stay unchanged; the callback is attempted at most once and mapper failure means no observer delivery. A callback failure identical to the primary throwable does not trigger self-suppression failure.
- **Fixtures/data:** recording/throwing observer, package-private injected mapper, one post-session router failure.
- **Mocks:** mocked `SkillExecutionViewMapper` for deterministic mapping failure; mocked router.
- **Affected surface:** Application API and ephemeral diagnostics.
- **Compatibility expectation:** intentional failure-observer behavior with protected original-failure precedence.

### 11. `successfulObserverFailureRemainsUnwrappedAndIsNotRetried`

- **Type:** unit regression
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java`
- **What it proves:** existing successful execution returns to the completion phase once; observer exception instance propagates unchanged, is not wrapped as skill execution failure, causes no second callback, and root cleanup still runs.
- **Fixtures/data:** successful router result and counted throwing observer.
- **Mocks:** mocked router; real runner.
- **Affected surface:** Application API.
- **Compatibility expectation:** protected success behavior.

### 12. `failureCompletionRunsAfterFinalizationAndBindingRestorationBeforeRootRelease`

- **Type:** unit/lifecycle
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java` and `FrameworkExecutionLifecycleTest.java`
- **What it proves:** the failure-aware completion receives a finalized journal, sees no current execution binding, runs once on the invoking thread while active-root count is one, and the original failure is rethrown before active-root count reaches zero. Existing success assertions remain true.
- **Fixtures/data:** real runner/lifecycle, captured caller thread/session/journal, thrown action exception.
- **Mocks:** none beyond no-op observation.
- **Affected surface:** Internal implementation and ephemeral diagnostics.
- **Compatibility expectation:** current-run diagnostic coherence under the protected root lifetime.

### 13. `unavailableHistorySkipsObserverAndPreservesOriginalFailure`

- **Type:** unit
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java` plus facade assertion in `DefaultSkillTemplateTest.java`
- **What it proves:** injected projection/finalization failure leaves no retained journal; failure completion/mapping cannot deliver a view; the action failure remains primary with cleanup/mapping failures only suppressed according to existing precedence.
- **Fixtures/data:** existing failing trace-handle/projection fixtures adapted to an action that also fails.
- **Mocks:** existing custom `InternalExecutionTraceHandleFactory`; injected mapper only for facade boundary.
- **Affected surface:** Ephemeral diagnostics and internal implementation.
- **Compatibility expectation:** available-history limitation, not guaranteed or historical readability.

### 14. `shutdownWaitsForSlowSuccessAndFailureCompletionUnderOneRoot`

- **Type:** concurrent lifecycle integration
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java`
- **What it proves:** counted/latch-blocked success and failure completion callbacks retain exactly one active root; a concurrent lifecycle stop waits under the shared deadline; callback runs on the original caller, not stop/waiter; release occurs once after callback exits.
- **Fixtures/data:** real lifecycle/runner, latches for completion entry/release, virtual caller thread, success and failure cases.
- **Mocks:** no-op observation factory.
- **Affected surface:** Internal lifecycle and Application API behavioral support.
- **Compatibility expectation:** protected PR 5.1 deadline/ownership semantics extended to failure observation.

### 15. `supportedSurfaceDiscoversValidatesAndObservesAllKinds`

- **Type:** existing end-to-end Spring integration
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java` and existing `src/test/resources/skills/integration/` fixtures
- **What it proves:** through `ai.loomspan.api` only, an application injects the catalog with sorted YAML/Java/REST descriptors and exact schemas; both validate overloads accept authorized inputs and reject invalid/unauthorized roots before model/handler calls; invoke still rechecks; a failing application leaf yields a public failure view without masking the facade exception; pre-session failure produces no callback.
- **Fixtures/data:** reuse the local MockWebServer, YAML planner, `supportedRestLeaf`, and annotated Java bean; add a deterministic failing annotated method or REST route within the same configuration and roles compatible with the authorized principal.
- **Mocks:** local protocol-compatible model server only; no internal bean replacements.
- **Affected surface:** Application API, Supported SPI regression, and configuration/manifest behavior regression.
- **Compatibility expectation:** new/protected public behavior; existing SPI and manifests preserved.

### 16. `publicSurfaceContainsExactlyTheThirteenApprovedTypes`

- **Type:** architecture
- **Location:** `loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`
- **What it proves:** exactly the ten existing plus three ticketed catalog types are public top-level API; signatures recursively exclude internal/autoconfigure types; no new SPI package/type or accidental extension surface appears; new technically public internal nested/collaboration members remain under the internal classification.
- **Fixtures/data:** ArchUnit production-class import and updated allowlist.
- **Mocks:** none.
- **Affected surface:** Application API, Supported SPI, and internal exposure boundary.
- **Compatibility expectation:** intentional allowlist growth only.

## How to Run

No external service, credentials, environment variables, or production-like host are required. The supported-surface test uses its existing local `MockWebServer` and classpath fixtures.

- Red test before production changes:
  - `mvn -pl loomspan-spring-boot-starter -Dtest=DefaultSkillTemplateTest#executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure test`
- Focused public/catalog/facade/security tests:
  - `mvn -pl loomspan-spring-boot-starter -Dtest=ApplicationApiValueTest,DefaultSkillCatalogTest,DefaultSkillTemplateTest,JavaSkillAuthorizationIntegrationTests,DefaultAccessGuardTest,LoomspanAutoConfigurationTests test`
- Focused completion/lifecycle tests:
  - `mvn -pl loomspan-spring-boot-starter -Dtest=LoomspanSessionRunnerTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,SkillExecutionViewMapperTest test`
- Public boundary and cumulative fixture:
  - `mvn -pl loomspan-spring-boot-starter -Dtest=LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest test`
- Full reactor verification:
  - `mvn clean verify`

## Exit Criteria

- [x] The failure-observer red test exists and fails for the expected zero-callback reason before the implementation, then passes after it.
- [x] All new public types/methods compile with only JDK/supported API signature types and the allowlist contains exactly thirteen top-level types.
- [x] Catalog tests prove eager completion, every registered kind, unfiltered immutable exact-name order/lookup, byte-for-byte schema strings, missing lookup, and absence of operator/security fields.
- [x] Both validate overloads match invocation preparation/error behavior, including distinct null rules, conversion/lookup order, structured issues, calling authentication, prefix/hierarchy, and exact unwrapped denial.
- [x] Validation has no session/root/binding/trace/observation/execution/quota/callback side effects, reserves no admission, and invocation still revalidates/rechecks authorization.
- [x] Success and mappable failure observers run at most once on the caller after binding restoration and before the same root release; shutdown waits for slow completion within the shared deadline.
- [x] Original execution failure and facade mapping survive mapper/failure-observer/finalization failures; successful-observer exceptions remain unchanged and are never retried.
- [x] Failure views remain current-version ordered diagnostics independent of Console storage and persistence choice; unavailable history is not represented as guaranteed retrieval.
- [x] The existing YAML/Java/REST public-only integration fixture proves injection, discovery, both pre-checks, execution-time enforcement, and failure history without a duplicate harness.
- [x] Existing `RestSkillHandler`, manifest/config, event-shape, successful invocation, and shutdown tests remain green; obsolete success-only failure-observer assertions are removed rather than retained behind dual behavior.
- [x] Root README and the repository-bundled Java API knowledge set match the implemented contract; no skill-authoring coverage changes are claimed.
- [x] All focused commands and `mvn clean verify` pass with no required failures/errors/skips.
- [x] There are no non-automatable checks required for this framework unit; Sidecar HTTP/JWT/queue/container integration remains the explicit future PR 5.4/SC5 gate, not a completion criterion here.
