---
audience: loomspan-application-developer
status: development
applies_to: bundled-loomspan-revision
coverage: initial-source-verified
---

# Loomspan Java API Knowledge Base

## Purpose

This knowledge set documents the supported application-facing Java API of the
Loomspan Spring Boot starter. Use it when writing application code that invokes
skills, declares Java skills, observes completed executions,
or handles errors from the supported facade.

Loomspan is under active development and has no production release yet. These
documents describe the repository revision with which this skill package was
published. In a consumer project, install the skill from the repository tag
matching the Loomspan Maven dependency once releases exist.

## Authority

The supported Java surface is closed. Its executable authority is the exact
allowlist in
`loomspan-spring-boot-starter/src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`.
The allowlist currently contains only:

- `SkillTemplate`
- `SkillExecutionView`
- `SkillExecutionEvent`
- `SkillMethod`
- `SkillParam`
- `SkillException`
- `SkillInputValidationException`
- `SkillInputValidationIssue`

All eight are public top-level types in `ai.loomspan.api`. A Java
`public` modifier does not make any other Loomspan type supported API. Read
[compatibility-and-boundaries.md](compatibility-and-boundaries.md) before
advising an application to depend on another Loomspan type.

For exact behavior, use this evidence order:

1. The architecture allowlist defines which Java types are supported API.
2. Public type signatures and focused API tests define their contracts.
3. Supported-surface integration tests demonstrate consumer composition.
4. Production implementation resolves an edge case without turning internal
   types into consumer guidance.

If bundled guidance and matching source or tests conflict, report the drift.
Follow the [source-verification protocol](../skill-authoring/source-verification.md)
when exact source inspection is required.

## Route a Java API Task

| Developer need | Read first | Then read |
| --- | --- | --- |
| Determine whether a Loomspan type or extension point is supported | [compatibility-and-boundaries.md](compatibility-and-boundaries.md) | The relevant API topic below |
| Invoke a Java or YAML skill from application code | [invocation.md](invocation.md) | [observation-and-errors.md](observation-and-errors.md) when observing or handling failures |
| Declare an application method as a skill | [java-skills.md](java-skills.md) | [reflected input contracts](../skill-authoring/input-contracts.md) for complete input-shape guidance |
| Consume an execution view or diagnose facade failures | [observation-and-errors.md](observation-and-errors.md) | [invocation.md](invocation.md) for lifecycle timing |
| Mock Loomspan in an application unit test | [invocation.md](invocation.md) | [compatibility-and-boundaries.md](compatibility-and-boundaries.md) for the supported testing boundary |
| Perform a framework integration test | [compatibility-and-boundaries.md](compatibility-and-boundaries.md) | [invocation.md](invocation.md) |

Do not load every document by default. For preparation, load this index and
the compatibility baseline; then route the concrete task to the smallest
relevant topic set.

## Coverage

| Topic | Coverage | Notes |
| --- | --- | --- |
| Supported public types | Source-verified | Exact eight-type allowlist and package boundaries |
| Skill invocation | Source-verified | Four overloads, input normalization and validation, result, session, security context, and observer timing |
| Java skill annotations | Source-verified | Exact shared names, direct root/child invocation, proxy constraints, and supported application usage; detailed reflected input shapes live in `skill-authoring` |
| Execution observations | Source-verified | Immutable view/event values, normalization, detail-value constraints, and trust boundary |
| Facade failures | Source-verified | Validation, authorization, safe wrapping, observer failures, and fatal errors |
| Configuration properties | Not documented here | Public configuration behavior is user-facing but its binding types are not application Java API |
| Framework extension SPI | Unsupported | Loomspan currently exposes no supported Java SPI or internal bean-replacement contract |

## Normative Language

- **MUST / MUST NOT** identifies a constraint enforced by the current API,
  architecture tests, or runtime behavior.
- **SHOULD / SHOULD NOT** identifies the recommended application default.
- **MAY** identifies an optional supported choice.

