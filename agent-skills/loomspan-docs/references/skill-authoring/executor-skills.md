---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Executor leaf declarations, transforms and configuration

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

Executor leaves MUST omit `model`, `thinking_level`, `prompt`, `planning_mode`, `concurrency`, `max_steps`, `allowed_skills`, `linter`, `output_schema_max_retries`, `output_from`, and `output_bindings`. Presence fails even for null, false, empty maps, empty lists or empty strings. The forms do not select precedence. `typesafe` and `rest` are unsupported top-level manifest keys. For the `executor: typesafe` declaration and its pack-owned input contract, see the [TypeSafe topic](typesafe-skills.md).

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
complete Java input object. All three configuration maps remain available in their
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

## Terminal contract diagnosis

Java callers can read `SkillException.getFailureDiagnosis()` through direct and
admitted invocation even with diagnostics set to `NEVER`. It identifies the
canonical skill and input schema, input transform, output transform or output
schema stage, with exact validator issues or the authored transform explanation.
Selection follows the canonical terminal cause. A corrected attempt or losing
parallel branch can leave diagnostic evidence while the public diagnosis is
absent. Read the [public diagnosis contract](../java-api/observation-and-errors.md#terminal-contract-diagnosis).

`ExecutorOutputSchemaIntegrationTest`, `ExecutorInputTransformIntegrationTest`,
`ExecutorOutputTransformIntegrationTest`, `StepLoopMissionExecutionEngineTest`
and `LoomspanSessionTest` verify these boundaries.

## Publish a transformed executor result

Only executor leaves can declare root `output_transform`. It may appear alone or
alongside `input_transform`. Each transform requires an object containing exactly
`language: jslt` and a nonblank string `expression`. Loomspan compiles both at
startup or namespace preparation. Failed preparation leaves the active generation
in place, and retained invocations use the captured leaf expressions.

This fragment publishes three business fields from a support-triage response.

```yaml
output_transform:
  language: jslt
  expression: |
    {
      "assignedTeam": .answers.team.choice,
      "repeatContactProbability": .answers.repeat.noul,
      "impactScore": .answers.impact.score
    }
```

After the executor succeeds, its ordinary result text MUST contain exactly one
complete JSON value. Loomspan binds JSLT's `.` to that value and evaluates once.
The author chooses which fields to expect. A missing-field lookup on an object
produces JSON null. Objects, arrays, strings, finite numbers, booleans and null
are valid at the root. The published result is JSON text, so a string such as `"returns"`
keeps its quotes and escaping, and null publishes as `null`. JSLT object
construction preserves explicit null fields and empty objects and arrays.

Direct callers and ordinary parent tool calls receive that published text.
[Child-result bindings](input-bindings.md) select from the published shape,
and [`output_from`](output-contracts.md#forwarding-declaration-and-completion) forwards the
whole published text unchanged. When both `output_transform` and `output_schema`
are omitted, the ordinary executor result text passes through exactly, including
empty or non-JSON text.

Invalid JSON or a transform error fails the leaf after successful executor
work. The executor may already have performed side effects. Loomspan neither
repeats the executor call nor publishes its raw text as a successful fallback.
Executor failures skip the transform.

Under existing trace access and size limits, the leaf's canonical completion
metadata records raw `executorOutput`, published `skillOutput` on success, and
`outputTransformStatus` with `succeeded` or `failed`. A failed transform includes
`outputTransformFailure` for diagnosis.

Evidence comes from `ExecutorDeclarationTests`, `ExecutorOutputTransformTest`
and `ExecutorOutputTransformIntegrationTest`. `YamlSkillCatalog` prepares the
expression, `YamlSkillDefinition` retains it and `ExecutionCoordinator` publishes
the result on the leaf execution path.

## Validate the published executor result

An executor leaf MAY declare root `output_schema`. Each skill owns its contract,
including when several skills share one executor. The schema uses the existing
[object-root standard profile](standard-schemas.md). It compiles during startup
or namespace preparation. Invalid preparation preserves the active generation,
and an admitted invocation uses its captured leaf schema.

| Declared fields | Successful publication |
| --- | --- |
| Neither | Exact ordinary executor text, including empty or non-JSON text |
| `output_schema` | Original text after strict complete-JSON and schema validation |
| `output_transform` | Serialized JSLT result with any JSON root |
| Both | Serialized JSLT result after schema validation of that transformed object |

This schema-only fragment validates an executor that already returns the intended
object. Numeric 0 and 1 both satisfy its inclusive bounds.

```yaml
output_schema:
  type: object
  properties:
    repeatContactProbability: {type: number, minimum: 0, maximum: 1}
  required: [repeatContactProbability]
  additionalProperties: false
```

For an executor returning a response envelope, add this fragment alongside the
same schema. Validation applies to the projected object.

```yaml
output_transform:
  language: jslt
  expression: |
    {"repeatContactProbability": .answers.repeat.noul}
```

The missing projection becomes a present null and fails `type: number`. A string
such as `"high"` fails `type`, and an out-of-range number fails `minimum` or
`maximum`. Omitting or misspelling the published key fails `required`. Schema-only
blank, malformed, non-JSON, multiple-value, scalar, array or null results fail.
Successful validation preserves the same candidate text, formatting and JSON
types. A transform alone can publish bare `0.95` or null. Probability validation
uses an object such as `{"repeatContactProbability":0.95}`.

Validation runs once after successful executor work and optional transformation,
before direct, tool, forwarding or downstream binding consumers accept the result.
A mismatch is terminal. Diagnostics identify the producing skill, offending
location and constraint. On mismatch, no additional executor or model call follows,
and a dependent child does not execute. Executor work may already have produced
side effects. Executor and transform failures retain their existing failure paths.
Executor leaves MUST omit `output_schema_max_retries` and MUST NOT use
`x-loomspan-evidence` annotations on their output schema because they have no
declared child work.

Under ordinary trace access and size limits, raw `executorOutput` remains evidence.
A successful transform retains `outputTransformStatus: succeeded` even if schema
validation fails. `skillOutput` records successful publication only. The existing
schema outcome reports one attempt and zero retries. Effective structural schema
metadata is exposed for the leaf and its forwarding parents through the
[ordinary metadata and binding rules](output-contracts.md#effective-output-metadata).

Evidence comes from `ExecutorDeclarationTests`,
`ExecutorOutputSchemaIntegrationTest` and `SkillGenerationManagerTest`.
`YamlSkillCatalog` prepares the authored contract, `ExecutionCoordinator` invokes
`OutputSchemaValidator` at publication, and `SkillDependencyValidator` resolves
effective structural metadata.

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

Published `ExecutionConfiguration` uses the same layout and accepts only its target namespace, rejecting even an empty foreign entry. Each publication is complete replacement: omitted executor configuration is empty and never inherits startup or prior-generation data. Skill `config` and namespace executor configuration are distinct maps. In published candidates, a whole-node `{ $var: name }` marker anywhere under either map resolves from the same candidate source as provider markers. Mapping keys and sequence elements participate. Other strings, including `${...}`, remain literal. Supplied strings are terminal and retain multiline contents. Framework checks structure and dependencies during staging, then effective keys after resolution. An unresolved key defers only a selector that depends on it. Executor domain validation runs at invocation. See [complete namespace publication](../java-api/skill-reload.md).

The `config` element on `@SkillExecutor` selects an optional dot-separated map path relative to the executor namespace. Omission or an explicitly empty string supplies an empty immutable map. Whitespace-only paths, surrounding whitespace and empty segments (`.x`, `x.`, `x..y`) fail without trimming. Selection uses exact case-sensitive map keys: no array indexing, expressions, wildcard, fallback lookup or literal-dot key addressing is supported. Owner missing, null, scalar, list and non-map intermediate selections fail; an empty selected map succeeds. Literal dotted keys inside the selected subtree remain unchanged data. The shared dependency validator validates decidable structure during staging, checks the complete resolved structure during preparation and resolves the reached binding during admission; invocation does not reselect it. See [Java declarations](../java-api/java-skills.md#reference-only-executor-declaration).

For foreign executors the same exact selector also selects
`consumerExecutorConfig()` from the namespace owning the executing YAML leaf.
A support parent reaching a billing leaf uses billing, just like direct execution.
Missing keys at any map level and selected `{}` yield an empty consumer map.
Present null, scalar or list nodes at intermediate or final segments fail metadata
validation. Case mismatches and literal dotted root keys are missing, not alternate
lookup syntax. Same-namespace execution and no selector always yield an empty
consumer map; owner validation remains required. Only the selected subtree is
exposed. Framework never merges maps or decides which consumer fields are allowed.

Complete publication replaces the target's consumer settings with its leaves.
Omission clears fresh admissions. Retained handoffs keep the original leaf and
owner generations and both maps. Publishing a consumer changes neither another
consumer nor the owner ID from `executorConfigGenerationId()`.
`ExecutorDependencyResolutionTest` and `ExecutorGenerationExecutionIntegrationTest`
exercise selection, leaf ownership, rotation, omission and retained capture.

## Dependency capture and lifecycle

A local executor reference resolves only in the declaring YAML skill's namespace. A qualified reference selects that exact namespace and name without fallback. Same-namespace executors may be Private; foreign executor namespaces MUST be Public. The `default` namespace has no implicit Public visibility. Callable names and executor names have separate indexes, so one qualified identity may exist in both roles. An executor registration alone is never a callable root, child, catalog skill or tool.

Each namespace generation owns its fixed executor registrations and immutable configuration alongside its callable declarations. Namespaces containing only executors initialize as Private. A complete replacement or explicit recreation restores assigned registrations with newly supplied configuration; it never inherits old configuration. Removal blocks fresh dependent admission, preserves the application/Spring beans, and allows retained work to finish. Application code owns resources, domain validation and shutdown; Loomspan adds no executor preparation or cleanup callbacks.

Preparation overlays the complete candidate namespace on captured foreign metadata, invokes no application code and retains no foreign runtime lease. Foreign changes after preparation do not invalidate target-base publication; each fresh admission validates the graph it captures. Admission follows only root-reachable callable edges and their executor dependencies, selecting one coherent generation per needed namespace. Retaining an executor namespace does not traverse unrelated skills or grant authority to call them.

The invocation receives the executor input object, the executing YAML leaf's `config` and qualified name, the selected owner and consumer configuration maps, and the executor namespace generation ID even when no config path is declared. Retained execution uses only that captured registration and maps across replacement, removal and recreation. Both Java forms share mission execution, caller authentication scope, observation, timeout and cancellation behavior. The leaf requires its YAML role policy AND its captured executor's Java declarative policy, evaluated against the same caller at root validation, planner filtering and execution. Role alternatives remain within each declaration; unrestricted/PermitAll cannot override the other constituent, and DenyAll denies. See [authorization](authorization.md#compose-executor-declaration-boundaries) for the composition rule and examples. Captured executor policy is rechecked with the trusted execution caller; retaining a generation never freezes an earlier authorization grant.

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
- `PreparedCandidateVariablesTest` and `ExecutorGenerationExecutionIntegrationTest`: candidate-wide variables, exact maps and strings, actual worked-fixture invocation and retained foreign owner/consumer/leaf values.
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
