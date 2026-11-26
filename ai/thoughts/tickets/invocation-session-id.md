# Return the session ID from skill invocations

## Outcome

A Loomspan Cloud caller who receives an unexpected answer or an execution failure can give a reviewer the session ID for that invocation. A reviewer with trace access can use that ID to identify the corresponding retained trace. Today, obtaining the ID requires an observer, which makes Framework build a public event for every finalized journal entry. If view construction fails, the observer never receives the ID.

## Acceptance criteria

- [x] Every successful `SkillTemplate.invoke(...)` and `AdmittedSkillInvocation.invoke(...)` call returns a result whose `text()` gives the original result text and whose `sessionId()` gives the root execution's non-null session ID as a `String`.
- [x] When an invocation throws after session creation, the caller can read that same ID directly from the exception using `getSessionId()`. On `SkillException`, the getter returns a nullable `String`, with `null` indicating failure before session creation. A post-session access denial exposes the same getter and remains catchable as Spring's `AccessDeniedException`.
- [x] Both guarantees apply with and without an execution-view observer. Invoking without an observer obtains the ID without building a public execution view or converting journal entries into public events. If requested view construction fails, the failure path still exposes the session ID.
- [x] The ID matches the session ID recorded in the canonical trace and in any delivered execution view. It remains available to the caller regardless of trace retention. Existing trace access and retention rules continue to apply.

## Caller examples

**Direct invocation with `SkillTemplate`**

```java
try {
    var result = skillTemplate.invoke("support", "summarize", input);
    return CloudResponse.success(result.text(), result.sessionId());
} catch (SkillException failure) {
    return CloudResponse.failure(failure, failure.getSessionId());
}
```

**Admitted invocation with `SkillInvocationHandoff`**

```java
try {
    var admitted = skillInvocationHandoff.handoff("support", "summarize", input);
    var result = admitted.invoke();
    return CloudResponse.success(result.text(), result.sessionId());
} catch (SkillException failure) {
    return CloudResponse.failure(failure, failure.getSessionId());
}
```

`CloudResponse` represents the caller's response handling. In the admitted example, a handoff rejected for invalid input yields a `SkillException` with a null session ID. A skill failure after execution starts carries the session ID. Both examples show the `SkillException` path. Callers that handle access denials separately can read the ID directly from the Loomspan access-denied subtype on a post-session denial.

## Pipeline notes

Framework and Cloud are in development with no deployments. Change the existing invocation contract directly and update affected in-repository callers and current API documentation together. Omit compatibility shims, legacy text-only signatures, and migration documentation. This ticket covers the Framework contract. Cloud response design and reviewer lookup are separate work.

## Execution profile

- **Recommended** — Full 5-Step Pipeline
- **Confidence** — high
- **Rationale** — The change affects the supported Java API and how callers obtain execution metadata across several failure paths.
- **Reassessment triggers** — None.
