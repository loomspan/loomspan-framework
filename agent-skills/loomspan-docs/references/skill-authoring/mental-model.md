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

Loomspan combines model-driven YAML skills, executor-backed YAML leaves, and deterministic Java `@SkillMethod` skills. Each declaration is one callable capability. An LLM-backed skill reasons about its mission and can invoke only the direct child skills exposed to it.

```text
Application code -> Java, executor-backed YAML, or model-backed YAML entry skill
YAML planner -> YAML specialist -> Java @SkillMethod
```

Not every tree needs every level. Java skills can be roots without a model; a simple reasoning skill can be one YAML declaration.

## Canonical namespace identity

A declaration has an owning namespace and a local name. Both components have
1–64 ASCII characters matching `[a-z][a-z0-9]*(?:-[a-z0-9]+)*` and exclude
`anthropic` and `claude` anywhere in the component. The pair must satisfy
`namespace.length() + 2 + localName.length() <= 64`, including reference-only
executors. Thus `order-support` permits 49 local characters. `lookup-3pl` is valid.
Case changes, whitespace, underscores and repeated hyphens fail validation.

Startup YAML belongs to `default` regardless of subdirectory. Supplied YAML uses
the namespace passed to validation/preparation. Both Java annotations use their
declared namespace, defaulting to `default`. A nonempty annotation `name` is used
verbatim. An omitted or empty name uses the Java method name verbatim. For example,
`search()` needs no name, while `findOrder()` declares `name = "find-order"`.
Invalid Java declarations identify the method and explain how to correct it.

Callable pairs are unique across YAML and Java in each namespace. Executor pairs
are unique independently, so a callable and executor can share the same pair.
Qualified identities render as `namespace:local-name`. `(a-b, c)` and `(a, b-c)`
therefore remain distinct. Local authored references resolve in the containing
owner. Explicit `warehouse:lookup` selects that exact pair under the usual
visibility and authorization checks. Missing local targets fail in their owner.
In `orders`, bare `warehouse-lookup` selects `orders:warehouse-lookup`, while
`warehouse:lookup` selects the foreign callable. Removing the local declaration
makes the bare reference fail even when the foreign callable is installed.
`allowed_skills`, input/output binding producers, `output_from.skill` and evidence
leaves select callable skills. The `executor` field selects only the executor
index. Resolution precedes the selected target's visibility and authorization
checks. Raw YAML, prompts, diagnostic labels and original evidence text retain their content.

`NamespaceIdentityFoundationTest` covers every authored field and content fidelity.
`SkillDependencyValidatorTest#ambiguousBareChildrenKeepTheirOwnerAndMissingLocalTargetsEndLookup`
and `ExecutorDependencyResolutionTest#ambiguousExecutorNamesSelectExactOwnersAndCallableRoles`
cover installed targets, misses and independent indexes.

Declaration grammar, owner assignment and role uniqueness are covered by
`SkillIdentityTest`, `SkillMethodBeanPostProcessorTest` and
`PortableDeclarationIdentityIntegrationTest`.

Each root captures an immutable association of exactly the namespace generations reached
by its declared child graph, before input conversion or validation. Every declared branch
counts, including optional and role-restricted children; Java skills are leaves. Executor dependencies retain their owning namespace without traversing unrelated callables. Each
reached whole generation is retained once, but unrelated skills are not traversed and
unrelated namespaces are not retained. Definitions, contracts, models, connections,
limits and captured visibility remain stable across publication, removal and recreation.

Existing dependency validators check the captured graph outside the publication lock.
Broken reachable dependencies reject before execution; unrelated broken skills do not.
Validation uses the same checks with temporary leases and reserves no execution.
Publication may succeed after dependency-only changes while new admission fails; catalogs
continue describing prepared contracts. See [output contracts](output-contracts.md#effective-output-metadata).
Retaining a reached generation can retain unused resources, and traversal can contend
with publication. Admission constructs no runtime/client and does not make external
systems transactional. Applications can observe retirement for generation-keyed cleanup;
see [two-stage application updates](../java-api/skill-reload.md). There is no historical-invocation API.

Evidence: `SkillGenerationManager#capture`, `SkillDependencyValidator#checkAdmission`,
`SkillAdmissionSnapshotTest`, and `SkillReachableAdmissionTest`.

Bean names, YAML source labels, and declared method signatures are diagnostic locations, never a second callable identity. File-loaded YAML has a file-derived label; application-supplied `SkillDocument` YAML has its exact arbitrary `sourceName` label, which need not resemble a path or have an extension. Neither changes manifest `name` or `allowed_skills` resolution. The inspection wire field remains named `sourcePath`. Any YAML `mapping` key, including null, empty, or scalar forms, is rejected with annotation-only guidance.

Startup discovery requires lowercase directories and filenames beneath a pattern's
fixed prefix. With `classpath:/skills/**/*.yaml`, `Orders/lookup.yaml` fails on
`Orders`, and `orders/lookup.YAML` fails on `lookup.YAML`, including on hosts
whose usual glob would omit it. A literal `classpath:/skills/Orders/lookup.yaml`
checks only `lookup.yaml`. The `.yaml` pattern selects that extension; configure
`.yml` explicitly to load `.yml` definitions. Every nested startup definition still
belongs to `default`.

Supplied labels such as `Orders import #42` and `Orders/lookup.yaml` retain their
exact content through validation and preparation. Hosts loading files or archives
check the actual definition paths before constructing documents. See the
[validation workflow](validation-workflow.md) and [publication API](../java-api/skill-reload.md).
`StartupSkillPathTest` covers filesystem, exploded classpath and packaged JAR
paths; `PortableDeclarationIdentityIntegrationTest` covers public label behavior.

## Capability types

### Executor-backed YAML leaf

An [executor leaf](executor-skills.md) supplies input, roles and configuration to
registered Java execution behavior. The callable YAML skill can be an entry or a
parent's child and is classified as `JAVA`. The reference-only executor itself is
not callable. Provider packs such as [TypeSafe](typesafe-skills.md) use this route;
applications own the callable declarations and policy. Results follow the Java
String/null/JSON boundary; no generated output schema is supplied.

### Entry skill

An entry is any registered Java, executor-backed YAML, or model-backed YAML skill invoked through `SkillTemplate`. The facade accepts a map or an object convertible to a map, validates its effective input contract, and creates a fresh session. Callers SHOULD understand the entry contract without understanding the tree below it.

### Model-backed YAML skill

A YAML skill declares a configured `model`. It can declare private `prompt`, schemas, evidence, linting, retries, model settings, and local `allowed_skills`. Explicit `planning_mode: true` selects step-based planning. Each YAML child retains its own model and planning settings.

### Java skill

An application Spring bean's `@SkillMethod` declares one directly callable skill. The annotation owns its name and description; the method signature and `@SkillParam` own inputs. Java supplies the returned value: null/void becomes empty text, Strings remain exact, and other values use Jackson JSON. See [Java results and failures](../java-api/java-skills.md#inputs-results-and-lifecycle). No companion YAML, Java output schema, model settings, or synthetic plan is created.

Distinct overloaded methods MAY have distinct explicit skill names. Same/default names collide. Spring proxy, interface, and bridge methods are canonicalized, and invocation resolves the final managed bean so configured advice runs. Methods must be invocable through that bean's proxy; relevant lazy declarations are deliberately initialized during startup.

Use Java for operations whose correctness should not depend on model reasoning: lookups, calculations, controlled I/O, policy operations, and fixture access. Read [Java skills](../java-api/java-skills.md) for declaration and proxy guidance, [input contracts](input-contracts.md) for reflected shapes, and [authorization](authorization.md) for JSR-250 enforcement.

```java
@SkillMethod(name = "account-lookup", description = "Look up one account")
public Account lookup(
        @SkillParam(description = "Stable account identifier") String accountId,
        @SkillParam(description = "Optional region", required = false) String region) {
    // application logic
}
```

Optional primitive parameters are invalid because omission binds null; use reference types. `Object` and `Map<String, Object>` permit heterogeneous JSON-compatible values. Prefer records or DTOs for discoverable stable fields. These input rules do not imply automatic inheritance of business inputs between missions.

Java roots and children participate in the common skill mission lifecycle. Java executes without a framework model request and shares bounded mission timeout, interruption, and late-write cutoff handling. A successful child contributes its exact name at the parent tool boundary; a denial or failure contributes no success. The parent mission resumes on every exit.

Public execution kinds are `YAML` and `JAVA`. Executor-backed YAML executes as Java while retaining its YAML definition. Framework and Console diagnostics use declaration ownership for original source presentation and retain the resolved executor identity and Java implementation alongside it; see [skill diagnostics](observability-http.md#skill-classification-and-declaration). Pagination and links use only the registered name.

## Root, Planner, Specialist, and Leaf Are Roles

These words describe how a capability is used, not distinct framework classes.

- **Root or entry skill:** invoked by application code.
- **Planner:** an LLM-backed YAML skill with `planning_mode: true` that creates and executes a bounded task plan.
- **Specialist:** a child YAML skill responsible for a narrower mission. It may itself be a planner.
- **Leaf:** a capability that performs work without further decomposition, commonly a deterministic Java skill.
- **Synthesis skill:** a skill whose primary responsibility is composing evidence and results into the final output.

A YAML skill can be a root in one tree and a nested specialist in another if its contract and visibility boundaries make sense in both contexts.

## Visibility Is Local

`allowed_skills` defines the candidate child Java, executor-backed YAML, or model-backed YAML skills for one LLM-backed skill. It is not transitive.

If a root allows `network:investigate`, and that skill allows `network:check-dns`, the root sees `network:investigate`; it does not automatically see `network:check-dns`.

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
- every branch preserves the root's authoritative retained namespace association, caller and session; current generation follows the executing target and the exact parent binding resumes after success or failure. See [execution limits](execution-limits.md) for the entry/current settings split.

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

### Planner inability

During standard execution, a root or nested model can call `loomspan_cannot_complete`
alone with an object containing one nonblank string, `reason`. The Framework
exposes this control even when the skill has no application tools. The invocation
ends unsuccessfully. Its reason describes why the objective cannot be completed
with the available inputs, tools, and authority. An explicit-step model can
declare inability during [assigned work or final synthesis](planning-concurrency.md#planner-inability).
A valid negative answer can fulfill an objective and follows ordinary result
validation.

An enclosing standard tool caller can receive the exact reason through its failed
call. It should treat the message as explanatory data and continue only with a
permitted action that addresses the obstacle. Completed work can have effects.
Explicit-step callers propagate the failure through their normal task boundary.
Loomspan performs no automatic replay or rollback. The tool control is available
on standard execution model turns.

`ExplicitJavaFailureHandlingIntegrationTest` verifies root and nested behavior.
See [Java caller handling](../java-api/observation-and-errors.md#error-taxonomy) and
[failure diagnostics](traces-and-debugging.md#terminal-outcome-and-failures).

### Explicit step planning

Use step-based planning when the skill genuinely needs dynamic decomposition, selective child-capability use, or multi-step progress toward its result. Planning adds an initial plan-model interaction and a bounded model-driven step loop. A planning step may invoke a nested skill, and that child independently chooses direct or planning execution from its own manifest.

An explicit planner may also declare planning-only `concurrency`; omission defaults it to enabled and `false` requires serialization. Generated `parallelGroup` values define validated ordered execution units. The coordinator assigns exact tasks, dispatches valid enabled groups concurrently, waits for every ordinary member outcome, and folds results and diagnostics in task-list order. Ungrouped tasks and disabled groups remain serialized. Each assigned task costs one step. Ordinary final synthesis costs one more; an explicit forwarding parent completes after all accepted work by returning one designated direct-child result, with no synthesis slot. Top-level `output_bindings` can assemble several accepted child results and current-input values after the same full-success boundary. Fully bound assembly also has no synthesis slot; mixed assembly retains one model contribution slot for unbound fields. See [output contracts](output-contracts.md) for `output_from`, `output_bindings`, exact result semantics, and derived schema metadata. Read [planning-concurrency.md](planning-concurrency.md) before authoring or diagnosing task groups, dependencies, join behavior, or step budgets.

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
- `YamlSkillCatalog` validates model-backed manifests and rejects legacy mappings. `SkillGenerationManager#prepare` assembles and validates a detached complete generation; `ExecutionBinding` carries that exact generation through execution.
- `DefaultSkillVisibilityResolver` filters the local shared child surface. `CapabilityExecutionRouter` and `ExecutionCoordinator` own dispatch and common isolated mission boundaries.
- `SupportedSurfaceIntegrationTest` demonstrates application methods called through the supported facade.
- `DefaultRegisteredSkillCatalogTest` covers declaration-source diagnostics; `ExecutorGenerationExecutionTest` covers retained executor YAML and public Java classification.

## Coverage Boundary

This document does not define every manifest field or the complete behavior of planning, inputs, outputs, RBAC, attachments, retries, quotas, or traces. Consult [README.md](README.md) for current topic coverage before advising on those areas.

## Namespace publication boundary

Complete publications require namespace, supplied documents and ExecutionConfiguration.
Startup namespaces are Private; foreign outgoing references require a Public target.
Fixed Java methods remain assigned to their original namespace across replacement
and explicit recreation; withdrawal preserves beans and retained work. There is no
activation API or configuration inheritance. See [publication](../java-api/skill-reload.md)
for complete signatures, sequencing and retirement. Namespace updates support preparation
and installation, retained reachable admission and captured cross-namespace execution.
Captured canonical observations and existing Console consumers are verified by `NamespaceObservationIntegrationTest` and `ConsoleTraceFixtureCorpusTest`.

Evidence: `SkillReloaderTest`, `SkillDependencyValidatorTest`, `NamespaceStartupIntegrationTest`.

Boundary evidence: `SkillReachableAdmissionTest#localDefaultReferencesMatchQualifiedAcrossAllRootOperations`, `NamespaceIdentityFoundationTest#sourcePromptSchemaAndRawEvidenceRetainAuthoredContent`, and `NamespaceStartupIntegrationTest#suppliedLocalDocumentsValidateAndPrepareInEachOwnerBeforePublicationWithoutFallback`.

Executor YAML leaves execute through generation-owned Java registrations and captured configuration bindings. Their public execution kind is `JAVA`; diagnostics retain their YAML declaration/source plus resolved executor identity and Java implementation. Annotated Java methods retain bean/method provenance. Execution classification and declaration provenance are separate; see [HTTP diagnostics](observability-http.md#skill-classification-and-declaration). `rest:` is unsupported manifest syntax. Read [executor leaves](executor-skills.md) for declaration rules and limitations.
