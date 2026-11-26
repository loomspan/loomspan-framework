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
`publicationVersion()` is an `Optional<String>` containing the exact host-supplied
label. It is empty when omitted for this generation. Empty and whitespace strings
are present values. A prepared snapshot describes its candidate, while
`snapshot(namespace)` describes the successfully published active generation. The
host defines label meaning and supplies it on each replacement or restart installation.
Acquisition selects the current namespace generation once; a missing namespace fails.

`skills()` is immutable, unfiltered and deterministically sorted by qualified name.
`skill("lookup")` accepts a local name in the selected namespace. For example,
`reloader.snapshot("orders").skill("lookup")` selects `orders:lookup`.
A qualified argument such as `orders:lookup` fails local-name validation. Both
components and their combined limit follow the declaration rules, even on a miss.
A valid unknown local name returns empty after one lookup in that catalog. Every registered kind is included
even when authorization denies execution. Catalogs remain unchanged after replacement
or removal and hold no executable resource lease, authorization or execution reservation.

Both annotated methods and executor-backed YAML have `kind: JAVA`; reference-only
executor registrations are absent. Declaration provenance is internal diagnostic
metadata, inspected through Console or [HTTP diagnostics](../skill-authoring/observability-http.md#skill-classification-and-declaration), rather than another application Java API.

Each `SkillDescriptor` has six components: `namespace`, `localName`, `description`, `kind`,
`inputSchema`, and nullable `outputSchema`. `inputSchema` is the exact registered
JSON input-schema string shown to models. `outputSchema` is normalized effective
output metadata: the ordinary model producer's shape inherited through a
forwarding chain or executor YAML leaf, or null for annotation-only Java and producers without a schema.
Child-local evidence annotations are omitted from metadata; it does not install
a parent validator or guarantee factual correctness. See [output contracts](../skill-authoring/output-contracts.md). Retain a catalog as metadata or acquire another through `SkillReloader.snapshot(namespace)` for current discovery. Discovery is not authorization. See [skill-reload.md](skill-reload.md).

Use `SkillTemplate.validate(namespace, localName, map)` or
`validate(namespace, localName, object)` before asynchronous
dispatch. Both freshly capture the entry's entire declared reachable graph and perform the same exact pair validation, conversion, input/dependency contract checks, error mapping, and entry role-policy evaluation used by invocation preparation. A null Object is
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

Both descriptors and successful validation's `ValidatedSkill` records expose
`namespace()`, `localName()` and derived `qualifiedName()`. For `orders:lookup`
these return `orders`, `lookup` and `orders:lookup`. `ValidatedSkill` contains the
two identity components and `kind`. Use the components directly.

```java
var descriptor = reloader.snapshot("orders").skill("lookup").orElseThrow();
skills.validate(descriptor.namespace(), descriptor.localName(), Map.of());
var result = skills.invoke(descriptor.namespace(), descriptor.localName(), Map.of());
String text = result.text();
String sessionId = result.sessionId();
var admitted = handoff.handoff(descriptor.namespace(), descriptor.localName(), Map.of());
try { admitted.invoke(); }
finally { admitted.release(); }
```

A hyphenated local `warehouse-lookup` in the orders catalog selects only
`orders:warehouse-lookup`. It cannot select `warehouse:lookup`. A retained catalog
continues describing its prepared contracts. Each validation, invocation or handoff
captures the then-current active view.

## Source Anchors

- `DefaultSkillCatalogTest#buildsEagerUnfilteredImmutablePublicSnapshotWithExactSchemas`
  protects snapshot order, scope, immutability, kind mapping, and schema fidelity.
- `DefaultSkillTemplateTest#validateUsesInvocationPreparationAndCallingAuthenticationWithoutExecution`
  protects session-free calling-authentication authorization.
- `ApplicationApiValueTest` and `PortableDeclarationIdentityIntegrationTest#validatedAndCataloguedComponentsSelectTheSameRealInvocation` protect exact metadata components and their reuse through real execution.
- `LoomspanPublicSurfaceArchitectureTest`
  protects the closed public surface.
- `SkillReloaderTest#publicationLabelsBelongToTheImmutableWinningGeneration` and
  `NamespacePublicationObservationIntegrationTest` protect candidate/active labels,
  empty namespace discovery and restart installation.

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
