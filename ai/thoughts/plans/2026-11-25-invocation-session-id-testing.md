# Invocation session ID testing plan

## Change summary

The approved full-pipeline implementation plan is `ai/thoughts/plans/2026-11-25-invocation-session-id.md`. The controlling ticket is `ai/thoughts/tickets/invocation-session-id.md`. Six public invocation methods return `SkillInvocationResult` with exact text and root identity. Public exceptions expose current root identity after allocation, independently of observer mapping or diagnostic retention. Immutable copies isolate metadata from reused application exceptions.

## Impacted areas

- Application API values, signatures and exception constructors.
- Direct and admitted facade success, preparation, admission, runtime failure and completion behavior.
- Root runner identity allocation, session initialization, finalization and ownership cleanup.
- Optional public mapper/event conversion.
- Canonical traces, delivered view correlation and persistence policy.
- Existing Java callers, samples and routed API/authoring documentation.

## Risk assessment

| Risk | Required proof |
| --- | --- |
| A callback, initialization or cleanup exception escapes after session allocation without identity | Fault-injected runtime failures carry the allocated root identity through the public boundary. |
| Application exception reuse leaks foreign identity or diagnosis across roots | Concurrent roots receive distinct IDs, each matching its own observed session, while the source exception remains unchanged. |
| Access-denied wrapping changes security handling | Post-session subtype remains catchable as Spring `AccessDeniedException` and exposes identity directly. Pre-session denial remains a denied invocation without execution. |
| Result wrapper alters payload | Exact text assertions cover whitespace, JSON, empty Java null/void output and ordinary strings. |
| Eager public view construction remains hidden in a convenience overload | Mapper mock sees zero interactions in both direct/admitted no-observer success and failure paths. |
| Observer failure changes primary failure or loses suppressed evidence | Execution-side primary remains public primary, observer/mapping failure stays suppressed, and all public runtime failure paths expose current identity. |
| Return-type change misses callers | Compile all tests, then run the complete Maven suite. Adapt only public-root call sites to `.text()`. |

Application API compatibility is deliberately changed under the ticket's Pipeline notes. Exact old facade exception identities and String return-shape expectations are updated. Preserve selected terminal diagnosis, built-in designated failure classifications and reasons, input issue values, Spring catchability, caller restoration, captured generation ownership, admission exclusivity and JVM `Error` propagation. Configuration/manifest semantics and current diagnostic formats remain covered by existing tests. There is no supported SPI or cross-version serialized format addition. Java-to-Go boundary changes are unnecessary under the implementation plan.

## Existing test coverage

- `ApplicationApiValueTest` reflects facade and admitted signatures and protects exception constructors, cause initialization, immutable diagnostics and public values.
- `LoomspanPublicSurfaceArchitectureTest` enforces the closed allowlist and rejects leaked implementation types and unintended extension points.
- `DefaultSkillTemplateTest` already uses real sessions with a mocked router, optional injected mapper and security strategy. It covers exact output, admission, preparation, observer timing, failure-side suppression and fatal errors.
- `SupportedSurfaceIntegrationTest` invokes actual Java, executor-backed YAML and model YAML through supported beans and a local provider endpoint.
- `ExplicitJavaFailureHandlingIntegrationTest` verifies exact designated subtypes, authored reasons and observer precedence through real direct/admitted paths.
- `ExecutorOutputSchemaIntegrationTest` verifies selected diagnosis, cancellation precedence and concurrent reuse of foreign exceptions.
- `LoomspanSessionRunnerTest` supplies recording observation factories and controllable trace handles for identity, retention, finalization faults and unavailable history.
- `SkillGenerationExecutionIntegrationTest`, `SkillReachableAdmissionTest` and `FrameworkShutdownIntegrationTest` protect generation capture, exact roots, nested rejection, single-use and shutdown behavior.
- `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest` cover the current canonical/observer protocol consumers.

The gaps are caller-visible identity, no-observer proof at the public boundary, immutable failure snapshots and correlation under every affected completion path.

## Failing test first

Add a reflection-based unit check to `ApplicationApiValueTest`, named `rootInvocationReturnsTextAndSessionIdentity`, before production changes. Resolve `SkillTemplate.invoke(String, String, Map)` and assert that its return type has record components named `text` and `sessionId`, both `String`, with the chosen `SkillInvocationResult` name. This test can compile against the current checkout and fails because the method currently returns `String`. Record that expected failing run.

After introducing the type, extend this into exact shape coverage for all six invocation methods and the immutable result contract. Add behavioral tests with the real facade before final implementation, where practical. A missing method or type compilation failure may establish a newly requested API, but the reflection check gives a focused executable pre-change failure without coupling the initial test to missing production declarations.

Command for the initial red check is `mvn -q "-Dtest=ApplicationApiValueTest#rootInvocationReturnsTextAndSessionIdentity" test`. Run the post-change class and broader suites after adapting callers.

## Tests to add or update

### 1. Exact public API and immutable values

Type is unit/architecture, in existing `ApplicationApiValueTest` and `LoomspanPublicSurfaceArchitectureTest`. Enumerate all four template and two admitted return signatures. Assert the result record contains exactly two String components and preserves exact text, while rejecting null text and null/blank identity. Test the denial subtype's Spring superclass, direct getter and cause. Test null identity from existing exception constructors and the full session-aware construction paths, including unchanged message-constructor `initCause` behavior. Allowlist exactly the two new public types and assert supported signatures contain no internal/autoconfigure types. Use concrete values and reflection, without mocks.

Affected surface is Application API. The return contract change is intentional. Existing supported construction behavior remains covered.

### 2. Direct and admitted success matrix

Type is unit plus a small supported-surface integration extension. Use `DefaultSkillTemplateTest` for Object/Map inputs and observer/null-observer variants. Have the router capture `LoomspanSession.getSessionId()` and return exact text. Compare result identity with that capture and any observer view. Invoke admitted handles through `SkillInvocationHandoff` for both overloads. Verify every actual invocation gets a fresh nonblank identity and stable existing generation identity. Existing Java null/void, string and JSON tests should assert `.text()` so payload behavior remains exact.

Fixtures are existing registered-skill helpers and application beans. Mock the router for the unit matrix, and use real public composition for integration. Affected surfaces are Application API and Internal implementation.

### 3. No observer bypasses public projection

Type is unit, in `DefaultSkillTemplateTest`. Inject a mapper mock configured to fail if invoked. Exercise direct and admitted no-observer success and runtime failure. Assert result/exception identity equals the session captured by the router and `verifyNoInteractions(mapper)`. Cover the explicit null-observer path as well as convenience overloads. Existing mapper tests establish that public event conversion is reached only through this mapper.

Affected surfaces are Application API and Internal implementation. This is the explicit performance and independence requirement.

### 4. Post-session runtime failure and access denial

Type is unit and focused supported integration. Exercise ordinary runtime failure, base `SkillException`, `RecoverableSkillException`, `PlannerInabilityException`, input-validation subtype thrown during execution, and Spring access denial after execution starts. Compare direct `getSessionId()` with the router's root capture and any delivered view. Preserve exact designated messages, issue values and selected diagnosis. Assert access denial is `SkillAccessDeniedException`, is catchable as Spring `AccessDeniedException`, has the original failure as cause and does not become successful skill output. Update former facade `isSameAs` tests to assert immutable snapshot semantics and preserved evidence.

Use the existing facade router fake and real Java failure beans in `SupportedSurfaceIntegrationTest` and `ExplicitJavaFailureHandlingIntegrationTest`. Affected surface is Application API. Designated classes and Spring catchability are protected. Original facade exception object identity changes deliberately.

### 5. Public mapping and observer failures

Type is unit, in `DefaultSkillTemplateTest`. Use the injected mapper and throwing observer for four situations. Mapping fails after execution success, observer fails after success, mapping fails after execution failure, and observer fails after execution failure. The first pair produces a primary session-bearing public exception with the requested failure as cause. The second pair retains the execution failure as primary and exposes the secondary failure in the suppressed evidence. Assert identity survives even when no observer callback can be delivered. Assert observer calls remain at most one and occur after binding restoration while root ownership is retained.

Affected surfaces are Application API and Internal implementation. Update the obsolete unchanged-observer-exception expectation under the intentional immutable failure contract.

### 6. Identity allocation, initialization and completion faults

Type is focused unit integration through the real runner and facade. Reuse existing `LoomspanSessionRunnerTest` observation/trace factory and controllable handle patterns, with a facade test helper where needed. Capture the identity-start notification and assert it equals the ID passed to observation/trace factories and the constructed session. Inject an observation or trace factory runtime failure after root UUID allocation, trace finalization failure with unavailable journal, and generation-owner release runtime failure after completion. Public failures must carry the allocated identity even when root completion or public mapping cannot succeed. Pre-allocation admission rejection leaves identity absent. Continue asserting resource release and completed binding restoration.

Keep JVM `Error` tests passing with original propagation. The plan defines these fatal failures outside the Java exception metadata contract. Do not add a new fatal-error wrapper API.

Affected surface is Internal implementation with Application API failure guarantees. Direct fault injection at existing seams avoids introducing production failure hooks solely for tests.

### 7. Concurrent reused exceptions and selected diagnosis

Type is integration, extending `ExecutorOutputSchemaIntegrationTest#publicFailureSnapshotsIgnoreReusedOrApplicationSuppliedDiagnosis`. Throw the same exception in at least two concurrent roots, including an exception returned from an earlier root and an application-created exception carrying a diagnosis. Capture each root ID in executor/session evidence. Assert each public failure has its own matching root identity and current selected diagnosis, both root IDs differ, and the original exception's identity/diagnosis remain unchanged. Retain cause linkage to the shared exception. Extend the existing recoverable-instance case with immutable snapshot expectations while retaining exact classification and reason.

Use virtual threads, existing latches/futures and deterministic executor fakes. Avoid timing-only assertions. Affected surfaces are Application API and Internal implementation. Per-invocation isolation is the protected behavior.

### 8. Pre-session null identity and admission lifecycle

Type is existing unit/integration extensions in `DefaultSkillTemplateTest`, `SkillReachableAdmissionTest`, `SkillGenerationExecutionIntegrationTest` and `FrameworkShutdownIntegrationTest`. Assert null `SkillException.getSessionId()` for invalid input during direct preparation and handoff, missing/invalid target, rejected nested root, released/reused admitted handle, closed admission and shutdown cutoff before execution. Validate-only calls still create no session and invoke no observer. Entry access denial still prevents execution. Keep `generationId()` available with its existing stability through release and failure. For any deliberately supplied foreign session-bearing exception reaching a pre-session failure path, assert it cannot masquerade as this invocation's root.

Affected surfaces are Application API and Internal implementation. Existing admission concurrency, authorization and generation contracts remain protected.

### 9. Canonical trace, view and retention correlation

Type is focused integration using existing real runner/trace patterns and temporary directories. Cover direct and admitted execution with observer enabled and disabled, success and failure, across `ALWAYS`, `ONERROR` and `NEVER`. Keep the matrix compact through parameterization and reuse. When retained records exist, compare the caller identity to canonical record-envelope identity and `TRACE_STARTED` session identity. Compare delivered view identity wherever mapping succeeds. With retention deleting the file, use the identity captured by the authoritative trace factory or execution session and assert the caller still receives it. Assert persistence still follows its existing success/failure policy.

Use real trace handles and readers, deterministic clocks where existing helpers provide them, and filesystem state confined to the test temporary directory. Existing fixture corpus tests supply the protocol regression coverage. Affected surface is Ephemeral diagnostics plus the Application API correlation contract.

## Authoring claims requiring evidence

| Claim in updated guidance | Executable evidence |
| --- | --- |
| Both public entry paths return exact text with a root session ID | Success matrix, exact API value tests and supported-surface integration. |
| Failure identity is nullable before session allocation and stable afterward | Pre-session cases and fault-injected runtime/completion cases. |
| A post-session denial exposes identity and remains a Spring denial | Public denial shape and real Java authorization integration. |
| A caller can obtain identity without public observer allocation | No-mapper-interaction tests. |
| Trace retention and view availability do not control caller identity | Trace/retention matrix and mapping/finalization failures. |
| Reused exceptions do not carry foreign invocation metadata | Concurrent reuse integration and selected-diagnosis assertions. |
| Java result conversion remains exact | Existing Java string/null/JSON cases adapted to `.text()`. |

Documentation drift classification is **aligned** before implementation. After edits, compare each exact claim to the implemented path and focused passing tests. Update routing/coverage entries with stable anchors. No new test should merely assert prose text.

## How to run

Maven 3.9.6 uses Java 21.0.2 through the existing `JAVA_HOME` in this workspace. Bare `java` is absent from PATH, but `mvn` succeeds. The POM runs integration-named JUnit classes through Surefire without a separate integration profile. Focused tests use local fakes or mock HTTP endpoints and require no external provider credentials.

1. Run the focused pre-change reflection test and record the expected assertion failure.
2. Run `mvn -q -DskipTests test-compile` after the contract and caller update.
3. Run `mvn -q "-Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,LoomspanSessionRunnerTest" test`.
4. Run `mvn -q "-Dtest=SupportedSurfaceIntegrationTest,ExplicitJavaFailureHandlingIntegrationTest,ExecutorOutputSchemaIntegrationTest,SkillGenerationExecutionIntegrationTest,SkillReachableAdmissionTest,FrameworkShutdownIntegrationTest,NamespaceObservationIntegrationTest,ConsoleTraceFixtureCorpusTest" test`.
5. Run `mvn -q test` after implementation and documentation are complete. This covers the broad mechanical root caller changes and required architecture test. Investigate failures using Surefire reports and preserve exact command/result evidence.
6. Run `git diff --check` and search current source/docs/examples for obsolete public String-returning invocation contracts and unadapted text consumers. Historical research and plans describe their original checkout and may retain baseline signatures.

## Acceptance mapping and exit criteria

| Ticket acceptance criterion | Required verification |
| --- | --- |
| Every successful template/admitted invocation returns original text and a non-null root String ID | Exact API shape/value tests, every-overload success matrix and supported-surface execution. |
| Post-session failures expose the same ID, with nullable pre-session SkillException identity and Spring-compatible denial | Runtime/subtype tests, pre-session lifecycle tests, concurrent reuse and completion/initialization fault tests. |
| Observer presence has no effect on availability, no-observer avoids public mapping, mapping failure retains ID | Mapper bypass assertions, callback/mapping failure matrix and observer identity comparison. |
| Caller, canonical trace and delivered view agree independently of trace retention | Real trace/retention matrix and existing diagnostic coherence suites. |

- [x] The initial reflection test fails against the baseline and passes after implementation.
- [x] The exact new supported surface is covered and the architecture test passes.
- [x] All six entry shapes and each affected failure boundary have sufficient executable coverage.
- [x] No-observer public mapping bypass is proven with a mapper mock.
- [x] Reused exceptions retain their original metadata and each concurrent caller receives the right identity.
- [x] Selected diagnosis, designated messages/subtypes, security behavior, ownership and JVM Error propagation remain covered.
- [x] Every current caller compiles and the complete Maven suite passes.
- [x] Current API guidance and examples match the implemented contract with source/test anchors and updated coverage inventories.
- [x] `git diff --check` passes and all required verification is recorded accurately.

## Optional developer checks

None. The ticket's Framework acceptance criteria are executable in this checkout. Cloud response design and reviewer lookup belong to the separate work identified by the ticket.
