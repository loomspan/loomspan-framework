---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Invoking Skills with `SkillTemplate`

## Supported Facade

Inject `ai.loomspan.api.SkillTemplate` into application code. It has four invoke
overloads and two advisory validation overloads:

```java
SkillInvocationResult invoke(String namespace, String localName, Object input);
SkillInvocationResult invoke(String namespace, String localName, Map<String, Object> input);
SkillInvocationResult invoke(String namespace, String localName, Object input,
              Consumer<SkillExecutionView> observer);
SkillInvocationResult invoke(String namespace, String localName, Map<String, Object> input,
              Consumer<SkillExecutionView> observer);
void validate(String namespace, String localName, Object input);
void validate(String namespace, String localName, Map<String, Object> input);
```

Supply `namespace` and `localName` separately for a registered Java,
executor-backed YAML, or model-backed YAML skill. Both components use the exact
ASCII grammar `[a-z][a-z0-9]*(?:-[a-z0-9]+)*`, contain 1–64 characters and exclude
`anthropic` and `claude`. The pair must satisfy
`namespace.length() + 2 + localName.length() <= 64`. Every root shape validates the
pair before capture, independent of ambient execution. Missing namespaces fail
without creation, and a missing target ends lookup. Metadata's `namespace()` and
`localName()` values pass directly to these arguments.
Bean names and method signatures are diagnostics, never invocation aliases.

```java
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillInvocationResult;
import java.util.Map;

public final class InvoiceWorkflow {
    private final SkillTemplate skills;

    public InvoiceWorkflow(SkillTemplate skills) {
        this.skills = skills;
    }

    public SkillInvocationResult check(String invoiceText) {
        return skills.invoke(
                "default", "duplicate-invoice-checker",
                Map.of("payload", invoiceText));
    }
}
```

The return value is `SkillInvocationResult(String text, String sessionId)`. Both
components are non-null, and the root session ID is nonblank. Read `text()` for
the original result and `sessionId()` for correlation with the canonical trace
and any delivered observer view. A caller that requires machine-readable output
SHOULD give the producing model YAML skill an `output_schema` and then parse `result.text()` as JSON
according to the application's own boundary policy. Java null/void results become empty text, Strings remain exact, and other values
use Jackson JSON. Java methods have no YAML output schema; see [Java results](java-skills.md#inputs-results-and-lifecycle).

[TypeSafe pack leaves](../skill-authoring/typesafe-skills.md) use the same result
facade. With `output_transform` omitted, they publish the complete provider JSON,
including its envelope, JSON null and quoted strings. In that shape, consumer
bindings can use `/answers/...`. An
[executor output transform](../skill-authoring/executor-skills.md#publish-a-transformed-executor-result)
publishes the skill's chosen JSON shape. Generic root dependency, input and
access checks still apply. Pack-domain validation runs at each invocation before
send. Framework supplies no generated output schema or provider-answer
validation and repair for executor leaves.

An explicit planning `output_from` parent returns its designated accepted direct
child task's existing String unchanged after every accepted task succeeds. It
makes no final model request or duplicate parent validation. Java Strings remain exact, null/void becomes empty text, and other Java results
use JSON. Executor YAML leaves apply this Java conversion before any declared
output transform. Executor YAML leaves enforce their declared `output_schema`
once against the published value. A forwarding parent returns that accepted child
text without adding parent validation. Effective catalog schema describes this
shape; annotation-only Java methods have no YAML output schema. Read [output contracts](../skill-authoring/output-contracts.md).

A failed direct or admitted invocation exposes the root ID through
`SkillException.getSessionId()`. The getter returns null when failure occurs
before root identity allocation, including invalid handoff input or a rejected
admission. After allocation, initialization, execution, finalization, public
mapping, observer and ownership-release runtime failures retain that identity.
A post-session denial is `SkillAccessDeniedException`, with the same getter,
and remains catchable as Spring `AccessDeniedException`.

The caller receives identity independently of observers and trace retention.
Invoking without an observer skips public execution-view construction and public
event conversion. Trace lookup remains subject to existing access and retention
rules. JVM `Error` propagation keeps its existing fatal-failure boundary.

```java
try {
    var result = skills.invoke("support", "summarize", input);
    logger.info("Session {} returned {}", result.sessionId(), result.text());
} catch (SkillAccessDeniedException failure) {
    logger.warn("Session {} denied access", failure.getSessionId());
    throw failure;
} catch (SkillException failure) {
    logger.warn("Session {} failed", failure.getSessionId(), failure);
    throw failure;
}
```

A failed invocation also exposes optional terminal contract detail
through `SkillException.getFailureDiagnosis()`. Input rejection during handoff
preparation exposes it on `SkillInputValidationException`. Availability is
independent of observers and diagnostic retention. Read the
[diagnosis contract](observation-and-errors.md#terminal-contract-diagnosis).

## Choose an Input Overload

- Use `Map<String, Object>` for an explicit JSON-object-shaped input.
- Use the `Object` overload for an application record or DTO that Jackson can
  convert to a map.
- The object conversion does not bypass validation; the normalized map follows
  the same input-contract validation path.
- A non-map-compatible object conversion failure crosses the facade as a safe
  `SkillException` with its original runtime failure as the cause.

A null `Object` input is invalid. A null map is accepted only for a generic
contract or a contract that permits an empty object; otherwise it raises
`SkillInputValidationException`. Prefer passing an explicit empty `Map.of()`
when the skill deliberately takes no fields.

Input is validated before a session is run. Invalid input does not execute the
skill and does not call the observer. `validate` additionally checks root authorization
using the calling authentication and the same reachable dependency checks, with temporary leases and without creating a session or reserving admission;
see [catalog and validation](catalog-and-validation.md).

## Invocation Lifecycle

Each invocation creates a new root Loomspan session. The implementation
captures the current Spring Security `Authentication`, when present, into that
session so authorization is evaluated with the caller's security context.

Preparation preserves the overload-specific error order. The lifecycle is:

```text
namespace and localName -> validate exact pair and capture reachable generations/leases atomically with publication
Object input -> reject null -> convert with Jackson -> prepared map
Map input -> normalize an allowed null map to {} -> prepared map
prepared map -> validate the entry input contract
    -> revalidate captured reachable dependency contracts outside the registry lock
    -> capture the calling authentication and check entry RBAC
    -> reserve admission, create and execute a new root session
    -> finalize available execution history and restore the execution binding
    -> if an observer was supplied, build its public view and deliver available completed history
    -> return SkillInvocationResult or throw a failure carrying root identity
```

The observer runs synchronously after session completion on success or a post-session
failure when history can be mapped. If mapping or the observer throws after
success, a session-bearing public failure retains that exception as its cause.
On execution failure, mapping or observer failure is suppressed behind the
execution failure and retained on its public snapshot. Keep observers small and reliable because they remain within root
ownership and the shutdown deadline.

Java roots use the same mission and observer lifecycle without a framework model
request. Direct children are credited at the parent boundary only after success. Executor construction and result semantics are documented in [Java declarations](java-skills.md).
Read [authorization](../skill-authoring/authorization.md) for caller scope and proxy enforcement.

Read [observation-and-errors.md](observation-and-errors.md) before persisting or
exposing events, or when defining exception handling.

## Atomic Application Handoff

Inject `SkillInvocationHandoff` when an application must transfer a request to
Loomspan while holding its own dispatch gate, then execute it after releasing
that gate. `handoff(namespace, localName, object)` and `handoff(namespace, localName, map)`
perform the same exact pair validation, reachable snapshot checks, conversion,
input validation and entry RBAC as direct invocation, capture
the current Spring Security authentication, and atomically reserve one root.
They do not create a session, submit work, invoke an observer, or wait.

```java
AdmittedSkillInvocation admitted;
synchronized (dispatchGate) {
    if (dispatchClosed) return;
    SecurityContextHolder.getContext().setAuthentication(authentication);
    admitted = handoff.handoff("default", "duplicate-invoice-checker", input);
}
try {
    String snapshotId = snapshotByGeneration.get(admitted.generationId());
    if (snapshotId == null) throw new IllegalStateException("Missing snapshot mapping");
    invocationRecords.record(snapshotId, admitted.generationId());
    var result = admitted.invoke(observer);
    String text = result.text();
    String sessionId = result.sessionId();
} finally {
    admitted.release();
}
```

`generationId()` returns the nonblank, process-local entry namespace generation ID captured during preparation, before object conversion and input validation. A newer generation may be active when handoff returns, but this handle retains the entire captured association, including dependency visibility. Cross-namespace child dispatch uses the captured association. The ID remains stable after execution, failure, release, retirement, or cutoff; concurrent reads do not claim or extend the admission. It is not a durable snapshot identifier. Stage and retain the application-owned generation-to-snapshot mapping according to application policy before admitting traffic, and record correlation before invocation. A missing mapping must be handled explicitly because cleanup can remove it after ownership ends, including cutoff, while the handle still exposes the ID. `release()` in `finally` covers lookup and recording failures and is harmless after invocation claims the admission.

The handle is single-use. Exactly one invocation, explicit idempotent release,
or framework cutoff can win; a losing invocation fails as `SkillException`
without creating another session. Release after execution has claimed the root
is harmless. Authorization is still enforced during execution with the captured
identity, nested work receives the existing trusted identity, and the worker's
prior security context is restored. Mission timeout accounting begins when
ordinary mission work is submitted. If shutdown has begun, a pending handle may
start only during the remaining single framework budget and is invalidated at
cutoff. Finalization and observer delivery remain within root ownership.

Both `admitted.invoke()` and `admitted.invoke(observer)` return the same
`SkillInvocationResult` shape as direct invocation. `generationId()` identifies
the captured entry generation. `result.sessionId()` identifies this root execution.

## Reachable retention and nesting

Capture traverses every declared child transitively, including optional or role-restricted
branches. Callable Java methods and executor YAML skills are leaves. Executor dependencies select and retain their namespace without traversing unrelated callable declarations or granting authority to call them. It retains each reached whole generation once and
ignores unrelated skills and namespaces. Dependency checks rerun existing visibility,
forwarding, binding and planning rules before execution; foreign targets must be Public.
Snapshot membership does not permit calling undeclared children. Admission does not
construct runtimes or provider clients and is not a transaction over external systems.

Validation releases temporary ownership on every outcome. Admitted and queued work
retain their association through replacement/removal/recreation. Abandonment and failure
release ownership through the existing lifecycle; a timeout response does not release
resources while physical mission work still owns them. Each actual root creates a fresh
session. Nested fresh or admitted root execution is rejected; validation and handoff
preparation inside an active execution remain allowed.

Evidence: `SkillReachableAdmissionTest`, `SkillAdmissionSnapshotTest`, and
`SkillAdmissionContractsTest`; `DefaultSkillTemplate`, `SkillGenerationManager`,
`SkillDependencyValidator`, and `FrameworkExecutionLifecycle` own these boundaries.

## Application Tests

Unit tests SHOULD mock `SkillTemplate` rather than Loomspan internals. For
framework integration, use a named connection and invoke the YAML skill through
the real facade; see [compatibility-and-boundaries.md](compatibility-and-boundaries.md).

## Source Anchors

- `SkillTemplate.java`, `SkillInvocationResult.java` and `ApplicationApiValueTest`
  define the supported signatures and result value contract.
- `DefaultSkillTemplateTest#invocationIdentityMatchesRootWithoutPublicMapping`
  protects the no-observer path and root identity.
- `LoomspanSessionRunnerTest#identityNotificationPrecedesSessionInitialization`
  protects authoritative identity allocation before session collaborators.
- `SkillInvocationHandoff.java` and `AdmittedSkillInvocation.java` define the
  supported ownership-transfer and single-use handle signatures.
- `DefaultSkillTemplateTest#handoffCapturesPreparedInputAndCallingAuthentication`
  protects captured identity, execution, and worker-context restoration.
- `SkillGenerationExecutionIntegrationTest#objectHandoffKeepsCapturedIdWhenConversionPublishesReplacement`
  protects capture timing across object conversion and publication.
- `DefaultSkillTemplateTest#objectOverloadDelegatesThroughValidatedMapPath`
  protects object normalization followed by map validation.
- `DefaultSkillTemplateTest#skillTemplateNullInputAndObserverLifecycle`
  protects required-input rejection and successful observation.
- `DefaultSkillTemplateTest#observerExceptionPropagatesAfterExecutionCompletes`
  protects observer timing and exception propagation.
- `DefaultSkillTemplateTest#capturesCurrentSecurityContextAuthenticationForRootInvocation`
  protects security-context capture.

Boundary evidence: `SkillReachableAdmissionTest#explicitPairsSelectExactTargetsAcrossAllRootOperations` and `#nestedExecutionRejectsAndReleasesWhileValidationAndHandoffPreparationStayAllowed` cover explicit pair selection, missing-target rejection and captured preparation inside foreign execution.
