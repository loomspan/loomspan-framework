---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Skill Authorization

Authored child references and binding producers MAY be local to the containing skill's namespace or explicitly qualified; local `search` in `sales` compiles to `sales-search`. Mixed spellings compare canonically and never search other namespaces. Runtime/model names remain qualified. See [canonical namespace identity](mental-model.md#canonical-namespace-identity) for grammar, limits and boundary rules. Declarations keep local `name` components.

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

All skill kinds use Spring's authority evaluation. The default prefix is `ROLE_`, so `FINANCE` requires authority `ROLE_FINANCE`. `GrantedAuthorityDefaults` configures a custom or empty prefix, and a configured `RoleHierarchy` applies to Java, TypeSafe, and model-backed YAML skills. Role names are not raw authorities: writing `ROLE_FINANCE` with the default prefix requires `ROLE_ROLE_FINANCE`; Loomspan does not strip prefixes. Omitted or empty YAML roles are unrestricted.

Use Spring's documented `GrantedAuthorityDefaults` bean configuration when the application uses a different prefix. Do not encode prefix handling in skill names, prompts, or business inputs.

## Local visibility and caller scope

A YAML parent's `allowed_skills` names the only candidate children. Java, TypeSafe, and model-backed YAML children are resolved by exact name after startup registration finishes. Missing references fail startup. Authorization then filters this local set for each invocation; `@PermitAll` never makes an undeclared child visible. A denied required child can make a planning or evidence requirement unsatisfiable.

At runtime every ordinary, planned and bound dispatch independently checks the actual
executing source before switching context. A call MUST be declared directly by that
source, resolve within the retained snapshot, target either the same namespace or a
captured Public namespace, and pass the target's role policy. These conditions also
filter discovery. Public does not bypass roles or declarations; roles do not bypass
a foreign Private boundary. A Public callee cannot call back into a Private caller,
even when that caller was the entry. Capturing a whole generation grants no authority
to its undeclared skills. Java skills remain leaves.

The facade captures the trusted caller authentication. Explicit invocation authentication has priority over session fallback. At Java execution boundaries Loomspan installs a fresh SecurityContext on the actual worker thread and restores the exact previous context in finally. Nested calls and parallel workers carry their caller identity without sharing mutable contexts or mutating session authentication for another invocation.

Manifest and Java policies belong to the immutable generation captured by the root, while authentication remains the actual trusted caller identity evaluated at validation, visibility, and execution boundaries. A newer generation may change policy for newly captured roots, but it neither substitutes policy into an older tree nor freezes/bypasses that tree's live caller authorization checks.

Spring authorization and authentication failures remain failures; they are not serialized into successful tool text. Denied business methods do not run and do not earn successful task/evidence credit. Ordinary Java method and serialization exceptions also remain failures. See [Java failure behavior](../java-api/java-skills.md#designated-java-failures).

## Boundaries and evidence

This topic does not promise visibility support for `@Secured`, expression annotations, pre/post filtering, arbitrary custom denial handlers, or additional Loomspan SPIs. Application-specific security may still run on its proxy, but it is outside this visibility contract.

`SkillAccessPolicyResolver` uses Spring's scanner. `DefaultAccessGuard` delegates role evaluation to `SkillRoleEvaluator` using Spring's authority manager with prefix and hierarchy. `Jsr250EnforcementVerifier` validates actual advice, and `ScopedAuthentication` restores caller contexts at Java invocation. `JavaSkillAuthorizationIntegrationTests`, `JavaSkillSecurityStartupTests`, and `JavaSkillAuthenticationScopeIntegrationTests` use real Spring contexts to compare visibility and proxy enforcement.

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. Namespace updates support preparation
and installation, retained reachable admission and independent runtime dispatch.
Captured canonical observation identities and existing Console consumers are covered by `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest`.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`.

## Root dependency checks and entry access

Host root arguments alone choose the namespace. Input/model arguments cannot override it.
An authorized host may directly enter a Private namespace; Private controls foreign
reference edges independently of entry RBAC. Public does not imply anonymous entry,
management rights or database access. Host entry authorization remains the host's duty.

Admission and temporary validation traverse all declared reachable branches before skill
execution. Same-namespace edges pass visibility; every foreign target must be captured
Public, including a callback into a Private entry. Missing/Private/incompatible optional
branches reject even if the caller lacks that child's role. This does not eagerly check
child RBAC, predict branches, or grant access to unrelated skills in a retained namespace.
Entry RBAC is checked with the operation's captured authentication; actual child role
checks remain execution boundaries. Already admitted work retains old visibility after
replacement/removal/recreation. Runtime dispatch repeats source, visibility and role checks.

Evidence: `SkillDependencyValidator#visit`, `DefaultSkillTemplate#authorize`,
`SkillAdmissionSnapshotTest#validatesEveryIncomingEdgeEvenWhenItsTargetWasAlreadyVisited`,
and `SkillReachableAdmissionTest#optionalRestrictedChildrenAreCapturedWithoutEagerRbacAndValidationNeverReserves`.

Runtime evidence: `DefaultAccessGuard#hasCallAuthority`, `NamespaceAccessGuardTest`,
and `NamespaceExecutionAuthorityIntegrationTest`.
