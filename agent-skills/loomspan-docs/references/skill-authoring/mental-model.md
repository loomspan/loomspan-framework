---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: initial
---

# Loomspan Skill-Tree Mental Model

## Purpose

Use this document to establish shared vocabulary and choose the broad shape of a Loomspan skill tree. It is not a complete YAML manifest reference.

## Core Model

Loomspan combines model-driven YAML skills and deterministic Java `@SkillMethod` skills. Each declaration is one callable capability. An LLM-backed skill reasons about its mission and can invoke only the direct child skills exposed to it.

```text
Application code -> Java or YAML entry skill
YAML planner -> YAML specialist -> Java @SkillMethod leaf
```

Not every tree needs every level. Java skills can be roots without a model; a simple reasoning skill can be one YAML declaration.

## Exact shared identity

A YAML manifest's `name` or a Java annotation's `name` is the single callable identity. An omitted or empty Java annotation name uses the canonical method name. Every name MUST match `^[A-Za-z_][A-Za-z0-9_]{0,63}$`: 1–64 ASCII characters, beginning with a letter or underscore. Names are case-sensitive and are never trimmed, sanitized, normalized, truncated, or aliased. Descriptive lowerCamelCase is recommended, but underscores and uppercase starts are valid.

Use the exact name in `SkillTemplate`, `allowed_skills`, plan targets, evidence expressions, provider tool definitions, metrics, and traces. Duplicate names across Java and YAML fail startup and identify both declarations. Relevant lazy Java beans are discovered before the shared registry is completed. Every child reference must resolve after both sources finish registration; authorization filtering does not make missing names valid.

Bean names, source paths, and declared method signatures are diagnostic locations, never a second callable identity. Any YAML `mapping` key, including null, empty, or scalar forms, is rejected with annotation-only guidance.

## Capability types

### Entry skill

An entry is any registered Java or YAML skill invoked through `SkillTemplate`. The facade accepts a map or an object convertible to a map, validates its effective input contract, and creates a fresh session. Callers SHOULD understand the entry contract without understanding the tree below it.

### Model-backed YAML skill

A YAML skill declares a configured `model`. It can declare private `prompt`, schemas, evidence, linting, retries, model settings, and local `allowed_skills`. Explicit `planning_mode: true` selects step-based planning. Each YAML child retains its own model and planning settings.

### Java skill

An application Spring bean's `@SkillMethod` declares one directly callable skill. The annotation owns its name and description; the method signature and `@SkillParam` own inputs. Java supplies the returned value, serialized using Jackson. No companion YAML, Java output schema, model settings, or synthetic plan is created.

Distinct overloaded methods MAY have distinct explicit skill names. Same/default names collide. Spring proxy, interface, and bridge methods are canonicalized, and invocation resolves the final managed bean so configured advice runs. Methods must be invocable through that bean's proxy; relevant lazy declarations are deliberately initialized during startup.

Use Java for operations whose correctness should not depend on model reasoning: lookups, calculations, controlled I/O, policy operations, and fixture access. Read [Java skills](../java-api/java-skills.md) for declaration and proxy guidance, [input contracts](input-contracts.md) for reflected shapes, and [authorization](authorization.md) for JSR-250 enforcement.

```java
@SkillMethod(name = "accountLookup", description = "Look up one account")
public Account lookup(
        @SkillParam(description = "Stable account identifier") String accountId,
        @SkillParam(description = "Optional region", required = false) String region) {
    // application logic
}
```

Optional primitive parameters are invalid because omission binds null; use reference types. `Object` and `Map<String, Object>` permit heterogeneous JSON-compatible values. Prefer records or DTOs for discoverable stable fields. These input rules do not imply automatic inheritance of business inputs between missions.

Java roots and children participate in the common skill mission lifecycle. Java executes without a framework model request and shares bounded mission timeout, interruption, and late-write cutoff handling. A successful child contributes its exact name at the parent tool boundary; a denial or failure contributes no success. The parent mission resumes on every exit.

Console's catalog shows `YAML` or `Java` explicitly. YAML details show the source resource and original YAML; Java details show the bean and deterministic declared method signature. Pagination and links use only the registered name.

## Root, Planner, Specialist, and Leaf Are Roles

These words describe how a capability is used, not distinct framework classes.

- **Root or entry skill:** invoked by application code.
- **Planner:** an LLM-backed YAML skill with `planning_mode: true` that creates and executes a bounded task plan.
- **Specialist:** a child YAML skill responsible for a narrower mission. It may itself be a planner.
- **Leaf:** a capability that performs work without further decomposition, commonly a deterministic Java skill.
- **Synthesis skill:** a skill whose primary responsibility is composing evidence and results into the final output.

A YAML skill can be a root in one tree and a nested specialist in another if its contract and visibility boundaries make sense in both contexts.

## Visibility Is Local

`allowed_skills` defines the candidate child Java or YAML skills for one LLM-backed skill. It is not transitive.

If a root allows `investigateNetwork`, and `investigateNetwork` allows `checkDns`, the root sees `investigateNetwork`; it does not automatically see `checkDns`.

Runtime authorization filters the declared tool surface again; see [authorization](authorization.md) for JSR-250, YAML roles, and trusted caller scope. Visibility is not authorization by itself, and authorization is enforced at execution as well as discovery.

Every entry uses the structured `{name: ...}` form. A planning parent may also declare unconditional generated-task minimums and maximums; positive minimums must be satisfiable by the already authorized visible surface. Read [planning-task-constraints.md](planning-task-constraints.md) for syntax, defaults, validation, and retry behavior.

A skill SHOULD expose only the capabilities needed for its responsibility. Narrow tool surfaces improve security, make plans easier to understand, and preserve HTN boundaries.

## Nested Execution Preserves Capability Boundaries

When an LLM-backed YAML skill invokes another LLM-backed YAML skill:

- the child receives the arguments supplied for the child contract;
- the child opens its own mission frame inside the current session;
- the child uses its own model, prompt, allowed skills, plan, and property-level output evidence requirements;
- the child owns an isolated plan and successful-direct-skill set, and the exact parent mission resumes after the child returns or fails;
- the parent observes the child capability result, not the child's internal tool surface or evidence ledger.

This isolation is intentional. A parent contract should describe the child capability it invokes rather than coupling itself to the child's internal leaves.

## Choose Direct Reasoning or Step-Based Planning Deliberately

The current execution coordinator selects the step-based planning executor only when the YAML manifest explicitly declares:

```yaml
planning_mode: true
```

Do not assume that a global planning default turns an undeclared skill into a step-loop planner in the current implementation.

An LLM-backed YAML skill without `planning_mode: true` uses direct mission execution. This remains true when the skill is nested. A nested specialist therefore does not need to become a planner merely because it is part of a deeper tree.

Use direct mission execution when one focused model-directed mission interaction is appropriate and the skill does not need Loomspan's explicit plan-and-step lifecycle. The direct executor still receives the skill's visible tools, so direct execution does not mean that the skill must have no children. It means Loomspan does not first create a task plan and then ask the model for one bounded step action at a time.

Direct execution is the normal choice for a focused specialist that does not benefit from an explicit plan. Do not interpret "direct" as a guarantee of exactly one physical provider request: tool-calling protocols, advisors, and provider behavior may involve additional internal interactions.

Use step-based planning when the skill genuinely needs dynamic decomposition, selective child-capability use, or multi-step progress toward its result. Planning adds an initial plan-model interaction and a bounded model-driven step loop. A planning step may invoke a nested skill, and that child independently chooses direct or planning execution from its own manifest.

An explicit planner may also declare planning-only `concurrency`; omission defaults it to enabled and `false` requires serialization. Generated `parallelGroup` values define validated ordered execution units. The coordinator assigns exact tasks, dispatches valid enabled groups concurrently, waits for every ordinary member outcome, and folds results and diagnostics in task-list order. Ungrouped tasks and disabled groups remain serialized. Each assigned task costs one step and final synthesis costs one more. Read [planning-concurrency.md](planning-concurrency.md) before authoring or diagnosing task groups, dependencies, join behavior, or step budgets.

Each additional planning boundary increases potential model calls, latency, validation retries, and failure locations. Nesting itself is not a reason to avoid a useful planner, but every planning level SHOULD provide a meaningful mission boundary and decomposition benefit.

Do not select direct execution merely to conceal that a mission needs decomposition, and do not select planning merely because it appears more capable. Choose the least complex execution semantics that truthfully fit the skill's responsibility.

A planner SHOULD have:

- a narrow mission;
- a bounded `max_steps` appropriate to the mission;
- a deliberate `allowed_skills` surface;
- explicit input and output contracts when callers depend on structured behavior;
- property-level `evidence` requirements only on immediate root output claims that need deterministic supportability enforcement.

A direct specialist SHOULD have:

- one focused reasoning or synthesis responsibility;
- a contract it can fulfill without an explicit, framework-managed task plan and step loop;
- a narrow `allowed_skills` surface if direct tool or child-skill use is part of that responsibility;
- explicit input and output contracts when callers depend on structured behavior;
- model and validation settings appropriate to that responsibility.

Sample claims about model compatibility MUST be scoped to the sample and configuration actually tested. Success on a shallow direct skill does not establish that the same model can reliably execute a multi-level planning tree. Loomspan skill architecture SHOULD target capable production models; small local models may be useful for experimentation but are not a reason to weaken planning semantics or runtime safeguards.

## Inputs, Runtime Metadata, and Evolving State

Keep these categories distinct when designing a tree:

- **Business input:** mission data such as ticket text, identifiers, dates, requested actions, and scenario names. It normally travels through declared skill and tool inputs.
- **Trusted execution metadata:** authoritative identity, authorization, tenant, correlation, deadline, or provenance information. It should not become model-controlled merely for propagation convenience. No general framework feature for arbitrary trusted metadata is documented here.
- **Evolving mission state:** plans, tool results, evidence, artifacts, and trace records. Use the framework concept appropriate to the information rather than inventing an ambient mutable bag.

Do not automatically treat repeated business inputs as runtime metadata. Explicit contracts preserve local comprehension and traceability.

## Initial Design Heuristics

- Keep the entry contract obvious to application developers.
- Give each skill one coherent responsibility.
- Use the model for decomposition, selection, interpretation, and synthesis.
- Use deterministic Java leaves for controlled side effects and operations with programmatic correctness rules.
- Keep child visibility local and narrow.
- Preserve child abstraction boundaries; do not make parents depend on internal leaves.
- Prefer explicit business inputs over implicit inheritance.
- Add contracts when the framework can enforce a meaningful invariant, not merely to decorate a manifest.
- Validate a proposed shape against representative successful and failure branches.

## Implementation anchors

- `SkillTemplate` and `DefaultSkillTemplate` define supported invocation, input validation, and session creation.
- `SkillMethodBeanPostProcessor` and its focused tests protect exact names, canonical discovery, reflection, final-proxy invocation, and Java policies.
- `YamlSkillCatalog` validates model-backed manifests and rejects legacy mappings. `YamlSkillCapabilityRegistrar#completeRegistration` finishes discovery and validates cross-source child references.
- `DefaultSkillVisibilityResolver` filters the local shared child surface. `CapabilityExecutionRouter` and `ExecutionCoordinator` own dispatch and common isolated mission boundaries.
- `SupportedSurfaceIntegrationTest` and `SampleApplicationTests` demonstrate application methods called through the supported facade.
- `DefaultRegisteredSkillCatalogTest`, `ConsoleRestFixtureCorpusTest`, and Console component tests cover both diagnostic source variants.

## Coverage Boundary

This document does not define every manifest field or the complete behavior of planning, inputs, outputs, RBAC, attachments, retries, quotas, or traces. Consult [README.md](README.md) for current topic coverage before advising on those areas.
