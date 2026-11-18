---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Executor leaf declarations and configuration

For an independently versioned provider pack using these generic contracts, see
the sibling Loomspan Decisions pack (`../../../../../loomspan-extensions/extensions/decisions/README.md` in the sibling checkout)
and its release-aligned executor configuration and runnable support-triage recipe.
Installation registers reference-only behavior; applications still declare their
own callable executor YAML skills. Pack syntax and capabilities belong to the
pack's documentation. Its installed-JAR consumer covers direct/child execution,
runtime bindings, complete JSON results and normal canonical diagnostics.

An executor leaf declares an operation owned by application Java code, with YAML supplying its input contract, roles and operation configuration. Loomspan prepares and executes these leaves through the shared Java route, classifies them as `SkillKind.JAVA`, and retains their YAML declarations and exact configuration. The example below shows accepted declaration syntax. The shared [standard schema topic](standard-schemas.md) defines input validation; the application example below exercises root and standard-tool child execution.

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

`input_schema` and `rbac_roles` are optional. Authored input schemas use the [standard declaration rules](standard-schemas.md). Without an input schema, the existing generic object contract applies. Business input is never inherited from a parent.

Executor leaves MUST omit `model`, `thinking_level`, `prompt`, `planning_mode`, `concurrency`, `max_steps`, `allowed_skills`, `linter`, `output_schema`, `output_schema_max_retries`, `output_from`, and `output_bindings`. Presence fails even for null, false, empty maps, empty lists or empty strings. The forms do not select precedence. `typesafe` and `rest` are unsupported top-level manifest keys. For the `executor: typesafe` declaration and its pack-owned input contract, see the [TypeSafe topic](typesafe-skills.md).

`config` is permitted only on executor leaves. Omission and `{}` yield an empty immutable map. Explicit null, a bare field, a scalar or a list fail. Nested nulls, maps, lists, empty containers, scalar types and exact data keys are preserved. `config` is executor-owned domain data, not a Framework schema. It is decoded separately with the existing Java numeric kinds; lossless JSON-bound skill decoding applies to the rest of the document, not this actual top-level subtree.

## Transform executor input

Only executor leaves can declare root `input_transform`. Supply an object
containing exactly `language: jslt` and a nonblank string `expression`. Loomspan
compiles the expression at startup or namespace preparation. Malformed syntax
and unresolved classpath imports fail at that boundary. Invalid preparation
leaves the active generation in place. Retained invocations use the expression of
the captured YAML leaf generation.

```yaml
name: lookup
description: Look up one value
executor: infrastructure-sql
input_schema:
  type: object
  properties:
    value: {type: string}
  required: [value]
  additionalProperties: false
input_transform:
  language: jslt
  expression: '{"answer": .value}'
```

With an application registration for `infrastructure-sql`, caller input
`{"value":"case-17"}` delivers `{"answer":"case-17"}` in Java's `invocation.input()`.
The leaf's ordinary `input_schema` defines caller and planner input. Parent
bindings target that schema. On every direct or child execution, the leaf
validates the assembled business input and resolves references before evaluating
JSLT once. JSLT `.` sees that business input object. The expression result is the
complete Java input object. Both configuration maps remain available in their
own [v1 invocation accessors](../java-api/java-skills.md#reference-only-executor-declaration).

Use objects with string keys, arrays, strings, booleans, nulls, and finite
numbers. JSLT object construction preserves explicit null members, empty
objects and arrays, exact keys, and literal strings such as
`${/input/notResolved}` when quoted in the expression. An empty result object
succeeds. Input containing a runtime handle such as a resolved Resource, an
InputStream, or a byte array fails JSON admission. Evaluation errors and results
whose root is a scalar, array or null fail before executor entry. After
evaluation, Loomspan checks the mission lifecycle before starting the Java
handler. Omitting `input_transform` delivers the ordinary reference-resolved input, including
supported handles. Diagnostic metadata retains the original business input
and schema and the authored YAML.

Evidence: `ExecutorDeclarationTests` and `ExecutorInputTransformTest` cover
declaration validation, compilation, JSON fidelity and execution failures.
`ExecutorInputTransformIntegrationTest` covers connected direct/child execution,
configuration separation, retained expressions, failed preparation and diagnostic
fidelity. `SharedDeclarativeAuthorizationIntegrationTest` and
`ExecutorInvocationLifecycleIntegrationTest` cover authorization and cancellation.

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

The invocation receives the executor input object, the executing YAML leaf's `config` and qualified name, the selected executor configuration map, and the executor namespace generation ID even when no config path is declared. Retained execution uses only that captured registration and map across replacement, removal and recreation. Both Java forms share mission execution, caller authentication scope, observation, timeout and cancellation behavior. The leaf requires its YAML role policy AND its captured executor's Java declarative policy, evaluated against the same caller at root validation, planner filtering and execution. Role alternatives remain within each declaration; unrestricted/PermitAll cannot override the other constituent, and DenyAll denies. See [authorization](authorization.md#compose-executor-declaration-boundaries) for the composition rule and examples. Captured executor policy is rechecked with the trusted execution caller; retaining a generation never freezes an earlier authorization grant.

Validation and discovery execute no application code or argument-dependent advice. Invocation still runs the final Spring proxy with actual arguments, so application advice may reject a leaf that passed declarative validation. Catalog visibility is unchanged.

Public catalogs classify these leaves as `JAVA`. Diagnostic catalogs report `kind: JAVA` and `declaration: YAML`, retaining unchanged authored `yaml` and descriptive `sourcePath` alongside resolved qualified `executorName`, `beanName` and declared `method`. The authored executor reference remains unchanged in YAML even when it used a local name. Reference-only registrations never appear as callable skills. See [HTTP diagnostics](observability-http.md#skill-classification-and-declaration) for the complete shape matrix.

Evidence: `DefaultRegisteredSkillCatalogTest`, `ExecutorGenerationExecutionTest`, `ConsoleRestFixtureCorpusTest`, and Console `TestAllCallableFormsPreserveProvenanceAndRejectInvalidContracts` cover classification, source fidelity, same-name roles and Java/Go/MCP agreement.

## Versioned executor handoff

Java methods receive exactly `ai.loomspan.api.extensions.v1.SkillExecutorInvocation`,
a final class created by Framework with no public constructor. Its business values
are captured; `isCancelled()` and `remainingTime()` are live read-only observations.
Remaining time includes parent/shutdown limits, excludes cleanup grace and does
not restart at executor entry. Empty means no deadline; expiry returns zero.
Executors SHOULD check these observations and thread interruption before work and
retry waits, preserve interruption and bound their own I/O. Physical cancellation
of remote work is not guaranteed. `diagnostics()` supplies bounded message/JSON
snapshots through `info`, `warn` and `error`; severity never changes execution.
Retained handles close with executor work or lifecycle cutoff. System notices
are bounded and best effort; their absence establishes no capture condition.
Read the complete
[Java handoff contract](../java-api/java-skills.md#reference-only-executor-declaration)
for exact accessors, supported JSON values, fixed limits and lifecycle limitations.

Evidence: `ExecutorInvocationLifecycleIntegrationTest` covers direct/standard/
explicit-child budgets and cross-thread cancellation; `MissionLifecycleTest`
covers no deadline, expiry, cleanup-grace distinction, ancestry and live shutdown.

## Evidence and next topics

Run the complete HTTP/SQL-shaped executor application (`docs/quickstart-executors.md` in the matching framework checkout)
without a model provider or database. `ExecutorApplicationIntegrationTest`
exercises actual startup ingestion, root and standard-tool child HTTP calls,
default `ref://` resolution to a Resource leaf, and invocation-only SQL-shaped
domain checks. A Resource handle preserves access to its underlying content;
it does not copy or freeze those bytes.

For generation-keyed pools or clients, follow the existing public
[retirement listener guidance](../java-api/skill-reload.md#executor-owned-application-resources).
The executor generation ID is supplied even with no configuration path; resource
creation, reuse, retirement and shutdown remain application responsibilities.

- `ExecutorDeclarationTests`, `YamlSkillCatalogTests` and `YamlSkillDefinitionTest`: explicit forms, presence checks, ordinary input schemas and immutable copies.
- `ExecutorConfigurationTests`, `ExecutionConfigurationParserTest` and `ExecutorConfigurationStartupTests`: namespace structure, target enforcement, complete replacement, real Config Data loading, exact values and ordinary Spring behavior.
- `ExecutorDependencyResolutionTest`: local/qualified roles, visibility, exact paths/maps, executor-only startup, retained binding, distinguishable captured executor policies, target-base publication and root-reachable capture.
- `ExecutorGenerationExecutionTest` and `ExecutorGenerationExecutionIntegrationTest`: YAML retention, public JAVA classification, captured invocation construction and secured handoff across foreign replacement/removal/recreation.
- `SkillRoleEvaluatorTest` and `SharedDeclarativeAuthorizationIntegrationTest`: composed YAML/Java policy, metadata-only visibility/validation, dispatch rechecks and actual argument advice on mission workers.
- [Java skills](../java-api/java-skills.md): annotation discovery, versioned invocation class and application result/failure contracts.
- [Model selection](model-selection-and-connections.md): separate Framework provider configuration.

## Framework-accounted usage

Framework-accounted usage covers native Framework model calls. Executor-managed
requests/retries do not reserve the native provider-attempt budget, and authored
usage-shaped diagnostic JSON does not contribute to these totals. Zero means zero
Framework-accounted usage, not measured zero executor usage. Preserve native usage
precision: a missing report can produce a heuristic estimate when text is available;
unavailable evidence remains unavailable. These totals are not total application
request counts or billing totals.

`ExecutorAccountingIntegrationTest` exercises an exact native-attempt quota around
application HTTP failure/retry, executor-only execution, missing native reported
usage and HTTP success followed by local decoding failure without Console.
`ModelUsageExtractorTest` protects the explicit unavailable-usage path. See
[trace inspection](../skill-authoring/traces-and-debugging.md#bounded-executor-diagnostics)
for the existing record/content tools.
