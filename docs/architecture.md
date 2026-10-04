# Architecture and adoption tradeoffs

Loomspan composes application capabilities into a hierarchy. A root skill receives
an application request. A model-backed skill can reason directly or, with
`planning_mode: true`, create a task plan using its allowed direct children.
A child can itself be a model-backed skill, an annotated Java method, or a REST
leaf implemented by the application.

```text
Application request
  -> model-backed entry skill
       -> specialist model-backed skill
       -> Java application service
       -> application-handled REST leaf
```

These are possible roles, not mandatory layers. A single YAML skill can handle a
small reasoning task. Java and REST skills can also be invoked directly without
a framework model call. The [skill-tree reference](../agent-skills/loomspan-docs/references/skill-authoring/mental-model.md)
defines exact naming, visibility, and composition semantics.

## When this approach fits

Consider Loomspan when the next useful operation depends on interpreting a
request or intermediate results, but those operations must remain explicit
application capabilities. Examples include gathering evidence from several
services, investigating a case, or analyzing documents against application data.

Keep a fixed sequence in ordinary application code when its steps are already
known and model reasoning adds no useful decision. A planner introduces model
latency, usage, and variability. YAML contracts and natural-language prompts still
require design, testing, and evaluation; they do not remove the need for code.

## What the application controls

- **Capabilities:** Java methods and the REST handler own application operations
  and their side effects. A REST manifest does not define endpoint credentials or
  provide a generic HTTP client; the application handler owns that integration.
- **Composition:** each model-backed parent declares its direct child surface.
  Child visibility is local, not transitive. Authorization is enforced separately.
- **Contracts:** input validation, structured-output validation, and optional
  evidence requirements constrain accepted behavior. They do not prove factual
  correctness. An evidence requirement tracks successful named child execution,
  not whether the child's result proves the model's claim.
- **Resources:** connections and model aliases choose providers; deadlines and
  quotas bound execution. They do not roll back external effects.
- **Access:** the application establishes caller identity and applicable policies.
  Diagnostic access needs explicit authorization boundaries. Canonical diagnostic
  content is preserved; the documented derived-journal exception is described in
  [observation and errors](../agent-skills/loomspan-docs/references/java-api/observation-and-errors.md).

## Runtime and development assistant

The framework runs application skills. Separately, versioned Agent Skills help a
development assistant author and integrate them. Console supplies a browser UI
and read-only MCP tools for runtime investigation. Installing an Agent Skill does
not configure a provider, start a runtime, or connect MCP.

Embed the framework in a Spring Boot application when in-process Java integration
fits your architecture. Use Sidecar when an HTTP service boundary fits better.
Sidecar adds deployment and authentication boundaries of its own; consult its
matching guidance rather than treating embedded Java examples as its API contract.
See [setup](setup.md).

## Supported extension boundary

Use the closed [application-facing Java API](../agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md).
`RestSkillHandler` is the sole supported Java SPI. Framework internals and Spring
integration machinery are not extension contracts. The architecture test
`LoomspanPublicSurfaceArchitectureTest` defines the exact public allowlist.
