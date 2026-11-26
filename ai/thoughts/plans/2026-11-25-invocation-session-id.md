# Invocation session ID implementation plan

## Overview

Return the root execution identity with every successful public invocation and expose it directly on failures after identity allocation. Applications can correlate a response or failure with retained diagnostics without registering an execution-view observer.

This is Step 2 of the approved Full 5-Step Pipeline. The controlling ticket is `ai/thoughts/tickets/invocation-session-id.md`. The developer approved the full route with “proceed”. Initial scope attribution is `main` at `0a9fadb0b4b96feafabf643b68b336b8518b6829`, with the ticket as the sole untracked file and no tracked changes. Research and these plans are pipeline artifacts. Preserve any subsequently discovered unrelated developer work.

## Current state analysis

`SkillTemplate` is implemented by four text-returning overloads at `DefaultSkillTemplate.java:93`. Both admitted invocation methods converge on its `invokePrepared` method through `DefaultSkillInvocationHandoff.java:99`. `LoomspanSessionRunner.java:320` allocates a UUID for each executed root and passes it into `LoomspanSession`, whose constructor passes the same value to observation and trace creation. The root completion callback already receives the session on execution failure and success. Public view mapping is guarded by `observer != null` at `DefaultSkillTemplate.java:258`.

The current facade preserves some exception identities and designated subtypes. It also lets successful-execution observer failures escape unchanged through `CompletionPhaseFailure`. `SkillException` carries an immutable selected contract diagnosis, but has no session identity. `ExecutorOutputSchemaIntegrationTest#publicFailureSnapshotsIgnoreReusedOrApplicationSuppliedDiagnosis` demonstrates concurrent reuse of old exceptions and protects against foreign metadata becoming the current invocation's diagnosis. Metadata for the new contract must have the same isolation.

Observation/trace initialization can fail during session construction. Ownership cleanup occurs outside the completion callback. Capturing identity solely in the action or completion callback would leave such paths uncovered. The selected terminal diagnosis still belongs to the finalized session and must continue to come from `session.terminalDiagnosis()`.

## Desired end state

- All four `SkillTemplate.invoke` methods and both `AdmittedSkillInvocation.invoke` methods return `SkillInvocationResult`.
- `SkillInvocationResult` is an immutable public record in `ai.loomspan.api`, with exactly `String text` and `String sessionId`. Require non-null text and a non-null, nonblank session ID. Preserve the original text exactly, including whitespace and JSON. Existing Java null/void conversion remains the producer's empty-text behavior.
- `SkillException.getSessionId()` returns a nullable `String`. Exceptions constructed by applications or produced before root identity allocation have null identity. A public invocation failure after allocation carries the current root identity.
- A new public `SkillAccessDeniedException` extends Spring's `AccessDeniedException` and exposes `String getSessionId()`. A post-session access denial uses this subtype and retains its original denial as the cause. Entry denial before identity allocation preserves the current Spring failure behavior.
- Results, public exceptions, canonical trace identity and any delivered completed view agree. An identifier does not imply that a trace was retained or grant permission to read it.
- The no-observer path allocates the small result value without calling `SkillExecutionViewMapper` or converting finalized journal entries to public events.

### Key discoveries

- The root runner is the authoritative identity allocator, independent of public view construction (`LoomspanSessionRunner.java:297`).
- Entry preparation, authorization, handoff reservation and single-use rejection occur before root identity allocation (`DefaultSkillTemplate.java:121`, `DefaultSkillInvocationHandoff.java:40`).
- Root completion selects diagnosis before mapping the observer view (`DefaultSkillTemplate.java:258`).
- Canonical traces and observations already share `LoomspanSession.getSessionId()` (`LoomspanSession.java:94`, `DefaultExecutionTraceHandle.java:90`).
- Public surface classification is closed and executable (`LoomspanPublicSurfaceArchitectureTest.java:29`).

## Scope boundaries

The implementation covers Framework's Java invocation contract and in-repository consumers and current documentation. Cloud response design and reviewer lookup remain separate ticket work. Existing trace access, retention, canonical formats, Console protocols, input/output validation, authorization decisions, admission concurrency and generation ownership continue to be exercised by their existing tests. Internal skill and executor return values keep their existing text contract. Existing `ExecutionJournalProjector` behavior remains governed by the repository's supported exception. Content fidelity applies to result text and diagnostic content.

## Skill-Authoring Documentation Impact

**Impact — Affected.** Root invocation examples, caller failure handling and trace correlation change. Manifest syntax and skill-produced result conversion retain their existing meaning.

Use the repository-local `agent-skills/loomspan-docs/SKILL.md` as the documentation router after source inspection. Its `1.0.0-beta.10-SNAPSHOT` version matches the checkout. The current String signatures, observer failure handling and Spring exception identity described by the guidance agree with current source and tests. Drift classification is **aligned** before implementation. Update those descriptions to the implemented contract atomically.

Documents to update are `README.md`, `docs/quickstart-java.md`, `docs/quickstart-executors.md`, and the following skill references under `agent-skills/loomspan-docs/references/`.

- `java-api/README.md` and `java-api/compatibility-and-boundaries.md` add the two deliberately supported types and adjust the exact surface count from thirty to thirty-two.
- `java-api/invocation.md` updates signatures, result handling, admission examples, optional mapping and lifecycle ordering.
- `java-api/observation-and-errors.md` documents nullable pre-session identity, session-bearing exception copies, access-denied handling, observer/mapping failure precedence, constructor shapes and retention independence.
- `java-api/java-skills.md` distinguishes the public result value from the existing skill-produced text semantics wherever root invocation is described.
- `skill-authoring/input-contracts.md` updates affected root invocation examples.
- `skill-authoring/traces-and-debugging.md` adds a short route from caller identity to existing diagnostic identity and access/retention rules.
- `skill-authoring/README.md` updates the Java results, input contracts and traces/debugging coverage entries with the new focused tests. Update Java API coverage entries as well.

Evidence will be `ApplicationApiValueTest`, `DefaultSkillTemplateTest`, `SupportedSurfaceIntegrationTest`, `ExecutorOutputSchemaIntegrationTest`, `ExplicitJavaFailureHandlingIntegrationTest` and focused root-runner identity tests. Keep the routed topics self-contained, use stable class/test anchors, and describe the current contract without ticket history or migration prose. Search for additional current invoke signatures or examples and update them according to their actual role.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned treatment |
| --- | --- | --- |
| Application API | Six invocation return signatures, `SkillException` metadata, new result and denial types. Existing allowlist, README and supported-surface tests deliberately support these entry points. | Intentional source and binary break authorized by ticket. Update all callers and supported-surface tests together. Preserve designated failure semantics, exact messages and current terminal diagnosis selection. |
| Supported SPI | Repository policy exposes no Java SPI or bean-replacement contract. | Keep internal hooks and factories internal. |
| Configuration and manifest contracts | Existing persistence policy affects whether matching evidence is retained. | Keep keys, defaults and existing authoring semantics coherent. Exercise retention combinations. |
| Persisted or serialized contracts | Invocation values are in-process Java values. Canonical identity already exists. | Existing trace records and exact compatibility marker continue to use current formats. |
| Ephemeral diagnostic formats | Caller identity must match canonical records and any observer view, including failed invocations. | Reuse the existing identity. Preserve writer, reader and projection coherence and content fidelity. |
| Internal or accidentally exposed implementation | Facades, runner completion plumbing and mapper seams change. Public modifiers do not establish support. | Change internal signatures and remove newly obsolete plumbing where the new flow makes it unnecessary. |

The public-surface delta is `SkillInvocationResult`, `SkillAccessDeniedException`, six changed invocation return types, nullable `SkillException.getSessionId()`, and the narrowly required immutable exception construction shapes. Deliberately add the two new types to the architecture allowlist. Supported public signatures expose only JDK, existing supported values and Spring's established exception superclass. Introduce no Spring replacement point.

**Shim decision — No shim.** The ticket explicitly authorizes changing the invocation contract directly. Add no text-only facade, legacy return signature, migration document or transition adapter. Existing exception constructors remain useful application construction paths and initialize the new field to null, so retaining them is ordinary behavior.

**Java-to-Go boundary coordination — Not required.** The caller result and exception metadata are Java-only. Existing REST/SSE, acquisition, problem and consumed NDJSON boundaries retain their schemas and compatibility marker. Existing Java fixture tests still exercise current diagnostic coherence.

**Pipeline notes alignment — Aligned.** Changes to exception identity and successful-root observer exception wrapping are necessary consequences of returning immutable per-invocation metadata. Protected application behavior includes catchability, designated classifications, exact authored reasons, cause/suppressed evidence, terminal diagnosis and authorization decisions. Update tests that assert the obsolete facade exception identity or text-only return contract.

## Implementation approach and material decisions

1. Reuse the UUID allocated by the root runner. Extend internal `RootCompletion` with a default identity-start notification, keeping it a functional interface for existing internal lambda callers. Notify immediately after root execution is claimed and the UUID is allocated, before constructing observation/trace collaborators. The facade uses a per-call holder to capture that value. This makes constructor-time failures after identity allocation identifiable and leaves preparation/admission rejection null. The UUID passed to notification must be exactly the one passed to `LoomspanSession`.
2. Keep final diagnosis selection in the existing completion boundary, before public mapping. Completion returns `new SkillInvocationResult(text, sessionId)` on success and still invokes the observer at most once when requested. The holder must already contain identity before optional mapping or observer work. Ownership-release exceptions after completion also flow through the same facade failure handling.
3. Use immutable per-invocation exception snapshots. Add a final nullable session field to `SkillException`. Keep existing constructors null-initializing and add a full constructor accepting message, cause, selected diagnosis and nullable session ID. Add the minimal session-aware construction paths required for `RecoverableSkillException`, `PlannerInabilityException` and `SkillInputValidationException` copies. Preserve exact messages, validation issues and designated built-in subtype where applicable. Arbitrary application subclasses remain available as the cause of a `SkillException` snapshot. Do not add a public metadata setter, mutate reused exceptions or infer current identity from an exception supplied by application code.
4. At the public boundary, wrap post-session access denials with the new immutable denial subtype. For other runtime failures, construct a session-bearing `SkillException` or its designated built-in subtype. Preserve the original failure as the cause and copy relevant suppressed exceptions onto the public snapshot so observer/finalization evidence remains directly available. Select diagnosis exclusively from the current session. For a pre-session failure, metadata must be null even when an application or reused exception carries foreign identity.
5. Successful-root observer or mapper runtime failures become the primary session-bearing public failure. Their original runtime exception remains the cause. When execution already failed, the execution failure remains primary and mapping/observer failure stays suppressed. Remove or simplify `CompletionPhaseFailure` only where repository-wide callers show it no longer serves a required distinction. Internal consumers do not require compatibility shims.
6. Preserve the existing JVM `Error` propagation boundary. Java `Error` lies outside the ticket's exception getter contract and is already explicitly tested by `DefaultSkillTemplateTest#doesNotCatchError`. Session metadata applies to application-facing exceptions. Keep this limitation explicit in API failure documentation. A retained trace still records failures according to existing runner behavior.

The new metadata is trusted execution identity owned by the runner, stored immutably in each returned value or public failure. The model never supplies it. A public result value is necessary because success currently exposes only text, and an access-denied subtype is necessary to retain Spring catchability while adding a direct getter. Immutable copies avoid races when the same application exception is thrown in concurrent roots. The small per-call holder is local to execution and does not become shared application state.

## Phase 1 — Public values and identity flow

### Changes required

- Add `src/main/java/ai/loomspan/api/SkillInvocationResult.java` and `SkillAccessDeniedException.java` with narrow documented construction and value validation.
- Change `SkillTemplate` and `AdmittedSkillInvocation` signatures and Javadocs.
- Add immutable session metadata construction to `SkillException` and the required built-in subclasses while preserving their message, cause and issue semantics.
- Update `DefaultSkillTemplate` and `DefaultSkillInvocationHandoff` return types and root completion flow.
- Update internal `LoomspanSessionRunner.RootCompletion` and the identity allocation site so identity is captured before optional initialization and completion work.
- Update exact API-shape assertions, add both new API types to the closed allowlist, and update affected test return-value uses to keep the project compilable. Use `.text()` wherever the caller intends text. Use the result itself only where it inspects identity.

### Automated success criteria

- [x] `mvn -q -DskipTests test-compile` passes with all in-repository callers adapted.
- [x] `mvn -q "-Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,LoomspanSessionRunnerTest" test` passes.
- [x] API tests cover exact return shapes, value validation, nullable exception identity and constructor semantics.
- [x] No-observer tests verify no mapper interaction while receiving the root identity.

## Phase 2 — Failure isolation and complete behavioral verification

### Changes required

- Add focused direct/admitted success and failure matrices to existing facade and supported-surface tests.
- Extend concurrent reused-exception coverage with distinct root IDs and unchanged source exception metadata.
- Exercise post-session access denial, designated failure types, successful-root observer/mapping failure, failure-side suppression, initialization/finalization failures and ownership-release failure with fault injection at existing seams.
- Exercise trace retention `ALWAYS`, `ONERROR` and `NEVER` while comparing caller identity with canonical trace data or captured trace-factory identity and any delivered view.
- Preserve pre-session invalid input, missing target, entry denial, handoff rejection, release/reuse/cutoff and nested-root rejection behavior. `SkillException` outcomes have null identity for these paths.
- Run the full Maven test suite for the broad caller update and existing lifecycle, authorization, projection and Console-fixture regressions.

### Automated success criteria

- [x] All acceptance criteria map to passing executable checks in the dedicated testing plan.
- [x] `mvn -q "-Dtest=SupportedSurfaceIntegrationTest,ExplicitJavaFailureHandlingIntegrationTest,ExecutorOutputSchemaIntegrationTest,SkillGenerationExecutionIntegrationTest,SkillReachableAdmissionTest,FrameworkShutdownIntegrationTest,NamespaceObservationIntegrationTest,ConsoleTraceFixtureCorpusTest" test` passes.
- [x] `mvn -q test` passes, including the mandatory public-surface architecture test.
- [x] Reused exceptions never supply or receive another invocation's identity or selected diagnosis.

## Phase 3 — Current API documentation and completion record

### Changes required

Update every document identified under Skill-Authoring Documentation Impact and additional current examples found by the caller/signature search. Prefer complete minimal examples that read `result.text()` and `result.sessionId()`. Document direct nullable failure identity, the denial subtype and observer behavior with stable test/source evidence. Update the exact supported-type inventories and coverage tables. Mark plan/testing progress and ticket acceptance criteria according to actual verification.

### Automated success criteria

- [x] Searches across `README.md`, `docs`, `agent-skills`, production sources and tests find no obsolete public text-returning invocation signatures or text-only call sites.
- [x] Updated exact claims are supported by the implementation and focused tests, with drift classification **aligned** after the change.
- [x] `git diff --check` passes.
- [x] The implementation report records the exact passing and failed commands and any environmental limitations.

## Testing strategy

The companion `2026-11-25-invocation-session-id-testing.md` owns the complete scenarios, failure-first checks and exit criteria. Reuse the existing unit and integration fixtures and local mock provider servers. New tests target missing identity and isolation guarantees, while existing assertions are adapted to the deliberately changed return contract.

## Performance considerations

No-observer invocation creates a two-field result and per-call identity bookkeeping. Public event conversion remains conditional on an observer. Internal journal finalization follows the existing lifecycle. Identity availability must not depend on reading or retaining trace files.

## Technical debt and alternatives considered

An observer-only solution retains mandatory public view construction and loses identity when that construction fails. Mutable metadata on the original exception fails concurrent reuse. A separate trace lookup or new identifier duplicates an existing authoritative root identity. The chosen value and immutable exceptions fit the existing public facade. `CompletionPhaseFailure` currently distinguishes observer errors after success and should be narrowed or removed if the new boundary no longer needs it. Research identified no additional dead production concept requiring expansion of this ticket.

## References

- Ticket `ai/thoughts/tickets/invocation-session-id.md`.
- Research `ai/thoughts/research/2026-11-25-invocation-session-id.md`.
- Repository policy `AGENTS.md` and `ai/thoughts/framework-feature-design-lens.md`.
- Existing behavior `DefaultSkillTemplate`, `DefaultSkillInvocationHandoff`, `LoomspanSessionRunner`, `LoomspanSession` and `SkillExecutionViewMapper`.
- Existing regression patterns `ApplicationApiValueTest`, `DefaultSkillTemplateTest`, `ExecutorOutputSchemaIntegrationTest` and `LoomspanSessionRunnerTest`.

## Implementation execution notes

The implementation uses the planned result and denial types, immutable per-invocation exception metadata, and the runner-owned identity notification. The existing `CompletionPhaseFailure` distinction became unnecessary when completion runtime failures entered the same session-bearing public failure boundary, so its declaration and use were removed.

Integration verification showed that nested root execution was previously rejected by the binding scope after constructing a fresh session. The runner now checks the existing nesting precondition inside claimed-root cleanup and before identity allocation. It retains the existing rejection message, completes claimed ownership through the existing `finally`, and gives rejected nested calls the planned null session identity. Validation and handoff preparation inside an execution remain allowed.

Maven's Mockito self-attachment stalled under Windows pipe restrictions. Successful test runs use the existing local Mockito jar as a startup agent through `-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar`. This command-line setting supplies the verification environment. Exact commands and outcomes are recorded in the implementation verification artifact.

Current authoring guidance was updated through the repository-local `loomspan-docs` router. The implemented API, root identity flow, optional mapping, failure precedence and retention behavior are the executable evidence for the updated claims. Documentation drift classification is **aligned**.
