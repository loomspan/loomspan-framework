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

An observer is called only after successful execution. It is not called for
input validation or execution failures. The callback is synchronous, and its
own exception propagates to the caller after execution has completed.

Java and YAML roots both produce a skill mission and terminal outcome. Java
execution does not create a model interaction merely for observation. Existing
canonical mission-frame records project into `SKILL_STARTED` and `SKILL_FINISHED`
events for both sources, so a Java-only root has a meaningful public observer
view even without model or tool events.

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

These events are intended for trusted development and debugging. They may
contain application business data and are neither a durable trace schema nor a
promise of comprehensive sanitization. Applications MUST apply their own data
classification, access control, redaction, retention, and export policy before
persisting or exposing them.

Use the separate `loomspan` runtime-inspection skill when the task needs live
trace discovery, retries, usage, failures, or raw artifacts rather than an
application callback.

## Error Taxonomy

| Failure | Public behavior |
| --- | --- |
| Caller input violates the skill contract | `SkillInputValidationException` |
| Skill is unknown, mismatched, or otherwise fails through the facade | `SkillException` or a subtype |
| Spring Security denies access | The original `AccessDeniedException` propagates |
| Spring authentication failure at a Java proxy | Remains a failure; never successful tool text |
| Runtime implementation failure | Safe `SkillException` message with the original cause |
| Observer throws after success | The observer's runtime exception propagates unchanged |
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
denials do not receive success/task/evidence credit. Ordinary Java method
exceptions retain their established exception-to-text behavior; this differs
from failures that reach the facade and are safely wrapped.

## Source Anchors

- `SkillExecutionView.java` and `SkillExecutionEvent.java` define value
  validation and immutable copying.
- `ApplicationApiValueTest` protects record shape, immutability, detail-value
  validation, normalization, and public exception constructors.
- `DefaultSkillTemplateTest#preservesAccessDeniedExceptionInstance` protects
  the authorization boundary.
- `DefaultSkillTemplateTest#preservesExistingSkillExceptionInstance` and
  `#wrapsOtherRuntimeFailureWithSafeSkillExceptionAndCause` protect wrapping.
- `DefaultSkillTemplateTest#doesNotCatchError` protects the fatal-error boundary.
- `DefaultSkillTemplateTest#invalidInputDoesNotInvokeObserver` protects the
  failed-invocation observer lifecycle.
