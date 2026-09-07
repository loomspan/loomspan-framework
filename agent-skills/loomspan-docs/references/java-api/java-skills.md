---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Annotation-defined Java Skills

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

Call this skill with `skills.invoke("invoiceExists", Map.of("reference", "INV-42"))`, where `skills` is the injected `SkillTemplate`.

The runtime method annotation has two elements:

```java
String name() default "";
String description();
```

An omitted or empty `name` uses the canonical Java method name. A nonempty name MUST match `^[A-Za-z_][A-Za-z0-9_]{0,63}$` exactly. Padded, whitespace-only, non-ASCII, hyphenated, and overlength names fail startup; Loomspan does not rewrite them. The annotation owns the description. A blank description retains the method-name fallback.

Java and YAML share one namespace. Duplicate names fail startup with both declaration locations. Distinct overloaded methods MAY declare distinct explicit names; overloads that retain the same name collide. Bean aliases and compiler bridge methods do not create additional registrations. Bean and method locations shown in Console are diagnostics, never alternative invocation names.

A YAML `mapping` key is rejected, including null, empty, and scalar forms. Remove the wrapper and put its chosen callable name and description on the annotation.

## Inputs, results, and lifecycle

`@SkillParam` is a runtime parameter annotation with `description() default ""` and `required() default true`. Descriptions SHOULD explain meaning and constraints. Optional parameters MUST use nullable reference types: omission binds null, so an optional primitive fails startup.

The Java signature and parameter metadata supply the input contract for root validation, tool publication, and planner guidance. Read [reflected Java input contracts](../skill-authoring/input-contracts.md) for scalars, Object, generic and typed maps, DTOs, collections, runtime references, and null semantics.

Java results retain Jackson serialization into the facade's textual result. Java declarations do not have a YAML output schema, prompt, model, or planning configuration. A Java method may implement application-owned I/O, but Loomspan itself makes no model request to execute that Java skill.

Java roots and children use the common mission lifecycle. Root observers receive the completed session after success. Java children retain the enclosing tool boundary, and only successful completion contributes their exact direct name to task/evidence accounting. Parent mission state resumes after return or failure.

Java work uses the same bounded mission submission, timeout, interruption, and cleanup path as model execution. A timed-out or interrupted invocation closes its mission and fences late runtime writes, even if application code ignores interruption. This does not undo application side effects; Java implementations SHOULD honor cancellation and provide their own I/O bounds.

## Spring invocation and authorization

Loomspan invokes the final Spring-managed bean so advice remains active. Skill methods MUST be invocable through the bean's actual proxy; prefer public methods and interfaces compatible with the chosen proxy strategy. Discovery includes relevant lazy beans. Uninvocable declarations fail startup instead of silently disappearing.

Supported visibility policies are JSR-250 `@RolesAllowed`, `@PermitAll`, and `@DenyAll`, resolved using Spring's class/method/interface rules. Applications declaring these policies MUST enable `@EnableMethodSecurity(jsr250Enabled = true)` and provide actual applicable method-security advice. Read [authorization](../skill-authoring/authorization.md) for role prefixes, hierarchy, startup checks, and caller authentication isolation.

Do not import registries, descriptors, invokers, or discovery components from `ai.loomspan.internal`. No Java SPI or bean replacement contract is created by annotation-based registration.

## Source anchors

- `SkillMethod` and `SkillParam` define the supported annotations; their API tests protect defaults and retention.
- `SkillMethodBeanPostProcessor` and its tests define canonical discovery, reflected inputs, and invocation of the final bean.
- `YamlSkillCapabilityRegistrar#completeRegistration` finishes both sources before checking child references.
- `SupportedSurfaceIntegrationTest` exercises the supported facade and application-owned methods.
- `MissionWorkExecutor` is the shared work/cutoff owner; `JavaSkillMissionCutoffTest` protects timeout, caller interruption, and noncooperative late returns.
- `DefaultRegisteredSkillCatalogTest`, `ConsoleRestFixtureCorpusTest`, and Console `SkillCatalog.test.tsx` / `SkillDetail.test.tsx` protect mixed-source diagnostics.

Private, final, and static skill methods on a CGLIB class proxy fail startup because they cannot dispatch safely through the proxy's target and advice. Plain unproxied bean methods retain reflected invocation. `JavaSkillIntegrationTest` covers the stateful class-proxy regression.
