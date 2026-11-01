---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# REST API types

`RestSkillHandler` and `RestSkillInvocation` are part of the supported Java
surface. However, `rest:` is unsupported YAML syntax and no supported manifest
declaration binds a handler. A handler bean alone does not expose an operation
as a callable skill. Use [Java skills](java-skills.md#declare-one-skill) for
application operations.

`RestSkillHandler` is a functional interface with
`String handle(RestSkillInvocation invocation)`. `RestSkillInvocation` has three
components: `String skillName`, `Map<String, Object> input`, and
`String generationId`. Its constructor rejects null components and blank
generation IDs. It defensively copies map/list containers, preserving nulls
and non-container leaf identity, including Spring `Resource` handles. The bytes
behind a resource are outside the immutability promise.

See [executor declarations](../skill-authoring/executor-skills.md) for accepted
executor syntax and its current limitations, and [namespace publication](skill-reload.md)
for complete configuration, captured generations and retirement.

## Evidence

- `ApplicationApiValueTest` protects invocation construction and copying.
- `LoomspanPublicSurfaceArchitectureTest` defines the supported Java surface.
- `ExecutorDeclarationTests` protects rejection of `rest:` manifest syntax.
