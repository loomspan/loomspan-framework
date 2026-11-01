---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Observation Values and Facade Errors

## Completed Execution View

Pass a `Consumer<SkillExecutionView>` to an observer overload of
`SkillTemplate.invoke` when application code needs a completed-session view.

`SkillExecutionView` is a record with:

```java
String sessionId;
List<SkillExecutionEvent> events;
```

The session ID is non-null and nonblank. The event list is immutable and is
defensively copied; a null list normalizes to an empty list.

An observer is called at most once with available completed history after success or a
post-session execution failure. Pre-session rejection and validation-only calls produce no
callback. Delivery is synchronous on the caller after execution binding restoration and
before the same admitted root is released. History is not guaranteed when session
finalization or mapping fails and does not depend on Console storage or trace persistence.
The same timing applies to `AdmittedSkillInvocation.invoke(observer)`; an
explicit release racing a claimed execution cannot end root ownership early.

Java and model-backed YAML roots all produce a skill mission and terminal outcome. Java
execution does not create a model interaction merely for observation. Existing
canonical mission-frame records project into `SKILL_STARTED` and `SKILL_FINISHED`
events for direct Java execution, so a Java-only root has a meaningful public observer
view even without model or tool events.

## Captured namespace identity

An invocation produces one session, canonical trace/event sequence and completed
observer view across its captured namespaces. Semantic skill references in events,
lookups and displays are complete `namespace-localName` strings, preserving local
case. Do not shorten them or reinterpret model/planning operation routes as skills.
Unknown-origin diagnostics retain their existing null/unavailable identity.

Canonical `TRACE_STARTED` metadata and existing live/finalized trace metadata carry
`entryNamespace`, entry-scoped `generationId`, and `namespaceGenerationIds`, a
snapshot map of all admitted reachable namespaces to their captured generation IDs.
Known record owner facts use `executingNamespace` and `executingGenerationId` where
execution context is represented. The map and record facts describe retained
execution, even after current publication changes or removal/recreation. They are
same-release diagnostics, not an additional supported Java snapshot API.

`AdmittedSkillInvocation.generationId()` identifies the entry generation;
`SkillExecutorInvocation.executorConfigGenerationId()` identifies the captured executor namespace generation, which can differ from both the entry generation and the executing YAML leaf's owner. Public skill-call
visibility grants neither observation nor configuration access. Host catalog
metadata remains unfiltered under existing observation authorization, while
runtime discovery enforces execution permissions.

Console requires the same exact release marker for live connections and saved
trace imports. `consoleCompatibilityVersion` comes from filtered
`META-INF/loomspan-release.properties` using Maven `${project.version}`. A missing
or different resolved release rejects. Matching `development` allows ordinary
complete validation but promises no compatibility between development builds.
There is no legacy reader, alternate counter, trace migration or historical
readability promise. See [HTTP diagnostics](../skill-authoring/observability-http.md)
and [trace interpretation](../skill-authoring/traces-and-debugging.md).

## Execution Events

`SkillExecutionEvent` is a current-version diagnostic value with:

| Component | Contract |
| --- | --- |
| `timestamp` | Non-null event time |
| `level` | Non-null, nonblank diagnostic level |
| `type` | Non-null, nonblank event type |
| `details` | Immutable map of diagnostic values; null normalizes to empty |
| `frameId` | Optional; null or blank normalizes to null |
| `route` | Optional; null or blank normalizes to null |

Event details are deeply copied into immutable maps and lists. Supported detail
values are JSON-like scalars, maps with non-null string keys, iterables, and
arrays; invalid map keys or unsupported value types are rejected. A mapper may
represent textual payload as `message` and array, scalar, or null payload as
`value`.

The current revision projects `MODEL_ATTEMPT_FAILED` as a `WARN`
`MODEL_ATTEMPT_FAILURE` event. Its details are compact scalar attempt metadata
only; stack text and provider diagnostics remain in the trace content selected
through Console. This warning is not a terminal execution error and may be
followed by a successful provider attempt.

These events are intended for trusted development and debugging and may contain
application business data. They are not a durable trace schema. Framework and
Console must not classify sensitivity or mask, redact, or sanitize prompts or
data outside the exception below: altering content can change user-request
behavior and diagnostic meaning.
Applications control access, retention, and export of events they consume.

Known, supported exception: the derived execution journal underlying observer
events retains its existing `ExecutionJournalProjector` field-name redaction.
This heuristic is not comprehensive sensitivity detection. It does not alter
canonical trace records, model prompts, tool execution inputs, or the skill
result returned by `SkillTemplate.invoke()`. Preserve this existing exception
without extending it to other paths or broadening it without an explicit product
decision.

Use the separate `loomspan-console` runtime-inspection skill when the task needs live
trace discovery, retries, usage, failures, or raw artifacts rather than an
application callback.

## Error Taxonomy

| Failure | Public behavior |
| --- | --- |
| Caller input violates the skill contract | `SkillInputValidationException` |
| Skill is unknown, mismatched, or otherwise fails through the facade | `SkillException` or a subtype |
| Spring Security denies access | The original `AccessDeniedException` propagates |
| Spring authentication failure at a Java proxy | Remains a failure; never successful tool text |
| Designated Java `RecoverableSkillException` | Propagates as a `SkillException` subtype; exact message and optional diagnostic cause retained |
| Runtime implementation or Java serialization failure | Safe `SkillException` message with the original cause |
| Admitted handle already executed, released, or cut off | Safe `SkillException`; no new session or work |
| Observer throws after success | The observer's runtime exception propagates unchanged |
| Mapping or observer throws after execution failure | The original execution failure remains primary; the later failure is suppressed |
| JVM `Error` | Not caught by the facade |

`SkillException` extends `RuntimeException` and supports a message or a message
plus cause.

`SkillInputValidationException` extends `SkillException`. Its `getIssues()`
method returns an immutable defensive copy; a null issue list normalizes to an
empty list. Its issue values are `SkillInputValidationIssue` records containing
`path`, `code`, and `message`. Treat these fields as diagnostics for the current
API revision; do not assume undocumented issue codes.

Catch the narrow validation exception when the application can correct or
report caller input. Catch `SkillException` at a boundary that can add
application context or choose a safe response. Do not collapse
`AccessDeniedException` into an ordinary execution error. Java authorization
denials do not receive success/task/evidence credit. Ordinary Java method and
serialization failures remain failures and reach the facade handling above. A
Java application wrapper is preserved at the Java boundary; the facade may wrap
that runtime exception in `SkillException` as described in the table.

`RecoverableSkillException` accepts only a non-null, nonblank message and an
optional diagnostic cause. The message remains exact; cause text is never fallback
feedback. Only transparent reflection/proxy wrappers preserve its designated
classification. An ordinary application wrapper remains terminal. This classification
applies at the Java invocation boundary; it does not
provide automatic retries or a planner correction loop. See [Java failures](java-skills.md#designated-java-failures).

## Source Anchors

- `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest` cover
  captured namespace generation identity and coordinated current readers.
- `NamespaceStartupIntegrationTest` executes embedded and managed caller flows
  through supported APIs, including one completed observer after retained admission.


- `SkillExecutionView.java` and `SkillExecutionEvent.java` define value
  validation and immutable copying.
- `SkillExecutorTest` and `SharedJavaDiscoveryInvocationTest` protect designated failure constructors, exact messages, shared propagation and infrastructure/application wrapper distinctions.
- `ApplicationApiValueTest` protects record shape, immutability, detail-value
  validation, normalization, and public exception constructors.
- `DefaultSkillTemplateTest#preservesAccessDeniedExceptionInstance` protects
  the authorization boundary.
- `DefaultSkillTemplateTest#preservesExistingSkillExceptionInstance` and
  `#executionFailureDeliversAvailableHistoryOnceWithoutChangingFacadeFailure`
  protect exception passthrough, safe wrapping, and failure-history delivery.
- `SkillExecutionViewMapperTest#refusesToMapSessionWithoutRetainedFinalizedHistory`
  protects the available-history limit after finalization failure.
- `DefaultSkillTemplateTest#doesNotCatchError` protects the fatal-error boundary.
- `DefaultSkillTemplateTest#invalidInputDoesNotInvokeObserver` protects the
  pre-session invalid-input no-callback boundary.
