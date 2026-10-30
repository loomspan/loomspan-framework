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
forwarding chain, or null for Java, REST and model producers without a schema.
Native descriptive metadata does not validate or repair TypeSafe answers.
Child-local evidence annotations are omitted from metadata; it does not install
a parent validator or guarantee factual correctness. See [output contracts](../skill-authoring/output-contracts.md). Retain a catalog as metadata or acquire another through `SkillReloader.snapshot(namespace)` for current discovery. Discovery is not authorization. See [skill-reload.md](skill-reload.md).

Use `SkillTemplate.validate(name, map)` or `validate(name, object)` before asynchronous
dispatch. Both perform the same exact lookup, conversion, contract validation, safe error
mapping, and root role-policy evaluation used by invocation preparation. A null Object is
always rejected before conversion; a null Map is accepted only for generic or
empty-permitting contracts. Authorization denial remains an unwrapped Spring Security
`AccessDeniedException` with the capability name.

Validation is advisory. It creates no session, trace, execution binding, observer callback,
model/skill execution, or quota use and reserves no lifecycle admission. Invocation repeats
input and access enforcement and can still be denied by changed authentication, shutdown,
or execution-time policy. Only the root is pre-checked; nested authorization remains an
execution outcome.

When atomic ownership transfer is required, use `SkillInvocationHandoff` rather
than `validate`: handoff prepares the input and reserves one lifecycle admission,
while execution still enforces authorization with the authentication captured at
handoff. See [invocation.md](invocation.md).

Pass `descriptor.name()` directly to invocation, validation or handoff; do not split
it or reconstruct a local alias. PR 27.2 supplies active-registry/removal integration;
PR 27.3 verifies descriptor-driven root admission.

## Source Anchors

- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`
  protects snapshot order, scope, immutability, kind mapping, and schema fidelity.
- `DefaultSkillTemplateTest#validateUsesInvocationPreparationAndCallingAuthenticationWithoutExecution`
  protects session-free calling-authentication authorization.
- `LoomspanPublicSurfaceArchitectureTest`
  protects the closed public surface.
