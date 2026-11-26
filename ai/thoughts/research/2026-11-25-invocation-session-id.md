---
date: 2026-11-25T19:13:42-08:00
researcher: Codex (GPT-6.1-sol)
git_commit: 0a9fadb0b4b96feafabf643b68b336b8518b6829
branch: main
repository: loomspan-framework
topic: "Return the session ID from skill invocations"
tags: [research, codebase, application-api, invocation, session, exceptions]
status: complete
last_updated: 2026-11-25
last_updated_by: Codex (GPT-6.1-sol)
---

# Research — Return the session ID from skill invocations

## Research question and execution context

Document the existing public invocation contract, session ownership, completion and error paths, trace identity, tests, callers and documentation relevant to `ai/thoughts/tickets/invocation-session-id.md`.

This is Step 1 in pipeline mode under the Full 5-Step Pipeline. The developer approved that profile with “proceed”. At triage the branch was `main`, with no staged or unstaged tracked changes. The ticket was the sole untracked file. This research makes no production changes and runs no tests.

Metadata was collected with PowerShell and Git. The prescribed `bash ai/scripts/spec_metadata.sh` was attempted, but Windows Bash returned `Bash/E_ACCESSDENIED`. An explicit Git Bash retry also failed during MSYS initialization with `NtCreateDirectoryObject` access denied. The equivalent successful commands were `git rev-parse HEAD`, `git branch --show-current`, `Split-Path (Get-Location) -Leaf`, and conversion of `DateTimeOffset.UtcNow` to `Pacific Standard Time`. The author identity is the research agent because no thoughts-system identity was available.

## Summary

`SkillTemplate` has four `String`-returning invocation overloads. Both `AdmittedSkillInvocation.invoke` overloads return `String`. Their implementations converge on `DefaultSkillTemplate.invokePrepared`, which delegates root session ownership and execution to `LoomspanSessionRunner`. Each root constructs a `LoomspanSession` with a fresh UUID, and that session ID is passed to observation and canonical trace handles.

The facade's root-completion callback receives the completed session on success and execution failure. It selects terminal contract diagnosis, conditionally maps public execution history when an observer is present, and returns the text. Consequently the internal callback already sees session identity without requiring a public observer. Public event conversion is conditional on the observer, while internal journal projection occurs during session finalization for every root.

`SkillException` exposes optional contract diagnosis but has no session field. Access denials currently propagate as the same Spring exception. Runtime failures are otherwise wrapped or preserved according to their diagnosis. Observer or mapping runtime failures after success propagate unchanged through a special completion wrapper. Existing tests deliberately cover those behaviors, designated failure subtypes, concurrent exception reuse, pre-session rejection, retention, ownership and observer delivery.

## Public declarations and supported classification

The executable classification authority is `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29`. Its closed `API_TYPES` set contains thirty top-level types, including `SkillTemplate`, `SkillInvocationHandoff`, `AdmittedSkillInvocation`, `SkillException`, `SkillInputValidationException`, `RecoverableSkillException`, `PlannerInabilityException`, `SkillExecutionView`, and the diagnosis values. The test also enforces that public API signatures do not expose implementation or autoconfiguration types.

The applicable categories from `ai/thoughts/framework-feature-design-lens.md` are as follows.

| Category | Existing evidence relevant to this ticket |
| --- | --- |
| Application API | The allowlisted invocation interfaces, exceptions, observation values and diagnosis types are explicitly supported. README and the bundled Java API knowledge set describe them. |
| Supported SPI | The repository explicitly exposes no supported Java SPI or internal bean replacement surface. |
| Configuration and manifest contracts | Existing trace persistence policy and skill input/output semantics affect invocation behavior. The ticket does not request new properties, YAML syntax or authorization rules. |
| Persisted or serialized contracts | The ticket requires identity agreement with existing traces. Canonical traces remain same-version diagnostic artifacts, including their existing exact Console compatibility marker. The invocation return type is an in-process Java contract. |
| Ephemeral diagnostic formats | Canonical trace identity, derived journal and public completed observer views participate in correlation. Trace access, retention and existing projections are separate from the facade result. |
| Internal or accidentally exposed implementation | `DefaultSkillTemplate`, `DefaultSkillInvocationHandoff`, `LoomspanSessionRunner`, `LoomspanSession`, mappers, generation managers and Spring wiring are internal regardless of Java visibility. |

The ticket explicitly authorizes direct replacement of the invocation contract and updates to callers and current documentation without shims or migration documentation. It scopes Cloud response handling and reviewer lookup to separate work.

### Interfaces, constructors and value semantics

- `src/main/java/ai/loomspan/api/SkillTemplate.java:18` defines two `validate` operations and four `String invoke` operations, split by `Object`/`Map` input and optional `Consumer<SkillExecutionView>`.
- `src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:12` exposes stable `generationId()`, two invocation methods and `release()`. Generation ID belongs to the retained entry namespace generation and is separate from the session ID created upon execution.
- `src/main/java/ai/loomspan/api/SkillException.java:5` stores final nullable `SkillFailureDiagnosis`. It has message, message/cause and message/cause/diagnosis constructors and `Optional<SkillFailureDiagnosis> getFailureDiagnosis()`.
- `src/main/java/ai/loomspan/api/SkillInputValidationException.java:5` extends `SkillException`, preserving immutable validation issues. These failures can happen before root session construction.
- `src/main/java/ai/loomspan/api/RecoverableSkillException.java:6` extends `SkillException`. Its constructors require an exact nonblank message. `PlannerInabilityException` is its designated subtype and is caught explicitly by supported consumers and tests.
- `src/main/java/ai/loomspan/api/SkillExecutionView.java:6` is a record of `sessionId` and immutable `events`. Its constructor requires a non-null, nonblank session ID.

## Invocation and session flow

### Preparation, authorization and admission

`DefaultSkillTemplate` overloads at `src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java:93` converge on preparation followed by execution. Preparation captures the exact namespace/local-name target and reachable generations before object conversion. It validates input, then revalidates reachable admission. Preparation failures close the captured generation lease. Validation-only calls check authorization and release ownership without creating a session.

Direct execution obtains the calling authentication and performs entry authorization before calling the session runner (`DefaultSkillTemplate.java:220`). Failures in this region occur before a session exists. Access denials and `SkillException` instances are rethrown, other runtime failures become `SkillException`, and fatal errors release the lease and propagate.

`src/main/java/ai/loomspan/internal/skillapi/DefaultSkillInvocationHandoff.java:40` prepares input and authentication, checks entry authorization, and reserves a lifecycle root. Handoff itself creates no session. The admitted handle keeps an atomic payload and the captured generation ID. At `DefaultSkillInvocationHandoff.java:99`, invocation atomically claims that payload and calls the same template execution method with captured authentication and admitted root. Reuse, release or cutoff rejection raises `SkillException` before a new session is constructed.

The Spring factory at `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:294` creates `DefaultSkillTemplate`; the handoff factory at line 314 constructs `DefaultSkillInvocationHandoff` with that template and framework lifecycle. These factories are internal integration machinery. Their signatures do not establish an SPI.

### Root execution and completion

`src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:297` owns admitted root execution. It claims root execution, chooses the captured generation's runtime/trace policy, and constructs `LoomspanSession` with `UUID.randomUUID().toString()` at line 320. Session construction precedes binding and skill execution.

`src/main/java/ai/loomspan/internal/core/LoomspanSession.java:94` validates and stores the ID in a final field. Observation handle creation receives that exact ID at line 113 and trace handle creation receives it at line 118. `getSessionId()` returns the field at line 146. Trace or observation handle initialization can itself fail before the session constructor returns.

Execution runs within `ExecutionBindingScope.supplyWith`. Its `finally` calls `completeSession(session, failure)`. After scope restoration, the root runner invokes `RootCompletion.complete(result, session, null)` on success. For a runtime failure or fatal error in execution/finalization, it calls `complete(null, session, failure)`, suppresses any callback failure onto the original throwable, and rethrows the original. A runtime callback failure after success is wrapped in the internal `CompletionPhaseFailure`. Root execution ownership is completed in the outer `finally` at line 362. Generation-owner closure also has an outer cleanup path for runners without framework lifecycle.

`DefaultSkillTemplate.java:248` supplies the root action and completion callback. The action executes the router and applies `String.valueOf(result)` at line 297. The callback at line 258 selects `session.terminalDiagnosis()` before invoking `executionViewMapper.map(session)` and the observer, then returns the text. The `observer != null` guard controls all public view/event construction here.

### Existing failure routing

`DefaultSkillTemplate.java:270` unwraps a successful-root completion failure and throws its original runtime exception unchanged. The next catch preserves any `AccessDeniedException`. Other runtime failures preserve an existing `SkillException` only when its diagnosis equals the selected session diagnosis. Otherwise the facade constructs a `SkillException` with the original cause and selected diagnosis. The facade does not currently attach root identity to any of these exceptions.

The distinction between current callback failures and execution failures is observable. On failure, mapping or observer exceptions are secondary suppressed failures. After successful skill execution, mapping or observer failures are the invocation's primary failure. Existing JVM errors propagate through the facade without conversion.

## Journal, public view and trace identity

`src/main/java/ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java:32` reads the finalized journal and maps it using `session.getSessionId()`. Each journal entry becomes a public `SkillExecutionEvent`, with JSON payload conversion into map/list/scalar values. The full public view is allocated only from the conditional observer path described above.

`LoomspanSession.java:616` finalizes the canonical trace and projects the derived journal before trace finalization. It stores `finalizedExecutionJournal` only after successful projection and finalization. Failures retain existing failure-recording and observation-completion behavior. An absent finalized journal causes public mapping to throw an `IllegalStateException`. Internal projection therefore remains a distinct operation from public event conversion.

`src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:90` stores the same session ID and includes it in the diagnostic filename. Initialization at line 208 emits `TRACE_STARTED` with `sessionId` in its data. Record envelopes also carry the handle's identity. `shouldDeleteAfterCompletion()` at line 556 deletes under `NEVER`, deletes nonerrored traces under `ONERROR`, and retains under `ALWAYS`. The live `LoomspanSession` field and completed observer value do not require a retained trace file.

The trace ID is separately generated and stored by the trace handle. Neither trace ID nor generation ID is the invocation session ID.

The repository's existing `ExecutionJournalProjector` field-name redaction exception applies to the derived journal and observer events. Canonical records and returned result text have separate fidelity guarantees under root `AGENTS.md`. Research found no ticket requirement to change that projection or introduce content rewriting.

## Tests, fixtures and verified usage

### Contract and facade tests

- `src/test/java/ai/loomspan/api/ApplicationApiValueTest.java:20` reflects all root overloads and asserts their exact return types. Its admitted shape test at line 89 also asserts exact methods. Its tests at line 267 enumerate `SkillException` constructors and protect diagnosis and message-constructor `initCause` semantics.
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java:29` owns the public allowlist. Root instructions require running this test after production type changes.
- `src/test/java/ai/loomspan/internal/skillapi/DefaultSkillTemplateTest.java:240` preserves access-denied exception identity today. The next test preserves existing `SkillException` identity.
- The same facade test at line 264 verifies one available observer history on execution failure. Tests at lines 380 and 400 verify observer/mapping failure suppression. Tests near line 536 verify input failures, exact success text, observer session IDs, object overload conversion, and observer exception propagation after successful execution. Package-private constructor injection already allows a mocked `SkillExecutionViewMapper`.
- `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java` protects handoff capture and generation replacement semantics. It has textual result assertions for admitted calls.
- `src/test/java/ai/loomspan/internal/skill/SkillReachableAdmissionTest.java` protects exact root identity pairs, reachable capture and pre-session rejection of nested root execution while allowing validation and handoff preparation.

### Integration and lifecycle evidence

- `src/test/java/ai/loomspan/integration/SupportedSurfaceIntegrationTest.java:43` invokes actual application Java skills and YAML through the supported facade. It protects exact text/null conversion, failure subtypes, input diagnosis, admitted execution, observers, and access denials.
- `src/test/java/ai/loomspan/integration/ExplicitJavaFailureHandlingIntegrationTest.java:159` covers direct/admitted planner inability and observer precedence. Several tests assert exact designated exception classes and exact authored reasons.
- `src/test/java/ai/loomspan/integration/ExecutorOutputSchemaIntegrationTest.java:214` concurrently reuses exception objects from prior invocations and application-supplied diagnosis. It ensures a foreign/reused exception cannot supply a terminal diagnosis for a different invocation. A later test preserves `RecoverableSkillException` identity. This is existing evidence relevant to per-invocation metadata and mutable exception reuse.
- `src/test/java/ai/loomspan/internal/core/LoomspanSessionRunnerTest.java` covers fresh session identity and isolation, `ALWAYS`/`ONERROR`/`NEVER`, complete traces, observation completion containment, write/finalization failure and ownership.
- `src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:48` covers pending handoff execution during shutdown, admission rejection before session construction, and ownership release behavior.
- `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest` cover coordinated trace and observer identities and existing Console consumers. The ticket changes the Java caller contract, while Console REST/SSE, problem, acquisition and NDJSON schemas remain existing diagnostic boundaries.

### Call sites and sample inventory

Production Java usages of the public invocation implementations are concentrated in the two facade implementation files. Internal `JavaSkillInvoker`, tool callbacks and coordinator methods have distinct invocation contracts and continue to produce skill text within execution.

Tests throughout `src/test/java/ai/loomspan/integration` use the facade directly, including Java, executor, schema, binding, authorization, namespace and observation scenarios. Some capture a `String`, some use AssertJ string assertions, and some ignore results to inspect side effects or exceptions. Broad Java test compilation is executable coverage for this caller inventory.

Current runnable sample snippets include `README.md:94`, `README.md:200`, `docs/quickstart-java.md:131`, and `docs/quickstart-executors.md:126`. The Java quickstart prints the invoke result directly. The executor quickstart assigns both results to `String` values.

## Documentation alignment and skill-authoring impact

The repository-local `agent-skills/loomspan-docs/SKILL.md` was consulted as the router after source and focused test investigation. Its declared version is `1.0.0-beta.10-SNAPSHOT`, matching the checkout's Maven project version. Relevant Java API topics were `references/java-api/README.md`, `compatibility-and-boundaries.md`, `invocation.md`, and `observation-and-errors.md`. The skill-authoring index and source-verification protocol were also consulted, with a focused scan of trace/debugging references.

**Drift classification — aligned.** Current invocation guidance documents `String` return values, unchanged observer runtime exceptions after success, original Spring access-denied propagation, optional diagnosis and thirty public types. Matching signatures and focused tests demonstrate those existing behaviors. The requested ticket introduces new behavior, so those current statements identify documentation that participates in the change.

`agent-skills/loomspan-docs/references/java-api/invocation.md` contains all four signatures, text-return examples, lifecycle ordering, handoff usage and application mock guidance. `observation-and-errors.md` defines error taxonomy, constructor contracts, observer behavior, session identity in views, and trace retention independence. `compatibility-and-boundaries.md`, the Java API index and README enumerate the closed public surface.

The application-facing API change also touches skill-author guidance wherever an example invokes a root or reads the result, including `references/skill-authoring/input-contracts.md`, and Java result guidance in `references/java-api/java-skills.md`. Internal skill output semantics remain represented as text independently of the application result wrapper. Trace debugging still relies on the existing root session identity and access/retention policy. No new manifest syntax, planning semantics, schema semantics or trace format is requested.

## Architecture and historical context

The architecture has one root lifecycle and one root session per actual execution, shared between direct and admitted public entry paths. Admission captures generation ownership and authentication earlier than session construction. Canonical trace completion, internal journal projection and public view mapping are separate stages. Public completed observers are optional consumers of session history.

The checkout has no prior research or implementation plan for this ticket. `ai/thoughts/framework-feature-design-lens.md` supplies the current feature and compatibility policy. Other files under `ai/thoughts/future/` describe separate future topics and are not execution evidence for invocation identity.

## Open questions for implementation planning

1. Choose the result value type, the supported access-denied subtype and their exact public shapes, then account for closed-surface and documentation inventories.
2. Define per-invocation session metadata attachment for existing `SkillException` subtypes and reused exception objects while preserving the ticket's direct getter and existing selected-diagnosis semantics. Existing exact subtype/identity tests provide the baseline to assess against the intentional contract change.
3. Map all post-session runtime exits, including requested view construction, observer failures, finalization and cleanup, to the session-bearing public failure contract. Existing fatal-error propagation and constructor-time failures need an explicit planning interpretation of session creation and exception scope.
4. Select focused identity/retention and public-mapper bypass verification, plus the breadth of existing callers to compile and run. Maven requires Java 21 or later and has a Surefire test configuration without a separate integration-test profile in `pom.xml`.

These are technical design details within the approved ticket scope. No unresolved product choice currently requires developer input.

The orchestrator reported a successful `mvn -version` check with Maven 3.9.6 and Java 21.0.2 from `JAVA_HOME=c:\hamdev\jbrsdk21`. Bare `java` is absent from `PATH`. Research did not run test commands.
