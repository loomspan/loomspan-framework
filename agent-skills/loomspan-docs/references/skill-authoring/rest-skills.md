---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# REST Leaf Skills

## Applicability

Use a REST leaf when application code owns remote-call details and Loomspan should expose that operation as a validated, authorized direct capability. REST here names an authoring mode; Loomspan does not supply an HTTP client or accept URLs, headers, or credentials in the manifest.

## Manifest contract

```yaml
name: fetchAccount
description: Fetch one account
rest: true
input_schema:
  type: object
rbac_roles: [ACCOUNT_READER]
```

| Field | Rule |
| --- | --- |
| `name`, `description` | Required under the shared YAML rules |
| `rest` | Required and MUST be the Boolean `true`; omit it for non-REST YAML |
| `input_schema`, `rbac_roles` | Optional |
| `model`, `prompt`, `thinking_level`, `allowed_skills`, `planning_mode`, `concurrency`, `max_steps`, `linter`, `output_schema`, `output_schema_max_retries` | MUST be absent, including null or empty declarations |

No input schema uses the existing generic object contract. Explicit schemas use the same validation and `ref://` resolution path as other skills. Business input is never inherited from a parent.

REST skills share the exact case-sensitive namespace with model YAML and Java skills and may be roots or direct children of model-backed YAML skills. A successful child earns the existing task, required-child, and evidence credit; denial, cancellation, null return, or failure does not. They execute through the direct mission lifecycle without a model request.

REST definitions and their resolved handler capability are part of the root's captured immutable generation. A running or admitted tree keeps that definition and policy consistently across nested execution; this does not expose a public reload or old-generation invocation API.

YAML `rbac_roles` applies normally. The captured caller authentication is installed on the handler execution thread and the prior thread context is restored.

## Application boundary and diagnostics

When REST manifests exist, the application MUST provide exactly one `RestSkillHandler` bean. See [REST handler SPI](../java-api/rest-skills.md). With no REST manifests, handler beans are unused and do not trigger cardinality validation.

Console lists the kind as `REST` and shows the YAML resource path and unchanged manifest text. It does not reveal handler internals or infer an endpoint. Framework and Console diagnostics require the exact coordinated project version; dual `development` versions only attempt ordinary complete validation.

## Evidence

- `YamlSkillCatalogTests` protects syntax, field presence, and model isolation.
- `SkillGenerationManagerTest` protects complete assembly, shared names, and fixed dependency capture; REST-focused startup tests protect handler cardinality and invocation.
- `ExecutionCoordinatorTest` protects the shared direct lifecycle.
- `SuccessfulSkillCompletionBoundaryTest` protects success-only task and direct-skill credit for REST children.
- `ConsoleRestFixtureCorpusTest` protects Java-to-Console fixtures.
