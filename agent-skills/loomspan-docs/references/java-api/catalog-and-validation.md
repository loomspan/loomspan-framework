---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Catalog Discovery and Root Pre-Checks

Inject `SkillCatalog` to read one eager startup snapshot. `skills()` is immutable and
sorted by exact registered name; `skill(name)` performs exact lookup and returns empty
when absent. Every registered YAML, Java, and REST skill is included even when restricted.
Each `SkillDescriptor` contains only `name`, `description`, `SkillKind`, and the exact
registered JSON input-schema string shown to models. The catalog has no refresh or
replacement contract and discovery is not authorization.

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

## Source Anchors

- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`
  protects snapshot order, scope, immutability, kind mapping, and schema fidelity.
- `DefaultSkillTemplateTest#validateUsesInvocationPreparationAndCallingAuthenticationWithoutExecution`
  protects session-free calling-authentication authorization.
- `LoomspanPublicSurfaceArchitectureTest#apiPackageContainsExactlyFifteenApprovedPublicTypes`
  protects the closed public surface.
