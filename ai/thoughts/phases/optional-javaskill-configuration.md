# Configured Java Skill Executors

## Status and scope

This feature is in design discussion. The agreed direction below records the discussion through 2026-10-31; it is not implemented and does not belong to the completed namespace implementation scope. The method shape is selected: one invocation object and a String result. Exact public names and remaining contract details are open; executor lifecycle hooks are excluded.

Implementation-specific published configuration would be a new framework feature: existing Java skills are Spring-managed annotated methods, and the framework currently has no configurable Java declaration binding or configuration-injection contract. The original handoff is preserved below as historical context; the current decisions take precedence where they differ.

Work is limited to generic framework capabilities. Cloud and DataSkill examples are motivating context only. The accepted namespace design assigns Java methods through `@SkillMethod(namespace = "...")`, defaulting to `default`; this registration decision does not implement the configuration proposal below.

Destructive API changes are permitted without compatibility shims. The repository's closed supported API rules, documentation requirements, and supported-surface tests still apply. See the namespace document's identity findings and source links for the shared investigation context.

## Agreed direction

- Use the name `SkillExecutor`, with an `executor:` reference in each YAML skill, instead of SkillInterface/SkillImplementation. An annotated Java method supplies reusable execution behavior; many uniquely named YAML skills can use it with different operation configuration and input schemas. Exact annotation element names and the invocation object's complete public shape remain to be finalized.
- Both executors and YAML skills have namespace ownership. Use existing namespace visibility, not a separate executor visibility flag: a skill may reference an executor in its own namespace or in a Public namespace, but not in another Private namespace.
- Executors are reference-only registrations. YAML skills provide their callable surface, including description, input contract, roles, and skill configuration. An executor is not itself offered as a tool or callable through `SkillTemplate.invoke()`. Existing `@SkillMethod` remains the mechanism for ordinary directly callable Java methods. Integrate executor dependencies into existing validation and captured execution without adding a direct executor invocation route.
- Verify executor existence and namespace access at startup and in the API update workflow. Fit checks into existing validation, preparation, and admission boundaries rather than assuming `publish()` reruns dependency validation.
- Place application executor configuration under `loomspan.executor-config.<namespace>.<relative-path>`. This supersedes the earlier `loomspan.interfaces` suggestion. The executor declares the optional relative configuration reference so skill authors do not manage infrastructure paths. Reference syntax, omission, and missing-path rules are defined below.
- Each YAML skill may contain multiple nested parameters under `config:`. This is operation configuration interpreted by the executor, separate from invocation input and the executor's application configuration. Framework must preserve its content without interpreting its domain language.
- A database executor may accept a database selector in the skill's `config:` and resolve it against application executor configuration. This can support multiple databases with one executor; separate host bindings are not required merely for that example.
- Database access rules belong to the executor, not Framework. Namespace visibility controls access to the executor registration; it does not define database permissions. Trusted caller/namespace context must not be inferred from business arguments.
- Develop this feature with the intention of replacing REST skills. Keep REST support while developing and testing the new mechanism in Sidecar; remove it only after replacement coverage is demonstrated. Removal is not part of this document edit.
- Reuse the existing namespace publication, immutable admission snapshot, ownership, and retirement machinery. Do not introduce a parallel configuration-generation lifecycle.
- SkillExecutor is invocation-only and should behave as much like a function as possible. Do not add executor preparation, configuration-validation, or cleanup hooks. Executor-specific configuration errors are reported during execution; Framework retains responsibility for its own binding and validation rules.
- Reuse and extend `RestSkillInvocation`'s defensive copying: recursively copy maps/lists and make them unmodifiable, preserving nulls and non-container leaf values. This refines the earlier universal immutability requirement: arbitrary leaf objects and the content behind resource handles are not promised to be deeply immutable. Do not add Kryo or general object-serialization copying. Executors may perform application-owned I/O.
- Use one invocation-object parameter and a `String` return. Include the captured executor-configuration generation ID so application beans can manage their own resources. Exact public type/accessor names and remaining metadata are still to be finalized.
- Pass only the executing skill's input/configuration, the referenced executor configuration subtree, and necessary trusted metadata. Do not expose full application configuration, internal generation/runtime object graphs, or unrelated resources. Spring collaborators own resource setup/teardown, including responses to configuration-generation changes.

### Configuration omission and binding rules

- When the YAML skill omits `config:`, supply an empty immutable map.
- When the executor declares no configuration reference, supply an empty immutable map for executor configuration.
- When an executor explicitly declares a reference but the path is missing, fail Framework binding validation. Do not silently supply empty configuration or fall back to another source.
- When referenced configuration exists but has executor-specific problems, the executor reports the error during invocation; Framework does not interpret domain settings.
- Always supply `executorConfigGenerationId()` as the captured executor namespace's generation ID, including when no configuration reference is declared. It is not null or a separate configuration-only revision.

These rules support either configuration source, both, or neither, without merging, precedence, or inheritance. Explicit null values and invalid structural types are separate syntax questions, not implied to be equivalent to omission.

### Namespace-scoped configuration publication

Keep the same `loomspan.executor-config.<namespace>.<relative-path>` YAML structure at startup and in `ExecutionConfiguration` supplied to the update API. Startup configuration may contain multiple namespaces. A publication may contain executor configuration only for its target namespace; reject foreign namespace entries rather than ignoring them or updating another namespace.

Publication remains complete replacement. Omitting `executor-config` supplies no executor configuration for the new generation; do not inherit previous or startup values. An explicit executor configuration reference whose path is consequently missing fails the agreed binding validation. Configuration belongs to the executor namespace, including when a YAML skill in another namespace references that executor.

### Configuration reference syntax

Use a simple dot-separated path relative to `loomspan.executor-config.<executor-namespace>`. Each segment selects a map key; for example, `databases.inventory` selects the `inventory` map inside `databases` in that namespace's executor configuration.

The selected value must be a map. Its contents remain executor-defined and may include nested maps, lists, and scalars. An omitted reference supplies the agreed empty immutable map. Do not support array indexing, wildcards, expressions, or fallback lookup. Dots separate path segments, so keys containing literal dots cannot be addressed through this syntax. Reuse existing suitable path handling where possible rather than introducing an expression engine.

### Executor identity and reference resolution

Reuse existing skill identity conventions for executor registrations: local name plus namespace, namespace defaulting to `default`, omitted name defaulting to the Java method name, and existing name validation/namespace normalization. Duplicate executor identities fail registration; do not resolve by Spring bean name or fallback search. Executor registrations and callable skills are separate identity categories, so a callable skill and executor may share a qualified name without ambiguity.

Same-namespace relative YAML references are the selected direction, to be implemented first through the separate [relative YAML references prerequisite ticket](../tickets/loomspan-relative-yaml-skill-references.md). Executor references will follow that rule: `executor: sql` resolves in the declaring YAML skill's namespace, while `executor: infrastructure-sql` is explicit. No fallback search is permitted. Current code and the original namespace design require qualification; do not claim the prerequisite is already implemented. Host invocation and catalog lookup remain qualified under that ticket.

The motivation is both embedded application simplicity and hosted authors who may not know their assigned namespace. Canonical runtime, provider-tool, and diagnostic identities remain qualified; source YAML remains unchanged. Contextual authoring resolution does not create another runtime identity. `default` is a namespace name, not Public visibility: the owner's earlier use of "public" when describing annotation defaults was corrected to "default". Omitting an annotation namespace uses `default`; supplying one uses that namespace. Existing namespace visibility is unchanged.

### Generality and implementation simplicity

SkillExecutor must extend the mechanisms already used to discover and invoke `@SkillMethod` methods. RBAC, input validation, namespace validation, and the direct execution lifecycle already exist; reuse and extend their current owners rather than implementing executor-specific copies. Preserve Spring-managed invocation and applicable advice, caller authentication, validation boundaries, and the existing execution/observation controls. Reuse does not mean blindly adopting Java result serialization or exception-to-text semantics where the agreed REST replacement goal requires different behavior; resolve those differences explicitly within the shared mechanisms.

No new subsystem, lifecycle, or execution path may be introduced implicitly as an implementation detail. If an existing mechanism cannot meet a committed requirement, identify the concrete gap, explain why extension is insufficient, and discuss the simplest alternatives with the owner. Any proposed new subsystem, lifecycle, or execution path requires explicit agreement before implementation. Record that decision and its tradeoffs in the design; difficulty extending existing code is a reason for discussion, not automatic authorization for parallel machinery.

Developers decide which configuration belongs in application-level executor configuration and which belongs in the individual skill's `config:`. Support both sources without assigning domain-specific meanings to either: application configuration is not restricted to connections, and skill configuration is not restricted to SQL or operation selectors. Those are examples, not Framework rules. Omission follows the agreed empty-map rules above.

Use the [framework feature design lens](../framework-feature-design-lens.md) when evaluating this feature. Choose the simplest complete design that meets the committed goals and preserves production boundaries. Generality should come from preserving executor-owned data and behavior, not from speculative hooks, registries, binding layers, or lifecycle subsystems. Identify substantial subsystem work and discuss simpler alternatives with the owner before selecting it. Minimize both permanent technical debt and transition debt; REST coexistence has the explicit end condition of verified replacement in Sidecar.

Recommended minimal data contract, pending final API design: deliver the two configuration sources separately, alongside invocation input and only the trusted context actually needed. Let executor code decide how to interpret or combine them. Do not introduce Framework merging, override precedence, interpolation, or domain validation merely to support both sources.

Cost assessment from the current source investigation: carrying structured configuration through a generation fits existing ownership; cross-namespace executor references require extending dependency validation, reachable admission capture, and execution resolution because today's traversal follows `allowedSkills`. Namespace isolation and retained execution are essential correctness, not optional follow-up work. A new executor resource factory lifecycle, generic configuration schema system, or host binding registry would add separate concepts and requires demonstrated need; none is selected. Exact implementation effort remains to be assessed during design.

### Illustrative authoring shape

This is proposed syntax, not a supported manifest or a finalized API. Namespace ownership of supplied skill documents continues to come from publication.

```yaml
# Application configuration
loomspan:
  executor-config:
    infrastructure:
      sql:
        databases:
          inventory:
            # Executor-defined connection settings
```

An executor named `sql` in namespace `infrastructure` would reference the relative configuration path `sql`. A skill published into `sales` could use it when `infrastructure` is Public:

```yaml
name: createCar
description: Create a car
executor: infrastructure-sql
config:
  database: inventory
  sql: |
    insert into car (name, model, year)
    values (:/car/name, :/car/model, :/car/year)
input_schema:
  type: object
  properties:
    car:
      type: object
      properties:
        name: { type: string }
        model: { type: string }
        year: { type: integer }
      required: [name, model, year]
  required: [car]
```

SQL syntax, parameter binding, connection selection, and database authorization belong to the executor. The example does not establish Framework SQL support or a required executor configuration schema.

## Existing publication machinery: source-verified baseline

Read before selecting new implementation machinery:

- `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`: `prepare` accepts complete documents and `ExecutionConfiguration` for one namespace, captures a preparation view, and returns a detached one-shot candidate. `publish` checks candidate ownership/state and lifecycle, then delegates installation. Closing an unpublished candidate disposes it.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java`: `capturePreparation` captures the target base ID (or absence) and a coherent view of active namespace metadata. Preparation checks candidate dependencies against that view and builds its runtime. `publish` checks only the target base, then installs atomically; changes to foreign dependencies do not themselves reject publication.
- The same manager's `capture` traverses the root's reachable `allowedSkills`, selects one active generation per reachable namespace under the registry monitor, and acquires ownership. `validateAdmission` checks the retained snapshot outside that monitor. Executor references are not currently represented in this traversal; their integration is necessary design work, not a reason to create another snapshot system.
- `src/main/java/ai/loomspan/internal/skill/SkillAdmissionSnapshot.java` and `SkillGeneration.java`: execution resolves through captured immutable namespace associations and generation-owned capabilities/definitions/runtime. Superseded generations remain alive while ownership exists; retirement closes generation resources and dispatches existing retirement notifications.
- `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java`: publishable top-level sections currently are `connections`, `models`, `session`, and `execution-trace` (with only supported nested settings accepted). `executor-config` is not supported today and requires an explicit parser/configuration extension.
- [Complete namespace publication](../../../agent-skills/loomspan-docs/references/java-api/skill-reload.md) describes complete replacement, defaulting without prior/startup configuration inheritance, namespace visibility, retained admission, and retirement limitations.

Apply these existing semantics to executor dependencies and configuration. Do not assume a caller publication permanently pins a foreign executor generation: the existing model captures the current reachable graph at admission. Exact executor dependency representation and configuration storage remain to be designed. Do not add public generation fields merely because internal capture involves more than one namespace.

## Questions still to resolve

### Configuration validation: no assumed domain checks

Framework can validate its own declaration syntax, executor resolution, and namespace visibility. It does not know that `database`, `sql`, or any other executor-owned key has a particular meaning.

The earlier suggestion that preparation verifies that a selected database exists and required executor settings are present was a possible executor-provided capability, not an agreed requirement. Even "database exists" is ambiguous: a key in supplied configuration, an externally resolved resource, or a reachable live database are different claims. No database lookup, connection probe, SQL validation, mandatory schema, or executor validation callback is selected.

Settled boundary:

- Invocation-only execution is selected; executor-specific errors are reported at execution time. Configuration schemas and executor validation callbacks are not part of this design. Revisit only for a demonstrated need and explicit agreement, preserving today's validation boundary rather than silently turning `validate()` into application execution.
- Do not design hypothetical optional-validation hooks as remaining required work. Framework reference/path binding checks still need to fit the existing preparation/admission boundaries without publication-time dependency revalidation or retaining foreign runtime resources during metadata-only preparation.

### Registration, configuration ownership, and invocation

- Final annotation elements and invocation-object accessor/type names. Registration naming, relative-reference direction, and reference-only behavior are settled.
- Concrete Java representation of the structured configuration/input values. REST-style copying is settled; do not reopen universal deep immutability, Kryo, or arbitrary object cloning. Generic maps are the proposed minimal representation; no typed configuration binding facility has been selected.
- Configuration reference syntax, ownership, and publication shape are settled above: simple dot-separated map paths, executor namespace ownership, the same namespace-indexed layout at startup and publication, and rejection of foreign namespace entries in a publication. Final annotation representation and malformed-path diagnostics remain implementation details to specify consistently with these rules.
- Which trusted skill/executor/caller metadata is needed beyond the agreed executor configuration generation ID. Proposed accessors `input()`, `skillConfig()`, `executorConfig()`, and `skillName()` illustrate the discussion but are not a finalized public record. Caller authentication already reaches the execution thread through existing mechanisms; do not assume an Authentication field or general context bag is needed.
- How fixed Spring-managed executor methods participate in namespace replacement/removal/recreation, preserving proxy/advice behavior without mutating shared bean configuration between concurrent invocations.
- Resource management stays application/Spring-owned under the invocation-only decision. No executor resource factory or generation-specific preparation/cleanup hook is selected. Any future need must be demonstrated and discussed explicitly.
- Final output and exception details around the agreed String return. REST-equivalent unchanged text, empty-string success, null failure, and exception propagation are the proposed replacement baseline. Verify exact wrapping/authorization/cancellation behavior in existing code; do not copy Java exception-to-text behavior blindly or add retries/output validation.
- How existing YAML roles and applicable Java method-security advice compose at the executor boundary. Reuse existing checks and preserve advice; determine the behavior explicitly rather than introducing another authorization engine or silently bypassing method policies.
- Remaining manifest structural rules, including explicit null/wrong-type configuration, malformed paths, and which model/planning/output fields an executor-backed leaf may declare. Do not infer new supported combinations from this document's examples.
- Console representation, diagnostics, packaging, and deliberately supported API additions. The current sole supported SPI remains `RestSkillHandler` until the public surface is explicitly changed, documented, and tested.

### REST replacement verification

Prove the new mechanism in Sidecar before removing REST. Cover named YAML declarations, input schemas/reference resolution, roles/caller authentication, root and child execution without a model call, raw non-null string results (including empty strings), failure behavior, observation, cancellation, and retained work across publication/removal. HTTP execution is the replacement case; SQL is a useful second case for testing generic configuration rather than making HTTP assumptions part of the contract. Exact test plan and migration/removal sequencing remain open.

## Handoff for the next design discussion

This is design work, not an instruction to start implementation. Work through remaining questions one at a time, prioritizing architectural simplicity. The relative-reference prerequisite ticket has been drafted and intentionally set aside; continue SkillExecutor discussion here without reopening its agreed authoring direction. Verify prerequisite implementation status before implementation planning.

Suggested next discussion: finish the invocation object's minimal fields and exact output/failure behavior, then address existing authorization composition and manifest structural rules. Distinguish a recommendation from a decision accepted by the owner. Do not turn every implementation detail into a new abstraction or require the owner to reconfirm decisions recorded above.

Important clarifications to retain:

- "Function-like" means invocation-only with defensively copied inputs, not pure or side-effect-free. A Spring-managed executor can delegate to other beans, perform HTTP/database work, and rely on application-owned resources.
- Application beans own creation/reuse/teardown of connection pools and other resources, including changes driven by executor configuration generations. The captured generation ID is a useful key, not a Framework promise to manage those resources. Reuse existing retirement facilities if appropriate; no new lifecycle hook is selected.
- REST's copy helper traverses maps and lists and preserves other leaves. Immutable scalars need no clone. Existing `ref://` resolution can yield Spring `Resource` file inputs; preserving a resource handle does not freeze or duplicate its underlying bytes. Such handles arise from relevant input values, not automatic exposure of connection pools, sessions, or runtime internals. See `RestSkillInvocation`, `DefaultRefResolver`, and `SessionLocalVirtualFileSystem` for the existing behavior.
- Configuration is general executor-owned data at either location. SQL, database selectors, connection settings, and HTTP are examples, not schema requirements or a division of responsibilities enforced by Framework.
- Avoid exposing unused object graphs or speculative metadata. The executor receives only its relevant input/configuration subtree and necessary trusted identity. No full configuration tree, admission snapshot, mutable session bag, or internal runtime object is selected.
- Reuse existing SkillMethod invocation, RBAC, input/reference validation, namespace validation, execution limits, observations, and generation retention. Any proposed new subsystem, lifecycle, or execution path must be identified, compared with simpler alternatives, and explicitly agreed before implementation.

Only design documents/tickets have been edited in this discussion; no executor production implementation or Sidecar verification has been performed. Documentation edits were checked with `git diff --check`, not production tests. Historical notes below preserve motivating context and must not override these current decisions.

## Historical proposal and motivating context

The following records the earlier handoff. Its tentative names, syntax, and open questions are historical rather than additional selected requirements.

### Direction and motivation

The owner supported this boundary: the framework knows how to invoke a JavaSkill without needing to parse or understand its data definition. The proposal generalizes this into optional configuration whose domain meaning belongs to the Java implementation.

DataSkill is the motivating example, not a new native framework skill language. It would be a JavaSkill implementation that interprets an app's declarative data definition. Other JavaSkills and hosting applications should be able to use the same generic configuration capability.

Research existing facilities first. The source discussion explicitly leaves open whether a framework change is necessary at all.

### DataSkill use case

The accepted V1 data-operation direction is named operations backed by explicit query definitions. Runtime arguments supply parameter values without changing the published query structure. Parsing queries from method names and accepting arbitrary runtime query definitions were not selected for V1.

The candidate definition describes tables, fields, data types, constraints, named operations, and descriptive guidance. Declarative business validation is intended to run deterministically in DataSkill, without an LLM call. A rule may supply one custom `message` containing both its failure explanation and correction guidance for the companion; separate message and hint fields were not selected.

These details explain the configuration's purpose. The framework should not interpret query operators, business rules, schema constraints, or validation messages. The exact schema/query language, helper set, transaction semantics, and database integration remain separate design work. Supabase is Cloud's intended initial backend, not a requirement for the framework configuration mechanism.

YAML was proposed for a consistent authoring experience. YAML storage, file naming, inline layout, and packaging are not finalized requirements.

### Proposed shape, not a selected contract

An assistant proposed an explicit configuration field in the JavaSkill declaration, rather than arbitrary unknown declaration fields:

```yaml
# Illustrative only; not checked against existing declaration syntax.
name: recipe-data
type: java
implementation: DataSkill
config:
  schema:
    # Implementation-owned data definition.
  operations:
    # Implementation-owned named operations.
```

The keys, implementation identifier, and Java binding API have not been selected. Referenced artifacts remain an alternative. Loading and preserving structured configuration for delivery to the implementation is a proposal; no particular Java representation or initialization/invocation signature is agreed.

The same Java implementation might serve multiple differently configured declarations, including declarations in different namespaces. Instance creation, configuration binding, mutable state, and isolation remain open.

### Configuration, call arguments, and trusted context

The source discussion recommends distinguishing:

| Information | Role |
|---|---|
| Implementation configuration | Published settings and definitions interpreted by the JavaSkill. |
| Invocation arguments | Per-call values, such as a named operation and its parameters. |
| Trusted runtime context | Host-established caller identity and authorized app/environment context. |

This is a recommended separation, not an adopted context API. Optional configuration alone does not establish access enforcement. Companion-authored definitions and arguments must not become authority to impersonate another caller or select an unauthorized app.

Cloud establishes the authenticated account, authorized app/environment, and membership for each application request. DataSkill enforces the selected sharing strategy on every operation, including related records. Cloud currently requires every application table definition to include `ownerId`, assigned from trusted account context. These are DataSkill/Cloud responsibilities, not framework schema rules.

Cloud-specific strategy JavaSkills were discussed as a possible implementation technique, but their cooperation with DataSkill and prevention of bypass are unresolved. The location and distribution of DataSkill, shared database code, and strategy implementations are also undecided. Initially building DataSkill in Cloud and later extracting a reusable library was an assistant suggestion, not an accepted packaging decision.

### Open research questions

- What configuration or extension information do current JavaSkill declarations already support?
- How are Java implementations registered and bound to declarations in the framework and sidecar?
- Should configuration be inline, referenced, or represented another way?
- What representation exposes configuration, and is it available at initialization, invocation, or both?
- Can the implementation validate its configuration, when would this happen, and how would author-facing failures be reported?
- How are updates handled when multiple declarations use one implementation with different configuration?
- How are instances and mutable state scoped to prevent one configured use from affecting another?
- How does the host supply trusted context separately from invocation arguments and published configuration?
- Which configuration is declaration-specific, namespace-specific, or host-level?

### Approaches not selected

- A framework-native DataSkill schema/query parser.
- A framework policy contract built around Cloud data-access strategies.
- Hardcoding Share Nothing, Owner Edit / Everyone Read, or Share Everything in framework core.

