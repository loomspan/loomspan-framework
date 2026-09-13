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

`skillName` MUST identify a registered Java, REST, or model-backed YAML skill by its exact name.
For Java, this is the annotation name or the canonical method name when omitted.
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
SHOULD give the YAML skill an `output_schema` and then parse the returned JSON
according to the application's own boundary policy. Java results use Jackson
serialization and do not have a YAML output schema.

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
using the calling authentication without creating a session or reserving admission;
see [catalog and validation](catalog-and-validation.md).

## Invocation Lifecycle

Each invocation creates a new root Loomspan session. The implementation
captures the current Spring Security `Authentication`, when present, into that
session so authorization is evaluated with the caller's security context.

Preparation preserves the overload-specific error order. The lifecycle is:

```text
Object input -> reject null -> convert with Jackson -> resolve the exact skill -> prepared map
Map input -> resolve the exact skill -> normalize an allowed null map to {} -> prepared map
prepared map -> validate the skill input contract
    -> capture the calling authentication
    -> create and execute a new root session
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

## Application Tests

Unit tests SHOULD mock `SkillTemplate` rather than Loomspan internals. For
framework integration, use a named connection and invoke the YAML skill through
the real facade; see [compatibility-and-boundaries.md](compatibility-and-boundaries.md).

## Source Anchors

- `SkillTemplate.java` defines the supported signatures.
- `DefaultSkillTemplateTest#objectOverloadDelegatesThroughValidatedMapPath`
  protects object normalization followed by map validation.
- `DefaultSkillTemplateTest#skillTemplateNullInputAndObserverLifecycle`
  protects required-input rejection and successful observation.
- `DefaultSkillTemplateTest#observerExceptionPropagatesAfterExecutionCompletes`
  protects observer timing and exception propagation.
- `DefaultSkillTemplateTest#capturesCurrentSecurityContextAuthenticationForRootInvocation`
  protects security-context capture.
