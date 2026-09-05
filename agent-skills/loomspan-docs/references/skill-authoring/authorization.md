---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Skill Authorization

## Applicability

Use this topic for Java JSR-250 policies, YAML `rbac_roles`, and local child visibility. Authorization is enforced before execution and filters a YAML parent's locally allowed tools. Prompts and model-supplied arguments MUST NOT choose caller authentication.

## Declare a Java policy

Enable standard Spring method security in the application:

```java
@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
class MethodSecurityConfiguration {}
```

Then annotate an application Spring bean method or its class:

```java
@RolesAllowed({"FINANCE", "ADMIN"})
@SkillMethod(name = "expenseLookup", description = "Retrieve recent expenses")
public List<Expense> lookup() { /* application implementation */ }
```

This example omits imports and domain implementation. Use `jakarta.annotation.security` annotations and Spring's `org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity`.

| Effective policy | Access |
| --- | --- |
| No supported annotation | Unrestricted |
| `@PermitAll` | Unrestricted |
| `@DenyAll` | Always denied, including administrators |
| `@RolesAllowed` | Requires authentication with any listed effective role |

Loomspan uses Spring Security's unique JSR-250 annotation scanner against the canonical method and actual class. Method, class, inherited, and interface policies follow Spring's resolution and ambiguity rules; conflicting effective declarations fail instead of being merged. An explicitly empty Java roles list does not become unrestricted.

Spring resolves class policy from the specific method's declaring class. A policy only on a subclass that does not override an inherited method does not automatically secure that superclass declaration; put the policy on the declaring type or an explicit override. Duplicate same-precedence interface policy declarations are rejected even when their role values are identical. Do not duplicate or hand-merge policies to work around this rule.

Every effective supported annotation, including `@PermitAll`, requires working JSR-250 enforcement on the final bean. Missing or disabled JSR-250, an unrelated proxy, or a secured method without applicable advice fails startup with bean/method guidance. Merely enabling another method-security family is insufficient. Unannotated Java skills do not require JSR-250 enablement.

## Role semantics shared with YAML

A YAML skill can declare the equivalent role policy:

```yaml
rbac_roles: [FINANCE, ADMIN]
```

Both sources use Spring's authority evaluation. The default prefix is `ROLE_`, so `FINANCE` requires authority `ROLE_FINANCE`. `GrantedAuthorityDefaults` configures a custom or empty prefix, and a configured `RoleHierarchy` applies to both sources. Role names are not raw authorities: writing `ROLE_FINANCE` with the default prefix requires `ROLE_ROLE_FINANCE`; Loomspan does not strip prefixes. Omitted or empty YAML roles are unrestricted.

Use Spring's documented `GrantedAuthorityDefaults` bean configuration when the application uses a different prefix. Do not encode prefix handling in skill names, prompts, or business inputs.

## Local visibility and caller scope

A YAML parent's `allowed_skills` names the only candidate children. Both Java and YAML children are resolved by exact name after startup registration finishes. Missing references fail startup. Authorization then filters this local set for each invocation; `@PermitAll` never makes an undeclared child visible. A denied required child can make a planning or evidence requirement unsatisfiable.

The facade captures the trusted caller authentication. Explicit invocation authentication has priority over session fallback. At the Java proxy boundary Loomspan installs a fresh SecurityContext and restores the exact previous context in finally. Nested calls and parallel workers carry their caller identity without sharing mutable contexts or mutating session authentication for another invocation.

Spring authorization and authentication failures remain failures; they are not serialized into successful tool text. Denied business methods do not run and do not earn successful task/evidence credit. Ordinary Java exception-to-text behavior is separate and remains unchanged.

## Boundaries and evidence

This topic does not promise visibility support for `@Secured`, expression annotations, pre/post filtering, arbitrary custom denial handlers, or additional Loomspan SPIs. Application-specific security may still run on its proxy, but it is outside this visibility contract.

`SkillAccessPolicyResolver` uses Spring's scanner. `DefaultAccessGuard` delegates role evaluation to `SkillRoleEvaluator` using Spring's authority manager with prefix and hierarchy. `Jsr250EnforcementVerifier` validates actual advice, and `ScopedAuthentication` restores caller contexts at Java invocation. `JavaSkillAuthorizationIntegrationTests`, `JavaSkillSecurityStartupTests`, and `JavaSkillAuthenticationScopeIntegrationTests` use real Spring contexts to compare visibility and proxy enforcement; `SampleApplication` enables JSR-250 and `ExpenseService` demonstrates `@PermitAll` without changing the sample's access requirement.
