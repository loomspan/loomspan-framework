---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Java Skill and Executor Declarations

## Declare one skill

Use `@SkillMethod` on an application-owned Spring bean method to declare one directly callable Java skill. No companion YAML is required. `SkillTemplate` and a YAML parent's `allowed_skills` both use its exact registered name.

```java
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import org.springframework.stereotype.Component;

@Component
public class InvoiceLookup {
    @SkillMethod(name = "invoiceExists", description = "Find whether an invoice reference already exists")
    public boolean exists(
            @SkillParam(description = "Invoice reference") String reference) {
        return false; // application-owned lookup
    }
}
```

Call this skill with `skills.invoke("default-invoiceExists", Map.of("reference", "INV-42"))`, where `skills` is the injected `SkillTemplate`.

The runtime method annotation has three elements:

```java
String name() default "";
String namespace() default "default";
String description();
```

An omitted or empty `name` uses the canonical Java method name. The declaration name remains local. A nonempty name MUST match `^[A-Za-z_][A-Za-z0-9_]{0,63}$` exactly. Padded, whitespace-only, non-ASCII, hyphenated, and overlength names fail startup; Loomspan does not rewrite them. The annotation owns the description. A blank description retains the method-name fallback.

Java, TypeSafe and model-backed YAML skills use canonical qualified identities. Authored local references resolve to their containing owner, including same-owner Java skills; host local invocation names always select default. Explicit foreign references remain qualified. Namespace ownership comes from `namespace`, normalized to lowercase with an independent 32-character limit; total identity length is at most 64. Duplicate canonical identities fail startup with both declaration locations; equal local names in different namespaces are distinct. Distinct overloaded methods MAY declare distinct explicit names; overloads that retain the same name collide. Bean aliases and compiler bridge methods do not create additional registrations. Bean and method locations shown in Console are diagnostics, never alternative invocation names.

A YAML `mapping` key is rejected, including null, empty, and scalar forms. Remove the wrapper and put its chosen callable name and description on the annotation.

The example registers `default-invoiceExists`. See the [identity rules](../skill-authoring/mental-model.md#canonical-namespace-identity) for exact grammar, limits and reference syntax.

## Inputs, results, and lifecycle

`@SkillParam` is a runtime parameter annotation with `description() default ""` and `required() default true`. Descriptions SHOULD explain meaning and constraints. Optional parameters MUST use nullable reference types: omission binds null, so an optional primitive fails startup.

The Java signature and parameter metadata supply the input contract for root validation, tool publication, and planner guidance. Read [reflected Java input contracts](../skill-authoring/input-contracts.md) for scalars, Object, generic and typed maps, DTOs, collections, runtime references, and null semantics.

Successful Java values use one conversion contract for both declaration roles:

| Returned value | Text |
| --- | --- |
| `null` or `void` | Empty String |
| Non-null `String`, including empty or whitespace-only text | Exact original String, without JSON quoting |
| Any other non-null value | JSON using the application Jackson mapper |

Serialization failures and application exceptions propagate as failures. There is no exception-to-successful-text adapter. Java callable declarations do not have a YAML output schema, prompt, model, or planning configuration. A method may implement application-owned I/O; Loomspan makes no model request merely to execute it.

Java roots and children use the common mission lifecycle. Root observers receive available completed history after success or post-session failure. Java children retain the enclosing tool boundary, and only successful completion contributes their exact direct name to task/evidence accounting. Parent mission state resumes after return or failure.

Java work uses the same bounded mission submission, timeout, interruption, and cleanup path as model execution. A timed-out or interrupted invocation closes its mission and fences late runtime writes, even if application code ignores interruption. This does not undo application side effects; Java implementations SHOULD honor cancellation and provide their own I/O bounds.

## Reference-only executor declaration

`@SkillExecutor` is a runtime method annotation with exactly these elements:

```java
String name() default "";
String namespace() default "default";
String config() default "";
```

An executor method MUST accept exactly one `SkillExecutorInvocation` parameter. It supports the same return values as `@SkillMethod`. Omitted names and namespace normalization follow the callable identity rules above. `config` records an optional configuration path; empty selects no configuration.

Loomspan discovers executor methods and preserves Spring proxy invocation. A YAML leaf selects one with `executor:` and executes as `SkillKind.JAVA`, retaining its YAML input contract and operation configuration. Configuration selection and registration ownership come from the captured executor namespace generation. An executor is reference-only: it has no generated callable schema, catalog entry, `allowed_skills` membership or `SkillTemplate` invocation route. Callable and executor names have separate indexes, so both roles may share one qualified identity; duplicates within either role fail. A bean may declare either or both roles.

The public record has exactly five components, in this order:

```java
Map<String, Object> input;
Map<String, Object> skillConfig;
Map<String, Object> executorConfig;
String skillName;
String executorConfigGenerationId;
```

Framework supplies validated, reference-resolved input; the executing YAML skill's `config` and canonical qualified name; the selected executor configuration map; and the executor namespace's captured generation ID, even when the annotation declares no configuration path. Invocation performs no live registration lookup or configuration rebinding. Domain configuration validation and application resources remain application-owned.

Its public constructor requires every component to be non-null and both metadata strings to be nonblank; valid strings retain their exact text. All three maps are defensively copied recursively into unmodifiable map/list containers, preserving nulls. Other leaves retain identity, including Spring `Resource` handles, arrays and arbitrary application objects. This does not freeze those objects or copy/freeze resource bytes. There is no builder, authentication field, context bag or internal runtime value in the record.

## Designated Java failures

`RecoverableSkillException extends SkillException` has exactly two constructors:

```java
RecoverableSkillException(String message);
RecoverableSkillException(String message, Throwable cause);
```

Messages MUST be non-null and nonblank. Valid messages, including surrounding whitespace, remain unchanged. An optional cause supplies diagnostics and never substitutes its message as feedback. The shared policy recognizes this subtype through transparent reflection/proxy (`InvocationTargetException`, `UndeclaredThrowableException`), worker-future (`ExecutionException`), and Spring AI callback (`ToolExecutionException`) wrappers. An ordinary application wrapper around it is a distinct terminal failure; classification never searches arbitrary causes.

| Invocation route | Designated recoverable failure | Other failure |
| --- | --- | --- |
| Direct `SkillTemplate.invoke()` | Throws through the existing facade boundary, preserving message and cause | Throws through the existing facade boundary |
| Standard tool calling | Records failure, then sends failed-call feedback permitting model correction | Terminates; no failure feedback |
| Explicit step planning | Terminates; no correction loop | Terminates |

Standard feedback uses the original tool-call ID/name and a JSON body with `status: "failed"`, `correctionPermitted: true`, and the exact application `message`. It is never a successful skill result, success credit, or a value for successful-result bindings. The model may choose normal continuation, but the exception does not replay work automatically. Failed calls and subsequent model/tool work consume the existing budgets with no reset or refund; the parent timeout continues. Authorization, quotas, timeout, cancellation and shutdown remain terminal controls. See [facade errors](observation-and-errors.md) for supported caller handling.

Evidence: `ExplicitJavaFailureHandlingIntegrationTest`, `SharedJavaDiscoveryInvocationTest`, `LoomspanSessionTest`, and the lifecycle/usage regression suites.


## Spring invocation and authorization

Loomspan invokes the final Spring-managed bean so advice remains active. Both Java declaration roles MUST be invocable through the bean's actual proxy; prefer public methods and interfaces compatible with the chosen proxy strategy. Discovery includes relevant lazy beans, including executor-only beans. Interface annotations, canonical generic bridges and equivalent interface contracts share one discovery pass. Incompatible interface contracts fail even when the implementation is annotated. A method cannot acquire both roles across implementation/interface declarations. Uninvocable declarations fail startup instead of silently disappearing.

Supported visibility policies are JSR-250 `@RolesAllowed`, `@PermitAll`, and `@DenyAll`, resolved using Spring's class/method/interface rules. Applications declaring these policies MUST enable `@EnableMethodSecurity(jsr250Enabled = true)` and provide actual applicable method-security advice. Read [authorization](../skill-authoring/authorization.md) for role prefixes, hierarchy, startup checks, and caller authentication isolation.

An annotation-only callable uses its captured Java policy. An executor YAML leaf requires its YAML policy AND the captured executor's Java policy, with role alternatives inside each constituent. Validation, handoff entry checks and planner discovery evaluate that metadata without invoking business code or argument-dependent advice. Actual root/child execution rechecks authorization and invokes the proxy with actual business arguments; `@PreAuthorize` and other applicable advice may therefore deny a candidate that passed validation. Both forms install the captured caller on the mission worker and restore the exact prior security context after success, advice denial and business failure. Catalog metadata remains unfiltered. See [composed declaration boundaries](../skill-authoring/authorization.md#compose-executor-declaration-boundaries).

Do not import registries, descriptors, invokers, or discovery components from `ai.loomspan.internal`. Loomspan exposes no supported Java SPI; annotation-based registration creates no SPI or bean replacement contract.

## Source anchors

- `SkillMethod`, `SkillParam`, `SkillExecutor`, `SkillExecutorInvocation` and `RecoverableSkillException` define the supported declarations and values; `SkillExecutorTest` protects the new annotation/constructor/copy contracts.
- `SharedJavaDiscoveryInvocationTest` covers shared result conversion, actual reflection/JDK/CGLIB wrappers, mixed-role identity indexes, lazy executor discovery, unsafe dispatch and real method security.
- `SkillMethodBeanPostProcessor` and its tests define shared canonical discovery and callable reflected inputs; `JavaSkillInvoker` owns final managed-proxy invocation and conversion for both roles.
- `SkillGenerationManager#prepare` finishes all declaration sources and validates child references before a complete immutable generation can activate.
- `SupportedSurfaceIntegrationTest` exercises the supported facade and application-owned methods.
- `SharedDeclarativeAuthorizationIntegrationTest` covers real JDK/CGLIB argument-dependent advice through root/child mission dispatch, shared pre-check/filter rules, rechecks, caller capture and exact worker-context restoration.
- `MissionWorkExecutor` is the shared work/cutoff owner; `JavaSkillMissionCutoffTest` protects timeout, caller interruption, and noncooperative late returns.
- `ExecutorDependencyResolutionTest` covers reference roles, exact configuration paths, retained generation binding, and reachable-only capture; `ExecutorGenerationExecutionIntegrationTest` covers the real executor invocation route. `ExecutorGenerationExecutionTest` and `DefaultRegisteredSkillCatalogTest` cover retained YAML/source labels, resolved executor identity, and Java implementation diagnostics; see [skill diagnostics](../skill-authoring/observability-http.md#skill-classification-and-declaration).

Private, final, and static methods of either Java role on a CGLIB class proxy fail startup because they cannot dispatch safely through the proxy's target and advice. Plain unproxied bean methods retain reflected invocation. `JavaSkillIntegrationTest` covers the stateful class-proxy regression.

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. Fresh callable admission captures
reachable namespace generations; retained execution uses that association across
replacement, removal and recreation. Each dispatch enforces the source's declared
children and captured target visibility. Executor dependencies retain their configuration-owner generation without adding callable children or traversing unrelated skills. The invocation uses only that captured registration, configuration subtree and generation ID.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`, `SkillReachableAdmissionTest`, and `NamespaceExecutionAuthorityIntegrationTest`.
