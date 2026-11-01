---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Catalog Discovery and Root Pre-Checks

Acquire a catalog with `SkillCatalog catalog = reloader.snapshot("default")` from
an injected `SkillReloader`. There is no injected startup `SkillCatalog` bean and
no no-argument snapshot alias. `namespace()` is the canonical lowercase owner,
including for an empty catalog; `generationId()` is its opaque generation ID.
Acquisition selects the current namespace generation once; a missing namespace fails.

`skills()` is immutable, unfiltered and deterministically sorted by qualified name.
`skill("Tax-search")` normalizes namespace case and preserves local-name case.
Malformed/unqualified arguments fail; valid unknown or foreign-namespace identities
return empty without consulting another catalog. Every registered kind is included
even when authorization denies execution. Catalogs remain unchanged after replacement
or removal and hold no executable resource lease, authorization or execution reservation.

Each `SkillDescriptor` has five components: `name`, `description`, `kind`,
`inputSchema`, and nullable `outputSchema`. `inputSchema` is the exact registered
JSON input-schema string shown to models. `outputSchema` is normalized effective
output metadata: the ordinary model producer's shape or a TypeSafe leaf's generated
descriptive schema, inherited through a
forwarding chain, or null for Java (including executor YAML) and model producers without a schema.
Native descriptive metadata does not validate or repair TypeSafe answers.
Child-local evidence annotations are omitted from metadata; it does not install
a parent validator or guarantee factual correctness. See [output contracts](../skill-authoring/output-contracts.md). Retain a catalog as metadata or acquire another through `SkillReloader.snapshot(namespace)` for current discovery. Discovery is not authorization. See [skill-reload.md](skill-reload.md).

Use `SkillTemplate.validate(name, map)` or `validate(name, object)` before asynchronous
dispatch. Both freshly capture the entry's entire declared reachable graph and perform the same local-to-default or explicit-qualified resolution, conversion, input/dependency contract checks, error mapping, and entry role-policy evaluation used by invocation preparation. A null Object is
always rejected before conversion; a null Map is accepted only for generic or
empty-permitting contracts. Authorization denial remains an unwrapped Spring Security
`AccessDeniedException` with the capability name.

Validation is advisory. It creates no session, trace, execution binding, observer callback,
model/skill execution, or quota use and reserves no lifecycle admission. Invocation repeats
input and access enforcement and can still be denied by changed authentication, shutdown,
or execution-time policy. Every declared reachable branch is checked for existence, captured namespace visibility,
forwarding, bindings and planning constraints, including optional/role-restricted branches.
Only entry RBAC is pre-checked; child role authorization remains an execution outcome.
Temporary leases are released on success or failure, so dependency deletion, visibility or
contract changes can invalidate later invocation even after validation succeeded.

When atomic ownership transfer is required, use `SkillInvocationHandoff` rather
than `validate`: handoff prepares the input and reserves one lifecycle admission,
while execution still enforces authorization with the authentication captured at
handoff. See [invocation.md](invocation.md).

Pass `descriptor.name()` directly to invocation, validation or handoff; do not split
it when invoking a non-default target. Local names are accepted by root operations only as default references; `SkillCatalog.skill(...)` remains qualified-only. A retained catalog continues describing its prepared contracts; it never pins later
validation, invocation or handoff. Each operation captures the then-current active view.

## Source Anchors

- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`
  protects snapshot order, scope, immutability, kind mapping, and schema fidelity.
- `DefaultSkillTemplateTest#validateUsesInvocationPreparationAndCallingAuthenticationWithoutExecution`
  protects session-free calling-authentication authorization.
- `LoomspanPublicSurfaceArchitectureTest`
  protects the closed public surface.

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. Captured cross-namespace child
dispatch independently enforces source authority, Public target visibility and target RBAC;
unfiltered host metadata does not grant execution or observation access.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`.

Admission evidence: `SkillReachableAdmissionTest#allRootOverloadsUseFreshQualifiedTargetsAcrossReplacementRemovalAndRecreation` and `#optionalRestrictedChildrenAreCapturedWithoutEagerRbacAndValidationNeverReserves`.
