---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Executor leaf declarations and configuration

An executor leaf declares an operation owned by application Java code, with YAML supplying its input contract, roles and operation configuration. Loomspan prepares and executes these leaves through the shared Java route, classifies them as `SkillKind.JAVA`, and retains their YAML declarations and exact configuration. The example below shows accepted declaration syntax.

```yaml
name: fetchAccount
description: Fetch one account
executor: infrastructure-http
config:
  operation: accountLookup
input_schema:
  type: object
rbac_roles: [ACCOUNT_READER]
```

`name` and `description` are required. `executor` MUST be a nonblank textual local or qualified reference under the shared identity grammar. Local references are qualified with the declaring namespace. It references execution behavior, not a callable child, and does not belong in `allowed_skills`.

`input_schema` and `rbac_roles` are optional and reuse ordinary YAML rules. Without an input schema, the existing generic object contract applies. Business input is never inherited from a parent.

Executor leaves MUST omit `typesafe`, `model`, `thinking_level`, `prompt`, `planning_mode`, `concurrency`, `max_steps`, `allowed_skills`, `linter`, `output_schema`, `output_schema_max_retries`, `output_from`, and `output_bindings`. Presence fails even for null, false, empty maps, empty lists or empty strings. The forms do not select precedence. `rest` is unsupported manifest syntax; every value fails unknown-field validation.

`config` is permitted only on executor leaves. Omission and `{}` yield an empty immutable map. Explicit null, a bare field, a scalar or a list fail. Nested nulls, maps, lists, empty containers, scalar types and exact data keys are preserved. `config` is executor-owned domain data, not a Framework schema.

## Namespace executor data

Startup YAML may supply multiple namespaces:

```yaml
loomspan:
  executor-config:
    infrastructure:
      accounts:
        endpoint: https://example.invalid
        api-key-ref: ordinary-content
        Upper: A
        upper: B
        literal.dot: retained
        nullable: null
        empty: []
```

The section and each namespace root MUST be maps. Namespace names follow the existing identity grammar and normalize case; duplicate normalized namespace names fail. Data keys inside a namespace retain case and literal dots. Startup YAML ingestion preserves nested maps/lists, nulls, empty containers and scalar distinctions before normal Spring flattening. YAML `null` and `~` remain null; quoted `"~"` remains text. Aliased maps/lists retain their contents; recursive containers fail validation. YAML logical expansion is limited to 1,000,000 values, including repeated alias contents, before downstream tree processing. Executor strings such as `${NAME}` and keys such as `api-key-ref` remain literal domain content. Ordinary Spring properties outside this section retain normal Spring binding behavior within that resource limit.

Published `ExecutionConfiguration` uses the same layout and accepts only its target namespace, rejecting even an empty foreign entry. Each publication is complete replacement: omitted executor configuration is empty and never inherits startup or prior-generation data. Skill `config` and namespace executor configuration remain separate; Framework does not merge, interpolate, resolve credentials, validate executor domain data, or call application validation code. See [complete namespace publication](../java-api/skill-reload.md).

The `config` element on `@SkillExecutor` selects an optional dot-separated map path relative to the executor namespace. Omission or an explicitly empty string supplies an empty immutable map. Whitespace-only paths, surrounding whitespace and empty segments (`.x`, `x.`, `x..y`) fail without trimming. Selection uses exact case-sensitive map keys: no array indexing, expressions, wildcard, fallback lookup or literal-dot key addressing is supported. Explicit missing, null, scalar, list and non-map intermediate selections fail; an empty selected map succeeds. Literal dotted keys inside the selected subtree remain unchanged data. The shared dependency validator validates this structure during startup/preparation and resolves the reached binding during admission; invocation does not reselect it. See [Java declarations](../java-api/java-skills.md#reference-only-executor-declaration).

## Dependency capture and lifecycle

A local executor reference resolves only in the declaring YAML skill's namespace. A qualified reference selects that exact namespace and name without fallback. Same-namespace executors may be Private; foreign executor namespaces MUST be Public. The `default` namespace has no implicit Public visibility. Callable names and executor names have separate indexes, so one qualified identity may exist in both roles. An executor registration alone is never a callable root, child, catalog skill or tool.

Each namespace generation owns its fixed executor registrations and immutable configuration alongside its callable declarations. Namespaces containing only executors initialize as Private. A complete replacement or explicit recreation restores assigned registrations with newly supplied configuration; it never inherits old configuration. Removal blocks fresh dependent admission, preserves the application/Spring beans, and allows retained work to finish. Application code owns resources, domain validation and shutdown; Loomspan adds no executor preparation or cleanup callbacks.

Preparation overlays the complete candidate namespace on captured foreign metadata, invokes no application code and retains no foreign runtime lease. Foreign changes after preparation do not invalidate target-base publication; each fresh admission validates the graph it captures. Admission follows only root-reachable callable edges and their executor dependencies, selecting one coherent generation per needed namespace. Retaining an executor namespace does not traverse unrelated skills or grant authority to call them.

The invocation receives validated/reference-resolved input, the executing YAML leaf's `config` and qualified name, the selected executor configuration map, and the executor namespace generation ID even when no config path is declared. Retained execution uses only that captured registration and map across replacement, removal and recreation. Both Java forms share mission execution, caller authentication scope, observation, timeout and cancellation behavior. The leaf requires its YAML role policy AND its captured executor's Java declarative policy, evaluated against the same caller at root validation, planner filtering and execution. Role alternatives remain within each declaration; unrestricted/PermitAll cannot override the other constituent, and DenyAll denies. See [authorization](authorization.md#compose-executor-declaration-boundaries) for the composition rule and examples. Captured executor policy is rechecked with the trusted execution caller; retaining a generation never freezes an earlier authorization grant.

Validation and discovery execute no application code or argument-dependent advice. Invocation still runs the final Spring proxy with actual arguments, so application advice may reject a leaf that passed declarative validation. Catalog visibility is unchanged.

Public catalogs classify these leaves as Java while Framework retains their YAML source. Current diagnostic displays do not expose a complete executor provenance model.

## Evidence and next topics

- `ExecutorDeclarationTests`, `YamlSkillCatalogTests`, `YamlSkillDefinitionTest` and `TypeSafeDeclarationTests`: explicit forms, presence checks, ordinary input schemas and immutable copies.
- `ExecutorConfigurationTests`, `ExecutionConfigurationParserTest` and `ExecutorConfigurationStartupTests`: namespace structure, target enforcement, complete replacement, real Config Data loading, exact values and ordinary Spring behavior.
- `ExecutorDependencyResolutionTest`: local/qualified roles, visibility, exact paths/maps, executor-only startup, retained binding, distinguishable captured executor policies, target-base publication and root-reachable capture.
- `ExecutorGenerationExecutionTest` and `ExecutorGenerationExecutionIntegrationTest`: YAML retention, public JAVA classification, captured invocation construction and secured handoff across foreign replacement/removal/recreation.
- `SkillRoleEvaluatorTest` and `SharedDeclarativeAuthorizationIntegrationTest`: composed YAML/Java policy, metadata-only visibility/validation, dispatch rechecks and actual argument advice on mission workers.
- [Java skills](../java-api/java-skills.md): annotation discovery, invocation record and application result/failure contracts.
- [Model selection](model-selection-and-connections.md): separate Framework provider configuration.
