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
String invoke(String skillName, Object input);
String invoke(String skillName, Map<String, Object> input);
String invoke(String skillName, Object input,
              Consumer<SkillExecutionView> observer);
String invoke(String skillName, Map<String, Object> input,
              Consumer<SkillExecutionView> observer);
void validate(String skillName, Object input);
void validate(String skillName, Map<String, Object> input);
```

`skillName` MUST identify a registered Java, REST, TypeSafe, or model-backed YAML skill by its exact name.
For Java, combine its owning annotation namespace with the local annotation name (or canonical method name when omitted). Pass a catalog descriptor name unchanged. Every invoke/validate/observer overload and both handoff forms accept local `search` as `default-search`, always independent of any ambient namespace. Explicit `tax-search` stays tax-scoped. Resolution happens before capture with no fallback or namespace search: namespace case normalizes, local-name case remains exact, namespace length is at most 32 and total identity length at most 64. Missing namespaces fail without creation.
Bean names and method signatures are diagnostics, never invocation aliases.

```java
import ai.loomspan.api.SkillTemplate;
import java.util.Map;

public final class InvoiceWorkflow {
    private final SkillTemplate skills;

    public InvoiceWorkflow(SkillTemplate skills) {
        this.skills = skills;
    }

    public String check(String invoiceText) {
        return skills.invoke(
                "duplicateInvoiceChecker",
                Map.of("payload", invoiceText));
    }
}
```

The return value is text. A caller that requires machine-readable output
SHOULD give the producing model YAML skill an `output_schema` and then parse the returned JSON
according to the application's own boundary policy. Java null/void results become empty text, Strings remain exact, and other values
use Jackson JSON. Java methods have no YAML output schema; see [Java results](java-skills.md#inputs-results-and-lifecycle).

[TypeSafe leaves](../skill-authoring/typesafe-skills.md) use the same String facade:
the complete named `answers` object becomes JSON text without a provider-envelope
wrapper. Their generated schema is descriptive and does not validate or repair
answers. Root pre-checks validate reachable dependencies, input and entry access; native substitution and
resolved-request checks run during invocation before any provider attempt.

An explicit planning `output_from` parent returns its designated accepted direct
child task's existing String unchanged after every accepted task succeeds. It
makes no final model request or duplicate parent validation. Java Strings remain exact, null/void becomes empty text, and other Java results
use JSON; REST returns
handler text with no envelope extraction. Effective catalog schema is metadata,
not a new Java/REST validation guarantee. Read [output contracts](../skill-authoring/output-contracts.md).

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
local or qualified target -> resolve (local uses default) and capture reachable generations/leases atomically with publication
Object input -> reject null -> convert with Jackson -> prepared map
Map input -> normalize an allowed null map to {} -> prepared map
prepared map -> validate the entry input contract
    -> revalidate captured reachable dependency contracts outside the registry lock
    -> capture the calling authentication and check entry RBAC
    -> reserve admission, create and execute a new root session
    -> finalize available execution history and restore the execution binding
    -> build the public execution view
    -> call the optional observer with available completed history
    -> return the textual result or rethrow the execution failure
```

The observer runs synchronously after session completion on success or a post-session
failure when history can be mapped. If it throws after success, its exception propagates
unchanged. On execution failure, mapping or observer failure is suppressed behind the
original failure. Keep observers small and reliable because they remain within root
ownership and the shutdown deadline.

Java and REST roots use the same mission and observer lifecycle without a framework model
request. Direct children are credited at the parent boundary only after success. REST handler semantics are documented in [rest-skills.md](rest-skills.md).
Read [authorization](../skill-authoring/authorization.md) for caller scope and proxy enforcement.

Read [observation-and-errors.md](observation-and-errors.md) before persisting or
exposing events, or when defining exception handling.

## Atomic Application Handoff

Inject `SkillInvocationHandoff` when an application must transfer a request to
Loomspan while holding its own dispatch gate, then execute it after releasing
that gate. `handoff(name, object)` and `handoff(name, map)` perform the same
default-local/explicit-qualified target resolution, reachable snapshot checks, conversion, input validation and entry RBAC as direct invocation, capture
the current Spring Security authentication, and atomically reserve one root.
They do not create a session, submit work, invoke an observer, or wait.

```java
AdmittedSkillInvocation admitted;
synchronized (dispatchGate) {
    if (dispatchClosed) return;
    SecurityContextHolder.getContext().setAuthentication(authentication);
    admitted = handoff.handoff("duplicateInvoiceChecker", input);
}
try {
    String snapshotId = snapshotByGeneration.get(admitted.generationId());
    if (snapshotId == null) throw new IllegalStateException("Missing snapshot mapping");
    invocationRecords.record(snapshotId, admitted.generationId());
    admitted.invoke(observer);
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

## Reachable retention and nesting

Capture traverses every declared child transitively, including optional or role-restricted
branches. Java skills are leaves. It retains each reached whole generation once and
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

- `SkillTemplate.java` defines the supported signatures.
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

Boundary evidence: `SkillReachableAdmissionTest#localDefaultReferencesMatchQualifiedAcrossAllRootOperations` and `#nestedExecutionRejectsAndReleasesWhileValidationAndHandoffPreparationStayAllowed` cover local equivalence, no fallback and captured default preparation inside foreign execution.
