---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: structural-source-verified
---

# Executor leaf declarations and configuration

An executor leaf declares an operation owned by application Java code, with YAML supplying its input contract, roles and operation configuration. Loomspan parses and validates these declarations and preserves their configuration. Executor-backed YAML skills cannot currently be prepared or executed. The example below shows accepted declaration syntax.

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

The `config` element on `@SkillExecutor` records an optional namespace-relative configuration path; its default is the empty string. Configuration-path validation and runtime binding are unavailable. Do not rely on an annotation path selecting namespace data for execution. See [Java declarations](../java-api/java-skills.md#reference-only-executor-declaration) for the annotation contract.

## Evidence and next topics

- `ExecutorDeclarationTests`, `YamlSkillCatalogTests`, `YamlSkillDefinitionTest` and `TypeSafeDeclarationTests`: explicit forms, presence checks, ordinary input schemas and immutable copies.
- `ExecutorConfigurationTests`, `ExecutionConfigurationParserTest` and `ExecutorConfigurationStartupTests`: namespace structure, target enforcement, complete replacement, real Config Data loading, exact values and ordinary Spring behavior.
- [Java skills](../java-api/java-skills.md): annotation discovery, invocation record and application result/failure contracts.
- [Model selection](model-selection-and-connections.md): separate Framework provider configuration.
